package com.entio.core

public data class ProposalBaseline(
    public val projectFingerprint: String,
    public val targetSourceId: String,
    public val targetSourcePath: String,
    public val targetSourceFingerprint: String,
    public val graphFingerprint: String,
)

public data class SourceFileImpact(
    public val affectedPaths: List<String>,
)
