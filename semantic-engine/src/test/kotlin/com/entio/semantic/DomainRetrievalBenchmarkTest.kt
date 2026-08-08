package com.entio.semantic

import com.entio.core.DomainModelingIntent
import com.entio.core.DomainOntologyProfileIdentity
import com.entio.core.DomainOperationKind
import com.entio.core.DomainRecommendationAction
import com.entio.core.ExternalEntityKind
import com.entio.core.Iri
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

class DomainRetrievalBenchmarkTest {
    private val root = Path.of("..", DomainCorpusIdentity.OUTPUT_RELATIVE_PATH).toAbsolutePath().normalize()
    private val modelRoot = Path.of(
        "..", "external-ontologies", "domain-search", "models", "all-MiniLM-L6-v2",
    ).toAbsolutePath().normalize()
    private val benchmarkPath = Path.of(
        "..", "docs", "decisions", "phase-13-retrieval-benchmark-v1.json",
    ).toAbsolutePath().normalize()
    private val indexFingerprint = DomainSearchAssetSupport.sha256(root.resolve(DomainSearchAssetSupport.SEARCH_MANIFEST))

    @Test
    fun developmentAndRegressionSetsMeetImplementationGates(): Unit {
        val sets = loadSets()
        DomainRecommendationService.open(root, modelRoot).use { service ->
            val development = evaluate(service, sets.getValue("development"))
            val regression = evaluate(service, sets.getValue("regression"))
            println("development=$development")
            println("regression=$regression")

            assertTrue(development.recallAt10 >= 0.85, development.toString())
            assertTrue(development.precisionAt3 >= 0.70, development.toString())
            assertTrue(regression.recallAt10 >= 0.85, regression.toString())
            assertTrue(regression.precisionAt3 >= 0.70, regression.toString())
            assertEquals(1.0, development.kindCorrectness, development.toString())
            assertEquals(1.0, regression.kindCorrectness, regression.toString())
            assertTrue(development.noMatchCorrectness >= 0.80, development.toString())
            assertTrue(regression.noMatchCorrectness >= 0.80, regression.toString())
        }
    }

    @Test
    fun lockedAcceptanceSetMeetsUntunedQualityGates(): Unit {
        val locked = loadSets().getValue("locked")
        DomainRecommendationService.open(root, modelRoot).use { service ->
            val metrics = evaluate(service, locked)
            println("locked=$metrics")

            assertTrue(metrics.recallAt10 >= 0.85, metrics.toString())
            assertTrue(metrics.precisionAt3 >= 0.70, metrics.toString())
            assertEquals(1.0, metrics.kindCorrectness, metrics.toString())
            assertEquals(1.0, metrics.hardNegativeActionSuppression, metrics.toString())
            assertTrue(metrics.noMatchCorrectness >= 0.80, metrics.toString())
            assertTrue(metrics.stableOrdering, metrics.toString())
        }
    }

