package com.entio.semantic

import com.entio.core.ChangeSet
import com.entio.core.EntioResult
import com.entio.core.GraphState
import com.entio.core.Iri
import com.entio.core.LoadedOntology
import com.entio.core.OntologyFormat
import com.entio.core.ResolvedOntologySource
import com.entio.core.ShaclConstraintKind
import com.entio.core.ShaclGraphRole
import com.entio.core.ShaclPath
import com.entio.core.ShaclPropertyShape
import com.entio.core.ShaclShapeId
import com.entio.core.ShaclTarget
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ShaclShapeAuthoringServiceTest {
    private val parser = OntologyParser()
    private val loader = ShaclGraphLoader()
    private val authoring = ShaclShapeAuthoringService()

    @Test
    fun separatesExplicitDataAndShapesRolesAndFingerprintsBothGraphs(): Unit {
        val data = loaded("data", setOf(ShaclGraphRole.Data), """
            @prefix ex: <https://example.com/> . ex:Account a ex:AccountClass .
        """.trimIndent())
        val shapes = loaded("shapes", setOf(ShaclGraphRole.Shapes), """
            @prefix ex: <https://example.com/> . @prefix sh: <http://www.w3.org/ns/shacl#> .
            ex:AccountShape a sh:NodeShape ; sh:targetClass ex:AccountClass .
        """.trimIndent())
        val both = loaded("both", setOf(ShaclGraphRole.Data, ShaclGraphRole.Shapes), """
            @prefix ex: <https://example.com/> . ex:Shared a ex:AccountClass .
        """.trimIndent())

        val result = assertIs<EntioResult.Success<ShaclGraphSet>>(loader.load(listOf(data, shapes, both))).value
        assertEquals(listOf("both", "data"), result.identity.dataSourceIds)
        assertEquals(listOf("both", "shapes"), result.identity.shapesSourceIds)
        assertTrue(result.dataGraph.triples.isNotEmpty())
        assertTrue(result.shapesGraph.triples.isNotEmpty())
        assertTrue(result.identity.dataGraphFingerprint.isNotBlank())
        assertTrue(result.identity.shapesGraphFingerprint.isNotBlank())
    }

    @Test
    fun parsesSupportedTargetsConstraintsAndStableShapeIdentity(): Unit {
        val graph = parse("""
            @prefix ex: <https://example.com/> .
            @prefix sh: <http://www.w3.org/ns/shacl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
            ex:AccountShape a sh:NodeShape ;
                rdfs:label "Account shape" ;
                sh:targetClass ex:Account ; sh:targetNode ex:Specific ;
                sh:targetSubjectsOf ex:owner ; sh:targetObjectsOf ex:ownedBy ;
                sh:closed true ; sh:message "Account shape" ;
                sh:property [ sh:path ex:accountNumber ; sh:minCount 1 ; sh:maxCount 1 ;
                    sh:datatype xsd:string ; sh:in ( "A" "B" ) ; sh:pattern "^[A-Z]" ;
                    sh:minLength 1 ; sh:maxLength 10 ; sh:severity sh:Warning ; sh:message "Number" ] .
        """.trimIndent())

        val first = assertIs<EntioResult.Success<ShaclAuthoringDocument>>(authoring.load("shapes", graph)).value
        val second = assertIs<EntioResult.Success<ShaclAuthoringDocument>>(authoring.load("shapes", graph)).value
        assertEquals(first.nodeShapes, second.nodeShapes)
        val shape = first.nodeShapes.single()
        assertEquals("Account shape", shape.label)
        assertTrue(shape.targets.contains(ShaclTarget.TargetClass(Iri("https://example.com/Account"))))
        assertTrue(shape.targets.contains(ShaclTarget.TargetNode(Iri("https://example.com/Specific"))))
        assertEquals(1, shape.propertyShapes.size)
        assertEquals(ShaclPath.DirectProperty(Iri("https://example.com/accountNumber")), shape.propertyShapes.single().path)
        assertTrue(shape.propertyShapes.single().constraints.any { it.kind == ShaclConstraintKind.Pattern })
        assertTrue(shape.propertyShapes.single().constraints.any { it.kind == ShaclConstraintKind.In })
    }

    @Test
    fun translatesAddEditAndDeleteAsGraphChanges(): Unit {
        val shape = com.entio.core.ShaclNodeShape(
            id = ShaclShapeId(Iri("https://example.com/AccountShape"), "shapes"),
            targets = listOf(ShaclTarget.TargetClass(Iri("https://example.com/Account"))),
            propertyShapes = listOf(
                ShaclPropertyShape(
                    id = ShaclShapeId(Iri("urn:property"), "shapes"),
                    path = ShaclPath.DirectProperty(Iri("https://example.com/accountNumber")),
                ),
            ),
        )
        val added = assertIs<EntioResult.Success<ChangeSet>>(
            authoring.translate(ShaclShapeEdit("shapes", ShaclShapeEditOperation.Add, shape), GraphState()),
        ).value
        assertTrue(added.additions.isNotEmpty())
        val graph = GraphState(added.additions.map { it.triple }.toSet())
        val deleted = assertIs<EntioResult.Success<ChangeSet>>(
            authoring.translate(ShaclShapeEdit("shapes", ShaclShapeEditOperation.Delete, shape), graph),
        ).value
        assertTrue(deleted.removals.isNotEmpty())
        val edited = assertIs<EntioResult.Success<ChangeSet>>(
            authoring.translate(
                ShaclShapeEdit("shapes", ShaclShapeEditOperation.Edit, shape, previousShape = shape),
                graph,
            ),
        ).value
        assertTrue(edited.additions.isNotEmpty())
        assertTrue(edited.removals.isNotEmpty())
    }

    @Test
    fun rejectsComplexPropertyPaths(): Unit {
        val graph = parse("""
            @prefix ex: <https://example.com/> . @prefix sh: <http://www.w3.org/ns/shacl#> .
            ex:AccountShape a sh:NodeShape ; sh:property [ sh:path [ sh:alternativePath ( ex:a ex:b ) ] ] .
        """.trimIndent())
        val result = authoring.load("shapes", graph)
        assertTrue(result is EntioResult.Failure)
        assertTrue((result as EntioResult.Failure).message.contains("Complex SHACL property paths"))
    }

    private fun loaded(id: String, roles: Set<ShaclGraphRole>, content: String): LoadedOntology =
        LoadedOntology(ResolvedOntologySource(id, Files.createTempFile("entio-shacl-$id", ".ttl"), OntologyFormat.Turtle, roles), parse(content))

    private fun parse(content: String): GraphState {
        val path = Files.createTempFile("entio-shacl", ".ttl")
        path.writeText(content)
        return assertIs<EntioResult.Success<com.entio.core.LoadedOntology>>(
            parser.parse(ResolvedOntologySource("fixture", path, OntologyFormat.Turtle)),
        ).value.graph
    }
}
