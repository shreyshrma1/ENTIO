package com.entio.semantic

import com.entio.core.DocumentAlignmentRecord
import com.entio.core.DocumentAssertionClassification
import com.entio.core.DocumentBenchmarkCategoryCount
import com.entio.core.DocumentBenchmarkExpectation
import com.entio.core.DocumentBenchmarkExpectationCategory
import com.entio.core.DocumentCompilationStatus
import com.entio.core.DocumentCompiledRecommendationResult
import com.entio.core.DocumentCompletenessMetrics
import com.entio.core.DocumentContentClassification
import com.entio.core.DocumentCoverageDisposition
import com.entio.core.DocumentCoverageDispositionKind
import com.entio.core.DocumentCriticFinding
import com.entio.core.DocumentDiscovery
import com.entio.core.DocumentDiscoveryKind
import com.entio.core.DocumentIndividualClassification
import com.entio.core.DocumentQualityMetric
import com.entio.core.DocumentSemanticOutcome
import com.entio.core.DocumentSemanticPlan
import com.entio.core.DocumentVerifiedSemanticPlan

/**
 * Verifies the deterministic discovery ledger and reports semantic coverage
 * separately from compilation success.
 */
public class DocumentCompletenessMetricService {
    public fun verify(
        discoveries: List<DocumentDiscovery>,
        semanticPlan: DocumentSemanticPlan,
        coverage: List<DocumentCoverageDisposition>,
        alignments: List<DocumentAlignmentRecord>,
        criticFindings: List<DocumentCriticFinding>,
        compilationResults: List<DocumentCompiledRecommendationResult> = emptyList(),
        benchmarkExpectations: List<DocumentBenchmarkExpectation> = emptyList(),
        confirmedIllustrativeDiscoveryIds: Set<String> = emptySet(),
    ): DocumentCompletenessMetrics {
        require(discoveries == discoveries.distinctBy(DocumentDiscovery::id)
            .sortedBy(DocumentDiscovery::stableOrderingKey)) {
            "Verified document discoveries must be sorted and unique."
        }
        require(coverage == coverage.distinctBy(DocumentCoverageDisposition::discoveryId)
            .sortedBy(DocumentCoverageDisposition::stableOrderingKey)) {
            "Every verified discovery requires exactly one sorted coverage disposition."
        }
        val discoveriesById = discoveries.associateBy(DocumentDiscovery::id)
        require(coverage.map(DocumentCoverageDisposition::discoveryId).toSet() == discoveriesById.keys) {
            "Coverage must contain exactly one disposition for every verified discovery."
        }
        require(semanticPlan.verifiedDiscoveryIds == discoveriesById.keys.sorted()) {
            "The semantic plan must name every verified discovery."
        }
        require(criticFindings == criticFindings.distinctBy(DocumentCriticFinding::id)
            .sortedBy(DocumentCriticFinding::stableOrderingKey)) {
            "Document critic findings must be sorted and unique."
        }
        require(semanticPlan.criticFindingIds == criticFindings.map(DocumentCriticFinding::id).sorted()) {
            "Every critic finding requires exactly one semantic-plan disposition."
        }

        val groupsById = semanticPlan.groups.associateBy { it.id }
        val alignmentsById = alignments.associateBy(DocumentAlignmentRecord::id)
        coverage.forEach { disposition ->
            val discovery = discoveriesById.getValue(disposition.discoveryId)
            when (disposition.kind) {
                DocumentCoverageDispositionKind.ExecutableRecommendation ->
                    require(groupsById[disposition.recommendationId]?.outcome == DocumentSemanticOutcome.Executable) {
                        "Executable coverage must reference an executable semantic group."
                    }
                DocumentCoverageDispositionKind.ReviewOnlyFinding ->
                    require(groupsById[disposition.recommendationId]?.outcome == DocumentSemanticOutcome.ReviewOnly) {
                        "Review-only coverage must reference a review-only semantic group."
                    }
                DocumentCoverageDispositionKind.MatchedExisting ->
                    require(alignmentsById.containsKey(disposition.alignmentId)) {
                        "Matched-existing coverage must reference a verified alignment."
                    }
                DocumentCoverageDispositionKind.MergedIntoAnotherDiscovery ->
                    require(discoveriesById.containsKey(disposition.relatedDiscoveryId)) {
                        "Merged coverage must reference another verified discovery."
                    }
                DocumentCoverageDispositionKind.AdministrativeMetadata ->
                    require(discovery.contentClassification == DocumentContentClassification.AdministrativeMetadata) {
                        "Only administrative metadata may use the administrative disposition."
                    }
                DocumentCoverageDispositionKind.IllustrativeExample ->
                    require(discovery.isIllustrative) {
                        "Only an illustrative example may use the illustrative disposition."
                    }
                else -> Unit
            }
            if (discovery.isIllustrative &&
                disposition.kind == DocumentCoverageDispositionKind.ExecutableRecommendation
            ) {
                require(discovery.id in confirmedIllustrativeDiscoveryIds) {
                    "An illustrative individual requires explicit creation confirmation."
                }
            }
        }

        require(benchmarkExpectations == benchmarkExpectations.distinctBy(DocumentBenchmarkExpectation::id)
            .sortedBy(DocumentBenchmarkExpectation::stableOrderingKey)) {
            "Benchmark expectations must be sorted and unique."
        }
        val importantDiscoveries = discoveries.filter { it.isImportant }
        val validImportantIds = coverage.map(DocumentCoverageDisposition::discoveryId)
            .toSet()
            .intersect(importantDiscoveries.map(DocumentDiscovery::id).toSet())
        val semanticCoverage = metric(validImportantIds.size, importantDiscoveries.size)
        val compilationSuccess = compilationMetric(semanticPlan, compilationResults)
        val benchmarkCounts = DocumentBenchmarkExpectationCategory.entries.mapNotNull { category ->
            benchmarkExpectations.filter { it.category == category }.takeIf { it.isNotEmpty() }?.let { expectations ->
                DocumentBenchmarkCategoryCount(category, expectations.count { it.satisfied }, expectations.size)
            }
        }

        return DocumentCompletenessMetrics(
            verifiedPlan = DocumentVerifiedSemanticPlan(semanticPlan, coverage),
            semanticCoverage = semanticCoverage,
            compilationSuccess = compilationSuccess,
            benchmarkCounts = benchmarkCounts,
        )
    }

