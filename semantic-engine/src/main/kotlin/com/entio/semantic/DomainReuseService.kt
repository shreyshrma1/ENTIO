package com.entio.semantic

import com.entio.core.ChangeSet
import com.entio.core.DomainCustomizationClassification
import com.entio.core.DomainMaterializationClassification
import com.entio.core.DomainReuseAction
import com.entio.core.DomainReuseCustomization
import com.entio.core.DomainReuseDependency
import com.entio.core.DomainReuseDependencyDisposition
import com.entio.core.DomainReuseDifference
import com.entio.core.DomainReusePreparedBatch
import com.entio.core.DomainReusePreparedEntry
import com.entio.core.DomainReuseSourceSnapshot
import com.entio.core.EntioResult
import com.entio.core.ExternalEntityKind
import com.entio.core.ExternalOntologyMaturity
import com.entio.core.GraphChange
import com.entio.core.GraphChangeKind
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.core.RdfTerm
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import com.entio.semantic.DomainSearchAssetSupport.string
import com.entio.semantic.DomainSearchAssetSupport.stringList
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

public data class DomainReusePreparationRequest(
    public val action: DomainReuseAction,
    public val canonicalIri: Iri,
    public val managedSourceId: String,
    public val customization: DomainReuseCustomization = DomainReuseCustomization(),
    public val partialMaterializationAcknowledged: Boolean = false,
    public val localIri: Iri? = null,
    public val localSourceId: String? = null,
    public val localIriNamespace: Iri? = null,
)

