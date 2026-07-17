package com.entio.web.ai

import com.entio.web.contract.WebPermission
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenAiResponsesClientTest {
    private val mapper: ObjectMapper = ObjectMapper()

    @Test
    fun requestUsesApprovedModelStoragePolicyTrustedInputAndStrictCustomFunctions(): Unit = runBlocking {
        var capturedBody: String? = null
        var capturedAuthorization: String? = null
        val engine = MockEngine { request ->
            capturedBody = (request.body as TextContent).text
            capturedAuthorization = request.headers[HttpHeaders.Authorization]
            respond(completedStream("resp-1", "Ready"), headers = eventStreamHeaders())
        }
        client(engine).use { provider ->
            val result = provider.respond("secret-key", request())

            assertIs<OpenAiResponsesResult.Completed>(result)
        }

        val body = mapper.readTree(capturedBody)
        assertEquals("gpt-5.2", body.path("model").asText())
        assertFalse(body.path("store").asBoolean(true))
        assertTrue(body.path("stream").asBoolean())
        assertFalse(body.path("parallel_tool_calls").asBoolean(true))
        assertEquals(listOf("developer", "user"), body.path("input").map { it.path("role").asText() })
        assertEquals("phase-7-v1", body.path("metadata").path("prompt_version").asText())
        assertEquals("Bearer secret-key", capturedAuthorization)
        assertFalse(capturedBody.orEmpty().contains("secret-key"))
        val tools = body.path("tools")
        assertTrue(tools.isArray && tools.size() == 4)
        tools.forEach { tool ->
            assertEquals("function", tool.path("type").asText())
            assertTrue(tool.path("strict").asBoolean())
            assertFalse(tool.path("parameters").path("additionalProperties").asBoolean(true))
            val propertyNames = tool.path("parameters").path("properties").fieldNames().asSequence().toSet()
            val required = tool.path("parameters").path("required").map { it.asText() }.toSet()
            assertEquals(propertyNames, required)
        }
        assertEquals(
            "^https?://[^\\s]+$",
            tools.first { it.path("name").asText() == "entio_entity_detail" }
                .path("parameters").path("properties").path("entityIri").path("pattern").asText(),
        )
    }

    @Test
    fun configurationRejectsAliasesUnapprovedEndpointsAndStoredResponses(): Unit {
        assertFailsWith<IllegalArgumentException> { configuration(modelId = "gpt-5.2-latest") }
        assertFailsWith<IllegalArgumentException> { configuration(endpoint = "https://example.com/v1/responses") }
        assertFailsWith<IllegalArgumentException> { configuration(store = true) }
        assertFailsWith<IllegalArgumentException> { configuration(modelId = "gpt-5.6") }
    }

    @Test
    fun streamingParsesTextFunctionCallsCompletionAndUsageWithoutExecutingTool(): Unit = runBlocking {
        val stream = sequenceOf(
            event("""{"type":"response.output_text.delta","delta":"Hello "}"""),
            event("""{"type":"response.function_call_arguments.delta","item_id":"item-1","delta":"{\"query\":"}"""),
            event("""{"type":"response.output_item.done","item":{"type":"function_call","call_id":"call-1","name":"unknown_tool","arguments":"{\"query\":\"x\"}"}}"""),
            event("""{"type":"response.output_text.delta","delta":"world"}"""),
            event("""{"type":"response.completed","response":{"id":"resp-2","usage":{"input_tokens":11,"output_tokens":7,"total_tokens":18}}}"""),
        ).joinToString("")
        val observed = mutableListOf<OpenAiProviderEvent>()
        val engine = MockEngine { respond(stream, headers = eventStreamHeaders()) }

        val result = client(engine).use { it.respond("key", request(), observed::add) }

        val completed = assertIs<OpenAiResponsesResult.Completed>(result).response
        assertEquals("Hello world", completed.text)
        assertEquals(OpenAiFunctionCall("call-1", "unknown_tool", "{\"query\":\"x\"}"), completed.functionCalls.single())
        assertEquals(OpenAiUsage(11, 7, 18), completed.usage)
        assertEquals("resp-2", completed.responseId)
        assertTrue(observed.any { it is OpenAiProviderEvent.FunctionArgumentsDelta })
    }

    @Test
    fun httpFailuresAreClassifiedRetriedAndRedacted(): Unit = runBlocking {
        var attempts = 0
        val rateLimitEngine = MockEngine {
            attempts += 1
            respond("secret-key and Authorization: Bearer secret-key", status = HttpStatusCode.TooManyRequests)
        }
        val rateLimited = client(rateLimitEngine).use { it.respond("secret-key", request()) }

        val rateFailure = assertIs<OpenAiResponsesResult.Failed>(rateLimited).failure
        assertEquals(OpenAiFailureCode.RATE_LIMIT, rateFailure.code)
        assertEquals(3, attempts)
        assertFalse(rateFailure.message.contains("secret-key"))
        assertFalse(rateFailure.message.contains("Authorization"))

        attempts = 0
        val invalidKeyEngine = MockEngine {
            attempts += 1
            respond("credential=secret-key", status = HttpStatusCode.Unauthorized)
        }
        val invalidKey = client(invalidKeyEngine).use { it.respond("secret-key", request()) }
        assertEquals(OpenAiFailureCode.INVALID_CREDENTIAL, assertIs<OpenAiResponsesResult.Failed>(invalidKey).failure.code)
        assertEquals(1, attempts)
    }

    @Test
    fun timeoutMalformedRefusalIncompleteAndProviderErrorsMapToStructuredFailures(): Unit = runBlocking {
        assertFailure(OpenAiFailureCode.TIMEOUT, HttpStatusCode.RequestTimeout, "timeout")
        assertStreamFailure(OpenAiFailureCode.MALFORMED_RESPONSE, event("not-json"))
        assertStreamFailure(OpenAiFailureCode.REFUSAL, event("""{"type":"response.refusal.done","refusal":"no"}"""))
        assertStreamFailure(
            OpenAiFailureCode.INCOMPLETE,
            event("""{"type":"response.incomplete","response":{"incomplete_details":{"reason":"max_output_tokens"}}}"""),
        )
        assertStreamFailure(OpenAiFailureCode.CANCELLED, event("""{"type":"response.cancelled"}"""))
        assertStreamFailure(OpenAiFailureCode.PROVIDER_ERROR, event("""{"type":"error","error":{"code":"internal"}}"""))
        assertStreamFailure(OpenAiFailureCode.MALFORMED_RESPONSE, event("""{"type":"response.output_text.delta","delta":"unterminated"}"""))
    }

    @Test
    fun callerCancellationCancelsInFlightProviderRequest(): Unit = runBlocking {
        val engine = MockEngine {
            delay(Long.MAX_VALUE)
            respond(completedStream("unused", "unused"), headers = eventStreamHeaders())
        }
        client(engine).use { provider ->
            val requestJob = async { provider.respond("key", request()) }
            yield()
            requestJob.cancelAndJoin()
            assertTrue(requestJob.isCancelled)
        }
    }

    @Test
    fun safeCredentialTestUsesProviderBoundaryAndNeverReturnsCredential(): Unit = runBlocking {
        val accepted = MockEngine { respond(completedStream("resp-test", "OK"), headers = eventStreamHeaders()) }
        val result = client(accepted).use { it.test("secret-key") }

        val passed = assertIs<AiProviderTestResult.Passed>(result)
        assertFalse(passed.message.contains("secret-key"))

        val rejected = MockEngine { respond("secret-key", status = HttpStatusCode.Unauthorized) }
        val failure = client(rejected).use { it.test("secret-key") }
        assertFalse(assertIs<AiProviderTestResult.Failed>(failure).message.contains("secret-key"))
    }

    private suspend fun assertFailure(code: OpenAiFailureCode, status: HttpStatusCode, body: String) {
        val engine = MockEngine { respond(body, status = status) }
        val result = client(engine).use { it.respond("key", request()) }
        assertEquals(code, assertIs<OpenAiResponsesResult.Failed>(result).failure.code)
    }

    private suspend fun assertStreamFailure(code: OpenAiFailureCode, stream: String) {
        val engine = MockEngine { respond(stream, headers = eventStreamHeaders()) }
        val result = client(engine).use { it.respond("key", request()) }
        val failure = assertIs<OpenAiResponsesResult.Failed>(result).failure
        assertEquals(code, failure.code)
        assertNull(result.failure.message.takeIf { it.contains("secret-key") })
    }

    private fun request(): OpenAiResponsesRequest {
        val scope = AiCapabilityScope(
            userId = "alice",
            projectId = "simple",
            conversationId = "conversation-1",
            allowedSourceIds = listOf("simple"),
            baselineFingerprint = "baseline",
            role = "CONTRIBUTOR",
            permissions = setOf(WebPermission.BROWSE.name, WebPermission.USE_AI.name),
            availableFeatures = setOf(AiCapabilityFeatures.LOCAL_SEMANTIC_READ, AiCapabilityFeatures.ENTIO_HELP),
            createdAt = Instant.parse("2026-07-17T12:00:00Z"),
        )
        return OpenAiResponsesRequest(
            trustedPolicy = "Trusted Entio policy.",
            userInput = "Explain the project.",
            capabilities = AiCapabilityRegistry().snapshot(scope),
        )
    }

    private fun client(engine: MockEngine): OpenAiResponsesClient {
        val configuration = configuration()
        return OpenAiResponsesClient(configuration, createOpenAiHttpClient(configuration, engine))
    }

    private fun configuration(
        modelId: String = OpenAiProviderConfiguration.MODEL_ID,
        endpoint: String = OpenAiProviderConfiguration.ENDPOINT,
        store: Boolean = false,
    ): OpenAiProviderConfiguration = OpenAiProviderConfiguration(
        modelId = modelId,
        promptVersion = "phase-7-v1",
        endpoint = endpoint,
        store = store,
        requestTimeoutMillis = 1_000,
    )

    private fun event(json: String): String = "data: $json\n\n"

    private fun completedStream(responseId: String, text: String): String = sequenceOf(
        event("""{"type":"response.output_text.delta","delta":${mapper.writeValueAsString(text)}}"""),
        event("""{"type":"response.completed","response":{"id":"$responseId","usage":{"input_tokens":1,"output_tokens":1,"total_tokens":2}}}"""),
    ).joinToString("")

    private fun eventStreamHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
}
