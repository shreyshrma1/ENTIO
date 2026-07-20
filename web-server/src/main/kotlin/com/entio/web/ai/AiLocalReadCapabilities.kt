package com.entio.web.ai

import com.entio.core.Iri
import com.entio.core.SemanticSearchQuery
import com.entio.web.ReadOnlyProjectAdapter
import com.entio.web.StagingWorkflowService
import com.entio.web.WebEntityDetailResponse
import com.entio.web.WebRelationship
import com.entio.web.contract.WebAction
import com.entio.web.contract.WebPageRequest
import com.entio.web.contract.WebPermission
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

public enum class AiFactProvenance {
    ASSERTED,
    INFERRED,
    EXTERNAL,
    SHACL,
    STAGED,
    PROPOSAL,
    APPLICATION,
}

public data class AiEvidenceReference(
    val id: String,
    val label: String,
    val kind: String,
    val provenance: AiFactProvenance,
    val sourceId: String? = null,
)

public sealed interface AiCapabilityPayload {
    public val evidence: List<AiEvidenceReference>
    public val truncated: Boolean
}

public data class AiProjectSummaryPayload(
    val projectId: String,
    val displayName: String,
    val configuredName: String,
    val symbolCount: Int,
    val graphTripleCount: Int,
    val sources: List<AiProjectSource>,
    override val evidence: List<AiEvidenceReference>,
    override val truncated: Boolean = false,
) : AiCapabilityPayload

public data class AiProjectSource(
    val id: String,
    val format: String,
    val roles: List<String>,
    val tripleCount: Int,
)

public data class AiEntitySnapshot(
    val iri: String,
    val label: String,
    val kind: String,
    val sourceId: String,
    val locality: String,
    val definitions: List<String>,
    val directSuperclasses: List<String>,
    val assertedTypes: List<String>,
    val outgoingRelationshipCount: Int,
    val incomingRelationshipCount: Int,
)

public data class AiEntityPayload(
    val entity: AiEntitySnapshot,
    override val evidence: List<AiEvidenceReference>,
    override val truncated: Boolean,
) : AiCapabilityPayload

public data class AiEntityComparisonPayload(
    val entities: List<AiEntitySnapshot>,
    override val evidence: List<AiEvidenceReference>,
    override val truncated: Boolean,
) : AiCapabilityPayload

public data class AiSearchHit(
    val iri: String,
    val label: String,
    val kind: String,
    val sourceId: String,
    val matchReason: String,
    val locality: String,
)

public data class AiSearchPayload(
    val query: String,
    val hits: List<AiSearchHit>,
    override val evidence: List<AiEvidenceReference>,
    override val truncated: Boolean,
) : AiCapabilityPayload

public data class AiHierarchyNode(
    val iri: String,
    val label: String,
    val sourceId: String,
    val childCount: Int,
)

public data class AiHierarchyPayload(
    val parentIri: String?,
    val nodes: List<AiHierarchyNode>,
    override val evidence: List<AiEvidenceReference>,
    override val truncated: Boolean,
) : AiCapabilityPayload

public data class AiUsageStatement(
    val direction: String,
    val predicateLabel: String,
    val valueLabel: String,
    val sourceId: String,
)

public data class AiEntityUsagePayload(
    val entity: AiEntitySnapshot,
    val incoming: List<AiUsageStatement>,
    val outgoing: List<AiUsageStatement>,
    override val evidence: List<AiEvidenceReference>,
    override val truncated: Boolean,
) : AiCapabilityPayload

public enum class AiScreenId {
    EXPLORE,
    CHANGES,
    REASONING,
    FIBO,
    ACTIVITY,
    SETTINGS,
}

/** Browser context is supplied by the server boundary and never decoded from model arguments. */
public data class AiCurrentScreenContext(
    val screen: AiScreenId,
    val selectedEntityIri: String? = null,
    val selectedSourceId: String? = null,
    val selectedProposalId: String? = null,
    val availableActions: Set<String> = emptySet(),
    val featureIds: Set<String> = emptySet(),
) {
    init {
        require(availableActions.all { action -> WebAction.entries.any { it.name == action } }) { "unknown-screen-action" }
    }
}

public data class AiScreenContextPayload(
    val context: AiCurrentScreenContext,
    override val evidence: List<AiEvidenceReference>,
    override val truncated: Boolean = false,
) : AiCapabilityPayload

