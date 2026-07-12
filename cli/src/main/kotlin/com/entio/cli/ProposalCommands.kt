package com.entio.cli

import com.entio.core.ChangeProposalStatus
import com.entio.core.EntioResult
import com.entio.core.ApplyProposalResult
import java.nio.file.Path
import java.util.concurrent.Callable
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.Spec

internal abstract class ProposalCommand(
    private val support: ProposalCommandSupport,
) : Callable<Int> {
    @Spec
    protected lateinit var spec: CommandSpec

    @Parameters(index = "0", paramLabel = "PROJECT_ROOT", description = ["Path to an Entio project root."])
    protected lateinit var projectRoot: String

    @Parameters(index = "1", paramLabel = "TARGET_SOURCE_ID", description = ["Ontology source ID targeted by the proposal."])
    protected lateinit var targetSourceId: String

    @Option(names = ["--edit"], defaultValue = "create-class", description = ["Supported edit kind."])
    protected var editKind: String = ProposalCommandSupport.CREATE_CLASS_EDIT

    @Option(names = ["--class-iri"], description = ["IRI for the class created by the edit."])
    protected var classIri: String = ""

    @Option(names = ["--label"], description = ["Optional label for the created class."])
    protected var label: String? = null

    @Option(names = ["--property-iri"], description = ["IRI for a property edit."])
    protected var propertyIri: String = ""

    @Option(names = ["--domain-iri"], description = ["IRI for a property domain."])
    protected var domainIri: String = ""

    @Option(names = ["--range-iri"], description = ["IRI for a property range."])
    protected var rangeIri: String = ""

    @Option(names = ["--datatype"], description = ["Datatype IRI for a literal or datatype property range."])
    protected var datatype: String? = null

    @Option(names = ["--individual-iri"], description = ["IRI for an individual edit."])
    protected var individualIri: String = ""

    @Option(names = ["--type-iri"], description = ["IRI for an entity type."])
    protected var typeIri: String = ""

    @Option(names = ["--subject-iri"], description = ["IRI for an assertion subject."])
    protected var subjectIri: String = ""

    @Option(names = ["--object-iri"], description = ["IRI for an object-property assertion object."])
    protected var objectIri: String = ""

    @Option(names = ["--value"], description = ["Lexical value for a datatype-property assertion."])
    protected var value: String = ""

    @Option(names = ["--language"], description = ["Optional language tag for a literal value or label."])
    protected var language: String? = null

    @Option(names = ["--superclass-iri"], description = ["IRI for a superclass relationship."])
    protected var superclassIri: String = ""

    @Option(names = ["--entity-iri"], description = ["IRI for an entity label edit."])
    protected var entityIri: String = ""

    @Option(names = ["--proposal-id"], defaultValue = "cli-proposal", description = ["Proposal identifier."])
    protected var proposalId: String = "cli-proposal"

    @Option(names = ["--title"], defaultValue = "CLI ontology proposal", description = ["Proposal title."])
    protected var title: String = "CLI ontology proposal"

    protected fun prepare(command: String): EntioResult<PreparedProposal> =
        try {
            support.prepare(
                projectRoot = Path.of(projectRoot),
                targetSourceId = targetSourceId,
                edit = CliEditRequest(
                    editKind = editKind,
                    classIri = classIri,
                    label = label,
                    propertyIri = propertyIri,
                    domainIri = domainIri,
                    rangeIri = rangeIri,
                    datatype = datatype,
                    individualIri = individualIri,
                    typeIri = typeIri,
                    subjectIri = subjectIri,
                    objectIri = objectIri,
                    value = value,
                    language = language,
                    superclassIri = superclassIri,
                    entityIri = entityIri,
                ),
                proposalId = proposalId,
                title = title,
            )
        } catch (exception: RuntimeException) {
            ProposalCommandSupport.failure(
                message = exception.message ?: "CLI proposal input could not be processed.",
                code = "invalid-cli-input",
                source = command,
            )
        }

    protected fun printResult(
        result: JsonFragment,
        exitCode: Int = EXIT_OK,
    ): Int {
        spec.commandLine().out.println(result.encoded)
        return exitCode
    }

    protected fun printFailure(command: String, result: EntioResult.Failure): Int {
        spec.commandLine().out.println(
            jsonObject(
                "command" to command,
                "ok" to false,
                "error" to jsonObject(
                    "message" to result.message,
                    "issues" to jsonArray(result.issues.map(::validationIssueJson)),
                ),
            ).encoded,
        )
        return EXIT_FAILED
    }

    protected fun support(): ProposalCommandSupport = support

    internal companion object {
        internal const val EXIT_OK: Int = 0
        internal const val EXIT_FAILED: Int = 1
    }
}

