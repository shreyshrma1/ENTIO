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
import com.entio.core.DocumentPlanOperationKind
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
        val fullState = listOf(exactMatch("candidate-1", selected))
        val verified = verifier().verify(input(reuse, retrieval = retrieval(listOf(selected)), fullState = fullState))

        assertEquals(selected.canonicalIri, verified.alignedEntities.getValue(selected.selectionId).iri)
        assertEquals(selected.selectionId, verified.itemAlignmentIds.getValue(reuse.id))
        assertFailsWith<IllegalArgumentException> {
            verifier().verify(input(
                reuse.copy(selectionId = "selection-invented"),
                retrieval = retrieval(listOf(selected)),
                fullState = fullState,
            ))
        }
        assertFailsWith<IllegalArgumentException> {
            verifier().verify(input(
                reuse,
                retrieval = retrieval(listOf(selected.copy(kind = SemanticDescriptorKind.Individual))),
                fullState = fullState,
            ))
        }
        assertFailsWith<IllegalArgumentException> {
            verifier().verify(input(
                reuse,
                retrieval = retrieval(listOf(selected)),
                fullState = fullState,
                currentOntology = hash('9'),
            ))
        }
    }

    @Test
    fun `compiles an exact FIBO reuse into the existing external reuse operation`(): Unit {
        val selected = selection().copy(
            scope = DocumentMatchScope.CuratedFibo,
            writable = false,
            sourceId = com.entio.core.Phase5PackageIdentity.SOURCE_ID,
            sourceOntologyIris = listOf(Iri("https://spec.edmcouncil.org/fibo/ontology/FND/Parties/Parties/")),
        )
        val reuse = item("item-payment", DocumentGroundedDisposition.ReuseExisting, selected.selectionId)
        val verified = verifier().verify(
            input(reuse, retrieval = retrieval(listOf(selected)), fullState = listOf(exactMatch("candidate-1", selected))),
        )
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

        assertEquals(
            com.entio.core.DocumentGroundedRecommendationStatus.Executable,
            verified.statusByItemId.getValue(reuse.id),
        )
        assertEquals(listOf(DocumentPlanOperationKind.ReuseExternal), compiled.single().operations.map { it.kind })
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
    fun `promotes an unresolved class with no compatible ontology alternative to a new proposal`(): Unit {
        val unresolved = item("item-service-evidence", DocumentGroundedDisposition.Unresolved)
            .copy(label = "Service evidence", ambiguity = "No existing ontology alternative was selected.")
        val verified = verifier().verify(input(unresolved))

        assertEquals(
            DocumentGroundedDisposition.ProposeNew,
            verified.verifiedAnalysis.items.single().disposition,
        )
        assertEquals(
            DocumentGroundedDisposition.ProposeNew,
            verified.verifiedAnalysis.coverage.single().disposition,
        )
        assertEquals(
            com.entio.core.DocumentGroundedRecommendationStatus.Executable,
            verified.statusByItemId.getValue(unresolved.id),
        )
        assertTrue(verified.editableFields.isEmpty())
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
    fun `compiles attached extension definitions and keeps unchanged definitions review only`(): Unit {
        val selected = selection().copy(definition = "Existing payment meaning.")
        val extension = item(
            "item-payment",
            DocumentGroundedDisposition.ExtendExisting,
            selected.selectionId,
        ).copy(definition = "A payment instruction authorized by the customer.")
        val verified = verifier().verify(input(extension, retrieval = retrieval(listOf(selected))))
        val compiled = DocumentSemanticPlanCompiler().compile(
            verified.plan,
            DocumentSemanticCompilerContext(
                targetSourceId = "source-1",
                iriNamespace = "https://example.com/ontology",
                existingEntities = mapOf(selected.canonicalIri to com.entio.core.DocumentTemporaryReferenceKind.Class),
                alignedEntities = verified.alignedEntities,
                itemAlignmentIds = verified.itemAlignmentIds,
            ),
        )

        assertEquals(DocumentSemanticItemKind.Definition, verified.plan.items.single {
            it.id.endsWith(":grounded-definition")
        }.kind)
        assertEquals("grounded-group-${extension.id}", verified.plan.groups.single().id)
        assertEquals(extension.label, verified.plan.groups.single().title)
        assertEquals(listOf(DocumentPlanOperationKind.AddDefinition), compiled.single().operations.map { it.kind })

        val unchanged = verifier().verify(
            input(
                extension.copy(definition = "EXISTING   payment meaning."),
                retrieval = retrieval(listOf(selected)),
            ),
        )
        assertEquals(1, unchanged.plan.items.size)
        assertEquals(com.entio.core.DocumentSemanticOutcome.ReviewOnly, unchanged.plan.groups.single().outcome)
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
            fullState = listOf(exactMatch("candidate-1", imported)),
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

    @Test
    fun `consolidates only exact unconnected reuse meanings with the same canonical entity`(): Unit {
        val firstCandidate = candidate().copy(
            id = "candidate-customer-1",
            displayText = "Customer",
            normalizedText = "customer",
        )
        val secondCandidate = candidate().copy(
            id = "candidate-customer-2",
            displayText = "CUSTOMER",
            normalizedText = "customer",
            documentId = DocumentId("document-2"),
            evidenceSpans = listOf(DocumentGroundedEvidenceSpan(
                DocumentEvidenceId("evidence-2"), DocumentEvidenceId("reference-2"), DocumentId("document-2"),
                DocumentTextBlockId("block-2"), 1, null, 0, 8, "CUSTOMER",
            )),
        )
        val firstSelection = selection().copy(
            selectionId = "selection-customer-1",
            candidateId = firstCandidate.id,
            canonicalIri = Iri("https://example.com/Customer"),
            preferredLabel = "Customer",
        )
        val secondSelection = firstSelection.copy(
            selectionId = "selection-customer-2",
            candidateId = secondCandidate.id,
        )
        val firstItem = item(
            "item-customer-1",
            DocumentGroundedDisposition.ReuseExisting,
            firstSelection.selectionId,
        ).copy(
            label = "Customer",
            candidateIds = listOf(firstCandidate.id),
        )
        val secondItem = item(
            "item-customer-2",
            DocumentGroundedDisposition.ReuseExisting,
            secondSelection.selectionId,
        ).copy(
            label = "Customer",
            candidateIds = listOf(secondCandidate.id),
            evidenceIds = listOf(DocumentEvidenceId("evidence-2")),
        )
        val verified = verifier().verify(input(
            candidates = listOf(firstCandidate, secondCandidate),
            items = listOf(firstItem, secondItem),
            retrieval = listOf(
                retrieval(firstCandidate.id, listOf(firstSelection)),
                retrieval(secondCandidate.id, listOf(secondSelection)),
            ),
            fullState = listOf(
                exactMatch(firstCandidate.id, firstSelection),
                exactMatch(secondCandidate.id, secondSelection),
            ),
        ))

        assertEquals(1, verified.plan.groups.size)
        assertEquals(1, verified.plan.items.size)
        assertEquals(listOf(firstCandidate.id, secondCandidate.id), verified.plan.items.single().discoveryIds)
        assertEquals(
            listOf(DocumentEvidenceId("evidence-1"), DocumentEvidenceId("evidence-2")),
            verified.plan.items.single().evidenceIds,
        )
    }

    @Test
    fun `consolidates duplicate unresolved items with one exact normalized meaning`(): Unit {
        val firstCandidate = candidate().copy(
            id = "candidate-corporate-customer-1",
            displayText = "Corporate Customer",
            normalizedText = "corporate customer",
        )
        val secondCandidate = firstCandidate.copy(
            id = "candidate-corporate-customer-2",
            documentId = DocumentId("document-2"),
            evidenceSpans = listOf(DocumentGroundedEvidenceSpan(
                DocumentEvidenceId("evidence-2"), DocumentEvidenceId("reference-2"), DocumentId("document-2"),
                DocumentTextBlockId("block-2"), 1, null, 0, 18, "Corporate Customer",
            )),
        )
        val firstItem = item(
            "item-corporate-customer-1",
            DocumentGroundedDisposition.Unresolved,
        ).copy(
            label = "corporate customer",
            candidateIds = listOf(firstCandidate.id),
        )
        val secondItem = firstItem.copy(
            id = "item-corporate-customer-2",
            candidateIds = listOf(secondCandidate.id),
            evidenceIds = listOf(DocumentEvidenceId("evidence-2")),
        )
        val verified = verifier().verify(input(
            candidates = listOf(firstCandidate, secondCandidate),
            items = listOf(firstItem, secondItem),
            retrieval = listOf(
                retrieval(firstCandidate.id, emptyList()),
                retrieval(secondCandidate.id, emptyList()),
            ),
        ))

        assertEquals(1, verified.plan.groups.size)
        assertEquals(1, verified.plan.items.size)
        assertEquals(
            listOf(firstCandidate.id, secondCandidate.id),
            verified.plan.items.single().discoveryIds,
        )
        assertEquals(
            listOf(DocumentEvidenceId("evidence-1"), DocumentEvidenceId("evidence-2")),
            verified.plan.items.single().evidenceIds,
        )
        assertEquals(
            com.entio.core.DocumentGroundedRecommendationStatus.Executable,
            verified.statusByItemId.values.single(),
        )
        assertEquals(
            DocumentGroundedDisposition.ProposeNew,
            verified.verifiedAnalysis.items.single().disposition,
        )
    }

    @Test
    fun `keeps an unconnected relationship phrase as editable property input`(): Unit {
        val relationshipCandidate = candidate().copy(
            category = DocumentCandidateExtractionCategory.RelationshipPhrase,
            displayText = "requires",
            normalizedText = "require",
        )
        val relationshipItem = item(
            "item-require",
            DocumentGroundedDisposition.Unresolved,
            kind = DocumentSemanticItemKind.ObjectProperty,
        ).copy(label = "require")
        val verified = verifier().verify(input(relationshipItem).copy(
            candidates = listOf(relationshipCandidate),
        ))

        assertEquals(listOf(relationshipItem.id), verified.plan.items.map { it.id })
        assertEquals(listOf(relationshipItem.id), verified.plan.groups.single().itemIds)
        assertEquals(
            setOf(
                DocumentEditableGroundedFieldKind.Disposition,
                DocumentEditableGroundedFieldKind.EntityKind,
                DocumentEditableGroundedFieldKind.Label,
                DocumentEditableGroundedFieldKind.Domain,
                DocumentEditableGroundedFieldKind.Range,
            ),
            verified.editableFields.map { it.kind }.toSet(),
        )
        assertEquals(
            com.entio.core.DocumentGroundedRecommendationStatus.NeedsInput,
            verified.statusByItemId.getValue(relationshipItem.id),
        )
    }

    @Test
    fun `does not consolidate a qualified concept into its broader reuse target`(): Unit {
        val agreement = candidate().copy(
            id = "candidate-agreement",
            displayText = "Agreement",
            normalizedText = "agreement",
        )
        val loanAgreement = candidate().copy(
            id = "candidate-loan-agreement",
            displayText = "Loan agreement",
            normalizedText = "loan agreement",
            evidenceSpans = listOf(DocumentGroundedEvidenceSpan(
                DocumentEvidenceId("evidence-loan"), DocumentEvidenceId("reference-loan"), DocumentId("document-1"),
                DocumentTextBlockId("block-loan"), 1, null, 0, 14, "Loan agreement",
            )),
        )
        val exactSelection = selection().copy(
            selectionId = "selection-agreement",
            candidateId = agreement.id,
            canonicalIri = Iri("https://example.com/Agreement"),
            preferredLabel = "Agreement",
        )
        val broaderSelection = exactSelection.copy(
            selectionId = "selection-loan-agreement",
            candidateId = loanAgreement.id,
        )
        val exactItem = item(
            "item-agreement",
            DocumentGroundedDisposition.ReuseExisting,
            exactSelection.selectionId,
        ).copy(label = "Agreement", candidateIds = listOf(agreement.id))
        val qualifiedItem = item(
            "item-loan-agreement",
            DocumentGroundedDisposition.ReuseExisting,
            broaderSelection.selectionId,
        ).copy(
            label = "Agreement",
            candidateIds = listOf(loanAgreement.id),
            evidenceIds = listOf(DocumentEvidenceId("evidence-loan")),
        )
        val verified = verifier().verify(input(
            candidates = listOf(agreement, loanAgreement),
            items = listOf(exactItem, qualifiedItem),
            retrieval = listOf(
                retrieval(agreement.id, listOf(exactSelection)),
                retrieval(loanAgreement.id, listOf(broaderSelection)),
            ),
        ))

        assertEquals(2, verified.plan.groups.size)
        assertEquals(2, verified.plan.items.size)
        val needsInput = verified.plan.items.single { it.id == qualifiedItem.id }
        assertEquals("Loan agreement", needsInput.label)
        assertEquals(
            DocumentGroundedDisposition.Unresolved,
            verified.verifiedAnalysis.items.single { it.id == qualifiedItem.id }.disposition,
        )
        assertEquals(
            broaderSelection.selectionId,
            verified.suggestedSuperclassSelectionIdsByItemId.getValue(qualifiedItem.id),
        )
        assertEquals(com.entio.core.DocumentSemanticOutcome.Blocked, needsInput.outcome)
        assertEquals(
            com.entio.core.DocumentGroundedRecommendationStatus.NeedsInput,
            verified.statusByItemId.getValue(qualifiedItem.id),
        )
        assertTrue(qualifiedItem.id !in verified.itemAlignmentIds)
        assertEquals(
            setOf(
                DocumentEditableGroundedFieldKind.Disposition,
                DocumentEditableGroundedFieldKind.EntityKind,
                DocumentEditableGroundedFieldKind.Label,
                DocumentEditableGroundedFieldKind.Selection,
            ),
            verified.editableFields.filter { it.id.startsWith("${qualifiedItem.id}:") }.map { it.kind }.toSet(),
        )
    }

    @Test
    fun `allows an explicit reviewer-authorized broader reuse without treating it as provider exactness`(): Unit {
        val selected = selection().copy(
            canonicalIri = Iri("https://example.com/Agreement"),
            preferredLabel = "Agreement",
        )
        val candidate = candidate().copy(
            displayText = "Loan agreement",
            normalizedText = "loan agreement",
        )
        val reuse = item(
            "item-loan-agreement",
            DocumentGroundedDisposition.ReuseExisting,
            selected.selectionId,
        ).copy(label = candidate.displayText)
        val base = input(reuse, retrieval = retrieval(listOf(selected)))
        val verified = verifier().verify(
            base.copy(
                candidates = listOf(candidate),
                reviewerAuthorizedReuseItemIds = setOf(reuse.id),
            ),
        )

        assertEquals(
            com.entio.core.DocumentGroundedRecommendationStatus.ReviewOnly,
            verified.statusByItemId.getValue(reuse.id),
        )
        assertEquals(selected.selectionId, verified.itemAlignmentIds.getValue(reuse.id))
        assertTrue(verified.editableFields.isEmpty())
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

    private fun input(
        candidates: List<DocumentGroundedCandidate>,
        items: List<DocumentGroundedSemanticItem>,
        retrieval: List<DocumentOntologyRetrievalResult>,
        fullState: List<DocumentFullStateMatch> = emptyList(),
    ): DocumentGroundedVerificationInput = DocumentGroundedVerificationInput(
        DocumentAnalysisWorkKey(hash('a')),
        candidates.sortedBy(DocumentGroundedCandidate::stableOrderingKey),
        retrieval.sortedBy { it.candidateId },
        fullState,
        DocumentGroundedAnalysisResult(
            DocumentAnalysisPipelineVersions.GROUNDED_RESPONSE,
            items.sortedBy(DocumentGroundedSemanticItem::stableOrderingKey),
            items.flatMap { item ->
                item.candidateIds.map { candidateId ->
                    DocumentGroundedCoverageDisposition(
                        candidateId,
                        item.id,
                        item.disposition,
                        "Complete disposition.",
                    )
                }
            }.sortedBy(DocumentGroundedCoverageDisposition::stableOrderingKey),
        ),
        hash('1'), hash('1'), hash('2'), hash('2'),
    )

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
        retrieval("candidate-1", selections)

    private fun retrieval(candidateId: String, selections: List<DocumentOntologyRetrievalSelection>) =
        DocumentOntologyRetrievalResult(
            candidateId, DocumentAnalysisPipelineVersions.RETRIEVAL_QUERY,
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

    private fun exactMatch(
        candidateId: String,
        selection: DocumentOntologyRetrievalSelection,
    ) = DocumentFullStateMatch(
        candidateId,
        selection.scope,
        selection.canonicalIri,
        selection.sourceId,
        exactIdentity = true,
        exactTypedOperation = false,
    )

    private fun hash(character: Char) = character.toString().repeat(64)
}
