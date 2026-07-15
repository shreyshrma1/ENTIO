package com.entio.semantic

import com.entio.core.EntioProjectConfig
import com.entio.core.EntioResult
import com.entio.core.ImportFindingKind
import com.entio.core.OntologyFormat
import com.entio.core.OntologySourceReference
import com.entio.core.ResolvedOntologySource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ImportClosureResolverTest {
    private val resolver = ImportClosureResolver()

    @Test
    fun resolvesNestedLocalImportsWithoutNetworkAccess(): Unit {
        val projectRoot = Files.createTempDirectory("entio-import-nested")
        val root = projectRoot.resolve("root.ttl").also {
            it.writeText(
                """
                @prefix ex: <https://example.com/> .
                @prefix owl: <http://www.w3.org/2002/07/owl#> .
                ex:root owl:imports ex:middle .
                """.trimIndent(),
            )
        }
        val middle = projectRoot.resolve("middle.ttl").also {
            it.writeText(
                """
                @prefix ex: <https://example.com/> .
                @prefix owl: <http://www.w3.org/2002/07/owl#> .
                ex:middle owl:imports ex:leaf .
                """.trimIndent(),
            )
        }
        val leaf = projectRoot.resolve("leaf.ttl").also {
            it.writeText("@prefix ex: <https://example.com/> . ex:leaf a ex:Ontology .")
        }
        val sources = listOf(
            resolvedSource("root", root),
            resolvedSource("middle", middle),
            resolvedSource("leaf", leaf),
        )
        val config = config(
            sources = sources,
            mappings = mapOf(
                "https://example.com/middle" to "middle",
                "https://example.com/leaf" to "leaf",
            ),
        )

        val result = resolver.resolve(config, sources)

        val report = assertIs<EntioResult.Success<com.entio.core.ImportClosureReport>>(result).value
        assertEquals(listOf("leaf", "middle", "root"), report.sourceIds)
        assertTrue(report.complete)
        assertTrue(report.findings.isEmpty())
    }

    @Test
    fun reportsMissingAndUnknownMappedImportsAsIncomplete(): Unit {
        val projectRoot = Files.createTempDirectory("entio-import-missing")
        val root = projectRoot.resolve("root.ttl").also {
            it.writeText(
                """
                @prefix ex: <https://example.com/> .
                @prefix owl: <http://www.w3.org/2002/07/owl#> .
                ex:root owl:imports ex:missing, ex:unknown .
                """.trimIndent(),
            )
        }
        val sources = listOf(resolvedSource("root", root))
        val config = config(
            sources = sources,
            mappings = mapOf("https://example.com/unknown" to "not-configured"),
        )

        val report = assertIs<EntioResult.Success<com.entio.core.ImportClosureReport>>(
            resolver.resolve(config, sources),
        ).value

        assertFalse(report.complete)
        assertEquals(
            listOf(ImportFindingKind.Missing, ImportFindingKind.Unresolved),
            report.findings.map { it.kind },
        )
    }

    @Test
    fun reportsCyclesButKeepsCompleteClosureWhenAllSourcesResolve(): Unit {
        val projectRoot = Files.createTempDirectory("entio-import-cycle")
        val first = projectRoot.resolve("first.ttl").also {
            it.writeText(
                """
                @prefix ex: <https://example.com/> .
                @prefix owl: <http://www.w3.org/2002/07/owl#> .
                ex:first owl:imports ex:second .
                """.trimIndent(),
            )
        }
        val second = projectRoot.resolve("second.ttl").also {
            it.writeText(
                """
                @prefix ex: <https://example.com/> .
                @prefix owl: <http://www.w3.org/2002/07/owl#> .
                ex:second owl:imports ex:first .
                """.trimIndent(),
            )
        }
        val sources = listOf(
            resolvedSource("first", first),
            resolvedSource("second", second),
        )
        val config = config(
            sources = sources,
            mappings = mapOf(
                "https://example.com/first" to "first",
                "https://example.com/second" to "second",
            ),
        )

        val report = assertIs<EntioResult.Success<com.entio.core.ImportClosureReport>>(
            resolver.resolve(config, sources),
        ).value

        assertTrue(report.complete)
        assertEquals(listOf(ImportFindingKind.Cycle), report.findings.map { it.kind })
    }

    private fun config(
        sources: List<ResolvedOntologySource>,
        mappings: Map<String, String>,
    ): EntioProjectConfig = EntioProjectConfig(
        name = "imports",
        ontologySources = sources.map { source ->
            OntologySourceReference(
                id = source.id,
                path = source.path.fileName.toString(),
                format = source.format,
                roles = source.roles,
            )
        },
        importMappings = mappings,
    )

    private fun resolvedSource(id: String, path: Path): ResolvedOntologySource = ResolvedOntologySource(
        id = id,
        path = path,
        format = OntologyFormat.Turtle,
    )
}
