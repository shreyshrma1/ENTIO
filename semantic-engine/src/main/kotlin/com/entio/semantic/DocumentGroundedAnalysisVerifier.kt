package com.entio.semantic

import com.entio.core.DocumentAnalysisWorkKey
import com.entio.core.DocumentCandidateExtractionCategory
import com.entio.core.DocumentConfidenceDimensions
import com.entio.core.DocumentEditableGroundedField
import com.entio.core.DocumentEditableGroundedFieldKind
import com.entio.core.DocumentGroundedAnalysisResult
import com.entio.core.DocumentGroundedCandidate
import com.entio.core.DocumentGroundedCoverageDisposition
import com.entio.core.DocumentGroundedDisposition
import com.entio.core.DocumentGroundedRecommendationStatus
import com.entio.core.DocumentGroundedSemanticItem
import com.entio.core.DocumentMatchScope
import com.entio.core.DocumentOntologyRetrievalResult
import com.entio.core.DocumentOntologyRetrievalSelection
import com.entio.core.DocumentPrerequisiteOrigin
import com.entio.core.DocumentSemanticItemKind
import com.entio.core.DocumentSemanticOutcome
import com.entio.core.DocumentSemanticPlan
import com.entio.core.DocumentSemanticPlanItem
import com.entio.core.DocumentSemanticRecommendationGroup
import com.entio.core.DocumentSemanticReference
import com.entio.core.DocumentSemanticReferenceRole
import com.entio.core.DocumentSemanticReferenceTarget
import com.entio.core.DocumentTemporaryReferenceKind
import com.entio.core.SemanticDescriptorKind

public data class DocumentGroundedVerificationInput(
    val workKey: DocumentAnalysisWorkKey,
    val candidates: List<DocumentGroundedCandidate>,
    val retrieval: List<DocumentOntologyRetrievalResult>,
    val fullStateMatches: List<DocumentFullStateMatch>,
    val analysis: DocumentGroundedAnalysisResult,
    val expectedOntologyFingerprint: String,
    val currentOntologyFingerprint: String,
    val expectedCurrentWorkFingerprint: String,
    val currentCurrentWorkFingerprint: String,
    val reviewerAuthorizedReuseItemIds: Set<String> = emptySet(),
)

public data class DocumentVerifiedGroundedAnalysis(
    val plan: DocumentSemanticPlan,
    val editableFields: List<DocumentEditableGroundedField>,
    val statusByItemId: Map<String, DocumentGroundedRecommendationStatus>,
    val alignedEntities: Map<String, DocumentCompilerEntity>,
    val itemAlignmentIds: Map<String, String>,
    val verifiedAnalysis: DocumentGroundedAnalysisResult,
    val suggestedSuperclassSelectionIdsByItemId: Map<String, String>,
)

