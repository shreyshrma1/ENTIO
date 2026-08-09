package com.entio.web

import com.entio.core.DomainOntologyMigrationStatus
import com.entio.web.contract.DomainWebAssetPaths
import com.entio.web.contract.InMemoryProjectRegistry
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.appendText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainMigrationWebTest {
    @Test
    fun `web adapter exposes recognized read-only migration evidence and activation preview`(): Unit {
        fixture().use { fixture ->
            val status = fixture.service.status("simple")
            val report = fixture.service.migration("simple")
            val preview = fixture.service.migration("simple", preview = true)

            assertEquals(DomainOntologyMigrationStatus.ExistingReuseRecognized, status.status.migrationStatus)
            assertEquals(DomainOntologyMigrationStatus.ExistingReuseRecognized, report.status)
            assertTrue(report.detectedIris.isNotEmpty())
            assertTrue(report.recognizedIriCount > 0)
            assertTrue(report.unsupportedIris.isEmpty())
            assertEquals("master_2026Q2", report.verifiedCurrentRelease)
            assertNull(report.historicalRelease)
            assertFalse(report.provenanceSeedingEligible)
            assertTrue(preview.activationPreview != null)
            assertTrue(preview.requiresNormalProposalForStatementMovement)
            assertFalse(preview.mutatesProject)
            assertFalse(Files.exists(fixture.root.resolve(".entio/domain-profile.yaml")))
        }
    }

    @Test
    fun `web adapter reports mixed current and unknown identities as ambiguous without previewing activation`(): Unit {
        fixture("<https://spec.edmcouncil.org/fibo/ontology/LEGACY/UnknownConcept> a <http://www.w3.org/2002/07/owl#Class> .")
            .use { fixture ->
                val report = fixture.service.migration("simple")

                assertEquals(DomainOntologyMigrationStatus.ExistingReuseAmbiguous, report.status)
                assertEquals(1, report.unsupportedIris.size)
                assertNull(report.verifiedCurrentRelease)
                assertNull(report.historicalRelease)
                assertFalse(Files.exists(fixture.root.resolve(".entio/domain-profile.yaml")))
            }
    }

    private fun fixture(extraStatement: String? = null): Fixture {
        val source = Path.of("../examples/simple-ontology").toAbsolutePath().normalize()
        val allowed = Files.createTempDirectory("entio-domain-migration-web")
        val root = allowed.resolve("simple")
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val target = root.resolve(source.relativize(path).toString())
                if (Files.isDirectory(path)) Files.createDirectories(target)
                else Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        extraStatement?.let { root.resolve("ontology/simple.ttl").appendText("\n$it\n") }
        val registry = InMemoryProjectRegistry(setOf(allowed))
        registry.register("simple", "Simple", root)
        val staging = StagingWorkflowService(registry)
        return Fixture(
            root,
            DomainOntologyWebService(
                registry,
                staging,
                LoadedProjectCache(),
                DomainWebAssetPaths.discover(),
                assetVerifier = {},
            ),
        )
    }

    private data class Fixture(val root: Path, val service: DomainOntologyWebService) : AutoCloseable {
        override fun close(): Unit = service.close()
    }
}
