package com.entio.semantic

import com.entio.core.DocumentAnalysisWorkKey
import com.entio.core.DocumentEditableGroundedField
import com.entio.core.DocumentEditableGroundedFieldKind
import com.entio.core.DocumentGroundedAnalysisResult
import com.entio.core.DocumentGroundedCandidate
import com.entio.core.DocumentGroundedDisposition
import com.entio.core.DocumentGroundedRecommendationStatus
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
)

public data class DocumentVerifiedGroundedAnalysis(
    val plan: DocumentSemanticPlan,
    val editableFields: List<DocumentEditableGroundedField>,
    val statusByItemId: Map<String, DocumentGroundedRecommendationStatus>,
    val alignedEntities: Map<String, DocumentCompilerEntity>,
    val itemAlignmentIds: Map<String, String>,
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
        val fields = mutableListOf<DocumentEditableGroundedField>()
        val statuses = linkedMapOf<String, DocumentGroundedRecommendationStatus>()
        val alignments = linkedMapOf<String, DocumentCompilerEntity>()
        val itemAlignmentIds = linkedMapOf<String, String>()
        val validItems = mutableListOf<DocumentSemanticPlanItem>()

        input.analysis.items.forEach { grounded ->
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
            if (grounded.disposition == DocumentGroundedDisposition.ExtendExisting && selection?.writable != true) {
                fields += field(grounded.id, DocumentEditableGroundedFieldKind.Source, "Choose a writable extension target.")
                statuses[grounded.id] = DocumentGroundedRecommendationStatus.NeedsInput
                return@forEach
            }
            if (grounded.disposition == DocumentGroundedDisposition.ProposeNew && input.fullStateMatches.any {
                    it.candidateId in grounded.candidateIds && it.exactIdentity
                }) {
                fields += field(grounded.id, DocumentEditableGroundedFieldKind.Selection, "Review the existing exact match before creating another entity.")
                statuses[grounded.id] = DocumentGroundedRecommendationStatus.NeedsInput
                return@forEach
            }
            val outcome = when (grounded.disposition) {
                DocumentGroundedDisposition.Unresolved -> DocumentSemanticOutcome.Blocked
                DocumentGroundedDisposition.Administrative,
                DocumentGroundedDisposition.Illustrative,
                DocumentGroundedDisposition.ReuseExisting,
                -> DocumentSemanticOutcome.ReviewOnly
                DocumentGroundedDisposition.ExtendExisting,
                DocumentGroundedDisposition.ProposeNew,
                -> if (grounded.kind in DocumentSemanticPatternRegistry.reviewOnlyKinds) {
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
                    reviewerInputRequired = outcome == DocumentSemanticOutcome.Blocked,
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
            statuses[grounded.id] = when (outcome) {
                DocumentSemanticOutcome.Executable -> DocumentGroundedRecommendationStatus.Executable
                DocumentSemanticOutcome.ReviewOnly -> DocumentGroundedRecommendationStatus.ReviewOnly
                DocumentSemanticOutcome.Blocked -> DocumentGroundedRecommendationStatus.Blocked
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
            val first = component.first()
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
            plan,
            fields.distinctBy { it.id }.sortedBy { it.id },
            statuses.toSortedMap(),
            alignments.toSortedMap(),
            itemAlignmentIds.toSortedMap(),
        )
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

    private fun field(itemId: String, kind: DocumentEditableGroundedFieldKind, message: String) =
        DocumentEditableGroundedField("field-$itemId-${kind.name.lowercase()}", kind, true, safeMessage = message)

    private fun missingFieldKind(kind: DocumentSemanticItemKind): DocumentEditableGroundedFieldKind = when (kind) {
        DocumentSemanticItemKind.ObjectPropertyDomain, DocumentSemanticItemKind.DatatypePropertyDomain -> DocumentEditableGroundedFieldKind.Domain
        DocumentSemanticItemKind.ObjectPropertyRange -> DocumentEditableGroundedFieldKind.Range
        DocumentSemanticItemKind.DatatypePropertyRange -> DocumentEditableGroundedFieldKind.Datatype
        DocumentSemanticItemKind.IndividualType -> DocumentEditableGroundedFieldKind.Type
        else -> DocumentEditableGroundedFieldKind.Prerequisite
    }

    private fun kindCompatible(kind: DocumentSemanticItemKind, selected: SemanticDescriptorKind): Boolean =
        kind.declarationKind == selected.temporaryKind

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