@Command(
    name = "proposal-preview",
    mixinStandardHelpOptions = true,
    description = ["Preview a supported ontology proposal as machine-readable JSON."],
)
internal class ProposalPreviewCommand(
    support: ProposalCommandSupport,
) : ProposalCommand(support) {
    override fun call(): Int =
        when (val result = prepare("proposal-preview")) {
            is EntioResult.Failure -> printFailure("proposal-preview", result)
            is EntioResult.Success -> printResult(
                proposalPayload("proposal-preview", result.value),
                if (result.value.validationReport.ok) 0 else 1,
            )
        }
}

@Command(
    name = "proposal-validate",
    mixinStandardHelpOptions = true,
    description = ["Validate a supported ontology proposal as machine-readable JSON."],
)
internal class ProposalValidateCommand(
    support: ProposalCommandSupport,
) : ProposalCommand(support) {
    override fun call(): Int =
        when (val result = prepare("proposal-validate")) {
            is EntioResult.Failure -> printFailure("proposal-validate", result)
            is EntioResult.Success -> printResult(
                proposalPayload("proposal-validate", result.value),
                if (result.value.validationReport.ok) 0 else 1,
            )
        }
}

@Command(
    name = "proposal-diff",
    mixinStandardHelpOptions = true,
    description = ["Return a supported proposal's semantic diff as machine-readable JSON."],
)
internal class ProposalDiffCommand(
    support: ProposalCommandSupport,
) : ProposalCommand(support) {
    override fun call(): Int =
        when (val result = prepare("proposal-diff")) {
            is EntioResult.Failure -> printFailure("proposal-diff", result)
            is EntioResult.Success -> printResult(proposalPayload("proposal-diff", result.value))
        }
}

@Command(
    name = "proposal-apply",
    mixinStandardHelpOptions = true,
    description = ["Apply a valid supported ontology proposal and return a machine-readable result."],
)
internal class ProposalApplyCommand(
    support: ProposalCommandSupport,
) : ProposalCommand(support) {
    override fun call(): Int =
        when (val result = prepare("proposal-apply")) {
            is EntioResult.Failure -> printFailure("proposal-apply", result)
            is EntioResult.Success -> {
                val prepared = result.value
                if (!prepared.validationReport.ok) {
                    printResult(proposalPayload("proposal-apply", prepared, ok = false), EXIT_FAILED)
                } else {
                    val approved = prepared.proposal.copy(
                        status = ChangeProposalStatus.Approved,
                    )
                    val applyResult = support().proposalApplier.applyProposal(Path.of(projectRoot), approved)
                    printResult(
                        applyProposalPayload(
                            proposalId = approved.id,
                            result = applyResult,
                        ),
                        if (applyResult is ApplyProposalResult.Applied) EXIT_OK else EXIT_FAILED,
                    )
                }
            }
        }
}

@Command(
    name = "proposal-reject",
    mixinStandardHelpOptions = true,
    description = ["Reject a supported ontology proposal without changing source files."],
)
internal class ProposalRejectCommand(
    support: ProposalCommandSupport,
) : ProposalCommand(support) {
    override fun call(): Int =
        when (val result = prepare("proposal-reject")) {
            is EntioResult.Failure -> printFailure("proposal-reject", result)
            is EntioResult.Success -> printResult(
                proposalPayload(
                    command = "proposal-reject",
                    prepared = result.value,
                    ok = true,
                    statusOverride = ChangeProposalStatus.Rejected,
                ),
            )
        }
}
