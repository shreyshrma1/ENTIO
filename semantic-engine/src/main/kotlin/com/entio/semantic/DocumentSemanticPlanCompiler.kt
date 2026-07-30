package com.entio.semantic

import com.entio.core.DocumentCompilationFailure
import com.entio.core.DocumentCompilationStatus
import com.entio.core.DocumentCompiledConfidenceDimensions
import com.entio.core.DocumentCompiledRecommendationResult
import com.entio.core.DocumentCompiledReference
import com.entio.core.DocumentPlanOperand
import com.entio.core.DocumentPlanOperation
import com.entio.core.DocumentPlanOperationKind
import com.entio.core.DocumentSemanticItemKind
import com.entio.core.DocumentSemanticOutcome
import com.entio.core.DocumentSemanticPlan
import com.entio.core.DocumentSemanticPlanItem
import com.entio.core.DocumentSemanticReferenceRole
import com.entio.core.DocumentSemanticReferenceTarget
import com.entio.core.DocumentTemporaryReference
import com.entio.core.DocumentTemporaryReferenceKind
import com.entio.core.Iri
import com.entio.core.MAX_DOCUMENT_EXPANDED_TYPED_EDITS_PER_RECOMMENDATION
import com.entio.core.MAX_DOCUMENT_EXPANDED_TYPED_EDITS_PER_TASK

public data class DocumentCompilerEntity(
    public val iri: Iri,
    public val kind: DocumentTemporaryReferenceKind,
    public val sourceId: String,
    public val writable: Boolean,
)

public data class DocumentSemanticCompilerContext(
    public val targetSourceId: String,
    public val iriNamespace: String,
    public val existingEntities: Map<Iri, DocumentTemporaryReferenceKind>,
    public val alignedEntities: Map<String, DocumentCompilerEntity>,
    public val itemAlignmentIds: Map<String, String> = emptyMap(),
    public val administrativeDiscoveryIds: Set<String> = emptySet(),
    public val expectedOntologyFingerprint: String = "current",
    public val currentOntologyFingerprint: String = expectedOntologyFingerprint,
    public val expectedCurrentWorkFingerprint: String = "current",
    public val currentWorkFingerprint: String = expectedCurrentWorkFingerprint,
    public val existingOperationKeys: Set<String> = emptySet(),
) {
    init {
        require(targetSourceId.isNotBlank()) { "A semantic compiler target source is required." }
        require(iriNamespace.isNotBlank()) { "A semantic compiler IRI namespace is required." }
        require(itemAlignmentIds.keys.all { it.isNotBlank() } &&
            itemAlignmentIds.values.all(alignedEntities::containsKey)) {
            "Semantic item alignments must resolve exactly."
        }
    }
}

/** The explicit set of semantic item kinds supported by deterministic compilation. */
public object DocumentSemanticPatternRegistry {
    public val executableKinds: Set<DocumentSemanticItemKind> = setOf(
        DocumentSemanticItemKind.Class,
        DocumentSemanticItemKind.ObjectProperty,
        DocumentSemanticItemKind.DatatypeProperty,
        DocumentSemanticItemKind.AnnotationProperty,
        DocumentSemanticItemKind.Individual,
        DocumentSemanticItemKind.SubclassRelationship,
        DocumentSemanticItemKind.ObjectPropertyDomain,
        DocumentSemanticItemKind.ObjectPropertyRange,
        DocumentSemanticItemKind.DatatypePropertyDomain,
        DocumentSemanticItemKind.DatatypePropertyRange,
        DocumentSemanticItemKind.IndividualType,
        DocumentSemanticItemKind.ObjectPropertyAssertion,
        DocumentSemanticItemKind.DatatypeValueAssertion,
        DocumentSemanticItemKind.PreferredLabel,
        DocumentSemanticItemKind.Definition,
        DocumentSemanticItemKind.AlternateLabel,
        DocumentSemanticItemKind.NodeShape,
        DocumentSemanticItemKind.PropertyShape,
        DocumentSemanticItemKind.ShaclConstraint,
    )

