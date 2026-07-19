package com.entio.web.ai

import java.time.Clock
import java.time.Instant
import java.util.UUID

public enum class AiAnalysisStatus { VALID, WARNING, BLOCKED, STALE, INCOMPLETE, FAILED }
public enum class AiAnalysisStage { TYPED_PREPARATION, PREVIEW, VALIDATION, SEMANTIC_DIFF, REASONING, SHACL }

public data class AiAnalysisFingerprintSet(
    val taskRevision: Long,
    val draftFingerprint: String,
    val projectFingerprint: String,
    val reasoningFingerprint: String?,
    val shaclFingerprint: String?,
)

public data class AiAnalysisStageResult(
    val stage: AiAnalysisStage,
    val status: AiAnalysisStatus,
    val referenceId: String,
    val fingerprints: AiAnalysisFingerprintSet,
    val findingCodes: List<String> = emptyList(),
    val skippedNotRelevant: Boolean = false,
)

public data class AiIncrementalAnalysis(
    val id: String,
    val taskId: String,
    val workPackageId: String?,
    val finalCombined: Boolean,
    val status: AiAnalysisStatus,
    val stages: List<AiAnalysisStageResult>,
    val fingerprints: AiAnalysisFingerprintSet,
    val createdAt: Instant,
)

public data class AiAnalysisRelevance(val reasoning: Boolean, val shacl: Boolean)

public fun interface AiDeterministicAnalysisRunner {
    public fun run(stage: AiAnalysisStage, fingerprints: AiAnalysisFingerprintSet): AiAnalysisStageResult
}

public class AiIncrementalAnalysisFailure(public val code: String, message: String) : IllegalArgumentException(message)

