package com.entio.semantic

import com.entio.core.BlankNodeResource
import com.entio.core.ChangeSet
import com.entio.core.EntioResult
import com.entio.core.GraphChange
import com.entio.core.GraphChangeKind
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.LoadedOntology
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.RdfTerm
import com.entio.core.ResolvedOntologySource
import com.entio.core.ShaclConstraint
import com.entio.core.ShaclConstraintKind
import com.entio.core.ShaclConstraintValue
import com.entio.core.ShaclGraphIdentity
import com.entio.core.ShaclGraphRole
import com.entio.core.ShaclNodeShape
import com.entio.core.ShaclPath
import com.entio.core.ShaclPropertyShape
import com.entio.core.ShaclSeverity
import com.entio.core.ShaclShapeId
import com.entio.core.ShaclSourceRole
import com.entio.core.ShaclTarget
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

private fun shaclGraphFingerprint(graph: GraphState): String = MessageDigest.getInstance("SHA-256")
    .digest(graph.triples.sortedWith(compareBy<GraphTriple>({ it.subjectResource.value }, { it.predicate.value }, { it.objectValue }))
        .joinToString("\n").toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

public data class ShaclGraphSet(
    public val dataGraph: GraphState,
    public val shapesGraph: GraphState,
    public val identity: ShaclGraphIdentity,
)

public data class ShaclAuthoringDocument(
    public val sourceId: String,
    public val graph: GraphState,
    public val nodeShapes: List<ShaclNodeShape>,
)

public enum class ShaclShapeEditOperation {
    Add,
    Edit,
    Delete,
}

public data class ShaclShapeEdit(
    public val sourceId: String,
    public val operation: ShaclShapeEditOperation,
    public val shape: ShaclNodeShape,
    public val previousShape: ShaclNodeShape? = null,
)

public class ShaclGraphLoader {
    public fun load(sources: List<LoadedOntology>): EntioResult<ShaclGraphSet> {
        val dataSources = sources.filter { it.source.roles.contains(ShaclGraphRole.Data) }
        val shapeSources = sources.filter { it.source.roles.contains(ShaclGraphRole.Shapes) }
        val dataGraph = GraphState(dataSources.flatMap { it.graph.triples }.toSet())
        val shapesGraph = GraphState(shapeSources.flatMap { it.graph.triples }.toSet())
        return EntioResult.Success(
            ShaclGraphSet(
                dataGraph = dataGraph,
                shapesGraph = shapesGraph,
                identity = ShaclGraphIdentity(
                    dataSourceIds = dataSources.map { it.source.id }.sorted(),
                    shapesSourceIds = shapeSources.map { it.source.id }.sorted(),
                    dataGraphFingerprint = shaclGraphFingerprint(dataGraph),
                    shapesGraphFingerprint = shaclGraphFingerprint(shapesGraph),
                ),
            ),
        )
    }
}

public class ShaclShapeAuthoringService {
    public fun load(sourceId: String, graph: GraphState): EntioResult<ShaclAuthoringDocument> {
        val nodeShapeResources = graph.triples
            .filter { it.predicate == SH_TYPE && it.objectTerm == SH_NODE_SHAPE }
            .map { it.subjectResource }
            .distinct()
            .sortedBy { it.value }
        val shapes = nodeShapeResources.mapIndexed { index, resource ->
            parseNodeShape(sourceId, graph, resource, index)
        }
        val failures = shapes.filterIsInstance<ParseFailure>()
        if (failures.isNotEmpty()) return failure(failures.first().message)
        return EntioResult.Success(
            ShaclAuthoringDocument(
                sourceId = sourceId,
                graph = graph,
                nodeShapes = shapes.filterIsInstance<ShaclNodeShape>().sortedBy { it.id.iri.value },
            ),
        )
    }

    public fun translate(edit: ShaclShapeEdit, currentGraph: GraphState): EntioResult<ChangeSet> {
        val additions = mutableListOf<GraphChange>()
        val removals = mutableListOf<GraphChange>()
        return try {
            when (edit.operation) {
                ShaclShapeEditOperation.Add -> additions += encode(edit.shape, edit.sourceId)
                    .map { GraphChange(GraphChangeKind.Addition, it) }
                ShaclShapeEditOperation.Delete -> removals += encode(edit.shape, edit.sourceId)
                    .filter { it in currentGraph.triples || it.subjectResource == edit.shape.id.iri }
                    .map { GraphChange(GraphChangeKind.Removal, it) }
                ShaclShapeEditOperation.Edit -> {
                    val previous = edit.previousShape
                        ?: return failure("An edited shape requires its previous shape.")
                    removals += encode(previous, edit.sourceId).map { GraphChange(GraphChangeKind.Removal, it) }
                    additions += encode(edit.shape, edit.sourceId).map { GraphChange(GraphChangeKind.Addition, it) }
                }
            }
            EntioResult.Success(ChangeSet(removals + additions))
        } catch (exception: IllegalArgumentException) {
            failure(exception.message ?: "Shape edit could not be translated.")
        }
    }

    private fun parseNodeShape(
        sourceId: String,
        graph: GraphState,
        resource: RdfResource,
        index: Int,
    ): Any = try {
        val shapeId = ShaclShapeId(stableShapeIri(sourceId, resource, index), sourceId)
        val targets = graph.objects(resource, SH_TARGET_CLASS).mapNotNull { it.asIri() }.map(ShaclTarget::TargetClass) +
            graph.objects(resource, SH_TARGET_NODE).mapNotNull { it as? RdfResource }.map(ShaclTarget::TargetNode) +
            graph.objects(resource, SH_TARGET_SUBJECTS_OF).mapNotNull { it.asIri() }.map(ShaclTarget::TargetSubjectsOf) +
            graph.objects(resource, SH_TARGET_OBJECTS_OF).mapNotNull { it.asIri() }.map(ShaclTarget::TargetObjectsOf)
        val propertyShapes = graph.objects(resource, SH_PROPERTY).mapNotNull { it as? RdfResource }.mapIndexed { propertyIndex, propertyResource ->
            parsePropertyShape(sourceId, graph, resource, propertyResource, propertyIndex)
        }
        val propertyFailures = propertyShapes.filterIsInstance<ParseFailure>()
        if (propertyFailures.isNotEmpty()) {
            propertyFailures.first()
        } else ShaclNodeShape(
            id = shapeId,
            label = graph.objects(resource, RDFS_LABEL).firstOrNull()?.asLiteral()?.lexicalForm,
            targets = targets,
            propertyShapes = propertyShapes.filterIsInstance<ShaclPropertyShape>(),
            constraints = parseConstraints(graph, resource),
            closed = graph.objects(resource, SH_CLOSED).firstOrNull()?.asBoolean() ?: false,
            ignoredProperties = graph.objects(resource, SH_IGNORED_PROPERTIES).flatMap { graph.list(it) }.mapNotNull { it.asIri() },
            severity = graph.objects(resource, SH_SEVERITY).firstOrNull()?.asSeverity() ?: ShaclSeverity.Violation,
            message = graph.objects(resource, SH_MESSAGE).firstOrNull()?.asLiteral()?.lexicalForm,
        )
    } catch (exception: UnsupportedShapeException) {
        ParseFailure(exception.message ?: "Unsupported SHACL shape.")
    }

    private fun parsePropertyShape(
        sourceId: String,
        graph: GraphState,
        nodeShape: RdfResource,
        resource: RdfResource,
        index: Int,
    ): Any {
        val path = graph.objects(resource, SH_PATH).firstOrNull() ?: throw UnsupportedShapeException("SHACL property shape has no path.")
        val propertyIri = path.asIri() ?: throw UnsupportedShapeException("Complex SHACL property paths are not supported.")
        return ShaclPropertyShape(
            id = ShaclShapeId(stablePropertyShapeIri(sourceId, nodeShape, propertyIri, index), sourceId),
            path = ShaclPath.DirectProperty(propertyIri),
            constraints = parseConstraints(graph, resource),
            severity = graph.objects(resource, SH_SEVERITY).firstOrNull()?.asSeverity() ?: ShaclSeverity.Violation,
            message = graph.objects(resource, SH_MESSAGE).firstOrNull()?.asLiteral()?.lexicalForm,
        )
    }

    private fun parseConstraints(graph: GraphState, resource: RdfResource): List<ShaclConstraint> = buildList {
        graph.integerConstraint(resource, SH_MIN_COUNT)?.let { add(ShaclConstraint(ShaclConstraintKind.MinCount, ShaclConstraintValue.IntegerValue(it))) }
        graph.integerConstraint(resource, SH_MAX_COUNT)?.let { add(ShaclConstraint(ShaclConstraintKind.MaxCount, ShaclConstraintValue.IntegerValue(it))) }
        graph.integerConstraint(resource, SH_MIN_COUNT)?.let { min ->
            graph.integerConstraint(resource, SH_MAX_COUNT)?.takeIf { it == min }?.let {
                add(ShaclConstraint(ShaclConstraintKind.ExactCount, ShaclConstraintValue.IntegerValue(min)))
            }
        }
        graph.termConstraint(resource, SH_DATATYPE)?.let { add(ShaclConstraint(ShaclConstraintKind.Datatype, ShaclConstraintValue.TermValue(it))) }
        graph.termConstraint(resource, SH_CLASS)?.let { add(ShaclConstraint(ShaclConstraintKind.Class, ShaclConstraintValue.TermValue(it))) }
        graph.objects(resource, SH_IN).firstOrNull()?.let { add(ShaclConstraint(ShaclConstraintKind.In, ShaclConstraintValue.TermListValue(graph.list(it)))) }
        graph.termConstraint(resource, SH_HAS_VALUE)?.let { add(ShaclConstraint(ShaclConstraintKind.HasValue, ShaclConstraintValue.TermValue(it))) }
        listOf(
            SH_MIN_INCLUSIVE to ShaclConstraintKind.MinInclusive,
            SH_MAX_INCLUSIVE to ShaclConstraintKind.MaxInclusive,
            SH_MIN_EXCLUSIVE to ShaclConstraintKind.MinExclusive,
            SH_MAX_EXCLUSIVE to ShaclConstraintKind.MaxExclusive,
        ).forEach { (predicate, kind) -> graph.termConstraint(resource, predicate)?.let { add(ShaclConstraint(kind, ShaclConstraintValue.TermValue(it))) } }
        graph.literalConstraint(resource, SH_PATTERN)?.let { add(ShaclConstraint(ShaclConstraintKind.Pattern, ShaclConstraintValue.TextValue(it))) }
        graph.integerConstraint(resource, SH_MIN_LENGTH)?.let { add(ShaclConstraint(ShaclConstraintKind.MinLength, ShaclConstraintValue.IntegerValue(it))) }
        graph.integerConstraint(resource, SH_MAX_LENGTH)?.let { add(ShaclConstraint(ShaclConstraintKind.MaxLength, ShaclConstraintValue.IntegerValue(it))) }
        graph.objects(resource, SH_CLOSED).firstOrNull()?.asBoolean()?.let { add(ShaclConstraint(ShaclConstraintKind.Closed, ShaclConstraintValue.BooleanValue(it))) }
    }

    private fun encode(shape: ShaclNodeShape, sourceId: String): List<GraphTriple> {
        val output = mutableListOf<GraphTriple>()
        fun add(subject: RdfResource, predicate: Iri, objectTerm: RdfTerm) { output += GraphTriple(subject, predicate, objectTerm) }
        val subject = shape.id.iri
        add(subject, SH_TYPE, SH_NODE_SHAPE)
        shape.label?.let { add(subject, RDFS_LABEL, RdfLiteral(it, Iri(XSD_STRING))) }
        shape.targets.forEach { target ->
            when (target) {
                is ShaclTarget.TargetClass -> add(subject, SH_TARGET_CLASS, target.classIri)
                is ShaclTarget.TargetNode -> add(subject, SH_TARGET_NODE, target.node)
                is ShaclTarget.TargetSubjectsOf -> add(subject, SH_TARGET_SUBJECTS_OF, target.propertyIri)
                is ShaclTarget.TargetObjectsOf -> add(subject, SH_TARGET_OBJECTS_OF, target.propertyIri)
            }
        }
        shape.propertyShapes.forEachIndexed { index, propertyShape ->
            val propertyNode = BlankNodeResource(stableBlankId(shape.id, propertyShape.path, index))
            add(subject, SH_PROPERTY, propertyNode)
            add(propertyNode, SH_PATH, (propertyShape.path as? ShaclPath.DirectProperty)?.propertyIri
                ?: throw UnsupportedShapeException("Complex SHACL property paths are not supported."))
            encodeCommon(output, propertyNode, propertyShape.constraints, propertyShape.severity, propertyShape.message)
        }
        encodeCommon(output, subject, shape.constraints, shape.severity, shape.message)
        if (shape.closed) add(subject, SH_CLOSED, RdfLiteral("true", Iri(XSD_BOOLEAN)))
        return output
    }

    private fun encodeCommon(output: MutableList<GraphTriple>, subject: RdfResource, constraints: List<ShaclConstraint>, severity: ShaclSeverity, message: String?) {
        severityIri(severity)?.let { output += GraphTriple(subject, SH_SEVERITY, it) }
        message?.let { output += GraphTriple(subject, SH_MESSAGE, RdfLiteral(it, Iri(XSD_STRING))) }
        constraints.forEach { constraint ->
            val predicate = constraintPredicate(constraint.kind)
            val value = constraint.value ?: return@forEach
            when (value) {
                is ShaclConstraintValue.IntegerValue -> output += GraphTriple(subject, predicate, RdfLiteral(value.value.toString(), Iri(XSD_INTEGER)))
                is ShaclConstraintValue.TermValue -> output += GraphTriple(subject, predicate, value.value)
                is ShaclConstraintValue.TextValue -> output += GraphTriple(subject, predicate, RdfLiteral(value.value, Iri(XSD_STRING)))
                is ShaclConstraintValue.BooleanValue -> output += GraphTriple(subject, predicate, RdfLiteral(value.value.toString(), Iri(XSD_BOOLEAN)))
                is ShaclConstraintValue.TermListValue -> {
                    val head = BlankNodeResource(stableListId(subject, predicate, value.values))
                    output += GraphTriple(subject, predicate, head)
                    value.values.forEachIndexed { index, term ->
                        val item = BlankNodeResource("${head.id}-$index")
                        output += GraphTriple(head, RDF_FIRST, term)
                        output += GraphTriple(head, RDF_REST, if (index == value.values.lastIndex) RDF_NIL else item)
                    }
                }
            }
        }
    }

    private fun constraintPredicate(kind: ShaclConstraintKind): Iri = constraintPredicates.getValue(kind)

    private fun stableShapeIri(sourceId: String, resource: RdfResource, index: Int): Iri = when (resource) {
        is Iri -> resource
        is BlankNodeResource -> Iri("urn:entio:shacl:shape:${hash("$sourceId|${resource.id}|$index")}")
    }

    private fun stablePropertyShapeIri(sourceId: String, nodeShape: RdfResource, path: Iri, index: Int): Iri =
        Iri("urn:entio:shacl:property:${hash("$sourceId|${nodeShape.value}|${path.value}|$index")}")

    private fun stableBlankId(shape: ShaclShapeId, path: ShaclPath, index: Int): String =
        "property-${hash("${shape.iri.value}|$path|$index").take(18)}"

    private fun stableListId(subject: RdfResource, predicate: Iri, values: List<RdfTerm>): String =
        "list-${hash(subject.value + predicate.value + values.joinToString { it.toString() }).take(18)}"

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun failure(message: String): EntioResult.Failure = EntioResult.Failure(message, listOf(ValidationIssue(ValidationSeverity.Error, "shacl-shape-invalid", message)))

    private data class ParseFailure(val message: String)
    private class UnsupportedShapeException(message: String) : RuntimeException(message)

    private companion object {
        private const val SH = "http://www.w3.org/ns/shacl#"
        private const val RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
        private const val XSD_STRING = "http://www.w3.org/2001/XMLSchema#string"
        private const val XSD_INTEGER = "http://www.w3.org/2001/XMLSchema#integer"
        private const val XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean"
        private val SH_NODE_SHAPE = Iri("$SH" + "NodeShape")
        private val SH_TYPE = Iri("$RDF" + "type")
        private val RDFS_LABEL = Iri("http://www.w3.org/2000/01/rdf-schema#label")
        private val SH_TARGET_CLASS = Iri("$SH" + "targetClass")
        private val SH_TARGET_NODE = Iri("$SH" + "targetNode")
        private val SH_TARGET_SUBJECTS_OF = Iri("$SH" + "targetSubjectsOf")
        private val SH_TARGET_OBJECTS_OF = Iri("$SH" + "targetObjectsOf")
        private val SH_PROPERTY = Iri("$SH" + "property")
        private val SH_PATH = Iri("$SH" + "path")
        private val SH_SEVERITY = Iri("$SH" + "severity")
        private val SH_MESSAGE = Iri("$SH" + "message")
        private val SH_CLOSED = Iri("$SH" + "closed")
        private val SH_IGNORED_PROPERTIES = Iri("$SH" + "ignoredProperties")
        private val SH_MIN_COUNT = Iri("$SH" + "minCount")
        private val SH_MAX_COUNT = Iri("$SH" + "maxCount")
        private val SH_DATATYPE = Iri("$SH" + "datatype")
        private val SH_CLASS = Iri("$SH" + "class")
        private val SH_IN = Iri("$SH" + "in")
        private val SH_HAS_VALUE = Iri("$SH" + "hasValue")
        private val SH_MIN_INCLUSIVE = Iri("$SH" + "minInclusive")
        private val SH_MAX_INCLUSIVE = Iri("$SH" + "maxInclusive")
        private val SH_MIN_EXCLUSIVE = Iri("$SH" + "minExclusive")
        private val SH_MAX_EXCLUSIVE = Iri("$SH" + "maxExclusive")
        private val SH_PATTERN = Iri("$SH" + "pattern")
        private val SH_MIN_LENGTH = Iri("$SH" + "minLength")
        private val SH_MAX_LENGTH = Iri("$SH" + "maxLength")
        private val RDF_FIRST = Iri("$RDF" + "first")
        private val RDF_REST = Iri("$RDF" + "rest")
        private val RDF_NIL = Iri("$RDF" + "nil")
        private val constraintPredicates = mapOf(
            ShaclConstraintKind.MinCount to SH_MIN_COUNT,
            ShaclConstraintKind.MaxCount to SH_MAX_COUNT,
            ShaclConstraintKind.ExactCount to SH_MIN_COUNT,
            ShaclConstraintKind.Datatype to SH_DATATYPE,
            ShaclConstraintKind.Class to SH_CLASS,
            ShaclConstraintKind.In to SH_IN,
            ShaclConstraintKind.HasValue to SH_HAS_VALUE,
            ShaclConstraintKind.MinInclusive to SH_MIN_INCLUSIVE,
            ShaclConstraintKind.MaxInclusive to SH_MAX_INCLUSIVE,
            ShaclConstraintKind.MinExclusive to SH_MIN_EXCLUSIVE,
            ShaclConstraintKind.MaxExclusive to SH_MAX_EXCLUSIVE,
            ShaclConstraintKind.Pattern to SH_PATTERN,
            ShaclConstraintKind.MinLength to SH_MIN_LENGTH,
            ShaclConstraintKind.MaxLength to SH_MAX_LENGTH,
            ShaclConstraintKind.Closed to SH_CLOSED,
        )
        private val graphTripleComparator = compareBy<GraphTriple>({ it.subjectResource.value }, { it.predicate.value }, { it.objectValue })
        private fun List<GraphTriple>.objects(subject: RdfResource, predicate: Iri): List<RdfTerm> = filter { it.subjectResource == subject && it.predicate == predicate }.map { it.objectTerm }
        private fun GraphState.objects(subject: RdfResource, predicate: Iri): List<RdfTerm> = triples.filter { it.subjectResource == subject && it.predicate == predicate }.map { it.objectTerm }
        private fun GraphState.integerConstraint(subject: RdfResource, predicate: Iri): Int? = objects(subject, predicate).firstOrNull()?.asLiteral()?.lexicalForm?.toIntOrNull()
        private fun GraphState.literalConstraint(subject: RdfResource, predicate: Iri): String? = objects(subject, predicate).firstOrNull()?.asLiteral()?.lexicalForm
        private fun GraphState.termConstraint(subject: RdfResource, predicate: Iri): RdfTerm? = objects(subject, predicate).firstOrNull()
        private fun GraphState.list(head: RdfTerm): List<RdfTerm> {
            val output = mutableListOf<RdfTerm>()
            var current = head as? RdfResource ?: return emptyList()
            val seen = mutableSetOf<RdfResource>()
            while (current != RDF_NIL && seen.add(current)) {
                val first = triples.firstOrNull { it.subjectResource == current && it.predicate == RDF_FIRST }?.objectTerm ?: break
                output += first
                current = triples.firstOrNull { it.subjectResource == current && it.predicate == RDF_REST }?.objectTerm as? RdfResource ?: break
            }
            return output
        }
        private fun RdfTerm.asIri(): Iri? = this as? Iri
        private fun RdfTerm.asLiteral(): RdfLiteral? = this as? RdfLiteral
        private fun RdfTerm.asBoolean(): Boolean? = asLiteral()?.lexicalForm?.toBooleanStrictOrNull()
        private fun RdfTerm.asSeverity(): ShaclSeverity? = when ((this as? Iri)?.value) {
            "$SH" + "Violation" -> ShaclSeverity.Violation
            "$SH" + "Warning" -> ShaclSeverity.Warning
            "$SH" + "Info" -> ShaclSeverity.Info
            else -> null
        }
        private fun severityIri(severity: ShaclSeverity): Iri = Iri("$SH" + severity.name)
    }
}
