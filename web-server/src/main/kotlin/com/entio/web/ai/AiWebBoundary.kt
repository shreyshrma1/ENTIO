package com.entio.web.ai

import com.entio.web.ReadOnlyProjectAdapter
import com.entio.web.contract.DevelopmentAuthorization
import com.entio.web.contract.WebAction
import com.entio.web.contract.WebAiAnalysisFinding
import com.entio.web.contract.WebAiAnalysisReference
import com.entio.web.contract.WebAiConversation
import com.entio.web.contract.WebAiConversationListResponse
import com.entio.web.contract.WebAiConversationMessage
import com.entio.web.contract.WebAiConversationResponse
import com.entio.web.contract.WebAiConversationTurnResponse
import com.entio.web.contract.WebAiDiffEntry
import com.entio.web.contract.WebAiDraft
import com.entio.web.contract.WebAiDraftAnalysis
import com.entio.web.contract.WebAiDraftAnalysisResponse
import com.entio.web.contract.WebAiDraftItem
import com.entio.web.contract.WebAiDraftResponse
import com.entio.web.contract.WebAiDraftRevision
import com.entio.web.contract.WebAiHelpEntry
import com.entio.web.contract.WebAiHelpResponse
import com.entio.web.contract.WebAiLimit
import com.entio.web.contract.WebAiMessageRequest
import com.entio.web.contract.WebAiPlan
import com.entio.web.contract.WebAiResynchronization
import com.entio.web.contract.WebAiReviewSubmissionRequest
import com.entio.web.contract.WebAiReviewSubmissionResponse
import com.entio.web.contract.WebAiRun
import com.entio.web.contract.WebAiRunEvent
import com.entio.web.contract.WebAiRunResponse
import com.entio.web.contract.WebAiScreenContextRequest
import com.entio.web.contract.WebAiStatusResponse
import com.entio.web.contract.WebAiSubmittedItemAttribution
import com.entio.web.contract.WebSessionUser
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal data class AiWebEventWindow(
    val run: WebAiRun,
    val events: List<WebAiRunEvent>,
    val resynchronization: WebAiResynchronization?,
)

private sealed interface IdempotencyStart {
    data object Accepted : IdempotencyStart
    data class Replay(val result: Any) : IdempotencyStart
}

private data class IdempotencyEntry(
    val fingerprint: String,
    var complete: Boolean = false,
    var result: Any? = null,
)

private class AiWebIdempotencyStore {
    private val entries: MutableMap<String, IdempotencyEntry> = linkedMapOf()

    @Synchronized
    fun begin(key: String, fingerprint: String): IdempotencyStart {
        if (key.isBlank() || key.length > 200) {
            throw AiConversationFailure("invalid-idempotency-key", "A bounded Idempotency-Key header is required.")
        }
        val existing = entries[key]
        if (existing == null) {
            entries[key] = IdempotencyEntry(fingerprint)
            return IdempotencyStart.Accepted
        }
        if (existing.fingerprint != fingerprint) {
            throw AiConversationFailure("idempotency-conflict", "The Idempotency-Key was already used for another request.")
        }
        if (!existing.complete) {
            throw AiConversationFailure("idempotency-in-progress", "The matching request is still in progress.")
        }
        return IdempotencyStart.Replay(requireNotNull(existing.result))
    }

    @Synchronized
    fun complete(key: String, result: Any) {
        val entry = entries.getValue(key)
        entry.result = result
        entry.complete = true
    }

    @Synchronized
    fun fail(key: String) {
        entries.remove(key)
    }
}

