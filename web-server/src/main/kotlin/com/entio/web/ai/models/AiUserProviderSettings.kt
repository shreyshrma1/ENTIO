package com.entio.web.ai.models

import com.entio.web.ai.AiCredentialStore
import com.entio.web.ai.provider.AiModelDiscoveryResult
import com.entio.web.ai.provider.AiModelProviderClient
import com.entio.web.ai.provider.AiModelVerificationResult
import com.entio.web.ai.provider.AiProviderModelErrorCategory
import com.entio.web.ai.AiConversationFailure
import com.entio.web.ai.AiRunModelBinding
import com.entio.web.ai.AiRunModelBindingResolver
import java.time.Clock
import java.time.Duration
import java.time.Instant

public enum class AiSettingsCredentialStatus {
    NOT_CONFIGURED,
    VALID,
    INVALID,
}

public enum class AiModelDiscoveryStatus {
    NOT_STARTED,
    COMPLETED,
    NO_COMPATIBLE_MODELS,
    FAILED,
    STALE,
}

public enum class AiModelSelectionStatus {
    NOT_SELECTED,
    READY,
    UNAVAILABLE,
    INCOMPATIBLE,
    VERIFICATION_FAILED,
}

public data class AiUserProviderSettings(
    val userId: String,
    val providerId: String?,
    val credentialGeneration: Long,
    val credentialStatus: AiSettingsCredentialStatus,
    val discoveryStatus: AiModelDiscoveryStatus,
    val discoveredAt: Instant?,
    val policyVersion: String,
    val candidates: List<AiSelectableModelDescriptor>,
    val unsupportedProviderModelCount: Int,
    val selectedModelId: String?,
    val selectedModelVerifiedAt: Instant?,
    val selectionStatus: AiModelSelectionStatus,
    val lastProviderErrorCategory: AiProviderModelErrorCategory?,
) {
    public companion object {
        public fun notConfigured(userId: String, policyVersion: String): AiUserProviderSettings = AiUserProviderSettings(
            userId = userId,
            providerId = null,
            credentialGeneration = 0,
            credentialStatus = AiSettingsCredentialStatus.NOT_CONFIGURED,
            discoveryStatus = AiModelDiscoveryStatus.NOT_STARTED,
            discoveredAt = null,
            policyVersion = policyVersion,
            candidates = emptyList(),
            unsupportedProviderModelCount = 0,
            selectedModelId = null,
            selectedModelVerifiedAt = null,
            selectionStatus = AiModelSelectionStatus.NOT_SELECTED,
            lastProviderErrorCategory = null,
        )
    }
}

public interface AiUserProviderSettingsStore {
    public fun find(userId: String): AiUserProviderSettings?

    public fun save(settings: AiUserProviderSettings)

    public fun remove(userId: String)

    public fun clearAll()
}

public class InMemoryAiUserProviderSettingsStore : AiUserProviderSettingsStore {
    private val settings: MutableMap<String, AiUserProviderSettings> = linkedMapOf()

    @Synchronized
    override fun find(userId: String): AiUserProviderSettings? = settings[userId]

    @Synchronized
    override fun save(settings: AiUserProviderSettings) {
        this.settings[settings.userId] = settings
    }

    @Synchronized
    override fun remove(userId: String) {
        settings.remove(userId)
    }

    @Synchronized
    override fun clearAll() {
        settings.clear()
    }
}

public enum class AiProviderCallKind {
    DISCOVERY,
    VERIFICATION,
}

