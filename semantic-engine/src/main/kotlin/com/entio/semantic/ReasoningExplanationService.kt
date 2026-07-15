package com.entio.semantic

import com.entio.core.ConsistencyStatus
import com.entio.core.EntioResult
import com.entio.core.FactOrigin
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.ReasoningExplanation
import com.entio.core.ReasoningExplanationKind
import com.entio.core.ReasoningResult
import com.entio.core.RdfResource
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity

public sealed interface ReasoningExplanationTarget {
    public data class ClassRelationship(
        public val subject: RdfResource,
        public val objectClass: RdfResource,
    ) : ReasoningExplanationTarget

    public data class IndividualType(
        public val individual: RdfResource,
        public val type: RdfResource,
    ) : ReasoningExplanationTarget

    public data class PropertyRelationship(
        public val subject: RdfResource,
        public val predicate: Iri,
        public val objectResource: RdfResource,
    ) : ReasoningExplanationTarget

    public data class Entity(
        public val resource: RdfResource,
    ) : ReasoningExplanationTarget
}

public class ReasoningExplanationService {
    public fun explain(
        target: ReasoningExplanationTarget,
        result: ReasoningResult,
        graph: GraphState,
    ): EntioResult<ReasoningExplanation> = when (target) {
        is ReasoningExplanationTarget.ClassRelationship -> explainClassRelationship(target, result, graph)
        is ReasoningExplanationTarget.IndividualType -> explainIndividualType(target, result, graph)
        is ReasoningExplanationTarget.PropertyRelationship -> explainPropertyRelationship(target, result, graph)
        is ReasoningExplanationTarget.Entity -> explainEntity(target.resource, result, graph)
    }

    private fun explainClassRelationship(
        target: ReasoningExplanationTarget.ClassRelationship,
        result: ReasoningResult,
        graph: GraphState,
    ): EntioResult<ReasoningExplanation> {
        val relationship = result.classRelationships.firstOrNull {
            it.subject == target.subject && it.objectClass == target.objectClass
        } ?: return invalid("The selected class relationship is not present in the reasoning result.")
        val path = subclassPath(graph, target.subject, target.objectClass)
        return success(
            ReasoningExplanation(
                target = target.subject,
                kind = ReasoningExplanationKind.Inference,
                rule = if (path.isEmpty()) "Reasoner-backed class relationship" else "Asserted subclass path",
                assertedEvidence = path,
                complete = relationship.origin == FactOrigin.Asserted || path.isNotEmpty(),
                caveat = if (path.isEmpty()) {
                    "The selected relationship is reasoner-backed, but this boundary cannot provide a complete minimal justification."
                } else {
                    null
                },
            ),
        )
    }

    private fun explainIndividualType(
        target: ReasoningExplanationTarget.IndividualType,
        result: ReasoningResult,
        graph: GraphState,
    ): EntioResult<ReasoningExplanation> {
        val relationship = result.individualTypes.firstOrNull {
            it.individual == target.individual && it.type == target.type
        } ?: return invalid("The selected individual type is not present in the reasoning result.")
        val assertedType = graph.triples.firstOrNull {
            it.subjectResource == target.individual &&
                it.predicate.value == RDF_TYPE
        }
        val sourceClass = assertedType?.objectTerm as? RdfResource
        val path = sourceClass?.let { subclassPath(graph, it, target.type) }.orEmpty()
        val evidence = listOfNotNull(assertedType) + path
        return success(
            ReasoningExplanation(
                target = target.individual,
                kind = ReasoningExplanationKind.Inference,
                rule = if (path.isEmpty()) "Reasoner-backed individual type" else "Asserted individual type plus subclass path",
                assertedEvidence = evidence.distinct(),
                complete = relationship.origin == FactOrigin.Asserted || evidence.isNotEmpty(),
                caveat = if (evidence.isEmpty()) {
                    "The selected type is reasoner-backed, but this boundary cannot provide a complete minimal justification."
                } else {
                    null
                },
            ),
        )
    }

    private fun explainPropertyRelationship(
        target: ReasoningExplanationTarget.PropertyRelationship,
        result: ReasoningResult,
        graph: GraphState,
    ): EntioResult<ReasoningExplanation> {
        val relationship = result.propertyRelationships.firstOrNull {
            it.subject == target.subject &&
                it.predicate == target.predicate &&
                it.objectResource == target.objectResource
        } ?: return invalid("The selected property relationship is not present in the reasoning result.")
        val inverseEvidence = inverseEvidence(target, graph)
        val transitiveEvidence = transitiveEvidence(target, graph)
        val evidence = when {
            inverseEvidence.isNotEmpty() -> inverseEvidence
            transitiveEvidence.isNotEmpty() -> transitiveEvidence
            else -> emptyList()
        }
        return success(
            ReasoningExplanation(
                target = target.subject,
                kind = ReasoningExplanationKind.Inference,
                rule = when {
                    inverseEvidence.isNotEmpty() -> "Asserted inverse property relationship"
                    transitiveEvidence.isNotEmpty() -> "Asserted transitive property path"
                    else -> "Reasoner-backed property relationship"
                },
                assertedEvidence = evidence,
                complete = relationship.origin == FactOrigin.Asserted || evidence.isNotEmpty(),
                caveat = if (evidence.isEmpty()) {
                    "The selected relationship is reasoner-backed, but this boundary cannot provide a complete minimal justification."
                } else {
                    null
                },
            ),
        )
    }

