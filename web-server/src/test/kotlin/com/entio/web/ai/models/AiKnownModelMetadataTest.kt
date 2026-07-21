package com.entio.web.ai.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiKnownModelMetadataTest {
    @Test
    fun defaultMetadataIsDeterministicOptionalAndUsesRelativeDescriptions(): Unit {
        val entries = AiKnownModelMetadataCatalog().entries()

        assertEquals(listOf("gpt-4o", "gpt-4o-mini", "gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna"), entries.map { it.modelId })
        assertTrue(entries.single { it.modelId == "gpt-4o" }.recommended)
        assertTrue(entries.single { it.modelId == "gpt-4o-mini" }.recommended)
        assertTrue(entries.single { it.modelId == "gpt-5.6-sol" }.recommended)
        assertTrue(entries.all { it.relativeCost.contains("relative", ignoreCase = true) })
        assertFalse(entries.any { it.relativeCost.contains('$') })
        assertEquals(null, AiKnownModelMetadataCatalog().find("openai", "future-tool-model-2026-07-19"))
    }

    @Test
    fun catalogRejectsDuplicatesUnsupportedProvidersInvalidRanksAndMovingAliases(): Unit {
        val valid = metadata("gpt-5.6-sol")

        assertFailsWith<IllegalArgumentException> { AiKnownModelMetadataCatalog(listOf(valid, valid.copy())) }
        assertFailsWith<IllegalArgumentException> { AiKnownModelMetadataCatalog(listOf(valid.copy(providerId = "other"))) }
        assertFailsWith<IllegalArgumentException> { AiKnownModelMetadataCatalog(listOf(valid.copy(preferenceRank = -1))) }
        assertFailsWith<IllegalArgumentException> { AiKnownModelMetadataCatalog(listOf(valid.copy(modelId = "gpt-latest"))) }
    }

    private fun metadata(modelId: String): AiKnownModelMetadata = AiKnownModelMetadata(
        providerId = "openai",
        modelId = modelId,
        displayName = "Known model",
        description = "Known Entio metadata.",
        capabilityTier = AiModelCapabilityTier.BALANCED,
        timeoutClass = AiModelTimeoutClass.STANDARD,
        relativeSpeed = "Balanced",
        relativeCost = "Moderate relative usage cost",
        preferenceRank = 10,
    )
}
