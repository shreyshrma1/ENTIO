package com.entio.semantic

import com.entio.core.AddDatatypePropertyAssertionEdit
import com.entio.core.AddObjectPropertyAssertionEdit
import com.entio.core.AddSuperclassEdit
import com.entio.core.AssignTypeEdit
import com.entio.core.CreateClassEdit
import com.entio.core.CreateDatatypePropertyEdit
import com.entio.core.CreateIndividualEdit
import com.entio.core.CreateObjectPropertyEdit
import com.entio.core.DocumentCandidateCategory
import com.entio.core.DocumentFinalRecommendation
import com.entio.core.DocumentFinalRecommendationStatus
import com.entio.core.DocumentPlanOperand
import com.entio.core.DocumentPlanOperation
import com.entio.core.DocumentPlanOperationKind
import com.entio.core.DocumentRecommendation
import com.entio.core.DocumentRecommendationAction
import com.entio.core.ExternalProposalIntent
import com.entio.core.Iri
import com.entio.core.AnnotationValue
import com.entio.core.EditableShaclConstraint
import com.entio.core.EditableShaclConstraintKind
import com.entio.core.EditableShaclConstraintValue
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.RemovePropertyDomainEdit
import com.entio.core.RemovePropertyRangeEdit
import com.entio.core.RemoveSuperclassEdit
import com.entio.core.SemanticEditRequest
import com.entio.core.SetEntityLabelEdit
import com.entio.core.SetPropertyDomainEdit
import com.entio.core.SetPropertyRangeEdit
import com.entio.core.TypedOntologyEdit
import com.entio.core.TypedShaclEdit
import com.entio.core.DocumentTemporaryReference

public sealed interface DocumentDraftOperation {
    public data class Ontology(val edit: TypedOntologyEdit) : DocumentDraftOperation
    public data class Semantic(val edit: SemanticEditRequest) : DocumentDraftOperation
    public data class Shacl(val edit: TypedShaclEdit) : DocumentDraftOperation
    public data class ExternalReuse(
        val intent: ExternalProposalIntent,
        val targetOntologyIri: Iri,
    ) : DocumentDraftOperation
}

public data class DocumentDraftTranslationContext(
    val targetSourceId: String?,
    val targetIri: Iri? = null,
    val relatedIri: Iri? = null,
    val existingRelatedIri: Iri? = null,
    val propertyIri: Iri? = null,
    val objectTerm: com.entio.core.RdfTerm? = null,
    val existingLiteral: RdfLiteral? = null,
    val shaclEdit: TypedShaclEdit? = null,
    val externalIntent: ExternalProposalIntent? = null,
    val targetOntologyIri: Iri? = null,
    val acceptedForDraft: Boolean = false,
    val clarificationResolved: Boolean = false,
    val lowConfidenceOcrConfirmed: Boolean = false,
    val evidenceCurrent: Boolean = true,
    val graphCurrent: Boolean = true,
    val modelAndPromptCurrent: Boolean = true,
    val duplicateOperation: Boolean = false,
    val domainIri: Iri? = null,
    val rangeIri: Iri? = null,
    val connectionPropertyIri: Iri? = null,
    val connectionDomainIri: Iri? = null,
)

public data class PreparedDocumentDraftOperation(
    val recommendationId: String,
    val targetSourceId: String,
    val operation: DocumentDraftOperation?,
    val normalizedTypedOperationKey: String?,
) {
    public val confirmOnly: Boolean
        get() = operation == null
}

public sealed interface DocumentDraftTranslationResult {
    public data class Prepared(val operations: List<PreparedDocumentDraftOperation>) : DocumentDraftTranslationResult
    public data class Blocked(val code: String, val message: String) : DocumentDraftTranslationResult
}

public data class ConnectedDocumentDraftContext(
    val finalIris: Map<DocumentTemporaryReference, Iri>,
    val writableSourceIds: Set<String>,
    val expectedWorkKey: String,
    val currentWorkKey: String,
    val graphCurrent: Boolean = true,
    val evidenceCurrent: Boolean = true,
    val modelAndPromptCurrent: Boolean = true,
    val existingNormalizedOperationKeys: Set<String> = emptySet(),
    val externalIntentsByOperationId: Map<String, Pair<ExternalProposalIntent, Iri>> = emptyMap(),
)

