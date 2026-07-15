package com.entio.semantic

import com.entio.core.EntioProject
import com.entio.core.EntioProjectConfig
import com.entio.core.EntioResult
import com.entio.core.ExternalDependency
import com.entio.core.ExternalDependencyCategory
import com.entio.core.ExternalDependencyClosure
import com.entio.core.ExternalDependencyRequirement
import com.entio.core.ExternalDependencySelection
import com.entio.core.ExternalDependencySet
import com.entio.core.ExternalDependencyVisibility
import com.entio.core.ExternalOntologyMaturity
import com.entio.core.ExternalProposalIntent
import com.entio.core.GraphState
import com.entio.core.Iri
import com.entio.core.OntologyFormat
import com.entio.core.OntologySourceReference
import com.entio.core.ResolvedOntologySource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExternalProposalPreparerTest {
    @Test
    fun preparesBaselineAwareProposalWithoutMutatingSource(): Unit {
        val directory = Files.createTempDirectory("entio-external-proposal")
        val sourcePath = directory.resolve("simple.ttl")
        val original = "@prefix ex: <https://example.com/> .\nex:Customer a ex:Class .\n"
        sourcePath.writeText(original)
        val source = ResolvedOntologySource("simple", sourcePath, OntologyFormat.Turtle)
        val targetOntology = Iri("https://example.com/entio/simple")
        val module = Iri("https://spec.edmcouncil.org/fibo/ontology/FND/Parties/Parties/")
        val project = EntioProject(
            config = EntioProjectConfig(
                name = "simple",
                ontologySources = listOf(
                    OntologySourceReference("simple", "simple.ttl", OntologyFormat.Turtle),
                ),
            ),
            resolvedSources = listOf(source),
            ontologies = emptyList(),
            symbols = emptyList(),
            graph = GraphState(),
        )
        val intent = ExternalProposalIntent.ReuseExternalClass(
            classIri = Iri("https://spec.edmcouncil.org/fibo/ontology/FND/Parties/Parties/Party"),
            sourceId = "fibo",
            dependencies = ExternalDependencySet(
                dependencies = listOf(
                    ExternalDependency(
                        category = ExternalDependencyCategory.SourceOntology,
                        requirement = ExternalDependencyRequirement.Required,
                        closure = ExternalDependencyClosure.Direct,
                        visibility = ExternalDependencyVisibility.UserVisible,
                        selection = ExternalDependencySelection.NewlySelected,
                        reason = "approved",
                        externalIri = module,
                        sourceModule = module,
                        maturity = ExternalOntologyMaturity.Release,
                    ),
                ),
            ),
        )

        val result = ExternalProposalPreparer().prepare(
            project = project,
            targetSourceId = "simple",
            targetOntologyIri = targetOntology,
            intent = intent,
            id = "proposal-1",
            title = "Reuse FIBO party",
        )
        val proposal = assertIs<EntioResult.Success<com.entio.core.ChangeProposal>>(result).value

        assertEquals("proposal-1", proposal.id)
        assertTrue(proposal.preview != null)
        assertTrue(proposal.baseline.projectFingerprint.isNotBlank())
        assertTrue(proposal.preview!!.graph.triples.any { it.objectTerm == module })
        assertEquals(original, sourcePath.readText())
    }
}
