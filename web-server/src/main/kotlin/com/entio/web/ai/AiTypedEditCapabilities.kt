package com.entio.web.ai

import com.entio.web.StagingWorkflowService
import com.entio.web.WebWorkflowFailure
import com.entio.web.contract.WebPermission
import com.entio.web.contract.WebStageChangeRequest
import com.fasterxml.jackson.databind.JsonNode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID

public enum class AiTypedEditLifecycleStatus {
    APPROVED,
    DEFERRED,
}

public data class AiTypedEditInventoryEntry(
    val family: String,
    val editTypes: Set<String>,
    val status: AiTypedEditLifecycleStatus,
    val existingPath: String,
    val reason: String,
)

/** Runtime counterpart to the reviewed capability inventory in docs/decisions. */
public object AiTypedEditCapabilityInventory {
    public val entries: List<AiTypedEditInventoryEntry> = listOf(
        AiTypedEditInventoryEntry(
            family = "ontology",
            editTypes = setOf(
                "create-class",
                "set-entity-label",
                "add-superclass",
                "remove-superclass",
                "create-object-property",
                "create-datatype-property",
                "set-property-domain",
                "set-property-range",
                "create-individual",
                "assign-type",
                "add-object-property-assertion",
                "add-datatype-property-assertion",
                "add-definition",
                "replace-definition",
                "remove-definition",
                "delete",
            ),
            status = AiTypedEditLifecycleStatus.APPROVED,
            existingPath = "WebStageChangeRequest -> StagingWorkflowService -> existing typed translators -> proposal workflow",
            reason = "The ordinary web workflow has a reusable typed preparation path without raw RDF.",
        ),
        AiTypedEditInventoryEntry(
            family = "shacl",
            editTypes = setOf(
                "shacl-create-node-shape",
                "shacl-create-property-shape",
                "shacl-update-constraint",
                "shacl-remove-constraint",
                "shacl-delete-shape",
            ),
            status = AiTypedEditLifecycleStatus.APPROVED,
            existingPath = "WebStageChangeRequest -> WebShaclStagePreparer -> TypedShaclEditTranslator -> proposal workflow",
            reason = "Slice 6 proved preview, validation impact, review, atomic apply, reload, and rollback.",
        ),
        AiTypedEditInventoryEntry(
            family = "semantic-metadata",
            editTypes = setOf(
                "add-alternate-label",
                "replace-alternate-label",
                "remove-alternate-label",
            ),
            status = AiTypedEditLifecycleStatus.APPROVED,
            existingPath = "SemanticEditRequest -> TypedOntologyEditTranslator",
            reason = "The ordinary web workflow and private AI draft both reuse the existing typed semantic metadata translator without raw RDF.",
        ),
        AiTypedEditInventoryEntry(
            family = "annotation-metadata",
            editTypes = setOf(
                "add-annotation",
                "remove-annotation",
            ),
            status = AiTypedEditLifecycleStatus.DEFERRED,
            existingPath = "SemanticEditRequest -> TypedOntologyEditTranslator",
            reason = "The current workbench exposes annotations as read-only facts; no user-facing mutation contract is available.",
        ),
        AiTypedEditInventoryEntry(
            family = "external-reuse",
            editTypes = setOf(
                "reuse-class",
                "reuse-object-property",
                "reuse-datatype-property",
                "create-local-subclass",
            ),
            status = AiTypedEditLifecycleStatus.DEFERRED,
            existingPath = "WebFiboProposalRequest -> FiboWebService -> shared staging",
            reason = "The current public adapter mutates shared staging and therefore cannot be used by a private AI draft.",
        ),
        AiTypedEditInventoryEntry(
            family = "advanced-shacl",
            editTypes = setOf("raw-shacl", "sparql-constraint", "complex-property-path", "qualified-value-shape"),
            status = AiTypedEditLifecycleStatus.DEFERRED,
            existingPath = "none",
            reason = "These operations are outside the bounded typed SHACL contract and have no raw RDF fallback.",
        ),
    )

    public val approvedOntologyEditTypes: Set<String> = entries
        .single { it.family == "ontology" }
        .editTypes

    public val approvedShaclEditTypes: Set<String> = entries
        .single { it.family == "shacl" }
        .editTypes

    public val approvedSemanticMetadataEditTypes: Set<String> = entries
        .single { it.family == "semantic-metadata" }
        .editTypes

    public val approvedEditTypes: Set<String> = entries
        .filter { it.status == AiTypedEditLifecycleStatus.APPROVED }
        .flatMapTo(linkedSetOf()) { it.editTypes }

    public fun requireApproved(editType: String): Unit {
        if (editType !in approvedEditTypes) {
            throw AiDraftFailure(
                "unsupported-typed-edit",
                "Typed edit '$editType' is not approved for private AI drafts; no raw RDF fallback is available.",
            )
        }
    }
}

public data class AiAddDraftItemArguments(
    override val sourceId: String,
    val request: WebStageChangeRequest,
    val rationale: String,
    val dependencyItemIds: List<String> = emptyList(),
) : AiDraftSourceResolvableArguments {
    init {
        if (sourceId != request.sourceId) throw AiDraftFailure("source-argument-mismatch", "The draft request source must match its capability scope source.")
    }
}

public data class AiUpdateDraftItemArguments(
    val itemId: String,
    override val sourceId: String,
    val request: WebStageChangeRequest,
    val rationale: String,
    val dependencyItemIds: List<String> = emptyList(),
) : AiDraftSourceResolvableArguments {
    init {
        if (sourceId != request.sourceId) throw AiDraftFailure("source-argument-mismatch", "The draft request source must match its capability scope source.")
    }
}

