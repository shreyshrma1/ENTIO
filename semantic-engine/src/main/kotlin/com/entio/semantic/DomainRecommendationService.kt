package com.entio.semantic

import com.entio.core.DomainModelingIntent
import com.entio.core.DomainOntologyProfileIdentity
import com.entio.core.DomainRecommendation
import com.entio.core.DomainRecommendationAction
import com.entio.core.DomainRecommendationConfidence
import com.entio.core.DomainRecommendationReason
import com.entio.core.DomainRecommendationReasonType
import com.entio.core.DomainRecommendationResult
import com.entio.core.DomainRecommendationWarningType
import com.entio.core.DomainRetrievalAvailability
import com.entio.core.ExternalEntityKind
import com.entio.core.ExternalOntologyMaturity
import com.entio.core.Iri
import com.entio.semantic.DomainSearchAssetSupport.string
import com.entio.semantic.DomainSearchAssetSupport.stringList
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

public class DomainRecommendationStaleException(message: String) : IllegalArgumentException(message)

/** Kotlin-owned Phase 13 hybrid recommendation and deterministic ranking service. */
public class DomainRecommendationService private constructor(
    private val index: DomainSearchIndex?,
    private val embeddingService: LocalSentenceEmbeddingService?,
    private val availability: DomainRetrievalAvailability,
    private val records: Map<String, RecommendationRecord>,
    private val foundationIris: Set<String>,
    private val packageFingerprint: String,
    private val indexFingerprint: String,
    private val stateStore: DomainRecommendationStateStore,
) : AutoCloseable {
    public fun recommend(userId: String, intent: DomainModelingIntent): DomainRecommendationResult {
        require(userId.isNotBlank() && userId.length <= 256)
        verifyFreshness(intent)
        val intentFingerprint = fingerprintIntent(userId, intent)
        if (availability == DomainRetrievalAvailability.Unavailable || index == null) {
            return DomainRecommendationResult(availability, emptyList(), true, intentFingerprint)
        }
        val query = normalizeQuery(intent)
        val normalizedDraftLabel = normalize(intent.draftLabel)
        if (!intent.broadSearch && records.values.any {
                it.maturity == ExternalOntologyMaturity.Informative &&
                    it.kind == intent.requestedKind && normalize(it.preferredLabel) == normalizedDraftLabel
            }
        ) {
            return DomainRecommendationResult(availability, emptyList(), true, intentFingerprint)
        }
        val lexical = index.searchLexical(query, CANDIDATE_LIMIT)
        val vector = if (embeddingService != null && index.vectorAvailable) {
            index.searchVector(embeddingService.embed(query).values, CANDIDATE_LIMIT)
        } else {
            emptyList()
        }
        val lexicalScores = lexical.associate { hit ->
            hit.iri to (hit.score / (hit.score + LEXICAL_SCORE_SCALE)).coerceIn(0.0f, 1.0f)
        }
        val vectorScores = vector.mapIndexed { position, hit ->
            hit.iri to hit.score.coerceIn(0.0f, 1.0f) * (1.0f - position.toFloat() / CANDIDATE_LIMIT)
        }.toMap()
        val vectorRawScores = vector.associate { it.iri to it.score }
        val exactEligibleLabelExists = records.values.any { record ->
            normalize(record.preferredLabel) == normalizedDraftLabel &&
                (intent.requestedKind == null || record.kind == intent.requestedKind) &&
                record.maturity != ExternalOntologyMaturity.Informative
        }
        val candidates = (lexicalScores.keys + vectorScores.keys).distinct().mapNotNull { iri ->
            records[iri]?.let { record ->
                rank(
                    record,
                    intent,
                    query,
                    lexicalScores[iri],
                    vectorScores[iri],
                    vectorRawScores[iri],
                    exactEligibleLabelExists,
                )
            }
        }.filter(RankedRecommendation::eligible).sortedWith(rankingComparator)
        val limit = if (intent.broadSearch) BROAD_RESULT_LIMIT else DEFAULT_RESULT_LIMIT
        val selected = candidates.take(limit).map { ranked ->
            val recommendation = ranked.toRecommendation(userId, intentFingerprint, intent)
            stateStore.put(userId, intent.projectId, recommendation, intentFingerprint, intent)
            recommendation
        }
        return DomainRecommendationResult(
            availability = availability,
            recommendations = selected,
            noConfidentMatch = selected.none { DomainRecommendationAction.Reuse in it.permittedActions },
            normalizedIntentFingerprint = intentFingerprint,
        )
    }

    public fun resolve(
        userId: String,
        projectId: String,
        recommendationId: String,
        currentFingerprints: DomainRecommendationFingerprints,
    ): DomainRecommendation = stateStore.resolve(userId, projectId, recommendationId, currentFingerprints)

    override fun close(): Unit {
        embeddingService?.close()
        index?.close()
    }

    private fun verifyFreshness(intent: DomainModelingIntent): Unit {
        if (intent.packageFingerprint != packageFingerprint || intent.indexFingerprint != indexFingerprint) {
            throw DomainRecommendationStaleException("domain-recommendation-stale: refresh the domain index context")
        }
    }

    private fun normalizeQuery(intent: DomainModelingIntent): String = listOfNotNull(
        intent.draftLabel,
        intent.alternateWording,
        intent.definition,
    ).joinToString(". ").trim().replace(Regex("\\s+"), " ").also {
        require(it.isNotBlank() && it.toByteArray(Charsets.UTF_8).size <= 24_576)
    }

    private fun rank(
        record: RecommendationRecord,
        intent: DomainModelingIntent,
        query: String,
        lexical: Float?,
        vector: Float?,
        vectorRaw: Float?,
        exactEligibleLabelExists: Boolean,
    ): RankedRecommendation {
        val exactLabel = normalize(record.preferredLabel) == normalize(intent.draftLabel)
        val normalizedQuery = normalize(query)
        val normalizedPreferredLabel = normalize(record.preferredLabel)
        val labelContained = normalizedQuery.split(' ').containsAll(normalizedPreferredLabel.split(' '))
        val queryTokens = normalizedQuery.split(' ').filter { it.length > 2 }.toSet()
        val recordTokens = normalize(record.descriptorText).split(' ').filter { it.length > 2 }.toSet()
        val lexicalCoverage = if (queryTokens.isEmpty()) 0.0 else
            queryTokens.count { it in recordTokens }.toDouble() / queryTokens.size
        val alternateMatch = record.alternateLabels.any { normalize(it) == normalize(intent.draftLabel) }
        val preferredLabelAcronym = acronym(record.preferredLabel).takeIf(String::isNotBlank)
        val acronymMatch = if (queryTokens.size == 1) {
            preferredLabelAcronym == normalize(intent.draftLabel)
        } else {
            preferredLabelAcronym in queryTokens
        }
        // A single shared token must not erase the distinction between an exact
        // base label and longer specialized labels containing that token.
        val normalizedLexicalCoverage = if (
            queryTokens.size == 1 && !exactLabel && !alternateMatch && !acronymMatch
        ) {
            lexicalCoverage.coerceAtMost(0.5)
        } else {
            lexicalCoverage
        }
        val kindCompatible = intent.requestedKind == null || intent.requestedKind == record.kind
        val domainCompatible = compatible(intent.requiredDomainIri, record.domains)
        val rangeContext = intent.requiredDatatypeIri ?: intent.requiredRangeIri
        val rangeCompatible = compatible(rangeContext, record.ranges)
        val sourceEligible = when {
            record.sourceFamily == "FIBO" -> true
            intent.broadSearch -> true
            Iri(record.iri) in intent.alreadyReusedIris || Iri(record.iri) in intent.nearbyProjectIris -> true
            else -> false
        }
        val maturityEligible = record.maturity != ExternalOntologyMaturity.Informative || intent.broadSearch
        val eligible = kindCompatible && domainCompatible && rangeCompatible && sourceEligible && maturityEligible

        val structureSignals = listOfNotNull(
            intent.requestedKind?.let { if (kindCompatible) 1.0 else 0.0 },
            intent.requiredDomainIri?.let { if (domainCompatible) 1.0 else 0.0 },
            rangeContext?.let { if (rangeCompatible) 1.0 else 0.0 },
        )
        val structure = if (structureSignals.isEmpty()) 0.0 else structureSignals.average()
        val alreadyReused = Iri(record.iri) in intent.alreadyReusedIris
        val connected = (record.parents + record.domains + record.ranges).any { Iri(it) in intent.nearbyProjectIris }
        val project = when {
            alreadyReused -> 1.0
            connected -> 0.6
            else -> 0.0
        }
        val foundation = when {
            record.iri in foundationIris -> 1.0
            Iri(record.ontologyIri) in intent.usedSourceModuleIris -> 0.6
            else -> 0.0
        }
        val lexicalRelevance = maxOf(
            lexical?.toDouble() ?: 0.0,
            normalizedLexicalCoverage,
            if (exactLabel || alternateMatch || acronymMatch || (queryTokens.size > 1 && labelContained)) 1.0 else 0.0,
        )
        var score = 0.35 * lexicalRelevance +
            0.25 * (vector?.toDouble() ?: 0.0) +
            0.20 * structure +
            0.15 * project +
            0.05 * foundation
        if (record.maturity == ExternalOntologyMaturity.Deprecated && !alreadyReused) score -= 0.15
        score = score.coerceIn(0.0, 1.0)
        val confidence = when {
            score >= STRONG_THRESHOLD -> DomainRecommendationConfidence.Strong
            score >= POSSIBLE_THRESHOLD -> DomainRecommendationConfidence.Possible
            else -> DomainRecommendationConfidence.Low
        }.let { initial ->
            when {
                exactEligibleLabelExists && !exactLabel -> DomainRecommendationConfidence.Low
                exactLabel || alternateMatch || acronymMatch -> initial
                labelContained && normalizedPreferredLabel.split(' ').size > 1 -> initial
                normalizedLexicalCoverage < 0.75 && (vectorRaw ?: 0.0f) < 0.75f ->
                    DomainRecommendationConfidence.Low
                else -> initial
            }
        }
        val reasons = buildList {
            if (exactLabel || labelContained || acronymMatch) {
                add(DomainRecommendationReason(DomainRecommendationReasonType.PreferredLabelMatch))
            }
            if (alternateMatch) add(DomainRecommendationReason(DomainRecommendationReasonType.AlternateLabelMatch))
            if (!exactLabel && !alternateMatch && lexical != null) {
                add(DomainRecommendationReason(DomainRecommendationReasonType.DefinitionMatch))
            }
            if (vector != null) add(DomainRecommendationReason(DomainRecommendationReasonType.ParaphraseSimilarity))
            if (intent.requestedKind != null && kindCompatible) {
                add(DomainRecommendationReason(DomainRecommendationReasonType.CompatibleEntityKind))
            }
            if (intent.requiredDomainIri != null && domainCompatible) {
                add(DomainRecommendationReason(DomainRecommendationReasonType.CompatibleDomain, intent.requiredDomainIri))
            }
            if (rangeContext != null && rangeCompatible) {
                add(DomainRecommendationReason(DomainRecommendationReasonType.CompatibleRange, rangeContext))
            }
            if (alreadyReused) add(DomainRecommendationReason(DomainRecommendationReasonType.AlreadyReused, Iri(record.iri)))
            if (connected) add(DomainRecommendationReason(DomainRecommendationReasonType.ConnectedProjectEntity))
            if (record.iri in foundationIris) add(DomainRecommendationReason(DomainRecommendationReasonType.FoundationMember))
            if (Iri(record.ontologyIri) in intent.usedSourceModuleIris) {
                add(DomainRecommendationReason(DomainRecommendationReasonType.SourceModuleAlreadyUsed, Iri(record.ontologyIri)))
            }
        }
        val warnings = buildList {
            if (record.maturity == ExternalOntologyMaturity.Deprecated) {
                add(DomainRecommendationWarningType.DeprecatedEntity)
            }
            if (record.maturity == ExternalOntologyMaturity.Informative) {
                add(DomainRecommendationWarningType.InformativeEntity)
            }
            if (confidence == DomainRecommendationConfidence.Low) add(DomainRecommendationWarningType.LowConfidence)
            if (record.parents.size + record.domains.size + record.ranges.size > 3) {
                add(DomainRecommendationWarningType.AdditionalDependencyCost)
            }
            if (Iri(record.ontologyIri) !in intent.usedSourceModuleIris) {
                add(DomainRecommendationWarningType.SourceModuleNotUsed)
            }
        }
        return RankedRecommendation(record, eligible, score, confidence, exactLabel, alreadyReused, reasons, warnings)
    }

    private fun compatible(required: Iri?, declared: List<String>): Boolean =
        required == null || declared.isEmpty() || required.value in declared

    private fun RankedRecommendation.toRecommendation(
        userId: String,
        intentFingerprint: String,
        intent: DomainModelingIntent,
    ): DomainRecommendation {
        val actionable = eligible && confidence != DomainRecommendationConfidence.Low &&
            record.maturity != ExternalOntologyMaturity.Informative &&
            (record.maturity != ExternalOntologyMaturity.Deprecated || alreadyReused)
        val actions = if (actionable) {
            setOf(
                DomainRecommendationAction.Browse,
                DomainRecommendationAction.Reuse,
                DomainRecommendationAction.Extend,
                DomainRecommendationAction.MapAnnotation,
            )
        } else {
            setOf(DomainRecommendationAction.Browse)
        }
        val id = "dr_" + sha256(
            listOf(
                userId,
                intent.projectId,
                intentFingerprint,
                record.recordFingerprint,
                RANKING_CONTRACT,
                availability.name,
            ).joinToString("\u0000"),
        ).take(40)
        return DomainRecommendation(
            recommendationId = id,
            iri = Iri(record.iri),
            preferredLabel = record.preferredLabel,
            kind = record.kind,
            sourceFamily = record.sourceFamily,
            sourceModuleIri = Iri(record.ontologyIri),
            maturity = record.maturity,
            confidence = confidence,
            permittedActions = actions,
            reasons = reasons,
            warnings = warnings,
        )
    }

    private fun fingerprintIntent(userId: String, intent: DomainModelingIntent): String = sha256(
        listOf(
            userId,
            intent.projectId,
            intent.operationKind.name,
            intent.requestedKind?.name.orEmpty(),
            normalizeQuery(intent),
            intent.currentEntityIri?.value.orEmpty(),
            intent.requiredParentIri?.value.orEmpty(),
            intent.requiredDomainIri?.value.orEmpty(),
            intent.requiredRangeIri?.value.orEmpty(),
            intent.requiredDatatypeIri?.value.orEmpty(),
            intent.nearbyProjectIris.map(Iri::value).sorted().joinToString(","),
            intent.alreadyReusedIris.map(Iri::value).sorted().joinToString(","),
            intent.projectFingerprint,
            intent.profileFingerprint,
            intent.ontologyFingerprint,
            intent.currentWorkFingerprint,
            intent.packageFingerprint,
            intent.indexFingerprint,
            intent.broadSearch.toString(),
        ).joinToString("\u0000"),
    )

    public companion object {
        public const val RANKING_CONTRACT: String = "domain-ranking-v1"
        public const val DEFAULT_RESULT_LIMIT: Int = 10
        public const val BROAD_RESULT_LIMIT: Int = 50
        private const val CANDIDATE_LIMIT: Int = 100
        private const val LEXICAL_SCORE_SCALE: Float = 100.0f
        private const val STRONG_THRESHOLD: Double = 0.60
        private const val POSSIBLE_THRESHOLD: Double = 0.43

        public fun open(
            root: Path,
            modelRoot: Path,
            clock: Clock = Clock.systemUTC(),
        ): DomainRecommendationService {
            val searchManifest = DomainSearchAssetSupport.mapping(root.resolve(DomainSearchAssetSupport.SEARCH_MANIFEST))
            val descriptorManifest = DomainSearchAssetSupport.mapping(root.resolve("manifest.yaml"))
            val packageFingerprint = descriptorManifest.string("packageFingerprint")
            require(packageFingerprint == DomainOntologyProfileIdentity.PACKAGE_FINGERPRINT)
            val indexFingerprint = DomainSearchAssetSupport.sha256(root.resolve(DomainSearchAssetSupport.SEARCH_MANIFEST))
            val records = readRecords(root.resolve("descriptors-v1.jsonl"))
            val foundations = readFoundationIris(root.resolve("foundation-profile-v1.json"))
            return try {
                val index = DomainSearchIndex.openFull(root)
                val embedding = LocalSentenceEmbeddingService.open(modelRoot)
                DomainRecommendationService(
                    index,
                    embedding,
                    DomainRetrievalAvailability.Full,
                    records,
                    foundations,
                    packageFingerprint,
                    indexFingerprint,
                    DomainRecommendationStateStore(clock),
                )
            } catch (_: Exception) {
                try {
                    DomainRecommendationService(
                        DomainSearchIndex.openLexical(root),
                        null,
                        DomainRetrievalAvailability.LexicalStructural,
                        records,
                        foundations,
                        packageFingerprint,
                        indexFingerprint,
                        DomainRecommendationStateStore(clock),
                    )
                } catch (_: Exception) {
                    DomainRecommendationService(
                        null,
                        null,
                        DomainRetrievalAvailability.Unavailable,
                        records,
                        foundations,
                        packageFingerprint,
                        indexFingerprint,
                        DomainRecommendationStateStore(clock),
                    )
                }
            }
        }

        private fun readRecords(path: Path): Map<String, RecommendationRecord> {
            val loader = Load(LoadSettings.builder().setLabel("domain-recommendation-record").build())
            return Files.readAllLines(path).filter(String::isNotBlank).associate { line ->
                val map = loader.loadFromString(line) as Map<*, *>
                val record = RecommendationRecord(
                    iri = map.string("iri"),
                    kind = ExternalEntityKind.valueOf(map.string("kind")),
                    sourceFamily = map.string("sourceFamily"),
                    ontologyIri = map.string("ontologyIri"),
                    maturity = ExternalOntologyMaturity.valueOf(map.string("maturity")),
                    preferredLabel = map.string("preferredLabel"),
                    alternateLabels = map.stringList("alternateLabels"),
                    definitions = map.stringList("definitions"),
                    descriptorText = map.string("descriptorText"),
                    parents = map.stringList("parents"),
                    domains = map.stringList("domains"),
                    ranges = map.stringList("ranges"),
                    recordFingerprint = map.string("recordFingerprint"),
                )
                record.iri to record
            }
        }

        private fun readFoundationIris(path: Path): Set<String> {
            val loader = Load(LoadSettings.builder().setLabel("domain-foundation-members").build())
            val root = loader.loadFromString(Files.readString(path)) as Map<*, *>
            return (root["groups"] as List<*>).flatMap { group ->
                ((group as Map<*, *>)["members"] as List<*>).map { member -> (member as Map<*, *>).string("iri") }
            }.toSet()
        }

        private fun normalize(value: String): String =
            value.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
        private fun acronym(label: String): String = label.split(Regex("[^A-Za-z0-9]+"))
            .filter(String::isNotBlank)
            .takeIf { it.size > 1 }
            ?.joinToString("") { it.first().lowercaseChar().toString() }
            .orEmpty()
        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(Locale.ROOT, it) }
    }
}

