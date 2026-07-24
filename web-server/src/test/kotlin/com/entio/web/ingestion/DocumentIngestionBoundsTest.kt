package com.entio.web.ingestion

import com.entio.core.MAX_INGESTION_DOCUMENT_BYTES
import com.entio.core.MAX_INGESTION_DOCUMENTS_PER_TASK
import java.nio.file.Files
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DocumentIngestionBoundsTest {
    @Test
    fun configurationCannotExceedApprovedNumericBounds(): Unit {
        val temporary = Files.createTempDirectory("entio-bounds-temporary")
        val provenance = Files.createTempDirectory("entio-bounds-provenance")
        fun configuration(change: (DocumentIngestionConfiguration) -> DocumentIngestionConfiguration): Unit {
            assertFailsWith<IllegalArgumentException> {
                change(DocumentIngestionConfiguration(temporaryRoot = temporary, provenanceRoot = provenance))
            }
        }

        configuration { it.copy(maximumDocumentBytes = MAX_INGESTION_DOCUMENT_BYTES + 1) }
        configuration { it.copy(maximumDocumentsPerTask = MAX_INGESTION_DOCUMENTS_PER_TASK + 1) }
        configuration { it.copy(maximumPdfPages = 501) }
        configuration { it.copy(maximumExtractedCharacters = 5_000_001) }
        configuration { it.copy(maximumOcrPagesPerDocument = 201) }
        configuration { it.copy(maximumRenderedImageBytesPerTask = 512L * 1024L * 1024L + 1) }
        configuration { it.copy(ocrDocumentTimeout = Duration.ofMinutes(10).plusMillis(1)) }
    }
}
