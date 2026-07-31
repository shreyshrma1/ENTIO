package com.entio.web.ingestion

import com.entio.core.DocumentAuthorityMetadata
import com.entio.core.DocumentAuthorityStatus
import com.entio.core.DocumentCandidateExtractionCategory
import com.entio.core.DocumentExtractionMethod
import com.entio.core.DocumentId
import com.entio.core.DocumentMediaType
import com.entio.core.DocumentProcessingStatus
import com.entio.core.DocumentTaskId
import com.entio.core.DocumentTextBlockId
import com.entio.core.IngestionDocument
import com.entio.core.LocatedDocumentTextBlock
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.readText
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DocumentCandidateExtractionServiceTest {
    private val text = Path.of("src/test/resources/document-ingestion/phase-12-candidate-extraction.txt").readText().trim()
    private val document = document()
    private val block = block(document.id, text)

    @Test
    fun `extracts required local candidate categories with exact evidence`(): Unit {
        val candidates = DocumentCandidateExtractionService(configuration()).extract(document, listOf(block))
        val categories = candidates.mapTo(mutableSetOf()) { it.category }

        assertTrue(DocumentCandidateExtractionCategory.Person in categories)
        assertTrue(DocumentCandidateExtractionCategory.Organization in categories)
        assertTrue(DocumentCandidateExtractionCategory.Location in categories)
        assertTrue(DocumentCandidateExtractionCategory.Date in categories)
        assertTrue(DocumentCandidateExtractionCategory.Identifier in categories)
        assertTrue(DocumentCandidateExtractionCategory.MonetaryAmount in categories)
        assertTrue(DocumentCandidateExtractionCategory.ConceptTerm in categories)
        assertTrue(DocumentCandidateExtractionCategory.RelationshipPhrase in categories)
        assertTrue(DocumentCandidateExtractionCategory.AttributeValue in categories)
        assertTrue(DocumentCandidateExtractionCategory.RuleCue in categories)
        assertTrue(candidates.all { candidate ->
            candidate.evidenceSpans.all { span ->
                span.documentId == document.id &&
                    span.blockId == block.id &&
                    block.exactText.substring(span.startOffsetInBlock, span.endOffsetInBlock) == span.exactText
            }
        })
        assertTrue(candidates.filter { it.category == DocumentCandidateExtractionCategory.RelationshipPhrase }
            .any { it.hints.isNotEmpty() })
    }

    @Test
    fun `produces identical IDs and order for frozen input`(): Unit {
        val service = DocumentCandidateExtractionService(configuration())
        val first: List<String>
        val elapsed = measureTimeMillis {
            first = service.extract(document, listOf(block)).map { it.id }
        }
        val second = service.extract(document, listOf(block)).map { it.id }

        assertEquals(first, second)
        assertTrue(elapsed < 2_000, "Pinned local NLP initialization and extraction exceeded the audited bound.")
    }

    @Test
    fun `keeps similar business terms separate and removes exact duplicate output`(): Unit {
        val candidates = DocumentCandidateExtractionService(configuration()).extract(document, listOf(block))
        val concepts = candidates.filter { it.category == DocumentCandidateExtractionCategory.ConceptTerm }

        assertTrue(concepts.any { it.normalizedText == "payment" })
        assertTrue(concepts.any { it.normalizedText == "payment instruction" })
        assertEquals(
            candidates.size,
            candidates.distinctBy {
                listOf(it.category.name, it.evidenceSpans.single().stableOrderingKey, it.normalizedText)
            }.size,
        )
    }

    @Test
    fun `marks administrative blocks without deciding ontology meaning`(): Unit {
        val administrative = block(document.id, "Revision 4 approved July 2026.", section = "Revision History")
        val illustrative = block(document.id, "For example, Elena approves invoice INV-1.", section = "Example")
            .copy(id = DocumentTextBlockId("block-illustrative"), blockOrder = 1)
        val candidates = DocumentCandidateExtractionService(configuration()).extract(
            document,
            listOf(administrative, illustrative),
        )

        assertTrue(candidates.any { it.category == DocumentCandidateExtractionCategory.Administrative })
        assertTrue(candidates.any { it.category == DocumentCandidateExtractionCategory.Illustrative })
    }

    @Test
    fun `rejects malformed and cross-document extracted blocks with a safe code`(): Unit {
        val service = DocumentCandidateExtractionService(configuration())
        val crossDocument = block(DocumentId("document-2"), text)

        val crossFailure = assertFailsWith<DocumentIngestionFailure> {
            service.extract(document, listOf(crossDocument))
        }
        val missingFailure = assertFailsWith<DocumentIngestionFailure> {
            service.extract(document, emptyList())
        }
        assertEquals("document-candidate-extraction-failed", crossFailure.code)
        assertEquals("document-candidate-extraction-failed", missingFailure.code)
        assertTrue(crossFailure.message.orEmpty().contains("path").not())
    }

    @Test
    fun `fails safely when a pinned NLP resource cannot initialize`(): Unit {
        val service = DocumentCandidateExtractionService(
            configuration = configuration(),
            resourceLoader = DocumentNlpResourceLoader { null },
        )

        val failure = assertFailsWith<DocumentIngestionFailure> {
            service.extract(document, listOf(block))
        }
        assertEquals("document-candidate-extraction-failed", failure.code)
        assertEquals("Local document candidate extraction resources are unavailable.", failure.message)
    }

    private fun configuration(): DocumentIngestionConfiguration = DocumentIngestionConfiguration(
        temporaryRoot = Path.of("/tmp/entio-phase-12-candidate-test"),
        provenanceRoot = Path.of("/tmp/entio-phase-12-provenance-test"),
    )

    private fun document(): IngestionDocument = IngestionDocument(
        id = DocumentId("document-1"),
        taskId = DocumentTaskId("task-1"),
        safeFilename = "candidate-fixture.txt",
        mediaType = DocumentMediaType.Text,
        byteSize = text.toByteArray().size.toLong(),
        checksumSha256 = "a".repeat(64),
        projectId = "project-1",
        uploaderUserId = "user-1",
        uploadedAt = Instant.parse("2026-07-31T12:00:00Z"),
        authority = DocumentAuthorityMetadata(DocumentAuthorityStatus.Authoritative),
        status = DocumentProcessingStatus.Analyzing,
    )

    private fun block(
        documentId: DocumentId,
        value: String,
        section: String? = "Policy",
    ): LocatedDocumentTextBlock = LocatedDocumentTextBlock(
        id = DocumentTextBlockId("block-${documentId.value}"),
        documentId = documentId,
        safeFilename = "candidate-fixture.txt",
        sectionHeading = section,
        blockOrder = 0,
        startOffset = 0,
        endOffset = value.length,
        exactText = value,
        extractionMethod = DocumentExtractionMethod.Text,
        extractorVersion = "phase-11-extractor-v1",
    )
}