private data class RecommendationRecord(
    val iri: String,
    val kind: ExternalEntityKind,
    val sourceFamily: String,
    val ontologyIri: String,
    val maturity: ExternalOntologyMaturity,
    val preferredLabel: String,
    val alternateLabels: List<String>,
    val definitions: List<String>,
    val descriptorText: String,
    val parents: List<String>,
    val domains: List<String>,
    val ranges: List<String>,
    val recordFingerprint: String,
)

private data class RankedRecommendation(
    val record: RecommendationRecord,
    val eligible: Boolean,
    val score: Double,
    val confidence: DomainRecommendationConfidence,
    val exactLabel: Boolean,
    val alreadyReused: Boolean,
    val reasons: List<DomainRecommendationReason>,
    val warnings: List<DomainRecommendationWarningType>,
)

private val rankingComparator = compareByDescending<RankedRecommendation> { it.eligible }
    .thenByDescending { it.score }
    .thenByDescending { it.exactLabel }
    .thenByDescending { it.alreadyReused }
    .thenBy { it.record.kind.name }
    .thenBy { it.record.preferredLabel.lowercase(Locale.ROOT) }
    .thenBy { it.record.iri }
    .thenBy { it.record.ontologyIri }

public data class DomainRecommendationFingerprints(
    public val project: String,
    public val profile: String,
    public val ontology: String,
    public val currentWork: String,
    public val packageValue: String,
    public val index: String,
)

