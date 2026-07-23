package com.entio.web

import com.entio.web.ai.AiProposalGenerationInput
import com.entio.web.ai.AiProposalGenerationResult
import com.entio.web.ai.AiProposalProvider
import com.entio.web.ai.AiResponseKind
import com.entio.web.ai.provider.DevelopmentAiModelProviderClient
import com.entio.web.ai.models.AiProviderModelDescriptor
import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebApplicationDependencies
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.delay

class AiProposalWorkflowTest {
    @Test
    fun duplicateEquivalentAiEditsBecomeOneStagedEdit(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-ai-duplicate-edits")
        val projectRoot = fixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot)).also { it.register("simple", "Simple", projectRoot) }
        val proposalProvider = FixtureProposalProvider()
        application {
            module(
                WebApplicationDependencies(
                    projectRegistry = registry,
                    aiModelProvider = DevelopmentAiModelProviderClient(
                        models = listOf(AiProviderModelDescriptor("openai", "gpt-test")),
                        verificationByModelId = mapOf("gpt-test" to com.entio.web.ai.provider.AiModelVerificationResult.Verified),
                    ),
                    aiProposalProvider = proposalProvider,
                ),
            )
        }
        client.put("/api/v1/ai/credentials") {
            contentType(ContentType.Application.Json)
            setBody("""{"providerId":"openai","apiKey":"test-secret"}""")
        }
        client.put("/api/v1/ai/model-selection") {
            headers.append("Idempotency-Key", "ai-duplicate-select")
            contentType(ContentType.Application.Json)
            setBody("""{"modelId":"gpt-test"}""")
        }

        val started = client.post("/api/v1/projects/simple/ai/proposals") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"duplicate-edits"}""")
        }
        val runId = Regex("\\\"runId\\\":\\\"([^\\\"]+)").find(started.bodyAsText())!!.groupValues[1]
        val ready = poll(client, runId)
        assertContains(ready, "READY")
        assertEquals(1, Regex("\\\"id\\\":\\\"duplicate-").findAll(ready).count())

        val staged = client.post("/api/v1/projects/simple/ai/proposals/$runId/stage")
        assertEquals(HttpStatusCode.OK, staged.status, staged.bodyAsText())
        val stagedBody = client.get("/api/v1/projects/simple/staged").bodyAsText()
        assertEquals(1, Regex("\\\"aiEditId\\\":").findAll(stagedBody).count())
        assertContains(stagedBody, "subjectLabel")
        assertContains(stagedBody, "Loan")
    }

    @Test
    fun noOpAiEditIsReturnedForRepair(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-ai-no-op")
        val projectRoot = fixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot)).also { it.register("simple", "Simple", projectRoot) }
        val proposalProvider = FixtureProposalProvider()
        application {
            module(
                WebApplicationDependencies(
                    projectRegistry = registry,
                    aiModelProvider = DevelopmentAiModelProviderClient(
                        models = listOf(AiProviderModelDescriptor("openai", "gpt-test")),
                        verificationByModelId = mapOf("gpt-test" to com.entio.web.ai.provider.AiModelVerificationResult.Verified),
                    ),
                    aiProposalProvider = proposalProvider,
                ),
            )
        }
        client.put("/api/v1/ai/credentials") {
            contentType(ContentType.Application.Json)
            setBody("""{"providerId":"openai","apiKey":"test-secret"}""")
        }
        client.put("/api/v1/ai/model-selection") {
            headers.append("Idempotency-Key", "ai-no-op-select")
            contentType(ContentType.Application.Json)
            setBody("""{"modelId":"gpt-test"}""")
        }

        val started = client.post("/api/v1/projects/simple/ai/proposals") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"noop"}""")
        }
        val runId = Regex("\\\"runId\\\":\\\"([^\\\"]+)").find(started.bodyAsText())!!.groupValues[1]
        val ready = poll(client, runId)
        assertContains(ready, "READY")
        assertContains(ready, "no-op")
        assertContains(ready, "Asking AI to diagnose and repair")
        assertContains(ready, "https://example.com/simple#Missing")
        assertContains(ready, "Repair action")
        assertContains(ready, "loan-class")
    }

    @Test
    fun unrepairedNoOpCannotBecomeReviewReady(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-ai-no-op-failure")
        val projectRoot = fixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot)).also { it.register("simple", "Simple", projectRoot) }
        application {
            module(
                WebApplicationDependencies(
                    projectRegistry = registry,
                    aiModelProvider = DevelopmentAiModelProviderClient(
                        models = listOf(AiProviderModelDescriptor("openai", "gpt-test")),
                        verificationByModelId = mapOf("gpt-test" to com.entio.web.ai.provider.AiModelVerificationResult.Verified),
                    ),
                    aiProposalProvider = FixtureProposalProvider(),
                ),
            )
        }
        client.put("/api/v1/ai/credentials") {
            contentType(ContentType.Application.Json)
            setBody("""{"providerId":"openai","apiKey":"test-secret"}""")
        }
        client.put("/api/v1/ai/model-selection") {
            headers.append("Idempotency-Key", "ai-no-op-failure-select")
            contentType(ContentType.Application.Json)
            setBody("""{"modelId":"gpt-test"}""")
        }

        val started = client.post("/api/v1/projects/simple/ai/proposals") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"noop-unrepairable"}""")
        }
        val runId = Regex("\\\"runId\\\":\\\"([^\\\"]+)").find(started.bodyAsText())!!.groupValues[1]
        val failed = poll(client, runId)
        assertContains(failed, "FAILED")
        assertFalse(failed.contains("Proposal ready for review with validation findings"))
        assertContains(failed, "no-op")
        assertContains(failed, "Deterministic validation found")
    }

    @Test
    fun modelCanAskForClarificationAfterValidationFindings(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-ai-repair-clarification")
        val projectRoot = fixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot)).also { it.register("simple", "Simple", projectRoot) }
        val proposalProvider = FixtureProposalProvider()
        application {
            module(
                WebApplicationDependencies(
                    projectRegistry = registry,
                    aiModelProvider = DevelopmentAiModelProviderClient(
                        models = listOf(AiProviderModelDescriptor("openai", "gpt-test")),
                        verificationByModelId = mapOf("gpt-test" to com.entio.web.ai.provider.AiModelVerificationResult.Verified),
                    ),
                    aiProposalProvider = proposalProvider,
                ),
            )
        }
        client.put("/api/v1/ai/credentials") {
            contentType(ContentType.Application.Json)
            setBody("""{"providerId":"openai","apiKey":"test-secret"}""")
        }
        client.put("/api/v1/ai/model-selection") {
            headers.append("Idempotency-Key", "ai-repair-clarification-select")
            contentType(ContentType.Application.Json)
            setBody("""{"modelId":"gpt-test"}""")
        }

        val started = client.post("/api/v1/projects/simple/ai/proposals") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"clarify-after-validation"}""")
        }
        val runId = Regex("\\\"runId\\\":\\\"([^\\\"]+)").find(started.bodyAsText())!!.groupValues[1]
        val ready = poll(client, runId)

        assertContains(ready, "\"status\":\"READY\"")
        assertContains(ready, "\"responseMode\":\"CLARIFICATION\"")
        assertContains(ready, "The ontology range requires Account")
        assertContains(ready, "Asking for clarification before preparing edits")
        assertEquals(2, proposalProvider.callCount)
    }

    @Test
    fun modelReviewsTrustedDraftInferencesBeforeProposalBecomesReady(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-ai-reasoning-review")
        val projectRoot = fixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot)).also { it.register("simple", "Simple", projectRoot) }
        val proposalProvider = FixtureProposalProvider()
        application {
            module(
                WebApplicationDependencies(
                    projectRegistry = registry,
                    aiModelProvider = DevelopmentAiModelProviderClient(
                        models = listOf(AiProviderModelDescriptor("openai", "gpt-test")),
                        verificationByModelId = mapOf("gpt-test" to com.entio.web.ai.provider.AiModelVerificationResult.Verified),
                    ),
                    aiProposalProvider = proposalProvider,
                ),
            )
        }
        client.put("/api/v1/ai/credentials") {
            contentType(ContentType.Application.Json)
            setBody("""{"providerId":"openai","apiKey":"test-secret"}""")
        }
        client.put("/api/v1/ai/model-selection") {
            headers.append("Idempotency-Key", "ai-reasoning-review-select")
            contentType(ContentType.Application.Json)
            setBody("""{"modelId":"gpt-test"}""")
        }

        val started = client.post("/api/v1/projects/simple/ai/proposals") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"reasoning-review"}""")
        }
        val runId = Regex("\\\"runId\\\":\\\"([^\\\"]+)").find(started.bodyAsText())!!.groupValues[1]
        val ready = poll(client, runId)

        assertContains(ready, "\"responseMode\":\"CLARIFICATION\"")
        assertContains(ready, "Checking Account 33271 will also be inferred as an Account")
        assertContains(ready, "Entio reasoning found new inferred consequences for AI review")
        assertEquals("reasoning", proposalProvider.lastInput?.repairMode)
        assertContains(proposalProvider.lastInput?.ontologyContext.orEmpty(), "TRUSTED ENTIO REASONING CONTEXT")
        assertContains(proposalProvider.lastInput?.ontologyContext.orEmpty(), "New inferred consequences introduced by the private draft")
        assertEquals(2, proposalProvider.callCount)
    }

    @Test
    fun changingInvalidRepairsStopAfterBoundedAttempts(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-ai-bounded-repair")
        val projectRoot = fixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot)).also { it.register("simple", "Simple", projectRoot) }
        val proposalProvider = FixtureProposalProvider()
        application {
            module(
                WebApplicationDependencies(
                    projectRegistry = registry,
                    aiModelProvider = DevelopmentAiModelProviderClient(
                        models = listOf(AiProviderModelDescriptor("openai", "gpt-test")),
                        verificationByModelId = mapOf("gpt-test" to com.entio.web.ai.provider.AiModelVerificationResult.Verified),
                    ),
                    aiProposalProvider = proposalProvider,
                ),
            )
        }
        client.put("/api/v1/ai/credentials") {
            contentType(ContentType.Application.Json)
            setBody("""{"providerId":"openai","apiKey":"test-secret"}""")
        }
        client.put("/api/v1/ai/model-selection") {
            headers.append("Idempotency-Key", "ai-bounded-repair-select")
            contentType(ContentType.Application.Json)
            setBody("""{"modelId":"gpt-test"}""")
        }

        val started = client.post("/api/v1/projects/simple/ai/proposals") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"varying-unrepairable"}""")
        }
        val runId = Regex("\\\"runId\\\":\\\"([^\\\"]+)").find(started.bodyAsText())!!.groupValues[1]
        val failed = poll(client, runId)

        assertContains(failed, "\"status\":\"FAILED\"")
        assertContains(failed, "Repair stopped with unresolved proposal findings")
        assertEquals(4, proposalProvider.callCount)
    }

    @Test
    fun privateProposalUsesGenericGraphEditsAndDoesNotWriteBeforeStage(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-ai-proposal")
        val projectRoot = fixture(allowedRoot)
        val source = projectRoot.resolve("ontology/simple.ttl")
        val before = Files.readString(source)
        val configBefore = Files.readString(projectRoot.resolve("entio.yaml"))
        val registry = InMemoryProjectRegistry(setOf(allowedRoot)).also { it.register("simple", "Simple", projectRoot) }
        val proposalProvider = FixtureProposalProvider()
        application {
            module(
                WebApplicationDependencies(
                    projectRegistry = registry,
                    aiModelProvider = DevelopmentAiModelProviderClient(
                        models = listOf(AiProviderModelDescriptor("openai", "gpt-test")),
                        verificationByModelId = mapOf("gpt-test" to com.entio.web.ai.provider.AiModelVerificationResult.Verified),
                    ),
                    aiProposalProvider = proposalProvider,
                ),
            )
        }

        client.put("/api/v1/ai/credentials") {
            contentType(ContentType.Application.Json)
            setBody("""{"providerId":"openai","apiKey":"test-secret"}""")
        }
        val selected = client.put("/api/v1/ai/model-selection") {
            headers.append("Idempotency-Key", "ai-select")
            contentType(ContentType.Application.Json)
            setBody("""{"modelId":"gpt-test"}""")
        }
        assertEquals(HttpStatusCode.OK, selected.status)

        val started = client.post("/api/v1/projects/simple/ai/proposals") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"Model a Loan class with a definition."}""")
        }
        assertEquals(HttpStatusCode.OK, started.status)
        val runId = Regex("\\\"runId\\\":\\\"([^\\\"]+)").find(started.bodyAsText())!!.groupValues[1]
        val ready = poll(client, runId)
        assertContains(ready, "READY")
        assertContains(ready, "Preparing the Entio project context")
        assertFalse(ready.contains("Searching FIBO"))
        val chats = client.get("/api/v1/projects/simple/ai/proposals")
        assertEquals(HttpStatusCode.OK, chats.status)
        assertContains(chats.bodyAsText(), "Model a Loan class with a definition.")
        assertContains(chats.bodyAsText(), runId)
        assertContains(ready, "subject")
        assertEquals(before, Files.readString(source))
        assertEquals(configBefore, Files.readString(projectRoot.resolve("entio.yaml")))
        assertContains(proposalProvider.lastInput?.ontologyContext.orEmpty(), "ENTIO TYPED ONTOLOGY CONTEXT")
        assertContains(proposalProvider.lastInput?.ontologyContext.orEmpty(), "ONTOLOGY ENGINEERING GUIDE")
        assertContains(proposalProvider.lastInput?.ontologyContext.orEmpty(), "sourceId=simple")
        assertContains(proposalProvider.lastInput?.ontologyContext.orEmpty(), "role: property")
        assertContains(proposalProvider.lastInput?.ontologyContext.orEmpty(), "preferred label:")
        assertContains(proposalProvider.lastInput?.ontologyContext.orEmpty(), "refer to entities by their preferred label")
        assertContains(proposalProvider.lastInput?.ontologyContext.orEmpty(), "declarations/types:")
        assertContains(proposalProvider.lastInput?.ontologyContext.orEmpty(), "asserted domains:")
        assertContains(proposalProvider.lastInput?.ontologyContext.orEmpty(), "rdfs:domain")
        assertContains(proposalProvider.lastInput?.ontologyContext.orEmpty(), "Treat deletion as dependency-aware graph cleanup")
        assertContains(proposalProvider.lastInput?.ontologyContext.orEmpty(), "SHACL node-shape structure")
        assertContains(proposalProvider.lastInput?.ontologyContext.orEmpty(), "`sh:targetClass` and `sh:property` belong to the node shape")
        assertContains(proposalProvider.lastInput?.ontologyContext.orEmpty(), "Semantic construction patterns are guidance, not a fixed plan")
        assertContains(proposalProvider.lastInput?.ontologyContext.orEmpty(), "A complete SHACL property constraint normally has")
        assertContains(proposalProvider.lastInput?.ontologyContext.orEmpty(), "Treat replacement as an IRI migration")

        val explanation = client.post("/api/v1/projects/simple/ai/proposals") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"explain" ,"runId":"$runId"}""")
        }
        assertEquals(HttpStatusCode.OK, explanation.status)
        val explained = poll(client, runId)
        assertContains(explained, "\"responseMode\":\"ANSWER\"")
        assertContains(explained, "rdfs:domain")
        assertContains(explained, "loan-class")
        assertContains(explained, "\"evidence\":[{\"subject\":\"https://example.com/simple#ownsAccount\"")

        val followUp = client.post("/api/v1/projects/simple/ai/proposals") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"Also add a loan property.","runId":"$runId"}""")
        }
        assertEquals(HttpStatusCode.OK, followUp.status)
        assertContains(followUp.bodyAsText(), "\"runId\":\"$runId\"")
        val continued = poll(client, runId)
        assertContains(continued, "Continuing the existing private proposal")
        assertContains(continued, "\"updates\":[{\"order\":1,\"message\":\"Continuing the existing private proposal")
        assertFalse(continued.contains("Answering from the ontology context"))
        assertContains(continued, "loan-class")
        assertContains(continued, "has-loan")
        assertContains(continued, "\"role\":\"user\"")
        assertTrue(proposalProvider.lastInput?.conversation.orEmpty().any { it.role == "user" && it.content.contains("Model a Loan class") })
        assertTrue(proposalProvider.lastInput?.conversation.orEmpty().any { it.role == "assistant" && it.content.contains("rdfs:domain") })
        assertContains(proposalProvider.lastInput?.currentProposal.orEmpty(), "loan-class")
        assertContains(proposalProvider.lastInput?.currentProposal.orEmpty(), "\"sourceId\":\"simple\"")
        assertContains(proposalProvider.lastInput?.ontologyContext.orEmpty(), "effective private draft")
        assertContains(proposalProvider.lastInput?.ontologyContext.orEmpty(), "<https://example.com/simple#Loan>")
        assertContains(proposalProvider.lastInput?.ontologyContext.orEmpty(), "role: class")

        val removed = client.post("/api/v1/projects/simple/ai/proposals/$runId/edits/loan-definition/remove")
        assertEquals(HttpStatusCode.OK, removed.status)
        assertContains(removed.bodyAsText(), "loan-class")
        assertFalse(removed.bodyAsText().contains("loan-definition"))

        val staged = client.post("/api/v1/projects/simple/ai/proposals/$runId/stage")
        assertEquals(HttpStatusCode.OK, staged.status, staged.bodyAsText())
        assertContains(staged.bodyAsText(), "STAGED")
        assertEquals(before, Files.readString(source))
        assertEquals(configBefore, Files.readString(projectRoot.resolve("entio.yaml")))
        val stagedBody = client.get("/api/v1/projects/simple/staged").bodyAsText()
        assertContains(stagedBody, "ai-graph-change")
        assertEquals(4, Regex("\\\"aiEditId\\\":").findAll(stagedBody).count())

        val sharedRejected = client.post("/api/v1/projects/simple/proposal/reject") {
            headers.append("X-Entio-User", "bob")
        }
        assertEquals(HttpStatusCode.OK, sharedRejected.status)
        val restored = client.get("/api/v1/projects/simple/ai/proposals/$runId")
        assertContains(restored.bodyAsText(), "\"status\":\"READY\"")
        assertContains(restored.bodyAsText(), "loan-class")
        assertContains(restored.bodyAsText(), "has-loan")

        val restaged = client.post("/api/v1/projects/simple/ai/proposals/$runId/stage")
        assertEquals(HttpStatusCode.OK, restaged.status, restaged.bodyAsText())
        assertContains(restaged.bodyAsText(), "\"status\":\"STAGED\"")
        assertContains(client.get("/api/v1/projects/simple/staged").bodyAsText(), "ai-graph-change")

        val rejectedAgain = client.post("/api/v1/projects/simple/proposal/reject") {
            headers.append("X-Entio-User", "bob")
        }
        assertEquals(HttpStatusCode.OK, rejectedAgain.status)

        val rejected = client.post("/api/v1/projects/simple/ai/proposals/$runId/reject")
        assertEquals(HttpStatusCode.OK, rejected.status)
        assertContains(rejected.bodyAsText(), "REJECTED")
        assertFalse(client.get("/api/v1/projects/simple/staged").bodyAsText().contains("ai-graph-change"))

        val answerOnly = client.post("/api/v1/projects/simple/ai/proposals") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"explain"}""")
        }
        val answerRunId = Regex("\\\"runId\\\":\\\"([^\\\"]+)").find(answerOnly.bodyAsText())!!.groupValues[1]
        val answer = poll(client, answerRunId)
        assertContains(answer, "\"responseMode\":\"ANSWER\"")
        assertContains(answer, "rdfs:domain")
        assertContains(answer, "\"evidence\":[{\"subject\":\"https://example.com/simple#ownsAccount\"")
        assertFalse(answer.contains("loan-class"))
        assertFalse(answer.contains("Proposal ready for review"))

        val clarificationOnly = client.post("/api/v1/projects/simple/ai/proposals") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"clarify"}""")
        }
        val clarificationRunId = Regex("\\\"runId\\\":\\\"([^\\\"]+)").find(clarificationOnly.bodyAsText())!!.groupValues[1]
        val clarification = poll(client, clarificationRunId)
        assertContains(clarification, "\"responseMode\":\"CLARIFICATION\"")
        assertContains(clarification, "Which ontology source")
        assertFalse(clarification.contains("loan-class"))

        val plainAnswerRequest = client.post("/api/v1/projects/simple/ai/proposals") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"no-evidence"}""")
        }
        val plainAnswerRunId = Regex("\\\"runId\\\":\\\"([^\\\"]+)").find(plainAnswerRequest.bodyAsText())!!.groupValues[1]
        val plainAnswer = poll(client, plainAnswerRunId)
        assertContains(plainAnswer, "\"responseMode\":\"ANSWER\"")
        assertContains(plainAnswer, "Customer as its domain")
        assertFalse(plainAnswer.contains("could not be parsed"))

        val glossary = client.post("/api/v1/projects/simple/ai/proposals") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"glossary"}""")
        }
        val glossaryRunId = Regex("\\\"runId\\\":\\\"([^\\\"]+)").find(glossary.bodyAsText())!!.groupValues[1]
        val glossaryReady = poll(client, glossaryRunId)
        assertContains(glossaryReady, "trusted-vocabulary")

        val slow = client.post("/api/v1/projects/simple/ai/proposals") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"slow"}""")
        }
        val slowRunId = Regex("\\\"runId\\\":\\\"([^\\\"]+)").find(slow.bodyAsText())!!.groupValues[1]
        val cancelled = client.post("/api/v1/projects/simple/ai/proposals/$slowRunId/cancel")
        assertContains(cancelled.bodyAsText(), "CANCELLED")
    }

    @Test
    fun followUpCanReviseAndRetractPrivateDraftEdits(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-ai-draft-revision")
        val projectRoot = fixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot)).also { it.register("simple", "Simple", projectRoot) }
        val proposalProvider = FixtureProposalProvider()
        application {
            module(
                WebApplicationDependencies(
                    projectRegistry = registry,
                    aiModelProvider = DevelopmentAiModelProviderClient(
                        models = listOf(AiProviderModelDescriptor("openai", "gpt-test")),
                        verificationByModelId = mapOf("gpt-test" to com.entio.web.ai.provider.AiModelVerificationResult.Verified),
                    ),
                    aiProposalProvider = proposalProvider,
                ),
            )
        }
        client.put("/api/v1/ai/credentials") {
            contentType(ContentType.Application.Json)
            setBody("""{"providerId":"openai","apiKey":"test-secret"}""")
        }
        client.put("/api/v1/ai/model-selection") {
            headers.append("Idempotency-Key", "ai-draft-revision-select")
            contentType(ContentType.Application.Json)
            setBody("""{"modelId":"gpt-test"}""")
        }

        val started = client.post("/api/v1/projects/simple/ai/proposals") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"draft-revision"}""")
        }
        val runId = Regex("\\\"runId\\\":\\\"([^\\\"]+)").find(started.bodyAsText())!!.groupValues[1]
        assertContains(poll(client, runId), "borrower-target")

        val followUp = client.post("/api/v1/projects/simple/ai/proposals") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"draft-revision-followup","runId":"$runId"}""")
        }
        assertEquals(HttpStatusCode.OK, followUp.status)
        val continued = poll(client, runId)

        assertContains(continued, "READY")
        assertContains(continued, "https://spec.edmcouncil.org/fibo/ontology/FBC/DebtAndEquities/Debt/Borrower")
        assertFalse(continued.contains("\"id\":\"borrower-class\""))
        assertFalse(continued.contains("https://example.com/simple#Borrower\""))
    }

    @Test
    fun deterministicValidationIsSentBackForPostGenerationRepair(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-ai-repair")
        val projectRoot = fixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot)).also { it.register("simple", "Simple", projectRoot) }
        val proposalProvider = FixtureProposalProvider()
        application {
            module(
                WebApplicationDependencies(
                    projectRegistry = registry,
                    aiModelProvider = DevelopmentAiModelProviderClient(
                        models = listOf(AiProviderModelDescriptor("openai", "gpt-test")),
                        verificationByModelId = mapOf("gpt-test" to com.entio.web.ai.provider.AiModelVerificationResult.Verified),
                    ),
                    aiProposalProvider = proposalProvider,
                ),
            )
        }

        client.put("/api/v1/ai/credentials") {
            contentType(ContentType.Application.Json)
            setBody("""{"providerId":"openai","apiKey":"test-secret"}""")
        }
        client.put("/api/v1/ai/model-selection") {
            headers.append("Idempotency-Key", "ai-repair-select")
            contentType(ContentType.Application.Json)
            setBody("""{"modelId":"gpt-test"}""")
        }

        val started = client.post("/api/v1/projects/simple/ai/proposals") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"repairable"}""")
        }
        assertEquals(HttpStatusCode.OK, started.status)
        val runId = Regex("\\\"runId\\\":\\\"([^\\\"]+)").find(started.bodyAsText())!!.groupValues[1]
        val ready = poll(client, runId)

        assertContains(ready, "READY")
        assertContains(ready, "interest-rate")
        assertContains(ready, "Asking AI to diagnose and repair")
        assertTrue(proposalProvider.callCount >= 2)
        val validationRepair = proposalProvider.inputs.first { input ->
            input.validationFindings.any { it.contains("property subject") }
        }
        assertTrue(validationRepair.repairAttempt >= 1)
        assertContains(validationRepair.validationFindings.joinToString("\n").lowercase(), "property subject")
        assertContains(validationRepair.validationFindings.joinToString("\n"), "source 'simple'")
        assertContains(validationRepair.validationFindings.joinToString("\n"), "Violation: <https://example.com/simple#Loan>")

        val explanation = client.post("/api/v1/projects/simple/ai/proposals") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"explain","runId":"$runId"}""")
        }
        assertEquals(HttpStatusCode.OK, explanation.status)
        val explained = poll(client, runId)
        assertContains(explained, "\"responseMode\":\"ANSWER\"")
        assertContains(explained, "interest-rate")
        val stagedAfterAnswer = client.post("/api/v1/projects/simple/ai/proposals/$runId/stage")
        assertEquals(HttpStatusCode.OK, stagedAfterAnswer.status)
        assertContains(stagedAfterAnswer.bodyAsText(), "STAGED")
    }

    @Test
    fun malformedShaclEditRepairPreservesTheCompleteProposal(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-ai-malformed-shacl")
        val projectRoot = fixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot)).also { it.register("simple", "Simple", projectRoot) }
        val proposalProvider = FixtureProposalProvider()
        application {
            module(
                WebApplicationDependencies(
                    projectRegistry = registry,
                    aiModelProvider = DevelopmentAiModelProviderClient(
                        models = listOf(AiProviderModelDescriptor("openai", "gpt-test")),
                        verificationByModelId = mapOf("gpt-test" to com.entio.web.ai.provider.AiModelVerificationResult.Verified),
                    ),
                    aiProposalProvider = proposalProvider,
                ),
            )
        }
        client.put("/api/v1/ai/credentials") {
            contentType(ContentType.Application.Json)
            setBody("""{"providerId":"openai","apiKey":"test-secret"}""")
        }
        client.put("/api/v1/ai/model-selection") {
            headers.append("Idempotency-Key", "ai-malformed-shacl-select")
            contentType(ContentType.Application.Json)
            setBody("""{"modelId":"gpt-test"}""")
        }

        val started = client.post("/api/v1/projects/simple/ai/proposals") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"malformed-shacl"}""")
        }
        val runId = Regex("\\\"runId\\\":\\\"([^\\\"]+)").find(started.bodyAsText())!!.groupValues[1]
        val ready = poll(client, runId)

        assertContains(ready, "READY")
        assertContains(ready, "customer-account-path")
        assertContains(ready, "borrower-class")
        assertContains(ready, "borrower-loan-min-count")
        assertTrue(proposalProvider.callCount >= 2)
        assertContains(proposalProvider.lastInput?.currentProposal.orEmpty(), "customer-target")
        assertContains(proposalProvider.lastInput?.currentProposal.orEmpty(), "borrower-class")
        assertContains(proposalProvider.lastInput?.validationFindings.orEmpty().joinToString(), "subject must be an absolute IRI")
    }

    @Test
    fun unknownModelSourceIdIsReturnedForRepair(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-ai-source-repair")
        val projectRoot = fixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot)).also { it.register("simple", "Simple", projectRoot) }
        val proposalProvider = FixtureProposalProvider()
        application {
            module(
                WebApplicationDependencies(
                    projectRegistry = registry,
                    aiModelProvider = DevelopmentAiModelProviderClient(
                        models = listOf(AiProviderModelDescriptor("openai", "gpt-test")),
                        verificationByModelId = mapOf("gpt-test" to com.entio.web.ai.provider.AiModelVerificationResult.Verified),
                    ),
                    aiProposalProvider = proposalProvider,
                ),
            )
        }
        client.put("/api/v1/ai/credentials") {
            contentType(ContentType.Application.Json)
            setBody("""{"providerId":"openai","apiKey":"test-secret"}""")
        }
        client.put("/api/v1/ai/model-selection") {
            headers.append("Idempotency-Key", "ai-source-repair-select")
            contentType(ContentType.Application.Json)
            setBody("""{"modelId":"gpt-test"}""")
        }

        val started = client.post("/api/v1/projects/simple/ai/proposals") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"invalid-source"}""")
        }
        val runId = Regex("\\\"runId\\\":\\\"([^\\\"]+)\\\"").find(started.bodyAsText())!!.groupValues[1]
        val ready = poll(client, runId)

        assertContains(ready, "READY")
        assertContains(ready, "loan-class")
        assertTrue(proposalProvider.callCount >= 2)
        assertContains(proposalProvider.lastInput?.validationFindings.orEmpty().joinToString(), "Allowed source IDs: simple")
    }

    private fun fixture(allowedRoot: Path): Path {
        val root = Files.createDirectory(allowedRoot.resolve("simple"))
        Files.createDirectories(root.resolve("ontology"))
        Files.writeString(root.resolve("entio.yaml"), """
            name: simple
            iriNamespace: https://example.com/simple#
            ontologySources:
              - id: simple
                path: ontology/simple.ttl
                format: turtle
        """.trimIndent())
        Files.writeString(root.resolve("ontology/simple.ttl"), """
            @prefix ex: <https://example.com/simple#> .
            @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            ex:Customer a owl:Class .
            ex:Account a owl:Class .
            ex:ownsAccount a rdf:Property ;
                rdfs:domain ex:Customer ;
                rdfs:range ex:Account .
        """.trimIndent())
        return root
    }

    private suspend fun poll(client: io.ktor.client.HttpClient, runId: String): String {
        repeat(80) {
            val body = client.get("/api/v1/projects/simple/ai/proposals/$runId").bodyAsText()
            val status = Regex("\\\"status\\\":\\\"([^\\\"]+)").find(body)?.groupValues?.get(1)
            if (status in setOf("READY", "FAILED", "CANCELLED")) return body
            Thread.sleep(25)
        }
        return client.get("/api/v1/projects/simple/ai/proposals/$runId").bodyAsText()
    }
}

