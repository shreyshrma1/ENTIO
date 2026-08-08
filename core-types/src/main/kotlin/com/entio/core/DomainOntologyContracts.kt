package com.entio.core

import java.nio.file.Path

/** Fixed Phase 13 project-profile identity and project-relative locations. */
public object DomainOntologyProfileIdentity {
    public const val SCHEMA: String = "entio-domain-profile-v1"
    public const val SOURCE_ID: String = Phase5PackageIdentity.SOURCE_ID
    public const val RELEASE: String = Phase5PackageIdentity.RELEASE
    public const val PACKAGE_FINGERPRINT: String =
        "015142b94819291379b89c3bba92048f037f1d8e635d3f1342d29f0f02f374ad"
    public const val MANAGED_SOURCE_ID: String = "fibo-reuse"
    public const val PROFILE_PATH: String = ".entio/domain-profile.yaml"
    public const val MANAGED_SOURCE_PATH: String = "ontology/fibo-reuse.ttl"
    public const val PROVENANCE_PATH: String = ".entio/domain-reuse/events-v1.jsonl"
    public const val TRANSACTION_JOURNAL_PATH: String = ".entio/domain-transaction-v1.json"
    public const val TRANSACTION_DIRECTORY_PATH: String = ".entio/domain-transaction-v1"
}

/** The exact optional domain ontology selection stored beside a project. */
public data class DomainOntologyProfile(
    public val schema: String = DomainOntologyProfileIdentity.SCHEMA,
    public val sourceId: String = DomainOntologyProfileIdentity.SOURCE_ID,
    public val release: String = DomainOntologyProfileIdentity.RELEASE,
    public val packageFingerprint: String = DomainOntologyProfileIdentity.PACKAGE_FINGERPRINT,
    public val managedSourceId: String = DomainOntologyProfileIdentity.MANAGED_SOURCE_ID,
)

/** Fixed paths resolved and checked by the semantic engine. */
public data class DomainOntologyProjectPaths(
    public val projectRoot: Path,
    public val profile: Path,
    public val managedSource: Path,
    public val provenance: Path,
    public val transactionJournal: Path,
    public val transactionDirectory: Path,
)

public data class ActiveDomainOntology(
    public val profile: DomainOntologyProfile,
    public val paths: DomainOntologyProjectPaths,
)

public enum class DomainOntologyAvailability {
    Inactive,
    Active,
    Invalid,
    Stale,
    RecoveryRequired,
}

public enum class DomainOntologyMigrationStatus {
    NoExistingReuse,
    ExistingReuseRecognized,
    ExistingReuseAmbiguous,
    ExistingReuseUnsupported,
}

public enum class DomainOntologyIssueType {
    MalformedProfile,
    UnsupportedSchema,
    UnsupportedSource,
    StaleRelease,
    StalePackageFingerprint,
    InvalidManagedSource,
    UnsafeProjectPath,
    TransactionRecoveryRequired,
    DeactivationBlocked,
}

public data class DomainOntologyIssue(
    public val type: DomainOntologyIssueType,
    public val code: String,
    public val message: String,
    public val path: String? = null,
)

public data class DomainOntologyStatus(
    public val availability: DomainOntologyAvailability,
    public val profile: DomainOntologyProfile? = null,
    public val migrationStatus: DomainOntologyMigrationStatus = DomainOntologyMigrationStatus.NoExistingReuse,
    public val issues: List<DomainOntologyIssue> = emptyList(),
)

public data class DomainProfileActivationPreview(
    public val profile: DomainOntologyProfile,
    public val profilePath: String,
    public val managedSourcePath: String,
    public val serializedProfile: String,
    public val serializedEmptyManagedSource: String,
    public val changesProjectOntology: Boolean = false,
)

public enum class DomainProfileDeactivationBlocker {
    ManagedSourceNotEmpty,
    LocalDependencyExists,
    StagedDependencyExists,
    ProposalDependencyExists,
    ActiveProvenanceExists,
    CompatibilityCheckFailed,
}

