package com.entio.cli

import com.entio.core.EntioResult
import com.entio.diff.GraphDiffer
import com.entio.diff.SemanticDiffFormatter
import java.nio.file.Path
import java.util.concurrent.Callable
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.Spec

@Command(
    name = "diff",
    mixinStandardHelpOptions = true,
    description = ["Compare two Entio project graph states."],
)
public class DiffCommand(
    private val projectReader: CliProjectReader,
    private val graphDiffer: GraphDiffer,
    private val diffFormatter: SemanticDiffFormatter,
) : Callable<Int> {
    @Spec
    private lateinit var spec: CommandSpec

    @Parameters(index = "0", paramLabel = "BEFORE_PROJECT_ROOT", description = ["Path to the before project root."])
    private lateinit var beforeProjectRoot: String

    @Parameters(index = "1", paramLabel = "AFTER_PROJECT_ROOT", description = ["Path to the after project root."])
    private lateinit var afterProjectRoot: String

    @Option(names = ["--json"], description = ["Print a machine-readable JSON response."])
    private var json: Boolean = false

    override fun call(): Int {
        val beforeGraph = when (val result = projectReader.loadGraph(Path.of(beforeProjectRoot))) {
            is EntioResult.Failure -> {
                if (json) {
                    spec.commandLine().out.println(
                        jsonObject(
                            "command" to "diff",
                            "ok" to false,
                            "error" to jsonObject(
                                "message" to result.message,
                                "issues" to jsonArray(result.issues.map(::validationIssueJson)),
                            ),
                        ).encoded,
                    )
                } else {
                    spec.commandLine().err.printValidationIssues(result.issues)
                }
                return EXIT_LOAD_FAILED
            }

            is EntioResult.Success -> result.value
        }

        val afterGraph = when (val result = projectReader.loadGraph(Path.of(afterProjectRoot))) {
            is EntioResult.Failure -> {
                if (json) {
                    spec.commandLine().out.println(
                        jsonObject(
                            "command" to "diff",
                            "ok" to false,
                            "error" to jsonObject(
                                "message" to result.message,
                                "issues" to jsonArray(result.issues.map(::validationIssueJson)),
                            ),
                        ).encoded,
                    )
                } else {
                    spec.commandLine().err.printValidationIssues(result.issues)
                }
                return EXIT_LOAD_FAILED
            }

            is EntioResult.Success -> result.value
        }

        val diff = graphDiffer.diff(beforeGraph, afterGraph)
        if (json) {
            spec.commandLine().out.printSemanticDiffJson("diff", diff)
        } else {
            spec.commandLine().out.println(diffFormatter.format(diff))
        }
        return if (diff.entries.isEmpty()) EXIT_OK else EXIT_DIFF_FOUND
    }

    private companion object {
        private const val EXIT_OK: Int = 0
        private const val EXIT_DIFF_FOUND: Int = 1
        private const val EXIT_LOAD_FAILED: Int = 2
    }
}
