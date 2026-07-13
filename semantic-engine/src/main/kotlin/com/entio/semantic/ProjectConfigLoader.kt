package com.entio.semantic

import com.entio.core.EntioProjectConfig
import com.entio.core.EntioResult
import com.entio.core.Iri
import com.entio.core.IriNamespaceConfig
import com.entio.core.OntologyFormat
import com.entio.core.OntologySourceReference
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import java.nio.file.Files
import java.nio.file.Path
import java.net.URI
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

public class ProjectConfigLoader {
    public fun loadConfig(projectRoot: Path): EntioResult<EntioProjectConfig> {
        val configPath = projectRoot.resolve(CONFIG_FILE_NAME)

        if (!Files.exists(configPath)) {
            return failure(
                code = "missing-entio-yaml",
                message = "Missing entio.yaml.",
            )
        }

        val loadedYaml = try {
            yamlLoader.loadFromString(Files.readString(configPath))
        } catch (exception: RuntimeException) {
            return EntioResult.Failure(
                message = "Could not parse entio.yaml.",
                issues = listOf(
                    ValidationIssue(
                        severity = ValidationSeverity.Error,
                        code = "invalid-entio-yaml",
                        message = "entio.yaml is not valid YAML.",
                        source = CONFIG_FILE_NAME,
                    ),
                ),
                cause = exception,
            )
        }

        val root = loadedYaml as? Map<*, *>
            ?: return failure(
                code = "invalid-entio-config",
                message = "entio.yaml must contain a YAML mapping.",
            )

        val name = root["name"] as? String
        if (name.isNullOrBlank()) {
            return failure(
                code = "missing-project-name",
                message = "entio.yaml must define a non-empty project name.",
            )
        }

        val iriNamespace = parseIriNamespace(root)
        when (iriNamespace) {
            is EntioResult.Failure -> return iriNamespace
            is EntioResult.Success -> Unit
        }

        val sourceEntries = root["ontologySources"] as? List<*>
            ?: return failure(
                code = "missing-ontology-sources",
                message = "entio.yaml must define ontologySources.",
            )

        if (sourceEntries.isEmpty()) {
            return failure(
                code = "empty-ontology-sources",
                message = "entio.yaml must define at least one ontology source.",
            )
        }

        val sources = mutableListOf<OntologySourceReference>()
        sourceEntries.forEachIndexed { index, entry ->
            val source = parseSource(index = index, entry = entry)
            when (source) {
                is EntioResult.Failure -> return source
                is EntioResult.Success -> sources += source.value
            }
        }

        return EntioResult.Success(
            EntioProjectConfig(
                name = name,
                ontologySources = sources,
                iriNamespace = iriNamespace.valueOrNull(),
            ),
        )
    }

    private fun parseIriNamespace(root: Map<*, *>): EntioResult<IriNamespaceConfig?> {
        if (!root.containsKey("iriNamespace")) {
            return EntioResult.Success(null)
        }

        val value = root["iriNamespace"] as? String
            ?: return failure(
                code = "invalid-iri-namespace",
                message = "iriNamespace must be a string.",
                source = "iriNamespace",
            )

        if (value.isBlank() || value.any(Char::isWhitespace)) {
            return failure(
                code = "invalid-iri-namespace",
                message = "iriNamespace must be a non-empty absolute IRI.",
                source = "iriNamespace",
            )
        }

        val uri = try {
            URI(value)
        } catch (_: IllegalArgumentException) {
            return failure(
                code = "invalid-iri-namespace",
                message = "iriNamespace must be a valid absolute IRI.",
                source = "iriNamespace",
            )
        }

        if (!uri.isAbsolute || !value.endsWith('#') && !value.endsWith('/')) {
            return failure(
                code = "invalid-iri-namespace",
                message = "iriNamespace must be absolute and end with '#' or '/'.",
                source = "iriNamespace",
            )
        }

        return EntioResult.Success(
            IriNamespaceConfig(namespace = Iri(value)),
        )
    }

    private fun parseSource(
        index: Int,
        entry: Any?,
    ): EntioResult<OntologySourceReference> {
        val sourcePath = "ontologySources[$index]"
        val source = entry as? Map<*, *>
            ?: return failure(
                code = "invalid-ontology-source",
                message = "$sourcePath must be a YAML mapping.",
                source = sourcePath,
            )

        val id = source["id"] as? String
        if (id.isNullOrBlank()) {
            return failure(
                code = "missing-ontology-source-id",
                message = "$sourcePath must define a non-empty id.",
                source = "$sourcePath.id",
            )
        }

        val path = source["path"] as? String
        if (path.isNullOrBlank()) {
            return failure(
                code = "missing-ontology-source-path",
                message = "$sourcePath must define a non-empty path.",
                source = "$sourcePath.path",
            )
        }

        val formatValue = source["format"] as? String
        if (formatValue.isNullOrBlank()) {
            return failure(
                code = "missing-ontology-source-format",
                message = "$sourcePath must define a non-empty format.",
                source = "$sourcePath.format",
            )
        }

        val format = parseFormat(formatValue)
            ?: return failure(
                code = "unsupported-ontology-format",
                message = "$sourcePath uses unsupported ontology format '$formatValue'.",
                source = "$sourcePath.format",
            )

        return EntioResult.Success(
            OntologySourceReference(
                id = id,
                path = path,
                format = format,
            ),
        )
    }

    private fun parseFormat(value: String): OntologyFormat? =
        when (value.lowercase()) {
            "turtle" -> OntologyFormat.Turtle
            else -> null
        }

    private fun failure(
        code: String,
        message: String,
        source: String = CONFIG_FILE_NAME,
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

    private fun <T> EntioResult<T>.valueOrNull(): T? =
        when (this) {
            is EntioResult.Failure -> null
            is EntioResult.Success -> value
        }

    private companion object {
        private const val CONFIG_FILE_NAME: String = "entio.yaml"

        private val yamlLoader: Load = Load(
            LoadSettings.builder()
                .setLabel(CONFIG_FILE_NAME)
                .build(),
        )
    }
}