public data class DomainProfileDeactivationPreview(
    public val active: Boolean,
    public val eligible: Boolean,
    public val blockers: List<DomainProfileDeactivationBlocker>,
    public val profilePath: String = DomainOntologyProfileIdentity.PROFILE_PATH,
    public val removeEmptyManagedSource: Boolean = false,
    public val changesProjectOntology: Boolean = false,
)

public data class DomainProfileDeactivationContext(
    public val managedSourceStatementCount: Int,
    public val hasLocalDependencies: Boolean = false,
    public val hasStagedDependencies: Boolean = false,
    public val hasProposalDependencies: Boolean = false,
    public val hasActiveProvenance: Boolean = false,
    public val compatibilityChecksPassed: Boolean = true,
) {
    init {
        require(managedSourceStatementCount >= 0) { "Managed source statement count must not be negative." }
    }
}

public enum class DomainOperationKind {
    GlobalSemanticSearch,
    CreateClass,
    CreateObjectProperty,
    CreateDatatypeProperty,
    CreateIndividualTypeSelection,
    EditLabelOrDefinition,
    EditClassHierarchy,
    EditPropertyHierarchy,
    EditDomain,
    EditRangeOrDatatype,
    AddAssertionOrValue,
    DeleteOrReplaceEntity,
    ProposalReuseReview,
    ShaclTargetClass,
    ShaclPropertyPath,
    ShaclClassOrDatatypeConstraint,
    OntologyMapRelatedSearch,
    ReasoningWorkspaceRelatedSearch,
    FoundationExpansion,
}

public enum class DomainRetrievalAvailability {
    Full,
    LexicalStructural,
    Unavailable,
}

public enum class DomainRecommendationConfidence {
    Strong,
    Possible,
    Low,
}

public enum class DomainRecommendationAction {
    Browse,
    Reuse,
    Extend,
    MapAnnotation,
}

public enum class DomainRecommendationReasonType {
    PreferredLabelMatch,
    AlternateLabelMatch,
    DefinitionMatch,
    ParaphraseSimilarity,
    CompatibleEntityKind,
    CompatibleDomain,
    CompatibleRange,
    AlreadyReused,
    ConnectedProjectEntity,
    FoundationMember,
    SourceModuleAlreadyUsed,
}

public enum class DomainRecommendationWarningType {
    DeprecatedEntity,
    AdditionalDependencyCost,
    SourceModuleNotUsed,
    SimilarLocalConcept,
    CustomizedFromSource,
    IncompleteOptionalStructure,
    LowConfidence,
    InformativeEntity,
}

public data class DomainModelingIntent(
    public val projectId: String,
    public val operationKind: DomainOperationKind,
    public val requestedKind: ExternalEntityKind? = null,
    public val draftLabel: String,
    public val alternateWording: String? = null,
    public val definition: String? = null,
    public val currentEntityIri: Iri? = null,
    public val requiredParentIri: Iri? = null,
    public val requiredDomainIri: Iri? = null,
    public val requiredRangeIri: Iri? = null,
    public val requiredDatatypeIri: Iri? = null,
    public val nearbyProjectIris: Set<Iri> = emptySet(),
    public val alreadyReusedIris: Set<Iri> = emptySet(),
    public val usedSourceModuleIris: Set<Iri> = emptySet(),
    public val targetSourceId: String? = null,
    public val languagePreference: String? = null,
    public val projectFingerprint: String,
    public val profileFingerprint: String,
    public val ontologyFingerprint: String,
    public val currentWorkFingerprint: String,
    public val packageFingerprint: String,
    public val indexFingerprint: String,
    public val broadSearch: Boolean = false,
) {
    init {
        require(projectId.isNotBlank() && projectId.length <= 256)
        require(draftLabel.isNotBlank() && draftLabel.toByteArray().size <= 4_096)
        require(alternateWording == null || alternateWording.toByteArray().size <= 4_096)
        require(definition == null || definition.toByteArray().size <= 16_384)
        require(nearbyProjectIris.size <= 100 && alreadyReusedIris.size <= 500)
        require(listOf(
            projectFingerprint,
            profileFingerprint,
            ontologyFingerprint,
            currentWorkFingerprint,
            packageFingerprint,
            indexFingerprint,
        ).all(String::isNotBlank))
    }
}

