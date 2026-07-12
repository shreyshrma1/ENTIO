package com.entio.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SemanticDiffTest {
    @Test
    fun constructsDiffAndChangeProposalObjects(): Unit {
        val entry = SemanticDiffEntry(
            kind = SemanticDiffKind.Added,
            subject = Iri("https://example.com/Customer"),
            predicate = Iri("http://www.w3.org/2000/01/rdf-schema#label"),
            objectValue = "Customer",
            description = "Added Customer label.",
        )
        val diff = SemanticDiff(entries = listOf(entry))
        val triple = GraphTriple(
            subject = Iri("https://example.com/Customer"),
            predicate = Iri("http://www.w3.org/2000/01/rdf-schema#label"),
            objectTerm = RdfLiteral(lexicalForm = "Customer"),
        )
        val changeSet = ChangeSet(
            changes = listOf(
                GraphChange(
                    kind = GraphChangeKind.Addition,
                    triple = triple,
                ),
            ),
        )
        val baseline = ProposalBaseline(
            projectFingerprint = "project-before",
            targetSourceId = "simple",
            targetSourcePath = "ontology/simple.ttl",
            targetSourceFingerprint = "source-before",
            graphFingerprint = "graph-before",
        )
        val proposal = ChangeProposal(
            id = "proposal-1",
            title = "Add Customer label",
            targetSourceId = "simple",
            changeSet = changeSet,
            baseline = baseline,
            status = ChangeProposalStatus.Draft,
            diff = diff,
        )

        assertEquals(listOf(entry), proposal.diff?.entries)
        assertEquals(ChangeProposalStatus.Draft, proposal.status)
    }
}
