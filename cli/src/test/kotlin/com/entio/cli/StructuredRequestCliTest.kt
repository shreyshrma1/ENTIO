package com.entio.cli

import com.entio.core.EntioResult
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StructuredRequestCliTest {
    @Test
    fun parserRejectsMalformedAndUnsupportedRequests(): Unit {
        val malformed = Files.createTempFile("entio-request-malformed", ".json")
        malformed.writeText("{not json")
        val malformedResult = StructuredRequestParser().parse(malformed)

        assertEquals("malformed-request-json", assertIs<EntioResult.Failure>(malformedResult).issues.single().code)

        val unsupported = Files.createTempFile("entio-request-schema", ".json")
        unsupported.writeText("{\"schemaVersion\":2,\"targetSourceId\":\"simple\",\"edits\":[{\"kind\":\"create-class\"}]}")
        val unsupportedResult = StructuredRequestParser().parse(unsupported)

        assertEquals("unsupported-request-schema", assertIs<EntioResult.Failure>(unsupportedResult).issues.single().code)
    }

    @Test
    fun proposalRequestPreservesHeterogeneousOrderAndStableOutput(): Unit {
        val project = createProject()
        val request = Files.createTempFile("entio-request", ".json")
        request.writeText(
            """
            {
              "schemaVersion": 1,
              "proposalId": "batch-1",
              "title": "Batch",
              "targetSourceId": "simple",
              "edits": [
                {"kind": "create-class", "classIri": "https://example.com/Invoice"},
                {"kind": "create-object-property", "propertyIri": "https://example.com/hasInvoice", "label": "has invoice"}
              ]
            }
            """.trimIndent(),
        )

        val run = runCli("proposal-request", project.toString(), "--request-file", request.toString())

        assertEquals(0, run.first)
        assertTrue(run.second.contains("\"ok\":true"), run.second)
        assertTrue(run.second.contains("\"orderedEditKinds\":[\"create-class\",\"create-object-property\"]"), run.second)
        assertTrue(run.second.contains("\"changeCount\":3"), run.second)
    }

    @Test
    fun unsupportedStructuredEditReturnsMachineReadableError(): Unit {
        val project = createProject()
        val request = Files.createTempFile("entio-request-unsupported", ".json")
        request.writeText("{\"targetSourceId\":\"simple\",\"edits\":[{\"kind\":\"future-edit\"}]}")

        val run = runCli("proposal-request", project.toString(), "--request-file", request.toString())

        assertEquals(1, run.first)
        assertTrue(run.second.contains("unsupported-edit-kind"))
    }

    @Test
    fun resolutionAndGenerationCommandsReturnStructuredResults(): Unit {
        val project = createProject()

        val resolution = runCli("resolve-label", project.toString(), "--label", "Customer", "--kind", "Class")
        val generated = runCli("generate-iri", project.toString(), "--label", "Commercial Loan", "--kind", "Class")

        assertEquals(0, resolution.first)
        assertTrue(resolution.second.contains("\"status\":\"resolved\""))
        assertEquals(0, generated.first)
        assertTrue(generated.second.contains("https://example.com/entio/simple#CommercialLoan"))
    }

    @Test
    fun deletionDependencyCommandExposesBlockers(): Unit {
        val project = createProject()

        val run = runCli(
            "deletion-dependencies",
            project.toString(),
            "simple",
            "--label",
            "Customer",
            "--kind",
            "Class",
        )

        assertEquals(1, run.first)
        assertTrue(run.second.contains("RequiresExplicitDependencies"), run.second)
        assertTrue(run.second.contains("IncomingReference"), run.second)
    }

    @Test
    fun combinedPreviewAndRejectReturnOneStructuredResultWithoutWriting(): Unit {
        val project = createProject()
        val request = Files.createTempFile("entio-combined", ".json")
        request.writeText(singleClassRequest())
        val source = project.resolve("ontology/simple.ttl")
        val before = Files.readAllBytes(source)

        val preview = runCli("proposal-combined", project.toString(), "--request-file", request.toString(), "--action", "preview")
        val reject = runCli("proposal-combined", project.toString(), "--request-file", request.toString(), "--action", "reject")

        assertEquals(0, preview.first, preview.second)
        assertTrue(preview.second.contains("\"command\":\"proposal-combined\""))
        assertTrue(preview.second.contains("\"semanticEquivalence\":{\"status\":\"equivalent\"}"))
        assertEquals(0, reject.first, reject.second)
        assertTrue(reject.second.contains("\"status\":\"rejected\""))
        assertEquals(before.toList(), Files.readAllBytes(source).toList())
    }

    @Test
    fun combinedApplyWritesAndReloadsOnlyAfterPreviewIsReady(): Unit {
        val project = createProject()
        val request = Files.createTempFile("entio-combined-apply", ".json")
        request.writeText(singleClassRequest(classIri = "https://example.com/Invoice"))

        val run = runCli("proposal-combined", project.toString(), "--request-file", request.toString(), "--action", "apply")

        assertEquals(0, run.first, run.second)
        assertTrue(run.second.contains("\"status\":\"applied\""), run.second)
        assertTrue(project.resolve("ontology/simple.ttl").toFile().readText().contains("Invoice"))
    }

    @Test
    fun combinedActionBlocksStaleBaselineBeforeWriting(): Unit {
        val project = createProject()
        val request = Files.createTempFile("entio-combined-stale", ".json")
        request.writeText(
            singleClassRequest().replace(
                "\"edits\":",
                "\"baseline\":{\"projectFingerprint\":\"stale\",\"targetSourceFingerprint\":\"stale\",\"graphFingerprint\":\"stale\"},\n  \"edits\":",
            ),
        )
        val source = project.resolve("ontology/simple.ttl")
        val before = Files.readAllBytes(source)

        val run = runCli("proposal-combined", project.toString(), "--request-file", request.toString(), "--action", "apply")

        assertEquals(1, run.first)
        assertTrue(run.second.contains("stale-proposal-baseline"), run.second)
        assertEquals(before.toList(), Files.readAllBytes(source).toList())
    }

    private fun runCli(vararg args: String): Pair<Int, String> {
        val output = ByteArrayOutputStream()
        val error = ByteArrayOutputStream()
        val code = EntioCli().execute(
            args = arrayOf(*args),
            out = PrintWriter(output, true),
            err = PrintWriter(error, true),
        )
        return code to output.toString(Charsets.UTF_8)
    }

    private fun createProject(): Path {
        val root = Files.createTempDirectory("entio-cli-structured")
        root.resolve("entio.yaml").writeText(
            """
            name: simple
            iriNamespace: https://example.com/entio/simple#
            ontologySources:
              - id: simple
                path: ontology/simple.ttl
                format: turtle
            """.trimIndent(),
        )
        Files.createDirectories(root.resolve("ontology"))
        root.resolve("ontology/simple.ttl").writeText(
            """
            @prefix ex: <https://example.com/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            ex:Customer a owl:Class ; rdfs:label "Customer" .
            ex:Person a owl:Class ; rdfs:subClassOf ex:Customer .
            """.trimIndent(),
        )
        return root
    }

    private fun singleClassRequest(classIri: String = "https://example.com/Invoice"): String =
        """
        {
          "schemaVersion": 1,
          "proposalId": "combined-1",
          "title": "Combined proposal",
          "targetSourceId": "simple",
          "edits": [{"kind": "create-class", "classIri": "$classIri"}]
        }
        """.trimIndent()
}
