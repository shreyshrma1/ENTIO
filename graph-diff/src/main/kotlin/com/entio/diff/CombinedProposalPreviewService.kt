package com.entio.diff

import com.entio.core.BaselineImpactStatus
import com.entio.core.ChangeSet
import com.entio.core.CombinedProposalMetadata
import com.entio.core.CombinedProposalPreview
import com.entio.core.CombinedProposalStatus
import com.entio.core.ConsistencyStatus
import com.entio.core.EntioResult
import com.entio.core.GraphState
import com.entio.core.ReasoningResult
import com.entio.core.ShaclValidationReport
import com.entio.core.ValidationIssue
import com.entio.core.ValidationReport
import com.entio.core.ValidationSeverity
import com.entio.core.ValidationStatus
import com.entio.core.ProposalBaseline
import com.entio.semantic.GraphChangePreviewer

/** Builds a combined, non-mutating proposal preview and its separate impact sections. */
public class CombinedProposalPreviewService(
    private val previewer: GraphChangePreviewer = GraphChangePreviewer(),
    private val graphDiffer: GraphDiffer = GraphDiffer(),
    private val impactAnalyzer: ProposalImpactAnalyzer = ProposalImpactAnalyzer(),
) {
    public fun preview(
        proposalId: String,
        stagedChangeIds: List<String>,
        targetSourceIds: List<String>,
        currentGraph: GraphState,
        changeSet: ChangeSet,
        currentReasoning: ReasoningResult? = null,
        previewReasoning: ReasoningResult? = null,
        currentShacl: ShaclValidationReport? = null,
        previewShacl: ShaclValidationReport? = null,
        baseline: ProposalBaseline? = null,
    ): EntioResult<CombinedProposalPreview> {
        val preview = when (val result = previewer.preview(currentGraph, changeSet)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        val impact = when (val result = impactAnalyzer.analyze(currentGraph, preview.graph, currentReasoning, previewReasoning, currentShacl, previewShacl)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        val validation = ValidationReport(
            status = if (impact.status == BaselineImpactStatus.Safe) ValidationStatus.Valid else ValidationStatus.Invalid,
            issues = impact.blockingMessages.map { message ->
                ValidationIssue(ValidationSeverity.Error, "proposal-impact", message, proposalId)
            },
        )
        val status = when (impact.status) {
            BaselineImpactStatus.Safe -> CombinedProposalStatus.ReadyForReview
            BaselineImpactStatus.BlocksApproval -> CombinedProposalStatus.Invalid
            BaselineImpactStatus.Incomplete -> CombinedProposalStatus.Invalid
            BaselineImpactStatus.Failed -> CombinedProposalStatus.Failed
        }
        return EntioResult.Success(
            CombinedProposalPreview(
                metadata = CombinedProposalMetadata(
                    proposalId = proposalId,
                    stagedChangeIds = stagedChangeIds,
                    targetSourceIds = targetSourceIds.distinct().sorted(),
                    status = status,
                    baseline = baseline,
                ),
                changeSet = changeSet,
                preview = preview,
                diff = impact.explicitDiff,
                validationReport = validation,
            ),
        )
    }
}
