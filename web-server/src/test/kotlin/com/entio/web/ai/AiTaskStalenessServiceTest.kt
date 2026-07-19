package com.entio.web.ai

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class AiTaskStalenessServiceTest {
    private val now = Instant.parse("2026-07-19T23:00:00Z")

    @Test
    fun projectChangeAtEveryActiveStageStopsMutationAndInvalidatesCache(): Unit {
        listOf(
            AiTaskStatus.UNDERSTANDING,
            AiTaskStatus.READY_TO_EXECUTE,
            AiTaskStatus.EXECUTING,
            AiTaskStatus.VALIDATING,
            AiTaskStatus.RUNNING_REASONING,
            AiTaskStatus.RUNNING_SHACL,
        ).forEach { status ->
            val fixture = fixture(status)
            val stale = fixture.service.observe("alice", "simple", "task-1", 0, "project-fingerprint-2")
            assertEquals(AiTaskStatus.STALE, stale.task.status, status.name)
            assertEquals(0, fixture.cache.size())
        }
    }

    @Test
    fun refreshPreservesDraftAndRequiresRevalidationOrMeaningConfirmation(): Unit {
        val fixture = fixture(AiTaskStatus.EXECUTING, privateDraftId = "draft-1")
        val stale = fixture.service.observe("alice", "simple", "task-1", 0, "project-fingerprint-2")
        val refreshed = fixture.service.refreshForRevalidation(
            "alice", "simple", "task-1", stale.revision, "project-fingerprint-2", meaningChanged = false,
        )
        assertEquals(AiTaskStatus.VALIDATING, refreshed.task.status)
        assertEquals("draft-1", refreshed.privateDraftId)
        assertEquals("project-fingerprint-2", refreshed.currentProjectFingerprint)

        val changedFixture = fixture(AiTaskStatus.EXECUTING, privateDraftId = "draft-2")
        val changedStale = changedFixture.service.observe("alice", "simple", "task-1", 0, "project-fingerprint-2")
        val changed = changedFixture.service.refreshForRevalidation(
            "alice", "simple", "task-1", changedStale.revision, "project-fingerprint-2", meaningChanged = true,
        )
        assertEquals(AiTaskStatus.AWAITING_PLAN_CONFIRMATION, changed.task.status)
        assertEquals("draft-2", changed.privateDraftId)
    }

    private fun fixture(status: AiTaskStatus, privateDraftId: String? = null): StaleFixture {
        val store = InMemoryAiTaskStore()
        store.create(AiTaskWorkspace(taskFixture(status = status), privateDraftId = privateDraftId))
        val cache = AiContextCache()
        cache.getOrPut(AiContextCacheKey(null, "simple", "project-fingerprint-1", resource = "map")) { "cached" }
        return StaleFixture(store, cache, AiTaskStalenessService(store, cache, Clock.fixed(now, ZoneOffset.UTC)))
    }

    private data class StaleFixture(
        val store: InMemoryAiTaskStore,
        val cache: AiContextCache,
        val service: AiTaskStalenessService,
    )
}
