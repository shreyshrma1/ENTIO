package com.entio.semantic

import com.entio.core.BlankNodeResource
import com.entio.core.EntioResult
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.LoadedOntology
import com.entio.core.OntologyFormat
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.RdfTerm
import com.entio.core.ResolvedOntologySource
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource
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

        val triples = try {
            model.toGraphTriples()
        } catch (exception: UnsupportedRdfNodeException) {
            return failure(
                source = source,
                code = "unsupported-rdf-node",
                message = "Ontology source '${source.id}' contains an RDF node Entio cannot represent.",
                cause = exception,
            )
        }

        return EntioResult.Success(
            LoadedOntology(
                source = source,
                graph = GraphState(
                    triples = triples,
                ),
            ),
        )
    }

    private fun Model.toGraphTriples(): Set<GraphTriple> =
        listStatements()
            .asSequence()
            .map { statement ->
                GraphTriple(
                    subject = statement.subject.toRdfResource(),
                    predicate = Iri(statement.predicate.uri),
                    objectTerm = statement.`object`.toRdfTerm(),
                )
            }
            .toSet()

    private fun Resource.toRdfResource(): RdfResource =
        when {
            isURIResource -> Iri(uri)
            isAnon -> BlankNodeResource(id = id.labelString)
            else -> throw UnsupportedRdfNodeException("Unsupported RDF resource: $this")
        }

    private fun RDFNode.toRdfTerm(): RdfTerm =
        when {
            isURIResource || isAnon -> asResource().toRdfResource()
            isLiteral -> {
                val literal = asLiteral()
                RdfLiteral(
                    lexicalForm = literal.lexicalForm,
                    datatypeIri = literal.datatypeURI?.let(::Iri),
                    languageTag = literal.language.takeIf { it.isNotBlank() },
                )
            }
            else -> throw UnsupportedRdfNodeException("Unsupported RDF node: $this")
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

    private class UnsupportedRdfNodeException(
        message: String,
    ) : RuntimeException(message)
}
