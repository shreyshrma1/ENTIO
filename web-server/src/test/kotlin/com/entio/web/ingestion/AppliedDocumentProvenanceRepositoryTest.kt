package com.entio.web.ingestion

import com.entio.core.AppliedDocumentApplyEvent
import com.entio.core.AppliedDocumentDecision
import com.entio.core.AppliedDocumentEvidence
import com.entio.core.AppliedDocumentIdentity
import com.entio.core.AppliedDocumentProvenance
import com.entio.core.DocumentEvidenceId
import com.entio.core.DocumentEvidenceType
import com.entio.core.DocumentExtractionMethod
import com.entio.core.DocumentId
import com.entio.core.DocumentRecommendationAction
import com.entio.core.DocumentRecommendationReviewStatus
import com.entio.core.DocumentTaskId
import com.entio.core.DocumentTextBlockId
import com.entio.web.contract.InMemoryProjectRegistry
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppliedDocumentProvenanceRepositoryTest {
    @Test
    fun recordsSurviveRestartAndTemporaryCleanupWithoutCrossProjectAccess(): Unit {
        val allowed = Files.createTempDirectory("entio-provenance-allowed")
        val projectA = Files.createDirectory(allowed.resolve("project-a"))
        val projectB = Files.createDirectory(allowed.resolve("project-b"))
        val registry = InMemoryProjectRegistry(setOf(allowed)).also {
            it.register("project-a", "A", projectA)
            it.register("project-b", "B", projectB)
        }
        val root = Files.createTempDirectory("entio-provenance-store")
        val temporaryRoot = Files.createTempDirectory("entio-provenance-temporary")
        val repository = AppliedDocumentProvenanceRepository(root, registry)
        val record = provenance()

        repository.save("project-a", listOf(record))
        val restarted = AppliedDocumentProvenanceRepository(root, registry)
        assertEquals(listOf(record), restarted.list("project-a"))
        assertTrue(restarted.list("project-b").isEmpty())
        assertFailsWith<IllegalArgumentException> { restarted.list("unknown") }

        DocumentTemporaryStorage(temporaryRoot).close()
        assertEquals(listOf(record), restarted.list("project-a"))
        assertFalse(root.startsWith(projectA))
        assertFalse(projectA.startsWith(root))
    }

    @Test
    fun pendingRecordsCommitAtomicallyAndRepositoryCannotOverlapProjects(): Unit {
        val allowed = Files.createTempDirectory("entio-provenance-overlap")
        val project = Files.createDirectory(allowed.resolve("project"))
        val registry = InMemoryProjectRegistry(setOf(allowed)).also {
            it.register("project-a", "A", project)
        }
        assertCode("provenance-root-overlaps-project") {
            AppliedDocumentProvenanceRepository(project.resolve("provenance"), registry)
        }

        val repository = AppliedDocumentProvenanceRepository(Files.createTempDirectory("entio-provenance-pending"), registry)
        val record = provenance()
        repository.beginPending(
            PendingDocumentProvenance("project-a", "proposal-1", "before", "after", listOf(record)),
        )
        assertEquals("proposal-1", repository.pending("project-a")?.proposalId)
        assertEquals(listOf(record), repository.commitPending("project-a"))
        assertNull(repository.pending("project-a"))
        assertEquals(listOf(record), repository.save("project-a", listOf(record)))
    }

    private fun provenance(): AppliedDocumentProvenance {
        val documentId = DocumentId("document-1")
        val recommendationId = "recommendation-1"
        return AppliedDocumentProvenance(
            recordId = "record-1",
            projectId = "project-a",
            taskId = DocumentTaskId("task-1"),
            document = AppliedDocumentIdentity(documentId, "a".repeat(64), "policy.txt"),
            evidence = listOf(
                AppliedDocumentEvidence(
                    evidenceId = DocumentEvidenceId("evidence-1"),
                    documentId = documentId,
                    pageNumber = null,
                    blockId = DocumentTextBlockId("block-1"),
                    startOffsetInBlock = 0,
                    endOffsetInBlock = 6,
                    exactExcerpt = "Policy",
                    extractionMethod = DocumentExtractionMethod.Text,
                    extractorVersion = "test-1",
                    confidence = 100,
                ),
            ),
            recommendationId = recommendationId,
            action = DocumentRecommendationAction.Confirm,
            decision = AppliedDocumentDecision(
                decisionId = "decision-1",
                recommendationId = recommendationId,
                actorUserId = "alice",
                decidedAt = Instant.parse("2026-01-01T00:00:00Z"),
                status = DocumentRecommendationReviewStatus.Accepted,
                clarification = null,
            ),
            modelId = null,
            promptVersion = null,
            confidence = 100,
            evidenceTypes = listOf(DocumentEvidenceType.Explicit),
            typedOperation = null,
            applyEvent = AppliedDocumentApplyEvent(
                proposalId = null,
                appliedByUserId = "alice",
                appliedAt = Instant.parse("2026-01-01T00:00:00Z"),
                baselineOntologyFingerprint = "before",
                resultingOntologyFingerprint = "before",
            ),
        )
    }

    private fun assertCode(code: String, block: () -> Unit): Unit {
        val failure = assertFailsWith<DocumentIngestionFailure> { block() }
        assertEquals(code, failure.code)
    }
}
