package com.entio.cli

import com.entio.core.EntioResult
import com.entio.core.GraphState
import com.entio.core.LoadedOntology
import com.entio.core.LoadedSymbol
import com.entio.core.ResolvedOntologySource
import com.entio.core.ValidationIssue
import com.entio.semantic.OntologyParser
import com.entio.semantic.OntologySourceResolver
import com.entio.semantic.ProjectConfigLoader
import com.entio.semantic.SymbolExtractor
import java.nio.file.Path

public class CliProjectReader(
    private val configLoader: ProjectConfigLoader = ProjectConfigLoader(),
    private val sourceResolver: OntologySourceResolver = OntologySourceResolver(),
    private val ontologyParser: OntologyParser = OntologyParser(),
    private val symbolExtractor: SymbolExtractor = SymbolExtractor(),
) {
    public fun loadSymbols(projectRoot: Path): EntioResult<List<LoadedSymbol>> {
        val ontologies = when (val result = loadOntologies(projectRoot)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }

        return EntioResult.Success(
            ontologies
                .flatMap { ontology -> symbolExtractor.extractSymbols(ontology) }
                .sortedWith(symbolComparator),
        )
    }

    public fun loadGraph(projectRoot: Path): EntioResult<GraphState> {
        val ontologies = when (val result = loadOntologies(projectRoot)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }

        return EntioResult.Success(
            GraphState(
                triples = ontologies
                    .flatMap { ontology -> ontology.graph.triples }
                    .toSet(),
            ),
        )
    }

    private fun loadOntologies(projectRoot: Path): EntioResult<List<LoadedOntology>> {
        val config = when (val result = configLoader.loadConfig(projectRoot)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }

        val resolvedSources = when (val result = sourceResolver.resolveSources(projectRoot, config)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }

        return parseOntologies(resolvedSources)
    }

    private fun parseOntologies(sources: List<ResolvedOntologySource>): EntioResult<List<LoadedOntology>> {
        val ontologies = mutableListOf<LoadedOntology>()
        val issues = mutableListOf<ValidationIssue>()

        for (source in sources) {
            when (val result = ontologyParser.parse(source)) {
                is EntioResult.Failure -> issues += result.issues
                is EntioResult.Success -> ontologies += result.value
            }
        }

        if (issues.isNotEmpty()) {
            return EntioResult.Failure(
                message = "One or more ontology sources could not be parsed.",
                issues = issues.sortedWith(issueComparator),
            )
        }

        return EntioResult.Success(ontologies)
    }

    private companion object {
        private val symbolComparator: Comparator<LoadedSymbol> =
            compareBy<LoadedSymbol> { it.iri.value }
                .thenBy { it.sourceId }
                .thenBy { it.kind.name }
                .thenBy { it.label.orEmpty() }

        private val issueComparator: Comparator<ValidationIssue> =
            compareBy<ValidationIssue> { it.severity.ordinal }
                .thenBy { it.code }
                .thenBy { it.source.orEmpty() }
                .thenBy { it.message }
    }
}
