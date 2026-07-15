package com.entio.semantic

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Verifies immutable package assets and deterministic generated indexes offline. */
public object FiboPackageVerifier {
    private const val SOURCE_ARCHIVE = "source/fibo-master_2026Q2-f59157f.zip"
    private const val SOURCE_ARCHIVE_SHA256 = "18947607581fc65f76db846a455440cca6efc596b52d2cacf32c43530c312bd9"
    private val GENERATED_FILES = listOf(
        "manifest.yaml",
        "indexes/catalog-v1.jsonl",
        "indexes/catalog-metadata-v1.json",
        "indexes/ontology-iri-map-v1.json",
        "indexes/curated-foundations-v1.json",
        "checksums/sha256sums.txt",
    )

    @JvmStatic
    public fun main(args: Array<String>): Unit {
        require(args.size == 1) { "Expected the FIBO package root." }
        verify(Path.of(args[0]))
    }

    public fun verify(packageRoot: Path): Unit {
        // The pinned local RDF/XML files contain large vocabulary entity declarations.
        // Verification reads only committed assets and never resolves external entities.
        System.setProperty("jdk.xml.totalEntitySizeLimit", "0")
        System.setProperty("jdk.xml.maxGeneralEntitySizeLimit", "0")
        System.setProperty("jdk.xml.maxParameterEntitySizeLimit", "0")
        System.setProperty("jdk.xml.entityExpansionLimit", "0")
        val manifestPath = packageRoot.resolve("manifest.yaml")
        require(Files.exists(manifestPath)) { "Missing FIBO manifest.yaml." }
        val manifest = Files.readString(manifestPath)
        require("release: master_2026Q2" in manifest) { "FIBO release does not match the approved package." }
        require("commitSha: f59157fe156e3d91b1c045222d0a7dc06b7d78a2" in manifest) {
            "FIBO commit does not match the approved package."
        }
        require("packageSchema: entio-fibo-package-v1" in manifest) { "Package schema is not approved." }
        require("catalogSchema: fibo-catalog-v1" in manifest) { "Catalog schema is not approved." }
        require("checksumAlgorithm: SHA-256" in manifest) { "Checksum algorithm is not approved." }
        require("commonsVersion: 1.3" in manifest) { "Commons version is not approved." }
        require("sourceArchivePath: $SOURCE_ARCHIVE" in manifest) { "Pinned source archive path is missing." }
        require("sourceArchiveSha256: $SOURCE_ARCHIVE_SHA256" in manifest) {
            "Pinned source archive checksum does not match the approved archive."
        }
        require(sha256(packageRoot.resolve(SOURCE_ARCHIVE)) == SOURCE_ARCHIVE_SHA256) {
            "Pinned source archive was modified."
        }
        require(Files.exists(packageRoot.resolve("indexes/catalog-v1.jsonl"))) { "Missing generated catalog index." }
        require(Files.exists(packageRoot.resolve("indexes/ontology-iri-map-v1.json"))) { "Missing ontology IRI map." }
        require(Files.exists(packageRoot.resolve("indexes/curated-foundations-v1.json"))) { "Missing curated package index." }
        require(Files.exists(packageRoot.resolve("ATTRIBUTION.md"))) { "Missing attribution metadata." }
        require(Files.exists(packageRoot.resolve("LICENSE-FIBO-MIT.txt"))) { "Missing FIBO license." }
        require(Files.exists(packageRoot.resolve("LICENSE-OMG-COMMONS-MIT.txt"))) { "Missing Commons license." }

        val assetChecksums = parseManifestChecksums(manifest)
        require(assetChecksums.isNotEmpty()) { "Manifest has no asset checksums." }
        assetChecksums.forEach { (relative, expected) ->
            val path = packageRoot.resolve(relative)
            require(Files.exists(path)) { "Manifest asset is missing: $relative" }
            require(sha256(path) == expected) { "Manifest asset checksum mismatch: $relative" }
        }
        val expectedFingerprint = sha256Text(assetChecksums.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" })
        require("packageFingerprint: $expectedFingerprint" in manifest) { "Package fingerprint is invalid." }
        verifyChecksumLedger(packageRoot, assetChecksums)

        val tempOutput = Files.createTempDirectory("entio-fibo-catalog-")
        try {
            FiboCatalogGenerator.generate(packageRoot, tempOutput)
            GENERATED_FILES.forEach { relative ->
                val expected = Files.readAllBytes(packageRoot.resolve(relative))
                val actual = Files.readAllBytes(tempOutput.resolve(relative))
                require(expected.contentEquals(actual)) {
                    "Generated package output drifted: $relative " +
                        "(committed=${sha256Bytes(expected)}, regenerated=${sha256Bytes(actual)}, " +
                        "firstDifference=${firstDifference(expected, actual)})"
                }
            }
        } finally {
            tempOutput.toFile().deleteRecursively()
        }
    }

    private fun parseManifestChecksums(manifest: String): Map<String, String> {
        val checksumPattern = Regex("(?m)^  - path: (.+)\\n    sha256: ([0-9a-f]{64})$")
        return checksumPattern.findAll(manifest).associate { match -> match.groupValues[1] to match.groupValues[2] }
    }

    private fun verifyChecksumLedger(packageRoot: Path, assets: Map<String, String>): Unit {
        val ledger = packageRoot.resolve("checksums/sha256sums.txt")
        require(Files.exists(ledger)) { "Missing checksum ledger." }
        val records = Files.readAllLines(ledger).filter { it.isNotBlank() }.associate { line ->
            val parts = line.split("  ", limit = 2)
            require(parts.size == 2) { "Malformed checksum ledger entry." }
            parts[1] to parts[0]
        }
        assets.forEach { (path, checksum) -> require(records[path] == checksum) { "Checksum ledger drifted: $path" } }
        GENERATED_FILES.filter { it != "checksums/sha256sums.txt" }.forEach { path ->
            require(records[path] == sha256(packageRoot.resolve(path))) { "Checksum ledger drifted: $path" }
        }
    }

    private fun sha256(path: Path): String = sha256Bytes(Files.readAllBytes(path))

    private fun sha256Text(value: String): String = sha256Bytes(value.toByteArray(Charsets.UTF_8))

    private fun firstDifference(expected: ByteArray, actual: ByteArray): String {
        val length = minOf(expected.size, actual.size)
        val index = (0 until length).firstOrNull { expected[it] != actual[it] } ?: length
        return "offset=$index"
    }

    private fun sha256Bytes(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(value)
        .joinToString("") { "%02x".format(it) }
}
