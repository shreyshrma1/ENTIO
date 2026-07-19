package com.entio.web.ai

import java.time.Clock
import java.time.Instant

public enum class AiPackageProgressType { STARTED, PROVIDER_CALL_COMPLETED, BATCH_APPENDED, COMPLETED, BLOCKED, LIMIT_REACHED }

public data class AiPackageProgressEvent(
    val sequence: Long,
    val taskId: String,
    val workPackageId: String,
    val type: AiPackageProgressType,
    val createdAt: Instant,
)

public data class AiPackageExecutionContext(
    val workspace: AiTaskWorkspace,
    val workPackage: AiWorkPackage,
    val modelBinding: AiRunModelBinding,
    val contextPackage: AiTaskContextPackage,
    val frozenBundle: AiFrozenCapabilityBundle,
    val events: List<AiPackageProgressEvent>,
)

public class AiWorkPackageExecutionFailure(public val code: String, message: String) : IllegalArgumentException(message)

public class AiWorkPackageExecutor(
    private val store: AiTaskStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    private var sequence: Long = 0
    private val replayKeys: MutableMap<String, AiPackageExecutionContext> = linkedMapOf()

    @Synchronized
    public fun beginNext(
        userId: String,
        projectId: String,
        taskId: String,
        expectedRevision: Long,
        plan: AiWorkflowPlanRevision,
        contextPackage: AiTaskContextPackage,
        frozenBundle: AiFrozenCapabilityBundle,
        idempotencyKey: String,
    ): AiPackageExecutionContext {
        val replayKey = listOf(userId, projectId, taskId, idempotencyKey).joinToString("\u0000")
        replayKeys[replayKey]?.let { return it }
        val current = store.get(userId, projectId, taskId)
        if (current.revision != expectedRevision) throw AiWorkPackageExecutionFailure("stale-package-execution", "The task changed before package execution.")
        if (current.task.status !in setOf(AiTaskStatus.READY_TO_EXECUTE, AiTaskStatus.EXECUTING)) {
            throw AiWorkPackageExecutionFailure("task-not-executable", "The task is not ready for package execution.")
        }
        if (plan.taskId != taskId || current.planReference?.revision != plan.revision) {
            throw AiWorkPackageExecutionFailure("stale-execution-plan", "The task plan reference is stale.")
        }
        if (plan.requiresConfirmation && plan.confirmation == null) {
            throw AiWorkPackageExecutionFailure("unconfirmed-execution-plan", "The current plan requires explicit confirmation.")
        }
        if (current.currentWorkPackageId != null) throw AiWorkPackageExecutionFailure("parallel-package-execution", "A package is already executing.")
        if (current.counters.workPackageCount >= current.task.policy.maxWorkPackages) {
            throw AiWorkPackageExecutionFailure("work-package-limit", "The task work-package limit was reached.")
        }
        val completed = current.completedWorkPackageIds.toSet()
        if (plan.workPackages.any { item -> item.id !in completed && item.dependsOn.any { it in current.failedWorkPackageIds } }) {
            throw AiWorkPackageExecutionFailure("blocked-work-package", "A work package dependency failed.")
        }
        val next = plan.workPackages.firstOrNull {
            it.id !in completed && it.id !in current.failedWorkPackageIds && completed.containsAll(it.dependsOn)
        }
            ?: throw AiWorkPackageExecutionFailure("no-ready-work-package", "No dependency-ready work package is available.")
        if (frozenBundle.bundle.id != next.bundleId) {
            throw AiWorkPackageExecutionFailure("package-bundle-mismatch", "The frozen bundle does not match the work package.")
        }
        if (contextPackage.taskId != taskId || contextPackage.taskRevision != expectedRevision) {
            throw AiWorkPackageExecutionFailure("stale-package-context", "The package context is stale.")
        }
        val updated = update(
            current,
            expectedRevision,
            AiTaskStatus.EXECUTING,
            currentWorkPackageId = next.id,
            counters = current.counters.copy(workPackageCount = current.counters.workPackageCount + 1),
        )
        val event = event(taskId, next.id, AiPackageProgressType.STARTED)
        val result = AiPackageExecutionContext(
            updated, next, updated.executionSegments.last().modelBinding, contextPackage, frozenBundle, listOf(event),
        )
        replayKeys[replayKey] = result
        return result
    }

    public fun recordProviderCall(context: AiPackageExecutionContext, toolCalls: Int = 1): AiPackageExecutionContext {
        require(toolCalls > 0)
        val current = store.get(context.workspace.task.userId, context.workspace.task.projectId, context.workspace.task.id)
        if (current.task.status != AiTaskStatus.EXECUTING || current.currentWorkPackageId != context.workPackage.id) {
            throw AiWorkPackageExecutionFailure("package-interrupted", "The package was cancelled, paused, or replaced.")
        }
        val packageCalls = context.events.count { it.type == AiPackageProgressType.PROVIDER_CALL_COMPLETED } + toolCalls
        val taskCalls = current.counters.toolCallCount + toolCalls
        if (packageCalls > current.task.policy.maxToolCallsPerPackage || taskCalls > current.task.policy.maxToolCallsPerTask) {
            val packageLimitReached = packageCalls > current.task.policy.maxToolCallsPerPackage
            val maximum = if (packageLimitReached) current.task.policy.maxToolCallsPerPackage else current.task.policy.maxToolCallsPerTask
            val observed = if (packageLimitReached) packageCalls else taskCalls
            val limited = update(
                current, current.revision, AiTaskStatus.LIMIT_REACHED, currentWorkPackageId = context.workPackage.id,
                limits = current.limits + AiTaskLimitRecord("tool-calls", maximum.toLong(), observed.toLong(), clock.instant()),
            )
            return context.copy(workspace = limited, events = context.events + event(current.task.id, context.workPackage.id, AiPackageProgressType.LIMIT_REACHED))
        }
        val updated = update(
            current, current.revision, current.task.status, currentWorkPackageId = current.currentWorkPackageId,
            counters = current.counters.copy(toolCallCount = taskCalls),
        )
        return context.copy(
            workspace = updated,
            events = context.events + List(toolCalls) { event(current.task.id, context.workPackage.id, AiPackageProgressType.PROVIDER_CALL_COMPLETED) },
        )
    }

    public fun complete(context: AiPackageExecutionContext, appendedItems: Int): AiPackageExecutionContext {
        require(appendedItems in 0..context.workspace.task.policy.maxDraftItemsPerBatch)
        val current = store.get(context.workspace.task.userId, context.workspace.task.projectId, context.workspace.task.id)
        if (current.task.status != AiTaskStatus.EXECUTING || current.currentWorkPackageId != context.workPackage.id) {
            throw AiWorkPackageExecutionFailure("package-interrupted", "The package was cancelled, paused, or replaced.")
        }
        val draftCount = current.counters.draftItemCount + appendedItems
        if (draftCount > current.task.policy.maxDraftItems) throw AiWorkPackageExecutionFailure("task-draft-limit", "The task draft limit was reached.")
        val completed = (current.completedWorkPackageIds + context.workPackage.id).distinct()
        val updated = update(
            current, current.revision, AiTaskStatus.EXECUTING, currentWorkPackageId = null,
            completed = completed, counters = current.counters.copy(draftItemCount = draftCount),
        )
        return context.copy(
            workspace = updated,
            events = context.events + event(current.task.id, context.workPackage.id, AiPackageProgressType.COMPLETED),
        )
    }

    private fun event(taskId: String, packageId: String, type: AiPackageProgressType) =
        AiPackageProgressEvent(++sequence, taskId, packageId, type, clock.instant())

    private fun update(
        current: AiTaskWorkspace,
        expectedRevision: Long,
        status: AiTaskStatus,
        currentWorkPackageId: String?,
        completed: List<String> = current.completedWorkPackageIds,
        counters: AiTaskCounters = current.counters,
        limits: List<AiTaskLimitRecord> = current.limits,
    ): AiTaskWorkspace {
        val now = clock.instant()
        return store.update(
            current.task.userId, current.task.projectId, current.task.id, expectedRevision,
            current.copy(
                task = current.task.copy(status = status, updatedAt = now), revision = expectedRevision + 1,
                currentWorkPackageId = currentWorkPackageId, completedWorkPackageIds = completed,
                counters = counters, limits = limits, updatedAt = now,
            ),
        )
    }
}
