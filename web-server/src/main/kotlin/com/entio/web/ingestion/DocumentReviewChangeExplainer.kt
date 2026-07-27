package com.entio.web.ingestion

import com.entio.core.AddDatatypePropertyAssertionEdit
import com.entio.core.AddObjectPropertyAssertionEdit
import com.entio.core.AddSuperclassEdit
import com.entio.core.AssignTypeEdit
import com.entio.core.BlankNodeResource
import com.entio.core.CreateClassEdit
import com.entio.core.CreateDatatypePropertyEdit
import com.entio.core.CreateIndividualEdit
import com.entio.core.CreateObjectPropertyEdit
import com.entio.core.DocumentRecommendation
import com.entio.core.DocumentRecommendationAction
import com.entio.core.EditableShaclConstraint
import com.entio.core.EditableShaclConstraintValue
import com.entio.core.Iri
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
import com.entio.semantic.DocumentDraftOperation
import com.entio.semantic.DocumentDraftTranslationContext
import com.entio.semantic.DocumentDraftTranslationResult
import com.entio.semantic.DocumentRecommendationDraftTranslator
import com.entio.semantic.PreparedDocumentDraftOperation

internal data class DocumentReviewExplanation(
    val description: String,
    val changePreview: DocumentReviewChangePreview,
)

