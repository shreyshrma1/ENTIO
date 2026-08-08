package com.entio.semantic

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalSentenceEmbeddingServiceTest {
    private val modelRoot = Path.of(
        "..",
        "external-ontologies",
        "domain-search",
        "models",
        "all-MiniLM-L6-v2",
    ).toAbsolutePath().normalize()

    @Test
    fun matchesPinnedPythonReferenceWithinTolerance(): Unit {
        val expectedPrefix = floatArrayOf(
            -0.088063985f,
            0.036235727f,
            -0.05322536f,
            -0.019421669f,
            -0.093973055f,
            0.02230247f,
            0.035877176f,
            0.057184864f,
            0.0312181f,
            -0.020825818f,
            0.0053762044f,
            -0.026107393f,
            0.0148317125f,
            0.01869012f,
            -0.0071443156f,
            0.009893011f,
        )

        LocalSentenceEmbeddingService.open(modelRoot).use { service ->
            val embedding = service.embed("a financial contract between a lender and borrower")

            assertEquals(12, embedding.tokenCount)
            assertFalse(embedding.truncated)
            assertEquals(LocalSentenceEmbeddingService.DIMENSION, embedding.values.size)
            expectedPrefix.indices.forEach { index ->
                assertTrue(abs(expectedPrefix[index] - embedding.values[index]) <= 2e-6f, "dimension $index")
            }
            assertTrue(abs(norm(embedding.values) - 1.0) <= 1e-5)
        }
    }

    @Test
    fun truncatesAtApprovedTokenLimitWithoutPadding(): Unit {
        LocalSentenceEmbeddingService.open(modelRoot).use { service ->
            val embedding = service.embed("financial ".repeat(1_000))

            assertEquals(LocalSentenceEmbeddingService.MAX_TOKENS, embedding.tokenCount)
            assertTrue(embedding.truncated)
            assertEquals(LocalSentenceEmbeddingService.DIMENSION, embedding.values.size)
        }
    }

    @Test
    fun rejectsBlankAndOversizedInput(): Unit {
        LocalSentenceEmbeddingService.open(modelRoot).use { service ->
            assertFailsWith<IllegalArgumentException> { service.embed("   ") }
            assertFailsWith<IllegalArgumentException> {
                service.embed("x".repeat(LocalSentenceEmbeddingService.MAX_INPUT_BYTES + 1))
            }
        }
    }

    @Test
    fun repeatedAndConcurrentInferenceIsDeterministicAndFinite(): Unit {
        LocalSentenceEmbeddingService.open(modelRoot).use { service ->
            val expected = service.embed("business loan agreement").values
            assertTrue(expected.all(Float::isFinite))
            assertTrue(expected.contentEquals(service.embed("business loan agreement").values))
            val executor = Executors.newFixedThreadPool(4)
            try {
                val tasks = List(16) { Callable { service.embed("business loan agreement").values } }
                executor.invokeAll(tasks).forEach { future -> assertTrue(expected.contentEquals(future.get())) }
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun closeReleasesOwnedResourcesAndIsIdempotent(): Unit {
        val service = LocalSentenceEmbeddingService.open(modelRoot)
        service.close()
        service.close()

        assertFailsWith<IllegalStateException> { service.embed("agreement") }
    }

    @Test
    fun missingOrWrongModelAssetsFailBeforeInference(): Unit {
        val missing = Files.createTempDirectory("entio-local-embedding-missing")
        assertFailsWith<IllegalArgumentException> { LocalSentenceEmbeddingService.open(missing) }
        val corrupt = Files.createTempDirectory("entio-local-embedding-corrupt")
        Files.writeString(corrupt.resolve("model.onnx"), "not the approved model")

        val failure = assertFailsWith<IllegalArgumentException> { LocalSentenceEmbeddingService.open(corrupt) }

        assertTrue(failure.message.orEmpty().contains("checksum mismatch"))
    }

    private fun norm(values: FloatArray): Double = sqrt(values.sumOf { it.toDouble() * it.toDouble() })
}