public data class DomainRecommendationReason(
    public val type: DomainRecommendationReasonType,
    public val relatedIri: Iri? = null,
)

public data class DomainRecommendation(
    public val recommendationId: String,
    public val iri: Iri,
    public val preferredLabel: String,
    public val kind: ExternalEntityKind,
    public val sourceFamily: String,
    public val sourceModuleIri: Iri,
    public val maturity: ExternalOntologyMaturity,
    public val confidence: DomainRecommendationConfidence,
    public val permittedActions: Set<DomainRecommendationAction>,
    public val reasons: List<DomainRecommendationReason>,
    public val warnings: List<DomainRecommendationWarningType>,
    public val rankingContract: String = "domain-ranking-v1",
)

public data class DomainRecommendationResult(
    public val availability: DomainRetrievalAvailability,
    public val recommendations: List<DomainRecommendation>,
    public val noConfidentMatch: Boolean,
    public val normalizedIntentFingerprint: String,
)

public data class DomainFoundationMember(
    public val elementId: String,
    public val iri: Iri,
    public val label: String,
    public val kind: ExternalEntityKind,
    public val sourceFamily: String,
)

public data class DomainFoundationGroup(
    public val groupId: String,
    public val label: String,
    public val members: List<DomainFoundationMember>,
)

public enum class DomainFoundationPlanItemRole {
    ExplicitSelection,
    RequiredDependency,
    AlreadyPresent,
}

public data class DomainFoundationPlanItem(
    public val iri: Iri,
    public val label: String,
    public val kind: ExternalEntityKind,
    public val role: DomainFoundationPlanItemRole,
)

public data class DomainFoundationPlanBatch(
    public val batchNumber: Int,
    public val explicitSelectionCount: Int,
    public val items: List<DomainFoundationPlanItem>,
)

public data class DomainFoundationPlan(
    public val planId: String,
    public val projectId: String,
    public val batches: List<DomainFoundationPlanBatch>,
    public val explicitSelectionCount: Int,
    public val dependencyCount: Int,
    public val packageFingerprint: String,
    public val indexFingerprint: String,
)

public enum class DomainReuseAction {
    Reuse,
    ReuseAndCustomize,
    ExtendLocally,
    MapClose,
    MapRelated,
    ContinueLocally,
    RemoveReuse,
}

public enum class DomainMaterializationClassification {
    CompleteSupportedMaterialization,
    PartialMaterialization,
    UnsupportedForReuse,
}

public enum class DomainReuseDependencyDisposition {
    ExplicitlySelected,
    RequiredStructuralDependency,
    AlreadyPresentUnchanged,
    AlreadyPresentCustomized,
    Conflicting,
    Unsupported,
    Missing,
}

public enum class DomainCustomizationClassification {
    Unchanged,
    AnnotationOnly,
    LogicalStructureChanged,
    SourceEntityDeprecated,
    SourceSnapshotUnavailable,
}

public data class DomainReuseCustomization(
    public val preferredLabel: String? = null,
    public val definition: String? = null,
    public val alternateLabels: List<String>? = null,
    public val parentIris: List<Iri>? = null,
    public val domainIris: List<Iri>? = null,
    public val rangeIris: List<Iri>? = null,
) {
    init {
        require(preferredLabel == null || preferredLabel.toByteArray().size <= 4_096) {
            "A customized preferred label must not exceed 4 KiB."
        }
        require(definition == null || definition.toByteArray().size <= 16_384) {
            "A customized definition must not exceed 16 KiB."
        }
        require(alternateLabels == null || alternateLabels.size <= 20) {
            "Domain reuse supports at most 20 alternate labels."
        }
        require(alternateLabels == null || alternateLabels.all { it.toByteArray().size <= 4_096 }) {
            "A customized alternate label must not exceed 4 KiB."
        }
        require(
            listOf(parentIris, domainIris, rangeIris).all { values -> values == null || values.size <= 20 },
        ) {
            "Domain reuse customization lists support at most 20 values."
        }
    }
}

