package com.entio.web

import com.entio.core.AddDatatypePropertyAssertionEdit
import com.entio.core.AddObjectPropertyAssertionEdit
import com.entio.core.AddSuperclassEdit
import com.entio.core.AssignTypeEdit
import com.entio.core.ChangeProposal
import com.entio.core.ChangeProposalStatus
import com.entio.core.CreateClassEdit
import com.entio.core.CreateDatatypePropertyEdit
import com.entio.core.CreateIndividualEdit
import com.entio.core.CreateObjectPropertyEdit
import com.entio.core.EntityCandidate
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.GeneratedIri
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.SetEntityLabelEdit
import com.entio.core.SetPropertyDomainEdit
import com.entio.core.SetPropertyRangeEdit
import com.entio.core.StagedChange
import com.entio.core.StagedChangeOperation
import com.entio.core.StagedChangeSet
import com.entio.core.StagedChangeSetStatus
import com.entio.core.TypedOntologyEdit
import com.entio.diff.GraphDiffer
import com.entio.semantic.DeletionDependencyAnalyzer
import com.entio.semantic.ProjectLoader
import com.entio.semantic.ProposalApplier
import com.entio.semantic.ProposalCreator
import com.entio.semantic.StagedChangeSetNormalizer
import com.entio.web.contract.IdempotencyDecision
import com.entio.web.contract.InMemoryIdempotencyStore
import com.entio.web.contract.ProjectRegistry
import com.entio.web.contract.WebProposalState
import com.entio.web.contract.WebStageChangeRequest
import com.entio.web.contract.WebStagedEntry
import com.entio.web.contract.WebStagingResponse
import com.entio.web.contract.WebDiffEntry
import java.util.UUID

public class WebWorkflowFailure(
    public val code: String,
    message: String,
) : IllegalArgumentException(message)

public data class WorkflowGraphSnapshot(
    val graph: com.entio.core.GraphState,
    val graphFingerprint: String,
    val proposalFingerprint: String? = null,
)

private data class StoredEntry(
    val staged: StagedChange,
    val authorId: String,
    var latestEditorId: String,
    val comment: String?,
    val aiGenerated: Boolean,
)

private data class ProjectSession(
    val entries: MutableList<StoredEntry> = mutableListOf(),
    var proposal: ChangeProposal? = null,
    val idempotency: InMemoryIdempotencyStore = InMemoryIdempotencyStore(),
    var nextOrder: Int = 1,
)

