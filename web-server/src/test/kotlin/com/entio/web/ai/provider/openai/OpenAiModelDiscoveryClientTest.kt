package com.entio.web.ai.provider.openai

import com.entio.web.ai.models.AiProviderModelDescriptor
import com.entio.web.ai.provider.AiModelDiscoveryResult
import com.entio.web.ai.provider.AiModelVerificationResult
import com.entio.web.ai.provider.AiProviderModelError
import com.entio.web.ai.provider.AiProviderModelErrorCategory
import com.entio.web.ai.provider.DevelopmentAiModelProviderClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OpenAiModelDiscoveryClientTest {

    @Test
    fun discoveryUsesFixedGetBoundaryAndMapsOnlyStableIds(): Unit = runBlocking {
        var requestMethod: HttpMethod? = null
        var requestUrl: String? = null
        var authorization: String? = null
        val engine = MockEngine { request ->
            requestMethod = request.method
            requestUrl = request.url.toString()
            authorization = request.headers[HttpHeaders.Authorization]
            respond(
                """{"object":"list","data":[{"id":"zeta-tool-model","owned_by":"secret-owner"},{"id":"gpt-5.6-sol","created":123},{"id":"gpt-5.6-sol","permission":["raw"]}]}""",
                headers = jsonHeaders(),
            )
        }

        val result = client(engine).use { it.discoverModels("  secret-key\n") }
        val models = assertIs<AiModelDiscoveryResult.Discovered>(result).models

        assertEquals(HttpMethod.Get, requestMethod)
        assertEquals(OpenAiModelProviderConfiguration.MODELS_ENDPOINT, requestUrl)
        assertEquals("Bearer secret-key", authorization)
        assertEquals(listOf("gpt-5.6-sol", "zeta-tool-model"), models.map { it.modelId })
        assertFalse(models.toString().contains("owned_by"))
        assertFalse(models.toString().contains("secret-owner"))
    }

    @Test
    fun emptyInventoryIsValidAndMalformedInventoryFailsSafely(): Unit = runBlocking {
        val empty = client(MockEngine { respond("""{"data":[]}""", headers = jsonHeaders()) })
            .use { it.discoverModels("key") }
        assertTrue(assertIs<AiModelDiscoveryResult.Discovered>(empty).models.isEmpty())

        listOf("{}", """{"data":{}}""", """{"data":[{}]}""", "not-json").forEach { body ->
            val result = client(MockEngine { respond(body, headers = jsonHeaders()) }).use { it.discoverModels("key") }
            assertEquals(
                AiProviderModelErrorCategory.MALFORMED_RESPONSE,
                assertIs<AiModelDiscoveryResult.Failed>(result).error.category,
            )
        }
    }

    @Test
    fun providerFailuresAreStructuredRedactedAndRedirectsAreNotFollowed(): Unit = runBlocking {
        val cases = listOf(
            HttpStatusCode.Unauthorized to AiProviderModelErrorCategory.AUTHENTICATION,
            HttpStatusCode.Forbidden to AiProviderModelErrorCategory.AUTHORIZATION,
            HttpStatusCode.TooManyRequests to AiProviderModelErrorCategory.RATE_LIMITED,
            HttpStatusCode.InternalServerError to AiProviderModelErrorCategory.PROVIDER_UNAVAILABLE,
            HttpStatusCode.BadRequest to AiProviderModelErrorCategory.MALFORMED_RESPONSE,
            HttpStatusCode.Found to AiProviderModelErrorCategory.MALFORMED_RESPONSE,
        )
        cases.forEach { (status, category) ->
            var calls = 0
            val engine = MockEngine {
                calls += 1
                respond(
                    """{"error":{"code":"secret-key","message":"Authorization: Bearer secret-key"}}""",
                    status = status,
                    headers = if (status == HttpStatusCode.Found) {
                        headersOf(HttpHeaders.Location, "https://attacker.example/models")
                    } else {
                        jsonHeaders()
                    },
                )
            }
            val failure = client(engine).use { it.discoverModels("secret-key") }
            val error = assertIs<AiModelDiscoveryResult.Failed>(failure).error
            assertEquals(category, error.category)
            assertFalse(error.toString().contains("secret-key"))
            assertFalse(error.toString().contains("Authorization"))
            assertEquals(1, calls)
        }
    }

    @Test
    fun transportFailureAndCancellationAreClassifiedWithoutLeakingSecrets(): Unit = runBlocking {
        val unavailable = client(MockEngine { throw IOException("secret-key") }).use { it.discoverModels("secret-key") }
        assertEquals(
            AiProviderModelErrorCategory.PROVIDER_UNAVAILABLE,
            assertIs<AiModelDiscoveryResult.Failed>(unavailable).error.category,
        )

        assertFailsWith<CancellationException> {
            client(MockEngine { throw CancellationException("cancelled") }).use { it.discoverModels("key") }
        }
    }

    @Test
    fun requestTimeoutMapsToStructuredTimeout(): Unit = runBlocking {
        val configuration = OpenAiModelProviderConfiguration(requestTimeoutMillis = 1)
        val engine = MockEngine {
            delay(50)
            respond("""{"data":[]}""", headers = jsonHeaders())
        }
        val result = OpenAiModelDiscoveryClient(
            configuration,
            createOpenAiModelHttpClient(configuration, engine),
        ).use { it.discoverModels("key") }

        assertEquals(
            AiProviderModelErrorCategory.TIMEOUT,
            assertIs<AiModelDiscoveryResult.Failed>(result).error.category,
        )
    }

    @Test
    fun verificationUsesModelMetadataBoundaryAndMapsModelAccessLoss(): Unit = runBlocking {
        var capturedMethod: HttpMethod? = null
        var capturedUrl: String? = null
        val accepted = MockEngine { request ->
            capturedMethod = request.method
            capturedUrl = request.url.toString()
            respond(
                """{"id":"future-tool-model","object":"model"}""",
                headers = jsonHeaders(),
            )
        }
        val result = client(accepted).use { it.verifyModel("secret-key", "future-tool-model") }

        assertIs<AiModelVerificationResult.Verified>(result)
        assertEquals(HttpMethod.Get, capturedMethod)
        assertEquals("${OpenAiModelProviderConfiguration.MODELS_ENDPOINT}/future-tool-model", capturedUrl)

        val missing = MockEngine {
            respond(
                """{"error":{"code":"model_not_found","param":"model","message":"secret-key"}}""",
                status = HttpStatusCode.NotFound,
                headers = jsonHeaders(),
            )
        }
        val failure = client(missing).use { it.verifyModel("secret-key", "future-tool-model") }
        assertEquals(
            AiProviderModelErrorCategory.MODEL_NOT_AVAILABLE,
            assertIs<AiModelVerificationResult.Failed>(failure).error.category,
        )
        assertFalse(failure.toString().contains("secret-key"))
    }

    @Test
    fun invalidOrMovingModelIdIsRejectedBeforeProviderCall(): Unit = runBlocking {
        var calls = 0
        val engine = MockEngine {
            calls += 1
            error("Provider must not be called")
        }
        client(engine).use { provider ->
            assertIs<AiModelVerificationResult.Failed>(provider.verifyModel("key", "gpt-latest"))
            assertIs<AiModelVerificationResult.Failed>(provider.verifyModel("key", "bad model"))
        }
        assertEquals(0, calls)
    }

    @Test
    fun deterministicProviderFixtureSupportsZeroMultipleUnknownAndFailingInventories(): Unit = runBlocking {
        val models = listOf(
            AiProviderModelDescriptor("openai", "unknown-tool-model"),
            AiProviderModelDescriptor("openai", "gpt-5.6-sol"),
        )
        val provider = DevelopmentAiModelProviderClient(
            models = models,
            verificationByModelId = mapOf("unknown-tool-model" to AiModelVerificationResult.Verified),
        )

        assertEquals(
            listOf("gpt-5.6-sol", "unknown-tool-model"),
            assertIs<AiModelDiscoveryResult.Discovered>(provider.discoverModels("ignored")).models.map { it.modelId },
        )
        assertIs<AiModelVerificationResult.Verified>(provider.verifyModel("ignored", "unknown-tool-model"))
        assertIs<AiModelVerificationResult.Failed>(provider.verifyModel("ignored", "missing"))
        assertTrue(assertIs<AiModelDiscoveryResult.Discovered>(DevelopmentAiModelProviderClient().discoverModels("ignored")).models.isEmpty())

        val failed = DevelopmentAiModelProviderClient(
            discoveryFailure = AiProviderModelError(AiProviderModelErrorCategory.TIMEOUT, retryable = true),
        ).discoverModels("ignored")
        assertEquals(AiProviderModelErrorCategory.TIMEOUT, assertIs<AiModelDiscoveryResult.Failed>(failed).error.category)
    }

    private fun client(engine: MockEngine): OpenAiModelDiscoveryClient {
        val configuration = OpenAiModelProviderConfiguration()
        return OpenAiModelDiscoveryClient(configuration, createOpenAiModelHttpClient(configuration, engine))
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
}
