package com.entio.web.ai

import com.entio.web.StagingWorkflowService
import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebPermission
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.ArrayDeque
import kotlin.io.path.isRegularFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

class AiConversationServiceTest {
    private val now: Instant = Instant.parse("2026-07-17T14:00:00Z")

    @Test
    fun focusedRequestPreparesPrivateDraftAndReturnsToolOutputInCallOrder(): Unit = runBlocking {
        val provider = FakeToolProvider(
            completed(calls = listOf(createClassCall("call-1", "Receivable Account")), responseId = "response-1"),
            completed(calls = listOf(createClassCall("call-2", "Payable Account")), responseId = "response-2"),
            completed(text = "I prepared two typed class edits for review.", responseId = "response-3"),
        )
        val fixture = fixture(provider)

        val result = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Create class Receivable Account and then create class Payable Account."),
        )

        assertEquals(AiRunStatus.READY_FOR_REVIEW, result.run.status)
        assertEquals("I prepared two typed class edits for review.", result.answer)
        val draft = fixture.drafts.get("alice", "simple", fixture.conversation.id, assertNotNull(result.draftId))
        assertEquals(listOf("Receivable Account", "Payable Account"), draft.items.map { (it.operation as AiTypedDraftOperation).request.label })
        assertEquals(listOf("call-1"), provider.requests[1].toolOutputs.map(OpenAiToolOutput::callId))
        assertEquals(listOf("call-2"), provider.requests[2].toolOutputs.map(OpenAiToolOutput::callId))
        assertTrue(provider.requests.all { it.userInput.contains("Create class Receivable Account") })
        assertEquals("response-2", provider.requests[2].previousResponseId)
        assertTrue(fixture.staging.snapshot("simple").entries.isEmpty())
    }

    @Test
    fun broadRequestRequiresConfirmationBeforeAnyDraftMutation(): Unit = runBlocking {
        val provider = FakeToolProvider(
            completed(calls = listOf(createClassCall("call-1", "Loan"))),
            completed(text = "The confirmed plan now has one private draft edit."),
        )
        val fixture = fixture(provider)

        val planned = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Design an ontology for a complete lending domain."),
        )

        assertEquals(AiRunStatus.AWAITING_PLAN_CONFIRMATION, planned.run.status)
        assertNotNull(planned.plan)
        assertNull(planned.draftId)
        assertTrue(provider.requests.isEmpty())
        assertTrue(fixture.drafts.list("alice", "simple", fixture.conversation.id).isEmpty())

        val confirmed = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Use Loan as the initial class.", AiConversationDecision.CONFIRM_PLAN),
        )

        assertEquals(planned.run.id, confirmed.run.id)
        assertEquals(AiRunStatus.READY_FOR_REVIEW, confirmed.run.status)
        assertEquals(1, fixture.drafts.get("alice", "simple", fixture.conversation.id, assertNotNull(confirmed.draftId)).items.size)
    }

    @Test
    fun materialAmbiguityPausesAndExplicitAnswerResumesWithBoundedConversationContext(): Unit = runBlocking {
        val provider = FakeToolProvider(
            completed(calls = listOf(createObjectPropertyCall("call-1", "manages account"))),
            completed(text = "I prepared the object property."),
        )
        val fixture = fixture(provider)

        val paused = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Create property manages account."),
        )

        assertEquals(AiRunStatus.AWAITING_CLARIFICATION, paused.run.status)
        assertNotNull(paused.clarificationQuestion)
        assertTrue(provider.requests.isEmpty())

        val resumed = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("It is an object property.", AiConversationDecision.ANSWER_CLARIFICATION),
        )

        assertEquals(paused.run.id, resumed.run.id)
        assertEquals(AiRunStatus.READY_FOR_REVIEW, resumed.run.status, resumed.answer)
        assertTrue(provider.requests.first().userInput.contains("Create property manages account."))
        assertTrue(provider.requests.first().userInput.contains("It is an object property."))
    }

    @Test
    fun followUpExplanationAndUndoPreserveConversationAndCurrentDraft(): Unit = runBlocking {
        val provider = FakeToolProvider(
            completed(calls = listOf(createClassCall("call-1", "Receivable Account"))),
            completed(text = "The class is in the private draft."),
            completed(text = "It remains private until explicit review submission.", responseId = "response-explain"),
            completed(calls = listOf(simpleCall("call-undo", "entio_draft_undo", """{"explanation":"Undo the class edit."}"""))),
            completed(text = "I undid the latest private draft revision."),
        )
        val fixture = fixture(provider)
        val first = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Create class Receivable Account."),
        )

        val explanation = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Why is this change still private?"),
        )
        assertEquals(first.draftId, explanation.draftId)
        assertTrue(provider.requests[2].userInput.contains("The class is in the private draft."))

        val undone = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Undo the last proposed item."),
        )
        val draft = fixture.drafts.get("alice", "simple", fixture.conversation.id, assertNotNull(undone.draftId))
        assertTrue(draft.items.isEmpty())
        assertEquals(listOf("add", "undo"), draft.revisions.map(AiDraftRevision::action))
        assertTrue(fixture.service.getConversation("alice", "simple", fixture.conversation.id).providerResponseIds.contains("response-explain"))
    }

    @Test
    fun draftRevisionPreservesConversationAndUpdatesTheExistingPrivateItem(): Unit = runBlocking {
        val provider = FakeToolProvider(
            completed(calls = listOf(createClassCall("call-add", "Receivable Account"))),
            completed(text = "The class is in the private draft."),
            completed(calls = listOf(updateClassCall("call-update", "draft-item-1", "Trade Receivable"))),
            completed(text = "I revised the existing private draft item."),
        )
        val fixture = fixture(provider)
        val initial = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Create class Receivable Account."),
        )

        val revised = fixture.service.send(
            fixture.scope,
            fixture.conversation.id,
            AiConversationTurnRequest("Revise the proposed class label to Trade Receivable."),
        )

        assertEquals(initial.draftId, revised.draftId)
        val draft = fixture.drafts.get("alice", "simple", fixture.conversation.id, assertNotNull(revised.draftId))
        assertEquals(listOf("add", "update"), draft.revisions.map(AiDraftRevision::action))
        assertEquals("Trade Receivable", (draft.items.single().operation as AiTypedDraftOperation).request.label)
        assertTrue(provider.requests.last().userInput.contains("Receivable Account"))
    }

    @Test
    fun unknownDuplicateReplayedAndUnauthorizedCallsFailWithoutDuplicateMutation(): Unit = runBlocking {
        val unknown = fixture(FakeToolProvider(completed(calls = listOf(simpleCall("unknown-1", "shell_exec", "{}")))))
        val unknownResult = unknown.service.send(unknown.scope, unknown.conversation.id, AiConversationTurnRequest("Create class A."))
        assertEquals(AiRunStatus.FAILED, unknownResult.run.status)

        val duplicateProvider = FakeToolProvider(
            completed(calls = listOf(createClassCall("same", "A"), createClassCall("same", "B"))),
        )
        val duplicate = fixture(duplicateProvider)
        val duplicateResult = duplicate.service.send(duplicate.scope, duplicate.conversation.id, AiConversationTurnRequest("Create class A."))
        assertEquals(AiRunStatus.FAILED, duplicateResult.run.status)
        assertTrue(duplicate.drafts.list("alice", "simple", duplicate.conversation.id).single().items.isEmpty())

        val replayProvider = FakeToolProvider(
            completed(calls = listOf(createClassCall("replay", "A"))),
            completed(text = "Created A."),
            completed(calls = listOf(createClassCall("replay", "B"))),
        )
        val replay = fixture(replayProvider)
        replay.service.send(replay.scope, replay.conversation.id, AiConversationTurnRequest("Create class A."))
        val replayed = replay.service.send(replay.scope, replay.conversation.id, AiConversationTurnRequest("Create class B."))
        assertEquals(AiRunStatus.FAILED, replayed.run.status)
        assertEquals(1, replay.drafts.list("alice", "simple", replay.conversation.id).single().items.size)

        val unauthorizedProvider = FakeToolProvider(completed(calls = listOf(createClassCall("unauthorized", "A"))))
        val unauthorized = fixture(unauthorizedProvider, includePreparePermission = false)
        val unauthorizedResult = unauthorized.service.send(
            unauthorized.scope,
            unauthorized.conversation.id,
            AiConversationTurnRequest("Create class A."),
        )
        assertEquals(AiRunStatus.FAILED, unauthorizedResult.run.status)
    }

    @Test
    fun requestToolTokenAndContextLimitsStopSafelyAndPreserveDraft(): Unit = runBlocking {
        val requestLimited = fixture(FakeToolProvider(completed(calls = listOf(createClassCall("call-1", "A")))))
        val requestResult = requestLimited.service.send(
            requestLimited.scope,
            requestLimited.conversation.id,
            AiConversationTurnRequest("Create class A.", policy = AiRunPolicy(maxProviderRequestsPerTurn = 1)),
        )
        assertEquals(AiRunStatus.LIMIT_REACHED, requestResult.run.status)
        assertEquals("provider-requests", requestResult.limits.single().kind)
        assertEquals(1, requestLimited.drafts.list("alice", "simple", requestLimited.conversation.id).single().items.size)

        val toolLimited = fixture(
            FakeToolProvider(
                completed(calls = listOf(createClassCall("call-1", "A"))),
                completed(calls = listOf(createClassCall("call-2", "B"))),
            ),
        )
        val toolResult = toolLimited.service.send(
            toolLimited.scope,
            toolLimited.conversation.id,
            AiConversationTurnRequest("Create class A.", policy = AiRunPolicy(maxCapabilityCallsPerTurn = 1)),
        )
        assertEquals(AiRunStatus.LIMIT_REACHED, toolResult.run.status)
        assertEquals("capability-calls", toolResult.limits.single().kind)
        assertEquals(1, toolLimited.drafts.list("alice", "simple", toolLimited.conversation.id).single().items.size)

        val tokenLimited = fixture(FakeToolProvider(completed(text = "Too expensive", usage = OpenAiUsage(10, 1, 11))))
        val tokenResult = tokenLimited.service.send(
            tokenLimited.scope,
            tokenLimited.conversation.id,
            AiConversationTurnRequest("Explain Customer.", policy = AiRunPolicy(maxInputTokens = 5)),
        )
        assertEquals(AiRunStatus.LIMIT_REACHED, tokenResult.run.status)
        assertEquals("input-tokens", tokenResult.limits.single().kind)

        val outputLimited = fixture(FakeToolProvider(completed(text = "Too verbose", usage = OpenAiUsage(1, 10, 11))))
        val outputResult = outputLimited.service.send(
            outputLimited.scope,
            outputLimited.conversation.id,
            AiConversationTurnRequest("Explain Customer.", policy = AiRunPolicy(maxOutputTokens = 5)),
        )
        assertEquals(AiRunStatus.LIMIT_REACHED, outputResult.run.status)
        assertEquals("output-tokens", outputResult.limits.single().kind)

        val editLimited = fixture(
            FakeToolProvider(
                completed(calls = listOf(createClassCall("edit-1", "A"), createClassCall("edit-2", "B"))),
            ),
        )
        val editResult = editLimited.service.send(
            editLimited.scope,
            editLimited.conversation.id,
            AiConversationTurnRequest("Create class A and create class B.", policy = AiRunPolicy(maxDraftEditsPerRun = 1)),
        )
        assertEquals(AiRunStatus.LIMIT_REACHED, editResult.run.status)
        assertEquals("draft-edits", editResult.limits.single().kind)
        assertEquals(1, editLimited.drafts.list("alice", "simple", editLimited.conversation.id).single().items.size)

        val elapsedClock = MutableClock(now)
        val elapsedProvider = FakeToolProvider(
            completed(calls = listOf(createClassCall("elapsed-1", "Elapsed Boundary"))),
            onRespond = { elapsedClock.advanceMillis(10) },
        )
        val elapsedLimited = fixture(elapsedProvider, clock = elapsedClock)
        val elapsedResult = elapsedLimited.service.send(
            elapsedLimited.scope,
            elapsedLimited.conversation.id,
            AiConversationTurnRequest("Create class Elapsed Boundary.", policy = AiRunPolicy(maxElapsedMillis = 5)),
        )
        assertEquals(AiRunStatus.LIMIT_REACHED, elapsedResult.run.status)
        assertEquals("elapsed-millis", elapsedResult.limits.single().kind)

        val contextProvider = FakeToolProvider(completed(text = "First answer."))
        val contextLimited = fixture(contextProvider)
        contextLimited.service.send(contextLimited.scope, contextLimited.conversation.id, AiConversationTurnRequest("Explain Customer."))
        val contextResult = contextLimited.service.send(
            contextLimited.scope,
            contextLimited.conversation.id,
            AiConversationTurnRequest("Explain Invoice.", policy = AiRunPolicy(maxConversationMessagesInContext = 2)),
        )
        assertEquals(AiRunStatus.LIMIT_REACHED, contextResult.run.status)
        assertEquals("context-messages", contextResult.limits.single().kind)
    }

    @Test
    fun cancellationPropagatesToProviderAndLeavesAuthoritativeCancelledRun(): Unit = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val provider = FakeToolProvider(blockStarted = started, blockGate = gate)
        val fixture = fixture(provider)
        val request = async {
            fixture.service.send(fixture.scope, fixture.conversation.id, AiConversationTurnRequest("Explain Customer."))
        }
        started.await()
        val run = fixture.runs.list("alice", "simple").single()

        fixture.service.cancel("alice", "simple", run.id)
        request.join()

        assertEquals(AiRunStatus.CANCELLED, fixture.runs.get("alice", "simple", run.id).status)
        assertTrue(provider.cancelled)
        assertTrue(fixture.service.events("alice", "simple", run.id).any { it.type == AiRunEventType.CANCELLED })
    }

    private fun fixture(
        provider: FakeToolProvider,
        includePreparePermission: Boolean = true,
        clock: Clock = Clock.fixed(now, ZoneOffset.UTC),
    ): Fixture {
        val source = listOf(Path.of("examples/simple-ontology"), Path.of("../examples/simple-ontology"))
            .first(Files::isDirectory)
            .toAbsolutePath()
            .normalize()
        val parent = Files.createTempDirectory("entio-ai-conversation")
        val project = parent.resolve("simple-ontology")
        Files.walk(source).use { paths ->
            paths.forEach { current ->
                val target = project.resolve(source.relativize(current).toString())
                if (current.isRegularFile()) {
                    Files.createDirectories(target.parent)
                    Files.copy(current, target, StandardCopyOption.REPLACE_EXISTING)
                } else {
                    Files.createDirectories(target)
                }
            }
        }
        val projects = InMemoryProjectRegistry(setOf(parent))
        projects.register("simple", "Simple", project)
        val staging = StagingWorkflowService(projects)
        val drafts = InMemoryAiDraftStore()
        val baseline = AiProjectBaselineService(projects).current("simple", "simple")
        val conversations = InMemoryAiConversationStore()
        val runs = InMemoryAiRunStore()
        val audits = InMemoryAiAuditStore()
        val ids = mutableMapOf<String, Int>()
        val workspace = AiPrivateDraftWorkspace(
            drafts,
            AiTypedEditCapabilityAdapter(staging),
            clock,
            itemIdFactory = { "draft-item-${ids.merge("draft-item", 1, Int::plus)}" },
        )
        val credentialStore = InMemoryAiCredentialStore()
        val credentials = AiCredentialService(credentialStore, provider)
        credentials.save("alice", AiCredentialRequest(provider.providerId, "test-key"))
        val service = AiConversationService(
            conversations = conversations,
            runs = runs,
            audits = audits,
            draftStore = drafts,
            draftWorkspace = workspace,
            registry = AiCapabilityRegistry(),
            dispatcher = DefaultAiCapabilityDispatcher(drafts = workspace),
            provider = provider,
            credentials = credentials,
            clock = clock,
            idFactory = { prefix -> "$prefix-${ids.merge(prefix, 1, Int::plus)}" },
        )
        val conversation = service.createConversation("alice", "simple")
        val permissions = buildSet {
            add(WebPermission.USE_AI.name)
            if (includePreparePermission) add(WebPermission.PREPARE_EDIT.name)
        }
        val scope = AiCapabilityScope(
            userId = "alice",
            projectId = "simple",
            conversationId = conversation.id,
            allowedSourceIds = listOf("simple", "shapes"),
            baselineFingerprint = baseline,
            role = "CONTRIBUTOR",
            permissions = permissions,
            availableFeatures = setOf(AiCapabilityFeatures.PRIVATE_DRAFT),
            createdAt = now,
        )
        return Fixture(service, conversations, runs, audits, drafts, staging, conversation, scope)
    }

    private fun completed(
        text: String = "",
        calls: List<OpenAiFunctionCall> = emptyList(),
        responseId: String? = null,
        usage: OpenAiUsage = OpenAiUsage(1, 1, 2),
    ): OpenAiResponsesResult = OpenAiResponsesResult.Completed(
        OpenAiCompletedResponse(responseId, text, calls, usage, emptyList()),
    )

    private fun createClassCall(id: String, label: String): OpenAiFunctionCall = simpleCall(
        id,
        AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
        """{"sourceId":"simple","editType":"create-class","rationale":"Add the reviewed concept.","label":"$label"}""",
    )

    private fun createObjectPropertyCall(id: String, label: String): OpenAiFunctionCall = simpleCall(
        id,
        AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
        """{"sourceId":"simple","editType":"create-object-property","rationale":"Add the reviewed relationship.","label":"$label"}""",
    )

    private fun updateClassCall(id: String, itemId: String, label: String): OpenAiFunctionCall = simpleCall(
        id,
        AiTypedEditCapabilityAdapter.UPDATE_ONTOLOGY_CAPABILITY,
        """{"itemId":"$itemId","sourceId":"simple","editType":"create-class","rationale":"Revise the proposed concept.","label":"$label"}""",
    )

    private fun simpleCall(id: String, name: String, arguments: String): OpenAiFunctionCall = OpenAiFunctionCall(id, name, arguments)

    private data class Fixture(
        val service: AiConversationService,
        val conversations: InMemoryAiConversationStore,
        val runs: InMemoryAiRunStore,
        val audits: InMemoryAiAuditStore,
        val drafts: InMemoryAiDraftStore,
        val staging: StagingWorkflowService,
        val conversation: AiConversation,
        val scope: AiCapabilityScope,
    )

    private class FakeToolProvider(
        vararg initialResponses: OpenAiResponsesResult,
        private val blockStarted: CompletableDeferred<Unit>? = null,
        private val blockGate: CompletableDeferred<Unit>? = null,
        private val onRespond: (() -> Unit)? = null,
    ) : AiToolLoopProvider, AiProviderClient {
        override val providerId: String = "openai"
        override val modelId: String = "gpt-5.2"
        override val promptVersion: String = "phase-7-test-v1"
        val requests: MutableList<OpenAiResponsesRequest> = mutableListOf()
        var cancelled: Boolean = false
        private val responses: ArrayDeque<OpenAiResponsesResult> = ArrayDeque(initialResponses.toList())

        override suspend fun test(apiKey: String): AiProviderTestResult = AiProviderTestResult.Passed("Accepted")

        override suspend fun respond(
            apiKey: String,
            request: OpenAiResponsesRequest,
            onEvent: suspend (OpenAiProviderEvent) -> Unit,
        ): OpenAiResponsesResult {
            requests += request
            blockStarted?.complete(Unit)
            try {
                blockGate?.await()
            } catch (cancelledFailure: kotlinx.coroutines.CancellationException) {
                cancelled = true
                throw cancelledFailure
            }
            onRespond?.invoke()
            return if (responses.isEmpty()) {
                OpenAiResponsesResult.Failed(OpenAiProviderFailure(OpenAiFailureCode.PROVIDER_ERROR, "No fake response.", false))
            } else {
                responses.removeFirst()
            }
        }
    }

    private class MutableClock(private var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current

        fun advanceMillis(milliseconds: Long): Unit {
            current = current.plusMillis(milliseconds)
        }
    }
}
