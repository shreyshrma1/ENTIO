package com.entio.web

import com.entio.core.DomainOperationKind
import com.entio.core.DomainReuseAction
import com.entio.core.EntioResult
import com.entio.core.ExternalEntityKind
import com.entio.semantic.DomainReuseProvenanceRepository
import com.entio.web.contract.DomainWebAssetPaths
import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebDomainRecommendationRequest
import com.entio.web.contract.WebDomainReuseStageRequest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DomainReuseStagingTest {
    @Test
    fun recommendationStagesPreviewsAppliesAndAppendsAtomicProvenance(): Unit = runBlocking {
        fixture().use { fixture ->
            val sourcePackageFingerprint = treeFingerprint(fixture.assets.fiboPackageRoot)
            fixture.activate()
            val recommendation = fixture.domain.recommend(
                "simple",
                "alice",
                WebDomainRecommendationRequest(
                    operationKind = DomainOperationKind.CreateClass,
                    requestedKind = ExternalEntityKind.Class,
                    draftLabel = "agreement",
                ),
            ).result.recommendations.first { it.iri.value.endsWith("/Agreement") }

            val staged = fixture.domain.stageRecommendation(
                "simple",
                "alice",
                recommendation.recommendationId,
                WebDomainReuseStageRequest(
                    action = DomainReuseAction.Reuse,
                    partialMaterializationAcknowledged = true,
                ),
                "reuse-agreement",
            )
            assertTrue(staged.entries.any { it.sourceId == "fibo-reuse" })
            assertTrue(staged.entries.any { it.normalizedValues["omittedSourceAxiomCount"] != "0" })

            val preview = fixture.staging.preview("simple", "alice")
            assertEquals("READYFORREVIEW", preview.status)
            fixture.staging.approve("simple", "reviewer")
            val applied = fixture.staging.apply("simple", "reviewer")

            assertEquals("APPLIED", applied.status)
            val managed = Files.readString(fixture.root.resolve("ontology/fibo-reuse.ttl"))
            assertTrue(managed.contains("Agreement"))
            assertFalse(managed.contains("owl:imports"))
            val provenance = assertIs<EntioResult.Success<List<com.entio.core.DomainReuseProvenanceEvent>>>(
                fixture.provenance.list(fixture.root),
            ).value
            assertEquals(1, provenance.size)
            assertEquals(recommendation.iri, provenance.single().canonicalIri)
            assertEquals("reviewer", provenance.single().actorId)
            assertEquals(sourcePackageFingerprint, treeFingerprint(fixture.assets.fiboPackageRoot))
        }
    }

    @Test
    fun continueLocallyAndMappingsDoNotMaterializeTheRecommendedTarget(): Unit = runBlocking {
        fixture().use { fixture ->
            fixture.activate()
            val recommendation = fixture.domain.recommend(
                "simple",
                "alice",
                WebDomainRecommendationRequest(
                    operationKind = DomainOperationKind.EditLabelOrDefinition,
                    requestedKind = ExternalEntityKind.Class,
                    draftLabel = "agreement",
                ),
            ).result.recommendations.first { it.iri.value.endsWith("/Agreement") }
            val unchanged = fixture.domain.stageRecommendation(
                "simple",
                "alice",
                recommendation.recommendationId,
                WebDomainReuseStageRequest(action = DomainReuseAction.ContinueLocally),
                "continue-locally",
            )
            assertTrue(unchanged.entries.isEmpty())

            val mapped = fixture.domain.stageRecommendation(
                "simple",
                "alice",
                recommendation.recommendationId,
                WebDomainReuseStageRequest(
                    action = DomainReuseAction.MapClose,
                    localIri = "https://example.com/entio/simple#Account",
                    localSourceId = "simple",
                ),
                "map-close",
            )
            assertEquals(1, mapped.entries.size)
            assertEquals("simple", mapped.entries.single().sourceId)
            assertTrue(mapped.entries.single().normalizedValues["domainReuseAction"] == "MapClose")
            assertFalse(Files.readString(fixture.root.resolve("ontology/fibo-reuse.ttl")).contains("Agreement"))
        }
    }

    private fun fixture(): Fixture {
        val source = Path.of("../examples/simple-ontology").toAbsolutePath().normalize()
        val allowed = Files.createTempDirectory("entio-domain-reuse-staging")
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
        val provenance = DomainReuseProvenanceRepository()
        staging.installDomainReuseApplyHooks(DomainReuseProvenanceCoordinator(registry, provenance))
        val assets = DomainWebAssetPaths.discover()
        val domain = DomainOntologyWebService(
            registry,
            staging,
            LoadedProjectCache(),
            assets,
            assetVerifier = {},
        )
        return Fixture(root, assets, staging, domain, provenance)
    }

    private fun treeFingerprint(root: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.walk(root).use { paths ->
            paths.filter(Files::isRegularFile).sorted().forEach { path ->
                digest.update(root.relativize(path).toString().toByteArray())
                digest.update(Files.readAllBytes(path))
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class Fixture(
        val root: Path,
        val assets: DomainWebAssetPaths,
        val staging: StagingWorkflowService,
        val domain: DomainOntologyWebService,
        val provenance: DomainReuseProvenanceRepository,
    ) : AutoCloseable {
        fun activate(): Unit {
            val preview = domain.previewActivation("simple", "alice")
            domain.activate("simple", "alice", preview.activationToken, "activate-${System.nanoTime()}")
        }

        override fun close(): Unit = domain.close()
    }
}