/** In-memory local protection only; provider quotas remain authoritative. */
public class AiProviderCallLimiter(
    private val clock: Clock,
    private val window: Duration = Duration.ofMinutes(15),
    private val discoveryLimit: Int = 5,
    private val verificationLimit: Int = 3,
) {
    private data class ActiveCall(val userId: String, val generation: Long, val kind: AiProviderCallKind)

    private val calls: MutableMap<Pair<String, AiProviderCallKind>, MutableList<Instant>> = linkedMapOf()
    private val active: MutableSet<ActiveCall> = linkedSetOf()

    init {
        require(!window.isNegative && !window.isZero)
        require(discoveryLimit > 0)
        require(verificationLimit > 0)
    }

    @Synchronized
    public fun begin(userId: String, generation: Long, kind: AiProviderCallKind) {
        val activeCall = ActiveCall(userId, generation, kind)
        if (!active.add(activeCall)) throw AiModelSettingsFailure("ai-provider-call-in-progress")
        val now = clock.instant()
        val history = calls.getOrPut(userId to kind, ::mutableListOf)
        history.removeIf { Duration.between(it, now) >= window }
        val limit = if (kind == AiProviderCallKind.DISCOVERY) discoveryLimit else verificationLimit
        if (history.size >= limit) {
            active.remove(activeCall)
            throw AiModelSettingsFailure("ai-provider-local-rate-limited")
        }
        history += now
    }

    @Synchronized
    public fun end(userId: String, generation: Long, kind: AiProviderCallKind) {
        active.remove(ActiveCall(userId, generation, kind))
    }
}

public class AiModelDiscoveryService(
    private val credentials: AiCredentialStore,
    private val settingsStore: AiUserProviderSettingsStore,
    private val provider: AiModelProviderClient,
    private val policy: AiModelCompatibilityPolicy,
    private val limiter: AiProviderCallLimiter,
    private val clock: Clock,
    private val freshness: Duration = Duration.ofMinutes(15),
) {
    init {
        require(!freshness.isNegative && !freshness.isZero)
    }

    public fun settings(userId: String): AiUserProviderSettings = settingsStore.find(userId)
        ?: AiUserProviderSettings.notConfigured(userId, policy.version)

    public fun isFresh(settings: AiUserProviderSettings): Boolean = settings.discoveredAt?.let {
        Duration.between(it, clock.instant()) < freshness
    } == true

    public suspend fun discover(userId: String, force: Boolean = false): AiUserProviderSettings {
        val current = settings(userId)
        if (current.credentialStatus == AiSettingsCredentialStatus.NOT_CONFIGURED) {
            throw AiModelSettingsFailure("ai-credential-missing")
        }
        if (!force && isFresh(current) && current.discoveryStatus in completedDiscoveryStatuses) return current

        limiter.begin(userId, current.credentialGeneration, AiProviderCallKind.DISCOVERY)
        return try {
            val result = credentials.withCredentialSuspending(userId) { providerId, apiKey ->
                if (providerId != provider.providerId || providerId != current.providerId) {
                    AiModelDiscoveryResult.Failed(
                        com.entio.web.ai.provider.AiProviderModelError(
                            AiProviderModelErrorCategory.AUTHORIZATION,
                            retryable = false,
                        ),
                    )
                } else {
                    provider.discoverModels(apiKey)
                }
            } ?: throw AiModelSettingsFailure("ai-credential-missing")
            val updated = when (result) {
                is AiModelDiscoveryResult.Discovered -> discovered(current, result)
                is AiModelDiscoveryResult.Failed -> discoveryFailed(current, result.error.category)
            }
            settingsStore.save(updated)
            updated
        } finally {
            limiter.end(userId, current.credentialGeneration, AiProviderCallKind.DISCOVERY)
        }
    }

    private fun discovered(
        current: AiUserProviderSettings,
        result: AiModelDiscoveryResult.Discovered,
    ): AiUserProviderSettings {
        val verified = current.selectedModelId
            ?.takeIf { current.selectionStatus == AiModelSelectionStatus.READY }
            ?.let { mapOf(it to AiModelVerificationStatus.VERIFIED) }
            .orEmpty()
        val projection = policy.project(result.models, verified)
        val selectedStillAvailable = current.selectedModelId != null && projection.candidates.any {
            it.modelId == current.selectedModelId
        }
        return current.copy(
            credentialStatus = AiSettingsCredentialStatus.VALID,
            discoveryStatus = if (projection.candidates.isEmpty()) {
                AiModelDiscoveryStatus.NO_COMPATIBLE_MODELS
            } else {
                AiModelDiscoveryStatus.COMPLETED
            },
            discoveredAt = clock.instant(),
            policyVersion = projection.policyVersion,
            candidates = projection.candidates,
            unsupportedProviderModelCount = projection.unsupportedProviderModelCount,
            selectionStatus = when {
                current.selectedModelId == null -> AiModelSelectionStatus.NOT_SELECTED
                selectedStillAvailable -> current.selectionStatus
                else -> AiModelSelectionStatus.UNAVAILABLE
            },
            selectedModelVerifiedAt = current.selectedModelVerifiedAt.takeIf { selectedStillAvailable },
            lastProviderErrorCategory = null,
        )
    }

    private fun discoveryFailed(
        current: AiUserProviderSettings,
        category: AiProviderModelErrorCategory,
    ): AiUserProviderSettings = current.copy(
        credentialStatus = if (category == AiProviderModelErrorCategory.AUTHENTICATION) {
            AiSettingsCredentialStatus.INVALID
        } else {
            current.credentialStatus
        },
        discoveryStatus = AiModelDiscoveryStatus.FAILED,
        selectedModelId = current.selectedModelId.takeUnless { category == AiProviderModelErrorCategory.AUTHENTICATION },
        selectedModelVerifiedAt = current.selectedModelVerifiedAt.takeUnless { category == AiProviderModelErrorCategory.AUTHENTICATION },
        selectionStatus = if (category == AiProviderModelErrorCategory.AUTHENTICATION) {
            AiModelSelectionStatus.NOT_SELECTED
        } else {
            current.selectionStatus
        },
        lastProviderErrorCategory = category,
    )

    private companion object {
        private val completedDiscoveryStatuses: Set<AiModelDiscoveryStatus> = setOf(
            AiModelDiscoveryStatus.COMPLETED,
            AiModelDiscoveryStatus.NO_COMPATIBLE_MODELS,
        )
    }
}

