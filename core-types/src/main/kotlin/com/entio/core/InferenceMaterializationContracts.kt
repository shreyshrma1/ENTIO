package com.entio.core

import java.time.Instant

public const val MAX_INFERENCE_MATERIALIZATION_BATCH_SIZE: Int = 100
public const val MAX_INFERENCE_IMPORT_REFERENCES: Int = 20

public enum class InferenceMaterializationKind {
    SubclassRelationship,
    IndividualType,
    ObjectPropertyAssertion,
}

public enum class InferenceStageability {
    Stageable,
    AlreadyAsserted,
    AlreadyStaged,
    Stale,
    UnsupportedType,
    UnsupportedTerm,
    MissingEntity,
    InvalidPredicate,
    NoWritableSource,
    AmbiguousSource,
    ImportDependencyUnsafe,
}

public enum class InferenceImportDependenceState {
    LocalOnly,
    Imported,
    Unknown,
}

/** Canonical, label-independent semantic identity for one supported inferred fact. */
public data class InferenceMaterializationFact(
    public val kind: InferenceMaterializationKind,
    public val subject: Iri,
    public val predicate: Iri,
    public val objectValue: Iri,
) {
    init {
        require(subject.value.isNotBlank()) { "Inference subject IRI must not be blank." }
        require(predicate.value.isNotBlank()) { "Inference predicate IRI must not be blank." }
        require(objectValue.value.isNotBlank()) { "Inference object IRI must not be blank." }
    }
}

/** Server-only identity used to match a retained fact to a fresh reasoning result. */
@JvmInline
public value class SemanticFactKey(public val value: String) {
    init {
        require(value.startsWith("entio-semantic-fact-v1:")) { "Semantic fact key must use the approved v1 prefix." }
        require(value.substringAfter(':').matches(Regex("[0-9a-f]{64}"))) { "Semantic fact key must contain a lowercase SHA-256 digest." }
    }
}

/** Opaque browser identity bound to one retained reasoning job and its owner. */
@JvmInline
public value class InferenceFactId(public val value: String) {
    init {
        require(value.startsWith("entio-reasoning-fact-v1:")) { "Inference fact ID must use the approved v1 prefix." }
        require(value.substringAfter(':').matches(Regex("[0-9a-f]{64}"))) { "Inference fact ID must contain a lowercase SHA-256 digest." }
    }
}

public data class InferenceSourceCandidate(
    public val sourceId: String,
    public val selected: Boolean = false,
) {
    init {
        require(sourceId.isNotBlank()) { "Inference source candidate ID must not be blank." }
    }
}

public data class InferenceImportDependence(
    public val state: InferenceImportDependenceState,
    public val sourceIds: List<String> = emptyList(),
) {
    init {
        require(sourceIds.size <= MAX_INFERENCE_IMPORT_REFERENCES) { "Inference import references exceed the approved bound." }
        require(sourceIds.all(String::isNotBlank)) { "Inference import references must not be blank." }
        require(sourceIds == sourceIds.distinct().sorted()) { "Inference import references must be sorted and unique." }
        require(state == InferenceImportDependenceState.Imported || sourceIds.isEmpty()) {
            "Only imported inference dependence may carry source references."
        }
        require(state != InferenceImportDependenceState.Imported || sourceIds.isNotEmpty()) {
            "Imported inference dependence requires at least one source reference."
        }
    }
}

public data class InferenceMaterializationCandidate(
    public val factId: InferenceFactId,
    public val semanticFactKey: SemanticFactKey,
    public val fact: InferenceMaterializationFact,
    public val stageability: InferenceStageability,
    public val sourceCandidates: List<InferenceSourceCandidate> = emptyList(),
    public val selectedSourceId: String? = null,
    public val existingStagedChangeId: String? = null,
    public val importDependence: InferenceImportDependence =
        InferenceImportDependence(InferenceImportDependenceState.LocalOnly),
) {
    init {
        require(sourceCandidates.map(InferenceSourceCandidate::sourceId).distinct().size == sourceCandidates.size) {
            "Inference source candidates must be unique."
        }
        require(sourceCandidates.map(InferenceSourceCandidate::sourceId).sorted() == sourceCandidates.map(InferenceSourceCandidate::sourceId)) {
            "Inference source candidates must use stable source-ID order."
        }
        require(selectedSourceId == null || sourceCandidates.any { it.sourceId == selectedSourceId }) {
            "Selected inference source must be one of the source candidates."
        }
        require(sourceCandidates.count(InferenceSourceCandidate::selected) <= 1) {
            "At most one inference source candidate may be selected."
        }
        require(sourceCandidates.firstOrNull(InferenceSourceCandidate::selected)?.sourceId == selectedSourceId) {
            "Selected source identity must match the selected source candidate."
        }
        require(stageability == InferenceStageability.AlreadyStaged || existingStagedChangeId == null) {
            "Only an already-staged fact may reference an existing staged change."
        }
        require(stageability != InferenceStageability.AlreadyStaged || !existingStagedChangeId.isNullOrBlank()) {
            "An already-staged fact must reference its existing staged change."
        }
    }
}

