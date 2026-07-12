package com.entio.core

public data class ChangeProposal(
    public val id: String,
    public val title: String,
    public val targetSourceId: String,
    public val changeSet: ChangeSet,
    public val baseline: ProposalBaseline,
    public val status: ChangeProposalStatus,
    public val preview: ChangePreview? = null,
    public val diff: SemanticDiff? = null,
    public val validationReport: ValidationReport? = null,
    public val sourceFileImpact: SourceFileImpact? = null,
    public val review: ProposalReview? = null,
)

public enum class ChangeProposalStatus {
    Draft,
    Previewed,
    Verified,
    ReadyForReview,
    Rejected,
    Approved,
    Applied,
    VerificationFailed,
    Stale,
    ApplyFailed,
    RolledBack,
}

public data class ProposalReview(
    public val reviewer: String,
    public val note: String? = null,
)
