package com.entio.web.contract

public data class WebOntologyGraphNodeId(
    val id: String,
    val sourceId: String,
    val entityIri: String,
)

public data class WebOntologyGraphNodeSummary(
    val directSuperclassLabels: List<String>,
    val domainLabels: List<String>,
    val rangeLabels: List<String>,
    val assertedTypeLabels: List<String>,
    val datatypeRangeLabels: List<String>,
    val loadedRelationshipCount: Int,
    val availableRelationshipCount: Int,
)

public data class WebOntologyGraphNode(
    val identity: WebOntologyGraphNodeId,
    val kind: String,
    val label: String,
    val definitionExcerpt: String?,
    val summary: WebOntologyGraphNodeSummary,
)

public data class WebOntologyGraphEdge(
    val id: String,
    val kind: String,
    val sourceNodeId: String,
    val targetNodeId: String,
    val label: String,
    val predicateIri: String?,
    val provenance: String,
)

public data class WebOntologyGraphLimits(
    val nodeLimit: Int,
    val edgeLimit: Int,
)

public data class WebOntologyGraphSource(
    val id: String,
    val displayName: String,
)

public data class WebOntologyGraphResponse(
    val apiVersion: String = WEB_API_VERSION,
    val projectId: String,
    val graphFingerprint: String,
    val sources: List<WebOntologyGraphSource>,
    val loadKind: String,
    val seed: WebOntologyGraphNodeId?,
    val nodes: List<WebOntologyGraphNode>,
    val edges: List<WebOntologyGraphEdge>,
    val limits: WebOntologyGraphLimits,
    val totalNodeCount: Int,
    val totalEdgeCount: Int,
    val continuation: String?,
    val ambiguousCrossSourceRelationshipCount: Int,
    val inferredOverlays: List<WebInferredFactsOverlay> = emptyList(),
)
