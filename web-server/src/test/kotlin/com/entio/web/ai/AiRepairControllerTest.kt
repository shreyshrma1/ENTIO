package com.entio.web.ai

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AiRepairControllerTest {
    private val now = Instant.parse("2026-07-20T01:00:00Z")

    @Test
    fun automaticRepairMutatesPrivateDraftThenRequiresReanalysisAndRetainsHistory(): Unit {
        val fixture = fixture()
        val packet = packet("invalid-language-tag", listOf("candidate-1"))
        val mutations = mutableListOf<String>()
        val updated = fixture.controller.repair("alice", "simple", "task-1", 0, packet) {
            mutations += it.finding.findingId
            "draft-revision-2"
        }

        assertEquals(listOf("finding-1"), mutations)
        assertEquals(AiTaskStatus.VALIDATING, updated.task.status)
        assertEquals(1, updated.counters.repairCycleCount)
        assertEquals(packet, fixture.controller.history("alice", "simple", "task-1").single().packet)
    }

    @Test
    fun unknownAndBusinessAmbiguousFindingsNeverMutateAutomatically(): Unit {
        listOf(packet("unknown", emptyList()), packet("ambiguous-preferred-label", emptyList())).forEach { packet ->
            val fixture = fixture()
            var called = false
            val updated = fixture.controller.repair("alice", "simple", "task-1", 0, packet) { called = true; "never" }
            assertTrue(!called)
            assertTrue(updated.task.status in setOf(AiTaskStatus.PAUSED, AiTaskStatus.FAILED))
        }
    }

    @Test
    fun packageAndTaskRepairCyclesAreBounded(): Unit {
        val fixture = fixture()
        var revision = 0L
        repeat(3) { index ->
            val updated = fixture.controller.repair(
                "alice", "simple", "task-1", revision, packet("invalid-language-tag", listOf("candidate-$index")),
            ) { "draft-${index + 1}" }
            revision = updated.revision
        }
        assertEquals("repair-cycle-limit", assertFailsWith<AiRepairFailure> {
            fixture.controller.repair(
                "alice", "simple", "task-1", revision, packet("invalid-language-tag", listOf("candidate-4")),
            ) { "never" }
        }.code)
    }

    private fun packet(code: String, candidates: List<String>): AiRepairPacket =
        AiRepairPacketBuilder(Clock.fixed(now, ZoneOffset.UTC), idFactory = { "packet-1" })
            .build(listOf(finding("finding-1", code, candidates = candidates))).single()

    private fun fixture(): RepairFixture {
        val store = InMemoryAiTaskStore()
        store.create(AiTaskWorkspace(taskFixture(status = AiTaskStatus.REPAIRING), privateDraftId = "draft-1"))
        return RepairFixture(store, AiRepairController(store, Clock.fixed(now, ZoneOffset.UTC)))
    }

    private data class RepairFixture(val store: InMemoryAiTaskStore, val controller: AiRepairController)
}