/** Maps authenticated web requests onto the server-owned Phase 7 conversation and draft services. */
internal class AiWebBoundary(
    private val readOnly: ReadOnlyProjectAdapter,
    private val authorization: DevelopmentAuthorization,
    private val conversations: AiConversationStore,
    private val runs: AiRunStore,
    private val drafts: AiDraftStore,
    private val conversationService: AiConversationService,
    private val analysisService: AiDraftAnalysisService,
    private val submissionService: AiReviewSubmissionService,
    private val baselineService: AiProjectBaselineService,
    private val help: EntioHelpService = EntioHelpService(),
) {
    private val idempotency: AiWebIdempotencyStore = AiWebIdempotencyStore()

    fun createConversation(user: WebSessionUser, projectId: String): WebAiConversationResponse {
        readOnly.summary(projectId)
        return WebAiConversationResponse(conversation = conversationService.createConversation(user.id, projectId).toWeb())
    }

    fun listConversations(user: WebSessionUser, projectId: String): WebAiConversationListResponse =
        WebAiConversationListResponse(conversations = conversationService.listConversations(user.id, projectId).map(AiConversation::toWeb))

    fun conversation(user: WebSessionUser, projectId: String, conversationId: String): WebAiConversationResponse =
        WebAiConversationResponse(conversation = conversationService.getConversation(user.id, projectId, conversationId).toWeb())

    fun deleteConversation(user: WebSessionUser, projectId: String, conversationId: String): WebAiStatusResponse {
        conversationService.deleteConversation(user.id, projectId, conversationId)
        return WebAiStatusResponse(status = "DELETED")
    }

    suspend fun sendMessage(
        user: WebSessionUser,
        projectId: String,
        conversationId: String,
        request: WebAiMessageRequest,
        idempotencyKey: String,
    ): WebAiConversationTurnResponse = idempotent(
        scopedKey(user.id, projectId, conversationId, "message", idempotencyKey),
        stableFingerprint(request),
    ) {
        val scope = currentScope(user, projectId, conversationId)
        conversationService.send(
            scope,
            conversationId,
            AiConversationTurnRequest(
                message = request.message,
                decision = enumValue(request.decision, "invalid-conversation-decision"),
            ),
            request.screenContext.toContext(user, scope.availableFeatures),
        ).toWeb()
    }

    fun run(user: WebSessionUser, projectId: String, runId: String): WebAiRunResponse {
        val run = conversationService.getRun(user.id, projectId, runId)
        conversationService.getConversation(user.id, projectId, run.conversationId)
        return WebAiRunResponse(run = run.toWeb())
    }

    fun cancelRun(user: WebSessionUser, projectId: String, runId: String): WebAiRunResponse =
        WebAiRunResponse(run = conversationService.cancel(user.id, projectId, runId).toWeb())

    fun events(user: WebSessionUser, projectId: String, runId: String, afterSequence: Int?): AiWebEventWindow {
        val window = conversationService.eventWindow(user.id, projectId, runId, afterSequence)
        conversationService.getConversation(user.id, projectId, window.run.conversationId)
        val resynchronization = if (window.resynchronizationRequired) {
            WebAiResynchronization(
                runId = runId,
                reason = "The requested event cursor is no longer retained. Refetch authoritative conversation, run, and draft state.",
                authoritativeRunRoute = "/api/v1/projects/$projectId/ai/runs/$runId",
                authoritativeConversationRoute = "/api/v1/projects/$projectId/ai/conversations/${window.run.conversationId}",
            )
        } else {
            null
        }
        return AiWebEventWindow(window.run.toWeb(), window.events.map(AiRunEvent::toWeb), resynchronization)
    }

    fun draft(user: WebSessionUser, projectId: String, draftId: String): WebAiDraftResponse =
        WebAiDraftResponse(draft = findDraft(user.id, projectId, draftId).toWeb())

    fun analyzeDraft(user: WebSessionUser, projectId: String, draftId: String): WebAiDraftAnalysisResponse {
        val draft = findDraft(user.id, projectId, draftId)
        val scope = currentScope(user, projectId, draft.conversationId)
        return WebAiDraftAnalysisResponse(analysis = analysisService.analyze(scope, draft.id).toWeb())
    }

    suspend fun submitDraft(
        user: WebSessionUser,
        projectId: String,
        draftId: String,
        request: WebAiReviewSubmissionRequest,
        idempotencyKey: String,
    ): WebAiReviewSubmissionResponse = idempotent(
        scopedKey(user.id, projectId, draftId, "submit", idempotencyKey),
        stableFingerprint(request),
    ) {
        val draft = findDraft(user.id, projectId, draftId)
        val run = runs.get(user.id, projectId, request.runId)
        if (run.conversationId != draft.conversationId) {
            throw AiDraftFailure("review-submission-scope-violation", "The selected run belongs to another conversation.")
        }
        submissionService.submit(
            run.scope,
            AiReviewSubmissionRequest(
                draftId = draft.id,
                analysisId = request.analysisId,
                runId = request.runId,
                rationale = request.rationale,
                expectedBaselineFingerprint = request.expectedBaselineFingerprint,
                expectedDraftFingerprint = request.expectedDraftFingerprint,
                expectedPreviewGraphFingerprint = request.expectedPreviewGraphFingerprint,
                expectedAnalysisReferenceIds = request.expectedAnalysisReferenceIds,
                explicitUserAction = true,
            ),
        ).toWeb()
    }

    fun help(user: WebSessionUser): WebAiHelpResponse {
        val permissions = authorization.permissionsFor(user.role).map(Enum<*>::name).toSet()
        val actions = WebAction.entries.filter { authorization.isAllowed(user.role, it) }.map(Enum<*>::name).toSet()
        return WebAiHelpResponse(
            entries = AiHelpTopic.entries.map { topic ->
                val entry = help.topic(topic)
                WebAiHelpEntry(
                    id = entry.id,
                    title = entry.title,
                    content = entry.content,
                    relatedActions = entry.relatedActions.filter(actions::contains),
                    relatedPermissions = entry.relatedPermissions.filter(permissions::contains),
                )
            },
        )
    }

    private fun currentScope(user: WebSessionUser, projectId: String, conversationId: String): AiCapabilityScope {
        val conversation = conversationService.getConversation(user.id, projectId, conversationId)
        val existing = runs.list(user.id, projectId)
            .filter { it.conversationId == conversationId && !it.status.terminal }
            .maxByOrNull(AiRun::createdAt)
        if (existing != null) return existing.scope
        val summary = readOnly.summary(projectId)
        val sourceIds = summary.sources.map { it.id }.distinct().sorted()
        val baseline = sourceIds.firstOrNull()
            ?.let { baselineService.current(projectId, it) }
            ?: throw AiDraftFailure("empty-project-sources", "The project has no ontology source available to the AI boundary.")
        return AiCapabilityScope(
            userId = user.id,
            projectId = projectId,
            conversationId = conversation.id,
            allowedSourceIds = sourceIds,
            baselineFingerprint = baseline,
            role = user.role.name,
            permissions = authorization.permissionsFor(user.role).map(Enum<*>::name).toSet(),
            availableFeatures = allFeatures,
            createdAt = conversation.createdAt,
        )
    }

    private fun WebAiScreenContextRequest.toContext(user: WebSessionUser, features: Set<String>): AiCurrentScreenContext =
        AiCurrentScreenContext(
            screen = enumValue(screen, "invalid-screen"),
            selectedEntityIri = selectedEntityIri,
            selectedSourceId = selectedSourceId,
            selectedProposalId = selectedProposalId,
            availableActions = WebAction.entries.filter { authorization.isAllowed(user.role, it) }.map(Enum<*>::name).toSet(),
            featureIds = features,
        )

    private fun findDraft(userId: String, projectId: String, draftId: String): AiDraft {
        val draft = conversations.list(userId, projectId).firstNotNullOfOrNull { conversation ->
            drafts.list(userId, projectId, conversation.id).firstOrNull { it.id == draftId }
        }
        return draft ?: throw AiDraftFailure("missing-draft", "Private draft '$draftId' was not found.")
    }

    private suspend fun <T : Any> idempotent(key: String, fingerprint: String, block: suspend () -> T): T {
        return when (val start = idempotency.begin(key, fingerprint)) {
            IdempotencyStart.Accepted -> try {
                block().also { idempotency.complete(key, it) }
            } catch (failure: Exception) {
                idempotency.fail(key)
                throw failure
            }
            is IdempotencyStart.Replay -> {
                @Suppress("UNCHECKED_CAST")
                start.result as T
            }
        }
    }

    private fun stableFingerprint(value: Any): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toString().toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun scopedKey(userId: String, projectId: String, resourceId: String, operation: String, key: String): String =
        listOf(userId, projectId, resourceId, operation, key).joinToString("|")

    private inline fun <reified T : Enum<T>> enumValue(value: String, code: String): T =
        enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: throw AiConversationFailure(code, "'$value' is not an allowed ${T::class.simpleName} value.")

    private companion object {
        val allFeatures: Set<String> = setOf(
            AiCapabilityFeatures.LOCAL_SEMANTIC_READ,
            AiCapabilityFeatures.ENTIO_HELP,
            AiCapabilityFeatures.SEMANTIC_RESULTS,
            AiCapabilityFeatures.PROPOSAL_READ,
            AiCapabilityFeatures.ACTIVITY_READ,
            AiCapabilityFeatures.FIBO_READ,
            AiCapabilityFeatures.PRIVATE_DRAFT,
        )
    }
}

