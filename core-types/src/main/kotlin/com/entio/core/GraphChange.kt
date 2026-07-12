package com.entio.core

public data class GraphChange(
    public val kind: GraphChangeKind,
    public val triple: GraphTriple,
)

public enum class GraphChangeKind {
    Addition,
    Removal,
}

public data class ChangeSet(
    public val changes: List<GraphChange>,
) {
    init {
        require(changes.isNotEmpty()) { "ChangeSet must contain at least one graph change." }
    }

    public val additions: List<GraphChange>
        get() = changes.filter { it.kind == GraphChangeKind.Addition }

    public val removals: List<GraphChange>
        get() = changes.filter { it.kind == GraphChangeKind.Removal }
}
