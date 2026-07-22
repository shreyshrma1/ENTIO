package com.entio.semantic

import com.entio.core.ExternalCatalogElement
import com.entio.core.ExternalConfidenceBand
import com.entio.core.ExternalElementCatalogStatus
import com.entio.core.ExternalEntityKind
import com.entio.core.ExternalMatchReason
import com.entio.core.ExternalMatchReasonType
import com.entio.core.ExternalSchemaCandidate
import com.entio.core.ExternalSchemaSearchQuery
import com.entio.core.ExternalScoreBreakdown
import com.entio.core.Iri
import com.entio.core.OntologyEntityDescriptor
import java.security.MessageDigest
import java.util.Locale

private fun normalizeValue(value: String): String = value.trim()
    .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
    .replace(Regex("[^A-Za-z0-9]+"), " ")
    .trim()
    .replace(Regex("\\s+"), " ")
    .lowercase(Locale.ROOT)

/** Deterministic, index-only schema retrieval for the approved FIBO search model. */
public class FiboSchemaSearchService {
    public fun search(
        session: ExternalFiboCatalogSession,
        query: ExternalSchemaSearchQuery,
    ): ExternalSchemaSearchResponse {
        val curatedModules = session.browseCuratedModules(pageSize = 100).items.map { it.ontologyIri }.toSet()
        return search(session.allElements(), query, curatedModules)
    }

    public fun search(
        elements: Iterable<ExternalCatalogElement>,
        query: ExternalSchemaSearchQuery,
        curatedModules: Set<Iri> = emptySet(),
    ): ExternalSchemaSearchResponse {
        val candidates = elements.toList()
        val normalized = QueryNormalization.from(query.text)
        val lexicalSeeds = candidates
            .filter { lexicalNameScore(it, normalized) >= RELATED_NAME_THRESHOLD }
            .associateBy { it.descriptor.descriptor.common.entity }
        val ranked = candidates.mapNotNull { element ->
            val scored = score(element, query, normalized, candidates, curatedModules, lexicalSeeds) ?: return@mapNotNull null
            if (scored.total < query.minimumScore) null else scored
        }
        val tieGroups = ranked.groupBy { it.tieKey() }
        val ordered = ranked
            .sortedWith(rankedComparator)
            .map { candidate ->
                candidate.copy(tieGroupId = tieGroups[candidate.tieKey()]
                    ?.takeIf { it.size > 1 }
                    ?.let { tieGroupId(candidate.tieKey()) })
            }
        val start = query.page * query.pageSize
        val pageItems = if (start >= ordered.size) emptyList() else ordered.drop(start).take(query.pageSize)
        val responseCandidates = pageItems.map { rankedCandidate ->
            rankedCandidate.toExternalCandidate(ordered.size, query.pageSize, query.page)
        }
        return ExternalSchemaSearchResponse(
            candidates = responseCandidates,
            totalResultCount = ordered.size,
            page = query.page,
            pageSize = query.pageSize,
        )
    }

