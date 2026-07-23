package com.entio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OntologyGraphContractsTest {
    private val customer = OntologyGraphNodeId("simple", Iri("https://example.com/Customer"))
    private val person = OntologyGraphNodeId("simple", Iri("https://example.com/Person"))

    @Test
    fun constructsEverySupportedGraphState(): Unit {
        assertEquals(4, OntologyGraphNodeKind.entries.size)
        assertEquals(5, OntologyGraphEdgeKind.entries.size)
        assertEquals(4, OntologyGraphExpansionCategory.entries.size)
        assertEquals(listOf(75, 150), listOf(OntologyGraphLimits.Initial.nodeLimit, OntologyGraphLimits.Initial.edgeLimit))
        assertEquals(listOf(50, 100), listOf(OntologyGraphLimits.Expansion.nodeLimit, OntologyGraphLimits.Expansion.edgeLimit))
        assertEquals(listOf(300, 600), listOf(OntologyGraphLimits.OpenTab.nodeLimit, OntologyGraphLimits.OpenTab.edgeLimit))

        val nodes = OntologyGraphNodeKind.entries.map { kind ->
            OntologyGraphNode(
                id = OntologyGraphNodeId("simple", Iri("https://example.com/${kind.name}")),
                kind = kind,
                label = kind.name,
            )
        }
        assertEquals(OntologyGraphNodeKind.entries, nodes.map { it.kind })
    }

    @Test
    fun preservesSourceAndIriAsStableIdentity(): Unit {
        assertEquals("simple\u0000https://example.com/Customer", customer.stableKey)
        assertTrue(customer != customer.copy(sourceId = "other"))
    }

    @Test
    fun constructsAssertedEdgesAndBoundedSummary(): Unit {
        val summary = OntologyGraphNodeSummary(
            directSuperclassLabels = listOf("Person"),
            loadedRelationshipCount = 1,
            availableRelationshipCount = 2,
        )
        val edge = OntologyGraphEdge(
            id = "subclass",
            kind = OntologyGraphEdgeKind.SubclassOf,
            source = customer,
            target = person,
            label = "subclass of",
        )

        assertEquals(OntologyGraphProvenance.Asserted, edge.provenance)
        assertEquals(listOf("Person"), summary.directSuperclassLabels)
    }

    @Test
    fun inferredEdgesRequireTheirGraphState(): Unit {
        val edge = OntologyGraphEdge(
            id = "inferred-subclass",
            kind = OntologyGraphEdgeKind.SubclassOf,
            source = customer,
            target = person,
            label = "subclass of",
            provenance = OntologyGraphProvenance.Inferred,
            inferredGraphState = InferredGraphState.Applied,
        )

        assertEquals(InferredGraphState.Applied, edge.inferredGraphState)
        assertFailsWith<IllegalArgumentException> {
            edge.copy(provenance = OntologyGraphProvenance.Asserted)
        }
        assertFailsWith<IllegalArgumentException> {
            edge.copy(inferredGraphState = null)
        }
    }

    @Test
    fun objectAssertionsRequireTheirPredicateIri(): Unit {
        assertFailsWith<IllegalArgumentException> {
            OntologyGraphEdge(
                id = "assertion",
                kind = OntologyGraphEdgeKind.ObjectAssertion,
                source = customer,
                target = person,
                label = "knows",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            OntologyGraphEdge(
                id = "type",
                kind = OntologyGraphEdgeKind.Type,
                source = customer,
                target = person,
                label = "type",
                predicateIri = Iri("https://example.com/knows"),
            )
        }
    }

    @Test
    fun rejectsInvalidIdentityCountsLimitsAndQueries(): Unit {
        assertFailsWith<IllegalArgumentException> { OntologyGraphNodeId(" ", customer.entityIri) }
        assertFailsWith<IllegalArgumentException> { OntologyGraphLimits(0, 1) }
        assertFailsWith<IllegalArgumentException> { OntologyGraphLimits(1, 0) }
        assertFailsWith<IllegalArgumentException> { OntologyGraphPageCursor(nodeOffset = -1) }
        assertFailsWith<IllegalArgumentException> { OntologyGraphContinuation(" ") }
        assertFailsWith<IllegalArgumentException> { OntologyGraphNodeSummary(loadedRelationshipCount = 2, availableRelationshipCount = 1) }
        assertFailsWith<IllegalArgumentException> { OntologyGraphInitialQuery(emptySet()) }
        assertFailsWith<IllegalArgumentException> {
            OntologyGraphNeighborhoodQuery(setOf("other"), customer, setOf(OntologyGraphExpansionCategory.ClassHierarchy))
        }
        assertFailsWith<IllegalArgumentException> {
            OntologyGraphNeighborhoodQuery(setOf("simple"), customer, emptySet())
        }
    }

    @Test
    fun rejectsOrphanEdgesAndTracksBoundedDiagnostics(): Unit {
        val customerNode = OntologyGraphNode(customer, OntologyGraphNodeKind.Class, "Customer")
        val personNode = OntologyGraphNode(person, OntologyGraphNodeKind.Class, "Person")
        val edge = OntologyGraphEdge("subclass", OntologyGraphEdgeKind.SubclassOf, customer, person, "subclass of")

        assertFailsWith<IllegalArgumentException> {
            OntologyGraphPage(
                loadKind = OntologyGraphLoadKind.EntityCentered,
                seed = customer,
                nodes = listOf(customerNode),
                edges = listOf(edge),
                totalNodeCount = 2,
                totalEdgeCount = 1,
            )
        }

        val page = OntologyGraphPage(
            loadKind = OntologyGraphLoadKind.EntityCentered,
            seed = customer,
            nodes = listOf(customerNode, personNode),
            edges = listOf(edge),
            totalNodeCount = 3,
            totalEdgeCount = 2,
            nextCursor = OntologyGraphPageCursor(nodeOffset = 2, edgeOffset = 1),
            ambiguousCrossSourceRelationshipCount = 1,
            inferredOverlays = listOf(
                InferredOverlaySummary(
                    graphState = InferredGraphState.Applied,
                    state = InferredReadState.Updating,
                ),
            ),
        )
        assertTrue(page.hasMoreNodes)
        assertTrue(page.hasMoreEdges)
        assertEquals(1, page.ambiguousCrossSourceRelationshipCount)
        assertEquals(InferredReadState.Updating, page.inferredOverlays.single().state)
        assertFailsWith<IllegalArgumentException> {
            page.copy(inferredOverlays = page.inferredOverlays + page.inferredOverlays)
        }
    }
}