/** Validates frozen grounded choices and converts only explicit meaning into the existing semantic plan. */
public class DocumentGroundedAnalysisVerifier {
    public fun verify(input: DocumentGroundedVerificationInput): DocumentVerifiedGroundedAnalysis {
        require(input.expectedOntologyFingerprint == input.currentOntologyFingerprint) { "stale-ontology" }
        require(input.expectedCurrentWorkFingerprint == input.currentCurrentWorkFingerprint) { "stale-current-work" }
        val candidates = input.candidates.associateBy(DocumentGroundedCandidate::id)
        require(input.analysis.coverage.map { it.candidateId }.toSet() == candidates.keys)
        val retrieval = input.retrieval.associateBy(DocumentOntologyRetrievalResult::candidateId)
        require(retrieval.keys == candidates.keys && input.retrieval.all(DocumentOntologyRetrievalResult::completeAuthorizedScopeSearch))
        val selectionById = input.retrieval.flatMap { it.selections }.associateBy(DocumentOntologyRetrievalSelection::selectionId)
        require(input.reviewerAuthorizedReuseItemIds.all { itemId ->
            input.analysis.items.any { item ->
                item.id == itemId && item.disposition == DocumentGroundedDisposition.ReuseExisting
            }
        }) { "grounded-reviewer-reuse-authorization-invalid" }
        val connectedAnalysis = retainOnlyConnectedRelationshipItems(input.analysis, candidates)
        val normalizedAnalysis = requireExactIdentityForReuse(
            connectedAnalysis,
            candidates,
            selectionById,
            input.fullStateMatches,
            input.reviewerAuthorizedReuseItemIds,
        )
        val exactReuseAnalysis = consolidateExactReuseItems(
            normalizedAnalysis,
            candidates,
            selectionById,
            input.fullStateMatches,
        )
        val analysis = consolidateEquivalentUnresolvedItems(exactReuseAnalysis, candidates)
        val suggestedSuperclassSelectionIds = suggestedSuperclassSelectionIds(
            analysis,
            candidates,
            selectionById.values,
        )
        val fields = mutableListOf<DocumentEditableGroundedField>()
        val statuses = linkedMapOf<String, DocumentGroundedRecommendationStatus>()
        val alignments = linkedMapOf<String, DocumentCompilerEntity>()
        val itemAlignmentIds = linkedMapOf<String, String>()
        val validItems = mutableListOf<DocumentSemanticPlanItem>()
        val explicitlyModeledDefinitionTargets = analysis.items
            .filter { it.kind == DocumentSemanticItemKind.Definition }
            .flatMap { definition ->
                definition.references.filter { it.role == DocumentSemanticReferenceRole.Entity }
                    .map { it.targetItemId }
            }
            .toSet()

        analysis.items.forEach { grounded ->
            require(grounded.candidateIds.all(candidates::containsKey))
            val candidateEvidence = grounded.candidateIds.flatMap { candidates.getValue(it).evidenceSpans }
                .map { it.evidenceId }.toSet()
            require(grounded.evidenceIds.all(candidateEvidence::contains)) { "grounded-evidence-invalid" }
            val selection = grounded.selectionId?.let { selectionId ->
                selectionById[selectionId]?.also { selected ->
                    require(selected.candidateId in grounded.candidateIds) { "grounded-selection-candidate-invalid" }
                    require(kindCompatible(grounded.kind, selected.kind)) { "grounded-selection-kind-invalid" }
                } ?: throw IllegalArgumentException("grounded-selection-invented")
            }
            if (grounded.disposition in setOf(
                    DocumentGroundedDisposition.Administrative,
                    DocumentGroundedDisposition.Illustrative,
                )
            ) {
                statuses[grounded.id] = DocumentGroundedRecommendationStatus.ReviewOnly
                return@forEach
            }
            var needsInput = false
            if (grounded.disposition == DocumentGroundedDisposition.ExtendExisting && selection?.writable != true) {
                fields += field(grounded.id, DocumentEditableGroundedFieldKind.Source, "Choose a writable extension target.")
                needsInput = true
            }
            if (grounded.disposition == DocumentGroundedDisposition.ProposeNew &&
                grounded.kind.declarationKind != null && input.fullStateMatches.any {
                    it.candidateId in grounded.candidateIds && it.exactIdentity
                }) {
                fields += field(
                    grounded.id,
                    DocumentEditableGroundedFieldKind.Selection,
                    "Review the existing exact match before creating another entity.",
                    input.retrieval
                        .filter { it.candidateId in grounded.candidateIds }
                        .flatMap { it.selections }
                        .filter { kindCompatible(grounded.kind, it.kind) }
                        .map(DocumentOntologyRetrievalSelection::selectionId),
                )
                needsInput = true
            }
            if (grounded.disposition == DocumentGroundedDisposition.Unresolved) {
                val compatibleSelections = input.retrieval
                    .filter { it.candidateId in grounded.candidateIds }
                    .flatMap { it.selections }
                    .filter { kindCompatible(grounded.kind, it.kind) }
                    .map(DocumentOntologyRetrievalSelection::selectionId)
                fields += field(
                    grounded.id,
                    DocumentEditableGroundedFieldKind.Disposition,
                    "Choose whether to reuse an authorized match or propose this evidence-backed meaning as new.",
                )
                fields += field(
                    grounded.id,
                    DocumentEditableGroundedFieldKind.EntityKind,
                    "Confirm the supported ontology entity kind.",
                )
                fields += field(
                    grounded.id,
                    DocumentEditableGroundedFieldKind.Label,
                    "Confirm or edit the ontology label.",
                )
                if (compatibleSelections.isNotEmpty()) {
                    fields += field(
                        grounded.id,
                        DocumentEditableGroundedFieldKind.Selection,
                        "Choose a compatible server-issued ontology match, or explicitly propose a new entity.",
                        compatibleSelections,
                    )
                }
                needsInput = true
            }
            val outcome = when (grounded.disposition) {
                DocumentGroundedDisposition.Unresolved -> DocumentSemanticOutcome.Blocked
                DocumentGroundedDisposition.Administrative,
                DocumentGroundedDisposition.Illustrative,
                DocumentGroundedDisposition.ReuseExisting,
                -> DocumentSemanticOutcome.ReviewOnly
                DocumentGroundedDisposition.ExtendExisting -> if (needsInput) {
                    DocumentSemanticOutcome.Blocked
                } else {
                    DocumentSemanticOutcome.ReviewOnly
                }
                DocumentGroundedDisposition.ProposeNew -> if (needsInput) {
                    DocumentSemanticOutcome.Blocked
                } else if (grounded.kind in DocumentSemanticPatternRegistry.reviewOnlyKinds) {
                    DocumentSemanticOutcome.ReviewOnly
                } else {
                    DocumentSemanticOutcome.Executable
                }
            }
            val semantic = runCatching {
                DocumentSemanticPlanItem(
                    id = grounded.id,
                    kind = grounded.kind,
                    label = grounded.label,
                    definition = grounded.definition,
                    literalValue = grounded.literalValue,
                    datatypeIntent = grounded.datatypeIntent,
                    references = grounded.references.map { reference ->
                        DocumentSemanticReference(
                            reference.role,
                            DocumentSemanticReferenceTarget.SemanticItem(reference.targetItemId),
                        )
                    }.sortedBy(DocumentSemanticReference::stableOrderingKey),
                    discoveryIds = grounded.candidateIds.sorted(),
                    evidenceIds = grounded.evidenceIds,
                    rationale = grounded.rationale,
                    outcome = outcome,
                    ambiguity = grounded.ambiguity,
                    confidence = grounded.confidence,
                    modelRecommended = grounded.references.any {
                        it.prerequisiteOrigin == DocumentPrerequisiteOrigin.ModelRecommended
                    },
                    reviewerInputRequired = needsInput,
                )
            }.getOrElse {
                fields += field(grounded.id, missingFieldKind(grounded.kind), "Complete the required connected semantic role.")
                statuses[grounded.id] = DocumentGroundedRecommendationStatus.NeedsInput
                return@forEach
            }
            selection?.let {
                val alignmentId = it.selectionId
                alignments[alignmentId] = DocumentCompilerEntity(
                    it.canonicalIri,
                    it.kind.temporaryKind,
                    it.sourceId,
                    it.writable,
                )
                itemAlignmentIds[grounded.id] = alignmentId
            }
            validItems += semantic
            if (!needsInput && shouldCompileAttachedDefinition(
                    grounded,
                    selection,
                    explicitlyModeledDefinitionTargets,
                )
            ) {
                val definition = requireNotNull(grounded.definition)
                validItems += DocumentSemanticPlanItem(
                    id = "${grounded.id}:grounded-definition",
                    kind = DocumentSemanticItemKind.Definition,
                    label = "Definition of ${grounded.label}".take(500),
                    definition = definition,
                    references = listOf(
                        DocumentSemanticReference(
                            DocumentSemanticReferenceRole.Entity,
                            DocumentSemanticReferenceTarget.SemanticItem(grounded.id),
                        ),
                    ),
                    discoveryIds = grounded.candidateIds,
                    evidenceIds = grounded.evidenceIds,
                    rationale = "The grounded declaration supplies explicit definition meaning.",
                    outcome = DocumentSemanticOutcome.Executable,
                    confidence = grounded.confidence,
                )
            }
            statuses[grounded.id] = when {
                needsInput -> DocumentGroundedRecommendationStatus.NeedsInput
                outcome == DocumentSemanticOutcome.Executable -> DocumentGroundedRecommendationStatus.Executable
                outcome == DocumentSemanticOutcome.ReviewOnly -> DocumentGroundedRecommendationStatus.ReviewOnly
                else -> DocumentGroundedRecommendationStatus.Blocked
            }
        }

        val itemIds = validItems.map(DocumentSemanticPlanItem::id).toSet()
        val closedItems = validItems.filter { it.referencedItemIds.all(itemIds::contains) }
        validItems.filterNot(closedItems::contains).forEach {
            fields += field(it.id, DocumentEditableGroundedFieldKind.Prerequisite, "Supply the missing connected prerequisite.")
            statuses[it.id] = DocumentGroundedRecommendationStatus.NeedsInput
        }
        val groups = components(closedItems).map { component ->
            val outcome = when {
                component.any { it.outcome == DocumentSemanticOutcome.Blocked } -> DocumentSemanticOutcome.Blocked
                component.all { it.outcome == DocumentSemanticOutcome.ReviewOnly } -> DocumentSemanticOutcome.ReviewOnly
                else -> DocumentSemanticOutcome.Executable
            }
            // Connected groups should be named for the ontology edit the reviewer is
            // resolving, not for a reused class that only supplies its domain, range,
            // or type. This also keeps the recommendation identity stable when Kotlin
            // recompiles reviewer-provided connected context.
            val first = component.firstOrNull {
                it.outcome == DocumentSemanticOutcome.Executable && it.kind.declarationKind != null
            } ?: component.firstOrNull {
                it.kind.declarationKind != null
            } ?: component.firstOrNull {
                it.outcome == DocumentSemanticOutcome.Executable
            } ?: component.first()
            DocumentSemanticRecommendationGroup(
                id = "grounded-group-${first.id}",
                title = first.label,
                description = "Connected evidence-grounded ontology meaning.",
                itemIds = component.map { it.id }.sorted(),
                reviewOnlyItemIds = component.filter { it.outcome == DocumentSemanticOutcome.ReviewOnly }.map { it.id }.sorted(),
                discoveryIds = component.flatMap { it.discoveryIds }.distinct().sorted(),
                evidenceIds = component.flatMap { it.evidenceIds }.distinct().sortedBy { it.value },
                outcome = outcome,
                rationale = first.rationale,
                confidence = component.map { it.confidence }.reduce { left, right ->
                    com.entio.core.DocumentConfidenceDimensions(
                        minOf(left.evidence, right.evidence),
                        minOf(left.modeling, right.modeling),
                        minOf(left.ontologyFit, right.ontologyFit),
                    )
                },
            )
        }.sortedBy(DocumentSemanticRecommendationGroup::stableOrderingKey)
        val sortedItems = closedItems.sortedBy(DocumentSemanticPlanItem::stableOrderingKey)
        val plan = DocumentSemanticPlan(
            input.workKey,
            candidates.keys.sorted(),
            emptyList(),
            sortedItems,
            groups,
        )
        return DocumentVerifiedGroundedAnalysis(
            plan = plan,
            editableFields = fields.distinctBy { it.id }.sortedBy { it.id },
            statusByItemId = statuses.toSortedMap(),
            alignedEntities = alignments.toSortedMap(),
            itemAlignmentIds = itemAlignmentIds.toSortedMap(),
            verifiedAnalysis = analysis,
            suggestedSuperclassSelectionIdsByItemId = suggestedSuperclassSelectionIds,
        )
    }

