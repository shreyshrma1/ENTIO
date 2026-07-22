package com.entio.semantic

import com.entio.core.ChangeProposal
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.ExternalProposalIntent
import com.entio.core.ExternalCatalogElement
import com.entio.core.GraphState
import com.entio.core.Iri

/** Creates the existing baseline-aware proposal contract from an approved external intent. */
public class ExternalProposalPreparer(
    private val translator: ExternalProposalIntentTranslator = ExternalProposalIntentTranslator(),
    private val proposalCreator: ProposalCreator = ProposalCreator(),
) {
    public fun prepare(
        project: EntioProject,
        targetSourceId: String,
        targetOntologyIri: Iri,
        intent: ExternalProposalIntent,
        materializedElements: List<ExternalCatalogElement> = emptyList(),
        existingGraph: GraphState? = null,
        id: String,
        title: String,
    ): EntioResult<ChangeProposal> {
        val changeSet = when (val result = translator.translate(intent, targetOntologyIri, materializedElements, existingGraph)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        return proposalCreator.createProposal(
            project = project,
            targetSourceId = targetSourceId,
            changeSet = changeSet,
            id = id,
            title = title,
        )
    }
}
