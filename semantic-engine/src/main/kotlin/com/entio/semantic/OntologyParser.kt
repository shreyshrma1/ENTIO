package com.entio.semantic

import com.entio.core.EntioResult
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.LoadedOntology
import com.entio.core.OntologyFormat
import com.entio.core.ResolvedOntologySource
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RiotException

public class OntologyParser {
    public fun parse(source: ResolvedOntologySource): EntioResult<LoadedOntology> {
        if (source.format != OntologyFormat.Turtle) {
            return failure(
                source = source,
                code = "unsupported-ontology-format",
                message = "Ontology source '${source.id}' uses unsupported format '${source.format}'.",
            )
        }

        val model = ModelFactory.createDefaultModel()

        try {
            RDFDataMgr.read(
                model,
                source.path.toUri().toString(),
                Lang.TURTLE,
            )
        } catch (exception: RiotException) {
            return failure(
                source = source,
                code = "invalid-turtle",
                message = "Ontology source '${source.id}' is not valid Turtle.",
                cause = exception,
            )
        } catch (exception: RuntimeException) {
            return failure(
                source = source,
                code = "ontology-parse-failed",
                message = "Ontology source '${source.id}' could not be parsed.",
                cause = exception,
            )
        }

        return EntioResult.Success(
            LoadedOntology(
                source = source,
                graph = GraphState(
                    triples = model.toGraphTriples(),
                ),
            ),
        )
    }

    private fun Model.toGraphTriples(): Set<GraphTriple> =
        listStatements()
            .asSequence()
            .map { statement ->
                GraphTriple(
                    subject = Iri(statement.subject.uri),
                    predicate = Iri(statement.predicate.uri),
                    objectValue = statement.`object`.stableValue(),
                )
            }
            .toSet()

    private fun RDFNode.stableValue(): String =
        when {
            isURIResource -> asResource().uri
            isLiteral -> asLiteral().lexicalForm
            isAnon -> asResource().id.labelString
            else -> toString()
        }

    private fun failure(
        source: ResolvedOntologySource,
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
                    source = source.id,
                ),
            ),
            cause = cause,
        )
}
