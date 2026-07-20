package com.entio.web

import com.entio.web.ai.DevelopmentAiProviderClient
import com.entio.web.ai.models.AiProviderModelDescriptor
import com.entio.web.ai.models.AiModelCompatibilityPolicy
import com.entio.web.ai.models.AiModelDiscoveryService
import com.entio.web.ai.models.AiModelSelectionService
import com.entio.web.ai.models.AiModelVerificationService
import com.entio.web.ai.models.AiProviderCallLimiter
import com.entio.web.ai.models.AiModelCompatibilityState
import com.entio.web.ai.models.AiModelDiscoveryStatus
import com.entio.web.ai.models.AiModelSelectionStatus
import com.entio.web.ai.models.AiModelVerificationStatus
import com.entio.web.ai.models.AiSettingsCredentialStatus
import com.entio.web.ai.models.AiUserProviderSettings
import com.entio.web.ai.models.InMemoryAiUserProviderSettingsStore
import com.entio.web.ai.models.AiProviderSettingsService
import com.entio.web.ai.InMemoryAiCredentialStore
import com.entio.web.ai.AiModelWebBoundary
import com.entio.web.ai.provider.AiModelVerificationResult
import com.entio.web.ai.provider.AiProviderModelError
import com.entio.web.ai.provider.AiProviderModelErrorCategory
import com.entio.web.ai.provider.DevelopmentAiModelProviderClient
import com.entio.web.contract.WebApplicationDependencies
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AiModelWebContractTest {
    @Test
    fun staleDiscoveredModelsAreNotReportedAsReady(): Unit {
        val now = Instant.parse("2026-07-20T00:00:00Z")
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val credentials = InMemoryAiCredentialStore().also { it.save("alice", "openai", "test-secret") }
        val store = InMemoryAiUserProviderSettingsStore()
        val provider = DevelopmentAiModelProviderClient(
            models = listOf(AiProviderModelDescriptor("openai", "gpt-5.6-sol")),
            verificationByModelId = mapOf("gpt-5.6-sol" to AiModelVerificationResult.Verified),
        )
        val policy = AiModelCompatibilityPolicy()
        val candidate = policy.project(listOf(AiProviderModelDescriptor("openai", "gpt-5.6-sol"))).candidates.single()
        store.save(
            AiUserProviderSettings(
                userId = "alice",
                providerId = "openai",
                credentialGeneration = 1,
                credentialStatus = AiSettingsCredentialStatus.VALID,
                discoveryStatus = AiModelDiscoveryStatus.COMPLETED,
                discoveredAt = now.minusSeconds(20 * 60),
                policyVersion = policy.version,
                candidates = listOf(candidate.copy(verificationStatus = AiModelVerificationStatus.VERIFIED, compatibilityState = AiModelCompatibilityState.AVAILABLE_AND_COMPATIBLE)),
                unsupportedProviderModelCount = 0,
                selectedModelId = "gpt-5.6-sol",
                selectedModelVerifiedAt = now.minusSeconds(20 * 60),
                selectionStatus = AiModelSelectionStatus.READY,
                lastProviderErrorCategory = null,
            ),
        )
        val discovery = AiModelDiscoveryService(credentials, store, provider, policy, AiProviderCallLimiter(clock), clock)
        val settings = AiProviderSettingsService(credentials, store, discovery, "openai", policy.version)
        val verification = AiModelVerificationService(credentials, store, provider, AiProviderCallLimiter(clock), clock)
        val boundary = AiModelWebBoundary(settings, discovery, AiModelSelectionService(store, discovery, verification))

        val response = boundary.status("alice")

        assertEquals("STALE", response.discoveryStatus)
        assertEquals("UNAVAILABLE", response.selectionStatus)
        assertEquals("gpt-5.6-sol", response.selectedModel?.modelId)
    }

    @Test
    fun credentialDiscoverySelectionRetestClearAndRemovalUseRedactedVersionedContracts(): Unit = testApplication {
        application { module(modelDependencies()) }

        val initial = client.get("/api/v1/ai/provider-settings")
        assertEquals(HttpStatusCode.OK, initial.status)
        assertContains(initial.bodyAsText(), "NOT_CONFIGURED")

        val secret = "openai-test-secret"
        val saved = client.put("/api/v1/ai/credentials") {
            contentType(ContentType.Application.Json)
            setBody("""{"providerId":"openai","apiKey":"$secret"}""")
        }
        val savedBody = saved.bodyAsText()
        assertEquals(HttpStatusCode.OK, saved.status)
        assertContains(savedBody, "gpt-5.6-sol")
        assertContains(savedBody, "future-tool-model")
        assertContains(savedBody, "NOT_SELECTED")
        assertFalse(savedBody.contains(secret))
        assertFalse(savedBody.contains("owned_by"))
        assertFalse(savedBody.contains("raw-provider"))

        val selected = client.put("/api/v1/ai/model-selection") {
            header("Idempotency-Key", "selection-1")
            contentType(ContentType.Application.Json)
            setBody("""{"modelId":"future-tool-model"}""")
        }
        assertEquals(HttpStatusCode.OK, selected.status)
        assertContains(selected.bodyAsText(), "READY")
        assertContains(selected.bodyAsText(), "future-tool-model")

        val replay = client.put("/api/v1/ai/model-selection") {
            header("Idempotency-Key", "selection-1")
            contentType(ContentType.Application.Json)
            setBody("""{"modelId":"future-tool-model"}""")
        }
        assertEquals(selected.bodyAsText(), replay.bodyAsText())

        val retested = client.post("/api/v1/ai/model-selection/test") {
            header("Idempotency-Key", "retest-1")
        }
        assertEquals(HttpStatusCode.OK, retested.status)
        assertContains(retested.bodyAsText(), "READY")

        val cleared = client.delete("/api/v1/ai/model-selection")
        assertEquals(HttpStatusCode.OK, cleared.status)
        assertContains(cleared.bodyAsText(), "NOT_SELECTED")

        val removed = client.delete("/api/v1/ai/credentials")
        assertEquals(HttpStatusCode.OK, removed.status)
        assertContains(removed.bodyAsText(), "NOT_CONFIGURED")
        assertContains(client.get("/api/v1/ai/credential-status").bodyAsText(), "NOT_CONFIGURED")
    }

    @Test
    fun identityIsolationArbitraryIdsAndIdempotencyAreEnforced(): Unit = testApplication {
        application { module(modelDependencies()) }
        client.put("/api/v1/ai/credentials") {
            contentType(ContentType.Application.Json)
            setBody("""{"providerId":"openai","apiKey":"openai-test-secret"}""")
        }

        val bob = client.get("/api/v1/ai/provider-settings") { header("X-Entio-User", "bob") }
        assertContains(bob.bodyAsText(), "NOT_CONFIGURED")

        val bobDiscovery = client.post("/api/v1/ai/models/discover") { header("X-Entio-User", "bob") }
        assertEquals(HttpStatusCode.BadRequest, bobDiscovery.status)
        assertContains(bobDiscovery.bodyAsText(), "AI_CREDENTIAL_MISSING")
        val bobSelection = client.put("/api/v1/ai/model-selection") {
            header("X-Entio-User", "bob")
            header("Idempotency-Key", "bob-selection")
            contentType(ContentType.Application.Json)
            setBody("""{"modelId":"gpt-5.6-sol"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, bobSelection.status)
        assertContains(bobSelection.bodyAsText(), "AI_CREDENTIAL_MISSING")
        assertContains(client.get("/api/v1/ai/provider-settings").bodyAsText(), "gpt-5.6-sol")

        val missingKey = client.put("/api/v1/ai/model-selection") {
            contentType(ContentType.Application.Json)
            setBody("""{"modelId":"gpt-5.6-sol"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, missingKey.status)
        assertContains(missingKey.bodyAsText(), "missing-idempotency-key")

        val arbitrary = client.put("/api/v1/ai/model-selection") {
            header("Idempotency-Key", "selection-2")
            contentType(ContentType.Application.Json)
            setBody("""{"modelId":"client-invented"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, arbitrary.status)
        assertContains(arbitrary.bodyAsText(), "AI_MODEL_NOT_AVAILABLE")
    }

    @Test
    fun noCompatibleModelsAndProviderFailureDoNotBreakNonAiRoutes(): Unit = testApplication {
        val provider = DevelopmentAiModelProviderClient(
            models = listOf(AiProviderModelDescriptor("openai", "text-embedding-3-large")),
        )
        application { module(modelDependencies(provider)) }

        val saved = client.put("/api/v1/ai/credentials") {
            contentType(ContentType.Application.Json)
            setBody("""{"providerId":"openai","apiKey":"key"}""")
        }
        assertEquals(HttpStatusCode.OK, saved.status)
        assertContains(saved.bodyAsText(), "NO_COMPATIBLE_MODELS")
        assertContains(saved.bodyAsText(), "VALID")
        assertEquals(HttpStatusCode.OK, client.get("/health").status)

        val failing = DevelopmentAiModelProviderClient(
            discoveryFailure = AiProviderModelError(AiProviderModelErrorCategory.PROVIDER_UNAVAILABLE, retryable = true),
        )
        testApplication {
            application { module(modelDependencies(failing)) }
            val response = client.put("/api/v1/ai/credentials") {
                contentType(ContentType.Application.Json)
                setBody("""{"providerId":"openai","apiKey":"key"}""")
            }
            assertContains(response.bodyAsText(), "PROVIDER_UNAVAILABLE")
            assertEquals(HttpStatusCode.OK, client.get("/health").status)
        }
    }

    @Test
    fun legacyCredentialRoutesRemainAvailable(): Unit = testApplication {
        application {
            module(
                WebApplicationDependencies(
                    aiProvider = DevelopmentAiProviderClient(),
                    aiModelProvider = DevelopmentAiModelProviderClient(),
                ),
            )
        }
        val saved = client.put("/api/v1/ai/credentials") {
            contentType(ContentType.Application.Json)
            setBody("""{"providerId":"provider-neutral","apiKey":"legacy-key"}""")
        }
        assertContains(saved.bodyAsText(), "NOT_TESTED")
        assertContains(client.post("/api/v1/ai/credentials/test").bodyAsText(), "PASSED")
        assertContains(client.get("/api/v1/ai/credential-status").bodyAsText(), "configured")
    }

    @Test
    fun logoutClearsCredentialDiscoveryAndSelectionTogether(): Unit = testApplication {
        application { module(modelDependencies()) }
        client.put("/api/v1/ai/credentials") {
            contentType(ContentType.Application.Json)
            setBody("""{"providerId":"openai","apiKey":"logout-secret"}""")
        }
        client.put("/api/v1/ai/model-selection") {
            header("Idempotency-Key", "logout-selection")
            contentType(ContentType.Application.Json)
            setBody("""{"modelId":"gpt-5.6-sol"}""")
        }

        assertContains(client.get("/api/v1/ai/provider-settings").bodyAsText(), "READY")
        client.post("/api/v1/session/logout")
        val status = client.get("/api/v1/ai/provider-settings").bodyAsText()
        assertContains(status, "NOT_CONFIGURED")
        assertFalse(status.contains("logout-secret"))
        assertFalse(status.contains("gpt-5.6-sol"))
    }

    private fun modelDependencies(
        modelProvider: DevelopmentAiModelProviderClient = DevelopmentAiModelProviderClient(
            models = listOf(
                AiProviderModelDescriptor("openai", "gpt-5.6-sol"),
                AiProviderModelDescriptor("openai", "future-tool-model"),
            ),
            verificationByModelId = mapOf(
                "gpt-5.6-sol" to AiModelVerificationResult.Verified,
                "future-tool-model" to AiModelVerificationResult.Verified,
            ),
        ),
    ): WebApplicationDependencies = WebApplicationDependencies(
        aiProvider = DevelopmentAiProviderClient(),
        aiModelProvider = modelProvider,
    )
}
