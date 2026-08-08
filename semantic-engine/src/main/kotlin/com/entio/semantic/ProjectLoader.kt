package com.entio.semantic

import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.GraphState
import com.entio.core.Iri
import com.entio.core.LoadedOntology
import com.entio.core.LoadedSymbol
import com.entio.core.OntologyFormat
import com.entio.core.ResolvedOntologySource
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import java.nio.file.Files
import java.nio.file.Path

public class ProjectLoader(
    private val configLoader: ProjectConfigLoader = ProjectConfigLoader(),
    private val sourceResolver: OntologySourceResolver = OntologySourceResolver(),
    private val ontologyParser: OntologyParser = OntologyParser(),
    private val extractSymbols: (LoadedOntology) -> List<LoadedSymbol> = SymbolExtractor()::extractSymbols,
    private val domainProfiles: DomainProfileRepository = DomainProfileRepository(),
    private val domainTransactions: DomainFileTransactionManager = DomainFileTransactionManager(domainProfiles),
) {
    public fun loadProject(projectRoot: Path): EntioResult<EntioProject> {
        val config = when (val result = configLoader.loadConfig(projectRoot)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }

        when (val recovery = domainTransactions.recover(projectRoot)) {
            is EntioResult.Failure -> return recovery
            is EntioResult.Success -> Unit
        }

        val domainProfile = when (val result = domainProfiles.read(projectRoot)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }

        val configuredSources = when (val result = sourceResolver.resolveSources(projectRoot, config)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        val activeDomain = domainProfile.activeDomainOntology
        if (activeDomain != null && configuredSources.any {
                it.id == activeDomain.profile.managedSourceId ||
                    Files.isSameFile(it.path, activeDomain.paths.managedSource)
            }
        ) {
            return EntioResult.Failure(
                message = "The managed domain source must not be duplicated in entio.yaml.",
                issues = listOf(
                    ValidationIssue(
                        ValidationSeverity.Error,
                        "duplicate-domain-managed-source",
                        "The managed domain source must be derived from the active profile only.",
                        activeDomain.profile.managedSourceId,
                    ),
                ),
            )
        }
        val resolvedSources = configuredSources + listOfNotNull(
            activeDomain?.let {
                ResolvedOntologySource(
                    id = it.profile.managedSourceId,
                    path = it.paths.managedSource,
                    format = OntologyFormat.Turtle,
                )
            },
        )

        val ontologies = when (val result = parseOntologies(resolvedSources)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        if (activeDomain != null) {
            val managedOntology = ontologies.single { it.source.id == activeDomain.profile.managedSourceId }
            val declaresOntology = managedOntology.graph.triples.any { triple ->
                triple.predicate.value == RDF_TYPE &&
                    triple.objectTerm == Iri(OWL_ONTOLOGY)
            }
            if (declaresOntology) {
                return EntioResult.Failure(
                    message = "The managed domain source must not declare a separate ontology.",
                    issues = listOf(
                        ValidationIssue(
                            ValidationSeverity.Error,
                            "unexpected-domain-ontology-declaration",
                            "The managed reuse source is a project-owned statement container, not an imported ontology.",
                            activeDomain.profile.managedSourceId,
                        ),
                    ),
                )
            }
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
                activeDomainOntology = activeDomain,
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
        private const val RDF_TYPE: String = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
        private const val OWL_ONTOLOGY: String = "http://www.w3.org/2002/07/owl#Ontology"
        private val symbolComparator: Comparator<LoadedSymbol> =
            compareBy<LoadedSymbol> { it.iri.value }
                .thenBy { it.sourceId }
                .thenBy { it.kind.name }
                .thenBy { it.label.orEmpty() }
    }
}
