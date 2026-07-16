package com.entio.web

import com.entio.web.contract.ProjectRegistry
import com.entio.web.contract.WebCollaborationEvent
import com.entio.web.contract.WebPresenceUser
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.Frame
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private data class ConnectedUser(
    val user: WebPresenceUser,
    var activeEntityIri: String? = null,
)

private class CollaborationRoom(
    val projectId: String,
    val sessionId: String = "collaboration-$projectId",
    val clients: MutableMap<WebSocketServerSession, ConnectedUser> = linkedMapOf(),
    var sequence: Long = 0,
)

/** Server-authoritative event hub. It broadcasts coordination state, never ontology mutations. */
public class CollaborationHub(
    private val projectRegistry: ProjectRegistry,
    private val stagingSnapshot: (String) -> Any? = { null },
    private val objectMapper: ObjectMapper = ObjectMapper(),
) {
    private val rooms: MutableMap<String, CollaborationRoom> = ConcurrentHashMap()

    public suspend fun join(
        projectId: String,
        user: WebPresenceUser,
        socket: WebSocketServerSession,
    ): Unit {
        projectRegistry.find(projectId) ?: throw WebWorkflowFailure("unknown-project", "The requested project is not registered.")
        val room = rooms.getOrPut(projectId) { CollaborationRoom(projectId) }
        synchronized(room) {
            room.clients[socket] = ConnectedUser(user)
        }
        send(socket, nextEvent(room, "collaboration.snapshot", userId = user.id, data = snapshot(room)))
        publish(room, "presence.joined", userId = user.id)
        try {
            for (frame in socket.incoming) {
                if (frame is Frame.Text) handleClientEvent(room, socket, user.id, frame.data.decodeToString())
            }
        } finally {
            synchronized(room) { room.clients.remove(socket) }
            publish(room, "presence.left", userId = user.id)
            if (synchronized(room) { room.clients.isEmpty() }) rooms.remove(projectId, room)
        }
    }

    public suspend fun stagedChange(projectId: String, stagedChangeId: String? = null, proposalId: String? = null): Unit = publishByProject(
        projectId,
        "staged-change.updated",
        stagedChangeId = stagedChangeId,
        proposalId = proposalId,
    )

    public suspend fun proposal(projectId: String, eventType: String, proposalId: String? = null): Unit = publishByProject(
        projectId,
        eventType,
        proposalId = proposalId,
    )

    private suspend fun publishByProject(projectId: String, eventType: String, stagedChangeId: String? = null, proposalId: String? = null): Unit {
        val room = rooms[projectId] ?: return
        publish(room, eventType, stagedChangeId = stagedChangeId, proposalId = proposalId)
    }

    private suspend fun handleClientEvent(room: CollaborationRoom, socket: WebSocketServerSession, userId: String, raw: String): Unit {
        val node = try {
            objectMapper.readTree(raw)
        } catch (_: RuntimeException) {
            return
        }
        when (node.path("type").asText()) {
            "entity-opened" -> {
                val entityIri = node.path("entityIri").asText().takeIf(String::isNotBlank)
                synchronized(room) { room.clients[socket]?.activeEntityIri = entityIri }
                publish(room, "entity.activity", userId = userId, entityIri = entityIri)
            }
            "entity-closed" -> {
                synchronized(room) { room.clients[socket]?.activeEntityIri = null }
                publish(room, "entity.activity", userId = userId)
            }
            "stage-change", "proposal-apply" -> publish(
                room,
                "mutation.rejected",
                userId = userId,
                data = mapOf("reason" to "HTTP is authoritative for ontology mutations."),
            )
        }
    }

    private suspend fun publish(
        room: CollaborationRoom,
        eventType: String,
        userId: String? = null,
        entityIri: String? = null,
        stagedChangeId: String? = null,
        proposalId: String? = null,
        data: Map<String, Any?> = emptyMap(),
    ): Unit {
        val event = nextEvent(room, eventType, userId, entityIri, stagedChangeId, proposalId, data)
        val sockets = synchronized(room) { room.clients.keys.toList() }
        sockets.forEach { socket -> send(socket, event) }
    }

    private fun nextEvent(
        room: CollaborationRoom,
        eventType: String,
        userId: String? = null,
        entityIri: String? = null,
        stagedChangeId: String? = null,
        proposalId: String? = null,
        data: Map<String, Any?> = emptyMap(),
    ): WebCollaborationEvent = synchronized(room) {
        room.sequence += 1
        WebCollaborationEvent(
            eventId = UUID.randomUUID().toString(),
            projectId = room.projectId,
            collaborationSessionId = room.sessionId,
            sequence = room.sequence,
            eventType = eventType,
            timestamp = Instant.now().toString(),
            userId = userId,
            entityIri = entityIri,
            stagedChangeId = stagedChangeId,
            proposalId = proposalId,
            data = data,
        )
    }

    private fun snapshot(room: CollaborationRoom): Map<String, Any?> = synchronized(room) {
        mapOf(
            "users" to room.clients.values.map { connected -> connected.user.copy(activeEntityIri = connected.activeEntityIri) },
            "staged" to stagingSnapshot(room.projectId),
        )
    }

    private suspend fun send(socket: WebSocketServerSession, event: WebCollaborationEvent): Unit {
        socket.send(Frame.Text(objectMapper.writeValueAsString(event)))
    }
}
