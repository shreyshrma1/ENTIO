package com.entio.web

import com.entio.core.AddDatatypePropertyAssertionEdit
import com.entio.core.AddObjectPropertyAssertionEdit
import com.entio.core.AddSuperclassEdit
import com.entio.core.AssignTypeEdit
import com.entio.core.ChangeProposal
import com.entio.core.ChangeProposalStatus
import com.entio.core.ExternalProposalIntent
import com.entio.core.CreateClassEdit
import com.entio.core.CreateDatatypePropertyEdit
import com.entio.core.CreateIndividualEdit
import com.entio.core.CreateObjectPropertyEdit
import com.entio.core.EntityCandidate
import com.entio.core.EntityResolutionResult
import com.entio.core.EntitySelector
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.GeneratedIri
import com.entio.core.Iri
import com.entio.core.MultiSourceApplyStatus
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.ShaclPath
import com.entio.core.SetEntityLabelEdit
import com.entio.core.SetPropertyDomainEdit
import com.entio.core.SetPropertyRangeEdit
import com.entio.core.StagedChange
import com.entio.core.StagedChangeOperation
import com.entio.core.StagedChangeSetStatus
import com.entio.core.SymbolKind
import com.entio.core.TypedOntologyEdit
import com.entio.diff.GraphDiffer
import com.entio.semantic.DeletionDependencyAnalyzer
import com.entio.semantic.DeterministicIriGenerator
import com.entio.semantic.ExternalProposalIntentTranslator
import com.entio.semantic.LabelResolver
import com.entio.semantic.MultiSourceAtomicApplier
import com.entio.semantic.ProjectLoader
import com.entio.semantic.ProposalCreator
import com.entio.semantic.StagedChangeSetNormalizer
import com.entio.web.contract.IdempotencyDecision
import com.entio.web.contract.InMemoryIdempotencyStore
import com.entio.web.contract.ProjectRegistry
import com.entio.web.contract.WebProposalState
import com.entio.web.contract.WebShaclFindingSummary
import com.entio.web.contract.WebShaclImpact
import com.entio.web.contract.WebStageChangeRequest
import com.entio.web.contract.WebStagedEntry
import com.entio.web.contract.WebStagingResponse
import com.entio.web.contract.WebDiffEntry

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
    var preparedProposal: PreparedWebProposal? = null,
    val idempotency: InMemoryIdempotencyStore = InMemoryIdempotencyStore(),
    var nextOrder: Int = 1,
)

private data class PreparedStageRequest(
    val request: WebStageChangeRequest,
    val resolvedCandidates: List<EntityCandidate>,
    val generatedIris: List<GeneratedIri>,
)

