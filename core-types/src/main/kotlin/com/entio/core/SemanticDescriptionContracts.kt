package com.entio.core

/** A human-readable RDF literal together with its language and datatype metadata. */
public data class LocalizedText(
    public val lexicalForm: String,
    public val languageTag: String? = null,
    public val datatypeIri: Iri? = null,
) {
    public val stableKey: String
        get() = listOf(lexicalForm, languageTag.orEmpty(), datatypeIri?.value.orEmpty())
            .joinToString(separator = "\u0000")

    public fun asRdfLiteral(): RdfLiteral = RdfLiteral(
        lexicalForm = lexicalForm,
        datatypeIri = datatypeIri,
        languageTag = languageTag,
    )
}

/** An annotation value that retains whether the RDF object is a resource or a literal. */
public sealed interface AnnotationValue {
    public val term: RdfTerm

    public data class Resource(
        public val resource: RdfResource,
    ) : AnnotationValue {
        public override val term: RdfTerm = resource
    }

    public data class Literal(
        public val literal: RdfLiteral,
    ) : AnnotationValue {
        public override val term: RdfTerm = literal
    }

    public companion object {
        public fun fromTerm(term: RdfTerm): AnnotationValue = when (term) {
            is RdfResource -> Resource(term)
            is RdfLiteral -> Literal(term)
        }
    }
}

/** One explicit annotation statement associated with an RDF subject. */
public data class AnnotationStatement(
    public val subject: RdfResource,
    public val property: Iri,
    public val value: AnnotationValue,
    public val sourceId: String,
) {
    public val stableKey: String
        get() = listOf(
            sourceId,
            subject.stableKey(),
            property.value,
            value.term.stableKey(),
        ).joinToString(separator = "\u0000")
}

public enum class SemanticDescriptorKind {
    Class,
    ObjectProperty,
    DatatypeProperty,
    AnnotationProperty,
    Individual,
}

public enum class LocalityStatus {
    Local,
    Imported,
    Unknown,
}

public enum class PreferredLabelSource {
    SkosPreferredLabel,
    RdfsLabel,
    AlternateLabel,
    IriLocalName,
    None,
}

/** Common descriptor data shared by every supported ontology entity kind. */
public data class SemanticDescriptorCommon(
    public val entity: RdfResource,
    public val kind: SemanticDescriptorKind,
    public val sourceId: String,
    public val sourceOntologyId: String? = null,
    public val locality: LocalityStatus = LocalityStatus.Unknown,
    public val preferredLabel: LocalizedText? = null,
    public val preferredLabelSource: PreferredLabelSource = PreferredLabelSource.None,
    public val ambiguousPreferredLabelLanguages: List<String> = emptyList(),
    public val alternateLabels: List<LocalizedText> = emptyList(),
    public val definitions: List<LocalizedText> = emptyList(),
    public val annotations: List<AnnotationStatement> = emptyList(),
)

/** An explicitly asserted object-property relationship. */
public data class ObjectPropertyAssertion(
    public val subject: RdfResource,
    public val property: Iri,
    public val value: RdfResource,
    public val sourceId: String,
)

/** An explicitly asserted datatype-property relationship. */
public data class DatatypePropertyAssertion(
    public val subject: RdfResource,
    public val property: Iri,
    public val value: RdfLiteral,
    public val sourceId: String,
)

/** An immutable descriptor assembled by the semantic engine. */
public sealed interface OntologyEntityDescriptor {
    public val common: SemanticDescriptorCommon

    public data class Class(
        override val common: SemanticDescriptorCommon,
        public val directSuperclasses: List<Iri> = emptyList(),
        public val directSubclasses: List<Iri> = emptyList(),
        public val directlyTypedIndividuals: List<RdfResource> = emptyList(),
    ) : OntologyEntityDescriptor

    public data class ObjectProperty(
        override val common: SemanticDescriptorCommon,
        public val domains: List<Iri> = emptyList(),
        public val ranges: List<Iri> = emptyList(),
        public val directAssertions: List<ObjectPropertyAssertion> = emptyList(),
    ) : OntologyEntityDescriptor

    public data class DatatypeProperty(
        override val common: SemanticDescriptorCommon,
        public val domains: List<Iri> = emptyList(),
        public val datatypeRanges: List<Iri> = emptyList(),
        public val directAssertions: List<DatatypePropertyAssertion> = emptyList(),
    ) : OntologyEntityDescriptor

    public data class AnnotationProperty(
        override val common: SemanticDescriptorCommon,
        public val statementsUsingProperty: List<AnnotationStatement> = emptyList(),
    ) : OntologyEntityDescriptor

