package com.entio.semantic

import com.entio.core.DomainOntologyMigrationStatus
import com.entio.core.DomainOntologyProfileIdentity
import com.entio.core.DomainProfileActivationPreview
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.Iri
import java.nio.file.Files
import java.nio.file.Path
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

public enum class DomainMigrationWorkState {
    Staged,
    Proposed,
    Rejected,
    RolledBack,
}

public data class DomainMigrationOpenWork(
    public val state: DomainMigrationWorkState,
    public val externalIris: Set<Iri>,
    public val baselineRetained: Boolean = true,
)

public data class DomainMigrationReport(
    public val status: DomainOntologyMigrationStatus,
    public val detectedIris: List<Iri>,
    public val recognizedIris: List<Iri>,
    public val unsupportedIris: List<Iri>,
    public val localExtensionCount: Int,
    public val verifiedCurrentRelease: String?,
    public val historicalRelease: String? = null,
    public val provenanceSeedCandidates: List<Iri> = emptyList(),
    public val provenanceSeedingEligible: Boolean = false,
    public val openWork: List<DomainMigrationOpenWork> = emptyList(),
    public val openWorkBaselineRetained: Boolean = true,
    public val profileAlreadyActive: Boolean = false,
    public val issues: List<String> = emptyList(),
)

public data class DomainMigrationPreview(
    public val report: DomainMigrationReport,
    public val activation: DomainProfileActivationPreview,
    public val movesExistingStatements: Boolean = false,
    public val seedsProvenance: Boolean = false,
    public val requiresNormalProposalForStatementMovement: Boolean = true,
)

