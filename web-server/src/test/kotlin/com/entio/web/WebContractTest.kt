package com.entio.web

import com.entio.web.contract.DevelopmentAuthorization
import com.entio.web.contract.DevelopmentIdentityProvider
import com.entio.web.contract.IdempotencyDecision
import com.entio.web.contract.InMemoryIdempotencyStore
import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebAction
import com.entio.web.contract.WebPageRequest
import com.entio.web.contract.WebPermission
import com.entio.web.contract.WebRole
import com.entio.web.contract.toWebPage
import com.entio.web.contract.encodeWebIri
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebContractTest {
    @Test
    fun projectRegistryExposesOnlyAllowlistedRegisteredProjects(): Unit {
        val allowedRoot = Files.createTempDirectory("entio-web-allowlist")
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        val projectRoot = Files.createDirectory(allowedRoot.resolve("simple"))

        registry.register("simple", "Simple ontology", projectRoot)

        assertEquals(listOf("simple"), registry.list().map { it.id })
        assertEquals(projectRoot.toAbsolutePath().normalize(), registry.rootFor("simple"))
        assertFailsWith<RuntimeException> {
            registry.register("outside", "Outside", Files.createTempDirectory("entio-outside"))
        }
    }

    @Test
    fun developmentRolesExposeReviewerOnlyPermissions(): Unit {
        val authorization = DevelopmentAuthorization()

        assertTrue(authorization.isAllowed(WebRole.CONTRIBUTOR, WebAction.STAGE_OWN_CHANGE))
        assertFalse(authorization.isAllowed(WebRole.CONTRIBUTOR, WebAction.APPLY))
        assertTrue(authorization.isAllowed(WebRole.REVIEWER, WebAction.APPLY))
        assertTrue(WebPermission.APPLY in authorization.permissionsFor(WebRole.REVIEWER))
        assertEquals("alice", DevelopmentIdentityProvider().find(null)?.id)
    }

    @Test
    fun idempotencyRejectsSameKeyWithDifferentPayload(): Unit {
        val store = InMemoryIdempotencyStore()

        assertEquals(IdempotencyDecision.Accepted("request-1"), store.begin("request-1", "hash-a"))
        assertEquals(IdempotencyDecision.Replay("request-1"), store.begin("request-1", "hash-a"))
        assertEquals(IdempotencyDecision.Conflict("request-1"), store.begin("request-1", "hash-b"))
    }

    @Test
    fun pagesHaveStableBoundsAndContinuation(): Unit {
        val page = (1..5).toList().toWebPage(WebPageRequest(offset = 2, limit = 2))

        assertEquals(listOf(3, 4), page.items)
        assertEquals(5, page.total)
        assertEquals(4, page.nextOffset)
        assertFailsWith<IllegalArgumentException> { WebPageRequest(limit = 101) }
    }

    @Test
    fun irisAreEncodedForPathSegments(): Unit {
        assertEquals(
            "https%3A%2F%2Fexample.com%2Fentio%2Fsimple%23Customer",
            encodeWebIri("https://example.com/entio/simple#Customer"),
        )
    }
}
