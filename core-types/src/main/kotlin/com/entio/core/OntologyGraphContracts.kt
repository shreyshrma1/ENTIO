package com.entio.core

/** Entity kinds that Phase 9 can expose in the read-only ontology graph. */
public enum class OntologyGraphNodeKind {
    Class,
    ObjectProperty,
    DatatypeProperty,
    Individual,
}

/** Asserted relationship meanings supported by the Phase 9 graph. */
public enum class OntologyGraphEdgeKind {
    SubclassOf,
    Domain,
    Range,
    Type,
    ObjectAssertion,
}

public enum class OntologyGraphProvenance {
    Asserted,
    Inferred,
}

public enum class OntologyGraphLoadKind {
    RootOverview,
    EntityCentered,
    Neighborhood,
}

public enum class OntologyGraphExpansionCategory {
    ClassHierarchy,
    PropertySchema,
    AssertedTypes,
    ObjectAssertions,
}

/** Stable graph identity; labels are deliberately excluded from identity. */
public data class OntologyGraphNodeId(
    public val sourceId: String,
    public val entityIri: Iri,
) {
    init {
        require(sourceId.isNotBlank()) { "source-id-must-not-be-blank" }
    }

    public val stableKey: String
        get() = "$sourceId\u0000${entityIri.value}"
}

/** Bounded read-only metadata used by nodes and their temporary information pop-up. */
public data class OntologyGraphNodeSummary(
    public val directSuperclassLabels: List<String> = emptyList(),
    public val domainLabels: List<String> = emptyList(),
    public val rangeLabels: List<String> = emptyList(),
    public val assertedTypeLabels: List<String> = emptyList(),
    public val datatypeRangeLabels: List<String> = emptyList(),
    public val loadedRelationshipCount: Int = 0,
    public val availableRelationshipCount: Int = 0,
) {
    init {
        require(loadedRelationshipCount >= 0) { "loaded-relationship-count-must-not-be-negative" }
        require(availableRelationshipCount >= 0) { "available-relationship-count-must-not-be-negative" }
        require(loadedRelationshipCount <= availableRelationshipCount) {
            "loaded-relationship-count-must-not-exceed-available-count"
        }
    }
}

public data class OntologyGraphNode(
    public val id: OntologyGraphNodeId,
    public val kind: OntologyGraphNodeKind,
    public val label: String,
    public val definitionExcerpt: String? = null,
    public val summary: OntologyGraphNodeSummary = OntologyGraphNodeSummary(),
) {
    init {
        require(label.isNotBlank()) { "graph-node-label-must-not-be-blank" }
        require(definitionExcerpt == null || definitionExcerpt.isNotBlank()) {
            "definition-excerpt-must-not-be-blank"
        }
    }
}

public data class OntologyGraphEdge(
    public val id: String,
    public val kind: OntologyGraphEdgeKind,
    public val source: OntologyGraphNodeId,
    public val target: OntologyGraphNodeId,
    public val label: String,
    public val predicateIri: Iri? = null,
    public val provenance: OntologyGraphProvenance = OntologyGraphProvenance.Asserted,
    public val inferredGraphState: InferredGraphState? = null,
) {
    init {
        require(id.isNotBlank()) { "graph-edge-id-must-not-be-blank" }
        require(label.isNotBlank()) { "graph-edge-label-must-not-be-blank" }
        require(source != target) { "graph-edge-endpoints-must-be-distinct" }
        require(kind == OntologyGraphEdgeKind.ObjectAssertion || predicateIri == null) {
            "predicate-iri-is-only-supported-for-object-assertions"
        }
        require(kind != OntologyGraphEdgeKind.ObjectAssertion || predicateIri != null) {
            "object-assertion-requires-predicate-iri"
        }
        require(provenance == OntologyGraphProvenance.Inferred || inferredGraphState == null) {
            "asserted-edge-must-not-carry-inferred-graph-state"
        }
        require(provenance != OntologyGraphProvenance.Inferred || inferredGraphState != null) {
            "inferred-edge-requires-graph-state"
        }
    }
}

