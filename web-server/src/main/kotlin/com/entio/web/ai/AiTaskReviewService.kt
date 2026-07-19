package com.entio.web.ai

import com.entio.web.CollaborationHub
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant

public data class AiTaskReviewPackage(
    val taskId: String,
    val taskRevision: Long,
    val objective: String,
    val plan: AiWorkflowPlanRevision?,
    val projectFingerprint: String,
    val draftId: String,
    val draftRevision: Int,
    val draftFingerprint: String,
    val packageSummaries: List<String>,
    val analysis: AiIncrementalAnalysis,
    val analysisReferences: AiTaskAnalysisReferences,
    val sourceIds: List<String>,
    val dependencies: Map<String, List<String>>,
    val assumptions: List<String>,
    val warnings: List<String>,
    val openQuestions: List<String>,
    val rationale: String,
    val submittingUserId: String,
    val preparedAt: Instant,
    val fingerprint: String,
)

public data class AiTaskAuditRecord(
    val taskId: String,
    val executionSegmentIds: List<String>,
    val contextBundleIds: List<String>,
    val planRevision: Int?,
    val draftRevision: Int,
    val analysisIds: List<String>,
    val repairCycles: Int,
    val checkpointReferences: List<String>,
    val finalProposalId: String,
    val submittingUserId: String,
    val startedAt: Instant,
    val submittedAt: Instant,
    val elapsedMillis: Long,
    val toolCallCount: Int,
)

public interface AiTaskAuditStore {
    public fun append(record: AiTaskAuditRecord): AiTaskAuditRecord
    public fun get(userId: String, projectId: String, taskId: String): AiTaskAuditRecord
}

public class InMemoryAiTaskAuditStore(private val tasks: AiTaskStore) : AiTaskAuditStore {
    private val records: MutableMap<String, AiTaskAuditRecord> = linkedMapOf()

    @Synchronized
    override fun append(record: AiTaskAuditRecord): AiTaskAuditRecord {
        if (records.putIfAbsent(record.taskId, record) != null) {
            throw AiDraftFailure("task-already-submitted", "The AI task was already submitted for review.")
        }
        return record
    }

    override fun get(userId: String, projectId: String, taskId: String): AiTaskAuditRecord {
        tasks.get(userId, projectId, taskId)
        return records[taskId] ?: throw AiDraftFailure("missing-task-audit", "The AI task audit was not found.")
    }
}

public class AiReviewPackageBuilder(private val clock: Clock = Clock.systemUTC()) {
    public fun build(
        workspace: AiTaskWorkspace,
        draft: AiDraft,
        plan: AiWorkflowPlanRevision?,
        analyses: List<AiIncrementalAnalysis>,
        packageSummaries: List<String>,
        rationale: String,
    ): AiTaskReviewPackage {
        if (workspace.task.status != AiTaskStatus.READY_FOR_REVIEW || workspace.failedWorkPackageIds.isNotEmpty()) {
            throw AiDraftFailure("task-not-ready-for-review", "Only complete, current task work can be packaged for review.")
        }
        val required = plan?.workPackages?.map(AiWorkPackage::id).orEmpty()
        if (!workspace.completedWorkPackageIds.containsAll(required)) {
            throw AiDraftFailure("incomplete-task-packages", "Required task work packages are incomplete.")
        }
        val finalAnalysis = analyses.lastOrNull { it.finalCombined }
            ?: throw AiDraftFailure("missing-final-task-analysis", "A final combined task analysis is required.")
        if (finalAnalysis.status !in setOf(AiAnalysisStatus.VALID, AiAnalysisStatus.WARNING) ||
            finalAnalysis.fingerprints.projectFingerprint != workspace.currentProjectFingerprint
        ) {
            throw AiDraftFailure("stale-or-blocked-task-analysis", "The final task analysis is stale or blocking.")
        }
        val draftFingerprint = draft.draftFingerprint
            ?: throw AiDraftFailure("incomplete-private-draft", "The private draft has no deterministic fingerprint.")
        if (workspace.privateDraftId != draft.id || finalAnalysis.fingerprints.draftFingerprint != draftFingerprint) {
            throw AiDraftFailure("changed-task-draft", "The private draft changed after final task analysis.")
        }
        val draftRevision = draft.revisions.maxOfOrNull(AiDraftRevision::revision) ?: 0
        val sources = (draft.allowedSourceIds + plan.orEmptySources()).distinct().sorted()
        val dependencies = plan?.workPackages.orEmpty().associate { it.id to it.dependsOn.sorted() }.toSortedMap()
        val assumptions = workspace.assumptions.map(AiTaskAssumption::statement).sorted()
        val warnings = finalAnalysis.stages.filter { it.status == AiAnalysisStatus.WARNING }.flatMap(AiAnalysisStageResult::findingCodes).distinct().sorted()
        val questions = workspace.openQuestions.map(AiTaskOpenQuestion::question).sorted()
        val preparedAt = clock.instant()
        val canonical = listOf(
            workspace.task.id, workspace.revision.toString(), workspace.task.objective, workspace.currentProjectFingerprint,
            draft.id, draftRevision.toString(), draftFingerprint, plan?.planId.orEmpty(), plan?.revision?.toString().orEmpty(),
            required.joinToString(","), packageSummaries.sorted().joinToString("|"), finalAnalysis.id,
            sources.joinToString(","), dependencies.entries.joinToString("|") { "${it.key}:${it.value.joinToString(",")}" },
            assumptions.joinToString("|"), warnings.joinToString(","), questions.joinToString("|"), rationale.trim(),
            workspace.task.userId, preparedAt.toString(),
        ).joinToString("\u001f")
        return AiTaskReviewPackage(
            workspace.task.id, workspace.revision, workspace.task.objective, plan, workspace.currentProjectFingerprint,
            draft.id, draftRevision, draftFingerprint, packageSummaries.sorted(), finalAnalysis, workspace.analysisReferences,
            sources, dependencies, assumptions, warnings, questions, rationale.trim(), workspace.task.userId, preparedAt,
            MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) },
        )
    }

    private fun AiWorkflowPlanRevision?.orEmptySources(): List<String> = this?.workPackages?.flatMap(AiWorkPackage::expectedSourceIds).orEmpty()
}

