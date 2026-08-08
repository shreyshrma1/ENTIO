package com.entio.semantic

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

/** Offline entry point used by the legacy-named catalog generation task. */
public object Phase13FiboAssetGenerator {
    @JvmStatic
    public fun main(args: Array<String>): Unit {
        require(args.size == 2) { "Expected the Phase 5 package root and Phase 13 output root." }
        val packageRoot = Path.of(args[0])
        FiboPackageVerifier.verify(packageRoot)
        // Preserve the established Phase 5 regeneration contract. Verification above
        // proves that this rewrite is byte-for-byte stable before any file is touched.
        FiboCatalogGenerator.generate(packageRoot, packageRoot)
        DomainCorpusGenerator.generate(packageRoot, Path.of(args[1]))
    }
}

/** Verifies the immutable Phase 5 package and deterministic Phase 13 descriptor assets. */
public object Phase13FiboAssetVerifier {
    @JvmStatic
    public fun main(args: Array<String>): Unit {
        require(args.size == 2) { "Expected the Phase 5 package root and Phase 13 output root." }
        val packageRoot = Path.of(args[0])
        FiboPackageVerifier.verify(packageRoot)
        DomainCorpusVerifier.verify(packageRoot, Path.of(args[1]))
    }
}

public object DomainCorpusVerifier {
    private val requiredFiles: List<String> = listOf(
        "ATTRIBUTION.md",
        "checksums/sha256sums.txt",
        "descriptors-v1.jsonl",
        "foundation-profile-v1.json",
        "manifest.yaml",
        "unsupported-constructs-v1.jsonl",
    )
    private val yamlLoader: Load = Load(LoadSettings.builder().setLabel("domain-corpus-verifier").build())

    public fun verify(packageRoot: Path, outputRoot: Path): Unit {
        requiredFiles.forEach { relative ->
            require(Files.isRegularFile(outputRoot.resolve(relative))) { "Missing Phase 13 domain asset: $relative" }
        }
        verifyChecksums(outputRoot)
        verifyManifest(packageRoot, outputRoot)
        verifyDescriptors(outputRoot)
        verifyFoundation(outputRoot)

        val regenerated = Files.createTempDirectory("entio-domain-corpus-")
        try {
            DomainCorpusGenerator.generate(packageRoot, regenerated)
            requiredFiles.forEach { relative ->
                val expected = Files.readAllBytes(outputRoot.resolve(relative))
                val actual = Files.readAllBytes(regenerated.resolve(relative))
                require(expected.contentEquals(actual)) {
                    "Generated Phase 13 domain asset drifted: $relative " +
                        "(committed=${sha256Bytes(expected)}, regenerated=${sha256Bytes(actual)})"
                }
            }
        } finally {
            regenerated.toFile().deleteRecursively()
        }
    }

    private fun verifyChecksums(outputRoot: Path): Unit {
        val lines = Files.readAllLines(outputRoot.resolve("checksums/sha256sums.txt")).filter(String::isNotBlank)
        require(lines.size == requiredFiles.size - 1) { "Phase 13 checksum ledger has an unexpected entry count." }
        val entries = lines.associate { line ->
            val parts = line.split("  ", limit = 2)
            require(parts.size == 2 && SHA256.matches(parts[0])) { "Malformed Phase 13 checksum entry." }
            parts[1] to parts[0]
        }
        require(entries.size == lines.size) { "Duplicate Phase 13 checksum path." }
        requiredFiles.filterNot { it == "checksums/sha256sums.txt" }.forEach { relative ->
            require(entries[relative] == sha256(outputRoot.resolve(relative))) { "Phase 13 checksum mismatch: $relative" }
        }
    }

    private fun verifyManifest(packageRoot: Path, outputRoot: Path): Unit {
        val map = mapping(Files.readString(outputRoot.resolve("manifest.yaml")))
        require(map.string("schema") == DomainCorpusIdentity.SCHEMA)
        require(map.string("release") == "master_2026Q2")
        require(map.string("recordSchema") == DomainCorpusIdentity.RECORD_SCHEMA)
        require(map.string("descriptorContract") == DomainCorpusIdentity.DESCRIPTOR_CONTRACT)
        require(map.string("graphContextContract") == DomainCorpusIdentity.GRAPH_CONTEXT_CONTRACT)
        require(map.string("foundationSchema") == DomainCorpusIdentity.FOUNDATION_SCHEMA)
        require(map.string("foundationProfile") == DomainCorpusIdentity.FOUNDATION_PROFILE)
        require(map.int("entityCount") == DomainCorpusIdentity.EXPECTED_ENTITY_COUNT)
        require(map.int("fiboCount") == DomainCorpusIdentity.EXPECTED_FIBO_COUNT)
        require(map.int("omgCommonsCount") == DomainCorpusIdentity.EXPECTED_COMMONS_COUNT)
        require(map.int("classCount") == DomainCorpusIdentity.EXPECTED_CLASS_COUNT)
        require(map.int("objectPropertyCount") == DomainCorpusIdentity.EXPECTED_OBJECT_PROPERTY_COUNT)
        require(map.int("datatypePropertyCount") == DomainCorpusIdentity.EXPECTED_DATATYPE_PROPERTY_COUNT)
        val phase5 = mapping(Files.readString(packageRoot.resolve("manifest.yaml")))
        require(map.string("packageFingerprint") == phase5.string("packageFingerprint")) {
            "Phase 13 descriptor package is stale for the Phase 5 package."
        }
        require(map.string("descriptorsSha256") == sha256(outputRoot.resolve("descriptors-v1.jsonl")))
        require(map.string("foundationSha256") == sha256(outputRoot.resolve("foundation-profile-v1.json")))
        require(map.string("unsupportedSha256") == sha256(outputRoot.resolve("unsupported-constructs-v1.jsonl")))
    }

