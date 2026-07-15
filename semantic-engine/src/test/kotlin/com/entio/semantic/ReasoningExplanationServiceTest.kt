package com.entio.semantic

import com.entio.core.ConsistencyStatus
import com.entio.core.EntioResult
import com.entio.core.Iri
import com.entio.core.OntologyFormat
import com.entio.core.ReasoningExplanationKind
import com.entio.core.ReasoningExplanation
import com.entio.core.ReasoningResult
import com.entio.core.ResolvedOntologySource
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReasoningExplanationServiceTest {
    private val parser = OntologyParser()
    private val reasoningService = ReasoningService()
    private val explanationService = ReasoningExplanationService()

    @Test
    fun explainsClassAndIndividualTypePathsWithStableEvidence(): Unit {
        val graph = parse(
            """
            @prefix ex: <https://example.com/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            ex:Animal a owl:Class .
            ex:Mammal a owl:Class ; rdfs:subClassOf ex:Animal .
            ex:Dog a owl:Class ; rdfs:subClassOf ex:Mammal .
            ex:Fido a ex:Dog .
            """.trimIndent(),
        ).graph
        val result = assertIs<EntioResult.Success<ReasoningResult>>(reasoningService.reason(graph)).value

        val classTarget = ReasoningExplanationTarget.ClassRelationship(
            subject = Iri("https://example.com/Dog"),
            objectClass = Iri("https://example.com/Animal"),
        )
        val first = assertIs<EntioResult.Success<ReasoningExplanation>>(explanationService.explain(classTarget, result, graph)).value
        val second = assertIs<EntioResult.Success<ReasoningExplanation>>(explanationService.explain(classTarget, result, graph)).value
        assertEquals(first, second)
        assertEquals("Asserted subclass path", first.rule)
        assertTrue(first.assertedEvidence.isNotEmpty())

        val typeTarget = ReasoningExplanationTarget.IndividualType(
            individual = Iri("https://example.com/Fido"),
            type = Iri("https://example.com/Animal"),
        )
        val typeExplanation = assertIs<EntioResult.Success<ReasoningExplanation>>(
            explanationService.explain(typeTarget, result, graph),
        ).value
        assertEquals("Asserted individual type plus subclass path", typeExplanation.rule)
        assertTrue(typeExplanation.assertedEvidence.isNotEmpty())
    }

    @Test
    fun explainsInverseAndTransitivePropertyConsequences(): Unit {
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
        val result = assertIs<EntioResult.Success<ReasoningResult>>(reasoningService.reason(graph)).value

        val inverse = assertIs<EntioResult.Success<ReasoningExplanation>>(
            explanationService.explain(
                ReasoningExplanationTarget.PropertyRelationship(
                    Iri("https://example.com/bob"),
                    Iri("https://example.com/childOf"),
                    Iri("https://example.com/alice"),
                ),
                result,
                graph,
            ),
        ).value
        assertEquals("Asserted inverse property relationship", inverse.rule)
        assertEquals(2, inverse.assertedEvidence.size)

        val transitive = assertIs<EntioResult.Success<ReasoningExplanation>>(
            explanationService.explain(
                ReasoningExplanationTarget.PropertyRelationship(
                    Iri("https://example.com/alice"),
                    Iri("https://example.com/ancestorOf"),
                    Iri("https://example.com/carol"),
                ),
                result,
                graph,
            ),
        ).value
        assertEquals("Asserted transitive property path", transitive.rule)
        assertEquals(2, transitive.assertedEvidence.size)
    }

    @Test
    fun explainsInconsistencyAndUnsatisfiableClassWithFallbackCaveat(): Unit {
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
        val result = assertIs<EntioResult.Success<ReasoningResult>>(reasoningService.reason(graph)).value
        val unsatisfiable = assertIs<EntioResult.Success<ReasoningExplanation>>(
            explanationService.explain(
                ReasoningExplanationTarget.Entity(Iri("https://example.com/Impossible")),
                result,
                graph,
            ),
        ).value
        assertEquals(ReasoningExplanationKind.UnsatisfiableClass, unsatisfiable.kind)
        assertTrue(unsatisfiable.caveat.orEmpty().contains("complete minimal"))

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
        val inconsistent = assertIs<EntioResult.Success<ReasoningResult>>(reasoningService.reason(inconsistentGraph)).value
        assertEquals(ConsistencyStatus.Inconsistent, inconsistent.consistency)
        val explanation = assertIs<EntioResult.Success<ReasoningExplanation>>(
            explanationService.explain(
                ReasoningExplanationTarget.Entity(Iri("https://example.com/Someone")),
                inconsistent,
                inconsistentGraph,
            ),
        ).value
        assertEquals(ReasoningExplanationKind.Inconsistency, explanation.kind)
        assertTrue(explanation.assertedEvidence.isNotEmpty())
    }

    private fun parse(content: String): com.entio.core.LoadedOntology {
        val path = Files.createTempFile("entio-explanation", ".ttl")
        path.writeText(content)
        return assertIs<EntioResult.Success<com.entio.core.LoadedOntology>>(
            parser.parse(ResolvedOntologySource("simple", path, OntologyFormat.Turtle)),
        ).value
    }
}
