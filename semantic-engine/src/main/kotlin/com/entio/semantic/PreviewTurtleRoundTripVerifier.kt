package com.entio.semantic

import com.entio.core.BlankNodeResource
import com.entio.core.ChangePreview
import com.entio.core.EntioResult
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.OntologyFormat
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.RdfTerm
import com.entio.core.ResolvedOntologySource
import com.entio.core.SemanticEquivalenceResult
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import org.apache.jena.datatypes.TypeMapper
import org.apache.jena.rdf.model.AnonId
import org.apache.jena.rdf.model.Literal
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RiotException

public class PreviewTurtleRoundTripVerifier(
    private val parser: OntologyParser = OntologyParser(),
) {
    public fun verify(preview: ChangePreview): EntioResult<SemanticEquivalenceResult> {
        val previewModel = try {
            preview.graph.toModel()
        } catch (exception: RuntimeException) {
            return failure(
                code = "preview-graph-serialization-failed",
                message = "Preview graph could not be converted to an RDF model.",
                cause = exception,
            )
        }
        val turtleBytes = when (val result = serializeModel(previewModel)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        val reparsedGraph = when (val result = reparseTurtle(turtleBytes)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        val reparsedModel = try {
            reparsedGraph.toModel()
        } catch (exception: RuntimeException) {
            return failure(
                code = "reparsed-graph-conversion-failed",
                message = "Reparsed Turtle graph could not be converted to an RDF model.",
                cause = exception,
            )
        }

        return if (previewModel.isIsomorphicWith(reparsedModel)) {
            EntioResult.Success(SemanticEquivalenceResult.Equivalent)
        } else {
            EntioResult.Success(
                SemanticEquivalenceResult.NotEquivalent(
                    reason = "Reparsed Turtle graph is not semantically equivalent to the preview graph.",
                ),
            )
        }
    }

    public fun serializeToTemporaryTurtle(preview: ChangePreview): EntioResult<ResolvedOntologySource> {
        val model = try {
            preview.graph.toModel()
        } catch (exception: RuntimeException) {
            return failure(
                code = "preview-graph-serialization-failed",
                message = "Preview graph could not be converted to an RDF model.",
                cause = exception,
            )
        }
        val turtleBytes = when (val result = serializeModel(model)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }

        return try {
            val path = Files.createTempFile("entio-preview-", ".ttl")
            Files.write(path, turtleBytes)
            EntioResult.Success(
                ResolvedOntologySource(
                    id = TEMPORARY_PREVIEW_SOURCE_ID,
                    path = path,
                    format = OntologyFormat.Turtle,
                ),
            )
        } catch (exception: IOException) {
            failure(
                code = "temporary-turtle-write-failed",
                message = "Temporary Turtle preview file could not be written.",
                cause = exception,
            )
        }
    }

    private fun serializeModel(model: Model): EntioResult<ByteArray> =
        try {
            val output = ByteArrayOutputStream()
            RDFDataMgr.write(output, model, Lang.TURTLE)
            EntioResult.Success(output.toByteArray())
        } catch (exception: RiotException) {
            failure(
                code = "temporary-turtle-serialization-failed",
                message = "Preview graph could not be serialized to Turtle.",
                cause = exception,
            )
        } catch (exception: RuntimeException) {
            failure(
                code = "temporary-turtle-serialization-failed",
                message = "Preview graph could not be serialized to Turtle.",
                cause = exception,
            )
        }

    private fun reparseTurtle(turtleBytes: ByteArray): EntioResult<GraphState> =
        try {
            val path = Files.createTempFile("entio-preview-reparse-", ".ttl")
            Files.write(path, turtleBytes)
            val source = ResolvedOntologySource(
                id = TEMPORARY_PREVIEW_SOURCE_ID,
                path = path,
                format = OntologyFormat.Turtle,
            )

            when (val result = parser.parse(source)) {
                is EntioResult.Failure -> EntioResult.Failure(
                    message = "Temporary Turtle could not be reparsed.",
                    issues = if (result.issues.isNotEmpty()) {
                        result.issues
                    } else {
                        listOf(
                            ValidationIssue(
                                severity = ValidationSeverity.Error,
                                code = "temporary-turtle-reparse-failed",
                                message = "Temporary Turtle could not be reparsed.",
                                source = TEMPORARY_PREVIEW_SOURCE_ID,
                            ),
                        )
                    },
                    cause = result.cause,
                )
                is EntioResult.Success -> EntioResult.Success(result.value.graph)
            }
        } catch (exception: IOException) {
            failure(
                code = "temporary-turtle-reparse-failed",
                message = "Temporary Turtle preview file could not be prepared for reparsing.",
                cause = exception,
            )
        }

    public fun compareSemanticEquivalence(
        expected: GraphState,
        actual: GraphState,
    ): SemanticEquivalenceResult =
        try {
            if (expected.toModel().isIsomorphicWith(actual.toModel())) {
                SemanticEquivalenceResult.Equivalent
            } else {
                SemanticEquivalenceResult.NotEquivalent(
                    reason = "Actual graph is not semantically equivalent to the expected graph.",
                )
            }
        } catch (exception: RuntimeException) {
            SemanticEquivalenceResult.Failed(
                reason = exception.message ?: "Semantic equivalence check failed.",
            )
        }

    private fun GraphState.toModel(): Model {
        val model = ModelFactory.createDefaultModel()
        triples.forEach { triple -> model.add(triple.toStatement(model)) }
        return model
    }

    private fun GraphTriple.toStatement(model: Model) =
        model.createStatement(
            subjectResource.toJenaResource(model),
            model.createProperty(predicate.value),
            objectTerm.toJenaNode(model),
        )

    private fun RdfResource.toJenaResource(model: Model): Resource =
        when (this) {
            is Iri -> model.createResource(value)
            is BlankNodeResource -> model.createResource(AnonId.create(id))
        }

    private fun RdfTerm.toJenaNode(model: Model): RDFNode =
        when (this) {
            is RdfResource -> toJenaResource(model)
            is RdfLiteral -> toJenaLiteral(model)
        }

    private fun RdfLiteral.toJenaLiteral(model: Model): Literal {
        val language = languageTag
        val datatype = datatypeIri

        return when {
            language != null -> model.createLiteral(lexicalForm, language)
            datatype != null -> model.createTypedLiteral(
                lexicalForm,
                TypeMapper.getInstance().getSafeTypeByName(datatype.value),
            )
            else -> model.createLiteral(lexicalForm)
        }
    }

    private fun failure(
        code: String,
        message: String,
        cause: Throwable? = null,
    ): EntioResult.Failure =
        EntioResult.Failure(
            message = message,
            issues = listOf(
                ValidationIssue(
                    severity = ValidationSeverity.Error,
                    code = code,
                    message = message,
                    source = TEMPORARY_PREVIEW_SOURCE_ID,
                ),
            ),
            cause = cause,
        )

    private companion object {
        private const val TEMPORARY_PREVIEW_SOURCE_ID: String = "preview"
    }
}
