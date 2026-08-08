package com.entio.semantic

import com.entio.core.DomainCustomizationClassification
import com.entio.core.DomainOntologyProfileIdentity
import com.entio.core.DomainReuseEventKind
import com.entio.core.DomainReuseProvenanceEvent
import com.entio.core.EntioResult
import com.entio.core.ExternalEntityKind
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.RdfTerm
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Locale
import org.snakeyaml.engine.v2.api.Dump
import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.common.FlowStyle

public data class PreparedDomainReuseProvenance(
    public val projectRoot: Path,
    public val eventCount: Int,
    public val intendedFingerprint: String,
)

/** Checksummed JSONL history with a recoverable prepare/commit/finalize protocol. */
public class DomainReuseProvenanceRepository {
    private val loader = Load(LoadSettings.builder().setLabel("domain-reuse-provenance").build())
    private val dumper = Dump(
        DumpSettings.builder().setDefaultFlowStyle(FlowStyle.FLOW).setDumpComments(false).build(),
    )

    public fun list(projectRoot: Path): EntioResult<List<DomainReuseProvenanceEvent>> {
        return try {
            val paths = paths(projectRoot)
            if (!Files.exists(paths.events, LinkOption.NOFOLLOW_LINKS)) return EntioResult.Success(emptyList())
            verifyRegular(paths.events)
            require(Files.size(paths.events) <= MAX_PROVENANCE_BYTES) { "domain-provenance-size-limit" }
            EntioResult.Success(
                Files.readAllLines(paths.events).filter(String::isNotBlank).mapIndexed { index, line ->
                    parseEvent(line, index + 1)
                },
            )
        } catch (failure: Exception) {
            failure("domain-provenance-invalid", "Domain reuse provenance is corrupt or unsafe.", failure)
        }
    }

    @Synchronized
    public fun prepare(
        projectRoot: Path,
        events: List<DomainReuseProvenanceEvent>,
        baselineProjectFingerprint: String,
        resultingProjectFingerprint: String,
    ): EntioResult<PreparedDomainReuseProvenance> {
        return try {
            require(events.isNotEmpty()) { "domain-provenance-events-required" }
            val paths = paths(projectRoot)
            Files.createDirectories(paths.directory)
            verifyDirectory(paths.directory)
            if (Files.exists(paths.journal, LinkOption.NOFOLLOW_LINKS)) {
                return failure("domain-provenance-recovery-required", "A prior domain reuse transaction requires recovery.")
            }
            val existing = when (val result = list(projectRoot)) {
                is EntioResult.Failure -> return result
                is EntioResult.Success -> result.value
            }
            val normalized = events.map(::withChecksum)
            require(normalized.all { event -> existing.none { it.recordId == event.recordId } }) {
                "domain-provenance-record-duplicate"
            }
            val original = if (Files.isRegularFile(paths.events, LinkOption.NOFOLLOW_LINKS)) {
                Files.readAllBytes(paths.events)
            } else {
                null
            }
            val intended = (existing + normalized).joinToString(separator = "\n", postfix = "\n", transform = ::serializeEvent)
                .toByteArray(Charsets.UTF_8)
            require(intended.size <= MAX_PROVENANCE_BYTES) { "domain-provenance-size-limit" }
            writeNew(paths.original, original ?: ByteArray(0))
            writeNew(paths.intended, intended)
            writeJournal(
                paths,
                Journal(
                    state = JournalState.Prepared,
                    originalExisted = original != null,
                    originalFingerprint = sha256(original ?: ByteArray(0)),
                    intendedFingerprint = sha256(intended),
                    baselineProjectFingerprint = baselineProjectFingerprint,
                    resultingProjectFingerprint = resultingProjectFingerprint,
                ),
            )
            EntioResult.Success(PreparedDomainReuseProvenance(projectRoot, normalized.size, sha256(intended)))
        } catch (failure: Exception) {
            runCatching { discardPrepared(paths(projectRoot)) }
            failure("domain-provenance-prepare-failed", "Domain reuse provenance could not be prepared.", failure)
        }
    }

    @Synchronized
    public fun commit(prepared: PreparedDomainReuseProvenance): EntioResult<Unit> = try {
        val paths = paths(prepared.projectRoot)
        val journal = readJournal(paths)
        require(journal.state == JournalState.Prepared) { "domain-provenance-transaction-not-prepared" }
        val current = if (Files.isRegularFile(paths.events, LinkOption.NOFOLLOW_LINKS)) {
            Files.readAllBytes(paths.events)
        } else {
            ByteArray(0)
        }
        require(sha256(current) == journal.originalFingerprint) { "domain-provenance-stale-baseline" }
        val intended = Files.readAllBytes(paths.intended)
        require(sha256(intended) == journal.intendedFingerprint) { "domain-provenance-intended-corrupt" }
        replaceAtomically(paths.events, intended)
        writeJournal(paths, journal.copy(state = JournalState.Committed))
        EntioResult.Success(Unit)
    } catch (failure: Exception) {
        failure("domain-provenance-commit-failed", "Domain reuse provenance could not be committed.", failure)
    }

