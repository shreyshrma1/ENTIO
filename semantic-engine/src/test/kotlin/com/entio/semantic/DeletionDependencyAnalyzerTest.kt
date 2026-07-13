package com.entio.semantic

import com.entio.core.DeletionPlanStatus
import com.entio.core.EntioResult
import com.entio.core.EntityCandidate
import com.entio.core.GraphState
import com.entio.core.Iri
import com.entio.core.LoadedOntology
import com.entio.core.LoadedSymbol
import com.entio.core.OntologyFormat
import com.entio.core.ResolvedOntologySource
import com.entio.core.SymbolKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DeletionDependencyAnalyzerTest {
    private val parser = OntologyParser()
    private val analyzer = DeletionDependencyAnalyzer()
    private val generator = DeletionChangeGenerator()
    private val customer = Iri("https://example.com/Customer")
    private val person = Iri("https://example.com/Person")

    @Test
    fun reportsDirectAndIncomingDependencies(): Unit {
        val ontology = ontology(
            """
            @prefix ex: <https://example.com/> .
            @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            ex:Customer a ex:Class ; rdfs:label "Customer" .
            ex:Person a ex:Class ; rdfs:subClassOf ex:Customer .
            """.trimIndent(),
        )
        val target = EntityCandidate(customer, "Customer", SymbolKind.Class, "simple")

        val plan = analyzer.analyze(ontology, target)

        assertEquals(DeletionPlanStatus.RequiresExplicitDependencies, plan.status)
        assertEquals(2, plan.directStatements.size)
        assertEquals(1, plan.dependentStatements.size)
        assertEquals(false, plan.dependentStatements.single().selectedForRemoval)
    }

    @Test
    fun becomesSafeOnlyWhenIncomingReferencesAreSelected(): Unit {
        val ontology = ontology(
            """
            @prefix ex: <https://example.com/> .
            ex:Customer a ex:Class .
            ex:Person a ex:Class ; <http://www.w3.org/2000/01/rdf-schema#subClassOf> ex:Customer .
            """.trimIndent(),
        )
        val target = EntityCandidate(customer, "Customer", SymbolKind.Class, "simple")
        val dependency = ontology.graph.triples.first { it.subjectResource == person }

        val plan = analyzer.analyze(ontology, target, selectedDependentStatements = setOf(dependency))
        val changes = assertIs<EntioResult.Success<com.entio.core.ChangeSet>>(generator.generate(plan)).value

        assertEquals(DeletionPlanStatus.Safe, plan.status)
        assertEquals(2, changes.removals.size)
    }

    @Test
    fun blocksWrongSourceTargets(): Unit {
        val ontology = ontology("""@prefix ex: <https://example.com/> . ex:Customer a ex:Class .""")
        val target = EntityCandidate(customer, "Customer", SymbolKind.Class, "other")

        val plan = analyzer.analyze(ontology, target)

        assertEquals(DeletionPlanStatus.Invalid, plan.status)
        assertIs<EntioResult.Failure>(generator.generate(plan))
    }

    private fun ontology(turtle: String): LoadedOntology {
        val source = SemanticEngineTestFixtures.resolvedSource(turtle)
        return assertIs<EntioResult.Success<LoadedOntology>>(parser.parse(source)).value
    }
}