public class AiProviderSettingsService(
    private val credentials: AiCredentialStore,
    private val settingsStore: AiUserProviderSettingsStore,
    private val discovery: AiModelDiscoveryService,
    private val expectedProviderId: String,
    private val policyVersion: String,
) {
    public fun settings(userId: String): AiUserProviderSettings = settingsStore.find(userId)
        ?: AiUserProviderSettings.notConfigured(userId, policyVersion)

    public suspend fun saveCredential(userId: String, providerId: String, apiKey: String): AiUserProviderSettings {
        if (providerId != expectedProviderId) throw AiModelSettingsFailure("ai-provider-not-supported")
        val normalized = apiKey.trim()
        if (normalized.isEmpty()) throw AiModelSettingsFailure("ai-credential-missing")
        val generation = (settingsStore.find(userId)?.credentialGeneration ?: 0) + 1
        credentials.save(userId, providerId, normalized)
        settingsStore.save(
            AiUserProviderSettings.notConfigured(userId, policyVersion).copy(
                providerId = providerId,
                credentialGeneration = generation,
                credentialStatus = AiSettingsCredentialStatus.VALID,
            ),
        )
        return discovery.discover(userId, force = true)
    }

    public fun removeCredential(userId: String): AiUserProviderSettings {
        credentials.remove(userId)
        settingsStore.remove(userId)
        return AiUserProviderSettings.notConfigured(userId, policyVersion)
    }

    public fun logout(userId: String) {
        credentials.remove(userId)
        settingsStore.remove(userId)
    }

    public fun clearAll() {
        credentials.clearAll()
        settingsStore.clearAll()
    }
}

