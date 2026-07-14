package com.entio.validation

import com.entio.core.GraphChange
import com.entio.core.GraphChangeKind
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.semantic.AnnotationVocabulary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SemanticMetadataValidatorTest {
    private val validator = SemanticMetadataValidator()
    private val customer = Iri("https://example.com/Customer")
    private val annotationProperty = Iri("https://example.com/note")
    private val rdfType = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
    private val owlClass = Iri("http://www.w3.org/2002/07/owl#Class")
    private val owlAnnotationProperty = Iri("http://www.w3.org/2002/07/owl#AnnotationProperty")
    private val owlObjectProperty = Iri("http://www.w3.org/2002/07/owl#ObjectProperty")

    @Test
    fun validatesLiteralShapeAndDatatypeDeterministically(): Unit {
        val changes = listOf(
            addition(AnnotationVocabulary.skosAltLabel, RdfLiteral("value", languageTag = "bad tag")),
            addition(
                AnnotationVocabulary.skosDefinition,
                RdfLiteral("value", datatypeIri = Iri("https://example.com/UnknownDatatype")),
            ),
        )

        val issues = validator.validate(baseGraph(), changes)

        assertEquals(listOf("invalid-language-tag", "unsupported-datatype"), issues.map { it.code })
    }

    @Test
    fun rejectsNonLiteralStandardMetadataAndAmbiguousPreferredLabels(): Unit {
        val changes = listOf(
            addition(AnnotationVocabulary.skosDefinition, annotationProperty),
            addition(AnnotationVocabulary.skosPrefLabel, RdfLiteral("Customer A", languageTag = "en")),
            addition(AnnotationVocabulary.skosPrefLabel, RdfLiteral("Customer B", languageTag = "en")),
        )

        val issues = validator.validate(baseGraph(), changes)

        assertTrue(issues.any { it.code == "invalid-semantic-value" })
        assertTrue(issues.any { it.code == "ambiguous-preferred-label" })
    }

    @Test
    fun requiresKnownSemanticTargets(): Unit {
        val missingTarget = GraphChange(
            kind = GraphChangeKind.Addition,
            triple = GraphTriple(Iri("https://example.com/Missing"), AnnotationVocabulary.rdfsComment, RdfLiteral("note")),
        )

        val issues = validator.validate(baseGraph(), listOf(missingTarget))

        assertTrue(issues.any { it.code == "missing-semantic-target" })
    }

    @Test
    fun rejectsObjectPropertyUsedAsAnnotationProperty(): Unit {
        val propertyType = GraphChange(
            kind = GraphChangeKind.Addition,
            triple = GraphTriple(annotationProperty, rdfType, owlObjectProperty),
        )
        val annotation = addition(annotationProperty, RdfLiteral("note"))

        val issues = validator.validate(baseGraph(), listOf(propertyType, annotation))

        assertEquals("incompatible-annotation-property", issues.single { it.code.contains("annotation-property") }.code)
    }

    private fun baseGraph(): GraphState = GraphState(
        triples = setOf(
            GraphTriple(customer, rdfType, owlClass),
            GraphTriple(annotationProperty, rdfType, owlAnnotationProperty),
        ),
    )

    private fun addition(predicate: Iri, objectTerm: com.entio.core.RdfTerm): GraphChange =
        GraphChange(
            kind = GraphChangeKind.Addition,
            triple = GraphTriple(customer, predicate, objectTerm),
        )
}
