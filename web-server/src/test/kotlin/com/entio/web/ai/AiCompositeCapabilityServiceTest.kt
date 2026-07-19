package com.entio.web.ai

import com.entio.web.contract.WebStageChangeRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AiCompositeCapabilityServiceTest {
    @Test
    fun exposedCapabilitiesAreCompleteApprovedInventorySubset(): Unit {
        val fixture = editingFixture()
        val service = AiCompositeCapabilityService(fixture.adapter)

        assertEquals(
            setOf("prepare_class_model", "prepare_property_model", "prepare_domain_model_batch"),
            service.exposedCapabilities().map(AiCompositeInventoryEntry::name).toSet(),
        )
        assertTrue(service.exposedCapabilities().all { it.status == AiCompositeCapabilityStatus.APPROVED })
    }

    @Test
    fun approvedCompositeMatchesManualTypedPreparation(): Unit {
        val fixture = editingFixture()
        val service = AiCompositeCapabilityService(fixture.adapter)
        val entry = fixture.entry(WebStageChangeRequest("simple", "create-class", label = "Receivable"))

        val prepared = assertIs<AiCompositePreparationResult.Prepared>(
            service.prepare(fixture.scope, "prepare_class_model", listOf(entry)),
        ).operations.single()
        val manual = fixture.adapter.prepare(fixture.scope, entry.capabilityName, entry.arguments.request)

        assertEquals(manual, prepared)
        assertTrue(fixture.staging.snapshot("simple").entries.isEmpty())
    }

    @Test
    fun ambiguousUnapprovedAndUnknownCapabilitiesNeverGuess(): Unit {
        val fixture = editingFixture()
        val service = AiCompositeCapabilityService(fixture.adapter)
        val entry = fixture.entry(WebStageChangeRequest("simple", "create-class", label = "Receivable"))

        assertIs<AiCompositePreparationResult.ClarificationRequired>(
            service.prepare(fixture.scope, "prepare_external_reuse", listOf(entry)),
        )
        assertIs<AiCompositePreparationResult.ClarificationRequired>(
            service.prepare(fixture.scope, "prepare_class_model", listOf(entry), ambiguous = true),
        )
        assertIs<AiCompositePreparationResult.Unsupported>(
            service.prepare(fixture.scope, "missing_inventory_entry", listOf(entry)),
        )
    }
}
