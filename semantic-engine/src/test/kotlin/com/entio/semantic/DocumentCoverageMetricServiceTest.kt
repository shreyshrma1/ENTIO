package com.entio.semantic

import com.entio.core.DocumentAlignmentAction
import com.entio.core.DocumentAlignmentRecord
import com.entio.core.DocumentAnalysisWorkKey
import com.entio.core.DocumentAssertionClassification
import com.entio.core.DocumentBenchmarkExpectation
import com.entio.core.DocumentBenchmarkExpectationCategory
import com.entio.core.DocumentCompilationFailure
import com.entio.core.DocumentCompilationStatus
import com.entio.core.DocumentCompiledConfidenceDimensions
import com.entio.core.DocumentCompiledRecommendationResult
import com.entio.core.DocumentConfidenceDimensions
import com.entio.core.DocumentContentClassification
import com.entio.core.DocumentCoverageDisposition
import com.entio.core.DocumentCoverageDispositionKind
import com.entio.core.DocumentDiscovery
import com.entio.core.DocumentDiscoveryKind
import com.entio.core.DocumentEvidence
import com.entio.core.DocumentEvidenceId
import com.entio.core.DocumentEvidenceType
import com.entio.core.DocumentId
import com.entio.core.DocumentIndividualClassification
import com.entio.core.DocumentPlanOperation
import com.entio.core.DocumentPlanOperationKind
import com.entio.core.DocumentSemanticItemKind
import com.entio.core.DocumentSemanticOutcome
import com.entio.core.DocumentSemanticPlan
import com.entio.core.DocumentSemanticPlanItem
import com.entio.core.DocumentSemanticRecommendationGroup
import com.entio.core.DocumentTemporaryReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DocumentCoverageMetricServiceTest {
    @Test
    fun `accepts a complete ledger and rejects missing duplicate and unknown dispositions`(): Unit {
        val discoveries = listOf(discovery("concept-1"), discovery("rule-1", DocumentDiscoveryKind.ConditionalRule))
            .sortedBy(DocumentDiscovery::stableOrderingKey)
        val plan = plan(discoveries, executableGroup("group-1", discoveries.map { it.id }))
        val coverage = discoveries.map {
            DocumentCoverageDisposition(
                it.id,
                DocumentCoverageDispositionKind.ExecutableRecommendation,
                recommendationId = "group-1",
            )
        }.sortedBy(DocumentCoverageDisposition::stableOrderingKey)

        assertEquals(100, service.verify(discoveries, plan, coverage, emptyList(), emptyList()).semanticCoverage.percentage)
        assertFailsWith<IllegalArgumentException> {
            service.verify(discoveries, plan, coverage.dropLast(1), emptyList(), emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            service.verify(discoveries, plan, coverage + coverage.first(), emptyList(), emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            service.verify(
                discoveries,
                plan,
                coverage.dropLast(1) + DocumentCoverageDisposition(
                    "unknown",
                    DocumentCoverageDispositionKind.Blocked,
                    rationale = "No verified discovery exists.",
                ),
                emptyList(),
                emptyList(),
            )
        }
    }

    @Test
    fun `requires explicit administrative and illustrative dispositions and confirmation before execution`(): Unit {
        val administrative = discovery(
            "metadata-1",
            DocumentDiscoveryKind.Metadata,
            DocumentContentClassification.AdministrativeMetadata,
        )
        val illustrative = discovery(
            "individual-1",
            DocumentDiscoveryKind.Individual,
            individualClassification = DocumentIndividualClassification.Illustrative,
            assertionClassification = DocumentAssertionClassification.IllustrativeExample,
        )
        val discoveries = listOf(administrative, illustrative).sortedBy(DocumentDiscovery::stableOrderingKey)
        val plan = plan(discoveries, executableGroup("group-1", listOf(illustrative.id)))
        val excludedCoverage = listOf(
            DocumentCoverageDisposition(
                illustrative.id,
                DocumentCoverageDispositionKind.IllustrativeExample,
            ),
            DocumentCoverageDisposition(
                administrative.id,
                DocumentCoverageDispositionKind.AdministrativeMetadata,
            ),
        ).sortedBy(DocumentCoverageDisposition::stableOrderingKey)

        val excluded = service.verify(discoveries, plan, excludedCoverage, emptyList(), emptyList())
        assertEquals(0, excluded.semanticCoverage.denominator)
        assertEquals(null, excluded.semanticCoverage.percentage)

        val executableCoverage = excludedCoverage.map {
            if (it.discoveryId == illustrative.id) {
                DocumentCoverageDisposition(
                    illustrative.id,
                    DocumentCoverageDispositionKind.ExecutableRecommendation,
                    recommendationId = "group-1",
                )
            } else {
                it
            }
        }
        assertFailsWith<IllegalArgumentException> {
            service.verify(discoveries, plan, executableCoverage, emptyList(), emptyList())
        }
        service.verify(
            discoveries,
            plan,
            executableCoverage,
            emptyList(),
            emptyList(),
            confirmedIllustrativeDiscoveryIds = setOf(illustrative.id),
        )
    }

    @Test
    fun `verifies matched existing blocked merged and critic references`(): Unit {
        val discoveries = listOf(discovery("concept-1"), discovery("concept-2"))
            .sortedBy(DocumentDiscovery::stableOrderingKey)
        val semanticPlan = plan(discoveries)
        val alignment = DocumentAlignmentRecord(
            id = "alignment-1",
            modelItemId = "model-1",
            action = DocumentAlignmentAction.LeaveUnchanged,
            rationale = "The verified ontology already carries this meaning.",
            ontologyFitConfidence = 95,
            ontologyFingerprint = "ontology",
            currentWorkFingerprint = "work",
        )
        val coverage = listOf(
            DocumentCoverageDisposition(
                "concept-1",
                DocumentCoverageDispositionKind.MatchedExisting,
                alignmentId = alignment.id,
            ),
            DocumentCoverageDisposition(
                "concept-2",
                DocumentCoverageDispositionKind.Blocked,
                rationale = "The required relationship remains ambiguous.",
            ),
        )

        service.verify(discoveries, semanticPlan, coverage, listOf(alignment), emptyList())
        assertFailsWith<IllegalArgumentException> {
            service.verify(discoveries, semanticPlan, coverage, emptyList(), emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            service.verify(
                discoveries,
                semanticPlan,
                listOf(
                    coverage.first(),
                    DocumentCoverageDisposition(
                        "concept-2",
                        DocumentCoverageDispositionKind.MergedIntoAnotherDiscovery,
                        relatedDiscoveryId = "missing",
                    ),
                ),
                listOf(alignment),
                emptyList(),
            )
        }
    }

    @Test
    fun `reports compilation and benchmark metrics separately with safe failures`(): Unit {
        val discovery = discovery("concept-1")
        val group = executableGroup("group-1", listOf(discovery.id), itemCount = 2)
        val semanticPlan = plan(listOf(discovery), group)
        val coverage = listOf(
            DocumentCoverageDisposition(
                discovery.id,
                DocumentCoverageDispositionKind.ExecutableRecommendation,
                recommendationId = group.id,
            ),
        )
        val failed = DocumentCompiledRecommendationResult(
            groupId = group.id,
            status = DocumentCompilationStatus.Blocked,
            failures = listOf(DocumentCompilationFailure("item-1", "unsupported-pattern", "Pattern is not supported.")),
            confidence = DocumentCompiledConfidenceDimensions(90, 85, 80, 0),
        )
        val expectations = listOf(
            DocumentBenchmarkExpectation("concept", DocumentBenchmarkExpectationCategory.Concept, true),
            DocumentBenchmarkExpectation("rule", DocumentBenchmarkExpectationCategory.Rule, false),
        ).sortedBy(DocumentBenchmarkExpectation::stableOrderingKey)

        val result = service.verify(
            listOf(discovery),
            semanticPlan,
            coverage,
            emptyList(),
            emptyList(),
            listOf(failed),
            expectations,
        )

        assertEquals(100, result.semanticCoverage.percentage)
        assertEquals(0, result.compilationSuccess.percentage)
        assertEquals(listOf("unsupported-pattern"), result.compilationSuccess.failureCodes)
        assertEquals(listOf(1, 0), result.benchmarkCounts.map { it.satisfied })
    }

    @Test
    fun `reports successful compilation and a not applicable zero denominator`(): Unit {
        val discovery = discovery("concept-1")
        val group = executableGroup("group-1", listOf(discovery.id))
        val compiled = DocumentCompiledRecommendationResult(
            groupId = group.id,
            status = DocumentCompilationStatus.Compiled,
            operations = listOf(
                DocumentPlanOperation(
                    id = "operation-1",
                    kind = DocumentPlanOperationKind.CreateClass,
                    order = 0,
                    declaration = DocumentTemporaryReference("new:class:Payment"),
                    expandedTypedEditCount = 1,
                ),
            ),
            confidence = DocumentCompiledConfidenceDimensions(90, 85, 80, 100),
        )
        val compiledResult = service.verify(
            listOf(discovery),
            plan(listOf(discovery), group),
            listOf(
                DocumentCoverageDisposition(
                    discovery.id,
                    DocumentCoverageDispositionKind.ExecutableRecommendation,
                    recommendationId = group.id,
                ),
            ),
            emptyList(),
            emptyList(),
            listOf(compiled),
        )
        assertEquals(100, compiledResult.compilationSuccess.percentage)

        val metadata = discovery(
            "metadata-1",
            DocumentDiscoveryKind.Metadata,
            DocumentContentClassification.AdministrativeMetadata,
        )
        val emptyResult = service.verify(
            listOf(metadata),
            plan(listOf(metadata)),
            listOf(
                DocumentCoverageDisposition(
                    metadata.id,
                    DocumentCoverageDispositionKind.AdministrativeMetadata,
                ),
            ),
            emptyList(),
            emptyList(),
        )
        assertEquals(null, emptyResult.semanticCoverage.percentage)
        assertEquals(null, emptyResult.compilationSuccess.percentage)
    }

    private val service = DocumentCompletenessMetricService()
    private val evidence = DocumentEvidence(
        DocumentEvidenceId("evidence-1"),
        DocumentEvidenceType.ExternalOntologyEvidence,
        entioRecordId = "verified-record",
    )

    private fun discovery(
        id: String,
        kind: DocumentDiscoveryKind = DocumentDiscoveryKind.Concept,
        contentClassification: DocumentContentClassification = DocumentContentClassification.BusinessContent,
        individualClassification: DocumentIndividualClassification? = null,
        assertionClassification: DocumentAssertionClassification = DocumentAssertionClassification.ExplicitFact,
    ): DocumentDiscovery = DocumentDiscovery(
        id = id,
        documentId = DocumentId("document-1"),
        kind = kind,
        contentClassification = contentClassification,
        assertionClassification = assertionClassification,
        description = "Verified $id meaning.",
        evidence = listOf(evidence),
        evidenceConfidence = 90,
        individualClassification = individualClassification,
    )

    private fun plan(
        discoveries: List<DocumentDiscovery>,
        group: DocumentSemanticRecommendationGroup? = null,
    ): DocumentSemanticPlan {
        val items = group?.itemIds.orEmpty().mapIndexed { index, id ->
            DocumentSemanticPlanItem(
                id = id,
                kind = DocumentSemanticItemKind.Class,
                label = "Item $index",
                discoveryIds = group!!.discoveryIds,
                evidenceIds = listOf(evidence.id),
                rationale = "The evidence supports this item.",
                outcome = group.outcome,
                confidence = DocumentConfidenceDimensions(90, 85, 80),
            )
        }.sortedBy(DocumentSemanticPlanItem::stableOrderingKey)
        return DocumentSemanticPlan(
            workKey = DocumentAnalysisWorkKey("a".repeat(64)),
            verifiedDiscoveryIds = discoveries.map(DocumentDiscovery::id).sorted(),
            criticFindingIds = emptyList(),
            items = items,
            groups = listOfNotNull(group).sortedBy(DocumentSemanticRecommendationGroup::stableOrderingKey),
        )
    }

    private fun executableGroup(
        id: String,
        discoveryIds: List<String>,
        itemCount: Int = 1,
    ): DocumentSemanticRecommendationGroup = DocumentSemanticRecommendationGroup(
        id = id,
        title = "Compile connected meaning",
        description = "Compile verified semantic meaning.",
        itemIds = (1..itemCount).map { "item-$it" }.sorted(),
        discoveryIds = discoveryIds.sorted(),
        evidenceIds = listOf(evidence.id),
        outcome = DocumentSemanticOutcome.Executable,
        rationale = "The group is supported and executable.",
        confidence = DocumentConfidenceDimensions(90, 85, 80),
    )
}
