package com.entio.web.ingestion

import com.entio.core.CreateClassEdit
import com.entio.core.DocumentAnalysisPipelineVersions
import com.entio.core.DocumentAnalysisStage
import com.entio.core.DocumentAnalysisStageRecord
import com.entio.core.DocumentAnalysisStageState
import com.entio.core.DocumentCandidateCategory
import com.entio.core.DocumentConfidenceDimensions
import com.entio.core.DocumentCoverageDisposition
import com.entio.core.DocumentCoverageDispositionKind
import com.entio.core.DocumentCriticDisposition
import com.entio.core.DocumentCriticDispositionKind
import com.entio.core.DocumentDraftProvenance
import com.entio.core.DocumentEvidence
import com.entio.core.DocumentEvidenceId
import com.entio.core.DocumentEvidenceReference
import com.entio.core.DocumentEvidenceType
import com.entio.core.DocumentExtractionMethod
import com.entio.core.DocumentId
import com.entio.core.DocumentFinalRecommendation
import com.entio.core.DocumentFinalRecommendationStatus
import com.entio.core.DocumentFinalPlan
import com.entio.core.DocumentGroupedDecisionKind
import com.entio.core.DocumentGroupedRecommendationDecision
import com.entio.core.DocumentPlanOperand
import com.entio.core.DocumentPlanOperation
import com.entio.core.DocumentPlanOperationKind
import com.entio.core.DocumentRecommendation
import com.entio.core.DocumentRecommendationAction
import com.entio.core.DocumentRecommendationCategory
import com.entio.core.DocumentRecommendationReviewStatus
import com.entio.core.DocumentReviewDecision
import com.entio.core.DocumentTaskId
import com.entio.core.DocumentTextBlockId
import com.entio.core.DocumentReviewOnlyFinding
import com.entio.core.Iri
import com.entio.core.LocatedDocumentTextBlock
import com.entio.core.RdfLiteral
import com.entio.semantic.DocumentDraftOperation
import com.entio.semantic.DocumentDraftTranslationContext
import com.entio.semantic.DocumentVerifiedFinalPlan
import com.entio.web.PreparedDocumentStagingItem
import com.entio.web.StagingWorkflowService
import com.entio.web.WebWorkflowFailure
import com.entio.web.contract.InMemoryProjectRegistry
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DocumentDraftProposalIntegrationTest {
    @Test
    fun recognizesCurrentGroundedAndLegacyConnectedModelPrompts(): Unit {
        val recordedAt = Instant.parse("2026-01-01T00:00:00Z")
        val grounded = DocumentAnalysisStageRecord(
            recordId = "stage-grounded",
            stage = DocumentAnalysisStage.ConnectedModeling,
            state = DocumentAnalysisStageState.Succeeded,
            scopeId = "task-1",
            startedAt = recordedAt,
            finishedAt = recordedAt,
            durationMillis = 0,
            selectedModelId = "gpt-test",
            promptVersion = DocumentAnalysisPipelineVersions.GROUNDED_PROMPT,
            requestSchemaVersion = DocumentAnalysisPipelineVersions.GROUNDED_REQUEST,
            responseSchemaVersion = DocumentAnalysisPipelineVersions.GROUNDED_RESPONSE,
            inputSha256 = "a".repeat(64),
            outputSha256 = "b".repeat(64),
            providerAttemptCount = 1,
            completedCount = 1,
            totalCount = 1,
        )

        assertEquals(
            DocumentAnalysisPipelineVersions.GROUNDED_PROMPT,
            expectedDocumentAnalysisPromptVersion(grounded),
        )
        assertEquals(
            DocumentAnalysisPipelineVersions.CONNECTED_MODEL_PROMPT,
            expectedDocumentAnalysisPromptVersion(
                grounded.copy(
                    promptVersion = DocumentAnalysisPipelineVersions.CONNECTED_MODEL_PROMPT,
                    requestSchemaVersion = DocumentAnalysisPipelineVersions.CONNECTED_MODEL_REQUEST,
                    responseSchemaVersion = DocumentAnalysisPipelineVersions.CONNECTED_MODEL_RESPONSE,
                ),
            ),
        )
    }

    @Test
    fun keepsEveryCompoundRecommendationInOneStagingBatch(): Unit {
        val firstCompound = (1..18).map { index ->
            prepared(index).copy(
                provenance = prepared(index).provenance.copy(recommendationId = "compound-1"),
            )
        }
        val secondCompound = (19..22).map { index ->
            prepared(index).copy(
                provenance = prepared(index).provenance.copy(recommendationId = "compound-2"),
            )
        }

        val batches = packAtomicDocumentRecommendationGroups(firstCompound + secondCompound)

        assertEquals(listOf(18, 4), batches.map { it.size })
        assertTrue(batches.all { batch -> batch.map { it.provenance.recommendationId }.distinct().size == 1 })
        assertEquals(
            "document-compound-recommendation-limit",
            assertFailsWith<DocumentIngestionFailure> {
                packAtomicDocumentRecommendationGroups(
                    (1..21).map { index ->
                        prepared(index).copy(
                            provenance = prepared(index).provenance.copy(recommendationId = "oversized"),
                        )
                    },
                )
            }.code,
        )
    }

    @Test
    fun stagesOneAtomicBatchWithFieldProvenanceAndNoSourceWrite(): Unit {
        val fixture = fixture()
        val service = StagingWorkflowService(fixture.registry)
        val before = fixture.source.readBytes()
        val items = (1..20).map(::prepared)

        val staged = service.stageDocumentBatch("simple", "alice", "task-1", "batch-1", items)

        assertEquals(20, staged.entries.size)
        assertTrue(staged.entries.all { it.documentDraftProvenance?.taskId == "task-1" })
        assertEquals(before.toList(), fixture.source.readBytes().toList())
        assertEquals(staged, service.stageDocumentBatch("simple", "alice", "task-1", "batch-1", items))
        assertEquals(
            "document-draft-batch-limit",
            assertFailsWith<WebWorkflowFailure> {
                service.stageDocumentBatch("simple", "alice", "task-1", "too-many", (1..21).map(::prepared))
            }.code,
        )
    }

    @Test
    fun stagesMoreThanOneHundredEditsAcrossBoundedBatches(): Unit {
        val fixture = fixture()
        val service = StagingWorkflowService(fixture.registry)
        val batches = packAtomicDocumentRecommendationGroups((1..120).map(::prepared))

        assertEquals(6, batches.size)
        assertTrue(batches.all { it.size == 20 })

        batches.forEachIndexed { index, batch ->
            service.stageDocumentBatch(
                projectId = "simple",
                userId = "alice",
                taskId = "task-1",
                idempotencyKey = "batch-${index + 1}",
                items = batch,
            )
        }

        assertEquals(120, service.snapshot("simple").entries.size)
    }

    @Test
    fun validatesEveryItemBeforeMutationAndRejectsUnrelatedSharedStaging(): Unit {
        val fixture = fixture()
        val service = StagingWorkflowService(fixture.registry)
        val invalid = prepared(2).copy(
            targetSourceId = "missing",
            provenance = prepared(2).provenance.copy(targetSourceId = "missing"),
        )
        assertEquals(
            "unknown-source",
            assertFailsWith<WebWorkflowFailure> {
                service.stageDocumentBatch("simple", "alice", "task-1", "invalid", listOf(prepared(1), invalid))
            }.code,
        )
        assertTrue(service.snapshot("simple").entries.isEmpty())

        service.stageDocumentBatch("simple", "alice", "task-1", "first", listOf(prepared(1)))
        assertEquals(
            "document-draft-shared-staging-not-empty",
            assertFailsWith<WebWorkflowFailure> {
                service.stageDocumentBatch("simple", "alice", "task-2", "other", listOf(prepared(2, "task-2")))
            }.code,
        )
        assertEquals(1, service.snapshot("simple").entries.size)
    }

    @Test
    fun commitsDurableProvenanceOnlyAfterSuccessfulExistingApply(): Unit {
        val fixture = fixture()
        val repository = AppliedDocumentProvenanceRepository(Files.createTempDirectory("entio-applied-provenance"), fixture.registry)
        val coordinator = DocumentApplyProvenanceCoordinator(repository)
        val service = StagingWorkflowService(fixture.registry)
        service.installDocumentApplyHooks(coordinator)
        val candidate = candidate()
        coordinator.register("simple", "task-1", listOf(candidate))
        service.stageDocumentBatch("simple", "alice", "task-1", "batch", listOf(prepared(1)))

        val preview = service.preview("simple", "alice")
        assertEquals("READYFORREVIEW", preview.proposal?.status)
        assertTrue(repository.list("simple").isEmpty())
        assertFalse(Files.readString(fixture.source).contains("document-1"))

        service.approve("simple", "reviewer")
        assertEquals("APPLIED", service.apply("simple", "reviewer").status)

        val record = repository.list("simple").single()
        assertEquals("recommendation-1", record.recommendationId)
        assertEquals("reviewer", record.applyEvent.appliedByUserId)
        assertNotNull(record.applyEvent.proposalId)
        assertEquals("Customer", record.evidence.single().exactExcerpt)
        assertFalse(Files.readString(fixture.source).contains("recommendation-1"))
        assertFalse(Files.readString(fixture.source).contains("document-1"))
    }

    @Test
    fun connectedApplyRetainsVerifiedPipelineAndReviewOnlyProvenance(): Unit {
        val fixture = fixture()
        val repository = AppliedDocumentProvenanceRepository(
            Files.createTempDirectory("entio-connected-applied-provenance"),
            fixture.registry,
        )
        val coordinator = DocumentApplyProvenanceCoordinator(repository)
        val service = StagingWorkflowService(fixture.registry)
        service.installDocumentApplyHooks(coordinator)
        val legacy = candidate()
        val recommendation = DocumentFinalRecommendation(
            id = "recommendation-1",
            title = "Create a connected customer concept",
            description = "Create the verified concept while retaining a related policy rule for review.",
            discoveryIds = listOf("discovery-1"),
            evidenceIds = listOf(DocumentEvidenceId("evidence-group")),
            operations = listOf(
                DocumentPlanOperation(
                    id = "operation-1",
                    kind = DocumentPlanOperationKind.CreateClass,
                    order = 0,
                    declaration = com.entio.core.DocumentTemporaryReference("new:class:DocumentClass1"),
                    operands = listOf(DocumentPlanOperand.SourceId("simple")),
                    expandedTypedEditCount = 1,
                ),
            ),
            reviewOnlyFindings = listOf(
                DocumentReviewOnlyFinding(
                    id = "review-only-1",
                    summary = "Manual aggregation review",
                    reason = "Aggregation cannot be represented by the supported typed edits.",
                    discoveryIds = listOf("discovery-1"),
                    evidenceIds = listOf(DocumentEvidenceId("evidence-group")),
                    relatedOperationIds = listOf("operation-1"),
                ),
            ),
            criticDispositions = listOf(
                DocumentCriticDisposition("critic-1", DocumentCriticDispositionKind.AcceptedAndIncorporated),
            ),
            confidence = DocumentConfidenceDimensions(95, 90, 85),
            status = DocumentFinalRecommendationStatus.Mixed,
        )
        val decidedAt = Instant.parse("2026-01-01T00:00:00Z")
        coordinator.registerConnected(
            "simple",
            listOf(
                ConnectedDocumentProvenanceCandidate(
                    taskId = "task-1",
                    recommendation = recommendation,
                    decision = DocumentGroupedRecommendationDecision(
                        decisionId = "decision-1",
                        recommendationId = recommendation.id,
                        actorUserId = "alice",
                        decidedAt = decidedAt,
                        kind = DocumentGroupedDecisionKind.Accepted,
                    ),
                    documents = legacy.documents,
                    blocks = legacy.blocks,
                    evidence = legacy.recommendation.evidence.associateBy { it.id.value },
                    workKey = "a".repeat(64),
                    modelId = "gpt-test",
                    analysisStages = listOf(
                        DocumentAnalysisStageRecord(
                            recordId = "stage-final-1",
                            stage = DocumentAnalysisStage.FinalPlanning,
                            state = DocumentAnalysisStageState.Succeeded,
                            scopeId = "task-1",
                            startedAt = decidedAt,
                            finishedAt = decidedAt,
                            durationMillis = 0,
                            selectedModelId = "gpt-test",
                            promptVersion = DocumentAnalysisPipelineVersions.FINAL_PLAN_PROMPT,
                            requestSchemaVersion = DocumentAnalysisPipelineVersions.FINAL_PLAN_REQUEST,
                            responseSchemaVersion = DocumentAnalysisPipelineVersions.FINAL_PLAN_RESPONSE,
                            inputSha256 = "b".repeat(64),
                            outputSha256 = "c".repeat(64),
                            providerAttemptCount = 1,
                            completedCount = 1,
                            totalCount = 1,
                        ),
                    ),
                    coverage = listOf(
                        DocumentCoverageDisposition(
                            discoveryId = "discovery-1",
                            kind = DocumentCoverageDispositionKind.ExecutableRecommendation,
                            recommendationId = recommendation.id,
                        ),
                    ),
                ),
            ),
        )
        val before = fixture.source.readBytes()
        service.stageDocumentBatch("simple", "alice", "task-1", "connected-batch", listOf(prepared(1)))
        service.preview("simple", "alice")
        service.approve("simple", "reviewer")

        assertEquals(before.toList(), fixture.source.readBytes().toList())
        assertEquals("APPLIED", service.apply("simple", "reviewer").status)
        val applied = repository.list("simple").single()
        assertEquals(null, applied.action)
        assertEquals("a".repeat(64), applied.analysisWorkKey)
        assertEquals(DocumentConfidenceDimensions(95, 90, 85), applied.confidenceDimensions)
        assertEquals(listOf("critic-1"), applied.criticDispositionIds)
        assertEquals(listOf("discovery-1"), applied.coverageDispositionIds)
        assertEquals(listOf("review-only-1"), applied.relatedReviewOnlyFindings.map { it.findingId })
        assertEquals(listOf("b".repeat(64)), applied.stageInputHashes)
        assertEquals(listOf("c".repeat(64)), applied.stageOutputHashes)
    }

    @Test
    fun explicitlyRetainsPureReviewOnlyFindingWithoutOntologyEdit(): Unit {
        val fixture = fixture()
        val repository = AppliedDocumentProvenanceRepository(
            Files.createTempDirectory("entio-review-only-provenance"),
            fixture.registry,
        )
        val coordinator = DocumentApplyProvenanceCoordinator(repository)
        val legacy = candidate()
        val finding = DocumentReviewOnlyFinding(
            id = "review-only-rule",
            summary = "Approval separation rule",
            reason = "The rule is meaningful but cannot be represented by a supported typed edit.",
            discoveryIds = listOf("discovery-1"),
            evidenceIds = listOf(DocumentEvidenceId("evidence-group")),
        )
        val recommendation = DocumentFinalRecommendation(
            id = "recommendation-review-only",
            title = "Retain approval separation rule",
            description = "Retain the verified rule as documented meaning.",
            discoveryIds = listOf("discovery-1"),
            evidenceIds = listOf(DocumentEvidenceId("evidence-group")),
            reviewOnlyFindings = listOf(finding),
            confidence = DocumentConfidenceDimensions(92, 88, 84),
            status = DocumentFinalRecommendationStatus.ReviewOnly,
        )
        val decision = DocumentGroupedRecommendationDecision(
            decisionId = "decision-review-only",
            recommendationId = recommendation.id,
            actorUserId = "alice",
            decidedAt = Instant.parse("2026-07-30T12:00:00Z"),
            kind = DocumentGroupedDecisionKind.Drafted,
        )
        val plan = DocumentFinalPlan(
            workKey = com.entio.core.DocumentAnalysisWorkKey("a".repeat(64)),
            verifiedDiscoveryIds = listOf("discovery-1"),
            criticFindingIds = emptyList(),
            recommendations = listOf(recommendation),
            coverage = listOf(
                DocumentCoverageDisposition(
                    discoveryId = "discovery-1",
                    kind = DocumentCoverageDispositionKind.ReviewOnlyFinding,
                    recommendationId = recommendation.id,
                ),
            ),
        )
        val candidate = VerifiedDocumentReviewOnlyCandidate(
            taskId = "task-1",
            recommendation = recommendation,
            decision = decision,
            reviewPlan = VerifiedDocumentReviewPlan(
                workKey = "a".repeat(64),
                graphFingerprint = "graph-fingerprint",
                plan = DocumentVerifiedFinalPlan(plan, emptyMap(), emptyList()),
                taskDocuments = legacy.documents,
                blocks = legacy.blocks,
                evidence = legacy.recommendation.evidence.associateBy { it.id.value },
                analysisStages = listOf(
                    DocumentAnalysisStageRecord(
                        recordId = "stage-semantic-1",
                        stage = DocumentAnalysisStage.FinalPlanning,
                        state = DocumentAnalysisStageState.Succeeded,
                        scopeId = "task-1",
                        startedAt = decision.decidedAt,
                        finishedAt = decision.decidedAt,
                        durationMillis = 0,
                        selectedModelId = "gpt-test",
                        promptVersion = DocumentAnalysisPipelineVersions.SEMANTIC_PLAN_PROMPT,
                        requestSchemaVersion = DocumentAnalysisPipelineVersions.SEMANTIC_PLAN_REQUEST,
                        responseSchemaVersion = DocumentAnalysisPipelineVersions.SEMANTIC_PLAN_RESPONSE,
                        inputSha256 = "b".repeat(64),
                        outputSha256 = "c".repeat(64),
                        providerAttemptCount = 1,
                        completedCount = 1,
                        totalCount = 1,
                    ),
                ),
            ),
        )
        val before = fixture.source.readBytes()

        assertEquals(1, coordinator.retainReviewOnly("simple", candidate, "gpt-test"))

        assertEquals(before.toList(), fixture.source.readBytes().toList())
        val retained = repository.list("simple").single()
        assertEquals(null, retained.typedOperation)
        assertEquals(null, retained.applyEvent.proposalId)
        assertEquals(listOf("review-only-rule"), retained.relatedReviewOnlyFindings.map { it.findingId })
        assertEquals(DocumentAnalysisPipelineVersions.SEMANTIC_PLAN_PROMPT, retained.promptVersion)
    }

    @Test
    fun provenanceFailureUsesExistingRollbackAndClearsPendingEvent(): Unit {
        val fixture = fixture()
        val repository = AppliedDocumentProvenanceRepository(Files.createTempDirectory("entio-rollback-provenance"), fixture.registry)
        val coordinator = object : DocumentApplyHooks {
            override fun begin(
                projectId: String,
                proposalId: String,
                baselineFingerprint: String,
                expectedFingerprint: String,
                staged: List<com.entio.core.StagedChange>,
                appliedByUserId: String,
            ) = Unit

            override fun commit(projectId: String): Unit = error("simulated provenance failure")
            override fun rolledBack(projectId: String): Unit = repository.discardPending(projectId)
        }
        val service = StagingWorkflowService(fixture.registry)
        service.installDocumentApplyHooks(coordinator)
        val before = fixture.source.readBytes()
        service.stageDocumentBatch("simple", "alice", "task-1", "batch", listOf(prepared(1)))
        service.preview("simple", "alice")
        service.approve("simple", "reviewer")

        assertEquals("ROLLEDBACK", service.apply("simple", "reviewer").status)
        assertEquals(before.toList(), fixture.source.readBytes().toList())
        assertTrue(repository.list("simple").isEmpty())
        assertEquals(null, repository.pending("simple"))
    }

    private fun prepared(index: Int, taskId: String = "task-1"): PreparedDocumentStagingItem =
        PreparedDocumentStagingItem(
            summary = "Create document class $index",
            editType = "create-class",
            targetSourceId = "simple",
            operation = DocumentDraftOperation.Ontology(
                CreateClassEdit(Iri("$NS#DocumentClass$index"), RdfLiteral("Document class $index")),
            ),
            provenance = provenance(index, taskId),
        )

    private fun provenance(index: Int, taskId: String): DocumentDraftProvenance = DocumentDraftProvenance(
        taskId = DocumentTaskId(taskId),
        recommendationId = "recommendation-$index",
        decisionId = "decision-$index",
        evidenceIds = listOf(DocumentEvidenceId("evidence-1")),
        modelId = "gpt-test",
        promptVersion = "prompt-v1",
        extractionMethods = listOf(DocumentExtractionMethod.Text),
        confidence = 90,
        targetSourceId = "simple",
        normalizedTypedOperationKey = "create-class-$index",
    )

    private fun candidate(): DocumentReviewDraftCandidate {
        val reference = DocumentEvidenceReference(
            DocumentEvidenceId("evidence-1"),
            DocumentId("document-1"),
            DocumentTextBlockId("block-1"),
            startOffsetInBlock = 0,
            endOffsetInBlock = 8,
            exactExcerpt = "Customer",
            extractionMethod = DocumentExtractionMethod.Text,
        )
        val recommendation = DocumentRecommendation(
            id = "recommendation-1",
            candidateIds = listOf("candidate-1"),
            type = DocumentCandidateCategory.Class,
            category = DocumentRecommendationCategory.OntologyStructure,
            proposedLabel = "Document class 1",
            action = DocumentRecommendationAction.CreateLocal,
            confidence = 90,
            rationale = "Verified source evidence.",
            evidence = listOf(
                DocumentEvidence(DocumentEvidenceId("evidence-group"), DocumentEvidenceType.Explicit, listOf(reference)),
            ),
            targetSourceId = "simple",
            modelId = "gpt-test",
            promptVersion = "prompt-v1",
        )
        val block = LocatedDocumentTextBlock(
            DocumentTextBlockId("block-1"),
            DocumentId("document-1"),
            "policy.txt",
            blockOrder = 0,
            startOffset = 0,
            endOffset = 15,
            exactText = "Customer policy",
            extractionMethod = DocumentExtractionMethod.Text,
            extractorVersion = "extractor-v1",
        )
        return DocumentReviewDraftCandidate(
            recommendation,
            DocumentDraftTranslationContext("simple", targetIri = Iri("$NS#DocumentClass1"), acceptedForDraft = true),
            DocumentReviewDecision(
                "decision-1",
                "recommendation-1",
                "alice",
                Instant.parse("2026-07-24T12:00:00Z"),
                DocumentRecommendationReviewStatus.Pending,
                DocumentRecommendationReviewStatus.Accepted,
            ),
            listOf(
                DocumentIngestionDocumentSnapshot(
                    "document-1",
                    "policy.txt",
                    "text",
                    15,
                    "a".repeat(64),
                    "authoritative",
                    "awaiting-review",
                ),
            ),
            mapOf("block-1" to block),
        )
    }

    private fun fixture(): Fixture {
        val allowed = Files.createTempDirectory("entio-document-draft")
        val root = Files.createDirectory(allowed.resolve("simple"))
        val ontology = Files.createDirectories(root.resolve("ontology"))
        Files.writeString(
            root.resolve("entio.yaml"),
            """
            name: simple-ontology
            iriNamespace: $NS#
            ontologySources:
              - id: simple
                path: ontology/simple.ttl
                format: turtle
                roles: [ontology, data]
            """.trimIndent(),
        )
        val source = ontology.resolve("simple.ttl")
        Files.writeString(
            source,
            """
            @prefix ex: <$NS#> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            ex:Customer a owl:Class ; rdfs:label "Customer" .
            """.trimIndent(),
        )
        val registry = InMemoryProjectRegistry(setOf(allowed)).also {
            it.register("simple", "Simple ontology", root)
        }
        return Fixture(root, source, registry)
    }

    private data class Fixture(
        val root: Path,
        val source: Path,
        val registry: InMemoryProjectRegistry,
    )

    private companion object {
        const val NS: String = "https://example.com/entio/simple"
    }
}