/** Converts reviewed recommendations only into existing, approved typed operations. */
public class DocumentRecommendationDraftTranslator {
    public fun translateConnected(
        recommendation: DocumentFinalRecommendation,
        context: ConnectedDocumentDraftContext,
    ): DocumentDraftTranslationResult {
        if (recommendation.status !in setOf(
                DocumentFinalRecommendationStatus.Executable,
                DocumentFinalRecommendationStatus.Mixed,
            ) ||
            recommendation.blockers.isNotEmpty()
        ) {
            return blocked("document-recommendation-review-only", "This grouped recommendation is not executable.")
        }
        if (context.expectedWorkKey != context.currentWorkKey ||
            !context.graphCurrent ||
            !context.evidenceCurrent ||
            !context.modelAndPromptCurrent
        ) {
            return blocked("document-draft-stale", "The graph, evidence, work key, model, or prompt changed.")
        }
        if (recommendation.individualReviewGates.any { !it.executable }) {
            return blocked(
                "document-individual-confirmation-required",
                "Every proposed individual requires explicit creation confirmation.",
            )
        }
        return try {
            val prepared = recommendation.operations.sortedBy(DocumentPlanOperation::order).map { operation ->
                translateConnectedOperation(recommendation.id, operation, context)
            }
            if (prepared.size != recommendation.expandedTypedEditCount ||
                prepared.size > com.entio.core.MAX_DOCUMENT_EXPANDED_TYPED_EDITS_PER_RECOMMENDATION
            ) {
                return blocked(
                    "document-compound-recommendation-limit",
                    "The grouped recommendation does not map exactly to its verified typed-edit count.",
                )
            }
            val keys = prepared.mapNotNull(PreparedDocumentDraftOperation::normalizedTypedOperationKey)
            if (keys.size != keys.distinct().size || keys.any(context.existingNormalizedOperationKeys::contains)) {
                return blocked("document-draft-duplicate", "The grouped recommendation contains a duplicate or no-op.")
            }
            DocumentDraftTranslationResult.Prepared(prepared)
        } catch (_: MissingDocumentDraftOperand) {
            blocked("document-draft-context-missing", "A required typed operand is missing.")
        } catch (_: UnsupportedDocumentDraftOperation) {
            blocked("document-recommendation-unsupported", "The grouped recommendation uses an unsupported operation.")
        } catch (_: IllegalArgumentException) {
            blocked("document-draft-edit-invalid", "The grouped recommendation contains an invalid typed operation.")
        }
    }

