package com.entio.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class InferenceMaterializationContractsTest {
    private val subclassFact = InferenceMaterializationFact(
        InferenceMaterializationKind.SubclassRelationship,
        Iri("https://example.com/MortgageLoan"),
        Iri("http://www.w3.org/2000/01/rdf-schema#subClassOf"),
        Iri("https://example.com/Loan"),
    )
    private val semanticKey = SemanticFactKey("entio-semantic-fact-v1:${"a".repeat(64)}")
    private val factId = InferenceFactId("entio-reasoning-fact-v1:${"b".repeat(64)}")

    @Test
    fun constructsAllSupportedFactsAndStageabilityStates(): Unit {
        val facts = listOf(
            subclassFact,
            InferenceMaterializationFact(
                InferenceMaterializationKind.IndividualType,
                Iri("https://example.com/loan123"),
                Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                Iri("https://example.com/Loan"),
            ),
            InferenceMaterializationFact(
                InferenceMaterializationKind.ObjectPropertyAssertion,
                Iri("https://example.com/loan123"),
                Iri("https://example.com/hasBorrower"),
                Iri("https://example.com/customer456"),
            ),
        )

        assertEquals(InferenceMaterializationKind.entries, facts.map(InferenceMaterializationFact::kind))
        assertEquals(11, InferenceStageability.entries.size)
        InferenceStageability.entries.filterNot { it == InferenceStageability.AlreadyStaged }.forEach { state ->
            InferenceMaterializationCandidate(factId, semanticKey, subclassFact, state)
        }
        InferenceMaterializationCandidate(
            factId,
            semanticKey,
            subclassFact,
            InferenceStageability.AlreadyStaged,
            existingStagedChangeId = "stage-1",
        )
    }

    @Test
    fun rejectsMalformedIdentitiesAndInvalidBatches(): Unit {
        assertFailsWith<IllegalArgumentException> { SemanticFactKey("semantic") }
        assertFailsWith<IllegalArgumentException> { InferenceFactId("fact") }
        assertFailsWith<IllegalArgumentException> {
            InferenceMaterializationFact(
                InferenceMaterializationKind.IndividualType,
                Iri(""),
                Iri("type"),
                Iri("Class"),
            )
        }
        assertFailsWith<IllegalArgumentException> { InferenceMaterializationBatch(emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            InferenceMaterializationBatch(
                List(MAX_INFERENCE_MATERIALIZATION_BATCH_SIZE + 1) { index ->
                    InferenceMaterializationSelection(InferenceFactId("entio-reasoning-fact-v1:${index.toString(16).padStart(64, '0')}"))
                },
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceMaterializationBatch(
                listOf(InferenceMaterializationSelection(factId), InferenceMaterializationSelection(factId)),
            )
        }
    }

    @Test
    fun keepsLabelsOutOfCanonicalFactIdentity(): Unit {
        val candidate = InferenceMaterializationCandidate(
            factId = factId,
            semanticFactKey = semanticKey,
            fact = subclassFact,
            stageability = InferenceStageability.Stageable,
            sourceCandidates = listOf(InferenceSourceCandidate("simple", selected = true)),
            selectedSourceId = "simple",
        )

        assertEquals(subclassFact, candidate.fact)
        assertEquals(semanticKey, candidate.semanticFactKey)
    }

    @Test
    fun preservesOrdinaryStagedChangesWithoutProvenance(): Unit {
        val staged = StagedChange(
            id = "stage-1",
            order = 1,
            targetSourceId = "simple",
            summary = "Add superclass",
            operation = StagedChangeOperation.TypedEdit(
                AddSuperclassEdit(subclassFact.subject, subclassFact.objectValue),
            ),
        )

        assertNull(staged.materializationProvenance)
    }

    @Test
    fun validatesPresentationNeutralImmutableProvenance(): Unit {
        val importDependence = InferenceImportDependence(
            InferenceImportDependenceState.Imported,
            listOf("imports-a", "imports-b"),
        )
        val provenance = InferenceMaterializationProvenance(
            inferenceKind = subclassFact.kind,
            reasoningJobId = "job-1",
            graphFingerprint = "graph-fingerprint",
            factId = factId,
            semanticFactKey = semanticKey,
            fact = subclassFact,
            stagedByUserId = "alice",
            stagedAt = Instant.parse("2026-07-23T12:00:00Z"),
            targetSourceId = "simple",
            importDependence = importDependence,
        )

        assertEquals(importDependence, provenance.importDependence)
        assertEquals("simple", provenance.targetSourceId)
        assertFailsWith<IllegalArgumentException> {
            provenance.copy(targetSourceId = "")
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceImportDependence(InferenceImportDependenceState.Imported, listOf("z", "a"))
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceMaterializationCandidate(
                factId,
                semanticKey,
                subclassFact,
                InferenceStageability.Stageable,
                sourceCandidates = listOf(InferenceSourceCandidate("simple")),
                selectedSourceId = "other",
            )
        }
    }

    @Test
    fun rejectsDuplicateSemanticFactsInPreparedBatch(): Unit {
        val provenance = InferenceMaterializationProvenance(
            inferenceKind = subclassFact.kind,
            reasoningJobId = "job-1",
            graphFingerprint = "graph-fingerprint",
            factId = factId,
            semanticFactKey = semanticKey,
            fact = subclassFact,
            stagedByUserId = "alice",
            stagedAt = Instant.parse("2026-07-23T12:00:00Z"),
            targetSourceId = "simple",
        )
        val triple = GraphTriple(subclassFact.subject, subclassFact.predicate, subclassFact.objectValue)
        val prepared = PreparedInferenceMaterialization(
            factId = factId,
            semanticFactKey = semanticKey,
            fact = subclassFact,
            targetSourceId = "simple",
            edit = AddSuperclassEdit(subclassFact.subject, subclassFact.objectValue),
            triple = triple,
            provenance = provenance,
        )

        assertFailsWith<IllegalArgumentException> {
            val secondFactId = InferenceFactId("entio-reasoning-fact-v1:${"c".repeat(64)}")
            PreparedInferenceMaterializationBatch(
                listOf(
                    prepared,
                    prepared.copy(
                        factId = secondFactId,
                        provenance = provenance.copy(factId = secondFactId),
                    ),
                ),
            )
        }
    }
}
