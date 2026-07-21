package com.entio.web.contract

public data class WebAiProposalCreateRequest(
    val prompt: String,
    val runId: String? = null,
)

public enum class WebAiProposalStatus { QUEUED, RUNNING, READY, FAILED, CANCELLED, STAGED, REJECTED }

/** The semantic outcome selected for the user's request. */
public enum class WebAiResponseMode { ANSWER, PROPOSAL, CLARIFICATION }

public data class WebAiStatusUpdate(
    val order: Int,
    val message: String,
    val timestamp: String,
)

/** A server-verified reference used to ground an AI answer in ontology evidence. */
public data class WebAiEvidence(
    val subject: String,
    val predicate: String,
    val objectKind: String,
    val objectValue: String,
    val source: String = "current-ontology",
)

public data class WebAiConversationMessage(
    val role: String,
    val content: String,
    val timestamp: String,
    val evidence: List<WebAiEvidence> = emptyList(),
)

public data class WebAiProposalEdit(
    val id: String,
    val sourceId: String,
    val operation: String,
    val subject: String,
    val predicate: String,
    val objectKind: String,
    val objectValue: String,
    val datatype: String? = null,
    val language: String? = null,
    val summary: String,
    val rationale: String? = null,
)

public data class WebAiProposalValidation(
    val valid: Boolean,
    val messages: List<String> = emptyList(),
    val diff: List<WebDiffEntry> = emptyList(),
)

public data class WebAiProposalRunResponse(
    val apiVersion: String = WEB_API_VERSION,
    val runId: String,
    val projectId: String,
    val status: WebAiProposalStatus,
    val responseMode: WebAiResponseMode = WebAiResponseMode.PROPOSAL,
    val prompt: String? = null,
    val messages: List<WebAiConversationMessage> = emptyList(),
    val summary: String? = null,
    val updates: List<WebAiStatusUpdate> = emptyList(),
    val edits: List<WebAiProposalEdit> = emptyList(),
    val validation: WebAiProposalValidation? = null,
    val message: String? = null,
)
