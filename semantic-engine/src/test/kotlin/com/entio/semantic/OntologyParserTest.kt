package com.entio.semantic

import com.entio.core.EntioResult
import com.entio.core.Iri
import com.entio.core.LoadedOntology
import com.entio.core.OntologyFormat
import com.entio.core.ResolvedOntologySource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
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
        val path = Files.createTempFile("entio-parser", ".ttl")
        path.writeText(content)
        return ResolvedOntologySource(
            id = "simple",
            path = path,
            format = OntologyFormat.Turtle,
        )
    }
}
