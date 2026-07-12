package com.entio.validation

import com.entio.core.ChangeProposal
import com.entio.core.ChangeProposalStatus
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.SemanticEquivalenceResult
import com.entio.core.ValidationIssue
import com.entio.core.ValidationReport
import com.entio.core.ValidationSeverity
import com.entio.core.ValidationStatus
import com.entio.semantic.GraphChangePreviewer
import com.entio.semantic.ProposalCreator

public class ProposalValidator(
    private val previewer: GraphChangePreviewer = GraphChangePreviewer(),
    private val proposalCreator: ProposalCreator = ProposalCreator(),
    private val issueSorter: ValidationIssueSorter = ValidationIssueSorter(),
) {
    public fun validateProposal(
        proposal: ChangeProposal,
        currentProject: EntioProject,
        projectValidationReport: ValidationReport = validReport(),
        semanticEquivalenceResult: SemanticEquivalenceResult? = null,
    ): ValidationReport {
        val issues = mutableListOf<ValidationIssue>()

        issues += projectValidationReport.issues
        issues += validateTargetSource(proposal, currentProject)
        issues += validatePreview(proposal, currentProject)
        issues += validateCurrentBaseline(proposal, currentProject)
        issues += validateSemanticEquivalence(proposal, semanticEquivalenceResult)

        return report(issues)
    }

    private fun validateTargetSource(
        proposal: ChangeProposal,
        currentProject: EntioProject,
    ): List<ValidationIssue> {
        val targetSourceExists = currentProject.resolvedSources.any { source -> source.id == proposal.targetSourceId }

        return if (targetSourceExists) {
            emptyList()
        } else {
            listOf(
                ValidationIssue(
                    severity = ValidationSeverity.Error,
                    code = "missing-target-source",
                    message = "Target ontology source '${proposal.targetSourceId}' was not found.",
                    source = proposal.targetSourceId,
                ),
            )
        }
    }

    private fun validatePreview(
        proposal: ChangeProposal,
        currentProject: EntioProject,
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()

        if (proposal.preview == null) {
            issues += ValidationIssue(
                severity = ValidationSeverity.Error,
                code = "missing-proposal-preview",
                message = "Proposal must include a preview graph before validation.",
                source = proposal.id,
            )
        }

        when (val previewResult = previewer.preview(currentProject.graph, proposal.changeSet)) {
            is EntioResult.Failure -> issues += previewResult.issues
            is EntioResult.Success -> Unit
        }

        return issues
    }

    private fun validateCurrentBaseline(
        proposal: ChangeProposal,
        currentProject: EntioProject,
    ): List<ValidationIssue> {
        if (currentProject.resolvedSources.none { source -> source.id == proposal.targetSourceId }) {
            return emptyList()
        }

        return when (val result = proposalCreator.isCurrent(proposal, currentProject)) {
            is EntioResult.Failure -> result.issues
            is EntioResult.Success -> if (result.value) {
                emptyList()
            } else {
                listOf(
                    ValidationIssue(
                        severity = ValidationSeverity.Error,
                        code = "stale-proposal-baseline",
                        message = "Proposal baseline no longer matches the current project state.",
                        source = proposal.id,
                    ),
                )
            }
        }
    }

    private fun validateSemanticEquivalence(
        proposal: ChangeProposal,
        result: SemanticEquivalenceResult?,
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()

        if (proposal.status == ChangeProposalStatus.VerificationFailed) {
            issues += ValidationIssue(
                severity = ValidationSeverity.Error,
                code = "proposal-verification-failed",
                message = "Proposal is marked as verification failed.",
                source = proposal.id,
            )
        }

        when (result) {
            null,
            SemanticEquivalenceResult.Equivalent,
            -> Unit
            is SemanticEquivalenceResult.NotEquivalent -> issues += ValidationIssue(
                severity = ValidationSeverity.Error,
                code = "semantic-equivalence-failed",
                message = result.reason,
                source = proposal.id,
            )
            is SemanticEquivalenceResult.Failed -> issues += ValidationIssue(
                severity = ValidationSeverity.Error,
                code = "semantic-equivalence-check-failed",
                message = result.reason,
                source = proposal.id,
            )
        }

        return issues
    }

    private fun report(issues: List<ValidationIssue>): ValidationReport {
        val sortedIssues = issueSorter.sortIssues(issues)
        val status = if (sortedIssues.any { issue -> issue.severity == ValidationSeverity.Error }) {
            ValidationStatus.Invalid
        } else {
            ValidationStatus.Valid
        }

        return ValidationReport(
            status = status,
            issues = sortedIssues,
        )
    }

    private companion object {
        private fun validReport(): ValidationReport =
            ValidationReport(
                status = ValidationStatus.Valid,
                issues = emptyList(),
            )
    }
}
