package com.entio.web

import com.entio.core.EntioResult
import com.entio.core.FactOrigin
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.ReasoningResult
import com.entio.core.ReasoningRunStatus
import com.entio.core.InferredGraphState
import com.entio.core.InferredFactsOverlay
import com.entio.core.InferredReadState
import com.entio.core.ShaclGraphIdentity
import com.entio.core.ShaclValidationMode
import com.entio.core.ShaclValidationReport
import com.entio.core.ShaclValidationStatus
import com.entio.semantic.ProjectLoader
import com.entio.semantic.InferenceMaterializationIdentityContext
import com.entio.semantic.InferenceMaterializationService
import com.entio.semantic.ReasoningService
import com.entio.semantic.InferredFactsReadService
import com.entio.semantic.ShaclGraphLoader
import com.entio.semantic.ShaclValidationService
import com.entio.web.contract.ProjectRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.coroutineContext

private data class CachedReasoning(
    val jobId: String,
    val snapshot: WorkflowGraphSnapshot,
    val graphFingerprint: String,
    val summary: Map<String, Any?>,
    val result: ReasoningResult,
)

private class MutableSemanticJob(
    val id: String,
    val projectId: String,
    val request: ParsedWebJobRequest,
    val snapshot: WorkflowGraphSnapshot,
    val submittedByUserId: String,
    val queuedAt: String = Instant.now().toString(),
) {
    var state: WebSemanticJobState = WebSemanticJobState.Queued
    var phase: String = "queued"
    var message: String? = "Semantic work is queued."
    var startedAt: String? = null
    var completedAt: String? = null
    var resultSummary: Map<String, Any?> = emptyMap()
    var reasoningResult: ReasoningResult? = null
    var shaclReport: ShaclValidationReport? = null
    var error: String? = null
    var coroutine: Job? = null

    fun status(): WebSemanticJobStatus = WebSemanticJobStatus(
        id = id,
        projectId = projectId,
        kind = request.kind,
        scope = request.scope,
        status = state,
        phase = phase,
        message = message,
        graphFingerprint = snapshot.graphFingerprint,
        proposalFingerprint = snapshot.proposalFingerprint,
        queuedAt = queuedAt,
        startedAt = startedAt,
        completedAt = completedAt,
        resultSummary = resultSummary,
        error = error,
    )
}

internal data class RetainedMaterializationJob(
    val id: String,
    val projectId: String,
    val submittedByUserId: String,
    val graphFingerprint: String,
    val reasoningResult: ReasoningResult,
)

