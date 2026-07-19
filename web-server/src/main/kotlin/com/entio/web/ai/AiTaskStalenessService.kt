package com.entio.web.ai

import java.time.Clock

public class AiTaskStalenessService(
    private val store: AiTaskStore,
    private val cache: AiContextCache,
    private val clock: Clock = Clock.systemUTC(),
) {
    public fun observe(
        userId: String,
        projectId: String,
        taskId: String,
        expectedRevision: Long,
        currentProjectFingerprint: String,
    ): AiTaskWorkspace {
        val current = store.get(userId, projectId, taskId)
        if (current.currentProjectFingerprint == currentProjectFingerprint) return current
        cache.invalidateProject(projectId)
        return update(current, expectedRevision, AiTaskStatus.STALE)
    }

    public fun refreshForRevalidation(
        userId: String,
        projectId: String,
        taskId: String,
        expectedRevision: Long,
        currentProjectFingerprint: String,
        meaningChanged: Boolean,
    ): AiTaskWorkspace {
        val current = store.get(userId, projectId, taskId)
        if (current.task.status != AiTaskStatus.STALE) throw AiIncrementalAnalysisFailure("task-not-stale", "Only a stale task can refresh.")
        return update(
            current,
            expectedRevision,
            if (meaningChanged) AiTaskStatus.AWAITING_PLAN_CONFIRMATION else AiTaskStatus.VALIDATING,
            currentProjectFingerprint,
        )
    }

    private fun update(
        current: AiTaskWorkspace,
        expectedRevision: Long,
        status: AiTaskStatus,
        projectFingerprint: String = current.currentProjectFingerprint,
    ): AiTaskWorkspace {
        val now = clock.instant()
        return store.update(
            current.task.userId, current.task.projectId, current.task.id, expectedRevision,
            current.copy(
                task = current.task.copy(status = status, updatedAt = now),
                revision = expectedRevision + 1,
                currentProjectFingerprint = projectFingerprint,
                updatedAt = now,
            ),
        )
    }
}
