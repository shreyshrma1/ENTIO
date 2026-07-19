package com.entio.web.ai

public class AiProjectMapService(
    private val maxSources: Int = 50,
    private val maxTopLevelEntities: Int = 40,
    private val maxDomainTerms: Int = 30,
    private val maxConventionSamples: Int = 20,
) {
    public fun build(snapshot: AiProjectRetrievalSnapshot): AiProjectMap {
        require(snapshot.projectFingerprint.isNotBlank())
        val visibleSources = snapshot.sources.sortedBy(AiProjectSourceSummary::sourceId).take(maxSources)
        val visibleSourceIds = visibleSources.map(AiProjectSourceSummary::sourceId).toSet()
        val entities = snapshot.entities.filter { it.sourceId in visibleSourceIds }
            .sortedWith(compareBy(AiRetrievalEntity::label, AiRetrievalEntity::iri))
        val topLevel = entities.filter { it.parentIris.isEmpty() }.take(maxTopLevelEntities)
        val domainTerms = entities.asSequence().map(AiRetrievalEntity::label).map(::normalizedTerm)
            .filter(String::isNotBlank).distinct().sorted().take(maxDomainTerms).toList()
        return AiProjectMap(
            projectId = snapshot.projectId,
            projectFingerprint = snapshot.projectFingerprint,
            retrievalPolicyVersion = AI_RETRIEVAL_POLICY_VERSION,
            sources = visibleSources,
            entityCountsByKind = entities.groupingBy(AiRetrievalEntity::kind).eachCount().toSortedMap(),
            topLevelEntities = topLevel,
            domainTerms = domainTerms,
            externalOntologyIris = snapshot.externalOntologyIris.distinct().sorted().take(20),
            reasoningAvailable = snapshot.reasoningAvailable,
            shaclAvailable = snapshot.shaclAvailable,
            stagedChangeCount = snapshot.stagedChangeCount,
            namingSamples = entities.map(AiRetrievalEntity::label).distinct().take(maxConventionSamples),
            iriSamples = entities.map(AiRetrievalEntity::iri).distinct().take(maxConventionSamples),
            truncated = snapshot.sources.size > maxSources ||
                topLevel.size < entities.count { it.parentIris.isEmpty() } ||
                entities.size > maxConventionSamples ||
                domainTerms.size < entities.map(AiRetrievalEntity::label).map(::normalizedTerm).distinct().size,
        )
    }

    public fun analyze(snapshot: AiProjectRetrievalSnapshot, limit: Int = 100): AiProjectAnalysisSummary {
        require(limit in 1..100)
        val ordered = snapshot.entities.sortedBy(AiRetrievalEntity::iri)
        fun bounded(predicate: (AiRetrievalEntity) -> Boolean): List<String> = ordered.filter(predicate).map(AiRetrievalEntity::iri).take(limit)
        return AiProjectAnalysisSummary(
            unnamedEntityIris = bounded { it.label.isBlank() },
            undefinedEntityIris = bounded { it.definitions.isEmpty() },
            orphanClassIris = bounded { it.kind == "CLASS" && it.parentIris.isEmpty() && it.childIris.isEmpty() },
            stagedChangeCount = snapshot.stagedChangeCount,
            truncated = ordered.size > limit,
        )
    }

    private fun normalizedTerm(value: String): String = value.trim().lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
}
