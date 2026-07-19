package com.entio.web.ai

import com.entio.core.StagedChangeOperation
import com.entio.web.contract.WebStageChangeRequest
import java.time.Instant

public enum class AiMessageRole {
    USER,
    ASSISTANT,
    TOOL,
}

public enum class AiConversationStatus {
    ACTIVE,
    CLOSED,
}

public data class AiConversationMessage(
    val id: String,
    val role: AiMessageRole,
    val content: String,
    val operation: AiOperationType? = null,
    val evidenceReferences: List<String> = emptyList(),
    val createdAt: Instant,
)

public data class AiConversation(
    val id: String,
    val userId: String,
    val projectId: String,
    val messages: List<AiConversationMessage> = emptyList(),
    val currentDraftId: String? = null,
    val modelId: String? = null,
    val promptVersion: String,
    val providerResponseIds: List<String> = emptyList(),
    val status: AiConversationStatus = AiConversationStatus.ACTIVE,
    val createdAt: Instant,
    val updatedAt: Instant,
)

public enum class AiRunStatus {
    QUEUED,
    RUNNING,
    AWAITING_CLARIFICATION,
    AWAITING_PLAN_CONFIRMATION,
    CALLING_TOOL,
    VALIDATING_DRAFT,
    RUNNING_REASONING,
    RUNNING_SHACL,
    REVISING_DRAFT,
    READY_FOR_REVIEW,
    FAILED,
    CANCELLED,
    LIMIT_REACHED,
    STALE,
    ;

    public val terminal: Boolean
        get() = this in setOf(READY_FOR_REVIEW, FAILED, CANCELLED, LIMIT_REACHED, STALE)

    public fun canTransitionTo(next: AiRunStatus): Boolean = when {
        this == next -> true
        terminal -> false
        this == QUEUED -> next in setOf(
            RUNNING,
            AWAITING_CLARIFICATION,
            AWAITING_PLAN_CONFIRMATION,
            CANCELLED,
            FAILED,
            LIMIT_REACHED,
            STALE,
        )
        else -> next != QUEUED
    }
}

public data class AiRunPolicy(
    val maxCapabilityCallsPerTurn: Int = 20,
    val maxDraftEditsPerRun: Int = 50,
    val maxCorrectionCycles: Int = 3,
    val maxActiveRunsPerUserProject: Int = 1,
    val maxLocalEntitiesInContext: Int = 20,
    val maxFiboCandidatesPerSearch: Int = 10,
    val maxProviderRequestsPerTurn: Int = 12,
    val maxConversationMessagesInContext: Int = 20,
    val maxElapsedMillis: Long = 120_000,
    val maxInputTokens: Long = 100_000,
    val maxOutputTokens: Long = 20_000,
) {
    init {
        require(maxCapabilityCallsPerTurn > 0)
        require(maxDraftEditsPerRun > 0)
        require(maxCorrectionCycles >= 0)
        require(maxActiveRunsPerUserProject > 0)
        require(maxLocalEntitiesInContext > 0)
        require(maxFiboCandidatesPerSearch > 0)
        require(maxProviderRequestsPerTurn > 0)
        require(maxConversationMessagesInContext > 0)
        require(maxElapsedMillis > 0)
        require(maxInputTokens > 0)
        require(maxOutputTokens > 0)
    }
}

public data class AiCapabilityScope(
    val userId: String,
    val projectId: String,
    val conversationId: String,
    val allowedSourceIds: List<String>,
    val baselineFingerprint: String,
    val collaborationSessionId: String? = null,
    val role: String,
    val permissions: Set<String>,
    val availableFeatures: Set<String>,
    val createdAt: Instant,
)