    private fun consolidateExactReuseItems(
        analysis: DocumentGroundedAnalysisResult,
        candidates: Map<String, DocumentGroundedCandidate>,
        selections: Map<String, DocumentOntologyRetrievalSelection>,
        fullStateMatches: List<DocumentFullStateMatch>,
    ): DocumentGroundedAnalysisResult {
        val referencedItemIds = analysis.items.flatMap { item -> item.references.map { it.targetItemId } }.toSet()
        val eligible = analysis.items.mapNotNull { item ->
            if (item.disposition != DocumentGroundedDisposition.ReuseExisting ||
                item.references.isNotEmpty() || item.id in referencedItemIds
            ) {
                return@mapNotNull null
            }
            val selection = item.selectionId?.let(selections::get) ?: return@mapNotNull null
            if (!item.candidateIds.all { candidateId ->
                    isExactIdentity(
                        candidates.getValue(candidateId),
                        selection,
                        fullStateMatches,
                    )
                }
            ) {
                return@mapNotNull null
            }
            ExactReuseKey(
                item.kind,
                selection.canonicalIri.value,
                selection.scope,
                selection.sourceId,
            ) to item
        }
        val grouped = eligible.groupBy({ it.first }, { it.second })
        val replacementIds = mutableMapOf<String, String>()
        val consolidated = grouped.values.mapNotNull { group ->
            if (group.size < 2) return@mapNotNull null
            val canonical = group.minBy { it.id }
            group.forEach { replacementIds[it.id] = canonical.id }
            val selection = canonical.selectionId?.let(selections::get)
            canonical.copy(
                label = selection?.preferredLabel ?: canonical.label,
                candidateIds = group.flatMap { it.candidateIds }.distinct().sorted(),
                evidenceIds = group.flatMap { it.evidenceIds }.distinct().sortedBy { it.value },
                confidence = group.map { it.confidence }.reduce(::minimumConfidence),
            )
        }
        if (replacementIds.isEmpty()) return analysis
        val replacedItemIds = replacementIds.keys
        return DocumentGroundedAnalysisResult(
            analysis.responseVersion,
            (analysis.items.filterNot { it.id in replacedItemIds } + consolidated)
                .sortedBy(DocumentGroundedSemanticItem::stableOrderingKey),
            analysis.coverage.map { coverage ->
                val replacementId = coverage.itemId?.let(replacementIds::get)
                if (replacementId == null) coverage else coverage.copy(itemId = replacementId)
            }.sortedBy { it.stableOrderingKey },
        )
    }

