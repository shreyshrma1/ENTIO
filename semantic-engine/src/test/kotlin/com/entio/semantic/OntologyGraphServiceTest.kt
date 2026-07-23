package com.entio.semantic

import com.entio.core.EntioProject
import com.entio.core.EntioProjectConfig
import com.entio.core.EntioResult
import com.entio.core.GraphState
import com.entio.core.InferredFactPlacement
import com.entio.core.InferredFactsOverlay
import com.entio.core.InferredGraphState
import com.entio.core.InferredReadFact
import com.entio.core.InferredReadKind
import com.entio.core.InferredReadState
import com.entio.core.Iri
import com.entio.core.LoadedOntology
import com.entio.core.OntologyFormat
import com.entio.core.OntologyGraphEdgeKind
import com.entio.core.OntologyGraphExpansionCategory
import com.entio.core.OntologyGraphInitialQuery
import com.entio.core.OntologyGraphNeighborhoodQuery
import com.entio.core.OntologyGraphNodeId
import com.entio.core.OntologyGraphNodeKind
import com.entio.core.OntologyGraphProvenance
import com.entio.core.OntologySourceReference
import com.entio.core.SemanticFactKey
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs

class OntologyGraphServiceTest {
    private val parser = OntologyParser()
    private val service = OntologyGraphService()

    @Test
    fun extractsSupportedLocalNodesAndAllAssertedEdgeKinds(): Unit {
        val project = project("simple", ontology())
        val customer = OntologyGraphNodeId("simple", Iri("https://example.com/Customer"))
        val shrey = OntologyGraphNodeId("simple", Iri("https://example.com/Shrey"))
        val property = OntologyGraphNodeId("simple", Iri("https://example.com/receivedInvoice"))
        val pages = listOf(customer, shrey, property).map { seed ->
            service.initial(project, OntologyGraphInitialQuery(setOf("simple"), seed = seed))
        }
        val overview = service.initial(project, OntologyGraphInitialQuery(setOf("simple")))

        assertEquals(
            setOf(OntologyGraphNodeKind.Class, OntologyGraphNodeKind.ObjectProperty, OntologyGraphNodeKind.DatatypeProperty, OntologyGraphNodeKind.Individual),
            overview.nodes.map { it.kind }.toSet(),
        )
        assertEquals(
            setOf(OntologyGraphEdgeKind.SubclassOf, OntologyGraphEdgeKind.Domain, OntologyGraphEdgeKind.Range, OntologyGraphEdgeKind.Type, OntologyGraphEdgeKind.ObjectAssertion),
            pages.flatMap { it.edges }.map { it.kind }.toSet(),
        )
        assertTrue(pages.flatMap { it.nodes }.none { it.id.entityIri.value.endsWith("note") })
        assertTrue(pages.flatMap { it.nodes }.none { it.id.entityIri.value.startsWith("http://www.w3.org/2001/XMLSchema") })
        pages.forEach { page ->
            assertTrue(page.edges.all { edge -> page.nodes.any { it.id == edge.source } && page.nodes.any { it.id == edge.target } })
        }
    }

    @Test
    fun returnsStableCategoryNeighborhoodsWithoutMutatingProject(): Unit {
        val project = project("simple", ontology())
        val before = project.graph
        val customer = OntologyGraphNodeId("simple", Iri("https://example.com/Customer"))
        val query = OntologyGraphNeighborhoodQuery(
            sourceIds = setOf("simple"),
            entity = customer,
            categories = OntologyGraphExpansionCategory.entries.toSet(),
        )

        val first = service.neighborhood(project, query)
        val second = service.neighborhood(project, query)

        assertEquals(first, second)
        assertEquals(before, project.graph)
        assertTrue(first.edges.isNotEmpty())
    }

    @Test
    fun layersInferredEdgesWithoutChangingAssertedOverviewPlacement(): Unit {
        val project = project("simple", ontology())
        val asserted = service.initial(project, OntologyGraphInitialQuery(setOf("simple")))
        val overlay = InferredFactsOverlay(
            graphState = InferredGraphState.Applied,
            state = InferredReadState.Current,
            facts = listOf(
                InferredReadFact(
                    semanticFactKey = SemanticFactKey("entio-semantic-fact-v1:${"a".repeat(64)}"),
                    subject = Iri("https://example.com/Customer"),
                    predicate = Iri("http://www.w3.org/2000/01/rdf-schema#subClassOf"),
                    objectValue = Iri("https://example.com/Invoice"),
                    kind = InferredReadKind.SubclassRelationship,
                    placements = setOf(InferredFactPlacement.ClassSuperclasses),
                    graphState = InferredGraphState.Applied,
                    reasoningResultId = "job",
                    graphFingerprint = "fingerprint",
                    sourceId = "simple",
                ),
            ),
            graphFingerprint = "fingerprint",
        )
        val withInference = service.initial(project, OntologyGraphInitialQuery(setOf("simple")), listOf(overlay))
        val customer = OntologyGraphNodeId("simple", Iri("https://example.com/Customer"))
        val neighborhood = service.neighborhood(
            project,
            OntologyGraphNeighborhoodQuery(
                setOf("simple"),
                customer,
                setOf(OntologyGraphExpansionCategory.ClassHierarchy),
            ),
            listOf(overlay),
        )

        assertEquals(asserted.nodes.map { it.id }, withInference.nodes.map { it.id })
        assertTrue(neighborhood.edges.any {
            it.provenance == OntologyGraphProvenance.Inferred &&
                it.inferredGraphState == InferredGraphState.Applied
        })
        assertEquals(InferredReadState.Current, neighborhood.inferredOverlays.single().state)
    }

