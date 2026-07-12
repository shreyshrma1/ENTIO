package com.entio.core

public sealed interface SemanticEquivalenceResult {
    public data object Equivalent : SemanticEquivalenceResult

    public data class NotEquivalent(
        public val reason: String,
    ) : SemanticEquivalenceResult

    public data class Failed(
        public val reason: String,
    ) : SemanticEquivalenceResult
}

public sealed interface ApplyProposalResult {
    public data class Applied(
        public val proposalId: String,
        public val changedFiles: List<String>,
    ) : ApplyProposalResult

    public data class Failed(
        public val proposalId: String,
        public val reason: String,
        public val rollback: RollbackResult,
    ) : ApplyProposalResult
}

public sealed interface RollbackResult {
    public data object NotRequired : RollbackResult

    public data class Restored(
        public val restoredFiles: List<String>,
    ) : RollbackResult

    public data class Failed(
        public val reason: String,
    ) : RollbackResult
}
