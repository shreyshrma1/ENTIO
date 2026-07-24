package com.entio.core

import java.time.Instant
import java.time.LocalDate

public const val MAX_INGESTION_DOCUMENT_BYTES: Long = 25L * 1024L * 1024L
public const val MAX_INGESTION_DOCUMENTS_PER_TASK: Int = 10
public const val MAX_INGESTION_DOCUMENT_CHARACTERS: Int = 5_000_000
public const val MAX_DOCUMENT_EVIDENCE_EXCERPT_CHARACTERS: Int = 500
public const val MAX_DOCUMENT_EVIDENCE_REFERENCES: Int = 8

@JvmInline
public value class DocumentId(public val value: String) {
    init {
        requireOpaqueDocumentId(value, "Document ID")
    }
}

@JvmInline
public value class DocumentTaskId(public val value: String) {
    init {
        requireOpaqueDocumentId(value, "Document task ID")
    }
}

@JvmInline
public value class DocumentTextBlockId(public val value: String) {
    init {
        requireOpaqueDocumentId(value, "Document text block ID")
    }
}

@JvmInline
public value class DocumentEvidenceId(public val value: String) {
    init {
        requireOpaqueDocumentId(value, "Document evidence ID")
    }
}

public enum class DocumentMediaType {
    Pdf,
    Docx,
    Text,
    Markdown,
}

public enum class DocumentAuthorityStatus {
    Authoritative,
    Supporting,
    Draft,
    Historical,
    Superseded,
    Amendment,
}

public enum class DocumentProcessingStatus {
    Uploaded,
    Extracting,
    Analyzing,
    Matching,
    Comparing,
    PreparingRecommendations,
    AwaitingReview,
    BuildingDraft,
    Validating,
    ReadyForProposalReview,
    BlockedForModel,
    BlockedForClarification,
    BlockedForLimits,
    Completed,
    Cancelled,
    Failed,
}

public enum class DocumentExtractionMethod {
    EmbeddedText,
    Ocr,
    Docx,
    Text,
    Markdown,
}

public enum class DocumentEvidenceType {
    Explicit,
    StronglyImplied,
    ModelingSuggestion,
    CombinedEvidence,
    ExternalOntologyEvidence,
    ReasoningImpact,
}

public data class DocumentAuthorityMetadata(
    public val status: DocumentAuthorityStatus,
    public val businessArea: String? = null,
    public val jurisdiction: String? = null,
    public val effectiveDate: LocalDate? = null,
    public val expirationDate: LocalDate? = null,
    public val relatedDocumentId: DocumentId? = null,
) {
    init {
        requireOptionalDocumentText(businessArea, "Business area", 200)
        requireOptionalDocumentText(jurisdiction, "Jurisdiction", 200)
        require(effectiveDate == null || expirationDate == null || !effectiveDate.isAfter(expirationDate)) {
            "Document effective date must not be after its expiration date."
        }
        require(status in setOf(DocumentAuthorityStatus.Amendment, DocumentAuthorityStatus.Superseded) || relatedDocumentId == null) {
            "Only an amendment or superseded document may reference a related document."
        }
    }
}

public data class IngestionDocument(
    public val id: DocumentId,
    public val taskId: DocumentTaskId,
    public val safeFilename: String,
    public val mediaType: DocumentMediaType,
    public val byteSize: Long,
    public val checksumSha256: String,
    public val projectId: String,
    public val uploaderUserId: String,
    public val uploadedAt: Instant,
    public val authority: DocumentAuthorityMetadata,
    public val language: String = "en",
    public val status: DocumentProcessingStatus = DocumentProcessingStatus.Uploaded,
) {
    init {
        requireSafeDocumentFilename(safeFilename)
        require(byteSize in 1..MAX_INGESTION_DOCUMENT_BYTES) { "Document byte size exceeds the approved bound." }
        requireSha256(checksumSha256, "Document checksum")
        requireNonBlankBounded(projectId, "Project ID")
        requireNonBlankBounded(uploaderUserId, "Uploader user ID")
        require(language == "en") { "Phase 11 supports only explicitly declared English documents." }
    }
}

public data class DocumentPageGeometry(
    public val widthPixels: Int,
    public val heightPixels: Int,
) {
    init {
        require(widthPixels > 0 && heightPixels > 0) { "Document page dimensions must be positive." }
        require(widthPixels <= 5_000 && heightPixels <= 5_000) { "Document page dimensions exceed the approved bound." }
    }
}

public data class DocumentTextRectangle(
    public val left: Double,
    public val top: Double,
    public val right: Double,
    public val bottom: Double,
) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() && it in 0.0..1.0 }) {
            "Document text coordinates must be finite normalized values."
        }
        require(left <= right && top <= bottom) { "Document text coordinates must be ordered." }
    }

    public val stableKey: String
        get() = "$left:$top:$right:$bottom"
}

