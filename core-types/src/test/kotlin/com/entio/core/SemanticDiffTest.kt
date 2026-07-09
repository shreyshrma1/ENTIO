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
        val proposal = ChangeProposal(
            id = "proposal-1",
            title = "Add Customer label",
            status = ChangeProposalStatus.Draft,
            diff = diff,
        )

        assertEquals(listOf(entry), proposal.diff.entries)
        assertEquals(ChangeProposalStatus.Draft, proposal.status)
    }
}