    @Test
    fun usesSameSourceThenUniqueCrossSourceAndOmitsAmbiguousTargets(): Unit {
        val sourceA = projectOntology("a", """
            @prefix ex: <https://example.com/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            ex:Local a owl:Class . ex:Child a owl:Class ; rdfs:subClassOf ex:Local, ex:Unique, ex:Shared .
        """)
        val sourceB = projectOntology("b", """
            @prefix ex: <https://example.com/> . @prefix owl: <http://www.w3.org/2002/07/owl#> .
            ex:Unique a owl:Class . ex:Shared a owl:Class .
        """)
        val sourceC = projectOntology("c", """
            @prefix ex: <https://example.com/> . @prefix owl: <http://www.w3.org/2002/07/owl#> .
            ex:Shared a owl:Class .
        """)
        val project = combinedProject(sourceA, sourceB, sourceC)
        val child = OntologyGraphNodeId("a", Iri("https://example.com/Child"))
        val page = service.initial(project, OntologyGraphInitialQuery(setOf("a", "b", "c"), child))

        assertTrue(page.edges.any { it.target == OntologyGraphNodeId("a", Iri("https://example.com/Local")) })
        assertTrue(page.edges.any { it.target == OntologyGraphNodeId("b", Iri("https://example.com/Unique")) })
        assertTrue(page.edges.none { it.target.entityIri == Iri("https://example.com/Shared") })
        assertEquals(1, page.ambiguousCrossSourceRelationshipCount)
    }

    @Test
    fun respectsInitialBoundsAndPerformanceForOneThousandEntities(): Unit {
        val classes = (0 until 1_000).joinToString("\n") { index ->
            val parent = if (index == 0) "" else "; rdfs:subClassOf ex:C${index - 1}"
            "ex:C$index a owl:Class $parent ."
        }
        val project = project("large", """
            @prefix ex: <https://example.com/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            $classes
        """)
        lateinit var page: com.entio.core.OntologyGraphPage
        val elapsed = measureTimeMillis {
            page = service.initial(project, OntologyGraphInitialQuery(setOf("large")))
        }

        assertEquals(75, page.nodes.size)
        assertTrue(page.edges.size <= 150)
        assertTrue(page.nextCursor != null)
        assertTrue(elapsed < 1_000, "Graph extraction took ${elapsed}ms")

        val nextPage = service.initial(
            project,
            OntologyGraphInitialQuery(setOf("large"), cursor = requireNotNull(page.nextCursor)),
        )
        assertEquals(75, nextPage.nodes.size)
        assertTrue(page.nodes.map { it.id }.toSet().intersect(nextPage.nodes.map { it.id }.toSet()).isEmpty())
    }

    private fun ontology(): String = """
        @prefix ex: <https://example.com/> .
        @prefix owl: <http://www.w3.org/2002/07/owl#> .
        @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
        @prefix skos: <http://www.w3.org/2004/02/skos/core#> .
        @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
        ex:Party a owl:Class ; rdfs:label "Party" .
        ex:Customer a owl:Class ; rdfs:subClassOf ex:Party ; rdfs:label "Customer" ; skos:definition "A customer." .
        ex:Invoice a owl:Class .
        ex:receivedInvoice a owl:ObjectProperty ; rdfs:domain ex:Customer ; rdfs:range ex:Invoice ; rdfs:label "received invoice" .
        ex:age a owl:DatatypeProperty ; rdfs:domain ex:Customer ; rdfs:range xsd:integer .
        ex:note a owl:AnnotationProperty .
        ex:Shrey a ex:Customer ; ex:receivedInvoice ex:Invoice1 ; ex:age "42"^^xsd:integer .
        ex:Invoice1 a ex:Invoice .
    """

    private fun project(id: String, content: String): EntioProject = combinedProject(projectOntology(id, content))

    private fun projectOntology(id: String, content: String): LoadedOntology {
        val result = parser.parse(SemanticEngineTestFixtures.resolvedSource(content.trimIndent(), id))
        return assertIs<EntioResult.Success<LoadedOntology>>(result).value
    }

    private fun combinedProject(vararg ontologies: LoadedOntology): EntioProject {
        val triples = ontologies.flatMap { it.graph.triples }.toSet()
        return EntioProject(
            config = EntioProjectConfig(
                name = "graph-test",
                ontologySources = ontologies.map { OntologySourceReference(it.source.id, it.source.path.toString(), OntologyFormat.Turtle) },
            ),
            resolvedSources = ontologies.map { it.source },
            ontologies = ontologies.toList(),
            symbols = emptyList(),
            graph = GraphState(triples),
        )
    }
}
