package com.entio.web.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import java.io.IOException
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException

public data class OpenAiProviderConfiguration(
    val providerId: String = PROVIDER_ID,
    val modelId: String = MODEL_ID,
    val promptVersion: String,
    val endpoint: String = ENDPOINT,
    val store: Boolean = false,
    val connectTimeoutMillis: Long = 10_000,
    val requestTimeoutMillis: Long = 120_000,
    val maxRetries: Int = 2,
) {
    init {
        require(providerId == PROVIDER_ID) { "openai-provider-id-required" }
        require(modelId == MODEL_ID && !modelId.contains("latest")) { "approved-openai-model-required" }
        require(promptVersion.isNotBlank()) { "openai-prompt-version-required" }
        require(endpoint == ENDPOINT && URI(endpoint).host == "api.openai.com") { "approved-openai-endpoint-required" }
        require(!store) { "openai-response-storage-must-be-disabled" }
        require(connectTimeoutMillis > 0)
        require(requestTimeoutMillis > 0)
        require(maxRetries in 0..2)
    }

    public companion object {
        public const val PROVIDER_ID: String = "openai"
        public const val MODEL_ID: String = "gpt-5.2"
        public const val ENDPOINT: String = "https://api.openai.com/v1/responses"
    }
}

public data class OpenAiResponsesRequest(
    val trustedPolicy: String,
    val userInput: String,
    val capabilities: AiCapabilityRegistrySnapshot,
    val previousResponseId: String? = null,
    val toolOutputs: List<OpenAiToolOutput> = emptyList(),
)

public data class OpenAiToolOutput(
    val callId: String,
    val output: String,
)

public interface AiToolLoopProvider {
    public val providerId: String
    public val modelId: String
    public val promptVersion: String

    public suspend fun respond(
        apiKey: String,
        request: OpenAiResponsesRequest,
        onEvent: suspend (OpenAiProviderEvent) -> Unit,
    ): OpenAiResponsesResult
}

/** Deterministic fallback for tests that do not opt into the production OpenAI boundary. */
public class DevelopmentAiToolLoopProvider(
    override val providerId: String = "provider-neutral",
    override val modelId: String = "development-ai",
    override val promptVersion: String = "phase-7-development-v1",
) : AiToolLoopProvider {
    override suspend fun respond(
        apiKey: String,
        request: OpenAiResponsesRequest,
        onEvent: suspend (OpenAiProviderEvent) -> Unit,
    ): OpenAiResponsesResult = OpenAiResponsesResult.Completed(
        OpenAiCompletedResponse(
            responseId = null,
            text = "The development AI boundary received the request. Configure the approved OpenAI provider for tool-driven assistance.",
            functionCalls = emptyList(),
            usage = OpenAiUsage(inputTokens = 0, outputTokens = 0, totalTokens = 0),
            events = emptyList(),
        ),
    )
}

public data class OpenAiFunctionCall(
    val callId: String,
    val name: String,
    val argumentsJson: String,
)

public data class OpenAiUsage(
    val inputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
)

public sealed interface OpenAiProviderEvent {
    public data class TextDelta(val delta: String) : OpenAiProviderEvent
    public data class FunctionArgumentsDelta(val callId: String?, val delta: String) : OpenAiProviderEvent
    public data class FunctionCall(val call: OpenAiFunctionCall) : OpenAiProviderEvent
    public data class Completed(val responseId: String?, val usage: OpenAiUsage?) : OpenAiProviderEvent
    public data class Refused(val message: String) : OpenAiProviderEvent
    public data class Incomplete(val reason: String) : OpenAiProviderEvent
    public data object Cancelled : OpenAiProviderEvent
    public data class Error(val code: String) : OpenAiProviderEvent
}

public data class OpenAiCompletedResponse(
    val responseId: String?,
    val text: String,
    val functionCalls: List<OpenAiFunctionCall>,
    val usage: OpenAiUsage?,
    val events: List<OpenAiProviderEvent>,
)

