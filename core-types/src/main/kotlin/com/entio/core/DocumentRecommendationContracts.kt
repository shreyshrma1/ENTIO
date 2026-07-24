package com.entio.core

import java.time.Instant

public const val MAX_DOCUMENT_CANDIDATES: Int = 2_000
public const val MAX_ACCEPTED_DOCUMENT_EDITS: Int = 100
public const val MAX_DOCUMENT_DRAFT_BATCH_SIZE: Int = 20

public enum class DocumentCandidateCategory {
    Class,
    ObjectProperty,
    DatatypeProperty,
    AnnotationValue,
    Individual,
    Label,
    Definition,
    SuperclassRelationship,
    Domain,
    Range,
    TypeAssertion,
    ObjectPropertyAssertion,
    DatatypeValue,
    BusinessRule,
    ShaclConstraint,
    Conflict,
    Ambiguity,
}

public enum class DocumentRecommendationCategory {
    OntologyStructure,
    BusinessFact,
}

public enum class DocumentRecommendationAction {
    Confirm,
    ReuseLocal,
    ReuseImportedOrFibo,
    Extend,
    Revise,
    CreateLocal,
    Split,
    Merge,
    Conflict,
    Supersede,
    InsufficientEvidence,
    Unsupported,
}

public enum class DocumentConfidenceBand {
    High,
    Medium,
    Low,
}

public enum class DocumentRecommendationReviewStatus {
    Pending,
    Accepted,
    Rejected,
    NeedsClarification,
    Drafted,
}

public enum class DocumentMatchScope {
    AppliedLocal,
    Imported,
    PrivateDraft,
    SharedStaging,
    CurrentProposal,
    SameTask,
    DurableProvenance,
    CuratedFibo,
}

public data class DocumentCandidateIdentity(
    public val value: String,
    public val documentChecksumSha256: String,
    public val category: DocumentCandidateCategory,
    public val normalizedValue: String,
    public val evidenceKeys: List<String>,
) {
    init {
        requireOpaqueDocumentId(value, "Document candidate ID")
        requireSha256(documentChecksumSha256, "Document candidate checksum")
        requireNonBlankBounded(normalizedValue, "Normalized document candidate value", 2_000)
        require(evidenceKeys.isNotEmpty() && evidenceKeys.size <= MAX_DOCUMENT_EVIDENCE_REFERENCES) {
            "Document candidate identity requires bounded evidence keys."
        }
        require(evidenceKeys == evidenceKeys.distinct().sorted()) {
            "Document candidate evidence keys must be sorted and unique."
        }
        evidenceKeys.forEach { requireNonBlankBounded(it, "Document candidate evidence key", 1_000) }
    }
}

public data class DocumentCandidate(
    public val identity: DocumentCandidateIdentity,
    public val documentId: DocumentId,
    public val category: DocumentCandidateCategory,
    public val recommendationCategory: DocumentRecommendationCategory,
    public val proposedLabel: String? = null,
    public val proposedValue: RdfTerm? = null,
    public val proposedDefinition: RdfLiteral? = null,
    public val confidence: Int,
    public val evidence: List<DocumentEvidence>,
    public val ambiguityFlags: List<String> = emptyList(),
) {
    init {
        require(identity.category == category) { "Document candidate identity category must match its candidate." }
        require(proposedLabel != null || proposedValue != null || proposedDefinition != null) {
            "Document candidate requires a proposed label, value, or definition."
        }
        requireOptionalDocumentText(proposedLabel, "Document candidate label", 500)
        require(confidence in 0..100) { "Document candidate confidence must be between 0 and 100." }
        require(evidence.flatMap(DocumentEvidence::references).size in 1..MAX_DOCUMENT_EVIDENCE_REFERENCES) {
            "Document candidate requires bounded evidence."
        }
        require(evidence.map(DocumentEvidence::id).distinct().size == evidence.size) {
            "Document candidate evidence must be unique."
        }
        require(ambiguityFlags == ambiguityFlags.distinct().sorted()) {
            "Document candidate ambiguity flags must be sorted and unique."
        }
        require(categorySupportsRecommendationCategory(category, recommendationCategory)) {
            "Document candidate category is incompatible with its schema or fact category."
        }
    }

    public val confidenceBand: DocumentConfidenceBand
        get() = confidence.toDocumentConfidenceBand()

    public val stableOrderingKey: String
        get() = "${documentId.value}:${recommendationCategory.name}:${category.name}:${identity.normalizedValue}:${identity.value}"
}

