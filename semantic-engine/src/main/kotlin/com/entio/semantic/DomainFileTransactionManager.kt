package com.entio.semantic

import com.entio.core.DomainOntologyProjectPaths
import com.entio.core.EntioResult
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

public enum class DomainTransactionOperation {
    Activation,
    Deactivation,
    ManagedApply,
}

public enum class DomainTransactionFile {
    Profile,
    ManagedSource,
    Provenance,
}

public enum class DomainTransactionRecoveryOutcome {
    NoTransaction,
    IntendedStateCompleted,
    OriginalStateConfirmed,
    RolledBackToOriginal,
}

public data class PreparedDomainTransaction(
    public val transactionId: String,
    public val operation: DomainTransactionOperation,
    public val projectRoot: Path,
)

/** Journaled writer restricted to the three fixed Phase 13 project files. */
public class DomainFileTransactionManager(
    private val repository: DomainProfileRepository = DomainProfileRepository(),
) {
    public fun prepare(
        projectRoot: Path,
        operation: DomainTransactionOperation,
        intended: Map<DomainTransactionFile, ByteArray?>,
    ): EntioResult<PreparedDomainTransaction> {
        validateTargets(operation, intended.keys)?.let { return it }
        val paths = when (val result = repository.resolvePaths(projectRoot)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        if (Files.exists(paths.transactionJournal) || Files.exists(paths.transactionDirectory)) {
            return failure("domain-transaction-already-active", "A domain transaction already requires completion or recovery.")
        }
        val transactionId = UUID.randomUUID().toString()
        val targets = mutableListOf<JournalTarget>()
        try {
            Files.createDirectories(paths.profile.parent)
            Files.createDirectory(paths.transactionDirectory)
            intended.entries.sortedBy { it.key.ordinal }.forEach { (file, bytes) ->
                val target = targetPath(paths, file)
                val originalBytes = if (Files.exists(target)) Files.readAllBytes(target) else null
                val backup = originalBytes?.let { paths.transactionDirectory.resolve("${file.name}.original") }
                val temporary = bytes?.let { paths.transactionDirectory.resolve("${file.name}.intended") }
                if (originalBytes != null) writeDurably(backup!!, originalBytes)
                if (bytes != null) writeDurably(temporary!!, bytes)
                targets += JournalTarget(
                    file = file,
                    relativePath = paths.projectRoot.relativize(target).toString().replace('\\', '/'),
                    originalSha256 = originalBytes?.let(::sha256),
                    intendedSha256 = bytes?.let(::sha256),
                    backup = backup,
                    temporary = temporary,
                )
            }
            writeJournal(paths, Journal(transactionId, operation, TransactionPhase.Prepared, targets))
            return EntioResult.Success(PreparedDomainTransaction(transactionId, operation, paths.projectRoot))
        } catch (exception: IOException) {
            cleanupPrepared(paths)
            return failure("domain-transaction-prepare-failed", "The domain transaction could not be prepared.", exception)
        }
    }

    public fun commit(
        prepared: PreparedDomainTransaction,
        semanticVerification: (() -> EntioResult<Unit>)? = null,
    ): EntioResult<Unit> {
        val paths = when (val result = repository.resolvePaths(prepared.projectRoot)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        val journal = when (val result = readJournal(paths)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        if (journal.transactionId != prepared.transactionId || journal.operation != prepared.operation) {
            return failure("domain-transaction-token-mismatch", "The prepared domain transaction does not match the recovery journal.")
        }
        return try {
            writeJournal(paths, journal.copy(phase = TransactionPhase.Replacing))
            replacementOrder(journal.operation).forEach { file ->
                journal.targets.firstOrNull { it.file == file }?.let { replaceTarget(paths, it) }
            }
            writeJournal(paths, journal.copy(phase = TransactionPhase.Verifying))
            if (!journal.targets.all { actualSha256(targetPath(paths, it.file)) == it.intendedSha256 }) {
                return rollback(paths, journal, "Domain transaction verification found an unexpected target state.")
            }
            val verified = runSemanticVerification(paths, journal.operation, semanticVerification)
            if (verified is EntioResult.Failure) {
                return rollback(paths, journal, verified.message)
            }
            writeJournal(paths, journal.copy(phase = TransactionPhase.Committed))
            cleanupCommitted(paths)
            EntioResult.Success(Unit)
        } catch (exception: Exception) {
            rollback(paths, journal, exception.message ?: "The domain transaction failed while replacing files.")
        }
    }

    private fun verifyBuiltInSemantics(
        paths: DomainOntologyProjectPaths,
        operation: DomainTransactionOperation,
    ): EntioResult<Unit> = when (operation) {
        DomainTransactionOperation.Activation -> when (val read = repository.read(paths.projectRoot)) {
            is EntioResult.Failure -> read
            is EntioResult.Success -> if (read.value.activeDomainOntology == null) {
                failure("domain-transaction-activation-verification-failed", "The activated domain profile did not load.")
            } else {
                EntioResult.Success(Unit)
            }
        }
        DomainTransactionOperation.Deactivation -> when (val read = repository.read(paths.projectRoot)) {
            is EntioResult.Failure -> read
            is EntioResult.Success -> if (read.value.activeDomainOntology != null) {
                failure("domain-transaction-deactivation-verification-failed", "The deactivated domain profile still loads.")
            } else {
                EntioResult.Success(Unit)
            }
        }
        DomainTransactionOperation.ManagedApply -> EntioResult.Success(Unit)
    }

    private fun runSemanticVerification(
        paths: DomainOntologyProjectPaths,
        operation: DomainTransactionOperation,
        verification: (() -> EntioResult<Unit>)?,
    ): EntioResult<Unit> = try {
        verification?.invoke() ?: verifyBuiltInSemantics(paths, operation)
    } catch (exception: Exception) {
        failure(
            "domain-transaction-semantic-verification-failed",
            "Domain transaction semantic verification could not complete.",
            exception,
        )
    }

    public fun recover(
        projectRoot: Path,
        semanticVerification: (() -> EntioResult<Unit>)? = null,
    ): EntioResult<DomainTransactionRecoveryOutcome> {
        val paths = when (val result = repository.resolvePaths(projectRoot)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        if (!Files.exists(paths.transactionJournal)) {
            return if (Files.exists(paths.transactionDirectory)) {
                failure(
                    "domain-transaction-orphaned-artifacts",
                    "Domain transaction artifacts exist without a recovery journal.",
                )
            } else {
                EntioResult.Success(DomainTransactionRecoveryOutcome.NoTransaction)
            }
        }
        val journal = when (val result = readJournal(paths)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        val actual = journal.targets.associateWith { actualSha256(targetPath(paths, it.file)) }
        val allIntended = journal.targets.all { actual.getValue(it) == it.intendedSha256 }
        if (allIntended) {
            val verified = runSemanticVerification(paths, journal.operation, semanticVerification)
            if (verified is EntioResult.Failure) {
                return when (val restored = restoreOriginals(paths, journal)) {
                    is EntioResult.Failure -> restored
                    is EntioResult.Success -> EntioResult.Success(DomainTransactionRecoveryOutcome.RolledBackToOriginal)
                }
            }
            return try {
                cleanupCommitted(paths)
                EntioResult.Success(DomainTransactionRecoveryOutcome.IntendedStateCompleted)
            } catch (exception: IOException) {
                failure("domain-transaction-cleanup-failed", "Completed domain transaction artifacts could not be removed.", exception)
            }
        }
        val allOriginal = journal.targets.all { actual.getValue(it) == it.originalSha256 }
        if (allOriginal) {
            return try {
                cleanupCommitted(paths)
                EntioResult.Success(DomainTransactionRecoveryOutcome.OriginalStateConfirmed)
            } catch (exception: IOException) {
                failure("domain-transaction-cleanup-failed", "Original domain transaction artifacts could not be removed.", exception)
            }
        }
        if (journal.targets.any { actual.getValue(it) !in setOf(it.originalSha256, it.intendedSha256) }) {
            return failure(
                "domain-transaction-unknown-state",
                "Domain transaction recovery found bytes matching neither the original nor intended state.",
            )
        }
        return when (val result = restoreOriginals(paths, journal)) {
            is EntioResult.Failure -> result
            is EntioResult.Success -> EntioResult.Success(DomainTransactionRecoveryOutcome.RolledBackToOriginal)
        }
    }

    private fun rollback(paths: DomainOntologyProjectPaths, journal: Journal, reason: String): EntioResult<Unit> =
        when (val restored = restoreOriginals(paths, journal)) {
            is EntioResult.Failure -> restored
            is EntioResult.Success -> failure("domain-transaction-rolled-back", reason)
        }

    private fun restoreOriginals(paths: DomainOntologyProjectPaths, journal: Journal): EntioResult<Unit> {
        return try {
            if (journal.targets.any {
                    actualSha256(targetPath(paths, it.file)) !in setOf(it.originalSha256, it.intendedSha256)
                }
            ) {
                return failure(
                    "domain-transaction-unknown-state",
                    "Domain transaction rollback found bytes matching neither the original nor intended state.",
                )
            }
            journal.targets.asReversed().forEach { target ->
                val path = targetPath(paths, target.file)
                if (target.originalSha256 == null) {
                    Files.deleteIfExists(path)
                } else {
                    val backup = target.backup
                        ?: return failure("domain-transaction-backup-missing", "A required domain transaction backup is missing.")
                    if (actualSha256(backup) != target.originalSha256) {
                        return failure("domain-transaction-backup-invalid", "A domain transaction backup failed checksum verification.")
                    }
                    Files.createDirectories(path.parent)
                    val restore = paths.transactionDirectory.resolve("${target.file.name}.restore")
                    if (Files.exists(restore) && actualSha256(restore) != target.originalSha256) {
                        return failure("domain-transaction-restore-invalid", "A domain transaction restore file failed checksum verification.")
                    }
                    if (!Files.exists(restore)) writeDurably(restore, Files.readAllBytes(backup))
                    try {
                        Files.move(restore, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                    } catch (_: AtomicMoveNotSupportedException) {
                        Files.move(restore, path, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
            }
            if (!journal.targets.all { actualSha256(targetPath(paths, it.file)) == it.originalSha256 }) {
                return failure("domain-transaction-rollback-verification-failed", "The original domain transaction state could not be verified.")
            }
            cleanupCommitted(paths)
            EntioResult.Success(Unit)
        } catch (exception: IOException) {
            failure("domain-transaction-rollback-failed", "The original domain transaction state could not be restored.", exception)
        }
    }

    private fun replaceTarget(paths: DomainOntologyProjectPaths, target: JournalTarget): Unit {
        val path = targetPath(paths, target.file)
        if (actualSha256(path) != target.originalSha256) {
            throw IOException("Domain transaction target '${target.file}' changed after preparation.")
        }
        if (target.intendedSha256 == null) {
            Files.deleteIfExists(path)
            return
        }
        val temporary = target.temporary ?: throw IOException("Domain transaction intended bytes are missing.")
        if (actualSha256(temporary) != target.intendedSha256) {
            throw IOException("Domain transaction intended bytes failed checksum verification.")
        }
        Files.createDirectories(path.parent)
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun validateTargets(
        operation: DomainTransactionOperation,
        files: Set<DomainTransactionFile>,
    ): EntioResult.Failure? {
        val expected = when (operation) {
            DomainTransactionOperation.Activation, DomainTransactionOperation.Deactivation ->
                setOf(DomainTransactionFile.Profile, DomainTransactionFile.ManagedSource)
            DomainTransactionOperation.ManagedApply ->
                setOf(DomainTransactionFile.ManagedSource, DomainTransactionFile.Provenance)
        }
        return if (files == expected) null else failure(
            "invalid-domain-transaction-targets",
            "Domain transaction '$operation' must target exactly ${expected.sortedBy { it.ordinal }.joinToString()}.",
        )
    }

    private fun replacementOrder(operation: DomainTransactionOperation): List<DomainTransactionFile> = when (operation) {
        DomainTransactionOperation.Activation -> listOf(DomainTransactionFile.ManagedSource, DomainTransactionFile.Profile)
        DomainTransactionOperation.Deactivation -> listOf(DomainTransactionFile.Profile, DomainTransactionFile.ManagedSource)
        DomainTransactionOperation.ManagedApply -> listOf(DomainTransactionFile.ManagedSource, DomainTransactionFile.Provenance)
    }

    private fun targetPath(paths: DomainOntologyProjectPaths, file: DomainTransactionFile): Path = when (file) {
        DomainTransactionFile.Profile -> paths.profile
        DomainTransactionFile.ManagedSource -> paths.managedSource
        DomainTransactionFile.Provenance -> paths.provenance
    }

    private fun writeJournal(paths: DomainOntologyProjectPaths, journal: Journal): Unit {
        val temporary = paths.transactionDirectory.resolve("journal.next")
        writeDurably(temporary, serializeJournal(journal).toByteArray(Charsets.UTF_8))
        try {
            Files.move(temporary, paths.transactionJournal, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, paths.transactionJournal, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun readJournal(paths: DomainOntologyProjectPaths): EntioResult<Journal> {
        val root = try {
            jsonLoader.loadFromString(Files.readString(paths.transactionJournal)) as? Map<*, *>
                ?: return failure("invalid-domain-transaction-journal", "The domain transaction journal is not an object.")
        } catch (exception: Exception) {
            return failure("invalid-domain-transaction-journal", "The domain transaction journal could not be parsed.", exception)
        }
        if (root["schema"] != JOURNAL_SCHEMA) {
            return failure("invalid-domain-transaction-journal", "The domain transaction journal schema is unsupported.")
        }
        return try {
            require(root.keys == JOURNAL_KEYS)
            val transactionId = root["transactionId"] as String
            UUID.fromString(transactionId)
            val operation = DomainTransactionOperation.valueOf(root["operation"] as String)
            val phase = TransactionPhase.valueOf(root["phase"] as String)
            val targetMaps = root["targets"] as List<*>
            val targets = targetMaps.map { value ->
                val map = value as Map<*, *>
                require(map.keys == TARGET_KEYS)
                val file = DomainTransactionFile.valueOf(map["file"] as String)
                val expectedPath = paths.projectRoot.relativize(targetPath(paths, file)).toString().replace('\\', '/')
                require(map["path"] == expectedPath)
                val originalSha256 = nullableString(map, "originalSha256")
                val intendedSha256 = nullableString(map, "intendedSha256")
                require(originalSha256 == null || SHA256.matches(originalSha256))
                require(intendedSha256 == null || SHA256.matches(intendedSha256))
                val backupName = originalSha256?.let { "${file.name}.original" }
                val temporaryName = intendedSha256?.let { "${file.name}.intended" }
                require(map["backup"] == backupName)
                require(map["temporary"] == temporaryName)
                JournalTarget(
                    file = file,
                    relativePath = expectedPath,
                    originalSha256 = originalSha256,
                    intendedSha256 = intendedSha256,
                    backup = backupName?.let(paths.transactionDirectory::resolve),
                    temporary = temporaryName?.let(paths.transactionDirectory::resolve),
                )
            }
            validateTargets(operation, targets.map { it.file }.toSet())?.let { return it }
            require(targets.map { it.file }.distinct().size == targets.size)
            EntioResult.Success(Journal(transactionId, operation, phase, targets.sortedBy { it.file.ordinal }))
        } catch (exception: Exception) {
            failure("invalid-domain-transaction-journal", "The domain transaction journal fields are invalid.", exception)
        }
    }

    private fun serializeJournal(journal: Journal): String = buildString {
        append("{\n")
        append("  \"schema\": \"$JOURNAL_SCHEMA\",\n")
        append("  \"transactionId\": \"${journal.transactionId}\",\n")
        append("  \"operation\": \"${journal.operation.name}\",\n")
        append("  \"phase\": \"${journal.phase.name}\",\n")
        append("  \"targets\": [\n")
        journal.targets.sortedBy { it.file.ordinal }.forEachIndexed { index, target ->
            append("    {\"file\": \"${target.file.name}\", \"path\": \"${target.relativePath}\", ")
            append("\"originalSha256\": ${jsonString(target.originalSha256)}, ")
            append("\"intendedSha256\": ${jsonString(target.intendedSha256)}, ")
            append("\"backup\": ${jsonString(target.backup?.fileName?.toString())}, ")
            append("\"temporary\": ${jsonString(target.temporary?.fileName?.toString())}}")
            appendLine(if (index == journal.targets.lastIndex) "" else ",")
        }
        append("  ]\n")
        append("}\n")
    }

    private fun jsonString(value: String?): String = value?.let { "\"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\"" } ?: "null"

    private fun nullableString(map: Map<*, *>, key: String): String? {
        val value = map[key]
        require(value == null || value is String)
        return value as String?
    }

    private fun writeDurably(path: Path, bytes: ByteArray): Unit {
        FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
    }

    private fun actualSha256(path: Path): String? = if (Files.exists(path)) sha256(Files.readAllBytes(path)) else null

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun cleanupPrepared(paths: DomainOntologyProjectPaths): Unit {
        runCatching {
            if (Files.isDirectory(paths.transactionDirectory)) {
                Files.list(paths.transactionDirectory).use { stream -> stream.forEach(Files::deleteIfExists) }
                Files.deleteIfExists(paths.transactionDirectory)
            }
        }
    }

    private fun cleanupCommitted(paths: DomainOntologyProjectPaths): Unit {
        if (Files.isDirectory(paths.transactionDirectory)) {
            Files.list(paths.transactionDirectory).use { stream -> stream.forEach(Files::deleteIfExists) }
            Files.deleteIfExists(paths.transactionDirectory)
        }
        Files.deleteIfExists(paths.transactionJournal)
    }

    private fun failure(code: String, message: String, cause: Throwable? = null): EntioResult.Failure = EntioResult.Failure(
        message = message,
        issues = listOf(ValidationIssue(ValidationSeverity.Error, code, message, "domain-transaction")),
        cause = cause,
    )

    private data class Journal(
        val transactionId: String,
        val operation: DomainTransactionOperation,
        val phase: TransactionPhase,
        val targets: List<JournalTarget>,
    )

    private data class JournalTarget(
        val file: DomainTransactionFile,
        val relativePath: String,
        val originalSha256: String?,
        val intendedSha256: String?,
        val backup: Path?,
        val temporary: Path?,
    )

    private enum class TransactionPhase {
        Prepared,
        Replacing,
        Verifying,
        Committed,
    }

    private companion object {
        private const val JOURNAL_SCHEMA: String = "entio-domain-transaction-v1"
        private val JOURNAL_KEYS: Set<String> = setOf("schema", "transactionId", "operation", "phase", "targets")
        private val TARGET_KEYS: Set<String> = setOf(
            "file",
            "path",
            "originalSha256",
            "intendedSha256",
            "backup",
            "temporary",
        )
        private val SHA256: Regex = Regex("[0-9a-f]{64}")
        private val jsonLoader: Load = Load(LoadSettings.builder().setLabel("domain-transaction-v1.json").build())
    }
}
