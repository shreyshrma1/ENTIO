package com.entio.web.ingestion

import com.entio.core.DocumentCandidateCategory
import com.entio.core.DocumentAlignmentAction
import com.entio.core.DocumentAnalysisWorkKey
import com.entio.core.DocumentAnalysisPipelineVersions
import com.entio.core.DocumentAssertionClassification
import com.entio.core.DocumentContentClassification
import com.entio.core.DocumentConnectedModelItem
import com.entio.core.DocumentConnectedModelItemKind
import com.entio.core.DocumentConnectedModelReferenceRole
import com.entio.core.DocumentConfidenceDimensions
import com.entio.core.DocumentCriticAction
import com.entio.core.DocumentCriticDisposition
import com.entio.core.DocumentCriticDispositionKind
import com.entio.core.DocumentCriticFinding
import com.entio.core.DocumentCoverageDisposition
import com.entio.core.DocumentCoverageDispositionKind
import com.entio.core.DocumentDiscovery
import com.entio.core.DocumentDiscoveryKind
import com.entio.core.DocumentEvidence
import com.entio.core.DocumentEvidenceId
import com.entio.core.DocumentFinalPlan
import com.entio.core.DocumentFinalRecommendation
import com.entio.core.DocumentFinalRecommendationStatus
import com.entio.core.DocumentGroundedAnalysisResult
import com.entio.core.DocumentGroundedCoverageDisposition
import com.entio.core.DocumentGroundedDisposition
import com.entio.core.DocumentGroundedReference
import com.entio.core.DocumentGroundedSemanticItem
import com.entio.core.DocumentPrerequisiteOrigin
import com.entio.core.DocumentIndividualReviewGate
import com.entio.core.DocumentIndividualClassification
import com.entio.core.DocumentPlanOperand
import com.entio.core.DocumentPlanOperation
import com.entio.core.DocumentPlanOperationKind
import com.entio.core.DocumentReviewOnlyFinding
import com.entio.core.DocumentSemanticItemKind
import com.entio.core.DocumentSemanticOutcome
import com.entio.core.DocumentSemanticPlan
import com.entio.core.DocumentSemanticPlanItem
import com.entio.core.DocumentSemanticRecommendationGroup
import com.entio.core.DocumentSemanticReference
import com.entio.core.DocumentSemanticReferenceRole
import com.entio.core.DocumentSemanticReferenceTarget
import com.entio.core.DocumentTemporaryReference
import com.entio.core.DocumentReconciliationKind
import com.entio.core.MAX_DOCUMENT_DISCOVERIES_PER_DOCUMENT
import com.entio.core.MAX_DOCUMENT_CONNECTED_MODEL_ITEMS_PER_PROVIDER_RESPONSE
import com.entio.core.MAX_DOCUMENT_PROVIDER_RESPONSE_CHARACTERS
import com.entio.core.DocumentRecommendationCategory
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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
    }
}

