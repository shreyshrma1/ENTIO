package com.entio.semantic

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

class FiboDomainCorpusTest {
    private val packageRoot = Path.of("..", "external-ontologies", "fibo").toAbsolutePath().normalize()
    private val outputRoot = Path.of("..", DomainCorpusIdentity.OUTPUT_RELATIVE_PATH).toAbsolutePath().normalize()
    private val loader = Load(LoadSettings.builder().build())

    @Test
    fun generatedCorpusTraversesEveryEligibleIdentityExactlyOnce(): Unit {
        val records = descriptors()

        assertEquals(DomainCorpusIdentity.EXPECTED_ENTITY_COUNT, records.size)
        assertEquals(records.size, records.map { it.string("iri") }.distinct().size)
        assertEquals(DomainCorpusIdentity.EXPECTED_CLASS_COUNT, records.count { it.string("kind") == "Class" })
        assertEquals(
            DomainCorpusIdentity.EXPECTED_OBJECT_PROPERTY_COUNT,
            records.count { it.string("kind") == "ObjectProperty" },
        )
        assertEquals(
            DomainCorpusIdentity.EXPECTED_DATATYPE_PROPERTY_COUNT,
            records.count { it.string("kind") == "DatatypeProperty" },
        )
        assertEquals(DomainCorpusIdentity.EXPECTED_FIBO_COUNT, records.count { it.string("sourceFamily") == "FIBO" })
        assertEquals(
            DomainCorpusIdentity.EXPECTED_COMMONS_COUNT,
            records.count { it.string("sourceFamily") == "OMG_COMMONS" },
        )
    }

    @Test
    fun descriptorsPreserveSemanticMetadataAndMaturity(): Unit {
        val records = descriptors().associateBy { it.string("iri") }
        val agreement = assertNotNull(
            records["https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/Agreement"],
        )
        assertEquals("agreement", agreement.string("preferredLabel"))
        assertTrue(agreement.stringList("definitions").single().startsWith("negotiated understanding"))
        assertEquals(
            listOf("https://www.omg.org/spec/Commons/PartiesAndSituations/Situation"),
            agreement.stringList("parents"),
        )
        assertEquals("source/FND/Agreements/Agreements.rdf", agreement.string("sourcePath"))
        assertEquals("Release", agreement.string("maturity"))
        assertTrue(agreement.string("descriptorText").startsWith("agreement. negotiated understanding"))

        val objectProperty = assertNotNull(
            records["https://spec.edmcouncil.org/fibo/ontology/FND/ProductsAndServices/ProductsAndServices/hasBuyer"],
        )
        assertEquals("ObjectProperty", objectProperty.string("kind"))
        assertEquals(
            listOf("https://spec.edmcouncil.org/fibo/ontology/FND/ProductsAndServices/ProductsAndServices/Buyer"),
            objectProperty.stringList("ranges"),
        )
        val contractParty = assertNotNull(
            records["https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Contracts/hasContractParty"],
        )
        assertEquals(
            listOf("https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Contracts/Contract"),
            contractParty.stringList("domains"),
        )

        val datatypeProperty = assertNotNull(records["https://www.omg.org/spec/Commons/Organizations/hasURL"])
        assertEquals("DatatypeProperty", datatypeProperty.string("kind"))
        assertEquals("OMG_COMMONS", datatypeProperty.string("sourceFamily"))
        assertEquals(listOf("http://www.w3.org/2001/XMLSchema#anyURI"), datatypeProperty.stringList("ranges"))
        assertTrue(records.values.any { it.string("maturity") == "Provisional" })
        assertTrue(records.values.any { it.string("maturity") == "Informative" })
        assertTrue(records.values.any { it.string("maturity") == "Deprecated" })
    }

    @Test
    fun descriptorsUseSemanticIriFallbackWhenCatalogLabelsAreAbsent(): Unit {
        val records = descriptors()
        val contractType = records.single {
            it.string("iri") ==
                "https://spec.edmcouncil.org/fibo/ontology/ACTUS/ACTUSTaxonomy/ACTUSContractType"
        }

        assertTrue(records.all { it.string("preferredLabel").isNotBlank() })
        assertTrue(records.all { it.string("descriptorText").isNotBlank() })
        assertEquals("ACTUSContract Type", contractType.string("preferredLabel"))
        assertTrue(contractType.string("descriptorText").startsWith("ACTUSContract Type"))
    }

    @Test
    fun unsupportedConstructsAreReportedWithoutChangingNamedGraphContext(): Unit {
        val agreement = descriptors().single {
            it.string("iri") == "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/Agreement"
        }
        val reportLines = Files.readAllLines(outputRoot.resolve("unsupported-constructs-v1.jsonl"))

        assertTrue(agreement.stringList("unsupportedConstructs").contains(
            "http://www.w3.org/2000/01/rdf-schema#subClassOf|anonymous-expression",
        ))
        assertTrue(reportLines.any { "Agreements/Agreements/Agreement" in it && "anonymous-expression" in it })
        assertEquals(
            listOf("https://www.omg.org/spec/Commons/PartiesAndSituations/Situation"),
            agreement.stringList("parents"),
        )
    }

