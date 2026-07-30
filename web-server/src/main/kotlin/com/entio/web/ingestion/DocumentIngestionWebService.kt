package com.entio.web.ingestion

import com.entio.core.DocumentDraftProvenance
import com.entio.core.DocumentAnalysisPipelineVersions
import com.entio.core.DocumentAnalysisStage
import com.entio.core.DocumentAnalysisStageState
import com.entio.core.DocumentTaskId
import com.entio.semantic.ConnectedDocumentDraftContext
import com.entio.semantic.DocumentDraftOperation
import com.entio.semantic.DocumentDraftTranslationResult
import com.entio.semantic.DocumentRecommendationDraftTranslator
import com.entio.web.PreparedDocumentStagingItem
import com.entio.web.StagingWorkflowService
import com.entio.web.ai.AiCredentialStore
import com.entio.web.ai.models.AiUserProviderSettingsStore
import com.entio.web.contract.ProjectRegistry
import com.entio.web.contract.WebPage
import com.entio.web.contract.WebPageRequest
import com.entio.web.contract.WebStagingResponse
import com.entio.web.contract.toWebPage
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart

internal data class DocumentMultipartMetadata(
    val documents: List<DocumentUploadMetadata>,
)

public class DocumentIngestionWebService(
    private val projectRegistry: ProjectRegistry,
    private val staging: StagingWorkflowService? = null,
    configuration: DocumentIngestionConfiguration = DocumentIngestionConfiguration(),
    private val objectMapper: ObjectMapper = ObjectMapper().findAndRegisterModules(),
) : AutoCloseable {
    private val configuration: DocumentIngestionConfiguration = configuration.also(::validateStorageSeparation)
    private val storage = DocumentTemporaryStorage(configuration.temporaryRoot)
    private val intake = DocumentIntakeService(configuration, storage)
    private val tasks = DocumentIngestionTaskManager(configuration, storage)
    private val reviews = DocumentReviewWorkspaceStore(configuration.clock)
    private val completedIntakeKeys: MutableMap<String, DocumentIngestionTaskSnapshot> = linkedMapOf()
    private val draftTranslator = DocumentRecommendationDraftTranslator()
    private var orchestrator: DocumentIngestionOrchestrator? = null
    private var modelSettings: AiUserProviderSettingsStore? = null
    public val provenanceRepository: AppliedDocumentProvenanceRepository =
        AppliedDocumentProvenanceRepository(configuration.provenanceRoot, projectRegistry)
    internal val provenanceCoordinator: DocumentApplyProvenanceCoordinator =
        DocumentApplyProvenanceCoordinator(provenanceRepository, configuration.clock)

    public suspend fun intake(
        projectId: String,
        userId: String,
        idempotencyKey: String,
        multipart: MultiPartData,
    ): DocumentIngestionTaskSnapshot {
        requireProject(projectId)
        val key = "$projectId\u0000$userId\u0000${validateIdempotencyKey(idempotencyKey)}"
        synchronized(completedIntakeKeys) {
            completedIntakeKeys[key]?.let { return it }
        }
        var requestMetadata: DocumentMultipartMetadata? = null
        var taskId: DocumentTaskId? = null
        val receivedClientIds = linkedSetOf<String>()
        try {
            multipart.forEachPart { part ->
                try {
                    when (part) {
                        is PartData.FormItem -> {
                            if (part.name != "metadata" || requestMetadata != null || taskId != null) {
                                throw DocumentIngestionFailure("invalid-multipart", "Document metadata must be the first and only form field.")
                            }
                            requestMetadata = parseMetadata(part.value)
                            taskId = tasks.begin(projectId, userId, requestMetadata!!.documents.size)
                        }
                        is PartData.FileItem -> {
                            val metadata = requestMetadata
                                ?: throw DocumentIngestionFailure("invalid-multipart", "Document metadata must precede file parts.")
                            val currentTaskId = taskId
                                ?: throw DocumentIngestionFailure("invalid-multipart", "The ingestion task was not initialized.")
                            val clientDocumentId = part.name
                                ?.removePrefix(FILE_PART_PREFIX)
                                ?.takeIf { part.name == "$FILE_PART_PREFIX$it" }
                                ?: throw DocumentIngestionFailure("invalid-multipart", "Every file part requires a document client ID.")
                            val documentMetadata = metadata.documents.singleOrNull { it.clientDocumentId == clientDocumentId }
                                ?: throw DocumentIngestionFailure("invalid-multipart", "A file part did not match declared document metadata.")
                            if (!receivedClientIds.add(clientDocumentId)) {
                                throw DocumentIngestionFailure("duplicate-document-part", "A document file part was repeated.")
                            }
                            if (part.originalFileName != documentMetadata.filename ||
                                part.contentType?.toString() != documentMetadata.declaredMediaType
                            ) {
                                throw DocumentIngestionFailure(
                                    "document-type-mismatch",
                                    "The file part does not match its safe filename and declared media type.",
                                )
                            }
                            val upload = intake.accept(
                                taskId = currentTaskId,
                                taskDirectory = tasks.directory(currentTaskId, projectId, userId),
                                projectId = projectId,
                                userId = userId,
                                metadata = documentMetadata,
                                channel = part.provider(),
                            )
                            tasks.addDocument(currentTaskId, projectId, userId, upload)
                        }
                        else -> throw DocumentIngestionFailure("invalid-multipart", "The multipart request contains an unsupported part.")
                    }
                } finally {
                    part.dispose()
                }
            }
            val metadata = requestMetadata
                ?: throw DocumentIngestionFailure("missing-document-metadata", "Document metadata is required.")
            val currentTaskId = taskId
                ?: throw DocumentIngestionFailure("missing-document-metadata", "Document metadata is required.")
            if (receivedClientIds != metadata.documents.map(DocumentUploadMetadata::clientDocumentId).toSet()) {
                throw DocumentIngestionFailure("missing-document-part", "The multipart request did not include every declared document.")
            }
            val result = tasks.completeIntake(currentTaskId, projectId, userId)
            orchestrator?.start(currentTaskId.value, projectId, userId)
                ?: throw DocumentIngestionFailure(
                    "document-processing-unavailable",
                    "Document processing is not configured.",
                )
            synchronized(completedIntakeKeys) {
                completedIntakeKeys[key] = result
            }
            return result
        } catch (failure: Exception) {
            taskId?.let { id ->
                runCatching { tasks.fail(id, projectId, userId, "Document intake failed safely.") }
            }
            when (failure) {
                is DocumentIngestionFailure -> throw failure
                else -> throw DocumentIngestionFailure("document-intake-failed", "Document intake failed safely.")
            }
        }
    }

    public fun list(projectId: String, userId: String, page: WebPageRequest): WebPage<DocumentIngestionTaskSnapshot> {
        requireProject(projectId)
        return tasks.list(projectId, userId).toWebPage(page)
    }

    public fun find(projectId: String, taskId: String, userId: String): DocumentIngestionTaskSnapshot {
        requireProject(projectId)
        return tasks.find(DocumentTaskId(taskId), projectId, userId)
    }

    public fun cancel(projectId: String, taskId: String, userId: String): DocumentIngestionTaskSnapshot {
        requireProject(projectId)
        val cancelled = tasks.cancel(DocumentTaskId(taskId), projectId, userId)
        orchestrator?.cancel(taskId)
        return cancelled
    }

    public fun delete(projectId: String, taskId: String, userId: String): Unit {
        requireProject(projectId)
        orchestrator?.cancel(taskId)
        tasks.delete(DocumentTaskId(taskId), projectId, userId)
        reviews.remove(taskId)
    }

    public fun review(
        projectId: String,
        taskId: String,
        userId: String,
        page: WebPageRequest,
    ): DocumentReviewWorkspaceResponse {
        requireProject(projectId)
        tasks.find(DocumentTaskId(taskId), projectId, userId)
        if (runCatching { reviews.verifiedPlan(projectId, taskId, userId) }.isSuccess) {
            return reviews.readVerified(projectId, taskId, userId, page)
        }
        return reviews.read(projectId, taskId, userId, page)
    }

    public fun evidence(
        projectId: String,
        taskId: String,
        evidenceId: String,
        userId: String,
    ): DocumentEvidenceViewResponse {
        requireProject(projectId)
        tasks.find(DocumentTaskId(taskId), projectId, userId)
        if (runCatching { reviews.verifiedPlan(projectId, taskId, userId) }.isSuccess) {
            return reviews.verifiedEvidence(projectId, taskId, userId, evidenceId)
        }
        return reviews.evidence(projectId, taskId, userId, evidenceId)
    }

    public fun decide(
        projectId: String,
        taskId: String,
        recommendationId: String,
        userId: String,
        request: DocumentReviewDecisionRequest,
        page: WebPageRequest,
    ): DocumentReviewWorkspaceResponse {
        requireProject(projectId)
        tasks.find(DocumentTaskId(taskId), projectId, userId)
        if (runCatching { reviews.verifiedPlan(projectId, taskId, userId) }.isSuccess) {
            when (request.action) {
                "accept" -> reviews.acceptVerified(
                    projectId,
                    taskId,
                    recommendationId,
                    userId,
                    request.expectedWorkKey,
                    request.expectedGraphFingerprint,
                    request.clarification,
                )
                "retain" -> {
                    val candidate = reviews.retainVerifiedReviewOnly(
                        projectId,
                        taskId,
                        recommendationId,
                        userId,
                        request.expectedWorkKey,
                        request.expectedGraphFingerprint,
                        request.clarification,
                    )
                    val modelId = candidate.reviewPlan.analysisStages
                        .mapNotNull { it.selectedModelId }
                        .distinct()
                        .singleOrNull()
                        ?: throw DocumentIngestionFailure(
                            "document-model-selection-stale",
                            "The review-only finding no longer has one verified selected model.",
                        )
                    provenanceCoordinator.retainReviewOnly(projectId, candidate, modelId)
                }
                "reject" -> reviews.rejectVerified(
                    projectId,
                    taskId,
                    recommendationId,
                    userId,
                    request.expectedWorkKey,
                    request.expectedGraphFingerprint,
                    request.clarification,
                )
                "confirm-individual" -> reviews.confirmVerifiedIndividual(
                    projectId,
                    taskId,
                    recommendationId,
                    request.operationId ?: throw DocumentIngestionFailure(
                        "document-individual-gate-not-found",
                        "Choose the proposed individual to confirm.",
                    ),
                    userId,
                    request.expectedWorkKey,
                    request.expectedGraphFingerprint,
                    request.confirmProductionClassification,
                )
                "exclude-optional" -> reviews.excludeVerifiedOptionalLeaves(
                    projectId,
                    taskId,
                    recommendationId,
                    request.operationIds.toSet(),
                    userId,
                    request.expectedWorkKey,
                    request.expectedGraphFingerprint,
                )
                "clarify" -> reviews.requestVerifiedReview(
                    projectId,
                    taskId,
                    recommendationId,
                    userId,
                    request.expectedWorkKey,
                    request.expectedGraphFingerprint,
                    com.entio.core.DocumentGroupedDecisionKind.NeedsClarification,
                    request.clarification,
                )
                "reconsider" -> reviews.requestVerifiedReview(
                    projectId,
                    taskId,
                    recommendationId,
                    userId,
                    request.expectedWorkKey,
                    request.expectedGraphFingerprint,
                    com.entio.core.DocumentGroupedDecisionKind.ReconsiderationRequested,
                    request.clarification,
                )
                "split" -> reviews.requestVerifiedReview(
                    projectId,
                    taskId,
                    recommendationId,
                    userId,
                    request.expectedWorkKey,
                    request.expectedGraphFingerprint,
                    com.entio.core.DocumentGroupedDecisionKind.SplitRequested,
                    request.clarification,
                )
                else -> throw DocumentIngestionFailure(
                    "document-review-action-unsupported",
                    "That review action is not supported for connected recommendations.",
                )
            }
            return reviews.readVerified(projectId, taskId, userId, page)
        }
        return reviews.decide(projectId, taskId, recommendationId, userId, request, page)
    }

    internal fun installReview(input: DocumentReviewWorkspaceInput): Unit {
        tasks.find(DocumentTaskId(input.task.taskId), input.task.projectId, input.task.ownerUserId)
        reviews.install(input)
    }

    internal fun installProcessingBoundary(
        credentials: AiCredentialStore,
        settings: AiUserProviderSettingsStore,
        provider: DocumentAnalysisProvider,
    ): Unit {
        check(orchestrator == null) { "Document processing is already configured." }
        modelSettings = settings
        orchestrator = DocumentIngestionOrchestrator(
            tasks,
            reviews,
            configuration,
            projectRegistry,
            provenanceRepository,
            credentials,
            settings,
            provider,
        )
    }

    internal suspend fun awaitProcessing(taskId: String): Unit {
        orchestrator?.await(taskId)
    }

    public fun buildDraft(
        projectId: String,
        taskId: String,
        userId: String,
        idempotencyKey: String,
        request: DocumentDraftBuildRequest,
    ): DocumentDraftBuildResponse {
        requireProject(projectId)
        tasks.find(DocumentTaskId(taskId), projectId, userId)
        val workflow = staging
            ?: throw DocumentIngestionFailure("document-draft-unavailable", "Document draft conversion is unavailable.")
        val verified = runCatching { reviews.verifiedReviewPlan(projectId, taskId, userId) }.getOrNull()
        if (verified != null) {
            return buildConnectedDraft(
                projectId,
                taskId,
                userId,
                idempotencyKey,
                request,
                workflow,
                verified,
            )
        }
        val current = reviews.read(projectId, taskId, userId, WebPageRequest(limit = 1))
        if (current.exactWorkKey != request.expectedWorkKey ||
            current.graphFingerprint != request.expectedGraphFingerprint
        ) {
            throw DocumentIngestionFailure("document-review-stale", "The review workspace changed; reload before drafting.")
        }
        val currentModel = modelSettings?.find(userId)
        val accepted = reviews.accepted(projectId, taskId, userId).map { candidate ->
            candidate.copy(
                context = candidate.context.copy(
                    modelAndPromptCurrent = candidate.recommendation.modelId == null ||
                        currentModel?.selectedModelId == candidate.recommendation.modelId &&
                        currentModel?.selectionStatus == com.entio.web.ai.models.AiModelSelectionStatus.READY,
                ),
            )
        }
        if (accepted.isEmpty()) {
            throw DocumentIngestionFailure("document-draft-empty", "Accept at least one current recommendation before drafting.")
        }
        val translated = accepted.map { candidate ->
            candidate to when (val result = draftTranslator.translateSafely(candidate.recommendation, candidate.context)) {
                is DocumentDraftTranslationResult.Blocked ->
                    throw DocumentIngestionFailure(result.code, result.message)
                is DocumentDraftTranslationResult.Prepared -> result.operations
            }
        }
        provenanceCoordinator.register(projectId, taskId, accepted)
        val schemaRecommendationIds = accepted
            .filter { it.recommendation.category == com.entio.core.DocumentRecommendationCategory.OntologyStructure }
            .mapTo(linkedSetOf()) { it.recommendation.id }
        val editOperations = translated.flatMap { (candidate, operations) ->
            operations.filterNot { it.confirmOnly }.map { prepared ->
                val operation = prepared.operation
                    ?: throw DocumentIngestionFailure("document-draft-operation-missing", "A typed draft operation is missing.")
                val evidenceReferences = candidate.recommendation.evidence.flatMap { it.references }
                val evidenceIds = evidenceReferences.map { it.id }.distinct().sortedBy { it.value }
                val extractionMethods = evidenceReferences.map { it.extractionMethod }.distinct().sortedBy { it.name }
                if (evidenceIds.isEmpty() || extractionMethods.isEmpty()) {
                    throw DocumentIngestionFailure("document-evidence-required", "A typed draft item requires verified document evidence.")
                }
                PreparedDocumentStagingItem(
                    summary = "Document recommendation · ${candidate.recommendation.proposedLabel ?: candidate.recommendation.type.name}",
                    editType = operation.editType(),
                    targetSourceId = prepared.targetSourceId,
                    operation = operation,
                    provenance = DocumentDraftProvenance(
                        taskId = DocumentTaskId(taskId),
                        recommendationId = candidate.recommendation.id,
                        decisionId = candidate.decision.decisionId,
                        evidenceIds = evidenceIds,
                        modelId = candidate.recommendation.modelId,
                        promptVersion = candidate.recommendation.promptVersion,
                        extractionMethods = extractionMethods,
                        confidence = candidate.recommendation.confidence,
                        targetSourceId = prepared.targetSourceId,
                        normalizedTypedOperationKey = prepared.normalizedTypedOperationKey,
                    ),
                )
            }
        }
        if (editOperations.size > com.entio.core.MAX_ACCEPTED_DOCUMENT_EDITS) {
            throw DocumentIngestionFailure("document-draft-task-limit", "A document task cannot stage more than 100 edits.")
        }
        val (schemaOperations, factOperations) = editOperations.partition {
            it.provenance.recommendationId in schemaRecommendationIds
        }
        val orderedBatches = packAtomicDocumentRecommendationGroups(schemaOperations) +
            packAtomicDocumentRecommendationGroups(factOperations)
        if (orderedBatches.size > MAX_DOCUMENT_DRAFT_BATCHES) {
            throw DocumentIngestionFailure(
                "document-draft-batch-count-limit",
                "Schema and fact edits require more than the approved five ordered batches.",
            )
        }
        var response: WebStagingResponse? = null
        orderedBatches.forEachIndexed { index, batch ->
            response = workflow.stageDocumentBatch(
                projectId,
                userId,
                taskId,
                "$idempotencyKey.batch-${index + 1}",
                batch,
            )
        }
        val draftedIds = translated.filter { (_, operations) -> operations.any { !it.confirmOnly } }
            .map { it.first.recommendation.id }
            .toSet()
        if (draftedIds.isNotEmpty()) reviews.markDrafted(projectId, taskId, userId, draftedIds)
        val confirms = translated.count { (_, operations) -> operations.all { it.confirmOnly } }
        if (editOperations.isEmpty()) {
            val graphFingerprint = workflow.graphSnapshot(projectId, com.entio.web.WebJobScope.Applied).graphFingerprint
            if (graphFingerprint != request.expectedGraphFingerprint) {
                throw DocumentIngestionFailure("document-review-stale", "The applied graph changed; reload before confirming.")
            }
            val committed = provenanceCoordinator.commitConfirmations(projectId, taskId, graphFingerprint, userId)
            return DocumentDraftBuildResponse(
                staging = workflow.snapshot(projectId),
                batchCount = 0,
                stagedEditCount = 0,
                confirmCount = committed,
            )
        }
        return DocumentDraftBuildResponse(
            staging = response!!,
            batchCount = orderedBatches.size,
            stagedEditCount = editOperations.size,
            confirmCount = confirms,
        )
    }

    private fun buildConnectedDraft(
        projectId: String,
        taskId: String,
        userId: String,
        idempotencyKey: String,
        request: DocumentDraftBuildRequest,
        workflow: StagingWorkflowService,
        reviewPlan: VerifiedDocumentReviewPlan,
    ): DocumentDraftBuildResponse {
        if (reviewPlan.workKey != request.expectedWorkKey ||
            reviewPlan.graphFingerprint != request.expectedGraphFingerprint
        ) {
            throw DocumentIngestionFailure("document-review-stale", "The review workspace changed; reload before drafting.")
        }
        val stagingSnapshot = workflow.connectedDocumentSnapshot(projectId)
        if (stagingSnapshot.graphFingerprint != reviewPlan.graphFingerprint) {
            throw DocumentIngestionFailure("document-review-stale", "The applied ontology changed; rerun document analysis.")
        }
        val providerStages = reviewPlan.analysisStages.filter { it.stage.providerBacked }
        val modelIds = providerStages.mapNotNull { it.selectedModelId }.distinct()
        val currentModel = modelSettings?.find(userId)
        val modelCurrent = modelIds.size == 1 &&
            currentModel?.selectedModelId == modelIds.single() &&
            currentModel.selectionStatus == com.entio.web.ai.models.AiModelSelectionStatus.READY
        val promptCurrent = providerStages.isNotEmpty() &&
            providerStages.all {
                it.state == DocumentAnalysisStageState.Succeeded &&
                    it.promptVersion == expectedPromptVersion(it.stage)
            }
        if (!modelCurrent || !promptCurrent) {
            throw DocumentIngestionFailure(
                "document-draft-stale",
                "The selected model or document-analysis prompt changed; rerun analysis.",
            )
        }
        val accepted = reviews.acceptedVerified(projectId, taskId, userId)
        if (accepted.isEmpty()) {
            throw DocumentIngestionFailure("document-draft-empty", "Accept at least one executable recommendation before drafting.")
        }
        val existingKeys = stagingSnapshot.normalizedDocumentOperationKeys.toMutableSet()
        val translated = accepted.map { candidate ->
            val result = draftTranslator.translateConnected(
                candidate.recommendation,
                ConnectedDocumentDraftContext(
                    finalIris = reviewPlan.plan.finalIris,
                    writableSourceIds = stagingSnapshot.writableSourceIds,
                    expectedWorkKey = reviewPlan.workKey,
                    currentWorkKey = request.expectedWorkKey,
                    graphCurrent = true,
                    evidenceCurrent = candidate.recommendation.evidenceIds.all { it.value in reviewPlan.evidence },
                    modelAndPromptCurrent = true,
                    existingNormalizedOperationKeys = existingKeys,
                ),
            )
            val operations = when (result) {
                is DocumentDraftTranslationResult.Blocked ->
                    throw DocumentIngestionFailure(result.code, result.message)
                is DocumentDraftTranslationResult.Prepared -> result.operations
            }
            existingKeys += operations.mapNotNull { it.normalizedTypedOperationKey }
            candidate to operations
        }
        val modelId = modelIds.single()
        val items = translated.flatMap { (candidate, operations) ->
            val evidenceGroups = candidate.recommendation.evidenceIds.map { evidenceId ->
                reviewPlan.evidence[evidenceId.value]
                    ?: throw DocumentIngestionFailure("document-evidence-required", "Verified recommendation evidence is stale.")
            }
            val references = evidenceGroups.flatMap { it.references }
            val extractionMethods = references.map { it.extractionMethod }.distinct().sortedBy { it.name }
            if (references.isEmpty() || extractionMethods.isEmpty()) {
                throw DocumentIngestionFailure("document-evidence-required", "A typed draft item requires verified evidence.")
            }
            operations.map { prepared ->
                val operation = prepared.operation
                    ?: throw DocumentIngestionFailure("document-draft-operation-missing", "A typed operation is missing.")
                PreparedDocumentStagingItem(
                    summary = "Document recommendation · ${candidate.recommendation.title}",
                    editType = operation.editType(),
                    targetSourceId = prepared.targetSourceId,
                    operation = operation,
                    provenance = DocumentDraftProvenance(
                        taskId = DocumentTaskId(taskId),
                        recommendationId = candidate.recommendation.id,
                        decisionId = candidate.decision.decisionId,
                        evidenceIds = candidate.recommendation.evidenceIds,
                        modelId = modelId,
                        promptVersion = DocumentAnalysisPipelineVersions.FINAL_PLAN_PROMPT,
                        extractionMethods = extractionMethods,
                        confidence = candidate.recommendation.confidence.overall,
                        targetSourceId = prepared.targetSourceId,
                        normalizedTypedOperationKey = prepared.normalizedTypedOperationKey,
                    ),
                )
            }
        }
        if (items.size > com.entio.core.MAX_ACCEPTED_DOCUMENT_EDITS) {
            throw DocumentIngestionFailure("document-draft-task-limit", "A document task cannot stage more than 100 edits.")
        }
        provenanceCoordinator.registerConnected(
            projectId,
            accepted.map { candidate ->
                ConnectedDocumentProvenanceCandidate(
                    taskId = taskId,
                    recommendation = candidate.recommendation,
                    decision = candidate.decision,
                    documents = reviewPlan.taskDocuments,
                    blocks = reviewPlan.blocks,
                    evidence = reviewPlan.evidence,
                    workKey = reviewPlan.workKey,
                    modelId = modelId,
                    analysisStages = reviewPlan.analysisStages,
                    coverage = reviewPlan.plan.plan.coverage,
                )
            },
        )
        val batches = packAtomicDocumentRecommendationGroups(items)
        if (batches.size > MAX_DOCUMENT_DRAFT_BATCHES) {
            throw DocumentIngestionFailure(
                "document-draft-batch-count-limit",
                "Connected recommendations require more than the approved five batches.",
            )
        }
        var response: WebStagingResponse? = null
        batches.forEachIndexed { index, batch ->
            response = workflow.stageDocumentBatch(
                projectId,
                userId,
                taskId,
                "$idempotencyKey.batch-${index + 1}",
                batch,
            )
        }
        reviews.markVerifiedDrafted(projectId, taskId, userId, accepted.mapTo(linkedSetOf()) { it.recommendation.id })
        return DocumentDraftBuildResponse(
            staging = response ?: workflow.snapshot(projectId),
            batchCount = batches.size,
            stagedEditCount = items.size,
            confirmCount = 0,
        )
    }

    private fun expectedPromptVersion(stage: DocumentAnalysisStage): String? = when (stage) {
        DocumentAnalysisStage.Discovery -> DocumentAnalysisPipelineVersions.DISCOVERY_PROMPT
        DocumentAnalysisStage.ConnectedModeling -> DocumentAnalysisPipelineVersions.CONNECTED_MODEL_PROMPT
        DocumentAnalysisStage.ModelConsolidation -> DocumentAnalysisPipelineVersions.MODEL_CONSOLIDATION_PROMPT
        DocumentAnalysisStage.Reconciliation -> DocumentAnalysisPipelineVersions.RECONCILIATION_PROMPT
        DocumentAnalysisStage.OntologyAlignment -> DocumentAnalysisPipelineVersions.ONTOLOGY_ALIGNMENT_PROMPT
        DocumentAnalysisStage.ModelingCritic -> DocumentAnalysisPipelineVersions.MODELING_CRITIC_PROMPT
        DocumentAnalysisStage.FinalPlanning -> DocumentAnalysisPipelineVersions.SEMANTIC_PLAN_PROMPT
        DocumentAnalysisStage.DeterministicVerification,
        DocumentAnalysisStage.AwaitingReview,
        -> null
    }

    override fun close(): Unit {
        orchestrator?.close()
        tasks.close()
        synchronized(completedIntakeKeys) {
            completedIntakeKeys.clear()
        }
    }

    private fun validateStorageSeparation(configuration: DocumentIngestionConfiguration): Unit {
        val temporaryRoot = configuration.temporaryRoot.toAbsolutePath().normalize()
        projectRegistry.list().forEach { project ->
            val projectRoot = projectRegistry.rootFor(project.id).toAbsolutePath().normalize()
            if (temporaryRoot == projectRoot ||
                temporaryRoot.startsWith(projectRoot) ||
                projectRoot.startsWith(temporaryRoot)
            ) {
                throw DocumentIngestionFailure(
                    "temporary-root-overlaps-project",
                    "The document temporary root must remain separate from ontology projects.",
                )
            }
        }
    }

    private fun validateIdempotencyKey(value: String): String =
        value.takeIf { it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,199}")) }
            ?: throw DocumentIngestionFailure("invalid-idempotency-key", "The Idempotency-Key is invalid.")

    private fun parseMetadata(value: String): DocumentMultipartMetadata {
        if (value.length > MAX_METADATA_CHARACTERS) {
            throw DocumentIngestionFailure("document-metadata-limit", "Document intake metadata exceeds the approved bound.")
        }
        val metadata = runCatching { objectMapper.readValue<DocumentMultipartMetadata>(value) }
            .getOrElse { throw DocumentIngestionFailure("invalid-document-metadata", "Document intake metadata is malformed.") }
        if (metadata.documents.isEmpty() || metadata.documents.size > configuration.maximumDocumentsPerTask) {
            throw DocumentIngestionFailure("document-count-limit", "An ingestion task requires between one and ten documents.")
        }
        val clientIds = metadata.documents.map(DocumentUploadMetadata::clientDocumentId)
        if (clientIds.distinct().size != clientIds.size ||
            clientIds.any { !it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,199}")) }
        ) {
            throw DocumentIngestionFailure("invalid-document-metadata", "Document client IDs must be unique opaque identifiers.")
        }
        if (metadata.documents.map(DocumentUploadMetadata::filename).distinct().size != metadata.documents.size) {
            throw DocumentIngestionFailure("invalid-document-metadata", "Document display filenames must be unique within a task.")
        }
        return metadata
    }

    private fun requireProject(projectId: String): Unit {
        if (projectRegistry.find(projectId) == null) {
            throw DocumentIngestionFailure("ingestion-task-not-found", "The requested ingestion task was not found.")
        }
    }

    private companion object {
        const val FILE_PART_PREFIX: String = "document."
        const val MAX_METADATA_CHARACTERS: Int = 50_000
        const val MAX_DOCUMENT_DRAFT_BATCHES: Int = 5
    }
}

