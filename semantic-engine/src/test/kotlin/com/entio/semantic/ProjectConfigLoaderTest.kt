package com.entio.semantic

import com.entio.core.EntioResult
import com.entio.core.EntioProjectConfig
import com.entio.core.IriNamespaceConfig
import com.entio.core.OntologyFormat
import com.entio.core.ShaclGraphRole
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProjectConfigLoaderTest {
    private val loader = ProjectConfigLoader()

    @Test
    fun loadsValidConfig(): Unit {
        val projectRoot = projectRootWithConfig(
            """
            name: simple-ontology
            ontologySources:
              - id: simple
                path: ontology/simple.ttl
                format: turtle
            """.trimIndent(),
        )

        val result = loader.loadConfig(projectRoot)

        val success = assertIs<EntioResult.Success<EntioProjectConfig>>(result)
        val config = success.value
        assertEquals("simple-ontology", config.name)
        assertEquals(1, config.ontologySources.size)
        assertEquals("simple", config.ontologySources.single().id)
        assertEquals("ontology/simple.ttl", config.ontologySources.single().path)
        assertEquals(OntologyFormat.Turtle, config.ontologySources.single().format)
    }

    @Test
    fun loadsOptionalIriNamespace(): Unit {
        val projectRoot = projectRootWithConfig(
            """
            name: simple-ontology
            iriNamespace: https://example.com/entio/simple#
            ontologySources:
              - id: simple
                path: ontology/simple.ttl
                format: turtle
            """.trimIndent(),
        )

        val result = loader.loadConfig(projectRoot)

        val config = assertIs<EntioResult.Success<EntioProjectConfig>>(result).value
        assertEquals(
            IriNamespaceConfig(com.entio.core.Iri("https://example.com/entio/simple#")),
            config.iriNamespace,
        )
    }

    @Test
    fun preservesReadOnlyLoadingWhenIriNamespaceIsAbsent(): Unit {
        val projectRoot = projectRootWithConfig(
            """
            name: simple-ontology
            ontologySources:
              - id: simple
                path: ontology/simple.ttl
                format: turtle
            """.trimIndent(),
        )

        val config = assertIs<EntioResult.Success<EntioProjectConfig>>(loader.loadConfig(projectRoot)).value

        assertEquals(null, config.iriNamespace)
    }

    @Test
    fun loadsExplicitGraphRolesAndImportMappings(): Unit {
        val projectRoot = projectRootWithConfig(
            """
            name: simple-ontology
            importMappings:
              https://example.com/imported: imported
            ontologySources:
              - id: shapes
                path: ontology/shapes.ttl
                format: turtle
                roles: [shapes]
              - id: imported
                path: ontology/imported.ttl
                format: turtle
                roles: [ontology, data]
            """.trimIndent(),
        )

        val config = assertIs<EntioResult.Success<EntioProjectConfig>>(loader.loadConfig(projectRoot)).value

        assertEquals(setOf(ShaclGraphRole.Shapes), config.ontologySources[0].roles)
        assertEquals(
            setOf(ShaclGraphRole.Ontology, ShaclGraphRole.Data),
            config.ontologySources[1].roles,
        )
        assertEquals("imported", config.importMappings["https://example.com/imported"])
    }

    @Test
    fun rejectsInvalidIriNamespace(): Unit {
        val projectRoot = projectRootWithConfig(
            """
            name: simple-ontology
            iriNamespace: relative/namespace
            ontologySources:
              - id: simple
                path: ontology/simple.ttl
                format: turtle
            """.trimIndent(),
        )

        val failure = assertIs<EntioResult.Failure>(loader.loadConfig(projectRoot))

        assertEquals("invalid-iri-namespace", failure.issues.single().code)
    }

    @Test
    fun returnsFailureWhenConfigFileIsMissing(): Unit {
        val result = loader.loadConfig(Files.createTempDirectory("entio-config-missing"))

        val failure = assertIs<EntioResult.Failure>(result)
        assertEquals("missing-entio-yaml", failure.issues.single().code)
    }

    @Test
    fun returnsFailureWhenYamlIsInvalid(): Unit {
        val projectRoot = projectRootWithConfig(
            """
            name: simple-ontology
            ontologySources:
              - id: simple
                path: ontology/simple.ttl
                format: [turtle
            """.trimIndent(),
        )

        val result = loader.loadConfig(projectRoot)

        val failure = assertIs<EntioResult.Failure>(result)
        assertEquals("invalid-entio-yaml", failure.issues.single().code)
    }

    @Test
    fun returnsFailureWhenProjectNameIsMissing(): Unit {
        val projectRoot = projectRootWithConfig(
            """
            ontologySources:
              - id: simple
                path: ontology/simple.ttl
                format: turtle
            """.trimIndent(),
        )

        val result = loader.loadConfig(projectRoot)

        val failure = assertIs<EntioResult.Failure>(result)
        assertEquals("missing-project-name", failure.issues.single().code)
    }

    @Test
    fun returnsFailureWhenOntologySourcesAreMissing(): Unit {
        val projectRoot = projectRootWithConfig(
            """
            name: simple-ontology
            """.trimIndent(),
        )

        val result = loader.loadConfig(projectRoot)

        val failure = assertIs<EntioResult.Failure>(result)
        assertEquals("missing-ontology-sources", failure.issues.single().code)
    }

    private fun projectRootWithConfig(config: String): Path {
        val projectRoot = Files.createTempDirectory("entio-config")
        projectRoot.resolve("entio.yaml").writeText(config)
        return projectRoot
    }
}
