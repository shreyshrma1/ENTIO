package com.entio.web.ingestion

import com.entio.core.AddDatatypePropertyAssertionEdit
import com.entio.core.AddObjectPropertyAssertionEdit
import com.entio.core.AddSuperclassEdit
import com.entio.core.AppliedDocumentApplyEvent
import com.entio.core.AppliedDocumentDecision
import com.entio.core.AppliedDocumentEvidence
import com.entio.core.AppliedDocumentIdentity
import com.entio.core.AppliedDocumentIndividualConfirmation
import com.entio.core.AppliedDocumentProvenance
import com.entio.core.AppliedDocumentReviewOnlyFinding
import com.entio.core.AppliedDocumentTypedOperation
import com.entio.core.AssignTypeEdit
import com.entio.core.CreateClassEdit
import com.entio.core.CreateDatatypePropertyEdit
import com.entio.core.CreateIndividualEdit
import com.entio.core.CreateObjectPropertyEdit
import com.entio.core.DocumentRecommendation
import com.entio.core.DocumentReviewDecision
import com.entio.core.DocumentAnalysisPipelineVersions
import com.entio.core.DocumentAnalysisStageRecord
import com.entio.core.DocumentCoverageDisposition
import com.entio.core.DocumentEvidence
import com.entio.core.DocumentFinalRecommendation
import com.entio.core.DocumentGroupedRecommendationDecision
import com.entio.core.Iri
import com.entio.core.RemovePropertyDomainEdit
import com.entio.core.RemovePropertyRangeEdit
import com.entio.core.RemoveSuperclassEdit
import com.entio.core.SetEntityLabelEdit
import com.entio.core.SetPropertyDomainEdit
import com.entio.core.SetPropertyRangeEdit
import com.entio.core.StagedChange
import com.entio.core.StagedChangeOperation
import com.entio.core.TypedOntologyEdit
import com.entio.core.DocumentRecommendationReviewStatus
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant

internal interface DocumentApplyHooks {
    fun begin(
        projectId: String,
        proposalId: String,
        baselineFingerprint: String,
        expectedFingerprint: String,
        staged: List<StagedChange>,
        appliedByUserId: String,
    )

    fun commit(projectId: String)
    fun rolledBack(projectId: String)
}

private data class DocumentProvenanceTemplate(
    val taskId: String,
    val recommendation: DocumentRecommendation,
    val decision: DocumentReviewDecision,
    val documents: List<DocumentIngestionDocumentSnapshot>,
    val blocks: Map<String, com.entio.core.LocatedDocumentTextBlock>,
)

internal data class ConnectedDocumentProvenanceCandidate(
    val taskId: String,
    val recommendation: DocumentFinalRecommendation,
    val decision: DocumentGroupedRecommendationDecision,
    val documents: List<DocumentIngestionDocumentSnapshot>,
    val blocks: Map<String, com.entio.core.LocatedDocumentTextBlock>,
    val evidence: Map<String, DocumentEvidence>,
    val workKey: String,
    val modelId: String,
    val analysisStages: List<DocumentAnalysisStageRecord>,
    val coverage: List<DocumentCoverageDisposition>,
)

private data class ConnectedDocumentProvenanceTemplate(
    val taskId: String,
    val recommendation: DocumentFinalRecommendation,
    val decision: DocumentGroupedRecommendationDecision,
    val documents: List<DocumentIngestionDocumentSnapshot>,
    val blocks: Map<String, com.entio.core.LocatedDocumentTextBlock>,
    val evidence: Map<String, DocumentEvidence>,
    val workKey: String,
    val modelId: String,
    val analysisStages: List<DocumentAnalysisStageRecord>,
    val coverage: List<DocumentCoverageDisposition>,
)

