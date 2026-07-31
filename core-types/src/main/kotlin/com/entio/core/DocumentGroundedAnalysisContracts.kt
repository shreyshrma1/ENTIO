package com.entio.core

public const val MAX_DOCUMENT_RETRIEVAL_CHOICES_PER_CANDIDATE: Int = 20
public const val MAX_DOCUMENT_RETRIEVAL_ALTERNATE_LABELS: Int = 5
public const val MAX_DOCUMENT_RETRIEVAL_STRUCTURAL_IRIS: Int = 5

public enum class DocumentCandidateExtractionCategory {
    Organization,
    Person,
    Location,
    Date,
    Identifier,
    MonetaryAmount,
    ConceptTerm,
    RelationshipPhrase,
    AttributeValue,
    RuleCue,
    Administrative,
    Illustrative,
}

public enum class DocumentCandidateOrigin {
    LocalNlp,
    ModelSupplement,
}

public enum class DocumentCandidateHintRole {
    Subject,
    Predicate,
    Object,
    Attribute,
    Value,
}

public data class DocumentGroundedEvidenceSpan(
    public val evidenceId: DocumentEvidenceId,
    public val referenceId: DocumentEvidenceId,
    public val documentId: DocumentId,
    public val blockId: DocumentTextBlockId,
    public val pageNumber: Int? = null,
    public val section: String? = null,
    public val startOffsetInBlock: Int,
    public val endOffsetInBlock: Int,
    public val exactText: String,
) {
    init {
        require(pageNumber == null || pageNumber > 0)
        requireOptionalDocumentText(section, "Grounded evidence section", 500)
        require(startOffsetInBlock >= 0 && endOffsetInBlock > startOffsetInBlock)
        requireNonBlankBounded(exactText, "Grounded evidence exact text", MAX_DOCUMENT_EVIDENCE_EXCERPT_CHARACTERS)
    }

    public val stableOrderingKey: String
        get() = "${documentId.value}:${blockId.value}:${startOffsetInBlock.toString().padStart(10, '0')}:${referenceId.value}"
}

public data class DocumentCandidateHint(
    public val role: DocumentCandidateHintRole,
    public val text: String,
    public val relatedCandidateId: String? = null,
) {
    init {
        requireNonBlankBounded(text, "Document candidate hint", 500)
        relatedCandidateId?.let { requireOpaqueDocumentId(it, "Related document candidate ID") }
    }

    public val stableOrderingKey: String
        get() = "${role.ordinal.toString().padStart(2, '0')}:$text:${relatedCandidateId.orEmpty()}"
}

public data class DocumentGroundedCandidate(
    public val id: String,
    public val origin: DocumentCandidateOrigin,
    public val category: DocumentCandidateExtractionCategory,
    public val displayText: String,
    public val normalizedText: String,
    public val documentId: DocumentId,
    public val documentChecksumSha256: String,
    public val evidenceSpans: List<DocumentGroundedEvidenceSpan>,
    public val hints: List<DocumentCandidateHint> = emptyList(),
    public val extractorContractVersion: String,
    public val resourceVersion: String,
) {
    init {
        requireOpaqueDocumentId(id, "Grounded document candidate ID")
        requireNonBlankBounded(displayText, "Grounded candidate display text", 2_000)
        requireNonBlankBounded(normalizedText, "Grounded candidate normalized text", 2_000)
        require(normalizedText == normalizedText.lowercase())
        requireSha256(documentChecksumSha256, "Grounded candidate document checksum")
        require(evidenceSpans.isNotEmpty())
        require(evidenceSpans == evidenceSpans.distinctBy(DocumentGroundedEvidenceSpan::referenceId)
            .sortedBy(DocumentGroundedEvidenceSpan::stableOrderingKey))
        require(evidenceSpans.all { it.documentId == documentId })
        require(hints == hints.distinct().sortedBy(DocumentCandidateHint::stableOrderingKey))
        requireNonBlankBounded(extractorContractVersion, "Candidate extractor contract version")
        requireNonBlankBounded(resourceVersion, "Candidate extractor resource version")
    }

    public val stableOrderingKey: String
        get() = "${documentId.value}:${evidenceSpans.first().stableOrderingKey}:${category.ordinal.toString().padStart(2, '0')}:$id"
}