    private fun requireExactIdentityForReuse(
        analysis: DocumentGroundedAnalysisResult,
        candidates: Map<String, DocumentGroundedCandidate>,
        selections: Map<String, DocumentOntologyRetrievalSelection>,
        fullStateMatches: List<DocumentFullStateMatch>,
        reviewerAuthorizedReuseItemIds: Set<String>,
    ): DocumentGroundedAnalysisResult {
        val normalizedItemIds = mutableSetOf<String>()
        val items = analysis.items.map { item ->
            if (item.disposition != DocumentGroundedDisposition.ReuseExisting ||
                item.id in reviewerAuthorizedReuseItemIds
            ) {
                return@map item
            }
            val selection = item.selectionId?.let(selections::get) ?: return@map item
            val exactForEveryCandidate = item.candidateIds.all { candidateId ->
                isExactIdentity(
                    candidates.getValue(candidateId),
                    selection,
                    fullStateMatches,
                )
            }
            if (exactForEveryCandidate) return@map item

            normalizedItemIds += item.id
            val evidenceLabel = item.candidateIds
                .map(candidates::getValue)
                .maxWithOrNull(
                    compareBy<DocumentGroundedCandidate> { it.normalizedText.split(' ').size }
                        .thenBy { it.normalizedText.length }
                        .thenBy(DocumentGroundedCandidate::stableOrderingKey),
                )
                ?.displayText
                ?: item.label
            item.copy(
                label = evidenceLabel,
                disposition = DocumentGroundedDisposition.Unresolved,
                selectionId = null,
                rationale = "${item.rationale} The proposed reuse was not an exact identity match and requires reviewer resolution.",
                ambiguity = BROADER_REUSE_AMBIGUITY,
            )
        }
        if (normalizedItemIds.isEmpty()) return analysis
        return DocumentGroundedAnalysisResult(
            analysis.responseVersion,
            items.sortedBy(DocumentGroundedSemanticItem::stableOrderingKey),
            analysis.coverage.map { coverage ->
                if (coverage.itemId !in normalizedItemIds) coverage else coverage.copy(
                    disposition = DocumentGroundedDisposition.Unresolved,
                    rationale = "The proposed reuse was not an exact identity match and requires reviewer resolution.",
                )
            }.sortedBy { it.stableOrderingKey },
        )
    }

