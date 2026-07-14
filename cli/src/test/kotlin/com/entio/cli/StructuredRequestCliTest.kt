package com.entio.cli

import com.entio.core.EntioResult
import com.entio.core.DeletionDependencyIdentity
import com.entio.core.GraphTriple
import com.entio.core.Iri
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
        assertTrue(run.second.contains("dependencyKey"), run.second)
    }

    @Test
    fun structuredDeletionRequestRequiresAndPreservesExplicitDependencySelections(): Unit {
        val project = createDeletionProject()
        val dependent = GraphTriple(
            subject = Iri("https://example.com/entio/simple#Shrey"),
            predicate = Iri("https://example.com/entio/simple#recievedInvoice"),
            objectTerm = Iri("https://example.com/entio/simple#20874"),
        )
        val dependencyKey = DeletionDependencyIdentity("simple", dependent).key
        val blockedRequest = Files.createTempFile("entio-delete-blocked", ".json")
        blockedRequest.writeText(deleteRequest())
        val selectedRequest = Files.createTempFile("entio-delete-selected", ".json")
        selectedRequest.writeText(deleteRequest(dependencyKey))

        val blocked = runCli("proposal-request", project.toString(), "--request-file", blockedRequest.toString())
        val selected = runCli("proposal-request", project.toString(), "--request-file", selectedRequest.toString())

        assertEquals(1, blocked.first)
        assertTrue(blocked.second.contains("unresolved-deletion-dependencies"), blocked.second)
        assertEquals(0, selected.first, selected.second)
        assertTrue(selected.second.contains("\"selectedDependencyKeys\":[\"$dependencyKey\"]"), selected.second)
        assertTrue(selected.second.contains("\"changeCount\":5"), selected.second)
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

    @Test
    fun semanticCombinedRequestUsesExistingPreviewAndRejectLifecycle(): Unit {
        val project = createProject()
        val request = Files.createTempFile("entio-semantic-combined", ".json")
        request.writeText(semanticAnnotationPropertyRequest())
        val source = project.resolve("ontology/simple.ttl")
        val before = Files.readAllBytes(source)

        val preview = runCli("proposal-combined", project.toString(), "--request-file", request.toString(), "--action", "preview")
        val reject = runCli("proposal-combined", project.toString(), "--request-file", request.toString(), "--action", "reject")

        assertEquals(0, preview.first, preview.second)
        assertTrue(preview.second.contains("\"status\":\"readyforreview\""), preview.second)
        assertTrue(preview.second.contains("\"semanticEquivalence\":{\"status\":\"equivalent\"}"), preview.second)
        assertEquals(0, reject.first, reject.second)
        assertTrue(reject.second.contains("\"status\":\"rejected\""), reject.second)
        assertEquals(before.toList(), Files.readAllBytes(source).toList())
    }

    @Test
    fun semanticCombinedRequestAppliesThroughExistingProposalApplier(): Unit {
        val project = createProject()
        val request = Files.createTempFile("entio-semantic-apply", ".json")
        request.writeText(semanticAnnotationPropertyRequest(propertyIri = "https://example.com/hasDefinition"))

        val run = runCli("proposal-combined", project.toString(), "--request-file", request.toString(), "--action", "apply")

        assertEquals(0, run.first, run.second)
        assertTrue(run.second.contains("\"status\":\"applied\""), run.second)
        assertTrue(project.resolve("ontology/simple.ttl").toFile().readText().contains("hasDefinition"))
    }

    @Test
    fun semanticCombinedRequestReportsMalformedEditFields(): Unit {
        val project = createProject()
        val request = Files.createTempFile("entio-semantic-invalid", ".json")
        request.writeText(
            """
            {
              "schemaVersion": 1,
              "proposalId": "semantic-invalid",
              "title": "Invalid semantic edit",
              "targetSourceId": "simple",
              "edits": [
                {"kind": "add-definition", "targetIri": "https://example.com/Customer"}
              ]
            }
            """.trimIndent(),
        )

        val run = runCli("proposal-combined", project.toString(), "--request-file", request.toString(), "--action", "preview")

        assertEquals(1, run.first)
        assertTrue(run.second.contains("missing-semantic-edit-value"), run.second)
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

    private fun createDeletionProject(): Path {
        val root = createProject()
        root.resolve("ontology/simple.ttl").writeText(
            """
            @prefix ex: <https://example.com/entio/simple#> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            ex:Customer a owl:Class .
            ex:Invoice a owl:Class .
            ex:20874 a ex:Invoice .
            ex:Shrey a ex:Customer ; ex:recievedInvoice ex:20874 .
            ex:recievedInvoice a owl:ObjectProperty ;
                <http://www.w3.org/2000/01/rdf-schema#domain> ex:Customer ;
                <http://www.w3.org/2000/01/rdf-schema#range> ex:Invoice ;
                <http://www.w3.org/2000/01/rdf-schema#label> "recieved invoice" .
            """.trimIndent(),
        )
        return root
    }

    private fun semanticAnnotationPropertyRequest(
        propertyIri: String = "https://example.com/hasDefinition",
    ): String =
        """
        {
          "schemaVersion": 1,
          "proposalId": "semantic-property-1",
          "title": "Create semantic annotation property",
          "targetSourceId": "simple",
          "edits": [
            {
              "kind": "create-annotation-property",
              "propertyIri": "$propertyIri",
              "label": "has definition",
              "definition": "A human-readable definition."
            }
          ]
        }
        """.trimIndent()

    private fun deleteRequest(selectedDependencyKey: String? = null): String {
        val selected = selectedDependencyKey?.let { "\n          ,\"selectedDependencyKeys\": [\"$it\"]" }.orEmpty()
        return """
        {
          "schemaVersion": 1,
          "proposalId": "delete-received-invoice",
          "title": "Delete received invoice",
          "targetSourceId": "simple",
          "edits": [{"kind": "delete-entity", "entityIri": "https://example.com/entio/simple#recievedInvoice"$selected}]
        }
        """.trimIndent()
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
