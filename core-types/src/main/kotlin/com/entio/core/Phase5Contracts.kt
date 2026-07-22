package com.entio.core

/** Fixed identity and curation decisions for the first external ontology package. */
public object Phase5PackageIdentity {
    public const val SOURCE_ID: String = "fibo"
    public const val RELEASE: String = "master_2026Q2"
    public const val COMMIT_SHA: String = "f59157fe156e3d91b1c045222d0a7dc06b7d78a2"
    public const val PACKAGE_SCHEMA: String = "entio-fibo-package-v1"
    public const val CATALOG_SCHEMA: String = "fibo-catalog-v1"
    public const val CHECKSUM_ALGORITHM: String = "SHA-256"
    public const val COMMONS_VERSION: String = "1.3"

    public val CURATED_SEEDS: List<Iri> = listOf(
        "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/",
        "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Contracts/",
        "https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/Documents/",
        "https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/IdentifiersAndIndices/",
        "https://spec.edmcouncil.org/fibo/ontology/FND/Parties/Parties/",
        "https://spec.edmcouncil.org/fibo/ontology/FND/AgentsAndPeople/People/",
        "https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/",
        "https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/BusinessDates/",
        "https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/Occurrences/",
        "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/CurrencyAmount/",
        "https://spec.edmcouncil.org/fibo/ontology/FND/OwnershipAndControl/Ownership/",
        "https://spec.edmcouncil.org/fibo/ontology/FND/OwnershipAndControl/Control/",
        "https://spec.edmcouncil.org/fibo/ontology/FND/ProductsAndServices/ProductsAndServices/",
        "https://spec.edmcouncil.org/fibo/ontology/FND/ProductsAndServices/PaymentsAndSchedules/",
        "https://spec.edmcouncil.org/fibo/ontology/FND/Places/RealProperty/",
    ).map(::Iri)
}

public enum class ExternalOntologyAvailability {
    Available,
    Unavailable,
    Invalid,
}

public enum class ExternalOntologyMaturity {
    Release,
    Provisional,
    Informative,
    Deprecated,
    Unknown,
}

public enum class ExternalElementLocality {
    External,
    Local,
}

public enum class ExternalElementCatalogStatus {
    Available,
    Curated,
    Selected,
    AlreadyUsed,
}

public enum class ExternalEntityKind {
    Class,
    ObjectProperty,
    DatatypeProperty,
}

public data class ExternalPackageFingerprint(
    public val value: String,
    public val algorithm: String = Phase5PackageIdentity.CHECKSUM_ALGORITHM,
)

/** The project-owned reference to approved external ontology modules. */
public data class ExternalOntologyReference(
    public val source: String,
    public val release: String,
    public val commitSha: String,
    public val packageFingerprint: ExternalPackageFingerprint,
    public val modules: List<Iri>,
) {
    init {
        require(source.isNotBlank()) { "External ontology source must not be blank." }
        require(release.isNotBlank()) { "External ontology release must not be blank." }
        require(commitSha.isNotBlank()) { "External ontology commit SHA must not be blank." }
        require(modules.distinct() == modules) { "External ontology modules must be unique." }
    }
}

/** A machine-readable description of one approved external ontology source. */
public data class ExternalOntologySource(
    public val id: String,
    public val displayName: String,
    public val version: String,
    public val description: String,
    public val availability: ExternalOntologyAvailability,
    public val curatedPackageId: String,
    public val catalogId: String,
    public val attribution: String,
)

/** Manifest identity and metadata for the immutable FIBO package. */
public data class ExternalOntologyManifest(
    public val sourceId: String,
    public val release: String,
    public val commitSha: String,
    public val packageSchema: String,
    public val catalogSchema: String,
    public val checksumAlgorithm: String,
    public val commonsVersion: String,
    public val packageFingerprint: ExternalPackageFingerprint,
    public val curatedSeedOntologyIris: List<Iri>,
    public val importClosureOntologyIris: List<Iri> = emptyList(),
    public val ontologyIriMappings: Map<Iri, String> = emptyMap(),
    public val assetChecksums: Map<String, String> = emptyMap(),
    public val assetLicenses: Map<String, String> = emptyMap(),
    public val attributionComplete: Boolean = false,
) {
    init {
        require(sourceId == Phase5PackageIdentity.SOURCE_ID) { "Unsupported external ontology source." }
        require(release == Phase5PackageIdentity.RELEASE) { "Unsupported FIBO release." }
        require(commitSha == Phase5PackageIdentity.COMMIT_SHA) { "FIBO commit SHA does not match the approved package." }
        require(packageSchema == Phase5PackageIdentity.PACKAGE_SCHEMA) { "Unsupported package schema." }
        require(catalogSchema == Phase5PackageIdentity.CATALOG_SCHEMA) { "Unsupported catalog schema." }
        require(checksumAlgorithm == Phase5PackageIdentity.CHECKSUM_ALGORITHM) { "Unsupported checksum algorithm." }
        require(commonsVersion == Phase5PackageIdentity.COMMONS_VERSION) { "Unsupported OMG Commons version." }
        require(curatedSeedOntologyIris == Phase5PackageIdentity.CURATED_SEEDS) {
            "Curated FIBO seed list does not match the approved package."
        }
        require(packageFingerprint.algorithm == Phase5PackageIdentity.CHECKSUM_ALGORITHM) {
            "Package fingerprint must use SHA-256."
        }
    }
}