public data class DocumentRetrievalFingerprints(
    public val ontologySha256: String,
    public val currentWorkSha256: String,
    public val provenanceSha256: String,
    public val catalogSha256: String,
) {
    init {
        requireSha256(ontologySha256, "Retrieval ontology fingerprint")
        requireSha256(currentWorkSha256, "Retrieval current-work fingerprint")
        requireSha256(provenanceSha256, "Retrieval provenance fingerprint")
        requireSha256(catalogSha256, "Retrieval catalog fingerprint")
    }
}

public data class DocumentRetrievalStructuralContext(
    public val superclassIris: List<Iri> = emptyList(),
    public val domainIris: List<Iri> = emptyList(),
    public val rangeIris: List<Iri> = emptyList(),
    public val datatypeIris: List<Iri> = emptyList(),
    public val assertedTypeIris: List<Iri> = emptyList(),
) {
    init {
        listOf(superclassIris, domainIris, rangeIris, datatypeIris, assertedTypeIris).forEach { values ->
            require(values.size <= MAX_DOCUMENT_RETRIEVAL_STRUCTURAL_IRIS && values == values.distinct().sortedBy(Iri::value))
        }
    }
}

public data class DocumentRetrievalMatchReason(
    public val kind: String,
    public val detail: String,
    public val points: Int,
) {
    init {
        requireNonBlankBounded(kind, "Retrieval match reason kind", 100)
        requireNonBlankBounded(detail, "Retrieval match reason detail", 500)
        require(points in 0..100)
    }

    public val stableOrderingKey: String get() = "$kind:$detail:${points.toString().padStart(3, '0')}"
}

public data class DocumentOntologyRetrievalSelection(
    public val selectionId: String,
    public val candidateId: String,
    public val canonicalIri: Iri,
    public val kind: SemanticDescriptorKind,
    public val scope: DocumentMatchScope,
    public val sourceId: String,
    public val writable: Boolean,
    public val preferredLabel: String?,
    public val alternateLabels: List<String> = emptyList(),
    public val definition: String? = null,
    public val structuralContext: DocumentRetrievalStructuralContext = DocumentRetrievalStructuralContext(),
    public val score: Int,
    public val matchReasons: List<DocumentRetrievalMatchReason>,
    public val fingerprints: DocumentRetrievalFingerprints,
) {
    init {
        requireOpaqueDocumentId(selectionId, "Ontology retrieval selection ID")
        requireOpaqueDocumentId(candidateId, "Ontology retrieval candidate ID")
        requireNonBlankBounded(sourceId, "Ontology retrieval source ID", 500)
        requireOptionalDocumentText(preferredLabel, "Ontology retrieval preferred label", 500)
        require(alternateLabels.size <= MAX_DOCUMENT_RETRIEVAL_ALTERNATE_LABELS &&
            alternateLabels == alternateLabels.distinct().sorted())
        alternateLabels.forEach { requireNonBlankBounded(it, "Ontology retrieval alternate label", 500) }
        requireOptionalDocumentText(definition, "Ontology retrieval definition", 500)
        require(score in 0..100)
        require(matchReasons.isNotEmpty() && matchReasons == matchReasons.distinct().sortedBy(DocumentRetrievalMatchReason::stableOrderingKey))
        require(scope !in setOf(DocumentMatchScope.Imported, DocumentMatchScope.CuratedFibo) || !writable)
    }

    public val stableOrderingKey: String
        get() = "${(100 - score).toString().padStart(3, '0')}:${scope.ordinal.toString().padStart(2, '0')}:${kind.ordinal.toString().padStart(2, '0')}:${canonicalIri.value}:$sourceId:$selectionId"
}

public data class DocumentOntologyRetrievalResult(
    public val candidateId: String,
    public val queryVersion: String,
    public val rankingVersion: String,
    public val resultVersion: String,
    public val selections: List<DocumentOntologyRetrievalSelection>,
    public val completeAuthorizedScopeSearch: Boolean,
) {
    init {
        requireOpaqueDocumentId(candidateId, "Ontology retrieval result candidate ID")
        requireNonBlankBounded(queryVersion, "Ontology retrieval query version")
        requireNonBlankBounded(rankingVersion, "Ontology retrieval ranking version")
        requireNonBlankBounded(resultVersion, "Ontology retrieval result version")
        require(selections.size <= MAX_DOCUMENT_RETRIEVAL_CHOICES_PER_CANDIDATE)
        require(selections.all { it.candidateId == candidateId })
        require(selections == selections.distinctBy(DocumentOntologyRetrievalSelection::selectionId)
            .sortedBy(DocumentOntologyRetrievalSelection::stableOrderingKey))
    }
}

public enum class DocumentGroundedDisposition {
    ReuseExisting,
    ExtendExisting,
    ProposeNew,
    Unresolved,
    Administrative,
    Illustrative,
}