    public val reviewOnlyKinds: Set<DocumentSemanticItemKind> =
        setOf(DocumentSemanticItemKind.ComplexRule)

    public val supportedShaclConstraints: Set<String> = setOf(
        "MinCount",
        "MaxCount",
        "Datatype",
        "Class",
        "MinInclusive",
        "MaxInclusive",
        "Pattern",
    )
}

/**
 * Compiles verified semantic meaning into the existing internal operation
 * contract. It does not mutate RDF or ontology sources.
 */
public class DocumentSemanticPlanCompiler {
    public fun compile(
        plan: DocumentSemanticPlan,
        context: DocumentSemanticCompilerContext,
    ): List<DocumentCompiledRecommendationResult> {
        val itemsById = plan.items.associateBy(DocumentSemanticPlanItem::id)
        val freshnessFailure = when {
            context.expectedOntologyFingerprint != context.currentOntologyFingerprint -> "stale-ontology"
            context.expectedCurrentWorkFingerprint != context.currentWorkFingerprint -> "stale-current-work"
            else -> null
        }
        val compiled = plan.groups.map { group ->
            if (freshnessFailure != null) {
                return@map blocked(
                    group.id,
                    group.itemIds.first(),
                    freshnessFailure,
                    "The deterministic compiler context is stale.",
                    group.confidence.evidence,
                    group.confidence.modeling,
                    group.confidence.ontologyFit,
                )
            }
            if (group.outcome == DocumentSemanticOutcome.ReviewOnly) {
                return@map DocumentCompiledRecommendationResult(
                    groupId = group.id,
                    status = DocumentCompilationStatus.ReviewOnly,
                    confidence = confidence(group.confidence.evidence, group.confidence.modeling, group.confidence.ontologyFit),
                )
            }
            if (group.outcome == DocumentSemanticOutcome.Blocked) {
                return@map blocked(
                    group.id,
                    group.itemIds.first(),
                    "semantic-group-blocked",
                    "The semantic group contains unresolved meaning.",
                    group.confidence.evidence,
                    group.confidence.modeling,
                    group.confidence.ontologyFit,
                )
            }
            val items = dependencyClosure(group.itemIds, itemsById)
            try {
                compileGroup(group.id, items, context)
            } catch (failure: CompilationBlocked) {
                blocked(
                    group.id,
                    failure.itemId,
                    failure.code,
                    failure.message.orEmpty(),
                    group.confidence.evidence,
                    group.confidence.modeling,
                    group.confidence.ontologyFit,
                )
            }
        }.flatMap(::splitSafely)
            .sortedBy(DocumentCompiledRecommendationResult::groupId)
        if (compiled.sumOf(DocumentCompiledRecommendationResult::expandedTypedEditCount) >
            MAX_DOCUMENT_EXPANDED_TYPED_EDITS_PER_TASK
        ) {
            return plan.groups.map { group ->
                blocked(
                    group.id,
                    group.itemIds.first(),
                    "task-edit-limit-exceeded",
                    "The compiled task exceeds the approved typed-edit limit.",
                    group.confidence.evidence,
                    group.confidence.modeling,
                    group.confidence.ontologyFit,
                )
            }.sortedBy(DocumentCompiledRecommendationResult::groupId)
        }
        return compiled
    }

    /**
     * A semantic group names its intended work. Explicit item references name
     * the declarations required to compile that work, so include their
     * transitive closure without inferring any additional ontology meaning.
     */
    private fun dependencyClosure(
        seedItemIds: List<String>,
        itemsById: Map<String, DocumentSemanticPlanItem>,
    ): List<DocumentSemanticPlanItem> {
        val pending = ArrayDeque(seedItemIds.sorted())
        val included = linkedSetOf<String>()
        while (pending.isNotEmpty()) {
            val itemId = pending.removeFirst()
            if (!included.add(itemId)) continue
            val item = itemsById[itemId]
                ?: throw CompilationBlocked(itemId, "semantic-reference-unresolved", "A semantic item is unavailable.")
            item.referencedItemIds
                .filterNot(included::contains)
                .sorted()
                .forEach(pending::addLast)
        }
        return included.map(itemsById::getValue)
    }

