package com.entio.web.ai

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class OpenAiProposalClientTest {
    @Test
    fun asksTheModelForFocusedExternalContextWhenNeeded(): Unit = runBlocking {
        var requestBody = ""
        val engine = MockEngine { request ->
            requestBody = (request.body as TextContent).text
            respond(
                """{"output_text":"{\"useFibo\":true,\"query\":\"financial account, transaction\"}"}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val configuration = OpenAiProposalConfiguration(maxRetries = 0)
        val client = OpenAiProposalClient(configuration, createProposalHttpClient(configuration, engine))

        val request = client.use {
            it.requestExternalContext("secret", "gpt-test", AiProposalGenerationInput("Find FIBO concepts.", "ontology", ""))
        }

        assertTrue(request.useFibo)
        assertEquals("financial account, transaction", request.query)
        val root = ObjectMapper().readTree(requestBody)
        assertEquals("entio_external_context_request", root.path("text").path("format").path("name").asText())
        assertTrue(root.path("instructions").asText().contains("concise list"))
    }

    @Test
    fun fallsBackThroughJsonModeWhenResponseFormatsAreRejected(): Unit = runBlocking {
        val requestBodies = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestBodies += (request.body as TextContent).text
            if (requestBodies.size < 3) {
                respond(
                    """{"error":{"message":"Schema is not supported by this model."}}""",
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            } else {
                respond(
                    """{"output_text":"{\"mode\":\"proposal\",\"answer\":\"Generated.\",\"summary\":\"Loan\",\"evidence\":[],\"edits\":[]}"}""",
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        }
        val configuration = OpenAiProposalConfiguration(maxRetries = 0)
        val client = OpenAiProposalClient(configuration, createProposalHttpClient(configuration, engine))

        val result = client.use {
            it.generate(
                "secret",
                "gpt-test",
                AiProposalGenerationInput("Model a Loan.", "context", "", responseKind = AiResponseKind.Proposal),
            )
        }

        assertIs<AiProposalGenerationResult.Completed>(result)
        val proposalInstructions = ObjectMapper().readTree(requestBodies[0]).path("instructions").asText()
        assertTrue(proposalInstructions.contains("serialization only"))
        assertTrue(proposalInstructions.contains("one RDF triple in each edit"))
        assertTrue(proposalInstructions.contains("Never use compact names"))
        assertTrue(proposalInstructions.contains("answer every question directly"))
        assertTrue(proposalInstructions.contains("distinguish the node shape from its property shapes"))
        assertTrue(proposalInstructions.contains("Do not substitute a similarly labeled property-shape IRI"))
        assertEquals("json_schema", ObjectMapper().readTree(requestBodies[0]).path("text").path("format").path("type").asText())
        assertEquals("json_object", ObjectMapper().readTree(requestBodies[1]).path("text").path("format").path("type").asText())
        assertTrue(ObjectMapper().readTree(requestBodies[2]).path("text").isMissingNode)
    }

    @Test
    fun routesOntologyOutcomeBeforeGeneratingIt(): Unit = runBlocking {
        var requestBody = ""
        val engine = MockEngine { request ->
            requestBody = (request.body as TextContent).text
            respond(
                """{"output_text":"{\"responseKind\":\"proposal\"}"}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val configuration = OpenAiProposalConfiguration(maxRetries = 0)
        val client = OpenAiProposalClient(configuration, createProposalHttpClient(configuration, engine))

        val kind = client.use {
            it.route(
                "secret",
                "gpt-test",
                AiProposalGenerationInput("Model a Loan class.", "project context", ""),
            )
        }

        assertEquals(AiResponseKind.Proposal, kind)
        val root = ObjectMapper().readTree(requestBody)
        assertTrue(root.path("instructions").asText().contains("answer", ignoreCase = false))
        assertTrue(root.path("instructions").asText().contains("clarification", ignoreCase = false))
        assertTrue(root.path("instructions").asText().contains("proposal", ignoreCase = false))
        assertTrue(root.path("instructions").asText().contains("{\"responseKind\":\"answer\"}"))
        assertTrue(root.path("instructions").asText().contains("Use `clarification`, never `clarify`."))
        assertTrue(root.path("instructions").asText().contains("questions about the current ontology or private proposal"))
        assertTrue(root.path("instructions").asText().contains("does not by itself make a message a proposal request"))
        assertTrue(root.path("instructions").asText().contains("authoritative source of truth"))
        assertTrue(root.path("instructions").asText().contains("inaccurate, conflicting, or materially incomplete"))
        assertTrue(root.path("instructions").asText().contains("These are reasoning instructions, not fixed classifications imposed by Entio"))
        assertEquals("json_schema", root.path("text").path("format").path("type").asText())
        assertEquals("entio_response_route", root.path("text").path("format").path("name").asText())
        assertEquals("Model a Loan class.", root.path("input").last().path("content").asText())
    }

    @Test
    fun sendsProjectContextAndConversationAsNativeMessages(): Unit = runBlocking {
        var requestBody = ""
        val engine = MockEngine { request ->
            requestBody = (request.body as TextContent).text
            respond(
                """{"output_text":"{\"mode\":\"answer\",\"answer\":\"Corrected answer\",\"evidence\":[],\"edits\":[]}"}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val configuration = OpenAiProposalConfiguration(maxRetries = 0)
        val client = OpenAiProposalClient(configuration, createProposalHttpClient(configuration, engine))

        val result = client.use {
            it.generate(
                "secret",
                "gpt-test",
                AiProposalGenerationInput(
                    userRequest = "Wouldn't declaration use rdf:type?",
                    ontologyContext = "ENTIO TYPED ONTOLOGY CONTEXT",
                    fiboContext = "",
                    conversation = listOf(
                        AiConversationTurn("user", "What does rdfs:domain mean?"),
                        AiConversationTurn("assistant", "The previous incorrect answer."),
                    ),
                ),
            )
        }

        assertIs<AiProposalGenerationResult.Completed>(result)
        val root = ObjectMapper().readTree(requestBody)
        assertTrue(root.path("instructions").asText().contains("general-purpose assistant"))
        assertEquals(false, root.path("store").asBoolean())
        val messages = root.path("input")
        assertEquals(listOf("developer", "user", "assistant", "user"), messages.map { it.path("role").asText() })
        assertTrue(messages[0].path("content").asText().contains("ENTIO TYPED ONTOLOGY CONTEXT"))
        assertTrue(messages[0].path("content").asText().contains("FIBO ACCESS BOUNDARY"))
        assertEquals("Wouldn't declaration use rdf:type?", messages[3].path("content").asText())
        assertFalse(requestBody.contains("CONVERSATION CONTEXT:"))
    }

    @Test
    fun explainsHowToRepairConflictingDomainAndRangeAxioms(): Unit = runBlocking {
        var requestBody = ""
        val engine = MockEngine { request ->
            requestBody = (request.body as TextContent).text
            respond(
                """{"output_text":"{\"mode\":\"proposal\",\"answer\":\"Repaired.\",\"summary\":\"Repair\",\"evidence\":[],\"edits\":[]}"}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val configuration = OpenAiProposalConfiguration(maxRetries = 0)
        val client = OpenAiProposalClient(configuration, createProposalHttpClient(configuration, engine))

        val result = client.use {
            it.generate(
                "secret",
                "gpt-test",
                AiProposalGenerationInput(
                    userRequest = "Repair hasLoan.",
                    ontologyContext = "hasLoan has declared range Loan.",
                    fiboContext = "",
                    currentProposal = "{\"edits\":[]}",
                    validationFindings = listOf("Object Customer is not an instance of the declared range Loan for property hasLoan."),
                    repairAttempt = 1,
                    repairMode = "proposal",
                    responseKind = AiResponseKind.Proposal,
                ),
            )
        }

        assertIs<AiProposalGenerationResult.Completed>(result)
        val root = ObjectMapper().readTree(requestBody)
        val instructions = root.path("input").last().path("content").asText()
        assertTrue(instructions.contains("remove the old conflicting triple"))
        assertTrue(instructions.contains("asserted triple"))
        assertTrue(instructions.contains("prior private proposal"))
        assertTrue(instructions.contains("do not treat every finding as permission"))
        assertTrue(instructions.contains("evidence, not as authority to override user intent"))
    }

    @Test
    fun givesTheModelOntologyGroundedClarificationInstructions(): Unit = runBlocking {
        var requestBody = ""
        val engine = MockEngine { request ->
            requestBody = (request.body as TextContent).text
            respond(
                """{"output_text":"Checking Account is a subclass of Account. May I continue?"}""",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val configuration = OpenAiProposalConfiguration(maxRetries = 0)
        val client = OpenAiProposalClient(configuration, createProposalHttpClient(configuration, engine))

        val result = client.use {
            it.generate(
                "secret",
                "gpt-test",
                AiProposalGenerationInput(
                    userRequest = "It should be a Checking Account, not an Account.",
                    ontologyContext = "Checking Account is a subclass of Account. owns account has range Account.",
                    fiboContext = "",
                    responseKind = AiResponseKind.Clarification,
                ),
            )
        }

        assertIs<AiProposalGenerationResult.Completed>(result)
        val instructions = ObjectMapper().readTree(requestBody).path("instructions").asText()
        assertTrue(instructions.contains("authoritative source of truth"))
        assertTrue(instructions.contains("asserted-versus-inferred"))
        assertTrue(instructions.contains("request permission to proceed"))
        assertTrue(instructions.contains("Do not prepare edits"))
    }
}
