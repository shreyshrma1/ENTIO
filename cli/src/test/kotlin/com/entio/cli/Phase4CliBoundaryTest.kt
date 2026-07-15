package com.entio.cli

import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Phase4CliBoundaryTest {
    @Test
    fun reasoningRefreshReturnsStableMachineReadableMetadata(): Unit {
        val project = createProject()

        val first = runCli("reasoning-refresh", project.toString())
        val second = runCli("reasoning-refresh", project.toString())

        assertEquals(0, first.exitCode, first.out)
        assertEquals(first.out, second.out)
        assertTrue(first.out.contains("\"command\":\"reasoning-refresh\""))
        assertTrue(first.out.contains("\"fingerprints\""))
        assertTrue(first.out.contains("\"importClosureComplete\":true"))
    }

    @Test
    fun shaclCommandsExposeModeResultsAndShapeDescriptors(): Unit {
        val project = createProject()

        val validation = runCli("shacl-validate", project.toString(), "--mode", "asserted-only")
        val shapes = runCli("shacl-shapes", project.toString())

        assertEquals(1, validation.exitCode, validation.out)
        assertTrue(validation.out.contains("\"command\":\"shacl-validate\""))
        assertTrue(validation.out.contains("\"mode\":\"asserted-only\""))
        assertTrue(validation.out.contains("\"severity\":\"violation\""))
        assertEquals(0, shapes.exitCode, shapes.out)
        assertTrue(shapes.out.contains("\"command\":\"shacl-shapes\""))
        assertTrue(shapes.out.contains("AccountShape"))
    }

    @Test
    fun invalidPhase4RequestReturnsStructuredOutput(): Unit {
        val project = createProject()

        val result = runCli("shacl-validate", project.toString(), "--mode", "unsupported")

        assertEquals(1, result.exitCode)
        assertTrue(result.err.isEmpty(), result.err)
        assertTrue(result.out.contains("\"command\":\"shacl-validate\""), result.out)
        assertTrue(result.out.contains("invalid-validation-mode"), result.out)
    }

    @Test
    fun proposalImpactSeparatesExplicitReasoningAndShaclSections(): Unit {
        val project = createProject()
        val request = Files.createTempFile("entio-phase4-impact", ".json")
        request.writeText(
            """
            {
              "schemaVersion": 1,
              "proposalId": "phase4-cli",
              "title": "Add invoice class",
              "targetSourceId": "simple",
              "edits": [
                {"kind": "create-class", "classIri": "https://example.com/entio/simple#Invoice", "label": "Invoice"}
              ]
            }
            """.trimIndent(),
        )

        val first = runCli("proposal-impact", project.toString(), "--request-file", request.toString())
        val second = runCli("proposal-impact", project.toString(), "--request-file", request.toString())

        assertEquals(0, first.exitCode, first.out)
        assertEquals(first.out, second.out)
        assertTrue(first.out.contains("\"explicitDiff\""))
        assertTrue(first.out.contains("\"reasoningImpact\""))
        assertTrue(first.out.contains("\"shaclImpact\""))
        assertTrue(first.out.contains("Invoice"))
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
        val root = Files.createTempDirectory("entio-phase4-cli")
        val ontologyDirectory = root.resolve("ontology")
        Files.createDirectories(ontologyDirectory)
        root.resolve("entio.yaml").writeText(
            """
            name: phase4-cli
            ontologySources:
              - id: simple
                path: ontology/simple.ttl
                format: turtle
                roles:
                  - ontology
                  - data
                  - shapes
            """.trimIndent(),
        )
        ontologyDirectory.resolve("simple.ttl").writeText(
            """
            @prefix ex: <https://example.com/entio/simple#> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix sh: <http://www.w3.org/ns/shacl#> .

            ex:Customer a owl:Class ;
                rdfs:label "Customer" .
            ex:Account a owl:Class ;
                rdfs:label "Account" .
            ex:Shrey a ex:Customer ;
                rdfs:label "Shrey" .
            ex:Account1 a ex:Account .
            ex:AccountShape a sh:NodeShape ;
                sh:targetClass ex:Account ;
                sh:property ex:AccountCodePropertyShape .
            ex:AccountCodePropertyShape sh:path ex:accountCode ;
                sh:minCount 1 .
            """.trimIndent(),
        )
        return root
    }

    private data class CliRun(
        val exitCode: Int,
        val out: String,
        val err: String,
    )
}
