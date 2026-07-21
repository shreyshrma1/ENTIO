package com.entio.web.ai.models

public enum class AiModelCapabilityTier {
    FRONTIER,
    BALANCED,
    EFFICIENT,
}

public enum class AiModelTimeoutClass {
    STANDARD,
    EXTENDED,
}

public data class AiKnownModelMetadata(
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val description: String,
    val capabilityTier: AiModelCapabilityTier,
    val timeoutClass: AiModelTimeoutClass,
    val relativeSpeed: String,
    val relativeCost: String,
    val preferenceRank: Int,
    val recommended: Boolean = false,
)

/** Optional presentation metadata. Membership never grants model eligibility. */
public class AiKnownModelMetadataCatalog(
    entries: List<AiKnownModelMetadata> = defaultKnownModelMetadata(),
    supportedProviderIds: Set<String> = setOf(OPENAI_PROVIDER_ID),
) {
    private val entriesByKey: Map<Pair<String, String>, AiKnownModelMetadata>

    init {
        require(supportedProviderIds.isNotEmpty()) { "supported-provider-required" }
        entries.forEach { entry ->
            require(entry.providerId in supportedProviderIds) { "unsupported-metadata-provider" }
            require(AiModelCompatibilityPolicy.isValidModelId(entry.modelId)) { "invalid-metadata-model-id" }
            require(!AiModelCompatibilityPolicy.isMovingAlias(entry.modelId)) { "moving-alias-metadata-forbidden" }
            require(entry.displayName.isNotBlank()) { "metadata-display-name-required" }
            require(entry.description.isNotBlank()) { "metadata-description-required" }
            require(entry.relativeSpeed.isNotBlank()) { "metadata-relative-speed-required" }
            require(entry.relativeCost.isNotBlank()) { "metadata-relative-cost-required" }
            require(entry.preferenceRank >= 0) { "metadata-preference-rank-invalid" }
        }
        require(entries.map { it.providerId to it.modelId }.distinct().size == entries.size) {
            "duplicate-model-metadata"
        }
        entriesByKey = entries.associateBy { it.providerId to it.modelId }
    }

    public fun find(providerId: String, modelId: String): AiKnownModelMetadata? =
        entriesByKey[providerId to modelId]

    public fun entries(): List<AiKnownModelMetadata> = entriesByKey.values.sortedWith(metadataComparator)

    public companion object {
        public const val OPENAI_PROVIDER_ID: String = "openai"

        private val metadataComparator: Comparator<AiKnownModelMetadata> =
            compareBy(AiKnownModelMetadata::preferenceRank, AiKnownModelMetadata::displayName, AiKnownModelMetadata::modelId)

        public fun defaultKnownModelMetadata(): List<AiKnownModelMetadata> = listOf(
            AiKnownModelMetadata(
                providerId = OPENAI_PROVIDER_ID,
                modelId = "gpt-4o",
                displayName = "GPT-4o",
                description = "Fast, capable GPT-4-class model for everyday ontology questions and edits.",
                capabilityTier = AiModelCapabilityTier.BALANCED,
                timeoutClass = AiModelTimeoutClass.STANDARD,
                relativeSpeed = "Fast",
                relativeCost = "Lower relative usage cost",
                preferenceRank = 1,
                recommended = true,
            ),
            AiKnownModelMetadata(
                providerId = OPENAI_PROVIDER_ID,
                modelId = "gpt-4o-mini",
                displayName = "GPT-4o mini",
                description = "Lightweight GPT-4-class model for fast, focused ontology conversations.",
                capabilityTier = AiModelCapabilityTier.EFFICIENT,
                timeoutClass = AiModelTimeoutClass.STANDARD,
                relativeSpeed = "Very fast",
                relativeCost = "Lowest relative usage cost",
                preferenceRank = 2,
                recommended = true,
            ),
            AiKnownModelMetadata(
                providerId = OPENAI_PROVIDER_ID,
                modelId = "gpt-5.6-sol",
                displayName = "GPT-5.6 Sol",
                description = "Frontier capability for broad, multi-step ontology design and review.",
                capabilityTier = AiModelCapabilityTier.FRONTIER,
                timeoutClass = AiModelTimeoutClass.EXTENDED,
                relativeSpeed = "Deliberate",
                relativeCost = "Higher relative usage cost",
                preferenceRank = 10,
                recommended = true,
            ),
            AiKnownModelMetadata(
                providerId = OPENAI_PROVIDER_ID,
                modelId = "gpt-5.6-terra",
                displayName = "GPT-5.6 Terra",
                description = "Balanced capability for everyday ontology explanation and editing.",
                capabilityTier = AiModelCapabilityTier.BALANCED,
                timeoutClass = AiModelTimeoutClass.STANDARD,
                relativeSpeed = "Balanced",
                relativeCost = "Moderate relative usage cost",
                preferenceRank = 20,
            ),
            AiKnownModelMetadata(
                providerId = OPENAI_PROVIDER_ID,
                modelId = "gpt-5.6-luna",
                displayName = "GPT-5.6 Luna",
                description = "Efficient option for focused explanations and bounded ontology edits.",
                capabilityTier = AiModelCapabilityTier.EFFICIENT,
                timeoutClass = AiModelTimeoutClass.STANDARD,
                relativeSpeed = "Faster",
                relativeCost = "Lower relative usage cost",
                preferenceRank = 30,
            ),
        )
    }
}