public data class DocumentMatchCandidate(
    public val scope: DocumentMatchScope,
    public val entityIri: Iri,
    public val sourceId: String,
    public val preferredLabel: String?,
    public val score: Int,
    public val reason: String,
    public val normalizedTypedOperationKey: String? = null,
) {
    init {
        requireNonBlankBounded(sourceId, "Document match source ID")
        requireOptionalDocumentText(preferredLabel, "Document match preferred label", 500)
        require(score in 0..100) { "Document match score must be between 0 and 100." }
        requireNonBlankBounded(reason, "Document match reason", 1_000)
        requireOptionalDocumentText(normalizedTypedOperationKey, "Document match typed operation key", 1_000)
    }

    public val stableOrderingKey: String
        get() = "${score.toString().padStart(3, '0')}:${scope.name}:${entityIri.value}:$sourceId"
}

public data class DocumentAmbiguity(
    public val id: String,
    public val message: String,
    public val candidateIris: List<Iri>,
) {
    init {
        requireOpaqueDocumentId(id, "Document ambiguity ID")
        requireNonBlankBounded(message, "Document ambiguity message", 2_000)
        require(candidateIris.size >= 2 && candidateIris == candidateIris.distinct().sortedBy(Iri::value)) {
            "Document ambiguity requires at least two sorted unique candidate IRIs."
        }
    }
}

public data class DocumentConflictAlternative(
    public val id: String,
    public val description: String,
    public val evidenceIds: List<DocumentEvidenceId>,
    public val affectedEntityIris: List<Iri> = emptyList(),
) {
    init {
        requireOpaqueDocumentId(id, "Document conflict alternative ID")
        requireNonBlankBounded(description, "Document conflict alternative", 2_000)
        require(evidenceIds.isNotEmpty() && evidenceIds == evidenceIds.distinct().sortedBy(DocumentEvidenceId::value)) {
            "Document conflict alternative evidence must be sorted and unique."
        }
        require(affectedEntityIris == affectedEntityIris.distinct().sortedBy(Iri::value)) {
            "Affected ontology entities must be sorted and unique."
        }
    }
}

public data class DocumentConflict(
    public val id: String,
    public val alternatives: List<DocumentConflictAlternative>,
    public val resolutionOptions: List<String>,
) {
    init {
        requireOpaqueDocumentId(id, "Document conflict ID")
        require(alternatives.size >= 2 && alternatives.map(DocumentConflictAlternative::id).distinct().size == alternatives.size) {
            "Document conflict requires at least two unique alternatives."
        }
        require(resolutionOptions.isNotEmpty() && resolutionOptions == resolutionOptions.distinct().sorted()) {
            "Document conflict resolution options must be sorted and unique."
        }
        resolutionOptions.forEach { requireNonBlankBounded(it, "Document conflict resolution option", 500) }
    }
}

public data class DocumentRecommendationDependency(
    public val recommendationId: String,
    public val required: Boolean,
) {
    init {
        requireOpaqueDocumentId(recommendationId, "Document recommendation dependency ID")
    }
}