public data class LocatedDocumentTextBlock(
    public val id: DocumentTextBlockId,
    public val documentId: DocumentId,
    public val safeFilename: String,
    public val pageNumber: Int? = null,
    public val sectionHeading: String? = null,
    public val blockOrder: Int,
    public val startOffset: Int,
    public val endOffset: Int,
    public val exactText: String,
    public val extractionMethod: DocumentExtractionMethod,
    public val extractorVersion: String,
    public val ocrConfidence: Int? = null,
    public val pageImageId: String? = null,
    public val pageGeometry: DocumentPageGeometry? = null,
    public val rectangles: List<DocumentTextRectangle> = emptyList(),
) {
    init {
        requireSafeDocumentFilename(safeFilename)
        require(pageNumber == null || pageNumber > 0) { "Document page number must be one-based." }
        require(extractionMethod != DocumentExtractionMethod.EmbeddedText || pageNumber != null) {
            "Embedded PDF text requires a page number."
        }
        require(extractionMethod != DocumentExtractionMethod.Ocr || pageNumber != null) { "OCR text requires a page number." }
        requireOptionalDocumentText(sectionHeading, "Section heading", 500)
        require(blockOrder >= 0) { "Document block order must not be negative." }
        require(startOffset >= 0 && endOffset > startOffset) { "Document text offsets must define a non-empty range." }
        require(endOffset <= MAX_INGESTION_DOCUMENT_CHARACTERS) { "Document text offsets exceed the approved bound." }
        require(exactText.isNotBlank() && exactText.length == endOffset - startOffset) {
            "Document block text must exactly fill its normalized offset range."
        }
        requireNonBlankBounded(extractorVersion, "Extractor version")
        if (extractionMethod == DocumentExtractionMethod.Ocr) {
            require(ocrConfidence in 0..100) { "OCR confidence must be between 0 and 100." }
            require(!pageImageId.isNullOrBlank()) { "OCR text requires a server-issued page image ID." }
            require(pageGeometry != null) { "OCR text requires page geometry." }
        } else {
            require(ocrConfidence == null && pageImageId == null && pageGeometry == null && rectangles.isEmpty()) {
                "Only OCR text may carry OCR confidence, page images, geometry, or coordinates."
            }
        }
        require(rectangles == rectangles.distinctBy(DocumentTextRectangle::stableKey)) {
            "Document text coordinates must be unique."
        }
    }

    public val stableOrderingKey: String
        get() = listOf(
            pageNumber?.toString()?.padStart(10, '0').orEmpty(),
            blockOrder.toString().padStart(10, '0'),
            startOffset.toString().padStart(10, '0'),
            id.value,
        ).joinToString(":")
}

public data class DocumentEvidenceReference(
    public val id: DocumentEvidenceId,
    public val documentId: DocumentId,
    public val blockId: DocumentTextBlockId,
    public val pageNumber: Int? = null,
    public val sectionHeading: String? = null,
    public val startOffsetInBlock: Int,
    public val endOffsetInBlock: Int,
    public val exactExcerpt: String,
    public val extractionMethod: DocumentExtractionMethod,
    public val ocrConfidence: Int? = null,
) {
    init {
        require(pageNumber == null || pageNumber > 0) { "Evidence page number must be one-based." }
        requireOptionalDocumentText(sectionHeading, "Evidence section heading", 500)
        require(startOffsetInBlock >= 0 && endOffsetInBlock > startOffsetInBlock) {
            "Evidence offsets must define a non-empty range."
        }
        require(exactExcerpt.length == endOffsetInBlock - startOffsetInBlock) {
            "Evidence excerpt must exactly fill its block-relative range."
        }
        require(exactExcerpt.isNotBlank() && exactExcerpt.length <= MAX_DOCUMENT_EVIDENCE_EXCERPT_CHARACTERS) {
            "Evidence excerpt exceeds the approved bound."
        }
        require(extractionMethod == DocumentExtractionMethod.Ocr || ocrConfidence == null) {
            "Only OCR evidence may carry OCR confidence."
        }
        require(extractionMethod != DocumentExtractionMethod.Ocr || ocrConfidence in 0..100) {
            "OCR evidence requires confidence between 0 and 100."
        }
    }

    public val stableOrderingKey: String
        get() = "${documentId.value}:${pageNumber ?: 0}:${blockId.value}:$startOffsetInBlock:${id.value}"
}

