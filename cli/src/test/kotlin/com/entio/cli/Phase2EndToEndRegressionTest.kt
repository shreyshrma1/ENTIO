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
import kotlin.test.assertFalse
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
    fun copiedFixtureSupportsAllPhase25CliEditsThroughApplyAndReload(): Unit {
        val fixture = copyPhase25Fixture()
        val originalSource = fixture.ontologyPath.readText()

        val propertyPreview = runCli(
            "proposal-preview",
            fixture.projectRoot.toString(),
            "simple",
            "--edit",
            "create-object-property",
            "--property-iri",
            "https://example.com/entio/simple#hasAccount",
            "--label",
            "has account",
            "--domain-iri",
            "https://example.com/entio/simple#Customer",
            "--range-iri",
            "https://example.com/entio/simple#Account",
        )
        assertEquals(0, propertyPreview.exitCode, propertyPreview.out)
        assertTrue(propertyPreview.out.contains("\"status\":\"valid\""), propertyPreview.out)
        assertTrue(propertyPreview.out.contains("\"entryCount\":"), propertyPreview.out)
        assertTrue(propertyPreview.out.contains(fixture.ontologyPath.toString()), propertyPreview.out)
        assertEquals(originalSource, fixture.ontologyPath.readText())

        assertApplied(
            fixture,
            "--edit", "create-object-property",
            "--property-iri", "https://example.com/entio/simple#hasAccount",
            "--label", "has account",
            "--domain-iri", "https://example.com/entio/simple#Customer",
            "--range-iri", "https://example.com/entio/simple#Account",
            "--proposal-id", "phase25-object-property",
        )
        assertApplied(
            fixture,
            "--edit", "create-datatype-property",
            "--property-iri", "https://example.com/entio/simple#accountCode",
            "--datatype", "http://www.w3.org/2001/XMLSchema#integer",
            "--proposal-id", "phase25-datatype-property",
        )
        assertApplied(
            fixture,
            "--edit", "create-individual",
            "--individual-iri", "https://example.com/entio/simple#bob",
            "--type-iri", "https://example.com/entio/simple#Customer",
            "--label", "Bob",
            "--proposal-id", "phase25-individual",
        )
        assertApplied(
            fixture,
            "--edit", "assign-individual-type",
            "--individual-iri", "https://example.com/entio/simple#alice",
            "--type-iri", "https://example.com/entio/simple#Account",
            "--proposal-id", "phase25-type-assignment",
        )
        assertApplied(
            fixture,
            "--edit", "add-object-property-assertion",
            "--subject-iri", "https://example.com/entio/simple#alice",
            "--property-iri", "https://example.com/entio/simple#hasAccount",
            "--object-iri", "https://example.com/entio/simple#account",
            "--proposal-id", "phase25-object-assertion",
        )
        assertApplied(
            fixture,
            "--edit", "add-datatype-property-assertion",
            "--subject-iri", "https://example.com/entio/simple#alice",
            "--property-iri", "https://example.com/entio/simple#accountCode",
            "--value", "42",
            "--datatype", "http://www.w3.org/2001/XMLSchema#integer",
            "--proposal-id", "phase25-datatype-assertion",
        )
        assertApplied(
            fixture,
            "--edit", "add-superclass",
            "--class-iri", "https://example.com/entio/simple#Customer",
            "--superclass-iri", "https://example.com/entio/simple#Entity",
            "--proposal-id", "phase25-superclass-add",
        )
        assertApplied(
            fixture,
            "--edit", "set-entity-label",
            "--entity-iri", "https://example.com/entio/simple#Customer",
            "--label", "Client",
            "--replace-existing",
            "--proposal-id", "phase25-label-replacement",
        )
        assertApplied(
            fixture,
            "--edit", "remove-superclass",
            "--class-iri", "https://example.com/entio/simple#Customer",
            "--superclass-iri", "https://example.com/entio/simple#Entity",
            "--proposal-id", "phase25-superclass-remove",
        )

        val reloaded = loadProject(fixture.projectRoot)
        assertTrue(reloaded.graph.triples.any { triple -> triple.subjectResource == Iri("https://example.com/entio/simple#hasAccount") })
        assertTrue(reloaded.graph.triples.any { triple -> triple.subjectResource == Iri("https://example.com/entio/simple#bob") })
        assertTrue(reloaded.graph.triples.any { triple -> triple.subjectResource == Iri("https://example.com/entio/simple#alice") && triple.predicate.value.endsWith("accountCode") })
        assertTrue(
            reloaded.graph.triples.any { triple ->
                val literal = triple.objectTerm as? RdfLiteral
                triple.subjectResource == Iri("https://example.com/entio/simple#Customer") &&
                    literal?.lexicalForm == "Client"
            },
        )
        assertFalse(reloaded.graph.triples.any { triple -> triple.subjectResource == Iri("https://example.com/entio/simple#Customer") && triple.predicate.value.endsWith("subClassOf") })
    }

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

    private fun copyPhase25Fixture(): ProjectFixture {
        val fixture = copyExampleProject()
        fixture.ontologyPath.writeText(
            """
                @prefix ex: <https://example.com/entio/simple#> .
                @prefix owl: <http://www.w3.org/2002/07/owl#> .
                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

                ex:Customer a owl:Class ; rdfs:label "Customer" .
                ex:Account a owl:Class .
                ex:Entity a owl:Class .
                ex:alice a owl:NamedIndividual, ex:Customer .
                ex:account a owl:NamedIndividual, ex:Account .
                ex:ownsAccount a owl:ObjectProperty ;
                    rdfs:domain ex:Customer ;
                    rdfs:range ex:Account .
                ex:accountNumber a owl:DatatypeProperty ;
                    rdfs:domain ex:Customer ;
                    rdfs:range xsd:integer .
            """.trimIndent() + "\n",
        )
        return fixture
    }

    private fun assertApplied(
        fixture: ProjectFixture,
        vararg options: String,
    ): Unit {
        val result = runCli(
            "proposal-apply",
            fixture.projectRoot.toString(),
            "simple",
            *options,
        )
        assertEquals(0, result.exitCode, result.out)
        assertTrue(result.out.contains("\"status\":\"applied\""), result.out)
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
