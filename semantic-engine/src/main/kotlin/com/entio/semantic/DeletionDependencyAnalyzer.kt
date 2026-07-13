package com.entio.semantic

import com.entio.core.DeletionDependency
import com.entio.core.DeletionDependencyKind
import com.entio.core.DeletionPlan
import com.entio.core.DeletionPlanStatus
import com.entio.core.EntityCandidate
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.LoadedOntology
import com.entio.core.RdfResource

/** Finds explicit outgoing definitions and incoming references for a supported deletion target. */
public class DeletionDependencyAnalyzer {
    public fun analyze(
        ontology: LoadedOntology,
        target: EntityCandidate,
        selectedDependentStatements: Set<GraphTriple> = emptySet(),
    ): DeletionPlan {
        if (target.sourceId != ontology.source.id) {
            return DeletionPlan(
                target = target,
                status = DeletionPlanStatus.Invalid,
            )
        }

        val targetIri = target.iri
        val direct = ontology.graph.triples
            .filter { it.subjectResource == targetIri }
            .sortedWith(TRIPLE_COMPARATOR)
            .map { triple ->
                DeletionDependency(
                    statement = triple,
                    kind = DeletionDependencyKind.DirectDefinition,
                    sourceId = ontology.source.id,
                )
            }
        val dependent = ontology.graph.triples
            .filter { it.subjectResource != targetIri && it.objectTerm == targetIri }
            .sortedWith(TRIPLE_COMPARATOR)
            .map { triple ->
                DeletionDependency(
                    statement = triple,
                    kind = DeletionDependencyKind.IncomingReference,
                    sourceId = ontology.source.id,
                    selectedForRemoval = triple in selectedDependentStatements,
                )
            }
        val status = when {
            direct.isEmpty() -> DeletionPlanStatus.Invalid
            dependent.isEmpty() -> DeletionPlanStatus.Safe
            dependent.all { it.selectedForRemoval } -> DeletionPlanStatus.Safe
            else -> DeletionPlanStatus.RequiresExplicitDependencies
        }

        return DeletionPlan(
            target = target,
            directStatements = direct,
            dependentStatements = dependent,
            status = status,
        )
    }

    private companion object {
        private val TRIPLE_COMPARATOR = compareBy<GraphTriple>(
            { it.subjectResource.value },
            { it.predicate.value },
            { it.objectTerm.sortKey() },
        )

        private fun com.entio.core.RdfTerm.sortKey(): String =
            when (this) {
                is RdfResource -> "resource:$value"
                is com.entio.core.RdfLiteral -> "literal:$lexicalForm:${datatypeIri?.value.orEmpty()}:${languageTag.orEmpty()}"
            }
    }
}
