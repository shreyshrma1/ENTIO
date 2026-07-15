package com.entio.semantic

import com.entio.core.EntioProject
import com.entio.core.EntioProjectConfig
import com.entio.core.EntioResult
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.nio.file.Path

class FiboCatalogLoaderTest {
    private val packageRoot: Path = Path.of("..", "external-ontologies", "fibo").toAbsolutePath().normalize()

    @Test
    fun loadsCuratedModulesAndPagedDescriptorsFromCompactIndexes(): Unit {
        val loader = FiboCatalogLoader(packageRoot)
        val first = assertIs<EntioResult.Success<ExternalFiboCatalogSession>>(loader.load()).value
        val second = assertIs<EntioResult.Success<ExternalFiboCatalogSession>>(loader.load()).value

        assertEquals(15, first.browseCuratedModules(pageSize = 100).totalCount)
        assertEquals(
            first.browseCuratedModules(pageSize = 100).items,
            second.browseCuratedModules(pageSize = 100).items,
        )
        assertEquals(first.manifest.curatedSeedOntologyIris.first(), first.browseCuratedModules(pageSize = 100).items.first().ontologyIri)

        val page = first.browseModules(pageSize = 25)
        assertEquals(25, page.items.size)
        assertTrue(page.totalCount > page.items.size)
        assertTrue(page.hasNext)
        assertEquals(page.items, first.browseModules(pageSize = 25).items)
        assertFalse(first.catalog.modules.any { it.domain.equals("EXMP", ignoreCase = true) })
        assertFalse(first.browseModule(page.items.first().ontologyIri, pageSize = 100).items.isEmpty())
    }

    @Test
    fun preservesOriginalIrisAndExplicitHierarchyWithoutInference(): Unit {
        val session = assertIs<EntioResult.Success<ExternalFiboCatalogSession>>(FiboCatalogLoader(packageRoot).load()).value
        val element = session.browseModule(session.browseCuratedModules(pageSize = 100).items.first().ontologyIri, pageSize = 1).items.first()
        val descriptor = element.descriptor.descriptor

        assertNotNull(descriptor.common.entity as? Iri)
        assertEquals(element.descriptor.moduleIri.value, descriptor.common.sourceOntologyId)
        when (descriptor) {
            is com.entio.core.OntologyEntityDescriptor.Class -> {
                assertTrue(descriptor.directSuperclasses.all { it.value.isNotBlank() })
                assertTrue(descriptor.directSubclasses.all { it.value.isNotBlank() })
            }
            is com.entio.core.OntologyEntityDescriptor.ObjectProperty -> {
                assertTrue(descriptor.domains.all { it.value.isNotBlank() })
                assertTrue(descriptor.ranges.all { it.value.isNotBlank() })
            }
            is com.entio.core.OntologyEntityDescriptor.DatatypeProperty -> {
                assertTrue(descriptor.domains.all { it.value.isNotBlank() })
                assertTrue(descriptor.datatypeRanges.all { it.value.isNotBlank() })
            }
            else -> error("Catalog returned an unsupported descriptor kind.")
        }
    }

    @Test
    fun marksAlreadyUsedOnlyFromAssertedLocalGraphFacts(): Unit {
        val baseSession = assertIs<EntioResult.Success<ExternalFiboCatalogSession>>(FiboCatalogLoader(packageRoot).load()).value
        val module = baseSession.browseModules(pageSize = 1).items.single()
        val element = baseSession.browseModule(module.ontologyIri, pageSize = 1).items.single()
        val externalIri = element.descriptor.descriptor.common.entity as Iri
        val project = EntioProject(
            config = EntioProjectConfig(name = "test", ontologySources = emptyList()),
            resolvedSources = emptyList(),
            ontologies = emptyList(),
            symbols = emptyList(),
            graph = GraphState(
                triples = setOf(
                    GraphTriple(
                        subject = externalIri,
                        predicate = Iri("https://example.com/asserted"),
                        objectTerm = RdfLiteral("value"),
                    ),
                ),
            ),
        )

        val session = assertIs<EntioResult.Success<ExternalFiboCatalogSession>>(FiboCatalogLoader(packageRoot).load(project)).value
        assertEquals(
            com.entio.core.ExternalElementCatalogStatus.AlreadyUsed,
            session.find(externalIri)?.descriptor?.catalogStatus,
        )
    }
}
