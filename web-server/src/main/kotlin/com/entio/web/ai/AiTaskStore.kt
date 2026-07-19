package com.entio.web.ai

public interface AiTaskStore {
    public fun create(workspace: AiTaskWorkspace): AiTaskWorkspace

    public fun get(userId: String, projectId: String, taskId: String): AiTaskWorkspace

    public fun update(
        userId: String,
        projectId: String,
        taskId: String,
        expectedRevision: Long,
        replacement: AiTaskWorkspace,
    ): AiTaskWorkspace

    public fun list(userId: String, projectId: String): List<AiTaskWorkspace>
}

public class InMemoryAiTaskStore(
    private val stateMachine: AiTaskStateMachine = AiTaskStateMachine(),
) : AiTaskStore {
    private val workspaces: MutableMap<String, AiTaskWorkspace> = linkedMapOf()

    @Synchronized
    override fun create(workspace: AiTaskWorkspace): AiTaskWorkspace {
        val normalized = normalize(workspace)
        if (normalized.revision != 0L) {
            throw AiStateAccessFailure("invalid-ai-task-revision", "A new AI task workspace must start at revision 0.")
        }
        if (workspaces.putIfAbsent(normalized.task.id, normalized) != null) {
            throw AiStateAccessFailure("duplicate-ai-task", "AI task '${normalized.task.id}' already exists.")
        }
        return normalized
    }

    @Synchronized
    override fun get(userId: String, projectId: String, taskId: String): AiTaskWorkspace =
        owned(workspaces[taskId], userId, projectId)

    @Synchronized
    override fun update(
        userId: String,
        projectId: String,
        taskId: String,
        expectedRevision: Long,
        replacement: AiTaskWorkspace,
    ): AiTaskWorkspace {
        val current = owned(workspaces[taskId], userId, projectId)
        if (current.revision != expectedRevision) {
            throw AiStateAccessFailure(
                "ai-task-revision-conflict",
                "The AI task workspace changed before this command was applied.",
            )
        }
        if (replacement.revision != expectedRevision + 1) {
            throw AiStateAccessFailure(
                "invalid-ai-task-revision",
                "The replacement AI task workspace must advance exactly one revision.",
            )
        }
        if (current.task.status.terminal) {
            stateMachine.requireWorkspaceMutation(current.task.status)
        }
        requireStableIdentity(current, replacement)
        stateMachine.requireTransition(current.task.status, replacement.task.status)
        val normalized = normalize(replacement)
        workspaces[taskId] = normalized
        return normalized
    }

    @Synchronized
    override fun list(userId: String, projectId: String): List<AiTaskWorkspace> = workspaces.values
        .filter { it.task.userId == userId && it.task.projectId == projectId }
        .sortedWith(compareBy({ it.task.createdAt }, { it.task.id }))

    private fun owned(workspace: AiTaskWorkspace?, userId: String, projectId: String): AiTaskWorkspace {
        if (workspace == null || workspace.task.userId != userId || workspace.task.projectId != projectId) {
            throw AiStateAccessFailure("missing-ai-task", "The AI task was not found.")
        }
        return workspace
    }

    private fun requireStableIdentity(current: AiTaskWorkspace, replacement: AiTaskWorkspace) {
        val currentTask = current.task
        val replacementTask = replacement.task
        if (
            replacementTask.id != currentTask.id ||
            replacementTask.userId != currentTask.userId ||
            replacementTask.projectId != currentTask.projectId ||
            replacementTask.conversationId != currentTask.conversationId ||
            replacementTask.scope != currentTask.scope ||
            replacementTask.initialExecutionSegment != currentTask.initialExecutionSegment ||
            replacementTask.policy != currentTask.policy ||
            replacementTask.createdAt != currentTask.createdAt
        ) {
            throw AiStateAccessFailure(
                "ai-task-scope-violation",
                "An AI task cannot change its identity, scope, initial model binding, policy, or creation time.",
            )
        }
        if (replacement.createdAt != current.createdAt) {
            throw AiStateAccessFailure("ai-task-scope-violation", "An AI task workspace cannot change its creation time.")
        }
    }

    private fun normalize(workspace: AiTaskWorkspace): AiTaskWorkspace = workspace.copy(
        task = workspace.task.copy(
            objective = workspace.task.objective.trim(),
            scope = workspace.task.scope.copy(
                allowedSourceIds = workspace.task.scope.allowedSourceIds.distinct().sorted(),
                permissions = workspace.task.scope.permissions.toSortedSet(),
                availableFeatures = workspace.task.scope.availableFeatures.toSortedSet(),
            ),
        ),
        assumptions = workspace.assumptions.distinctBy(AiTaskAssumption::id).sortedBy(AiTaskAssumption::id),
        openQuestions = workspace.openQuestions.distinctBy(AiTaskOpenQuestion::id).sortedBy(AiTaskOpenQuestion::id),
        selectedEntities = workspace.selectedEntities
            .distinctBy { listOf(it.sourceId, it.iri) }
            .sortedWith(compareBy(AiTaskEntityReference::sourceId, AiTaskEntityReference::label, AiTaskEntityReference::iri)),
        completedWorkPackageIds = workspace.completedWorkPackageIds.distinct().sorted(),
        failedWorkPackageIds = workspace.failedWorkPackageIds.distinct().sorted(),
        analysisReferences = workspace.analysisReferences.copy(
            validationReferenceIds = workspace.analysisReferences.validationReferenceIds.distinct().sorted(),
            reasoningReferenceIds = workspace.analysisReferences.reasoningReferenceIds.distinct().sorted(),
            shaclReferenceIds = workspace.analysisReferences.shaclReferenceIds.distinct().sorted(),
            semanticDiffReferenceIds = workspace.analysisReferences.semanticDiffReferenceIds.distinct().sorted(),
        ),
        executionSegments = workspace.executionSegments.sortedBy(AiTaskExecutionSegment::ordinal),
    )
}
