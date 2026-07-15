package com.entio.semantic

import com.entio.core.EntioProject
import com.entio.core.EntioProjectConfig
import com.entio.core.EntioResult
import com.entio.core.ExternalDependencyCategory
import com.entio.core.ExternalDependencySelection
import com.entio.core.ExternalDependencyVisibility
import com.entio.core.ExternalEntityKind
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.LoadedOntology
import com.entio.core.OntologyFormat
import com.entio.core.OntologySourceReference
import com.entio.core.ResolvedOntologySource
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalDependencyReviewerTest {
    private val packageRoot: Path = Path.of("..", "external-ontologies", "fibo").toAbsolutePath().normalize()

    @Test
    fun producesBoundedReviewAndKeepsPackageClosureImplementationOnly(): Unit {
        val session = loadSession()
        val curatedModules = session.browseCuratedModules(pageSize = 100).items.map { it.ontologyIri }.toSet()
        val element = session.allElements().first { candidate ->
            candidate.kind == ExternalEntityKind.ObjectProperty &&
                candidate.descriptor.moduleIri in curatedModules &&
                (candidate.descriptor.descriptor as? com.entio.core.OntologyEntityDescriptor.ObjectProperty)
                    ?.let { it.domains.isNotEmpty() && it.ranges.isNotEmpty() } == true
        }

        val dependencies = ExternalDependencyReviewer().review(session, element)

        assertTrue(dependencies.dependencies.isNotEmpty())
        assertTrue(dependencies.dependencies.size < 20)
        assertTrue(dependencies.dependencies.any { it.category == ExternalDependencyCategory.PropertyDomain })
        assertTrue(dependencies.dependencies.any { it.category == ExternalDependencyCategory.PropertyRange })
        assertTrue(dependencies.dependencies.any { it.category == ExternalDependencyCategory.SourceOntology })
        assertTrue(dependencies.dependencies.any { it.category == ExternalDependencyCategory.PackageRuntime })
        assertTrue(
            dependencies.requiredUserVisibleDependencies.any { it.selection == ExternalDependencySelection.Missing },
        )
        assertTrue(
            dependencies.dependencies.filter { it.category == ExternalDependencyCategory.PackageRuntime }
                .all { it.visibility == ExternalDependencyVisibility.ImplementationOnly },
        )
        assertEquals(
            dependencies.dependencies,
            dependencies.dependencies.sortedWith(
                compareBy({ it.category.ordinal }, { it.externalIri?.value.orEmpty() }, { it.sourceModule?.value.orEmpty() }),
            ),
        )
    }

    @Test
    fun marksAssertedModuleAndElementAsAlreadyAvailable(): Unit {
        val session = loadSession()
        val element = session.allElements().first { it.kind == ExternalEntityKind.Class }
        val elementIri = element.descriptor.descriptor.common.entity as Iri
        val moduleIri = element.descriptor.moduleIri
        val project = projectWith(
            GraphState(
                triples = setOf(
                    GraphTriple(
                        subject = Iri("https://example.com/project"),
                        predicate = OWL_IMPORTS,
                        objectTerm = moduleIri,
                    ),
                    GraphTriple(
                        subject = elementIri,
                        predicate = RDF_TYPE,
                        objectTerm = Iri("http://www.w3.org/2002/07/owl#Class"),
                    ),
                ),
            ),
        )

        val dependencies = ExternalDependencyReviewer().review(session, element, project)

        assertEquals(
            ExternalDependencySelection.AlreadyAvailable,
            dependencies.dependencies.first { it.category == ExternalDependencyCategory.SourceOntology }.selection,
        )
        assertTrue(
            dependencies.dependencies.any {
                it.category == ExternalDependencyCategory.LocalReference &&
                    it.selection == ExternalDependencySelection.AlreadyAvailable
            },
        )
    }

    @Test
    fun includesExplicitClassParentAsUserVisibleDependency(): Unit {
        val session = loadSession()
        val element = session.allElements().first { candidate ->
            candidate.kind == ExternalEntityKind.Class &&
                (candidate.descriptor.descriptor as? com.entio.core.OntologyEntityDescriptor.Class)
                    ?.directSuperclasses?.isNotEmpty() == true
        }

        val dependencies = ExternalDependencyReviewer().review(session, element)

        assertTrue(
            dependencies.dependencies.any {
                it.category == ExternalDependencyCategory.SemanticParent &&
                    it.visibility == ExternalDependencyVisibility.UserVisible &&
                    it.externalIri != null
            },
        )
        assertFalse(dependencies.dependencies.any { it.category == ExternalDependencyCategory.LocalReference })
    }

    private fun loadSession(): ExternalFiboCatalogSession =
        (FiboCatalogLoader(packageRoot).load() as EntioResult.Success).value

    private fun projectWith(graph: GraphState): EntioProject = EntioProject(
        config = EntioProjectConfig(
            name = "dependency-test",
            ontologySources = listOf(
                OntologySourceReference(
                    id = "simple",
                    path = "ontology/simple.ttl",
                    format = OntologyFormat.Turtle,
                ),
            ),
        ),
        resolvedSources = listOf(
            ResolvedOntologySource(
                id = "simple",
                path = Path.of("ontology/simple.ttl"),
                format = OntologyFormat.Turtle,
            ),
        ),
        ontologies = emptyList<LoadedOntology>(),
        symbols = emptyList(),
        graph = graph,
    )

    private companion object {
        val OWL_IMPORTS = Iri("http://www.w3.org/2002/07/owl#imports")
        val RDF_TYPE = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
    }
}
