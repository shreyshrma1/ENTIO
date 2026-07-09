package com.entio.core

public data class ChangeProposal(
    public val id: String,
    public val title: String,
    public val status: ChangeProposalStatus,
    public val diff: SemanticDiff,
)

public enum class ChangeProposalStatus {
    Draft,
    Approved,
    Rejected,
}
