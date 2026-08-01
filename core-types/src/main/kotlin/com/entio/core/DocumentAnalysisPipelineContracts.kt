package com.entio.core

import java.time.Instant

public const val MAX_DOCUMENT_DISCOVERIES_PER_DOCUMENT: Int = 200
public const val MAX_DOCUMENT_DISCOVERIES_PER_TASK: Int = 2_000
public const val MAX_DOCUMENT_CONNECTED_MODEL_ITEMS_PER_PROVIDER_RESPONSE: Int = 300
public const val MAX_DOCUMENT_EXPANDED_TYPED_EDITS_PER_RECOMMENDATION: Int = 20
public const val MAX_DOCUMENT_PLANNED_LOGICAL_CALLS: Int = 15
public const val MAX_DOCUMENT_PROVIDER_ATTEMPTS: Int = 20
public const val MAX_DOCUMENT_AUTOMATIC_RETRY_ATTEMPTS: Int = 3
public const val MAX_DOCUMENT_RECONSIDERATION_ATTEMPTS: Int = 2
public const val MAX_DOCUMENT_PROVIDER_RESPONSE_CHARACTERS: Int = 1_000_000

/** Version constants shared by neutral task records and server-side stage adapters. */
public object DocumentAnalysisPipelineVersions {
    public const val CANDIDATE_EXTRACTION_CONTRACT: String = "phase-12-candidate-extraction-v2"
    public const val NLP_RESOURCE_SET: String = "phase-12-opennlp-en-1.3-v1"
    public const val RETRIEVAL_QUERY: String = "phase-12-ontology-retrieval-query-v1"
    public const val RETRIEVAL_RANKING: String = "phase-12-ontology-retrieval-ranking-v1"
    public const val RETRIEVAL_RESULT: String = "phase-12-ontology-retrieval-result-v1"
    public const val GROUNDED_PROMPT: String = "phase-12-grounded-model-prompt-v2"
    public const val GROUNDED_REQUEST: String = "phase-12-grounded-model-request-v2"
    public const val GROUNDED_RESPONSE: String = "phase-12-grounded-model-response-v1"
    public const val GROUNDED_VERIFICATION: String = "phase-12-grounded-verification-v1"
    public const val PUBLIC_REVIEW: String = "phase-12-document-review-v2"
    public const val PROGRESS_COUNTS: String = "phase-12-analysis-counts-v2"
    public const val WORK_KEY: String = "phase-12-grounded-work-key-v2"
    public const val BENCHMARK_MANIFEST: String = "phase-12-two-document-benchmark-v2"
    public const val BENCHMARK_SCORING: String = "phase-12-benchmark-scoring-v1"
    public const val DISCOVERY_PROMPT: String = "phase-11-5-document-discovery-v2"
    public const val DISCOVERY_REQUEST: String = "phase-11-5-document-discovery-request-v2"
    public const val DISCOVERY_RESPONSE: String = "phase-11-5-document-discovery-response-v2"
    public const val CONNECTED_MODEL_PROMPT: String = "phase-11-5-connected-model-v2"
    public const val CONNECTED_MODEL_REQUEST: String = "phase-11-5-connected-model-request-v1"
    public const val CONNECTED_MODEL_RESPONSE: String = "phase-11-5-connected-model-response-v2"
    public const val MODEL_CONSOLIDATION_PROMPT: String = "phase-11-5-model-consolidation-v2"
    public const val MODEL_CONSOLIDATION_REQUEST: String = "phase-11-5-model-consolidation-request-v2"
    public const val MODEL_CONSOLIDATION_RESPONSE: String = "phase-11-5-model-consolidation-response-v2"
    public const val PREREQUISITE_COMPLETION_PROMPT: String = "phase-11-5-prerequisite-completion-v1"
    public const val PREREQUISITE_COMPLETION_REQUEST: String = "phase-11-5-prerequisite-completion-request-v1"
    public const val PREREQUISITE_COMPLETION_RESPONSE: String = "phase-11-5-prerequisite-completion-response-v1"
    public const val RECONCILIATION_PROMPT: String = "phase-11-5-reconciliation-v1"
    public const val RECONCILIATION_REQUEST: String = "phase-11-5-reconciliation-request-v1"
    public const val RECONCILIATION_RESPONSE: String = "phase-11-5-reconciliation-response-v1"
    public const val ONTOLOGY_ALIGNMENT_PROMPT: String = "phase-11-5-ontology-alignment-v1"
    public const val ONTOLOGY_ALIGNMENT_REQUEST: String = "phase-11-5-ontology-alignment-request-v1"
    public const val ONTOLOGY_ALIGNMENT_RESPONSE: String = "phase-11-5-ontology-alignment-response-v1"
    public const val MODELING_CRITIC_PROMPT: String = "phase-11-5-modeling-critic-v1"
    public const val MODELING_CRITIC_REQUEST: String = "phase-11-5-modeling-critic-request-v1"
    public const val MODELING_CRITIC_RESPONSE: String = "phase-11-5-modeling-critic-response-v1"
    public const val FINAL_PLAN_PROMPT: String = "phase-11-5-final-plan-v1"
    public const val FINAL_PLAN_REQUEST: String = "phase-11-5-final-plan-request-v1"
    public const val FINAL_PLAN_RESPONSE: String = "phase-11-5-final-plan-response-v1"
    public const val SEMANTIC_PLAN_PROMPT: String = "phase-11-5-plus-semantic-plan-prompt-v1"
    public const val SEMANTIC_PLAN_REQUEST: String = "phase-11-5-plus-semantic-plan-request-v1"
    public const val SEMANTIC_PLAN_RESPONSE: String = "phase-11-5-plus-semantic-plan-response-v1"
    public const val SEMANTIC_PATTERN_REGISTRY: String = "phase-11-5-plus-pattern-registry-v1"
    public const val COMPILER_RESULT: String = "phase-11-5-plus-compiler-result-v1"
    public const val DOCUMENT_REVIEW: String = "phase-11-5-plus-document-review-v1"
}

@JvmInline
public value class DocumentAnalysisWorkKey(public val sha256: String) {
    init {
        requireSha256(sha256, "Document analysis work key")
    }
}

public enum class DocumentAnalysisStage(public val providerBacked: Boolean) {
    Discovery(true),
    ConnectedModeling(true),
    ModelConsolidation(true),
    PrerequisiteCompletion(true),
    Reconciliation(true),
    OntologyAlignment(true),
    ModelingCritic(true),
    FinalPlanning(true),
    SemanticAssembly(false),
    DeterministicVerification(false),
    AwaitingReview(false),
}

public enum class DocumentAnalysisStageState(public val terminal: Boolean) {
    Pending(false),
    Running(false),
    Retrying(false),
    Succeeded(true),
    Incomplete(true),
    Blocked(true),
    Cancelled(true),
    Failed(true),
}

