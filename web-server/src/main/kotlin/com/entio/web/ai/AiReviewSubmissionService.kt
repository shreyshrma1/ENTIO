package com.entio.web.ai

import com.entio.core.SemanticDiff
import com.entio.web.AtomicReviewSubmissionEntry
import com.entio.web.CollaborationHub
import com.entio.web.StagingWorkflowService
import com.entio.web.WebWorkflowFailure
import com.entio.web.contract.WebPermission
import com.entio.web.contract.WebStagingResponse
import java.time.Clock
import java.time.Instant
import java.util.UUID

public data class AiReviewSubmissionRequest(
    val draftId: String,
    val analysisId: String,
    val runId: String,
    val rationale: String,
    val expectedBaselineFingerprint: String,
    val expectedDraftFingerprint: String,
    val expectedPreviewGraphFingerprint: String,
    val expectedAnalysisReferenceIds: List<String>,
    val explicitUserAction: Boolean,
)

public data class AiSubmittedItemAttribution(
    val itemId: String,
    val acceptingUserId: String,
    val conversationId: String,
    val runId: String,
    val rationale: String,
    val aiGenerated: Boolean = true,
)

public data class AiReviewSubmissionResult(
    val submissionId: String,
    val proposalId: String,
    val reviewState: String,
    val projectId: String,
    val draftId: String,
    val draftRevision: Int,
    val submittingUserId: String,
    val conversationId: String,
    val runId: String,
    val rationale: String,
    val semanticDiff: SemanticDiff,
    val analysisReferenceIds: List<String>,
    val itemAttributions: List<AiSubmittedItemAttribution>,
    val reviewRoute: String,
    val review: WebStagingResponse,
)

public data class AiReviewSubmissionAudit(
    val id: String,
    val proposalId: String,
    val projectId: String,
    val draftId: String,
    val draftRevision: Int,
    val submittingUserId: String,
    val conversationId: String,
    val runId: String,
    val rationale: String,
    val baselineFingerprint: String,
    val draftFingerprint: String,
    val analysisReferenceIds: List<String>,
    val createdAt: Instant,
)

public interface AiReviewSubmissionAuditStore {
    public fun append(record: AiReviewSubmissionAudit): AiReviewSubmissionAudit
    public fun get(userId: String, projectId: String, submissionId: String): AiReviewSubmissionAudit
    public fun list(userId: String, projectId: String): List<AiReviewSubmissionAudit>
}

public class InMemoryAiReviewSubmissionAuditStore : AiReviewSubmissionAuditStore {
    private val records: MutableMap<String, AiReviewSubmissionAudit> = linkedMapOf()

    @Synchronized
    override fun append(record: AiReviewSubmissionAudit): AiReviewSubmissionAudit {
        if (records.putIfAbsent(record.id, record) != null) {
            throw AiDraftFailure("duplicate-review-submission", "Review submission '${record.id}' already exists.")
        }
        return record
    }

    @Synchronized
    override fun get(userId: String, projectId: String, submissionId: String): AiReviewSubmissionAudit {
        val record = records[submissionId]
            ?: throw AiDraftFailure("missing-review-submission", "Review submission '$submissionId' was not found.")
        if (record.submittingUserId != userId || record.projectId != projectId) {
            throw AiDraftFailure("review-submission-scope-violation", "The review submission is outside the requested scope.")
        }
        return record
    }

    @Synchronized
    override fun list(userId: String, projectId: String): List<AiReviewSubmissionAudit> = records.values
        .filter { it.submittingUserId == userId && it.projectId == projectId }
        .sortedWith(compareBy(AiReviewSubmissionAudit::createdAt, AiReviewSubmissionAudit::id))
}

