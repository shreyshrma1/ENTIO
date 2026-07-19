package com.entio.semantic

import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.LoadedOntology
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProjectLoaderTest {
    private val loader = ProjectLoader()

    @Test
    fun loadsExampleProject(): Unit {
        val projectRoot = Path.of("..", "examples", "simple-ontology").toAbsolutePath().normalize()

        val result = loader.loadProject(projectRoot)

        val success = assertIs<EntioResult.Success<EntioProject>>(result)
        assertEquals("simple-ontology", success.value.config.name)
        assertEquals(listOf("simple", "shapes"), success.value.resolvedSources.map { it.id })
        assertTrue(success.value.ontologies.all { it.graph.triples.isNotEmpty() })
        assertEquals(
            listOf(
                "https://example.com/entio/simple#20874",
                "https://example.com/entio/simple#Account",
                "https://example.com/entio/simple#Account101",
                "https://example.com/entio/simple#Checking",
                "https://example.com/entio/simple#Customer",
                "https://example.com/entio/simple#CustomerShape",
                "https://example.com/entio/simple#Invoice",
                "https://example.com/entio/simple#Shrey",
                "https://example.com/entio/simple#dateOpened",
                "https://example.com/entio/simple#ownsAccount",
            ),
            success.value.symbols.map { it.iri.value },
        )
        assertEquals(success.value.ontologies.flatMap { it.graph.triples }.toSet(), success.value.graph.triples)
    }

    @Test
    fun returnsProjectAggregateWithConfigSourcesOntologiesSymbolsAndGraph(): Unit {
        val projectRoot = projectWithSources(
            SourceFile(
                id = "simple",
                path = "ontology/simple.ttl",
                content = """
                    @prefix ex: <https://example.com/> .
                    @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

                    ex:Customer a rdfs:Class ;
                      rdfs:label "Customer" .
                """.trimIndent(),
            ),
        )

        val success = assertIs<EntioResult.Success<EntioProject>>(loader.loadProject(projectRoot))
        val project = success.value

        assertEquals("test-project", project.config.name)
        assertEquals(listOf("simple"), project.resolvedSources.map { it.id })
        assertEquals(listOf("simple"), project.ontologies.map { it.source.id })
        assertEquals(listOf("https://example.com/Customer"), project.symbols.map { it.iri.value })
        assertEquals(project.ontologies.flatMap { it.graph.triples }.toSet(), project.graph.triples)
    }

    @Test
    fun loadsMultipleSourcesDeterministically(): Unit {
        val projectRoot = projectWithSources(
            SourceFile(
                id = "second",
                path = "ontology/second.ttl",
                content = """
                    @prefix ex: <https://example.com/> .
                    @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

                    ex:Beta a rdfs:Class .
                """.trimIndent(),
            ),
            SourceFile(
                id = "first",
                path = "ontology/first.ttl",
                content = """
                    @prefix ex: <https://example.com/> .
                    @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

                    ex:Alpha a rdfs:Class .
                """.trimIndent(),
            ),
        )

        val first = assertIs<EntioResult.Success<EntioProject>>(loader.loadProject(projectRoot)).value
        val second = assertIs<EntioResult.Success<EntioProject>>(loader.loadProject(projectRoot)).value

        assertEquals(listOf("second", "first"), first.resolvedSources.map { it.id })
        assertEquals(first.ontologies.map { it.source.id }, second.ontologies.map { it.source.id })
        assertEquals(first.symbols.map { it.iri.value to it.sourceId }, second.symbols.map { it.iri.value to it.sourceId })
        assertEquals(
            listOf(
                "https://example.com/Alpha" to "first",
                "https://example.com/Beta" to "second",
            ),
            first.symbols.map { it.iri.value to it.sourceId },
        )
    }

    @Test
    fun collapsesDuplicateTriplesOnlyInCombinedGraph(): Unit {
        val duplicateContent = """
            @prefix ex: <https://example.com/> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            ex:Customer a rdfs:Class .
        """.trimIndent()
        val projectRoot = projectWithSources(
            SourceFile(id = "first", path = "ontology/first.ttl", content = duplicateContent),
            SourceFile(id = "second", path = "ontology/second.ttl", content = duplicateContent),
        )

        val project = assertIs<EntioResult.Success<EntioProject>>(loader.loadProject(projectRoot)).value

        assertEquals(listOf(1, 1), project.ontologies.map { it.graph.triples.size })
        assertEquals(1, project.graph.triples.size)
        assertEquals(project.ontologies.first().graph.triples.single(), project.graph.triples.single())
    }

    @Test
    fun returnsStructuredFailureForConfigErrors(): Unit {
        val projectRoot = Files.createTempDirectory("entio-project-loader")

        val failure = assertIs<EntioResult.Failure>(loader.loadProject(projectRoot))

        assertEquals("missing-entio-yaml", failure.issues.single().code)
    }

    @Test
    fun returnsStructuredFailureForSourceResolutionErrors(): Unit {
        val projectRoot = projectWithConfig(
            """
            name: test-project
            ontologySources:
              - id: missing
                path: ontology/missing.ttl
                format: turtle
            """.trimIndent(),
        )

        val failure = assertIs<EntioResult.Failure>(loader.loadProject(projectRoot))

        assertEquals("missing-ontology-source-file", failure.issues.single().code)
    }

    @Test
    fun returnsStructuredFailureForParseErrors(): Unit {
        val projectRoot = projectWithSources(
            SourceFile(
                id = "invalid",
                path = "ontology/invalid.ttl",
                content = """
                    @prefix ex: <https://example.com/> .
                    ex:Customer ex:relatedTo .
                """.trimIndent(),
            ),
        )

        val failure = assertIs<EntioResult.Failure>(loader.loadProject(projectRoot))

        assertEquals("invalid-turtle", failure.issues.single().code)
        assertEquals("invalid", failure.issues.single().source)
    }

    @Test
    fun returnsStructuredFailureForSymbolExtractionErrors(): Unit {
        val projectRoot = projectWithSources(
            SourceFile(
                id = "simple",
                path = "ontology/simple.ttl",
                content = """
                    @prefix ex: <https://example.com/> .
                    @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

                    ex:Customer a rdfs:Class .
                """.trimIndent(),
            ),
        )
        val failingLoader = ProjectLoader(
            extractSymbols = { ontology: LoadedOntology ->
                throw IllegalStateException("Cannot extract ${ontology.source.id}")
            },
        )

        val failure = assertIs<EntioResult.Failure>(failingLoader.loadProject(projectRoot))

        assertEquals("symbol-extraction-failed", failure.issues.single().code)
        assertEquals("simple", failure.issues.single().source)
    }

    private fun projectWithSources(vararg sources: SourceFile): Path {
        val sourceConfig = sources.joinToString(separator = "\n") { source ->
            "  - id: ${source.id}\n" +
                "    path: ${source.path}\n" +
                "    format: turtle"
        }
        val projectRoot = projectWithConfig(
            "name: test-project\n" +
                "ontologySources:\n" +
                sourceConfig,
        )

        sources.forEach { source ->
            val sourcePath = projectRoot.resolve(source.path)
            sourcePath.parent.createDirectories()
            sourcePath.writeText(source.content)
        }

        return projectRoot
    }

    private fun projectWithConfig(config: String): Path {
        val projectRoot = Files.createTempDirectory("entio-project-loader")
        projectRoot.resolve("entio.yaml").writeText(config)
        return projectRoot
    }

    private data class SourceFile(
        val id: String,
        val path: String,
        val content: String,
    )
}
