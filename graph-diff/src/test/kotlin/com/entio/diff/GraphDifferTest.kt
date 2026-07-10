package com.entio.diff

import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.SemanticDiffKind
import kotlin.test.Test
import kotlin.test.assertEquals

class GraphDifferTest {
    private val differ = GraphDiffer()

    @Test
    fun identicalGraphsReturnEmptyDiff(): Unit {
        val graph = graph(
            triple(
                subject = "https://example.com/Customer",
                predicate = RDFS_LABEL,
                objectValue = "Customer",
            ),
        )

        val diff = differ.diff(graph, graph)

        assertEquals(emptyList(), diff.entries)
    }

    @Test
    fun reportsAddedTriple(): Unit {
        val added = triple(
            subject = "https://example.com/Customer",
            predicate = RDFS_LABEL,
            objectValue = "Customer",
        )

        val diff = differ.diff(
            before = graph(),
            after = graph(added),
        )

        assertEquals(1, diff.entries.size)
        assertEquals(SemanticDiffKind.Added, diff.entries.single().kind)
        assertEquals(added.subject, diff.entries.single().subject)
        assertEquals(added.predicate, diff.entries.single().predicate)
        assertEquals(added.objectValue, diff.entries.single().objectValue)
    }

    @Test
    fun reportsRemovedTriple(): Unit {
        val removed = triple(
            subject = "https://example.com/Customer",
            predicate = RDFS_LABEL,
            objectValue = "Customer",
        )

        val diff = differ.diff(
            before = graph(removed),
            after = graph(),
        )

        assertEquals(1, diff.entries.size)
        assertEquals(SemanticDiffKind.Removed, diff.entries.single().kind)
        assertEquals(removed.subject, diff.entries.single().subject)
        assertEquals(removed.predicate, diff.entries.single().predicate)
        assertEquals(removed.objectValue, diff.entries.single().objectValue)
    }

    @Test
    fun reportsLabelChangeAsChangedEntry(): Unit {
        val diff = differ.diff(
            before = graph(
                triple(
                    subject = "https://example.com/Customer",
                    predicate = RDFS_LABEL,
                    objectValue = "Customer",
                ),
            ),
            after = graph(
                triple(
                    subject = "https://example.com/Customer",
                    predicate = RDFS_LABEL,
                    objectValue = "Client",
                ),
            ),
        )

        assertEquals(1, diff.entries.size)
        assertEquals(SemanticDiffKind.Changed, diff.entries.single().kind)
        assertEquals("Customer -> Client", diff.entries.single().objectValue)
    }

    @Test
    fun returnsEntriesInStableOrder(): Unit {
        val diff = differ.diff(
            before = graph(
                triple("https://example.com/Zeta", RDFS_LABEL, "Zeta"),
            ),
            after = graph(
                triple("https://example.com/Alpha", RDFS_LABEL, "Alpha"),
                triple("https://example.com/Beta", RDFS_LABEL, "Beta"),
            ),
        )

        assertEquals(
            listOf(
                "https://example.com/Alpha",
                "https://example.com/Beta",
                "https://example.com/Zeta",
            ),
            diff.entries.map { it.subject.value },
        )
    }

    private fun graph(vararg triples: GraphTriple): GraphState =
        GraphState(triples = triples.toSet())

    private fun triple(
        subject: String,
        predicate: String,
        objectValue: String,
    ): GraphTriple =
        GraphTriple(
            subject = Iri(subject),
            predicate = Iri(predicate),
            objectValue = objectValue,
        )

    private companion object {
        private const val RDFS_LABEL: String = "http://www.w3.org/2000/01/rdf-schema#label"
    }
}
