package com.entio.semantic

import com.entio.core.ChangePreview
import com.entio.core.ChangeSet
import com.entio.core.EntioResult
import com.entio.core.GraphState
import com.entio.core.MultiSourceApplyResult
import com.entio.core.MultiSourceApplyStatus
import com.entio.core.OntologyFormat
import com.entio.core.ResolvedOntologySource
import com.entio.core.SemanticEquivalenceResult
import com.entio.core.StagedChangeSet
import com.entio.core.StagedChangeSetStatus
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** One file participating in a combined proposal application. */
public data class MultiSourceApplyTarget(
    public val sourceId: String,
    public val path: Path,
    public val baselineFingerprint: String,
    public val changeSet: ChangeSet,
    public val expectedGraph: GraphState,
)

/** Applies all prepared source replacements or restores every original source. */
public class MultiSourceAtomicApplier(
    private val verifier: PreviewTurtleRoundTripVerifier = PreviewTurtleRoundTripVerifier(),
    private val parser: OntologyParser = OntologyParser(),
    private val postSaveVerification: (List<MultiSourceApplyTarget>) -> EntioResult<Unit> = { EntioResult.Success(Unit) },
) {
    public fun apply(targets: List<MultiSourceApplyTarget>): MultiSourceApplyResult {
        if (targets.isEmpty()) return failed("A multi-source proposal must target at least one source.")
        if (targets.map { it.sourceId }.distinct().size != targets.size) return failed("A multi-source proposal contains a duplicate source id.")
        if (targets.map { it.path.normalize() }.distinct().size != targets.size) return failed("A multi-source proposal contains a duplicate source path.")

        val originals = linkedMapOf<MultiSourceApplyTarget, ByteArray>()
        targets.forEach { target ->
            val bytes = try {
                Files.readAllBytes(target.path)
            } catch (exception: IOException) {
                return failed("Source '${target.sourceId}' could not be read before application.")
            }
            if (fingerprint(bytes) != target.baselineFingerprint) {
                return failed("Source '${target.sourceId}' has a stale baseline.")
            }
            originals[target] = bytes
        }

        val temporaryPaths = linkedMapOf<MultiSourceApplyTarget, Path>()
        try {
            targets.forEach { target ->
                val preview = ChangePreview(target.expectedGraph, target.changeSet)
                val temporary = when (val result = verifier.serializeToTemporaryTurtle(preview)) {
                    is EntioResult.Failure -> return failed(result.message)
                    is EntioResult.Success -> result.value.path
                }
                val reparsed = parseGraph(target, temporary)
                    ?: return failed("Temporary source '${target.sourceId}' could not be parsed.")
                when (val equivalence = verifier.compareSemanticEquivalence(target.expectedGraph, reparsed)) {
                    SemanticEquivalenceResult.Equivalent -> temporaryPaths[target] = temporary
                    is SemanticEquivalenceResult.Failed -> return failed(equivalence.reason)
                    is SemanticEquivalenceResult.NotEquivalent -> return failed(equivalence.reason)
                }
            }

            temporaryPaths.forEach { (target, temporary) -> moveReplacingTarget(temporary, target.path) }
            targets.forEach { target ->
                val reloaded = parseGraph(target, target.path)
                    ?: return rollback(targets, originals, "Saved source '${target.sourceId}' could not be reloaded.")
                when (val equivalence = verifier.compareSemanticEquivalence(target.expectedGraph, reloaded)) {
                    SemanticEquivalenceResult.Equivalent -> Unit
                    is SemanticEquivalenceResult.Failed -> return rollback(targets, originals, equivalence.reason)
                    is SemanticEquivalenceResult.NotEquivalent -> return rollback(targets, originals, equivalence.reason)
                }
            }
            when (val verification = postSaveVerification(targets)) {
                is EntioResult.Failure -> return rollback(targets, originals, verification.message)
                is EntioResult.Success -> Unit
            }
            return MultiSourceApplyResult(MultiSourceApplyStatus.Applied, targets.map { it.path.toString() })
        } catch (exception: IOException) {
            return rollback(targets, originals, exception.message ?: "Multi-source proposal application failed.")
        } finally {
            temporaryPaths.values.forEach { path -> Files.deleteIfExists(path) }
        }
    }

    /** Rejection leaves the in-memory staged entries available for correction. */
    public fun reject(stagedChangeSet: StagedChangeSet): StagedChangeSet = stagedChangeSet.copy(status = StagedChangeSetStatus.Ready)

    private fun parseGraph(target: MultiSourceApplyTarget, path: Path): GraphState? = when (
        val result = parser.parse(ResolvedOntologySource(target.sourceId, path, OntologyFormat.Turtle))
    ) {
        is EntioResult.Failure -> null
        is EntioResult.Success -> result.value.graph
    }

    private fun moveReplacingTarget(temporary: Path, target: Path): Unit {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun rollback(
        targets: List<MultiSourceApplyTarget>,
        originals: Map<MultiSourceApplyTarget, ByteArray>,
        reason: String,
    ): MultiSourceApplyResult {
        val restored = mutableListOf<String>()
        return try {
            targets.forEach { target ->
                Files.write(target.path, originals.getValue(target))
                restored += target.path.toString()
            }
            MultiSourceApplyResult(MultiSourceApplyStatus.RolledBack, reason = reason, restoredFiles = restored)
        } catch (exception: IOException) {
            MultiSourceApplyResult(
                status = MultiSourceApplyStatus.RollbackFailed,
                reason = "$reason Rollback failed: ${exception.message ?: "unknown error"}",
                restoredFiles = restored,
            )
        }
    }

    private fun failed(reason: String): MultiSourceApplyResult = MultiSourceApplyResult(MultiSourceApplyStatus.Failed, reason = reason)

    private fun fingerprint(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