private class FixtureProposalProvider : AiProposalProvider {
    override val providerId: String = "openai"
    @Volatile
    var lastInput: AiProposalGenerationInput? = null

    @Volatile
    var callCount: Int = 0

    val inputs: MutableList<AiProposalGenerationInput> = mutableListOf()

    override suspend fun route(apiKey: String, selectedModelId: String, input: AiProposalGenerationInput): AiResponseKind = when (input.userRequest) {
        "explain", "no-evidence", "glossary" -> AiResponseKind.Answer
        "clarify" -> AiResponseKind.Clarification
        else -> AiResponseKind.Proposal
    }

    override suspend fun generate(apiKey: String, selectedModelId: String, input: AiProposalGenerationInput): AiProposalGenerationResult {
        lastInput = input
        synchronized(inputs) { inputs += input }
        callCount += 1
        if (input.userRequest == "slow") delay(2_000)
        if (input.userRequest == "clarify-after-validation" && input.validationFindings.isEmpty()) {
            return AiProposalGenerationResult.Completed("""
                {"mode":"proposal","answer":"Drafted.","summary":"Conflicting account proposal","evidence":[],"edits":[
                  {"id":"invalid-range-object","sourceId":"simple","operation":"remove","subject":"https://example.com/simple#Missing","predicate":"http://www.w3.org/2000/01/rdf-schema#label","objectKind":"literal","objectValue":"Missing","summary":"Remove missing label","rationale":"Trigger repair for the clarification test."}
                ]}
            """.trimIndent())
        }
        if (input.userRequest == "clarify-after-validation") {
            return AiProposalGenerationResult.Completed(
                """{"mode":"clarification","answer":"The ontology range requires Account. May I continue with that consequence?","summary":"","evidence":[],"edits":[]}""",
            )
        }
        if (input.userRequest == "reasoning-review" && input.repairMode == "reasoning") {
            return AiProposalGenerationResult.Completed(
                """{"mode":"clarification","answer":"Checking Account 33271 will also be inferred as an Account. May I continue?","summary":"","evidence":[],"edits":[]}""",
            )
        }
        if (input.userRequest == "reasoning-review") {
            return AiProposalGenerationResult.Completed("""
                {"mode":"proposal","answer":"Drafted.","summary":"Create checking account","evidence":[],"edits":[
                  {"id":"checking-class","sourceId":"simple","operation":"add","subject":"https://example.com/simple#Checking","predicate":"http://www.w3.org/1999/02/22-rdf-syntax-ns#type","objectKind":"iri","objectValue":"http://www.w3.org/2002/07/owl#Class","summary":"Declare Checking Account","rationale":"Create the requested account subtype."},
                  {"id":"checking-label","sourceId":"simple","operation":"add","subject":"https://example.com/simple#Checking","predicate":"http://www.w3.org/2000/01/rdf-schema#label","objectKind":"literal","objectValue":"Checking Account","summary":"Label Checking Account","rationale":"Provide a readable label."},
                  {"id":"checking-definition","sourceId":"simple","operation":"add","subject":"https://example.com/simple#Checking","predicate":"http://www.w3.org/2004/02/skos/core#definition","objectKind":"literal","objectValue":"An account used for transactional deposits and withdrawals.","summary":"Define Checking Account","rationale":"Describe the subtype."},
                  {"id":"checking-parent","sourceId":"simple","operation":"add","subject":"https://example.com/simple#Checking","predicate":"http://www.w3.org/2000/01/rdf-schema#subClassOf","objectKind":"iri","objectValue":"https://example.com/simple#Account","summary":"Make Checking Account an Account","rationale":"Model the requested subtype."},
                  {"id":"checking-individual","sourceId":"simple","operation":"add","subject":"https://example.com/simple#CheckingAccount33271","predicate":"http://www.w3.org/1999/02/22-rdf-syntax-ns#type","objectKind":"iri","objectValue":"https://example.com/simple#Checking","summary":"Type Checking Account 33271","rationale":"Create the requested individual."}
                ]}
            """.trimIndent())
        }
        if (input.userRequest == "varying-unrepairable") {
            return AiProposalGenerationResult.Completed("""
                {"mode":"proposal","answer":"Attempt $callCount.","summary":"Invalid attempt $callCount","evidence":[],"edits":[
                  {"id":"invalid-$callCount","sourceId":"simple","operation":"remove","subject":"https://example.com/simple#Missing","predicate":"http://www.w3.org/2000/01/rdf-schema#label","objectKind":"literal","objectValue":"Missing $callCount","summary":"Remove missing label","rationale":"Exercise the bounded repair loop."}
                ]}
            """.trimIndent())
        }
        if (input.userRequest == "repairable" && input.validationFindings.isEmpty()) {
            return AiProposalGenerationResult.Completed("""
                {"mode":"proposal","summary":"Repairable Loan proposal","edits":[
                  {"id":"loan-class","sourceId":"simple","operation":"add","subject":"https://example.com/simple#Loan","predicate":"http://www.w3.org/1999/02/22-rdf-syntax-ns#type","objectKind":"iri","objectValue":"http://www.w3.org/2002/07/owl#Class","summary":"Create Loan class","rationale":"A new class is needed for the requested concept."},
                  {"id":"loan-domain-mistake","sourceId":"simple","operation":"add","subject":"https://example.com/simple#Loan","predicate":"http://www.w3.org/2000/01/rdf-schema#domain","objectKind":"iri","objectValue":"https://example.com/simple#Customer","summary":"Set an invalid domain","rationale":"This intentionally exercises semantic role validation."},
                  {"id":"loan-interest-use","sourceId":"simple","operation":"add","subject":"https://example.com/simple#Loan","predicate":"https://example.com/simple#interestRate","objectKind":"literal","objectValue":"4.5","datatype":"http://www.w3.org/2001/XMLSchema#decimal","summary":"Use interest rate","rationale":"The initial proposal intentionally exercises post-generation validation repair."}
                ]}
            """.trimIndent())
        }
        if (input.userRequest == "malformed-shacl" && input.validationFindings.isEmpty()) {
            return AiProposalGenerationResult.Completed("""
                {"mode":"proposal","summary":"Customer and Borrower constraints","edits":[
                  {"id":"customer-target","sourceId":"simple","operation":"add","subject":"https://example.com/simple#CustomerShape","predicate":"http://www.w3.org/ns/shacl#targetClass","objectKind":"iri","objectValue":"https://example.com/simple#Customer","summary":"Target Customer","rationale":"Validate Customer instances."},
                  {"id":"customer-property","sourceId":"simple","operation":"add","subject":"https://example.com/simple#CustomerShape","predicate":"http://www.w3.org/ns/shacl#property","objectKind":"iri","objectValue":"https://example.com/simple#CustomerAccountPropertyShape","summary":"Link account property shape","rationale":"Attach the account minimum constraint."},
                  {"id":"customer-account-path","sourceId":"simple","operation":"add","subject":"_:CustomerAccountPropertyShape","predicate":"http://www.w3.org/ns/shacl#path","objectKind":"iri","objectValue":"https://example.com/simple#ownsAccount","summary":"Set account path","rationale":"ownsAccount is the relevant property for account ownership."},
                  {"id":"borrower-class","sourceId":"simple","operation":"add","subject":"https://example.com/simple#Borrower","predicate":"http://www.w3.org/1999/02/22-rdf-syntax-ns#type","objectKind":"iri","objectValue":"http://www.w3.org/2002/07/owl#Class","summary":"Declare Borrower","rationale":"The request includes a Borrower class."},
                  {"id":"borrower-loan-min-count","sourceId":"simple","operation":"add","subject":"https://example.com/simple#BorrowerLoanPropertyShape","predicate":"http://www.w3.org/ns/shacl#minCount","objectKind":"literal","objectValue":"1","datatype":"http://www.w3.org/2001/XMLSchema#integer","summary":"Require one loan","rationale":"Borrowers must have at least one loan."}
                ]}
            """.trimIndent())
        }
        if (input.userRequest == "draft-revision") {
            return AiProposalGenerationResult.Completed("""
                {"mode":"proposal","summary":"Local Borrower draft","edits":[
                  {"id":"borrower-class","sourceId":"simple","operation":"add","subject":"https://example.com/simple#Borrower","predicate":"http://www.w3.org/1999/02/22-rdf-syntax-ns#type","objectKind":"iri","objectValue":"http://www.w3.org/2002/07/owl#Class","summary":"Declare local Borrower","rationale":"Initial draft for the borrower concept."},
                  {"id":"borrower-target","sourceId":"simple","operation":"add","subject":"https://example.com/simple#BorrowerShape","predicate":"http://www.w3.org/ns/shacl#targetClass","objectKind":"iri","objectValue":"https://example.com/simple#Borrower","summary":"Target local Borrower","rationale":"Initial shape target."}
                ]}
            """.trimIndent())
        }
        if (input.userRequest == "draft-revision-followup") {
            return AiProposalGenerationResult.Completed("""
                {"mode":"proposal","summary":"FIBO Borrower draft","edits":[
                  {"id":"borrower-target","sourceId":"simple","operation":"add","subject":"https://example.com/simple#BorrowerShape","predicate":"http://www.w3.org/ns/shacl#targetClass","objectKind":"iri","objectValue":"https://spec.edmcouncil.org/fibo/ontology/FBC/DebtAndEquities/Debt/Borrower","summary":"Target FIBO Borrower","rationale":"Use the existing FIBO Borrower concept."},
                  {"id":"remove-local-borrower","sourceId":"simple","operation":"remove","subject":"https://example.com/simple#Borrower","predicate":"http://www.w3.org/1999/02/22-rdf-syntax-ns#type","objectKind":"iri","objectValue":"http://www.w3.org/2002/07/owl#Class","summary":"Retract local Borrower","rationale":"The local class is replaced by FIBO Borrower."}
                ]}
            """.trimIndent())
        }
        if (input.userRequest == "malformed-shacl") {
            return AiProposalGenerationResult.Completed("""
                {"mode":"proposal","summary":"Customer and Borrower constraints","edits":[
                  {"id":"customer-target","sourceId":"simple","operation":"add","subject":"https://example.com/simple#CustomerShape","predicate":"http://www.w3.org/ns/shacl#targetClass","objectKind":"iri","objectValue":"https://example.com/simple#Customer","summary":"Target Customer","rationale":"Validate Customer instances."},
                  {"id":"customer-property","sourceId":"simple","operation":"add","subject":"https://example.com/simple#CustomerShape","predicate":"http://www.w3.org/ns/shacl#property","objectKind":"iri","objectValue":"https://example.com/simple#CustomerAccountPropertyShape","summary":"Link account property shape","rationale":"Attach the account minimum constraint."},
                  {"id":"customer-account-path","sourceId":"simple","operation":"add","subject":"https://example.com/simple#CustomerAccountPropertyShape","predicate":"http://www.w3.org/ns/shacl#path","objectKind":"iri","objectValue":"https://example.com/simple#ownsAccount","summary":"Set account path","rationale":"ownsAccount is the relevant property for account ownership."},
                  {"id":"customer-account-min-count","sourceId":"simple","operation":"add","subject":"https://example.com/simple#CustomerAccountPropertyShape","predicate":"http://www.w3.org/ns/shacl#minCount","objectKind":"literal","objectValue":"1","datatype":"http://www.w3.org/2001/XMLSchema#integer","summary":"Require one account","rationale":"Customers must have at least one account."},
                  {"id":"borrower-class","sourceId":"simple","operation":"add","subject":"https://example.com/simple#Borrower","predicate":"http://www.w3.org/1999/02/22-rdf-syntax-ns#type","objectKind":"iri","objectValue":"http://www.w3.org/2002/07/owl#Class","summary":"Declare Borrower","rationale":"The request includes a Borrower class."},
                  {"id":"borrower-loan-min-count","sourceId":"simple","operation":"add","subject":"https://example.com/simple#BorrowerLoanPropertyShape","predicate":"http://www.w3.org/ns/shacl#minCount","objectKind":"literal","objectValue":"1","datatype":"http://www.w3.org/2001/XMLSchema#integer","summary":"Require one loan","rationale":"Borrowers must have at least one loan."}
                ]}
            """.trimIndent())
        }
        if (input.userRequest == "duplicate-edits") {
            return AiProposalGenerationResult.Completed("""
                {"mode":"proposal","summary":"Duplicate edit proposal","edits":[
                  {"id":"duplicate-one","sourceId":"simple","operation":"add","subject":"https://example.com/simple#Loan","predicate":"http://www.w3.org/1999/02/22-rdf-syntax-ns#type","objectKind":"iri","objectValue":"http://www.w3.org/2002/07/owl#Class","summary":"Create Loan class","rationale":"Create the requested class."},
                  {"id":"duplicate-two","sourceId":"simple","operation":"add","subject":"https://example.com/simple#Loan","predicate":"http://www.w3.org/1999/02/22-rdf-syntax-ns#type","objectKind":"iri","objectValue":"http://www.w3.org/2002/07/owl#Class","summary":"Create Loan class again","rationale":"This duplicate should be removed."}
                ]}
            """.trimIndent())
        }
        if (input.userRequest == "noop" && input.validationFindings.isEmpty()) {
            return AiProposalGenerationResult.Completed("""
                {"mode":"proposal","summary":"No-op proposal","edits":[
                  {"id":"noop-edit","sourceId":"simple","operation":"remove","subject":"https://example.com/simple#Missing","predicate":"http://www.w3.org/2000/01/rdf-schema#label","objectKind":"literal","objectValue":"Missing","summary":"Remove missing label","rationale":"This intentionally exercises no-op repair."}
                ]}
            """.trimIndent())
        }
        if (input.userRequest == "noop-unrepairable") {
            return AiProposalGenerationResult.Completed("""
                {"mode":"proposal","summary":"Unrepairable no-op proposal","edits":[
                  {"id":"noop-edit","sourceId":"simple","operation":"remove","subject":"https://example.com/simple#Missing","predicate":"http://www.w3.org/2000/01/rdf-schema#label","objectKind":"literal","objectValue":"Missing","summary":"Remove missing label","rationale":"This intentionally remains invalid."}
                ]}
            """.trimIndent())
        }
        if (input.userRequest == "noop") {
            return AiProposalGenerationResult.Completed("""
                {"mode":"proposal","summary":"Repaired proposal","edits":[
                  {"id":"loan-class","sourceId":"simple","operation":"add","subject":"https://example.com/simple#Loan","predicate":"http://www.w3.org/1999/02/22-rdf-syntax-ns#type","objectKind":"iri","objectValue":"http://www.w3.org/2002/07/owl#Class","summary":"Create Loan class","rationale":"The repaired edit changes the graph."}
                ]}
            """.trimIndent())
        }
        if (input.userRequest == "invalid-source" && input.validationFindings.isEmpty()) {
            return AiProposalGenerationResult.Completed("""
                {"mode":"proposal","summary":"Loan proposal","edits":[
                  {"id":"loan-class","sourceId":"user-request","operation":"add","subject":"https://example.com/simple#Loan","predicate":"http://www.w3.org/1999/02/22-rdf-syntax-ns#type","objectKind":"iri","objectValue":"http://www.w3.org/2002/07/owl#Class","summary":"Create Loan class","rationale":"Model the requested class."}
                ]}
            """.trimIndent())
        }
        if (input.userRequest == "invalid-source") {
            return AiProposalGenerationResult.Completed("""
                {"mode":"proposal","summary":"Loan proposal","edits":[
                  {"id":"loan-class","sourceId":"simple","operation":"add","subject":"https://example.com/simple#Loan","predicate":"http://www.w3.org/1999/02/22-rdf-syntax-ns#type","objectKind":"iri","objectValue":"http://www.w3.org/2002/07/owl#Class","summary":"Create Loan class","rationale":"Model the requested class in the available ontology source."}
                ]}
            """.trimIndent())
        }
        if (input.userRequest == "repairable") {
            return AiProposalGenerationResult.Completed("""
                {"mode":"proposal","summary":"Repaired Loan proposal","edits":[
                  {"id":"loan-class","sourceId":"simple","operation":"add","subject":"https://example.com/simple#Loan","predicate":"http://www.w3.org/1999/02/22-rdf-syntax-ns#type","objectKind":"iri","objectValue":"http://www.w3.org/2002/07/owl#Class","summary":"Create Loan class","rationale":"A new class is needed for the requested concept."},
                  {"id":"interest-rate","sourceId":"simple","operation":"add","subject":"https://example.com/simple#interestRate","predicate":"http://www.w3.org/1999/02/22-rdf-syntax-ns#type","objectKind":"iri","objectValue":"http://www.w3.org/2002/07/owl#DatatypeProperty","summary":"Declare interest rate property","rationale":"Declare the property before using it."},
                  {"id":"interest-rate-domain","sourceId":"simple","operation":"add","subject":"https://example.com/simple#interestRate","predicate":"http://www.w3.org/2000/01/rdf-schema#domain","objectKind":"iri","objectValue":"https://example.com/simple#Loan","summary":"Set interest rate domain","rationale":"Interest rate applies to loans."},
                  {"id":"interest-rate-range","sourceId":"simple","operation":"add","subject":"https://example.com/simple#interestRate","predicate":"http://www.w3.org/2000/01/rdf-schema#range","objectKind":"iri","objectValue":"http://www.w3.org/2001/XMLSchema#decimal","summary":"Set interest rate range","rationale":"Interest rate values are decimal numbers."},
                  {"id":"loan-interest-use","sourceId":"simple","operation":"add","subject":"https://example.com/simple#Loan","predicate":"https://example.com/simple#interestRate","objectKind":"literal","objectValue":"4.5","datatype":"http://www.w3.org/2001/XMLSchema#decimal","summary":"Use interest rate","rationale":"The repaired proposal includes the declaration and its use."}
                ]}
            """.trimIndent())
        }
        if (input.userRequest == "explain") {
            return AiProposalGenerationResult.Completed("""
                {"mode":"answer","answer":"rdfs:domain states which class of resources may be subjects of a property. For ownsAccount, the domain triple has the property as its subject: ownsAccount rdfs:domain Customer. It does not make a class the property.","evidence":[{"subject":"https://example.com/simple#ownsAccount","predicate":"http://www.w3.org/2000/01/rdf-schema#domain","objectKind":"iri","objectValue":"https://example.com/simple#Customer","source":"current-ontology"}],"edits":[]}
            """.trimIndent())
        }
        if (input.userRequest == "no-evidence") {
            return AiProposalGenerationResult.Completed("The property uses Customer as its domain.")
        }
        if (input.userRequest == "glossary") {
            return AiProposalGenerationResult.Completed("""
                {"mode":"answer","answer":"rdfs:domain identifies the class of possible subjects for a property.","evidence":[{"subject":"http://www.w3.org/2000/01/rdf-schema#domain","predicate":"http://www.w3.org/2000/01/rdf-schema#comment","objectKind":"literal","objectValue":"rdfs:domain identifies the class of possible subjects for a property.","source":"trusted-vocabulary"}],"edits":[]}
            """.trimIndent())
        }
        if (input.userRequest == "clarify") {
            return AiProposalGenerationResult.Completed("""
                {"mode":"clarification","answer":"Which ontology source should contain the requested change?","edits":[]}
            """.trimIndent())
        }
        if (input.userRequest == "Also add a loan property.") {
            return AiProposalGenerationResult.Completed("""
                {"mode":"proposal","summary":"Add customer loan ownership","edits":[
                  {"id":"has-loan","sourceId":"simple","operation":"add","subject":"https://example.com/simple#hasLoan","predicate":"http://www.w3.org/1999/02/22-rdf-syntax-ns#type","objectKind":"iri","objectValue":"http://www.w3.org/2002/07/owl#ObjectProperty","summary":"Declare hasLoan property","rationale":"Link customers to their loans."},
                  {"id":"has-loan-domain","sourceId":"simple","operation":"add","subject":"https://example.com/simple#hasLoan","predicate":"http://www.w3.org/2000/01/rdf-schema#domain","objectKind":"iri","objectValue":"https://example.com/simple#Customer","summary":"Set hasLoan domain","rationale":"Customers own loans."},
                  {"id":"has-loan-range","sourceId":"simple","operation":"add","subject":"https://example.com/simple#hasLoan","predicate":"http://www.w3.org/2000/01/rdf-schema#range","objectKind":"iri","objectValue":"https://example.com/simple#Loan","summary":"Set hasLoan range","rationale":"The property points to Loan instances."}
                ]}
            """.trimIndent())
        }
        return AiProposalGenerationResult.Completed("""
            {"mode":"proposal","summary":"Loan proposal","edits":[
              {"id":"loan-class","sourceId":"simple","operation":"add","subject":"https://example.com/simple#Loan","predicate":"http://www.w3.org/1999/02/22-rdf-syntax-ns#type","objectKind":"iri","objectValue":"http://www.w3.org/2002/07/owl#Class","summary":"Create Loan class","rationale":"A new class is needed for the requested concept."},
              {"id":"loan-definition","sourceId":"simple","operation":"add","subject":"https://example.com/simple#Loan","predicate":"http://www.w3.org/2000/01/rdf-schema#label","objectKind":"literal","objectValue":"Loan","summary":"Label Loan","rationale":"Give the class a human-readable label."}
            ]}
        """.trimIndent())
    }
}
