package com.entio.semantic

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

class DomainFoundationProfileTest {
    private val outputRoot = Path.of("..", DomainCorpusIdentity.OUTPUT_RELATIVE_PATH).toAbsolutePath().normalize()
    private val loader = Load(LoadSettings.builder().build())

    @Test
    fun foundationHasReviewedEightGroupOrderAndValidMembers(): Unit {
        val profile = mapping(Files.readString(outputRoot.resolve("foundation-profile-v1.json")))
        val groups = profile.list("groups").map { it as Map<*, *> }
        val descriptorIris = Files.readAllLines(outputRoot.resolve("descriptors-v1.jsonl"))
            .filter(String::isNotBlank)
            .map { mapping(it).string("iri") }
            .toSet()

        assertEquals(DomainCorpusIdentity.FOUNDATION_SCHEMA, profile.string("schema"))
        assertEquals(true, profile["reviewed"])
        assertEquals(
            listOf(
                "agents-organizations",
                "agreements-commitments",
                "identifiers-classifications",
                "dates-temporal",
                "quantities-units-measures",
                "ownership-control",
                "products-services",
                "places-addresses",
            ),
            groups.map { it.string("id") },
        )
        assertEquals(groups.flatMap { it.list("members") }.size, groups.flatMap { it.list("members") }
            .map { (it as Map<*, *>).string("iri") }.distinct().size)
        groups.forEach { group ->
            assertTrue(group.list("members").isNotEmpty())
            assertTrue(group.list("members").map { (it as Map<*, *>).string("kind") }
                .any { it == "ObjectProperty" || it == "DatatypeProperty" })
            group.list("members").forEach { member ->
                val memberMap = member as Map<*, *>
                assertTrue(memberMap.string("iri") in descriptorIris)
                assertTrue(memberMap.string("kind") in setOf("Class", "ObjectProperty", "DatatypeProperty"))
                assertTrue(memberMap.string("sourceFamily") in setOf("FIBO", "OMG_COMMONS"))
            }
        }
    }

    @Test
    fun generationIsByteForByteDeterministicIncludingFoundationOrder(): Unit {
        val packageRoot = Path.of("..", "external-ontologies", "fibo").toAbsolutePath().normalize()
        val first = Files.createTempDirectory("entio-domain-foundation-first")
        val second = Files.createTempDirectory("entio-domain-foundation-second")

        DomainCorpusGenerator.generate(packageRoot, first)
        DomainCorpusGenerator.generate(packageRoot, second)

        listOf(
            "ATTRIBUTION.md",
            "checksums/sha256sums.txt",
            "descriptors-v1.jsonl",
            "foundation-profile-v1.json",
            "manifest.yaml",
            "unsupported-constructs-v1.jsonl",
        ).forEach { relative ->
            assertTrue(Files.readAllBytes(first.resolve(relative)).contentEquals(Files.readAllBytes(second.resolve(relative))))
        }
    }

    private fun mapping(value: String): Map<*, *> = loader.loadFromString(value) as Map<*, *>
    private fun Map<*, *>.string(key: String): String = this[key] as? String ?: error("Missing string: $key")
    private fun Map<*, *>.list(key: String): List<*> = this[key] as? List<*> ?: error("Missing list: $key")
}
