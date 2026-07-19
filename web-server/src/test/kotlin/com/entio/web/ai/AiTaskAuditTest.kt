package com.entio.web.ai

import com.entio.core.SemanticDiff
import com.entio.web.CollaborationHub
import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebStagingResponse
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlinx.coroutines.runBlocking

class AiTaskAuditTest {
    @Test
    fun submissionRecordsSafeTraceabilityAdvancesTaskAndRejectsReplay(): Unit = runBlocking {
        val now = Instant.parse("2026-07-20T04:05:00Z")
        val task = taskFixture(status = AiTaskStatus.READY_FOR_REVIEW)
        val store = InMemoryAiTaskStore()
        store.create(AiTaskWorkspace(task, completedWorkPackageIds = listOf("package-a"), privateDraftId = "draft-1"))
        val root = Files.createTempDirectory("task-audit-projects")
        val registry = InMemoryProjectRegistry(setOf(root))
        val project = Files.createDirectory(root.resolve("simple"))
        registry.register("simple", "Simple", project)
        val audits = InMemoryAiTaskAuditStore(store)
        val service = AiTaskReviewService(
            store,
            AiTaskDraftSubmitter { _, _ -> submissionResult() },
            audits,
            CollaborationHub(registry),
            Clock.fixed(now, ZoneOffset.UTC),
        )
        val scope = capabilityScope(task)
        val review = reviewPackage(task, now)
        val request = submissionRequest()

        val result = service.submit(scope, "task-1", 0, review, request, listOf("bundle-1"), listOf("checkpoint-1"))

        assertEquals("proposal-1", result.proposalId)
        assertEquals(AiTaskStatus.SUBMITTED_FOR_REVIEW, store.get("alice", "simple", "task-1").task.status)
        val audit = audits.get("alice", "simple", "task-1")
        assertEquals(listOf("segment-1"), audit.executionSegmentIds)
        assertEquals(listOf("bundle-1"), audit.contextBundleIds)
        assertEquals(listOf("analysis-1"), audit.analysisIds)
        assertEquals("proposal-1", audit.finalProposalId)
        assertFalse(audit.toString().contains("provider payload", ignoreCase = true))
        assertEquals("invalid-task-review-submission", assertFailsWith<AiDraftFailure> {
            service.submit(scope, "task-1", 1, review.copy(taskRevision = 1), request)
        }.code)
    }

    private fun submissionResult() = AiReviewSubmissionResult(
        "submission-1", "proposal-1", "READYFORREVIEW", "simple", "draft-1", 1, "alice", "conversation-1", "run-1",
        "Ready for review.", SemanticDiff(emptyList()), listOf("analysis-1"), emptyList(), "/review/proposal-1",
        WebStagingResponse(projectId = "simple", status = "READYFORREVIEW", entries = emptyList()),
    )

    private fun submissionRequest() = AiReviewSubmissionRequest(
        "draft-1", "analysis-1", "run-1", "Ready for review.", "project-fingerprint-1", "draft-fingerprint-1",
        "preview-1", listOf("analysis-1"), true,
    )

    private fun capabilityScope(task: AiTask) = AiCapabilityScope(
        userId = task.userId,
        projectId = task.projectId,
        conversationId = task.conversationId,
        allowedSourceIds = listOf("simple"),
        baselineFingerprint = task.scope.projectFingerprint,
        role = task.scope.role,
        permissions = task.scope.permissions,
        availableFeatures = task.scope.availableFeatures,
        createdAt = task.createdAt,
    )

    private fun reviewPackage(task: AiTask, now: Instant) = AiTaskReviewPackage(
        "task-1", 0, task.objective, null, "project-fingerprint-1", "draft-1", 1, "draft-fingerprint-1", listOf("Created Account"),
        AiIncrementalAnalysis("analysis-1", "task-1", null, true, AiAnalysisStatus.VALID, emptyList(), AiAnalysisFingerprintSet(2, "draft-fingerprint-1", "project-fingerprint-1", null, null), now),
        AiTaskAnalysisReferences(), listOf("simple"), emptyMap(), emptyList(), emptyList(), emptyList(), "Ready for review.", "alice", now, "fingerprint",
    )
}
