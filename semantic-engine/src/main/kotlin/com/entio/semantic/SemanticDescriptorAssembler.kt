package com.entio.semantic

import com.entio.core.AnnotationStatement
import com.entio.core.AnnotationValue
import com.entio.core.DatatypePropertyAssertion
import com.entio.core.EntioProject
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.LoadedOntology
import com.entio.core.LocalityStatus
import com.entio.core.ObjectPropertyAssertion
import com.entio.core.OntologyEntityDescriptor
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.RdfTerm
import com.entio.core.SemanticDescriptorCommon
import com.entio.core.SemanticDescriptorKind

/** Assembles descriptors from explicit graph facts without OWL inference. */
public class SemanticDescriptorAssembler(
    private val annotationExtractor: ExplicitAnnotationExtractor = ExplicitAnnotationExtractor(),
    private val labelPolicy: SemanticLabelPolicy = SemanticLabelPolicy(),
) {
    public fun assemble(
        project: EntioProject,
        preferredLanguage: String? = null,
    ): List<OntologyEntityDescriptor> =
        project.ontologies
            .flatMap { ontology -> assemble(ontology, preferredLanguage) }
            .sortedWith(descriptorComparator)

    public fun assemble(
        ontology: LoadedOntology,
        preferredLanguage: String? = null,
    ): List<OntologyEntityDescriptor> {
        val triples = ontology.graph.triples
        val kinds = classifyEntities(triples)
        val descriptors = kinds.map { (entity, kind) ->
            val common = commonDescriptor(ontology, entity, kind, preferredLanguage)
            when (kind) {
                SemanticDescriptorKind.Class -> OntologyEntityDescriptor.Class(
                    common = common,
                    directSuperclasses = directSuperclasses(triples, entity),
                    directSubclasses = directSubclasses(triples, entity),
                    directlyTypedIndividuals = directlyTypedIndividuals(triples, entity),
                )

                SemanticDescriptorKind.ObjectProperty -> OntologyEntityDescriptor.ObjectProperty(
                    common = common,
                    domains = propertyValues(triples, entity, RDFS_DOMAIN),
                    ranges = propertyValues(triples, entity, RDFS_RANGE),
                    directAssertions = triples
                        .filter { it.predicate == entity && it.objectTerm is RdfResource }
                        .map { triple ->
                            ObjectPropertyAssertion(
                                subject = triple.subjectResource,
                                property = entity as Iri,
                                value = triple.objectTerm as RdfResource,
                                sourceId = ontology.source.id,
                            )
                        }
                        .sortedWith(objectAssertionComparator),
                )

                SemanticDescriptorKind.DatatypeProperty -> OntologyEntityDescriptor.DatatypeProperty(
                    common = common,
                    domains = propertyValues(triples, entity, RDFS_DOMAIN),
                    datatypeRanges = propertyValues(triples, entity, RDFS_RANGE),
                    directAssertions = triples
                        .filter { it.predicate == entity && it.objectTerm is RdfLiteral }
                        .map { triple ->
                            DatatypePropertyAssertion(
                                subject = triple.subjectResource,
                                property = entity as Iri,
                                value = triple.objectTerm as RdfLiteral,
                                sourceId = ontology.source.id,
                            )
                        }
                        .sortedWith(datatypeAssertionComparator),
                )

                SemanticDescriptorKind.AnnotationProperty -> OntologyEntityDescriptor.AnnotationProperty(
                    common = common,
                    statementsUsingProperty = triples
                        .filter { it.predicate == entity }
                        .map { it.toAnnotationStatement(ontology.source.id) }
                        .sortedBy { it.stableKey },
                )

                SemanticDescriptorKind.Individual -> OntologyEntityDescriptor.Individual(
                    common = common,
                    assertedTypes = assertedTypes(triples, entity),
                    objectPropertyAssertions = triples
                        .filter { it.subjectResource == entity && it.predicate in kinds
                            .filterValues { kindValue -> kindValue == SemanticDescriptorKind.ObjectProperty }
                            .keys
                        }
                        .mapNotNull { triple -> triple.toObjectAssertion(ontology.source.id) }
                        .sortedWith(objectAssertionComparator),
                    datatypePropertyAssertions = triples
                        .filter { it.subjectResource == entity && it.predicate in kinds
                            .filterValues { kindValue -> kindValue == SemanticDescriptorKind.DatatypeProperty }
                            .keys
                        }
                        .mapNotNull { triple -> triple.toDatatypeAssertion(ontology.source.id) }
                        .sortedWith(datatypeAssertionComparator),
                )
            }
        }

        return descriptors.sortedWith(descriptorComparator)
    }

    private fun commonDescriptor(
        ontology: LoadedOntology,
        entity: RdfResource,
        kind: SemanticDescriptorKind,
        preferredLanguage: String?,
    ): SemanticDescriptorCommon {
        val annotations = annotationExtractor.extract(ontology, entity)
        val metadata = labelPolicy.apply(
            entity = entity,
            annotations = annotations,
            preferredLanguage = preferredLanguage,
        )
        return SemanticDescriptorCommon(
            entity = entity,
            kind = kind,
            sourceId = ontology.source.id,
            sourceOntologyId = ontology.source.id,
            locality = LocalityStatus.Unknown,
            preferredLabel = metadata.preferredLabel,
            preferredLabelSource = metadata.preferredLabelSource,
            ambiguousPreferredLabelLanguages = metadata.ambiguousPreferredLabelLanguages,
            alternateLabels = metadata.alternateLabels,
            definitions = metadata.definitions,
            annotations = metadata.annotations,
        )
    }

    private fun classifyEntities(triples: Set<GraphTriple>): Map<RdfResource, SemanticDescriptorKind> {
        val typesByEntity = triples
            .asSequence()
            .filter { it.predicate == RDF_TYPE }
            .mapNotNull { triple ->
                val type = triple.objectTerm as? RdfResource ?: return@mapNotNull null
                triple.subjectResource to type.value
            }
            .groupBy({ it.first }, { it.second })

        return typesByEntity
            .mapValues { (_, types) ->
                when {
                    OWL_OBJECT_PROPERTY in types -> SemanticDescriptorKind.ObjectProperty
                    OWL_DATATYPE_PROPERTY in types -> SemanticDescriptorKind.DatatypeProperty
                    OWL_ANNOTATION_PROPERTY in types -> SemanticDescriptorKind.AnnotationProperty
                    OWL_CLASS in types || RDFS_CLASS in types -> SemanticDescriptorKind.Class
                    RDF_PROPERTY in types -> SemanticDescriptorKind.ObjectProperty
                    else -> SemanticDescriptorKind.Individual
                }
            }
    }

    private fun directSuperclasses(triples: Set<GraphTriple>, entity: RdfResource): List<Iri> =
        triples.filter { it.subjectResource == entity && it.predicate == RDFS_SUB_CLASS_OF }
            .mapNotNull { it.objectTerm as? Iri }
            .sortedBy { it.value }

    private fun directSubclasses(triples: Set<GraphTriple>, entity: RdfResource): List<Iri> =
        triples.filter { it.predicate == RDFS_SUB_CLASS_OF && it.objectTerm == entity }
            .mapNotNull { it.subjectResource as? Iri }
            .sortedBy { it.value }

    private fun directlyTypedIndividuals(triples: Set<GraphTriple>, entity: RdfResource): List<RdfResource> =
        triples.filter { it.predicate == RDF_TYPE && it.objectTerm == entity }
            .map { it.subjectResource }
            .sortedWith(resourceComparator)

    private fun propertyValues(triples: Set<GraphTriple>, property: RdfResource, predicate: Iri): List<Iri> =
        triples.filter { it.subjectResource == property && it.predicate == predicate }
            .mapNotNull { it.objectTerm as? Iri }
            .sortedBy { it.value }

    private fun assertedTypes(triples: Set<GraphTriple>, entity: RdfResource): List<Iri> =
        triples.filter { it.subjectResource == entity && it.predicate == RDF_TYPE }
            .mapNotNull { it.objectTerm as? Iri }
            .sortedBy { it.value }

    private fun GraphTriple.toAnnotationStatement(sourceId: String): AnnotationStatement =
        AnnotationStatement(
            subject = subjectResource,
            property = predicate,
            value = AnnotationValue.fromTerm(objectTerm),
            sourceId = sourceId,
        )

    private fun GraphTriple.toObjectAssertion(sourceId: String): ObjectPropertyAssertion? {
        val value = objectTerm as? RdfResource ?: return null
        return ObjectPropertyAssertion(
            subject = subjectResource,
            property = predicate,
            value = value,
            sourceId = sourceId,
        )
    }

    private fun GraphTriple.toDatatypeAssertion(sourceId: String): DatatypePropertyAssertion? {
        val value = objectTerm as? RdfLiteral ?: return null
        return DatatypePropertyAssertion(
            subject = subjectResource,
            property = predicate,
            value = value,
            sourceId = sourceId,
        )
    }

    private companion object {
        private val RDF_TYPE = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
        private val RDF_PROPERTY = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#Property").value
        private val RDFS_CLASS = Iri("http://www.w3.org/2000/01/rdf-schema#Class").value
        private val RDFS_SUB_CLASS_OF = Iri("http://www.w3.org/2000/01/rdf-schema#subClassOf")
        private val RDFS_DOMAIN = Iri("http://www.w3.org/2000/01/rdf-schema#domain")
        private val RDFS_RANGE = Iri("http://www.w3.org/2000/01/rdf-schema#range")
        private val OWL_CLASS = Iri("http://www.w3.org/2002/07/owl#Class").value
        private val OWL_OBJECT_PROPERTY = Iri("http://www.w3.org/2002/07/owl#ObjectProperty").value
        private val OWL_DATATYPE_PROPERTY = Iri("http://www.w3.org/2002/07/owl#DatatypeProperty").value
        private val OWL_ANNOTATION_PROPERTY = Iri("http://www.w3.org/2002/07/owl#AnnotationProperty").value

        private val resourceComparator = compareBy<RdfResource> { it.value }
        private val objectAssertionComparator = compareBy<ObjectPropertyAssertion> { it.subject.value }
            .thenBy { it.property.value }
            .thenBy { it.value.value }
            .thenBy { it.sourceId }
        private val datatypeAssertionComparator = compareBy<DatatypePropertyAssertion> { it.subject.value }
            .thenBy { it.property.value }
            .thenBy { it.value.lexicalForm }
            .thenBy { it.value.datatypeIri?.value.orEmpty() }
            .thenBy { it.value.languageTag.orEmpty() }
            .thenBy { it.sourceId }
        private val descriptorComparator = compareBy<OntologyEntityDescriptor> { it.common.entity.value }
            .thenBy { it.common.sourceId }
            .thenBy { it.common.kind.name }
    }
}
