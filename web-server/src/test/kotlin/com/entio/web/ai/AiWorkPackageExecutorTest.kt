package com.entio.web.ai

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AiWorkPackageExecutorTest {
    @Test
    fun executesDependencyOrderedPackagesSeriallyWithOrderedEventsAndReplay(): Unit {
        val fixture = executorFixture()
        val first = fixture.executor.beginNext(
            "alice", "simple", "task-1", 0, fixture.plan, fixture.context(0), fixture.bundle, "start-a",
        )
        assertEquals("a", first.workPackage.id)
        assertSame(first, fixture.executor.beginNext(
            "alice", "simple", "task-1", 0, fixture.plan, fixture.context(0), fixture.bundle, "start-a",
        ))
        val called = fixture.executor.recordProviderCall(first, 2)
        val completed = fixture.executor.complete(called, 10)
        assertEquals(listOf(1L, 2L, 3L, 4L), completed.events.map(AiPackageProgressEvent::sequence))

        val secondBundle = fixture.bundleFor(fixture.plan.workPackages[1].bundleId)
        val second = fixture.executor.beginNext(
            "alice", "simple", "task-1", completed.workspace.revision, fixture.plan,
            fixture.context(completed.workspace.revision), secondBundle, "start-b",
        )
        assertEquals("b", second.workPackage.id)
        assertEquals(listOf("a"), second.workspace.completedWorkPackageIds)
    }

    @Test
    fun unconfirmedBlockedParallelAndInterruptedExecutionFailSafely(): Unit {
        val unconfirmed = executorFixture(confirmed = false)
        assertCode("unconfirmed-execution-plan") {
            unconfirmed.executor.beginNext("alice", "simple", "task-1", 0, unconfirmed.plan, unconfirmed.context(0), unconfirmed.bundle, "x")
        }

        val blocked = executorFixture(failed = listOf("a"), completed = emptyList())
        assertCode("blocked-work-package") {
            blocked.executor.beginNext("alice", "simple", "task-1", 0, blocked.plan, blocked.context(0), blocked.bundle, "x")
        }

        val active = executorFixture()
        val started = active.executor.beginNext("alice", "simple", "task-1", 0, active.plan, active.context(0), active.bundle, "x")
        assertCode("parallel-package-execution") {
            active.executor.beginNext("alice", "simple", "task-1", 1, active.plan, active.context(1), active.bundle, "y")
        }
        val current = active.store.get("alice", "simple", "task-1")
        active.store.update(
            "alice", "simple", "task-1", current.revision,
            current.copy(task = current.task.copy(status = AiTaskStatus.CANCELLED), revision = current.revision + 1),
        )
        assertCode("package-interrupted") { active.executor.recordProviderCall(started) }
    }

    @Test
    fun providerToolLimitsPreserveWorkspaceWithoutContinuing(): Unit {
        val fixture = executorFixture(policy = AiTaskPolicy(maxToolCallsPerPackage = 2, maxToolCallsPerTask = 2))
        val started = fixture.executor.beginNext("alice", "simple", "task-1", 0, fixture.plan, fixture.context(0), fixture.bundle, "x")
        val limited = fixture.executor.recordProviderCall(started, 3)

        assertEquals(AiTaskStatus.LIMIT_REACHED, limited.workspace.task.status)
        assertTrue(limited.events.any { it.type == AiPackageProgressType.LIMIT_REACHED })
    }

    private fun assertCode(code: String, block: () -> Unit) {
        assertEquals(code, assertFailsWith<AiWorkPackageExecutionFailure> { block() }.code)
    }
}

private data class ExecutorFixture(
    val store: InMemoryAiTaskStore,
    val executor: AiWorkPackageExecutor,
    val plan: AiWorkflowPlanRevision,
    val bundle: AiFrozenCapabilityBundle,
) {
    fun context(revision: Long) = taskContextFixture(revision)
    fun bundleFor(id: AiCapabilityBundleId): AiFrozenCapabilityBundle {
        require(id == bundle.bundle.id)
        return bundle
    }
}

private fun executorFixture(
    confirmed: Boolean = true,
    failed: List<String> = emptyList(),
    completed: List<String> = emptyList(),
    policy: AiTaskPolicy = AiTaskPolicy(),
): ExecutorFixture {
    val now = Instant.parse("2026-07-19T21:00:00Z")
    val packages = listOf(pkg("a"), pkg("b", dependencies = listOf("a")))
    val plan = AiWorkflowPlanRevision(
        "plan-1", 1, "task-1", packages, true,
        confirmation = if (confirmed) AiPlanConfirmation("alice", 1, now) else null,
        createdAt = now,
    )
    val task = taskFixture(status = AiTaskStatus.READY_TO_EXECUTE).copy(policy = policy)
    val store = InMemoryAiTaskStore()
    store.create(
        AiTaskWorkspace(
            task, planReference = AiTaskPlanReference("plan-1", 1),
            completedWorkPackageIds = completed, failedWorkPackageIds = failed,
        ),
    )
    val frozen = AiCapabilityBundleRegistry().freeze(task, capabilityScope())
    return ExecutorFixture(store, AiWorkPackageExecutor(store, Clock.fixed(now, ZoneOffset.UTC)), plan, frozen)
}

private fun taskContextFixture(revision: Long): AiTaskContextPackage = AiTaskContextPackage(
    taskId = "task-1",
    taskRevision = revision,
    projectMap = AiProjectMap(
        "simple", "project-fingerprint-1", AI_RETRIEVAL_POLICY_VERSION, emptyList(), emptyMap(), emptyList(),
        emptyList(), emptyList(), false, false, 0, emptyList(), emptyList(), false,
    ),
    entities = emptyList(), fiboCandidates = emptyList(), shaclFindings = emptyList(), assumptions = emptyList(),
    openQuestions = emptyList(), stagingSummary = null, draftSummary = null, rules = emptyList(), expanded = false,
    approximateBytes = 0,
)
