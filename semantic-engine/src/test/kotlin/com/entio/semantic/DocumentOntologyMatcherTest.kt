package com.entio.semantic

import com.entio.core.DocumentAuthorityMetadata
import com.entio.core.DocumentAuthorityStatus
import com.entio.core.DocumentCandidate
import com.entio.core.DocumentCandidateCategory
import com.entio.core.DocumentCandidateIdentity
import com.entio.core.DocumentEvidence
import com.entio.core.DocumentEvidenceId
import com.entio.core.DocumentEvidenceReference
import com.entio.core.DocumentEvidenceType
import com.entio.core.DocumentExtractionMethod
import com.entio.core.DocumentId
import com.entio.core.DocumentMatchScope
import com.entio.core.DocumentRecommendationAction
import com.entio.core.DocumentRecommendationCategory
import com.entio.core.DocumentTextBlockId
import com.entio.core.Iri
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DocumentOntologyMatcherTest {
    @Test
    fun searchesApprovedScopesInOrderAndKeepsFiboPinned(): Unit {
        val candidate = candidate("Customer")
        val scopes = listOf(
            DocumentMatchScope.AppliedLocal,
            DocumentMatchScope.Imported,
            DocumentMatchScope.PrivateDraft,
            DocumentMatchScope.SharedStaging,
            DocumentMatchScope.CurrentProposal,
            DocumentMatchScope.SameTask,
            DocumentMatchScope.DurableProvenance,
            DocumentMatchScope.CuratedFibo,
        )
        val records = scopes.mapIndexed { index, scope ->
            record(scope, "https://example.com/$index", sourceId = if (scope == DocumentMatchScope.CuratedFibo) "fibo-approved" else "source-$index")
        } + record(DocumentMatchScope.CuratedFibo, "https://example.com/unapproved", sourceId = "fibo-unapproved")
        val result = matcher(
            candidate,
            records = records,
            curatedFiboSourceIds = setOf("fibo-approved"),
        )

        assertEquals(scopes, result.matches.map { it.scope })
        assertTrue(result.matches.none { it.sourceId == "fibo-unapproved" })
        assertEquals(DocumentRecommendationAction.ReuseLocal, result.action)
    }

    @Test
    fun exactTypedOperationsPreventDuplicatesAcrossCurrentAndDurableWork(): Unit {
        val candidate = candidate("Customer")
        val operation = "add-class|source|https://example.com/Customer"
        val records = listOf(
            record(DocumentMatchScope.SameTask, "https://example.com/Customer", operation = operation),
            record(DocumentMatchScope.DurableProvenance, "https://example.com/Customer", operation = operation),
        )

        val recommendation = matcher(candidate, records, mapOf(candidate.identity.value to operation))

        assertEquals(DocumentRecommendationAction.Confirm, recommendation.action)
        assertTrue(recommendation.rationale.contains("without a duplicate edit"))
        assertTrue(recommendation.relatedDraftItemIds.isEmpty())

        val duplicate = candidate("Customer duplicate")
        val operationKey = "add-class|source|https://example.com/CustomerDuplicate"
        val sameTask = DocumentOntologyMatcher().match(
            DocumentMatchingInput(
                exactWorkKey = "same-task",
                candidates = listOf(duplicate, duplicate.copy(
                    identity = duplicate.identity.copy(value = "candidate-customer-duplicate-2"),
                )),
                records = emptyList(),
                candidateTypedOperationKeys = mapOf(
                    duplicate.identity.value to operationKey,
                    "candidate-customer-duplicate-2" to operationKey,
                ),
                targetSourceId = "ontology",
                modelId = "gpt-test",
                promptVersion = "prompt-v1",
            ),
        )
        assertTrue(sameTask.recommendations.all { it.action == DocumentRecommendationAction.Confirm })
    }

    @Test
    fun createsOnNoMatchAndBlocksAmbiguousLabelOnlyMatches(): Unit {
        val candidate = candidate("Customer")
        assertEquals(DocumentRecommendationAction.CreateLocal, matcher(candidate).action)

        val ambiguous = matcher(
            candidate,
            records = listOf(
                record(DocumentMatchScope.AppliedLocal, "https://example.com/CustomerA"),
                record(DocumentMatchScope.AppliedLocal, "https://example.com/CustomerB"),
            ),
        )
        assertEquals(DocumentRecommendationAction.InsufficientEvidence, ambiguous.action)
        assertEquals(2, ambiguous.ambiguities.single().candidateIris.size)
        assertTrue(ambiguous.mandatoryClarificationReasons.isNotEmpty())
    }

    @Test
    fun emitsReviewOnlyEvolutionActionsAndConflictAlternatives(): Unit {
        val expected = mapOf(
            "extend" to DocumentRecommendationAction.Extend,
            "revise" to DocumentRecommendationAction.Revise,
            "split" to DocumentRecommendationAction.Split,
            "merge" to DocumentRecommendationAction.Merge,
            "conflict" to DocumentRecommendationAction.Conflict,
            "unsupported" to DocumentRecommendationAction.Unsupported,
        )
        expected.forEach { (flag, action) ->
            val recommendation = matcher(
                candidate("Customer-$flag", flags = listOf(flag)),
                records = if (flag == "conflict") {
                    listOf(
                        record(DocumentMatchScope.AppliedLocal, "https://example.com/one", label = "Customer conflict"),
                        record(DocumentMatchScope.AppliedLocal, "https://example.com/two", label = "Customer conflict"),
                    )
                } else {
                    emptyList()
                },
            )
            assertEquals(action, recommendation.action)
            if (action in setOf(
                    DocumentRecommendationAction.Revise,
                    DocumentRecommendationAction.Split,
                    DocumentRecommendationAction.Merge,
                    DocumentRecommendationAction.Conflict,
                )
            ) {
                assertTrue(recommendation.mandatoryClarificationReasons.isNotEmpty())
            }
            if (action == DocumentRecommendationAction.Conflict) {
                assertTrue(recommendation.conflicts.single().alternatives.size >= 2)
            }
        }
    }

    @Test
    fun usesAuthorityApplicabilityAndNeverTreatsRecencyAsSupersession(): Unit {
        val candidate = candidate("Policy")
        val newerSupporting = DocumentAuthorityMetadata(
            status = DocumentAuthorityStatus.Supporting,
            businessArea = "Risk",
            jurisdiction = "US",
            effectiveDate = LocalDate.parse("2026-07-01"),
        )
        val ordinary = matcher(candidate, authority = newerSupporting)
        assertNotEquals(DocumentRecommendationAction.Supersede, ordinary.action)
        assertTrue(ordinary.rationale.contains("Risk / US"))
        assertTrue(ordinary.rationale.contains("2026-07-01"))

        val superseded = matcher(
            candidate("Old policy"),
            authority = DocumentAuthorityMetadata(
                status = DocumentAuthorityStatus.Superseded,
                jurisdiction = "UK",
                expirationDate = LocalDate.parse("2026-06-30"),
            ),
        )
        assertEquals(DocumentRecommendationAction.Supersede, superseded.action)
        assertTrue(superseded.mandatoryClarificationReasons.isNotEmpty())

        val applicability = DocumentOntologyMatcher().match(
            DocumentMatchingInput(
                exactWorkKey = "applicability",
                candidates = listOf(candidate),
                records = listOf(
                    record(DocumentMatchScope.AppliedLocal, "https://example.com/US", label = "Policy").copy(
                        authority = DocumentAuthorityMetadata(
                            status = DocumentAuthorityStatus.Authoritative,
                            jurisdiction = "US",
                            businessArea = "Risk",
                        ),
                    ),
                    record(DocumentMatchScope.AppliedLocal, "https://example.com/UK", label = "Policy").copy(
                        authority = DocumentAuthorityMetadata(
                            status = DocumentAuthorityStatus.Authoritative,
                            jurisdiction = "UK",
                            businessArea = "Finance",
                            expirationDate = LocalDate.parse("2025-12-31"),
                        ),
                    ),
                ),
                authorityByDocumentId = mapOf(candidate.documentId.value to newerSupporting),
                targetSourceId = "ontology",
                modelId = "gpt-test",
                promptVersion = "prompt-v1",
            ),
        ).recommendations.single()
        assertEquals("https://example.com/US", applicability.matches.first().entityIri.value)
        assertTrue(applicability.matches.last().reason.contains("lowered the rank"))
    }

    @Test
    fun remainsStableAndReprocessesOnlyWhenExactWorkKeyChanges(): Unit {
        val candidate = candidate("Customer")
        val matcher = DocumentOntologyMatcher()
        val input = input("work-a", candidate, listOf(record(DocumentMatchScope.Imported, "https://example.com/Customer")))

        val first = matcher.match(input)
        assertEquals(first, matcher.match(input))
        val changed = matcher.match(input.copy(exactWorkKey = "work-b", records = emptyList()))

        assertEquals(DocumentRecommendationAction.ReuseImportedOrFibo, first.recommendations.single().action)
        assertEquals(DocumentRecommendationAction.CreateLocal, changed.recommendations.single().action)
    }

    private fun matcher(
        candidate: DocumentCandidate,
        records: List<DocumentSemanticRecord> = emptyList(),
        typedKeys: Map<String, String> = emptyMap(),
        authority: DocumentAuthorityMetadata? = null,
        curatedFiboSourceIds: Set<String> = emptySet(),
    ) = DocumentOntologyMatcher().match(
        input(
            "work-1",
            candidate,
            records,
            typedKeys,
            authority,
            curatedFiboSourceIds,
        ),
    ).recommendations.single()

    private fun input(
        workKey: String,
        candidate: DocumentCandidate,
        records: List<DocumentSemanticRecord>,
        typedKeys: Map<String, String> = emptyMap(),
        authority: DocumentAuthorityMetadata? = null,
        curatedFiboSourceIds: Set<String> = emptySet(),
    ): DocumentMatchingInput = DocumentMatchingInput(
        exactWorkKey = workKey,
        candidates = listOf(candidate),
        records = records,
        candidateTypedOperationKeys = typedKeys,
        authorityByDocumentId = authority?.let { mapOf(candidate.documentId.value to it) }.orEmpty(),
        curatedFiboSourceIds = curatedFiboSourceIds,
        targetSourceId = "ontology",
        modelId = "gpt-test",
        promptVersion = "prompt-v1",
    )

    private fun record(
        scope: DocumentMatchScope,
        iri: String,
        label: String = "Customer",
        sourceId: String = "ontology",
        operation: String? = null,
    ): DocumentSemanticRecord = DocumentSemanticRecord(
        scope = scope,
        entityIri = Iri(iri),
        sourceId = sourceId,
        preferredLabel = label,
        category = DocumentCandidateCategory.Class,
        normalizedIdentityKey = label.lowercase(),
        normalizedTypedOperationKey = operation,
    )

    private fun candidate(
        label: String,
        confidence: Int = 90,
        flags: List<String> = emptyList(),
    ): DocumentCandidate {
        val normalized = label.lowercase()
        val evidenceId = DocumentEvidenceId("evidence-${normalized.replace(Regex("[^a-z0-9]"), "-")}")
        val reference = DocumentEvidenceReference(
            id = evidenceId,
            documentId = DocumentId("document-1"),
            blockId = DocumentTextBlockId("block-1"),
            startOffsetInBlock = 0,
            endOffsetInBlock = 8,
            exactExcerpt = "Customer",
            extractionMethod = DocumentExtractionMethod.Text,
        )
        return DocumentCandidate(
            identity = DocumentCandidateIdentity(
                value = "candidate-${normalized.replace(Regex("[^a-z0-9]"), "-")}",
                documentChecksumSha256 = "a".repeat(64),
                category = DocumentCandidateCategory.Class,
                normalizedValue = normalized,
                evidenceKeys = listOf(evidenceId.value),
            ),
            documentId = DocumentId("document-1"),
            category = DocumentCandidateCategory.Class,
            recommendationCategory = DocumentRecommendationCategory.OntologyStructure,
            proposedLabel = label,
            confidence = confidence,
            evidence = listOf(DocumentEvidence(evidenceId, DocumentEvidenceType.Explicit, listOf(reference))),
            ambiguityFlags = flags.sorted(),
        )
    }
}