/** Moves one exact analyzed private draft into ordinary human review without granting review authority. */
public class AiReviewSubmissionService(
    private val conversations: AiConversationStore,
    private val drafts: AiDraftStore,
    private val draftWorkspace: AiPrivateDraftWorkspace,
    private val analyses: AiDraftAnalysisStore,
    private val runs: AiRunStore,
    private val baseline: AiProjectBaselineService,
    private val staging: StagingWorkflowService,
    private val collaboration: CollaborationHub,
    private val submissionAudits: AiReviewSubmissionAuditStore,
    private val clock: Clock = Clock.systemUTC(),
    private val idFactory: () -> String = { "ai-submission-${UUID.randomUUID()}" },
) {
    public suspend fun submit(scope: AiCapabilityScope, request: AiReviewSubmissionRequest): AiReviewSubmissionResult {
        if (!request.explicitUserAction) {
            throw AiDraftFailure("explicit-submission-required", "Submitting a private AI draft requires an explicit user action.")
        }
        if (WebPermission.STAGE_OWN_CHANGE.name !in scope.permissions) {
            throw AiDraftFailure("review-submission-forbidden", "The current user cannot submit staged changes for review.")
        }
        if (request.rationale.isBlank() || request.rationale.length > 4_000) {
            throw AiDraftFailure("invalid-submission-rationale", "A submission rationale between 1 and 4000 characters is required.")
        }

        val draft = drafts.get(scope.userId, scope.projectId, scope.conversationId, request.draftId)
        val conversation = conversations.get(scope.userId, scope.projectId, scope.conversationId)
        if (conversation.currentDraftId != draft.id) {
            throw AiDraftFailure("draft-not-current", "Only the conversation's current private draft can be submitted.")
        }
        val revision = draft.revisions.maxOfOrNull(AiDraftRevision::revision) ?: 0
        val draftFingerprint = draft.draftFingerprint
            ?: throw AiDraftFailure("incomplete-private-draft", "The private draft has no deterministic fingerprint.")
        if (draft.status != AiDraftStatus.READY_FOR_REVIEW || draft.items.isEmpty()) {
            throw AiDraftFailure("draft-not-ready-for-review", "Only a current, complete, fully analyzed private draft can be submitted.")
        }
        if (draft.allowedSourceIds.any { it !in scope.allowedSourceIds }) {
            throw AiDraftFailure("source-scope-violation", "The private draft includes a source outside the current run scope.")
        }
        if (draft.baselineFingerprint != scope.baselineFingerprint ||
            request.expectedBaselineFingerprint != draft.baselineFingerprint
        ) {
            throw AiDraftFailure("stale-draft-baseline", "The private draft baseline no longer matches the submitted scope.")
        }
        if (request.expectedDraftFingerprint != draftFingerprint) {
            throw AiDraftFailure("stale-draft-fingerprint", "The private draft changed after the submission view was prepared.")
        }
        val targetSourceId = draft.items.map(AiDraftItem::operation).map(AiDraftOperation::targetSourceId).sorted().first()
        if (baseline.current(scope.projectId, targetSourceId) != draft.baselineFingerprint) {
            drafts.update(draft.copy(status = AiDraftStatus.STALE, analysisReferenceIds = emptyList(), updatedAt = clock.instant()))
            throw AiDraftFailure("stale-draft-baseline", "The applied project changed after the private draft was analyzed.")
        }

        val analysis = analyses.get(scope.userId, scope.projectId, scope.conversationId, request.analysisId)
        val analysisReferences = analysis.references.map(AiDraftAnalysisReference::id).sorted()
        if (analysis.draftId != draft.id ||
            analysis.revision != revision ||
            analysis.baselineFingerprint != draft.baselineFingerprint ||
            analysis.draftFingerprint != draftFingerprint ||
            analysis.previewGraphFingerprint != request.expectedPreviewGraphFingerprint ||
            analysisReferences != request.expectedAnalysisReferenceIds.distinct().sorted() ||
            draft.analysisReferenceIds.sorted() != analysisReferences ||
            analysis.status != AiDraftAnalysisStatus.COMPLETED ||
            !analysis.readyForReview ||
            analysis.semanticDiff == null
        ) {
            throw AiDraftFailure("stale-or-incomplete-analysis", "The deterministic analysis does not match the exact submitted draft revision.")
        }

        val run = runs.get(scope.userId, scope.projectId, request.runId)
        if (run.conversationId != scope.conversationId || run.scope != scope || run.status != AiRunStatus.READY_FOR_REVIEW) {
            throw AiDraftFailure("invalid-submission-run", "The submission run does not match the current user, project, conversation, and scope.")
        }
        val itemAttributions = draft.items.map { item ->
            val attribution = item.attribution
                ?: throw AiDraftFailure("missing-ai-attribution", "Draft item '${item.id}' has no AI attribution.")
            if (!attribution.aiGenerated || attribution.acceptingUserId != scope.userId || attribution.conversationId != scope.conversationId) {
                throw AiDraftFailure("invalid-ai-attribution", "Draft item '${item.id}' has attribution outside the submitted scope.")
            }
            AiSubmittedItemAttribution(
                itemId = item.id,
                acceptingUserId = scope.userId,
                conversationId = scope.conversationId,
                runId = attribution.runId ?: request.runId,
                rationale = item.rationale,
            )
        }
        val (submitted, review) = draftWorkspace.submitForReview(scope, draft.id, revision, draftFingerprint) { lockedDraft ->
            try {
                staging.submitForReview(
                    scope.projectId,
                    lockedDraft.items.map { item ->
                        val operation = item.operation as? AiTypedDraftOperation
                            ?: throw AiDraftFailure("unsupported-draft-operation", "Draft item '${item.id}' is not an approved typed operation.")
                        AtomicReviewSubmissionEntry(operation.request, item.rationale)
                    },
                    scope.userId,
                )
            } catch (failure: WebWorkflowFailure) {
                throw AiDraftFailure(failure.code, failure.message ?: "The shared review workflow rejected the private draft.")
            }
        }
        val proposal = review.proposal
            ?: throw AiDraftFailure("missing-review-proposal", "The shared workflow did not return a review proposal.")
        val submissionId = idFactory()
        submissionAudits.append(
            AiReviewSubmissionAudit(
                id = submissionId,
                proposalId = proposal.id,
                projectId = scope.projectId,
                draftId = draft.id,
                draftRevision = revision,
                submittingUserId = scope.userId,
                conversationId = scope.conversationId,
                runId = request.runId,
                rationale = request.rationale.trim(),
                baselineFingerprint = draft.baselineFingerprint,
                draftFingerprint = draftFingerprint,
                analysisReferenceIds = analysisReferences,
                createdAt = clock.instant(),
            ),
        )
        collaboration.aiProposalSubmitted(scope.projectId, proposal.id, scope.userId, request.runId, request.rationale.trim())
        conversations.update(conversation.copy(currentDraftId = null, updatedAt = clock.instant()))
        return AiReviewSubmissionResult(
            submissionId = submissionId,
            proposalId = proposal.id,
            reviewState = proposal.status,
            projectId = scope.projectId,
            draftId = submitted.id,
            draftRevision = revision,
            submittingUserId = scope.userId,
            conversationId = scope.conversationId,
            runId = request.runId,
            rationale = request.rationale.trim(),
            semanticDiff = analysis.semanticDiff,
            analysisReferenceIds = analysisReferences,
            itemAttributions = itemAttributions,
            reviewRoute = "/projects/${scope.projectId}/changes?proposalId=${proposal.id}",
            review = review,
        )
    }
}
