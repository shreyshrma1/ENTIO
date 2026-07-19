package com.entio.web.ai.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiModelCompatibilityPolicyTest {
    private val policy = AiModelCompatibilityPolicy()

    @Test
    fun excludesUnsupportedResourceFamiliesMovingAliasesMalformedIdsAndProviders(): Unit {
        val excludedIds = listOf(
            "text-embedding-3-large",
            "omni-moderation-latest",
            "gpt-image-1",
            "dall-e-3",
            "gpt-4o-transcribe",
            "tts-1",
            "whisper-1",
            "gpt-audio-1",
            "gpt-realtime-1",
            "sora-2",
            "video-generation-1",
        )
        val projection = policy.project(
            excludedIds.map { AiProviderModelDescriptor("openai", it) } +
                AiProviderModelDescriptor("openai", "gpt-5-latest") +
                AiProviderModelDescriptor("openai", "bad id") +
                AiProviderModelDescriptor("other", "chat-model-1"),
        )

        assertTrue(projection.candidates.isEmpty())
        assertEquals(excludedIds.size + 3, projection.unsupportedProviderModelCount)
        assertTrue(projection.eligibility.any { it.modelId == "gpt-5-latest" && it.state == AiModelCompatibilityState.EXCLUDED_MOVING_ALIAS })
        assertTrue(projection.eligibility.any { it.modelId == "bad id" && it.state == AiModelCompatibilityState.MALFORMED })
    }

    @Test
    fun unknownDiscoveredModelRemainsMinimalAndMayProceedToVerification(): Unit {
        val modelId = "future-tool-model-2026-07-19"
        val descriptor = policy.project(listOf(AiProviderModelDescriptor("openai", modelId))).candidates.single()

        assertEquals(modelId, descriptor.modelId)
        assertEquals(modelId, descriptor.displayName)
        assertEquals(AiModelCompatibilityPolicy.UNKNOWN_MODEL_DESCRIPTION, descriptor.description)
        assertFalse(descriptor.metadataKnown)
        assertEquals(AiModelVerificationStatus.NOT_VERIFIED, descriptor.verificationStatus)
        assertEquals(AiModelCompatibilityState.CANDIDATE_REQUIRES_VERIFICATION, descriptor.compatibilityState)
        assertNull(descriptor.capabilityTier)
        assertNull(descriptor.timeoutClass)
        assertNull(descriptor.relativeSpeed)
        assertNull(descriptor.relativeCost)
    }

    @Test
    fun projectionIsStableDeduplicatedAndOrderedByKnownPreferenceThenUnknownId(): Unit {
        val inventory = listOf(
            AiProviderModelDescriptor("openai", "zeta-tool-model"),
            AiProviderModelDescriptor("openai", "gpt-5.6-luna"),
            AiProviderModelDescriptor("openai", "gpt-5.6-sol"),
            AiProviderModelDescriptor("openai", "alpha-tool-model"),
            AiProviderModelDescriptor("openai", "gpt-5.6-sol"),
        )

        val first = policy.project(inventory)
        val second = policy.project(inventory.reversed())

        assertEquals(first, second)
        assertEquals(
            listOf("gpt-5.6-sol", "gpt-5.6-luna", "alpha-tool-model", "zeta-tool-model"),
            first.candidates.map { it.modelId },
        )
        assertTrue(first.candidates.first().metadataKnown)
        assertEquals(AiModelCompatibilityPolicy.POLICY_VERSION, first.policyVersion)
    }

    @Test
    fun verificationProjectionChangesUsabilityWithoutAcceptingClientCompatibilityInput(): Unit {
        val inventory = listOf(
            AiProviderModelDescriptor("openai", "gpt-5.6-sol"),
            AiProviderModelDescriptor("openai", "future-tool-model"),
        )
        val projection = policy.project(
            inventory,
            verificationByModelId = mapOf(
                "gpt-5.6-sol" to AiModelVerificationStatus.VERIFIED,
                "future-tool-model" to AiModelVerificationStatus.FAILED,
                "client-invented-model" to AiModelVerificationStatus.VERIFIED,
            ),
        )

        assertEquals(AiModelCompatibilityState.AVAILABLE_AND_COMPATIBLE, projection.candidates[0].compatibilityState)
        assertEquals(AiModelCompatibilityState.VERIFICATION_FAILED, projection.candidates[1].compatibilityState)
        assertNull(projection.candidates.find { it.modelId == "client-invented-model" })
        assertNotNull(projection.eligibility.find { it.modelId == "gpt-5.6-sol" })
    }

    @Test
    fun providerOnlyFieldsCannotCrossTheMinimalDescriptorProjection(): Unit {
        val providerPayload = mapOf(
            "id" to "gpt-5.6-sol",
            "owned_by" to "provider-internal",
            "created" to 123456789,
            "permission" to listOf("raw-provider-permission"),
        )
        val minimal = AiProviderModelDescriptor("openai", providerPayload.getValue("id") as String)
        val descriptor = policy.project(listOf(minimal)).candidates.single()

        assertEquals("gpt-5.6-sol", descriptor.modelId)
        assertFalse(descriptor.toString().contains("owned_by"))
        assertFalse(descriptor.toString().contains("raw-provider-permission"))
    }
}