public data class DocumentDraftBuildRequest(
    val expectedWorkKey: String,
    val expectedGraphFingerprint: String,
)

public data class DocumentDraftBuildResponse(
    val apiVersion: String = "v1",
    val staging: WebStagingResponse,
    val batchCount: Int,
    val stagedEditCount: Int,
    val confirmCount: Int,
)

internal fun packAtomicDocumentRecommendationGroups(
    items: List<PreparedDocumentStagingItem>,
): List<List<PreparedDocumentStagingItem>> {
    val batches = mutableListOf<List<PreparedDocumentStagingItem>>()
    var current = mutableListOf<PreparedDocumentStagingItem>()
    items.groupBy { it.provenance.recommendationId }.values.forEach { group ->
        if (group.size > com.entio.core.MAX_DOCUMENT_DRAFT_BATCH_SIZE) {
            throw DocumentIngestionFailure(
                "document-compound-recommendation-limit",
                "One compound recommendation exceeds the approved atomic batch size.",
            )
        }
        if (current.isNotEmpty() &&
            current.size + group.size > com.entio.core.MAX_DOCUMENT_DRAFT_BATCH_SIZE
        ) {
            batches += current
            current = mutableListOf()
        }
        current += group
    }
    if (current.isNotEmpty()) batches += current
    return batches
}

private fun DocumentDraftOperation.editType(): String = when (this) {
    is DocumentDraftOperation.Ontology -> edit::class.simpleName.orEmpty().removeSuffix("Edit").toKebabCase()
    is DocumentDraftOperation.Semantic -> edit.kind.name.toKebabCase()
    is DocumentDraftOperation.Shacl -> edit::class.simpleName.orEmpty().toKebabCase()
    is DocumentDraftOperation.ExternalReuse -> "external-reuse"
}

private fun String.toKebabCase(): String =
    replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").lowercase()