public data class AiAvailableActionsPayload(
    val screen: AiScreenId,
    val actions: List<String>,
    val permissions: List<String>,
    override val evidence: List<AiEvidenceReference>,
    override val truncated: Boolean = false,
) : AiCapabilityPayload

public data class AiWorkflowStatePayload(
    val status: String,
    val stagedChangeCount: Int,
    val stagedSummaries: List<String>,
    val proposalId: String?,
    val proposalStatus: String?,
    override val evidence: List<AiEvidenceReference>,
    override val truncated: Boolean,
) : AiCapabilityPayload

public data class AiHelpPayload(
    val id: String,
    val title: String,
    val content: String,
    val relatedActions: List<String>,
    val relatedPermissions: List<String>,
    override val evidence: List<AiEvidenceReference>,
    override val truncated: Boolean = false,
) : AiCapabilityPayload

public data class AiErrorHelpPayload(
    val code: String,
    val explanation: String,
    override val evidence: List<AiEvidenceReference>,
    override val truncated: Boolean = false,
) : AiCapabilityPayload

public data class AiCapabilityExecution(
    val result: AiCapabilityResult,
    val payload: AiCapabilityPayload,
)

public data class AiContextPackage(
    val project: AiProjectSummaryPayload,
    val ontologyOverview: AiOntologyOverview,
    val selectedEntity: AiEntityPayload?,
    val screen: AiCurrentScreenContext,
    val workflow: AiWorkflowStatePayload,
)

public data class AiOntologyOverview(
    val entities: List<AiOntologyContextEntity>,
    val truncated: Boolean,
    /** Compact label/kind inventory for the whole allowed ontology scope. */
    val inventory: List<AiOntologyInventoryEntry> = emptyList(),
    /** True when the compact inventory itself exceeds its safe context budget. */
    val inventoryTruncated: Boolean = false,
)

public data class AiOntologyInventoryEntry(
    val iri: String,
    val label: String,
    val kind: String,
    val sourceId: String,
)

public data class AiOntologyContextEntity(
    val entity: AiEntitySnapshot,
    val incoming: List<AiUsageStatement>,
    val outgoing: List<AiUsageStatement>,
)

public class AiContextPackageBuilder(
    private val capabilities: AiLocalReadCapabilityService,
) {
    public fun build(scope: AiCapabilityScope, context: AiCurrentScreenContext, objective: String? = null): AiContextPackage {
        validateContextScope(scope, context)
        val project = capabilities.projectSummary(scope)
        val entity = context.selectedEntityIri?.let {
            capabilities.entity(scope, AiEntityDetailArguments(it, context.selectedSourceId))
        }
        return AiContextPackage(project, capabilities.ontologyOverview(scope, objective), entity, context, capabilities.workflow(scope))
    }

    private fun validateContextScope(scope: AiCapabilityScope, context: AiCurrentScreenContext) {
        if (context.selectedSourceId != null && context.selectedSourceId !in scope.allowedSourceIds) {
            throw AiCapabilityFailure("source-scope-violation", "The selected source is outside the current AI scope.")
        }
    }
}

