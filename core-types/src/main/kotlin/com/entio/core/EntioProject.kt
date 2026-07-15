package com.entio.core

import java.nio.file.Path

public data class EntioProjectConfig(
    public val name: String,
    public val ontologySources: List<OntologySourceReference>,
    public val iriNamespace: IriNamespaceConfig? = null,
    public val importMappings: Map<String, String> = emptyMap(),
)

public data class EntioProject(
    public val config: EntioProjectConfig,
    public val resolvedSources: List<ResolvedOntologySource>,
    public val ontologies: List<LoadedOntology>,
    public val symbols: List<LoadedSymbol>,
    public val graph: GraphState,
)

public data class OntologySourceReference(
    public val id: String,
    public val path: String,
    public val format: OntologyFormat,
    public val roles: Set<ShaclGraphRole> = DEFAULT_SOURCE_ROLES,
)

public data class ResolvedOntologySource(
    public val id: String,
    public val path: Path,
    public val format: OntologyFormat,
    public val roles: Set<ShaclGraphRole> = DEFAULT_SOURCE_ROLES,
)

public enum class OntologyFormat {
    Turtle,
}

private val DEFAULT_SOURCE_ROLES: Set<ShaclGraphRole> = setOf(
    ShaclGraphRole.Ontology,
    ShaclGraphRole.Data,
)
