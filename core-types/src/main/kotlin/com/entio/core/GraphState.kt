package com.entio.core

public data class GraphState(
    public val triples: Set<GraphTriple> = emptySet(),
)

public data class GraphTriple(
    public val subject: Iri,
    public val predicate: Iri,
    public val objectValue: String,
)
