package com.entio.web.ai

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiRepairPacketBuilderTest {
    private val builder = AiRepairPacketBuilder(
        Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC),
        idFactory = { "packet" },
    )

    @Test
    fun inventoryStatusesAreCompleteAndOnlyApprovedCodesAutoRepair(): Unit {
        assertEquals(
            setOf(
                "invalid-language-tag", "duplicate-draft-edit", "ambiguous-preferred-label",
                "incompatible-property-domain", "incompatible-property-range", "shacl-validation",
            ),
            AiRepairPacketBuilder.inventoryStatuses().keys,
        )
        assertEquals(AiRepairCodeStatus.AUTO_REPAIRABLE, AiRepairPacketBuilder.inventoryStatuses()["invalid-language-tag"])
        assertTrue(AiRepairPacketBuilder.inventoryStatuses().filterValues { it != AiRepairCodeStatus.AUTO_REPAIRABLE }.isNotEmpty())
    }

    @Test
    fun packetsAreStableCompleteAndUnknownOrAmbiguousCandidatesAreNotAutomatic(): Unit {
        val findings = listOf(
            finding("z", "unknown-code"),
            finding("a", "invalid-language-tag", candidates = listOf("candidate-1")),
            finding("b", "invalid-language-tag", candidates = listOf("one", "two")),
            finding("c", "ambiguous-preferred-label"),
        )
        val packets = builder.build(findings)

        assertEquals(listOf("ambiguous-preferred-label", "invalid-language-tag", "invalid-language-tag", "unknown-code"), packets.map { it.finding.code })
        assertEquals(AiRepairCodeStatus.AUTO_REPAIRABLE, packets[1].status)
        assertEquals(AiRepairCodeStatus.CLARIFICATION_REQUIRED, packets[2].status)
        assertEquals(AiRepairCodeStatus.UNSUPPORTED, packets.last().status)
        assertTrue(packets.all { it.finding.source.isNotBlank() && it.finding.evidenceReferences.isNotEmpty() })
    }
}

internal fun finding(
    id: String,
    code: String,
    packageId: String = "package-a",
    candidates: List<String> = emptyList(),
) = AiRepairFinding(
    findingId = id,
    code = code,
    workPackageId = packageId,
    entityIris = listOf("https://example.com#Account"),
    draftItemIds = listOf("item-1"),
    expected = "en",
    actual = "en_US",
    source = "SemanticMetadataValidator",
    evidenceReferences = listOf("analysis-1"),
    deterministicCandidateReferences = candidates,
)
