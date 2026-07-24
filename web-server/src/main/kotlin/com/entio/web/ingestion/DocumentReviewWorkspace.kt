package com.entio.web.ingestion

import com.entio.core.DocumentEvidenceReference
import com.entio.core.DocumentRecommendation
import com.entio.core.DocumentRecommendationReviewStatus
import com.entio.core.LocatedDocumentTextBlock
import com.entio.web.contract.WebPage
import com.entio.web.contract.WebPageRequest
import com.entio.web.contract.toWebPage
import java.time.Clock
import java.time.Instant

public data class DocumentReviewWorkspaceResponse(
    val apiVersion: String = "v1",
    val taskId: String,
    val projectId: String,
    val exactWorkKey: String,
    val graphFingerprint: String,
    val documents: List<DocumentReviewDocumentSummary>,
    val summaries: List<DocumentReviewSummary>,
    val recommendations: WebPage<DocumentReviewRecommendation>,
    val draftImpact: DocumentDraftImpact,
)

public data class DocumentReviewDocumentSummary(
    val documentId: String,
    val safeFilename: String,
    val mediaType: String,
    val authorityStatus: String,
    val pageCount: Int?,
    val warningCount: Int,
)

public data class DocumentReviewSummary(
    val documentId: String,
    val purpose: String,
    val highlights: List<String>,
)

public data class DocumentReviewRecommendation(
    val id: String,
    val category: String,
    val type: String,
    val action: String,
    val proposedLabel: String?,
    val confidence: Int,
    val confidenceBand: String,
    val rationale: String,
    val reviewStatus: String,
    val evidence: List<DocumentReviewEvidenceSummary>,
    val matches: List<DocumentReviewMatch>,
    val selectedMatchIri: String?,
    val conflicts: List<DocumentReviewConflict>,
    val mandatoryClarificationReasons: List<String>,
    val clarification: String?,
    val targetSourceId: String?,
    val reconsiderationCount: Int,
    val priorWorkflowProvenance: List<String>,
)

public data class DocumentReviewEvidenceSummary(
    val evidenceId: String,
    val evidenceType: String,
    val documentId: String?,
    val pageNumber: Int?,
    val extractionMethod: String?,
    val ocrConfidence: Int?,
    val excerpt: String?,
    val priorRecordId: String?,
)

public data class DocumentReviewMatch(
    val scope: String,
    val entityIri: String,
    val sourceId: String,
    val preferredLabel: String?,
    val score: Int,
    val reason: String,
)

public data class DocumentReviewConflict(
    val id: String,
    val alternatives: List<String>,
    val affectedEntityIris: List<String>,
    val resolutionOptions: List<String>,
)

public data class DocumentDraftImpact(
    val acceptedCount: Int,
    val pendingCount: Int,
    val blockedCount: Int,
    val maximumAcceptedEdits: Int = 100,
    val readOnly: Boolean = true,
)

public data class DocumentEvidenceViewResponse(
    val apiVersion: String = "v1",
    val evidenceId: String,
    val documentId: String,
    val safeFilename: String,
    val pageNumber: Int?,
    val sectionHeading: String?,
    val extractionMethod: String,
    val ocrConfidence: Int?,
    val text: String,
    val highlightStart: Int,
    val highlightEnd: Int,
    val pageImageAvailable: Boolean,
    val truncated: Boolean,
)

public data class DocumentReviewDecisionRequest(
    val action: String,
    val expectedWorkKey: String,
    val expectedGraphFingerprint: String,
    val proposedLabel: String? = null,
    val selectedMatchIri: String? = null,
    val targetSourceId: String? = null,
    val clarification: String? = null,
    val mergedRecommendationIds: List<String> = emptyList(),
)

internal data class DocumentReviewWorkspaceInput(
    val task: DocumentIngestionTaskSnapshot,
    val exactWorkKey: String,
    val graphFingerprint: String,
    val extractedDocuments: List<ExtractedDocument>,
    val summaries: List<VerifiedDocumentAnalysisSummary>,
    val recommendations: List<DocumentRecommendation>,
    val priorWorkflowProvenance: Map<String, List<String>> = emptyMap(),
)

private data class MutableReviewRecommendation(
    val source: DocumentRecommendation,
    var status: DocumentRecommendationReviewStatus = source.reviewStatus,
    var proposedLabel: String? = source.proposedLabel,
    var selectedMatchIri: String? = source.selectedMatch?.entityIri?.value,
    var targetSourceId: String? = source.targetSourceId,
    var clarification: String? = null,
    var reconsiderationCount: Int = 0,
)