    private fun compileGroup(
        groupId: String,
        items: List<DocumentSemanticPlanItem>,
        context: DocumentSemanticCompilerContext,
    ): DocumentCompiledRecommendationResult {
        val itemEntities = linkedMapOf<String, DocumentPlanOperand>()
        val references = mutableListOf<DocumentCompiledReference>()
        val operations = mutableListOf<DocumentPlanOperation>()
        val usedIris = context.existingEntities.keys.toMutableSet()

        items.filter { it.kind.declarationKind != null }.forEach { item ->
            if (item.discoveryIds.all(context.administrativeDiscoveryIds::contains)) {
                throw CompilationBlocked(
                    item.id,
                    "administrative-metadata-not-executable",
                    "Administrative metadata cannot be compiled as a business entity.",
                )
            }
            val expectedKind = requireNotNull(item.kind.declarationKind)
            val alignmentId = context.itemAlignmentIds[item.id]
            if (alignmentId != null) {
                val aligned = context.alignedEntities.getValue(alignmentId)
                if (aligned.kind != expectedKind) {
                    throw CompilationBlocked(item.id, "aligned-kind-mismatch", "The aligned entity has an incompatible kind.")
                }
                itemEntities[item.id] = DocumentPlanOperand.ExistingEntity(aligned.iri)
            } else {
                val temporary = DocumentTemporaryReference("new:${expectedKind.token}:${localName(item.label)}")
                val finalIri = Iri("${context.iriNamespace.trimEnd('#', '/')}/${temporary.localName}")
                if (!usedIris.add(finalIri)) {
                    throw CompilationBlocked(item.id, "duplicate-entity", "The generated entity already exists.")
                }
                itemEntities[item.id] = DocumentPlanOperand.TemporaryEntity(temporary)
                references += DocumentCompiledReference(item.id, temporary, finalIri)
            }
        }

        fun resolve(
            item: DocumentSemanticPlanItem,
            role: DocumentSemanticReferenceRole,
            vararg allowedKinds: DocumentTemporaryReferenceKind,
        ): DocumentPlanOperand {
            val reference = item.references.singleOrNull { it.role == role }
                ?: throw CompilationBlocked(item.id, "semantic-reference-missing", "A required semantic reference is missing.")
            val (operand, kind) = when (val target = reference.target) {
                is DocumentSemanticReferenceTarget.SemanticItem -> {
                    val operand = itemEntities[target.itemId]
                        ?: throw CompilationBlocked(item.id, "semantic-reference-unresolved", "A referenced item is not an entity.")
                    operand to items.firstOrNull { it.id == target.itemId }?.kind?.declarationKind
                }
                is DocumentSemanticReferenceTarget.Alignment -> {
                    val aligned = context.alignedEntities[target.alignmentId]
                        ?: throw CompilationBlocked(item.id, "alignment-unresolved", "A verified alignment is unavailable.")
                    DocumentPlanOperand.ExistingEntity(aligned.iri) to aligned.kind
                }
            }
            if (allowedKinds.isNotEmpty() && kind !in allowedKinds) {
                throw CompilationBlocked(
                    item.id,
                    "semantic-reference-kind-mismatch",
                    "A semantic reference has an incompatible entity kind.",
                )
            }
            return operand
        }

        val propertyShapeConstraints = items
            .filter { it.kind == DocumentSemanticItemKind.ShaclConstraint }
            .mapNotNull { constraint ->
                val target = constraint.references.singleOrNull {
                    it.role == DocumentSemanticReferenceRole.ConstraintTarget
                }?.target as? DocumentSemanticReferenceTarget.SemanticItem
                target?.itemId?.let { it to constraint }
            }
            .groupBy({ it.first }, { it.second })
        val consumedConstraintIds = mutableSetOf<String>()

        items.filter { it.kind.declarationKind != null }.forEach { item ->
            val entity = itemEntities.getValue(item.id)
            if (entity is DocumentPlanOperand.ExistingEntity) return@forEach
            val declaration = (entity as DocumentPlanOperand.TemporaryEntity).reference
            val operands = when (item.kind) {
                DocumentSemanticItemKind.NodeShape -> listOf(
                    resolve(item, DocumentSemanticReferenceRole.TargetClass, DocumentTemporaryReferenceKind.Class),
                    DocumentPlanOperand.TextValue(item.label),
                )
                DocumentSemanticItemKind.PropertyShape -> {
                    val constraint = propertyShapeConstraints[item.id]?.singleOrNull()
                        ?: throw CompilationBlocked(
                            item.id,
                            "property-shape-constraint-required",
                            "A property shape requires exactly one supported constraint.",
                        )
                    consumedConstraintIds += constraint.id
                    propertyShapeOperands(item, constraint, ::resolve)
                }
                else -> listOf(DocumentPlanOperand.TextValue(item.label))
            }
            operations += operation(
                item,
                item.kind.declarationOperation!!,
                context,
                declaration = declaration,
                operands = operands,
            )
        }

        items.filter { it.kind.declarationKind == null && it.id !in consumedConstraintIds }.forEach { item ->
            val operands = when (item.kind) {
                DocumentSemanticItemKind.SubclassRelationship ->
                    listOf(
                        resolve(item, DocumentSemanticReferenceRole.Subclass, DocumentTemporaryReferenceKind.Class),
                        resolve(item, DocumentSemanticReferenceRole.Superclass, DocumentTemporaryReferenceKind.Class),
                    )
                DocumentSemanticItemKind.ObjectPropertyDomain,
                DocumentSemanticItemKind.DatatypePropertyDomain,
                -> listOf(
                    resolve(
                        item,
                        DocumentSemanticReferenceRole.Property,
                        DocumentTemporaryReferenceKind.ObjectProperty,
                        DocumentTemporaryReferenceKind.DatatypeProperty,
                    ),
                    resolve(item, DocumentSemanticReferenceRole.Domain, DocumentTemporaryReferenceKind.Class),
                )
                DocumentSemanticItemKind.ObjectPropertyRange -> listOf(
                    resolve(item, DocumentSemanticReferenceRole.Property, DocumentTemporaryReferenceKind.ObjectProperty),
                    resolve(item, DocumentSemanticReferenceRole.Range, DocumentTemporaryReferenceKind.Class),
                )
                DocumentSemanticItemKind.DatatypePropertyRange -> listOf(
                    resolve(item, DocumentSemanticReferenceRole.Property, DocumentTemporaryReferenceKind.DatatypeProperty),
                    resolve(item, DocumentSemanticReferenceRole.Range),
                )
                DocumentSemanticItemKind.IndividualType ->
                    listOf(
                        resolve(item, DocumentSemanticReferenceRole.Individual, DocumentTemporaryReferenceKind.Individual),
                        resolve(item, DocumentSemanticReferenceRole.Type, DocumentTemporaryReferenceKind.Class),
                    )
                DocumentSemanticItemKind.ObjectPropertyAssertion ->
                    listOf(
                        resolve(item, DocumentSemanticReferenceRole.Subject, DocumentTemporaryReferenceKind.Individual),
                        resolve(item, DocumentSemanticReferenceRole.Predicate, DocumentTemporaryReferenceKind.ObjectProperty),
                        resolve(item, DocumentSemanticReferenceRole.Object, DocumentTemporaryReferenceKind.Individual),
                    )
                DocumentSemanticItemKind.DatatypeValueAssertion ->
                    listOf(
                        resolve(item, DocumentSemanticReferenceRole.Subject, DocumentTemporaryReferenceKind.Individual),
                        resolve(item, DocumentSemanticReferenceRole.Predicate, DocumentTemporaryReferenceKind.DatatypeProperty),
                        DocumentPlanOperand.LiteralValue(requireNotNull(item.literalValue)),
                    )
                DocumentSemanticItemKind.PreferredLabel ->
                    listOf(resolve(item, DocumentSemanticReferenceRole.Entity), DocumentPlanOperand.LiteralValue(
                        com.entio.core.RdfLiteral(item.label),
                    ))
                DocumentSemanticItemKind.Definition ->
                    listOf(resolve(item, DocumentSemanticReferenceRole.Entity), DocumentPlanOperand.LiteralValue(
                        com.entio.core.RdfLiteral(item.definition ?: item.label),
                    ))
                DocumentSemanticItemKind.AlternateLabel ->
                    listOf(resolve(item, DocumentSemanticReferenceRole.Entity), DocumentPlanOperand.LiteralValue(
                        com.entio.core.RdfLiteral(item.label),
                    ))
                DocumentSemanticItemKind.ShaclConstraint -> shaclOperands(item, ::resolve)
                DocumentSemanticItemKind.ComplexRule ->
                    throw CompilationBlocked(item.id, "complex-rule-review-only", "Complex rules remain review-only.")
                else -> throw CompilationBlocked(item.id, "semantic-pattern-incomplete",
                    "The semantic item cannot be compiled as a standalone operation.")
            }
            operations += operation(item, item.kind.operationKind!!, context, operands = operands)
        }

        if (operations.isEmpty()) {
            throw CompilationBlocked(items.first().id, "semantic-group-empty", "The semantic group compiled no typed work.")
        }
        val orderedOperations = orderWithDependencies(operations)
        val operationKeys = orderedOperations.map(::operationKey)
        if (operationKeys.size != operationKeys.distinct().size ||
            operationKeys.any(context.existingOperationKeys::contains)
        ) {
            throw CompilationBlocked(
                items.first().id,
                "duplicate-or-no-op",
                "The compiled group duplicates current work or contains a no-op.",
            )
        }
        return DocumentCompiledRecommendationResult(
            groupId = groupId,
            status = DocumentCompilationStatus.Compiled,
            operations = orderedOperations,
            references = references.sortedBy(DocumentCompiledReference::stableOrderingKey),
            confidence = confidence(
                items.minOf { it.confidence.evidence },
                items.minOf { it.confidence.modeling },
                items.minOf { it.confidence.ontologyFit },
                100,
            ),
        )
    }

