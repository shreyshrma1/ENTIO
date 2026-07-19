package com.entio.web.ai

import com.entio.web.contract.DevelopmentAuthorization
import com.entio.web.contract.WebAiLimit
import com.entio.web.contract.WebAiTask
import com.entio.web.contract.WebAiTaskCommandRequest
import com.entio.web.contract.WebAiTaskCreateRequest
import com.entio.web.contract.WebAiTaskEvent
import com.entio.web.contract.WebAiTaskResourceResponse
import com.entio.web.contract.WebAiTaskResponse
import com.entio.web.contract.WebAiTaskWorkspace
import com.entio.web.contract.WebAiTaskWorkspaceResponse
import com.entio.web.contract.WebPermission
import com.entio.web.contract.WebSessionUser
import java.time.Clock
import java.time.Instant

public data class AiTaskEventWindow(
    val events: List<WebAiTaskEvent>,
    val resynchronizationRequired: Boolean,
    val terminal: Boolean,
)

/** Versioned task transport adapter. Workflow legality remains in Phase 8 task services and stores. */
public class AiTaskWebBoundary(
    private val store: AiTaskStore,
    private val lifecycle: AiTaskLifecycleService,
    private val authorization: DevelopmentAuthorization,
    private val modelBindings: AiRunModelBindingResolver,
    private val projectFingerprint: (String, List<String>) -> String,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val events: MutableMap<String, List<WebAiTaskEvent>> = linkedMapOf()
    private val idempotentResponses: MutableMap<String, Pair<String, WebAiTaskResponse>> = linkedMapOf()

    @Synchronized
    public fun create(user: WebSessionUser, projectId: String, request: WebAiTaskCreateRequest, idempotencyKey: String): WebAiTaskResponse {
        requireIdempotency(idempotencyKey)
        requireUseAi(user)
        val replayKey = "${user.id}:$projectId:create:$idempotencyKey"
        val requestFingerprint = listOf(request.conversationId, request.objective, request.allowedSourceIds.sorted().joinToString(",")).joinToString("|")
        idempotentResponses[replayKey]?.let { (storedFingerprint, response) ->
            if (storedFingerprint != requestFingerprint) throw AiTaskLifecycleFailure("idempotency-conflict", "The idempotency key was used for another task request.")
            return response
        }
        if (request.conversationId.isBlank() || request.objective.isBlank() || request.allowedSourceIds.isEmpty()) {
            throw AiTaskLifecycleFailure("invalid-task-request", "Conversation, objective, and allowed sources are required.")
        }
        val permissions = authorization.permissionsFor(user.role).map(WebPermission::name).toSet()
        val now = clock.instant()
        val scope = AiTaskScopeSnapshot(
            user.id, projectId, request.conversationId, request.allowedSourceIds,
            projectFingerprint(projectId, request.allowedSourceIds), role = user.role.name,
            permissions = permissions, availableFeatures = setOf(AiCapabilityFeatures.PRIVATE_DRAFT), capturedAt = now,
        )
        val workspace = lifecycle.create(AiTaskCreationRequest(request.objective, scope, modelBindings.resolve(user.id)))
        append(workspace, "TASK_CREATED", "The task workspace was created.")
        return WebAiTaskResponse(task = workspace.toWebTask()).also { idempotentResponses[replayKey] = requestFingerprint to it }
    }

    public fun get(user: WebSessionUser, projectId: String, taskId: String): WebAiTaskResponse =
        WebAiTaskResponse(task = owned(user, projectId, taskId).toWebTask())

    public fun workspace(user: WebSessionUser, projectId: String, taskId: String): WebAiTaskWorkspaceResponse {
        val current = owned(user, projectId, taskId)
        val references = current.analysisReferences.run {
            validationReferenceIds + semanticDiffReferenceIds + reasoningReferenceIds + shaclReferenceIds
        }.distinct().sorted()
        return WebAiTaskWorkspaceResponse(
            workspace = WebAiTaskWorkspace(
                current.toWebTask(), current.currentProjectFingerprint, current.assumptions.map(AiTaskAssumption::statement),
                current.openQuestions.map(AiTaskOpenQuestion::question), current.selectedEntities.map(AiTaskEntityReference::iri),
                current.planReference?.id, current.planReference?.revision, references,
                current.counters.repairCycleCount, current.counters.toolCallCount, current.pause?.code,
                current.limits.map { WebAiLimit(it.kind, it.maximum, it.observed) },
            ),
        )
    }

    public fun resource(user: WebSessionUser, projectId: String, taskId: String, resource: String): WebAiTaskResourceResponse {
        val current = owned(user, projectId, taskId)
        val references = when (resource) {
            "draft" -> listOfNotNull(current.privateDraftId)
            "analysis" -> current.analysisReferences.run { validationReferenceIds + semanticDiffReferenceIds + reasoningReferenceIds + shaclReferenceIds }
            "review-package" -> if (current.task.status in setOf(AiTaskStatus.READY_FOR_REVIEW, AiTaskStatus.SUBMITTED_FOR_REVIEW)) listOf("task-review:${current.task.id}:${current.revision}") else emptyList()
            else -> throw AiTaskLifecycleFailure("unknown-task-resource", "The requested task resource is unknown.")
        }.distinct().sorted()
        return WebAiTaskResourceResponse(taskId = taskId, revision = current.revision, resource = resource, referenceIds = references, available = references.isNotEmpty())
    }

    @Synchronized
    public fun command(
        user: WebSessionUser,
        projectId: String,
        taskId: String,
        action: String,
        request: WebAiTaskCommandRequest,
        idempotencyKey: String,
    ): WebAiTaskResponse {
        requireIdempotency(idempotencyKey)
        requireUseAi(user)
        val replayKey = "${user.id}:$projectId:$taskId:$action:$idempotencyKey"
        val requestFingerprint = listOf(request.expectedRevision.toString(), request.message.orEmpty(), request.answer.orEmpty(), request.planRevision?.toString().orEmpty()).joinToString("|")
        idempotentResponses[replayKey]?.let { (storedFingerprint, response) ->
            if (storedFingerprint != requestFingerprint) throw AiTaskLifecycleFailure("idempotency-conflict", "The idempotency key was used for another task command.")
            return response
        }
        val current = owned(user, projectId, taskId)
        if (current.revision != request.expectedRevision) throw AiStateAccessFailure("ai-task-revision-conflict", "The AI task workspace changed before this command.")
        val updated = when (action) {
            "pause" -> lifecycle.pause(user.id, projectId, taskId, request.expectedRevision)
            "resume" -> lifecycle.resume(user.id, projectId, taskId, request.expectedRevision)
            "cancel" -> lifecycle.cancel(user.id, projectId, taskId, request.expectedRevision)
            "messages", "clarifications", "plan", "plan-confirm", "execute", "submit" -> current
            else -> throw AiTaskLifecycleFailure("unknown-task-command", "The requested task command is unknown.")
        }
        append(updated, action.toEventType(), "Task command '$action' was accepted.")
        return WebAiTaskResponse(task = updated.toWebTask()).also { idempotentResponses[replayKey] = requestFingerprint to it }
    }

    public fun events(user: WebSessionUser, projectId: String, taskId: String, afterSequence: Int): AiTaskEventWindow {
        val current = owned(user, projectId, taskId)
        val retained = events[taskId].orEmpty()
        val earliest = retained.firstOrNull()?.sequence
        val gap = afterSequence > 0 && earliest != null && afterSequence < earliest - 1
        return AiTaskEventWindow(if (gap) emptyList() else retained.filter { it.sequence > afterSequence }, gap, current.task.status.terminal)
    }

    private fun owned(user: WebSessionUser, projectId: String, taskId: String): AiTaskWorkspace {
        requireUseAi(user)
        return store.get(user.id, projectId, taskId)
    }

    @Synchronized
    private fun append(workspace: AiTaskWorkspace, type: String, message: String) {
        val current = events[workspace.task.id].orEmpty()
        val event = WebAiTaskEvent(
            (current.lastOrNull()?.sequence ?: 0) + 1, workspace.task.id, type, workspace.task.status.name,
            message, emptyList(), clock.instant().toString(),
        )
        events[workspace.task.id] = (current + event).takeLast(MAX_EVENTS)
    }

    private fun requireUseAi(user: WebSessionUser) {
        if (WebPermission.USE_AI !in authorization.permissionsFor(user.role)) {
            throw AiTaskLifecycleFailure("ai-task-forbidden", "The current user cannot use AI tasks.")
        }
    }

    private fun requireIdempotency(value: String) {
        if (value.isBlank() || value.length > 200) throw AiTaskLifecycleFailure("invalid-idempotency-key", "A bounded idempotency key is required.")
    }

    private fun AiTaskWorkspace.toWebTask(): WebAiTask = WebAiTask(
        task.id, task.conversationId, task.projectId, task.objective, task.type.name, task.size.name, task.status.name,
        revision, task.initialExecutionSegment.modelBinding.modelId, currentWorkPackageId, completedWorkPackageIds,
        failedWorkPackageIds, privateDraftId, task.createdAt.toString(), updatedAt.toString(),
    )

    private fun String.toEventType(): String = replace('-', '_').uppercase()

    private companion object { const val MAX_EVENTS: Int = 200 }
}
