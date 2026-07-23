package com.entio.web

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.call
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.close
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.delete
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import com.entio.web.contract.WebApplicationDependencies
import com.entio.web.contract.WebErrorResponse
import com.entio.web.contract.WebProjectListResponse
import com.entio.web.contract.WebSessionResponse
import com.entio.web.contract.WebPageRequest
import com.entio.web.contract.WebStageChangeRequest
import com.entio.web.contract.WebDeletionDependenciesRequest
import com.entio.web.contract.WebPresenceUser
import com.entio.core.Iri
import com.entio.core.SemanticDescriptorKind
import com.entio.core.SemanticSearchQuery
import io.ktor.server.application.ApplicationCall
import com.entio.web.ai.AiCredentialRequest
import com.entio.web.ai.AiCredentialService
import com.entio.web.ai.AiCredentialFailure
import com.entio.web.ai.AiModelWebBoundary
import com.entio.web.ai.models.AiModelCompatibilityPolicy
import com.entio.web.ai.models.AiModelDiscoveryService
import com.entio.web.ai.models.AiModelSelectionService
import com.entio.web.ai.models.AiModelSettingsFailure
import com.entio.web.ai.models.AiModelVerificationService
import com.entio.web.ai.models.AiProviderCallLimiter
import com.entio.web.ai.models.AiProviderSettingsService
import com.entio.web.ai.models.InMemoryAiUserProviderSettingsStore
import com.entio.web.ai.AiProposalService
import com.entio.web.contract.WebAiModelSelectionRequest
import com.entio.web.contract.WebAiProposalCreateRequest
import com.entio.web.contract.WebInferenceMaterializationRequest
import java.time.Clock

/**
 * Installs the smallest server boundary needed before semantic web contracts are added.
 */
