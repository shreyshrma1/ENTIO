package com.entio.web.ai

import java.time.Clock
import java.time.Instant
import java.util.UUID

public class AiTaskLifecycleFailure(
    public val code: String,
    message: String,
) : IllegalArgumentException(message)

public data class AiTaskCreationRequest(
    val objective: String,
    val scope: AiTaskScopeSnapshot,
    val modelBinding: AiRunModelBinding,
)

/** Owns lifecycle policy; callers and models cannot choose task status or override limits. */
public class AiTaskLifecycleService(
    private val store: AiTaskStore,
    private val classifier: AiTaskClassifier = AiTaskClassifier(),
    private val policy: AiTaskPolicy = AiTaskPolicy(),
    private val clock: Clock = Clock.systemUTC(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    public fun create(request: AiTaskCreationRequest): AiTaskWorkspace {
        val classification = classifier.classify(request.objective)
        requireCapacity(request.scope, classification.mutating)
        val now = clock.instant()
        val segment = AiTaskExecutionSegment(
            id = idFactory(),
            ordinal = 1,
            modelBinding = request.modelBinding,
            createdAt = now,
        )
        val task = AiTask(
            id = idFactory(),
            userId = request.scope.userId,
            projectId = request.scope.projectId,
            conversationId = request.scope.conversationId,
            objective = request.objective,
            type = classification.type,
            size = classification.size,
            scope = request.scope,
            initialExecutionSegment = segment,
            policy = policy,
            status = if (classification.requiresPlanning) AiTaskStatus.PLANNING else AiTaskStatus.READY_TO_EXECUTE,
            createdAt = now,
            updatedAt = now,
        )
        return store.create(AiTaskWorkspace(task = task))
    }

    public fun pause(
        userId: String,
        projectId: String,
        taskId: String,
        expectedRevision: Long,
        code: String = "user-paused",
        message: String = "The AI task was paused.",
    ): AiTaskWorkspace = pauseInternal(userId, projectId, taskId, expectedRevision, code, message, false)

    public fun markModelUnavailable(
        userId: String,
        projectId: String,
        taskId: String,
        expectedRevision: Long,
    ): AiTaskWorkspace = pauseInternal(
        userId,
        projectId,
        taskId,
        expectedRevision,
        "model-unavailable",
        "The bound model is unavailable. Select and confirm a newly verified model to resume.",
        true,
    )

    public fun resume(
        userId: String,
        projectId: String,
        taskId: String,
        expectedRevision: Long,
    ): AiTaskWorkspace {
        val current = store.get(userId, projectId, taskId)
        val pause = current.pause ?: throw AiTaskLifecycleFailure("ai-task-not-paused", "The AI task is not paused.")
        if (pause.requiresModelRebind) {
            throw AiTaskLifecycleFailure("ai-task-model-rebind-required", "A confirmed model rebind is required.")
        }
        return updateStatus(current, expectedRevision, pause.resumeStatus, pause = null)
    }

    public fun rebindAndResume(
        userId: String,
        projectId: String,
        taskId: String,
        expectedRevision: Long,
        binding: AiRunModelBinding,
        userConfirmedVerifiedSelection: Boolean,
    ): AiTaskWorkspace {
        if (!userConfirmedVerifiedSelection) {
            throw AiTaskLifecycleFailure("ai-task-model-rebind-unconfirmed", "The new model selection must be verified and confirmed.")
        }
        val current = store.get(userId, projectId, taskId)
        val pause = current.pause ?: throw AiTaskLifecycleFailure("ai-task-not-paused", "The AI task is not paused.")
        if (!pause.requiresModelRebind) {
            throw AiTaskLifecycleFailure("ai-task-model-rebind-not-required", "The paused task does not require a model rebind.")
        }
        val now = clock.instant()
        val completed = current.executionSegments.mapIndexed { index, segment ->
            if (index == current.executionSegments.lastIndex && segment.completedAt == null) segment.copy(completedAt = now) else segment
        }
        val next = AiTaskExecutionSegment(
            id = idFactory(),
            ordinal = completed.size + 1,
            modelBinding = binding,
            createdAt = now,
        )
        return updateStatus(
            current = current,
            expectedRevision = expectedRevision,
            status = pause.resumeStatus,
            pause = null,
            executionSegments = completed + next,
        )
    }

    public fun cancel(userId: String, projectId: String, taskId: String, expectedRevision: Long): AiTaskWorkspace {
        val current = store.get(userId, projectId, taskId)
        return updateStatus(current, expectedRevision, AiTaskStatus.CANCELLED, pause = null)
    }

    public fun markStale(userId: String, projectId: String, taskId: String, expectedRevision: Long): AiTaskWorkspace {
        val current = store.get(userId, projectId, taskId)
        return updateStatus(current, expectedRevision, AiTaskStatus.STALE, pause = null)
    }

    public fun recordLimit(
        userId: String,
        projectId: String,
        taskId: String,
        expectedRevision: Long,
        kind: String,
        maximum: Long,
        observed: Long,
    ): AiTaskWorkspace {
        val current = store.get(userId, projectId, taskId)
        val now = clock.instant()
        return updateStatus(
            current,
            expectedRevision,
            AiTaskStatus.LIMIT_REACHED,
            pause = null,
            limits = current.limits + AiTaskLimitRecord(kind, maximum, observed, now),
        )
    }

    private fun pauseInternal(
        userId: String,
        projectId: String,
        taskId: String,
        expectedRevision: Long,
        code: String,
        message: String,
        requiresModelRebind: Boolean,
    ): AiTaskWorkspace {
        val current = store.get(userId, projectId, taskId)
        if (current.task.status.terminal) {
            throw AiTaskLifecycleFailure("ai-task-terminal", "A terminal AI task cannot be paused.")
        }
        val now = clock.instant()
        return updateStatus(
            current,
            expectedRevision,
            AiTaskStatus.PAUSED,
            AiTaskPause(code, message, current.task.status, requiresModelRebind, now),
        )
    }

    private fun requireCapacity(scope: AiTaskScopeSnapshot, mutating: Boolean) {
        val active = store.list(scope.userId, scope.projectId).filter { !it.task.status.terminal && it.task.status != AiTaskStatus.LIMIT_REACHED }
        val count = active.count { (it.task.type in AiTaskClassifier.mutatingTypes) == mutating }
        val maximum = if (mutating) policy.maxActiveMutatingTasksPerUserProject else policy.maxConcurrentReadOnlyTasksPerUserProject
        if (count >= maximum) {
            throw AiTaskLifecycleFailure("ai-task-concurrency-limit", "The active AI task limit has been reached.")
        }
    }

    private fun updateStatus(
        current: AiTaskWorkspace,
        expectedRevision: Long,
        status: AiTaskStatus,
        pause: AiTaskPause?,
        executionSegments: List<AiTaskExecutionSegment> = current.executionSegments,
        limits: List<AiTaskLimitRecord> = current.limits,
    ): AiTaskWorkspace {
        val now = clock.instant()
        return store.update(
            current.task.userId,
            current.task.projectId,
            current.task.id,
            expectedRevision,
            current.copy(
                task = current.task.copy(status = status, updatedAt = now),
                revision = expectedRevision + 1,
                pause = pause,
                executionSegments = executionSegments,
                limits = limits,
                updatedAt = now,
            ),
        )
    }
}