public data class OntologyGraphLimits(
    public val nodeLimit: Int,
    public val edgeLimit: Int,
) {
    init {
        require(nodeLimit > 0) { "graph-node-limit-must-be-positive" }
        require(edgeLimit > 0) { "graph-edge-limit-must-be-positive" }
    }

    public companion object {
        public val Initial: OntologyGraphLimits = OntologyGraphLimits(nodeLimit = 75, edgeLimit = 150)
        public val Expansion: OntologyGraphLimits = OntologyGraphLimits(nodeLimit = 50, edgeLimit = 100)
        public val OpenTab: OntologyGraphLimits = OntologyGraphLimits(nodeLimit = 300, edgeLimit = 600)
    }
}

/** Stable semantic-engine cursor state; web adapters replace it with an opaque server-owned ID. */
public data class OntologyGraphPageCursor(
    public val nodeOffset: Int = 0,
    public val edgeOffset: Int = 0,
) {
    init {
        require(nodeOffset >= 0) { "graph-node-offset-must-not-be-negative" }
        require(edgeOffset >= 0) { "graph-edge-offset-must-not-be-negative" }
    }
}

/** Opaque continuation value exposed only by an adapter boundary. */
public data class OntologyGraphContinuation(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) { "graph-continuation-must-not-be-blank" }
    }
}

public data class OntologyGraphInitialQuery(
    public val sourceIds: Set<String>,
    public val seed: OntologyGraphNodeId? = null,
    public val cursor: OntologyGraphPageCursor = OntologyGraphPageCursor(),
    public val limits: OntologyGraphLimits = OntologyGraphLimits.Initial,
) {
    init {
        require(sourceIds.isNotEmpty()) { "graph-query-requires-a-source" }
        require(sourceIds.none(String::isBlank)) { "graph-query-source-must-not-be-blank" }
        require(seed == null || seed.sourceId in sourceIds) { "graph-seed-must-belong-to-selected-sources" }
    }
}

public data class OntologyGraphNeighborhoodQuery(
    public val sourceIds: Set<String>,
    public val entity: OntologyGraphNodeId,
    public val categories: Set<OntologyGraphExpansionCategory>,
    public val cursor: OntologyGraphPageCursor = OntologyGraphPageCursor(),
    public val limits: OntologyGraphLimits = OntologyGraphLimits.Expansion,
) {
    init {
        require(sourceIds.isNotEmpty()) { "graph-query-requires-a-source" }
        require(sourceIds.none(String::isBlank)) { "graph-query-source-must-not-be-blank" }
        require(entity.sourceId in sourceIds) { "graph-entity-must-belong-to-selected-sources" }
        require(categories.isNotEmpty()) { "graph-neighborhood-requires-a-category" }
    }
}

public data class OntologyGraphPage(
    public val loadKind: OntologyGraphLoadKind,
    public val seed: OntologyGraphNodeId?,
    public val nodes: List<OntologyGraphNode>,
    public val edges: List<OntologyGraphEdge>,
    public val totalNodeCount: Int,
    public val totalEdgeCount: Int,
    public val nextCursor: OntologyGraphPageCursor? = null,
    public val ambiguousCrossSourceRelationshipCount: Int = 0,
    public val inferredOverlays: List<InferredOverlaySummary> = emptyList(),
) {
    init {
        require(totalNodeCount >= nodes.size) { "total-node-count-must-cover-page" }
        require(totalEdgeCount >= edges.size) { "total-edge-count-must-cover-page" }
        require(ambiguousCrossSourceRelationshipCount >= 0) {
            "ambiguous-cross-source-count-must-not-be-negative"
        }
        require(inferredOverlays.size <= InferredGraphState.entries.size) {
            "graph-page-has-too-many-inferred-overlays"
        }
        require(inferredOverlays.map(InferredOverlaySummary::graphState).distinct().size == inferredOverlays.size) {
            "graph-page-inferred-overlays-must-have-unique-graph-states"
        }
        val nodeIds = nodes.mapTo(mutableSetOf()) { it.id }
        require(edges.all { it.source in nodeIds && it.target in nodeIds }) {
            "graph-page-must-not-contain-orphan-edges"
        }
    }

    public val hasMoreNodes: Boolean
        get() = nextCursor != null && nodes.size < totalNodeCount

    public val hasMoreEdges: Boolean
        get() = nextCursor != null && edges.size < totalEdgeCount
}
