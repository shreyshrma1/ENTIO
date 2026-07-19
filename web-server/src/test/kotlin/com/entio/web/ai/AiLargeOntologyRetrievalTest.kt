package com.entio.web.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiLargeOntologyRetrievalTest {
    private val maps = AiProjectMapService()
    private val neighborhoods = AiOntologyNeighborhoodService()

    @Test
    fun generatedFiveHundredAndOneThousandEntityProjectsStayBounded(): Unit {
        listOf(500, 1_000).forEach { count ->
            val snapshot = retrievalSnapshot(count)
            val map = maps.build(snapshot)
            val neighborhood = neighborhoods.neighborhood(
                snapshot,
                AiNeighborhoodRequest(
                    targetIri = snapshot.entities.first().iri,
                    allowedSourceIds = setOf("core", "extension"),
                    category = AiNeighborhoodCategory.CHILDREN,
                    pageSize = 50,
                    maxEntities = 50,
                    maxBytes = 16_000,
                ),
            )

            assertTrue(map.topLevelEntities.size <= 40)
            assertTrue(map.namingSamples.size <= 20)
            assertTrue(neighborhood.entities.size <= 50)
            assertTrue(neighborhood.approximateBytes <= 16_000)
            assertEquals(count - 1, neighborhood.totalCandidates)
            assertFalse(neighborhood.entities.any { it.label.contains("@prefix") || it.definitions.any { definition -> definition.contains("@prefix") } })
        }
    }
}