public class AiModelVerificationService(
    private val credentials: AiCredentialStore,
    private val settingsStore: AiUserProviderSettingsStore,
    private val provider: AiModelProviderClient,
    private val limiter: AiProviderCallLimiter,
    private val clock: Clock,
) {
    private data class IdempotencyEntry(
        val modelId: String,
        var complete: Boolean = false,
        var result: AiUserProviderSettings? = null,
    )

    private val idempotency: MutableMap<Triple<String, Long, String>, IdempotencyEntry> = linkedMapOf()

    public suspend fun verify(userId: String, modelId: String, idempotencyKey: String): AiUserProviderSettings {
        val current = settingsStore.find(userId) ?: throw AiModelSettingsFailure("ai-credential-missing")
        val key = idempotencyKey.trim()
        if (key.isEmpty() || key.length > 200) throw AiModelSettingsFailure("ai-idempotency-key-invalid")
        val scopedKey = Triple(userId, current.credentialGeneration, key)
        synchronized(this) {
            val existing = idempotency[scopedKey]
            if (existing != null) {
                if (existing.modelId != modelId) throw AiModelSettingsFailure("ai-idempotency-conflict")
                return existing.result ?: throw AiModelSettingsFailure("ai-verification-in-progress")
            }
            idempotency[scopedKey] = IdempotencyEntry(modelId)
        }

        try {
            limiter.begin(userId, current.credentialGeneration, AiProviderCallKind.VERIFICATION)
        } catch (failure: Exception) {
            synchronized(this) { idempotency.remove(scopedKey) }
            throw failure
        }
        return try {
            val result = credentials.withCredentialSuspending(userId) { providerId, apiKey ->
                if (providerId != provider.providerId || providerId != current.providerId) {
                    AiModelVerificationResult.Failed(
                        com.entio.web.ai.provider.AiProviderModelError(
                            AiProviderModelErrorCategory.AUTHORIZATION,
                            retryable = false,
                        ),
                    )
                } else {
                    provider.verifyModel(apiKey, modelId)
                }
            } ?: throw AiModelSettingsFailure("ai-credential-missing")
            val updated = when (result) {
                AiModelVerificationResult.Verified -> current.copy(
                    selectedModelId = modelId,
                    selectedModelVerifiedAt = clock.instant(),
                    selectionStatus = AiModelSelectionStatus.READY,
                    candidates = current.candidates.map { descriptor ->
                        descriptor.copy(
                            verificationStatus = if (descriptor.modelId == modelId) {
                                AiModelVerificationStatus.VERIFIED
                            } else {
                                AiModelVerificationStatus.NOT_VERIFIED
                            },
                            compatibilityState = if (descriptor.modelId == modelId) {
                                AiModelCompatibilityState.AVAILABLE_AND_COMPATIBLE
                            } else {
                                AiModelCompatibilityState.CANDIDATE_REQUIRES_VERIFICATION
                            },
                        )
                    },
                    lastProviderErrorCategory = null,
                )
                is AiModelVerificationResult.Failed -> verificationFailed(current, result.error.category)
            }
            settingsStore.save(updated)
            synchronized(this) {
                idempotency.getValue(scopedKey).apply {
                    complete = true
                    this.result = updated
                }
            }
            updated
        } catch (failure: Exception) {
            synchronized(this) { idempotency.remove(scopedKey) }
            throw failure
        } finally {
            limiter.end(userId, current.credentialGeneration, AiProviderCallKind.VERIFICATION)
        }
    }

    private fun verificationFailed(
        current: AiUserProviderSettings,
        category: AiProviderModelErrorCategory,
    ): AiUserProviderSettings = current.copy(
        credentialStatus = if (category == AiProviderModelErrorCategory.AUTHENTICATION) {
            AiSettingsCredentialStatus.INVALID
        } else {
            current.credentialStatus
        },
        selectedModelId = null,
        selectedModelVerifiedAt = null,
        selectionStatus = AiModelSelectionStatus.VERIFICATION_FAILED,
        lastProviderErrorCategory = category,
    )
}