    @Test
    fun verifierRejectsCorruptionAndDeterministicRegenerationMatches(): Unit {
        DomainCorpusVerifier.verify(packageRoot, outputRoot)
        val copy = Files.createTempDirectory("entio-domain-corpus-corrupt")
        copyDirectory(outputRoot, copy)
        val descriptors = copy.resolve("descriptors-v1.jsonl")
        Files.writeString(descriptors, Files.readString(descriptors).replaceFirst("\"kind\":\"Class\"", "\"kind\":\"Other\""))

        val failure = assertFailsWith<IllegalArgumentException> { DomainCorpusVerifier.verify(packageRoot, copy) }

        assertTrue(failure.message.orEmpty().contains("checksum mismatch"))
    }

    @Test
    fun verifierRejectsDescriptorPackageStaleForPhase5Fingerprint(): Unit {
        val copy = Files.createTempDirectory("entio-domain-corpus-stale")
        copyDirectory(outputRoot, copy)
        val manifest = copy.resolve("manifest.yaml")
        Files.writeString(
            manifest,
            Files.readString(manifest).replace(
                "packageFingerprint: 015142b94819291379b89c3bba92048f037f1d8e635d3f1342d29f0f02f374ad",
                "packageFingerprint: 115142b94819291379b89c3bba92048f037f1d8e635d3f1342d29f0f02f374ad",
            ),
        )
        val ledger = copy.resolve("checksums/sha256sums.txt")
        Files.writeString(
            ledger,
            Files.readAllLines(ledger).joinToString("\n", postfix = "\n") { line ->
                if (line.endsWith("  manifest.yaml")) "${sha256(manifest)}  manifest.yaml" else line
            },
        )

        val failure = assertFailsWith<IllegalArgumentException> { DomainCorpusVerifier.verify(packageRoot, copy) }

        assertTrue(failure.message.orEmpty().contains("stale"))
    }

    @Test
    fun verifierRejectsStaleRecordFingerprintEvenWithUpdatedFileChecksums(): Unit {
        val copy = Files.createTempDirectory("entio-domain-corpus-stale-record")
        copyDirectory(outputRoot, copy)
        val descriptors = copy.resolve("descriptors-v1.jsonl")
        Files.writeString(
            descriptors,
            Files.readString(descriptors).replaceFirst(
                Regex("\"recordFingerprint\":\"[0-9a-f]{64}\""),
                "\"recordFingerprint\":\"${"0".repeat(64)}\"",
            ),
        )
        val manifest = copy.resolve("manifest.yaml")
        Files.writeString(
            manifest,
            Files.readString(manifest).replace(
                Regex("(?m)^descriptorsSha256: [0-9a-f]{64}$"),
                "descriptorsSha256: ${sha256(descriptors)}",
            ),
        )
        val ledger = copy.resolve("checksums/sha256sums.txt")
        Files.writeString(
            ledger,
            Files.readAllLines(ledger).joinToString("\n", postfix = "\n") { line ->
                when {
                    line.endsWith("  descriptors-v1.jsonl") -> "${sha256(descriptors)}  descriptors-v1.jsonl"
                    line.endsWith("  manifest.yaml") -> "${sha256(manifest)}  manifest.yaml"
                    else -> line
                }
            },
        )

        val failure = assertFailsWith<IllegalArgumentException> { DomainCorpusVerifier.verify(packageRoot, copy) }

        assertTrue(failure.message.orEmpty().contains("record fingerprint is stale"))
    }

    @Test
    fun phase5AndPhase12CompatibilityFingerprintsRemainUnchanged(): Unit {
        assertEquals(
            "05e9c612bd308fec918ff3e4edc3b5bda422b23fad79bffae10e8ebce03373a5",
            sha256(packageRoot.resolve("manifest.yaml")),
        )
        assertEquals(
            "65ec3b1bf37bc703163c2bf82f1da2e4108b704acda22462ccf31c05af32acfc",
            sha256(packageRoot.resolve("indexes/catalog-metadata-v1.json")),
        )
        assertEquals(
            "8194bc5cad5827aa98a2a6586c6a9a9da1cdf40c5f77681b0d56fa4e5868cb05",
            sha256(packageRoot.resolve("indexes/catalog-v1.jsonl")),
        )
        assertEquals(
            "5d538592282548be0b021248e3c0a398e268a3a4d6c1de2627af81ee0f29da50",
            sha256(packageRoot.resolve("indexes/curated-foundations-v1.json")),
        )
        assertEquals(
            "dc7089f4618e390db4b7d0b3d4c0ba17d5376ef0d00246e3a652ad62eaba0f90",
            sha256(Path.of("src/main/kotlin/com/entio/semantic/DocumentOntologyRetrievalService.kt")),
        )
    }

    private fun descriptors(): List<Map<*, *>> = Files.readAllLines(outputRoot.resolve("descriptors-v1.jsonl"))
        .filter(String::isNotBlank)
        .map { loader.loadFromString(it) as Map<*, *> }

    private fun copyDirectory(source: Path, target: Path): Unit {
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val destination = target.resolve(source.relativize(path).toString())
                if (Files.isDirectory(path)) Files.createDirectories(destination) else Files.copy(path, destination)
            }
        }
    }

    private fun Map<*, *>.string(key: String): String = this[key] as? String ?: error("Missing string: $key")
    private fun Map<*, *>.stringList(key: String): List<String> =
        (this[key] as? List<*>)?.map { it as String } ?: error("Missing list: $key")
    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}
