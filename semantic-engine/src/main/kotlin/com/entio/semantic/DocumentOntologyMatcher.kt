package com.entio.semantic

import com.entio.core.DocumentAmbiguity
import com.entio.core.DocumentAuthorityMetadata
import com.entio.core.DocumentAuthorityStatus
import com.entio.core.DocumentCandidate
import com.entio.core.DocumentCandidateCategory
import com.entio.core.DocumentConflict
import com.entio.core.DocumentConflictAlternative
import com.entio.core.DocumentEvidence
import com.entio.core.DocumentMatchCandidate
import com.entio.core.DocumentMatchScope
import com.entio.core.DocumentRecommendation
import com.entio.core.DocumentRecommendationAction
import com.entio.core.Iri
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

public data class DocumentSemanticRecord(
    val scope: DocumentMatchScope,
    val entityIri: Iri,
    val sourceId: String,
    val preferredLabel: String?,
    val aliases: List<String> = emptyList(),
    val category: DocumentCandidateCategory?,
    val normalizedIdentityKey: String?,
    val normalizedTypedOperationKey: String?,
    val authority: DocumentAuthorityMetadata? = null,
) {
    init {
        require(sourceId.isNotBlank())
        require(aliases == aliases.distinct().sorted())
    }
}

public data class DocumentMatchingInput(
    val exactWorkKey: String,
    val candidates: List<DocumentCandidate>,
    val records: List<DocumentSemanticRecord>,
    val candidateTypedOperationKeys: Map<String, String> = emptyMap(),
    val authorityByDocumentId: Map<String, DocumentAuthorityMetadata> = emptyMap(),
    val curatedFiboSourceIds: Set<String> = emptySet(),
    val targetSourceId: String?,
    val modelId: String?,
    val promptVersion: String?,
) {
    init {
        require(exactWorkKey.isNotBlank())
        require(candidates.size <= 2_000)
        require(records.size <= 20_000)
        require((modelId == null) == (promptVersion == null))
    }
}

public data class DocumentMatchingResult(
    val exactWorkKey: String,
    val recommendations: List<DocumentRecommendation>,
)

/** Deterministic, bounded matching over records supplied by existing Entio search boundaries. */
public class DocumentOntologyMatcher {
    private val completed: MutableMap<String, DocumentMatchingResult> = linkedMapOf()

    @Synchronized
    public fun match(input: DocumentMatchingInput): DocumentMatchingResult {
        completed[input.exactWorkKey]?.let { return it }
        val recommendations = input.candidates
            .distinctBy { it.identity.value }
            .map { candidate -> recommend(candidate, input) }
            .sortedBy(DocumentRecommendation::stableOrderingKey)
        return DocumentMatchingResult(input.exactWorkKey, recommendations).also {
            completed[input.exactWorkKey] = it
        }
    }

