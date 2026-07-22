package com.entio.web

import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebApplicationDependencies
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OntologyGraphWebContractTest {
    @Test
    fun graphRouteSerializesBoundedReadContractsWithoutPathOrSourceContent(): Unit = testApplication {
        val registry = registry()
        application { module(WebApplicationDependencies(projectRegistry = registry)) }

        val response = client.get("/api/v1/projects/simple/graph?sourceId=simple")
        val body = response.bodyAsText()
        val json = ObjectMapper().readTree(body)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("v1", json.path("apiVersion").asText())
        assertEquals(75, json.path("limits").path("nodeLimit").asInt())
        assertEquals(150, json.path("limits").path("edgeLimit").asInt())
        assertTrue(json.path("nodes").isArray)
        assertTrue(json.path("edges").isArray)
        assertTrue(json.path("graphFingerprint").asText().matches(Regex("[0-9a-f]{64}")))
        assertFalse(body.contains("ontology/simple.ttl"))
        assertFalse(body.contains("@prefix"))
        assertFalse(body.contains(projectRoot().toString()))
        json.path("nodes").forEach { node ->
            assertTrue(node.path("identity").path("id").asText().matches(Regex("[0-9a-f]{64}")))
            assertTrue(node.has("summary"))
        }
    }

    @Test
    fun graphRoutesEnforceIdentityScopeEntityAndFingerprint(): Unit = testApplication {
        val registry = registry()
        application { module(WebApplicationDependencies(projectRegistry = registry)) }

        val initial = client.get("/api/v1/projects/simple/graph?sourceId=simple")
        val fingerprint = ObjectMapper().readTree(initial.bodyAsText()).path("graphFingerprint").asText()
        val neighborhood = client.get(
            "/api/v1/projects/simple/graph/neighborhood?sourceId=simple&entitySourceId=simple" +
                "&entityIri=https%3A%2F%2Fexample.com%2Fentio%2Fsimple%23Invoice" +
                "&category=ClassHierarchy&expectedFingerprint=$fingerprint",
        )
        val unknownUser = client.get("/api/v1/projects/simple/graph") { header("X-Entio-User", "mallory") }
        val invalidSource = client.get("/api/v1/projects/simple/graph?sourceId=external")
        val missingEntity = client.get(
            "/api/v1/projects/simple/graph?sourceId=simple&seedSourceId=simple" +
                "&seedIri=https%3A%2F%2Fexample.com%2Fmissing",
        )
        val stale = client.get("/api/v1/projects/simple/graph?sourceId=simple&expectedFingerprint=stale")
        val lostContinuation = client.get("/api/v1/projects/simple/graph?sourceId=simple&continuation=process-lost")

        assertEquals(HttpStatusCode.OK, neighborhood.status)
        assertTrue(neighborhood.bodyAsText().contains("Neighborhood"))
        assertEquals(HttpStatusCode.Unauthorized, unknownUser.status)
        assertTrue(unknownUser.bodyAsText().contains("unknown-development-user"))
        assertEquals(HttpStatusCode.BadRequest, invalidSource.status)
        assertEquals(HttpStatusCode.NotFound, missingEntity.status)
        assertEquals(HttpStatusCode.Conflict, stale.status)
        assertEquals(HttpStatusCode.BadRequest, lostContinuation.status)
        assertTrue(lostContinuation.bodyAsText().contains("invalid-graph-continuation"))
        assertNotEquals("stale", fingerprint)
    }

    private fun registry(): InMemoryProjectRegistry = InMemoryProjectRegistry(setOf(projectRoot().parent)).also {
        it.register("simple", "Simple ontology", projectRoot())
    }

    private fun projectRoot(): Path = Path.of("../examples/simple-ontology").toAbsolutePath().normalize()
}