public class AiIncrementalValidationService(
    private val store: AiTaskStore,
    private val runner: AiDeterministicAnalysisRunner,
    private val clock: Clock = Clock.systemUTC(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val histories: MutableMap<String, List<AiIncrementalAnalysis>> = linkedMapOf()

    public fun deriveRelevance(operations: List<AiTypedDraftOperation>, shaclConfigured: Boolean): AiAnalysisRelevance {
        val editTypes = operations.map { it.request.editType }.toSet()
        val reasoning = editTypes.any { type ->
            type in setOf(
                "create-class", "add-superclass", "remove-superclass", "create-object-property",
                "create-datatype-property", "set-property-domain", "set-property-range", "assign-type", "delete",
            )
        }
        val shacl = shaclConfigured && editTypes.any { it.startsWith("shacl-") || it in setOf("assign-type", "delete") }
        return AiAnalysisRelevance(reasoning, shacl)
    }

    @Synchronized
    public fun analyze(
        userId: String,
        projectId: String,
        taskId: String,
        expectedRevision: Long,
        workPackageId: String?,
        draftFingerprint: String,
        currentProjectFingerprint: String,
        reasoningFingerprint: String?,
        shaclFingerprint: String?,
        relevance: AiAnalysisRelevance,
        finalCombined: Boolean = false,
        requiredPackageIds: List<String> = emptyList(),
    ): Pair<AiTaskWorkspace, AiIncrementalAnalysis> {
        val workspace = store.get(userId, projectId, taskId)
        if (workspace.revision != expectedRevision) throw AiIncrementalAnalysisFailure("stale-analysis-task", "The task changed before analysis.")
        if (workspace.currentProjectFingerprint != currentProjectFingerprint) {
            return stale(workspace, expectedRevision, workPackageId, draftFingerprint, currentProjectFingerprint, reasoningFingerprint, shaclFingerprint, finalCombined)
        }
        if (finalCombined && !workspace.completedWorkPackageIds.containsAll(requiredPackageIds)) {
            throw AiIncrementalAnalysisFailure("final-analysis-premature", "Required work packages are incomplete.")
        }
        val fingerprints = AiAnalysisFingerprintSet(
            expectedRevision, draftFingerprint, currentProjectFingerprint, reasoningFingerprint, shaclFingerprint,
        )
        val stages = mutableListOf<AiAnalysisStageResult>()
        val ordered = listOf(
            AiAnalysisStage.TYPED_PREPARATION,
            AiAnalysisStage.PREVIEW,
            AiAnalysisStage.VALIDATION,
            AiAnalysisStage.SEMANTIC_DIFF,
            AiAnalysisStage.REASONING,
            AiAnalysisStage.SHACL,
        )
        ordered.forEach { stage ->
            val relevant = when (stage) {
                AiAnalysisStage.REASONING -> relevance.reasoning
                AiAnalysisStage.SHACL -> relevance.shacl
                else -> true
            }
            val result = if (relevant) runner.run(stage, fingerprints) else AiAnalysisStageResult(
                stage, AiAnalysisStatus.VALID, "skipped-${stage.name.lowercase()}", fingerprints, skippedNotRelevant = true,
            )
            if (result.stage != stage || result.fingerprints != fingerprints) {
                stages += result.copy(status = AiAnalysisStatus.STALE)
                return finish(workspace, expectedRevision, workPackageId, finalCombined, fingerprints, stages)
            }
            stages += result
            if (result.status in stopStatuses) return finish(workspace, expectedRevision, workPackageId, finalCombined, fingerprints, stages)
        }
        return finish(workspace, expectedRevision, workPackageId, finalCombined, fingerprints, stages)
    }

    public fun history(userId: String, projectId: String, taskId: String): List<AiIncrementalAnalysis> {
        store.get(userId, projectId, taskId)
        return histories[taskId].orEmpty()
    }

    private fun finish(
        workspace: AiTaskWorkspace,
        expectedRevision: Long,
        packageId: String?,
        finalCombined: Boolean,
        fingerprints: AiAnalysisFingerprintSet,
        stages: List<AiAnalysisStageResult>,
    ): Pair<AiTaskWorkspace, AiIncrementalAnalysis> {
        val status = aggregate(stages.map(AiAnalysisStageResult::status))
        val analysis = AiIncrementalAnalysis(
            idFactory(), workspace.task.id, packageId, finalCombined, status, stages, fingerprints, clock.instant(),
        )
        val blocked = status in stopStatuses
        val updatedStatus = when {
            status == AiAnalysisStatus.STALE -> AiTaskStatus.STALE
            blocked -> AiTaskStatus.VALIDATING
            finalCombined -> AiTaskStatus.READY_FOR_REVIEW
            else -> AiTaskStatus.EXECUTING
        }
        val now = clock.instant()
        val replacement = workspace.copy(
            task = workspace.task.copy(status = updatedStatus, updatedAt = now),
            revision = expectedRevision + 1,
            failedWorkPackageIds = if (blocked && packageId != null) workspace.failedWorkPackageIds + packageId else workspace.failedWorkPackageIds,
            analysisReferences = workspace.analysisReferences.copy(
                validationReferenceIds = workspace.analysisReferences.validationReferenceIds + stages
                    .filter { it.stage in setOf(AiAnalysisStage.TYPED_PREPARATION, AiAnalysisStage.PREVIEW, AiAnalysisStage.VALIDATION) }
                    .map(AiAnalysisStageResult::referenceId),
                semanticDiffReferenceIds = workspace.analysisReferences.semanticDiffReferenceIds + stages
                    .filter { it.stage == AiAnalysisStage.SEMANTIC_DIFF }.map(AiAnalysisStageResult::referenceId),
                reasoningReferenceIds = workspace.analysisReferences.reasoningReferenceIds + stages
                    .filter { it.stage == AiAnalysisStage.REASONING && !it.skippedNotRelevant }.map(AiAnalysisStageResult::referenceId),
                shaclReferenceIds = workspace.analysisReferences.shaclReferenceIds + stages
                    .filter { it.stage == AiAnalysisStage.SHACL && !it.skippedNotRelevant }.map(AiAnalysisStageResult::referenceId),
            ),
            updatedAt = now,
        )
        val updated = store.update(workspace.task.userId, workspace.task.projectId, workspace.task.id, expectedRevision, replacement)
        histories[workspace.task.id] = histories[workspace.task.id].orEmpty() + analysis
        return updated to analysis
    }

    private fun stale(
        workspace: AiTaskWorkspace,
        revision: Long,
        packageId: String?,
        draft: String,
        project: String,
        reasoning: String?,
        shacl: String?,
        final: Boolean,
    ): Pair<AiTaskWorkspace, AiIncrementalAnalysis> {
        val fingerprints = AiAnalysisFingerprintSet(revision, draft, project, reasoning, shacl)
        val result = AiAnalysisStageResult(AiAnalysisStage.TYPED_PREPARATION, AiAnalysisStatus.STALE, idFactory(), fingerprints)
        return finish(workspace, revision, packageId, final, fingerprints, listOf(result))
    }

    private fun aggregate(statuses: List<AiAnalysisStatus>): AiAnalysisStatus = when {
        AiAnalysisStatus.STALE in statuses -> AiAnalysisStatus.STALE
        AiAnalysisStatus.FAILED in statuses -> AiAnalysisStatus.FAILED
        AiAnalysisStatus.BLOCKED in statuses -> AiAnalysisStatus.BLOCKED
        AiAnalysisStatus.INCOMPLETE in statuses -> AiAnalysisStatus.INCOMPLETE
        AiAnalysisStatus.WARNING in statuses -> AiAnalysisStatus.WARNING
        else -> AiAnalysisStatus.VALID
    }

    private companion object {
        val stopStatuses = setOf(AiAnalysisStatus.BLOCKED, AiAnalysisStatus.STALE, AiAnalysisStatus.INCOMPLETE, AiAnalysisStatus.FAILED)
    }
}
