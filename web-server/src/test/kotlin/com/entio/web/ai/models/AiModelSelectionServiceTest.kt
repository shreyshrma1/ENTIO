package com.entio.web.ai.models

import java.time.Duration
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AiModelSelectionServiceTest {
    @Test
    fun arbitraryAndCrossUserIdsAreRejectedBeforeVerification(): Unit = runBlocking {
        val fixture = aiModelServiceFixture()
        fixture.provider.inventory("alice-key", "gpt-5.6-sol")
        fixture.provider.inventory("bob-key", "future-tool-model")
        fixture.credentialSettings.saveCredential("alice", "openai", "alice-key")
        fixture.credentialSettings.saveCredential("bob", "openai", "bob-key")

        assertFailsWith<AiModelSettingsFailure> {
            fixture.selection.selectAndVerify("alice", "future-tool-model", "request-1")
        }
        assertFailsWith<AiModelSettingsFailure> {
            fixture.selection.selectAndVerify("alice", "client-invented", "request-2")
        }
        assertEquals(0, fixture.provider.verificationRequests.size)
    }

    @Test
    fun explicitSelectionVerifiesPerUserAndClearPreservesCredential(): Unit = runBlocking {
        val fixture = aiModelServiceFixture()
        fixture.provider.inventory("alice-key", "gpt-5.6-sol")
        fixture.provider.inventory("bob-key", "gpt-5.6-terra")
        fixture.credentialSettings.saveCredential("alice", "openai", "alice-key")
        fixture.credentialSettings.saveCredential("bob", "openai", "bob-key")

        val ready = fixture.selection.selectAndVerify("alice", "gpt-5.6-sol", "request-1")
        assertEquals(AiModelSelectionStatus.READY, ready.selectionStatus)
        assertEquals("gpt-5.6-sol", ready.selectedModelId)
        assertNull(fixture.credentialSettings.settings("bob").selectedModelId)

        val cleared = fixture.selection.clear("alice")
        assertEquals(AiModelSelectionStatus.NOT_SELECTED, cleared.selectionStatus)
        assertEquals(AiSettingsCredentialStatus.VALID, cleared.credentialStatus)
        assertNull(cleared.selectedModelId)
    }

    @Test
    fun staleSelectionRefreshesAndUnavailableCandidateIsRejected(): Unit = runBlocking {
        val fixture = aiModelServiceFixture()
        fixture.provider.inventory("key", "gpt-5.6-sol")
        fixture.credentialSettings.saveCredential("alice", "openai", "key")
        fixture.clock.advance(Duration.ofMinutes(16))
        fixture.provider.inventory("key", "gpt-5.6-terra")

        assertFailsWith<AiModelSettingsFailure> {
            fixture.selection.selectAndVerify("alice", "gpt-5.6-sol", "request-1")
        }
        assertEquals(0, fixture.provider.verificationRequests.size)
    }

    @Test
    fun replacingRemovingAndRestartCleanupInvalidateSelection(): Unit = runBlocking {
        val fixture = aiModelServiceFixture()
        fixture.provider.inventory("key-1", "gpt-5.6-sol")
        fixture.provider.inventory("key-2", "gpt-5.6-terra")
        fixture.credentialSettings.saveCredential("alice", "openai", "key-1")
        val first = fixture.selection.selectAndVerify("alice", "gpt-5.6-sol", "request-1")

        val replaced = fixture.credentialSettings.saveCredential("alice", "openai", "key-2")
        assertEquals(first.credentialGeneration + 1, replaced.credentialGeneration)
        assertNull(replaced.selectedModelId)
        assertEquals(AiModelSelectionStatus.NOT_SELECTED, replaced.selectionStatus)

        val removed = fixture.credentialSettings.removeCredential("alice")
        assertEquals(AiSettingsCredentialStatus.NOT_CONFIGURED, removed.credentialStatus)
        assertNull(fixture.credentials.providerFor("alice"))

        fixture.credentialSettings.saveCredential("alice", "openai", "key-1")
        fixture.credentialSettings.clearAll()
        assertEquals(AiSettingsCredentialStatus.NOT_CONFIGURED, fixture.credentialSettings.settings("alice").credentialStatus)
        assertNull(fixture.credentials.providerFor("alice"))
    }
}
