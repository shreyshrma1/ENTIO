package com.entio.web.ai

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiTaskFollowUpContextTest {
    @Test
    fun whyWhatChangedWhatFailedAndWhatRemainsUseAuthoritativeTaskEvidence(): Unit {
        val now = Instant.parse("2026-07-20T03:00:00Z")
        val store = InMemoryAiTaskStore()
        store.create(
            AiTaskWorkspace(
                taskFixture(status = AiTaskStatus.REPAIRING),
                completedWorkPackageIds = listOf("package-a"),
                failedWorkPackageIds = listOf("package-b"),
                assumptions = listOf(AiTaskAssumption("a", "Use banking meaning", now)),
                openQuestions = listOf(AiTaskOpenQuestion("q", "Which range?", "package-b", now)),
                privateDraftId = "draft-1",
                analysisReferences = AiTaskAnalysisReferences(validationReferenceIds = listOf("validation-1")),
            ),
        )
        val repairs = AiRepairController(store, Clock.fixed(now, ZoneOffset.UTC))
        val packet = AiRepairPacketBuilder(Clock.fixed(now, ZoneOffset.UTC), idFactory = { "packet" })
            .build(listOf(finding("finding", "invalid-language-tag", candidates = listOf("candidate")))).single()
        repairs.repair("alice", "simple", "task-1", 0, packet) { "draft-revision-2" }

        val context = AiTaskFollowUpContextBuilder(store, repairs).build("alice", "simple", "task-1")

        assertEquals(listOf("package-a"), context.completedPackages)
        assertEquals(listOf("package-b"), context.failedPackages)
        assertTrue("validation-1" in context.analysisReferences.validationReferenceIds)
        assertEquals(listOf("invalid-language-tag"), context.repairFindingCodes)
        assertEquals(listOf("Which range?"), context.remainingQuestions)
        assertEquals("draft-1", context.privateDraftId)
    }
}