public class AiModelSelectionService(
    private val settingsStore: AiUserProviderSettingsStore,
    private val discovery: AiModelDiscoveryService,
    private val verification: AiModelVerificationService,
) {
    public suspend fun selectAndVerify(
        userId: String,
        modelId: String,
        idempotencyKey: String,
    ): AiUserProviderSettings {
        var current = settingsStore.find(userId) ?: throw AiModelSettingsFailure("ai-credential-missing")
        if (!discovery.isFresh(current)) current = discovery.discover(userId, force = true)
        if (current.candidates.none { it.modelId == modelId }) throw AiModelSettingsFailure("ai-model-not-available")
        return verification.verify(userId, modelId, idempotencyKey)
    }

    public suspend fun retest(userId: String, idempotencyKey: String): AiUserProviderSettings {
        val selected = settingsStore.find(userId)?.selectedModelId
            ?: throw AiModelSettingsFailure("ai-model-selection-required")
        return selectAndVerify(userId, selected, idempotencyKey)
    }

    public fun clear(userId: String): AiUserProviderSettings {
        val current = settingsStore.find(userId) ?: throw AiModelSettingsFailure("ai-credential-missing")
        val updated = current.copy(
            selectedModelId = null,
            selectedModelVerifiedAt = null,
            selectionStatus = AiModelSelectionStatus.NOT_SELECTED,
            candidates = current.candidates.map { it.copy(
                verificationStatus = AiModelVerificationStatus.NOT_VERIFIED,
                compatibilityState = AiModelCompatibilityState.CANDIDATE_REQUIRES_VERIFICATION,
            ) },
        )
        settingsStore.save(updated)
        return updated
    }
}

public class SelectedAiRunModelBindingResolver(
    private val settingsStore: AiUserProviderSettingsStore,
    private val discovery: AiModelDiscoveryService,
    private val providerId: String,
    private val promptVersion: String,
) : AiRunModelBindingResolver {
    override fun resolve(userId: String): AiRunModelBinding {
        val settings = settingsStore.find(userId)
            ?: throw selectionRequired()
        val modelId = settings.selectedModelId
        val selected = modelId?.let { id -> settings.candidates.find { it.modelId == id } }
        if (
            settings.providerId != providerId ||
            settings.credentialStatus != AiSettingsCredentialStatus.VALID ||
            settings.discoveryStatus != AiModelDiscoveryStatus.COMPLETED ||
            !discovery.isFresh(settings) ||
            settings.selectionStatus != AiModelSelectionStatus.READY ||
            settings.selectedModelVerifiedAt == null ||
            selected?.verificationStatus != AiModelVerificationStatus.VERIFIED
        ) throw selectionRequired()
        return AiRunModelBinding(
            providerId = providerId,
            modelId = modelId,
            catalogVersion = settings.policyVersion,
            credentialGeneration = settings.credentialGeneration,
            promptVersion = promptVersion,
            requestPolicyVersion = REQUEST_POLICY_VERSION,
            compatibilityState = selected.compatibilityState,
        )
    }

    override suspend fun markUnavailableAndRefresh(userId: String, binding: AiRunModelBinding) {
        val current = settingsStore.find(userId) ?: return
        if (
            current.credentialGeneration != binding.credentialGeneration ||
            current.selectedModelId != binding.modelId
        ) return
        settingsStore.save(
            current.copy(
                discoveryStatus = AiModelDiscoveryStatus.STALE,
                selectedModelVerifiedAt = null,
                selectionStatus = AiModelSelectionStatus.UNAVAILABLE,
                candidates = current.candidates.map { descriptor ->
                    if (descriptor.modelId == binding.modelId) descriptor.copy(verificationStatus = AiModelVerificationStatus.FAILED) else descriptor
                },
                lastProviderErrorCategory = AiProviderModelErrorCategory.MODEL_NOT_AVAILABLE,
            ),
        )
        runCatching { discovery.discover(userId, force = true) }
    }

    private fun selectionRequired(): AiConversationFailure = AiConversationFailure(
        "AI_MODEL_SELECTION_REQUIRED",
        "Select and verify an available AI model before starting a run.",
    )

    private companion object {
        const val REQUEST_POLICY_VERSION: String = "phase-7.5-request-policy-v1"
    }
}

public class AiModelSettingsFailure(
    public val code: String,
) : IllegalArgumentException(code)
