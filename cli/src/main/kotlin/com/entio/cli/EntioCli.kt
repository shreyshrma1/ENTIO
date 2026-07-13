package com.entio.cli

import com.entio.diff.GraphDiffer
import com.entio.diff.ProposalDiffGenerator
import com.entio.diff.SemanticDiffFormatter
import com.entio.semantic.PreviewTurtleRoundTripVerifier
import com.entio.semantic.ProposalApplier
import com.entio.semantic.ProposalCreator
import com.entio.semantic.ProjectLoader
import com.entio.semantic.TypedOntologyEditTranslator
import com.entio.validation.ProjectValidator
import com.entio.validation.ProposalValidator
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
    private val proposalCommandSupport: ProposalCommandSupport = ProposalCommandSupport(
        projectLoader = ProjectLoader(),
        proposalCreator = ProposalCreator(),
        proposalDiffGenerator = ProposalDiffGenerator(),
        proposalValidator = ProposalValidator(),
        projectValidator = ProjectValidator(),
        equivalenceVerifier = PreviewTurtleRoundTripVerifier(),
        proposalApplier = ProposalApplier(),
        editTranslator = TypedOntologyEditTranslator(),
    ),
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
            .addSubcommand("project-summary", ProjectSummaryCommand())
            .addSubcommand("summary", ProjectSummaryCommand())
            .addSubcommand("proposal-preview", ProposalPreviewCommand(proposalCommandSupport))
            .addSubcommand("proposal-validate", ProposalValidateCommand(proposalCommandSupport))
            .addSubcommand("proposal-diff", ProposalDiffCommand(proposalCommandSupport))
            .addSubcommand("proposal-apply", ProposalApplyCommand(proposalCommandSupport))
            .addSubcommand("proposal-reject", ProposalRejectCommand(proposalCommandSupport))
            .addSubcommand("proposal-request", StructuredProposalCommand())
            .addSubcommand("proposal-combined", CombinedProposalCommand())
            .addSubcommand("resolve-label", ResolveLabelCommand())
            .addSubcommand("generate-iri", GenerateIriCommand())
            .addSubcommand("deletion-dependencies", DeletionDependenciesCommand())

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