private fun AiConversation.toWeb(): WebAiConversation = WebAiConversation(
    id = id,
    projectId = projectId,
    messages = messages.map { message ->
        WebAiConversationMessage(
            id = message.id,
            role = message.role.name,
            content = message.content,
            operation = message.operation?.name,
            evidenceReferenceIds = message.evidenceReferences,
            createdAt = message.createdAt.toString(),
        )
    },
    currentDraftId = currentDraftId,
    modelId = modelId,
    status = status.name,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)

private fun AiConversationTurnResult.toWeb(): WebAiConversationTurnResponse = WebAiConversationTurnResponse(
    conversation = conversation.toWeb(),
    run = run.toWeb(),
    intent = intent.name,
    answer = answer,
    plan = plan?.let { WebAiPlan(it.request, it.steps, it.openDecisions, it.estimatedEditCount) },
    clarificationQuestion = clarificationQuestion,
    draftId = draftId,
    limits = limits.map { WebAiLimit(it.kind, it.maximum, it.observed) },
)

private fun AiRun.toWeb(): WebAiRun = WebAiRun(
    id = id,
    conversationId = conversationId,
    projectId = projectId,
    status = status.name,
    capabilityCallCount = capabilityCallCount,
    draftEditCount = draftEditCount,
    correctionCycleCount = correctionCycleCount,
    cancellationRequested = cancellationRequested,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)