public data class DocumentAnalysisStageRecord(
    public val recordId: String,
    public val stage: DocumentAnalysisStage,
    public val state: DocumentAnalysisStageState,
    public val scopeId: String,
    public val startedAt: Instant? = null,
    public val finishedAt: Instant? = null,
    public val durationMillis: Long? = null,
    public val selectedModelId: String? = null,
    public val promptVersion: String? = null,
    public val requestSchemaVersion: String? = null,
    public val responseSchemaVersion: String? = null,
    public val inputSha256: String? = null,
    public val outputSha256: String? = null,
    public val providerAttemptCount: Int = 0,
    public val completedCount: Int = 0,
    public val totalCount: Int = 0,
    public val safeCode: String? = null,
) {
    init {
        requireOpaqueDocumentId(recordId, "Document analysis stage record ID")
        requireOpaqueDocumentId(scopeId, "Document analysis stage scope ID")
        require(providerAttemptCount >= 0) {
            "Document analysis stage attempt count cannot be negative."
        }
        require(completedCount >= 0 && totalCount >= 0 && completedCount <= totalCount) {
            "Document analysis stage progress counts are invalid."
        }
        requireOptionalDocumentText(safeCode, "Document analysis stage safe code", 200)
        when (state) {
            DocumentAnalysisStageState.Pending -> {
                require(
                    startedAt == null &&
                        finishedAt == null &&
                        durationMillis == null &&
                        inputSha256 == null &&
                        outputSha256 == null &&
                        providerAttemptCount == 0,
                ) {
                    "A pending document analysis stage must not claim execution metadata."
                }
            }
            DocumentAnalysisStageState.Running,
            DocumentAnalysisStageState.Retrying,
            -> {
                require(startedAt != null && finishedAt == null && durationMillis == null) {
                    "An active document analysis stage requires only a start time."
                }
                require(inputSha256 != null && outputSha256 == null) {
                    "An active document analysis stage requires only an input hash."
                }
            }
            else -> {
                require(startedAt != null && finishedAt != null && !finishedAt.isBefore(startedAt)) {
                    "A terminal document analysis stage requires ordered start and finish times."
                }
                require(durationMillis != null && durationMillis >= 0) {
                    "A terminal document analysis stage requires a nonnegative duration."
                }
                require(inputSha256 != null) {
                    "A terminal document analysis stage requires an input hash."
                }
            }
        }
        inputSha256?.let { requireSha256(it, "Document analysis stage input hash") }
        outputSha256?.let { requireSha256(it, "Document analysis stage output hash") }
        require(state != DocumentAnalysisStageState.Succeeded || outputSha256 != null) {
            "A successful document analysis stage requires an output hash."
        }
        require(
            state !in setOf(
                DocumentAnalysisStageState.Incomplete,
                DocumentAnalysisStageState.Blocked,
                DocumentAnalysisStageState.Cancelled,
                DocumentAnalysisStageState.Failed,
            ) || safeCode != null,
        ) {
            "A non-successful terminal stage requires a safe code."
        }
        if (stage.providerBacked && state != DocumentAnalysisStageState.Pending) {
            require(
                listOf(
                    selectedModelId,
                    promptVersion,
                    requestSchemaVersion,
                    responseSchemaVersion,
                ).all { !it.isNullOrBlank() },
            ) {
                "A provider-backed stage requires model, prompt, request, and response versions."
            }
            require(providerAttemptCount > 0) {
                "A started provider-backed stage requires at least one provider attempt."
            }
        } else if (!stage.providerBacked) {
            require(
                selectedModelId == null &&
                    promptVersion == null &&
                    requestSchemaVersion == null &&
                    responseSchemaVersion == null &&
                    providerAttemptCount == 0,
            ) {
                "A deterministic stage must not claim provider metadata."
            }
        }
        requireOptionalDocumentText(selectedModelId, "Document analysis selected model ID", 200)
        requireOptionalDocumentText(promptVersion, "Document analysis prompt version", 200)
        requireOptionalDocumentText(requestSchemaVersion, "Document analysis request schema version", 200)
        requireOptionalDocumentText(responseSchemaVersion, "Document analysis response schema version", 200)
    }

    public val stableOrderingKey: String
        get() = "${stage.ordinal.toString().padStart(2, '0')}:$scopeId:$recordId"
}

public enum class DocumentDiscoveryKind {
    Concept,
    Definition,
    Individual,
    Relationship,
    Attribute,
    Value,
    Requirement,
    Control,
    ConditionalRule,
    Conflict,
    Ambiguity,
    Role,
    Metadata,
}

public enum class DocumentContentClassification {
    BusinessContent,
    AdministrativeMetadata,
}

public enum class DocumentAssertionClassification {
    ExplicitFact,
    ImpliedFact,
    ModelInterpretation,
    IllustrativeExample,
}

public enum class DocumentIndividualClassification {
    Illustrative,
    Production,
    Ambiguous,
    Unknown,
}

public data class DocumentDiscovery(
    public val id: String,
    public val documentId: DocumentId,
    public val kind: DocumentDiscoveryKind,
    public val contentClassification: DocumentContentClassification,
    public val assertionClassification: DocumentAssertionClassification,
    public val description: String,
    public val evidence: List<DocumentEvidence>,
    public val relatedDiscoveryIds: List<String> = emptyList(),
    public val evidenceConfidence: Int,
    public val individualClassification: DocumentIndividualClassification? = null,
) {
    init {
        requireOpaqueDocumentId(id, "Document discovery ID")
        requireNonBlankBounded(description, "Document discovery description", 2_000)
        require(evidence.isNotEmpty() && evidence.size <= MAX_DOCUMENT_EVIDENCE_REFERENCES) {
            "A document discovery requires bounded verified evidence."
        }
        require(evidence == evidence.distinctBy(DocumentEvidence::id).sortedBy { it.id.value }) {
            "Document discovery evidence must be sorted and unique."
        }
        require(evidence.flatMap(DocumentEvidence::references).all { it.documentId == documentId }) {
            "Document discovery evidence must belong to its document."
        }
        require(relatedDiscoveryIds == relatedDiscoveryIds.distinct().sorted() && id !in relatedDiscoveryIds) {
            "Related document discoveries must be sorted, unique, and different from the discovery."
        }
        relatedDiscoveryIds.forEach { requireOpaqueDocumentId(it, "Related document discovery ID") }
        require(evidenceConfidence in 0..100) {
            "Document discovery evidence confidence must be between 0 and 100."
        }
        require((kind == DocumentDiscoveryKind.Individual) == (individualClassification != null)) {
            "Only an individual discovery requires an individual classification."
        }
    }

    public val stableOrderingKey: String
        get() = "${documentId.value}:${kind.name}:$id"
}

public enum class DocumentConnectedModelItemKind {
    Class,
    ObjectProperty,
    DatatypeProperty,
    AnnotationProperty,
    SubclassRelationship,
    DomainAssignment,
    RangeAssignment,
    Individual,
    TypeAssertion,
    ObjectPropertyAssertion,
    DatatypeValueAssertion,
    NodeShape,
    PropertyShape,
    Constraint,
    ComplexRule,
}

public enum class DocumentConnectedModelReferenceRole {
    Subclass,
    Superclass,
    Property,
    Domain,
    Range,
    Subject,
    Predicate,
    Object,
    Individual,
    Type,
    Shape,
    TargetClass,
    Path,
    ConstraintTarget,
    Related,
}

public data class DocumentConnectedModelReference(
    public val role: DocumentConnectedModelReferenceRole,
    public val itemId: String,
) {
    init {
        requireOpaqueDocumentId(itemId, "Connected model referenced item ID")
    }

    public val stableOrderingKey: String
        get() = "${role.ordinal.toString().padStart(2, '0')}:$itemId"
}

public data class DocumentConnectedModelItem(
    public val id: String,
    public val kind: DocumentConnectedModelItemKind,
    public val label: String,
    public val rationale: String,
    public val discoveryIds: List<String>,
    public val references: List<DocumentConnectedModelReference> = emptyList(),
    public val literalValue: RdfLiteral? = null,
    public val datatypeIntent: String? = null,
    public val order: Int,
    public val reviewOnlyEligible: Boolean = false,
    public val modelRecommended: Boolean = false,
    public val reviewerInputRequired: Boolean = false,
) {
    init {
        requireOpaqueDocumentId(id, "Connected model item ID")
        requireNonBlankBounded(label, "Connected model item label", 500)
        requireNonBlankBounded(rationale, "Connected model item rationale", 2_000)
        require(discoveryIds.isNotEmpty() && discoveryIds == discoveryIds.distinct().sorted()) {
            "Connected model discovery references must be sorted, unique, and nonempty."
        }
        discoveryIds.forEach { requireOpaqueDocumentId(it, "Connected model discovery ID") }
        require(
            references == references.distinct().sortedBy(DocumentConnectedModelReference::stableOrderingKey) &&
                references.none { it.itemId == id },
        ) {
            "Connected model item references must be sorted, unique, and non-self-referential."
        }
        require(order >= 0) { "Connected model item order must not be negative." }
        require(kind != DocumentConnectedModelItemKind.ComplexRule || reviewOnlyEligible) {
            "A complex connected-model rule must remain eligible for review-only handling."
        }
        require((kind == DocumentConnectedModelItemKind.DatatypeValueAssertion) == (literalValue != null)) {
            "Only a datatype-value assertion requires a literal value."
        }
        require(datatypeIntent == null || kind == DocumentConnectedModelItemKind.RangeAssignment) {
            "Only a datatype range assignment may carry datatype intent."
        }
        datatypeIntent?.let(::Iri)
        requireReferenceRoles()
    }

    public val referencedItemIds: List<String>
        get() = references.map(DocumentConnectedModelReference::itemId).distinct().sorted()

    private fun requireReferenceRoles(): Unit {
        val roles = references.map(DocumentConnectedModelReference::role)
        if (kind == DocumentConnectedModelItemKind.RangeAssignment && datatypeIntent != null) {
            val allowed = listOf(
                listOf(DocumentConnectedModelReferenceRole.Property),
                listOf(DocumentConnectedModelReferenceRole.Property, DocumentConnectedModelReferenceRole.Range),
            )
            require(allowed.any { expected ->
                roles.sortedBy(DocumentConnectedModelReferenceRole::ordinal) ==
                    expected.sortedBy(DocumentConnectedModelReferenceRole::ordinal)
            }) {
                "A datatype range requires a property reference and may retain its grounded range item."
            }
            return
        }
        val expectedRoles = when (kind) {
            DocumentConnectedModelItemKind.SubclassRelationship ->
                listOf(DocumentConnectedModelReferenceRole.Subclass, DocumentConnectedModelReferenceRole.Superclass)
            DocumentConnectedModelItemKind.DomainAssignment ->
                listOf(DocumentConnectedModelReferenceRole.Property, DocumentConnectedModelReferenceRole.Domain)
            DocumentConnectedModelItemKind.RangeAssignment ->
                listOf(DocumentConnectedModelReferenceRole.Property, DocumentConnectedModelReferenceRole.Range)
            DocumentConnectedModelItemKind.TypeAssertion ->
                listOf(DocumentConnectedModelReferenceRole.Individual, DocumentConnectedModelReferenceRole.Type)
            DocumentConnectedModelItemKind.ObjectPropertyAssertion ->
                listOf(
                    DocumentConnectedModelReferenceRole.Subject,
                    DocumentConnectedModelReferenceRole.Predicate,
                    DocumentConnectedModelReferenceRole.Object,
                )
            DocumentConnectedModelItemKind.DatatypeValueAssertion ->
                listOf(DocumentConnectedModelReferenceRole.Subject, DocumentConnectedModelReferenceRole.Predicate)
            DocumentConnectedModelItemKind.NodeShape ->
                listOf(DocumentConnectedModelReferenceRole.TargetClass)
            DocumentConnectedModelItemKind.PropertyShape ->
                listOf(DocumentConnectedModelReferenceRole.Shape, DocumentConnectedModelReferenceRole.Path)
            DocumentConnectedModelItemKind.Constraint ->
                listOf(DocumentConnectedModelReferenceRole.ConstraintTarget)
            DocumentConnectedModelItemKind.Class,
            DocumentConnectedModelItemKind.ObjectProperty,
            DocumentConnectedModelItemKind.DatatypeProperty,
            DocumentConnectedModelItemKind.AnnotationProperty,
            DocumentConnectedModelItemKind.Individual,
            -> emptyList()
            DocumentConnectedModelItemKind.ComplexRule -> null
        }
        if (expectedRoles == null) {
            require(references.isNotEmpty() && roles.all { it == DocumentConnectedModelReferenceRole.Related }) {
                "A complex rule requires one or more related model items."
            }
        } else {
            require(roles.sortedBy(DocumentConnectedModelReferenceRole::ordinal) ==
                expectedRoles.sortedBy(DocumentConnectedModelReferenceRole::ordinal)) {
                "Connected model reference roles are incompatible with the item kind."
            }
        }
    }
}