/** Prepares bounded project-owned copies of supported source meaning without adding owl:imports. */
public class DomainReuseService private constructor(
    private val records: Map<String, DomainReuseRecord>,
) {
    public fun prepare(
        request: DomainReusePreparationRequest,
        projectGraph: GraphState,
        managedGraph: GraphState,
    ): EntioResult<DomainReusePreparedBatch> {
        return try {
            val started = System.nanoTime()
            require(request.managedSourceId == com.entio.core.DomainOntologyProfileIdentity.MANAGED_SOURCE_ID) {
                "domain-managed-source-required"
            }
            require(request.action != DomainReuseAction.ContinueLocally) {
                "domain-local-continuation-has-no-reuse-batch"
            }
            val root = records[request.canonicalIri.value] ?: return failure(
                "domain-source-record-missing",
                "The selected canonical IRI is absent from the approved source package.",
            )
            if (root.kind !in SUPPORTED_KINDS) {
                return failure("domain-source-kind-unsupported", "The selected source entity kind cannot be reused.")
            }
            val customization = request.customization.takeIf { request.action == DomainReuseAction.ReuseAndCustomize }
            validateCustomizationReferences(customization, projectGraph)
            val closure = dependencyClosure(root, root.dependencies(customization), started)
            val dependencies = classifyDependencies(root, closure, projectGraph)
            val blockers = dependencies.filter { it.disposition in BLOCKING_DEPENDENCIES }
            if (blockers.isNotEmpty()) {
                return failure(
                    "domain-dependency-blocked",
                    "A required domain dependency is missing, conflicting, or unsupported.",
                )
            }
            val snapshot = sourceSnapshot(root, closure)
            if (snapshot.classification == DomainMaterializationClassification.UnsupportedForReuse &&
                request.action in MATERIALIZING_ACTIONS
            ) {
                return failure(
                    "domain-source-meaning-unsupported",
                    "The selected source meaning contains a construct that cannot be safely materialized.",
                )
            }
            if (snapshot.classification == DomainMaterializationClassification.PartialMaterialization &&
                !request.partialMaterializationAcknowledged && request.action in MATERIALIZING_ACTIONS
            ) {
                return failure(
                    "domain-partial-materialization-acknowledgement-required",
                    "The source contains omitted axioms that require explicit acknowledgement.",
                )
            }

            val entries = when (request.action) {
                DomainReuseAction.Reuse, DomainReuseAction.ReuseAndCustomize -> listOf(
                    managedEntry(request, root, closure, projectGraph),
                ).filterNotNull()
                DomainReuseAction.ExtendLocally -> extendEntries(request, root, closure, projectGraph)
                DomainReuseAction.MapClose, DomainReuseAction.MapRelated ->
                    listOfNotNull(mappingEntry(request, root, projectGraph))
                DomainReuseAction.RemoveReuse -> listOf(removalEntry(request, projectGraph, managedGraph))
                DomainReuseAction.ContinueLocally -> error("handled above")
            }
            if (entries.isEmpty()) {
                return failure("domain-reuse-no-op", "The selected domain action would not change the project.")
            }
            val statementCount = entries.sumOf { it.changeSet.changes.size }
            require(statementCount <= MAX_STATEMENTS) { "domain-reuse-statement-limit" }
            val changeBytes = entries.sumOf { entry ->
                entry.changeSet.changes.sumOf { stableTriple(it.triple).toByteArray(Charsets.UTF_8).size + 16 }
            }
            val dependencyBytes = dependencies.sumOf { dependency ->
                dependency.iri.value.toByteArray(Charsets.UTF_8).size +
                    dependency.label.toByteArray(Charsets.UTF_8).size +
                    dependency.kind.name.length + dependency.disposition.name.length + 16
            }
            val snapshotBytes = snapshot.statements.sumOf { stableTriple(it).toByteArray(Charsets.UTF_8).size + 16 } +
                snapshot.omittedSourceAxioms.sumOf { it.toByteArray(Charsets.UTF_8).size + 8 } +
                snapshot.sourcePath.toByteArray(Charsets.UTF_8).size +
                snapshot.sourceOntologyIri.value.toByteArray(Charsets.UTF_8).size + 128
            val payloadBytes = changeBytes + dependencyBytes + snapshotBytes
            require(payloadBytes <= MAX_PAYLOAD_BYTES) { "domain-reuse-preview-size-limit" }
            require(System.nanoTime() - started <= PREPARATION_TIMEOUT_NANOS) { "domain-reuse-preparation-timeout" }
            EntioResult.Success(
                DomainReusePreparedBatch(
                    action = request.action,
                    canonicalIri = request.canonicalIri,
                    entries = entries,
                    dependencies = dependencies,
                    sourceSnapshot = snapshot,
                    explicitSelectionCount = 1,
                    generatedStatementCount = statementCount,
                    preparedPayloadBytes = payloadBytes,
                    partialMaterializationAcknowledged = request.partialMaterializationAcknowledged,
                ),
            )
        } catch (failure: IllegalArgumentException) {
            failure(failure.message ?: "domain-reuse-invalid", "The domain reuse request is invalid.")
        }
    }

    public fun describe(canonicalIri: Iri, projectGraph: GraphState): EntioResult<DomainReuseDifference> {
        val record = records[canonicalIri.value]
            ?: return failure("domain-source-record-missing", "The source record is unavailable.")
        val snapshot = sourceSnapshot(record)
        val source = snapshot.statements.toSet()
        val project = projectGraph.triples.filter { it.subjectResource == canonicalIri }.sortedBy(::stableTriple)
        val added = project.filterNot(source::contains)
        val removed = snapshot.statements.filterNot(project.toSet()::contains)
        val classification = when {
            record.maturity == ExternalOntologyMaturity.Deprecated -> DomainCustomizationClassification.SourceEntityDeprecated
            added.isEmpty() && removed.isEmpty() -> DomainCustomizationClassification.Unchanged
            (added + removed).all { it.predicate in ANNOTATION_PREDICATES } -> DomainCustomizationClassification.AnnotationOnly
            else -> DomainCustomizationClassification.LogicalStructureChanged
        }
        return EntioResult.Success(
            DomainReuseDifference(
                entityId = entityId(canonicalIri),
                canonicalIri = canonicalIri,
                sourceSnapshot = snapshot,
                projectStatements = project,
                addedProjectStatements = added,
                removedSourceStatements = removed,
                classification = classification,
            ),
        )
    }

    public fun resolveEntityId(entityId: String, projectGraph: GraphState): Iri? = projectGraph.triples
        .flatMap { listOf(it.subjectResource, it.objectTerm) }
        .filterIsInstance<Iri>()
        .filter { it.value in records }
        .distinct()
        .singleOrNull { entityId(it) == entityId }

    private fun managedEntry(
        request: DomainReusePreparationRequest,
        root: DomainReuseRecord,
        closure: List<DomainReuseRecord>,
        projectGraph: GraphState,
    ): DomainReusePreparedEntry? {
        val customization = request.customization.takeIf { request.action == DomainReuseAction.ReuseAndCustomize }
        val triples = closure.flatMap { record ->
            supportedStatements(record, if (record == root) customization else null)
        }.distinct().sortedBy(::stableTriple)
        val changes = triples.filterNot(projectGraph.triples::contains).map { GraphChange(GraphChangeKind.Addition, it) }
        return changes.takeIf { it.isNotEmpty() }
            ?.let { DomainReusePreparedEntry(request.managedSourceId, ChangeSet(it)) }
    }

    private fun extendEntries(
        request: DomainReusePreparationRequest,
        root: DomainReuseRecord,
        closure: List<DomainReuseRecord>,
        projectGraph: GraphState,
    ): List<DomainReusePreparedEntry> {
        val localIri = requireNotNull(request.localIri) { "domain-local-iri-required" }
        val localSource = requireNotNull(request.localSourceId) { "domain-local-source-required" }
        val localNamespace = requireNotNull(request.localIriNamespace) { "domain-local-iri-namespace-required" }
        require(localIri.value.startsWith(localNamespace.value)) { "domain-local-iri-must-use-project-namespace" }
        val managed = managedEntry(request.copy(action = DomainReuseAction.Reuse), root, closure, projectGraph)
        val declaration = declaration(root.kind)
        val relation = when (root.kind) {
            ExternalEntityKind.Class -> RDFS_SUBCLASS_OF
            ExternalEntityKind.ObjectProperty, ExternalEntityKind.DatatypeProperty -> RDFS_SUBPROPERTY_OF
        }
        val local = listOf(
            GraphTriple(localIri, RDF_TYPE, declaration),
            GraphTriple(localIri, relation, root.iri),
        ).filterNot(projectGraph.triples::contains).map { GraphChange(GraphChangeKind.Addition, it) }
        val localEntry = local.takeIf { it.isNotEmpty() }
            ?.let { DomainReusePreparedEntry(localSource, ChangeSet(it)) }
        return listOfNotNull(managed, localEntry)
    }

    private fun mappingEntry(
        request: DomainReusePreparationRequest,
        root: DomainReuseRecord,
        projectGraph: GraphState,
    ): DomainReusePreparedEntry? {
        val localIri = requireNotNull(request.localIri) { "domain-mapping-local-iri-required" }
        val localSource = requireNotNull(request.localSourceId) { "domain-local-source-required" }
        require(!isExternal(localIri)) { "domain-mapping-subject-must-be-local" }
        require(projectGraph.triples.any { it.subjectResource == localIri }) { "domain-mapping-subject-must-exist" }
        val predicate = if (request.action == DomainReuseAction.MapClose) SKOS_CLOSE_MATCH else SKOS_RELATED_MATCH
        val triple = GraphTriple(localIri, predicate, root.iri)
        if (triple in projectGraph.triples) return null
        return DomainReusePreparedEntry(
            localSource,
            ChangeSet(listOf(GraphChange(GraphChangeKind.Addition, triple))),
        )
    }

    private fun removalEntry(
        request: DomainReusePreparationRequest,
        projectGraph: GraphState,
        managedGraph: GraphState,
    ): DomainReusePreparedEntry {
        val owned = managedGraph.triples.filter { it.subjectResource == request.canonicalIri }
        require(owned.isNotEmpty()) { "domain-reuse-not-present" }
        val managed = managedGraph.triples.toSet()
        val localDependencies = projectGraph.triples.filter { triple ->
            triple !in managed &&
                triple.objectTerm == request.canonicalIri &&
                triple.predicate !in MAPPING_PREDICATES
        }
        require(localDependencies.isEmpty()) { "domain-reuse-removal-has-local-dependencies" }
        return DomainReusePreparedEntry(
            request.managedSourceId,
            ChangeSet(owned.sortedBy(::stableTriple).map { GraphChange(GraphChangeKind.Removal, it) }),
        )
    }

    private fun dependencyClosure(
        root: DomainReuseRecord,
        rootDependencies: List<String>,
        started: Long,
    ): List<DomainReuseRecord> {
        val selected = linkedMapOf(root.iri.value to root)
        var frontierIris = rootDependencies
        var depth = 0
        while (frontierIris.isNotEmpty()) {
            require(depth++ < MAX_DEPTH) { "domain-dependency-depth-limit" }
            val frontier = frontierIris.distinct().sorted().mapNotNull { iri ->
                when {
                    iri in selected -> null
                    iri in records -> records.getValue(iri).also { selected[iri] = it }
                    isExternal(Iri(iri)) -> MissingDomainReuseRecord(iri).also { selected[iri] = it }
                    else -> null
                }
            }
            frontierIris = frontier.flatMap { it.dependencies() }
            require(selected.size <= MAX_CLOSURE) { "domain-dependency-entity-limit" }
            require(System.nanoTime() - started <= PREPARATION_TIMEOUT_NANOS) { "domain-reuse-preparation-timeout" }
        }
        return selected.values.sortedBy { it.iri.value }
    }

    private fun validateCustomizationReferences(
        customization: DomainReuseCustomization?,
        projectGraph: GraphState,
    ): Unit {
        customization?.referencedIris().orEmpty().forEach { iri ->
            require(
                iri.value in records ||
                    BUILT_IN_VOCABULARY_PREFIXES.any(iri.value::startsWith) ||
                    projectGraph.triples.any { it.subjectResource == iri },
            ) { "domain-customization-reference-unresolved" }
        }
    }

    private fun classifyDependencies(
        root: DomainReuseRecord,
        closure: List<DomainReuseRecord>,
        projectGraph: GraphState,
    ): List<DomainReuseDependency> = closure.map { record ->
        val projectStatements = projectGraph.triples.filter { it.subjectResource == record.iri }
        val sourceStatements = if (record.missing) emptyList() else supportedStatements(record)
        val declarationConflict = projectStatements.any { it.predicate == RDF_TYPE && it.objectTerm != declaration(record.kind) }
        val disposition = when {
            record == root -> DomainReuseDependencyDisposition.ExplicitlySelected
            record.missing -> DomainReuseDependencyDisposition.Missing
            record.kind !in SUPPORTED_KINDS || record.hasBlockingUnsupportedConstruct() ->
                DomainReuseDependencyDisposition.Unsupported
            declarationConflict -> DomainReuseDependencyDisposition.Conflicting
            projectStatements.isEmpty() -> DomainReuseDependencyDisposition.RequiredStructuralDependency
            projectStatements.toSet().containsAll(sourceStatements) -> DomainReuseDependencyDisposition.AlreadyPresentUnchanged
            else -> DomainReuseDependencyDisposition.AlreadyPresentCustomized
        }
        DomainReuseDependency(record.iri, record.preferredLabel, record.kind, disposition)
    }.sortedBy { it.iri.value }

    private fun sourceSnapshot(
        record: DomainReuseRecord,
        closure: List<DomainReuseRecord> = listOf(record),
    ): DomainReuseSourceSnapshot {
        val statements = supportedStatements(record).sortedBy(::stableTriple)
        val omitted = closure.flatMap { dependency ->
            dependency.unsupportedConstructs.map { "${dependency.iri.value}|$it" }
        }.distinct().sorted()
        val classification = when {
            record.kind !in SUPPORTED_KINDS || record.missing || closure.any(DomainReuseRecord::hasBlockingUnsupportedConstruct) ->
                DomainMaterializationClassification.UnsupportedForReuse
            omitted.isNotEmpty() -> DomainMaterializationClassification.PartialMaterialization
            else -> DomainMaterializationClassification.CompleteSupportedMaterialization
        }
        return DomainReuseSourceSnapshot(
            canonicalIri = record.iri,
            kind = record.kind,
            sourceFamily = record.sourceFamily,
            sourceOntologyIri = record.ontologyIri,
            sourcePath = record.sourcePath,
            recordFingerprint = record.recordFingerprint,
            statementFingerprint = sha256(statements.joinToString("\n", transform = ::stableTriple)),
            statements = statements,
            omittedSourceAxioms = omitted,
            classification = classification,
        )
    }

    private fun supportedStatements(
        record: DomainReuseRecord,
        customization: DomainReuseCustomization? = null,
    ): List<GraphTriple> {
        val preferred = customization?.preferredLabel ?: record.preferredLabel
        val definitions = customization?.definition?.let(::listOf) ?: record.definitions.take(1)
        val alternates = customization?.alternateLabels ?: record.alternateLabels.take(20)
        val parents = customization?.parentIris?.map(Iri::value) ?: record.parents
        val domains = customization?.domainIris?.map(Iri::value) ?: record.domains
        val ranges = customization?.rangeIris?.map(Iri::value) ?: record.ranges
        return buildList {
            add(GraphTriple(record.iri, RDF_TYPE, declaration(record.kind)))
            if (preferred.isNotBlank()) add(GraphTriple(record.iri, RDFS_LABEL, RdfLiteral(preferred)))
            alternates.distinct().sorted().forEach { add(GraphTriple(record.iri, SKOS_ALT_LABEL, RdfLiteral(it))) }
            definitions.filter(String::isNotBlank).distinct().sorted().forEach {
                add(GraphTriple(record.iri, SKOS_DEFINITION, RdfLiteral(it)))
            }
            val hierarchy = if (record.kind == ExternalEntityKind.Class) RDFS_SUBCLASS_OF else RDFS_SUBPROPERTY_OF
            parents.distinct().sorted().forEach { add(GraphTriple(record.iri, hierarchy, Iri(it))) }
            domains.distinct().sorted().forEach { add(GraphTriple(record.iri, RDFS_DOMAIN, Iri(it))) }
            ranges.distinct().sorted().forEach { add(GraphTriple(record.iri, RDFS_RANGE, Iri(it))) }
        }.distinct().sortedBy(::stableTriple)
    }

    private fun declaration(kind: ExternalEntityKind): Iri = when (kind) {
        ExternalEntityKind.Class -> OWL_CLASS
        ExternalEntityKind.ObjectProperty -> OWL_OBJECT_PROPERTY
        ExternalEntityKind.DatatypeProperty -> OWL_DATATYPE_PROPERTY
    }

    private fun failure(code: String, message: String): EntioResult.Failure = EntioResult.Failure(
        message,
        listOf(ValidationIssue(ValidationSeverity.Error, code, message, "domain-reuse")),
    )

    public companion object {
        public fun open(searchRoot: Path): DomainReuseService {
            val loader = Load(LoadSettings.builder().setLabel("domain-reuse-record").build())
            val records = Files.readAllLines(searchRoot.resolve("descriptors-v1.jsonl"))
                .filter(String::isNotBlank)
                .map { line ->
                    val value = loader.loadFromString(line) as Map<*, *>
                    DomainReuseRecord(
                        iri = Iri(value.string("iri")),
                        kind = ExternalEntityKind.valueOf(value.string("kind")),
                        sourceFamily = value.string("sourceFamily"),
                        sourcePath = value.string("sourcePath"),
                        ontologyIri = Iri(value.string("ontologyIri")),
                        maturity = ExternalOntologyMaturity.valueOf(value.string("maturity")),
                        preferredLabel = value.string("preferredLabel"),
                        alternateLabels = value.stringList("alternateLabels"),
                        definitions = value.stringList("definitions"),
                        parents = value.stringList("parents"),
                        domains = value.stringList("domains"),
                        ranges = value.stringList("ranges"),
                        unsupportedConstructs = value.stringList("unsupportedConstructs"),
                        recordFingerprint = value.string("recordFingerprint"),
                    )
                }.associateBy { it.iri.value }
            return DomainReuseService(records)
        }

        public fun entityId(iri: Iri): String = "dre_" + sha256(iri.value).take(40)

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(Locale.ROOT, it) }

        private fun stableTriple(triple: GraphTriple): String = listOf(
            triple.subjectResource.value,
            triple.predicate.value,
            when (val term: RdfTerm = triple.objectTerm) {
                is Iri -> "I:${term.value}"
                is com.entio.core.BlankNodeResource -> "B:${term.id}"
                is RdfLiteral -> "L:${term.lexicalForm}|${term.datatypeIri?.value.orEmpty()}|${term.languageTag.orEmpty()}"
            },
        ).joinToString("\u001F")

        private fun isExternal(iri: Iri): Boolean = iri.value.startsWith(FIBO_PREFIX) || iri.value.startsWith(COMMONS_PREFIX)

        private const val MAX_CLOSURE: Int = 100
        private const val MAX_STATEMENTS: Int = 2_000
        private const val MAX_PAYLOAD_BYTES: Int = 2_097_152
        private const val MAX_DEPTH: Int = 16
        private const val PREPARATION_TIMEOUT_NANOS: Long = 10_000_000_000
        private const val FIBO_PREFIX: String = "https://spec.edmcouncil.org/fibo/ontology/"
        private const val COMMONS_PREFIX: String = "https://www.omg.org/spec/Commons/"
        private val BUILT_IN_VOCABULARY_PREFIXES = listOf(
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            "http://www.w3.org/2000/01/rdf-schema#",
            "http://www.w3.org/2001/XMLSchema#",
            "http://www.w3.org/2002/07/owl#",
            "http://www.w3.org/2004/02/skos/core#",
        )
        private val SUPPORTED_KINDS = setOf(
            ExternalEntityKind.Class,
            ExternalEntityKind.ObjectProperty,
            ExternalEntityKind.DatatypeProperty,
        )
        private val MATERIALIZING_ACTIONS = setOf(
            DomainReuseAction.Reuse,
            DomainReuseAction.ReuseAndCustomize,
            DomainReuseAction.ExtendLocally,
        )
        private val BLOCKING_DEPENDENCIES = setOf(
            DomainReuseDependencyDisposition.Conflicting,
            DomainReuseDependencyDisposition.Unsupported,
            DomainReuseDependencyDisposition.Missing,
        )
        private val RDF_TYPE = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
        private val OWL_CLASS = Iri("http://www.w3.org/2002/07/owl#Class")
        private val OWL_OBJECT_PROPERTY = Iri("http://www.w3.org/2002/07/owl#ObjectProperty")
        private val OWL_DATATYPE_PROPERTY = Iri("http://www.w3.org/2002/07/owl#DatatypeProperty")
        private val RDFS_LABEL = Iri("http://www.w3.org/2000/01/rdf-schema#label")
        private val RDFS_SUBCLASS_OF = Iri("http://www.w3.org/2000/01/rdf-schema#subClassOf")
        private val RDFS_SUBPROPERTY_OF = Iri("http://www.w3.org/2000/01/rdf-schema#subPropertyOf")
        private val RDFS_DOMAIN = Iri("http://www.w3.org/2000/01/rdf-schema#domain")
        private val RDFS_RANGE = Iri("http://www.w3.org/2000/01/rdf-schema#range")
        private val SKOS_ALT_LABEL = Iri("http://www.w3.org/2004/02/skos/core#altLabel")
        private val SKOS_DEFINITION = Iri("http://www.w3.org/2004/02/skos/core#definition")
        private val SKOS_CLOSE_MATCH = Iri("http://www.w3.org/2004/02/skos/core#closeMatch")
        private val SKOS_RELATED_MATCH = Iri("http://www.w3.org/2004/02/skos/core#relatedMatch")
        private val ANNOTATION_PREDICATES = setOf(RDFS_LABEL, SKOS_ALT_LABEL, SKOS_DEFINITION, SKOS_CLOSE_MATCH, SKOS_RELATED_MATCH)
        private val MAPPING_PREDICATES = setOf(SKOS_CLOSE_MATCH, SKOS_RELATED_MATCH)
    }
}

