package com.entio.web.ai

import java.time.Clock
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

public data class AiSemanticTaskStart(
    val workspace: AiTaskWorkspace,
    val plan: AiWorkflowPlanRevision,
    val turn: AiConversationTurnResult,
)

/**
 * Bridges the browser conversation surface to the Phase 8 task/workspace boundary.
 *
 * Conversation history remains a presentation and follow-up record. Task state, plan revisions,
 * execution progress, cancellation, and model provenance are authoritative in the Phase 8 store.
 * Provider output still reaches ontology state only through the existing conversation capability
 * dispatcher and private typed draft workspace.
 */
public class AiSemanticTaskOrchestrator(
    private val tasks: AiTaskStore,
    private val lifecycle: AiTaskLifecycleService,
    private val planning: AiTaskPlanningService,
    private val bundles: AiCapabilityBundleRegistry,
    private val conversationService: AiConversationService,
    private val eventLog: AiTaskEventLog,
    private val modelBindings: AiRunModelBindingResolver,
    private val clock: Clock = Clock.systemUTC(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val backgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val taskRuns: MutableMap<String, String> = linkedMapOf()
    private val taskOwners: MutableMap<String, Pair<String, String>> = linkedMapOf()

    /** Creates a task, records a validated plan, and starts the corresponding provider run. */
    public suspend fun start(
        scope: AiCapabilityScope,
        conversationId: String,
        request: AiConversationTurnRequest,
        screenContext: AiCurrentScreenContext,
        respondAsync: Boolean,
    ): AiSemanticTaskStart {
        val prepared = prepare(scope, conversationId, request.message)
        val executing = markExecuting(prepared.workspace)
        eventLog.append(executing.task.id, executing.task.status, "EXECUTION_STARTED", "Entio is executing the task plan serially.")
        val frozenBundle = bundles.freeze(executing.task, scope)
        val semanticRequest = request.copy(
            executionMode = AiExecutionMode.SEMANTIC_TASK,
            capabilitySnapshot = frozenBundle.snapshot,
            capabilityRegistry = frozenBundle.registry,
        )
        val turn = if (respondAsync) {
            conversationService.start(scope, conversationId, semanticRequest, screenContext)
        } else {
            conversationService.send(scope, conversationId, semanticRequest, screenContext)
        }.copy(taskId = executing.task.id)
        synchronized(this) {
            taskRuns[executing.task.id] = turn.run.id
            taskOwners[executing.task.id] = executing.task.userId to executing.task.projectId
        }
        if (respondAsync) {
            monitor(executing.task.id, turn.run.id)
        } else {
            synchronize(executing.task.id, turn.run.id)
        }
        return AiSemanticTaskStart(executing, prepared.plan, turn)
    }

    /** Starts a task created through the explicit task workspace route. */
    public fun execute(
        userId: String,
        projectId: String,
        taskId: String,
        expectedRevision: Long,
        screenContext: AiCurrentScreenContext = AiCurrentScreenContext(AiScreenId.EXPLORE),
    ): AiSemanticTaskStart {
        val current = tasks.get(userId, projectId, taskId)
        if (current.revision != expectedRevision) throw AiStateAccessFailure("ai-task-revision-conflict", "The AI task changed before execution started.")
        val plan = current.planReference?.let { planning.history(userId, projectId, taskId).current }
            ?: throw AiWorkflowPlanFailure("missing-workflow-plan", "The task must have a validated plan before execution.")
        val executing = markExecuting(current)
        val scope = current.toCapabilityScope()
        val frozenBundle = bundles.freeze(executing.task, scope)
        val request = AiConversationTurnRequest(
            executing.task.objective,
            executionMode = AiExecutionMode.SEMANTIC_TASK,
            capabilitySnapshot = frozenBundle.snapshot,
            capabilityRegistry = frozenBundle.registry,
        )
        val turn = conversationService.start(scope, executing.task.conversationId, request, screenContext)
            .copy(taskId = taskId)
        synchronized(this) {
            taskRuns[taskId] = turn.run.id
            taskOwners[taskId] = executing.task.userId to executing.task.projectId
        }
        monitor(taskId, turn.run.id)
        return AiSemanticTaskStart(executing, plan, turn)
    }

    public fun cancel(userId: String, projectId: String, taskId: String, expectedRevision: Long): AiTaskWorkspace {
        val current = tasks.get(userId, projectId, taskId)
        if (current.revision != expectedRevision) throw AiStateAccessFailure("ai-task-revision-conflict", "The AI task changed before cancellation.")
        synchronized(this) { taskRuns[taskId] }?.let { runId ->
            runCatching { conversationService.cancel(userId, projectId, runId) }
        }
        val cancelled = lifecycle.cancel(userId, projectId, taskId, expectedRevision)
        eventLog.append(taskId, cancelled.task.status, "CANCELLED", "The task was cancelled by the user.")
        return cancelled
    }

    private fun prepare(scope: AiCapabilityScope, conversationId: String, objective: String): PreparedTask {
        val taskScope = AiTaskScopeSnapshot(
            userId = scope.userId,
            projectId = scope.projectId,
            conversationId = conversationId,
            allowedSourceIds = scope.allowedSourceIds,
            projectFingerprint = scope.baselineFingerprint,
            collaborationSessionId = scope.collaborationSessionId,
            role = scope.role,
            permissions = scope.permissions,
            availableFeatures = scope.availableFeatures,
            capturedAt = scope.createdAt,
        )
        val created = lifecycle.create(AiTaskCreationRequest(objective, taskScope, modelBinding = currentModelBinding(scope)))
        eventLog.append(created.task.id, created.task.status, "TASK_CREATED", "The semantic task workspace was created.")
        val packageTask = created.task.copy(status = AiTaskStatus.READY_TO_EXECUTE)
        val workPackages = buildWorkPackages(packageTask, scope)
        val planned = planning.createPlan(
            scope.userId,
            scope.projectId,
            created.task.id,
            created.revision,
            workPackages,
            userRequestedConfirmation = false,
        )
        eventLog.append(
            created.task.id,
            planned.workspace.task.status,
            "PLAN_CREATED",
            "Created a dependency-ordered plan with ${planned.plan?.workPackages?.size ?: 0} work package(s).",
        )
        return PreparedTask(planned.workspace, requireNotNull(planned.plan))
    }

    private fun currentModelBinding(scope: AiCapabilityScope): AiRunModelBinding = modelBindings.resolve(scope.userId)

    private fun buildWorkPackages(task: AiTask, scope: AiCapabilityScope): List<AiWorkPackage> {
        val objectiveReference = "task:${task.id}:objective"
        val objectiveSummary = task.objective.trim().replace(Regex("\\s+"), " ").take(120)
        val discoveryId = "package-${idFactory()}"
        val discovery = AiWorkPackage(
            id = discoveryId,
            title = "Inspect context relevant to '$objectiveSummary'",
            expectedSourceIds = scope.allowedSourceIds,
            bundleId = AiCapabilityBundleId.EXPLORATION,
            estimate = AiWorkEstimate(0, "SMALL"),
            evidenceReferences = listOf(objectiveReference),
        )
        val packages = mutableListOf(discovery)
        var dependency = discoveryId
        if (task.type in AiTaskClassifier.mutatingTypes) {
            val draftId = "package-${idFactory()}"
            packages += AiWorkPackage(
                id = draftId,
                title = draftPackageTitle(task.type),
                dependsOn = listOf(dependency),
                expectedSourceIds = scope.allowedSourceIds,
                bundleId = when (task.type) {
                    AiTaskType.REPAIR -> AiCapabilityBundleId.REPAIR
                    else -> AiCapabilityBundleId.ONTOLOGY_EDITING
                },
                estimate = AiWorkEstimate(estimateFor(task), effortFor(task)),
                evidenceReferences = listOf(objectiveReference),
                riskFlags = riskFlagsFor(task),
            )
            dependency = draftId
        }
        packages += AiWorkPackage(
            id = "package-${idFactory()}",
            title = "Validate the result, explain evidence, and prepare it for human review",
            dependsOn = listOf(dependency),
            expectedSourceIds = scope.allowedSourceIds,
            bundleId = AiCapabilityBundleId.ANALYSIS,
            estimate = AiWorkEstimate(0, "SMALL"),
            evidenceReferences = listOf(objectiveReference),
        )
        return packages
    }

    private fun draftPackageTitle(type: AiTaskType): String = when (type) {
        AiTaskType.DOMAIN_MODELING -> "Reuse or create the requested concepts, relationships, examples, and constraints in a typed private draft"
        AiTaskType.MULTI_EDIT_CHANGE -> "Stage every requested target as dependency-ordered typed private-draft operations"
        AiTaskType.REFACTORING -> "Prepare the requested hierarchy or vocabulary refactor as typed private-draft operations"
        AiTaskType.REPAIR -> "Repair blocking findings with the smallest valid typed private-draft operations"
        AiTaskType.FOCUSED_EDIT -> "Prepare the requested typed ontology change in the private draft"
        else -> "Prepare the requested typed operations in the private draft"
    }

    private fun markExecuting(current: AiTaskWorkspace): AiTaskWorkspace {
        if (current.task.status == AiTaskStatus.EXECUTING) return current
        if (current.task.status != AiTaskStatus.READY_TO_EXECUTE) {
            throw AiTaskLifecycleFailure("ai-task-not-ready", "The semantic task is not ready to execute.")
        }
        val now = clock.instant()
        return tasks.update(
            current.task.userId,
            current.task.projectId,
            current.task.id,
            current.revision,
            current.copy(
                task = current.task.copy(status = AiTaskStatus.EXECUTING, updatedAt = now),
                revision = current.revision + 1,
                currentWorkPackageId = planWorkPackageId(current),
                updatedAt = now,
            ),
        )
    }

    private fun monitor(taskId: String, runId: String) {
        backgroundScope.launch {
            try {
                while (true) {
                    val owner = synchronized(this@AiSemanticTaskOrchestrator) { taskOwners[taskId] } ?: break
                    val run = runCatching {
                        conversationService.getRun(owner.first, owner.second, runId)
                    }.getOrNull() ?: break
                    synchronize(taskId, runId)
                    if (run.status.terminal) break
                    delay(100)
                }
            } catch (_: CancellationException) {
                // The task remains in the authoritative cancellation state.
            } finally {
                synchronized(this@AiSemanticTaskOrchestrator) {
                    taskRuns.remove(taskId)
                    taskOwners.remove(taskId)
                }
            }
        }
    }

    private fun synchronize(taskId: String, runId: String) {
        val owner = synchronized(this) { taskOwners[taskId] } ?: return
        val run = runCatching {
            conversationService.getRun(owner.first, owner.second, runId)
        }.getOrNull() ?: return
        val current = runCatching { tasks.get(run.userId, run.projectId, taskId) }.getOrNull() ?: return
        val conversation = runCatching { conversationService.getConversation(run.userId, run.projectId, run.conversationId) }.getOrNull()
        val draftId = conversation?.currentDraftId ?: current.privateDraftId
        val desired = when (run.status) {
            AiRunStatus.READY_FOR_REVIEW -> AiTaskStatus.READY_FOR_REVIEW
            AiRunStatus.FAILED -> AiTaskStatus.FAILED
            AiRunStatus.CANCELLED -> AiTaskStatus.CANCELLED
            AiRunStatus.LIMIT_REACHED -> AiTaskStatus.LIMIT_REACHED
            else -> current.task.status
        }
        val plannedPackageIds = runCatching {
            planning.history(current.task.userId, current.task.projectId, taskId).current.workPackages.map(AiWorkPackage::id)
        }.getOrDefault(emptyList())
        val completedPackages = if (desired == AiTaskStatus.READY_FOR_REVIEW) {
            plannedPackageIds
        } else {
            current.completedWorkPackageIds
        }
        var updated = current
        if (desired != current.task.status && AiTaskStateMachine().canTransition(current.task.status, desired)) {
            updated = current.copy(task = current.task.copy(status = desired, updatedAt = clock.instant()))
        }
        val counters = current.counters.copy(
            toolCallCount = run.capabilityCallCount,
            draftItemCount = run.draftEditCount,
            repairCycleCount = run.correctionCycleCount,
        )
        val nextWorkPackageId = if (desired == AiTaskStatus.READY_FOR_REVIEW || desired.terminal) null else current.currentWorkPackageId
        if (
            updated == current && draftId == current.privateDraftId && counters == current.counters &&
            nextWorkPackageId == current.currentWorkPackageId && completedPackages == current.completedWorkPackageIds
        ) return
        updated = updated.copy(
            revision = current.revision + 1,
            currentWorkPackageId = nextWorkPackageId,
            privateDraftId = draftId,
            completedWorkPackageIds = completedPackages,
            counters = counters,
            updatedAt = clock.instant(),
        )
        if (updated == current) return
        val saved = runCatching {
            tasks.update(current.task.userId, current.task.projectId, taskId, current.revision, updated)
        }.getOrNull() ?: return
        val eventType = when (saved.task.status) {
            AiTaskStatus.READY_FOR_REVIEW -> "READY_FOR_REVIEW"
            AiTaskStatus.FAILED -> "FAILED"
            AiTaskStatus.CANCELLED -> "CANCELLED"
            AiTaskStatus.LIMIT_REACHED -> "LIMIT_REACHED"
            else -> "PROGRESS"
        }
        eventLog.append(taskId, saved.task.status, eventType, progressMessage(saved.task.status))
    }

    private fun estimateFor(task: AiTask): Int = when (task.type) {
        AiTaskType.EXPLANATION, AiTaskType.SEARCH_AND_DISCOVERY, AiTaskType.REVIEW -> 0
        AiTaskType.FOCUSED_EDIT -> 3
        AiTaskType.MULTI_EDIT_CHANGE, AiTaskType.REPAIR, AiTaskType.PROJECT_ANALYSIS -> 12
        AiTaskType.DOMAIN_MODELING -> 16
        AiTaskType.REFACTORING -> 20
    }

    private fun effortFor(task: AiTask): String = when (task.size) {
        AiTaskSize.SIMPLE -> "SMALL"
        AiTaskSize.MEDIUM -> "MEDIUM"
        AiTaskSize.LARGE -> "LARGE"
    }

    private fun riskFlagsFor(task: AiTask): Set<AiPlanRiskFlag> = buildSet {
        if (task.type == AiTaskType.REFACTORING) add(AiPlanRiskFlag.HIERARCHY_REFACTOR)
        if (task.scope.allowedSourceIds.size > 1) add(AiPlanRiskFlag.MULTI_SOURCE)
    }

    private fun progressMessage(status: AiTaskStatus): String = when (status) {
        AiTaskStatus.READY_FOR_REVIEW -> "The private draft passed the current execution boundary and is ready for deterministic review."
        AiTaskStatus.FAILED -> "The semantic task stopped safely; no ontology source was written."
        AiTaskStatus.CANCELLED -> "The semantic task stopped at a safe cancellation boundary."
        AiTaskStatus.LIMIT_REACHED -> "The task policy boundary was reached; the private workspace remains available."
        else -> "The semantic task is continuing through its current work package."
    }

    private fun planWorkPackageId(current: AiTaskWorkspace): String =
        runCatching {
            planning.history(current.task.userId, current.task.projectId, current.task.id).current.workPackages.firstOrNull()?.id
        }.getOrNull() ?: current.currentWorkPackageId.orEmpty().ifBlank { "package-${current.task.id}" }

    private data class PreparedTask(
        val workspace: AiTaskWorkspace,
        val plan: AiWorkflowPlanRevision,
    )
}

private fun AiTaskWorkspace.toCapabilityScope(): AiCapabilityScope = AiCapabilityScope(
    userId = task.userId,
    projectId = task.projectId,
    conversationId = task.conversationId,
    allowedSourceIds = task.scope.allowedSourceIds,
    baselineFingerprint = task.scope.projectFingerprint,
    collaborationSessionId = task.scope.collaborationSessionId,
    role = task.scope.role,
    permissions = task.scope.permissions,
    availableFeatures = task.scope.availableFeatures,
    createdAt = task.scope.capturedAt,
)
