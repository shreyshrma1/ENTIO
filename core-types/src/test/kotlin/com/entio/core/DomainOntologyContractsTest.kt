package com.entio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DomainOntologyContractsTest {
    @Test
    fun approvedProfileUsesFixedIdentityAndPaths(): Unit {
        val profile = DomainOntologyProfile()

        assertEquals(DomainOntologyProfileIdentity.SOURCE_ID, profile.sourceId)
        assertEquals(DomainOntologyProfileIdentity.RELEASE, profile.release)
        assertEquals("fibo-reuse", profile.managedSourceId)
        assertEquals(".entio/domain-profile.yaml", DomainOntologyProfileIdentity.PROFILE_PATH)
        assertEquals("ontology/fibo-reuse.ttl", DomainOntologyProfileIdentity.MANAGED_SOURCE_PATH)
        assertEquals(".entio/domain-reuse/events-v1.jsonl", DomainOntologyProfileIdentity.PROVENANCE_PATH)
    }

    @Test
    fun previewsAreNonMutatingContracts(): Unit {
        val activation = DomainProfileActivationPreview(
            profile = DomainOntologyProfile(),
            profilePath = DomainOntologyProfileIdentity.PROFILE_PATH,
            managedSourcePath = DomainOntologyProfileIdentity.MANAGED_SOURCE_PATH,
            serializedProfile = "profile",
            serializedEmptyManagedSource = "source",
        )
        val deactivation = DomainProfileDeactivationPreview(active = true, eligible = false, blockers = emptyList())

        assertFalse(activation.changesProjectOntology)
        assertFalse(deactivation.changesProjectOntology)
    }

    @Test
    fun deactivationContextRejectsNegativeStatementCounts(): Unit {
        assertFailsWith<IllegalArgumentException> {
            DomainProfileDeactivationContext(managedSourceStatementCount = -1)
        }
    }
}
