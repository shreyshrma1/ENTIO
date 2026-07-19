package com.entio.web.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiOntologyNeighborhoodServiceTest {
    private val service = AiOntologyNeighborhoodService()

    @Test
    fun neighborhoodEnforcesPageEntityByteAndSourceBoundsWithProvenance(): Unit {
        val snapshot = retrievalSnapshot(100)
        val result = service.neighborhood(
            snapshot,
            AiNeighborhoodRequest(
                targetIri = snapshot.entities.first().iri,
                allowedSourceIds = setOf("core"),
                category = AiNeighborhoodCategory.CHILDREN,
                page = 1,
                pageSize = 10,
                maxEntities = 6,
                maxBytes = 4_000,
            ),
        )

        assertTrue(result.entities.size <= 6)
        assertTrue(result.entities.all { it.sourceId == "core" })
        assertTrue(result.approximateBytes <= 4_000)
        assertTrue(result.hasMore)
        assertTrue(result.entities.any(AiRetrievalEntity::asserted) || result.entities.any(AiRetrievalEntity::inferred))
        assertEquals(snapshot.reasoningFingerprint, result.reasoningFingerprint)
    }

    @Test
    fun layeredSearchPreservesSourcesAndStableLayerOrder(): Unit {
        val local = AiRetrievalEntity("https://example.com#Account", "Account", "CLASS", "core", listOf("A financial account"))
        val semantic = AiRetrievalEntity("https://example.com#BankAccount", "Bank account", "CLASS", "extension", listOf("Customer account"))
        val fibo = AiRetrievalEntity("https://fibo.example#Account", "Account", "CLASS", "fibo")
        val snapshot = retrievalSnapshot(0).copy(entities = listOf(semantic, local))

        val hits = service.search(snapshot, "Account", setOf("core", "extension"), listOf(fibo))

        assertEquals(listOf(AiSearchLayer.EXACT, AiSearchLayer.SEMANTIC, AiSearchLayer.FIBO), hits.map(AiLayeredSearchHit::layer))
        assertEquals(listOf("core", "extension", "fibo"), hits.map { it.entity.sourceId })
        assertFalse(hits.first().external)
        assertTrue(hits.last().external)
    }
}