    private fun orderWithDependencies(operations: List<DocumentPlanOperation>): List<DocumentPlanOperation> {
        val declarationIds = operations.mapNotNull { operation ->
            operation.declaration?.let { it to operation.id }
        }.toMap()
        val withDependencies = operations.map { operation ->
            operation.copy(
                dependsOnOperationIds = (
                    operation.dependsOnOperationIds +
                        operation.referencedTemporaryEntities.mapNotNull(declarationIds::get)
                ).filterNot { it == operation.id }.distinct().sorted(),
            )
        }
        val byId = withDependencies.associateBy(DocumentPlanOperation::id)
        require(byId.size == withDependencies.size) { "Compiled operation IDs must be unique." }
        require(withDependencies.flatMap(DocumentPlanOperation::dependsOnOperationIds).all(byId::containsKey)) {
            "A compiled operation dependency is unresolved."
        }
        val remaining = withDependencies.associate { it.id to it.dependsOnOperationIds.toMutableSet() }.toMutableMap()
        val ordered = mutableListOf<DocumentPlanOperation>()
        while (remaining.isNotEmpty()) {
            val ready = remaining.filterValues(Set<String>::isEmpty).keys.sorted()
            if (ready.isEmpty()) throw CompilationBlocked(
                withDependencies.first().id,
                "operation-dependency-cycle",
                "The compiled operation dependency graph contains a cycle.",
            )
            ready.forEach { id ->
                ordered += byId.getValue(id)
                remaining.remove(id)
                remaining.values.forEach { it.remove(id) }
            }
        }
        return ordered.mapIndexed { index, operation -> operation.copy(order = index) }
    }

