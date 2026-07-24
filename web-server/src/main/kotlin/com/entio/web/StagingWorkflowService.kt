package com.entio.web

import com.entio.core.AddDatatypePropertyAssertionEdit
import com.entio.core.AddObjectPropertyAssertionEdit
import com.entio.core.AddSuperclassEdit
import com.entio.core.AssignTypeEdit
import com.entio.core.ChangeProposal
import com.entio.core.ChangeProposalStatus
import com.entio.core.ExternalProposalIntent
import com.entio.core.ExternalCatalogElement
import com.entio.core.CreateClassEdit
import com.entio.core.CreateDatatypePropertyEdit
import com.entio.core.CreateIndividualEdit
import com.entio.core.CreateObjectPropertyEdit
import com.entio.core.EntityCandidate
import com.entio.core.EntityResolutionResult
import com.entio.core.EntitySelector
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.GeneratedIri
import com.entio.core.GraphChangeKind
import com.entio.core.GraphTriple
import com.entio.core.GraphChange
import com.entio.core.GraphState
import com.entio.core.Iri
import com.entio.core.InferenceMaterializationBatchResult
import com.entio.core.InferenceMaterializationMapping
import com.entio.core.PreparedInferenceMaterialization
import com.entio.core.PreparedInferenceMaterializationBatch
import com.entio.core.MultiSourceApplyStatus
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.SemanticEditRequest
import com.entio.core.RemovePropertyDomainEdit
import com.entio.core.RemovePropertyRangeEdit
import com.entio.core.ShaclPath
import com.entio.core.SetEntityLabelEdit
import com.entio.core.SetPropertyDomainEdit
import com.entio.core.SetPropertyRangeEdit
import com.entio.core.StagedChange
import com.entio.core.StagedChangeOperation
import com.entio.core.StagedChangeSetStatus
import com.entio.core.SymbolKind
import com.entio.core.TypedOntologyEdit
import com.entio.core.DocumentDraftProvenance
import com.entio.diff.GraphDiffer
import com.entio.semantic.DeletionDependencyAnalyzer
import com.entio.semantic.DeterministicIriGenerator
import com.entio.semantic.ExternalProposalIntentTranslator
import com.entio.semantic.LabelResolver
import com.entio.semantic.MultiSourceAtomicApplier
import com.entio.semantic.ProjectLoader
import com.entio.semantic.ProposalCreator
import com.entio.semantic.StagedChangeSetNormalizer
import com.entio.semantic.TypedOntologyEditTranslator
import com.entio.semantic.TypedShaclEditTranslator
import com.entio.semantic.DocumentDraftOperation
import com.entio.web.ingestion.DocumentApplyHooks
import com.entio.web.contract.IdempotencyDecision
import com.entio.web.contract.InMemoryIdempotencyStore
import com.entio.web.contract.ProjectRegistry
import com.entio.web.contract.WebProposalState
import com.entio.web.contract.WebProposalRemediation
import com.entio.web.contract.WebProposalValidationIssue
import com.entio.web.contract.WebDeletionDependenciesRequest
import com.entio.web.contract.WebDeletionDependenciesResponse
import com.entio.web.contract.WebDeletionDependency
import com.entio.web.contract.WebShaclFindingSummary
import com.entio.web.contract.WebShaclImpact
import com.entio.web.contract.WebStageChangeRequest
import com.entio.web.contract.WebStagedEntry
import com.entio.web.contract.WebInferenceMaterializationProvenance
import com.entio.web.contract.WebDocumentDraftProvenance
import com.entio.web.contract.WebStagingResponse
import com.entio.web.contract.WebDiffEntry
import java.time.Clock
import java.time.Instant

public class WebWorkflowFailure(
    public val code: String,
    message: String,
) : IllegalArgumentException(message)

public data class WorkflowGraphSnapshot(
    val graph: com.entio.core.GraphState,
    val graphFingerprint: String,
    val proposalFingerprint: String? = null,
)

private data class StoredEntry(
    val staged: StagedChange,
    val editType: String,
    val authorId: String,
    var latestEditorId: String,
    val comment: String?,
)

private data class ProjectSession(
    val entries: MutableList<StoredEntry> = mutableListOf(),
    var proposal: ChangeProposal? = null,
    var preparedProposal: PreparedWebProposal? = null,
    val idempotency: InMemoryIdempotencyStore = InMemoryIdempotencyStore(),
    val materializationReplays: MutableMap<String, InferenceMaterializationBatchResult> = linkedMapOf(),
    val documentBatchCounts: MutableMap<String, Int> = linkedMapOf(),
    val documentBatchPayloads: MutableMap<String, String> = linkedMapOf(),
    var nextOrder: Int = 1,
)

public data class PreparedDocumentStagingItem(
    val summary: String,
    val editType: String,
    val targetSourceId: String,
    val operation: DocumentDraftOperation,
    val provenance: DocumentDraftProvenance,
)

private data class PreparedStageRequest(
    val request: WebStageChangeRequest,
    val resolvedCandidates: List<EntityCandidate>,
    val generatedIris: List<GeneratedIri>,
)

private data class PreparedChange(
    val summary: String,
    val operation: StagedChangeOperation,
    val normalizedValues: Map<String, String>,
    val resolvedCandidates: List<EntityCandidate>,
    val generatedIris: List<GeneratedIri>,
)

public data class InferenceMaterializationStagingResult(
    val staging: WebStagingResponse,
    val batch: InferenceMaterializationBatchResult,
)

