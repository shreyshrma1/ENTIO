package com.entio.web.ai

import com.entio.web.CollaborationHub
import com.entio.web.contract.InMemoryProjectRegistry
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class AiTaskCollaborationPrivacyTest {
    @Test
    fun collaboratorsReceiveOnlyPostSubmissionSummaryRationaleAndSources(): Unit = runBlocking {
        val root = Files.createTempDirectory("task-collaboration")
        val registry = InMemoryProjectRegistry(setOf(root))
        registry.register("simple", "Simple", Files.createDirectory(root.resolve("simple")))
        val hub = CollaborationHub(registry)

        hub.aiTaskProposalSubmitted(
            "simple", "proposal-1", "task-1", "alice", "Add banking definitions.", listOf("simple"), listOf("Defined Account"),
        )

        val event = hub.recentSharedActivity("simple").events.single()
        assertEquals("proposal.ai-task-submitted", event.eventType)
        assertEquals(setOf("aiGenerated", "taskId", "submittingUserId", "rationale", "sourceIds", "packageSummaries"), event.data.keys)
        assertTrue(event.data["sourceIds"].toString().contains("simple"))
        listOf("conversation", "repair", "provider", "prompt", "privateDraft", "plan").forEach {
            assertFalse(event.data.keys.any { key -> key.contains(it, ignoreCase = true) })
        }
    }
}
