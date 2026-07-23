package com.entio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InferredFactsReadContractsTest {
    private val digest = "a".repeat(64)
    private val subject = Iri("https://example.com/Customer")
    private val predicate = Iri("http://www.w3.org/2000/01/rdf-schema#subClassOf")
    private val objectValue = Iri("https://example.com/Party")

    @Test
    fun constructsCurrentAppliedAndProposalOverlays(): Unit {
        val applied = fact(InferredGraphState.Applied)
        val proposal = fact(InferredGraphState.Proposal)

        val appliedOverlay = InferredFactsOverlay(
            graphState = InferredGraphState.Applied,
            state = InferredReadState.Current,
            facts = listOf(applied),
            graphFingerprint = "applied-fingerprint",
        )
        val proposalOverlay = InferredFactsOverlay(
            graphState = InferredGraphState.Proposal,
            state = InferredReadState.Current,
            facts = listOf(proposal),
            graphFingerprint = "proposal-graph",
            proposalFingerprint = "proposal-fingerprint",
        )

        assertEquals(listOf(applied), appliedOverlay.facts)
        assertEquals(InferredGraphState.Proposal, proposalOverlay.facts.single().graphState)
        assertEquals(InferredReadState.Current, proposalOverlay.state)
    }

    @Test
    fun acceptsMaterializationAndReadOnlySemanticKeyPrefixes(): Unit {
        assertEquals(
            "entio-semantic-fact-v1:$digest",
            SemanticFactKey("entio-semantic-fact-v1:$digest").value,
        )
        assertEquals(
            "entio-inferred-read-v1:$digest",
            SemanticFactKey("entio-inferred-read-v1:$digest").value,
        )
        assertFailsWith<IllegalArgumentException> {
            SemanticFactKey("entio-unknown-v1:$digest")
        }
    }

    @Test
    fun rejectsInvalidProvenanceAndBounds(): Unit {
        assertFailsWith<IllegalArgumentException> {
            fact(InferredGraphState.Applied).copy(proposalFingerprint = "proposal")
        }
        assertFailsWith<IllegalArgumentException> {
            fact(InferredGraphState.Proposal).copy(proposalFingerprint = null)
        }
        assertFailsWith<IllegalArgumentException> {
            fact(InferredGraphState.Applied).copy(placements = emptySet())
        }
        assertFailsWith<IllegalArgumentException> {
            InferredFactsOverlay(
                graphState = InferredGraphState.Applied,
                state = InferredReadState.Updating,
                facts = listOf(fact(InferredGraphState.Applied)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferredFactsOverlay(
                graphState = InferredGraphState.Applied,
                state = InferredReadState.Current,
                facts = List(MAX_INFERRED_READ_FACTS + 1) { index ->
                    fact(InferredGraphState.Applied, semanticKey = "entio-inferred-read-v1:${index.toString(16).padStart(64, '0')}")
                },
                graphFingerprint = "applied",
            )
        }
    }

    @Test
    fun requiresDeterministicTruncationAndUniqueFacts(): Unit {
        val current = fact(InferredGraphState.Applied)
        assertFailsWith<IllegalArgumentException> {
            InferredFactsOverlay(
                graphState = InferredGraphState.Applied,
                state = InferredReadState.Current,
                facts = listOf(current, current),
                totalFactCount = 2,
                graphFingerprint = "applied",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferredFactsOverlay(
                graphState = InferredGraphState.Applied,
                state = InferredReadState.Current,
                facts = listOf(current),
                totalFactCount = 2,
                truncated = false,
                graphFingerprint = "applied",
            )
        }
    }

    private fun fact(
        graphState: InferredGraphState,
        semanticKey: String = "entio-semantic-fact-v1:$digest",
    ): InferredReadFact = InferredReadFact(
        semanticFactKey = SemanticFactKey(semanticKey),
        subject = subject,
        predicate = predicate,
        objectValue = objectValue,
        kind = InferredReadKind.SubclassRelationship,
        placements = setOf(InferredFactPlacement.ClassSuperclasses),
        graphState = graphState,
        reasoningResultId = "reasoning-result",
        graphFingerprint = "graph-fingerprint",
        proposalFingerprint = if (graphState == InferredGraphState.Proposal) "proposal-fingerprint" else null,
        sourceId = "simple",
    )
}
