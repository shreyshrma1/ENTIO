package com.entio.semantic

import com.entio.core.ConsistencyStatus
import com.entio.core.EntioProject
import com.entio.core.EntioProjectConfig
import com.entio.core.EntioResult
import com.entio.core.FactOrigin
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.InferredGraphState
import com.entio.core.InferredReadKind
import com.entio.core.InferredReadState
import com.entio.core.Iri
import com.entio.core.LoadedOntology
import com.entio.core.OntologyFormat
import com.entio.core.OntologySourceReference
import com.entio.core.ReasoningClassRelationship
import com.entio.core.ReasoningFingerprints
import com.entio.core.ReasoningIndividualType
import com.entio.core.ReasoningPropertyRelationship
import com.entio.core.ReasoningResult
import com.entio.core.ReasoningRunMetadata
import com.entio.core.ReasoningRunStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InferredFactsReadServiceTest {
    private val parser = OntologyParser()
    private val service = InferredFactsReadService()
    private val customer = Iri("https://example.com/Customer")
    private val party = Iri("https://example.com/Party")
    private val account = Iri("https://example.com/Account")
    private val thing = Iri("https://example.com/Thing")
    private val shrey = Iri("https://example.com/Shrey")
    private val account101 = Iri("https://example.com/Account101")
    private val ownsAccount = Iri("https://example.com/ownsAccount")

    @Test
    fun projectsSupportedFactsAndEffectivePropertySchema(): Unit {
        val project = project()
        val overlay = service.project(
            project = project,
            assertedGraph = project.graph,
            reasoningResultId = "job-1",
            reasoning = reasoning(),
            graphState = InferredGraphState.Applied,
        )

        assertEquals(InferredReadState.Current, overlay.state)
        assertEquals(
            setOf(
                InferredReadKind.SubclassRelationship,
                InferredReadKind.IndividualType,
                InferredReadKind.ObjectPropertyAssertion,
                InferredReadKind.EffectiveDomain,
                InferredReadKind.EffectiveRange,
            ),
            overlay.facts.map { it.kind }.toSet(),
        )
        assertTrue(overlay.facts.all { it.graphState == InferredGraphState.Applied })
        assertTrue(overlay.facts.all { it.placements.isNotEmpty() })
        assertTrue(overlay.facts.any {
            it.kind == InferredReadKind.EffectiveDomain && it.subject == ownsAccount && it.objectValue == party
        })
        assertTrue(overlay.facts.any {
            it.kind == InferredReadKind.EffectiveRange && it.subject == ownsAccount && it.objectValue == thing
        })
    }

    @Test
    fun suppressesAssertedDuplicatesAndKeepsStableOrder(): Unit {
        val base = project()
        val assertedType = GraphTriple(shrey, RDF_TYPE, customer)
        val assertedProject = base.copy(graph = GraphState(base.graph.triples + assertedType))

        val first = service.project(assertedProject, assertedProject.graph, "job-1", reasoning(), InferredGraphState.Applied)
        val second = service.project(assertedProject, assertedProject.graph, "job-1", reasoning(), InferredGraphState.Applied)

        assertEquals(first, second)
        assertTrue(first.facts.none { it.kind == InferredReadKind.IndividualType })
        assertEquals(first.facts.map { it.semanticFactKey }.distinct(), first.facts.map { it.semanticFactKey })
    }

    @Test
    fun reportsIncompleteResultsWithoutFactsAndBoundsCurrentResults(): Unit {
        val project = project()
        val incomplete = reasoning().copy(
            metadata = reasoning().metadata.copy(status = ReasoningRunStatus.Incomplete),
        )
        val failed = service.project(project, project.graph, "job", incomplete, InferredGraphState.Applied)
        assertEquals(InferredReadState.Failed, failed.state)
        assertTrue(failed.facts.isEmpty())

        val bounded = service.project(project, project.graph, "job", reasoning(), InferredGraphState.Applied, limit = 1)
        assertEquals(1, bounded.facts.size)
        assertTrue(bounded.truncated)
        assertTrue(bounded.totalFactCount > bounded.facts.size)
    }

    @Test
    fun proposalFactsRequireAndRetainProposalFingerprint(): Unit {
        val project = project()
        val overlay = service.project(
            project,
            project.graph,
            "proposal-job",
            reasoning(),
            InferredGraphState.Proposal,
            proposalFingerprint = "proposal-fingerprint",
        )

        assertEquals("proposal-fingerprint", overlay.proposalFingerprint)
        assertTrue(overlay.facts.all { it.proposalFingerprint == "proposal-fingerprint" })
    }

    private fun reasoning(): ReasoningResult = ReasoningResult(
        metadata = ReasoningRunMetadata(
            status = ReasoningRunStatus.Completed,
            reasonerName = "test",
            reasonerVersion = "1",
            owlApiVersion = "1",
            fingerprints = ReasoningFingerprints("graph-fingerprint", "imports", "config"),
            importClosureComplete = true,
        ),
        consistency = ConsistencyStatus.Consistent,
        classRelationships = listOf(
            ReasoningClassRelationship(customer, party, FactOrigin.Inferred, "simple"),
            ReasoningClassRelationship(account, thing, FactOrigin.Inferred, "simple"),
        ),
        individualTypes = listOf(
            ReasoningIndividualType(shrey, customer, FactOrigin.Inferred, "simple"),
        ),
        propertyRelationships = listOf(
            ReasoningPropertyRelationship(shrey, ownsAccount, account101, FactOrigin.Inferred, "simple"),
        ),
    )

    private fun project(): EntioProject {
        val ontology = assertIs<EntioResult.Success<LoadedOntology>>(
            parser.parse(
                SemanticEngineTestFixtures.resolvedSource(
                    """
                    @prefix ex: <https://example.com/> .
                    @prefix owl: <http://www.w3.org/2002/07/owl#> .
                    @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                    ex:Party a owl:Class .
                    ex:Customer a owl:Class .
                    ex:Account a owl:Class .
                    ex:Thing a owl:Class .
                    ex:ownsAccount a owl:ObjectProperty ; rdfs:domain ex:Customer ; rdfs:range ex:Account .
                    ex:Shrey a owl:NamedIndividual .
                    ex:Account101 a owl:NamedIndividual .
                    """.trimIndent(),
                    "simple",
                ),
            ),
        ).value
        return EntioProject(
            config = EntioProjectConfig(
                "inferred-read",
                listOf(OntologySourceReference("simple", ontology.source.path.toString(), OntologyFormat.Turtle)),
            ),
            resolvedSources = listOf(ontology.source),
            ontologies = listOf(ontology),
            symbols = emptyList(),
            graph = ontology.graph,
        )
    }

    private companion object {
        private val RDF_TYPE = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
    }
}
