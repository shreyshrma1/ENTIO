package com.entio.web.ai

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiTaskPlanningServiceTest {
    private val now = Instant.parse("2026-07-19T18:00:00Z")

    @Test
    fun simpleTaskMayBecomeReadyWithoutPlanButMediumAndLargeUsePlans(): Unit {
        val simpleFixture = planningFixture(AiTaskSize.SIMPLE, AiTaskStatus.PLANNING)
        val simple = simpleFixture.service.readySimple("alice", "simple", "task-1", 0)
        assertNull(simple.plan)
        assertEquals(AiTaskStatus.READY_TO_EXECUTE, simple.workspace.task.status)

        val mediumFixture = planningFixture(AiTaskSize.MEDIUM)
        val medium = mediumFixture.service.createPlan("alice", "simple", "task-1", 0, listOf(pkg("a")))
        assertNotNull(medium.plan)
        assertEquals(AiTaskStatus.READY_TO_EXECUTE, medium.workspace.task.status)

        val largeFixture = planningFixture(AiTaskSize.LARGE)
        val large = largeFixture.service.createPlan("alice", "simple", "task-1", 0, listOf(pkg("a")))
        assertTrue(large.plan!!.requiresConfirmation)
        assertEquals(AiTaskStatus.AWAITING_PLAN_CONFIRMATION, large.workspace.task.status)
    }

    @Test
    fun destructiveShaclReuseCrossSourceAndRequestedCasesRequireConfirmation(): Unit {
        val risks = listOf(
            AiPlanRiskFlag.DELETION,
            AiPlanRiskFlag.HIERARCHY_REFACTOR,
            AiPlanRiskFlag.MATERIAL_EXTERNAL_REUSE,
            AiPlanRiskFlag.HIGH_IMPACT_SHACL,
            AiPlanRiskFlag.IDENTITY_AMBIGUITY,
        )
        risks.forEach { risk ->
            val fixture = planningFixture(AiTaskSize.MEDIUM)
            val result = fixture.service.createPlan("alice", "simple", "task-1", 0, listOf(pkg("a", risks = setOf(risk))))
            assertTrue(result.plan!!.requiresConfirmation, risk.name)
        }
        val crossSource = planningFixture(AiTaskSize.MEDIUM, allowedSources = listOf("simple", "shapes"))
        assertTrue(crossSource.service.createPlan(
            "alice", "simple", "task-1", 0,
            listOf(pkg("a"), pkg("b", sources = listOf("shapes"), bundle = AiCapabilityBundleId.SHACL)),
        ).plan!!.requiresConfirmation)
        val requested = planningFixture(AiTaskSize.MEDIUM)
        assertTrue(requested.service.createPlan("alice", "simple", "task-1", 0, listOf(pkg("a")), true).plan!!.requiresConfirmation)
    }

    @Test
    fun confirmationIsExplicitVersionMatchedNonReplayableAndNotApproval(): Unit {
        val fixture = planningFixture(AiTaskSize.LARGE)
        val created = fixture.service.createPlan("alice", "simple", "task-1", 0, listOf(pkg("a")))

        assertEquals("user-plan-confirmation-required", assertFailsWith<AiWorkflowPlanFailure> {
            fixture.service.confirm("alice", "simple", "task-1", 1, 1, "PROVIDER")
        }.code)
        assertEquals("stale-plan-confirmation", assertFailsWith<AiWorkflowPlanFailure> {
            fixture.service.confirm("alice", "simple", "task-1", 1, 2, "USER")
        }.code)
        val confirmed = fixture.service.confirm("alice", "simple", "task-1", 1, 1, "USER")
        assertEquals(AiTaskStatus.READY_TO_EXECUTE, confirmed.workspace.task.status)
        assertEquals("alice", confirmed.plan!!.confirmation!!.userId)
        assertEquals("plan-confirmation-replay", assertFailsWith<AiWorkflowPlanFailure> {
            fixture.service.confirm("alice", "simple", "task-1", 2, 1, "USER")
        }.code)
    }

    @Test
    fun revisionsPreserveHistoryAndInvalidateOldConfirmationVersion(): Unit {
        val fixture = planningFixture(AiTaskSize.LARGE)
        val created = fixture.service.createPlan("alice", "simple", "task-1", 0, listOf(pkg("a")))
        val revised = fixture.service.revisePlan("alice", "simple", "task-1", 1, 1, listOf(pkg("b")))

        assertEquals(2, revised.plan!!.revision)
        assertEquals(2, fixture.service.history("alice", "simple", "task-1").revisions.size)
        assertEquals("stale-plan-confirmation", assertFailsWith<AiWorkflowPlanFailure> {
            fixture.service.confirm("alice", "simple", "task-1", 2, created.plan!!.revision, "USER")
        }.code)
    }

    private fun planningFixture(
        size: AiTaskSize,
        status: AiTaskStatus = AiTaskStatus.PLANNING,
        allowedSources: List<String> = listOf("simple"),
    ): PlanningFixture {
        val store = InMemoryAiTaskStore()
        val task = taskFixture(status = status).copy(
            size = size,
            scope = taskFixture().scope.copy(allowedSourceIds = allowedSources),
        )
        store.create(AiTaskWorkspace(task))
        var id = 0
        return PlanningFixture(
            store,
            AiTaskPlanningService(store, clock = Clock.fixed(now, ZoneOffset.UTC), idFactory = { "plan-${++id}" }),
        )
    }

    private data class PlanningFixture(val store: InMemoryAiTaskStore, val service: AiTaskPlanningService)
}
