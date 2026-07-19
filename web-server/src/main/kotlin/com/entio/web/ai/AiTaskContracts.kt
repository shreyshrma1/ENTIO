package com.entio.web.ai

import java.time.Instant

public enum class AiTaskType {
    EXPLANATION,
    SEARCH_AND_DISCOVERY,
    FOCUSED_EDIT,
    MULTI_EDIT_CHANGE,
    REFACTORING,
    DOMAIN_MODELING,
    REPAIR,
    REVIEW,
    PROJECT_ANALYSIS,
}

public enum class AiTaskSize {
    SIMPLE,
    MEDIUM,
    LARGE,
}

public enum class AiTaskStatus {
    UNDERSTANDING,
    AWAITING_CLARIFICATION,
    PLANNING,
    AWAITING_PLAN_CONFIRMATION,
    READY_TO_EXECUTE,
    EXECUTING,
    VALIDATING,
    RUNNING_REASONING,
    RUNNING_SHACL,
    REPAIRING,
    PAUSED,
    READY_FOR_REVIEW,
    SUBMITTED_FOR_REVIEW,
    FAILED,
    CANCELLED,
    STALE,
    LIMIT_REACHED,
    ;

    public val terminal: Boolean
        get() = this in setOf(SUBMITTED_FOR_REVIEW, FAILED, CANCELLED)

    public val allowsWorkspaceMutation: Boolean
        get() = !terminal && this !in setOf(STALE, LIMIT_REACHED)
}

public data class AiTaskPolicy(
    val maxWorkPackages: Int = 12,
    val maxDraftItems: Int = 100,
    val maxDraftItemsPerBatch: Int = 20,
    val maxRepairCyclesPerPackage: Int = 3,
    val maxRepairCyclesPerTask: Int = 8,
    val maxToolCallsPerPackage: Int = 30,
    val maxToolCallsPerTask: Int = 200,
    val maxContextEntities: Int = 20,
    val maxExpandedContextEntities: Int = 50,
    val maxFiboCandidatesPerSearch: Int = 10,
    val maxShaclFindingsInContext: Int = 20,
    val maxActiveMutatingTasksPerUserProject: Int = 1,
    val maxConcurrentReadOnlyTasksPerUserProject: Int = 3,
    val maxPackageElapsedMillis: Long = 120_000,
    val maxTaskElapsedMillis: Long = 900_000,
) {
    init {
        require(maxWorkPackages > 0)
        require(maxDraftItems > 0)
        require(maxDraftItemsPerBatch in 1..maxDraftItems)
        require(maxRepairCyclesPerPackage >= 0)
        require(maxRepairCyclesPerTask >= maxRepairCyclesPerPackage)
        require(maxToolCallsPerPackage > 0)
        require(maxToolCallsPerTask >= maxToolCallsPerPackage)
        require(maxContextEntities > 0)
        require(maxExpandedContextEntities >= maxContextEntities)
        require(maxFiboCandidatesPerSearch > 0)
        require(maxShaclFindingsInContext > 0)
        require(maxActiveMutatingTasksPerUserProject > 0)
        require(maxConcurrentReadOnlyTasksPerUserProject > 0)
        require(maxPackageElapsedMillis > 0)
        require(maxTaskElapsedMillis >= maxPackageElapsedMillis)
    }
}

public data class AiTaskScopeSnapshot(
    val userId: String,
    val projectId: String,
    val conversationId: String,
    val allowedSourceIds: List<String>,
    val projectFingerprint: String,
    val collaborationSessionId: String? = null,
    val role: String,
    val permissions: Set<String>,
    val availableFeatures: Set<String>,
    val capturedAt: Instant,
) {
    init {
        require(userId.isNotBlank())
        require(projectId.isNotBlank())
        require(conversationId.isNotBlank())
        require(allowedSourceIds.isNotEmpty())
        require(allowedSourceIds.all(String::isNotBlank))
        require(projectFingerprint.isNotBlank())
        require(role.isNotBlank())
    }
}