public data class DocumentEvidence(
    public val id: DocumentEvidenceId,
    public val type: DocumentEvidenceType,
    public val references: List<DocumentEvidenceReference> = emptyList(),
    public val entioRecordId: String? = null,
) {
    init {
        require(references.size <= MAX_DOCUMENT_EVIDENCE_REFERENCES) {
            "Document evidence exceeds the approved reference bound."
        }
        require(references.map(DocumentEvidenceReference::id).distinct().size == references.size) {
            "Document evidence references must be unique."
        }
        require(references == references.sortedBy(DocumentEvidenceReference::stableOrderingKey)) {
            "Document evidence references must use stable document order."
        }
        when (type) {
            DocumentEvidenceType.ExternalOntologyEvidence,
            DocumentEvidenceType.ReasoningImpact,
            -> {
                require(references.isEmpty()) { "External and reasoning evidence must not masquerade as document excerpts." }
                require(!entioRecordId.isNullOrBlank()) { "External and reasoning evidence require an Entio record ID." }
            }
            DocumentEvidenceType.CombinedEvidence -> {
                require(references.size >= 2) { "Combined evidence requires at least two document references." }
                require(entioRecordId == null) { "Combined document evidence must not carry an external record ID." }
            }
            else -> {
                require(references.isNotEmpty()) { "Document evidence requires at least one exact reference." }
                require(entioRecordId == null) { "Document evidence must not carry an external record ID." }
            }
        }
    }
}

public data class DocumentReviewDecision(
    public val decisionId: String,
    public val recommendationId: String,
    public val actorUserId: String,
    public val decidedAt: Instant,
    public val previousStatus: DocumentRecommendationReviewStatus,
    public val newStatus: DocumentRecommendationReviewStatus,
    public val clarification: String? = null,
) {
    init {
        requireOpaqueDocumentId(decisionId, "Document review decision ID")
        requireOpaqueDocumentId(recommendationId, "Document recommendation ID")
        requireNonBlankBounded(actorUserId, "Document review actor")
        require(previousStatus != newStatus) { "Document review decision must change status." }
        requireOptionalDocumentText(clarification, "Document review clarification", 2_000)
    }
}

public data class DocumentDraftProvenance(
    public val taskId: DocumentTaskId,
    public val recommendationId: String,
    public val decisionId: String,
    public val evidenceIds: List<DocumentEvidenceId>,
    public val modelId: String?,
    public val promptVersion: String?,
    public val extractionMethods: List<DocumentExtractionMethod>,
    public val confidence: Int,
    public val targetSourceId: String?,
    public val normalizedTypedOperationKey: String?,
) {
    init {
        requireOpaqueDocumentId(recommendationId, "Document recommendation ID")
        requireOpaqueDocumentId(decisionId, "Document review decision ID")
        require(evidenceIds.isNotEmpty() && evidenceIds.size <= MAX_DOCUMENT_EVIDENCE_REFERENCES) {
            "Draft provenance requires bounded evidence."
        }
        require(evidenceIds == evidenceIds.distinct().sortedBy(DocumentEvidenceId::value)) {
            "Draft provenance evidence IDs must be sorted and unique."
        }
        require(extractionMethods.isNotEmpty() && extractionMethods == extractionMethods.distinct().sortedBy(Enum<*>::name)) {
            "Draft provenance extraction methods must be sorted and unique."
        }
        require(confidence in 0..100) { "Draft provenance confidence must be between 0 and 100." }
        require((modelId == null) == (promptVersion == null)) {
            "Draft provenance model and prompt version must either both be present or both be absent."
        }
        requireOptionalDocumentText(modelId, "Model ID", 200)
        requireOptionalDocumentText(promptVersion, "Prompt version", 200)
        requireOptionalDocumentText(targetSourceId, "Target source ID", 200)
        requireOptionalDocumentText(normalizedTypedOperationKey, "Typed operation key", 1_000)
    }
}

internal fun requireOpaqueDocumentId(value: String, label: String): Unit {
    require(value.length in 1..200 && value.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]*"))) {
        "$label must be an approved opaque identifier."
    }
}

internal fun requireSha256(value: String, label: String): Unit {
    require(value.matches(Regex("[0-9a-f]{64}"))) { "$label must be a lowercase SHA-256 digest." }
}

internal fun requireNonBlankBounded(value: String, label: String, maximum: Int = 200): Unit {
    require(value.isNotBlank() && value == value.trim() && value.length <= maximum) {
        "$label must be trimmed, nonblank, and at most $maximum characters."
    }
}

internal fun requireOptionalDocumentText(value: String?, label: String, maximum: Int): Unit {
    require(value == null || (value.isNotBlank() && value == value.trim() && value.length <= maximum)) {
        "$label must be absent or trimmed, nonblank, and at most $maximum characters."
    }
}

internal fun requireSafeDocumentFilename(value: String): Unit {
    require(value == value.trim() && value.length in 1..255) { "Document filename must be trimmed and bounded." }
    require(value !in setOf(".", "..")) { "Document filename must not be a path segment." }
    require(value.none { it == '/' || it == '\\' || it.isISOControl() || it in bidiControlCharacters }) {
        "Document filename must be safe display metadata."
    }
}

private val bidiControlCharacters: Set<Char> = setOf(
    '\u202A',
    '\u202B',
    '\u202C',
    '\u202D',
    '\u202E',
    '\u2066',
    '\u2067',
    '\u2068',
    '\u2069',
)
