package com.entio.semantic

import com.entio.core.DocumentFinalPlan
import com.entio.core.DocumentFinalRecommendation
import com.entio.core.DocumentFinalRecommendationStatus
import com.entio.core.DocumentPlanOperand
import com.entio.core.DocumentPlanOperation
import com.entio.core.DocumentPlanOperationKind
import com.entio.core.DocumentTemporaryReference
import com.entio.core.DocumentTemporaryReferenceKind
import com.entio.core.Iri

public data class DocumentPlanVerificationContext(
    val expectedOntologyFingerprint: String,
    val currentOntologyFingerprint: String,
    val expectedCurrentWorkFingerprint: String,
    val currentWorkFingerprint: String,
    val writableSourceIds: Set<String>,
    val existingEntityKinds: Map<Iri, DocumentTemporaryReferenceKind>,
    val iriNamespace: String,
) {
    init {
        require(iriNamespace.isNotBlank())
    }
}

public data class DocumentRecommendationImpactPreview(
    val recommendationId: String,
    val semanticDiffSummary: String,
    val validationSummary: String,
    val reasoningSummary: String,
    val shaclSummary: String,
)

public data class DocumentVerifiedFinalPlan(
    val plan: DocumentFinalPlan,
    val finalIris: Map<DocumentTemporaryReference, Iri>,
    val previews: List<DocumentRecommendationImpactPreview>,
)

/**
 * Deterministically checks provider-planned references and supported operation
 * shapes before any plan can reach draft preparation.
 */
public class DocumentChangeSetPlanVerifier {
    public fun verify(
        plan: DocumentFinalPlan,
        context: DocumentPlanVerificationContext,
    ): DocumentVerifiedFinalPlan {
        require(context.expectedOntologyFingerprint == context.currentOntologyFingerprint) {
            "The ontology fingerprint is stale."
        }
        require(context.expectedCurrentWorkFingerprint == context.currentWorkFingerprint) {
            "The current-work fingerprint is stale."
        }
        val usedIris = context.existingEntityKinds.keys.map(Iri::value).toMutableSet()
        val finalIris = linkedMapOf<DocumentTemporaryReference, Iri>()
        val recommendations = plan.recommendations.map { recommendation ->
            if (recommendation.status == DocumentFinalRecommendationStatus.Blocked) {
                recommendation
            } else {
                try {
                    verifyRecommendation(recommendation, context)
                    val recommendationIris = generateFinalIris(recommendation, context, usedIris.toMutableSet())
                    usedIris += recommendationIris.values.map(Iri::value)
                    finalIris += recommendationIris
                    recommendation
                } catch (failure: IllegalArgumentException) {
                    recommendation.copy(
                        status = DocumentFinalRecommendationStatus.Blocked,
                        blockers = (recommendation.blockers + verificationBlocker(failure)).distinct().sorted(),
                    )
                }
            }
        }.sortedBy(DocumentFinalRecommendation::stableOrderingKey)
        val verifiedPlan = plan.copy(recommendations = recommendations)
        return DocumentVerifiedFinalPlan(
            plan = verifiedPlan,
            finalIris = finalIris.toMap(),
            previews = verifiedPlan.recommendations.map { recommendation ->
                preview(recommendation)
            },
        )
    }

    private fun preview(recommendation: DocumentFinalRecommendation): DocumentRecommendationImpactPreview =
        DocumentRecommendationImpactPreview(
            recommendationId = recommendation.id,
            semanticDiffSummary =
                "${recommendation.operations.size} ordered operation(s); " +
                    "${recommendation.expandedTypedEditCount} expanded typed edit(s).",
            validationSummary = if (recommendation.status == DocumentFinalRecommendationStatus.Blocked) {
                "Blocked: ${recommendation.blockers.joinToString("; ")}"
            } else {
                "Typed-operation, source, identity, reference, dependency, and bound checks passed."
            },
            reasoningSummary = if (recommendation.status == DocumentFinalRecommendationStatus.Blocked) {
                "Reasoning preview is unavailable until all recommendation blockers are resolved."
            } else {
                "The complete atomic group is ready for the existing proposal reasoning preview."
            },
            shaclSummary = if (recommendation.operations.any { it.kind in SHACL_OPERATIONS }) {
                "Supported typed SHACL operations are present and ready for proposal SHACL preview."
            } else {
                "No SHACL operation is planned."
            },
        )

