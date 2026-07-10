package com.entio.core

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CoreTypesConstructionTest {
    @Test
    fun constructsProjectAndOntologyObjects(): Unit {
        val source = OntologySourceReference(
            id = "simple",
            path = "ontology/simple.ttl",
            format = OntologyFormat.Turtle,
        )
        val config = EntioProjectConfig(
            name = "simple-ontology",
            ontologySources = listOf(source),
        )
        val resolvedSource = ResolvedOntologySource(
            id = source.id,
            path = Path.of("ontology/simple.ttl"),
            format = source.format,
        )
        val triple = GraphTriple(
            subject = Iri("https://example.com/Customer"),
            predicate = Iri("http://www.w3.org/2000/01/rdf-schema#label"),
            objectValue = "Customer",
        )
        val ontology = LoadedOntology(
            source = resolvedSource,
            graph = GraphState(triples = setOf(triple)),
        )
        val symbol = LoadedSymbol(
            iri = triple.subject,
            label = "Customer",
            kind = SymbolKind.Class,
            sourceId = source.id,
        )
        val project = EntioProject(
            config = config,
            resolvedSources = listOf(resolvedSource),
            ontologies = listOf(ontology),
            symbols = listOf(symbol),
            graph = ontology.graph,
        )

        assertEquals("simple-ontology", project.config.name)
        assertEquals(setOf(triple), project.ontologies.single().graph.triples)
        assertEquals(SymbolKind.Class, project.symbols.single().kind)
        assertEquals(setOf(triple), project.graph.triples)
    }

    @Test
    fun representsSuccessAndFailureResults(): Unit {
        val success: EntioResult<String> = EntioResult.Success("loaded")
        val failure: EntioResult<String> = EntioResult.Failure(
            message = "Project is invalid.",
            issues = listOf(
                ValidationIssue(
                    severity = ValidationSeverity.Error,
                    code = "missing-entio-yaml",
                    message = "Missing entio.yaml.",
                    source = "entio.yaml",
                ),
            ),
        )

        assertIs<EntioResult.Success<String>>(success)
        assertIs<EntioResult.Failure>(failure)
    }
}
