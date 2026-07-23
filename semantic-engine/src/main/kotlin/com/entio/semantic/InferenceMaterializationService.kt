package com.entio.semantic

import com.entio.core.AddObjectPropertyAssertionEdit
import com.entio.core.AddSuperclassEdit
import com.entio.core.AssignTypeEdit
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.FactOrigin
import com.entio.core.GraphTriple
import com.entio.core.InferenceFactId
import com.entio.core.InferenceImportDependence
import com.entio.core.InferenceImportDependenceState
import com.entio.core.InferenceMaterializationCandidate
import com.entio.core.InferenceMaterializationFact
import com.entio.core.InferenceMaterializationKind
import com.entio.core.InferenceSourceCandidate
import com.entio.core.InferenceStageability
import com.entio.core.Iri
import com.entio.core.ReasoningResult
import com.entio.core.ReasoningRunStatus
import com.entio.core.RdfResource
import com.entio.core.SemanticFactKey
import com.entio.core.SymbolKind
import com.entio.core.TypedOntologyEdit
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

public data class InferenceMaterializationIdentityContext(
    public val projectId: String,
    public val submittingUserId: String,
    public val reasoningJobId: String,
) {
    init {
        require(projectId.isNotBlank()) { "Materialization project ID must not be blank." }
        require(submittingUserId.isNotBlank()) { "Materialization user ID must not be blank." }
        require(reasoningJobId.isNotBlank()) { "Materialization reasoning job ID must not be blank." }
    }
}

/**
 * Semantic analysis for one reasoning fact.
 *
 * Unsupported anonymous terms retain a safe state and display values but never gain a
 * materialization candidate or typed edit.
 */
public data class InferenceMaterializationAnalysis(
    public val kind: InferenceMaterializationKind,
    public val subject: RdfResource,
    public val predicate: Iri,
    public val objectValue: RdfResource,
    public val stageability: InferenceStageability,
    public val reason: String,
    public val candidate: InferenceMaterializationCandidate? = null,
    public val edit: TypedOntologyEdit? = null,
    public val triple: GraphTriple? = null,
)