    private fun retainOnlyConnectedRelationshipItems(
        analysis: DocumentGroundedAnalysisResult,
        candidates: Map<String, DocumentGroundedCandidate>,
    ): DocumentGroundedAnalysisResult {
        val referencedItemIds = analysis.items.flatMap { item -> item.references.map { it.targetItemId } }.toSet()
        val documentOnlyItemIds = analysis.items.filterTo(mutableListOf()) { item ->
            item.kind == DocumentSemanticItemKind.ObjectProperty &&
                item.disposition in setOf(
                    DocumentGroundedDisposition.ProposeNew,
                    DocumentGroundedDisposition.Unresolved,
                ) &&
                item.references.isEmpty() &&
                item.id !in referencedItemIds &&
                item.candidateIds.all { candidateId ->
                    candidates.getValue(candidateId).category == DocumentCandidateExtractionCategory.RelationshipPhrase
                }
        }.mapTo(mutableSetOf(), DocumentGroundedSemanticItem::id)
        if (documentOnlyItemIds.isEmpty()) return analysis
        val rationale = "The relationship phrase has no connected subject or object semantic item and remains document-only evidence."
        return DocumentGroundedAnalysisResult(
            analysis.responseVersion,
            analysis.items.map { item ->
                if (item.id !in documentOnlyItemIds) item else item.copy(
                    disposition = DocumentGroundedDisposition.Administrative,
                    selectionId = null,
                    rationale = rationale,
                    ambiguity = null,
                )
            }.sortedBy(DocumentGroundedSemanticItem::stableOrderingKey),
            analysis.coverage.map { coverage ->
                if (coverage.itemId !in documentOnlyItemIds) coverage else coverage.copy(
                    disposition = DocumentGroundedDisposition.Administrative,
                    rationale = rationale,
                )
            }.sortedBy(DocumentGroundedCoverageDisposition::stableOrderingKey),
        )
    }

