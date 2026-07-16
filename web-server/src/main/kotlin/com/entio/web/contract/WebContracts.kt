package com.entio.web.contract

import java.nio.file.Path
import java.util.UUID

public const val WEB_API_VERSION: String = "v1"

public data class WebErrorResponse(
    val apiVersion: String = WEB_API_VERSION,
    val requestId: String,
    val code: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
)

public data class WebProjectDescriptor(
    val id: String,
    val displayName: String,
)

public data class WebProjectListResponse(
    val apiVersion: String = WEB_API_VERSION,
    val projects: List<RegisteredProject>,
)

public data class WebSessionUser(
    val id: String,
    val displayName: String,
    val avatar: String,
    val role: WebRole,
)

public data class WebSessionResponse(
    val apiVersion: String = WEB_API_VERSION,
    val user: WebSessionUser,
    val permissions: Set<WebPermission>,
)

public enum class WebRole {
    CONTRIBUTOR,
    REVIEWER,
}

public enum class WebPermission {
    BROWSE,
    PREPARE_EDIT,
    STAGE_OWN_CHANGE,
    REMOVE_OWN_CHANGE,
    REVIEW_ANY_CHANGE,
    APPROVE,
    REJECT,
    APPLY,
    ROLLBACK,
    CANCEL_SEMANTIC_JOB,
}

public data class WebPageRequest(
    val offset: Int = 0,
    val limit: Int = DEFAULT_PAGE_LIMIT,
) {
    init {
        require(offset >= 0) { "offset-must-not-be-negative" }
        require(limit in 1..MAX_PAGE_LIMIT) { "limit-must-be-between-1-and-$MAX_PAGE_LIMIT" }
    }

    public companion object {
        public const val DEFAULT_PAGE_LIMIT: Int = 50
        public const val MAX_PAGE_LIMIT: Int = 100
    }
}

public data class WebPage<T>(
    val items: List<T>,
    val offset: Int,
    val limit: Int,
    val total: Int,
    val nextOffset: Int?,
)

public fun <T> List<T>.toWebPage(request: WebPageRequest): WebPage<T> {
    val pageItems = drop(request.offset).take(request.limit)
    val nextOffset = (request.offset + pageItems.size).takeIf { it < size }
    return WebPage(
        items = pageItems,
        offset = request.offset,
        limit = request.limit,
        total = size,
        nextOffset = nextOffset,
    )
}

public fun encodeWebIri(iri: String): String = java.net.URLEncoder.encode(iri, Charsets.UTF_8)

public data class WebApplicationDependencies(
    val projectRegistry: ProjectRegistry = InMemoryProjectRegistry(emptySet()),
    val identityProvider: DevelopmentIdentityProvider = DevelopmentIdentityProvider(),
    val authorization: DevelopmentAuthorization = DevelopmentAuthorization(),
    val requestIdFactory: () -> String = { UUID.randomUUID().toString() },
)

public fun normalizeProjectRoot(path: Path): Path = path.toAbsolutePath().normalize()
