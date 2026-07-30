package com.entio.web.ingestion

import com.entio.core.MAX_INGESTION_DOCUMENT_BYTES
import com.entio.core.MAX_INGESTION_DOCUMENTS_PER_TASK
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.util.UUID

public data class DocumentIngestionConfiguration(
    val temporaryRoot: Path = Path.of(System.getProperty("java.io.tmpdir"), "entio-document-ingestion-v1"),
    val provenanceRoot: Path = Path.of(System.getProperty("java.io.tmpdir"), "entio-data", "phase-11-provenance-v1"),
    val maximumDocumentBytes: Long = MAX_INGESTION_DOCUMENT_BYTES,
    val maximumDocumentsPerTask: Int = MAX_INGESTION_DOCUMENTS_PER_TASK,
    val maximumPdfPages: Int = 500,
    val maximumActiveTasks: Int = 2,
    val taskLifetime: Duration = Duration.ofHours(24),
    val maximumExtractedCharacters: Int = 5_000_000,
    val maximumOcrPagesPerDocument: Int = 200,
    val maximumRenderedImageBytesPerTask: Long = 512L * 1024L * 1024L,
    val ocrDocumentTimeout: Duration = Duration.ofMinutes(10),
    val tesseract: TesseractConfiguration? = null,
    val clock: Clock = Clock.systemUTC(),
    val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    init {
        require(temporaryRoot.isAbsolute) { "Document temporary root must be absolute." }
        require(provenanceRoot.isAbsolute) { "Document provenance root must be absolute." }
        require(temporaryRoot.normalize() != provenanceRoot.normalize()) {
            "Document temporary and provenance roots must be separate."
        }
        require(maximumDocumentBytes in 1..MAX_INGESTION_DOCUMENT_BYTES) {
            "Document byte limit exceeds the approved bound."
        }
        require(maximumDocumentsPerTask in 1..MAX_INGESTION_DOCUMENTS_PER_TASK) {
            "Document count limit exceeds the approved bound."
        }
        require(maximumPdfPages in 1..500) { "PDF page limit exceeds the approved bound." }
        require(maximumActiveTasks > 0) { "Document ingestion requires a positive server task limit." }
        require(!taskLifetime.isNegative && !taskLifetime.isZero) { "Document task lifetime must be positive." }
        require(maximumExtractedCharacters in 1..5_000_000) { "Extracted text limit exceeds the approved bound." }
        require(maximumOcrPagesPerDocument in 1..200) { "OCR page limit exceeds the approved bound." }
        require(maximumRenderedImageBytesPerTask in 1..512L * 1024L * 1024L) {
            "Rendered image limit exceeds the approved bound."
        }
        require(!ocrDocumentTimeout.isNegative && !ocrDocumentTimeout.isZero &&
            ocrDocumentTimeout <= Duration.ofMinutes(10)
        ) { "OCR timeout exceeds the approved bound." }
    }
}

public class DocumentIngestionFailure(
    public val code: String,
    message: String,
) : IllegalArgumentException(message)
