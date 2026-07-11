package com.entio.cli

import com.entio.core.BlankNodeResource
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.diff.GraphDiffer
import com.entio.semantic.ProjectLoader
import com.entio.validation.ProjectValidator
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Phase15EndToEndRegressionTest {
    private val projectLoader = ProjectLoader()
    private val projectValidator = ProjectValidator()
    private val graphDiffer = GraphDiffer()

    @Test
    fun phase15CoreEngineFlowPreservesRdfTermsAndCliBehavior(): Unit {
        val beforeProjectRoot = projectWithOntology(
            """
            @prefix ex: <https://example.com/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

            ex:Customer a owl:Class ;
              rdfs:label "Customer"@en ;
              ex:score "42"^^xsd:integer ;
              ex:hasDetail [ ex:code "C-001" ] .

            ex:Account a owl:Class ;
              rdfs:label "Account" .

            ex:ownsAccount a owl:ObjectProperty ;
              rdfs:label "owns account" .

            [] ex:note "anonymous note" .
            """.trimIndent(),
        )
        val afterProjectRoot = copyProject(beforeProjectRoot)
        afterProjectRoot.resolve("ontology/simple.ttl").writeText(
            Files.readString(afterProjectRoot.resolve("ontology/simple.ttl")) + """

            ex:Invoice a owl:Class ;
              rdfs:label "Invoice"@en .
            """.trimIndent() + "\n",
        )

        val beforeProject = assertIs<EntioResult.Success<EntioProject>>(
            projectLoader.loadProject(beforeProjectRoot),
        ).value
        val afterProject = assertIs<EntioResult.Success<EntioProject>>(
            projectLoader.loadProject(afterProjectRoot),
        ).value

        assertCorrectedTermsSurviveCombinedGraphAssembly(beforeProject)
        assertEquals(3, beforeProject.symbols.size)
        assertEquals(
            listOf(
                "https://example.com/Account",
                "https://example.com/Customer",
                "https://example.com/ownsAccount",
            ),
            beforeProject.symbols.map { it.iri.value },
        )

        val validation = projectValidator.validateProject(beforeProjectRoot)
        assertTrue(validation.ok)
        assertEquals(emptyList(), validation.issues)

        val semanticDiff = graphDiffer.diff(beforeProject.graph, afterProject.graph)
        assertTrue(semanticDiff.entries.any { entry ->
            entry.description.contains("Added triple") &&
                entry.description.contains("https://example.com/Invoice")
        })

        val validateCli = runCli("validate", beforeProjectRoot.toString())
        assertEquals(0, validateCli.exitCode)
        assertEquals("Validation: valid\n", validateCli.out)
        assertEquals("", validateCli.err)

        val symbolsCli = runCli("symbols", beforeProjectRoot.toString())
        assertEquals(0, symbolsCli.exitCode)
        assertTrue(symbolsCli.out.contains("Class https://example.com/Customer \"Customer\" [simple]"))
        assertTrue(symbolsCli.out.contains("Property https://example.com/ownsAccount \"owns account\" [simple]"))
        assertEquals("", symbolsCli.err)

        val diffCli = runCli("diff", beforeProjectRoot.toString(), afterProjectRoot.toString())
        assertEquals(1, diffCli.exitCode)
        assertTrue(diffCli.out.contains("Added triple"))
        assertTrue(diffCli.out.contains("https://example.com/Invoice"))
        assertEquals("", diffCli.err)
    }

    private fun assertCorrectedTermsSurviveCombinedGraphAssembly(project: EntioProject): Unit {
        assertTrue(project.graph.triples.any { triple -> triple.subjectResource is BlankNodeResource })
        assertTrue(project.graph.triples.any { triple -> triple.objectTerm is BlankNodeResource })

        val scoreLiteral = assertIs<RdfLiteral>(
            project.graph.triples.single { triple ->
                triple.subjectResource == Iri("https://example.com/Customer") &&
                    triple.predicate == Iri("https://example.com/score")
            }.objectTerm,
        )
        assertEquals("42", scoreLiteral.lexicalForm)
        assertEquals(Iri("http://www.w3.org/2001/XMLSchema#integer"), scoreLiteral.datatypeIri)

        val labelLiteral = assertIs<RdfLiteral>(
            project.graph.triples.single { triple ->
                triple.subjectResource == Iri("https://example.com/Customer") &&
                    triple.predicate == Iri("http://www.w3.org/2000/01/rdf-schema#label")
            }.objectTerm,
        )
        assertEquals("Customer", labelLiteral.lexicalForm)
        assertEquals("en", labelLiteral.languageTag)

        assertEquals(
            project.ontologies.flatMap { ontology -> ontology.graph.triples }.toSet(),
            project.graph.triples,
        )
    }

    private fun runCli(vararg args: String): CliRun {
        val out = StringWriter()
        val err = StringWriter()

        val exitCode = EntioCli().execute(
            args = args.toList().toTypedArray(),
            out = PrintWriter(out, true),
            err = PrintWriter(err, true),
        )

        return CliRun(
            exitCode = exitCode,
            out = out.toString(),
            err = err.toString(),
        )
    }

    private fun projectWithOntology(turtle: String): Path {
        val projectRoot = Files.createTempDirectory("entio-phase-15-e2e")
        val ontologyDirectory = projectRoot.resolve("ontology")
        ontologyDirectory.createDirectories()
        projectRoot.resolve("entio.yaml").writeText(
            """
            name: phase-15-e2e
            ontologySources:
              - id: simple
                path: ontology/simple.ttl
                format: turtle
            """.trimIndent(),
        )
        ontologyDirectory.resolve("simple.ttl").writeText(turtle)
        return projectRoot
    }

    private fun copyProject(source: Path): Path {
        val target = Files.createTempDirectory("entio-phase-15-e2e-after")
        val paths = Files.walk(source)

        try {
            paths.forEach { path ->
                val targetPath = target.resolve(source.relativize(path).toString())

                if (Files.isDirectory(path)) {
                    Files.createDirectories(targetPath)
                } else {
                    Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        } finally {
            paths.close()
        }

        return target
    }

    private data class CliRun(
        val exitCode: Int,
        val out: String,
        val err: String,
    )
}
