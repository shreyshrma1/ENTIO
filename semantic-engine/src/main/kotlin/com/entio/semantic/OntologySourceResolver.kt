package com.entio.semantic

import com.entio.core.EntioProjectConfig
import com.entio.core.EntioResult
import com.entio.core.OntologySourceReference
import com.entio.core.ResolvedOntologySource
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import java.nio.file.Files
import java.nio.file.Path

public class OntologySourceResolver {
    public fun resolveSources(
        projectRoot: Path,
        config: EntioProjectConfig,
    ): EntioResult<List<ResolvedOntologySource>> {
        val duplicateId = config.ontologySources
            .groupingBy { it.id }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key

        if (duplicateId != null) {
            return failure(
                code = "duplicate-ontology-source-id",
                message = "Ontology source id '$duplicateId' is defined more than once.",
                source = duplicateId,
            )
        }

        val normalizedProjectRoot = projectRoot.toAbsolutePath().normalize()
        val resolvedSources = mutableListOf<ResolvedOntologySource>()

        for (source in config.ontologySources) {
            val resolvedSource = resolveSource(
                projectRoot = normalizedProjectRoot,
                source = source,
            )

            when (resolvedSource) {
                is EntioResult.Failure -> return resolvedSource
                is EntioResult.Success -> resolvedSources += resolvedSource.value
            }
        }

        return EntioResult.Success(resolvedSources)
    }

    private fun resolveSource(
        projectRoot: Path,
        source: OntologySourceReference,
    ): EntioResult<ResolvedOntologySource> {
        val rawPath = Path.of(source.path)

        if (rawPath.isAbsolute) {
            return failure(
                code = "absolute-ontology-source-path",
                message = "Ontology source '${source.id}' must use a relative path.",
                source = source.id,
            )
        }

        val resolvedPath = projectRoot.resolve(rawPath).normalize()

        if (!resolvedPath.startsWith(projectRoot)) {
            return failure(
                code = "unsafe-ontology-source-path",
                message = "Ontology source '${source.id}' must stay within the project root.",
                source = source.id,
            )
        }

        if (!Files.isRegularFile(resolvedPath)) {
            return failure(
                code = "missing-ontology-source-file",
                message = "Ontology source '${source.id}' does not reference an existing file.",
                source = source.id,
            )
        }

        return EntioResult.Success(
            ResolvedOntologySource(
                id = source.id,
                path = resolvedPath,
                format = source.format,
            ),
        )
    }

    private fun failure(
        code: String,
        message: String,
        source: String,
    ): EntioResult.Failure =
        EntioResult.Failure(
            message = message,
            issues = listOf(
                ValidationIssue(
                    severity = ValidationSeverity.Error,
                    code = code,
                    message = message,
                    source = source,
                ),
            ),
        )
}