/** Server-owned single-client staging state that delegates semantic work to Kotlin services. */
public class StagingWorkflowService(
    private val projectRegistry: ProjectRegistry,
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val normalizer: StagedChangeSetNormalizer = StagedChangeSetNormalizer(),
    private val proposalCreator: ProposalCreator = ProposalCreator(),
    private val proposalValidator: com.entio.validation.ProposalValidator = com.entio.validation.ProposalValidator(),
    private val graphDiffer: GraphDiffer = GraphDiffer(),
    private val proposalApplier: ProposalApplier = ProposalApplier(),
    private val deletionAnalyzer: DeletionDependencyAnalyzer = DeletionDependencyAnalyzer(),
) {
    private val sessions: MutableMap<String, ProjectSession> = linkedMapOf()

    @Synchronized
    public fun snapshot(projectId: String): WebStagingResponse = response(projectId, session(projectId))

    @Synchronized
    public fun graphSnapshot(projectId: String, scope: WebJobScope): WorkflowGraphSnapshot {
        val project = load(projectId)
        return when (scope) {
            WebJobScope.Applied -> WorkflowGraphSnapshot(
                graph = project.graph,
                graphFingerprint = webGraphFingerprint(project.graph),
            )
            WebJobScope.Proposal -> {
                val proposal = session(projectId).proposal
                    ?: throw WebWorkflowFailure("missing-proposal", "A proposal preview is required for proposal-scoped semantic work.")
                val previewGraph = proposal.preview?.graph
                    ?: throw WebWorkflowFailure("missing-proposal-preview", "The proposal has no preview graph.")
                WorkflowGraphSnapshot(
                    graph = previewGraph,
                    graphFingerprint = webGraphFingerprint(previewGraph),
                    proposalFingerprint = webProposalFingerprint(proposal.id, previewGraph),
                )
            }
        }
    }

    @Synchronized
    public fun stage(projectId: String, request: WebStageChangeRequest, userId: String): WebStagingResponse {
        val session = session(projectId)
        val key = request.idempotencyKey
        if (key != null) {
            when (val decision = session.idempotency.begin(key, request.toString())) {
                is IdempotencyDecision.Replay -> return response(projectId, session)
                is IdempotencyDecision.Conflict -> throw WebWorkflowFailure("idempotency-conflict", "The idempotency key was already used for another stage request.")
                is IdempotencyDecision.Accepted -> Unit
            }
        }

        val project = load(projectId)
        val operation = request.toOperation(project)
        val id = "stage-${session.nextOrder++}"
        val staged = StagedChange(
            id = id,
            order = session.nextOrder - 1,
            targetSourceId = request.sourceId,
            summary = request.summary(),
            operation = operation,
            normalizedValues = request.normalizedValues(),
            generatedIris = request.generatedIris(),
        )
        session.entries += StoredEntry(staged, userId, userId, request.comment, request.aiGenerated)
        session.proposal = null
        return response(projectId, session)
    }

    @Synchronized
    public fun discard(projectId: String, stagedId: String): WebStagingResponse {
        val session = session(projectId)
        if (session.entries.removeIf { it.staged.id == stagedId }.not()) {
            throw WebWorkflowFailure("unknown-staged-change", "Staged change '$stagedId' was not found.")
        }
        session.proposal = null
        return response(projectId, session)
    }

    @Synchronized
    public fun preview(projectId: String, userId: String): WebStagingResponse {
        val session = session(projectId)
        val project = load(projectId)
        val normalized = when (val result = normalizer.normalize(StagedChangeSet(session.entries.map { it.staged }))) {
            is EntioResult.Failure -> throw WebWorkflowFailure("staging-invalid", result.message)
            is EntioResult.Success -> result.value
        }
        val changeSet = normalized.changeSet ?: throw WebWorkflowFailure("empty-staged-set", "At least one staged change is required.")
        if (normalized.conflicts.isNotEmpty()) {
            throw WebWorkflowFailure("staging-conflict", normalized.conflicts.joinToString { it.message })
        }
        val targetSourceId = normalized.entries.map(StagedChange::targetSourceId).distinct().singleOrNull()
            ?: throw WebWorkflowFailure("multiple-sources-unsupported", "Single-client staging currently targets one ontology source per proposal.")
        val proposal = when (val result = proposalCreator.createProposal(project, targetSourceId, changeSet, "proposal-${UUID.randomUUID()}", "Web staged ontology changes")) {
            is EntioResult.Failure -> throw WebWorkflowFailure("proposal-preview-failed", result.message)
            is EntioResult.Success -> result.value
        }
        val validation = proposalValidator.validateProposal(proposal, project)
        if (!validation.ok) {
            session.proposal = proposal.copy(status = ChangeProposalStatus.VerificationFailed, validationReport = validation)
            throw WebWorkflowFailure("proposal-invalid", validation.issues.joinToString { it.message })
        }
        session.proposal = proposal.copy(status = ChangeProposalStatus.ReadyForReview, validationReport = validation, diff = graphDiffer.diff(project.graph, proposal.preview!!.graph))
        return response(projectId, session, "Proposal prepared by $userId and is ready for review.")
    }

    @Synchronized
    public fun approve(projectId: String, userId: String): WebStagingResponse {
        val session = session(projectId)
        val proposal = session.proposal ?: throw WebWorkflowFailure("missing-proposal", "Preview the staged changes before approval.")
        if (proposal.validationReport?.ok != true || proposal.status != ChangeProposalStatus.ReadyForReview) {
            throw WebWorkflowFailure("proposal-not-approvable", "Only a current, valid proposal can be approved.")
        }
        session.proposal = proposal.copy(status = ChangeProposalStatus.Approved, review = com.entio.core.ProposalReview(userId, "Approved through the web workbench."))
        return response(projectId, session, "Proposal approved by $userId.")
    }

    @Synchronized
    public fun reject(projectId: String, userId: String): WebStagingResponse {
        val session = session(projectId)
        if (session.proposal == null) throw WebWorkflowFailure("missing-proposal", "There is no proposal to reject.")
        session.proposal = null
        return response(projectId, session, "Proposal rejected by $userId; staged changes remain available for correction.")
    }

    @Synchronized
    public fun apply(projectId: String, userId: String): WebStagingResponse {
        val session = session(projectId)
        val proposal = session.proposal ?: throw WebWorkflowFailure("missing-proposal", "There is no proposal to apply.")
        if (proposal.status != ChangeProposalStatus.Approved) throw WebWorkflowFailure("proposal-not-approved", "Only an approved proposal can be applied.")
        val result = proposalApplier.applyProposal(projectRegistry.rootFor(projectId), proposal)
        return when (result) {
            is com.entio.core.ApplyProposalResult.Applied -> {
                session.entries.clear()
                session.proposal = proposal.copy(status = ChangeProposalStatus.Applied)
                response(projectId, session, "Proposal applied by $userId; source was reloaded.")
            }
            is com.entio.core.ApplyProposalResult.Failed -> {
                session.proposal = proposal.copy(status = ChangeProposalStatus.ApplyFailed)
                response(projectId, session, result.reason)
            }
        }
    }

    private fun session(projectId: String): ProjectSession = sessions.getOrPut(projectId) { ProjectSession() }

    private fun load(projectId: String): EntioProject {
        val result = projectLoader.loadProject(projectRegistry.rootFor(projectId))
        return when (result) {
            is EntioResult.Failure -> throw WebWorkflowFailure("project-load-failed", result.message)
            is EntioResult.Success -> result.value
        }
    }

    private fun response(projectId: String, session: ProjectSession, message: String? = null): WebStagingResponse {
        val proposal = session.proposal
        return WebStagingResponse(
            projectId = projectId,
            status = when {
                proposal?.status == ChangeProposalStatus.Applied -> "APPLIED"
                proposal != null -> proposal.status.name.uppercase()
                session.entries.isEmpty() -> StagedChangeSetStatus.Empty.name.uppercase()
                else -> StagedChangeSetStatus.Ready.name.uppercase()
            },
            entries = session.entries.map { entry ->
                WebStagedEntry(
                    id = entry.staged.id,
                    order = entry.staged.order,
                    sourceId = entry.staged.targetSourceId,
                    summary = entry.staged.summary,
                    editType = entry.staged.operation::class.simpleName.orEmpty(),
                    status = entry.staged.status.name.uppercase(),
                    authorId = entry.authorId,
                    latestEditorId = entry.latestEditorId,
                    comment = entry.comment,
                    aiGenerated = entry.aiGenerated,
                    normalizedValues = entry.staged.normalizedValues,
                    generatedIris = entry.staged.generatedIris.map(GeneratedIri::iri).map(Iri::value),
                    validationMessages = entry.staged.validationReport?.issues?.map { it.message }.orEmpty(),
                )
            },
            proposal = proposal?.let { current ->
                WebProposalState(
                    id = current.id,
                    status = current.status.name.uppercase(),
                    stagedChangeIds = session.entries.map { it.staged.id },
                    baselineProjectFingerprint = current.baseline.projectFingerprint,
                    validationMessages = current.validationReport?.issues?.map { it.message }.orEmpty(),
                    diff = current.diff?.entries?.map { entry -> WebDiffEntry(entry.kind.name, entry.subject.value, entry.predicate?.value, entry.objectValue, entry.description) }.orEmpty(),
                    message = message,
                )
            },
        )
    }

    private fun WebStageChangeRequest.toOperation(project: EntioProject): StagedChangeOperation {
        if (editType == "delete") {
            val target = project.symbols.firstOrNull { symbol -> targetIri == symbol.iri.value || (targetIri == null && targetLabel == symbol.label) }
                ?: throw WebWorkflowFailure("unknown-delete-target", "The deletion target does not exist.")
            val ontology = project.ontologies.firstOrNull { it.source.id == sourceId }
                ?: throw WebWorkflowFailure("unknown-source", "Ontology source '$sourceId' was not found.")
            val candidate = EntityCandidate(target.iri, target.label, target.kind, target.sourceId)
            val plan = deletionAnalyzer.analyze(ontology, candidate, selectedDependencyKeys = dependencyKeys)
            return StagedChangeOperation.Delete(plan)
        }
        return StagedChangeOperation.TypedEdit(toTypedEdit())
    }

    private fun WebStageChangeRequest.toTypedEdit(): TypedOntologyEdit = when (editType) {
        "create-class" -> CreateClassEdit(requiredIri(classIri, "classIri"), label?.let(::literal))
        "set-entity-label" -> SetEntityLabelEdit(requiredResource(resourceIri ?: targetIri, "resourceIri"), requiredLiteral(label, "label"))
        "add-superclass" -> AddSuperclassEdit(requiredIri(classIri, "classIri"), requiredIri(superclassIri, "superclassIri"))
        "remove-superclass" -> com.entio.core.RemoveSuperclassEdit(requiredIri(classIri, "classIri"), requiredIri(superclassIri, "superclassIri"))
        "create-object-property" -> CreateObjectPropertyEdit(requiredIri(propertyIri, "propertyIri"), label?.let(::literal))
        "create-datatype-property" -> CreateDatatypePropertyEdit(requiredIri(propertyIri, "propertyIri"), label?.let(::literal))
        "set-property-domain" -> SetPropertyDomainEdit(requiredIri(propertyIri, "propertyIri"), requiredIri(domainClassIri, "domainClassIri"))
        "set-property-range" -> SetPropertyRangeEdit(requiredIri(propertyIri, "propertyIri"), requiredIri(rangeIri, "rangeIri"))
        "create-individual" -> CreateIndividualEdit(requiredIri(individualIri, "individualIri"), classIri?.let(::Iri))
        "assign-type" -> AssignTypeEdit(requiredResource(resourceIri, "resourceIri"), requiredIri(typeIri, "typeIri"))
        "add-object-property-assertion" -> AddObjectPropertyAssertionEdit(requiredResource(subjectIri, "subjectIri"), requiredIri(propertyIri, "propertyIri"), requiredResource(objectIri, "objectIri"))
        "add-datatype-property-assertion" -> AddDatatypePropertyAssertionEdit(requiredResource(subjectIri, "subjectIri"), requiredIri(propertyIri, "propertyIri"), requiredLiteral(value, "value"))
        else -> throw WebWorkflowFailure("unsupported-edit-type", "Edit type '$editType' is not supported by the web boundary.")
    }

    private fun WebStageChangeRequest.normalizedValues(): Map<String, String> = sequenceOf(
        "classIri" to classIri, "superclassIri" to superclassIri, "propertyIri" to propertyIri,
        "domainClassIri" to domainClassIri, "rangeIri" to rangeIri, "individualIri" to individualIri,
        "resourceIri" to (resourceIri ?: targetIri), "typeIri" to typeIri, "subjectIri" to subjectIri,
        "objectIri" to objectIri, "targetIri" to targetIri, "label" to label, "value" to value,
    ).mapNotNull { (key, current) -> current?.takeIf(String::isNotBlank)?.let { key to it } }.toMap()

    private fun WebStageChangeRequest.generatedIris(): List<GeneratedIri> = normalizedValues().filterKeys { it.endsWith("Iri", ignoreCase = true) }.values.map { value ->
        GeneratedIri(Iri(value), value.substringAfterLast('#').substringAfterLast('/'), com.entio.core.IriCollisionOutcome.New, "web-explicit-iri-v1")
    }

    private fun WebStageChangeRequest.summary(): String = "$editType on ${targetIri ?: classIri ?: propertyIri ?: individualIri ?: subjectIri ?: "target"}"

    private fun requiredIri(value: String?, field: String): Iri = value?.takeIf(String::isNotBlank)?.let(::Iri) ?: throw WebWorkflowFailure("missing-field", "Field '$field' is required.")
    private fun requiredResource(value: String?, field: String): RdfResource = requiredIri(value, field)
    private fun requiredLiteral(value: String?, field: String): RdfLiteral = value?.takeIf(String::isNotBlank)?.let(::literal) ?: throw WebWorkflowFailure("missing-field", "Field '$field' is required.")
    private fun literal(value: String): RdfLiteral = RdfLiteral(value, datatypeIri = datatypeIri, languageTag = null)

    private companion object {
        private val datatypeIri = Iri("http://www.w3.org/2001/XMLSchema#string")
    }
}
