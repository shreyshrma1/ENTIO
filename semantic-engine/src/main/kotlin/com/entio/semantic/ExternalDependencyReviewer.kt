package com.entio.semantic

import com.entio.core.EntioProject
import com.entio.core.ExternalCatalogElement
import com.entio.core.ExternalDependency
import com.entio.core.ExternalDependencyCategory
import com.entio.core.ExternalDependencyClosure
import com.entio.core.ExternalDependencyRequirement
import com.entio.core.ExternalDependencySelection
import com.entio.core.ExternalDependencySet
import com.entio.core.ExternalDependencyVisibility
import com.entio.core.ExternalOntologyMaturity
import com.entio.core.Iri
import com.entio.core.OntologyEntityDescriptor

/** Builds a bounded, deterministic dependency review for one catalog element. */
public class ExternalDependencyReviewer {
    public fun review(
        session: ExternalFiboCatalogSession,
        element: ExternalCatalogElement,
        project: EntioProject? = null,
    ): ExternalDependencySet {
        val selectedIri = element.descriptor.descriptor.common.entity as? Iri
            ?: error("External catalog elements must have IRI entities.")
        val module = session.catalog.modules.firstOrNull { it.ontologyIri == element.descriptor.moduleIri }
        val dependencies = buildList {
            add(
                dependency(
                    category = ExternalDependencyCategory.SourceOntology,
                    requirement = ExternalDependencyRequirement.Required,
                    closure = ExternalDependencyClosure.Direct,
                    visibility = ExternalDependencyVisibility.UserVisible,
                    selection = if (module?.ontologyIri?.let { isImported(project, it) } == true) {
                        ExternalDependencySelection.AlreadyAvailable
                    } else {
                        ExternalDependencySelection.Missing
                    },
                    reason = "The selected element is defined by this external ontology module.",
                    externalIri = element.descriptor.moduleIri,
                    sourceModule = element.descriptor.moduleIri,
                    maturity = element.descriptor.maturity,
                ),
            )

            add(
                dependency(
                    category = ExternalDependencyCategory.PackageRuntime,
                    requirement = ExternalDependencyRequirement.Required,
                    closure = ExternalDependencyClosure.PackageTransitive,
                    visibility = ExternalDependencyVisibility.ImplementationOnly,
                    selection = ExternalDependencySelection.AlreadyAvailable,
                    reason = "The pinned FIBO package already provides the module import closure.",
                    sourceModule = element.descriptor.moduleIri,
                    maturity = element.descriptor.maturity,
                ),
            )

            module?.importedOntologyIris.orEmpty().distinct().sortedBy(Iri::value).forEach { importedIri ->
                add(
                    dependency(
                        category = ExternalDependencyCategory.OwlImport,
                        requirement = ExternalDependencyRequirement.Optional,
                        closure = ExternalDependencyClosure.PackageTransitive,
                        visibility = ExternalDependencyVisibility.ImplementationOnly,
                        selection = ExternalDependencySelection.AlreadyAvailable,
                        reason = "The pinned package resolves this ontology import as runtime coverage.",
                        externalIri = importedIri,
                        sourceModule = element.descriptor.moduleIri,
                        maturity = element.descriptor.maturity,
                    ),
                )
            }

            when (val descriptor = element.descriptor.descriptor) {
                is OntologyEntityDescriptor.Class -> {
                    descriptor.directSuperclasses
                    .distinct()
                    .sortedBy(Iri::value)
                    .forEach { parentIri ->
                        add(
                            dependency(
                                category = ExternalDependencyCategory.SemanticParent,
                                requirement = ExternalDependencyRequirement.Required,
                                closure = ExternalDependencyClosure.Direct,
                                visibility = ExternalDependencyVisibility.UserVisible,
                                selection = if (hasAssertedRelation(project, selectedIri, SUBCLASS_OF, parentIri)) {
                                    ExternalDependencySelection.AlreadyAvailable
                                } else {
                                    ExternalDependencySelection.Missing
                                },
                                reason = "The selected class explicitly declares this superclass.",
                                externalIri = parentIri,
                                sourceModule = element.descriptor.moduleIri,
                                maturity = element.descriptor.maturity,
                            ),
                        )
                    }
                }
                is OntologyEntityDescriptor.ObjectProperty -> {
                    descriptor.domains.distinct().sortedBy(Iri::value).forEach { domainIri ->
                        add(
                            dependency(
                                category = ExternalDependencyCategory.PropertyDomain,
                                requirement = ExternalDependencyRequirement.Required,
                                closure = ExternalDependencyClosure.Direct,
                                visibility = ExternalDependencyVisibility.UserVisible,
                                selection = if (hasAssertedRelation(project, selectedIri, DOMAIN, domainIri)) {
                                    ExternalDependencySelection.AlreadyAvailable
                                } else {
                                    ExternalDependencySelection.Missing
                                },
                                reason = "The selected object property explicitly declares this domain.",
                                externalIri = domainIri,
                                sourceModule = element.descriptor.moduleIri,
                                maturity = element.descriptor.maturity,
                            ),
                        )
                    }
                    descriptor.ranges.distinct().sortedBy(Iri::value).forEach { rangeIri ->
                        add(
                            dependency(
                                category = ExternalDependencyCategory.PropertyRange,
                                requirement = ExternalDependencyRequirement.Required,
                                closure = ExternalDependencyClosure.Direct,
                                visibility = ExternalDependencyVisibility.UserVisible,
                                selection = if (hasAssertedRelation(project, selectedIri, RANGE, rangeIri)) {
                                    ExternalDependencySelection.AlreadyAvailable
                                } else {
                                    ExternalDependencySelection.Missing
                                },
                                reason = "The selected object property explicitly declares this range.",
                                externalIri = rangeIri,
                                sourceModule = element.descriptor.moduleIri,
                                maturity = element.descriptor.maturity,
                            ),
                        )
                    }
                }
                is OntologyEntityDescriptor.DatatypeProperty -> {
                    descriptor.domains.distinct().sortedBy(Iri::value).forEach { domainIri ->
                        add(
                            dependency(
                                category = ExternalDependencyCategory.PropertyDomain,
                                requirement = ExternalDependencyRequirement.Required,
                                closure = ExternalDependencyClosure.Direct,
                                visibility = ExternalDependencyVisibility.UserVisible,
                                selection = if (hasAssertedRelation(project, selectedIri, DOMAIN, domainIri)) {
                                    ExternalDependencySelection.AlreadyAvailable
                                } else {
                                    ExternalDependencySelection.Missing
                                },
                                reason = "The selected datatype property explicitly declares this domain.",
                                externalIri = domainIri,
                                sourceModule = element.descriptor.moduleIri,
                                maturity = element.descriptor.maturity,
                            ),
                        )
                    }
                    descriptor.datatypeRanges.distinct().sortedBy(Iri::value).forEach { datatypeIri ->
                        add(
                            dependency(
                                category = ExternalDependencyCategory.PropertyRange,
                                requirement = ExternalDependencyRequirement.Required,
                                closure = ExternalDependencyClosure.Direct,
                                visibility = ExternalDependencyVisibility.UserVisible,
                                selection = if (hasAssertedRelation(project, selectedIri, RANGE, datatypeIri)) {
                                    ExternalDependencySelection.AlreadyAvailable
                                } else {
                                    ExternalDependencySelection.Missing
                                },
                                reason = "The selected datatype property explicitly declares this datatype range.",
                                externalIri = datatypeIri,
                                sourceModule = element.descriptor.moduleIri,
                                maturity = element.descriptor.maturity,
                            ),
                        )
                    }
                }
                else -> Unit
            }

            if (element.descriptor.maturity != ExternalOntologyMaturity.Release) {
                add(
                    dependency(
                        category = ExternalDependencyCategory.Metadata,
                        requirement = ExternalDependencyRequirement.Required,
                        closure = ExternalDependencyClosure.Direct,
                        visibility = ExternalDependencyVisibility.UserVisible,
                        selection = ExternalDependencySelection.Missing,
                        reason = "The selected element requires acknowledgement of its non-release maturity.",
                        externalIri = selectedIri,
                        sourceModule = element.descriptor.moduleIri,
                        maturity = element.descriptor.maturity,
                    ),
                )
            }

            if (isAssertedInProject(project, selectedIri)) {
                add(
                    dependency(
                        category = ExternalDependencyCategory.LocalReference,
                        requirement = ExternalDependencyRequirement.Optional,
                        closure = ExternalDependencyClosure.Direct,
                        visibility = ExternalDependencyVisibility.UserVisible,
                        selection = ExternalDependencySelection.AlreadyAvailable,
                        reason = "The selected external IRI is already asserted in the local project graph.",
                        externalIri = selectedIri,
                        sourceModule = element.descriptor.moduleIri,
                        maturity = element.descriptor.maturity,
                    ),
                )
            }
        }.sortedWith(dependencyComparator)

        return ExternalDependencySet(dependencies = dependencies)
    }

