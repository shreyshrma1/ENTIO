package com.entio.diff

import com.entio.core.BaselineImpactStatus
import com.entio.core.ConsistencyStatus
import com.entio.core.EntioResult
import com.entio.core.GraphState
import com.entio.core.ProposalImpactReport
import com.entio.core.ReasoningImpactSummary
import com.entio.core.ReasoningResult
import com.entio.core.SemanticDiff
import com.entio.core.ShaclImpactSummary
import com.entio.core.ShaclSeverity
import com.entio.core.ShaclValidationReport
import com.entio.core.ShaclValidationResult
import com.entio.core.ShaclValidationStatus

/** Compares explicit graph, reasoning, and SHACL effects without mixing them together. */
public class ProposalImpactAnalyzer(
    private val graphDiffer: GraphDiffer = GraphDiffer(),
) {
    public fun analyze(
        beforeGraph: GraphState,
        afterGraph: GraphState,
        currentReasoning: ReasoningResult?,
        previewReasoning: ReasoningResult?,
        currentShacl: ShaclValidationReport?,
        previewShacl: ShaclValidationReport?,
    ): EntioResult<ProposalImpactReport> {
        val explicitDiff = graphDiffer.diff(beforeGraph, afterGraph)
        val reasoningImpact = compareReasoning(currentReasoning, previewReasoning)
        val shaclImpact = compareShacl(currentShacl, previewShacl)
        val blockingMessages = buildList {
            if (previewReasoning?.consistency == ConsistencyStatus.Inconsistent && currentReasoning?.consistency != ConsistencyStatus.Inconsistent) {
                add("The proposal introduces an inconsistent ontology.")
            }
            if (reasoningImpact.unsatisfiableClassesAdded.isNotEmpty()) {
                add("The proposal introduces unsatisfiable classes.")
            }
            shaclImpact.newResults.filter(::isBlocking).forEach { add("The proposal introduces a blocking SHACL violation: ${it.message}") }
            shaclImpact.worsenedResults.filter(::isBlocking).forEach { add("The proposal worsens a blocking SHACL violation: ${it.message}") }
            if (previewReasoning != null && previewReasoning.metadata.status != com.entio.core.ReasoningRunStatus.Completed) {
                add("Reasoning is incomplete for the proposal preview.")
            }
            if (previewShacl != null && previewShacl.status != ShaclValidationStatus.Completed) {
                add("SHACL validation is incomplete for the proposal preview.")
            }
        }
        val failed = listOfNotNull(previewReasoning?.metadata?.status, previewShacl?.status).any {
            it == com.entio.core.ReasoningRunStatus.Failed || it == ShaclValidationStatus.Failed
        }
        val incomplete = blockingMessages.any { it.contains("incomplete", ignoreCase = true) }
        val status = when {
            failed -> BaselineImpactStatus.Failed
            incomplete -> BaselineImpactStatus.Incomplete
            blockingMessages.isNotEmpty() -> BaselineImpactStatus.BlocksApproval
            else -> BaselineImpactStatus.Safe
        }
        return EntioResult.Success(
            ProposalImpactReport(
                explicitDiff = explicitDiff,
                reasoningImpact = reasoningImpact,
                shaclImpact = shaclImpact,
                status = status,
                blockingMessages = blockingMessages,
            ),
        )
    }

    private fun compareReasoning(
        current: ReasoningResult?,
        preview: ReasoningResult?,
    ): ReasoningImpactSummary {
        val currentInferred = current?.propertyRelationships.orEmpty().filter { it.origin == com.entio.core.FactOrigin.Inferred }.toSet()
        val previewInferred = preview?.propertyRelationships.orEmpty().filter { it.origin == com.entio.core.FactOrigin.Inferred }.toSet()
        val currentUnsatisfiable = current?.unsatisfiableClasses.orEmpty().toSet()
        val previewUnsatisfiable = preview?.unsatisfiableClasses.orEmpty().toSet()
        return ReasoningImpactSummary(
            addedInferences = (previewInferred - currentInferred).sortedBy { it.toString() },
            removedInferences = (currentInferred - previewInferred).sortedBy { it.toString() },
            consistencyChanged = current?.consistency != preview?.consistency,
            unsatisfiableClassesAdded = (previewUnsatisfiable - currentUnsatisfiable).sortedBy { it.value },
            unsatisfiableClassesResolved = (currentUnsatisfiable - previewUnsatisfiable).sortedBy { it.value },
        )
    }

    private fun compareShacl(
        current: ShaclValidationReport?,
        preview: ShaclValidationReport?,
    ): ShaclImpactSummary {
        val currentResults = current?.results.orEmpty()
        val previewResults = preview?.results.orEmpty()
        val currentByKey = currentResults.groupBy(::resultKey)
        val previewByKey = previewResults.groupBy(::resultKey)
        val unchanged = currentResults.filter { result -> previewResults.any { it.resultId == result.resultId } }
        val worsened = previewByKey
            .filterKeys { it in currentByKey }
            .flatMap { (key, results) -> results.filter { candidate -> currentByKey.getValue(key).none { it.resultId == candidate.resultId } } }
        val newResults = previewByKey
            .filterKeys { it !in currentByKey }
            .values
            .flatten()
        val resolvedResults = currentByKey
            .filterKeys { it !in previewByKey }
            .values
            .flatten()
        return ShaclImpactSummary(
            newResults = newResults.sortedBy(ShaclValidationResult::resultId),
            worsenedResults = worsened.sortedBy(ShaclValidationResult::resultId),
            unchangedResults = unchanged.sortedBy(ShaclValidationResult::resultId),
            resolvedResults = resolvedResults.sortedBy(ShaclValidationResult::resultId),
        )
    }

    private fun resultKey(result: ShaclValidationResult): String = listOf(
        result.focusNode.value,
        result.path,
        result.shape,
        result.constraint,
        result.sourceId,
    ).joinToString("|")

    private fun isBlocking(result: ShaclValidationResult): Boolean = result.severity == ShaclSeverity.Violation
}
