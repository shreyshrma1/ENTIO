package com.entio.core

/** Project-level namespace metadata used by later deterministic identifier services. */
public data class IriNamespaceConfig(
    public val namespace: Iri,
    public val normalizationVersion: String = "phase-2.5-plus-v1",
)

/** Label-first or explicit-IRI selection input for an existing entity. */
public data class EntitySelector(
    public val label: String? = null,
    public val iri: Iri? = null,
    public val kind: SymbolKind? = null,
    public val sourceId: String? = null,
)

/** A deterministic candidate returned for an entity selection. */
public data class EntityCandidate(
    public val iri: Iri,
    public val label: String?,
    public val kind: SymbolKind,
    public val sourceId: String,
)

/** Outcome of resolving an entity selector without performing fuzzy matching. */
public sealed interface EntityResolutionResult {
    public data class Resolved(
        public val candidate: EntityCandidate,
    ) : EntityResolutionResult

    public data class Ambiguous(
        public val candidates: List<EntityCandidate>,
    ) : EntityResolutionResult

    public data object NotFound : EntityResolutionResult

    public data class Invalid(
        public val reason: String,
    ) : EntityResolutionResult
}

public enum class IriCollisionOutcome {
    New,
    ExistingEquivalent,
    SuffixRequired,
    Rejected,
}

/** Result metadata for a generated identifier; generation itself belongs to semantic-engine. */
public data class GeneratedIri(
    public val iri: Iri,
    public val localName: String,
    public val collision: IriCollisionOutcome,
    public val normalizationVersion: String,
)

public enum class DeletionDependencyKind {
    DirectDefinition,
    IncomingReference,
    OutgoingReference,
}

/** Stable identity for one explicit RDF statement considered during deletion review. */
public data class DeletionDependencyIdentity(
    public val sourceId: String,
    public val statement: GraphTriple,
) {
    /** Length-prefixed encoding avoids ambiguity when RDF values contain separators. */
    public val key: String
        get() = listOf(
            sourceId,
            statement.subjectResource.identityPart(),
            statement.predicate.value,
            statement.objectTerm.identityPart(),
        ).joinToString(separator = "") { component -> "${component.length}:$component" }

    private fun RdfResource.identityPart(): String = when (this) {
        is Iri -> "iri:$value"
        is BlankNodeResource -> "blank:$id"
    }

    private fun RdfTerm.identityPart(): String = when (this) {
        is RdfResource -> "resource:${identityPart()}"
        is RdfLiteral -> "literal:${lexicalForm}|${datatypeIri?.value.orEmpty()}|${languageTag.orEmpty()}"
    }
}

/** An explicit graph statement considered by a deletion analysis. */
public data class DeletionDependency(
    public val statement: GraphTriple,
    public val kind: DeletionDependencyKind,
    public val sourceId: String,
    public val selectedForRemoval: Boolean = false,
) {
    public val identity: DeletionDependencyIdentity
        get() = DeletionDependencyIdentity(sourceId, statement)

    public val identityKey: String
        get() = identity.key
}

public enum class DeletionPlanStatus {
    Safe,
    RequiresExplicitDependencies,
    Blocked,
    Invalid,
    InvalidDependencySelection,
}

/** Reviewable deletion metadata; graph traversal and plan construction belong to semantic-engine. */
public data class DeletionPlan(
    public val target: EntityCandidate,
    public val directStatements: List<DeletionDependency> = emptyList(),
    public val dependentStatements: List<DeletionDependency> = emptyList(),
    public val status: DeletionPlanStatus,
    public val invalidSelectedDependencyKeys: List<String> = emptyList(),
)

/** The operation represented by one staged entry. */
public sealed interface StagedChangeOperation {
    public data class TypedEdit(
        public val edit: TypedOntologyEdit,
    ) : StagedChangeOperation

    /** Graph changes produced by an already validated external reuse intent. */
    public data class GraphChanges(
        public val changeSet: ChangeSet,
    ) : StagedChangeOperation

    public data class Delete(
        public val plan: DeletionPlan,
    ) : StagedChangeOperation
}

public enum class StagedChangeStatus {
    Previewed,
    Edited,
    Invalid,
    Removed,
}

/** One successfully previewed or reviewable in-memory change entry. */
public data class StagedChange(
    public val id: String,
    public val order: Int,
    public val targetSourceId: String,
    public val summary: String,
    public val operation: StagedChangeOperation,
    public val normalizedValues: Map<String, String> = emptyMap(),
    public val resolvedCandidates: List<EntityCandidate> = emptyList(),
    public val generatedIris: List<GeneratedIri> = emptyList(),
    public val validationReport: ValidationReport? = null,
    public val status: StagedChangeStatus = StagedChangeStatus.Previewed,
)

public enum class StagedChangeSetStatus {
    Empty,
    Ready,
    Conflicted,
    Previewed,
    Approved,
    Applied,
    Rejected,
    Failed,
}

/** Ordered in-memory collection submitted for combined preview and review. */
public data class StagedChangeSet(
    public val entries: List<StagedChange> = emptyList(),
    public val status: StagedChangeSetStatus = if (entries.isEmpty()) {
        StagedChangeSetStatus.Empty
    } else {
        StagedChangeSetStatus.Ready
    },
)

public enum class StagedConflictKind {
    DuplicateTarget,
    CreateDeleteSameEntity,
    DeletedDependency,
    IncompatibleEdits,
    EmptyChange,
    StaleBaseline,
}

/** Conflict metadata that identifies every staged entry involved in the conflict. */
public data class StagedConflict(
    public val kind: StagedConflictKind,
    public val stagedChangeIds: List<String>,
    public val message: String,
)

public data class StagedValidationAttribution(
    public val stagedChangeId: String,
    public val issueCodes: List<String>,
)

public enum class CombinedProposalStatus {
    Prepared,
    Conflicted,
    Invalid,
    ReadyForReview,
    Approved,
    Rejected,
    Applied,
    Failed,
    Stale,
}

/** Metadata shared by combined preview, validation, diff, and application results. */
public data class CombinedProposalMetadata(
    public val proposalId: String,
    public val stagedChangeIds: List<String>,
    public val targetSourceIds: List<String>,
    public val status: CombinedProposalStatus,
    public val conflicts: List<StagedConflict> = emptyList(),
    public val validationAttribution: List<StagedValidationAttribution> = emptyList(),
    public val baseline: ProposalBaseline? = null,
)

/** Result container for one combined in-memory preview and its review metadata. */
public data class CombinedProposalPreview(
    public val metadata: CombinedProposalMetadata,
    public val changeSet: ChangeSet? = null,
    public val preview: ChangePreview? = null,
    public val diff: SemanticDiff? = null,
    public val validationReport: ValidationReport,
    public val equivalence: SemanticEquivalenceResult? = null,
)

/** Deterministically ordered staged entries and their translated graph changes. */
public data class NormalizedStagedChangeSet(
    public val entries: List<StagedChange>,
    public val changeSet: ChangeSet? = null,
    public val conflicts: List<StagedConflict> = emptyList(),
)
