package com.entio.web

import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.GraphState
import com.entio.core.Iri
import com.entio.core.OntologyGraphEdge
import com.entio.core.OntologyGraphExpansionCategory
import com.entio.core.OntologyGraphInitialQuery
import com.entio.core.OntologyGraphLimits
import com.entio.core.OntologyGraphNeighborhoodQuery
import com.entio.core.OntologyGraphNode
import com.entio.core.OntologyGraphNodeId
import com.entio.core.OntologyGraphPage
import com.entio.core.OntologyGraphPageCursor
import com.entio.core.ShaclGraphRole
import com.entio.semantic.OntologyGraphService
import com.entio.semantic.ProjectLoader
import com.entio.web.contract.ProjectRegistry
import com.entio.web.contract.WebOntologyGraphEdge
import com.entio.web.contract.WebOntologyGraphLimits
import com.entio.web.contract.WebOntologyGraphNode
import com.entio.web.contract.WebOntologyGraphNodeId
import com.entio.web.contract.WebOntologyGraphNodeSummary
import com.entio.web.contract.WebOntologyGraphResponse
import com.entio.web.contract.WebOntologyGraphSource
import com.entio.web.contract.toWeb
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

public class OntologyGraphWebFailure(public val code: String, message: String) : IllegalArgumentException(message)

