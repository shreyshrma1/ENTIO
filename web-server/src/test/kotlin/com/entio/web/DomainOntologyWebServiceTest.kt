package com.entio.web

import com.entio.core.DomainOntologyAvailability
import com.entio.core.DomainOperationKind
import com.entio.core.ExternalEntityKind
import com.entio.web.contract.DomainWebAssetPaths
import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebDomainFoundationPlanRequest
import com.entio.web.contract.WebDomainRecommendationRequest
import com.entio.web.contract.WebStageChangeRequest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

class DomainOntologyWebServiceTest {
    @Test
    fun activationIsPreviewedTokenBoundIdempotentAndLeavesEntioYamlUnchanged(): Unit {
        fixture().use { fixture ->
            val entioBefore = Files.readAllBytes(fixture.root.resolve("entio.yaml"))
            val preview = fixture.service.previewActivation("simple", "alice")

            assertFalse(Files.exists(fixture.root.resolve(".entio/domain-profile.yaml")))
            val activated = fixture.service.activate(
                "simple",
                "alice",
                preview.activationToken,
                "activate-one",
            )
            val replay = fixture.service.activate(
                "simple",
                "alice",
                preview.activationToken,
                "activate-one",
            )

            assertEquals(DomainOntologyAvailability.Active, activated.status.availability)
            assertEquals(activated, replay)
            assertContentEquals(entioBefore, Files.readAllBytes(fixture.root.resolve("entio.yaml")))
            assertEquals(
                com.entio.semantic.DomainProfileService.EMPTY_MANAGED_SOURCE,
                Files.readString(fixture.root.resolve("ontology/fibo-reuse.ttl")),
            )
        }
    }

    @Test
    fun staleActivationTokenAndWrongUserFailClosedWithoutMutation(): Unit {
        fixture().use { fixture ->
            val wrongUser = fixture.service.previewActivation("simple", "alice")
            assertEquals(
                "domain-activation-stale",
                assertFailsWith<DomainOntologyWebFailure> {
                    fixture.service.activate("simple", "bob", wrongUser.activationToken, "wrong-user")
                }.code,
            )
            val stale = fixture.service.previewActivation("simple", "alice")
            Files.writeString(fixture.root.resolve("entio.yaml"), Files.readString(fixture.root.resolve("entio.yaml")) + "\n")

            assertEquals(
                "domain-activation-stale",
                assertFailsWith<DomainOntologyWebFailure> {
                    fixture.service.activate("simple", "alice", stale.activationToken, "stale")
                }.code,
            )
            assertFalse(Files.exists(fixture.root.resolve(".entio/domain-profile.yaml")))
        }
    }

    @Test
    fun foundationPlansAreServerIssuedBoundedAndProjectUserOwned(): Unit {
        fixture().use { fixture ->
            fixture.activate()
            val groups = fixture.service.foundations("simple").groups
            val plan = fixture.service.planFoundation(
                "simple",
                "alice",
                WebDomainFoundationPlanRequest(selectAll = true),
            ).plan

            assertEquals(8, groups.size)
            assertTrue(groups.flatMap { it.members }.all { it.elementId.matches(Regex("dfe_[0-9a-f]{40}")) })
            assertTrue(plan.batches.all { it.explicitSelectionCount <= 20 && it.items.size <= 100 })
            assertEquals(plan, fixture.service.foundationPlan("simple", "alice", plan.planId).plan)
            assertFailsWith<com.entio.semantic.DomainRecommendationStaleException> {
                fixture.service.foundationPlan("simple", "bob", plan.planId)
            }
        }
    }

    @Test
    fun recommendationsRequireActiveProfileAndRejectCrossUserOrUnknownContext(): Unit = runBlocking {
        fixture().use { fixture ->
            val request = WebDomainRecommendationRequest(
                operationKind = DomainOperationKind.CreateClass,
                requestedKind = ExternalEntityKind.Class,
                draftLabel = "agreement",
            )
            assertEquals(
                "domain-profile-inactive",
                assertFailsWith<DomainOntologyWebFailure> {
                    fixture.service.recommend("simple", "alice", request)
                }.code,
            )
            fixture.activate()
            val result = fixture.service.recommend("simple", "alice", request).result
            val recommendation = result.recommendations.first()

            assertTrue(recommendation.iri.value.endsWith("/Agreement"))
            assertEquals(
                recommendation,
                fixture.service.recommendation("simple", "alice", recommendation.recommendationId).recommendation,
            )
            assertFailsWith<com.entio.semantic.DomainRecommendationStaleException> {
                fixture.service.recommendation("simple", "bob", recommendation.recommendationId)
            }
            assertEquals(
                "unknown-project-entity",
                assertFailsWith<DomainOntologyWebFailure> {
                    fixture.service.recommend(
                        "simple",
                        "alice",
                        request.copy(requiredParentIri = "https://example.com/not-in-project"),
                    )
                }.code,
            )
        }
    }

