package com.entio.core

public sealed interface TypedOntologyEdit

public data class CreateClassEdit(
    public val classIri: Iri,
    public val label: RdfLiteral? = null,
) : TypedOntologyEdit

public data class SetEntityLabelEdit(
    public val entity: RdfResource,
    public val label: RdfLiteral,
) : TypedOntologyEdit

public data class AddSuperclassEdit(
    public val classIri: Iri,
    public val superclassIri: Iri,
) : TypedOntologyEdit

public data class RemoveSuperclassEdit(
    public val classIri: Iri,
    public val superclassIri: Iri,
) : TypedOntologyEdit

public data class CreateObjectPropertyEdit(
    public val propertyIri: Iri,
    public val label: RdfLiteral? = null,
) : TypedOntologyEdit

public data class CreateDatatypePropertyEdit(
    public val propertyIri: Iri,
    public val label: RdfLiteral? = null,
) : TypedOntologyEdit

public data class SetPropertyDomainEdit(
    public val propertyIri: Iri,
    public val domainClassIri: Iri,
) : TypedOntologyEdit

public data class SetPropertyRangeEdit(
    public val propertyIri: Iri,
    public val rangeIri: Iri,
) : TypedOntologyEdit

public data class CreateIndividualEdit(
    public val individualIri: Iri,
    public val classIri: Iri? = null,
) : TypedOntologyEdit

public data class AssignTypeEdit(
    public val resource: RdfResource,
    public val typeIri: Iri,
) : TypedOntologyEdit

public data class AddObjectPropertyAssertionEdit(
    public val subject: RdfResource,
    public val propertyIri: Iri,
    public val objectResource: RdfResource,
) : TypedOntologyEdit

public data class AddDatatypePropertyAssertionEdit(
    public val subject: RdfResource,
    public val propertyIri: Iri,
    public val value: RdfLiteral,
) : TypedOntologyEdit
