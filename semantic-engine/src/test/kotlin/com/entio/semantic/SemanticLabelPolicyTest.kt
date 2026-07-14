package com.entio.semantic

import com.entio.core.EntioResult
import com.entio.core.Iri
import com.entio.core.LoadedOntology
import com.entio.core.LocalizedText
import com.entio.core.PreferredLabelSource
import com.entio.core.RdfLiteral
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs

class SemanticLabelPolicyTest {
    private val parser = OntologyParser()
    private val policy = SemanticLabelPolicy()

    @Test
    fun appliesConfiguredLanguageAndVocabularyPriority(): Unit {
        val ontology = parse(
            """
            @prefix ex: <https://example.com/> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix skos: <http://www.w3.org/2004/02/skos/core#> .

            ex:Thing rdfs:label "Thing"@en ;
                rdfs:label "Chose"@fr ;
                skos:prefLabel "Objet"@fr ;
                skos:prefLabel "Object" ;
                skos:altLabel "Item"@en ;
                skos:altLabel "Item"@en ;
                skos:definition "A thing"@en ;
                skos:definition "A thing"@en .
            """.trimIndent(),
        )

        val annotations = ExplicitAnnotationExtractor().extract(ontology, Iri("https://example.com/Thing"))
        val french = policy.apply(Iri("https://example.com/Thing"), annotations, preferredLanguage = "fr")
        val english = policy.apply(Iri("https://example.com/Thing"), annotations, preferredLanguage = "en")

        val langString = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#langString")
        assertEquals(LocalizedText("Objet", languageTag = "fr", datatypeIri = langString), french.preferredLabel)
        assertEquals(PreferredLabelSource.SkosPreferredLabel, french.preferredLabelSource)
        assertEquals(LocalizedText("Thing", languageTag = "en", datatypeIri = langString), english.preferredLabel)
        assertEquals(PreferredLabelSource.RdfsLabel, english.preferredLabelSource)
        assertEquals(listOf(LocalizedText("Item", languageTag = "en", datatypeIri = langString)), english.alternateLabels)
        assertEquals(listOf(LocalizedText("A thing", languageTag = "en", datatypeIri = langString)), english.definitions)
        assertTrue(english.annotations.none { it.property == AnnotationVocabulary.rdfsLabel })
    }

    @Test
    fun exposesAmbiguousPreferredLanguageAndReadableIriFallback(): Unit {
        val ontology = parse(
            """
            @prefix ex: <https://example.com/> .
            @prefix skos: <http://www.w3.org/2004/02/skos/core#> .

            ex:noLabel skos:prefLabel "One"@en, "Two"@en .
            ex:receivedInvoice a <http://www.w3.org/2002/07/owl#Class> .
            """.trimIndent(),
        )

        val extractor = ExplicitAnnotationExtractor()
        val ambiguous = policy.apply(
            Iri("https://example.com/noLabel"),
            extractor.extract(ontology, Iri("https://example.com/noLabel")),
            preferredLanguage = "en",
        )
        val fallback = policy.apply(
            Iri("https://example.com/receivedInvoice"),
            extractor.extract(ontology, Iri("https://example.com/receivedInvoice")),
        )

        assertEquals(listOf("en"), ambiguous.ambiguousPreferredLabelLanguages)
        assertEquals(
            LocalizedText(
                "One",
                languageTag = "en",
                datatypeIri = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#langString"),
            ),
            ambiguous.preferredLabel,
        )
        assertEquals(LocalizedText("received Invoice"), fallback.preferredLabel)
        assertEquals(PreferredLabelSource.IriLocalName, fallback.preferredLabelSource)
    }

    private fun parse(content: String): LoadedOntology {
        val result = parser.parse(SemanticEngineTestFixtures.resolvedSource(content))
        return assertIs<EntioResult.Success<LoadedOntology>>(result).value
    }
}
