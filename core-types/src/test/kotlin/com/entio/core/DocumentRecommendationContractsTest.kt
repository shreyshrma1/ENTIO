package com.entio.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentRecommendationContractsTest {
    private val documentId = DocumentId("document-1")
    private val evidenceId = DocumentEvidenceId("evidence-1")
    private val evidence = DocumentEvidence(
        evidenceId,
        DocumentEvidenceType.Explicit,
        listOf(
            DocumentEvidenceReference(
                id = evidenceId,
                documentId = documentId,
                blockId = DocumentTextBlockId("block-1"),
                pageNumber = 1,
                startOffsetInBlock = 0,
                endOffsetInBlock = 12,
                exactExcerpt = "The borrower",
                extractionMethod = DocumentExtractionMethod.EmbeddedText,
            ),
        ),
    )

    @Test
    fun `constructs separate schema and fact candidates with stable confidence bands`(): Unit {
        val schema = candidate(DocumentCandidateCategory.Class, DocumentRecommendationCategory.OntologyStructure)
        val fact = candidate(DocumentCandidateCategory.Individual, DocumentRecommendationCategory.BusinessFact, confidence = 65)

        assertEquals(DocumentConfidenceBand.High, schema.confidenceBand)
        assertEquals(DocumentConfidenceBand.Medium, fact.confidenceBand)
        assertTrue(schema.stableOrderingKey < fact.stableOrderingKey || schema.stableOrderingKey > fact.stableOrderingKey)
        assertFailsWith<IllegalArgumentException> {
            candidate(DocumentCandidateCategory.Class, DocumentRecommendationCategory.BusinessFact)
        }
    }

    @Test
    fun `validates deterministic match selection and reuse scope`(): Unit {
        val local = DocumentMatchCandidate(
            scope = DocumentMatchScope.AppliedLocal,
            entityIri = Iri("https://example.com/Borrower"),
            sourceId = "simple",
            preferredLabel = "Borrower",
            score = 98,
            reason = "Exact preferred label",
        )
        val recommendation = recommendation(
            action = DocumentRecommendationAction.ReuseLocal,
            matches = listOf(local),
            selectedMatch = local,
            targetSourceId = null,
        )

        assertEquals(local, recommendation.selectedMatch)
        assertFailsWith<IllegalArgumentException> {
            recommendation(
                action = DocumentRecommendationAction.ReuseImportedOrFibo,
                matches = listOf(local),
                selectedMatch = local,
                targetSourceId = null,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            recommendation(
                action = DocumentRecommendationAction.CreateLocal,
                matches = listOf(local),
                selectedMatch = local,
            )
        }
    }

    @Test
    fun `requires mandatory clarification before accepting risky recommendations`(): Unit {
        val pending = recommendation(
            action = DocumentRecommendationAction.Revise,
            mandatoryClarificationReasons = listOf("replacement-of-existing-meaning"),
            reviewStatus = DocumentRecommendationReviewStatus.NeedsClarification,
        )

        assertEquals(DocumentRecommendationReviewStatus.NeedsClarification, pending.reviewStatus)
        assertFailsWith<IllegalArgumentException> {
            recommendation(
                action = DocumentRecommendationAction.Revise,
                mandatoryClarificationReasons = emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            pending.copy(reviewStatus = DocumentRecommendationReviewStatus.Accepted)
        }
        assertFailsWith<IllegalArgumentException> {
            recommendation(
                confidence = 59,
                mandatoryClarificationReasons = emptyList(),
            )
        }
    }

    @Test
    fun `keeps confirm provenance separate from typed draft operations`(): Unit {
        val provenance = appliedProvenance(
            action = DocumentRecommendationAction.Confirm,
            typedOperation = null,
            proposalId = null,
        )

        assertNull(provenance.typedOperation)
        assertFailsWith<IllegalArgumentException> {
            provenance.copy(
                typedOperation = AppliedDocumentTypedOperation(
                    stagedItemId = "stage-1",
                    targetSourceId = "simple",
                    normalizedTypedOperationKey = "create-class",
                    targetEntityIri = Iri("https://example.com/Borrower"),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            appliedProvenance(
                action = DocumentRecommendationAction.CreateLocal,
                typedOperation = null,
                proposalId = "proposal-1",
            )
        }
    }

    @Test
    fun `links every summary highlight to evidence and recommendations`(): Unit {
        val highlight = DocumentSummaryHighlight(
            id = "highlight-1",
            text = "Borrowers receive loans.",
            category = DocumentRecommendationCategory.BusinessFact,
            evidenceIds = listOf(evidenceId),
            recommendationIds = listOf("recommendation-1"),
        )
        val summary = DocumentSummary(
            documentId = documentId,
            purpose = "Defines lending responsibilities.",
            highlights = listOf(highlight),
            modelId = "gpt-model",
            promptVersion = "phase-11-document-analysis-v1",
        )

        assertEquals(highlight, summary.highlights.single())
        assertFailsWith<IllegalArgumentException> { summary.copy(highlights = emptyList()) }
        assertFailsWith<IllegalArgumentException> { highlight.copy(evidenceIds = emptyList()) }
    }

    @Test
    fun `contracts remain presentation parser provider and filesystem neutral`(): Unit {
        val forbiddenPrefixes = listOf(
            "io.ktor.",
            "com.fasterxml.",
            "org.apache.pdfbox.",
            "org.apache.poi.",
            "net.sourceforge.tess4j.",
            "org.apache.jena.",
            "java.nio.file.",
        )
        val contractTypes = listOf(
            IngestionDocument::class,
            LocatedDocumentTextBlock::class,
            DocumentEvidence::class,
            DocumentCandidate::class,
            DocumentRecommendation::class,
            AppliedDocumentProvenance::class,
        )

        contractTypes.flatMap { it.java.declaredFields.toList() }.forEach { field ->
            assertTrue(forbiddenPrefixes.none(field.type.name::startsWith), "${field.name} leaked ${field.type.name}")
        }
    }

    private fun candidate(
        type: DocumentCandidateCategory,
        category: DocumentRecommendationCategory,
        confidence: Int = 90,
    ): DocumentCandidate = DocumentCandidate(
        identity = DocumentCandidateIdentity(
            value = "candidate-${type.name}",
            documentChecksumSha256 = "a".repeat(64),
            category = type,
            normalizedValue = type.name.lowercase(),
            evidenceKeys = listOf("evidence-key-1"),
        ),
        documentId = documentId,
        category = type,
        recommendationCategory = category,
        proposedLabel = type.name,
        confidence = confidence,
        evidence = listOf(evidence),
    )

    private fun recommendation(
        action: DocumentRecommendationAction = DocumentRecommendationAction.CreateLocal,
        confidence: Int = 90,
        matches: List<DocumentMatchCandidate> = emptyList(),
        selectedMatch: DocumentMatchCandidate? = null,
        mandatoryClarificationReasons: List<String> = emptyList(),
        reviewStatus: DocumentRecommendationReviewStatus = DocumentRecommendationReviewStatus.Pending,
        targetSourceId: String? = "simple",
    ): DocumentRecommendation = DocumentRecommendation(
        id = "recommendation-1",
        candidateIds = listOf("candidate-1"),
        type = DocumentCandidateCategory.Class,
        category = DocumentRecommendationCategory.OntologyStructure,
        proposedLabel = "Borrower",
        action = action,
        confidence = confidence,
        rationale = "The document defines a borrower.",
        evidence = listOf(evidence),
        matches = matches,
        selectedMatch = selectedMatch,
        mandatoryClarificationReasons = mandatoryClarificationReasons,
        targetSourceId = targetSourceId,
        reviewStatus = reviewStatus,
        modelId = "gpt-model",
        promptVersion = "phase-11-document-analysis-v1",
    )

    private fun appliedProvenance(
        action: DocumentRecommendationAction,
        typedOperation: AppliedDocumentTypedOperation?,
        proposalId: String?,
    ): AppliedDocumentProvenance = AppliedDocumentProvenance(
        recordId = "provenance-1",
        projectId = "simple",
        taskId = DocumentTaskId("task-1"),
        document = AppliedDocumentIdentity(documentId, "a".repeat(64), "policy.pdf"),
        evidence = listOf(
            AppliedDocumentEvidence(
                evidenceId = evidenceId,
                documentId = documentId,
                pageNumber = 1,
                blockId = DocumentTextBlockId("block-1"),
                startOffsetInBlock = 0,
                endOffsetInBlock = 12,
                exactExcerpt = "The borrower",
                extractionMethod = DocumentExtractionMethod.EmbeddedText,
                extractorVersion = "pdfbox-3.0.8",
                confidence = 100,
            ),
        ),
        recommendationId = "recommendation-1",
        action = action,
        decision = AppliedDocumentDecision(
            decisionId = "decision-1",
            recommendationId = "recommendation-1",
            actorUserId = "alice",
            decidedAt = Instant.parse("2026-07-24T12:00:00Z"),
            status = DocumentRecommendationReviewStatus.Accepted,
            clarification = null,
        ),
        modelId = "gpt-model",
        promptVersion = "phase-11-document-analysis-v1",
        confidence = 95,
        evidenceTypes = listOf(DocumentEvidenceType.Explicit),
        typedOperation = typedOperation,
        applyEvent = AppliedDocumentApplyEvent(
            proposalId = proposalId,
            appliedByUserId = "bob",
            appliedAt = Instant.parse("2026-07-24T13:00:00Z"),
            baselineOntologyFingerprint = "baseline",
            resultingOntologyFingerprint = "result",
        ),
    )
}
