package com.entio.web.ai

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiTaskContractsTest {
    @Test
    fun defaultPolicyMatchesApprovedInitialTaskLimits(): Unit {
        val policy = AiTaskPolicy()

        assertEquals(12, policy.maxWorkPackages)
        assertEquals(100, policy.maxDraftItems)
        assertEquals(20, policy.maxDraftItemsPerBatch)
        assertEquals(3, policy.maxRepairCyclesPerPackage)
        assertEquals(8, policy.maxRepairCyclesPerTask)
        assertEquals(30, policy.maxToolCallsPerPackage)
        assertEquals(200, policy.maxToolCallsPerTask)
        assertEquals(20, policy.maxContextEntities)
        assertEquals(50, policy.maxExpandedContextEntities)
        assertEquals(10, policy.maxFiboCandidatesPerSearch)
        assertEquals(20, policy.maxShaclFindingsInContext)
        assertEquals(1, policy.maxActiveMutatingTasksPerUserProject)
        assertEquals(3, policy.maxConcurrentReadOnlyTasksPerUserProject)
        assertTrue(policy.maxTaskElapsedMillis >= policy.maxPackageElapsedMillis)
    }

    @Test
    fun policyRejectsInternallyInconsistentLimits(): Unit {
        assertFailsWith<IllegalArgumentException> { AiTaskPolicy(maxDraftItems = 10, maxDraftItemsPerBatch = 11) }
        assertFailsWith<IllegalArgumentException> { AiTaskPolicy(maxRepairCyclesPerPackage = 4, maxRepairCyclesPerTask = 3) }
        assertFailsWith<IllegalArgumentException> { AiTaskPolicy(maxToolCallsPerPackage = 31, maxToolCallsPerTask = 30) }
        assertFailsWith<IllegalArgumentException> { AiTaskPolicy(maxContextEntities = 51, maxExpandedContextEntities = 50) }
        assertFailsWith<IllegalArgumentException> { AiTaskPolicy(maxPackageElapsedMillis = 2, maxTaskElapsedMillis = 1) }
    }

    @Test
    fun taskRequiresScopeAndInitialModelProvenanceToMatchItsIdentity(): Unit {
        val task = taskFixture()

        assertEquals(task.userId, task.scope.userId)
        assertEquals(task.projectId, task.scope.projectId)
        assertEquals(task.conversationId, task.scope.conversationId)
        assertEquals(1, task.initialExecutionSegment.ordinal)
        assertEquals(task.initialExecutionSegment, AiTaskWorkspace(task).executionSegments.single())

        assertFailsWith<IllegalArgumentException> { task.copy(userId = "bob") }
        assertFailsWith<IllegalArgumentException> {
            task.copy(initialExecutionSegment = task.initialExecutionSegment.copy(ordinal = 2))
        }
    }

    @Test
    fun terminalAndWorkspaceMutationFlagsAreExplicit(): Unit {
        assertTrue(AiTaskStatus.SUBMITTED_FOR_REVIEW.terminal)
        assertTrue(AiTaskStatus.FAILED.terminal)
        assertTrue(AiTaskStatus.CANCELLED.terminal)
        assertFalse(AiTaskStatus.READY_FOR_REVIEW.terminal)
        assertFalse(AiTaskStatus.STALE.allowsWorkspaceMutation)
        assertFalse(AiTaskStatus.LIMIT_REACHED.allowsWorkspaceMutation)
        assertTrue(AiTaskStatus.EXECUTING.allowsWorkspaceMutation)
    }
}

internal fun taskFixture(
    id: String = "task-1",
    userId: String = "alice",
    projectId: String = "simple",
    status: AiTaskStatus = AiTaskStatus.UNDERSTANDING,
): AiTask {
    val now = Instant.parse("2026-07-19T12:00:00Z")
    return AiTask(
        id = id,
        userId = userId,
        projectId = projectId,
        conversationId = "conversation-1",
        objective = "Model the approved lending concepts.",
        type = AiTaskType.DOMAIN_MODELING,
        size = AiTaskSize.LARGE,
        scope = AiTaskScopeSnapshot(
            userId = userId,
            projectId = projectId,
            conversationId = "conversation-1",
            allowedSourceIds = listOf("shapes", "simple", "simple"),
            projectFingerprint = "project-fingerprint-1",
            role = "CONTRIBUTOR",
            permissions = setOf("PREPARE_EDIT", "USE_AI"),
            availableFeatures = setOf("PRIVATE_DRAFT"),
            capturedAt = now,
        ),
        initialExecutionSegment = AiTaskExecutionSegment(
            id = "segment-1",
            ordinal = 1,
            modelBinding = AiRunModelBinding(
                providerId = "openai",
                modelId = "gpt-test",
                catalogVersion = "catalog-test",
                credentialGeneration = 7,
                promptVersion = "prompt-test",
                requestPolicyVersion = "request-test",
            ),
            createdAt = now,
        ),
        status = status,
        createdAt = now,
        updatedAt = now,
    )
}
