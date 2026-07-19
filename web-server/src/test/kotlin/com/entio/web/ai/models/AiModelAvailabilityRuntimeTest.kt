package com.entio.web.ai.models

import com.entio.web.ai.AiConversationFailure
import java.time.Duration
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AiModelAvailabilityRuntimeTest {
    @Test
    fun missingUnverifiedAndStaleSelectionsCannotCreateRunBindings(): Unit = runBlocking {
        val fixture = aiModelServiceFixture()
        val resolver = resolver(fixture)
        assertSelectionRequired { resolver.resolve("alice") }

        fixture.provider.inventory("key", "gpt-5.6-sol")
        fixture.credentialSettings.saveCredential("alice", "openai", "key")
        assertSelectionRequired { resolver.resolve("alice") }

        fixture.selection.selectAndVerify("alice", "gpt-5.6-sol", "select-1")
        fixture.clock.advance(Duration.ofMinutes(16))
        assertSelectionRequired { resolver.resolve("alice") }
    }

    @Test
    fun verifiedSelectionCreatesRedactedBindingAndAccessLossForcesRefresh(): Unit = runBlocking {
        val fixture = aiModelServiceFixture()
        fixture.provider.inventory("key", "gpt-5.6-sol")
        fixture.credentialSettings.saveCredential("alice", "openai", "key")
        val ready = fixture.selection.selectAndVerify("alice", "gpt-5.6-sol", "select-1")
        val resolver = resolver(fixture)

        val binding = resolver.resolve("alice")
        assertEquals("gpt-5.6-sol", binding.modelId)
        assertEquals(ready.credentialGeneration, binding.credentialGeneration)
        assertEquals(ready.policyVersion, binding.catalogVersion)

        fixture.provider.inventory("key", "gpt-5.6-terra")
        resolver.markUnavailableAndRefresh("alice", binding)
        val recovered = fixture.store.find("alice")!!
        assertEquals(AiModelSelectionStatus.UNAVAILABLE, recovered.selectionStatus)
        assertEquals(AiModelDiscoveryStatus.COMPLETED, recovered.discoveryStatus)
        assertEquals(listOf("gpt-5.6-terra"), recovered.candidates.map(AiSelectableModelDescriptor::modelId))
        assertEquals(2, fixture.provider.discoveryKeys.size)
    }

    private fun resolver(fixture: AiModelServiceFixture): SelectedAiRunModelBindingResolver =
        SelectedAiRunModelBindingResolver(fixture.store, fixture.discovery, "openai", "prompt-test")

    private fun assertSelectionRequired(block: () -> Unit) {
        val failure = assertFailsWith<AiConversationFailure> { block() }
        assertEquals("AI_MODEL_SELECTION_REQUIRED", failure.code)
    }
}