public fun Application.module(dependencies: WebApplicationDependencies = WebApplicationDependencies()): Unit {
    install(ContentNegotiation) {
        jackson()
    }
    install(WebSockets)

    val readOnly = ReadOnlyProjectAdapter(dependencies.projectRegistry)
    val ontologyGraph = OntologyGraphWebService(dependencies.projectRegistry)
    val staging = StagingWorkflowService(dependencies.projectRegistry)
    val collaboration = CollaborationHub(dependencies.projectRegistry, staging::snapshot)
    val fibo = FiboWebService(dependencies.projectRegistry, staging)
    val aiCredentials = AiCredentialService(dependencies.aiCredentials, dependencies.aiProvider)
    val aiModelPolicy = AiModelCompatibilityPolicy()
    val aiModelSettingsStore = InMemoryAiUserProviderSettingsStore()
    val aiModelClock = Clock.systemUTC()
    val aiModelLimiter = AiProviderCallLimiter(aiModelClock)
    val aiModelDiscovery = AiModelDiscoveryService(
        dependencies.aiCredentials,
        aiModelSettingsStore,
        dependencies.aiModelProvider,
        aiModelPolicy,
        aiModelLimiter,
        aiModelClock,
    )
    val aiProviderSettings = AiProviderSettingsService(
        dependencies.aiCredentials,
        aiModelSettingsStore,
        aiModelDiscovery,
        dependencies.aiModelProvider.providerId,
        aiModelPolicy.version,
    )
    val aiModelVerification = AiModelVerificationService(
        dependencies.aiCredentials,
        aiModelSettingsStore,
        dependencies.aiModelProvider,
        aiModelLimiter,
        aiModelClock,
    )
    val aiModelSelection = AiModelSelectionService(aiModelSettingsStore, aiModelDiscovery, aiModelVerification)
    val aiModelWeb = AiModelWebBoundary(aiProviderSettings, aiModelDiscovery, aiModelSelection)
    val aiProposal = AiProposalService(
        projectRegistry = dependencies.projectRegistry,
        staging = staging,
        credentials = dependencies.aiCredentials,
        settingsStore = aiModelSettingsStore,
        provider = dependencies.aiProposalProvider,
        fibo = fibo,
    )
    val jobs = SemanticJobManager(
        staging = staging,
        projectRegistry = dependencies.projectRegistry,
        onUpdate = { status ->
            collaboration.job(status.projectId, "semantic-job.${status.status.name.lowercase()}", status.id)
        },
    )
    val inferenceMaterialization = InferenceMaterializationWebService(
        jobs = jobs,
        staging = staging,
        projectRegistry = dependencies.projectRegistry,
    )

    routing {
        get("/health") {
            call.respondText("ok", ContentType.Text.Plain)
        }

        get("/ready") {
            call.respondText("ready", ContentType.Text.Plain)
        }

        get("/api/v1/session") {
            val requestedUser = call.request.header("X-Entio-User")
            val user = dependencies.identityProvider.find(requestedUser)

            if (user == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    WebErrorResponse(
                        requestId = dependencies.requestIdFactory(),
                        code = "unknown-development-user",
                        message = "The requested development user is not configured.",
                    ),
                )
            } else {
                call.respond(
                    WebSessionResponse(
                        user = user,
                        permissions = dependencies.authorization.permissionsFor(user.role),
                    ),
                )
            }
        }

        get("/api/v1/ai/credential-status") {
            call.respondAi { aiCredentials.status(call.requireUser(dependencies).id) }
        }

        get("/api/v1/ai/provider-settings") {
            call.respondAiModels { aiModelWeb.status(call.requireUser(dependencies).id) }
        }

        put("/api/v1/ai/credentials") {
            call.respondAi {
                val user = call.requireUser(dependencies)
                val request = call.receive<AiCredentialRequest>()
                if (request.providerId == dependencies.aiModelProvider.providerId) {
                    aiModelWeb.saveCredential(user.id, request)
                } else {
                    aiCredentials.save(user.id, request)
                }
            }
        }

        post("/api/v1/ai/credentials/test") {
            call.respondAi { aiCredentials.test(call.requireUser(dependencies).id) }
        }

        delete("/api/v1/ai/credentials") {
            call.respondAi {
                val userId = call.requireUser(dependencies).id
                if (aiProviderSettings.settings(userId).providerId == dependencies.aiModelProvider.providerId) {
                    aiModelWeb.removeCredential(userId)
                } else {
                    aiCredentials.remove(userId)
                }
            }
        }

        post("/api/v1/ai/models/discover") {
            call.respondAiModels { aiModelWeb.discover(call.requireUser(dependencies).id) }
        }

        get("/api/v1/ai/models") {
            call.respondAiModels { aiModelWeb.status(call.requireUser(dependencies).id) }
        }

        put("/api/v1/ai/model-selection") {
            call.respondAiModels {
                val request = call.receive<WebAiModelSelectionRequest>()
                aiModelWeb.select(
                    call.requireUser(dependencies).id,
                    request.modelId,
                    call.requiredIdempotencyKey(),
                )
            }
        }

        post("/api/v1/ai/model-selection/test") {
            call.respondAiModels {
                aiModelWeb.retest(call.requireUser(dependencies).id, call.requiredIdempotencyKey())
            }
        }

        post("/api/v1/projects/{projectId}/ai/proposals") {
            call.respondAi {
                val user = call.requireUser(dependencies)
                val request = call.receive<WebAiProposalCreateRequest>()
                aiProposal.start(call.requiredProjectId(), user.id, request.prompt, request.runId)
            }
        }

        get("/api/v1/projects/{projectId}/ai/proposals") {
            call.respondAi {
                aiProposal.list(call.requiredProjectId(), call.requireUser(dependencies).id)
            }
        }

        get("/api/v1/projects/{projectId}/ai/proposals/{runId}") {
            call.respondAi {
                val user = call.requireUser(dependencies)
                aiProposal.get(call.requiredProjectId(), call.requiredAiRunId(), user.id)
            }
        }

        post("/api/v1/projects/{projectId}/ai/proposals/{runId}/edits/{editId}/remove") {
            call.respondAi {
                val user = call.requireUser(dependencies)
                aiProposal.removeEdit(call.requiredProjectId(), call.requiredAiRunId(), call.requiredAiEditId(), user.id)
            }
        }

        post("/api/v1/projects/{projectId}/ai/proposals/{runId}/stage") {
            call.respondAi {
                val user = call.requireUser(dependencies)
                aiProposal.stage(call.requiredProjectId(), call.requiredAiRunId(), user.id)
            }
        }

        post("/api/v1/projects/{projectId}/ai/proposals/{runId}/reject") {
            call.respondAi {
                val user = call.requireUser(dependencies)
                aiProposal.reject(call.requiredProjectId(), call.requiredAiRunId(), user.id)
            }
        }

        post("/api/v1/projects/{projectId}/ai/proposals/{runId}/cancel") {
            call.respondAi {
                val user = call.requireUser(dependencies)
                aiProposal.cancel(call.requiredProjectId(), call.requiredAiRunId(), user.id)
            }
        }

        delete("/api/v1/ai/model-selection") {
            call.respondAiModels { aiModelWeb.clearSelection(call.requireUser(dependencies).id) }
        }

        post("/api/v1/session/logout") {
            call.respondAi {
                val user = call.requireUser(dependencies)
                aiCredentials.logout(user.id)
                aiProviderSettings.logout(user.id)
                mapOf("apiVersion" to "v1", "status" to "LOGGED_OUT")
            }
        }

        get("/api/v1/projects") {
            call.respond(
                WebProjectListResponse(
                    projects = dependencies.projectRegistry.list(),
                ),
            )
        }

        get("/api/v1/projects/{projectId}") {
            val projectId = call.parameters["projectId"]
            val project = projectId?.let(dependencies.projectRegistry::find)

            if (project == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    WebErrorResponse(
                        requestId = dependencies.requestIdFactory(),
                        code = "unknown-project",
                        message = "The requested project is not registered.",
                    ),
                )
            } else {
                call.respond(project)
            }
        }

        get("/api/v1/projects/{projectId}/summary") {
            call.respondReadOnly { readOnly.summary(call.requiredProjectId()) }
        }

        get("/api/v1/projects/{projectId}/sources") {
            call.respondReadOnly { readOnly.sources(call.requiredProjectId(), call.pageRequest()) }
        }

        get("/api/v1/projects/{projectId}/graph") {
            call.respondOntologyGraph {
                val user = call.requireBrowseUser(dependencies)
                ontologyGraph.initial(
                    userId = user.id,
                    projectId = call.requiredProjectId(),
                    requestedSourceIds = call.request.queryParameters.getAll("sourceId").orEmpty().toSet(),
                    seedSourceId = call.request.queryParameters["seedSourceId"],
                    seedIri = call.request.queryParameters["seedIri"],
                    expectedFingerprint = call.request.queryParameters["expectedFingerprint"],
                    continuation = call.request.queryParameters["continuation"],
                )
            }
        }

        get("/api/v1/projects/{projectId}/graph/neighborhood") {
            call.respondOntologyGraph {
                val user = call.requireBrowseUser(dependencies)
                ontologyGraph.neighborhood(
                    userId = user.id,
                    projectId = call.requiredProjectId(),
                    requestedSourceIds = call.request.queryParameters.getAll("sourceId").orEmpty().toSet(),
                    entitySourceId = call.request.queryParameters["entitySourceId"],
                    entityIri = call.request.queryParameters["entityIri"],
                    requestedCategories = call.request.queryParameters.getAll("category").orEmpty().toSet(),
                    expectedFingerprint = call.request.queryParameters["expectedFingerprint"],
                    continuation = call.request.queryParameters["continuation"],
                )
            }
        }

        get("/api/v1/projects/{projectId}/hierarchy") {
            call.respondReadOnly {
                readOnly.hierarchy(
                    projectId = call.requiredProjectId(),
                    sourceId = call.request.queryParameters["sourceId"],
                    parentIri = call.request.queryParameters["parentIri"]?.takeIf(String::isNotBlank)?.let(::Iri),
                    request = call.pageRequest(),
                )
            }
        }

        get("/api/v1/projects/{projectId}/outline") {
            call.respondReadOnly {
                readOnly.outline(
                    projectId = call.requiredProjectId(),
                    sourceId = call.request.queryParameters["sourceId"],
                    request = call.pageRequest(),
                )
            }
        }

        get("/api/v1/projects/{projectId}/entities") {
            call.respondReadOnly {
                val iri = call.request.queryParameters["iri"]
                    ?.takeIf(String::isNotBlank)
                    ?: throw ProjectReadFailure("missing-entity-iri", "An entity IRI is required.")
                readOnly.entity(
                    projectId = call.requiredProjectId(),
                    entityIri = Iri(iri),
                    sourceId = call.request.queryParameters["sourceId"],
                )
            }
        }

        get("/api/v1/projects/{projectId}/search") {
            call.respondReadOnly {
                val text = call.request.queryParameters["q"]
                    ?: throw ProjectReadFailure("invalid-search-query", "Search text is required.")
                val kind = call.request.queryParameters["kind"]?.let(::descriptorKind)
                readOnly.search(
                    projectId = call.requiredProjectId(),
                    query = SemanticSearchQuery(
                        text = text,
                        preferredLanguage = call.request.queryParameters["language"],
                        kind = kind,
                        sourceId = call.request.queryParameters["sourceId"],
                    ),
                    request = call.pageRequest(),
                )
            }
        }

        get("/api/v1/projects/{projectId}/shacl/shapes") {
            call.respondReadOnly { readOnly.shaclShapes(call.requiredProjectId()) }
        }

        get("/api/v1/projects/{projectId}/external/fibo/modules") {
            call.respondExternal {
                fibo.modules(
                    projectId = call.requiredProjectId(),
                    curatedOnly = call.request.queryParameters["curated"]?.toBoolean() ?: true,
                    request = call.pageRequest(),
                )
            }
        }

        get("/api/v1/projects/{projectId}/external/fibo/module-elements") {
            call.respondExternal {
                val moduleIri = call.request.queryParameters["moduleIri"]
                    ?.takeIf(String::isNotBlank)
                    ?.let(::Iri)
                    ?: throw WebWorkflowFailure("missing-external-module-iri", "An external module IRI is required.")
                fibo.moduleElements(call.requiredProjectId(), moduleIri, call.pageRequest())
            }
        }

        get("/api/v1/projects/{projectId}/external/fibo/search") {
            call.respondExternal {
                val text = call.request.queryParameters["q"]
                    ?.takeIf(String::isNotBlank)
                    ?: throw WebWorkflowFailure("invalid-external-search", "External search text is required.")
                fibo.search(
                    projectId = call.requiredProjectId(),
                    text = text,
                    kind = call.request.queryParameters["kind"]?.let(::externalKind),
                    moduleIri = call.request.queryParameters["moduleIri"]?.takeIf(String::isNotBlank)?.let(::Iri),
                    curatedOnly = call.request.queryParameters["curated"]?.toBoolean() ?: false,
                    request = call.pageRequest(),
                )
            }
        }

        get("/api/v1/projects/{projectId}/external/fibo/details") {
            call.respondExternal {
                val iri = call.request.queryParameters["iri"]
                    ?.takeIf(String::isNotBlank)
                    ?.let(::Iri)
                    ?: throw WebWorkflowFailure("missing-external-iri", "An external element IRI is required.")
                fibo.details(call.requiredProjectId(), iri)
            }
        }

        post("/api/v1/projects/{projectId}/external/fibo/proposals") {
            call.respondWorkflow {
                val user = call.requireUser(dependencies)
                val projectId = call.requiredProjectId()
                val result = fibo.stageProposal(projectId, call.receive<WebFiboProposalRequest>(), user.id)
                collaboration.stagedChange(projectId, userId = user.id)
                result
            }
        }

        get("/api/v1/projects/{projectId}/staged") {
            call.respondWorkflow { staging.snapshot(call.requiredProjectId()) }
        }

        get("/api/v1/projects/{projectId}/activity") {
            call.respondWorkflow {
                call.requireUser(dependencies)
                collaboration.recentSharedActivity(call.requiredProjectId(), call.request.queryParameters["limit"]?.toIntOrNull() ?: 1000)
            }
        }

        post("/api/v1/projects/{projectId}/deletion-dependencies") {
            call.respondWorkflow {
                staging.deletionDependencies(
                    call.requiredProjectId(),
                    call.receive<WebDeletionDependenciesRequest>(),
                )
            }
        }

        post("/api/v1/projects/{projectId}/staged") {
            call.respondWorkflow {
                val user = call.requireUser(dependencies)
                if (!dependencies.authorization.isAllowed(user.role, com.entio.web.contract.WebAction.STAGE_OWN_CHANGE)) {
                    throw WebWorkflowFailure("forbidden", "The current user cannot stage changes.")
                }
                val projectId = call.requiredProjectId()
                val result = staging.stage(projectId, call.receive<WebStageChangeRequest>(), user.id)
                jobs.invalidateProposalJobs(projectId)
                collaboration.stagedChange(projectId, result.entries.lastOrNull()?.id, user.id)
                result
            }
        }

        delete("/api/v1/projects/{projectId}/staged/{stagedId}") {
            call.respondWorkflow {
                val projectId = call.requiredProjectId()
                val user = call.requireUser(dependencies)
                val result = staging.discard(projectId, call.requiredStagedId())
                jobs.invalidateProposalJobs(projectId)
                collaboration.stagedChange(projectId, userId = user.id)
                result
            }
        }

        post("/api/v1/projects/{projectId}/proposal/preview") {
            call.respondWorkflow {
                val projectId = call.requiredProjectId()
                val result = staging.preview(projectId, call.requireUser(dependencies).id)
                collaboration.proposal(projectId, "proposal.previewed", result.proposal?.id, call.requireUser(dependencies).id)
                result
            }
        }

        post("/api/v1/projects/{projectId}/proposal/approve") {
            call.respondWorkflow {
                val projectId = call.requiredProjectId()
                val result = staging.approve(projectId, call.requireReviewer(dependencies).id)
                collaboration.proposal(projectId, "proposal.approved", result.proposal?.id, call.requireReviewer(dependencies).id)
                result
            }
        }

        post("/api/v1/projects/{projectId}/proposal/reject") {
            call.respondWorkflow {
                val projectId = call.requiredProjectId()
                val result = staging.reject(projectId, call.requireReviewer(dependencies).id)
                jobs.invalidateProposalJobs(projectId)
                collaboration.proposal(projectId, "proposal.rejected", userId = call.requireReviewer(dependencies).id)
                result
            }
        }

        post("/api/v1/projects/{projectId}/proposal/apply") {
            call.respondWorkflow {
                val projectId = call.requiredProjectId()
                val result = staging.apply(projectId, call.requireReviewer(dependencies).id)
                collaboration.proposal(
                    projectId,
                    if (result.proposal?.status == "APPLYFAILED") "proposal.conflicted" else "proposal.applied",
                    result.proposal?.id,
                    call.requireReviewer(dependencies).id,
                )
                result
            }
        }

        post("/api/v1/projects/{projectId}/semantic-jobs") {
            call.respondJob {
                val user = call.requireUser(dependencies)
                jobs.submit(call.requiredProjectId(), call.receive<WebJobRequest>(), user.id)
            }
        }

        get("/api/v1/projects/{projectId}/semantic-jobs/{jobId}") {
            call.respondJob {
                jobs.find(call.requiredProjectId(), call.requiredJobId())
                    ?: throw WebWorkflowFailure("unknown-semantic-job", "The requested semantic job was not found.")
            }
        }

        get("/api/v1/projects/{projectId}/semantic-jobs/{jobId}/details") {
            call.respondJob {
                val user = call.requireUser(dependencies)
                jobs.details(call.requiredProjectId(), call.requiredJobId(), requestingUserId = user.id)
                    ?: throw WebWorkflowFailure("unknown-semantic-job", "The requested semantic job was not found.")
            }
        }

        post("/api/v1/projects/{projectId}/semantic-jobs/{jobId}/materializations") {
            call.respondJob {
                val user = call.requireUser(dependencies)
                if (!dependencies.authorization.isAllowed(user.role, com.entio.web.contract.WebAction.STAGE_OWN_CHANGE)) {
                    throw WebWorkflowFailure("forbidden", "The current user cannot stage changes.")
                }
                val projectId = call.requiredProjectId()
                val result = inferenceMaterialization.materialize(
                    projectId,
                    call.requiredJobId(),
                    user.id,
                    call.receive<WebInferenceMaterializationRequest>(),
                )
                jobs.invalidateProposalJobs(projectId)
                collaboration.stagedChange(projectId, userId = user.id)
                result
            }
        }

        delete("/api/v1/projects/{projectId}/semantic-jobs/{jobId}") {
            call.respondJob {
                call.requireReviewer(dependencies)
                jobs.cancel(call.requiredProjectId(), call.requiredJobId())
            }
        }

        webSocket("/api/v1/projects/{projectId}/collaboration") {
            val projectId = call.requiredProjectId()
            val requestedUser = call.request.queryParameters["userId"]
            val user = dependencies.identityProvider.find(requestedUser)
            if (user == null) {
                close(io.ktor.websocket.CloseReason(io.ktor.websocket.CloseReason.Codes.VIOLATED_POLICY, "unknown-development-user"))
            } else {
                collaboration.join(
                    projectId = projectId,
                    user = WebPresenceUser(user.id, user.displayName, user.avatar, user.role.name),
                    socket = this,
                )
            }
        }
    }
}

