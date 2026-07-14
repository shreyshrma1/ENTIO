package com.entio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SemanticDescriptionContractsTest {
    private val sourceId = "simple"
    private val classIri = Iri("https://example.com/entio/simple#Customer")
    private val annotationIri = Iri("https://example.com/entio/simple#note")

    @Test
    fun preservesAllRdfTermShapesInAnnotationValues(): Unit {
        val terms = listOf<RdfTerm>(
            Iri("https://example.com/resource"),
            BlankNodeResource("b0"),
            RdfLiteral("plain"),
            RdfLiteral("42", datatypeIri = Iri("http://www.w3.org/2001/XMLSchema#integer")),
            RdfLiteral("bonjour", languageTag = "fr"),
        )

        terms.forEach { term ->
            val value = AnnotationValue.fromTerm(term)
            assertEquals(term, value.term)
            when (term) {
                is RdfResource -> assertIs<AnnotationValue.Resource>(value)
                is RdfLiteral -> assertIs<AnnotationValue.Literal>(value)
            }
        }
    }

    @Test
    fun localizedTextRoundTripsItsRdfLiteralMetadata(): Unit {
        val text = LocalizedText(
            lexicalForm = "Customer",
            languageTag = "en",
            datatypeIri = null,
        )

        assertEquals(RdfLiteral("Customer", languageTag = "en"), text.asRdfLiteral())
        assertEquals(text, LocalizedText("Customer", languageTag = "en"))
    }

    @Test
    fun constructsEveryDescriptorKindWithImmutableDefaults(): Unit {
        val common = SemanticDescriptorCommon(
            entity = classIri,
            kind = SemanticDescriptorKind.Class,
            sourceId = sourceId,
        )
        val descriptors = listOf<OntologyEntityDescriptor>(
            OntologyEntityDescriptor.Class(common),
            OntologyEntityDescriptor.ObjectProperty(common.copy(kind = SemanticDescriptorKind.ObjectProperty)),
            OntologyEntityDescriptor.DatatypeProperty(common.copy(kind = SemanticDescriptorKind.DatatypeProperty)),
            OntologyEntityDescriptor.AnnotationProperty(common.copy(kind = SemanticDescriptorKind.AnnotationProperty)),
            OntologyEntityDescriptor.Individual(common.copy(kind = SemanticDescriptorKind.Individual)),
        )

        assertEquals(5, descriptors.size)
        descriptors.forEach { descriptor ->
            assertEquals(emptyList<LocalizedText>(), descriptor.common.alternateLabels)
            assertEquals(emptyList<LocalizedText>(), descriptor.common.definitions)
            assertEquals(emptyList<AnnotationStatement>(), descriptor.common.annotations)
        }
    }

    @Test
    fun annotationStatementsHaveDeterministicOrderingKeys(): Unit {
        val statement = AnnotationStatement(
            subject = classIri,
            property = annotationIri,
            value = AnnotationValue.Literal(RdfLiteral("A", languageTag = "en")),
            sourceId = sourceId,
        )

        assertEquals(
            "simple\u0000iri:https://example.com/entio/simple#Customer\u0000" +
                "https://example.com/entio/simple#note\u0000literal:A||en",
            statement.stableKey,
        )
    }

    @Test
    fun constructsSearchAndEverySemanticEditState(): Unit {
        val descriptor = OntologyEntityDescriptor.Class(
            common = SemanticDescriptorCommon(
                entity = classIri,
                kind = SemanticDescriptorKind.Class,
                sourceId = sourceId,
                preferredLabel = LocalizedText("Customer", languageTag = "en"),
            ),
        )
        val result = SemanticSearchResult(descriptor, SemanticMatchReason.PreferredLabel, rank = 0)
        val query = SemanticSearchQuery(text = "customer", preferredLanguage = "en")
        val edits = listOf<SemanticEditRequest>(
            SemanticEditRequest.CreateAnnotationProperty(annotationIri, sourceId),
            SemanticEditRequest.AddDefinition(classIri, RdfLiteral("A customer"), sourceId),
            SemanticEditRequest.ReplaceDefinition(classIri, RdfLiteral("Old"), RdfLiteral("New"), sourceId),
            SemanticEditRequest.RemoveDefinition(classIri, RdfLiteral("Old"), sourceId),
            SemanticEditRequest.AddAlternateLabel(classIri, RdfLiteral("Client"), sourceId),
            SemanticEditRequest.ReplaceAlternateLabel(classIri, RdfLiteral("Client"), RdfLiteral("Buyer"), sourceId),
            SemanticEditRequest.RemoveAlternateLabel(classIri, RdfLiteral("Client"), sourceId),
            SemanticEditRequest.AddAnnotation(
                target = classIri,
                property = annotationIri,
                value = AnnotationValue.Literal(RdfLiteral("A")),
                sourceId = sourceId,
            ),
            SemanticEditRequest.RemoveAnnotation(
                target = classIri,
                property = annotationIri,
                value = AnnotationValue.Resource(BlankNodeResource("b0")),
                sourceId = sourceId,
            ),
        )

        assertEquals("customer", query.text)
        assertEquals(SemanticMatchReason.PreferredLabel, result.reason)
        assertEquals((0).toString(), result.stableKey.substringBefore('\u0000'))
        assertEquals(
            listOf(
                SemanticEditKind.CreateAnnotationProperty,
                SemanticEditKind.AddDefinition,
                SemanticEditKind.ReplaceDefinition,
                SemanticEditKind.RemoveDefinition,
                SemanticEditKind.AddAlternateLabel,
                SemanticEditKind.ReplaceAlternateLabel,
                SemanticEditKind.RemoveAlternateLabel,
                SemanticEditKind.AddAnnotation,
                SemanticEditKind.RemoveAnnotation,
            ),
            edits.map { it.kind },
        )
    }
}
