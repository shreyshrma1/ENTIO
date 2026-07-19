package com.entio.web.ai

import com.entio.web.contract.WebStageChangeRequest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiIncrementalValidationServiceTest {
    private val now = Instant.parse("2026-07-19T22:00:00Z")

    @Test
    fun runsDeterministicStagesInOrderAndSkipsIrrelevantReasoningAndShacl(): Unit {
        val fixture = analysisFixture()
        val result = fixture.service.analyze(
            "alice", "simple", "task-1", 0, "package-a", "draft-1", "project-fingerprint-1",
            "reasoning-1", "shacl-1", AiAnalysisRelevance(false, false),
        )

        assertEquals(AiAnalysisStage.entries, result.second.stages.map(AiAnalysisStageResult::stage))
        assertEquals(AiAnalysisStage.entries.dropLast(2), fixture.called)
        assertTrue(result.second.stages.takeLast(2).all(AiAnalysisStageResult::skippedNotRelevant))
        assertEquals(AiAnalysisStatus.VALID, result.second.status)
        assertEquals(AiTaskStatus.EXECUTING, result.first.task.status)
    }

    @Test
    fun warningsContinueButBlockingStopsAndPreventsPackageCompletion(): Unit {
        val warning = analysisFixture(statuses = mapOf(AiAnalysisStage.VALIDATION to AiAnalysisStatus.WARNING))
        val warningResult = warning.run()
        assertEquals(AiAnalysisStatus.WARNING, warningResult.second.status)
        assertFalse("package-a" in warningResult.first.failedWorkPackageIds)

        val blocked = analysisFixture(statuses = mapOf(AiAnalysisStage.VALIDATION to AiAnalysisStatus.BLOCKED))
        val blockedResult = blocked.run()
        assertEquals(AiAnalysisStatus.BLOCKED, blockedResult.second.status)
        assertEquals(AiAnalysisStage.VALIDATION, blockedResult.second.stages.last().stage)
        assertTrue("package-a" in blockedResult.first.failedWorkPackageIds)
        assertEquals(AiTaskStatus.VALIDATING, blockedResult.first.task.status)
    }

    @Test
    fun everyStageResultMustMatchCurrentFingerprints(): Unit {
        val fixture = analysisFixture(staleStage = AiAnalysisStage.SEMANTIC_DIFF)
        val result = fixture.run()

        assertEquals(AiAnalysisStatus.STALE, result.second.status)
        assertEquals(AiTaskStatus.STALE, result.first.task.status)
        assertEquals(AiAnalysisStage.SEMANTIC_DIFF, result.second.stages.last().stage)
    }

    @Test
    fun finalCombinedAnalysisRequiresAllPackagesAndBecomesReviewReady(): Unit {
        val incomplete = analysisFixture(completed = listOf("a"))
        assertEquals("final-analysis-premature", assertFailsWith<AiIncrementalAnalysisFailure> {
            incomplete.service.analyze(
                "alice", "simple", "task-1", 0, null, "draft", "project-fingerprint-1", null, null,
                AiAnalysisRelevance(false, false), finalCombined = true, requiredPackageIds = listOf("a", "b"),
            )
        }.code)
        val complete = analysisFixture(completed = listOf("a", "b"))
        val result = complete.service.analyze(
            "alice", "simple", "task-1", 0, null, "draft", "project-fingerprint-1", null, null,
            AiAnalysisRelevance(false, false), finalCombined = true, requiredPackageIds = listOf("a", "b"),
        )
        assertEquals(AiTaskStatus.READY_FOR_REVIEW, result.first.task.status)
    }

    @Test
    fun relevanceIsDerivedFromTypedOperationsAndConfiguredSources(): Unit {
        val editing = editingFixture()
        val classOperation = editing.adapter.prepare(
            editing.scope, AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
            WebStageChangeRequest("simple", "create-class", label = "Receivable"),
        )
        val definition = editing.adapter.prepare(
            editing.scope, AiTypedEditCapabilityAdapter.ADD_DEFINITION_CAPABILITY,
            WebStageChangeRequest("simple", "add-definition", targetLabel = "Account", value = "A financial record."),
        )
        val service = analysisFixture().service

        assertEquals(AiAnalysisRelevance(true, false), service.deriveRelevance(listOf(classOperation), false))
        assertEquals(AiAnalysisRelevance(false, false), service.deriveRelevance(listOf(definition), true))
    }

    private fun AnalysisFixture.run() = service.analyze(
        "alice", "simple", "task-1", 0, "package-a", "draft-1", "project-fingerprint-1",
        "reasoning-1", "shacl-1", AiAnalysisRelevance(true, true),
    )

    private fun analysisFixture(
        statuses: Map<AiAnalysisStage, AiAnalysisStatus> = emptyMap(),
        staleStage: AiAnalysisStage? = null,
        completed: List<String> = emptyList(),
    ): AnalysisFixture {
        val store = InMemoryAiTaskStore()
        store.create(AiTaskWorkspace(taskFixture(status = AiTaskStatus.EXECUTING), completedWorkPackageIds = completed))
        val called = mutableListOf<AiAnalysisStage>()
        val runner = AiDeterministicAnalysisRunner { stage, fingerprints ->
            called += stage
            AiAnalysisStageResult(
                stage,
                statuses[stage] ?: AiAnalysisStatus.VALID,
                "${stage.name.lowercase()}-1",
                if (stage == staleStage) fingerprints.copy(projectFingerprint = "changed") else fingerprints,
            )
        }
        return AnalysisFixture(
            AiIncrementalValidationService(store, runner, Clock.fixed(now, ZoneOffset.UTC), idFactory = { "analysis-1" }),
            called,
        )
    }

    private data class AnalysisFixture(
        val service: AiIncrementalValidationService,
        val called: MutableList<AiAnalysisStage>,
    )
}