private fun ApplicationCall.requiredProjectId(): String = parameters["projectId"]
    ?.takeIf(String::isNotBlank)
    ?: throw ProjectReadFailure("missing-project-id", "A project id is required.")

private fun ApplicationCall.pageRequest(): WebPageRequest = try {
    WebPageRequest(
        offset = request.queryParameters["offset"]?.toIntOrNull() ?: 0,
        limit = request.queryParameters["limit"]?.toIntOrNull() ?: WebPageRequest.DEFAULT_PAGE_LIMIT,
    )
} catch (exception: IllegalArgumentException) {
    throw ProjectReadFailure("invalid-pagination", exception.message ?: "Invalid pagination.")
}

private fun ApplicationCall.requiredStagedId(): String = parameters["stagedId"]
    ?.takeIf(String::isNotBlank)
    ?: throw WebWorkflowFailure("missing-staged-id", "A staged change id is required.")

private fun ApplicationCall.requiredAiRunId(): String = parameters["runId"]
    ?.takeIf(String::isNotBlank)
    ?: throw WebWorkflowFailure("unknown-ai-run", "An AI proposal run id is required.")

private fun ApplicationCall.requiredAiEditId(): String = parameters["editId"]
    ?.takeIf(String::isNotBlank)
    ?: throw WebWorkflowFailure("unknown-ai-edit", "An AI proposal edit id is required.")

