package com.entio.cli

import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.Iri
import com.entio.semantic.ProjectLoader
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Phase4EndToEndRegressionTest {
    private val projectLoader = ProjectLoader()

    @Test
    fun copiedFixtureRunsPhase4WorkflowWithoutMutatingPreviewAndReloadsAppliedChange(): Unit {
        val fixture = copyExampleProject()
        addShapeSource(fixture.projectRoot)
        addReasoningFacts(fixture.ontologyPath)
        val originalSource = fixture.ontologyPath.readText()

        val reasoning = runCli("reasoning-refresh", fixture.projectRoot.toString())
        assertEquals(0, reasoning.exitCode, reasoning.out)
        assertTrue(reasoning.out.contains("\"command\":\"reasoning-refresh\""), reasoning.out)
        assertTrue(reasoning.out.contains("\"origin\":\"asserted\""), reasoning.out)
        assertTrue(reasoning.out.contains("\"origin\":\"inferred\""), reasoning.out)

        val validation = runCli("shacl-validate", fixture.projectRoot.toString(), "--mode", "asserted-only")
        assertEquals(1, validation.exitCode, validation.out)
        assertTrue(validation.out.contains("\"command\":\"shacl-validate\""), validation.out)
        assertTrue(validation.out.contains("\"mode\":\"asserted-only\""), validation.out)
        assertTrue(validation.out.contains("\"severity\":\"violation\""), validation.out)

        val shapes = runCli("shacl-shapes", fixture.projectRoot.toString())
        assertEquals(0, shapes.exitCode, shapes.out)
        assertTrue(shapes.out.contains("CustomerShape"), shapes.out)

        val request = writeProposalRequest(fixture.projectRoot)
        val impact = runCli("proposal-impact", fixture.projectRoot.toString(), "--request-file", request.toString())
        assertEquals(0, impact.exitCode, impact.out)
        assertTrue(impact.out.contains("\"explicitDiff\""), impact.out)
        assertTrue(impact.out.contains("\"reasoningImpact\""), impact.out)
        assertTrue(impact.out.contains("\"shaclImpact\""), impact.out)

        val preview = runCli(
            "proposal-combined",
            fixture.projectRoot.toString(),
            "--request-file",
            request.toString(),
            "--action",
            "preview",
        )
        assertEquals(0, preview.exitCode, preview.out)
        assertTrue(preview.out.contains("\"status\":\"readyforreview\""), preview.out)
        assertEquals(originalSource, fixture.ontologyPath.readText())

        val rejected = runCli(
            "proposal-combined",
            fixture.projectRoot.toString(),
            "--request-file",
            request.toString(),
            "--action",
            "reject",
        )
        assertEquals(0, rejected.exitCode, rejected.out)
        assertTrue(rejected.out.contains("\"status\":\"rejected\""), rejected.out)
        assertEquals(originalSource, fixture.ontologyPath.readText())

        val appliedFixture = copyExampleProject()
        val applied = runCli(
            "proposal-combined",
            appliedFixture.projectRoot.toString(),
            "--request-file",
            writeProposalRequest(appliedFixture.projectRoot).toString(),
            "--action",
            "apply",
        )
        assertEquals(0, applied.exitCode, applied.out)
        assertTrue(applied.out.contains("\"status\":\"applied\""), applied.out)
        val reloaded = loadProject(appliedFixture.projectRoot)
        assertTrue(
            reloaded.graph.triples.any { triple ->
                triple.subjectResource == Iri("https://example.com/entio/simple#Phase4RegressionClass")
            },
        )
    }

    private fun addShapeSource(projectRoot: Path): Unit {
        projectRoot.resolve("entio.yaml").writeText(
            """
            name: simple-ontology
            iriNamespace: https://example.com/entio/simple#
            ontologySources:
              - id: simple
                path: ontology/simple.ttl
                format: turtle
                roles:
                  - ontology
                  - data
              - id: shapes
                path: ontology/shapes.ttl
                format: turtle
                roles:
                  - shapes
            """.trimIndent(),
        )
        projectRoot.resolve("ontology/shapes.ttl").writeText(
            """
            @prefix ex: <https://example.com/entio/simple#> .
            @prefix fibo-ps: <https://spec.edmcouncil.org/fibo/ontology/FND/ProductsAndServices/ProductsAndServices/> .
            @prefix sh: <http://www.w3.org/ns/shacl#> .

            ex:CustomerShape a sh:NodeShape ;
                sh:targetClass fibo-ps:Customer ;
                sh:property ex:CustomerCodePropertyShape .
            ex:CustomerCodePropertyShape sh:path ex:customerCode ;
                sh:minCount 1 .
            """.trimIndent(),
        )
    }

    private fun addReasoningFacts(ontologyPath: Path): Unit {
        ontologyPath.writeText(
            ontologyPath.readText() + """

            @prefix ex: <https://example.com/entio/simple#> .
            @prefix fibo-ps: <https://spec.edmcouncil.org/fibo/ontology/FND/ProductsAndServices/ProductsAndServices/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            ex:BusinessParty a owl:Class .
            fibo-ps:Customer rdfs:subClassOf ex:BusinessParty .
            """.trimIndent() + "\n",
        )
    }

    private fun writeProposalRequest(projectRoot: Path): Path = projectRoot.resolve("phase4-regression-request.json").also { path ->
        path.writeText(
            """
            {
              "schemaVersion": 1,
              "proposalId": "phase4-e2e-regression",
              "title": "Phase 4 end-to-end regression",
              "targetSourceId": "simple",
              "edits": [
                {"kind":"create-class","classIri":"https://example.com/entio/simple#Phase4RegressionClass","label":"Phase 4 Regression Class"}
              ]
            }
            """.trimIndent(),
        )
    }

    private fun copyExampleProject(): ProjectFixture {
        val sourceRoot = repositoryRoot().resolve("examples/simple-ontology")
        val targetRoot = Files.createTempDirectory("entio-phase-4-e2e")
        val paths = Files.walk(sourceRoot)
        try {
            paths.forEach { sourcePath ->
                val targetPath = targetRoot.resolve(sourceRoot.relativize(sourcePath).toString())
                if (Files.isDirectory(sourcePath)) Files.createDirectories(targetPath)
                else Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            paths.close()
        }
        return ProjectFixture(targetRoot, targetRoot.resolve("ontology/simple.ttl"))
    }

    private fun loadProject(root: Path): EntioProject =
        assertIs<EntioResult.Success<EntioProject>>(projectLoader.loadProject(root)).value

    private fun repositoryRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        while (true) {
            if (Files.isRegularFile(current.resolve("examples/simple-ontology/entio.yaml"))) return current
            current = current.parent ?: error("Could not locate the Entio repository root.")
        }
    }

    private fun runCli(vararg args: String): CliRun {
        val out = StringWriter()
        val err = StringWriter()
        val exitCode = EntioCli().execute(
            args.toList().toTypedArray(),
            PrintWriter(out, true),
            PrintWriter(err, true),
        )
        return CliRun(exitCode, out.toString(), err.toString())
    }

    private data class ProjectFixture(val projectRoot: Path, val ontologyPath: Path)

    private data class CliRun(val exitCode: Int, val out: String, val err: String)
}