    private fun score(
        element: ExternalCatalogElement,
        query: ExternalSchemaSearchQuery,
        normalized: QueryNormalization,
        allElements: List<ExternalCatalogElement>,
        curatedModules: Set<Iri>,
        lexicalSeeds: Map<com.entio.core.RdfResource, ExternalCatalogElement>,
    ): RankedCandidate? {
        val descriptor = element.descriptor.descriptor
        val external = element.descriptor
        if (query.kind != null && element.kind != query.kind) return null
        if (query.moduleIri != null && external.moduleIri != query.moduleIri) return null
        if (query.domain != null && !external.domain.equals(query.domain, ignoreCase = true)) return null
        if (query.curatedOnly && external.moduleIri !in curatedModules) return null
        val allowedMaturity = if (query.includeInformative) {
            query.maturity + setOf(
                com.entio.core.ExternalOntologyMaturity.Informative,
                com.entio.core.ExternalOntologyMaturity.Deprecated,
            )
        } else {
            query.maturity
        }
        if (external.maturity !in allowedMaturity) return null

        val context = contextScore(descriptor, external.moduleIri, external.domain, query, allElements)
        if (query.context.parentRequired && !context.parentMatch) return null
        if (query.context.domainRequired && !context.domainMatch) return null
        if (query.context.rangeRequired && !context.rangeMatch) return null

        val preferredLabelScore = descriptor.common.preferredLabel?.lexicalForm?.let { preferredLabelScore(it, normalized) } ?: 0
        val alternateLabelScore = descriptor.common.alternateLabels.maxOfOrNull { alternateLabelScore(it.lexicalForm, normalized) } ?: 0
        val iriScore = iriScore(descriptor.common.entity.value, normalized)
        val nameScore = maxOf(preferredLabelScore, alternateLabelScore, iriScore).coerceAtMost(60)
        val definitionScore = descriptor.common.definitions.maxOfOrNull {
            definitionScore(it.lexicalForm, normalized)
        }?.coerceAtMost(20) ?: 0
        val relatedConcepts = relatedConcepts(element, lexicalSeeds)
        val catalogScore = if (external.moduleIri in curatedModules) 5 else 1
        val localScore = if (external.catalogStatus == ExternalElementCatalogStatus.AlreadyUsed) 3 else 0
        val breakdown = ExternalScoreBreakdown(
            nameOrIri = nameScore,
            definition = definitionScore,
            semanticContext = (context.score + relatedConcepts.sumOf { it.points }).coerceAtMost(40),
            catalogStatus = catalogScore,
            localProjectRelevance = localScore,
        )
        return RankedCandidate(
            element = element,
            score = breakdown,
            preferredLabelScore = preferredLabelScore,
            definitionScore = definitionScore,
            relatedConceptScore = relatedConcepts.maxOfOrNull { it.points } ?: 0,
            reasons = reasons(
                element,
                query,
                normalized,
                preferredLabelScore,
                alternateLabelScore,
                iriScore,
                nameScore,
                definitionScore,
                context,
                relatedConcepts,
                catalogScore,
                localScore,
            ),
            exactIri = normalized.original == descriptor.common.entity.value,
            tieGroupId = null,
        )
    }

    private fun reasons(
        element: ExternalCatalogElement,
        query: ExternalSchemaSearchQuery,
        normalized: QueryNormalization,
        preferredLabelScore: Int,
        alternateLabelScore: Int,
        iriScore: Int,
        nameScore: Int,
        definitionScore: Int,
        context: ContextScore,
        relatedConcepts: List<RelatedConceptMatch>,
        catalogScore: Int,
        localScore: Int,
    ): List<ExternalMatchReason> = buildList {
        val descriptor = element.descriptor.descriptor
        if (iriScore == nameScore && iriScore > 0) {
            add(ExternalMatchReason(ExternalMatchReasonType.Iri, iriScore, "iri", normalized.original))
        }
        if (preferredLabelScore == nameScore && preferredLabelScore > 0) {
            add(
                ExternalMatchReason(
                    ExternalMatchReasonType.PreferredLabel,
                    preferredLabelScore,
                    "preferredLabel",
                    descriptor.common.preferredLabel?.lexicalForm,
                ),
            )
        }
        if (alternateLabelScore == nameScore && alternateLabelScore > 0) {
            add(
                ExternalMatchReason(
                    ExternalMatchReasonType.AlternateLabel,
                    alternateLabelScore,
                    "alternateLabel",
                    descriptor.common.alternateLabels.firstOrNull()?.lexicalForm,
                ),
            )
        }
        if (definitionScore > 0) {
            add(
                ExternalMatchReason(
                    ExternalMatchReasonType.Definition,
                    definitionScore,
                    "definition",
                    descriptor.common.definitions.firstOrNull()?.lexicalForm,
                ),
            )
        }
        context.parentPoints.takeIf { it > 0 }?.let {
            add(ExternalMatchReason(ExternalMatchReasonType.ParentCompatibility, it, "parent", relatedIri = query.context.parentIri))
        }
        context.domainPoints.takeIf { it > 0 }?.let {
            add(ExternalMatchReason(ExternalMatchReasonType.DomainCompatibility, it, "domain", relatedIri = query.context.domainIri))
        }
        context.rangePoints.takeIf { it > 0 }?.let {
            add(ExternalMatchReason(ExternalMatchReasonType.RangeCompatibility, it, "range", relatedIri = query.context.rangeIri))
        }
        relatedConcepts.forEach { related ->
            add(ExternalMatchReason(ExternalMatchReasonType.RelatedConcept, related.points, related.relationship, relatedIri = related.relatedIri))
        }
        if (catalogScore == 5) {
            add(ExternalMatchReason(ExternalMatchReasonType.CuratedPackage, catalogScore, "catalog", "FIBO Foundations"))
        }
        if (localScore > 0) {
            add(ExternalMatchReason(ExternalMatchReasonType.LocalProjectUse, localScore, "localProjectUse"))
        }
    }