public data class AiRun(
    val id: String,
    val conversationId: String,
    val userId: String,
    val projectId: String,
    val scope: AiCapabilityScope,
    val modelBinding: AiRunModelBinding = AiRunModelBinding(
        providerId = "provider-neutral",
        modelId = "development-ai",
        catalogVersion = "deterministic-provider",
        credentialGeneration = 0,
        promptVersion = "phase-7-development-v1",
        requestPolicyVersion = "phase-7-request-policy-v1",
    ),
    val policy: AiRunPolicy = AiRunPolicy(),
    val status: AiRunStatus = AiRunStatus.QUEUED,
    val capabilityCallCount: Int = 0,
    val draftEditCount: Int = 0,
    val correctionCycleCount: Int = 0,
    val cancellationRequested: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

public data class AiRunModelBinding(
    val providerId: String,
    val modelId: String,
    val catalogVersion: String,
    val credentialGeneration: Long,
    val promptVersion: String,
    val requestPolicyVersion: String,
)

public enum class AiDraftStatus {
    EMPTY,
    EDITING,
    INVALID,
    ANALYZING,
    READY_FOR_REVIEW,
    STALE,
    CONFLICTED,
    SUBMITTED,
}

/** A reference to an approved typed operation. It never contains raw RDF or source text. */
public sealed interface AiDraftOperation {
    public val capabilityName: String
    public val targetSourceId: String
    public val summary: String
}

public data class AiDraftOperationReference(
    override val capabilityName: String,
    override val targetSourceId: String,
    override val summary: String,
    val typedRequestId: String,
) : AiDraftOperation

/** A side-effect-free typed operation prepared through the ordinary staging adapter. */
public data class AiTypedDraftOperation(
    override val capabilityName: String,
    override val targetSourceId: String,
    override val summary: String,
    val request: WebStageChangeRequest,
    val preparedOperation: StagedChangeOperation,
    val normalizedValues: Map<String, String>,
    val generatedIris: List<String> = emptyList(),
) : AiDraftOperation

public data class AiDraftAttribution(
    val aiGenerated: Boolean = true,
    val acceptingUserId: String,
    val conversationId: String,
    val runId: String? = null,
    val taskId: String? = null,
    val workPackageId: String? = null,
    val executionSegmentId: String? = null,
)

public data class AiDraftItem(
    val id: String,
    val order: Int,
    val operation: AiDraftOperation,
    val rationale: String,
    val dependencyItemIds: List<String> = emptyList(),
    val attribution: AiDraftAttribution? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

public data class AiDraftRevision(
    val revision: Int,
    val action: String,
    val explanation: String,
    val itemIds: List<String>,
    val createdAt: Instant,
    val beforeItems: List<AiDraftItem> = emptyList(),
    val afterItems: List<AiDraftItem> = emptyList(),
    val undoneRevision: Int? = null,
)

public data class AiDraft(
    val id: String,
    val conversationId: String,
    val userId: String,
    val projectId: String,
    val baselineFingerprint: String,
    val allowedSourceIds: List<String>,
    val items: List<AiDraftItem> = emptyList(),
    val revisions: List<AiDraftRevision> = emptyList(),
    val status: AiDraftStatus = AiDraftStatus.EMPTY,
    val draftFingerprint: String? = null,
    val analysisReferenceIds: List<String> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
)

public data class AiAuditCapabilityCall(
    val capabilityName: String,
    val outcome: String,
    val resultReferenceId: String? = null,
)

public data class AiAuditRecord(
    val id: String,
    val runId: String,
    val conversationId: String,
    val userId: String,
    val projectId: String,
    val modelId: String,
    val catalogVersion: String = "legacy",
    val requestPolicyVersion: String = "legacy",
    val credentialGeneration: Long = 0,
    val promptVersion: String,
    val allowedCapabilities: List<String>,
    val capabilityCalls: List<AiAuditCapabilityCall> = emptyList(),
    val draftRevisionNumbers: List<Int> = emptyList(),
    val resultReferenceIds: List<String> = emptyList(),
    val status: AiRunStatus,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val createdAt: Instant,
    val completedAt: Instant? = null,
)

public data class AiWarning(
    val code: String,
    val message: String,
)

public data class AiLimit(
    val kind: String,
    val maximum: Long,
    val observed: Long,
)

public data class AiNextAction(
    val action: String,
    val label: String,
    val requiresConfirmation: Boolean = false,
)

public data class AiResponse(
    val answer: String,
    val operation: AiOperationType,
    val evidence: List<AiEvidence> = emptyList(),
    val assertedFacts: List<String> = emptyList(),
    val inferredFacts: List<String> = emptyList(),
    val shaclFindingReferences: List<String> = emptyList(),
    val fiboResults: List<AiEvidence> = emptyList(),
    val draftId: String? = null,
    val uncertainty: List<String> = emptyList(),
    val warnings: List<AiWarning> = emptyList(),
    val limits: List<AiLimit> = emptyList(),
    val nextActions: List<AiNextAction> = emptyList(),
)
