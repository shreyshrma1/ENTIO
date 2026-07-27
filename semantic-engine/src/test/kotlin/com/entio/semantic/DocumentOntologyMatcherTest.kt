package com.entio.semantic

import com.entio.core.DocumentAuthorityMetadata
import com.entio.core.DocumentAuthorityStatus
import com.entio.core.DocumentCandidate
import com.entio.core.DocumentCandidateCategory
import com.entio.core.DocumentCandidateIdentity
import com.entio.core.DocumentConnectedModelItem
import com.entio.core.DocumentConnectedModelItemKind
import com.entio.core.DocumentConflictAlternative
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DocumentOntologyMatcherTest {
    @Test
    fun resolvesCanonicalAlignmentTargetsAcrossEveryApprovedScope(): Unit {
        val item = alignedItem("Customer")
        val scopes = DocumentMatchScope.entries
        val records = scopes.mapIndexed { index, scope ->
            record(
                scope,
                "https://example.com/customer-$index",
                sourceId = if (scope == DocumentMatchScope.CuratedFibo) "fibo-approved" else "source-$index",
            )
        }

        val targets = DocumentOntologyMatcher().resolveAlignmentTargets(
            item,
            records,
            records,
            curatedFiboSourceIds = setOf("fibo-approved"),
        )

        assertEquals(scopes.toSet(), targets.map { it.scope }.toSet())
        assertEquals(records.map { it.entityIri }.toSet(), targets.map { it.entityIri }.toSet())
    }

    @Test
    fun independentlyRejectsUnrelatedOrUnapprovedAlignmentTargets(): Unit {
        val matcher = DocumentOntologyMatcher()
        val payment = alignedItem("Payment")
        val account = record(DocumentMatchScope.AppliedLocal, "https://example.com/Account", label = "Account")
        assertFailsWith<IllegalArgumentException> {
            matcher.resolveAlignmentTargets(payment, listOf(account), listOf(account))
        }

        val fibo = record(
            DocumentMatchScope.CuratedFibo,
            "https://spec.edmcouncil.org/fibo/Payment",
            label = "Payment",
            sourceId = "fibo-unapproved",
        )
        assertFailsWith<IllegalArgumentException> {
            matcher.resolveAlignmentTargets(payment, listOf(fibo), listOf(fibo))
        }

        val connection = DocumentConnectedModelItem(
            id = "model-customer-loan",
            kind = DocumentConnectedModelItemKind.ObjectProperty,
            label = "customer loan connection",
            rationale = "A proposed relationship requires independent semantic review.",
            discoveryIds = listOf("discovery-connection"),
            order = 0,
        )
        val unrelatedConnections = listOf(
            record(DocumentMatchScope.AppliedLocal, "https://example.com/Customer", label = "Customer"),
            record(DocumentMatchScope.AppliedLocal, "https://example.com/Loan", label = "Loan"),
            record(DocumentMatchScope.AppliedLocal, "https://example.com/Account", label = "Account"),
            record(DocumentMatchScope.AppliedLocal, "https://example.com/Invoice", label = "Invoice"),
        )
        unrelatedConnections.forEach { target ->
            assertFailsWith<IllegalArgumentException> {
                matcher.resolveAlignmentTargets(connection, listOf(target), unrelatedConnections)
            }
        }
    }

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
        assertTrue(sameTask.recommendations.all { it.action == DocumentRecommendationAction.CreateLocal })
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
    fun preservesTheModelsConclusionAndKeepsExplicitAmbiguityReviewOnly(): Unit {
        val rationale = "The document defines material customer meaning that is absent from the ontology."
        assertEquals(
            rationale,
            matcher(candidate("Customer").copy(analysisRationale = rationale)).rationale,
        )

        val source = candidate("Effective Date")
        val ambiguity = source.copy(
            identity = source.identity.copy(category = DocumentCandidateCategory.Ambiguity),
            category = DocumentCandidateCategory.Ambiguity,
            analysisRationale = "Two documents imply incompatible ontology structures.",
        )
        assertEquals(
            DocumentRecommendationAction.InsufficientEvidence,
            matcher(ambiguity).action,
        )
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
            val label = "Customer-$flag"
            val recommendation = matcher(
                candidate(label, flags = listOf(flag)),
                records = when (flag) {
                    "conflict" -> listOf(
                        record(DocumentMatchScope.AppliedLocal, "https://example.com/one", label = "Customer conflict"),
                        record(DocumentMatchScope.AppliedLocal, "https://example.com/two", label = "Customer conflict"),
                    )
                    "extend", "revise" -> listOf(
                        record(DocumentMatchScope.AppliedLocal, "https://example.com/existing-$flag", label = label),
                    )
                    else -> emptyList()
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
    fun buildsUnmatchedCrossDocumentConflictsFromTheirSourceEvidence(): Unit {
        val first = candidate("Payment authorization", flags = listOf("conflict"))
        val secondEvidenceId = DocumentEvidenceId("evidence-document-2")
        val secondReference = DocumentEvidenceReference(
            id = secondEvidenceId,
            documentId = DocumentId("document-2"),
            blockId = DocumentTextBlockId("block-2"),
            startOffsetInBlock = 0,
            endOffsetInBlock = 21,
            exactExcerpt = "Payment authorization",
            extractionMethod = DocumentExtractionMethod.Text,
        )
        val conflict = first.copy(
            identity = first.identity.copy(
                evidenceKeys = (first.identity.evidenceKeys + secondEvidenceId.value).sorted(),
            ),
            evidence = (
                first.evidence +
                    DocumentEvidence(
                        secondEvidenceId,
                        DocumentEvidenceType.Explicit,
                        listOf(secondReference),
                    )
                ).sortedBy { it.id.value },
        )

        val recommendation = matcher(conflict)

        assertEquals(DocumentRecommendationAction.Conflict, recommendation.action)
        assertEquals(2, recommendation.conflicts.single().alternatives.size)
        assertTrue(
            recommendation.conflicts.single().alternatives
                .map(DocumentConflictAlternative::description)
                .containsAll(
                    listOf(
                        "Retain the interpretation supported by document document-1.",
                        "Retain the interpretation supported by document document-2.",
                    ),
                ),
        )
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

    private fun alignedItem(label: String): DocumentConnectedModelItem = DocumentConnectedModelItem(
        id = "model-${label.lowercase()}",
        kind = DocumentConnectedModelItemKind.Class,
        label = label,
        rationale = "The verified discoveries describe $label.",
        discoveryIds = listOf("discovery-${label.lowercase()}"),
        order = 0,
    )
}
