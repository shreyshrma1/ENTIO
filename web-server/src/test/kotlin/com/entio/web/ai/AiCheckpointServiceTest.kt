package com.entio.web.ai

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AiCheckpointServiceTest {
    private val now = Instant.parse("2026-07-19T19:00:00Z")

    @Test
    fun clarificationAnswerResumesSamePackageAndCannotReplay(): Unit {
        val fixture = fixture(AiTaskStatus.EXECUTING, "package-a")
        val awaiting = fixture.service.ask("alice", "simple", "task-1", 0, "package-a", "Which Account is intended?")
        val question = awaiting.openQuestions.single()
        assertEquals(AiTaskStatus.AWAITING_CLARIFICATION, awaiting.task.status)

        val resumed = fixture.service.answer("alice", "simple", "task-1", 1, question.id, "The banking Account class")
        assertEquals(AiTaskStatus.EXECUTING, resumed.task.status)
        assertEquals("package-a", resumed.currentWorkPackageId)
        assertTrue(resumed.assumptions.single().statement.contains("banking Account"))
        assertEquals("clarification-replay", assertFailsWith<AiCheckpointFailure> {
            fixture.service.answer("alice", "simple", "task-1", 2, question.id, "again")
        }.code)
    }

    @Test
    fun checkpointActionsAreRevisionCheckedAndSingleUse(): Unit {
        val fixture = fixture(AiTaskStatus.EXECUTING, "package-a")
        val checkpoint = fixture.service.create("alice", "simple", "task-1")
        assertEquals("stale-checkpoint", assertFailsWith<AiCheckpointFailure> {
            fixture.service.act("alice", "simple", "task-1", checkpoint.id, 1, AiCheckpointAction.CONTINUE)
        }.code)
        val continued = fixture.service.act("alice", "simple", "task-1", checkpoint.id, 0, AiCheckpointAction.CONTINUE)
        assertEquals(AiTaskStatus.EXECUTING, continued.task.status)
        assertEquals("checkpoint-replay", assertFailsWith<AiCheckpointFailure> {
            fixture.service.act("alice", "simple", "task-1", checkpoint.id, 1, AiCheckpointAction.CONTINUE)
        }.code)
    }

    @Test
    fun revisePauseAndCancelActionsUseTaskLifecycleStates(): Unit {
        assertEquals(AiTaskStatus.PLANNING, act(AiCheckpointAction.REVISE).task.status)
        assertEquals(AiTaskStatus.PAUSED, act(AiCheckpointAction.PAUSE).task.status)
        assertEquals(AiTaskStatus.CANCELLED, act(AiCheckpointAction.CANCEL).task.status)
    }

    private fun act(action: AiCheckpointAction): AiTaskWorkspace {
        val fixture = fixture(AiTaskStatus.EXECUTING, "package-a")
        val checkpoint = fixture.service.create("alice", "simple", "task-1")
        return fixture.service.act("alice", "simple", "task-1", checkpoint.id, 0, action)
    }

    private fun fixture(status: AiTaskStatus, packageId: String?): CheckpointFixture {
        val store = InMemoryAiTaskStore()
        store.create(AiTaskWorkspace(taskFixture(status = status), currentWorkPackageId = packageId))
        var id = 0
        return CheckpointFixture(
            AiCheckpointService(store, Clock.fixed(now, ZoneOffset.UTC), idFactory = { "checkpoint-${++id}" }),
        )
    }

    private data class CheckpointFixture(val service: AiCheckpointService)
}