/** Executes only bounded read capabilities over existing structured Entio adapters. */
public class AiLocalReadCapabilityService(
    private val readOnly: ReadOnlyProjectAdapter,
    private val staging: StagingWorkflowService,
    private val help: EntioHelpService = EntioHelpService(),
) {
    public fun ontologyOverview(scope: AiCapabilityScope, objective: String? = null): AiOntologyOverview {
        val items = scope.allowedSourceIds.flatMap { sourceId ->
            allOutlineItems(scope.projectId, sourceId)
        }.distinctBy { listOf(it.sourceId, it.iri) }
            .sortedWith(compareBy<com.entio.web.WebOutlineItem> { it.kind }.thenBy { it.label.lowercase() }.thenBy { it.iri })
        val relevantIris = objective?.trim()?.takeIf(String::isNotBlank)?.let { query ->
            search(scope, AiLocalSearchArguments(query.take(256), emptyList(), null, MAX_CONTEXT_ENTITIES)).hits.map { it.iri }.toSet()
        }.orEmpty()
        val orderedItems = items.sortedWith(
            compareByDescending<com.entio.web.WebOutlineItem> { it.iri in relevantIris }
                .thenBy { it.kind }
                .thenBy { it.label.lowercase() }
                .thenBy { it.iri },
        )
        val visible = orderedItems.take(MAX_CONTEXT_ENTITIES).map { item ->
            val detail = readEntity(scope, item.iri, item.sourceId)
            AiOntologyContextEntity(
                entity = detail.snapshot(),
                incoming = detail.incomingRelationships.take(MAX_CONTEXT_RELATIONSHIPS).map(WebRelationship::usage),
                outgoing = detail.outgoingRelationships.take(MAX_CONTEXT_RELATIONSHIPS).map(WebRelationship::usage),
            )
        }
        val inventory = items.take(MAX_CONTEXT_INVENTORY_ENTITIES).map { item ->
            AiOntologyInventoryEntry(item.iri, item.label, item.kind, item.sourceId)
        }
        return AiOntologyOverview(
            entities = visible,
            truncated = orderedItems.size > visible.size,
            inventory = inventory,
            inventoryTruncated = items.size > MAX_CONTEXT_INVENTORY_ENTITIES,
        )
    }

    private fun allOutlineItems(projectId: String, sourceId: String): List<com.entio.web.WebOutlineItem> {
        val items = mutableListOf<com.entio.web.WebOutlineItem>()
        var offset = 0
        while (true) {
            val page = readOnly.outline(projectId, sourceId, WebPageRequest(offset = offset, limit = WebPageRequest.MAX_PAGE_LIMIT)).page
            items += page.items
            // Fetch only enough rows to distinguish a complete compact inventory from
            // one that must be completed with the approved search/paging capabilities.
            if (items.size > MAX_CONTEXT_INVENTORY_ENTITIES) break
            val next = page.nextOffset ?: break
            if (next <= offset) break
            offset = next
        }
        return items
    }

    public fun execute(
        invocation: AiDecodedCapabilityInvocation,
        scope: AiCapabilityScope,
        context: AiCurrentScreenContext,
    ): AiCapabilityExecution {
        val payload = when (val arguments = invocation.arguments) {
            AiProjectSummaryArguments -> projectSummary(scope)
            is AiEntityDetailArguments -> entity(scope, arguments)
            is AiEntityComparisonArguments -> compare(scope, arguments)
            is AiLocalSearchArguments -> search(scope, arguments)
            is AiHierarchyArguments -> hierarchy(scope, arguments)
            is AiEntityUsageArguments -> usage(scope, arguments)
            AiScreenContextArguments -> screen(scope, context)
            AiAvailableActionsArguments -> actions(scope, context)
            AiWorkflowStateArguments -> workflow(scope)
            is AiHelpArguments -> help(scope, context, arguments)
            is AiErrorCodeArguments -> errorHelp(arguments)
            is AiSemanticJobArguments,
            is AiProposalReadArguments,
            is AiActivityReadArguments,
            is AiFiboSearchArguments,
            is AiFiboEntityArguments,
            is AiAddDraftItemArguments,
            is AiUpdateDraftItemArguments,
            is AiRemoveDraftItemArguments,
            is AiReorderDraftItemsArguments,
            is AiUndoDraftArguments,
            is AiClearDraftArguments,
            is AiDraftAnalysisArguments,
            -> throw AiCapabilityFailure("capability-service-mismatch", "This capability requires a different bounded capability service.")
        }
        return AiCapabilityExecution(
            result = AiCapabilityResult(
                invocationId = invocation.invocationId,
                capabilityName = invocation.definition.name,
                status = if (payload.truncated) AiCapabilityResultStatus.LIMIT_REACHED else AiCapabilityResultStatus.COMPLETED,
                summary = payload.summary(),
                resultReferenceIds = payload.evidence.map(AiEvidenceReference::id),
            ),
            payload = payload,
        )
    }

    public fun projectSummary(scope: AiCapabilityScope): AiProjectSummaryPayload {
        val summary = readOnly.summary(scope.projectId)
        val sources = summary.sources.filter { it.id in scope.allowedSourceIds }.map {
            AiProjectSource(it.id, it.format, it.roles.sorted(), it.tripleCount)
        }
        return AiProjectSummaryPayload(
            projectId = summary.project.id,
            displayName = summary.project.displayName,
            configuredName = summary.project.name,
            symbolCount = sources.sumOf { source ->
                readOnly.outline(scope.projectId, source.id, WebPageRequest(limit = 1)).page.total
            },
            graphTripleCount = sources.sumOf(AiProjectSource::tripleCount),
            sources = sources,
            evidence = listOf(reference(scope, "project", summary.project.id, summary.project.displayName, AiFactProvenance.ASSERTED)),
        )
    }

    public fun entity(scope: AiCapabilityScope, arguments: AiEntityDetailArguments): AiEntityPayload {
        ensureSource(scope, arguments.sourceId)
        val detail = readEntity(scope, arguments.entityIri, arguments.sourceId)
        val snapshot = detail.snapshot()
        return AiEntityPayload(
            entity = snapshot,
            evidence = listOf(reference(scope, "entity", detail.iri, detail.label, detail.provenance(), detail.sourceId)),
            truncated = detail.definitions.size > MAX_DEFINITIONS ||
                detail.directSuperclasses.size > MAX_RELATED || detail.assertedTypes.size > MAX_RELATED,
        )
    }

    private fun compare(scope: AiCapabilityScope, arguments: AiEntityComparisonArguments): AiEntityComparisonPayload {
        ensureSource(scope, arguments.sourceId)
        val entities = arguments.entityIris.map { readEntity(scope, it, arguments.sourceId) }
        return AiEntityComparisonPayload(
            entities = entities.map(WebEntityDetailResponse::snapshot),
            evidence = entities.map { reference(scope, "entity", it.iri, it.label, it.provenance(), it.sourceId) },
            truncated = entities.any { it.definitions.size > MAX_DEFINITIONS || it.directSuperclasses.size > MAX_RELATED },
        )
    }

    private fun search(scope: AiCapabilityScope, arguments: AiLocalSearchArguments): AiSearchPayload {
        ensureSource(scope, arguments.sourceId)
        val responses = selectedSources(scope, arguments.sourceId).map { sourceId ->
            readOnly.search(
                scope.projectId,
                SemanticSearchQuery(text = arguments.query, sourceId = sourceId),
                WebPageRequest(limit = 100),
            )
        }
        val allowedKinds = arguments.kinds.map(AiEntityKindFilter::webKind).toSet()
        val filtered = responses.flatMap { it.page.items }
            .filter { allowedKinds.isEmpty() || it.kind in allowedKinds }
            .distinctBy { listOf(it.sourceId, it.iri) }
            .sortedWith(compareBy<com.entio.web.WebSemanticSearchHit> { it.rank }.thenBy { it.sourceId }.thenBy { it.iri })
        val hits = filtered.take(arguments.limit).map {
            AiSearchHit(it.iri, it.label, it.kind, it.sourceId, it.reason, it.locality)
        }
        return AiSearchPayload(
            query = arguments.query,
            hits = hits,
            evidence = hits.map { reference(scope, "search-hit", it.iri, it.label, it.locality.provenance(), it.sourceId) },
            truncated = filtered.size > hits.size || responses.any { it.page.nextOffset != null },
        )
    }

    private fun hierarchy(scope: AiCapabilityScope, arguments: AiHierarchyArguments): AiHierarchyPayload {
        ensureSource(scope, arguments.sourceId)
        val responses = selectedSources(scope, arguments.sourceId).mapNotNull { sourceId ->
            runCatching {
                readOnly.hierarchy(
                    scope.projectId,
                    sourceId,
                    arguments.parentIri?.let(::Iri),
                    WebPageRequest(limit = arguments.limit),
                )
            }.getOrNull()
        }
        if (arguments.parentIri != null && responses.isEmpty()) {
            throw AiCapabilityFailure("missing-hierarchy-parent", "The requested hierarchy parent was not found in the allowed source scope.")
        }
        val allNodes = responses.flatMap { it.page.items }
            .distinctBy { listOf(it.sourceId, it.iri) }
            .sortedWith(compareBy<com.entio.web.WebHierarchyItem> { it.label.lowercase() }.thenBy { it.iri })
        val nodes = allNodes.take(arguments.limit).map { AiHierarchyNode(it.iri, it.label, it.sourceId, it.childCount) }
        return AiHierarchyPayload(
            parentIri = arguments.parentIri,
            nodes = nodes,
            evidence = nodes.map { reference(scope, "hierarchy-node", it.iri, it.label, AiFactProvenance.ASSERTED, it.sourceId) },
            truncated = allNodes.size > nodes.size || responses.any { it.page.nextOffset != null },
        )
    }

    private fun usage(scope: AiCapabilityScope, arguments: AiEntityUsageArguments): AiEntityUsagePayload {
        val detail = readEntity(scope, arguments.entityIri, arguments.sourceId)
        val incoming = detail.incomingRelationships.take(arguments.limit).map(WebRelationship::usage)
        val outgoing = detail.outgoingRelationships.take(arguments.limit).map(WebRelationship::usage)
        return AiEntityUsagePayload(
            entity = detail.snapshot(),
            incoming = incoming,
            outgoing = outgoing,
            evidence = listOf(reference(scope, "entity-usage", detail.iri, detail.label, detail.provenance(), detail.sourceId)),
            truncated = detail.incomingRelationships.size > incoming.size || detail.outgoingRelationships.size > outgoing.size,
        )
    }

    private fun screen(scope: AiCapabilityScope, context: AiCurrentScreenContext): AiScreenContextPayload {
        ensureSource(scope, context.selectedSourceId)
        return AiScreenContextPayload(
            context,
            listOf(reference(scope, "screen", context.screen.name, context.screen.name.lowercase(), AiFactProvenance.APPLICATION)),
        )
    }

    private fun actions(scope: AiCapabilityScope, context: AiCurrentScreenContext): AiAvailableActionsPayload {
        val permissions = scope.permissions.filter { permission -> WebPermission.entries.any { it.name == permission } }.sorted()
        val actions = context.availableActions.filter { it in permissions }.sorted()
        return AiAvailableActionsPayload(
            screen = context.screen,
            actions = actions,
            permissions = permissions,
            evidence = listOf(reference(scope, "actions", context.screen.name, "Available actions", AiFactProvenance.APPLICATION)),
        )
    }

    public fun workflow(scope: AiCapabilityScope): AiWorkflowStatePayload {
        val snapshot = staging.snapshot(scope.projectId)
        val scopedEntries = snapshot.entries.filter { it.sourceId in scope.allowedSourceIds }
        val entries = scopedEntries.take(MAX_WORKFLOW_ITEMS)
        val visibleIds = scopedEntries.map { it.id }.toSet()
        val proposal = snapshot.proposal?.takeIf { it.stagedChangeIds.isNotEmpty() && it.stagedChangeIds.all(visibleIds::contains) }
        return AiWorkflowStatePayload(
            status = when {
                proposal != null -> proposal.status
                scopedEntries.isEmpty() -> "EMPTY"
                else -> "READY"
            },
            stagedChangeCount = scopedEntries.size,
            stagedSummaries = entries.map { it.summary },
            proposalId = proposal?.id,
            proposalStatus = proposal?.status,
            evidence = buildList {
                entries.forEach { add(reference(scope, "staged-change", it.id, it.summary, AiFactProvenance.STAGED, it.sourceId)) }
                proposal?.let { add(reference(scope, "proposal", it.id, it.status, AiFactProvenance.STAGED)) }
            },
            truncated = scopedEntries.size > entries.size,
        )
    }

    private fun help(
        scope: AiCapabilityScope,
        context: AiCurrentScreenContext,
        arguments: AiHelpArguments,
    ): AiHelpPayload {
        val entry = help.topic(arguments.topic)
        val available = actions(scope, context).actions.toSet()
        return AiHelpPayload(
            id = entry.id,
            title = entry.title,
            content = entry.content,
            relatedActions = entry.relatedActions.filter(available::contains),
            relatedPermissions = entry.relatedPermissions.filter(scope.permissions::contains),
            evidence = listOf(reference(scope, "help", entry.id, entry.title, AiFactProvenance.APPLICATION)),
        )
    }

    private fun errorHelp(arguments: AiErrorCodeArguments): AiErrorHelpPayload {
        val entry = help.error(arguments.code)
        val evidence = AiEvidenceReference(
            id = stableReferenceId("help-error", arguments.code),
            label = arguments.code,
            kind = "error-help",
            provenance = AiFactProvenance.APPLICATION,
        )
        return AiErrorHelpPayload(arguments.code, entry, listOf(evidence))
    }

    private fun ensureSource(scope: AiCapabilityScope, sourceId: String?) {
        if (sourceId != null && sourceId !in scope.allowedSourceIds) {
            throw AiCapabilityFailure("source-scope-violation", "The requested source is outside the current AI scope.")
        }
    }

    private fun selectedSources(scope: AiCapabilityScope, sourceId: String?): List<String> {
        ensureSource(scope, sourceId)
        return sourceId?.let(::listOf) ?: scope.allowedSourceIds.sorted()
    }

    private fun readEntity(scope: AiCapabilityScope, entityIri: String, sourceId: String?): WebEntityDetailResponse {
        val detail = selectedSources(scope, sourceId).firstNotNullOfOrNull { allowedSourceId ->
            runCatching { readOnly.entity(scope.projectId, Iri(entityIri), allowedSourceId) }.getOrNull()
        }
        return detail ?: throw AiCapabilityFailure("missing-entity", "The requested entity was not found in the allowed source scope.")
    }

    private fun reference(
        scope: AiCapabilityScope,
        kind: String,
        key: String,
        label: String,
        provenance: AiFactProvenance,
        sourceId: String? = null,
    ): AiEvidenceReference = AiEvidenceReference(
        id = stableReferenceId(scope.projectId, kind, key, sourceId.orEmpty(), scope.baselineFingerprint),
        label = label,
        kind = kind,
        provenance = provenance,
        sourceId = sourceId,
    )

    private companion object {
        const val MAX_CONTEXT_ENTITIES: Int = 20
        const val MAX_CONTEXT_INVENTORY_ENTITIES: Int = 2_000
        const val MAX_CONTEXT_RELATIONSHIPS: Int = 20
        const val MAX_DEFINITIONS: Int = 3
        const val MAX_RELATED: Int = 10
        const val MAX_WORKFLOW_ITEMS: Int = 20
    }
}