/** Session-scoped, bounded recommendation identity state. */
public class DomainRecommendationStateStore(
    private val clock: Clock = Clock.systemUTC(),
    private val ttl: Duration = Duration.ofMinutes(30),
    private val recommendationCapacity: Int = 500,
    private val planCapacity: Int = 50,
) {
    private val sequence = AtomicLong()
    private val records = linkedMapOf<Scope, MutableList<StoredRecommendation>>()
    private val plans = linkedMapOf<Scope, MutableList<StoredPlan>>()

    @Synchronized
    public fun put(
        userId: String,
        projectId: String,
        recommendation: DomainRecommendation,
        intentFingerprint: String,
        intent: DomainModelingIntent,
    ): Unit {
        val scope = Scope(projectId, userId)
        cleanup(scope)
        val values = records.getOrPut(scope) { mutableListOf() }
        values.removeAll { it.recommendation.recommendationId == recommendation.recommendationId }
        values += StoredRecommendation(
            recommendation,
            intentFingerprint,
            DomainRecommendationFingerprints(
                intent.projectFingerprint,
                intent.profileFingerprint,
                intent.ontologyFingerprint,
                intent.currentWorkFingerprint,
                intent.packageFingerprint,
                intent.indexFingerprint,
            ),
            clock.instant(),
            sequence.incrementAndGet(),
        )
        evictOldest(values, recommendationCapacity)
    }

    @Synchronized
    public fun resolve(
        userId: String,
        projectId: String,
        recommendationId: String,
        current: DomainRecommendationFingerprints,
    ): DomainRecommendation {
        val scope = Scope(projectId, userId)
        cleanup(scope)
        val stored = records[scope]?.singleOrNull { it.recommendation.recommendationId == recommendationId }
            ?: throw DomainRecommendationStaleException("domain-recommendation-stale: refresh recommendations")
        if (stored.fingerprints != current) {
            throw DomainRecommendationStaleException("domain-recommendation-stale: project context changed")
        }
        return stored.recommendation
    }

    @Synchronized
    public fun putPlan(userId: String, projectId: String, planId: String, recommendationIds: Set<String>): Unit {
        require(planId.isNotBlank() && recommendationIds.size <= 100)
        val scope = Scope(projectId, userId)
        cleanup(scope)
        val values = plans.getOrPut(scope) { mutableListOf() }
        values.removeAll { it.id == planId }
        values += StoredPlan(planId, recommendationIds, clock.instant(), sequence.incrementAndGet())
        evictOldest(values, planCapacity)
    }

    @Synchronized
    public fun counts(userId: String, projectId: String): Pair<Int, Int> {
        val scope = Scope(projectId, userId)
        cleanup(scope)
        return (records[scope]?.size ?: 0) to (plans[scope]?.size ?: 0)
    }

    private fun cleanup(scope: Scope): Unit {
        val cutoff = clock.instant().minus(ttl)
        records[scope]?.removeAll { !it.createdAt.isAfter(cutoff) }
        plans[scope]?.removeAll { !it.createdAt.isAfter(cutoff) }
        val liveIds = records[scope].orEmpty().map { it.recommendation.recommendationId }.toSet()
        plans[scope]?.removeAll { plan -> plan.recommendationIds.any { it !in liveIds } }
        if (records[scope].isNullOrEmpty()) records.remove(scope)
        if (plans[scope].isNullOrEmpty()) plans.remove(scope)
    }

    private fun <T : Sequenced> evictOldest(values: MutableList<T>, capacity: Int): Unit {
        while (values.size > capacity) values.remove(values.minBy { it.createdSequence })
    }

    private data class Scope(val projectId: String, val userId: String)
    private sealed interface Sequenced { val createdSequence: Long }
    private data class StoredRecommendation(
        val recommendation: DomainRecommendation,
        val intentFingerprint: String,
        val fingerprints: DomainRecommendationFingerprints,
        val createdAt: Instant,
        override val createdSequence: Long,
    ) : Sequenced
    private data class StoredPlan(
        val id: String,
        val recommendationIds: Set<String>,
        val createdAt: Instant,
        override val createdSequence: Long,
    ) : Sequenced
}