private fun ApplicationCall.requiredJobId(): String = parameters["jobId"]
    ?.takeIf(String::isNotBlank)
    ?: throw WebWorkflowFailure("missing-semantic-job-id", "A semantic job id is required.")

private fun ApplicationCall.requiredIdempotencyKey(): String = request.header("Idempotency-Key")
    ?.takeIf(String::isNotBlank)
    ?: throw WebWorkflowFailure("missing-idempotency-key", "An Idempotency-Key header is required for this request.")

private fun ApplicationCall.requireUser(dependencies: WebApplicationDependencies): com.entio.web.contract.WebSessionUser {
    val user = dependencies.identityProvider.find(request.headers["X-Entio-User"])
        ?: throw WebWorkflowFailure("unknown-development-user", "The requested development user is not configured.")
    return user
}

private fun ApplicationCall.requireReviewer(dependencies: WebApplicationDependencies): com.entio.web.contract.WebSessionUser {
    val user = requireUser(dependencies)
    if (user.role != com.entio.web.contract.WebRole.REVIEWER) {
        throw WebWorkflowFailure("forbidden", "Reviewer permission is required for this proposal action.")
    }
    return user
}

private fun ApplicationCall.requireBrowseUser(dependencies: WebApplicationDependencies): com.entio.web.contract.WebSessionUser {
    val user = requireUser(dependencies)
    if (!dependencies.authorization.isAllowed(user.role, com.entio.web.contract.WebAction.BROWSE)) {
        throw OntologyGraphWebFailure("forbidden", "Browse permission is required for ontology graph reads.")
    }
    return user
}

