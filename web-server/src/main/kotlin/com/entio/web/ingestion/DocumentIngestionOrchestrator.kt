package com.entio.web.ingestion

import com.entio.core.DocumentCandidateCategory
import com.entio.core.DocumentMatchScope
import com.entio.core.DocumentProcessingStatus
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.Iri
import com.entio.core.LocalityStatus
import com.entio.core.OntologyEntityDescriptor
import com.entio.core.SemanticDescriptorKind
import com.entio.core.ShaclGraphRole
import com.entio.core.SymbolKind
import com.entio.semantic.DeterministicIriGenerator
import com.entio.semantic.DocumentDraftTranslationContext
import com.entio.semantic.DocumentMatchingInput
import com.entio.semantic.DocumentOntologyMatcher
import com.entio.semantic.DocumentSemanticRecord
import com.entio.semantic.ProjectLoader
import com.entio.semantic.SemanticDescriptionService
import com.entio.web.ai.AiCredentialStore
import com.entio.web.ai.models.AiUserProviderSettingsStore
import com.entio.web.contract.ProjectRegistry
import com.entio.web.webGraphFingerprint
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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
    credentials: AiCredentialStore,
    settings: AiUserProviderSettingsStore,
    private val provider: DocumentAnalysisProvider,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val descriptions: SemanticDescriptionService = SemanticDescriptionService(),
    private val matcher: DocumentOntologyMatcher = DocumentOntologyMatcher(),
    private val iriGenerator: DeterministicIriGenerator = DeterministicIriGenerator(),
) : AutoCloseable {
    private val jobs: MutableMap<String, Job> = linkedMapOf()
    private val analysis = DocumentAnalysisService(
        credentials,
        settings,
        provider,
        clock = configuration.clock,
        isCancelled = { taskId ->
            val input = synchronized(processingInputs) { processingInputs[taskId] }
            input == null || tasks.isCancelled(input.taskId, input.projectId, input.ownerUserId)
        },
    )
    private val processingInputs: MutableMap<String, DocumentIngestionProcessingInput> = linkedMapOf()

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

    override fun close(): Unit {
        scope.cancel()
        (provider as? AutoCloseable)?.close()
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
        val project = loadProject(input.projectId)
        val graphFingerprint = webGraphFingerprint(project.graph)
        tasks.transition(
            input.taskId,
            input.projectId,
            input.ownerUserId,
            DocumentProcessingStatus.Analyzing,
            input.documents.size,
            40,
            "Analyzing verified document text with the selected model.",
        )
        checkCancellation(input)
        val completed = analysis.analyze(
            input.ownerUserId,
            DocumentAnalysisWork(
                input.taskId.value,
                graphFingerprint,
                extracted,
                authorityKey(input),
            ),
        )
        tasks.transition(
            input.taskId,
            input.projectId,
            input.ownerUserId,
            DocumentProcessingStatus.Matching,
            input.documents.size,
            75,
            "Matching verified candidates against current ontology records.",
        )
        checkCancellation(input)
        val semanticRecords = semanticRecords(project) + provenanceRecords(input.projectId)
        val targetSourceId = project.resolvedSources
            .firstOrNull { ShaclGraphRole.Ontology in it.roles }
            ?.id
        val matched = matcher.match(
            DocumentMatchingInput(
                exactWorkKey = completed.exactWorkKey,
                candidates = completed.candidates,
                records = semanticRecords,
                authorityByDocumentId = input.documents.associate {
                    it.document.id.value to it.document.authority
                },
                targetSourceId = targetSourceId,
                modelId = completed.modelId,
                promptVersion = completed.promptVersion,
            ),
        )
        tasks.transition(
            input.taskId,
            input.projectId,
            input.ownerUserId,
            DocumentProcessingStatus.PreparingRecommendations,
            input.documents.size,
            90,
            "Preparing evidence-linked recommendations for review.",
        )
        checkCancellation(input)
        val reviewTask = tasks.find(input.taskId, input.projectId, input.ownerUserId).let { task ->
            task.copy(
                status = "awaiting-review",
                documents = task.documents.map { it.copy(status = "awaiting-review") },
                progress = task.progress.copy(
                    stage = "awaiting-review",
                    percent = 100,
                    message = "Evidence-linked recommendations are ready for review.",
                ),
            )
        }
        reviews.install(
            DocumentReviewWorkspaceInput(
                task = reviewTask,
                exactWorkKey = completed.exactWorkKey,
                graphFingerprint = graphFingerprint,
                extractedDocuments = extracted,
                summaries = completed.summaries,
                recommendations = matched.recommendations,
                priorWorkflowProvenance = matched.recommendations.associate { recommendation ->
                    recommendation.id to recommendation.matches
                        .filter { it.scope == DocumentMatchScope.DurableProvenance }
                        .map { it.sourceId }
                        .distinct()
                        .sorted()
                },
                draftContexts = matched.recommendations.associate { recommendation ->
                    recommendation.id to draftContext(recommendation, project, targetSourceId, graphFingerprint)
                },
            ),
        )
        tasks.transition(
            input.taskId,
            input.projectId,
            input.ownerUserId,
            DocumentProcessingStatus.AwaitingReview,
            input.documents.size,
            100,
            "Evidence-linked recommendations are ready for review.",
        )
    }

    private fun draftContext(
        recommendation: com.entio.core.DocumentRecommendation,
        project: EntioProject,
        targetSourceId: String?,
        graphFingerprint: String,
    ): DocumentDraftTranslationContext {
        val selected = recommendation.selectedMatch?.entityIri
        val generated = if (selected == null && targetSourceId != null) {
            val kind = when (recommendation.type) {
                DocumentCandidateCategory.Class -> SymbolKind.Class
                DocumentCandidateCategory.ObjectProperty,
                DocumentCandidateCategory.DatatypeProperty,
                -> SymbolKind.Property
                DocumentCandidateCategory.Individual -> SymbolKind.Individual
                else -> null
            }
            kind?.let {
                when (val result = iriGenerator.generate(
                    recommendation.proposedLabel.orEmpty(),
                    it,
                    project.config.iriNamespace,
                    project.symbols,
                )) {
                    is EntioResult.Success -> result.value.iri
                    is EntioResult.Failure -> null
                }
            }
        } else {
            null
        }
        return DocumentDraftTranslationContext(
            targetSourceId = recommendation.targetSourceId ?: targetSourceId,
            targetIri = selected ?: generated,
            graphCurrent = webGraphFingerprint(project.graph) == graphFingerprint,
            evidenceCurrent = true,
            modelAndPromptCurrent = true,
            duplicateOperation = recommendation.action == com.entio.core.DocumentRecommendationAction.ReuseLocal,
        )
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
        } else {
            tasks.fail(id, projectId, userId, "Document processing failed safely.")
        }
    }
}
