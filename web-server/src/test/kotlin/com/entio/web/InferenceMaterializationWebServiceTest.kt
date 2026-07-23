package com.entio.web

import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebInferenceMaterializationRequest
import com.entio.web.contract.WebInferenceMaterializationSelection
import com.entio.web.contract.WebApplicationDependencies
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async

class InferenceMaterializationWebServiceTest {
    @Test
    fun materializationRouteAcceptsFactIdsOnlyAndEnforcesJobOwner(): Unit = testApplication {
        val fixture = fixture()
        application { module(WebApplicationDependencies(projectRegistry = fixture.registry)) }
        val before = fixture.source.readBytes()
        val submitted = client.post("/api/v1/projects/reasoning/semantic-jobs") {
            header("X-Entio-User", "alice")
            contentType(ContentType.Application.Json)
            setBody("""{"kind":"reasoning","scope":"applied"}""")
        }
        assertEquals(HttpStatusCode.OK, submitted.status, submitted.bodyAsText())
        val jobId = ObjectMapper().readTree(submitted.bodyAsText()).path("id").asText()

        var completed = false
        repeat(200) {
            val status = client.get("/api/v1/projects/reasoning/semantic-jobs/$jobId")
            if (ObjectMapper().readTree(status.bodyAsText()).path("status").asText() == "Completed") {
                completed = true
                return@repeat
            }
            Thread.sleep(25)
        }
        assertTrue(completed)
        val details = client.get("/api/v1/projects/reasoning/semantic-jobs/$jobId/details") {
            header("X-Entio-User", "alice")
        }
        val candidates = ObjectMapper().readTree(details.bodyAsText()).path("materializationCandidates")
        val factId = candidates.first { it.path("stageability").asText() == "Stageable" }.path("factId").asText()
        val materialized = client.post("/api/v1/projects/reasoning/semantic-jobs/$jobId/materializations") {
            header("X-Entio-User", "alice")
            contentType(ContentType.Application.Json)
            setBody("""{"selections":[{"factId":"$factId"}],"idempotencyKey":"route-stage"}""")
        }

        assertEquals(HttpStatusCode.OK, materialized.status, materialized.bodyAsText())
        assertTrue(materialized.bodyAsText().contains("\"mappings\""))
        assertEquals(before.toList(), fixture.source.readBytes().toList())

        val crossUser = client.post("/api/v1/projects/reasoning/semantic-jobs/$jobId/materializations") {
            header("X-Entio-User", "bob")
            contentType(ContentType.Application.Json)
            setBody("""{"selections":[{"factId":"$factId"}],"idempotencyKey":"cross-user"}""")
        }
        assertEquals(HttpStatusCode.NotFound, crossUser.status)
        assertTrue(crossUser.bodyAsText().contains("unknown-semantic-job"))
    }

    @Test
    fun exposesOwnerBoundCandidatesAndStagesAfterFreshSemanticKeyMatch(): Unit = runBlocking {
        val fixture = fixture()
        val staging = StagingWorkflowService(fixture.registry)
        val jobs = SemanticJobManager(staging, fixture.registry)
        val service = InferenceMaterializationWebService(jobs, staging, fixture.registry)
        val submitted = jobs.submit("reasoning", WebJobRequest(), "alice")
        val completed = awaitCompletion(jobs, submitted.id)

        assertEquals(WebSemanticJobState.Completed, completed.status)
        val aliceDetails = assertNotNull(jobs.details("reasoning", submitted.id, 100, "alice"))
        assertTrue(aliceDetails.materializationCandidates.isNotEmpty())
        assertTrue(aliceDetails.materializationCandidates.any { it.kind == "SubclassRelationship" })
        assertEquals(emptyList(), jobs.details("reasoning", submitted.id, 100, "bob")?.materializationCandidates)

        val selected = aliceDetails.materializationCandidates.first { it.stageability == "Stageable" }
        val before = fixture.source.readBytes()
        val response = service.materialize(
            "reasoning",
            submitted.id,
            "alice",
            WebInferenceMaterializationRequest(
                selections = listOf(WebInferenceMaterializationSelection(selected.factId)),
                idempotencyKey = "stage-one",
            ),
        )

        assertEquals(1, response.mappings.size)
        assertEquals(1, response.staging.entries.size)
        assertEquals(before.toList(), fixture.source.readBytes().toList())
        val refreshed = assertNotNull(jobs.details("reasoning", submitted.id, 100, "alice"))
        assertTrue(refreshed.materializationCandidates.any {
            it.factId == selected.factId &&
                it.stageability == "AlreadyStaged" &&
                it.existingStagedChangeId == response.mappings.single().stagedChangeId
        })
    }

