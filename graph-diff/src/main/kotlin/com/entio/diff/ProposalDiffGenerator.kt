package com.entio.diff

import com.entio.core.ChangeProposal
import com.entio.core.EntioResult
import com.entio.core.GraphState
import com.entio.core.SemanticDiff
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity

public class ProposalDiffGenerator(
    private val graphDiffer: GraphDiffer = GraphDiffer(),
) {
    public fun diffProposal(
        proposal: ChangeProposal,
        currentGraph: GraphState,
    ): EntioResult<SemanticDiff> {
        val preview = proposal.preview
            ?: return EntioResult.Failure(
                message = "Proposal semantic diff could not be generated.",
                issues = listOf(
                    ValidationIssue(
                        severity = ValidationSeverity.Error,
                        code = "missing-proposal-preview",
                        message = "Proposal must include a preview graph before semantic diff generation.",
                        source = proposal.id,
                    ),
                ),
            )

        return EntioResult.Success(
            graphDiffer.diff(
                before = currentGraph,
                after = preview.graph,
            ),
        )
    }

    public fun attachDiff(
        proposal: ChangeProposal,
        currentGraph: GraphState,
    ): EntioResult<ChangeProposal> =
        when (val result = diffProposal(proposal, currentGraph)) {
            is EntioResult.Failure -> result
            is EntioResult.Success -> EntioResult.Success(
                proposal.copy(diff = result.value),
            )
        }
}
