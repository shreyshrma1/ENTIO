package com.entio.web.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import java.io.IOException
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay

public data class OpenAiProposalConfiguration(
    val providerId: String = "openai",
    val endpoint: String = "https://api.openai.com/v1/responses",
    val connectTimeoutMillis: Long = 10_000,
    val requestTimeoutMillis: Long? = null,
    val maxRetries: Int = 2,
) {
    init {
        require(providerId == "openai")
        require(approvedEndpoint(endpoint))
        require(maxRetries in 0..2)
    }

    private companion object {
        fun approvedEndpoint(value: String): Boolean = runCatching {
            val uri = URI(value)
            uri.scheme == "https" && uri.host == "api.openai.com" && uri.path == "/v1/responses" && uri.query == null && uri.fragment == null
        }.getOrDefault(false)
    }
}

/** OpenAI Responses adapter. The model returns JSON data; no tools or file writes are exposed. */
public class OpenAiProposalClient(
    private val configuration: OpenAiProposalConfiguration = OpenAiProposalConfiguration(),
    private val httpClient: HttpClient = createProposalHttpClient(configuration),
    private val objectMapper: ObjectMapper = ObjectMapper(),
) : AiProposalProvider, AutoCloseable {
    override val providerId: String = configuration.providerId

    override suspend fun generate(apiKey: String, selectedModelId: String, input: AiProposalGenerationInput): AiProposalGenerationResult {
        if (apiKey.isBlank() || selectedModelId.isBlank()) return AiProposalGenerationResult.Failed("A verified provider credential and model are required.")
        val body = objectMapper.createObjectNode().apply {
            put("model", selectedModelId)
            put("store", false)
            put("input", prompt(input))
        }.toString()
        var attempt = 0
        while (true) {
            try {
                val response = httpClient.post(configuration.endpoint) {
                    header(HttpHeaders.Authorization, "Bearer ${apiKey.trim()}")
                    accept(ContentType.Application.Json)
                    setBody(TextContent(body, ContentType.Application.Json))
                }
                val responseText = response.bodyAsText()
                if (response.status.isSuccess()) {
                    return AiProposalGenerationResult.Completed(extractText(responseText))
                }
                if (response.status.value in setOf(408, 429, 500, 502, 503, 504) && attempt < configuration.maxRetries) {
                    attempt += 1
                    delay((attempt * 500L).coerceAtMost(2_000L))
                    continue
                }
                return AiProposalGenerationResult.Failed("OpenAI proposal request failed with HTTP ${response.status.value}.", response.status.value in setOf(408, 429, 500, 502, 503, 504))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: HttpRequestTimeoutException) {
                if (attempt++ < configuration.maxRetries) continue
                return AiProposalGenerationResult.Failed("The OpenAI proposal request timed out.", true)
            } catch (_: IOException) {
                if (attempt++ < configuration.maxRetries) continue
                return AiProposalGenerationResult.Failed("OpenAI is temporarily unavailable.", true)
            } catch (_: Exception) {
                return AiProposalGenerationResult.Failed("The OpenAI proposal response could not be read safely.")
            }
        }
    }

    private fun prompt(input: AiProposalGenerationInput): String = """
        You are Entio's ontology assistant. Understand the user's requested outcome semantically, inspect the supplied typed ontology context and optional FIBO context, and choose the appropriate response mode. Never output Turtle, SPARQL, code, or instructions to write files. Return only one JSON object with this shape: {"mode":"answer|proposal|clarification","answer":"natural-language answer or clarification question","summary":"proposal summary or null","evidence":[{"subject":"absolute IRI","predicate":"absolute IRI","objectKind":"iri|literal|blank","objectValue":"IRI or lexical value","datatype":null,"language":null,"source":"current-ontology|private-draft|trusted-vocabulary|fibo"}],"edits":[{"id":"stable-id","sourceId":"source-id","operation":"add|remove","subject":"absolute IRI","predicate":"absolute IRI","objectKind":"iri|literal|blank","objectValue":"IRI or lexical value","datatype":null,"language":null,"summary":"...","rationale":"..."}]}. Choose answer when the user wants an explanation, definition, inference, or description and does not ask Entio to change the ontology. Choose proposal when the user wants an ontology outcome or change, including an indirect or unfamiliar phrasing. Choose clarification only when the intended outcome cannot be determined safely. Answer and clarification responses must contain answer text, an evidence array, and an empty edits array. For ontology-specific claims, cite exact supplied triples in evidence; do not invent evidence. For a canonical glossary claim, use source trusted-vocabulary, the vocabulary term as subject, rdfs:comment as predicate, and the supplied glossary wording as a literal object. Clearly distinguish asserted facts from inferred conclusions and suggestions. Proposal responses must contain the complete review proposal, with one declarative RDF triple per edit, all dependencies and definitions, and newly created entities represented before they are referenced conceptually. Do not create a proposal merely because the request mentions an ontology term.

        USER REQUEST:
        ${input.userRequest}

        CURRENT ONTOLOGY (read-only):
        ${input.ontologyContext}

        FIBO SEARCH CONTEXT (read-only, may be empty):
        ${input.fiboContext}

        CONVERSATION CONTEXT:
        ${input.conversationContext}

        CURRENT PRIVATE PROPOSAL (return a complete replacement proposal, preserving existing edits unless the request changes them):
        ${input.currentProposal}

        ${if (input.validationFindings.isEmpty()) "" else if (input.repairMode == "answer") """
        POST-GENERATION ANSWER EVIDENCE FINDINGS (repair attempt ${input.repairAttempt}):
        Entio checked the completed answer and found the following issue:
        ${input.validationFindings.joinToString("\n") { "- $it" }}
        Return a corrected answer with mode set to answer, preserve the useful explanation, and include at least one exact triple from the supplied ontology context in the evidence array. Do not invent evidence or return edits.
        """ else """
        POST-GENERATION VALIDATION FINDINGS (repair attempt ${input.repairAttempt}):
        The proposal above has already been generated. Entio ran deterministic validation after generation and found the following issues:
        ${input.validationFindings.joinToString("\n") { "- $it" }}
        Diagnose these findings and return one complete replacement JSON proposal with mode set to proposal. Preserve valid edits, add missing dependencies, and remove or correct invalid edits. Do not return a partial patch, an answer-only response, commentary, Turtle, SPARQL, code, or file-writing instructions. The validation findings are feedback for this repair pass; initial response-mode selection and proposal generation are not constrained by them.
        """}
    """.trimIndent()

    private fun extractText(response: String): String {
        val root = objectMapper.readTree(response)
        val direct = root.path("output_text").asText("").trim()
        if (direct.isNotEmpty()) return direct
        val output = root.path("output")
        if (output.isArray) {
            val texts = output.flatMap { item ->
                item.path("content").mapNotNull { content -> content.path("text").asText(null) }
            }
            if (texts.isNotEmpty()) return texts.joinToString("\n")
        }
        throw IllegalArgumentException("OpenAI returned no proposal text.")
    }

    override fun close() { httpClient.close() }
}

public fun createProposalHttpClient(configuration: OpenAiProposalConfiguration): HttpClient = createProposalHttpClient(configuration, CIO.create())

public fun createProposalHttpClient(configuration: OpenAiProposalConfiguration, engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    install(HttpTimeout) {
        connectTimeoutMillis = configuration.connectTimeoutMillis
        requestTimeoutMillis = configuration.requestTimeoutMillis
    }
}

public fun defaultOpenAiProposalClient(): OpenAiProposalClient = OpenAiProposalClient()