public data class ExternalOntologyModule(
    public val ontologyIri: Iri,
    public val label: String,
    public val domain: String,
    public val sourcePath: String,
    public val maturity: ExternalOntologyMaturity,
    public val curated: Boolean,
    public val importedOntologyIris: List<Iri> = emptyList(),
)

public data class ExternalOntologyCatalog(
    public val sourceId: String,
    public val release: String,
    public val catalogSchema: String,
    public val modules: List<ExternalOntologyModule> = emptyList(),
    public val elementCount: Int = 0,
)

/** Phase 3 semantic description enriched with immutable external-source metadata. */
public data class ExternalSemanticDescriptor(
    public val descriptor: OntologyEntityDescriptor,
    public val sourceId: String,
    public val release: String,
    public val moduleIri: Iri,
    public val domain: String,
    public val maturity: ExternalOntologyMaturity,
    public val locality: ExternalElementLocality = ExternalElementLocality.External,
    public val catalogStatus: ExternalElementCatalogStatus = ExternalElementCatalogStatus.Available,
)

public data class ExternalCatalogElement(
    public val descriptor: ExternalSemanticDescriptor,
    public val kind: ExternalEntityKind,
)

public data class ExternalAlreadyUsedState(
    public val externalIri: Iri,
    public val assertedInLocalGraph: Boolean = false,
    public val assertedImport: Boolean = false,
    public val approvedModuleReference: Boolean = false,
) {
    public val alreadyUsed: Boolean
        get() = assertedInLocalGraph || assertedImport || approvedModuleReference
}

public enum class ExternalMatchReasonType {
    PreferredLabel,
    AlternateLabel,
    Iri,
    Definition,
    ParentCompatibility,
    DomainCompatibility,
    RangeCompatibility,
    RelatedConcept,
    CuratedPackage,
    LocalProjectUse,
}

public enum class ExternalConfidenceBand {
    VeryStrong,
    Strong,
    Possible,
    Weak,
    LowConfidence,
}

public data class ExternalSearchContext(
    public val parentIri: Iri? = null,
    public val domainIri: Iri? = null,
    public val rangeIri: Iri? = null,
    public val parentRequired: Boolean = false,
    public val domainRequired: Boolean = false,
    public val rangeRequired: Boolean = false,
)

public data class ExternalSchemaSearchQuery(
    public val text: String,
    public val kind: ExternalEntityKind? = null,
    public val moduleIri: Iri? = null,
    public val domain: String? = null,
    public val curatedOnly: Boolean = false,
    public val maturity: Set<ExternalOntologyMaturity> = setOf(
        ExternalOntologyMaturity.Release,
        ExternalOntologyMaturity.Provisional,
    ),
    public val includeInformative: Boolean = false,
    public val context: ExternalSearchContext = ExternalSearchContext(),
    public val minimumScore: Int = 20,
    public val pageSize: Int = 25,
    public val page: Int = 0,
) {
    init {
        require(text.isNotBlank()) { "Search text must not be blank." }
        require(minimumScore >= 0) { "Minimum score must not be negative." }
        require(pageSize in 1..100) { "Page size must be between 1 and 100." }
        require(page >= 0) { "Page must not be negative." }
    }
}

public data class ExternalMatchReason(
    public val type: ExternalMatchReasonType,
    public val points: Int,
    public val matchedField: String,
    public val matchedText: String? = null,
    public val relatedIri: Iri? = null,
)

public data class ExternalScoreBreakdown(
    public val nameOrIri: Int = 0,
    public val definition: Int = 0,
    public val semanticContext: Int = 0,
    public val catalogStatus: Int = 0,
    public val localProjectRelevance: Int = 0,
) {
    public val total: Int
        get() = nameOrIri + definition + semanticContext + catalogStatus + localProjectRelevance
}

