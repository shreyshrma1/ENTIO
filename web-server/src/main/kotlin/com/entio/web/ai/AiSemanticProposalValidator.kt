package com.entio.web.ai

import com.entio.core.GraphState
import com.entio.core.Iri
import com.entio.web.contract.WebAiProposalEdit

/**
 * Conservative semantic guardrails for proposal output. These checks only
 * reject an unambiguous RDFS role error and leave broader ontology judgment to
 * deterministic validation and human review.
 */
internal class AiSemanticProposalValidator {
    fun validate(graph: GraphState, edits: List<WebAiProposalEdit>): List<String> {
        val additions = edits.filter { it.operation == "add" }.mapNotNull { it.toGraphTriple() }
        val removals = edits.filter { it.operation == "remove" }.mapNotNull { it.toGraphTriple() }.toSet()
        val planned = (graph.triples + additions).minus(removals)
        val classResources = planned.filter { it.predicate == RDF_TYPE && (it.objectTerm as? com.entio.core.RdfResource)?.value in CLASS_TYPES }
            .map { it.subjectResource.value }
            .toSet()
        val propertyResources = planned.filter { it.predicate == RDF_TYPE && (it.objectTerm as? com.entio.core.RdfResource)?.value in PROPERTY_TYPES }
            .map { it.subjectResource.value }
            .toSet()
        return edits.filter { it.operation == "add" }
            .mapNotNull { edit ->
                val triple = edit.toGraphTriple() ?: return@mapNotNull null
                if (triple.predicate != RDFS_DOMAIN && triple.predicate != RDFS_RANGE) return@mapNotNull null
                if (triple.subjectResource.value !in classResources || triple.subjectResource.value in propertyResources) return@mapNotNull null
                "Deterministic semantic validation error for edit '${edit.id}' in source '${edit.sourceId}': " +
                    "RDFS ${if (triple.predicate == RDFS_DOMAIN) "domain" else "range"} axioms require a property subject; " +
                    "'${triple.subjectResource.value}' is declared as a class. Violation: ${formatTriple(triple)}. " +
                    "Repair action: remove the domain/range edit or change its subject to the declared property IRI."
            }
            .distinct()
    }

    private companion object {
        private val RDF_TYPE = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
        private val RDFS_DOMAIN = Iri("http://www.w3.org/2000/01/rdf-schema#domain")
        private val RDFS_RANGE = Iri("http://www.w3.org/2000/01/rdf-schema#range")
        private val CLASS_TYPES = setOf("http://www.w3.org/2000/01/rdf-schema#Class", "http://www.w3.org/2002/07/owl#Class")
        private val PROPERTY_TYPES = setOf(
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#Property",
            "http://www.w3.org/2002/07/owl#ObjectProperty",
            "http://www.w3.org/2002/07/owl#DatatypeProperty",
            "http://www.w3.org/2002/07/owl#AnnotationProperty",
        )

        private fun formatTriple(triple: com.entio.core.GraphTriple): String =
            "<${triple.subjectResource.value}> <${triple.predicate.value}> ${formatTerm(triple.objectTerm)} ."

        private fun formatTerm(term: com.entio.core.RdfTerm): String = when (term) {
            is com.entio.core.RdfResource -> "<${term.value}>"
            is com.entio.core.RdfLiteral -> "\"${term.lexicalForm}\""
        }
    }
}