/** Converts retained inferred facts into existing typed-edit paths without mutation. */
public class InferenceMaterializationService(
    private val editTranslator: TypedOntologyEditTranslator = TypedOntologyEditTranslator(),
) {
    public fun analyze(
        project: EntioProject,
        reasoning: ReasoningResult,
        identity: InferenceMaterializationIdentityContext,
    ): List<InferenceMaterializationAnalysis> {
        if (reasoning.metadata.status != ReasoningRunStatus.Completed) return emptyList()

        val rawFacts = buildList {
            reasoning.classRelationships.filter { it.origin == FactOrigin.Inferred }.forEach {
                add(RawInference(it.subject, RDFS_SUBCLASS_OF, it.objectClass, InferenceMaterializationKind.SubclassRelationship, it.sourceId))
            }
            reasoning.individualTypes.filter { it.origin == FactOrigin.Inferred }.forEach {
                add(RawInference(it.individual, RDF_TYPE, it.type, InferenceMaterializationKind.IndividualType, it.sourceId))
            }
            reasoning.propertyRelationships.filter { it.origin == FactOrigin.Inferred }.forEach {
                add(RawInference(it.subject, it.predicate, it.objectResource, InferenceMaterializationKind.ObjectPropertyAssertion, it.sourceId))
            }
        }

        return rawFacts
            .map { analyzeFact(project, reasoning, identity, it) }
            .sortedWith(
                compareBy<InferenceMaterializationAnalysis>({ it.kind.ordinal }, { it.subject.value }, { it.predicate.value }, { it.objectValue.value })
                    .thenBy { it.candidate?.factId?.value.orEmpty() },
            )
    }

    public fun semanticFactKey(fact: InferenceMaterializationFact): SemanticFactKey =
        SemanticFactKey(
            "$SEMANTIC_KEY_PREFIX${digest(
                lengthPrefixed(
                    fact.kind.name,
                    canonical(fact.subject),
                    fact.predicate.value,
                    canonical(fact.objectValue),
                ),
            )}",
        )

    public fun factId(
        identity: InferenceMaterializationIdentityContext,
        graphFingerprint: String,
        semanticFactKey: SemanticFactKey,
    ): InferenceFactId {
        require(graphFingerprint.isNotBlank()) { "Materialization graph fingerprint must not be blank." }
        return InferenceFactId(
            "$FACT_ID_PREFIX${digest(
                lengthPrefixed(
                    identity.projectId,
                    identity.submittingUserId,
                    identity.reasoningJobId,
                    graphFingerprint,
                    semanticFactKey.value,
                ),
            )}",
        )
    }

    private fun analyzeFact(
        project: EntioProject,
        reasoning: ReasoningResult,
        identity: InferenceMaterializationIdentityContext,
        raw: RawInference,
    ): InferenceMaterializationAnalysis {
        val subject = raw.subject as? Iri
        val objectValue = raw.objectValue as? Iri
        if (subject == null || objectValue == null) {
            return raw.analysis(
                InferenceStageability.UnsupportedTerm,
                "Only named IRI resources can be materialized.",
            )
        }

        val fact = InferenceMaterializationFact(raw.kind, subject, raw.predicate, objectValue)
        val semanticKey = semanticFactKey(fact)
        val factId = factId(identity, reasoning.metadata.fingerprints.graphFingerprint, semanticKey)
        val triple = GraphTriple(subject, raw.predicate, objectValue)
        val edit = typedEdit(fact)
        val sourceCandidates = writableSubjectSources(project, subject)
        val importDependence = importDependence(project, reasoning, raw)

        val stageability = when {
            triple in project.graph.triples -> InferenceStageability.AlreadyAsserted
            !entitiesCompatible(project, fact) -> InferenceStageability.MissingEntity
            raw.kind == InferenceMaterializationKind.ObjectPropertyAssertion && !isObjectProperty(project, raw.predicate) ->
                InferenceStageability.InvalidPredicate
            sourceCandidates.isEmpty() -> InferenceStageability.NoWritableSource
            sourceCandidates.size > 1 -> InferenceStageability.AmbiguousSource
            importDependence.state == InferenceImportDependenceState.Unknown -> InferenceStageability.ImportDependencyUnsafe
            !translatesTo(edit, triple) -> InferenceStageability.UnsupportedType
            else -> InferenceStageability.Stageable
        }
        val selectedSource = sourceCandidates.singleOrNull()
        val candidate = InferenceMaterializationCandidate(
            factId = factId,
            semanticFactKey = semanticKey,
            fact = fact,
            stageability = stageability,
            sourceCandidates = sourceCandidates.map { sourceId ->
                InferenceSourceCandidate(sourceId, selected = sourceId == selectedSource)
            },
            selectedSourceId = selectedSource,
            importDependence = importDependence,
        )
        return raw.analysis(
            stageability = stageability,
            reason = reason(stageability),
            candidate = candidate,
            edit = edit,
            triple = triple,
        )
    }

    private fun typedEdit(fact: InferenceMaterializationFact): TypedOntologyEdit = when (fact.kind) {
        InferenceMaterializationKind.SubclassRelationship -> AddSuperclassEdit(fact.subject, fact.objectValue)
        InferenceMaterializationKind.IndividualType -> AssignTypeEdit(fact.subject, fact.objectValue)
        InferenceMaterializationKind.ObjectPropertyAssertion ->
            AddObjectPropertyAssertionEdit(fact.subject, fact.predicate, fact.objectValue)
    }

    private fun translatesTo(edit: TypedOntologyEdit, expected: GraphTriple): Boolean =
        when (val translated = editTranslator.translate(edit)) {
            is EntioResult.Failure -> false
            is EntioResult.Success -> translated.value.changes.singleOrNull()?.triple == expected
        }

    private fun entitiesCompatible(project: EntioProject, fact: InferenceMaterializationFact): Boolean {
        fun hasKind(iri: Iri, kind: SymbolKind): Boolean = project.symbols.any { it.iri == iri && it.kind == kind }
        fun exists(iri: Iri): Boolean = project.symbols.any { it.iri == iri }
        return when (fact.kind) {
            InferenceMaterializationKind.SubclassRelationship ->
                hasKind(fact.subject, SymbolKind.Class) && hasKind(fact.objectValue, SymbolKind.Class)
            InferenceMaterializationKind.IndividualType ->
                exists(fact.subject) && hasKind(fact.objectValue, SymbolKind.Class)
            InferenceMaterializationKind.ObjectPropertyAssertion ->
                exists(fact.subject) && exists(fact.objectValue)
        }
    }

    private fun isObjectProperty(project: EntioProject, predicate: Iri): Boolean =
        project.graph.triples.any {
            it.subjectResource == predicate && it.predicate == RDF_TYPE && it.objectTerm == OWL_OBJECT_PROPERTY
        }

    private fun writableSubjectSources(project: EntioProject, subject: Iri): List<String> {
        val importedSourceIds = project.config.importMappings.values.toSet()
        val writableSourceIds = project.resolvedSources.map { it.id }.toSet() - importedSourceIds
        return project.symbols
            .asSequence()
            .filter { it.iri == subject && it.sourceId in writableSourceIds }
            .map { it.sourceId }
            .distinct()
            .sorted()
            .toList()
    }

    private fun importDependence(
        project: EntioProject,
        reasoning: ReasoningResult,
        raw: RawInference,
    ): InferenceImportDependence {
        val importedSourceIds = project.config.importMappings.values.toSortedSet()
        if (importedSourceIds.isEmpty()) {
            return InferenceImportDependence(InferenceImportDependenceState.LocalOnly)
        }
        if (!reasoning.metadata.importClosureComplete) {
            return InferenceImportDependence(InferenceImportDependenceState.Unknown)
        }
        val referenced = listOf(raw.subject.value, raw.predicate.value, raw.objectValue.value).toSet()
        val contributing = project.symbols
            .filter { it.sourceId in importedSourceIds && it.iri.value in referenced }
            .map { it.sourceId }
            .toMutableSet()
        raw.sourceId?.takeIf { it in importedSourceIds }?.let(contributing::add)
        return if (contributing.isNotEmpty()) {
            InferenceImportDependence(InferenceImportDependenceState.Imported, contributing.sorted().take(20))
        } else {
            InferenceImportDependence(InferenceImportDependenceState.Unknown)
        }
    }

    private fun reason(stageability: InferenceStageability): String = when (stageability) {
        InferenceStageability.Stageable -> "This inferred fact can be staged as an asserted change."
        InferenceStageability.AlreadyAsserted -> "This fact is already asserted in the applied graph."
        InferenceStageability.AlreadyStaged -> "This fact is already present in shared staging."
        InferenceStageability.Stale -> "The reasoning result no longer matches the applied graph."
        InferenceStageability.UnsupportedType -> "This inference has no approved typed-edit conversion."
        InferenceStageability.UnsupportedTerm -> "Only named IRI resources can be materialized."
        InferenceStageability.MissingEntity -> "A referenced ontology entity is unavailable."
        InferenceStageability.InvalidPredicate -> "The predicate is not a known object property."
        InferenceStageability.NoWritableSource -> "No writable local source declares the assertion subject."
        InferenceStageability.AmbiguousSource -> "Choose one of the writable local sources that declares the subject."
        InferenceStageability.ImportDependencyUnsafe -> "Imported knowledge may contribute, but that dependence cannot be proven safely."
    }

    private fun lengthPrefixed(vararg components: String): ByteArray {
        val bytes = ArrayList<Byte>()
        components.forEach { component ->
            val componentBytes = component.toByteArray(StandardCharsets.UTF_8)
            "${componentBytes.size}:".toByteArray(StandardCharsets.UTF_8).forEach(bytes::add)
            componentBytes.forEach(bytes::add)
        }
        return bytes.toByteArray()
    }

    private fun digest(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(value)
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun canonical(iri: Iri): String = "iri:${iri.value}"

    private data class RawInference(
        val subject: RdfResource,
        val predicate: Iri,
        val objectValue: RdfResource,
        val kind: InferenceMaterializationKind,
        val sourceId: String?,
    ) {
        fun analysis(
            stageability: InferenceStageability,
            reason: String,
            candidate: InferenceMaterializationCandidate? = null,
            edit: TypedOntologyEdit? = null,
            triple: GraphTriple? = null,
        ): InferenceMaterializationAnalysis = InferenceMaterializationAnalysis(
            kind = kind,
            subject = subject,
            predicate = predicate,
            objectValue = objectValue,
            stageability = stageability,
            reason = reason,
            candidate = candidate,
            edit = edit,
            triple = triple,
        )
    }

    private companion object {
        private const val SEMANTIC_KEY_PREFIX = "entio-semantic-fact-v1:"
        private const val FACT_ID_PREFIX = "entio-reasoning-fact-v1:"
        private val RDF_TYPE = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
        private val RDFS_SUBCLASS_OF = Iri("http://www.w3.org/2000/01/rdf-schema#subClassOf")
        private val OWL_OBJECT_PROPERTY = Iri("http://www.w3.org/2002/07/owl#ObjectProperty")
    }
}
