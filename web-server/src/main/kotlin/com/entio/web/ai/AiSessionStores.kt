package com.entio.web.ai

public class AiStateAccessFailure(
    public val code: String,
    message: String,
) : IllegalArgumentException(message)

public interface AiConversationStore {
    public fun create(conversation: AiConversation): AiConversation
    public fun get(userId: String, projectId: String, conversationId: String): AiConversation
    public fun update(conversation: AiConversation): AiConversation
    public fun list(userId: String, projectId: String): List<AiConversation>
    public fun delete(userId: String, projectId: String, conversationId: String)
}

public class InMemoryAiConversationStore : AiConversationStore {
    private val conversations: MutableMap<String, AiConversation> = linkedMapOf()

    @Synchronized
    override fun create(conversation: AiConversation): AiConversation {
        if (conversations.putIfAbsent(conversation.id, conversation) != null) {
            throw AiStateAccessFailure("duplicate-conversation", "Conversation '${conversation.id}' already exists.")
        }
        return conversation
    }

    @Synchronized
    override fun get(userId: String, projectId: String, conversationId: String): AiConversation =
        owned(conversations[conversationId], userId, projectId, "conversation", conversationId)

    @Synchronized
    override fun update(conversation: AiConversation): AiConversation {
        val current = conversations[conversation.id]
            ?: throw AiStateAccessFailure("missing-conversation", "Conversation '${conversation.id}' was not found.")
        ensureSameOwner(current.userId, current.projectId, conversation.userId, conversation.projectId, "conversation")
        conversations[conversation.id] = conversation.copy(
            messages = conversation.messages.sortedWith(compareBy(AiConversationMessage::createdAt, AiConversationMessage::id)),
            providerResponseIds = conversation.providerResponseIds.distinct(),
        )
        return conversations.getValue(conversation.id)
    }

    @Synchronized
    override fun list(userId: String, projectId: String): List<AiConversation> = conversations.values
        .filter { it.userId == userId && it.projectId == projectId }
        .sortedWith(compareBy(AiConversation::createdAt, AiConversation::id))

    @Synchronized
    override fun delete(userId: String, projectId: String, conversationId: String) {
        get(userId, projectId, conversationId)
        conversations.remove(conversationId)
    }
}

public interface AiRunStore {
    public fun create(run: AiRun): AiRun
    public fun get(userId: String, projectId: String, runId: String): AiRun
    public fun transition(userId: String, projectId: String, runId: String, status: AiRunStatus): AiRun
    public fun cancel(userId: String, projectId: String, runId: String): AiRun
    public fun list(userId: String, projectId: String): List<AiRun>
}

public class InMemoryAiRunStore : AiRunStore {
    private val runs: MutableMap<String, AiRun> = linkedMapOf()

    @Synchronized
    override fun create(run: AiRun): AiRun {
        val active = runs.values.count { it.userId == run.userId && it.projectId == run.projectId && !it.status.terminal }
        if (active >= run.policy.maxActiveRunsPerUserProject) {
            throw AiStateAccessFailure("active-run-limit", "Only one active AI run is allowed for this user and project.")
        }
        if (runs.putIfAbsent(run.id, run) != null) {
            throw AiStateAccessFailure("duplicate-run", "Run '${run.id}' already exists.")
        }
        return run
    }

    @Synchronized
    override fun get(userId: String, projectId: String, runId: String): AiRun =
        owned(runs[runId], userId, projectId, "run", runId)

    @Synchronized
    override fun transition(userId: String, projectId: String, runId: String, status: AiRunStatus): AiRun {
        val current = get(userId, projectId, runId)
        if (!current.status.canTransitionTo(status)) {
            throw AiStateAccessFailure(
                "invalid-run-transition",
                "Run '$runId' cannot transition from ${current.status} to $status.",
            )
        }
        val updated = current.copy(status = status)
        runs[runId] = updated
        return updated
    }

    @Synchronized
    override fun cancel(userId: String, projectId: String, runId: String): AiRun {
        val current = get(userId, projectId, runId)
        if (current.status.terminal) return current
        val updated = current.copy(status = AiRunStatus.CANCELLED, cancellationRequested = true)
        runs[runId] = updated
        return updated
    }

    @Synchronized
    override fun list(userId: String, projectId: String): List<AiRun> = runs.values
        .filter { it.userId == userId && it.projectId == projectId }
        .sortedWith(compareBy(AiRun::createdAt, AiRun::id))
}

public interface AiDraftStore {
    public fun create(draft: AiDraft): AiDraft
    public fun get(userId: String, projectId: String, conversationId: String, draftId: String): AiDraft
    public fun update(draft: AiDraft): AiDraft
    public fun list(userId: String, projectId: String, conversationId: String): List<AiDraft>
    public fun delete(userId: String, projectId: String, conversationId: String, draftId: String)
}

