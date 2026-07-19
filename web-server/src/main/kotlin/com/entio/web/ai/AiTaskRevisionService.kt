package com.entio.web.ai

import java.time.Clock

public class AiTaskRevisionService(
    private val taskStore: AiTaskStore,
    private val drafts: AiPrivateDraftWorkspace,
    private val clock: Clock = Clock.systemUTC(),
) {
    public fun undoLatest(scope: AiCapabilityScope, taskId: String, draftId: String): AiDraft {
        taskStore.get(scope.userId, scope.projectId, taskId)
        return drafts.undo(scope, draftId, AiUndoDraftArguments("Undo the latest task draft revision."))
    }

    public fun undoPackage(scope: AiCapabilityScope, taskId: String, draftId: String, packageId: String): AiDraft {
        taskStore.get(scope.userId, scope.projectId, taskId)
        val draft = drafts.read(scope, draftId)
        val last = draft.revisions.lastOrNull() ?: throw AiRepairFailure("missing-draft-revision", "The task draft has no revision.")
        val added = last.afterItems.filter { item -> item.id !in last.beforeItems.map(AiDraftItem::id) }
        if (added.isEmpty() || added.any { it.attribution?.workPackageId != packageId }) {
            throw AiRepairFailure("package-undo-not-latest", "Only the latest complete package revision can be undone safely.")
        }
        return drafts.undo(scope, draftId, AiUndoDraftArguments("Undo task package '$packageId'."))
    }

    public fun removeItem(scope: AiCapabilityScope, taskId: String, draftId: String, itemId: String): AiDraft {
        taskStore.get(scope.userId, scope.projectId, taskId)
        return drafts.remove(scope, draftId, AiRemoveDraftItemArguments(itemId, "Remove the proposed task item."))
    }

    public fun changeAssumption(
        userId: String,
        projectId: String,
        taskId: String,
        expectedRevision: Long,
        assumptionId: String,
        statement: String,
    ): AiTaskWorkspace {
        val current = taskStore.get(userId, projectId, taskId)
        if (statement.isBlank()) throw AiRepairFailure("assumption-required", "An updated assumption is required.")
        if (current.assumptions.none { it.id == assumptionId }) throw AiRepairFailure("missing-assumption", "The assumption was not found.")
        val now = clock.instant()
        return taskStore.update(
            userId, projectId, taskId, expectedRevision,
            current.copy(
                task = current.task.copy(status = AiTaskStatus.VALIDATING, updatedAt = now),
                revision = expectedRevision + 1,
                assumptions = current.assumptions.map { if (it.id == assumptionId) it.copy(statement = statement.trim(), recordedAt = now) else it },
                updatedAt = now,
            ),
        )
    }

    public fun revisePackage(
        userId: String,
        projectId: String,
        taskId: String,
        expectedRevision: Long,
        packageId: String,
    ): AiTaskWorkspace = updateStatus(userId, projectId, taskId, expectedRevision, AiTaskStatus.PLANNING, packageId)

    public fun rerunAnalysis(
        userId: String,
        projectId: String,
        taskId: String,
        expectedRevision: Long,
    ): AiTaskWorkspace = updateStatus(userId, projectId, taskId, expectedRevision, AiTaskStatus.VALIDATING)

    private fun updateStatus(
        userId: String,
        projectId: String,
        taskId: String,
        expectedRevision: Long,
        status: AiTaskStatus,
        packageId: String? = null,
    ): AiTaskWorkspace {
        val current = taskStore.get(userId, projectId, taskId)
        val now = clock.instant()
        return taskStore.update(
            userId, projectId, taskId, expectedRevision,
            current.copy(
                task = current.task.copy(status = status, updatedAt = now),
                revision = expectedRevision + 1,
                currentWorkPackageId = packageId ?: current.currentWorkPackageId,
                updatedAt = now,
            ),
        )
    }
}
