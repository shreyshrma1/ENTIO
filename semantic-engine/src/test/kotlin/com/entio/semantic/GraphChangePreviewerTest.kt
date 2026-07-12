package com.entio.semantic

import com.entio.core.ChangePreview
import com.entio.core.ChangeSet
import com.entio.core.EntioResult
import com.entio.core.GraphChange
import com.entio.core.GraphChangeKind
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class GraphChangePreviewerTest {
    private val previewer = GraphChangePreviewer()

    @Test
    fun addsTripleToPreviewGraphWithoutMutatingOriginalGraph(): Unit {
        val existingTriple = typeTriple("Customer")
        val addedTriple = labelTriple("Customer", "Customer")
        val currentGraph = GraphState(triples = setOf(existingTriple))
        val changeSet = ChangeSet(
            changes = listOf(
                GraphChange(kind = GraphChangeKind.Addition, triple = addedTriple),
            ),
        )

        val result = previewer.preview(currentGraph, changeSet)

        val preview = assertIs<EntioResult.Success<ChangePreview>>(result).value
        assertEquals(setOf(existingTriple, addedTriple), preview.graph.triples)
        assertEquals(setOf(existingTriple), currentGraph.triples)
        assertFalse(currentGraph.triples.contains(addedTriple))
    }

    @Test
    fun removesExistingTripleFromPreviewGraph(): Unit {
        val removedTriple = labelTriple("Customer", "Customer")
        val retainedTriple = typeTriple("Customer")
        val currentGraph = GraphState(triples = setOf(removedTriple, retainedTriple))
        val changeSet = ChangeSet(
            changes = listOf(
                GraphChange(kind = GraphChangeKind.Removal, triple = removedTriple),
            ),
        )

        val result = previewer.preview(currentGraph, changeSet)

        val preview = assertIs<EntioResult.Success<ChangePreview>>(result).value
        assertEquals(setOf(retainedTriple), preview.graph.triples)
        assertEquals(setOf(removedTriple, retainedTriple), currentGraph.triples)
    }

    @Test
    fun detectsDuplicateAdditionAgainstCurrentGraph(): Unit {
        val existingTriple = typeTriple("Customer")
        val currentGraph = GraphState(triples = setOf(existingTriple))
        val changeSet = ChangeSet(
            changes = listOf(
                GraphChange(kind = GraphChangeKind.Addition, triple = existingTriple),
            ),
        )

        val failure = assertIs<EntioResult.Failure>(previewer.preview(currentGraph, changeSet))

        assertEquals("Graph change preview failed.", failure.message)
        assertEquals("duplicate-triple-addition", failure.issues.single().code)
        assertEquals("changeSet.changes[0]", failure.issues.single().source)
        assertEquals(setOf(existingTriple), currentGraph.triples)
    }

    @Test
    fun detectsDuplicateAdditionWithinChangeSet(): Unit {
        val addedTriple = typeTriple("Customer")
        val currentGraph = GraphState()
        val changeSet = ChangeSet(
            changes = listOf(
                GraphChange(kind = GraphChangeKind.Addition, triple = addedTriple),
                GraphChange(kind = GraphChangeKind.Addition, triple = addedTriple),
            ),
        )

        val failure = assertIs<EntioResult.Failure>(previewer.preview(currentGraph, changeSet))

        assertEquals("duplicate-triple-addition", failure.issues.single().code)
        assertEquals("changeSet.changes[1]", failure.issues.single().source)
    }

    @Test
    fun detectsMissingRemoval(): Unit {
        val missingTriple = typeTriple("Customer")
        val changeSet = ChangeSet(
            changes = listOf(
                GraphChange(kind = GraphChangeKind.Removal, triple = missingTriple),
            ),
        )

        val failure = assertIs<EntioResult.Failure>(previewer.preview(GraphState(), changeSet))

        assertEquals("missing-triple-removal", failure.issues.single().code)
        assertEquals("changeSet.changes[0]", failure.issues.single().source)
    }

    @Test
    fun reportsIssuesDeterministicallyInChangeOrder(): Unit {
        val firstMissing = typeTriple("Customer")
        val secondMissing = labelTriple("Account", "Account")
        val changeSet = ChangeSet(
            changes = listOf(
                GraphChange(kind = GraphChangeKind.Removal, triple = firstMissing),
                GraphChange(kind = GraphChangeKind.Removal, triple = secondMissing),
            ),
        )

        val first = assertIs<EntioResult.Failure>(previewer.preview(GraphState(), changeSet))
        val second = assertIs<EntioResult.Failure>(previewer.preview(GraphState(), changeSet))

        assertEquals(
            listOf("changeSet.changes[0]", "changeSet.changes[1]"),
            first.issues.map { it.source },
        )
        assertEquals(first.issues, second.issues)
    }

    private fun typeTriple(localName: String): GraphTriple =
        GraphTriple(
            subject = Iri("https://example.com/$localName"),
            predicate = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
            objectTerm = Iri("http://www.w3.org/2000/01/rdf-schema#Class"),
        )

    private fun labelTriple(localName: String, label: String): GraphTriple =
        GraphTriple(
            subject = Iri("https://example.com/$localName"),
            predicate = Iri("http://www.w3.org/2000/01/rdf-schema#label"),
            objectTerm = RdfLiteral(label),
        )
}
