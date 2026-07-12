package com.entio.cli

import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MachineReadableCliTest {
    @Test
    fun projectSummaryReturnsStableStructuredOutput(): Unit {
        val projectRoot = createProject()

        val result = runCli("project-summary", projectRoot.toString())

        assertEquals(0, result.exitCode)
        assertTrue(result.out.startsWith("{\"command\":\"project-summary\",\"ok\":true"))
        assertTrue(result.out.contains("\"name\":\"simple-ontology\""))
        assertTrue(result.out.contains("\"id\":\"simple\""))
        assertTrue(result.out.contains("\"graphTripleCount\":1"))
        assertEquals("", result.err)
    }

    @Test
    fun existingValidateCommandSupportsJsonOutput(): Unit {
        val projectRoot = createProject()

        val result = runCli("validate", projectRoot.toString(), "--json")

        assertEquals(0, result.exitCode)
        assertTrue(result.out.startsWith("{\"command\":\"validate\",\"ok\":true"))
        assertTrue(result.out.contains("\"status\":\"valid\""))
        assertEquals("", result.err)
    }

    @Test
    fun proposalPreviewValidateAndDiffReturnStructuredResults(): Unit {
        val projectRoot = createProject()
        val arguments = arrayOf(
            projectRoot.toString(),
            "simple",
            "--class-iri",
            "https://example.com/Invoice",
            "--label",
            "Invoice",
        )

        listOf("proposal-preview", "proposal-validate", "proposal-diff").forEach { command ->
            val result = runCli(command, *arguments)

            assertEquals(0, result.exitCode, command)
            assertTrue(result.out.contains("\"command\":\"$command\""), result.out)
            assertTrue(result.out.contains("\"validation\":{\"status\":\"valid\""), result.out)
            assertTrue(result.out.contains("https://example.com/Invoice"), result.out)
            assertEquals("", result.err)
        }
    }

    @Test
    fun proposalBoundaryDispatchesEveryPhase25EditKind(): Unit {
        val projectRoot = createProject()
        val common = arrayOf(projectRoot.toString(), "simple")
        val requests = listOf(
            arrayOf("--edit", "create-class", "--class-iri", "https://example.com/Invoice"),
            arrayOf("--edit", "create-object-property", "--property-iri", "https://example.com/owns"),
            arrayOf("--edit", "create-datatype-property", "--property-iri", "https://example.com/name"),
            arrayOf("--edit", "set-property-domain", "--property-iri", "https://example.com/owns", "--domain-iri", "https://example.com/Customer"),
            arrayOf("--edit", "set-property-range", "--property-iri", "https://example.com/owns", "--range-iri", "https://example.com/Account"),
            arrayOf("--edit", "create-individual", "--individual-iri", "https://example.com/alice", "--type-iri", "https://example.com/Customer"),
            arrayOf("--edit", "assign-individual-type", "--individual-iri", "https://example.com/alice", "--type-iri", "https://example.com/Customer"),
            arrayOf("--edit", "add-object-property-assertion", "--subject-iri", "https://example.com/alice", "--property-iri", "https://example.com/owns", "--object-iri", "https://example.com/account"),
            arrayOf("--edit", "add-datatype-property-assertion", "--subject-iri", "https://example.com/alice", "--property-iri", "https://example.com/name", "--value", "Alice"),
            arrayOf("--edit", "add-superclass", "--class-iri", "https://example.com/Invoice", "--superclass-iri", "https://example.com/Document"),
            arrayOf("--edit", "remove-superclass", "--class-iri", "https://example.com/Invoice", "--superclass-iri", "https://example.com/Document"),
            arrayOf("--edit", "set-entity-label", "--entity-iri", "https://example.com/Customer", "--label", "Client"),
        )

        requests.forEach { request ->
            val result = runCli("proposal-preview", *(common + request))

            assertTrue(!result.out.contains("unsupported-cli-edit"), result.out)
            assertTrue(!result.out.contains("Unsupported CLI edit"), result.out)
        }
    }

    @Test
    fun propertyCreationComposesOptionalDomainAndRangeChanges(): Unit {
        val projectRoot = createPropertyProject()

        val result = runCli(
            "proposal-preview",
            projectRoot.toString(),
            "simple",
            "--edit",
            "create-object-property",
            "--property-iri",
            "https://example.com/hasAccount",
            "--label",
            "owns account",
            "--domain-iri",
            "https://example.com/Customer",
            "--range-iri",
            "https://example.com/Account",
        )

        assertEquals(0, result.exitCode, result.out)
        assertTrue(result.out.contains("\"changeCount\":4"), result.out)
        assertTrue(result.out.contains("https://example.com/Customer"), result.out)
        assertTrue(result.out.contains("https://example.com/Account"), result.out)
        assertTrue(result.out.contains("\"status\":\"valid\""), result.out)
    }

    @Test
    fun datatypePropertyAcceptsDatatypeRangeAndRangeReplacementRemovesOldStatement(): Unit {
        val projectRoot = createPropertyProject()

        val datatypeResult = runCli(
            "proposal-preview",
            projectRoot.toString(),
            "simple",
            "--edit",
            "create-datatype-property",
            "--property-iri",
            "https://example.com/accountNumber",
            "--datatype",
            "http://www.w3.org/2001/XMLSchema#integer",
        )
        assertEquals(0, datatypeResult.exitCode, datatypeResult.out)
        assertTrue(datatypeResult.out.contains("http://www.w3.org/2001/XMLSchema#integer"), datatypeResult.out)

        val replacementResult = runCli(
            "proposal-preview",
            projectRoot.toString(),
            "simple",
            "--edit",
            "set-property-range",
            "--property-iri",
            "https://example.com/ownsAccount",
            "--range-iri",
            "https://example.com/Account",
            "--replace-existing",
        )
        assertEquals(0, replacementResult.exitCode, replacementResult.out)
        assertTrue(replacementResult.out.contains("\"changeCount\":2"), replacementResult.out)
        assertTrue(replacementResult.out.contains("https://example.com/Customer"), replacementResult.out)
        assertTrue(replacementResult.out.contains("https://example.com/Account"), replacementResult.out)
    }

    @Test
    fun proposalRejectReturnsWithoutChangingSource(): Unit {
        val projectRoot = createProject()
        val source = projectRoot.resolve("ontology/simple.ttl")
        val original = Files.readString(source)

        val result = runCli(
            "proposal-reject",
            projectRoot.toString(),
            "simple",
            "--class-iri",
            "https://example.com/Invoice",
        )

        assertEquals(0, result.exitCode)
        assertTrue(result.out.contains("\"status\":\"rejected\""))
        assertEquals(original, Files.readString(source))
        assertEquals("", result.err)
    }

    @Test
    fun proposalApplyReturnsStructuredResultAndWritesTargetSource(): Unit {
        val projectRoot = createProject()
        val source = projectRoot.resolve("ontology/simple.ttl")

        val result = runCli(
            "proposal-apply",
            projectRoot.toString(),
            "simple",
            "--class-iri",
            "https://example.com/Invoice",
            "--proposal-id",
            "apply-1",
        )

        assertEquals(0, result.exitCode)
        assertTrue(result.out.contains("\"status\":\"applied\""), result.out)
        assertTrue(Files.readString(source).contains("Invoice"))
        assertEquals("", result.err)
    }

    private fun runCli(vararg args: String): CliRun {
        val out = StringWriter()
        val err = StringWriter()
        val exitCode = EntioCli().execute(
            args = args.toList().toTypedArray(),
            out = PrintWriter(out, true),
            err = PrintWriter(err, true),
        )
        return CliRun(exitCode, out.toString(), err.toString())
    }

    private fun createProject(): Path {
        val projectRoot = Files.createTempDirectory("entio-machine-cli")
        val ontologyDirectory = projectRoot.resolve("ontology")
        Files.createDirectories(ontologyDirectory)
        Files.writeString(
            projectRoot.resolve("entio.yaml"),
            """
                name: simple-ontology
                ontologySources:
                  - id: simple
                    path: ontology/simple.ttl
                    format: turtle
            """.trimIndent(),
        )
        Files.writeString(
            ontologyDirectory.resolve("simple.ttl"),
            """
                @prefix ex: <https://example.com/> .
                @prefix owl: <http://www.w3.org/2002/07/owl#> .

                ex:Customer a owl:Class .
            """.trimIndent() + "\n",
        )
        return projectRoot
    }

    private fun createPropertyProject(): Path {
        val projectRoot = Files.createTempDirectory("entio-property-cli")
        val ontologyDirectory = projectRoot.resolve("ontology")
        Files.createDirectories(ontologyDirectory)
        Files.writeString(
            projectRoot.resolve("entio.yaml"),
            """
                name: simple-ontology
                ontologySources:
                  - id: simple
                    path: ontology/simple.ttl
                    format: turtle
            """.trimIndent(),
        )
        Files.writeString(
            ontologyDirectory.resolve("simple.ttl"),
            """
                @prefix ex: <https://example.com/> .
                @prefix owl: <http://www.w3.org/2002/07/owl#> .
                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

                ex:Customer a owl:Class .
                ex:Account a owl:Class .
                ex:ownsAccount a owl:ObjectProperty ;
                    rdfs:domain ex:Customer ;
                    rdfs:range ex:Customer .
            """.trimIndent() + "\n",
        )
        return projectRoot
    }

    private data class CliRun(
        val exitCode: Int,
        val out: String,
        val err: String,
    )
}
