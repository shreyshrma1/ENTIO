package com.entio.semantic

import com.entio.core.DocumentAnalysisPipelineVersions
import com.entio.core.DocumentCandidateExtractionCategory
import com.entio.core.DocumentGroundedCandidate
import com.entio.core.DocumentMatchScope
import com.entio.core.DocumentOntologyRetrievalResult
import com.entio.core.DocumentOntologyRetrievalSelection
import com.entio.core.DocumentRetrievalFingerprints
import com.entio.core.DocumentRetrievalMatchReason
import com.entio.core.DocumentRetrievalStructuralContext
import com.entio.core.EntioProject
import com.entio.core.ExternalEntityKind
import com.entio.core.ExternalSchemaSearchQuery
import com.entio.core.Iri
import com.entio.core.LocalityStatus
import com.entio.core.OntologyEntityDescriptor
import com.entio.core.SemanticDescriptorKind
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

public data class DocumentOntologyRetrievalRecord(
    val projectId: String,
    val matcherRecord: DocumentSemanticRecord,
    val kind: SemanticDescriptorKind,
    val definition: String? = null,
    val structuralContext: DocumentRetrievalStructuralContext = DocumentRetrievalStructuralContext(),
    val writable: Boolean = false,
    val fingerprints: DocumentRetrievalFingerprints,
) {
    init {
        require(projectId.isNotBlank())
        require(matcherRecord.scope !in setOf(DocumentMatchScope.Imported, DocumentMatchScope.CuratedFibo) || !writable)
    }
}

public data class DocumentOntologyRetrievalInput(
    val projectId: String,
    val candidates: List<DocumentGroundedCandidate>,
    val project: EntioProject,
    val importedRecords: List<DocumentOntologyRetrievalRecord> = emptyList(),
    val currentWorkRecords: List<DocumentOntologyRetrievalRecord> = emptyList(),
    val sameTaskRecords: List<DocumentOntologyRetrievalRecord> = emptyList(),
    val provenanceRecords: List<DocumentOntologyRetrievalRecord> = emptyList(),
    val fiboSession: ExternalFiboCatalogSession? = null,
    val fingerprints: DocumentRetrievalFingerprints,
) {
    init {
        require(projectId.isNotBlank())
        require(candidates == candidates.distinctBy(DocumentGroundedCandidate::id)
            .sortedBy(DocumentGroundedCandidate::stableOrderingKey))
        val explicitRecords = importedRecords + currentWorkRecords + sameTaskRecords + provenanceRecords
        require(explicitRecords.all { it.projectId == projectId })
        require(explicitRecords.all { it.fingerprints == fingerprints })
        require(importedRecords.all { it.matcherRecord.scope == DocumentMatchScope.Imported && !it.writable })
        require(currentWorkRecords.all {
            it.matcherRecord.scope in setOf(
                DocumentMatchScope.PrivateDraft,
                DocumentMatchScope.SharedStaging,
                DocumentMatchScope.CurrentProposal,
            )
        })
        require(sameTaskRecords.all { it.matcherRecord.scope == DocumentMatchScope.SameTask })
        require(provenanceRecords.all { it.matcherRecord.scope == DocumentMatchScope.DurableProvenance })
    }
}

public data class DocumentFullStateMatch(
    val candidateId: String,
    val scope: DocumentMatchScope,
    val canonicalIri: Iri,
    val sourceId: String,
    val exactIdentity: Boolean,
    val exactTypedOperation: Boolean,
)

public data class DocumentOntologyRetrievalBatch(
    val results: List<DocumentOntologyRetrievalResult>,
    val fullStateMatches: List<DocumentFullStateMatch>,
)

