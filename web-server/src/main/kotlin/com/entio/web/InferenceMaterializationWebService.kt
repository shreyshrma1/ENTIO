package com.entio.web

import com.entio.core.EntioResult
import com.entio.core.InferenceFactId
import com.entio.core.InferenceMaterializationBatch
import com.entio.core.InferenceMaterializationProvenance
import com.entio.core.InferenceMaterializationSelection
import com.entio.core.InferenceStageability
import com.entio.core.PreparedInferenceMaterialization
import com.entio.core.PreparedInferenceMaterializationBatch
import com.entio.semantic.ImportClosureResolver
import com.entio.semantic.InferenceMaterializationIdentityContext
import com.entio.semantic.InferenceMaterializationService
import com.entio.semantic.ProjectLoader
import com.entio.semantic.ReasoningService
import com.entio.web.contract.ProjectRegistry
import com.entio.web.contract.WebInferenceMaterializationMapping
import com.entio.web.contract.WebInferenceMaterializationRequest
import com.entio.web.contract.WebInferenceMaterializationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Reconfirms retained inferences before delegating one atomic batch to shared staging. */
public class InferenceMaterializationWebService(
    private val jobs: SemanticJobManager,
    private val staging: StagingWorkflowService,
    private val projectRegistry: ProjectRegistry,
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val importClosureResolver: ImportClosureResolver = ImportClosureResolver(),
    private val reasoningService: ReasoningService = ReasoningService(),
    private val materializationService: InferenceMaterializationService = InferenceMaterializationService(),
    private val clock: Clock = Clock.systemUTC(),
    private val timeoutMillis: Long = 60_000,
) {
    private val projectLocks: ConcurrentHashMap<String, Mutex> = ConcurrentHashMap()

    public suspend fun materialize(
        projectId: String,
        jobId: String,
        userId: String,
        request: WebInferenceMaterializationRequest,
    ): WebInferenceMaterializationResponse {
        val lock = projectLocks.computeIfAbsent(projectId) { Mutex() }
        if (!lock.tryLock()) {
            throw WebWorkflowFailure("materialization-in-progress", "Another materialization request is already running for this project.")
        }
        val deadlineNanos = System.nanoTime() + timeoutMillis * 1_000_000
        return try {
            withTimeout(timeoutMillis) {
                runInterruptible(Dispatchers.Default) {
                    materializeBlocking(projectId, jobId, userId, request, deadlineNanos)
                }
            }
        } catch (failure: kotlinx.coroutines.TimeoutCancellationException) {
            throw WebWorkflowFailure("materialization-timeout", "Inference rechecking exceeded the bounded materialization time.")
        } finally {
            lock.unlock()
        }
    }

    private fun materializeBlocking(
        projectId: String,
        jobId: String,
        userId: String,
        request: WebInferenceMaterializationRequest,
        deadlineNanos: Long,
    ): WebInferenceMaterializationResponse {
        if (request.idempotencyKey.isBlank()) {
            throw WebWorkflowFailure("missing-idempotency-key", "A materialization idempotency key is required.")
        }
        val selections = try {
            InferenceMaterializationBatch(
                request.selections.map {
                    InferenceMaterializationSelection(
                        factId = InferenceFactId(it.factId),
                        selectedSourceId = it.targetSourceId,
                    )
                },
            )
        } catch (failure: IllegalArgumentException) {
            throw WebWorkflowFailure("invalid-materialization-request", failure.message ?: "The materialization request is invalid.")
        }

        val retained = jobs.retainedMaterializationJob(projectId, jobId, userId)
        val project = when (val loaded = projectLoader.loadProject(projectRegistry.rootFor(projectId))) {
            is EntioResult.Failure -> throw WebWorkflowFailure("project-load-failed", loaded.message)
            is EntioResult.Success -> loaded.value
        }
        val currentFingerprint = webGraphFingerprint(project.graph)
        if (currentFingerprint != retained.graphFingerprint) {
            throw WebWorkflowFailure("stale-semantic-job", "The applied project changed after reasoning completed.")
        }
        val identity = InferenceMaterializationIdentityContext(projectId, userId, jobId)
        val retainedAnalyses = materializationService.analyze(project, retained.reasoningResult, identity)
        val retainedByFactId = retainedAnalyses.mapNotNull { analysis ->
            analysis.candidate?.let { it.factId to analysis }
        }.toMap()
        val selectedRetained = selections.selections.map { selection ->
            retainedByFactId[selection.factId]
                ?: throw WebWorkflowFailure("unknown-inference-fact", "A selected inference fact was not found in the retained job.")
        }
        if (selectedRetained.mapNotNull { it.candidate?.semanticFactKey }.distinct().size != selectedRetained.size) {
            throw WebWorkflowFailure("duplicate-inference-fact", "The request contains duplicate semantic facts.")
        }

        val importClosure = when (val resolved = importClosureResolver.resolve(project.config, project.resolvedSources)) {
            is EntioResult.Failure -> throw WebWorkflowFailure("import-closure-failed", "The current import closure could not be verified.")
            is EntioResult.Success -> resolved.value
        }
        val freshReasoning = when (val result = reasoningService.reason(project.graph, importClosure)) {
            is EntioResult.Failure -> throw WebWorkflowFailure("reasoning-recheck-failed", "Fresh reasoning did not complete.")
            is EntioResult.Success -> result.value
        }
        if (freshReasoning.metadata.status != com.entio.core.ReasoningRunStatus.Completed ||
            !freshReasoning.metadata.importClosureComplete
        ) {
            throw WebWorkflowFailure("reasoning-recheck-incomplete", "Fresh reasoning was incomplete.")
        }
        val freshBySemanticKey = materializationService.analyze(project, freshReasoning, identity)
            .mapNotNull { analysis -> analysis.candidate?.let { it.semanticFactKey to analysis } }
            .toMap()
        val stagedAt = Instant.now(clock)
        val prepared = selections.selections.mapIndexed { index, selection ->
            val retainedAnalysis = selectedRetained[index]
            val retainedCandidate = requireNotNull(retainedAnalysis.candidate)
            val fresh = freshBySemanticKey[retainedCandidate.semanticFactKey]
                ?: throw WebWorkflowFailure("inference-no-longer-entailed", "A selected fact is no longer inferred by the current graph.")
            val candidate = requireNotNull(fresh.candidate)
            val targetSourceId = selectedTarget(candidate.sourceCandidates.map { it.sourceId }, selection.selectedSourceId)
            if (fresh.stageability !in setOf(InferenceStageability.Stageable, InferenceStageability.AmbiguousSource)) {
                throw WebWorkflowFailure(
                    stageabilityCode(fresh.stageability),
                    fresh.reason,
                )
            }
            val edit = fresh.edit
                ?: throw WebWorkflowFailure("unsupported-inference-type", "The selected inference has no typed-edit conversion.")
            val triple = fresh.triple
                ?: throw WebWorkflowFailure("unsupported-inference-type", "The selected inference has no asserted triple.")
            val provenance = InferenceMaterializationProvenance(
                inferenceKind = candidate.fact.kind,
                reasoningJobId = jobId,
                graphFingerprint = currentFingerprint,
                factId = retainedCandidate.factId,
                semanticFactKey = candidate.semanticFactKey,
                fact = candidate.fact,
                stagedByUserId = userId,
                stagedAt = stagedAt,
                targetSourceId = targetSourceId,
                importDependence = candidate.importDependence,
            )
            PreparedInferenceMaterialization(
                factId = retainedCandidate.factId,
                semanticFactKey = candidate.semanticFactKey,
                fact = candidate.fact,
                targetSourceId = targetSourceId,
                edit = edit,
                triple = triple,
                provenance = provenance,
            )
        }
        if (Thread.currentThread().isInterrupted || System.nanoTime() >= deadlineNanos) {
            throw WebWorkflowFailure("materialization-timeout", "Inference rechecking exceeded the bounded materialization time.")
        }
        val staged = staging.stageMaterializations(
            projectId,
            userId,
            request.idempotencyKey,
            PreparedInferenceMaterializationBatch(prepared),
        )
        return WebInferenceMaterializationResponse(
            projectId = projectId,
            reasoningJobId = jobId,
            graphFingerprint = currentFingerprint,
            mappings = staged.batch.mappings.map {
                WebInferenceMaterializationMapping(it.factId.value, it.stagedChangeId)
            },
            staging = staged.staging,
        )
    }

    private fun selectedTarget(candidates: List<String>, requested: String?): String = when {
        candidates.isEmpty() -> throw WebWorkflowFailure("no-writable-source", "No writable local source declares the assertion subject.")
        candidates.size == 1 && requested == null -> candidates.single()
        requested == null -> throw WebWorkflowFailure("ambiguous-source", "Select one of the writable local target sources.")
        requested !in candidates -> throw WebWorkflowFailure("invalid-source-selection", "The selected target source is not valid for this fact.")
        else -> requested
    }

    private fun stageabilityCode(state: InferenceStageability): String = when (state) {
        InferenceStageability.AlreadyAsserted -> "inference-already-asserted"
        InferenceStageability.NoWritableSource -> "no-writable-source"
        InferenceStageability.AmbiguousSource -> "ambiguous-source"
        InferenceStageability.ImportDependencyUnsafe -> "import-derived-materialization-unsafe"
        InferenceStageability.InvalidPredicate -> "invalid-object-property"
        InferenceStageability.MissingEntity -> "missing-inference-entity"
        InferenceStageability.UnsupportedTerm -> "unsupported-inference-term"
        InferenceStageability.UnsupportedType -> "unsupported-inference-type"
        InferenceStageability.Stale -> "stale-semantic-job"
        InferenceStageability.AlreadyStaged -> "inference-already-staged"
        InferenceStageability.Stageable -> "stageable"
    }
}