    private fun splitSafely(
        result: DocumentCompiledRecommendationResult,
    ): List<DocumentCompiledRecommendationResult> {
        if (result.status != DocumentCompilationStatus.Compiled ||
            result.expandedTypedEditCount <= MAX_DOCUMENT_EXPANDED_TYPED_EDITS_PER_RECOMMENDATION
        ) {
            return listOf(result)
        }
        val byId = result.operations.associateBy(DocumentPlanOperation::id)
        val neighbors = result.operations.associate { it.id to mutableSetOf<String>() }
        result.operations.forEach { operation ->
            operation.dependsOnOperationIds.forEach { dependency ->
                neighbors.getValue(operation.id) += dependency
                neighbors.getValue(dependency) += operation.id
            }
        }
        val components = mutableListOf<List<DocumentPlanOperation>>()
        val unseen = byId.keys.toMutableSet()
        while (unseen.isNotEmpty()) {
            val pending = ArrayDeque(listOf(unseen.min()))
            val componentIds = linkedSetOf<String>()
            while (pending.isNotEmpty()) {
                val id = pending.removeFirst()
                if (!componentIds.add(id)) continue
                unseen.remove(id)
                neighbors.getValue(id).sorted().forEach(pending::addLast)
            }
            components += componentIds.map(byId::getValue).sortedBy(DocumentPlanOperation::order)
        }
        if (components.any {
                it.sumOf(DocumentPlanOperation::expandedTypedEditCount) >
                    MAX_DOCUMENT_EXPANDED_TYPED_EDITS_PER_RECOMMENDATION
            }
        ) {
            return listOf(blocked(
                result.sourceGroupId,
                result.failures.firstOrNull()?.semanticItemId ?: result.sourceGroupId,
                "atomic-group-exceeds-limit",
                "An atomic dependency closure exceeds the approved recommendation limit.",
                result.confidence.evidence,
                result.confidence.modeling,
                result.confidence.ontologyFit,
            ))
        }
        val partitions = mutableListOf<MutableList<DocumentPlanOperation>>()
        components.forEach { component ->
            val current = partitions.lastOrNull()
            if (current == null ||
                current.sumOf(DocumentPlanOperation::expandedTypedEditCount) +
                component.sumOf(DocumentPlanOperation::expandedTypedEditCount) >
                MAX_DOCUMENT_EXPANDED_TYPED_EDITS_PER_RECOMMENDATION
            ) {
                partitions += mutableListOf<DocumentPlanOperation>()
            }
            partitions.last() += component
        }
        return partitions.mapIndexed { index, partition ->
            val declarations = partition.mapNotNull(DocumentPlanOperation::declaration).toSet()
            result.copy(
                groupId = "${result.sourceGroupId}-part-${(index + 1).toString().padStart(2, '0')}",
                sourceGroupId = result.sourceGroupId,
                operations = partition.sortedBy(DocumentPlanOperation::order)
                    .mapIndexed { order, operation -> operation.copy(order = order) },
                references = result.references.filter { it.temporaryReference in declarations },
            )
        }
    }