/** Adapts the authoritative typed-draft translation into plain review language. */
internal class DocumentReviewChangeExplainer(
    private val translator: DocumentRecommendationDraftTranslator = DocumentRecommendationDraftTranslator(),
) {
    fun explain(
        recommendation: DocumentRecommendation,
        context: DocumentDraftTranslationContext?,
    ): DocumentReviewExplanation {
        if (context == null) {
            return blocked(
                recommendation,
                "Entio does not have enough typed information to prepare an ontology change.",
            )
        }
        return when (val result = translator.previewSafely(recommendation, context)) {
            is DocumentDraftTranslationResult.Blocked -> blocked(recommendation, result.message)
            is DocumentDraftTranslationResult.Prepared -> {
                val operations = result.operations.map(::describe)
                val confirmOnly = result.operations.isNotEmpty() && result.operations.all(PreparedDocumentDraftOperation::confirmOnly)
                DocumentReviewExplanation(
                    description(recommendation, draftable = true),
                    DocumentReviewChangePreview(
                        draftable = true,
                        summary = if (confirmOnly) {
                            "Keep this document as supporting provenance. No ontology statements will change."
                        } else {
                            "${operations.size} exact change${if (operations.size == 1) "" else "s"} will be added to the proposal."
                        },
                        operations = operations,
                        blockingReason = null,
                    ),
                )
            }
        }
    }

    private fun blocked(recommendation: DocumentRecommendation, reason: String): DocumentReviewExplanation =
        DocumentReviewExplanation(
            description(recommendation, draftable = false),
            DocumentReviewChangePreview(
                draftable = false,
                summary = "No ontology change can be created from this recommendation.",
                operations = emptyList(),
                blockingReason = reason,
            ),
        )

    private fun description(recommendation: DocumentRecommendation, draftable: Boolean): String {
        val label = recommendation.proposedLabel?.let { "“$it”" } ?: "this document finding"
        if (!draftable) {
            return "Entio found possible meaning about $label, but it cannot safely map that meaning to a supported change."
        }
        return when (recommendation.action) {
            DocumentRecommendationAction.Confirm ->
                "The document supports meaning that is already represented in the ontology."
            DocumentRecommendationAction.CreateLocal ->
                "The document describes $label, which is not currently represented in the selected ontology source."
            DocumentRecommendationAction.Extend ->
                "The document adds information about $label to a selected ontology item."
            DocumentRecommendationAction.Revise ->
                "The document proposes replacing existing meaning about $label."
            DocumentRecommendationAction.ReuseLocal ->
                "The document refers to an item that already exists in this project."
            DocumentRecommendationAction.ReuseImportedOrFibo ->
                "The document refers to an approved term from an imported ontology."
            DocumentRecommendationAction.Split ->
                "The document suggests that $label may represent more than one concept."
            DocumentRecommendationAction.Merge ->
                "The document suggests that $label duplicates another concept."
            DocumentRecommendationAction.Conflict ->
                "The documents contain conflicting statements about $label."
            DocumentRecommendationAction.Supersede ->
                "The document explicitly replaces earlier meaning about $label."
            DocumentRecommendationAction.InsufficientEvidence ->
                "The available document evidence is not strong enough to propose a change for $label."
            DocumentRecommendationAction.Unsupported ->
                "The document describes $label, but that change is outside Entio's supported review workflow."
        }
    }

    private fun describe(prepared: PreparedDocumentDraftOperation): DocumentReviewProposedOperation {
        val operation = prepared.operation
        return when (operation) {
            null -> DocumentReviewProposedOperation(
                operation = "Record supporting provenance",
                description = "Link the reviewed document evidence to existing ontology meaning without changing the ontology.",
                targetSourceId = prepared.targetSourceId.takeIf(String::isNotBlank),
            )
            is DocumentDraftOperation.Ontology -> operation.edit.describe(prepared.targetSourceId)
            is DocumentDraftOperation.Semantic -> operation.edit.describe(prepared.targetSourceId)
            is DocumentDraftOperation.Shacl -> operation.edit.describe(prepared.targetSourceId)
            is DocumentDraftOperation.ExternalReuse -> DocumentReviewProposedOperation(
                operation = "Reuse approved external term",
                description = "Add ${operation.intent.kind.name} to ontology ${operation.targetOntologyIri.value}.",
                targetSourceId = prepared.targetSourceId,
            )
        }
    }

    private fun TypedOntologyEdit.describe(sourceId: String): DocumentReviewProposedOperation = when (this) {
        is CreateClassEdit -> change("Create class", "Create ${classIri.value}${label?.let { " with label “${it.lexicalForm}”" }.orEmpty()}.", sourceId)
        is SetEntityLabelEdit -> change("Set label", "Set ${entity.display()} label to “${label.lexicalForm}”.", sourceId)
        is AddSuperclassEdit -> change("Add parent class", "Make ${classIri.value} a subclass of ${superclassIri.value}.", sourceId)
        is RemoveSuperclassEdit -> change("Remove parent class", "Remove ${superclassIri.value} as a parent of ${classIri.value}.", sourceId)
        is CreateObjectPropertyEdit -> change("Create relationship", "Create ${propertyIri.value}${label?.let { " with label “${it.lexicalForm}”" }.orEmpty()}.", sourceId)
        is CreateDatatypePropertyEdit -> change("Create data field", "Create ${propertyIri.value}${label?.let { " with label “${it.lexicalForm}”" }.orEmpty()}.", sourceId)
        is SetPropertyDomainEdit -> change("Set relationship source", "Set ${propertyIri.value} domain to ${domainClassIri.value}.", sourceId)
        is SetPropertyRangeEdit -> change("Set relationship target", "Set ${propertyIri.value} range to ${rangeIri.value}.", sourceId)
        is RemovePropertyDomainEdit -> change("Remove relationship source", "Remove ${domainClassIri.value} as the domain of ${propertyIri.value}.", sourceId)
        is RemovePropertyRangeEdit -> change("Remove relationship target", "Remove ${rangeIri.value} as the range of ${propertyIri.value}.", sourceId)
        is CreateIndividualEdit -> change(
            "Create individual",
            "Create ${individualIri.value}${classIri?.let { " as an instance of ${it.value}" }.orEmpty()}.",
            sourceId,
        )
        is AssignTypeEdit -> change("Assign type", "Make ${resource.display()} an instance of ${typeIri.value}.", sourceId)
        is AddObjectPropertyAssertionEdit -> change(
            "Add relationship",
            "Add ${subject.display()} — ${propertyIri.value} — ${objectResource.display()}.",
            sourceId,
        )
        is AddDatatypePropertyAssertionEdit -> change(
            "Add value",
            "Set ${subject.display()} — ${propertyIri.value} — “${value.lexicalForm}”.",
            sourceId,
        )
    }

    private fun SemanticEditRequest.describe(sourceId: String): DocumentReviewProposedOperation = when (this) {
        is SemanticEditRequest.CreateAnnotationProperty -> change(
            "Create annotation property",
            "Create ${propertyIri.value}${label?.let { " with label “${it.lexicalForm}”" }.orEmpty()}.",
            sourceId,
        )
        is SemanticEditRequest.AddDefinition ->
            change("Add definition", "Add definition “${value.lexicalForm}” to ${target.display()}.", sourceId)
        is SemanticEditRequest.ReplaceDefinition -> change(
            "Replace definition",
            "Replace “${existing.lexicalForm}” with “${replacement.lexicalForm}” on ${target.display()}.",
            sourceId,
        )
        is SemanticEditRequest.RemoveDefinition ->
            change("Remove definition", "Remove definition “${value.lexicalForm}” from ${target.display()}.", sourceId)
        is SemanticEditRequest.AddAlternateLabel ->
            change("Add alternate label", "Add alternate label “${value.lexicalForm}” to ${target.display()}.", sourceId)
        is SemanticEditRequest.ReplaceAlternateLabel -> change(
            "Replace alternate label",
            "Replace “${existing.lexicalForm}” with “${replacement.lexicalForm}” on ${target.display()}.",
            sourceId,
        )
        is SemanticEditRequest.RemoveAlternateLabel ->
            change("Remove alternate label", "Remove alternate label “${value.lexicalForm}” from ${target.display()}.", sourceId)
        is SemanticEditRequest.AddAnnotation ->
            change("Add annotation", "Add annotation ${property.value} to ${target.display()}.", sourceId)
        is SemanticEditRequest.RemoveAnnotation ->
            change("Remove annotation", "Remove annotation ${property.value} from ${target.display()}.", sourceId)
    }

    private fun TypedShaclEdit.describe(sourceId: String): DocumentReviewProposedOperation = when (this) {
        is TypedShaclEdit.CreateNodeShape -> change(
            "Create validation rule",
            "Create node shape ${shapeIri.value} for ${targetClassIri.value} with label “$label”.",
            sourceId,
        )
        is TypedShaclEdit.CreatePropertyShape -> change(
            "Create validation rule",
            "Create property shape ${shapeIri.value} for ${targetClassIri.value}; validate ${pathIri.value} with ${constraint.display()}.",
            sourceId,
        )
        is TypedShaclEdit.UpdateConstraint -> change(
            "Update validation rule",
            "Update ${shapeIri.value} on ${pathIri.value} to ${constraint.display()}.",
            sourceId,
        )
        is TypedShaclEdit.UpdateShapeLabel,
        is TypedShaclEdit.RemoveConstraint,
        is TypedShaclEdit.DeleteShape,
        -> change("Unsupported validation edit", "This validation edit is not approved for document ingestion.", sourceId)
    }

    private fun EditableShaclConstraint.display(): String = "${kind.name} ${when (val current = value) {
        is EditableShaclConstraintValue.IntegerValue -> current.value
        is EditableShaclConstraintValue.IriValue -> current.value.value
        is EditableShaclConstraintValue.DecimalValue -> current.lexicalForm
        is EditableShaclConstraintValue.TextValue -> "“${current.value}”"
    }}"

    private fun change(operation: String, description: String, sourceId: String): DocumentReviewProposedOperation =
        DocumentReviewProposedOperation(operation, description, sourceId)

    private fun RdfResource.display(): String = when (this) {
        is Iri -> value
        is BlankNodeResource -> "_:$id"
    }
}