/** Schedules bounded semantic work without making HTTP requests wait for the reasoner. */
public class SemanticJobManager(
    private val staging: StagingWorkflowService,
    private val projectRegistry: ProjectRegistry,
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val reasoningService: ReasoningService = ReasoningService(),
    private val shaclGraphLoader: ShaclGraphLoader = ShaclGraphLoader(),
    private val shaclValidationService: ShaclValidationService = ShaclValidationService(),
    private val materializationService: InferenceMaterializationService = InferenceMaterializationService(),
    private val onUpdate: suspend (WebSemanticJobStatus) -> Unit = {},
) {
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs: MutableMap<String, MutableSemanticJob> = linkedMapOf()
    private val activeAppliedReasoning: MutableMap<String, String> = linkedMapOf()
    private val activeProposalReasoning: MutableMap<String, String> = linkedMapOf()
    private val latestAppliedReasoning: MutableMap<String, CachedReasoning> = linkedMapOf()
    private val latestProposalReasoning: MutableMap<String, CachedReasoning> = linkedMapOf()
    private val inferredFactsReadService: InferredFactsReadService = InferredFactsReadService()

    @Synchronized
    public fun submit(
        projectId: String,
        request: WebJobRequest,
        submittedByUserId: String = "system",
    ): WebSemanticJobStatus {
        require(submittedByUserId.isNotBlank()) { "semantic-job-submitting-user-required" }
        projectRegistry.find(projectId)
            ?: throw WebWorkflowFailure("unknown-project", "The requested project is not registered.")
        val parsed = request.parse()
        val snapshot = if (parsed.scope == WebJobScope.Proposal) {
            staging.inferredReadGraphSnapshot(projectId)
        } else {
            staging.graphSnapshot(projectId, parsed.scope)
        }
        val duplicateKey = snapshot.proposalFingerprint?.let { fingerprint ->
            if (parsed.kind == WebJobKind.Reasoning) "$projectId|$fingerprint" else null
        }
        duplicateKey?.let { key ->
            activeProposalReasoning[key]?.let { existingId ->
                jobs[existingId]?.takeIf { it.isActive() }?.let { return it.status() }
            }
        }
        if (parsed.kind == WebJobKind.Reasoning && parsed.scope == WebJobScope.Applied) {
            activeAppliedReasoning.remove(projectId)?.let { previousId ->
                jobs[previousId]?.let {
                    markStaleLocked(it, "Superseded by a newer applied-graph reasoning job.")
                    notify(it.status())
                }
            }
        }
        val id = "job-${UUID.randomUUID()}"
        val record = MutableSemanticJob(id, projectId, parsed, snapshot, submittedByUserId)
        latestAppliedReasoning[projectId]?.takeIf { cached ->
            parsed.kind == WebJobKind.Reasoning && parsed.scope == WebJobScope.Applied && cached.graphFingerprint != snapshot.graphFingerprint
        }?.let { cached -> record.resultSummary = mapOf("previousValidResult" to cached.summary) }
        jobs[id] = record
        if (parsed.kind == WebJobKind.Reasoning && parsed.scope == WebJobScope.Applied) activeAppliedReasoning[projectId] = id
        if (duplicateKey != null) activeProposalReasoning[duplicateKey] = id
        notify(record)
        record.coroutine = scope.launch { run(record) }
        return record.status()
    }

    @Synchronized
    public fun find(projectId: String, jobId: String): WebSemanticJobStatus? = jobs[jobId]
        ?.takeIf { it.projectId == projectId }
        ?.status()

    @Synchronized
    public fun details(
        projectId: String,
        jobId: String,
        limit: Int = 50,
        requestingUserId: String? = null,
    ): WebSemanticJobDetails? {
        require(limit in 1..100) { "semantic-job-detail-limit-must-be-between-1-and-100" }
        val record = jobs[jobId]?.takeIf { it.projectId == projectId } ?: return null
        val reasoning = record.reasoningResult
        val shacl = record.shaclReport
        val allFacts = reasoning?.toWebFacts().orEmpty()
        val materializationCandidates = if (
            reasoning != null &&
            requestingUserId == record.submittedByUserId &&
            record.request.kind == WebJobKind.Reasoning &&
            record.request.scope == WebJobScope.Applied &&
            record.state == WebSemanticJobState.Completed
        ) {
            materializationCandidates(record, reasoning)
        } else {
            emptyList()
        }
        val allUnsatisfiable = reasoning?.unsatisfiableClasses?.map { it.value }.orEmpty()
        val allFindings = shacl?.results?.map { result ->
            WebShaclFinding(
                resultId = result.resultId,
                severity = result.severity.name,
                message = result.message,
                focusNode = result.focusNode.value,
                path = result.path?.let { path ->
                    when (path) {
                        is com.entio.core.ShaclPath.DirectProperty -> path.propertyIri.value
                    }
                },
                shapeIri = result.shape.iri.value,
                shapeSourceId = result.shape.sourceId,
                constraint = result.constraint.name,
                value = result.value?.displayValue(),
                sourceId = result.sourceId,
            )
        }.orEmpty()
        val retainedStatus = record.status()
        val currentStatus = if (retainedStatus.status == WebSemanticJobState.Completed && !acceptsCurrentGraph(record)) {
            retainedStatus.copy(
                status = WebSemanticJobState.Stale,
                phase = "stale",
                message = "The retained semantic result no longer matches the current graph scope.",
            )
        } else {
            retainedStatus
        }
        return WebSemanticJobDetails(
            job = currentStatus,
            facts = allFacts.take(limit),
            materializationCandidates = materializationCandidates.take(limit),
            unsatisfiableClasses = allUnsatisfiable.take(limit),
            shaclFindings = allFindings.take(limit),
            unsupportedFeatures = reasoning?.unsupportedFeatures?.take(limit)?.map { finding ->
                listOf(finding.feature, finding.support.name, finding.message).filterNotNull().joinToString(": ")
            }.orEmpty(),
            warnings = reasoning?.warnings.orEmpty() + shacl?.warnings.orEmpty(),
            errors = reasoning?.errors.orEmpty() + shacl?.errors.orEmpty(),
            truncated = allFacts.size > limit || allUnsatisfiable.size > limit || allFindings.size > limit ||
                (reasoning?.unsupportedFeatures?.size ?: 0) > limit,
        )
    }

    @Synchronized
    internal fun retainedMaterializationJob(
        projectId: String,
        jobId: String,
        requestingUserId: String,
    ): RetainedMaterializationJob {
        val record = jobs[jobId]?.takeIf {
            it.projectId == projectId && it.submittedByUserId == requestingUserId
        } ?: throw WebWorkflowFailure("unknown-semantic-job", "The requested semantic job was not found.")
        if (record.request.kind != WebJobKind.Reasoning ||
            record.request.scope != WebJobScope.Applied ||
            record.state != WebSemanticJobState.Completed ||
            record.reasoningResult == null
        ) {
            throw WebWorkflowFailure("semantic-job-not-materializable", "Only a completed applied-graph reasoning job can be materialized.")
        }
        if (!acceptsCurrentGraph(record)) {
            throw WebWorkflowFailure("stale-semantic-job", "The retained reasoning result no longer matches the applied graph.")
        }
        val result = requireNotNull(record.reasoningResult)
        if (result.metadata.status != ReasoningRunStatus.Completed || !result.metadata.importClosureComplete) {
            throw WebWorkflowFailure("semantic-job-incomplete", "The retained reasoning result is incomplete.")
        }
        return RetainedMaterializationJob(
            id = record.id,
            projectId = record.projectId,
            submittedByUserId = record.submittedByUserId,
            graphFingerprint = record.snapshot.graphFingerprint,
            reasoningResult = result,
        )
    }

    @Synchronized
    public fun cancel(projectId: String, jobId: String): WebSemanticJobStatus {
        val record = jobs[jobId]?.takeIf { it.projectId == projectId }
            ?: throw WebWorkflowFailure("unknown-semantic-job", "Semantic job '$jobId' was not found.")
        if (record.isActive()) {
            record.coroutine?.cancel(CancellationException("Cancelled by reviewer."))
            markLocked(record, WebSemanticJobState.Cancelled, "cancelled", "Cancelled by reviewer.")
        }
        return record.status()
    }

    @Synchronized
    public fun invalidateProposalJobs(projectId: String): Unit {
        jobs.values
            .filter { it.projectId == projectId && it.request.scope == WebJobScope.Proposal && it.isActive() }
            .forEach { record ->
                record.coroutine?.cancel(CancellationException("The staged proposal changed."))
                markStaleLocked(record, "The staged proposal changed; this result is stale.")
                notify(record)
            }
        latestProposalReasoning.remove(projectId)
    }

    /**
     * Ensures one project-owned reasoning run exists for the current graph state.
     * Results are deliberately independent of the requesting user.
     */
    @Synchronized
    public fun ensureInferredRead(projectId: String, scope: WebJobScope): Unit {
        val snapshot = try {
            if (scope == WebJobScope.Proposal) staging.inferredReadGraphSnapshot(projectId)
            else staging.graphSnapshot(projectId, scope)
        } catch (_: WebWorkflowFailure) {
            if (scope == WebJobScope.Proposal) latestProposalReasoning.remove(projectId)
            return
        }
        val cached = if (scope == WebJobScope.Applied) latestAppliedReasoning[projectId] else latestProposalReasoning[projectId]
        if (cached?.snapshot?.graphFingerprint == snapshot.graphFingerprint &&
            cached.snapshot.proposalFingerprint == snapshot.proposalFingerprint
        ) return
        val active = jobs.values.any {
            it.projectId == projectId && it.request.kind == WebJobKind.Reasoning &&
                it.request.scope == scope && it.snapshot.graphFingerprint == snapshot.graphFingerprint &&
                it.snapshot.proposalFingerprint == snapshot.proposalFingerprint && it.isActive()
        }
        if (!active) submit(projectId, WebJobRequest(scope = scope.name.lowercase()), "system")
    }

    @Synchronized
    public fun inferredReadOverlay(projectId: String, scope: WebJobScope, enabled: Boolean): InferredFactsOverlay {
        val graphState = if (scope == WebJobScope.Applied) InferredGraphState.Applied else InferredGraphState.Proposal
        if (!enabled) return InferredFactsOverlay(graphState, InferredReadState.Off)
        ensureInferredRead(projectId, scope)
        val snapshot = try {
            if (scope == WebJobScope.Proposal) staging.inferredReadGraphSnapshot(projectId)
            else staging.graphSnapshot(projectId, scope)
        } catch (failure: WebWorkflowFailure) {
            return InferredFactsOverlay(graphState, InferredReadState.Unavailable, message = failure.message)
        }
        val cached = if (scope == WebJobScope.Applied) latestAppliedReasoning[projectId] else latestProposalReasoning[projectId]
        if (cached != null && cached.snapshot.graphFingerprint == snapshot.graphFingerprint &&
            cached.snapshot.proposalFingerprint == snapshot.proposalFingerprint
        ) {
            val project = when (val loaded = projectLoader.loadProject(projectRegistry.rootFor(projectId))) {
                is EntioResult.Failure -> return InferredFactsOverlay(graphState, InferredReadState.Failed, message = loaded.message)
                is EntioResult.Success -> loaded.value
            }
            return inferredFactsReadService.project(
                project,
                snapshot.graph,
                cached.jobId,
                cached.result,
                graphState,
                snapshot.proposalFingerprint,
            )
        }
        val failed = jobs.values.lastOrNull {
            it.projectId == projectId && it.request.scope == scope &&
                it.snapshot.graphFingerprint == snapshot.graphFingerprint &&
                it.state in setOf(WebSemanticJobState.Failed, WebSemanticJobState.Cancelled, WebSemanticJobState.Incomplete)
        }
        return if (failed != null) {
            InferredFactsOverlay(graphState, InferredReadState.Failed, message = failed.error ?: failed.message)
        } else {
            InferredFactsOverlay(graphState, InferredReadState.Updating, message = "Reasoning is updating for the current graph.")
        }
    }

    private suspend fun run(record: MutableSemanticJob): Unit {
        try {
            update(record, WebSemanticJobState.Running, "loading", "Loading the graph snapshot.")
            coroutineContext.ensureActive()
            when (record.request.kind) {
                WebJobKind.Reasoning -> runReasoning(record)
                WebJobKind.Shacl -> runShacl(record)
            }
        } catch (_: CancellationException) {
            synchronized(this) {
                if (record.state == WebSemanticJobState.Running || record.state == WebSemanticJobState.Queued) {
                    markLocked(record, WebSemanticJobState.Cancelled, "cancelled", "Semantic work was cancelled.")
                }
            }
        } catch (exception: RuntimeException) {
            update(record, WebSemanticJobState.Failed, "failed", "Semantic work failed.", exception.message ?: "Semantic work failed.")
        } finally {
            synchronized(this) {
                if (activeAppliedReasoning[record.projectId] == record.id) activeAppliedReasoning.remove(record.projectId)
                val key = record.snapshot.proposalFingerprint?.let { fingerprint -> "${record.projectId}|$fingerprint" }
                if (key != null && activeProposalReasoning[key] == record.id) activeProposalReasoning.remove(key)
            }
        }
    }

    private suspend fun runReasoning(record: MutableSemanticJob): Unit {
        update(record, WebSemanticJobState.Running, "reasoning", "Classifying the graph.")
        val result = when (val reasoning = reasoningService.reason(record.snapshot.graph)) {
            is EntioResult.Failure -> throw IllegalStateException(reasoning.message)
            is EntioResult.Success -> reasoning.value
        }
        coroutineContext.ensureActive()
        if (!acceptsCurrentGraph(record)) {
            update(record, WebSemanticJobState.Stale, "stale", "The graph changed while reasoning was running.")
            return
        }
        val summary = reasoningSummary(result)
        val complete = result.metadata.status == ReasoningRunStatus.Completed
        synchronized(this) {
            record.resultSummary = summary
            record.reasoningResult = result
            if (complete && record.request.scope == WebJobScope.Applied) {
                latestAppliedReasoning[record.projectId] = CachedReasoning(
                    record.id,
                    record.snapshot,
                    record.snapshot.graphFingerprint,
                    summary,
                    result,
                )
            }
            if (complete && record.request.scope == WebJobScope.Proposal) {
                latestProposalReasoning[record.projectId] = CachedReasoning(
                    record.id,
                    record.snapshot,
                    record.snapshot.graphFingerprint,
                    summary,
                    result,
                )
            }
        }
        update(
            record,
            if (complete) WebSemanticJobState.Completed else WebSemanticJobState.Incomplete,
            "completed",
            if (complete) "Reasoning completed." else "Reasoning completed with incomplete import coverage.",
        )
    }

    private suspend fun runShacl(record: MutableSemanticJob): Unit {
        update(record, WebSemanticJobState.Running, "shacl", "Validating the graph with SHACL.")
        val project = when (val loaded = projectLoader.loadProject(projectRegistry.rootFor(record.projectId))) {
            is EntioResult.Failure -> throw IllegalStateException(loaded.message)
            is EntioResult.Success -> loaded.value
        }
        val graphs = when (val loaded = shaclGraphLoader.load(project.ontologies)) {
            is EntioResult.Failure -> throw IllegalStateException(loaded.message)
            is EntioResult.Success -> loaded.value
        }
        val currentGraphs = graphs.copy(
            dataGraph = record.snapshot.graph,
            identity = graphs.identity.copy(dataGraphFingerprint = record.snapshot.graphFingerprint),
        )
        var inferred: GraphState? = null
        if (record.request.mode == ShaclValidationMode.AssertedAndInferred) {
            val cached = synchronized(this) { latestAppliedReasoning[record.projectId] }
                ?.takeIf { it.graphFingerprint == record.snapshot.graphFingerprint }
            if (cached == null) {
                update(record, WebSemanticJobState.Running, "reasoning", "A complete matching reasoning result is required first.")
                val reasoning = when (val result = reasoningService.reason(record.snapshot.graph)) {
                    is EntioResult.Failure -> throw IllegalStateException(result.message)
                    is EntioResult.Success -> result.value
                }
                coroutineContext.ensureActive()
                if (reasoning.metadata.status != ReasoningRunStatus.Completed) {
                    update(record, WebSemanticJobState.Incomplete, "incomplete", "Matching reasoning did not complete.")
                    return
                }
                inferred = inferredGraph(reasoning)
            } else {
                inferred = inferredGraph(cached.result)
            }
        }
        val report = when (val validated = shaclValidationService.validate(currentGraphs, record.request.mode, inferred)) {
            is EntioResult.Failure -> throw IllegalStateException(validated.message)
            is EntioResult.Success -> validated.value
        }
        coroutineContext.ensureActive()
        if (!acceptsCurrentGraph(record)) {
            update(record, WebSemanticJobState.Stale, "stale", "The graph changed while SHACL validation was running.")
            return
        }
        val complete = report.status == ShaclValidationStatus.Completed
        synchronized(this) {
            record.resultSummary = shaclSummary(report)
            record.shaclReport = report
        }
        update(
            record,
            if (complete) WebSemanticJobState.Completed else WebSemanticJobState.Failed,
            "completed",
            if (complete) "SHACL validation completed." else "SHACL validation did not complete.",
            report.errors.firstOrNull(),
        )
    }

    private fun acceptsCurrentGraph(record: MutableSemanticJob): Boolean = try {
        val current = if (record.request.scope == WebJobScope.Proposal) {
            staging.inferredReadGraphSnapshot(record.projectId)
        } else {
            staging.graphSnapshot(record.projectId, record.request.scope)
        }
        current.graphFingerprint == record.snapshot.graphFingerprint && current.proposalFingerprint == record.snapshot.proposalFingerprint
    } catch (_: WebWorkflowFailure) {
        false
    }

    private suspend fun update(
        record: MutableSemanticJob,
        state: WebSemanticJobState,
        phase: String,
        message: String,
        error: String? = null,
    ): Unit {
        val status = synchronized(this) {
            markLocked(record, state, phase, message, error)
            record.status()
        }
        if (record.submittedByUserId != "system") notify(status)
    }

    private fun markLocked(
        record: MutableSemanticJob,
        state: WebSemanticJobState,
        phase: String,
        message: String,
        error: String? = null,
    ): Unit {
        record.state = state
        record.phase = phase
        record.message = message
        record.error = error
        if (state == WebSemanticJobState.Running && record.startedAt == null) record.startedAt = Instant.now().toString()
        if (state !in setOf(WebSemanticJobState.Queued, WebSemanticJobState.Running)) record.completedAt = Instant.now().toString()
    }

    private fun markStaleLocked(record: MutableSemanticJob, message: String): Unit = markLocked(record, WebSemanticJobState.Stale, "stale", message)

    private fun notify(status: WebSemanticJobStatus): Unit {
        scope.launch { onUpdate(status) }
    }

    private fun notify(record: MutableSemanticJob): Unit {
        if (record.submittedByUserId != "system") notify(record.status())
    }

    private fun MutableSemanticJob.isActive(): Boolean = state == WebSemanticJobState.Queued || state == WebSemanticJobState.Running

    private fun reasoningSummary(result: ReasoningResult): Map<String, Any?> = mapOf(
        "consistency" to result.consistency.name,
        "classRelationships" to result.classRelationships.size,
        "individualTypes" to result.individualTypes.size,
        "propertyRelationships" to result.propertyRelationships.size,
        "unsatisfiableClasses" to result.unsatisfiableClasses.size,
        "warnings" to result.warnings,
        "metadataStatus" to result.metadata.status.name,
        "reasoner" to result.metadata.reasonerName,
    )

    private fun shaclSummary(report: com.entio.core.ShaclValidationReport): Map<String, Any?> = mapOf(
        "status" to report.status.name,
        "mode" to report.mode.name,
        "results" to report.results.size,
        "warnings" to report.warnings,
        "errors" to report.errors,
    )

    private fun inferredGraph(result: ReasoningResult): GraphState {
        val triples = buildSet {
            result.classRelationships.filter { it.origin == FactOrigin.Inferred }.forEach {
                add(GraphTriple(it.subject, Iri(RDFS_SUBCLASS), it.objectClass))
            }
            result.individualTypes.filter { it.origin == FactOrigin.Inferred }.forEach {
                add(GraphTriple(it.individual, Iri(RDF_TYPE), it.type))
            }
            result.propertyRelationships.filter { it.origin == FactOrigin.Inferred }.forEach {
                add(GraphTriple(it.subject, it.predicate, it.objectResource))
            }
        }
        return GraphState(triples)
    }

    private fun ReasoningResult.toWebFacts(): List<WebReasoningFact> = buildList {
        classRelationships.forEach { fact ->
            add(WebReasoningFact("class-relationship", fact.subject.value, RDFS_SUBCLASS, fact.objectClass.value, fact.origin.name, fact.sourceId))
        }
        individualTypes.forEach { fact ->
            add(WebReasoningFact("individual-type", fact.individual.value, RDF_TYPE, fact.type.value, fact.origin.name, fact.sourceId))
        }
        propertyRelationships.forEach { fact ->
            add(WebReasoningFact("property-relationship", fact.subject.value, fact.predicate.value, fact.objectResource.value, fact.origin.name, fact.sourceId))
        }
    }.sortedWith(compareBy(WebReasoningFact::kind, WebReasoningFact::subject, WebReasoningFact::predicate, WebReasoningFact::objectValue))

    private fun materializationCandidates(
        record: MutableSemanticJob,
        reasoning: ReasoningResult,
    ): List<WebInferenceMaterializationCandidate> {
        val project = when (val loaded = projectLoader.loadProject(projectRegistry.rootFor(record.projectId))) {
            is EntioResult.Failure -> return emptyList()
            is EntioResult.Success -> loaded.value
        }
        val labels = project.symbols
            .sortedWith(compareBy({ it.iri.value }, { it.sourceId }))
            .mapNotNull { symbol -> symbol.label?.let { symbol.iri.value to it } }
            .toMap()
        val staged = staging.materializationStagedIds(record.projectId)
        return materializationService.analyze(
            project,
            reasoning,
            InferenceMaterializationIdentityContext(
                record.projectId,
                record.submittedByUserId,
                record.id,
            ),
        ).mapNotNull { analysis ->
            val candidate = analysis.candidate ?: return@mapNotNull null
            val existingId = staged[candidate.semanticFactKey]
            val stageability = if (existingId != null) com.entio.core.InferenceStageability.AlreadyStaged else candidate.stageability
            WebInferenceMaterializationCandidate(
                factId = candidate.factId.value,
                kind = candidate.fact.kind.name,
                subject = candidate.fact.subject.value,
                subjectLabel = labels[candidate.fact.subject.value] ?: candidate.fact.subject.value,
                predicate = candidate.fact.predicate.value,
                predicateLabel = labels[candidate.fact.predicate.value] ?: candidate.fact.predicate.value,
                objectValue = candidate.fact.objectValue.value,
                objectLabel = labels[candidate.fact.objectValue.value] ?: candidate.fact.objectValue.value,
                stageability = stageability.name,
                reason = if (existingId != null) "This fact is already present in shared staging." else analysis.reason,
                sourceCandidates = candidate.sourceCandidates.map {
                    WebInferenceSourceCandidate(it.sourceId, it.selected)
                },
                selectedSourceId = candidate.selectedSourceId,
                existingStagedChangeId = existingId,
                importDependence = candidate.importDependence.state.name,
                importSourceIds = candidate.importDependence.sourceIds,
            )
        }
    }

    private fun com.entio.core.RdfTerm.displayValue(): String = when (this) {
        is com.entio.core.RdfResource -> value
        is com.entio.core.RdfLiteral -> buildString {
            append(lexicalForm)
            languageTag?.let { append('@').append(it) }
            datatypeIri?.let { append("^^").append(it.value) }
        }
    }

    private companion object {
        private const val RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
        private const val RDFS_SUBCLASS = "http://www.w3.org/2000/01/rdf-schema#subClassOf"
    }
}