public data class DomainReuseSourceSnapshot(
    public val canonicalIri: Iri,
    public val kind: ExternalEntityKind,
    public val sourceFamily: String,
    public val sourceOntologyIri: Iri,
    public val sourcePath: String,
    public val recordFingerprint: String,
    public val statementFingerprint: String,
    public val statements: List<GraphTriple>,
    public val omittedSourceAxioms: List<String> = emptyList(),
    public val classification: DomainMaterializationClassification,
) {
    init {
        require(statements.size <= 2_000) { "A domain source snapshot supports at most 2,000 statements." }
        require(omittedSourceAxioms.size <= 100) { "A domain source snapshot supports at most 100 omitted-axiom notices." }
    }
}

public data class DomainReuseDependency(
    public val iri: Iri,
    public val label: String,
    public val kind: ExternalEntityKind,
    public val disposition: DomainReuseDependencyDisposition,
)

public data class DomainReusePreparedEntry(
    public val targetSourceId: String,
    public val changeSet: ChangeSet,
)

public data class DomainReusePreparedBatch(
    public val action: DomainReuseAction,
    public val canonicalIri: Iri,
    public val entries: List<DomainReusePreparedEntry>,
    public val dependencies: List<DomainReuseDependency>,
    public val sourceSnapshot: DomainReuseSourceSnapshot,
    public val explicitSelectionCount: Int,
    public val generatedStatementCount: Int,
    public val preparedPayloadBytes: Int,
    public val partialMaterializationAcknowledged: Boolean,
) {
    init {
        require(explicitSelectionCount in 1..20) { "Domain reuse supports 1 to 20 explicit selections per batch." }
        require(dependencies.size <= 100) { "Domain reuse dependency closure supports at most 100 entities." }
        require(generatedStatementCount in 1..2_000) { "Domain reuse supports 1 to 2,000 generated statements." }
        require(preparedPayloadBytes in 1..2_097_152) { "Domain reuse preview data must not exceed 2 MiB." }
        require(entries.isNotEmpty()) { "A prepared domain reuse batch requires graph changes." }
    }
}

public data class DomainReuseDraftProvenance(
    public val action: DomainReuseAction,
    public val canonicalIri: Iri,
    public val sourceSnapshot: DomainReuseSourceSnapshot,
    public val dependencySetFingerprint: String,
    public val targetManagedSourceId: String,
    public val priorRecordId: String? = null,
)

public data class DomainReuseDifference(
    public val entityId: String,
    public val canonicalIri: Iri,
    public val sourceSnapshot: DomainReuseSourceSnapshot,
    public val projectStatements: List<GraphTriple>,
    public val addedProjectStatements: List<GraphTriple>,
    public val removedSourceStatements: List<GraphTriple>,
    public val classification: DomainCustomizationClassification,
)

public enum class DomainReuseEventKind {
    Reused,
    Customized,
    Extended,
    Mapped,
    Removed,
}

public data class DomainReuseProvenanceEvent(
    public val schema: String = "entio-domain-reuse-provenance-v1",
    public val recordId: String,
    public val eventKind: DomainReuseEventKind,
    public val sourceId: String,
    public val release: String,
    public val packageFingerprint: String,
    public val recordFingerprint: String,
    public val canonicalIri: Iri,
    public val entityKind: ExternalEntityKind,
    public val sourceOntologyIri: Iri,
    public val sourcePath: String,
    public val sourceStatementFingerprint: String,
    public val sourceSnapshot: List<GraphTriple>,
    public val omittedSourceAxioms: List<String>,
    public val dependencySetFingerprint: String,
    public val targetManagedSourceId: String,
    public val proposalId: String,
    public val appliedChangeSetId: String,
    public val actorId: String,
    public val appliedAt: String,
    public val baselineProjectFingerprint: String,
    public val resultingProjectFingerprint: String,
    public val projectStatementFingerprint: String,
    public val customization: DomainCustomizationClassification,
    public val priorRecordId: String? = null,
    public val checksum: String,
) {
    init {
        require(schema == "entio-domain-reuse-provenance-v1")
        require(sourceSnapshot.size <= 2_000)
        require(omittedSourceAxioms.size <= 100)
        require(Regex("[a-f0-9]{64}").matches(checksum))
    }
}