    private fun verifyDescriptors(outputRoot: Path): Unit {
        val lines = Files.readAllLines(outputRoot.resolve("descriptors-v1.jsonl"))
            .filter(String::isNotBlank)
        val records = lines.map(::mapping)
        require(records.size == DomainCorpusIdentity.EXPECTED_ENTITY_COUNT)
        val iris = records.map { it.string("iri") }
        require(iris == iris.sorted()) { "Phase 13 descriptors are not in canonical IRI order." }
        require(iris.distinct().size == iris.size) { "Duplicate Phase 13 descriptor IRI." }
        records.zip(lines).forEach { (record, line) ->
            require(record.string("schema") == DomainCorpusIdentity.RECORD_SCHEMA)
            require(record.string("kind") in setOf("Class", "ObjectProperty", "DatatypeProperty"))
            require(record.string("maturity") in setOf("Release", "Provisional", "Informative", "Deprecated", "Unknown"))
            val path = record.string("sourcePath")
            val family = record.string("sourceFamily")
            require(
                family == DomainSourceFamily.FIBO.name && path.startsWith("source/") ||
                    family == DomainSourceFamily.OMG_COMMONS.name && path.startsWith("dependencies/omg-commons-1.3/"),
            ) { "Descriptor source family does not match its verified source path." }
            val recordFingerprint = record.string("recordFingerprint")
            require(SHA256.matches(recordFingerprint))
            val fingerprintField = ",\"recordFingerprint\":\"$recordFingerprint\"}"
            require(line.endsWith(fingerprintField)) { "Descriptor record fingerprint field order drifted." }
            val canonical = line.removeSuffix(fingerprintField) + "}"
            require(recordFingerprint == sha256Bytes(canonical.toByteArray(Charsets.UTF_8))) {
                "Descriptor record fingerprint is stale: ${record.string("iri")}"
            }
            require(SHA256.matches(record.string("dependencyFingerprint")))
        }
    }

    private fun verifyFoundation(outputRoot: Path): Unit {
        val profile = mapping(Files.readString(outputRoot.resolve("foundation-profile-v1.json")))
        require(profile.string("schema") == DomainCorpusIdentity.FOUNDATION_SCHEMA)
        require(profile.string("profileId") == DomainCorpusIdentity.FOUNDATION_PROFILE)
        require(profile["reviewed"] == true) { "Foundation profile has not been reviewed." }
        val groups = profile["groups"] as? List<*> ?: error("Foundation groups are missing.")
        require(groups.size == 8) { "Foundation profile must contain exactly eight ordered groups." }
        val ids = groups.map { (it as Map<*, *>).string("id") }
        require(ids == listOf(
            "agents-organizations",
            "agreements-commitments",
            "identifiers-classifications",
            "dates-temporal",
            "quantities-units-measures",
            "ownership-control",
            "products-services",
            "places-addresses",
        )) { "Foundation group order drifted." }
        val descriptorIris = Files.readAllLines(outputRoot.resolve("descriptors-v1.jsonl"))
            .filter(String::isNotBlank).map { mapping(it).string("iri") }.toSet()
        groups.forEach { groupValue ->
            val group = groupValue as Map<*, *>
            val members = group["members"] as? List<*> ?: error("Foundation group members are missing.")
            require(members.isNotEmpty())
            members.forEach { member -> require((member as Map<*, *>).string("iri") in descriptorIris) }
        }
    }

    private fun mapping(value: String): Map<*, *> =
        yamlLoader.loadFromString(value) as? Map<*, *> ?: error("Expected a mapping.")
    private fun Map<*, *>.string(key: String): String = this[key] as? String ?: error("Missing string: $key")
    private fun Map<*, *>.int(key: String): Int = (this[key] as? Number)?.toInt() ?: error("Missing number: $key")
    private fun sha256(path: Path): String = sha256Bytes(Files.readAllBytes(path))
    private fun sha256Bytes(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private val SHA256: Regex = Regex("[0-9a-f]{64}")
}