public data class EntioHelpEntry(
    val id: String,
    val title: String,
    val content: String,
    val relatedActions: List<String> = emptyList(),
    val relatedPermissions: List<String> = emptyList(),
)

private data class EntioHelpDocument(
    val version: String,
    val topics: List<EntioHelpEntry>,
    val errorCodes: Map<String, String>,
)

/** Loads one fixed, versioned classpath resource. Model arguments can select only known IDs. */
public class EntioHelpService(
    objectMapper: ObjectMapper = ObjectMapper(),
) {
    private val document: EntioHelpDocument = EntioHelpService::class.java
        .getResourceAsStream(RESOURCE_PATH)
        ?.use(objectMapper::readTree)
        ?.let(::parseHelpDocument)
        ?: throw IllegalStateException("entio-help-resource-missing")
    private val topics: Map<String, EntioHelpEntry> = document.topics.associateBy(EntioHelpEntry::id)

    init {
        require(document.version == "v1") { "unsupported-entio-help-version" }
        require(topics.size == AiHelpTopic.entries.size) { "incomplete-entio-help-topics" }
        require(topics.keys == AiHelpTopic.entries.map(Enum<*>::name).toSet()) { "unknown-entio-help-topic" }
        require(document.topics.flatMap(EntioHelpEntry::relatedActions).all { action -> WebAction.entries.any { it.name == action } }) {
            "unknown-entio-help-action"
        }
        require(document.topics.flatMap(EntioHelpEntry::relatedPermissions).all { permission -> WebPermission.entries.any { it.name == permission } }) {
            "unknown-entio-help-permission"
        }
    }

    public fun topic(topic: AiHelpTopic): EntioHelpEntry = topics.getValue(topic.name)

    public fun error(code: String): String = document.errorCodes[code]
        ?: throw AiCapabilityFailure("unknown-error-code", "The requested Entio error code is not documented.")

    public fun knownErrorCodes(): Set<String> = document.errorCodes.keys.toSortedSet()

    private companion object {
        const val RESOURCE_PATH: String = "/entio-help/v1/help.json"
    }
}