    private fun consolidateEquivalentUnresolvedItems(
        analysis: DocumentGroundedAnalysisResult,
        candidates: Map<String, DocumentGroundedCandidate>,
    ): DocumentGroundedAnalysisResult {
        val referencedItemIds = analysis.items.flatMap { item -> item.references.map { it.targetItemId } }.toSet()
        val eligible = analysis.items.mapNotNull { item ->
            if (item.disposition != DocumentGroundedDisposition.Unresolved ||
                item.references.isNotEmpty() || item.id in referencedItemIds
            ) {
                return@mapNotNull null
            }
            val normalizedMeaning = item.candidateIds
                .map { candidateId -> candidates.getValue(candidateId).normalizedText }
                .distinct()
                .singleOrNull()
                ?: return@mapNotNull null
            if (normalizeIdentity(item.label) != normalizedMeaning) return@mapNotNull null
            EquivalentUnresolvedKey(item.kind, normalizedMeaning) to item
        }
        val replacementIds = mutableMapOf<String, String>()
        val consolidated = eligible.groupBy({ it.first }, { it.second }).values.mapNotNull { group ->
            if (group.size < 2) return@mapNotNull null
            val canonical = group.minBy(DocumentGroundedSemanticItem::id)
            group.forEach { replacementIds[it.id] = canonical.id }
            canonical.copy(
                candidateIds = group.flatMap(DocumentGroundedSemanticItem::candidateIds).distinct().sorted(),
                evidenceIds = group.flatMap(DocumentGroundedSemanticItem::evidenceIds)
                    .distinct().sortedBy { it.value },
                confidence = group.map(DocumentGroundedSemanticItem::confidence).reduce(::minimumConfidence),
            )
        }
        if (replacementIds.isEmpty()) return analysis
        return DocumentGroundedAnalysisResult(
            analysis.responseVersion,
            (analysis.items.filterNot { it.id in replacementIds } + consolidated)
                .sortedBy(DocumentGroundedSemanticItem::stableOrderingKey),
            analysis.coverage.map { coverage ->
                val replacementId = coverage.itemId?.let(replacementIds::get)
                if (replacementId == null) coverage else coverage.copy(itemId = replacementId)
            }.sortedBy(DocumentGroundedCoverageDisposition::stableOrderingKey),
        )
    }

