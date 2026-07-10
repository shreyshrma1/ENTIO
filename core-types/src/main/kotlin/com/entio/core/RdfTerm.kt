package com.entio.core

/**
 * Entio-owned representation of values that may appear in RDF node positions.
 */
public sealed interface RdfTerm

/**
 * RDF terms that identify graph resources.
 *
 * Literals must not implement this interface.
 */
public sealed interface RdfResource : RdfTerm {
    public val value: String
}

/**
 * RDF blank-node resource.
 *
 * The identifier is parser-local and should not be treated as a durable
 * business identity.
 */
public data class BlankNodeResource(
    public val id: String,
) : RdfResource {
    public override val value: String = "_:$id"
}

/**
 * RDF literal value with optional datatype and language metadata.
 */
public data class RdfLiteral(
    public val lexicalForm: String,
    public val datatypeIri: Iri? = null,
    public val languageTag: String? = null,
) : RdfTerm
