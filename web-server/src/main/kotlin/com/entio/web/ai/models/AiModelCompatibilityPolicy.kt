package com.entio.web.ai.models

public data class AiProviderModelDescriptor(
    val providerId: String,
    val modelId: String,
)

public enum class AiModelVerificationStatus {
    NOT_VERIFIED,
    VERIFIED,
    FAILED,
}

public enum class AiModelCompatibilityState {
    CANDIDATE_REQUIRES_VERIFICATION,
    AVAILABLE_AND_COMPATIBLE,
    VERIFICATION_FAILED,
    EXCLUDED_UNSUPPORTED_CATEGORY,
    EXCLUDED_MOVING_ALIAS,
    MALFORMED,
}

public data class AiModelEligibility(
    val providerId: String,
    val modelId: String,
    val state: AiModelCompatibilityState,
    val eligibleForVerification: Boolean,
)

public data class AiSelectableModelDescriptor(
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val description: String,
    val metadataKnown: Boolean,
    val recommended: Boolean,
    val capabilityTier: AiModelCapabilityTier?,
    val timeoutClass: AiModelTimeoutClass?,
    val relativeSpeed: String?,
    val relativeCost: String?,
    val verificationStatus: AiModelVerificationStatus,
    val compatibilityState: AiModelCompatibilityState,
    val policyVersion: String,
)

public data class AiModelCompatibilityProjection(
    val policyVersion: String,
    val candidates: List<AiSelectableModelDescriptor>,
    val eligibility: List<AiModelEligibility>,
    val unsupportedProviderModelCount: Int,
)

/** Deterministic Entio policy over minimal provider model descriptors. */
public class AiModelCompatibilityPolicy(
    private val metadata: AiKnownModelMetadataCatalog = AiKnownModelMetadataCatalog(),
    public val version: String = POLICY_VERSION,
) {
    init {
        require(version.isNotBlank()) { "compatibility-policy-version-required" }
    }

    public fun project(
        providerModels: List<AiProviderModelDescriptor>,
        verificationByModelId: Map<String, AiModelVerificationStatus> = emptyMap(),
    ): AiModelCompatibilityProjection {
        val uniqueModels = providerModels
            .distinctBy { it.providerId to it.modelId }
            .sortedWith(compareBy(AiProviderModelDescriptor::providerId, AiProviderModelDescriptor::modelId))
        val eligibility = uniqueModels.map(::classify)
        val candidates = eligibility
            .filter(AiModelEligibility::eligibleForVerification)
            .map { eligible -> descriptor(eligible, verificationByModelId[eligible.modelId] ?: AiModelVerificationStatus.NOT_VERIFIED) }
            .sortedWith(candidateComparator)

        return AiModelCompatibilityProjection(
            policyVersion = version,
            candidates = candidates,
            eligibility = eligibility,
            unsupportedProviderModelCount = eligibility.count { !it.eligibleForVerification },
        )
    }

    public fun classify(model: AiProviderModelDescriptor): AiModelEligibility {
        val state = when {
            model.providerId != AiKnownModelMetadataCatalog.OPENAI_PROVIDER_ID -> AiModelCompatibilityState.MALFORMED
            !isValidModelId(model.modelId) -> AiModelCompatibilityState.MALFORMED
            isMovingAlias(model.modelId) -> AiModelCompatibilityState.EXCLUDED_MOVING_ALIAS
            unsupportedCategoryPattern.containsMatchIn(model.modelId) -> AiModelCompatibilityState.EXCLUDED_UNSUPPORTED_CATEGORY
            else -> AiModelCompatibilityState.CANDIDATE_REQUIRES_VERIFICATION
        }
        return AiModelEligibility(
            providerId = model.providerId,
            modelId = model.modelId,
            state = state,
            eligibleForVerification = state == AiModelCompatibilityState.CANDIDATE_REQUIRES_VERIFICATION,
        )
    }

    private fun descriptor(
        eligibility: AiModelEligibility,
        verificationStatus: AiModelVerificationStatus,
    ): AiSelectableModelDescriptor {
        val known = metadata.find(eligibility.providerId, eligibility.modelId)
        val compatibilityState = when (verificationStatus) {
            AiModelVerificationStatus.NOT_VERIFIED -> AiModelCompatibilityState.CANDIDATE_REQUIRES_VERIFICATION
            AiModelVerificationStatus.VERIFIED -> AiModelCompatibilityState.AVAILABLE_AND_COMPATIBLE
            AiModelVerificationStatus.FAILED -> AiModelCompatibilityState.VERIFICATION_FAILED
        }
        return AiSelectableModelDescriptor(
            providerId = eligibility.providerId,
            modelId = eligibility.modelId,
            displayName = known?.displayName ?: eligibility.modelId,
            description = known?.description ?: UNKNOWN_MODEL_DESCRIPTION,
            metadataKnown = known != null,
            recommended = known?.recommended == true,
            capabilityTier = known?.capabilityTier,
            timeoutClass = known?.timeoutClass,
            relativeSpeed = known?.relativeSpeed,
            relativeCost = known?.relativeCost,
            verificationStatus = verificationStatus,
            compatibilityState = compatibilityState,
            policyVersion = version,
        )
    }

    private val candidateComparator: Comparator<AiSelectableModelDescriptor> = compareBy(
        { descriptor -> metadata.find(descriptor.providerId, descriptor.modelId)?.preferenceRank ?: Int.MAX_VALUE },
        AiSelectableModelDescriptor::displayName,
        AiSelectableModelDescriptor::modelId,
    )

    public companion object {
        public const val POLICY_VERSION: String = "phase-7.5-compatibility-v1"
        public const val UNKNOWN_MODEL_DESCRIPTION: String = "Discovered model; compatibility must be verified."

        private val validModelIdPattern: Regex = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,199}")
        private val movingAliasPattern: Regex = Regex("(^|[-_.])(latest|current)([-_.]|$)", RegexOption.IGNORE_CASE)
        private val unsupportedCategoryPattern: Regex = Regex(
            "(^|[-_.])(embedding|embeddings|moderation|image|images|dall-e|tts|speech|transcribe|transcription|whisper|audio|realtime|sora|video)([-_.]|$)",
            RegexOption.IGNORE_CASE,
        )

        public fun isValidModelId(modelId: String): Boolean = validModelIdPattern.matches(modelId)

        public fun isMovingAlias(modelId: String): Boolean = movingAliasPattern.containsMatchIn(modelId)
    }
}
