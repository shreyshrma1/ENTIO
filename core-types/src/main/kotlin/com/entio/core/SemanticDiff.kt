package com.entio.core

public data class SemanticDiff(
    public val entries: List<SemanticDiffEntry>,
)

public data class SemanticDiffEntry(
    public val kind: SemanticDiffKind,
    public val subject: Iri,
    public val predicate: Iri?,
    public val objectValue: String?,
    public val description: String,
)

public enum class SemanticDiffKind {
    Added,
    Removed,
    Changed,
}
