package com.entio.core

public data class LoadedOntology(
    public val source: ResolvedOntologySource,
    public val graph: GraphState,
)

public data class LoadedSymbol(
    public val iri: Iri,
    public val label: String?,
    public val kind: SymbolKind,
    public val sourceId: String,
)

public enum class SymbolKind {
    Class,
    Property,
    Individual,
    Shape,
    NamespaceTerm,
    Unknown,
}