    private fun operationKey(operation: DocumentPlanOperation): String =
        "${operation.kind.name}:${operation.declaration?.value.orEmpty()}:" +
            operation.operands.filterNot { it is DocumentPlanOperand.SourceId }.joinToString("|")

    private fun shaclOperands(
        item: DocumentSemanticPlanItem,
        resolve: (DocumentSemanticPlanItem, DocumentSemanticReferenceRole) -> DocumentPlanOperand,
    ): List<DocumentPlanOperand> {
        val encoded = item.datatypeIntent?.split(":", limit = 2)
            ?: throw CompilationBlocked(item.id, "shacl-constraint-missing", "A SHACL constraint kind and value are required.")
        val kind = encoded.first()
        val value = encoded.getOrNull(1)?.takeIf(String::isNotBlank)
            ?: throw CompilationBlocked(item.id, "shacl-constraint-missing", "A SHACL constraint value is required.")
        if (kind !in DocumentSemanticPatternRegistry.supportedShaclConstraints) {
            throw CompilationBlocked(item.id, "shacl-constraint-unsupported", "The SHACL constraint is not supported.")
        }
        val constraintValue = when (kind) {
            "MinCount", "MaxCount" -> value.toIntOrNull()?.let(DocumentPlanOperand::IntegerValue)
            "MinInclusive", "MaxInclusive" -> value.toBigDecimalOrNull()?.let {
                DocumentPlanOperand.DecimalValue(value)
            }
            "Datatype", "Class" -> runCatching { DocumentPlanOperand.ExistingEntity(Iri(value)) }.getOrNull()
            "Pattern" -> DocumentPlanOperand.TextValue(value)
            else -> null
        } ?: throw CompilationBlocked(item.id, "shacl-constraint-value-invalid", "The SHACL value is invalid.")
        return listOf(
            resolve(item, DocumentSemanticReferenceRole.ConstraintTarget),
            DocumentPlanOperand.TextValue(kind),
            constraintValue,
        )
    }

