package com.entio.web

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.delete
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
import com.entio.core.Iri
import com.entio.core.SemanticDescriptorKind
import com.entio.core.SemanticSearchQuery
import io.ktor.server.application.ApplicationCall

/**
 * Installs the smallest server boundary needed before semantic web contracts are added.
 */
public fun Application.module(dependencies: WebApplicationDependencies = WebApplicationDependencies()): Unit {
    install(ContentNegotiation) {
        jackson()
    }

    val readOnly = ReadOnlyProjectAdapter(dependencies.projectRegistry)
    val staging = StagingWorkflowService(dependencies.projectRegistry)

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

        get("/api/v1/projects/{projectId}/staged") {
            call.respondWorkflow { staging.snapshot(call.requiredProjectId()) }
        }

        post("/api/v1/projects/{projectId}/staged") {
            call.respondWorkflow {
                val user = call.requireUser(dependencies)
                if (!dependencies.authorization.isAllowed(user.role, com.entio.web.contract.WebAction.STAGE_OWN_CHANGE)) {
                    throw WebWorkflowFailure("forbidden", "The current user cannot stage changes.")
                }
                staging.stage(call.requiredProjectId(), call.receive<WebStageChangeRequest>(), user.id)
            }
        }

        delete("/api/v1/projects/{projectId}/staged/{stagedId}") {
            call.respondWorkflow { staging.discard(call.requiredProjectId(), call.requiredStagedId()) }
        }

        post("/api/v1/projects/{projectId}/proposal/preview") {
            call.respondWorkflow { staging.preview(call.requiredProjectId(), call.requireUser(dependencies).id) }
        }

        post("/api/v1/projects/{projectId}/proposal/approve") {
            call.respondWorkflow { staging.approve(call.requiredProjectId(), call.requireReviewer(dependencies).id) }
        }

        post("/api/v1/projects/{projectId}/proposal/reject") {
            call.respondWorkflow { staging.reject(call.requiredProjectId(), call.requireReviewer(dependencies).id) }
        }

        post("/api/v1/projects/{projectId}/proposal/apply") {
            call.respondWorkflow { staging.apply(call.requiredProjectId(), call.requireReviewer(dependencies).id) }
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
