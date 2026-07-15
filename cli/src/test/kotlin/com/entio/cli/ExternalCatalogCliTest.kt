package com.entio.cli

import com.entio.core.EntioResult
import com.entio.semantic.FiboCatalogLoader
import com.entio.semantic.ExternalFiboCatalogSession
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalCatalogCliTest {
    private val projectRoot: Path = repoRoot().resolve("examples/simple-ontology")

    @Test
    fun sourceAndManifestCommandsReturnReadOnlyPinnedPackageMetadata(): Unit {
        val sources = runCli("external-sources")
        assertEquals(0, sources.exitCode, sources.out)
        assertTrue(sources.out.contains("\"command\":\"external-sources\""), sources.out)
        assertTrue(sources.out.contains("\"id\":\"fibo\""), sources.out)
        assertTrue(sources.out.contains("\"projectChanged\":false"), sources.out)

        val manifest = runCli("external-manifest", projectRoot.toString())
        assertEquals(0, manifest.exitCode, manifest.out)
        assertTrue(manifest.out.contains("\"release\":\"master_2026Q2\""), manifest.out)
        assertTrue(manifest.out.contains("\"catalogSchema\":\"fibo-catalog-v1\""), manifest.out)
        assertTrue(manifest.out.contains("\"availability\":\"available\""), manifest.out)
    }

    @Test
    fun browseSearchAndDescriptorCommandsUseKotlinOwnedCatalogServices(): Unit {
        val session = loadSession()
        val module = session.browseCuratedModules(pageSize = 1).items.single()
        val element = session.browseModule(module.ontologyIri, pageSize = 1).items.single()
        val entityIri = element.descriptor.descriptor.common.entity.value

        val browse = runCli("external-browse", projectRoot.toString(), "--mode", "curated", "--page-size", "1")
        assertEquals(0, browse.exitCode, browse.out)
        assertTrue(browse.out.contains("\"totalCount\":15"), browse.out)
        assertTrue(browse.out.contains("\"noSilentTruncation\":true"), browse.out)

        val descriptor = runCli("external-describe", projectRoot.toString(), entityIri, "--kind", element.kind.name)
        assertEquals(0, descriptor.exitCode, descriptor.out)
        assertTrue(descriptor.out.contains("\"locality\":\"External\""), descriptor.out)
        assertTrue(descriptor.out.contains(entityIri), descriptor.out)

        val search = runCli("external-search", projectRoot.toString(), "agreement", "--page-size", "2")
        assertEquals(0, search.exitCode, search.out)
        assertTrue(search.out.contains("\"schema\":\"fibo-schema-search-v1\""), search.out)
        assertTrue(search.out.contains("\"scoreBreakdown\":{"), search.out)
    }

    @Test
    fun dependencyReviewAndProposalPreparationExposeExplicitBlockedState(): Unit {
        val session = loadSession()
        val module = session.browseCuratedModules(pageSize = 1).items.single()
        val element = session.browseModule(module.ontologyIri, pageSize = 1).items.single()
        val entityIri = element.descriptor.descriptor.common.entity.value

        val dependencies = runCli("external-dependencies", projectRoot.toString(), entityIri, "--kind", element.kind.name)
        assertEquals(1, dependencies.exitCode, dependencies.out)
        assertTrue(dependencies.out.contains("\"requiresExplicitApproval\":true"), dependencies.out)
        assertTrue(dependencies.out.contains("\"selection\":\"Missing\""), dependencies.out)

        val before = Files.readAllBytes(projectRoot.resolve("ontology/simple.ttl")).toList()
        val proposal = runCli(
            "external-proposal",
            projectRoot.toString(),
            "simple",
            "https://example.com/entio/simple",
            "--intent",
            intentFor(element.kind),
            "--external-iri",
            entityIri,
            "--kind",
            element.kind.name,
        )
        assertEquals(1, proposal.exitCode, proposal.out)
        assertTrue(proposal.out.contains("unapproved-required-dependency"), proposal.out)
        assertEquals(before, Files.readAllBytes(projectRoot.resolve("ontology/simple.ttl")).toList())
    }

    @Test
    fun invalidFiltersAndEmptyResultsAreStructuredResponses(): Unit {
        val invalid = runCli("external-search", projectRoot.toString(), "customer", "--kind", "not-a-kind")
        assertEquals(1, invalid.exitCode)
        assertTrue(invalid.out.contains("\"code\":\"invalid-external-kind\""), invalid.out)

        val empty = runCli("external-search", projectRoot.toString(), "zzzz-no-external-match", "--minimum-score", "100")
        assertEquals(0, empty.exitCode, empty.out)
        assertTrue(empty.out.contains("\"totalResultCount\":0"), empty.out)
        assertTrue(empty.out.contains("\"candidates\":[]"), empty.out)
    }

    private fun loadSession(): ExternalFiboCatalogSession {
        val packageRoot = repoRoot().resolve("external-ontologies/fibo")
        return (FiboCatalogLoader(packageRoot).load() as EntioResult.Success).value
    }

    private fun intentFor(kind: com.entio.core.ExternalEntityKind): String = when (kind) {
        com.entio.core.ExternalEntityKind.Class -> "reuse-class"
        com.entio.core.ExternalEntityKind.ObjectProperty -> "reuse-object-property"
        com.entio.core.ExternalEntityKind.DatatypeProperty -> "reuse-datatype-property"
    }

    private fun runCli(vararg args: String): CliRun {
        val out = StringWriter()
        val err = StringWriter()
        val exitCode = EntioCli().execute(args.toList().toTypedArray(), PrintWriter(out, true), PrintWriter(err, true))
        return CliRun(exitCode, out.toString(), err.toString())
    }

    private fun repoRoot(): Path = generateSequence(Path.of("").toAbsolutePath().normalize()) { it.parent }
        .first { Files.exists(it.resolve("external-ontologies/fibo")) && Files.exists(it.resolve("examples/simple-ontology/entio.yaml")) }

    private data class CliRun(val exitCode: Int, val out: String, val err: String)
}