public enum class DocumentPrerequisiteOrigin {
    DocumentExplicit,
    ModelRecommended,
    ReviewerProvided,
}

public data class DocumentGroundedReference(
    public val role: DocumentSemanticReferenceRole,
    public val targetItemId: String,
    public val prerequisiteOrigin: DocumentPrerequisiteOrigin? = null,
) {
    init {
        requireOpaqueDocumentId(targetItemId, "Grounded referenced item ID")
    }

    public val stableOrderingKey: String
        get() = "${role.ordinal.toString().padStart(2, '0')}:$targetItemId:${prerequisiteOrigin?.ordinal ?: -1}"
}

public data class DocumentGroundedSemanticItem(
    public val id: String,
    public val kind: DocumentSemanticItemKind,
    public val label: String,
    public val definition: String? = null,
    public val literalValue: RdfLiteral? = null,
    public val datatypeIntent: String? = null,
    public val candidateIds: List<String>,
    public val evidenceIds: List<DocumentEvidenceId>,
    public val disposition: DocumentGroundedDisposition,
    public val selectionId: String? = null,
    public val references: List<DocumentGroundedReference> = emptyList(),
    public val rationale: String,
    public val confidence: DocumentConfidenceDimensions,
    public val ambiguity: String? = null,
) {
    init {
        requireOpaqueDocumentId(id, "Grounded semantic item ID")
        requireNonBlankBounded(label, "Grounded semantic item label", 500)
        requireOptionalDocumentText(definition, "Grounded semantic item definition", 2_000)
        requireOptionalDocumentText(datatypeIntent, "Grounded semantic datatype intent", 500)
        require(candidateIds.isNotEmpty() && candidateIds == candidateIds.distinct().sorted())
        candidateIds.forEach { requireOpaqueDocumentId(it, "Grounded semantic item candidate ID") }
        require(evidenceIds.isNotEmpty() && evidenceIds == evidenceIds.distinct().sortedBy(DocumentEvidenceId::value))
        require(references == references.distinct().sortedBy(DocumentGroundedReference::stableOrderingKey))
        requireNonBlankBounded(rationale, "Grounded semantic item rationale", 2_000)
        requireOptionalDocumentText(ambiguity, "Grounded semantic item ambiguity", 2_000)
        if (disposition in setOf(DocumentGroundedDisposition.ReuseExisting, DocumentGroundedDisposition.ExtendExisting)) {
            require(selectionId != null) { "Reuse and extension require a server-issued selection ID." }
        } else {
            require(selectionId == null) { "Only reuse and extension may carry a selection ID." }
        }
        selectionId?.let { requireOpaqueDocumentId(it, "Grounded semantic item selection ID") }
        require((kind == DocumentSemanticItemKind.DatatypeValueAssertion) == (literalValue != null)) {
            "Only a grounded datatype-value assertion requires an explicit literal value."
        }
        require(datatypeIntent == null || kind in setOf(
            DocumentSemanticItemKind.DatatypeProperty,
            DocumentSemanticItemKind.DatatypePropertyRange,
            DocumentSemanticItemKind.DatatypeValueAssertion,
            DocumentSemanticItemKind.ShaclConstraint,
        )) { "Grounded datatype intent is supported only for datatype semantic items." }
    }

    public val stableOrderingKey: String get() = "${kind.ordinal.toString().padStart(2, '0')}:$label:$id"
}

public data class DocumentGroundedCoverageDisposition(
    public val candidateId: String,
    public val itemId: String? = null,
    public val disposition: DocumentGroundedDisposition,
    public val rationale: String,
) {
    init {
        requireOpaqueDocumentId(candidateId, "Grounded coverage candidate ID")
        itemId?.let { requireOpaqueDocumentId(it, "Grounded coverage item ID") }
        requireNonBlankBounded(rationale, "Grounded coverage rationale", 1_000)
        require(itemId != null || disposition in setOf(
            DocumentGroundedDisposition.Unresolved,
            DocumentGroundedDisposition.Administrative,
            DocumentGroundedDisposition.Illustrative,
        ))
    }

    public val stableOrderingKey: String get() = candidateId
}

