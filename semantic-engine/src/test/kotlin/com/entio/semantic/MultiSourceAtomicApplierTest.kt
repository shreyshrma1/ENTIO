package com.entio.semantic

import com.entio.core.CreateClassEdit
import com.entio.core.EntioResult
import com.entio.core.GraphChange
import com.entio.core.GraphChangeKind
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.MultiSourceApplyStatus
import com.entio.core.OntologyFormat
import com.entio.core.ResolvedOntologySource
import com.entio.core.StagedChange
import com.entio.core.StagedChangeOperation
import com.entio.core.StagedChangeSet
import com.entio.core.StagedChangeSetStatus
import java.nio.file.Files
import kotlin.io.path.readBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiSourceAtomicApplierTest {
    private val parser = OntologyParser()

    @Test
    fun appliesAllSourcesOnlyAfterEveryTemporaryGraphVerifies(): Unit {
        val first = fixture("first", "@prefix ex: <https://example.com/> . ex:First a ex:Class .")
        val second = fixture("second", "@prefix ex: <https://example.com/> . ex:Second a ex:Class .")
        val firstGraph = parse(first)
        val secondGraph = parse(second)
        val firstAddition = GraphTriple(Iri("https://example.com/First"), Iri("https://example.com/label"), com.entio.core.RdfLiteral("First"))
        val secondAddition = GraphTriple(Iri("https://example.com/Second"), Iri("https://example.com/label"), com.entio.core.RdfLiteral("Second"))
        val targets = listOf(
            target("first", first, firstGraph, firstAddition),
            target("second", second, secondGraph, secondAddition),
        )

        val result = MultiSourceAtomicApplier().apply(targets)

        assertEquals(MultiSourceApplyStatus.Applied, result.status)
        assertEquals(2, result.changedFiles.size)
        assertTrue(parse(first).triples.any { it.subjectResource == firstAddition.subjectResource && it.predicate == firstAddition.predicate && it.objectTerm.lexicalValue() == "First" })
        assertTrue(parse(second).triples.any { it.subjectResource == secondAddition.subjectResource && it.predicate == secondAddition.predicate && it.objectTerm.lexicalValue() == "Second" })
    }

    @Test
    fun staleBaselineFailsBeforeMutationAndPostSaveFailureRestoresEverySource(): Unit {
        val first = fixture("first-stale", "@prefix ex: <https://example.com/> . ex:First a ex:Class .")
        val second = fixture("second-stale", "@prefix ex: <https://example.com/> . ex:Second a ex:Class .")
        val firstGraph = parse(first)
        val secondGraph = parse(second)
        val firstAddition = GraphTriple(Iri("https://example.com/First"), Iri("https://example.com/label"), com.entio.core.RdfLiteral("First"))
        val secondAddition = GraphTriple(Iri("https://example.com/Second"), Iri("https://example.com/label"), com.entio.core.RdfLiteral("Second"))
        val targets = listOf(
            target("first", first, firstGraph, firstAddition),
            target("second", second, secondGraph, secondAddition),
        )
        val beforeFirst = first.readBytes()
        val beforeSecond = second.readBytes()

        val stale = MultiSourceAtomicApplier().apply(targets.mapIndexed { index, target ->
            if (index == 1) target.copy(baselineFingerprint = "stale") else target
        })
        assertEquals(MultiSourceApplyStatus.Failed, stale.status)
        assertEquals(beforeFirst.toList(), first.readBytes().toList())
        assertEquals(beforeSecond.toList(), second.readBytes().toList())

        val rollback = MultiSourceAtomicApplier(postSaveVerification = { EntioResult.Failure("post-save impact mismatch") }).apply(targets)
        assertEquals(MultiSourceApplyStatus.RolledBack, rollback.status)
        assertEquals(beforeFirst.toList(), first.readBytes().toList())
        assertEquals(beforeSecond.toList(), second.readBytes().toList())
    }

    @Test
    fun rejectionPreservesStagedEntriesForCorrection(): Unit {
        val staged = StagedChangeSet(
            entries = listOf(
                StagedChange(
                    id = "change-1",
                    order = 0,
                    targetSourceId = "first",
                    summary = "Create class",
                    operation = StagedChangeOperation.TypedEdit(CreateClassEdit(Iri("https://example.com/NewClass"))),
                ),
            ),
            status = StagedChangeSetStatus.Previewed,
        )

        val rejected = MultiSourceAtomicApplier().reject(staged)

        assertEquals(staged.entries, rejected.entries)
        assertEquals(StagedChangeSetStatus.Ready, rejected.status)
    }

    private fun target(id: String, path: java.nio.file.Path, graph: GraphState, addition: GraphTriple): MultiSourceApplyTarget =
        MultiSourceApplyTarget(
            sourceId = id,
            path = path,
            baselineFingerprint = sha256(path.readBytes()),
            changeSet = com.entio.core.ChangeSet(listOf(GraphChange(GraphChangeKind.Addition, addition))),
            expectedGraph = GraphState(graph.triples + addition),
        )

    private fun fixture(id: String, content: String): java.nio.file.Path = Files.createTempFile("entio-$id", ".ttl").also { it.writeText(content) }

    private fun parse(path: java.nio.file.Path): GraphState = when (
        val result = parser.parse(ResolvedOntologySource(path.fileName.toString(), path, OntologyFormat.Turtle))
    ) {
        is EntioResult.Failure -> error(result.message)
        is EntioResult.Success -> result.value.graph
    }

    private fun sha256(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun com.entio.core.RdfTerm.lexicalValue(): String = when (this) {
        is com.entio.core.RdfLiteral -> lexicalForm
        is com.entio.core.RdfResource -> value
    }
}
