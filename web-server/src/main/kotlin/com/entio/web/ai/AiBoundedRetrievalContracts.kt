package com.entio.web.ai

public const val AI_RETRIEVAL_POLICY_VERSION: String = "phase-8-retrieval-v1"

public data class AiProjectSourceSummary(
    val sourceId: String,
    val role: String,
    val namespace: String,
    val entityCount: Int,
)

public data class AiRetrievalEntity(
    val iri: String,
    val label: String,
    val kind: String,
    val sourceId: String,
    val definitions: List<String> = emptyList(),
    val parentIris: List<String> = emptyList(),
    val childIris: List<String> = emptyList(),
    val propertyIris: List<String> = emptyList(),
    val domainIris: List<String> = emptyList(),
    val rangeIris: List<String> = emptyList(),
    val individualIris: List<String> = emptyList(),
    val asserted: Boolean = true,
    val inferred: Boolean = false,
    val shapeReferences: List<String> = emptyList(),
    val findingReferences: List<String> = emptyList(),
    val usageReferences: List<String> = emptyList(),
    val stagedImpactReferences: List<String> = emptyList(),
)

public data class AiProjectRetrievalSnapshot(
    val projectId: String,
    val projectFingerprint: String,
    val sources: List<AiProjectSourceSummary>,
    val entities: List<AiRetrievalEntity>,
    val externalOntologyIris: List<String> = emptyList(),
    val reasoningFingerprint: String? = null,
    val shaclFingerprint: String? = null,
    val draftFingerprint: String? = null,
    val reasoningAvailable: Boolean = false,
    val shaclAvailable: Boolean = false,
    val stagedChangeCount: Int = 0,
)

public data class AiProjectMap(
    val projectId: String,
    val projectFingerprint: String,
    val retrievalPolicyVersion: String,
    val sources: List<AiProjectSourceSummary>,
    val entityCountsByKind: Map<String, Int>,
    val topLevelEntities: List<AiRetrievalEntity>,
    val domainTerms: List<String>,
    val externalOntologyIris: List<String>,
    val reasoningAvailable: Boolean,
    val shaclAvailable: Boolean,
    val stagedChangeCount: Int,
    val namingSamples: List<String>,
    val iriSamples: List<String>,
    val truncated: Boolean,
)

public enum class AiNeighborhoodCategory {
    PARENTS,
    CHILDREN,
    PROPERTIES,
    DOMAINS,
    RANGES,
    INDIVIDUALS,
    SHAPES,
    FINDINGS,
    USAGE,
    STAGED_IMPACT,
}

public data class AiNeighborhoodRequest(
    val targetIri: String,
    val allowedSourceIds: Set<String>,
    val category: AiNeighborhoodCategory? = null,
    val page: Int = 0,
    val pageSize: Int = 20,
    val maxEntities: Int = 50,
    val maxBytes: Int = 32_000,
)

public data class AiOntologyNeighborhood(
    val target: AiRetrievalEntity,
    val entities: List<AiRetrievalEntity>,
    val category: AiNeighborhoodCategory?,
    val page: Int,
    val pageSize: Int,
    val totalCandidates: Int,
    val hasMore: Boolean,
    val approximateBytes: Int,
    val projectFingerprint: String,
    val reasoningFingerprint: String?,
    val shaclFingerprint: String?,
    val draftFingerprint: String?,
)

public enum class AiSearchLayer { EXACT, NORMALIZED, SEMANTIC, FIBO }

public data class AiLayeredSearchHit(
    val entity: AiRetrievalEntity,
    val layer: AiSearchLayer,
    val external: Boolean = false,
)

public data class AiProjectAnalysisSummary(
    val unnamedEntityIris: List<String>,
    val undefinedEntityIris: List<String>,
    val orphanClassIris: List<String>,
    val stagedChangeCount: Int,
    val truncated: Boolean,
)
