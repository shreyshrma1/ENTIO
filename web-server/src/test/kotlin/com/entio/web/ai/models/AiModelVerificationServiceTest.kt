package com.entio.web.ai.models

import com.entio.web.ai.provider.AiModelVerificationResult
import com.entio.web.ai.provider.AiProviderModelError
import com.entio.web.ai.provider.AiProviderModelErrorCategory
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AiModelVerificationServiceTest {
    @Test
    fun verificationIsHarmlessServerScopedAndIdempotent(): Unit = runBlocking {
        val fixture = aiModelServiceFixture()
        fixture.provider.inventory("secret-key", "gpt-5.6-sol")
        fixture.credentialSettings.saveCredential("alice", "openai", "secret-key")

        val first = fixture.selection.selectAndVerify("alice", "gpt-5.6-sol", "request-1")
        val replay = fixture.selection.selectAndVerify("alice", "gpt-5.6-sol", "request-1")

        assertEquals(first, replay)
        assertEquals(listOf("secret-key" to "gpt-5.6-sol"), fixture.provider.verificationRequests)
        assertFalse(first.toString().contains("secret-key"))
        assertFalse(fixture.provider.verificationRequests.toString().contains("project"))
        assertFalse(fixture.provider.verificationRequests.toString().contains("ontology"))
    }

    @Test
    fun verificationFailurePreservesValidCredentialAndStoresNoSelection(): Unit = runBlocking {
        val fixture = aiModelServiceFixture()
        fixture.provider.inventory("key", "gpt-5.6-sol")
        fixture.provider.verifications["key" to "gpt-5.6-sol"] = AiModelVerificationResult.Failed(
            AiProviderModelError(AiProviderModelErrorCategory.MODEL_NOT_AVAILABLE, retryable = false),
        )
        fixture.credentialSettings.saveCredential("alice", "openai", "key")

        val failed = fixture.selection.selectAndVerify("alice", "gpt-5.6-sol", "request-1")

        assertEquals(AiSettingsCredentialStatus.VALID, failed.credentialStatus)
        assertEquals(AiModelSelectionStatus.VERIFICATION_FAILED, failed.selectionStatus)
        assertNull(failed.selectedModelId)
    }

    @Test
    fun authenticationFailureInvalidatesCredentialAndVerificationLimitIsBounded(): Unit = runBlocking {
        val invalidFixture = aiModelServiceFixture()
        invalidFixture.provider.inventory("bad-key", "gpt-5.6-sol")
        invalidFixture.provider.verifications["bad-key" to "gpt-5.6-sol"] = AiModelVerificationResult.Failed(
            AiProviderModelError(AiProviderModelErrorCategory.AUTHENTICATION, retryable = false),
        )
        invalidFixture.credentialSettings.saveCredential("alice", "openai", "bad-key")
        val invalid = invalidFixture.selection.selectAndVerify("alice", "gpt-5.6-sol", "request-1")
        assertEquals(AiSettingsCredentialStatus.INVALID, invalid.credentialStatus)
        assertNull(invalid.selectedModelId)

        val limited = aiModelServiceFixture(verificationLimit = 3)
        limited.provider.inventory("key", "gpt-5.6-sol")
        limited.credentialSettings.saveCredential("alice", "openai", "key")
        repeat(3) { index ->
            limited.selection.selectAndVerify("alice", "gpt-5.6-sol", "request-$index")
        }
        val failure = assertFailsWith<AiModelSettingsFailure> {
            limited.selection.selectAndVerify("alice", "gpt-5.6-sol", "request-4")
        }
        assertEquals("ai-provider-local-rate-limited", failure.code)
        assertEquals(3, limited.provider.verificationRequests.size)
    }

    @Test
    fun oneUsersVerificationNeverChangesAnotherUsersSettings(): Unit = runBlocking {
        val fixture = aiModelServiceFixture()
        fixture.provider.inventory("alice-key", "gpt-5.6-sol")
        fixture.provider.inventory("bob-key", "gpt-5.6-sol")
        fixture.credentialSettings.saveCredential("alice", "openai", "alice-key")
        fixture.credentialSettings.saveCredential("bob", "openai", "bob-key")

        fixture.selection.selectAndVerify("alice", "gpt-5.6-sol", "alice-request")

        assertEquals(AiModelSelectionStatus.READY, fixture.credentialSettings.settings("alice").selectionStatus)
        assertEquals(AiModelSelectionStatus.NOT_SELECTED, fixture.credentialSettings.settings("bob").selectionStatus)
    }
}
