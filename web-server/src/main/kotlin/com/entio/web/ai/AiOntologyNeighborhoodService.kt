package com.entio.web.ai

public class AiRetrievalFailure(public val code: String, message: String) : IllegalArgumentException(message)

public class AiOntologyNeighborhoodService {
    public fun neighborhood(
        snapshot: AiProjectRetrievalSnapshot,
        request: AiNeighborhoodRequest,
    ): AiOntologyNeighborhood {
        require(request.page >= 0)
        require(request.pageSize in 1..50)
        require(request.maxEntities in 1..50)
        require(request.maxBytes in 1..64_000)
        val visible = snapshot.entities.filter { it.sourceId in request.allowedSourceIds }.associateBy(AiRetrievalEntity::iri)
        val target = visible[request.targetIri]
            ?: throw AiRetrievalFailure("missing-retrieval-target", "The requested entity is not available in task scope.")
        val references = references(target, request.category)
        val candidates = references.asSequence().distinct().mapNotNull(visible::get)
            .sortedWith(compareBy(AiRetrievalEntity::label, AiRetrievalEntity::iri)).toList()
        val offset = request.page * request.pageSize
        val page = candidates.drop(offset).take(minOf(request.pageSize, request.maxEntities))
        val bounded = mutableListOf<AiRetrievalEntity>()
        var bytes = estimate(target)
        for (entity in page) {
            val next = estimate(entity)
            if (bounded.size >= request.maxEntities || bytes + next > request.maxBytes) break
            bounded += entity
            bytes += next
        }
        return AiOntologyNeighborhood(
            target = target,
            entities = bounded,
            category = request.category,
            page = request.page,
            pageSize = request.pageSize,
            totalCandidates = candidates.size,
            hasMore = offset + bounded.size < candidates.size,
            approximateBytes = bytes,
            projectFingerprint = snapshot.projectFingerprint,
            reasoningFingerprint = snapshot.reasoningFingerprint,
            shaclFingerprint = snapshot.shaclFingerprint,
            draftFingerprint = snapshot.draftFingerprint,
        )
    }

    public fun search(
        snapshot: AiProjectRetrievalSnapshot,
        query: String,
        allowedSourceIds: Set<String>,
        fiboCandidates: List<AiRetrievalEntity> = emptyList(),
        limit: Int = 20,
    ): List<AiLayeredSearchHit> {
        require(limit in 1..50)
        val normalized = normalize(query)
        val local = snapshot.entities.filter { it.sourceId in allowedSourceIds }
        fun layer(entity: AiRetrievalEntity): AiSearchLayer? = when {
            entity.label == query || entity.iri == query -> AiSearchLayer.EXACT
            normalize(entity.label) == normalized -> AiSearchLayer.NORMALIZED
            normalize(entity.label).contains(normalized) || entity.definitions.any { normalize(it).contains(normalized) } -> AiSearchLayer.SEMANTIC
            else -> null
        }
        val localHits = local.mapNotNull { entity -> layer(entity)?.let { AiLayeredSearchHit(entity, it) } }
        val fiboHits = fiboCandidates.take(10).map { AiLayeredSearchHit(it, AiSearchLayer.FIBO, external = true) }
        return (localHits + fiboHits).distinctBy { listOf(it.entity.sourceId, it.entity.iri) }
            .sortedWith(compareBy({ it.layer.ordinal }, { it.entity.label }, { it.entity.iri })).take(limit)
    }

    private fun references(entity: AiRetrievalEntity, category: AiNeighborhoodCategory?): List<String> = when (category) {
        AiNeighborhoodCategory.PARENTS -> entity.parentIris
        AiNeighborhoodCategory.CHILDREN -> entity.childIris
        AiNeighborhoodCategory.PROPERTIES -> entity.propertyIris
        AiNeighborhoodCategory.DOMAINS -> entity.domainIris
        AiNeighborhoodCategory.RANGES -> entity.rangeIris
        AiNeighborhoodCategory.INDIVIDUALS -> entity.individualIris
        AiNeighborhoodCategory.SHAPES -> entity.shapeReferences
        AiNeighborhoodCategory.FINDINGS -> entity.findingReferences
        AiNeighborhoodCategory.USAGE -> entity.usageReferences
        AiNeighborhoodCategory.STAGED_IMPACT -> entity.stagedImpactReferences
        null -> entity.parentIris + entity.childIris + entity.propertyIris + entity.domainIris + entity.rangeIris +
            entity.individualIris + entity.shapeReferences + entity.findingReferences + entity.usageReferences +
            entity.stagedImpactReferences
    }

    private fun estimate(entity: AiRetrievalEntity): Int = (
        entity.iri.length + entity.label.length + entity.kind.length + entity.sourceId.length +
            entity.definitions.sumOf(String::length) + 64
    ) * 2

    private fun normalize(value: String): String = value.trim().lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
}