private fun descriptorKind(value: String): SemanticDescriptorKind =
    SemanticDescriptorKind.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        ?: throw ProjectReadFailure("invalid-kind", "Unknown semantic entity kind '$value'.")

private fun externalKind(value: String): com.entio.core.ExternalEntityKind =
    com.entio.core.ExternalEntityKind.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        ?: throw WebWorkflowFailure("invalid-external-kind", "Unknown external entity kind '$value'.")

private suspend fun ApplicationCall.respondReadOnly(block: () -> Any): Unit = try {
    respond(block())
} catch (failure: ProjectReadFailure) {
    val status = when (failure.code) {
        "unknown-project", "missing-entity", "missing-hierarchy-parent" -> HttpStatusCode.NotFound
        "project-load-failed" -> HttpStatusCode.UnprocessableEntity
        else -> HttpStatusCode.BadRequest
    }
    respond(
        status,
        WebErrorResponse(
            requestId = request.headers["X-Request-Id"] ?: "web-${System.nanoTime()}",
            code = failure.code,
            message = failure.message ?: "The read request could not be completed.",
        ),
    )
}

private suspend fun ApplicationCall.respondOntologyGraph(block: () -> Any): Unit = try {
    respond(block())
} catch (failure: OntologyGraphWebFailure) {
    val status = when (failure.code) {
        "unknown-project", "unknown-graph-entity" -> HttpStatusCode.NotFound
        "forbidden" -> HttpStatusCode.Forbidden
        "project-load-failed" -> HttpStatusCode.UnprocessableEntity
        "stale-graph-fingerprint" -> HttpStatusCode.Conflict
        else -> HttpStatusCode.BadRequest
    }
    respond(status, WebErrorResponse(requestId = request.headers["X-Request-Id"] ?: "web-${System.nanoTime()}", code = failure.code, message = failure.message ?: "The ontology graph request could not be completed."))
} catch (failure: WebWorkflowFailure) {
    val status = if (failure.code == "unknown-development-user") HttpStatusCode.Unauthorized else HttpStatusCode.BadRequest
    respond(status, WebErrorResponse(requestId = request.headers["X-Request-Id"] ?: "web-${System.nanoTime()}", code = failure.code, message = failure.message ?: "The ontology graph request could not be completed."))
}

