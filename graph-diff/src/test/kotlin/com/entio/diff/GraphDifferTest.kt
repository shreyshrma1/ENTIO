package com.entio.diff

import com.entio.core.BlankNodeResource
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.RdfTerm
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
    fun reportsAddedTripleWithIriObject(): Unit {
        val added = triple(
            subject = Iri("https://example.com/Customer"),
            predicate = "https://example.com/relatedTo",
            objectTerm = Iri("https://example.com/Account"),
        )

        val diff = differ.diff(
            before = graph(),
            after = graph(added),
        )

        assertEquals(1, diff.entries.size)
        assertEquals(SemanticDiffKind.Added, diff.entries.single().kind)
        assertEquals("https://example.com/Account", diff.entries.single().objectValue)
        assertEquals(
            "Added triple (https://example.com/Customer, https://example.com/relatedTo, https://example.com/Account).",
            diff.entries.single().description,
        )
    }

    @Test
    fun reportsAddedTripleWithLiteralObject(): Unit {
        val added = triple(
            subject = Iri("https://example.com/Customer"),
            predicate = "https://example.com/score",
            objectTerm = RdfLiteral(
                lexicalForm = "42",
                datatypeIri = Iri("http://www.w3.org/2001/XMLSchema#integer"),
            ),
        )

        val diff = differ.diff(
            before = graph(),
            after = graph(added),
        )

        assertEquals(1, diff.entries.size)
        assertEquals(SemanticDiffKind.Added, diff.entries.single().kind)
        assertEquals("42", diff.entries.single().objectValue)
        assertEquals(
            "Added triple (https://example.com/Customer, https://example.com/score, \"42\"^^http://www.w3.org/2001/XMLSchema#integer).",
            diff.entries.single().description,
        )
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
        assertEquals(
            "Changed label for https://example.com/Customer from \"Customer\" to \"Client\".",
            diff.entries.single().description,
        )
    }

    @Test
    fun doesNotTreatNonLiteralLabelsAsLabelChanges(): Unit {
        val diff = differ.diff(
            before = graph(
                triple(
                    subject = Iri("https://example.com/Customer"),
                    predicate = RDFS_LABEL,
                    objectTerm = Iri("https://example.com/CustomerLabel"),
                ),
            ),
            after = graph(
                triple(
                    subject = Iri("https://example.com/Customer"),
                    predicate = RDFS_LABEL,
                    objectTerm = Iri("https://example.com/ClientLabel"),
                ),
            ),
        )

        assertEquals(
            listOf(SemanticDiffKind.Added, SemanticDiffKind.Removed),
            diff.entries.map { it.kind },
        )
    }

    @Test
    fun formatsBlankNodeContainingTriples(): Unit {
        val added = triple(
            subject = BlankNodeResource(id = "b0"),
            predicate = "https://example.com/detail",
            objectTerm = BlankNodeResource(id = "b1"),
        )

        val diff = differ.diff(
            before = graph(),
            after = graph(added),
        )

        assertEquals(1, diff.entries.size)
        assertEquals(SemanticDiffKind.Added, diff.entries.single().kind)
        assertEquals("_:b1", diff.entries.single().objectValue)
        assertEquals(
            "Added triple (blank node (_:b0), https://example.com/detail, blank node (_:b1)).",
            diff.entries.single().description,
        )
    }

    @Test
    fun describesShaclChangesWithReviewableVocabulary(): Unit {
        val added = triple(
            subject = BlankNodeResource("property-shape"),
            predicate = "http://www.w3.org/ns/shacl#minCount",
            objectTerm = RdfLiteral("1", Iri("http://www.w3.org/2001/XMLSchema#integer")),
        )

        val diff = differ.diff(graph(), graph(added))

        assertEquals(
            "Added SHACL minimum count for property shape with value \"1\"^^http://www.w3.org/2001/XMLSchema#integer.",
            diff.entries.single().description,
        )
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

    @Test
    fun returnsEntriesInStableOrderUnderMixedRdfTermTypes(): Unit {
        val first = differ.diff(
            before = graph(),
            after = graph(
                triple(
                    subject = Iri("https://example.com/Alpha"),
                    predicate = "https://example.com/value",
                    objectTerm = BlankNodeResource(id = "b1"),
                ),
                triple(
                    subject = BlankNodeResource(id = "b0"),
                    predicate = "https://example.com/value",
                    objectTerm = RdfLiteral(lexicalForm = "blank subject"),
                ),
                triple(
                    subject = Iri("https://example.com/Alpha"),
                    predicate = "https://example.com/value",
                    objectTerm = Iri("https://example.com/Beta"),
                ),
                triple(
                    subject = Iri("https://example.com/Alpha"),
                    predicate = "https://example.com/value",
                    objectTerm = RdfLiteral(lexicalForm = "42"),
                ),
            ),
        )
        val second = differ.diff(
            before = graph(),
            after = graph(
                triple(
                    subject = Iri("https://example.com/Alpha"),
                    predicate = "https://example.com/value",
                    objectTerm = Iri("https://example.com/Beta"),
                ),
                triple(
                    subject = Iri("https://example.com/Alpha"),
                    predicate = "https://example.com/value",
                    objectTerm = RdfLiteral(lexicalForm = "42"),
                ),
                triple(
                    subject = BlankNodeResource(id = "b0"),
                    predicate = "https://example.com/value",
                    objectTerm = RdfLiteral(lexicalForm = "blank subject"),
                ),
                triple(
                    subject = Iri("https://example.com/Alpha"),
                    predicate = "https://example.com/value",
                    objectTerm = BlankNodeResource(id = "b1"),
                ),
            ),
        )

        assertEquals(first.entries.map { it.description }, second.entries.map { it.description })
        assertEquals(
            listOf(
                "Added triple (blank node (_:b0), https://example.com/value, \"blank subject\").",
                "Added triple (https://example.com/Alpha, https://example.com/value, \"42\").",
                "Added triple (https://example.com/Alpha, https://example.com/value, blank node (_:b1)).",
                "Added triple (https://example.com/Alpha, https://example.com/value, https://example.com/Beta).",
            ),
            first.entries.map { it.description },
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

    private fun triple(
        subject: RdfResource,
        predicate: String,
        objectTerm: RdfTerm,
    ): GraphTriple =
        GraphTriple(
            subject = subject,
            predicate = Iri(predicate),
            objectTerm = objectTerm,
        )

    private companion object {
        private const val RDFS_LABEL: String = "http://www.w3.org/2000/01/rdf-schema#label"
    }
}
