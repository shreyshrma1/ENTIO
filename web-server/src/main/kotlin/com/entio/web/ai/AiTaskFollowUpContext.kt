package com.entio.web.ai

public data class AiTaskFollowUpContext(
    val taskId: String,
    val objective: String,
    val status: AiTaskStatus,
    val completedPackages: List<String>,
    val failedPackages: List<String>,
    val assumptions: List<String>,
    val analysisReferences: AiTaskAnalysisReferences,
    val repairFindingCodes: List<String>,
    val remainingQuestions: List<String>,
    val privateDraftId: String?,
)

public class AiTaskFollowUpContextBuilder(
    private val store: AiTaskStore,
    private val repairs: AiRepairController,
) {
    public fun build(userId: String, projectId: String, taskId: String): AiTaskFollowUpContext {
        val workspace = store.get(userId, projectId, taskId)
        return AiTaskFollowUpContext(
            taskId,
            workspace.task.objective,
            workspace.task.status,
            workspace.completedWorkPackageIds,
            workspace.failedWorkPackageIds,
            workspace.assumptions.map(AiTaskAssumption::statement),
            workspace.analysisReferences,
            repairs.history(userId, projectId, taskId).map { it.packet.finding.code },
            workspace.openQuestions.map(AiTaskOpenQuestion::question),
            workspace.privateDraftId,
        )
    }
}
