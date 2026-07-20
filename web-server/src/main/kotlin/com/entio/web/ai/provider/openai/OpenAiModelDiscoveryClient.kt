package com.entio.web.ai.provider.openai

import com.entio.web.ai.models.AiModelCompatibilityPolicy
import com.entio.web.ai.models.AiProviderModelDescriptor
import com.entio.web.ai.provider.AiModelDiscoveryResult
import com.entio.web.ai.provider.AiModelProviderClient
import com.entio.web.ai.provider.AiModelVerificationResult
import com.entio.web.ai.provider.AiProviderModelError
import com.entio.web.ai.provider.AiProviderModelErrorCategory
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import java.io.IOException
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException

public data class OpenAiModelProviderConfiguration(
    val providerId: String = PROVIDER_ID,
    val modelsEndpoint: String = MODELS_ENDPOINT,
    val connectTimeoutMillis: Long = 10_000,
    val requestTimeoutMillis: Long = 30_000,
) {
    init {
        require(providerId == PROVIDER_ID) { "openai-provider-id-required" }
        require(isApprovedEndpoint(modelsEndpoint, "/v1/models")) { "approved-openai-models-endpoint-required" }
        require(connectTimeoutMillis > 0) { "openai-connect-timeout-required" }
        require(requestTimeoutMillis > 0) { "openai-request-timeout-required" }
    }

    public companion object {
        public const val PROVIDER_ID: String = "openai"
        public const val MODELS_ENDPOINT: String = "https://api.openai.com/v1/models"

        private fun isApprovedEndpoint(value: String, path: String): Boolean = runCatching {
            val uri = URI(value)
            uri.scheme == "https" && uri.host == "api.openai.com" && uri.path == path && uri.query == null && uri.fragment == null
        }.getOrDefault(false)
    }
}

