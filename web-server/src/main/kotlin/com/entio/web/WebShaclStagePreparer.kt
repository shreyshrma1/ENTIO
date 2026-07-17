package com.entio.web

import com.entio.core.EditableShaclConstraint
import com.entio.core.EditableShaclConstraintKind
import com.entio.core.EditableShaclConstraintValue
import com.entio.core.EntityCandidate
import com.entio.core.EntityResolutionResult
import com.entio.core.EntitySelector
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.GeneratedIri
import com.entio.core.Iri
import com.entio.core.ShaclGraphRole
import com.entio.core.ShaclSeverity
import com.entio.core.SymbolKind
import com.entio.core.TypedShaclEdit
import com.entio.semantic.DeterministicIriGenerator
import com.entio.semantic.LabelResolver
import com.entio.semantic.TypedShaclEditTranslator
import com.entio.web.contract.WebStageChangeRequest
import java.nio.file.Files

internal data class PreparedShaclStage(
    val edit: TypedShaclEdit,
    val summary: String,
    val normalizedValues: Map<String, String>,
    val resolvedCandidates: List<EntityCandidate>,
    val generatedIris: List<GeneratedIri>,
)

/** Validates a strict web SHACL request and delegates graph translation to the semantic engine. */
internal class WebShaclStagePreparer(
    private val translator: TypedShaclEditTranslator = TypedShaclEditTranslator(),
    private val labelResolver: LabelResolver = LabelResolver(),
    private val iriGenerator: DeterministicIriGenerator = DeterministicIriGenerator(),
) {
    fun prepare(project: EntioProject, request: WebStageChangeRequest): PreparedShaclStage {
        val source = project.resolvedSources.firstOrNull { it.id == request.sourceId }
            ?: throw WebWorkflowFailure("unknown-source", "Ontology source '${request.sourceId}' was not found.")
        if (ShaclGraphRole.Shapes !in source.roles) {
            throw WebWorkflowFailure("invalid-shacl-source-role", "SHACL edits require a source with the shapes role.")
        }
        if (!Files.isWritable(source.path)) {
            throw WebWorkflowFailure("immutable-shacl-source", "The selected SHACL source is not writable.")
        }
        val ontology = project.ontologies.first { it.source.id == source.id }
        val resolved = mutableListOf<EntityCandidate>()
        val generated = mutableListOf<GeneratedIri>()

        fun existing(iri: String?, label: String?, kind: SymbolKind, field: String): Iri {
            val candidate = if (!iri.isNullOrBlank()) {
                project.symbols.firstOrNull { it.iri.value == iri && it.kind == kind }?.let {
                    EntityCandidate(it.iri, it.label, it.kind, it.sourceId)
                }
            } else {
                val value = label?.takeIf(String::isNotBlank)
                    ?: throw WebWorkflowFailure("missing-field", "A $field label is required.")
                when (val result = labelResolver.resolve(project.symbols, EntitySelector(label = value, kind = kind))) {
                    is EntityResolutionResult.Resolved -> result.candidate
                    is EntityResolutionResult.Ambiguous -> throw WebWorkflowFailure("ambiguous-label", "The $field label matches more than one entity.")
                    EntityResolutionResult.NotFound -> throw WebWorkflowFailure("unknown-entity", "The $field '$value' does not exist.")
                    is EntityResolutionResult.Invalid -> throw WebWorkflowFailure("invalid-entity-selector", result.reason)
                }
            } ?: throw WebWorkflowFailure("unknown-entity", "The $field does not exist.")
            resolved += candidate
            return candidate.iri
        }

        fun shapeIri(create: Boolean): Iri {
            request.shapeIri?.takeIf(String::isNotBlank)?.let(::Iri)?.let { explicit ->
                if (!create) {
                    val symbol = project.symbols.firstOrNull { it.iri == explicit && it.kind == SymbolKind.Shape && it.sourceId == request.sourceId }
                        ?: throw WebWorkflowFailure("shacl-shape-not-found", "The shape does not exist in the selected source.")
                    resolved += EntityCandidate(symbol.iri, symbol.label, symbol.kind, symbol.sourceId)
                }
                return explicit
            }
            if (!create) return existing(null, request.shapeLabel, SymbolKind.Shape, "shape").also { iri ->
                if (resolved.last().sourceId != request.sourceId) {
                    throw WebWorkflowFailure("shacl-source-mismatch", "The shape is not declared in the selected source.")
                }
            }
            val label = request.shapeLabel?.takeIf(String::isNotBlank)
                ?: throw WebWorkflowFailure("missing-field", "A shape label is required.")
            return when (val result = iriGenerator.generate(label, SymbolKind.Shape, project.config.iriNamespace, project.symbols)) {
                is EntioResult.Failure -> throw WebWorkflowFailure(result.issues.firstOrNull()?.code ?: "iri-generation-failed", result.message)
                is EntioResult.Success -> result.value.also(generated::add).iri
            }
        }

        fun constraint(): EditableShaclConstraint {
            val kind = request.constraintKind?.toEditableConstraintKind()
                ?: throw WebWorkflowFailure("missing-field", "A SHACL constraint kind is required.")
            val raw = request.constraintValue?.takeIf(String::isNotBlank)
                ?: throw WebWorkflowFailure("missing-field", "A SHACL constraint value is required.")
            val value = when (kind) {
                EditableShaclConstraintKind.MinCount,
                EditableShaclConstraintKind.MaxCount,
                -> EditableShaclConstraintValue.IntegerValue(
                    raw.toIntOrNull() ?: throw WebWorkflowFailure("invalid-shacl-value", "Count constraints require a non-negative integer."),
                )
                EditableShaclConstraintKind.Datatype -> EditableShaclConstraintValue.IriValue(
                    Iri(
                        standardDatatype(raw)
                            ?: raw.takeIf(::isAbsoluteIri)
                            ?: throw WebWorkflowFailure("invalid-shacl-datatype", "A datatype must be a supported name or an absolute IRI."),
                    ),
                )
                EditableShaclConstraintKind.Class -> EditableShaclConstraintValue.IriValue(existing(raw.takeIf(::isAbsoluteIri), raw.takeUnless(::isAbsoluteIri), SymbolKind.Class, "constraint class"))
                EditableShaclConstraintKind.MinInclusive,
                EditableShaclConstraintKind.MaxInclusive,
                -> EditableShaclConstraintValue.DecimalValue(raw)
                EditableShaclConstraintKind.Pattern -> EditableShaclConstraintValue.TextValue(raw)
            }
            return EditableShaclConstraint(kind, value)
        }

        val create = request.editType in setOf("shacl-create-node-shape", "shacl-create-property-shape")
        val shapeIri = shapeIri(create)
        val edit = when (request.editType) {
            "shacl-create-node-shape" -> TypedShaclEdit.CreateNodeShape(
                sourceId = request.sourceId,
                shapeIri = shapeIri,
                label = request.shapeLabel.orEmpty(),
                targetClassIri = existing(request.targetClassIri, request.targetClassLabel, SymbolKind.Class, "target class"),
                severity = request.severity.toSeverity(),
                message = request.validationMessage,
            )
            "shacl-create-property-shape" -> TypedShaclEdit.CreatePropertyShape(
                sourceId = request.sourceId,
                shapeIri = shapeIri,
                label = request.shapeLabel.orEmpty(),
                targetClassIri = existing(request.targetClassIri, request.targetClassLabel, SymbolKind.Class, "target class"),
                pathIri = existing(request.pathIri, request.pathLabel, SymbolKind.Property, "property path"),
                constraint = constraint(),
                severity = request.severity.toSeverity(),
                message = request.validationMessage,
            )
            "shacl-update-constraint" -> TypedShaclEdit.UpdateConstraint(
                request.sourceId,
                shapeIri,
                existing(request.pathIri, request.pathLabel, SymbolKind.Property, "property path"),
                constraint(),
            )
            "shacl-remove-constraint" -> TypedShaclEdit.RemoveConstraint(
                request.sourceId,
                shapeIri,
                existing(request.pathIri, request.pathLabel, SymbolKind.Property, "property path"),
                request.constraintKind?.toEditableConstraintKind()
                    ?: throw WebWorkflowFailure("missing-field", "A SHACL constraint kind is required."),
            )
            "shacl-delete-shape" -> TypedShaclEdit.DeleteShape(request.sourceId, shapeIri)
            else -> throw WebWorkflowFailure("unsupported-shacl-edit", "The requested typed SHACL edit is not supported.")
        }
        val changes = when (val translated = translator.translate(edit, ontology.graph)) {
            is EntioResult.Failure -> throw WebWorkflowFailure(translated.issues.firstOrNull()?.code ?: "shacl-edit-invalid", translated.message)
            is EntioResult.Success -> translated.value
        }
        return PreparedShaclStage(
            edit = edit,
            summary = request.shaclSummary(),
            normalizedValues = buildMap {
                put("shapeIri", shapeIri.value)
                put("sourceId", request.sourceId)
                request.pathIri?.let { put("pathIri", it) }
                request.constraintKind?.let { put("constraintKind", it) }
                request.constraintValue?.let { put("constraintValue", it) }
            },
            resolvedCandidates = resolved,
            generatedIris = generated,
        )
    }
}

