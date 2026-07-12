package com.entio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CoreEditingContractsTest {
    private val customerIri = Iri("https://example.com/Customer")
    private val accountIri = Iri("https://example.com/Account")
    private val labelIri = Iri("http://www.w3.org/2000/01/rdf-schema#label")
    private val subclassIri = Iri("http://www.w3.org/2000/01/rdf-schema#subClassOf")

    @Test
    fun constructsGraphChangesAndChangeSetsInOrder(): Unit {
        val addLabel = GraphChange(
            kind = GraphChangeKind.Addition,
            triple = GraphTriple(
                subject = customerIri,
                predicate = labelIri,
                objectTerm = RdfLiteral("Customer"),
            ),
        )
        val removeSuperclass = GraphChange(
            kind = GraphChangeKind.Removal,
            triple = GraphTriple(
                subject = customerIri,
                predicate = subclassIri,
                objectTerm = accountIri,
            ),
        )
        val changeSet = ChangeSet(changes = listOf(addLabel, removeSuperclass))

        assertEquals(listOf(addLabel, removeSuperclass), changeSet.changes)
        assertEquals(listOf(addLabel), changeSet.additions)
        assertEquals(listOf(removeSuperclass), changeSet.removals)
    }

    @Test
    fun rejectsEmptyChangeSetsByConstruction(): Unit {
        assertFailsWith<IllegalArgumentException> {
            ChangeSet(changes = emptyList())
        }
    }

    @Test
    fun constructsSupportedTypedOntologyEdits(): Unit {
        val edits = listOf(
            CreateClassEdit(classIri = customerIri),
            SetEntityLabelEdit(entity = customerIri, label = RdfLiteral("Customer", languageTag = "en")),
            AddSuperclassEdit(classIri = customerIri, superclassIri = accountIri),
            RemoveSuperclassEdit(classIri = customerIri, superclassIri = accountIri),
            CreateObjectPropertyEdit(propertyIri = Iri("https://example.com/owns")),
            CreateDatatypePropertyEdit(propertyIri = Iri("https://example.com/name")),
            SetPropertyDomainEdit(propertyIri = Iri("https://example.com/name"), domainClassIri = customerIri),
            SetPropertyRangeEdit(
                propertyIri = Iri("https://example.com/name"),
                rangeIri = Iri("http://www.w3.org/2001/XMLSchema#string"),
            ),
            CreateIndividualEdit(individualIri = Iri("https://example.com/customer-1"), classIri = customerIri),
            AssignTypeEdit(resource = BlankNodeResource(id = "b0"), typeIri = customerIri),
            AddObjectPropertyAssertionEdit(
                subject = customerIri,
                propertyIri = Iri("https://example.com/owns"),
                objectResource = accountIri,
            ),
            AddDatatypePropertyAssertionEdit(
                subject = customerIri,
                propertyIri = Iri("https://example.com/name"),
                value = RdfLiteral("Customer"),
            ),
        )

        edits.forEach { edit ->
            assertIs<TypedOntologyEdit>(edit)
        }
    }

    @Test
    fun typedOntologyEditsPreventLiteralSubjectsThroughResourceTypes(): Unit {
        val literal: RdfTerm = RdfLiteral("Customer")

        assertIs<RdfLiteral>(literal)
        AddDatatypePropertyAssertionEdit(
            subject = customerIri,
            propertyIri = Iri("https://example.com/name"),
            value = literal,
        )
    }

    @Test
    fun constructsProposalPreviewBaselineImpactAndResults(): Unit {
        val triple = GraphTriple(
            subject = customerIri,
            predicate = labelIri,
            objectTerm = RdfLiteral("Customer"),
        )
        val changeSet = ChangeSet(
            changes = listOf(GraphChange(kind = GraphChangeKind.Addition, triple = triple)),
        )
        val baseline = ProposalBaseline(
            projectFingerprint = "project-before",
            targetSourceId = "simple",
            targetSourcePath = "ontology/simple.ttl",
            targetSourceFingerprint = "source-before",
            graphFingerprint = "graph-before",
        )
        val preview = ChangePreview(
            graph = GraphState(triples = setOf(triple)),
            changeSet = changeSet,
        )
        val impact = SourceFileImpact(affectedPaths = listOf("ontology/simple.ttl"))
        val proposal = ChangeProposal(
            id = "proposal-1",
            title = "Add Customer label",
            targetSourceId = "simple",
            changeSet = changeSet,
            baseline = baseline,
            status = ChangeProposalStatus.ReadyForReview,
            preview = preview,
            sourceFileImpact = impact,
            review = ProposalReview(reviewer = "moderator", note = "Looks good."),
        )
        val applyResult = ApplyProposalResult.Applied(
            proposalId = proposal.id,
            changedFiles = impact.affectedPaths,
        )
        val rollbackResult = RollbackResult.Restored(restoredFiles = impact.affectedPaths)

        assertEquals(preview, proposal.preview)
        assertEquals(impact, proposal.sourceFileImpact)
        assertEquals(ChangeProposalStatus.ReadyForReview, proposal.status)
        assertEquals(SemanticEquivalenceResult.Equivalent, SemanticEquivalenceResult.Equivalent)
        assertEquals(proposal.id, applyResult.proposalId)
        assertEquals(impact.affectedPaths, rollbackResult.restoredFiles)
    }
}