/** Enforces web scope and maps the deterministic semantic graph without exposing cursor internals. */
public class OntologyGraphWebService(
    private val projectRegistry: ProjectRegistry,
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val graphService: OntologyGraphService = OntologyGraphService(),
    private val clock: Clock = Clock.systemUTC(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val continuationLifetime: Duration = Duration.ofMinutes(10),
    private val inferredFacts: InferredFactsWebService? = null,
) {
    private val continuations: MutableMap<String, ContinuationState> = linkedMapOf()

    public fun initial(
        userId: String,
        projectId: String,
        requestedSourceIds: Set<String>,
        seedSourceId: String?,
        seedIri: String?,
        expectedFingerprint: String?,
        continuation: String?,
        includeAppliedInferred: Boolean = false,
        includeProposalInferred: Boolean = false,
    ): WebOntologyGraphResponse {
        val project = load(projectId)
        val sourceIds = selectedSources(project, requestedSourceIds)
        val seed = parseOptionalNode(seedSourceId, seedIri, sourceIds)
        val fingerprint = fingerprint(project, sourceIds)
        requireExpectedFingerprint(expectedFingerprint, fingerprint)
        val overlays = inferredFacts?.readCore(projectId, includeAppliedInferred, includeProposalInferred).orEmpty()
        val inferredFingerprint = overlays.joinToString("|") { "${it.graphState}:${it.state}:${it.graphFingerprint}:${it.proposalFingerprint}" }
        val signature = QuerySignature(userId, projectId, sourceIds, fingerprint, inferredFingerprint, "initial", seed?.stableKey.orEmpty(), emptySet())
        val cursor = consume(continuation, signature)
        val limits = OntologyGraphLimits.Initial
        val page = try {
            graphService.initial(project, OntologyGraphInitialQuery(sourceIds, seed, cursor, limits), overlays)
        } catch (failure: IllegalArgumentException) {
            if (failure.message == "missing-graph-entity") throw OntologyGraphWebFailure("unknown-graph-entity", "The requested local graph entity was not found.")
            throw failure
        }
        return response(projectId, project, sourceIds, fingerprint, limits, page, signature)
    }

    public fun neighborhood(
        userId: String,
        projectId: String,
        requestedSourceIds: Set<String>,
        entitySourceId: String?,
        entityIri: String?,
        requestedCategories: Set<String>,
        expectedFingerprint: String?,
        continuation: String?,
        includeAppliedInferred: Boolean = false,
        includeProposalInferred: Boolean = false,
    ): WebOntologyGraphResponse {
        val project = load(projectId)
        val sourceIds = selectedSources(project, requestedSourceIds)
        val entity = parseRequiredNode(entitySourceId, entityIri, sourceIds)
        val categories = parseCategories(requestedCategories)
        val fingerprint = fingerprint(project, sourceIds)
        requireExpectedFingerprint(expectedFingerprint, fingerprint)
        val overlays = inferredFacts?.readCore(projectId, includeAppliedInferred, includeProposalInferred).orEmpty()
        val inferredFingerprint = overlays.joinToString("|") { "${it.graphState}:${it.state}:${it.graphFingerprint}:${it.proposalFingerprint}" }
        val signature = QuerySignature(userId, projectId, sourceIds, fingerprint, inferredFingerprint, "neighborhood", entity.stableKey, categories.mapTo(sortedSetOf()) { it.name })
        val cursor = consume(continuation, signature)
        val limits = OntologyGraphLimits.Expansion
        val page = try {
            graphService.neighborhood(project, OntologyGraphNeighborhoodQuery(sourceIds, entity, categories, cursor, limits), overlays)
        } catch (failure: IllegalArgumentException) {
            if (failure.message == "missing-graph-entity") throw OntologyGraphWebFailure("unknown-graph-entity", "The requested local graph entity was not found.")
            throw failure
        }
        return response(projectId, project, sourceIds, fingerprint, limits, page, signature)
    }

    private fun load(projectId: String): EntioProject {
        if (projectRegistry.find(projectId) == null) throw OntologyGraphWebFailure("unknown-project", "The requested project is not registered.")
        return when (val result = projectLoader.loadProject(projectRegistry.rootFor(projectId))) {
            is EntioResult.Success -> result.value
            is EntioResult.Failure -> throw OntologyGraphWebFailure("project-load-failed", "The registered project could not be loaded.")
        }
    }

    private fun selectedSources(project: EntioProject, requested: Set<String>): Set<String> {
        val allowed = project.ontologies.filter { ShaclGraphRole.Ontology in it.source.roles || ShaclGraphRole.Data in it.source.roles }.map { it.source.id }.toSet()
        val selected = if (requested.isEmpty()) allowed else requested
        if (selected.isEmpty() || !allowed.containsAll(selected)) throw OntologyGraphWebFailure("invalid-graph-source", "Every selected source must be a local ontology or data source in this project.")
        return selected
    }

    private fun parseOptionalNode(sourceId: String?, iri: String?, sources: Set<String>): OntologyGraphNodeId? {
        if (sourceId == null && iri == null) return null
        return parseRequiredNode(sourceId, iri, sources)
    }

    private fun parseRequiredNode(sourceId: String?, iri: String?, sources: Set<String>): OntologyGraphNodeId {
        val source = sourceId?.takeIf(String::isNotBlank) ?: throw OntologyGraphWebFailure("missing-graph-entity", "A source id and entity IRI are required.")
        val value = iri?.takeIf(String::isNotBlank) ?: throw OntologyGraphWebFailure("missing-graph-entity", "A source id and entity IRI are required.")
        if (source !in sources) throw OntologyGraphWebFailure("invalid-graph-source", "The entity source must be selected and local to this project.")
        return OntologyGraphNodeId(source, Iri(value))
    }

    private fun parseCategories(values: Set<String>): Set<OntologyGraphExpansionCategory> {
        if (values.isEmpty()) throw OntologyGraphWebFailure("missing-graph-category", "At least one expansion category is required.")
        return values.mapTo(linkedSetOf()) { value ->
            OntologyGraphExpansionCategory.entries.firstOrNull { it.name.equals(value, true) }
                ?: throw OntologyGraphWebFailure("invalid-graph-category", "An expansion category is not supported.")
        }
    }

    private fun fingerprint(project: EntioProject, sourceIds: Set<String>): String = webGraphFingerprint(
        GraphState(project.ontologies.filter { it.source.id in sourceIds }.flatMapTo(mutableSetOf()) { it.graph.triples }),
    )

    private fun requireExpectedFingerprint(expected: String?, current: String) {
        if (expected != null && expected != current) throw OntologyGraphWebFailure("stale-graph-fingerprint", "The ontology graph changed; refresh before loading more graph data.")
    }

    private fun consume(token: String?, signature: QuerySignature): OntologyGraphPageCursor {
        prune()
        if (token == null) return OntologyGraphPageCursor()
        val state = continuations[token] ?: throw OntologyGraphWebFailure("invalid-graph-continuation", "The graph continuation is unknown or expired.")
        if (state.signature != signature) throw OntologyGraphWebFailure("invalid-graph-continuation", "The graph continuation does not belong to this request scope.")
        continuations.remove(token)
        return state.cursor
    }

    private fun response(projectId: String, project: EntioProject, sourceIds: Set<String>, fingerprint: String, limits: OntologyGraphLimits, page: OntologyGraphPage, signature: QuerySignature): WebOntologyGraphResponse {
        val continuation = page.nextCursor?.let { cursor ->
            idFactory().also { continuations[it] = ContinuationState(signature, cursor, clock.instant().plus(continuationLifetime)) }
        }
        return WebOntologyGraphResponse(
            projectId = projectId,
            graphFingerprint = fingerprint,
            sources = project.config.ontologySources.filter { it.id in sourceIds }.sortedBy { it.id }.map { WebOntologyGraphSource(it.id, it.id) },
            loadKind = page.loadKind.name,
            seed = page.seed?.toWeb(),
            nodes = page.nodes.map(::node),
            edges = page.edges.map(::edge),
            limits = WebOntologyGraphLimits(limits.nodeLimit, limits.edgeLimit),
            totalNodeCount = page.totalNodeCount,
            totalEdgeCount = page.totalEdgeCount,
            continuation = continuation,
            ambiguousCrossSourceRelationshipCount = page.ambiguousCrossSourceRelationshipCount,
            inferredOverlays = page.inferredOverlays.map { overlay ->
                com.entio.core.InferredFactsOverlay(
                    graphState = overlay.graphState,
                    state = overlay.state,
                    totalFactCount = overlay.totalFactCount,
                    truncated = overlay.truncated,
                    graphFingerprint = overlay.graphFingerprint,
                    proposalFingerprint = overlay.proposalFingerprint,
                    message = overlay.message,
                ).toWeb()
            },
        )
    }

    private fun prune() { val now = clock.instant(); continuations.entries.removeIf { !it.value.expiresAt.isAfter(now) } }
    private fun node(value: OntologyGraphNode): WebOntologyGraphNode = WebOntologyGraphNode(value.id.toWeb(), value.kind.name, value.label, value.definitionExcerpt, WebOntologyGraphNodeSummary(value.summary.directSuperclassLabels, value.summary.domainLabels, value.summary.rangeLabels, value.summary.assertedTypeLabels, value.summary.datatypeRangeLabels, value.summary.loadedRelationshipCount, value.summary.availableRelationshipCount))
    private fun edge(value: OntologyGraphEdge): WebOntologyGraphEdge = WebOntologyGraphEdge(stableId(value.id), value.kind.name, stableId(value.source.stableKey), stableId(value.target.stableKey), value.label, value.predicateIri?.value, value.provenance.name)
    private fun OntologyGraphNodeId.toWeb(): WebOntologyGraphNodeId = WebOntologyGraphNodeId(stableId(stableKey), sourceId, entityIri.value)
    private fun stableId(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private data class QuerySignature(val userId: String, val projectId: String, val sourceIds: Set<String>, val fingerprint: String, val inferredFingerprint: String, val queryType: String, val entityKey: String, val categories: Set<String>)
    private data class ContinuationState(val signature: QuerySignature, val cursor: OntologyGraphPageCursor, val expiresAt: Instant)
}