public data class AiRemoveDraftItemArguments(
    val itemId: String,
    val explanation: String,
) : AiCapabilityArguments

public data class AiReorderDraftItemsArguments(
    val itemIds: List<String>,
    val explanation: String,
) : AiCapabilityArguments

public data class AiUndoDraftArguments(
    val explanation: String,
) : AiCapabilityArguments

public data class AiClearDraftArguments(
    val explanation: String,
) : AiCapabilityArguments

public class AiDraftFailure(
    public val code: String,
    message: String,
) : IllegalArgumentException(message)

public class AiTypedEditCapabilityAdapter(
    private val staging: StagingWorkflowService,
) {
    public fun prepare(
        scope: AiCapabilityScope,
        capabilityName: String,
        request: WebStageChangeRequest,
    ): AiTypedDraftOperation {
        AiTypedEditCapabilityInventory.requireApproved(request.editType)
        val expectedFamily = when (capabilityName) {
            ADD_ONTOLOGY_CAPABILITY, UPDATE_ONTOLOGY_CAPABILITY ->
                AiTypedEditCapabilityInventory.approvedOntologyEditTypes + AiTypedEditCapabilityInventory.approvedSemanticMetadataEditTypes
            ADD_DEFINITION_CAPABILITY -> setOf("add-definition")
            ADD_SHACL_CAPABILITY, UPDATE_SHACL_CAPABILITY -> AiTypedEditCapabilityInventory.approvedShaclEditTypes
            else -> throw AiDraftFailure("unsupported-draft-capability", "Capability '$capabilityName' cannot prepare a typed draft edit.")
        }
        if (request.editType !in expectedFamily) {
            throw AiDraftFailure("typed-edit-family-mismatch", "The typed edit does not match the selected draft capability.")
        }
        val normalizedRequest = normalizeSemanticTarget(request)
        val scopedRequest = resolveScopedSource(scope, normalizedRequest)
        val aiRequest = scopedRequest.copy(aiGenerated = true, idempotencyKey = null)
        val prepared = try {
            staging.preparePrivateDraft(scope.projectId, aiRequest)
        } catch (failure: WebWorkflowFailure) {
            throw AiDraftFailure(failure.code, failure.message ?: "The typed draft edit could not be prepared.")
        }
        val deletion = prepared.operation as? com.entio.core.StagedChangeOperation.Delete
        if (deletion != null && deletion.plan.status != com.entio.core.DeletionPlanStatus.Safe) {
            throw AiDraftFailure(
                "deletion-dependencies-required",
                "Deletion dependencies must be explicitly selected before the edit can enter a private AI draft.",
            )
        }
        return AiTypedDraftOperation(
            capabilityName = capabilityName,
            targetSourceId = prepared.request.sourceId,
            summary = prepared.summary,
            request = prepared.request.copy(aiGenerated = true, idempotencyKey = null),
            preparedOperation = prepared.operation,
            normalizedValues = prepared.normalizedValues.toSortedMap(),
            generatedIris = prepared.generatedIris.map { it.iri.value }.sorted(),
        )
    }

    private fun normalizeSemanticTarget(request: WebStageChangeRequest): WebStageChangeRequest = when (request.editType) {
        "add-definition", "replace-definition", "remove-definition" -> request.copy(
            targetIri = request.targetIri ?: request.resourceIri,
            targetLabel = request.targetLabel ?: request.resourceLabel ?: request.label,
        )
        else -> request
    }

    private fun resolveScopedSource(scope: AiCapabilityScope, request: WebStageChangeRequest): WebStageChangeRequest {
        if (request.sourceId in scope.allowedSourceIds) return request
        val candidates = scope.allowedSourceIds.mapNotNull { sourceId ->
            val candidate = request.copy(sourceId = sourceId, aiGenerated = true, idempotencyKey = null)
            try {
                staging.preparePrivateDraft(scope.projectId, candidate)
                candidate
            } catch (_: WebWorkflowFailure) {
                null
            }
        }
        if (candidates.size == 1) return candidates.single()
        throw AiDraftFailure("source-scope-violation", "The requested source is outside the current AI run scope.")
    }

    public companion object {
        public const val ADD_ONTOLOGY_CAPABILITY: String = "entio_draft_add_ontology_edit"
        public const val ADD_DEFINITION_CAPABILITY: String = "entio_draft_add_definition"
        public const val ADD_SHACL_CAPABILITY: String = "entio_draft_add_shacl_edit"
        public const val UPDATE_ONTOLOGY_CAPABILITY: String = "entio_draft_update_ontology_edit"
        public const val UPDATE_SHACL_CAPABILITY: String = "entio_draft_update_shacl_edit"
    }
}

