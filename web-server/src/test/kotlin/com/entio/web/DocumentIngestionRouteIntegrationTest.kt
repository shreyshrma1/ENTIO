package com.entio.web

import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebApplicationDependencies
import com.entio.web.ingestion.DocumentIngestionConfiguration
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.delay

class DocumentIngestionRouteIntegrationTest {
    @Test
    fun intakeStartsProcessingAndTaskReadsResistCrossUserAndProjectEnumeration(): Unit = testApplication {
        val allowed = Files.createTempDirectory("entio-ingestion-route-projects")
        val project = Files.createDirectory(allowed.resolve("simple"))
        Files.createDirectories(project.resolve("ontology"))
        Files.writeString(
            project.resolve("entio.yaml"),
            """
            name: simple
            iriNamespace: https://example.com/simple#
            ontologySources:
              - id: simple
                path: ontology/simple.ttl
                format: turtle
            """.trimIndent(),
        )
        Files.writeString(
            project.resolve("ontology/simple.ttl"),
            """
            @prefix ex: <https://example.com/simple#> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            ex:Customer a owl:Class .
            """.trimIndent(),
        )
        val registry = InMemoryProjectRegistry(setOf(allowed)).also {
            it.register("simple", "Simple", project)
        }
        val temporary = Files.createTempDirectory("entio-ingestion-route-temporary")
        application {
            module(
                WebApplicationDependencies(projectRegistry = registry),
                DocumentIngestionConfiguration(
                    temporaryRoot = temporary,
                    provenanceRoot = Files.createTempDirectory("entio-ingestion-route-provenance"),
                    idFactory = sequenceOf("one", "two", "three").iterator()::next,
                ),
            )
        }

        val missingKey = client.post("/api/v1/projects/simple/document-ingestion/tasks") {
            header("X-Entio-User", "alice")
        }
        assertEquals(HttpStatusCode.BadRequest, missingKey.status)
        assertContains(missingKey.bodyAsText(), "missing-idempotency-key")

        val intake = client.submitFormWithBinaryData(
            "/api/v1/projects/simple/document-ingestion/tasks",
            formData {
                append(
                    "metadata",
                    """{"documents":[{"clientDocumentId":"client-1","filename":"policy.txt","declaredMediaType":"text/plain","language":"en","authorityStatus":"Authoritative"}]}""",
                )
                append(
                    "document.client-1",
                    "Supplier policy defines approved suppliers.".toByteArray(),
                    Headers.build {
                        append(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
                        append(HttpHeaders.ContentDisposition, ContentDisposition.File.withParameter("filename", "policy.txt").toString())
                    },
                )
            },
        ) {
            header("X-Entio-User", "alice")
            header("Idempotency-Key", "route-intake-1")
        }
        assertEquals(HttpStatusCode.Accepted, intake.status)
        val taskId = Regex(""""taskId":"([^"]+)"""").find(intake.bodyAsText())!!.groupValues[1]

        var ownerBody = ""
        var attempts = 0
        while ("blocked-for-model" !in ownerBody && attempts < 50) {
            val owner = client.get("/api/v1/projects/simple/document-ingestion/tasks/$taskId") {
                header("X-Entio-User", "alice")
            }
            ownerBody = owner.bodyAsText()
            attempts += 1
            if ("blocked-for-model" !in ownerBody) delay(10)
        }
        assertContains(ownerBody, "blocked-for-model")
        assertFalse(ownerBody.contains(project.toString()))
        assertFalse(ownerBody.contains(temporary.toString()))

        val otherUserList = client.get("/api/v1/projects/simple/document-ingestion/tasks") {
            header("X-Entio-User", "bob")
        }
        assertContains(otherUserList.bodyAsText(), """"items":[]""")
        val otherUserRead = client.get("/api/v1/projects/simple/document-ingestion/tasks/$taskId") {
            header("X-Entio-User", "bob")
        }
        assertEquals(HttpStatusCode.NotFound, otherUserRead.status)
        assertContains(otherUserRead.bodyAsText(), "ingestion-task-not-found")
        val otherProjectRead = client.get("/api/v1/projects/other/document-ingestion/tasks/$taskId") {
            header("X-Entio-User", "alice")
        }
        assertEquals(HttpStatusCode.NotFound, otherProjectRead.status)
        assertContains(otherProjectRead.bodyAsText(), "ingestion-task-not-found")

        val deleted = client.delete("/api/v1/projects/simple/document-ingestion/tasks/$taskId") {
            header("X-Entio-User", "alice")
        }
        assertEquals(HttpStatusCode.NoContent, deleted.status)
    }
}
