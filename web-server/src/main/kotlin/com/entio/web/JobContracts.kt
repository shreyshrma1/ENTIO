package com.entio.web

import com.entio.core.ShaclValidationMode
import java.time.Instant

public enum class WebJobKind {
    Reasoning,
    Shacl,
}

public enum class WebJobScope {
    Applied,
    Proposal,
}

public enum class WebSemanticJobState {
    Queued,
    Running,
    Completed,
    Failed,
    Cancelled,
    Incomplete,
    Stale,
}

public data class WebJobRequest(
    val kind: String = "reasoning",
    val scope: String = "applied",
    val mode: String? = null,
)

public data class WebSemanticJobStatus(
    val apiVersion: String = "v1",
    val id: String,
    val projectId: String,
    val kind: WebJobKind,
    val scope: WebJobScope,
    val status: WebSemanticJobState,
    val phase: String,
    val message: String? = null,
    val graphFingerprint: String,
    val proposalFingerprint: String? = null,
    val queuedAt: String,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val resultSummary: Map<String, Any?> = emptyMap(),
    val error: String? = null,
)

internal data class ParsedWebJobRequest(
    val kind: WebJobKind,
    val scope: WebJobScope,
    val mode: ShaclValidationMode,
)

internal fun WebJobRequest.parse(): ParsedWebJobRequest {
    val parsedKind = WebJobKind.entries.firstOrNull { it.name.equals(kind, ignoreCase = true) }
        ?: throw WebWorkflowFailure("invalid-semantic-job-kind", "Unknown semantic job kind '$kind'.")
    val parsedScope = WebJobScope.entries.firstOrNull { it.name.equals(scope, ignoreCase = true) }
        ?: throw WebWorkflowFailure("invalid-semantic-job-scope", "Unknown semantic job scope '$scope'.")
    val parsedMode = when {
        parsedKind == WebJobKind.Reasoning -> ShaclValidationMode.AssertedOnly
        mode.isNullOrBlank() || mode.equals("asserted-only", ignoreCase = true) || mode.equals("assertedonly", ignoreCase = true) -> ShaclValidationMode.AssertedOnly
        mode.equals("asserted-and-inferred", ignoreCase = true) || mode.equals("assertedandinferred", ignoreCase = true) -> ShaclValidationMode.AssertedAndInferred
        else -> throw WebWorkflowFailure("invalid-shacl-mode", "Unknown SHACL validation mode '$mode'.")
    }
    return ParsedWebJobRequest(parsedKind, parsedScope, parsedMode)
}

internal fun Instant.asWebTimestamp(): String = toString()
