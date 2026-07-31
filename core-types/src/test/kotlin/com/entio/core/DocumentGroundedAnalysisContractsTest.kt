package com.entio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentGroundedAnalysisContractsTest {
    @Test
    fun `accepts the complete provider-neutral Phase 12 vocabulary`(): Unit {
        assertEquals(12, DocumentCandidateExtractionCategory.entries.size)
        assertEquals(8, DocumentMatchScope.entries.size)
        assertEquals(6, DocumentGroundedDisposition.entries.size)
        assertEquals(5, DocumentGroundedRecommendationStatus.entries.size)
        assertEquals(11, DocumentEditableGroundedFieldKind.entries.size)
        assertTrue(DocumentGroundedAnalysisStage.GroundedModeling.providerBacked)
        assertFalse(DocumentGroundedAnalysisStage.OntologyRetrieval.providerBacked)
        assertEquals("phase-12-grounded-model-response-v1", DocumentAnalysisPipelineVersions.GROUNDED_RESPONSE)
    }

    @Test
    fun `requires deterministic candidate evidence and ordering`(): Unit {
        val first = span("reference-1", 0, "Payment")
        val second = span("reference-2", 8, "Account")
        val candidate = candidate(listOf(first, second))

        assertEquals(candidate, candidate(listOf(first, second)))
        assertFailsWith<IllegalArgumentException> { candidate(listOf(second, first)) }
        assertFailsWith<IllegalArgumentException> { candidate(listOf(first, first)) }
        assertFailsWith<IllegalArgumentException> {
            candidate(listOf(first.copy(documentId = DocumentId("other-document"))))
        }
        assertFailsWith<IllegalArgumentException> { candidate(listOf(first), normalizedText = "Payment") }
    }

    @Test
    fun `requires stable retrieval selections reasons context and source permissions`(): Unit {
        val first = selection("selection-1", score = 95, iri = "https://example.com/Payment")
        val second = selection("selection-2", score = 80, iri = "https://example.com/Account")
        val result = retrieval(listOf(first, second))

        assertEquals(listOf("selection-1", "selection-2"), result.selections.map { it.selectionId })
        assertFailsWith<IllegalArgumentException> { retrieval(listOf(second, first)) }
        assertFailsWith<IllegalArgumentException> { retrieval(listOf(first, first)) }
        assertFailsWith<IllegalArgumentException> {
            selection("selection-3", scope = DocumentMatchScope.Imported, writable = true)
        }
        assertFailsWith<IllegalArgumentException> {
            first.copy(matchReasons = first.matchReasons.reversed())
        }
        assertFailsWith<IllegalArgumentException> {
            DocumentRetrievalStructuralContext(
                superclassIris = (1..6).map { Iri("https://example.com/Class$it") },
            )
        }
    }

    @Test
    fun `requires supplied same-candidate selections for reuse and extension`(): Unit {
        val reused = item(
            id = "item-1",
            disposition = DocumentGroundedDisposition.ReuseExisting,
            selectionId = "selection-1",
        )
        val extended = item(
            id = "item-2",
            disposition = DocumentGroundedDisposition.ExtendExisting,
            selectionId = "selection-2",
        )
        val created = item(id = "item-3", disposition = DocumentGroundedDisposition.ProposeNew)

        assertEquals("selection-1", reused.selectionId)
        assertEquals("selection-2", extended.selectionId)
        assertEquals(null, created.selectionId)
        assertFailsWith<IllegalArgumentException> {
            item(id = "invalid-reuse", disposition = DocumentGroundedDisposition.ReuseExisting)
        }
        assertFailsWith<IllegalArgumentException> {
            item(
                id = "invalid-new",
                disposition = DocumentGroundedDisposition.ProposeNew,
                selectionId = "https://example.com/Invented",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            reused.copy(candidateIds = listOf("candidate-2", "candidate-1"))
        }
    }

    @Test
    fun `requires complete unique grounded coverage`(): Unit {
        val first = item(id = "item-1", disposition = DocumentGroundedDisposition.ProposeNew)
        val second = item(
            id = "item-2",
            candidateIds = listOf("candidate-2"),
            evidenceIds = listOf(DocumentEvidenceId("evidence-2")),
            disposition = DocumentGroundedDisposition.Unresolved,
        )
        val coverage = listOf(
            coverage("candidate-1", "item-1", DocumentGroundedDisposition.ProposeNew),
            coverage("candidate-2", "item-2", DocumentGroundedDisposition.Unresolved),
        )
        val result = DocumentGroundedAnalysisResult(
            responseVersion = DocumentAnalysisPipelineVersions.GROUNDED_RESPONSE,
            items = listOf(first, second),
            coverage = coverage,
        )

        assertEquals(2, result.coverage.size)
        assertFailsWith<IllegalArgumentException> { result.copy(coverage = coverage.take(1)) }
        assertFailsWith<IllegalArgumentException> { result.copy(coverage = listOf(coverage.first(), coverage.first())) }
        assertFailsWith<IllegalArgumentException> {
            result.copy(coverage = coverage.map { it.copy(itemId = "missing-item") })
        }
    }

    @Test
    fun `keeps NeedsInput fields and count units explicit`(): Unit {
        val field = DocumentEditableGroundedField(
            id = "field-domain",
            kind = DocumentEditableGroundedFieldKind.Domain,
            required = true,
            compatibleSelectionIds = listOf("selection-1", "selection-2"),
            safeMessage = "Choose a compatible domain.",
        )
        val counts = DocumentAnalysisCounts(
            evidenceBlocks = 4,
            nlpCandidatesRetained = 3,
            nlpCandidatesRejected = 1,
            groundedItemsRetained = 2,
            groundedItemsUnresolved = 1,
            groundedItemsRejected = 0,
            recommendationsExecutable = 1,
            recommendationsMixed = 0,
            recommendationsNeedsInput = 1,
            recommendationsReviewOnly = 0,
            recommendationsBlocked = 0,
            expandedTypedEdits = 5,
        )

        assertTrue(field.required)
        assertEquals(3, counts.nlpCandidatesRetained)
        assertEquals(5, counts.expandedTypedEdits)
        assertFailsWith<IllegalArgumentException> { counts.copy(groundedItemsRejected = -1) }
        assertFailsWith<IllegalArgumentException> {
            field.copy(compatibleSelectionIds = listOf("selection-2", "selection-1"))
        }
    }

    @Test
    fun `validates frozen work identity without raw documents or credentials`(): Unit {
        val inputs = DocumentGroundedWorkKeyInputs(
            version = DocumentAnalysisPipelineVersions.WORK_KEY,
            projectId = "project-1",
            taskId = "task-1",
            documentInventorySha256 = hash('1'),
            evidenceInventorySha256 = hash('2'),
            candidateInventorySha256 = hash('3'),
            retrievalInventorySha256 = hash('4'),
            ontologySha256 = hash('5'),
            currentWorkSha256 = hash('6'),
            provenanceSha256 = hash('7'),
            fiboSha256 = hash('8'),
            extractorVersion = "phase-11-extractor-v1",
            nlpResourceVersion = DocumentAnalysisPipelineVersions.NLP_RESOURCE_SET,
            rankingVersion = DocumentAnalysisPipelineVersions.RETRIEVAL_RANKING,
            selectedModelId = "verified-model",
            promptVersion = DocumentAnalysisPipelineVersions.GROUNDED_PROMPT,
            responseVersion = DocumentAnalysisPipelineVersions.GROUNDED_RESPONSE,
        )

        assertEquals(inputs, inputs.copy())
        assertFailsWith<IllegalArgumentException> { inputs.copy(candidateInventorySha256 = "bad") }
        assertFailsWith<IllegalArgumentException> { inputs.copy(selectedModelId = " secret ") }
    }

    private fun span(referenceId: String, start: Int, text: String): DocumentGroundedEvidenceSpan =
        DocumentGroundedEvidenceSpan(
            evidenceId = DocumentEvidenceId("evidence-$referenceId"),
            referenceId = DocumentEvidenceId(referenceId),
            documentId = DocumentId("document-1"),
            blockId = DocumentTextBlockId("block-1"),
            pageNumber = 1,
            startOffsetInBlock = start,
            endOffsetInBlock = start + text.length,
            exactText = text,
        )

    private fun candidate(
        spans: List<DocumentGroundedEvidenceSpan>,
        normalizedText: String = "payment",
    ): DocumentGroundedCandidate = DocumentGroundedCandidate(
        id = "candidate-1",
        origin = DocumentCandidateOrigin.LocalNlp,
        category = DocumentCandidateExtractionCategory.ConceptTerm,
        displayText = "Payment",
        normalizedText = normalizedText,
        documentId = DocumentId("document-1"),
        documentChecksumSha256 = hash('a'),
        evidenceSpans = spans,
        hints = emptyList(),
        extractorContractVersion = DocumentAnalysisPipelineVersions.CANDIDATE_EXTRACTION_CONTRACT,
        resourceVersion = DocumentAnalysisPipelineVersions.NLP_RESOURCE_SET,
    )

    private fun selection(
        id: String,
        score: Int = 90,
        iri: String = "https://example.com/Payment",
        scope: DocumentMatchScope = DocumentMatchScope.AppliedLocal,
        writable: Boolean = true,
    ): DocumentOntologyRetrievalSelection = DocumentOntologyRetrievalSelection(
        selectionId = id,
        candidateId = "candidate-1",
        canonicalIri = Iri(iri),
        kind = SemanticDescriptorKind.Class,
        scope = scope,
        sourceId = "source-1",
        writable = writable,
        preferredLabel = "Payment",
        alternateLabels = listOf("Outgoing Payment"),
        definition = "A transfer of value.",
        score = score,
        matchReasons = listOf(
            DocumentRetrievalMatchReason("iri", "Canonical IRI token match", 20),
            DocumentRetrievalMatchReason("preferred-label", "Exact preferred label", 90),
        ),
        fingerprints = DocumentRetrievalFingerprints(hash('1'), hash('2'), hash('3'), hash('4')),
    )

    private fun retrieval(selections: List<DocumentOntologyRetrievalSelection>): DocumentOntologyRetrievalResult =
        DocumentOntologyRetrievalResult(
            candidateId = "candidate-1",
            queryVersion = DocumentAnalysisPipelineVersions.RETRIEVAL_QUERY,
            rankingVersion = DocumentAnalysisPipelineVersions.RETRIEVAL_RANKING,
            resultVersion = DocumentAnalysisPipelineVersions.RETRIEVAL_RESULT,
            selections = selections,
            completeAuthorizedScopeSearch = true,
        )

    private fun item(
        id: String,
        candidateIds: List<String> = listOf("candidate-1"),
        evidenceIds: List<DocumentEvidenceId> = listOf(DocumentEvidenceId("evidence-1")),
        disposition: DocumentGroundedDisposition,
        selectionId: String? = null,
    ): DocumentGroundedSemanticItem = DocumentGroundedSemanticItem(
        id = id,
        kind = DocumentSemanticItemKind.Class,
        label = "Payment",
        candidateIds = candidateIds,
        evidenceIds = evidenceIds,
        disposition = disposition,
        selectionId = selectionId,
        rationale = "The evidence describes a payment.",
        confidence = DocumentConfidenceDimensions(90, 80, 70),
    )

    private fun coverage(
        candidateId: String,
        itemId: String?,
        disposition: DocumentGroundedDisposition,
    ): DocumentGroundedCoverageDisposition = DocumentGroundedCoverageDisposition(
        candidateId = candidateId,
        itemId = itemId,
        disposition = disposition,
        rationale = "The candidate has one explicit disposition.",
    )

    private fun hash(character: Char): String = character.toString().repeat(64)
}
