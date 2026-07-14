package com.entio.cli

import com.entio.core.EntioResult
import com.entio.core.SemanticDescriptorKind
import com.entio.core.SemanticSearchQuery
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import com.entio.semantic.ProjectLoader
import com.entio.semantic.SemanticDescriptionService
import java.nio.file.Path
import java.util.concurrent.Callable
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.Spec

@Command(
    name = "descriptor",
    mixinStandardHelpOptions = true,
    description = ["Return a machine-readable semantic descriptor."],
)
public class DescriptorCommand(
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val descriptionService: SemanticDescriptionService = SemanticDescriptionService(),
) : Callable<Int> {
    @Spec
    private lateinit var spec: CommandSpec

    @Parameters(index = "0", paramLabel = "PROJECT_ROOT", description = ["Path to an Entio project root."])
    private lateinit var projectRoot: String

    @Parameters(index = "1", paramLabel = "ENTITY_IRI", description = ["IRI of the entity to describe."])
    private lateinit var entityIri: String

    @Option(names = ["--language"], description = ["Preferred language tag for labels."])
    private var preferredLanguage: String? = null

    override fun call(): Int = when (val projectResult = projectLoader.loadProject(Path.of(projectRoot))) {
        is EntioResult.Failure -> failure("descriptor", projectResult.message, projectResult.issues)
        is EntioResult.Success -> when (val result = descriptionService.describe(projectResult.value, com.entio.core.Iri(entityIri), preferredLanguage)) {
            is EntioResult.Failure -> failure("descriptor", result.message, result.issues)
            is EntioResult.Success -> {
                spec.commandLine().out.println(
                    jsonObject(
                        "command" to "descriptor",
                        "ok" to true,
                        "descriptor" to semanticDescriptorJson(result.value),
                    ).encoded,
                )
                EXIT_OK
            }
        }
    }

    private fun failure(command: String, message: String, issues: List<ValidationIssue>): Int {
        spec.commandLine().out.println(
            jsonObject(
                "command" to command,
                "ok" to false,
                "error" to jsonObject(
                    "message" to message,
                    "issues" to jsonArray(issues.map(::validationIssueJson)),
                ),
            ).encoded,
        )
        return EXIT_FAILED
    }

    private companion object {
        private const val EXIT_OK: Int = 0
        private const val EXIT_FAILED: Int = 1
    }
}

@Command(
    name = "search",
    mixinStandardHelpOptions = true,
    description = ["Search semantic descriptors using deterministic label and IRI matching."],
)
public class SemanticSearchCommand(
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val descriptionService: SemanticDescriptionService = SemanticDescriptionService(),
) : Callable<Int> {
    @Spec
    private lateinit var spec: CommandSpec

    @Parameters(index = "0", paramLabel = "PROJECT_ROOT", description = ["Path to an Entio project root."])
    private lateinit var projectRoot: String

    @Parameters(index = "1", paramLabel = "QUERY", description = ["Text to match against labels, annotations, or IRIs."])
    private lateinit var query: String

    @Option(names = ["--language"], description = ["Preferred language tag for labels."])
    private var preferredLanguage: String? = null

    @Option(names = ["--kind"], description = ["Filter by descriptor kind."])
    private var kind: String? = null

    @Option(names = ["--source", "--source-id"], description = ["Filter by ontology source id."])
    private var sourceId: String? = null

    override fun call(): Int {
        val descriptorKind = if (kind == null) {
            null
        } else {
            parseKind(kind) ?: return failure(
                message = "Unknown descriptor kind '$kind'.",
                issues = listOf(
                    ValidationIssue(
                        severity = ValidationSeverity.Error,
                        code = "invalid-descriptor-kind",
                        message = "Unknown descriptor kind '$kind'.",
                        source = "--kind",
                    ),
                ),
            )
        }

        return when (val projectResult = projectLoader.loadProject(Path.of(projectRoot))) {
            is EntioResult.Failure -> failure(projectResult.message, projectResult.issues)
            is EntioResult.Success -> {
                val results = descriptionService.search(
                    projectResult.value,
                    SemanticSearchQuery(
                        text = query,
                        preferredLanguage = preferredLanguage,
                        kind = descriptorKind,
                        sourceId = sourceId,
                    ),
                )
                spec.commandLine().out.println(
                    jsonObject(
                        "command" to "search",
                        "ok" to true,
                        "query" to query,
                        "ambiguous" to (results.size > 1),
                        "results" to jsonArray(results.map(::semanticSearchResultJson)),
                    ).encoded,
                )
                EXIT_OK
            }
        }
    }

    private fun parseKind(value: String?): SemanticDescriptorKind? {
        if (value == null) return null
        return SemanticDescriptorKind.entries.firstOrNull { kindValue ->
            kindValue.name.equals(value, ignoreCase = true)
        }
    }

    private fun failure(message: String, issues: List<ValidationIssue>): Int {
        spec.commandLine().out.println(
            jsonObject(
                "command" to "search",
                "ok" to false,
                "error" to jsonObject(
                    "message" to message,
                    "issues" to jsonArray(issues.map(::validationIssueJson)),
                ),
            ).encoded,
        )
        return EXIT_FAILED
    }

    private companion object {
        private const val EXIT_OK: Int = 0
        private const val EXIT_FAILED: Int = 1
    }
}