/** Coordinates existing deterministic descriptor, matcher-record, and FIBO search boundaries. */
public class DocumentOntologyRetrievalService(
    private val descriptions: SemanticDescriptionService = SemanticDescriptionService(),
    private val fiboSearch: FiboSchemaSearchService = FiboSchemaSearchService(),
) {
    public fun retrieve(input: DocumentOntologyRetrievalInput): DocumentOntologyRetrievalBatch {
        val projectRecords = descriptions.describeAll(input.project).map { descriptor ->
            descriptorRecord(input.projectId, descriptor, input.fingerprints)
        }
        val supplied = input.importedRecords + input.currentWorkRecords + input.sameTaskRecords + input.provenanceRecords
        val baseRecords = (projectRecords + supplied).validatedDistinct()
        val fiboRecordsByCandidateId = input.candidates.associate { candidate ->
            candidate.id to input.fiboSession?.let {
                searchFibo(input.projectId, candidate, it, input.fingerprints)
            }.orEmpty()
        }
        val results = input.candidates.map { candidate ->
            val fiboRecords = fiboRecordsByCandidateId.getValue(candidate.id)
            result(candidate, (baseRecords + fiboRecords).validatedDistinct())
        }
        val fullStateMatches = input.candidates.flatMap { candidate ->
            fullStateMatches(candidate, baseRecords + fiboRecordsByCandidateId.getValue(candidate.id))
        }.sortedWith(compareBy(DocumentFullStateMatch::candidateId)
            .thenBy { it.scope.ordinal }
            .thenBy { it.canonicalIri.value }
            .thenBy(DocumentFullStateMatch::sourceId))
        return DocumentOntologyRetrievalBatch(results, fullStateMatches)
    }

    private fun result(
        candidate: DocumentGroundedCandidate,
        records: List<DocumentOntologyRetrievalRecord>,
    ): DocumentOntologyRetrievalResult {
        val selections = records.asSequence()
            .filter { compatible(candidate.category, it.kind) }
            .mapNotNull { score(candidate, it) }
            .distinctBy { Triple(it.scope, it.canonicalIri, it.sourceId) }
            .sortedBy(DocumentOntologyRetrievalSelection::stableOrderingKey)
            .take(com.entio.core.MAX_DOCUMENT_RETRIEVAL_CHOICES_PER_CANDIDATE)
            .toList()
        return DocumentOntologyRetrievalResult(
            candidateId = candidate.id,
            queryVersion = DocumentAnalysisPipelineVersions.RETRIEVAL_QUERY,
            rankingVersion = DocumentAnalysisPipelineVersions.RETRIEVAL_RANKING,
            resultVersion = DocumentAnalysisPipelineVersions.RETRIEVAL_RESULT,
            selections = selections,
            completeAuthorizedScopeSearch = true,
        )
    }

    private fun score(
        candidate: DocumentGroundedCandidate,
        record: DocumentOntologyRetrievalRecord,
    ): DocumentOntologyRetrievalSelection? {
        val normalized = normalize(candidate.normalizedText)
        val matcher = record.matcherRecord
        val labels = listOfNotNull(matcher.preferredLabel) + matcher.aliases
        val normalizedLabels = labels.map(::normalize)
        val exactIdentity = matcher.normalizedIdentityKey == normalized
        val exactLabel = normalized in normalizedLabels
        val overlap = tokenOverlap(normalized, normalizedLabels)
        val iriMatch = normalize(matcher.entityIri.value.substringAfterLast('#').substringAfterLast('/')) == normalized
        if (!exactIdentity && !exactLabel && !iriMatch && overlap < MINIMUM_OVERLAP) return null
        val score = when {
            exactIdentity -> 100
            exactLabel -> 95
            iriMatch -> 90
            else -> overlap
        }
        val reasons = buildList {
            if (exactIdentity) add(DocumentRetrievalMatchReason("identity", "Exact normalized identity", 100))
            if (exactLabel) add(DocumentRetrievalMatchReason("preferred-label", "Exact normalized label", 95))
            if (iriMatch) add(DocumentRetrievalMatchReason("iri", "Exact normalized IRI local name", 90))
            if (!exactIdentity && !exactLabel && !iriMatch) {
                add(DocumentRetrievalMatchReason("token-overlap", "Bounded lexical token overlap", overlap))
            }
            candidate.hints.forEach { hint ->
                val hintOverlap = tokenOverlap(normalize(hint.text), normalizedLabels)
                if (hintOverlap >= MINIMUM_OVERLAP) {
                    add(DocumentRetrievalMatchReason("context-${hint.role.name.lowercase()}", "Nearby candidate context", 5))
                }
            }
        }.distinct().sortedBy(DocumentRetrievalMatchReason::stableOrderingKey)
        val selectionId = "selection-${stableId(
            candidate.id,
            matcher.entityIri.value,
            record.kind.name,
            matcher.scope.name,
            matcher.sourceId,
            DocumentAnalysisPipelineVersions.RETRIEVAL_RANKING,
            record.fingerprints.ontologySha256,
            record.fingerprints.currentWorkSha256,
            record.fingerprints.provenanceSha256,
            record.fingerprints.catalogSha256,
        ).take(32)}"
        return DocumentOntologyRetrievalSelection(
            selectionId = selectionId,
            candidateId = candidate.id,
            canonicalIri = matcher.entityIri,
            kind = record.kind,
            scope = matcher.scope,
            sourceId = matcher.sourceId,
            writable = record.writable,
            preferredLabel = matcher.preferredLabel,
            alternateLabels = matcher.aliases.take(com.entio.core.MAX_DOCUMENT_RETRIEVAL_ALTERNATE_LABELS),
            definition = record.definition?.take(500),
            structuralContext = record.structuralContext,
            score = score.coerceIn(0, 100),
            matchReasons = reasons,
            fingerprints = record.fingerprints,
        )
    }

    private fun descriptorRecord(
        projectId: String,
        descriptor: OntologyEntityDescriptor,
        fingerprints: DocumentRetrievalFingerprints,
    ): DocumentOntologyRetrievalRecord {
        val common = descriptor.common
        val scope = if (common.locality == LocalityStatus.Imported) DocumentMatchScope.Imported else DocumentMatchScope.AppliedLocal
        return DocumentOntologyRetrievalRecord(
            projectId = projectId,
            matcherRecord = DocumentSemanticRecord(
                scope = scope,
                entityIri = Iri(common.entity.value),
                sourceId = common.sourceId,
                preferredLabel = common.preferredLabel?.lexicalForm,
                aliases = common.alternateLabels.map { it.lexicalForm }.distinct().sorted(),
                category = null,
                normalizedIdentityKey = common.preferredLabel?.lexicalForm?.let(::normalize),
                normalizedTypedOperationKey = null,
            ),
            kind = common.kind,
            definition = common.definitions.firstOrNull()?.lexicalForm,
            structuralContext = structuralContext(descriptor),
            writable = scope == DocumentMatchScope.AppliedLocal,
            fingerprints = fingerprints,
        )
    }

    private fun searchFibo(
        projectId: String,
        candidate: DocumentGroundedCandidate,
        session: ExternalFiboCatalogSession,
        fingerprints: DocumentRetrievalFingerprints,
    ): List<DocumentOntologyRetrievalRecord> = fiboSearch.search(
        session,
        ExternalSchemaSearchQuery(text = candidate.displayText, pageSize = 100, curatedOnly = true),
    ).candidates.map { fibo ->
        val descriptor = fibo.descriptor.descriptor
        DocumentOntologyRetrievalRecord(
            projectId = projectId,
            matcherRecord = DocumentSemanticRecord(
                scope = DocumentMatchScope.CuratedFibo,
                entityIri = Iri(descriptor.common.entity.value),
                sourceId = fibo.descriptor.sourceId,
                preferredLabel = descriptor.common.preferredLabel?.lexicalForm,
                aliases = descriptor.common.alternateLabels.map { it.lexicalForm }.distinct().sorted(),
                category = null,
                normalizedIdentityKey = descriptor.common.preferredLabel?.lexicalForm?.let(::normalize),
                normalizedTypedOperationKey = null,
            ),
            kind = fibo.kind.semanticKind,
            definition = descriptor.common.definitions.firstOrNull()?.lexicalForm,
            structuralContext = structuralContext(descriptor),
            writable = false,
            fingerprints = fingerprints,
        )
    }

    private fun fullStateMatches(
        candidate: DocumentGroundedCandidate,
        records: List<DocumentOntologyRetrievalRecord>,
    ): List<DocumentFullStateMatch> = records.mapNotNull { record ->
        val normalized = normalize(candidate.normalizedText)
        val matcher = record.matcherRecord
        val exactIdentity = matcher.normalizedIdentityKey == normalized ||
            listOfNotNull(matcher.preferredLabel).plus(matcher.aliases).map(::normalize).contains(normalized)
        val exactOperation = matcher.normalizedTypedOperationKey?.let { it == normalized } == true
        if (!exactIdentity && !exactOperation) null else DocumentFullStateMatch(
            candidate.id,
            matcher.scope,
            matcher.entityIri,
            matcher.sourceId,
            exactIdentity,
            exactOperation,
        )
    }.distinct().sortedWith(compareBy<DocumentFullStateMatch> { it.scope.ordinal }
        .thenBy { it.canonicalIri.value }
        .thenBy(DocumentFullStateMatch::sourceId))

    private fun List<DocumentOntologyRetrievalRecord>.validatedDistinct(): List<DocumentOntologyRetrievalRecord> {
        require(all { record ->
            record.matcherRecord.scope != DocumentMatchScope.CuratedFibo || !record.writable
        })
        return distinctBy { Triple(it.matcherRecord.scope, it.matcherRecord.entityIri, it.matcherRecord.sourceId) }
    }

    private fun structuralContext(descriptor: OntologyEntityDescriptor): DocumentRetrievalStructuralContext = when (descriptor) {
        is OntologyEntityDescriptor.Class -> DocumentRetrievalStructuralContext(
            superclassIris = descriptor.directSuperclasses.distinct().sortedBy(Iri::value).take(5),
        )
        is OntologyEntityDescriptor.ObjectProperty -> DocumentRetrievalStructuralContext(
            domainIris = descriptor.domains.distinct().sortedBy(Iri::value).take(5),
            rangeIris = descriptor.ranges.distinct().sortedBy(Iri::value).take(5),
        )
        is OntologyEntityDescriptor.DatatypeProperty -> DocumentRetrievalStructuralContext(
            domainIris = descriptor.domains.distinct().sortedBy(Iri::value).take(5),
            datatypeIris = descriptor.datatypeRanges.distinct().sortedBy(Iri::value).take(5),
        )
        is OntologyEntityDescriptor.Individual -> DocumentRetrievalStructuralContext(
            assertedTypeIris = descriptor.assertedTypes.distinct().sortedBy(Iri::value).take(5),
        )
        is OntologyEntityDescriptor.AnnotationProperty -> DocumentRetrievalStructuralContext()
    }

    private fun compatible(category: DocumentCandidateExtractionCategory, kind: SemanticDescriptorKind): Boolean = when (category) {
        DocumentCandidateExtractionCategory.Person,
        DocumentCandidateExtractionCategory.Organization,
        DocumentCandidateExtractionCategory.Location,
        DocumentCandidateExtractionCategory.Identifier,
        -> kind in setOf(SemanticDescriptorKind.Class, SemanticDescriptorKind.Individual)
        DocumentCandidateExtractionCategory.RelationshipPhrase ->
            kind in setOf(SemanticDescriptorKind.ObjectProperty, SemanticDescriptorKind.DatatypeProperty)
        DocumentCandidateExtractionCategory.AttributeValue,
        DocumentCandidateExtractionCategory.MonetaryAmount,
        DocumentCandidateExtractionCategory.Date,
        -> kind in setOf(SemanticDescriptorKind.DatatypeProperty, SemanticDescriptorKind.Individual)
        DocumentCandidateExtractionCategory.ConceptTerm,
        DocumentCandidateExtractionCategory.RuleCue,
        DocumentCandidateExtractionCategory.Administrative,
        DocumentCandidateExtractionCategory.Illustrative,
        -> true
    }

    private companion object {
        private const val MINIMUM_OVERLAP = 40

        private val ExternalEntityKind.semanticKind: SemanticDescriptorKind
            get() = when (this) {
                ExternalEntityKind.Class -> SemanticDescriptorKind.Class
                ExternalEntityKind.ObjectProperty -> SemanticDescriptorKind.ObjectProperty
                ExternalEntityKind.DatatypeProperty -> SemanticDescriptorKind.DatatypeProperty
            }

        private fun normalize(value: String): String = value.trim()
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)

        private fun tokenOverlap(value: String, candidates: List<String>): Int {
            val tokens = value.split(' ').filter(String::isNotBlank).toSet()
            if (tokens.isEmpty()) return 0
            return candidates.maxOfOrNull { candidate ->
                val other = candidate.split(' ').filter(String::isNotBlank).toSet()
                if (other.isEmpty()) 0 else (tokens.intersect(other).size * 100) / tokens.union(other).size
            } ?: 0
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
    }
}
