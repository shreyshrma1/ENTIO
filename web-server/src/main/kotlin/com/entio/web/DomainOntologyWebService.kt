package com.entio.web

import com.entio.core.DomainModelingIntent
import com.entio.core.DomainOntologyMigrationStatus
import com.entio.core.DomainOntologyProfile
import com.entio.core.DomainOntologyProfileIdentity
import com.entio.core.DomainOperationKind
import com.entio.core.DomainProfileDeactivationContext
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.ExternalEntityKind
import com.entio.core.Iri
import com.entio.semantic.DomainFileTransactionManager
import com.entio.semantic.DomainProfileRepository
import com.entio.semantic.DomainProfileService
import com.entio.semantic.DomainRecommendationFingerprints
import com.entio.semantic.DomainRecommendationService
import com.entio.semantic.DomainRecommendationStaleException
import com.entio.semantic.DomainReusePreparationRequest
import com.entio.semantic.DomainReuseService
import com.entio.semantic.DomainSearchIndexVerifier
import com.entio.semantic.FiboPackageVerifier
import com.entio.semantic.ProjectLoader
import com.entio.web.contract.DomainWebAssetPaths
import com.entio.web.contract.ProjectRegistry
import com.entio.web.contract.WebDomainActivationPreviewResponse
import com.entio.web.contract.WebDomainDeactivationPreviewResponse
import com.entio.web.contract.WebDomainDependencyPreviewResponse
import com.entio.web.contract.WebDomainFoundationPlanRequest
import com.entio.web.contract.WebDomainFoundationPlanResponse
import com.entio.web.contract.WebDomainFoundationResponse
import com.entio.web.contract.WebDomainMigrationResponse
import com.entio.web.contract.WebDomainOntologyDescriptor
import com.entio.web.contract.WebDomainOntologyListResponse
import com.entio.web.contract.WebDomainOntologyStatusResponse
import com.entio.web.contract.WebDomainProfileActionResponse
import com.entio.web.contract.WebDomainRecommendationDetailResponse
import com.entio.web.contract.WebDomainRecommendationRequest
import com.entio.web.contract.WebDomainRecommendationResponse
import com.entio.web.contract.WebDomainReuseDetailResponse
import com.entio.web.contract.WebDomainReuseStageRequest
import com.entio.web.contract.WebStagingResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

public class DomainOntologyWebFailure(
    public val code: String,
    message: String,
) : IllegalArgumentException(message)

