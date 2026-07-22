package com.entio.cli

import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.Iri
import com.entio.core.RdfLiteral
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

class Phase3EndToEndRegressionTest {
    private val projectLoader = ProjectLoader()

    @Test
    fun copiedFixtureSupportsSemanticPreviewRejectApplyReloadDescriptorAndSearch(): Unit {
        val fixture = copyExampleProject()
        addAnnotationProperty(fixture)
        val request = writeSemanticRequest(fixture.projectRoot)
        val originalSource = fixture.ontologyPath.readText()

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
        assertTrue(preview.out.contains("definition"), preview.out)
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

        val applied = runCli(
            "proposal-combined",
            fixture.projectRoot.toString(),
            "--request-file",
            request.toString(),
            "--action",
            "apply",
        )
        assertEquals(0, applied.exitCode, applied.out)
        assertTrue(applied.out.contains("\"status\":\"applied\""), applied.out)

        val reloaded = loadProject(fixture.projectRoot)
        assertTrue(
            reloaded.graph.triples.any { triple ->
                triple.subjectResource == Iri(CUSTOMER_IRI) &&
                    (triple.objectTerm as? RdfLiteral)?.lexicalForm == "A trusted customer." &&
                    (triple.objectTerm as? RdfLiteral)?.languageTag == "en"
            },
        )
        assertTrue(
            reloaded.graph.triples.any { triple ->
                triple.subjectResource == Iri(CUSTOMER_IRI) &&
                    (triple.objectTerm as? RdfLiteral)?.lexicalForm == "Trusted account" &&
                    (triple.objectTerm as? RdfLiteral)?.datatypeIri == Iri(XSD_STRING)
            },
        )

        val descriptor = runCli("descriptor", fixture.projectRoot.toString(), CUSTOMER_IRI)
        assertEquals(0, descriptor.exitCode, descriptor.out)
        assertTrue(descriptor.out.contains("A trusted customer."), descriptor.out)
        assertTrue(descriptor.out.contains("Client"), descriptor.out)

        val alternateSearch = runCli("search", fixture.projectRoot.toString(), "Client")
        assertEquals(0, alternateSearch.exitCode, alternateSearch.out)
        assertTrue(alternateSearch.out.contains("\"reason\":\"AlternateLabel\""), alternateSearch.out)

        val annotationSearch = runCli("search", fixture.projectRoot.toString(), "Trusted account")
        assertEquals(0, annotationSearch.exitCode, annotationSearch.out)
        assertTrue(annotationSearch.out.contains("\"reason\":\"Annotation\""), annotationSearch.out)
    }

    private fun addAnnotationProperty(fixture: ProjectFixture): Unit {
        fixture.ontologyPath.writeText(
            fixture.ontologyPath.readText() + """

            <$NOTE_IRI>
                    a <http://www.w3.org/2002/07/owl#AnnotationProperty> ;
                    <http://www.w3.org/2000/01/rdf-schema#label> "note" .
            """.trimIndent() + "\n",
        )
    }

    private fun writeSemanticRequest(projectRoot: Path): Path = projectRoot.resolve("phase-3-semantic-request.json").also { path ->
        path.writeText(
            """
            {
              "schemaVersion": 1,
              "proposalId": "phase-3-semantic-regression",
              "title": "Phase 3 semantic regression",
              "targetSourceId": "simple",
              "edits": [
                {"kind":"add-definition","targetIri":"$CUSTOMER_IRI","value":"A trusted customer.","language":"en"},
                {"kind":"add-alternate-label","targetIri":"$CUSTOMER_IRI","value":"Client","language":"en"},
                {"kind":"add-annotation","targetIri":"$CUSTOMER_IRI","propertyIri":"$NOTE_IRI","value":"Trusted account","datatype":"$XSD_STRING"}
              ]
            }
            """.trimIndent(),
        )
    }

    private fun copyExampleProject(): ProjectFixture {
        val sourceRoot = repositoryRoot().resolve("examples/simple-ontology")
        val targetRoot = Files.createTempDirectory("entio-phase-3-e2e")
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
        val exitCode = EntioCli().execute(args.toList().toTypedArray(), PrintWriter(out, true), PrintWriter(err, true))
        return CliRun(exitCode, out.toString(), err.toString())
    }

    private data class ProjectFixture(val projectRoot: Path, val ontologyPath: Path)
    private data class CliRun(val exitCode: Int, val out: String, val err: String)

    private companion object {
        private const val CUSTOMER_IRI =
            "https://spec.edmcouncil.org/fibo/ontology/FND/ProductsAndServices/ProductsAndServices/Customer"
        private const val NOTE_IRI = "https://example.com/entio/simple#note"
        private const val XSD_STRING = "http://www.w3.org/2001/XMLSchema#string"
    }
}
