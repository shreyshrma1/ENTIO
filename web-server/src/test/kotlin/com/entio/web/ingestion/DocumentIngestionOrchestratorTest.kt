package com.entio.web.ingestion

import com.entio.core.DocumentAnalysisStage
import com.entio.core.DocumentConfidenceDimensions
import com.entio.core.DocumentCoverageDisposition
import com.entio.core.DocumentCoverageDispositionKind
import com.entio.core.DocumentDiscovery
import com.entio.core.DocumentEvidence
import com.entio.core.DocumentEvidenceId
import com.entio.core.DocumentFinalPlan
import com.entio.core.DocumentFinalRecommendation
import com.entio.core.DocumentFinalRecommendationStatus
import com.entio.core.DocumentPlanOperand
import com.entio.core.DocumentPlanOperation
import com.entio.core.DocumentPlanOperationKind
import com.entio.core.DocumentReviewOnlyFinding
import com.entio.core.DocumentTemporaryReference
import com.entio.core.DocumentSemanticItemKind
import com.entio.core.DocumentSemanticOutcome
import com.entio.core.DocumentSemanticPlan
import com.entio.core.DocumentSemanticPlanItem
import com.entio.core.DocumentSemanticRecommendationGroup
import com.entio.web.ai.InMemoryAiCredentialStore
import com.entio.web.ai.models.AiModelCompatibilityState
import com.entio.web.ai.models.AiModelDiscoveryStatus
import com.entio.web.ai.models.AiModelSelectionStatus
import com.entio.web.ai.models.AiModelVerificationStatus
import com.entio.web.ai.models.AiSelectableModelDescriptor
import com.entio.web.ai.models.AiSettingsCredentialStatus
import com.entio.web.ai.models.AiUserProviderSettings
import com.entio.web.ai.models.InMemoryAiUserProviderSettingsStore
import com.entio.web.contract.InMemoryProjectRegistry
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class DocumentIngestionOrchestratorTest {
    @Test
    fun connectsIntakeExtractionAnalysisMatchingAndReviewWithoutWritingOntology(): Unit = runBlocking {
        val fixture = fixture(readyModel = true)
        val before = Files.readAllBytes(fixture.source)
        val taskId = fixture.manager.begin("simple", "alice", 1)
        val directory = fixture.manager.directory(taskId, "simple", "alice")
        val upload = fixture.intake.accept(
            taskId,
            directory,
            "simple",
            "alice",
            metadata(),
            ByteArrayInputStream("Supplier policy defines approved suppliers.".toByteArray()),
        )
        fixture.manager.addDocument(taskId, "simple", "alice", upload)
        fixture.manager.completeIntake(taskId, "simple", "alice")

        fixture.orchestrator.start(taskId.value, "simple", "alice")
        fixture.orchestrator.await(taskId.value)

        val task = fixture.manager.find(taskId, "simple", "alice")
        assertEquals("awaiting-review", task.status)
        assertEquals(100, task.progress.percent)
        assertTrue(task.updates.any { it.message.contains("Discovering evidence-grounded meaning") })
        assertTrue(task.updates.any { it.message.contains("Synthesizing connected meaning") })
        assertTrue(task.updates.any { it.message.contains("Planning ontology-aware grouped recommendations") })
        assertEquals(
            listOf(
                DocumentAnalysisStage.Discovery,
                DocumentAnalysisStage.ConnectedModeling,
                DocumentAnalysisStage.FinalPlanning,
                DocumentAnalysisStage.DeterministicVerification,
            ),
            task.analysisStages.map { it.stage },
        )
        val reviewPlan = fixture.reviews.verifiedReviewPlan("simple", taskId.value, "alice")
        val recommendation = reviewPlan.plan.plan.recommendations.single()
        assertEquals("Create Supplier", recommendation.title)
        assertTrue(reviewPlan.graphFingerprint.isNotBlank())
        assertEquals(task.analysisStages, reviewPlan.analysisStages)
        assertEquals(recommendation.evidenceIds.toSet(), reviewPlan.evidence.keys.map(::DocumentEvidenceId).toSet())
        assertTrue(reviewPlan.blocks.isNotEmpty())
        assertEquals(task.documents, reviewPlan.taskDocuments)
        assertEquals(before.toList(), Files.readAllBytes(fixture.source).toList())
        assertTrue(directory.path.toFile().exists())
        assertEquals(1, fixture.provider.discoveryCalls)
        assertEquals(1, fixture.provider.connectedModelCalls)
        assertEquals(1, fixture.provider.finalPlanningCalls)
        assertEquals(0, fixture.provider.reconciliationCalls)
        assertEquals(0, fixture.provider.alignmentCalls)
        assertEquals(0, fixture.provider.criticCalls)
        fixture.close()
    }

    @Test
    fun preparesACompoundConceptAndConnectionAgainstCurrentOntologyContext(): Unit = runBlocking {
        val fixture = fixture(readyModel = true, compoundConcept = true)
        val taskId = fixture.manager.begin("simple", "alice", 1)
        val directory = fixture.manager.directory(taskId, "simple", "alice")
        val text = "Account closure means the date the account is closed after all adjustments."
        val upload = fixture.intake.accept(
            taskId,
            directory,
            "simple",
            "alice",
            metadata(),
            ByteArrayInputStream(text.toByteArray()),
        )
        fixture.manager.addDocument(taskId, "simple", "alice", upload)
        fixture.manager.completeIntake(taskId, "simple", "alice")

        fixture.orchestrator.start(taskId.value, "simple", "alice")
        fixture.orchestrator.await(taskId.value)

        val recommendation = fixture.reviews.verifiedPlan("simple", taskId.value, "alice")
            .plan.recommendations.single()
        assertEquals("Create Account closure", recommendation.title)
        assertEquals(
            "new:class:AccountClosure",
            recommendation.operations.single().declaration?.value,
        )
        fixture.close()
    }

    @Test
    fun completesTenDocumentPipelineWithinThePinnedLogicalCallBudget(): Unit = runBlocking {
        val fixture = fixture(readyModel = true)
        val taskId = fixture.manager.begin("simple", "alice", 10)
        val directory = fixture.manager.directory(taskId, "simple", "alice")
        repeat(10) { index ->
            val upload = fixture.intake.accept(
                taskId,
                directory,
                "simple",
                "alice",
                metadata(index + 1),
                ByteArrayInputStream("Supplier $index policy defines approved suppliers.".toByteArray()),
            )
            fixture.manager.addDocument(taskId, "simple", "alice", upload)
        }
        fixture.manager.completeIntake(taskId, "simple", "alice")

        fixture.orchestrator.start(taskId.value, "simple", "alice")
        fixture.orchestrator.await(taskId.value)

        val task = fixture.manager.find(taskId, "simple", "alice")
        assertEquals("awaiting-review", task.status, task.updates.joinToString(" | ") { it.message })
        assertEquals(10, task.analysisStages.count { it.stage == DocumentAnalysisStage.Discovery })
        assertEquals(10, fixture.reviews.verifiedPlan("simple", taskId.value, "alice").plan.recommendations.size)
        fixture.close()
    }

    @Test
    fun reportsModelBlockWithoutExposingOrDeletingReviewableTaskMetadata(): Unit = runBlocking {
        val fixture = fixture(readyModel = false)
        val taskId = fixture.manager.begin("simple", "alice", 1)
        val directory = fixture.manager.directory(taskId, "simple", "alice")
        val upload = fixture.intake.accept(
            taskId,
            directory,
            "simple",
            "alice",
            metadata(),
            ByteArrayInputStream("Supplier policy defines approved suppliers.".toByteArray()),
        )
        fixture.manager.addDocument(taskId, "simple", "alice", upload)
        fixture.manager.completeIntake(taskId, "simple", "alice")

        fixture.orchestrator.start(taskId.value, "simple", "alice")
        fixture.orchestrator.await(taskId.value)

        val task = fixture.manager.find(taskId, "simple", "alice")
        assertEquals("blocked-for-model", task.status)
        assertTrue(task.progress.message.contains("selected model"))
        assertTrue(task.documents.single().safeFilename == "policy-1.txt")
        assertTrue(directory.path.toFile().exists())
        fixture.close()
    }

    @Test
    fun reportsSafeSpecificSchemaFailureWithoutExposingProviderPayloads(): Unit = runBlocking {
        val fixture = fixture(
            readyModel = true,
            providerFailure = DocumentAnalysisProviderResult.Failed(
                retryable = false,
                safeCode = "document-provider-request-schema-invalid",
            ),
        )
        val taskId = fixture.manager.begin("simple", "alice", 1)
        val directory = fixture.manager.directory(taskId, "simple", "alice")
        val upload = fixture.intake.accept(
            taskId,
            directory,
            "simple",
            "alice",
            metadata(),
            ByteArrayInputStream("Supplier policy defines approved suppliers.".toByteArray()),
        )
        fixture.manager.addDocument(taskId, "simple", "alice", upload)
        fixture.manager.completeIntake(taskId, "simple", "alice")

        fixture.orchestrator.start(taskId.value, "simple", "alice")
        fixture.orchestrator.await(taskId.value)

        val task = fixture.manager.find(taskId, "simple", "alice")
        assertEquals("failed", task.status)
        assertEquals(
            "The model provider rejected Entio's structured response schema " +
                "(document-provider-request-schema-invalid).",
            task.progress.message,
        )
        assertTrue(task.updates.last().message.contains("structured response schema"))
        assertTrue(directory.path.toFile().exists().not())
        fixture.close()
    }

    @Test
    fun reportsPhaseElevenPointFiveFailureCodeAndRetainsEarlierDiscoveryProgress(): Unit = runBlocking {
        val fixture = fixture(
            readyModel = true,
            failDiscoveryDocumentNumber = 2,
            discoveryFailureCode = "document-discovery-provider-schema-invalid",
        )
        val taskId = fixture.manager.begin("simple", "alice", 2)
        val directory = fixture.manager.directory(taskId, "simple", "alice")
        repeat(2) { index ->
            val upload = fixture.intake.accept(
                taskId,
                directory,
                "simple",
                "alice",
                metadata(index + 1),
                ByteArrayInputStream("Supplier $index policy defines approved suppliers.".toByteArray()),
            )
            fixture.manager.addDocument(taskId, "simple", "alice", upload)
        }
        fixture.manager.completeIntake(taskId, "simple", "alice")

        fixture.orchestrator.start(taskId.value, "simple", "alice")
        fixture.orchestrator.await(taskId.value)

        val task = fixture.manager.find(taskId, "simple", "alice")
        assertEquals("failed", task.status)
        assertEquals(1, task.analysisStages.count { it.stage == DocumentAnalysisStage.Discovery })
        assertTrue(task.progress.message.contains("document-discovery-provider-schema-invalid"))
        fixture.close()
    }

    @Test
    fun regeneratesAnInvalidSemanticReferenceOnceWithTheSameVerifiedInput(): Unit = runBlocking {
        val fixture = fixture(readyModel = true, retryFinalPlanningOnce = true)
        val taskId = fixture.manager.begin("simple", "alice", 1)
        val directory = fixture.manager.directory(taskId, "simple", "alice")
        val upload = fixture.intake.accept(
            taskId,
            directory,
            "simple",
            "alice",
            metadata(),
            ByteArrayInputStream("Supplier policy defines approved suppliers.".toByteArray()),
        )
        fixture.manager.addDocument(taskId, "simple", "alice", upload)
        fixture.manager.completeIntake(taskId, "simple", "alice")

        fixture.orchestrator.start(taskId.value, "simple", "alice")
        fixture.orchestrator.await(taskId.value)

        val task = fixture.manager.find(taskId, "simple", "alice")
        assertEquals("awaiting-review", task.status)
        assertEquals(
            2,
            task.analysisStages.single { it.stage == DocumentAnalysisStage.FinalPlanning }.providerAttemptCount,
        )
        assertContains(fixture.provider.finalPlanningInstructions.first(), "reuse its exact item ID")
        assertContains(
            fixture.provider.finalPlanningInstructions.last(),
            "One bounded full-plan regeneration is required",
        )
        assertContains(
            fixture.provider.finalPlanningInstructions.last(),
            "document-semantic-plan-reference-invalid",
        )
        fixture.close()
    }

    @Test
    fun correctsBlockedOperationContractBeforePublishingReviewResults(): Unit = runBlocking {
        val fixture = fixture(readyModel = true, invalidFinalPlanOnce = true)
        val taskId = fixture.manager.begin("simple", "alice", 1)
        val directory = fixture.manager.directory(taskId, "simple", "alice")
        val upload = fixture.intake.accept(
            taskId,
            directory,
            "simple",
            "alice",
            metadata(),
            ByteArrayInputStream("Supplier policy defines approved suppliers.".toByteArray()),
        )
        fixture.manager.addDocument(taskId, "simple", "alice", upload)
        fixture.manager.completeIntake(taskId, "simple", "alice")

        fixture.orchestrator.start(taskId.value, "simple", "alice")
        fixture.orchestrator.await(taskId.value)

        val task = fixture.manager.find(taskId, "simple", "alice")
        assertEquals("awaiting-review", task.status)
        assertEquals(2, fixture.provider.finalPlanningCalls)
        assertContains(fixture.provider.finalPlanningInstructions.last(), "semantic-group-blocked")
        assertContains(fixture.provider.finalPlanningInstructions.last(), "Supplier")
        assertContains(fixture.provider.finalPlanningInstructions.last(), "corrected semantic plan")
        assertContains(fixture.provider.finalPlanningInstructions.last(), "Do not emit operations")
        val recommendations = fixture.reviews
            .verifiedReviewPlan("simple", taskId.value, "alice")
            .plan
            .plan
            .recommendations
        assertTrue(recommendations.any { it.status == DocumentFinalRecommendationStatus.Executable })
        assertTrue(recommendations.none { "operation-contract-invalid" in it.blockers })
        fixture.close()
    }

    @Test
    fun retainsTheInitialPlanWhenTheCorrectionReducesExecutableCoverage(): Unit = runBlocking {
        val fixture = fixture(readyModel = true, degradeFinalPlanCorrection = true)
        val taskId = fixture.manager.begin("simple", "alice", 1)
        val directory = fixture.manager.directory(taskId, "simple", "alice")
        val upload = fixture.intake.accept(
            taskId,
            directory,
            "simple",
            "alice",
            metadata(),
            ByteArrayInputStream("Supplier policy defines approved suppliers.".toByteArray()),
        )
        fixture.manager.addDocument(taskId, "simple", "alice", upload)
        fixture.manager.completeIntake(taskId, "simple", "alice")

        fixture.orchestrator.start(taskId.value, "simple", "alice")
        fixture.orchestrator.await(taskId.value)

        val task = fixture.manager.find(taskId, "simple", "alice")
        assertEquals("awaiting-review", task.status)
        assertEquals(2, fixture.provider.finalPlanningCalls)
        val recommendations = fixture.reviews
            .verifiedReviewPlan("simple", taskId.value, "alice")
            .plan
            .plan
            .recommendations
        assertTrue(recommendations.any { it.status == DocumentFinalRecommendationStatus.Executable })
        assertTrue(recommendations.none { it.title == "Review Supplier only" })
        fixture.close()
    }

    @Test
    fun allowsTheApprovedTaskRetryReserveOnOneLogicalCall(): Unit = runBlocking {
        val fixture = fixture(
            readyModel = true,
            retryDiscoveryTimes = com.entio.core.MAX_DOCUMENT_AUTOMATIC_RETRY_ATTEMPTS,
        )
        val taskId = fixture.manager.begin("simple", "alice", 1)
        val directory = fixture.manager.directory(taskId, "simple", "alice")
        val upload = fixture.intake.accept(
            taskId,
            directory,
            "simple",
            "alice",
            metadata(),
            ByteArrayInputStream("Supplier policy defines approved suppliers.".toByteArray()),
        )
        fixture.manager.addDocument(taskId, "simple", "alice", upload)
        fixture.manager.completeIntake(taskId, "simple", "alice")

        fixture.orchestrator.start(taskId.value, "simple", "alice")
        fixture.orchestrator.await(taskId.value)

        val task = fixture.manager.find(taskId, "simple", "alice")
        assertEquals("awaiting-review", task.status)
        assertEquals(
            com.entio.core.MAX_DOCUMENT_AUTOMATIC_RETRY_ATTEMPTS + 1,
            task.analysisStages.single { it.stage == DocumentAnalysisStage.Discovery }.providerAttemptCount,
        )
        fixture.close()
    }

    @Test
    fun retriesAConnectedModelThatFailsDeterministicGrounding(): Unit = runBlocking {
        val fixture = fixture(readyModel = true, invalidConnectedModelOnce = true)
        val taskId = fixture.manager.begin("simple", "alice", 1)
        val directory = fixture.manager.directory(taskId, "simple", "alice")
        val upload = fixture.intake.accept(
            taskId,
            directory,
            "simple",
            "alice",
            metadata(),
            ByteArrayInputStream("Supplier policy defines approved suppliers.".toByteArray()),
        )
        fixture.manager.addDocument(taskId, "simple", "alice", upload)
        fixture.manager.completeIntake(taskId, "simple", "alice")

        fixture.orchestrator.start(taskId.value, "simple", "alice")
        fixture.orchestrator.await(taskId.value)

        val task = fixture.manager.find(taskId, "simple", "alice")
        assertEquals("awaiting-review", task.status)
        assertEquals(
            2,
            task.analysisStages.single { it.stage == DocumentAnalysisStage.ConnectedModeling }.providerAttemptCount,
        )
        assertContains(
            fixture.provider.connectedModelInstructions.last(),
            "document-connected-model-grounding-invalid",
        )
        assertContains(fixture.provider.connectedModelInstructions.last(), "unknown-discovery")
        fixture.close()
    }

    @Test
    fun continuesFromVerifiedDiscoveriesWhenConnectedModelCorrectionIsExhausted(): Unit = runBlocking {
        val fixture = fixture(readyModel = true, invalidConnectedModelAlways = true)
        val taskId = fixture.manager.begin("simple", "alice", 1)
        val directory = fixture.manager.directory(taskId, "simple", "alice")
        val upload = fixture.intake.accept(
            taskId,
            directory,
            "simple",
            "alice",
            metadata(),
            ByteArrayInputStream("Supplier policy defines approved suppliers.".toByteArray()),
        )
        fixture.manager.addDocument(taskId, "simple", "alice", upload)
        fixture.manager.completeIntake(taskId, "simple", "alice")

        fixture.orchestrator.start(taskId.value, "simple", "alice")
        fixture.orchestrator.await(taskId.value)

        val task = fixture.manager.find(taskId, "simple", "alice")
        assertEquals("awaiting-review", task.status)
        assertEquals(2, fixture.provider.connectedModelCalls)
        assertTrue(
            task.updates.any { update ->
                update.message ==
                    "Semantic synthesis could not retain a valid connected item; continuing from verified discoveries."
            },
        )
        assertTrue(
            task.updates
                .flatMap { it.details }
                .any { it.contains("Unknown discovery IDs: unknown-discovery.") },
        )
        assertEquals(1, fixture.provider.finalPlanningCalls)
        fixture.close()
    }

    @Test
    fun skipsAdministrativeMetadataItemsAndContinuesWithValidBusinessMeaning(): Unit = runBlocking {
        val fixture = fixture(readyModel = true, includeAdministrativeMetadataItem = true)
        val taskId = fixture.manager.begin("simple", "alice", 1)
        val directory = fixture.manager.directory(taskId, "simple", "alice")
        val upload = fixture.intake.accept(
            taskId,
            directory,
            "simple",
            "alice",
            metadata(),
            ByteArrayInputStream("Supplier policy defines approved suppliers.".toByteArray()),
        )
        fixture.manager.addDocument(taskId, "simple", "alice", upload)
        fixture.manager.completeIntake(taskId, "simple", "alice")

        fixture.orchestrator.start(taskId.value, "simple", "alice")
        fixture.orchestrator.await(taskId.value)

        val task = fixture.manager.find(taskId, "simple", "alice")
        assertEquals("awaiting-review", task.status)
        assertEquals(1, fixture.provider.connectedModelCalls)
        val skipUpdate = task.updates.single { it.message.contains("skipped 1 invalid item") }
        assertTrue(skipUpdate.details.any { it.contains("hasPolicyID") })
        assertTrue(skipUpdate.details.any { it.contains("AdministrativeMetadata") })
        fixture.close()
    }

    @Test
    fun stoppingGenerationCancelsTheInFlightProviderCall(): Unit = runBlocking {
        val providerStarted = CompletableDeferred<Unit>()
        val providerCancelled = CompletableDeferred<Unit>()
        val fixture = fixture(
            readyModel = true,
            blockDiscovery = true,
            providerStarted = providerStarted,
            providerCancelled = providerCancelled,
        )
        val taskId = fixture.manager.begin("simple", "alice", 1)
        val directory = fixture.manager.directory(taskId, "simple", "alice")
        val upload = fixture.intake.accept(
            taskId,
            directory,
            "simple",
            "alice",
            metadata(),
            ByteArrayInputStream("Supplier policy defines approved suppliers.".toByteArray()),
        )
        fixture.manager.addDocument(taskId, "simple", "alice", upload)
        fixture.manager.completeIntake(taskId, "simple", "alice")

        fixture.orchestrator.start(taskId.value, "simple", "alice")
        withTimeout(1_000) { providerStarted.await() }
        fixture.manager.cancel(taskId, "simple", "alice")
        fixture.orchestrator.cancel(taskId.value)
        withTimeout(1_000) {
            providerCancelled.await()
            fixture.orchestrator.await(taskId.value)
        }

        val task = fixture.manager.find(taskId, "simple", "alice")
        assertEquals("cancelled", task.status)
        assertEquals("Generation stopped by user.", task.progress.message)
        fixture.close()
    }

    private fun fixture(
        readyModel: Boolean,
        providerFailure: DocumentAnalysisProviderResult.Failed? = null,
        compoundConcept: Boolean = false,
        failDiscoveryDocumentNumber: Int? = null,
        discoveryFailureCode: String? = providerFailure?.safeCode,
        retryDiscoveryTimes: Int = 0,
        invalidConnectedModelOnce: Boolean = false,
        invalidConnectedModelAlways: Boolean = false,
        includeAdministrativeMetadataItem: Boolean = false,
        retryFinalPlanningOnce: Boolean = false,
        invalidFinalPlanOnce: Boolean = false,
        degradeFinalPlanCorrection: Boolean = false,
        blockDiscovery: Boolean = false,
        providerStarted: CompletableDeferred<Unit>? = null,
        providerCancelled: CompletableDeferred<Unit>? = null,
    ): Fixture {
        val now = Instant.parse("2026-07-24T12:00:00Z")
        val root = Files.createTempDirectory("entio-orchestration-projects")
        val project = Files.createDirectory(root.resolve("simple"))
        val ontology = Files.createDirectories(project.resolve("ontology"))
        Files.writeString(
            project.resolve("entio.yaml"),
            """
            name: simple
            iriNamespace: https://example.com/simple#
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
            @prefix ex: <https://example.com/simple#> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            ex:Customer a owl:Class .
            ex:Account a owl:Class ; rdfs:label "Account" .
            """.trimIndent(),
        )
        val registry = InMemoryProjectRegistry(setOf(root)).also {
            it.register("simple", "Simple", project)
        }
        val temporary = Files.createTempDirectory("entio-orchestration-temporary")
        var nextId = 0
        val configuration = DocumentIngestionConfiguration(
            temporaryRoot = temporary,
            provenanceRoot = Files.createTempDirectory("entio-orchestration-provenance"),
            clock = Clock.fixed(now, ZoneOffset.UTC),
            idFactory = { "id-${nextId++}" },
        )
        val storage = DocumentTemporaryStorage(temporary)
        val manager = DocumentIngestionTaskManager(configuration, storage)
        val reviews = DocumentReviewWorkspaceStore(configuration.clock)
        val credentials = InMemoryAiCredentialStore().also { it.save("alice", "openai", "secret") }
        val settings = settings(now, readyModel)
        val provider = TestPipelineProvider(
            compoundConcept = compoundConcept,
            discoveryFailureCode = discoveryFailureCode,
            failDiscoveryDocumentNumber = failDiscoveryDocumentNumber,
            retryDiscoveryTimes = retryDiscoveryTimes,
            invalidConnectedModelOnce = invalidConnectedModelOnce,
            invalidConnectedModelAlways = invalidConnectedModelAlways,
            includeAdministrativeMetadataItem = includeAdministrativeMetadataItem,
            retryFinalPlanningOnce = retryFinalPlanningOnce,
            invalidFinalPlanOnce = invalidFinalPlanOnce,
            degradeFinalPlanCorrection = degradeFinalPlanCorrection,
            blockDiscovery = blockDiscovery,
            providerStarted = providerStarted,
            providerCancelled = providerCancelled,
        )
        val provenance = AppliedDocumentProvenanceRepository(configuration.provenanceRoot, registry)
        val orchestrator = DocumentIngestionOrchestrator(
            manager,
            reviews,
            configuration,
            registry,
            provenance,
            credentials,
            settings,
            provider,
        )
        return Fixture(manager, reviews, DocumentIntakeService(configuration, storage), orchestrator, provider, source)
    }

    private class TestPipelineProvider(
        private val compoundConcept: Boolean,
        private val discoveryFailureCode: String?,
        private val failDiscoveryDocumentNumber: Int?,
        private val retryDiscoveryTimes: Int,
        private val invalidConnectedModelOnce: Boolean,
        private val invalidConnectedModelAlways: Boolean,
        private val includeAdministrativeMetadataItem: Boolean,
        private val retryFinalPlanningOnce: Boolean,
        private val invalidFinalPlanOnce: Boolean,
        private val degradeFinalPlanCorrection: Boolean,
        private val blockDiscovery: Boolean,
        private val providerStarted: CompletableDeferred<Unit>?,
        private val providerCancelled: CompletableDeferred<Unit>?,
    ) : DocumentPipelineProvider {
        val connectedModelInstructions: MutableList<String> = mutableListOf()
        val finalPlanningInstructions: MutableList<String> = mutableListOf()
        var discoveryCalls: Int = 0
            private set
        var connectedModelCalls: Int = 0
            private set
        var reconciliationCalls: Int = 0
            private set
        var alignmentCalls: Int = 0
            private set
        var criticCalls: Int = 0
            private set
        var finalPlanningCalls: Int = 0
            private set

        override suspend fun analyze(
            apiKey: String,
            selectedModelId: String,
            systemInstruction: String,
            request: DocumentAnalysisRequest,
        ): DocumentAnalysisProviderResult =
            DocumentAnalysisProviderResult.Failed(false, "document-legacy-analysis-disabled")

        override suspend fun discover(
            apiKey: String,
            selectedModelId: String,
            systemInstruction: String,
            request: DocumentDiscoveryRequest,
        ): DocumentDiscoveryProviderResult {
            assertContains(systemInstruction, "Use Role for a generic responsibility or job title")
            assertContains(systemInstruction, "policy, standard, procedure, manual, or guideline title")
            discoveryCalls += 1
            if (blockDiscovery) {
                providerStarted?.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    providerCancelled?.complete(Unit)
                }
            }
            if (discoveryCalls <= retryDiscoveryTimes) {
                return DocumentDiscoveryProviderResult.Failed(
                    retryable = true,
                    safeCode = "document-provider-malformed-output",
                )
            }
            val shouldFail = discoveryFailureCode != null &&
                (failDiscoveryDocumentNumber == null ||
                    discoveryCalls == failDiscoveryDocumentNumber)
            if (shouldFail) {
                return DocumentDiscoveryProviderResult.Failed(false, discoveryFailureCode)
            }
            val block = request.blocks.single()
            val description = if (compoundConcept) "Account closure" else "Supplier"
            val excerpt = if (compoundConcept) block.text else "Supplier"
            return DocumentDiscoveryProviderResult.Completed(
                DocumentDiscoveryResponse(
                    discoveries = listOf(
                        ProviderDocumentDiscovery(
                            providerId = "discovery-${request.documentId}",
                            kind = "Concept",
                            contentClassification = "BusinessContent",
                            assertionClassification = "ExplicitFact",
                            description = description,
                            evidence = listOf(
                                ProviderEvidenceClaim(
                                    block.documentId,
                                    block.blockId,
                                    0,
                                    excerpt.length,
                                    excerpt,
                                ),
                            ),
                            relatedProviderIds = emptyList(),
                            evidenceConfidence = 95,
                            individualClassification = null,
                        ),
                    ) + if (includeAdministrativeMetadataItem) {
                        listOf(
                            ProviderDocumentDiscovery(
                                providerId = "metadata-${request.documentId}",
                                kind = "Metadata",
                                contentClassification = "AdministrativeMetadata",
                                assertionClassification = "ExplicitFact",
                                description = "Policy identifier",
                                evidence = listOf(
                                    ProviderEvidenceClaim(
                                        block.documentId,
                                        block.blockId,
                                        0,
                                        excerpt.length,
                                        excerpt,
                                    ),
                                ),
                                relatedProviderIds = emptyList(),
                                evidenceConfidence = 95,
                                individualClassification = null,
                            ),
                        )
                    } else {
                        emptyList()
                    },
                ),
            )
        }

        override suspend fun model(
            apiKey: String,
            selectedModelId: String,
            systemInstruction: String,
            request: DocumentConnectedModelRequest,
        ): DocumentConnectedModelProviderResult {
            assertContains(systemInstruction, "A document title is provenance, not a class")
            assertContains(systemInstruction, "must never become individuals")
            connectedModelInstructions += systemInstruction
            connectedModelCalls += 1
            val businessDiscoveryIds = request.discoveries
                .filter { it.contentClassification.name == "BusinessContent" }
                .map { it.id }
                .sorted()
            val metadataDiscoveryIds = request.discoveries
                .filter { it.contentClassification.name == "AdministrativeMetadata" }
                .map { it.id }
                .sorted()
            return DocumentConnectedModelProviderResult.CompletedModel(
                DocumentConnectedModelResponse(
                    items = listOf(
                        ProviderConnectedModelItem(
                            providerId = "model-concept",
                            kind = "Class",
                            label = if (compoundConcept) "Account closure" else "Supplier",
                            rationale = "The verified discovery describes a business concept.",
                            discoveryIds = if (
                                invalidConnectedModelAlways ||
                                invalidConnectedModelOnce && connectedModelCalls == 1
                            ) {
                                listOf("unknown-discovery")
                            } else {
                                businessDiscoveryIds
                            },
                            references = emptyList(),
                            literalLexicalForm = null,
                            literalDatatypeIri = null,
                            literalLanguageTag = null,
                            order = 0,
                            reviewOnlyEligible = false,
                        ),
                    ) + if (includeAdministrativeMetadataItem) {
                        listOf(
                            ProviderConnectedModelItem(
                                providerId = "model-policy-id",
                                kind = "DatatypeProperty",
                                label = "hasPolicyID",
                                rationale = "The document header contains a policy identifier.",
                                discoveryIds = metadataDiscoveryIds,
                                references = emptyList(),
                                literalLexicalForm = null,
                                literalDatatypeIri = null,
                                literalLanguageTag = null,
                                order = 1,
                                reviewOnlyEligible = false,
                            ),
                        )
                    } else {
                        emptyList()
                    },
                ),
            )
        }

        override suspend fun consolidate(
            apiKey: String,
            selectedModelId: String,
            systemInstruction: String,
            request: DocumentModelConsolidationRequest,
        ): DocumentConnectedModelProviderResult =
            DocumentConnectedModelProviderResult.CompletedConsolidation(
                DocumentModelConsolidationResponse(items = request.chunkModels.flatMap { it.items }),
            )

        override suspend fun reconcile(
            apiKey: String,
            selectedModelId: String,
            systemInstruction: String,
            request: DocumentReconciliationRequest,
        ): DocumentReconciliationProviderResult {
            reconciliationCalls += 1
            return DocumentReconciliationProviderResult.Completed(DocumentReconciliationResponse(records = emptyList()))
        }

        override suspend fun align(
            apiKey: String,
            selectedModelId: String,
            systemInstruction: String,
            request: DocumentOntologyAlignmentRequest,
        ): DocumentOntologyAlignmentProviderResult {
            alignmentCalls += 1
            return DocumentOntologyAlignmentProviderResult.Completed(
                DocumentOntologyAlignmentResponse(
                    records = request.connectedModel.items.map { item ->
                        ProviderDocumentOntologyAlignment(
                            providerId = "alignment-${item.order}",
                            modelItemId = item.id,
                            action = "Create",
                            advisedReferenceIds = emptyList(),
                            targetSourceId = request.snapshot.writableSourceIds.first(),
                            rationale = "No supplied ontology entity represents this concept.",
                            ontologyFitConfidence = 90,
                            domainRangeRationale = null,
                        )
                    },
                ),
            )
        }

        override suspend fun critique(
            apiKey: String,
            selectedModelId: String,
            systemInstruction: String,
            request: DocumentModelingCriticRequest,
        ): DocumentModelingCriticProviderResult {
            criticCalls += 1
            return DocumentModelingCriticProviderResult.Completed(DocumentModelingCriticResponse(findings = emptyList()))
        }

        override suspend fun plan(
            apiKey: String,
            selectedModelId: String,
            systemInstruction: String,
            request: DocumentFinalPlanningRequest,
        ): DocumentFinalPlanningProviderResult {
            assertTrue(systemInstruction.contains("SetPropertyRange operands are exactly"))
            assertTrue(systemInstruction.contains("never as TextValue or LiteralValue"))
            assertContains(systemInstruction, "Semantic fidelity is more important than producing an executable edit")
            assertContains(systemInstruction, "Generic job titles are Roles, never Individuals")
            finalPlanningCalls += 1
            finalPlanningInstructions += systemInstruction
            if (invalidFinalPlanOnce && finalPlanningCalls == 1) {
                val discoveryIds = request.discoveries.map(DocumentDiscovery::id).sorted()
                val recommendation = DocumentFinalRecommendation(
                    id = "recommendation-invalid",
                    title = "Define Supplier",
                    description = "Create a supplier concept.",
                    discoveryIds = discoveryIds,
                    evidenceIds = request.discoveries
                        .flatMap(DocumentDiscovery::evidence)
                        .map(DocumentEvidence::id)
                        .distinct()
                        .sortedBy(DocumentEvidenceId::value),
                    confidence = DocumentConfidenceDimensions(90, 80, 80),
                    status = DocumentFinalRecommendationStatus.Blocked,
                    blockers = listOf("operation-contract-invalid"),
                )
                return DocumentFinalPlanningProviderResult.Completed(
                    DocumentFinalPlanningResponse(
                        plan = DocumentFinalPlan(
                            workKey = request.workKey,
                            verifiedDiscoveryIds = discoveryIds,
                            criticFindingIds = emptyList(),
                            recommendations = listOf(recommendation),
                            coverage = discoveryIds.map { discoveryId ->
                                DocumentCoverageDisposition(
                                    discoveryId = discoveryId,
                                    kind = DocumentCoverageDispositionKind.ReviewOnlyFinding,
                                    recommendationId = recommendation.id,
                                )
                            },
                        ),
                    ),
                )
            }
            if (retryFinalPlanningOnce && finalPlanningCalls == 1) {
                return DocumentFinalPlanningProviderResult.Failed(
                    retryable = true,
                    safeCode = "document-provider-malformed-output",
                )
            }
            val discoveryIds = request.discoveries.map { it.id }.sorted()
            if (degradeFinalPlanCorrection && finalPlanningCalls == 2) {
                val evidenceIds = request.discoveries
                    .flatMap(DocumentDiscovery::evidence)
                    .map(DocumentEvidence::id)
                    .distinct()
                    .sortedBy(DocumentEvidenceId::value)
                val finding = DocumentReviewOnlyFinding(
                    id = "finding-review-supplier",
                    summary = "Review Supplier only",
                    reason = "The correction omitted the supported executable change.",
                    discoveryIds = discoveryIds,
                    evidenceIds = evidenceIds,
                )
                val recommendation = DocumentFinalRecommendation(
                    id = "recommendation-review-only",
                    title = "Review Supplier only",
                    description = "Retain the supplier meaning for review.",
                    discoveryIds = discoveryIds,
                    evidenceIds = evidenceIds,
                    reviewOnlyFindings = listOf(finding),
                    confidence = DocumentConfidenceDimensions(90, 70, 70),
                    status = DocumentFinalRecommendationStatus.ReviewOnly,
                )
                return DocumentFinalPlanningProviderResult.Completed(
                    DocumentFinalPlanningResponse(
                        plan = DocumentFinalPlan(
                            workKey = request.workKey,
                            verifiedDiscoveryIds = discoveryIds,
                            criticFindingIds = emptyList(),
                            recommendations = listOf(recommendation),
                            coverage = discoveryIds.map { discoveryId ->
                                DocumentCoverageDisposition(
                                    discoveryId = discoveryId,
                                    kind = DocumentCoverageDispositionKind.ReviewOnlyFinding,
                                    recommendationId = recommendation.id,
                                )
                            },
                        ),
                    ),
                )
            }
            val label = if (compoundConcept) "Account closure" else "Supplier"
            val executableRecommendations = request.discoveries.sortedBy { it.id }.mapIndexed { index, discovery ->
                val localName = when {
                    compoundConcept -> "AccountClosure"
                    request.discoveries.size == 1 -> "Supplier"
                    else -> "Supplier${index + 1}"
                }
                DocumentFinalRecommendation(
                    id = "recommendation-${index + 1}",
                    title = "Create $label",
                    description = "Create the evidence-grounded business concept.",
                    discoveryIds = listOf(discovery.id),
                    evidenceIds = discovery.evidence.map { it.id }.sortedBy { it.value },
                    operations = listOf(
                        DocumentPlanOperation(
                            id = "create-concept-${index + 1}",
                            kind = DocumentPlanOperationKind.CreateClass,
                            order = 0,
                            declaration = DocumentTemporaryReference("new:class:$localName"),
                            operands = listOf(
                                DocumentPlanOperand.SourceId(request.ontologySnapshot.writableSourceIds.first()),
                            ),
                            expandedTypedEditCount = 1,
                        ),
                    ),
                    confidence = DocumentConfidenceDimensions(95, 90, 90),
                    status = DocumentFinalRecommendationStatus.Executable,
                )
            }.sortedBy(DocumentFinalRecommendation::stableOrderingKey)
            val recommendations = if (degradeFinalPlanCorrection && finalPlanningCalls == 1) {
                (
                    executableRecommendations + DocumentFinalRecommendation(
                        id = "recommendation-needs-correction",
                        title = "Invalid duplicate Supplier proposal",
                        description = "This intentionally triggers the existing correction path.",
                        discoveryIds = discoveryIds,
                        evidenceIds = request.discoveries
                            .flatMap(DocumentDiscovery::evidence)
                            .map(DocumentEvidence::id)
                            .distinct()
                            .sortedBy(DocumentEvidenceId::value),
                        confidence = DocumentConfidenceDimensions(90, 70, 70),
                        status = DocumentFinalRecommendationStatus.Blocked,
                        blockers = listOf("operation-contract-invalid"),
                    )
                ).sortedBy(DocumentFinalRecommendation::stableOrderingKey)
            } else {
                executableRecommendations
            }
            val recommendationByDiscovery = executableRecommendations.associateBy { it.discoveryIds.single() }
            return DocumentFinalPlanningProviderResult.Completed(
                DocumentFinalPlanningResponse(
                    plan = DocumentFinalPlan(
                        workKey = request.workKey,
                        verifiedDiscoveryIds = discoveryIds,
                        criticFindingIds = emptyList(),
                        recommendations = recommendations,
                        coverage = discoveryIds.map { discoveryId ->
                            DocumentCoverageDisposition(
                                discoveryId,
                                DocumentCoverageDispositionKind.ExecutableRecommendation,
                                recommendationId = recommendationByDiscovery.getValue(discoveryId).id,
                            )
                        },
                    ),
                ),
            )
        }

        override suspend fun planSemantic(
            apiKey: String,
            selectedModelId: String,
            systemInstruction: String,
            request: DocumentFinalPlanningRequest,
        ): DocumentSemanticPlanningProviderResult {
            assertContains(systemInstruction, "strict Phase 11.5+ semantic-plan response")
            assertContains(systemInstruction, "Never emit Entio operations")
            finalPlanningCalls += 1
            finalPlanningInstructions += systemInstruction
            if (retryFinalPlanningOnce && finalPlanningCalls == 1) {
                return DocumentSemanticPlanningProviderResult.Failed(
                    retryable = true,
                    safeCode = "document-semantic-plan-reference-invalid",
                )
            }
            val discoveries = request.discoveries.sortedBy(DocumentDiscovery::stableOrderingKey)
            val discoveryIds = discoveries.map(DocumentDiscovery::id).sorted()
            val businessDiscoveries = discoveries.filter {
                it.contentClassification.name == "BusinessContent"
            }
            val correctionReviewOnly = degradeFinalPlanCorrection && finalPlanningCalls == 2
            val forceBlocked = invalidFinalPlanOnce && finalPlanningCalls == 1
            val items = businessDiscoveries.mapIndexed { index, discovery ->
                DocumentSemanticPlanItem(
                    id = "semantic-${index + 1}",
                    kind = DocumentSemanticItemKind.Class,
                    label = if (compoundConcept) "Account closure" else {
                        if (businessDiscoveries.size == 1) "Supplier" else "Supplier ${index + 1}"
                    },
                    definition = "An evidence-grounded business concept.",
                    discoveryIds = listOf(discovery.id),
                    evidenceIds = discovery.evidence.map(DocumentEvidence::id).sortedBy(DocumentEvidenceId::value),
                    rationale = "Verified evidence defines this reusable concept.",
                    outcome = when {
                        correctionReviewOnly -> DocumentSemanticOutcome.ReviewOnly
                        forceBlocked -> DocumentSemanticOutcome.Blocked
                        else -> DocumentSemanticOutcome.Executable
                    },
                    confidence = DocumentConfidenceDimensions(95, 90, 90),
                )
            }.sortedBy(DocumentSemanticPlanItem::stableOrderingKey)
            val semanticGroups = items.mapIndexed { index, item ->
                DocumentSemanticRecommendationGroup(
                    id = "recommendation-${index + 1}",
                    title = if (correctionReviewOnly) "Review Supplier only" else
                        "Create ${if (compoundConcept) "Account closure" else item.label}",
                    description = "Treat the verified business concept faithfully.",
                    itemIds = listOf(item.id),
                    discoveryIds = item.discoveryIds,
                    evidenceIds = item.evidenceIds,
                    outcome = when {
                        correctionReviewOnly -> DocumentSemanticOutcome.ReviewOnly
                        forceBlocked -> DocumentSemanticOutcome.Blocked
                        else -> DocumentSemanticOutcome.Executable
                    },
                    rationale = if (correctionReviewOnly) {
                        "Retain the complete meaning for human review."
                    } else {
                        "Compile the verified reusable concept."
                    },
                    confidence = DocumentConfidenceDimensions(95, 90, 90),
                )
            }
            val coverage = discoveries.map { discovery ->
                val recommendationId = items.indexOfFirst { discovery.id in it.discoveryIds }
                    .takeIf { it >= 0 }
                    ?.let { "recommendation-${it + 1}" }
                when {
                    discovery.contentClassification.name == "AdministrativeMetadata" ->
                        DocumentCoverageDisposition(
                            discovery.id,
                            DocumentCoverageDispositionKind.AdministrativeMetadata,
                        )
                    forceBlocked -> DocumentCoverageDisposition(
                        discovery.id,
                        DocumentCoverageDispositionKind.Blocked,
                        rationale = "Deterministic correction is required.",
                    )
                    correctionReviewOnly -> DocumentCoverageDisposition(
                        discovery.id,
                        DocumentCoverageDispositionKind.ReviewOnlyFinding,
                        recommendationId = recommendationId,
                    )
                    else -> DocumentCoverageDisposition(
                        discovery.id,
                        DocumentCoverageDispositionKind.ExecutableRecommendation,
                        recommendationId = recommendationId,
                    )
                }
            }.sortedBy(DocumentCoverageDisposition::stableOrderingKey)
            val groups = (
                semanticGroups + if (degradeFinalPlanCorrection && finalPlanningCalls == 1) {
                    listOf(
                        semanticGroups.first().copy(
                            id = "recommendation-needs-correction",
                            title = "Blocked duplicate Supplier treatment",
                            outcome = DocumentSemanticOutcome.Blocked,
                            rationale = "This deterministic failure exercises the bounded correction path.",
                        ),
                    )
                } else {
                    emptyList()
                }
            ).sortedBy(DocumentSemanticRecommendationGroup::stableOrderingKey)
            return DocumentSemanticPlanningProviderResult.Completed(
                DocumentSemanticPlanningResponse(
                    plan = DocumentSemanticPlan(
                        workKey = request.workKey,
                        verifiedDiscoveryIds = discoveryIds,
                        criticFindingIds = request.criticFindings.map { it.id }.sorted(),
                        items = items,
                        groups = groups,
                    ),
                    coverage = coverage,
                ),
            )
        }
    }

    private fun settings(now: Instant, ready: Boolean): InMemoryAiUserProviderSettingsStore =
        InMemoryAiUserProviderSettingsStore().also { store ->
            store.save(
                AiUserProviderSettings(
                    userId = "alice",
                    providerId = "openai",
                    credentialGeneration = 1,
                    credentialStatus = AiSettingsCredentialStatus.VALID,
                    discoveryStatus = AiModelDiscoveryStatus.COMPLETED,
                    discoveredAt = now,
                    policyVersion = "test-policy",
                    candidates = listOf(
                        AiSelectableModelDescriptor(
                            providerId = "openai",
                            modelId = "gpt-test",
                            displayName = "Test",
                            description = "Test",
                            metadataKnown = true,
                            recommended = true,
                            capabilityTier = null,
                            timeoutClass = null,
                            relativeSpeed = null,
                            relativeCost = null,
                            verificationStatus = if (ready) {
                                AiModelVerificationStatus.VERIFIED
                            } else {
                                AiModelVerificationStatus.NOT_VERIFIED
                            },
                            compatibilityState = if (ready) {
                                AiModelCompatibilityState.AVAILABLE_AND_COMPATIBLE
                            } else {
                                AiModelCompatibilityState.CANDIDATE_REQUIRES_VERIFICATION
                            },
                            policyVersion = "test-policy",
                        ),
                    ),
                    unsupportedProviderModelCount = 0,
                    selectedModelId = "gpt-test",
                    selectedModelVerifiedAt = now,
                    selectionStatus = if (ready) AiModelSelectionStatus.READY else AiModelSelectionStatus.NOT_SELECTED,
                    lastProviderErrorCategory = null,
                ),
            )
        }

    private fun metadata(index: Int = 1): DocumentUploadMetadata = DocumentUploadMetadata(
        clientDocumentId = "client-$index",
        filename = "policy-$index.txt",
        declaredMediaType = "text/plain",
        language = "en",
        authorityStatus = "Authoritative",
    )

    private data class Fixture(
        val manager: DocumentIngestionTaskManager,
        val reviews: DocumentReviewWorkspaceStore,
        val intake: DocumentIntakeService,
        val orchestrator: DocumentIngestionOrchestrator,
        val provider: TestPipelineProvider,
        val source: java.nio.file.Path,
    ) {
        fun close(): Unit {
            orchestrator.close()
            manager.close()
        }
    }
}
