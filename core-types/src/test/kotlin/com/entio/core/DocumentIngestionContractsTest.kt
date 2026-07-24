package com.entio.core

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DocumentIngestionContractsTest {
    private val taskId = DocumentTaskId("task-1")
    private val documentId = DocumentId("document-1")
    private val blockId = DocumentTextBlockId("block-1")
    private val evidenceId = DocumentEvidenceId("evidence-1")

    @Test
    fun `constructs neutral document and located OCR contracts`(): Unit {
        val document = IngestionDocument(
            id = documentId,
            taskId = taskId,
            safeFilename = "policy.pdf",
            mediaType = DocumentMediaType.Pdf,
            byteSize = 1_024,
            checksumSha256 = "a".repeat(64),
            projectId = "simple",
            uploaderUserId = "alice",
            uploadedAt = Instant.parse("2026-07-24T12:00:00Z"),
            authority = DocumentAuthorityMetadata(
                status = DocumentAuthorityStatus.Authoritative,
                businessArea = "Lending",
                effectiveDate = LocalDate.parse("2026-01-01"),
            ),
        )
        val block = LocatedDocumentTextBlock(
            id = blockId,
            documentId = documentId,
            safeFilename = document.safeFilename,
            pageNumber = 2,
            blockOrder = 0,
            startOffset = 10,
            endOffset = 21,
            exactText = "Loan policy",
            extractionMethod = DocumentExtractionMethod.Ocr,
            extractorVersion = "tesseract-5.5.2",
            ocrConfidence = 92,
            pageImageId = "page-image-2",
            pageGeometry = DocumentPageGeometry(1_200, 1_600),
            rectangles = listOf(DocumentTextRectangle(0.1, 0.2, 0.4, 0.3)),
        )

        assertEquals("en", document.language)
        assertEquals(DocumentConfidenceBand.High, block.ocrConfidence?.toDocumentConfidenceBand())
        assertEquals("0000000002:0000000000:0000000010:block-1", block.stableOrderingKey)
    }

    @Test
    fun `rejects unsafe identities filenames checksums bounds and metadata`(): Unit {
        assertFailsWith<IllegalArgumentException> { DocumentId("../document") }
        assertFailsWith<IllegalArgumentException> {
            baseDocument(safeFilename = "../policy.pdf")
        }
        assertFailsWith<IllegalArgumentException> {
            baseDocument(checksumSha256 = "ABC")
        }
        assertFailsWith<IllegalArgumentException> {
            baseDocument(byteSize = MAX_INGESTION_DOCUMENT_BYTES + 1)
        }
        assertFailsWith<IllegalArgumentException> {
            DocumentAuthorityMetadata(
                status = DocumentAuthorityStatus.Supporting,
                effectiveDate = LocalDate.parse("2026-02-01"),
                expirationDate = LocalDate.parse("2026-01-01"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DocumentAuthorityMetadata(
                status = DocumentAuthorityStatus.Supporting,
                relatedDocumentId = DocumentId("document-2"),
            )
        }
    }

    @Test
    fun `enforces located text offsets and OCR-only fields`(): Unit {
        assertFailsWith<IllegalArgumentException> {
            embeddedBlock(startOffset = 0, endOffset = 5, exactText = "four")
        }
        assertFailsWith<IllegalArgumentException> {
            embeddedBlock(ocrConfidence = 90)
        }
        assertFailsWith<IllegalArgumentException> {
            LocatedDocumentTextBlock(
                id = blockId,
                documentId = documentId,
                safeFilename = "policy.pdf",
                pageNumber = 1,
                blockOrder = 0,
                startOffset = 0,
                endOffset = 4,
                exactText = "text",
                extractionMethod = DocumentExtractionMethod.Ocr,
                extractorVersion = "tesseract-5.5.2",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DocumentTextRectangle(0.8, 0.1, 0.2, 0.5)
        }
    }

    @Test
    fun `requires exact bounded evidence and keeps external evidence separate`(): Unit {
        val reference = evidenceReference()
        val explicit = DocumentEvidence(evidenceId, DocumentEvidenceType.Explicit, listOf(reference))
        val external = DocumentEvidence(
            DocumentEvidenceId("evidence-external"),
            DocumentEvidenceType.ExternalOntologyEvidence,
            entioRecordId = "fibo-record-1",
        )

        assertEquals("The borrower", explicit.references.single().exactExcerpt)
        assertEquals("fibo-record-1", external.entioRecordId)
        assertFailsWith<IllegalArgumentException> {
            evidenceReference(endOffsetInBlock = 11)
        }
        assertFailsWith<IllegalArgumentException> {
            DocumentEvidence(
                DocumentEvidenceId("combined"),
                DocumentEvidenceType.CombinedEvidence,
                listOf(reference),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DocumentEvidence(
                DocumentEvidenceId("external"),
                DocumentEvidenceType.ExternalOntologyEvidence,
                listOf(reference),
                "fibo-record-1",
            )
        }
    }

    @Test
    fun `validates append-only review and draft provenance ordering`(): Unit {
        val decision = DocumentReviewDecision(
            decisionId = "decision-1",
            recommendationId = "recommendation-1",
            actorUserId = "alice",
            decidedAt = Instant.parse("2026-07-24T12:00:00Z"),
            previousStatus = DocumentRecommendationReviewStatus.Pending,
            newStatus = DocumentRecommendationReviewStatus.Accepted,
        )
        val provenance = DocumentDraftProvenance(
            taskId = taskId,
            recommendationId = decision.recommendationId,
            decisionId = decision.decisionId,
            evidenceIds = listOf(evidenceId),
            modelId = "gpt-model",
            promptVersion = "phase-11-document-analysis-v1",
            extractionMethods = listOf(DocumentExtractionMethod.EmbeddedText),
            confidence = 88,
            targetSourceId = "simple",
            normalizedTypedOperationKey = "create-class",
        )

        assertEquals(88, provenance.confidence)
        assertFailsWith<IllegalArgumentException> { decision.copy(newStatus = decision.previousStatus) }
        assertFailsWith<IllegalArgumentException> {
            provenance.copy(
                evidenceIds = listOf(DocumentEvidenceId("z"), DocumentEvidenceId("a")),
            )
        }
    }

    private fun baseDocument(
        safeFilename: String = "policy.pdf",
        byteSize: Long = 100,
        checksumSha256: String = "a".repeat(64),
    ): IngestionDocument = IngestionDocument(
        id = documentId,
        taskId = taskId,
        safeFilename = safeFilename,
        mediaType = DocumentMediaType.Pdf,
        byteSize = byteSize,
        checksumSha256 = checksumSha256,
        projectId = "simple",
        uploaderUserId = "alice",
        uploadedAt = Instant.EPOCH,
        authority = DocumentAuthorityMetadata(DocumentAuthorityStatus.Supporting),
    )

    private fun embeddedBlock(
        startOffset: Int = 0,
        endOffset: Int = 4,
        exactText: String = "text",
        ocrConfidence: Int? = null,
    ): LocatedDocumentTextBlock = LocatedDocumentTextBlock(
        id = blockId,
        documentId = documentId,
        safeFilename = "policy.pdf",
        pageNumber = 1,
        blockOrder = 0,
        startOffset = startOffset,
        endOffset = endOffset,
        exactText = exactText,
        extractionMethod = DocumentExtractionMethod.EmbeddedText,
        extractorVersion = "pdfbox-3.0.8",
        ocrConfidence = ocrConfidence,
    )

    private fun evidenceReference(
        endOffsetInBlock: Int = 12,
    ): DocumentEvidenceReference = DocumentEvidenceReference(
        id = evidenceId,
        documentId = documentId,
        blockId = blockId,
        pageNumber = 1,
        startOffsetInBlock = 0,
        endOffsetInBlock = endOffsetInBlock,
        exactExcerpt = "The borrower",
        extractionMethod = DocumentExtractionMethod.EmbeddedText,
    )
}
