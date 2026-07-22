package com.entio.semantic

import com.entio.core.EntioProject
import com.entio.core.EntioProjectConfig
import com.entio.core.EntioResult
import com.entio.core.GraphState
import com.entio.core.LoadedOntology
import com.entio.core.OntologyFormat
import com.entio.core.OntologyGraphInitialQuery
import com.entio.core.OntologySourceReference
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Phase9ScalePerformanceTest {
    private val parser = OntologyParser()
    private val service = OntologyGraphService()

    @Test
    fun deterministicScaleFixturesStayBounded(): Unit {
        listOf(12, 120, 500, 1_000).forEach { entityCount ->
            val page = service.initial(fixture(entityCount), OntologyGraphInitialQuery(setOf("scale")))
            assertEquals(minOf(entityCount, 75), page.nodes.size)
            assertTrue(page.edges.size <= 150)
            assertEquals(entityCount, page.totalNodeCount)
            assertEquals(entityCount > 75, page.nextCursor != null)
        }
    }

    @Test
    fun oneThousandEntityInitialResponseMeetsFiveRunGate(): Unit {
        val project = fixture(1_000)
        service.initial(project, OntologyGraphInitialQuery(setOf("scale")))
        val runs = List(5) {
            measureTimeMillis { service.initial(project, OntologyGraphInitialQuery(setOf("scale"))) }
        }
        val median = runs.sorted()[runs.size / 2]
        val worst = runs.max()
        println("phase9-api-runs-ms=${runs.joinToString(",")} median=$median worst=$worst")
        assertTrue(median <= 500, "1,000-entity median was ${median}ms")
        assertTrue(worst <= 1_000, "1,000-entity worst run was ${worst}ms")
    }

    private fun fixture(entityCount: Int): EntioProject {
        val classes = (0 until entityCount).joinToString("\n") { index ->
            val parent = if (index == 0) "" else "; rdfs:subClassOf ex:C${index - 1}"
            "ex:C$index a owl:Class $parent ."
        }
        val content = """
            @prefix ex: <https://example.com/scale/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            $classes
        """.trimIndent()
        val ontology = assertIs<EntioResult.Success<LoadedOntology>>(
            parser.parse(SemanticEngineTestFixtures.resolvedSource(content, "scale")),
        ).value
        return EntioProject(
            config = EntioProjectConfig("phase-9-scale-$entityCount", listOf(OntologySourceReference("scale", ontology.source.path.toString(), OntologyFormat.Turtle))),
            resolvedSources = listOf(ontology.source),
            ontologies = listOf(ontology),
            symbols = emptyList(),
            graph = GraphState(ontology.graph.triples),
        )
    }
}
