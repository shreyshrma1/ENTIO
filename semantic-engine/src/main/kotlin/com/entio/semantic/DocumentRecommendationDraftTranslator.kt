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
import com.entio.core.DocumentRecommendation
import com.entio.core.DocumentRecommendationAction
import com.entio.core.ExternalProposalIntent
import com.entio.core.Iri
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

/** Converts reviewed recommendations only into existing, approved typed operations. */
public class DocumentRecommendationDraftTranslator {
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
            DocumentCandidateCategory.Class -> listOf(
                DocumentDraftOperation.Ontology(
                    CreateClassEdit(required(context.targetIri, "class IRI"), recommendation.proposedLabel?.asLabel()),
                ),
            )
            DocumentCandidateCategory.ObjectProperty -> listOf(
                DocumentDraftOperation.Ontology(
                    CreateObjectPropertyEdit(required(context.targetIri, "property IRI"), recommendation.proposedLabel?.asLabel()),
                ),
            )
            DocumentCandidateCategory.DatatypeProperty -> listOf(
                DocumentDraftOperation.Ontology(
                    CreateDatatypePropertyEdit(required(context.targetIri, "property IRI"), recommendation.proposedLabel?.asLabel()),
                ),
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
            DocumentCandidateCategory.BusinessRule,
            DocumentCandidateCategory.ShaclConstraint,
            -> listOf(DocumentDraftOperation.Shacl(approvedShacl(context.shaclEdit, sourceId)))
            DocumentCandidateCategory.Conflict,
            DocumentCandidateCategory.Ambiguity,
            -> return blocked("document-recommendation-review-only", "This recommendation remains review-only.")
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
