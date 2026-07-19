package com.entio.web.ai

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AiTaskStoreTest {
    @Test
    fun createNormalizesDeterministicWorkspaceCollections(): Unit {
        val store = InMemoryAiTaskStore()
        val task = taskFixture()
        val now = task.createdAt
        val workspace = AiTaskWorkspace(
            task = task,
            assumptions = listOf(
                AiTaskAssumption("b", "Second", now),
                AiTaskAssumption("a", "First", now),
                AiTaskAssumption("a", "Duplicate", now),
            ),
            selectedEntities = listOf(
                AiTaskEntityReference("https://example.com/B", "B", "CLASS", "simple"),
                AiTaskEntityReference("https://example.com/A", "A", "CLASS", "simple"),
            ),
            analysisReferences = AiTaskAnalysisReferences(validationReferenceIds = listOf("v2", "v1", "v2")),
        )

        val created = store.create(workspace)

        assertEquals(listOf("shapes", "simple"), created.task.scope.allowedSourceIds)
        assertEquals(listOf("a", "b"), created.assumptions.map(AiTaskAssumption::id))
        assertEquals(listOf("A", "B"), created.selectedEntities.map(AiTaskEntityReference::label))
        assertEquals(listOf("v1", "v2"), created.analysisReferences.validationReferenceIds)
        assertEquals(created, store.get("alice", "simple", "task-1"))
    }

    @Test
    fun compareAndSetAdvancesExactlyOneRevisionAndEnforcesTransitions(): Unit {
        val store = InMemoryAiTaskStore()
        val current = store.create(AiTaskWorkspace(taskFixture()))
        val updatedAt = Instant.parse("2026-07-19T12:01:00Z")
        val replacement = current.copy(
            task = current.task.copy(status = AiTaskStatus.PLANNING, updatedAt = updatedAt),
            revision = 1,
            updatedAt = updatedAt,
        )

        val updated = store.update("alice", "simple", "task-1", 0, replacement)

        assertEquals(1, updated.revision)
        assertEquals(AiTaskStatus.PLANNING, updated.task.status)
        val conflict = assertFailsWith<AiStateAccessFailure> {
            store.update("alice", "simple", "task-1", 0, replacement)
        }
        assertEquals("ai-task-revision-conflict", conflict.code)

        val invalid = updated.copy(
            task = updated.task.copy(status = AiTaskStatus.SUBMITTED_FOR_REVIEW),
            revision = 2,
        )
        val transition = assertFailsWith<AiTaskTransitionFailure> {
            store.update("alice", "simple", "task-1", 1, invalid)
        }
        assertEquals("invalid-ai-task-transition", transition.code)
    }

    @Test
    fun updateRejectsIdentityScopePolicyAndInitialBindingChanges(): Unit {
        val store = InMemoryAiTaskStore()
        val current = store.create(AiTaskWorkspace(taskFixture()))
        val changedConversationTask = current.task.copy(
            conversationId = "other",
            scope = current.task.scope.copy(conversationId = "other"),
        )
        val changedBindingTask = current.task.copy(
            initialExecutionSegment = current.task.initialExecutionSegment.copy(
                modelBinding = current.task.initialExecutionSegment.modelBinding.copy(modelId = "other"),
            ),
        )
        val replacements = listOf(
            current.copy(task = changedConversationTask, revision = 1),
            current.copy(task = current.task.copy(scope = current.task.scope.copy(projectFingerprint = "other")), revision = 1),
            current.copy(task = current.task.copy(policy = AiTaskPolicy(maxWorkPackages = 11)), revision = 1),
            current.copy(
                task = changedBindingTask,
                executionSegments = listOf(changedBindingTask.initialExecutionSegment),
                revision = 1,
            ),
        )

        replacements.forEach { replacement ->
            val failure = assertFailsWith<AiStateAccessFailure> {
                store.update("alice", "simple", "task-1", 0, replacement)
            }
            assertEquals("ai-task-scope-violation", failure.code)
        }
    }

    @Test
    fun ownershipFailuresDoNotDiscloseWhetherTaskExists(): Unit {
        val store = InMemoryAiTaskStore()
        store.create(AiTaskWorkspace(taskFixture()))

        listOf(
            Triple("bob", "simple", "task-1"),
            Triple("alice", "other", "task-1"),
            Triple("alice", "simple", "missing"),
        ).forEach { (userId, projectId, taskId) ->
            val failure = assertFailsWith<AiStateAccessFailure> {
                store.get(userId, projectId, taskId)
            }
            assertEquals("missing-ai-task", failure.code)
            assertEquals("The AI task was not found.", failure.message)
        }
    }

    @Test
    fun terminalTaskCannotAdvanceItsWorkspaceRevision(): Unit {
        val store = InMemoryAiTaskStore()
        val current = store.create(AiTaskWorkspace(taskFixture(status = AiTaskStatus.CANCELLED)))

        val failure = assertFailsWith<AiTaskTransitionFailure> {
            store.update("alice", "simple", "task-1", 0, current.copy(revision = 1))
        }

        assertEquals("ai-task-mutation-not-allowed", failure.code)
    }

    @Test
    fun aNewStoreDoesNotReconstructTasksFromConversationState(): Unit {
        val first = InMemoryAiTaskStore()
        first.create(AiTaskWorkspace(taskFixture()))

        val restarted = InMemoryAiTaskStore()

        assertEquals(emptyList(), restarted.list("alice", "simple"))
        val failure = assertFailsWith<AiStateAccessFailure> {
            restarted.get("alice", "simple", "task-1")
        }
        assertEquals("missing-ai-task", failure.code)
    }
}
