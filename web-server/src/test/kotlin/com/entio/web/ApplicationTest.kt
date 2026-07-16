package com.entio.web

import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebApplicationDependencies
import io.ktor.client.request.get
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.websocket.Frame
import io.ktor.websocket.send
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

    @Test
    fun stagingWorkflowKeepsDraftsPrivateUntilPreviewAndAppliesOnlyAfterReview(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-web-staging")
        val projectRoot = createReadOnlyFixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)

        application { module(WebApplicationDependencies(projectRegistry = registry)) }

        val stage = client.post("/api/v1/projects/simple/staged") {
            contentType(ContentType.Application.Json)
            setBody("""
                {"sourceId":"simple","editType":"create-class","classIri":"https://example.com/entio/simple#Account","label":"Account","idempotencyKey":"stage-account"}
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.OK, stage.status)
        assertContains(stage.bodyAsText(), "stage-1")
        assertContains(stage.bodyAsText(), "alice")

        val preview = client.post("/api/v1/projects/simple/proposal/preview")
        assertEquals(HttpStatusCode.OK, preview.status)
        assertContains(preview.bodyAsText(), "READYFORREVIEW")
        assertContains(preview.bodyAsText(), "Account")

        val contributorApproval = client.post("/api/v1/projects/simple/proposal/approve") {
            headers.append("X-Entio-User", "alice")
        }
        assertEquals(HttpStatusCode.Forbidden, contributorApproval.status)

        val approval = client.post("/api/v1/projects/simple/proposal/approve") {
            headers.append("X-Entio-User", "bob")
        }
        assertEquals(HttpStatusCode.OK, approval.status)
        assertContains(approval.bodyAsText(), "APPROVED")

        val applied = client.post("/api/v1/projects/simple/proposal/apply") {
            headers.append("X-Entio-User", "bob")
        }
        assertEquals(HttpStatusCode.OK, applied.status)
        assertContains(applied.bodyAsText(), "APPLIED")
        assertContains(Files.readString(projectRoot.resolve("ontology/simple.ttl")), "Account")
    }

    @Test
    fun rejectingAProposalLeavesItsStagedEntriesForCorrection(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-web-reject")
        val projectRoot = createReadOnlyFixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)

        application { module(WebApplicationDependencies(projectRegistry = registry)) }

        client.post("/api/v1/projects/simple/staged") {
            contentType(ContentType.Application.Json)
            setBody("""{"sourceId":"simple","editType":"create-class","classIri":"https://example.com/entio/simple#Account","label":"Account"}""")
        }
        assertEquals(HttpStatusCode.OK, client.post("/api/v1/projects/simple/proposal/preview").status)
        val rejected = client.post("/api/v1/projects/simple/proposal/reject") {
            headers.append("X-Entio-User", "bob")
        }
        assertEquals(HttpStatusCode.OK, rejected.status)
        assertContains(rejected.bodyAsText(), "stage-1")
        assertContains(rejected.bodyAsText(), "READY")
        assertFalse(Files.readString(projectRoot.resolve("ontology/simple.ttl")).contains("Account"))
    }

    @Test
    fun collaborationClientsReceiveOrderedPresenceActivityAndMutationEvents(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-web-collaboration")
        val projectRoot = createReadOnlyFixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)

        application { module(WebApplicationDependencies(projectRegistry = registry)) }
        val clientA = createClient { install(WebSockets) }
        val clientB = createClient { install(WebSockets) }

        clientA.webSocket("/api/v1/projects/simple/collaboration?userId=alice") {
            val snapshotA = nextEvent(incoming.receive())
            val joinedA = nextEvent(incoming.receive())
            val incomingA = incoming
            assertEquals("collaboration.snapshot", snapshotA["eventType"])
            assertEquals("presence.joined", joinedA["eventType"])
            assertEquals(2, joinedA["sequence"])

            clientB.webSocket("/api/v1/projects/simple/collaboration?userId=bob") {
                val snapshotB = nextEvent(incoming.receive())
                val joinedB = nextEvent(incoming.receive())
                assertEquals("collaboration.snapshot", snapshotB["eventType"])
                assertEquals("presence.joined", joinedB["eventType"])
                assertEquals(3, snapshotB["sequence"])
                assertEquals(4, joinedB["sequence"])

                val joinedForA = nextEvent(incomingA.receive())
                assertEquals("presence.joined", joinedForA["eventType"])

                send(Frame.Text("""{"type":"entity-opened","entityIri":"https://example.com/Customer"}"""))
                val activityB = nextEvent(incoming.receive())
                val activityA = nextEvent(incomingA.receive())
                assertEquals("entity.activity", activityB["eventType"])
                assertEquals("https://example.com/Customer", activityB["entityIri"])
                assertEquals(activityB["sequence"], activityA["sequence"])

                send(Frame.Text("""{"type":"stage-change"}"""))
                assertEquals("mutation.rejected", nextEvent(incoming.receive())["eventType"])
                assertEquals("mutation.rejected", nextEvent(incomingA.receive())["eventType"])
            }

            val leftA = nextEvent(incomingA.receive())
            assertEquals("presence.left", leftA["eventType"])
        }
    }

    @Test
    fun staleProposalApplicationReturnsAnExplicitConflictState(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-web-stale")
        val projectRoot = createReadOnlyFixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)

        application { module(WebApplicationDependencies(projectRegistry = registry)) }
        client.post("/api/v1/projects/simple/staged") {
            contentType(ContentType.Application.Json)
            setBody("""{"sourceId":"simple","editType":"create-class","classIri":"https://example.com/entio/simple#Account","label":"Account"}""")
        }
        assertEquals(HttpStatusCode.OK, client.post("/api/v1/projects/simple/proposal/preview").status)
        assertEquals(HttpStatusCode.OK, client.post("/api/v1/projects/simple/proposal/approve") { headers.append("X-Entio-User", "bob") }.status)
        Files.writeString(projectRoot.resolve("ontology/simple.ttl"), Files.readString(projectRoot.resolve("ontology/simple.ttl")) + "\n")

        val applied = client.post("/api/v1/projects/simple/proposal/apply") { headers.append("X-Entio-User", "bob") }
        assertEquals(HttpStatusCode.OK, applied.status)
        assertContains(applied.bodyAsText(), "APPLYFAILED")
        assertContains(applied.bodyAsText(), "stale")
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

    private fun nextEvent(frame: Frame): Map<String, Any?> {
        require(frame is Frame.Text)
        val text = frame.data.decodeToString()
        return com.fasterxml.jackson.databind.ObjectMapper().readValue(text, Map::class.java) as Map<String, Any?>
    }
}
