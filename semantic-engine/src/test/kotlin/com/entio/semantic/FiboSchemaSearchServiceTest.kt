package com.entio.semantic

import com.entio.core.EntioResult
import com.entio.core.ExternalConfidenceBand
import com.entio.core.ExternalEntityKind
import com.entio.core.ExternalOntologyMaturity
import com.entio.core.ExternalSchemaSearchQuery
import com.entio.core.ExternalSearchContext
import com.entio.core.Iri
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FiboSchemaSearchServiceTest {
    private val packageRoot: Path = Path.of("..", "external-ontologies", "fibo").toAbsolutePath().normalize()

    @Test
    fun ranksExactPreferredLabelAndReturnsDeterministicReasons(): Unit {
        val session = loadSession()
        val element = session.allElements().first {
            it.descriptor.maturity in setOf(ExternalOntologyMaturity.Release, ExternalOntologyMaturity.Provisional) &&
                it.descriptor.descriptor.common.preferredLabel?.lexicalForm?.let { label ->
                    label.split(Regex("[^A-Za-z0-9]+"))
                        .filter(String::isNotBlank)
                        .all { token -> token.length >= 3 }
                } == true
        }
        val label = element.descriptor.descriptor.common.preferredLabel!!.lexicalForm

        val first = FiboSchemaSearchService().search(
            session,
            ExternalSchemaSearchQuery(text = label, kind = element.kind, pageSize = 10),
        )
        val second = FiboSchemaSearchService().search(
            session,
            ExternalSchemaSearchQuery(text = label, kind = element.kind, pageSize = 10),
        )

        assertEquals(first, second)
        assertFalse(first.candidates.isEmpty())
        val matching = first.candidates.firstOrNull {
            it.descriptor.descriptor.common.entity == element.descriptor.descriptor.common.entity
        }
        assertNotNull(matching)
        assertTrue(matching.score.nameOrIri >= 42)
        assertTrue(matching.reasons.isNotEmpty())
    }

    @Test
    fun exactFullIriIsVeryStrongAndPreservesOriginalResource(): Unit {
        val session = loadSession()
        val element = session.allElements().first {
            it.descriptor.maturity == ExternalOntologyMaturity.Release
        }
        val iri = element.descriptor.descriptor.common.entity as Iri

        val response = FiboSchemaSearchService().search(session, ExternalSchemaSearchQuery(text = iri.value))
        val candidate = response.candidates.first { it.descriptor.descriptor.common.entity == iri }

        assertEquals(ExternalConfidenceBand.VeryStrong, candidate.confidence)
        assertEquals(60, candidate.score.nameOrIri)
        assertTrue(candidate.reasons.any { it.type.name == "Iri" })
    }

    @Test
    fun appliesKindCuratedAndRequiredDomainFiltersBeforeRanking(): Unit {
        val session = loadSession()
        val curatedModules = session.browseCuratedModules(pageSize = 100).items.map { it.ontologyIri }.toSet()
        val property = session.allElements().firstOrNull { element ->
            element.kind == ExternalEntityKind.ObjectProperty &&
                element.descriptor.moduleIri in curatedModules &&
                element.descriptor.descriptor is com.entio.core.OntologyEntityDescriptor.ObjectProperty &&
                (element.descriptor.descriptor as com.entio.core.OntologyEntityDescriptor.ObjectProperty).domains.isNotEmpty()
        } ?: error("The approved package should contain a curated object property with a domain.")
        val descriptor = property.descriptor.descriptor as com.entio.core.OntologyEntityDescriptor.ObjectProperty
        val label = descriptor.common.preferredLabel?.lexicalForm ?: descriptor.common.entity.value
        val domain = descriptor.domains.first()

        val response = FiboSchemaSearchService().search(
            session,
            ExternalSchemaSearchQuery(
                text = label,
                kind = ExternalEntityKind.ObjectProperty,
                curatedOnly = true,
                context = ExternalSearchContext(domainIri = domain, domainRequired = true),
            ),
        )

        assertTrue(response.candidates.all { it.kind == ExternalEntityKind.ObjectProperty })
        assertTrue(response.candidates.all { it.descriptor.moduleIri in curatedModules })
        assertTrue(response.candidates.any { it.descriptor.descriptor.common.entity == property.descriptor.descriptor.common.entity })
        assertTrue(response.candidates.first().reasons.any { it.type.name == "DomainCompatibility" })
    }

    @Test
    fun respectsPaginationWithoutChangingRanking(): Unit {
        val session = loadSession()
        val query = ExternalSchemaSearchQuery(text = "business", pageSize = 3)
        val service = FiboSchemaSearchService()

        val firstPage = service.search(session, query)
        val repeatedFirstPage = service.search(session, query)
        val secondPage = service.search(session, query.copy(page = 1))

        assertEquals(firstPage, repeatedFirstPage)
        assertTrue(firstPage.candidates.size <= 3)
        assertTrue(secondPage.candidates.none { candidate ->
            firstPage.candidates.any { it.descriptor.descriptor.common.entity == candidate.descriptor.descriptor.common.entity }
        })
        assertEquals(firstPage.totalResultCount, secondPage.totalResultCount)
    }

    private fun loadSession(): ExternalFiboCatalogSession =
        (FiboCatalogLoader(packageRoot).load() as EntioResult.Success).value
}
