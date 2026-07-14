package com.entio.semantic

import com.entio.core.AnnotationStatement
import com.entio.core.AnnotationValue
import com.entio.core.GraphState
import com.entio.core.LoadedOntology
import com.entio.core.RdfResource

/** Explicit annotation statements separated from structural graph statements. */
public data class ExplicitAnnotationSet(
    public val recognized: Map<com.entio.core.Iri, List<AnnotationStatement>>,
    public val general: List<AnnotationStatement>,
) {
    public val all: List<AnnotationStatement>
        get() = recognized.values.flatten() + general
}

/** Extracts explicit annotation statements without inference or metadata policy. */
public class ExplicitAnnotationExtractor {
    public fun extract(ontology: LoadedOntology, subject: RdfResource): ExplicitAnnotationSet =
        extract(ontology.graph, ontology.source.id, subject)

    public fun extract(graph: GraphState, sourceId: String, subject: RdfResource): ExplicitAnnotationSet {
        val statements = graph.triples
            .asSequence()
            .filter { it.subjectResource == subject }
            .filterNot { AnnotationVocabulary.isStructural(it.predicate) }
            .map { triple ->
                AnnotationStatement(
                    subject = triple.subjectResource,
                    property = triple.predicate,
                    value = AnnotationValue.fromTerm(triple.objectTerm),
                    sourceId = sourceId,
                )
            }
            .sortedBy { it.stableKey }
            .toList()

        val recognized = statements
            .filter { AnnotationVocabulary.isRecognized(it.property) }
            .groupBy { it.property }
            .toSortedMap(compareBy { it.value })

        val general = statements.filterNot { AnnotationVocabulary.isRecognized(it.property) }

        return ExplicitAnnotationSet(
            recognized = recognized,
            general = general,
        )
    }
}
