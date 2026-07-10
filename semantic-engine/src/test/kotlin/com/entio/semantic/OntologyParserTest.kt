package com.entio.semantic

import com.entio.core.BlankNodeResource
import com.entio.core.EntioResult
import com.entio.core.Iri
import com.entio.core.LoadedOntology
import com.entio.core.RdfLiteral
import com.entio.core.ResolvedOntologySource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OntologyParserTest {
    private val parser = OntologyParser()

    @Test
    fun parsesValidTurtleIntoGraphTriples(): Unit {
        val source = resolvedSource(
            """
            @prefix ex: <https://example.com/> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            ex:Customer rdfs:label "Customer" .
            ex:Customer ex:relatedTo ex:Account .
            """.trimIndent(),
        )

        val result = parser.parse(source)

        val success = assertIs<EntioResult.Success<LoadedOntology>>(result)
        val triples = success.value.graph.triples
        assertEquals(source, success.value.source)
        assertEquals(2, triples.size)
        assertEquals(
            setOf(
                Triple(
                    "https://example.com/Customer",
                    "http://www.w3.org/2000/01/rdf-schema#label",
                    "Customer",
                ),
                Triple(
                    "https://example.com/Customer",
                    "https://example.com/relatedTo",
                    "https://example.com/Account",
                ),
            ),
            triples.map { Triple(it.subject.value, it.predicate.value, it.objectValue) }.toSet(),
        )
    }

    @Test
    fun preservesIriObjectTerms(): Unit {
        val source = resolvedSource(
            """
            @prefix ex: <https://example.com/> .

            ex:Customer ex:relatedTo ex:Account .
            """.trimIndent(),
        )

        val success = assertIs<EntioResult.Success<LoadedOntology>>(parser.parse(source))
        val triple = success.value.graph.triples.single()

        assertEquals(Iri("https://example.com/Customer"), triple.subjectResource)
        assertEquals(Iri("https://example.com/relatedTo"), triple.predicate)
        assertEquals(Iri("https://example.com/Account"), triple.objectTerm)
    }

    @Test
    fun preservesBlankNodeSubjects(): Unit {
        val source = resolvedSource(
            """
            @prefix ex: <https://example.com/> .

            [] ex:code "C-001" .
            """.trimIndent(),
        )

        val success = assertIs<EntioResult.Success<LoadedOntology>>(parser.parse(source))
        val triple = success.value.graph.triples.single()

        assertIs<BlankNodeResource>(triple.subjectResource)
        assertEquals(Iri("https://example.com/code"), triple.predicate)
        assertEquals("C-001", assertIs<RdfLiteral>(triple.objectTerm).lexicalForm)
    }

    @Test
    fun preservesBlankNodeObjects(): Unit {
        val source = resolvedSource(
            """
            @prefix ex: <https://example.com/> .

            ex:Customer ex:hasDetail [ ex:code "C-001" ] .
            """.trimIndent(),
        )

        val success = assertIs<EntioResult.Success<LoadedOntology>>(parser.parse(source))
        val triple = success.value.graph.triples.single {
            it.subjectResource == Iri("https://example.com/Customer") &&
                it.predicate == Iri("https://example.com/hasDetail")
        }

        assertIs<BlankNodeResource>(triple.objectTerm)
    }

    @Test
    fun preservesPlainLiteralTerms(): Unit {
        val source = resolvedSource(
            """
            @prefix ex: <https://example.com/> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            ex:Customer rdfs:label "Customer" .
            """.trimIndent(),
        )

        val success = assertIs<EntioResult.Success<LoadedOntology>>(parser.parse(source))
        val literal = assertIs<RdfLiteral>(success.value.graph.triples.single().objectTerm)

        assertEquals("Customer", literal.lexicalForm)
        assertEquals(null, literal.languageTag)
    }

    @Test
    fun preservesDatatypedLiteralTerms(): Unit {
        val source = resolvedSource(
            """
            @prefix ex: <https://example.com/> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

            ex:Customer ex:age "42"^^xsd:integer .
            """.trimIndent(),
        )

        val success = assertIs<EntioResult.Success<LoadedOntology>>(parser.parse(source))
        val literal = assertIs<RdfLiteral>(success.value.graph.triples.single().objectTerm)

        assertEquals("42", literal.lexicalForm)
        assertEquals(Iri("http://www.w3.org/2001/XMLSchema#integer"), literal.datatypeIri)
        assertEquals(null, literal.languageTag)
    }

    @Test
    fun preservesLanguageTaggedLiteralTerms(): Unit {
        val source = resolvedSource(
            """
            @prefix ex: <https://example.com/> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            ex:Customer rdfs:label "Customer"@en .
            """.trimIndent(),
        )

        val success = assertIs<EntioResult.Success<LoadedOntology>>(parser.parse(source))
        val literal = assertIs<RdfLiteral>(success.value.graph.triples.single().objectTerm)

        assertEquals("Customer", literal.lexicalForm)
        assertEquals("en", literal.languageTag)
    }

    @Test
    fun returnsStructuredFailureForInvalidTurtle(): Unit {
        val source = resolvedSource(
            """
            @prefix ex: <https://example.com/> .
            ex:Customer ex:relatedTo .
            """.trimIndent(),
        )

        val result = parser.parse(source)

        val failure = assertIs<EntioResult.Failure>(result)
        assertEquals("invalid-turtle", failure.issues.single().code)
        assertEquals("simple", failure.issues.single().source)
    }

    @Test
    fun parserOutputIsStableAcrossRepeatedParses(): Unit {
        val source = resolvedSource(
            """
            @prefix ex: <https://example.com/> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            ex:Account rdfs:label "Account" .
            ex:Customer rdfs:label "Customer" .
            """.trimIndent(),
        )

        val first = assertIs<EntioResult.Success<LoadedOntology>>(parser.parse(source))
        val second = assertIs<EntioResult.Success<LoadedOntology>>(parser.parse(source))

        assertEquals(first.value.graph, second.value.graph)
    }

    private fun resolvedSource(content: String): ResolvedOntologySource {
        return SemanticEngineTestFixtures.resolvedSource(content)
    }
}
