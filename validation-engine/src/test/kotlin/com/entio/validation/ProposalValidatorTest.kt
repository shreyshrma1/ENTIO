package com.entio.validation

import com.entio.core.ChangeProposal
import com.entio.core.ChangeProposalStatus
import com.entio.core.ChangeSet
import com.entio.core.EntioProject
import com.entio.core.EntioProjectConfig
import com.entio.core.EntioResult
import com.entio.core.GraphChange
import com.entio.core.GraphChangeKind
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.OntologyFormat
import com.entio.core.OntologySourceReference
import com.entio.core.RdfLiteral
import com.entio.core.ResolvedOntologySource
import com.entio.core.SemanticEquivalenceResult
import com.entio.core.ValidationIssue
import com.entio.core.ValidationReport
import com.entio.core.ValidationSeverity
import com.entio.core.ValidationStatus
import com.entio.semantic.ProposalCreator
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProposalValidatorTest {
    private val proposalCreator = ProposalCreator()
    private val validator = ProposalValidator()

    @Test
    fun returnsValidReportForValidProposal(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val proposal = proposal(project)

        val report = validator.validateProposal(proposal, project)

        assertEquals(ValidationStatus.Valid, report.status)
        assertTrue(report.ok)
        assertEquals(emptyList(), report.issues)
    }

    @Test
    fun emptyChangeSetsRemainRejectedByCoreContract(): Unit {
        assertFailsWith<IllegalArgumentException> {
            ChangeSet(changes = emptyList())
        }
    }

    @Test
    fun duplicateAdditionReturnsDeterministicIssue(): Unit {
        val source = tempSource()
        val existingTriple = typeTriple("Customer")
        val project = project(source = source, graph = GraphState(setOf(existingTriple)))
        val proposal = proposal(
            project = project,
            changeSet = ChangeSet(
                changes = listOf(GraphChange(GraphChangeKind.Addition, existingTriple)),
            ),
            allowPreviewFailure = true,
        )

        val report = validator.validateProposal(proposal, project)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertFalse(report.ok)
        assertEquals("duplicate-triple-addition", report.issues.single().code)
        assertEquals("changeSet.changes[0]", report.issues.single().source)
    }

    @Test
    fun missingRemovalReturnsDeterministicIssue(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val proposal = proposal(
            project = project,
            changeSet = ChangeSet(
                changes = listOf(GraphChange(GraphChangeKind.Removal, labelTriple("Customer", "Customer"))),
            ),
            allowPreviewFailure = true,
        )

        val report = validator.validateProposal(proposal, project)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertEquals("missing-triple-removal", report.issues.single().code)
        assertEquals("changeSet.changes[0]", report.issues.single().source)
    }

    @Test
    fun staleProposalReturnsDeterministicIssue(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val proposal = proposal(project)
        source.path.writeText("@prefix ex: <https://example.com/> .\nex:Account a ex:Class .\n")

        val report = validator.validateProposal(proposal, project)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertEquals("stale-proposal-baseline", report.issues.single().code)
        assertEquals(proposal.id, report.issues.single().source)
    }

    @Test
    fun missingTargetSourceReturnsDeterministicIssue(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val proposal = proposal(project).copy(targetSourceId = "missing")

        val report = validator.validateProposal(proposal, project)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertEquals(
            listOf("missing-target-source"),
            report.issues.map { issue -> issue.code },
        )
    }

    @Test
    fun missingPreviewReturnsDeterministicIssue(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val proposal = proposal(project).copy(preview = null)

        val report = validator.validateProposal(proposal, project)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertEquals("missing-proposal-preview", report.issues.single().code)
        assertEquals(proposal.id, report.issues.single().source)
    }

    @Test
    fun semanticEquivalenceFailureReturnsDeterministicIssueWhenAvailable(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val proposal = proposal(project)

        val report = validator.validateProposal(
            proposal = proposal,
            currentProject = project,
            semanticEquivalenceResult = SemanticEquivalenceResult.NotEquivalent("Preview graph changed after round trip."),
        )

        assertEquals(ValidationStatus.Invalid, report.status)
        assertEquals("semantic-equivalence-failed", report.issues.single().code)
        assertEquals("Preview graph changed after round trip.", report.issues.single().message)
    }

    @Test
    fun verificationFailedStatusReturnsDeterministicIssueWhenAvailable(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val proposal = proposal(project).copy(status = ChangeProposalStatus.VerificationFailed)

        val report = validator.validateProposal(proposal, project)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertEquals("proposal-verification-failed", report.issues.single().code)
    }

    @Test
    fun combinesExistingProjectValidationIssues(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val proposal = proposal(project)
        val projectReport = ValidationReport(
            status = ValidationStatus.Invalid,
            issues = listOf(
                ValidationIssue(
                    severity = ValidationSeverity.Error,
                    code = "invalid-turtle",
                    message = "Ontology source could not be parsed as Turtle.",
                    source = "simple",
                ),
            ),
        )

        val report = validator.validateProposal(
            proposal = proposal,
            currentProject = project,
            projectValidationReport = projectReport,
        )

        assertEquals(ValidationStatus.Invalid, report.status)
        assertEquals("invalid-turtle", report.issues.single().code)
    }

    @Test
    fun returnsIssuesInStableSortedOrder(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val proposal = proposal(project)
            .copy(preview = null, status = ChangeProposalStatus.VerificationFailed)

        val first = validator.validateProposal(proposal, project)
        val second = validator.validateProposal(proposal, project)

        assertEquals(first.issues, second.issues)
        assertEquals(
            listOf("missing-proposal-preview", "proposal-verification-failed"),
            first.issues.map { issue -> issue.code },
        )
    }

    private fun proposal(
        project: EntioProject,
        changeSet: ChangeSet = ChangeSet(
            changes = listOf(GraphChange(GraphChangeKind.Addition, labelTriple("Customer", "Customer"))),
        ),
        allowPreviewFailure: Boolean = false,
    ): ChangeProposal {
        val result = proposalCreator.createProposal(
            project = project,
            targetSourceId = "simple",
            changeSet = changeSet,
            id = "proposal-1",
            title = "Add customer label",
        )

        return when (result) {
            is EntioResult.Success -> result.value
            is EntioResult.Failure -> if (allowPreviewFailure) {
                proposal(project).copy(changeSet = changeSet)
            } else {
                error(result.message)
            }
        }
    }

    private fun project(
        source: ResolvedOntologySource,
        graph: GraphState = GraphState(setOf(typeTriple("Customer"))),
    ): EntioProject =
        EntioProject(
            config = EntioProjectConfig(
                name = "simple",
                ontologySources = listOf(
                    OntologySourceReference(
                        id = source.id,
                        path = source.path.fileName.toString(),
                        format = OntologyFormat.Turtle,
                    ),
                ),
            ),
            resolvedSources = listOf(source),
            ontologies = emptyList(),
            symbols = emptyList(),
            graph = graph,
        )

    private fun tempSource(
        id: String = "simple",
        content: String = "@prefix ex: <https://example.com/> .\nex:Customer a ex:Class .\n",
    ): ResolvedOntologySource {
        val directory = Files.createTempDirectory("entio-proposal-validation")
        val path = directory.resolve("$id.ttl")
        path.writeText(content)
        return ResolvedOntologySource(
            id = id,
            path = path,
            format = OntologyFormat.Turtle,
        )
    }

    private fun typeTriple(localName: String): GraphTriple =
        GraphTriple(
            subject = Iri("https://example.com/$localName"),
            predicate = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
            objectTerm = Iri("http://www.w3.org/2000/01/rdf-schema#Class"),
        )

    private fun labelTriple(localName: String, label: String): GraphTriple =
        GraphTriple(
            subject = Iri("https://example.com/$localName"),
            predicate = Iri("http://www.w3.org/2000/01/rdf-schema#label"),
            objectTerm = RdfLiteral(label),
        )
}
