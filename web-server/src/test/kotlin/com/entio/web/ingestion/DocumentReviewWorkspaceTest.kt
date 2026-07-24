package com.entio.web.ingestion

import com.entio.core.DocumentAuthorityMetadata
import com.entio.core.DocumentAuthorityStatus
import com.entio.core.DocumentCandidateCategory
import com.entio.core.DocumentEvidence
import com.entio.core.DocumentEvidenceId
import com.entio.core.DocumentEvidenceReference
import com.entio.core.DocumentEvidenceType
import com.entio.core.DocumentExtractionMethod
import com.entio.core.DocumentId
import com.entio.core.DocumentMatchCandidate
import com.entio.core.DocumentMatchScope
import com.entio.core.DocumentMediaType
import com.entio.core.DocumentProcessingStatus
import com.entio.core.DocumentRecommendation
import com.entio.core.DocumentRecommendationAction
import com.entio.core.DocumentRecommendationCategory
import com.entio.core.DocumentTaskId
import com.entio.core.DocumentTextBlockId
import com.entio.core.IngestionDocument
import com.entio.core.Iri
import com.entio.core.LocatedDocumentTextBlock
import com.entio.web.contract.WebPageRequest
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentReviewWorkspaceTest {
    @Test
    fun keepsReadsBoundedAuthorizedAndFreeOfFullDocumentText(): Unit {
        val store = fixture()

        val firstPage = store.read("project-a", "task-1", "alice", WebPageRequest(0, 1))
        assertEquals(1, firstPage.recommendations.items.size)
        assertEquals(2, firstPage.recommendations.total)
        assertEquals(1, firstPage.recommendations.nextOffset)
        assertTrue(firstPage.draftImpact.readOnly)
        assertFailsWith<DocumentIngestionFailure> {
            store.read("project-a", "task-1", "bob", WebPageRequest())
        }
        assertFailsWith<DocumentIngestionFailure> {
            store.read("project-b", "task-1", "alice", WebPageRequest())
        }

        val json = ObjectMapper().findAndRegisterModules().writeValueAsString(firstPage)
        assertFalse(json.contains(FULL_TEXT_SUFFIX))
        assertFalse(json.contains("/private/server/path"))
        assertFalse(json.contains("provider-payload"))

        val evidence = store.evidence("project-a", "task-1", "alice", "evidence-1")
        assertEquals("Customer", evidence.text.substring(evidence.highlightStart, evidence.highlightEnd))
        assertEquals("Text", evidence.extractionMethod)
        assertFalse(evidence.pageImageAvailable)
    }

    @Test
    fun supportsReviewChoicesAndRejectsInvalidOrStaleTransitions(): Unit {
        val store = fixture()
        val base = request("accept")

        assertEquals(
            "Accepted",
            store.decide("project-a", "task-1", "recommendation-1", "alice", base)
                .recommendations.items.first { it.id == "recommendation-1" }.reviewStatus,
        )
        assertEquals(
            "Rejected",
            store.decide("project-a", "task-1", "recommendation-1", "alice", request("reject"))
                .recommendations.items.first { it.id == "recommendation-1" }.reviewStatus,
        )
        val edited = store.decide(
            "project-a",
            "task-1",
            "recommendation-1",
            "alice",
            request("edit").copy(proposedLabel = "Business customer", targetSourceId = "ontology"),
        ).recommendations.items.first { it.id == "recommendation-1" }
        assertEquals("Business customer", edited.proposedLabel)
        assertEquals("ontology", edited.targetSourceId)

        val rematched = store.decide(
            "project-a",
            "task-1",
            "recommendation-1",
            "alice",
            request("rematch").copy(selectedMatchIri = "https://example.com/Customer"),
        ).recommendations.items.first { it.id == "recommendation-1" }
        assertEquals("https://example.com/Customer", rematched.selectedMatchIri)

        assertEquals(
            "NeedsClarification",
            store.decide(
                "project-a",
                "task-1",
                "recommendation-1",
                "alice",
                request("clarify").copy(clarification = "Confirm the applicable customer population."),
            ).recommendations.items.first { it.id == "recommendation-1" }.reviewStatus,
        )
        repeat(3) {
            store.decide(
                "project-a",
                "task-1",
                "recommendation-1",
                "alice",
                request("reconsider").copy(clarification = "Recheck bounded evidence ${it + 1}."),
            )
        }
        assertCode("document-reconsideration-limit") {
            store.decide(
                "project-a",
                "task-1",
                "recommendation-1",
                "alice",
                request("reconsider").copy(clarification = "One more."),
            )
        }

        val merged = store.decide(
            "project-a",
            "task-1",
            "recommendation-1",
            "alice",
            request("merge").copy(mergedRecommendationIds = listOf("recommendation-2")),
        )
        assertEquals("Rejected", merged.recommendations.items.first { it.id == "recommendation-2" }.reviewStatus)
        assertCode("document-review-stale") {
            store.decide(
                "project-a",
                "task-1",
                "recommendation-1",
                "alice",
                base.copy(expectedGraphFingerprint = "changed"),
            )
        }
        assertCode("document-match-invalid") {
            store.decide(
                "project-a",
                "task-1",
                "recommendation-1",
                "alice",
                request("rematch").copy(selectedMatchIri = "https://example.com/Unknown"),
            )
        }
    }

    private fun fixture(): DocumentReviewWorkspaceStore {
        val now = Instant.parse("2026-07-24T12:00:00Z")
        val store = DocumentReviewWorkspaceStore(Clock.fixed(now, ZoneOffset.UTC))
        val documentId = DocumentId("document-1")
        val blockId = DocumentTextBlockId("block-1")
        val text = "Customer policy applies. $FULL_TEXT_SUFFIX"
        val document = IngestionDocument(
            id = documentId,
            taskId = DocumentTaskId("task-1"),
            safeFilename = "policy.txt",
            mediaType = DocumentMediaType.Text,
            byteSize = text.length.toLong(),
            checksumSha256 = "a".repeat(64),
            projectId = "project-a",
            uploaderUserId = "alice",
            uploadedAt = now,
            authority = DocumentAuthorityMetadata(DocumentAuthorityStatus.Authoritative),
            status = DocumentProcessingStatus.AwaitingReview,
        )
        val block = LocatedDocumentTextBlock(
            id = blockId,
            documentId = documentId,
            safeFilename = "policy.txt",
            blockOrder = 0,
            startOffset = 0,
            endOffset = text.length,
            exactText = text,
            extractionMethod = DocumentExtractionMethod.Text,
            extractorVersion = "test-extractor",
        )
        val evidenceReference = DocumentEvidenceReference(
            id = DocumentEvidenceId("evidence-1"),
            documentId = documentId,
            blockId = blockId,
            startOffsetInBlock = 0,
            endOffsetInBlock = 8,
            exactExcerpt = "Customer",
            extractionMethod = DocumentExtractionMethod.Text,
        )
        val evidence = DocumentEvidence(
            id = DocumentEvidenceId("evidence-group-1"),
            type = DocumentEvidenceType.Explicit,
            references = listOf(evidenceReference),
        )
        val match = DocumentMatchCandidate(
            scope = DocumentMatchScope.AppliedLocal,
            entityIri = Iri("https://example.com/Customer"),
            sourceId = "ontology",
            preferredLabel = "Customer",
            score = 100,
            reason = "Canonical local match.",
        )
        val recommendations = (1..2).map { index ->
            DocumentRecommendation(
                id = "recommendation-$index",
                candidateIds = listOf("candidate-$index"),
                type = DocumentCandidateCategory.Class,
                category = DocumentRecommendationCategory.OntologyStructure,
                proposedLabel = "Customer $index",
                action = DocumentRecommendationAction.Extend,
                confidence = 90,
                rationale = "The source explicitly describes a customer concept.",
                evidence = listOf(evidence),
                matches = listOf(match),
                selectedMatch = match,
                targetSourceId = "ontology",
            )
        }
        val task = DocumentIngestionTaskSnapshot(
            taskId = "task-1",
            projectId = "project-a",
            ownerUserId = "alice",
            status = "awaiting-review",
            createdAt = now.toString(),
            updatedAt = now.toString(),
            documents = listOf(
                DocumentIngestionDocumentSnapshot(
                    documentId = "document-1",
                    safeFilename = "policy.txt",
                    mediaType = "text",
                    byteSize = text.length.toLong(),
                    checksumSha256 = "a".repeat(64),
                    authorityStatus = "authoritative",
                    status = "awaiting-review",
                ),
            ),
            progress = DocumentIngestionProgress("awaiting-review", 1, 1, 100, "Recommendations are ready for review."),
        )
        store.install(
            DocumentReviewWorkspaceInput(
                task = task,
                exactWorkKey = "work-key",
                graphFingerprint = "graph-fingerprint",
                extractedDocuments = listOf(ExtractedDocument(document, listOf(block), emptyList(), emptyMap())),
                summaries = listOf(
                    VerifiedDocumentAnalysisSummary(
                        "document-1",
                        "Defines customer policy.",
                        listOf(VerifiedDocumentAnalysisHighlight("Customer", "candidate-1", listOf("evidence-1"))),
                    ),
                ),
                recommendations = recommendations,
                priorWorkflowProvenance = mapOf("recommendation-1" to listOf("applied-record-1")),
            ),
        )
        return store
    }

    private fun request(action: String): DocumentReviewDecisionRequest =
        DocumentReviewDecisionRequest(action, "work-key", "graph-fingerprint")

    private fun assertCode(code: String, block: () -> Unit): Unit {
        val failure = assertFailsWith<DocumentIngestionFailure> { block() }
        assertEquals(code, failure.code)
    }

    private companion object {
        const val FULL_TEXT_SUFFIX: String =
            "This sentence represents content outside the bounded evidence response and must not leak."
    }
}
