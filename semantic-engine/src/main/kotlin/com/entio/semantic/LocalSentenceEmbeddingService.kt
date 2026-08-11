package com.entio.semantic

import ai.djl.huggingface.tokenizers.Encoding
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

public data class LocalSentenceEmbedding(
    public val values: FloatArray,
    public val tokenCount: Int,
    public val truncated: Boolean,
)

/** Local, bounded sentence-transformer inference over the pinned Phase 13 assets. */
public class LocalSentenceEmbeddingService private constructor(
    private val tokenizer: HuggingFaceTokenizer,
    private val environment: OrtEnvironment,
    private val session: OrtSession,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val tokenizerLock = Any()

    public fun embed(input: String): LocalSentenceEmbedding {
        check(!closed.get()) { "The local sentence embedding service is closed." }
        require(input.isNotBlank()) { "Embedding input must not be blank." }
        require(input.toByteArray(Charsets.UTF_8).size <= MAX_INPUT_BYTES) {
            "Embedding input exceeds $MAX_INPUT_BYTES UTF-8 bytes."
        }
        val encoding = synchronized(tokenizerLock) { tokenizer.encode(input) }
        val ids = encoding.ids
        val attention = encoding.attentionMask
        val typeIds = encoding.typeIds
        require(ids.isNotEmpty() && ids.size <= MAX_TOKENS) { "Tokenizer produced an invalid token count." }
        require(attention.size == ids.size && typeIds.size == ids.size) { "Tokenizer tensor lengths do not match." }

        val inputs = linkedMapOf<String, OnnxTensor>()
        try {
            inputs["input_ids"] = OnnxTensor.createTensor(environment, arrayOf(ids))
            inputs["attention_mask"] = OnnxTensor.createTensor(environment, arrayOf(attention))
            if ("token_type_ids" in session.inputNames) {
                inputs["token_type_ids"] = OnnxTensor.createTensor(environment, arrayOf(typeIds))
            }
            session.run(inputs).use { result ->
                @Suppress("UNCHECKED_CAST")
                val hidden = result.get(0).value as? Array<Array<FloatArray>>
                    ?: error("The embedding model returned an unexpected output type.")
                require(hidden.size == 1 && hidden[0].size == ids.size) { "The embedding model output shape is invalid." }
                val values = meanPoolAndNormalize(hidden[0], attention)
                return LocalSentenceEmbedding(
                    values = values,
                    tokenCount = ids.size,
                    truncated = encoding.wasTruncated(),
                )
            }
        } finally {
            inputs.values.forEach(OnnxTensor::close)
        }
    }

    override fun close(): Unit {
        if (closed.compareAndSet(false, true)) {
            session.close()
            tokenizer.close()
        }
    }

    private fun meanPoolAndNormalize(tokens: Array<FloatArray>, attention: LongArray): FloatArray {
        require(tokens.isNotEmpty() && tokens.all { it.size == DIMENSION }) { "Embedding dimension is not $DIMENSION." }
        val pooled = FloatArray(DIMENSION)
        var included = 0
        tokens.forEachIndexed { tokenIndex, token ->
            if (attention[tokenIndex] == 1L) {
                included += 1
                for (dimension in 0 until DIMENSION) pooled[dimension] += token[dimension]
            }
        }
        require(included > 0) { "Embedding attention mask contains no included tokens." }
        for (dimension in pooled.indices) pooled[dimension] /= included.toFloat()
        var squaredNorm = 0.0
        pooled.forEach { value ->
            require(value.isFinite()) { "Embedding contains a non-finite value." }
            squaredNorm += value.toDouble() * value.toDouble()
        }
        require(squaredNorm > 0.0) { "Embedding model returned a zero vector." }
        val norm = sqrt(squaredNorm).toFloat()
        for (dimension in pooled.indices) pooled[dimension] /= norm
        return pooled
    }

    private fun Encoding.wasTruncated(): Boolean = exceedMaxLength() || overflowing.isNotEmpty()

    public companion object {
        public const val MODEL_ID: String = "sentence-transformers/all-MiniLM-L6-v2"
        public const val MODEL_REVISION: String = "94ea1512acaefbfe2e255b2d2ea4bf0d9d7b3dc3"
        public const val DIMENSION: Int = 384
        public const val MAX_TOKENS: Int = 256
        public const val MAX_INPUT_BYTES: Int = 65_536
        public const val MODEL_SHA256: String = "6fd5d72fe4589f189f8ebc006442dbb529bb7ce38f8082112682524616046452"
        public const val TOKENIZER_SHA256: String = "be50c3628f2bf5bb5e3a7f17b1f74611b2561a3a27eeab05e5aa30f411572037"
        public const val TOKENIZER_CONFIG_SHA256: String =
            "acb92769e8195aabd29b7b2137a9e6d6e25c476a4f15aa4355c233426c61576b"
        public const val SPECIAL_TOKENS_SHA256: String =
            "303df45a03609e4ead04bc3dc1536d0ab19b5358db685b6f3da123d05ec200e3"
        public const val CONFIG_SHA256: String = "953f9c0d463486b10a6871cc2fd59f223b2c70184f49815e7efbcab5d8908b41"

        public fun open(modelRoot: Path): LocalSentenceEmbeddingService {
            val runtimeInitializer = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "entio-domain-onnx-init").apply { isDaemon = true }
            }
            val environmentFuture = runtimeInitializer.submit<OrtEnvironment> {
                OrtEnvironment.getEnvironment("entio-domain-embedding")
            }
            val files = mapOf(
                "model.onnx" to MODEL_SHA256,
                "tokenizer.json" to TOKENIZER_SHA256,
                "tokenizer_config.json" to TOKENIZER_CONFIG_SHA256,
                "special_tokens_map.json" to SPECIAL_TOKENS_SHA256,
                "config.json" to CONFIG_SHA256,
            )
            try {
                files.forEach { (relative, expected) ->
                    val path = modelRoot.resolve(relative)
                    require(Files.isRegularFile(path)) { "Missing local embedding asset: $relative" }
                    require(sha256(path) == expected) { "Local embedding asset checksum mismatch: $relative" }
                }
                val tokenizer = HuggingFaceTokenizer.builder()
                    .optTokenizerPath(modelRoot.resolve("tokenizer.json"))
                    .optTokenizerConfigPath(modelRoot.resolve("tokenizer_config.json").toString())
                    .optAddSpecialTokens(true)
                    .optTruncation(true)
                    .optWithOverflowingTokens(true)
                    .optMaxLength(MAX_TOKENS)
                    .optPadding(false)
                    .build()
                try {
                    val environment = environmentFuture.get()
                    val session = OrtSession.SessionOptions().use { options ->
                        // The model is already exported for inference. Basic
                        // rewrites preserve its approved output contract
                        // without extended per-process optimization.
                        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                        // Four intra-op workers retain bounded transformer
                        // parallelism without machine-sized session pools.
                        options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
                        options.setInterOpNumThreads(1)
                        options.setIntraOpNumThreads(4)
                        options.setSessionLogVerbosityLevel(0)
                        environment.createSession(modelRoot.resolve("model.onnx").toString(), options)
                    }
                    require(session.inputNames.containsAll(setOf("input_ids", "attention_mask"))) {
                        "The embedding model input contract is unsupported."
                    }
                    return LocalSentenceEmbeddingService(tokenizer, environment, session)
                } catch (exception: Exception) {
                    tokenizer.close()
                    throw exception
                }
            } finally {
                runtimeInitializer.shutdownNow()
            }
        }

        private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(path))
            .joinToString("") { "%02x".format(it) }
    }
}
