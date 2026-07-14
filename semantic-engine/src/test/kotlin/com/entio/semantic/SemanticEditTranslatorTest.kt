package com.entio.semantic

import com.entio.core.AnnotationValue
import com.entio.core.ChangeSet
import com.entio.core.EntioResult
import com.entio.core.GraphChange
import com.entio.core.GraphChangeKind
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.core.SemanticEditRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SemanticEditTranslatorTest {
    private val translator = TypedOntologyEditTranslator()
    private val customer = Iri("https://example.com/Customer")
    private val note = Iri("https://example.com/note")
    private val replacement = RdfLiteral("replacement", languageTag = "en")

    @Test
    fun translatesAnnotationPropertyCreationAndMetadata(): Unit {
        val property = Iri("https://example.com/definition")

        assertEquals(
            listOf(
                added(property, RDF_TYPE, OWL_ANNOTATION_PROPERTY),
                added(property, RDFS_LABEL, RdfLiteral("Definition")),
                added(property, SKOS_DEFINITION, RdfLiteral("A definition")),
            ),
            translate(
                SemanticEditRequest.CreateAnnotationProperty(
                    propertyIri = property,
                    sourceId = "simple",
                    label = RdfLiteral("Definition"),
                    definition = RdfLiteral("A definition"),
                ),
            ).changes,
        )
    }

    @Test
    fun translatesDefinitionAlternateLabelAndAnnotationEdits(): Unit {
        assertEquals(
            listOf(added(customer, SKOS_DEFINITION, replacement)),
            translate(SemanticEditRequest.AddDefinition(customer, replacement, "simple")).changes,
        )
        assertEquals(
            listOf(removed(customer, SKOS_DEFINITION, replacement)),
            translate(SemanticEditRequest.RemoveDefinition(customer, replacement, "simple")).changes,
        )
        assertEquals(
            listOf(added(customer, SKOS_ALT_LABEL, replacement)),
            translate(SemanticEditRequest.AddAlternateLabel(customer, replacement, "simple")).changes,
        )
        assertEquals(
            listOf(removed(customer, SKOS_ALT_LABEL, replacement)),
            translate(SemanticEditRequest.RemoveAlternateLabel(customer, replacement, "simple")).changes,
        )
        assertEquals(
            listOf(added(customer, note, replacement)),
            translate(
                SemanticEditRequest.AddAnnotation(
                    target = customer,
                    property = note,
                    value = AnnotationValue.Literal(replacement),
                    sourceId = "simple",
                ),
            ).changes,
        )
    }

    @Test
    fun replacementIsAnExplicitRemovalFollowedByAddition(): Unit {
        val old = RdfLiteral("old", languageTag = "en")
        val edit = SemanticEditRequest.ReplaceAlternateLabel(
            target = customer,
            existing = old,
            replacement = replacement,
            sourceId = "simple",
        )

        assertEquals(
            listOf(
                removed(customer, SKOS_ALT_LABEL, old),
                added(customer, SKOS_ALT_LABEL, replacement),
            ),
            translate(edit).changes,
        )
    }

    @Test
    fun rejectsInvalidTargetsAndMissingAnnotationProperties(): Unit {
        val blankTarget = assertIs<EntioResult.Failure>(
            translator.translate(
                SemanticEditRequest.AddDefinition(Iri("   "), replacement, "simple"),
            ),
        )
        assertEquals("invalid-iri", blankTarget.issues.single().code)

        val missingProperty = assertIs<EntioResult.Failure>(
            translator.translate(
                SemanticEditRequest.AddAnnotation(
                    target = customer,
                    property = note,
                    value = AnnotationValue.Literal(replacement),
                    sourceId = "simple",
                ),
                existingAnnotationProperties = emptySet(),
            ),
        )
        assertEquals("missing-annotation-property", missingProperty.issues.single().code)
    }

    private fun translate(edit: SemanticEditRequest): ChangeSet =
        assertIs<EntioResult.Success<ChangeSet>>(translator.translate(edit)).value

    private fun added(subject: Iri, predicate: Iri, objectTerm: com.entio.core.RdfTerm): GraphChange =
        GraphChange(GraphChangeKind.Addition, GraphTriple(subject, predicate, objectTerm))

    private fun removed(subject: Iri, predicate: Iri, objectTerm: com.entio.core.RdfTerm): GraphChange =
        GraphChange(GraphChangeKind.Removal, GraphTriple(subject, predicate, objectTerm))

    private companion object {
        private val RDF_TYPE = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
        private val RDFS_LABEL = Iri("http://www.w3.org/2000/01/rdf-schema#label")
        private val SKOS_ALT_LABEL = Iri("http://www.w3.org/2004/02/skos/core#altLabel")
        private val SKOS_DEFINITION = Iri("http://www.w3.org/2004/02/skos/core#definition")
        private val OWL_ANNOTATION_PROPERTY = Iri("http://www.w3.org/2002/07/owl#AnnotationProperty")
    }
}