    private fun translateConnectedOperation(
        recommendationId: String,
        operation: DocumentPlanOperation,
        context: ConnectedDocumentDraftContext,
    ): PreparedDocumentDraftOperation {
        val sourceId = operation.operands.filterIsInstance<DocumentPlanOperand.SourceId>()
            .singleOrNull()?.value
            ?: throw MissingDocumentDraftOperand("target source")
        if (sourceId !in context.writableSourceIds) throw UnsupportedDocumentDraftOperation()
        val entities = operation.operands.mapNotNull { operand ->
            when (operand) {
                is DocumentPlanOperand.ExistingEntity -> operand.iri
                is DocumentPlanOperand.TemporaryEntity -> context.finalIris[operand.reference]
                    ?: throw MissingDocumentDraftOperand("temporary reference")
                else -> null
            }
        }
        val texts = operation.operands.filterIsInstance<DocumentPlanOperand.TextValue>().map { it.value }
        val literals = operation.operands.filterIsInstance<DocumentPlanOperand.LiteralValue>().map { it.value }
        val declaration = operation.declaration?.let {
            context.finalIris[it] ?: throw MissingDocumentDraftOperand("generated IRI")
        }
        fun entity(index: Int): Iri = entities.getOrNull(index) ?: throw MissingDocumentDraftOperand("entity operand")
        fun literal(index: Int): RdfLiteral = literals.getOrNull(index)
            ?: texts.getOrNull(index)?.let(::RdfLiteral)
            ?: throw MissingDocumentDraftOperand("literal operand")
        fun label(): RdfLiteral? = texts.firstOrNull()?.let(::RdfLiteral)
        val draft = when (operation.kind) {
            DocumentPlanOperationKind.CreateClass ->
                DocumentDraftOperation.Ontology(CreateClassEdit(required(declaration, "class IRI"), label()))
            DocumentPlanOperationKind.CreateObjectProperty ->
                DocumentDraftOperation.Ontology(CreateObjectPropertyEdit(required(declaration, "property IRI"), label()))
            DocumentPlanOperationKind.CreateDatatypeProperty ->
                DocumentDraftOperation.Ontology(CreateDatatypePropertyEdit(required(declaration, "property IRI"), label()))
            DocumentPlanOperationKind.CreateAnnotationProperty -> DocumentDraftOperation.Semantic(
                SemanticEditRequest.CreateAnnotationProperty(
                    required(declaration, "annotation property IRI"),
                    sourceId,
                    label(),
                ),
            )
            DocumentPlanOperationKind.CreateIndividual ->
                DocumentDraftOperation.Ontology(CreateIndividualEdit(required(declaration, "individual IRI"), entities.firstOrNull()))
            DocumentPlanOperationKind.SetEntityLabel ->
                DocumentDraftOperation.Ontology(SetEntityLabelEdit(entity(0), literal(0)))
            DocumentPlanOperationKind.AddSuperclass ->
                DocumentDraftOperation.Ontology(AddSuperclassEdit(entity(0), entity(1)))
            DocumentPlanOperationKind.RemoveSuperclass ->
                DocumentDraftOperation.Ontology(RemoveSuperclassEdit(entity(0), entity(1)))
            DocumentPlanOperationKind.SetPropertyDomain ->
                DocumentDraftOperation.Ontology(SetPropertyDomainEdit(entity(0), entity(1)))
            DocumentPlanOperationKind.RemovePropertyDomain ->
                DocumentDraftOperation.Ontology(RemovePropertyDomainEdit(entity(0), entity(1)))
            DocumentPlanOperationKind.SetPropertyRange ->
                DocumentDraftOperation.Ontology(SetPropertyRangeEdit(entity(0), entity(1)))
            DocumentPlanOperationKind.RemovePropertyRange ->
                DocumentDraftOperation.Ontology(RemovePropertyRangeEdit(entity(0), entity(1)))
            DocumentPlanOperationKind.AssignType ->
                DocumentDraftOperation.Ontology(AssignTypeEdit(entity(0), entity(1)))
            DocumentPlanOperationKind.AddObjectPropertyAssertion ->
                DocumentDraftOperation.Ontology(AddObjectPropertyAssertionEdit(entity(0), entity(1), entity(2)))
            DocumentPlanOperationKind.AddDatatypePropertyAssertion ->
                DocumentDraftOperation.Ontology(AddDatatypePropertyAssertionEdit(entity(0), entity(1), literal(0)))
            DocumentPlanOperationKind.AddDefinition -> DocumentDraftOperation.Semantic(
                SemanticEditRequest.AddDefinition(entity(0), literal(0), sourceId),
            )
            DocumentPlanOperationKind.ReplaceDefinition -> DocumentDraftOperation.Semantic(
                SemanticEditRequest.ReplaceDefinition(entity(0), literal(0), literal(1), sourceId),
            )
            DocumentPlanOperationKind.RemoveDefinition -> DocumentDraftOperation.Semantic(
                SemanticEditRequest.RemoveDefinition(entity(0), literal(0), sourceId),
            )
            DocumentPlanOperationKind.AddAlternateLabel -> DocumentDraftOperation.Semantic(
                SemanticEditRequest.AddAlternateLabel(entity(0), literal(0), sourceId),
            )
            DocumentPlanOperationKind.ReplaceAlternateLabel -> DocumentDraftOperation.Semantic(
                SemanticEditRequest.ReplaceAlternateLabel(entity(0), literal(0), literal(1), sourceId),
            )
            DocumentPlanOperationKind.RemoveAlternateLabel -> DocumentDraftOperation.Semantic(
                SemanticEditRequest.RemoveAlternateLabel(entity(0), literal(0), sourceId),
            )
            DocumentPlanOperationKind.AddAnnotation -> DocumentDraftOperation.Semantic(
                SemanticEditRequest.AddAnnotation(
                    entity(0),
                    entity(1),
                    AnnotationValue.fromTerm(literals.firstOrNull() ?: entities.getOrNull(2)
                    ?: throw MissingDocumentDraftOperand("annotation value")),
                    sourceId,
                ),
            )
            DocumentPlanOperationKind.RemoveAnnotation -> DocumentDraftOperation.Semantic(
                SemanticEditRequest.RemoveAnnotation(
                    entity(0),
                    entity(1),
                    AnnotationValue.fromTerm(literals.firstOrNull() ?: entities.getOrNull(2)
                    ?: throw MissingDocumentDraftOperand("annotation value")),
                    sourceId,
                ),
            )
            DocumentPlanOperationKind.ReuseExternal -> {
                val external = context.externalIntentsByOperationId[operation.id]
                    ?: throw MissingDocumentDraftOperand("approved external reuse")
                DocumentDraftOperation.ExternalReuse(external.first, external.second)
            }
            DocumentPlanOperationKind.CreateNodeShape -> DocumentDraftOperation.Shacl(
                TypedShaclEdit.CreateNodeShape(
                    sourceId,
                    required(declaration, "shape IRI"),
                    texts.firstOrNull() ?: operation.declaration!!.localName,
                    entity(0),
                ),
            )
            DocumentPlanOperationKind.CreatePropertyShape -> DocumentDraftOperation.Shacl(
                TypedShaclEdit.CreatePropertyShape(
                    sourceId,
                    required(declaration, "shape IRI"),
                    texts.firstOrNull() ?: operation.declaration!!.localName,
                    entity(0),
                    entity(1),
                    shaclConstraint(operation, entities),
                ),
            )
            DocumentPlanOperationKind.UpdateShaclConstraint -> DocumentDraftOperation.Shacl(
                TypedShaclEdit.UpdateConstraint(
                    sourceId,
                    entity(0),
                    entity(1),
                    shaclConstraint(operation, entities.drop(2)),
                ),
            )
            DocumentPlanOperationKind.RemoveShaclConstraint -> DocumentDraftOperation.Shacl(
                TypedShaclEdit.RemoveConstraint(
                    sourceId,
                    entity(0),
                    entity(1),
                    EditableShaclConstraintKind.valueOf(texts.first()),
                ),
            )
            DocumentPlanOperationKind.UpdateShapeLabel -> DocumentDraftOperation.Shacl(
                TypedShaclEdit.UpdateShapeLabel(sourceId, entity(0), texts.first()),
            )
            DocumentPlanOperationKind.DeleteShape ->
                DocumentDraftOperation.Shacl(TypedShaclEdit.DeleteShape(sourceId, entity(0)))
        }
        return PreparedDocumentDraftOperation(
            recommendationId,
            sourceId,
            draft,
            normalizedKey(sourceId, draft),
        )
    }

