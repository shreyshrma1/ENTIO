package com.entio.web.contract

public data class WebAiConversationMessage(
    val id: String,
    val role: String,
    val content: String,
    val operation: String?,
    val evidenceReferenceIds: List<String>,
    val createdAt: String,
)

public data class WebAiConversation(
    val id: String,
    val projectId: String,
    val messages: List<WebAiConversationMessage>,
    val currentDraftId: String?,
    val modelId: String?,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
)

public data class WebAiConversationResponse(
    val apiVersion: String = WEB_API_VERSION,
    val conversation: WebAiConversation,
)

public data class WebAiConversationListResponse(
    val apiVersion: String = WEB_API_VERSION,
    val conversations: List<WebAiConversation>,
)

public data class WebAiScreenContextRequest(
    val screen: String = "EXPLORE",
    val selectedEntityIri: String? = null,
    val selectedSourceId: String? = null,
    val selectedProposalId: String? = null,
)

public data class WebAiMessageRequest(
    val message: String,
    val decision: String = "MESSAGE",
    val screenContext: WebAiScreenContextRequest = WebAiScreenContextRequest(),
)

public data class WebAiPlan(
    val request: String,
    val steps: List<String>,
    val openDecisions: List<String>,
    val estimatedEditCount: Int?,
)

public data class WebAiLimit(
    val kind: String,
    val maximum: Long,
    val observed: Long,
)

public data class WebAiRun(
    val id: String,
    val conversationId: String,
    val projectId: String,
    val status: String,
    val capabilityCallCount: Int,
    val draftEditCount: Int,
    val correctionCycleCount: Int,
    val cancellationRequested: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

public data class WebAiConversationTurnResponse(
    val apiVersion: String = WEB_API_VERSION,
    val conversation: WebAiConversation,
    val run: WebAiRun,
    val intent: String,
    val answer: String,
    val plan: WebAiPlan?,
    val clarificationQuestion: String?,
    val draftId: String?,
    val limits: List<WebAiLimit>,
    val taskId: String? = null,
)

public data class WebAiRunResponse(
    val apiVersion: String = WEB_API_VERSION,
    val run: WebAiRun,
)

public data class WebAiStatusResponse(
    val apiVersion: String = WEB_API_VERSION,
    val status: String,
)

public data class WebAiDraftItem(
    val id: String,
    val order: Int,
    val capabilityName: String,
    val targetSourceId: String,
    val summary: String,
    val editType: String?,
    val targetLabel: String?,
    val targetIri: String?,
    val value: String?,
    val rationale: String,
    val dependencyItemIds: List<String>,
    val aiGenerated: Boolean,
    val acceptingUserId: String?,
    val runId: String?,
)

public data class WebAiDraftRevision(
    val revision: Int,
    val action: String,
    val explanation: String,
    val itemIds: List<String>,
    val undoneRevision: Int?,
    val createdAt: String,
)

public data class WebAiDraft(
    val id: String,
    val conversationId: String,
    val projectId: String,
    val baselineFingerprint: String,
    val allowedSourceIds: List<String>,
    val status: String,
    val draftFingerprint: String?,
    val analysisReferenceIds: List<String>,
    val items: List<WebAiDraftItem>,
    val revisions: List<WebAiDraftRevision>,
    val createdAt: String,
    val updatedAt: String,
)

public data class WebAiDraftResponse(
    val apiVersion: String = WEB_API_VERSION,
    val draft: WebAiDraft,
)

public data class WebAiAnalysisFinding(
    val id: String,
    val code: String,
    val severity: String,
    val message: String,
    val source: String?,
)

public data class WebAiDiffEntry(
    val kind: String,
    val subject: String,
    val predicate: String?,
    val objectValue: String?,
    val description: String,
)

public data class WebAiAnalysisReference(
    val stage: String,
    val id: String,
)

public data class WebAiDraftAnalysis(
    val id: String,
    val draftId: String,
    val revision: Int,
    val status: String,
    val baselineFingerprint: String,
    val draftFingerprint: String,
    val previewGraphFingerprint: String?,
    val readyForReview: Boolean,
    val validationOk: Boolean,
    val findings: List<WebAiAnalysisFinding>,
    val diff: List<WebAiDiffEntry>,
    val references: List<WebAiAnalysisReference>,
    val createdAt: String,
)

public data class WebAiDraftAnalysisResponse(
    val apiVersion: String = WEB_API_VERSION,
    val analysis: WebAiDraftAnalysis,
)

public data class WebAiReviewSubmissionRequest(
    val analysisId: String,
    val runId: String,
    val rationale: String,
    val expectedBaselineFingerprint: String,
    val expectedDraftFingerprint: String,
    val expectedPreviewGraphFingerprint: String,
    val expectedAnalysisReferenceIds: List<String>,
)

public data class WebAiSubmittedItemAttribution(
    val itemId: String,
    val acceptingUserId: String,
    val conversationId: String,
    val runId: String,
    val rationale: String,
    val aiGenerated: Boolean,
)

public data class WebAiReviewSubmissionResponse(
    val apiVersion: String = WEB_API_VERSION,
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
    val diff: List<WebAiDiffEntry>,
    val analysisReferenceIds: List<String>,
    val itemAttributions: List<WebAiSubmittedItemAttribution>,
    val reviewRoute: String,
)

public data class WebAiHelpEntry(
    val id: String,
    val title: String,
    val content: String,
    val relatedActions: List<String>,
    val relatedPermissions: List<String>,
)

public data class WebAiHelpResponse(
    val apiVersion: String = WEB_API_VERSION,
    val entries: List<WebAiHelpEntry>,
)

public data class WebAiRunEvent(
    val sequence: Int,
    val runId: String,
    val type: String,
    val message: String,
    val referenceIds: List<String>,
    val createdAt: String,
)

public data class WebAiResynchronization(
    val apiVersion: String = WEB_API_VERSION,
    val runId: String,
    val reason: String,
    val authoritativeRunRoute: String,
    val authoritativeConversationRoute: String,
)