private suspend fun ApplicationCall.respondExternal(block: () -> Any): Unit = try {
    respond(block())
} catch (failure: WebWorkflowFailure) {
    val status = when (failure.code) {
        "unknown-project", "external-element-not-found" -> HttpStatusCode.NotFound
        else -> HttpStatusCode.BadRequest
    }
    respond(
        status,
        WebErrorResponse(
            requestId = request.headers["X-Request-Id"] ?: "web-${System.nanoTime()}",
            code = failure.code,
            message = failure.message ?: "The external ontology request could not be completed.",
        ),
    )
}

private suspend fun ApplicationCall.respondAi(block: suspend () -> Any): Unit = try {
    respond(block())
} catch (failure: com.entio.web.ai.AiCredentialFailure) {
    respond(
        HttpStatusCode.BadRequest,
        WebErrorResponse(
            requestId = request.headers["X-Request-Id"] ?: "web-${System.nanoTime()}",
            code = failure.code,
            message = failure.message ?: "The AI credential request could not be completed.",
        ),
    )
} catch (failure: WebWorkflowFailure) {
    val status = if (failure.code == "unknown-development-user") HttpStatusCode.Unauthorized else HttpStatusCode.BadRequest
    respond(
        status,
        WebErrorResponse(
            requestId = request.headers["X-Request-Id"] ?: "web-${System.nanoTime()}",
            code = failure.code,
            message = failure.message ?: "The AI credential request could not be completed.",
        ),
    )
}

