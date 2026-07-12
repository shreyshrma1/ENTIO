package com.entio.semantic

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
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProposalCreatorTest {
    private val proposalCreator = ProposalCreator()

    @Test
    fun createsProposalWithTargetSourceBaselinePreviewAndFileImpact(): Unit {
        val source = tempSource()
        val existingTriple = typeTriple("Customer")
        val addedTriple = labelTriple("Customer", "Customer")
        val project = project(source = source, graph = GraphState(setOf(existingTriple)))
        val changeSet = ChangeSet(
            changes = listOf(GraphChange(GraphChangeKind.Addition, addedTriple)),
        )

        val proposal = createProposal(project, changeSet)

        assertEquals("proposal-1", proposal.id)
        assertEquals("Add customer label", proposal.title)
        assertEquals("simple", proposal.targetSourceId)
        assertEquals(ChangeProposalStatus.Previewed, proposal.status)
        assertEquals(changeSet, proposal.changeSet)
        assertEquals("simple", proposal.baseline.targetSourceId)
        assertEquals(source.path.toString(), proposal.baseline.targetSourcePath)
        assertTrue(proposal.baseline.targetSourceFingerprint.isNotBlank())
        assertTrue(proposal.baseline.graphFingerprint.isNotBlank())
        assertTrue(proposal.baseline.projectFingerprint.isNotBlank())
        assertEquals(setOf(existingTriple, addedTriple), proposal.preview?.graph?.triples)
        assertEquals(listOf(source.path.toString()), proposal.sourceFileImpact?.affectedPaths)
    }

    @Test
    fun detectsUnchangedBaselineAsCurrent(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val proposal = createProposal(project)

        val current = assertIs<EntioResult.Success<Boolean>>(
            proposalCreator.isCurrent(proposal, project),
        ).value

        assertTrue(current)
    }

    @Test
    fun detectsChangedTargetFileAsStale(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val proposal = createProposal(project)
        source.path.writeText("@prefix ex: <https://example.com/> .\nex:Account a ex:Class .\n")

        val current = assertIs<EntioResult.Success<Boolean>>(
            proposalCreator.isCurrent(proposal, project),
        ).value

        assertFalse(current)
    }

    @Test
    fun detectsChangedGraphAsStale(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val proposal = createProposal(project)
        val changedProject = project(
            source = source,
            graph = GraphState(
                triples = project.graph.triples + labelTriple("Account", "Account"),
            ),
        )

        val current = assertIs<EntioResult.Success<Boolean>>(
            proposalCreator.isCurrent(proposal, changedProject),
        ).value

        assertFalse(current)
    }

    @Test
    fun ignoresUnrelatedProjectFilesWhenBaselineInputsAreUnchanged(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val proposal = createProposal(project)
        source.path.parent.resolve("notes.md").writeText("Unrelated local note.\n")

        val current = assertIs<EntioResult.Success<Boolean>>(
            proposalCreator.isCurrent(proposal, project),
        ).value

        assertTrue(current)
    }

    @Test
    fun failsWhenTargetSourceIsMissing(): Unit {
        val source = tempSource()
        val project = project(source = source)
        val changeSet = ChangeSet(
            changes = listOf(GraphChange(GraphChangeKind.Addition, labelTriple("Customer", "Customer"))),
        )

        val failure = assertIs<EntioResult.Failure>(
            proposalCreator.createProposal(
                project = project,
                targetSourceId = "missing",
                changeSet = changeSet,
                id = "proposal-1",
                title = "Missing target",
            ),
        )

        assertEquals("Target ontology source 'missing' was not found.", failure.message)
        assertEquals("missing-target-source", failure.issues.single().code)
    }

    private fun createProposal(
        project: EntioProject,
        changeSet: ChangeSet = ChangeSet(
            changes = listOf(GraphChange(GraphChangeKind.Addition, labelTriple("Customer", "Customer"))),
        ),
    ): ChangeProposal =
        assertIs<EntioResult.Success<ChangeProposal>>(
            proposalCreator.createProposal(
                project = project,
                targetSourceId = "simple",
                changeSet = changeSet,
                id = "proposal-1",
                title = "Add customer label",
            ),
        ).value

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
        val directory = Files.createTempDirectory("entio-proposal")
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
