package com.entio.semantic

import com.entio.semantic.DomainSearchAssetSupport.IRI_FILE
import com.entio.semantic.DomainSearchAssetSupport.LEXICAL_FILE
import com.entio.semantic.DomainSearchAssetSupport.SEARCH_CHECKSUMS
import com.entio.semantic.DomainSearchAssetSupport.SEARCH_MANIFEST
import com.entio.semantic.DomainSearchAssetSupport.VECTOR_FILE
import com.entio.semantic.DomainSearchAssetSupport.sha256
import com.entio.semantic.DomainSearchAssetSupport.string
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

/** Offline generator for deterministic lexical documents and exact-scan vectors. */
public object DomainSearchIndexGenerator {
    @JvmStatic
    public fun main(args: Array<String>): Unit {
        require(args.size == 2) { "Expected the Phase 13 corpus root and local model root." }
        generate(Path.of(args[0]), Path.of(args[1]))
    }

    public fun generate(outputRoot: Path, modelRoot: Path): Unit {
        val descriptorPath = outputRoot.resolve("descriptors-v1.jsonl")
        require(Files.isRegularFile(descriptorPath)) { "Phase 13 descriptors are missing." }
        val documents = DomainSearchAssetSupport.readDocuments(descriptorPath)
        require(documents.size == DomainCorpusIdentity.EXPECTED_ENTITY_COUNT)
        require(documents.map(DomainSearchDocument::iri) == documents.map(DomainSearchDocument::iri).sorted())
        require(documents.map(DomainSearchDocument::iri).distinct().size == documents.size)
        require(documents.all { it.descriptorText.isNotBlank() }) { "Embedding text must not be blank." }

        val lexicalPath = outputRoot.resolve(LEXICAL_FILE)
        val iriPath = outputRoot.resolve(IRI_FILE)
        val vectorPath = outputRoot.resolve(VECTOR_FILE)
        Files.createDirectories(lexicalPath.parent)
        Files.createDirectories(vectorPath.parent)
        Files.writeString(
            lexicalPath,
            documents.joinToString(separator = "\n", postfix = "\n", transform = DomainSearchDocument::toJson),
        )
        Files.writeString(iriPath, documents.joinToString(separator = "\n", postfix = "\n") { it.iri })

        val vectorBytes = ByteBuffer.allocate(
            documents.size * LocalSentenceEmbeddingService.DIMENSION * Float.SIZE_BYTES,
        ).order(ByteOrder.LITTLE_ENDIAN)
        LocalSentenceEmbeddingService.open(modelRoot).use { service ->
            documents.forEach { document ->
                val vector = service.embed(document.descriptorText).values
                require(vector.size == LocalSentenceEmbeddingService.DIMENSION)
                vector.forEach(vectorBytes::putFloat)
            }
        }
        Files.write(vectorPath, vectorBytes.array())

        val descriptorManifest = DomainSearchAssetSupport.mapping(outputRoot.resolve("manifest.yaml"))
        val orderedIriFingerprint = sha256(
            documents.joinToString(separator = "\n", postfix = "\n") { it.iri }.toByteArray(Charsets.UTF_8),
        )
        val orderedRecordFingerprint = sha256(
            documents.joinToString(separator = "\n", postfix = "\n") { it.recordFingerprint }
                .toByteArray(Charsets.UTF_8),
        )
        val manifestPath = outputRoot.resolve(SEARCH_MANIFEST)
        Files.writeString(
            manifestPath,
            buildString {
                appendLine("schema: ${DomainSearchAssetSupport.INDEX_SCHEMA}")
                appendLine("sourceId: fibo")
                appendLine("release: master_2026Q2")
                appendLine("packageFingerprint: ${descriptorManifest.string("packageFingerprint")}")
                appendLine("descriptorContract: ${DomainCorpusIdentity.DESCRIPTOR_CONTRACT}")
                appendLine("descriptorSha256: ${sha256(descriptorPath)}")
                appendLine("entityCount: ${documents.size}")
                appendLine("orderedIriFingerprint: $orderedIriFingerprint")
                appendLine("orderedRecordFingerprint: $orderedRecordFingerprint")
                appendLine("lexicalContract: ${DomainSearchAssetSupport.LEXICAL_CONTRACT}")
                appendLine("luceneVersion: 10.5.0")
                appendLine("lexicalSha256: ${sha256(lexicalPath)}")
                appendLine("modelId: ${LocalSentenceEmbeddingService.MODEL_ID}")
                appendLine("modelRevision: ${LocalSentenceEmbeddingService.MODEL_REVISION}")
                appendLine("modelSha256: ${LocalSentenceEmbeddingService.MODEL_SHA256}")
                appendLine("tokenizerSha256: ${LocalSentenceEmbeddingService.TOKENIZER_SHA256}")
                appendLine("tokenizerConfigSha256: ${LocalSentenceEmbeddingService.TOKENIZER_CONFIG_SHA256}")
                appendLine("specialTokensSha256: ${LocalSentenceEmbeddingService.SPECIAL_TOKENS_SHA256}")
                appendLine("modelConfigSha256: ${LocalSentenceEmbeddingService.CONFIG_SHA256}")
                appendLine("modelLicense: Apache-2.0")
                appendLine("modelNoticeSha256: ${sha256(modelRoot.resolve("NOTICE.md"))}")
                appendLine("textContract: ${DomainSearchAssetSupport.TEXT_CONTRACT}")
                appendLine("tokenLimit: ${LocalSentenceEmbeddingService.MAX_TOKENS}")
                appendLine("pooling: attention-mask-mean")
                appendLine("normalization: l2")
                appendLine("vectorContract: ${DomainSearchAssetSupport.VECTOR_CONTRACT}")
                appendLine("dimension: ${LocalSentenceEmbeddingService.DIMENSION}")
                appendLine("similarity: cosine-exact-scan")
                appendLine("byteOrder: little-endian")
                appendLine("vectorBytes: ${Files.size(vectorPath)}")
                appendLine("iriSha256: ${sha256(iriPath)}")
                appendLine("vectorSha256: ${sha256(vectorPath)}")
                appendLine("graphContextContract: ${DomainCorpusIdentity.GRAPH_CONTEXT_CONTRACT}")
                appendLine("rankingContract: phase-13-hybrid-ranking-v1")
            },
        )
        val checksumsPath = outputRoot.resolve(SEARCH_CHECKSUMS)
        Files.createDirectories(checksumsPath.parent)
        Files.writeString(
            checksumsPath,
            listOf(LEXICAL_FILE, IRI_FILE, VECTOR_FILE, SEARCH_MANIFEST).joinToString("\n", postfix = "\n") {
                "${sha256(outputRoot.resolve(it))}  $it"
            },
        )
    }
}
