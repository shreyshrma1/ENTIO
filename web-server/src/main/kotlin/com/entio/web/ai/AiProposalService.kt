package com.entio.web.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.entio.core.BlankNodeResource
import com.entio.core.ChangeSet
import com.entio.core.GraphChange
import com.entio.core.GraphChangeKind
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.RdfTerm
import com.entio.core.ShaclGraphRole
import com.entio.web.FiboWebService
import com.entio.web.StagingWorkflowService
import com.entio.web.WebWorkflowFailure
import com.entio.web.contract.ProjectRegistry
import com.entio.web.contract.WebAiProposalEdit
import com.entio.web.contract.WebAiProposalRunResponse
import com.entio.web.contract.WebAiProposalStatus
import com.entio.web.contract.WebAiProposalValidation
import com.entio.web.contract.WebAiResponseMode
import com.entio.web.contract.WebAiConversationMessage
import com.entio.web.contract.WebAiChatSummary
import com.entio.web.contract.WebAiStatusUpdate
import com.entio.web.contract.WebDiffEntry
import com.entio.web.contract.WebPageRequest
import com.entio.semantic.ProjectLoader
import com.entio.core.EntioResult
import com.entio.web.ai.models.AiModelSelectionStatus
import com.entio.web.ai.models.AiUserProviderSettingsStore
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private data class ProposalState(
    val runId: String,
    val projectId: String,
    val userId: String,
    var prompt: String,
    var status: WebAiProposalStatus = WebAiProposalStatus.QUEUED,
    var responseMode: WebAiResponseMode = WebAiResponseMode.PROPOSAL,
    var summary: String? = null,
    val updates: MutableList<WebAiStatusUpdate> = mutableListOf(),
    var edits: List<WebAiProposalEdit> = emptyList(),
    var validation: WebAiProposalValidation? = null,
    var message: String? = null,
    var job: Job? = null,
    var stageAttempt: Long = 0,
    var currentGraph: GraphState? = null,
    var sourceGraphs: Map<String, Set<com.entio.core.GraphTriple>> = emptyMap(),
    var editableSourceIds: Set<String> = emptySet(),
    val messages: MutableList<WebAiConversationMessage> = mutableListOf(),
)

private data class ParsedAiResponse(
    val mode: WebAiResponseMode,
    val answer: String?,
    val summary: String?,
    val edits: List<WebAiProposalEdit>,
    val evidence: List<AiEvidenceClaim>,
)

/**
 * A structured proposal was returned, but one or more edits could not be
 * normalized. Keeping the edits that did parse lets the repair pass preserve
 * the user's complete request instead of starting over with only the failing
 * edit.
 */
private class ProposalParseFailure(
    message: String,
    cause: Throwable?,
    val partialEdits: List<WebAiProposalEdit>,
) : IllegalArgumentException(message, cause)