/** Narrow ingestion-only OpenAI Responses adapter with no tools, URLs, or conversation state. */
internal class OpenAiDocumentAnalysisClient(
    private val configuration: OpenAiDocumentAnalysisConfiguration = OpenAiDocumentAnalysisConfiguration(),
    private val objectMapper: ObjectMapper = ObjectMapper().findAndRegisterModules(),
    engine: HttpClientEngine? = null,
) : DocumentPipelineProvider,
    DocumentGroundedAnalysisProvider,
    AutoCloseable {
    private val client = if (engine == null) {
        HttpClient(CIO) {
            followRedirects = false
            install(HttpTimeout) {
                connectTimeoutMillis = configuration.connectTimeoutMillis
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            }
        }
    } else {
        HttpClient(engine) {
            followRedirects = false
            install(HttpTimeout) {
                connectTimeoutMillis = configuration.connectTimeoutMillis
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
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
                val failure = classifyHttpFailure(response)
                return DocumentAnalysisProviderResult.Failed(
                    retryable = failure.retryable,
                    safeCode = failure.safeCode,
                )
            }
            val responseText = response.bodyAsText()
            if (responseText.length > MAX_PROVIDER_RESPONSE_CHARACTERS) {
                return DocumentAnalysisProviderResult.Failed(false, "document-provider-response-limit")
            }
            val structured = parseStrictResponse(extractOutputText(responseText))
            DocumentAnalysisProviderResult.Completed(structured)
        } catch (failure: SafeProviderResponseFailure) {
            DocumentAnalysisProviderResult.Failed(failure.retryable, failure.code)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: HttpRequestTimeoutException) {
            DocumentAnalysisProviderResult.Failed(true, "document-provider-timeout")
        } catch (_: IOException) {
            DocumentAnalysisProviderResult.Failed(true, "document-provider-unavailable")
        } catch (_: Exception) {
            DocumentAnalysisProviderResult.Failed(true, "document-provider-malformed-output")
        }
    }

    override suspend fun analyzeGrounded(
        apiKey: String,
        selectedModelId: String,
        systemInstruction: String,
        request: DocumentGroundedAnalysisRequest,
    ): DocumentGroundedAnalysisProviderResult {
        if (apiKey.isBlank() || selectedModelId.isBlank()) {
            return DocumentGroundedAnalysisProviderResult.Failed(false, "document-provider-authorization")
        }
        return try {
            val response = client.post(configuration.endpoint) {
                header(HttpHeaders.Authorization, "Bearer ${apiKey.trim()}")
                accept(ContentType.Application.Json)
                setBody(TextContent(groundedRequestBody(selectedModelId, systemInstruction, request), ContentType.Application.Json))
            }
            if (!response.status.isSuccess()) {
                val failure = classifyHttpFailure(response)
                return DocumentGroundedAnalysisProviderResult.Failed(failure.retryable, failure.safeCode)
            }
            val responseText = response.bodyAsText()
            if (responseText.length > MAX_DOCUMENT_PROVIDER_RESPONSE_CHARACTERS) {
                return DocumentGroundedAnalysisProviderResult.Failed(false, "document-provider-response-limit")
            }
            DocumentGroundedAnalysisProviderResult.Completed(
                parseStrictGroundedResponse(extractOutputText(responseText)),
            )
        } catch (failure: SafeProviderResponseFailure) {
            DocumentGroundedAnalysisProviderResult.Failed(failure.retryable, failure.code)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: HttpRequestTimeoutException) {
            DocumentGroundedAnalysisProviderResult.Failed(true, "document-provider-timeout")
        } catch (failure: JsonProcessingException) {
            val path = (failure as? com.fasterxml.jackson.databind.JsonMappingException)?.path
                ?.joinToString(".") { reference -> reference.fieldName ?: "[${reference.index}]" }
                .orEmpty()
            diagnostic("grounded-parse-failure type=${failure::class.simpleName} path=$path")
            DocumentGroundedAnalysisProviderResult.Failed(true, "document-provider-malformed-output")
        } catch (failure: IllegalArgumentException) {
            diagnostic("grounded-parse-failure type=${failure::class.simpleName}")
            DocumentGroundedAnalysisProviderResult.Failed(true, "document-provider-malformed-output")
        } catch (_: IOException) {
            DocumentGroundedAnalysisProviderResult.Failed(true, "document-provider-unavailable")
        } catch (_: Exception) {
            DocumentGroundedAnalysisProviderResult.Failed(true, "document-provider-malformed-output")
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
                val failure = classifyHttpFailure(response)
                return DocumentDiscoveryProviderResult.Failed(
                    retryable = failure.retryable,
                    safeCode = failure.safeCode,
                )
            }
            val responseText = response.bodyAsText()
            if (responseText.length > MAX_DOCUMENT_PROVIDER_RESPONSE_CHARACTERS) {
                return DocumentDiscoveryProviderResult.Failed(false, "document-provider-response-limit")
            }
            val structured = parseStrictDiscoveryResponse(extractOutputText(responseText), request)
            DocumentDiscoveryProviderResult.Completed(structured)
        } catch (failure: SafeProviderResponseFailure) {
            DocumentDiscoveryProviderResult.Failed(failure.retryable, failure.code)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: HttpRequestTimeoutException) {
            DocumentDiscoveryProviderResult.Failed(true, "document-provider-timeout")
        } catch (_: IOException) {
            DocumentDiscoveryProviderResult.Failed(true, "document-provider-unavailable")
        } catch (_: Exception) {
            DocumentDiscoveryProviderResult.Failed(true, "document-provider-malformed-output")
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

    override suspend fun completePrerequisites(
        apiKey: String,
        selectedModelId: String,
        systemInstruction: String,
        request: DocumentPrerequisiteCompletionRequest,
    ): DocumentConnectedModelProviderResult =
        connectedModelCall(
            apiKey = apiKey,
            selectedModelId = selectedModelId,
            systemInstruction = systemInstruction,
            request = request,
            responseSchemaVersion = DocumentAnalysisPipelineVersions.PREREQUISITE_COMPLETION_RESPONSE,
            formatName = "phase_11_5_document_prerequisite_completion",
        ) { response ->
            DocumentConnectedModelProviderResult.CompletedPrerequisites(
                DocumentPrerequisiteCompletionResponse(items = response),
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
                val failure = classifyHttpFailure(response)
                return DocumentReconciliationProviderResult.Failed(
                    retryable = failure.retryable,
                    safeCode = failure.safeCode,
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
            DocumentReconciliationProviderResult.Failed(failure.retryable, failure.code)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: HttpRequestTimeoutException) {
            DocumentReconciliationProviderResult.Failed(true, "document-provider-timeout")
        } catch (_: IOException) {
            DocumentReconciliationProviderResult.Failed(true, "document-provider-unavailable")
        } catch (_: Exception) {
            DocumentReconciliationProviderResult.Failed(true, "document-provider-malformed-output")
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
                val failure = classifyHttpFailure(response)
                return DocumentOntologyAlignmentProviderResult.Failed(
                    retryable = failure.retryable,
                    safeCode = failure.safeCode,
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
            DocumentOntologyAlignmentProviderResult.Failed(failure.retryable, failure.code)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: HttpRequestTimeoutException) {
            DocumentOntologyAlignmentProviderResult.Failed(true, "document-provider-timeout")
        } catch (_: IOException) {
            DocumentOntologyAlignmentProviderResult.Failed(true, "document-provider-unavailable")
        } catch (_: Exception) {
            DocumentOntologyAlignmentProviderResult.Failed(true, "document-provider-malformed-output")
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
                val failure = classifyHttpFailure(response)
                return DocumentModelingCriticProviderResult.Failed(
                    retryable = failure.retryable,
                    safeCode = failure.safeCode,
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
            DocumentModelingCriticProviderResult.Failed(failure.retryable, failure.code)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: HttpRequestTimeoutException) {
            DocumentModelingCriticProviderResult.Failed(true, "document-provider-timeout")
        } catch (_: IOException) {
            DocumentModelingCriticProviderResult.Failed(true, "document-provider-unavailable")
        } catch (_: Exception) {
            DocumentModelingCriticProviderResult.Failed(true, "document-provider-malformed-output")
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
                val failure = classifyHttpFailure(response)
                return DocumentFinalPlanningProviderResult.Failed(
                    retryable = failure.retryable,
                    safeCode = failure.safeCode,
                )
            }
            val responseText = response.bodyAsText()
            if (responseText.length > MAX_DOCUMENT_PROVIDER_RESPONSE_CHARACTERS) {
                return DocumentFinalPlanningProviderResult.Failed(false, "document-provider-response-limit")
            }
            DocumentFinalPlanningProviderResult.Completed(
                parseStrictFinalPlanningResponse(extractOutputText(responseText), request),
            )
        } catch (failure: SafeProviderResponseFailure) {
            DocumentFinalPlanningProviderResult.Failed(failure.retryable, failure.code)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: HttpRequestTimeoutException) {
            DocumentFinalPlanningProviderResult.Failed(true, "document-provider-timeout")
        } catch (_: IOException) {
            DocumentFinalPlanningProviderResult.Failed(true, "document-provider-unavailable")
        } catch (failure: IllegalArgumentException) {
            DocumentFinalPlanningProviderResult.Failed(true, classifyFinalPlanParseFailure(failure))
        } catch (_: Exception) {
            DocumentFinalPlanningProviderResult.Failed(true, "document-provider-malformed-output")
        }
    }

    override suspend fun planSemantic(
        apiKey: String,
        selectedModelId: String,
        systemInstruction: String,
        request: DocumentFinalPlanningRequest,
    ): DocumentSemanticPlanningProviderResult {
        if (apiKey.isBlank() || selectedModelId.isBlank()) {
            return DocumentSemanticPlanningProviderResult.Failed(false, "document-provider-authorization")
        }
        return try {
            val response = client.post(configuration.endpoint) {
                header(HttpHeaders.Authorization, "Bearer ${apiKey.trim()}")
                accept(ContentType.Application.Json)
                setBody(
                    TextContent(
                        semanticPlanningRequestBody(selectedModelId, systemInstruction, request),
                        ContentType.Application.Json,
                    ),
                )
            }
            if (!response.status.isSuccess()) {
                val failure = classifyHttpFailure(response)
                return DocumentSemanticPlanningProviderResult.Failed(failure.retryable, failure.safeCode)
            }
            val responseText = response.bodyAsText()
            if (responseText.length > MAX_DOCUMENT_PROVIDER_RESPONSE_CHARACTERS) {
                return DocumentSemanticPlanningProviderResult.Failed(false, "document-provider-response-limit")
            }
            DocumentSemanticPlanningProviderResult.Completed(
                parseStrictSemanticPlanningResponse(extractOutputText(responseText), request),
            )
        } catch (failure: SafeProviderResponseFailure) {
            DocumentSemanticPlanningProviderResult.Failed(failure.retryable, failure.code)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: HttpRequestTimeoutException) {
            DocumentSemanticPlanningProviderResult.Failed(true, "document-provider-timeout")
        } catch (_: IOException) {
            DocumentSemanticPlanningProviderResult.Failed(true, "document-provider-unavailable")
        } catch (failure: IllegalArgumentException) {
            val safeCode = classifySemanticPlanParseFailure(failure)
            DocumentSemanticPlanningProviderResult.Failed(
                retryable = safeCode in RETRYABLE_SEMANTIC_PLAN_SAFE_CODES,
                safeCode = safeCode,
            )
        } catch (_: Exception) {
            DocumentSemanticPlanningProviderResult.Failed(true, "document-provider-malformed-output")
        }
    }

    override fun close(): Unit = client.close()

    private suspend fun classifyHttpFailure(response: HttpResponse): SafeHttpFailure {
        val status = response.status.value
        val responseBody = runCatching {
            response.bodyAsText().takeIf { it.length <= MAX_PROVIDER_ERROR_CHARACTERS }
        }.getOrNull()
        diagnostic("http-failure status=$status body=${responseBody.orEmpty()}")
        val providerError = if (status in 400..499) {
            runCatching {
                responseBody
                    ?.let(objectMapper::readTree)
                    ?.path("error")
            }.getOrNull()
        } else {
            null
        }
        val providerCode = providerError?.path("code")?.takeIf(JsonNode::isTextual)?.asText()
        val providerType = providerError?.path("type")?.takeIf(JsonNode::isTextual)?.asText()
        val providerParameter = providerError?.path("param")?.takeIf(JsonNode::isTextual)?.asText()
        val providerMessage = providerError?.path("message")?.takeIf(JsonNode::isTextual)?.asText().orEmpty()
        val safeCode = when {
            status == 401 || status == 403 -> "document-provider-authorization"
            status == 429 && (providerCode == "insufficient_quota" || providerType == "insufficient_quota") ->
                "document-provider-quota-exhausted"
            status == 429 && providerMessage.contains("request too large", ignoreCase = true) ->
                "document-provider-request-rate-limit"
            status == 429 -> "document-provider-rate-limited"
            status >= 500 -> "document-provider-unavailable"
            providerCode == "invalid_json_schema" ||
                providerParameter in OPENAI_SCHEMA_PARAMETERS ->
                "document-provider-request-schema-invalid"
            providerCode == "model_not_found" -> "document-provider-model-not-found"
            else -> "document-provider-request-rejected"
        }
        return SafeHttpFailure(
            retryable = safeCode == "document-provider-rate-limited" || status >= 500,
            safeCode = safeCode,
        )
    }

    private fun diagnostic(message: String): Unit {
        if (System.getenv("ENTIO_DOCUMENT_ANALYSIS_DEBUG") == "true") {
            System.err.println("entio-document-analysis $message")
        }
    }

    private fun classifyFinalPlanParseFailure(failure: IllegalArgumentException): String {
        val message = failure.message.orEmpty()
        return when {
            message.contains("temporary", ignoreCase = true) ->
                "document-final-plan-temporary-reference-invalid"
            message.contains("dependenc", ignoreCase = true) ->
                "document-final-plan-dependency-invalid"
            message.contains("coverage", ignoreCase = true) ||
                message.contains("verified discovery", ignoreCase = true) ->
                "document-final-plan-coverage-invalid"
            message.contains("critic", ignoreCase = true) ->
                "document-final-plan-critic-disposition-invalid"
            message.contains("individual", ignoreCase = true) ->
                "document-final-plan-individual-gate-invalid"
            message.contains("expanded", ignoreCase = true) ->
                "document-final-plan-edit-limit-invalid"
            message.contains("executable", ignoreCase = true) ||
                message.contains("review-only", ignoreCase = true) ||
                message.contains("mixed recommendation", ignoreCase = true) ||
                message.contains("blocked recommendation", ignoreCase = true) ->
                "document-final-plan-status-invalid"
            message.contains("recommendation", ignoreCase = true) ->
                "document-final-plan-recommendation-invalid"
            else -> "document-final-plan-schema-invalid"
        }
    }

    private fun classifySemanticPlanParseFailure(failure: IllegalArgumentException): String {
        val message = failure.message.orEmpty()
        return when {
            message.contains("coverage", ignoreCase = true) ||
                message.contains("verified discovery", ignoreCase = true) ->
                "document-semantic-plan-coverage-invalid"
            message.contains("reference", ignoreCase = true) ->
                "document-semantic-plan-reference-invalid"
            message.contains("critic", ignoreCase = true) ->
                "document-semantic-plan-critic-invalid"
            message.contains("group", ignoreCase = true) ->
                "document-semantic-plan-group-invalid"
            message.contains("unknown", ignoreCase = true) ||
                message.contains("field", ignoreCase = true) ||
                message.contains("schema", ignoreCase = true) ->
                "document-semantic-plan-schema-invalid"
            message.contains("item", ignoreCase = true) ||
                message.contains("semantic", ignoreCase = true) ->
                "document-semantic-plan-item-invalid"
            else -> "document-semantic-plan-invalid"
        }
    }

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
            val requestBody = connectedModelRequestBody(
                selectedModelId,
                systemInstruction,
                request,
                responseSchemaVersion,
                formatName,
            )
            if (request is DocumentConnectedModelRequest) {
                diagnostic(
                    "connected-model request chunk=${request.chunkIndex} " +
                        "discoveries=${request.discoveries.size} characters=${requestBody.length}",
                )
            } else if (request is DocumentPrerequisiteCompletionRequest) {
                diagnostic(
                    "prerequisite-completion request missing=${request.missingPrerequisites.size} " +
                        "discoveries=${request.discoveries.size} characters=${requestBody.length}",
                )
            }
            val response = client.post(configuration.endpoint) {
                header(HttpHeaders.Authorization, "Bearer ${apiKey.trim()}")
                accept(ContentType.Application.Json)
                setBody(
                    TextContent(
                        requestBody,
                        ContentType.Application.Json,
                    ),
                )
            }
            if (!response.status.isSuccess()) {
                val failure = classifyHttpFailure(response)
                return DocumentConnectedModelProviderResult.Failed(
                    retryable = failure.retryable,
                    safeCode = failure.safeCode,
                )
            }
            val responseText = response.bodyAsText()
            val outputText = extractOutputText(responseText)
            when (request) {
                is DocumentConnectedModelRequest -> diagnostic(
                    "connected-model response chunk=${request.chunkIndex} characters=${responseText.length} " +
                        "outputCharacters=${outputText.length}",
                )
                is DocumentModelConsolidationRequest -> diagnostic(
                    "model-consolidation response characters=${responseText.length} " +
                        "outputCharacters=${outputText.length}",
                )
                is DocumentPrerequisiteCompletionRequest -> diagnostic(
                    "prerequisite-completion response characters=${responseText.length} " +
                        "outputCharacters=${outputText.length}",
                )
            }
            if (responseText.length > MAX_DOCUMENT_PROVIDER_RESPONSE_CHARACTERS) {
                return DocumentConnectedModelProviderResult.Failed(false, "document-provider-response-limit")
            }
            completed(parseStrictConnectedModelResponse(outputText, responseSchemaVersion))
        } catch (failure: SafeProviderResponseFailure) {
            diagnostic("connected-model response-failure code=${failure.code}")
            DocumentConnectedModelProviderResult.Failed(failure.retryable, failure.code)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: HttpRequestTimeoutException) {
            DocumentConnectedModelProviderResult.Failed(true, "document-provider-timeout")
        } catch (failure: IOException) {
            diagnostic("connected-model io-failure=${failure::class.simpleName} message=${failure.message.orEmpty()}")
            DocumentConnectedModelProviderResult.Failed(true, "document-provider-unavailable")
        } catch (failure: Exception) {
            diagnostic(
                "connected-model unexpected-failure=${failure::class.simpleName} " +
                    "message=${failure.message.orEmpty()}",
            )
            DocumentConnectedModelProviderResult.Failed(true, "document-provider-malformed-output")
        }
    }

    private fun requestBody(modelId: String, instruction: String, request: DocumentAnalysisRequest): String {
        val root = objectMapper.createObjectNode()
        root.put("model", modelId)
        root.put("store", false)
        root.put("max_output_tokens", MAX_DOCUMENT_PROVIDER_OUTPUT_TOKENS)
        root.putArray("tools")
        root.put("instructions", instruction)
        root.put("input", objectMapper.writeValueAsString(request))
        root.set<JsonNode>("text", strictTextFormat())
        return objectMapper.writeValueAsString(root)
    }

    private fun groundedRequestBody(
        modelId: String,
        instruction: String,
        request: DocumentGroundedAnalysisRequest,
    ): String {
        val root = objectMapper.createObjectNode()
        root.put("model", modelId)
        root.put("store", false)
        root.put("max_output_tokens", MAX_DOCUMENT_PROVIDER_OUTPUT_TOKENS)
        root.putArray("tools")
        root.put("instructions", instruction)
        root.put("input", objectMapper.writeValueAsString(request))
        root.set<JsonNode>("text", strictGroundedTextFormat(request))
        return objectMapper.writeValueAsString(root)
    }

    private fun strictGroundedTextFormat(request: DocumentGroundedAnalysisRequest): JsonNode {
        fun objectSchema(required: List<String>, properties: com.fasterxml.jackson.databind.node.ObjectNode): JsonNode =
            objectMapper.createObjectNode().apply {
                put("type", "object")
                put("additionalProperties", false)
                set<JsonNode>("required", objectMapper.valueToTree(required.sorted()))
                set<JsonNode>("properties", properties)
            }
        fun nullableString(max: Int): JsonNode = objectMapper.createObjectNode().apply {
            set<JsonNode>("type", objectMapper.valueToTree(listOf("string", "null")))
            put("maxLength", max)
        }
        fun stringArray(values: List<String>, max: Int): JsonNode = objectMapper.createObjectNode().apply {
            put("type", "array")
            put("minItems", 1)
            put("maxItems", max)
            set<JsonNode>("items", stringEnum(values, "Exact server-issued ID."))
        }
        val candidateIds = request.candidates.map { it.id }
        val evidenceIds = request.candidates.flatMap { it.evidenceSpans }.map { it.evidenceId.value }.distinct().sorted()
        val selectionIds = request.retrieval.flatMap { it.selections }.map { it.selectionId }.distinct().sorted()
        val reference = objectSchema(
            listOf("prerequisiteOrigin", "role", "targetItemId"),
            objectMapper.createObjectNode().apply {
                set<JsonNode>("role", stringEnum(com.entio.core.DocumentSemanticReferenceRole.entries.map { it.name }, "Reference role."))
                putObject("targetItemId").put("type", "string").put("minLength", 1).put("maxLength", 200)
                set<JsonNode>("prerequisiteOrigin", stringEnum(DocumentPrerequisiteOrigin.entries.map { it.name }, "Prerequisite origin."))
            },
        )
        val confidence = objectSchema(
            listOf("evidence", "modeling", "ontologyFit"),
            objectMapper.createObjectNode().apply {
                listOf("evidence", "modeling", "ontologyFit").forEach {
                    putObject(it).put("type", "integer").put("minimum", 0).put("maximum", 100)
                }
            },
        )
        val iri = objectSchema(
            listOf("value"),
            objectMapper.createObjectNode().apply {
                putObject("value").put("type", "string").put("minLength", 1).put("maxLength", 2_000)
            },
        )
        val literal = objectSchema(
            listOf("datatypeIri", "languageTag", "lexicalForm"),
            objectMapper.createObjectNode().apply {
                putObject("lexicalForm").put("type", "string").put("minLength", 1).put("maxLength", 8_000)
                set<JsonNode>("datatypeIri", objectMapper.createObjectNode().apply {
                    set<JsonNode>("anyOf", objectMapper.valueToTree(listOf(iri, objectMapper.createObjectNode().put("type", "null"))))
                })
                set<JsonNode>("languageTag", nullableString(100))
            },
        )
        val item = objectSchema(
            listOf("ambiguity", "candidateIds", "confidence", "datatypeIntent", "definition", "disposition", "evidenceIds", "id", "kind", "label", "literalValue", "rationale", "references", "selectionId"),
            objectMapper.createObjectNode().apply {
                putObject("id").put("type", "string").put("minLength", 1).put("maxLength", 200)
                set<JsonNode>("kind", stringEnum(com.entio.core.DocumentSemanticItemKind.entries.map { it.name }, "Semantic kind."))
                putObject("label").put("type", "string").put("minLength", 1).put("maxLength", 500)
                set<JsonNode>("definition", nullableString(2_000))
                set<JsonNode>("literalValue", objectMapper.createObjectNode().apply {
                    set<JsonNode>("anyOf", objectMapper.valueToTree(listOf(literal, objectMapper.createObjectNode().put("type", "null"))))
                })
                set<JsonNode>("datatypeIntent", nullableString(500))
                set<JsonNode>("candidateIds", stringArray(candidateIds, candidateIds.size))
                set<JsonNode>("evidenceIds", stringArray(evidenceIds, evidenceIds.size))
                set<JsonNode>("disposition", stringEnum(DocumentGroundedDisposition.entries.map { it.name }, "Grounded disposition."))
                set<JsonNode>("selectionId", if (selectionIds.isEmpty()) objectMapper.createObjectNode().put("type", "null") else objectMapper.createObjectNode().apply {
                    set<JsonNode>("anyOf", objectMapper.valueToTree(listOf(stringEnum(selectionIds, "Exact selection ID."), objectMapper.createObjectNode().put("type", "null"))))
                })
                set<JsonNode>("references", objectMapper.createObjectNode().apply { put("type", "array"); put("maxItems", 20); set<JsonNode>("items", reference) })
                putObject("rationale").put("type", "string").put("minLength", 1).put("maxLength", 2_000)
                set<JsonNode>("confidence", confidence)
                set<JsonNode>("ambiguity", nullableString(2_000))
            },
        )
        val coverage = objectSchema(
            listOf("candidateId", "disposition", "itemId", "rationale"),
            objectMapper.createObjectNode().apply {
                set<JsonNode>("candidateId", stringEnum(candidateIds, "Exact candidate ID."))
                set<JsonNode>("itemId", nullableString(200))
                set<JsonNode>("disposition", stringEnum(DocumentGroundedDisposition.entries.map { it.name }, "Coverage disposition."))
                putObject("rationale").put("type", "string").put("minLength", 1).put("maxLength", 1_000)
            },
        )
        val schema = objectSchema(
            listOf("coverage", "items", "responseVersion"),
            objectMapper.createObjectNode().apply {
                putObject("responseVersion").put("type", "string").put("const", DocumentAnalysisPipelineVersions.GROUNDED_RESPONSE)
                set<JsonNode>("items", objectMapper.createObjectNode().apply { put("type", "array"); put("maxItems", 80); set<JsonNode>("items", item) })
                set<JsonNode>("coverage", objectMapper.createObjectNode().apply { put("type", "array"); put("minItems", candidateIds.size); put("maxItems", candidateIds.size); set<JsonNode>("items", coverage) })
            },
        )
        return objectMapper.createObjectNode().set<JsonNode>(
            "format",
            objectMapper.createObjectNode().apply {
                put("type", "json_schema")
                put("name", "phase_12_grounded_document_analysis")
                put("strict", true)
                set<JsonNode>("schema", schema)
            },
        )
    }

    private fun discoveryRequestBody(
        modelId: String,
        instruction: String,
        request: DocumentDiscoveryRequest,
    ): String {
        val root = objectMapper.createObjectNode()
        root.put("model", modelId)
        root.put("store", false)
        root.put("max_output_tokens", MAX_DOCUMENT_DISCOVERY_OUTPUT_TOKENS)
        root.putArray("tools")
        root.put("instructions", instruction)
        root.put("input", objectMapper.writeValueAsString(request.toPromptPayload()))
        root.set<JsonNode>("text", strictDiscoveryTextFormat(request))
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
        root.put(
            "max_output_tokens",
            when (request) {
                is DocumentModelConsolidationRequest -> MAX_DOCUMENT_MODEL_CONSOLIDATION_OUTPUT_TOKENS
                is DocumentPrerequisiteCompletionRequest -> MAX_DOCUMENT_PREREQUISITE_OUTPUT_TOKENS
                else -> MAX_DOCUMENT_CONNECTED_MODEL_OUTPUT_TOKENS
            },
        )
        root.putArray("tools")
        root.put("instructions", instruction)
        val promptPayload = when (request) {
            is DocumentConnectedModelRequest -> request.toPromptPayload()
            else -> request
        }
        root.put("input", objectMapper.writeValueAsString(promptPayload))
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
        root.put("max_output_tokens", MAX_DOCUMENT_PROVIDER_OUTPUT_TOKENS)
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
        root.put("max_output_tokens", alignmentOutputTokens(request))
        root.putArray("tools")
        root.put("instructions", instruction)
        root.put("input", objectMapper.writeValueAsString(request))
        root.set<JsonNode>("text", strictOntologyAlignmentTextFormat(request.connectedModel.items.size))
        return objectMapper.writeValueAsString(root)
    }

    private fun alignmentOutputTokens(request: DocumentOntologyAlignmentRequest): Int =
        (MIN_DOCUMENT_ALIGNMENT_OUTPUT_TOKENS +
            request.connectedModel.items.size * DOCUMENT_ALIGNMENT_TOKENS_PER_MODEL_ITEM)
            .coerceAtMost(MAX_DOCUMENT_ALIGNMENT_OUTPUT_TOKENS)

    private fun modelingCriticRequestBody(
        modelId: String,
        instruction: String,
        request: DocumentModelingCriticRequest,
    ): String {
        val root = objectMapper.createObjectNode()
        root.put("model", modelId)
        root.put("store", false)
        root.put("max_output_tokens", MAX_DOCUMENT_PROVIDER_OUTPUT_TOKENS)
        root.putArray("tools")
        root.put("instructions", instruction)
        root.put("input", objectMapper.writeValueAsString(request.toPromptPayload()))
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
        root.put("max_output_tokens", finalPlanOutputTokens(request))
        root.putArray("tools")
        root.put("instructions", instruction)
        root.put("input", objectMapper.writeValueAsString(request.toPromptPayload()))
        root.set<JsonNode>("text", strictFinalPlanningTextFormat(request))
        return objectMapper.writeValueAsString(root)
    }

    private fun semanticPlanningRequestBody(
        modelId: String,
        instruction: String,
        request: DocumentFinalPlanningRequest,
    ): String {
        val root = objectMapper.createObjectNode()
        root.put("model", modelId)
        root.put("store", false)
        root.put("max_output_tokens", finalPlanOutputTokens(request))
        root.putArray("tools")
        root.put("instructions", instruction)
        root.put("input", objectMapper.writeValueAsString(request.toPromptPayload()))
        root.set<JsonNode>("text", strictSemanticPlanningTextFormat(request))
        return objectMapper.writeValueAsString(root)
    }

    private fun finalPlanOutputTokens(request: DocumentFinalPlanningRequest): Int =
        (MIN_DOCUMENT_FINAL_PLAN_OUTPUT_TOKENS +
            maxOf(
                request.connectedModel.items.size,
                request.discoveries.count {
                    it.contentClassification == DocumentContentClassification.BusinessContent
                },
            ) * DOCUMENT_FINAL_PLAN_TOKENS_PER_MODEL_ITEM)
            .coerceAtMost(MAX_DOCUMENT_FINAL_PLAN_OUTPUT_TOKENS)

    private fun strictSemanticPlanningTextFormat(request: DocumentFinalPlanningRequest): JsonNode {
        fun objectSchema(fields: Set<String>, properties: JsonNode): JsonNode =
            objectMapper.createObjectNode().apply {
                put("type", "object")
                put("additionalProperties", false)
                set<JsonNode>("required", objectMapper.valueToTree(fields.sorted()))
                set<JsonNode>("properties", properties)
            }
        fun strings(min: Int = 0, max: Int = 500): JsonNode = boundedUniqueStringArray(min, max, 500)
        fun confidenceFields(node: com.fasterxml.jackson.databind.node.ObjectNode): Unit {
            listOf("evidenceConfidence", "modelingConfidence", "ontologyFitConfidence").forEach { field ->
                node.putObject(field).put("type", "integer").put("minimum", 0).put("maximum", 100)
            }
        }
        val criticDisposition = objectSchema(
            SEMANTIC_CRITIC_DISPOSITION_FIELDS,
            objectMapper.createObjectNode().apply {
                putObject("findingId").put("type", "string").put("minLength", 1).put("maxLength", 200)
                set<JsonNode>("kind", stringEnum(DocumentCriticDispositionKind.entries.map { it.name }, "Disposition."))
                set<JsonNode>("rationale", nullableString(2_000, "Required only when rejected."))
            },
        )
        val reference = objectSchema(
            SEMANTIC_REFERENCE_FIELDS,
            objectMapper.createObjectNode().apply {
                set<JsonNode>("role", stringEnum(DocumentSemanticReferenceRole.entries.map { it.name }, "Reference role."))
                set<JsonNode>("targetKind", stringEnum(listOf("SemanticItem", "Alignment"), "Reference target kind."))
                putObject("targetId").put("type", "string").put("minLength", 1).put("maxLength", 200)
            },
        )
        val literal = objectSchema(
            SEMANTIC_LITERAL_FIELDS,
            objectMapper.createObjectNode().apply {
                putObject("lexicalForm").put("type", "string").put("minLength", 1).put("maxLength", 2_000)
                set<JsonNode>("datatypeIri", nullableString(2_000, "Optional datatype IRI."))
                set<JsonNode>("language", nullableString(100, "Optional language tag."))
            },
        )
        val item = objectSchema(
            SEMANTIC_ITEM_FIELDS,
            objectMapper.createObjectNode().apply {
                set<JsonNode>(
                    "id",
                    stringEnum(
                        request.connectedModel.items.map(DocumentConnectedModelItem::id).distinct().sorted(),
                        "Exact retained connected-model item ID.",
                    ),
                )
                set<JsonNode>("kind", stringEnum(DocumentSemanticItemKind.entries.map { it.name }, "Semantic item kind."))
                putObject("label").put("type", "string").put("minLength", 1).put("maxLength", 500)
                set<JsonNode>("definition", nullableString(2_000, "Optional definition."))
                set<JsonNode>("literalValue", objectMapper.createObjectNode().apply {
                    set<JsonNode>("anyOf", objectMapper.createArrayNode().apply {
                        add(objectMapper.createObjectNode().put("type", "null"))
                        add(literal)
                    })
                })
                set<JsonNode>("datatypeIntent", nullableString(500, "Optional datatype or supported constraint intent."))
                set<JsonNode>("references", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("maxItems", 20)
                    set<JsonNode>("items", reference)
                })
                set<JsonNode>("discoveryIds", strings(1))
                set<JsonNode>("evidenceIds", strings(1, 8))
                putObject("rationale").put("type", "string").put("minLength", 1).put("maxLength", 2_000)
                set<JsonNode>("outcome", stringEnum(DocumentSemanticOutcome.entries.map { it.name }, "Semantic outcome."))
                set<JsonNode>("ambiguity", nullableString(2_000, "Unresolved ambiguity for non-executable meaning."))
                set<JsonNode>("criticDispositions", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("maxItems", 600)
                    set<JsonNode>("items", criticDisposition)
                })
                confidenceFields(this)
            },
        )
        val group = objectSchema(
            SEMANTIC_GROUP_FIELDS,
            objectMapper.createObjectNode().apply {
                putObject("id").put("type", "string").put("minLength", 1).put("maxLength", 200)
                putObject("title").put("type", "string").put("minLength", 1).put("maxLength", 500)
                putObject("description").put("type", "string").put("minLength", 1).put("maxLength", 2_000)
                set<JsonNode>("itemIds", strings(1))
                set<JsonNode>("discoveryIds", strings(1))
                set<JsonNode>("evidenceIds", strings(1, 8))
                set<JsonNode>("outcome", stringEnum(DocumentSemanticOutcome.entries.map { it.name }, "Group outcome."))
                putObject("rationale").put("type", "string").put("minLength", 1).put("maxLength", 2_000)
                set<JsonNode>("criticDispositions", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("maxItems", 600)
                    set<JsonNode>("items", criticDisposition)
                })
                confidenceFields(this)
            },
        )
        val coverage = objectSchema(
            SEMANTIC_COVERAGE_FIELDS,
            objectMapper.createObjectNode().apply {
                putObject("discoveryId").put("type", "string").put("minLength", 1).put("maxLength", 200)
                set<JsonNode>("kind", stringEnum(DocumentCoverageDispositionKind.entries.map { it.name }, "Coverage kind."))
                set<JsonNode>("recommendationId", nullableString(200, "Related semantic group ID."))
                set<JsonNode>("relatedDiscoveryId", nullableString(200, "Merged discovery ID."))
                set<JsonNode>("alignmentId", nullableString(200, "Exact supplied alignment ID."))
                set<JsonNode>("rationale", nullableString(2_000, "Required rejection or blocked rationale."))
            },
        )
        val plan = objectSchema(
            SEMANTIC_PLAN_FIELDS,
            objectMapper.createObjectNode().apply {
                putObject("workKey").put("type", "string").put("pattern", "^[a-f0-9]{64}$")
                set<JsonNode>("verifiedDiscoveryIds", strings(1))
                set<JsonNode>("criticFindingIds", strings())
                set<JsonNode>("items", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("minItems", request.connectedModel.items.size)
                    put("maxItems", request.connectedModel.items.size)
                    set<JsonNode>("items", item)
                })
                set<JsonNode>("groups", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("minItems", if (request.connectedModel.items.isEmpty()) 0 else 1)
                    put("maxItems", 100)
                    set<JsonNode>("items", group)
                })
            },
        )
        val root = objectSchema(
            setOf("schemaVersion", "plan", "coverage"),
            objectMapper.createObjectNode().apply {
                putObject("schemaVersion").put("type", "string")
                    .put("const", DocumentAnalysisPipelineVersions.SEMANTIC_PLAN_RESPONSE)
                set<JsonNode>("plan", plan)
                set<JsonNode>("coverage", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("minItems", request.discoveries.size)
                    put("maxItems", request.discoveries.size)
                    set<JsonNode>("items", coverage)
                })
            },
        )
        return objectMapper.createObjectNode().set<JsonNode>(
            "format",
            objectMapper.createObjectNode().apply {
                put("type", "json_schema")
                put("name", "phase_11_5_plus_semantic_plan")
                put("strict", true)
                set<JsonNode>("schema", root)
            },
        )
    }

    private fun strictFinalPlanningTextFormat(request: DocumentFinalPlanningRequest): JsonNode {
        fun objectSchema(required: List<String>, properties: JsonNode): JsonNode =
            objectMapper.createObjectNode().apply {
                put("type", "object")
                put("additionalProperties", false)
                set<JsonNode>("required", objectMapper.valueToTree(required.sorted()))
                set<JsonNode>("properties", properties)
            }
        fun stringArray(maxItems: Int): JsonNode = boundedUniqueStringArray(0, maxItems, 500)
        fun requiredStringArray(maxItems: Int): JsonNode = boundedUniqueStringArray(1, maxItems, 500)
        fun nullSchema(description: String): JsonNode =
            objectMapper.createObjectNode().put("type", "null").put("description", description)
        fun operand(
            kind: String,
            valueSchema: JsonNode,
            datatypeSchema: JsonNode = nullSchema("Not used by this operand kind."),
            languageSchema: JsonNode = nullSchema("Not used by this operand kind."),
        ): JsonNode = objectSchema(
            FINAL_OPERAND_FIELDS.toList(),
            objectMapper.createObjectNode().apply {
                set<JsonNode>("kind", stringEnum(listOf(kind), "The exact typed operand kind."))
                set<JsonNode>("value", valueSchema)
                set<JsonNode>("datatypeIri", datatypeSchema)
                set<JsonNode>("language", languageSchema)
            },
        )
        fun boundedText(description: String): JsonNode =
            objectMapper.createObjectNode()
                .put("type", "string")
                .put("minLength", 1)
                .put("maxLength", 2_000)
                .put("description", description)
        val allowedExistingEntityIris = (
            request.ontologySnapshot.entries.map(DocumentOntologyAlignmentContextEntry::entityIri) +
                listOf(
                    "http://www.w3.org/2001/XMLSchema#boolean",
                    "http://www.w3.org/2001/XMLSchema#date",
                    "http://www.w3.org/2001/XMLSchema#dateTime",
                    "http://www.w3.org/2001/XMLSchema#decimal",
                    "http://www.w3.org/2001/XMLSchema#integer",
                    "http://www.w3.org/2001/XMLSchema#string",
                )
            ).distinct().sorted()
        val operand = objectMapper.createObjectNode().apply {
            set<JsonNode>("anyOf", objectMapper.createArrayNode().apply {
                add(operand(
                    "ExistingEntity",
                    stringEnum(
                        allowedExistingEntityIris,
                        "An exact entity IRI copied from the supplied ontology snapshot or an approved XSD datatype IRI.",
                    ),
                ))
                add(operand(
                    "TemporaryEntity",
                    objectMapper.createObjectNode()
                        .put("type", "string")
                        .put(
                            "pattern",
                            "^new:(class|objectProperty|datatypeProperty|annotationProperty|individual|shape):" +
                                "[A-Za-z][A-Za-z0-9_]*$",
                        )
                        .put("description", "An exact declaration created earlier in this recommendation."),
                ))
                add(operand(
                    "LiteralValue",
                    boundedText("The literal lexical form copied from verified evidence."),
                    nullableString(2_000, "The literal datatype IRI when present."),
                    nullableString(100, "The literal language tag when present."),
                ))
                add(operand("TextValue", boundedText("A supported label, definition, annotation, or SHACL constraint kind.")))
                add(operand(
                    "IntegerValue",
                    objectMapper.createObjectNode()
                        .put("type", "string")
                        .put("pattern", "^-?[0-9]+$")
                        .put("description", "An integer encoded as text for the typed operand wire contract."),
                ))
                add(operand(
                    "DecimalValue",
                    objectMapper.createObjectNode()
                        .put("type", "string")
                        .put("pattern", "^-?[0-9]+(?:\\.[0-9]+)?$")
                        .put("description", "A decimal encoded as text for the typed operand wire contract."),
                ))
                add(operand(
                    "SourceId",
                    stringEnum(
                        request.ontologySnapshot.writableSourceIds,
                        "An exact writable ontology source ID copied from the supplied snapshot.",
                    ),
                ))
            })
        }
        val operation = objectSchema(
            FINAL_OPERATION_FIELDS.toList(),
            objectMapper.createObjectNode().apply {
                putObject("id").put("type", "string").put("minLength", 1).put("maxLength", 200)
                set<JsonNode>(
                    "kind",
                    stringEnum(
                        DocumentPlanOperationKind.entries.map { it.name },
                        "Typed ontology operation. A created object or datatype property requires separate " +
                            "SetPropertyDomain and SetPropertyRange operations in the same recommendation. Model generic " +
                            "roles as classes, never individuals. Model supported enforceable requirements with SHACL " +
                            "shape operations; otherwise retain exactly one review-only finding.",
                    ),
                )
                putObject("order").put("type", "integer").put("minimum", 0).put("maximum", 99)
                set<JsonNode>(
                    "declaration",
                    nullablePatternString(
                        500,
                        "^new:(class|objectProperty|datatypeProperty|annotationProperty|individual|shape):" +
                            "[A-Za-z][A-Za-z0-9_]*$",
                        "Temporary new:<kind>:<LocalName> reference with no spaces or punctuation.",
                    ),
                )
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
                set<JsonNode>("discoveryIds", requiredStringArray(100))
                set<JsonNode>("evidenceIds", requiredStringArray(8))
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
                set<JsonNode>("discoveryIds", requiredStringArray(100))
                set<JsonNode>("evidenceIds", requiredStringArray(8))
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
                    putObject(field)
                        .put("type", "integer")
                        .put("minimum", 0)
                        .put("maximum", 100)
                        .put("description", "Percentage on a 0-100 scale; use 80 for eighty percent, not 4.")
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
                set<JsonNode>("verifiedDiscoveryIds", requiredStringArray(500))
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
                    putObject(name)
                        .put("type", "integer")
                        .put("minimum", 0)
                        .put("maximum", 100)
                        .put("description", "Percentage on a 0-100 scale; use 80 for eighty percent, not 4.")
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

    private fun strictOntologyAlignmentTextFormat(expectedRecordCount: Int): JsonNode {
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
                    put("minItems", expectedRecordCount)
                    put("maxItems", expectedRecordCount)
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
        set<JsonNode>("items", objectMapper.createObjectNode().put("type", "string").put("maxLength", maxLength))
    }

    private fun strictConnectedModelTextFormat(
        responseSchemaVersion: String,
        formatName: String,
    ): JsonNode {
        fun reference(roles: List<DocumentConnectedModelReferenceRole>): JsonNode =
            objectMapper.createObjectNode().apply {
                put("type", "object")
                put("additionalProperties", false)
                set<JsonNode>("required", objectMapper.valueToTree(CONNECTED_MODEL_REFERENCE_FIELDS.sorted()))
                set<JsonNode>("properties", objectMapper.createObjectNode().apply {
                    set<JsonNode>(
                        "role",
                        stringEnum(
                            (roles.ifEmpty { DocumentConnectedModelReferenceRole.entries }).map { it.name },
                            "The exact semantic role of this dependency.",
                        ),
                    )
                    putObject("providerItemId")
                        .put("type", "string")
                        .put("pattern", "^[A-Za-z0-9][A-Za-z0-9._:-]{0,199}$")
                })
            }
        fun item(
            kinds: List<DocumentConnectedModelItemKind>,
            roles: List<DocumentConnectedModelReferenceRole>,
            minimumReferences: Int,
            maximumReferences: Int,
        ): JsonNode = objectMapper.createObjectNode().apply {
            put("type", "object")
            put("additionalProperties", false)
            set<JsonNode>("required", objectMapper.valueToTree(CONNECTED_MODEL_FIELDS.sorted()))
            set<JsonNode>("properties", objectMapper.createObjectNode().apply {
                putObject("providerId")
                    .put("type", "string")
                    .put("pattern", "^[A-Za-z0-9][A-Za-z0-9._:-]{0,199}$")
                set<JsonNode>(
                    "kind",
                    stringEnum(kinds.map { it.name }, "The connected-model item kind for this exact reference contract."),
                )
                putObject("label").put("type", "string").put("minLength", 1).put("maxLength", 500)
                putObject("rationale").put("type", "string").put("minLength", 1).put("maxLength", 2_000)
                set<JsonNode>("discoveryIds", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("minItems", 1)
                    put("maxItems", 2_000)
                    set<JsonNode>("items", objectMapper.createObjectNode().put("type", "string").put("maxLength", 200))
                })
                set<JsonNode>("references", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("minItems", minimumReferences)
                    put("maxItems", maximumReferences)
                    set<JsonNode>("items", reference(roles))
                })
                set<JsonNode>(
                    "literalLexicalForm",
                    nullableString(8_000, "A datatype assertion's lexical form; otherwise null."),
                )
                set<JsonNode>(
                    "literalDatatypeIri",
                    nullableString(
                        2_000,
                        "A datatype assertion's explicit datatype or a datatype range's reviewed XSD IRI; otherwise null.",
                    ),
                )
                set<JsonNode>(
                    "literalLanguageTag",
                    nullableString(100, "An explicitly supported literal language tag; otherwise null."),
                )
                putObject("order").put("type", "integer").put("minimum", 0)
                putObject("reviewOnlyEligible").put("type", "boolean")
                putObject("modelRecommended").put("type", "boolean")
            })
        }
        val item = item(
            DocumentConnectedModelItemKind.entries,
            DocumentConnectedModelReferenceRole.entries,
            0,
            20,
        )
        val schema = objectMapper.createObjectNode().apply {
            put("type", "object")
            put("additionalProperties", false)
            set<JsonNode>("required", objectMapper.valueToTree(listOf("schemaVersion", "items")))
            set<JsonNode>("properties", objectMapper.createObjectNode().apply {
                putObject("schemaVersion").put("type", "string").put("const", responseSchemaVersion)
                set<JsonNode>("items", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("minItems", 1)
                    put("maxItems", MAX_DOCUMENT_CONNECTED_MODEL_ITEMS_PER_PROVIDER_RESPONSE)
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

    private fun strictDiscoveryTextFormat(request: DocumentDiscoveryRequest): JsonNode {
        val allowedAnchorIds = request.evidenceAnchors
            .map(DocumentDiscoveryEvidenceAnchor::anchorId)
            .distinct()
            .sorted()
        val evidence = objectMapper.createObjectNode().apply {
            put("type", "array")
            put("minItems", 1)
            put("maxItems", 8)
            set<JsonNode>("items", objectMapper.createObjectNode().apply {
                put("type", "object")
                put("additionalProperties", false)
                set<JsonNode>("required", objectMapper.valueToTree(DISCOVERY_EVIDENCE_FIELDS.sorted()))
                set<JsonNode>("properties", objectMapper.createObjectNode().apply {
                    set<JsonNode>("anchorId", stringEnum(
                        allowedAnchorIds,
                        "A server-issued evidence anchor copied exactly from the request.",
                    ))
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
                put("name", "phase_11_5_document_discovery_v2")
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

    private fun nullablePatternString(
        maxLength: Int,
        pattern: String,
        description: String,
    ): JsonNode =
        objectMapper.createObjectNode().apply {
            putArray("type").add("string").add("null")
            put("maxLength", maxLength)
            put("pattern", pattern)
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

    private fun parseStrictGroundedResponse(value: String): DocumentGroundedAnalysisResult {
        val root = objectMapper.readTree(value)
        require(root.isObject && root.fieldNames().asSequence().toSet() == setOf("responseVersion", "items", "coverage"))
        val items = root.path("items").map { node ->
            val confidence = node.path("confidence")
            DocumentGroundedSemanticItem(
                id = node.path("id").asText(),
                kind = DocumentSemanticItemKind.valueOf(node.path("kind").asText()),
                label = node.path("label").asText(),
                definition = node.path("definition").takeUnless(JsonNode::isNull)?.asText(),
                literalValue = node.path("literalValue").takeUnless(JsonNode::isNull)?.let {
                    objectMapper.treeToValue(it, RdfLiteral::class.java)
                },
                datatypeIntent = node.path("datatypeIntent").takeUnless(JsonNode::isNull)?.asText(),
                candidateIds = node.path("candidateIds").map(JsonNode::asText).sorted(),
                evidenceIds = node.path("evidenceIds").map { DocumentEvidenceId(it.asText()) }
                    .sortedBy(DocumentEvidenceId::value),
                disposition = DocumentGroundedDisposition.valueOf(node.path("disposition").asText()),
                selectionId = node.path("selectionId").takeUnless(JsonNode::isNull)?.asText(),
                references = node.path("references").map {
                    objectMapper.treeToValue(it, DocumentGroundedReference::class.java)
                }.sortedBy(DocumentGroundedReference::stableOrderingKey),
                rationale = node.path("rationale").asText(),
                confidence = DocumentConfidenceDimensions(
                    confidence.path("evidence").asInt(),
                    confidence.path("modeling").asInt(),
                    confidence.path("ontologyFit").asInt(),
                ),
                ambiguity = node.path("ambiguity").takeUnless(JsonNode::isNull)?.asText(),
            )
        }.sortedBy(DocumentGroundedSemanticItem::stableOrderingKey)
        val coverage = root.path("coverage").map { node ->
            DocumentGroundedCoverageDisposition(
                candidateId = node.path("candidateId").asText(),
                itemId = node.path("itemId").takeUnless(JsonNode::isNull)?.asText(),
                disposition = DocumentGroundedDisposition.valueOf(node.path("disposition").asText()),
                rationale = node.path("rationale").asText(),
            )
        }.sortedBy(DocumentGroundedCoverageDisposition::stableOrderingKey)
        return DocumentGroundedAnalysisResult(root.path("responseVersion").asText(), items, coverage)
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

    private fun parseStrictDiscoveryResponse(
        value: String,
        request: DocumentDiscoveryRequest,
    ): DocumentDiscoveryResponse {
        val anchorsById = request.evidenceAnchors.associateBy(DocumentDiscoveryEvidenceAnchor::anchorId)
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
                        require(
                            claim.isObject &&
                                claim.fieldNames().asSequence().toSet() == DISCOVERY_EVIDENCE_FIELDS,
                        )
                        val anchor = anchorsById[claim.requiredText("anchorId")]
                            ?: throw IllegalArgumentException("Unknown discovery evidence anchor.")
                        ProviderEvidenceClaim(
                            documentId = anchor.documentId,
                            blockId = anchor.blockId,
                            startOffsetInBlock = anchor.startOffsetInBlock,
                            endOffsetInBlock = anchor.endOffsetInBlock,
                            excerpt = anchor.exactExcerpt,
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
        require(items.isArray && items.size() in 1..MAX_DOCUMENT_CONNECTED_MODEL_ITEMS_PER_PROVIDER_RESPONSE)
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
                modelRecommended = item.path("modelRecommended").takeIf(JsonNode::isBoolean)?.booleanValue()
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

    private fun parseStrictSemanticPlanningResponse(
        value: String,
        request: DocumentFinalPlanningRequest,
    ): DocumentSemanticPlanningResponse {
        val root = objectMapper.readTree(value)
        require(root.isObject && root.fieldNames().asSequence().toSet() == setOf("schemaVersion", "plan", "coverage")) {
            "Semantic plan response schema is invalid."
        }
        require(root.requiredText("schemaVersion") == DocumentAnalysisPipelineVersions.SEMANTIC_PLAN_RESPONSE) {
            "Semantic plan response schema version is invalid."
        }
        val planNode = root.path("plan")
        require(planNode.isObject && planNode.fieldNames().asSequence().toSet() == SEMANTIC_PLAN_FIELDS) {
            "Semantic plan schema fields are invalid."
        }
        require(planNode.requiredText("workKey") == request.workKey.sha256) {
            "Semantic plan work key is invalid."
        }
        val knownDiscoveryIds = request.discoveries.map(DocumentDiscovery::id).toSet()
        val knownEvidenceIds = request.discoveries.flatMap(DocumentDiscovery::evidence)
            .map { it.id.value }
            .toSet()
        val knownAlignmentIds = request.alignments.map { it.id }.toSet()
        val knownCriticIds = request.criticFindings.map { it.id }.toSet()
        val connectedItemsById = request.connectedModel.items.associateBy(DocumentConnectedModelItem::id)
        val evidenceIdsByDiscovery = request.discoveries.associate { discovery ->
            discovery.id to discovery.evidence.map { it.id.value }
        }

        fun criticDisposition(node: JsonNode): DocumentCriticDisposition {
            require(node.isObject &&
                node.fieldNames().asSequence().toSet() == SEMANTIC_CRITIC_DISPOSITION_FIELDS) {
                "Unknown semantic critic-disposition field."
            }
            return DocumentCriticDisposition(
                findingId = node.requiredText("findingId"),
                kind = DocumentCriticDispositionKind.valueOf(node.requiredText("kind")),
                rationale = node.optionalText("rationale"),
            )
        }
        fun confidence(node: JsonNode): DocumentConfidenceDimensions = DocumentConfidenceDimensions(
            evidence = node.requiredInteger("evidenceConfidence"),
            modeling = node.requiredInteger("modelingConfidence"),
            ontologyFit = node.requiredInteger("ontologyFitConfidence"),
        )
        fun references(node: JsonNode): List<DocumentSemanticReference> {
            val values = node.path("references")
            require(values.isArray && values.size() <= 20)
            return values.map { reference ->
                require(reference.isObject &&
                    reference.fieldNames().asSequence().toSet() == SEMANTIC_REFERENCE_FIELDS) {
                    "Unknown semantic reference field."
                }
                val targetId = reference.requiredText("targetId")
                val target = when (reference.requiredText("targetKind")) {
                    "SemanticItem" -> DocumentSemanticReferenceTarget.SemanticItem(targetId)
                    "Alignment" -> {
                        require(targetId in knownAlignmentIds) { "Unknown semantic alignment reference." }
                        DocumentSemanticReferenceTarget.Alignment(targetId)
                    }
                    else -> throw IllegalArgumentException("Unknown semantic reference target kind.")
                }
                DocumentSemanticReference(
                    DocumentSemanticReferenceRole.valueOf(reference.requiredText("role")),
                    target,
                )
            }.sortedBy(DocumentSemanticReference::stableOrderingKey)
        }
        fun canonicalDiscoveryIds(itemIds: List<String>): List<String> =
            itemIds.flatMap { connectedItemsById.getValue(it).discoveryIds }
                .distinct()
                .sorted()
        fun canonicalEvidenceIds(discoveryIds: List<String>): List<DocumentEvidenceId> =
            discoveryIds.flatMap { evidenceIdsByDiscovery[it].orEmpty() }
                .distinct()
                .sorted()
                .take(MAX_FINAL_EVIDENCE_IDS)
                .map(::DocumentEvidenceId)
        fun literal(node: JsonNode): RdfLiteral? {
            val literal = node.path("literalValue")
            if (literal.isNull) return null
            require(literal.isObject && literal.fieldNames().asSequence().toSet() == SEMANTIC_LITERAL_FIELDS)
            return RdfLiteral(
                lexicalForm = literal.requiredText("lexicalForm"),
                datatypeIri = literal.optionalText("datatypeIri")?.let(::Iri),
                languageTag = literal.optionalText("language"),
            )
        }

        val itemsNode = planNode.path("items")
        require(itemsNode.isArray)
        val suppliedItemIds = itemsNode.map { it.requiredText("id") }
        require(
            suppliedItemIds.size == connectedItemsById.size &&
                suppliedItemIds.toSet() == connectedItemsById.keys &&
                suppliedItemIds.distinct().size == suppliedItemIds.size,
        ) {
            "Semantic plan must contain every retained connected-model item exactly once."
        }
        val items = itemsNode.map { node ->
            require(node.isObject && node.fieldNames().asSequence().toSet() == SEMANTIC_ITEM_FIELDS) {
                "Unknown semantic item field."
            }
            val criticDispositions = node.path("criticDispositions")
            require(criticDispositions.isArray)
            val itemId = node.requiredText("id")
            val canonicalDiscoveryIds = canonicalDiscoveryIds(listOf(itemId))
            DocumentSemanticPlanItem(
                id = itemId,
                kind = DocumentSemanticItemKind.valueOf(node.requiredText("kind")),
                label = node.requiredText("label"),
                definition = node.optionalText("definition"),
                literalValue = literal(node),
                datatypeIntent = node.optionalText("datatypeIntent"),
                references = references(node),
                discoveryIds = canonicalDiscoveryIds,
                evidenceIds = canonicalEvidenceIds(canonicalDiscoveryIds),
                rationale = node.requiredText("rationale"),
                outcome = DocumentSemanticOutcome.valueOf(node.requiredText("outcome")),
                ambiguity = node.optionalText("ambiguity"),
                criticDispositions = criticDispositions.map(::criticDisposition)
                    .sortedBy(DocumentCriticDisposition::stableOrderingKey),
                confidence = confidence(node),
            )
        }.sortedBy(DocumentSemanticPlanItem::stableOrderingKey)
        val semanticItemsById = items.associateBy(DocumentSemanticPlanItem::id)
        fun dependencyClosure(seedItemIds: List<String>): List<String> {
            val pending = ArrayDeque(seedItemIds.sorted())
            val included = linkedSetOf<String>()
            while (pending.isNotEmpty()) {
                val itemId = pending.removeFirst()
                if (!included.add(itemId)) continue
                semanticItemsById.getValue(itemId).referencedItemIds
                    .filterNot(included::contains)
                    .sorted()
                    .forEach(pending::addLast)
            }
            return included.sorted()
        }
        fun weakestOutcome(
            declared: DocumentSemanticOutcome,
            itemIds: List<String>,
        ): DocumentSemanticOutcome {
            val outcomes = itemIds.map { semanticItemsById.getValue(it).outcome } + declared
            return when {
                DocumentSemanticOutcome.Blocked in outcomes -> DocumentSemanticOutcome.Blocked
                DocumentSemanticOutcome.ReviewOnly in outcomes -> DocumentSemanticOutcome.ReviewOnly
                else -> DocumentSemanticOutcome.Executable
            }
        }
        val groupsNode = planNode.path("groups")
        require(groupsNode.isArray && groupsNode.size() <= connectedItemsById.size.coerceAtLeast(1))
        val suppliedGroups = groupsNode.map { node ->
            require(node.isObject && node.fieldNames().asSequence().toSet() == SEMANTIC_GROUP_FIELDS) {
                "Unknown semantic group field."
            }
            val criticDispositions = node.path("criticDispositions")
            require(criticDispositions.isArray)
            val itemIds = node.requiredTextArray("itemIds", 1, connectedItemsById.size.coerceAtLeast(1))
                .distinct()
                .sorted()
            require(itemIds.all(connectedItemsById::containsKey)) {
                "Semantic group contains an unknown retained item."
            }
            val canonicalDiscoveryIds = canonicalDiscoveryIds(itemIds)
            DocumentSemanticRecommendationGroup(
                id = node.requiredText("id"),
                title = node.requiredText("title"),
                description = node.requiredText("description"),
                itemIds = itemIds,
                discoveryIds = canonicalDiscoveryIds,
                evidenceIds = canonicalEvidenceIds(canonicalDiscoveryIds),
                outcome = DocumentSemanticOutcome.valueOf(node.requiredText("outcome")),
                rationale = node.requiredText("rationale"),
                criticDispositions = criticDispositions.map(::criticDisposition)
                    .onEach { require(it.findingId in knownCriticIds) }
                    .sortedBy(DocumentCriticDisposition::stableOrderingKey),
                confidence = confidence(node),
            )
        }
        val expandedGroups = suppliedGroups.map { group ->
            val itemIds = dependencyClosure(group.itemIds)
            val discoveryIds = canonicalDiscoveryIds(itemIds)
            val itemConfidence = itemIds.map { semanticItemsById.getValue(it).confidence }
            group.copy(
                itemIds = itemIds,
                discoveryIds = discoveryIds,
                evidenceIds = canonicalEvidenceIds(discoveryIds),
                outcome = weakestOutcome(group.outcome, itemIds),
                confidence = DocumentConfidenceDimensions(
                    evidence = minOf(group.confidence.evidence, itemConfidence.minOf { it.evidence }),
                    modeling = minOf(group.confidence.modeling, itemConfidence.minOf { it.modeling }),
                    ontologyFit = minOf(group.confidence.ontologyFit, itemConfidence.minOf { it.ontologyFit }),
                ),
            )
        }
        val completedGroups = expandedGroups.toMutableList()
        val coveredItemIds = expandedGroups.flatMap(DocumentSemanticRecommendationGroup::itemIds).toMutableSet()
        val usedGroupIds = expandedGroups.map(DocumentSemanticRecommendationGroup::id).toMutableSet()
        val disposedCriticIds = expandedGroups
            .flatMap(DocumentSemanticRecommendationGroup::criticDispositions)
            .map(DocumentCriticDisposition::findingId)
            .toMutableSet()
        items.sortedBy(DocumentSemanticPlanItem::stableOrderingKey).forEach { rootItem ->
            if (rootItem.id in coveredItemIds) return@forEach
            val itemIds = dependencyClosure(listOf(rootItem.id))
            val discoveryIds = canonicalDiscoveryIds(itemIds)
            val closureItems = itemIds.map(semanticItemsById::getValue)
            val groupIdBase = "generated-group-${rootItem.id}".take(200)
            val groupId = generateSequence(groupIdBase) { previous -> "$previous-x".take(200) }
                .first(usedGroupIds::add)
            val criticDispositions = closureItems
                .flatMap(DocumentSemanticPlanItem::criticDispositions)
                .filter { it.findingId !in disposedCriticIds }
                .distinctBy(DocumentCriticDisposition::findingId)
                .sortedBy(DocumentCriticDisposition::stableOrderingKey)
            disposedCriticIds += criticDispositions.map(DocumentCriticDisposition::findingId)
            completedGroups += DocumentSemanticRecommendationGroup(
                id = groupId,
                title = "Complete ${rootItem.label}".take(500),
                description = "Deterministically includes the explicit prerequisites required by ${rootItem.label}.".take(2_000),
                itemIds = itemIds,
                discoveryIds = discoveryIds,
                evidenceIds = canonicalEvidenceIds(discoveryIds),
                outcome = weakestOutcome(rootItem.outcome, itemIds),
                rationale = "This standalone unit preserves an ungrouped semantic item and only its explicit dependencies.",
                criticDispositions = criticDispositions,
                confidence = DocumentConfidenceDimensions(
                    evidence = closureItems.minOf { it.confidence.evidence },
                    modeling = closureItems.minOf { it.confidence.modeling },
                    ontologyFit = closureItems.minOf { it.confidence.ontologyFit },
                ),
            )
            coveredItemIds += itemIds
        }
        val groups = completedGroups.sortedBy(DocumentSemanticRecommendationGroup::stableOrderingKey)
        val verifiedDiscoveryIds = knownDiscoveryIds.sorted()
        val criticFindingIds = knownCriticIds.sorted()
        val plan = DocumentSemanticPlan(
            workKey = request.workKey,
            verifiedDiscoveryIds = verifiedDiscoveryIds,
            criticFindingIds = criticFindingIds,
            items = items,
            groups = groups,
        )
        val coverageNode = root.path("coverage")
        require(coverageNode.isArray && coverageNode.size() <= 500) {
            "Semantic plan coverage array is invalid."
        }
        coverageNode.forEach { node ->
            require(node.isObject && node.fieldNames().asSequence().toSet() == SEMANTIC_COVERAGE_FIELDS) {
                "Unknown semantic coverage field."
            }
        }
        val groupsByDiscovery = groups
            .flatMap { group -> group.discoveryIds.map { discoveryId -> discoveryId to group } }
            .groupBy({ it.first }, { it.second })
        val discoveriesById = request.discoveries.associateBy(DocumentDiscovery::id)
        val coverage = verifiedDiscoveryIds.map { discoveryId ->
            val discovery = discoveriesById.getValue(discoveryId)
            val group = groupsByDiscovery[discoveryId].orEmpty().sortedWith(
                compareBy<DocumentSemanticRecommendationGroup>(
                    {
                        when (it.outcome) {
                            DocumentSemanticOutcome.Executable -> 0
                            DocumentSemanticOutcome.ReviewOnly -> 1
                            DocumentSemanticOutcome.Blocked -> 2
                        }
                    },
                    DocumentSemanticRecommendationGroup::stableOrderingKey,
                ),
            ).firstOrNull()
            val kind = when {
                discovery.contentClassification == DocumentContentClassification.AdministrativeMetadata ->
                    DocumentCoverageDispositionKind.AdministrativeMetadata
                discovery.assertionClassification == DocumentAssertionClassification.IllustrativeExample ||
                    discovery.individualClassification == DocumentIndividualClassification.Illustrative ->
                    DocumentCoverageDispositionKind.IllustrativeExample
                group?.outcome == DocumentSemanticOutcome.Executable ->
                    DocumentCoverageDispositionKind.ExecutableRecommendation
                group?.outcome == DocumentSemanticOutcome.ReviewOnly ->
                    DocumentCoverageDispositionKind.ReviewOnlyFinding
                group?.outcome == DocumentSemanticOutcome.Blocked ->
                    DocumentCoverageDispositionKind.Blocked
                else -> DocumentCoverageDispositionKind.Unsupported
            }
            DocumentCoverageDisposition(
                discoveryId = discoveryId,
                kind = kind,
                recommendationId = group?.id?.takeIf {
                    kind in setOf(
                        DocumentCoverageDispositionKind.ExecutableRecommendation,
                        DocumentCoverageDispositionKind.ReviewOnlyFinding,
                    )
                },
                rationale = group?.rationale?.takeIf { kind == DocumentCoverageDispositionKind.Blocked },
            )
        }.sortedBy(DocumentCoverageDisposition::stableOrderingKey)
        return DocumentSemanticPlanningResponse(plan = plan, coverage = coverage)
    }

    private fun parseStrictFinalPlanningResponse(
        value: String,
        request: DocumentFinalPlanningRequest,
    ): DocumentFinalPlanningResponse {
        val root = objectMapper.readTree(value)
        require(root.isObject && root.fieldNames().asSequence().toSet() == setOf("schemaVersion", "plan"))
        require(root.requiredText("schemaVersion") == DocumentAnalysisPipelineVersions.FINAL_PLAN_RESPONSE)
        val planNode = root.path("plan")
        require(planNode.isObject && planNode.fieldNames().asSequence().toSet() == FINAL_PLAN_FIELDS)
        val recommendations = planNode.path("recommendations")
        val coverage = planNode.path("coverage")
        require(recommendations.isArray)
        require(coverage.isArray)
        val verifiedDiscoveryIds = request.discoveries.map(DocumentDiscovery::id).sorted()
        val discoveryById = request.discoveries.associateBy(DocumentDiscovery::id)
        val knownCriticFindingIds = request.criticFindings.map(DocumentCriticFinding::id).toSet()
        val citationCanonicalRecommendations = recommendations
            .mapNotNull { node ->
                runCatching { parseFinalRecommendation(node, knownCriticFindingIds) }.getOrNull()
            }
            .mapNotNull { canonicalizeFinalRecommendationCitations(it, discoveryById) }
        val parsedRecommendations = canonicalizeFinalRecommendationConfidence(
            canonicalizeFinalCriticDispositions(
                canonicalizeFinalOperationSources(
                    citationCanonicalRecommendations,
                    request.ontologySnapshot.writableSourceIds,
                ),
                request,
            ),
            request,
        )
            .let { recommendationsWithConfidence ->
                canonicalizeFinalIndividualGates(
                    recommendationsWithConfidence,
                    discoveryById,
                )
            }
            .let(::canonicalizeUnresolvedConfidenceGates)
            .let { canonicalizeMissingBusinessDocumentRecommendations(it, request) }
            .sortedBy(DocumentFinalRecommendation::stableOrderingKey)
        val recommendationsByDiscovery = parsedRecommendations
            .flatMap { recommendation ->
                recommendation.discoveryIds.map { discoveryId -> discoveryId to recommendation }
            }
            .groupBy({ it.first }, { it.second })
        val parsedCoverage = coverage
            .mapNotNull { node ->
                runCatching { parseFinalCoverage(node, recommendationsByDiscovery) }.getOrNull()
            }
            .filter { it.discoveryId in discoveryById }
            .groupBy(DocumentCoverageDisposition::discoveryId)
        val canonicalCoverage = verifiedDiscoveryIds.map { discoveryId ->
            val matchingRecommendations = recommendationsByDiscovery[discoveryId].orEmpty().distinct()
            val supplied = parsedCoverage[discoveryId].orEmpty().distinct()
            when {
                matchingRecommendations.isNotEmpty() -> {
                    val recommendation = matchingRecommendations.sortedWith(
                        compareBy<DocumentFinalRecommendation>(
                            { coverageRecommendationPriority(it.status) },
                            { -it.confidence.overall },
                            DocumentFinalRecommendation::stableOrderingKey,
                        ),
                    ).first()
                    DocumentCoverageDisposition(
                        discoveryId = discoveryId,
                        kind = if (recommendation.status in setOf(
                                DocumentFinalRecommendationStatus.Executable,
                                DocumentFinalRecommendationStatus.Mixed,
                            )
                        ) {
                            DocumentCoverageDispositionKind.ExecutableRecommendation
                        } else {
                            DocumentCoverageDispositionKind.ReviewOnlyFinding
                        },
                        recommendationId = recommendation.id,
                    )
                }
                supplied.size == 1 -> supplied.single()
                else -> {
                    val discovery = discoveryById.getValue(discoveryId)
                    DocumentCoverageDisposition(
                        discoveryId = discoveryId,
                        kind = when {
                            discovery.contentClassification == DocumentContentClassification.AdministrativeMetadata ->
                                DocumentCoverageDispositionKind.AdministrativeMetadata
                            discovery.assertionClassification ==
                                com.entio.core.DocumentAssertionClassification.IllustrativeExample ->
                                DocumentCoverageDispositionKind.IllustrativeExample
                            else -> DocumentCoverageDispositionKind.Unsupported
                        },
                    )
                }
            }
        }
        val plan = DocumentFinalPlan(
            workKey = request.workKey,
            verifiedDiscoveryIds = verifiedDiscoveryIds,
            criticFindingIds = request.criticFindings.map(DocumentCriticFinding::id).sorted(),
            recommendations = parsedRecommendations,
            coverage = canonicalCoverage,
        )
        return DocumentFinalPlanningResponse(plan = plan)
    }

    private fun canonicalizeFinalRecommendationConfidence(
        recommendations: List<DocumentFinalRecommendation>,
        request: DocumentFinalPlanningRequest,
    ): List<DocumentFinalRecommendation> {
        val discoveries = request.discoveries.associateBy(DocumentDiscovery::id)
        val modelItemsByDiscovery = request.connectedModel.items
            .flatMap { item -> item.discoveryIds.map { discoveryId -> discoveryId to item.id } }
            .groupBy({ it.first }, { it.second })
        return recommendations.map { recommendation ->
            val relatedTargetIds = recommendation.discoveryIds
                .flatMap { modelItemsByDiscovery[it].orEmpty() }
                .distinct()
            val relatedConfidence = relatedTargetIds.mapNotNull(request.confidenceByTarget::get)
            val evidenceConfidence = recommendation.discoveryIds
                .mapNotNull(discoveries::get)
                .minOfOrNull(DocumentDiscovery::evidenceConfidence)
                ?: recommendation.confidence.evidence
            val canonical = if (relatedConfidence.isEmpty()) {
                DocumentConfidenceDimensions(
                    evidence = evidenceConfidence,
                    modeling = recommendation.confidence.modeling,
                    ontologyFit = recommendation.confidence.ontologyFit,
                )
            } else {
                DocumentConfidenceDimensions(
                    evidence = minOf(evidenceConfidence, relatedConfidence.minOf { it.evidence }),
                    modeling = relatedConfidence.minOf { it.modeling },
                    ontologyFit = relatedConfidence.minOf { it.ontologyFit },
                )
            }
            recommendation.copy(confidence = canonical)
        }
    }

    private fun canonicalizeUnresolvedConfidenceGates(
        recommendations: List<DocumentFinalRecommendation>,
    ): List<DocumentFinalRecommendation> = recommendations.map { recommendation ->
        if (recommendation.operations.isEmpty() ||
            listOf(
                recommendation.confidence.evidence,
                recommendation.confidence.modeling,
                recommendation.confidence.ontologyFit,
            ).all { it > 0 }
        ) {
            recommendation
        } else {
            recommendation.copy(
                status = DocumentFinalRecommendationStatus.Blocked,
                blockers = (recommendation.blockers + "confidence-dimension-unresolved").distinct().sorted(),
            )
        }
    }

    private fun canonicalizeMissingBusinessDocumentRecommendations(
        recommendations: List<DocumentFinalRecommendation>,
        request: DocumentFinalPlanningRequest,
    ): List<DocumentFinalRecommendation> {
        val discoveriesByDocument = request.discoveries
            .filter { it.contentClassification == DocumentContentClassification.BusinessContent }
            .groupBy { it.documentId.value }
        val representedDocumentIds = recommendations
            .flatMap(DocumentFinalRecommendation::discoveryIds)
            .mapNotNull { discoveryId -> request.discoveries.singleOrNull { it.id == discoveryId } }
            .map { it.documentId.value }
            .toSet()
        val modelItemsByDiscovery = request.connectedModel.items
            .flatMap { item -> item.discoveryIds.map { discoveryId -> discoveryId to item } }
            .groupBy({ it.first }, { it.second })
        val retained = discoveriesByDocument
            .filterKeys { it !in representedDocumentIds }
            .toSortedMap()
            .map { (documentId, discoveries) ->
                val discoveryIds = discoveries.map(DocumentDiscovery::id).distinct().sorted()
                val evidenceIds = discoveries.flatMap(DocumentDiscovery::evidence)
                    .map(DocumentEvidence::id)
                    .distinct()
                    .sortedBy(DocumentEvidenceId::value)
                    .take(MAX_FINAL_EVIDENCE_IDS)
                val representativeLabel = discoveryIds
                    .flatMap { modelItemsByDiscovery[it].orEmpty() }
                    .sortedBy { it.order }
                    .firstOrNull()
                    ?.label
                    ?: "document-backed business meaning"
                val suffix = documentId.replace(Regex("[^A-Za-z0-9]"), "").take(32)
                DocumentFinalRecommendation(
                    id = "recommendation-retained-$suffix",
                    title = "Review omitted meaning: $representativeLabel".take(500),
                    description = "Verified business meaning from this document was not represented by the final planner. " +
                        "Entio retained it for explicit review instead of silently discarding it.",
                    discoveryIds = discoveryIds,
                    evidenceIds = evidenceIds,
                    reviewOnlyFindings = listOf(
                        DocumentReviewOnlyFinding(
                            id = "review-retained-$suffix",
                            summary = "Review omitted document-backed meaning".take(1_000),
                            reason = "The final planner did not produce a supported grouped change for this verified meaning.",
                            discoveryIds = discoveryIds,
                            evidenceIds = evidenceIds,
                        ),
                    ),
                    confidence = DocumentConfidenceDimensions(
                        evidence = discoveries.minOf(DocumentDiscovery::evidenceConfidence),
                        modeling = 0,
                        ontologyFit = 0,
                    ),
                    status = DocumentFinalRecommendationStatus.ReviewOnly,
                )
            }
        return recommendations + retained
    }

    private fun canonicalizeFinalOperationSources(
        recommendations: List<DocumentFinalRecommendation>,
        writableSourceIds: List<String>,
    ): List<DocumentFinalRecommendation> = recommendations.map { recommendation ->
        val suppliedSources = recommendation.operations
            .flatMap(DocumentPlanOperation::operands)
            .filterIsInstance<DocumentPlanOperand.SourceId>()
            .map(DocumentPlanOperand.SourceId::value)
            .distinct()
        val source = when {
            suppliedSources.size == 1 && suppliedSources.single() in writableSourceIds ->
                suppliedSources.single()
            suppliedSources.isEmpty() && writableSourceIds.size == 1 ->
                writableSourceIds.single()
            else -> null
        }
        if (source == null) {
            recommendation
        } else {
            recommendation.copy(
                operations = recommendation.operations.map { operation ->
                    if (operation.operands.any { it is DocumentPlanOperand.SourceId }) {
                        operation
                    } else {
                        operation.copy(operands = operation.operands + DocumentPlanOperand.SourceId(source))
                    }
                },
            )
        }
    }

    private fun canonicalizeFinalIndividualGates(
        recommendations: List<DocumentFinalRecommendation>,
        discoveries: Map<String, DocumentDiscovery>,
    ): List<DocumentFinalRecommendation> = recommendations.map { recommendation ->
        val individualOperations = recommendation.operations.filter {
            it.kind == DocumentPlanOperationKind.CreateIndividual
        }
        if (individualOperations.isEmpty()) {
            recommendation.copy(individualReviewGates = emptyList())
        } else {
            val classifications = recommendation.discoveryIds
                .mapNotNull(discoveries::get)
                .filter { it.kind == DocumentDiscoveryKind.Individual }
                .mapNotNull(DocumentDiscovery::individualClassification)
            val classification = when {
                DocumentIndividualClassification.Illustrative in classifications ->
                    DocumentIndividualClassification.Illustrative
                DocumentIndividualClassification.Ambiguous in classifications ->
                    DocumentIndividualClassification.Ambiguous
                DocumentIndividualClassification.Unknown in classifications || classifications.isEmpty() ->
                    DocumentIndividualClassification.Unknown
                else -> DocumentIndividualClassification.Production
            }
            recommendation.copy(
                status = DocumentFinalRecommendationStatus.Blocked,
                blockers = (recommendation.blockers + "individual-confirmation-required").distinct().sorted(),
                individualReviewGates = individualOperations.map { operation ->
                    DocumentIndividualReviewGate(
                        operationId = operation.id,
                        classification = classification,
                    )
                }.sortedBy(DocumentIndividualReviewGate::operationId),
            )
        }
    }

    private fun coverageRecommendationPriority(status: DocumentFinalRecommendationStatus): Int = when (status) {
        DocumentFinalRecommendationStatus.Executable -> 0
        DocumentFinalRecommendationStatus.Mixed -> 1
        DocumentFinalRecommendationStatus.ReviewOnly -> 2
        DocumentFinalRecommendationStatus.Blocked -> 3
    }

    private fun canonicalizeFinalRecommendationCitations(
        recommendation: DocumentFinalRecommendation,
        discoveryById: Map<String, DocumentDiscovery>,
    ): DocumentFinalRecommendation? {
        val discoveryIds = recommendation.discoveryIds
            .filter(discoveryById::containsKey)
            .distinct()
            .sorted()
        if (discoveryIds.isEmpty()) return null

        fun verifiedEvidenceIds(ids: List<String>): List<DocumentEvidenceId> =
            ids.flatMap { discoveryById.getValue(it).evidence }
                .map(DocumentEvidence::id)
                .distinct()
                .sortedBy(DocumentEvidenceId::value)
                .take(MAX_FINAL_EVIDENCE_IDS)

        val evidenceIds = verifiedEvidenceIds(discoveryIds)
        require(evidenceIds.isNotEmpty())
        val reviewOnlyFindings = recommendation.reviewOnlyFindings.map { finding ->
            val findingDiscoveryIds = finding.discoveryIds
                .filter(discoveryIds::contains)
                .distinct()
                .sorted()
                .ifEmpty { discoveryIds }
            finding.copy(
                discoveryIds = findingDiscoveryIds,
                evidenceIds = verifiedEvidenceIds(findingDiscoveryIds),
            )
        }
        return recommendation.copy(
            discoveryIds = discoveryIds,
            evidenceIds = evidenceIds,
            reviewOnlyFindings = reviewOnlyFindings,
        )
    }

    private fun canonicalizeFinalCriticDispositions(
        recommendations: List<DocumentFinalRecommendation>,
        request: DocumentFinalPlanningRequest,
    ): List<DocumentFinalRecommendation> {
        val knownFindings = request.criticFindings.associateBy(DocumentCriticFinding::id)
        val occurrences = recommendations.flatMap { recommendation ->
            recommendation.criticDispositions
                .filter { it.findingId in knownFindings }
                .map { it.findingId to recommendation.id }
        }.groupBy({ it.first }, { it.second })
        val canonical = recommendations.map { recommendation ->
            recommendation.copy(
                criticDispositions = recommendation.criticDispositions
                    .filter { disposition ->
                        disposition.findingId in knownFindings &&
                            occurrences[disposition.findingId].orEmpty().size == 1
                    }
                    .distinctBy(DocumentCriticDisposition::findingId)
                    .sortedBy(DocumentCriticDisposition::stableOrderingKey),
            )
        }.toMutableList()
        val retainedFindingIds = canonical.flatMap(DocumentFinalRecommendation::criticDispositions)
            .map(DocumentCriticDisposition::findingId)
            .toSet()
        val modelItems = request.connectedModel.items.associateBy { it.id }
        val alignments = request.alignments.associateBy { it.id }
        knownFindings.values.filter { it.id !in retainedFindingIds }.sortedBy(DocumentCriticFinding::stableOrderingKey)
            .forEach { finding ->
                val modelItemId = finding.targetId.takeIf(modelItems::containsKey)
                    ?: alignments[finding.targetId]?.modelItemId
                val relatedDiscoveryIds = modelItemId?.let(modelItems::get)?.discoveryIds.orEmpty().toSet()
                val targetIndex = canonical.indices.maxWithOrNull(
                    compareBy<Int>(
                        { canonical[it].discoveryIds.count(relatedDiscoveryIds::contains) },
                        { canonical[it].stableOrderingKey },
                    ),
                )?.takeIf { canonical[it].discoveryIds.any(relatedDiscoveryIds::contains) }
                if (targetIndex == null) {
                    val modelItem = checkNotNull(modelItemId?.let(modelItems::get)) {
                        "A verified critic target must resolve to a connected-model item."
                    }
                    val discoveryIds = relatedDiscoveryIds.sorted()
                    val evidenceIds = discoveryIds
                        .flatMap { discoveryId ->
                            request.discoveries.single { it.id == discoveryId }.evidence
                        }
                        .map(DocumentEvidence::id)
                        .distinct()
                        .sortedBy(DocumentEvidenceId::value)
                        .take(MAX_FINAL_EVIDENCE_IDS)
                    canonical += DocumentFinalRecommendation(
                        id = "recommendation-${finding.id}",
                        title = "Review modeling concern: ${modelItem.label}".take(500),
                        description = finding.reason,
                        discoveryIds = discoveryIds,
                        evidenceIds = evidenceIds,
                        reviewOnlyFindings = listOf(
                            DocumentReviewOnlyFinding(
                                id = "review-${finding.id}",
                                summary = "Review ${modelItem.label}".take(1_000),
                                reason = finding.reason,
                                discoveryIds = discoveryIds,
                                evidenceIds = evidenceIds,
                            ),
                        ),
                        criticDispositions = listOf(
                            DocumentCriticDisposition(
                                findingId = finding.id,
                                kind = DocumentCriticDispositionKind.Unresolved,
                            ),
                        ),
                        confidence = request.confidenceByTarget.getValue(finding.targetId),
                        status = DocumentFinalRecommendationStatus.Blocked,
                        blockers = listOf("unresolved-critic-finding"),
                    )
                    return@forEach
                }
                val target = canonical[targetIndex]
                canonical[targetIndex] = target.copy(
                    criticDispositions = (
                        target.criticDispositions +
                            DocumentCriticDisposition(
                                findingId = finding.id,
                                kind = DocumentCriticDispositionKind.Unresolved,
                            )
                        ).sortedBy(DocumentCriticDisposition::stableOrderingKey),
                    status = DocumentFinalRecommendationStatus.Blocked,
                    blockers = (target.blockers + "unresolved-critic-finding").distinct().sorted(),
                )
            }
        return canonical
    }

    private fun parseFinalCoverage(
        node: JsonNode,
        recommendationsByDiscovery: Map<String, List<DocumentFinalRecommendation>>,
    ): DocumentCoverageDisposition {
        require(node.isObject && node.fieldNames().asSequence().toSet() == FINAL_COVERAGE_FIELDS)
        val discoveryId = node.requiredText("discoveryId")
        val kind = DocumentCoverageDispositionKind.valueOf(node.requiredText("kind"))
        val matchingRecommendations = recommendationsByDiscovery[discoveryId].orEmpty()
        val suppliedRecommendationId = node.optionalText("recommendationId")
        val recommendationId = if (
            kind in setOf(
                DocumentCoverageDispositionKind.ExecutableRecommendation,
                DocumentCoverageDispositionKind.ReviewOnlyFinding,
            )
        ) {
            suppliedRecommendationId
                ?.takeIf { id -> matchingRecommendations.any { it.id == id } }
                ?: matchingRecommendations.singleOrNull()?.id
        } else {
            null
        }
        return DocumentCoverageDisposition(
            discoveryId = discoveryId,
            kind = kind,
            recommendationId = recommendationId,
            relatedDiscoveryId = node.optionalText("relatedDiscoveryId")
                .takeIf { kind == DocumentCoverageDispositionKind.MergedIntoAnotherDiscovery },
            rationale = node.optionalText("rationale")
                .takeIf { kind == DocumentCoverageDispositionKind.RejectedWithRationale },
        )
    }

    private fun parseFinalRecommendation(
        node: JsonNode,
        knownCriticFindingIds: Set<String>,
    ): DocumentFinalRecommendation? {
        require(node.isObject && node.fieldNames().asSequence().toSet() == FINAL_RECOMMENDATION_FIELDS)
        val operations = node.path("operations")
        val reviewOnly = node.path("reviewOnlyFindings")
        val dispositions = node.path("criticDispositions")
        val individualGates = node.path("individualReviewGates")
        require(operations.isArray && operations.size() <= 20)
        require(reviewOnly.isArray && reviewOnly.size() <= 20)
        require(dispositions.isArray && dispositions.size() <= 600)
        require(individualGates.isArray && individualGates.size() <= 20)
        val operationResults = operations.map { operation ->
            operation to runCatching { parseFinalOperation(operation) }
        }
        val parsedOperations = canonicalizeFinalOperations(
            operationResults.mapNotNull { (_, result) -> result.getOrNull() },
        )
        val operationFailures = operationResults.mapNotNull { (operation, result) ->
            result.exceptionOrNull()?.let { failure ->
                val operationId = operation.path("id").asText("(missing-id)").take(80)
                val operationKind = operation.path("kind").asText("(missing-kind)").take(80)
                "operation-contract-invalid: operation '$operationId' ($operationKind): " +
                    (failure.message ?: "The operation did not satisfy the typed-operation contract.")
                        .replace(Regex("\\s+"), " ")
                        .take(250)
            }
        }.distinct().sorted()
        val parsedOperationIds = parsedOperations.map(DocumentPlanOperation::id).toSet()
        val parsedReviewOnly = reviewOnly.mapNotNull { finding ->
            runCatching {
                require(finding.isObject && finding.fieldNames().asSequence().toSet() == FINAL_REVIEW_ONLY_FIELDS)
                DocumentReviewOnlyFinding(
                    id = finding.requiredText("id"),
                    summary = finding.requiredText("summary"),
                    reason = finding.requiredText("reason"),
                    discoveryIds = finding.requiredTextArray("discoveryIds", 1, 100).distinct().sorted(),
                    evidenceIds = finding.requiredTextArray("evidenceIds", 1, 8)
                        .distinct()
                        .map(::DocumentEvidenceId)
                        .sortedBy(DocumentEvidenceId::value),
                    relatedOperationIds = finding.requiredTextArray("relatedOperationIds", 0, 20)
                        .filter(parsedOperationIds::contains)
                        .distinct()
                        .sorted(),
                )
            }.getOrNull()
        }
        val parsedDispositions = dispositions.mapNotNull { disposition ->
            require(
                disposition.isObject &&
                    disposition.fieldNames().asSequence().toSet() == FINAL_CRITIC_DISPOSITION_FIELDS,
            )
            val findingId = disposition.requiredText("findingId")
            if (findingId !in knownCriticFindingIds) return@mapNotNull null
            val kind = DocumentCriticDispositionKind.valueOf(disposition.requiredText("kind"))
            val rationale = disposition.optionalText("rationale")
            if (kind == DocumentCriticDispositionKind.RejectedWithRationale && rationale == null) {
                DocumentCriticDisposition(
                    findingId = findingId,
                    kind = DocumentCriticDispositionKind.Unresolved,
                )
            } else {
                DocumentCriticDisposition(
                    findingId = findingId,
                    kind = kind,
                    rationale = rationale.takeIf { kind == DocumentCriticDispositionKind.RejectedWithRationale },
                )
            }
        }.groupBy(DocumentCriticDisposition::findingId)
            .map { (findingId, duplicates) ->
                val kinds = duplicates.map(DocumentCriticDisposition::kind).distinct()
                if (kinds.size > 1) {
                    DocumentCriticDisposition(
                        findingId = findingId,
                        kind = DocumentCriticDispositionKind.Unresolved,
                    )
                } else {
                    duplicates.sortedBy { it.rationale.orEmpty() }.first()
                }
            }
            .sortedBy(DocumentCriticDisposition::stableOrderingKey)
        val parsedIndividualGates = individualGates.mapNotNull { gate ->
            runCatching {
                require(gate.isObject && gate.fieldNames().asSequence().toSet() == FINAL_INDIVIDUAL_GATE_FIELDS)
                DocumentIndividualReviewGate(
                    operationId = gate.requiredText("operationId"),
                    classification = DocumentIndividualClassification.valueOf(gate.requiredText("classification")),
                    creationConfirmed = false,
                    productionClassificationConfirmed = false,
                )
            }.getOrNull()
        }.filter { gate ->
            parsedOperations.any {
                it.id == gate.operationId && it.kind == DocumentPlanOperationKind.CreateIndividual
            }
        }.sortedBy(DocumentIndividualReviewGate::operationId)
        val discoveryIds = node.requiredTextArray("discoveryIds", 1, 100).distinct().sorted()
        val suppliedEvidenceIds = node.requiredTextArray("evidenceIds", 1, 8)
            .distinct()
            .map(::DocumentEvidenceId)
            .sortedBy(DocumentEvidenceId::value)
        val title = node.requiredText("title")
        val description = node.requiredText("description")
        val fallbackReviewOnly = if (parsedOperations.isEmpty() && parsedReviewOnly.isEmpty()) {
            listOf(
                DocumentReviewOnlyFinding(
                    id = "review-${node.requiredText("id")}",
                    summary = title,
                    reason = (
                        "The proposed change did not satisfy Entio's supported typed-operation contract. " +
                            operationFailures.joinToString(" ").ifBlank {
                                "No supported typed operation was retained."
                            } +
                            " $description"
                        ).take(2_000),
                    discoveryIds = discoveryIds,
                    evidenceIds = suppliedEvidenceIds,
                ),
            )
        } else {
            parsedReviewOnly
        }
        val parsedBlockers = buildList {
            addAll(node.requiredTextArray("blockers", 0, 20))
            if (parsedDispositions.any { it.kind == DocumentCriticDispositionKind.Unresolved }) {
                add("unresolved-critic-finding")
            }
            if (parsedIndividualGates.any { !it.executable }) {
                add("individual-confirmation-required")
            }
            if (parsedOperations.isEmpty() && parsedReviewOnly.isEmpty()) {
                addAll(
                    operationFailures.ifEmpty { listOf("operation-contract-invalid") },
                )
            }
        }.distinct().sorted()
        DocumentFinalRecommendationStatus.valueOf(node.requiredText("status"))
        val canonicalStatus = when {
            parsedBlockers.isNotEmpty() -> DocumentFinalRecommendationStatus.Blocked
            parsedOperations.isNotEmpty() && fallbackReviewOnly.isNotEmpty() -> DocumentFinalRecommendationStatus.Mixed
            parsedOperations.isNotEmpty() -> DocumentFinalRecommendationStatus.Executable
            fallbackReviewOnly.isNotEmpty() -> DocumentFinalRecommendationStatus.ReviewOnly
            else -> return null
        }
        return DocumentFinalRecommendation(
            id = node.requiredText("id"),
            title = title,
            description = description,
            discoveryIds = discoveryIds,
            evidenceIds = suppliedEvidenceIds,
            operations = parsedOperations,
            reviewOnlyFindings = fallbackReviewOnly,
            criticDispositions = parsedDispositions,
            confidence = com.entio.core.DocumentConfidenceDimensions(
                evidence = node.requiredInteger("evidenceConfidence"),
                modeling = node.requiredInteger("modelingConfidence"),
                ontologyFit = node.requiredInteger("ontologyFitConfidence"),
            ),
            status = canonicalStatus,
            blockers = parsedBlockers,
            individualReviewGates = parsedIndividualGates,
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
            dependsOnOperationIds = node.requiredTextArray("dependsOnOperationIds", 0, 20).distinct().sorted(),
            expandedTypedEditCount = node.requiredInteger("expandedTypedEditCount"),
            optionalLeaf = node.path("optionalLeaf").booleanValue(),
        )
    }

    private fun canonicalizeFinalOperations(operations: List<DocumentPlanOperation>): List<DocumentPlanOperation> {
        val uniqueIds = operations.groupBy(DocumentPlanOperation::id)
            .filterValues { it.size == 1 }
            .keys
        val uniqueDeclarations = operations.mapNotNull { operation ->
            operation.declaration?.let { it to operation.id }
        }.groupBy({ it.first }, { it.second })
            .filterValues { it.size == 1 }
            .mapValues { it.value.single() }
        var retained = operations.filter { operation ->
            operation.id in uniqueIds &&
                (operation.declaration == null || operation.declaration in uniqueDeclarations)
        }
        while (true) {
            val retainedIds = retained.map(DocumentPlanOperation::id).toSet()
            val retainedDeclarations = retained.mapNotNull(DocumentPlanOperation::declaration).toSet()
            val next = retained.filter { operation ->
                operation.dependsOnOperationIds.all(retainedIds::contains) &&
                    operation.referencedTemporaryEntities.all(retainedDeclarations::contains)
            }
            if (next.size == retained.size) break
            retained = next
        }
        val byId = retained.associateBy(DocumentPlanOperation::id)
        val declarationByReference = retained.mapNotNull { operation ->
            operation.declaration?.let { it to operation.id }
        }.toMap()
        val dependencies = retained.associate { operation ->
            val temporaryDependencies = operation.referencedTemporaryEntities.mapNotNull(declarationByReference::get)
            operation.id to (operation.dependsOnOperationIds + temporaryDependencies).distinct().sorted()
        }
        val remaining = dependencies.mapValuesTo(mutableMapOf()) { (_, values) -> values.toMutableSet() }
        val ordered = mutableListOf<DocumentPlanOperation>()
        while (remaining.isNotEmpty()) {
            val ready = remaining.filterValues(Set<String>::isEmpty).keys
                .sortedWith(compareBy({ byId.getValue(it).order }, { it }))
            if (ready.isEmpty()) break
            ready.forEach { id ->
                ordered += byId.getValue(id)
                remaining.remove(id)
                remaining.values.forEach { it.remove(id) }
            }
        }
        return ordered.mapIndexed { index, operation ->
            operation.copy(
                order = index,
                dependsOnOperationIds = dependencies.getValue(operation.id),
            )
        }
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
        const val MAX_PROVIDER_ERROR_CHARACTERS: Int = 64_000
        const val MAX_DOCUMENT_PROVIDER_OUTPUT_TOKENS: Int = 8_000
        const val MAX_DOCUMENT_DISCOVERY_OUTPUT_TOKENS: Int = 16_000
        const val MAX_DOCUMENT_CONNECTED_MODEL_OUTPUT_TOKENS: Int = 16_000
        const val MAX_DOCUMENT_MODEL_CONSOLIDATION_OUTPUT_TOKENS: Int = 32_000
        const val MAX_DOCUMENT_PREREQUISITE_OUTPUT_TOKENS: Int = 8_000
        const val MIN_DOCUMENT_ALIGNMENT_OUTPUT_TOKENS: Int = 4_000
        const val DOCUMENT_ALIGNMENT_TOKENS_PER_MODEL_ITEM: Int = 500
        const val MAX_DOCUMENT_ALIGNMENT_OUTPUT_TOKENS: Int = 16_000
        const val MIN_DOCUMENT_FINAL_PLAN_OUTPUT_TOKENS: Int = 6_000
        const val DOCUMENT_FINAL_PLAN_TOKENS_PER_MODEL_ITEM: Int = 1_000
        const val MAX_DOCUMENT_FINAL_PLAN_OUTPUT_TOKENS: Int = 16_000
        val SEMANTIC_PLAN_FIELDS: Set<String> =
            setOf("workKey", "verifiedDiscoveryIds", "criticFindingIds", "items", "groups")
        val SEMANTIC_ITEM_FIELDS: Set<String> = setOf(
            "id",
            "kind",
            "label",
            "definition",
            "literalValue",
            "datatypeIntent",
            "references",
            "discoveryIds",
            "evidenceIds",
            "rationale",
            "outcome",
            "ambiguity",
            "criticDispositions",
            "evidenceConfidence",
            "modelingConfidence",
            "ontologyFitConfidence",
        )
        val SEMANTIC_GROUP_FIELDS: Set<String> = setOf(
            "id",
            "title",
            "description",
            "itemIds",
            "discoveryIds",
            "evidenceIds",
            "outcome",
            "rationale",
            "criticDispositions",
            "evidenceConfidence",
            "modelingConfidence",
            "ontologyFitConfidence",
        )
        val SEMANTIC_REFERENCE_FIELDS: Set<String> = setOf("role", "targetKind", "targetId")
        val SEMANTIC_LITERAL_FIELDS: Set<String> = setOf("lexicalForm", "datatypeIri", "language")
        val SEMANTIC_CRITIC_DISPOSITION_FIELDS: Set<String> = setOf("findingId", "kind", "rationale")
        val SEMANTIC_COVERAGE_FIELDS: Set<String> = setOf(
            "discoveryId",
            "kind",
            "recommendationId",
            "relatedDiscoveryId",
            "alignmentId",
            "rationale",
        )
        val RETRYABLE_SEMANTIC_PLAN_SAFE_CODES: Set<String> = setOf(
            "document-semantic-plan-coverage-invalid",
            "document-semantic-plan-reference-invalid",
            "document-semantic-plan-critic-invalid",
            "document-semantic-plan-group-invalid",
            "document-semantic-plan-item-invalid",
            "document-semantic-plan-schema-invalid",
            "document-semantic-plan-invalid",
        )
        const val MAX_FINAL_EVIDENCE_IDS: Int = 8
        val OPENAI_SCHEMA_PARAMETERS: Set<String> = setOf(
            "response_format",
            "response_format.json_schema.schema",
            "text.format",
            "text.format.schema",
        )
        val EVIDENCE_FIELDS: Set<String> =
            setOf("documentId", "blockId", "startOffsetInBlock", "endOffsetInBlock", "excerpt")
        val DISCOVERY_EVIDENCE_FIELDS: Set<String> = setOf("anchorId")
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
            "modelRecommended",
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

private data class SafeHttpFailure(
    val retryable: Boolean,
    val safeCode: String,
)

private class SafeProviderResponseFailure(
    val code: String,
) : IllegalArgumentException(code) {
    val retryable: Boolean
        get() = code in setOf(
            "document-provider-refusal",
            "document-provider-empty-output",
            "document-provider-incomplete-output",
        )
}