    public data class Individual(
        override val common: SemanticDescriptorCommon,
        public val assertedTypes: List<Iri> = emptyList(),
        public val objectPropertyAssertions: List<ObjectPropertyAssertion> = emptyList(),
        public val datatypePropertyAssertions: List<DatatypePropertyAssertion> = emptyList(),
    ) : OntologyEntityDescriptor
}

public enum class SemanticMatchReason {
    PreferredLabel,
    AlternateLabel,
    Iri,
    Annotation,
}

/** Filters accepted by the semantic search boundary. */
public data class SemanticSearchQuery(
    public val text: String,
    public val preferredLanguage: String? = null,
    public val kind: SemanticDescriptorKind? = null,
    public val sourceId: String? = null,
)

/** A deterministic search hit with an explicit explanation for the match. */
public data class SemanticSearchResult(
    public val descriptor: OntologyEntityDescriptor,
    public val reason: SemanticMatchReason,
    public val rank: Int,
) {
    public val stableKey: String
        get() = listOf(rank.toString(), descriptor.common.sourceId, descriptor.common.entity.stableKey())
            .joinToString(separator = "\u0000")
}

public enum class SemanticEditKind {
    CreateAnnotationProperty,
    AddDefinition,
    ReplaceDefinition,
    RemoveDefinition,
    AddAlternateLabel,
    ReplaceAlternateLabel,
    RemoveAlternateLabel,
    AddAnnotation,
    RemoveAnnotation,
}

/** Base contract for typed semantic edits before translation into graph changes. */
public sealed interface SemanticEditRequest {
    public val kind: SemanticEditKind
    public val sourceId: String

    public data class CreateAnnotationProperty(
        public val propertyIri: Iri,
        override val sourceId: String,
        public val label: RdfLiteral? = null,
        public val definition: RdfLiteral? = null,
    ) : SemanticEditRequest {
        public override val kind: SemanticEditKind = SemanticEditKind.CreateAnnotationProperty
    }

    public data class AddDefinition(
        public val target: RdfResource,
        public val value: RdfLiteral,
        override val sourceId: String,
    ) : SemanticEditRequest {
        public override val kind: SemanticEditKind = SemanticEditKind.AddDefinition
    }

    public data class ReplaceDefinition(
        public val target: RdfResource,
        public val existing: RdfLiteral,
        public val replacement: RdfLiteral,
        override val sourceId: String,
    ) : SemanticEditRequest {
        public override val kind: SemanticEditKind = SemanticEditKind.ReplaceDefinition
    }

    public data class RemoveDefinition(
        public val target: RdfResource,
        public val value: RdfLiteral,
        override val sourceId: String,
    ) : SemanticEditRequest {
        public override val kind: SemanticEditKind = SemanticEditKind.RemoveDefinition
    }

    public data class AddAlternateLabel(
        public val target: RdfResource,
        public val value: RdfLiteral,
        override val sourceId: String,
    ) : SemanticEditRequest {
        public override val kind: SemanticEditKind = SemanticEditKind.AddAlternateLabel
    }

    public data class ReplaceAlternateLabel(
        public val target: RdfResource,
        public val existing: RdfLiteral,
        public val replacement: RdfLiteral,
        override val sourceId: String,
    ) : SemanticEditRequest {
        public override val kind: SemanticEditKind = SemanticEditKind.ReplaceAlternateLabel
    }

    public data class RemoveAlternateLabel(
        public val target: RdfResource,
        public val value: RdfLiteral,
        override val sourceId: String,
    ) : SemanticEditRequest {
        public override val kind: SemanticEditKind = SemanticEditKind.RemoveAlternateLabel
    }

    public data class AddAnnotation(
        public val target: RdfResource,
        public val property: Iri,
        public val value: AnnotationValue,
        override val sourceId: String,
    ) : SemanticEditRequest {
        public override val kind: SemanticEditKind = SemanticEditKind.AddAnnotation
    }

    public data class RemoveAnnotation(
        public val target: RdfResource,
        public val property: Iri,
        public val value: AnnotationValue,
        override val sourceId: String,
    ) : SemanticEditRequest {
        public override val kind: SemanticEditKind = SemanticEditKind.RemoveAnnotation
    }
}

private fun RdfResource.stableKey(): String = when (this) {
    is Iri -> "iri:$value"
    is BlankNodeResource -> "blank:$id"
}

private fun RdfTerm.stableKey(): String = when (this) {
    is RdfResource -> when (this) {
        is Iri -> "iri:$value"
        is BlankNodeResource -> "blank:$id"
    }
    is RdfLiteral -> "literal:$lexicalForm|${datatypeIri?.value.orEmpty()}|${languageTag.orEmpty()}"
}