/** Owns private AI proposal runs; it never applies or writes ontology/configuration files. */
public class AiProposalService internal constructor(
    private val projectRegistry: ProjectRegistry,
    private val staging: StagingWorkflowService,
    private val credentials: AiCredentialStore,
    private val settingsStore: AiUserProviderSettingsStore,
    private val provider: AiProposalProvider,
    private val fibo: FiboWebService,
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val objectMapper: ObjectMapper = ObjectMapper(),
    private val ontologyContextBuilder: AiOntologyContextBuilder = AiOntologyContextBuilder(),
    private val semanticProposalValidator: AiSemanticProposalValidator = AiSemanticProposalValidator(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val runs: MutableMap<String, ProposalState> = linkedMapOf()

    public fun start(projectId: String, userId: String, prompt: String, runId: String? = null): WebAiProposalRunResponse {
        if (prompt.isBlank()) throw WebWorkflowFailure("missing-ai-prompt", "A request is required.")
        if (projectRegistry.find(projectId) == null) throw WebWorkflowFailure("unknown-project", "The requested project is not registered.")
        val settings = settingsStore.find(userId)
        if (settings?.selectionStatus != AiModelSelectionStatus.READY || settings.selectedModelId.isNullOrBlank()) {
            throw WebWorkflowFailure("ai-model-selection-required", "Select and verify an AI model before starting a proposal.")
        }
        val normalizedPrompt = prompt.trim()
        if (runId != null) {
            val state = owned(projectId, runId, userId)
            synchronized(state) {
                if (state.status == WebAiProposalStatus.STAGED || state.status == WebAiProposalStatus.REJECTED) {
                    throw WebWorkflowFailure("ai-run-closed", "That proposal was already accepted or rejected. Start a new proposal.")
                }
                if (state.status == WebAiProposalStatus.QUEUED || state.status == WebAiProposalStatus.RUNNING) {
                    throw WebWorkflowFailure("ai-run-in-progress", "Wait for the current AI response before sending a follow-up.")
                }
                state.prompt = normalizedPrompt
                state.messages += WebAiConversationMessage("user", normalizedPrompt, Instant.now().toString())
                state.updates.clear()
                state.status = WebAiProposalStatus.QUEUED
                state.message = "Entio is continuing the private proposal with your follow-up."
                state.job = scope.launch { execute(state, settings.selectedModelId, normalizedPrompt, continuing = true) }
                return response(state)
            }
        }
        val state = ProposalState(UUID.randomUUID().toString(), projectId, userId, normalizedPrompt)
        state.messages += WebAiConversationMessage("user", normalizedPrompt, Instant.now().toString())
        synchronized(runs) { runs[state.runId] = state }
        state.job = scope.launch { execute(state, settings.selectedModelId, normalizedPrompt, continuing = false) }
        return response(state)
    }

    public fun get(projectId: String, runId: String, userId: String): WebAiProposalRunResponse {
        val state = owned(projectId, runId, userId)
        synchronizeStagedState(state)
        synchronized(state) {
            // Older in-memory runs may have been generated before semantic
            // duplicate cleanup was added. Normalize them when reopened so a
            // user does not need to discard an otherwise valid proposal.
            state.edits = deduplicateEdits(state.edits)
        }
        return response(state)
    }

    public fun list(projectId: String, userId: String): List<WebAiChatSummary> {
        if (projectRegistry.find(projectId) == null) throw WebWorkflowFailure("unknown-project", "The requested project is not registered.")
        return synchronized(runs) {
            runs.values
                .filter { it.projectId == projectId && it.userId == userId }
                .map { state ->
                    synchronized(state) {
                        WebAiChatSummary(
                            runId = state.runId,
                            projectId = state.projectId,
                            title = state.messages.firstOrNull { it.role == "user" }?.content?.take(80) ?: "New chat",
                            status = state.status,
                            updatedAt = state.messages.lastOrNull()?.timestamp ?: Instant.now().toString(),
                        )
                    }
                }
                .sortedByDescending(WebAiChatSummary::updatedAt)
        }
    }

    public fun removeEdit(projectId: String, runId: String, editId: String, userId: String): WebAiProposalRunResponse {
        val state = owned(projectId, runId, userId)
        synchronized(state) {
            if (state.status != WebAiProposalStatus.READY) throw WebWorkflowFailure("ai-proposal-not-editable", "Only a ready private proposal can be edited.")
            state.edits = state.edits.filterNot { it.id == editId }
            validate(state)
            return response(state)
        }
    }

    public fun stage(projectId: String, runId: String, userId: String): WebAiProposalRunResponse {
        val state = owned(projectId, runId, userId)
        synchronized(state) {
            if (state.status != WebAiProposalStatus.READY) throw WebWorkflowFailure("ai-proposal-not-ready", "The AI proposal is not ready to stage.")
            val validation = state.validation ?: throw WebWorkflowFailure("ai-proposal-not-validated", "Validate the proposal before staging it.")
            if (!validation.valid) throw WebWorkflowFailure("ai-proposal-invalid", "The AI proposal has deterministic validation findings.")
            // The project may have changed while the proposal was waiting for
            // review. Re-check against freshly loaded source graphs so an edit
            // that has since become an add-existing/remove-missing no-op can
            // never enter the shared queue.
            val currentProject = when (val loaded = projectLoader.loadProject(projectRegistry.rootFor(projectId))) {
                is EntioResult.Failure -> throw WebWorkflowFailure("project-load-failed", loaded.message)
                is EntioResult.Success -> loaded.value
            }
            state.sourceGraphs = currentProject.ontologies.associate { ontology -> ontology.source.id to ontology.graph.triples }
            val staleNoOpIssues = noOpEditFindings(state)
            if (staleNoOpIssues.isNotEmpty()) {
                state.validation = WebAiProposalValidation(false, staleNoOpIssues)
                throw WebWorkflowFailure("ai-proposal-stale", "The project changed while this proposal was waiting for review; regenerate the affected edits.")
            }
            // If a previous staging attempt failed after adding some entries,
            // remove only this run's leftovers before retrying. This prevents
            // a retry from creating duplicate shared edits while preserving
            // other users' staged work.
            staging.snapshot(projectId).entries
                .filter { it.normalizedValues["aiRunId"] == state.runId }
                .forEach { staging.discard(projectId, it.id) }
            // A rejected shared proposal may be restored and staged again. The
            // idempotency key must identify that new staging attempt, rather
            // than replaying the entries from the rejected attempt.
            val attempt = ++state.stageAttempt
            state.edits.forEachIndexed { index, edit ->
                val change = toGraphChange(edit)
                staging.stageGraphChanges(
                    projectId,
                    edit.sourceId,
                    ChangeSet(listOf(change)),
                    "AI proposal: ${edit.summary.ifBlank { state.summary ?: state.prompt.take(80) }}",
                    userId,
                    "ai-${state.runId}-$attempt-$index-${edit.id}",
                    aiStageMetadata(edit, change.triple, currentProject.graph.triples, state.runId),
                )
            }
            staging.preview(projectId, userId)
            state.status = WebAiProposalStatus.STAGED
            state.message = "The proposal is staged in the shared review queue. It has not been applied."
            addUpdate(state, "Proposal staged for human review")
            return response(state)
        }
    }

    public fun reject(projectId: String, runId: String, userId: String): WebAiProposalRunResponse {
        val state = owned(projectId, runId, userId)
        synchronized(state) {
            if (state.status == WebAiProposalStatus.STAGED) runCatching { staging.reject(projectId, userId) }
            state.status = WebAiProposalStatus.REJECTED
            state.edits = emptyList()
            state.validation = null
            state.message = "The private AI proposal was rejected."
            addUpdate(state, "Proposal rejected")
            return response(state)
        }
    }

    public fun cancel(projectId: String, runId: String, userId: String): WebAiProposalRunResponse {
        val state = owned(projectId, runId, userId)
        synchronized(state) {
            if (state.status == WebAiProposalStatus.QUEUED || state.status == WebAiProposalStatus.RUNNING) {
                state.status = WebAiProposalStatus.CANCELLED
                state.message = "The AI proposal run was cancelled."
                addUpdate(state, "Run cancelled by the user")
                state.job?.cancel()
            }
            return response(state)
        }
    }

    private suspend fun execute(state: ProposalState, modelId: String, requestText: String, continuing: Boolean) {
        synchronized(state) {
            if (state.status == WebAiProposalStatus.CANCELLED) return
            state.status = WebAiProposalStatus.RUNNING
            if (continuing) addUpdate(state, "Continuing the existing private proposal with the new request")
            addUpdate(state, "Preparing the Entio project context")
        }
        try {
            val project = when (val loaded = projectLoader.loadProject(projectRegistry.rootFor(state.projectId))) {
                is EntioResult.Failure -> throw WebWorkflowFailure("project-load-failed", loaded.message)
                is EntioResult.Success -> loaded.value
            }
            val ontologyContext = project.resolvedSources.joinToString("\n\n") { source ->
                "SOURCE ${source.id} (${source.path.fileName}):\n${Files.readString(source.path)}"
            }
            synchronized(state) {
                state.currentGraph = project.graph
                state.sourceGraphs = project.ontologies.associate { ontology -> ontology.source.id to ontology.graph.triples }
            }
            val privateDraftEdits = synchronized(state) { state.edits.toList() }
            val effectiveDraftGraph = effectiveDraftGraph(project.graph, privateDraftEdits)
            val typedOntologyContext = ontologyContextBuilder.build(
                project.copy(graph = effectiveDraftGraph),
                requestText,
                ontologyContext,
                includesPrivateDraft = privateDraftEdits.isNotEmpty(),
            ).text
            val conversation = synchronized(state) {
                state.messages.dropLast(1).map { message ->
                    AiConversationTurn(message.role, message.content)
                }
            }
            val defaultSourceId = project.resolvedSources.firstOrNull { source ->
                ShaclGraphRole.Ontology in source.roles || ShaclGraphRole.Data in source.roles
            }?.id ?: project.resolvedSources.firstOrNull()?.id
            synchronized(state) {
                state.editableSourceIds = project.resolvedSources
                    .filter { source ->
                        (ShaclGraphRole.Ontology in source.roles || ShaclGraphRole.Data in source.roles || ShaclGraphRole.Shapes in source.roles) &&
                            Files.isWritable(source.path)
                    }
                    .map { it.id }
                    .toSet()
            }
            var currentProposal = proposalContext(privateDraftEdits)
            var validationFindings = emptyList<String>()
            var repairAttempt = 0
            var repairMode: String? = null
            val seenFailures = mutableSetOf<String>()
            var parsePreservedEdits = emptyList<WebAiProposalEdit>()
            synchronized(state) { addUpdate(state, "Determining whether to answer, clarify, or prepare a proposal") }
            val responseKind = requestProviderRoute(
                state,
                modelId,
                AiProposalGenerationInput(
                    userRequest = requestText,
                    ontologyContext = typedOntologyContext,
                    fiboContext = "",
                    conversation = conversation,
                    currentProposal = currentProposal,
                ),
            )
            synchronized(state) { addUpdate(state, "AI selected the ${responseKind.name.lowercase()} response path") }
            synchronized(state) { addUpdate(state, "Determining whether curated FIBO context is needed") }
            val externalContextRequest = requestProviderExternalContext(
                state,
                modelId,
                AiProposalGenerationInput(
                    userRequest = requestText,
                    ontologyContext = typedOntologyContext,
                    fiboContext = "",
                    conversation = conversation,
                    currentProposal = currentProposal,
                    responseKind = responseKind,
                ),
            )
            val fiboContext = if (externalContextRequest.useFibo) {
                val query = externalContextRequest.query.orEmpty()
                synchronized(state) { addUpdate(state, "Searching the pinned FIBO catalog") }
                searchFiboContext(state.projectId, query)
            } else {
                synchronized(state) { addUpdate(state, "AI determined curated FIBO context was not needed") }
                "The model did not request curated FIBO context for this request."
            }

            while (true) {
                synchronized(state) {
                    if (state.status == WebAiProposalStatus.CANCELLED) return
                    if (repairAttempt > 0) {
                        addUpdate(state, "Asking AI to diagnose and repair the private proposal")
                    }
                }
                val input = AiProposalGenerationInput(
                    userRequest = requestText,
                    ontologyContext = typedOntologyContext,
                    fiboContext = fiboContext,
                    conversation = conversation,
                    currentProposal = currentProposal,
                    validationFindings = validationFindings,
                    repairAttempt = repairAttempt,
                    repairMode = repairMode,
                    responseKind = responseKind,
                )
                val text = requestProvider(state, modelId, input)
                synchronized(state) {
                    if (state.status == WebAiProposalStatus.CANCELLED) return
                    addUpdate(state, "Interpreting the AI response")
                }

                val parsed = try {
                    parseResponse(text, defaultSourceId, responseKind).also { response ->
                        if (repairAttempt > 0 && repairMode == "proposal" && response.mode != WebAiResponseMode.PROPOSAL) {
                            throw IllegalArgumentException("A proposal repair response must remain in proposal mode.")
                        }
                    }
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    val finding = "The generated proposal could not be parsed or validated: ${failure.message ?: "unknown proposal error"}"
                    val parseFailure = failure as? ProposalParseFailure
                    if (parseFailure != null) {
                        parsePreservedEdits = deduplicateEdits(state.edits + parseFailure.partialEdits)
                    }
                    synchronized(state) {
                        state.validation = WebAiProposalValidation(false, listOf(finding))
                        addUpdate(state, "The generated proposal needs repair: $finding", listOf(finding))
                    }
                    currentProposal = if (parsePreservedEdits.isNotEmpty()) {
                        proposalContext(parsePreservedEdits)
                    } else {
                        text
                    }
                    val fingerprint = "parse:${text.hashCode()}:$finding"
                    if (!seenFailures.add(fingerprint)) {
                        finishUnrepaired(state)
                        return
                    }
                    validationFindings = listOf(finding)
                    repairMode = "proposal"
                    repairAttempt += 1
                    continue
                }

                if (parsed.mode == WebAiResponseMode.ANSWER || parsed.mode == WebAiResponseMode.CLARIFICATION) {
                    synchronized(state) {
                        if (state.status == WebAiProposalStatus.CANCELLED) return
                        val answer = parsed.answer ?: throw IllegalArgumentException("An answer or clarification response must include answer text.")
                        state.responseMode = parsed.mode
                        val evidence = ontologyContextBuilder.verifyEvidence(state.currentGraph ?: GraphState(), parsed.evidence, state.edits)
                        state.messages += WebAiConversationMessage("assistant", answer, Instant.now().toString(), evidence)
                        state.message = null
                        state.status = WebAiProposalStatus.READY
                        addUpdate(
                            state,
                            if (parsed.mode == WebAiResponseMode.ANSWER) "Answering from the ontology context" else "Asking for clarification before preparing edits",
                        )
                    }
                    return
                }

                synchronized(state) {
                    addUpdate(state, "Generating declarative ontology edits")
                    addUpdate(
                        state,
                        if (repairAttempt > 0) "Revalidating the repaired private proposal" else "Running deterministic validation",
                    )
                }
                val validation = synchronized(state) {
                    if (state.status == WebAiProposalStatus.CANCELLED) return
                    state.responseMode = WebAiResponseMode.PROPOSAL
                    // A follow-up preserves unaffected draft edits while allowing
                    // the model to revise or retract the requested portions.
                    state.edits = deduplicateEdits(
                        when {
                            parsePreservedEdits.isNotEmpty() -> parsePreservedEdits + parsed.edits
                            continuing && repairAttempt == 0 -> mergePrivateDraftEdits(privateDraftEdits, parsed.edits, state.sourceGraphs)
                            else -> parsed.edits
                        },
                    )
                    // The successfully parsed response is now the authoritative
                    // replacement. Only malformed-response recovery needs the
                    // one-time preservation merge above.
                    parsePreservedEdits = emptyList()
                    state.summary = parsed.summary ?: state.summary
                    validate(state)
                    state.validation ?: WebAiProposalValidation(false, listOf("The proposal could not be validated."))
                }

                if (validation.valid) {
                    synchronized(state) {
                        parsed.answer?.takeIf { it.isNotBlank() }?.let { answer ->
                            val evidence = ontologyContextBuilder.verifyEvidence(state.currentGraph ?: GraphState(), parsed.evidence, state.edits)
                            state.messages += WebAiConversationMessage("assistant", answer, Instant.now().toString(), evidence)
                        }
                        state.status = WebAiProposalStatus.READY
                        state.message = "Proposal ready for review"
                        addUpdate(state, "Proposal ready for review")
                    }
                    return
                }

                val findings = validation.messages.ifEmpty {
                    standardizeValidationFindings(
                        state,
                        listOf("Deterministic validation rejected the proposal without a detailed message."),
                    )
                }
                // Give the repair pass the normalized edits that actually
                // failed validation, rather than only the provider's raw
                // response. This makes prior domain/range axioms explicit so
                // the model can replace conflicting axioms instead of
                // appending another one.
                currentProposal = proposalContext(state.edits)
                val fingerprint = "validation:${text.hashCode()}:${findings.joinToString("|")}"
                if (!seenFailures.add(fingerprint)) {
                    finishUnrepaired(state)
                    return
                }
                synchronized(state) {
                    addUpdate(state, "Deterministic validation found ${findings.size} issue(s)", findings)
                }
                validationFindings = findings
                repairMode = "proposal"
                repairAttempt += 1
            }
        } catch (cancelled: CancellationException) {
            synchronized(state) {
                state.status = WebAiProposalStatus.CANCELLED
                state.message = "The AI proposal run was cancelled."
            }
        } catch (failure: Exception) {
            synchronized(state) {
                state.status = WebAiProposalStatus.FAILED
                state.message = failure.message ?: "The AI proposal could not be completed."
                addUpdate(state, "AI proposal failed: ${state.message}")
            }
        }
    }

    private suspend fun requestProvider(
        state: ProposalState,
        modelId: String,
        input: AiProposalGenerationInput,
    ): String {
        val result = credentials.withCredentialSuspending(state.userId) { providerId, apiKey ->
            if (providerId != provider.providerId) {
                AiProposalGenerationResult.Failed("The configured AI provider is not available.")
            } else {
                provider.generate(apiKey, modelId, input)
            }
        } ?: AiProposalGenerationResult.Failed("Configure an AI credential before starting a proposal.")
        return when (result) {
            is AiProposalGenerationResult.Failed -> throw WebWorkflowFailure("ai-provider-failed", result.message)
            is AiProposalGenerationResult.Completed -> result.text
        }
    }

    private suspend fun requestProviderRoute(
        state: ProposalState,
        modelId: String,
        input: AiProposalGenerationInput,
    ): AiResponseKind {
        return credentials.withCredentialSuspending(state.userId) { providerId, apiKey ->
            if (providerId != provider.providerId) {
                throw WebWorkflowFailure("ai-provider-failed", "The configured AI provider is not available.")
            }
            provider.route(apiKey, modelId, input)
        } ?: throw WebWorkflowFailure("ai-provider-failed", "Configure an AI credential before starting a proposal.")
    }

    private suspend fun requestProviderExternalContext(
        state: ProposalState,
        modelId: String,
        input: AiProposalGenerationInput,
    ): AiExternalContextRequest {
        return credentials.withCredentialSuspending(state.userId) { providerId, apiKey ->
            if (providerId != provider.providerId) {
                throw WebWorkflowFailure("ai-provider-failed", "The configured AI provider is not available.")
            }
            provider.requestExternalContext(apiKey, modelId, input)
        } ?: throw WebWorkflowFailure("ai-provider-failed", "Configure an AI credential before starting a proposal.")
    }

    private fun searchFiboContext(projectId: String, query: String): String {
        if (query.isBlank()) return "No FIBO search terms were requested by the model."
        val queries = query.split(',', ';', '\n').map(String::trim).filter(String::isNotBlank).distinct().take(8)
        val items = queries.flatMap { term ->
            // The AI may use every element in Entio's pinned, read-only FIBO
            // package. Curated foundations remain part of that package, but
            // domain modules such as FBC and LOAN are also valid retrieval
            // sources when the user explicitly asks for FIBO concepts.
            fibo.search(projectId, term, null, null, false, WebPageRequest(limit = 8)).page.items
        }.distinctBy { it.iri }.take(20)
        return buildString {
            appendLine("Server-retrieved read-only FIBO catalog results (curated foundations and wider pinned catalog) for: ${queries.joinToString(", ")}")
            if (items.isEmpty()) {
                appendLine("No matching entries were returned from the pinned FIBO catalog.")
            } else {
                items.forEach { item ->
                    appendLine("${item.kind} ${item.label} <${item.iri}> [catalogStatus=${item.catalogStatus}; module=${item.moduleIri}]: ${item.definitions.firstOrNull().orEmpty()}")
                }
            }
        }.trim()
    }

    private fun finishUnrepaired(state: ProposalState) {
        synchronized(state) {
            if (state.status == WebAiProposalStatus.CANCELLED) return
            // A proposal with deterministic findings is not review-ready. It
            // remains available for a follow-up, but cannot be staged or
            // presented as an approvable proposal until the repair succeeds.
            state.status = WebAiProposalStatus.FAILED
            state.message = "The proposal could not be made ready because deterministic validation findings remain. Send a follow-up to repair it."
            addUpdate(state, "Repair stopped after the AI repeated the same proposal failure", state.validation?.messages.orEmpty())
            addUpdate(state, "Proposal is not ready for review until the findings are repaired")
        }
    }

    private fun proposalContext(edits: List<WebAiProposalEdit>): String = if (edits.isEmpty()) {
        ""
    } else {
        objectMapper.writeValueAsString(mapOf("edits" to edits))
    }

    private fun mergePrivateDraftEdits(
        existing: List<WebAiProposalEdit>,
        incoming: List<WebAiProposalEdit>,
        sourceGraphs: Map<String, Set<GraphTriple>>,
    ): List<WebAiProposalEdit> {
        val merged = existing.toMutableList()
        incoming.forEach { next ->
            val sameIdIndex = merged.indexOfFirst { it.id == next.id }
            if (sameIdIndex >= 0) {
                val previous = merged.removeAt(sameIdIndex)
                if (isDraftCancellation(previous, next, sourceGraphs)) return@forEach
                merged += next
                return@forEach
            }

            val oppositeIndex = merged.indexOfFirst { previous ->
                previous.sourceId == next.sourceId &&
                    previous.operation != next.operation &&
                    sameTriple(previous, next)
            }
            if (oppositeIndex >= 0) {
                val previous = merged.removeAt(oppositeIndex)
                // A removal of a triple introduced only by the private draft
                // retracts that pending addition; it must not become a staged
                // removal against the applied source graph.
                if (isDraftCancellation(previous, next, sourceGraphs)) return@forEach
                // Conversely, adding back a triple that was only pending for
                // removal cancels that removal when the source already has it.
                if (previous.operation == "remove" && sourceContains(previous, sourceGraphs)) return@forEach
                merged += next
                return@forEach
            }
            merged += next
        }
        return merged
    }

    private fun isDraftCancellation(
        previous: WebAiProposalEdit,
        next: WebAiProposalEdit,
        sourceGraphs: Map<String, Set<GraphTriple>>,
    ): Boolean {
        if (previous.operation == "add" && next.operation == "remove") {
            return sameTriple(previous, next) && !sourceContains(previous, sourceGraphs)
        }
        if (previous.operation == "remove" && next.operation == "add") {
            return sameTriple(previous, next) && sourceContains(previous, sourceGraphs)
        }
        return false
    }

    private fun sameTriple(first: WebAiProposalEdit, second: WebAiProposalEdit): Boolean =
        toGraphChange(first).triple == toGraphChange(second).triple

    private fun sourceContains(edit: WebAiProposalEdit, sourceGraphs: Map<String, Set<GraphTriple>>): Boolean =
        sourceGraphs[edit.sourceId]?.contains(toGraphChange(edit).triple) == true

    /** Keep one review item for an exact RDF operation while preserving edits that differ by operation or value. */
    private fun deduplicateEdits(edits: List<WebAiProposalEdit>): List<WebAiProposalEdit> {
        val seen = linkedSetOf<String>()
        return edits.filter { edit ->
            val key = listOf(
                edit.operation,
                edit.subject,
                edit.predicate,
                edit.objectKind,
                edit.objectValue,
                edit.datatype.orEmpty(),
                edit.language.orEmpty(),
            ).joinToString("\u0000")
            seen.add(key)
        }
    }

    private fun effectiveDraftGraph(appliedGraph: GraphState, edits: List<WebAiProposalEdit>): GraphState {
        val triples = appliedGraph.triples.toMutableSet()
        edits.forEach { edit ->
            val triple = edit.toGraphTriple() ?: return@forEach
            if (edit.operation == "add") triples += triple else triples -= triple
        }
        return GraphState(triples)
    }

    private fun validate(state: ProposalState) {
        if (state.edits.isEmpty()) {
            state.validation = WebAiProposalValidation(false, listOf("Deterministic validation error at proposal level: the proposal contains no edits. Repair action: return at least one graph edit for the requested ontology outcome."))
            return
        }
        val unknownSourceIds = state.edits.map(WebAiProposalEdit::sourceId).toSet() - state.editableSourceIds
        if (unknownSourceIds.isNotEmpty()) {
            val allowed = state.editableSourceIds.sorted().joinToString().ifBlank { "none" }
            state.validation = WebAiProposalValidation(
                false,
                listOf("Deterministic validation error for source ID(s) '${unknownSourceIds.sorted().joinToString()}': the source is unknown or non-editable. Allowed source IDs: $allowed. Repair action: use an exact editable sourceId from the project context."),
            )
            return
        }
        // Reject no-op edits before asking the staging planner to build a
        // preview. The planner quite correctly refuses an entirely empty
        // effective change set, but that exception would hide the actionable
        // per-edit finding needed by the AI repair pass.
        val noOpIssues = noOpEditFindings(state)
        if (noOpIssues.isNotEmpty()) {
            val semanticIssues = state.currentGraph?.let { semanticProposalValidator.validate(it, state.edits) }.orEmpty()
            val findings = standardizeValidationFindings(state, semanticIssues + noOpIssues)
            state.validation = WebAiProposalValidation(
                valid = false,
                messages = findings,
                diff = emptyList(),
            )
            return
        }
        val bySource = state.edits.groupBy(WebAiProposalEdit::sourceId).mapValues { (_, edits) -> edits.map(::toGraphChange) }
        val preview = staging.previewGraphChanges(state.projectId, bySource, state.userId)
        val proposal = preview.proposal
        val semanticIssues = state.currentGraph?.let { semanticProposalValidator.validate(it, state.edits) }.orEmpty()
        val findings = standardizeValidationFindings(state, proposal?.validationMessages.orEmpty() + semanticIssues + noOpIssues)
        state.validation = WebAiProposalValidation(
            valid = proposal?.validationMessages?.isEmpty() == true && semanticIssues.isEmpty() && noOpIssues.isEmpty() && proposal.status == "READYFORREVIEW",
            messages = findings,
            diff = proposal?.diff.orEmpty(),
        )
    }

    /** Adds the responsible proposal source and exact triple to every deterministic finding. */
    private fun standardizeValidationFindings(state: ProposalState, findings: List<String>): List<String> = findings.map { finding ->
        if (finding.contains("source '") && finding.contains("Violation:")) return@map finding
        val indexed = Regex("changeSet\\.changes\\[(\\d+)]").find(finding)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val tokenMatches = state.edits.filter { edit ->
            listOf(edit.subject, edit.predicate, edit.objectValue, edit.id, edit.sourceId).any { token -> token.isNotBlank() && finding.contains(token) }
        }
        val proposalOrder = state.edits.groupBy(WebAiProposalEdit::sourceId)
            .toSortedMap()
            .values
            .flatten()
        val matching = when {
            tokenMatches.isNotEmpty() -> tokenMatches
            indexed != null -> proposalOrder.getOrNull(indexed)?.let(::listOf).orEmpty()
            else -> emptyList()
        }
        if (matching.size == 1) {
            val edit = matching.single()
            "$finding Source: edit '${edit.id}' in ontology source '${edit.sourceId}'. Violation triple: ${formatEditTriple(edit)}. Repair action: correct or remove this edit as indicated."
        } else if (matching.isNotEmpty()) {
            val sources = matching.joinToString("; ") { edit -> "edit '${edit.id}' in source '${edit.sourceId}': ${formatEditTriple(edit)}" }
            "$finding Source: multiple proposal edits may be involved: $sources. Repair action: inspect these exact edits and correct the violating one(s)."
        } else {
            val sourceSummary = state.edits.distinctBy(WebAiProposalEdit::sourceId).joinToString(", ") { "'${it.sourceId}'" }
            "$finding Source: proposal-level validation; affected ontology source(s): ${sourceSummary.ifBlank { "none" }}. Repair action: use the exact source and triple context from the proposal edits to correct this finding."
        }
    }.distinct()

    private fun formatEditTriple(edit: WebAiProposalEdit): String {
        val objectTerm = when (edit.objectKind.lowercase()) {
            "iri" -> "<${edit.objectValue}>"
            "blank" -> "_:${edit.objectValue.removePrefix("_:")}"
            else -> buildString {
                append('"')
                append(edit.objectValue.replace("\\", "\\\\").replace("\"", "\\\""))
                append('"')
                edit.language?.let { append("@$it") }
                edit.datatype?.let { append("^^<$it>") }
            }
        }
        return "<${edit.subject}> <${edit.predicate}> $objectTerm ."
    }

    private fun noOpEditFindings(state: ProposalState): List<String> {
        val graphs = state.sourceGraphs.mapValues { (_, triples) -> triples.toMutableSet() }.toMutableMap()
        val initialGraphs = graphs.mapValues { (_, triples) -> triples.toSet() }
        val perEditFindings = state.edits.mapNotNull { edit ->
            val graph = graphs[edit.sourceId] ?: return@mapNotNull null
            val triple = toGraphChange(edit).triple
            val changed = if (edit.operation == "add") graph.add(triple) else graph.remove(triple)
            if (changed) {
                null
            } else {
                val requestedState = if (edit.operation == "add") "already exists" else "does not exist"
                val repairAction = if (edit.operation == "add") {
                    "remove this edit from the proposal"
                } else {
                    "remove this edit from the proposal or correct its subject, predicate, or object"
                }
                "Deterministic no-op validation error for edit '${edit.id}' in source '${edit.sourceId}': " +
                    "the requested ${edit.operation} operation is invalid because the exact triple $requestedState. " +
                    "Violation: ${formatTriple(triple)}. Repair action: $repairAction."
            }
        }
        if (perEditFindings.isNotEmpty()) return perEditFindings
        return if (graphs.mapValues { (_, triples) -> triples.toSet() } == initialGraphs) {
            val proposedTriples = state.edits.map { edit -> formatTriple(toGraphChange(edit).triple) }.distinct().joinToString("; ")
            listOf("Deterministic no-op validation error: the proposal produces no net graph change; its edits cancel each other out or leave every source unchanged. Proposed triples: $proposedTriples. Repair action: keep only edits that change the source graph.")
        } else {
            emptyList()
        }
    }

    private fun formatTriple(triple: GraphTriple): String =
        "${formatTerm(triple.subjectResource)} ${formatTerm(triple.predicate)} ${formatTerm(triple.objectTerm)} ."

    private fun formatTerm(term: RdfTerm): String = when (term) {
        is RdfResource -> "<${term.value}>"
        is RdfLiteral -> buildString {
            append('"')
            append(term.lexicalForm.replace("\\", "\\\\").replace("\"", "\\\""))
            append('"')
            term.languageTag?.let { append("@$it") }
            term.datatypeIri?.let { append("^^<${it.value}>") }
        }
    }

    private fun aiStageMetadata(
        edit: WebAiProposalEdit,
        triple: GraphTriple,
        graph: Set<GraphTriple>,
        runId: String,
    ): Map<String, String> = buildMap {
        put("proposalSource", "native-ai")
        put("aiRunId", runId)
        put("aiEditId", edit.id)
        put("operation", edit.operation)
        put("subjectIri", triple.subjectResource.value)
        put("subjectLabel", preferredLabel(triple.subjectResource.value, graph))
        put("predicateIri", triple.predicate.value)
        put("predicateLabel", preferredLabel(triple.predicate.value, graph))
        when (val objectTerm = triple.objectTerm) {
            is RdfResource -> {
                put("objectIri", objectTerm.value)
                put("objectLabel", preferredLabel(objectTerm.value, graph))
            }
            is RdfLiteral -> {
                put("objectValue", objectTerm.lexicalForm)
                objectTerm.datatypeIri?.let { put("objectDatatypeIri", it.value) }
                objectTerm.languageTag?.let { put("objectLanguage", it) }
            }
        }
    }

    private fun preferredLabel(iri: String, graph: Set<GraphTriple>): String = graph
        .asSequence()
        .filter { it.subjectResource.value == iri && it.predicate.value in PREFERRED_LABEL_PREDICATES }
        .mapNotNull { (it.objectTerm as? RdfLiteral)?.lexicalForm?.takeIf(String::isNotBlank) }
        .firstOrNull()
        ?: iri.substringAfterLast('#').substringAfterLast('/').replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").replace('_', ' ').replace('-', ' ')

    private companion object {
        private val PREFERRED_LABEL_PREDICATES = setOf(
            "http://www.w3.org/2000/01/rdf-schema#label",
            "http://www.w3.org/2004/02/skos/core#prefLabel",
        )
    }

    private fun parseResponse(text: String, defaultSourceId: String?, responseKind: AiResponseKind): ParsedAiResponse {
        val cleaned = text.trim().removePrefix("```").removePrefix("json").removeSuffix("```").trim()
        require(cleaned.isNotBlank()) { "The AI response was empty." }
        val root = runCatching { objectMapper.readTree(cleaned) }.getOrElse { failure ->
            if (responseKind == AiResponseKind.Proposal || cleaned.startsWith("{") || cleaned.startsWith("[")) throw failure
            return ParsedAiResponse(
                mode = when (responseKind) {
                    AiResponseKind.Clarification -> WebAiResponseMode.CLARIFICATION
                    else -> WebAiResponseMode.ANSWER
                },
                answer = cleaned,
                summary = null,
                edits = emptyList(),
                evidence = emptyList(),
            )
        }
        require(root.isObject) { "The AI response was not a semantic AI response JSON object." }
        val mode = when (root.path("mode").asText("").lowercase()) {
            "answer" -> WebAiResponseMode.ANSWER
            "clarification" -> WebAiResponseMode.CLARIFICATION
            "proposal" -> WebAiResponseMode.PROPOSAL
            else -> if (root.path("edits").isArray) WebAiResponseMode.PROPOSAL else WebAiResponseMode.ANSWER
        }
        if (responseKind == AiResponseKind.Proposal) require(mode == WebAiResponseMode.PROPOSAL) {
            "The proposal response did not contain a structured proposal."
        }
        // Answer and clarification modes are intentionally unable to mutate the
        // private draft, even if a provider accidentally includes an edits field.
        val edits = if (mode == WebAiResponseMode.PROPOSAL) parseEdits(root, defaultSourceId) else emptyList()
        if (mode != WebAiResponseMode.PROPOSAL) {
            require(!root.path("answer").asText("").isBlank()) {
                "Answer and clarification responses must include answer text."
            }
        }
        return ParsedAiResponse(
            mode = mode,
            answer = root.path("answer").asText(null)?.trim()?.takeIf { it.isNotEmpty() },
            summary = root.path("summary").asText(null)?.trim()?.takeIf { it.isNotEmpty() },
            edits = edits,
            evidence = parseEvidence(root),
        )
    }

    private fun parseEvidence(root: JsonNode): List<AiEvidenceClaim> = root.path("evidence")
        .takeIf { it.isArray }
        ?.mapNotNull { node ->
            val subject = node.path("subject").asText("").trim()
            val predicate = node.path("predicate").asText("").trim()
            val objectKind = node.path("objectKind").asText("iri").lowercase()
            val objectValue = node.path("objectValue").asText("").trim()
            if (subject.isBlank() || predicate.isBlank() || objectValue.isBlank() || "://" !in subject || "://" !in predicate) {
                null
            } else {
                AiEvidenceClaim(
                    subject = subject,
                    predicate = predicate,
                    objectKind = objectKind,
                    objectValue = objectValue,
                    datatype = node.path("datatype").asText(null),
                    language = node.path("language").asText(null),
                    source = node.path("source").asText("current-ontology"),
                )
            }
        }
        .orEmpty()

    private fun parseEdits(root: JsonNode, defaultSourceId: String?): List<WebAiProposalEdit> {
        val parsed = mutableListOf<WebAiProposalEdit>()
        val failures = mutableListOf<String>()
        root.path("edits").forEachIndexed { index, node ->
            try {
                parsed += WebAiProposalEdit(
                id = node.path("id").asText("edit-${index + 1}"),
                sourceId = node.path("sourceId").asText("").ifBlank { defaultSourceId.orEmpty() }.requireNotBlank("sourceId"),
                operation = node.path("operation").asText().lowercase().also { require(it == "add" || it == "remove") { "operation must be add or remove" } },
                subject = node.path("subject").asText().requireAbsoluteIri("subject"),
                predicate = node.path("predicate").asText().requireAbsoluteIri("predicate"),
                objectKind = node.path("objectKind").asText().lowercase().also { require(it in setOf("iri", "literal", "blank")) { "objectKind is invalid" } },
                objectValue = node.path("objectValue").asText().requireNotBlank("objectValue"),
                datatype = node.path("datatype").asText(null),
                language = node.path("language").asText(null),
                summary = node.path("summary").asText("AI ontology edit"),
                rationale = node.path("rationale").asText(null),
                )
            } catch (failure: IllegalArgumentException) {
                failures += formatMalformedEditFinding(index, node, failure.message ?: "invalid proposal edit", defaultSourceId)
            }
        }
        if (failures.isNotEmpty()) {
            throw ProposalParseFailure(failures.joinToString(" "), null, parsed.toList())
        }
        return parsed
    }

    private fun formatMalformedEditFinding(index: Int, node: JsonNode, violation: String, defaultSourceId: String?): String {
        val id = node.path("id").asText("edit-${index + 1}")
        val sourceId = node.path("sourceId").asText("").ifBlank { defaultSourceId ?: "unspecified" }
        val subject = node.path("subject").asText("<missing subject>")
        val predicate = node.path("predicate").asText("<missing predicate>")
        val objectKind = node.path("objectKind").asText("<missing object kind>")
        val objectValue = node.path("objectValue").asText("<missing object>")
        return "Deterministic validation error for edit #${index + 1} '$id' in source '$sourceId': $violation. " +
            "Proposed triple: subject='$subject' predicate='$predicate' objectKind='$objectKind' object='$objectValue'. " +
            "Repair action: correct this edit and return the complete proposal again."
    }

    private fun toGraphChange(edit: WebAiProposalEdit): GraphChange {
        val objectTerm = when (edit.objectKind) {
            "iri" -> Iri(edit.objectValue.requireAbsoluteIri("objectValue"))
            "blank" -> BlankNodeResource(edit.objectValue.removePrefix("_:"))
            else -> RdfLiteral(edit.objectValue, edit.datatype?.let(::Iri), edit.language)
        }
        return GraphChange(if (edit.operation == "add") GraphChangeKind.Addition else GraphChangeKind.Removal, GraphTriple(Iri(edit.subject), Iri(edit.predicate), objectTerm))
    }

    private fun owned(projectId: String, runId: String, userId: String): ProposalState = synchronized(runs) {
        val state = runs[runId] ?: throw WebWorkflowFailure("unknown-ai-run", "The AI proposal run was not found.")
        if (state.projectId != projectId || state.userId != userId) throw WebWorkflowFailure("unknown-ai-run", "The AI proposal run was not found.")
        state
    }

    private fun synchronizeStagedState(state: ProposalState) {
        synchronized(state) {
            if (state.status != WebAiProposalStatus.STAGED) return
            val snapshot = staging.snapshot(state.projectId)
            val stillStaged = snapshot.entries.any { it.normalizedValues["aiRunId"] == state.runId }
            val wasApplied = snapshot.proposal?.status == "APPLIED"
            if (!stillStaged && !wasApplied && snapshot.proposal == null) {
                state.status = WebAiProposalStatus.READY
                state.message = "The staged changes were rejected. The private proposal is available again for review."
                addUpdate(state, "Staged changes rejected; private proposal restored")
            }
        }
    }

    private fun response(state: ProposalState): WebAiProposalRunResponse = synchronized(state) {
        WebAiProposalRunResponse(
            runId = state.runId,
            projectId = state.projectId,
            status = state.status,
            responseMode = state.responseMode,
            prompt = state.prompt,
            messages = state.messages.toList(),
            summary = state.summary,
            updates = state.updates.toList(),
            edits = state.edits,
            validation = state.validation,
            message = state.message,
        )
    }

    private fun addUpdate(state: ProposalState, message: String, details: List<String> = emptyList()) {
        state.updates += WebAiStatusUpdate(state.updates.size + 1, message, Instant.now().toString(), details)
    }

    private fun String.requireNotBlank(field: String): String = also { require(isNotBlank()) { "$field is required" } }
    private fun String.requireAbsoluteIri(field: String): String = requireNotBlank(field).also { require("://" in it) { "$field must be an absolute IRI" } }
}
