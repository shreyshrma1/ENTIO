package com.entio.web.ai

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiSessionContractsTest {
    private val now: Instant = Instant.parse("2026-07-17T12:00:00Z")

    @Test
    fun defaultPolicyLeavesProviderTokenBudgetUnbounded(): Unit {
        val policy = AiRunPolicy()

        assertNull(policy.maxCapabilityCallsPerTurn)
        assertNull(policy.maxDraftEditsPerRun)
        assertEquals(3, policy.maxCorrectionCycles)
        assertEquals(1, policy.maxActiveRunsPerUserProject)
        assertEquals(20, policy.maxLocalEntitiesInContext)
        assertEquals(10, policy.maxFiboCandidatesPerSearch)
        assertNull(policy.maxProviderRequestsPerTurn)
        assertNull(policy.maxConversationMessagesInContext)
        assertNull(policy.maxElapsedMillis)
        assertNull(policy.maxInputTokens)
        assertNull(policy.maxOutputTokens)
    }

    @Test
    fun conversationStoreEnforcesOwnershipAndDeterministicMessageOrder(): Unit {
        val store = InMemoryAiConversationStore()
        val conversation = conversation()
        store.create(conversation)

        val updated = store.update(
            conversation.copy(
                messages = listOf(
                    message("later", now.plusSeconds(2)),
                    message("earlier", now.plusSeconds(1)),
                ),
                providerResponseIds = listOf("response-2", "response-1", "response-2"),
            ),
        )

        assertEquals(listOf("earlier", "later"), updated.messages.map(AiConversationMessage::id))
        assertEquals(listOf("response-2", "response-1"), updated.providerResponseIds)
        assertFailsWith<AiStateAccessFailure> { store.get("bob", "project-1", conversation.id) }
        assertFailsWith<AiStateAccessFailure> { store.get("alice", "project-2", conversation.id) }
    }

    @Test
    fun runStoreEnforcesOneActiveRunAndValidCancellation(): Unit {
        val store = InMemoryAiRunStore()
        val run = run("run-1")
        store.create(run)

        assertFailsWith<AiStateAccessFailure> { store.create(run("run-2")) }
        assertEquals(AiRunStatus.RUNNING, store.transition("alice", "project-1", run.id, AiRunStatus.RUNNING).status)
        val cancelled = store.cancel("alice", "project-1", run.id)
        assertEquals(AiRunStatus.CANCELLED, cancelled.status)
        assertTrue(cancelled.cancellationRequested)
        assertFailsWith<AiStateAccessFailure> {
            store.transition("alice", "project-1", run.id, AiRunStatus.RUNNING)
        }
        assertFailsWith<AiStateAccessFailure> { store.get("bob", "project-1", run.id) }
    }

    @Test
    fun draftStoreKeepsConversationScopeAndDeterministicRevisionOrder(): Unit {
        val store = InMemoryAiDraftStore()
        val draft = draft()
        store.create(draft)
        val operation = AiDraftOperationReference("create-class", "simple", "Create Account", "request-1")
        val updated = store.update(
            draft.copy(
                status = AiDraftStatus.EDITING,
                allowedSourceIds = listOf("simple", "shapes", "simple"),
                items = listOf(
                    AiDraftItem("item-2", 2, operation, "Second", createdAt = now, updatedAt = now),
                    AiDraftItem("item-1", 1, operation, "First", createdAt = now, updatedAt = now),
                ),
                revisions = listOf(
                    AiDraftRevision(2, "add", "Second", listOf("item-1", "item-2"), now),
                    AiDraftRevision(1, "add", "First", listOf("item-1"), now),
                ),
            ),
        )

        assertEquals(listOf("item-1", "item-2"), updated.items.map(AiDraftItem::id))
        assertEquals(listOf(1, 2), updated.revisions.map(AiDraftRevision::revision))
        assertEquals(listOf("shapes", "simple"), updated.allowedSourceIds)
        assertFailsWith<AiStateAccessFailure> { store.get("alice", "project-1", "conversation-2", draft.id) }
        assertFailsWith<AiStateAccessFailure> { store.get("bob", "project-1", draft.conversationId, draft.id) }
    }

    @Test
    fun responseAndAuditContractsHaveNoSecretFields(): Unit {
        val fieldNames = (AiResponse::class.java.declaredFields + AiAuditRecord::class.java.declaredFields)
            .map { it.name.lowercase() }

        assertFalse(fieldNames.any { it.contains("apikey") || (it.contains("credential") && it != "credentialgeneration") || it.contains("authorization") || it == "secret" })
        assertFalse(AiRunStatus.READY_FOR_REVIEW.canTransitionTo(AiRunStatus.RUNNING))
        assertTrue(AiRunStatus.QUEUED.canTransitionTo(AiRunStatus.CANCELLED))
    }

    private fun conversation(): AiConversation = AiConversation(
        id = "conversation-1",
        userId = "alice",
        projectId = "project-1",
        promptVersion = "phase-7-v1",
        createdAt = now,
        updatedAt = now,
    )

    private fun message(id: String, createdAt: Instant): AiConversationMessage = AiConversationMessage(
        id = id,
        role = AiMessageRole.USER,
        content = id,
        createdAt = createdAt,
    )

    private fun run(id: String): AiRun = AiRun(
        id = id,
        conversationId = "conversation-1",
        userId = "alice",
        projectId = "project-1",
        scope = AiCapabilityScope(
            userId = "alice",
            projectId = "project-1",
            conversationId = "conversation-1",
            allowedSourceIds = listOf("simple"),
            baselineFingerprint = "baseline",
            role = "contributor",
            permissions = setOf("ai:use"),
            availableFeatures = setOf("ai"),
            createdAt = now,
        ),
        createdAt = now,
        updatedAt = now,
    )

    private fun draft(): AiDraft = AiDraft(
        id = "draft-1",
        conversationId = "conversation-1",
        userId = "alice",
        projectId = "project-1",
        baselineFingerprint = "baseline",
        allowedSourceIds = listOf("simple"),
        createdAt = now,
        updatedAt = now,
    )
}