public class InMemoryAiDraftStore : AiDraftStore {
    private val drafts: MutableMap<String, AiDraft> = linkedMapOf()

    @Synchronized
    override fun create(draft: AiDraft): AiDraft {
        if (drafts.putIfAbsent(draft.id, draft) != null) {
            throw AiStateAccessFailure("duplicate-draft", "Draft '${draft.id}' already exists.")
        }
        return draft
    }

    @Synchronized
    override fun get(userId: String, projectId: String, conversationId: String, draftId: String): AiDraft {
        val draft = owned(drafts[draftId], userId, projectId, "draft", draftId)
        if (draft.conversationId != conversationId) {
            throw AiStateAccessFailure("draft-scope-violation", "Draft '$draftId' does not belong to this conversation.")
        }
        return draft
    }

    @Synchronized
    override fun update(draft: AiDraft): AiDraft {
        val current = drafts[draft.id]
            ?: throw AiStateAccessFailure("missing-draft", "Draft '${draft.id}' was not found.")
        ensureSameOwner(current.userId, current.projectId, draft.userId, draft.projectId, "draft")
        if (current.conversationId != draft.conversationId) {
            throw AiStateAccessFailure("draft-scope-violation", "A draft cannot move to another conversation.")
        }
        val normalized = draft.copy(
            items = draft.items.sortedWith(compareBy(AiDraftItem::order, AiDraftItem::id)),
            revisions = draft.revisions.sortedBy(AiDraftRevision::revision),
            allowedSourceIds = draft.allowedSourceIds.distinct().sorted(),
            analysisReferenceIds = draft.analysisReferenceIds.distinct().sorted(),
        )
        drafts[draft.id] = normalized
        return normalized
    }

    @Synchronized
    override fun list(userId: String, projectId: String, conversationId: String): List<AiDraft> = drafts.values
        .filter { it.userId == userId && it.projectId == projectId && it.conversationId == conversationId }
        .sortedWith(compareBy(AiDraft::createdAt, AiDraft::id))

    @Synchronized
    override fun delete(userId: String, projectId: String, conversationId: String, draftId: String) {
        get(userId, projectId, conversationId, draftId)
        drafts.remove(draftId)
    }
}

public interface AiAuditStore {
    public fun append(record: AiAuditRecord): AiAuditRecord
    public fun get(userId: String, projectId: String, recordId: String): AiAuditRecord
    public fun list(userId: String, projectId: String): List<AiAuditRecord>
}

public class InMemoryAiAuditStore : AiAuditStore {
    private val records: MutableMap<String, AiAuditRecord> = linkedMapOf()

    @Synchronized
    override fun append(record: AiAuditRecord): AiAuditRecord {
        if (records.putIfAbsent(record.id, record) != null) {
            throw AiStateAccessFailure("duplicate-audit-record", "Audit record '${record.id}' already exists.")
        }
        return record
    }

    @Synchronized
    override fun get(userId: String, projectId: String, recordId: String): AiAuditRecord =
        owned(records[recordId], userId, projectId, "audit record", recordId)

    @Synchronized
    override fun list(userId: String, projectId: String): List<AiAuditRecord> = records.values
        .filter { it.userId == userId && it.projectId == projectId }
        .sortedWith(compareBy(AiAuditRecord::createdAt, AiAuditRecord::id))
}

private fun ensureSameOwner(
    currentUserId: String,
    currentProjectId: String,
    requestedUserId: String,
    requestedProjectId: String,
    resource: String,
) {
    if (currentUserId != requestedUserId || currentProjectId != requestedProjectId) {
        throw AiStateAccessFailure("$resource-scope-violation", "The $resource cannot move across user or project scope.")
    }
}

private fun <T> owned(value: T?, userId: String, projectId: String, resource: String, resourceId: String): T {
    val current = value ?: throw AiStateAccessFailure("missing-${resource.replace(' ', '-')}", "The $resource '$resourceId' was not found.")
    val owner = when (current) {
        is AiConversation -> current.userId to current.projectId
        is AiRun -> current.userId to current.projectId
        is AiDraft -> current.userId to current.projectId
        is AiAuditRecord -> current.userId to current.projectId
        else -> throw IllegalArgumentException("Unsupported AI state resource.")
    }
    if (owner.first != userId || owner.second != projectId) {
        throw AiStateAccessFailure("${resource.replace(' ', '-')}-scope-violation", "The $resource is outside the requested scope.")
    }
    return current
}
