package com.entio.web.ai

import com.entio.web.contract.DevelopmentAuthorization
import com.entio.web.contract.WebAiTaskCommandRequest
import com.entio.web.contract.WebAiTaskCreateRequest
import com.entio.web.contract.WebRole
import com.entio.web.contract.WebSessionUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiTaskWebContractTest {
    private val alice = WebSessionUser("alice", "Alice", "A", WebRole.CONTRIBUTOR)
    private val bob = WebSessionUser("bob", "Bob", "B", WebRole.REVIEWER)

    @Test
    fun createReadWorkspaceResourcesCommandsAndIdempotencyAreScopedAndRevisionChecked(): Unit {
        val boundary = fixture()
        val request = WebAiTaskCreateRequest("conversation-1", "Explain the Account class.", listOf("simple"))
        val created = boundary.create(alice, "simple", request, "create-1")
        val replay = boundary.create(alice, "simple", request, "create-1")

        assertEquals(created, replay)
        assertEquals("READY_TO_EXECUTE", created.task.status)
        assertEquals(created, boundary.get(alice, "simple", created.task.id))
        assertEquals("project-fingerprint", boundary.workspace(alice, "simple", created.task.id).workspace.projectFingerprint)
        assertFalse(boundary.resource(alice, "simple", created.task.id, "draft").available)
        assertEquals("idempotency-conflict", assertFailsWith<AiTaskLifecycleFailure> {
            boundary.create(alice, "simple", request.copy(objective = "Different"), "create-1")
        }.code)
        assertEquals("missing-ai-task", assertFailsWith<AiStateAccessFailure> {
            boundary.get(bob, "simple", created.task.id)
        }.code)

        val paused = boundary.command(alice, "simple", created.task.id, "pause", WebAiTaskCommandRequest(0), "pause-1")
        assertEquals("PAUSED", paused.task.status)
        assertEquals(paused, boundary.command(alice, "simple", created.task.id, "pause", WebAiTaskCommandRequest(0), "pause-1"))
        assertEquals("ai-task-revision-conflict", assertFailsWith<AiStateAccessFailure> {
            boundary.command(alice, "simple", created.task.id, "resume", WebAiTaskCommandRequest(0), "resume-stale")
        }.code)
        val resumed = boundary.command(alice, "simple", created.task.id, "resume", WebAiTaskCommandRequest(1), "resume-1")
        assertEquals("READY_TO_EXECUTE", resumed.task.status)
        val cancelled = boundary.command(alice, "simple", created.task.id, "cancel", WebAiTaskCommandRequest(2), "cancel-1")
        assertEquals("CANCELLED", cancelled.task.status)
        assertTrue(boundary.events(alice, "simple", created.task.id, 0).terminal)
    }

    @Test
    fun eventsAreOrderedBoundedPrivateAndSignalRetentionGaps(): Unit {
        val boundary = fixture()
        val task = boundary.create(alice, "simple", WebAiTaskCreateRequest("c", "Explain Account.", listOf("simple")), "create").task
        repeat(205) { index ->
            boundary.command(alice, "simple", task.id, "messages", WebAiTaskCommandRequest(0, message = "message-$index"), "message-$index")
        }
        val window = boundary.events(alice, "simple", task.id, 0)
        assertEquals(200, window.events.size)
        assertEquals(window.events.map { it.sequence }.sorted(), window.events.map { it.sequence })
        assertTrue(boundary.events(alice, "simple", task.id, 1).resynchronizationRequired)
        assertFailsWith<AiStateAccessFailure> { boundary.events(bob, "simple", task.id, 0) }
        assertTrue(window.events.none { it.toString().contains("credential", ignoreCase = true) })
    }

    private fun fixture(): AiTaskWebBoundary {
        val store = InMemoryAiTaskStore()
        val binding = object : AiRunModelBindingResolver {
            override fun resolve(userId: String): AiRunModelBinding = AiRunModelBinding("openai", "gpt-test", "catalog", 1, "prompt", "policy")
        }
        var id = 0
        return AiTaskWebBoundary(
            store,
            AiTaskLifecycleService(store, idFactory = { "id-${++id}" }),
            DevelopmentAuthorization(),
            binding,
            projectFingerprint = { _, _ -> "project-fingerprint" },
        )
    }
}