/** Detects legacy FIBO use against the verified current descriptor package without inferring history. */
public class DomainMigrationService private constructor(
    private val knownEntityIris: Set<String>,
    private val knownOntologyIris: Set<String>,
    private val declarationTypes: Map<String, String>,
    private val profiles: DomainProfileService,
) {
    public fun detect(
        project: EntioProject,
        openWork: List<DomainMigrationOpenWork> = emptyList(),
    ): DomainMigrationReport {
        val detected = project.graph.triples.flatMap { triple ->
            listOf(triple.subjectResource.value, triple.predicate.value, (triple.objectTerm as? Iri)?.value)
        }.filterNotNull().filter(::isDomainIri).toSortedSet()
        val recognized = detected.filter { it in knownEntityIris || it in knownOntologyIris }
        val unsupported = detected.filterNot { it in knownEntityIris || it in knownOntologyIris }
        val active = project.activeDomainOntology != null
        val status = when {
            active || detected.isEmpty() -> DomainOntologyMigrationStatus.NoExistingReuse
            recognized.isEmpty() -> DomainOntologyMigrationStatus.ExistingReuseUnsupported
            unsupported.isNotEmpty() -> DomainOntologyMigrationStatus.ExistingReuseAmbiguous
            else -> DomainOntologyMigrationStatus.ExistingReuseRecognized
        }
        val exactDeclarations = project.graph.triples.filter { triple ->
            val subject = triple.subjectResource.value
            subject in recognized && triple.predicate.value == RDF_TYPE &&
                (triple.objectTerm as? Iri)?.value == declarationTypes[subject]
        }.mapNotNull { it.subjectResource as? Iri }.distinct().sortedBy { it.value }
        val localExtensions = project.graph.triples.count { triple ->
            !isDomainIri(triple.subjectResource.value) &&
                (isDomainIri(triple.predicate.value) || ((triple.objectTerm as? Iri)?.value?.let(::isDomainIri) == true))
        }
        val normalizedWork = openWork.map { work ->
            work.copy(externalIris = work.externalIris.filter { isDomainIri(it.value) }.toSortedSet(compareBy(Iri::value)))
        }.sortedWith(compareBy<DomainMigrationOpenWork> { it.state.ordinal }.thenBy { it.externalIris.joinToString { iri -> iri.value } })
        return DomainMigrationReport(
            status = status,
            detectedIris = detected.map(::Iri),
            recognizedIris = recognized.map(::Iri),
            unsupportedIris = unsupported.map(::Iri),
            localExtensionCount = localExtensions,
            verifiedCurrentRelease = DomainOntologyProfileIdentity.RELEASE.takeIf {
                status == DomainOntologyMigrationStatus.ExistingReuseRecognized
            },
            provenanceSeedCandidates = exactDeclarations.takeIf {
                status == DomainOntologyMigrationStatus.ExistingReuseRecognized
            }.orEmpty(),
            // Historical proposal and actor identifiers are not present in legacy RDF, so Slice 11 never invents them.
            provenanceSeedingEligible = false,
            openWork = normalizedWork,
            openWorkBaselineRetained = normalizedWork.all(DomainMigrationOpenWork::baselineRetained),
            profileAlreadyActive = active,
            issues = buildList {
                if (status == DomainOntologyMigrationStatus.ExistingReuseAmbiguous) {
                    add("Some detected domain IRIs are not in the approved current package; no historical release was inferred.")
                }
                if (status == DomainOntologyMigrationStatus.ExistingReuseUnsupported) {
                    add("Detected domain IRIs are absent from the approved current package.")
                }
                if (exactDeclarations.isNotEmpty()) {
                    add("Source declarations match the current package, but legacy provenance is incomplete and was not created.")
                }
                if (normalizedWork.isNotEmpty()) {
                    add("Existing open-work baselines are retained and may become stale through the normal baseline checks after activation.")
                }
            },
        )
    }

    public fun preview(
        projectRoot: Path,
        project: EntioProject,
        openWork: List<DomainMigrationOpenWork> = emptyList(),
    ): EntioResult<DomainMigrationPreview> {
        val report = detect(project, openWork)
        if (report.status != DomainOntologyMigrationStatus.ExistingReuseRecognized) {
            return EntioResult.Failure(
                "Only recognized current-package reuse can produce a migration preview.",
                listOf(com.entio.core.ValidationIssue(
                    com.entio.core.ValidationSeverity.Error,
                    "domain-migration-not-recognized",
                    "Resolve ambiguous or unsupported domain identities before activation.",
                    "domain-migration",
                )),
            )
        }
        return when (val activation = profiles.previewActivation(projectRoot)) {
            is EntioResult.Failure -> activation
            is EntioResult.Success -> EntioResult.Success(DomainMigrationPreview(report, activation.value))
        }
    }

    public companion object {
        public fun open(
            searchRoot: Path,
            profiles: DomainProfileService = DomainProfileService(),
        ): DomainMigrationService {
            val manifest = DomainSearchAssetSupport.mapping(searchRoot.resolve("manifest.yaml"))
            require(manifest["schema"] == "entio-domain-descriptor-package-v1")
            require(manifest["sourceId"] == DomainOntologyProfileIdentity.SOURCE_ID)
            require(manifest["release"] == DomainOntologyProfileIdentity.RELEASE)
            require(manifest["packageFingerprint"] == DomainOntologyProfileIdentity.PACKAGE_FINGERPRINT)
            require(
                DomainSearchAssetSupport.sha256(searchRoot.resolve("descriptors-v1.jsonl")) ==
                    manifest["descriptorsSha256"],
            )
            val loader = Load(LoadSettings.builder().setLabel("domain-migration-descriptor").build())
            val records = Files.readAllLines(searchRoot.resolve("descriptors-v1.jsonl"))
                .filter(String::isNotBlank)
                .map { loader.loadFromString(it) as Map<*, *> }
            val entities = records.map { it["iri"] as String }.toSet()
            val ontologies = records.map { it["ontologyIri"] as String }.toSet()
            val declarations = records.associate { record ->
                val kind = record["kind"] as String
                (record["iri"] as String) to when (kind) {
                    "Class" -> OWL_CLASS
                    "ObjectProperty" -> OWL_OBJECT_PROPERTY
                    "DatatypeProperty" -> OWL_DATATYPE_PROPERTY
                    else -> ""
                }
            }.filterValues(String::isNotEmpty)
            return DomainMigrationService(entities, ontologies, declarations, profiles)
        }

        private fun isDomainIri(value: String): Boolean =
            value.startsWith(FIBO_PREFIX) || value.startsWith(COMMONS_PREFIX)

        private const val FIBO_PREFIX = "https://spec.edmcouncil.org/fibo/ontology/"
        private const val COMMONS_PREFIX = "https://www.omg.org/spec/Commons/"
        private const val RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
        private const val OWL_CLASS = "http://www.w3.org/2002/07/owl#Class"
        private const val OWL_OBJECT_PROPERTY = "http://www.w3.org/2002/07/owl#ObjectProperty"
        private const val OWL_DATATYPE_PROPERTY = "http://www.w3.org/2002/07/owl#DatatypeProperty"
    }
}
