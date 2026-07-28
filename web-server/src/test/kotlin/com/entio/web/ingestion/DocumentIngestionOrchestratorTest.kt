package com.entio.web.ingestion

import com.entio.core.DocumentAnalysisStage
import com.entio.core.DocumentConfidenceDimensions
import com.entio.core.DocumentCoverageDisposition
import com.entio.core.DocumentCoverageDispositionKind
import com.entio.core.DocumentFinalPlan
import com.entio.core.DocumentFinalRecommendation
import com.entio.core.DocumentFinalRecommendationStatus
import com.entio.core.DocumentPlanOperand
import com.entio.core.DocumentPlanOperation
import com.entio.core.DocumentPlanOperationKind
import com.entio.core.DocumentTemporaryReference
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

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
        assertTrue(task.updates.any { it.message.contains("connected model") })
        assertTrue(task.updates.any { it.message.contains("Critiquing modeling quality") })
        assertEquals(
            listOf(
                DocumentAnalysisStage.Discovery,
                DocumentAnalysisStage.ConnectedModeling,
                DocumentAnalysisStage.Reconciliation,
                DocumentAnalysisStage.OntologyAlignment,
                DocumentAnalysisStage.ModelingCritic,
                DocumentAnalysisStage.FinalPlanning,
                DocumentAnalysisStage.DeterministicVerification,
            ),
            task.analysisStages.map { it.stage },
        )
        val recommendation = fixture.reviews.verifiedPlan("simple", taskId.value, "alice")
            .plan.recommendations.single()
        assertEquals("Create Supplier", recommendation.title)
        assertEquals(before.toList(), Files.readAllBytes(fixture.source).toList())
        assertTrue(directory.path.toFile().exists())
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
        assertEquals("awaiting-review", task.status)
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
    fun reportsSafeSpecificProviderFailureWithoutExposingProviderPayloads(): Unit = runBlocking {
        val fixture = fixture(
            readyModel = true,
            providerFailure = DocumentAnalysisProviderResult.Failed(
                retryable = false,
                safeCode = "document-provider-request-rejected",
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
        assertEquals("The model provider rejected Entio's document-analysis request.", task.progress.message)
        assertTrue(task.updates.last().message.contains("rejected"))
        assertTrue(directory.path.toFile().exists().not())
        fixture.close()
    }

    private fun fixture(
        readyModel: Boolean,
        providerFailure: DocumentAnalysisProviderResult.Failed? = null,
        compoundConcept: Boolean = false,
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
            discoveryFailureCode = providerFailure?.safeCode,
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
        return Fixture(manager, reviews, DocumentIntakeService(configuration, storage), orchestrator, source)
    }

    private class TestPipelineProvider(
        private val compoundConcept: Boolean,
        private val discoveryFailureCode: String?,
    ) : DocumentPipelineProvider {
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
            discoveryFailureCode?.let { return DocumentDiscoveryProviderResult.Failed(false, it) }
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
                    ),
                ),
            )
        }

        override suspend fun model(
            apiKey: String,
            selectedModelId: String,
            systemInstruction: String,
            request: DocumentConnectedModelRequest,
        ): DocumentConnectedModelProviderResult =
            DocumentConnectedModelProviderResult.CompletedModel(
                DocumentConnectedModelResponse(
                    items = listOf(
                        ProviderConnectedModelItem(
                            providerId = "model-concept",
                            kind = "Class",
                            label = if (compoundConcept) "Account closure" else "Supplier",
                            rationale = "The verified discovery describes a business concept.",
                            discoveryIds = request.discoveries.map { it.id }.sorted(),
                            references = emptyList(),
                            literalLexicalForm = null,
                            literalDatatypeIri = null,
                            literalLanguageTag = null,
                            order = 0,
                            reviewOnlyEligible = false,
                        ),
                    ),
                ),
            )

        override suspend fun consolidate(
            apiKey: String,
            selectedModelId: String,
            systemInstruction: String,
            request: DocumentModelConsolidationRequest,
        ): DocumentConnectedModelProviderResult =
            DocumentConnectedModelProviderResult.CompletedConsolidation(
                DocumentModelConsolidationResponse(items = request.chunkModels.first().items),
            )

        override suspend fun reconcile(
            apiKey: String,
            selectedModelId: String,
            systemInstruction: String,
            request: DocumentReconciliationRequest,
        ): DocumentReconciliationProviderResult =
            DocumentReconciliationProviderResult.Completed(DocumentReconciliationResponse(records = emptyList()))

        override suspend fun align(
            apiKey: String,
            selectedModelId: String,
            systemInstruction: String,
            request: DocumentOntologyAlignmentRequest,
        ): DocumentOntologyAlignmentProviderResult =
            DocumentOntologyAlignmentProviderResult.Completed(
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

        override suspend fun critique(
            apiKey: String,
            selectedModelId: String,
            systemInstruction: String,
            request: DocumentModelingCriticRequest,
        ): DocumentModelingCriticProviderResult =
            DocumentModelingCriticProviderResult.Completed(DocumentModelingCriticResponse(findings = emptyList()))

        override suspend fun plan(
            apiKey: String,
            selectedModelId: String,
            systemInstruction: String,
            request: DocumentFinalPlanningRequest,
        ): DocumentFinalPlanningProviderResult {
            val discoveryIds = request.discoveries.map { it.id }.sorted()
            val label = if (compoundConcept) "Account closure" else "Supplier"
            val recommendations = request.discoveries.sortedBy { it.id }.mapIndexed { index, discovery ->
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
            val recommendationByDiscovery = recommendations.associateBy { it.discoveryIds.single() }
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
        val source: java.nio.file.Path,
    ) {
        fun close(): Unit {
            orchestrator.close()
            manager.close()
        }
    }
}
