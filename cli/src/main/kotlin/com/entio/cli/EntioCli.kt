package com.entio.cli

import com.entio.diff.GraphDiffer
import com.entio.diff.SemanticDiffFormatter
import com.entio.validation.ProjectValidator
import java.io.PrintWriter
import kotlin.system.exitProcess
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Spec

public object CliModule {
    public const val NAME: String = "cli"
}

public class EntioCli(
    private val projectValidator: ProjectValidator = ProjectValidator(),
    private val projectReader: CliProjectReader = CliProjectReader(),
    private val graphDiffer: GraphDiffer = GraphDiffer(),
    private val diffFormatter: SemanticDiffFormatter = SemanticDiffFormatter(),
) {
    public fun execute(
        args: Array<String>,
        out: PrintWriter = PrintWriter(System.out, true),
        err: PrintWriter = PrintWriter(System.err, true),
    ): Int {
        val commandLine = CommandLine(RootCommand())
            .addSubcommand("validate", ValidateCommand(projectValidator))
            .addSubcommand("symbols", SymbolsCommand(projectReader))
            .addSubcommand("diff", DiffCommand(projectReader, graphDiffer, diffFormatter))

        return commandLine
            .setOut(out)
            .setErr(err)
            .execute(*args)
    }
}

@Command(
    name = "entio",
    mixinStandardHelpOptions = true,
    description = ["Entio Core Semantic Engine CLI."],
)
private class RootCommand : Runnable {
    @Spec
    private lateinit var spec: CommandSpec

    override fun run(): Unit {
        spec.commandLine().usage(spec.commandLine().out)
    }
}

public fun main(args: Array<String>): Unit {
    exitProcess(EntioCli().execute(args))
}
