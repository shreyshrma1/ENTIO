package com.entio.web.ingestion

import com.entio.core.DocumentCandidateCategory
import com.entio.core.DocumentAlignmentAction
import com.entio.core.DocumentAnalysisWorkKey
import com.entio.core.DocumentAnalysisPipelineVersions
import com.entio.core.DocumentAssertionClassification
import com.entio.core.DocumentContentClassification
import com.entio.core.DocumentConnectedModelItemKind
import com.entio.core.DocumentConnectedModelReferenceRole
import com.entio.core.DocumentCriticAction
import com.entio.core.DocumentCriticDisposition
import com.entio.core.DocumentCriticDispositionKind
import com.entio.core.DocumentCoverageDisposition
import com.entio.core.DocumentCoverageDispositionKind
import com.entio.core.DocumentDiscoveryKind
import com.entio.core.DocumentEvidenceId
import com.entio.core.DocumentFinalPlan
import com.entio.core.DocumentFinalRecommendation
import com.entio.core.DocumentFinalRecommendationStatus
import com.entio.core.DocumentIndividualReviewGate
import com.entio.core.DocumentIndividualClassification
import com.entio.core.DocumentPlanOperand
import com.entio.core.DocumentPlanOperation
import com.entio.core.DocumentPlanOperationKind
import com.entio.core.DocumentReviewOnlyFinding
import com.entio.core.DocumentTemporaryReference
import com.entio.core.DocumentReconciliationKind
import com.entio.core.MAX_DOCUMENT_DISCOVERIES_PER_DOCUMENT
import com.entio.core.MAX_DOCUMENT_CONNECTED_MODEL_ITEMS
import com.entio.core.MAX_DOCUMENT_PROVIDER_RESPONSE_CHARACTERS
import com.entio.core.DocumentRecommendationCategory
import com.entio.core.Iri
import com.entio.core.RdfLiteral
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
) : DocumentPipelineProvider,
    AutoCloseable {
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

    override suspend fun reconcile(
        apiKey: String,
        selectedModelId: String,
        systemInstruction: String,
        request: DocumentReconciliationRequest,
    ): DocumentReconciliationProviderResult {
        if (apiKey.isBlank() || selectedModelId.isBlank()) {
            return DocumentReconciliationProviderResult.Failed(false, "document-provider-authorization")
        }
        return try {
            val response = client.post(configuration.endpoint) {
                header(HttpHeaders.Authorization, "Bearer ${apiKey.trim()}")
                accept(ContentType.Application.Json)
                setBody(
                    TextContent(
                        reconciliationRequestBody(selectedModelId, systemInstruction, request),
                        ContentType.Application.Json,
                    ),
                )
            }
            if (!response.status.isSuccess()) {
                return DocumentReconciliationProviderResult.Failed(
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
                return DocumentReconciliationProviderResult.Failed(false, "document-provider-response-limit")
            }
            DocumentReconciliationProviderResult.Completed(
                parseStrictReconciliationResponse(extractOutputText(responseText)),
            )
        } catch (failure: SafeProviderResponseFailure) {
            DocumentReconciliationProviderResult.Failed(false, failure.code)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: HttpRequestTimeoutException) {
            DocumentReconciliationProviderResult.Failed(true, "document-provider-timeout")
        } catch (_: IOException) {
            DocumentReconciliationProviderResult.Failed(true, "document-provider-unavailable")
        } catch (_: Exception) {
            DocumentReconciliationProviderResult.Failed(false, "document-provider-malformed-output")
        }
    }

    override suspend fun align(
        apiKey: String,
        selectedModelId: String,
        systemInstruction: String,
        request: DocumentOntologyAlignmentRequest,
    ): DocumentOntologyAlignmentProviderResult {
        if (apiKey.isBlank() || selectedModelId.isBlank()) {
            return DocumentOntologyAlignmentProviderResult.Failed(false, "document-provider-authorization")
        }
        return try {
            val response = client.post(configuration.endpoint) {
                header(HttpHeaders.Authorization, "Bearer ${apiKey.trim()}")
                accept(ContentType.Application.Json)
                setBody(
                    TextContent(
                        ontologyAlignmentRequestBody(selectedModelId, systemInstruction, request),
                        ContentType.Application.Json,
                    ),
                )
            }
            if (!response.status.isSuccess()) {
                return DocumentOntologyAlignmentProviderResult.Failed(
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
                return DocumentOntologyAlignmentProviderResult.Failed(false, "document-provider-response-limit")
            }
            DocumentOntologyAlignmentProviderResult.Completed(
                parseStrictOntologyAlignmentResponse(extractOutputText(responseText)),
            )
        } catch (failure: SafeProviderResponseFailure) {
            DocumentOntologyAlignmentProviderResult.Failed(false, failure.code)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: HttpRequestTimeoutException) {
            DocumentOntologyAlignmentProviderResult.Failed(true, "document-provider-timeout")
        } catch (_: IOException) {
            DocumentOntologyAlignmentProviderResult.Failed(true, "document-provider-unavailable")
        } catch (_: Exception) {
            DocumentOntologyAlignmentProviderResult.Failed(false, "document-provider-malformed-output")
        }
    }

    override suspend fun critique(
        apiKey: String,
        selectedModelId: String,
        systemInstruction: String,
        request: DocumentModelingCriticRequest,
    ): DocumentModelingCriticProviderResult {
        if (apiKey.isBlank() || selectedModelId.isBlank()) {
            return DocumentModelingCriticProviderResult.Failed(false, "document-provider-authorization")
        }
        return try {
            val response = client.post(configuration.endpoint) {
                header(HttpHeaders.Authorization, "Bearer ${apiKey.trim()}")
                accept(ContentType.Application.Json)
                setBody(
                    TextContent(
                        modelingCriticRequestBody(selectedModelId, systemInstruction, request),
                        ContentType.Application.Json,
                    ),
                )
            }
            if (!response.status.isSuccess()) {
                return DocumentModelingCriticProviderResult.Failed(
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
                return DocumentModelingCriticProviderResult.Failed(false, "document-provider-response-limit")
            }
            DocumentModelingCriticProviderResult.Completed(
                parseStrictModelingCriticResponse(extractOutputText(responseText)),
            )
        } catch (failure: SafeProviderResponseFailure) {
            DocumentModelingCriticProviderResult.Failed(false, failure.code)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: HttpRequestTimeoutException) {
            DocumentModelingCriticProviderResult.Failed(true, "document-provider-timeout")
        } catch (_: IOException) {
            DocumentModelingCriticProviderResult.Failed(true, "document-provider-unavailable")
        } catch (_: Exception) {
            DocumentModelingCriticProviderResult.Failed(false, "document-provider-malformed-output")
        }
    }

    override suspend fun plan(
        apiKey: String,
        selectedModelId: String,
        systemInstruction: String,
        request: DocumentFinalPlanningRequest,
    ): DocumentFinalPlanningProviderResult {
        if (apiKey.isBlank() || selectedModelId.isBlank()) {
            return DocumentFinalPlanningProviderResult.Failed(false, "document-provider-authorization")
        }
        return try {
            val response = client.post(configuration.endpoint) {
                header(HttpHeaders.Authorization, "Bearer ${apiKey.trim()}")
                accept(ContentType.Application.Json)
                setBody(
                    TextContent(
                        finalPlanningRequestBody(selectedModelId, systemInstruction, request),
                        ContentType.Application.Json,
                    ),
                )
            }
            if (!response.status.isSuccess()) {
                return DocumentFinalPlanningProviderResult.Failed(
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
                return DocumentFinalPlanningProviderResult.Failed(false, "document-provider-response-limit")
            }
            DocumentFinalPlanningProviderResult.Completed(
                parseStrictFinalPlanningResponse(extractOutputText(responseText)),
            )
        } catch (failure: SafeProviderResponseFailure) {
            DocumentFinalPlanningProviderResult.Failed(false, failure.code)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: HttpRequestTimeoutException) {
            DocumentFinalPlanningProviderResult.Failed(true, "document-provider-timeout")
        } catch (_: IOException) {
            DocumentFinalPlanningProviderResult.Failed(true, "document-provider-unavailable")
        } catch (_: Exception) {
            DocumentFinalPlanningProviderResult.Failed(false, "document-provider-malformed-output")
        }
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

    private fun reconciliationRequestBody(
        modelId: String,
        instruction: String,
        request: DocumentReconciliationRequest,
    ): String {
        val root = objectMapper.createObjectNode()
        root.put("model", modelId)
        root.put("store", false)
        root.putArray("tools")
        root.put("instructions", instruction)
        root.put("input", objectMapper.writeValueAsString(request))
        root.set<JsonNode>("text", strictReconciliationTextFormat())
        return objectMapper.writeValueAsString(root)
    }

    private fun ontologyAlignmentRequestBody(
        modelId: String,
        instruction: String,
        request: DocumentOntologyAlignmentRequest,
    ): String {
        val root = objectMapper.createObjectNode()
        root.put("model", modelId)
        root.put("store", false)
        root.putArray("tools")
        root.put("instructions", instruction)
        root.put("input", objectMapper.writeValueAsString(request))
        root.set<JsonNode>("text", strictOntologyAlignmentTextFormat())
        return objectMapper.writeValueAsString(root)
    }

    private fun modelingCriticRequestBody(
        modelId: String,
        instruction: String,
        request: DocumentModelingCriticRequest,
    ): String {
        val root = objectMapper.createObjectNode()
        root.put("model", modelId)
        root.put("store", false)
        root.putArray("tools")
        root.put("instructions", instruction)
        root.put("input", objectMapper.writeValueAsString(request))
        root.set<JsonNode>("text", strictModelingCriticTextFormat())
        return objectMapper.writeValueAsString(root)
    }

    private fun finalPlanningRequestBody(
        modelId: String,
        instruction: String,
        request: DocumentFinalPlanningRequest,
    ): String {
        val root = objectMapper.createObjectNode()
        root.put("model", modelId)
        root.put("store", false)
        root.putArray("tools")
        root.put("instructions", instruction)
        root.put("input", objectMapper.writeValueAsString(request))
        root.set<JsonNode>("text", strictFinalPlanningTextFormat())
        return objectMapper.writeValueAsString(root)
    }

    private fun strictFinalPlanningTextFormat(): JsonNode {
        fun objectSchema(required: List<String>, properties: JsonNode): JsonNode =
            objectMapper.createObjectNode().apply {
                put("type", "object")
                put("additionalProperties", false)
                set<JsonNode>("required", objectMapper.valueToTree(required.sorted()))
                set<JsonNode>("properties", properties)
            }
        fun stringArray(maxItems: Int): JsonNode = boundedUniqueStringArray(0, maxItems, 500)
        val operand = objectSchema(
            FINAL_OPERAND_FIELDS.toList(),
            objectMapper.createObjectNode().apply {
                set<JsonNode>("kind", stringEnum(FINAL_OPERAND_KINDS, "Typed operand kind."))
                putObject("value").put("type", "string").put("minLength", 1).put("maxLength", 2_000)
                set<JsonNode>("datatypeIri", nullableString(2_000, "Literal datatype IRI."))
                set<JsonNode>("language", nullableString(100, "Literal language tag."))
            },
        )
        val operation = objectSchema(
            FINAL_OPERATION_FIELDS.toList(),
            objectMapper.createObjectNode().apply {
                putObject("id").put("type", "string").put("minLength", 1).put("maxLength", 200)
                set<JsonNode>("kind", stringEnum(DocumentPlanOperationKind.entries.map { it.name }, "Typed operation."))
                putObject("order").put("type", "integer").put("minimum", 0).put("maximum", 99)
                set<JsonNode>("declaration", nullableString(500, "Temporary new:<kind>:<localName> reference."))
                set<JsonNode>("operands", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("maxItems", 20)
                    set<JsonNode>("items", operand)
                })
                set<JsonNode>("dependsOnOperationIds", stringArray(20))
                putObject("expandedTypedEditCount").put("type", "integer").put("minimum", 1).put("maximum", 20)
                putObject("optionalLeaf").put("type", "boolean")
            },
        )
        val reviewOnly = objectSchema(
            FINAL_REVIEW_ONLY_FIELDS.toList(),
            objectMapper.createObjectNode().apply {
                putObject("id").put("type", "string").put("minLength", 1).put("maxLength", 200)
                putObject("summary").put("type", "string").put("minLength", 1).put("maxLength", 1_000)
                putObject("reason").put("type", "string").put("minLength", 1).put("maxLength", 2_000)
                set<JsonNode>("discoveryIds", stringArray(100))
                set<JsonNode>("evidenceIds", stringArray(8))
                set<JsonNode>("relatedOperationIds", stringArray(20))
            },
        )
        val criticDisposition = objectSchema(
            FINAL_CRITIC_DISPOSITION_FIELDS.toList(),
            objectMapper.createObjectNode().apply {
                putObject("findingId").put("type", "string").put("minLength", 1).put("maxLength", 200)
                set<JsonNode>(
                    "kind",
                    stringEnum(DocumentCriticDispositionKind.entries.map { it.name }, "Final critic disposition."),
                )
                set<JsonNode>("rationale", nullableString(2_000, "Required rejection rationale."))
            },
        )
        val individualGate = objectSchema(
            FINAL_INDIVIDUAL_GATE_FIELDS.toList(),
            objectMapper.createObjectNode().apply {
                putObject("operationId").put("type", "string").put("minLength", 1).put("maxLength", 200)
                set<JsonNode>(
                    "classification",
                    stringEnum(DocumentIndividualClassification.entries.map { it.name }, "Individual classification."),
                )
                putObject("creationConfirmed").put("type", "boolean")
                putObject("productionClassificationConfirmed").put("type", "boolean")
            },
        )
        val recommendation = objectSchema(
            FINAL_RECOMMENDATION_FIELDS.toList(),
            objectMapper.createObjectNode().apply {
                putObject("id").put("type", "string").put("minLength", 1).put("maxLength", 200)
                putObject("title").put("type", "string").put("minLength", 1).put("maxLength", 500)
                putObject("description").put("type", "string").put("minLength", 1).put("maxLength", 2_000)
                set<JsonNode>("discoveryIds", stringArray(100))
                set<JsonNode>("evidenceIds", stringArray(8))
                set<JsonNode>("operations", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("maxItems", 20)
                    set<JsonNode>("items", operation)
                })
                set<JsonNode>("reviewOnlyFindings", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("maxItems", 20)
                    set<JsonNode>("items", reviewOnly)
                })
                set<JsonNode>("criticDispositions", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("maxItems", 600)
                    set<JsonNode>("items", criticDisposition)
                })
                listOf("evidenceConfidence", "modelingConfidence", "ontologyFitConfidence").forEach { field ->
                    putObject(field).put("type", "integer").put("minimum", 0).put("maximum", 100)
                }
                set<JsonNode>(
                    "status",
                    stringEnum(DocumentFinalRecommendationStatus.entries.map { it.name }, "Recommendation status."),
                )
                set<JsonNode>("blockers", stringArray(20))
                set<JsonNode>("individualReviewGates", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("maxItems", 20)
                    set<JsonNode>("items", individualGate)
                })
            },
        )
        val coverage = objectSchema(
            FINAL_COVERAGE_FIELDS.toList(),
            objectMapper.createObjectNode().apply {
                putObject("discoveryId").put("type", "string").put("minLength", 1).put("maxLength", 200)
                set<JsonNode>(
                    "kind",
                    stringEnum(DocumentCoverageDispositionKind.entries.map { it.name }, "Discovery disposition."),
                )
                set<JsonNode>("recommendationId", nullableString(200, "Related recommendation ID."))
                set<JsonNode>("relatedDiscoveryId", nullableString(200, "Merged discovery ID."))
                set<JsonNode>("rationale", nullableString(2_000, "Required rejection rationale."))
            },
        )
        val plan = objectSchema(
            FINAL_PLAN_FIELDS.toList(),
            objectMapper.createObjectNode().apply {
                putObject("workKey").put("type", "string").put("pattern", "^[a-f0-9]{64}$")
                set<JsonNode>("verifiedDiscoveryIds", stringArray(500))
                set<JsonNode>("criticFindingIds", stringArray(600))
                set<JsonNode>("recommendations", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("maxItems", 100)
                    set<JsonNode>("items", recommendation)
                })
                set<JsonNode>("coverage", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("maxItems", 500)
                    set<JsonNode>("items", coverage)
                })
            },
        )
        val schema = objectSchema(
            listOf("schemaVersion", "plan"),
            objectMapper.createObjectNode().apply {
                putObject("schemaVersion")
                    .put("type", "string")
                    .put("const", DocumentAnalysisPipelineVersions.FINAL_PLAN_RESPONSE)
                set<JsonNode>("plan", plan)
            },
        )
        return objectMapper.createObjectNode().apply {
            set<JsonNode>("format", objectMapper.createObjectNode().apply {
                put("type", "json_schema")
                put("name", "phase_11_5_document_final_plan")
                put("strict", true)
                set<JsonNode>("schema", schema)
            })
        }
    }

    private fun strictModelingCriticTextFormat(): JsonNode {
        val finding = objectMapper.createObjectNode().apply {
            put("type", "object")
            put("additionalProperties", false)
            set<JsonNode>("required", objectMapper.valueToTree(MODELING_CRITIC_FIELDS.sorted()))
            set<JsonNode>("properties", objectMapper.createObjectNode().apply {
                putObject("providerId")
                    .put("type", "string")
                    .put("pattern", "^[A-Za-z0-9][A-Za-z0-9._:-]{0,199}$")
                putObject("targetId").put("type", "string").put("minLength", 1).put("maxLength", 200)
                set<JsonNode>("action", stringEnum(
                    DocumentCriticAction.entries.map { it.name },
                    "The advisory modeling-critic action.",
                ))
                putObject("reason").put("type", "string").put("minLength", 1).put("maxLength", 2_000)
                listOf("evidenceConfidence", "modelingConfidence", "ontologyFitConfidence").forEach { name ->
                    putObject(name).put("type", "integer").put("minimum", 0).put("maximum", 100)
                }
            })
        }
        val schema = objectMapper.createObjectNode().apply {
            put("type", "object")
            put("additionalProperties", false)
            set<JsonNode>("required", objectMapper.valueToTree(listOf("schemaVersion", "findings")))
            set<JsonNode>("properties", objectMapper.createObjectNode().apply {
                putObject("schemaVersion")
                    .put("type", "string")
                    .put("const", DocumentAnalysisPipelineVersions.MODELING_CRITIC_RESPONSE)
                set<JsonNode>("findings", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("maxItems", 600)
                    set<JsonNode>("items", finding)
                })
            })
        }
        return objectMapper.createObjectNode().apply {
            set<JsonNode>("format", objectMapper.createObjectNode().apply {
                put("type", "json_schema")
                put("name", "phase_11_5_document_modeling_critic")
                put("strict", true)
                set<JsonNode>("schema", schema)
            })
        }
    }

    private fun strictOntologyAlignmentTextFormat(): JsonNode {
        val record = objectMapper.createObjectNode().apply {
            put("type", "object")
            put("additionalProperties", false)
            set<JsonNode>("required", objectMapper.valueToTree(ONTOLOGY_ALIGNMENT_FIELDS.sorted()))
            set<JsonNode>("properties", objectMapper.createObjectNode().apply {
                putObject("providerId")
                    .put("type", "string")
                    .put("pattern", "^[A-Za-z0-9][A-Za-z0-9._:-]{0,199}$")
                putObject("modelItemId").put("type", "string").put("maxLength", 200)
                set<JsonNode>("action", stringEnum(
                    DocumentAlignmentAction.entries.map { it.name },
                    "The advisory ontology-alignment action.",
                ))
                set<JsonNode>("advisedReferenceIds", boundedUniqueStringArray(0, 20, 200))
                set<JsonNode>("targetSourceId", nullableString(200, "A supplied writable ontology source ID."))
                putObject("rationale").put("type", "string").put("minLength", 1).put("maxLength", 2_000)
                putObject("ontologyFitConfidence").put("type", "integer").put("minimum", 0).put("maximum", 100)
                set<JsonNode>(
                    "domainRangeRationale",
                    nullableString(2_000, "Required rationale for domain and range assignment items."),
                )
            })
        }
        val schema = objectMapper.createObjectNode().apply {
            put("type", "object")
            put("additionalProperties", false)
            set<JsonNode>("required", objectMapper.valueToTree(listOf("schemaVersion", "records")))
            set<JsonNode>("properties", objectMapper.createObjectNode().apply {
                putObject("schemaVersion")
                    .put("type", "string")
                    .put("const", DocumentAnalysisPipelineVersions.ONTOLOGY_ALIGNMENT_RESPONSE)
                set<JsonNode>("records", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("maxItems", 300)
                    set<JsonNode>("items", record)
                })
            })
        }
        return objectMapper.createObjectNode().apply {
            set<JsonNode>("format", objectMapper.createObjectNode().apply {
                put("type", "json_schema")
                put("name", "phase_11_5_document_ontology_alignment")
                put("strict", true)
                set<JsonNode>("schema", schema)
            })
        }
    }

    private fun strictReconciliationTextFormat(): JsonNode {
        val record = objectMapper.createObjectNode().apply {
            put("type", "object")
            put("additionalProperties", false)
            set<JsonNode>("required", objectMapper.valueToTree(RECONCILIATION_FIELDS.sorted()))
            set<JsonNode>("properties", objectMapper.createObjectNode().apply {
                putObject("providerId")
                    .put("type", "string")
                    .put("pattern", "^[A-Za-z0-9][A-Za-z0-9._:-]{0,199}$")
                set<JsonNode>("kind", stringEnum(
                    DocumentReconciliationKind.entries.map { it.name },
                    "The relationship among supplied task or prior-provenance meanings.",
                ))
                set<JsonNode>("participantIds", boundedUniqueStringArray(2, 20, 200))
                set<JsonNode>("evidenceIds", boundedUniqueStringArray(0, 80, 200))
                set<JsonNode>("priorProvenanceIds", boundedUniqueStringArray(0, 25, 200))
                putObject("explanation").put("type", "string").put("minLength", 1).put("maxLength", 2_000)
                putObject("humanDecisionRequired").put("type", "boolean")
            })
        }
        val schema = objectMapper.createObjectNode().apply {
            put("type", "object")
            put("additionalProperties", false)
            set<JsonNode>("required", objectMapper.valueToTree(listOf("schemaVersion", "records")))
            set<JsonNode>("properties", objectMapper.createObjectNode().apply {
                putObject("schemaVersion")
                    .put("type", "string")
                    .put("const", DocumentAnalysisPipelineVersions.RECONCILIATION_RESPONSE)
                set<JsonNode>("records", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("maxItems", 300)
                    set<JsonNode>("items", record)
                })
            })
        }
        return objectMapper.createObjectNode().apply {
            set<JsonNode>("format", objectMapper.createObjectNode().apply {
                put("type", "json_schema")
                put("name", "phase_11_5_document_reconciliation")
                put("strict", true)
                set<JsonNode>("schema", schema)
            })
        }
    }

    private fun boundedUniqueStringArray(
        minItems: Int,
        maxItems: Int,
        maxLength: Int,
    ): JsonNode = objectMapper.createObjectNode().apply {
        put("type", "array")
        put("minItems", minItems)
        put("maxItems", maxItems)
        put("uniqueItems", true)
        set<JsonNode>("items", objectMapper.createObjectNode().put("type", "string").put("maxLength", maxLength))
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

    private fun parseStrictReconciliationResponse(value: String): DocumentReconciliationResponse {
        val root = objectMapper.readTree(value)
        require(root.isObject && root.fieldNames().asSequence().toSet() == setOf("schemaVersion", "records"))
        require(root.path("schemaVersion").asText() == DocumentAnalysisPipelineVersions.RECONCILIATION_RESPONSE)
        val records = root.path("records")
        require(records.isArray && records.size() <= 300)
        return DocumentReconciliationResponse(
            records = records.map { record ->
                require(record.isObject && record.fieldNames().asSequence().toSet() == RECONCILIATION_FIELDS)
                ProviderDocumentReconciliation(
                    providerId = record.requiredText("providerId"),
                    kind = record.requiredText("kind"),
                    participantIds = record.requiredTextArray("participantIds", 2, 20),
                    evidenceIds = record.requiredTextArray("evidenceIds", 0, 80),
                    priorProvenanceIds = record.requiredTextArray("priorProvenanceIds", 0, 25),
                    explanation = record.requiredText("explanation"),
                    humanDecisionRequired =
                        record.path("humanDecisionRequired").takeIf(JsonNode::isBoolean)?.booleanValue()
                            ?: throw IllegalArgumentException("Missing required boolean."),
                )
            },
        )
    }

    private fun parseStrictOntologyAlignmentResponse(value: String): DocumentOntologyAlignmentResponse {
        val root = objectMapper.readTree(value)
        require(root.isObject && root.fieldNames().asSequence().toSet() == setOf("schemaVersion", "records"))
        require(root.path("schemaVersion").asText() == DocumentAnalysisPipelineVersions.ONTOLOGY_ALIGNMENT_RESPONSE)
        val records = root.path("records")
        require(records.isArray && records.size() <= 300)
        return DocumentOntologyAlignmentResponse(
            records = records.map { record ->
                require(record.isObject && record.fieldNames().asSequence().toSet() == ONTOLOGY_ALIGNMENT_FIELDS)
                ProviderDocumentOntologyAlignment(
                    providerId = record.requiredText("providerId"),
                    modelItemId = record.requiredText("modelItemId"),
                    action = record.requiredText("action"),
                    advisedReferenceIds = record.requiredTextArray("advisedReferenceIds", 0, 20),
                    targetSourceId = record.optionalText("targetSourceId"),
                    rationale = record.requiredText("rationale"),
                    ontologyFitConfidence = record.requiredInteger("ontologyFitConfidence"),
                    domainRangeRationale = record.optionalText("domainRangeRationale"),
                )
            },
        )
    }

    private fun parseStrictModelingCriticResponse(value: String): DocumentModelingCriticResponse {
        val root = objectMapper.readTree(value)
        require(root.isObject && root.fieldNames().asSequence().toSet() == setOf("schemaVersion", "findings"))
        require(root.path("schemaVersion").asText() == DocumentAnalysisPipelineVersions.MODELING_CRITIC_RESPONSE)
        val findings = root.path("findings")
        require(findings.isArray && findings.size() <= 600)
        return DocumentModelingCriticResponse(
            findings = findings.map { finding ->
                require(finding.isObject && finding.fieldNames().asSequence().toSet() == MODELING_CRITIC_FIELDS)
                ProviderDocumentCriticFinding(
                    providerId = finding.requiredText("providerId"),
                    targetId = finding.requiredText("targetId"),
                    action = finding.requiredText("action"),
                    reason = finding.requiredText("reason"),
                    evidenceConfidence = finding.requiredInteger("evidenceConfidence"),
                    modelingConfidence = finding.requiredInteger("modelingConfidence"),
                    ontologyFitConfidence = finding.requiredInteger("ontologyFitConfidence"),
                )
            },
        )
    }

    private fun parseStrictFinalPlanningResponse(value: String): DocumentFinalPlanningResponse {
        val root = objectMapper.readTree(value)
        require(root.isObject && root.fieldNames().asSequence().toSet() == setOf("schemaVersion", "plan"))
        require(root.requiredText("schemaVersion") == DocumentAnalysisPipelineVersions.FINAL_PLAN_RESPONSE)
        val planNode = root.path("plan")
        require(planNode.isObject && planNode.fieldNames().asSequence().toSet() == FINAL_PLAN_FIELDS)
        val recommendations = planNode.path("recommendations")
        val coverage = planNode.path("coverage")
        require(recommendations.isArray && recommendations.size() <= 100)
        require(coverage.isArray)
        val plan = DocumentFinalPlan(
            workKey = DocumentAnalysisWorkKey(planNode.requiredText("workKey")),
            verifiedDiscoveryIds = planNode.requiredTextArray("verifiedDiscoveryIds", 1, 500).sorted(),
            criticFindingIds = planNode.requiredTextArray("criticFindingIds", 0, 600).sorted(),
            recommendations = recommendations.map(::parseFinalRecommendation)
                .sortedBy(DocumentFinalRecommendation::stableOrderingKey),
            coverage = coverage.map { node ->
                require(node.isObject && node.fieldNames().asSequence().toSet() == FINAL_COVERAGE_FIELDS)
                DocumentCoverageDisposition(
                    discoveryId = node.requiredText("discoveryId"),
                    kind = DocumentCoverageDispositionKind.valueOf(node.requiredText("kind")),
                    recommendationId = node.optionalText("recommendationId"),
                    relatedDiscoveryId = node.optionalText("relatedDiscoveryId"),
                    rationale = node.optionalText("rationale"),
                )
            }.sortedBy(DocumentCoverageDisposition::stableOrderingKey),
        )
        return DocumentFinalPlanningResponse(plan = plan)
    }

    private fun parseFinalRecommendation(node: JsonNode): DocumentFinalRecommendation {
        require(node.isObject && node.fieldNames().asSequence().toSet() == FINAL_RECOMMENDATION_FIELDS)
        val operations = node.path("operations")
        val reviewOnly = node.path("reviewOnlyFindings")
        val dispositions = node.path("criticDispositions")
        val individualGates = node.path("individualReviewGates")
        require(operations.isArray && operations.size() <= 20)
        require(reviewOnly.isArray && reviewOnly.size() <= 20)
        require(dispositions.isArray && dispositions.size() <= 600)
        require(individualGates.isArray && individualGates.size() <= 20)
        return DocumentFinalRecommendation(
            id = node.requiredText("id"),
            title = node.requiredText("title"),
            description = node.requiredText("description"),
            discoveryIds = node.requiredTextArray("discoveryIds", 1, 100).sorted(),
            evidenceIds = node.requiredTextArray("evidenceIds", 1, 8).map(::DocumentEvidenceId).sortedBy(DocumentEvidenceId::value),
            operations = operations.map(::parseFinalOperation).sortedBy(DocumentPlanOperation::order),
            reviewOnlyFindings = reviewOnly.map { finding ->
                require(finding.isObject && finding.fieldNames().asSequence().toSet() == FINAL_REVIEW_ONLY_FIELDS)
                DocumentReviewOnlyFinding(
                    id = finding.requiredText("id"),
                    summary = finding.requiredText("summary"),
                    reason = finding.requiredText("reason"),
                    discoveryIds = finding.requiredTextArray("discoveryIds", 1, 100).sorted(),
                    evidenceIds = finding.requiredTextArray("evidenceIds", 1, 8)
                        .map(::DocumentEvidenceId)
                        .sortedBy(DocumentEvidenceId::value),
                    relatedOperationIds = finding.requiredTextArray("relatedOperationIds", 0, 20).sorted(),
                )
            },
            criticDispositions = dispositions.map { disposition ->
                require(
                    disposition.isObject &&
                        disposition.fieldNames().asSequence().toSet() == FINAL_CRITIC_DISPOSITION_FIELDS,
                )
                DocumentCriticDisposition(
                    findingId = disposition.requiredText("findingId"),
                    kind = DocumentCriticDispositionKind.valueOf(disposition.requiredText("kind")),
                    rationale = disposition.optionalText("rationale"),
                )
            }.sortedBy(DocumentCriticDisposition::stableOrderingKey),
            confidence = com.entio.core.DocumentConfidenceDimensions(
                evidence = node.requiredInteger("evidenceConfidence"),
                modeling = node.requiredInteger("modelingConfidence"),
                ontologyFit = node.requiredInteger("ontologyFitConfidence"),
            ),
            status = DocumentFinalRecommendationStatus.valueOf(node.requiredText("status")),
            blockers = node.requiredTextArray("blockers", 0, 20).sorted(),
            individualReviewGates = individualGates.map { gate ->
                require(gate.isObject && gate.fieldNames().asSequence().toSet() == FINAL_INDIVIDUAL_GATE_FIELDS)
                DocumentIndividualReviewGate(
                    operationId = gate.requiredText("operationId"),
                    classification = DocumentIndividualClassification.valueOf(gate.requiredText("classification")),
                    creationConfirmed = gate.path("creationConfirmed").booleanValue(),
                    productionClassificationConfirmed = gate.path("productionClassificationConfirmed").booleanValue(),
                )
            }.sortedBy(DocumentIndividualReviewGate::operationId),
        )
    }

    private fun parseFinalOperation(node: JsonNode): DocumentPlanOperation {
        require(node.isObject && node.fieldNames().asSequence().toSet() == FINAL_OPERATION_FIELDS)
        val operands = node.path("operands")
        require(operands.isArray && operands.size() <= 20)
        return DocumentPlanOperation(
            id = node.requiredText("id"),
            kind = DocumentPlanOperationKind.valueOf(node.requiredText("kind")),
            order = node.requiredInteger("order"),
            declaration = node.optionalText("declaration")?.let(::DocumentTemporaryReference),
            operands = operands.map { operand ->
                require(operand.isObject && operand.fieldNames().asSequence().toSet() == FINAL_OPERAND_FIELDS)
                val text = operand.requiredText("value")
                when (operand.requiredText("kind")) {
                    "ExistingEntity" -> DocumentPlanOperand.ExistingEntity(Iri(text))
                    "TemporaryEntity" -> DocumentPlanOperand.TemporaryEntity(DocumentTemporaryReference(text))
                    "LiteralValue" -> DocumentPlanOperand.LiteralValue(
                        RdfLiteral(
                            lexicalForm = text,
                            datatypeIri = operand.optionalText("datatypeIri")?.let(::Iri),
                            languageTag = operand.optionalText("language"),
                        ),
                    )
                    "TextValue" -> DocumentPlanOperand.TextValue(text)
                    "IntegerValue" -> DocumentPlanOperand.IntegerValue(text.toInt())
                    "DecimalValue" -> DocumentPlanOperand.DecimalValue(text)
                    "SourceId" -> DocumentPlanOperand.SourceId(text)
                    else -> throw IllegalArgumentException("Unsupported final-plan operand kind.")
                }
            },
            dependsOnOperationIds = node.requiredTextArray("dependsOnOperationIds", 0, 20).sorted(),
            expandedTypedEditCount = node.requiredInteger("expandedTypedEditCount"),
            optionalLeaf = node.path("optionalLeaf").booleanValue(),
        )
    }

    private fun JsonNode.requiredText(name: String): String =
        path(name).takeIf(JsonNode::isTextual)?.asText()?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Missing required text.")

    private fun JsonNode.requiredInteger(name: String): Int =
        path(name).takeIf(JsonNode::isIntegralNumber)?.intValue()
            ?: throw IllegalArgumentException("Missing required integer.")

    private fun JsonNode.requiredTextArray(
        name: String,
        minItems: Int,
        maxItems: Int,
    ): List<String> {
        val values = path(name)
        require(values.isArray && values.size() in minItems..maxItems)
        return values.map { value ->
            value.takeIf(JsonNode::isTextual)?.asText()
                ?: throw IllegalArgumentException("Invalid required text array.")
        }
    }

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
        val RECONCILIATION_FIELDS: Set<String> = setOf(
            "providerId",
            "kind",
            "participantIds",
            "evidenceIds",
            "priorProvenanceIds",
            "explanation",
            "humanDecisionRequired",
        )
        val ONTOLOGY_ALIGNMENT_FIELDS: Set<String> = setOf(
            "providerId",
            "modelItemId",
            "action",
            "advisedReferenceIds",
            "targetSourceId",
            "rationale",
            "ontologyFitConfidence",
            "domainRangeRationale",
        )
        val MODELING_CRITIC_FIELDS: Set<String> = setOf(
            "providerId",
            "targetId",
            "action",
            "reason",
            "evidenceConfidence",
            "modelingConfidence",
            "ontologyFitConfidence",
        )
        val FINAL_PLAN_FIELDS: Set<String> = setOf(
            "workKey",
            "verifiedDiscoveryIds",
            "criticFindingIds",
            "recommendations",
            "coverage",
        )
        val FINAL_RECOMMENDATION_FIELDS: Set<String> = setOf(
            "id",
            "title",
            "description",
            "discoveryIds",
            "evidenceIds",
            "operations",
            "reviewOnlyFindings",
            "criticDispositions",
            "evidenceConfidence",
            "modelingConfidence",
            "ontologyFitConfidence",
            "status",
            "blockers",
            "individualReviewGates",
        )
        val FINAL_OPERATION_FIELDS: Set<String> = setOf(
            "id",
            "kind",
            "order",
            "declaration",
            "operands",
            "dependsOnOperationIds",
            "expandedTypedEditCount",
            "optionalLeaf",
        )
        val FINAL_OPERAND_FIELDS: Set<String> = setOf("kind", "value", "datatypeIri", "language")
        val FINAL_OPERAND_KINDS: List<String> = listOf(
            "ExistingEntity",
            "TemporaryEntity",
            "LiteralValue",
            "TextValue",
            "IntegerValue",
            "DecimalValue",
            "SourceId",
        )
        val FINAL_REVIEW_ONLY_FIELDS: Set<String> =
            setOf("id", "summary", "reason", "discoveryIds", "evidenceIds", "relatedOperationIds")
        val FINAL_CRITIC_DISPOSITION_FIELDS: Set<String> = setOf("findingId", "kind", "rationale")
        val FINAL_INDIVIDUAL_GATE_FIELDS: Set<String> =
            setOf("operationId", "classification", "creationConfirmed", "productionClassificationConfirmed")
        val FINAL_COVERAGE_FIELDS: Set<String> =
            setOf("discoveryId", "kind", "recommendationId", "relatedDiscoveryId", "rationale")
    }
}

private class SafeProviderResponseFailure(
    val code: String,
) : IllegalArgumentException(code)
