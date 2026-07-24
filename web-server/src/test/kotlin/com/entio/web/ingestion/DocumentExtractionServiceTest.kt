package com.entio.web.ingestion

import com.entio.core.DocumentAuthorityMetadata
import com.entio.core.DocumentAuthorityStatus
import com.entio.core.DocumentExtractionMethod
import com.entio.core.DocumentId
import com.entio.core.DocumentMediaType
import com.entio.core.DocumentTaskId
import com.entio.core.DocumentTextRectangle
import com.entio.core.IngestionDocument
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.poi.xwpf.usermodel.XWPFDocument

class DocumentExtractionServiceTest {
    @Test
    fun extractsTextMarkdownAndDocxInStableReadingOrder(): Unit {
        val fixture = fixture()
        val text = fixture.upload("policy.txt", DocumentMediaType.Text, "First paragraph.\n\nSecond paragraph.")
        val markdown = fixture.upload("policy.md", DocumentMediaType.Markdown, "# Scope\n\nThe policy applies.")
        val docxPath = fixture.directory.path.resolve("document-docx.bin")
        XWPFDocument().use { document ->
            document.createParagraph().apply {
                style = "Heading1"
                createRun().setText("Responsibilities")
            }
            document.createTable(1, 2).also { table ->
                table.getRow(0).getCell(0).text = "Owner"
                table.getRow(0).getCell(1).text = "Reviewer"
            }
            Files.newOutputStream(docxPath).use(document::write)
        }
        val docx = fixture.uploadAt("policy.docx", DocumentMediaType.Docx, docxPath)
        val service = DocumentExtractionService(fixture.configuration)

        assertEquals(listOf("First paragraph.", "Second paragraph."), service.extract(text, fixture.directory).blocks.map { it.exactText })
        assertEquals("Scope", service.extract(markdown, fixture.directory).blocks.first().sectionHeading)
        val docxBlocks = service.extract(docx, fixture.directory).blocks
        assertEquals("Responsibilities", docxBlocks.first().sectionHeading)
        assertEquals("Owner\tReviewer", docxBlocks.last().exactText)
        assertTrue(docxBlocks.zipWithNext().all { (left, right) -> left.endOffset < right.startOffset })

        val repeated = service.extract(text, fixture.directory)
        assertEquals(
            service.extract(text, fixture.directory).blocks.map { it.id },
            repeated.blocks.map { it.id },
        )
    }

    @Test
    fun usesOcrOnlyForUnreliablePdfPagesAndRetainsGeometryAndWarnings(): Unit {
        val fixture = fixture()
        val pdf = fixture.pdf(
            listOf(
                "This embedded page has enough reliable policy words to prevent optical recognition work.",
                null,
            ),
        )
        var calls = 0
        val service = DocumentExtractionService(
            fixture.configuration,
            DocumentPageOcr { _, geometry, _ ->
                calls += 1
                OcrPageResult(
                    "Recognized scanned policy content",
                    59,
                    listOf(DocumentTextRectangle(0.1, 0.2, 0.8, 0.3)),
                ).also {
                    assertTrue(geometry.widthPixels > 0)
                }
            },
        )

        val extracted = service.extract(pdf, fixture.directory)

        assertEquals(1, calls)
        assertEquals(listOf(DocumentExtractionMethod.EmbeddedText, DocumentExtractionMethod.Ocr), extracted.blocks.map { it.extractionMethod })
        assertEquals(59, extracted.blocks.last().ocrConfidence)
        assertTrue(extracted.blocks.last().pageImageId in extracted.pageImages)
        assertEquals(1, extracted.blocks.last().rectangles.size)
        assertTrue(extracted.warnings.single().contains("below 60"))
    }

