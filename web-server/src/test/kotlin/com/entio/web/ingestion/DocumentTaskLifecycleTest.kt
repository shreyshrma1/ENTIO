package com.entio.web.ingestion

import com.entio.core.DocumentAuthorityMetadata
import com.entio.core.DocumentAuthorityStatus
import com.entio.core.DocumentId
import com.entio.core.DocumentMediaType
import com.entio.core.DocumentProcessingStatus
import com.entio.core.DocumentTaskId
import com.entio.core.IngestionDocument
import java.nio.file.Files
import java.time.Instant
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentTaskLifecycleTest {
    @Test
    fun enforcesOwnershipCountsDuplicatesCancellationAndDeletion(): Unit {
        val root = Files.createTempDirectory("entio-task-test")
        val storage = DocumentTemporaryStorage(root)
        val ids = sequenceOf("one", "two", "three", "four").iterator()
        val manager = DocumentIngestionTaskManager(
            DocumentIngestionConfiguration(
                temporaryRoot = root,
                provenanceRoot = Files.createTempDirectory("entio-task-provenance"),
                idFactory = ids::next,
            ),
            storage,
        )

        assertCode("document-count-limit") { manager.begin("project-a", "alice", 11) }
        val first = manager.begin("project-a", "alice", 1)
        val firstDirectory = manager.directory(first, "project-a", "alice")
        val firstUpload = upload(first, firstDirectory, "a".repeat(64))
        manager.addDocument(first, "project-a", "alice", firstUpload)
        assertEquals("extracting", manager.completeIntake(first, "project-a", "alice").status)
        manager.transition(
            first,
            "project-a",
            "alice",
            DocumentProcessingStatus.AwaitingReview,
            1,
            100,
            "Ready for review.",
        )

        assertCode("ingestion-task-not-found") { manager.find(first, "project-a", "bob") }
        assertCode("ingestion-task-not-found") { manager.find(first, "project-b", "alice") }

        val second = manager.begin("project-a", "alice", 1)
        val secondDirectory = manager.directory(second, "project-a", "alice")
        assertCode("duplicate-document") {
            manager.addDocument(second, "project-a", "alice", upload(second, secondDirectory, "a".repeat(64)))
        }
        val cancelled = manager.cancel(second, "project-a", "alice")
        assertEquals("cancelled", cancelled.status)
        assertFalse(storage.taskExists(secondDirectory))

        manager.delete(first, "project-a", "alice")
        assertCode("ingestion-task-not-found") { manager.find(first, "project-a", "alice") }
        manager.close()
        assertFalse(Files.list(root).use { paths -> paths.anyMatch { it.fileName.toString().startsWith("task-") } })
    }

    @Test
    fun rejectsSymlinkedTaskStorageAndCleansStaleMarkedDirectoriesOnRestart(): Unit {
        val root = Files.createTempDirectory("entio-storage-test")
        val storage = DocumentTemporaryStorage(root)
        val task = storage.createTask("task-stale")
        val outside = Files.createTempDirectory("entio-storage-outside")
        val link = task.path.resolve("escape")
        runCatching { Files.createSymbolicLink(link, outside) }.getOrElse { return }

        storage.close()

        assertFalse(task.path.exists())
        assertTrue(outside.exists())
        DocumentTemporaryStorage(root).close()
    }

    private fun upload(taskId: DocumentTaskId, directory: TemporaryTaskDirectory, checksum: String): AcceptedDocumentUpload {
        val file = directory.path.resolve("document-${taskId.value}.bin")
        Files.writeString(file, "content")
        return AcceptedDocumentUpload(
            IngestionDocument(
                id = DocumentId("document-${taskId.value}"),
                taskId = taskId,
                safeFilename = "policy.txt",
                mediaType = DocumentMediaType.Text,
                byteSize = 7,
                checksumSha256 = checksum,
                projectId = "project-a",
                uploaderUserId = "alice",
                uploadedAt = Instant.parse("2026-01-01T00:00:00Z"),
                authority = DocumentAuthorityMetadata(DocumentAuthorityStatus.Authoritative),
                status = DocumentProcessingStatus.Uploaded,
            ),
            StoredDocumentFile(file.fileName.toString(), file, 7),
        )
    }

    private fun assertCode(code: String, block: () -> Unit): Unit {
        val failure = assertFailsWith<DocumentIngestionFailure> { block() }
        assertEquals(code, failure.code)
    }
}
