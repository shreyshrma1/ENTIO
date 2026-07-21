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
    var currentGraph: GraphState? = null,
    val messages: MutableList<WebAiConversationMessage> = mutableListOf(),
)

private data class ParsedAiResponse(
    val mode: WebAiResponseMode,
    val answer: String?,
    val summary: String?,
    val edits: List<WebAiProposalEdit>,
    val evidence: List<AiEvidenceClaim>,
)

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

    public fun get(projectId: String, runId: String, userId: String): WebAiProposalRunResponse = response(owned(projectId, runId, userId))

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
            state.edits.groupBy(WebAiProposalEdit::sourceId).forEach { (sourceId, edits) ->
                val changes = edits.map(::toGraphChange)
                staging.stageGraphChanges(projectId, sourceId, ChangeSet(changes), "AI proposal: ${state.summary ?: state.prompt.take(80)}", userId, "ai-${state.runId}-$sourceId")
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
            addUpdate(state, "Searching ontology for existing classes, properties, individuals, and constraints")
            addUpdate(state, "Searching FIBO for related concepts")
        }
        try {
            val project = when (val loaded = projectLoader.loadProject(projectRegistry.rootFor(state.projectId))) {
                is EntioResult.Failure -> throw WebWorkflowFailure("project-load-failed", loaded.message)
                is EntioResult.Success -> loaded.value
            }
            val ontologyContext = project.resolvedSources.joinToString("\n\n") { source ->
                "SOURCE ${source.id} (${source.path.fileName}):\n${Files.readString(source.path)}"
            }
            synchronized(state) { state.currentGraph = project.graph }
            val typedOntologyContext = ontologyContextBuilder.build(project, requestText, ontologyContext).text
            val fiboContext = runCatching {
                fibo.search(state.projectId, requestText, null, null, true, WebPageRequest(limit = 20)).page.items.joinToString("\n") { item ->
                    "${item.kind} ${item.label} <${item.iri}>: ${item.definitions.firstOrNull().orEmpty()}"
                }
            }.getOrDefault("")
            val conversationContext = synchronized(state) {
                state.messages.joinToString("\n") { message ->
                    val evidence = message.evidence.joinToString("; ") { item -> "${item.subject} ${item.predicate} ${item.objectValue}" }
                    "${message.role.uppercase()}: ${message.content}${if (evidence.isBlank()) "" else "\nEvidence: $evidence"}"
                }
            }
            val defaultSourceId = project.resolvedSources.firstOrNull { source ->
                ShaclGraphRole.Ontology in source.roles || ShaclGraphRole.Data in source.roles
            }?.id ?: project.resolvedSources.firstOrNull()?.id
            var currentProposal = synchronized(state) { proposalContext(state.edits) }
            var validationFindings = emptyList<String>()
            var repairAttempt = 0
            var repairMode: String? = null
            val seenFailures = mutableSetOf<String>()

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
                    conversationContext = conversationContext,
                    currentProposal = currentProposal,
                    validationFindings = validationFindings,
                    repairAttempt = repairAttempt,
                    repairMode = repairMode,
                )
                val text = requestProvider(state, modelId, input)
                synchronized(state) {
                    if (state.status == WebAiProposalStatus.CANCELLED) return
                    addUpdate(state, "Interpreting the AI response")
                }

                val parsed = try {
                    parseResponse(text, defaultSourceId).also { response ->
                        if (repairAttempt > 0 && repairMode == "proposal" && response.mode != WebAiResponseMode.PROPOSAL) {
                            throw IllegalArgumentException("A proposal repair response must remain in proposal mode.")
                        }
                    }
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    val finding = "The generated proposal could not be parsed or validated: ${failure.message ?: "unknown proposal error"}"
                    synchronized(state) {
                        state.validation = WebAiProposalValidation(false, listOf(finding))
                        addUpdate(state, "The generated proposal needs repair: $finding")
                    }
                    currentProposal = text
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
                    var needsEvidenceRepair = false
                    synchronized(state) {
                        if (state.status == WebAiProposalStatus.CANCELLED) return
                        val answer = parsed.answer ?: throw IllegalArgumentException("An answer or clarification response must include answer text.")
                        state.responseMode = parsed.mode
                        val evidence = ontologyContextBuilder.verifyEvidence(state.currentGraph ?: GraphState(), parsed.evidence, state.edits)
                        if (parsed.mode == WebAiResponseMode.ANSWER && evidence.isEmpty()) {
                            val finding = "The answer must cite at least one exact, verifiable ontology triple from the supplied context."
                            val fingerprint = "answer-evidence:${text.hashCode()}"
                            if (!seenFailures.add(fingerprint)) {
                                state.responseMode = parsed.mode
                                state.status = WebAiProposalStatus.READY
                                state.message = finding
                                addUpdate(state, "Answer stopped because it did not provide verifiable evidence")
                                return
                            }
                            validationFindings = listOf(finding)
                            currentProposal = text
                            repairMode = "answer"
                            repairAttempt += 1
                            addUpdate(state, "The answer needs a verifiable ontology citation; asking AI to repair it")
                            needsEvidenceRepair = true
                        } else {
                            state.messages += WebAiConversationMessage("assistant", answer, Instant.now().toString(), evidence)
                            state.message = null
                            state.status = WebAiProposalStatus.READY
                            addUpdate(
                                state,
                                if (parsed.mode == WebAiResponseMode.ANSWER) "Answering from the ontology context" else "Asking for clarification before preparing edits",
                            )
                        }
                    }
                    if (needsEvidenceRepair) continue
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
                    state.edits = parsed.edits
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
                    listOf("Deterministic validation rejected the proposal without a detailed message.")
                }
                currentProposal = text
                val fingerprint = "validation:${text.hashCode()}:${findings.joinToString("|")}"
                if (!seenFailures.add(fingerprint)) {
                    finishUnrepaired(state)
                    return
                }
                synchronized(state) {
                    addUpdate(state, "Deterministic validation found ${findings.size} issue(s)")
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

    private fun finishUnrepaired(state: ProposalState) {
        synchronized(state) {
            if (state.status == WebAiProposalStatus.CANCELLED) return
            state.status = WebAiProposalStatus.READY
            state.message = "The proposal still has deterministic validation findings. Review or send a follow-up to repair it."
            addUpdate(state, "Repair stopped after the AI repeated the same proposal failure")
            addUpdate(state, "Proposal ready for review with validation findings")
        }
    }

    private fun proposalContext(edits: List<WebAiProposalEdit>): String = edits.joinToString("\n") { edit ->
        "${edit.id} ${edit.operation} ${edit.subject} ${edit.predicate} ${edit.objectKind} ${edit.objectValue} — ${edit.summary} — ${edit.rationale.orEmpty()}"
    }

    private fun validate(state: ProposalState) {
        if (state.edits.isEmpty()) {
            state.validation = WebAiProposalValidation(false, listOf("The proposal contains no edits."))
            return
        }
        val bySource = state.edits.groupBy(WebAiProposalEdit::sourceId).mapValues { (_, edits) -> edits.map(::toGraphChange) }
        val preview = staging.previewGraphChanges(state.projectId, bySource, state.userId)
        val proposal = preview.proposal
        val semanticIssues = state.currentGraph?.let { semanticProposalValidator.validate(it, state.edits) }.orEmpty()
        state.validation = WebAiProposalValidation(
            valid = proposal?.validationMessages?.isEmpty() == true && semanticIssues.isEmpty() && proposal.status == "READYFORREVIEW",
            messages = proposal?.validationMessages.orEmpty() + semanticIssues,
            diff = proposal?.diff.orEmpty(),
        )
    }

    private fun parseResponse(text: String, defaultSourceId: String?): ParsedAiResponse {
        val cleaned = text.trim().removePrefix("```").removePrefix("json").removeSuffix("```").trim()
        val root = objectMapper.readTree(cleaned)
        require(root.isObject) { "The AI response was not a semantic AI response JSON object." }
        val mode = when (root.path("mode").asText("").lowercase()) {
            "answer" -> WebAiResponseMode.ANSWER
            "clarification" -> WebAiResponseMode.CLARIFICATION
            "proposal" -> WebAiResponseMode.PROPOSAL
            else -> if (root.path("edits").isArray) WebAiResponseMode.PROPOSAL else WebAiResponseMode.ANSWER
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

    private fun parseEdits(root: JsonNode, defaultSourceId: String?): List<WebAiProposalEdit> = root.path("edits").mapIndexed { index, node ->
            WebAiProposalEdit(
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

    private fun addUpdate(state: ProposalState, message: String) {
        state.updates += WebAiStatusUpdate(state.updates.size + 1, message, Instant.now().toString())
    }

    private fun String.requireNotBlank(field: String): String = also { require(isNotBlank()) { "$field is required" } }
    private fun String.requireAbsoluteIri(field: String): String = requireNotBlank(field).also { require("://" in it) { "$field must be an absolute IRI" } }
}