    @Test
    fun rejectsCrossUserTamperedAndStaleRequestsWithoutChangingStaging(): Unit = runBlocking {
        val fixture = fixture()
        val staging = StagingWorkflowService(fixture.registry)
        val jobs = SemanticJobManager(staging, fixture.registry)
        val service = InferenceMaterializationWebService(jobs, staging, fixture.registry)
        val job = jobs.submit("reasoning", WebJobRequest(), "alice")
        awaitCompletion(jobs, job.id)
        val factId = assertNotNull(jobs.details("reasoning", job.id, 100, "alice"))
            .materializationCandidates.first { it.stageability == "Stageable" }.factId

        assertEquals(
            "unknown-semantic-job",
            assertFailsWith<WebWorkflowFailure> {
                service.materialize(
                    "reasoning",
                    job.id,
                    "bob",
                    WebInferenceMaterializationRequest(
                        listOf(WebInferenceMaterializationSelection(factId)),
                        "cross-user",
                    ),
                )
            }.code,
        )
        assertEquals(
            "invalid-materialization-request",
            assertFailsWith<WebWorkflowFailure> {
                service.materialize(
                    "reasoning",
                    job.id,
                    "alice",
                    WebInferenceMaterializationRequest(
                        listOf(WebInferenceMaterializationSelection("tampered")),
                        "tampered",
                    ),
                )
            }.code,
        )
        assertEquals(
            "invalid-source-selection",
            assertFailsWith<WebWorkflowFailure> {
                service.materialize(
                    "reasoning",
                    job.id,
                    "alice",
                    WebInferenceMaterializationRequest(
                        listOf(WebInferenceMaterializationSelection(factId, "other-project-source")),
                        "invalid-source",
                    ),
                )
            }.code,
        )
        assertEquals(0, staging.snapshot("reasoning").entries.size)

        Files.writeString(fixture.source, Files.readString(fixture.source) + "\nex:NewClass a owl:Class .\n")
        assertEquals(
            "stale-semantic-job",
            assertFailsWith<WebWorkflowFailure> {
                service.materialize(
                    "reasoning",
                    job.id,
                    "alice",
                    WebInferenceMaterializationRequest(
                        listOf(WebInferenceMaterializationSelection(factId)),
                        "stale",
                    ),
                )
            }.code,
        )
        assertEquals(0, staging.snapshot("reasoning").entries.size)
    }

    @Test
    fun rejectsIncompleteScopeAndBoundViolations(): Unit = runBlocking {
        val fixture = fixture()
        val staging = StagingWorkflowService(fixture.registry)
        val jobs = SemanticJobManager(staging, fixture.registry)
        val service = InferenceMaterializationWebService(jobs, staging, fixture.registry)
        val proposalFailure = assertFailsWith<WebWorkflowFailure> {
            jobs.submit("reasoning", WebJobRequest(scope = "proposal"), "alice")
        }
        assertEquals("missing-proposal", proposalFailure.code)

        val job = jobs.submit("reasoning", WebJobRequest(), "alice")
        awaitCompletion(jobs, job.id)
        val factId = assertNotNull(jobs.details("reasoning", job.id, 100, "alice"))
            .materializationCandidates.first().factId
        assertEquals(
            "invalid-materialization-request",
            assertFailsWith<WebWorkflowFailure> {
                service.materialize(
                    "reasoning",
                    job.id,
                    "alice",
                    WebInferenceMaterializationRequest(
                        List(101) { WebInferenceMaterializationSelection(factId) },
                        "oversized",
                    ),
                )
            }.code,
        )
        assertEquals(0, staging.snapshot("reasoning").entries.size)

        val shacl = jobs.submit("reasoning", WebJobRequest(kind = "shacl"), "alice")
        awaitCompletion(jobs, shacl.id)
        assertEquals(
            "semantic-job-not-materializable",
            assertFailsWith<WebWorkflowFailure> {
                service.materialize(
                    "reasoning",
                    shacl.id,
                    "alice",
                    WebInferenceMaterializationRequest(
                        listOf(WebInferenceMaterializationSelection(factId)),
                        "shacl",
                    ),
                )
            }.code,
        )

        val cancelled = jobs.submit("reasoning", WebJobRequest(), "alice")
        jobs.cancel("reasoning", cancelled.id)
        assertEquals(
            "semantic-job-not-materializable",
            assertFailsWith<WebWorkflowFailure> {
                service.materialize(
                    "reasoning",
                    cancelled.id,
                    "alice",
                    WebInferenceMaterializationRequest(
                        listOf(WebInferenceMaterializationSelection(factId)),
                        "cancelled",
                    ),
                )
            }.code,
        )
    }