    private fun isExactIdentity(
        candidate: DocumentGroundedCandidate,
        selection: DocumentOntologyRetrievalSelection,
        fullStateMatches: List<DocumentFullStateMatch>,
    ): Boolean {
        val completeStateExact = fullStateMatches.any { match ->
            match.candidateId == candidate.id &&
                match.exactIdentity &&
                match.canonicalIri == selection.canonicalIri &&
                match.scope == selection.scope &&
                match.sourceId == selection.sourceId
        }
        if (completeStateExact) return true
        val normalizedLabels = listOfNotNull(selection.preferredLabel)
            .plus(selection.alternateLabels)
            .map(::normalizeIdentity)
            .toSet()
        return normalizeIdentity(candidate.normalizedText) in normalizedLabels
    }

    private fun suggestedSuperclassSelectionIds(
        analysis: DocumentGroundedAnalysisResult,
        candidates: Map<String, DocumentGroundedCandidate>,
        selections: Collection<DocumentOntologyRetrievalSelection>,
    ): Map<String, String> = analysis.items.mapNotNull { item ->
        if (item.disposition != DocumentGroundedDisposition.Unresolved ||
            item.kind != DocumentSemanticItemKind.Class ||
            item.ambiguity != BROADER_REUSE_AMBIGUITY
        ) {
            return@mapNotNull null
        }
        val selection = selections.asSequence()
            .filter { it.candidateId in item.candidateIds && it.kind == SemanticDescriptorKind.Class }
            .filter { selected ->
                val candidateMeaning = candidates.getValue(selected.candidateId).normalizedText
                listOfNotNull(selected.preferredLabel).plus(selected.alternateLabels).any { label ->
                    val normalizedLabel = normalizeIdentity(label)
                    candidateMeaning != normalizedLabel && candidateMeaning.endsWith(" $normalizedLabel")
                }
            }
            .sortedWith(compareByDescending<DocumentOntologyRetrievalSelection> { it.score }
                .thenBy(DocumentOntologyRetrievalSelection::stableOrderingKey))
            .firstOrNull()
            ?: return@mapNotNull null
        item.id to selection.selectionId
    }.toMap().toSortedMap()

    private fun normalizeIdentity(value: String): String = value
        .trim()
        .replace(Regex("\\s+"), " ")
        .lowercase()

    private fun minimumConfidence(
        left: DocumentConfidenceDimensions,
        right: DocumentConfidenceDimensions,
    ): DocumentConfidenceDimensions = DocumentConfidenceDimensions(
        minOf(left.evidence, right.evidence),
        minOf(left.modeling, right.modeling),
        minOf(left.ontologyFit, right.ontologyFit),
    )

    private data class ExactReuseKey(
        val kind: DocumentSemanticItemKind,
        val canonicalIri: String,
        val scope: DocumentMatchScope,
        val sourceId: String,
    )

    private data class EquivalentUnresolvedKey(
        val kind: DocumentSemanticItemKind,
        val normalizedMeaning: String,
    )

    private companion object {
        const val BROADER_REUSE_AMBIGUITY: String =
            "The selected ontology entity is broader than the evidence-backed candidate meaning."
    }

