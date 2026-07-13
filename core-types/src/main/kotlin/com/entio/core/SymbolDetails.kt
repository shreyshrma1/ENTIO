package com.entio.core

public data class SymbolDetails(
    public val symbol: LoadedSymbol,
    public val relationships: List<SymbolRelationship> = emptyList(),
)

public data class SymbolRelationship(
    public val direction: SymbolRelationshipDirection,
    public val kind: SymbolRelationshipKind,
    public val predicate: Iri,
    public val predicateLabel: String?,
    public val value: RdfTerm,
    public val valueLabel: String?,
    public val sourceId: String,
)

public enum class SymbolRelationshipDirection {
    Outgoing,
    Incoming,
}

public enum class SymbolRelationshipKind {
    Type,
    Property,
}
