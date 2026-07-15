package com.entio.core

public enum class ReasoningRunStatus {
    Running,
    Completed,
    Failed,
    Cancelled,
    TimedOut,
    Incomplete,
    Unavailable,
}

public enum class ConsistencyStatus {
    Consistent,
    Inconsistent,
    Unknown,
}

public enum class FactOrigin {
    Asserted,
    Inferred,
}

public data class ReasoningFingerprints(
    public val graphFingerprint: String,
    public val importClosureFingerprint: String,
    public val reasonerConfigurationFingerprint: String,
)

public data class ReasoningRunMetadata(
    public val status: ReasoningRunStatus,
    public val reasonerName: String,
    public val reasonerVersion: String,
    public val owlApiVersion: String,
    public val fingerprints: ReasoningFingerprints,
    public val importClosureComplete: Boolean,
    public val reused: Boolean = false,
)

public data class ReasoningClassRelationship(
    public val subject: RdfResource,
    public val objectClass: RdfResource,
    public val origin: FactOrigin,
    public val sourceId: String? = null,
)

public data class ReasoningIndividualType(
    public val individual: RdfResource,
    public val type: RdfResource,
    public val origin: FactOrigin,
    public val sourceId: String? = null,
)

public data class ReasoningPropertyRelationship(
    public val subject: RdfResource,
    public val predicate: Iri,
    public val objectResource: RdfResource,
    public val origin: FactOrigin,
    public val sourceId: String? = null,
)

public data class ReasoningResult(
    public val metadata: ReasoningRunMetadata,
    public val consistency: ConsistencyStatus,
    public val classRelationships: List<ReasoningClassRelationship> = emptyList(),
    public val individualTypes: List<ReasoningIndividualType> = emptyList(),
    public val propertyRelationships: List<ReasoningPropertyRelationship> = emptyList(),
    public val unsatisfiableClasses: List<RdfResource> = emptyList(),
    public val unsupportedFeatures: List<OwlFeatureFinding> = emptyList(),
    public val warnings: List<String> = emptyList(),
    public val errors: List<String> = emptyList(),
)

public enum class ImportFindingKind {
    Missing,
    Unresolved,
    Cycle,
    Unsupported,
}

public data class ImportFinding(
    public val importedIri: Iri,
    public val kind: ImportFindingKind,
    public val message: String,
    public val sourceId: String? = null,
    public val relatedSourceId: String? = null,
)

public data class ImportClosureReport(
    public val sourceIds: List<String>,
    public val findings: List<ImportFinding> = emptyList(),
    public val complete: Boolean,
)

public enum class OwlFeatureSupport {
    Supported,
    Partial,
    Unsupported,
    Ignored,
}

public data class OwlFeatureFinding(
    public val feature: String,
    public val support: OwlFeatureSupport,
    public val affectsCompleteness: Boolean,
    public val message: String? = null,
)

public data class OwlFeatureReport(
    public val profile: String? = null,
    public val findings: List<OwlFeatureFinding> = emptyList(),
)

public enum class ReasoningExplanationKind {
    Inference,
    Inconsistency,
    UnsatisfiableClass,
}

public data class ReasoningExplanation(
    public val target: RdfResource,
    public val kind: ReasoningExplanationKind,
    public val rule: String,
    public val assertedEvidence: List<GraphTriple> = emptyList(),
    public val complete: Boolean,
    public val caveat: String? = null,
)

public enum class ShaclGraphRole {
    Ontology,
    Data,
    Shapes,
}

public data class ShaclSourceRole(
    public val sourceId: String,
    public val roles: Set<ShaclGraphRole>,
)

public data class ShaclGraphIdentity(
    public val dataSourceIds: List<String>,
    public val shapesSourceIds: List<String>,
    public val dataGraphFingerprint: String,
    public val shapesGraphFingerprint: String,
)

public data class ShaclShapeId(
    public val iri: Iri,
    public val sourceId: String,
)

public sealed interface ShaclTarget {
    public data class TargetClass(
        public val classIri: Iri,
    ) : ShaclTarget

    public data class TargetNode(
        public val node: RdfResource,
    ) : ShaclTarget

    public data class TargetSubjectsOf(
        public val propertyIri: Iri,
    ) : ShaclTarget

    public data class TargetObjectsOf(
        public val propertyIri: Iri,
    ) : ShaclTarget
}