    private fun contextScore(
        descriptor: OntologyEntityDescriptor,
        moduleIri: Iri,
        domain: String,
        query: ExternalSchemaSearchQuery,
        allElements: List<ExternalCatalogElement>,
    ): ContextScore {
        val context = query.context
        val classDescriptor = descriptor as? OntologyEntityDescriptor.Class
        val objectProperty = descriptor as? OntologyEntityDescriptor.ObjectProperty
        val datatypeProperty = descriptor as? OntologyEntityDescriptor.DatatypeProperty
        val propertyDomains = objectProperty?.domains ?: datatypeProperty?.domains.orEmpty()
        val propertyRanges = objectProperty?.ranges ?: datatypeProperty?.datatypeRanges.orEmpty()
        val directParent = context.parentIri != null && classDescriptor?.directSuperclasses?.contains(context.parentIri) == true
        val broaderParent = context.parentIri != null && !directParent && classDescriptor?.let {
            hasExplicitAncestor(it, context.parentIri, allElements, mutableSetOf())
        } == true
        val domainMatch = context.domainIri != null && context.domainIri in propertyDomains
        val rangeMatch = context.rangeIri != null && context.rangeIri in propertyRanges
        val contextLocationMatch = query.moduleIri == moduleIri || query.domain?.equals(domain, ignoreCase = true) == true
        val parentPoints = when {
            directParent -> 8
            broaderParent -> 5
            else -> 0
        }
        val propertyPoints = (if (domainMatch) 6 else 0) + (if (rangeMatch) 6 else 0)
        val locationPoints = if (contextLocationMatch) {
            if (classDescriptor != null) 4 else 3
        } else {
            0
        }
        return ContextScore(
            parentMatch = directParent || broaderParent,
            domainMatch = domainMatch,
            rangeMatch = rangeMatch,
            parentPoints = parentPoints,
            domainPoints = if (domainMatch) 6 else 0,
            rangePoints = if (rangeMatch) 6 else 0,
            score = (parentPoints + propertyPoints + locationPoints).coerceAtMost(12),
        )
    }

    /** Finds direct ontology relationships to concepts that matched the query text. */
    private fun relatedConcepts(
        element: ExternalCatalogElement,
        lexicalSeeds: Map<com.entio.core.RdfResource, ExternalCatalogElement>,
    ): List<RelatedConceptMatch> {
        if (lexicalSeeds.isEmpty()) return emptyList()
        val descriptor = element.descriptor.descriptor
        val entity = descriptor.common.entity as? Iri ?: return emptyList()
        val matches = mutableListOf<RelatedConceptMatch>()
        when (descriptor) {
            is OntologyEntityDescriptor.Class -> {
                descriptor.directSuperclasses
                    .filter { it in lexicalSeeds }
                    .forEach { matches += RelatedConceptMatch("subclass", it) }
                lexicalSeeds.values
                    .map { it.descriptor.descriptor }
                    .filterIsInstance<OntologyEntityDescriptor.Class>()
                    .filter { entity in it.directSuperclasses }
                    .forEach { matches += RelatedConceptMatch("parent", it.common.entity as Iri) }
            }
            is OntologyEntityDescriptor.ObjectProperty -> {
                descriptor.domains.filter { it in lexicalSeeds }.forEach { matches += RelatedConceptMatch("domain", it) }
                descriptor.ranges.filter { it in lexicalSeeds }.forEach { matches += RelatedConceptMatch("range", it) }
            }
            is OntologyEntityDescriptor.DatatypeProperty -> {
                descriptor.domains.filter { it in lexicalSeeds }.forEach { matches += RelatedConceptMatch("domain", it) }
                descriptor.datatypeRanges.filter { it in lexicalSeeds }.forEach { matches += RelatedConceptMatch("range", it) }
            }
            else -> Unit
        }
        return matches.distinctBy { it.relationship to it.relatedIri }
    }

