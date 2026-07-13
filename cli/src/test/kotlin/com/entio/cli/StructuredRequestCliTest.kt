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
}