/** Server-owned single-client staging state that delegates semantic work to Kotlin services. */
public class StagingWorkflowService(
    private val projectRegistry: ProjectRegistry,
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val normalizer: StagedChangeSetNormalizer = StagedChangeSetNormalizer(),
    private val proposalCreator: ProposalCreator = ProposalCreator(),
    private val proposalValidator: com.entio.validation.ProposalValidator = com.entio.validation.ProposalValidator(),
    private val graphDiffer: GraphDiffer = GraphDiffer(),
    private val deletionAnalyzer: DeletionDependencyAnalyzer = DeletionDependencyAnalyzer(),
    private val externalIntentTranslator: ExternalProposalIntentTranslator = ExternalProposalIntentTranslator(),
    private val labelResolver: LabelResolver = LabelResolver(),
    private val iriGenerator: DeterministicIriGenerator = DeterministicIriGenerator(),
    private val additionalPostApplyVerification: (String, EntioProject) -> EntioResult<Unit> = { _, _ -> EntioResult.Success(Unit) },
) {
    private val sessions: MutableMap<String, ProjectSession> = linkedMapOf()
    private val proposalPlanner: WebProposalPlanner = WebProposalPlanner(
        normalizer = normalizer,
        proposalCreator = proposalCreator,
        proposalValidator = proposalValidator,
        graphDiffer = graphDiffer,
    )
    private val shaclStagePreparer: WebShaclStagePreparer = WebShaclStagePreparer(
        labelResolver = labelResolver,
        iriGenerator = iriGenerator,
    )

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
        val requestedSource = project.resolvedSources.firstOrNull { it.id == request.sourceId }
            ?: throw WebWorkflowFailure("unknown-source", "Ontology source '${request.sourceId}' was not found.")
        if (!request.isShaclEdit() && com.entio.core.ShaclGraphRole.Ontology !in requestedSource.roles) {
            throw WebWorkflowFailure("invalid-ontology-source-role", "Ontology edits require a source with the ontology role.")
        }
        val id = "stage-${session.nextOrder++}"
        val staged = if (request.isShaclEdit()) {
            val prepared = shaclStagePreparer.prepare(project, request)
            StagedChange(
                id = id,
                order = session.nextOrder - 1,
                targetSourceId = request.sourceId,
                summary = prepared.summary,
                operation = StagedChangeOperation.ShaclEdit(prepared.edit),
                normalizedValues = prepared.normalizedValues,
                resolvedCandidates = prepared.resolvedCandidates,
                generatedIris = prepared.generatedIris,
            )
        } else {
            val prepared = request.prepare(project)
            StagedChange(
                id = id,
                order = session.nextOrder - 1,
                targetSourceId = request.sourceId,
                summary = request.summary(),
                operation = prepared.request.toOperation(project),
                normalizedValues = prepared.request.normalizedValues(),
                resolvedCandidates = prepared.resolvedCandidates,
                generatedIris = prepared.generatedIris,
            )
        }
        session.entries += StoredEntry(staged, userId, userId, request.comment, request.aiGenerated)
        session.clearPreparedProposal()
        return response(projectId, session)
    }

    @Synchronized
    public fun stageExternal(
        projectId: String,
        sourceId: String,
        targetOntologyIri: Iri,
        intent: ExternalProposalIntent,
        summary: String,
        userId: String,
        idempotencyKey: String? = null,
    ): WebStagingResponse {
        val session = session(projectId)
        if (idempotencyKey != null) {
            when (val decision = session.idempotency.begin(idempotencyKey, intent.toString())) {
                is IdempotencyDecision.Replay -> return response(projectId, session)
                is IdempotencyDecision.Conflict -> throw WebWorkflowFailure("idempotency-conflict", "The idempotency key was already used for another stage request.")
                is IdempotencyDecision.Accepted -> Unit
            }
        }
        val project = load(projectId)
        if (project.ontologies.none { it.source.id == sourceId }) {
            throw WebWorkflowFailure("unknown-source", "Ontology source '$sourceId' was not found.")
        }
        val changeSet = when (val translated = externalIntentTranslator.translate(intent, targetOntologyIri)) {
            is EntioResult.Failure -> throw WebWorkflowFailure("external-proposal-invalid", translated.issues.joinToString { it.message })
            is EntioResult.Success -> translated.value
        }
        val id = "stage-${session.nextOrder++}"
        session.entries += StoredEntry(
            staged = StagedChange(
                id = id,
                order = session.nextOrder - 1,
                targetSourceId = sourceId,
                summary = summary,
                operation = StagedChangeOperation.GraphChanges(changeSet),
                normalizedValues = mapOf("externalSource" to intent.sourceId, "targetOntologyIri" to targetOntologyIri.value),
            ),
            authorId = userId,
            latestEditorId = userId,
            comment = "External ontology reuse staged for review.",
            aiGenerated = false,
        )
        session.clearPreparedProposal()
        return response(projectId, session)
    }

    @Synchronized
    public fun discard(projectId: String, stagedId: String): WebStagingResponse {
        val session = session(projectId)
        if (session.entries.removeIf { it.staged.id == stagedId }.not()) {
            throw WebWorkflowFailure("unknown-staged-change", "Staged change '$stagedId' was not found.")
        }
        session.clearPreparedProposal()
        return response(projectId, session)
    }

    @Synchronized
    public fun preview(projectId: String, userId: String): WebStagingResponse {
        val session = session(projectId)
        val project = load(projectId)
        val prepared = proposalPlanner.prepare(project, session.entries.map(StoredEntry::staged))
        session.preparedProposal = prepared
        session.proposal = prepared.proposal
        if (prepared.proposal.validationReport?.ok != true) {
            return response(projectId, session, "Proposal preview is invalid and cannot be approved until its findings are resolved.")
        }
        return response(projectId, session, "Proposal prepared by $userId and is ready for review.")
    }

    @Synchronized
    public fun approve(projectId: String, userId: String): WebStagingResponse {
        val session = session(projectId)
        val proposal = session.proposal ?: throw WebWorkflowFailure("missing-proposal", "Preview the staged changes before approval.")
        if (proposal.validationReport?.ok != true || proposal.status != ChangeProposalStatus.ReadyForReview) {
            throw WebWorkflowFailure("proposal-not-approvable", "Only a current, valid proposal can be approved.")
        }
        val currentProject = load(projectId)
        val isCurrent = when (val result = proposalCreator.isCurrent(proposal, currentProject)) {
            is EntioResult.Failure -> throw WebWorkflowFailure("proposal-current-check-failed", result.message)
            is EntioResult.Success -> result.value
        }
        if (!isCurrent) throw WebWorkflowFailure("stale-proposal-baseline", "The proposal baseline no longer matches the current project.")
        session.proposal = proposal.copy(status = ChangeProposalStatus.Approved, review = com.entio.core.ProposalReview(userId, "Approved through the web workbench."))
        return response(projectId, session, "Proposal approved by $userId.")
    }

    @Synchronized
    public fun reject(projectId: String, userId: String): WebStagingResponse {
        val session = session(projectId)
        if (session.proposal == null) throw WebWorkflowFailure("missing-proposal", "There is no proposal to reject.")
        session.clearPreparedProposal()
        return response(projectId, session, "Proposal rejected by $userId; staged changes remain available for correction.")
    }

    @Synchronized
    public fun apply(projectId: String, userId: String): WebStagingResponse {
        val session = session(projectId)
        val proposal = session.proposal ?: throw WebWorkflowFailure("missing-proposal", "There is no proposal to apply.")
        if (proposal.status != ChangeProposalStatus.Approved) throw WebWorkflowFailure("proposal-not-approved", "Only an approved proposal can be applied.")
        val prepared = session.preparedProposal
            ?: throw WebWorkflowFailure("missing-prepared-application", "Preview the staged changes again before applying.")
        val result = MultiSourceAtomicApplier(
            postSaveVerification = {
                val reloaded = try {
                    load(projectId)
                } catch (failure: WebWorkflowFailure) {
                    return@MultiSourceAtomicApplier EntioResult.Failure(failure.message ?: "The applied project could not be reloaded.")
                }
                when (val verification = proposalPlanner.verifyAppliedProject(reloaded, prepared.expectedShaclResults)) {
                    is EntioResult.Failure -> verification
                    is EntioResult.Success -> additionalPostApplyVerification(projectId, reloaded)
                }
            },
        ).apply(prepared.targets)
        return when (result.status) {
            MultiSourceApplyStatus.Applied -> {
                session.entries.clear()
                session.proposal = proposal.copy(status = ChangeProposalStatus.Applied)
                session.preparedProposal = null
                response(projectId, session, "Proposal applied by $userId; source was reloaded.")
            }
            MultiSourceApplyStatus.RolledBack,
            MultiSourceApplyStatus.RollbackFailed,
            -> {
                session.proposal = proposal.copy(status = ChangeProposalStatus.RolledBack)
                response(projectId, session, result.reason ?: "Proposal application failed and source files were restored.")
            }
            MultiSourceApplyStatus.Failed -> {
                session.proposal = proposal.copy(status = ChangeProposalStatus.ApplyFailed)
                response(projectId, session, result.reason ?: "Proposal application failed.")
            }
        }
    }

    private fun session(projectId: String): ProjectSession = sessions.getOrPut(projectId) { ProjectSession() }

    private fun ProjectSession.clearPreparedProposal(): Unit {
        proposal = null
        preparedProposal = null
    }

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
                    targetSourceIds = session.preparedProposal?.targets?.map { it.sourceId }.orEmpty(),
                    shaclImpact = session.preparedProposal?.toWebShaclImpact(),
                    message = message,
                )
            },
        )
    }

    private fun PreparedWebProposal.toWebShaclImpact(): WebShaclImpact = WebShaclImpact(
        currentGraphFingerprint = currentShaclFingerprint,
        previewGraphFingerprint = previewShaclFingerprint,
        newFindings = impact.shaclImpact.newResults.map(::webFinding),
        worsenedFindings = impact.shaclImpact.worsenedResults.map(::webFinding),
        unchangedFindings = impact.shaclImpact.unchangedResults.map(::webFinding),
        resolvedFindings = impact.shaclImpact.resolvedResults.map(::webFinding),
    )

    private fun webFinding(result: com.entio.core.ShaclValidationResult): WebShaclFindingSummary = WebShaclFindingSummary(
        resultId = result.resultId,
        severity = result.severity.name,
        message = result.message,
        focusNode = result.focusNode.value,
        path = (result.path as? ShaclPath.DirectProperty)?.propertyIri?.value,
        shapeIri = result.shape.iri.value,
    )

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

    private fun WebStageChangeRequest.prepare(project: EntioProject): PreparedStageRequest {
        val resolved = mutableListOf<EntityCandidate>()
        val generated = mutableListOf<GeneratedIri>()

        fun existing(
            iri: String?,
            label: String?,
            kind: SymbolKind?,
            fieldName: String,
        ): String {
            iri?.takeIf(String::isNotBlank)?.let { return it }
            val selectorLabel = label?.takeIf(String::isNotBlank)
                ?: throw WebWorkflowFailure("missing-field", "A $fieldName label is required.")
            return when (val result = labelResolver.resolve(project.symbols, EntitySelector(label = selectorLabel, kind = kind))) {
                is EntityResolutionResult.Resolved -> {
                    resolved += result.candidate
                    result.candidate.iri.value
                }
                is EntityResolutionResult.Ambiguous -> throw WebWorkflowFailure(
                    "ambiguous-label",
                    "The $fieldName label '$selectorLabel' matches more than one entity.",
                )
                EntityResolutionResult.NotFound -> throw WebWorkflowFailure(
                    "unknown-label",
                    "The $fieldName label '$selectorLabel' does not exist.",
                )
                is EntityResolutionResult.Invalid -> throw WebWorkflowFailure("invalid-label", result.reason)
            }
        }

        fun newEntity(
            iri: String?,
            label: String?,
            kind: SymbolKind,
            fieldName: String,
        ): String {
            iri?.takeIf(String::isNotBlank)?.let { return it }
            val entityLabel = label?.takeIf(String::isNotBlank)
                ?: throw WebWorkflowFailure("missing-field", "A $fieldName label is required.")
            return when (val result = iriGenerator.generate(entityLabel, kind, project.config.iriNamespace, project.symbols)) {
                is EntioResult.Failure -> throw WebWorkflowFailure(
                    result.issues.firstOrNull()?.code ?: "iri-generation-failed",
                    result.message,
                )
                is EntioResult.Success -> {
                    generated += result.value
                    result.value.iri.value
                }
            }
        }

        fun range(): String {
            rangeIri?.takeIf(String::isNotBlank)?.let { return it }
            val value = rangeLabel?.takeIf(String::isNotBlank)
                ?: throw WebWorkflowFailure("missing-field", "A range label is required.")
            standardDatatype(value)?.let { return it }
            return existing(null, value, null, "range")
        }

        val prepared = when (editType) {
            "create-class" -> copy(classIri = newEntity(classIri, label, SymbolKind.Class, "class"))
            "set-entity-label" -> copy(resourceIri = existing(resourceIri ?: targetIri, resourceLabel ?: targetLabel, null, "entity"))
            "add-superclass", "remove-superclass" -> copy(
                classIri = existing(classIri, classLabel, SymbolKind.Class, "class"),
                superclassIri = existing(superclassIri, superclassLabel, SymbolKind.Class, "superclass"),
            )
            "create-object-property", "create-datatype-property" -> copy(
                propertyIri = newEntity(propertyIri, label, SymbolKind.Property, "property"),
            )
            "set-property-domain" -> copy(
                propertyIri = existing(propertyIri, propertyLabel, SymbolKind.Property, "property"),
                domainClassIri = existing(domainClassIri, domainClassLabel, SymbolKind.Class, "domain class"),
            )
            "set-property-range" -> copy(
                propertyIri = existing(propertyIri, propertyLabel, SymbolKind.Property, "property"),
                rangeIri = range(),
            )
            "create-individual" -> copy(
                individualIri = newEntity(individualIri, label ?: individualLabel, SymbolKind.Individual, "individual"),
                classIri = if (classIri.isNullOrBlank() && classLabel.isNullOrBlank()) null else existing(classIri, classLabel, SymbolKind.Class, "class"),
            )
            "assign-type" -> copy(
                resourceIri = existing(resourceIri, resourceLabel, null, "resource"),
                typeIri = existing(typeIri, typeLabel, SymbolKind.Class, "type"),
            )
            "add-object-property-assertion" -> copy(
                subjectIri = existing(subjectIri, subjectLabel, null, "subject"),
                propertyIri = existing(propertyIri, propertyLabel, SymbolKind.Property, "property"),
                objectIri = existing(objectIri, objectLabel, null, "object"),
            )
            "add-datatype-property-assertion" -> copy(
                subjectIri = existing(subjectIri, subjectLabel, null, "subject"),
                propertyIri = existing(propertyIri, propertyLabel, SymbolKind.Property, "property"),
            )
            else -> this
        }
        return PreparedStageRequest(prepared, resolved, generated)
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
        "classLabel" to classLabel, "superclassLabel" to superclassLabel, "propertyLabel" to propertyLabel,
        "domainClassLabel" to domainClassLabel, "rangeLabel" to rangeLabel, "individualLabel" to individualLabel,
        "resourceLabel" to resourceLabel, "typeLabel" to typeLabel, "subjectLabel" to subjectLabel,
        "objectLabel" to objectLabel,
    ).mapNotNull { (key, current) -> current?.takeIf(String::isNotBlank)?.let { key to it } }.toMap()

    private fun WebStageChangeRequest.summary(): String = "$editType · ${targetLabel ?: label ?: classLabel ?: propertyLabel ?: individualLabel ?: resourceLabel ?: subjectLabel ?: targetIri ?: classIri ?: propertyIri ?: individualIri ?: subjectIri ?: "target"}"

    private fun standardDatatype(label: String): String? = STANDARD_DATATYPES[label.trim().lowercase()]

    private fun requiredIri(value: String?, field: String): Iri = value?.takeIf(String::isNotBlank)?.let(::Iri) ?: throw WebWorkflowFailure("missing-field", "Field '$field' is required.")
    private fun requiredResource(value: String?, field: String): RdfResource = requiredIri(value, field)
    private fun requiredLiteral(value: String?, field: String): RdfLiteral = value?.takeIf(String::isNotBlank)?.let(::literal) ?: throw WebWorkflowFailure("missing-field", "Field '$field' is required.")
    private fun literal(value: String): RdfLiteral = RdfLiteral(value, datatypeIri = datatypeIri, languageTag = null)

    private companion object {
        private val datatypeIri = Iri("http://www.w3.org/2001/XMLSchema#string")
        private val STANDARD_DATATYPES: Map<String, String> = mapOf(
            "string" to "http://www.w3.org/2001/XMLSchema#string",
            "integer" to "http://www.w3.org/2001/XMLSchema#integer",
            "decimal" to "http://www.w3.org/2001/XMLSchema#decimal",
            "boolean" to "http://www.w3.org/2001/XMLSchema#boolean",
            "date" to "http://www.w3.org/2001/XMLSchema#date",
            "datetime" to "http://www.w3.org/2001/XMLSchema#dateTime",
        )
    }
}