    private fun lexicalNameScore(element: ExternalCatalogElement, normalized: QueryNormalization): Int {
        val descriptor = element.descriptor.descriptor
        val preferred = descriptor.common.preferredLabel?.lexicalForm?.let { preferredLabelScore(it, normalized) } ?: 0
        val alternate = descriptor.common.alternateLabels.maxOfOrNull { alternateLabelScore(it.lexicalForm, normalized) } ?: 0
        return maxOf(preferred, alternate, iriScore(descriptor.common.entity.value, normalized))
    }

    private fun hasExplicitAncestor(
        descriptor: OntologyEntityDescriptor.Class,
        target: Iri?,
        allElements: List<ExternalCatalogElement>,
        visited: MutableSet<Iri>,
    ): Boolean {
        if (target == null) return false
        descriptor.directSuperclasses.forEach { parent ->
            if (parent == target) return true
            if (visited.add(parent)) {
                val parentClass = allElements.firstOrNull {
                    it.descriptor.descriptor.common.entity == parent && it.descriptor.descriptor is OntologyEntityDescriptor.Class
                }?.descriptor?.descriptor as? OntologyEntityDescriptor.Class
                if (parentClass != null && hasExplicitAncestor(parentClass, target, allElements, visited)) return true
            }
        }
        return false
    }

    private fun preferredLabelScore(value: String, query: QueryNormalization): Int {
        val normalized = normalizeValue(value)
        if (normalized.isBlank() || query.normalized.isBlank()) return 0
        val valueTokens = normalized.split(' ')
        return when {
            normalized == query.normalized -> 50
            valueTokens.toSet() == query.tokens.toSet() && valueTokens.size == query.tokens.size -> 44
            normalized.startsWith(query.normalized) -> 38
            normalized.contains(query.normalized) -> 34
            query.tokens.all { it in valueTokens } -> 30
            else -> partialScore(valueTokens, query.tokens, 20)
        }
    }

    private fun alternateLabelScore(value: String, query: QueryNormalization): Int {
        val normalized = normalizeValue(value)
        if (normalized.isBlank() || query.normalized.isBlank()) return 0
        val valueTokens = normalized.split(' ')
        return when {
            normalized == query.normalized -> 36
            query.tokens.all { it in valueTokens } -> 24
            else -> partialScore(valueTokens, query.tokens, 16)
        }
    }

    private fun definitionScore(value: String, query: QueryNormalization): Int {
        val normalized = normalizeValue(value)
        if (normalized.isBlank() || query.normalized.isBlank() || query.tokens.isEmpty()) return 0
        val valueTokens = normalized.split(' ').toSet()
        val matched = query.tokens.count { it in valueTokens }
        return when {
            normalized == query.normalized -> 20
            matched == query.tokens.size -> 16
            matched * 4 >= query.tokens.size * 3 -> 12
            matched * 2 >= query.tokens.size -> 8
            matched > 0 -> 4
            else -> 0
        }
    }

    private fun partialScore(valueTokens: List<String>, queryTokens: List<String>, maximum: Int): Int {
        if (queryTokens.isEmpty()) return 0
        val matched = queryTokens.count { it in valueTokens }
        return (maximum * matched) / queryTokens.size
    }

    private fun iriScore(value: String, query: QueryNormalization): Int {
        if (query.original == value) return 60
        val localName = value.substringAfterLast('#', value.substringAfterLast('/'))
        val normalizedLocalName = normalizeValue(localName)
        return when {
            normalizedLocalName == query.normalized && query.normalized.isNotBlank() -> 42
            query.normalized.isNotBlank() && normalizedLocalName.contains(query.normalized) -> 24
            query.normalized.isNotBlank() && normalizeValue(value).contains(query.normalized) -> 24
            else -> 0
        }
    }

    private data class QueryNormalization(
        val original: String,
        val normalized: String,
        val tokens: List<String>,
    ) {
        companion object {
            fun from(value: String): QueryNormalization {
                val normalized = normalizeValue(value)
                val tokens = normalized.split(' ')
                    .filter { it.isNotBlank() }
                    .filter { it.length >= 3 || it == "has" || it == "is" }
                    .filterNot(STOPWORDS::contains)
                    .distinct()
                return QueryNormalization(value.trim(), tokens.joinToString(" "), tokens)
            }
        }
    }

