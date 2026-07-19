package com.entio.web.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiTaskStateMachineTest {
    private val stateMachine = AiTaskStateMachine()

    @Test
    fun transitionTableAcceptsExactlyItsDeclaredTransitions(): Unit {
        AiTaskStatus.entries.forEach { from ->
            AiTaskStatus.entries.forEach { to ->
                val expected = from == to || to in stateMachine.allowedFrom(from)
                assertEquals(expected, stateMachine.canTransition(from, to), "$from -> $to")
                if (expected) {
                    stateMachine.requireTransition(from, to)
                } else {
                    val failure = assertFailsWith<AiTaskTransitionFailure> {
                        stateMachine.requireTransition(from, to)
                    }
                    assertEquals("invalid-ai-task-transition", failure.code)
                }
            }
        }
    }

    @Test
    fun terminalStatesHaveNoOutgoingTransitions(): Unit {
        listOf(
            AiTaskStatus.SUBMITTED_FOR_REVIEW,
            AiTaskStatus.FAILED,
            AiTaskStatus.CANCELLED,
        ).forEach { status ->
            assertTrue(stateMachine.allowedFrom(status).isEmpty())
            assertTrue(stateMachine.canTransition(status, status))
        }
    }

    @Test
    fun workspaceMutationRejectsTerminalStaleAndLimitStates(): Unit {
        AiTaskStatus.entries.forEach { status ->
            if (status.allowsWorkspaceMutation) {
                stateMachine.requireWorkspaceMutation(status)
            } else {
                val failure = assertFailsWith<AiTaskTransitionFailure> {
                    stateMachine.requireWorkspaceMutation(status)
                }
                assertEquals("ai-task-mutation-not-allowed", failure.code)
            }
        }
        assertFalse(AiTaskStatus.STALE.allowsWorkspaceMutation)
    }
}