public class AiPrivateDraftWorkspace(
    private val store: AiDraftStore,
    private val adapter: AiTypedEditCapabilityAdapter,
    private val clock: Clock = Clock.systemUTC(),
    private val itemIdFactory: () -> String = { "draft-item-${UUID.randomUUID()}" },
) {
    @Synchronized
    public fun create(scope: AiCapabilityScope, draftId: String): AiDraft {
        if (draftId.isBlank()) throw AiDraftFailure("draft-id-required", "A draft ID is required.")
        val now = clock.instant()
        return store.create(
            AiDraft(
                id = draftId,
                conversationId = scope.conversationId,
                userId = scope.userId,
                projectId = scope.projectId,
                baselineFingerprint = scope.baselineFingerprint,
                allowedSourceIds = scope.allowedSourceIds,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    public fun read(scope: AiCapabilityScope, draftId: String): AiDraft =
        store.get(scope.userId, scope.projectId, scope.conversationId, draftId)

    @Synchronized
    public fun add(
        scope: AiCapabilityScope,
        draftId: String,
        capabilityName: String,
        arguments: AiAddDraftItemArguments,
        runId: String? = null,
    ): AiDraft {
        val draft = mutableDraft(scope, draftId)
        validateRationale(arguments.rationale)
        val operation = adapter.prepare(scope, capabilityName, arguments.request)
        val existingDefinitionIndex = draft.items.indexOfFirst { item ->
            val existing = item.operation as? AiTypedDraftOperation
            existing?.definitionTargetKey() != null && existing.definitionTargetKey() == operation.definitionTargetKey()
        }
        if (existingDefinitionIndex >= 0) {
            val existing = draft.items[existingDefinitionIndex]
            validateDependencies(draft.items, arguments.dependencyItemIds, currentItemId = existing.id)
            val now = clock.instant()
            val replacement = existing.copy(
                operation = operation,
                rationale = arguments.rationale.trim(),
                dependencyItemIds = arguments.dependencyItemIds.distinct(),
                attribution = AiDraftAttribution(
                    acceptingUserId = scope.userId,
                    conversationId = scope.conversationId,
                    runId = runId,
                ),
                updatedAt = now,
            )
            return saveMutation(
                draft,
                draft.items.toMutableList().apply { set(existingDefinitionIndex, replacement) },
                "update",
                "Updated ${operation.summary}; one effective definition is retained for the target.",
                now,
            )
        }
        validateDependencies(draft.items, arguments.dependencyItemIds)
        rejectDuplicateOrConflict(draft.items, operation, ignoredItemId = null)
        val now = clock.instant()
        val item = AiDraftItem(
            id = itemIdFactory(),
            order = draft.items.size + 1,
            operation = operation,
            rationale = arguments.rationale.trim(),
            dependencyItemIds = arguments.dependencyItemIds.distinct(),
            attribution = AiDraftAttribution(
                acceptingUserId = scope.userId,
                conversationId = scope.conversationId,
                runId = runId,
            ),
            createdAt = now,
            updatedAt = now,
        )
        return saveMutation(draft, draft.items + item, "add", "Added ${operation.summary}.", now)
    }

    /** Prepares and validates a complete task batch before making one draft revision visible. */
    @Synchronized
    internal fun addBatch(
        scope: AiCapabilityScope,
        draftId: String,
        entries: List<AiDraftBatchEntry>,
        attribution: AiDraftAttribution,
    ): AiDraft {
        if (entries.isEmpty() || entries.size > 20) {
            throw AiDraftFailure("draft-batch-limit", "A draft batch requires between 1 and 20 items.")
        }
        val draft = mutableDraft(scope, draftId)
        val prepared = mutableListOf<Pair<AiAddDraftItemArguments, AiTypedDraftOperation>>()
        entries.forEach { entry ->
            validateRationale(entry.arguments.rationale)
            validateDependencies(draft.items, entry.arguments.dependencyItemIds)
            val operation = adapter.prepare(scope, entry.capabilityName, entry.arguments.request)
            rejectDuplicateOrConflict(draft.items, operation, ignoredItemId = null)
            rejectDuplicateOrConflict(
                prepared.mapIndexed { index, (_, current) ->
                    AiDraftItem("prepared-$index", index + 1, current, "prepared", createdAt = clock.instant(), updatedAt = clock.instant())
                },
                operation,
                ignoredItemId = null,
            )
            prepared += entry.arguments to operation
        }
        val now = clock.instant()
        val added = prepared.mapIndexed { index, (arguments, operation) ->
            AiDraftItem(
                id = itemIdFactory(),
                order = draft.items.size + index + 1,
                operation = operation,
                rationale = arguments.rationale.trim(),
                dependencyItemIds = arguments.dependencyItemIds.distinct(),
                attribution = attribution,
                createdAt = now,
                updatedAt = now,
            )
        }
        return saveMutation(draft, draft.items + added, "task-batch-add", "Added ${added.size} task draft items.", now)
    }

    @Synchronized
    public fun update(
        scope: AiCapabilityScope,
        draftId: String,
        capabilityName: String,
        arguments: AiUpdateDraftItemArguments,
        runId: String? = null,
    ): AiDraft {
        val draft = mutableDraft(scope, draftId)
        val current = draft.items.firstOrNull { it.id == arguments.itemId }
            ?: throw AiDraftFailure("unknown-draft-item", "Draft item '${arguments.itemId}' was not found.")
        validateRationale(arguments.rationale)
        validateDependencies(draft.items, arguments.dependencyItemIds, arguments.itemId)
        val operation = adapter.prepare(scope, capabilityName, arguments.request)
        rejectDuplicateOrConflict(draft.items, operation, ignoredItemId = current.id)
        val now = clock.instant()
        val replacement = current.copy(
            operation = operation,
            rationale = arguments.rationale.trim(),
            dependencyItemIds = arguments.dependencyItemIds.distinct(),
            attribution = AiDraftAttribution(
                acceptingUserId = scope.userId,
                conversationId = scope.conversationId,
                runId = runId,
            ),
            updatedAt = now,
        )
        return saveMutation(
            draft,
            draft.items.map { if (it.id == current.id) replacement else it },
            "update",
            "Updated ${operation.summary}.",
            now,
        )
    }

    @Synchronized
    public fun remove(scope: AiCapabilityScope, draftId: String, arguments: AiRemoveDraftItemArguments): AiDraft {
        val draft = mutableDraft(scope, draftId)
        if (draft.items.none { it.id == arguments.itemId }) {
            throw AiDraftFailure("unknown-draft-item", "Draft item '${arguments.itemId}' was not found.")
        }
        val dependents = draft.items.filter { arguments.itemId in it.dependencyItemIds }
        if (dependents.isNotEmpty()) {
            throw AiDraftFailure("draft-item-has-dependents", "Remove dependent draft items before removing '${arguments.itemId}'.")
        }
        val now = clock.instant()
        return saveMutation(
            draft,
            draft.items.filterNot { it.id == arguments.itemId }.renumber(now),
            "remove",
            requiredExplanation(arguments.explanation),
            now,
        )
    }

    @Synchronized
    public fun reorder(scope: AiCapabilityScope, draftId: String, arguments: AiReorderDraftItemsArguments): AiDraft {
        val draft = mutableDraft(scope, draftId)
        if (arguments.itemIds.distinct().size != arguments.itemIds.size || arguments.itemIds.toSet() != draft.items.map { it.id }.toSet()) {
            throw AiDraftFailure("invalid-draft-order", "A reorder must contain every draft item exactly once.")
        }
        val byId = draft.items.associateBy(AiDraftItem::id)
        val now = clock.instant()
        val ordered = arguments.itemIds.mapIndexed { index, id -> byId.getValue(id).copy(order = index + 1, updatedAt = now) }
        return saveMutation(draft, ordered, "reorder", requiredExplanation(arguments.explanation), now)
    }

    @Synchronized
    public fun undo(scope: AiCapabilityScope, draftId: String, arguments: AiUndoDraftArguments): AiDraft {
        val draft = mutableDraft(scope, draftId)
        val alreadyUndone = draft.revisions.mapNotNull(AiDraftRevision::undoneRevision).toSet()
        val target = draft.revisions.asReversed().firstOrNull {
            it.action != "undo" && it.revision !in alreadyUndone
        } ?: throw AiDraftFailure("nothing-to-undo", "The private draft has no remaining mutation to undo.")
        val now = clock.instant()
        return saveMutation(
            draft,
            target.beforeItems.renumber(now),
            "undo",
            requiredExplanation(arguments.explanation),
            now,
            undoneRevision = target.revision,
        )
    }

    @Synchronized
    public fun clear(scope: AiCapabilityScope, draftId: String, arguments: AiClearDraftArguments): AiDraft {
        val draft = mutableDraft(scope, draftId)
        if (draft.items.isEmpty()) throw AiDraftFailure("draft-already-empty", "The private draft is already empty.")
        return saveMutation(draft, emptyList(), "clear", requiredExplanation(arguments.explanation), clock.instant())
    }

    /** Holds the private-draft mutation lock while one exact revision enters the shared review workflow. */
    @Synchronized
    internal fun <T> submitForReview(
        scope: AiCapabilityScope,
        draftId: String,
        expectedRevision: Int,
        expectedFingerprint: String,
        import: (AiDraft) -> T,
    ): Pair<AiDraft, T> {
        val draft = read(scope, draftId)
        val revision = draft.revisions.maxOfOrNull(AiDraftRevision::revision) ?: 0
        if (draft.status != AiDraftStatus.READY_FOR_REVIEW ||
            revision != expectedRevision ||
            draft.draftFingerprint != expectedFingerprint
        ) {
            throw AiDraftFailure("concurrent-draft-mutation", "The private draft changed before review submission completed.")
        }
        val imported = import(draft)
        val submitted = store.update(draft.copy(status = AiDraftStatus.SUBMITTED, updatedAt = clock.instant()))
        return submitted to imported
    }

    private fun mutableDraft(scope: AiCapabilityScope, draftId: String): AiDraft {
        val draft = read(scope, draftId)
        if (draft.baselineFingerprint != scope.baselineFingerprint) {
            store.update(draft.copy(status = AiDraftStatus.STALE, updatedAt = clock.instant()))
            throw AiDraftFailure("stale-draft-baseline", "The private draft baseline no longer matches the current project.")
        }
        if (draft.status == AiDraftStatus.SUBMITTED) {
            throw AiDraftFailure("submitted-draft-immutable", "A submitted private draft cannot be mutated.")
        }
        return draft
    }

    private fun saveMutation(
        draft: AiDraft,
        items: List<AiDraftItem>,
        action: String,
        explanation: String,
        now: Instant,
        undoneRevision: Int? = null,
    ): AiDraft {
        val normalized = items.sortedWith(compareBy(AiDraftItem::order, AiDraftItem::id))
        val revision = AiDraftRevision(
            revision = (draft.revisions.maxOfOrNull(AiDraftRevision::revision) ?: 0) + 1,
            action = action,
            explanation = explanation,
            itemIds = normalized.map(AiDraftItem::id),
            createdAt = now,
            beforeItems = draft.items,
            afterItems = normalized,
            undoneRevision = undoneRevision,
        )
        return store.update(
            draft.copy(
                items = normalized,
                revisions = draft.revisions + revision,
                status = if (normalized.isEmpty()) AiDraftStatus.EMPTY else AiDraftStatus.EDITING,
                draftFingerprint = fingerprint(normalized),
                analysisReferenceIds = emptyList(),
                updatedAt = now,
            ),
        )
    }

    private fun validateDependencies(items: List<AiDraftItem>, dependencies: List<String>, currentItemId: String? = null) {
        if (dependencies.distinct().size != dependencies.size) {
            throw AiDraftFailure("duplicate-draft-dependency", "Draft dependency IDs must be unique.")
        }
        if (currentItemId in dependencies) throw AiDraftFailure("self-draft-dependency", "A draft item cannot depend on itself.")
        val known = items.map(AiDraftItem::id).toSet()
        val missing = dependencies.filterNot(known::contains)
        if (missing.isNotEmpty()) throw AiDraftFailure("unknown-draft-dependency", "Unknown draft dependency '${missing.first()}'.")
    }

    private fun rejectDuplicateOrConflict(items: List<AiDraftItem>, operation: AiTypedDraftOperation, ignoredItemId: String?) {
        val existing = items.filter { it.id != ignoredItemId }.mapNotNull { item ->
            (item.operation as? AiTypedDraftOperation)?.let { item.id to it }
        }
        if (existing.any { (_, current) -> current.request == operation.request }) {
            throw AiDraftFailure("duplicate-draft-edit", "The private draft already contains this typed edit.")
        }
        val key = operation.conflictKey() ?: return
        if (existing.any { (_, current) -> current.conflictKey() == key }) {
            throw AiDraftFailure("conflicting-draft-edit", "The private draft already contains a conflicting edit for this target.")
        }
    }

    private fun AiTypedDraftOperation.conflictKey(): String? {
        fun value(name: String): String? = normalizedValues[name]
        return when (request.editType) {
            "create-class" -> "create-class:${value("classIri")}"
            "set-entity-label" -> "label:${value("resourceIri")}"
            "create-object-property", "create-datatype-property" -> "create-property:${value("propertyIri")}"
            "set-property-domain" -> "domain:${value("propertyIri")}"
            "set-property-range" -> "range:${value("propertyIri")}"
            "create-individual" -> "create-individual:${value("individualIri")}"
            "assign-type" -> "type:${value("resourceIri")}:${value("typeIri")}"
            "delete" -> "delete:${request.targetIri ?: request.targetLabel}"
            "shacl-update-constraint", "shacl-remove-constraint" ->
                "shacl-constraint:${value("shapeIri")}:${value("pathIri")}:${value("constraintKind")}"
            "shacl-delete-shape" -> "shacl-delete:${value("shapeIri")}"
            else -> null
        }
    }

    private fun AiTypedDraftOperation.definitionTargetKey(): String? {
        if (request.editType != "add-definition") return null
        val target = request.targetIri ?: normalizedValues["targetIri"] ?: request.targetLabel ?: return null
        return "${request.sourceId}:$target"
    }

    private fun List<AiDraftItem>.renumber(now: Instant): List<AiDraftItem> =
        mapIndexed { index, item -> item.copy(order = index + 1, updatedAt = now) }

    private fun validateRationale(rationale: String): Unit {
        if (rationale.isBlank() || rationale.length > 4_000) {
            throw AiDraftFailure("invalid-draft-rationale", "A rationale between 1 and 4000 characters is required.")
        }
    }

    private fun requiredExplanation(explanation: String): String = explanation.trim().also {
        if (it.isBlank() || it.length > 4_000) {
            throw AiDraftFailure("invalid-draft-explanation", "An explanation between 1 and 4000 characters is required.")
        }
    }

    private fun fingerprint(items: List<AiDraftItem>): String {
        val material = items.sortedBy(AiDraftItem::order).joinToString("\u0000") { item ->
            val operation = item.operation as AiTypedDraftOperation
            listOf(
                item.id,
                item.order.toString(),
                operation.capabilityName,
                operation.targetSourceId,
                operation.request.copy(dependencyKeys = operation.request.dependencyKeys.toSortedSet()).toString(),
                item.rationale,
                item.dependencyItemIds.sorted().joinToString(","),
            ).joinToString("|")
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

internal fun typedEditCapabilityDefinitions(): List<AiCapabilityDefinition> = listOf(
    typedEditDefinition(
        AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
        AiCapabilityOperationType.DRAFT_ADD_TYPED_EDIT,
        "Add one approved ontology or deletion request to the current private AI draft.",
        AiTypedEditCapabilityInventory.approvedOntologyEditTypes +
            AiTypedEditCapabilityInventory.approvedSemanticMetadataEditTypes - "add-definition",
        update = false,
    ),
    addDefinitionCapabilityDefinition(),
    typedEditDefinition(
        AiTypedEditCapabilityAdapter.ADD_SHACL_CAPABILITY,
        AiCapabilityOperationType.DRAFT_ADD_TYPED_EDIT,
        "Add one bounded typed SHACL request to the current private AI draft.",
        AiTypedEditCapabilityInventory.approvedShaclEditTypes,
        update = false,
    ),
    typedEditDefinition(
        AiTypedEditCapabilityAdapter.UPDATE_ONTOLOGY_CAPABILITY,
        AiCapabilityOperationType.DRAFT_UPDATE_TYPED_EDIT,
        "Replace one private AI draft item with an approved ontology or deletion request.",
        AiTypedEditCapabilityInventory.approvedOntologyEditTypes +
            AiTypedEditCapabilityInventory.approvedSemanticMetadataEditTypes,
        update = true,
    ),
    typedEditDefinition(
        AiTypedEditCapabilityAdapter.UPDATE_SHACL_CAPABILITY,
        AiCapabilityOperationType.DRAFT_UPDATE_TYPED_EDIT,
        "Replace one private AI draft item with a bounded typed SHACL request.",
        AiTypedEditCapabilityInventory.approvedShaclEditTypes,
        update = true,
    ),
    draftMutationDefinition(
        name = "entio_draft_remove_item",
        operationType = AiCapabilityOperationType.DRAFT_REMOVE_ITEM,
        description = "Remove one item from the current private AI draft.",
        properties = listOf(textProperty("itemId", 128), textProperty("explanation", 4_000)),
        required = setOf("itemId", "explanation"),
        decoder = AiCapabilityArgumentDecoder { input ->
            val value = DraftStrictObject(input, setOf("itemId", "explanation"), setOf("itemId", "explanation"))
            AiRemoveDraftItemArguments(value.string("itemId", 128), value.string("explanation", 4_000))
        },
    ),
    draftMutationDefinition(
        name = "entio_draft_reorder_items",
        operationType = AiCapabilityOperationType.DRAFT_REORDER_ITEMS,
        description = "Set the complete deterministic order of the current private AI draft.",
        properties = listOf(
            AiSchemaProperty("itemIds", AiArraySchema(AiStringSchema(maxLength = 128), minItems = 1, maxItems = 50), description = "Every draft item ID exactly once."),
            textProperty("explanation", 4_000),
        ),
        required = setOf("itemIds", "explanation"),
        decoder = AiCapabilityArgumentDecoder { input ->
            val value = DraftStrictObject(input, setOf("itemIds", "explanation"), setOf("itemIds", "explanation"))
            AiReorderDraftItemsArguments(value.stringArray("itemIds", 1, 50, 128), value.string("explanation", 4_000))
        },
    ),
    simpleDraftDefinition(
        "entio_draft_undo",
        AiCapabilityOperationType.DRAFT_UNDO,
        "Undo the latest non-undone private draft mutation.",
        ::AiUndoDraftArguments,
    ),
    simpleDraftDefinition(
        "entio_draft_clear",
        AiCapabilityOperationType.DRAFT_CLEAR,
        "Clear all items from the current private AI draft.",
        ::AiClearDraftArguments,
    ),
)

private fun addDefinitionCapabilityDefinition(): AiCapabilityDefinition = AiCapabilityDefinition(
    name = AiTypedEditCapabilityAdapter.ADD_DEFINITION_CAPABILITY,
    operationType = AiCapabilityOperationType.DRAFT_ADD_TYPED_EDIT,
    category = AiCapabilityCategory.PRIVATE_DRAFT,
    description = "Stage one concise ontological definition for one existing entity. Call once per requested class; after success, use the returned draft inventory to select an entity not yet covered.",
    inputSchema = AiObjectSchema(
        properties = listOf(
            AiSchemaProperty("sourceId", AiStringSchema(maxLength = 128, format = AiStringFormat.SOURCE_ID), description = "Allowed writable source ID containing the target entity."),
            AiSchemaProperty("targetLabel", AiStringSchema(maxLength = 2_048), description = "Exact existing ontology label returned by Entio context."),
            AiSchemaProperty("entityIri", AiStringSchema(maxLength = 2_048, format = AiStringFormat.HTTP_IRI), description = "Optional exact entity IRI when the model has one from an earlier draft or context read.", nullable = true),
            AiSchemaProperty(
                "value",
                AiStringSchema(maxLength = 10_000),
                description = "Exact definition to stage. Write an entity-centered ontological statement, not an instruction, purpose statement, rationale, or text beginning with 'To define'. Avoid circular restatement.",
            ),
            AiSchemaProperty("rationale", AiStringSchema(maxLength = 4_000), description = "Why this definition is appropriate; this is separate from the definition value."),
        ),
        required = setOf("sourceId", "targetLabel", "value", "rationale"),
    ),
    access = AiCapabilityAccess.PRIVATE_DRAFT_MUTATION,
    requiredRole = AiRequiredRole.CONTRIBUTOR,
    requiredPermissions = setOf(WebPermission.USE_AI.name, WebPermission.PREPARE_EDIT.name),
    requiredFeature = AiCapabilityFeatures.PRIVATE_DRAFT,
    sourceScope = AiSourceScopeRule.REQUIRED_ALLOWED_SOURCE,
    resultLimit = 1,
    timeoutMillis = 10_000,
    auditClassification = AiCapabilityAuditClassification.PRIVATE_DRAFT_CHANGE,
    decoder = AiCapabilityArgumentDecoder { input ->
        val value = DraftStrictObject(input, setOf("sourceId", "targetLabel", "entityIri", "value", "rationale"), setOf("sourceId", "value", "rationale"))
        val sourceId = value.sourceId("sourceId")
        val targetLabel = value.optionalString("targetLabel")
        val entityIri = value.optionalIri("entityIri")
        if (targetLabel == null && entityIri == null) {
            throw AiCapabilityFailure("missing-argument", "Either targetLabel or entityIri is required.")
        }
        AiAddDraftItemArguments(
            sourceId = sourceId,
            request = WebStageChangeRequest(
                sourceId = sourceId,
                editType = "add-definition",
                targetIri = entityIri,
                targetLabel = targetLabel,
                value = value.string("value", 10_000),
                aiGenerated = true,
            ),
            rationale = value.string("rationale", 4_000),
        )
    },
)

private fun typedEditDefinition(
    name: String,
    operationType: AiCapabilityOperationType,
    description: String,
    editTypes: Set<String>,
    update: Boolean,
): AiCapabilityDefinition {
    val properties = typedRequestProperties(editTypes, update)
    val required = buildSet {
        add("sourceId")
        add("editType")
        add("rationale")
        if (update) add("itemId")
    }
    return AiCapabilityDefinition(
        name = name,
        operationType = operationType,
        category = AiCapabilityCategory.PRIVATE_DRAFT,
        description = description,
        inputSchema = AiObjectSchema(properties, required),
        access = AiCapabilityAccess.PRIVATE_DRAFT_MUTATION,
        requiredRole = AiRequiredRole.CONTRIBUTOR,
        requiredPermissions = setOf(WebPermission.USE_AI.name, WebPermission.PREPARE_EDIT.name),
        requiredFeature = AiCapabilityFeatures.PRIVATE_DRAFT,
        sourceScope = AiSourceScopeRule.REQUIRED_ALLOWED_SOURCE,
        resultLimit = 1,
        timeoutMillis = 10_000,
        auditClassification = AiCapabilityAuditClassification.PRIVATE_DRAFT_CHANGE,
        decoder = AiCapabilityArgumentDecoder { input -> decodeTypedEditArguments(input, editTypes, update) },
    )
}

private fun decodeTypedEditArguments(input: JsonNode, editTypes: Set<String>, update: Boolean): AiCapabilityArguments {
    val allowed = typedRequestFieldNames + if (update) setOf("itemId") else emptySet()
    val required = setOf("sourceId", "editType", "rationale") + if (update) setOf("itemId") else emptySet()
    val value = DraftStrictObject(input, allowed, required)
    val sourceId = value.sourceId("sourceId")
    val editType = value.enum("editType", editTypes)
    val request = WebStageChangeRequest(
        sourceId = sourceId,
        editType = editType,
        classIri = value.optionalIri("classIri"),
        classLabel = value.optionalString("classLabel"),
        superclassIri = value.optionalIri("superclassIri"),
        superclassLabel = value.optionalString("superclassLabel"),
        propertyIri = value.optionalIri("propertyIri"),
        propertyLabel = value.optionalString("propertyLabel"),
        domainClassIri = value.optionalIri("domainClassIri"),
        domainClassLabel = value.optionalString("domainClassLabel"),
        rangeIri = value.optionalIri("rangeIri"),
        rangeLabel = value.optionalString("rangeLabel"),
        individualIri = value.optionalIri("individualIri"),
        individualLabel = value.optionalString("individualLabel"),
        resourceIri = value.optionalIri("resourceIri"),
        resourceLabel = value.optionalString("resourceLabel"),
        typeIri = value.optionalIri("typeIri"),
        typeLabel = value.optionalString("typeLabel"),
        subjectIri = value.optionalIri("subjectIri"),
        subjectLabel = value.optionalString("subjectLabel"),
        objectIri = value.optionalIri("objectIri"),
        objectLabel = value.optionalString("objectLabel"),
        targetIri = value.optionalIri("targetIri") ?: value.optionalIri("entityIri"),
        targetLabel = value.optionalString("targetLabel"),
        shapeIri = value.optionalIri("shapeIri"),
        shapeLabel = value.optionalString("shapeLabel"),
        targetClassIri = value.optionalIri("targetClassIri"),
        targetClassLabel = value.optionalString("targetClassLabel"),
        pathIri = value.optionalIri("pathIri"),
        pathLabel = value.optionalString("pathLabel"),
        constraintKind = value.optionalString("constraintKind", 64),
        constraintValue = value.optionalString("constraintValue"),
        severity = value.optionalString("severity", 32),
        validationMessage = value.optionalString("validationMessage", 4_000),
        dependencyKeys = value.optionalStringArray("dependencyKeys", 50, 4_000).toSet(),
        label = value.optionalString("label"),
        value = value.optionalString("value", 10_000),
        existingValue = value.optionalString("existingValue", 10_000),
        datatypeIri = value.optionalIri("datatypeIri"),
        aiGenerated = true,
    )
    val rationale = value.string("rationale", 4_000)
    val dependencies = value.optionalStringArray("dependencyItemIds", 50, 128)
    return if (update) {
        AiUpdateDraftItemArguments(value.string("itemId", 128), sourceId, request, rationale, dependencies)
    } else {
        AiAddDraftItemArguments(sourceId, request, rationale, dependencies)
    }
}

private fun typedRequestProperties(editTypes: Set<String>, update: Boolean): List<AiSchemaProperty> = buildList {
    if (update) add(textProperty("itemId", 128))
    add(AiSchemaProperty("sourceId", AiStringSchema(maxLength = 128, format = AiStringFormat.SOURCE_ID), description = "Allowed writable source ID."))
    add(AiSchemaProperty("editType", AiStringSchema(maxLength = 64, allowedValues = editTypes.sorted()), description = "Approved typed edit operation."))
    add(textProperty("rationale", 4_000))
    add(AiSchemaProperty("dependencyItemIds", AiArraySchema(AiStringSchema(maxLength = 128), maxItems = 50), nullable = true, description = "Private draft item dependencies."))
    typedRequestFieldNames.filterNot { it in setOf("sourceId", "editType", "rationale", "dependencyItemIds") }.sorted().forEach { field ->
        when (field) {
            "dependencyKeys" -> add(AiSchemaProperty(field, AiArraySchema(AiStringSchema(maxLength = 4_000), maxItems = 50), nullable = true, description = "Explicit deletion dependency keys."))
            in iriFieldNames -> add(AiSchemaProperty(field, AiStringSchema(maxLength = 2_048, format = AiStringFormat.HTTP_IRI), nullable = true, description = "Optional absolute IRI resolved by the existing typed adapter."))
            "value", "existingValue" -> add(textProperty(field, 10_000, nullable = true))
            "validationMessage" -> add(textProperty(field, 4_000, nullable = true))
            else -> add(textProperty(field, 2_048, nullable = true))
        }
    }
}

private fun draftMutationDefinition(
    name: String,
    operationType: AiCapabilityOperationType,
    description: String,
    properties: List<AiSchemaProperty>,
    required: Set<String>,
    decoder: AiCapabilityArgumentDecoder,
): AiCapabilityDefinition = AiCapabilityDefinition(
    name = name,
    operationType = operationType,
    category = AiCapabilityCategory.PRIVATE_DRAFT,
    description = description,
    inputSchema = AiObjectSchema(properties, required),
    access = AiCapabilityAccess.PRIVATE_DRAFT_MUTATION,
    requiredRole = AiRequiredRole.CONTRIBUTOR,
    requiredPermissions = setOf(WebPermission.USE_AI.name, WebPermission.PREPARE_EDIT.name),
    requiredFeature = AiCapabilityFeatures.PRIVATE_DRAFT,
    sourceScope = AiSourceScopeRule.NONE,
    resultLimit = 1,
    timeoutMillis = 5_000,
    auditClassification = AiCapabilityAuditClassification.PRIVATE_DRAFT_CHANGE,
    decoder = decoder,
)

private fun simpleDraftDefinition(
    name: String,
    operationType: AiCapabilityOperationType,
    description: String,
    factory: (String) -> AiCapabilityArguments,
): AiCapabilityDefinition = draftMutationDefinition(
    name = name,
    operationType = operationType,
    description = description,
    properties = listOf(textProperty("explanation", 4_000)),
    required = setOf("explanation"),
    decoder = AiCapabilityArgumentDecoder { input ->
        val value = DraftStrictObject(input, setOf("explanation"), setOf("explanation"))
        factory(value.string("explanation", 4_000))
    },
)

private fun textProperty(name: String, maximum: Int, nullable: Boolean = false): AiSchemaProperty =
    AiSchemaProperty(name, AiStringSchema(maxLength = maximum), nullable = nullable, description = name)

private class DraftStrictObject(
    private val input: JsonNode,
    allowedFields: Set<String>,
    requiredFields: Set<String>,
) {
    init {
        if (!input.isObject) throw AiCapabilityFailure("malformed-arguments", "Capability arguments must be a JSON object.")
        val present = input.fieldNames().asSequence().toSet()
        val unknown = present - allowedFields
        if (unknown.isNotEmpty()) throw AiCapabilityFailure("unknown-argument", "Unknown capability argument: ${unknown.sorted().joinToString()}.")
        val missing = requiredFields - present
        if (missing.isNotEmpty()) throw AiCapabilityFailure("missing-argument", "Missing capability argument: ${missing.sorted().joinToString()}.")
    }

    fun string(name: String, maximum: Int): String {
        val node = input.get(name)
        if (node == null || !node.isTextual) throw AiCapabilityFailure("malformed-argument", "$name must be a string.")
        return node.textValue().also {
            if (it.isBlank() || it.length > maximum) throw AiCapabilityFailure("argument-out-of-range", "$name has an invalid length.")
        }
    }

    fun optionalString(name: String, maximum: Int = 2_048): String? {
        val node = input.get(name) ?: return null
        if (node.isNull) return null
        return string(name, maximum)
    }

    fun sourceId(name: String): String = string(name, 128).also {
        if (!it.matches(Regex("[A-Za-z][A-Za-z0-9._-]{0,127}"))) {
            throw AiCapabilityFailure("invalid-source-id", "$name is not a valid source ID.")
        }
    }

    fun optionalIri(name: String): String? = optionalString(name)?.also {
        if (!it.startsWith("http://") && !it.startsWith("https://")) {
            throw AiCapabilityFailure("invalid-iri", "$name must be an absolute HTTP or HTTPS IRI.")
        }
    }

    fun enum(name: String, values: Set<String>): String = string(name, 64).also {
        if (it !in values) throw AiCapabilityFailure("invalid-enum-value", "$name is not an allowed value.")
    }

    fun optionalStringArray(name: String, maximumItems: Int, maximumLength: Int): List<String> {
        val node = input.get(name) ?: return emptyList()
        if (node.isNull) return emptyList()
        return stringArray(name, 0, maximumItems, maximumLength)
    }

    fun stringArray(name: String, minimumItems: Int, maximumItems: Int, maximumLength: Int): List<String> {
        val node = input.get(name)
        if (node == null || !node.isArray) throw AiCapabilityFailure("malformed-argument", "$name must be an array.")
        if (node.size() !in minimumItems..maximumItems) throw AiCapabilityFailure("array-limit", "$name has an invalid number of values.")
        val values = node.map { item ->
            if (!item.isTextual || item.textValue().isBlank() || item.textValue().length > maximumLength) {
                throw AiCapabilityFailure("malformed-argument", "$name entries must be bounded non-empty strings.")
            }
            item.textValue()
        }
        if (values.distinct().size != values.size) throw AiCapabilityFailure("duplicate-array-value", "$name values must be unique.")
        return values
    }
}

private val iriFieldNames: Set<String> = setOf(
    "classIri",
    "superclassIri",
    "propertyIri",
    "domainClassIri",
    "rangeIri",
    "individualIri",
    "resourceIri",
    "typeIri",
    "subjectIri",
    "objectIri",
    "targetIri",
    "entityIri",
    "shapeIri",
    "targetClassIri",
    "pathIri",
    "datatypeIri",
)

private val typedRequestFieldNames: Set<String> = setOf(
    "sourceId",
    "editType",
    "rationale",
    "dependencyItemIds",
    "classIri",
    "classLabel",
    "superclassIri",
    "superclassLabel",
    "propertyIri",
    "propertyLabel",
    "domainClassIri",
    "domainClassLabel",
    "rangeIri",
    "rangeLabel",
    "individualIri",
    "individualLabel",
    "resourceIri",
    "resourceLabel",
    "typeIri",
    "typeLabel",
    "subjectIri",
    "subjectLabel",
    "objectIri",
    "objectLabel",
    "targetIri",
    "entityIri",
    "targetLabel",
    "shapeIri",
    "shapeLabel",
    "targetClassIri",
    "targetClassLabel",
    "pathIri",
    "pathLabel",
    "constraintKind",
    "constraintValue",
    "severity",
    "validationMessage",
    "dependencyKeys",
    "label",
    "value",
    "existingValue",
    "datatypeIri",
)
