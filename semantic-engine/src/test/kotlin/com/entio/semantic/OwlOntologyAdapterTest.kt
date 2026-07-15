package com.entio.semantic

import com.entio.core.EntioResult
import com.entio.core.OntologyFormat
import com.entio.core.OwlFeatureSupport
import com.entio.core.ResolvedOntologySource
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OwlOntologyAdapterTest {
    private val adapter = OwlOntologyAdapter()

    @Test
    fun loadsSmallLocalOntologyThroughOwlApi(): Unit {
        val source = sourceWithTurtle(
            """
            @prefix ex: <https://example.com/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            ex:Customer a owl:Class .
            ex:Person a owl:Class .
            ex:Customer rdfs:subClassOf ex:Person .
            """.trimIndent(),
        )

        val result = adapter.load(source)

        val document = assertIs<EntioResult.Success<OwlOntologyDocument>>(result).value
        assertEquals("simple", document.sourceId)
        assertTrue(
            document.ontology.classesInSignature()
                .toList()
                .any { it.iri.toString() == "https://example.com/Customer" },
        )
    }

    @Test
    fun reportsSupportedAndPartialOwlFeatureCoverage(): Unit {
        val source = sourceWithTurtle(
            """
            @prefix ex: <https://example.com/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            ex:Customer a owl:Class .
            ex:Person a owl:Class .
            ex:Customer rdfs:subClassOf ex:Person .
            ex:Customer owl:equivalentClass ex:Person .
            ex:parentOf a owl:ObjectProperty .
            ex:childOf a owl:ObjectProperty .
            ex:parentOf owl:inverseOf ex:childOf .
            ex:ancestorOf a owl:TransitiveProperty .
            """.trimIndent(),
        )
        val document = assertIs<EntioResult.Success<OwlOntologyDocument>>(adapter.load(source)).value

        val report = OwlFeatureReporter().report(document)
        assertTrue(report.findings.any { it.feature == "SubClassOf" }, report.findings.toString())

        assertEquals("OWL 2 DL", report.profile)
        assertEquals(
            OwlFeatureSupport.Supported,
            report.findings.first { it.feature == "SubClassOf" }.support,
        )
        assertEquals(
            OwlFeatureSupport.Partial,
            report.findings.first { it.feature == "EquivalentClasses" }.support,
        )
        assertEquals(
            OwlFeatureSupport.Partial,
            report.findings.first { it.feature == "InverseObjectProperties" }.support,
        )
        assertEquals(
            OwlFeatureSupport.Partial,
            report.findings.first { it.feature == "TransitiveObjectProperty" }.support,
        )
        assertTrue(report.findings.all { it.feature.isNotBlank() })
    }

    @Test
    fun mapsConfiguredImportIriToLocalDocument(): Unit {
        val projectRoot = Files.createTempDirectory("entio-owl-import")
        val imported = projectRoot.resolve("imported.ttl").also {
            it.writeText(
                """
                @prefix ex: <https://example.com/> .
                @prefix owl: <http://www.w3.org/2002/07/owl#> .
                ex:ImportedOntology a owl:Ontology .
                """.trimIndent(),
            )
        }
        val root = projectRoot.resolve("root.ttl").also {
            it.writeText(
                """
                @prefix ex: <https://example.com/> .
                @prefix owl: <http://www.w3.org/2002/07/owl#> .
                ex:RootOntology a owl:Ontology ; owl:imports ex:ImportedOntology .
                """.trimIndent(),
            )
        }
        val source = ResolvedOntologySource("root", root, OntologyFormat.Turtle)

        val result = adapter.load(
            source = source,
            importMappings = mapOf("https://example.com/ImportedOntology" to imported),
        )

        val document = assertIs<EntioResult.Success<OwlOntologyDocument>>(result).value
        assertEquals(listOf("https://example.com/ImportedOntology"), document.importedSourceIds)
        assertTrue(document.ontology.imports().toList().isNotEmpty())
    }

    private fun sourceWithTurtle(content: String): ResolvedOntologySource {
        val path = Files.createTempFile("entio-owl-adapter", ".ttl")
        path.writeText(content)
        return ResolvedOntologySource("simple", path, OntologyFormat.Turtle)
    }
}
