package com.entio.web.ingestion

import com.entio.core.DocumentCandidateCategory
import com.entio.core.DocumentAnalysisPipelineVersions
import com.entio.core.DocumentAssertionClassification
import com.entio.core.DocumentContentClassification
import com.entio.core.DocumentConnectedModelItemKind
import com.entio.core.DocumentConnectedModelReferenceRole
import com.entio.core.DocumentDiscoveryKind
import com.entio.core.DocumentIndividualClassification
import com.entio.core.MAX_DOCUMENT_DISCOVERIES_PER_DOCUMENT
import com.entio.core.MAX_DOCUMENT_CONNECTED_MODEL_ITEMS
import com.entio.core.MAX_DOCUMENT_PROVIDER_RESPONSE_CHARACTERS
import com.entio.core.DocumentRecommendationCategory
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

internal data class OpenAiDocumentAnalysisConfiguration(
    val endpoint: String = "https://api.openai.com/v1/responses",
    val connectTimeoutMillis: Long = 10_000,
    val requestTimeoutMillis: Long = 120_000,
) {
    init {
        require(runCatching {
            val uri = URI(endpoint)
            uri.scheme == "https" &&
                uri.host == "api.openai.com" &&
                uri.path == "/v1/responses" &&
                uri.query == null &&
                uri.fragment == null
        }.getOrDefault(false)) { "Document analysis requires the fixed OpenAI Responses endpoint." }
        require(connectTimeoutMillis in 1..30_000)
        require(requestTimeoutMillis in 1..180_000)
    }
}

