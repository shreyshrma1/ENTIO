package com.entio.web.ai

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiCapabilityBundleRegistryTest {
    private val bundles = AiCapabilityBundleRegistry()

    @Test
    fun mapsStagesToOneVersionedServerOwnedBundle(): Unit {
        assertEquals(AiCapabilityBundleId.EXPLORATION, bundles.bundleFor(taskFixture()).id)
        assertEquals(AiCapabilityBundleId.PLANNING, bundles.bundleFor(taskFixture(status = AiTaskStatus.PLANNING)).id)
        assertEquals(AiCapabilityBundleId.ONTOLOGY_EDITING, bundles.bundleFor(taskFixture(status = AiTaskStatus.READY_TO_EXECUTE)).id)
        assertEquals(AiCapabilityBundleId.ANALYSIS, bundles.bundleFor(taskFixture(status = AiTaskStatus.VALIDATING)).id)
        assertEquals(AiCapabilityBundleId.REPAIR, bundles.bundleFor(taskFixture(status = AiTaskStatus.REPAIRING)).id)
        assertEquals(AiCapabilityBundleId.HELP, bundles.bundleFor(taskFixture(), helpRequested = true).id)
        assertEquals(AiCapabilityBundleRegistry.VERSION, bundles.bundleFor(taskFixture()).version)
    }

    @Test
    fun helpAndExplorationCannotMutateAndNoBundleCanApproveOrApply(): Unit {
        listOf(false, true).forEach { help ->
            val frozen = bundles.freeze(taskFixture(), capabilityScope(), help)
            assertTrue(frozen.snapshot.definitions.all { it.access == AiCapabilityAccess.READ_ONLY })
        }
        val editing = bundles.freeze(
            taskFixture(status = AiTaskStatus.READY_TO_EXECUTE),
            capabilityScope().copy(availableFeatures = setOf(AiCapabilityFeatures.PRIVATE_DRAFT, AiCapabilityFeatures.LOCAL_SEMANTIC_READ)),
        )
        assertTrue(editing.snapshot.definitions.any { it.name == "entio_draft_validate" })
        assertTrue(editing.snapshot.definitions.any { it.name == AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY })
        AiTaskStatus.entries.filterNot(AiTaskStatus::terminal).forEach { status ->
            val names = bundles.freeze(taskFixture(status = status), capabilityScope()).snapshot.definitions.map { it.name }
            assertFalse(names.any { name -> listOf("approve", "reject", "apply", "rollback").any(name::contains) })
        }
    }

    @Test
    fun frozenRegistryRejectsCrossBundleAndStaleCalls(): Unit {
        val scope = capabilityScope()
        val exploration = bundles.freeze(taskFixture(), scope)
        val invocation = AiCapabilityInvocation(
            id = "call-1",
            capabilityName = AiTypedEditCapabilityAdapter.ADD_DEFINITION_CAPABILITY,
            registrySnapshotId = exploration.snapshot.id,
            userId = scope.userId,
            projectId = scope.projectId,
            conversationId = scope.conversationId,
            arguments = jacksonObjectMapper().readTree("{}"),
        )
        assertEquals("unknown-capability", assertFailsWith<AiCapabilityFailure> {
            exploration.registry.decode(invocation, exploration.snapshot, scope)
        }.code)
        val readInvocation = invocation.copy(capabilityName = "entio_project_summary")
        assertEquals("stale-capability-registry", assertFailsWith<AiCapabilityFailure> {
            exploration.registry.decode(readInvocation, exploration.snapshot, scope.copy(baselineFingerprint = "changed"))
        }.code)
    }
}

internal fun capabilityScope(): AiCapabilityScope = AiCapabilityScope(
    userId = "alice",
    projectId = "simple",
    conversationId = "conversation-1",
    allowedSourceIds = listOf("simple", "shapes"),
    baselineFingerprint = "project-fingerprint-1",
    role = "CONTRIBUTOR",
    permissions = setOf("USE_AI", "PREPARE_EDIT"),
    availableFeatures = setOf("PRIVATE_DRAFT", "FIBO", "REASONING", "SHACL"),
    createdAt = Instant.parse("2026-07-19T12:00:00Z"),
)
