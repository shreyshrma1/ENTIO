package com.entio.semantic

import com.entio.core.EditableShaclConstraint
import com.entio.core.EditableShaclConstraintKind
import com.entio.core.EditableShaclConstraintValue
import com.entio.core.ChangeSet
import com.entio.core.EntioResult
import com.entio.core.GraphChange
import com.entio.core.GraphChangeKind
import com.entio.core.GraphState
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.core.ShaclConstraint
import com.entio.core.ShaclConstraintKind
import com.entio.core.ShaclConstraintValue
import com.entio.core.ShaclNodeShape
import com.entio.core.ShaclPath
import com.entio.core.ShaclPropertyShape
import com.entio.core.ShaclShapeId
import com.entio.core.ShaclTarget
import com.entio.core.TypedShaclEdit
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Translates the bounded public SHACL edit contract through the existing shape authoring service. */
public class TypedShaclEditTranslator(
    private val authoring: ShaclShapeAuthoringService = ShaclShapeAuthoringService(),
) {
    public fun translate(edit: TypedShaclEdit, currentShapesGraph: GraphState): EntioResult<com.entio.core.ChangeSet> = try {
        translateValidated(edit, currentShapesGraph)
    } catch (exception: IllegalArgumentException) {
        failure(exception.message ?: "The SHACL edit is invalid.", "shacl-edit-invalid")
    }

    private fun translateValidated(edit: TypedShaclEdit, currentShapesGraph: GraphState): EntioResult<com.entio.core.ChangeSet> {
        val document = when (val loaded = authoring.load(edit.sourceId, currentShapesGraph)) {
            is EntioResult.Failure -> return loaded
            is EntioResult.Success -> loaded.value
        }
        val existing = document.nodeShapes.firstOrNull { it.id.iri == edit.shapeIri }
        val shapeEdit = when (edit) {
            is TypedShaclEdit.CreateNodeShape -> {
                if (existing != null) return failure("Shape '${edit.shapeIri.value}' already exists.", "shacl-shape-exists")
                com.entio.semantic.ShaclShapeEdit(
                    sourceId = edit.sourceId,
                    operation = ShaclShapeEditOperation.Add,
                    shape = ShaclNodeShape(
                        id = ShaclShapeId(edit.shapeIri, edit.sourceId),
                        label = edit.label.required("A shape label is required."),
                        targets = listOf(ShaclTarget.TargetClass(edit.targetClassIri)),
                        severity = edit.severity,
                        message = edit.message?.takeIf(String::isNotBlank),
                    ),
                )
            }
            is TypedShaclEdit.CreatePropertyShape -> {
                if (existing != null) return failure("Shape '${edit.shapeIri.value}' already exists.", "shacl-shape-exists")
                val property = ShaclPropertyShape(
                    id = propertyShapeId(edit.sourceId, edit.shapeIri, edit.pathIri),
                    path = ShaclPath.DirectProperty(edit.pathIri),
                    constraints = listOf(edit.constraint.toCore()),
                    severity = edit.severity,
                    message = edit.message?.takeIf(String::isNotBlank),
                )
                ShaclShapeEdit(
                    sourceId = edit.sourceId,
                    operation = ShaclShapeEditOperation.Add,
                    shape = ShaclNodeShape(
                        id = ShaclShapeId(edit.shapeIri, edit.sourceId),
                        label = edit.label.required("A shape label is required."),
                        targets = listOf(ShaclTarget.TargetClass(edit.targetClassIri)),
                        propertyShapes = listOf(property),
                    ),
                )
            }
            is TypedShaclEdit.UpdateConstraint -> return updateConstraint(edit, currentShapesGraph, existing)
            is TypedShaclEdit.UpdateShapeLabel -> return updateShapeLabel(edit, currentShapesGraph, existing)
            is TypedShaclEdit.RemoveConstraint -> return removeConstraint(edit, currentShapesGraph, existing)
            is TypedShaclEdit.DeleteShape -> return deleteShape(edit, currentShapesGraph, existing)
        }
        val translated = when (val result = authoring.translate(shapeEdit, currentShapesGraph)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        val missingRemovals = translated.changes
            .filter { it.kind == GraphChangeKind.Removal && it.triple !in currentShapesGraph.triples }
        if (missingRemovals.isNotEmpty()) {
            return failure(
                "The existing shape cannot be edited safely because its RDF structure is outside the supported typed representation.",
                "shacl-shape-not-round-trippable",
            )
        }
        return EntioResult.Success(translated)
    }

    private fun updateConstraint(
        edit: TypedShaclEdit.UpdateConstraint,
        graph: GraphState,
        existing: ShaclNodeShape?,
    ): EntioResult<ChangeSet> {
        existing ?: return failure("Shape '${edit.shapeIri.value}' was not found.", "shacl-shape-not-found")
        val propertyNode = graph.directPropertyNode(edit.shapeIri, edit.pathIri)
            ?: return failure("Property path '${edit.pathIri.value}' was not found on the shape.", "shacl-path-not-found")
        val predicate = constraintPredicate(edit.constraint.kind)
        val current = graph.triples.filter { it.subjectResource == propertyNode && it.predicate == predicate }
        if (current.isEmpty()) {
            return failure("Constraint '${edit.constraint.kind}' was not found on the property shape.", "shacl-constraint-not-found")
        }
        val replacement = com.entio.core.GraphTriple(propertyNode, predicate, edit.constraint.toRdfTerm())
        if (current.size == 1 && current.single() == replacement) {
            return failure("The replacement constraint is identical to the current value.", "shacl-constraint-unchanged")
        }
        return EntioResult.Success(
            ChangeSet(current.map { GraphChange(GraphChangeKind.Removal, it) } + GraphChange(GraphChangeKind.Addition, replacement)),
        )
    }

    private fun updateShapeLabel(
        edit: TypedShaclEdit.UpdateShapeLabel,
        graph: GraphState,
        existing: ShaclNodeShape?,
    ): EntioResult<ChangeSet> {
        existing ?: return failure("Shape '${edit.shapeIri.value}' was not found.", "shacl-shape-not-found")
        val label = edit.label.required("A shape label is required.")
        val current = graph.triples.filter { it.subjectResource == edit.shapeIri && it.predicate == RDFS_LABEL }
        val replacement = com.entio.core.GraphTriple(edit.shapeIri, RDFS_LABEL, RdfLiteral(label, Iri(XSD_STRING)))
        if (current.size == 1 && current.single() == replacement) {
            return failure("The replacement shape label is identical to the current value.", "shacl-label-unchanged")
        }
        return EntioResult.Success(
            ChangeSet(current.map { GraphChange(GraphChangeKind.Removal, it) } + GraphChange(GraphChangeKind.Addition, replacement)),
        )
    }

    private fun removeConstraint(
        edit: TypedShaclEdit.RemoveConstraint,
        graph: GraphState,
        existing: ShaclNodeShape?,
    ): EntioResult<ChangeSet> {
        existing ?: return failure("Shape '${edit.shapeIri.value}' was not found.", "shacl-shape-not-found")
        val propertyNode = graph.directPropertyNode(edit.shapeIri, edit.pathIri)
            ?: return failure("Property path '${edit.pathIri.value}' was not found on the shape.", "shacl-path-not-found")
        val predicate = constraintPredicate(edit.constraintKind)
        val current = graph.triples.filter { it.subjectResource == propertyNode && it.predicate == predicate }
        if (current.isEmpty()) {
            return failure("Constraint '${edit.constraintKind}' was not found on the property shape.", "shacl-constraint-not-found")
        }
        return EntioResult.Success(ChangeSet(current.map { GraphChange(GraphChangeKind.Removal, it) }))
    }

    private fun deleteShape(
        edit: TypedShaclEdit.DeleteShape,
        graph: GraphState,
        existing: ShaclNodeShape?,
    ): EntioResult<ChangeSet> {
        existing ?: return failure("Shape '${edit.shapeIri.value}' was not found.", "shacl-shape-not-found")
        val incoming = graph.triples.filter { it.objectTerm == edit.shapeIri && it.subjectResource != edit.shapeIri }
        if (incoming.isNotEmpty()) {
            return failure("The shape has incoming dependencies that must be removed first.", "shacl-shape-has-dependencies")
        }
        val propertyNodes = graph.triples
            .filter { it.subjectResource == edit.shapeIri && it.predicate == SH_PROPERTY }
            .mapNotNull { it.objectTerm as? com.entio.core.RdfResource }
            .toSet()
        val statements = graph.triples.filter { it.subjectResource == edit.shapeIri || it.subjectResource in propertyNodes }
        if (statements.isEmpty()) return failure("The shape has no removable statements.", "shacl-shape-empty")
        return EntioResult.Success(ChangeSet(statements.map { GraphChange(GraphChangeKind.Removal, it) }))
    }

    private fun GraphState.directPropertyNode(shapeIri: Iri, pathIri: Iri): com.entio.core.RdfResource? {
        val candidates = triples
            .filter { it.subjectResource == shapeIri && it.predicate == SH_PROPERTY }
            .mapNotNull { it.objectTerm as? com.entio.core.RdfResource }
            .filter { node -> triples.any { it.subjectResource == node && it.predicate == SH_PATH && it.objectTerm == pathIri } }
        return candidates.singleOrNull()
    }

    private fun EditableShaclConstraint.toRdfTerm(): com.entio.core.RdfTerm = when (val current = value) {
        is EditableShaclConstraintValue.IntegerValue -> RdfLiteral(current.value.toString(), Iri(XSD_INTEGER))
        is EditableShaclConstraintValue.IriValue -> current.value
        is EditableShaclConstraintValue.DecimalValue -> {
            BigDecimal(current.lexicalForm)
            RdfLiteral(current.lexicalForm, Iri(XSD_DECIMAL))
        }
        is EditableShaclConstraintValue.TextValue -> RdfLiteral(current.value.required("A SHACL pattern is required."), Iri(XSD_STRING))
    }

    private fun constraintPredicate(kind: EditableShaclConstraintKind): Iri = when (kind) {
        EditableShaclConstraintKind.MinCount -> Iri("$SH" + "minCount")
        EditableShaclConstraintKind.MaxCount -> Iri("$SH" + "maxCount")
        EditableShaclConstraintKind.Datatype -> Iri("$SH" + "datatype")
        EditableShaclConstraintKind.Class -> Iri("$SH" + "class")
        EditableShaclConstraintKind.MinInclusive -> Iri("$SH" + "minInclusive")
        EditableShaclConstraintKind.MaxInclusive -> Iri("$SH" + "maxInclusive")
        EditableShaclConstraintKind.Pattern -> Iri("$SH" + "pattern")
    }

    private fun EditableShaclConstraint.toCore(): ShaclConstraint = ShaclConstraint(kind.toCoreKind(), value.toCore(kind))

    private fun EditableShaclConstraintKind.toCoreKind(): ShaclConstraintKind = when (this) {
        EditableShaclConstraintKind.MinCount -> ShaclConstraintKind.MinCount
        EditableShaclConstraintKind.MaxCount -> ShaclConstraintKind.MaxCount
        EditableShaclConstraintKind.Datatype -> ShaclConstraintKind.Datatype
        EditableShaclConstraintKind.Class -> ShaclConstraintKind.Class
        EditableShaclConstraintKind.MinInclusive -> ShaclConstraintKind.MinInclusive
        EditableShaclConstraintKind.MaxInclusive -> ShaclConstraintKind.MaxInclusive
        EditableShaclConstraintKind.Pattern -> ShaclConstraintKind.Pattern
    }

    private fun EditableShaclConstraintValue.toCore(kind: EditableShaclConstraintKind): ShaclConstraintValue = when (this) {
        is EditableShaclConstraintValue.IntegerValue -> {
            require(kind in setOf(EditableShaclConstraintKind.MinCount, EditableShaclConstraintKind.MaxCount)) {
                "Integer values are supported only for count constraints."
            }
            require(value >= 0) { "Count constraints cannot be negative." }
            ShaclConstraintValue.IntegerValue(value)
        }
        is EditableShaclConstraintValue.IriValue -> {
            require(kind in setOf(EditableShaclConstraintKind.Datatype, EditableShaclConstraintKind.Class)) {
                "IRI values are supported only for datatype and class constraints."
            }
            ShaclConstraintValue.TermValue(value)
        }
        is EditableShaclConstraintValue.DecimalValue -> {
            require(kind in setOf(EditableShaclConstraintKind.MinInclusive, EditableShaclConstraintKind.MaxInclusive)) {
                "Decimal values are supported only for inclusive numeric constraints."
            }
            BigDecimal(lexicalForm)
            ShaclConstraintValue.TermValue(RdfLiteral(lexicalForm, Iri(XSD_DECIMAL)))
        }
        is EditableShaclConstraintValue.TextValue -> {
            require(kind == EditableShaclConstraintKind.Pattern) { "Text values are supported only for pattern constraints." }
            ShaclConstraintValue.TextValue(value.required("A SHACL pattern is required."))
        }
    }

    private fun propertyShapeId(sourceId: String, shapeIri: Iri, pathIri: Iri): ShaclShapeId {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${shapeIri.value}|${pathIri.value}".toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return ShaclShapeId(Iri("urn:entio:shacl:property:$digest"), sourceId)
    }

    private fun String.required(message: String): String = trim().takeIf(String::isNotEmpty) ?: throw IllegalArgumentException(message)

    private fun failure(message: String, code: String): EntioResult.Failure = EntioResult.Failure(
        message,
        listOf(ValidationIssue(ValidationSeverity.Error, code, message)),
    )

    private companion object {
        const val SH: String = "http://www.w3.org/ns/shacl#"
        const val XSD_DECIMAL: String = "http://www.w3.org/2001/XMLSchema#decimal"
        const val XSD_INTEGER: String = "http://www.w3.org/2001/XMLSchema#integer"
        const val XSD_STRING: String = "http://www.w3.org/2001/XMLSchema#string"
        val SH_PROPERTY: Iri = Iri("$SH" + "property")
        val SH_PATH: Iri = Iri("$SH" + "path")
        val RDFS_LABEL: Iri = Iri("http://www.w3.org/2000/01/rdf-schema#label")
    }
}