public data class AiTaskExecutionSegment(
    val id: String,
    val ordinal: Int,
    val modelBinding: AiRunModelBinding,
    val createdAt: Instant,
    val completedAt: Instant? = null,
) {
    init {
        require(id.isNotBlank())
        require(ordinal > 0)
    }
}

public data class AiTask(
    val id: String,
    val userId: String,
    val projectId: String,
    val conversationId: String,
    val objective: String,
    val type: AiTaskType,
    val size: AiTaskSize,
    val scope: AiTaskScopeSnapshot,
    val initialExecutionSegment: AiTaskExecutionSegment,
    val policy: AiTaskPolicy = AiTaskPolicy(),
    val status: AiTaskStatus = AiTaskStatus.UNDERSTANDING,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(id.isNotBlank())
        require(userId.isNotBlank())
        require(projectId.isNotBlank())
        require(conversationId.isNotBlank())
        require(objective.isNotBlank())
        require(scope.userId == userId)
        require(scope.projectId == projectId)
        require(scope.conversationId == conversationId)
        require(initialExecutionSegment.ordinal == 1)
    }
}

public data class AiTaskAssumption(
    val id: String,
    val statement: String,
    val recordedAt: Instant,
)

public data class AiTaskOpenQuestion(
    val id: String,
    val question: String,
    val workPackageId: String? = null,
    val recordedAt: Instant,
)

public data class AiTaskEntityReference(
    val iri: String,
    val label: String,
    val kind: String,
    val sourceId: String,
)

public data class AiTaskPlanReference(
    val id: String,
    val revision: Int,
)

public data class AiTaskAnalysisReferences(
    val validationReferenceIds: List<String> = emptyList(),
    val reasoningReferenceIds: List<String> = emptyList(),
    val shaclReferenceIds: List<String> = emptyList(),
    val semanticDiffReferenceIds: List<String> = emptyList(),
)

public data class AiTaskCounters(
    val workPackageCount: Int = 0,
    val draftItemCount: Int = 0,
    val repairCycleCount: Int = 0,
    val toolCallCount: Int = 0,
) {
    init {
        require(workPackageCount >= 0)
        require(draftItemCount >= 0)
        require(repairCycleCount >= 0)
        require(toolCallCount >= 0)
    }
}

public data class AiTaskPause(
    val code: String,
    val message: String,
    val resumeStatus: AiTaskStatus,
    val requiresModelRebind: Boolean = false,
    val createdAt: Instant,
) {
    init {
        require(code.isNotBlank())
        require(message.isNotBlank())
        require(resumeStatus != AiTaskStatus.PAUSED)
    }
}

public data class AiTaskLimitRecord(
    val kind: String,
    val maximum: Long,
    val observed: Long,
    val recordedAt: Instant,
) {
    init {
        require(kind.isNotBlank())
        require(maximum >= 0)
        require(observed >= maximum)
    }
}

public data class AiTaskWorkspace(
    val task: AiTask,
    val revision: Long = 0,
    val assumptions: List<AiTaskAssumption> = emptyList(),
    val openQuestions: List<AiTaskOpenQuestion> = emptyList(),
    val selectedEntities: List<AiTaskEntityReference> = emptyList(),
    val planReference: AiTaskPlanReference? = null,
    val currentWorkPackageId: String? = null,
    val completedWorkPackageIds: List<String> = emptyList(),
    val failedWorkPackageIds: List<String> = emptyList(),
    val privateDraftId: String? = null,
    val analysisReferences: AiTaskAnalysisReferences = AiTaskAnalysisReferences(),
    val executionSegments: List<AiTaskExecutionSegment> = listOf(task.initialExecutionSegment),
    val counters: AiTaskCounters = AiTaskCounters(),
    val pause: AiTaskPause? = null,
    val limits: List<AiTaskLimitRecord> = emptyList(),
    val createdAt: Instant = task.createdAt,
    val updatedAt: Instant = task.updatedAt,
) {
    init {
        require(revision >= 0)
        require(executionSegments.isNotEmpty())
        require(executionSegments.first().copy(completedAt = task.initialExecutionSegment.completedAt) == task.initialExecutionSegment)
        require(executionSegments.map(AiTaskExecutionSegment::ordinal) == (1..executionSegments.size).toList())
    }
}