    @Synchronized
    public fun finish(prepared: PreparedDomainReuseProvenance): EntioResult<Unit> = try {
        val paths = paths(prepared.projectRoot)
        val journal = readJournal(paths)
        require(journal.state == JournalState.Committed) { "domain-provenance-transaction-not-committed" }
        require(Files.isRegularFile(paths.events, LinkOption.NOFOLLOW_LINKS)) { "domain-provenance-events-missing" }
        require(sha256(Files.readAllBytes(paths.events)) == journal.intendedFingerprint) {
            "domain-provenance-commit-verification-failed"
        }
        discardPrepared(paths)
        EntioResult.Success(Unit)
    } catch (failure: Exception) {
        failure("domain-provenance-finish-failed", "Domain reuse provenance could not be finalized.", failure)
    }

    @Synchronized
    public fun rollback(prepared: PreparedDomainReuseProvenance): EntioResult<Unit> = rollback(prepared.projectRoot)

    @Synchronized
    public fun recover(projectRoot: Path, currentProjectFingerprint: String): EntioResult<Unit> {
        return try {
            val paths = paths(projectRoot)
            if (!Files.exists(paths.journal, LinkOption.NOFOLLOW_LINKS)) return EntioResult.Success(Unit)
            val journal = readJournal(paths)
            val eventFingerprint = if (Files.isRegularFile(paths.events, LinkOption.NOFOLLOW_LINKS)) {
                sha256(Files.readAllBytes(paths.events))
            } else {
                sha256(ByteArray(0))
            }
            when {
                currentProjectFingerprint == journal.resultingProjectFingerprint &&
                    eventFingerprint == journal.intendedFingerprint -> discardPrepared(paths)
                currentProjectFingerprint == journal.baselineProjectFingerprint -> restore(paths, journal)
                else -> return failure(
                    "domain-provenance-recovery-ambiguous",
                    "Domain reuse provenance cannot be recovered against the current ontology fingerprint.",
                )
            }
            EntioResult.Success(Unit)
        } catch (failure: Exception) {
            failure("domain-provenance-recovery-failed", "Domain reuse provenance recovery failed.", failure)
        }
    }

    private fun rollback(projectRoot: Path): EntioResult<Unit> {
        return try {
            val paths = paths(projectRoot)
            if (!Files.exists(paths.journal, LinkOption.NOFOLLOW_LINKS)) return EntioResult.Success(Unit)
            restore(paths, readJournal(paths))
            EntioResult.Success(Unit)
        } catch (failure: Exception) {
            failure("domain-provenance-rollback-failed", "Domain reuse provenance rollback failed.", failure)
        }
    }

    private fun restore(paths: ProvenancePaths, journal: Journal): Unit {
        val original = Files.readAllBytes(paths.original)
        require(sha256(original) == journal.originalFingerprint) { "domain-provenance-original-corrupt" }
        if (journal.originalExisted) replaceAtomically(paths.events, original) else Files.deleteIfExists(paths.events)
        discardPrepared(paths)
    }

