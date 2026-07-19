package com.entio.web.ai.models

import com.entio.web.ai.provider.AiProviderModelErrorCategory
import java.time.Duration
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiModelDiscoveryServiceTest {
    @Test
    fun credentialSaveDiscoversPerUserAndZeroOneMultipleCandidatesRemainUnselected(): Unit = runBlocking {
        val fixture = aiModelServiceFixture()
        fixture.provider.inventory("alice-key")
        fixture.provider.inventory("bob-key", "gpt-5.6-sol")
        fixture.provider.inventory("carol-key", "gpt-5.6-sol", "future-tool-model")

        val alice = fixture.credentialSettings.saveCredential("alice", "openai", "alice-key")
        val bob = fixture.credentialSettings.saveCredential("bob", "openai", "bob-key")
        val carol = fixture.credentialSettings.saveCredential("carol", "openai", "carol-key")

        assertEquals(AiModelDiscoveryStatus.NO_COMPATIBLE_MODELS, alice.discoveryStatus)
        assertEquals(1, bob.candidates.size)
        assertEquals(2, carol.candidates.size)
        assertTrue(listOf(alice, bob, carol).all { it.selectionStatus == AiModelSelectionStatus.NOT_SELECTED })
        assertTrue(listOf(alice, bob, carol).all { it.credentialStatus == AiSettingsCredentialStatus.VALID })
        assertEquals(listOf("alice-key", "bob-key", "carol-key"), fixture.provider.discoveryKeys)
    }

    @Test
    fun freshDiscoveryIsReusedExplicitRefreshBypassesAndStaleDiscoveryRefreshes(): Unit = runBlocking {
        val fixture = aiModelServiceFixture()
        fixture.provider.inventory("key", "gpt-5.6-sol")
        fixture.credentialSettings.saveCredential("alice", "openai", "key")

        fixture.discovery.discover("alice")
        assertEquals(1, fixture.provider.discoveryKeys.size)
        fixture.discovery.discover("alice", force = true)
        assertEquals(2, fixture.provider.discoveryKeys.size)
        fixture.clock.advance(Duration.ofMinutes(16))
        fixture.discovery.discover("alice")
        assertEquals(3, fixture.provider.discoveryKeys.size)
    }

    @Test
    fun authenticationFailureInvalidatesCredentialWhileOtherFailuresPreserveIt(): Unit = runBlocking {
        val fixture = aiModelServiceFixture()
        fixture.provider.failDiscovery("bad-key", AiProviderModelErrorCategory.AUTHENTICATION)
        fixture.provider.failDiscovery("slow-key", AiProviderModelErrorCategory.TIMEOUT)

        val invalid = fixture.credentialSettings.saveCredential("alice", "openai", "bad-key")
        val timeout = fixture.credentialSettings.saveCredential("bob", "openai", "slow-key")

        assertEquals(AiSettingsCredentialStatus.INVALID, invalid.credentialStatus)
        assertEquals(AiSettingsCredentialStatus.VALID, timeout.credentialStatus)
        assertEquals(AiModelDiscoveryStatus.FAILED, timeout.discoveryStatus)
        assertNull(timeout.selectedModelId)
    }

    @Test
    fun discoveryLocalLimitIsPerUserAndReplacementDoesNotBypassIt(): Unit = runBlocking {
        val fixture = aiModelServiceFixture(discoveryLimit = 5)
        fixture.provider.inventory("key-1", "gpt-5.6-sol")
        fixture.provider.inventory("key-2", "gpt-5.6-terra")
        fixture.credentialSettings.saveCredential("alice", "openai", "key-1")
        repeat(3) { fixture.discovery.discover("alice", force = true) }
        fixture.credentialSettings.saveCredential("alice", "openai", "key-2")

        val failure = assertFailsWith<AiModelSettingsFailure> {
            fixture.discovery.discover("alice", force = true)
        }
        assertEquals("ai-provider-local-rate-limited", failure.code)

        fixture.provider.inventory("bob-key", "gpt-5.6-sol")
        assertEquals(
            AiModelDiscoveryStatus.COMPLETED,
            fixture.credentialSettings.saveCredential("bob", "openai", "bob-key").discoveryStatus,
        )
    }
}