private val BLOCKING_UNSUPPORTED_CONSTRUCTS = setOf(
    "http://www.w3.org/2002/07/owl#equivalentClass",
    "http://www.w3.org/2002/07/owl#equivalentProperty",
    "http://www.w3.org/2002/07/owl#propertyChainAxiom",
)

private open class DomainReuseRecord(
    val iri: Iri,
    val kind: ExternalEntityKind,
    val sourceFamily: String,
    val sourcePath: String,
    val ontologyIri: Iri,
    val maturity: ExternalOntologyMaturity,
    val preferredLabel: String,
    val alternateLabels: List<String>,
    val definitions: List<String>,
    val parents: List<String>,
    val domains: List<String>,
    val ranges: List<String>,
    val unsupportedConstructs: List<String>,
    val recordFingerprint: String,
    val missing: Boolean = false,
) {
    fun dependencies(customization: DomainReuseCustomization? = null): List<String> = (
        (customization?.parentIris?.map(Iri::value) ?: parents) +
            (customization?.domainIris?.map(Iri::value) ?: domains) +
            (customization?.rangeIris?.map(Iri::value) ?: ranges)
    ).distinct().sorted()

    fun hasBlockingUnsupportedConstruct(): Boolean = unsupportedConstructs.any { construct ->
        construct.substringBefore('|') in BLOCKING_UNSUPPORTED_CONSTRUCTS
    }
}

private fun DomainReuseCustomization.referencedIris(): List<Iri> =
    listOf(parentIris, domainIris, rangeIris).filterNotNull().flatten().distinct().sortedBy(Iri::value)

private class MissingDomainReuseRecord(iri: String) : DomainReuseRecord(
    iri = Iri(iri),
    kind = ExternalEntityKind.Class,
    sourceFamily = "UNKNOWN",
    sourcePath = "",
    ontologyIri = Iri("urn:entio:missing-domain-record"),
    maturity = ExternalOntologyMaturity.Unknown,
    preferredLabel = iri.substringAfterLast('#').substringAfterLast('/'),
    alternateLabels = emptyList(),
    definitions = emptyList(),
    parents = emptyList(),
    domains = emptyList(),
    ranges = emptyList(),
    unsupportedConstructs = emptyList(),
    recordFingerprint = "missing",
    missing = true,
)
