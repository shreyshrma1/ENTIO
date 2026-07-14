package com.entio.cli

import com.entio.core.ChangeProposal
import com.entio.core.ChangeProposalStatus
import com.entio.core.ChangeSet
import com.entio.core.CombinedProposalPreview
import com.entio.core.CombinedProposalStatus
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.ProposalBaseline
import com.entio.core.SemanticEquivalenceResult
import com.entio.core.SourceFileImpact
import com.entio.core.StagedChange
import com.entio.core.StagedChangeSet
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import com.entio.diff.CombinedPreviewService
import com.entio.diff.ProposalDiffGenerator
import com.entio.semantic.PreviewTurtleRoundTripVerifier
import com.entio.semantic.ProjectLoader
import com.entio.semantic.ProposalCreator
import com.entio.semantic.TypedOntologyEditTranslator
import com.entio.validation.ProjectValidator
import com.entio.validation.ProposalValidator
import java.nio.file.Path
import java.util.concurrent.Callable
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.Spec

/** Runs one structured request through the complete combined proposal lifecycle. */
@Command(
    name = "proposal-combined",
    mixinStandardHelpOptions = true,
    description = ["Preview, validate, diff, apply, or reject an ordered structured proposal request."],
)
internal class CombinedProposalCommand(
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val parser: StructuredRequestParser = StructuredRequestParser(),
    private val previewService: CombinedPreviewService = CombinedPreviewService(),
    private val proposalApplier: com.entio.semantic.ProposalApplier = com.entio.semantic.ProposalApplier(),
    private val proposalCreator: ProposalCreator = ProposalCreator(),
    private val proposalDiffGenerator: ProposalDiffGenerator = ProposalDiffGenerator(),
    private val proposalValidator: ProposalValidator = ProposalValidator(),
    private val projectValidator: ProjectValidator = ProjectValidator(),
    private val equivalenceVerifier: PreviewTurtleRoundTripVerifier = PreviewTurtleRoundTripVerifier(),
    private val editTranslator: TypedOntologyEditTranslator = TypedOntologyEditTranslator(),
) : Callable<Int> {
    @Spec
    private lateinit var spec: CommandSpec

    @Parameters(index = "0", paramLabel = "PROJECT_ROOT", description = ["Path to an Entio project root."])
    private lateinit var projectRoot: String

    @Option(names = ["--request-file"], required = true, description = ["JSON file containing the ordered proposal request."])
    private lateinit var requestFile: String

    @Option(
        names = ["--action"],
        defaultValue = "preview",
        description = ["Lifecycle action: preview, validate, diff, apply, or reject."],
    )
    private lateinit var action: String

    override fun call(): Int {
        val root = Path.of(projectRoot)
        val request = when (val result = parser.parse(Path.of(requestFile))) {
            is EntioResult.Failure -> return printFailure(result)
            is EntioResult.Success -> result.value
        }
        val project = when (val result = projectLoader.loadProject(root)) {
            is EntioResult.Failure -> return printFailure(result)
            is EntioResult.Success -> result.value
        }
        val actionName = action.lowercase()
        if (actionName !in SUPPORTED_ACTIONS) {
            return printFailure(failure("unsupported-proposal-action", "Unsupported proposal action '$action'."))
        }
        if (request.edits.any(StructuredEditRequest::isSemanticEdit)) {
            if (request.edits.any { !it.isSemanticEdit() }) {
                return printFailure(failure("mixed-edit-kinds", "Semantic and structural edits must be submitted in separate requests."))
            }
            return semanticLifecycle(root, project, request, actionName)
        }
        val staged = when (val result = stagedChanges(project, request)) {
            is EntioResult.Failure -> return printFailure(result)
            is EntioResult.Success -> result.value
        }
        val expectedBaseline = when (val result = expectedBaseline(project, request)) {
            is EntioResult.Failure -> return printFailure(result)
            is EntioResult.Success -> result.value
        }
        val combined = when (
            val result = previewService.preview(
                project = project,
                stagedChangeSet = StagedChangeSet(staged),
                proposalId = request.proposalId,
                expectedBaseline = expectedBaseline,
            )
        ) {
            is EntioResult.Failure -> return printFailure(result)
            is EntioResult.Success -> result.value
        }

        if (actionName == "apply") {
            return apply(root, project, request, combined)
        }

        val rejected = actionName == "reject"
        val validForAction = combined.validationReport.ok && combined.equivalence is SemanticEquivalenceResult.Equivalent
        val ok = if (rejected) true else validForAction
        spec.commandLine().out.println(combinedPayload(actionName, request, combined, project, ok, rejected).encoded)
        return if (ok) EXIT_OK else EXIT_FAILED
    }

    private fun semanticLifecycle(
        projectRoot: Path,
        project: EntioProject,
        request: StructuredProposalRequest,
        action: String,
    ): Int {
        val changeSet = when (val result = semanticChangeSet(project, request)) {
            is EntioResult.Failure -> return printFailure(result)
            is EntioResult.Success -> result.value
        }
        val proposal = when (
            val result = proposalCreator.createProposal(
                project = project,
                targetSourceId = request.targetSourceId,
                changeSet = changeSet,
                id = request.proposalId,
                title = request.title,
            )
        ) {
            is EntioResult.Failure -> return printFailure(result)
            is EntioResult.Success -> result.value
        }
        val requestedBaseline = when (val result = expectedBaseline(project, request)) {
            is EntioResult.Failure -> return printFailure(result)
            is EntioResult.Success -> result.value
        }
        val proposalWithBaseline = requestedBaseline?.let { proposal.copy(baseline = it) } ?: proposal
        val proposalWithDiff = when (val result = proposalDiffGenerator.attachDiff(proposalWithBaseline, project.graph)) {
            is EntioResult.Failure -> return printFailure(result)
            is EntioResult.Success -> result.value
        }
        val preview = proposalWithDiff.preview
            ?: return printFailure(failure("missing-proposal-preview", "Proposal '${proposalWithDiff.id}' does not include a preview graph."))
        val equivalence = when (val result = equivalenceVerifier.verify(preview)) {
            is EntioResult.Failure -> return printFailure(result)
            is EntioResult.Success -> result.value
        }
        val validationReport = proposalValidator.validateProposal(
            proposal = proposalWithDiff,
            currentProject = project,
            projectValidationReport = projectValidator.validateProject(projectRoot),
            semanticEquivalenceResult = equivalence,
        )
        val preparedProposal = proposalWithDiff.copy(validationReport = validationReport)
        val combined = semanticCombinedPreview(request, preparedProposal, validationReport, equivalence)

        if (action == "apply") {
            val valid = validationReport.ok && equivalence is SemanticEquivalenceResult.Equivalent
            if (!valid) {
                spec.commandLine().out.println(combinedPayload(action, request, combined, project, ok = false, rejected = false).encoded)
                return EXIT_FAILED
            }
            val applyResult = proposalApplier.applyProposal(
                projectRoot,
                preparedProposal.copy(status = ChangeProposalStatus.Approved),
            )
            spec.commandLine().out.println(
                combinedPayload(
                    action = action,
                    request = request,
                    combined = combined,
                    project = project,
                    ok = applyResult is com.entio.core.ApplyProposalResult.Applied,
                    rejected = false,
                    applyResult = applyResult,
                ).encoded,
            )
            return if (applyResult is com.entio.core.ApplyProposalResult.Applied) EXIT_OK else EXIT_FAILED
        }

        val rejected = action == "reject"
        val validForAction = validationReport.ok && equivalence is SemanticEquivalenceResult.Equivalent
        val ok = rejected || validForAction
        spec.commandLine().out.println(combinedPayload(action, request, combined, project, ok, rejected).encoded)
        return if (ok) EXIT_OK else EXIT_FAILED
    }

    private fun semanticChangeSet(
        project: EntioProject,
        request: StructuredProposalRequest,
    ): EntioResult<ChangeSet> {
        val existingAnnotationProperties = project.graph.triples
            .filter { triple ->
                triple.predicate.value == RDF_TYPE &&
                    (triple.objectTerm as? com.entio.core.RdfResource)?.value == OWL_ANNOTATION_PROPERTY
            }
            .map { triple -> triple.subjectResource }
            .filterIsInstance<com.entio.core.Iri>()
            .toSet()
        val changes = mutableListOf<com.entio.core.GraphChange>()
        request.edits.forEach { edit ->
            val semanticEdit = when (val result = edit.toSemanticEditRequest(request.targetSourceId)) {
                is EntioResult.Failure -> return result
                is EntioResult.Success -> result.value
            }
            when (val result = editTranslator.translate(semanticEdit, existingAnnotationProperties)) {
                is EntioResult.Failure -> return result
                is EntioResult.Success -> changes += result.value.changes
            }
        }
        return EntioResult.Success(ChangeSet(changes))
    }

    private fun semanticCombinedPreview(
        request: StructuredProposalRequest,
        proposal: ChangeProposal,
        validationReport: com.entio.core.ValidationReport,
        equivalence: SemanticEquivalenceResult,
    ): CombinedProposalPreview {
        val status = when {
            validationReport.issues.any { issue -> issue.code == "stale-proposal-baseline" } -> CombinedProposalStatus.Stale
            validationReport.ok && equivalence is SemanticEquivalenceResult.Equivalent -> CombinedProposalStatus.ReadyForReview
            else -> CombinedProposalStatus.Invalid
        }
        return CombinedProposalPreview(
            metadata = com.entio.core.CombinedProposalMetadata(
                proposalId = request.proposalId,
                stagedChangeIds = request.edits.indices.map { index -> "${request.proposalId}-$index" },
                targetSourceIds = listOf(request.targetSourceId),
                status = status,
                baseline = proposal.baseline,
            ),
            changeSet = proposal.changeSet,
            preview = proposal.preview,
            diff = proposal.diff,
            validationReport = validationReport,
            equivalence = equivalence,
        )
    }

    private fun apply(
        projectRoot: Path,
        project: EntioProject,
        request: StructuredProposalRequest,
        combined: CombinedProposalPreview,
    ): Int {
        val baseline = combined.metadata.baseline
        val preview = combined.preview
        val changeSet = combined.changeSet
        if (
            combined.metadata.status != CombinedProposalStatus.ReadyForReview ||
            baseline == null ||
            preview == null ||
            changeSet == null
        ) {
            spec.commandLine().out.println(combinedPayload("apply", request, combined, project, ok = false, rejected = false).encoded)
            return EXIT_FAILED
        }

        val targetSource = project.resolvedSources.single { it.id == request.targetSourceId }
        val proposal = ChangeProposal(
            id = request.proposalId,
            title = request.title,
            targetSourceId = request.targetSourceId,
            changeSet = changeSet,
            baseline = baseline,
            status = ChangeProposalStatus.Approved,
            preview = preview,
            diff = combined.diff,
            validationReport = combined.validationReport,
            sourceFileImpact = SourceFileImpact(listOf(targetSource.path.toString())),
        )
        val result = proposalApplier.applyProposal(projectRoot, proposal)
        spec.commandLine().out.println(
            combinedPayload(
                action = "apply",
                request = request,
                combined = combined,
                project = project,
                ok = result is com.entio.core.ApplyProposalResult.Applied,
                rejected = false,
                applyResult = result,
            ).encoded,
        )
        return if (result is com.entio.core.ApplyProposalResult.Applied) EXIT_OK else EXIT_FAILED
    }

    private fun stagedChanges(
        project: EntioProject,
        request: StructuredProposalRequest,
    ): EntioResult<List<StagedChange>> {
        if (project.resolvedSources.none { it.id == request.targetSourceId }) {
            return failure("missing-target-source", "Target ontology source '${request.targetSourceId}' was not found.")
        }
        return EntioResult.Success(
            request.edits.mapIndexed { index, edit ->
                val operation = when (val result = edit.toOperation(project, request.targetSourceId)) {
                    is EntioResult.Failure -> return result
                    is EntioResult.Success -> result.value
                }
                StagedChange(
                    id = "${request.proposalId}-$index",
                    order = index,
                    targetSourceId = request.targetSourceId,
                    summary = edit.kind,
                    operation = operation,
                )
            },
        )
    }

    private fun expectedBaseline(
        project: EntioProject,
        request: StructuredProposalRequest,
    ): EntioResult<ProposalBaseline?> {
        val requested = request.baseline ?: return EntioResult.Success(null)
        if (
            requested.projectFingerprint == null ||
            requested.targetSourceFingerprint == null ||
            requested.graphFingerprint == null
        ) {
            return failure("invalid-request-baseline", "Structured request baseline must include all fingerprints.")
        }
        val source = project.resolvedSources.firstOrNull { it.id == request.targetSourceId }
            ?: return failure("missing-target-source", "Target ontology source '${request.targetSourceId}' was not found.")
        return EntioResult.Success(
            ProposalBaseline(
                projectFingerprint = requested.projectFingerprint,
                targetSourceId = request.targetSourceId,
                targetSourcePath = source.path.toString(),
                targetSourceFingerprint = requested.targetSourceFingerprint,
                graphFingerprint = requested.graphFingerprint,
            ),
        )
    }

    private fun combinedPayload(
        action: String,
        request: StructuredProposalRequest,
        combined: CombinedProposalPreview,
        project: EntioProject,
        ok: Boolean,
        rejected: Boolean,
        applyResult: com.entio.core.ApplyProposalResult? = null,
    ): JsonFragment {
        val status = when {
            applyResult is com.entio.core.ApplyProposalResult.Applied -> "applied"
            applyResult is com.entio.core.ApplyProposalResult.Failed -> "apply-failed"
            rejected -> "rejected"
            else -> combined.metadata.status.name.lowercase()
        }
        val affectedPaths = project.resolvedSources
            .filter { source -> source.id in combined.metadata.targetSourceIds }
            .map { source -> source.path.toString() }
        return jsonObject(
            "command" to "proposal-combined",
            "action" to action,
            "ok" to ok,
            "status" to status,
            "proposal" to jsonObject(
                "id" to request.proposalId,
                "title" to request.title,
                "targetSourceIds" to jsonArray(combined.metadata.targetSourceIds),
                "stagedChangeIds" to jsonArray(combined.metadata.stagedChangeIds),
                "normalizedChangeCount" to (combined.changeSet?.changes?.size ?: 0),
                "conflicts" to jsonArray(combined.metadata.conflicts.map { conflict ->
                    jsonObject(
                        "kind" to conflict.kind.name,
                        "stagedChangeIds" to jsonArray(conflict.stagedChangeIds),
                        "message" to conflict.message,
                    )
                }),
            ),
            "preview" to jsonObject("tripleCount" to combined.preview?.graph?.triples?.size),
            "diff" to (combined.diff?.let(::semanticDiffJson) ?: jsonObject("entryCount" to 0, "entries" to emptyList<Any>())),
            "validation" to validationReportJson(combined.validationReport),
            "semanticEquivalence" to semanticEquivalenceJson(combined.equivalence),
            "sourceFileImpact" to jsonObject("affectedPaths" to jsonArray(affectedPaths)),
            "rollback" to (applyResult?.let(::rollbackJson) ?: jsonObject("status" to "not-required")),
            "changedFiles" to jsonArray(
                (applyResult as? com.entio.core.ApplyProposalResult.Applied)?.changedFiles.orEmpty(),
            ),
        )
    }

    private fun semanticEquivalenceJson(result: SemanticEquivalenceResult?): JsonFragment =
        when (result) {
            null -> jsonObject("status" to "not-run")
            SemanticEquivalenceResult.Equivalent -> jsonObject("status" to "equivalent")
            is SemanticEquivalenceResult.NotEquivalent -> jsonObject("status" to "not-equivalent", "reason" to result.reason)
            is SemanticEquivalenceResult.Failed -> jsonObject("status" to "failed", "reason" to result.reason)
        }

    private fun rollbackJson(result: com.entio.core.ApplyProposalResult): JsonFragment =
        when (result) {
            is com.entio.core.ApplyProposalResult.Applied -> jsonObject("status" to "not-required")
            is com.entio.core.ApplyProposalResult.Failed -> when (val rollback = result.rollback) {
                com.entio.core.RollbackResult.NotRequired -> jsonObject("status" to "not-required")
                is com.entio.core.RollbackResult.Restored -> jsonObject("status" to "restored", "restoredFiles" to jsonArray(rollback.restoredFiles))
                is com.entio.core.RollbackResult.Failed -> jsonObject("status" to "failed", "reason" to rollback.reason)
            }
        }

    private fun printFailure(result: EntioResult.Failure): Int {
        spec.commandLine().out.println(
            jsonObject(
                "command" to "proposal-combined",
                "ok" to false,
                "status" to result.issues.firstOrNull()?.code,
                "error" to jsonObject(
                    "message" to result.message,
                    "issues" to jsonArray(result.issues.map(::validationIssueJson)),
                ),
            ).encoded,
        )
        return EXIT_FAILED
    }

    private companion object {
        private val SUPPORTED_ACTIONS = setOf("preview", "validate", "diff", "apply", "reject")
        private const val EXIT_OK = 0
        private const val EXIT_FAILED = 1
        private const val RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
        private const val OWL_ANNOTATION_PROPERTY = "http://www.w3.org/2002/07/owl#AnnotationProperty"

        private fun failure(code: String, message: String): EntioResult.Failure =
            EntioResult.Failure(
                message = message,
                issues = listOf(ValidationIssue(ValidationSeverity.Error, code, message)),
            )
    }
}
