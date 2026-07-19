package com.entio.web.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AiContextCacheTest {
    @Test
    fun cacheHitsAndFingerprintChangesMiss(): Unit {
        val cache = AiContextCache()
        var calls = 0
        val key = key(projectFingerprint = "project-1")

        val first = cache.getOrPut(key) { "value-${++calls}" }
        val hit = cache.getOrPut(key) { "value-${++calls}" }
        val invalidated = cache.getOrPut(key(projectFingerprint = "project-2")) { "value-${++calls}" }

        assertEquals(first, hit)
        assertNotEquals(first, invalidated)
        assertEquals(2, calls)
    }

    @Test
    fun privateDraftEntriesAreOwnerAndFingerprintIsolated(): Unit {
        val cache = AiContextCache()
        val alice = cache.getOrPut(key(ownerId = "alice", draftFingerprint = "draft-1")) { "alice" }
        val bob = cache.getOrPut(key(ownerId = "bob", draftFingerprint = "draft-1")) { "bob" }
        val newDraft = cache.getOrPut(key(ownerId = "alice", draftFingerprint = "draft-2")) { "alice-new" }

        assertEquals(listOf("alice", "bob", "alice-new"), listOf(alice, bob, newDraft))
        assertEquals(3, cache.size())
        assertEquals(3, cache.invalidateProject("large"))
        assertEquals(0, cache.size())
    }

    private fun key(
        ownerId: String? = null,
        projectFingerprint: String = "project-1",
        draftFingerprint: String? = null,
    ) = AiContextCacheKey(
        ownerId = ownerId,
        projectId = "large",
        projectFingerprint = projectFingerprint,
        draftFingerprint = draftFingerprint,
        resource = "map",
    )
}
