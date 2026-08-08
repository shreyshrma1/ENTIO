package com.entio.semantic

import com.entio.semantic.DomainSearchAssetSupport.IRI_FILE
import com.entio.semantic.DomainSearchAssetSupport.LEXICAL_FILE
import com.entio.semantic.DomainSearchAssetSupport.SEARCH_CHECKSUMS
import com.entio.semantic.DomainSearchAssetSupport.SEARCH_MANIFEST
import com.entio.semantic.DomainSearchAssetSupport.VECTOR_FILE
import com.entio.semantic.DomainSearchAssetSupport.int
import com.entio.semantic.DomainSearchAssetSupport.string
import java.nio.file.Files
import java.nio.file.Path

/** Offline integrity and reproducibility verifier for the local hybrid index. */
public object DomainSearchIndexVerifier {
    private val generatedFiles = listOf(LEXICAL_FILE, IRI_FILE, VECTOR_FILE, SEARCH_MANIFEST)

    @JvmStatic
    public fun main(args: Array<String>): Unit {
        require(args.size == 2) { "Expected the Phase 13 corpus root and local model root." }
        verify(Path.of(args[0]), Path.of(args[1]), regenerate = true)
    }

    public fun verify(root: Path, modelRoot: Path, regenerate: Boolean): Unit {
        verifyModel(modelRoot)
        val manifest = DomainSearchAssetSupport.mapping(root.resolve(SEARCH_MANIFEST))
        require(manifest.string("schema") == DomainSearchAssetSupport.INDEX_SCHEMA)
        require(manifest.int("entityCount") == DomainCorpusIdentity.EXPECTED_ENTITY_COUNT)
        require(manifest.string("descriptorSha256") == DomainSearchAssetSupport.sha256(root.resolve("descriptors-v1.jsonl")))
        require(manifest.string("modelSha256") == LocalSentenceEmbeddingService.MODEL_SHA256)
        require(manifest.string("tokenizerSha256") == LocalSentenceEmbeddingService.TOKENIZER_SHA256)
        require(manifest.string("tokenizerConfigSha256") == LocalSentenceEmbeddingService.TOKENIZER_CONFIG_SHA256)
        require(manifest.string("specialTokensSha256") == LocalSentenceEmbeddingService.SPECIAL_TOKENS_SHA256)
        require(manifest.string("modelConfigSha256") == LocalSentenceEmbeddingService.CONFIG_SHA256)
        require(manifest.string("modelLicense") == "Apache-2.0")
        require(manifest.string("modelNoticeSha256") == DomainSearchAssetSupport.sha256(modelRoot.resolve("NOTICE.md")))
        verifyChecksums(root)
        DomainSearchIndex.openLexical(root).use { index -> require(!index.vectorAvailable) }
        DomainSearchIndex.openFull(root).use { index -> require(index.vectorAvailable) }

        if (regenerate) {
            val temporary = Files.createTempDirectory("entio-domain-search-index-")
            try {
                Files.copy(root.resolve("descriptors-v1.jsonl"), temporary.resolve("descriptors-v1.jsonl"))
                Files.copy(root.resolve("manifest.yaml"), temporary.resolve("manifest.yaml"))
                DomainSearchIndexGenerator.generate(temporary, modelRoot)
                generatedFiles.forEach { relative ->
                    require(Files.readAllBytes(root.resolve(relative)).contentEquals(Files.readAllBytes(temporary.resolve(relative)))) {
                        "Generated domain search artifact drifted: $relative"
                    }
                }
            } finally {
                temporary.toFile().deleteRecursively()
            }
        }
    }

    private fun verifyModel(modelRoot: Path): Unit {
        require(Files.isRegularFile(modelRoot.resolve("LICENSE-APACHE-2.0.txt"))) { "Model license is missing." }
        require(Files.isRegularFile(modelRoot.resolve("NOTICE.md"))) { "Model NOTICE is missing." }
        LocalSentenceEmbeddingService.open(modelRoot).close()
    }

    private fun verifyChecksums(root: Path): Unit {
        val entries = Files.readAllLines(root.resolve(SEARCH_CHECKSUMS)).filter(String::isNotBlank).associate { line ->
            val parts = line.split("  ", limit = 2)
            require(parts.size == 2 && parts[0].matches(Regex("[0-9a-f]{64}"))) { "Malformed search checksum." }
            parts[1] to parts[0]
        }
        require(entries.size == generatedFiles.size) { "Unexpected domain search checksum count." }
        generatedFiles.forEach { relative ->
            require(entries[relative] == DomainSearchAssetSupport.sha256(root.resolve(relative))) {
                "Domain search checksum mismatch: $relative"
            }
        }
    }
}