    private fun shaclConstraint(
        operation: DocumentPlanOperation,
        entityValues: List<Iri>,
    ): EditableShaclConstraint {
        val texts = operation.operands.filterIsInstance<DocumentPlanOperand.TextValue>().map { it.value }
        val kind = EditableShaclConstraintKind.valueOf(texts.first())
        val value = operation.operands.firstNotNullOfOrNull { operand ->
            when (operand) {
                is DocumentPlanOperand.IntegerValue -> EditableShaclConstraintValue.IntegerValue(operand.value)
                is DocumentPlanOperand.DecimalValue -> EditableShaclConstraintValue.DecimalValue(operand.lexicalForm)
                else -> null
            }
        } ?: entityValues.firstOrNull()?.let(EditableShaclConstraintValue::IriValue)
            ?: texts.drop(1).firstOrNull()?.let(EditableShaclConstraintValue::TextValue)
            ?: throw MissingDocumentDraftOperand("SHACL constraint value")
        return EditableShaclConstraint(kind, value)
    }

    /**
     * Produces the same typed operations as drafting without requiring an acceptance decision first.
     *
     * Review-only, stale, duplicate, or incomplete recommendations remain blocked. Human-review
     * gates are bypassed only so the UI can show what an approval would stage after those gates
     * are resolved.
     */
    public fun previewSafely(
        recommendation: DocumentRecommendation,
        context: DocumentDraftTranslationContext,
    ): DocumentDraftTranslationResult = translateSafely(
        recommendation,
        context.copy(
            acceptedForDraft = true,
            clarificationResolved = true,
            lowConfidenceOcrConfirmed = true,
        ),
    )

