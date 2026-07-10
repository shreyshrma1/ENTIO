package com.entio.semantic

import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.LoadedOntology
import com.entio.core.LoadedSymbol
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.SymbolKind

public class SymbolExtractor {
    public fun extractSymbols(ontology: LoadedOntology): List<LoadedSymbol> {
        val labelsByIri = ontology.graph.triples
            .mapNotNull { it.toLabelCandidate() }
            .groupBy { it.iri.value }
            .mapValues { (_, labels) -> labels.map { it.label }.minOrNull() }

        return ontology.graph.triples
            .mapNotNull { it.toSymbolCandidate() }
            .groupBy { it.iri.value }
            .map { (iri, candidates) ->
                LoadedSymbol(
                    iri = Iri(iri),
                    label = labelsByIri[iri],
                    kind = candidates.map { it.kind }.minBy { it.priority },
                    sourceId = ontology.source.id,
                )
            }
            .sortedBy { it.iri.value }
    }

    private fun GraphTriple.toSymbolCandidate(): SymbolCandidate? {
        if (predicate.value != RDF_TYPE) {
            return null
        }

        val subjectIri = subjectResource as? Iri ?: return null
        val typeResource = objectTerm as? RdfResource ?: return null

        val kind = when (typeResource.value) {
            OWL_CLASS,
            RDFS_CLASS,
            -> SymbolKind.Class

            RDF_PROPERTY,
            OWL_OBJECT_PROPERTY,
            OWL_DATATYPE_PROPERTY,
            OWL_ANNOTATION_PROPERTY,
            -> SymbolKind.Property

            SH_NODE_SHAPE,
            SH_PROPERTY_SHAPE,
            -> SymbolKind.Shape

            else -> SymbolKind.Individual
        }

        return SymbolCandidate(
            iri = subjectIri,
            kind = kind,
        )
    }

    private fun GraphTriple.toLabelCandidate(): LabelCandidate? {
        if (predicate.value != RDFS_LABEL) {
            return null
        }

        val subjectIri = subjectResource as? Iri ?: return null
        val literal = objectTerm as? RdfLiteral ?: return null

        return LabelCandidate(
            iri = subjectIri,
            label = literal.lexicalForm,
        )
    }

    private data class SymbolCandidate(
        val iri: Iri,
        val kind: SymbolKind,
    )

    private data class LabelCandidate(
        val iri: Iri,
        val label: String,
    )

    private companion object {
        private const val RDF_TYPE: String = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
        private const val RDF_PROPERTY: String = "http://www.w3.org/1999/02/22-rdf-syntax-ns#Property"
        private const val RDFS_CLASS: String = "http://www.w3.org/2000/01/rdf-schema#Class"
        private const val RDFS_LABEL: String = "http://www.w3.org/2000/01/rdf-schema#label"
        private const val OWL_CLASS: String = "http://www.w3.org/2002/07/owl#Class"
        private const val OWL_OBJECT_PROPERTY: String = "http://www.w3.org/2002/07/owl#ObjectProperty"
        private const val OWL_DATATYPE_PROPERTY: String = "http://www.w3.org/2002/07/owl#DatatypeProperty"
        private const val OWL_ANNOTATION_PROPERTY: String = "http://www.w3.org/2002/07/owl#AnnotationProperty"
        private const val SH_NODE_SHAPE: String = "http://www.w3.org/ns/shacl#NodeShape"
        private const val SH_PROPERTY_SHAPE: String = "http://www.w3.org/ns/shacl#PropertyShape"

        private val SymbolKind.priority: Int
            get() = when (this) {
                SymbolKind.Class -> 0
                SymbolKind.Property -> 1
                SymbolKind.Shape -> 2
                SymbolKind.Individual -> 3
                SymbolKind.NamespaceTerm -> 4
                SymbolKind.Unknown -> 5
            }
    }
}
