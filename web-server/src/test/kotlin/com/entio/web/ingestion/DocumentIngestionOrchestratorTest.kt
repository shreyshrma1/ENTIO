package com.entio.web.ingestion

import com.entio.core.DocumentCandidateCategory
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
import com.entio.web.contract.WebPageRequest
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
        assertTrue(task.updates.any { it.message.contains("Waiting for the selected model to analyze document 1 of 1") })
        assertTrue(task.updates.any { it.message.contains("verifying its evidence") })
        assertTrue(task.updates.any { it.message.contains("evidence-grounded candidates") })
        val review = fixture.reviews.read("simple", taskId.value, "alice", WebPageRequest())
        val recommendation = review.recommendations.items.single()
        assertEquals("Supplier", recommendation.proposedLabel)
        assertEquals("CreateLocal", recommendation.action)
        assertEquals("simple", recommendation.targetSourceId)
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

        val recommendation = fixture.reviews
            .read("simple", taskId.value, "alice", WebPageRequest())
            .recommendations
            .items
            .single()
        assertEquals("Account closure", recommendation.proposedLabel)
        assertTrue(recommendation.changePreview.draftable)
        assertEquals(
            listOf(
                "Create class",
                "Add definition",
                "Create relationship",
                "Set relationship source",
                "Set relationship target",
            ),
            recommendation.changePreview.operations.map { it.operation },
        )
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
        assertTrue(task.documents.single().safeFilename == "policy.txt")
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
        val configuration = DocumentIngestionConfiguration(
            temporaryRoot = temporary,
            provenanceRoot = Files.createTempDirectory("entio-orchestration-provenance"),
            clock = Clock.fixed(now, ZoneOffset.UTC),
            idFactory = sequenceOf("one", "two", "three").iterator()::next,
        )
        val storage = DocumentTemporaryStorage(temporary)
        val manager = DocumentIngestionTaskManager(configuration, storage)
        val reviews = DocumentReviewWorkspaceStore(configuration.clock)
        val credentials = InMemoryAiCredentialStore().also { it.save("alice", "openai", "secret") }
        val settings = settings(now, readyModel)
        val provider = DocumentAnalysisProvider { _, _, _, request ->
            if (providerFailure != null) return@DocumentAnalysisProvider providerFailure
            val block = request.blocks.single()
            val proposedLabel = if (compoundConcept) "Account closure" else "Supplier"
            val excerpt = if (compoundConcept) block.text else "Supplier"
            DocumentAnalysisProviderResult.Completed(
                DocumentAnalysisResponse(
                    candidates = listOf(
                        ProviderDocumentCandidate(
                            category = DocumentCandidateCategory.Class.name,
                            recommendationCategory = "OntologyStructure",
                            proposedLabel = proposedLabel,
                            confidence = 95,
                            interpretation = "explicit",
                            evidenceType = "Explicit",
                            evidence = listOf(
                                ProviderEvidenceClaim(
                                    block.documentId,
                                    block.blockId,
                                    0,
                                    excerpt.length,
                                    excerpt,
                                ),
                            ),
                            proposedDefinition = excerpt.takeIf { compoundConcept },
                            proposedConnectionLabel = "has account closure".takeIf { compoundConcept },
                            proposedConnectionDomainIri = "https://example.com/simple#Account".takeIf { compoundConcept },
                        ),
                    ),
                ),
            )
        }
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

    private fun metadata(): DocumentUploadMetadata = DocumentUploadMetadata(
        clientDocumentId = "client-1",
        filename = "policy.txt",
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