    public fun translate(
        recommendation: DocumentRecommendation,
        context: DocumentDraftTranslationContext,
    ): DocumentDraftTranslationResult {
        gate(recommendation, context)?.let { return it }
        if (recommendation.action == DocumentRecommendationAction.Confirm) {
            return DocumentDraftTranslationResult.Prepared(
                listOf(PreparedDocumentDraftOperation(recommendation.id, context.targetSourceId.orEmpty(), null, null)),
            )
        }
        val sourceId = context.targetSourceId
            ?: return blocked("document-source-required", "Choose a writable target ontology source.")
        if (recommendation.action == DocumentRecommendationAction.ReuseImportedOrFibo) {
            val intent = context.externalIntent
                ?: return blocked("document-external-context-required", "Approved external reuse context is missing.")
            val ontologyIri = context.targetOntologyIri
                ?: return blocked("document-external-context-required", "The target ontology IRI is missing.")
            val operation = DocumentDraftOperation.ExternalReuse(intent, ontologyIri)
            return DocumentDraftTranslationResult.Prepared(
                listOf(
                    PreparedDocumentDraftOperation(
                        recommendation.id,
                        sourceId,
                        operation,
                        normalizedKey(sourceId, operation),
                    ),
                ),
            )
        }
        val operations = when (recommendation.type) {
            DocumentCandidateCategory.Class -> classOperations(recommendation, context, sourceId)
            DocumentCandidateCategory.ObjectProperty -> propertyOperations(
                recommendation,
                context,
                sourceId,
                objectProperty = true,
            )
            DocumentCandidateCategory.DatatypeProperty -> propertyOperations(
                recommendation,
                context,
                sourceId,
                objectProperty = false,
            )
            DocumentCandidateCategory.Individual -> listOf(
                DocumentDraftOperation.Ontology(
                    CreateIndividualEdit(required(context.targetIri, "individual IRI"), context.relatedIri),
                ),
            )
            DocumentCandidateCategory.Label -> listOf(
                DocumentDraftOperation.Ontology(
                    SetEntityLabelEdit(required(context.targetIri, "entity IRI"), requiredLabel(recommendation)),
                ),
            )
            DocumentCandidateCategory.AnnotationValue -> listOf(
                DocumentDraftOperation.Semantic(
                    if (context.existingLiteral == null) {
                        SemanticEditRequest.AddAlternateLabel(
                            required(context.targetIri, "entity IRI"),
                            requiredLiteral(context.objectTerm, "alternate label"),
                            sourceId,
                        )
                    } else {
                        SemanticEditRequest.ReplaceAlternateLabel(
                            required(context.targetIri, "entity IRI"),
                            context.existingLiteral,
                            requiredLiteral(context.objectTerm, "alternate label"),
                            sourceId,
                        )
                    },
                ),
            )
            DocumentCandidateCategory.Definition -> listOf(
                DocumentDraftOperation.Semantic(
                    if (context.existingLiteral == null) {
                        SemanticEditRequest.AddDefinition(
                            required(context.targetIri, "entity IRI"),
                            recommendation.proposedDefinition ?: requiredLiteral(context.objectTerm, "definition"),
                            sourceId,
                        )
                    } else {
                        SemanticEditRequest.ReplaceDefinition(
                            required(context.targetIri, "entity IRI"),
                            context.existingLiteral,
                            recommendation.proposedDefinition ?: requiredLiteral(context.objectTerm, "definition"),
                            sourceId,
                        )
                    },
                ),
            )
            DocumentCandidateCategory.SuperclassRelationship -> relationshipRevision(
                recommendation,
                context.existingRelatedIri?.let { RemoveSuperclassEdit(required(context.targetIri, "class IRI"), it) },
                AddSuperclassEdit(required(context.targetIri, "class IRI"), required(context.relatedIri, "superclass IRI")),
            )
            DocumentCandidateCategory.Domain -> relationshipRevision(
                recommendation,
                context.existingRelatedIri?.let { RemovePropertyDomainEdit(required(context.propertyIri, "property IRI"), it) },
                SetPropertyDomainEdit(required(context.propertyIri, "property IRI"), required(context.relatedIri, "domain IRI")),
            )
            DocumentCandidateCategory.Range -> relationshipRevision(
                recommendation,
                context.existingRelatedIri?.let { RemovePropertyRangeEdit(required(context.propertyIri, "property IRI"), it) },
                SetPropertyRangeEdit(required(context.propertyIri, "property IRI"), required(context.relatedIri, "range IRI")),
            )
            DocumentCandidateCategory.TypeAssertion -> listOf(
                DocumentDraftOperation.Ontology(
                    AssignTypeEdit(required(context.targetIri, "resource IRI"), required(context.relatedIri, "type IRI")),
                ),
            )
            DocumentCandidateCategory.ObjectPropertyAssertion -> listOf(
                DocumentDraftOperation.Ontology(
                    AddObjectPropertyAssertionEdit(
                        required(context.targetIri, "subject IRI"),
                        required(context.propertyIri, "property IRI"),
                        requiredResource(context.objectTerm, "object resource"),
                    ),
                ),
            )
            DocumentCandidateCategory.DatatypeValue -> listOf(
                DocumentDraftOperation.Ontology(
                    AddDatatypePropertyAssertionEdit(
                        required(context.targetIri, "subject IRI"),
                        required(context.propertyIri, "property IRI"),
                        requiredLiteral(context.objectTerm, "datatype value"),
                    ),
                ),
            )
            DocumentCandidateCategory.ShaclConstraint,
            -> listOf(DocumentDraftOperation.Shacl(approvedShacl(context.shaclEdit, sourceId)))
            DocumentCandidateCategory.Conflict,
            DocumentCandidateCategory.Ambiguity,
            -> return blocked("document-recommendation-review-only", "This recommendation remains review-only.")
        }
        if (operations.isEmpty()) {
            return blocked(
                "document-recommendation-no-exact-change",
                "This recommendation does not contain an exact supported ontology change.",
            )
        }
        return DocumentDraftTranslationResult.Prepared(
            operations.map { operation ->
                PreparedDocumentDraftOperation(
                    recommendation.id,
                    sourceId,
                    operation,
                    normalizedKey(sourceId, operation),
                )
            },
        )
    }