/** Fixed-host OpenAI adapter that maps provider payloads immediately to Entio contracts. */
public class OpenAiModelDiscoveryClient(
    private val configuration: OpenAiModelProviderConfiguration = OpenAiModelProviderConfiguration(),
    private val httpClient: HttpClient = createOpenAiModelHttpClient(configuration),
    private val objectMapper: ObjectMapper = ObjectMapper(),
) : AiModelProviderClient, AutoCloseable {
    override val providerId: String = configuration.providerId

    override suspend fun discoverModels(apiKey: String): AiModelDiscoveryResult {
        val key = apiKey.trim()
        if (key.isEmpty()) return failed(AiProviderModelErrorCategory.AUTHENTICATION)
        return try {
            val response = httpClient.get(configuration.modelsEndpoint) {
                header(HttpHeaders.Authorization, "Bearer $key")
                accept(ContentType.Application.Json)
            }
            if (!response.status.isSuccess()) {
                AiModelDiscoveryResult.Failed(mapFailure(response.status, response.bodyAsText(), verification = false))
            } else {
                parseDiscovery(response.body())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: HttpRequestTimeoutException) {
            failed(AiProviderModelErrorCategory.TIMEOUT, retryable = true)
        } catch (_: IOException) {
            failed(AiProviderModelErrorCategory.PROVIDER_UNAVAILABLE, retryable = true)
        } catch (_: Exception) {
            failed(AiProviderModelErrorCategory.MALFORMED_RESPONSE)
        }
    }

    override suspend fun verifyModel(apiKey: String, modelId: String): AiModelVerificationResult {
        val key = apiKey.trim()
        if (key.isEmpty()) return verificationFailed(AiProviderModelErrorCategory.AUTHENTICATION)
        if (!AiModelCompatibilityPolicy.isValidModelId(modelId) || AiModelCompatibilityPolicy.isMovingAlias(modelId)) {
            return verificationFailed(AiProviderModelErrorCategory.MODEL_NOT_AVAILABLE)
        }
        return try {
            val response = httpClient.get(modelEndpoint(modelId)) {
                header(HttpHeaders.Authorization, "Bearer $key")
                accept(ContentType.Application.Json)
            }
            if (!response.status.isSuccess()) {
                AiModelVerificationResult.Failed(mapFailure(response.status, response.bodyAsText(), verification = true))
            } else {
                AiModelVerificationResult.Verified
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: HttpRequestTimeoutException) {
            verificationFailed(AiProviderModelErrorCategory.TIMEOUT, retryable = true)
        } catch (_: IOException) {
            verificationFailed(AiProviderModelErrorCategory.PROVIDER_UNAVAILABLE, retryable = true)
        } catch (_: Exception) {
            verificationFailed(AiProviderModelErrorCategory.MALFORMED_RESPONSE)
        }
    }

    private fun parseDiscovery(body: String): AiModelDiscoveryResult = runCatching {
        val data = objectMapper.readTree(body).path("data")
        require(data.isArray) { "provider-model-data-required" }
        val models = data.map { item ->
            val id = item.path("id").takeIf(JsonNode::isTextual)?.textValue()?.takeIf(String::isNotBlank)
                ?: error("provider-model-id-required")
            AiProviderModelDescriptor(providerId, id)
        }.distinctBy(AiProviderModelDescriptor::modelId).sortedBy(AiProviderModelDescriptor::modelId)
        AiModelDiscoveryResult.Discovered(models)
    }.getOrElse { failed(AiProviderModelErrorCategory.MALFORMED_RESPONSE) }

    private fun modelEndpoint(modelId: String): String = "${configuration.modelsEndpoint}/$modelId"

    private fun mapFailure(status: HttpStatusCode, body: String, verification: Boolean): AiProviderModelError {
        val error = runCatching { objectMapper.readTree(body).path("error") }.getOrNull()
        val code = error?.path("code")?.takeIf(JsonNode::isTextual)?.textValue()
        val parameter = error?.path("param")?.takeIf(JsonNode::isTextual)?.textValue()
        val modelSpecific = verification && (status == HttpStatusCode.NotFound || parameter == "model" || code in modelErrorCodes)
        return when {
            modelSpecific -> AiProviderModelError(AiProviderModelErrorCategory.MODEL_NOT_AVAILABLE, retryable = false)
            status == HttpStatusCode.Unauthorized -> AiProviderModelError(AiProviderModelErrorCategory.AUTHENTICATION, retryable = false)
            status == HttpStatusCode.Forbidden -> AiProviderModelError(AiProviderModelErrorCategory.AUTHORIZATION, retryable = false)
            status == HttpStatusCode.TooManyRequests -> AiProviderModelError(
                AiProviderModelErrorCategory.RATE_LIMITED,
                retryable = true,
            )
            status.value >= 500 -> AiProviderModelError(AiProviderModelErrorCategory.PROVIDER_UNAVAILABLE, retryable = true)
            else -> AiProviderModelError(AiProviderModelErrorCategory.MALFORMED_RESPONSE, retryable = false)
        }
    }

    override fun close() {
        httpClient.close()
    }

    public companion object {
        private val modelErrorCodes: Set<String> = setOf("model_not_found", "model_not_available", "unsupported_model")
    }
}

public fun createOpenAiModelHttpClient(
    configuration: OpenAiModelProviderConfiguration,
    engine: HttpClientEngine = CIO.create(),
): HttpClient = HttpClient(engine) {
    expectSuccess = false
    followRedirects = false
    install(HttpTimeout) {
        connectTimeoutMillis = configuration.connectTimeoutMillis
        requestTimeoutMillis = configuration.requestTimeoutMillis
    }
}

private fun failed(category: AiProviderModelErrorCategory, retryable: Boolean = false): AiModelDiscoveryResult.Failed =
    AiModelDiscoveryResult.Failed(AiProviderModelError(category, retryable))

private fun verificationFailed(
    category: AiProviderModelErrorCategory,
    retryable: Boolean = false,
): AiModelVerificationResult.Failed = AiModelVerificationResult.Failed(AiProviderModelError(category, retryable))
