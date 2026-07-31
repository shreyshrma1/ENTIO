package com.entio.web.ingestion

import com.entio.core.DocumentAnalysisPipelineVersions
import com.entio.core.DocumentCandidateExtractionCategory
import com.entio.core.DocumentCandidateOrigin
import com.entio.core.DocumentConfidenceDimensions
import com.entio.core.DocumentEvidenceId
import com.entio.core.DocumentGroundedAnalysisResult
import com.entio.core.DocumentGroundedCandidate
import com.entio.core.DocumentGroundedCoverageDisposition
import com.entio.core.DocumentGroundedDisposition
import com.entio.core.DocumentGroundedEvidenceSpan
import com.entio.core.DocumentGroundedSemanticItem
import com.entio.core.DocumentId
import com.entio.core.DocumentOntologyRetrievalResult
import com.entio.core.DocumentSemanticItemKind
import com.entio.core.DocumentTextBlockId
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DocumentGroundedAnalysisServiceTest {
    @Test
    fun `uses exact input retry and records attempts separately from logical calls`(): Unit = runBlocking {
        var calls = 0
        val candidate = candidate(1)
        val provider = DocumentGroundedAnalysisProvider { _, model, instruction, request ->
            calls += 1
            assertEquals("verified-model", model)
            assertTrue(instruction.contains("untrusted document evidence"))
            if (calls == 1) DocumentGroundedAnalysisProviderResult.Failed(true, "document-provider-timeout")
            else DocumentGroundedAnalysisProviderResult.Completed(result(request.candidates))
        }

        val completed = DocumentGroundedAnalysisService(provider).analyze(
            "secret", "verified-model", "task-1", listOf(candidate), listOf(retrieval(candidate.id)),
        )

        assertEquals(1, completed.logicalCallCount)
        assertEquals(2, completed.providerAttemptCount)
        assertEquals(listOf(candidate.id), completed.results.single().coverage.map { it.candidateId })
    }

    @Test
    fun `adaptively splits oversized groups without losing successful candidates`(): Unit = runBlocking {
        val candidates = (1..4).map(::candidate)
        val provider = DocumentGroundedAnalysisProvider { _, _, _, request ->
            if (request.candidates.size > 1) {
                DocumentGroundedAnalysisProviderResult.Failed(false, "document-provider-output-limit")
            } else {
                DocumentGroundedAnalysisProviderResult.Completed(result(request.candidates))
            }
        }

        val completed = DocumentGroundedAnalysisService(provider).analyze(
            "secret", "verified-model", "task-1", candidates, candidates.map { retrieval(it.id) },
        )

        assertEquals(7, completed.logicalCallCount)
        assertEquals(candidates.map { it.id }, completed.results.flatMap { it.coverage }.map { it.candidateId })
    }

    @Test
    fun `rejects partial output and invented selection IDs`(): Unit = runBlocking {
        val candidates = listOf(candidate(1), candidate(2))
        val partial = DocumentGroundedAnalysisProvider { _, _, _, request ->
            DocumentGroundedAnalysisProviderResult.Completed(result(request.candidates.take(1)))
        }
        assertFailsWith<IllegalArgumentException> {
            DocumentGroundedAnalysisService(partial).analyze(
                "secret", "verified-model", "task-1", candidates, candidates.map { retrieval(it.id) },
            )
        }

        val invented = DocumentGroundedAnalysisProvider { _, _, _, request ->
            DocumentGroundedAnalysisProviderResult.Completed(result(request.candidates, "invented-selection"))
        }
        assertFailsWith<IllegalArgumentException> {
            DocumentGroundedAnalysisService(invented).analyze(
                "secret", "verified-model", "task-1", listOf(candidates.first()), listOf(retrieval(candidates.first().id)),
            )
        }
    }

    @Test
    fun `does not call provider after cancellation`(): Unit = runBlocking {
        var calls = 0
        val provider = DocumentGroundedAnalysisProvider { _, _, _, _ ->
            calls += 1
            error("must not run")
        }
        assertFailsWith<CancellationException> {
            DocumentGroundedAnalysisService(provider, isCancelled = { true }).analyze(
                "secret", "verified-model", "task-1", listOf(candidate(1)), listOf(retrieval("candidate-01")),
            )
        }
        assertEquals(0, calls)
    }

    @Test
    fun `scales planned groups with promoted candidates instead of applying a task-wide ceiling`(): Unit = runBlocking {
        val candidates = (1..601).map(::candidate).sortedBy(DocumentGroundedCandidate::stableOrderingKey)
        val progress = mutableListOf<Pair<Int, Int>>()
        val provider = DocumentGroundedAnalysisProvider { _, _, _, request ->
            DocumentGroundedAnalysisProviderResult.Completed(result(request.candidates))
        }

        val completed = DocumentGroundedAnalysisService(provider, onProgress = { done, planned ->
            progress += done to planned
        }).analyze(
            "secret",
            "verified-model",
            "task-1",
            candidates,
            candidates.map { retrieval(it.id) },
        )

        assertEquals(16, completed.logicalCallCount)
        assertEquals(16, completed.providerAttemptCount)
        assertEquals(601, completed.results.flatMap { it.coverage }.size)
        assertEquals(16 to 16, progress.last())
    }

    private fun result(candidates: List<DocumentGroundedCandidate>, selection: String? = null): DocumentGroundedAnalysisResult {
        val items = candidates.map { candidate ->
            val disposition = if (selection == null) DocumentGroundedDisposition.ProposeNew else DocumentGroundedDisposition.ReuseExisting
            DocumentGroundedSemanticItem(
                id = "item-${candidate.id}", kind = DocumentSemanticItemKind.Class, label = candidate.displayText,
                candidateIds = listOf(candidate.id), evidenceIds = listOf(candidate.evidenceSpans.single().evidenceId),
                disposition = disposition, selectionId = selection, rationale = "Grounded in exact evidence.",
                confidence = DocumentConfidenceDimensions(90, 80, 70),
            )
        }.sortedBy(DocumentGroundedSemanticItem::stableOrderingKey)
        return DocumentGroundedAnalysisResult(
            DocumentAnalysisPipelineVersions.GROUNDED_RESPONSE,
            items,
            candidates.mapIndexed { index, candidate ->
                DocumentGroundedCoverageDisposition(candidate.id, "item-${candidate.id}", items.first { it.id == "item-${candidate.id}" }.disposition, "Disposition $index is complete.")
            }.sortedBy(DocumentGroundedCoverageDisposition::stableOrderingKey),
        )
    }

    private fun candidate(index: Int): DocumentGroundedCandidate {
        val id = index.toString().padStart(2, '0')
        val text = "Payment $id"
        return DocumentGroundedCandidate(
            id = "candidate-$id", origin = DocumentCandidateOrigin.LocalNlp,
            category = DocumentCandidateExtractionCategory.ConceptTerm, displayText = text,
            normalizedText = text.lowercase(), documentId = DocumentId("document-1"),
            documentChecksumSha256 = "a".repeat(64),
            evidenceSpans = listOf(DocumentGroundedEvidenceSpan(
                DocumentEvidenceId("evidence-$id"), DocumentEvidenceId("reference-$id"), DocumentId("document-1"),
                DocumentTextBlockId("block-$id"), 1, null, 0, text.length, text,
            )), extractorContractVersion = DocumentAnalysisPipelineVersions.CANDIDATE_EXTRACTION_CONTRACT,
            resourceVersion = DocumentAnalysisPipelineVersions.NLP_RESOURCE_SET,
        )
    }

    private fun retrieval(candidateId: String): DocumentOntologyRetrievalResult = DocumentOntologyRetrievalResult(
        candidateId, DocumentAnalysisPipelineVersions.RETRIEVAL_QUERY, DocumentAnalysisPipelineVersions.RETRIEVAL_RANKING,
        DocumentAnalysisPipelineVersions.RETRIEVAL_RESULT, emptyList(), true,
    )
}