private fun parseHelpDocument(root: com.fasterxml.jackson.databind.JsonNode): EntioHelpDocument {
    val version = root.requiredText("version")
    val topicsNode = root.path("topics")
    require(topicsNode.isArray) { "invalid-entio-help-topics" }
    val topics = topicsNode.map { node ->
        EntioHelpEntry(
            id = node.requiredText("id"),
            title = node.requiredText("title"),
            content = node.requiredText("content"),
            relatedActions = node.requiredTextArray("relatedActions"),
            relatedPermissions = node.requiredTextArray("relatedPermissions"),
        )
    }
    val errorsNode = root.path("errorCodes")
    require(errorsNode.isObject) { "invalid-entio-help-errors" }
    val errors = errorsNode.fields().asSequence().associate { (code, value) ->
        require(value.isTextual && value.textValue().isNotBlank()) { "invalid-entio-help-error:$code" }
        code to value.textValue()
    }.toSortedMap()
    return EntioHelpDocument(version, topics, errors)
}

private fun com.fasterxml.jackson.databind.JsonNode.requiredText(name: String): String {
    val node = path(name)
    require(node.isTextual && node.textValue().isNotBlank()) { "invalid-entio-help-field:$name" }
    return node.textValue()
}

private fun com.fasterxml.jackson.databind.JsonNode.requiredTextArray(name: String): List<String> {
    val node = path(name)
    require(node.isArray && node.all(com.fasterxml.jackson.databind.JsonNode::isTextual)) { "invalid-entio-help-field:$name" }
    return node.map(com.fasterxml.jackson.databind.JsonNode::textValue)
}

