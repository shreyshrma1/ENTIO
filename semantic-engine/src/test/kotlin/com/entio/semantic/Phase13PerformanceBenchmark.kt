package com.entio.semantic

import com.entio.core.DomainModelingIntent
import com.entio.core.DomainOntologyProfileIdentity
import com.entio.core.DomainOperationKind
import com.entio.core.ExternalEntityKind
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.system.measureNanoTime

/** Isolated Slice 12 benchmark; run through the phase13PerformanceBenchmark task. */
public object Phase13PerformanceBenchmark {
    private val searchRoot = Path.of("..", DomainCorpusIdentity.OUTPUT_RELATIVE_PATH).toAbsolutePath().normalize()
    private val modelRoot = Path.of(
        "..", "external-ontologies", "domain-search", "models", "all-MiniLM-L6-v2",
    ).toAbsolutePath().normalize()
    private val indexFingerprint = DomainSearchAssetSupport.sha256(searchRoot.resolve(DomainSearchAssetSupport.SEARCH_MANIFEST))

    @JvmStatic
    public fun main(args: Array<String>): Unit {
        require(args.isEmpty()) { "The Phase 13 performance benchmark accepts no arguments." }
        DomainSearchIndex.openFull(searchRoot).close()
        val warmLoadMillis = millis { DomainSearchIndex.openFull(searchRoot).close() }
        lateinit var firstService: LocalSentenceEmbeddingService
        val firstServiceOpenMillis = millis { firstService = LocalSentenceEmbeddingService.open(modelRoot) }
        val firstEmbeddingMillis = firstService.use { service ->
            millis { service.embed("financial agreement") }
        }
        val firstInferenceMillis = firstServiceOpenMillis + firstEmbeddingMillis
        val lexicalP95 = lexicalP95()

        DomainRecommendationService.open(searchRoot, modelRoot).use { service ->
            service.recommend("warmup", intent("agreement", 0))
            val defaultSamples = (1..24).map { sample ->
                millis { service.recommend("default-$sample", intent(QUERIES[sample % QUERIES.size], sample)) }
            }
            val broadSamples = (1..24).map { sample ->
                millis {
                    service.recommend(
                        "broad-$sample",
                        intent(QUERIES[sample % QUERIES.size], 100 + sample).copy(broadSearch = true),
                    )
                }
            }
            val concurrent = Executors.newFixedThreadPool(8)
            val concurrentSamples = try {
                concurrent.invokeAll((1..8).map { sample ->
                    Callable {
                        millis {
                            service.recommend(
                                "concurrent-$sample",
                                intent(QUERIES[sample % QUERIES.size], 200 + sample),
                            )
                        }
                    }
                }).map { it.get() }
            } finally {
                concurrent.shutdownNow()
            }
            val defaultP95 = percentile95(defaultSamples)
            val broadP95 = percentile95(broadSamples)
            val concurrentP95 = percentile95(concurrentSamples)
            val assetBytes = directoryBytes(searchRoot) + directoryBytes(modelRoot)
            println(
                "phase13Performance=" + mapOf(
                    "warmIndexLoadMs" to warmLoadMillis,
                    "firstServiceOpenMs" to firstServiceOpenMillis,
                    "firstEmbeddingMs" to firstEmbeddingMillis,
                    "firstLocalInferenceMs" to firstInferenceMillis,
                    "warmDefaultP95Ms" to defaultP95,
                    "warmLexicalStructuralP95Ms" to lexicalP95,
                    "warmBroadP95Ms" to broadP95,
                    "concurrent8P95Ms" to concurrentP95,
                    "assetBytes" to assetBytes,
                ),
            )
            require(warmLoadMillis <= 3_000) { "warm index load: $warmLoadMillis ms" }
            require(firstInferenceMillis <= 5_000) { "first local inference: $firstInferenceMillis ms" }
            require(defaultP95 <= 300) { "warm default p95: $defaultP95 ms" }
            require(lexicalP95 <= 150) { "warm lexical-structural p95: $lexicalP95 ms" }
            require(broadP95 <= 750) { "warm broad p95: $broadP95 ms" }
            require(concurrentP95 <= 1_000) { "eight-request concurrent p95: $concurrentP95 ms" }
            require(assetBytes <= 250L * 1024 * 1024) { "model plus index assets: $assetBytes bytes" }
        }
    }

    private fun intent(query: String, sequence: Int): DomainModelingIntent = DomainModelingIntent(
        projectId = "performance-$sequence",
        operationKind = DomainOperationKind.GlobalSemanticSearch,
        requestedKind = ExternalEntityKind.Class,
        draftLabel = query,
        projectFingerprint = "project-$sequence",
        profileFingerprint = "profile-$sequence",
        ontologyFingerprint = "ontology-$sequence",
        currentWorkFingerprint = "work-$sequence",
        packageFingerprint = DomainOntologyProfileIdentity.PACKAGE_FINGERPRINT,
        indexFingerprint = indexFingerprint,
    )

    private fun lexicalP95(): Long {
        val copy = Files.createTempDirectory("entio-domain-lexical-performance")
        listOf(
            "manifest.yaml",
            "descriptors-v1.jsonl",
            "foundation-profile-v1.json",
            DomainSearchAssetSupport.SEARCH_MANIFEST,
            DomainSearchAssetSupport.LEXICAL_FILE,
        ).forEach { relative ->
            val target = copy.resolve(relative)
            Files.createDirectories(target.parent)
            Files.copy(searchRoot.resolve(relative), target)
        }
        return DomainRecommendationService.open(copy, modelRoot).use { service ->
            percentile95((1..24).map { sample ->
                millis { service.recommend("lexical-$sample", intent(QUERIES[sample % QUERIES.size], 300 + sample)) }
            })
        }
    }

    private fun millis(block: () -> Unit): Long = measureNanoTime(block) / 1_000_000

    private fun percentile95(samples: List<Long>): Long {
        val sorted = samples.sorted()
        return sorted[(ceil(sorted.size * 0.95).toInt() - 1).coerceAtLeast(0)]
    }

    private fun directoryBytes(root: Path): Long = Files.walk(root).use { paths ->
        paths.filter(Files::isRegularFile).mapToLong(Files::size).sum()
    }

    private val QUERIES = listOf("agreement", "loan", "legal entity", "monetary amount", "payment", "owner")
}