private fun AiRunEvent.toWeb(): WebAiRunEvent = WebAiRunEvent(
    sequence = sequence,
    runId = runId,
    type = type.name,
    message = message,
    referenceIds = referenceIds,
    createdAt = createdAt.toString(),
)

private fun AiDraft.toWeb(): WebAiDraft = WebAiDraft(
    id = id,
    conversationId = conversationId,
    projectId = projectId,
    baselineFingerprint = baselineFingerprint,
    allowedSourceIds = allowedSourceIds,
    status = status.name,
    draftFingerprint = draftFingerprint,
    analysisReferenceIds = analysisReferenceIds,
    items = items.map { item ->
        val typedOperation = item.operation as? AiTypedDraftOperation
        WebAiDraftItem(
            id = item.id,
            order = item.order,
            capabilityName = item.operation.capabilityName,
            targetSourceId = item.operation.targetSourceId,
            summary = item.operation.summary,
            editType = typedOperation?.request?.editType,
            targetLabel = typedOperation?.request?.targetLabel,
            targetIri = typedOperation?.request?.targetIri,
            value = typedOperation?.request?.value,
            rationale = item.rationale,
            dependencyItemIds = item.dependencyItemIds,
            aiGenerated = item.attribution?.aiGenerated == true,
            acceptingUserId = item.attribution?.acceptingUserId,
            runId = item.attribution?.runId,
        )
    },
    revisions = revisions.map { revision ->
        WebAiDraftRevision(
            revision = revision.revision,
            action = revision.action,
            explanation = revision.explanation,
            itemIds = revision.itemIds,
            undoneRevision = revision.undoneRevision,
            createdAt = revision.createdAt.toString(),
        )
    },
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)

private fun AiDraftAnalysis.toWeb(): WebAiDraftAnalysis = WebAiDraftAnalysis(
    id = id,
    draftId = draftId,
    revision = revision,
    status = status.name,
    baselineFingerprint = baselineFingerprint,
    draftFingerprint = draftFingerprint,
    previewGraphFingerprint = previewGraphFingerprint,
    readyForReview = readyForReview,
    validationOk = validationReport.ok,
    findings = findings.map { finding ->
        WebAiAnalysisFinding(finding.id, finding.code, finding.severity.name, finding.message, finding.source)
    },
    diff = semanticDiff?.entries.orEmpty().map { entry ->
        WebAiDiffEntry(entry.kind.name, entry.subject.value, entry.predicate?.value, entry.objectValue, entry.description)
    },
    references = references.map { reference -> WebAiAnalysisReference(reference.stage.name, reference.id) },
    createdAt = createdAt.toString(),
)

private fun AiReviewSubmissionResult.toWeb(): WebAiReviewSubmissionResponse = WebAiReviewSubmissionResponse(
    submissionId = submissionId,
    proposalId = proposalId,
    reviewState = reviewState,
    projectId = projectId,
    draftId = draftId,
    draftRevision = draftRevision,
    submittingUserId = submittingUserId,
    conversationId = conversationId,
    runId = runId,
    rationale = rationale,
    diff = semanticDiff.entries.map { entry ->
        WebAiDiffEntry(entry.kind.name, entry.subject.value, entry.predicate?.value, entry.objectValue, entry.description)
    },
    analysisReferenceIds = analysisReferenceIds,
    itemAttributions = itemAttributions.map { attribution ->
        WebAiSubmittedItemAttribution(
            itemId = attribution.itemId,
            acceptingUserId = attribution.acceptingUserId,
            conversationId = attribution.conversationId,
            runId = attribution.runId,
            rationale = attribution.rationale,
            aiGenerated = attribution.aiGenerated,
        )
    },
    reviewRoute = reviewRoute,
)