public data class DocumentRecommendation(
    public val id: String,
    public val candidateIds: List<String>,
    public val type: DocumentCandidateCategory,
    public val category: DocumentRecommendationCategory,
    public val proposedLabel: String? = null,
    public val proposedValue: RdfTerm? = null,
    public val proposedDefinition: RdfLiteral? = null,
    public val action: DocumentRecommendationAction,
    public val confidence: Int,
    public val rationale: String,
    public val evidence: List<DocumentEvidence>,
    public val matches: List<DocumentMatchCandidate> = emptyList(),
    public val selectedMatch: DocumentMatchCandidate? = null,
    public val ambiguities: List<DocumentAmbiguity> = emptyList(),
    public val conflicts: List<DocumentConflict> = emptyList(),
    public val dependencies: List<DocumentRecommendationDependency> = emptyList(),
    public val mandatoryClarificationReasons: List<String> = emptyList(),
    public val targetSourceId: String? = null,
    public val reviewStatus: DocumentRecommendationReviewStatus = DocumentRecommendationReviewStatus.Pending,
    public val relatedDraftItemIds: List<String> = emptyList(),
    public val modelId: String? = null,
    public val promptVersion: String? = null,
) {
    init {
        requireOpaqueDocumentId(id, "Document recommendation ID")
        require(candidateIds.isNotEmpty() && candidateIds == candidateIds.distinct().sorted()) {
            "Document recommendation candidate IDs must be sorted and unique."
        }
        require(categorySupportsRecommendationCategory(type, category)) {
            "Document recommendation type is incompatible with its schema or fact category."
        }
        require(proposedLabel != null || proposedValue != null || proposedDefinition != null) {
            "Document recommendation requires a proposed label, value, or definition."
        }
        requireOptionalDocumentText(proposedLabel, "Document recommendation label", 500)
        require(confidence in 0..100) { "Document recommendation confidence must be between 0 and 100." }
        requireNonBlankBounded(rationale, "Document recommendation rationale", 2_000)
        require(evidence.flatMap(DocumentEvidence::references).size <= MAX_DOCUMENT_EVIDENCE_REFERENCES) {
            "Document recommendation evidence exceeds the approved excerpt bound."
        }
        require(evidence.map(DocumentEvidence::id).distinct().size == evidence.size) {
            "Document recommendation evidence must be unique."
        }
        require(action in setOf(DocumentRecommendationAction.InsufficientEvidence, DocumentRecommendationAction.Unsupported) || evidence.isNotEmpty()) {
            "A supported document recommendation requires evidence."
        }
        require(matches == matches.distinctBy { it.scope to it.entityIri } && matches == matches.sortedWith(documentMatchComparator)) {
            "Document recommendation matches must be unique and deterministically ordered."
        }
        require(selectedMatch == null || matches.contains(selectedMatch)) {
            "Selected document match must be one of the recommendation matches."
        }
        require(action != DocumentRecommendationAction.ReuseLocal || selectedMatch?.scope == DocumentMatchScope.AppliedLocal) {
            "Local reuse requires a selected applied-local match."
        }
        require(action != DocumentRecommendationAction.ReuseImportedOrFibo || selectedMatch?.scope in setOf(DocumentMatchScope.Imported, DocumentMatchScope.CuratedFibo)) {
            "Imported or FIBO reuse requires a selected imported or curated-FIBO match."
        }
        require(action != DocumentRecommendationAction.CreateLocal || selectedMatch == null) {
            "A create-local recommendation must not select an existing entity."
        }
        require(ambiguities.map(DocumentAmbiguity::id).distinct().size == ambiguities.size) {
            "Document recommendation ambiguities must be unique."
        }
        require(conflicts.map(DocumentConflict::id).distinct().size == conflicts.size) {
            "Document recommendation conflicts must be unique."
        }
        require(dependencies == dependencies.distinctBy(DocumentRecommendationDependency::recommendationId).sortedBy(DocumentRecommendationDependency::recommendationId)) {
            "Document recommendation dependencies must be sorted and unique."
        }
        require(mandatoryClarificationReasons == mandatoryClarificationReasons.distinct().sorted()) {
            "Mandatory clarification reasons must be sorted and unique."
        }
        mandatoryClarificationReasons.forEach {
            requireNonBlankBounded(it, "Mandatory clarification reason", 500)
        }
        require(!requiresMandatoryClarification() || mandatoryClarificationReasons.isNotEmpty()) {
            "This document recommendation action or confidence requires clarification."
        }
        require(reviewStatus != DocumentRecommendationReviewStatus.Accepted || mandatoryClarificationReasons.isEmpty()) {
            "A recommendation with unresolved mandatory clarification cannot be accepted."
        }
        requireOptionalDocumentText(targetSourceId, "Document recommendation target source", 200)
        require(relatedDraftItemIds == relatedDraftItemIds.distinct().sorted()) {
            "Related draft item IDs must be sorted and unique."
        }
        relatedDraftItemIds.forEach { requireOpaqueDocumentId(it, "Related draft item ID") }
        require(action !in noDraftActions || relatedDraftItemIds.isEmpty()) {
            "This document recommendation action cannot reference a draft item."
        }
        require(reviewStatus != DocumentRecommendationReviewStatus.Drafted || relatedDraftItemIds.isNotEmpty()) {
            "A drafted recommendation requires a related draft item."
        }
        require(modelId == null || !promptVersion.isNullOrBlank()) {
            "AI-assisted document recommendation requires a prompt version."
        }
        requireOptionalDocumentText(modelId, "Document recommendation model ID", 200)
        requireOptionalDocumentText(promptVersion, "Document recommendation prompt version", 200)
    }

    public val confidenceBand: DocumentConfidenceBand
        get() = confidence.toDocumentConfidenceBand()

    public val stableOrderingKey: String
        get() = "${category.name}:${type.name}:${candidateIds.first()}:$id"

    private fun requiresMandatoryClarification(): Boolean =
        confidence < 60 ||
            ambiguities.isNotEmpty() ||
            conflicts.isNotEmpty() ||
            action in setOf(
                DocumentRecommendationAction.Split,
                DocumentRecommendationAction.Merge,
                DocumentRecommendationAction.Conflict,
                DocumentRecommendationAction.Supersede,
                DocumentRecommendationAction.Revise,
            ) ||
            targetSourceId == null && action !in noDraftActions + setOf(
                DocumentRecommendationAction.Confirm,
                DocumentRecommendationAction.ReuseLocal,
                DocumentRecommendationAction.ReuseImportedOrFibo,
            )

    private companion object {
        val noDraftActions: Set<DocumentRecommendationAction> = setOf(
            DocumentRecommendationAction.Confirm,
            DocumentRecommendationAction.InsufficientEvidence,
            DocumentRecommendationAction.Unsupported,
        )
    }
}

