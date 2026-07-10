package com.entio.core

public data class GraphState(
    public val triples: Set<GraphTriple> = emptySet(),
)

public data class GraphTriple(
    /**
     * Temporary Phase 1 compatibility view for consumers that still assume IRI subjects.
     * Prefer `subjectResource` for RDF-term-aware code.
     */
    public val subject: Iri,
    public val predicate: Iri,
    /**
     * Temporary Phase 1 compatibility view for consumers that still format objects as strings.
     * Prefer `objectTerm` for RDF-term-aware code.
     */
    public val objectValue: String,
    public val subjectResource: RdfResource = subject,
    public val objectTerm: RdfTerm = RdfLiteral(lexicalForm = objectValue),
) {
    public constructor(
        subject: RdfResource,
        predicate: Iri,
        objectTerm: RdfTerm,
    ) : this(
        subject = subject.toCompatibilityIri(),
        predicate = predicate,
        objectValue = objectTerm.toCompatibilityValue(),
        subjectResource = subject,
        objectTerm = objectTerm,
    )
}

private fun RdfResource.toCompatibilityIri(): Iri =
    when (this) {
        is Iri -> this
        is BlankNodeResource -> Iri(value)
    }

private fun RdfTerm.toCompatibilityValue(): String =
    when (this) {
        is RdfLiteral -> lexicalForm
        is RdfResource -> value
    }
