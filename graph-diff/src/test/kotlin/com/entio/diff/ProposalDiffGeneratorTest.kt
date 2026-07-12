package com.entio.diff

import com.entio.core.ChangePreview
import com.entio.core.ChangeProposal
import com.entio.core.ChangeProposalStatus
import com.entio.core.ChangeSet
import com.entio.core.EntioResult
import com.entio.core.GraphChange
import com.entio.core.GraphChangeKind
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.ProposalBaseline
import com.entio.core.RdfLiteral
import com.entio.core.SemanticDiff
import com.entio.core.SemanticDiffKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProposalDiffGeneratorTest {
    private val generator = ProposalDiffGenerator()
    private val formatter = SemanticDiffFormatter()

    @Test
    fun proposalDiffReportsAddedTriples(): Unit {
        val currentGraph = graph(typeTriple("Customer"))
        val addedTriple = labelTriple("Customer", "Customer")
        val proposal = proposal(
            currentGraph = currentGraph,
            previewGraph = graph(typeTriple("Customer"), addedTriple),
            changes = listOf(GraphChange(GraphChangeKind.Addition, addedTriple)),
        )

        val diff = diffProposal(proposal, currentGraph)

        assertEquals(1, diff.entries.size)
        assertEquals(SemanticDiffKind.Added, diff.entries.single().kind)
        assertEquals("https://example.com/Customer", diff.entries.single().subject.value)
        assertEquals(RDFS_LABEL, diff.entries.single().predicate?.value)
        assertEquals("Customer", diff.entries.single().objectValue)
        assertEquals(
            "Added triple (https://example.com/Customer, $RDFS_LABEL, \"Customer\").",
            diff.entries.single().description,
        )
    }

    @Test
    fun proposalDiffReportsRemovedTriples(): Unit {
        val removedTriple = labelTriple("Customer", "Customer")
        val currentGraph = graph(typeTriple("Customer"), removedTriple)
        val proposal = proposal(
            currentGraph = currentGraph,
            previewGraph = graph(typeTriple("Customer")),
            changes = listOf(GraphChange(GraphChangeKind.Removal, removedTriple)),
        )

        val diff = diffProposal(proposal, currentGraph)

        assertEquals(1, diff.entries.size)
        assertEquals(SemanticDiffKind.Removed, diff.entries.single().kind)
        assertEquals(
            "Removed triple (https://example.com/Customer, $RDFS_LABEL, \"Customer\").",
            diff.entries.single().description,
        )
    }

    @Test
    fun proposalDiffPreservesLabelChangeBehavior(): Unit {
        val oldLabel = labelTriple("Customer", "Customer")
        val newLabel = labelTriple("Customer", "Client")
        val currentGraph = graph(typeTriple("Customer"), oldLabel)
        val proposal = proposal(
            currentGraph = currentGraph,
            previewGraph = graph(typeTriple("Customer"), newLabel),
            changes = listOf(
                GraphChange(GraphChangeKind.Removal, oldLabel),
                GraphChange(GraphChangeKind.Addition, newLabel),
            ),
        )

        val diff = diffProposal(proposal, currentGraph)

        assertEquals(1, diff.entries.size)
        assertEquals(SemanticDiffKind.Changed, diff.entries.single().kind)
        assertEquals("Customer -> Client", diff.entries.single().objectValue)
        assertEquals(
            "Changed label for https://example.com/Customer from \"Customer\" to \"Client\".",
            diff.entries.single().description,
        )
    }

    @Test
    fun proposalDiffFormattingIsDeterministic(): Unit {
        val currentGraph = graph(typeTriple("Zeta"))
        val alpha = labelTriple("Alpha", "Alpha")
        val beta = labelTriple("Beta", "Beta")
        val proposal = proposal(
            currentGraph = currentGraph,
            previewGraph = graph(typeTriple("Zeta"), beta, alpha),
            changes = listOf(
                GraphChange(GraphChangeKind.Addition, beta),
                GraphChange(GraphChangeKind.Addition, alpha),
            ),
        )

        val first = diffProposal(proposal, currentGraph)
        val second = diffProposal(proposal, currentGraph)

        assertEquals(first, second)
        assertEquals(
            "Added triple (https://example.com/Alpha, $RDFS_LABEL, \"Alpha\").\n" +
                "Added triple (https://example.com/Beta, $RDFS_LABEL, \"Beta\").",
            formatter.format(first),
        )
    }

    @Test
    fun attachDiffReturnsProposalWithDiff(): Unit {
        val currentGraph = graph(typeTriple("Customer"))
        val addedTriple = labelTriple("Customer", "Customer")
        val proposal = proposal(
            currentGraph = currentGraph,
            previewGraph = graph(typeTriple("Customer"), addedTriple),
            changes = listOf(GraphChange(GraphChangeKind.Addition, addedTriple)),
        )

        val updated = assertIs<EntioResult.Success<ChangeProposal>>(
            generator.attachDiff(proposal, currentGraph),
        ).value

        assertEquals(SemanticDiffKind.Added, updated.diff?.entries?.single()?.kind)
        assertEquals(null, proposal.diff)
    }

    @Test
    fun missingProposalPreviewReturnsStructuredFailure(): Unit {
        val proposal = proposal(
            currentGraph = graph(typeTriple("Customer")),
            previewGraph = graph(typeTriple("Customer")),
        ).copy(preview = null)

        val failure = assertIs<EntioResult.Failure>(
            generator.diffProposal(proposal, graph(typeTriple("Customer"))),
        )

        assertEquals("Proposal semantic diff could not be generated.", failure.message)
        assertEquals("missing-proposal-preview", failure.issues.single().code)
        assertEquals(proposal.id, failure.issues.single().source)
    }

    private fun diffProposal(
        proposal: ChangeProposal,
        currentGraph: GraphState,
    ): SemanticDiff =
        assertIs<EntioResult.Success<SemanticDiff>>(
            generator.diffProposal(proposal, currentGraph),
        ).value

    private fun proposal(
        currentGraph: GraphState,
        previewGraph: GraphState,
        changes: List<GraphChange> = listOf(GraphChange(GraphChangeKind.Addition, labelTriple("Customer", "Customer"))),
    ): ChangeProposal {
        val changeSet = ChangeSet(changes = changes)

        return ChangeProposal(
            id = "proposal-1",
            title = "Preview semantic diff",
            targetSourceId = "simple",
            changeSet = changeSet,
            baseline = ProposalBaseline(
                projectFingerprint = "project-before",
                targetSourceId = "simple",
                targetSourcePath = "ontology/simple.ttl",
                targetSourceFingerprint = "source-before",
                graphFingerprint = currentGraph.triples.hashCode().toString(),
            ),
            status = ChangeProposalStatus.Previewed,
            preview = ChangePreview(
                graph = previewGraph,
                changeSet = changeSet,
            ),
        )
    }

    private fun graph(vararg triples: GraphTriple): GraphState =
        GraphState(triples = triples.toSet())

    private fun typeTriple(localName: String): GraphTriple =
        GraphTriple(
            subject = Iri("https://example.com/$localName"),
            predicate = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
            objectTerm = Iri("http://www.w3.org/2000/01/rdf-schema#Class"),
        )

    private fun labelTriple(localName: String, label: String): GraphTriple =
        GraphTriple(
            subject = Iri("https://example.com/$localName"),
            predicate = Iri(RDFS_LABEL),
            objectTerm = RdfLiteral(label),
        )

    private companion object {
        private const val RDFS_LABEL: String = "http://www.w3.org/2000/01/rdf-schema#label"
    }
}
