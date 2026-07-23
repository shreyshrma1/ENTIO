package com.entio.semantic

import com.entio.core.ConsistencyStatus
import com.entio.core.EntioResult
import com.entio.core.FactOrigin
import com.entio.core.ImportClosureReport
import com.entio.core.ImportFinding
import com.entio.core.ImportFindingKind
import com.entio.core.Iri
import com.entio.core.OntologyFormat
import com.entio.core.ReasoningRunStatus
import com.entio.core.ResolvedOntologySource
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReasoningServiceTest {
    private val parser = OntologyParser()
    private val service = ReasoningService()

    @Test
    fun infersTransitiveClassHierarchyAndIndividualTypesWithoutChangingAssertedGraph(): Unit {
        val loaded = parse(
            """
            @prefix ex: <https://example.com/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            ex:Animal a owl:Class .
            ex:Mammal a owl:Class ; rdfs:subClassOf ex:Animal .
            ex:Dog a owl:Class ; rdfs:subClassOf ex:Mammal .
            ex:Fido a ex:Dog .
            """.trimIndent(),
        )
        val originalGraph = loaded.graph

        val result = assertIs<EntioResult.Success<*>>(service.reason(originalGraph))
        val reasoning = assertIs<com.entio.core.ReasoningResult>(result.value)

        assertEquals(ReasoningRunStatus.Completed, reasoning.metadata.status)
        assertEquals(ConsistencyStatus.Consistent, reasoning.consistency)
        assertTrue(
            reasoning.classRelationships.any {
                it.subject == Iri("https://example.com/Dog") &&
                    it.objectClass == Iri("https://example.com/Animal") &&
                    it.origin == FactOrigin.Inferred
            },
        )
        assertTrue(
            reasoning.individualTypes.any {
                it.individual == Iri("https://example.com/Fido") &&
                    it.type == Iri("https://example.com/Animal") &&
                    it.origin == FactOrigin.Inferred
            },
        )
        assertTrue(
            reasoning.individualTypes.any {
                it.individual == Iri("https://example.com/Fido") &&
                    it.type == Iri("https://example.com/Dog") &&
                    it.origin == FactOrigin.Asserted
            },
        )
        assertEquals(originalGraph, loaded.graph)
    }

    @Test
    fun reportsConsistencyAndUnsatisfiableClasses(): Unit {
        val graph = parse(
            """
            @prefix ex: <https://example.com/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            ex:Person a owl:Class .
            ex:Robot a owl:Class .
            ex:Person owl:disjointWith ex:Robot .
            ex:Impossible a owl:Class ; rdfs:subClassOf ex:Person, ex:Robot .
            """.trimIndent(),
        ).graph

        val result = service.reason(graph)
        assertTrue(
            result is EntioResult.Success,
            (result as? EntioResult.Failure)?.cause?.stackTraceToString() ?: result.toString(),
        )
        val reasoning = assertIs<com.entio.core.ReasoningResult>((result as EntioResult.Success).value)

        assertEquals(ConsistencyStatus.Consistent, reasoning.consistency)
        assertTrue(reasoning.unsatisfiableClasses.contains(Iri("https://example.com/Impossible")))

        val inconsistentGraph = parse(
            """
            @prefix ex: <https://example.com/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            ex:Person a owl:Class .
            ex:Robot a owl:Class .
            ex:Person owl:disjointWith ex:Robot .
            ex:Someone a ex:Person, ex:Robot .
            """.trimIndent(),
        ).graph
        val inconsistentResult = assertIs<EntioResult.Success<*>>(service.reason(inconsistentGraph))
        val inconsistentReasoning = assertIs<com.entio.core.ReasoningResult>(inconsistentResult.value)
        assertEquals(ConsistencyStatus.Inconsistent, inconsistentReasoning.consistency)
    }

    @Test
    fun reportsEquivalentInverseAndTransitivePropertyConsequencesAsInferred(): Unit {
        val graph = parse(
            """
            @prefix ex: <https://example.com/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            ex:parentOf a owl:ObjectProperty .
            ex:childOf a owl:ObjectProperty .
            ex:parentOf owl:inverseOf ex:childOf .
            ex:ancestorOf a owl:TransitiveProperty .
            ex:alice ex:parentOf ex:bob ; ex:ancestorOf ex:bob .
            ex:bob ex:parentOf ex:carol ; ex:ancestorOf ex:carol .
            """.trimIndent(),
        ).graph

        val result = assertIs<EntioResult.Success<*>>(service.reason(graph))
        val reasoning = assertIs<com.entio.core.ReasoningResult>(result.value)

        assertTrue(
            reasoning.propertyRelationships.any {
                it.subject == Iri("https://example.com/bob") &&
                    it.predicate == Iri("https://example.com/childOf") &&
                    it.objectResource == Iri("https://example.com/alice") &&
                    it.origin == FactOrigin.Inferred
            },
        )
        assertTrue(
            reasoning.propertyRelationships.any {
                it.subject == Iri("https://example.com/alice") &&
                    it.predicate == Iri("https://example.com/ancestorOf") &&
                    it.objectResource == Iri("https://example.com/carol") &&
                    it.origin == FactOrigin.Inferred
            },
        )
        assertEquals(
            listOf("Certain assertions are currently unavailable. Reasoning completeness is not guaranteed."),
            reasoning.warnings,
        )
    }

    @Test
    fun excludesInferencesWhoseClassDependenciesAreNotDeclaredInTheLoadedOntology(): Unit {
        val graph = parse(
            """
            @prefix ex: <https://example.com/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            ex:Beneficiary a owl:Class ; rdfs:subClassOf ex:LocalRole .
            ex:LocalRole a owl:Class ; rdfs:subClassOf <https://external.example/AgentRole> .
            ex:Someone a ex:Beneficiary .
            """.trimIndent(),
        ).graph

        val result = assertIs<EntioResult.Success<*>>(service.reason(graph))
        val reasoning = assertIs<com.entio.core.ReasoningResult>(result.value)

        assertTrue(
            reasoning.classRelationships.none {
                it.origin == FactOrigin.Inferred &&
                    it.objectClass == Iri("https://external.example/AgentRole")
            },
        )
        assertTrue(
            reasoning.individualTypes.none {
                it.origin == FactOrigin.Inferred &&
                    it.type == Iri("https://external.example/AgentRole")
            },
        )
        assertTrue(
            reasoning.classRelationships.any {
                it.origin == FactOrigin.Asserted &&
                    it.subject == Iri("https://example.com/LocalRole") &&
                    it.objectClass == Iri("https://external.example/AgentRole")
            },
        )
    }

    @Test
    fun marksIncompleteImportClosureWithoutClaimingCompleteConsistency(): Unit {
        val graph = parse(
            """
            @prefix ex: <https://example.com/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            ex:Thing a owl:Class .
            """.trimIndent(),
        ).graph
        val importClosure = ImportClosureReport(
            sourceIds = listOf("simple"),
            findings = listOf(
                ImportFinding(
                    importedIri = Iri("https://example.com/missing"),
                    kind = ImportFindingKind.Missing,
                    message = "Missing local import.",
                    sourceId = "simple",
                ),
            ),
            complete = false,
        )

        val result = assertIs<EntioResult.Success<*>>(service.reason(graph, importClosure))
        val reasoning = assertIs<com.entio.core.ReasoningResult>(result.value)

        assertEquals(ReasoningRunStatus.Incomplete, reasoning.metadata.status)
        assertEquals(ConsistencyStatus.Unknown, reasoning.consistency)
        assertTrue(reasoning.warnings.contains("Missing local import."))
    }

    private fun parse(content: String): com.entio.core.LoadedOntology {
        val path = Files.createTempFile("entio-reasoning", ".ttl")
        path.writeText(content)
        val source = ResolvedOntologySource("simple", path, OntologyFormat.Turtle)
        return assertIs<EntioResult.Success<com.entio.core.LoadedOntology>>(parser.parse(source)).value
    }
}
