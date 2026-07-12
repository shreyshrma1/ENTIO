package com.entio.semantic

import com.entio.core.BlankNodeResource
import com.entio.core.ChangePreview
import com.entio.core.ChangeSet
import com.entio.core.EntioResult
import com.entio.core.GraphChange
import com.entio.core.GraphChangeKind
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.LoadedOntology
import com.entio.core.RdfLiteral
import com.entio.core.SemanticEquivalenceResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PreviewTurtleRoundTripVerifierTest {
    private val verifier = PreviewTurtleRoundTripVerifier()
    private val parser = OntologyParser()

    @Test
    fun serializesAndReparsesIriResourceGraphs(): Unit {
        val graph = graph(
            triple(
                subject = Iri("https://example.com/Customer"),
                predicate = Iri("https://example.com/relatedTo"),
                objectTerm = Iri("https://example.com/Account"),
            ),
        )

        assertEquivalentAfterRoundTrip(graph)
    }

    @Test
    fun serializesAndReparsesPlainLiterals(): Unit {
        val graph = graph(
            triple(
                subject = Iri("https://example.com/Customer"),
                predicate = Iri("http://www.w3.org/2000/01/rdf-schema#label"),
                objectTerm = RdfLiteral("Customer"),
            ),
        )

        assertEquivalentAfterRoundTrip(graph)
    }

    @Test
    fun serializesAndReparsesDatatypedLiterals(): Unit {
        val graph = graph(
            triple(
                subject = Iri("https://example.com/Customer"),
                predicate = Iri("https://example.com/score"),
                objectTerm = RdfLiteral(
                    lexicalForm = "42",
                    datatypeIri = Iri("http://www.w3.org/2001/XMLSchema#integer"),
                ),
            ),
        )

        assertEquivalentAfterRoundTrip(graph)
    }

    @Test
    fun serializesAndReparsesLanguageTaggedLiterals(): Unit {
        val graph = graph(
            triple(
                subject = Iri("https://example.com/Customer"),
                predicate = Iri("http://www.w3.org/2000/01/rdf-schema#label"),
                objectTerm = RdfLiteral(
                    lexicalForm = "Customer",
                    languageTag = "en",
                ),
            ),
        )

        assertEquivalentAfterRoundTrip(graph)
    }

    @Test
    fun serializesAndReparsesBlankNodesWithoutTreatingLabelsAsDurable(): Unit {
        val graph = graph(
            triple(
                subject = Iri("https://example.com/Customer"),
                predicate = Iri("https://example.com/hasDetail"),
                objectTerm = BlankNodeResource(id = "detail"),
            ),
            triple(
                subject = BlankNodeResource(id = "detail"),
                predicate = Iri("https://example.com/code"),
                objectTerm = RdfLiteral("C-001"),
            ),
        )

        val source = assertIs<EntioResult.Success<com.entio.core.ResolvedOntologySource>>(
            verifier.serializeToTemporaryTurtle(preview(graph)),
        ).value
        val reparsed = assertIs<EntioResult.Success<LoadedOntology>>(parser.parse(source)).value

        assertIs<BlankNodeResource>(
            reparsed.graph.triples.single {
                it.subjectResource == Iri("https://example.com/Customer")
            }.objectTerm,
        )
        assertEquals(SemanticEquivalenceResult.Equivalent, verifier.verify(preview(graph)).successValue())
    }

    @Test
    fun detectsSemanticEquivalenceFailureDeterministically(): Unit {
        val expected = graph(
            triple(
                subject = Iri("https://example.com/Customer"),
                predicate = Iri("https://example.com/relatedTo"),
                objectTerm = Iri("https://example.com/Account"),
            ),
        )
        val actual = graph(
            triple(
                subject = Iri("https://example.com/Customer"),
                predicate = Iri("https://example.com/relatedTo"),
                objectTerm = Iri("https://example.com/Opportunity"),
            ),
        )

        val first = verifier.compareSemanticEquivalence(expected, actual)
        val second = verifier.compareSemanticEquivalence(expected, actual)

        assertEquals(first, second)
        assertIs<SemanticEquivalenceResult.NotEquivalent>(first)
    }

    private fun assertEquivalentAfterRoundTrip(graph: GraphState): Unit {
        val result = verifier.verify(preview(graph))

        assertEquals(SemanticEquivalenceResult.Equivalent, result.successValue())
    }

    private fun preview(graph: GraphState): ChangePreview {
        val changeSet = ChangeSet(
            changes = listOf(GraphChange(GraphChangeKind.Addition, graph.triples.first())),
        )

        return ChangePreview(
            graph = graph,
            changeSet = changeSet,
        )
    }

    private fun graph(vararg triples: GraphTriple): GraphState =
        GraphState(triples = triples.toSet())

    private fun triple(
        subject: com.entio.core.RdfResource,
        predicate: Iri,
        objectTerm: com.entio.core.RdfTerm,
    ): GraphTriple =
        GraphTriple(
            subject = subject,
            predicate = predicate,
            objectTerm = objectTerm,
        )

    private fun EntioResult<SemanticEquivalenceResult>.successValue(): SemanticEquivalenceResult =
        assertIs<EntioResult.Success<SemanticEquivalenceResult>>(this).value
}