public sealed interface ShaclPath {
    public data class DirectProperty(
        public val propertyIri: Iri,
    ) : ShaclPath
}

public enum class ShaclConstraintKind {
    MinCount,
    MaxCount,
    ExactCount,
    Datatype,
    Class,
    In,
    HasValue,
    MinInclusive,
    MaxInclusive,
    MinExclusive,
    MaxExclusive,
    Pattern,
    MinLength,
    MaxLength,
    Closed,
}

public sealed interface ShaclConstraintValue {
    public data class IntegerValue(
        public val value: Int,
    ) : ShaclConstraintValue

    public data class TermValue(
        public val value: RdfTerm,
    ) : ShaclConstraintValue

    public data class TermListValue(
        public val values: List<RdfTerm>,
    ) : ShaclConstraintValue

    public data class TextValue(
        public val value: String,
    ) : ShaclConstraintValue

    public data class BooleanValue(
        public val value: Boolean,
    ) : ShaclConstraintValue
}

public data class ShaclConstraint(
    public val kind: ShaclConstraintKind,
    public val value: ShaclConstraintValue? = null,
)

public enum class ShaclSeverity {
    Violation,
    Warning,
    Info,
}

public data class ShaclPropertyShape(
    public val id: ShaclShapeId,
    public val path: ShaclPath,
    public val constraints: List<ShaclConstraint> = emptyList(),
    public val severity: ShaclSeverity = ShaclSeverity.Violation,
    public val message: String? = null,
)

public data class ShaclNodeShape(
    public val id: ShaclShapeId,
    public val targets: List<ShaclTarget> = emptyList(),
    public val propertyShapes: List<ShaclPropertyShape> = emptyList(),
    public val constraints: List<ShaclConstraint> = emptyList(),
    public val closed: Boolean = false,
    public val ignoredProperties: List<Iri> = emptyList(),
    public val severity: ShaclSeverity = ShaclSeverity.Violation,
    public val message: String? = null,
)

public enum class ShaclValidationMode {
    AssertedOnly,
    AssertedAndInferred,
}

public enum class ShaclValidationStatus {
    Running,
    Completed,
    Failed,
    Cancelled,
    TimedOut,
    Unavailable,
}

public data class ShaclValidationResult(
    public val severity: ShaclSeverity,
    public val message: String,
    public val focusNode: RdfResource,
    public val path: ShaclPath? = null,
    public val shape: ShaclShapeId,
    public val constraint: ShaclConstraintKind,
    public val value: RdfTerm? = null,
    public val sourceId: String? = null,
)

public data class ShaclValidationReport(
    public val status: ShaclValidationStatus,
    public val mode: ShaclValidationMode,
    public val graphIdentity: ShaclGraphIdentity,
    public val results: List<ShaclValidationResult> = emptyList(),
    public val warnings: List<String> = emptyList(),
    public val errors: List<String> = emptyList(),
)

public enum class BaselineImpactStatus {
    Safe,
    BlocksApproval,
    Incomplete,
    Failed,
}

public data class ReasoningImpactSummary(
    public val addedInferences: List<ReasoningPropertyRelationship> = emptyList(),
    public val removedInferences: List<ReasoningPropertyRelationship> = emptyList(),
    public val consistencyChanged: Boolean = false,
    public val unsatisfiableClassesAdded: List<RdfResource> = emptyList(),
    public val unsatisfiableClassesResolved: List<RdfResource> = emptyList(),
)

public data class ShaclImpactSummary(
    public val newResults: List<ShaclValidationResult> = emptyList(),
    public val worsenedResults: List<ShaclValidationResult> = emptyList(),
    public val unchangedResults: List<ShaclValidationResult> = emptyList(),
    public val resolvedResults: List<ShaclValidationResult> = emptyList(),
)

public data class ProposalImpactReport(
    public val explicitDiff: SemanticDiff,
    public val reasoningImpact: ReasoningImpactSummary,
    public val shaclImpact: ShaclImpactSummary,
    public val status: BaselineImpactStatus,
    public val blockingMessages: List<String> = emptyList(),
)

public enum class MultiSourceApplyStatus {
    Applied,
    Failed,
    RolledBack,
    RollbackFailed,
}

public data class MultiSourceApplyResult(
    public val status: MultiSourceApplyStatus,
    public val changedFiles: List<String> = emptyList(),
    public val reason: String? = null,
    public val restoredFiles: List<String> = emptyList(),
)
