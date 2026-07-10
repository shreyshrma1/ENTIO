package com.entio.cli

import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliModuleTest {
    @Test
    fun exposesModuleName(): Unit {
        assertEquals("cli", CliModule.NAME)
    }

    @Test
    fun validateCommandReturnsZeroForValidProject(): Unit {
        val projectRoot = createProject(
            turtle = """
                @prefix ex: <https://example.com/> .
                @prefix owl: <http://www.w3.org/2002/07/owl#> .
                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

                ex:Customer a owl:Class ;
                    rdfs:label "Customer" .
            """.trimIndent(),
        )

        val result = runCli("validate", projectRoot.toString())

        assertEquals(0, result.exitCode)
        assertEquals("Validation: valid\n", result.out)
        assertEquals("", result.err)
    }

    @Test
    fun validateCommandReturnsOneForInvalidProject(): Unit {
        val missingProjectRoot = Files.createTempDirectory("entio-missing-project").resolve("missing")

        val result = runCli("validate", missingProjectRoot.toString())

        assertEquals(1, result.exitCode)
        assertTrue(result.out.contains("Validation: invalid"))
        assertTrue(result.out.contains("ERROR missing-project-root"))
        assertEquals("", result.err)
    }

    @Test
    fun symbolsCommandPrintsSymbolsInStableFormat(): Unit {
        val projectRoot = createProject(
            turtle = """
                @prefix ex: <https://example.com/> .
                @prefix owl: <http://www.w3.org/2002/07/owl#> .
                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

                ex:Customer a owl:Class ;
                    rdfs:label "Customer" .
            """.trimIndent(),
        )

        val result = runCli("symbols", projectRoot.toString())

        assertEquals(0, result.exitCode)
        assertEquals("Class https://example.com/Customer \"Customer\" [simple]\n", result.out)
        assertEquals("", result.err)
    }

    @Test
    fun diffCommandReturnsZeroWhenProjectsHaveSameGraph(): Unit {
        val before = createProject(
            turtle = """
                @prefix ex: <https://example.com/> .
                @prefix owl: <http://www.w3.org/2002/07/owl#> .

                ex:Customer a owl:Class .
            """.trimIndent(),
        )
        val after = createProject(
            turtle = """
                @prefix ex: <https://example.com/> .
                @prefix owl: <http://www.w3.org/2002/07/owl#> .

                ex:Customer a owl:Class .
            """.trimIndent(),
        )

        val result = runCli("diff", before.toString(), after.toString())

        assertEquals(0, result.exitCode)
        assertEquals("No semantic changes.\n", result.out)
        assertEquals("", result.err)
    }

    @Test
    fun diffCommandReturnsOneWhenDiffEntriesExist(): Unit {
        val before = createProject(
            turtle = """
                @prefix ex: <https://example.com/> .
                @prefix owl: <http://www.w3.org/2002/07/owl#> .

                ex:Customer a owl:Class .
            """.trimIndent(),
        )
        val after = createProject(
            turtle = """
                @prefix ex: <https://example.com/> .
                @prefix owl: <http://www.w3.org/2002/07/owl#> .

                ex:Customer a owl:Class .
                ex:Invoice a owl:Class .
            """.trimIndent(),
        )

        val result = runCli("diff", before.toString(), after.toString())

        assertEquals(1, result.exitCode)
        assertTrue(result.out.contains("Added triple"))
        assertTrue(result.out.contains("https://example.com/Invoice"))
        assertEquals("", result.err)
    }

    @Test
    fun commandParsingReturnsTwoForMissingArguments(): Unit {
        val result = runCli("validate")

        assertEquals(2, result.exitCode)
        assertTrue(result.err.contains("Missing required parameter"))
    }

    private fun runCli(vararg args: String): CliRun {
        val out = StringWriter()
        val err = StringWriter()

        val exitCode = EntioCli().execute(
            args = args.toList().toTypedArray(),
            out = PrintWriter(out, true),
            err = PrintWriter(err, true),
        )

        return CliRun(
            exitCode = exitCode,
            out = out.toString(),
            err = err.toString(),
        )
    }

    private fun createProject(turtle: String): Path {
        val projectRoot = Files.createTempDirectory("entio-cli-test")
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
        Files.writeString(ontologyDirectory.resolve("simple.ttl"), turtle)
        return projectRoot
    }

    private data class CliRun(
        val exitCode: Int,
        val out: String,
        val err: String,
    )
}
