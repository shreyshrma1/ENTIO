package com.entio.web.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiProjectMapServiceTest {
    @Test
    fun mapIsStableFingerprintBoundedAndIncludesApprovedSummaryFields(): Unit {
        val snapshot = retrievalSnapshot(100)
        val service = AiProjectMapService(maxTopLevelEntities = 12, maxDomainTerms = 8, maxConventionSamples = 5)

        val first = service.build(snapshot)
        val second = service.build(snapshot.copy(entities = snapshot.entities.reversed()))

        assertEquals(first, second)
        assertEquals("fingerprint-100", first.projectFingerprint)
        assertEquals(AI_RETRIEVAL_POLICY_VERSION, first.retrievalPolicyVersion)
        assertEquals(1, first.topLevelEntities.size)
        assertEquals(8, first.domainTerms.size)
        assertEquals(5, first.namingSamples.size)
        assertEquals(100, first.entityCountsByKind["CLASS"])
        assertTrue(first.reasoningAvailable)
        assertTrue(first.shaclAvailable)
        assertTrue(first.truncated)
    }

    @Test
    fun approvedProjectAnalysisChecksAreBounded(): Unit {
        val summary = AiProjectMapService().analyze(retrievalSnapshot(500), limit = 25)

        assertEquals(25, summary.undefinedEntityIris.size)
        assertTrue(summary.orphanClassIris.size <= 25)
        assertTrue(summary.truncated)
    }
}

internal fun retrievalSnapshot(count: Int): AiProjectRetrievalSnapshot {
    val entities = (0 until count).map { index ->
        AiRetrievalEntity(
            iri = "https://example.com/ontology#Entity%04d".format(index),
            label = "Entity %04d".format(index),
            kind = "CLASS",
            sourceId = if (index % 2 == 0) "core" else "extension",
            parentIris = if (index == 0) emptyList() else listOf("https://example.com/ontology#Entity0000"),
            childIris = if (index == 0) (1 until count).map { "https://example.com/ontology#Entity%04d".format(it) } else emptyList(),
            asserted = index % 3 != 0,
            inferred = index % 3 == 0,
        )
    }
    return AiProjectRetrievalSnapshot(
        projectId = "large",
        projectFingerprint = "fingerprint-$count",
        sources = listOf(
            AiProjectSourceSummary("core", "ontology", "https://example.com/ontology#", (count + 1) / 2),
            AiProjectSourceSummary("extension", "ontology", "https://example.com/extension#", count / 2),
        ),
        entities = entities,
        externalOntologyIris = listOf("https://spec.edmcouncil.org/fibo/ontology/BE/"),
        reasoningFingerprint = "reasoning-$count",
        shaclFingerprint = "shacl-$count",
        draftFingerprint = "draft-$count",
        reasoningAvailable = true,
        shaclAvailable = true,
        stagedChangeCount = 3,
    )
}
