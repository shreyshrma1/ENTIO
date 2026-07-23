package com.entio.semantic

import com.entio.core.EntioProject
import com.entio.core.Iri
import com.entio.core.OntologyEntityDescriptor
import com.entio.core.OntologyGraphEdge
import com.entio.core.OntologyGraphEdgeKind
import com.entio.core.OntologyGraphExpansionCategory
import com.entio.core.OntologyGraphInitialQuery
import com.entio.core.OntologyGraphLoadKind
import com.entio.core.OntologyGraphNeighborhoodQuery
import com.entio.core.OntologyGraphNode
import com.entio.core.OntologyGraphNodeId
import com.entio.core.OntologyGraphNodeKind
import com.entio.core.OntologyGraphNodeSummary
import com.entio.core.OntologyGraphPage
import com.entio.core.OntologyGraphPageCursor
import com.entio.core.RdfResource
import com.entio.core.SemanticDescriptorKind

/** Builds deterministic bounded read models from asserted local ontology descriptors. */
public class OntologyGraphService(
    private val descriptorAssembler: SemanticDescriptorAssembler = SemanticDescriptorAssembler(),
) {
    public fun initial(project: EntioProject, query: OntologyGraphInitialQuery): OntologyGraphPage {
        val model = buildModel(project, query.sourceIds)
        val seed = query.seed?.also { require(model.nodes.containsKey(it)) { "missing-graph-entity" } }
        val selectedIds = if (seed != null) {
            model.edges.asSequence()
                .filter { it.source == seed || it.target == seed }
                .flatMap { sequenceOf(it.source, it.target) }
                .plus(seed)
                .distinct()
                .sortedWith(nodeIdComparator(model.nodes))
                .toList()
        } else {
            rootOverview(model)
        }
        return page(
            model = model,
            selectedIds = selectedIds,
            selectedEdges = model.edges,
            loadKind = if (seed == null) OntologyGraphLoadKind.RootOverview else OntologyGraphLoadKind.EntityCentered,
            seed = seed,
            cursor = query.cursor,
            nodeLimit = query.limits.nodeLimit,
            edgeLimit = query.limits.edgeLimit,
        )
    }

    public fun neighborhood(project: EntioProject, query: OntologyGraphNeighborhoodQuery): OntologyGraphPage {
        val model = buildModel(project, query.sourceIds)
        require(model.nodes.containsKey(query.entity)) { "missing-graph-entity" }
        val allowedKinds = query.categories.flatMapTo(mutableSetOf()) { category ->
            when (category) {
                OntologyGraphExpansionCategory.ClassHierarchy -> setOf(OntologyGraphEdgeKind.SubclassOf)
                OntologyGraphExpansionCategory.PropertySchema -> setOf(OntologyGraphEdgeKind.Domain, OntologyGraphEdgeKind.Range)
                OntologyGraphExpansionCategory.AssertedTypes -> setOf(OntologyGraphEdgeKind.Type)
                OntologyGraphExpansionCategory.ObjectAssertions -> setOf(OntologyGraphEdgeKind.ObjectAssertion)
            }
        }
        val edges = model.edges.filter { edge ->
            edge.kind in allowedKinds && (edge.source == query.entity || edge.target == query.entity)
        }
        val selectedIds = edges.asSequence()
            .flatMap { sequenceOf(it.source, it.target) }
            .plus(query.entity)
            .distinct()
            .sortedWith(nodeIdComparator(model.nodes))
            .toList()
        return page(
            model = model,
            selectedIds = selectedIds,
            selectedEdges = edges,
            loadKind = OntologyGraphLoadKind.Neighborhood,
            seed = query.entity,
            cursor = query.cursor,
            nodeLimit = query.limits.nodeLimit,
            edgeLimit = query.limits.edgeLimit,
        )
    }

    private fun buildModel(project: EntioProject, sourceIds: Set<String>): GraphModel {
        val descriptors = descriptorAssembler.assemble(project)
            .filter { descriptor -> descriptor.common.sourceId in sourceIds }
            .filter { descriptor -> descriptor.common.entity is Iri }
            .filter { descriptor -> descriptor.common.kind.toGraphKind() != null }
        val descriptorsById = descriptors.associateBy { descriptor -> descriptor.nodeId() }
        val idsByIri = descriptorsById.keys.groupBy { it.entityIri }
        var ambiguousCount = 0

        fun resolve(sourceId: String, iri: Iri): OntologyGraphNodeId? {
            val candidates = idsByIri[iri].orEmpty()
            candidates.singleOrNull { it.sourceId == sourceId }?.let { return it }
            if (candidates.size == 1) return candidates.single()
            if (candidates.size > 1) ambiguousCount += 1
            return null
        }

        val edges = buildList {
            descriptors.forEach { descriptor ->
                val source = descriptor.nodeId()
                when (descriptor) {
                    is OntologyEntityDescriptor.Class -> descriptor.directSuperclasses.forEach { targetIri ->
                        resolve(source.sourceId, targetIri)?.let { target ->
                            addEdge(OntologyGraphEdgeKind.SubclassOf, source, target, "subclass of")
                        }
                    }
                    is OntologyEntityDescriptor.ObjectProperty -> {
                        descriptor.domains.forEach { targetIri ->
                            resolve(source.sourceId, targetIri)?.let { addEdge(OntologyGraphEdgeKind.Domain, source, it, "domain") }
                        }
                        descriptor.ranges.forEach { targetIri ->
                            resolve(source.sourceId, targetIri)?.let { addEdge(OntologyGraphEdgeKind.Range, source, it, "range") }
                        }
                    }
                    is OntologyEntityDescriptor.DatatypeProperty -> descriptor.domains.forEach { targetIri ->
                        resolve(source.sourceId, targetIri)?.let { addEdge(OntologyGraphEdgeKind.Domain, source, it, "domain") }
                    }
                    is OntologyEntityDescriptor.Individual -> {
                        descriptor.assertedTypes.forEach { targetIri ->
                            resolve(source.sourceId, targetIri)?.let { addEdge(OntologyGraphEdgeKind.Type, source, it, "type") }
                        }
                        descriptor.objectPropertyAssertions.forEach { assertion ->
                            val targetIri = assertion.value as? Iri ?: return@forEach
                            val target = resolve(source.sourceId, targetIri) ?: return@forEach
                            val propertyLabel = descriptors
                                .firstOrNull { it.common.entity == assertion.property && it.common.sourceId == source.sourceId }
                                ?.displayLabel()
                                ?: assertion.property.value.graphFallbackLabel()
                            addEdge(
                                kind = OntologyGraphEdgeKind.ObjectAssertion,
                                source = source,
                                target = target,
                                label = propertyLabel,
                                predicateIri = assertion.property,
                            )
                        }
                    }
                    is OntologyEntityDescriptor.AnnotationProperty -> Unit
                }
            }
        }.distinctBy { it.id }.sortedWith(edgeComparator)

        val availableCounts = edges.flatMap { listOf(it.source, it.target) }.groupingBy { it }.eachCount()
        val nodes = descriptorsById.mapValues { (id, descriptor) -> descriptor.toNode(id, availableCounts[id] ?: 0, descriptors) }
        return GraphModel(nodes, edges, ambiguousCount.coerceAtMost(MAX_AMBIGUOUS_DIAGNOSTIC_COUNT))
    }

    private fun MutableList<OntologyGraphEdge>.addEdge(
        kind: OntologyGraphEdgeKind,
        source: OntologyGraphNodeId,
        target: OntologyGraphNodeId,
        label: String,
        predicateIri: Iri? = null,
    ) {
        if (source == target) return
        val id = listOf(kind.name, source.stableKey, target.stableKey, predicateIri?.value.orEmpty()).joinToString("\u0000")
        add(OntologyGraphEdge(id, kind, source, target, label, predicateIri))
    }

    private fun rootOverview(model: GraphModel): List<OntologyGraphNodeId> {
        val roots = model.nodes.values
            .filter { it.kind == OntologyGraphNodeKind.Class }
            .filter { node -> model.edges.none { it.kind == OntologyGraphEdgeKind.SubclassOf && it.source == node.id } }
            .map { it.id }
            .sortedWith(nodeIdComparator(model.nodes))
        val ordered = linkedSetOf<OntologyGraphNodeId>()
        val queue = ArrayDeque(roots)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!ordered.add(current)) continue
            model.edges.asSequence()
                .filter { it.kind == OntologyGraphEdgeKind.SubclassOf && it.target == current }
                .map { it.source }
                .sortedWith(nodeIdComparator(model.nodes))
                .forEach(queue::addLast)
        }
        model.edges.asSequence()
            .filter { it.kind == OntologyGraphEdgeKind.Domain || it.kind == OntologyGraphEdgeKind.Range }
            .filter { it.target in ordered }
            .map { it.source }
            .sortedWith(nodeIdComparator(model.nodes))
            .forEach(ordered::add)
        model.edges.asSequence()
            .filter { it.kind == OntologyGraphEdgeKind.Type && it.target in ordered }
            .map { it.source }
            .sortedWith(nodeIdComparator(model.nodes))
            .forEach(ordered::add)
        return ordered.toList()
    }

    private fun page(
        model: GraphModel,
        selectedIds: List<OntologyGraphNodeId>,
        selectedEdges: List<OntologyGraphEdge>,
        loadKind: OntologyGraphLoadKind,
        seed: OntologyGraphNodeId?,
        cursor: OntologyGraphPageCursor,
        nodeLimit: Int,
        edgeLimit: Int,
    ): OntologyGraphPage {
        val pageIds = selectedIds.drop(cursor.nodeOffset).take(nodeLimit)
        val pageIdSet = pageIds.toSet()
        val eligibleEdges = selectedEdges.filter { it.source in pageIdSet && it.target in pageIdSet }
        val pageEdges = eligibleEdges.drop(cursor.edgeOffset).take(edgeLimit)
        val loadedCounts = pageEdges.flatMap { listOf(it.source, it.target) }.groupingBy { it }.eachCount()
        val pageNodes = pageIds.map { id ->
            val node = requireNotNull(model.nodes[id])
            node.copy(summary = node.summary.copy(loadedRelationshipCount = loadedCounts[id] ?: 0))
        }
        val nextNodeOffset = cursor.nodeOffset + pageIds.size
        val nextEdgeOffset = cursor.edgeOffset + pageEdges.size
        val nextCursor = when {
            nextEdgeOffset < eligibleEdges.size -> OntologyGraphPageCursor(cursor.nodeOffset, nextEdgeOffset)
            nextNodeOffset < selectedIds.size -> OntologyGraphPageCursor(nextNodeOffset, 0)
            else -> null
        }
        return OntologyGraphPage(
            loadKind = loadKind,
            seed = seed,
            nodes = pageNodes,
            edges = pageEdges,
            totalNodeCount = selectedIds.size,
            totalEdgeCount = selectedEdges.count { it.source in selectedIds && it.target in selectedIds },
            nextCursor = nextCursor,
            ambiguousCrossSourceRelationshipCount = model.ambiguousCount,
        )
    }

    private fun OntologyEntityDescriptor.toNode(
        id: OntologyGraphNodeId,
        availableCount: Int,
        descriptors: List<OntologyEntityDescriptor>,
    ): OntologyGraphNode {
        fun labels(iris: List<Iri>): List<String> = iris.map { iri ->
            descriptors.firstOrNull { it.common.entity == iri && it.common.sourceId == id.sourceId }?.displayLabel()
                ?: iri.value.graphFallbackLabel()
        }.distinct().sortedWith(compareBy(String::lowercase).thenBy { it })
        val summary = when (this) {
            is OntologyEntityDescriptor.Class -> OntologyGraphNodeSummary(
                directSuperclassLabels = labels(directSuperclasses),
                availableRelationshipCount = availableCount,
            )
            is OntologyEntityDescriptor.ObjectProperty -> OntologyGraphNodeSummary(
                domainLabels = labels(domains),
                rangeLabels = labels(ranges),
                availableRelationshipCount = availableCount,
            )
            is OntologyEntityDescriptor.DatatypeProperty -> OntologyGraphNodeSummary(
                domainLabels = labels(domains),
                datatypeRangeLabels = labels(datatypeRanges),
                availableRelationshipCount = availableCount,
            )
            is OntologyEntityDescriptor.Individual -> OntologyGraphNodeSummary(
                assertedTypeLabels = labels(assertedTypes),
                availableRelationshipCount = availableCount,
            )
            is OntologyEntityDescriptor.AnnotationProperty -> error("Unsupported graph descriptor")
        }
        return OntologyGraphNode(
            id = id,
            kind = requireNotNull(common.kind.toGraphKind()),
            label = displayLabel(),
            definitionExcerpt = common.definitions.firstOrNull()?.lexicalForm?.take(DEFINITION_EXCERPT_LIMIT),
            summary = summary,
        )
    }

    private fun OntologyEntityDescriptor.nodeId(): OntologyGraphNodeId =
        OntologyGraphNodeId(common.sourceId, common.entity as Iri)

    private fun OntologyEntityDescriptor.displayLabel(): String =
        common.preferredLabel?.lexicalForm ?: common.entity.value.graphFallbackLabel()

    private fun String.graphFallbackLabel(): String =
        substringAfterLast('#', substringAfterLast('/')).takeIf(String::isNotBlank) ?: this

    private fun SemanticDescriptorKind.toGraphKind(): OntologyGraphNodeKind? = when (this) {
        SemanticDescriptorKind.Class -> OntologyGraphNodeKind.Class
        SemanticDescriptorKind.ObjectProperty -> OntologyGraphNodeKind.ObjectProperty
        SemanticDescriptorKind.DatatypeProperty -> OntologyGraphNodeKind.DatatypeProperty
        SemanticDescriptorKind.Individual -> OntologyGraphNodeKind.Individual
        SemanticDescriptorKind.AnnotationProperty -> null
    }

    private fun nodeIdComparator(nodes: Map<OntologyGraphNodeId, OntologyGraphNode>): Comparator<OntologyGraphNodeId> =
        compareBy<OntologyGraphNodeId> { nodes[it]?.kind?.ordinal ?: Int.MAX_VALUE }
            .thenBy { nodes[it]?.label?.lowercase().orEmpty() }
            .thenBy { it.sourceId }
            .thenBy { it.entityIri.value }

    private data class GraphModel(
        val nodes: Map<OntologyGraphNodeId, OntologyGraphNode>,
        val edges: List<OntologyGraphEdge>,
        val ambiguousCount: Int,
    )

    private companion object {
        private const val DEFINITION_EXCERPT_LIMIT: Int = 160
        private const val MAX_AMBIGUOUS_DIAGNOSTIC_COUNT: Int = 1_000
        private val edgeComparator = compareBy<OntologyGraphEdge> { it.kind.ordinal }
            .thenBy { it.label.lowercase() }
            .thenBy { it.source.stableKey }
            .thenBy { it.target.stableKey }
            .thenBy { it.predicateIri?.value.orEmpty() }
    }
}
