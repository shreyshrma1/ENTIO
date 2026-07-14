package com.entio.semantic

import com.entio.core.EntioProject
import com.entio.core.EntioProjectConfig
import com.entio.core.EntioResult
import com.entio.core.Iri
import com.entio.core.OntologyEntityDescriptor
import com.entio.core.OntologySourceReference
import com.entio.core.SemanticDescriptorKind
import com.entio.core.SemanticMatchReason
import com.entio.core.SemanticSearchQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SemanticDescriptionServiceTest {
    private val parser = OntologyParser()
    private val service = SemanticDescriptionService()

    @Test
    fun describesAnEntityAndReportsMissingDescriptors(): Unit {
        val project = project()

        val descriptor = assertIs<EntioResult.Success<OntologyEntityDescriptor.Class>>(
            service.describe(project, Iri("https://example.com/Customer"), preferredLanguage = "en"),
        ).value
        assertEquals("Customer", descriptor.common.preferredLabel?.lexicalForm)

        val missing = service.describe(project, Iri("https://example.com/Missing"))
        assertTrue(missing is EntioResult.Failure)
        assertEquals("missing-semantic-descriptor", (missing as EntioResult.Failure).issues.single().code)
    }

    @Test
    fun searchesWithStableReasonPrecedenceAndFilters(): Unit {
        val project = project()

        val preferred = service.search(project, SemanticSearchQuery(text = "customer", preferredLanguage = "en"))
        assertEquals(SemanticMatchReason.PreferredLabel, preferred.single().reason)
        assertEquals(SemanticDescriptorKind.Class, preferred.single().descriptor.common.kind)

        val alternate = service.search(
            project,
            SemanticSearchQuery(text = "client", preferredLanguage = "en", kind = SemanticDescriptorKind.Class),
        )
        assertEquals(SemanticMatchReason.AlternateLabel, alternate.single().reason)

        val annotation = service.search(project, SemanticSearchQuery(text = "important"))
        assertEquals(SemanticMatchReason.Annotation, annotation.single().reason)

        val sourceFiltered = service.search(project, SemanticSearchQuery(text = "customer", preferredLanguage = "en", sourceId = "simple"))
        assertEquals(1, sourceFiltered.size)
        assertTrue(service.search(project, SemanticSearchQuery(text = "customer", sourceId = "other")).isEmpty())
    }

    private fun project(): EntioProject {
        val loadedOntology = assertIs<EntioResult.Success<com.entio.core.LoadedOntology>>(
            parser.parse(
                SemanticEngineTestFixtures.resolvedSource(
                    """
                    @prefix ex: <https://example.com/> .
                    @prefix owl: <http://www.w3.org/2002/07/owl#> .
                    @prefix skos: <http://www.w3.org/2004/02/skos/core#> .

                    ex:Customer a owl:Class ;
                        skos:prefLabel "Customer"@en ;
                        skos:altLabel "Client"@en ;
                        ex:note "Important customer" .
                    ex:note a owl:AnnotationProperty .
                    """.trimIndent(),
                ),
            ),
        ).value

        return EntioProject(
            config = EntioProjectConfig(
                name = "simple",
                ontologySources = listOf(
                    OntologySourceReference("simple", loadedOntology.source.path.toString(), loadedOntology.source.format),
                ),
            ),
            resolvedSources = listOf(loadedOntology.source),
            ontologies = listOf(loadedOntology),
            symbols = emptyList(),
            graph = loadedOntology.graph,
        )
    }
}