public data class DocumentConnectedModel(
    public val items: List<DocumentConnectedModelItem>,
) {
    init {
        require(items == items.sortedBy(DocumentConnectedModelItem::order)) {
            "Connected model items must use deterministic order."
        }
        require(items.map(DocumentConnectedModelItem::id).distinct().size == items.size) {
            "Connected model item IDs must be unique."
        }
        require(items.map(DocumentConnectedModelItem::order) == items.indices.toList()) {
            "Connected model item order must be contiguous and zero-based."
        }
        val positions = items.associate { it.id to it.order }
        items.forEach { item ->
            item.referencedItemIds.forEach { referencedId ->
                require(positions.getOrElse(referencedId) {
                    throw IllegalArgumentException("Connected model reference is unresolved.")
                } < item.order) {
                    "Connected model references must point to earlier declarations."
                }
            }
        }
    }
}

public enum class DocumentReconciliationKind {
    Duplicate,
    AlternateLabel,
    Supports,
    Refines,
    Conflict,
    SupersessionClaim,
    ContextSpecific,
}

public data class DocumentReconciliationRecord(
    public val id: String,
    public val kind: DocumentReconciliationKind,
    public val participantIds: List<String>,
    public val evidenceIds: List<DocumentEvidenceId> = emptyList(),
    public val priorProvenanceIds: List<String> = emptyList(),
    public val explanation: String,
    public val humanDecisionRequired: Boolean,
) {
    init {
        requireOpaqueDocumentId(id, "Document reconciliation ID")
        require(participantIds.size >= 2 && participantIds == participantIds.distinct().sorted()) {
            "Document reconciliation requires at least two sorted unique participants."
        }
        participantIds.forEach { requireOpaqueDocumentId(it, "Document reconciliation participant ID") }
        require(evidenceIds == evidenceIds.distinct().sortedBy(DocumentEvidenceId::value)) {
            "Document reconciliation evidence IDs must be sorted and unique."
        }
        require(priorProvenanceIds == priorProvenanceIds.distinct().sorted()) {
            "Document reconciliation provenance IDs must be sorted and unique."
        }
        priorProvenanceIds.forEach { requireOpaqueDocumentId(it, "Prior document provenance ID") }
        requireNonBlankBounded(explanation, "Document reconciliation explanation", 2_000)
        require(
            kind !in setOf(DocumentReconciliationKind.Conflict, DocumentReconciliationKind.SupersessionClaim) ||
                humanDecisionRequired,
        ) {
            "Document conflicts and supersession claims require a human decision."
        }
    }

    public val stableOrderingKey: String
        get() = "${kind.name}:${participantIds.joinToString(":")}:$id"
}

public enum class DocumentAlignmentAction {
    Reuse,
    Extend,
    Revise,
    Create,
    Split,
    Merge,
    ConflictReview,
    LeaveUnchanged,
    Unsupported,
}

public data class DocumentAlignmentTarget(
    public val scope: DocumentMatchScope,
    public val entityIri: Iri,
    public val sourceId: String,
) {
    init {
        requireNonBlankBounded(sourceId, "Document alignment source ID", 200)
    }

    public val stableOrderingKey: String
        get() = "${scope.name}:${entityIri.value}:$sourceId"
}

public data class DocumentAlignmentRecord(
    public val id: String,
    public val modelItemId: String,
    public val action: DocumentAlignmentAction,
    public val advisedTargets: List<DocumentAlignmentTarget> = emptyList(),
    public val targetSourceId: String? = null,
    public val rationale: String,
    public val ontologyFitConfidence: Int,
    public val ontologyFingerprint: String,
    public val currentWorkFingerprint: String,
) {
    init {
        requireOpaqueDocumentId(id, "Document alignment ID")
        requireOpaqueDocumentId(modelItemId, "Document alignment model item ID")
        require(advisedTargets == advisedTargets.distinct().sortedBy(DocumentAlignmentTarget::stableOrderingKey)) {
            "Document alignment targets must be sorted and unique."
        }
        require(action != DocumentAlignmentAction.Create || advisedTargets.isEmpty()) {
            "A create alignment must not claim an existing target."
        }
        require(action != DocumentAlignmentAction.Reuse || advisedTargets.isNotEmpty()) {
            "A reuse alignment requires an advised target."
        }
        requireOptionalDocumentText(targetSourceId, "Document alignment target source ID", 200)
        requireNonBlankBounded(rationale, "Document alignment rationale", 2_000)
        require(ontologyFitConfidence in 0..100) {
            "Document alignment confidence must be between 0 and 100."
        }
        requireNonBlankBounded(ontologyFingerprint, "Document alignment ontology fingerprint", 500)
        requireNonBlankBounded(currentWorkFingerprint, "Document alignment current-work fingerprint", 500)
    }

    public val stableOrderingKey: String
        get() = "$modelItemId:${action.name}:$id"
}

public enum class DocumentCriticAction {
    Approve,
    Revise,
    Split,
    Replace,
    Downgrade,
    Reject,
    RequestClarification,
}

public data class DocumentConfidenceDimensions(
    public val evidence: Int,
    public val modeling: Int,
    public val ontologyFit: Int,
    public val overall: Int = minOf(evidence, modeling, ontologyFit),
) {
    init {
        require(listOf(evidence, modeling, ontologyFit, overall).all { it in 0..100 }) {
            "Document confidence dimensions must be between 0 and 100."
        }
        require(overall == minOf(evidence, modeling, ontologyFit)) {
            "Overall document confidence must equal the weakest confidence dimension."
        }
    }
}

public data class DocumentConfidenceDowngrade(
    public val evidence: Int? = null,
    public val modeling: Int? = null,
    public val ontologyFit: Int? = null,
) {
    init {
        require(listOf(evidence, modeling, ontologyFit).any { it != null }) {
            "A confidence downgrade requires at least one dimension."
        }
        require(listOfNotNull(evidence, modeling, ontologyFit).all { it in 0..100 }) {
            "A confidence downgrade must remain between 0 and 100."
        }
    }
}

