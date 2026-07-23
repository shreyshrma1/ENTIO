package com.entio.core

public const val MAX_INFERRED_READ_FACTS: Int = 100

/** The graph state from which a read-only inference was derived. */
public enum class InferredGraphState {
    Applied,
    Proposal,
}

/** Availability of one project-owned inferred read overlay. */
public enum class InferredReadState {
    Off,
    Current,
    Updating,
    Unavailable,
    Failed,
}

/** Inference families that Phase 10.5 may expose through read contracts. */
public enum class InferredReadKind {
    SubclassRelationship,
    IndividualType,
    ObjectPropertyAssertion,
    EffectiveDomain,
    EffectiveRange,
}

/** Existing Explore locations into which Kotlin may project an inferred fact. */
public enum class InferredFactPlacement {
    ClassSuperclasses,
    ClassSubclasses,
    ClassTypedIndividuals,
    ClassRelatedProperties,
    HierarchyChildren,
    OutlineDirectType,
    PropertyDomains,
    PropertyRanges,
    PropertyUsage,
    IndividualTypes,
    IndividualOutgoingRelationships,
    IndividualIncomingRelationships,
}

/**
 * A named, provenance-aware inferred relationship for read-only presentation.
 *
 * Placement is supplied by Kotlin so presentation clients do not interpret RDF
 * predicates or invent ontology meaning.
 */
public data class InferredReadFact(
    public val semanticFactKey: SemanticFactKey,
    public val subject: Iri,
    public val predicate: Iri,
    public val objectValue: Iri,
    public val kind: InferredReadKind,
    public val placements: Set<InferredFactPlacement>,
    public val origin: FactOrigin = FactOrigin.Inferred,
    public val graphState: InferredGraphState,
    public val reasoningResultId: String,
    public val graphFingerprint: String,
    public val proposalFingerprint: String? = null,
    public val sourceId: String? = null,
) {
    init {
        require(placements.isNotEmpty()) { "An inferred read fact requires at least one placement." }
        require(origin == FactOrigin.Inferred) { "An inferred read fact must have inferred origin." }
        require(reasoningResultId.isNotBlank()) { "An inferred read fact requires a reasoning result ID." }
        require(graphFingerprint.isNotBlank()) { "An inferred read fact requires a graph fingerprint." }
        require(sourceId == null || sourceId.isNotBlank()) { "An inferred read source ID must not be blank." }
        require(graphState == InferredGraphState.Proposal || proposalFingerprint == null) {
            "Only proposal inferred facts may carry a proposal fingerprint."
        }
        require(graphState != InferredGraphState.Proposal || !proposalFingerprint.isNullOrBlank()) {
            "A proposal inferred fact requires a proposal fingerprint."
        }
    }
}

/** Bounded facts and freshness metadata for one project graph state. */
public data class InferredFactsOverlay(
    public val graphState: InferredGraphState,
    public val state: InferredReadState,
    public val facts: List<InferredReadFact> = emptyList(),
    public val totalFactCount: Int = facts.size,
    public val truncated: Boolean = false,
    public val graphFingerprint: String? = null,
    public val proposalFingerprint: String? = null,
    public val message: String? = null,
) {
    init {
        require(facts.size <= MAX_INFERRED_READ_FACTS) { "An inferred read overlay exceeds the approved bound." }
        require(facts.all { it.graphState == graphState }) { "Overlay facts must share the overlay graph state." }
        require(facts.map(InferredReadFact::semanticFactKey).distinct().size == facts.size) {
            "An inferred read overlay must not contain duplicate semantic facts."
        }
        require(totalFactCount >= facts.size) { "Total inferred fact count must include every returned fact." }
        require(truncated == (totalFactCount > facts.size)) {
            "Inferred overlay truncation must match its returned and total fact counts."
        }
        require(state == InferredReadState.Current || facts.isEmpty()) {
            "Only a current inferred overlay may contain facts."
        }
        require(state != InferredReadState.Current || !graphFingerprint.isNullOrBlank()) {
            "A current inferred overlay requires a graph fingerprint."
        }
        require(graphState == InferredGraphState.Proposal || proposalFingerprint == null) {
            "Only a proposal overlay may carry a proposal fingerprint."
        }
        require(
            graphState != InferredGraphState.Proposal ||
                state != InferredReadState.Current ||
                !proposalFingerprint.isNullOrBlank(),
        ) { "A current proposal overlay requires a proposal fingerprint." }
        require(message == null || message.isNotBlank()) { "An inferred overlay message must not be blank." }
    }
}

/** Aggregate graph-response metadata without duplicating inferred facts or edges. */
public data class InferredOverlaySummary(
    public val graphState: InferredGraphState,
    public val state: InferredReadState,
    public val totalFactCount: Int = 0,
    public val truncated: Boolean = false,
    public val graphFingerprint: String? = null,
    public val proposalFingerprint: String? = null,
    public val message: String? = null,
) {
    init {
        require(totalFactCount >= 0) { "Inferred graph fact count must not be negative." }
        require(state != InferredReadState.Current || !graphFingerprint.isNullOrBlank()) {
            "A current inferred graph summary requires a graph fingerprint."
        }
        require(graphState == InferredGraphState.Proposal || proposalFingerprint == null) {
            "Only a proposal graph summary may carry a proposal fingerprint."
        }
        require(
            graphState != InferredGraphState.Proposal ||
                state != InferredReadState.Current ||
                !proposalFingerprint.isNullOrBlank(),
        ) { "A current proposal graph summary requires a proposal fingerprint." }
        require(message == null || message.isNotBlank()) { "An inferred graph message must not be blank." }
    }
}
