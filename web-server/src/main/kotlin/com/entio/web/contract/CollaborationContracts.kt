package com.entio.web.contract

public data class WebPresenceUser(
    val id: String,
    val displayName: String,
    val avatar: String,
    val role: String,
    val activeEntityIri: String? = null,
)

public data class WebCollaborationEvent(
    val eventId: String,
    val projectId: String,
    val collaborationSessionId: String,
    val sequence: Long,
    val eventType: String,
    val timestamp: String,
    val userId: String? = null,
    val entityIri: String? = null,
    val stagedChangeId: String? = null,
    val proposalId: String? = null,
    val jobId: String? = null,
    val data: Map<String, Any?> = emptyMap(),
)

/** Bounded, in-memory shared workflow activity. Private presence activity is excluded. */
public data class WebActivitySnapshot(
    val projectId: String,
    val events: List<WebCollaborationEvent>,
    val truncated: Boolean,
)
