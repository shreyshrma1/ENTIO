package com.entio.web

import com.entio.web.contract.DomainWebAssetPaths
import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebApplicationDependencies
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
import java.nio.file.StandardCopyOption
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class DomainOntologyDomainRecommendationWebContractTest {
    @Test
    fun routesAuthorizePreviewActivateReadAndRecommendWithoutExposingPaths(): Unit = testApplication {
        val fixture = copyFixture()
        val registry = InMemoryProjectRegistry(setOf(fixture.parent))
        registry.register("simple", "Simple", fixture)
        application {
            module(
                WebApplicationDependencies(
                    projectRegistry = registry,
                    domainAssets = DomainWebAssetPaths.discover(),
                    domainAssetVerifier = {},
                ),
            )
        }

        val unknownUser = client.get("/api/v1/domain-ontologies") { header("X-Entio-User", "missing") }
        assertEquals(HttpStatusCode.Unauthorized, unknownUser.status)

        val catalog = client.get("/api/v1/domain-ontologies")
        assertEquals(HttpStatusCode.OK, catalog.status)
        assertContains(catalog.bodyAsText(), "Financial Industry Business Ontology")

        val inactiveRecommendation = client.post("/api/v1/projects/simple/domain-recommendations") {
            contentType(ContentType.Application.Json)
            setBody("""{"operationKind":"CreateClass","requestedKind":"Class","draftLabel":"agreement"}""")
        }
        assertEquals(HttpStatusCode.Conflict, inactiveRecommendation.status)
        assertContains(inactiveRecommendation.bodyAsText(), "domain-profile-inactive")

        val preview = client.post("/api/v1/projects/simple/domain-ontology/activation-preview")
        val previewBody = preview.bodyAsText()
        val token = Regex("\\\"activationToken\\\":\\\"([^\\\"]+)\\\"").find(previewBody)?.groupValues?.get(1)
        assertEquals(HttpStatusCode.OK, preview.status)
        assertNotNull(token)
        assertFalse(Files.exists(fixture.resolve(".entio/domain-profile.yaml")))

        val activate = client.post("/api/v1/projects/simple/domain-ontology/activate") {
            header("Idempotency-Key", "route-activation")
            contentType(ContentType.Application.Json)
            setBody("""{"confirmationToken":"$token"}""")
        }
        assertEquals(HttpStatusCode.OK, activate.status)
        assertContains(activate.bodyAsText(), "Active")

        val foundation = client.get("/api/v1/projects/simple/domain-ontology/foundation")
        assertEquals(HttpStatusCode.OK, foundation.status)
        assertContains(foundation.bodyAsText(), "Agents and organizations")

        val recommendation = client.post("/api/v1/projects/simple/domain-recommendations") {
            contentType(ContentType.Application.Json)
            setBody("""{"operationKind":"CreateClass","requestedKind":"Class","draftLabel":"agreement"}""")
        }
        val recommendationBody = recommendation.bodyAsText()
        assertEquals(HttpStatusCode.OK, recommendation.status)
        assertContains(recommendationBody, "Agreement")
        assertFalse(recommendationBody.contains(fixture.toString()))

        val missing = client.get("/api/v1/projects/missing/domain-ontology")
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertFalse(missing.bodyAsText().contains(fixture.toString()))
    }

    private fun copyFixture(): Path {
        val source = Path.of("../examples/simple-ontology").toAbsolutePath().normalize()
        val root = Files.createTempDirectory("entio-domain-route").resolve("simple")
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val target = root.resolve(source.relativize(path).toString())
                if (Files.isDirectory(path)) Files.createDirectories(target)
                else Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        return root
    }
}
