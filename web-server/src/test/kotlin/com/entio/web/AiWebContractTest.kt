package com.entio.web

import com.entio.web.ai.AiAssistantProvider
import com.entio.web.ai.AiCredentialStore
import com.entio.web.ai.AiProviderClient
import com.entio.web.ai.AiProviderCompletion
import com.entio.web.ai.AiProviderRequest
import com.entio.web.ai.AiProviderTestResult
import com.entio.web.ai.AiToolLoopProvider
import com.entio.web.ai.AiTypedEditCapabilityAdapter
import com.entio.web.ai.InMemoryAiCredentialStore
import com.entio.web.ai.OpenAiCompletedResponse
import com.entio.web.ai.OpenAiFunctionCall
import com.entio.web.ai.OpenAiProviderEvent
import com.entio.web.ai.OpenAiResponsesRequest
import com.entio.web.ai.OpenAiResponsesResult
import com.entio.web.ai.OpenAiUsage
import com.entio.web.ai.models.AiProviderModelDescriptor
import com.entio.web.ai.provider.AiModelVerificationResult
import com.entio.web.ai.provider.DevelopmentAiModelProviderClient
import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebApplicationDependencies
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AiWebContractTest {
    private val mapper: ObjectMapper = ObjectMapper().findAndRegisterModules()

    @Test
    fun conversationDraftAnalysisSubmissionAndHumanApplicationAreVersionedScopedAndIdempotent(): Unit = testApplication {
        val provider = FakeAiProvider(
            completed(
                calls = listOf(
                    OpenAiFunctionCall(
                        callId = "call-create-receivable",
                        name = AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
                        argumentsJson = """{"sourceId":"simple","editType":"create-class","rationale":"Add the reviewed concept.","label":"Receivable Account"}""",
                    ),
                ),
            ),
            completed(text = "The private draft is ready for deterministic analysis."),
        )
        val setup = setup(provider)
        application { module(setup.dependencies) }
        configureModel()

        val created = client.post("/api/v1/projects/simple/ai/conversations")
        assertEquals(HttpStatusCode.OK, created.status)
        val createdBody = created.bodyAsText()
        val conversationId = mapper.readTree(createdBody).at("/conversation/id").asText()
        check(conversationId.isNotBlank()) { "Conversation creation returned no id: $createdBody" }

        val messageBody = """{"message":"Create class Receivable Account.","screenContext":{"screen":"CHANGES","selectedSourceId":"simple"}}"""
        val first = client.post("/api/v1/projects/simple/ai/conversations/$conversationId/messages") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "message-1")
            setBody(messageBody)
        }
        val firstBody = first.bodyAsText()
        check(first.status == HttpStatusCode.OK) { "Unexpected message response ${first.status}: $firstBody" }
        val firstJson = mapper.readTree(firstBody)
        val runId = firstJson.path("run").path("id").asText()
        val draftId = firstJson.path("draftId").asText()
        assertEquals("v1", firstJson.path("apiVersion").asText())
        check(firstJson.at("/run/status").asText() == "READY_FOR_REVIEW") { firstJson.toPrettyString() }
        assertTrue(draftId.isNotBlank())

        val replay = client.post("/api/v1/projects/simple/ai/conversations/$conversationId/messages") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "message-1")
            setBody(messageBody)
        }
        assertEquals(firstJson, replay.json())
        assertEquals(2, provider.toolRequests.size)

        val conversation = client.get("/api/v1/projects/simple/ai/conversations/$conversationId").json()
        assertEquals(2, conversation.at("/conversation/messages").size())
        val privateDraft = client.get("/api/v1/projects/simple/ai/drafts/$draftId").json()
        assertContains(privateDraft.at("/draft/items/0/summary").asText(), "Receivable Account")
        assertTrue(privateDraft.at("/draft/items/0/aiGenerated").asBoolean())

        val analyzed = client.post("/api/v1/projects/simple/ai/drafts/$draftId/analysis")
        assertEquals(HttpStatusCode.OK, analyzed.status)
        val analysis = analyzed.json().path("analysis")
        assertTrue(analysis.path("readyForReview").asBoolean())
        assertTrue(analysis.path("validationOk").asBoolean())
        val previewFingerprint = analysis.path("previewGraphFingerprint").asText()
        assertTrue(previewFingerprint.isNotBlank())

        val submitBody = mapper.writeValueAsString(
            mapOf(
                "analysisId" to analysis.path("id").asText(),
                "runId" to runId,
                "rationale" to "Submit the reviewed accounting concept.",
                "expectedBaselineFingerprint" to analysis.path("baselineFingerprint").asText(),
                "expectedDraftFingerprint" to analysis.path("draftFingerprint").asText(),
                "expectedPreviewGraphFingerprint" to previewFingerprint,
                "expectedAnalysisReferenceIds" to analysis.path("references").map { it.path("id").asText() },
            ),
        )
        val submitted = client.post("/api/v1/projects/simple/ai/drafts/$draftId/submit") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "submit-1")
            setBody(submitBody)
        }
        assertEquals(HttpStatusCode.OK, submitted.status)
        val submittedJson = submitted.json()
        assertEquals("READYFORREVIEW", submittedJson.path("reviewState").asText())
        assertEquals("alice", submittedJson.path("submittingUserId").asText())
        assertTrue(submittedJson.at("/itemAttributions/0/aiGenerated").asBoolean())
        val proposalId = submittedJson.path("proposalId").asText()

        val submitReplay = client.post("/api/v1/projects/simple/ai/drafts/$draftId/submit") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "submit-1")
            setBody(submitBody)
        }
        assertEquals(proposalId, submitReplay.json().path("proposalId").asText())
        assertEquals(1, client.get("/api/v1/projects/simple/staged").json().path("entries").size())
        assertEquals(setup.sourceBefore, Files.readString(setup.projectRoot.resolve("ontology/simple.ttl")))

        val contributorApproval = client.post("/api/v1/projects/simple/proposal/approve") {
            header("X-Entio-User", "alice")
        }
        assertEquals(HttpStatusCode.Forbidden, contributorApproval.status)
        assertEquals(setup.sourceBefore, Files.readString(setup.projectRoot.resolve("ontology/simple.ttl")))

        val reviewerApproval = client.post("/api/v1/projects/simple/proposal/approve") {
            header("X-Entio-User", "bob")
        }
        assertEquals(HttpStatusCode.OK, reviewerApproval.status)
        assertContains(reviewerApproval.bodyAsText(), "APPROVED")
        val applied = client.post("/api/v1/projects/simple/proposal/apply") {
            header("X-Entio-User", "bob")
        }
        assertEquals(HttpStatusCode.OK, applied.status)
        assertContains(applied.bodyAsText(), "APPLIED")
        assertContains(Files.readString(setup.projectRoot.resolve("ontology/simple.ttl")), "ReceivableAccount")
        assertTrue(client.get("/api/v1/projects/simple/summary").json().path("symbolCount").asInt() >= 3)

        val crossUser = client.get("/api/v1/projects/simple/ai/conversations/$conversationId") {
            header("X-Entio-User", "bob")
        }
        assertEquals(HttpStatusCode.NotFound, crossUser.status)
        assertContains(crossUser.bodyAsText(), "conversation-scope-violation")

        val conflict = client.post("/api/v1/projects/simple/ai/conversations/$conversationId/messages") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "message-1")
            setBody("""{"message":"Explain Customer."}""")
        }
        assertEquals(HttpStatusCode.Conflict, conflict.status)
        assertContains(conflict.bodyAsText(), "idempotency-conflict")

        val help = client.get("/api/v1/projects/simple/ai/help")
        assertEquals(HttpStatusCode.OK, help.status)
        assertTrue(help.json().path("entries").size() > 0)
    }

    @Test
    fun privateSseSupportsOrderingReconnectResyncCancellationAndLegacyCompatibility(): Unit = testApplication {
        val provider = FakeAiProvider(
            completed(text = "Customer is an asserted class in the approved project."),
            completed(text = "I interpreted the lending request semantically and left the private draft ready for review."),
        )
        val setup = setup(provider)
        application { module(setup.dependencies) }
        configureModel()

        val created = client.post("/api/v1/projects/simple/ai/conversations")
        val createdBody = created.bodyAsText()
        val conversationId = mapper.readTree(createdBody).at("/conversation/id").asText()
        check(conversationId.isNotBlank()) { "Conversation creation returned no id: $createdBody" }
        val turnResponse = client.post("/api/v1/projects/simple/ai/conversations/$conversationId/messages") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "explain-1")
            setBody("""{"message":"Explain Customer."}""")
        }
        val turnBody = turnResponse.bodyAsText()
        check(turnResponse.status == HttpStatusCode.OK) { "Unexpected message response ${turnResponse.status}: $turnBody" }
        val turn = mapper.readTree(turnBody)
        val runId = turn.at("/run/id").asText()

        val stream = client.get("/api/v1/projects/simple/ai/runs/$runId/events") {
            header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
        }.bodyAsText()
        assertTrue(stream.indexOf("id: $runId:1") < stream.indexOf("id: $runId:2"))
        assertContains(stream, "event: run-started")
        assertContains(stream, "event: text-completed")
        assertFalse(stream.contains("server-only-test-secret"))
        assertFalse(stream.contains("provider-neutral"))

        val resumed = client.get("/api/v1/projects/simple/ai/runs/$runId/events") {
            header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
            header("Last-Event-ID", "$runId:1")
        }.bodyAsText()
        assertFalse(resumed.contains("id: $runId:1\n"))
        assertContains(resumed, "id: $runId:2")

        val resync = client.get("/api/v1/projects/simple/ai/runs/$runId/events") {
            header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
            header("Last-Event-ID", "$runId:9999")
        }.bodyAsText()
        assertContains(resync, "resynchronization-required")
        assertContains(resync, "/api/v1/projects/simple/ai/runs/$runId")

        val crossUser = client.get("/api/v1/projects/simple/ai/runs/$runId/events") {
            header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
            header("X-Entio-User", "bob")
        }.bodyAsText()
        assertContains(crossUser, "run-scope-violation")
        assertFalse(crossUser.contains("Customer is an asserted class"))

        val plannedConversation = client.post("/api/v1/projects/simple/ai/conversations").json().at("/conversation/id").asText()
        val semanticallyPlanned = client.post("/api/v1/projects/simple/ai/conversations/$plannedConversation/messages") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "plan-1")
            setBody("""{"message":"Design the entire enterprise accounting ontology."}""")
        }.json()
        assertEquals("SEMANTIC_REQUEST", semanticallyPlanned.at("/intent").asText())
        assertEquals("READY_FOR_REVIEW", semanticallyPlanned.at("/run/status").asText())
        assertTrue(semanticallyPlanned.at("/draftId").asText().isNotBlank())

        val missingIdempotency = client.post("/api/v1/projects/simple/ai/conversations/$conversationId/messages") {
            contentType(ContentType.Application.Json)
            setBody("""{"message":"Explain Invoice."}""")
        }
        assertEquals(HttpStatusCode.BadRequest, missingIdempotency.status)
        assertContains(missingIdempotency.bodyAsText(), "missing-idempotency-key")
        assertEquals(HttpStatusCode.NotFound, client.post("/api/v1/projects/missing/ai/conversations").status)

        val legacy = client.post("/api/v1/projects/simple/ai/assistant") {
            contentType(ContentType.Application.Json)
            setBody("""{"operation":"EXPLAIN_ENTITY","entityIri":"https://example.com/entio/simple#Customer"}""")
        }
        assertEquals(HttpStatusCode.OK, legacy.status)
        assertContains(legacy.bodyAsText(), "legacy-compatible answer")

        val deleted = client.delete("/api/v1/projects/simple/ai/conversations/$conversationId")
        assertEquals(HttpStatusCode.OK, deleted.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/v1/projects/simple/ai/conversations/$conversationId").status)
    }

    private fun setup(provider: FakeAiProvider): TestSetup {
        val allowedRoot = Files.createTempDirectory("entio-ai-web-contract")
        val projectRoot = createFixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)
        val credentials: AiCredentialStore = InMemoryAiCredentialStore().also {
            it.save("alice", provider.providerId, "server-only-test-secret")
        }
        return TestSetup(
            projectRoot = projectRoot,
            sourceBefore = Files.readString(projectRoot.resolve("ontology/simple.ttl")),
            dependencies = WebApplicationDependencies(
                projectRegistry = registry,
                aiCredentials = credentials,
                aiProvider = provider,
                aiAssistant = provider,
                aiToolLoopProvider = provider,
                aiModelProvider = DevelopmentAiModelProviderClient(
                    models = listOf(AiProviderModelDescriptor("openai", "gpt-5.2")),
                    verificationByModelId = mapOf("gpt-5.2" to AiModelVerificationResult.Verified),
                ),
            ),
        )
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.configureModel() {
        val credential = client.put("/api/v1/ai/credentials") {
            contentType(ContentType.Application.Json)
            setBody("""{"providerId":"openai","apiKey":"server-only-test-secret"}""")
        }
        assertEquals(HttpStatusCode.OK, credential.status)
        val selected = client.put("/api/v1/ai/model-selection") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", "web-contract-model")
            setBody("""{"modelId":"gpt-5.2"}""")
        }
        assertEquals(HttpStatusCode.OK, selected.status, selected.bodyAsText())
    }

    private fun createFixture(allowedRoot: Path): Path {
        val root = Files.createDirectory(allowedRoot.resolve("simple"))
        val ontology = Files.createDirectories(root.resolve("ontology"))
        Files.writeString(
            root.resolve("entio.yaml"),
            """
                name: simple-ontology
                iriNamespace: https://example.com/entio/simple#
                ontologySources:
                  - id: simple
                    path: ontology/simple.ttl
                    format: turtle
            """.trimIndent(),
        )
        Files.writeString(
            ontology.resolve("simple.ttl"),
            """
                @prefix ex: <https://example.com/entio/simple#> .
                @prefix owl: <http://www.w3.org/2002/07/owl#> .
                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                ex:Account a owl:Class ; rdfs:label "Account" .
                ex:Customer a owl:Class ; rdfs:label "Customer" .
            """.trimIndent(),
        )
        return root
    }

    private fun completed(
        text: String = "",
        calls: List<OpenAiFunctionCall> = emptyList(),
    ): OpenAiResponsesResult = OpenAiResponsesResult.Completed(
        OpenAiCompletedResponse(
            responseId = null,
            text = text,
            functionCalls = calls,
            usage = OpenAiUsage(1, 1, 2),
            events = emptyList(),
        ),
    )

    private suspend fun io.ktor.client.statement.HttpResponse.json(): JsonNode = mapper.readTree(bodyAsText())

    private data class TestSetup(
        val projectRoot: Path,
        val sourceBefore: String,
        val dependencies: WebApplicationDependencies,
    )

    private class FakeAiProvider(vararg initialResponses: OpenAiResponsesResult) :
        AiProviderClient,
        AiAssistantProvider,
        AiToolLoopProvider {
        override val providerId: String = "openai"
        override val promptVersion: String = "phase-7-web-contract-test-v1"
        val toolRequests: MutableList<OpenAiResponsesRequest> = mutableListOf()
        private val responses: ArrayDeque<OpenAiResponsesResult> = ArrayDeque(initialResponses.toList())

        override suspend fun test(apiKey: String): AiProviderTestResult = AiProviderTestResult.Passed("Accepted")

        override suspend fun complete(apiKey: String, request: AiProviderRequest): AiProviderCompletion =
            AiProviderCompletion.Success(answer = "legacy-compatible answer")

        override suspend fun respond(
            apiKey: String,
            request: OpenAiResponsesRequest,
            onEvent: suspend (OpenAiProviderEvent) -> Unit,
        ): OpenAiResponsesResult {
            toolRequests += request
            return assertNotNull(responses.removeFirstOrNull(), "The fake provider received an unexpected request.")
        }
    }
}
