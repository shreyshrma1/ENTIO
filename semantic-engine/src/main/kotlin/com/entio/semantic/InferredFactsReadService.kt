package com.entio.semantic

import com.entio.core.EntioProject
import com.entio.core.FactOrigin
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.InferenceMaterializationFact
import com.entio.core.InferenceMaterializationKind
import com.entio.core.InferredFactPlacement
import com.entio.core.InferredFactsOverlay
import com.entio.core.InferredGraphState
import com.entio.core.InferredReadFact
import com.entio.core.InferredReadKind
import com.entio.core.InferredReadState
import com.entio.core.Iri
import com.entio.core.ReasoningResult
import com.entio.core.ReasoningRunStatus
import com.entio.core.RdfResource
import com.entio.core.SemanticFactKey
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Projects complete reasoning results into bounded, read-only Explore facts. */
public class InferredFactsReadService(
    private val materializationService: InferenceMaterializationService = InferenceMaterializationService(),
) {
    public fun project(
        project: EntioProject,
        assertedGraph: GraphState,
        reasoningResultId: String,
        reasoning: ReasoningResult,
        graphState: InferredGraphState,
        proposalFingerprint: String? = null,
        limit: Int = 100,
    ): InferredFactsOverlay {
        require(reasoningResultId.isNotBlank()) { "reasoning-result-id-required" }
        require(limit in 1..100) { "inferred-read-limit-must-be-between-1-and-100" }
        require(graphState == InferredGraphState.Proposal || proposalFingerprint == null) {
            "applied-inferred-read-must-not-carry-proposal-fingerprint"
        }
        require(graphState != InferredGraphState.Proposal || !proposalFingerprint.isNullOrBlank()) {
            "proposal-inferred-read-requires-proposal-fingerprint"
        }
        if (reasoning.metadata.status != ReasoningRunStatus.Completed || !reasoning.metadata.importClosureComplete) {
            return InferredFactsOverlay(
                graphState = graphState,
                state = InferredReadState.Failed,
                proposalFingerprint = proposalFingerprint,
                message = "Reasoning did not produce a complete current result.",
            )
        }

        val asserted = assertedGraph.triples
        val known = knownNamedEntities(assertedGraph)
        val raw = buildList {
            reasoning.classRelationships.filter { it.origin == FactOrigin.Inferred }.forEach { relationship ->
                add(
                    RawFact(
                        relationship.subject,
                        RDFS_SUBCLASS_OF,
                        relationship.objectClass,
                        InferredReadKind.SubclassRelationship,
                        setOf(
                            InferredFactPlacement.ClassSuperclasses,
                            InferredFactPlacement.ClassSubclasses,
                            InferredFactPlacement.HierarchyChildren,
                        ),
                        relationship.sourceId,
                    ),
                )
            }
            reasoning.individualTypes.filter { it.origin == FactOrigin.Inferred }.forEach { type ->
                add(
                    RawFact(
                        type.individual,
                        RDF_TYPE,
                        type.type,
                        InferredReadKind.IndividualType,
                        setOf(
                            InferredFactPlacement.IndividualTypes,
                            InferredFactPlacement.ClassTypedIndividuals,
                            InferredFactPlacement.OutlineDirectType,
                        ),
                        type.sourceId,
                    ),
                )
            }
            reasoning.propertyRelationships.filter { it.origin == FactOrigin.Inferred }.forEach { relationship ->
                add(
                    RawFact(
                        relationship.subject,
                        relationship.predicate,
                        relationship.objectResource,
                        InferredReadKind.ObjectPropertyAssertion,
                        setOf(
                            InferredFactPlacement.IndividualOutgoingRelationships,
                            InferredFactPlacement.IndividualIncomingRelationships,
                            InferredFactPlacement.PropertyUsage,
                        ),
                        relationship.sourceId,
                    ),
                )
            }
            addAll(effectivePropertyFacts(assertedGraph, reasoning))
        }
        val facts = raw.mapNotNull { candidate ->
            candidate.toFact(
                project = project,
                known = known,
                asserted = asserted,
                reasoningResultId = reasoningResultId,
                reasoning = reasoning,
                graphState = graphState,
                proposalFingerprint = proposalFingerprint,
            )
        }.distinctBy(InferredReadFact::semanticFactKey).sortedWith(factComparator)

        return InferredFactsOverlay(
            graphState = graphState,
            state = InferredReadState.Current,
            facts = facts.take(limit),
            totalFactCount = facts.size,
            truncated = facts.size > limit,
            graphFingerprint = reasoning.metadata.fingerprints.graphFingerprint,
            proposalFingerprint = proposalFingerprint,
        )
    }

    private fun effectivePropertyFacts(
        assertedGraph: GraphState,
        reasoning: ReasoningResult,
    ): List<RawFact> {
        val inferredSuperclasses = reasoning.classRelationships
            .filter { it.origin == FactOrigin.Inferred }
            .mapNotNull { relationship ->
                val child = relationship.subject as? Iri ?: return@mapNotNull null
                val parent = relationship.objectClass as? Iri ?: return@mapNotNull null
                child to parent
            }
            .groupBy({ it.first }, { it.second })
        return assertedGraph.triples.flatMap { triple ->
            if (triple.predicate != RDFS_DOMAIN && triple.predicate != RDFS_RANGE) return@flatMap emptyList()
            val property = triple.subjectResource as? Iri ?: return@flatMap emptyList()
            val assertedClass = triple.objectTerm as? Iri ?: return@flatMap emptyList()
            inferredSuperclasses[assertedClass].orEmpty().map { inferredClass ->
                val domain = triple.predicate == RDFS_DOMAIN
                RawFact(
                    property,
                    triple.predicate,
                    inferredClass,
                    if (domain) InferredReadKind.EffectiveDomain else InferredReadKind.EffectiveRange,
                    setOf(
                        if (domain) InferredFactPlacement.PropertyDomains else InferredFactPlacement.PropertyRanges,
                        InferredFactPlacement.ClassRelatedProperties,
                    ),
                    sourceId = null,
                )
            }
        }
    }

    private fun RawFact.toFact(
        project: EntioProject,
        known: Set<Iri>,
        asserted: Set<GraphTriple>,
        reasoningResultId: String,
        reasoning: ReasoningResult,
        graphState: InferredGraphState,
        proposalFingerprint: String?,
    ): InferredReadFact? {
        val namedSubject = subject as? Iri ?: return null
        val namedObject = objectValue as? Iri ?: return null
        if (namedSubject !in known || namedObject !in known) return null
        if (kind == InferredReadKind.ObjectPropertyAssertion && predicate !in knownObjectProperties(project, asserted)) return null
        if (GraphTriple(namedSubject, predicate, namedObject) in asserted) return null
        val semanticKey = materializationKind(kind)?.let { materializationKind ->
            materializationService.semanticFactKey(
                InferenceMaterializationFact(materializationKind, namedSubject, predicate, namedObject),
            )
        } ?: readOnlySemanticKey(kind, namedSubject, predicate, namedObject)
        return InferredReadFact(
            semanticFactKey = semanticKey,
            subject = namedSubject,
            predicate = predicate,
            objectValue = namedObject,
            kind = kind,
            placements = placements,
            graphState = graphState,
            reasoningResultId = reasoningResultId,
            graphFingerprint = reasoning.metadata.fingerprints.graphFingerprint,
            proposalFingerprint = proposalFingerprint,
            sourceId = sourceId,
        )
    }

    private fun knownNamedEntities(graph: GraphState): Set<Iri> = graph.triples.flatMapTo(mutableSetOf()) { triple ->
        listOfNotNull(triple.subjectResource as? Iri, triple.objectTerm as? Iri)
    }

    private fun knownObjectProperties(project: EntioProject, asserted: Set<GraphTriple>): Set<Iri> =
        (project.graph.triples + asserted).mapNotNullTo(mutableSetOf()) { triple ->
            (triple.subjectResource as? Iri)?.takeIf {
                triple.predicate == RDF_TYPE && triple.objectTerm == OWL_OBJECT_PROPERTY
            }
        }

    private fun materializationKind(kind: InferredReadKind): InferenceMaterializationKind? = when (kind) {
        InferredReadKind.SubclassRelationship -> InferenceMaterializationKind.SubclassRelationship
        InferredReadKind.IndividualType -> InferenceMaterializationKind.IndividualType
        InferredReadKind.ObjectPropertyAssertion -> InferenceMaterializationKind.ObjectPropertyAssertion
        InferredReadKind.EffectiveDomain,
        InferredReadKind.EffectiveRange,
        -> null
    }

    private fun readOnlySemanticKey(kind: InferredReadKind, subject: Iri, predicate: Iri, objectValue: Iri): SemanticFactKey =
        SemanticFactKey(
            "entio-inferred-read-v1:${digest(
                lengthPrefixed(kind.name, canonical(subject), predicate.value, canonical(objectValue)),
            )}",
        )

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

    private data class RawFact(
        val subject: RdfResource,
        val predicate: Iri,
        val objectValue: RdfResource,
        val kind: InferredReadKind,
        val placements: Set<InferredFactPlacement>,
        val sourceId: String?,
    )

    private companion object {
        private val RDF_TYPE = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
        private val RDFS_SUBCLASS_OF = Iri("http://www.w3.org/2000/01/rdf-schema#subClassOf")
        private val RDFS_DOMAIN = Iri("http://www.w3.org/2000/01/rdf-schema#domain")
        private val RDFS_RANGE = Iri("http://www.w3.org/2000/01/rdf-schema#range")
        private val OWL_OBJECT_PROPERTY = Iri("http://www.w3.org/2002/07/owl#ObjectProperty")
        private val factComparator = compareBy<InferredReadFact> { it.kind.ordinal }
            .thenBy { it.subject.value }
            .thenBy { it.predicate.value }
            .thenBy { it.objectValue.value }
            .thenBy { it.semanticFactKey.value }
    }
}