internal fun WebStageChangeRequest.isShaclEdit(): Boolean = editType.startsWith("shacl-")

private fun String.toEditableConstraintKind(): EditableShaclConstraintKind = EditableShaclConstraintKind.entries.firstOrNull {
    it.name.equals(replace("-", ""), ignoreCase = true)
} ?: throw WebWorkflowFailure("unsupported-shacl-constraint", "Constraint '$this' is not supported by typed SHACL editing.")

private fun String?.toSeverity(): ShaclSeverity = when {
    isNullOrBlank() -> ShaclSeverity.Violation
    else -> ShaclSeverity.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }
        ?: throw WebWorkflowFailure("invalid-shacl-severity", "SHACL severity '$this' is not supported.")
}

private fun standardDatatype(value: String): String? = when (value.lowercase()) {
    "string", "xsd:string" -> "http://www.w3.org/2001/XMLSchema#string"
    "integer", "xsd:integer" -> "http://www.w3.org/2001/XMLSchema#integer"
    "decimal", "xsd:decimal" -> "http://www.w3.org/2001/XMLSchema#decimal"
    "boolean", "xsd:boolean" -> "http://www.w3.org/2001/XMLSchema#boolean"
    "date", "xsd:date" -> "http://www.w3.org/2001/XMLSchema#date"
    "datetime", "xsd:datetime" -> "http://www.w3.org/2001/XMLSchema#dateTime"
    else -> null
}

private fun isAbsoluteIri(value: String): Boolean = value.startsWith("http://") || value.startsWith("https://")

private fun WebStageChangeRequest.shaclSummary(): String = when (editType) {
    "shacl-create-node-shape" -> "Create SHACL node shape '${shapeLabel.orEmpty()}'"
    "shacl-create-property-shape" -> "Create SHACL property shape '${shapeLabel.orEmpty()}'"
    "shacl-update-constraint" -> "Update SHACL ${constraintKind.orEmpty()} constraint"
    "shacl-remove-constraint" -> "Remove SHACL ${constraintKind.orEmpty()} constraint"
    "shacl-delete-shape" -> "Delete SHACL shape '${shapeLabel ?: shapeIri.orEmpty()}'"
    else -> editType
}