public data class DocumentSummaryHighlight(
    public val id: String,
    public val text: String,
    public val category: DocumentRecommendationCategory,
    public val evidenceIds: List<DocumentEvidenceId>,
    public val recommendationIds: List<String>,
) {
    init {
        requireOpaqueDocumentId(id, "Document summary highlight ID")
        requireNonBlankBounded(text, "Document summary highlight", 1_000)
        require(evidenceIds.isNotEmpty() && evidenceIds == evidenceIds.distinct().sortedBy(DocumentEvidenceId::value)) {
            "Document summary highlight evidence must be sorted and unique."
        }
        require(recommendationIds.isNotEmpty() && recommendationIds == recommendationIds.distinct().sorted()) {
            "Document summary recommendation IDs must be sorted and unique."
        }
        recommendationIds.forEach { requireOpaqueDocumentId(it, "Document summary recommendation ID") }
    }
}

public data class DocumentSummary(
    public val documentId: DocumentId,
    public val purpose: String,
    public val highlights: List<DocumentSummaryHighlight>,
    public val modelId: String,
    public val promptVersion: String,
) {
    init {
        requireNonBlankBounded(purpose, "Document summary purpose", 2_000)
        require(highlights.isNotEmpty() && highlights.map(DocumentSummaryHighlight::id).distinct().size == highlights.size) {
            "Document summary requires unique evidence-linked highlights."
        }
        requireNonBlankBounded(modelId, "Document summary model ID")
        requireNonBlankBounded(promptVersion, "Document summary prompt version")
    }
}

public data class AppliedDocumentIdentity(
    public val documentId: DocumentId,
    public val checksumSha256: String,
    public val safeFilename: String,
) {
    init {
        requireSha256(checksumSha256, "Applied provenance document checksum")
        requireSafeDocumentFilename(safeFilename)
    }
}