internal class DocumentApplyProvenanceCoordinator(
    private val repository: AppliedDocumentProvenanceRepository,
    private val clock: Clock = Clock.systemUTC(),
) : DocumentApplyHooks {
    private val templates: MutableMap<String, MutableMap<String, DocumentProvenanceTemplate>> = linkedMapOf()
    private val connectedTemplates:
        MutableMap<String, MutableMap<String, ConnectedDocumentProvenanceTemplate>> = linkedMapOf()

    @Synchronized
    fun register(projectId: String, taskId: String, candidates: List<DocumentReviewDraftCandidate>): Unit {
        val projectTemplates = templates.getOrPut(projectId) { linkedMapOf() }
        candidates.forEach { candidate ->
            projectTemplates[candidate.recommendation.id] = DocumentProvenanceTemplate(
                taskId,
                candidate.recommendation,
                candidate.decision,
                candidate.documents,
                candidate.blocks,
            )
        }
    }

    @Synchronized
    fun registerConnected(projectId: String, candidates: List<ConnectedDocumentProvenanceCandidate>): Unit {
        val projectTemplates = connectedTemplates.getOrPut(projectId) { linkedMapOf() }
        candidates.forEach { candidate ->
            projectTemplates[candidate.recommendation.id] = ConnectedDocumentProvenanceTemplate(
                taskId = candidate.taskId,
                recommendation = candidate.recommendation,
                decision = candidate.decision,
                documents = candidate.documents,
                blocks = candidate.blocks,
                evidence = candidate.evidence,
                workKey = candidate.workKey,
                modelId = candidate.modelId,
                analysisStages = candidate.analysisStages,
                coverage = candidate.coverage,
            )
        }
    }

    @Synchronized
    fun retainReviewOnly(
        projectId: String,
        candidate: VerifiedDocumentReviewOnlyCandidate,
        modelId: String,
    ): Int {
        val recommendation = candidate.recommendation
        val reviewPlan = candidate.reviewPlan
        val evidenceGroups = recommendation.evidenceIds.map { evidenceId ->
            reviewPlan.evidence[evidenceId.value]
                ?: throw DocumentIngestionFailure(
                    "document-provenance-evidence-stale",
                    "Review-only provenance evidence is no longer available.",
                )
        }
        val prompts = reviewPlan.analysisStages.mapNotNull(DocumentAnalysisStageRecord::promptVersion)
            .distinct().sorted()
        val inputHashes = reviewPlan.analysisStages.mapNotNull(DocumentAnalysisStageRecord::inputSha256)
            .distinct().sorted()
        val outputHashes = reviewPlan.analysisStages.mapNotNull(DocumentAnalysisStageRecord::outputSha256)
            .distinct().sorted()
        val coverageIds = reviewPlan.plan.plan.coverage
            .filter { it.recommendationId == recommendation.id }
            .map(DocumentCoverageDisposition::discoveryId)
            .distinct().sorted()
        val findings = recommendation.reviewOnlyFindings.map { finding ->
            AppliedDocumentReviewOnlyFinding(
                findingId = finding.id,
                summary = finding.summary,
                reason = finding.reason,
                evidenceIds = finding.evidenceIds,
            )
        }.sortedBy(AppliedDocumentReviewOnlyFinding::findingId)
        val appliedAt = Instant.now(clock)
        val records = evidenceGroups.flatMap(DocumentEvidence::references)
            .groupBy { it.documentId.value }
            .map { (documentId, documentEvidence) ->
                val document = reviewPlan.taskDocuments.singleOrNull { it.documentId == documentId }
                    ?: throw DocumentIngestionFailure(
                        "document-provenance-document-missing",
                        "A review-only provenance document is unavailable.",
                    )
                val evidence = documentEvidence.map { reference ->
                    val block = reviewPlan.blocks[reference.blockId.value]
                        ?: throw DocumentIngestionFailure(
                            "document-provenance-evidence-stale",
                            "Review-only provenance evidence is stale.",
                        )
                    AppliedDocumentEvidence(
                        evidenceId = reference.id,
                        documentId = reference.documentId,
                        pageNumber = reference.pageNumber,
                        blockId = reference.blockId,
                        startOffsetInBlock = reference.startOffsetInBlock,
                        endOffsetInBlock = reference.endOffsetInBlock,
                        exactExcerpt = reference.exactExcerpt,
                        extractionMethod = reference.extractionMethod,
                        extractorVersion = block.extractorVersion,
                        confidence = reference.ocrConfidence ?: recommendation.confidence.overall,
                    )
                }
                AppliedDocumentProvenance(
                    recordId = recordId(
                        projectId,
                        "documented-rule",
                        recommendation.id,
                        document.checksumSha256,
                        evidence.joinToString("\u0000") { it.evidenceId.value },
                        recommendation.id,
                        candidate.decision.decisionId,
                        reviewPlan.graphFingerprint,
                    ),
                    projectId = projectId,
                    taskId = com.entio.core.DocumentTaskId(candidate.taskId),
                    document = AppliedDocumentIdentity(
                        documentId = com.entio.core.DocumentId(document.documentId),
                        checksumSha256 = document.checksumSha256,
                        safeFilename = document.safeFilename,
                    ),
                    evidence = evidence,
                    recommendationId = recommendation.id,
                    action = com.entio.core.DocumentRecommendationAction.Confirm,
                    decision = AppliedDocumentDecision(
                        decisionId = candidate.decision.decisionId,
                        recommendationId = recommendation.id,
                        actorUserId = candidate.decision.actorUserId,
                        decidedAt = candidate.decision.decidedAt,
                        status = DocumentRecommendationReviewStatus.Drafted,
                        clarification = candidate.decision.clarification,
                    ),
                    modelId = modelId,
                    promptVersion = DocumentAnalysisPipelineVersions.SEMANTIC_PLAN_PROMPT,
                    confidence = recommendation.confidence.overall,
                    evidenceTypes = evidenceGroups.map(DocumentEvidence::type).distinct().sortedBy { it.name },
                    typedOperation = null,
                    applyEvent = AppliedDocumentApplyEvent(
                        proposalId = null,
                        appliedByUserId = candidate.decision.actorUserId,
                        appliedAt = appliedAt,
                        baselineOntologyFingerprint = reviewPlan.graphFingerprint,
                        resultingOntologyFingerprint = reviewPlan.graphFingerprint,
                    ),
                    analysisWorkKey = reviewPlan.workKey,
                    promptVersions = prompts,
                    stageInputHashes = inputHashes,
                    stageOutputHashes = outputHashes,
                    confidenceDimensions = recommendation.confidence,
                    criticDispositionIds = recommendation.criticDispositions.map { it.findingId }.distinct().sorted(),
                    coverageDispositionIds = coverageIds,
                    relatedReviewOnlyFindings = findings,
                )
            }.sortedBy(AppliedDocumentProvenance::recordId)
        if (records.isEmpty()) {
            throw DocumentIngestionFailure(
                "document-provenance-evidence-stale",
                "Review-only provenance requires exact document evidence.",
            )
        }
        repository.save(projectId, records)
        return records.size
    }

    @Synchronized
    override fun begin(
        projectId: String,
        proposalId: String,
        baselineFingerprint: String,
        expectedFingerprint: String,
        staged: List<StagedChange>,
        appliedByUserId: String,
    ) {
        val documentEntries = staged.filter { it.documentDraftProvenance != null }
        if (documentEntries.isEmpty()) return
        val projectTemplates = templates[projectId].orEmpty()
        val projectConnectedTemplates = connectedTemplates[projectId].orEmpty()
        val appliedAt = Instant.now(clock)
        val records = documentEntries.flatMap { entry ->
            val provenance = entry.documentDraftProvenance!!
            val legacy = projectTemplates[provenance.recommendationId]
            val connected = projectConnectedTemplates[provenance.recommendationId]
            when {
                legacy != null -> records(
                    projectId,
                    entry,
                    legacy,
                    proposalId,
                    baselineFingerprint,
                    expectedFingerprint,
                    appliedByUserId,
                    appliedAt,
                )
                connected != null -> connectedRecords(
                    projectId,
                    entry,
                    connected,
                    proposalId,
                    baselineFingerprint,
                    expectedFingerprint,
                    appliedByUserId,
                    appliedAt,
                )
                else -> throw DocumentIngestionFailure(
                    "document-provenance-template-missing",
                    "Document provenance cannot be prepared.",
                )
            }
        } + projectTemplates.values
            .filter { template ->
                template.taskId in documentEntries.mapNotNull { it.documentDraftProvenance?.taskId?.value } &&
                    template.recommendation.action == com.entio.core.DocumentRecommendationAction.Confirm
            }
            .flatMap { template ->
                confirmationRecords(
                    projectId,
                    template,
                    baselineFingerprint,
                    expectedFingerprint,
                    appliedByUserId,
                    appliedAt,
                )
            }
        val orderedRecords = records.sortedBy(AppliedDocumentProvenance::recordId)
        repository.beginPending(
            PendingDocumentProvenance(
                projectId,
                proposalId,
                baselineFingerprint,
                expectedFingerprint,
                orderedRecords,
            ),
        )
    }

    @Synchronized
    fun commitConfirmations(
        projectId: String,
        taskId: String,
        graphFingerprint: String,
        appliedByUserId: String,
    ): Int {
        val projectTemplates = templates[projectId].orEmpty().values
            .filter { it.taskId == taskId && it.recommendation.action == com.entio.core.DocumentRecommendationAction.Confirm }
        if (projectTemplates.isEmpty()) {
            throw DocumentIngestionFailure("document-confirmation-empty", "No accepted confirmations are available.")
        }
        val appliedAt = Instant.now(clock)
        val records = projectTemplates.flatMap {
            confirmationRecords(projectId, it, graphFingerprint, graphFingerprint, appliedByUserId, appliedAt)
        }.sortedBy(AppliedDocumentProvenance::recordId)
        repository.beginPending(
            PendingDocumentProvenance(
                projectId,
                "confirm-$taskId",
                graphFingerprint,
                graphFingerprint,
                records,
            ),
        )
        repository.commitPending(projectId)
        return records.size
    }

    @Synchronized
    override fun commit(projectId: String): Unit {
        if (repository.pending(projectId) != null) repository.commitPending(projectId)
    }

    @Synchronized
    override fun rolledBack(projectId: String): Unit {
        repository.discardPending(projectId)
    }

    private fun records(
        projectId: String,
        staged: StagedChange,
        template: DocumentProvenanceTemplate,
        proposalId: String,
        baselineFingerprint: String,
        expectedFingerprint: String,
        appliedByUserId: String,
        appliedAt: Instant,
    ): List<AppliedDocumentProvenance> {
        val draft = staged.documentDraftProvenance!!
        val references = template.recommendation.evidence.flatMap { it.references }
        return references.groupBy { it.documentId.value }.map { (documentId, documentEvidence) ->
            val document = template.documents.singleOrNull { it.documentId == documentId }
                ?: throw DocumentIngestionFailure("document-provenance-document-missing", "A provenance document is unavailable.")
            val evidence = documentEvidence.map { reference ->
                val block = template.blocks[reference.blockId.value]
                    ?: throw DocumentIngestionFailure("document-provenance-evidence-stale", "Provenance evidence is stale.")
                AppliedDocumentEvidence(
                    evidenceId = reference.id,
                    documentId = reference.documentId,
                    pageNumber = reference.pageNumber,
                    blockId = reference.blockId,
                    startOffsetInBlock = reference.startOffsetInBlock,
                    endOffsetInBlock = reference.endOffsetInBlock,
                    exactExcerpt = reference.exactExcerpt,
                    extractionMethod = reference.extractionMethod,
                    extractorVersion = block.extractorVersion,
                    confidence = reference.ocrConfidence ?: template.recommendation.confidence,
                )
            }
            val typed = AppliedDocumentTypedOperation(
                stagedItemId = staged.id,
                targetSourceId = staged.targetSourceId,
                normalizedTypedOperationKey = draft.normalizedTypedOperationKey
                    ?: throw DocumentIngestionFailure("document-provenance-operation-missing", "Typed operation provenance is missing."),
                targetEntityIri = targetEntity(staged.operation),
                targetAssertionKey = targetAssertion(staged.operation),
            )
            val applyEvent = AppliedDocumentApplyEvent(
                proposalId = proposalId,
                appliedByUserId = appliedByUserId,
                appliedAt = appliedAt,
                baselineOntologyFingerprint = baselineFingerprint,
                resultingOntologyFingerprint = expectedFingerprint,
            )
            AppliedDocumentProvenance(
                recordId = recordId(
                    projectId,
                    staged.targetSourceId,
                    draft.normalizedTypedOperationKey
                        ?: throw DocumentIngestionFailure(
                            "document-provenance-operation-missing",
                            "Typed operation provenance is missing.",
                        ),
                    document.checksumSha256,
                    evidence.joinToString("\u0000") { it.evidenceId.value },
                    template.recommendation.id,
                    template.decision.decisionId,
                    expectedFingerprint,
                ),
                projectId = projectId,
                taskId = draft.taskId,
                document = AppliedDocumentIdentity(
                    documentId = com.entio.core.DocumentId(document.documentId),
                    checksumSha256 = document.checksumSha256,
                    safeFilename = document.safeFilename,
                ),
                evidence = evidence,
                recommendationId = template.recommendation.id,
                action = template.recommendation.action,
                decision = AppliedDocumentDecision(
                    decisionId = template.decision.decisionId,
                    recommendationId = template.recommendation.id,
                    actorUserId = template.decision.actorUserId,
                    decidedAt = template.decision.decidedAt,
                    status = DocumentRecommendationReviewStatus.Drafted,
                    clarification = template.decision.clarification,
                ),
                modelId = template.recommendation.modelId,
                promptVersion = template.recommendation.promptVersion,
                confidence = template.recommendation.confidence,
                evidenceTypes = template.recommendation.evidence.map { it.type }.distinct().sortedBy { it.name },
                typedOperation = typed,
                applyEvent = applyEvent,
            )
        }
    }

    private fun connectedRecords(
        projectId: String,
        staged: StagedChange,
        template: ConnectedDocumentProvenanceTemplate,
        proposalId: String,
        baselineFingerprint: String,
        expectedFingerprint: String,
        appliedByUserId: String,
        appliedAt: Instant,
    ): List<AppliedDocumentProvenance> {
        val draft = staged.documentDraftProvenance!!
        val evidenceGroups = template.recommendation.evidenceIds.map { evidenceId ->
            template.evidence[evidenceId.value]
                ?: throw DocumentIngestionFailure(
                    "document-provenance-evidence-stale",
                    "Connected recommendation evidence is no longer available.",
                )
        }
        val references = evidenceGroups.flatMap(DocumentEvidence::references)
        if (references.isEmpty()) {
            throw DocumentIngestionFailure(
                "document-provenance-evidence-stale",
                "Connected recommendation evidence has no exact document reference.",
            )
        }
        val typed = AppliedDocumentTypedOperation(
            stagedItemId = staged.id,
            targetSourceId = staged.targetSourceId,
            normalizedTypedOperationKey = draft.normalizedTypedOperationKey
                ?: throw DocumentIngestionFailure(
                    "document-provenance-operation-missing",
                    "Typed operation provenance is missing.",
                ),
            targetEntityIri = targetEntity(staged.operation),
            targetAssertionKey = targetAssertion(staged.operation),
        )
        val prompts = template.analysisStages.mapNotNull(DocumentAnalysisStageRecord::promptVersion)
            .distinct()
            .sorted()
        val inputHashes = template.analysisStages.mapNotNull(DocumentAnalysisStageRecord::inputSha256)
            .distinct()
            .sorted()
        val outputHashes = template.analysisStages.mapNotNull(DocumentAnalysisStageRecord::outputSha256)
            .distinct()
            .sorted()
        val criticIds = template.recommendation.criticDispositions.map { it.findingId }.distinct().sorted()
        val coverageIds = template.coverage
            .filter { it.recommendationId == template.recommendation.id }
            .map(DocumentCoverageDisposition::discoveryId)
            .distinct()
            .sorted()
        val reviewOnly = template.recommendation.reviewOnlyFindings.map { finding ->
            AppliedDocumentReviewOnlyFinding(
                findingId = finding.id,
                summary = finding.summary,
                reason = finding.reason,
                evidenceIds = finding.evidenceIds,
            )
        }.sortedBy(AppliedDocumentReviewOnlyFinding::findingId)
        val individuals = template.recommendation.individualReviewGates.map { gate ->
            AppliedDocumentIndividualConfirmation(
                operationId = gate.operationId,
                classification = gate.classification,
                creationConfirmed = gate.creationConfirmed,
                productionClassificationConfirmed = gate.productionClassificationConfirmed,
            )
        }.sortedBy(AppliedDocumentIndividualConfirmation::operationId)
        val applyEvent = AppliedDocumentApplyEvent(
            proposalId = proposalId,
            appliedByUserId = appliedByUserId,
            appliedAt = appliedAt,
            baselineOntologyFingerprint = baselineFingerprint,
            resultingOntologyFingerprint = expectedFingerprint,
        )
        return references.groupBy { it.documentId.value }.map { (documentId, documentEvidence) ->
            val document = template.documents.singleOrNull { it.documentId == documentId }
                ?: throw DocumentIngestionFailure(
                    "document-provenance-document-missing",
                    "A connected-provenance document is unavailable.",
                )
            val evidence = documentEvidence.map { reference ->
                val block = template.blocks[reference.blockId.value]
                    ?: throw DocumentIngestionFailure(
                        "document-provenance-evidence-stale",
                        "Connected recommendation evidence is stale.",
                    )
                AppliedDocumentEvidence(
                    evidenceId = reference.id,
                    documentId = reference.documentId,
                    pageNumber = reference.pageNumber,
                    blockId = reference.blockId,
                    startOffsetInBlock = reference.startOffsetInBlock,
                    endOffsetInBlock = reference.endOffsetInBlock,
                    exactExcerpt = reference.exactExcerpt,
                    extractionMethod = reference.extractionMethod,
                    extractorVersion = block.extractorVersion,
                    confidence = reference.ocrConfidence ?: template.recommendation.confidence.overall,
                )
            }
            AppliedDocumentProvenance(
                recordId = recordId(
                    projectId,
                    staged.targetSourceId,
                    typed.normalizedTypedOperationKey,
                    document.checksumSha256,
                    evidence.joinToString("\u0000") { it.evidenceId.value },
                    template.recommendation.id,
                    template.decision.decisionId,
                    expectedFingerprint,
                ),
                projectId = projectId,
                taskId = com.entio.core.DocumentTaskId(template.taskId),
                document = AppliedDocumentIdentity(
                    documentId = com.entio.core.DocumentId(document.documentId),
                    checksumSha256 = document.checksumSha256,
                    safeFilename = document.safeFilename,
                ),
                evidence = evidence,
                recommendationId = template.recommendation.id,
                action = null,
                decision = AppliedDocumentDecision(
                    decisionId = template.decision.decisionId,
                    recommendationId = template.recommendation.id,
                    actorUserId = template.decision.actorUserId,
                    decidedAt = template.decision.decidedAt,
                    status = DocumentRecommendationReviewStatus.Drafted,
                    clarification = template.decision.clarification,
                ),
                modelId = template.modelId,
                promptVersion = DocumentAnalysisPipelineVersions.SEMANTIC_PLAN_PROMPT,
                confidence = template.recommendation.confidence.overall,
                evidenceTypes = evidenceGroups.map(DocumentEvidence::type).distinct().sortedBy { it.name },
                typedOperation = typed,
                applyEvent = applyEvent,
                analysisWorkKey = template.workKey,
                promptVersions = prompts,
                stageInputHashes = inputHashes,
                stageOutputHashes = outputHashes,
                confidenceDimensions = template.recommendation.confidence,
                criticDispositionIds = criticIds,
                coverageDispositionIds = coverageIds,
                relatedReviewOnlyFindings = reviewOnly,
                individualConfirmations = individuals,
            )
        }
    }

    private fun confirmationRecords(
        projectId: String,
        template: DocumentProvenanceTemplate,
        baselineFingerprint: String,
        resultingFingerprint: String,
        appliedByUserId: String,
        appliedAt: Instant,
    ): List<AppliedDocumentProvenance> {
        val references = template.recommendation.evidence.flatMap { it.references }
        return references.groupBy { it.documentId.value }.map { (documentId, documentEvidence) ->
            val document = template.documents.singleOrNull { it.documentId == documentId }
                ?: throw DocumentIngestionFailure("document-provenance-document-missing", "A provenance document is unavailable.")
            val evidence = documentEvidence.map { reference ->
                val block = template.blocks[reference.blockId.value]
                    ?: throw DocumentIngestionFailure("document-provenance-evidence-stale", "Provenance evidence is stale.")
                AppliedDocumentEvidence(
                    reference.id,
                    reference.documentId,
                    reference.pageNumber,
                    reference.blockId,
                    reference.startOffsetInBlock,
                    reference.endOffsetInBlock,
                    reference.exactExcerpt,
                    reference.extractionMethod,
                    block.extractorVersion,
                    reference.ocrConfidence ?: template.recommendation.confidence,
                )
            }
            AppliedDocumentProvenance(
                recordId = recordId(
                    projectId,
                    template.recommendation.selectedMatch?.entityIri?.value
                        ?: template.recommendation.proposedLabel
                        ?: template.recommendation.id,
                    document.checksumSha256,
                    evidence.joinToString("\u0000") { it.evidenceId.value },
                    template.recommendation.id,
                    template.decision.decisionId,
                    resultingFingerprint,
                ),
                projectId = projectId,
                taskId = com.entio.core.DocumentTaskId(template.taskId),
                document = AppliedDocumentIdentity(
                    com.entio.core.DocumentId(document.documentId),
                    document.checksumSha256,
                    document.safeFilename,
                ),
                evidence = evidence,
                recommendationId = template.recommendation.id,
                action = com.entio.core.DocumentRecommendationAction.Confirm,
                decision = AppliedDocumentDecision(
                    template.decision.decisionId,
                    template.recommendation.id,
                    template.decision.actorUserId,
                    template.decision.decidedAt,
                    DocumentRecommendationReviewStatus.Accepted,
                    template.decision.clarification,
                ),
                modelId = template.recommendation.modelId,
                promptVersion = template.recommendation.promptVersion,
                confidence = template.recommendation.confidence,
                evidenceTypes = template.recommendation.evidence.map { it.type }.distinct().sortedBy { it.name },
                typedOperation = null,
                applyEvent = AppliedDocumentApplyEvent(
                    proposalId = null,
                    appliedByUserId = appliedByUserId,
                    appliedAt = appliedAt,
                    baselineOntologyFingerprint = baselineFingerprint,
                    resultingOntologyFingerprint = resultingFingerprint,
                ),
            )
        }
    }

    private fun targetEntity(operation: StagedChangeOperation): Iri? = when (operation) {
        is StagedChangeOperation.TypedEdit -> operation.edit.targetEntity()
        is StagedChangeOperation.ShaclEdit -> operation.edit.shapeIri
        is StagedChangeOperation.GraphChanges -> operation.changeSet.changes.firstOrNull()?.triple?.subjectResource as? Iri
        is StagedChangeOperation.Delete -> null
    }

    private fun targetAssertion(operation: StagedChangeOperation): String? = when (operation) {
        is StagedChangeOperation.TypedEdit -> when (operation.edit) {
            is AddSuperclassEdit,
            is RemoveSuperclassEdit,
            is SetPropertyDomainEdit,
            is RemovePropertyDomainEdit,
            is SetPropertyRangeEdit,
            is RemovePropertyRangeEdit,
            is AssignTypeEdit,
            is AddObjectPropertyAssertionEdit,
            is AddDatatypePropertyAssertionEdit,
            -> operation.edit.toString().take(1_000)
            else -> null
        }
        is StagedChangeOperation.GraphChanges -> operation.changeSet.changes.firstOrNull()?.triple?.toString()?.take(1_000)
        is StagedChangeOperation.ShaclEdit -> operation.edit.toString().take(1_000)
        is StagedChangeOperation.Delete -> null
    }

    private fun TypedOntologyEdit.targetEntity(): Iri? = when (this) {
        is CreateClassEdit -> classIri
        is CreateObjectPropertyEdit -> propertyIri
        is CreateDatatypePropertyEdit -> propertyIri
        is CreateIndividualEdit -> individualIri
        is SetEntityLabelEdit -> entity as? Iri
        is AddSuperclassEdit -> classIri
        is RemoveSuperclassEdit -> classIri
        is SetPropertyDomainEdit -> propertyIri
        is RemovePropertyDomainEdit -> propertyIri
        is SetPropertyRangeEdit -> propertyIri
        is RemovePropertyRangeEdit -> propertyIri
        is AssignTypeEdit -> resource as? Iri
        is AddObjectPropertyAssertionEdit -> subject as? Iri
        is AddDatatypePropertyAssertionEdit -> subject as? Iri
    }

    private fun recordId(vararg values: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        values.forEach { value ->
            digest.update(value.length.toString().toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(value.toByteArray(StandardCharsets.UTF_8))
        }
        return "record-${digest.digest().joinToString("") { "%02x".format(it) }}"
    }
}
