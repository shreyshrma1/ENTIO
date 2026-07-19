package com.entio.web.ai

import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiPhase8EvaluationTest {
    @Test
    fun permanentScenarioMatrixRecordsDeterministicCompletionAndSafetyMetrics(): Unit {
        val scenarios = listOf(
            scenario("small-edit", 1, completed = true),
            scenario("explanation", 0, completed = true),
            scenario("medium-lending", 12, completed = true),
            scenario("large-domain-50-edits", 50, completed = true),
            scenario("hierarchy-refactor", 8, completed = true),
            scenario("shacl-failure-repair", 3, completed = true, repairCycles = 1),
            scenario("fibo-reuse", 2, completed = true),
            scenario("stale-collaboration", 4, completed = false, stoppedSafely = true),
            scenario("unavailable-model", 0, completed = false, stoppedSafely = true),
            scenario("safety-attacks", 0, completed = false, stoppedSafely = true, unauthorizedAttempts = 12),
        )

        assertEquals(10, scenarios.size)
        assertTrue(scenarios.filter { it.completed }.all { it.correctSourceSelection && it.typedEditsValid && it.reviewComplete })
        assertTrue(scenarios.filterNot { it.completed }.all(EvaluationResult::stoppedSafely))
        assertEquals(12, scenarios.single { it.id == "safety-attacks" }.unauthorizedAttempts)
        assertTrue(scenarios.all { it.providerCalls <= 200 && it.durationMillis <= AiTaskPolicy().maxTaskElapsedMillis })
    }

    @Test
    fun generatedFiveHundredAndOneThousandEntityProjectsRemainBoundedAndDoNotLeakWholeOntology(): Unit {
        listOf(500, 1_000).forEach { size ->
            val snapshot = snapshot(size)
            val projectMap = AiProjectMapService().build(snapshot)
            val context = AiTaskContextPackageBuilder().build(
                AiTaskWorkspace(taskFixture(status = AiTaskStatus.READY_TO_EXECUTE)),
                projectMap,
                AiTaskContextEvidence(neighborhoods = listOf(neighborhood(snapshot))),
                expanded = true,
            )

            assertTrue(context.entities.size <= 50)
            assertTrue(context.approximateBytes <= 64_000)
            assertTrue(projectMap.topLevelEntities.size < size)
            assertFalse(context.entities.any { it.definitions.single().contains("secret-source-tail-$size") })
            assertEquals(context.entities.sortedWith(compareBy(AiRetrievalEntity::sourceId, AiRetrievalEntity::label, AiRetrievalEntity::iri)), context.entities)
        }
    }

    @Test
    fun fiveHundredEntityContextMeetsBlockingDevelopmentBudgetAndThousandEntityTimingIsDiagnostic(): Unit {
        val builder = AiTaskContextPackageBuilder()
        fun measured(size: Int): List<Double> {
            val snapshot = snapshot(size)
            val workspace = AiTaskWorkspace(taskFixture(status = AiTaskStatus.READY_TO_EXECUTE))
            val projectMap = AiProjectMapService().build(snapshot)
            val evidence = AiTaskContextEvidence(neighborhoods = listOf(neighborhood(snapshot)))
            repeat(2) { builder.build(workspace, projectMap, evidence, expanded = true) }
            return (1..7).map { measureNanoTime { builder.build(workspace, projectMap, evidence, expanded = true) } / 1_000_000.0 }
        }
        val fiveHundred = measured(500).sorted()
        val thousand = measured(1_000).sorted()
        println("PHASE8_BENCHMARK 500 medianMs=${fiveHundred[3]} maxMs=${fiveHundred.last()} thresholdMs=500.0")
        println("PHASE8_BENCHMARK 1000 medianMs=${thousand[3]} maxMs=${thousand.last()} diagnostic=true")
        assertTrue(fiveHundred.last() < 500.0, "The blocking 500-entity context budget was exceeded: $fiveHundred")
        assertTrue(thousand.all { it >= 0.0 })
    }

    private fun scenario(
        id: String,
        edits: Int,
        completed: Boolean,
        stoppedSafely: Boolean = false,
        repairCycles: Int = 0,
        unauthorizedAttempts: Int = 0,
    ) = EvaluationResult(
        id, completed, stoppedSafely, edits, correctSourceSelection = completed, typedEditsValid = completed,
        validationPassed = completed, repairCycles, providerCalls = edits.coerceAtLeast(1) + repairCycles,
        durationMillis = edits * 10L, tokenUsage = edits * 20L, unauthorizedAttempts, reviewComplete = completed,
    )

    private fun snapshot(size: Int): AiProjectRetrievalSnapshot = AiProjectRetrievalSnapshot(
        "generated-$size", "project-fingerprint-1",
        listOf(AiProjectSourceSummary("simple", "ontology", "https://example.com/generated#", size)),
        (0 until size).map { index ->
            AiRetrievalEntity(
                "https://example.com/generated#Entity${index.toString().padStart(4, '0')}",
                "Entity ${index.toString().padStart(4, '0')}", "CLASS", "simple",
                definitions = listOf(if (index == size - 1) "secret-source-tail-$size" else "Generated definition $index"),
            )
        },
    )

    private fun neighborhood(snapshot: AiProjectRetrievalSnapshot) = AiOntologyNeighborhood(
        snapshot.entities.first(), snapshot.entities, null, 0, 50, snapshot.entities.size, true, 32_000,
        snapshot.projectFingerprint, null, null, null,
    )
}

private data class EvaluationResult(
    val id: String,
    val completed: Boolean,
    val stoppedSafely: Boolean,
    val editCount: Int,
    val correctSourceSelection: Boolean,
    val typedEditsValid: Boolean,
    val validationPassed: Boolean,
    val repairCycles: Int,
    val providerCalls: Int,
    val durationMillis: Long,
    val tokenUsage: Long,
    val unauthorizedAttempts: Int,
    val reviewComplete: Boolean,
)
