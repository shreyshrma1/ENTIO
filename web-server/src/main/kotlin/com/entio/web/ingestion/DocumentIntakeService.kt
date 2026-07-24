package com.entio.web.ingestion

import com.entio.core.DocumentAuthorityMetadata
import com.entio.core.DocumentAuthorityStatus
import com.entio.core.DocumentId
import com.entio.core.DocumentMediaType
import com.entio.core.DocumentProcessingStatus
import com.entio.core.DocumentTaskId
import com.entio.core.IngestionDocument
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.zip.ZipInputStream
import io.ktor.utils.io.ByteReadChannel

internal data class DocumentUploadMetadata(
    val clientDocumentId: String,
    val filename: String,
    val declaredMediaType: String,
    val language: String,
    val authorityStatus: String,
    val businessArea: String? = null,
    val jurisdiction: String? = null,
    val effectiveDate: String? = null,
    val expirationDate: String? = null,
    val relatedDocumentId: String? = null,
)

internal data class AcceptedDocumentUpload(
    val document: IngestionDocument,
    val storedFile: StoredDocumentFile,
)

internal class DocumentIntakeService(
    private val configuration: DocumentIngestionConfiguration,
    private val storage: DocumentTemporaryStorage,
) {
    fun accept(
        taskId: DocumentTaskId,
        taskDirectory: TemporaryTaskDirectory,
        projectId: String,
        userId: String,
        metadata: DocumentUploadMetadata,
        input: InputStream,
    ): AcceptedDocumentUpload {
        val mediaType = mediaType(metadata.filename, metadata.declaredMediaType)
        val documentId = DocumentId("document-${configuration.idFactory()}")
        val stored = storage.writeDocument(taskDirectory, documentId.value, input, configuration.maximumDocumentBytes)
        return finishAcceptance(taskId, projectId, userId, metadata, mediaType, documentId, stored)
    }

    suspend fun accept(
        taskId: DocumentTaskId,
        taskDirectory: TemporaryTaskDirectory,
        projectId: String,
        userId: String,
        metadata: DocumentUploadMetadata,
        channel: ByteReadChannel,
    ): AcceptedDocumentUpload {
        val mediaType = mediaType(metadata.filename, metadata.declaredMediaType)
        val documentId = DocumentId("document-${configuration.idFactory()}")
        val stored = storage.writeDocument(taskDirectory, documentId.value, channel, configuration.maximumDocumentBytes)
        return finishAcceptance(taskId, projectId, userId, metadata, mediaType, documentId, stored)
    }

    private fun finishAcceptance(
        taskId: DocumentTaskId,
        projectId: String,
        userId: String,
        metadata: DocumentUploadMetadata,
        mediaType: DocumentMediaType,
        documentId: DocumentId,
        stored: StoredDocumentFile,
    ): AcceptedDocumentUpload {
        try {
            preflight(stored.path, mediaType)
            val checksum = sha256(stored.path)
            val document = IngestionDocument(
                id = documentId,
                taskId = taskId,
                safeFilename = metadata.filename,
                mediaType = mediaType,
                byteSize = stored.byteSize,
                checksumSha256 = checksum,
                projectId = projectId,
                uploaderUserId = userId,
                uploadedAt = Instant.now(configuration.clock),
                authority = authority(metadata),
                language = metadata.language,
                status = DocumentProcessingStatus.Uploaded,
            )
            return AcceptedDocumentUpload(document, stored)
        } catch (failure: Exception) {
            Files.deleteIfExists(stored.path)
            throw failure
        }
    }

    private fun mediaType(filename: String, declared: String): DocumentMediaType {
        val extension = filename.substringAfterLast('.', "").lowercase()
        return when {
            extension == "pdf" && declared.equals("application/pdf", ignoreCase = true) -> DocumentMediaType.Pdf
            extension == "docx" && declared.equals(DOCX_MEDIA_TYPE, ignoreCase = true) -> DocumentMediaType.Docx
            extension == "txt" && declared.equals("text/plain", ignoreCase = true) -> DocumentMediaType.Text
            extension == "md" && declared.equals("text/markdown", ignoreCase = true) -> DocumentMediaType.Markdown
            extension !in supportedExtensions -> throw DocumentIngestionFailure(
                "unsupported-document-type",
                "The uploaded document type is not supported.",
            )
            else -> throw DocumentIngestionFailure(
                "document-type-mismatch",
                "The document extension and declared media type do not agree.",
            )
        }
    }

    private fun preflight(path: Path, mediaType: DocumentMediaType): Unit = when (mediaType) {
        DocumentMediaType.Pdf -> preflightPdf(path)
        DocumentMediaType.Docx -> preflightDocx(path)
        DocumentMediaType.Text,
        DocumentMediaType.Markdown,
        -> validateUtf8(path)
    }

    private fun preflightPdf(path: Path): Unit {
        val bytes = Files.readAllBytes(path)
        if (bytes.size < 5 || !bytes.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray(StandardCharsets.US_ASCII))) {
            throw DocumentIngestionFailure("document-signature-mismatch", "The uploaded PDF signature is invalid.")
        }
        val boundedText = bytes.toString(StandardCharsets.ISO_8859_1)
        if (Regex("/Encrypt\\b").containsMatchIn(boundedText)) {
            throw DocumentIngestionFailure("encrypted-document-unsupported", "Encrypted documents are not supported.")
        }
        val pageMarkers = Regex("/Type\\s*/Page\\b").findAll(boundedText).count()
        if (pageMarkers > configuration.maximumPdfPages) {
            throw DocumentIngestionFailure("document-page-limit", "The PDF exceeds the 500-page limit.")
        }
    }

    private fun preflightDocx(path: Path): Unit {
        val signature = Files.newInputStream(path).use { input -> ByteArray(4).also { input.read(it) } }
        if (!signature.contentEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04))) {
            val encryptedOfficeSignature = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte())
            val code = if (signature.contentEquals(encryptedOfficeSignature)) {
                "encrypted-document-unsupported"
            } else {
                "document-signature-mismatch"
            }
            throw DocumentIngestionFailure(code, "The uploaded DOCX package is invalid or encrypted.")
        }

        val names = linkedSetOf<String>()
        var totalExpanded = 0L
        var imageCount = 0
        var imageBytes = 0L
        var hasContentTypes = false
        var hasDocument = false
        try {
            ZipInputStream(Files.newInputStream(path)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    validateZipEntryName(name, names)
                    if (names.size > MAX_DOCX_ENTRIES) {
                        throw DocumentIngestionFailure("docx-entry-limit", "The DOCX package contains too many entries.")
                    }
                    val lowerName = name.lowercase()
                    if (lowerName == "[content_types].xml") hasContentTypes = true
                    if (lowerName == "word/document.xml") hasDocument = true
                    if (lowerName.contains("vbaproject.bin") ||
                        lowerName.startsWith("word/activex/") ||
                        lowerName.startsWith("word/embeddings/") ||
                        lowerName.endsWith(".exe") ||
                        lowerName.endsWith(".dll") ||
                        lowerName == "encryptedpackage"
                    ) {
                        throw DocumentIngestionFailure("unsafe-docx-content", "The DOCX package contains unsupported active or embedded content.")
                    }

                    var entryExpanded = 0L
                    val capture = lowerName == "[content_types].xml" || lowerName.endsWith(".rels")
                    val captured = if (capture) java.io.ByteArrayOutputStream() else null
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        entryExpanded += read
                        totalExpanded += read
                        if (entryExpanded > MAX_DOCX_ENTRY_BYTES || totalExpanded > MAX_DOCX_EXPANDED_BYTES) {
                            throw DocumentIngestionFailure("docx-expansion-limit", "The DOCX package exceeds safe expansion limits.")
                        }
                        if (captured != null) {
                            if (captured.size() + read > MAX_DOCX_INSPECTED_XML_BYTES) {
                                throw DocumentIngestionFailure("docx-xml-limit", "The DOCX package metadata exceeds safe inspection limits.")
                            }
                            captured.write(buffer, 0, read)
                        }
                    }
                    val compressed = entry.compressedSize
                    if (compressed > 0 && entryExpanded > compressed * MAX_DOCX_EXPANSION_RATIO) {
                        throw DocumentIngestionFailure("docx-compression-ratio", "The DOCX package exceeds the safe compression ratio.")
                    }
                    if (lowerName.startsWith("word/media/")) {
                        imageCount += 1
                        imageBytes += entryExpanded
                        if (imageCount > MAX_DOCX_IMAGES || entryExpanded > MAX_DOCX_IMAGE_BYTES || imageBytes > MAX_DOCX_TOTAL_IMAGE_BYTES) {
                            throw DocumentIngestionFailure("docx-image-limit", "The DOCX package exceeds safe image limits.")
                        }
                    }
                    captured?.toString(StandardCharsets.UTF_8)?.let { xml ->
                        if (Regex("""TargetMode\s*=\s*["']External["']""", RegexOption.IGNORE_CASE).containsMatchIn(xml) ||
                            Regex("macroEnabled", RegexOption.IGNORE_CASE).containsMatchIn(xml)
                        ) {
                            throw DocumentIngestionFailure("unsafe-docx-content", "The DOCX package contains macros or external relationships.")
                        }
                    }
                    zip.closeEntry()
                }
            }
        } catch (failure: DocumentIngestionFailure) {
            throw failure
        } catch (failure: IOException) {
            throw DocumentIngestionFailure("document-signature-mismatch", "The uploaded DOCX package is malformed.")
        }
        if (!hasContentTypes || !hasDocument) {
            throw DocumentIngestionFailure("document-signature-mismatch", "The uploaded file is not a supported DOCX document.")
        }
    }

    private fun validateZipEntryName(name: String, names: MutableSet<String>): Unit {
        val normalized = Path.of(name.replace('\\', '/')).normalize().toString().replace('\\', '/')
        if (name.isBlank() ||
            name.startsWith("/") ||
            name.contains('\\') ||
            normalized != name.trimEnd('/') ||
            normalized == ".." ||
            normalized.startsWith("../") ||
            name.any(Char::isISOControl) ||
            !names.add(name)
        ) {
            throw DocumentIngestionFailure("unsafe-docx-entry", "The DOCX package contains an unsafe or duplicate entry.")
        }
    }

    private fun validateUtf8(path: Path): Unit {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        runCatching { decoder.decode(ByteBuffer.wrap(Files.readAllBytes(path))) }
            .getOrElse { throw DocumentIngestionFailure("invalid-utf8", "Text documents must use valid UTF-8.") }
    }

    private fun authority(metadata: DocumentUploadMetadata): DocumentAuthorityMetadata {
        val status = DocumentAuthorityStatus.entries.firstOrNull {
            it.name.equals(metadata.authorityStatus, ignoreCase = true)
        } ?: throw DocumentIngestionFailure("invalid-document-authority", "The document authority status is invalid.")
        return try {
            DocumentAuthorityMetadata(
                status = status,
                businessArea = metadata.businessArea,
                jurisdiction = metadata.jurisdiction,
                effectiveDate = metadata.effectiveDate?.let(LocalDate::parse),
                expirationDate = metadata.expirationDate?.let(LocalDate::parse),
                relatedDocumentId = metadata.relatedDocumentId?.let(::DocumentId),
            )
        } catch (failure: IllegalArgumentException) {
            throw DocumentIngestionFailure("invalid-document-metadata", failure.message ?: "Document metadata is invalid.")
        }
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val DOCX_MEDIA_TYPE: String = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        val supportedExtensions: Set<String> = setOf("pdf", "docx", "txt", "md")
        const val MAX_DOCX_ENTRIES: Int = 1_000
        const val MAX_DOCX_ENTRY_BYTES: Long = 25L * 1024L * 1024L
        const val MAX_DOCX_EXPANDED_BYTES: Long = 100L * 1024L * 1024L
        const val MAX_DOCX_EXPANSION_RATIO: Long = 100L
        const val MAX_DOCX_IMAGES: Int = 100
        const val MAX_DOCX_IMAGE_BYTES: Long = 10L * 1024L * 1024L
        const val MAX_DOCX_TOTAL_IMAGE_BYTES: Long = 50L * 1024L * 1024L
        const val MAX_DOCX_INSPECTED_XML_BYTES: Int = 2 * 1024 * 1024
    }
}
