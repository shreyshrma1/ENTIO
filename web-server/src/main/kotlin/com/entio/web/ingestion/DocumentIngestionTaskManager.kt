package com.entio.web.ingestion

import com.entio.core.DocumentProcessingStatus
import com.entio.core.DocumentTaskId
import com.entio.core.IngestionDocument
import java.time.Duration
import java.time.Instant

public data class DocumentIngestionProgress(
    val stage: String,
    val completedDocuments: Int,
    val totalDocuments: Int,
    val percent: Int,
    val message: String,
) {
    init {
        require(completedDocuments in 0..totalDocuments) { "Completed document count is invalid." }
        require(totalDocuments > 0) { "Document progress requires at least one document." }
        require(percent in 0..100) { "Document progress percent must be between 0 and 100." }
        require(message.isNotBlank() && message.length <= 500) { "Document progress message must be safe and bounded." }
    }
}

public data class DocumentIngestionStatusUpdate(
    val order: Int,
    val stage: String,
    val completedDocuments: Int,
    val totalDocuments: Int,
    val percent: Int,
    val message: String,
    val timestamp: String,
)

public data class DocumentIngestionTaskSnapshot(
    val taskId: String,
    val projectId: String,
    val ownerUserId: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val documents: List<DocumentIngestionDocumentSnapshot>,
    val progress: DocumentIngestionProgress,
    val updates: List<DocumentIngestionStatusUpdate> = emptyList(),
)

public data class DocumentIngestionDocumentSnapshot(
    val documentId: String,
    val safeFilename: String,
    val mediaType: String,
    val byteSize: Long,
    val checksumSha256: String,
    val authorityStatus: String,
    val status: String,
)

private data class StoredIngestionTask(
    val id: DocumentTaskId,
    val projectId: String,
    val ownerUserId: String,
    val createdAt: Instant,
    var updatedAt: Instant,
    var status: DocumentProcessingStatus,
    val directory: TemporaryTaskDirectory,
    val documents: MutableList<AcceptedDocumentUpload> = mutableListOf(),
    var progress: DocumentIngestionProgress,
    val updates: MutableList<DocumentIngestionStatusUpdate>,
)

internal data class DocumentIngestionProcessingInput(
    val taskId: DocumentTaskId,
    val projectId: String,
    val ownerUserId: String,
    val directory: TemporaryTaskDirectory,
    val documents: List<AcceptedDocumentUpload>,
)