    private fun parseEvent(line: String, lineNumber: Int): DomainReuseProvenanceEvent {
        val map = loader.loadFromString(line) as? Map<*, *> ?: error("Invalid provenance line $lineNumber")
        fun string(key: String): String = map[key] as? String ?: error("Missing $key on provenance line $lineNumber")
        fun strings(key: String): List<String> = (map[key] as? List<*>)?.map { it as String } ?: emptyList()
        val triples = (map["sourceSnapshot"] as? List<*>)?.map { parseTriple(it as Map<*, *>) }.orEmpty()
        val event = DomainReuseProvenanceEvent(
            recordId = string("recordId"),
            eventKind = DomainReuseEventKind.valueOf(string("eventKind")),
            sourceId = string("sourceId"),
            release = string("release"),
            packageFingerprint = string("packageFingerprint"),
            recordFingerprint = string("recordFingerprint"),
            canonicalIri = Iri(string("canonicalIri")),
            entityKind = ExternalEntityKind.valueOf(string("entityKind")),
            sourceOntologyIri = Iri(string("sourceOntologyIri")),
            sourcePath = string("sourcePath"),
            sourceStatementFingerprint = string("sourceStatementFingerprint"),
            sourceSnapshot = triples,
            omittedSourceAxioms = strings("omittedSourceAxioms"),
            dependencySetFingerprint = string("dependencySetFingerprint"),
            targetManagedSourceId = string("targetManagedSourceId"),
            proposalId = string("proposalId"),
            appliedChangeSetId = string("appliedChangeSetId"),
            actorId = string("actorId"),
            appliedAt = string("appliedAt"),
            baselineProjectFingerprint = string("baselineProjectFingerprint"),
            resultingProjectFingerprint = string("resultingProjectFingerprint"),
            projectStatementFingerprint = string("projectStatementFingerprint"),
            customization = DomainCustomizationClassification.valueOf(string("customization")),
            priorRecordId = map["priorRecordId"] as? String,
            checksum = string("checksum"),
        )
        require(withChecksum(event).checksum == event.checksum) { "Invalid provenance checksum on line $lineNumber" }
        return event
    }

    private fun withChecksum(event: DomainReuseProvenanceEvent): DomainReuseProvenanceEvent =
        event.copy(checksum = sha256(eventPayload(event).toByteArray(Charsets.UTF_8)))

    private fun serializeEvent(event: DomainReuseProvenanceEvent): String {
        val normalized = withChecksum(event)
        return eventPayload(normalized).dropLast(1) + ",\"checksum\":\"${normalized.checksum}\"}"
    }

    private fun eventPayload(event: DomainReuseProvenanceEvent): String = buildString {
        append("{\"schema\":\"entio-domain-reuse-provenance-v1\"")
        field("recordId", event.recordId)
        field("eventKind", event.eventKind.name)
        field("sourceId", event.sourceId)
        field("release", event.release)
        field("packageFingerprint", event.packageFingerprint)
        field("recordFingerprint", event.recordFingerprint)
        field("canonicalIri", event.canonicalIri.value)
        field("entityKind", event.entityKind.name)
        field("sourceOntologyIri", event.sourceOntologyIri.value)
        field("sourcePath", event.sourcePath)
        field("sourceStatementFingerprint", event.sourceStatementFingerprint)
        append(",\"sourceSnapshot\":[${event.sourceSnapshot.sortedBy(::stableTriple).joinToString(",", transform = ::serializeTriple)}]")
        append(",\"omittedSourceAxioms\":${jsonArray(event.omittedSourceAxioms.sorted())}")
        field("dependencySetFingerprint", event.dependencySetFingerprint)
        field("targetManagedSourceId", event.targetManagedSourceId)
        field("proposalId", event.proposalId)
        field("appliedChangeSetId", event.appliedChangeSetId)
        field("actorId", event.actorId)
        field("appliedAt", event.appliedAt)
        field("baselineProjectFingerprint", event.baselineProjectFingerprint)
        field("resultingProjectFingerprint", event.resultingProjectFingerprint)
        field("projectStatementFingerprint", event.projectStatementFingerprint)
        field("customization", event.customization.name)
        event.priorRecordId?.let { field("priorRecordId", it) }
        append('}')
    }

    private fun StringBuilder.field(name: String, value: String): Unit {
        append(",\"").append(name).append("\":\"").append(jsonEscape(value)).append('"')
    }

    private fun serializeTriple(triple: GraphTriple): String = buildString {
        append("{\"subject\":\"").append(jsonEscape(triple.subjectResource.value)).append('"')
        append(",\"predicate\":\"").append(jsonEscape(triple.predicate.value)).append('"')
        when (val term = triple.objectTerm) {
            is RdfResource -> {
                append(",\"objectKind\":\"iri\",\"object\":\"").append(jsonEscape(term.value)).append('"')
            }
            is RdfLiteral -> {
                append(",\"objectKind\":\"literal\",\"object\":\"").append(jsonEscape(term.lexicalForm)).append('"')
                term.datatypeIri?.let { append(",\"datatype\":\"").append(jsonEscape(it.value)).append('"') }
                term.languageTag?.let { append(",\"language\":\"").append(jsonEscape(it)).append('"') }
            }
        }
        append('}')
    }

    private fun parseTriple(map: Map<*, *>): GraphTriple {
        val subject = Iri(map["subject"] as String)
        val predicate = Iri(map["predicate"] as String)
        val objectValue = map["object"] as String
        val objectTerm: RdfTerm = if (map["objectKind"] == "iri") {
            Iri(objectValue)
        } else {
            RdfLiteral(objectValue, (map["datatype"] as? String)?.let(::Iri), map["language"] as? String)
        }
        return GraphTriple(subject, predicate, objectTerm)
    }

