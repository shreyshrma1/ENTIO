package com.entio.web.ai

public enum class AiCompositeCapabilityStatus { APPROVED, CLARIFICATION_REQUIRED, EXPLANATION_ONLY, UNSUPPORTED }

public data class AiCompositeInventoryEntry(
    val name: String,
    val status: AiCompositeCapabilityStatus,
    val maxOperations: Int,
)

public sealed interface AiCompositePreparationResult {
    public data class Prepared(val operations: List<AiTypedDraftOperation>) : AiCompositePreparationResult
    public data class ClarificationRequired(val capabilityName: String, val message: String) : AiCompositePreparationResult
    public data class Unsupported(val capabilityName: String, val message: String) : AiCompositePreparationResult
}

public class AiCompositeCapabilityService(
    private val adapter: AiTypedEditCapabilityAdapter,
) {
    public fun exposedCapabilities(): List<AiCompositeInventoryEntry> = inventory.values
        .filter { it.status == AiCompositeCapabilityStatus.APPROVED }
        .sortedBy(AiCompositeInventoryEntry::name)

    public fun prepare(
        scope: AiCapabilityScope,
        capabilityName: String,
        entries: List<AiDraftBatchEntry>,
        ambiguous: Boolean = false,
    ): AiCompositePreparationResult {
        val inventoryEntry = inventory[capabilityName]
            ?: return AiCompositePreparationResult.Unsupported(capabilityName, "The composite capability is not inventoried.")
        if (inventoryEntry.status != AiCompositeCapabilityStatus.APPROVED || ambiguous) {
            return AiCompositePreparationResult.ClarificationRequired(
                capabilityName,
                "The composite request requires explicit target and dependency clarification.",
            )
        }
        if (entries.isEmpty() || entries.size > inventoryEntry.maxOperations) {
            return AiCompositePreparationResult.Unsupported(capabilityName, "The composite output exceeds its bounded operation limit.")
        }
        return AiCompositePreparationResult.Prepared(
            entries.map { adapter.prepare(scope, it.capabilityName, it.arguments.request) },
        )
    }

    public companion object {
        public val inventory: Map<String, AiCompositeInventoryEntry> = listOf(
            AiCompositeInventoryEntry("prepare_class_model", AiCompositeCapabilityStatus.APPROVED, 20),
            AiCompositeInventoryEntry("prepare_property_model", AiCompositeCapabilityStatus.APPROVED, 20),
            AiCompositeInventoryEntry("prepare_domain_model_batch", AiCompositeCapabilityStatus.APPROVED, 20),
            AiCompositeInventoryEntry("prepare_external_reuse", AiCompositeCapabilityStatus.CLARIFICATION_REQUIRED, 20),
            AiCompositeInventoryEntry("prepare_entity_refactor", AiCompositeCapabilityStatus.CLARIFICATION_REQUIRED, 20),
        ).associateBy(AiCompositeInventoryEntry::name)
    }
}