    private fun verificationBlocker(failure: IllegalArgumentException): String =
        when {
            failure.message?.contains("unwritable", ignoreCase = true) == true -> "unwritable-source"
            failure.message?.contains("collides", ignoreCase = true) == true -> "iri-collision"
            failure.message?.contains("kind", ignoreCase = true) == true -> "operation-kind-invalid"
            failure.message?.contains("SHACL", ignoreCase = true) == true -> "unsupported-shacl-operation"
            else -> "operation-verification-failed"
        }

    public fun excludeOptionalLeaves(
        recommendation: DocumentFinalRecommendation,
        excludedOperationIds: Set<String>,
    ): DocumentFinalRecommendation {
        val operations = recommendation.operations
        val referencedBy = operations.flatMap { operation ->
            operation.dependsOnOperationIds.map { it to operation.id } +
                operation.referencedTemporaryEntities.mapNotNull { reference ->
                    operations.firstOrNull { it.declaration == reference }?.id?.let { it to operation.id }
                }
        }.groupBy({ it.first }, { it.second })
        excludedOperationIds.forEach { id ->
            val operation = operations.singleOrNull { it.id == id }
                ?: throw IllegalArgumentException("Optional-leaf exclusion references an unknown operation.")
            require(operation.optionalLeaf && referencedBy[id].isNullOrEmpty()) {
                "Only an unreferenced optional leaf may be excluded."
            }
        }
        val retained = operations.filterNot { it.id in excludedOperationIds }
            .mapIndexed { index, operation -> operation.copy(order = index) }
        return recommendation.copy(operations = retained)
    }

    public fun dependencyClosure(
        recommendation: DocumentFinalRecommendation,
        operationIds: Set<String>,
    ): Set<String> {
        val byId = recommendation.operations.associateBy(DocumentPlanOperation::id)
        val closure = linkedSetOf<String>()
        fun include(id: String): Unit {
            val operation = byId[id] ?: throw IllegalArgumentException("Split references an unknown operation.")
            if (!closure.add(id)) return
            operation.dependsOnOperationIds.forEach(::include)
            operation.referencedTemporaryEntities.forEach { reference ->
                include(recommendation.operations.single { it.declaration == reference }.id)
            }
        }
        operationIds.forEach(::include)
        return closure
    }

    private fun generateFinalIris(
        recommendation: DocumentFinalRecommendation,
        context: DocumentPlanVerificationContext,
        used: MutableSet<String>,
    ): Map<DocumentTemporaryReference, Iri> {
        return buildMap {
            recommendation.operations
                .mapNotNull(DocumentPlanOperation::declaration)
                .sortedBy(DocumentTemporaryReference::value)
                .forEach { reference ->
                    val separator = if (context.iriNamespace.endsWith('#') || context.iriNamespace.endsWith('/')) "" else "#"
                    val iri = Iri("${context.iriNamespace}$separator${reference.localName}")
                    require(used.add(iri.value)) { "A generated document-plan IRI collides with current work." }
                    put(reference, iri)
                }
        }
    }

    private fun verifyRecommendation(
        recommendation: DocumentFinalRecommendation,
        context: DocumentPlanVerificationContext,
    ): Unit {
        if (recommendation.status == DocumentFinalRecommendationStatus.Blocked) return
        val declaredKinds = recommendation.operations.mapNotNull { operation ->
            operation.declaration?.let { it to it.kind }
        }.toMap()
        recommendation.operations.forEach { operation ->
            verifySource(operation, context)
            verifyKindCompatibility(operation, declaredKinds, context.existingEntityKinds)
            if (operation.kind == DocumentPlanOperationKind.UpdateShaclConstraint) {
                val constraint = operation.operands.filterIsInstance<DocumentPlanOperand.TextValue>().firstOrNull()?.value
                require(constraint in SUPPORTED_SHACL_CONSTRAINTS) {
                    "The document plan uses an unsupported SHACL constraint."
                }
            }
        }
    }

