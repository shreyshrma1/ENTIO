package com.entio.web.ingestion

import com.entio.core.DocumentExtractionMethod
import com.entio.core.DocumentMediaType
import com.entio.core.DocumentPageGeometry
import com.entio.core.DocumentTextBlockId
import com.entio.core.IngestionDocument
import com.entio.core.LocatedDocumentTextBlock
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import javax.imageio.ImageIO
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.openxml4j.util.ZipSecureFile
import org.apache.poi.xwpf.usermodel.IBodyElement
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable

internal data class ExtractedDocument(
    val document: IngestionDocument,
    val blocks: List<LocatedDocumentTextBlock>,
    val warnings: List<String>,
    val pageImages: Map<String, Path>,
) {
    init {
        require(blocks == blocks.sortedBy(LocatedDocumentTextBlock::stableOrderingKey))
        require(warnings.size <= 500)
    }
}

internal class DocumentExtractionService(
    private val configuration: DocumentIngestionConfiguration,
    private val ocr: DocumentPageOcr? = configuration.tesseract?.let(::FixedTesseractOcr),
    private val isCancelled: () -> Boolean = { false },
) {
    fun extract(upload: AcceptedDocumentUpload, taskDirectory: TemporaryTaskDirectory): ExtractedDocument =
        when {
            isCancelled() -> throw DocumentIngestionFailure("ingestion-cancelled", "Document extraction was cancelled.")
            upload.document.mediaType == DocumentMediaType.Pdf -> extractPdf(upload, taskDirectory)
            upload.document.mediaType == DocumentMediaType.Docx -> extractDocx(upload)
            upload.document.mediaType == DocumentMediaType.Text ->
                extractPlain(upload, DocumentExtractionMethod.Text, markdown = false)
            else ->
                extractPlain(upload, DocumentExtractionMethod.Markdown, markdown = true)
        }.also(::validateLanguage)

    private fun extractPdf(upload: AcceptedDocumentUpload, taskDirectory: TemporaryTaskDirectory): ExtractedDocument {
        val warnings = mutableListOf<String>()
        val images = linkedMapOf<String, Path>()
        val blocks = mutableListOf<LocatedDocumentTextBlock>()
        var offset = 0
        var ocrPages = 0
        var renderedBytes = 0L
        val started = System.nanoTime()
        try {
            Loader.loadPDF(upload.storedFile.path.toFile()).use { pdf ->
                if (pdf.isEncrypted) throw DocumentIngestionFailure("encrypted-document-unsupported", "Encrypted PDFs are not supported.")
                if (pdf.numberOfPages > configuration.maximumPdfPages) {
                    throw DocumentIngestionFailure("document-page-limit", "The PDF exceeds the 500-page limit.")
                }
                val renderer = PDFRenderer(pdf)
                for (index in 0 until pdf.numberOfPages) {
                    if (isCancelled()) {
                        throw DocumentIngestionFailure("ingestion-cancelled", "Document extraction was cancelled.")
                    }
                    val pageNumber = index + 1
                    val embedded = normalizeText(extractPdfPageText(pdf, pageNumber))
                    if (isReliableEmbeddedText(embedded)) {
                        blocks += block(
                            upload.document,
                            pageNumber,
                            blocks.size,
                            offset,
                            embedded,
                            DocumentExtractionMethod.EmbeddedText,
                        )
                        offset = checkedNextOffset(offset, embedded)
                        continue
                    }
                    ocrPages += 1
                    if (ocrPages > configuration.maximumOcrPagesPerDocument) {
                        throw DocumentIngestionFailure("ocr-page-limit", "The document exceeds the 200-page OCR limit.")
                    }
                    val elapsed = Duration.ofNanos(System.nanoTime() - started)
                    val remaining = configuration.ocrDocumentTimeout.minus(elapsed)
                    if (remaining.isNegative || remaining.isZero) {
                        throw DocumentIngestionFailure("ocr-time-limit", "OCR exceeded the approved document time limit.")
                    }
                    val adapter = ocr ?: throw DocumentIngestionFailure(
                        "ocr-unavailable",
                        "This PDF page requires the configured Tesseract 5.5.2 runtime.",
                    )
                    val image = renderer.renderImageWithDPI(index, RENDER_DPI, ImageType.RGB)
                    val geometry = validateGeometry(image)
                    val imageId = "page-${stableId(upload.document.checksumSha256, pageNumber.toString())}"
                    val imagePath = taskDirectory.path.resolve("$imageId.png").normalize()
                    require(imagePath.parent == taskDirectory.path)
                    ImageIO.write(image, "png", imagePath.toFile())
                    val byteSize = Files.size(imagePath)
                    if (byteSize > MAX_PAGE_IMAGE_BYTES) {
                        Files.deleteIfExists(imagePath)
                        throw DocumentIngestionFailure("rendered-page-limit", "A rendered PDF page exceeds the approved image limit.")
                    }
                    renderedBytes += byteSize
                    if (renderedBytes > configuration.maximumRenderedImageBytesPerTask) {
                        Files.deleteIfExists(imagePath)
                        throw DocumentIngestionFailure("rendered-task-limit", "Rendered PDF pages exceed the approved task limit.")
                    }
                    images[imageId] = imagePath
                    val result = adapter.recognize(imagePath, geometry, remaining)
                    val text = normalizeText(result.text)
                    if (text.isBlank()) throw DocumentIngestionFailure("ocr-no-text", "OCR did not produce usable text.")
                    blocks += block(
                        upload.document,
                        pageNumber,
                        blocks.size,
                        offset,
                        text,
                        DocumentExtractionMethod.Ocr,
                        result.confidence,
                        imageId,
                        geometry,
                        result.rectangles,
                    )
                    if (result.confidence < 80) {
                        warnings += if (result.confidence < 60) {
                            "Page $pageNumber OCR confidence is below 60."
                        } else {
                            "Page $pageNumber OCR confidence is below 80."
                        }
                    }
                    offset = checkedNextOffset(offset, text)
                }
            }
        } catch (_: InvalidPasswordException) {
            images.values.forEach { Files.deleteIfExists(it) }
            throw DocumentIngestionFailure("encrypted-document-unsupported", "Encrypted PDFs are not supported.")
        } catch (failure: Exception) {
            images.values.forEach { Files.deleteIfExists(it) }
            throw failure
        }
        return ExtractedDocument(upload.document, blocks, warnings, images)
    }

    private fun extractDocx(upload: AcceptedDocumentUpload): ExtractedDocument {
        configurePoiZipSafety()
        val blocks = mutableListOf<LocatedDocumentTextBlock>()
        var offset = 0
        Files.newInputStream(upload.storedFile.path).use { input ->
            XWPFDocument(input).use { document ->
                document.bodyElements.forEach { element ->
                    val extracted = when (element) {
                        is XWPFParagraph -> paragraphText(element)
                        is XWPFTable -> element.rows.joinToString("\n") { row ->
                            row.tableCells.joinToString("\t") { normalizeText(it.text) }
                        }
                        else -> ""
                    }
                    if (extracted.isNotBlank()) {
                        val heading = (element as? XWPFParagraph)?.takeIf { it.style?.startsWith("Heading") == true }?.text
                        blocks += block(
                            upload.document,
                            null,
                            blocks.size,
                            offset,
                            extracted,
                            DocumentExtractionMethod.Docx,
                            heading = heading,
                        )
                        offset = checkedNextOffset(offset, extracted)
                    }
                }
            }
        }
        return requireBlocks(upload.document, blocks)
    }

    private fun extractPlain(
        upload: AcceptedDocumentUpload,
        method: DocumentExtractionMethod,
        markdown: Boolean,
    ): ExtractedDocument {
        val text = normalizeText(Files.readString(upload.storedFile.path, StandardCharsets.UTF_8))
        val blocks = mutableListOf<LocatedDocumentTextBlock>()
        var offset = 0
        text.split(Regex("\\n{2,}")).forEach { raw ->
            val value = raw.trim()
            if (value.isNotBlank()) {
                val heading = if (markdown) {
                    value.lineSequence().firstOrNull()?.takeIf { it.matches(Regex("#{1,6}\\s+.+")) }?.replaceFirst(Regex("^#{1,6}\\s+"), "")
                } else {
                    null
                }
                blocks += block(upload.document, null, blocks.size, offset, value, method, heading = heading)
                offset = checkedNextOffset(offset, value)
            }
        }
        return requireBlocks(upload.document, blocks)
    }

    private fun requireBlocks(document: IngestionDocument, blocks: List<LocatedDocumentTextBlock>): ExtractedDocument {
        if (blocks.isEmpty()) throw DocumentIngestionFailure("document-text-empty", "The document contains no usable text.")
        return ExtractedDocument(document, blocks, emptyList(), emptyMap())
    }

    private fun validateLanguage(extracted: ExtractedDocument): Unit {
        val letters = extracted.blocks.asSequence().flatMap { it.exactText.asSequence() }.filter(Char::isLetter).toList()
        if (letters.size >= 100 && letters.count { Character.UnicodeScript.of(it.code) != Character.UnicodeScript.LATIN } >
            letters.size / 10
        ) {
            throw DocumentIngestionFailure("unsupported-document-language", "Phase 11 supports English-language documents only.")
        }
    }

    private fun block(
        document: IngestionDocument,
        pageNumber: Int?,
        order: Int,
        offset: Int,
        text: String,
        method: DocumentExtractionMethod,
        confidence: Int? = null,
        imageId: String? = null,
        geometry: DocumentPageGeometry? = null,
        rectangles: List<com.entio.core.DocumentTextRectangle> = emptyList(),
        heading: String? = null,
    ): LocatedDocumentTextBlock {
        if (offset + text.length > configuration.maximumExtractedCharacters) {
            throw DocumentIngestionFailure("document-text-limit", "Extracted document text exceeds five million characters.")
        }
        return LocatedDocumentTextBlock(
            id = DocumentTextBlockId(
                "block-${stableId(document.checksumSha256, EXTRACTOR_VERSION, pageNumber?.toString().orEmpty(), order.toString(), offset.toString(), text)}",
            ),
            documentId = document.id,
            safeFilename = document.safeFilename,
            pageNumber = pageNumber,
            sectionHeading = heading,
            blockOrder = order,
            startOffset = offset,
            endOffset = offset + text.length,
            exactText = text,
            extractionMethod = method,
            extractorVersion = EXTRACTOR_VERSION,
            ocrConfidence = confidence,
            pageImageId = imageId,
            pageGeometry = geometry,
            rectangles = rectangles,
        )
    }

    private fun checkedNextOffset(current: Int, text: String): Int {
        val next = current + text.length + 1
        if (next > configuration.maximumExtractedCharacters + 1) {
            throw DocumentIngestionFailure("document-text-limit", "Extracted document text exceeds five million characters.")
        }
        return next
    }

    private fun extractPdfPageText(document: PDDocument, pageNumber: Int): String =
        PDFTextStripper().apply {
            startPage = pageNumber
            endPage = pageNumber
            sortByPosition = true
        }.getText(document)

    private fun paragraphText(paragraph: XWPFParagraph): String = normalizeText(paragraph.text)

    private fun validateGeometry(image: BufferedImage): DocumentPageGeometry {
        if (image.width > MAX_PAGE_DIMENSION || image.height > MAX_PAGE_DIMENSION) {
            throw DocumentIngestionFailure("rendered-page-dimensions", "A rendered PDF page exceeds approved dimensions.")
        }
        return DocumentPageGeometry(image.width, image.height)
    }

    private fun normalizeText(value: String): String = value
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(Regex("[\\t\\x0B\\f ]+"), " ")
        .replace(Regex(" *\\n *"), "\n")
        .trim()

    internal fun isReliableEmbeddedText(text: String): Boolean {
        val nonWhitespace = text.count { !it.isWhitespace() }
        if (nonWhitespace < 30) return false
        val lettersOrNumbers = text.count { !it.isWhitespace() && it.isLetterOrDigit() }
        if (lettersOrNumbers.toDouble() / nonWhitespace < 0.60) return false
        if (Regex("\uFFFD{2,}").containsMatchIn(text)) return false
        return text.lineSequence().any { line -> line.trim().split(Regex("\\s+")).count { it.isNotBlank() } >= 3 }
    }

    private fun stableId(vararg values: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        values.forEach { value ->
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val EXTRACTOR_VERSION: String = "phase-11-extractor-v1-pdfbox-3.0.8-poi-5.5.1"
        const val RENDER_DPI: Float = 150f
        const val MAX_PAGE_DIMENSION: Int = 5_000
        const val MAX_PAGE_IMAGE_BYTES: Long = 10L * 1024L * 1024L

        @Synchronized
        fun configurePoiZipSafety(): Unit {
            ZipSecureFile.setMinInflateRatio(0.01)
            ZipSecureFile.setMaxEntrySize(25L * 1024L * 1024L)
            ZipSecureFile.setMaxTextSize(5_000_000L)
        }
    }
}
