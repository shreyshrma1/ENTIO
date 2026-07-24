package com.entio.web.ingestion

import java.io.InputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable

internal data class TemporaryTaskDirectory(
    val taskDirectoryName: String,
    val path: Path,
)

internal data class StoredDocumentFile(
    val fileName: String,
    val path: Path,
    val byteSize: Long,
)

/** Owns generated temporary paths and never accepts a client path. */
internal class DocumentTemporaryStorage(
    root: Path,
) : AutoCloseable {
    private val root: Path = root.toAbsolutePath().normalize()

    init {
        initializeRoot()
        cleanupStaleTasks()
    }

    fun createTask(taskId: String): TemporaryTaskDirectory {
        val name = taskId
        val path = directChild(name)
        try {
            Files.createDirectory(path)
            setOwnerOnlyPermissions(path, directory = true)
            Files.writeString(
                path.resolve(TASK_MARKER),
                TASK_MARKER_CONTENT,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
            )
            setOwnerOnlyPermissions(path.resolve(TASK_MARKER), directory = false)
            return TemporaryTaskDirectory(name, path)
        } catch (failure: Exception) {
            runCatching { deleteMarkedTask(path) }
            throw DocumentIngestionFailure("temporary-storage-failed", "Entio could not create temporary document storage.")
        }
    }

    fun writeDocument(
        task: TemporaryTaskDirectory,
        documentId: String,
        input: InputStream,
        maximumBytes: Long,
    ): StoredDocumentFile {
        requireOwnedTask(task)
        val fileName = "document-$documentId.bin"
        val destination = task.path.resolve(fileName).normalize()
        require(destination.parent == task.path && destination.startsWith(root)) { "Generated document path escaped its task." }
        var count = 0L
        try {
            Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    count += read
                    if (count > maximumBytes) {
                        throw DocumentIngestionFailure("document-too-large", "A document exceeds the 25 MB limit.")
                    }
                    output.write(buffer, 0, read)
                }
            }
            if (count == 0L) throw DocumentIngestionFailure("empty-document", "An uploaded document must not be empty.")
            setOwnerOnlyPermissions(destination, directory = false)
            return StoredDocumentFile(fileName, destination, count)
        } catch (failure: DocumentIngestionFailure) {
            Files.deleteIfExists(destination)
            throw failure
        } catch (_: Exception) {
            Files.deleteIfExists(destination)
            throw DocumentIngestionFailure("temporary-storage-failed", "Entio could not store the uploaded document.")
        }
    }

    suspend fun writeDocument(
        task: TemporaryTaskDirectory,
        documentId: String,
        channel: ByteReadChannel,
        maximumBytes: Long,
    ): StoredDocumentFile {
        requireOwnedTask(task)
        val fileName = "document-$documentId.bin"
        val destination = task.path.resolve(fileName).normalize()
        require(destination.parent == task.path && destination.startsWith(root)) { "Generated document path escaped its task." }
        var count = 0L
        try {
            Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    count += read
                    if (count > maximumBytes) {
                        throw DocumentIngestionFailure("document-too-large", "A document exceeds the 25 MB limit.")
                    }
                    output.write(buffer, 0, read)
                }
            }
            channel.closedCause?.let { throw it }
            if (count == 0L) throw DocumentIngestionFailure("empty-document", "An uploaded document must not be empty.")
            setOwnerOnlyPermissions(destination, directory = false)
            return StoredDocumentFile(fileName, destination, count)
        } catch (failure: DocumentIngestionFailure) {
            Files.deleteIfExists(destination)
            throw failure
        } catch (_: Exception) {
            Files.deleteIfExists(destination)
            throw DocumentIngestionFailure("temporary-storage-failed", "Entio could not store the uploaded document.")
        }
    }

    fun deleteTask(task: TemporaryTaskDirectory): Unit = deleteMarkedTask(task.path)

    fun taskExists(task: TemporaryTaskDirectory): Boolean = runCatching {
        requireOwnedTask(task)
        true
    }.getOrDefault(false)

    override fun close(): Unit = cleanupStaleTasks()

    private fun initializeRoot(): Unit {
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw DocumentIngestionFailure("temporary-root-unsafe", "The configured document temporary root is unsafe.")
            }
        } else {
            Files.createDirectories(root)
        }
        setOwnerOnlyPermissions(root, directory = true)
        val marker = root.resolve(ROOT_MARKER)
        if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(marker) || Files.readString(marker) != ROOT_MARKER_CONTENT) {
                throw DocumentIngestionFailure("temporary-root-unsafe", "The configured document temporary root is not Entio-owned.")
            }
        } else {
            Files.writeString(marker, ROOT_MARKER_CONTENT, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
            setOwnerOnlyPermissions(marker, directory = false)
        }
    }

    private fun cleanupStaleTasks(): Unit {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return
        Files.newDirectoryStream(root, "task-*").use { children ->
            children.forEach { child ->
                if (!Files.isSymbolicLink(child) && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    val marker = child.resolve(TASK_MARKER)
                    if (Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) &&
                        !Files.isSymbolicLink(marker) &&
                        Files.readString(marker) == TASK_MARKER_CONTENT
                    ) {
                        deleteMarkedTask(child)
                    }
                }
            }
        }
    }

    private fun deleteMarkedTask(path: Path): Unit {
        val normalized = path.toAbsolutePath().normalize()
        require(normalized.parent == root && normalized.fileName.toString().startsWith("task-")) {
            "Only direct Entio task directories may be deleted."
        }
        if (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) return
        require(!Files.isSymbolicLink(normalized) && Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            "Document task path is unsafe."
        }
        val marker = normalized.resolve(TASK_MARKER)
        require(Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(marker)) {
            "Document task ownership marker is missing."
        }
        require(Files.readString(marker) == TASK_MARKER_CONTENT) { "Document task ownership marker is invalid." }
        Files.walkFileTree(
            normalized,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                    if (exc != null) throw exc
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    private fun requireOwnedTask(task: TemporaryTaskDirectory): Unit {
        val expected = directChild(task.taskDirectoryName)
        require(expected == task.path.toAbsolutePath().normalize()) { "Document task path does not match its generated identity." }
        require(!Files.isSymbolicLink(expected) && Files.isDirectory(expected, LinkOption.NOFOLLOW_LINKS)) {
            "Document task directory is unavailable."
        }
        val marker = expected.resolve(TASK_MARKER)
        require(
            Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) &&
                !Files.isSymbolicLink(marker) &&
                Files.readString(marker) == TASK_MARKER_CONTENT,
        ) { "Document task directory is not Entio-owned." }
    }

    private fun directChild(name: String): Path {
        require(name.matches(Regex("task-[A-Za-z0-9][A-Za-z0-9._:-]{0,199}"))) { "Invalid generated task directory name." }
        val path = root.resolve(name).normalize()
        require(path.parent == root && path.startsWith(root)) { "Generated task path escaped the temporary root." }
        return path
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

    private companion object {
        const val ROOT_MARKER: String = ".entio-ingestion-root-v1"
        const val ROOT_MARKER_CONTENT: String = "entio-document-ingestion-root-v1\n"
        const val TASK_MARKER: String = ".entio-ingestion-task-v1"
        const val TASK_MARKER_CONTENT: String = "entio-document-ingestion-task-v1\n"
    }
}
