package com.entio.web.ai

import io.ktor.client.HttpClient
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
import io.ktor.http.isSuccess
import java.io.IOException
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException

/** Configuration used only for provider credential verification. */
public data class OpenAiProviderConfiguration(
    val providerId: String = PROVIDER_ID,
    val modelsEndpoint: String = MODELS_ENDPOINT,
    val connectTimeoutMillis: Long = 10_000,
    val requestTimeoutMillis: Long? = 30_000,
    val maxRetries: Int = 2,
    val retryBaseDelayMillis: Long = 250,
    val maxRetryDelayMillis: Long = 2_000,
) {
    init {
        require(providerId == PROVIDER_ID) { "openai-provider-id-required" }
        require(isApprovedModelsEndpoint(modelsEndpoint)) { "approved-openai-models-endpoint-required" }
        require(connectTimeoutMillis > 0)
        require(requestTimeoutMillis == null || requestTimeoutMillis > 0)
        require(maxRetries in 0..2)
    }

    public companion object {
        public const val PROVIDER_ID: String = "openai"
        public const val MODELS_ENDPOINT: String = "https://api.openai.com/v1/models"

        private fun isApprovedModelsEndpoint(value: String): Boolean = runCatching {
            val uri = URI(value)
            uri.scheme == "https" && uri.host == "api.openai.com" && uri.path == "/v1/models" &&
                uri.query == null && uri.fragment == null
        }.getOrDefault(false)
    }
}

/** OpenAI adapter retained solely for testing whether a user credential can access the provider. */
public class OpenAiCredentialClient(
    private val configuration: OpenAiProviderConfiguration,
    private val httpClient: HttpClient = createOpenAiHttpClient(configuration),
) : AiProviderClient, AutoCloseable {
    override val providerId: String = configuration.providerId

    override suspend fun test(apiKey: String): AiProviderTestResult {
        val normalizedApiKey = apiKey.trim()
        if (normalizedApiKey.isEmpty()) return AiProviderTestResult.Failed("The OpenAI credential was rejected.")

        repeat(configuration.maxRetries + 1) { attempt ->
            try {
                val response = httpClient.get(configuration.modelsEndpoint) {
                    header(HttpHeaders.Authorization, "Bearer $normalizedApiKey")
                    accept(ContentType.Application.Json)
                }
                if (response.status.isSuccess()) {
                    response.bodyAsText()
                    return AiProviderTestResult.Passed("The OpenAI credential was accepted.")
                }
                if (attempt == configuration.maxRetries || response.status.value !in setOf(408, 429, 500, 502, 503, 504)) {
                    return AiProviderTestResult.Failed("OpenAI rejected the credential test with HTTP ${response.status.value}.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: HttpRequestTimeoutException) {
                if (attempt == configuration.maxRetries) return AiProviderTestResult.Failed("The OpenAI credential test timed out.")
            } catch (_: IOException) {
                if (attempt == configuration.maxRetries) return AiProviderTestResult.Failed("OpenAI is temporarily unavailable.")
            } catch (_: Exception) {
                return AiProviderTestResult.Failed("The OpenAI credential test failed before OpenAI returned a response.")
            }
        }
        return AiProviderTestResult.Failed("The OpenAI credential test failed.")
    }

    override fun close() {
        httpClient.close()
    }
}

public fun createOpenAiHttpClient(configuration: OpenAiProviderConfiguration): HttpClient =
    createOpenAiHttpClient(configuration, CIO.create())

public fun defaultOpenAiCredentialClient(): OpenAiCredentialClient = OpenAiCredentialClient(OpenAiProviderConfiguration())

public fun createOpenAiHttpClient(configuration: OpenAiProviderConfiguration, engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    install(HttpTimeout) {
        connectTimeoutMillis = configuration.connectTimeoutMillis
        requestTimeoutMillis = configuration.requestTimeoutMillis
    }
}