public data class AppliedDocumentEvidence(
    public val evidenceId: DocumentEvidenceId,
    public val documentId: DocumentId,
    public val pageNumber: Int?,
    public val blockId: DocumentTextBlockId,
    public val startOffsetInBlock: Int,
    public val endOffsetInBlock: Int,
    public val exactExcerpt: String,
    public val extractionMethod: DocumentExtractionMethod,
    public val extractorVersion: String,
    public val confidence: Int,
) {
    init {
        require(pageNumber == null || pageNumber > 0) { "Applied provenance page number must be one-based." }
        require(startOffsetInBlock >= 0 && endOffsetInBlock > startOffsetInBlock) {
            "Applied provenance evidence offsets must define a non-empty range."
        }
        require(exactExcerpt.length == endOffsetInBlock - startOffsetInBlock && exactExcerpt.isNotBlank()) {
            "Applied provenance excerpt must exactly fill its range."
        }
        require(exactExcerpt.length <= MAX_DOCUMENT_EVIDENCE_EXCERPT_CHARACTERS) {
            "Applied provenance excerpt exceeds the approved bound."
        }
        requireNonBlankBounded(extractorVersion, "Applied provenance extractor version")
        require(confidence in 0..100) { "Applied provenance confidence must be between 0 and 100." }
    }
}

public data class AppliedDocumentDecision(
    public val decisionId: String,
    public val recommendationId: String,
    public val actorUserId: String,
    public val decidedAt: Instant,
    public val status: DocumentRecommendationReviewStatus,
    public val clarification: String?,
) {
    init {
        requireOpaqueDocumentId(decisionId, "Applied provenance decision ID")
        requireOpaqueDocumentId(recommendationId, "Applied provenance recommendation ID")
        requireNonBlankBounded(actorUserId, "Applied provenance actor")
        require(status in setOf(DocumentRecommendationReviewStatus.Accepted, DocumentRecommendationReviewStatus.Drafted)) {
            "Applied provenance requires an accepted or drafted decision."
        }
        requireOptionalDocumentText(clarification, "Applied provenance clarification", 2_000)
    }
}

public data class AppliedDocumentTypedOperation(
    public val stagedItemId: String,
    public val targetSourceId: String,
    public val normalizedTypedOperationKey: String,
    public val targetEntityIri: Iri? = null,
    public val targetAssertionKey: String? = null,
) {
    init {
        requireOpaqueDocumentId(stagedItemId, "Applied provenance staged item ID")
        requireNonBlankBounded(targetSourceId, "Applied provenance target source")
        requireNonBlankBounded(normalizedTypedOperationKey, "Applied provenance typed operation key", 1_000)
        require(targetEntityIri != null || !targetAssertionKey.isNullOrBlank()) {
            "Applied provenance typed operation requires an entity or assertion target."
        }
        requireOptionalDocumentText(targetAssertionKey, "Applied provenance assertion key", 1_000)
    }
}

public data class AppliedDocumentApplyEvent(
    public val proposalId: String?,
    public val appliedByUserId: String,
    public val appliedAt: Instant,
    public val baselineOntologyFingerprint: String,
    public val resultingOntologyFingerprint: String,
) {
    init {
        require(proposalId == null || proposalId.isNotBlank()) { "Applied provenance proposal ID must not be blank." }
        requireNonBlankBounded(appliedByUserId, "Applied provenance apply actor")
        requireNonBlankBounded(baselineOntologyFingerprint, "Applied provenance baseline fingerprint", 500)
        requireNonBlankBounded(resultingOntologyFingerprint, "Applied provenance resulting fingerprint", 500)
    }
}

