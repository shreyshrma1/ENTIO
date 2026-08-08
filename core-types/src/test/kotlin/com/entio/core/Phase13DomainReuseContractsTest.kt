package com.entio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Phase13DomainReuseContractsTest {
    @Test
    fun preparedBatchEnforcesApprovedHardBounds(): Unit {
        val iri = Iri("https://spec.edmcouncil.org/fibo/ontology/example/Concept")
        val triple = GraphTriple(
            iri,
            Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
            Iri("http://www.w3.org/2002/07/owl#Class"),
        )
        val snapshot = DomainReuseSourceSnapshot(
            canonicalIri = iri,
            kind = ExternalEntityKind.Class,
            sourceFamily = "FIBO",
            sourceOntologyIri = Iri("https://spec.edmcouncil.org/fibo/ontology/example/"),
            sourcePath = "source/example.rdf",
            recordFingerprint = "record",
            statementFingerprint = "statements",
            statements = listOf(triple),
            classification = DomainMaterializationClassification.CompleteSupportedMaterialization,
        )

        val batch = DomainReusePreparedBatch(
            action = DomainReuseAction.Reuse,
            canonicalIri = iri,
            entries = listOf(
                DomainReusePreparedEntry(
                    DomainOntologyProfileIdentity.MANAGED_SOURCE_ID,
                    ChangeSet(listOf(GraphChange(GraphChangeKind.Addition, triple))),
                ),
            ),
            dependencies = emptyList(),
            sourceSnapshot = snapshot,
            explicitSelectionCount = 1,
            generatedStatementCount = 1,
            preparedPayloadBytes = 128,
            partialMaterializationAcknowledged = false,
        )

        assertEquals(DomainReuseAction.Reuse, batch.action)
        assertEquals(20, batch.copy(explicitSelectionCount = 20).explicitSelectionCount)
        assertFailsWith<IllegalArgumentException> { batch.copy(explicitSelectionCount = 21) }
        assertFailsWith<IllegalArgumentException> { batch.copy(generatedStatementCount = 2_001) }
        assertFailsWith<IllegalArgumentException> { batch.copy(preparedPayloadBytes = 2_097_153) }
    }

    @Test
    fun provenanceRequiresTheFixedSchemaAndChecksumShape(): Unit {
        val iri = Iri("https://spec.edmcouncil.org/fibo/ontology/example/Concept")
        val event = DomainReuseProvenanceEvent(
            recordId = "event",
            eventKind = DomainReuseEventKind.Reused,
            sourceId = DomainOntologyProfileIdentity.SOURCE_ID,
            release = DomainOntologyProfileIdentity.RELEASE,
            packageFingerprint = DomainOntologyProfileIdentity.PACKAGE_FINGERPRINT,
            recordFingerprint = "record",
            canonicalIri = iri,
            entityKind = ExternalEntityKind.Class,
            sourceOntologyIri = Iri("https://spec.edmcouncil.org/fibo/ontology/example/"),
            sourcePath = "source/example.rdf",
            sourceStatementFingerprint = "statements",
            sourceSnapshot = emptyList(),
            omittedSourceAxioms = emptyList(),
            dependencySetFingerprint = "dependencies",
            targetManagedSourceId = DomainOntologyProfileIdentity.MANAGED_SOURCE_ID,
            proposalId = "proposal",
            appliedChangeSetId = "changes",
            actorId = "alice",
            appliedAt = "2026-08-08T00:00:00Z",
            baselineProjectFingerprint = "before",
            resultingProjectFingerprint = "after",
            projectStatementFingerprint = "project-statements",
            customization = DomainCustomizationClassification.Unchanged,
            checksum = "a".repeat(64),
        )

        assertEquals("entio-domain-reuse-provenance-v1", event.schema)
        assertFailsWith<IllegalArgumentException> { event.copy(checksum = "not-sha256") }
    }
}
