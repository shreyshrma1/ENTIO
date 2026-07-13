package com.entio.diff

import com.entio.core.ChangeProposal
import com.entio.core.ChangeProposalStatus
import com.entio.core.CombinedProposalMetadata
import com.entio.core.CombinedProposalPreview
import com.entio.core.CombinedProposalStatus
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.NormalizedStagedChangeSet
import com.entio.core.SemanticEquivalenceResult
import com.entio.core.StagedValidationAttribution
import com.entio.core.StagedChangeSet
import com.entio.core.ValidationIssue
import com.entio.core.ValidationReport
import com.entio.core.ValidationSeverity
import com.entio.core.ValidationStatus
import com.entio.semantic.PreviewTurtleRoundTripVerifier
import com.entio.semantic.ProjectConfigLoader
import com.entio.semantic.ProposalCreator
import com.entio.semantic.StagedChangeSetNormalizer
import com.entio.validation.ProposalValidator

/** Prepares one deterministic combined proposal entirely in memory. */
public class CombinedPreviewService(
    private val normalizer: StagedChangeSetNormalizer = StagedChangeSetNormalizer(),
    private val proposalCreator: ProposalCreator = ProposalCreator(),
    private val proposalValidator: ProposalValidator = ProposalValidator(),
    private val graphDiffer: GraphDiffer = GraphDiffer(),
    private val roundTripVerifier: PreviewTurtleRoundTripVerifier = PreviewTurtleRoundTripVerifier(),
) {
    public fun preview(
        project: EntioProject,
        stagedChangeSet: StagedChangeSet,
        proposalId: String,
        expectedBaseline: com.entio.core.ProposalBaseline? = null,
    ): EntioResult<CombinedProposalPreview> {
        val normalized = when (val result = normalizer.normalize(stagedChangeSet)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        if (normalized.entries.isEmpty()) {
            return failure("empty-staged-change-set", "The staged change set is empty.")
        }
        if (normalized.conflicts.isNotEmpty()) {
            return EntioResult.Success(
                CombinedProposalPreview(
                    metadata = metadata(normalized, proposalId, CombinedProposalStatus.Conflicted),
                    changeSet = normalized.changeSet,
                    validationReport = invalidReport(normalized.conflicts.map { conflict ->
                        ValidationIssue(
                            severity = ValidationSeverity.Error,
                            code = "staged-conflict",
                            message = conflict.message,
                            source = conflict.stagedChangeIds.joinToString(","),
                        )
                    }),
                ),
            )
        }

        val changeSet = normalized.changeSet ?: return failure("empty-normalized-change-set", "No graph changes remain after normalization.")
        val sourceIds = normalized.entries.map { it.targetSourceId }.distinct()
        if (sourceIds.size != 1) {
            return failure("multiple-target-sources", "A combined preview must target exactly one ontology source.")
        }
        val proposal = when (val result = proposalCreator.createProposal(project, sourceIds.single(), changeSet, proposalId, "Combined staged changes")) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        if (expectedBaseline != null && proposal.baseline != expectedBaseline) {
            return failure("stale-proposal-baseline", "The staged change set is based on an outdated project.")
        }

        val validation = proposalValidator.validateProposal(proposal, project)
        val diff = proposal.preview?.let { graphDiffer.diff(project.graph, it.graph) }
        val equivalence = proposal.preview?.let { roundTripVerifier.verify(it) }
        val equivalenceValue = when (equivalence) {
            null -> null
            is EntioResult.Failure -> SemanticEquivalenceResult.Failed(equivalence.message)
            is EntioResult.Success -> equivalence.value
        }
        val metadata = metadata(
            normalized = normalized,
            proposalId = proposalId,
            status = if (validation.ok && equivalenceValue is SemanticEquivalenceResult.Equivalent) {
                CombinedProposalStatus.ReadyForReview
            } else {
                CombinedProposalStatus.Invalid
            },
            baseline = proposal.baseline,
            validation = validation,
        )
        return EntioResult.Success(
            CombinedProposalPreview(
                metadata = metadata,
                changeSet = changeSet,
                preview = proposal.preview,
                diff = diff,
                validationReport = validation,
                equivalence = equivalenceValue,
            ),
        )
    }

    private fun metadata(
        normalized: NormalizedStagedChangeSet,
        proposalId: String,
        status: CombinedProposalStatus,
        baseline: com.entio.core.ProposalBaseline? = null,
        validation: ValidationReport? = null,
    ): CombinedProposalMetadata {
        val attribution = validation?.issues.orEmpty()
            .mapNotNull { issue ->
                val source = issue.source ?: return@mapNotNull null
                val id = source.substringAfter("changeSet.changes[", "").substringBefore(']').toIntOrNull()
                    ?: return@mapNotNull null
                normalized.entries.getOrNull(id)?.id?.let { stagedId ->
                    StagedValidationAttribution(stagedId, listOf(issue.code))
                }
            }
            .groupBy(StagedValidationAttribution::stagedChangeId)
            .map { (id, entries) -> StagedValidationAttribution(id, entries.flatMap { it.issueCodes }.distinct()) }
        return CombinedProposalMetadata(
            proposalId = proposalId,
            stagedChangeIds = normalized.entries.map { it.id },
            targetSourceIds = normalized.entries.map { it.targetSourceId }.distinct(),
            status = status,
            conflicts = normalized.conflicts,
            validationAttribution = attribution,
            baseline = baseline,
        )
    }

    private fun invalidReport(issues: List<ValidationIssue>): ValidationReport =
        ValidationReport(ValidationStatus.Invalid, issues)

    private fun failure(code: String, message: String): EntioResult.Failure =
        EntioResult.Failure(
            message = message,
            issues = listOf(ValidationIssue(ValidationSeverity.Error, code, message)),
        )
}