    @Test
    fun recommendationExecutionIsBoundedConcurrentAndCancellable(): Unit = runBlocking {
        fixture().use { fixture ->
            fixture.activate()
            val requests = (1..8).map {
                async {
                    fixture.service.recommend(
                        "simple",
                        "alice",
                        WebDomainRecommendationRequest(
                            operationKind = DomainOperationKind.CreateClass,
                            requestedKind = ExternalEntityKind.Class,
                            draftLabel = "agreement",
                        ),
                    ).result.recommendations.first().iri
                }
            }.awaitAll()

            assertEquals(1, requests.distinct().size)
        }
        fixture(recommendationTimeoutMillis = 0).use { fixture ->
            fixture.activate()
            val failure = assertFailsWith<DomainOntologyWebFailure> {
                fixture.service.recommend(
                    "simple",
                    "alice",
                    WebDomainRecommendationRequest(
                        operationKind = DomainOperationKind.CreateClass,
                        requestedKind = ExternalEntityKind.Class,
                        draftLabel = "agreement",
                    ),
                )
            }

            assertEquals("domain-recommendation-timeout", failure.code)
        }
    }

    @Test
    fun emptyProfileDeactivatesWhileOpenWorkBlocksDeactivationPreview(): Unit {
        fixture().use { fixture ->
            fixture.activate()
            val preview = fixture.service.previewDeactivation("simple", "alice")
            assertTrue(preview.preview.eligible)
            val deactivated = fixture.service.deactivate(
                "simple",
                "alice",
                requireNotNull(preview.deactivationToken),
                "deactivate-one",
            )

            assertEquals(DomainOntologyAvailability.Inactive, deactivated.status.availability)
            assertFalse(Files.exists(fixture.root.resolve(".entio/domain-profile.yaml")))
            assertFalse(Files.exists(fixture.root.resolve("ontology/fibo-reuse.ttl")))
        }
    }

    @Test
    fun stagedProjectWorkBlocksDeactivationWithoutChangingProfileFiles(): Unit {
        fixture().use { fixture ->
            fixture.activate()
            fixture.staging.stage(
                "simple",
                WebStageChangeRequest(
                    sourceId = "simple",
                    editType = "set-entity-label",
                    resourceIri = "https://example.com/entio/simple#Customer",
                    label = "Client",
                ),
                "alice",
            )

            val preview = fixture.service.previewDeactivation("simple", "alice")

            assertFalse(preview.preview.eligible)
            assertTrue(com.entio.core.DomainProfileDeactivationBlocker.StagedDependencyExists in preview.preview.blockers)
            assertTrue(Files.exists(fixture.root.resolve(".entio/domain-profile.yaml")))
        }
    }

    @Test
    fun migrationPreviewIsReadOnlyAndAssetFailuresAreRedacted(): Unit {
        fixture().use { fixture ->
            val preview = fixture.service.migration("simple", preview = true)

            assertEquals(com.entio.core.DomainOntologyMigrationStatus.ExistingReuseRecognized, preview.status)
            assertTrue(preview.recognizedIriCount > 0)
            assertFalse(preview.mutatesProject)
            assertFalse(Files.exists(fixture.root.resolve(".entio/domain-profile.yaml")))
        }
        fixture(assetVerifier = { error("sensitive /private/model/path") }).use { fixture ->
            val failure = assertFailsWith<DomainOntologyWebFailure> {
                fixture.service.previewActivation("simple", "alice")
            }

            assertEquals("domain-assets-unavailable", failure.code)
            assertFalse(requireNotNull(failure.message).contains("/private/model/path"))
        }
    }

    private fun fixture(
        assetVerifier: () -> Unit = {},
        recommendationTimeoutMillis: Long = 10_000,
    ): Fixture {
        val source = Path.of("../examples/simple-ontology").toAbsolutePath().normalize()
        val allowed = Files.createTempDirectory("entio-domain-web-service")
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
        val service = DomainOntologyWebService(
            registry,
            staging,
            LoadedProjectCache(),
            DomainWebAssetPaths.discover(),
            assetVerifier = assetVerifier,
            recommendationTimeoutMillis = recommendationTimeoutMillis,
        )
        return Fixture(root, staging, service)
    }

    private data class Fixture(
        val root: Path,
        val staging: StagingWorkflowService,
        val service: DomainOntologyWebService,
    ) : AutoCloseable {
        fun activate(): Unit {
            val preview = service.previewActivation("simple", "alice")
            service.activate("simple", "alice", preview.activationToken, "activate-${System.nanoTime()}")
        }

        override fun close(): Unit = service.close()
    }
}