    @Test
    fun reliabilityAndBoundariesAreDeterministic(): Unit {
        val fixture = fixture()
        val service = DocumentExtractionService(fixture.configuration, DocumentPageOcr { _, _, _ ->
            OcrPageResult("OCR fallback text", 85, emptyList())
        })

        assertTrue(service.isReliableEmbeddedText("This line contains enough ordinary policy words and letters for reliable extraction."))
        assertTrue(!service.isReliableEmbeddedText("too sparse"))
        assertTrue(!service.isReliableEmbeddedText("word word word \uFFFD\uFFFD symbols symbols"))

        val bounded = DocumentExtractionService(
            fixture.configuration.copy(maximumOcrPagesPerDocument = 1),
            DocumentPageOcr { _, _, _ -> OcrPageResult("OCR page words", 90, emptyList()) },
        )
        assertCode("ocr-page-limit") {
            bounded.extract(fixture.pdf(listOf(null, null)), fixture.directory)
        }
        assertTrue(
            Files.list(fixture.directory.path).use { paths ->
                paths.noneMatch { it.fileName.toString().startsWith("page-") }
            },
        )

        var cancelled = false
        val cancellable = DocumentExtractionService(
            fixture.configuration,
            DocumentPageOcr { _, _, _ ->
                cancelled = true
                OcrPageResult("first OCR page", 90, emptyList())
            },
            isCancelled = { cancelled },
        )
        assertCode("ingestion-cancelled") {
            cancellable.extract(fixture.pdf(listOf(null, null)), fixture.directory)
        }

        val textBounded = DocumentExtractionService(fixture.configuration.copy(maximumExtractedCharacters = 5))
        assertCode("document-text-limit") {
            textBounded.extract(fixture.upload("long.txt", DocumentMediaType.Text, "sixsix"), fixture.directory)
        }
    }

    @Test
    fun rejectsEncryptedPdfAndMixedScriptDocuments(): Unit {
        val fixture = fixture()
        val encryptedPath = fixture.directory.path.resolve("document-encrypted.bin")
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.protect(StandardProtectionPolicy("owner", "reader", AccessPermission()))
            document.save(encryptedPath.toFile())
        }
        assertCode("encrypted-document-unsupported") {
            DocumentExtractionService(fixture.configuration).extract(
                fixture.uploadAt("secret.pdf", DocumentMediaType.Pdf, encryptedPath),
                fixture.directory,
            )
        }

        val nonLatin = "a".repeat(100) + "Ж".repeat(12)
        assertCode("unsupported-document-language") {
            DocumentExtractionService(fixture.configuration).extract(
                fixture.upload("mixed.txt", DocumentMediaType.Text, nonLatin),
                fixture.directory,
            )
        }
    }

    private fun fixture(): ExtractionFixture {
        val temporary = Files.createTempDirectory("entio-extraction-test")
        val storage = DocumentTemporaryStorage(temporary)
        val directory = storage.createTask("task-extraction")
        return ExtractionFixture(
            DocumentIngestionConfiguration(
                temporaryRoot = temporary,
                provenanceRoot = Files.createTempDirectory("entio-extraction-provenance"),
            ),
            directory,
        )
    }

    private data class ExtractionFixture(
        val configuration: DocumentIngestionConfiguration,
        val directory: TemporaryTaskDirectory,
    ) {
        fun upload(filename: String, mediaType: DocumentMediaType, content: String): AcceptedDocumentUpload {
            val path = directory.path.resolve("document-${filename.hashCode()}.bin")
            Files.writeString(path, content)
            return uploadAt(filename, mediaType, path)
        }

        fun uploadAt(filename: String, mediaType: DocumentMediaType, path: Path): AcceptedDocumentUpload {
            val id = "document-${filename.substringBefore('.')}"
            val document = IngestionDocument(
                id = DocumentId(id),
                taskId = DocumentTaskId("task-extraction"),
                safeFilename = filename,
                mediaType = mediaType,
                byteSize = Files.size(path),
                checksumSha256 = sha(path),
                projectId = "project-a",
                uploaderUserId = "alice",
                uploadedAt = Instant.parse("2026-01-01T00:00:00Z"),
                authority = DocumentAuthorityMetadata(DocumentAuthorityStatus.Authoritative),
            )
            return AcceptedDocumentUpload(document, StoredDocumentFile(path.fileName.toString(), path, Files.size(path)))
        }

        fun pdf(pageTexts: List<String?>): AcceptedDocumentUpload {
            val path = directory.path.resolve("document-pdf-${pageTexts.hashCode()}.bin")
            PDDocument().use { document ->
                pageTexts.forEach { text ->
                    val page = PDPage()
                    document.addPage(page)
                    if (text != null) {
                        PDPageContentStream(document, page).use { content ->
                            content.beginText()
                            content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                            content.newLineAtOffset(50f, 700f)
                            content.showText(text)
                            content.endText()
                        }
                    }
                }
                document.save(path.toFile())
            }
            return uploadAt("policy.pdf", DocumentMediaType.Pdf, path)
        }

        private fun sha(path: Path): String = java.security.MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(path))
            .joinToString("") { "%02x".format(it) }
    }

    private fun assertCode(code: String, block: () -> Unit): Unit {
        val failure = assertFailsWith<DocumentIngestionFailure> { block() }
        assertEquals(code, failure.code)
    }
}
