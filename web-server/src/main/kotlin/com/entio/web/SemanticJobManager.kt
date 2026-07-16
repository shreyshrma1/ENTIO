package com.entio.web

import com.entio.core.EntioResult
import com.entio.core.FactOrigin
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.ReasoningResult
import com.entio.core.ReasoningRunStatus
import com.entio.core.ShaclGraphIdentity
import com.entio.core.ShaclValidationMode
import com.entio.core.ShaclValidationStatus
import com.entio.semantic.ProjectLoader
import com.entio.semantic.ReasoningService
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
    val graphFingerprint: String,
    val summary: Map<String, Any?>,
    val result: ReasoningResult,
)

private class MutableSemanticJob(
    val id: String,
    val projectId: String,
    val request: ParsedWebJobRequest,
    val snapshot: WorkflowGraphSnapshot,
    val queuedAt: String = Instant.now().toString(),
) {
    var state: WebSemanticJobState = WebSemanticJobState.Queued
    var phase: String = "queued"
    var message: String? = "Semantic work is queued."
    var startedAt: String? = null
    var completedAt: String? = null
    var resultSummary: Map<String, Any?> = emptyMap()
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

/** Schedules bounded semantic work without making HTTP requests wait for the reasoner. */
public class SemanticJobManager(
    private val staging: StagingWorkflowService,
    private val projectRegistry: ProjectRegistry,
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val reasoningService: ReasoningService = ReasoningService(),
    private val shaclGraphLoader: ShaclGraphLoader = ShaclGraphLoader(),
    private val shaclValidationService: ShaclValidationService = ShaclValidationService(),
    private val onUpdate: suspend (WebSemanticJobStatus) -> Unit = {},
) {
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs: MutableMap<String, MutableSemanticJob> = linkedMapOf()
    private val activeAppliedReasoning: MutableMap<String, String> = linkedMapOf()
    private val activeProposalReasoning: MutableMap<String, String> = linkedMapOf()
    private val latestAppliedReasoning: MutableMap<String, CachedReasoning> = linkedMapOf()

    @Synchronized
    public fun submit(projectId: String, request: WebJobRequest): WebSemanticJobStatus {
        projectRegistry.find(projectId)
            ?: throw WebWorkflowFailure("unknown-project", "The requested project is not registered.")
        val parsed = request.parse()
        val snapshot = staging.graphSnapshot(projectId, parsed.scope)
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
        val record = MutableSemanticJob(id, projectId, parsed, snapshot)
        latestAppliedReasoning[projectId]?.takeIf { cached ->
            parsed.kind == WebJobKind.Reasoning && parsed.scope == WebJobScope.Applied && cached.graphFingerprint != snapshot.graphFingerprint
        }?.let { cached -> record.resultSummary = mapOf("previousValidResult" to cached.summary) }
        jobs[id] = record
        if (parsed.kind == WebJobKind.Reasoning && parsed.scope == WebJobScope.Applied) activeAppliedReasoning[projectId] = id
        if (duplicateKey != null) activeProposalReasoning[duplicateKey] = id
        notify(record.status())
        record.coroutine = scope.launch { run(record) }
        return record.status()
    }

    @Synchronized
    public fun find(projectId: String, jobId: String): WebSemanticJobStatus? = jobs[jobId]
        ?.takeIf { it.projectId == projectId }
        ?.status()

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
                notify(record.status())
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
            if (complete && record.request.scope == WebJobScope.Applied) {
                latestAppliedReasoning[record.projectId] = CachedReasoning(record.snapshot.graphFingerprint, summary, result)
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
        synchronized(this) { record.resultSummary = shaclSummary(report) }
        update(
            record,
            if (complete) WebSemanticJobState.Completed else WebSemanticJobState.Failed,
            "completed",
            if (complete) "SHACL validation completed." else "SHACL validation did not complete.",
            report.errors.firstOrNull(),
        )
    }

    private fun acceptsCurrentGraph(record: MutableSemanticJob): Boolean = try {
        val current = staging.graphSnapshot(record.projectId, record.request.scope)
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
        notify(status)
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

    private companion object {
        private const val RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
        private const val RDFS_SUBCLASS = "http://www.w3.org/2000/01/rdf-schema#subClassOf"
    }
}
