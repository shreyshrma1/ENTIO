package com.entio.semantic

import com.entio.core.ConsistencyStatus
import com.entio.core.EntioResult
import com.entio.core.FactOrigin
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.ImportClosureReport
import com.entio.core.Iri
import com.entio.core.ReasoningClassRelationship
import com.entio.core.ReasoningFingerprints
import com.entio.core.ReasoningIndividualType
import com.entio.core.ReasoningPropertyRelationship
import com.entio.core.ReasoningResult
import com.entio.core.ReasoningRunMetadata
import com.entio.core.ReasoningRunStatus
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.RdfTerm
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.apache.jena.rdf.model.AnonId
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.semanticweb.HermiT.ReasonerFactory
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.io.StringDocumentSource
import org.semanticweb.owlapi.model.IRI
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.model.OWLOntologyCreationException
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration
import org.semanticweb.owlapi.model.MissingImportHandlingStrategy
import org.semanticweb.owlapi.model.OWLClass
import org.semanticweb.owlapi.model.OWLNamedIndividual
import org.semanticweb.owlapi.model.OWLObjectProperty
import org.semanticweb.owlapi.reasoner.OWLReasoner

public class ReasoningService(
    private val featureReporter: OwlFeatureReporter = OwlFeatureReporter(),
) {
    public fun reason(
        graph: GraphState,
        importClosure: ImportClosureReport? = null,
        reasonerConfiguration: String = DEFAULT_REASONER_CONFIGURATION,
        sourceId: String? = null,
    ): EntioResult<ReasoningResult> = try {
        val document = loadInMemoryOntology(graph)
        reason(
            document = document,
            graph = graph,
            importClosure = importClosure,
            reasonerConfiguration = reasonerConfiguration,
            sourceId = sourceId,
        )
    } catch (exception: OWLOntologyCreationException) {
        failure("Reasoning ontology could not be constructed.", exception)
    } catch (exception: RuntimeException) {
        failure("Reasoning ontology could not be constructed.", exception)
    }

    public fun reason(
        document: OwlOntologyDocument,
        graph: GraphState,
        importClosure: ImportClosureReport? = null,
        reasonerConfiguration: String = DEFAULT_REASONER_CONFIGURATION,
        sourceId: String? = document.sourceId,
    ): EntioResult<ReasoningResult> {
        val featureReport = featureReporter.report(document)
        val importComplete = importClosure?.complete ?: true
        val fingerprints = ReasoningFingerprints(
            graphFingerprint = fingerprint(graph),
            importClosureFingerprint = fingerprint(importClosure),
            reasonerConfigurationFingerprint = fingerprint(reasonerConfiguration),
        )
        val metadata = ReasoningRunMetadata(
            status = if (importComplete) ReasoningRunStatus.Completed else ReasoningRunStatus.Incomplete,
            reasonerName = REASONER_NAME,
            reasonerVersion = REASONER_VERSION,
            owlApiVersion = OWL_API_VERSION,
            fingerprints = fingerprints,
            importClosureComplete = importComplete,
        )

        return try {
            val reasoner = ReasonerFactory().createReasoner(document.ontology)
            try {
                val consistent = reasoner.isConsistent()
                val unsatisfiableClasses = if (consistent) {
                    reasoner.getUnsatisfiableClasses()
                        .getEntitiesMinusBottom()
                        .toList()
                        .map(::toIriResource)
                        .sortedBy { it.value }
                } else {
                    emptyList()
                }

                val objectPropertyIris = document.ontology.objectPropertiesInSignature()
                    .toList()
                    .map { it.iri.toString() }
                    .toSet()
                val assertedClassRelationships = assertedClassRelationships(graph, sourceId)
                val inferredClassRelationships = if (consistent) {
                    inferredClassRelationships(
                        reasoner = reasoner,
                        ontology = document.ontology,
                        asserted = assertedClassRelationships.toSet(),
                        sourceId = sourceId,
                    )
                } else {
                    emptyList()
                }
                val assertedTypes = assertedIndividualTypes(graph, sourceId)
                val inferredTypes = if (consistent) {
                    inferredIndividualTypes(
                        reasoner = reasoner,
                        ontology = document.ontology,
                        asserted = assertedTypes.toSet(),
                        sourceId = sourceId,
                    )
                } else {
                    emptyList()
                }
                val assertedProperties = assertedPropertyRelationships(graph, objectPropertyIris, sourceId)
                val inferredProperties = if (consistent) {
                    inferredPropertyRelationships(
                        reasoner = reasoner,
                        ontology = document.ontology,
                        asserted = assertedProperties.toSet(),
                        sourceId = sourceId,
                    )
                } else {
                    emptyList()
                }
                val warnings = buildList {
                    if (!consistent) add("HermiT could not classify an inconsistent ontology.")
                    if (featureReport.findings.any { it.affectsCompleteness }) {
                        add("Certain assertions are currently unavailable. Reasoning completeness is not guaranteed.")
                    }
                    importClosure?.findings
                        ?.filter { it.kind.name != "Cycle" }
                        ?.map { it.message }
                        ?.sorted()
                        ?.forEach(::add)
                }

                EntioResult.Success(
                    ReasoningResult(
                        metadata = metadata,
                        consistency = if (importComplete) {
                            if (consistent) ConsistencyStatus.Consistent else ConsistencyStatus.Inconsistent
                        } else {
                            ConsistencyStatus.Unknown
                        },
                        classRelationships = (assertedClassRelationships + inferredClassRelationships)
                            .sortedWith(classRelationshipComparator),
                        individualTypes = (assertedTypes + inferredTypes)
                            .sortedWith(individualTypeComparator),
                        propertyRelationships = (assertedProperties + inferredProperties)
                            .sortedWith(propertyRelationshipComparator),
                        unsatisfiableClasses = unsatisfiableClasses,
                        unsupportedFeatures = featureReport.findings
                            .filter { it.support.name != "Supported" || it.affectsCompleteness }
                            .sortedBy { it.feature },
                        warnings = warnings,
                    ),
                )
            } finally {
                reasoner.dispose()
            }
        } catch (exception: RuntimeException) {
            failure("HermiT reasoning failed for the supplied graph.", exception)
        }
    }

    private fun loadInMemoryOntology(graph: GraphState): OwlOntologyDocument {
        val manager = OWLManager.createOWLOntologyManager()
        val ontologyIri = IRI.create(IN_MEMORY_ONTOLOGY_IRI)
        val ontology = if (graph.triples.isEmpty()) {
            manager.createOntology(ontologyIri)
        } else {
            val serialized = graph.toTurtle()
            val configuration = OWLOntologyLoaderConfiguration()
                .setMissingImportHandlingStrategy(MissingImportHandlingStrategy.THROW_EXCEPTION)
            manager.loadOntologyFromOntologyDocument(
                StringDocumentSource(serialized, ontologyIri),
                configuration,
            )
        }
        return OwlOntologyDocument(sourceId = IN_MEMORY_SOURCE_ID, ontology = ontology)
    }

    private fun GraphState.toTurtle(): String {
        val model = ModelFactory.createDefaultModel()
        triples.forEach { triple ->
            model.add(
                triple.subjectResource.toJenaResource(model),
                model.createProperty(triple.predicate.value),
                triple.objectTerm.toJenaNode(model),
            )
        }
        return StringWriter().also { writer ->
            RDFDataMgr.write(writer, model, Lang.TURTLE)
        }.toString()
    }

    private fun RdfResource.toJenaResource(model: Model): Resource = when (this) {
        is Iri -> model.createResource(value)
        is com.entio.core.BlankNodeResource -> model.createResource(AnonId.create(id))
    }

    private fun RdfTerm.toJenaNode(model: Model): RDFNode = when (this) {
        is RdfResource -> toJenaResource(model)
        is RdfLiteral -> when {
            languageTag != null -> model.createLiteral(lexicalForm, languageTag)
            datatypeIri != null -> model.createTypedLiteral(lexicalForm, datatypeIri?.value)
            else -> model.createLiteral(lexicalForm)
        }
    }

    private fun assertedClassRelationships(
        graph: GraphState,
        sourceId: String?,
    ): List<ReasoningClassRelationship> = graph.triples
        .flatMap { triple ->
            val subject = triple.subjectResource
            val objectClass = triple.objectTerm as? RdfResource
            if (objectClass !is Iri) return@flatMap emptyList()
            when (triple.predicate.value) {
                RDFS_SUBCLASS -> listOf(ReasoningClassRelationship(subject, objectClass, FactOrigin.Asserted, sourceId))
                OWL_EQUIVALENT_CLASS -> listOf(
                    ReasoningClassRelationship(subject, objectClass, FactOrigin.Asserted, sourceId),
                    ReasoningClassRelationship(objectClass, subject, FactOrigin.Asserted, sourceId),
                )
                else -> emptyList()
            }
        }
        .distinct()

    private fun inferredClassRelationships(
        reasoner: OWLReasoner,
        ontology: OWLOntology,
        asserted: Set<ReasoningClassRelationship>,
        sourceId: String?,
    ): List<ReasoningClassRelationship> {
        val assertedKeys = asserted.map { classKey(it.subject, it.objectClass) }.toSet()
        val inferred = mutableListOf<ReasoningClassRelationship>()
        ontology.classesInSignature().toList().sortedBy { it.iri.toString() }.forEach { owlClass ->
            val subject = toIriResource(owlClass)
            val superClasses = reasoner.getSuperClasses(owlClass, false).entities().toList()
            val equivalents = reasoner.getEquivalentClasses(owlClass).getEntitiesMinus(owlClass).toList()
            (superClasses + equivalents).forEach { objectClass ->
                if (objectClass.isOWLThing || objectClass.isOWLNothing) return@forEach
                val target = toIriResource(objectClass)
                if (classKey(subject, target) !in assertedKeys) {
                    inferred += ReasoningClassRelationship(subject, target, FactOrigin.Inferred, sourceId)
                }
            }
        }
        return inferred.distinct()
    }

    private fun assertedIndividualTypes(
        graph: GraphState,
        sourceId: String?,
    ): List<ReasoningIndividualType> = graph.triples.mapNotNull { triple ->
        if (triple.predicate.value != RDF_TYPE) return@mapNotNull null
        val type = triple.objectTerm as? Iri ?: return@mapNotNull null
        if (type.value in DECLARATION_TYPES) return@mapNotNull null
        ReasoningIndividualType(triple.subjectResource, type, FactOrigin.Asserted, sourceId)
    }.distinct()

    private fun inferredIndividualTypes(
        reasoner: OWLReasoner,
        ontology: OWLOntology,
        asserted: Set<ReasoningIndividualType>,
        sourceId: String?,
    ): List<ReasoningIndividualType> {
        val assertedKeys = asserted.map { individualTypeKey(it.individual, it.type) }.toSet()
        val inferred = mutableListOf<ReasoningIndividualType>()
        ontology.individualsInSignature().toList().sortedBy { it.iri.toString() }.forEach { individual ->
            val individualResource = toIriResource(individual)
            reasoner.getTypes(individual, false).entities().toList().forEach { type ->
                if (type.isOWLThing || type.isOWLNothing) return@forEach
                val typeResource = toIriResource(type)
                if (individualTypeKey(individualResource, typeResource) !in assertedKeys) {
                    inferred += ReasoningIndividualType(
                        individual = individualResource,
                        type = typeResource,
                        origin = FactOrigin.Inferred,
                        sourceId = sourceId,
                    )
                }
            }
        }
        return inferred.distinct()
    }

    private fun assertedPropertyRelationships(
        graph: GraphState,
        objectPropertyIris: Set<String>,
        sourceId: String?,
    ): List<ReasoningPropertyRelationship> = graph.triples.mapNotNull { triple ->
        val objectResource = triple.objectTerm as? Iri ?: return@mapNotNull null
        if (triple.predicate.value !in objectPropertyIris) return@mapNotNull null
        ReasoningPropertyRelationship(
            subject = triple.subjectResource,
            predicate = triple.predicate,
            objectResource = objectResource,
            origin = FactOrigin.Asserted,
            sourceId = sourceId,
        )
    }.distinct()

    private fun inferredPropertyRelationships(
        reasoner: OWLReasoner,
        ontology: OWLOntology,
        asserted: Set<ReasoningPropertyRelationship>,
        sourceId: String?,
    ): List<ReasoningPropertyRelationship> {
        val assertedKeys = asserted.map { propertyKey(it.subject, it.predicate, it.objectResource) }.toSet()
        val inferred = mutableListOf<ReasoningPropertyRelationship>()
        val individuals = ontology.individualsInSignature().toList().sortedBy { it.iri.toString() }
        val properties = ontology.objectPropertiesInSignature().toList().sortedBy { it.iri.toString() }
        individuals.forEach { individual ->
            properties.forEach { property ->
                reasoner.getObjectPropertyValues(individual, property).entities().toList().forEach { targetIndividual ->
                    val subjectResource = toIriResource(individual)
                    val predicate = Iri(property.iri.toString())
                    val objectResource = toIriResource(targetIndividual)
                    if (propertyKey(subjectResource, predicate, objectResource) !in assertedKeys) {
                        inferred += ReasoningPropertyRelationship(
                            subject = subjectResource,
                            predicate = predicate,
                            objectResource = objectResource,
                            origin = FactOrigin.Inferred,
                            sourceId = sourceId,
                        )
                    }
                }
            }
        }
        return inferred.distinct()
    }

    private fun toIriResource(entity: OWLClass): Iri = Iri(entity.iri.toString())

    private fun toIriResource(entity: OWLNamedIndividual): Iri = Iri(entity.iri.toString())

    private fun toIriResource(entity: OWLObjectProperty): Iri = Iri(entity.iri.toString())

    private fun failure(message: String, cause: Throwable): EntioResult.Failure = EntioResult.Failure(
        message = message,
        issues = listOf(
            ValidationIssue(
                severity = ValidationSeverity.Error,
                code = "reasoning-failed",
                message = message,
            ),
        ),
        cause = cause,
    )

    private fun fingerprint(value: String): String = sha256(value)

    private fun fingerprint(graph: GraphState): String = sha256(
        graph.triples
            .map(::canonicalTriple)
            .sorted()
            .joinToString("\n"),
    )

    private fun fingerprint(report: ImportClosureReport?): String = sha256(
        report?.let {
            buildString {
                append(it.sourceIds.sorted().joinToString(","))
                append("|")
                it.findings.sortedWith(compareBy({ finding -> finding.importedIri.value }, { finding -> finding.kind.name }))
                    .forEach { finding -> append(finding.kind.name).append(":").append(finding.importedIri.value).append("\n") }
                append("complete=").append(it.complete)
            }
        } ?: "no-import-closure",
    )

    private fun canonicalTriple(triple: GraphTriple): String = listOf(
        canonicalResource(triple.subjectResource),
        triple.predicate.value,
        canonicalTerm(triple.objectTerm),
    ).joinToString("|")

    private fun canonicalResource(resource: RdfResource): String = when (resource) {
        is Iri -> "iri:${resource.value}"
        is com.entio.core.BlankNodeResource -> "blank:${resource.id}"
    }

    private fun canonicalTerm(term: RdfTerm): String = when (term) {
        is RdfResource -> canonicalResource(term)
        is RdfLiteral -> "literal:${term.lexicalForm}|${term.datatypeIri?.value.orEmpty()}|${term.languageTag.orEmpty()}"
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        private const val IN_MEMORY_SOURCE_ID = "in-memory-preview"
        private const val IN_MEMORY_ONTOLOGY_IRI = "urn:entio:reasoning:in-memory"
        private const val REASONER_NAME = "HermiT"
        private const val REASONER_VERSION = "1.4.5.519"
        private const val OWL_API_VERSION = "5.1.9"
        private const val DEFAULT_REASONER_CONFIGURATION = "hermit-default"
        private const val RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
        private const val RDFS_SUBCLASS = "http://www.w3.org/2000/01/rdf-schema#subClassOf"
        private const val OWL_EQUIVALENT_CLASS = "http://www.w3.org/2002/07/owl#equivalentClass"
        private val DECLARATION_TYPES = setOf(
            "http://www.w3.org/2002/07/owl#Class",
            "http://www.w3.org/2002/07/owl#ObjectProperty",
            "http://www.w3.org/2002/07/owl#DatatypeProperty",
            "http://www.w3.org/2002/07/owl#AnnotationProperty",
            "http://www.w3.org/2002/07/owl#Ontology",
            "http://www.w3.org/2002/07/owl#NamedIndividual",
        )
        private val classRelationshipComparator = compareBy<ReasoningClassRelationship>(
            { it.subject.value },
            { it.objectClass.value },
            { it.origin.name },
        )
        private val individualTypeComparator = compareBy<ReasoningIndividualType>(
            { it.individual.value },
            { it.type.value },
            { it.origin.name },
        )
        private val propertyRelationshipComparator = compareBy<ReasoningPropertyRelationship>(
            { it.subject.value },
            { it.predicate.value },
            { it.objectResource.value },
            { it.origin.name },
        )
        private fun classKey(subject: RdfResource, objectClass: RdfResource): String =
            "${subject.value}|${objectClass.value}"

        private fun individualTypeKey(individual: RdfResource, type: RdfResource): String =
            "${individual.value}|${type.value}"

        private fun propertyKey(subject: RdfResource, predicate: Iri, objectResource: RdfResource): String =
            "${subject.value}|${predicate.value}|${objectResource.value}"
    }
}
