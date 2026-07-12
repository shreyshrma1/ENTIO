package com.entio.core

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class CoreTypesEnumTest {
    @Test
    fun exposesExpectedFixedStates(): Unit {
        assertEquals(listOf(OntologyFormat.Turtle), OntologyFormat.entries)

        assertContains(SymbolKind.entries, SymbolKind.Class)
        assertContains(SymbolKind.entries, SymbolKind.Property)
        assertContains(SymbolKind.entries, SymbolKind.Individual)
        assertContains(SymbolKind.entries, SymbolKind.Shape)
        assertContains(SymbolKind.entries, SymbolKind.NamespaceTerm)
        assertContains(SymbolKind.entries, SymbolKind.Unknown)

        assertEquals(
            listOf(ValidationSeverity.Error, ValidationSeverity.Warning, ValidationSeverity.Info),
            ValidationSeverity.entries,
        )
        assertEquals(listOf(ValidationStatus.Valid, ValidationStatus.Invalid), ValidationStatus.entries)
        assertEquals(
            listOf(SemanticDiffKind.Added, SemanticDiffKind.Removed, SemanticDiffKind.Changed),
            SemanticDiffKind.entries,
        )
        assertEquals(listOf(GraphChangeKind.Addition, GraphChangeKind.Removal), GraphChangeKind.entries)
        assertEquals(
            listOf(
                ChangeProposalStatus.Draft,
                ChangeProposalStatus.Previewed,
                ChangeProposalStatus.Verified,
                ChangeProposalStatus.ReadyForReview,
                ChangeProposalStatus.Rejected,
                ChangeProposalStatus.Approved,
                ChangeProposalStatus.Applied,
                ChangeProposalStatus.VerificationFailed,
                ChangeProposalStatus.Stale,
                ChangeProposalStatus.ApplyFailed,
                ChangeProposalStatus.RolledBack,
            ),
            ChangeProposalStatus.entries,
        )
    }
}
