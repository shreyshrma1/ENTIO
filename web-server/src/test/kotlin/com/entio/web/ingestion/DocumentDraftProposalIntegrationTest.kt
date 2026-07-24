package com.entio.web.ingestion

import com.entio.core.CreateClassEdit
import com.entio.core.DocumentCandidateCategory
import com.entio.core.DocumentDraftProvenance
import com.entio.core.DocumentEvidence
import com.entio.core.DocumentEvidenceId
import com.entio.core.DocumentEvidenceReference
import com.entio.core.DocumentEvidenceType
import com.entio.core.DocumentExtractionMethod
import com.entio.core.DocumentId
import com.entio.core.DocumentRecommendation
import com.entio.core.DocumentRecommendationAction
import com.entio.core.DocumentRecommendationCategory
import com.entio.core.DocumentRecommendationReviewStatus
import com.entio.core.DocumentReviewDecision
import com.entio.core.DocumentTaskId
import com.entio.core.DocumentTextBlockId
import com.entio.core.Iri
import com.entio.core.LocatedDocumentTextBlock
import com.entio.core.RdfLiteral
import com.entio.semantic.DocumentDraftOperation
import com.entio.semantic.DocumentDraftTranslationContext
import com.entio.web.PreparedDocumentStagingItem
import com.entio.web.StagingWorkflowService
import com.entio.web.WebWorkflowFailure
import com.entio.web.contract.InMemoryProjectRegistry
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DocumentDraftProposalIntegrationTest {
    @Test
    fun stagesOneAtomicBatchWithFieldProvenanceAndNoSourceWrite(): Unit {
        val fixture = fixture()
        val service = StagingWorkflowService(fixture.registry)
        val before = fixture.source.readBytes()
        val items = (1..20).map(::prepared)

        val staged = service.stageDocumentBatch("simple", "alice", "task-1", "batch-1", items)

        assertEquals(20, staged.entries.size)
        assertTrue(staged.entries.all { it.documentDraftProvenance?.taskId == "task-1" })
        assertEquals(before.toList(), fixture.source.readBytes().toList())
        assertEquals(staged, service.stageDocumentBatch("simple", "alice", "task-1", "batch-1", items))
        assertEquals(
            "document-draft-batch-limit",
            assertFailsWith<WebWorkflowFailure> {
                service.stageDocumentBatch("simple", "alice", "task-1", "too-many", (1..21).map(::prepared))
            }.code,
        )
    }

    @Test
    fun validatesEveryItemBeforeMutationAndRejectsUnrelatedSharedStaging(): Unit {
        val fixture = fixture()
        val service = StagingWorkflowService(fixture.registry)
        val invalid = prepared(2).copy(
            targetSourceId = "missing",
            provenance = prepared(2).provenance.copy(targetSourceId = "missing"),
        )
        assertEquals(
            "unknown-source",
            assertFailsWith<WebWorkflowFailure> {
                service.stageDocumentBatch("simple", "alice", "task-1", "invalid", listOf(prepared(1), invalid))
            }.code,
        )
        assertTrue(service.snapshot("simple").entries.isEmpty())

        service.stageDocumentBatch("simple", "alice", "task-1", "first", listOf(prepared(1)))
        assertEquals(
            "document-draft-shared-staging-not-empty",
            assertFailsWith<WebWorkflowFailure> {
                service.stageDocumentBatch("simple", "alice", "task-2", "other", listOf(prepared(2, "task-2")))
            }.code,
        )
        assertEquals(1, service.snapshot("simple").entries.size)
    }

    @Test
    fun commitsDurableProvenanceOnlyAfterSuccessfulExistingApply(): Unit {
        val fixture = fixture()
        val repository = AppliedDocumentProvenanceRepository(Files.createTempDirectory("entio-applied-provenance"), fixture.registry)
        val coordinator = DocumentApplyProvenanceCoordinator(repository)
        val service = StagingWorkflowService(fixture.registry)
        service.installDocumentApplyHooks(coordinator)
        val candidate = candidate()
        coordinator.register("simple", "task-1", listOf(candidate))
        service.stageDocumentBatch("simple", "alice", "task-1", "batch", listOf(prepared(1)))

        val preview = service.preview("simple", "alice")
        assertEquals("READYFORREVIEW", preview.proposal?.status)
        assertTrue(repository.list("simple").isEmpty())
        assertFalse(Files.readString(fixture.source).contains("document-1"))

        service.approve("simple", "reviewer")
        assertEquals("APPLIED", service.apply("simple", "reviewer").status)

        val record = repository.list("simple").single()
        assertEquals("recommendation-1", record.recommendationId)
        assertEquals("reviewer", record.applyEvent.appliedByUserId)
        assertNotNull(record.applyEvent.proposalId)
        assertEquals("Customer", record.evidence.single().exactExcerpt)
        assertFalse(Files.readString(fixture.source).contains("recommendation-1"))
        assertFalse(Files.readString(fixture.source).contains("document-1"))
    }

    @Test
    fun provenanceFailureUsesExistingRollbackAndClearsPendingEvent(): Unit {
        val fixture = fixture()
        val repository = AppliedDocumentProvenanceRepository(Files.createTempDirectory("entio-rollback-provenance"), fixture.registry)
        val coordinator = object : DocumentApplyHooks {
            override fun begin(
                projectId: String,
                proposalId: String,
                baselineFingerprint: String,
                expectedFingerprint: String,
                staged: List<com.entio.core.StagedChange>,
                appliedByUserId: String,
            ) = Unit

            override fun commit(projectId: String): Unit = error("simulated provenance failure")
            override fun rolledBack(projectId: String): Unit = repository.discardPending(projectId)
        }
        val service = StagingWorkflowService(fixture.registry)
        service.installDocumentApplyHooks(coordinator)
        val before = fixture.source.readBytes()
        service.stageDocumentBatch("simple", "alice", "task-1", "batch", listOf(prepared(1)))
        service.preview("simple", "alice")
        service.approve("simple", "reviewer")

        assertEquals("ROLLEDBACK", service.apply("simple", "reviewer").status)
        assertEquals(before.toList(), fixture.source.readBytes().toList())
        assertTrue(repository.list("simple").isEmpty())
        assertEquals(null, repository.pending("simple"))
    }

    private fun prepared(index: Int, taskId: String = "task-1"): PreparedDocumentStagingItem =
        PreparedDocumentStagingItem(
            summary = "Create document class $index",
            editType = "create-class",
            targetSourceId = "simple",
            operation = DocumentDraftOperation.Ontology(
                CreateClassEdit(Iri("$NS#DocumentClass$index"), RdfLiteral("Document class $index")),
            ),
            provenance = provenance(index, taskId),
        )

    private fun provenance(index: Int, taskId: String): DocumentDraftProvenance = DocumentDraftProvenance(
        taskId = DocumentTaskId(taskId),
        recommendationId = "recommendation-$index",
        decisionId = "decision-$index",
        evidenceIds = listOf(DocumentEvidenceId("evidence-1")),
        modelId = "gpt-test",
        promptVersion = "prompt-v1",
        extractionMethods = listOf(DocumentExtractionMethod.Text),
        confidence = 90,
        targetSourceId = "simple",
        normalizedTypedOperationKey = "create-class-$index",
    )

    private fun candidate(): DocumentReviewDraftCandidate {
        val reference = DocumentEvidenceReference(
            DocumentEvidenceId("evidence-1"),
            DocumentId("document-1"),
            DocumentTextBlockId("block-1"),
            startOffsetInBlock = 0,
            endOffsetInBlock = 8,
            exactExcerpt = "Customer",
            extractionMethod = DocumentExtractionMethod.Text,
        )
        val recommendation = DocumentRecommendation(
            id = "recommendation-1",
            candidateIds = listOf("candidate-1"),
            type = DocumentCandidateCategory.Class,
            category = DocumentRecommendationCategory.OntologyStructure,
            proposedLabel = "Document class 1",
            action = DocumentRecommendationAction.CreateLocal,
            confidence = 90,
            rationale = "Verified source evidence.",
            evidence = listOf(
                DocumentEvidence(DocumentEvidenceId("evidence-group"), DocumentEvidenceType.Explicit, listOf(reference)),
            ),
            targetSourceId = "simple",
            modelId = "gpt-test",
            promptVersion = "prompt-v1",
        )
        val block = LocatedDocumentTextBlock(
            DocumentTextBlockId("block-1"),
            DocumentId("document-1"),
            "policy.txt",
            blockOrder = 0,
            startOffset = 0,
            endOffset = 15,
            exactText = "Customer policy",
            extractionMethod = DocumentExtractionMethod.Text,
            extractorVersion = "extractor-v1",
        )
        return DocumentReviewDraftCandidate(
            recommendation,
            DocumentDraftTranslationContext("simple", targetIri = Iri("$NS#DocumentClass1"), acceptedForDraft = true),
            DocumentReviewDecision(
                "decision-1",
                "recommendation-1",
                "alice",
                Instant.parse("2026-07-24T12:00:00Z"),
                DocumentRecommendationReviewStatus.Pending,
                DocumentRecommendationReviewStatus.Accepted,
            ),
            listOf(
                DocumentIngestionDocumentSnapshot(
                    "document-1",
                    "policy.txt",
                    "text",
                    15,
                    "a".repeat(64),
                    "authoritative",
                    "awaiting-review",
                ),
            ),
            mapOf("block-1" to block),
        )
    }

    private fun fixture(): Fixture {
        val allowed = Files.createTempDirectory("entio-document-draft")
        val root = Files.createDirectory(allowed.resolve("simple"))
        val ontology = Files.createDirectories(root.resolve("ontology"))
        Files.writeString(
            root.resolve("entio.yaml"),
            """
            name: simple-ontology
            iriNamespace: $NS#
            ontologySources:
              - id: simple
                path: ontology/simple.ttl
                format: turtle
                roles: [ontology, data]
            """.trimIndent(),
        )
        val source = ontology.resolve("simple.ttl")
        Files.writeString(
            source,
            """
            @prefix ex: <$NS#> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            ex:Customer a owl:Class ; rdfs:label "Customer" .
            """.trimIndent(),
        )
        val registry = InMemoryProjectRegistry(setOf(allowed)).also {
            it.register("simple", "Simple ontology", root)
        }
        return Fixture(root, source, registry)
    }

    private data class Fixture(
        val root: Path,
        val source: Path,
        val registry: InMemoryProjectRegistry,
    )

    private companion object {
        const val NS: String = "https://example.com/entio/simple"
    }
}
