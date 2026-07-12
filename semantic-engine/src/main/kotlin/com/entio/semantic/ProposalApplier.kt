package com.entio.semantic

import com.entio.core.ApplyProposalResult
import com.entio.core.ChangePreview
import com.entio.core.ChangeProposal
import com.entio.core.ChangeProposalStatus
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.GraphState
import com.entio.core.ResolvedOntologySource
import com.entio.core.RollbackResult
import com.entio.core.SemanticEquivalenceResult
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

public class ProposalApplier(
    private val loadProject: (Path) -> EntioResult<EntioProject> = ProjectLoader()::loadProject,
    private val isCurrent: (ChangeProposal, EntioProject) -> EntioResult<Boolean> = ProposalCreator()::isCurrent,
    private val verifyPreview: (ChangePreview) -> EntioResult<SemanticEquivalenceResult> =
        PreviewTurtleRoundTripVerifier()::verify,
    private val serializePreview: (ChangePreview) -> EntioResult<ResolvedOntologySource> =
        PreviewTurtleRoundTripVerifier()::serializeToTemporaryTurtle,
    private val compareGraphs: (GraphState, GraphState) -> SemanticEquivalenceResult =
        PreviewTurtleRoundTripVerifier()::compareSemanticEquivalence,
    private val restoreSource: (Path, ByteArray) -> RollbackResult = ::restoreOriginalSource,
) {
    public fun applyProposal(
        projectRoot: Path,
        proposal: ChangeProposal,
    ): ApplyProposalResult {
        if (proposal.status != ChangeProposalStatus.Approved) {
            return failed(
                proposal = proposal,
                reason = "Only approved proposals can be applied.",
                rollback = RollbackResult.NotRequired,
            )
        }

        val preview = proposal.preview
            ?: return failed(
                proposal = proposal,
                reason = "Approved proposal '${proposal.id}' does not include a preview graph.",
                rollback = RollbackResult.NotRequired,
            )

        val currentProject = when (val result = loadProject(projectRoot)) {
            is EntioResult.Failure -> return failed(proposal, result.message, RollbackResult.NotRequired)
            is EntioResult.Success -> result.value
        }

        val targetSource = currentProject.resolvedSources.firstOrNull { source -> source.id == proposal.targetSourceId }
            ?: return failed(
                proposal = proposal,
                reason = "Target ontology source '${proposal.targetSourceId}' was not found.",
                rollback = RollbackResult.NotRequired,
            )

        when (val current = isCurrent(proposal, currentProject)) {
            is EntioResult.Failure -> return failed(proposal, current.message, RollbackResult.NotRequired)
            is EntioResult.Success -> if (!current.value) {
                return failed(
                    proposal = proposal,
                    reason = "Proposal '${proposal.id}' is stale and cannot be applied.",
                    rollback = RollbackResult.NotRequired,
                )
            }
        }

        when (val verification = verifyPreview(preview)) {
            is EntioResult.Failure -> return failed(proposal, verification.message, RollbackResult.NotRequired)
            is EntioResult.Success -> when (val equivalence = verification.value) {
                SemanticEquivalenceResult.Equivalent -> Unit
                is SemanticEquivalenceResult.Failed -> return failed(proposal, equivalence.reason, RollbackResult.NotRequired)
                is SemanticEquivalenceResult.NotEquivalent -> return failed(
                    proposal,
                    equivalence.reason,
                    RollbackResult.NotRequired,
                )
            }
        }

        val temporarySource = when (val result = serializePreview(preview)) {
            is EntioResult.Failure -> return failed(proposal, result.message, RollbackResult.NotRequired)
            is EntioResult.Success -> result.value
        }

        val targetPath = targetSource.path
        val originalBytes = try {
            Files.readAllBytes(targetPath)
        } catch (exception: IOException) {
            return failed(
                proposal = proposal,
                reason = "Target ontology source '${targetSource.id}' could not be read before application.",
                rollback = RollbackResult.NotRequired,
            )
        }

        return try {
            moveReplacingTarget(temporarySource.path, targetPath)
            verifySavedProject(projectRoot, proposal, preview.graph, targetPath, originalBytes)
        } catch (exception: IOException) {
            failed(
                proposal = proposal,
                reason = exception.message ?: "Proposal '${proposal.id}' could not be applied.",
                rollback = restore(targetPath, originalBytes),
            )
        }
    }

    private fun verifySavedProject(
        projectRoot: Path,
        proposal: ChangeProposal,
        expectedGraph: GraphState,
        targetPath: Path,
        originalBytes: ByteArray,
    ): ApplyProposalResult {
        val savedProject = when (val result = loadProject(projectRoot)) {
            is EntioResult.Failure -> return failed(proposal, result.message, restore(targetPath, originalBytes))
            is EntioResult.Success -> result.value
        }

        return when (val equivalence = compareGraphs(expectedGraph, savedProject.graph)) {
            SemanticEquivalenceResult.Equivalent -> ApplyProposalResult.Applied(
                proposalId = proposal.id,
                changedFiles = listOf(targetPath.toString()),
            )
            is SemanticEquivalenceResult.Failed -> failed(proposal, equivalence.reason, restore(targetPath, originalBytes))
            is SemanticEquivalenceResult.NotEquivalent -> failed(proposal, equivalence.reason, restore(targetPath, originalBytes))
        }
    }

    private fun moveReplacingTarget(
        temporaryPath: Path,
        targetPath: Path,
    ): Unit {
        try {
            Files.move(
                temporaryPath,
                targetPath,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (exception: AtomicMoveNotSupportedException) {
            Files.move(
                temporaryPath,
                targetPath,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun restore(
        targetPath: Path,
        originalBytes: ByteArray,
    ): RollbackResult = restoreSource(targetPath, originalBytes)

    private fun failed(
        proposal: ChangeProposal,
        reason: String,
        rollback: RollbackResult,
    ): ApplyProposalResult.Failed =
        ApplyProposalResult.Failed(
            proposalId = proposal.id,
            reason = reason,
            rollback = rollback,
        )
}

private fun restoreOriginalSource(
    targetPath: Path,
    originalBytes: ByteArray,
): RollbackResult =
    try {
        Files.write(targetPath, originalBytes)
        RollbackResult.Restored(restoredFiles = listOf(targetPath.toString()))
    } catch (exception: IOException) {
        RollbackResult.Failed(
            reason = exception.message ?: "Original ontology source could not be restored.",
        )
    }
