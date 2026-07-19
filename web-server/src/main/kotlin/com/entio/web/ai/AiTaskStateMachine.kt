package com.entio.web.ai

public class AiTaskTransitionFailure(
    public val code: String,
    message: String,
) : IllegalArgumentException(message)

/** Enforces task lifecycle transitions independently of provider or browser claims. */
public class AiTaskStateMachine {
    public fun canTransition(from: AiTaskStatus, to: AiTaskStatus): Boolean =
        from == to || to in allowedTransitions.getValue(from)

    public fun requireTransition(from: AiTaskStatus, to: AiTaskStatus): Unit {
        if (!canTransition(from, to)) {
            throw AiTaskTransitionFailure(
                "invalid-ai-task-transition",
                "An AI task cannot transition from $from to $to.",
            )
        }
    }

    public fun requireWorkspaceMutation(status: AiTaskStatus): Unit {
        if (!status.allowsWorkspaceMutation) {
            throw AiTaskTransitionFailure(
                "ai-task-mutation-not-allowed",
                "The AI task cannot mutate its workspace while it is $status.",
            )
        }
    }

    public fun allowedFrom(status: AiTaskStatus): Set<AiTaskStatus> = allowedTransitions.getValue(status)

    private companion object {
        val activeFailureTransitions: Set<AiTaskStatus> = setOf(
            AiTaskStatus.PAUSED,
            AiTaskStatus.FAILED,
            AiTaskStatus.CANCELLED,
            AiTaskStatus.STALE,
            AiTaskStatus.LIMIT_REACHED,
        )

        val allowedTransitions: Map<AiTaskStatus, Set<AiTaskStatus>> = mapOf(
            AiTaskStatus.UNDERSTANDING to setOf(
                AiTaskStatus.AWAITING_CLARIFICATION,
                AiTaskStatus.PLANNING,
                AiTaskStatus.READY_TO_EXECUTE,
            ) + activeFailureTransitions,
            AiTaskStatus.AWAITING_CLARIFICATION to setOf(
                AiTaskStatus.UNDERSTANDING,
                AiTaskStatus.PLANNING,
                AiTaskStatus.READY_TO_EXECUTE,
                AiTaskStatus.EXECUTING,
                AiTaskStatus.REPAIRING,
            ) + activeFailureTransitions,
            AiTaskStatus.PLANNING to setOf(
                AiTaskStatus.AWAITING_CLARIFICATION,
                AiTaskStatus.AWAITING_PLAN_CONFIRMATION,
                AiTaskStatus.READY_TO_EXECUTE,
            ) + activeFailureTransitions,
            AiTaskStatus.AWAITING_PLAN_CONFIRMATION to setOf(
                AiTaskStatus.PLANNING,
                AiTaskStatus.READY_TO_EXECUTE,
            ) + activeFailureTransitions,
            AiTaskStatus.READY_TO_EXECUTE to setOf(AiTaskStatus.EXECUTING) + activeFailureTransitions,
            AiTaskStatus.EXECUTING to setOf(
                AiTaskStatus.AWAITING_CLARIFICATION,
                AiTaskStatus.PLANNING,
                AiTaskStatus.VALIDATING,
                AiTaskStatus.READY_FOR_REVIEW,
            ) + activeFailureTransitions,
            AiTaskStatus.VALIDATING to setOf(
                AiTaskStatus.EXECUTING,
                AiTaskStatus.RUNNING_REASONING,
                AiTaskStatus.RUNNING_SHACL,
                AiTaskStatus.REPAIRING,
                AiTaskStatus.READY_FOR_REVIEW,
            ) + activeFailureTransitions,
            AiTaskStatus.RUNNING_REASONING to setOf(
                AiTaskStatus.RUNNING_SHACL,
                AiTaskStatus.REPAIRING,
                AiTaskStatus.READY_FOR_REVIEW,
            ) + activeFailureTransitions,
            AiTaskStatus.RUNNING_SHACL to setOf(
                AiTaskStatus.REPAIRING,
                AiTaskStatus.READY_FOR_REVIEW,
            ) + activeFailureTransitions,
            AiTaskStatus.REPAIRING to setOf(
                AiTaskStatus.AWAITING_CLARIFICATION,
                AiTaskStatus.VALIDATING,
            ) + activeFailureTransitions,
            AiTaskStatus.PAUSED to setOf(
                AiTaskStatus.UNDERSTANDING,
                AiTaskStatus.PLANNING,
                AiTaskStatus.AWAITING_PLAN_CONFIRMATION,
                AiTaskStatus.READY_TO_EXECUTE,
                AiTaskStatus.EXECUTING,
                AiTaskStatus.VALIDATING,
                AiTaskStatus.RUNNING_REASONING,
                AiTaskStatus.RUNNING_SHACL,
                AiTaskStatus.REPAIRING,
                AiTaskStatus.READY_FOR_REVIEW,
                AiTaskStatus.CANCELLED,
                AiTaskStatus.STALE,
                AiTaskStatus.LIMIT_REACHED,
            ),
            AiTaskStatus.READY_FOR_REVIEW to setOf(
                AiTaskStatus.SUBMITTED_FOR_REVIEW,
                AiTaskStatus.EXECUTING,
                AiTaskStatus.STALE,
                AiTaskStatus.CANCELLED,
            ),
            AiTaskStatus.STALE to setOf(
                AiTaskStatus.UNDERSTANDING,
                AiTaskStatus.VALIDATING,
                AiTaskStatus.CANCELLED,
                AiTaskStatus.FAILED,
            ),
            AiTaskStatus.LIMIT_REACHED to setOf(AiTaskStatus.CANCELLED),
            AiTaskStatus.SUBMITTED_FOR_REVIEW to emptySet(),
            AiTaskStatus.FAILED to emptySet(),
            AiTaskStatus.CANCELLED to emptySet(),
        )
    }
}
