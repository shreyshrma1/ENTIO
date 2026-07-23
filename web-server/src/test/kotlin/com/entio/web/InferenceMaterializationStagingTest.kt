package com.entio.web

import com.entio.core.AddObjectPropertyAssertionEdit
import com.entio.core.AddSuperclassEdit
import com.entio.core.AssignTypeEdit
import com.entio.core.GraphTriple
import com.entio.core.InferenceFactId
import com.entio.core.InferenceImportDependence
import com.entio.core.InferenceImportDependenceState
import com.entio.core.InferenceMaterializationFact
import com.entio.core.InferenceMaterializationKind
import com.entio.core.InferenceMaterializationProvenance
import com.entio.core.Iri
import com.entio.core.PreparedInferenceMaterialization
import com.entio.core.PreparedInferenceMaterializationBatch
import com.entio.core.SemanticFactKey
import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebStageChangeRequest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class InferenceMaterializationStagingTest {
    private val fixedInstant = Instant.parse("2026-07-23T15:00:00Z")

    @Test
    fun appendsOneOrManyItemsAtomicallyWithServerProvenance(): Unit {
        val fixture = fixture()
        val before = fixture.source.readBytes()
        val service = service(fixture)
        val items = listOf(
            prepared(1, InferenceMaterializationKind.SubclassRelationship),
            prepared(2, InferenceMaterializationKind.IndividualType),
            prepared(3, InferenceMaterializationKind.ObjectPropertyAssertion),
        )

        val result = service.stageMaterializations(
            "simple",
            "server-user",
            "batch-1",
            PreparedInferenceMaterializationBatch(items),
        )

        assertEquals(listOf("stage-1", "stage-2", "stage-3"), result.batch.mappings.map { it.stagedChangeId })
        assertEquals(3, result.staging.entries.size)
        result.staging.entries.forEach { entry ->
            assertEquals("server-user", entry.authorId)
            assertEquals("server-user", entry.materializationProvenance?.stagedByUserId)
            assertEquals(fixedInstant.toString(), entry.materializationProvenance?.stagedAt)
            assertEquals("MaterializedFromReasoning", entry.materializationProvenance?.origin)
        }
        assertEquals(before.toList(), fixture.source.readBytes().toList())
    }

    @Test
    fun validationFailureLeavesQueueProposalAndIdempotencyReusable(): Unit {
        (0..2).forEach { invalidIndex ->
            val fixture = fixture()
            val service = service(fixture)
            val validItems = listOf(
                prepared(1, InferenceMaterializationKind.SubclassRelationship),
                prepared(2, InferenceMaterializationKind.IndividualType),
                prepared(3, InferenceMaterializationKind.ObjectPropertyAssertion),
            )
            val original = validItems[invalidIndex]
            val invalid = original.copy(
                targetSourceId = "missing",
                provenance = original.provenance.copy(targetSourceId = "missing"),
            )
            val submitted = validItems.toMutableList().also { it[invalidIndex] = invalid }

            val failure = assertFailsWith<WebWorkflowFailure> {
                service.stageMaterializations(
                    "simple",
                    "alice",
                    "reusable-key",
                    PreparedInferenceMaterializationBatch(submitted),
                )
            }
            assertEquals("unknown-source", failure.code)
            assertEquals(0, service.snapshot("simple").entries.size)
            assertNull(service.snapshot("simple").proposal)

            val success = service.stageMaterializations(
                "simple",
                "alice",
                "reusable-key",
                PreparedInferenceMaterializationBatch(validItems),
            )
            assertEquals(3, success.staging.entries.size)
        }
    }

    @Test
    fun handlesMixedAndAllExistingBatchesWithoutDuplicateEntries(): Unit {
        val fixture = fixture()
        val service = service(fixture)
        val first = prepared(1, InferenceMaterializationKind.SubclassRelationship)
        val second = prepared(2, InferenceMaterializationKind.IndividualType)
        val initial = service.stageMaterializations(
            "simple",
            "alice",
            "initial",
            PreparedInferenceMaterializationBatch(listOf(first)),
        )

        val mixed = service.stageMaterializations(
            "simple",
            "alice",
            "mixed",
            PreparedInferenceMaterializationBatch(listOf(first, second)),
        )
        assertEquals(2, mixed.staging.entries.size)
        assertEquals(initial.batch.mappings.single().stagedChangeId, mixed.batch.mappings.first().stagedChangeId)

        val allExisting = service.stageMaterializations(
            "simple",
            "alice",
            "all-existing",
            PreparedInferenceMaterializationBatch(listOf(first, second)),
        )
        assertEquals(mixed.batch.mappings, allExisting.batch.mappings)
        assertEquals(2, allExisting.staging.entries.size)
    }

    @Test
    fun rejectsAppliedFactsAndSupportsIdenticalButNotConflictingReplay(): Unit {
        val fixture = fixture()
        val service = service(fixture)
        val asserted = prepared(
            9,
            InferenceMaterializationKind.SubclassRelationship,
            subject = Iri("$NS#Checking"),
            objectValue = Iri("$NS#Account"),
        )
        assertEquals(
            "inference-already-asserted",
            assertFailsWith<WebWorkflowFailure> {
                service.stageMaterializations(
                    "simple",
                    "alice",
                    "asserted",
                    PreparedInferenceMaterializationBatch(listOf(asserted)),
                )
            }.code,
        )

        val first = prepared(1, InferenceMaterializationKind.SubclassRelationship)
        val initial = service.stageMaterializations(
            "simple",
            "alice",
            "replay",
            PreparedInferenceMaterializationBatch(listOf(first)),
        )
        val replay = service.stageMaterializations(
            "simple",
            "alice",
            "replay",
            PreparedInferenceMaterializationBatch(listOf(first)),
        )
        assertEquals(initial.batch, replay.batch)
        assertEquals(1, replay.staging.entries.size)

        val conflict = prepared(2, InferenceMaterializationKind.IndividualType)
        assertEquals(
            "idempotency-conflict",
            assertFailsWith<WebWorkflowFailure> {
                service.stageMaterializations(
                    "simple",
                    "alice",
                    "replay",
                    PreparedInferenceMaterializationBatch(listOf(first, conflict)),
                )
            }.code,
        )
        assertEquals(1, service.snapshot("simple").entries.size)
    }

    @Test
    fun ordinaryStagingRemainsUnchangedAndProposalUsesTypedEdits(): Unit {
        val fixture = fixture()
        val service = service(fixture)
        val ordinary = service.stage(
            "simple",
            WebStageChangeRequest(
                sourceId = "simple",
                editType = "set-entity-label",
                resourceIri = "$NS#Loan",
                label = "Loan",
            ),
            "alice",
        )
        assertNull(ordinary.entries.single().materializationProvenance)

        service.stageMaterializations(
            "simple",
            "alice",
            "materialization",
            PreparedInferenceMaterializationBatch(
                listOf(prepared(1, InferenceMaterializationKind.SubclassRelationship)),
            ),
        )
        val preview = service.preview("simple", "alice")
        assertEquals(2, preview.entries.size)
        assertEquals(true, preview.proposal?.diff.orEmpty().any { it.subject == "$NS#Loan" })
    }

    @Test
    fun retainsRequestOrderAcrossMultipleTargetSources(): Unit {
        val fixture = fixture(includeSecondarySource = true)
        val service = service(fixture)
        val first = prepared(1, InferenceMaterializationKind.SubclassRelationship)
        val secondBase = prepared(2, InferenceMaterializationKind.IndividualType)
        val second = secondBase.copy(
            targetSourceId = "secondary",
            provenance = secondBase.provenance.copy(targetSourceId = "secondary"),
        )

        val result = service.stageMaterializations(
            "simple",
            "alice",
            "multi-source",
            PreparedInferenceMaterializationBatch(listOf(first, second)),
        )

        assertEquals(listOf(1, 2), result.staging.entries.map { it.order })
        assertEquals(listOf("simple", "secondary"), result.staging.entries.map { it.sourceId })
        assertEquals(listOf(first.factId, second.factId), result.batch.mappings.map { it.factId })
    }

    private fun service(fixture: Fixture): StagingWorkflowService = StagingWorkflowService(
        projectRegistry = fixture.registry,
        clock = Clock.fixed(fixedInstant, ZoneOffset.UTC),
    )

    private fun prepared(
        index: Int,
        kind: InferenceMaterializationKind,
        subject: Iri = when (kind) {
            InferenceMaterializationKind.SubclassRelationship -> Iri("$NS#Loan")
            InferenceMaterializationKind.IndividualType -> Iri("$NS#Loan123")
            InferenceMaterializationKind.ObjectPropertyAssertion -> Iri("$NS#Shrey")
        },
        objectValue: Iri = when (kind) {
            InferenceMaterializationKind.SubclassRelationship -> Iri("$NS#Account")
            InferenceMaterializationKind.IndividualType -> Iri("$NS#Account")
            InferenceMaterializationKind.ObjectPropertyAssertion -> Iri("$NS#Account101")
        },
    ): PreparedInferenceMaterialization {
        val predicate = when (kind) {
            InferenceMaterializationKind.SubclassRelationship -> RDFS_SUBCLASS
            InferenceMaterializationKind.IndividualType -> RDF_TYPE
            InferenceMaterializationKind.ObjectPropertyAssertion -> Iri("$NS#hasLoan")
        }
        val fact = InferenceMaterializationFact(kind, subject, predicate, objectValue)
        val factId = InferenceFactId("entio-reasoning-fact-v1:${index.toString(16).padStart(64, '0')}")
        val semanticKey = SemanticFactKey("entio-semantic-fact-v1:${(index + 100).toString(16).padStart(64, '0')}")
        val edit = when (kind) {
            InferenceMaterializationKind.SubclassRelationship -> AddSuperclassEdit(subject, objectValue)
            InferenceMaterializationKind.IndividualType -> AssignTypeEdit(subject, objectValue)
            InferenceMaterializationKind.ObjectPropertyAssertion ->
                AddObjectPropertyAssertionEdit(subject, predicate, objectValue)
        }
        val provenance = InferenceMaterializationProvenance(
            inferenceKind = kind,
            reasoningJobId = "job-1",
            graphFingerprint = "graph-1",
            factId = factId,
            semanticFactKey = semanticKey,
            fact = fact,
            stagedByUserId = "caller-supplied",
            stagedAt = Instant.EPOCH,
            targetSourceId = "simple",
            importDependence = InferenceImportDependence(InferenceImportDependenceState.LocalOnly),
        )
        return PreparedInferenceMaterialization(
            factId = factId,
            semanticFactKey = semanticKey,
            fact = fact,
            targetSourceId = "simple",
            edit = edit,
            triple = GraphTriple(subject, predicate, objectValue),
            provenance = provenance,
        )
    }

    private fun fixture(includeSecondarySource: Boolean = false): Fixture {
        val root = Files.createTempDirectory("entio-phase10-staging")
        val sourceRoot = Path.of("../examples/simple-ontology").toAbsolutePath().normalize()
        Files.walk(sourceRoot).use { paths ->
            paths.forEach { source ->
                val target = root.resolve(sourceRoot.relativize(source).toString())
                if (Files.isDirectory(source)) Files.createDirectories(target) else Files.copy(source, target)
            }
        }
        if (includeSecondarySource) {
            Files.writeString(
                root.resolve("entio.yaml"),
                "\n  - id: secondary\n    path: ontology/secondary.ttl\n    format: turtle\n    roles:\n      - ontology\n      - data\n",
                StandardOpenOption.APPEND,
            )
            Files.writeString(
                root.resolve("ontology/secondary.ttl"),
                "<https://example.com/entio/secondary#> a <http://www.w3.org/2002/07/owl#Ontology> .\n",
            )
        }
        val registry = InMemoryProjectRegistry(setOf(root.parent))
        registry.register("simple", "Simple", root)
        return Fixture(registry, root.resolve("ontology/simple.ttl"))
    }

    private data class Fixture(
        val registry: InMemoryProjectRegistry,
        val source: Path,
    )

    private companion object {
        private const val NS = "https://example.com/entio/simple"
        private val RDF_TYPE = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
        private val RDFS_SUBCLASS = Iri("http://www.w3.org/2000/01/rdf-schema#subClassOf")
    }
}