    private fun verifySource(
        operation: DocumentPlanOperation,
        context: DocumentPlanVerificationContext,
    ): Unit {
        operation.operands.filterIsInstance<DocumentPlanOperand.SourceId>().forEach { source ->
            require(source.value in context.writableSourceIds) {
                "The document plan targets an unwritable ontology source."
            }
        }
    }

    private fun verifyKindCompatibility(
        operation: DocumentPlanOperation,
        declaredKinds: Map<DocumentTemporaryReference, DocumentTemporaryReferenceKind>,
        existingKinds: Map<Iri, DocumentTemporaryReferenceKind>,
    ): Unit {
        fun kind(index: Int): DocumentTemporaryReferenceKind? = when (val operand = operation.operands.getOrNull(index)) {
            is DocumentPlanOperand.TemporaryEntity -> declaredKinds[operand.reference]
            is DocumentPlanOperand.ExistingEntity -> existingKinds[operand.iri]
            else -> null
        }
        fun requireKind(index: Int, vararg allowed: DocumentTemporaryReferenceKind): Unit {
            require(kind(index) in allowed.toSet()) { "A document-plan operand has an incompatible entity kind." }
        }
        when (operation.kind) {
            DocumentPlanOperationKind.AddSuperclass,
            DocumentPlanOperationKind.RemoveSuperclass,
            -> {
                requireKind(0, DocumentTemporaryReferenceKind.Class)
                requireKind(1, DocumentTemporaryReferenceKind.Class)
            }
            DocumentPlanOperationKind.SetPropertyDomain,
            DocumentPlanOperationKind.RemovePropertyDomain,
            -> {
                requireKind(
                    0,
                    DocumentTemporaryReferenceKind.ObjectProperty,
                    DocumentTemporaryReferenceKind.DatatypeProperty,
                )
                requireKind(1, DocumentTemporaryReferenceKind.Class)
            }
            DocumentPlanOperationKind.SetPropertyRange,
            DocumentPlanOperationKind.RemovePropertyRange,
            -> requireKind(
                0,
                DocumentTemporaryReferenceKind.ObjectProperty,
                DocumentTemporaryReferenceKind.DatatypeProperty,
            )
            DocumentPlanOperationKind.AssignType -> {
                requireKind(0, DocumentTemporaryReferenceKind.Individual)
                requireKind(1, DocumentTemporaryReferenceKind.Class)
            }
            DocumentPlanOperationKind.AddObjectPropertyAssertion -> {
                requireKind(0, DocumentTemporaryReferenceKind.Individual)
                requireKind(1, DocumentTemporaryReferenceKind.ObjectProperty)
                requireKind(2, DocumentTemporaryReferenceKind.Individual)
            }
            DocumentPlanOperationKind.AddDatatypePropertyAssertion -> {
                requireKind(0, DocumentTemporaryReferenceKind.Individual)
                requireKind(1, DocumentTemporaryReferenceKind.DatatypeProperty)
                require(operation.operands.getOrNull(2) is DocumentPlanOperand.LiteralValue)
            }
            DocumentPlanOperationKind.CreatePropertyShape -> {
                requireKind(0, DocumentTemporaryReferenceKind.Shape)
                requireKind(
                    1,
                    DocumentTemporaryReferenceKind.ObjectProperty,
                    DocumentTemporaryReferenceKind.DatatypeProperty,
                )
            }
            else -> Unit
        }
    }

    private companion object {
        val SHACL_OPERATIONS: Set<DocumentPlanOperationKind> = setOf(
            DocumentPlanOperationKind.CreateNodeShape,
            DocumentPlanOperationKind.CreatePropertyShape,
            DocumentPlanOperationKind.UpdateShaclConstraint,
            DocumentPlanOperationKind.RemoveShaclConstraint,
            DocumentPlanOperationKind.UpdateShapeLabel,
            DocumentPlanOperationKind.DeleteShape,
        )
        val SUPPORTED_SHACL_CONSTRAINTS: Set<String> = setOf(
            "MinCount",
            "MaxCount",
            "Datatype",
            "Class",
            "In",
            "HasValue",
            "MinInclusive",
        )
    }
}