    @Test
    fun timeoutAndConcurrentRequestLeaveSharedStateSafe(): Unit = runBlocking {
        val fixture = fixture()
        val staging = StagingWorkflowService(fixture.registry)
        val jobs = SemanticJobManager(staging, fixture.registry)
        val job = jobs.submit("reasoning", WebJobRequest(), "alice")
        awaitCompletion(jobs, job.id)
        val factId = assertNotNull(jobs.details("reasoning", job.id, 100, "alice"))
            .materializationCandidates.first { it.stageability == "Stageable" }.factId
        val request = WebInferenceMaterializationRequest(
            listOf(WebInferenceMaterializationSelection(factId)),
            "bounded",
        )
        val timed = InferenceMaterializationWebService(
            jobs = jobs,
            staging = staging,
            projectRegistry = fixture.registry,
            timeoutMillis = 1,
        )
        assertEquals(
            "materialization-timeout",
            assertFailsWith<WebWorkflowFailure> {
                timed.materialize("reasoning", job.id, "alice", request)
            }.code,
        )
        assertEquals(0, staging.snapshot("reasoning").entries.size)

        val service = InferenceMaterializationWebService(jobs, staging, fixture.registry)
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            service.materialize("reasoning", job.id, "alice", request.copy(idempotencyKey = "first"))
        }
        val concurrent = assertFailsWith<WebWorkflowFailure> {
            service.materialize("reasoning", job.id, "alice", request.copy(idempotencyKey = "second"))
        }
        assertEquals("materialization-in-progress", concurrent.code)
        assertEquals(1, first.await().staging.entries.size)
    }

    private fun awaitCompletion(
        jobs: SemanticJobManager,
        jobId: String,
    ): WebSemanticJobStatus {
        repeat(200) {
            val status = assertNotNull(jobs.find("reasoning", jobId))
            if (status.status !in setOf(WebSemanticJobState.Queued, WebSemanticJobState.Running)) return status
            Thread.sleep(25)
        }
        error("Reasoning job did not finish.")
    }

    private fun fixture(): Fixture {
        val allowed = Files.createTempDirectory("entio-phase10-web")
        val root = Files.createDirectory(allowed.resolve("reasoning"))
        Files.createDirectories(root.resolve("ontology"))
        Files.writeString(
            root.resolve("entio.yaml"),
            """
            name: reasoning
            ontologySources:
              - id: local
                path: ontology/reasoning.ttl
                format: turtle
                roles:
                  - ontology
                  - data
            """.trimIndent(),
        )
        val source = root.resolve("ontology/reasoning.ttl")
        Files.writeString(
            source,
            """
            @prefix ex: <https://example.com/reasoning#> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            ex:A a owl:Class ; rdfs:subClassOf ex:B .
            ex:B a owl:Class ; rdfs:subClassOf ex:C .
            ex:C a owl:Class .
            ex:x a owl:NamedIndividual, ex:A .
            ex:y a owl:NamedIndividual .
            ex:z a owl:NamedIndividual .
            ex:related a owl:ObjectProperty, owl:TransitiveProperty .
            ex:x ex:related ex:y .
            ex:y ex:related ex:z .
            """.trimIndent(),
        )
        val registry = InMemoryProjectRegistry(setOf(allowed))
        registry.register("reasoning", "Reasoning", root)
        return Fixture(registry, source)
    }

    private data class Fixture(
        val registry: InMemoryProjectRegistry,
        val source: Path,
    )
}