private suspend fun ApplicationCall.respondAiModels(block: suspend () -> Any): Unit = try {
    respond(block())
} catch (failure: AiModelSettingsFailure) {
    val publicCode = when (failure.code) {
        "ai-credential-missing" -> "AI_CREDENTIAL_MISSING"
        "ai-provider-not-supported" -> "AI_MODEL_NOT_APPROVED"
        "ai-model-not-available" -> "AI_MODEL_NOT_AVAILABLE"
        "ai-model-selection-required" -> "AI_MODEL_SELECTION_REQUIRED"
        "ai-provider-local-rate-limited" -> "AI_PROVIDER_RATE_LIMITED"
        "ai-provider-call-in-progress", "ai-verification-in-progress" -> "AI_MODEL_VERIFICATION_FAILED"
        "ai-idempotency-key-invalid", "ai-idempotency-conflict" -> "AI_MODEL_VERIFICATION_FAILED"
        else -> "AI_MODEL_VERIFICATION_FAILED"
    }
    val status = when (failure.code) {
        "ai-provider-local-rate-limited" -> HttpStatusCode.TooManyRequests
        "ai-provider-call-in-progress", "ai-verification-in-progress", "ai-idempotency-conflict" -> HttpStatusCode.Conflict
        "ai-model-not-available", "ai-model-selection-required" -> HttpStatusCode.UnprocessableEntity
        else -> HttpStatusCode.BadRequest
    }
    respond(
        status,
        WebErrorResponse(
            requestId = request.headers["X-Request-Id"] ?: "web-${System.nanoTime()}",
            code = publicCode,
            message = aiModelErrorMessage(publicCode),
        ),
    )
} catch (failure: WebWorkflowFailure) {
    val status = if (failure.code == "unknown-development-user") HttpStatusCode.Unauthorized else HttpStatusCode.BadRequest
    respond(
        status,
        WebErrorResponse(
            requestId = request.headers["X-Request-Id"] ?: "web-${System.nanoTime()}",
            code = failure.code,
            message = failure.message ?: "The AI model request could not be completed.",
        ),
    )
}

