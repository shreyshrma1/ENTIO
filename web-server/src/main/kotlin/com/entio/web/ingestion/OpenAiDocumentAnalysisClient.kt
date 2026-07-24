package com.entio.web.ingestion

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
) : DocumentAnalysisProvider, AutoCloseable {
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

    override fun close(): Unit = client.close()

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

    private fun strictTextFormat(): JsonNode {
        val candidate = objectMapper.createObjectNode().apply {
            put("type", "object")
            put("additionalProperties", false)
            set<JsonNode>("required", objectMapper.valueToTree(CANDIDATE_FIELDS.sorted()))
            set<JsonNode>("properties", objectMapper.createObjectNode().apply {
                putObject("category").put("type", "string")
                putObject("recommendationCategory").put("type", "string")
                putObject("proposedLabel").put("type", "string").put("maxLength", 500)
                putObject("confidence").put("type", "integer").put("minimum", 0).put("maximum", 100)
                putObject("interpretation").put("type", "string")
                putObject("evidenceType").put("type", "string")
                set<JsonNode>("evidence", objectMapper.createObjectNode().apply {
                    put("type", "array")
                    put("minItems", 1)
                    put("maxItems", 8)
                    set<JsonNode>("items", objectMapper.createObjectNode().apply {
                        put("type", "object")
                        put("additionalProperties", false)
                        set<JsonNode>("required", objectMapper.valueToTree(EVIDENCE_FIELDS.sorted()))
                        set<JsonNode>("properties", objectMapper.createObjectNode().apply {
                            EVIDENCE_FIELDS.forEach { field ->
                                putObject(field).put(
                                    "type",
                                    if (field.endsWith("OffsetInBlock")) "integer" else "string",
                                )
                            }
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

    private fun extractOutputText(response: String): String {
        val root = objectMapper.readTree(response)
        val texts = root.path("output").flatMap { output ->
            output.path("content").filter { it.path("type").asText() == "output_text" }.map { it.path("text").asText() }
        }
        return texts.joinToString("").takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Missing structured provider output.")
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

    private fun JsonNode.requiredText(name: String): String =
        path(name).takeIf(JsonNode::isTextual)?.asText()?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Missing required text.")

    private fun JsonNode.requiredInteger(name: String): Int =
        path(name).takeIf(JsonNode::isIntegralNumber)?.intValue()
            ?: throw IllegalArgumentException("Missing required integer.")

    private companion object {
        const val RESPONSE_SCHEMA_VERSION: String = "phase-11-document-analysis-response-v1"
        const val MAX_PROVIDER_RESPONSE_CHARACTERS: Int = 1_000_000
        val EVIDENCE_FIELDS: Set<String> =
            setOf("documentId", "blockId", "startOffsetInBlock", "endOffsetInBlock", "excerpt")
        val CANDIDATE_FIELDS: Set<String> = setOf(
            "category",
            "recommendationCategory",
            "proposedLabel",
            "confidence",
            "interpretation",
            "evidenceType",
            "evidence",
            "ambiguityFlags",
        )
    }
}
