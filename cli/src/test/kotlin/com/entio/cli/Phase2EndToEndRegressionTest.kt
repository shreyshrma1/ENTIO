package com.entio.cli

import com.entio.core.ApplyProposalResult
import com.entio.core.ChangeProposal
import com.entio.core.ChangeProposalStatus
import com.entio.core.CreateClassEdit
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.core.RollbackResult
import com.entio.core.SemanticEquivalenceResult
import com.entio.diff.ProposalDiffGenerator
import com.entio.semantic.PreviewTurtleRoundTripVerifier
import com.entio.semantic.ProposalApplier
import com.entio.semantic.ProposalCreator
import com.entio.semantic.ProjectLoader
import com.entio.semantic.TypedOntologyEditTranslator
import com.entio.validation.ProjectValidator
import com.entio.validation.ProposalValidator
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

class Phase2EndToEndRegressionTest {
    private val projectLoader = ProjectLoader()
    private val editTranslator = TypedOntologyEditTranslator()
    private val proposalCreator = ProposalCreator()
    private val proposalDiffGenerator = ProposalDiffGenerator()
    private val equivalenceVerifier = PreviewTurtleRoundTripVerifier()
    private val projectValidator = ProjectValidator()
    private val proposalValidator = ProposalValidator()

    @Test
    fun copiedFixtureCoversTypedEditPreviewValidationApprovalApplyReloadAndEquivalence(): Unit {
        val fixture = copyExampleProject()
        val originalSource = fixture.ontologyPath.readText()
        val project = loadProject(fixture.projectRoot)
        val proposal = attachDiff(
            project = project,
            proposal = createProposal(project),
        )
        val preview = requireNotNull(proposal.preview)
        val equivalence = assertIs<EntioResult.Success<SemanticEquivalenceResult>>(
            equivalenceVerifier.verify(preview),
        ).value
        val validation = proposalValidator.validateProposal(
            proposal = proposal,
            currentProject = project,
            projectValidationReport = projectValidator.validateProject(fixture.projectRoot),
            semanticEquivalenceResult = equivalence,
        )

        assertEquals(SemanticEquivalenceResult.Equivalent, equivalence)
        assertTrue(validation.ok)
        assertTrue(proposal.diff!!.entries.isNotEmpty())
        assertTrue(proposal.sourceFileImpact!!.affectedPaths.contains(fixture.ontologyPath.toString()))
        assertEquals(originalSource, fixture.ontologyPath.readText())

        val applied = assertIs<ApplyProposalResult.Applied>(
            ProposalApplier().applyProposal(
                projectRoot = fixture.projectRoot,
                proposal = proposal.copy(
                    status = ChangeProposalStatus.Approved,
                    validationReport = validation,
                ),
            ),
        )
        assertEquals(listOf(fixture.ontologyPath.toString()), applied.changedFiles)

        val reloaded = loadProject(fixture.projectRoot)
        assertEquals(
            SemanticEquivalenceResult.Equivalent,
            equivalenceVerifier.compareSemanticEquivalence(preview.graph, reloaded.graph),
        )
        assertTrue(
            reloaded.graph.triples.any { triple ->
                triple.subjectResource == Iri("https://example.com/entio/simple#PurchaseOrder")
            },
        )
    }

    @Test
    fun rejectingCopiedFixtureLeavesSourceUnchanged(): Unit {
        val fixture = copyExampleProject()
        val originalSource = fixture.ontologyPath.readText()

        val result = runCli(
            "proposal-reject",
            fixture.projectRoot.toString(),
            "simple",
            "--class-iri",
            "https://example.com/entio/simple#PurchaseOrder",
            "--label",
            "Purchase order",
        )

        assertEquals(0, result.exitCode)
        assertTrue(result.out.contains("\"status\":\"rejected\""))
        assertEquals(originalSource, fixture.ontologyPath.readText())
    }

    @Test
    fun staleProposalIsBlockedOnCopiedFixture(): Unit {
        val fixture = copyExampleProject()
        val project = loadProject(fixture.projectRoot)
        val proposal = createProposal(project).copy(status = ChangeProposalStatus.Approved)
        val externallyChangedSource = fixture.ontologyPath.readText() + "\n# External change\n"
        fixture.ontologyPath.writeText(externallyChangedSource)

        val result = ProposalApplier().applyProposal(fixture.projectRoot, proposal)

        val failure = assertIs<ApplyProposalResult.Failed>(result)
        assertTrue(failure.reason.contains("stale"))
        assertEquals(RollbackResult.NotRequired, failure.rollback)
        assertEquals(externallyChangedSource, fixture.ontologyPath.readText())
    }

    @Test
    fun failedApplyRestoresCopiedFixture(): Unit {
        val fixture = copyExampleProject()
        val originalSource = fixture.ontologyPath.readText()
        val project = loadProject(fixture.projectRoot)
        val proposal = createProposal(project).copy(status = ChangeProposalStatus.Approved)
        val applier = ProposalApplier(
            compareGraphs = { _, _ ->
                SemanticEquivalenceResult.NotEquivalent("Saved graph did not match approved preview.")
            },
        )

        val result = applier.applyProposal(fixture.projectRoot, proposal)

        val failure = assertIs<ApplyProposalResult.Failed>(result)
        assertEquals("Saved graph did not match approved preview.", failure.reason)
        assertIs<RollbackResult.Restored>(failure.rollback)
        assertEquals(originalSource, fixture.ontologyPath.readText())
    }

    private fun createProposal(project: EntioProject): ChangeProposal {
        val changeSet = assertIs<EntioResult.Success<com.entio.core.ChangeSet>>(
            editTranslator.translate(
                CreateClassEdit(
                    classIri = Iri("https://example.com/entio/simple#PurchaseOrder"),
                    label = RdfLiteral("Purchase order"),
                ),
            ),
        ).value

        return assertIs<EntioResult.Success<ChangeProposal>>(
            proposalCreator.createProposal(
                project = project,
                targetSourceId = "simple",
                changeSet = changeSet,
                id = "phase-2-e2e-proposal",
                title = "Create purchase order class",
            ),
        ).value
    }

    private fun attachDiff(
        project: EntioProject,
        proposal: ChangeProposal,
    ): ChangeProposal = assertIs<EntioResult.Success<ChangeProposal>>(
        proposalDiffGenerator.attachDiff(proposal, project.graph),
    ).value

    private fun loadProject(projectRoot: Path): EntioProject =
        assertIs<EntioResult.Success<EntioProject>>(
            projectLoader.loadProject(projectRoot),
        ).value

    private fun copyExampleProject(): ProjectFixture {
        val sourceRoot = repositoryRoot().resolve("examples/simple-ontology")
        val targetRoot = Files.createTempDirectory("entio-phase-2-e2e")
        val paths = Files.walk(sourceRoot)

        try {
            paths.forEach { sourcePath ->
                val targetPath = targetRoot.resolve(sourceRoot.relativize(sourcePath).toString())
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath)
                } else {
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        } finally {
            paths.close()
        }

        return ProjectFixture(
            projectRoot = targetRoot,
            ontologyPath = targetRoot.resolve("ontology/simple.ttl"),
        )
    }

    private fun repositoryRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        while (true) {
            if (Files.isRegularFile(current.resolve("examples/simple-ontology/entio.yaml"))) {
                return current
            }
            current = current.parent ?: error("Could not locate the Entio repository root.")
        }
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

    private data class ProjectFixture(
        val projectRoot: Path,
        val ontologyPath: Path,
    )

    private data class CliRun(
        val exitCode: Int,
        val out: String,
        val err: String,
    )
}
