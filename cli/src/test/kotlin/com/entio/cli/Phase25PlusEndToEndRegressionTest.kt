package com.entio.cli

import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.core.SemanticEquivalenceResult
import com.entio.semantic.ProjectLoader
import com.entio.semantic.ProposalApplier
import com.entio.semantic.ProposalCreator
import com.entio.semantic.TypedOntologyEditTranslator
import com.entio.core.CreateClassEdit
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

class Phase25PlusEndToEndRegressionTest {
    private val projectLoader = ProjectLoader()
    private val editTranslator = TypedOntologyEditTranslator()

    @Test
    fun copiedExampleSupportsCombinedPreviewRejectApplyAndReload(): Unit {
        val previewFixture = copyExampleProject()
        val request = writeCombinedRequest(previewFixture.projectRoot)
        val originalSource = previewFixture.ontologyPath.readText()

        val preview = runCli(
            "proposal-combined",
            previewFixture.projectRoot.toString(),
            "--request-file",
            request.toString(),
            "--action",
            "preview",
        )
        assertEquals(0, preview.exitCode, preview.out)
        assertTrue(preview.out.contains("\"status\":\"readyforreview\""), preview.out)
        assertTrue(preview.out.contains("\"entryCount\":"), preview.out)
        assertTrue(preview.out.contains("PurchaseOrder"), preview.out)
        assertEquals(originalSource, previewFixture.ontologyPath.readText())

        val rejected = runCli(
            "proposal-combined",
            previewFixture.projectRoot.toString(),
            "--request-file",
            request.toString(),
            "--action",
            "reject",
        )
        assertEquals(0, rejected.exitCode, rejected.out)
        assertTrue(rejected.out.contains("\"status\":\"rejected\""), rejected.out)
        assertEquals(originalSource, previewFixture.ontologyPath.readText())

        val appliedFixture = copyExampleProject()
        val applied = runCli(
            "proposal-combined",
            appliedFixture.projectRoot.toString(),
            "--request-file",
            writeCombinedRequest(appliedFixture.projectRoot).toString(),
            "--action",
            "apply",
        )
        assertEquals(0, applied.exitCode, applied.out)
        assertTrue(applied.out.contains("\"status\":\"applied\""), applied.out)
        val reloaded = loadProject(appliedFixture.projectRoot)
        assertTrue(reloaded.graph.triples.any { triple -> triple.subjectResource == Iri("https://example.com/entio/simple#PurchaseOrder") })
        assertTrue(reloaded.graph.triples.any { triple -> triple.subjectResource == Iri("https://example.com/entio/simple#newInvoice") })
    }

    @Test
    fun labelResolutionGenerationCollisionAndDeletionUseStructuredBoundaries(): Unit {
        val fixture = copyExampleProject()

        val resolved = runCli(
            "resolve-label",
            fixture.projectRoot.toString(),
            "--label",
            "Shrey",
            "--kind",
            "Individual",
            "--source-id",
            "simple",
        )
        assertEquals(0, resolved.exitCode, resolved.out)
        assertTrue(resolved.out.contains("\"status\":\"resolved\""), resolved.out)
        assertTrue(resolved.out.contains("https://example.com/entio/simple#Shrey"), resolved.out)

        val generated = runCli(
            "generate-iri",
            fixture.projectRoot.toString(),
            "--label",
            "Purchase Order",
            "--kind",
            "Class",
        )
        assertEquals(0, generated.exitCode, generated.out)
        assertTrue(generated.out.contains("https://example.com/entio/simple#PurchaseOrder"), generated.out)

        val collision = runCli(
            "generate-iri",
            fixture.projectRoot.toString(),
            "--label",
            "Customer",
            "--kind",
            "Class",
        )
        assertEquals(1, collision.exitCode)
        assertTrue(collision.out.contains("collision"), collision.out)

        val ambiguousFixture = ambiguousProject()
        val ambiguous = runCli(
            "resolve-label",
            ambiguousFixture.toString(),
            "--label",
            "Thing",
        )
        assertEquals(1, ambiguous.exitCode)
        assertTrue(ambiguous.out.contains("ambiguous"), ambiguous.out)

        val blockedDeletion = runCli(
            "deletion-dependencies",
            fixture.projectRoot.toString(),
            "simple",
            "--label",
            "Customer",
            "--kind",
            "Class",
        )
        assertEquals(1, blockedDeletion.exitCode)
        assertTrue(blockedDeletion.out.contains("RequiresExplicitDependencies"), blockedDeletion.out)
        assertTrue(blockedDeletion.out.contains("IncomingReference"), blockedDeletion.out)
    }