    private fun compilationMetric(
        semanticPlan: DocumentSemanticPlan,
        results: List<DocumentCompiledRecommendationResult>,
    ): DocumentQualityMetric {
        require(results == results.distinctBy(DocumentCompiledRecommendationResult::groupId)
            .sortedBy(DocumentCompiledRecommendationResult::groupId)) {
            "Compilation results must be sorted and unique."
        }
        val executableGroups = semanticPlan.groups.filter { it.outcome == DocumentSemanticOutcome.Executable }
        val executableItemIds = executableGroups.flatMap { it.itemIds }.toSortedSet()
        val resultsByGroup = results.associateBy(DocumentCompiledRecommendationResult::groupId)
        require(resultsByGroup.keys.all { id -> executableGroups.any { it.id == id } }) {
            "Compilation results must reference executable semantic groups."
        }
        val compiledItemIds = executableGroups
            .filter { resultsByGroup[it.id]?.status == DocumentCompilationStatus.Compiled }
            .flatMap { it.itemIds }
            .toSet()
        val failureCodes = results.flatMap { result -> result.failures.map { it.safeCode } }.distinct().sorted()
        return metric(
            numerator = executableItemIds.count(compiledItemIds::contains),
            denominator = executableItemIds.size,
            failureCodes = failureCodes,
        )
    }

    private fun metric(
        numerator: Int,
        denominator: Int,
        failureCodes: List<String> = emptyList(),
    ): DocumentQualityMetric = DocumentQualityMetric(
        numerator = numerator,
        denominator = denominator,
        percentage = if (denominator == 0) null else numerator * 100 / denominator,
        failureCodes = failureCodes,
    )

    private val DocumentDiscovery.isIllustrative: Boolean
        get() = assertionClassification == DocumentAssertionClassification.IllustrativeExample ||
            individualClassification == DocumentIndividualClassification.Illustrative

    private val DocumentDiscovery.isImportant: Boolean
        get() = contentClassification == DocumentContentClassification.BusinessContent &&
            !isIllustrative &&
            kind in IMPORTANT_KINDS

    private companion object {
        val IMPORTANT_KINDS: Set<DocumentDiscoveryKind> = setOf(
            DocumentDiscoveryKind.Concept,
            DocumentDiscoveryKind.Relationship,
            DocumentDiscoveryKind.Requirement,
            DocumentDiscoveryKind.Control,
            DocumentDiscoveryKind.ConditionalRule,
            DocumentDiscoveryKind.Attribute,
            DocumentDiscoveryKind.Value,
            DocumentDiscoveryKind.Definition,
            DocumentDiscoveryKind.Individual,
        )
    }
}