public data class DocumentCriticFinding(
    public val id: String,
    public val targetId: String,
    public val action: DocumentCriticAction,
    public val reason: String,
    public val confidenceDowngrade: DocumentConfidenceDowngrade? = null,
) {
    init {
        requireOpaqueDocumentId(id, "Document critic finding ID")
        requireOpaqueDocumentId(targetId, "Document critic target ID")
        requireNonBlankBounded(reason, "Document critic finding reason", 2_000)
        require((action == DocumentCriticAction.Downgrade) == (confidenceDowngrade != null)) {
            "Only a confidence downgrade finding carries downgraded dimensions."
        }
    }

    public val stableOrderingKey: String
        get() = "$targetId:${action.name}:$id"
}

public enum class DocumentCriticDispositionKind {
    AcceptedAndIncorporated,
    RejectedWithRationale,
    Unresolved,
}

public data class DocumentCriticDisposition(
    public val findingId: String,
    public val kind: DocumentCriticDispositionKind,
    public val rationale: String? = null,
) {
    init {
        requireOpaqueDocumentId(findingId, "Document critic finding ID")
        requireOptionalDocumentText(rationale, "Document critic disposition rationale", 2_000)
        require((kind == DocumentCriticDispositionKind.RejectedWithRationale) == (rationale != null)) {
            "Only a rejected critic finding requires a rationale."
        }
    }

    public val stableOrderingKey: String
        get() = "$findingId:${kind.name}"
}

public enum class DocumentTemporaryReferenceKind(public val token: String) {
    Class("class"),
    ObjectProperty("objectProperty"),
    DatatypeProperty("datatypeProperty"),
    AnnotationProperty("annotationProperty"),
    Individual("individual"),
    Shape("shape"),
    ;

    public companion object {
        public fun fromToken(token: String): DocumentTemporaryReferenceKind =
            entries.firstOrNull { it.token == token }
                ?: throw IllegalArgumentException("Document temporary reference kind is unsupported.")
    }
}

@JvmInline
public value class DocumentTemporaryReference(public val value: String) {
    init {
        require(TEMPORARY_REFERENCE_PATTERN.matches(value)) {
            "Document temporary reference must use new:<kind>:<localName>."
        }
        DocumentTemporaryReferenceKind.fromToken(value.substringAfter("new:").substringBefore(':'))
    }

    public val kind: DocumentTemporaryReferenceKind
        get() = DocumentTemporaryReferenceKind.fromToken(value.substringAfter("new:").substringBefore(':'))

    public val localName: String
        get() = value.substringAfterLast(':')

    private companion object {
        val TEMPORARY_REFERENCE_PATTERN: Regex =
            Regex("new:(class|objectProperty|datatypeProperty|annotationProperty|individual|shape):[A-Za-z][A-Za-z0-9_]*")
    }
}

public enum class DocumentPlanOperationKind(
    public val declarationKind: DocumentTemporaryReferenceKind? = null,
) {
    CreateClass(DocumentTemporaryReferenceKind.Class),
    CreateObjectProperty(DocumentTemporaryReferenceKind.ObjectProperty),
    CreateDatatypeProperty(DocumentTemporaryReferenceKind.DatatypeProperty),
    CreateAnnotationProperty(DocumentTemporaryReferenceKind.AnnotationProperty),
    CreateIndividual(DocumentTemporaryReferenceKind.Individual),
    SetEntityLabel,
    AddSuperclass,
    RemoveSuperclass,
    SetPropertyDomain,
    RemovePropertyDomain,
    SetPropertyRange,
    RemovePropertyRange,
    AssignType,
    AddObjectPropertyAssertion,
    AddDatatypePropertyAssertion,
    AddDefinition,
    ReplaceDefinition,
    RemoveDefinition,
    AddAlternateLabel,
    ReplaceAlternateLabel,
    RemoveAlternateLabel,
    AddAnnotation,
    RemoveAnnotation,
    ReuseExternal,
    CreateNodeShape(DocumentTemporaryReferenceKind.Shape),
    CreatePropertyShape(DocumentTemporaryReferenceKind.Shape),
    UpdateShaclConstraint,
    RemoveShaclConstraint,
    UpdateShapeLabel,
    DeleteShape,
}

public sealed interface DocumentPlanOperand {
    public data class ExistingEntity(public val iri: Iri) : DocumentPlanOperand
    public data class TemporaryEntity(public val reference: DocumentTemporaryReference) : DocumentPlanOperand
    public data class LiteralValue(public val value: RdfLiteral) : DocumentPlanOperand

    public data class TextValue(public val value: String) : DocumentPlanOperand {
        init {
            requireNonBlankBounded(value, "Document plan text operand", 2_000)
        }
    }

    public data class IntegerValue(public val value: Int) : DocumentPlanOperand

    public data class DecimalValue(public val lexicalForm: String) : DocumentPlanOperand {
        init {
            requireNonBlankBounded(lexicalForm, "Document plan decimal operand", 200)
        }
    }

    public data class SourceId(public val value: String) : DocumentPlanOperand {
        init {
            requireNonBlankBounded(value, "Document plan source ID", 200)
        }
    }
}

public data class DocumentPlanOperation(
    public val id: String,
    public val kind: DocumentPlanOperationKind,
    public val order: Int,
    public val declaration: DocumentTemporaryReference? = null,
    public val operands: List<DocumentPlanOperand> = emptyList(),
    public val dependsOnOperationIds: List<String> = emptyList(),
    public val expandedTypedEditCount: Int,
    public val optionalLeaf: Boolean = false,
    public val modelRecommended: Boolean = false,
    public val reviewerInputRequired: Boolean = false,
) {
    init {
        requireOpaqueDocumentId(id, "Document plan operation ID")
        require(order >= 0) { "Document plan operation order must not be negative." }
        require((kind.declarationKind == null) == (declaration == null)) {
            "Only a declaring operation may carry a temporary declaration."
        }
        require(declaration == null || declaration.kind == kind.declarationKind) {
            "Document plan declaration kind is incompatible with its operation."
        }
        require(dependsOnOperationIds == dependsOnOperationIds.distinct().sorted() && id !in dependsOnOperationIds) {
            "Document plan dependencies must be sorted, unique, and non-self-referential."
        }
        dependsOnOperationIds.forEach { requireOpaqueDocumentId(it, "Document plan dependency ID") }
        require(expandedTypedEditCount in 1..MAX_DOCUMENT_EXPANDED_TYPED_EDITS_PER_RECOMMENDATION) {
            "Document plan operation expanded edit count exceeds the approved bound."
        }
    }

    public val referencedTemporaryEntities: List<DocumentTemporaryReference>
        get() = operands.filterIsInstance<DocumentPlanOperand.TemporaryEntity>()
            .map(DocumentPlanOperand.TemporaryEntity::reference)
            .distinct()
            .sortedBy(DocumentTemporaryReference::value)
}

public data class DocumentReviewOnlyFinding(
    public val id: String,
    public val summary: String,
    public val reason: String,
    public val discoveryIds: List<String>,
    public val evidenceIds: List<DocumentEvidenceId>,
    public val relatedOperationIds: List<String> = emptyList(),
) {
    init {
        requireOpaqueDocumentId(id, "Document review-only finding ID")
        requireNonBlankBounded(summary, "Document review-only finding summary", 1_000)
        requireNonBlankBounded(reason, "Document review-only finding reason", 2_000)
        require(discoveryIds.isNotEmpty() && discoveryIds == discoveryIds.distinct().sorted()) {
            "Document review-only discoveries must be sorted, unique, and nonempty."
        }
        discoveryIds.forEach { requireOpaqueDocumentId(it, "Document review-only discovery ID") }
        require(evidenceIds.isNotEmpty() && evidenceIds == evidenceIds.distinct().sortedBy(DocumentEvidenceId::value)) {
            "Document review-only evidence IDs must be sorted, unique, and nonempty."
        }
        require(relatedOperationIds == relatedOperationIds.distinct().sorted()) {
            "Document review-only operation IDs must be sorted and unique."
        }
        relatedOperationIds.forEach { requireOpaqueDocumentId(it, "Document review-only operation ID") }
    }
}

public enum class DocumentCoverageDispositionKind {
    ExecutableRecommendation,
    ReviewOnlyFinding,
    MatchedExisting,
    MergedIntoAnotherDiscovery,
    Duplicate,
    AdministrativeMetadata,
    IllustrativeExample,
    Unsupported,
    RejectedWithRationale,
    Blocked,
}

