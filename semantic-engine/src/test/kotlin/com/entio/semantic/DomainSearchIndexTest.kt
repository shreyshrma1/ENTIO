package com.entio.semantic

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DomainSearchIndexTest {
    private val root = Path.of("..", DomainCorpusIdentity.OUTPUT_RELATIVE_PATH).toAbsolutePath().normalize()
    private val modelRoot = Path.of(
        "..", "external-ontologies", "domain-search", "models", "all-MiniLM-L6-v2",
    ).toAbsolutePath().normalize()

    @Test
    fun generatedAssetsHaveExactIdentityDimensionAndNormalization(): Unit {
        val iris = Files.readAllLines(root.resolve(DomainSearchAssetSupport.IRI_FILE)).filter(String::isNotBlank)
        val bytes = Files.readAllBytes(root.resolve(DomainSearchAssetSupport.VECTOR_FILE))

        assertEquals(DomainCorpusIdentity.EXPECTED_ENTITY_COUNT, iris.size)
        assertEquals(iris.sorted(), iris)
        assertEquals(iris.size, iris.distinct().size)
        assertEquals(7_033_344, bytes.size)
        DomainSearchIndex.openFull(root).use { assertTrue(it.vectorAvailable) }
    }

    @Test
    fun lexicalAndVectorIndexesJoinOnCanonicalIdentity(): Unit {
        val firstIri = Files.readAllLines(root.resolve(DomainSearchAssetSupport.IRI_FILE)).first()
        val firstVector = readFirstVector(root.resolve(DomainSearchAssetSupport.VECTOR_FILE))
        DomainSearchIndex.openFull(root).use { index ->
            val lexical = index.searchLexical("negotiated understanding", 10)
            val vector = index.searchVector(firstVector, 10)

            assertTrue(lexical.any { it.iri.endsWith("/Agreement") })
            assertEquals(firstIri, vector.first().iri)
            assertTrue(abs(vector.first().score - 1.0f) <= 1e-5f)
            assertEquals(vector, index.searchVector(firstVector, 10))
        }
    }

    @Test
    fun corruptVectorsFailFullLoadWhileLexicalOnlyModeRemainsAvailable(): Unit {
        val copy = Files.createTempDirectory("entio-domain-search-corrupt")
        copySearchAssets(copy)
        val vectorPath = copy.resolve(DomainSearchAssetSupport.VECTOR_FILE)
        val bytes = Files.readAllBytes(vectorPath)
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        Files.write(vectorPath, bytes)

        assertFailsWith<IllegalArgumentException> { DomainSearchIndex.openFull(copy) }
        DomainSearchIndex.openLexical(copy).use { index ->
            assertFalse(index.vectorAvailable)
            assertTrue(index.searchLexical("agreement").isNotEmpty())
        }
    }

    @Test
    fun missingAndWrongModelContractsAreRejected(): Unit {
        val missing = Files.createTempDirectory("entio-domain-search-missing")
        assertFailsWith<Exception> { DomainSearchIndex.openFull(missing) }
        val copy = Files.createTempDirectory("entio-domain-search-wrong-model")
        copySearchAssets(copy)
        val manifest = copy.resolve(DomainSearchAssetSupport.SEARCH_MANIFEST)
        Files.writeString(
            manifest,
            Files.readString(manifest).replace(
                LocalSentenceEmbeddingService.MODEL_REVISION,
                "wrong-model-revision",
            ),
        )

        val failure = assertFailsWith<IllegalArgumentException> { DomainSearchIndex.openFull(copy) }
        assertTrue(failure.message.orEmpty().contains("revision"))
    }

    @Test
    fun concurrentQueriesAreSafeAndCloseIsEnforced(): Unit {
        val index = DomainSearchIndex.openFull(root)
        val expected = index.searchLexical("financial contract", 20)
        val executor = Executors.newFixedThreadPool(4)
        try {
            val tasks = List(20) { Callable { index.searchLexical("financial contract", 20) } }
            executor.invokeAll(tasks).forEach { assertEquals(expected, it.get()) }
        } finally {
            executor.shutdownNow()
        }
        index.close()
        index.close()
        assertFailsWith<IllegalStateException> { index.searchLexical("agreement") }
    }

    @Test
    fun exactScanStaysWithinApprovedInteractiveBound(): Unit {
        val query = readFirstVector(root.resolve(DomainSearchAssetSupport.VECTOR_FILE))
        DomainSearchIndex.openFull(root).use { index ->
            repeat(50) { index.searchVector(query) }
            val durations = LongArray(250) {
                val started = System.nanoTime()
                index.searchVector(query)
                System.nanoTime() - started
            }.sorted()
            val p99Millis = durations[(durations.size * 99 / 100)].toDouble() / 1_000_000.0
            assertTrue(p99Millis < 25.0, "exact-scan p99 was $p99Millis ms")
        }
    }

    @Test
    fun verifierAcceptsCommittedOfflineAssetsWithoutRegeneration(): Unit {
        DomainSearchIndexVerifier.verify(root, modelRoot, regenerate = false)
    }

    private fun readFirstVector(path: Path): FloatArray {
        val buffer = ByteBuffer.wrap(Files.readAllBytes(path)).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(LocalSentenceEmbeddingService.DIMENSION) { buffer.float }
    }

    private fun copySearchAssets(target: Path): Unit {
        listOf(
            "descriptors-v1.jsonl",
            DomainSearchAssetSupport.LEXICAL_FILE,
            DomainSearchAssetSupport.IRI_FILE,
            DomainSearchAssetSupport.VECTOR_FILE,
            DomainSearchAssetSupport.SEARCH_MANIFEST,
            DomainSearchAssetSupport.SEARCH_CHECKSUMS,
        ).forEach { relative ->
            val destination = target.resolve(relative)
            Files.createDirectories(destination.parent)
            Files.copy(root.resolve(relative), destination)
        }
    }
}
