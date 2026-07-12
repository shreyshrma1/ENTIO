package com.entio.cli

import com.entio.validation.ProjectValidator
import java.nio.file.Path
import java.util.concurrent.Callable
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.Spec
import picocli.CommandLine.Model.CommandSpec

@Command(
    name = "validate",
    mixinStandardHelpOptions = true,
    description = ["Validate an Entio project."],
)
public class ValidateCommand(
    private val projectValidator: ProjectValidator,
) : Callable<Int> {
    @Spec
    private lateinit var spec: CommandSpec

    @Parameters(index = "0", paramLabel = "PROJECT_ROOT", description = ["Path to an Entio project root."])
    private lateinit var projectRoot: String

    @Option(names = ["--json"], description = ["Print a machine-readable JSON response."])
    private var json: Boolean = false

    override fun call(): Int {
        val report = projectValidator.validateProject(Path.of(projectRoot))
        if (json) {
            spec.commandLine().out.printValidationReportJson("validate", report)
        } else {
            spec.commandLine().out.printValidationReport(report)
        }
        return if (report.ok) EXIT_OK else EXIT_VALIDATION_FAILED
    }

    private companion object {
        private const val EXIT_OK: Int = 0
        private const val EXIT_VALIDATION_FAILED: Int = 1
    }
}
