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

    private data class CliRun(
        val exitCode: Int,
        val out: String,
        val err: String,
    )
}
