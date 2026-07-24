package com.entio.web.ingestion

import com.entio.core.AppliedDocumentProvenance
import com.entio.web.contract.ProjectRegistry
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest

private const val PROVENANCE_SCHEMA_VERSION: Int = 1

public data class PendingDocumentProvenance(
    val projectId: String,
    val proposalId: String,
    val baselineOntologyFingerprint: String,
    val expectedOntologyFingerprint: String,
    val records: List<AppliedDocumentProvenance>,
)

private data class DocumentProvenanceSnapshot(
    val schemaVersion: Int = PROVENANCE_SCHEMA_VERSION,
    val records: List<AppliedDocumentProvenance> = emptyList(),
)

private data class PendingDocumentProvenanceSnapshot(
    val schemaVersion: Int = PROVENANCE_SCHEMA_VERSION,
    val event: PendingDocumentProvenance,
)

/** Minimal durable workflow provenance store kept separate from ontology sources. */
public class AppliedDocumentProvenanceRepository(
    root: Path,
    private val projectRegistry: ProjectRegistry,
    private val objectMapper: ObjectMapper = ObjectMapper().findAndRegisterModules(),
) {
    private val root: Path = root.toAbsolutePath().normalize()

    init {
        initializeRoot()
        validateRootSeparation()
    }

    @Synchronized
    public fun list(projectId: String): List<AppliedDocumentProvenance> {
        val directory = projectDirectory(projectId, create = false) ?: return emptyList()
        return readSnapshot(directory).records
    }

    @Synchronized
    public fun save(projectId: String, records: List<AppliedDocumentProvenance>): List<AppliedDocumentProvenance> {
        require(records.all { it.projectId == projectId }) { "Applied provenance project IDs must match the repository scope." }
        val directory = projectDirectory(projectId, create = true)!!
        val current = readSnapshot(directory).records
        val merged = (current + records)
            .associateBy(AppliedDocumentProvenance::recordId)
            .values
            .sortedBy(AppliedDocumentProvenance::recordId)
        writeSnapshot(directory, DocumentProvenanceSnapshot(records = merged))
        return merged
    }

    @Synchronized
    public fun beginPending(event: PendingDocumentProvenance): Unit {
        require(event.records.isNotEmpty()) { "Pending document provenance requires records." }
        require(event.records.all { it.projectId == event.projectId }) { "Pending provenance project IDs must match." }
        val directory = projectDirectory(event.projectId, create = true)!!
        val pending = directory.resolve(PENDING_FILE)
        if (Files.exists(pending, LinkOption.NOFOLLOW_LINKS)) {
            throw DocumentIngestionFailure("provenance-recovery-required", "A prior document provenance event requires recovery.")
        }
        writeAtomic(pending, objectMapper.writeValueAsBytes(PendingDocumentProvenanceSnapshot(event = event)))
    }

    @Synchronized
    public fun commitPending(projectId: String): List<AppliedDocumentProvenance> {
        val directory = projectDirectory(projectId, create = false)
            ?: throw DocumentIngestionFailure("provenance-pending-missing", "The pending document provenance event was not found.")
        val event = readPending(directory)
            ?: throw DocumentIngestionFailure("provenance-pending-missing", "The pending document provenance event was not found.")
        val records = save(projectId, event.records)
        Files.delete(directory.resolve(PENDING_FILE))
        return records
    }

    @Synchronized
    public fun discardPending(projectId: String): Unit {
        val directory = projectDirectory(projectId, create = false) ?: return
        Files.deleteIfExists(directory.resolve(PENDING_FILE))
    }

    @Synchronized
    public fun pending(projectId: String): PendingDocumentProvenance? {
        val directory = projectDirectory(projectId, create = false) ?: return null
        return readPending(directory)
    }

    private fun initializeRoot(): Unit {
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw DocumentIngestionFailure("provenance-root-unsafe", "The configured provenance root is unsafe.")
            }
        } else {
            Files.createDirectories(root)
        }
        setOwnerOnlyPermissions(root, directory = true)
        val marker = root.resolve(ROOT_MARKER)
        if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(marker) || Files.readString(marker) != ROOT_MARKER_CONTENT) {
                throw DocumentIngestionFailure("provenance-root-unsafe", "The configured provenance root is not Entio-owned.")
            }
        } else {
            Files.writeString(marker, ROOT_MARKER_CONTENT, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
            setOwnerOnlyPermissions(marker, directory = false)
        }
    }

    private fun validateRootSeparation(): Unit {
        projectRegistry.list().forEach { project ->
            val projectRoot = projectRegistry.rootFor(project.id).toAbsolutePath().normalize()
            if (root == projectRoot || root.startsWith(projectRoot) || projectRoot.startsWith(root)) {
                throw DocumentIngestionFailure(
                    "provenance-root-overlaps-project",
                    "The provenance repository must remain separate from ontology projects.",
                )
            }
        }
    }

    private fun projectDirectory(projectId: String, create: Boolean): Path? {
        projectRegistry.find(projectId)
            ?: throw DocumentIngestionFailure("ingestion-task-not-found", "The requested ingestion project was not found.")
        val key = sha256(projectId)
        val directory = root.resolve("project-$key").normalize()
        require(directory.parent == root && directory.startsWith(root)) { "Generated provenance path escaped its root." }
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (!create) return null
            Files.createDirectory(directory)
            setOwnerOnlyPermissions(directory, directory = true)
            Files.writeString(directory.resolve(PROJECT_MARKER), projectId, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
            setOwnerOnlyPermissions(directory.resolve(PROJECT_MARKER), directory = false)
        }
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw DocumentIngestionFailure("provenance-root-unsafe", "The project provenance directory is unsafe.")
        }
        val marker = directory.resolve(PROJECT_MARKER)
        if (Files.isSymbolicLink(marker) ||
            !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) ||
            Files.readString(marker) != projectId
        ) {
            throw DocumentIngestionFailure("provenance-root-unsafe", "The project provenance directory is not Entio-owned.")
        }
        return directory
    }

    private fun readSnapshot(directory: Path): DocumentProvenanceSnapshot {
        val path = directory.resolve(RECORDS_FILE)
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return DocumentProvenanceSnapshot()
        requireSafeRegularFile(path)
        val snapshot = runCatching { objectMapper.readValue<DocumentProvenanceSnapshot>(Files.readAllBytes(path)) }
            .getOrElse { throw DocumentIngestionFailure("provenance-read-failed", "Document provenance could not be read safely.") }
        if (snapshot.schemaVersion != PROVENANCE_SCHEMA_VERSION) {
            throw DocumentIngestionFailure("provenance-migration-required", "Document provenance uses an unsupported schema version.")
        }
        return snapshot
    }

    private fun readPending(directory: Path): PendingDocumentProvenance? {
        val path = directory.resolve(PENDING_FILE)
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null
        requireSafeRegularFile(path)
        val snapshot = runCatching { objectMapper.readValue<PendingDocumentProvenanceSnapshot>(Files.readAllBytes(path)) }
            .getOrElse { throw DocumentIngestionFailure("provenance-read-failed", "Pending document provenance could not be read safely.") }
        if (snapshot.schemaVersion != PROVENANCE_SCHEMA_VERSION) {
            throw DocumentIngestionFailure("provenance-migration-required", "Pending provenance uses an unsupported schema version.")
        }
        return snapshot.event
    }

    private fun writeSnapshot(directory: Path, snapshot: DocumentProvenanceSnapshot): Unit {
        if (snapshot.records.size > MAX_PROVENANCE_RECORDS) {
            throw DocumentIngestionFailure("provenance-limit", "The project provenance record limit was reached.")
        }
        val bytes = objectMapper.writeValueAsBytes(snapshot)
        if (bytes.size > MAX_PROVENANCE_BYTES) {
            throw DocumentIngestionFailure("provenance-limit", "The project provenance storage limit was reached.")
        }
        writeAtomic(directory.resolve(RECORDS_FILE), bytes)
    }

    private fun writeAtomic(target: Path, bytes: ByteArray): Unit {
        val directory = target.parent
        val temporary = directory.resolve(".${target.fileName}.${java.util.UUID.randomUUID()}.tmp")
        try {
            FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) {
                    channel.write(buffer)
                }
                channel.force(true)
            }
            setOwnerOnlyPermissions(temporary, directory = false)
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                throw DocumentIngestionFailure("provenance-atomic-move-unsupported", "The provenance filesystem does not support atomic replacement.")
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun requireSafeRegularFile(path: Path): Unit {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw DocumentIngestionFailure("provenance-root-unsafe", "A provenance file is unsafe.")
        }
    }

    private fun setOwnerOnlyPermissions(path: Path, directory: Boolean): Unit {
        runCatching {
            val permissions = if (directory) {
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                )
            } else {
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            }
            Files.setPosixFilePermissions(path, permissions)
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_PROVENANCE_RECORDS: Int = 100_000
        const val MAX_PROVENANCE_BYTES: Int = 512 * 1024 * 1024
        const val ROOT_MARKER: String = ".entio-provenance-root-v1"
        const val ROOT_MARKER_CONTENT: String = "entio-document-provenance-root-v1\n"
        const val PROJECT_MARKER: String = ".entio-project-id-v1"
        const val RECORDS_FILE: String = "records-v1.json"
        const val PENDING_FILE: String = "pending-v1.json"
    }
}