public enum class InferenceMaterializationOrigin {
    MaterializedFromReasoning,
}

public data class InferenceMaterializationProvenance(
    public val origin: InferenceMaterializationOrigin = InferenceMaterializationOrigin.MaterializedFromReasoning,
    public val inferenceKind: InferenceMaterializationKind,
    public val reasoningJobId: String,
    public val graphFingerprint: String,
    public val factId: InferenceFactId,
    public val semanticFactKey: SemanticFactKey,
    public val fact: InferenceMaterializationFact,
    public val stagedByUserId: String,
    public val stagedAt: Instant,
    public val targetSourceId: String,
    public val entailedBeforeAssertion: Boolean = true,
    public val importDependence: InferenceImportDependence =
        InferenceImportDependence(InferenceImportDependenceState.LocalOnly),
) {
    init {
        require(inferenceKind == fact.kind) { "Inference provenance kind must match its fact." }
        require(reasoningJobId.isNotBlank()) { "Reasoning job ID must not be blank." }
        require(graphFingerprint.isNotBlank()) { "Reasoning graph fingerprint must not be blank." }
        require(stagedByUserId.isNotBlank()) { "Staging user ID must not be blank." }
        require(targetSourceId.isNotBlank()) { "Target source ID must not be blank." }
        require(entailedBeforeAssertion) { "A reasoning materialization must have been entailed before assertion." }
    }
}

public data class InferenceMaterializationSelection(
    public val factId: InferenceFactId,
    public val selectedSourceId: String? = null,
) {
    init {
        require(selectedSourceId == null || selectedSourceId.isNotBlank()) { "Selected source ID must not be blank." }
    }
}

public data class InferenceMaterializationBatch(
    public val selections: List<InferenceMaterializationSelection>,
) {
    init {
        require(selections.isNotEmpty()) { "An inference materialization batch must not be empty." }
        require(selections.size <= MAX_INFERENCE_MATERIALIZATION_BATCH_SIZE) {
            "An inference materialization batch exceeds the approved bound."
        }
        require(selections.map(InferenceMaterializationSelection::factId).distinct().size == selections.size) {
            "An inference materialization batch must not contain duplicate fact IDs."
        }
    }
}

public data class PreparedInferenceMaterialization(
    public val factId: InferenceFactId,
    public val semanticFactKey: SemanticFactKey,
    public val fact: InferenceMaterializationFact,
    public val targetSourceId: String,
    public val edit: TypedOntologyEdit,
    public val triple: GraphTriple,
    public val provenance: InferenceMaterializationProvenance,
) {
    init {
        require(targetSourceId.isNotBlank()) { "Prepared target source ID must not be blank." }
        require(targetSourceId == provenance.targetSourceId) { "Prepared and provenance target sources must match." }
        require(factId == provenance.factId) { "Prepared and provenance fact IDs must match." }
        require(semanticFactKey == provenance.semanticFactKey) { "Prepared and provenance semantic keys must match." }
        require(fact == provenance.fact) { "Prepared and provenance facts must match." }
    }
}

public data class PreparedInferenceMaterializationBatch(
    public val items: List<PreparedInferenceMaterialization>,
) {
    init {
        require(items.isNotEmpty()) { "A prepared inference materialization batch must not be empty." }
        require(items.size <= MAX_INFERENCE_MATERIALIZATION_BATCH_SIZE) {
            "A prepared inference materialization batch exceeds the approved bound."
        }
        require(items.map(PreparedInferenceMaterialization::factId).distinct().size == items.size) {
            "A prepared inference materialization batch must not contain duplicate fact IDs."
        }
        require(items.map(PreparedInferenceMaterialization::semanticFactKey).distinct().size == items.size) {
            "A prepared inference materialization batch must not contain duplicate semantic facts."
        }
        require(items.map(PreparedInferenceMaterialization::triple).distinct().size == items.size) {
            "A prepared inference materialization batch must not contain duplicate asserted triples."
        }
    }
}

public data class InferenceMaterializationMapping(
    public val factId: InferenceFactId,
    public val stagedChangeId: String,
) {
    init {
        require(stagedChangeId.isNotBlank()) { "Staged change ID must not be blank." }
    }
}

public data class InferenceMaterializationBatchResult(
    public val mappings: List<InferenceMaterializationMapping>,
) {
    init {
        require(mappings.isNotEmpty()) { "An inference materialization result must not be empty." }
        require(mappings.size <= MAX_INFERENCE_MATERIALIZATION_BATCH_SIZE) {
            "An inference materialization result exceeds the approved bound."
        }
        require(mappings.map(InferenceMaterializationMapping::factId).distinct().size == mappings.size) {
            "An inference materialization result must not contain duplicate fact IDs."
        }
    }
}
