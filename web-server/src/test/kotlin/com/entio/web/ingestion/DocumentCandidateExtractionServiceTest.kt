package com.entio.web.ingestion

import com.entio.core.DocumentAuthorityMetadata
import com.entio.core.DocumentAuthorityStatus
import com.entio.core.DocumentCandidateExtractionCategory
import com.entio.core.DocumentCandidatePromotionReason
import com.entio.core.DocumentExtractionMethod
import com.entio.core.DocumentId
import com.entio.core.DocumentMediaType
import com.entio.core.DocumentProcessingStatus
import com.entio.core.DocumentTaskId
import com.entio.core.DocumentTextBlockId
import com.entio.core.IngestionDocument
import com.entio.core.LocatedDocumentTextBlock
import java.nio.file.Path
import java.nio.file.Files
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
        val mentions = DocumentCandidateExtractionService(configuration()).extractMentions(document, listOf(block))
        val categories = mentions.mapTo(mutableSetOf()) { it.category }

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
        assertTrue(mentions.all { mention ->
            listOf(mention.evidenceSpan).all { span ->
                span.documentId == document.id &&
                    span.blockId == block.id &&
                    block.exactText.substring(span.startOffsetInBlock, span.endOffsetInBlock) == span.exactText
            }
        })
        assertTrue(mentions.filter { it.category == DocumentCandidateExtractionCategory.RelationshipPhrase }
            .any { it.hints.isNotEmpty() })
    }

    @Test
    fun `produces identical IDs and order for frozen input`(): Unit {
        val service = DocumentCandidateExtractionService(configuration())
        val first: List<String>
        val elapsed = measureTimeMillis {
            first = service.extractMentions(document, listOf(block)).map { it.id }
        }
        val second = service.extractMentions(document, listOf(block)).map { it.id }

        assertEquals(first, second)
        assertTrue(elapsed < 2_000, "Pinned local NLP initialization and extraction exceeded the audited bound.")
    }

    @Test
    fun `discards symbol-only NLP artifacts without failing document extraction`(): Unit {
        val policy = block(
            document.id,
            "A lender evaluates a green loan • and records the decision ✓.",
        )
        val mentions = DocumentCandidateExtractionService(configuration()).extractMentions(document, listOf(policy))

        assertTrue(mentions.isNotEmpty())
        assertTrue(mentions.all { it.normalizedText.isNotBlank() })
        assertTrue(mentions.any { it.normalizedText.contains("loan") })
    }

    @Test
    fun `groups safe equivalents and promotes only meaning-bearing candidates`(): Unit {
        val repeated = listOf(
            block(document.id, "A Payment Instruction authorizes a transfer.").copy(id = DocumentTextBlockId("block-1")),
            block(document.id, "Payment Instructions are reviewed by an analyst.").copy(id = DocumentTextBlockId("block-2"), blockOrder = 1),
            block(document.id, "Information appears in this section.").copy(id = DocumentTextBlockId("block-3"), blockOrder = 2),
        )
        val service = DocumentCandidateExtractionService(configuration())
        val mentions = service.extractMentions(document, repeated)
        val extraction = service.promoteCandidates(mentions)
        val paymentInstruction = extraction.candidates.single { it.normalizedText == "payment instruction" }

        assertEquals(2, paymentInstruction.mentionIds.size)
        assertTrue(DocumentCandidatePromotionReason.RepeatedMeaningfulContext in paymentInstruction.promotionReasons)
        assertTrue(extraction.candidates.none { it.normalizedText == "information" })
        assertTrue(extraction.coverage.any { it.reasonCode == "low-value-mention" })
    }

    @Test
    fun `promotes grounded relationships and attribute names while retaining values as support`(): Unit {
        val value = "A customer authorizes a payment. Review frequency: monthly."
        val service = DocumentCandidateExtractionService(configuration())
        val mentions = service.extractMentions(document, listOf(block(document.id, value)))
        val extraction = service.promoteCandidates(mentions)
        val relationship = checkNotNull(
            extraction.candidates.singleOrNull { it.category == DocumentCandidateExtractionCategory.RelationshipPhrase },
        ) { "Expected one relationship; candidates=${extraction.candidates.map { it.category to it.normalizedText }}" }
        val attribute = checkNotNull(extraction.candidates.singleOrNull { it.normalizedText == "review frequency" }) {
            "Expected the attribute label; candidates=${extraction.candidates.map { it.category to it.normalizedText }}"
        }

        assertEquals("authorize", relationship.normalizedText)
        assertEquals(2, relationship.hints.count { it.relatedCandidateId != null })
        assertTrue(DocumentCandidatePromotionReason.ConnectedRelationship in relationship.promotionReasons)
        assertTrue(attribute.hints.any { it.role == com.entio.core.DocumentCandidateHintRole.Value && it.text == "monthly" })
        assertTrue(extraction.coverage.any {
            it.kind == com.entio.core.DocumentMentionCoverageKind.SupportingValue
        })
    }

    @Test
    fun `marks administrative blocks without deciding ontology meaning`(): Unit {
        val administrative = block(document.id, "Revision 4 approved July 2026.", section = "Revision History")
        val illustrative = block(document.id, "For example, Elena approves invoice INV-1.", section = "Example")
            .copy(id = DocumentTextBlockId("block-illustrative"), blockOrder = 1)
        val service = DocumentCandidateExtractionService(configuration())
        val mentions = service.extractMentions(
            document,
            listOf(administrative, illustrative),
        )
        val extraction = service.promoteCandidates(mentions)

        assertTrue(mentions.any { it.category == DocumentCandidateExtractionCategory.Administrative })
        assertTrue(mentions.any { it.category == DocumentCandidateExtractionCategory.Illustrative })
        assertTrue(extraction.candidates.isEmpty())
        assertTrue(extraction.coverage.all { it.reasonCode == "document-only" })
    }

    @Test
    fun `keeps numbered example sections in document-only coverage`(): Unit {
        val sectioned = block(
            document.id,
            """
            1. Requirements
            A Payment Instruction requires approval.
            2. Examples and Interpretation
            Elena Ruiz owns Example Account.
            3. Monitoring
            A Payment Instruction is reviewed.
            """.trimIndent(),
        )
        val service = DocumentCandidateExtractionService(configuration())
        val mentions = service.extractMentions(document, listOf(sectioned))
        val extraction = service.promoteCandidates(mentions)

        assertTrue(mentions.any { it.category == DocumentCandidateExtractionCategory.Illustrative })
        assertTrue(extraction.coverage.any { it.reasonCode == "document-only" })
        assertTrue(extraction.candidates.any { it.normalizedText == "payment instruction" })
        assertTrue(extraction.candidates.none { it.displayText.contains("Elena", ignoreCase = true) })
        assertTrue(extraction.candidates.none { it.normalizedText == "example account" })
    }

    @Test
    fun `keeps similar concepts separate and promotes an exact ontology match`(): Unit {
        val blocks = listOf(
            block(document.id, "A payment instruction is available.").copy(id = DocumentTextBlockId("block-1")),
            block(document.id, "The payment instruction remains active.").copy(id = DocumentTextBlockId("block-2"), blockOrder = 1),
            block(document.id, "A transfer instruction is available.").copy(id = DocumentTextBlockId("block-3"), blockOrder = 2),
            block(document.id, "The transfer instruction remains active.").copy(id = DocumentTextBlockId("block-4"), blockOrder = 3),
            block(document.id, "settlement ledger.").copy(id = DocumentTextBlockId("block-5"), blockOrder = 4),
        )
        val service = DocumentCandidateExtractionService(configuration())
        val mentions = service.extractMentions(document, blocks)
        val extraction = service.promoteCandidates(mentions, setOf("Settlement Ledger"))

        assertTrue(extraction.candidates.any { it.normalizedText == "payment instruction" })
        assertTrue(extraction.candidates.any { it.normalizedText == "transfer instruction" })
        assertTrue(extraction.candidates.any {
            it.normalizedText == "settlement ledger" &&
                DocumentCandidatePromotionReason.StrongOntologyMatch in it.promotionReasons
        })
    }

    @Test
    fun `retains standalone values only as supporting coverage`(): Unit {
        val valueBlock = block(document.id, "USD 25.00 2026-07-31 REF-2026-44 for seven years at 12 percent.")
        val service = DocumentCandidateExtractionService(configuration())
        val extraction = service.promoteCandidates(service.extractMentions(document, listOf(valueBlock)))

        assertTrue(extraction.candidates.isEmpty())
        assertTrue(extraction.coverage.isNotEmpty())
        assertTrue(extraction.coverage.all { it.kind == com.entio.core.DocumentMentionCoverageKind.SupportingValue })
    }

    @Test
    fun `reduces the frozen two document corpus before ontology retrieval`(): Unit {
        val temporary = Files.createTempDirectory("entio-candidate-funnel-test")
        val config = configuration().copy(temporaryRoot = temporary)
        val directory = TemporaryTaskDirectory("task-funnel", temporary)
        val files = listOf(
            "consumer-lending-servicing-compliance-standard.pdf",
            "commercial-account-and-payment-authorization-policy.pdf",
        ).map { filename -> Path.of("../examples/simple-ontology/documents/$filename").toAbsolutePath().normalize() }
        val extracted = files.mapIndexed { index, path ->
            val ingestionDocument = document().copy(
                id = DocumentId("document-${index + 1}"),
                safeFilename = path.fileName.toString(),
                mediaType = DocumentMediaType.Pdf,
                byteSize = Files.size(path),
                checksumSha256 = sha256(path),
            )
            DocumentExtractionService(config).extract(
                AcceptedDocumentUpload(
                    ingestionDocument,
                    StoredDocumentFile(path.fileName.toString(), path, Files.size(path)),
                ),
                directory,
            )
        }
        val service = DocumentCandidateExtractionService(config)
        val mentions = extracted.flatMap { service.extractMentions(it.document, it.blocks) }
            .sortedBy(com.entio.core.DocumentEvidenceMention::stableOrderingKey)
        val extraction = service.promoteCandidates(mentions)
        assertTrue(mentions.size > 100)
        assertTrue(extraction.groupedCandidateCount < mentions.size)
        assertTrue(extraction.candidates.size < extraction.groupedCandidateCount)
        assertTrue(
            extraction.candidates.size in 50..120,
            "The frozen two-document corpus produced ${extraction.candidates.size} ontology-bearing candidates; " +
                "categories=${extraction.candidates.groupingBy { it.category }.eachCount().toSortedMap()}; " +
                "reasons=${extraction.candidates.flatMap { it.promotionReasons }.groupingBy { it }.eachCount().toSortedMap()}.",
        )
        assertTrue(extraction.coverage.any { it.kind == com.entio.core.DocumentMentionCoverageKind.SupportingValue })
        assertTrue(extraction.coverage.any { it.kind == com.entio.core.DocumentMentionCoverageKind.Rejected })
    }

    @Test
    fun `rejects malformed and cross-document extracted blocks with a safe code`(): Unit {
        val service = DocumentCandidateExtractionService(configuration())
        val crossDocument = block(DocumentId("document-2"), text)

        val crossFailure = assertFailsWith<DocumentIngestionFailure> {
            service.extractMentions(document, listOf(crossDocument))
        }
        val missingFailure = assertFailsWith<DocumentIngestionFailure> {
            service.extractMentions(document, emptyList())
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
            service.extractMentions(document, listOf(block))
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

    private fun sha256(path: Path): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path))
        .joinToString("") { "%02x".format(it) }

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