/** Server-owned single-client staging state that delegates semantic work to Kotlin services. */
public class StagingWorkflowService(
    private val projectRegistry: ProjectRegistry,
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val normalizer: StagedChangeSetNormalizer = StagedChangeSetNormalizer(),
    private val proposalCreator: ProposalCreator = ProposalCreator(),
    private val proposalValidator: com.entio.validation.ProposalValidator = com.entio.validation.ProposalValidator(),
    private val graphDiffer: GraphDiffer = GraphDiffer(),
    private val deletionAnalyzer: DeletionDependencyAnalyzer = DeletionDependencyAnalyzer(),
    private val externalIntentTranslator: ExternalProposalIntentTranslator = ExternalProposalIntentTranslator(),
    private val labelResolver: LabelResolver = LabelResolver(),
    private val iriGenerator: DeterministicIriGenerator = DeterministicIriGenerator(),
    private val editTranslator: TypedOntologyEditTranslator = TypedOntologyEditTranslator(),
    private val shaclEditTranslator: TypedShaclEditTranslator = TypedShaclEditTranslator(),
    private val additionalPostApplyVerification: (String, EntioProject) -> EntioResult<Unit> = { _, _ -> EntioResult.Success(Unit) },
    private val clock: Clock = Clock.systemUTC(),
) {
    private var documentApplyHooks: DocumentApplyHooks? = null
    private val sessions: MutableMap<String, ProjectSession> = linkedMapOf()
    private val proposalPlanner: WebProposalPlanner = WebProposalPlanner(
        normalizer = normalizer,
        proposalCreator = proposalCreator,
        proposalValidator = proposalValidator,
        graphDiffer = graphDiffer,
    )
    private val shaclStagePreparer: WebShaclStagePreparer = WebShaclStagePreparer(
        labelResolver = labelResolver,
        iriGenerator = iriGenerator,
    )

    internal fun installDocumentApplyHooks(hooks: DocumentApplyHooks): Unit {
        check(documentApplyHooks == null) { "Document apply hooks are already installed." }
        documentApplyHooks = hooks
    }

    @Synchronized
    public fun snapshot(projectId: String): WebStagingResponse = response(projectId, session(projectId))

    @Synchronized
    internal fun materializationStagedIds(projectId: String): Map<com.entio.core.SemanticFactKey, String> =
        session(projectId).entries.mapNotNull { entry ->
            entry.staged.materializationProvenance?.semanticFactKey?.let { it to entry.staged.id }
        }.toMap()

    @Synchronized
    public fun deletionDependencies(
        projectId: String,
        request: WebDeletionDependenciesRequest,
    ): WebDeletionDependenciesResponse {
        val project = load(projectId)
        val target = project.symbols.firstOrNull { symbol ->
            request.targetIri == symbol.iri.value ||
                (request.targetIri == null && request.targetLabel == symbol.label)
        } ?: throw WebWorkflowFailure("unknown-delete-target", "The deletion target does not exist.")
        val ontology = project.ontologies.firstOrNull { it.source.id == request.sourceId }
            ?: throw WebWorkflowFailure("unknown-source", "Ontology source '${request.sourceId}' was not found.")
        val plan = deletionAnalyzer.analyze(
            ontology,
            EntityCandidate(target.iri, target.label, target.kind, target.sourceId),
        )
        val labels = project.symbols.mapNotNull { symbol -> symbol.label?.let { symbol.iri.value to it } }.toMap()
        val targetLabel = target.label ?: target.iri.value.substringAfterLast('#').substringAfterLast('/')
        return WebDeletionDependenciesResponse(
            projectId = projectId,
            targetIri = target.iri.value,
            targetLabel = targetLabel,
            status = plan.status.name,
            directStatements = plan.directStatements.map { webDeletionDependency(it, labels) },
            dependentStatements = plan.dependentStatements.map { webDeletionDependency(it, labels) },
        )
    }

    @Synchronized
    public fun graphSnapshot(projectId: String, scope: WebJobScope): WorkflowGraphSnapshot {
        val project = load(projectId)
        return when (scope) {
            WebJobScope.Applied -> WorkflowGraphSnapshot(
                graph = project.graph,
                graphFingerprint = webGraphFingerprint(project.graph),
            )
            WebJobScope.Proposal -> {
                val proposal = session(projectId).proposal
                    ?: throw WebWorkflowFailure("missing-proposal", "A proposal preview is required for proposal-scoped semantic work.")
                val previewGraph = proposal.preview?.graph
                    ?: throw WebWorkflowFailure("missing-proposal-preview", "The proposal has no preview graph.")
                WorkflowGraphSnapshot(
                    graph = previewGraph,
                    graphFingerprint = webGraphFingerprint(previewGraph),
                    proposalFingerprint = webProposalFingerprint(proposal.id, previewGraph),
                )
            }
        }
    }

    /**
     * Builds the current effective proposal graph for read-only consumers without
     * creating or changing proposal review state.
     */
    @Synchronized
    public fun inferredReadGraphSnapshot(projectId: String): WorkflowGraphSnapshot {
        val project = load(projectId)
        val staged = session(projectId).entries.map(StoredEntry::staged)
        if (staged.isEmpty()) {
            throw WebWorkflowFailure("missing-proposal", "There are no staged changes for proposal-scoped inferred reads.")
        }
        val prepared = proposalPlanner.prepare(project, staged)
        val previewGraph = prepared.proposal.preview?.graph
            ?: throw WebWorkflowFailure("missing-proposal-preview", "The staged changes did not produce a proposal graph.")
        return WorkflowGraphSnapshot(
            graph = previewGraph,
            graphFingerprint = webGraphFingerprint(previewGraph),
            proposalFingerprint = webProposalFingerprint(prepared.proposal.id, previewGraph),
        )
    }

    @Synchronized
    public fun stage(projectId: String, request: WebStageChangeRequest, userId: String): WebStagingResponse {
        val session = session(projectId)
        val key = request.idempotencyKey
        if (key != null) {
            when (val decision = session.idempotency.begin(key, request.toString())) {
                is IdempotencyDecision.Replay -> return response(projectId, session)
                is IdempotencyDecision.Conflict -> throw WebWorkflowFailure("idempotency-conflict", "The idempotency key was already used for another stage request.")
                is IdempotencyDecision.Accepted -> Unit
            }
        }

        val project = load(projectId)
        val prepared = prepareChange(project, request)
        val replacementIndex = request.replacesStagedId?.let { stagedId ->
            session.entries.indexOfFirst { it.staged.id == stagedId }
                .takeIf { it >= 0 }
                ?: throw WebWorkflowFailure("unknown-staged-change", "Staged change '$stagedId' was not found.")
        }
        val replaced = replacementIndex?.let(session.entries::get)
        val id = replaced?.staged?.id ?: "stage-${session.nextOrder++}"
        val order = replaced?.staged?.order ?: session.nextOrder - 1
        val staged = StagedChange(
            id = id,
            order = order,
            targetSourceId = request.sourceId,
            summary = prepared.summary,
            operation = prepared.operation,
            normalizedValues = prepared.normalizedValues,
            resolvedCandidates = prepared.resolvedCandidates,
            generatedIris = prepared.generatedIris,
        )
        val stored = StoredEntry(
            staged = staged,
            editType = request.editType,
            authorId = replaced?.authorId ?: userId,
            latestEditorId = userId,
            comment = request.comment,
        )
        if (replacementIndex == null) session.entries += stored else session.entries[replacementIndex] = stored
        session.clearPreparedProposal()
        return response(projectId, session)
    }

    /**
     * Atomically appends a fully prepared reasoning batch.
     *
     * The semantic adapter has already chosen typed edits and source candidates. This
     * boundary independently checks current applied/staged state before one session
     * mutation and replaces caller-supplied author/time values with server values.
     */
    @Synchronized
    public fun stageMaterializations(
        projectId: String,
        userId: String,
        idempotencyKey: String,
        preparedItems: PreparedInferenceMaterializationBatch,
    ): InferenceMaterializationStagingResult {
        require(userId.isNotBlank()) { "materialization-user-required" }
        require(idempotencyKey.isNotBlank()) { "idempotency-key-required" }
        val session = session(projectId)
        val project = load(projectId)
        val items = preparedItems.items

        items.forEach { item -> validatePreparedMaterialization(project, item) }

        val existingByTriple = session.entries
            .sortedBy { it.staged.order }
            .flatMap { entry ->
                materializedGraphChanges(entry.staged, project).map { change -> change.triple to entry.staged.id }
            }
            .toMap()
        val existingMappings = items.mapNotNull { item ->
            existingByTriple[item.triple]?.let { stagedId -> InferenceMaterializationMapping(item.factId, stagedId) }
        }
        val newItems = items.filter { it.triple !in existingByTriple }

        if (newItems.isEmpty()) {
            return InferenceMaterializationStagingResult(
                staging = response(projectId, session),
                batch = InferenceMaterializationBatchResult(existingMappings),
            )
        }

        val payloadFingerprint = items.joinToString("\n") {
            "${it.factId.value}|${it.semanticFactKey.value}|${it.targetSourceId}"
        }
        when (session.idempotency.begin(idempotencyKey, payloadFingerprint)) {
            is IdempotencyDecision.Replay -> {
                val replay = session.materializationReplays[idempotencyKey]
                    ?: throw WebWorkflowFailure("idempotency-replay-unavailable", "The materialization replay result is unavailable.")
                return InferenceMaterializationStagingResult(response(projectId, session), replay)
            }
            is IdempotencyDecision.Conflict ->
                throw WebWorkflowFailure("idempotency-conflict", "The idempotency key was already used for another materialization request.")
            is IdempotencyDecision.Accepted -> Unit
        }

        val stagedAt = Instant.now(clock)
        val nextOrder = session.nextOrder
        val newEntries = newItems.mapIndexed { index, item ->
            val order = nextOrder + index
            StoredEntry(
                staged = StagedChange(
                    id = "stage-$order",
                    order = order,
                    targetSourceId = item.targetSourceId,
                    summary = materializationSummary(item),
                    operation = StagedChangeOperation.TypedEdit(item.edit),
                    materializationProvenance = item.provenance.copy(
                        stagedByUserId = userId,
                        stagedAt = stagedAt,
                        targetSourceId = item.targetSourceId,
                    ),
                ),
                editType = materializationEditType(item),
                authorId = userId,
                latestEditorId = userId,
                comment = "Materialized from reasoning; review before approval.",
            )
        }
        session.entries += newEntries
        session.nextOrder += newEntries.size
        session.clearPreparedProposal()

        val newMappings = newEntries.mapIndexed { index, entry ->
            InferenceMaterializationMapping(newItems[index].factId, entry.staged.id)
        }
        val mappingsByFact = (existingMappings + newMappings).associateBy { it.factId }
        val batchResult = InferenceMaterializationBatchResult(items.map { mappingsByFact.getValue(it.factId) })
        session.materializationReplays[idempotencyKey] = batchResult
        return InferenceMaterializationStagingResult(response(projectId, session), batchResult)
    }

    /** Atomically appends one already-reviewed document-derived batch. */
    @Synchronized
    public fun stageDocumentBatch(
        projectId: String,
        userId: String,
        taskId: String,
        idempotencyKey: String,
        items: List<PreparedDocumentStagingItem>,
    ): WebStagingResponse {
        if (items.isEmpty() || items.size > com.entio.core.MAX_DOCUMENT_DRAFT_BATCH_SIZE) {
            throw WebWorkflowFailure("document-draft-batch-limit", "A document draft batch requires between one and 20 edits.")
        }
        val session = session(projectId)
        val payload = items.joinToString("\n") { item ->
            "${item.provenance.recommendationId}|${item.provenance.normalizedTypedOperationKey}"
        }
        session.documentBatchPayloads[idempotencyKey]?.let { existingPayload ->
            if (existingPayload == payload) {
                return response(projectId, session)
            }
            throw WebWorkflowFailure("idempotency-conflict", "The idempotency key was already used for another draft batch.")
        }
        val project = load(projectId)
        if (session.entries.any { it.staged.documentDraftProvenance?.taskId?.value != taskId }) {
            throw WebWorkflowFailure(
                "document-draft-shared-staging-not-empty",
                "Shared staging must be empty or contain only entries from this document task.",
            )
        }
        val existingDocumentItems = session.entries.count { it.staged.documentDraftProvenance?.taskId?.value == taskId }
        if (existingDocumentItems + items.size > com.entio.core.MAX_ACCEPTED_DOCUMENT_EDITS) {
            throw WebWorkflowFailure("document-draft-task-limit", "A document task cannot stage more than 100 edits.")
        }
        if (session.documentBatchCounts.getOrDefault(taskId, 0) >= 5) {
            throw WebWorkflowFailure("document-draft-batch-count-limit", "A document task cannot stage more than five batches.")
        }
        if (items.any { it.provenance.taskId.value != taskId || it.targetSourceId != it.provenance.targetSourceId }) {
            throw WebWorkflowFailure("document-draft-provenance-mismatch", "Document draft provenance does not match its task or target.")
        }
        val existingKeys = session.entries.mapNotNull { it.staged.documentDraftProvenance?.normalizedTypedOperationKey }.toSet()
        val newKeys = items.mapNotNull { it.provenance.normalizedTypedOperationKey }
        if (newKeys.size != newKeys.distinct().size || newKeys.any(existingKeys::contains)) {
            throw WebWorkflowFailure("document-draft-duplicate", "The document draft contains a duplicate typed operation.")
        }
        val prepared = items.map { item ->
            val source = project.resolvedSources.firstOrNull { it.id == item.targetSourceId }
                ?: throw WebWorkflowFailure("unknown-source", "A document draft target source was not found.")
            val stagedOperation = when (val operation = item.operation) {
                is DocumentDraftOperation.Ontology -> {
                    if (com.entio.core.ShaclGraphRole.Ontology !in source.roles) {
                        throw WebWorkflowFailure("invalid-ontology-source-role", "Ontology edits require an ontology source.")
                    }
                    when (val translated = editTranslator.translate(operation.edit)) {
                        is EntioResult.Failure -> throw WebWorkflowFailure("document-draft-edit-invalid", translated.message)
                        is EntioResult.Success -> if (translated.value.changes.isEmpty()) {
                            throw WebWorkflowFailure("document-draft-edit-empty", "A document draft edit produced no graph change.")
                        } else {
                            StagedChangeOperation.TypedEdit(operation.edit)
                        }
                    }
                }
                is DocumentDraftOperation.Semantic -> {
                    if (com.entio.core.ShaclGraphRole.Ontology !in source.roles || operation.edit.sourceId != item.targetSourceId) {
                        throw WebWorkflowFailure("invalid-ontology-source-role", "Semantic edits require their selected ontology source.")
                    }
                    when (val translated = editTranslator.translate(operation.edit)) {
                        is EntioResult.Failure -> throw WebWorkflowFailure("document-draft-edit-invalid", translated.message)
                        is EntioResult.Success -> StagedChangeOperation.GraphChanges(translated.value)
                    }
                }
                is DocumentDraftOperation.Shacl -> {
                    if (operation.edit.sourceId != item.targetSourceId) {
                        throw WebWorkflowFailure("document-draft-source-mismatch", "The SHACL edit target does not match its source.")
                    }
                    val shapesGraph = project.ontologies.firstOrNull { it.source.id == item.targetSourceId }?.graph
                        ?: throw WebWorkflowFailure("unknown-source", "The SHACL target source was not found.")
                    when (val translated = shaclEditTranslator.translate(operation.edit, shapesGraph)) {
                        is EntioResult.Failure -> throw WebWorkflowFailure("document-draft-edit-invalid", translated.message)
                        is EntioResult.Success -> StagedChangeOperation.ShaclEdit(operation.edit)
                    }
                }
                is DocumentDraftOperation.ExternalReuse -> {
                    when (val translated = externalIntentTranslator.translate(
                        operation.intent,
                        operation.targetOntologyIri,
                        existingGraph = project.graph,
                    )) {
                        is EntioResult.Failure -> throw WebWorkflowFailure("document-draft-external-invalid", translated.message)
                        is EntioResult.Success -> StagedChangeOperation.GraphChanges(translated.value)
                    }
                }
            }
            item to stagedOperation
        }
        val nextOrder = session.nextOrder
        val newEntries = prepared.mapIndexed { index, (item, operation) ->
            val order = nextOrder + index
            StoredEntry(
                staged = StagedChange(
                    id = "stage-$order",
                    order = order,
                    targetSourceId = item.targetSourceId,
                    summary = item.summary,
                    operation = operation,
                    normalizedValues = mapOf(
                        "documentTaskId" to taskId,
                        "documentRecommendationId" to item.provenance.recommendationId,
                    ),
                    documentDraftProvenance = item.provenance,
                ),
                editType = item.editType,
                authorId = userId,
                latestEditorId = userId,
                comment = "Accepted from document evidence; review before approval.",
            )
        }
        session.entries += newEntries
        session.nextOrder += newEntries.size
        session.documentBatchCounts[taskId] = session.documentBatchCounts.getOrDefault(taskId, 0) + 1
        session.documentBatchPayloads[idempotencyKey] = payload
        session.clearPreparedProposal()
        return response(projectId, session, "Document draft batch added to shared staging.")
    }

    @Synchronized
    public fun stageExternal(
        projectId: String,
        sourceId: String,
        targetOntologyIri: Iri,
        intent: ExternalProposalIntent,
        summary: String,
        userId: String,
        idempotencyKey: String? = null,
        materializedElements: List<ExternalCatalogElement> = emptyList(),
        existingGraph: GraphState? = null,
    ): WebStagingResponse {
        val session = session(projectId)
        if (idempotencyKey != null) {
            when (val decision = session.idempotency.begin(idempotencyKey, intent.toString())) {
                is IdempotencyDecision.Replay -> return response(projectId, session)
                is IdempotencyDecision.Conflict -> throw WebWorkflowFailure("idempotency-conflict", "The idempotency key was already used for another stage request.")
                is IdempotencyDecision.Accepted -> Unit
            }
        }
        val project = load(projectId)
        if (project.ontologies.none { it.source.id == sourceId }) {
            throw WebWorkflowFailure("unknown-source", "Ontology source '$sourceId' was not found.")
        }
        val changeSet = when (val translated = externalIntentTranslator.translate(intent, targetOntologyIri, materializedElements, existingGraph)) {
            is EntioResult.Failure -> throw WebWorkflowFailure("external-proposal-invalid", translated.issues.joinToString { it.message })
            is EntioResult.Success -> translated.value
        }
        val id = "stage-${session.nextOrder++}"
        session.entries += StoredEntry(
            staged = StagedChange(
                id = id,
                order = session.nextOrder - 1,
                targetSourceId = sourceId,
                summary = summary,
                operation = StagedChangeOperation.GraphChanges(changeSet),
                normalizedValues = mapOf("externalSource" to intent.sourceId, "targetOntologyIri" to targetOntologyIri.value),
            ),
            editType = "external-reuse",
            authorId = userId,
            latestEditorId = userId,
            comment = "External ontology reuse staged for review.",
        )
        session.clearPreparedProposal()
        return response(projectId, session)
    }

    /** Stages already-translated generic graph changes without touching source files. */
    @Synchronized
    public fun stageGraphChanges(
        projectId: String,
        sourceId: String,
        changeSet: com.entio.core.ChangeSet,
        summary: String,
        userId: String,
        idempotencyKey: String? = null,
        metadata: Map<String, String> = emptyMap(),
    ): WebStagingResponse {
        val session = session(projectId)
        if (idempotencyKey != null) {
            when (val decision = session.idempotency.begin(idempotencyKey, changeSet.toString())) {
                is IdempotencyDecision.Replay -> return response(projectId, session)
                is IdempotencyDecision.Conflict -> throw WebWorkflowFailure("idempotency-conflict", "The idempotency key was already used for another stage request.")
                is IdempotencyDecision.Accepted -> Unit
            }
        }
        val project = load(projectId)
        project.resolvedSources.firstOrNull { it.id == sourceId }
            ?: throw WebWorkflowFailure("unknown-source", "Ontology source '$sourceId' was not found.")
        val id = "stage-${session.nextOrder++}"
        session.entries += StoredEntry(
            staged = StagedChange(
                id = id,
                order = session.nextOrder - 1,
                targetSourceId = sourceId,
                summary = summary,
                operation = StagedChangeOperation.GraphChanges(changeSet),
                normalizedValues = mapOf("proposalSource" to "native-ai") + metadata,
            ),
            editType = "ai-graph-change",
            authorId = userId,
            latestEditorId = userId,
            comment = "Generated by Entio AI; review before approval.",
        )
        session.clearPreparedProposal()
        return response(projectId, session)
    }

    /** Builds a proposal preview from temporary entries; the shared staging queue is not changed. */
    @Synchronized
    public fun previewGraphChanges(
        projectId: String,
        changesBySource: Map<String, List<com.entio.core.GraphChange>>,
        userId: String,
    ): WebStagingResponse {
        val temporary = ProjectSession()
        var order = 1
        changesBySource.toSortedMap().forEach { (sourceId, changes) ->
            if (changes.isEmpty()) return@forEach
            temporary.entries += StoredEntry(
                staged = StagedChange(
                    id = "ai-preview-$order",
                    order = order,
                    targetSourceId = sourceId,
                    summary = "AI proposal graph changes",
                    operation = StagedChangeOperation.GraphChanges(com.entio.core.ChangeSet(changes)),
                ),
                editType = "ai-graph-change",
                authorId = userId,
                latestEditorId = userId,
                comment = "Temporary AI proposal preview.",
            )
            order += 1
        }
        if (temporary.entries.isEmpty()) throw WebWorkflowFailure("empty-ai-proposal", "The AI proposal did not contain any graph edits.")
        val prepared = proposalPlanner.prepare(load(projectId), temporary.entries.map(StoredEntry::staged))
        temporary.preparedProposal = prepared
        temporary.proposal = prepared.proposal
        return response(projectId, temporary, "Deterministic validation completed for the private AI proposal.")
    }

    @Synchronized
    public fun discard(projectId: String, stagedId: String): WebStagingResponse {
        val session = session(projectId)
        if (session.entries.removeIf { it.staged.id == stagedId }.not()) {
            throw WebWorkflowFailure("unknown-staged-change", "Staged change '$stagedId' was not found.")
        }
        session.clearPreparedProposal()
        return response(projectId, session)
    }

    @Synchronized
    public fun preview(projectId: String, userId: String): WebStagingResponse {
        val session = session(projectId)
        val project = load(projectId)
        val prepared = proposalPlanner.prepare(project, session.entries.map(StoredEntry::staged))
        session.preparedProposal = prepared
        session.proposal = prepared.proposal
        if (prepared.proposal.validationReport?.ok != true) {
            return response(projectId, session, "Proposal preview is invalid and cannot be approved until its findings are resolved.")
        }
        return response(projectId, session, "Proposal prepared by $userId and is ready for review.")
    }

    @Synchronized
    public fun approve(projectId: String, userId: String): WebStagingResponse {
        val session = session(projectId)
        val proposal = session.proposal ?: throw WebWorkflowFailure("missing-proposal", "Preview the staged changes before approval.")
        if (proposal.validationReport?.ok != true || proposal.status != ChangeProposalStatus.ReadyForReview) {
            throw WebWorkflowFailure("proposal-not-approvable", "Only a current, valid proposal can be approved.")
        }
        val currentProject = load(projectId)
        val isCurrent = when (val result = proposalCreator.isCurrent(proposal, currentProject)) {
            is EntioResult.Failure -> throw WebWorkflowFailure("proposal-current-check-failed", result.message)
            is EntioResult.Success -> result.value
        }
        if (!isCurrent) throw WebWorkflowFailure("stale-proposal-baseline", "The proposal baseline no longer matches the current project.")
        session.proposal = proposal.copy(status = ChangeProposalStatus.Approved, review = com.entio.core.ProposalReview(userId, "Approved through the web workbench."))
        return response(projectId, session, "Proposal approved by $userId.")
    }

    @Synchronized
    public fun reject(projectId: String, userId: String): WebStagingResponse {
        val session = session(projectId)
        if (session.proposal == null) throw WebWorkflowFailure("missing-proposal", "There is no proposal to reject.")
        session.entries.clear()
        session.clearPreparedProposal()
        return response(projectId, session, "Proposal rejected by $userId; its staged changes were removed from the review queue.")
    }

    @Synchronized
    public fun apply(projectId: String, userId: String): WebStagingResponse {
        val session = session(projectId)
        val proposal = session.proposal ?: throw WebWorkflowFailure("missing-proposal", "There is no proposal to apply.")
        if (proposal.status != ChangeProposalStatus.Approved) throw WebWorkflowFailure("proposal-not-approved", "Only an approved proposal can be applied.")
        val prepared = session.preparedProposal
            ?: throw WebWorkflowFailure("missing-prepared-application", "Preview the staged changes again before applying.")
        val documentEntries = session.entries.map(StoredEntry::staged).filter { it.documentDraftProvenance != null }
        if (documentEntries.isNotEmpty()) {
            val hooks = documentApplyHooks
                ?: throw WebWorkflowFailure("document-provenance-unavailable", "Document provenance is unavailable.")
            hooks.begin(
                projectId = projectId,
                proposalId = proposal.id,
                baselineFingerprint = webGraphFingerprint(load(projectId).graph),
                expectedFingerprint = webGraphFingerprint(
                    proposal.preview?.graph
                        ?: throw WebWorkflowFailure("missing-proposal-preview", "The approved proposal preview is unavailable."),
                ),
                staged = documentEntries,
                appliedByUserId = userId,
            )
        }
        val result = MultiSourceAtomicApplier(
            postSaveVerification = {
                val reloaded = try {
                    load(projectId)
                } catch (failure: WebWorkflowFailure) {
                    return@MultiSourceAtomicApplier EntioResult.Failure(failure.message ?: "The applied project could not be reloaded.")
                }
                when (val verification = proposalPlanner.verifyAppliedProject(reloaded, prepared.expectedShaclResults)) {
                    is EntioResult.Failure -> verification
                    is EntioResult.Success -> when (val additional = additionalPostApplyVerification(projectId, reloaded)) {
                        is EntioResult.Failure -> additional
                        is EntioResult.Success -> try {
                            if (documentEntries.isNotEmpty()) {
                                documentApplyHooks?.commit(projectId)
                            }
                            EntioResult.Success(Unit)
                        } catch (failure: Exception) {
                            EntioResult.Failure(failure.message ?: "Document provenance could not be committed.")
                        }
                    }
                }
            },
        ).apply(prepared.targets)
        return when (result.status) {
            MultiSourceApplyStatus.Applied -> {
                session.entries.clear()
                session.proposal = proposal.copy(status = ChangeProposalStatus.Applied)
                session.preparedProposal = null
                response(projectId, session, "Proposal applied by $userId; source was reloaded.")
            }
            MultiSourceApplyStatus.RolledBack -> {
                if (documentEntries.isNotEmpty()) {
                    documentApplyHooks?.rolledBack(projectId)
                }
                session.proposal = proposal.copy(status = ChangeProposalStatus.RolledBack)
                response(projectId, session, result.reason ?: "Proposal application failed and source files were restored.")
            }
            MultiSourceApplyStatus.RollbackFailed -> {
                session.proposal = proposal.copy(status = ChangeProposalStatus.RolledBack)
                response(projectId, session, result.reason ?: "Proposal application failed and source rollback could not be verified.")
            }
            MultiSourceApplyStatus.Failed -> {
                if (documentEntries.isNotEmpty()) {
                    documentApplyHooks?.rolledBack(projectId)
                }
                session.proposal = proposal.copy(status = ChangeProposalStatus.ApplyFailed)
                response(projectId, session, result.reason ?: "Proposal application failed.")
            }
        }
    }

    private fun session(projectId: String): ProjectSession = sessions.getOrPut(projectId) { ProjectSession() }

    private fun ProjectSession.clearPreparedProposal(): Unit {
        proposal = null
        preparedProposal = null
    }

    private fun load(projectId: String): EntioProject {
        val result = projectLoader.loadProject(projectRegistry.rootFor(projectId))
        return when (result) {
            is EntioResult.Failure -> throw WebWorkflowFailure("project-load-failed", result.message)
            is EntioResult.Success -> result.value
        }
    }

    private fun response(projectId: String, session: ProjectSession, message: String? = null): WebStagingResponse {
        val proposal = session.proposal
        val project = session.entries.takeIf { it.isNotEmpty() }?.let { load(projectId) }
        val graph = project?.graph?.triples ?: emptySet()
        return WebStagingResponse(
            projectId = projectId,
            status = when {
                proposal?.status == ChangeProposalStatus.Applied -> "APPLIED"
                proposal != null -> proposal.status.name.uppercase()
                session.entries.isEmpty() -> StagedChangeSetStatus.Empty.name.uppercase()
                else -> StagedChangeSetStatus.Ready.name.uppercase()
            },
            entries = session.entries.map { entry ->
                WebStagedEntry(
                    id = entry.staged.id,
                    order = entry.staged.order,
                    sourceId = entry.staged.targetSourceId,
                    summary = entry.staged.summary,
                    editType = entry.editType,
                    status = entry.staged.status.name.uppercase(),
                    authorId = entry.authorId,
                    latestEditorId = entry.latestEditorId,
                    comment = entry.comment,
                    normalizedValues = graphChangeNormalizedValues(entry.staged, project, graph) + entry.staged.normalizedValues,
                    generatedIris = entry.staged.generatedIris.map(GeneratedIri::iri).map(Iri::value),
                    validationMessages = entry.staged.validationReport?.issues?.map { it.message }.orEmpty(),
                    materializationProvenance = entry.staged.materializationProvenance?.let { provenance ->
                        WebInferenceMaterializationProvenance(
                            origin = provenance.origin.name,
                            inferenceKind = provenance.inferenceKind.name,
                            reasoningJobId = provenance.reasoningJobId,
                            graphFingerprint = provenance.graphFingerprint,
                            factId = provenance.factId.value,
                            stagedByUserId = provenance.stagedByUserId,
                            stagedAt = provenance.stagedAt.toString(),
                            targetSourceId = provenance.targetSourceId,
                            entailedBeforeAssertion = provenance.entailedBeforeAssertion,
                            importDependence = provenance.importDependence.state.name,
                            importSourceIds = provenance.importDependence.sourceIds,
                        )
                    },
                    documentDraftProvenance = entry.staged.documentDraftProvenance?.let { provenance ->
                        WebDocumentDraftProvenance(
                            taskId = provenance.taskId.value,
                            recommendationId = provenance.recommendationId,
                            decisionId = provenance.decisionId,
                            evidenceIds = provenance.evidenceIds.map { it.value },
                            modelId = provenance.modelId,
                            promptVersion = provenance.promptVersion,
                            extractionMethods = provenance.extractionMethods.map { it.name },
                            confidence = provenance.confidence,
                            targetSourceId = provenance.targetSourceId,
                            normalizedTypedOperationKey = provenance.normalizedTypedOperationKey,
                        )
                    },
                )
            },
            proposal = proposal?.let { current ->
                WebProposalState(
                    id = current.id,
                    status = current.status.name.uppercase(),
                    stagedChangeIds = session.entries.map { it.staged.id },
                    baselineProjectFingerprint = current.baseline.projectFingerprint,
                    validationMessages = current.validationReport?.issues?.map { it.message }.orEmpty(),
                    diff = current.diff?.entries?.map { entry -> WebDiffEntry(entry.kind.name, entry.subject.value, entry.predicate?.value, entry.objectValue, entry.description) }.orEmpty(),
                    targetSourceIds = session.preparedProposal?.targets?.map { it.sourceId }.orEmpty(),
                    shaclImpact = session.preparedProposal?.toWebShaclImpact(),
                    message = message,
                    validationIssues = session.preparedProposal?.stagingIntegrityIssues?.map { issue ->
                        WebProposalValidationIssue(
                            code = issue.code,
                            message = issue.message,
                            stagedChangeId = issue.stagedChangeId,
                            remediations = issue.remediations.map { remediation ->
                                WebProposalRemediation(
                                    action = remediation.action,
                                    label = remediation.label,
                                    stagedChangeIds = remediation.stagedChangeIds,
                                )
                            },
                        )
                    }.orEmpty(),
                )
            },
        )
    }

    private fun graphChangeNormalizedValues(staged: StagedChange, project: EntioProject?, graph: Set<GraphTriple>): Map<String, String> {
        val changes = materializedGraphChanges(staged, project)
        if (changes.isEmpty()) return emptyMap()
        if (changes.size != 1) {
            return mapOf("tripleSummary" to changes.joinToString("; ") { formatDisplayTriple(it.triple, graph) })
        }
        val change = changes.single()
        val triple = change.triple
        fun label(resource: RdfResource): String = graph.asSequence()
            .filter { it.subjectResource.value == resource.value && it.predicate.value in PREFERRED_LABEL_PREDICATES }
            .mapNotNull { (it.objectTerm as? RdfLiteral)?.lexicalForm?.takeIf(String::isNotBlank) }
            .firstOrNull()
            ?: resource.value.substringAfterLast('#').substringAfterLast('/').replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").replace('_', ' ').replace('-', ' ')

        return buildMap {
            put("operation", if (change.kind == GraphChangeKind.Addition) "add" else "remove")
            put("subjectIri", triple.subjectResource.value)
            put("subjectLabel", label(triple.subjectResource))
            put("predicateIri", triple.predicate.value)
            put("predicateLabel", label(triple.predicate))
            when (val objectTerm = triple.objectTerm) {
                is RdfResource -> {
                    put("objectIri", objectTerm.value)
                    put("objectLabel", label(objectTerm))
                }
                is RdfLiteral -> put("objectValue", objectTerm.lexicalForm)
            }
        }
    }

    private fun materializedGraphChanges(staged: StagedChange, project: EntioProject?): List<GraphChange> = when (val operation = staged.operation) {
        is StagedChangeOperation.GraphChanges -> operation.changeSet.changes
        is StagedChangeOperation.TypedEdit -> when (val translated = editTranslator.translate(operation.edit)) {
            is EntioResult.Success -> translated.value.changes
            is EntioResult.Failure -> emptyList()
        }
        is StagedChangeOperation.ShaclEdit -> {
            val shapesGraph = project?.ontologies?.firstOrNull { it.source.id == staged.targetSourceId }?.graph ?: return emptyList()
            when (val translated = shaclEditTranslator.translate(operation.edit, shapesGraph)) {
                is EntioResult.Success -> translated.value.changes
                is EntioResult.Failure -> emptyList()
            }
        }
        is StagedChangeOperation.Delete -> (operation.plan.directStatements + operation.plan.dependentStatements)
            .distinctBy { it.identityKey }
            .map { GraphChange(GraphChangeKind.Removal, it.statement) }
    }

    private fun validatePreparedMaterialization(
        project: EntioProject,
        item: PreparedInferenceMaterialization,
    ): Unit {
        if (item.triple in project.graph.triples) {
            throw WebWorkflowFailure("inference-already-asserted", "A selected inferred fact is already asserted.")
        }
        val source = project.resolvedSources.firstOrNull { it.id == item.targetSourceId }
            ?: throw WebWorkflowFailure("unknown-source", "A selected target source was not found.")
        if (com.entio.core.ShaclGraphRole.Ontology !in source.roles) {
            throw WebWorkflowFailure("invalid-ontology-source-role", "Materialized assertions require a source with the ontology role.")
        }
        val translated = when (val result = editTranslator.translate(item.edit)) {
            is EntioResult.Failure -> throw WebWorkflowFailure("inference-edit-invalid", "A selected inferred fact cannot use its typed edit.")
            is EntioResult.Success -> result.value
        }
        if (translated.changes.size != 1 ||
            translated.changes.single().kind != GraphChangeKind.Addition ||
            translated.changes.single().triple != item.triple
        ) {
            throw WebWorkflowFailure("inference-edit-mismatch", "A selected inferred fact does not match its typed edit.")
        }
    }

    private fun materializationSummary(item: PreparedInferenceMaterialization): String = when (item.fact.kind) {
        com.entio.core.InferenceMaterializationKind.SubclassRelationship -> "Assert inferred superclass relationship"
        com.entio.core.InferenceMaterializationKind.IndividualType -> "Assert inferred individual type"
        com.entio.core.InferenceMaterializationKind.ObjectPropertyAssertion -> "Assert inferred object-property relationship"
    }

    private fun materializationEditType(item: PreparedInferenceMaterialization): String = when (item.fact.kind) {
        com.entio.core.InferenceMaterializationKind.SubclassRelationship -> "add-superclass"
        com.entio.core.InferenceMaterializationKind.IndividualType -> "assign-type"
        com.entio.core.InferenceMaterializationKind.ObjectPropertyAssertion -> "add-object-property-assertion"
    }

    private fun formatDisplayTriple(triple: GraphTriple, graph: Set<GraphTriple>): String {
        fun label(resource: RdfResource): String = graph.asSequence()
            .filter { it.subjectResource.value == resource.value && it.predicate.value in PREFERRED_LABEL_PREDICATES }
            .mapNotNull { (it.objectTerm as? RdfLiteral)?.lexicalForm?.takeIf(String::isNotBlank) }
            .firstOrNull()
            ?: resource.value.substringAfterLast('#').substringAfterLast('/').replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").replace('_', ' ').replace('-', ' ')
        val subject = label(triple.subjectResource)
        val predicate = label(triple.predicate)
        val objectValue = when (val term = triple.objectTerm) {
            is RdfResource -> label(term)
            is RdfLiteral -> term.lexicalForm
        }
        return "$subject — $predicate — $objectValue"
    }

    private fun prepareChange(project: EntioProject, request: WebStageChangeRequest): PreparedChange {
        val requestedSource = project.resolvedSources.firstOrNull { it.id == request.sourceId }
            ?: throw WebWorkflowFailure("unknown-source", "Ontology source '${request.sourceId}' was not found.")
        if (!request.isShaclEdit() && com.entio.core.ShaclGraphRole.Ontology !in requestedSource.roles) {
            throw WebWorkflowFailure("invalid-ontology-source-role", "Ontology edits require a source with the ontology role.")
        }
        if (request.isShaclEdit()) {
            val prepared = shaclStagePreparer.prepare(project, request)
            return PreparedChange(
                summary = prepared.summary,
                operation = StagedChangeOperation.ShaclEdit(prepared.edit),
                normalizedValues = prepared.normalizedValues,
                resolvedCandidates = prepared.resolvedCandidates,
                generatedIris = prepared.generatedIris,
            )
        }
        val prepared = request.prepare(project)
        return PreparedChange(
            summary = prepared.request.summary(),
            operation = prepared.request.toOperation(project),
            normalizedValues = prepared.request.normalizedValues(),
            resolvedCandidates = prepared.resolvedCandidates,
            generatedIris = prepared.generatedIris,
        )
    }

    private fun PreparedWebProposal.toWebShaclImpact(): WebShaclImpact = WebShaclImpact(
        currentGraphFingerprint = currentShaclFingerprint,
        previewGraphFingerprint = previewShaclFingerprint,
        newFindings = impact.shaclImpact.newResults.map(::webFinding),
        worsenedFindings = impact.shaclImpact.worsenedResults.map(::webFinding),
        unchangedFindings = impact.shaclImpact.unchangedResults.map(::webFinding),
        resolvedFindings = impact.shaclImpact.resolvedResults.map(::webFinding),
    )

    private fun webFinding(result: com.entio.core.ShaclValidationResult): WebShaclFindingSummary = WebShaclFindingSummary(
        resultId = result.resultId,
        severity = result.severity.name,
        message = result.message,
        focusNode = result.focusNode.value,
        path = (result.path as? ShaclPath.DirectProperty)?.propertyIri?.value,
        shapeIri = result.shape.iri.value,
    )

    private fun webDeletionDependency(
        dependency: com.entio.core.DeletionDependency,
        labels: Map<String, String>,
    ): WebDeletionDependency {
        val objectValue = when (val term = dependency.statement.objectTerm) {
            is RdfResource -> term.value
            is RdfLiteral -> buildString {
                append('"').append(term.lexicalForm).append('"')
                term.languageTag?.let { append('@').append(it) }
                term.datatypeIri?.let { append("^^").append(it.value) }
            }
        }
        return WebDeletionDependency(
            key = dependency.identityKey,
            kind = dependency.kind.name,
            subject = dependency.statement.subjectResource.value,
            subjectLabel = deletionTermLabel(dependency.statement.subjectResource.value, labels),
            predicate = dependency.statement.predicate.value,
            predicateLabel = deletionTermLabel(dependency.statement.predicate.value, labels),
            objectValue = objectValue,
            objectLabel = (dependency.statement.objectTerm as? RdfResource)
                ?.let { deletionTermLabel(it.value, labels) }
                ?: (dependency.statement.objectTerm as RdfLiteral).lexicalForm,
        )
    }

    private fun deletionTermLabel(value: String, labels: Map<String, String>): String =
        labels[value] ?: DELETION_TERM_LABELS[value] ?: value.substringAfterLast('#').substringAfterLast('/')

    private fun WebStageChangeRequest.toOperation(project: EntioProject): StagedChangeOperation {
        if (editType == "delete") {
            val target = project.symbols.firstOrNull { symbol -> targetIri == symbol.iri.value || (targetIri == null && targetLabel == symbol.label) }
                ?: throw WebWorkflowFailure("unknown-delete-target", "The deletion target does not exist.")
            val ontology = project.ontologies.firstOrNull { it.source.id == sourceId }
                ?: throw WebWorkflowFailure("unknown-source", "Ontology source '$sourceId' was not found.")
            val candidate = EntityCandidate(target.iri, target.label, target.kind, target.sourceId)
            val plan = deletionAnalyzer.analyze(ontology, candidate, selectedDependencyKeys = dependencyKeys)
            return StagedChangeOperation.Delete(plan)
        }
        if (isSemanticMetadataEdit()) {
            return when (val translated = editTranslator.translate(toSemanticEdit())) {
                is EntioResult.Failure -> throw WebWorkflowFailure(
                    translated.issues.firstOrNull()?.code ?: "semantic-edit-invalid",
                    translated.message,
                )
                is EntioResult.Success -> StagedChangeOperation.GraphChanges(translated.value)
            }
        }
        return StagedChangeOperation.TypedEdit(toTypedEdit())
    }

    private fun WebStageChangeRequest.prepare(project: EntioProject): PreparedStageRequest {
        val resolved = mutableListOf<EntityCandidate>()
        val generated = mutableListOf<GeneratedIri>()

        fun existing(
            iri: String?,
            label: String?,
            kind: SymbolKind?,
            fieldName: String,
        ): String {
            iri?.takeIf(String::isNotBlank)?.let { return it }
            val selectorLabel = label?.takeIf(String::isNotBlank)
                ?: throw WebWorkflowFailure("missing-field", "A $fieldName label is required.")
            return when (val result = labelResolver.resolve(project.symbols, EntitySelector(label = selectorLabel, kind = kind))) {
                is EntityResolutionResult.Resolved -> {
                    resolved += result.candidate
                    result.candidate.iri.value
                }
                is EntityResolutionResult.Ambiguous -> throw WebWorkflowFailure(
                    "ambiguous-label",
                    "The $fieldName label '$selectorLabel' matches more than one entity.",
                )
                EntityResolutionResult.NotFound -> throw WebWorkflowFailure(
                    "unknown-label",
                    "The $fieldName label '$selectorLabel' does not exist.",
                )
                is EntityResolutionResult.Invalid -> throw WebWorkflowFailure("invalid-label", result.reason)
            }
        }

        fun newEntity(
            iri: String?,
            label: String?,
            kind: SymbolKind,
            fieldName: String,
        ): String {
            iri?.takeIf(String::isNotBlank)?.let { return it }
            val entityLabel = label?.takeIf(String::isNotBlank)
                ?: throw WebWorkflowFailure("missing-field", "A $fieldName label is required.")
            return when (val result = iriGenerator.generate(entityLabel, kind, project.config.iriNamespace, project.symbols)) {
                is EntioResult.Failure -> throw WebWorkflowFailure(
                    result.issues.firstOrNull()?.code ?: "iri-generation-failed",
                    result.message,
                )
                is EntioResult.Success -> {
                    generated += result.value
                    result.value.iri.value
                }
            }
        }

        fun range(): String {
            rangeIri?.takeIf(String::isNotBlank)?.let { return it }
            val value = rangeLabel?.takeIf(String::isNotBlank)
                ?: throw WebWorkflowFailure("missing-field", "A range label is required.")
            standardDatatype(value)?.let { return it }
            return existing(null, value, null, "range")
        }

        val prepared = when (editType) {
            "create-class" -> copy(classIri = newEntity(classIri, label, SymbolKind.Class, "class"))
            "set-entity-label" -> copy(resourceIri = existing(resourceIri ?: targetIri, resourceLabel ?: targetLabel, null, "entity"))
            "add-definition", "replace-definition", "remove-definition",
            "add-alternate-label", "replace-alternate-label", "remove-alternate-label",
            -> copy(targetIri = existing(targetIri, targetLabel, null, "target"))
            "add-superclass", "remove-superclass" -> copy(
                classIri = existing(classIri, classLabel, SymbolKind.Class, "class"),
                superclassIri = existing(superclassIri, superclassLabel, SymbolKind.Class, "superclass"),
            )
            "create-object-property", "create-datatype-property" -> copy(
                propertyIri = newEntity(propertyIri, label, SymbolKind.Property, "property"),
            )
            "set-property-domain", "remove-property-domain" -> copy(
                propertyIri = existing(propertyIri, propertyLabel, SymbolKind.Property, "property"),
                domainClassIri = existing(domainClassIri, domainClassLabel, SymbolKind.Class, "domain class"),
            )
            "set-property-range", "remove-property-range" -> copy(
                propertyIri = existing(propertyIri, propertyLabel, SymbolKind.Property, "property"),
                rangeIri = range(),
            )
            "create-individual" -> copy(
                individualIri = newEntity(individualIri, label ?: individualLabel, SymbolKind.Individual, "individual"),
                classIri = existing(classIri, classLabel, SymbolKind.Class, "class"),
            )
            "assign-type" -> copy(
                resourceIri = existing(resourceIri, resourceLabel, null, "resource"),
                typeIri = existing(typeIri, typeLabel, SymbolKind.Class, "type"),
            )
            "add-object-property-assertion" -> copy(
                subjectIri = existing(subjectIri, subjectLabel, null, "subject"),
                propertyIri = existing(propertyIri, propertyLabel, SymbolKind.Property, "property"),
                objectIri = existing(objectIri, objectLabel, null, "object"),
            )
            "add-datatype-property-assertion" -> copy(
                subjectIri = existing(subjectIri, subjectLabel, null, "subject"),
                propertyIri = existing(propertyIri, propertyLabel, SymbolKind.Property, "property"),
            )
            else -> this
        }
        return PreparedStageRequest(prepared, resolved, generated)
    }

    private fun WebStageChangeRequest.toTypedEdit(): TypedOntologyEdit = when (editType) {
        "create-class" -> CreateClassEdit(requiredIri(classIri, "classIri"), label?.let { literal(it) })
        "set-entity-label" -> SetEntityLabelEdit(requiredResource(resourceIri ?: targetIri, "resourceIri"), requiredLiteral(label, "label"))
        "add-superclass" -> AddSuperclassEdit(requiredIri(classIri, "classIri"), requiredIri(superclassIri, "superclassIri"))
        "remove-superclass" -> com.entio.core.RemoveSuperclassEdit(requiredIri(classIri, "classIri"), requiredIri(superclassIri, "superclassIri"))
        "create-object-property" -> CreateObjectPropertyEdit(requiredIri(propertyIri, "propertyIri"), label?.let { literal(it) })
        "create-datatype-property" -> CreateDatatypePropertyEdit(requiredIri(propertyIri, "propertyIri"), label?.let { literal(it) })
        "set-property-domain" -> SetPropertyDomainEdit(requiredIri(propertyIri, "propertyIri"), requiredIri(domainClassIri, "domainClassIri"))
        "set-property-range" -> SetPropertyRangeEdit(requiredIri(propertyIri, "propertyIri"), requiredIri(rangeIri, "rangeIri"))
        "remove-property-domain" -> RemovePropertyDomainEdit(requiredIri(propertyIri, "propertyIri"), requiredIri(domainClassIri, "domainClassIri"))
        "remove-property-range" -> RemovePropertyRangeEdit(requiredIri(propertyIri, "propertyIri"), requiredIri(rangeIri, "rangeIri"))
        "create-individual" -> CreateIndividualEdit(requiredIri(individualIri, "individualIri"), classIri?.let(::Iri))
        "assign-type" -> AssignTypeEdit(requiredResource(resourceIri, "resourceIri"), requiredIri(typeIri, "typeIri"))
        "add-object-property-assertion" -> AddObjectPropertyAssertionEdit(requiredResource(subjectIri, "subjectIri"), requiredIri(propertyIri, "propertyIri"), requiredResource(objectIri, "objectIri"))
        "add-datatype-property-assertion" -> AddDatatypePropertyAssertionEdit(requiredResource(subjectIri, "subjectIri"), requiredIri(propertyIri, "propertyIri"), requiredLiteral(value, "value"))
        else -> throw WebWorkflowFailure("unsupported-edit-type", "Edit type '$editType' is not supported by the web boundary.")
    }

    private fun WebStageChangeRequest.toSemanticEdit(): SemanticEditRequest = when (editType) {
        "add-definition" -> SemanticEditRequest.AddDefinition(requiredResource(targetIri, "targetIri"), requiredLiteral(value, "value"), sourceId)
        "replace-definition" -> SemanticEditRequest.ReplaceDefinition(
            requiredResource(targetIri, "targetIri"),
            requiredLiteral(existingValue, "existingValue"),
            requiredLiteral(value, "value"),
            sourceId,
        )
        "remove-definition" -> SemanticEditRequest.RemoveDefinition(requiredResource(targetIri, "targetIri"), requiredLiteral(value, "value"), sourceId)
        "add-alternate-label" -> SemanticEditRequest.AddAlternateLabel(requiredResource(targetIri, "targetIri"), requiredLiteral(value, "value"), sourceId)
        "replace-alternate-label" -> SemanticEditRequest.ReplaceAlternateLabel(
            requiredResource(targetIri, "targetIri"),
            requiredLiteral(existingValue, "existingValue"),
            requiredLiteral(value, "value"),
            sourceId,
        )
        "remove-alternate-label" -> SemanticEditRequest.RemoveAlternateLabel(requiredResource(targetIri, "targetIri"), requiredLiteral(value, "value"), sourceId)
        else -> throw WebWorkflowFailure("unsupported-edit-type", "Edit type '$editType' is not a semantic metadata edit.")
    }

    private fun WebStageChangeRequest.isSemanticMetadataEdit(): Boolean = editType in setOf(
        "add-definition",
        "replace-definition",
        "remove-definition",
        "add-alternate-label",
        "replace-alternate-label",
        "remove-alternate-label",
    )

    private fun WebStageChangeRequest.normalizedValues(): Map<String, String> = sequenceOf(
        "classIri" to classIri, "superclassIri" to superclassIri, "propertyIri" to propertyIri,
        "domainClassIri" to domainClassIri, "rangeIri" to rangeIri, "individualIri" to individualIri,
        "resourceIri" to (resourceIri ?: targetIri), "typeIri" to typeIri, "subjectIri" to subjectIri,
        "objectIri" to objectIri, "targetIri" to targetIri, "label" to label, "value" to value,
        "existingValue" to existingValue, "datatypeIri" to datatypeIri,
        "classLabel" to classLabel, "superclassLabel" to superclassLabel, "propertyLabel" to propertyLabel,
        "domainClassLabel" to domainClassLabel, "rangeLabel" to rangeLabel, "individualLabel" to individualLabel,
        "resourceLabel" to resourceLabel, "typeLabel" to typeLabel, "subjectLabel" to subjectLabel,
        "objectLabel" to objectLabel, "targetLabel" to targetLabel,
    ).mapNotNull { (key, current) -> current?.takeIf(String::isNotBlank)?.let { key to it } }.toMap()

    private fun WebStageChangeRequest.summary(): String = "$editType · ${targetLabel ?: label ?: classLabel ?: propertyLabel ?: individualLabel ?: resourceLabel ?: subjectLabel ?: targetIri ?: classIri ?: propertyIri ?: individualIri ?: subjectIri ?: "target"}"

    private fun standardDatatype(label: String): String? = STANDARD_DATATYPES[label.trim().lowercase()]

    private fun requiredIri(value: String?, field: String): Iri = value?.takeIf(String::isNotBlank)?.let(::Iri) ?: throw WebWorkflowFailure("missing-field", "Field '$field' is required.")
    private fun requiredResource(value: String?, field: String): RdfResource = requiredIri(value, field)
    private fun WebStageChangeRequest.requiredLiteral(value: String?, field: String): RdfLiteral =
        value?.takeIf(String::isNotBlank)?.let { literal(it, datatypeIri) }
            ?: throw WebWorkflowFailure("missing-field", "Field '$field' is required.")

    private fun literal(value: String, requestedDatatypeIri: String? = null): RdfLiteral =
        RdfLiteral(value, datatypeIri = Iri(requestedDatatypeIri?.takeIf(String::isNotBlank) ?: XSD_STRING), languageTag = null)

    private companion object {
        private val PREFERRED_LABEL_PREDICATES = setOf(
            "http://www.w3.org/2000/01/rdf-schema#label",
            "http://www.w3.org/2004/02/skos/core#prefLabel",
        )
        private const val XSD_STRING = "http://www.w3.org/2001/XMLSchema#string"
        private val STANDARD_DATATYPES: Map<String, String> = mapOf(
            "string" to "http://www.w3.org/2001/XMLSchema#string",
            "integer" to "http://www.w3.org/2001/XMLSchema#integer",
            "decimal" to "http://www.w3.org/2001/XMLSchema#decimal",
            "boolean" to "http://www.w3.org/2001/XMLSchema#boolean",
            "date" to "http://www.w3.org/2001/XMLSchema#date",
            "datetime" to "http://www.w3.org/2001/XMLSchema#dateTime",
        )
        private val DELETION_TERM_LABELS: Map<String, String> = mapOf(
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type" to "type",
            "http://www.w3.org/2000/01/rdf-schema#label" to "label",
            "http://www.w3.org/2000/01/rdf-schema#subClassOf" to "superclass",
            "http://www.w3.org/2000/01/rdf-schema#domain" to "domain",
            "http://www.w3.org/2000/01/rdf-schema#range" to "range",
            "http://www.w3.org/2002/07/owl#Class" to "Class",
            "http://www.w3.org/2002/07/owl#ObjectProperty" to "Object property",
            "http://www.w3.org/2002/07/owl#DatatypeProperty" to "Datatype property",
            "http://www.w3.org/2002/07/owl#NamedIndividual" to "Individual",
        )
    }
}
