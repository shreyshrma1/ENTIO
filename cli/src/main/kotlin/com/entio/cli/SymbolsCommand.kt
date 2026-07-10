package com.entio.cli

import com.entio.core.EntioResult
import java.nio.file.Path
import java.util.concurrent.Callable
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Parameters
import picocli.CommandLine.Spec

@Command(
    name = "symbols",
    mixinStandardHelpOptions = true,
    description = ["List symbols extracted from an Entio project."],
)
public class SymbolsCommand(
    private val projectReader: CliProjectReader,
) : Callable<Int> {
    @Spec
    private lateinit var spec: CommandSpec

    @Parameters(index = "0", paramLabel = "PROJECT_ROOT", description = ["Path to an Entio project root."])
    private lateinit var projectRoot: String

    override fun call(): Int =
        when (val result = projectReader.loadSymbols(Path.of(projectRoot))) {
            is EntioResult.Failure -> {
                spec.commandLine().err.printValidationIssues(result.issues)
                EXIT_LOAD_FAILED
            }

            is EntioResult.Success -> {
                spec.commandLine().out.printSymbols(result.value)
                EXIT_OK
            }
        }

    private companion object {
        private const val EXIT_OK: Int = 0
        private const val EXIT_LOAD_FAILED: Int = 1
    }
}