public data class AppliedDocumentProvenance(
    public val recordId: String,
    public val projectId: String,
    public val taskId: DocumentTaskId,
    public val document: AppliedDocumentIdentity,
    public val evidence: List<AppliedDocumentEvidence>,
    public val recommendationId: String,
    public val action: DocumentRecommendationAction,
    public val decision: AppliedDocumentDecision,
    public val modelId: String?,
    public val promptVersion: String?,
    public val confidence: Int,
    public val evidenceTypes: List<DocumentEvidenceType>,
    public val typedOperation: AppliedDocumentTypedOperation?,
    public val applyEvent: AppliedDocumentApplyEvent,
) {
    init {
        requireOpaqueDocumentId(recordId, "Applied document provenance record ID")
        requireNonBlankBounded(projectId, "Applied provenance project ID")
        require(evidence.isNotEmpty() && evidence.size <= MAX_DOCUMENT_EVIDENCE_REFERENCES) {
            "Applied document provenance requires bounded evidence."
        }
        require(evidence.map(AppliedDocumentEvidence::evidenceId).distinct().size == evidence.size) {
            "Applied document provenance evidence must be unique."
        }
        requireOpaqueDocumentId(recommendationId, "Applied provenance recommendation ID")
        require(decision.recommendationId == recommendationId) {
            "Applied provenance decision must reference its recommendation."
        }
        require(confidence in 0..100) { "Applied provenance confidence must be between 0 and 100." }
        require(evidenceTypes.isNotEmpty() && evidenceTypes == evidenceTypes.distinct().sortedBy(Enum<*>::name)) {
            "Applied provenance evidence types must be sorted and unique."
        }
        require((modelId == null) == (promptVersion == null)) {
            "Applied provenance model and prompt version must either both be present or both be absent."
        }
        requireOptionalDocumentText(modelId, "Applied provenance model ID", 200)
        requireOptionalDocumentText(promptVersion, "Applied provenance prompt version", 200)
        require(action == DocumentRecommendationAction.Confirm || typedOperation != null) {
            "Applied provenance requires a typed operation unless it records confirmation."
        }
        require(action != DocumentRecommendationAction.Confirm || typedOperation == null) {
            "Confirmation provenance must not create a typed operation."
        }
        require(action != DocumentRecommendationAction.Confirm || applyEvent.proposalId == null) {
            "Confirmation-only provenance must not claim an ontology proposal."
        }
        require(evidence.all { it.documentId == document.documentId }) {
            "Applied provenance evidence must belong to its document."
        }
    }
}

public fun Int.toDocumentConfidenceBand(): DocumentConfidenceBand {
    require(this in 0..100) { "Document confidence must be between 0 and 100." }
    return when (this) {
        in 80..100 -> DocumentConfidenceBand.High
        in 60..79 -> DocumentConfidenceBand.Medium
        else -> DocumentConfidenceBand.Low
    }
}

private fun categorySupportsRecommendationCategory(
    candidate: DocumentCandidateCategory,
    recommendation: DocumentRecommendationCategory,
): Boolean = when (candidate) {
    DocumentCandidateCategory.Individual,
    DocumentCandidateCategory.TypeAssertion,
    DocumentCandidateCategory.ObjectPropertyAssertion,
    DocumentCandidateCategory.DatatypeValue,
    -> recommendation == DocumentRecommendationCategory.BusinessFact
    DocumentCandidateCategory.Class,
    DocumentCandidateCategory.ObjectProperty,
    DocumentCandidateCategory.DatatypeProperty,
    DocumentCandidateCategory.Definition,
    DocumentCandidateCategory.SuperclassRelationship,
    DocumentCandidateCategory.Domain,
    DocumentCandidateCategory.Range,
    DocumentCandidateCategory.BusinessRule,
    DocumentCandidateCategory.ShaclConstraint,
    -> recommendation == DocumentRecommendationCategory.OntologyStructure
    DocumentCandidateCategory.AnnotationValue,
    DocumentCandidateCategory.Label,
    DocumentCandidateCategory.Conflict,
    DocumentCandidateCategory.Ambiguity,
    -> true
}

private val documentMatchComparator: Comparator<DocumentMatchCandidate> =
    compareByDescending<DocumentMatchCandidate>(DocumentMatchCandidate::score)
        .thenBy { it.scope.name }
        .thenBy { it.entityIri.value }
        .thenBy(DocumentMatchCandidate::sourceId)
