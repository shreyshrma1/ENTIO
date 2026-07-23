package com.entio.web

import com.entio.core.InferredFactsOverlay
import com.entio.web.contract.WebInferredFactsOverlay
import com.entio.web.contract.toWeb

/** Selects project-owned inferred overlays; browsers cannot nominate reasoning jobs. */
public class InferredFactsWebService(
    private val jobs: SemanticJobManager,
) {
    public fun read(
        projectId: String,
        includeApplied: Boolean,
        includeProposal: Boolean,
    ): List<WebInferredFactsOverlay> {
        // Applied reasoning starts on the first authorized project read even while
        // presentation filters remain off.
        jobs.ensureInferredRead(projectId, WebJobScope.Applied)
        return readCore(projectId, includeApplied, includeProposal).map(InferredFactsOverlay::toWeb)
    }

    internal fun readCore(
        projectId: String,
        includeApplied: Boolean,
        includeProposal: Boolean,
    ): List<InferredFactsOverlay> {
        jobs.ensureInferredRead(projectId, WebJobScope.Applied)
        return listOf(
            jobs.inferredReadOverlay(projectId, WebJobScope.Applied, includeApplied),
            jobs.inferredReadOverlay(projectId, WebJobScope.Proposal, includeProposal),
        )
    }

    public fun refreshProposal(projectId: String): Unit {
        jobs.invalidateProposalJobs(projectId)
        jobs.ensureInferredRead(projectId, WebJobScope.Proposal)
    }

    public fun refreshApplied(projectId: String): Unit {
        jobs.ensureInferredRead(projectId, WebJobScope.Applied)
    }
}
