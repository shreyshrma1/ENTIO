package com.entio.web.ai

import com.entio.web.contract.WebAiTaskEvent
import java.time.Clock

/**
 * Process-memory task event log shared by the task API and the native execution bridge.
 *
 * Keeping the log server-owned means a provider can report observable milestones without being
 * able to manufacture task state or progress. The log is intentionally bounded and contains no
 * provider prompts, credentials, or hidden reasoning.
 */
public class AiTaskEventLog(
    private val clock: Clock = Clock.systemUTC(),
    private val maxEvents: Int = 200,
) {
    private val eventsByTask: MutableMap<String, MutableList<WebAiTaskEvent>> = linkedMapOf()

    @Synchronized
    public fun append(
        taskId: String,
        status: AiTaskStatus,
        type: String,
        message: String,
        referenceIds: List<String> = emptyList(),
    ): WebAiTaskEvent {
        require(taskId.isNotBlank())
        require(type.isNotBlank())
        require(message.isNotBlank())
        val current = eventsByTask.getOrPut(taskId) { mutableListOf() }
        val event = WebAiTaskEvent(
            sequence = (current.lastOrNull()?.sequence ?: 0) + 1,
            taskId = taskId,
            type = type,
            status = status.name,
            message = message,
            referenceIds = referenceIds.distinct().sorted(),
            createdAt = clock.instant().toString(),
        )
        current += event
        if (current.size > maxEvents) current.subList(0, current.size - maxEvents).clear()
        return event
    }

    @Synchronized
    public fun events(taskId: String): List<WebAiTaskEvent> = eventsByTask[taskId].orEmpty().toList()

    @Synchronized
    public fun clear(taskId: String): Unit {
        eventsByTask.remove(taskId)
    }
}
