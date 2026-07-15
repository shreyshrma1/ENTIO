package com.entio.semantic

import com.entio.core.BlankNodeResource
import com.entio.core.EntioResult
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.RdfTerm
import com.entio.core.ShaclConstraintKind
import com.entio.core.ShaclGraphIdentity
import com.entio.core.ShaclPath
import com.entio.core.ShaclSeverity
import com.entio.core.ShaclShapeId
import com.entio.core.ShaclValidationMode
import com.entio.core.ShaclValidationReport
import com.entio.core.ShaclValidationResult
import com.entio.core.ShaclValidationStatus
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.apache.jena.graph.Graph
import org.apache.jena.graph.Node
import org.apache.jena.graph.NodeFactory
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.shacl.ShaclValidator
import org.apache.jena.shacl.vocabulary.SHACL
import org.apache.jena.sparql.path.P_Link

/**
 * Runs Jena SHACL while exposing only Entio-owned validation contracts.
 */
public class ShaclValidationService {
    public fun validate(
        graphs: ShaclGraphSet,
        mode: ShaclValidationMode = ShaclValidationMode.AssertedOnly,
        inferredGraph: GraphState? = null,
    ): EntioResult<ShaclValidationReport> {
        if (mode == ShaclValidationMode.AssertedAndInferred && inferredGraph == null) {
            return EntioResult.Success(
                ShaclValidationReport(
                    status = ShaclValidationStatus.Unavailable,
                    mode = mode,
                    graphIdentity = graphs.identity,
                    errors = listOf("Asserted-plus-inferred validation requires a complete inferred graph."),
                ),
            )
        }

        val dataGraph = if (mode == ShaclValidationMode.AssertedAndInferred) {
            GraphState(graphs.dataGraph.triples + inferredGraph!!.triples)
        } else {
            graphs.dataGraph
        }
        val identity = graphs.identity.copy(dataGraphFingerprint = fingerprint(dataGraph))
        val unsupported = unsupportedShapeMessage(graphs.shapesGraph)
        if (unsupported != null) return failedReport(mode, identity, unsupported)

        return try {
            val shapesModel = graphs.shapesGraph.toModel()
            val dataModel = dataGraph.toModel()
            val shapes = ShaclValidator.get().parse(shapesModel.graph)
            val report = ShaclValidator.get().validate(shapes, dataModel.graph)
            val entries = report.entries.sortedWith(
                compareBy({ it.focusNode()?.canonicalNodeKey() ?: "" }, { it.source()?.canonicalNodeKey() ?: "" }, { it.message() }),
            )
            val normalized = mutableListOf<ShaclValidationResult>()
            entries.forEach { entry ->
                val shapeNode = entry.source() ?: return@forEach
                val constraint = constraintKind(entry.sourceConstraintComponent())
                    ?: throw UnsupportedShapeException("Jena returned an unsupported SHACL constraint component.")
                val focusNode = shapeNodeResource(entry.focusNode())
                    ?: throw UnsupportedShapeException("SHACL returned a non-resource focus node.")
                val shape = shapeId(shapeNode, graphs.shapesGraph, graphs.identity.shapesSourceIds.singleOrNull())
                val path = entry.resultPath().toEntioPath()
                val value = entry.value()?.toRdfTerm()
                val severity = entry.severity()?.level()?.uri.toShaclSeverity()
                    ?: throw UnsupportedShapeException("SHACL returned an unsupported severity.")
                val sourceId = graphs.identity.dataSourceIds.singleOrNull()
                val message = entry.message().takeUnless { it.isBlank() } ?: "SHACL validation failed."
                normalized += ShaclValidationResult(
                    severity = severity,
                    message = message,
                    focusNode = focusNode,
                    path = path,
                    shape = shape,
                    constraint = constraint,
                    value = value,
                    sourceId = sourceId,
                    resultId = resultId(severity, message, focusNode, path, shape, constraint, value, sourceId),
                )
            }
            EntioResult.Success(
                ShaclValidationReport(
                    status = ShaclValidationStatus.Completed,
                    mode = mode,
                    graphIdentity = identity,
                    results = normalized.sortedBy { it.resultId },
                ),
            )
        } catch (exception: UnsupportedShapeException) {
            failedReport(mode, identity, exception.message ?: "Unsupported SHACL shape.")
        } catch (exception: RuntimeException) {
            failedReport(mode, identity, "SHACL validation failed: ${exception.message ?: exception::class.simpleName}.")
        }
    }

    private fun unsupportedShapeMessage(graph: GraphState): String? {
        val unsupportedPredicates = setOf(
            SHACL.nodeKind,
            SHACL.languageIn,
            SHACL.uniqueLang,
            SHACL.equals,
            SHACL.disjoint,
            SHACL.lessThan,
            SHACL.lessThanOrEquals,
            SHACL.node,
            SHACL.and,
            SHACL.or,
            SHACL.xone,
            SHACL.not,
            SHACL.qualifiedValueShape,
            SHACL.qualifiedMinCount,
            SHACL.qualifiedMaxCount,
            SHACL.sparql,
            SHACL.js,
        )
        if (graph.triples.any { it.predicate.value in unsupportedPredicates.map(Node::getURI) }) {
            return "The SHACL shapes graph contains an unsupported constraint form."
        }
        val complexPath = graph.triples.firstOrNull { it.predicate.value == SHACL.path.getURI() && it.objectTerm !is Iri }
        return if (complexPath != null) "Complex SHACL property paths are not supported." else null
    }

