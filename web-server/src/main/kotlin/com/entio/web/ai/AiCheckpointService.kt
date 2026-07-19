package com.entio.web.ai

import java.time.Clock
import java.time.Instant
import java.util.UUID

public enum class AiCheckpointAction { CONTINUE, REVISE, ANSWER, PAUSE, CANCEL }

public data class AiTaskCheckpoint(
    val id: String,
    val taskId: String,
    val workPackageId: String?,
    val taskRevision: Long,
    val actions: Set<AiCheckpointAction>,
    val createdAt: Instant,
)

public class AiCheckpointFailure(public val code: String, message: String) : IllegalArgumentException(message)

public class AiCheckpointService(
    private val store: AiTaskStore,
    private val clock: Clock = Clock.systemUTC(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val checkpoints: MutableMap<String, AiTaskCheckpoint> = linkedMapOf()
    private val clarificationResumeStatuses: MutableMap<String, AiTaskStatus> = linkedMapOf()

    public fun create(
        userId: String,
        projectId: String,
        taskId: String,
        actions: Set<AiCheckpointAction> = AiCheckpointAction.entries.toSet(),
    ): AiTaskCheckpoint {
        val workspace = store.get(userId, projectId, taskId)
        val checkpoint = AiTaskCheckpoint(
            idFactory(), taskId, workspace.currentWorkPackageId, workspace.revision, actions, clock.instant(),
        )
        checkpoints[checkpoint.id] = checkpoint
        return checkpoint
    }

    public fun act(
        userId: String,
        projectId: String,
        taskId: String,
        checkpointId: String,
        expectedRevision: Long,
        action: AiCheckpointAction,
    ): AiTaskWorkspace {
        val current = store.get(userId, projectId, taskId)
        val checkpoint = checkpoints[checkpointId]
            ?: throw AiCheckpointFailure("checkpoint-replay", "The checkpoint is missing or already answered.")
        if (checkpoint.taskId != taskId || checkpoint.taskRevision != expectedRevision || current.revision != expectedRevision) {
            throw AiCheckpointFailure("stale-checkpoint", "The task changed before the checkpoint action.")
        }
        if (action !in checkpoint.actions || action == AiCheckpointAction.ANSWER) {
            throw AiCheckpointFailure("checkpoint-action-invalid", "This checkpoint does not allow that action.")
        }
        val status = when (action) {
            AiCheckpointAction.CONTINUE -> current.task.status
            AiCheckpointAction.REVISE -> AiTaskStatus.PLANNING
            AiCheckpointAction.PAUSE -> AiTaskStatus.PAUSED
            AiCheckpointAction.CANCEL -> AiTaskStatus.CANCELLED
            AiCheckpointAction.ANSWER -> error("handled above")
        }
        val pause = if (action == AiCheckpointAction.PAUSE) {
            AiTaskPause("checkpoint-paused", "The task was paused at a checkpoint.", current.task.status, createdAt = clock.instant())
        } else {
            current.pause
        }
        val result = update(current, expectedRevision, status, pause = pause)
        checkpoints.remove(checkpointId)
        return result
    }

    public fun ask(
        userId: String,
        projectId: String,
        taskId: String,
        expectedRevision: Long,
        workPackageId: String?,
        question: String,
    ): AiTaskWorkspace {
        val current = store.get(userId, projectId, taskId)
        if (question.isBlank()) throw AiCheckpointFailure("clarification-question-required", "A clarification question is required.")
        if (current.openQuestions.size >= 10) throw AiCheckpointFailure("clarification-question-limit", "The open-question limit was reached.")
        clarificationResumeStatuses[taskId] = current.task.status
        val item = AiTaskOpenQuestion(idFactory(), question.trim(), workPackageId, clock.instant())
        return update(
            current, expectedRevision, AiTaskStatus.AWAITING_CLARIFICATION,
            openQuestions = current.openQuestions + item,
            currentWorkPackageId = workPackageId,
        )
    }

    public fun answer(
        userId: String,
        projectId: String,
        taskId: String,
        expectedRevision: Long,
        questionId: String,
        answer: String,
    ): AiTaskWorkspace {
        val current = store.get(userId, projectId, taskId)
        val question = current.openQuestions.firstOrNull { it.id == questionId }
            ?: throw AiCheckpointFailure("clarification-replay", "The clarification question is missing or already answered.")
        if (answer.isBlank()) throw AiCheckpointFailure("clarification-answer-required", "A clarification answer is required.")
        val resume = clarificationResumeStatuses[taskId]
            ?: throw AiCheckpointFailure("clarification-resume-missing", "The clarification resume state is unavailable.")
        val assumption = AiTaskAssumption(idFactory(), "${question.question} Answer: ${answer.trim()}", clock.instant())
        val result = update(
            current, expectedRevision, resume,
            assumptions = (current.assumptions + assumption).takeLast(10),
            openQuestions = current.openQuestions.filterNot { it.id == questionId },
            currentWorkPackageId = question.workPackageId,
        )
        clarificationResumeStatuses.remove(taskId)
        return result
    }

    private fun update(
        current: AiTaskWorkspace,
        expectedRevision: Long,
        status: AiTaskStatus,
        pause: AiTaskPause? = current.pause,
        assumptions: List<AiTaskAssumption> = current.assumptions,
        openQuestions: List<AiTaskOpenQuestion> = current.openQuestions,
        currentWorkPackageId: String? = current.currentWorkPackageId,
    ): AiTaskWorkspace {
        val now = clock.instant()
        return store.update(
            current.task.userId, current.task.projectId, current.task.id, expectedRevision,
            current.copy(
                task = current.task.copy(status = status, updatedAt = now), revision = expectedRevision + 1,
                pause = pause, assumptions = assumptions, openQuestions = openQuestions,
                currentWorkPackageId = currentWorkPackageId, updatedAt = now,
            ),
        )
    }
}
