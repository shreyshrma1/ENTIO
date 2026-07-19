package com.entio.web.ai

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AiTaskLifecycleServiceTest {
    private val now = Instant.parse("2026-07-19T15:00:00Z")
    private val store = InMemoryAiTaskStore()
    private var nextId = 0
    private val service = AiTaskLifecycleService(
        store = store,
        clock = Clock.fixed(now, ZoneOffset.UTC),
        idFactory = { "generated-${++nextId}" },
    )

    @Test
    fun createsSimpleAndPlannedTasksWithServerChosenStatus(): Unit {
        val simple = service.create(request("Explain Account", conversationId = "conversation-simple"))
        val planned = service.create(request("Analyze project structure", conversationId = "conversation-planned"))

        assertEquals(AiTaskStatus.READY_TO_EXECUTE, simple.task.status)
        assertEquals(AiTaskStatus.PLANNING, planned.task.status)
    }

    @Test
    fun enforcesOneMutatingAndThreeReadOnlyTasksPerUserProject(): Unit {
        service.create(request("Add a definition to Account", conversationId = "mutating-1"))
        assertEquals(
            "ai-task-concurrency-limit",
            assertFailsWith<AiTaskLifecycleFailure> {
                service.create(request("Rename Customer", conversationId = "mutating-2"))
            }.code,
        )

        repeat(3) { index -> service.create(request("Explain concept $index", conversationId = "read-$index")) }
        assertEquals(
            "ai-task-concurrency-limit",
            assertFailsWith<AiTaskLifecycleFailure> {
                service.create(request("Explain one more concept", conversationId = "read-4"))
            }.code,
        )
    }

    @Test
    fun pauseResumeAndCancelPreserveWorkspaceAndEnforceTerminalState(): Unit {
        val created = service.create(request("Explain Account"))
        val paused = service.pause("alice", "simple", created.task.id, created.revision)
        assertEquals(AiTaskStatus.PAUSED, paused.task.status)
        assertSame(created.task.initialExecutionSegment, paused.task.initialExecutionSegment)

        val resumed = service.resume("alice", "simple", paused.task.id, paused.revision)
        assertEquals(AiTaskStatus.READY_TO_EXECUTE, resumed.task.status)
        assertNull(resumed.pause)

        val cancelled = service.cancel("alice", "simple", resumed.task.id, resumed.revision)
        assertEquals(AiTaskStatus.CANCELLED, cancelled.task.status)
        assertFailsWith<AiTaskLifecycleFailure> {
            service.pause("alice", "simple", cancelled.task.id, cancelled.revision)
        }
    }

    @Test
    fun unavailableModelRequiresConfirmedRebindAndPreservesProvenance(): Unit {
        val created = service.create(request("Explain Account"))
        val paused = service.markModelUnavailable("alice", "simple", created.task.id, created.revision)

        assertEquals("ai-task-model-rebind-required", assertFailsWith<AiTaskLifecycleFailure> {
            service.resume("alice", "simple", paused.task.id, paused.revision)
        }.code)
        assertEquals("ai-task-model-rebind-unconfirmed", assertFailsWith<AiTaskLifecycleFailure> {
            service.rebindAndResume(
                "alice", "simple", paused.task.id, paused.revision, binding("gpt-new"), false,
            )
        }.code)

        val resumed = service.rebindAndResume(
            "alice", "simple", paused.task.id, paused.revision, binding("gpt-new"), true,
        )
        assertEquals(2, resumed.executionSegments.size)
        assertEquals("gpt-test", resumed.executionSegments[0].modelBinding.modelId)
        assertEquals(now, resumed.executionSegments[0].completedAt)
        assertEquals("gpt-new", resumed.executionSegments[1].modelBinding.modelId)
        assertEquals(created.task.initialExecutionSegment, resumed.task.initialExecutionSegment)
    }

    @Test
    fun staleAndLimitStatesPreserveWorkspaceEvidence(): Unit {
        val staleSource = service.create(request("Explain Account"))
        val stale = service.markStale("alice", "simple", staleSource.task.id, staleSource.revision)
        assertEquals(AiTaskStatus.STALE, stale.task.status)

        val limitSource = service.create(request("Explain Invoice", conversationId = "limit"))
        val limited = service.recordLimit(
            "alice", "simple", limitSource.task.id, limitSource.revision, "tool-calls", 200, 200,
        )
        assertEquals(AiTaskStatus.LIMIT_REACHED, limited.task.status)
        assertEquals("tool-calls", limited.limits.single().kind)
        assertTrue(limited.executionSegments.isNotEmpty())
    }

    @Test
    fun forbiddenRequestNeverCreatesWorkspace(): Unit {
        assertFailsWith<AiTaskClassificationFailure> { service.create(request("Approve my own draft")) }
        assertTrue(store.list("alice", "simple").isEmpty())
    }

    private fun request(objective: String, conversationId: String = "conversation-1") = AiTaskCreationRequest(
        objective = objective,
        scope = scope(conversationId),
        modelBinding = binding("gpt-test"),
    )

    private fun scope(conversationId: String) = AiTaskScopeSnapshot(
        userId = "alice",
        projectId = "simple",
        conversationId = conversationId,
        allowedSourceIds = listOf("simple"),
        projectFingerprint = "fingerprint-1",
        role = "CONTRIBUTOR",
        permissions = setOf("USE_AI"),
        availableFeatures = setOf("PRIVATE_DRAFT"),
        capturedAt = now,
    )

    private fun binding(modelId: String) = AiRunModelBinding(
        providerId = "openai",
        modelId = modelId,
        catalogVersion = "catalog-1",
        credentialGeneration = 1,
        promptVersion = "prompt-1",
        requestPolicyVersion = "policy-1",
    )
}
