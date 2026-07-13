package com.entio.semantic

import com.entio.core.EntioProject
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.RdfTerm
import com.entio.core.SymbolDetails
import com.entio.core.SymbolRelationship
import com.entio.core.SymbolRelationshipDirection
import com.entio.core.SymbolRelationshipKind

public class SymbolRelationshipExtractor {
    public fun extractDetails(project: EntioProject): List<SymbolDetails> {
        val labels = project.graph.triples
            .asSequence()
            .filter { it.predicate == RDFS_LABEL && it.objectTerm is RdfLiteral }
            .groupBy { it.subjectResource }
            .mapValues { (_, triples) ->
                triples.mapNotNull { (it.objectTerm as? RdfLiteral)?.lexicalForm }
                    .sorted()
                    .firstOrNull()
            }
        val sourceIds = sourceIdsByTriple(project)

        return project.symbols.map { symbol ->
            val relationships = project.graph.triples
                .asSequence()
                .flatMap { triple ->
                    val sourceId = sourceIds[triple] ?: symbol.sourceId
                    listOfNotNull(
                        triple.outgoingRelationship(symbol.iri, labels, sourceId),
                        triple.incomingRelationship(symbol.iri, labels, sourceId),
                    ).asSequence()
                }
                .sortedWith(relationshipComparator)
                .toList()
            SymbolDetails(symbol = symbol, relationships = relationships)
        }
    }

    private fun sourceIdsByTriple(project: EntioProject): Map<GraphTriple, String> =
        buildMap {
            project.ontologies.forEach { ontology ->
                ontology.graph.triples.forEach { triple ->
                    putIfAbsent(triple, ontology.source.id)
                }
            }
        }

    private fun GraphTriple.outgoingRelationship(
        symbolIri: Iri,
        labels: Map<RdfResource, String?>,
        sourceId: String,
    ): SymbolRelationship? {
        if (subjectResource != symbolIri || predicate == RDFS_LABEL) return null
        return SymbolRelationship(
            direction = SymbolRelationshipDirection.Outgoing,
            kind = relationshipKind(predicate),
            predicate = predicate,
            predicateLabel = labels[predicate],
            value = objectTerm,
            valueLabel = (objectTerm as? RdfResource)?.let(labels::get),
            sourceId = sourceId,
        )
    }

    private fun GraphTriple.incomingRelationship(
        symbolIri: Iri,
        labels: Map<RdfResource, String?>,
        sourceId: String,
    ): SymbolRelationship? {
        if (predicate == RDFS_LABEL || objectTerm != symbolIri) return null
        return SymbolRelationship(
            direction = SymbolRelationshipDirection.Incoming,
            kind = relationshipKind(predicate),
            predicate = predicate,
            predicateLabel = labels[predicate],
            value = subjectResource,
            valueLabel = labels[subjectResource],
            sourceId = sourceId,
        )
    }

    private companion object {
        private val RDF_TYPE = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
        private val RDFS_LABEL = Iri("http://www.w3.org/2000/01/rdf-schema#label")

        private val relationshipComparator: Comparator<SymbolRelationship> =
            compareBy<SymbolRelationship> { it.direction }
                .thenBy { it.kind }
                .thenBy { it.predicate.value }
                .thenBy { relationshipValue(it.value) }
                .thenBy { it.sourceId }

        private fun relationshipKind(predicate: Iri): SymbolRelationshipKind =
            if (predicate == RDF_TYPE) SymbolRelationshipKind.Type else SymbolRelationshipKind.Property

        private fun relationshipValue(term: RdfTerm): String =
            when (term) {
                is RdfResource -> term.value
                is RdfLiteral -> buildString {
                    append(term.lexicalForm)
                    term.datatypeIri?.let { append("^^").append(it.value) }
                    term.languageTag?.let { append("@").append(it) }
                }
            }
    }
}
