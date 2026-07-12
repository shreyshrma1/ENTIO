package com.entio.cli

import com.entio.core.EntioResult
import com.entio.semantic.ProjectLoader
import java.nio.file.Path
import java.util.concurrent.Callable
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Parameters
import picocli.CommandLine.Spec

@Command(
    name = "project-summary",
    mixinStandardHelpOptions = true,
    description = ["Return a machine-readable Entio project summary."],
)
public class ProjectSummaryCommand(
    private val projectLoader: ProjectLoader = ProjectLoader(),
) : Callable<Int> {
    @Spec
    private lateinit var spec: CommandSpec

    @Parameters(index = "0", paramLabel = "PROJECT_ROOT", description = ["Path to an Entio project root."])
    private lateinit var projectRoot: String

    override fun call(): Int =
        when (val result = projectLoader.loadProject(Path.of(projectRoot))) {
            is EntioResult.Failure -> {
                spec.commandLine().out.println(
                    jsonObject(
                        "command" to "project-summary",
                        "ok" to false,
                        "error" to jsonObject(
                            "message" to result.message,
                            "issues" to jsonArray(result.issues.map(::validationIssueJson)),
                        ),
                    ).encoded,
                )
                EXIT_FAILED
            }

            is EntioResult.Success -> {
                val project = result.value
                spec.commandLine().out.println(
                    jsonObject(
                        "command" to "project-summary",
                        "ok" to true,
                        "project" to jsonObject(
                            "name" to project.config.name,
                            "root" to Path.of(projectRoot).toAbsolutePath().normalize().toString(),
                            "graphTripleCount" to project.graph.triples.size,
                        ),
                        "ontologySources" to jsonArray(
                            project.ontologies.map { ontology ->
                                jsonObject(
                                    "id" to ontology.source.id,
                                    "path" to project.config.ontologySources
                                        .first { source -> source.id == ontology.source.id }
                                        .path,
                                    "format" to ontology.source.format.name.lowercase(),
                                    "tripleCount" to ontology.graph.triples.size,
                                )
                            },
                        ),
                        "symbols" to jsonArray(project.symbols.map(::symbolJson)),
                    ).encoded,
                )
                EXIT_OK
            }
        }

    private companion object {
        private const val EXIT_OK: Int = 0
        private const val EXIT_FAILED: Int = 1
    }
}
