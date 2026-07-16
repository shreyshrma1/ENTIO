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
import com.entio.web.contract.WebStagingResponse
import com.entio.web.contract.WebPresenceUser
import com.entio.core.Iri
import com.entio.core.SemanticDescriptorKind
import com.entio.core.SemanticSearchQuery
import io.ktor.server.application.ApplicationCall
import com.entio.web.ai.AiCredentialRequest
import com.entio.web.ai.AiCredentialService

/**
 * Installs the smallest server boundary needed before semantic web contracts are added.
 */
public fun Application.module(dependencies: WebApplicationDependencies = WebApplicationDependencies()): Unit {
    install(ContentNegotiation) {
        jackson()
    }
    install(WebSockets)

    val readOnly = ReadOnlyProjectAdapter(dependencies.projectRegistry)
    val staging = StagingWorkflowService(dependencies.projectRegistry)
    val collaboration = CollaborationHub(dependencies.projectRegistry, staging::snapshot)
    val fibo = FiboWebService(dependencies.projectRegistry, staging)
    val aiCredentials = AiCredentialService(dependencies.aiCredentials, dependencies.aiProvider)
    val jobs = SemanticJobManager(
        staging = staging,
        projectRegistry = dependencies.projectRegistry,
        onUpdate = { status ->
            collaboration.job(status.projectId, "semantic-job.${status.status.name.lowercase()}", status.id)
        },
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

        put("/api/v1/ai/credentials") {
            call.respondAi {
                val user = call.requireUser(dependencies)
                aiCredentials.save(user.id, call.receive<AiCredentialRequest>())
            }
        }

        post("/api/v1/ai/credentials/test") {
            call.respondAi { aiCredentials.test(call.requireUser(dependencies).id) }
        }

        delete("/api/v1/ai/credentials") {
            call.respondAi { aiCredentials.remove(call.requireUser(dependencies).id) }
        }

        post("/api/v1/session/logout") {
            call.respondAi {
                val user = call.requireUser(dependencies)
                aiCredentials.logout(user.id)
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
                fibo.stageProposal(call.requiredProjectId(), call.receive<WebFiboProposalRequest>(), user.id)
            }
        }

        get("/api/v1/projects/{projectId}/staged") {
            call.respondWorkflow { staging.snapshot(call.requiredProjectId()) }
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
                collaboration.stagedChange(projectId, result.entries.lastOrNull()?.id)
                result
            }
        }

        delete("/api/v1/projects/{projectId}/staged/{stagedId}") {
            call.respondWorkflow {
                val projectId = call.requiredProjectId()
                val result = staging.discard(projectId, call.requiredStagedId())
                jobs.invalidateProposalJobs(projectId)
                collaboration.stagedChange(projectId)
                result
            }
        }

        post("/api/v1/projects/{projectId}/proposal/preview") {
            call.respondWorkflow {
                val projectId = call.requiredProjectId()
                val result = staging.preview(projectId, call.requireUser(dependencies).id)
                collaboration.proposal(projectId, "proposal.previewed", result.proposal?.id)
                result
            }
        }

        post("/api/v1/projects/{projectId}/proposal/approve") {
            call.respondWorkflow {
                val projectId = call.requiredProjectId()
                val result = staging.approve(projectId, call.requireReviewer(dependencies).id)
                collaboration.proposal(projectId, "proposal.approved", result.proposal?.id)
                result
            }
        }

        post("/api/v1/projects/{projectId}/proposal/reject") {
            call.respondWorkflow {
                val projectId = call.requiredProjectId()
                val result = staging.reject(projectId, call.requireReviewer(dependencies).id)
                jobs.invalidateProposalJobs(projectId)
                collaboration.proposal(projectId, "proposal.rejected")
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
                )
                result
            }
        }

        post("/api/v1/projects/{projectId}/semantic-jobs") {
            call.respondJob {
                call.requireUser(dependencies)
                jobs.submit(call.requiredProjectId(), call.receive<WebJobRequest>())
            }
        }

        get("/api/v1/projects/{projectId}/semantic-jobs/{jobId}") {
            call.respondJob {
                jobs.find(call.requiredProjectId(), call.requiredJobId())
                    ?: throw WebWorkflowFailure("unknown-semantic-job", "The requested semantic job was not found.")
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

private fun ApplicationCall.requiredJobId(): String = parameters["jobId"]
    ?.takeIf(String::isNotBlank)
    ?: throw WebWorkflowFailure("missing-semantic-job-id", "A semantic job id is required.")

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

private suspend fun ApplicationCall.respondWorkflow(block: suspend () -> WebStagingResponse): Unit = try {
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

private suspend fun ApplicationCall.respondJob(block: suspend () -> WebSemanticJobStatus): Unit = try {
    respond(block())
} catch (failure: WebWorkflowFailure) {
    val status = when (failure.code) {
        "unknown-project", "unknown-semantic-job" -> HttpStatusCode.NotFound
        "forbidden" -> HttpStatusCode.Forbidden
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