    private fun explainEntity(
        resource: RdfResource,
        result: ReasoningResult,
        graph: GraphState,
    ): EntioResult<ReasoningExplanation> {
        val kind = when {
            result.consistency == ConsistencyStatus.Inconsistent -> ReasoningExplanationKind.Inconsistency
            result.unsatisfiableClasses.contains(resource) -> ReasoningExplanationKind.UnsatisfiableClass
            else -> return invalid("The selected entity has no inconsistency or unsatisfiable-class finding.")
        }
        val evidence = graph.triples
            .filter { it.subjectResource == resource || it.objectTerm == resource }
            .sortedWith(graphTripleComparator)
        return success(
            ReasoningExplanation(
                target = resource,
                kind = kind,
                rule = if (kind == ReasoningExplanationKind.Inconsistency) {
                    "Reasoner reported ontology inconsistency"
                } else {
                    "Reasoner reported an unsatisfiable class"
                },
                assertedEvidence = evidence,
                complete = false,
                caveat = "HermiT evidence is available, but this boundary does not promise a complete minimal justification.",
            ),
        )
    }

    private fun subclassPath(
        graph: GraphState,
        start: RdfResource,
        target: RdfResource,
    ): List<GraphTriple> {
        if (start == target) return emptyList()
        val edges = graph.triples
            .filter { it.predicate.value == RDFS_SUBCLASS }
            .sortedWith(graphTripleComparator)
        val queue = ArrayDeque<Pair<RdfResource, List<GraphTriple>>>()
        val visited = mutableSetOf<RdfResource>()
        queue += start to emptyList()
        while (queue.isNotEmpty()) {
            val (current, path) = queue.removeFirst()
            if (!visited.add(current)) continue
            edges.filter { it.subjectResource == current }.forEach { edge ->
                val next = edge.objectTerm as? RdfResource ?: return@forEach
                val nextPath = path + edge
                if (next == target) return nextPath
                queue += next to nextPath
            }
        }
        return emptyList()
    }

    private fun inverseEvidence(
        target: ReasoningExplanationTarget.PropertyRelationship,
        graph: GraphState,
    ): List<GraphTriple> {
        val inverse = graph.triples
            .filter { it.predicate.value == OWL_INVERSE }
            .sortedWith(graphTripleComparator)
        return inverse.firstNotNullOfOrNull { declaration ->
            val left = declaration.subjectResource as? Iri ?: return@firstNotNullOfOrNull null
            val right = declaration.objectTerm as? Iri ?: return@firstNotNullOfOrNull null
            val inversePredicate = when {
                left == target.predicate -> right
                right == target.predicate -> left
                else -> return@firstNotNullOfOrNull null
            }
            val asserted = graph.triples.firstOrNull {
                it.subjectResource == target.objectResource &&
                    it.predicate == inversePredicate &&
                    it.objectTerm == target.subject
            } ?: return@firstNotNullOfOrNull null
            listOf(declaration, asserted)
        } ?: emptyList()
    }

    private fun transitiveEvidence(
        target: ReasoningExplanationTarget.PropertyRelationship,
        graph: GraphState,
    ): List<GraphTriple> {
        val edges = graph.triples
            .filter { it.predicate == target.predicate }
            .sortedWith(graphTripleComparator)
        return edges.firstNotNullOfOrNull { first ->
            val middle = first.objectTerm as? RdfResource ?: return@firstNotNullOfOrNull null
            val second = edges.firstOrNull {
                it.subjectResource == middle && it.objectTerm == target.objectResource
            } ?: return@firstNotNullOfOrNull null
            if (first.subjectResource == target.subject) listOf(first, second) else null
        } ?: emptyList()
    }

    private fun success(explanation: ReasoningExplanation): EntioResult<ReasoningExplanation> =
        EntioResult.Success(explanation)

    private fun invalid(message: String): EntioResult<Nothing> = EntioResult.Failure(
        message = message,
        issues = listOf(
            ValidationIssue(
                severity = ValidationSeverity.Error,
                code = "reasoning-explanation-invalid-target",
                message = message,
            ),
        ),
    )

    private companion object {
        private const val RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
        private const val RDFS_SUBCLASS = "http://www.w3.org/2000/01/rdf-schema#subClassOf"
        private const val OWL_INVERSE = "http://www.w3.org/2002/07/owl#inverseOf"
        private val graphTripleComparator = compareBy<GraphTriple>(
            { it.subjectResource.value },
            { it.predicate.value },
            { it.objectValue },
        )
    }
}