    private fun propertyShapeOperands(
        item: DocumentSemanticPlanItem,
        constraint: DocumentSemanticPlanItem,
        resolve: (DocumentSemanticPlanItem, DocumentSemanticReferenceRole) -> DocumentPlanOperand,
    ): List<DocumentPlanOperand> {
        val encoded = constraint.datatypeIntent?.split(":", limit = 2)
            ?: throw CompilationBlocked(
                constraint.id,
                "property-shape-constraint-required",
                "A property shape requires an exact supported constraint.",
            )
        val kind = encoded.first()
        val rawValue = encoded.getOrNull(1)
        val value = when (kind) {
            "MinCount", "MaxCount" -> rawValue?.toIntOrNull()?.takeIf { it >= 0 }
                ?.let(DocumentPlanOperand::IntegerValue)
            "MinInclusive", "MaxInclusive" -> rawValue?.toBigDecimalOrNull()
                ?.let { DocumentPlanOperand.DecimalValue(rawValue) }
            else -> null
        } ?: throw CompilationBlocked(
            item.id,
            "property-shape-constraint-unsupported",
            "The property-shape constraint is not a supported count or numeric threshold.",
        )
        return listOf(
            resolve(item, DocumentSemanticReferenceRole.Shape),
            resolve(item, DocumentSemanticReferenceRole.Path),
            DocumentPlanOperand.TextValue(item.label),
            DocumentPlanOperand.TextValue(kind),
            value,
        )
    }

    private fun operation(
        item: DocumentSemanticPlanItem,
        kind: DocumentPlanOperationKind,
        context: DocumentSemanticCompilerContext,
        declaration: DocumentTemporaryReference? = null,
        operands: List<DocumentPlanOperand>,
    ): DocumentPlanOperation = DocumentPlanOperation(
        id = "compile-${item.id}-${kind.name.lowercase()}",
        kind = kind,
        order = 0,
        declaration = declaration,
        operands = operands + DocumentPlanOperand.SourceId(context.targetSourceId),
        expandedTypedEditCount = 1,
    )

    private fun blocked(
        groupId: String,
        itemId: String,
        code: String,
        message: String,
        evidence: Int,
        modeling: Int,
        ontologyFit: Int,
    ): DocumentCompiledRecommendationResult = DocumentCompiledRecommendationResult(
        groupId = groupId,
        status = DocumentCompilationStatus.Blocked,
        failures = listOf(DocumentCompilationFailure(itemId, code, message)),
        confidence = confidence(evidence, modeling, ontologyFit, 0),
    )