public fun interface AiTaskDraftSubmitter {
    public suspend fun submit(scope: AiCapabilityScope, request: AiReviewSubmissionRequest): AiReviewSubmissionResult
}

public class AiTaskReviewService(
    private val tasks: AiTaskStore,
    private val submitter: AiTaskDraftSubmitter,
    private val audits: AiTaskAuditStore,
    private val collaboration: CollaborationHub,
    private val clock: Clock = Clock.systemUTC(),
) {
    public suspend fun submit(
        scope: AiCapabilityScope,
        taskId: String,
        expectedRevision: Long,
        reviewPackage: AiTaskReviewPackage,
        request: AiReviewSubmissionRequest,
        contextBundleIds: List<String> = emptyList(),
        checkpointReferences: List<String> = emptyList(),
    ): AiReviewSubmissionResult {
        val workspace = tasks.get(scope.userId, scope.projectId, taskId)
        if (workspace.revision != expectedRevision || reviewPackage.taskRevision != expectedRevision) {
            throw AiDraftFailure("stale-task-review-package", "The task changed after its review package was prepared.")
        }
        if (workspace.task.status != AiTaskStatus.READY_FOR_REVIEW || reviewPackage.fingerprint.isBlank() ||
            request.draftId != reviewPackage.draftId || request.expectedDraftFingerprint != reviewPackage.draftFingerprint
        ) {
            throw AiDraftFailure("invalid-task-review-submission", "The submission does not match the current review package.")
        }
        val result = submitter.submit(scope, request)
        val now = clock.instant()
        audits.append(
            AiTaskAuditRecord(
                taskId, workspace.executionSegments.map(AiTaskExecutionSegment::id), contextBundleIds.distinct().sorted(),
                workspace.planReference?.revision, reviewPackage.draftRevision, listOf(reviewPackage.analysis.id),
                workspace.counters.repairCycleCount, checkpointReferences.distinct().sorted(), result.proposalId,
                scope.userId, workspace.task.createdAt, now,
                java.time.Duration.between(workspace.task.createdAt, now).toMillis().coerceAtLeast(0), workspace.counters.toolCallCount,
            ),
        )
        val replacement = workspace.copy(
            task = workspace.task.copy(status = AiTaskStatus.SUBMITTED_FOR_REVIEW, updatedAt = now),
            revision = expectedRevision + 1,
            updatedAt = now,
        )
        tasks.update(scope.userId, scope.projectId, taskId, expectedRevision, replacement)
        collaboration.aiTaskProposalSubmitted(
            scope.projectId, result.proposalId, taskId, scope.userId, reviewPackage.rationale,
            reviewPackage.sourceIds, reviewPackage.packageSummaries,
        )
        return result
    }
}