    private fun writeJournal(paths: ProvenancePaths, journal: Journal): Unit {
        val values = linkedMapOf<String, Any>(
            "schema" to JOURNAL_SCHEMA,
            "state" to journal.state.name,
            "originalExisted" to journal.originalExisted,
            "originalFingerprint" to journal.originalFingerprint,
            "intendedFingerprint" to journal.intendedFingerprint,
            "baselineProjectFingerprint" to journal.baselineProjectFingerprint,
            "resultingProjectFingerprint" to journal.resultingProjectFingerprint,
        )
        replaceAtomically(paths.journal, dumper.dumpToString(values).toByteArray(Charsets.UTF_8))
    }

    private fun readJournal(paths: ProvenancePaths): Journal {
        verifyRegular(paths.journal)
        val map = loader.loadFromString(Files.readString(paths.journal)) as Map<*, *>
        require(map["schema"] == JOURNAL_SCHEMA) { "domain-provenance-journal-schema" }
        return Journal(
            JournalState.valueOf(map["state"] as String),
            map["originalExisted"] as Boolean,
            map["originalFingerprint"] as String,
            map["intendedFingerprint"] as String,
            map["baselineProjectFingerprint"] as String,
            map["resultingProjectFingerprint"] as String,
        )
    }

    private fun paths(projectRoot: Path): ProvenancePaths {
        val root = projectRoot.toAbsolutePath().normalize()
        val directory = root.resolve(".entio/domain-reuse").normalize()
        require(directory.startsWith(root)) { "domain-provenance-path-unsafe" }
        return ProvenancePaths(
            directory,
            root.resolve(DomainOntologyProfileIdentity.PROVENANCE_PATH).normalize(),
            directory.resolve("apply-transaction-v1.yaml"),
            directory.resolve("apply-original-v1.bin"),
            directory.resolve("apply-intended-v1.jsonl"),
        )
    }

    private fun writeNew(path: Path, bytes: ByteArray): Unit {
        verifyAbsent(path)
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    }

    private fun replaceAtomically(path: Path, bytes: ByteArray): Unit {
        Files.createDirectories(path.parent)
        val temporary = Files.createTempFile(path.parent, ".${path.fileName}.", ".tmp")
        try {
            Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING)
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun discardPrepared(paths: ProvenancePaths): Unit {
        Files.deleteIfExists(paths.journal)
        Files.deleteIfExists(paths.original)
        Files.deleteIfExists(paths.intended)
    }

    private fun verifyDirectory(path: Path): Unit {
        require(!Files.isSymbolicLink(path) && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            "domain-provenance-directory-unsafe"
        }
    }

    private fun verifyRegular(path: Path): Unit {
        require(!Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            "domain-provenance-file-unsafe"
        }
    }

    private fun verifyAbsent(path: Path): Unit {
        require(!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) { "domain-provenance-transaction-file-exists" }
    }

    private fun failure(code: String, message: String, cause: Throwable? = null): EntioResult.Failure = EntioResult.Failure(
        message,
        listOf(ValidationIssue(ValidationSeverity.Error, code, message, "domain-reuse-provenance")),
        cause,
    )

    private data class ProvenancePaths(
        val directory: Path,
        val events: Path,
        val journal: Path,
        val original: Path,
        val intended: Path,
    )

    private data class Journal(
        val state: JournalState,
        val originalExisted: Boolean,
        val originalFingerprint: String,
        val intendedFingerprint: String,
        val baselineProjectFingerprint: String,
        val resultingProjectFingerprint: String,
    )

    private enum class JournalState { Prepared, Committed }

    private companion object {
        const val JOURNAL_SCHEMA: String = "entio-domain-reuse-apply-transaction-v1"
        const val MAX_PROVENANCE_BYTES: Int = 16 * 1024 * 1024

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(Locale.ROOT, it) }

        fun stableTriple(triple: GraphTriple): String = listOf(
            triple.subjectResource.value,
            triple.predicate.value,
            when (val term = triple.objectTerm) {
                is RdfResource -> "I:${term.value}"
                is RdfLiteral -> "L:${term.lexicalForm}|${term.datatypeIri?.value.orEmpty()}|${term.languageTag.orEmpty()}"
            },
        ).joinToString("\u001F")

        fun jsonArray(values: List<String>): String = values.joinToString(prefix = "[", postfix = "]") {
            "\"${jsonEscape(it)}\""
        }

        fun jsonEscape(value: String): String = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