    private data class ContextScore(
        val parentMatch: Boolean,
        val domainMatch: Boolean,
        val rangeMatch: Boolean,
        val parentPoints: Int,
        val domainPoints: Int,
        val rangePoints: Int,
        val score: Int,
    )

    private data class RelatedConceptMatch(
        val relationship: String,
        val relatedIri: Iri,
        val points: Int = RELATED_CONCEPT_POINTS,
    )

    private data class RankedCandidate(
        val element: ExternalCatalogElement,
        val score: ExternalScoreBreakdown,
        val preferredLabelScore: Int,
        val definitionScore: Int,
        val relatedConceptScore: Int,
        val reasons: List<ExternalMatchReason>,
        val exactIri: Boolean,
        val tieGroupId: String?,
    ) {
        val total: Int get() = score.total

        fun tieKey(): String = listOf(
            score.nameOrIri,
            score.definition,
            score.semanticContext,
            score.catalogStatus,
            score.localProjectRelevance,
        ).joinToString(":")

        fun toExternalCandidate(totalCount: Int, pageSize: Int, page: Int): ExternalSchemaCandidate = ExternalSchemaCandidate(
            descriptor = element.descriptor,
            kind = element.kind,
            score = score,
            confidence = when {
                exactIri || total >= 60 -> ExternalConfidenceBand.VeryStrong
                total >= 45 -> ExternalConfidenceBand.Strong
                total >= 30 -> ExternalConfidenceBand.Possible
                total >= 20 -> ExternalConfidenceBand.Weak
                else -> ExternalConfidenceBand.LowConfidence
            },
            reasons = reasons,
            tieGroupId = tieGroupId,
            totalResultCount = totalCount,
            pageSize = pageSize,
            page = page,
        )
    }

    private companion object {
        // Expand relationships from strong concept hits, not every definition
        // or partial name match, to keep result pages focused and useful.
        private const val RELATED_NAME_THRESHOLD = 42
        private const val RELATED_CONCEPT_POINTS = 40
        private val STOPWORDS = setOf("a", "an", "the", "of", "for", "to", "in", "on", "by", "with", "from", "and", "or", "as", "at")
        private val rankedComparator = compareByDescending<RankedCandidate> { maxOf(it.score.nameOrIri, it.relatedConceptScore) }
            .thenByDescending { it.preferredLabelScore }
            .thenByDescending { it.definitionScore }
            .thenByDescending { it.score.semanticContext }
            .thenByDescending { it.score.catalogStatus }
            .thenBy { maturityOrder(it.element.descriptor.maturity) }
            .thenBy { kindOrder(it.element.kind) }
            .thenBy { it.element.descriptor.descriptor.common.preferredLabel?.lexicalForm?.lowercase(Locale.ROOT).orEmpty() }
            .thenBy { it.element.descriptor.descriptor.common.entity.value }

        private fun kindOrder(kind: ExternalEntityKind): Int = when (kind) {
            ExternalEntityKind.Class -> 0
            ExternalEntityKind.ObjectProperty -> 1
            ExternalEntityKind.DatatypeProperty -> 2
        }

        private fun maturityOrder(maturity: com.entio.core.ExternalOntologyMaturity): Int = when (maturity) {
            com.entio.core.ExternalOntologyMaturity.Release -> 0
            com.entio.core.ExternalOntologyMaturity.Provisional -> 1
            com.entio.core.ExternalOntologyMaturity.Informative -> 2
            com.entio.core.ExternalOntologyMaturity.Deprecated -> 3
            com.entio.core.ExternalOntologyMaturity.Unknown -> 4
        }

        private fun tieGroupId(key: String): String = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(12)
    }
}

public data class ExternalSchemaSearchResponse(
    public val candidates: List<ExternalSchemaCandidate>,
    public val totalResultCount: Int,
    public val page: Int,
    public val pageSize: Int,
) {
    public val hasNext: Boolean get() = (page + 1) * pageSize < totalResultCount
}
