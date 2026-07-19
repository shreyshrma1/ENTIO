package com.entio.web.ai

public data class AiDraftBatchEntry(
    val capabilityName: String,
    val arguments: AiAddDraftItemArguments,
)

public data class AiDraftBatchRequest(
    val taskId: String,
    val workPackageId: String,
    val executionSegmentId: String,
    val providerRunId: String,
    val expectedTaskRevision: Long,
    val entries: List<AiDraftBatchEntry>,
)

public class AiDraftBatchFailure(public val code: String, message: String) : IllegalArgumentException(message)

public class AiDraftBatchService(
    private val taskStore: AiTaskStore,
    private val drafts: AiPrivateDraftWorkspace,
) {
    public fun append(scope: AiCapabilityScope, draftId: String, request: AiDraftBatchRequest): AiDraft {
        val workspace = taskStore.get(scope.userId, scope.projectId, request.taskId)
        if (workspace.revision != request.expectedTaskRevision) {
            throw AiDraftBatchFailure("stale-task-batch", "The task changed before the draft batch was appended.")
        }
        if (workspace.task.status != AiTaskStatus.EXECUTING || workspace.currentWorkPackageId != request.workPackageId) {
            throw AiDraftBatchFailure("task-package-not-executing", "The requested work package is not executing.")
        }
        if (workspace.executionSegments.last().id != request.executionSegmentId) {
            throw AiDraftBatchFailure("execution-segment-mismatch", "The batch does not belong to the active execution segment.")
        }
        if (request.entries.size > workspace.task.policy.maxDraftItemsPerBatch) {
            throw AiDraftBatchFailure("task-batch-limit", "The task batch exceeds its item limit.")
        }
        val currentDraft = drafts.read(scope, draftId)
        if (currentDraft.items.size + request.entries.size > workspace.task.policy.maxDraftItems) {
            throw AiDraftBatchFailure("task-draft-limit", "The task draft exceeds its item limit.")
        }
        return drafts.addBatch(
            scope,
            draftId,
            request.entries,
            AiDraftAttribution(
                acceptingUserId = scope.userId,
                conversationId = scope.conversationId,
                runId = request.providerRunId,
                taskId = request.taskId,
                workPackageId = request.workPackageId,
                executionSegmentId = request.executionSegmentId,
            ),
        )
    }
}