    private fun components(items: List<DocumentSemanticPlanItem>): List<List<DocumentSemanticPlanItem>> {
        val byId = items.associateBy(DocumentSemanticPlanItem::id)
        val remaining = byId.keys.toMutableSet()
        val result = mutableListOf<List<DocumentSemanticPlanItem>>()
        while (remaining.isNotEmpty()) {
            val pending = ArrayDeque(listOf(remaining.min()))
            val ids = linkedSetOf<String>()
            while (pending.isNotEmpty()) {
                val id = pending.removeFirst()
                if (!ids.add(id)) continue
                val related = byId.getValue(id).referencedItemIds + items.filter { id in it.referencedItemIds }.map { it.id }
                related.filter(remaining::contains).sorted().forEach(pending::addLast)
            }
            remaining.removeAll(ids)
            result += ids.map(byId::getValue).sortedBy(DocumentSemanticPlanItem::stableOrderingKey)
        }
        return result
    }

    private fun field(
        itemId: String,
        kind: DocumentEditableGroundedFieldKind,
        message: String,
        compatibleSelectionIds: List<String> = emptyList(),
    ) = DocumentEditableGroundedField(
        "$itemId:${kind.name.lowercase()}",
        kind,
        true,
        compatibleSelectionIds.distinct().sorted(),
        message,
    )

    private fun missingFieldKind(kind: DocumentSemanticItemKind): DocumentEditableGroundedFieldKind = when (kind) {
        DocumentSemanticItemKind.ObjectPropertyDomain, DocumentSemanticItemKind.DatatypePropertyDomain -> DocumentEditableGroundedFieldKind.Domain
        DocumentSemanticItemKind.ObjectPropertyRange -> DocumentEditableGroundedFieldKind.Range
        DocumentSemanticItemKind.DatatypePropertyRange -> DocumentEditableGroundedFieldKind.Datatype
        DocumentSemanticItemKind.IndividualType -> DocumentEditableGroundedFieldKind.Type
        else -> DocumentEditableGroundedFieldKind.Prerequisite
    }

    private fun kindCompatible(kind: DocumentSemanticItemKind, selected: SemanticDescriptorKind): Boolean =
        kind.declarationKind == selected.temporaryKind

    private fun shouldCompileAttachedDefinition(
        item: com.entio.core.DocumentGroundedSemanticItem,
        selection: DocumentOntologyRetrievalSelection?,
        explicitlyModeledDefinitionTargets: Set<String>,
    ): Boolean {
        val definition = item.definition ?: return false
        if (item.id in explicitlyModeledDefinitionTargets || item.kind.declarationKind == null) return false
        return when (item.disposition) {
            DocumentGroundedDisposition.ProposeNew -> true
            DocumentGroundedDisposition.ExtendExisting ->
                normalizeDefinition(definition) != normalizeDefinition(selection?.definition)
            else -> false
        }
    }

    private fun normalizeDefinition(value: String?): String? = value
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.lowercase()

    private val SemanticDescriptorKind.temporaryKind: DocumentTemporaryReferenceKind
        get() = when (this) {
            SemanticDescriptorKind.Class -> DocumentTemporaryReferenceKind.Class
            SemanticDescriptorKind.ObjectProperty -> DocumentTemporaryReferenceKind.ObjectProperty
            SemanticDescriptorKind.DatatypeProperty -> DocumentTemporaryReferenceKind.DatatypeProperty
            SemanticDescriptorKind.AnnotationProperty -> DocumentTemporaryReferenceKind.AnnotationProperty
            SemanticDescriptorKind.Individual -> DocumentTemporaryReferenceKind.Individual
        }

    private val DocumentSemanticItemKind.declarationKind: DocumentTemporaryReferenceKind?
        get() = when (this) {
            DocumentSemanticItemKind.Class -> DocumentTemporaryReferenceKind.Class
            DocumentSemanticItemKind.ObjectProperty -> DocumentTemporaryReferenceKind.ObjectProperty
            DocumentSemanticItemKind.DatatypeProperty -> DocumentTemporaryReferenceKind.DatatypeProperty
            DocumentSemanticItemKind.AnnotationProperty -> DocumentTemporaryReferenceKind.AnnotationProperty
            DocumentSemanticItemKind.Individual -> DocumentTemporaryReferenceKind.Individual
            DocumentSemanticItemKind.NodeShape, DocumentSemanticItemKind.PropertyShape -> DocumentTemporaryReferenceKind.Shape
            else -> null
        }
}