    private fun failedReport(
        mode: ShaclValidationMode,
        identity: ShaclGraphIdentity,
        message: String,
    ): EntioResult<ShaclValidationReport> = EntioResult.Success(
        ShaclValidationReport(
            status = ShaclValidationStatus.Failed,
            mode = mode,
            graphIdentity = identity,
            errors = listOf(message),
        ),
    )

    private fun constraintKind(component: Node?): ShaclConstraintKind? = when (component?.uri?.substringAfterLast('#')) {
        "MinCountConstraintComponent" -> ShaclConstraintKind.MinCount
        "MaxCountConstraintComponent" -> ShaclConstraintKind.MaxCount
        "DatatypeConstraintComponent" -> ShaclConstraintKind.Datatype
        "ClassConstraintComponent" -> ShaclConstraintKind.Class
        "InConstraintComponent" -> ShaclConstraintKind.In
        "HasValueConstraintComponent" -> ShaclConstraintKind.HasValue
        "MinInclusiveConstraintComponent" -> ShaclConstraintKind.MinInclusive
        "MaxInclusiveConstraintComponent" -> ShaclConstraintKind.MaxInclusive
        "MinExclusiveConstraintComponent" -> ShaclConstraintKind.MinExclusive
        "MaxExclusiveConstraintComponent" -> ShaclConstraintKind.MaxExclusive
        "PatternConstraintComponent" -> ShaclConstraintKind.Pattern
        "MinLengthConstraintComponent" -> ShaclConstraintKind.MinLength
        "MaxLengthConstraintComponent" -> ShaclConstraintKind.MaxLength
        "ClosedConstraintComponent" -> ShaclConstraintKind.Closed
        else -> null
    }

    private fun Node?.toRdfTerm(): RdfTerm? = this?.let { node ->
        when {
            node.isURI -> Iri(node.uri)
            node.isBlank -> BlankNodeResource(node.blankNodeLabel)
            node.isLiteral -> RdfLiteral(node.literalLexicalForm, node.literalDatatypeURI?.let(::Iri), node.literalLanguage.takeIf { it.isNotBlank() })
            else -> null
        }
    }

    private fun shapeNodeResource(node: Node?): RdfResource? = node?.toRdfTerm() as? RdfResource

    private fun shapeId(node: Node, shapesGraph: GraphState, sourceId: String?): ShaclShapeId {
        val resource = shapeNodeResource(node) ?: Iri("urn:entio:shacl:validation:unknown")
        return ShaclShapeId(
            iri = if (resource is Iri) resource else Iri("urn:entio:shacl:validation:${sha256(resource.value + shapesGraph.triples.size)}"),
            sourceId = sourceId ?: "shacl-validation",
        )
    }

    private fun org.apache.jena.sparql.path.Path?.toEntioPath(): ShaclPath? = when (this) {
        is P_Link -> getNode().takeIf { it.isURI }?.let { ShaclPath.DirectProperty(Iri(it.uri)) }
        else -> null
    }

    private fun String?.toShaclSeverity(): ShaclSeverity? = when (this) {
        SHACL.Violation.getURI() -> ShaclSeverity.Violation
        SHACL.Warning.getURI() -> ShaclSeverity.Warning
        SHACL.Info.getURI() -> ShaclSeverity.Info
        else -> null
    }

    private fun resultId(
        severity: ShaclSeverity,
        message: String,
        focusNode: RdfResource,
        path: ShaclPath?,
        shape: ShaclShapeId,
        constraint: ShaclConstraintKind,
        value: RdfTerm?,
        sourceId: String?,
    ): String = sha256(listOf(severity, message, focusNode.value, path, shape, constraint, value, sourceId).joinToString("|"))

    private fun fingerprint(graph: GraphState): String = sha256(
        graph.triples.sortedWith(compareBy<GraphTriple>({ it.subjectResource.value }, { it.predicate.value }, { it.objectTerm.canonicalNodeKey() }))
            .joinToString("\n") { "${it.subjectResource.value}|${it.predicate.value}|${it.objectTerm.canonicalNodeKey()}" },
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun GraphState.toModel(): Model {
        val model = ModelFactory.createDefaultModel()
        triples.forEach { triple ->
            model.add(triple.subjectResource.toJenaResource(model), model.createProperty(triple.predicate.value), triple.objectTerm.toJenaNode(model))
        }
        return model
    }

    private fun RdfResource.toJenaResource(model: Model) = when (this) {
        is Iri -> model.createResource(value)
        is BlankNodeResource -> model.createResource(org.apache.jena.rdf.model.AnonId(id))
    }

    private fun RdfTerm.toJenaNode(model: Model) = when (this) {
        is RdfResource -> toJenaResource(model)
        is RdfLiteral -> {
            val datatype = datatypeIri
            when {
                languageTag != null -> model.createLiteral(lexicalForm, languageTag)
                datatype != null -> model.createTypedLiteral(lexicalForm, datatype.value)
                else -> model.createLiteral(lexicalForm)
            }
        }
    }

    private fun RdfTerm.canonicalNodeKey(): String = when (this) {
        is Iri -> "iri:$value"
        is BlankNodeResource -> "blank:$id"
        is RdfLiteral -> "literal:$lexicalForm|${datatypeIri?.value}|$languageTag"
    }

    private fun Node.canonicalNodeKey(): String = when {
        isURI -> "iri:$uri"
        isBlank -> "blank:$blankNodeLabel"
        isLiteral -> "literal:$literalLexicalForm|$literalDatatypeURI|$literalLanguage"
        else -> toString()
    }

    private class UnsupportedShapeException(message: String) : RuntimeException(message)
}