public data class ExternalSchemaCandidate(
    public val descriptor: ExternalSemanticDescriptor,
    public val kind: ExternalEntityKind,
    public val scoreModel: String = "fibo-schema-search-v1",
    public val score: ExternalScoreBreakdown = ExternalScoreBreakdown(),
    public val confidence: ExternalConfidenceBand = ExternalConfidenceBand.LowConfidence,
    public val reasons: List<ExternalMatchReason> = emptyList(),
    public val tieGroupId: String? = null,
    public val totalResultCount: Int = 0,
    public val pageSize: Int = 25,
    public val page: Int = 0,
)

public enum class ExternalDependencyCategory {
    SemanticParent,
    PropertyDomain,
    PropertyRange,
    SourceOntology,
    OwlImport,
    PackageRuntime,
    Metadata,
    LocalReference,
}

public enum class ExternalDependencyRequirement {
    Required,
    Optional,
}

public enum class ExternalDependencyClosure {
    Direct,
    PackageTransitive,
}

public enum class ExternalDependencyVisibility {
    UserVisible,
    ImplementationOnly,
}

public enum class ExternalDependencySelection {
    AlreadyAvailable,
    NewlySelected,
    Rejected,
    Missing,
}

public data class ExternalDependency(
    public val category: ExternalDependencyCategory,
    public val requirement: ExternalDependencyRequirement,
    public val closure: ExternalDependencyClosure,
    public val visibility: ExternalDependencyVisibility,
    public val selection: ExternalDependencySelection,
    public val reason: String,
    public val externalIri: Iri? = null,
    public val sourceModule: Iri? = null,
    public val maturity: ExternalOntologyMaturity = ExternalOntologyMaturity.Unknown,
    public val packageAvailable: Boolean = true,
)

public enum class ExternalDependencySetStatus {
    Complete,
    Incomplete,
    Conflicting,
    Invalid,
}

public data class ExternalDependencySet(
    public val dependencies: List<ExternalDependency> = emptyList(),
    public val status: ExternalDependencySetStatus = ExternalDependencySetStatus.Complete,
) {
    public val requiredUserVisibleDependencies: List<ExternalDependency>
        get() = dependencies.filter {
            it.visibility == ExternalDependencyVisibility.UserVisible &&
                it.requirement == ExternalDependencyRequirement.Required
        }
}

public enum class ExternalProposalIntentKind {
    ReuseClass,
    ReuseObjectProperty,
    ReuseDatatypeProperty,
    CreateLocalSubclass,
    AddExternalOntologyReference,
}

public sealed interface ExternalProposalIntent {
    public val sourceId: String
    public val dependencies: ExternalDependencySet
    public val kind: ExternalProposalIntentKind

    public data class ReuseExternalClass(
        public val classIri: Iri,
        override val sourceId: String,
        override val dependencies: ExternalDependencySet = ExternalDependencySet(),
    ) : ExternalProposalIntent {
        override val kind: ExternalProposalIntentKind = ExternalProposalIntentKind.ReuseClass
    }

    public data class ReuseExternalObjectProperty(
        public val propertyIri: Iri,
        override val sourceId: String,
        override val dependencies: ExternalDependencySet = ExternalDependencySet(),
    ) : ExternalProposalIntent {
        override val kind: ExternalProposalIntentKind = ExternalProposalIntentKind.ReuseObjectProperty
    }

    public data class ReuseExternalDatatypeProperty(
        public val propertyIri: Iri,
        override val sourceId: String,
        override val dependencies: ExternalDependencySet = ExternalDependencySet(),
    ) : ExternalProposalIntent {
        override val kind: ExternalProposalIntentKind = ExternalProposalIntentKind.ReuseDatatypeProperty
    }

    public data class CreateLocalSubclassOfExternalClass(
        public val localClassIri: Iri,
        public val externalSuperclassIri: Iri,
        override val sourceId: String,
        override val dependencies: ExternalDependencySet = ExternalDependencySet(),
    ) : ExternalProposalIntent {
        override val kind: ExternalProposalIntentKind = ExternalProposalIntentKind.CreateLocalSubclass
    }

    public data class AddExternalOntologyReference(
        public val reference: ExternalOntologyReference,
        override val sourceId: String,
        override val dependencies: ExternalDependencySet = ExternalDependencySet(),
    ) : ExternalProposalIntent {
        override val kind: ExternalProposalIntentKind = ExternalProposalIntentKind.AddExternalOntologyReference
    }
}