    private fun classOperations(
        recommendation: DocumentRecommendation,
        context: DocumentDraftTranslationContext,
        sourceId: String,
    ): List<DocumentDraftOperation> {
        val target = required(context.targetIri, "class IRI")
        return buildList {
            if (recommendation.action == DocumentRecommendationAction.CreateLocal) {
                add(DocumentDraftOperation.Ontology(CreateClassEdit(target, recommendation.proposedLabel?.asLabel())))
            }
            recommendation.proposedDefinition?.let { definition ->
                add(DocumentDraftOperation.Semantic(SemanticEditRequest.AddDefinition(target, definition, sourceId)))
            }
            if (recommendation.action == DocumentRecommendationAction.CreateLocal) {
                recommendation.proposedConnectionLabel?.let { connectionLabel ->
                    val property = required(context.connectionPropertyIri, "connecting property IRI")
                    val domain = required(context.connectionDomainIri, "connecting property domain IRI")
                    add(DocumentDraftOperation.Ontology(CreateObjectPropertyEdit(property, connectionLabel.asLabel())))
                    add(DocumentDraftOperation.Ontology(SetPropertyDomainEdit(property, domain)))
                    add(DocumentDraftOperation.Ontology(SetPropertyRangeEdit(property, target)))
                }
            }
        }
    }

    private fun propertyOperations(
        recommendation: DocumentRecommendation,
        context: DocumentDraftTranslationContext,
        sourceId: String,
        objectProperty: Boolean,
    ): List<DocumentDraftOperation> {
        val property = required(context.targetIri, "property IRI")
        return buildList {
            if (recommendation.action == DocumentRecommendationAction.CreateLocal) {
                add(
                    DocumentDraftOperation.Ontology(
                        if (objectProperty) {
                            CreateObjectPropertyEdit(property, recommendation.proposedLabel?.asLabel())
                        } else {
                            CreateDatatypePropertyEdit(property, recommendation.proposedLabel?.asLabel())
                        },
                    ),
                )
            }
            context.domainIri?.let { domain ->
                add(DocumentDraftOperation.Ontology(SetPropertyDomainEdit(property, domain)))
            }
            context.rangeIri?.let { range ->
                add(DocumentDraftOperation.Ontology(SetPropertyRangeEdit(property, range)))
            }
            recommendation.proposedDefinition?.let { definition ->
                add(DocumentDraftOperation.Semantic(SemanticEditRequest.AddDefinition(property, definition, sourceId)))
            }
        }
    }