public data class DocumentCoverageDisposition(
    public val discoveryId: String,
    public val kind: DocumentCoverageDispositionKind,
    public val recommendationId: String? = null,
    public val relatedDiscoveryId: String? = null,
    public val alignmentId: String? = null,
    public val rationale: String? = null,
) {
    init {
        requireOpaqueDocumentId(discoveryId, "Document coverage discovery ID")
        recommendationId?.let { requireOpaqueDocumentId(it, "Document coverage recommendation ID") }
        relatedDiscoveryId?.let { requireOpaqueDocumentId(it, "Related document coverage discovery ID") }
        alignmentId?.let { requireOpaqueDocumentId(it, "Document coverage alignment ID") }
        requireOptionalDocumentText(rationale, "Document coverage rationale", 2_000)
        require(
            (kind in setOf(
                DocumentCoverageDispositionKind.ExecutableRecommendation,
                DocumentCoverageDispositionKind.ReviewOnlyFinding,
            )) == (recommendationId != null),
        ) {
            "Only recommendation coverage outcomes require a recommendation ID."
        }
        require(
            (kind == DocumentCoverageDispositionKind.MergedIntoAnotherDiscovery) == (relatedDiscoveryId != null),
        ) {
            "Only merged discovery coverage requires a related discovery ID."
        }
        require((kind == DocumentCoverageDispositionKind.MatchedExisting) == (alignmentId != null)) {
            "Only matched-existing coverage requires an alignment ID."
        }
        require(relatedDiscoveryId == null || relatedDiscoveryId != discoveryId) {
            "A discovery cannot be merged into itself."
        }
        require(
            (kind in setOf(
                DocumentCoverageDispositionKind.RejectedWithRationale,
                DocumentCoverageDispositionKind.Blocked,
            )) == (rationale != null),
        ) {
            "Rejected and blocked discovery coverage require a rationale."
        }
    }

    public val stableOrderingKey: String
        get() = discoveryId
}

public enum class DocumentFinalRecommendationStatus {
    Executable,
    ReviewOnly,
    Blocked,
    Mixed,
}

public data class DocumentIndividualReviewGate(
    public val operationId: String,
    public val classification: DocumentIndividualClassification,
    public val creationConfirmed: Boolean = false,
    public val productionClassificationConfirmed: Boolean = false,
) {
    init {
        requireOpaqueDocumentId(operationId, "Document individual operation ID")
        require(!productionClassificationConfirmed || creationConfirmed) {
            "Production classification confirmation requires creation confirmation."
        }
        require(
            classification != DocumentIndividualClassification.Production ||
                !productionClassificationConfirmed,
        ) {
            "An already-production individual does not require reclassification confirmation."
        }
    }

    public val executable: Boolean
        get() = creationConfirmed
}

public data class DocumentFinalRecommendation(
    public val id: String,
    public val title: String,
    public val description: String,
    public val discoveryIds: List<String>,
    public val evidenceIds: List<DocumentEvidenceId>,
    public val operations: List<DocumentPlanOperation> = emptyList(),
    public val reviewOnlyFindings: List<DocumentReviewOnlyFinding> = emptyList(),
    public val criticDispositions: List<DocumentCriticDisposition> = emptyList(),
    public val confidence: DocumentConfidenceDimensions,
    public val status: DocumentFinalRecommendationStatus,
    public val blockers: List<String> = emptyList(),
    public val individualReviewGates: List<DocumentIndividualReviewGate> = emptyList(),
) {
    init {
        requireOpaqueDocumentId(id, "Final document recommendation ID")
        requireNonBlankBounded(title, "Final document recommendation title", 500)
        requireNonBlankBounded(description, "Final document recommendation description", 2_000)
        require(discoveryIds.isNotEmpty() && discoveryIds == discoveryIds.distinct().sorted()) {
            "Final recommendation discoveries must be sorted, unique, and nonempty."
        }
        discoveryIds.forEach { requireOpaqueDocumentId(it, "Final recommendation discovery ID") }
        require(
            evidenceIds.isNotEmpty() &&
                evidenceIds.size <= MAX_DOCUMENT_EVIDENCE_REFERENCES &&
                evidenceIds == evidenceIds.distinct().sortedBy(DocumentEvidenceId::value),
        ) {
            "Final recommendation evidence must be bounded, sorted, and unique."
        }
        require(operations == operations.sortedBy(DocumentPlanOperation::order)) {
            "Final recommendation operations must use deterministic order."
        }
        require(operations.map(DocumentPlanOperation::id).distinct().size == operations.size) {
            "Final recommendation operation IDs must be unique."
        }
        require(operations.map(DocumentPlanOperation::order) == operations.indices.toList()) {
            "Final recommendation operation order must be contiguous and zero-based."
        }
        require(reviewOnlyFindings.map(DocumentReviewOnlyFinding::id).distinct().size == reviewOnlyFindings.size) {
            "Final recommendation review-only findings must be unique."
        }
        require(criticDispositions == criticDispositions.distinctBy(DocumentCriticDisposition::findingId)
            .sortedBy(DocumentCriticDisposition::stableOrderingKey)) {
            "Final recommendation critic dispositions must be sorted and unique."
        }
        require(blockers == blockers.distinct().sorted()) {
            "Final recommendation blockers must be sorted and unique."
        }
        blockers.forEach { requireNonBlankBounded(it, "Final recommendation blocker", 500) }
        require(individualReviewGates == individualReviewGates.distinctBy(DocumentIndividualReviewGate::operationId)
            .sortedBy(DocumentIndividualReviewGate::operationId)) {
            "Final recommendation individual gates must be sorted and unique."
        }
        validateOperationDependencies()
        validateTemporaryReferences()
        validateStatus()
        val operationIds = operations.map(DocumentPlanOperation::id).toSet()
        require(reviewOnlyFindings.flatMap(DocumentReviewOnlyFinding::relatedOperationIds).all(operationIds::contains)) {
            "Review-only findings must reference operations in the same recommendation."
        }
        require(individualReviewGates.all { gate ->
            operations.any { operation ->
                operation.id == gate.operationId && operation.kind == DocumentPlanOperationKind.CreateIndividual
            }
        }) {
            "Individual review gates must target individual-creation operations."
        }
    }

    public val expandedTypedEditCount: Int
        get() = operations.sumOf(DocumentPlanOperation::expandedTypedEditCount)

    private fun validateOperationDependencies(): Unit {
        val positions = operations.associate { it.id to it.order }
        operations.forEach { operation ->
            operation.dependsOnOperationIds.forEach { dependencyId ->
                require(positions.getOrElse(dependencyId) {
                    throw IllegalArgumentException("Document plan dependency is unresolved.")
                } < operation.order) {
                    "Document plan dependencies must point to earlier operations."
                }
            }
        }
        require(expandedTypedEditCount <= MAX_DOCUMENT_EXPANDED_TYPED_EDITS_PER_RECOMMENDATION) {
            "Final recommendation expanded edit count exceeds the approved bound."
        }
    }

    private fun validateTemporaryReferences(): Unit {
        val declarations = mutableMapOf<DocumentTemporaryReference, Int>()
        operations.forEach { operation ->
            operation.referencedTemporaryEntities.forEach { reference ->
                require(declarations.getOrElse(reference) {
                    throw IllegalArgumentException("Document temporary reference is unresolved or forward-invalid.")
                } < operation.order) {
                    "Document temporary references must point to earlier declarations."
                }
            }
            operation.declaration?.let { reference ->
                require(declarations.put(reference, operation.order) == null) {
                    "Document temporary declarations must be unique."
                }
            }
        }
    }

    private fun validateStatus(): Unit {
        when (status) {
            DocumentFinalRecommendationStatus.Executable -> {
                require(operations.isNotEmpty() && reviewOnlyFindings.isEmpty() && blockers.isEmpty()) {
                    "An executable recommendation requires only executable operations."
                }
            }
            DocumentFinalRecommendationStatus.ReviewOnly -> {
                require(operations.isEmpty() && reviewOnlyFindings.isNotEmpty() && blockers.isEmpty()) {
                    "A review-only recommendation requires only review-only findings."
                }
            }
            DocumentFinalRecommendationStatus.Blocked -> {
                require(blockers.isNotEmpty()) { "A blocked recommendation requires a blocker." }
            }
            DocumentFinalRecommendationStatus.Mixed -> {
                require(operations.isNotEmpty() && reviewOnlyFindings.isNotEmpty() && blockers.isEmpty()) {
                    "A mixed recommendation requires executable and review-only content."
                }
            }
        }
        require(
            criticDispositions.none { it.kind == DocumentCriticDispositionKind.Unresolved } ||
                status == DocumentFinalRecommendationStatus.Blocked,
        ) {
            "An unresolved critic finding must block its recommendation."
        }
        require(
            individualReviewGates.all(DocumentIndividualReviewGate::executable) ||
                status == DocumentFinalRecommendationStatus.Blocked,
        ) {
            "An unconfirmed individual must block its recommendation."
        }
    }

    public val stableOrderingKey: String
        get() = "${status.name}:$title:$id"
}

