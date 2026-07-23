package com.entio.web.contract

import com.entio.core.InferredFactsOverlay

/** Safe, bounded inferred facts returned by existing read endpoints. */
public data class WebInferredFact(
    val semanticFactKey: String,
    val subject: String,
    val predicate: String,
    val objectValue: String,
    val kind: String,
    val placements: Set<String>,
    val graphState: String,
    val sourceId: String? = null,
)

public data class WebInferredFactsOverlay(
    val graphState: String,
    val state: String,
    val facts: List<WebInferredFact> = emptyList(),
    val totalFactCount: Int = 0,
    val truncated: Boolean = false,
    val graphFingerprint: String? = null,
    val proposalFingerprint: String? = null,
    val message: String? = null,
)

public fun InferredFactsOverlay.toWeb(): WebInferredFactsOverlay = WebInferredFactsOverlay(
    graphState = graphState.name,
    state = state.name,
    facts = facts.map {
        WebInferredFact(
            semanticFactKey = it.semanticFactKey.value,
            subject = it.subject.value,
            predicate = it.predicate.value,
            objectValue = it.objectValue.value,
            kind = it.kind.name,
            placements = it.placements.mapTo(sortedSetOf()) { placement -> placement.name },
            graphState = it.graphState.name,
            sourceId = it.sourceId,
        )
    },
    totalFactCount = totalFactCount,
    truncated = truncated,
    graphFingerprint = graphFingerprint,
    proposalFingerprint = proposalFingerprint,
    message = message,
)
