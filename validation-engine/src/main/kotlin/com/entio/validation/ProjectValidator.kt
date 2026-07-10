package com.entio.validation

import com.entio.core.EntioProjectConfig
import com.entio.core.EntioResult
import com.entio.core.LoadedOntology
import com.entio.core.ResolvedOntologySource
import com.entio.core.ValidationIssue
import com.entio.core.ValidationReport
import com.entio.core.ValidationSeverity
import com.entio.core.ValidationStatus
import com.entio.semantic.OntologyParser
import com.entio.semantic.OntologySourceResolver
import com.entio.semantic.ProjectConfigLoader
import com.entio.semantic.SymbolExtractor
import java.nio.file.Files
import java.nio.file.Path

public class ProjectValidator(
    private val configLoader: ProjectConfigLoader = ProjectConfigLoader(),
    private val sourceResolver: OntologySourceResolver = OntologySourceResolver(),
    private val ontologyParser: OntologyParser = OntologyParser(),
    private val symbolExtractor: SymbolExtractor = SymbolExtractor(),
    private val issueSorter: ValidationIssueSorter = ValidationIssueSorter(),
) {
    public fun validateProject(projectRoot: Path): ValidationReport {
        if (!Files.exists(projectRoot)) {
            return invalidReport(
                ValidationIssue(
                    severity = ValidationSeverity.Error,
                    code = "missing-project-root",
                    message = "Project root does not exist.",
                    source = projectRoot.toString(),
                ),
            )
        }

        if (!Files.isDirectory(projectRoot)) {
            return invalidReport(
                ValidationIssue(
                    severity = ValidationSeverity.Error,
                    code = "project-root-not-directory",
                    message = "Project root must be a directory.",
                    source = projectRoot.toString(),
                ),
            )
        }

        val config = when (val result = configLoader.loadConfig(projectRoot)) {
            is EntioResult.Failure -> return report(result.issues)
            is EntioResult.Success -> result.value
        }

        val resolvedSources = when (val result = sourceResolver.resolveSources(projectRoot, config)) {
            is EntioResult.Failure -> return report(result.issues)
            is EntioResult.Success -> result.value
        }

        val parseResults = parseOntologies(resolvedSources)
        val parseIssues = parseResults
            .mapNotNull { result ->
                when (result) {
                    is EntioResult.Failure -> result.issues
                    is EntioResult.Success -> null
                }
            }
            .flatten()

        if (parseIssues.isNotEmpty()) {
            return report(parseIssues)
        }

        val symbolIssues = extractSymbols(config, parseResults.successValues())

        return report(symbolIssues)
    }

    private fun parseOntologies(
        sources: List<ResolvedOntologySource>,
    ): List<EntioResult<LoadedOntology>> =
        sources.map { source -> ontologyParser.parse(source) }

    private fun List<EntioResult<LoadedOntology>>.successValues(): List<LoadedOntology> =
        mapNotNull { result ->
            when (result) {
                is EntioResult.Failure -> null
                is EntioResult.Success -> result.value
            }
        }

    private fun extractSymbols(
        config: EntioProjectConfig,
        ontologies: List<LoadedOntology>,
    ): List<ValidationIssue> =
        try {
            ontologies.forEach { ontology -> symbolExtractor.extractSymbols(ontology) }
            emptyList()
        } catch (exception: RuntimeException) {
            listOf(
                ValidationIssue(
                    severity = ValidationSeverity.Error,
                    code = "symbol-extraction-failed",
                    message = "Loaded symbols could not be extracted for project '${config.name}'.",
                    source = config.name,
                ),
            )
        }

    private fun invalidReport(issue: ValidationIssue): ValidationReport =
        report(listOf(issue))

    private fun report(issues: List<ValidationIssue>): ValidationReport {
        val sortedIssues = issueSorter.sortIssues(issues)
        val status = if (sortedIssues.any { it.severity == ValidationSeverity.Error }) {
            ValidationStatus.Invalid
        } else {
            ValidationStatus.Valid
        }

        return ValidationReport(
            status = status,
            issues = sortedIssues,
        )
    }
}
