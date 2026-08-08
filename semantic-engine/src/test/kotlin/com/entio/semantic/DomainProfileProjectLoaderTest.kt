package com.entio.semantic

import com.entio.core.DomainOntologyProfile
import com.entio.core.DomainOntologyProfileIdentity
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DomainProfileProjectLoaderTest {
    private val repository = DomainProfileRepository()
    private val loader = ProjectLoader()

    @Test
    fun absentProfileLeavesResolvedSourcesAndSemanticGraphUnchanged(): Unit {
        val root = project()

        val loaded = assertIs<EntioResult.Success<EntioProject>>(loader.loadProject(root)).value

        assertNull(loaded.activeDomainOntology)
        assertEquals(listOf("local"), loaded.resolvedSources.map { it.id })
        assertEquals(setOf("https://example.com/Local"), loaded.symbols.map { it.iri.value }.toSet())
        assertEquals(1, loaded.graph.triples.size)
    }

    @Test
    fun exactActiveProfileAddsDerivedManagedSourceWithoutChangingConfig(): Unit {
        val root = project()
        activate(root)
        val configBefore = Files.readString(root.resolve("entio.yaml"))

        val loaded = assertIs<EntioResult.Success<EntioProject>>(loader.loadProject(root)).value

        assertNotNull(loaded.activeDomainOntology)
        assertEquals(listOf("local", "fibo-reuse"), loaded.resolvedSources.map { it.id })
        assertEquals(listOf("local"), loaded.config.ontologySources.map { it.id })
        assertEquals(1, loaded.graph.triples.size)
        assertEquals(configBefore, Files.readString(root.resolve("entio.yaml")))
    }

    @Test
    fun activeProfileRejectsDuplicateConfiguredManagedSourceId(): Unit {
        val root = project(
            additionalConfig =
                "  - id: fibo-reuse\n" +
                    "    path: ontology/other.ttl\n" +
                    "    format: turtle\n",
        )
        root.resolve("ontology/other.ttl").writeText(DomainProfileService.EMPTY_MANAGED_SOURCE)
        activate(root)

        val failure = assertIs<EntioResult.Failure>(loader.loadProject(root))

        assertEquals("duplicate-domain-managed-source", failure.issues.single().code)
    }

    @Test
    fun activeProfileRejectsDuplicateConfiguredManagedSourcePath(): Unit {
        val root = project(
            additionalConfig =
                "  - id: duplicate-path\n" +
                    "    path: ontology/fibo-reuse.ttl\n" +
                    "    format: turtle\n",
        )
        activate(root)

        val failure = assertIs<EntioResult.Failure>(loader.loadProject(root))

        assertEquals("duplicate-domain-managed-source", failure.issues.single().code)
    }

    @Test
    fun activeProfileRejectsOntologyDeclarationInManagedStatementContainer(): Unit {
        val root = project()
        activate(root)
        root.resolve(DomainOntologyProfileIdentity.MANAGED_SOURCE_PATH).writeText(
            """
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            <https://example.com/managed> a owl:Ontology .
            """.trimIndent(),
        )

        val failure = assertIs<EntioResult.Failure>(loader.loadProject(root))

        assertEquals("unexpected-domain-ontology-declaration", failure.issues.single().code)
    }

    @Test
    fun projectLoadRecoversPreparedActivationBeforeReadingProfile(): Unit {
        val root = project()
        val service = DomainProfileService()
        assertIs<EntioResult.Success<PreparedDomainTransaction>>(service.prepareActivation(root))

        val loaded = assertIs<EntioResult.Success<EntioProject>>(loader.loadProject(root)).value

        assertNull(loaded.activeDomainOntology)
        assertEquals(listOf("local"), loaded.resolvedSources.map { it.id })
        assertEquals(DomainTransactionRecoveryOutcome.NoTransaction, assertIs<EntioResult.Success<DomainTransactionRecoveryOutcome>>(
            DomainFileTransactionManager().recover(root),
        ).value)
    }

    private fun project(additionalConfig: String = ""): Path {
        val root = Files.createTempDirectory("entio-domain-loader")
        root.resolve("ontology").createDirectories()
        root.resolve("ontology/local.ttl").writeText(
            """
            @prefix ex: <https://example.com/> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            ex:Local a rdfs:Class .
            """.trimIndent(),
        )
        root.resolve("entio.yaml").writeText(
            "name: domain-test\n" +
                "ontologySources:\n" +
                "  - id: local\n" +
                "    path: ontology/local.ttl\n" +
                "    format: turtle\n" +
                additionalConfig,
        )
        return root
    }

    private fun activate(root: Path): Unit {
        root.resolve(".entio").createDirectories()
        root.resolve(DomainOntologyProfileIdentity.PROFILE_PATH).writeText(
            assertIs<EntioResult.Success<String>>(repository.serialize(DomainOntologyProfile())).value,
        )
        root.resolve(DomainOntologyProfileIdentity.MANAGED_SOURCE_PATH).writeText(DomainProfileService.EMPTY_MANAGED_SOURCE)
    }
}