public data class DocumentFinalPlan(
    public val workKey: DocumentAnalysisWorkKey,
    public val verifiedDiscoveryIds: List<String>,
    public val criticFindingIds: List<String>,
    public val recommendations: List<DocumentFinalRecommendation>,
    public val coverage: List<DocumentCoverageDisposition>,
) {
    init {
        require(
            verifiedDiscoveryIds.isNotEmpty() &&
                verifiedDiscoveryIds.size <= MAX_DOCUMENT_DISCOVERIES_PER_TASK &&
                verifiedDiscoveryIds == verifiedDiscoveryIds.distinct().sorted(),
        ) {
            "Final plan discovery IDs must be bounded, sorted, and unique."
        }
        verifiedDiscoveryIds.forEach { requireOpaqueDocumentId(it, "Final plan discovery ID") }
        require(criticFindingIds == criticFindingIds.distinct().sorted()) {
            "Final plan critic finding IDs must be sorted and unique."
        }
        criticFindingIds.forEach { requireOpaqueDocumentId(it, "Final plan critic finding ID") }
        require(
            recommendations == recommendations.sortedBy(DocumentFinalRecommendation::stableOrderingKey) &&
                recommendations.map(DocumentFinalRecommendation::id).distinct().size == recommendations.size,
        ) {
            "Final plan recommendations must be sorted and unique."
        }
        require(coverage == coverage.sortedBy(DocumentCoverageDisposition::stableOrderingKey)) {
            "Final plan coverage must use deterministic discovery order."
        }
        require(coverage.map(DocumentCoverageDisposition::discoveryId) == verifiedDiscoveryIds) {
            "Every verified discovery must have exactly one coverage disposition."
        }
        val recommendationIds = recommendations.map(DocumentFinalRecommendation::id).toSet()
        require(coverage.mapNotNull(DocumentCoverageDisposition::recommendationId).all(recommendationIds::contains)) {
            "Coverage dispositions must reference recommendations in the final plan."
        }
        val declaredReferences = recommendations.flatMap { recommendation ->
            recommendation.operations.mapNotNull(DocumentPlanOperation::declaration)
        }
        require(declaredReferences.distinct().size == declaredReferences.size) {
            "Temporary references must be unique within the final plan."
        }
        val dispositions = recommendations.flatMap(DocumentFinalRecommendation::criticDispositions)
        require(dispositions.map(DocumentCriticDisposition::findingId).sorted() == criticFindingIds) {
            "Every critic finding must have exactly one final disposition."
        }
    }
}

public enum class DocumentSemanticItemKind {
    Class,
    ObjectProperty,
    DatatypeProperty,
    AnnotationProperty,
    Individual,
    SubclassRelationship,
    ObjectPropertyDomain,
    ObjectPropertyRange,
    DatatypePropertyDomain,
    DatatypePropertyRange,
    IndividualType,
    ObjectPropertyAssertion,
    DatatypeValueAssertion,
    PreferredLabel,
    Definition,
    AlternateLabel,
    NodeShape,
    PropertyShape,
    ShaclConstraint,
    ComplexRule,
}

public enum class DocumentSemanticReferenceRole {
    Subject,
    Predicate,
    Object,
    Subclass,
    Superclass,
    Property,
    Domain,
    Range,
    Individual,
    Type,
    Entity,
    Shape,
    TargetClass,
    Path,
    ConstraintTarget,
    Datatype,
    Related,
}

public sealed interface DocumentSemanticReferenceTarget {
    public data class SemanticItem(public val itemId: String) : DocumentSemanticReferenceTarget {
        init {
            requireOpaqueDocumentId(itemId, "Document semantic referenced item ID")
        }
    }

    public data class Alignment(public val alignmentId: String) : DocumentSemanticReferenceTarget {
        init {
            requireOpaqueDocumentId(alignmentId, "Document semantic alignment ID")
        }
    }
}

public data class DocumentSemanticReference(
    public val role: DocumentSemanticReferenceRole,
    public val target: DocumentSemanticReferenceTarget,
) {
    public val stableOrderingKey: String
        get() = "${role.ordinal.toString().padStart(2, '0')}:${target.stableValue}"
}

private val DocumentSemanticReferenceTarget.stableValue: String
    get() = when (this) {
        is DocumentSemanticReferenceTarget.Alignment -> "alignment:$alignmentId"
        is DocumentSemanticReferenceTarget.SemanticItem -> "item:$itemId"
    }

public enum class DocumentSemanticOutcome {
    Executable,
    ReviewOnly,
    Blocked,
}

public data class DocumentSemanticPlanItem(
    public val id: String,
    public val kind: DocumentSemanticItemKind,
    public val label: String,
    public val definition: String? = null,
    public val literalValue: RdfLiteral? = null,
    public val datatypeIntent: String? = null,
    public val references: List<DocumentSemanticReference> = emptyList(),
    public val discoveryIds: List<String>,
    public val evidenceIds: List<DocumentEvidenceId>,
    public val rationale: String,
    public val outcome: DocumentSemanticOutcome,
    public val ambiguity: String? = null,
    public val criticDispositions: List<DocumentCriticDisposition> = emptyList(),
    public val confidence: DocumentConfidenceDimensions,
    public val modelRecommended: Boolean = false,
    public val reviewerInputRequired: Boolean = false,
) {
    init {
        requireOpaqueDocumentId(id, "Document semantic item ID")
        requireNonBlankBounded(label, "Document semantic item label", 500)
        requireOptionalDocumentText(definition, "Document semantic item definition", 2_000)
        requireOptionalDocumentText(datatypeIntent, "Document semantic datatype intent", 500)
        requireOptionalDocumentText(ambiguity, "Document semantic item ambiguity", 2_000)
        requireNonBlankBounded(rationale, "Document semantic item rationale", 2_000)
        require(discoveryIds.isNotEmpty() && discoveryIds == discoveryIds.distinct().sorted()) {
            "Document semantic item discoveries must be sorted, unique, and nonempty."
        }
        discoveryIds.forEach { requireOpaqueDocumentId(it, "Document semantic item discovery ID") }
        require(
            evidenceIds.isNotEmpty() &&
                evidenceIds.size <= MAX_DOCUMENT_EVIDENCE_REFERENCES &&
                evidenceIds == evidenceIds.distinct().sortedBy(DocumentEvidenceId::value),
        ) {
            "Document semantic item evidence must be bounded, sorted, unique, and nonempty."
        }
        require(references == references.distinct().sortedBy(DocumentSemanticReference::stableOrderingKey)) {
            "Document semantic item references must be sorted and unique."
        }
        require(references.none {
            (it.target as? DocumentSemanticReferenceTarget.SemanticItem)?.itemId == id
        }) {
            "A document semantic item cannot reference itself."
        }
        require(
            criticDispositions == criticDispositions.distinctBy(DocumentCriticDisposition::findingId)
                .sortedBy(DocumentCriticDisposition::stableOrderingKey),
        ) {
            "Document semantic critic dispositions must be sorted and unique."
        }
        require((kind == DocumentSemanticItemKind.DatatypeValueAssertion) == (literalValue != null)) {
            "Only a datatype-value semantic item requires a literal value."
        }
        require(datatypeIntent == null || kind in DATATYPE_INTENT_KINDS) {
            "Datatype intent is supported only for datatype semantic items."
        }
        require(outcome != DocumentSemanticOutcome.Executable || ambiguity == null) {
            "An executable semantic item cannot retain unresolved ambiguity."
        }
        requireReferenceRoles()
    }

    public val referencedItemIds: List<String>
        get() = references.mapNotNull {
            (it.target as? DocumentSemanticReferenceTarget.SemanticItem)?.itemId
        }.distinct().sorted()

    public val alignmentIds: List<String>
        get() = references.mapNotNull {
            (it.target as? DocumentSemanticReferenceTarget.Alignment)?.alignmentId
        }.distinct().sorted()

    public val stableOrderingKey: String
        get() = "${kind.name}:$label:$id"

    private fun requireReferenceRoles(): Unit {
        val roles = references.map(DocumentSemanticReference::role)
        if (kind == DocumentSemanticItemKind.DatatypePropertyRange && datatypeIntent != null) {
            val allowed = listOf(
                listOf(DocumentSemanticReferenceRole.Property),
                listOf(DocumentSemanticReferenceRole.Property, DocumentSemanticReferenceRole.Range),
            )
            require(allowed.any { expected ->
                roles.sortedBy(DocumentSemanticReferenceRole::ordinal) ==
                    expected.sortedBy(DocumentSemanticReferenceRole::ordinal)
            }) {
                "A datatype semantic range requires a property reference and may retain its grounded range item."
            }
            return
        }
        val expected = when (kind) {
            DocumentSemanticItemKind.SubclassRelationship ->
                listOf(DocumentSemanticReferenceRole.Subclass, DocumentSemanticReferenceRole.Superclass)
            DocumentSemanticItemKind.ObjectPropertyDomain,
            DocumentSemanticItemKind.DatatypePropertyDomain,
            -> listOf(DocumentSemanticReferenceRole.Property, DocumentSemanticReferenceRole.Domain)
            DocumentSemanticItemKind.ObjectPropertyRange ->
                listOf(DocumentSemanticReferenceRole.Property, DocumentSemanticReferenceRole.Range)
            DocumentSemanticItemKind.DatatypePropertyRange ->
                listOf(DocumentSemanticReferenceRole.Property, DocumentSemanticReferenceRole.Range)
            DocumentSemanticItemKind.IndividualType ->
                listOf(DocumentSemanticReferenceRole.Individual, DocumentSemanticReferenceRole.Type)
            DocumentSemanticItemKind.ObjectPropertyAssertion ->
                listOf(
                    DocumentSemanticReferenceRole.Subject,
                    DocumentSemanticReferenceRole.Predicate,
                    DocumentSemanticReferenceRole.Object,
                )
            DocumentSemanticItemKind.DatatypeValueAssertion ->
                listOf(DocumentSemanticReferenceRole.Subject, DocumentSemanticReferenceRole.Predicate)
            DocumentSemanticItemKind.PreferredLabel,
            DocumentSemanticItemKind.Definition,
            DocumentSemanticItemKind.AlternateLabel,
            -> listOf(DocumentSemanticReferenceRole.Entity)
            DocumentSemanticItemKind.NodeShape -> listOf(DocumentSemanticReferenceRole.TargetClass)
            DocumentSemanticItemKind.PropertyShape ->
                listOf(DocumentSemanticReferenceRole.Shape, DocumentSemanticReferenceRole.Path)
            DocumentSemanticItemKind.ShaclConstraint ->
                listOf(DocumentSemanticReferenceRole.ConstraintTarget)
            DocumentSemanticItemKind.ComplexRule -> null
            DocumentSemanticItemKind.Class,
            DocumentSemanticItemKind.ObjectProperty,
            DocumentSemanticItemKind.DatatypeProperty,
            DocumentSemanticItemKind.AnnotationProperty,
            DocumentSemanticItemKind.Individual,
            -> emptyList()
        }
        if (expected == null) {
            require(references.isNotEmpty() && roles.all { it == DocumentSemanticReferenceRole.Related }) {
                "A complex semantic rule requires one or more related references."
            }
        } else {
            require(roles.sortedBy(DocumentSemanticReferenceRole::ordinal) ==
                expected.sortedBy(DocumentSemanticReferenceRole::ordinal)) {
                "Document semantic reference roles are incompatible with the item kind."
            }
        }
    }

    private companion object {
        val DATATYPE_INTENT_KINDS: Set<DocumentSemanticItemKind> = setOf(
            DocumentSemanticItemKind.DatatypeProperty,
            DocumentSemanticItemKind.DatatypePropertyRange,
            DocumentSemanticItemKind.DatatypeValueAssertion,
            DocumentSemanticItemKind.ShaclConstraint,
        )
    }
}

