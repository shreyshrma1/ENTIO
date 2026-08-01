package com.entio.semantic

import com.entio.core.DocumentAnalysisPipelineVersions
import com.entio.core.DocumentAnalysisWorkKey
import com.entio.core.DocumentCandidateExtractionCategory
import com.entio.core.DocumentCandidateOrigin
import com.entio.core.DocumentConfidenceDimensions
import com.entio.core.DocumentEditableGroundedFieldKind
import com.entio.core.DocumentEvidenceId
import com.entio.core.DocumentGroundedAnalysisResult
import com.entio.core.DocumentGroundedCandidate
import com.entio.core.DocumentGroundedCoverageDisposition
import com.entio.core.DocumentGroundedDisposition
import com.entio.core.DocumentGroundedEvidenceSpan
import com.entio.core.DocumentGroundedReference
import com.entio.core.DocumentGroundedSemanticItem
import com.entio.core.DocumentId
import com.entio.core.DocumentMatchScope
import com.entio.core.DocumentOntologyRetrievalResult
import com.entio.core.DocumentOntologyRetrievalSelection
import com.entio.core.DocumentRetrievalFingerprints
import com.entio.core.DocumentRetrievalMatchReason
import com.entio.core.DocumentSemanticItemKind
import com.entio.core.DocumentSemanticReferenceRole
import com.entio.core.DocumentTextBlockId
import com.entio.core.Iri
import com.entio.core.SemanticDescriptorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DocumentGroundedAnalysisVerifierTest {
    @Test
    fun `verifies new meaning and compiles it through the existing compiler`(): Unit {
        val verified = verifier().verify(input(item("item-payment")))
        val compiled = DocumentSemanticPlanCompiler().compile(
            verified.plan,
            DocumentSemanticCompilerContext(
                targetSourceId = "source-1",
                iriNamespace = "https://example.com/ontology",
                existingEntities = emptyMap(),
                alignedEntities = verified.alignedEntities,
                itemAlignmentIds = verified.itemAlignmentIds,
            ),
        )

        assertEquals(1, compiled.size)
        assertEquals(com.entio.core.DocumentCompilationStatus.Compiled, compiled.single().status)
        assertTrue(compiled.single().operations.isNotEmpty())
    }

    @Test
    fun `accepts exact frozen reuse and rejects invented stale or wrong-kind selections`(): Unit {
        val selected = selection()
        val reuse = item("item-payment", DocumentGroundedDisposition.ReuseExisting, selected.selectionId)
        val verified = verifier().verify(input(reuse, retrieval = retrieval(listOf(selected))))

        assertEquals(selected.canonicalIri, verified.alignedEntities.getValue(selected.selectionId).iri)
        assertEquals(selected.selectionId, verified.itemAlignmentIds.getValue(reuse.id))
        assertFailsWith<IllegalArgumentException> {
            verifier().verify(input(reuse.copy(selectionId = "selection-invented"), retrieval = retrieval(listOf(selected))))
        }
        assertFailsWith<IllegalArgumentException> {
            verifier().verify(input(reuse, retrieval = retrieval(listOf(selected.copy(kind = SemanticDescriptorKind.Individual)))))
        }
        assertFailsWith<IllegalArgumentException> {
            verifier().verify(input(reuse, retrieval = retrieval(listOf(selected)), currentOntology = hash('9')))
        }
    }

    @Test
    fun `turns exact full-state duplicates and missing connected roles into editable needs-input fields`(): Unit {
        val duplicate = verifier().verify(
            input(
                item("item-payment"),
                fullState = listOf(
                    DocumentFullStateMatch("candidate-1", DocumentMatchScope.PrivateDraft,
                        Iri("https://example.com/Payment"), "source-1", true, false),
                ),
            ),
        )
        assertEquals(DocumentEditableGroundedFieldKind.Selection, duplicate.editableFields.single().kind)

        val missingDomain = item("item-domain", kind = DocumentSemanticItemKind.ObjectPropertyDomain)
        val needsRole = verifier().verify(input(missingDomain))
        assertEquals(DocumentEditableGroundedFieldKind.Domain, needsRole.editableFields.single().kind)
        assertTrue(needsRole.plan.items.isEmpty())
    }

    @Test
    fun `retains unresolved meaning as reviewer-solvable needs input`(): Unit {
        val unresolved = item("item-payment", DocumentGroundedDisposition.Unresolved)
        val verified = verifier().verify(
            input(unresolved, retrieval = retrieval(listOf(selection()))),
        )

        assertEquals(
            com.entio.core.DocumentGroundedRecommendationStatus.NeedsInput,
            verified.statusByItemId.getValue(unresolved.id),
        )
        assertEquals(setOf(unresolved.id), verified.plan.items.map { it.id }.toSet())
        assertEquals(setOf(unresolved.id), verified.plan.groups.single().itemIds.toSet())
        assertEquals(
            setOf(
                DocumentEditableGroundedFieldKind.Disposition,
                DocumentEditableGroundedFieldKind.EntityKind,
                DocumentEditableGroundedFieldKind.Label,
                DocumentEditableGroundedFieldKind.Selection,
            ),
            verified.editableFields.map { it.kind }.toSet(),
        )
        assertTrue(verified.editableFields.all { it.id.startsWith("${unresolved.id}:") })
        assertEquals(listOf("selection-1"), verified.editableFields.single {
            it.kind == DocumentEditableGroundedFieldKind.Selection
        }.compatibleSelectionIds)
    }

    @Test
    fun `names connected groups for executable declarations instead of reused support classes`(): Unit {
        val property = item(
            "item-ownership-information",
            kind = DocumentSemanticItemKind.DatatypeProperty,
        ).copy(label = "Ownership Information")
        val domain = item(
            "item-ownership-information-domain-entity",
            DocumentGroundedDisposition.ReuseExisting,
            selection().selectionId,
        ).copy(label = "Customer")
        val domainRelationship = item(
            "item-ownership-information-domain",
            kind = DocumentSemanticItemKind.DatatypePropertyDomain,
        ).copy(
            label = "Domain of Ownership Information",
            references = listOf(
                DocumentGroundedReference(DocumentSemanticReferenceRole.Property, property.id),
                DocumentGroundedReference(DocumentSemanticReferenceRole.Domain, domain.id),
            ).sortedBy(DocumentGroundedReference::stableOrderingKey),
        )
        val base = input(property, retrieval = retrieval(listOf(selection())))
        val verified = verifier().verify(
            base.copy(
                analysis = base.analysis.copy(
                    items = listOf(property, domain, domainRelationship)
                        .sortedBy(DocumentGroundedSemanticItem::stableOrderingKey),
                ),
            ),
        )

        assertEquals("grounded-group-${property.id}", verified.plan.groups.single().id)
        assertEquals(property.label, verified.plan.groups.single().title)
    }

    @Test
    fun `blocks extension of imported or FIBO selections while allowing read-only reuse`(): Unit {
        val imported = selection(scope = DocumentMatchScope.Imported, writable = false)
        val extension = verifier().verify(input(
            item("item-payment", DocumentGroundedDisposition.ExtendExisting, imported.selectionId),
            retrieval = retrieval(listOf(imported)),
        ))
        assertEquals(DocumentEditableGroundedFieldKind.Source, extension.editableFields.single().kind)

        val reuse = verifier().verify(input(
            item("item-payment", DocumentGroundedDisposition.ReuseExisting, imported.selectionId),
            retrieval = retrieval(listOf(imported)),
        ))
        assertTrue(reuse.editableFields.isEmpty())
    }

    @Test
    fun `retains administrative and illustrative dispositions without recommendation groups`(): Unit {
        listOf(
            DocumentGroundedDisposition.Administrative,
            DocumentGroundedDisposition.Illustrative,
        ).forEach { disposition ->
            val item = item("item-payment", disposition)
            val verified = verifier().verify(input(item))

            assertTrue(verified.plan.items.isEmpty())
            assertTrue(verified.plan.groups.isEmpty())
            assertEquals(
                com.entio.core.DocumentGroundedRecommendationStatus.ReviewOnly,
                verified.statusByItemId.getValue(item.id),
            )
        }
    }

    private fun verifier() = DocumentGroundedAnalysisVerifier()

    private fun input(
        item: DocumentGroundedSemanticItem,
        retrieval: DocumentOntologyRetrievalResult = retrieval(),
        fullState: List<DocumentFullStateMatch> = emptyList(),
        currentOntology: String = hash('1'),
    ): DocumentGroundedVerificationInput {
        val candidate = candidate()
        return DocumentGroundedVerificationInput(
            DocumentAnalysisWorkKey(hash('a')),
            listOf(candidate),
            listOf(retrieval),
            fullState,
            DocumentGroundedAnalysisResult(
                DocumentAnalysisPipelineVersions.GROUNDED_RESPONSE,
                listOf(item).sortedBy(DocumentGroundedSemanticItem::stableOrderingKey),
                listOf(DocumentGroundedCoverageDisposition(candidate.id, item.id, item.disposition, "Complete disposition.")),
            ),
            hash('1'), currentOntology, hash('2'), hash('2'),
        )
    }

    private fun item(
        id: String,
        disposition: DocumentGroundedDisposition = DocumentGroundedDisposition.ProposeNew,
        selectionId: String? = null,
        kind: DocumentSemanticItemKind = DocumentSemanticItemKind.Class,
    ) = DocumentGroundedSemanticItem(
        id = id,
        kind = kind,
        label = "Payment",
        candidateIds = listOf("candidate-1"),
        evidenceIds = listOf(DocumentEvidenceId("evidence-1")),
        disposition = disposition,
        selectionId = selectionId,
        rationale = "The exact evidence supports this meaning.",
        confidence = DocumentConfidenceDimensions(90, 80, 70),
    )

    private fun candidate() = DocumentGroundedCandidate(
        id = "candidate-1",
        origin = DocumentCandidateOrigin.LocalNlp,
        category = DocumentCandidateExtractionCategory.ConceptTerm,
        displayText = "Payment",
        normalizedText = "payment",
        documentId = DocumentId("document-1"),
        documentChecksumSha256 = hash('d'),
        evidenceSpans = listOf(DocumentGroundedEvidenceSpan(
            DocumentEvidenceId("evidence-1"), DocumentEvidenceId("reference-1"), DocumentId("document-1"),
            DocumentTextBlockId("block-1"), 1, null, 0, 7, "Payment",
        )),
        extractorContractVersion = DocumentAnalysisPipelineVersions.CANDIDATE_EXTRACTION_CONTRACT,
        resourceVersion = DocumentAnalysisPipelineVersions.NLP_RESOURCE_SET,
    )

    private fun retrieval(selections: List<DocumentOntologyRetrievalSelection> = emptyList()) =
        DocumentOntologyRetrievalResult(
            "candidate-1", DocumentAnalysisPipelineVersions.RETRIEVAL_QUERY,
            DocumentAnalysisPipelineVersions.RETRIEVAL_RANKING, DocumentAnalysisPipelineVersions.RETRIEVAL_RESULT,
            selections, true,
        )

    private fun selection(
        scope: DocumentMatchScope = DocumentMatchScope.AppliedLocal,
        writable: Boolean = true,
    ) = DocumentOntologyRetrievalSelection(
        "selection-1", "candidate-1", Iri("https://example.com/Payment"), SemanticDescriptorKind.Class,
        scope, "source-1", writable, "Payment", score = 100,
        matchReasons = listOf(DocumentRetrievalMatchReason("identity", "Exact normalized identity", 100)),
        fingerprints = DocumentRetrievalFingerprints(hash('1'), hash('2'), hash('3'), hash('4')),
    )

    private fun hash(character: Char) = character.toString().repeat(64)
}
