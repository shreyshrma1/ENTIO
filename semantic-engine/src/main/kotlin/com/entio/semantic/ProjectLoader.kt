package com.entio.semantic

import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.GraphState
import com.entio.core.LoadedOntology
import com.entio.core.LoadedSymbol
import com.entio.core.ResolvedOntologySource
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import java.nio.file.Path

public class ProjectLoader(
    private val configLoader: ProjectConfigLoader = ProjectConfigLoader(),
    private val sourceResolver: OntologySourceResolver = OntologySourceResolver(),
    private val ontologyParser: OntologyParser = OntologyParser(),
    private val extractSymbols: (LoadedOntology) -> List<LoadedSymbol> = SymbolExtractor()::extractSymbols,
) {
    public fun loadProject(projectRoot: Path): EntioResult<EntioProject> {
        val config = when (val result = configLoader.loadConfig(projectRoot)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }

        val resolvedSources = when (val result = sourceResolver.resolveSources(projectRoot, config)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }

        val ontologies = when (val result = parseOntologies(resolvedSources)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }

        val symbols = when (val result = extractProjectSymbols(ontologies)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }

        return EntioResult.Success(
            EntioProject(
                config = config,
                resolvedSources = resolvedSources,
                ontologies = ontologies,
                symbols = symbols,
                graph = GraphState(
                    triples = ontologies
                        .flatMap { ontology -> ontology.graph.triples }
                        .toSet(),
                ),
            ),
        )
    }

    private fun parseOntologies(
        sources: List<ResolvedOntologySource>,
    ): EntioResult<List<LoadedOntology>> {
        val ontologies = mutableListOf<LoadedOntology>()
        val issues = mutableListOf<ValidationIssue>()

        sources.forEach { source ->
            when (val result = ontologyParser.parse(source)) {
                is EntioResult.Failure -> issues += result.issues
                is EntioResult.Success -> ontologies += result.value
            }
        }

        if (issues.isNotEmpty()) {
            return EntioResult.Failure(
                message = "One or more ontology sources could not be parsed.",
                issues = issues,
            )
        }

        return EntioResult.Success(ontologies)
    }

    private fun extractProjectSymbols(
        ontologies: List<LoadedOntology>,
    ): EntioResult<List<LoadedSymbol>> {
        val symbols = mutableListOf<LoadedSymbol>()

        ontologies.forEach { ontology ->
            val ontologySymbols = try {
                extractSymbols(ontology)
            } catch (exception: RuntimeException) {
                return EntioResult.Failure(
                    message = "Loaded symbols could not be extracted for ontology source '${ontology.source.id}'.",
                    issues = listOf(
                        ValidationIssue(
                            severity = ValidationSeverity.Error,
                            code = "symbol-extraction-failed",
                            message = "Loaded symbols could not be extracted for ontology source '${ontology.source.id}'.",
                            source = ontology.source.id,
                        ),
                    ),
                    cause = exception,
                )
            }

            symbols += ontologySymbols
        }

        return EntioResult.Success(
            symbols.sortedWith(symbolComparator),
        )
    }

    private companion object {
        private val symbolComparator: Comparator<LoadedSymbol> =
            compareBy<LoadedSymbol> { it.iri.value }
                .thenBy { it.sourceId }
                .thenBy { it.kind.name }
                .thenBy { it.label.orEmpty() }
    }
}