public data class DocumentSemanticRecommendationGroup(
    public val id: String,
    public val title: String,
    public val description: String,
    public val itemIds: List<String>,
    public val reviewOnlyItemIds: List<String> = emptyList(),
    public val discoveryIds: List<String>,
    public val evidenceIds: List<DocumentEvidenceId>,
    public val outcome: DocumentSemanticOutcome,
    public val rationale: String,
    public val criticDispositions: List<DocumentCriticDisposition> = emptyList(),
    public val confidence: DocumentConfidenceDimensions,
) {
    init {
        requireOpaqueDocumentId(id, "Document semantic group ID")
        requireNonBlankBounded(title, "Document semantic group title", 500)
        requireNonBlankBounded(description, "Document semantic group description", 2_000)
        requireNonBlankBounded(rationale, "Document semantic group rationale", 2_000)
        require(itemIds.isNotEmpty() && itemIds == itemIds.distinct().sorted()) {
            "Document semantic group item IDs must be sorted, unique, and nonempty."
        }
        itemIds.forEach { requireOpaqueDocumentId(it, "Document semantic group item ID") }
        require(
            reviewOnlyItemIds == reviewOnlyItemIds.distinct().sorted() &&
                reviewOnlyItemIds.all(itemIds::contains),
        ) {
            "Document semantic group review-only item IDs must be sorted, unique, and belong to the group."
        }
        reviewOnlyItemIds.forEach { requireOpaqueDocumentId(it, "Document semantic group review-only item ID") }
        require(discoveryIds.isNotEmpty() && discoveryIds == discoveryIds.distinct().sorted()) {
            "Document semantic group discovery IDs must be sorted, unique, and nonempty."
        }
        discoveryIds.forEach { requireOpaqueDocumentId(it, "Document semantic group discovery ID") }
        require(
            evidenceIds.isNotEmpty() &&
                evidenceIds.size <= MAX_DOCUMENT_EVIDENCE_REFERENCES &&
                evidenceIds == evidenceIds.distinct().sortedBy(DocumentEvidenceId::value),
        ) {
            "Document semantic group evidence must be bounded, sorted, unique, and nonempty."
        }
        require(
            criticDispositions == criticDispositions.distinctBy(DocumentCriticDisposition::findingId)
                .sortedBy(DocumentCriticDisposition::stableOrderingKey),
        ) {
            "Document semantic group critic dispositions must be sorted and unique."
        }
    }

    public val stableOrderingKey: String
        get() = "${outcome.name}:$title:$id"

    /** Items that Kotlin may compile while retaining the rest as review context. */
    public val executableItemIds: List<String>
        get() = if (outcome == DocumentSemanticOutcome.ReviewOnly) {
            emptyList()
        } else {
            itemIds.filterNot(reviewOnlyItemIds::contains)
        }

    /** Review context retained with this group, including legacy review-only groups. */
    public val retainedReviewOnlyItemIds: List<String>
        get() = if (outcome == DocumentSemanticOutcome.ReviewOnly && reviewOnlyItemIds.isEmpty()) {
            itemIds
        } else {
            reviewOnlyItemIds
        }
}

public data class DocumentSemanticPlan(
    public val workKey: DocumentAnalysisWorkKey,
    public val verifiedDiscoveryIds: List<String>,
    public val criticFindingIds: List<String>,
    public val items: List<DocumentSemanticPlanItem>,
    public val groups: List<DocumentSemanticRecommendationGroup>,
) {
    init {
        require(
            verifiedDiscoveryIds.isNotEmpty() &&
                verifiedDiscoveryIds.size <= MAX_DOCUMENT_DISCOVERIES_PER_TASK &&
                verifiedDiscoveryIds == verifiedDiscoveryIds.distinct().sorted(),
        ) {
            "Document semantic plan discovery IDs must be bounded, sorted, unique, and nonempty."
        }
        verifiedDiscoveryIds.forEach { requireOpaqueDocumentId(it, "Document semantic plan discovery ID") }
        require(criticFindingIds == criticFindingIds.distinct().sorted()) {
            "Document semantic plan critic finding IDs must be sorted and unique."
        }
        criticFindingIds.forEach { requireOpaqueDocumentId(it, "Document semantic plan critic finding ID") }
        require(items == items.sortedBy(DocumentSemanticPlanItem::stableOrderingKey)) {
            "Document semantic plan items must use deterministic order."
        }
        require(items.map(DocumentSemanticPlanItem::id).distinct().size == items.size) {
            "Document semantic plan item IDs must be unique."
        }
        require(
            groups == groups.sortedBy(DocumentSemanticRecommendationGroup::stableOrderingKey) &&
                groups.map(DocumentSemanticRecommendationGroup::id).distinct().size == groups.size,
        ) {
            "Document semantic plan groups must be sorted and unique."
        }
        val itemIds = items.map(DocumentSemanticPlanItem::id).toSet()
        require(items.flatMap(DocumentSemanticPlanItem::referencedItemIds).all(itemIds::contains)) {
            "Document semantic plan contains an unresolved item reference."
        }
        require(groups.flatMap(DocumentSemanticRecommendationGroup::itemIds).all(itemIds::contains)) {
            "Document semantic group contains an unresolved item ID."
        }
        require(items.flatMap(DocumentSemanticPlanItem::discoveryIds).all(verifiedDiscoveryIds::contains)) {
            "Document semantic item contains an unknown discovery ID."
        }
        require(groups.flatMap(DocumentSemanticRecommendationGroup::discoveryIds).all(verifiedDiscoveryIds::contains)) {
            "Document semantic group contains an unknown discovery ID."
        }
        val dispositions = groups.flatMap(DocumentSemanticRecommendationGroup::criticDispositions)
        require(dispositions.map(DocumentCriticDisposition::findingId).sorted() == criticFindingIds) {
            "Every semantic-plan critic finding must have exactly one group disposition."
        }
    }
}