internal class DocumentIngestionTaskManager(
    private val configuration: DocumentIngestionConfiguration,
    private val storage: DocumentTemporaryStorage,
) : AutoCloseable {
    private val tasks: MutableMap<DocumentTaskId, StoredIngestionTask> = linkedMapOf()

    @Synchronized
    fun begin(projectId: String, userId: String, expectedDocuments: Int): DocumentTaskId {
        expireTasks()
        if (expectedDocuments !in 1..configuration.maximumDocumentsPerTask) {
            throw DocumentIngestionFailure("document-count-limit", "An ingestion task requires between one and ten documents.")
        }
        if (tasks.values.count { it.status.isActiveExecution() } >= configuration.maximumActiveTasks) {
            throw DocumentIngestionFailure("ingestion-concurrency-limit", "The server is already processing the maximum number of ingestion tasks.")
        }
        if (tasks.values.any { it.projectId == projectId && it.status.isActiveExecution() }) {
            throw DocumentIngestionFailure("project-ingestion-in-progress", "This project already has an active ingestion task.")
        }
        val taskId = DocumentTaskId("task-${configuration.idFactory()}")
        val now = Instant.now(configuration.clock)
        val directory = storage.createTask(taskId.value)
        val initialProgress = DocumentIngestionProgress(
            "uploaded",
            0,
            expectedDocuments,
            0,
            "Documents uploaded and awaiting extraction.",
        )
        tasks[taskId] = StoredIngestionTask(
            id = taskId,
            projectId = projectId,
            ownerUserId = userId,
            createdAt = now,
            updatedAt = now,
            status = DocumentProcessingStatus.Uploaded,
            directory = directory,
            progress = initialProgress,
            updates = mutableListOf(initialProgress.toStatusUpdate(1, now)),
        )
        return taskId
    }

    @Synchronized
    fun directory(taskId: DocumentTaskId, projectId: String, userId: String): TemporaryTaskDirectory =
        ownedTask(taskId, projectId, userId).directory

    @Synchronized
    fun addDocument(taskId: DocumentTaskId, projectId: String, userId: String, upload: AcceptedDocumentUpload): Unit {
        val task = ownedTask(taskId, projectId, userId)
        if (task.documents.size >= task.progress.totalDocuments) {
            throw DocumentIngestionFailure("document-count-limit", "The task already contains its declared documents.")
        }
        val duplicate = tasks.values
            .asSequence()
            .filter { it.projectId == projectId && it.id != taskId }
            .filter { it.status !in setOf(DocumentProcessingStatus.Cancelled, DocumentProcessingStatus.Failed) }
            .flatMap { it.documents.asSequence() }
            .firstOrNull { it.document.checksumSha256 == upload.document.checksumSha256 }
        if (duplicate != null) {
            throw DocumentIngestionFailure("duplicate-document", "This project already has an identical uploaded document.")
        }
        if (task.documents.any { it.document.checksumSha256 == upload.document.checksumSha256 }) {
            throw DocumentIngestionFailure("duplicate-document", "The ingestion task contains duplicate document content.")
        }
        task.documents += upload
        task.recordProgress(task.progress.copy(
            completedDocuments = task.documents.size,
            percent = (task.documents.size * 100) / task.progress.totalDocuments,
            message = "Accepted ${task.documents.size} of ${task.progress.totalDocuments} documents.",
        ))
    }

    @Synchronized
    fun completeIntake(taskId: DocumentTaskId, projectId: String, userId: String): DocumentIngestionTaskSnapshot {
        val task = ownedTask(taskId, projectId, userId)
        if (task.documents.size != task.progress.totalDocuments) {
            failAndCleanup(task, "The multipart request did not include every declared document.")
            throw DocumentIngestionFailure("missing-document-part", "The multipart request did not include every declared document.")
        }
        task.status = DocumentProcessingStatus.Extracting
        task.recordProgress(task.progress.copy(
            stage = "extracting",
            completedDocuments = 0,
            percent = 0,
            message = "All documents passed intake validation; extraction started.",
        ))
        return task.snapshot()
    }

    @Synchronized
    fun processingInput(taskId: DocumentTaskId, projectId: String, userId: String): DocumentIngestionProcessingInput {
        val task = ownedTask(taskId, projectId, userId)
        if (task.status != DocumentProcessingStatus.Extracting) {
            throw DocumentIngestionFailure("ingestion-task-state-invalid", "The ingestion task is not ready for extraction.")
        }
        return DocumentIngestionProcessingInput(task.id, task.projectId, task.ownerUserId, task.directory, task.documents.toList())
    }

    @Synchronized
    fun transition(
        taskId: DocumentTaskId,
        projectId: String,
        userId: String,
        status: DocumentProcessingStatus,
        completedDocuments: Int,
        percent: Int,
        message: String,
    ): DocumentIngestionTaskSnapshot {
        val task = ownedTask(taskId, projectId, userId)
        if (task.status == DocumentProcessingStatus.Cancelled) {
            throw DocumentIngestionFailure("ingestion-cancelled", "Document ingestion was cancelled.")
        }
        task.status = status
        task.recordProgress(DocumentIngestionProgress(
            stage = status.name.toKebabCase(),
            completedDocuments = completedDocuments,
            totalDocuments = task.progress.totalDocuments,
            percent = percent,
            message = message.take(500),
        ))
        return task.snapshot()
    }

    @Synchronized
    fun isCancelled(taskId: DocumentTaskId, projectId: String, userId: String): Boolean =
        runCatching { ownedTask(taskId, projectId, userId).status == DocumentProcessingStatus.Cancelled }
            .getOrDefault(true)

    @Synchronized
    fun find(taskId: DocumentTaskId, projectId: String, userId: String): DocumentIngestionTaskSnapshot =
        ownedTask(taskId, projectId, userId).snapshot()

    @Synchronized
    fun list(projectId: String, userId: String): List<DocumentIngestionTaskSnapshot> {
        expireTasks()
        return tasks.values
            .filter { it.projectId == projectId && it.ownerUserId == userId }
            .sortedWith(compareByDescending<StoredIngestionTask>(StoredIngestionTask::createdAt).thenBy { it.id.value })
            .map { it.snapshot() }
    }

    @Synchronized
    fun cancel(taskId: DocumentTaskId, projectId: String, userId: String): DocumentIngestionTaskSnapshot {
        val task = ownedTask(taskId, projectId, userId)
        if (task.status !in terminalStates) {
            task.status = DocumentProcessingStatus.Cancelled
            task.recordProgress(task.progress.copy(stage = "cancelled", message = "Document ingestion was cancelled."))
            storage.deleteTask(task.directory)
        }
        return task.snapshot()
    }

    @Synchronized
    fun delete(taskId: DocumentTaskId, projectId: String, userId: String): Unit {
        val task = ownedTask(taskId, projectId, userId)
        if (storage.taskExists(task.directory)) storage.deleteTask(task.directory)
        tasks.remove(taskId)
    }

    @Synchronized
    fun fail(taskId: DocumentTaskId, projectId: String, userId: String, message: String): Unit {
        val task = ownedTask(taskId, projectId, userId)
        failAndCleanup(task, message)
    }

    @Synchronized
    override fun close(): Unit {
        tasks.values.forEach { task ->
            if (storage.taskExists(task.directory)) runCatching { storage.deleteTask(task.directory) }
        }
        tasks.clear()
    }

    private fun ownedTask(taskId: DocumentTaskId, projectId: String, userId: String): StoredIngestionTask {
        expireTasks()
        return tasks[taskId]
            ?.takeIf { it.projectId == projectId && it.ownerUserId == userId }
            ?: throw DocumentIngestionFailure("ingestion-task-not-found", "The requested ingestion task was not found.")
    }

    private fun expireTasks(): Unit {
        val now = Instant.now(configuration.clock)
        tasks.values
            .filter {
                Duration.between(it.createdAt, now) >= configuration.taskLifetime &&
                    it.progress.stage != "expired"
            }
            .forEach { task ->
                if (storage.taskExists(task.directory)) runCatching { storage.deleteTask(task.directory) }
                task.status = DocumentProcessingStatus.Cancelled
                task.recordProgress(
                    task.progress.copy(stage = "expired", message = "The temporary ingestion task expired."),
                    now,
                )
            }
    }

    private fun failAndCleanup(task: StoredIngestionTask, message: String): Unit {
        task.status = DocumentProcessingStatus.Failed
        task.recordProgress(task.progress.copy(stage = "failed", message = message.take(500)))
        if (storage.taskExists(task.directory)) runCatching { storage.deleteTask(task.directory) }
    }

    private fun StoredIngestionTask.recordProgress(
        nextProgress: DocumentIngestionProgress,
        now: Instant = Instant.now(configuration.clock),
    ): Unit {
        updatedAt = now
        progress = nextProgress
        updates += nextProgress.toStatusUpdate((updates.lastOrNull()?.order ?: 0) + 1, now)
        while (updates.size > MAX_STATUS_UPDATES) updates.removeFirst()
    }

    private fun DocumentIngestionProgress.toStatusUpdate(order: Int, timestamp: Instant): DocumentIngestionStatusUpdate =
        DocumentIngestionStatusUpdate(
            order = order,
            stage = stage,
            completedDocuments = completedDocuments,
            totalDocuments = totalDocuments,
            percent = percent,
            message = message,
            timestamp = timestamp.toString(),
        )

    private fun StoredIngestionTask.snapshot(): DocumentIngestionTaskSnapshot = DocumentIngestionTaskSnapshot(
        taskId = id.value,
        projectId = projectId,
        ownerUserId = ownerUserId,
        status = status.name.toKebabCase(),
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
        documents = documents.map { upload ->
            val document = upload.document
            DocumentIngestionDocumentSnapshot(
                documentId = document.id.value,
                safeFilename = document.safeFilename,
                mediaType = document.mediaType.name.toKebabCase(),
                byteSize = document.byteSize,
                checksumSha256 = document.checksumSha256,
                authorityStatus = document.authority.status.name.toKebabCase(),
                status = when (status) {
                    DocumentProcessingStatus.AwaitingReview -> DocumentProcessingStatus.AwaitingReview
                    DocumentProcessingStatus.Completed -> DocumentProcessingStatus.Completed
                    DocumentProcessingStatus.Cancelled -> DocumentProcessingStatus.Cancelled
                    DocumentProcessingStatus.Failed -> DocumentProcessingStatus.Failed
                    else -> document.status
                }.name.toKebabCase(),
            )
        },
        progress = progress,
        updates = updates.toList(),
    )

    private fun DocumentProcessingStatus.isActiveExecution(): Boolean = this in activeExecutionStates

    private fun String.toKebabCase(): String =
        replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").lowercase()

    private companion object {
        const val MAX_STATUS_UPDATES: Int = 50

        val activeExecutionStates: Set<DocumentProcessingStatus> = setOf(
            DocumentProcessingStatus.Uploaded,
            DocumentProcessingStatus.Extracting,
            DocumentProcessingStatus.Analyzing,
            DocumentProcessingStatus.Matching,
            DocumentProcessingStatus.Comparing,
            DocumentProcessingStatus.PreparingRecommendations,
            DocumentProcessingStatus.BuildingDraft,
            DocumentProcessingStatus.Validating,
        )
        val terminalStates: Set<DocumentProcessingStatus> = setOf(
            DocumentProcessingStatus.Completed,
            DocumentProcessingStatus.Cancelled,
            DocumentProcessingStatus.Failed,
        )
    }
}