    private fun confidence(
        evidence: Int,
        modeling: Int,
        ontologyFit: Int,
        compilation: Int? = null,
    ): DocumentCompiledConfidenceDimensions =
        DocumentCompiledConfidenceDimensions(evidence, modeling, ontologyFit, compilation)

    private fun localName(label: String): String =
        label.split(Regex("[^A-Za-z0-9]+"))
            .filter(String::isNotBlank)
            .joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
            .let { value -> if (value.firstOrNull()?.isLetter() == true) value else "Item$value" }

    private val DocumentSemanticItemKind.declarationKind: DocumentTemporaryReferenceKind?
        get() = when (this) {
            DocumentSemanticItemKind.Class -> DocumentTemporaryReferenceKind.Class
            DocumentSemanticItemKind.ObjectProperty -> DocumentTemporaryReferenceKind.ObjectProperty
            DocumentSemanticItemKind.DatatypeProperty -> DocumentTemporaryReferenceKind.DatatypeProperty
            DocumentSemanticItemKind.AnnotationProperty -> DocumentTemporaryReferenceKind.AnnotationProperty
            DocumentSemanticItemKind.Individual -> DocumentTemporaryReferenceKind.Individual
            DocumentSemanticItemKind.NodeShape -> DocumentTemporaryReferenceKind.Shape
            DocumentSemanticItemKind.PropertyShape -> DocumentTemporaryReferenceKind.Shape
            else -> null
        }

    private val DocumentSemanticItemKind.declarationOperation: DocumentPlanOperationKind?
        get() = when (this) {
            DocumentSemanticItemKind.Class -> DocumentPlanOperationKind.CreateClass
            DocumentSemanticItemKind.ObjectProperty -> DocumentPlanOperationKind.CreateObjectProperty
            DocumentSemanticItemKind.DatatypeProperty -> DocumentPlanOperationKind.CreateDatatypeProperty
            DocumentSemanticItemKind.AnnotationProperty -> DocumentPlanOperationKind.CreateAnnotationProperty
            DocumentSemanticItemKind.Individual -> DocumentPlanOperationKind.CreateIndividual
            DocumentSemanticItemKind.NodeShape -> DocumentPlanOperationKind.CreateNodeShape
            DocumentSemanticItemKind.PropertyShape -> DocumentPlanOperationKind.CreatePropertyShape
            else -> null
        }

    private val DocumentSemanticItemKind.operationKind: DocumentPlanOperationKind?
        get() = when (this) {
            DocumentSemanticItemKind.SubclassRelationship -> DocumentPlanOperationKind.AddSuperclass
            DocumentSemanticItemKind.ObjectPropertyDomain,
            DocumentSemanticItemKind.DatatypePropertyDomain,
            -> DocumentPlanOperationKind.SetPropertyDomain
            DocumentSemanticItemKind.ObjectPropertyRange,
            DocumentSemanticItemKind.DatatypePropertyRange,
            -> DocumentPlanOperationKind.SetPropertyRange
            DocumentSemanticItemKind.IndividualType -> DocumentPlanOperationKind.AssignType
            DocumentSemanticItemKind.ObjectPropertyAssertion -> DocumentPlanOperationKind.AddObjectPropertyAssertion
            DocumentSemanticItemKind.DatatypeValueAssertion -> DocumentPlanOperationKind.AddDatatypePropertyAssertion
            DocumentSemanticItemKind.PreferredLabel -> DocumentPlanOperationKind.SetEntityLabel
            DocumentSemanticItemKind.Definition -> DocumentPlanOperationKind.AddDefinition
            DocumentSemanticItemKind.AlternateLabel -> DocumentPlanOperationKind.AddAlternateLabel
            DocumentSemanticItemKind.ShaclConstraint -> DocumentPlanOperationKind.UpdateShaclConstraint
            else -> null
        }
}

private class CompilationBlocked(
    val itemId: String,
    val code: String,
    message: String,
) : IllegalArgumentException(message)
