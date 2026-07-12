package com.entio.semantic

import com.entio.core.ApplyProposalResult
import com.entio.core.ChangeProposal
import com.entio.core.ChangeProposalStatus
import com.entio.core.ChangeSet
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.GraphChange
import com.entio.core.GraphChangeKind
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.core.RollbackResult
import com.entio.core.SemanticEquivalenceResult
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProposalApplierTest {
    private val projectLoader = ProjectLoader()
    private val proposalCreator = ProposalCreator()

    @Test
    fun approvedProposalWritesOnlyTargetOntologySource(): Unit {
        val fixture = createProjectFixture()
        val proposal = approvedProposal(fixture.projectRoot, labelChangeSet("Customer", "Customer"))
        val unrelatedBefore = fixture.notesPath.readText()

        val result = ProposalApplier().applyProposal(fixture.projectRoot, proposal)

        val applied = assertIs<ApplyProposalResult.Applied>(result)
        assertEquals(proposal.id, applied.proposalId)
        assertEquals(listOf(fixture.ontologyPath.toString()), applied.changedFiles)
        assertTrue(loadProject(fixture.projectRoot).graph.triples.hasLabel("Customer", "Customer"))
        assertEquals(unrelatedBefore, fixture.notesPath.readText())
    }

    @Test
    fun rejectedProposalDoesNotWriteFiles(): Unit {
        val fixture = createProjectFixture()
        val originalOntology = fixture.ontologyPath.readText()
        val proposal = approvedProposal(fixture.projectRoot, labelChangeSet("Customer", "Customer"))
            .copy(status = ChangeProposalStatus.Rejected)

        val result = ProposalApplier().applyProposal(fixture.projectRoot, proposal)

        val failure = assertIs<ApplyProposalResult.Failed>(result)
        assertEquals("Only approved proposals can be applied.", failure.reason)
        assertEquals(RollbackResult.NotRequired, failure.rollback)
        assertEquals(originalOntology, fixture.ontologyPath.readText())
    }

    @Test
    fun staleProposalIsBlockedBeforeWriting(): Unit {
        val fixture = createProjectFixture()
        val proposal = approvedProposal(fixture.projectRoot, labelChangeSet("Customer", "Customer"))
        val externallyChangedOntology = """
            @prefix ex: <https://example.com/> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            ex:Customer a rdfs:Class .
            ex:External a rdfs:Class .
        """.trimIndent()
        fixture.ontologyPath.writeText(externallyChangedOntology)

        val result = ProposalApplier().applyProposal(fixture.projectRoot, proposal)

        val failure = assertIs<ApplyProposalResult.Failed>(result)
        assertEquals("Proposal '${proposal.id}' is stale and cannot be applied.", failure.reason)
        assertEquals(RollbackResult.NotRequired, failure.rollback)
        assertEquals(externallyChangedOntology, fixture.ontologyPath.readText())
    }

    @Test
    fun temporarySerializationFailureDoesNotModifySource(): Unit {
        val fixture = createProjectFixture()
        val originalOntology = fixture.ontologyPath.readText()
        val proposal = approvedProposal(fixture.projectRoot, labelChangeSet("Customer", "Customer"))
        val applier = ProposalApplier(
            serializePreview = {
                EntioResult.Failure(
                    message = "Temporary Turtle preview file could not be written.",
                    issues = listOf(
                        ValidationIssue(
                            severity = ValidationSeverity.Error,
                            code = "temporary-turtle-write-failed",
                            message = "Temporary Turtle preview file could not be written.",
                            source = "preview",
                        ),
                    ),
                )
            },
        )

        val result = applier.applyProposal(fixture.projectRoot, proposal)

        val failure = assertIs<ApplyProposalResult.Failed>(result)
        assertEquals("Temporary Turtle preview file could not be written.", failure.reason)
        assertEquals(RollbackResult.NotRequired, failure.rollback)
        assertEquals(originalOntology, fixture.ontologyPath.readText())
    }

    @Test
    fun postSaveVerificationFailureRestoresPriorSource(): Unit {
        val fixture = createProjectFixture()
        val originalOntology = fixture.ontologyPath.readText()
        val proposal = approvedProposal(fixture.projectRoot, labelChangeSet("Customer", "Customer"))
        val applier = ProposalApplier(
            compareGraphs = { _, _ ->
                SemanticEquivalenceResult.NotEquivalent(
                    reason = "Saved graph did not match approved preview.",
                )
            },
        )

        val result = applier.applyProposal(fixture.projectRoot, proposal)

        val failure = assertIs<ApplyProposalResult.Failed>(result)
        assertEquals("Saved graph did not match approved preview.", failure.reason)
        val rollback = assertIs<RollbackResult.Restored>(failure.rollback)
        assertEquals(listOf(fixture.ontologyPath.toString()), rollback.restoredFiles)
        assertEquals(originalOntology, fixture.ontologyPath.readText())
    }

    @Test
    fun applicationReportsRollbackFailureWhenOriginalSourceCannotBeRestored(): Unit {
        val fixture = createProjectFixture()
        val proposal = approvedProposal(fixture.projectRoot, labelChangeSet("Customer", "Customer"))
        val applier = ProposalApplier(
            compareGraphs = { _, _ -> SemanticEquivalenceResult.NotEquivalent("Saved graph did not match preview.") },
            restoreSource = { _, _ -> RollbackResult.Failed("Injected rollback failure.") },
        )

        val result = applier.applyProposal(fixture.projectRoot, proposal)

        val failure = assertIs<ApplyProposalResult.Failed>(result)
        assertEquals("Saved graph did not match preview.", failure.reason)
        assertEquals(RollbackResult.Failed("Injected rollback failure."), failure.rollback)
    }

    @Test
    fun unrelatedFilesRemainUntouchedWhenApplicationFailsAfterWrite(): Unit {
        val fixture = createProjectFixture()
        val unrelatedBefore = fixture.notesPath.readText()
        val proposal = approvedProposal(fixture.projectRoot, labelChangeSet("Customer", "Customer"))
        val applier = ProposalApplier(
            compareGraphs = { _, _ -> SemanticEquivalenceResult.NotEquivalent("Saved graph did not match preview.") },
        )

        val result = applier.applyProposal(fixture.projectRoot, proposal)

        assertIs<ApplyProposalResult.Failed>(result)
        assertEquals(unrelatedBefore, fixture.notesPath.readText())
    }

    private fun approvedProposal(
        projectRoot: Path,
        changeSet: ChangeSet,
    ): ChangeProposal {
        val project = loadProject(projectRoot)
        val proposal = assertIs<EntioResult.Success<ChangeProposal>>(
            proposalCreator.createProposal(
                project = project,
                targetSourceId = "simple",
                changeSet = changeSet,
                id = "proposal-1",
                title = "Apply proposal",
            ),
        ).value

        return proposal.copy(status = ChangeProposalStatus.Approved)
    }

    private fun loadProject(projectRoot: Path): EntioProject =
        assertIs<EntioResult.Success<EntioProject>>(
            projectLoader.loadProject(projectRoot),
        ).value

    private fun labelChangeSet(
        localName: String,
        label: String,
    ): ChangeSet =
        ChangeSet(
            changes = listOf(
                GraphChange(
                    kind = GraphChangeKind.Addition,
                    triple = GraphTriple(
                        subject = Iri("https://example.com/$localName"),
                        predicate = Iri("http://www.w3.org/2000/01/rdf-schema#label"),
                        objectTerm = RdfLiteral(label),
                    ),
                ),
            ),
        )

    private fun createProjectFixture(): ProjectFixture {
        val projectRoot = Files.createTempDirectory("entio-apply")
        val ontologyDirectory = projectRoot.resolve("ontology")
        Files.createDirectories(ontologyDirectory)
        val ontologyPath = ontologyDirectory.resolve("simple.ttl")
        val notesPath = projectRoot.resolve("notes.md")

        projectRoot.resolve("entio.yaml").writeText(
            """
            name: simple-ontology
            ontologySources:
              - id: simple
                path: ontology/simple.ttl
                format: turtle
            """.trimIndent(),
        )
        ontologyPath.writeText(
            """
            @prefix ex: <https://example.com/> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            ex:Customer a rdfs:Class .
            """.trimIndent(),
        )
        notesPath.writeText("Unrelated file.\n")

        assertTrue(projectRoot.resolve("entio.yaml").exists())
        assertTrue(ontologyPath.exists())
        assertTrue(notesPath.exists())

        return ProjectFixture(
            projectRoot = projectRoot,
            ontologyPath = ontologyPath,
            notesPath = notesPath,
        )
    }

    private data class ProjectFixture(
        val projectRoot: Path,
        val ontologyPath: Path,
        val notesPath: Path,
    )

    private fun Set<GraphTriple>.hasLabel(
        localName: String,
        label: String,
    ): Boolean =
        any { triple ->
            triple.subjectResource == Iri("https://example.com/$localName") &&
                triple.predicate == Iri("http://www.w3.org/2000/01/rdf-schema#label") &&
                (triple.objectTerm as? RdfLiteral)?.lexicalForm == label
        }
}
