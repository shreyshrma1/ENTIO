package com.entio.web.ai.models

import com.entio.web.ai.InMemoryAiCredentialStore
import com.entio.web.ai.provider.AiModelDiscoveryResult
import com.entio.web.ai.provider.AiModelProviderClient
import com.entio.web.ai.provider.AiModelVerificationResult
import com.entio.web.ai.provider.AiProviderModelError
import com.entio.web.ai.provider.AiProviderModelErrorCategory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

internal class MutableAiClock(
    private var current: Instant = Instant.parse("2026-07-19T12:00:00Z"),
) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = current

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }
}

internal class RecordingAiModelProvider : AiModelProviderClient {
    override val providerId: String = "openai"
    val inventories: MutableMap<String, AiModelDiscoveryResult> = linkedMapOf()
    val verifications: MutableMap<Pair<String, String>, AiModelVerificationResult> = linkedMapOf()
    val discoveryKeys: MutableList<String> = mutableListOf()
    val verificationRequests: MutableList<Pair<String, String>> = mutableListOf()

    override suspend fun discoverModels(apiKey: String): AiModelDiscoveryResult {
        discoveryKeys += apiKey
        return inventories[apiKey] ?: AiModelDiscoveryResult.Discovered(emptyList())
    }

    override suspend fun verifyModel(apiKey: String, modelId: String): AiModelVerificationResult {
        verificationRequests += apiKey to modelId
        return verifications[apiKey to modelId] ?: AiModelVerificationResult.Verified
    }

    fun inventory(apiKey: String, vararg modelIds: String) {
        inventories[apiKey] = AiModelDiscoveryResult.Discovered(
            modelIds.map { AiProviderModelDescriptor(providerId, it) },
        )
    }

    fun failDiscovery(apiKey: String, category: AiProviderModelErrorCategory) {
        inventories[apiKey] = AiModelDiscoveryResult.Failed(AiProviderModelError(category, retryable = false))
    }
}

internal data class AiModelServiceFixture(
    val clock: MutableAiClock,
    val credentials: InMemoryAiCredentialStore,
    val store: InMemoryAiUserProviderSettingsStore,
    val provider: RecordingAiModelProvider,
    val discovery: AiModelDiscoveryService,
    val credentialSettings: AiProviderSettingsService,
    val verification: AiModelVerificationService,
    val selection: AiModelSelectionService,
)

internal fun aiModelServiceFixture(
    discoveryLimit: Int = 5,
    verificationLimit: Int = 3,
): AiModelServiceFixture {
    val clock = MutableAiClock()
    val credentials = InMemoryAiCredentialStore()
    val store = InMemoryAiUserProviderSettingsStore()
    val provider = RecordingAiModelProvider()
    val policy = AiModelCompatibilityPolicy()
    val limiter = AiProviderCallLimiter(clock, discoveryLimit = discoveryLimit, verificationLimit = verificationLimit)
    val discovery = AiModelDiscoveryService(credentials, store, provider, policy, limiter, clock)
    val credentialSettings = AiProviderSettingsService(
        credentials,
        store,
        discovery,
        provider.providerId,
        policy.version,
    )
    val verification = AiModelVerificationService(credentials, store, provider, limiter, clock)
    return AiModelServiceFixture(
        clock,
        credentials,
        store,
        provider,
        discovery,
        credentialSettings,
        verification,
        AiModelSelectionService(store, discovery, verification),
    )
}