/** Authorized web adapter for the Kotlin-owned Phase 13 profile and recommendation services. */
public class DomainOntologyWebService(
    private val projectRegistry: ProjectRegistry,
    private val staging: StagingWorkflowService,
    private val loadedProjects: LoadedProjectCache,
    private val assets: DomainWebAssetPaths,
    private val clock: Clock = Clock.systemUTC(),
    assetVerifier: (() -> Unit)? = null,
    private val repository: DomainProfileRepository = DomainProfileRepository(),
    private val transactions: DomainFileTransactionManager = DomainFileTransactionManager(repository),
    private val profiles: DomainProfileService = DomainProfileService(repository, transactions),
    private val projectLoader: ProjectLoader = ProjectLoader(recoverDomainTransactions = false),
    private val recommendationTimeoutMillis: Long = RECOMMENDATION_TIMEOUT_MILLIS,
    recommendationFactory: () -> DomainRecommendationService = {
        DomainRecommendationService.open(assets.searchRoot, assets.modelRoot, clock)
    },
    reuseFactory: () -> DomainReuseService = { DomainReuseService.open(assets.searchRoot) },
) : AutoCloseable {
    private val verifyAssets: () -> Unit = assetVerifier ?: {
        FiboPackageVerifier.verify(assets.fiboPackageRoot)
        DomainSearchIndexVerifier.verify(assets.searchRoot, assets.modelRoot, regenerate = false)
    }
    private val recommendationDelegate = lazy(recommendationFactory)
    private val recommendations: DomainRecommendationService get() = recommendationDelegate.value
    private val reuseDelegate = lazy(reuseFactory)
    private val reuse: DomainReuseService get() = reuseDelegate.value
    private val recommendationPermits = Semaphore(MAX_CONCURRENT_RECOMMENDATIONS)
    private val tokens = linkedMapOf<String, ConfirmationToken>()
    private val idempotentResponses = linkedMapOf<String, IdempotentResponse>()

    public fun list(): WebDomainOntologyListResponse = WebDomainOntologyListResponse(
        domainOntologies = listOf(
            WebDomainOntologyDescriptor(
                sourceId = DomainOntologyProfileIdentity.SOURCE_ID,
                displayName = "Financial Industry Business Ontology (FIBO)",
                release = DomainOntologyProfileIdentity.RELEASE,
                packageFingerprint = DomainOntologyProfileIdentity.PACKAGE_FINGERPRINT,
                retrievalAvailability = recommendations.retrievalAvailability(),
                selectable = recommendations.retrievalAvailability() != com.entio.core.DomainRetrievalAvailability.Unavailable,
            ),
        ),
    )

    public fun status(projectId: String): WebDomainOntologyStatusResponse {
        val root = root(projectId)
        return WebDomainOntologyStatusResponse(projectId = projectId, status = profiles.status(root))
    }

    @Synchronized
    public fun previewActivation(projectId: String, userId: String): WebDomainActivationPreviewResponse {
        val root = root(projectId)
        preflight()
        val preview = profiles.previewActivation(root).valueOrFailure()
        val token = issueToken(TokenKind.Activation, projectId, userId, configurationFingerprint(root))
        return WebDomainActivationPreviewResponse(projectId = projectId, activationToken = token, preview = preview)
    }

    @Synchronized
    public fun activate(
        projectId: String,
        userId: String,
        confirmationToken: String,
        idempotencyKey: String,
    ): WebDomainProfileActionResponse = idempotent(
        scope = "activate:$projectId:$userId:$idempotencyKey",
        payload = confirmationToken,
    ) {
        val root = root(projectId)
        consumeToken(confirmationToken, TokenKind.Activation, projectId, userId, configurationFingerprint(root))
        preflight()
        val prepared = profiles.prepareActivation(root).valueOrFailure()
        transactions.commit(prepared) {
            loadedProjects.invalidate(root)
            val project = projectLoader.loadProject(root).valueOrFailure()
            if (project.activeDomainOntology == null) {
                throw DomainOntologyWebFailure("domain-activation-verification-failed", "The activated profile did not reload.")
            }
            EntioResult.Success(Unit)
        }.valueOrFailure()
        loadedProjects.invalidate(root)
        WebDomainProfileActionResponse(projectId = projectId, status = profiles.status(root))
    }

    @Synchronized
    public fun previewDeactivation(projectId: String, userId: String): WebDomainDeactivationPreviewResponse {
        val root = root(projectId)
        val preview = profiles.previewDeactivation(root, deactivationContext(projectId, root)).valueOrFailure()
        val token = if (preview.active && preview.eligible) {
            issueToken(TokenKind.Deactivation, projectId, userId, configurationFingerprint(root))
        } else {
            null
        }
        return WebDomainDeactivationPreviewResponse(projectId = projectId, deactivationToken = token, preview = preview)
    }

    @Synchronized
    public fun deactivate(
        projectId: String,
        userId: String,
        confirmationToken: String,
        idempotencyKey: String,
    ): WebDomainProfileActionResponse = idempotent(
        scope = "deactivate:$projectId:$userId:$idempotencyKey",
        payload = confirmationToken,
    ) {
        val root = root(projectId)
        consumeToken(confirmationToken, TokenKind.Deactivation, projectId, userId, configurationFingerprint(root))
        val prepared = profiles.prepareDeactivation(root, deactivationContext(projectId, root)).valueOrFailure()
        transactions.commit(prepared) {
            loadedProjects.invalidate(root)
            val project = projectLoader.loadProject(root).valueOrFailure()
            if (project.activeDomainOntology != null) {
                throw DomainOntologyWebFailure("domain-deactivation-verification-failed", "The deactivated profile still reloads.")
            }
            EntioResult.Success(Unit)
        }.valueOrFailure()
        loadedProjects.invalidate(root)
        WebDomainProfileActionResponse(projectId = projectId, status = profiles.status(root))
    }

    public fun foundations(projectId: String): WebDomainFoundationResponse {
        requireActive(projectId)
        return WebDomainFoundationResponse(projectId = projectId, groups = recommendations.foundations())
    }

    public fun planFoundation(
        projectId: String,
        userId: String,
        request: WebDomainFoundationPlanRequest,
    ): WebDomainFoundationPlanResponse {
        val project = requireActive(projectId)
        val plan = recommendations.planFoundation(
            userId = userId,
            projectId = projectId,
            selectedElementIds = request.elementIds,
            selectAll = request.selectAll,
            alreadyPresentIris = project.externalIris(),
            fingerprints = fingerprints(projectId, project),
        )
        return WebDomainFoundationPlanResponse(plan = plan)
    }

    public fun foundationPlan(projectId: String, userId: String, planId: String): WebDomainFoundationPlanResponse {
        requireActive(projectId)
        return WebDomainFoundationPlanResponse(
            plan = recommendations.resolveFoundationPlan(userId, projectId, planId),
        )
    }

    public suspend fun recommend(
        projectId: String,
        userId: String,
        request: WebDomainRecommendationRequest,
    ): WebDomainRecommendationResponse = try {
        withTimeout(recommendationTimeoutMillis) {
            recommendationPermits.withPermit {
                runInterruptible(Dispatchers.Default) {
                    recommendBlocking(projectId, userId, request)
                }
            }
        }
    } catch (_: TimeoutCancellationException) {
        throw DomainOntologyWebFailure(
            "domain-recommendation-timeout",
            "The bounded domain recommendation request did not finish in time.",
        )
    }

    private fun recommendBlocking(
        projectId: String,
        userId: String,
        request: WebDomainRecommendationRequest,
    ): WebDomainRecommendationResponse {
        val project = requireActive(projectId)
        require(request.draftLabel.length <= 2_000) { "domain-query-too-large" }
        requireRecommendationContract(request)
        val context = listOfNotNull(
            request.currentEntityIri,
            request.requiredParentIri,
            request.requiredDomainIri,
            request.requiredRangeIri,
        ) + request.nearbyProjectIris
        context.forEach { requireProjectIri(project, it) }
        request.requiredDatatypeIri?.let { requireDatatypeIri(project, it) }
        resolveRecommendationContext(project, request)
        request.targetSourceId?.let { sourceId ->
            if (project.resolvedSources.none { it.id == sourceId }) {
                throw DomainOntologyWebFailure("unknown-source", "The target ontology source was not found.")
            }
        }
        val current = fingerprints(projectId, project)
        val intent = DomainModelingIntent(
            projectId = projectId,
            operationKind = request.operationKind,
            requestedKind = request.requestedKind,
            draftLabel = request.draftLabel,
            alternateWording = request.alternateWording,
            definition = request.definition,
            currentEntityIri = request.currentEntityIri?.let(::Iri),
            requiredParentIri = request.requiredParentIri?.let(::Iri),
            requiredDomainIri = request.requiredDomainIri?.let(::Iri),
            requiredRangeIri = request.requiredRangeIri?.let(::Iri),
            requiredDatatypeIri = request.requiredDatatypeIri?.let(::Iri),
            nearbyProjectIris = request.nearbyProjectIris.map(::Iri).toSet(),
            alreadyReusedIris = project.externalIris(),
            usedSourceModuleIris = emptySet(),
            targetSourceId = request.targetSourceId,
            languagePreference = request.languagePreference,
            projectFingerprint = current.project,
            profileFingerprint = current.profile,
            ontologyFingerprint = current.ontology,
            currentWorkFingerprint = current.currentWork,
            packageFingerprint = current.packageValue,
            indexFingerprint = current.index,
            broadSearch = request.broadSearch,
        )
        return WebDomainRecommendationResponse(projectId = projectId, result = recommendations.recommend(userId, intent))
    }

    public fun recommendation(
        projectId: String,
        userId: String,
        recommendationId: String,
    ): WebDomainRecommendationDetailResponse {
        val project = requireActive(projectId)
        val recommendation = recommendations.resolve(
            userId,
            projectId,
            recommendationId,
            fingerprints(projectId, project),
        )
        return WebDomainRecommendationDetailResponse(
            projectId = projectId,
            recommendation = recommendation,
            difference = reuse.describe(recommendation.iri, project.graph).valueOrFailure(),
        )
    }

    public fun dependencyPreview(
        projectId: String,
        userId: String,
        recommendationId: String,
    ): WebDomainDependencyPreviewResponse {
        val project = requireActive(projectId)
        return WebDomainDependencyPreviewResponse(
            projectId = projectId,
            recommendationId = recommendationId,
            dependencyIris = recommendations.dependencyIris(
                userId,
                projectId,
                recommendationId,
                fingerprints(projectId, project),
            ).map(Iri::value),
        )
    }

    public fun stageRecommendation(
        projectId: String,
        userId: String,
        recommendationId: String,
        request: WebDomainReuseStageRequest,
        idempotencyKey: String,
    ): WebStagingResponse {
        val project = requireActive(projectId)
        preflight()
        val recommendation = recommendations.resolve(
            userId,
            projectId,
            recommendationId,
            fingerprints(projectId, project),
        )
        val permitted = when (request.action) {
            com.entio.core.DomainReuseAction.Reuse,
            com.entio.core.DomainReuseAction.ReuseAndCustomize,
            com.entio.core.DomainReuseAction.RemoveReuse -> com.entio.core.DomainRecommendationAction.Reuse
            com.entio.core.DomainReuseAction.ExtendLocally -> com.entio.core.DomainRecommendationAction.Extend
            com.entio.core.DomainReuseAction.MapClose,
            com.entio.core.DomainReuseAction.MapRelated -> com.entio.core.DomainRecommendationAction.MapAnnotation
            com.entio.core.DomainReuseAction.ContinueLocally -> null
        }
        if (permitted != null && permitted !in recommendation.permittedActions &&
            !(request.action == com.entio.core.DomainReuseAction.RemoveReuse && recommendation.iri in project.externalIris())
        ) {
            throw DomainOntologyWebFailure("domain-candidate-ineligible", "The selected recommendation does not permit this action.")
        }
        if (request.action == com.entio.core.DomainReuseAction.ContinueLocally) return staging.snapshot(projectId)
        request.localSourceId?.let { sourceId ->
            if (project.resolvedSources.none { it.id == sourceId }) {
                throw DomainOntologyWebFailure("unknown-source", "The local target source was not found.")
            }
        }
        val managedGraph = project.ontologies.singleOrNull {
            it.source.id == DomainOntologyProfileIdentity.MANAGED_SOURCE_ID
        }?.graph ?: throw DomainOntologyWebFailure("domain-managed-source-missing", "The managed reuse source is unavailable.")
        val batch = reuse.prepare(
            DomainReusePreparationRequest(
                action = request.action,
                canonicalIri = recommendation.iri,
                managedSourceId = DomainOntologyProfileIdentity.MANAGED_SOURCE_ID,
                customization = request.customization,
                partialMaterializationAcknowledged = request.partialMaterializationAcknowledged,
                localIri = request.localIri?.let(::Iri),
                localSourceId = request.localSourceId,
                localIriNamespace = project.config.iriNamespace?.namespace,
            ),
            project.graph,
            managedGraph,
        ).valueOrFailure()
        return staging.stageDomainReuse(projectId, batch, userId, idempotencyKey)
    }

    public fun reuseDetail(projectId: String, entityId: String): WebDomainReuseDetailResponse {
        val project = requireActive(projectId)
        val iri = reuse.resolveEntityId(entityId, project.graph)
            ?: throw DomainOntologyWebFailure("domain-reuse-not-found", "The reused domain entity was not found.")
        return WebDomainReuseDetailResponse(projectId = projectId, difference = reuse.describe(iri, project.graph).valueOrFailure())
    }

    public fun migration(projectId: String, preview: Boolean = false): WebDomainMigrationResponse {
        val project = load(projectId)
        val recognized = project.externalIris().size
        val migration = when {
            project.activeDomainOntology != null || recognized == 0 -> DomainOntologyMigrationStatus.NoExistingReuse
            else -> DomainOntologyMigrationStatus.ExistingReuseRecognized
        }
        return WebDomainMigrationResponse(
            projectId = projectId,
            status = migration,
            recognizedIriCount = recognized,
            proposedProfile = DomainOntologyProfile().takeIf { preview && migration == DomainOntologyMigrationStatus.ExistingReuseRecognized },
        )
    }

    override fun close(): Unit {
        if (recommendationDelegate.isInitialized()) recommendationDelegate.value.close()
    }

    private fun preflight(): Unit = try {
        verifyAssets()
        if (recommendations.retrievalAvailability() == com.entio.core.DomainRetrievalAvailability.Unavailable) {
            throw IllegalStateException("The domain search assets are unavailable.")
        }
        Unit
    } catch (failure: DomainOntologyWebFailure) {
        throw failure
    } catch (_: Exception) {
        throw DomainOntologyWebFailure("domain-assets-unavailable", "The approved FIBO package or search index failed verification.")
    }

    private fun requireActive(projectId: String): EntioProject {
        val project = load(projectId)
        if (project.activeDomainOntology == null) {
            throw DomainOntologyWebFailure("domain-profile-inactive", "Activate a domain ontology before using this operation.")
        }
        return project
    }

    private fun load(projectId: String): EntioProject = loadedProjects.load(root(projectId)).valueOrFailure()

    private fun root(projectId: String): Path {
        if (projectRegistry.find(projectId) == null) {
            throw DomainOntologyWebFailure("unknown-project", "The requested project is not registered.")
        }
        return projectRegistry.rootFor(projectId)
    }

    private fun deactivationContext(projectId: String, root: Path): DomainProfileDeactivationContext {
        val managed = root.resolve(DomainOntologyProfileIdentity.MANAGED_SOURCE_PATH)
        val managedStatements = if (
            Files.isRegularFile(managed) && Files.readString(managed) == DomainProfileService.EMPTY_MANAGED_SOURCE
        ) {
            0
        } else {
            1
        }
        val staged = staging.snapshot(projectId)
        val provenance = root.resolve(DomainOntologyProfileIdentity.PROVENANCE_PATH)
        return DomainProfileDeactivationContext(
            managedSourceStatementCount = managedStatements,
            hasStagedDependencies = staged.entries.isNotEmpty(),
            hasProposalDependencies = staged.proposal != null,
            hasActiveProvenance = Files.isRegularFile(provenance) && Files.size(provenance) > 0,
        )
    }

    private fun fingerprints(projectId: String, project: EntioProject): DomainRecommendationFingerprints {
        val root = root(projectId)
        val profilePath = root.resolve(DomainOntologyProfileIdentity.PROFILE_PATH)
        return DomainRecommendationFingerprints(
            project = configurationFingerprint(root),
            profile = if (Files.isRegularFile(profilePath)) sha256(Files.readAllBytes(profilePath)) else "inactive",
            ontology = webGraphFingerprint(project.graph),
            currentWork = sha256(staging.snapshot(projectId).toString().toByteArray(Charsets.UTF_8)),
            packageValue = recommendations.packageFingerprint(),
            index = recommendations.indexFingerprint(),
        )
    }

    private fun requireProjectIri(project: EntioProject, value: String): Unit {
        val iri = Iri(value)
        if (project.graph.triples.none { it.subjectResource == iri || it.objectTerm == iri }) {
            throw DomainOntologyWebFailure("unknown-project-entity", "A supplied semantic context entity is not in the project.")
        }
    }

    private fun requireDatatypeIri(project: EntioProject, value: String): Unit {
        if (value.startsWith(XSD_PREFIX)) return
        requireProjectIri(project, value)
    }

    private fun requireRecommendationContract(request: WebDomainRecommendationRequest): Unit {
        if (request.operationKind != DomainOperationKind.GlobalSemanticSearch && request.requestedKind == null) {
            throw DomainOntologyWebFailure(
                "domain-recommendation-kind-required",
                "Contextual domain recommendations require an explicit entity kind.",
            )
        }
        val requiredKind = when (request.operationKind) {
            DomainOperationKind.CreateClass,
            DomainOperationKind.CreateIndividualTypeSelection,
            DomainOperationKind.EditClassHierarchy,
            DomainOperationKind.EditDomain -> ExternalEntityKind.Class
            DomainOperationKind.CreateObjectProperty -> ExternalEntityKind.ObjectProperty
            DomainOperationKind.CreateDatatypeProperty -> ExternalEntityKind.DatatypeProperty
            else -> null
        }
        if (requiredKind != null && request.requestedKind != requiredKind) {
            throw DomainOntologyWebFailure(
                "domain-recommendation-kind-mismatch",
                "The requested entity kind does not match the authoring operation.",
            )
        }
    }

    private fun resolveRecommendationContext(project: EntioProject, request: WebDomainRecommendationRequest): Unit {
        request.requiredParentIri?.let { requireProjectKind(project, it, setOf(ExternalEntityKind.Class)) }
        request.requiredDomainIri?.let { requireProjectKind(project, it, setOf(ExternalEntityKind.Class)) }
        request.requiredRangeIri?.let { requireProjectKind(project, it, setOf(ExternalEntityKind.Class)) }
        val currentIri = request.currentEntityIri ?: return
        val expectedCurrentKinds = when (request.operationKind) {
            DomainOperationKind.EditLabelOrDefinition -> setOfNotNull(request.requestedKind)
            DomainOperationKind.EditClassHierarchy -> setOf(ExternalEntityKind.Class)
            DomainOperationKind.EditPropertyHierarchy -> setOfNotNull(request.requestedKind)
            DomainOperationKind.EditDomain,
            DomainOperationKind.EditRangeOrDatatype -> setOf(
                ExternalEntityKind.ObjectProperty,
                ExternalEntityKind.DatatypeProperty,
            )
            else -> emptySet()
        }
        if (expectedCurrentKinds.isNotEmpty()) requireProjectKind(project, currentIri, expectedCurrentKinds)
    }

    private fun requireProjectKind(
        project: EntioProject,
        value: String,
        expected: Set<ExternalEntityKind>,
    ): Unit {
        val iri = Iri(value)
        val types = project.graph.triples
            .filter { it.subjectResource == iri && it.predicate.value == RDF_TYPE }
            .mapNotNull { (it.objectTerm as? Iri)?.value }
            .toSet()
        val actual = when {
            OWL_OBJECT_PROPERTY in types || RDF_PROPERTY in types -> ExternalEntityKind.ObjectProperty
            OWL_DATATYPE_PROPERTY in types -> ExternalEntityKind.DatatypeProperty
            OWL_CLASS in types || RDFS_CLASS in types -> ExternalEntityKind.Class
            else -> null
        }
        if (actual !in expected) {
            throw DomainOntologyWebFailure(
                "domain-recommendation-context-kind-mismatch",
                "A supplied semantic context entity has an incompatible project kind.",
            )
        }
    }

    private fun EntioProject.externalIris(): Set<Iri> = graph.triples.flatMap { triple ->
        listOfNotNull(triple.subjectResource as? Iri, triple.objectTerm as? Iri)
    }.filter { iri -> iri.value.startsWith(FIBO_PREFIX) || iri.value.startsWith(COMMONS_PREFIX) }.toSet()

    private fun configurationFingerprint(root: Path): String = sha256(Files.readAllBytes(root.resolve("entio.yaml")))

    @Synchronized
    private fun issueToken(kind: TokenKind, projectId: String, userId: String, fingerprint: String): String {
        cleanupTokens()
        while (tokens.size >= TOKEN_CAPACITY) tokens.remove(tokens.minBy { it.value.createdAt }.key)
        val token = "dct_" + UUID.randomUUID().toString().replace("-", "")
        tokens[token] = ConfirmationToken(kind, projectId, userId, fingerprint, clock.instant())
        return token
    }

    private fun consumeToken(
        token: String,
        kind: TokenKind,
        projectId: String,
        userId: String,
        fingerprint: String,
    ): Unit {
        cleanupTokens()
        val stored = tokens.remove(token)
            ?: throw DomainOntologyWebFailure("domain-activation-stale", "The confirmation token is missing or expired.")
        if (stored.kind != kind || stored.projectId != projectId || stored.userId != userId || stored.fingerprint != fingerprint) {
            throw DomainOntologyWebFailure("domain-activation-stale", "The confirmation token does not match current project state.")
        }
    }

    private fun cleanupTokens(): Unit {
        val cutoff = clock.instant().minus(TOKEN_TTL)
        tokens.entries.removeIf { !it.value.createdAt.isAfter(cutoff) }
    }

    private fun idempotent(
        scope: String,
        payload: String,
        action: () -> WebDomainProfileActionResponse,
    ): WebDomainProfileActionResponse {
        cleanupIdempotentResponses()
        val fingerprint = sha256(payload.toByteArray(Charsets.UTF_8))
        val previous = idempotentResponses[scope]
        if (previous != null) {
            if (previous.payloadFingerprint != fingerprint) {
                throw DomainOntologyWebFailure(
                    "idempotency-conflict",
                    "The idempotency key was already used for a different domain profile request.",
                )
            }
            return previous.response
        }
        while (idempotentResponses.size >= IDEMPOTENCY_CAPACITY) {
            idempotentResponses.remove(idempotentResponses.minBy { it.value.createdAt }.key)
        }
        return action().also {
            idempotentResponses[scope] = IdempotentResponse(fingerprint, it, clock.instant())
        }
    }

    private fun cleanupIdempotentResponses(): Unit {
        val cutoff = clock.instant().minus(IDEMPOTENCY_TTL)
        idempotentResponses.entries.removeIf { !it.value.createdAt.isAfter(cutoff) }
    }

    private fun <T> EntioResult<T>.valueOrFailure(): T = when (this) {
        is EntioResult.Success -> value
        is EntioResult.Failure -> throw DomainOntologyWebFailure(
            issues.firstOrNull()?.code ?: "domain-operation-failed",
            message,
        )
    }

    private data class ConfirmationToken(
        val kind: TokenKind,
        val projectId: String,
        val userId: String,
        val fingerprint: String,
        val createdAt: Instant,
    )

    private data class IdempotentResponse(
        val payloadFingerprint: String,
        val response: WebDomainProfileActionResponse,
        val createdAt: Instant,
    )

    private enum class TokenKind { Activation, Deactivation }

    private companion object {
        val TOKEN_TTL: Duration = Duration.ofMinutes(10)
        val IDEMPOTENCY_TTL: Duration = Duration.ofHours(24)
        const val TOKEN_CAPACITY: Int = 100
        const val IDEMPOTENCY_CAPACITY: Int = 100
        const val MAX_CONCURRENT_RECOMMENDATIONS: Int = 8
        const val RECOMMENDATION_TIMEOUT_MILLIS: Long = 10_000
        const val FIBO_PREFIX: String = "https://spec.edmcouncil.org/fibo/ontology/"
        const val COMMONS_PREFIX: String = "https://www.omg.org/spec/Commons/"
        const val XSD_PREFIX: String = "http://www.w3.org/2001/XMLSchema#"
        const val RDF_TYPE: String = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
        const val RDF_PROPERTY: String = "http://www.w3.org/1999/02/22-rdf-syntax-ns#Property"
        const val RDFS_CLASS: String = "http://www.w3.org/2000/01/rdf-schema#Class"
        const val OWL_CLASS: String = "http://www.w3.org/2002/07/owl#Class"
        const val OWL_OBJECT_PROPERTY: String = "http://www.w3.org/2002/07/owl#ObjectProperty"
        const val OWL_DATATYPE_PROPERTY: String = "http://www.w3.org/2002/07/owl#DatatypeProperty"

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