    private fun gate(
        recommendation: DocumentRecommendation,
        context: DocumentDraftTranslationContext,
    ): DocumentDraftTranslationResult.Blocked? {
        if (!context.acceptedForDraft) {
            return blocked("document-recommendation-not-accepted", "Only accepted recommendations can enter the draft.")
        }
        if (!context.graphCurrent || !context.evidenceCurrent || !context.modelAndPromptCurrent) {
            return blocked("document-draft-stale", "The graph, evidence, model, or prompt changed; reprocess before drafting.")
        }
        if (context.duplicateOperation) {
            return blocked("document-draft-duplicate", "The same typed operation already exists in applied or staged work.")
        }
        if (recommendation.confidence < 60 && !context.lowConfidenceOcrConfirmed) {
            return blocked("document-low-confidence-unconfirmed", "Low-confidence evidence requires explicit confirmation.")
        }
        if ((recommendation.ambiguities.isNotEmpty() || recommendation.conflicts.isNotEmpty() ||
                recommendation.mandatoryClarificationReasons.isNotEmpty()) &&
            !context.clarificationResolved
        ) {
            return blocked("document-clarification-required", "Resolve ambiguity, conflict, and mandatory clarification first.")
        }
        if (recommendation.action in unsupportedActions) {
            return blocked("document-recommendation-unsupported", "This recommendation action cannot become a typed draft.")
        }
        return null
    }

    private fun relationshipRevision(
        recommendation: DocumentRecommendation,
        removal: TypedOntologyEdit?,
        addition: TypedOntologyEdit,
    ): List<DocumentDraftOperation> {
        if (recommendation.action in setOf(DocumentRecommendationAction.Revise, DocumentRecommendationAction.Supersede) &&
            removal == null
        ) {
            throw MissingDocumentDraftOperand("exact current relationship")
        }
        return listOfNotNull(removal?.let(DocumentDraftOperation::Ontology), DocumentDraftOperation.Ontology(addition))
    }

    private fun approvedShacl(edit: TypedShaclEdit?, sourceId: String): TypedShaclEdit {
        val current = edit ?: throw MissingDocumentDraftOperand("typed SHACL edit")
        if (current.sourceId != sourceId ||
            current !is TypedShaclEdit.CreateNodeShape &&
            current !is TypedShaclEdit.CreatePropertyShape &&
            current !is TypedShaclEdit.UpdateConstraint
        ) {
            throw UnsupportedDocumentDraftOperation()
        }
        return current
    }

    private fun normalizedKey(
        sourceId: String,
        operation: DocumentDraftOperation,
    ): String = listOf("document-draft-v1", sourceId, operation.toString())
        .joinToString("\u0000")
        .take(1_000)

    private fun required(value: Iri?, label: String): Iri = value ?: throw MissingDocumentDraftOperand(label)

    private fun requiredResource(value: com.entio.core.RdfTerm?, label: String): RdfResource =
        value as? RdfResource ?: throw MissingDocumentDraftOperand(label)

    private fun requiredLiteral(value: com.entio.core.RdfTerm?, label: String): RdfLiteral =
        value as? RdfLiteral ?: throw MissingDocumentDraftOperand(label)

    private fun requiredLabel(recommendation: DocumentRecommendation): RdfLiteral =
        recommendation.proposedLabel?.asLabel() ?: throw MissingDocumentDraftOperand("label")

    private fun String.asLabel(): RdfLiteral = RdfLiteral(this, datatypeIri = XSD_STRING)

    private fun blocked(code: String, message: String): DocumentDraftTranslationResult.Blocked =
        DocumentDraftTranslationResult.Blocked(code, message)

    public fun translateSafely(
        recommendation: DocumentRecommendation,
        context: DocumentDraftTranslationContext,
    ): DocumentDraftTranslationResult = try {
        translate(recommendation, context)
    } catch (failure: MissingDocumentDraftOperand) {
        blocked("document-draft-operand-missing", "The approved typed mapping is missing ${failure.label}.")
    } catch (_: UnsupportedDocumentDraftOperation) {
        blocked("document-draft-operation-unsupported", "The requested typed operation is not approved for ingestion.")
    }

    private companion object {
        val XSD_STRING: Iri = Iri("http://www.w3.org/2001/XMLSchema#string")
        val unsupportedActions: Set<DocumentRecommendationAction> = setOf(
            DocumentRecommendationAction.Split,
            DocumentRecommendationAction.Merge,
            DocumentRecommendationAction.Conflict,
            DocumentRecommendationAction.InsufficientEvidence,
            DocumentRecommendationAction.Unsupported,
        )
    }
}

private class MissingDocumentDraftOperand(val label: String) : IllegalArgumentException()
private class UnsupportedDocumentDraftOperation : IllegalArgumentException()
