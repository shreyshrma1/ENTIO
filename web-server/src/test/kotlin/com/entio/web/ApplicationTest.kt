package com.entio.web

import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebApplicationDependencies
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun readOnlyRoutesExposeSummaryHierarchySearchAndEntityRelationships(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-web-read-only")
        val projectRoot = createReadOnlyFixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)

        application {
            module(WebApplicationDependencies(projectRegistry = registry))
        }

        val summary = client.get("/api/v1/projects/simple/summary")
        val hierarchy = client.get("/api/v1/projects/simple/hierarchy?limit=1")
        val search = client.get("/api/v1/projects/simple/search?q=customer")
        val detail = client.get(
            "/api/v1/projects/simple/entities?iri=" +
                "https%3A%2F%2Fexample.com%2Fentio%2Fsimple%23Shrey",
        )

        assertEquals(HttpStatusCode.OK, summary.status)
        assertContains(summary.bodyAsText(), "graphTripleCount")
        assertContains(summary.bodyAsText(), "ontology/simple.ttl")
        assertEquals(HttpStatusCode.OK, hierarchy.status)
        assertContains(hierarchy.bodyAsText(), "Invoice")
        assertContains(hierarchy.bodyAsText(), "nextOffset")
        assertEquals(HttpStatusCode.OK, search.status)
        assertContains(search.bodyAsText(), "Customer")
        assertContains(search.bodyAsText(), "PreferredLabel")
        assertEquals(HttpStatusCode.OK, detail.status)
        assertContains(detail.bodyAsText(), "Shrey")
        assertContains(detail.bodyAsText(), "received invoice")
        assertContains(detail.bodyAsText(), "Invoice 20874")
    }

    @Test
    fun readOnlyRoutesReturnStructuredErrorsForMissingEntitiesAndInvalidQueries(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-web-read-only-errors")
        val projectRoot = createReadOnlyFixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)

        application {
            module(WebApplicationDependencies(projectRegistry = registry))
        }

        val missing = client.get("/api/v1/projects/simple/entities?iri=https%3A%2F%2Fexample.com%2Fmissing")
        val invalidSearch = client.get("/api/v1/projects/simple/search?q=")

        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertContains(missing.bodyAsText(), "missing-entity")
        assertEquals(HttpStatusCode.BadRequest, invalidSearch.status)
        assertContains(invalidSearch.bodyAsText(), "invalid-search-query")
    }

    private fun createReadOnlyFixture(allowedRoot: Path): Path {
        val projectRoot = Files.createDirectory(allowedRoot.resolve("simple"))
        val ontologyDirectory = Files.createDirectories(projectRoot.resolve("ontology"))
        Files.writeString(
            projectRoot.resolve("entio.yaml"),
            """
            name: simple-ontology
            ontologySources:
              - id: simple
                path: ontology/simple.ttl
                format: turtle
            """.trimIndent(),
        )
        Files.writeString(
            ontologyDirectory.resolve("simple.ttl"),
            """
            @prefix ex: <https://example.com/entio/simple#> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            ex:Party a owl:Class ; rdfs:label "Party" .
            ex:Customer a owl:Class ; rdfs:label "Customer" ; rdfs:subClassOf ex:Party .
            ex:Invoice a owl:Class ; rdfs:label "Invoice" .
            ex:receivedInvoice a owl:ObjectProperty ; rdfs:label "received invoice" ;
                rdfs:domain ex:Customer ; rdfs:range ex:Invoice .
            ex:Shrey a ex:Customer ; rdfs:label "Shrey" ; ex:receivedInvoice ex:Invoice20874 .
            ex:Invoice20874 a ex:Invoice ; rdfs:label "Invoice 20874" .
            """.trimIndent(),
        )
        assertTrue(Files.isRegularFile(ontologyDirectory.resolve("simple.ttl")))
        return projectRoot
    }
}
