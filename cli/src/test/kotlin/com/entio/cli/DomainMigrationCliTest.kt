package com.entio.cli

import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.appendText
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DomainMigrationCliTest {
    @Test
    fun `reports and previews recognized copied legacy project without mutation`(): Unit {
        val root = copiedProject()
        val before = Files.readAllBytes(root.resolve("ontology/simple.ttl")).toList()

        val report = runCli("domain-migration", root.toString())
        val preview = runCli("domain-migration-preview", root.toString())

        assertEquals(0, report.exitCode, report.out)
        assertTrue(report.out.contains("\"status\":\"ExistingReuseRecognized\""), report.out)
        assertTrue(report.out.contains("\"verifiedCurrentRelease\":\"master_2026Q2\""), report.out)
        assertTrue(report.out.contains("\"historicalRelease\":null"), report.out)
        assertEquals(0, preview.exitCode, preview.out)
        assertTrue(preview.out.contains("\"readOnly\":true"), preview.out)
        assertTrue(preview.out.contains("\"movesExistingStatements\":false"), preview.out)
        assertTrue(preview.out.contains("\"requiresNormalProposalForStatementMovement\":true"), preview.out)
        assertFalse(Files.exists(root.resolve(".entio/domain-profile.yaml")))
        assertEquals(before, Files.readAllBytes(root.resolve("ontology/simple.ttl")).toList())
    }

    @Test
    fun `ambiguous copied legacy project returns diagnostics and blocks migration preview`(): Unit {
        val root = copiedProject()
        root.resolve("ontology/simple.ttl").appendText(
            "\n<https://spec.edmcouncil.org/fibo/ontology/LEGACY/UnknownConcept> " +
                "a <http://www.w3.org/2002/07/owl#Class> .\n",
        )

        val report = runCli("domain-migration", root.toString())
        val preview = runCli("domain-migration-preview", root.toString())

        assertEquals(0, report.exitCode, report.out)
        assertTrue(report.out.contains("\"status\":\"ExistingReuseAmbiguous\""), report.out)
        assertTrue(report.out.contains("UnknownConcept"), report.out)
        assertEquals(1, preview.exitCode, preview.out)
        assertTrue(preview.out.contains("\"code\":\"domain-migration-not-recognized\""), preview.out)
        assertFalse(Files.exists(root.resolve(".entio/domain-profile.yaml")))
    }

    private fun copiedProject(): Path {
        val source = repoRoot().resolve("examples/simple-ontology")
        val root = Files.createTempDirectory("entio-domain-migration-cli")
        root.resolve("ontology").createDirectories()
        Files.copy(source.resolve("entio.yaml"), root.resolve("entio.yaml"))
        Files.copy(source.resolve("ontology/simple.ttl"), root.resolve("ontology/simple.ttl"))
        Files.copy(source.resolve("ontology/shapes.ttl"), root.resolve("ontology/shapes.ttl"))
        return root
    }

    private fun runCli(vararg args: String): CliRun {
        val out = StringWriter()
        val err = StringWriter()
        val exitCode = EntioCli().execute(args.toList().toTypedArray(), PrintWriter(out, true), PrintWriter(err, true))
        return CliRun(exitCode, out.toString(), err.toString())
    }

    private fun repoRoot(): Path = generateSequence(Path.of("").toAbsolutePath().normalize()) { it.parent }
        .first { Files.isDirectory(it.resolve("external-ontologies/domain-search")) }

    private data class CliRun(val exitCode: Int, val out: String, val err: String)
}
