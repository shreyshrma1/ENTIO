package com.entio.web.ai

import java.time.Clock
import java.util.UUID

public data class AiPlanningResult(val workspace: AiTaskWorkspace, val plan: AiWorkflowPlanRevision?)

public class AiTaskPlanningService(
    private val store: AiTaskStore,
    private val validator: AiWorkflowPlanValidator = AiWorkflowPlanValidator(),
    private val clock: Clock = Clock.systemUTC(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val histories: MutableMap<String, AiWorkflowPlanHistory> = linkedMapOf()

    public fun readySimple(userId: String, projectId: String, taskId: String, expectedRevision: Long): AiPlanningResult {
        val current = store.get(userId, projectId, taskId)
        if (current.task.size != AiTaskSize.SIMPLE) throw AiWorkflowPlanFailure("workflow-plan-required", "Only simple tasks may skip planning.")
        return AiPlanningResult(update(current, expectedRevision, AiTaskStatus.READY_TO_EXECUTE), null)
    }

    @Synchronized
    public fun createPlan(
        userId: String,
        projectId: String,
        taskId: String,
        expectedRevision: Long,
        packages: List<AiWorkPackage>,
        userRequestedConfirmation: Boolean = false,
    ): AiPlanningResult {
        val current = store.get(userId, projectId, taskId)
        if (histories.containsKey(taskId)) throw AiWorkflowPlanFailure("workflow-plan-already-exists", "Revise the existing plan instead.")
        val ordered = validator.validate(current.task, packages)
        val requires = requiresConfirmation(current.task, ordered, userRequestedConfirmation)
        val now = clock.instant()
        val plan = AiWorkflowPlanRevision(idFactory(), 1, taskId, ordered, requires, createdAt = now)
        val status = if (requires) AiTaskStatus.AWAITING_PLAN_CONFIRMATION else AiTaskStatus.READY_TO_EXECUTE
        val workspace = update(current, expectedRevision, status, AiTaskPlanReference(plan.planId, 1))
        histories[taskId] = AiWorkflowPlanHistory(plan.planId, listOf(plan))
        return AiPlanningResult(workspace, plan)
    }

    @Synchronized
    public fun revisePlan(
        userId: String,
        projectId: String,
        taskId: String,
        expectedRevision: Long,
        expectedPlanRevision: Int,
        packages: List<AiWorkPackage>,
    ): AiPlanningResult {
        val current = store.get(userId, projectId, taskId)
        val history = histories[taskId] ?: throw AiWorkflowPlanFailure("missing-workflow-plan", "The workflow plan was not found.")
        if (history.current.revision != expectedPlanRevision) throw AiWorkflowPlanFailure("stale-plan-revision", "The plan changed before revision.")
        val ordered = validator.validate(current.task, packages)
        val revision = expectedPlanRevision + 1
        val plan = AiWorkflowPlanRevision(history.planId, revision, taskId, ordered, requiresConfirmation(current.task, ordered, false), createdAt = clock.instant())
        val status = if (plan.requiresConfirmation) AiTaskStatus.AWAITING_PLAN_CONFIRMATION else AiTaskStatus.READY_TO_EXECUTE
        val workspace = update(current, expectedRevision, status, AiTaskPlanReference(plan.planId, revision))
        histories[taskId] = history.copy(revisions = history.revisions + plan)
        return AiPlanningResult(workspace, plan)
    }

    @Synchronized
    public fun confirm(
        userId: String,
        projectId: String,
        taskId: String,
        expectedRevision: Long,
        planRevision: Int,
        actorType: String,
    ): AiPlanningResult {
        if (actorType != "USER") throw AiWorkflowPlanFailure("user-plan-confirmation-required", "Only an explicit user action can confirm a plan.")
        val current = store.get(userId, projectId, taskId)
        val history = histories[taskId] ?: throw AiWorkflowPlanFailure("missing-workflow-plan", "The workflow plan was not found.")
        val plan = history.current
        if (plan.revision != planRevision) throw AiWorkflowPlanFailure("stale-plan-confirmation", "The confirmation targets a stale plan revision.")
        if (plan.confirmation != null) throw AiWorkflowPlanFailure("plan-confirmation-replay", "This plan revision is already confirmed.")
        val confirmed = plan.copy(confirmation = AiPlanConfirmation(userId, planRevision, clock.instant()))
        val workspace = update(current, expectedRevision, AiTaskStatus.READY_TO_EXECUTE)
        histories[taskId] = history.copy(revisions = history.revisions.dropLast(1) + confirmed)
        return AiPlanningResult(workspace, confirmed)
    }

    public fun history(userId: String, projectId: String, taskId: String): AiWorkflowPlanHistory {
        store.get(userId, projectId, taskId)
        return histories[taskId] ?: throw AiWorkflowPlanFailure("missing-workflow-plan", "The workflow plan was not found.")
    }

    private fun requiresConfirmation(task: AiTask, packages: List<AiWorkPackage>, requested: Boolean): Boolean =
        task.size == AiTaskSize.LARGE || requested || packages.sumOf { it.estimate.expectedEditCount } > 20 ||
            packages.flatMap { it.riskFlags }.isNotEmpty() || packages.flatMap { it.expectedSourceIds }.distinct().size > 1

    private fun update(
        current: AiTaskWorkspace,
        expectedRevision: Long,
        status: AiTaskStatus,
        planReference: AiTaskPlanReference? = current.planReference,
    ): AiTaskWorkspace {
        val now = clock.instant()
        return store.update(
            current.task.userId, current.task.projectId, current.task.id, expectedRevision,
            current.copy(
                task = current.task.copy(status = status, updatedAt = now),
                revision = expectedRevision + 1,
                planReference = planReference,
                updatedAt = now,
            ),
        )
    }
}
