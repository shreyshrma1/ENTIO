package com.entio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class Phase25PlusContractsTest {
    private val customer = EntityCandidate(
        iri = Iri("https://example.com/Customer"),
        label = "Customer",
        kind = SymbolKind.Class,
        sourceId = "simple",
    )

    private val labelTriple = GraphTriple(
        subject = customer.iri,
        predicate = Iri("http://www.w3.org/2000/01/rdf-schema#label"),
        objectTerm = RdfLiteral("Customer"),
    )

    @Test
    fun constructsNamespaceAndSelectionContracts(): Unit {
        val namespace = IriNamespaceConfig(Iri("https://example.com/entio/simple#"))
        val selector = EntitySelector(label = "Customer", kind = SymbolKind.Class, sourceId = "simple")
        val resolution = EntityResolutionResult.Resolved(customer)

        assertEquals("https://example.com/entio/simple#", namespace.namespace.value)
        assertEquals("Customer", selector.label)
        assertEquals(customer, resolution.candidate)
        assertIs<EntityResolutionResult.Resolved>(resolution)
    }

    @Test
    fun preservesGeneratedIriMetadataAndCollisionState(): Unit {
        val generated = GeneratedIri(
            iri = Iri("https://example.com/entio/simple#Customer__2"),
            localName = "Customer__2",
            collision = IriCollisionOutcome.SuffixRequired,
            normalizationVersion = "phase-2.5-plus-v1",
        )

        assertEquals(IriCollisionOutcome.SuffixRequired, generated.collision)
        assertEquals("Customer__2", generated.localName)
    }

    @Test
    fun constructsDeletionDependenciesUsingEntioRdfTerms(): Unit {
        val dependency = DeletionDependency(
            statement = labelTriple,
            kind = DeletionDependencyKind.DirectDefinition,
            sourceId = "simple",
        )
        val plan = DeletionPlan(
            target = customer,
            directStatements = listOf(dependency),
            status = DeletionPlanStatus.Safe,
        )

        assertEquals(RdfLiteral("Customer"), plan.directStatements.single().statement.objectTerm)
        assertEquals(DeletionPlanStatus.Safe, plan.status)
        assertEquals(
            DeletionDependencyIdentity("simple", labelTriple).key,
            dependency.identityKey,
        )
    }

    @Test
    fun givesDifferentRdfStatementsDifferentStableDependencyKeys(): Unit {
        val resourceObject = labelTriple.copy(objectTerm = Iri("https://example.com/Customer"))

        assertEquals(
            false,
            DeletionDependencyIdentity("simple", labelTriple).key ==
                DeletionDependencyIdentity("simple", resourceObject).key,
        )
    }

    @Test
    fun preservesStagedOrderAndCombinedAttribution(): Unit {
        val first = StagedChange(
            id = "change-1",
            order = 0,
            targetSourceId = "simple",
            summary = "Create Customer",
            operation = StagedChangeOperation.TypedEdit(
                CreateClassEdit(classIri = customer.iri),
            ),
        )
        val second = StagedChange(
            id = "change-2",
            order = 1,
            targetSourceId = "simple",
            summary = "Label Customer",
            operation = StagedChangeOperation.TypedEdit(
                SetEntityLabelEdit(entity = customer.iri, label = RdfLiteral("Customer")),
            ),
        )
        val set = StagedChangeSet(entries = listOf(first, second))
        val conflict = StagedConflict(
            kind = StagedConflictKind.IncompatibleEdits,
            stagedChangeIds = listOf(first.id, second.id),
            message = "The staged edits are incompatible.",
        )
        val metadata = CombinedProposalMetadata(
            proposalId = "combined-1",
            stagedChangeIds = set.entries.map(StagedChange::id),
            targetSourceIds = listOf("simple"),
            status = CombinedProposalStatus.Conflicted,
            conflicts = listOf(conflict),
            validationAttribution = listOf(
                StagedValidationAttribution(stagedChangeId = second.id, issueCodes = listOf("example")),
            ),
        )

        assertEquals(listOf("change-1", "change-2"), set.entries.map(StagedChange::id))
        assertEquals(StagedChangeSetStatus.Ready, set.status)
        assertEquals(listOf("change-1", "change-2"), metadata.stagedChangeIds)
        assertEquals(listOf("example"), metadata.validationAttribution.single().issueCodes)
        assertEquals(StagedConflictKind.IncompatibleEdits, metadata.conflicts.single().kind)
    }

    @Test
    fun exposesExplicitResolutionAndLifecycleStates(): Unit {
        assertEquals(EntityResolutionResult.NotFound, EntityResolutionResult.NotFound)
        assertEquals(CombinedProposalStatus.ReadyForReview, CombinedProposalStatus.ReadyForReview)
        assertEquals(StagedChangeStatus.Removed, StagedChangeStatus.Removed)
        assertEquals(StagedChangeSetStatus.Empty, StagedChangeSet().status)
    }
}