    private fun evaluate(service: DomainRecommendationService, cases: List<BenchmarkCase>): BenchmarkMetrics {
        val positive = cases.filterNot(BenchmarkCase::noMatch)
        val noMatch = cases.filter(BenchmarkCase::noMatch)
        var correctKinds = 0
        var actionableCount = 0
        var hardNegativeActions = 0
        var hardNegativeCount = 0
        var orderingStable = true
        val outcomes = cases.associateWith { case ->
            val intent = intent(case)
            val first = service.recommend("benchmark-user", intent)
            val repeated = service.recommend("benchmark-user", intent)
            orderingStable = orderingStable &&
                first.recommendations.map { it.iri } == repeated.recommendations.map { it.iri }
            first.recommendations.filter { DomainRecommendationAction.Reuse in it.permittedActions }.forEach { result ->
                actionableCount += 1
                if (result.kind in case.allowedKinds) correctKinds += 1
                if (result.iri.value in case.hardNegativeIris) hardNegativeActions += 1
            }
            hardNegativeCount += case.hardNegativeIris.size
            first
        }
        val recall = positive.map { case ->
            val returned = outcomes.getValue(case).recommendations.take(10).map { it.iri.value }.toSet()
            case.relevantIris.count { it in returned }.toDouble() / case.relevantIris.size
        }.average()
        val precisionValues = positive.mapNotNull { case ->
            val actionable = outcomes.getValue(case).recommendations.take(3)
                .filter { DomainRecommendationAction.Reuse in it.permittedActions }
            if (actionable.isEmpty()) null else
                actionable.count { it.iri.value in case.relevantIris }.toDouble() / actionable.size
        }
        val precision = if (precisionValues.isEmpty()) 1.0 else precisionValues.average()
        val noMatchCorrectness = if (noMatch.isEmpty()) 1.0 else
            noMatch.count { outcomes.getValue(it).noConfidentMatch }.toDouble() / noMatch.size
        return BenchmarkMetrics(
            recallAt10 = recall,
            precisionAt3 = precision,
            kindCorrectness = if (actionableCount == 0) 1.0 else correctKinds.toDouble() / actionableCount,
            hardNegativeActionSuppression = if (hardNegativeCount == 0) 1.0 else
                1.0 - hardNegativeActions.toDouble() / hardNegativeCount,
            noMatchCorrectness = noMatchCorrectness,
            stableOrdering = orderingStable,
        )
    }

    private fun intent(case: BenchmarkCase): DomainModelingIntent = DomainModelingIntent(
        projectId = "benchmark-project",
        operationKind = DomainOperationKind.GlobalSemanticSearch,
        requestedKind = case.allowedKinds.singleOrNull(),
        draftLabel = case.query,
        requiredDomainIri = case.requiredDomainIri?.let(::Iri),
        requiredRangeIri = case.requiredRangeIri?.let(::Iri),
        projectFingerprint = "benchmark-project-v1",
        profileFingerprint = "benchmark-profile-v1",
        ontologyFingerprint = "benchmark-ontology-v1",
        currentWorkFingerprint = "benchmark-work-v1",
        packageFingerprint = DomainOntologyProfileIdentity.PACKAGE_FINGERPRINT,
        indexFingerprint = indexFingerprint,
        broadSearch = case.broadSearch,
    )

    private fun loadSets(): Map<String, List<BenchmarkCase>> {
        val loader = Load(LoadSettings.builder().setLabel("phase-13-benchmark-v1").build())
        val root = loader.loadFromString(Files.readString(benchmarkPath)) as Map<*, *>
        assertEquals("entio-domain-retrieval-benchmark-v1", root["schema"])
        val sets = root["sets"] as List<*>
        return sets.associate { value ->
            val set = value as Map<*, *>
            val name = set["name"] as String
            val cases = (set["cases"] as List<*>).map { caseValue ->
                val case = caseValue as Map<*, *>
                BenchmarkCase(
                    id = case["id"] as String,
                    query = case["query"] as String,
                    allowedKinds = (case["allowedKinds"] as List<*>).map { ExternalEntityKind.valueOf(it as String) }.toSet(),
                    relevantIris = (case["relevantIris"] as List<*>).map { it as String }.toSet(),
                    hardNegativeIris = (case["hardNegativeIris"] as List<*>).map { it as String }.toSet(),
                    noMatch = case["noMatch"] as Boolean,
                    broadSearch = case["broadSearch"] as? Boolean ?: false,
                    requiredDomainIri = case["requiredDomainIri"] as? String,
                    requiredRangeIri = case["requiredRangeIri"] as? String,
                )
            }
            name to cases
        }
    }

    private data class BenchmarkCase(
        val id: String,
        val query: String,
        val allowedKinds: Set<ExternalEntityKind>,
        val relevantIris: Set<String>,
        val hardNegativeIris: Set<String>,
        val noMatch: Boolean,
        val broadSearch: Boolean,
        val requiredDomainIri: String?,
        val requiredRangeIri: String?,
    )

    private data class BenchmarkMetrics(
        val recallAt10: Double,
        val precisionAt3: Double,
        val kindCorrectness: Double,
        val hardNegativeActionSuppression: Double,
        val noMatchCorrectness: Double,
        val stableOrdering: Boolean,
    )
}