public data class DocumentGroundedAnalysisResult(
    public val responseVersion: String,
    public val items: List<DocumentGroundedSemanticItem>,
    public val coverage: List<DocumentGroundedCoverageDisposition>,
) {
    init {
        requireNonBlankBounded(responseVersion, "Grounded response version")
        require(items == items.distinctBy(DocumentGroundedSemanticItem::id).sortedBy(DocumentGroundedSemanticItem::stableOrderingKey))
        require(coverage == coverage.distinctBy(DocumentGroundedCoverageDisposition::candidateId)
            .sortedBy(DocumentGroundedCoverageDisposition::stableOrderingKey))
        val itemIds = items.map(DocumentGroundedSemanticItem::id).toSet()
        require(coverage.mapNotNull(DocumentGroundedCoverageDisposition::itemId).all(itemIds::contains))
        require(items.flatMap(DocumentGroundedSemanticItem::candidateIds).toSet() == coverage.map(DocumentGroundedCoverageDisposition::candidateId).toSet())
    }
}

public enum class DocumentGroundedRecommendationStatus {
    Executable,
    Mixed,
    NeedsInput,
    ReviewOnly,
    Blocked,
}

public enum class DocumentEditableGroundedFieldKind {
    Disposition,
    Selection,
    EntityKind,
    Label,
    Definition,
    Source,
    Domain,
    Range,
    Datatype,
    Type,
    Prerequisite,
}

public data class DocumentEditableGroundedField(
    public val id: String,
    public val kind: DocumentEditableGroundedFieldKind,
    public val required: Boolean,
    public val compatibleSelectionIds: List<String> = emptyList(),
    public val safeMessage: String,
) {
    init {
        requireOpaqueDocumentId(id, "Editable grounded field ID")
        require(compatibleSelectionIds == compatibleSelectionIds.distinct().sorted())
        compatibleSelectionIds.forEach { requireOpaqueDocumentId(it, "Compatible retrieval selection ID") }
        requireNonBlankBounded(safeMessage, "Editable grounded field message", 500)
    }
}

public data class DocumentAnalysisCounts(
    public val evidenceBlocks: Int,
    public val nlpCandidatesRetained: Int,
    public val nlpCandidatesRejected: Int,
    public val groundedItemsRetained: Int,
    public val groundedItemsUnresolved: Int,
    public val groundedItemsRejected: Int,
    public val recommendationsExecutable: Int,
    public val recommendationsMixed: Int,
    public val recommendationsNeedsInput: Int,
    public val recommendationsReviewOnly: Int,
    public val recommendationsBlocked: Int,
    public val expandedTypedEdits: Int,
) {
    init {
        require(
            listOf(
                evidenceBlocks,
                nlpCandidatesRetained,
                nlpCandidatesRejected,
                groundedItemsRetained,
                groundedItemsUnresolved,
                groundedItemsRejected,
                recommendationsExecutable,
                recommendationsMixed,
                recommendationsNeedsInput,
                recommendationsReviewOnly,
                recommendationsBlocked,
                expandedTypedEdits,
            ).all { it >= 0 },
        )
    }
}

public enum class DocumentGroundedAnalysisStage(public val providerBacked: Boolean) {
    CandidateExtraction(false),
    OntologyRetrieval(false),
    GroundedModeling(true),
    GroundedVerification(false),
    SemanticAssembly(false),
    DeterministicVerification(false),
    AwaitingReview(false),
}

public data class DocumentGroundedWorkKeyInputs(
    public val version: String,
    public val projectId: String,
    public val taskId: String,
    public val documentInventorySha256: String,
    public val evidenceInventorySha256: String,
    public val candidateInventorySha256: String,
    public val retrievalInventorySha256: String,
    public val ontologySha256: String,
    public val currentWorkSha256: String,
    public val provenanceSha256: String,
    public val fiboSha256: String,
    public val extractorVersion: String,
    public val nlpResourceVersion: String,
    public val rankingVersion: String,
    public val selectedModelId: String,
    public val promptVersion: String,
    public val responseVersion: String,
) {
    init {
        requireNonBlankBounded(version, "Grounded work-key version")
        requireOpaqueDocumentId(projectId, "Grounded work-key project ID")
        requireOpaqueDocumentId(taskId, "Grounded work-key task ID")
        listOf(
            documentInventorySha256,
            evidenceInventorySha256,
            candidateInventorySha256,
            retrievalInventorySha256,
            ontologySha256,
            currentWorkSha256,
            provenanceSha256,
            fiboSha256,
        ).forEach { requireSha256(it, "Grounded work-key fingerprint") }
        listOf(extractorVersion, nlpResourceVersion, rankingVersion, selectedModelId, promptVersion, responseVersion)
            .forEach { requireNonBlankBounded(it, "Grounded work-key version input", 500) }
    }
}
