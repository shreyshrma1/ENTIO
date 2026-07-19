package com.entio.web.ai

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AiReviewPackageBuilderTest {
    private val now = Instant.parse("2026-07-20T04:00:00Z")
    private val builder = AiReviewPackageBuilder(Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun completeCurrentTaskProducesStableEvidenceRichPackage(): Unit {
        val fixture = fixture()
        val first = builder.build(fixture.workspace, fixture.draft, fixture.plan, listOf(fixture.analysis), listOf("Created Account."), "Ready for review.")
        val second = builder.build(fixture.workspace, fixture.draft, fixture.plan, listOf(fixture.analysis), listOf("Created Account."), "Ready for review.")

        assertEquals(first.fingerprint, second.fingerprint)
        assertEquals(listOf("package-a"), first.plan?.workPackages?.map(AiWorkPackage::id))
        assertEquals(mapOf("package-a" to emptyList()), first.dependencies)
        assertEquals(listOf("simple"), first.sourceIds)
        assertEquals(listOf("Use banking meaning"), first.assumptions)
        assertEquals(listOf("warning-code"), first.warnings)
        assertEquals("alice", first.submittingUserId)
        assertTrue(first.fingerprint.length == 64)
    }

    @Test
    fun incompleteStaleAndChangedDraftWorkCannotBePackaged(): Unit {
        val fixture = fixture()
        assertEquals("task-not-ready-for-review", assertFailsWith<AiDraftFailure> {
            builder.build(
                fixture.workspace.copy(task = fixture.workspace.task.copy(status = AiTaskStatus.STALE)),
                fixture.draft, fixture.plan, listOf(fixture.analysis), emptyList(), "No.",
            )
        }.code)
        assertEquals("changed-task-draft", assertFailsWith<AiDraftFailure> {
            builder.build(
                fixture.workspace, fixture.draft.copy(draftFingerprint = "changed"), fixture.plan,
                listOf(fixture.analysis), emptyList(), "No.",
            )
        }.code)
    }

    private fun fixture(): ReviewFixture {
        val task = taskFixture(status = AiTaskStatus.READY_FOR_REVIEW)
        val workspace = AiTaskWorkspace(
            task = task,
            revision = 7,
            completedWorkPackageIds = listOf("package-a"),
            privateDraftId = "draft-1",
            assumptions = listOf(AiTaskAssumption("a", "Use banking meaning", now)),
            analysisReferences = AiTaskAnalysisReferences(validationReferenceIds = listOf("validation-1")),
        )
        val draft = AiDraft(
            "draft-1", "conversation-1", "alice", "simple", "project-fingerprint-1", listOf("simple"),
            revisions = listOf(AiDraftRevision(1, "add", "Add reviewed concept.", emptyList(), now)),
            status = AiDraftStatus.READY_FOR_REVIEW, draftFingerprint = "draft-fingerprint-1", createdAt = now, updatedAt = now,
        )
        val plan = AiWorkflowPlanRevision(
            "plan-1", 1, "task-1",
            listOf(AiWorkPackage("package-a", "Create Account", expectedSourceIds = listOf("simple"), bundleId = AiCapabilityBundleId.ONTOLOGY_EDITING, estimate = AiWorkEstimate(1, "SMALL"))),
            false, createdAt = now,
        )
        val fingerprints = AiAnalysisFingerprintSet(6, "draft-fingerprint-1", "project-fingerprint-1", null, null)
        val analysis = AiIncrementalAnalysis(
            "analysis-1", "task-1", null, true, AiAnalysisStatus.WARNING,
            listOf(AiAnalysisStageResult(AiAnalysisStage.VALIDATION, AiAnalysisStatus.WARNING, "validation-1", fingerprints, listOf("warning-code"))),
            fingerprints, now,
        )
        return ReviewFixture(workspace, draft, plan, analysis)
    }

    private data class ReviewFixture(
        val workspace: AiTaskWorkspace,
        val draft: AiDraft,
        val plan: AiWorkflowPlanRevision,
        val analysis: AiIncrementalAnalysis,
    )
}
