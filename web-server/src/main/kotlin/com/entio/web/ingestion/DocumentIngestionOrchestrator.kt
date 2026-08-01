package com.entio.web.ingestion

import com.entio.core.DocumentAnalysisPipelineVersions
import com.entio.core.DocumentAnalysisStage
import com.entio.core.DocumentAnalysisStageRecord
import com.entio.core.DocumentAnalysisStageState
import com.entio.core.DocumentAnalysisWorkKey
import com.entio.core.DocumentAssertionClassification
import com.entio.core.DocumentContentClassification
import com.entio.core.DocumentDiscovery
import com.entio.core.DocumentDiscoveryKind
import com.entio.core.DocumentEvidence
import com.entio.core.DocumentEvidenceReference
import com.entio.core.DocumentEvidenceType
import com.entio.core.DocumentExtractionMethod
import com.entio.core.DocumentGroundedWorkKeyInputs
import com.entio.core.DocumentIndividualClassification
import com.entio.core.DocumentCandidateCategory
import com.entio.core.DocumentMatchScope
import com.entio.core.DocumentProcessingStatus
import com.entio.core.DocumentTemporaryReferenceKind
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.Iri
import com.entio.core.LocalityStatus
import com.entio.core.OntologyEntityDescriptor
import com.entio.core.SemanticDescriptorKind
import com.entio.core.ShaclGraphRole
import com.entio.semantic.DocumentPlanVerificationContext
import com.entio.semantic.DocumentGroundedAnalysisVerifier
import com.entio.semantic.DocumentGroundedVerificationInput
import com.entio.semantic.DocumentOntologyRetrievalService
import com.entio.semantic.DocumentSemanticCompilerContext
import com.entio.semantic.DocumentSemanticRecord
import com.entio.semantic.ProjectLoader
import com.entio.semantic.SemanticDescriptionService
import com.entio.web.ai.AiCredentialStore
import com.entio.web.ai.models.AiUserProviderSettingsStore
import com.entio.web.ai.models.AiModelSelectionStatus
import com.entio.web.contract.ProjectRegistry
import com.entio.web.webGraphFingerprint
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Connects the bounded Phase 11 services into one task-owned production workflow. */
internal class DocumentIngestionOrchestrator(
    private val tasks: DocumentIngestionTaskManager,
    private val reviews: DocumentReviewWorkspaceStore,
    private val configuration: DocumentIngestionConfiguration,
    private val projectRegistry: ProjectRegistry,
    private val provenance: AppliedDocumentProvenanceRepository,
    private val credentials: AiCredentialStore,
    private val settings: AiUserProviderSettingsStore,
    provider: DocumentAnalysisProvider,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val descriptions: SemanticDescriptionService = SemanticDescriptionService(),
    private val stagingSnapshot: ((String) -> com.entio.web.contract.WebStagingResponse?) = { null },
) : AutoCloseable {
    private val jobs: MutableMap<String, Job> = linkedMapOf()
    private val processingInputs: MutableMap<String, DocumentIngestionProcessingInput> = linkedMapOf()
    private val boundedProvider = BudgetedDocumentPipelineProvider(
        provider as? DocumentPipelineProvider
            ?: throw IllegalArgumentException("Document ingestion requires the Phase 11.5 pipeline provider."),
    )
    private val discovery = DocumentDiscoveryService(
        credentials,
        settings,
        boundedProvider,
        clock = configuration.clock,
        isCancelled = ::isCancelled,
    )
    private val modeling = DocumentConnectedModelingService(
        credentials,
        settings,
        boundedProvider,
        clock = configuration.clock,
        isCancelled = ::isCancelled,
    )
    private val finalPlanning = DocumentFinalPlanningService(
        credentials,
        settings,
        SemanticCompilingDocumentFinalPlanningProvider(boundedProvider),
        clock = configuration.clock,
        isCancelled = ::isCancelled,
    )
    private val groundedProvider = provider as? DocumentGroundedAnalysisProvider
    private val groundedCompiler = SemanticCompilingDocumentFinalPlanningProvider(boundedProvider)

    @Synchronized
    fun start(taskId: String, projectId: String, userId: String): Unit {
        check(taskId !in jobs) { "Document ingestion task processing was already started." }
        jobs[taskId] = scope.launch {
            try {
                process(tasks.processingInput(com.entio.core.DocumentTaskId(taskId), projectId, userId))
            } catch (failure: Exception) {
                handleFailure(taskId, projectId, userId, failure)
            } finally {
                synchronized(this@DocumentIngestionOrchestrator) {
                    jobs.remove(taskId)
                }
            }
        }
    }

    suspend fun await(taskId: String): Unit {
        val job = synchronized(this) { jobs[taskId] }
        job?.join()
    }

    @Synchronized
    fun cancel(taskId: String): Unit {
        jobs[taskId]?.cancel(CancellationException("Document generation was stopped by the user."))
    }

    override fun close(): Unit {
        scope.cancel()
        boundedProvider.close()
    }

    private suspend fun process(input: DocumentIngestionProcessingInput): Unit {
        synchronized(processingInputs) {
            processingInputs[input.taskId.value] = input
        }
        try {
            processCurrent(input)
        } finally {
            synchronized(processingInputs) {
                processingInputs.remove(input.taskId.value)
            }
        }
    }

    private suspend fun processCurrent(input: DocumentIngestionProcessingInput): Unit {
        val isCancelled = {
            tasks.isCancelled(input.taskId, input.projectId, input.ownerUserId)
        }
        val extraction = DocumentExtractionService(configuration, isCancelled = isCancelled)
        val extracted = input.documents.mapIndexed { index, document ->
            checkCancellation(input)
            extraction.extract(document, input.directory).also {
                tasks.transition(
                    input.taskId,
                    input.projectId,
                    input.ownerUserId,
                    DocumentProcessingStatus.Extracting,
                    index + 1,
                    ((index + 1) * 35) / input.documents.size,
                    "Extracted ${index + 1} of ${input.documents.size} documents.",
                )
            }
        }
        if (configuration.groundedAnalysisEnabled) {
            processGrounded(input, extracted, isCancelled)
            return
        }
        val project = loadProject(input.projectId)
        val ontologyFingerprint = webGraphFingerprint(project.graph)
        val currentWorkFingerprint = ontologyFingerprint
        require(input.documents.size + REQUIRED_POST_DISCOVERY_LOGICAL_CALLS <=
            com.entio.core.MAX_DOCUMENT_PLANNED_LOGICAL_CALLS) {
            "The complete analysis cannot fit the approved logical-call budget."
        }
        tasks.transition(
            input.taskId,
            input.projectId,
            input.ownerUserId,
            DocumentProcessingStatus.Analyzing,
            input.documents.size,
            40,
            "Discovering evidence-grounded meaning in each document.",
        )
        checkCancellation(input)
        val writableSourceIds = project.resolvedSources
            .filter { ShaclGraphRole.Ontology in it.roles }
            .map { it.id }
            .distinct()
            .sorted()
        val discoveredDocuments = extracted
            .sortedBy { it.document.id.value }
            .map { document ->
                discovery.discover(
                    input.ownerUserId,
                    input.taskId.value,
                    document,
                ).also { completed ->
                    // Persist each completed call immediately so a later document failure
                    // does not erase useful progress and timing from the task history.
                    recordStage(input, completed.stageRecord)
                }
            }
        val discoveryResult = CompletedDocumentDiscoveryStage(discoveredDocuments)
        val filenamesByDocumentId = input.documents.associate {
            it.document.id.value to it.document.safeFilename
        }
        val discoverySkipCount = discoveredDocuments.sumOf { it.skipped.size }
        val discoveryDetails = discoveredDocuments
            .filter { it.skipped.isNotEmpty() }
            .map { completed ->
                val groupedCodes = completed.skipped
                    .groupingBy(DocumentDiscoverySkip::safeCode)
                    .eachCount()
                    .toSortedMap()
                    .entries
                    .joinToString("; ") { (code, count) -> "$count × $code" }
                "${filenamesByDocumentId[completed.documentId] ?: completed.documentId}: retained " +
                    "${completed.discoveries.size} of ${completed.stageRecord.totalCount} provider findings; " +
                    "rejected $groupedCodes."
            }
        announce(
            input,
            50,
            if (discoverySkipCount == 0) {
                "Verified discovery is complete for ${extracted.size} document(s)."
            } else {
                "Verified discovery retained ${discoveryResult.discoveries.size} evidence-grounded meaning(s) and rejected " +
                    "$discoverySkipCount invalid provider finding(s)."
            },
            discoveryDetails,
        )
        checkCancellation(input)
        var remainingLogicalCalls = com.entio.core.MAX_DOCUMENT_PLANNED_LOGICAL_CALLS - input.documents.size
        announce(input, 52, "Synthesizing connected meaning across verified discoveries.")
        val connected = modeling.model(
            input.ownerUserId,
            input.taskId.value,
            discoveryResult,
            remainingLogicalCalls,
        )
        connected.stageRecords.forEach { recordStage(input, it) }
        remainingLogicalCalls -= connected.logicalCalls
        checkCancellation(input)
        val displayedItems = connected.model.items.take(MAX_REPORTED_CONNECTED_MODEL_ITEMS)
        val retainedDetails = displayedItems.map { item ->
            "Retained ${item.kind.name}: '${item.label}'."
        } + if (connected.model.items.size > displayedItems.size) {
            listOf(
                "${connected.model.items.size - displayedItems.size} additional retained items were omitted " +
                    "from this bounded status update.",
            )
        } else {
            emptyList()
        }
        val displayedSkips = connected.skippedItems.take(MAX_REPORTED_CONNECTED_MODEL_SKIPS)
        val unrepresentedDetails = connected.unrepresentedDocumentIds.map { documentId ->
            "Connected synthesis did not represent " +
                (filenamesByDocumentId[documentId] ?: documentId) +
                "; final planning will use its verified discoveries directly."
        }
        val details = displayedSkips.map(DocumentConnectedModelSkip::statusDetail) +
            if (connected.skippedItems.size > displayedSkips.size) {
                listOf(
                    "${connected.skippedItems.size - displayedSkips.size} additional skipped items were omitted " +
                        "from this bounded status update.",
                )
            } else {
                emptyList()
            } +
            retainedDetails +
            unrepresentedDetails
        announce(
            input,
            66,
            if (connected.model.items.isEmpty()) {
                "Semantic synthesis could not retain a valid connected item; continuing from verified discoveries."
            } else if (connected.unrepresentedDocumentIds.isNotEmpty()) {
                "Semantic synthesis retained ${connected.model.items.size} valid item(s); " +
                    "continuing with verified discoveries for " +
                    "${connected.unrepresentedDocumentIds.size} unrepresented document(s)."
            } else if (connected.skippedItems.isNotEmpty()) {
                "Semantic synthesis retained ${connected.model.items.size} valid item(s) and skipped " +
                    "${connected.skippedItems.size} invalid item(s)."
            } else {
                "Semantic synthesis retained ${connected.model.items.size} valid item(s)."
            },
            details,
        )
        announce(input, 68, "Semantic synthesis is complete; preparing current ontology context.")
        val snapshot = alignmentSnapshot(
            input.projectId,
            project,
            ontologyFingerprint,
            currentWorkFingerprint,
            writableSourceIds,
        )
        checkCancellation(input)
        announce(input, 76, "Compiling connected meaning into grouped recommendations and exact change sets.")
        val workKey = workKey(input, ontologyFingerprint)
        val finalResult = finalPlanning.planStreamlined(
            input.ownerUserId,
            input.taskId.value,
            workKey,
            discoveryResult,
            connected,
            snapshot,
            extracted.associate { it.document.id.value to it.document.authority }.toSortedMap(),
            provenance.summaries(input.projectId),
            DocumentPlanVerificationContext(
                expectedOntologyFingerprint = ontologyFingerprint,
                currentOntologyFingerprint = webGraphFingerprint(loadProject(input.projectId).graph),
                expectedCurrentWorkFingerprint = currentWorkFingerprint,
                currentWorkFingerprint = currentWorkFingerprint,
                writableSourceIds = writableSourceIds.toSet(),
                existingEntityKinds = existingEntityKinds(project),
                iriNamespace = project.config.iriNamespace?.namespace?.value
                    ?: project.symbols.firstOrNull { it.sourceId in writableSourceIds }?.iri?.value
                        ?.substringBeforeLast('#')
                    ?: throw DocumentAnalysisFailure(
                        "document-iri-namespace-missing",
                        "A writable ontology namespace is required for final planning.",
                    ),
                discoveryKinds = discoveryResult.discoveries.associate { it.id to it.kind },
                discoveryContentClassifications = discoveryResult.discoveries.associate {
                    it.id to it.contentClassification
                },
                discoveryIndividualClassifications = discoveryResult.discoveries.associate {
                    it.id to it.individualClassification
                },
            ),
        )
        recordStage(input, finalResult.stageRecord)
        checkCancellation(input)
        val verificationStarted = Instant.now(configuration.clock)
        recordStage(
            input,
            DocumentAnalysisStageRecord(
                recordId = "stage-verification-${workKey.sha256.take(24)}",
                stage = DocumentAnalysisStage.DeterministicVerification,
                state = DocumentAnalysisStageState.Succeeded,
                scopeId = input.taskId.value,
                startedAt = verificationStarted,
                finishedAt = Instant.now(configuration.clock),
                durationMillis = 0,
                inputSha256 = finalResult.stageRecord.outputSha256,
                outputSha256 = hash(finalResult.verifiedPlan.plan),
                completedCount = finalResult.verifiedPlan.plan.recommendations.size,
                totalCount = finalResult.verifiedPlan.plan.recommendations.size,
            ),
        )
        reviews.installVerifiedPlan(
            tasks.find(input.taskId, input.projectId, input.ownerUserId),
            workKey.sha256,
            ontologyFingerprint,
            finalResult.verifiedPlan,
            extracted,
            discoveryResult.discoveries,
        )
        tasks.transition(
            input.taskId,
            input.projectId,
            input.ownerUserId,
            DocumentProcessingStatus.AwaitingReview,
            input.documents.size,
            100,
            "Grouped evidence-linked recommendations are ready for review.",
        )
    }

    private suspend fun processGrounded(
        input: DocumentIngestionProcessingInput,
        extracted: List<ExtractedDocument>,
        isCancelled: () -> Boolean,
    ): Unit {
        val project = loadProject(input.projectId)
        val ontologyFingerprint = webGraphFingerprint(project.graph)
        val candidateExtractor = DocumentCandidateExtractionService(configuration)
        val mentions = extracted.sortedBy { it.document.id.value }.flatMap { document ->
            candidateExtractor.extractMentions(document.document, document.blocks)
        }.sortedBy(com.entio.core.DocumentEvidenceMention::stableOrderingKey)
        val extraction = candidateExtractor.promoteCandidates(mentions, ontologyLabels(project))
        val candidates = extraction.candidates
        require(candidates.isNotEmpty()) { "document-candidate-extraction-empty" }
        val candidateStarted = Instant.now(configuration.clock)
        recordGroundedStage(input, DocumentAnalysisStage.SemanticAssembly, "candidates", candidateStarted,
            hash(extracted), hash(extraction), candidates.size, 0)
        announce(
            input,
            48,
            "Grouped ${mentions.size} evidence mention(s) into ${candidates.size} ontology-bearing candidate(s).",
            listOf(
                "Document-only mentions: ${extraction.coverage.count { it.kind == com.entio.core.DocumentMentionCoverageKind.DocumentOnly }}.",
                "Supporting values: ${extraction.coverage.count { it.kind == com.entio.core.DocumentMentionCoverageKind.SupportingValue }}.",
                "Rejected low-value mentions: ${extraction.coverage.count { it.kind == com.entio.core.DocumentMentionCoverageKind.Rejected }}.",
            ),
        )
        checkCancellation(input)

        val retrievalContext = DocumentRetrievalContextFactory().create(
            input.projectId,
            project,
            candidates,
            ontologyFingerprint,
            stagingSnapshot(input.projectId),
            provenance.summaries(input.projectId),
        )
        val retrievalStarted = Instant.now(configuration.clock)
        val retrieval = DocumentOntologyRetrievalService(descriptions).retrieve(retrievalContext.input)
        recordGroundedStage(input, DocumentAnalysisStage.SemanticAssembly, "retrieval", retrievalStarted,
            hash(candidates), hash(retrieval.results), retrieval.results.size, 0)
        announce(input, 62, "Completed authorized ontology retrieval for ${retrieval.results.size} candidate(s).")
        checkCancellation(input)

        val refreshed = DocumentRetrievalContextFactory().create(
            input.projectId,
            loadProject(input.projectId),
            candidates,
            webGraphFingerprint(loadProject(input.projectId).graph),
            stagingSnapshot(input.projectId),
            provenance.summaries(input.projectId),
        )
        require(refreshed.fingerprints == retrievalContext.fingerprints) { "document-retrieval-stale" }
        val selected = settings.find(input.ownerUserId)
            ?.takeIf { it.selectionStatus == AiModelSelectionStatus.READY && !it.selectedModelId.isNullOrBlank() }
            ?.selectedModelId
            ?: throw DocumentAnalysisFailure("document-model-not-ready", "A verified compatible model is required.")
        val workInputs = DocumentGroundedWorkKeyInputs(
            DocumentAnalysisPipelineVersions.WORK_KEY,
            input.projectId,
            input.taskId.value,
            hash(input.documents.map { it.document.checksumSha256 }),
            hash(mentions),
            hash(candidates),
            hash(retrieval.results),
            retrievalContext.fingerprints.ontologySha256,
            retrievalContext.fingerprints.currentWorkSha256,
            retrievalContext.fingerprints.provenanceSha256,
            retrievalContext.fingerprints.catalogSha256,
            configuration.candidateExtractorContractVersion,
            configuration.nlpResourceVersion,
            DocumentAnalysisPipelineVersions.RETRIEVAL_RANKING,
            selected,
            DocumentAnalysisPipelineVersions.GROUNDED_PROMPT,
            DocumentAnalysisPipelineVersions.GROUNDED_RESPONSE,
        )
        val workKey = DocumentAnalysisWorkKey(hash(workInputs))
        val groundedService = DocumentGroundedAnalysisService(
            groundedProvider ?: throw DocumentAnalysisFailure(
                "document-grounded-provider-missing",
                "The configured provider does not support grounded document analysis.",
            ),
            isCancelled,
        ) { completed, planned ->
            val percent = 62 + ((completed * 16) / planned.coerceAtLeast(1)).coerceIn(0, 16)
            announce(input, percent, "Grounded modeling completed $completed of $planned planned group(s).")
        }
        val modeledStarted = Instant.now(configuration.clock)
        val modeled = credentials.withCredentialSuspending(input.ownerUserId) { _, apiKey ->
            groundedService.analyze(apiKey, selected, input.taskId.value, candidates, retrieval.results)
        } ?: throw DocumentAnalysisFailure("document-credential-missing", "A provider credential is required.")
        val analysis = com.entio.core.DocumentGroundedAnalysisResult(
            DocumentAnalysisPipelineVersions.GROUNDED_RESPONSE,
            modeled.results.flatMap { it.items }.distinctBy { it.id }.sortedBy { it.stableOrderingKey },
            modeled.results.flatMap { it.coverage }.distinctBy { it.candidateId }.sortedBy { it.stableOrderingKey },
        )
        if (System.getenv("ENTIO_DOCUMENT_ANALYSIS_DEBUG") == "true") {
            System.err.println(
                "entio-document-analysis grounded-summary candidates=${candidates.size} items=${analysis.items.size} " +
                    "dispositions=${analysis.items.groupingBy { it.disposition }.eachCount().toSortedMap()} " +
                    "kinds=${analysis.items.groupingBy { it.kind }.eachCount().toSortedMap()} " +
                    "logicalCalls=${modeled.logicalCallCount} attempts=${modeled.providerAttemptCount}",
            )
        }
        recordGroundedStage(input, DocumentAnalysisStage.ConnectedModeling, "grounded", modeledStarted,
            hash(retrieval.results), hash(analysis), analysis.items.size, modeled.providerAttemptCount,
            selected, modeled.logicalCallCount)
        checkCancellation(input)

        val verified = DocumentGroundedAnalysisVerifier().verify(
            DocumentGroundedVerificationInput(
                workKey, candidates, retrieval.results, retrieval.fullStateMatches, analysis,
                ontologyFingerprint, webGraphFingerprint(loadProject(input.projectId).graph),
                retrievalContext.fingerprints.currentWorkSha256, refreshed.fingerprints.currentWorkSha256,
            ),
        )
        if (System.getenv("ENTIO_DOCUMENT_ANALYSIS_DEBUG") == "true") {
            System.err.println(
                "entio-document-analysis verification-summary groups=${verified.plan.groups.size} " +
                    "outcomes=${verified.plan.groups.groupingBy { it.outcome }.eachCount().toSortedMap()} " +
                    "statuses=${verified.statusByItemId.values.groupingBy { it }.eachCount().toSortedMap()} " +
                    "editableFields=${verified.editableFields.size}",
            )
        }
        val writableSourceIds = project.resolvedSources.filter { ShaclGraphRole.Ontology in it.roles }
            .map { it.id }.distinct().sorted()
        val compilerContext = DocumentSemanticCompilerContext(
            writableSourceIds.firstOrNull() ?: throw DocumentAnalysisFailure(
                "document-writable-source-missing", "A writable ontology source is required.",
            ),
            project.config.iriNamespace?.namespace?.value
                ?: throw DocumentAnalysisFailure("document-iri-namespace-missing", "A writable ontology namespace is required."),
            existingEntityKinds(project),
            verified.alignedEntities,
            verified.itemAlignmentIds,
            expectedOntologyFingerprint = ontologyFingerprint,
            currentOntologyFingerprint = webGraphFingerprint(loadProject(input.projectId).graph),
            expectedCurrentWorkFingerprint = retrievalContext.fingerprints.currentWorkSha256,
            currentWorkFingerprint = refreshed.fingerprints.currentWorkSha256,
        )
        val nonRecommendationCoverage = analysis.coverage.mapNotNull { disposition ->
            val kind = when (disposition.disposition) {
                com.entio.core.DocumentGroundedDisposition.Administrative ->
                    com.entio.core.DocumentCoverageDispositionKind.AdministrativeMetadata
                com.entio.core.DocumentGroundedDisposition.Illustrative ->
                    com.entio.core.DocumentCoverageDispositionKind.IllustrativeExample
                else -> null
            }
            kind?.let { disposition.candidateId to it }
        }.toMap()
        val planned = when (
            val result = groundedCompiler.compileGrounded(verified.plan, compilerContext, nonRecommendationCoverage)
        ) {
            is DocumentFinalPlanningProviderResult.Completed -> result.response.plan
            is DocumentFinalPlanningProviderResult.Failed -> throw DocumentAnalysisFailure(result.safeCode,
                "Grounded semantic compilation failed safely.")
        }
        val verificationContext = DocumentPlanVerificationContext(
            ontologyFingerprint, webGraphFingerprint(loadProject(input.projectId).graph),
            retrievalContext.fingerprints.currentWorkSha256, refreshed.fingerprints.currentWorkSha256,
            writableSourceIds.toSet(), existingEntityKinds(project), compilerContext.iriNamespace,
        )
        val finalPlan = com.entio.semantic.DocumentChangeSetPlanVerifier().verify(planned, verificationContext)
        val discoveries = groundedDiscoveries(candidates, extracted)
        val itemIdsByRecommendationId = verified.plan.groups.associate { group ->
            group.id to group.itemIds
        }
        val statuses = verified.statusByItemId.values
        val recommendationStatuses = verified.plan.groups.map { group ->
            group.itemIds.mapNotNull(verified.statusByItemId::get).toSet()
        }
        reviews.installVerifiedPlan(
            tasks.find(input.taskId, input.projectId, input.ownerUserId),
            workKey.sha256,
            ontologyFingerprint,
            finalPlan,
            extracted,
            discoveries,
            DocumentGroundedReviewContext(
                candidates = candidates,
                analysis = analysis,
                retrieval = retrieval.results,
                fullStateMatches = retrieval.fullStateMatches,
                compilerContext = compilerContext,
                verificationContext = verificationContext,
                nonRecommendationCoverage = nonRecommendationCoverage,
                mentionCoverage = extraction.coverage,
                editableFields = verified.editableFields,
                statusByItemId = verified.statusByItemId,
                itemIdsByRecommendationId = itemIdsByRecommendationId,
                counts = com.entio.core.DocumentAnalysisCounts(
                    evidenceBlocks = extracted.sumOf { it.blocks.size },
                    evidenceMentions = mentions.size,
                    groupedCandidates = extraction.groupedCandidateCount,
                    ontologyBearingCandidates = candidates.size,
                    documentOnlyMentions = extraction.coverage.count {
                        it.kind == com.entio.core.DocumentMentionCoverageKind.DocumentOnly
                    },
                    supportingValueMentions = extraction.coverage.count {
                        it.kind == com.entio.core.DocumentMentionCoverageKind.SupportingValue
                    },
                    nlpCandidatesRetained = candidates.size,
                    nlpCandidatesRejected = extraction.coverage.count {
                        it.kind == com.entio.core.DocumentMentionCoverageKind.Rejected
                    },
                    groundedItemsRetained = analysis.items.size,
                    groundedItemsUnresolved = analysis.coverage.count {
                        it.disposition == com.entio.core.DocumentGroundedDisposition.Unresolved
                    },
                    groundedItemsRejected = 0,
                    recommendationsExecutable = recommendationStatuses.count {
                        it == setOf(com.entio.core.DocumentGroundedRecommendationStatus.Executable)
                    },
                    recommendationsMixed = recommendationStatuses.count {
                        com.entio.core.DocumentGroundedRecommendationStatus.Executable in it && it.size > 1
                    },
                    recommendationsNeedsInput = statuses.count {
                        it == com.entio.core.DocumentGroundedRecommendationStatus.NeedsInput
                    },
                    recommendationsReviewOnly = statuses.count {
                        it == com.entio.core.DocumentGroundedRecommendationStatus.ReviewOnly
                    },
                    recommendationsBlocked = statuses.count {
                        it == com.entio.core.DocumentGroundedRecommendationStatus.Blocked
                    },
                    expandedTypedEdits = finalPlan.plan.recommendations.sumOf { it.operations.size },
                ),
            ),
        )
        tasks.transition(input.taskId, input.projectId, input.ownerUserId, DocumentProcessingStatus.AwaitingReview,
            input.documents.size, 100, "Grounded evidence-linked recommendations are ready for review.")
    }

    private fun recordGroundedStage(
        input: DocumentIngestionProcessingInput,
        stage: DocumentAnalysisStage,
        name: String,
        started: Instant,
        inputHash: String,
        outputHash: String,
        count: Int,
        attempts: Int,
        modelId: String? = null,
        logicalCalls: Int = count,
    ): Unit {
        val finished = Instant.now(configuration.clock)
        recordStage(
            input,
            DocumentAnalysisStageRecord(
                recordId = "phase-12-$name-${outputHash.take(24)}",
                stage = stage,
                state = DocumentAnalysisStageState.Succeeded,
                scopeId = input.taskId.value,
                startedAt = started,
                finishedAt = finished,
                durationMillis = java.time.Duration.between(started, finished).toMillis().coerceAtLeast(0),
                selectedModelId = modelId,
                promptVersion = modelId?.let { DocumentAnalysisPipelineVersions.GROUNDED_PROMPT },
                requestSchemaVersion = modelId?.let { DocumentAnalysisPipelineVersions.GROUNDED_REQUEST },
                responseSchemaVersion = modelId?.let { DocumentAnalysisPipelineVersions.GROUNDED_RESPONSE },
                inputSha256 = inputHash,
                outputSha256 = outputHash,
                providerAttemptCount = attempts,
                completedCount = logicalCalls,
                totalCount = logicalCalls,
            ),
        )
    }

    private fun groundedDiscoveries(
        candidates: List<com.entio.core.DocumentGroundedCandidate>,
        extracted: List<ExtractedDocument>,
    ): List<DocumentDiscovery> {
        val blocks = extracted.flatMap { it.blocks }.associateBy { it.id }
        return candidates.flatMap { candidate ->
            val kind = when (candidate.category) {
                com.entio.core.DocumentCandidateExtractionCategory.Person,
                com.entio.core.DocumentCandidateExtractionCategory.Organization,
                -> DocumentDiscoveryKind.Individual
                com.entio.core.DocumentCandidateExtractionCategory.RelationshipPhrase -> DocumentDiscoveryKind.Relationship
                com.entio.core.DocumentCandidateExtractionCategory.AttributeValue -> DocumentDiscoveryKind.Attribute
                com.entio.core.DocumentCandidateExtractionCategory.MonetaryAmount,
                com.entio.core.DocumentCandidateExtractionCategory.Date,
                com.entio.core.DocumentCandidateExtractionCategory.Identifier,
                -> DocumentDiscoveryKind.Value
                com.entio.core.DocumentCandidateExtractionCategory.RuleCue -> DocumentDiscoveryKind.ConditionalRule
                com.entio.core.DocumentCandidateExtractionCategory.Administrative -> DocumentDiscoveryKind.Metadata
                else -> DocumentDiscoveryKind.Concept
            }
            candidate.evidenceSpans.groupBy { it.documentId }
                .toSortedMap(compareBy<com.entio.core.DocumentId> { it.value })
                .entries
                .mapIndexed { index, entry ->
                val (documentId, spans) = entry
                val evidence = spans.map { span ->
                    val block = blocks.getValue(span.blockId)
                    DocumentEvidence(
                        span.evidenceId,
                        DocumentEvidenceType.Explicit,
                        listOf(
                            DocumentEvidenceReference(
                                span.referenceId,
                                span.documentId,
                                span.blockId,
                                span.pageNumber,
                                span.section,
                                span.startOffsetInBlock,
                                span.endOffsetInBlock,
                                span.exactText,
                                block.extractionMethod,
                                block.ocrConfidence,
                            ),
                        ),
                    )
                }.sortedBy { it.id.value }
                DocumentDiscovery(
                    if (index == 0) candidate.id else "${candidate.id}-evidence-${hash(documentId.value).take(12)}",
                    documentId,
                    kind,
                    DocumentContentClassification.BusinessContent,
                    DocumentAssertionClassification.ExplicitFact,
                    candidate.displayText,
                    evidence,
                    evidenceConfidence = 100,
                    individualClassification = if (kind == DocumentDiscoveryKind.Individual) {
                        DocumentIndividualClassification.Unknown
                    } else {
                        null
                    },
                )
            }
        }.sortedBy(DocumentDiscovery::stableOrderingKey)
    }

    private fun ontologyLabels(project: com.entio.core.EntioProject): Set<String> = descriptions.describeAll(project)
        .flatMap { descriptor ->
            val common = descriptor.common
            buildList {
                common.preferredLabel?.lexicalForm?.let(::add)
                addAll(common.alternateLabels.map { it.lexicalForm })
                add(common.entity.value.substringAfterLast('#').substringAfterLast('/'))
            }
        }
        .filter(String::isNotBlank)
        .toSet()

    private fun announce(
        input: DocumentIngestionProcessingInput,
        percent: Int,
        message: String,
        details: List<String> = emptyList(),
    ): Unit {
        checkCancellation(input)
        tasks.transition(
            input.taskId,
            input.projectId,
            input.ownerUserId,
            DocumentProcessingStatus.Analyzing,
            input.documents.size,
            percent,
            message,
            details,
        )
    }

    private fun recordStage(
        input: DocumentIngestionProcessingInput,
        record: DocumentAnalysisStageRecord,
    ): Unit {
        checkCancellation(input)
        tasks.recordAnalysisStage(input.taskId, input.projectId, input.ownerUserId, record)
    }

    private fun alignmentSnapshot(
        projectId: String,
        project: EntioProject,
        ontologyFingerprint: String,
        currentWorkFingerprint: String,
        writableSourceIds: List<String>,
    ): DocumentOntologyAlignmentSnapshot {
        val records = (semanticRecords(project) + provenanceRecords(projectId))
            .sortedWith(compareBy({ it.scope.name }, { it.sourceId }, { it.entityIri.value }))
            .take(MAX_ALIGNMENT_ENTRIES)
        return DocumentOntologyAlignmentSnapshot(
            projectId = projectId,
            ontologyFingerprint = ontologyFingerprint,
            currentWorkFingerprint = currentWorkFingerprint,
            entries = records.map { record ->
                DocumentOntologyAlignmentContextEntry(
                    referenceId = "context-${hash(record).take(24)}",
                    projectId = projectId,
                    scope = record.scope.name,
                    entityIri = record.entityIri.value,
                    sourceId = record.sourceId,
                    preferredLabel = record.preferredLabel,
                    aliases = record.aliases,
                    category = record.category?.name,
                    writable = record.sourceId in writableSourceIds,
                )
            }.sortedBy(DocumentOntologyAlignmentContextEntry::referenceId),
            writableSourceIds = writableSourceIds,
        )
    }

    private fun existingEntityKinds(project: EntioProject): Map<Iri, DocumentTemporaryReferenceKind> =
        descriptions.describeAll(project).mapNotNull { descriptor ->
            val iri = descriptor.common.entity as? Iri ?: return@mapNotNull null
            val kind = when (descriptor.common.kind) {
                SemanticDescriptorKind.Class -> DocumentTemporaryReferenceKind.Class
                SemanticDescriptorKind.ObjectProperty -> DocumentTemporaryReferenceKind.ObjectProperty
                SemanticDescriptorKind.DatatypeProperty -> DocumentTemporaryReferenceKind.DatatypeProperty
                SemanticDescriptorKind.AnnotationProperty -> DocumentTemporaryReferenceKind.AnnotationProperty
                SemanticDescriptorKind.Individual -> DocumentTemporaryReferenceKind.Individual
            }
            iri to kind
        }.toMap()

    private fun workKey(
        input: DocumentIngestionProcessingInput,
        ontologyFingerprint: String,
    ): DocumentAnalysisWorkKey = DocumentAnalysisWorkKey(
        hash(
            listOf(
                input.projectId,
                ontologyFingerprint,
                authorityKey(input),
                DocumentAnalysisPipelineVersions.DISCOVERY_PROMPT,
                DocumentAnalysisPipelineVersions.CONNECTED_MODEL_PROMPT,
                DocumentAnalysisPipelineVersions.RECONCILIATION_PROMPT,
                DocumentAnalysisPipelineVersions.ONTOLOGY_ALIGNMENT_PROMPT,
                DocumentAnalysisPipelineVersions.MODELING_CRITIC_PROMPT,
                DocumentAnalysisPipelineVersions.SEMANTIC_PLAN_PROMPT,
            ) + input.documents.sortedBy { it.document.id.value }.map { it.document.checksumSha256 },
        ),
    )

    private fun hash(value: Any): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toString().toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun isCancelled(taskId: String): Boolean {
        val input = synchronized(processingInputs) { processingInputs[taskId] } ?: return true
        return tasks.isCancelled(input.taskId, input.projectId, input.ownerUserId)
    }

    private fun semanticRecords(project: EntioProject): List<DocumentSemanticRecord> =
        descriptions.describeAll(project).mapNotNull { descriptor ->
            val common = descriptor.common
            val iri = common.entity as? Iri ?: return@mapNotNull null
            DocumentSemanticRecord(
                scope = if (common.locality == LocalityStatus.Imported) {
                    DocumentMatchScope.Imported
                } else {
                    DocumentMatchScope.AppliedLocal
                },
                entityIri = iri,
                sourceId = common.sourceId,
                preferredLabel = common.preferredLabel?.lexicalForm,
                aliases = common.alternateLabels.map { it.lexicalForm }.distinct().sorted(),
                category = descriptor.category(),
                normalizedIdentityKey = common.preferredLabel?.lexicalForm?.normalizeIdentity(),
                normalizedTypedOperationKey = null,
            )
        }

    private fun ontologyContext(
        project: EntioProject,
        documents: List<ExtractedDocument>,
    ): List<DocumentOntologyContextEntity> {
        var remainingCharacters = MAX_ONTOLOGY_CONTEXT_CHARACTERS
        val entities = descriptions.describeAll(project)
            .mapNotNull { descriptor ->
                val common = descriptor.common
                val iri = common.entity as? Iri ?: return@mapNotNull null
                val directSuperclasses = when (descriptor) {
                    is OntologyEntityDescriptor.Class -> descriptor.directSuperclasses.map(Iri::value)
                    else -> emptyList()
                }
                val domains = when (descriptor) {
                    is OntologyEntityDescriptor.ObjectProperty -> descriptor.domains.map(Iri::value)
                    is OntologyEntityDescriptor.DatatypeProperty -> descriptor.domains.map(Iri::value)
                    else -> emptyList()
                }
                val ranges = when (descriptor) {
                    is OntologyEntityDescriptor.ObjectProperty -> descriptor.ranges.map(Iri::value)
                    is OntologyEntityDescriptor.DatatypeProperty -> descriptor.datatypeRanges.map(Iri::value)
                    else -> emptyList()
                }
                DocumentOntologyContextEntity(
                    iri = iri.value,
                    kind = common.kind.name,
                    sourceId = common.sourceId,
                    preferredLabel = common.preferredLabel?.lexicalForm,
                    definitions = common.definitions
                        .map { it.lexicalForm.take(MAX_ONTOLOGY_CONTEXT_TEXT) }
                        .distinct()
                        .sorted()
                        .take(3),
                    directSuperclasses = directSuperclasses.distinct().sorted().take(20),
                    domains = domains.distinct().sorted().take(20),
                    ranges = ranges.distinct().sorted().take(20),
                )
            }
        val documentTokens = documents.asSequence()
            .flatMap { it.blocks.asSequence() }
            .flatMap { ontologyContextTokens(it.exactText).asSequence() }
            .take(MAX_ONTOLOGY_CONTEXT_TOKENS)
            .toSet()
        fun directScore(entity: DocumentOntologyContextEntity): Int {
            val labelScore = ontologyContextTokens(entity.preferredLabel.orEmpty()).count(documentTokens::contains) * 10
            val definitionScore = entity.definitions
                .flatMap(::ontologyContextTokens)
                .distinct()
                .count(documentTokens::contains)
            return labelScore + definitionScore
        }
        val directScores = entities.associate { it.iri to directScore(it) }
        val directlyRelevantIris = directScores.filterValues { it > 0 }.keys
        return entities.sortedWith(
                compareByDescending<DocumentOntologyContextEntity> { entity ->
                    directScores.getValue(entity.iri) +
                        if ((entity.domains + entity.ranges + entity.directSuperclasses).any(directlyRelevantIris::contains)) {
                            5
                        } else {
                            0
                        }
                }.thenBy(
                    DocumentOntologyContextEntity::kind,
                ).thenBy(
                    DocumentOntologyContextEntity::preferredLabel,
                ).thenBy(
                    DocumentOntologyContextEntity::iri,
                ),
            )
            .mapNotNull { entity ->
                val size = entity.iri.length +
                    entity.kind.length +
                    entity.sourceId.length +
                    entity.preferredLabel.orEmpty().length +
                    entity.definitions.sumOf(String::length) +
                    entity.directSuperclasses.sumOf(String::length) +
                    entity.domains.sumOf(String::length) +
                    entity.ranges.sumOf(String::length)
                if (size > remainingCharacters) {
                    null
                } else {
                    remainingCharacters -= size
                    entity
                }
            }
            .take(MAX_ONTOLOGY_CONTEXT_ENTITIES)
    }

    private fun ontologyContextTokens(value: String): List<String> =
        value.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 && it !in ONTOLOGY_CONTEXT_STOP_WORDS }

    private fun provenanceRecords(projectId: String): List<DocumentSemanticRecord> =
        provenance.list(projectId).mapNotNull { record ->
            val typed = record.typedOperation ?: return@mapNotNull null
            val entity = typed.targetEntityIri ?: return@mapNotNull null
            DocumentSemanticRecord(
                scope = DocumentMatchScope.DurableProvenance,
                entityIri = entity,
                sourceId = record.recordId,
                preferredLabel = null,
                category = null,
                normalizedIdentityKey = null,
                normalizedTypedOperationKey = typed.normalizedTypedOperationKey,
            )
        }

    private fun OntologyEntityDescriptor.category(): DocumentCandidateCategory? = when (common.kind) {
        SemanticDescriptorKind.Class -> DocumentCandidateCategory.Class
        SemanticDescriptorKind.ObjectProperty -> DocumentCandidateCategory.ObjectProperty
        SemanticDescriptorKind.DatatypeProperty -> DocumentCandidateCategory.DatatypeProperty
        SemanticDescriptorKind.AnnotationProperty -> DocumentCandidateCategory.AnnotationValue
        SemanticDescriptorKind.Individual -> DocumentCandidateCategory.Individual
    }

    private fun loadProject(projectId: String): EntioProject =
        when (val result = projectLoader.loadProject(projectRegistry.rootFor(projectId))) {
            is EntioResult.Success -> result.value
            is EntioResult.Failure -> throw DocumentIngestionFailure(
                "document-project-load-failed",
                "The project could not be loaded for document analysis.",
            )
        }

    private fun authorityKey(input: DocumentIngestionProcessingInput): String {
        val digest = MessageDigest.getInstance("SHA-256")
        input.documents.sortedBy { it.document.id.value }.forEach { upload ->
            val value = "${upload.document.checksumSha256}|${upload.document.authority}"
            digest.update(value.toByteArray(StandardCharsets.UTF_8))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun String.normalizeIdentity(): String =
        trim().lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun checkCancellation(input: DocumentIngestionProcessingInput): Unit {
        if (tasks.isCancelled(input.taskId, input.projectId, input.ownerUserId)) {
            throw DocumentIngestionFailure("ingestion-cancelled", "Document ingestion was cancelled.")
        }
    }

    private fun handleFailure(taskId: String, projectId: String, userId: String, failure: Exception): Unit {
        if (failure is CancellationException) return
        val id = com.entio.core.DocumentTaskId(taskId)
        if (tasks.isCancelled(id, projectId, userId)) return
        if (failure is DocumentAnalysisFailure &&
            failure.code in setOf(
                "document-model-not-configured",
                "document-model-not-ready",
                "document-credential-missing",
            )
        ) {
            tasks.transition(
                id,
                projectId,
                userId,
                DocumentProcessingStatus.BlockedForModel,
                0,
                40,
                "Document analysis is blocked until the selected model and credential are ready.",
            )
        } else if (failure is DocumentAnalysisFailure) {
            tasks.fail(
                id,
                projectId,
                userId,
                safeAnalysisFailureMessage(failure.code),
                safeAnalysisFailureDetails(failure),
            )
        } else {
            if (System.getenv("ENTIO_DOCUMENT_ANALYSIS_DEBUG") == "true") {
                System.err.println(
                    "entio-document-analysis unexpected-pipeline-failure=${failure::class.simpleName} " +
                        "message=${failure.message.orEmpty()}",
                )
            }
            tasks.fail(id, projectId, userId, "Document processing failed safely.")
        }
    }

    private fun safeAnalysisFailureDetails(failure: DocumentAnalysisFailure): List<String> = buildList {
        add("Stage: ${safeAnalysisFailureStage(failure.code)}.")
        add("Error code: ${failure.code}.")
        addAll(failure.details)
        if (failure.details.isEmpty() && !failure.message.isNullOrBlank()) {
            add("Cause: ${failure.message}.")
        }
    }

    private fun safeAnalysisFailureStage(code: String): String = when {
        code.startsWith("document-discovery-") -> "document discovery"
        code.startsWith("document-connected-model-") || code.startsWith("document-model-consolidation-") ->
            "connected semantic synthesis"
        code.startsWith("document-final-plan-") -> "ontology-aware recommendation planning"
        code.startsWith("document-provider-") -> "model provider request"
        code.startsWith("evidence-") -> "evidence verification"
        else -> "deterministic document analysis"
    }

    private fun safeAnalysisFailureMessage(code: String): String = when (code) {
        "document-provider-authorization" ->
            "Model analysis authorization failed. Recheck the provider credential."
        "document-provider-rate-limited" ->
            "Model analysis was rate limited after bounded retries."
        "document-provider-request-rate-limit" ->
            "The selected model's token-rate allowance is too small for this bounded analysis request " +
                "(document-provider-request-rate-limit)."
        "document-provider-quota-exhausted" ->
            "The provider account has no available model quota (document-provider-quota-exhausted)."
        "document-provider-unavailable" ->
            "The model provider was unavailable after bounded retries."
        "document-provider-timeout" ->
            "Model analysis timed out after bounded retries."
        "document-provider-request-rejected" ->
            "The model provider rejected Entio's document-analysis request."
        "document-provider-request-schema-invalid" ->
            "The model provider rejected Entio's structured response schema " +
                "(document-provider-request-schema-invalid)."
        "document-provider-model-not-found" ->
            "The selected model is no longer available from the provider " +
                "(document-provider-model-not-found)."
        "document-provider-response-limit" ->
            "The model response exceeded Entio's safe analysis limit."
        "document-provider-incomplete-output" ->
            "The model stopped before completing Entio's structured analysis (document-provider-incomplete-output)."
        "document-provider-output-token-limit" ->
            "The model reached its output limit before completing structured analysis (document-provider-output-token-limit)."
        "document-provider-content-filter" ->
            "The model stopped because its content filter interrupted the analysis (document-provider-content-filter)."
        "document-provider-refusal" ->
            "The model declined the document-analysis request (document-provider-refusal)."
        "document-provider-empty-output" ->
            "The model returned no structured document analysis (document-provider-empty-output)."
        "document-provider-malformed-output" ->
            "The model output could not be parsed as structured analysis (document-provider-malformed-output)."
        "document-provider-schema-invalid" ->
            "The model analysis did not match Entio's supported field contract (document-provider-schema-invalid)."
        "document-evidence-type-invalid" ->
            "The model used a document evidence type that Entio does not permit (document-evidence-type-invalid)."
        "document-interpretation-invalid" ->
            "The model used an unsupported interpretation label (document-interpretation-invalid)."
        "evidence-count-invalid" ->
            "The model returned an unsupported number of evidence references (evidence-count-invalid)."
        "evidence-block-not-found" ->
            "The model referenced an extracted text block that does not exist (evidence-block-not-found)."
        "evidence-cross-document" ->
            "The model attached evidence to the wrong document (evidence-cross-document)."
        "evidence-offset-invalid" ->
            "The model returned evidence positions outside the extracted text (evidence-offset-invalid)."
        "evidence-excerpt-mismatch" ->
            "The model's evidence quotation did not match the extracted text (evidence-excerpt-mismatch)."
        "evidence-duplicate" ->
            "The model returned duplicate evidence references (evidence-duplicate)."
        "document-provider-call-limit" ->
            "Document analysis reached Entio's provider-call limit."
        "document-analysis-input-empty" ->
            "No extracted document text was available for model analysis."
        "document-final-plan-evidence-invalid" ->
            "The final recommendation cited evidence outside Entio's verified discovery inventory " +
                "(document-final-plan-evidence-invalid)."
        "document-final-plan-evidence-stale" ->
            "The final recommendation evidence no longer matches the extracted document text " +
                "(document-final-plan-evidence-stale)."
        "document-review-work-key-mismatch",
        "document-review-graph-fingerprint-missing",
        ->
            "The verified recommendation plan did not match the current review workspace ($code)."
        else ->
            "Document analysis stopped because deterministic pipeline validation rejected a result ($code)."
    }

    private companion object {
        const val MAX_REPORTED_CONNECTED_MODEL_SKIPS: Int = 4
        const val MAX_REPORTED_CONNECTED_MODEL_ITEMS: Int = 4
        const val MAX_ONTOLOGY_CONTEXT_ENTITIES: Int = 200
        const val MAX_ONTOLOGY_CONTEXT_CHARACTERS: Int = 40_000
        const val MAX_ONTOLOGY_CONTEXT_TEXT: Int = 500
        const val MAX_ONTOLOGY_CONTEXT_TOKENS: Int = 5_000
        const val MAX_ALIGNMENT_ENTRIES: Int = 20_000
        const val REQUIRED_POST_DISCOVERY_LOGICAL_CALLS: Int = 1
        val ONTOLOGY_CONTEXT_STOP_WORDS: Set<String> = setOf(
            "and",
            "are",
            "for",
            "from",
            "has",
            "that",
            "the",
            "this",
            "with",
        )
    }
}

/**
 * Applies one task-wide provider-attempt budget across all pipeline services.
 * A repeated request hash is the only request eligible for a bounded retry.
 */
private class BudgetedDocumentPipelineProvider(
    private val delegate: DocumentPipelineProvider,
) : DocumentPipelineProvider, AutoCloseable {
    private val attemptsByTask: MutableMap<String, Int> = linkedMapOf()
    private val retriesByTask: MutableMap<String, Int> = linkedMapOf()
    private val requestCountsByTask: MutableMap<String, MutableMap<String, Int>> = linkedMapOf()

    override suspend fun analyze(
        apiKey: String,
        selectedModelId: String,
        systemInstruction: String,
        request: DocumentAnalysisRequest,
    ): DocumentAnalysisProviderResult =
        throw DocumentAnalysisFailure(
            "document-legacy-analysis-disabled",
            "The legacy single-stage document analysis path is disabled.",
        )

    override suspend fun discover(
        apiKey: String,
        selectedModelId: String,
        systemInstruction: String,
        request: DocumentDiscoveryRequest,
    ): DocumentDiscoveryProviderResult {
        reserve(request.taskId, "discovery", request)
        return delegate.discover(apiKey, selectedModelId, systemInstruction, request)
    }

    override suspend fun model(
        apiKey: String,
        selectedModelId: String,
        systemInstruction: String,
        request: DocumentConnectedModelRequest,
    ): DocumentConnectedModelProviderResult {
        reserve(request.taskId, "model", request)
        return delegate.model(apiKey, selectedModelId, systemInstruction, request)
    }

    override suspend fun consolidate(
        apiKey: String,
        selectedModelId: String,
        systemInstruction: String,
        request: DocumentModelConsolidationRequest,
    ): DocumentConnectedModelProviderResult {
        reserve(request.taskId, "consolidate", request)
        return delegate.consolidate(apiKey, selectedModelId, systemInstruction, request)
    }

    override suspend fun completePrerequisites(
        apiKey: String,
        selectedModelId: String,
        systemInstruction: String,
        request: DocumentPrerequisiteCompletionRequest,
    ): DocumentConnectedModelProviderResult {
        reserve(request.taskId, "prerequisite", request)
        return delegate.completePrerequisites(apiKey, selectedModelId, systemInstruction, request)
    }

    override suspend fun reconcile(
        apiKey: String,
        selectedModelId: String,
        systemInstruction: String,
        request: DocumentReconciliationRequest,
    ): DocumentReconciliationProviderResult {
        reserve(request.taskId, "reconcile", request)
        return delegate.reconcile(apiKey, selectedModelId, systemInstruction, request)
    }

    override suspend fun align(
        apiKey: String,
        selectedModelId: String,
        systemInstruction: String,
        request: DocumentOntologyAlignmentRequest,
    ): DocumentOntologyAlignmentProviderResult {
        reserve(request.taskId, "align", request)
        return delegate.align(apiKey, selectedModelId, systemInstruction, request)
    }

    override suspend fun critique(
        apiKey: String,
        selectedModelId: String,
        systemInstruction: String,
        request: DocumentModelingCriticRequest,
    ): DocumentModelingCriticProviderResult {
        reserve(request.taskId, "critic", request)
        return delegate.critique(apiKey, selectedModelId, systemInstruction, request)
    }

    override suspend fun plan(
        apiKey: String,
        selectedModelId: String,
        systemInstruction: String,
        request: DocumentFinalPlanningRequest,
    ): DocumentFinalPlanningProviderResult {
        reserve(request.taskId, "final", request)
        return delegate.plan(apiKey, selectedModelId, systemInstruction, request)
    }

    override suspend fun planSemantic(
        apiKey: String,
        selectedModelId: String,
        systemInstruction: String,
        request: DocumentFinalPlanningRequest,
    ): DocumentSemanticPlanningProviderResult {
        reserve(request.taskId, "semantic-final", request.toPromptPayload())
        return delegate.planSemantic(apiKey, selectedModelId, systemInstruction, request)
    }

    @Synchronized
    private fun reserve(
        taskId: String,
        stage: String,
        request: Any,
    ): Unit {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$stage|$request".toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val requests = requestCountsByTask.getOrPut(taskId) { linkedMapOf() }
        val prior = requests[digest] ?: 0
        if (prior >= com.entio.core.MAX_DOCUMENT_AUTOMATIC_RETRY_ATTEMPTS + 1) {
            throw DocumentAnalysisFailure(
                "document-provider-retry-limit",
                "A logical model call exhausted the bounded exact-input retry reserve.",
            )
        }
        if (prior == 0 && requests.size >= com.entio.core.MAX_DOCUMENT_PLANNED_LOGICAL_CALLS) {
            throw DocumentAnalysisFailure(
                "document-provider-call-limit",
                "The planned logical-call limit was reached.",
            )
        }
        if (prior > 0) {
            val retries = (retriesByTask[taskId] ?: 0) + 1
            if (retries > com.entio.core.MAX_DOCUMENT_AUTOMATIC_RETRY_ATTEMPTS) {
                throw DocumentAnalysisFailure(
                    "document-provider-retry-limit",
                    "The task retry reserve was exhausted.",
                )
            }
            retriesByTask[taskId] = retries
        }
        val attempts = (attemptsByTask[taskId] ?: 0) + 1
        if (attempts > com.entio.core.MAX_DOCUMENT_PROVIDER_ATTEMPTS) {
            throw DocumentAnalysisFailure(
                "document-provider-attempt-limit",
                "The provider attempt limit was reached.",
            )
        }
        attemptsByTask[taskId] = attempts
        requests[digest] = prior + 1
    }

    override fun close(): Unit {
        (delegate as? AutoCloseable)?.close()
    }
}
