package com.entio.semantic

import com.entio.core.BlankNodeResource
import com.entio.core.EditableShaclConstraint
import com.entio.core.EditableShaclConstraintKind
import com.entio.core.EditableShaclConstraintValue
import com.entio.core.EntioResult
import com.entio.core.GraphChangeKind
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.core.TypedShaclEdit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TypedShaclEditTranslatorTest {
    private val translator = TypedShaclEditTranslator()
    private val shape = Iri("https://example.com/CustomerShape")
    private val path = Iri("https://example.com/ownsAccount")
    private val propertyNode = BlankNodeResource("existing-property")

    @Test
    fun createsTypedPropertyShapeThroughAuthoringService(): Unit {
        val result = translator.translate(
            TypedShaclEdit.CreatePropertyShape(
                sourceId = "shapes",
                shapeIri = Iri("https://example.com/BorrowerShape"),
                label = "Borrower shape",
                targetClassIri = Iri("https://example.com/Borrower"),
                pathIri = Iri("https://example.com/hasBorrower"),
                constraint = EditableShaclConstraint(
                    EditableShaclConstraintKind.MinCount,
                    EditableShaclConstraintValue.IntegerValue(1),
                ),
            ),
            GraphState(emptySet()),
        )

        val changes = assertIs<EntioResult.Success<com.entio.core.ChangeSet>>(result).value
        assertTrue(changes.additions.any { it.triple.predicate.value == SH_TARGET_CLASS })
        assertTrue(changes.additions.any { it.triple.predicate.value == SH_MIN_COUNT })
        assertTrue(changes.removals.isEmpty())
    }

    @Test
    fun updatesAndRemovesTheActualExistingBlankNodeConstraint(): Unit {
        val update = translator.translate(
            TypedShaclEdit.UpdateConstraint(
                "shapes",
                shape,
                path,
                EditableShaclConstraint(
                    EditableShaclConstraintKind.MinCount,
                    EditableShaclConstraintValue.IntegerValue(2),
                ),
            ),
            existingGraph(),
        )
        val updateChanges = assertIs<EntioResult.Success<com.entio.core.ChangeSet>>(update).value
        assertEquals(setOf(propertyNode), updateChanges.changes.map { it.triple.subjectResource }.toSet())
        assertEquals(listOf(GraphChangeKind.Removal, GraphChangeKind.Addition), updateChanges.changes.map { it.kind })
        assertEquals("2", updateChanges.additions.single().triple.objectValue)

        val remove = translator.translate(
            TypedShaclEdit.RemoveConstraint("shapes", shape, path, EditableShaclConstraintKind.MinCount),
            existingGraph(),
        )
        val removeChanges = assertIs<EntioResult.Success<com.entio.core.ChangeSet>>(remove).value
        assertEquals(1, removeChanges.removals.size)
        assertEquals(propertyNode, removeChanges.removals.single().triple.subjectResource)
    }

    @Test
    fun deletesTheNodeShapeAndItsBoundedPropertyShapeSubgraph(): Unit {
        val result = translator.translate(TypedShaclEdit.DeleteShape("shapes", shape), existingGraph())

        val changes = assertIs<EntioResult.Success<com.entio.core.ChangeSet>>(result).value
        assertTrue(changes.changes.all { it.kind == GraphChangeKind.Removal })
        assertEquals(existingGraph().triples, changes.changes.map { it.triple }.toSet())
    }

    private fun existingGraph(): GraphState = GraphState(
        setOf(
            GraphTriple(shape, Iri(RDF_TYPE), Iri(SH_NODE_SHAPE)),
            GraphTriple(shape, Iri(SH_TARGET_CLASS), Iri("https://example.com/Customer")),
            GraphTriple(shape, Iri(SH_PROPERTY), propertyNode),
            GraphTriple(propertyNode, Iri(SH_PATH), path),
            GraphTriple(propertyNode, Iri(SH_MIN_COUNT), RdfLiteral("1", Iri(XSD_INTEGER))),
        ),
    )

    private companion object {
        const val RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
        const val SH_NODE_SHAPE = "http://www.w3.org/ns/shacl#NodeShape"
        const val SH_TARGET_CLASS = "http://www.w3.org/ns/shacl#targetClass"
        const val SH_PROPERTY = "http://www.w3.org/ns/shacl#property"
        const val SH_PATH = "http://www.w3.org/ns/shacl#path"
        const val SH_MIN_COUNT = "http://www.w3.org/ns/shacl#minCount"
        const val XSD_INTEGER = "http://www.w3.org/2001/XMLSchema#integer"
    }
}