private data class StoredReviewWorkspace(
    val projectId: String,
    val ownerUserId: String,
    val exactWorkKey: String,
    val graphFingerprint: String,
    val documents: List<DocumentReviewDocumentSummary>,
    val blocks: Map<String, LocatedDocumentTextBlock>,
    val evidence: Map<String, DocumentEvidenceReference>,
    val summaries: List<DocumentReviewSummary>,
    val recommendations: LinkedHashMap<String, MutableReviewRecommendation>,
    val priorWorkflowProvenance: Map<String, List<String>>,
    var updatedAt: Instant,
)

internal class DocumentReviewWorkspaceStore(
    private val clock: Clock = Clock.systemUTC(),
) {
    private val workspaces: MutableMap<String, StoredReviewWorkspace> = linkedMapOf()

    @Synchronized
    fun install(input: DocumentReviewWorkspaceInput): Unit {
        require(input.recommendations.size <= MAX_RECOMMENDATIONS)
        val task = input.task
        val extractedById = input.extractedDocuments.associateBy { it.document.id.value }
        require(task.documents.all { it.documentId in extractedById })
        val blocks = input.extractedDocuments.flatMap(ExtractedDocument::blocks).associateBy { it.id.value }
        val evidence = input.recommendations
            .flatMap(DocumentRecommendation::evidence)
            .flatMap { it.references }
            .associateBy { it.id.value }
        require(evidence.values.all { reference -> blocks[reference.blockId.value]?.documentId == reference.documentId })
        workspaces[task.taskId] = StoredReviewWorkspace(
            projectId = task.projectId,
            ownerUserId = task.ownerUserId,
            exactWorkKey = input.exactWorkKey,
            graphFingerprint = input.graphFingerprint,
            documents = task.documents.map { document ->
                val extracted = extractedById.getValue(document.documentId)
                DocumentReviewDocumentSummary(
                    document.documentId,
                    document.safeFilename,
                    document.mediaType,
                    document.authorityStatus,
                    extracted.blocks.mapNotNull(LocatedDocumentTextBlock::pageNumber).maxOrNull(),
                    extracted.warnings.size,
                )
            },
            blocks = blocks,
            evidence = evidence,
            summaries = input.summaries.sortedBy(VerifiedDocumentAnalysisSummary::documentId).map {
                DocumentReviewSummary(
                    documentId = it.documentId,
                    purpose = it.purpose.take(MAX_SUMMARY_CHARACTERS),
                    highlights = it.highlights.map(VerifiedDocumentAnalysisHighlight::text).take(MAX_HIGHLIGHTS),
                )
            },
            recommendations = input.recommendations
                .sortedBy(DocumentRecommendation::stableOrderingKey)
                .associateTo(linkedMapOf()) { it.id to MutableReviewRecommendation(it) },
            priorWorkflowProvenance = input.priorWorkflowProvenance.mapValues { (_, values) ->
                values.distinct().sorted().take(MAX_PRIOR_RECORDS)
            },
            updatedAt = Instant.now(clock),
        )
    }

    @Synchronized
    fun read(
        projectId: String,
        taskId: String,
        userId: String,
        page: WebPageRequest,
    ): DocumentReviewWorkspaceResponse {
        val workspace = owned(projectId, taskId, userId)
        return workspace.response(taskId, page)
    }

    @Synchronized
    fun evidence(
        projectId: String,
        taskId: String,
        userId: String,
        evidenceId: String,
    ): DocumentEvidenceViewResponse {
        val workspace = owned(projectId, taskId, userId)
        val reference = workspace.evidence[evidenceId]
            ?: throw DocumentIngestionFailure("document-evidence-not-found", "The requested evidence was not found.")
        val block = workspace.blocks[reference.blockId.value]
            ?: throw DocumentIngestionFailure("document-evidence-stale", "The requested evidence is no longer current.")
        val excerptStart = reference.startOffsetInBlock
        val windowStart = maxOf(0, excerptStart - EVIDENCE_CONTEXT_CHARACTERS)
        val windowEnd = minOf(block.exactText.length, reference.endOffsetInBlock + EVIDENCE_CONTEXT_CHARACTERS)
        val unbounded = block.exactText.substring(windowStart, windowEnd)
        val text = unbounded.take(MAX_EVIDENCE_VIEW_CHARACTERS)
        val highlightStart = (excerptStart - windowStart).coerceAtMost(text.length)
        val highlightEnd = (reference.endOffsetInBlock - windowStart).coerceAtMost(text.length)
        return DocumentEvidenceViewResponse(
            evidenceId = reference.id.value,
            documentId = reference.documentId.value,
            safeFilename = block.safeFilename,
            pageNumber = reference.pageNumber,
            sectionHeading = reference.sectionHeading,
            extractionMethod = reference.extractionMethod.name,
            ocrConfidence = reference.ocrConfidence,
            text = text,
            highlightStart = highlightStart,
            highlightEnd = highlightEnd,
            pageImageAvailable = block.pageImageId != null,
            truncated = unbounded.length > text.length,
        )
    }

    @Synchronized
    fun decide(
        projectId: String,
        taskId: String,
        recommendationId: String,
        userId: String,
        request: DocumentReviewDecisionRequest,
        page: WebPageRequest = WebPageRequest(),
    ): DocumentReviewWorkspaceResponse {
        val workspace = owned(projectId, taskId, userId)
        requireCurrent(workspace, request)
        val recommendation = workspace.recommendations[recommendationId]
            ?: throw DocumentIngestionFailure("document-recommendation-not-found", "The requested recommendation was not found.")
        when (request.action) {
            "accept" -> {
                if (recommendation.source.mandatoryClarificationReasons.isNotEmpty() && request.clarification.isNullOrBlank()) {
                    throw DocumentIngestionFailure("document-clarification-required", "Resolve mandatory clarification before accepting.")
                }
                recommendation.clarification = safeOptionalText(request.clarification)
                recommendation.status = DocumentRecommendationReviewStatus.Accepted
            }
            "reject" -> recommendation.status = DocumentRecommendationReviewStatus.Rejected
            "clarify" -> {
                recommendation.clarification = safeRequiredText(request.clarification, "Clarification")
                recommendation.status = DocumentRecommendationReviewStatus.NeedsClarification
            }
            "edit" -> {
                recommendation.proposedLabel = safeRequiredText(request.proposedLabel, "Proposed label", 500)
                recommendation.targetSourceId = safeOptionalText(request.targetSourceId)
                recommendation.clarification = safeOptionalText(request.clarification)
                recommendation.status = DocumentRecommendationReviewStatus.Pending
            }
            "rematch" -> {
                val iri = safeRequiredText(request.selectedMatchIri, "Selected match", 2_000)
                if (recommendation.source.matches.none { it.entityIri.value == iri }) {
                    throw DocumentIngestionFailure("document-match-invalid", "The selected ontology match is unavailable.")
                }
                recommendation.selectedMatchIri = iri
                recommendation.status = DocumentRecommendationReviewStatus.Pending
            }
            "merge" -> merge(workspace, recommendationId, request.mergedRecommendationIds)
            "reconsider" -> {
                safeRequiredText(request.clarification, "Clarification")
                if (recommendation.reconsiderationCount >= MAX_RECONSIDERATIONS) {
                    throw DocumentIngestionFailure("document-reconsideration-limit", "This recommendation reached its reconsideration limit.")
                }
                recommendation.reconsiderationCount += 1
                recommendation.clarification = request.clarification!!.trim()
                recommendation.status = DocumentRecommendationReviewStatus.Pending
            }
            else -> throw DocumentIngestionFailure("document-review-action-invalid", "The requested review action is unsupported.")
        }
        workspace.updatedAt = Instant.now(clock)
        return workspace.response(taskId, page)
    }

    @Synchronized
    fun remove(taskId: String): Unit {
        workspaces.remove(taskId)
    }

    private fun merge(workspace: StoredReviewWorkspace, primaryId: String, mergedIds: List<String>): Unit {
        val ids = mergedIds.distinct().sorted()
        if (ids.isEmpty() || primaryId in ids || ids.size > MAX_MERGED_RECOMMENDATIONS) {
            throw DocumentIngestionFailure("document-merge-invalid", "Choose bounded duplicate recommendations to merge.")
        }
        val primary = workspace.recommendations.getValue(primaryId)
        ids.forEach { id ->
            val duplicate = workspace.recommendations[id]
                ?: throw DocumentIngestionFailure("document-recommendation-not-found", "A duplicate recommendation was not found.")
            if (duplicate.source.category != primary.source.category || duplicate.source.type != primary.source.type) {
                throw DocumentIngestionFailure("document-merge-invalid", "Only recommendations with the same category and type can be merged.")
            }
            duplicate.status = DocumentRecommendationReviewStatus.Rejected
            duplicate.clarification = "Merged into $primaryId."
        }
        primary.status = DocumentRecommendationReviewStatus.Pending
    }

    private fun requireCurrent(workspace: StoredReviewWorkspace, request: DocumentReviewDecisionRequest): Unit {
        if (request.expectedWorkKey != workspace.exactWorkKey ||
            request.expectedGraphFingerprint != workspace.graphFingerprint
        ) {
            throw DocumentIngestionFailure("document-review-stale", "The review workspace changed; reload it before deciding.")
        }
    }

    private fun owned(projectId: String, taskId: String, userId: String): StoredReviewWorkspace =
        workspaces[taskId]
            ?.takeIf { it.projectId == projectId && it.ownerUserId == userId }
            ?: throw DocumentIngestionFailure("ingestion-task-not-found", "The requested ingestion task was not found.")

    private fun StoredReviewWorkspace.response(taskId: String, page: WebPageRequest): DocumentReviewWorkspaceResponse {
        val items = recommendations.values.map { it.response(priorWorkflowProvenance[it.source.id].orEmpty()) }
        return DocumentReviewWorkspaceResponse(
            taskId = taskId,
            projectId = projectId,
            exactWorkKey = exactWorkKey,
            graphFingerprint = graphFingerprint,
            documents = documents,
            summaries = summaries,
            recommendations = items.toWebPage(page),
            draftImpact = DocumentDraftImpact(
                acceptedCount = recommendations.values.count { it.status == DocumentRecommendationReviewStatus.Accepted },
                pendingCount = recommendations.values.count { it.status == DocumentRecommendationReviewStatus.Pending },
                blockedCount = recommendations.values.count {
                    it.source.mandatoryClarificationReasons.isNotEmpty() &&
                        it.status != DocumentRecommendationReviewStatus.Rejected
                },
            ),
        )
    }

    private fun MutableReviewRecommendation.response(prior: List<String>): DocumentReviewRecommendation =
        DocumentReviewRecommendation(
            id = source.id,
            category = source.category.name,
            type = source.type.name,
            action = source.action.name,
            proposedLabel = proposedLabel,
            confidence = source.confidence,
            confidenceBand = source.confidenceBand.name,
            rationale = source.rationale,
            reviewStatus = status.name,
            evidence = source.evidence.flatMap { group ->
                if (group.references.isEmpty()) {
                    listOf(DocumentReviewEvidenceSummary(group.id.value, group.type.name, null, null, null, null, null, group.entioRecordId))
                } else {
                    group.references.map { reference ->
                        DocumentReviewEvidenceSummary(
                            reference.id.value,
                            group.type.name,
                            reference.documentId.value,
                            reference.pageNumber,
                            reference.extractionMethod.name,
                            reference.ocrConfidence,
                            reference.exactExcerpt,
                            null,
                        )
                    }
                }
            }.take(MAX_EVIDENCE_SUMMARIES),
            matches = source.matches.map {
                DocumentReviewMatch(it.scope.name, it.entityIri.value, it.sourceId, it.preferredLabel, it.score, it.reason)
            },
            selectedMatchIri = selectedMatchIri,
            conflicts = source.conflicts.map { conflict ->
                DocumentReviewConflict(
                    conflict.id,
                    conflict.alternatives.map { it.description },
                    conflict.alternatives.flatMap { it.affectedEntityIris }.map { it.value }.distinct().sorted(),
                    conflict.resolutionOptions,
                )
            },
            mandatoryClarificationReasons = source.mandatoryClarificationReasons,
            clarification = clarification,
            targetSourceId = targetSourceId,
            reconsiderationCount = reconsiderationCount,
            priorWorkflowProvenance = prior,
        )

    private fun safeRequiredText(value: String?, label: String, maximum: Int = 2_000): String {
        val safe = value?.trim()
        if (safe.isNullOrBlank() || safe.length > maximum) {
            throw DocumentIngestionFailure("document-review-input-invalid", "$label must be nonblank and bounded.")
        }
        return safe
    }

    private fun safeOptionalText(value: String?, maximum: Int = 2_000): String? =
        value?.trim()?.takeIf(String::isNotBlank)?.also {
            if (it.length > maximum) {
                throw DocumentIngestionFailure("document-review-input-invalid", "Review input exceeds the approved bound.")
            }
        }

    private companion object {
        const val MAX_RECOMMENDATIONS: Int = 2_000
        const val MAX_RECONSIDERATIONS: Int = 3
        const val MAX_MERGED_RECOMMENDATIONS: Int = 20
        const val MAX_EVIDENCE_SUMMARIES: Int = 100
        const val MAX_PRIOR_RECORDS: Int = 100
        const val MAX_SUMMARY_CHARACTERS: Int = 2_000
        const val MAX_HIGHLIGHTS: Int = 100
        const val EVIDENCE_CONTEXT_CHARACTERS: Int = 2_000
        const val MAX_EVIDENCE_VIEW_CHARACTERS: Int = 8_000
    }
}
