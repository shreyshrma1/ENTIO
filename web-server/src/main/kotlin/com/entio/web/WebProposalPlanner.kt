package com.entio.web

import com.entio.core.ChangeProposal
import com.entio.core.ChangeProposalStatus
import com.entio.core.ChangeSet
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.LoadedOntology
import com.entio.core.ProposalImpactReport
import com.entio.core.ShaclValidationMode
import com.entio.core.ShaclValidationReport
import com.entio.core.ShaclValidationStatus
import com.entio.core.SourceFileImpact
import com.entio.core.StagedChange
import com.entio.core.StagedChangeSet
import com.entio.core.ValidationIssue
import com.entio.core.ValidationReport
import com.entio.core.ValidationSeverity
import com.entio.core.ValidationStatus
import com.entio.diff.GraphDiffer
import com.entio.diff.ProposalImpactAnalyzer
import com.entio.semantic.GraphChangePreviewer
import com.entio.semantic.MultiSourceApplyTarget
import com.entio.semantic.ProposalCreator
import com.entio.semantic.ShaclGraphLoader
import com.entio.semantic.ShaclValidationService
import com.entio.semantic.StagedChangeSetNormalizer
import com.entio.semantic.TypedShaclEditTranslator
import com.entio.validation.ProposalValidator
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID

internal data class PreparedWebProposal(
    val proposal: ChangeProposal,
    val targets: List<MultiSourceApplyTarget>,
    val impact: ProposalImpactReport,
    val currentShaclFingerprint: String,
    val previewShaclFingerprint: String,
    val expectedShaclResults: Set<String>,
)

