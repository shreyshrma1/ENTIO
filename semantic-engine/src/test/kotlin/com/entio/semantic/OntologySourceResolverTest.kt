package com.entio.semantic

import com.entio.core.EntioProjectConfig
import com.entio.core.EntioResult
import com.entio.core.OntologyFormat
import com.entio.core.OntologySourceReference
import com.entio.core.ResolvedOntologySource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OntologySourceResolverTest {
    private val resolver = OntologySourceResolver()

    @Test
    fun resolvesExistingRelativeOntologyFile(): Unit {
        val projectRoot = Files.createTempDirectory("entio-source-resolution")
        projectRoot.resolve("ontology").createDirectories()
        projectRoot.resolve("ontology/simple.ttl").writeText("@prefix ex: <https://example.com/> .")
        val config = configWithSources(
            source(path = "ontology/simple.ttl"),
        )

        val result = resolver.resolveSources(projectRoot, config)

        val success = assertIs<EntioResult.Success<List<ResolvedOntologySource>>>(result)
        val resolvedSource = success.value.single()
        assertEquals("simple", resolvedSource.id)
        assertEquals(OntologyFormat.Turtle, resolvedSource.format)
        assertEquals(projectRoot.resolve("ontology/simple.ttl").toAbsolutePath().normalize(), resolvedSource.path)
    }

    @Test
    fun preservesConfiguredSourceOrder(): Unit {
        val projectRoot = Files.createTempDirectory("entio-source-order")
        projectRoot.resolve("ontology").createDirectories()
        projectRoot.resolve("ontology/first.ttl").writeText("@prefix ex: <https://example.com/> .")
        projectRoot.resolve("ontology/second.ttl").writeText("@prefix ex: <https://example.org/> .")
        val config = configWithSources(
            source(id = "first", path = "ontology/first.ttl"),
            source(id = "second", path = "ontology/second.ttl"),
        )

        val result = resolver.resolveSources(projectRoot, config)

        val success = assertIs<EntioResult.Success<List<ResolvedOntologySource>>>(result)
        assertEquals(listOf("first", "second"), success.value.map { it.id })
    }

    @Test
    fun returnsFailureWhenOntologyFileIsMissing(): Unit {
        val projectRoot = Files.createTempDirectory("entio-source-missing")
        val config = configWithSources(
            source(path = "ontology/missing.ttl"),
        )

        val result = resolver.resolveSources(projectRoot, config)

        val failure = assertIs<EntioResult.Failure>(result)
        assertEquals("missing-ontology-source-file", failure.issues.single().code)
    }

    @Test
    fun rejectsAbsoluteOntologyPath(): Unit {
        val config = configWithSources(
            source(path = Path.of("/tmp/outside.ttl").toString()),
        )

        val result = resolver.resolveSources(
            projectRoot = Files.createTempDirectory("entio-source-absolute"),
            config = config,
        )

        val failure = assertIs<EntioResult.Failure>(result)
        assertEquals("absolute-ontology-source-path", failure.issues.single().code)
    }

    @Test
    fun rejectsPathTraversalOutsideProjectRoot(): Unit {
        val projectRoot = Files.createTempDirectory("entio-source-traversal")
        val outside = Files.createTempFile(projectRoot.parent, "outside", ".ttl")
        outside.writeText("@prefix ex: <https://example.com/> .")
        val config = configWithSources(
            source(path = "../${outside.fileName}"),
        )

        val result = resolver.resolveSources(projectRoot, config)

        val failure = assertIs<EntioResult.Failure>(result)
        assertEquals("unsafe-ontology-source-path", failure.issues.single().code)
    }

    @Test
    fun returnsFailureForDuplicateSourceIds(): Unit {
        val projectRoot = Files.createTempDirectory("entio-source-duplicates")
        val config = configWithSources(
            source(id = "simple", path = "ontology/one.ttl"),
            source(id = "simple", path = "ontology/two.ttl"),
        )

        val result = resolver.resolveSources(projectRoot, config)

        val failure = assertIs<EntioResult.Failure>(result)
        assertEquals("duplicate-ontology-source-id", failure.issues.single().code)
        assertTrue(failure.message.contains("simple"))
    }

    private fun configWithSources(
        vararg sources: OntologySourceReference,
    ): EntioProjectConfig =
        EntioProjectConfig(
            name = "simple-ontology",
            ontologySources = sources.toList(),
        )

    private fun source(
        id: String = "simple",
        path: String,
    ): OntologySourceReference =
        OntologySourceReference(
            id = id,
            path = path,
            format = OntologyFormat.Turtle,
        )
}
