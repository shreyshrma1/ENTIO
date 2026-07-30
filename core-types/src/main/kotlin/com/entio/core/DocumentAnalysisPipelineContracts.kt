package com.entio.core

import java.time.Instant

public const val MAX_DOCUMENT_DISCOVERIES_PER_DOCUMENT: Int = 200
public const val MAX_DOCUMENT_DISCOVERIES_PER_TASK: Int = 2_000
public const val MAX_DOCUMENT_CONNECTED_MODEL_ITEMS: Int = 300
public const val MAX_DOCUMENT_FINAL_RECOMMENDATIONS: Int = 100
public const val MAX_DOCUMENT_EXPANDED_TYPED_EDITS_PER_RECOMMENDATION: Int = 20
public const val MAX_DOCUMENT_EXPANDED_TYPED_EDITS_PER_TASK: Int = 100
public const val MAX_DOCUMENT_PLANNED_LOGICAL_CALLS: Int = 15
public const val MAX_DOCUMENT_PROVIDER_ATTEMPTS: Int = 20
public const val MAX_DOCUMENT_AUTOMATIC_RETRY_ATTEMPTS: Int = 3
public const val MAX_DOCUMENT_RECONSIDERATION_ATTEMPTS: Int = 2
public const val MAX_DOCUMENT_STAGE_PROMPT_CHARACTERS: Int = 60_000
public const val MAX_DOCUMENT_PROVIDER_RESPONSE_CHARACTERS: Int = 1_000_000

/** Version constants shared by neutral task records and server-side stage adapters. */
public object DocumentAnalysisPipelineVersions {
    public const val DISCOVERY_PROMPT: String = "phase-11-5-document-discovery-v2"
    public const val DISCOVERY_REQUEST: String = "phase-11-5-document-discovery-request-v2"
    public const val DISCOVERY_RESPONSE: String = "phase-11-5-document-discovery-response-v2"
    public const val CONNECTED_MODEL_PROMPT: String = "phase-11-5-connected-model-v1"
    public const val CONNECTED_MODEL_REQUEST: String = "phase-11-5-connected-model-request-v1"
    public const val CONNECTED_MODEL_RESPONSE: String = "phase-11-5-connected-model-response-v1"
    public const val MODEL_CONSOLIDATION_PROMPT: String = "phase-11-5-model-consolidation-v1"
    public const val MODEL_CONSOLIDATION_REQUEST: String = "phase-11-5-model-consolidation-request-v1"
    public const val MODEL_CONSOLIDATION_RESPONSE: String = "phase-11-5-model-consolidation-response-v1"
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
    Reconciliation(true),
    OntologyAlignment(true),
    ModelingCritic(true),
    FinalPlanning(true),
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
        require(providerAttemptCount in 0..MAX_DOCUMENT_PROVIDER_ATTEMPTS) {
            "Document analysis stage attempt count exceeds the approved bound."
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
    public val order: Int,
    public val reviewOnlyEligible: Boolean = false,
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
        requireReferenceRoles()
    }

    public val referencedItemIds: List<String>
        get() = references.map(DocumentConnectedModelReference::itemId).distinct().sorted()

    private fun requireReferenceRoles(): Unit {
        val roles = references.map(DocumentConnectedModelReference::role)
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
        require(items.size <= MAX_DOCUMENT_CONNECTED_MODEL_ITEMS) {
            "A connected document model requires bounded items."
        }
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
    MergedIntoAnotherDiscovery,
    Duplicate,
    AdministrativeMetadata,
    IllustrativeExample,
    Unsupported,
    RejectedWithRationale,
}

public data class DocumentCoverageDisposition(
    public val discoveryId: String,
    public val kind: DocumentCoverageDispositionKind,
    public val recommendationId: String? = null,
    public val relatedDiscoveryId: String? = null,
    public val rationale: String? = null,
) {
    init {
        requireOpaqueDocumentId(discoveryId, "Document coverage discovery ID")
        recommendationId?.let { requireOpaqueDocumentId(it, "Document coverage recommendation ID") }
        relatedDiscoveryId?.let { requireOpaqueDocumentId(it, "Related document coverage discovery ID") }
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
        require(relatedDiscoveryId == null || relatedDiscoveryId != discoveryId) {
            "A discovery cannot be merged into itself."
        }
        require((kind == DocumentCoverageDispositionKind.RejectedWithRationale) == (rationale != null)) {
            "Only rejected discovery coverage requires a rationale."
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
            recommendations.size <= MAX_DOCUMENT_FINAL_RECOMMENDATIONS &&
                recommendations == recommendations.sortedBy(DocumentFinalRecommendation::stableOrderingKey) &&
                recommendations.map(DocumentFinalRecommendation::id).distinct().size == recommendations.size,
        ) {
            "Final plan recommendations must be bounded, sorted, and unique."
        }
        require(coverage == coverage.sortedBy(DocumentCoverageDisposition::stableOrderingKey)) {
            "Final plan coverage must use deterministic discovery order."
        }
        require(coverage.map(DocumentCoverageDisposition::discoveryId) == verifiedDiscoveryIds) {
            "Every verified discovery must have exactly one coverage disposition."
        }
        require(recommendations.sumOf(DocumentFinalRecommendation::expandedTypedEditCount) <=
            MAX_DOCUMENT_EXPANDED_TYPED_EDITS_PER_TASK) {
            "Final plan expanded edit count exceeds the approved task bound."
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
