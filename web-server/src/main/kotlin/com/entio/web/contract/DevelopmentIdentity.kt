package com.entio.web.contract

public class DevelopmentIdentityProvider(
    users: List<WebSessionUser> = defaultUsers(),
    private val defaultUserId: String = "alice",
) {
    private val usersById: Map<String, WebSessionUser> = users.associateBy(WebSessionUser::id)

    public fun find(userId: String?): WebSessionUser? = usersById[userId ?: defaultUserId]

    public fun user(userId: String): WebSessionUser = find(userId)
        ?: throw IllegalArgumentException("unknown-development-user")

    private companion object {
        fun defaultUsers(): List<WebSessionUser> = listOf(
            WebSessionUser("alice", "Alice Contributor", "AC", WebRole.CONTRIBUTOR),
            WebSessionUser("bob", "Bob Reviewer", "BR", WebRole.REVIEWER),
        )
    }
}

public enum class WebAction {
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

public class DevelopmentAuthorization {
    public fun permissionsFor(role: WebRole): Set<WebPermission> = when (role) {
        WebRole.CONTRIBUTOR -> setOf(
            WebPermission.BROWSE,
            WebPermission.PREPARE_EDIT,
            WebPermission.STAGE_OWN_CHANGE,
            WebPermission.REMOVE_OWN_CHANGE,
        )
        WebRole.REVIEWER -> WebPermission.entries.toSet()
    }

    public fun isAllowed(role: WebRole, action: WebAction): Boolean = when (action) {
        WebAction.BROWSE -> true
        WebAction.PREPARE_EDIT -> true
        WebAction.STAGE_OWN_CHANGE -> true
        WebAction.REMOVE_OWN_CHANGE -> true
        WebAction.REVIEW_ANY_CHANGE,
        WebAction.APPROVE,
        WebAction.REJECT,
        WebAction.APPLY,
        WebAction.ROLLBACK,
        WebAction.CANCEL_SEMANTIC_JOB,
        -> role == WebRole.REVIEWER
    }
}
