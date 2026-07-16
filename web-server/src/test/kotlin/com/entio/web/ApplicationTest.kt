package com.entio.web

import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebApplicationDependencies
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ApplicationTest {
    @Test
    fun healthEndpointReportsThatTheServerIsAlive(): Unit = testApplication {
        application { module() }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
    }

    @Test
    fun readinessEndpointReportsThatTheBoundaryIsReady(): Unit = testApplication {
        application { module() }

        val response = client.get("/ready")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ready", response.bodyAsText())
    }

    @Test
    fun sessionEndpointReturnsDeterministicDevelopmentIdentity(): Unit = testApplication {
        application { module() }

        val response = client.get("/api/v1/session")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(body, "Alice Contributor")
        assertContains(body, "CONTRIBUTOR")
    }

    @Test
    fun projectRoutesExposeRegisteredDescriptorsWithoutFilesystemRoots(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-web-route-allowlist")
        val projectRoot = Files.createDirectory(allowedRoot.resolve("simple"))
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)

        application {
            module(WebApplicationDependencies(projectRegistry = registry))
        }

        val listResponse = client.get("/api/v1/projects")
        val listBody = listResponse.bodyAsText()
        val missingResponse = client.get("/api/v1/projects/missing")

        assertEquals(HttpStatusCode.OK, listResponse.status)
        assertContains(listBody, "Simple ontology")
        assertFalse(listBody.contains(projectRoot.toString()))
        assertEquals(HttpStatusCode.NotFound, missingResponse.status)
        assertContains(missingResponse.bodyAsText(), "unknown-project")
    }
}