    @Test
    fun staleCombinedRequestAndFailedSavePreserveCopiedSource(): Unit {
        val staleFixture = copyExampleProject()
        val staleRequest = writeCombinedRequest(staleFixture.projectRoot, baseline = true)
        val originalSource = staleFixture.ontologyPath.readText()

        val stale = runCli(
            "proposal-combined",
            staleFixture.projectRoot.toString(),
            "--request-file",
            staleRequest.toString(),
            "--action",
            "apply",
        )
        assertEquals(1, stale.exitCode)
        assertTrue(stale.out.contains("stale-proposal-baseline"), stale.out)
        assertEquals(originalSource, staleFixture.ontologyPath.readText())

        val rollbackFixture = copyExampleProject()
        val project = loadProject(rollbackFixture.projectRoot)
        val changeSet = assertIs<EntioResult.Success<com.entio.core.ChangeSet>>(
            editTranslator.translate(
                CreateClassEdit(Iri("https://example.com/entio/simple#RollbackClass"), RdfLiteral("Rollback class")),
            ),
        ).value
        val proposal = assertIs<EntioResult.Success<com.entio.core.ChangeProposal>>(
            ProposalCreator().createProposal(project, "simple", changeSet, "rollback-e2e", "Rollback E2E"),
        ).value.copy(status = com.entio.core.ChangeProposalStatus.Approved)
        val originalRollbackSource = rollbackFixture.ontologyPath.readText()
        val result = ProposalApplier(
            compareGraphs = { _, _ -> SemanticEquivalenceResult.NotEquivalent("Injected Phase 2.5+ verification failure.") },
        ).applyProposal(rollbackFixture.projectRoot, proposal)

        val failure = assertIs<com.entio.core.ApplyProposalResult.Failed>(result)
        assertTrue(failure.reason.contains("Injected Phase 2.5+ verification failure."))
        assertIs<com.entio.core.RollbackResult.Restored>(failure.rollback)
        assertEquals(originalRollbackSource, rollbackFixture.ontologyPath.readText())
    }

    private fun writeCombinedRequest(projectRoot: Path, baseline: Boolean = false): Path {
        val baselineJson = if (baseline) {
            "\"baseline\":{\"projectFingerprint\":\"stale\",\"targetSourceFingerprint\":\"stale\",\"graphFingerprint\":\"stale\"},"
        } else {
            ""
        }
        return projectRoot.resolve("combined-request.json").also { path ->
            path.writeText(
                """
                {
                  "schemaVersion": 1,
                  "proposalId": "phase25-plus-combined",
                  "title": "Phase 2.5+ combined regression",
                  "targetSourceId": "simple",
                  $baselineJson
                  "edits": [
                    {"kind":"create-class","classIri":"https://example.com/entio/simple#PurchaseOrder","label":"Purchase Order"},
                    {"kind":"create-individual","individualIri":"https://example.com/entio/simple#newInvoice","typeIri":"https://example.com/entio/simple#Invoice","label":"New Invoice"}
                  ]
                }
                """.trimIndent(),
            )
        }
    }

    private fun copyExampleProject(): ProjectFixture {
        val sourceRoot = repositoryRoot().resolve("examples/simple-ontology")
        val targetRoot = Files.createTempDirectory("entio-phase-25-plus-e2e")
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
        targetRoot.resolve("entio.yaml").writeText(
            targetRoot.resolve("entio.yaml").readText().replace(
                "name: simple-ontology",
                "name: simple-ontology\niriNamespace: https://example.com/entio/simple#",
            ),
        )
        return ProjectFixture(targetRoot, targetRoot.resolve("ontology/simple.ttl"))
    }

    private fun ambiguousProject(): Path {
        val root = Files.createTempDirectory("entio-phase-25-plus-ambiguous")
        Files.createDirectories(root.resolve("ontology"))
        root.resolve("entio.yaml").writeText(
            """
            name: ambiguous
            iriNamespace: https://example.com/ambiguous#
            ontologySources:
              - id: simple
                path: ontology/simple.ttl
                format: turtle
            """.trimIndent(),
        )
        root.resolve("ontology/simple.ttl").writeText(
            """
            @prefix ex: <https://example.com/ambiguous#> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            ex:ThingClass a owl:Class ; rdfs:label "Thing" .
            ex:thingIndividual a owl:NamedIndividual ; rdfs:label "Thing" .
            """.trimIndent(),
        )
        return root
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
}