private fun aiModelErrorMessage(code: String): String = when (code) {
    "AI_CREDENTIAL_MISSING" -> "Configure an OpenAI credential before managing models."
    "AI_MODEL_NOT_AVAILABLE" -> "The requested model is not available in the current discovered candidate set."
    "AI_MODEL_SELECTION_REQUIRED" -> "Select and verify a model before testing it."
    "AI_PROVIDER_RATE_LIMITED" -> "Entio temporarily limited repeated provider model requests."
    "AI_MODEL_NOT_APPROVED" -> "The requested provider or model is not approved for this server boundary."
    else -> "The model selection request could not be completed safely."
}

private suspend inline fun <reified T : Any> ApplicationCall.respondWorkflow(crossinline block: suspend () -> T): Unit = try {
    respond(block())
} catch (failure: WebWorkflowFailure) {
    val status = when (failure.code) {
        "unknown-project", "unknown-staged-change" -> HttpStatusCode.NotFound
        "forbidden" -> HttpStatusCode.Forbidden
        "project-load-failed" -> HttpStatusCode.UnprocessableEntity
        else -> HttpStatusCode.BadRequest
    }
    respond(
        status,
        WebErrorResponse(
            requestId = request.headers["X-Request-Id"] ?: "web-${System.nanoTime()}",
            code = failure.code,
            message = failure.message ?: "The workflow request could not be completed.",
        ),
    )
}

private suspend inline fun <reified T : Any> ApplicationCall.respondJob(crossinline block: suspend () -> T): Unit = try {
    respond(block())
} catch (failure: WebWorkflowFailure) {
    val status = when (failure.code) {
        "unknown-project", "unknown-semantic-job" -> HttpStatusCode.NotFound
        "forbidden" -> HttpStatusCode.Forbidden
        "materialization-in-progress" -> HttpStatusCode.Conflict
        "materialization-timeout" -> HttpStatusCode.RequestTimeout
        else -> HttpStatusCode.BadRequest
    }
    respond(
        status,
        WebErrorResponse(
            requestId = request.headers["X-Request-Id"] ?: "web-${System.nanoTime()}",
            code = failure.code,
            message = failure.message ?: "The semantic job request could not be completed.",
        ),
    )
}
