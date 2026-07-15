package com.entio.semantic

import com.entio.core.EntioResult
import com.entio.core.GraphState
import com.entio.core.LoadedOntology
import com.entio.core.OntologyFormat
import com.entio.core.ResolvedOntologySource
import com.entio.core.ShaclGraphIdentity
import com.entio.core.ShaclSeverity
import com.entio.core.ShaclValidationMode
import com.entio.core.ShaclValidationStatus
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ShaclValidationServiceTest {
    private val parser = OntologyParser()
    private val service = ShaclValidationService()

    @Test
    fun normalizesSupportedConstraintResultsAndSeveritiesDeterministically(): Unit {
        val graphs = graphSet(
            data = """
                @prefix ex: <https://example.com/> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                ex:Account1 a ex:Account ; ex:code "x" ; ex:amount 1 ; ex:owner ex:Invoice ; ex:status "pending" ; ex:unexpected "value" .
            """.trimIndent(),
            shapes = """
                @prefix ex: <https://example.com/> .
                @prefix sh: <http://www.w3.org/ns/shacl#> .
                @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
                ex:AccountShape a sh:NodeShape ; sh:targetClass ex:Account ; sh:closed true ; sh:message "Account is invalid" ;
                    sh:property [ sh:path ex:code ; sh:minCount 1 ; sh:datatype xsd:string ; sh:minLength 3 ; sh:maxLength 5 ; sh:pattern "^[A-Z]" ; sh:in ( "ABC" "DEF" ) ; sh:severity sh:Warning ] ;
                    sh:property [ sh:path ex:amount ; sh:minInclusive 5 ; sh:maxExclusive 10 ] ;
                    sh:property [ sh:path ex:owner ; sh:class ex:Person ] ;
                    sh:property [ sh:path ex:status ; sh:hasValue "approved" ] .
            """.trimIndent(),
        )

        val first = assertIs<EntioResult.Success<com.entio.core.ShaclValidationReport>>(
            service.validate(graphs),
        ).value
        val second = assertIs<EntioResult.Success<com.entio.core.ShaclValidationReport>>(
            service.validate(graphs),
        ).value

        assertEquals(ShaclValidationStatus.Completed, first.status)
        assertEquals(ShaclValidationMode.AssertedOnly, first.mode)
        assertEquals(first, second)
        assertTrue(first.results.isNotEmpty())
        assertTrue(first.results.all { it.resultId.isNotBlank() })
        assertEquals(first.results.map { it.resultId }.sorted(), first.results.map { it.resultId })
        assertTrue(first.results.any { it.severity == ShaclSeverity.Warning })
        assertTrue(first.results.any { it.constraint.name.contains("Count") || it.constraint.name == "Closed" })
        assertTrue(first.results.any { it.value != null })
        assertEquals(listOf("data"), first.graphIdentity.dataSourceIds)
        assertNotEquals("", first.graphIdentity.dataGraphFingerprint)
        assertNotEquals("", first.graphIdentity.shapesGraphFingerprint)
    }

    @Test
    fun inferredModeIsExplicitAndCanChangeOnlyTheValidatedGraph(): Unit {
        val graphs = graphSet(
            data = """
                @prefix ex: <https://example.com/> .
                ex:Shrey a ex:Customer .
            """.trimIndent(),
            shapes = """
                @prefix ex: <https://example.com/> . @prefix sh: <http://www.w3.org/ns/shacl#> .
                ex:CustomerShape a sh:NodeShape ; sh:targetClass ex:Customer ;
                    sh:property [ sh:path ex:receivedInvoice ; sh:minCount 1 ] .
            """.trimIndent(),
        )
        val inferred = graph("""
            @prefix ex: <https://example.com/> .
            ex:Shrey ex:receivedInvoice ex:Invoice20874 .
        """.trimIndent())

        val asserted = assertIs<EntioResult.Success<com.entio.core.ShaclValidationReport>>(
            service.validate(graphs, ShaclValidationMode.AssertedOnly),
        ).value
        val inferredReport = assertIs<EntioResult.Success<com.entio.core.ShaclValidationReport>>(
            service.validate(graphs, ShaclValidationMode.AssertedAndInferred, inferred),
        ).value
        val unavailable = assertIs<EntioResult.Success<com.entio.core.ShaclValidationReport>>(
            service.validate(graphs, ShaclValidationMode.AssertedAndInferred),
        ).value

        assertTrue(asserted.results.isNotEmpty())
        assertTrue(inferredReport.results.isEmpty())
        assertEquals(ShaclValidationStatus.Unavailable, unavailable.status)
        assertTrue(unavailable.errors.single().contains("complete inferred graph"))
        assertNotEquals(asserted.graphIdentity.dataGraphFingerprint, inferredReport.graphIdentity.dataGraphFingerprint)
    }

    @Test
    fun malformedOrUnsupportedShapesFailWithoutMutatingGraphs(): Unit {
        val shapes = graph("""
            @prefix ex: <https://example.com/> . @prefix sh: <http://www.w3.org/ns/shacl#> .
            ex:AccountShape a sh:NodeShape ; sh:targetClass ex:Account ;
                sh:property [ sh:path [ sh:alternativePath ( ex:a ex:b ) ] ] .
        """.trimIndent())
        val graphs = ShaclGraphSet(
            dataGraph = GraphState(),
            shapesGraph = shapes,
            identity = ShaclGraphIdentity(emptyList(), listOf("shapes"), "data", "shapes"),
        )

        val result = assertIs<EntioResult.Success<com.entio.core.ShaclValidationReport>>(
            service.validate(graphs),
        ).value

        assertEquals(ShaclValidationStatus.Failed, result.status)
        assertTrue(result.errors.single().contains("complex", ignoreCase = true))
        assertTrue(graphs.shapesGraph.triples.isNotEmpty())
    }

    private fun graphSet(data: String, shapes: String): ShaclGraphSet = ShaclGraphSet(
        dataGraph = graph(data),
        shapesGraph = graph(shapes),
        identity = ShaclGraphIdentity(
            dataSourceIds = listOf("data"),
            shapesSourceIds = listOf("shapes"),
            dataGraphFingerprint = "data-input",
            shapesGraphFingerprint = "shapes-input",
        ),
    )

    private fun graph(content: String): GraphState {
        val path = Files.createTempFile("entio-shacl-validation", ".ttl")
        path.writeText(content)
        return assertIs<EntioResult.Success<LoadedOntology>>(
            parser.parse(ResolvedOntologySource("fixture", path, OntologyFormat.Turtle)),
        ).value.graph
    }
}
