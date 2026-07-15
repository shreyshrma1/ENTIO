package com.entio.diff

import com.entio.core.ChangeSet
import com.entio.core.EntioResult
import com.entio.core.GraphChange
import com.entio.core.GraphChangeKind
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.core.CombinedProposalStatus
import com.entio.core.ValidationStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CombinedProposalPreviewServiceTest {
    @Test
    fun previewsWithoutMutationAndKeepsImpactSectionsSeparate(): Unit {
        val originalTriple = GraphTriple(Iri("https://example.com/Account"), Iri("https://example.com/label"), RdfLiteral("Account"))
        val addition = GraphTriple(Iri("https://example.com/Account"), Iri("https://example.com/status"), RdfLiteral("active"))
        val current = GraphState(setOf(originalTriple))
        val result = assertIs<EntioResult.Success<com.entio.core.CombinedProposalPreview>>(
            CombinedProposalPreviewService().preview(
                proposalId = "proposal-1",
                stagedChangeIds = listOf("change-1"),
                targetSourceIds = listOf("data", "shapes"),
                currentGraph = current,
                changeSet = ChangeSet(listOf(GraphChange(GraphChangeKind.Addition, addition))),
            ),
        ).value

        assertEquals(setOf(originalTriple), current.triples)
        assertEquals(CombinedProposalStatus.ReadyForReview, result.metadata.status)
        assertEquals(ValidationStatus.Valid, result.validationReport.status)
        assertEquals(1, result.diff?.entries?.size)
        assertTrue(result.preview?.graph?.triples?.contains(addition) == true)
        assertTrue(result.metadata.targetSourceIds == listOf("data", "shapes"))
    }
}
