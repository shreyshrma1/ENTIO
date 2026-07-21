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
        return additions.filter { it.predicate == RDFS_DOMAIN || it.predicate == RDFS_RANGE }
            .filter { it.subjectResource.value in classResources && it.subjectResource.value !in propertyResources }
            .map { triple ->
                "RDFS ${if (triple.predicate == RDFS_DOMAIN) "domain" else "range"} axioms require a property subject; '${triple.subjectResource.value}' is declared as a class."
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
    }
}
