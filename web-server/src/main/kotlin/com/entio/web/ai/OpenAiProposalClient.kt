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

    override suspend fun route(apiKey: String, selectedModelId: String, input: AiProposalGenerationInput): AiResponseKind {
        val result = request(apiKey, selectedModelId, ROUTING_INSTRUCTIONS, providerInput(input, includeRepair = false), routingFormat())
        val text = (result as? AiProposalGenerationResult.Completed)?.text
            ?: throw IllegalStateException((result as AiProposalGenerationResult.Failed).message)
        val kind = runCatching { parseJsonObject(text).path("responseKind").asText() }.getOrDefault("")
        return when (kind.lowercase()) {
            "answer" -> AiResponseKind.Answer
            "proposal" -> AiResponseKind.Proposal
            "clarification" -> AiResponseKind.Clarification
            else -> throw IllegalArgumentException("OpenAI returned an invalid semantic response route.")
        }
    }

    override suspend fun requestExternalContext(apiKey: String, selectedModelId: String, input: AiProposalGenerationInput): AiExternalContextRequest {
        val result = request(apiKey, selectedModelId, EXTERNAL_CONTEXT_INSTRUCTIONS, providerInput(input, includeRepair = false), externalContextFormat())
        val text = (result as? AiProposalGenerationResult.Completed)?.text
            ?: throw IllegalStateException((result as AiProposalGenerationResult.Failed).message)
        val root = parseJsonObject(text)
        val query = root.path("query").asText("").trim().takeIf { it.isNotEmpty() }
        return AiExternalContextRequest(root.path("useFibo").asBoolean(false), query)
    }

    override suspend fun generate(apiKey: String, selectedModelId: String, input: AiProposalGenerationInput): AiProposalGenerationResult {
        val instructions = when (input.responseKind) {
            AiResponseKind.Proposal -> PROPOSAL_INSTRUCTIONS
            AiResponseKind.Clarification -> CLARIFICATION_INSTRUCTIONS
            else -> ANSWER_INSTRUCTIONS
        }
        val format = if (input.responseKind == AiResponseKind.Proposal) proposalFormat() else null
        return request(apiKey, selectedModelId, instructions, providerInput(input, includeRepair = true), format)
    }

    private suspend fun request(
        apiKey: String,
        selectedModelId: String,
        instructions: String,
        messages: JsonNode,
        responseFormat: JsonNode?,
    ): AiProposalGenerationResult {
        if (apiKey.isBlank() || selectedModelId.isBlank()) return AiProposalGenerationResult.Failed("A verified provider credential and model are required.")
        var activeFormat = responseFormat
        var formatFallbacks = 0
        var attempt = 0
        while (true) {
            try {
                val body = requestBody(selectedModelId, instructions, messages, activeFormat)
                val response = httpClient.post(configuration.endpoint) {
                    header(HttpHeaders.Authorization, "Bearer ${apiKey.trim()}")
                    accept(ContentType.Application.Json)
                    setBody(TextContent(body, ContentType.Application.Json))
                }
                val responseText = response.bodyAsText()
                if (response.status.isSuccess()) {
                    return AiProposalGenerationResult.Completed(extractText(responseText))
                }
                if (response.status.value == 400 && activeFormat != null) {
                    activeFormat = when (activeFormat.path("type").asText()) {
                        "json_schema" -> jsonObjectFormat()
                        "json_object" -> null
                        else -> activeFormat
                    }
                    formatFallbacks += 1
                    if (formatFallbacks <= 2) continue
                }
                if (response.status.value in setOf(408, 429, 500, 502, 503, 504) && attempt < configuration.maxRetries) {
                    attempt += 1
                    delay((attempt * 500L).coerceAtMost(2_000L))
                    continue
                }
                val detail = safeProviderError(responseText)
                return AiProposalGenerationResult.Failed(
                    "OpenAI proposal request failed with HTTP ${response.status.value}${detail?.let { ": $it" }.orEmpty()}.",
                    response.status.value in setOf(408, 429, 500, 502, 503, 504),
                )
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

    private fun requestBody(modelId: String, instructions: String, messages: JsonNode, responseFormat: JsonNode?): String =
        objectMapper.createObjectNode().apply {
            put("model", modelId)
            put("store", false)
            put("instructions", instructions)
            set<JsonNode>("input", messages)
            responseFormat?.let { format ->
                set<JsonNode>("text", objectMapper.createObjectNode().set<JsonNode>("format", format))
            }
        }.toString()

    private fun jsonObjectFormat(): JsonNode = objectMapper.createObjectNode().put("type", "json_object")

    private fun safeProviderError(response: String): String? = runCatching {
        objectMapper.readTree(response).path("error").path("message").asText("").trim()
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .take(400)
            .takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun providerInput(input: AiProposalGenerationInput, includeRepair: Boolean): JsonNode = objectMapper.createArrayNode().apply {
        add(message("developer", projectContext(input)))
        input.conversation.forEach { turn -> add(message(turn.role, turn.content)) }
        add(message("user", input.userRequest))
        if (includeRepair) repairContext(input)?.let { add(message("developer", it)) }
    }

    private fun message(role: String, content: String): JsonNode = objectMapper.createObjectNode().apply {
        put("role", role)
        put("content", content)
    }

    private fun projectContext(input: AiProposalGenerationInput): String = """
        ENTIO PROJECT CONTEXT
        The following is trusted, read-only context for the current Entio project. Use it when relevant to the conversation.

        ${input.ontologyContext}

        FIBO ACCESS BOUNDARY
        Entio exposes the complete pinned, read-only FIBO catalog through server-retrieved context. The catalog includes a curated foundations subset and a wider indexed set of FIBO domain modules. You may use the supplied catalog results from either scope to explain or propose reuse of FIBO classes and properties. This is not unrestricted internet access or guaranteed access to the wider/latest FIBO ontology. If no catalog entries were returned, say that no matching entries were found in the pinned catalog; do not claim that Entio cannot access FIBO at all.
        When the user asks to search, reuse, or pull in FIBO concepts, use the exact external IRIs from the supplied FIBO results. Never invent a local replacement IRI and present it as a FIBO class or property. If a suitable FIBO result is unavailable, say so rather than claiming a locally invented concept came from FIBO.

        ${input.fiboContext.ifBlank { "No catalog results were supplied for this request." }}

        CURRENT PRIVATE PROPOSAL
        ${input.currentProposal.ifBlank { "No private proposal exists." }}
        When a private proposal exists, it is authoritative mutable draft state: its additions and removals already exist in the effective private-draft graph supplied above. A follow-up request continues that proposal and may add, revise, or retract pending edits. Return the edits needed for the new request, do not recreate unaffected edits, and reuse an existing edit id when revising one. To revise an earlier edit, return the same id with the corrected triple. To retract a draft-only addition, return a `remove` edit for that exact pending triple; Entio will cancel the pending addition rather than stage a removal against the applied graph. To restore a triple pending removal, return an `add` edit for that exact triple. Do not emit competing edits merely to compensate for an earlier draft edit; express the intended final state directly and Entio will reconcile the private edits.
    """.trimIndent()

    private fun repairContext(input: AiProposalGenerationInput): String? {
        if (input.validationFindings.isEmpty()) return null
        return if (input.repairMode == "answer") """
        POST-GENERATION ANSWER EVIDENCE FINDINGS (repair attempt ${input.repairAttempt}):
        Entio checked the completed answer and found the following issue:
        ${input.validationFindings.joinToString("\n") { "- $it" }}
        Reconsider the answer against the project context and return a corrected answer with verifiable evidence. Do not preserve a prior claim when the evidence contradicts it.
        """ else """
        POST-GENERATION VALIDATION FINDINGS (repair attempt ${input.repairAttempt}):
        Entio ran deterministic validation on the private proposal and found:
        ${input.validationFindings.joinToString("\n") { "- $it" }}
        Return a complete corrected replacement proposal, preserving valid work and repairing the findings.
        Treat each finding as a required semantic correction, not merely a message to acknowledge. Findings include the exact source-scoped triple involved and, where possible, the required repair action; use those details directly when revising the edits. For a no-op `add`, remove that exact edit from the replacement proposal rather than emitting it again. For a no-op `remove`, remove it or correct its exact subject, predicate, or object so the replacement targets a triple that is actually present. Re-evaluate whether the asserted triple, the property's domain/range axiom, or both are inconsistent with the user's intended model and the ontology context. When changing an existing domain or range, remove the old conflicting triple and add the corrected triple; do not leave both axioms in the replacement proposal. If the conflicting axiom came from the prior private proposal, include its removal explicitly. Preserve every other valid edit in the corrected replacement.
        If the finding came from a malformed edit, the source-scoped proposal context contains the valid edits recovered from the original response. Preserve all of those edits and repair the malformed edit; do not return only the edit named in the finding. Return the complete proposal needed to satisfy the original request, including every requested class, property, node shape, property shape, and constraint.

        $PROPOSAL_PRESENTATION_CONTRACT
        """
    }

    private fun proposalFormat(): JsonNode = objectMapper.createObjectNode().apply {
        put("type", "json_schema")
        put("name", "entio_ontology_proposal")
        put("strict", true)
        set<JsonNode>("schema", objectMapper.createObjectNode().apply {
            put("type", "object")
            put("additionalProperties", false)
            set<JsonNode>("properties", objectMapper.createObjectNode().apply {
                set<JsonNode>("mode", stringSchema(listOf("proposal")))
                set<JsonNode>("answer", stringSchema())
                set<JsonNode>("summary", stringSchema())
                set<JsonNode>("evidence", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    set<JsonNode>("items", evidenceSchema())
                })
                set<JsonNode>("edits", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    set<JsonNode>("items", editSchema())
                })
            })
            putArray("required").add("mode").add("answer").add("summary").add("evidence").add("edits")
        })
    }

    private fun routingFormat(): JsonNode = objectMapper.createObjectNode().apply {
        put("type", "json_schema")
        put("name", "entio_response_route")
        put("strict", true)
        set<JsonNode>("schema", objectMapper.createObjectNode().apply {
            put("type", "object")
            put("additionalProperties", false)
            set<JsonNode>("properties", objectMapper.createObjectNode().set<JsonNode>("responseKind", stringSchema(listOf("answer", "clarification", "proposal"))))
            putArray("required").add("responseKind")
        })
    }

    private fun externalContextFormat(): JsonNode = objectMapper.createObjectNode().apply {
        put("type", "json_schema")
        put("name", "entio_external_context_request")
        put("strict", true)
        set<JsonNode>("schema", objectMapper.createObjectNode().apply {
            put("type", "object")
            put("additionalProperties", false)
            set<JsonNode>("properties", objectMapper.createObjectNode().apply {
                set<JsonNode>("useFibo", objectMapper.createObjectNode().put("type", "boolean"))
                set<JsonNode>("query", objectMapper.createObjectNode().put("type", "string"))
            })
            putArray("required").add("useFibo").add("query")
        })
    }

    private fun evidenceSchema(): JsonNode = objectMapper.createObjectNode().apply {
        put("type", "object")
        put("additionalProperties", false)
        set<JsonNode>("properties", objectMapper.createObjectNode().apply {
            set<JsonNode>("subject", stringSchema())
            set<JsonNode>("predicate", stringSchema())
            set<JsonNode>("objectKind", stringSchema(listOf("iri", "literal", "blank")))
            set<JsonNode>("objectValue", stringSchema())
            set<JsonNode>("datatype", nullableStringSchema())
            set<JsonNode>("language", nullableStringSchema())
            set<JsonNode>("source", stringSchema(listOf("current-ontology", "private-draft", "trusted-vocabulary", "fibo")))
        })
        putArray("required").add("subject").add("predicate").add("objectKind").add("objectValue").add("datatype").add("language").add("source")
    }

    private fun editSchema(): JsonNode = objectMapper.createObjectNode().apply {
        put("type", "object")
        put("additionalProperties", false)
        set<JsonNode>("properties", objectMapper.createObjectNode().apply {
            set<JsonNode>("id", stringSchema())
            set<JsonNode>("sourceId", stringSchema())
            set<JsonNode>("operation", stringSchema(listOf("add", "remove")))
            set<JsonNode>("subject", stringSchema())
            set<JsonNode>("predicate", stringSchema())
            set<JsonNode>("objectKind", stringSchema(listOf("iri", "literal", "blank")))
            set<JsonNode>("objectValue", stringSchema())
            set<JsonNode>("datatype", nullableStringSchema())
            set<JsonNode>("language", nullableStringSchema())
            set<JsonNode>("summary", stringSchema())
            set<JsonNode>("rationale", nullableStringSchema())
        })
        putArray("required").add("id").add("sourceId").add("operation").add("subject").add("predicate").add("objectKind")
            .add("objectValue").add("datatype").add("language").add("summary").add("rationale")
    }

    private fun stringSchema(values: List<String> = emptyList()): JsonNode = objectMapper.createObjectNode().apply {
        put("type", "string")
        if (values.isNotEmpty()) putArray("enum").also { array -> values.forEach(array::add) }
    }

    private fun nullableStringSchema(): JsonNode = objectMapper.createObjectNode().apply {
        putArray("type").add("string").add("null")
    }

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

    private fun parseJsonObject(text: String): JsonNode {
        val trimmed = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return runCatching { objectMapper.readTree(trimmed) }.getOrElse {
            val start = trimmed.indexOf('{')
            val end = trimmed.lastIndexOf('}')
            if (start < 0 || end <= start) throw it
            objectMapper.readTree(trimmed.substring(start, end + 1))
        }
    }

    override fun close() { httpClient.close() }

    private companion object {
        private val ROUTING_INSTRUCTIONS = """
            Decide semantically how Entio AI should respond to the latest user message in its conversation and project context.

            Return exactly one of these JSON objects, with no Markdown fences, prose, explanation, or additional fields:
            {"responseKind":"answer"}
            {"responseKind":"clarification"}
            {"responseKind":"proposal"}

            The only valid key is `responseKind`. The only valid values are exactly `answer`, `clarification`, and `proposal`, all lowercase. Use `clarification`, never `clarify`.
            Choose `proposal` when the user explicitly asks to create, change, remove, model, or otherwise produce an ontology outcome. Choose `answer` for ordinary discussion, explanation, analysis, status questions, and questions about the current ontology or private proposal, including challenges such as asking why an edit is present. The existence of a current private proposal does not by itself make a message a proposal request. A question about a proposal becomes a proposal only when the user also asks you to modify it. Choose `clarification` only when a requested ontology outcome is too ambiguous to propose safely. Do not use keyword matching; interpret the user's intended outcome in context.
        """.trimIndent()
        private val ANSWER_INSTRUCTIONS = """
            You are Entio AI, a capable general-purpose assistant with trusted context about the current Entio ontology project. Reason normally, follow the conversation, and answer the user directly in ordinary prose. Reconsider earlier answers when challenged or corrected. Use project context when relevant, distinguish asserted facts from inferences, and accurately describe Entio's server-retrieved curated FIBO catalog access; do not claim that Entio has no FIBO access when catalog context is supplied. Do not create a proposal.
        """.trimIndent()
        private val EXTERNAL_CONTEXT_INSTRUCTIONS = """
            Decide whether the latest user request requires looking up Entio's pinned, read-only FIBO catalog. You have the current Entio ontology context and conversation, but not the catalog contents. The catalog includes curated foundations and a wider indexed set of FIBO domain modules. Request FIBO context when FIBO reuse, comparison, discovery, or external ontology grounding would materially improve the response. Do not request it for ordinary questions that can be answered from the supplied project context.

            Return exactly one JSON object with no prose or Markdown:
            {"useFibo":true,"query":"concise ontology concepts to search, comma-separated"}
            or
            {"useFibo":false,"query":""}

            If requesting FIBO, make `query` a concise list of the most relevant concepts, classes, or properties—not the full user request or full ontology context. Entio will perform the catalog lookup and provide the results to the next response step.
        """.trimIndent()
        private val CLARIFICATION_INSTRUCTIONS = """
            You are Entio AI. Ask one concise, useful clarification question needed before you can safely prepare the requested ontology change. Use the conversation and project context. Return ordinary prose only.
        """.trimIndent()
        private val PROPOSAL_PRESENTATION_CONTRACT = """
            ENTIO PROPOSAL PRESENTATION CONTRACT
            This contract controls serialization only; it does not limit your ontology reasoning, plan, modeling choices, or use of supplied context.
            - Return exactly one JSON object. Do not wrap it in Markdown fences and do not put prose before or after it.
            - Put the user-facing natural-language response in `answer`, the proposal title in `summary`, and all graph changes in `edits`.
            - If the user's request contains one or more questions, answer every question directly and clearly in `answer` using the current graph and conversation context. Distinguish the current state from what will be true after the proposed edits, then briefly summarize the proposal. Do not let the proposal summary replace the requested answers.
            - Represent exactly one RDF triple in each edit. Express multi-triple modeling decisions as multiple edits.
            - `id` is a stable JSON string. `sourceId` is an exact source ID listed in project context.
            - `operation` is exactly `add` or `remove`, in lowercase. Do not use create, delete, addition, removal, or uppercase variants.
            - `subject` and `predicate` are absolute IRIs. Never use compact names such as rdf:type, rdfs:label, owl:Class, skos:definition, xsd:decimal, or simple:Loan.
            - `objectKind` is exactly `iri`, `literal`, or `blank`, in lowercase. Class, ObjectProperty, DatatypeProperty, NamedIndividual, and datatype names belong in `objectValue`, not `objectKind`.
            - When `objectKind` is `iri`, `objectValue` is an absolute IRI. When it is `literal`, `objectValue` is the lexical value and `datatype` contains an absolute datatype IRI or null.
            - Include `datatype`, `language`, and `rationale` explicitly, using null when absent.
            Example edit: {"id":"loan-class","sourceId":"simple","operation":"add","subject":"https://example.com/entio/simple#Loan","predicate":"http://www.w3.org/1999/02/22-rdf-syntax-ns#type","objectKind":"iri","objectValue":"http://www.w3.org/2002/07/owl#Class","datatype":null,"language":null,"summary":"Declare Loan as a class","rationale":"The requested model requires a Loan class."}
        """.trimIndent()
        private val PROPOSAL_INSTRUCTIONS = """
            You are Entio AI, a capable ontology engineer with trusted context about the current project. Determine the complete ontology outcome requested by the user. You cannot edit ontology files or Entio configuration; prepare a review-only proposal for Entio validation.

            Think through the ontology freely and include every declaration, definition, axiom, individual, and assertion required for a complete result. Treat deletion as dependency-aware cleanup: remove every affected triple where the deleted IRI appears as subject, predicate, or object, including property assertions, object/type references, and SHACL targets, paths, and constraints, unless the user explicitly asks to preserve or repoint a reference. Treat replacement as an IRI migration rather than a label edit: after incorporating the replacement concept's authoritative semantics, migrate all references from the old IRI to the new IRI across domains, ranges, hierarchy, type assertions, property assertions, and SHACL structures, leaving no dependent old-IRI triples. When the user asks for FIBO reuse, use exact FIBO IRIs from the supplied catalog results and do not represent invented local IRIs as FIBO concepts. When the user asks to propose a specific number of classes or properties, include concrete edits for each recommendation; never return an empty `edits` array for a proposal request. If the context contains a current private proposal, treat its entities and triples as already present in the effective draft. For a follow-up, return only the new or revised edits needed for the user's request; do not recreate unaffected edits, and reuse an existing edit id when revising one. Entio preserves and reconciles the existing private edits. A follow-up may retract a pending draft addition with a `remove` edit for its exact triple, or restore a pending draft removal with an `add` edit for its exact triple; these are draft operations and are not no-ops. Before emitting each edit, compare its exact subject/predicate/object against the source-scoped graph context and the current private draft: add only triples absent from the effective draft, and remove only triples present in the effective draft. Do not emit a second competing assertion when revising an earlier edit; revise the earlier edit by ID or retract it explicitly. If the requested state is already true, omit that edit and explain it in `answer`. The answer should briefly say that the proposal was generated and summarize its modeling choices; keep the edit details in the edits array for Entio's proposal popup. Never invent source IDs or evidence.
            For SHACL, distinguish the node shape from its property shapes: `sh:targetClass` and `sh:property` are triples on the node shape (`rdf:type sh:NodeShape`), while `sh:path`, `sh:minCount`, `sh:maxCount`, `sh:datatype`, `sh:class`, `sh:nodeKind`, and `sh:message` are normally on the linked property-shape resource. When removing a node shape or its constraints, use the actual asserted node-shape subject, remove the requested node-shape triples, and clean up linked property-shape triples that would otherwise be orphaned. Do not substitute a similarly labeled property-shape IRI for the node-shape IRI.
            Every edit subject and predicate must be an absolute IRI. In particular, do not use a blank-node subject (`_:`) for a SHACL node shape or property shape; create or reuse a named absolute IRI for each shape resource so Entio can validate and review it.

            $PROPOSAL_PRESENTATION_CONTRACT
        """.trimIndent()
    }
}

public fun createProposalHttpClient(configuration: OpenAiProposalConfiguration): HttpClient = createProposalHttpClient(configuration, CIO.create())

public fun createProposalHttpClient(configuration: OpenAiProposalConfiguration, engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    install(HttpTimeout) {
        connectTimeoutMillis = configuration.connectTimeoutMillis
        requestTimeoutMillis = configuration.requestTimeoutMillis
    }
}

public fun defaultOpenAiProposalClient(): OpenAiProposalClient = OpenAiProposalClient()
