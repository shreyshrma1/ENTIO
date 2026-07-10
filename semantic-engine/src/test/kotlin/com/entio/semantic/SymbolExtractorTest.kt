package com.entio.semantic

import com.entio.core.EntioResult
import com.entio.core.LoadedOntology
import com.entio.core.SymbolKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SymbolExtractorTest {
    private val parser = OntologyParser()
    private val extractor = SymbolExtractor()

    @Test
    fun extractsClassesWithLabels(): Unit {
        val ontology = parseOntology(
            """
            @prefix ex: <https://example.com/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            ex:Customer a owl:Class ;
              rdfs:label "Customer" .
            """.trimIndent(),
        )

        val symbols = extractor.extractSymbols(ontology)

        assertEquals(1, symbols.size)
        assertEquals("https://example.com/Customer", symbols.single().iri.value)
        assertEquals("Customer", symbols.single().label)
        assertEquals(SymbolKind.Class, symbols.single().kind)
        assertEquals("simple", symbols.single().sourceId)
    }

    @Test
    fun extractsProperties(): Unit {
        val ontology = parseOntology(
            """
            @prefix ex: <https://example.com/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .

            ex:ownsAccount a owl:ObjectProperty .
            ex:openedOn a owl:DatatypeProperty .
            """.trimIndent(),
        )

        val symbols = extractor.extractSymbols(ontology)

        assertEquals(
            listOf(
                "https://example.com/openedOn" to SymbolKind.Property,
                "https://example.com/ownsAccount" to SymbolKind.Property,
            ),
            symbols.map { it.iri.value to it.kind },
        )
    }

    @Test
    fun extractsIndividuals(): Unit {
        val ontology = parseOntology(
            """
            @prefix ex: <https://example.com/> .

            ex:AcmeCorp a ex:Organization .
            """.trimIndent(),
        )

        val symbols = extractor.extractSymbols(ontology)

        assertEquals(1, symbols.size)
        assertEquals("https://example.com/AcmeCorp", symbols.single().iri.value)
        assertEquals(SymbolKind.Individual, symbols.single().kind)
    }

    @Test
    fun extractsShapes(): Unit {
        val ontology = parseOntology(
            """
            @prefix ex: <https://example.com/> .
            @prefix sh: <http://www.w3.org/ns/shacl#> .

            ex:CustomerShape a sh:NodeShape .
            """.trimIndent(),
        )

        val symbols = extractor.extractSymbols(ontology)

        assertEquals(1, symbols.size)
        assertEquals("https://example.com/CustomerShape", symbols.single().iri.value)
        assertEquals(SymbolKind.Shape, symbols.single().kind)
    }

    @Test
    fun returnsSymbolsInStableIriOrder(): Unit {
        val ontology = parseOntology(
            """
            @prefix ex: <https://example.com/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .

            ex:Zeta a owl:Class .
            ex:Alpha a owl:Class .
            """.trimIndent(),
        )

        val symbols = extractor.extractSymbols(ontology)

        assertEquals(
            listOf(
                "https://example.com/Alpha",
                "https://example.com/Zeta",
            ),
            symbols.map { it.iri.value },
        )
    }

    private fun parseOntology(turtle: String): LoadedOntology {
        val source = SemanticEngineTestFixtures.resolvedSource(turtle)
        val result = parser.parse(source)
        return assertIs<EntioResult.Success<LoadedOntology>>(result).value
    }
}