    private fun recommend(candidate: DocumentCandidate, input: DocumentMatchingInput): DocumentRecommendation {
        val candidateOperation = input.candidateTypedOperationKeys[candidate.identity.value]
        val sameTaskRecords = input.candidates.asSequence()
            .filter { it.identity.value != candidate.identity.value }
            .map { other ->
                DocumentSemanticRecord(
                    scope = DocumentMatchScope.SameTask,
                    entityIri = Iri("urn:entio:document-candidate:${other.identity.value}"),
                    sourceId = "document-ingestion-task",
                    preferredLabel = other.proposedLabel,
                    category = other.category,
                    normalizedIdentityKey = other.identity.normalizedValue,
                    normalizedTypedOperationKey = input.candidateTypedOperationKeys[other.identity.value],
                    authority = input.authorityByDocumentId[other.documentId.value],
                )
            }
        val authority = input.authorityByDocumentId[candidate.documentId.value]
        val matches = (input.records.asSequence() + sameTaskRecords)
            .filter { it.scope != DocumentMatchScope.CuratedFibo || it.sourceId in input.curatedFiboSourceIds }
            .filter { it.category == null || it.category == candidate.category }
            .mapNotNull { record -> score(candidate, candidateOperation, authority, record) }
            .distinctBy { it.scope to it.entityIri }
            .sortedWith(
                compareByDescending<DocumentMatchCandidate>(DocumentMatchCandidate::score)
                    .thenBy { it.scope.name }
                    .thenBy { it.entityIri.value }
                    .thenBy(DocumentMatchCandidate::sourceId),
            )
            .take(MAX_MATCHES)
            .toList()
        val exactOperationMatches = if (candidateOperation == null) {
            emptyList()
        } else {
            matches.filter { it.normalizedTypedOperationKey == candidateOperation }
        }
        val bestScore = matches.firstOrNull()?.score
        val equallyBest = matches.filter { it.score == bestScore }
        val ambiguity = if (equallyBest.map { it.entityIri }.distinct().size >= 2) {
            listOf(
                DocumentAmbiguity(
                    id = "ambiguity-${stableId(candidate.identity.value, *equallyBest.map { it.entityIri.value }.sorted().toTypedArray())}",
                    message = "Multiple ontology records are equally plausible; choose the intended match.",
                    candidateIris = equallyBest.map(DocumentMatchCandidate::entityIri).distinct().sortedBy(Iri::value),
                ),
            )
        } else {
            emptyList()
        }
        val explicitDirective = directive(candidate, authority)
        val selected = when {
            ambiguity.isNotEmpty() -> null
            exactOperationMatches.isNotEmpty() -> exactOperationMatches.first()
            else -> matches.firstOrNull { it.score >= REUSE_SCORE }
        }
        val action = when {
            explicitDirective != null -> explicitDirective
            ambiguity.isNotEmpty() -> DocumentRecommendationAction.InsufficientEvidence
            exactOperationMatches.any { it.scope in confirmScopes } -> DocumentRecommendationAction.Confirm
            selected?.scope == DocumentMatchScope.AppliedLocal -> DocumentRecommendationAction.ReuseLocal
            selected?.scope in setOf(DocumentMatchScope.Imported, DocumentMatchScope.CuratedFibo) ->
                DocumentRecommendationAction.ReuseImportedOrFibo
            selected != null -> DocumentRecommendationAction.Extend
            candidate.confidence < 60 -> DocumentRecommendationAction.InsufficientEvidence
            else -> DocumentRecommendationAction.CreateLocal
        }
        val conflicts = if (action == DocumentRecommendationAction.Conflict) conflicts(candidate, matches) else emptyList()
        val clarificationReasons = buildList {
            if (candidate.confidence < 60) add("Candidate confidence is below 60.")
            if (ambiguity.isNotEmpty()) add("Choose one of the equally ranked ontology matches.")
            if (action == DocumentRecommendationAction.Split) add("Confirm the intended split and resulting concepts.")
            if (action == DocumentRecommendationAction.Merge) add("Confirm the intended merge and surviving concept.")
            if (action == DocumentRecommendationAction.Conflict) add("Resolve the conflicting document interpretations.")
            if (action == DocumentRecommendationAction.Supersede) add("Confirm the explicit supersession target.")
            if (action == DocumentRecommendationAction.Revise) add("Confirm that existing trusted meaning should be revised.")
            if (input.targetSourceId == null &&
                action !in noTargetActions
            ) {
                add("Choose a writable target ontology source.")
            }
        }.distinct().sorted()
        val recommendationId = "recommendation-${stableId(
            candidate.identity.value,
            action.name,
            selected?.scope?.name.orEmpty(),
            selected?.entityIri?.value.orEmpty(),
            *candidate.evidence.map { it.id.value }.sorted().toTypedArray(),
        )}"
        return DocumentRecommendation(
            id = recommendationId,
            candidateIds = listOf(candidate.identity.value),
            type = candidate.category,
            category = candidate.recommendationCategory,
            proposedLabel = candidate.proposedLabel,
            proposedValue = candidate.proposedValue,
            proposedDefinition = candidate.proposedDefinition,
            action = action,
            confidence = candidate.confidence,
            rationale = rationale(action, authority, selected),
            evidence = candidate.evidence,
            matches = matches,
            selectedMatch = selected,
            ambiguities = ambiguity,
            conflicts = conflicts,
            mandatoryClarificationReasons = clarificationReasons,
            targetSourceId = input.targetSourceId,
            modelId = input.modelId,
            promptVersion = input.promptVersion,
        )
    }

    private fun directive(
        candidate: DocumentCandidate,
        authority: DocumentAuthorityMetadata?,
    ): DocumentRecommendationAction? = when {
        "unsupported" in candidate.ambiguityFlags -> DocumentRecommendationAction.Unsupported
        "conflict" in candidate.ambiguityFlags || candidate.category == DocumentCandidateCategory.Conflict ->
            DocumentRecommendationAction.Conflict
        "split" in candidate.ambiguityFlags -> DocumentRecommendationAction.Split
        "merge" in candidate.ambiguityFlags -> DocumentRecommendationAction.Merge
        authority?.status == DocumentAuthorityStatus.Superseded ||
            authority?.status == DocumentAuthorityStatus.Amendment ||
            "supersede" in candidate.ambiguityFlags -> DocumentRecommendationAction.Supersede
        "revise" in candidate.ambiguityFlags -> DocumentRecommendationAction.Revise
        "extend" in candidate.ambiguityFlags -> DocumentRecommendationAction.Extend
        else -> null
    }

    private fun score(
        candidate: DocumentCandidate,
        candidateOperation: String?,
        candidateAuthority: DocumentAuthorityMetadata?,
        record: DocumentSemanticRecord,
    ): DocumentMatchCandidate? {
        val normalizedLabel = normalize(candidate.proposedLabel ?: candidate.identity.normalizedValue)
        val recordLabels = (listOfNotNull(record.preferredLabel) + record.aliases).map(::normalize)
        val exactOperation = candidateOperation != null && candidateOperation == record.normalizedTypedOperationKey
        val exactIdentity = record.normalizedIdentityKey == candidate.identity.normalizedValue
        val exactLabel = normalizedLabel in recordLabels
        val overlap = tokenOverlap(normalizedLabel, recordLabels)
        if (!exactOperation && !exactIdentity && !exactLabel && overlap < 50) return null
        val base = when {
            exactOperation -> 100
            exactIdentity -> 92
            exactLabel -> 84
            else -> overlap
        }
        val applicabilityPenalty = applicabilityPenalty(candidateAuthority, record.authority)
        val score = (base - scopeRank(record.scope) - applicabilityPenalty).coerceIn(0, 100)
        val reason = when {
            exactOperation -> "Exact normalized typed-operation identity in ${record.scope.name}."
            exactIdentity -> "Exact normalized semantic identity in ${record.scope.name}."
            exactLabel -> "Exact normalized label; semantic review is still required."
            else -> "Deterministic token overlap; semantic review is required."
        } + if (applicabilityPenalty > 0) " Applicability differs and lowered the rank." else ""
        return DocumentMatchCandidate(
            scope = record.scope,
            entityIri = record.entityIri,
            sourceId = record.sourceId,
            preferredLabel = record.preferredLabel,
            score = score,
            reason = reason,
            normalizedTypedOperationKey = record.normalizedTypedOperationKey,
        )
    }

    private fun applicabilityPenalty(
        candidate: DocumentAuthorityMetadata?,
        existing: DocumentAuthorityMetadata?,
    ): Int {
        if (candidate == null || existing == null) return 0
        var penalty = 0
        if (candidate.jurisdiction != null &&
            existing.jurisdiction != null &&
            !candidate.jurisdiction.equals(existing.jurisdiction, ignoreCase = true)
        ) {
            penalty += 15
        }
        if (candidate.businessArea != null &&
            existing.businessArea != null &&
            !candidate.businessArea.equals(existing.businessArea, ignoreCase = true)
        ) {
            penalty += 10
        }
        val candidateEffectiveDate = candidate.effectiveDate
        val existingExpirationDate = existing.expirationDate
        if (candidateEffectiveDate != null &&
            existingExpirationDate != null &&
            existingExpirationDate.isBefore(candidateEffectiveDate)
        ) {
            penalty += 5
        }
        return penalty
    }

    private fun conflicts(
        candidate: DocumentCandidate,
        matches: List<DocumentMatchCandidate>,
    ): List<DocumentConflict> {
        val evidenceIds = candidate.evidence.map(DocumentEvidence::id).sortedBy { it.value }
        val alternatives = matches.take(2).mapIndexed { index, match ->
            DocumentConflictAlternative(
                id = "alternative-${stableId(candidate.identity.value, index.toString(), match.entityIri.value)}",
                description = "Retain or reconcile the meaning represented by ${match.entityIri.value}.",
                evidenceIds = evidenceIds,
                affectedEntityIris = listOf(match.entityIri),
            )
        }.toMutableList()
        if (alternatives.size < 2) {
            alternatives += DocumentConflictAlternative(
                id = "alternative-${stableId(candidate.identity.value, "document")}",
                description = "Retain the document interpretation as a separate reviewed alternative.",
                evidenceIds = evidenceIds,
            )
        }
        return listOf(
            DocumentConflict(
                id = "conflict-${stableId(candidate.identity.value, *alternatives.map { it.id }.toTypedArray())}",
                alternatives = alternatives,
                resolutionOptions = listOf("choose-existing", "keep-separate", "revise-with-clarification").sorted(),
            ),
        )
    }

    private fun rationale(
        action: DocumentRecommendationAction,
        authority: DocumentAuthorityMetadata?,
        selected: DocumentMatchCandidate?,
    ): String {
        val applicability = listOfNotNull(authority?.businessArea, authority?.jurisdiction).joinToString(" / ")
        return buildString {
            append(
                when (action) {
                    DocumentRecommendationAction.Confirm -> "An exact reviewed identity already exists; retain evidence without a duplicate edit."
                    DocumentRecommendationAction.ReuseLocal -> "A strong applied-local match is available for explicit reuse."
                    DocumentRecommendationAction.ReuseImportedOrFibo -> "A strong imported or curated FIBO match is available for explicit reuse."
                    DocumentRecommendationAction.CreateLocal -> "No sufficiently strong existing semantic match was found."
                    DocumentRecommendationAction.Extend -> "The document explicitly suggests extending existing meaning."
                    DocumentRecommendationAction.Revise -> "The document suggests revising existing meaning and requires clarification."
                    DocumentRecommendationAction.Split -> "The evidence suggests a split that requires human modeling review."
                    DocumentRecommendationAction.Merge -> "The evidence suggests a merge that requires human modeling review."
                    DocumentRecommendationAction.Conflict -> "The evidence contains incompatible alternatives that require a decision."
                    DocumentRecommendationAction.Supersede -> "Document metadata explicitly marks amendment or supersession; recency alone is not used."
                    DocumentRecommendationAction.InsufficientEvidence -> "Evidence or match confidence is insufficient for a draft."
                    DocumentRecommendationAction.Unsupported -> "The requested meaning is outside the approved typed-operation boundary."
                },
            )
            selected?.let { append(" Selected match: ${it.entityIri.value}.") }
            if (applicability.isNotBlank()) append(" Applicability: $applicability.")
            authority?.effectiveDate?.let { append(" Effective date: $it.") }
            authority?.expirationDate?.let { append(" Expiration date: $it.") }
        }.take(2_000)
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun tokenOverlap(candidate: String, labels: List<String>): Int {
        val left = candidate.split(' ').filter(String::isNotBlank).toSet()
        if (left.isEmpty()) return 0
        return labels.maxOfOrNull { label ->
            val right = label.split(' ').filter(String::isNotBlank).toSet()
            if (right.isEmpty()) 0 else ((left.intersect(right).size * 100.0) / left.union(right).size).toInt()
        } ?: 0
    }

    private fun scopeRank(scope: DocumentMatchScope): Int = when (scope) {
        DocumentMatchScope.AppliedLocal -> 0
        DocumentMatchScope.Imported -> 1
        DocumentMatchScope.PrivateDraft -> 2
        DocumentMatchScope.SharedStaging -> 3
        DocumentMatchScope.CurrentProposal -> 4
        DocumentMatchScope.SameTask -> 5
        DocumentMatchScope.DurableProvenance -> 6
        DocumentMatchScope.CuratedFibo -> 7
    }

    private fun stableId(vararg values: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        values.forEach { value ->
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MAX_MATCHES: Int = 20
        const val REUSE_SCORE: Int = 77
        val confirmScopes: Set<DocumentMatchScope> = setOf(
            DocumentMatchScope.AppliedLocal,
            DocumentMatchScope.PrivateDraft,
            DocumentMatchScope.SharedStaging,
            DocumentMatchScope.CurrentProposal,
            DocumentMatchScope.SameTask,
            DocumentMatchScope.DurableProvenance,
        )
        val noTargetActions: Set<DocumentRecommendationAction> = setOf(
            DocumentRecommendationAction.Confirm,
            DocumentRecommendationAction.ReuseLocal,
            DocumentRecommendationAction.ReuseImportedOrFibo,
            DocumentRecommendationAction.InsufficientEvidence,
            DocumentRecommendationAction.Unsupported,
        )
    }
}