private fun WebEntityDetailResponse.snapshot(): AiEntitySnapshot = AiEntitySnapshot(
    iri = iri,
    label = label,
    kind = kind,
    sourceId = sourceId,
    locality = locality,
    definitions = definitions.take(3).map { it.value },
    directSuperclasses = directSuperclasses.take(10).map { it.label },
    assertedTypes = assertedTypes.take(10).map { it.label },
    outgoingRelationshipCount = outgoingRelationships.size,
    incomingRelationshipCount = incomingRelationships.size,
)

private fun WebEntityDetailResponse.provenance(): AiFactProvenance = locality.provenance()

private fun String.provenance(): AiFactProvenance = if (equals("Imported", ignoreCase = true)) {
    AiFactProvenance.EXTERNAL
} else {
    AiFactProvenance.ASSERTED
}

private fun WebRelationship.usage(): AiUsageStatement = AiUsageStatement(
    direction = direction,
    predicateLabel = predicate.label,
    valueLabel = value.label ?: value.value,
    sourceId = sourceId,
)

private fun AiEntityKindFilter.webKind(): String = when (this) {
    AiEntityKindFilter.CLASS -> "Class"
    AiEntityKindFilter.OBJECT_PROPERTY -> "ObjectProperty"
    AiEntityKindFilter.DATATYPE_PROPERTY -> "DatatypeProperty"
    AiEntityKindFilter.ANNOTATION_PROPERTY -> "AnnotationProperty"
    AiEntityKindFilter.INDIVIDUAL -> "Individual"
    AiEntityKindFilter.SHAPE -> "Shape"
}

