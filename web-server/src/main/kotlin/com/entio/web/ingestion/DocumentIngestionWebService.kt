package com.entio.web.ingestion

import com.entio.core.DocumentTaskId
import com.entio.web.contract.ProjectRegistry
import com.entio.web.contract.WebPage
import com.entio.web.contract.WebPageRequest
import com.entio.web.contract.toWebPage
import com.entio.web.StagingWorkflowService
import com.entio.web.PreparedDocumentStagingItem
import com.entio.web.contract.WebStagingResponse
import com.entio.semantic.DocumentDraftOperation
import com.entio.semantic.DocumentDraftTranslationResult
import com.entio.semantic.DocumentRecommendationDraftTranslator
import com.entio.core.DocumentDraftProvenance
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
        return tasks.cancel(DocumentTaskId(taskId), projectId, userId)
    }

    public fun delete(projectId: String, taskId: String, userId: String): Unit {
        requireProject(projectId)
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
        return reviews.decide(projectId, taskId, recommendationId, userId, request, page)
    }

    internal fun installReview(input: DocumentReviewWorkspaceInput): Unit {
        tasks.find(DocumentTaskId(input.task.taskId), input.task.projectId, input.task.ownerUserId)
        reviews.install(input)
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
        val current = reviews.read(projectId, taskId, userId, WebPageRequest(limit = 1))
        if (current.exactWorkKey != request.expectedWorkKey ||
            current.graphFingerprint != request.expectedGraphFingerprint
        ) {
            throw DocumentIngestionFailure("document-review-stale", "The review workspace changed; reload before drafting.")
        }
        val accepted = reviews.accepted(projectId, taskId, userId)
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
        val orderedBatches = schemaOperations.chunked(com.entio.core.MAX_DOCUMENT_DRAFT_BATCH_SIZE) +
            factOperations.chunked(com.entio.core.MAX_DOCUMENT_DRAFT_BATCH_SIZE)
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

    override fun close(): Unit {
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

private fun DocumentDraftOperation.editType(): String = when (this) {
    is DocumentDraftOperation.Ontology -> edit::class.simpleName.orEmpty().removeSuffix("Edit").toKebabCase()
    is DocumentDraftOperation.Semantic -> edit.kind.name.toKebabCase()
    is DocumentDraftOperation.Shacl -> edit::class.simpleName.orEmpty().toKebabCase()
    is DocumentDraftOperation.ExternalReuse -> "external-reuse"
}

private fun String.toKebabCase(): String =
    replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").lowercase()