    private fun dependency(
        category: ExternalDependencyCategory,
        requirement: ExternalDependencyRequirement,
        closure: ExternalDependencyClosure,
        visibility: ExternalDependencyVisibility,
        selection: ExternalDependencySelection,
        reason: String,
        externalIri: Iri? = null,
        sourceModule: Iri? = null,
        maturity: ExternalOntologyMaturity,
    ): ExternalDependency = ExternalDependency(
        category = category,
        requirement = requirement,
        closure = closure,
        visibility = visibility,
        selection = selection,
        reason = reason,
        externalIri = externalIri,
        sourceModule = sourceModule,
        maturity = maturity,
        packageAvailable = true,
    )

    private fun isImported(project: EntioProject?, moduleIri: Iri): Boolean = project?.graph?.triples?.any { triple ->
        triple.predicate == OWL_IMPORTS && triple.objectTerm == moduleIri
    } == true

    private fun isAssertedInProject(project: EntioProject?, iri: Iri): Boolean = project?.graph?.triples?.any { triple ->
        triple.subjectResource == iri || triple.objectTerm == iri
    } == true

    private fun hasAssertedRelation(project: EntioProject?, subject: Iri, predicate: Iri, objectIri: Iri): Boolean =
        project?.graph?.triples?.any { triple ->
            triple.subjectResource == subject && triple.predicate == predicate && triple.objectTerm == objectIri
        } == true

    private companion object {
        private val OWL_IMPORTS = Iri("http://www.w3.org/2002/07/owl#imports")
        private val SUBCLASS_OF = Iri("http://www.w3.org/2000/01/rdf-schema#subClassOf")
        private val DOMAIN = Iri("http://www.w3.org/2000/01/rdf-schema#domain")
        private val RANGE = Iri("http://www.w3.org/2000/01/rdf-schema#range")
        private val dependencyComparator = compareBy<ExternalDependency> { it.category.ordinal }
            .thenBy { it.externalIri?.value.orEmpty() }
            .thenBy { it.sourceModule?.value.orEmpty() }
            .thenBy { it.selection.ordinal }
            .thenBy { it.reason }
    }
}
