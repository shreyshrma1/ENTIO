package com.entio.core

/** Strict SHACL operations supported by the ordinary reviewed proposal workflow. */
public sealed interface TypedShaclEdit {
    public val sourceId: String
    public val shapeIri: Iri

    public data class CreateNodeShape(
        override val sourceId: String,
        override val shapeIri: Iri,
        val label: String,
        val targetClassIri: Iri,
        val severity: ShaclSeverity = ShaclSeverity.Violation,
        val message: String? = null,
    ) : TypedShaclEdit

    public data class CreatePropertyShape(
        override val sourceId: String,
        override val shapeIri: Iri,
        val label: String,
        val targetClassIri: Iri,
        val pathIri: Iri,
        val constraint: EditableShaclConstraint,
        val severity: ShaclSeverity = ShaclSeverity.Violation,
        val message: String? = null,
    ) : TypedShaclEdit

    public data class UpdateConstraint(
        override val sourceId: String,
        override val shapeIri: Iri,
        val pathIri: Iri,
        val constraint: EditableShaclConstraint,
    ) : TypedShaclEdit

    public data class RemoveConstraint(
        override val sourceId: String,
        override val shapeIri: Iri,
        val pathIri: Iri,
        val constraintKind: EditableShaclConstraintKind,
    ) : TypedShaclEdit

    public data class DeleteShape(
        override val sourceId: String,
        override val shapeIri: Iri,
    ) : TypedShaclEdit
}

public enum class EditableShaclConstraintKind {
    MinCount,
    MaxCount,
    Datatype,
    Class,
    MinInclusive,
    MaxInclusive,
    Pattern,
}

public sealed interface EditableShaclConstraintValue {
    public data class IntegerValue(val value: Int) : EditableShaclConstraintValue
    public data class IriValue(val value: Iri) : EditableShaclConstraintValue
    public data class DecimalValue(val lexicalForm: String) : EditableShaclConstraintValue
    public data class TextValue(val value: String) : EditableShaclConstraintValue
}

public data class EditableShaclConstraint(
    val kind: EditableShaclConstraintKind,
    val value: EditableShaclConstraintValue,
)