public enum class DocumentCompilationStatus {
    Compiled,
    ReviewOnly,
    Blocked,
}

public data class DocumentCompilationFailure(
    public val semanticItemId: String,
    public val safeCode: String,
    public val message: String,
) {
    init {
        requireOpaqueDocumentId(semanticItemId, "Document compilation semantic item ID")
        requireNonBlankBounded(safeCode, "Document compilation safe code", 200)
        requireNonBlankBounded(message, "Document compilation failure message", 2_000)
    }

    public val stableOrderingKey: String
        get() = "$semanticItemId:$safeCode"
}

public data class DocumentCompiledReference(
    public val semanticItemId: String,
    public val temporaryReference: DocumentTemporaryReference,
    public val finalIri: Iri,
) {
    init {
        requireOpaqueDocumentId(semanticItemId, "Document compiled semantic item ID")
    }

    public val stableOrderingKey: String
        get() = semanticItemId
}

public data class DocumentQualityMetric(
    public val numerator: Int,
    public val denominator: Int,
    public val percentage: Int?,
    public val failureCodes: List<String> = emptyList(),
) {
    init {
        require(numerator >= 0 && denominator >= 0 && numerator <= denominator) {
            "Document quality metric counts are invalid."
        }
        require(
            (denominator == 0 && percentage == null) ||
                (denominator > 0 && percentage == numerator * 100 / denominator),
        ) {
            "Document quality metric percentage must match its counts."
        }
        require(failureCodes == failureCodes.distinct().sorted()) {
            "Document quality metric failure codes must be sorted and unique."
        }
        failureCodes.forEach { requireNonBlankBounded(it, "Document quality metric failure code", 200) }
    }
}

public enum class DocumentBenchmarkExpectationCategory {
    Concept,
    Relationship,
    Rule,
    Individual,
    NegativeExpectation,
}

public data class DocumentBenchmarkExpectation(
    public val id: String,
    public val category: DocumentBenchmarkExpectationCategory,
    public val satisfied: Boolean,
) {
    init {
        requireOpaqueDocumentId(id, "Document benchmark expectation ID")
    }

    public val stableOrderingKey: String
        get() = "${category.ordinal.toString().padStart(2, '0')}:$id"
}

public data class DocumentBenchmarkCategoryCount(
    public val category: DocumentBenchmarkExpectationCategory,
    public val satisfied: Int,
    public val total: Int,
) {
    init {
        require(satisfied >= 0 && total >= 0 && satisfied <= total) {
            "Document benchmark category counts are invalid."
        }
    }
}

public data class DocumentCompletenessMetrics(
    public val verifiedPlan: DocumentVerifiedSemanticPlan,
    public val semanticCoverage: DocumentQualityMetric,
    public val compilationSuccess: DocumentQualityMetric,
    public val benchmarkCounts: List<DocumentBenchmarkCategoryCount> = emptyList(),
) {
    init {
        require(benchmarkCounts == benchmarkCounts.distinctBy(DocumentBenchmarkCategoryCount::category)
            .sortedBy { it.category.ordinal }) {
            "Document benchmark category counts must be sorted and unique."
        }
    }
}

public data class DocumentCompiledConfidenceDimensions(
    public val evidence: Int,
    public val modeling: Int,
    public val ontologyFit: Int,
    public val compilation: Int?,
    public val overall: Int = listOfNotNull(evidence, modeling, ontologyFit, compilation).min(),
) {
    init {
        require(listOf(evidence, modeling, ontologyFit, overall).all { it in 0..100 }) {
            "Document compiled confidence dimensions must be between 0 and 100."
        }
        require(compilation == null || compilation in 0..100) {
            "Document compilation confidence must be absent or between 0 and 100."
        }
        require(overall == listOfNotNull(evidence, modeling, ontologyFit, compilation).min()) {
            "Overall compiled confidence must equal the weakest applicable dimension."
        }
    }
}

public data class DocumentCompiledRecommendationResult(
    public val groupId: String,
    public val status: DocumentCompilationStatus,
    public val operations: List<DocumentPlanOperation> = emptyList(),
    public val references: List<DocumentCompiledReference> = emptyList(),
    public val failures: List<DocumentCompilationFailure> = emptyList(),
    public val confidence: DocumentCompiledConfidenceDimensions,
    public val sourceGroupId: String = groupId,
) {
    init {
        requireOpaqueDocumentId(groupId, "Document compiled group ID")
        requireOpaqueDocumentId(sourceGroupId, "Document compiled source group ID")
        require(operations == operations.sortedBy(DocumentPlanOperation::order)) {
            "Compiled document operations must use deterministic order."
        }
        require(operations.map(DocumentPlanOperation::id).distinct().size == operations.size) {
            "Compiled document operation IDs must be unique."
        }
        require(references == references.distinctBy(DocumentCompiledReference::semanticItemId)
            .sortedBy(DocumentCompiledReference::stableOrderingKey)) {
            "Compiled document references must be sorted and unique."
        }
        require(failures == failures.distinctBy { it.semanticItemId to it.safeCode }
            .sortedBy(DocumentCompilationFailure::stableOrderingKey)) {
            "Document compilation failures must be sorted and unique."
        }
        when (status) {
            DocumentCompilationStatus.Compiled ->
                require(operations.isNotEmpty() && failures.isEmpty() && confidence.compilation != null) {
                    "A compiled document group requires operations and compilation confidence."
                }
            DocumentCompilationStatus.ReviewOnly ->
                require(operations.isEmpty() && failures.isEmpty() && confidence.compilation == null) {
                    "A review-only document group cannot claim compilation output."
                }
            DocumentCompilationStatus.Blocked ->
                require(failures.isNotEmpty() && confidence.compilation == 0) {
                    "A blocked document group requires failures and zero compilation confidence."
                }
        }
    }

    public val expandedTypedEditCount: Int
        get() = operations.sumOf(DocumentPlanOperation::expandedTypedEditCount)
}

public data class DocumentVerifiedSemanticPlan(
    public val plan: DocumentSemanticPlan,
    public val coverage: List<DocumentCoverageDisposition>,
)

public data class DocumentCompiledPlanResult(
    public val workKey: DocumentAnalysisWorkKey,
    public val semanticPlan: DocumentVerifiedSemanticPlan,
    public val recommendations: List<DocumentCompiledRecommendationResult>,
    public val semanticCoverage: DocumentQualityMetric,
    public val compilationSuccess: DocumentQualityMetric,
) {
    init {
        require(
            recommendations == recommendations.sortedBy(DocumentCompiledRecommendationResult::groupId) &&
                recommendations.map(DocumentCompiledRecommendationResult::groupId).distinct().size ==
                recommendations.size,
        ) {
            "Compiled document recommendations must be sorted and unique."
        }
        require(workKey == semanticPlan.plan.workKey) {
            "Compiled document result work key must match its semantic plan."
        }
    }
}

public enum class DocumentGroupedDecisionKind {
    Pending,
    Accepted,
    Rejected,
    NeedsClarification,
    ReconsiderationRequested,
    SplitRequested,
    Drafted,
}

public data class DocumentGroupedRecommendationDecision(
    public val decisionId: String,
    public val recommendationId: String,
    public val actorUserId: String,
    public val decidedAt: Instant,
    public val kind: DocumentGroupedDecisionKind,
    public val clarification: String? = null,
) {
    init {
        requireOpaqueDocumentId(decisionId, "Grouped document decision ID")
        requireOpaqueDocumentId(recommendationId, "Grouped document recommendation ID")
        requireNonBlankBounded(actorUserId, "Grouped document decision actor", 200)
        requireOptionalDocumentText(clarification, "Grouped document decision clarification", 2_000)
        require(
            kind !in setOf(
                DocumentGroupedDecisionKind.NeedsClarification,
                DocumentGroupedDecisionKind.ReconsiderationRequested,
                DocumentGroupedDecisionKind.SplitRequested,
            ) || clarification != null,
        ) {
            "Clarification, reconsideration, and split requests require reviewer input."
        }
    }
}