/** Builds one reviewable proposal while retaining source-specific atomic apply targets. */
internal class WebProposalPlanner(
    private val normalizer: StagedChangeSetNormalizer = StagedChangeSetNormalizer(),
    private val proposalCreator: ProposalCreator = ProposalCreator(),
    private val proposalValidator: ProposalValidator = ProposalValidator(),
    private val previewer: GraphChangePreviewer = GraphChangePreviewer(),
    private val graphDiffer: GraphDiffer = GraphDiffer(),
    private val shaclGraphLoader: ShaclGraphLoader = ShaclGraphLoader(),
    private val shaclValidation: ShaclValidationService = ShaclValidationService(),
    private val impactAnalyzer: ProposalImpactAnalyzer = ProposalImpactAnalyzer(),
    private val shaclTranslator: TypedShaclEditTranslator = TypedShaclEditTranslator(),
) {
    fun prepare(project: EntioProject, entries: List<StagedChange>): PreparedWebProposal {
        val materializedEntries = materializeShaclEdits(project, entries)
        val normalized = normalize(materializedEntries)
        if (normalized.conflicts.isNotEmpty()) {
            throw WebWorkflowFailure("staging-conflict", normalized.conflicts.joinToString { it.message })
        }
        val allChanges = normalized.changeSet
            ?: throw WebWorkflowFailure("empty-staged-set", "At least one staged change is required.")

        val targets = materializedEntries.groupBy(StagedChange::targetSourceId)
            .toSortedMap()
            .map { (sourceId, sourceEntries) -> sourceTarget(project, sourceId, sourceEntries) }
        val primarySourceId = targets.first().sourceId
        val proposal = when (
            val result = proposalCreator.createProposal(
                project,
                primarySourceId,
                allChanges,
                "proposal-${UUID.randomUUID()}",
                "Web staged ontology and SHACL changes",
            )
        ) {
            is EntioResult.Failure -> throw WebWorkflowFailure("proposal-preview-failed", result.message)
            is EntioResult.Success -> result.value
        }
        val preview = proposal.preview
            ?: throw WebWorkflowFailure("proposal-preview-failed", "The proposal preview graph was not created.")
        val previewOntologies = previewOntologies(project.ontologies, targets)
        val currentShacl = validateShacl(project.ontologies)
        val previewShacl = validateShacl(previewOntologies)
        val impact = when (
            val result = impactAnalyzer.analyze(
                beforeGraph = project.graph,
                afterGraph = preview.graph,
                currentReasoning = null,
                previewReasoning = null,
                currentShacl = currentShacl,
                previewShacl = previewShacl,
            )
        ) {
            is EntioResult.Failure -> throw WebWorkflowFailure("proposal-impact-failed", result.message)
            is EntioResult.Success -> result.value
        }
        val ontologyEntries = materializedEntries.filter { entry ->
            project.resolvedSources.firstOrNull { it.id == entry.targetSourceId }
                ?.roles
                ?.contains(com.entio.core.ShaclGraphRole.Shapes) != true
        }
        val validation = combinedValidation(project, ontologyEntries, impact, currentShacl, previewShacl)
        val prepared = proposal.copy(
            status = if (validation.ok) ChangeProposalStatus.ReadyForReview else ChangeProposalStatus.VerificationFailed,
            validationReport = validation,
            diff = graphDiffer.diff(project.graph, preview.graph),
            sourceFileImpact = SourceFileImpact(targets.map { it.path.toString() }),
        )
        return PreparedWebProposal(
            proposal = prepared,
            targets = targets,
            impact = impact,
            currentShaclFingerprint = currentShacl.graphIdentity.combinedFingerprint(),
            previewShaclFingerprint = previewShacl.graphIdentity.combinedFingerprint(),
            expectedShaclResults = previewShacl.results.map(::verificationKey).toSet(),
        )
    }

    private fun materializeShaclEdits(project: EntioProject, entries: List<StagedChange>): List<StagedChange> {
        val currentGraphs = project.ontologies.associate { it.source.id to it.graph }.toMutableMap()
        return entries.sortedWith(compareBy<StagedChange>({ it.order }, { it.id })).map { entry ->
            val operation = entry.operation
            if (operation !is com.entio.core.StagedChangeOperation.ShaclEdit) return@map entry
            val current = currentGraphs[entry.targetSourceId]
                ?: throw WebWorkflowFailure("unknown-source", "Ontology source '${entry.targetSourceId}' was not loaded.")
            val changes = when (val result = shaclTranslator.translate(operation.edit, current)) {
                is EntioResult.Failure -> throw WebWorkflowFailure(
                    result.issues.firstOrNull()?.code ?: "shacl-edit-invalid",
                    result.message,
                )
                is EntioResult.Success -> result.value
            }
            val next = when (val result = previewer.preview(current, changes)) {
                is EntioResult.Failure -> throw WebWorkflowFailure("shacl-preview-failed", result.issues.joinToString { it.message })
                is EntioResult.Success -> result.value.graph
            }
            currentGraphs[entry.targetSourceId] = next
            entry.copy(operation = com.entio.core.StagedChangeOperation.GraphChanges(changes))
        }
    }

    fun verifyAppliedProject(project: EntioProject, expectedShaclResults: Set<String>): EntioResult<Unit> {
        val report = validateShacl(project.ontologies)
        if (report.status != ShaclValidationStatus.Completed) {
            return EntioResult.Failure(report.errors.joinToString().ifBlank { "Reloaded SHACL validation did not complete." })
        }
        if (report.results.map(::verificationKey).toSet() != expectedShaclResults) {
            return EntioResult.Failure("Reloaded SHACL findings do not match the reviewed proposal preview.")
        }
        return EntioResult.Success(Unit)
    }

    private fun normalize(entries: List<StagedChange>): com.entio.core.NormalizedStagedChangeSet = when (
        val result = normalizer.normalize(StagedChangeSet(entries))
    ) {
        is EntioResult.Failure -> throw WebWorkflowFailure("staging-invalid", result.message)
        is EntioResult.Success -> result.value
    }

    private fun sourceTarget(
        project: EntioProject,
        sourceId: String,
        entries: List<StagedChange>,
    ): MultiSourceApplyTarget {
        val source = project.resolvedSources.firstOrNull { it.id == sourceId }
            ?: throw WebWorkflowFailure("unknown-source", "Ontology source '$sourceId' was not found.")
        val ontology = project.ontologies.firstOrNull { it.source.id == sourceId }
            ?: throw WebWorkflowFailure("unknown-source", "Ontology source '$sourceId' was not loaded.")
        val normalized = normalize(entries)
        if (normalized.conflicts.isNotEmpty()) {
            throw WebWorkflowFailure("staging-conflict", normalized.conflicts.joinToString { it.message })
        }
        val changeSet = normalized.changeSet
            ?: throw WebWorkflowFailure("empty-source-change-set", "Source '$sourceId' has no graph changes.")
        val preview = when (val result = previewer.preview(ontology.graph, changeSet)) {
            is EntioResult.Failure -> throw WebWorkflowFailure(
                "source-preview-failed",
                result.issues.joinToString { it.message }.ifBlank { result.message },
            )
            is EntioResult.Success -> result.value
        }
        return MultiSourceApplyTarget(
            sourceId = sourceId,
            path = source.path,
            baselineFingerprint = sha256(Files.readAllBytes(source.path)),
            changeSet = changeSet,
            expectedGraph = preview.graph,
        )
    }

    private fun previewOntologies(
        current: List<LoadedOntology>,
        targets: List<MultiSourceApplyTarget>,
    ): List<LoadedOntology> {
        val bySource = targets.associateBy(MultiSourceApplyTarget::sourceId)
        return current.map { ontology ->
            bySource[ontology.source.id]?.let { ontology.copy(graph = it.expectedGraph) } ?: ontology
        }
    }

    private fun validateShacl(ontologies: List<LoadedOntology>): ShaclValidationReport {
        val graphs = when (val result = shaclGraphLoader.load(ontologies)) {
            is EntioResult.Failure -> throw WebWorkflowFailure("shacl-graph-load-failed", result.message)
            is EntioResult.Success -> result.value
        }
        return when (val result = shaclValidation.validate(graphs, ShaclValidationMode.AssertedOnly)) {
            is EntioResult.Failure -> throw WebWorkflowFailure("shacl-validation-failed", result.message)
            is EntioResult.Success -> result.value
        }
    }

    private fun combinedValidation(
        project: EntioProject,
        ontologyEntries: List<StagedChange>,
        impact: ProposalImpactReport,
        currentShacl: ShaclValidationReport,
        previewShacl: ShaclValidationReport,
    ): ValidationReport {
        val issues = mutableListOf<ValidationIssue>()
        if (ontologyEntries.isNotEmpty()) {
            val normalized = normalize(ontologyEntries)
            val changes = normalized.changeSet
                ?: throw WebWorkflowFailure("empty-ontology-change-set", "The ontology entries have no graph changes.")
            val sourceId = ontologyEntries.first().targetSourceId
            val ontologyProposal = when (
                val result = proposalCreator.createProposal(project, sourceId, changes, "ontology-validation", "Ontology validation")
            ) {
                is EntioResult.Failure -> throw WebWorkflowFailure("ontology-preview-failed", result.message)
                is EntioResult.Success -> result.value
            }
            issues += proposalValidator.validateProposal(ontologyProposal, project).issues
        }
        if (currentShacl.status != ShaclValidationStatus.Completed) {
            issues += ValidationIssue(ValidationSeverity.Error, "current-shacl-incomplete", currentShacl.errors.joinToString().ifBlank { "Current SHACL validation did not complete." })
        }
        if (previewShacl.status != ShaclValidationStatus.Completed) {
            issues += ValidationIssue(ValidationSeverity.Error, "preview-shacl-incomplete", previewShacl.errors.joinToString().ifBlank { "Preview SHACL validation did not complete." })
        }
        impact.blockingMessages.forEach { message ->
            issues += ValidationIssue(ValidationSeverity.Error, "blocking-shacl-impact", message)
        }
        impact.shaclImpact.newResults.filter { it.severity != com.entio.core.ShaclSeverity.Violation }.forEach { result ->
            issues += ValidationIssue(ValidationSeverity.Warning, "new-shacl-${result.severity.name.lowercase()}", result.message, result.focusNode.value)
        }
        return ValidationReport(
            status = if (issues.any { it.severity == ValidationSeverity.Error }) ValidationStatus.Invalid else ValidationStatus.Valid,
            issues = issues.distinct().sortedWith(compareBy({ it.severity }, { it.code }, { it.source.orEmpty() }, { it.message })),
        )
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun com.entio.core.ShaclGraphIdentity.combinedFingerprint(): String = sha256(
        "$dataGraphFingerprint|$shapesGraphFingerprint".toByteArray(),
    )

    private fun verificationKey(result: com.entio.core.ShaclValidationResult): String = listOf(
        result.severity.name,
        result.message,
        result.focusNode.value,
        (result.path as? com.entio.core.ShaclPath.DirectProperty)?.propertyIri?.value.orEmpty(),
        result.constraint.name,
        result.value.toString(),
        result.sourceId.orEmpty(),
    ).joinToString("|")
}