/** Narrow ingestion-only OpenAI Responses adapter with no tools, URLs, or conversation state. */
internal class OpenAiDocumentAnalysisClient(
    private val configuration: OpenAiDocumentAnalysisConfiguration = OpenAiDocumentAnalysisConfiguration(),
    private val objectMapper: ObjectMapper = ObjectMapper().findAndRegisterModules(),
    engine: HttpClientEngine? = null,
) : DocumentAnalysisProvider, DocumentDiscoveryProvider, DocumentConnectedModelProvider, AutoCloseable {
    private val client = if (engine == null) {
        HttpClient(CIO) {
            followRedirects = false
            install(HttpTimeout) {
                connectTimeoutMillis = configuration.connectTimeoutMillis
                requestTimeoutMillis = configuration.requestTimeoutMillis
            }
        }
    } else {
        HttpClient(engine) {
            followRedirects = false
            install(HttpTimeout) {
                connectTimeoutMillis = configuration.connectTimeoutMillis
                requestTimeoutMillis = configuration.requestTimeoutMillis
            }
        }
    }

    override suspend fun analyze(
        apiKey: String,
        selectedModelId: String,
        systemInstruction: String,
        request: DocumentAnalysisRequest,
    ): DocumentAnalysisProviderResult {
        if (apiKey.isBlank() || selectedModelId.isBlank()) {
            return DocumentAnalysisProviderResult.Failed(false, "document-provider-authorization")
        }
        return try {
            val response = client.post(configuration.endpoint) {
                header(HttpHeaders.Authorization, "Bearer ${apiKey.trim()}")
                accept(ContentType.Application.Json)
                setBody(TextContent(requestBody(selectedModelId, systemInstruction, request), ContentType.Application.Json))
            }
            if (!response.status.isSuccess()) {
                return DocumentAnalysisProviderResult.Failed(
                    retryable = response.status.value == 429 || response.status.value >= 500,
                    safeCode = when {
                        response.status.value == 401 || response.status.value == 403 -> "document-provider-authorization"
                        response.status.value == 429 -> "document-provider-rate-limited"
                        response.status.value >= 500 -> "document-provider-unavailable"
                        else -> "document-provider-request-rejected"
                    },
                )
            }
            val responseText = response.bodyAsText()
            if (responseText.length > MAX_PROVIDER_RESPONSE_CHARACTERS) {
                return DocumentAnalysisProviderResult.Failed(false, "document-provider-response-limit")
            }
            val structured = parseStrictResponse(extractOutputText(responseText))
            DocumentAnalysisProviderResult.Completed(structured)
        } catch (failure: SafeProviderResponseFailure) {
            DocumentAnalysisProviderResult.Failed(false, failure.code)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: HttpRequestTimeoutException) {
            DocumentAnalysisProviderResult.Failed(true, "document-provider-timeout")
        } catch (_: IOException) {
            DocumentAnalysisProviderResult.Failed(true, "document-provider-unavailable")
        } catch (_: Exception) {
            DocumentAnalysisProviderResult.Failed(false, "document-provider-malformed-output")
        }
    }

    override suspend fun discover(
        apiKey: String,
        selectedModelId: String,
        systemInstruction: String,
        request: DocumentDiscoveryRequest,
    ): DocumentDiscoveryProviderResult {
        if (apiKey.isBlank() || selectedModelId.isBlank()) {
            return DocumentDiscoveryProviderResult.Failed(false, "document-provider-authorization")
        }
        return try {
            val response = client.post(configuration.endpoint) {
                header(HttpHeaders.Authorization, "Bearer ${apiKey.trim()}")
                accept(ContentType.Application.Json)
                setBody(
                    TextContent(
                        discoveryRequestBody(selectedModelId, systemInstruction, request),
                        ContentType.Application.Json,
                    ),
                )
            }
            if (!response.status.isSuccess()) {
                return DocumentDiscoveryProviderResult.Failed(
                    retryable = response.status.value == 429 || response.status.value >= 500,
                    safeCode = when {
                        response.status.value == 401 || response.status.value == 403 ->
                            "document-provider-authorization"
                        response.status.value == 429 -> "document-provider-rate-limited"
                        response.status.value >= 500 -> "document-provider-unavailable"
                        else -> "document-provider-request-rejected"
                    },
                )
            }
            val responseText = response.bodyAsText()
            if (responseText.length > MAX_DOCUMENT_PROVIDER_RESPONSE_CHARACTERS) {
                return DocumentDiscoveryProviderResult.Failed(false, "document-provider-response-limit")
            }
            val structured = parseStrictDiscoveryResponse(extractOutputText(responseText))
            DocumentDiscoveryProviderResult.Completed(structured)
        } catch (failure: SafeProviderResponseFailure) {
            DocumentDiscoveryProviderResult.Failed(false, failure.code)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: HttpRequestTimeoutException) {
            DocumentDiscoveryProviderResult.Failed(true, "document-provider-timeout")
        } catch (_: IOException) {
            DocumentDiscoveryProviderResult.Failed(true, "document-provider-unavailable")
        } catch (_: Exception) {
            DocumentDiscoveryProviderResult.Failed(false, "document-provider-malformed-output")
        }
    }

    override suspend fun model(
        apiKey: String,
        selectedModelId: String,
        systemInstruction: String,
        request: DocumentConnectedModelRequest,
    ): DocumentConnectedModelProviderResult =
        connectedModelCall(
            apiKey = apiKey,
            selectedModelId = selectedModelId,
            systemInstruction = systemInstruction,
            request = request,
            responseSchemaVersion = DocumentAnalysisPipelineVersions.CONNECTED_MODEL_RESPONSE,
            formatName = "phase_11_5_connected_document_model",
        ) { response ->
            DocumentConnectedModelProviderResult.CompletedModel(
                DocumentConnectedModelResponse(items = response),
            )
        }

    override suspend fun consolidate(
        apiKey: String,
        selectedModelId: String,
        systemInstruction: String,
        request: DocumentModelConsolidationRequest,
    ): DocumentConnectedModelProviderResult =
        connectedModelCall(
            apiKey = apiKey,
            selectedModelId = selectedModelId,
            systemInstruction = systemInstruction,
            request = request,
            responseSchemaVersion = DocumentAnalysisPipelineVersions.MODEL_CONSOLIDATION_RESPONSE,
            formatName = "phase_11_5_document_model_consolidation",
        ) { response ->
            DocumentConnectedModelProviderResult.CompletedConsolidation(
                DocumentModelConsolidationResponse(items = response),
            )
        }

    override fun close(): Unit = client.close()

    private suspend fun connectedModelCall(
        apiKey: String,
        selectedModelId: String,
        systemInstruction: String,
        request: Any,
        responseSchemaVersion: String,
        formatName: String,
        completed: (List<ProviderConnectedModelItem>) -> DocumentConnectedModelProviderResult,
    ): DocumentConnectedModelProviderResult {
        if (apiKey.isBlank() || selectedModelId.isBlank()) {
            return DocumentConnectedModelProviderResult.Failed(false, "document-provider-authorization")
        }
        return try {
            val response = client.post(configuration.endpoint) {
                header(HttpHeaders.Authorization, "Bearer ${apiKey.trim()}")
                accept(ContentType.Application.Json)
                setBody(
                    TextContent(
                        connectedModelRequestBody(
                            selectedModelId,
                            systemInstruction,
                            request,
                            responseSchemaVersion,
                            formatName,
                        ),
                        ContentType.Application.Json,
                    ),
                )
            }
            if (!response.status.isSuccess()) {
                return DocumentConnectedModelProviderResult.Failed(
                    retryable = response.status.value == 429 || response.status.value >= 500,
                    safeCode = when {
                        response.status.value == 401 || response.status.value == 403 ->
                            "document-provider-authorization"
                        response.status.value == 429 -> "document-provider-rate-limited"
                        response.status.value >= 500 -> "document-provider-unavailable"
                        else -> "document-provider-request-rejected"
                    },
                )
            }
            val responseText = response.bodyAsText()
            if (responseText.length > MAX_DOCUMENT_PROVIDER_RESPONSE_CHARACTERS) {
                return DocumentConnectedModelProviderResult.Failed(false, "document-provider-response-limit")
            }
            completed(parseStrictConnectedModelResponse(extractOutputText(responseText), responseSchemaVersion))
        } catch (failure: SafeProviderResponseFailure) {
            DocumentConnectedModelProviderResult.Failed(false, failure.code)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: HttpRequestTimeoutException) {
            DocumentConnectedModelProviderResult.Failed(true, "document-provider-timeout")
        } catch (_: IOException) {
            DocumentConnectedModelProviderResult.Failed(true, "document-provider-unavailable")
        } catch (_: Exception) {
            DocumentConnectedModelProviderResult.Failed(false, "document-provider-malformed-output")
        }
    }

    private fun requestBody(modelId: String, instruction: String, request: DocumentAnalysisRequest): String {
        val root = objectMapper.createObjectNode()
        root.put("model", modelId)
        root.put("store", false)
        root.putArray("tools")
        root.put("instructions", instruction)
        root.put("input", objectMapper.writeValueAsString(request))
        root.set<JsonNode>("text", strictTextFormat())
        return objectMapper.writeValueAsString(root)
    }

    private fun discoveryRequestBody(
        modelId: String,
        instruction: String,
        request: DocumentDiscoveryRequest,
    ): String {
        val root = objectMapper.createObjectNode()
        root.put("model", modelId)
        root.put("store", false)
        root.putArray("tools")
        root.put("instructions", instruction)
        root.put("input", objectMapper.writeValueAsString(request))
        root.set<JsonNode>("text", strictDiscoveryTextFormat())
        return objectMapper.writeValueAsString(root)
    }

    private fun connectedModelRequestBody(
        modelId: String,
        instruction: String,
        request: Any,
        responseSchemaVersion: String,
        formatName: String,
    ): String {
        val root = objectMapper.createObjectNode()
        root.put("model", modelId)
        root.put("store", false)
        root.putArray("tools")
        root.put("instructions", instruction)
        root.put("input", objectMapper.writeValueAsString(request))
        root.set<JsonNode>("text", strictConnectedModelTextFormat(responseSchemaVersion, formatName))
        return objectMapper.writeValueAsString(root)
    }

    private fun strictConnectedModelTextFormat(
        responseSchemaVersion: String,
        formatName: String,
    ): JsonNode {
        val reference = objectMapper.createObjectNode().apply {
            put("type", "object")
            put("additionalProperties", false)
            set<JsonNode>("required", objectMapper.valueToTree(CONNECTED_MODEL_REFERENCE_FIELDS.sorted()))
            set<JsonNode>("properties", objectMapper.createObjectNode().apply {
                set<JsonNode>("role", stringEnum(
                    DocumentConnectedModelReferenceRole.entries.map { it.name },
                    "The semantic role of this dependency.",
                ))
                putObject("providerItemId")
                    .put("type", "string")
                    .put("pattern", "^[A-Za-z0-9][A-Za-z0-9._:-]{0,199}$")
            })
        }
        val item = objectMapper.createObjectNode().apply {
            put("type", "object")
            put("additionalProperties", false)
            set<JsonNode>("required", objectMapper.valueToTree(CONNECTED_MODEL_FIELDS.sorted()))
            set<JsonNode>("properties", objectMapper.createObjectNode().apply {
                putObject("providerId")
                    .put("type", "string")
                    .put("pattern", "^[A-Za-z0-9][A-Za-z0-9._:-]{0,199}$")
                set<JsonNode>("kind", stringEnum(
                    DocumentConnectedModelItemKind.entries.map { it.name },
                    "The local connected-model item kind.",
                ))
                putObject("label").put("type", "string").put("minLength", 1).put("maxLength", 500)
                putObject("rationale").put("type", "string").put("minLength", 1).put("maxLength", 2_000)
                set<JsonNode>("discoveryIds", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("minItems", 1)
                    put("maxItems", 2_000)
                    put("uniqueItems", true)
                    set<JsonNode>("items", objectMapper.createObjectNode().put("type", "string").put("maxLength", 200))
                })
                set<JsonNode>("references", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("maxItems", 20)
                    put("uniqueItems", true)
                    set<JsonNode>("items", reference)
                })
                set<JsonNode>("literalLexicalForm", nullableString(
                    8_000,
                    "The literal lexical form for a DatatypeValueAssertion, otherwise null.",
                ))
                set<JsonNode>("literalDatatypeIri", nullableString(
                    2_000,
                    "The literal datatype IRI when present, otherwise null.",
                ))
                set<JsonNode>("literalLanguageTag", nullableString(
                    100,
                    "The literal language tag when present, otherwise null.",
                ))
                putObject("order").put("type", "integer").put("minimum", 0)
                putObject("reviewOnlyEligible").put("type", "boolean")
            })
        }
        val schema = objectMapper.createObjectNode().apply {
            put("type", "object")
            put("additionalProperties", false)
            set<JsonNode>("required", objectMapper.valueToTree(listOf("schemaVersion", "items")))
            set<JsonNode>("properties", objectMapper.createObjectNode().apply {
                putObject("schemaVersion").put("type", "string").put("const", responseSchemaVersion)
                set<JsonNode>("items", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("minItems", 1)
                    put("maxItems", MAX_DOCUMENT_CONNECTED_MODEL_ITEMS)
                    set<JsonNode>("items", item)
                })
            })
        }
        return objectMapper.createObjectNode().apply {
            set<JsonNode>("format", objectMapper.createObjectNode().apply {
                put("type", "json_schema")
                put("name", formatName)
                put("strict", true)
                set<JsonNode>("schema", schema)
            })
        }
    }

    private fun strictDiscoveryTextFormat(): JsonNode {
        val evidence = objectMapper.createObjectNode().apply {
            put("type", "array")
            put("minItems", 1)
            put("maxItems", 8)
            set<JsonNode>("items", objectMapper.createObjectNode().apply {
                put("type", "object")
                put("additionalProperties", false)
                set<JsonNode>("required", objectMapper.valueToTree(EVIDENCE_FIELDS.sorted()))
                set<JsonNode>("properties", objectMapper.createObjectNode().apply {
                    putObject("documentId")
                        .put("type", "string")
                        .put("maxLength", 200)
                    putObject("blockId")
                        .put("type", "string")
                        .put("maxLength", 200)
                    putObject("startOffsetInBlock")
                        .put("type", "integer")
                        .put("minimum", 0)
                    putObject("endOffsetInBlock")
                        .put("type", "integer")
                        .put("minimum", 1)
                    putObject("excerpt")
                        .put("type", "string")
                        .put("minLength", 1)
                        .put("maxLength", 500)
                })
            })
        }
        val discovery = objectMapper.createObjectNode().apply {
            put("type", "object")
            put("additionalProperties", false)
            set<JsonNode>("required", objectMapper.valueToTree(DISCOVERY_FIELDS.sorted()))
            set<JsonNode>("properties", objectMapper.createObjectNode().apply {
                putObject("providerId")
                    .put("type", "string")
                    .put("pattern", "^[A-Za-z0-9][A-Za-z0-9._:-]{0,199}$")
                set<JsonNode>("kind", stringEnum(
                    DocumentDiscoveryKind.entries.map { it.name },
                    "The kind of meaning found in the document.",
                ))
                set<JsonNode>("contentClassification", stringEnum(
                    DocumentContentClassification.entries.map { it.name },
                    "Whether the item is business content or document-control metadata.",
                ))
                set<JsonNode>("assertionClassification", stringEnum(
                    DocumentAssertionClassification.entries.map { it.name },
                    "How directly the document supports the item.",
                ))
                putObject("description")
                    .put("type", "string")
                    .put("minLength", 1)
                    .put("maxLength", 2_000)
                set<JsonNode>("evidence", evidence)
                set<JsonNode>("relatedProviderIds", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("maxItems", MAX_DOCUMENT_DISCOVERIES_PER_DOCUMENT)
                    put("uniqueItems", true)
                    set<JsonNode>("items", objectMapper.createObjectNode()
                        .put("type", "string")
                        .put("maxLength", 200))
                })
                putObject("evidenceConfidence")
                    .put("type", "integer")
                    .put("minimum", 0)
                    .put("maximum", 100)
                set<JsonNode>("individualClassification", nullableEnum(
                    DocumentIndividualClassification.entries.map { it.name },
                    "Required for an Individual discovery and null for every other kind.",
                ))
            })
        }
        val schema = objectMapper.createObjectNode().apply {
            put("type", "object")
            put("additionalProperties", false)
            set<JsonNode>("required", objectMapper.valueToTree(listOf("schemaVersion", "discoveries")))
            set<JsonNode>("properties", objectMapper.createObjectNode().apply {
                putObject("schemaVersion")
                    .put("type", "string")
                    .put("const", DocumentAnalysisPipelineVersions.DISCOVERY_RESPONSE)
                set<JsonNode>("discoveries", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("maxItems", MAX_DOCUMENT_DISCOVERIES_PER_DOCUMENT)
                    set<JsonNode>("items", discovery)
                })
            })
        }
        return objectMapper.createObjectNode().apply {
            set<JsonNode>("format", objectMapper.createObjectNode().apply {
                put("type", "json_schema")
                put("name", "phase_11_5_document_discovery")
                put("strict", true)
                set<JsonNode>("schema", schema)
            })
        }
    }

    private fun strictTextFormat(): JsonNode {
        val candidate = objectMapper.createObjectNode().apply {
            put("type", "object")
            put("additionalProperties", false)
            set<JsonNode>("required", objectMapper.valueToTree(CANDIDATE_FIELDS.sorted()))
            set<JsonNode>("properties", objectMapper.createObjectNode().apply {
                set<JsonNode>("category", stringEnum(
                    DocumentCandidateCategory.entries.map { it.name },
                    "The supported Entio category that best expresses the semantic conclusion.",
                ))
                set<JsonNode>("recommendationCategory", stringEnum(
                    DocumentRecommendationCategory.entries.map { it.name },
                    "Whether the candidate describes ontology structure or a business fact.",
                ))
                putObject("proposedLabel")
                    .put("type", "string")
                    .put("maxLength", 500)
                    .put("description", "A concise human-readable label for the semantic conclusion.")
                set<JsonNode>("proposedDefinition", nullableString(
                    2_000,
                    "A concise proposed ontology definition synthesized from the cited evidence, or null.",
                ))
                set<JsonNode>("proposedDomainIri", nullableString(
                    2_000,
                    "An existing class IRI from ontologyContext when the semantic conclusion includes a property domain; otherwise null.",
                ))
                set<JsonNode>("proposedRangeIri", nullableString(
                    2_000,
                    "An existing class IRI or XSD datatype when the semantic conclusion includes a property range; otherwise null.",
                ))
                set<JsonNode>("proposedConnectionLabel", nullableString(
                    500,
                    "An object-property label when the semantic conclusion includes a connection to a newly proposed Class; otherwise null.",
                ))
                set<JsonNode>("proposedConnectionDomainIri", nullableString(
                    2_000,
                    "The existing domain Class IRI for proposedConnectionLabel; otherwise null.",
                ))
                set<JsonNode>("reasoningSummary", nullableString(
                    2_000,
                    "A concise explanation of why this meaning should affect the ontology, or null; do not provide hidden chain-of-thought.",
                ))
                putObject("confidence").put("type", "integer").put("minimum", 0).put("maximum", 100)
                set<JsonNode>("interpretation", stringEnum(
                    APPROVED_DOCUMENT_INTERPRETATIONS,
                    "How directly the document supports the candidate.",
                ))
                set<JsonNode>("evidenceType", stringEnum(
                    PROVIDER_DOCUMENT_EVIDENCE_TYPES,
                    "The permitted document evidence classification.",
                ))
                set<JsonNode>("evidence", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("minItems", 1)
                    put("maxItems", 8)
                    set<JsonNode>("items", objectMapper.createObjectNode().apply {
                        put("type", "object")
                        put("additionalProperties", false)
                        set<JsonNode>("required", objectMapper.valueToTree(EVIDENCE_FIELDS.sorted()))
                        set<JsonNode>("properties", objectMapper.createObjectNode().apply {
                            putObject("documentId")
                                .put("type", "string")
                                .put("description", "Copy the opaque documentId from the selected input block exactly.")
                            putObject("blockId")
                                .put("type", "string")
                                .put("description", "Copy the opaque blockId from the selected input block exactly.")
                            putObject("startOffsetInBlock")
                                .put("type", "integer")
                                .put("minimum", 0)
                                .put("description", "Zero-based inclusive offset in the exact input block text.")
                            putObject("endOffsetInBlock")
                                .put("type", "integer")
                                .put("minimum", 1)
                                .put("description", "Exclusive offset in the exact input block text.")
                            putObject("excerpt")
                                .put("type", "string")
                                .put("maxLength", 8_000)
                                .put("description", "Exact input substring between the supplied offsets, preserving whitespace and punctuation.")
                        })
                    })
                })
                set<JsonNode>("ambiguityFlags", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("maxItems", 20)
                    set<JsonNode>("items", objectMapper.createObjectNode().put("type", "string").put("maxLength", 500))
                })
            })
        }
        val schema = objectMapper.createObjectNode().apply {
            put("type", "object")
            put("additionalProperties", false)
            set<JsonNode>("required", objectMapper.valueToTree(listOf("schemaVersion", "candidates")))
            set<JsonNode>("properties", objectMapper.createObjectNode().apply {
                putObject("schemaVersion").put("type", "string").put("const", RESPONSE_SCHEMA_VERSION)
                set<JsonNode>("candidates", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("maxItems", 200)
                    set<JsonNode>("items", candidate)
                })
            })
        }
        return objectMapper.createObjectNode().apply {
            set<JsonNode>("format", objectMapper.createObjectNode().apply {
                put("type", "json_schema")
                put("name", "phase_11_document_analysis")
                put("strict", true)
                set<JsonNode>("schema", schema)
            })
        }
    }

    private fun stringEnum(values: List<String>, description: String): JsonNode =
        objectMapper.createObjectNode().apply {
            put("type", "string")
            put("description", description)
            set<JsonNode>("enum", objectMapper.valueToTree(values))
        }

    private fun nullableString(maxLength: Int, description: String): JsonNode =
        objectMapper.createObjectNode().apply {
            putArray("type").add("string").add("null")
            put("maxLength", maxLength)
            put("description", description)
        }

    private fun nullableEnum(values: List<String>, description: String): JsonNode =
        objectMapper.createObjectNode().apply {
            putArray("type").add("string").add("null")
            put("description", description)
            set<JsonNode>("enum", objectMapper.createArrayNode().apply {
                values.forEach(::add)
                addNull()
            })
        }

    private fun extractOutputText(response: String): String {
        val root = objectMapper.readTree(response)
        if (root.path("status").asText() == "incomplete") {
            throw SafeProviderResponseFailure(
                when (root.path("incomplete_details").path("reason").asText()) {
                    "max_output_tokens" -> "document-provider-output-token-limit"
                    "content_filter" -> "document-provider-content-filter"
                    else -> "document-provider-incomplete-output"
                },
            )
        }
        val refusal = root.path("output").flatMap { output ->
            output.path("content").filter { it.path("type").asText() == "refusal" }
        }
        if (refusal.isNotEmpty()) {
            throw SafeProviderResponseFailure("document-provider-refusal")
        }
        val texts = root.path("output").flatMap { output ->
            output.path("content").filter { it.path("type").asText() == "output_text" }.map { it.path("text").asText() }
        }
        return texts.joinToString("").takeIf(String::isNotBlank)
            ?: throw SafeProviderResponseFailure("document-provider-empty-output")
    }

    private fun parseStrictResponse(value: String): DocumentAnalysisResponse {
        val root = objectMapper.readTree(value)
        require(root.isObject && root.fieldNames().asSequence().toSet() == setOf("schemaVersion", "candidates"))
        require(root.path("schemaVersion").asText() == RESPONSE_SCHEMA_VERSION)
        val candidates = root.path("candidates")
        require(candidates.isArray && candidates.size() <= 200)
        return DocumentAnalysisResponse(
            candidates = candidates.map { candidate ->
                require(candidate.isObject && candidate.fieldNames().asSequence().toSet() == CANDIDATE_FIELDS)
                val evidence = candidate.path("evidence")
                require(evidence.isArray && evidence.size() in 1..8)
                ProviderDocumentCandidate(
                    category = candidate.requiredText("category"),
                    recommendationCategory = candidate.requiredText("recommendationCategory"),
                    proposedLabel = candidate.requiredText("proposedLabel"),
                    proposedDefinition = candidate.optionalText("proposedDefinition"),
                    proposedDomainIri = candidate.optionalText("proposedDomainIri"),
                    proposedRangeIri = candidate.optionalText("proposedRangeIri"),
                    proposedConnectionLabel = candidate.optionalText("proposedConnectionLabel"),
                    proposedConnectionDomainIri = candidate.optionalText("proposedConnectionDomainIri"),
                    reasoningSummary = candidate.optionalText("reasoningSummary"),
                    confidence = candidate.path("confidence").takeIf(JsonNode::isIntegralNumber)?.intValue()
                        ?: throw IllegalArgumentException("Invalid confidence."),
                    interpretation = candidate.requiredText("interpretation"),
                    evidenceType = candidate.requiredText("evidenceType"),
                    evidence = evidence.map { claim ->
                        require(claim.isObject && claim.fieldNames().asSequence().toSet() == EVIDENCE_FIELDS)
                        ProviderEvidenceClaim(
                            documentId = claim.requiredText("documentId"),
                            blockId = claim.requiredText("blockId"),
                            startOffsetInBlock = claim.requiredInteger("startOffsetInBlock"),
                            endOffsetInBlock = claim.requiredInteger("endOffsetInBlock"),
                            excerpt = claim.requiredText("excerpt"),
                        )
                    },
                    ambiguityFlags = candidate.path("ambiguityFlags").map { it.asText() },
                )
            },
        )
    }

    private fun parseStrictDiscoveryResponse(value: String): DocumentDiscoveryResponse {
        val root = objectMapper.readTree(value)
        require(root.isObject && root.fieldNames().asSequence().toSet() == setOf("schemaVersion", "discoveries"))
        require(root.path("schemaVersion").asText() == DocumentAnalysisPipelineVersions.DISCOVERY_RESPONSE)
        val discoveries = root.path("discoveries")
        require(discoveries.isArray && discoveries.size() <= MAX_DOCUMENT_DISCOVERIES_PER_DOCUMENT)
        return DocumentDiscoveryResponse(
            discoveries = discoveries.map { discovery ->
                require(discovery.isObject && discovery.fieldNames().asSequence().toSet() == DISCOVERY_FIELDS)
                val evidence = discovery.path("evidence")
                require(evidence.isArray && evidence.size() in 1..8)
                val related = discovery.path("relatedProviderIds")
                require(related.isArray && related.size() <= MAX_DOCUMENT_DISCOVERIES_PER_DOCUMENT)
                ProviderDocumentDiscovery(
                    providerId = discovery.requiredText("providerId"),
                    kind = discovery.requiredText("kind"),
                    contentClassification = discovery.requiredText("contentClassification"),
                    assertionClassification = discovery.requiredText("assertionClassification"),
                    description = discovery.requiredText("description"),
                    evidence = evidence.map { claim ->
                        require(claim.isObject && claim.fieldNames().asSequence().toSet() == EVIDENCE_FIELDS)
                        ProviderEvidenceClaim(
                            documentId = claim.requiredText("documentId"),
                            blockId = claim.requiredText("blockId"),
                            startOffsetInBlock = claim.requiredInteger("startOffsetInBlock"),
                            endOffsetInBlock = claim.requiredInteger("endOffsetInBlock"),
                            excerpt = claim.requiredText("excerpt"),
                        )
                    },
                    relatedProviderIds = related.map { item ->
                        item.takeIf(JsonNode::isTextual)?.asText()
                            ?: throw IllegalArgumentException("Invalid related discovery identity.")
                    },
                    evidenceConfidence =
                        discovery.path("evidenceConfidence").takeIf(JsonNode::isIntegralNumber)?.intValue()
                            ?: throw IllegalArgumentException("Invalid evidence confidence."),
                    individualClassification = discovery.optionalText("individualClassification"),
                )
            },
        )
    }

    private fun parseStrictConnectedModelResponse(
        value: String,
        responseSchemaVersion: String,
    ): List<ProviderConnectedModelItem> {
        val root = objectMapper.readTree(value)
        require(root.isObject && root.fieldNames().asSequence().toSet() == setOf("schemaVersion", "items"))
        require(root.path("schemaVersion").asText() == responseSchemaVersion)
        val items = root.path("items")
        require(items.isArray && items.size() in 1..MAX_DOCUMENT_CONNECTED_MODEL_ITEMS)
        return items.map { item ->
            require(item.isObject && item.fieldNames().asSequence().toSet() == CONNECTED_MODEL_FIELDS)
            val discoveryIds = item.path("discoveryIds")
            require(discoveryIds.isArray && discoveryIds.size() in 1..2_000)
            val references = item.path("references")
            require(references.isArray && references.size() <= 20)
            ProviderConnectedModelItem(
                providerId = item.requiredText("providerId"),
                kind = item.requiredText("kind"),
                label = item.requiredText("label"),
                rationale = item.requiredText("rationale"),
                discoveryIds = discoveryIds.map { discovery ->
                    discovery.takeIf(JsonNode::isTextual)?.asText()
                        ?: throw IllegalArgumentException("Invalid discovery identity.")
                },
                references = references.map { reference ->
                    require(
                        reference.isObject &&
                            reference.fieldNames().asSequence().toSet() == CONNECTED_MODEL_REFERENCE_FIELDS,
                    )
                    ProviderConnectedModelReference(
                        role = reference.requiredText("role"),
                        providerItemId = reference.requiredText("providerItemId"),
                    )
                },
                literalLexicalForm = item.optionalText("literalLexicalForm"),
                literalDatatypeIri = item.optionalText("literalDatatypeIri"),
                literalLanguageTag = item.optionalText("literalLanguageTag"),
                order = item.requiredInteger("order"),
                reviewOnlyEligible = item.path("reviewOnlyEligible").takeIf(JsonNode::isBoolean)?.booleanValue()
                    ?: throw IllegalArgumentException("Missing required boolean."),
            )
        }
    }

    private fun JsonNode.requiredText(name: String): String =
        path(name).takeIf(JsonNode::isTextual)?.asText()?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Missing required text.")

    private fun JsonNode.requiredInteger(name: String): Int =
        path(name).takeIf(JsonNode::isIntegralNumber)?.intValue()
            ?: throw IllegalArgumentException("Missing required integer.")

    private fun JsonNode.optionalText(name: String): String? {
        val value = path(name)
        return when {
            value.isNull -> null
            value.isTextual -> value.asText().trim().takeIf(String::isNotBlank)
            else -> throw IllegalArgumentException("Invalid optional text.")
        }
    }

    private companion object {
        const val RESPONSE_SCHEMA_VERSION: String = "phase-11-document-analysis-response-v4"
        const val MAX_PROVIDER_RESPONSE_CHARACTERS: Int = 1_000_000
        val EVIDENCE_FIELDS: Set<String> =
            setOf("documentId", "blockId", "startOffsetInBlock", "endOffsetInBlock", "excerpt")
        val CANDIDATE_FIELDS: Set<String> = setOf(
            "category",
            "recommendationCategory",
            "proposedLabel",
            "proposedDefinition",
            "proposedDomainIri",
            "proposedRangeIri",
            "proposedConnectionLabel",
            "proposedConnectionDomainIri",
            "reasoningSummary",
            "confidence",
            "interpretation",
            "evidenceType",
            "evidence",
            "ambiguityFlags",
        )
        val DISCOVERY_FIELDS: Set<String> = setOf(
            "providerId",
            "kind",
            "contentClassification",
            "assertionClassification",
            "description",
            "evidence",
            "relatedProviderIds",
            "evidenceConfidence",
            "individualClassification",
        )
        val CONNECTED_MODEL_REFERENCE_FIELDS: Set<String> = setOf(
            "role",
            "providerItemId",
        )
        val CONNECTED_MODEL_FIELDS: Set<String> = setOf(
            "providerId",
            "kind",
            "label",
            "rationale",
            "discoveryIds",
            "references",
            "literalLexicalForm",
            "literalDatatypeIri",
            "literalLanguageTag",
            "order",
            "reviewOnlyEligible",
        )
    }
}

private class SafeProviderResponseFailure(
    val code: String,
) : IllegalArgumentException(code)
