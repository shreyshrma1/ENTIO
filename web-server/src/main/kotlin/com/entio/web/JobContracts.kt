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

public data class WebReasoningFact(
    val kind: String,
    val subject: String,
    val subjectLabel: String? = null,
    val predicate: String? = null,
    val predicateLabel: String? = null,
    val objectValue: String,
    val objectLabel: String? = null,
    val origin: String,
    val sourceId: String? = null,
)

public data class WebInferenceSourceCandidate(
    val sourceId: String,
    val selected: Boolean,
)

public data class WebInferenceMaterializationCandidate(
    val factId: String,
    val kind: String,
    val subject: String,
    val subjectLabel: String,
    val predicate: String,
    val predicateLabel: String,
    val objectValue: String,
    val objectLabel: String,
    val origin: String = "Inferred",
    val stageability: String,
    val reason: String,
    val sourceCandidates: List<WebInferenceSourceCandidate>,
    val selectedSourceId: String? = null,
    val existingStagedChangeId: String? = null,
    val importDependence: String,
    val importSourceIds: List<String>,
)

public data class WebShaclFinding(
    val resultId: String,
    val severity: String,
    val message: String,
    val focusNode: String,
    val path: String? = null,
    val shapeIri: String,
    val shapeSourceId: String,
    val constraint: String,
    val value: String? = null,
    val sourceId: String? = null,
)

/** Bounded semantic details retained with a job; raw result graphs are never exposed. */
public data class WebSemanticJobDetails(
    val apiVersion: String = "v1",
    val job: WebSemanticJobStatus,
    val facts: List<WebReasoningFact> = emptyList(),
    val factOffset: Int = 0,
    val factLimit: Int = 50,
    val totalFactCount: Int = facts.size,
    val nextFactOffset: Int? = null,
    val materializationCandidates: List<WebInferenceMaterializationCandidate> = emptyList(),
    val unsatisfiableClasses: List<String> = emptyList(),
    val shaclFindings: List<WebShaclFinding> = emptyList(),
    val unsupportedFeatures: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val explanationsAvailable: Boolean = false,
    val truncated: Boolean = false,
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
