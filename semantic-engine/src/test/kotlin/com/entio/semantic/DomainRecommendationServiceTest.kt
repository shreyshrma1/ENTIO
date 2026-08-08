package com.entio.semantic

import com.entio.core.DomainModelingIntent
import com.entio.core.DomainFoundationPlanItemRole
import com.entio.core.DomainOntologyProfileIdentity
import com.entio.core.DomainOperationKind
import com.entio.core.DomainRecommendationAction
import com.entio.core.DomainRecommendationConfidence
import com.entio.core.DomainRecommendationReasonType
import com.entio.core.DomainRecommendationWarningType
import com.entio.core.DomainRetrievalAvailability
import com.entio.core.ExternalEntityKind
import com.entio.core.Iri
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DomainRecommendationServiceTest {
    private val root = Path.of("..", DomainCorpusIdentity.OUTPUT_RELATIVE_PATH).toAbsolutePath().normalize()
    private val modelRoot = Path.of(
        "..", "external-ontologies", "domain-search", "models", "all-MiniLM-L6-v2",
    ).toAbsolutePath().normalize()
    private val indexFingerprint = DomainSearchAssetSupport.sha256(root.resolve(DomainSearchAssetSupport.SEARCH_MANIFEST))

    @Test
    fun exactClassRecommendationHasVerifiedReasonsAndStableIdentity(): Unit {
        DomainRecommendationService.open(root, modelRoot).use { service ->
            val intent = intent("agreement", ExternalEntityKind.Class)
            val first = service.recommend("user-1", intent)
            val repeated = service.recommend("user-1", intent)
            val agreement = first.recommendations.first()

            assertEquals(DomainRetrievalAvailability.Full, first.availability)
            assertTrue(agreement.iri.value.endsWith("/Agreement"))
            assertTrue(DomainRecommendationReasonType.PreferredLabelMatch in agreement.reasons.map { it.type })
            assertTrue(DomainRecommendationReasonType.CompatibleEntityKind in agreement.reasons.map { it.type })
            assertTrue(DomainRecommendationAction.Reuse in agreement.permittedActions)
            assertEquals(first.recommendations.map { it.iri }, repeated.recommendations.map { it.iri })
            assertEquals(first.recommendations.map { it.recommendationId }, repeated.recommendations.map { it.recommendationId })
        }
    }

    @Test
    fun exactSingleTokenLabelRanksAheadOfSpecializedLabelsContainingThatToken(): Unit {
        DomainRecommendationService.open(root, modelRoot).use { service ->
            val result = service.recommend("user-1", intent("bond", ExternalEntityKind.Class))

            assertTrue(result.recommendations.first().iri.value.endsWith("/Bond"))
        }
    }

    @Test
    fun multiTokenQueryRetainsAcronymTokenSignal(): Unit {
        DomainRecommendationService.open(root, modelRoot).use { service ->
            val result = service.recommend("user-1", intent("LEI identifier", ExternalEntityKind.Class))

            assertTrue(result.recommendations.take(10).any { it.iri.value.endsWith("/LegalEntityIdentifier") })
        }
    }

    @Test
    fun hardKindAndRangeIncompatibilityCannotBeOvercomeByText(): Unit {
        DomainRecommendationService.open(root, modelRoot).use { service ->
            val wrongKind = service.recommend("user-1", intent("has borrower", ExternalEntityKind.DatatypeProperty))
            val wrongRange = service.recommend(
                "user-1",
                intent("has borrower", ExternalEntityKind.ObjectProperty).copy(
                    requiredRangeIri = Iri("https://spec.edmcouncil.org/fibo/ontology/FBC/DebtAndEquities/Debt/Lender"),
                ),
            )

            assertTrue(wrongKind.recommendations.none { it.iri.value.endsWith("/hasBorrower") })
            assertTrue(wrongRange.recommendations.none { it.iri.value.endsWith("/hasBorrower") })
        }
    }

    @Test
    fun projectContextReranksAndExplainsAlreadyReusedEntity(): Unit {
        val partnership = Iri(
            "https://spec.edmcouncil.org/fibo/ontology/BE/Partnerships/Partnerships/PartnershipAgreement",
        )
        DomainRecommendationService.open(root, modelRoot).use { service ->
            val result = service.recommend(
                "user-1",
                intent("agreement", ExternalEntityKind.Class).copy(alreadyReusedIris = setOf(partnership)),
            )
            val reused = assertNotNull(result.recommendations.find { it.iri == partnership })

            assertTrue(DomainRecommendationReasonType.AlreadyReused in reused.reasons.map { it.type })
        }
    }

    @Test
    fun informativeAndDeprecatedEntitiesHaveBoundedActionsAndWarnings(): Unit {
        DomainRecommendationService.open(root, modelRoot).use { service ->
            val ordinary = service.recommend("user-1", intent("market transaction", ExternalEntityKind.Class))
            val broad = service.recommend(
                "user-1",
                intent("market transaction", ExternalEntityKind.Class).copy(broadSearch = true),
            )
            val informative = assertNotNull(broad.recommendations.find { it.iri.value.endsWith("/MarketTransaction") })
            val board = service.recommend("user-1", intent("board agreement", ExternalEntityKind.Class))

            assertTrue(ordinary.recommendations.none { it.iri.value.endsWith("/MarketTransaction") })
            assertEquals(setOf(DomainRecommendationAction.Browse), informative.permittedActions)
            assertTrue(DomainRecommendationWarningType.InformativeEntity in informative.warnings)
            assertTrue(board.recommendations.none {
                it.iri.value.contains("/BE/Corporations/") && DomainRecommendationAction.Reuse in it.permittedActions
            })
        }
    }

    @Test
    fun corruptVectorsDegradeToLexicalStructuralWithoutChangingExactResult(): Unit {
        val copy = Files.createTempDirectory("entio-domain-recommendation-degraded")
        copyAssets(copy)
        val vector = copy.resolve(DomainSearchAssetSupport.VECTOR_FILE)
        val bytes = Files.readAllBytes(vector)
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        Files.write(vector, bytes)
        val degradedFingerprint = DomainSearchAssetSupport.sha256(copy.resolve(DomainSearchAssetSupport.SEARCH_MANIFEST))
        DomainRecommendationService.open(copy, modelRoot).use { service ->
            val result = service.recommend(
                "user-1",
                intent("agreement", ExternalEntityKind.Class).copy(indexFingerprint = degradedFingerprint),
            )

            assertEquals(DomainRetrievalAvailability.LexicalStructural, result.availability)
            assertTrue(result.recommendations.first().iri.value.endsWith("/Agreement"))
        }
    }

    @Test
    fun staleFingerprintsAndExpiredOrRestartedIdsFailClosed(): Unit {
        val clock = MutableClock(Instant.parse("2026-08-08T12:00:00Z"))
        DomainRecommendationService.open(root, modelRoot, clock).use { service ->
            assertFailsWith<DomainRecommendationStaleException> {
                service.recommend("user-1", intent("agreement", ExternalEntityKind.Class).copy(indexFingerprint = "stale"))
            }
            val intent = intent("agreement", ExternalEntityKind.Class)
            val recommendation = service.recommend("user-1", intent).recommendations.first()
            val fingerprints = fingerprints(intent)
            assertEquals(recommendation, service.resolve("user-1", "project-1", recommendation.recommendationId, fingerprints))
            clock.advance(Duration.ofMinutes(30))
            assertFailsWith<DomainRecommendationStaleException> {
                service.resolve("user-1", "project-1", recommendation.recommendationId, fingerprints)
            }
        }
        DomainRecommendationService.open(root, modelRoot, clock).use { restarted ->
            val intent = intent("agreement", ExternalEntityKind.Class)
            assertFailsWith<DomainRecommendationStaleException> {
                restarted.resolve("user-1", "project-1", "dr_from_previous_process", fingerprints(intent))
            }
        }
    }

    @Test
    fun foundationPlansUseOpaqueSelectionsDeterministicBoundsAndOwnerScopedResolution(): Unit {
        DomainRecommendationService.open(root, modelRoot).use { service ->
            val intent = intent("agreement", ExternalEntityKind.Class)
            val fingerprints = fingerprints(intent)
            val foundations = service.foundations()
            val selected = foundations.flatMap { it.members }.take(2)
            val first = service.planFoundation(
                userId = "user-1",
                projectId = intent.projectId,
                selectedElementIds = selected.map { it.elementId }.toSet(),
                selectAll = false,
                alreadyPresentIris = setOf(selected.first().iri),
                fingerprints = fingerprints,
            )
            val repeated = service.planFoundation(
                userId = "user-1",
                projectId = intent.projectId,
                selectedElementIds = selected.map { it.elementId }.reversed().toSet(),
                selectAll = false,
                alreadyPresentIris = setOf(selected.first().iri),
                fingerprints = fingerprints,
            )

            assertEquals(8, foundations.size)
            assertTrue(foundations.flatMap { it.members }.all { it.elementId.matches(Regex("dfe_[0-9a-f]{40}")) })
            assertEquals(first.planId, repeated.planId)
            assertTrue(first.batches.all { it.explicitSelectionCount <= 20 && it.items.size <= 100 })
            assertTrue(first.batches.flatMap { it.items }.any {
                it.iri == selected.first().iri && it.role == DomainFoundationPlanItemRole.AlreadyPresent
            })
            assertEquals(first, service.resolveFoundationPlan("user-1", intent.projectId, first.planId))
            assertFailsWith<DomainRecommendationStaleException> {
                service.resolveFoundationPlan("user-2", intent.projectId, first.planId)
            }
        }
    }

    @Test
    fun stateCapacityUsesExpiredFirstThenOldestSequenceAndKeepsPlansBounded(): Unit {
        val clock = MutableClock(Instant.parse("2026-08-08T12:00:00Z"))
        val store = DomainRecommendationStateStore(clock, Duration.ofMinutes(30), 2, 1)
        val intent = intent("agreement", ExternalEntityKind.Class)
        listOf("one", "two", "three").forEach { id ->
            store.put("user", "project", recommendation(id), "intent", intent)
        }
        store.putPlan("user", "project", "plan-one", setOf("dr_two"))
        store.putPlan("user", "project", "plan-two", setOf("dr_three"))

        assertEquals(2 to 1, store.counts("user", "project"))
        assertFailsWith<DomainRecommendationStaleException> {
            store.resolve("user", "project", "dr_one", fingerprints(intent))
        }
        clock.advance(Duration.ofMinutes(30))
        assertEquals(0 to 0, store.counts("user", "project"))
    }

    private fun intent(text: String, kind: ExternalEntityKind): DomainModelingIntent = DomainModelingIntent(
        projectId = "project-1",
        operationKind = DomainOperationKind.GlobalSemanticSearch,
        requestedKind = kind,
        draftLabel = text,
        projectFingerprint = "project-v1",
        profileFingerprint = "profile-v1",
        ontologyFingerprint = "ontology-v1",
        currentWorkFingerprint = "work-v1",
        packageFingerprint = DomainOntologyProfileIdentity.PACKAGE_FINGERPRINT,
        indexFingerprint = indexFingerprint,
    )

    private fun fingerprints(intent: DomainModelingIntent): DomainRecommendationFingerprints =
        DomainRecommendationFingerprints(
            intent.projectFingerprint,
            intent.profileFingerprint,
            intent.ontologyFingerprint,
            intent.currentWorkFingerprint,
            intent.packageFingerprint,
            intent.indexFingerprint,
        )

    private fun recommendation(id: String): com.entio.core.DomainRecommendation = com.entio.core.DomainRecommendation(
        recommendationId = "dr_$id",
        iri = Iri("https://example.com/$id"),
        preferredLabel = id,
        kind = ExternalEntityKind.Class,
        sourceFamily = "FIBO",
        sourceModuleIri = Iri("https://example.com/module"),
        maturity = com.entio.core.ExternalOntologyMaturity.Release,
        confidence = DomainRecommendationConfidence.Strong,
        permittedActions = setOf(DomainRecommendationAction.Reuse),
        reasons = emptyList(),
        warnings = emptyList(),
    )

    private fun copyAssets(target: Path): Unit {
        listOf(
            "descriptors-v1.jsonl",
            "manifest.yaml",
            "foundation-profile-v1.json",
            DomainSearchAssetSupport.LEXICAL_FILE,
            DomainSearchAssetSupport.IRI_FILE,
            DomainSearchAssetSupport.VECTOR_FILE,
            DomainSearchAssetSupport.SEARCH_MANIFEST,
        ).forEach { relative ->
            val destination = target.resolve(relative)
            Files.createDirectories(destination.parent)
            Files.copy(root.resolve(relative), destination)
        }
    }

    private class MutableClock(private var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = current
        fun advance(duration: Duration): Unit { current = current.plus(duration) }
    }
}