public enum class OpenAiFailureCode {
    INVALID_CREDENTIAL,
    RATE_LIMIT,
    TIMEOUT,
    PROVIDER_UNAVAILABLE,
    MALFORMED_RESPONSE,
    REFUSAL,
    INCOMPLETE,
    CANCELLED,
    PROVIDER_ERROR,
}

public data class OpenAiProviderFailure(
    val code: OpenAiFailureCode,
    val message: String,
    val retryable: Boolean,
)

public sealed interface OpenAiResponsesResult {
    public data class Completed(val response: OpenAiCompletedResponse) : OpenAiResponsesResult
    public data class Failed(val failure: OpenAiProviderFailure) : OpenAiResponsesResult
}

/** OpenAI Responses adapter. It accepts only the approved endpoint, model, and custom Entio function tools. */
public class OpenAiResponsesClient(
    private val configuration: OpenAiProviderConfiguration,
    private val httpClient: HttpClient = createOpenAiHttpClient(configuration),
    private val objectMapper: ObjectMapper = ObjectMapper(),
) : AiProviderClient, AiAssistantProvider, AiToolLoopProvider, AutoCloseable {
    override val providerId: String = configuration.providerId
    override val modelId: String = configuration.modelId
    override val promptVersion: String = configuration.promptVersion

    override suspend fun test(apiKey: String): AiProviderTestResult {
        val result = respond(
            apiKey,
            OpenAiResponsesRequest(
                trustedPolicy = "Return a short acknowledgement. Do not request a tool.",
                userInput = "Test the configured Entio provider credential.",
                capabilities = AiCapabilityRegistrySnapshot("credential-test", emptyList()),
            ),
        )
        return when (result) {
            is OpenAiResponsesResult.Completed -> AiProviderTestResult.Passed("The OpenAI credential was accepted by the approved provider boundary.")
            is OpenAiResponsesResult.Failed -> AiProviderTestResult.Failed(result.failure.message)
        }
    }

    override suspend fun complete(apiKey: String, request: AiProviderRequest): AiProviderCompletion {
        val result = respond(
            apiKey,
            OpenAiResponsesRequest(
                trustedPolicy = request.context.trustedPolicy,
                userInput = request.context.userRequest,
                capabilities = AiCapabilityRegistrySnapshot("phase-6-compatibility", emptyList()),
            ),
        )
        return when (result) {
            is OpenAiResponsesResult.Failed -> AiProviderCompletion.Failed(result.failure.message)
            is OpenAiResponsesResult.Completed -> AiProviderCompletion.Success(
                answer = result.response.text.ifBlank { "The provider returned no text response." },
                warnings = if (result.response.functionCalls.isEmpty()) emptyList() else {
                    listOf("The provider requested a capability, but tool execution is not enabled in this slice.")
                },
            )
        }
    }

    public suspend fun respond(apiKey: String, request: OpenAiResponsesRequest): OpenAiResponsesResult =
        respond(apiKey, request) {}

    override suspend fun respond(
        apiKey: String,
        request: OpenAiResponsesRequest,
        onEvent: suspend (OpenAiProviderEvent) -> Unit,
    ): OpenAiResponsesResult {
        if (apiKey.isBlank()) return failure(OpenAiFailureCode.INVALID_CREDENTIAL, false)
        val payload = serializeRequest(request)
        var attempt = 0
        while (true) {
            try {
                val response = httpClient.post(configuration.endpoint) {
                    header(HttpHeaders.Authorization, "Bearer $apiKey")
                    accept(ContentType.Text.EventStream)
                    setBody(TextContent(payload, ContentType.Application.Json))
                }
                if (!response.status.isSuccess()) {
                    val mapped = mapHttpFailure(response.status)
                    if (mapped.retryable && attempt < configuration.maxRetries) {
                        attempt += 1
                        continue
                    }
                    return OpenAiResponsesResult.Failed(mapped)
                }
                return parseEvents(response, onEvent)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: HttpRequestTimeoutException) {
                if (attempt++ < configuration.maxRetries) continue
                return failure(OpenAiFailureCode.TIMEOUT, true)
            } catch (_: IOException) {
                if (attempt++ < configuration.maxRetries) continue
                return failure(OpenAiFailureCode.PROVIDER_UNAVAILABLE, true)
            } catch (_: Exception) {
                return failure(OpenAiFailureCode.PROVIDER_ERROR, false)
            }
        }
    }

    public fun serializeRequest(request: OpenAiResponsesRequest): String {
        val root = objectMapper.createObjectNode()
        root.put("model", configuration.modelId)
        root.put("store", false)
        root.put("stream", true)
        root.put("parallel_tool_calls", false)
        request.previousResponseId?.let { root.put("previous_response_id", it) }
        root.set<ArrayNode>("input", objectMapper.createArrayNode().apply {
            add(inputMessage("developer", request.trustedPolicy))
            add(inputMessage("user", request.userInput))
            request.toolOutputs.forEach { output ->
                add(objectMapper.createObjectNode().apply {
                    put("type", "function_call_output")
                    put("call_id", output.callId)
                    put("output", output.output)
                })
            }
        })
        root.set<ArrayNode>("tools", objectMapper.createArrayNode().apply {
            request.capabilities.definitions.forEach { add(serializeTool(it)) }
        })
        root.set<ObjectNode>("metadata", objectMapper.createObjectNode().put("prompt_version", configuration.promptVersion))
        return objectMapper.writeValueAsString(root)
    }

    private suspend fun parseEvents(
        response: HttpResponse,
        onEvent: suspend (OpenAiProviderEvent) -> Unit,
    ): OpenAiResponsesResult {
        val text = StringBuilder()
        val calls = mutableListOf<OpenAiFunctionCall>()
        val events = mutableListOf<OpenAiProviderEvent>()
        var responseId: String? = null
        var usage: OpenAiUsage? = null
        var terminal = false
        val channel = response.body<io.ktor.utils.io.ByteReadChannel>()
        while (true) {
            val line = channel.readUTF8Line() ?: break
            if (!line.startsWith("data:")) continue
            val data = line.removePrefix("data:").trim()
            if (data.isEmpty() || data == "[DONE]") continue
            val node = runCatching { objectMapper.readTree(data) }.getOrNull()
                ?: return failure(OpenAiFailureCode.MALFORMED_RESPONSE, false)
            val event = parseEvent(node) ?: continue
            events += event
            onEvent(event)
            when (event) {
                is OpenAiProviderEvent.TextDelta -> text.append(event.delta)
                is OpenAiProviderEvent.FunctionCall -> calls += event.call
                is OpenAiProviderEvent.Completed -> {
                    responseId = event.responseId
                    usage = event.usage
                    terminal = true
                }
                is OpenAiProviderEvent.Refused -> return failure(OpenAiFailureCode.REFUSAL, false)
                is OpenAiProviderEvent.Incomplete -> return failure(OpenAiFailureCode.INCOMPLETE, false)
                OpenAiProviderEvent.Cancelled -> return failure(OpenAiFailureCode.CANCELLED, false)
                is OpenAiProviderEvent.Error -> return failure(OpenAiFailureCode.PROVIDER_ERROR, false)
                is OpenAiProviderEvent.FunctionArgumentsDelta -> Unit
            }
        }
        if (!terminal) return failure(OpenAiFailureCode.MALFORMED_RESPONSE, false)
        return OpenAiResponsesResult.Completed(OpenAiCompletedResponse(responseId, text.toString(), calls, usage, events))
    }

    private fun parseEvent(node: JsonNode): OpenAiProviderEvent? = when (node.path("type").asText()) {
        "response.output_text.delta" -> OpenAiProviderEvent.TextDelta(node.path("delta").asText())
        "response.function_call_arguments.delta" -> OpenAiProviderEvent.FunctionArgumentsDelta(
            node.path("call_id").textOrNull() ?: node.path("item_id").textOrNull(),
            node.path("delta").asText(),
        )
        "response.output_item.done" -> node.path("item").takeIf { it.path("type").asText() == "function_call" }?.let { item ->
            OpenAiProviderEvent.FunctionCall(
                OpenAiFunctionCall(
                    callId = item.path("call_id").requiredText("call_id"),
                    name = item.path("name").requiredText("name"),
                    argumentsJson = item.path("arguments").requiredText("arguments"),
                ),
            )
        }
        "response.completed" -> {
            val completed = node.path("response")
            OpenAiProviderEvent.Completed(completed.path("id").textOrNull(), completed.path("usage").toUsage())
        }
        "response.refusal.delta", "response.refusal.done" -> OpenAiProviderEvent.Refused(
            node.path("delta").textOrNull() ?: node.path("refusal").textOrNull() ?: "The provider refused the request.",
        )
        "response.incomplete" -> OpenAiProviderEvent.Incomplete(
            node.path("response").path("incomplete_details").path("reason").textOrNull() ?: "The provider response was incomplete.",
        )
        "response.cancelled" -> OpenAiProviderEvent.Cancelled
        "response.failed", "error" -> OpenAiProviderEvent.Error(node.path("error").path("code").textOrNull() ?: "provider-error")
        else -> null
    }

    private fun inputMessage(role: String, text: String): ObjectNode = objectMapper.createObjectNode().apply {
        put("role", role)
        set<ArrayNode>("content", objectMapper.createArrayNode().add(objectMapper.createObjectNode().apply {
            put("type", "input_text")
            put("text", text)
        }))
    }

    private fun serializeTool(definition: AiCapabilityDefinition): ObjectNode = objectMapper.createObjectNode().apply {
        put("type", "function")
        put("name", definition.name)
        put("description", definition.description)
        put("strict", true)
        set<ObjectNode>("parameters", serializeSchema(definition.inputSchema) as ObjectNode)
    }

    private fun serializeSchema(schema: AiJsonSchema, nullable: Boolean = false): JsonNode = when (schema) {
        is AiObjectSchema -> objectMapper.createObjectNode().apply {
            put("type", "object")
            set<ObjectNode>("properties", objectMapper.createObjectNode().apply {
                schema.properties.forEach { property -> set<JsonNode>(property.name, serializeSchema(property.schema, property.nullable)) }
            })
            set<ArrayNode>("required", objectMapper.createArrayNode().apply { schema.properties.forEach { add(it.name) } })
            put("additionalProperties", false)
        }
        is AiStringSchema -> objectMapper.createObjectNode().apply {
            putType(this, "string", nullable)
            put("minLength", schema.minLength)
            put("maxLength", schema.maxLength)
            if (schema.allowedValues.isNotEmpty()) {
                set<ArrayNode>("enum", objectMapper.createArrayNode().apply {
                    schema.allowedValues.forEach(::add)
                    if (nullable) addNull()
                })
            }
            schema.format?.let {
                put(
                    "pattern",
                    if (it == AiStringFormat.HTTP_IRI) "^https?://[^\\s]+$" else "^[A-Za-z][A-Za-z0-9._-]{0,127}$",
                )
            }
        }
        is AiIntegerSchema -> objectMapper.createObjectNode().apply {
            putType(this, "integer", nullable)
            put("minimum", schema.minimum)
            put("maximum", schema.maximum)
        }
        is AiArraySchema -> objectMapper.createObjectNode().apply {
            putType(this, "array", nullable)
            set<JsonNode>("items", serializeSchema(schema.items))
            put("minItems", schema.minItems)
            put("maxItems", schema.maxItems)
            put("uniqueItems", schema.uniqueItems)
        }
    }

    private fun putType(node: ObjectNode, type: String, nullable: Boolean) {
        if (nullable) node.set<ArrayNode>("type", objectMapper.createArrayNode().add(type).add("null")) else node.put("type", type)
    }

    private fun mapHttpFailure(status: HttpStatusCode): OpenAiProviderFailure = when (status.value) {
        401, 403 -> OpenAiProviderFailure(OpenAiFailureCode.INVALID_CREDENTIAL, "The OpenAI credential was rejected.", false)
        408 -> OpenAiProviderFailure(OpenAiFailureCode.TIMEOUT, "The OpenAI request timed out.", true)
        429 -> OpenAiProviderFailure(OpenAiFailureCode.RATE_LIMIT, "The OpenAI request was rate limited.", true)
        in 500..599 -> OpenAiProviderFailure(OpenAiFailureCode.PROVIDER_UNAVAILABLE, "OpenAI is temporarily unavailable.", true)
        else -> OpenAiProviderFailure(OpenAiFailureCode.PROVIDER_ERROR, "The OpenAI request failed safely.", false)
    }

    override fun close() {
        httpClient.close()
    }
}

