package com.entio.semantic

import com.entio.core.DomainCustomizationClassification
import com.entio.core.DomainOntologyProfileIdentity
import com.entio.core.DomainReuseEventKind
import com.entio.core.DomainReuseProvenanceEvent
import com.entio.core.EntioResult
import com.entio.core.ExternalEntityKind
import com.entio.core.GraphTriple
import com.entio.core.Iri
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DomainReuseProvenanceRepositoryTest {
    @Test
    fun prepareCommitFinishAppendsChecksummedJsonl(): Unit {
        val root = Files.createTempDirectory("entio-domain-provenance-append")
        val repository = DomainReuseProvenanceRepository()
        val prepared = assertIs<EntioResult.Success<PreparedDomainReuseProvenance>>(
            repository.prepare(root, listOf(event("one")), "before", "after"),
        ).value

        assertTrue(assertIs<EntioResult.Success<List<DomainReuseProvenanceEvent>>>(repository.list(root)).value.isEmpty())
        assertIs<EntioResult.Success<Unit>>(repository.commit(prepared))
        val committed = assertIs<EntioResult.Success<List<DomainReuseProvenanceEvent>>>(repository.list(root)).value
        assertEquals(listOf("one"), committed.map { it.recordId })
        assertTrue(committed.single().checksum.matches(Regex("[a-f0-9]{64}")))
        assertIs<EntioResult.Success<Unit>>(repository.finish(prepared))
        assertFalse(Files.exists(root.resolve(".entio/domain-reuse/apply-transaction-v1.yaml")))
    }

    @Test
    fun committedProvenanceRollsBackWithOntologyFailure(): Unit {
        val root = Files.createTempDirectory("entio-domain-provenance-rollback")
        val repository = DomainReuseProvenanceRepository()
        val prepared = assertIs<EntioResult.Success<PreparedDomainReuseProvenance>>(
            repository.prepare(root, listOf(event("rollback")), "before", "after"),
        ).value
        assertIs<EntioResult.Success<Unit>>(repository.commit(prepared))
        assertIs<EntioResult.Success<Unit>>(repository.rollback(prepared))

        assertTrue(assertIs<EntioResult.Success<List<DomainReuseProvenanceEvent>>>(repository.list(root)).value.isEmpty())
    }

    @Test
    fun failedFinalizationRetainsJournalForRollback(): Unit {
        val root = Files.createTempDirectory("entio-domain-provenance-finalize-failure")
        val repository = DomainReuseProvenanceRepository()
        val prepared = assertIs<EntioResult.Success<PreparedDomainReuseProvenance>>(
            repository.prepare(root, listOf(event("finalize-failure")), "before", "after"),
        ).value
        assertIs<EntioResult.Success<Unit>>(repository.commit(prepared))
        Files.writeString(root.resolve(".entio/domain-reuse/events-v1.jsonl"), "tampered")

        assertIs<EntioResult.Failure>(repository.finish(prepared))
        assertTrue(Files.exists(root.resolve(".entio/domain-reuse/apply-transaction-v1.yaml")))
        assertIs<EntioResult.Success<Unit>>(repository.rollback(prepared))
        assertTrue(assertIs<EntioResult.Success<List<DomainReuseProvenanceEvent>>>(repository.list(root)).value.isEmpty())
    }

    @Test
    fun recoveryFinishesMatchingCommitOrRestoresPreparedBaseline(): Unit {
        val committedRoot = Files.createTempDirectory("entio-domain-provenance-recover-commit")
        val repository = DomainReuseProvenanceRepository()
        val committed = assertIs<EntioResult.Success<PreparedDomainReuseProvenance>>(
            repository.prepare(committedRoot, listOf(event("committed")), "before", "after"),
        ).value
        assertIs<EntioResult.Success<Unit>>(repository.commit(committed))
        assertIs<EntioResult.Success<Unit>>(repository.recover(committedRoot, "after"))
        assertEquals(1, assertIs<EntioResult.Success<List<DomainReuseProvenanceEvent>>>(repository.list(committedRoot)).value.size)

        val preparedRoot = Files.createTempDirectory("entio-domain-provenance-recover-prepare")
        assertIs<EntioResult.Success<PreparedDomainReuseProvenance>>(
            repository.prepare(preparedRoot, listOf(event("prepared")), "before", "after"),
        )
        assertIs<EntioResult.Success<Unit>>(repository.recover(preparedRoot, "before"))
        assertTrue(assertIs<EntioResult.Success<List<DomainReuseProvenanceEvent>>>(repository.list(preparedRoot)).value.isEmpty())
    }

    @Test
    fun corruptionAndAmbiguousRecoveryFailClosed(): Unit {
        val root = Files.createTempDirectory("entio-domain-provenance-corrupt")
        val repository = DomainReuseProvenanceRepository()
        val prepared = assertIs<EntioResult.Success<PreparedDomainReuseProvenance>>(
            repository.prepare(root, listOf(event("corrupt")), "before", "after"),
        ).value
        assertIs<EntioResult.Success<Unit>>(repository.commit(prepared))
        Files.writeString(root.resolve(DomainOntologyProfileIdentity.PROVENANCE_PATH), "{\"checksum\":\"bad\"}\n")

        assertEquals("domain-provenance-invalid", assertIs<EntioResult.Failure>(repository.list(root)).issues.single().code)
        assertEquals(
            "domain-provenance-recovery-ambiguous",
            assertIs<EntioResult.Failure>(repository.recover(root, "neither")).issues.single().code,
        )
    }

    private fun event(id: String): DomainReuseProvenanceEvent {
        val iri = Iri("https://spec.edmcouncil.org/fibo/ontology/example/$id")
        return DomainReuseProvenanceEvent(
            recordId = id,
            eventKind = DomainReuseEventKind.Reused,
            sourceId = DomainOntologyProfileIdentity.SOURCE_ID,
            release = DomainOntologyProfileIdentity.RELEASE,
            packageFingerprint = DomainOntologyProfileIdentity.PACKAGE_FINGERPRINT,
            recordFingerprint = "record-$id",
            canonicalIri = iri,
            entityKind = ExternalEntityKind.Class,
            sourceOntologyIri = Iri("https://spec.edmcouncil.org/fibo/ontology/example/"),
            sourcePath = "source/example.rdf",
            sourceStatementFingerprint = "source-statements",
            sourceSnapshot = listOf(
                GraphTriple(
                    iri,
                    Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                    Iri("http://www.w3.org/2002/07/owl#Class"),
                ),
            ),
            omittedSourceAxioms = emptyList(),
            dependencySetFingerprint = "dependencies",
            targetManagedSourceId = DomainOntologyProfileIdentity.MANAGED_SOURCE_ID,
            proposalId = "proposal-$id",
            appliedChangeSetId = "changes-$id",
            actorId = "alice",
            appliedAt = "2026-08-08T00:00:00Z",
            baselineProjectFingerprint = "before",
            resultingProjectFingerprint = "after",
            projectStatementFingerprint = "project-statements",
            customization = DomainCustomizationClassification.Unchanged,
            checksum = "0".repeat(64),
        )
    }
}
