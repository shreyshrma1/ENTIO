package com.entio.semantic

import com.entio.core.DocumentContentClassification
import com.entio.core.DocumentDiscoveryKind
import com.entio.core.DocumentFinalPlan
import com.entio.core.DocumentFinalRecommendation
import com.entio.core.DocumentFinalRecommendationStatus
import com.entio.core.DocumentIndividualClassification
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
    val discoveryKinds: Map<String, DocumentDiscoveryKind> = emptyMap(),
    val discoveryContentClassifications: Map<String, DocumentContentClassification> = emptyMap(),
    val discoveryIndividualClassifications: Map<String, DocumentIndividualClassification?> = emptyMap(),
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
                try {
                    verifySemanticIntent(recommendation, context)
                    recommendation
                } catch (failure: IllegalArgumentException) {
                    recommendation.copy(
                        blockers = (recommendation.blockers + verificationBlocker(failure)).distinct().sorted(),
                    )
                }
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
            failure.message?.contains("Administrative document metadata", ignoreCase = true) == true ->
                "administrative-metadata-not-executable"
            failure.message?.contains("model-recommended prerequisite", ignoreCase = true) == true ->
                "model-recommended-prerequisite-unattached"
            failure.message?.contains("reusable concept", ignoreCase = true) == true -> "class-evidence-required"
            failure.message?.contains("cannot be represented only as an ontology class", ignoreCase = true) == true ->
                "normative-meaning-modeled-as-class"
            failure.message?.contains("particular production entity", ignoreCase = true) == true ->
                "individual-evidence-required"
            failure.message?.contains("explicit type operation", ignoreCase = true) == true ->
                "individual-type-required"
            failure.message?.contains("source", ignoreCase = true) == true -> "source-required"
            failure.message?.contains("collides", ignoreCase = true) == true -> "iri-collision"
            failure.message?.contains("domain", ignoreCase = true) == true ||
                failure.message?.contains("range", ignoreCase = true) == true -> "property-context-required"
            failure.message?.contains("operand contract", ignoreCase = true) == true -> "operation-operand-invalid"
            failure.message?.contains("connecting operation", ignoreCase = true) == true -> "disconnected-declarations"
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
        verifySemanticIntent(recommendation, context)
        recommendation.operations.forEach { operation ->
            verifySource(operation, context)
            verifyOperandContract(operation)
            verifyKindCompatibility(operation, declaredKinds, context.existingEntityKinds)
            if (operation.kind == DocumentPlanOperationKind.UpdateShaclConstraint) {
                val constraint = operation.operands.filterIsInstance<DocumentPlanOperand.TextValue>().firstOrNull()?.value
                require(constraint in SUPPORTED_SHACL_CONSTRAINTS) {
                    "The document plan uses an unsupported SHACL constraint."
                }
            }
        }
        verifyCreatedPropertyContext(recommendation)
        verifyModelRecommendedPrerequisites(recommendation)
        verifyDeclaredEntitiesAreConnected(recommendation)
    }

    private fun verifySemanticIntent(
        recommendation: DocumentFinalRecommendation,
        context: DocumentPlanVerificationContext,
    ): Unit {
        if (context.discoveryKinds.isEmpty()) return
        val citedKinds = recommendation.discoveryIds.mapNotNull(context.discoveryKinds::get).toSet()
        require(citedKinds.isNotEmpty()) {
            "An executable recommendation requires known discovery context."
        }
        val citedContent = recommendation.discoveryIds
            .mapNotNull(context.discoveryContentClassifications::get)
            .toSet()
        require(citedContent != setOf(DocumentContentClassification.AdministrativeMetadata)) {
            "Administrative document metadata cannot directly produce ontology operations."
        }

        val createdClasses = recommendation.operations.mapNotNull { operation ->
            operation.declaration?.takeIf {
                operation.kind == DocumentPlanOperationKind.CreateClass &&
                    !operation.reviewerInputRequired &&
                    !operation.modelRecommended
            }
        }
        if (createdClasses.isNotEmpty()) {
            val operationalContextEvidence =
                citedKinds.any(NORMATIVE_DISCOVERY_KINDS::contains) &&
                    citedKinds.all(OPERATIONAL_CONTEXT_DISCOVERY_KINDS::contains)
            val createsGenericModelingCategory = createdClasses.any { declaration ->
                normalizedClassLabelTokens(declaration.localName)
                    .joinToString(" ") in GENERIC_MODELING_CLASS_LABELS
            }
            val createsNormativeWrapper = createdClasses.any { declaration ->
                normalizedClassLabelTokens(declaration.localName)
                    .any(NON_ENTITY_CLASS_TOKENS::contains)
            }
            require(!createsGenericModelingCategory) {
                "A class declaration requires evidence for a reusable concept, definition, or role."
            }
            require(
                !createsNormativeWrapper ||
                    recommendation.operations.any { it.kind in SHACL_OPERATIONS },
            ) {
                "A requirement or control cannot be represented only as an ontology class."
            }
            require(
                citedKinds.any(CLASS_SUPPORTING_DISCOVERY_KINDS::contains) ||
                    operationalContextEvidence &&
                    createdClasses.all { looksLikeOperationalClassLabel(it.localName) },
            ) {
                "A class declaration requires evidence for a reusable concept, definition, or role."
            }
        }

        val createdIndividuals = recommendation.operations.mapNotNull { operation ->
            operation.declaration?.takeIf { operation.kind == DocumentPlanOperationKind.CreateIndividual }
        }
        if (createdIndividuals.isNotEmpty()) {
            require(
                recommendation.discoveryIds.any { discoveryId ->
                    context.discoveryKinds[discoveryId] == DocumentDiscoveryKind.Individual &&
                        context.discoveryIndividualClassifications[discoveryId] ==
                        DocumentIndividualClassification.Production
                },
            ) {
                "An individual declaration requires evidence for a particular production entity, not a generic role."
            }
            createdIndividuals.forEach { individual ->
                require(recommendation.operations.any { operation ->
                    operation.kind == DocumentPlanOperationKind.AssignType &&
                        operation.operands.firstOrNull() == DocumentPlanOperand.TemporaryEntity(individual)
                }) {
                    "A newly created individual requires an explicit type operation."
                }
            }
        }
    }

    private fun looksLikeOperationalClassLabel(value: String): Boolean {
        val tokens = normalizedClassLabelTokens(value)
        return tokens.isNotEmpty() &&
            tokens.size <= MAX_OPERATIONAL_CLASS_LABEL_TOKENS &&
            tokens.none(NON_ENTITY_CLASS_TOKENS::contains) &&
            tokens.joinToString(" ") !in GENERIC_MODELING_CLASS_LABELS
    }

    private fun normalizedClassLabelTokens(value: String): List<String> =
        value
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter(String::isNotBlank)
            .map(::canonicalClassToken)

    private fun canonicalClassToken(token: String): String = when {
        token.length > 4 && token.endsWith("ies") -> token.dropLast(3) + "y"
        token.length > 3 && token.endsWith("s") && !token.endsWith("ss") -> token.dropLast(1)
        else -> token
    }

    private fun verifyOperandContract(operation: DocumentPlanOperation): Unit {
        val values = operation.operands.filterNot { it is DocumentPlanOperand.SourceId }
        fun isEntity(value: DocumentPlanOperand): Boolean =
            value is DocumentPlanOperand.ExistingEntity || value is DocumentPlanOperand.TemporaryEntity
        fun requireEntities(count: Int): Unit {
            require(values.size == count && values.all(::isEntity)) {
                "The document-plan operation has an invalid operand contract."
            }
        }
        when (operation.kind) {
            DocumentPlanOperationKind.CreateClass,
            DocumentPlanOperationKind.CreateObjectProperty,
            DocumentPlanOperationKind.CreateDatatypeProperty,
            DocumentPlanOperationKind.CreateAnnotationProperty,
            DocumentPlanOperationKind.CreateIndividual,
            -> require(
                values.size <= 1 &&
                    values.all { value -> value is DocumentPlanOperand.TextValue },
            ) {
                "The declaration operation has an invalid operand contract; it accepts only one optional text label."
            }
            DocumentPlanOperationKind.AddSuperclass,
            DocumentPlanOperationKind.RemoveSuperclass,
            DocumentPlanOperationKind.SetPropertyDomain,
            DocumentPlanOperationKind.RemovePropertyDomain,
            DocumentPlanOperationKind.SetPropertyRange,
            DocumentPlanOperationKind.RemovePropertyRange,
            DocumentPlanOperationKind.AssignType,
            -> requireEntities(2)
            DocumentPlanOperationKind.AddObjectPropertyAssertion -> requireEntities(3)
            else -> Unit
        }
    }

    private fun verifySource(
        operation: DocumentPlanOperation,
        context: DocumentPlanVerificationContext,
    ): Unit {
        val sources = operation.operands.filterIsInstance<DocumentPlanOperand.SourceId>()
        require(sources.size == 1) {
            "Every document-plan operation requires exactly one source."
        }
        require(sources.single().value in context.writableSourceIds) {
            "The document plan targets an unwritable ontology source."
        }
    }

    private fun verifyCreatedPropertyContext(recommendation: DocumentFinalRecommendation): Unit {
        val createdProperties = recommendation.operations.mapNotNull { operation ->
            operation.declaration?.takeIf {
                operation.kind in setOf(
                    DocumentPlanOperationKind.CreateObjectProperty,
                    DocumentPlanOperationKind.CreateDatatypeProperty,
                )
            }
        }
        createdProperties.forEach { property ->
            val propertyOperand = DocumentPlanOperand.TemporaryEntity(property)
            require(recommendation.operations.any { operation ->
                operation.kind == DocumentPlanOperationKind.SetPropertyDomain &&
                    propertyOperand in operation.operands
            }) {
                "A newly created property requires a domain operation."
            }
            require(recommendation.operations.any { operation ->
                operation.kind == DocumentPlanOperationKind.SetPropertyRange &&
                    propertyOperand in operation.operands
            }) {
                "A newly created property requires a range operation."
            }
        }
    }

    private fun verifyModelRecommendedPrerequisites(recommendation: DocumentFinalRecommendation): Unit {
        recommendation.operations.filter { operation ->
            operation.modelRecommended &&
                !operation.reviewerInputRequired &&
                operation.declaration != null
        }.forEach { prerequisite ->
            val declaration = requireNotNull(prerequisite.declaration)
            require(recommendation.operations.any { operation ->
                operation.id != prerequisite.id && declaration in operation.referencedTemporaryEntities
            }) {
                "A model-recommended prerequisite must be attached to the operation it supports."
            }
        }
    }

    private fun verifyDeclaredEntitiesAreConnected(recommendation: DocumentFinalRecommendation): Unit {
        val declarations = recommendation.operations.mapNotNull(DocumentPlanOperation::declaration).toSet()
        if (declarations.size <= 1) return
        val hasConnectingOperation = recommendation.operations.any { operation ->
            (operation.referencedTemporaryEntities + listOfNotNull(operation.declaration))
                .filter(declarations::contains)
                .distinct()
                .size >= 2
        }
        require(hasConnectingOperation) {
            "A recommendation with multiple declarations requires a connecting operation."
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
        val CLASS_SUPPORTING_DISCOVERY_KINDS: Set<DocumentDiscoveryKind> = setOf(
            DocumentDiscoveryKind.Concept,
            DocumentDiscoveryKind.Definition,
            DocumentDiscoveryKind.Role,
        )
        val NORMATIVE_DISCOVERY_KINDS: Set<DocumentDiscoveryKind> = setOf(
            DocumentDiscoveryKind.Requirement,
            DocumentDiscoveryKind.Control,
            DocumentDiscoveryKind.ConditionalRule,
        )
        val OPERATIONAL_CONTEXT_DISCOVERY_KINDS: Set<DocumentDiscoveryKind> =
            NORMATIVE_DISCOVERY_KINDS + setOf(
                DocumentDiscoveryKind.Relationship,
                DocumentDiscoveryKind.Attribute,
                DocumentDiscoveryKind.Value,
            )
        const val MAX_OPERATIONAL_CLASS_LABEL_TOKENS: Int = 4
        val NON_ENTITY_CLASS_TOKENS: Set<String> = setOf(
            "ambiguity",
            "conflict",
            "constraint",
            "control",
            "definition",
            "enforcement",
            "exception",
            "guideline",
            "mandate",
            "management",
            "obligation",
            "policy",
            "procedure",
            "reconciliation",
            "requirement",
            "resolution",
            "retention",
            "review",
            "rule",
            "separation",
            "standard",
            "threshold",
            "timing",
            "validation",
        )
        val GENERIC_MODELING_CLASS_LABELS: Set<String> = setOf(
            "ambiguity",
            "conflict",
            "control",
            "definition",
            "exception",
            "policy",
            "requirement",
            "rule",
            "standard",
        )
        val SUPPORTED_SHACL_CONSTRAINTS: Set<String> = setOf(
            "MinCount",
            "MaxCount",
            "Datatype",
            "Class",
            "MinInclusive",
            "MaxInclusive",
            "Pattern",
        )
    }
}