public fun createOpenAiHttpClient(configuration: OpenAiProviderConfiguration): HttpClient = createOpenAiHttpClient(configuration, CIO.create())

/** Creates the production Phase 7 provider with the fixed endpoint, storage policy, and model allowlist. */
public fun defaultOpenAiResponsesClient(): OpenAiResponsesClient {
    val modelId = System.getenv("ENTIO_OPENAI_MODEL")?.takeIf(String::isNotBlank)
        ?: OpenAiProviderConfiguration.MODEL_ID
    return OpenAiResponsesClient(
        OpenAiProviderConfiguration(
            modelId = modelId,
            promptVersion = "phase-7-v1",
        ),
    )
}

public fun createOpenAiHttpClient(configuration: OpenAiProviderConfiguration, engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = configuration.connectTimeoutMillis
        requestTimeoutMillis = configuration.requestTimeoutMillis
    }
}

private fun JsonNode.textOrNull(): String? = takeIf { isTextual }?.textValue()

private fun JsonNode.requiredText(field: String): String = textOrNull()?.takeIf(String::isNotBlank)
    ?: throw IllegalArgumentException("Missing provider $field.")

private fun JsonNode.toUsage(): OpenAiUsage? = takeIf { isObject }?.let {
    val input = path("input_tokens")
    val output = path("output_tokens")
    val total = path("total_tokens")
    if (!input.canConvertToLong() || !output.canConvertToLong() || !total.canConvertToLong()) null
    else OpenAiUsage(input.longValue(), output.longValue(), total.longValue())
}

private fun failure(code: OpenAiFailureCode, retryable: Boolean): OpenAiResponsesResult.Failed =
    OpenAiResponsesResult.Failed(
        OpenAiProviderFailure(
            code = code,
            message = when (code) {
                OpenAiFailureCode.INVALID_CREDENTIAL -> "The OpenAI credential was rejected."
                OpenAiFailureCode.RATE_LIMIT -> "The OpenAI request was rate limited."
                OpenAiFailureCode.TIMEOUT -> "The OpenAI request timed out."
                OpenAiFailureCode.PROVIDER_UNAVAILABLE -> "OpenAI is temporarily unavailable."
                OpenAiFailureCode.MALFORMED_RESPONSE -> "OpenAI returned a malformed response."
                OpenAiFailureCode.REFUSAL -> "OpenAI refused the request."
                OpenAiFailureCode.INCOMPLETE -> "OpenAI could not complete the request."
                OpenAiFailureCode.CANCELLED -> "The OpenAI request was cancelled."
                OpenAiFailureCode.PROVIDER_ERROR -> "The OpenAI request failed safely."
            },
            retryable = retryable,
        ),
    )
