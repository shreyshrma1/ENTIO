package com.entio.semantic

import com.entio.core.EntioProjectConfig
import com.entio.core.EntioResult
import com.entio.core.GraphTriple
import com.entio.core.ImportClosureReport
import com.entio.core.ImportFinding
import com.entio.core.ImportFindingKind
import com.entio.core.Iri
import com.entio.core.LoadedOntology
import com.entio.core.RdfResource
import com.entio.core.ResolvedOntologySource
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity

public class ImportClosureResolver(
    private val parser: OntologyParser = OntologyParser(),
) {
    public fun resolve(
        config: EntioProjectConfig,
        sources: List<ResolvedOntologySource>,
    ): EntioResult<ImportClosureReport> {
        val sourcesById = sources.associateBy { it.id }
        val findings = mutableListOf<ImportFinding>()
        val loaded = mutableMapOf<String, LoadedOntology>()
        val visited = mutableSetOf<String>()
        val active = mutableListOf<String>()

        fun visit(source: ResolvedOntologySource) {
            if (source.id in active) return
            if (!visited.add(source.id)) return

            val parsed = when (val result = parser.parse(source)) {
                is EntioResult.Failure -> {
                    findings += ImportFinding(
                        importedIri = Iri(source.path.toUri().toString()),
                        kind = ImportFindingKind.Unresolved,
                        message = "Ontology source '${source.id}' could not be parsed for import resolution.",
                        sourceId = source.id,
                    )
                    return
                }
                is EntioResult.Success -> result.value
            }
            loaded[source.id] = parsed
            active += source.id

            parsed.graph.triples
                .filter { it.predicate == OWL_IMPORTS }
                .sortedWith(importTripleComparator)
                .forEach { triple ->
                    val importedIri = (triple.objectTerm as? RdfResource) as? Iri
                    if (importedIri == null) {
                        findings += ImportFinding(
                            importedIri = Iri(triple.objectValue),
                            kind = ImportFindingKind.Unsupported,
                            message = "owl:imports must point to an IRI resource.",
                            sourceId = source.id,
                        )
                        return@forEach
                    }

                    val targetId = config.importMappings[importedIri.value]
                    val target = when {
                        targetId != null -> sourcesById[targetId]
                        else -> sources.firstOrNull {
                            it.path.toUri().toString() == importedIri.value
                        }
                    }

                    if (targetId != null && target == null) {
                        findings += ImportFinding(
                            importedIri = importedIri,
                            kind = ImportFindingKind.Unresolved,
                            message = "Import '${importedIri.value}' maps to unknown source '$targetId'.",
                            sourceId = source.id,
                        )
                    } else if (target == null) {
                        findings += ImportFinding(
                            importedIri = importedIri,
                            kind = ImportFindingKind.Missing,
                            message = "No local ontology source is configured for import '${importedIri.value}'.",
                            sourceId = source.id,
                        )
                    } else if (target.id in active) {
                        findings += ImportFinding(
                            importedIri = importedIri,
                            kind = ImportFindingKind.Cycle,
                            message = "Import cycle detected through source '${target.id}'.",
                            sourceId = source.id,
                            relatedSourceId = target.id,
                        )
                    } else {
                        visit(target)
                    }
                }

            active.removeAt(active.lastIndex)
        }

        sources.forEach(::visit)

        val orderedFindings = findings.sortedWith(
            compareBy<ImportFinding> { it.importedIri.value }
                .thenBy { it.kind.name }
                .thenBy { it.sourceId.orEmpty() }
                .thenBy { it.relatedSourceId.orEmpty() },
        )
        val complete = orderedFindings.none {
            it.kind == ImportFindingKind.Missing ||
                it.kind == ImportFindingKind.Unresolved ||
                it.kind == ImportFindingKind.Unsupported
        }

        return EntioResult.Success(
            ImportClosureReport(
                sourceIds = loaded.keys.sorted(),
                findings = orderedFindings,
                complete = complete,
            ),
        )
    }

    private companion object {
        private val OWL_IMPORTS = Iri("http://www.w3.org/2002/07/owl#imports")
        private val importTripleComparator: Comparator<GraphTriple> =
            compareBy<GraphTriple> { it.objectValue }
                .thenBy { it.subjectResource.value }
    }
}