internal fun AiCapabilityPayload.summary(): String = when (this) {
    is AiProjectSummaryPayload -> "Project summary returned with ${sources.size} allowed source(s)."
    is AiEntityPayload -> "Entity descriptor returned for ${entity.label}."
    is AiEntityComparisonPayload -> "Compared ${entities.size} entities."
    is AiSearchPayload -> "Search returned ${hits.size} local result(s)."
    is AiHierarchyPayload -> "Hierarchy returned ${nodes.size} node(s)."
    is AiEntityUsagePayload -> "Usage returned ${incoming.size + outgoing.size} relationship(s)."
    is AiScreenContextPayload -> "Current screen context returned."
    is AiAvailableActionsPayload -> "Returned ${actions.size} currently available action(s)."
    is AiWorkflowStatePayload -> "Workflow state returned with $stagedChangeCount staged change(s)."
    is AiHelpPayload -> "Entio help returned for $id."
    is AiErrorHelpPayload -> "Entio error help returned for $code."
    is AiSemanticJobPayload -> "Semantic job ${jobId} returned with ${facts.size + shaclFindings.size} bounded result(s)."
    is AiProposalReadPayload -> "Proposal ${proposalId} returned with ${diff.size} diff entr${if (diff.size == 1) "y" else "ies"}."
    is AiActivityPayload -> "Returned ${events.size} shared workflow event(s)."
    is AiFiboSearchPayload -> "FIBO search returned ${hits.size} result(s)."
    is AiFiboEntityPayload -> "FIBO descriptor returned for ${entity.label}."
}

internal fun stableReferenceId(vararg components: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(components.joinToString("\u0000").toByteArray(StandardCharsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
