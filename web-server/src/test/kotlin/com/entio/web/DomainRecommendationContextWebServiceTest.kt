package com.entio.web

import com.entio.core.DomainOperationKind
import com.entio.core.ExternalEntityKind
import com.entio.web.contract.DomainWebAssetPaths
import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebDomainRecommendationRequest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DomainRecommendationContextWebServiceTest {
    @Test
    fun contextualOperationsRequireTheirServerDefinedEntityKind(): Unit = runBlocking {
        fixture().use { fixture ->
            fixture.activate()

            val mismatch = assertFailsWith<DomainOntologyWebFailure> {
                fixture.service.recommend(
                    "simple",
                    "alice",
                    WebDomainRecommendationRequest(
                        operationKind = DomainOperationKind.CreateClass,
                        requestedKind = ExternalEntityKind.ObjectProperty,
                        draftLabel = "agreement",
                    ),
                )
            }
            val missing = assertFailsWith<DomainOntologyWebFailure> {
                fixture.service.recommend(
                    "simple",
                    "alice",
                    WebDomainRecommendationRequest(
                        operationKind = DomainOperationKind.EditLabelOrDefinition,
                        draftLabel = "customer",
                    ),
                )
            }

            assertEquals("domain-recommendation-kind-mismatch", mismatch.code)
            assertEquals("domain-recommendation-kind-required", missing.code)
            assertEquals(
                "domain-recommendation-context-kind-mismatch",
                assertFailsWith<DomainOntologyWebFailure> {
                    fixture.service.recommend(
                        "simple",
                        "alice",
                        WebDomainRecommendationRequest(
                            operationKind = DomainOperationKind.EditPropertyHierarchy,
                            requestedKind = ExternalEntityKind.ObjectProperty,
                            draftLabel = "account",
                            currentEntityIri = "https://example.com/entio/simple#Account",
                        ),
                    )
                }.code,
            )
        }
    }

    @Test
    fun semanticContextMustResolveInTheProjectWhileStandardDatatypesRemainValid(): Unit = runBlocking {
        fixture().use { fixture ->
            fixture.activate()
            val result = fixture.service.recommend(
                "simple",
                "alice",
                WebDomainRecommendationRequest(
                    operationKind = DomainOperationKind.AddAssertionOrValue,
                    requestedKind = ExternalEntityKind.DatatypeProperty,
                    draftLabel = "name",
                    currentEntityIri = "https://example.com/entio/simple#Account",
                    requiredDatatypeIri = "http://www.w3.org/2001/XMLSchema#string",
                    targetSourceId = "simple",
                ),
            ).result

            assertTrue(result.recommendations.all { it.kind == ExternalEntityKind.DatatypeProperty })
            assertEquals(
                "unknown-project-entity",
                assertFailsWith<DomainOntologyWebFailure> {
                    fixture.service.recommend(
                        "simple",
                        "alice",
                        WebDomainRecommendationRequest(
                            operationKind = DomainOperationKind.AddAssertionOrValue,
                            requestedKind = ExternalEntityKind.DatatypeProperty,
                            draftLabel = "name",
                            currentEntityIri = "https://example.com/not-in-project",
                            requiredDatatypeIri = "http://www.w3.org/2001/XMLSchema#string",
                        ),
                    )
                }.code,
            )
            assertEquals(
                "unknown-project-entity",
                assertFailsWith<DomainOntologyWebFailure> {
                    fixture.service.recommend(
                        "simple",
                        "alice",
                        WebDomainRecommendationRequest(
                            operationKind = DomainOperationKind.AddAssertionOrValue,
                            requestedKind = ExternalEntityKind.DatatypeProperty,
                            draftLabel = "name",
                            requiredDatatypeIri = "https://example.com/not-a-project-datatype",
                        ),
                    )
                }.code,
            )
        }
    }

    private fun fixture(): Fixture {
        val source = Path.of("../examples/simple-ontology").toAbsolutePath().normalize()
        val allowed = Files.createTempDirectory("entio-domain-recommendation-context")
        val root = allowed.resolve("simple")
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val target = root.resolve(source.relativize(path).toString())
                if (Files.isDirectory(path)) Files.createDirectories(target)
                else Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        val registry = InMemoryProjectRegistry(setOf(allowed))
        registry.register("simple", "Simple", root)
        val staging = StagingWorkflowService(registry)
        return Fixture(
            DomainOntologyWebService(
                registry,
                staging,
                LoadedProjectCache(),
                DomainWebAssetPaths.discover(),
                assetVerifier = {},
            ),
        )
    }

    private data class Fixture(val service: DomainOntologyWebService) : AutoCloseable {
        fun activate(): Unit {
            val preview = service.previewActivation("simple", "alice")
            service.activate("simple", "alice", preview.activationToken, "activate-context")
        }

        override fun close(): Unit = service.close()
    }
}
