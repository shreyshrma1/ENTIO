package com.entio.web

import com.entio.web.ai.AiProposalGenerationInput
import com.entio.web.ai.AiProposalGenerationResult
import com.entio.web.ai.AiProposalProvider
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
        assertContains(ready, "Searching ontology")
        assertContains(ready, "subject")
        assertEquals(before, Files.readString(source))
        assertEquals(configBefore, Files.readString(projectRoot.resolve("entio.yaml")))
        assertContains(proposalProvider.lastInput?.ontologyContext.orEmpty(), "ENTIO TYPED ONTOLOGY CONTEXT")
        assertContains(proposalProvider.lastInput?.ontologyContext.orEmpty(), "TRUSTED VOCABULARY GLOSSARY")
        assertContains(proposalProvider.lastInput?.ontologyContext.orEmpty(), "rdfs:domain")

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
        assertContains(continued, "\"role\":\"user\"")
        assertContains(proposalProvider.lastInput?.conversationContext.orEmpty(), "Model a Loan class")
        assertContains(proposalProvider.lastInput?.currentProposal.orEmpty(), "loan-class")

        val removed = client.post("/api/v1/projects/simple/ai/proposals/$runId/edits/loan-definition/remove")
        assertEquals(HttpStatusCode.OK, removed.status)
        assertContains(removed.bodyAsText(), "loan-class")
        assertFalse(removed.bodyAsText().contains("loan-definition"))

        val staged = client.post("/api/v1/projects/simple/ai/proposals/$runId/stage")
        assertEquals(HttpStatusCode.OK, staged.status, staged.bodyAsText())
        assertContains(staged.bodyAsText(), "STAGED")
        assertEquals(before, Files.readString(source))
        assertEquals(configBefore, Files.readString(projectRoot.resolve("entio.yaml")))
        assertContains(client.get("/api/v1/projects/simple/staged").bodyAsText(), "ai-graph-change")

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

        val evidenceRepair = client.post("/api/v1/projects/simple/ai/proposals") {
            contentType(ContentType.Application.Json)
            setBody("""{"prompt":"no-evidence"}""")
        }
        val evidenceRepairRunId = Regex("\\\"runId\\\":\\\"([^\\\"]+)").find(evidenceRepair.bodyAsText())!!.groupValues[1]
        val evidenceReady = poll(client, evidenceRepairRunId)
        assertContains(evidenceReady, "The answer needs a verifiable ontology citation")
        assertContains(evidenceReady, "\"evidence\":[{\"subject\":\"https://example.com/simple#ownsAccount\"")

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
        assertTrue(proposalProvider.lastInput?.repairAttempt ?: 0 >= 1)
        assertTrue(proposalProvider.lastInput?.validationFindings.orEmpty().isNotEmpty())
        assertContains(proposalProvider.lastInput?.validationFindings.orEmpty().joinToString("\n").lowercase(), "property subject")

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

    override suspend fun generate(apiKey: String, selectedModelId: String, input: AiProposalGenerationInput): AiProposalGenerationResult {
        lastInput = input
        callCount += 1
        if (input.userRequest == "slow") delay(2_000)
        if (input.userRequest == "repairable" && input.validationFindings.isEmpty()) {
            return AiProposalGenerationResult.Completed("""
                {"mode":"proposal","summary":"Repairable Loan proposal","edits":[
                  {"id":"loan-class","sourceId":"simple","operation":"add","subject":"https://example.com/simple#Loan","predicate":"http://www.w3.org/1999/02/22-rdf-syntax-ns#type","objectKind":"iri","objectValue":"http://www.w3.org/2002/07/owl#Class","summary":"Create Loan class","rationale":"A new class is needed for the requested concept."},
                  {"id":"loan-domain-mistake","sourceId":"simple","operation":"add","subject":"https://example.com/simple#Loan","predicate":"http://www.w3.org/2000/01/rdf-schema#domain","objectKind":"iri","objectValue":"https://example.com/simple#Customer","summary":"Set an invalid domain","rationale":"This intentionally exercises semantic role validation."},
                  {"id":"loan-interest-use","sourceId":"simple","operation":"add","subject":"https://example.com/simple#Loan","predicate":"https://example.com/simple#interestRate","objectKind":"literal","objectValue":"4.5","datatype":"http://www.w3.org/2001/XMLSchema#decimal","summary":"Use interest rate","rationale":"The initial proposal intentionally exercises post-generation validation repair."}
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
            return if (input.repairMode == "answer") {
                AiProposalGenerationResult.Completed("""
                    {"mode":"answer","answer":"The property uses Customer as its domain.","evidence":[{"subject":"https://example.com/simple#ownsAccount","predicate":"http://www.w3.org/2000/01/rdf-schema#domain","objectKind":"iri","objectValue":"https://example.com/simple#Customer"}],"edits":[]}
                """.trimIndent())
            } else {
                AiProposalGenerationResult.Completed("""{"mode":"answer","answer":"The property uses Customer as its domain.","edits":[]}""")
            }
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
        return AiProposalGenerationResult.Completed("""
            {"mode":"proposal","summary":"Loan proposal","edits":[
              {"id":"loan-class","sourceId":"simple","operation":"add","subject":"https://example.com/simple#Loan","predicate":"http://www.w3.org/1999/02/22-rdf-syntax-ns#type","objectKind":"iri","objectValue":"http://www.w3.org/2002/07/owl#Class","summary":"Create Loan class","rationale":"A new class is needed for the requested concept."},
              {"id":"loan-definition","sourceId":"simple","operation":"add","subject":"https://example.com/simple#Loan","predicate":"http://www.w3.org/2000/01/rdf-schema#label","objectKind":"literal","objectValue":"Loan","summary":"Label Loan","rationale":"Give the class a human-readable label."}
            ]}
        """.trimIndent())
    }
}
