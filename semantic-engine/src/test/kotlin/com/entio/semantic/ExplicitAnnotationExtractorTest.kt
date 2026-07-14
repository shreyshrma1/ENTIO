package com.entio.semantic

import com.entio.core.AnnotationValue
import com.entio.core.BlankNodeResource
import com.entio.core.EntioResult
import com.entio.core.Iri
import com.entio.core.LoadedOntology
import com.entio.core.RdfLiteral
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ExplicitAnnotationExtractorTest {
    private val parser = OntologyParser()
    private val extractor = ExplicitAnnotationExtractor()

    @Test
    fun extractsRequiredVocabularyPropertiesWithoutApplyingPolicy(): Unit {
        val ontology = parse(
            """
            @prefix ex: <https://example.com/> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix skos: <http://www.w3.org/2004/02/skos/core#> .
            @prefix dcterms: <http://purl.org/dc/terms/> .

            ex:Customer rdfs:label "Customer"@en ;
                rdfs:comment "A customer" ;
                skos:prefLabel "Client"@en ;
                skos:altLabel "Buyer"@en ;
                skos:definition "A buyer of goods"@en ;
                dcterms:source ex:Source .
            """.trimIndent(),
        )

        val annotations = extractor.extract(ontology, Iri("https://example.com/Customer"))

        assertEquals(
            setOf(
                AnnotationVocabulary.rdfsLabel,
                AnnotationVocabulary.rdfsComment,
                AnnotationVocabulary.skosPrefLabel,
                AnnotationVocabulary.skosAltLabel,
                AnnotationVocabulary.skosDefinition,
                AnnotationVocabulary.dctermsSource,
            ),
            annotations.recognized.keys,
        )
        assertEquals(6, annotations.all.size)
        assertEquals(emptyList(), annotations.general)
    }

    @Test
    fun preservesLiteralMetadataResourcesAndBlankNodes(): Unit {
        val ontology = parse(
            """
            @prefix ex: <https://example.com/> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

            ex:Customer ex:iriValue ex:Account ;
                ex:blankValue [ ex:code "B-1" ] ;
                ex:typedValue "42"^^xsd:integer ;
                rdfs:label "Customer"@en .
            """.trimIndent(),
        )

        val annotations = extractor.extract(ontology, Iri("https://example.com/Customer"))
        val valuesByProperty = annotations.general.associateBy { it.property }

        assertEquals(
            AnnotationValue.Resource(Iri("https://example.com/Account")),
            valuesByProperty.getValue(Iri("https://example.com/iriValue")).value,
        )
        assertIs<BlankNodeResource>(
            assertIs<AnnotationValue.Resource>(valuesByProperty.getValue(Iri("https://example.com/blankValue")).value)
                .resource,
        )
        assertEquals(
            AnnotationValue.Literal(
                RdfLiteral(
                    lexicalForm = "42",
                    datatypeIri = Iri("http://www.w3.org/2001/XMLSchema#integer"),
                ),
            ),
            valuesByProperty.getValue(Iri("https://example.com/typedValue")).value,
        )
    }

    @Test
    fun excludesStructuralTriplesAndPreservesDistinctStatementsInStableOrder(): Unit {
        val ontology = parse(
            """
            @prefix ex: <https://example.com/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            ex:Customer a owl:Class ;
                rdfs:subClassOf ex:Party ;
                rdfs:label "Customer" ;
                ex:note "same" ;
                ex:note "other" ;
                ex:other "value" .
            """.trimIndent(),
        )

        val annotations = extractor.extract(ontology, Iri("https://example.com/Customer"))

        assertEquals(
            listOf(
                "https://example.com/note",
                "https://example.com/note",
                "https://example.com/other",
            ),
            annotations.general.map { it.property.value },
        )
        assertEquals(
            listOf("other", "same"),
            annotations.general
                .filter { it.property == Iri("https://example.com/note") }
                .map { (it.value as AnnotationValue.Literal).literal.lexicalForm },
        )
    }

    @Test
    fun vocabularyClassificationIsExplicitAndStable(): Unit {
        assertEquals(true, AnnotationVocabulary.isRecognized(AnnotationVocabulary.skosDefinition))
        assertEquals(true, AnnotationVocabulary.isStructural(Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")))
        assertEquals(false, AnnotationVocabulary.isRecognized(Iri("https://example.com/note")))
        assertEquals(false, AnnotationVocabulary.isStructural(Iri("https://example.com/note")))
    }

    private fun parse(content: String): LoadedOntology {
        val result = parser.parse(SemanticEngineTestFixtures.resolvedSource(content))
        return assertIs<EntioResult.Success<LoadedOntology>>(result).value
    }
}
