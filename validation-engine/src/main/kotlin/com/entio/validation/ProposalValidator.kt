package com.entio.validation

import com.entio.core.ChangeProposal
import com.entio.core.ChangeProposalStatus
import com.entio.core.GraphChangeKind
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.SemanticEquivalenceResult
import com.entio.core.ValidationIssue
import com.entio.core.ValidationReport
import com.entio.core.ValidationSeverity
import com.entio.core.ValidationStatus
import com.entio.semantic.GraphChangePreviewer
import com.entio.semantic.ProposalCreator

public class ProposalValidator(
    private val previewer: GraphChangePreviewer = GraphChangePreviewer(),
    private val proposalCreator: ProposalCreator = ProposalCreator(),
    private val issueSorter: ValidationIssueSorter = ValidationIssueSorter(),
    private val semanticMetadataValidator: SemanticMetadataValidator = SemanticMetadataValidator(),
) {
    public fun validateProposal(
        proposal: ChangeProposal,
        currentProject: EntioProject,
        projectValidationReport: ValidationReport = validReport(),
        semanticEquivalenceResult: SemanticEquivalenceResult? = null,
    ): ValidationReport {
        val issues = mutableListOf<ValidationIssue>()

        issues += projectValidationReport.issues
        issues += validateTargetSource(proposal, currentProject)
        issues += validatePreview(proposal, currentProject)
        issues += validateCurrentBaseline(proposal, currentProject)
        issues += validateSemanticEquivalence(proposal, semanticEquivalenceResult)
        // Validate metadata against the complete draft graph. Keep baseline
        // resources visible as well so removing an existing label or
        // definition remains a valid operation.
        val validationGraph = proposal.preview?.graph?.let { previewGraph ->
            GraphState(currentProject.graph.triples + previewGraph.triples)
        } ?: currentProject.graph
        issues += semanticMetadataValidator.validate(validationGraph, proposal.changeSet.changes)
        issues += validateEditSpecificRules(proposal, currentProject.graph)

        return report(issues)
    }

    private fun validateTargetSource(
        proposal: ChangeProposal,
        currentProject: EntioProject,
    ): List<ValidationIssue> {
        val targetSourceExists = currentProject.resolvedSources.any { source -> source.id == proposal.targetSourceId }

        return if (targetSourceExists) {
            emptyList()
        } else {
            listOf(
                ValidationIssue(
                    severity = ValidationSeverity.Error,
                    code = "missing-target-source",
                    message = "Target ontology source '${proposal.targetSourceId}' was not found.",
                    source = proposal.targetSourceId,
                ),
            )
        }
    }

    private fun validatePreview(
        proposal: ChangeProposal,
        currentProject: EntioProject,
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()

        if (proposal.preview == null) {
            issues += ValidationIssue(
                severity = ValidationSeverity.Error,
                code = "missing-proposal-preview",
                message = "Proposal must include a preview graph before validation.",
                source = proposal.id,
            )
        }

        when (val previewResult = previewer.preview(currentProject.graph, proposal.changeSet)) {
            is EntioResult.Failure -> issues += previewResult.issues
            is EntioResult.Success -> Unit
        }

        return issues
    }

    private fun validateCurrentBaseline(
        proposal: ChangeProposal,
        currentProject: EntioProject,
    ): List<ValidationIssue> {
        if (currentProject.resolvedSources.none { source -> source.id == proposal.targetSourceId }) {
            return emptyList()
        }

        return when (val result = proposalCreator.isCurrent(proposal, currentProject)) {
            is EntioResult.Failure -> result.issues
            is EntioResult.Success -> if (result.value) {
                emptyList()
            } else {
                listOf(
                    ValidationIssue(
                        severity = ValidationSeverity.Error,
                        code = "stale-proposal-baseline",
                        message = "Proposal baseline no longer matches the current project state.",
                        source = proposal.id,
                    ),
                )
            }
        }
    }

    private fun validateSemanticEquivalence(
        proposal: ChangeProposal,
        result: SemanticEquivalenceResult?,
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()

        if (proposal.status == ChangeProposalStatus.VerificationFailed) {
            issues += ValidationIssue(
                severity = ValidationSeverity.Error,
                code = "proposal-verification-failed",
                message = "Proposal is marked as verification failed.",
                source = proposal.id,
            )
        }

        when (result) {
            null,
            SemanticEquivalenceResult.Equivalent,
            -> Unit
            is SemanticEquivalenceResult.NotEquivalent -> issues += ValidationIssue(
                severity = ValidationSeverity.Error,
                code = "semantic-equivalence-failed",
                message = result.reason,
                source = proposal.id,
            )
            is SemanticEquivalenceResult.Failed -> issues += ValidationIssue(
                severity = ValidationSeverity.Error,
                code = "semantic-equivalence-check-failed",
                message = result.reason,
                source = proposal.id,
            )
        }

        return issues
    }

    private fun validateEditSpecificRules(
        proposal: ChangeProposal,
        currentGraph: GraphState,
    ): List<ValidationIssue> {
        val facts = GraphFacts(currentGraph, proposal.changeSet.changes)
        return proposal.changeSet.changes.flatMapIndexed { index, change ->
            when (change.kind) {
                GraphChangeKind.Addition -> validateAddition(change.triple, index, facts)
                GraphChangeKind.Removal -> validateRemoval(change.triple, index, facts)
            }
        }
    }

    private fun validateAddition(
        triple: GraphTriple,
        index: Int,
        facts: GraphFacts,
    ): List<ValidationIssue> {
        val source = changeSource(index)
        val objectIri = triple.objectTerm as? Iri
        val issues = mutableListOf<ValidationIssue>()

        when (triple.predicate.value) {
            RDF_TYPE -> {
                if (objectIri != null) {
                    if (objectIri.value !in BUILT_IN_TYPES && !facts.isClass(objectIri)) {
                        issues += missingClassIssue(objectIri, source)
                    }
                    if (!facts.isKnownResource(triple.subjectResource)) {
                        issues += missingResourceIssue(triple.subjectResource, source)
                    }
                    if (objectIri.value == OWL_OBJECT_PROPERTY && facts.hasType(triple.subjectResource, OWL_DATATYPE_PROPERTY)) {
                        issues += incompatibleDeclarationIssue(triple.subjectResource, source)
                    }
                    if (objectIri.value == OWL_DATATYPE_PROPERTY && facts.hasType(triple.subjectResource, OWL_OBJECT_PROPERTY)) {
                        issues += incompatibleDeclarationIssue(triple.subjectResource, source)
                    }
                    if (objectIri.value in setOf(OWL_CLASS, RDFS_CLASS) && facts.propertyKind(triple.subjectResource) != PropertyKind.Missing) {
                        issues += incompatibleDeclarationIssue(triple.subjectResource, source)
                    }
                }
            }

            RDFS_SUBCLASS_OF -> {
                val superclass = objectIri
                if (superclass != null) {
                    if (triple.subjectResource.value == superclass.value) {
                        issues += issue(
                            code = "self-subclass",
                            message = "A class cannot be made a superclass of itself.",
                            source = source,
                        )
                    }
                    if (!facts.isClass(triple.subjectResource)) {
                        issues += missingClassIssue(triple.subjectResource, source)
                    }
                    if (!facts.isClass(superclass)) {
                        issues += missingClassIssue(superclass, source)
                    }
                }
            }

            RDFS_DOMAIN -> {
                if (objectIri != null) {
                    issues += validatePropertyReference(triple.subjectResource, PropertyKind.Any, source, facts)
                    if (!facts.isClass(objectIri)) {
                        issues += missingClassIssue(objectIri, source)
                    }
                }
            }

            RDFS_RANGE -> {
                if (objectIri != null) {
                    val propertyKind = facts.propertyKind(triple.subjectResource)
                    issues += validatePropertyReference(triple.subjectResource, PropertyKind.Any, source, facts)
                    when (propertyKind) {
                        PropertyKind.Object -> if (!facts.isClass(objectIri)) {
                            issues += missingClassIssue(objectIri, source)
                        }

                        PropertyKind.Datatype -> if (!isSupportedDatatype(objectIri)) {
                            issues += issue(
                                code = "incompatible-property-range",
                                message = "Datatype property range '${objectIri.value}' is not a supported datatype.",
                                source = source,
                            )
                        }

                        else -> Unit
                    }
                }
            }

            RDFS_LABEL -> {
                // SemanticMetadataValidator owns all metadata predicates, including labels.
            }

            else -> {
                if (triple.predicate.value !in STRUCTURAL_PREDICATES &&
                    triple.predicate.value !in SEMANTIC_METADATA_PREDICATES &&
                    !facts.isAnnotationProperty(triple.predicate)
                ) {
                    val propertyKind = if (triple.objectTerm is RdfLiteral) {
                        PropertyKind.Datatype
                    } else {
                        PropertyKind.Object
                    }
                    issues += validatePropertyReference(triple.predicate, propertyKind, source, facts)
                    if (!facts.isKnownResource(triple.subjectResource)) {
                        issues += missingResourceIssue(triple.subjectResource, source)
                    }
                    when (val value = triple.objectTerm) {
                        is RdfLiteral -> {
                            issues += validateLiteral(value, source)
                            issues += validateLiteralRange(triple.predicate, value, source, facts)
                        }

                        is RdfResource -> {
                            if (!facts.isKnownResource(value)) {
                                issues += missingResourceIssue(value, source)
                            }
                            issues += validateObjectDomainAndRange(
                                subject = triple.subjectResource,
                                property = triple.predicate,
                                objectResource = value,
                                source = source,
                                facts = facts,
                            )
                        }
                    }
                }
            }
        }

        return issues
    }

    private fun validateRemoval(
        triple: GraphTriple,
        index: Int,
        facts: GraphFacts,
    ): List<ValidationIssue> {
        if (triple.predicate.value != RDFS_SUBCLASS_OF) {
            return emptyList()
        }

        val superclass = triple.objectTerm as? Iri ?: return emptyList()
        val source = changeSource(index)
        return buildList {
            if (triple.subjectResource.value == superclass.value) {
                add(
                    issue(
                        code = "self-subclass",
                        message = "A class cannot be made a superclass of itself.",
                        source = source,
                    ),
                )
            }
            if (!facts.isClass(triple.subjectResource)) add(missingClassIssue(triple.subjectResource, source))
            if (!facts.isClass(superclass)) add(missingClassIssue(superclass, source))
        }
    }

    private fun validatePropertyReference(
        property: RdfResource,
        expected: PropertyKind,
        source: String,
        facts: GraphFacts,
    ): List<ValidationIssue> {
        if (!facts.isKnownResource(property)) {
            return listOf(missingResourceIssue(property, source))
        }

        val actual = facts.propertyKind(property)
        if (expected != PropertyKind.Any && actual != expected) {
            return listOf(
                issue(
                    code = "incompatible-property-kind",
                    message = "Property '${property.value}' is ${actual.description}, but this edit requires a ${expected.description} property.",
                    source = source,
                ),
            )
        }
        if (actual == PropertyKind.Missing) {
            return listOf(
                issue(
                    code = "missing-property",
                    message = "Property '${property.value}' is not declared in the current graph.",
                    source = source,
                ),
            )
        }
        if (actual == PropertyKind.Ambiguous) {
            return listOf(
                issue(
                    code = "incompatible-property-kind",
                    message = "Property '${property.value}' has incompatible object and datatype declarations.",
                    source = source,
                ),
            )
        }
        return emptyList()
    }

    private fun validateObjectDomainAndRange(
        subject: RdfResource,
        property: Iri,
        objectResource: RdfResource,
        source: String,
        facts: GraphFacts,
    ): List<ValidationIssue> = buildList {
        val domains = facts.objects(property, RDFS_DOMAIN)
        if (domains.isNotEmpty() && facts.hasExplicitType(subject) && domains.none { domain -> facts.hasType(subject, domain.value) }) {
            add(
                issue(
                    code = "incompatible-property-domain",
                    message = "Subject '${subject.value}' does not have a known domain class for property '${property.value}'.",
                    source = source,
                ),
            )
        }

        val ranges = facts.objects(property, RDFS_RANGE)
        if (ranges.isNotEmpty() && facts.hasExplicitType(objectResource) && ranges.none { range -> facts.hasType(objectResource, range.value) }) {
            val declaredRanges = ranges.joinToString(", ") { range -> "'${range.value}'" }
            add(
                issue(
                    code = "incompatible-property-range",
                    message = "Object '${objectResource.value}' is not an instance of the declared range ${declaredRanges} for property '${property.value}'.",
                    source = source,
                ),
            )
        }
    }

    private fun validateLiteralRange(
        property: Iri,
        literal: RdfLiteral,
        source: String,
        facts: GraphFacts,
    ): List<ValidationIssue> {
        val ranges = facts.objects(property, RDFS_RANGE)
        val expectedDatatype = ranges.singleOrNull() ?: return emptyList()
        if (!isSupportedDatatype(expectedDatatype)) return emptyList()

        val actualDatatype = literal.datatypeIri ?: if (literal.languageTag == null) Iri(XSD_STRING) else Iri(RDF_LANG_STRING)
        return if (actualDatatype.value == expectedDatatype.value && literalIsValid(literal, expectedDatatype)) {
            emptyList()
        } else {
            listOf(
                issue(
                    code = "incompatible-literal",
                    message = "Literal value is incompatible with property range '${expectedDatatype.value}'.",
                    source = source,
                ),
            )
        }
    }

    private fun validateLabel(
        term: com.entio.core.RdfTerm,
        source: String,
    ): List<ValidationIssue> = when (term) {
        is RdfLiteral -> validateLiteral(term, source)
        is RdfResource -> listOf(
            issue(
                code = "invalid-label",
                message = "Entity labels must be RDF literals.",
                source = source,
            ),
        )
    }

    private fun validateLiteral(
        literal: RdfLiteral,
        source: String,
    ): List<ValidationIssue> = buildList {
        val languageTag = literal.languageTag
        if (languageTag != null && !LANGUAGE_TAG.matches(languageTag)) {
            add(
                issue(
                    code = "invalid-language-tag",
                    message = "Language tag '$languageTag' is not valid.",
                    source = source,
                ),
            )
        }
        if (languageTag != null && literal.datatypeIri != null) {
            add(
                issue(
                    code = "incompatible-literal",
                    message = "A literal cannot contain both a language tag and an explicit datatype.",
                    source = source,
                ),
            )
        }
        val datatype = literal.datatypeIri
        if (datatype != null && !isSupportedDatatype(datatype) && datatype.value != RDF_LANG_STRING) {
            add(
                issue(
                    code = "unsupported-datatype",
                    message = "Datatype '${datatype.value}' is not supported by Phase 2.5 validation.",
                    source = source,
                ),
            )
        } else if (datatype != null && datatype.value != RDF_LANG_STRING && !literalIsValid(literal, datatype)) {
            add(
                issue(
                    code = "invalid-literal",
                    message = "Literal value '${literal.lexicalForm}' is not valid for datatype '${datatype.value}'.",
                    source = source,
                ),
            )
        }
    }

    private fun literalIsValid(
        literal: RdfLiteral,
        datatype: Iri,
    ): Boolean = when (datatype.value) {
        XSD_STRING -> true
        XSD_BOOLEAN -> literal.lexicalForm in setOf("true", "false", "1", "0")
        XSD_INTEGER -> INTEGER_VALUE.matches(literal.lexicalForm)
        XSD_DECIMAL -> DECIMAL_VALUE.matches(literal.lexicalForm)
        XSD_DATE -> DATE_VALUE.matches(literal.lexicalForm)
        XSD_DATE_TIME -> DATE_TIME_VALUE.matches(literal.lexicalForm)
        else -> true
    }

    private fun isSupportedDatatype(iri: Iri): Boolean = iri.value in SUPPORTED_DATATYPES

    private fun missingClassIssue(resource: RdfResource, source: String): ValidationIssue =
        issue(
            code = "missing-class-reference",
            message = "Class '${resource.value}' is not declared in the current graph.",
            source = source,
        )

    private fun missingResourceIssue(resource: RdfResource, source: String): ValidationIssue =
        issue(
            code = "missing-referenced-resource",
            message = "Referenced resource '${resource.value}' is not present in the current graph.",
            source = source,
        )

    private fun incompatibleDeclarationIssue(resource: RdfResource, source: String): ValidationIssue =
        issue(
            code = "incompatible-symbol-kind",
            message = "Resource '${resource.value}' already has an incompatible declared kind.",
            source = source,
        )

    private fun issue(
        code: String,
        message: String,
        source: String,
    ): ValidationIssue = ValidationIssue(
        severity = ValidationSeverity.Error,
        code = code,
        message = message,
        source = source,
    )

    private fun changeSource(index: Int): String = "changeSet.changes[$index]"

    private class GraphFacts(
        currentGraph: GraphState,
        changes: List<com.entio.core.GraphChange>,
    ) {
        private val currentTriples: Set<GraphTriple> = currentGraph.triples
        private val removedTriples: Set<GraphTriple> = changes
            .filter { it.kind == GraphChangeKind.Removal }
            .map { it.triple }
            .toSet()
        private val plannedTriples: Set<GraphTriple> = changes
            .filter { it.kind == GraphChangeKind.Addition }
            .map { it.triple }
            .toSet()
        private val plannedDeclarations: Set<RdfResource> = changes
            .filter { it.kind == GraphChangeKind.Addition }
            .mapNotNull { change ->
                if (change.triple.predicate.value == RDF_TYPE) change.triple.subjectResource else null
            }
            .toSet()

        fun isKnownResource(resource: RdfResource): Boolean = resource.value in BUILT_IN_RESOURCES || resource in currentResources() || resource in plannedDeclarations

        fun isClass(resource: RdfResource): Boolean = hasType(resource, OWL_CLASS) || hasType(resource, RDFS_CLASS)

        fun hasExplicitType(resource: RdfResource): Boolean = allTriples().any {
            it.subjectResource == resource && it.predicate.value == RDF_TYPE && it.objectTerm is Iri
        }

        fun hasType(resource: RdfResource, type: String): Boolean = allTriples().any {
            it.subjectResource == resource && it.predicate.value == RDF_TYPE && (it.objectTerm as? RdfResource)?.value == type
        }

        fun propertyKind(resource: RdfResource): PropertyKind {
            val objectProperty = hasType(resource, OWL_OBJECT_PROPERTY)
            val datatypeProperty = hasType(resource, OWL_DATATYPE_PROPERTY)
            val genericProperty = hasType(resource, RDF_PROPERTY)
            return when {
                objectProperty && datatypeProperty -> PropertyKind.Ambiguous
                datatypeProperty -> PropertyKind.Datatype
                objectProperty || genericProperty -> PropertyKind.Object
                else -> PropertyKind.Missing
            }
        }

        fun isAnnotationProperty(resource: RdfResource): Boolean = hasType(resource, OWL_ANNOTATION_PROPERTY)

        fun objects(subject: RdfResource, predicate: String): List<Iri> = allTriples()
            .filter { it.subjectResource == subject && it.predicate.value == predicate }
            .mapNotNull { it.objectTerm as? Iri }

        private fun currentResources(): Set<RdfResource> = currentTriples
            .flatMap { triple ->
                buildList {
                    add(triple.subjectResource)
                    val objectResource = triple.objectTerm as? RdfResource
                    if (objectResource != null) add(objectResource)
                }
            }
            .toSet()

        private fun allTriples(): Set<GraphTriple> = (currentTriples - removedTriples) + plannedTriples
    }

    private enum class PropertyKind(
        val description: String,
    ) {
        Any("property"),
        Object("object"),
        Datatype("datatype"),
        Missing("unknown"),
        Ambiguous("ambiguous"),
    }

    private fun report(issues: List<ValidationIssue>): ValidationReport {
        val sortedIssues = issueSorter.sortIssues(issues)
        val status = if (sortedIssues.any { issue -> issue.severity == ValidationSeverity.Error }) {
            ValidationStatus.Invalid
        } else {
            ValidationStatus.Valid
        }

        return ValidationReport(
            status = status,
            issues = sortedIssues,
        )
    }

    private companion object {
        private const val RDF_TYPE: String = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
        private const val RDFS_LABEL: String = "http://www.w3.org/2000/01/rdf-schema#label"
        private const val RDFS_SUBCLASS_OF: String = "http://www.w3.org/2000/01/rdf-schema#subClassOf"
        private const val RDFS_DOMAIN: String = "http://www.w3.org/2000/01/rdf-schema#domain"
        private const val RDFS_RANGE: String = "http://www.w3.org/2000/01/rdf-schema#range"
        private const val RDF_PROPERTY: String = "http://www.w3.org/1999/02/22-rdf-syntax-ns#Property"
        private const val OWL_CLASS: String = "http://www.w3.org/2002/07/owl#Class"
        private const val RDFS_CLASS: String = "http://www.w3.org/2000/01/rdf-schema#Class"
        private const val OWL_OBJECT_PROPERTY: String = "http://www.w3.org/2002/07/owl#ObjectProperty"
        private const val OWL_DATATYPE_PROPERTY: String = "http://www.w3.org/2002/07/owl#DatatypeProperty"
        private const val OWL_NAMED_INDIVIDUAL: String = "http://www.w3.org/2002/07/owl#NamedIndividual"
        private const val OWL_ANNOTATION_PROPERTY: String = "http://www.w3.org/2002/07/owl#AnnotationProperty"
        private const val RDF_LANG_STRING: String = "http://www.w3.org/1999/02/22-rdf-syntax-ns#langString"
        private const val XSD_STRING: String = "http://www.w3.org/2001/XMLSchema#string"
        private const val XSD_BOOLEAN: String = "http://www.w3.org/2001/XMLSchema#boolean"
        private const val XSD_INTEGER: String = "http://www.w3.org/2001/XMLSchema#integer"
        private const val XSD_DECIMAL: String = "http://www.w3.org/2001/XMLSchema#decimal"
        private const val XSD_DATE: String = "http://www.w3.org/2001/XMLSchema#date"
        private const val XSD_DATE_TIME: String = "http://www.w3.org/2001/XMLSchema#dateTime"
        private val BUILT_IN_TYPES: Set<String> = setOf(
            OWL_CLASS,
            RDFS_CLASS,
            RDF_PROPERTY,
            OWL_OBJECT_PROPERTY,
            OWL_DATATYPE_PROPERTY,
            OWL_NAMED_INDIVIDUAL,
            OWL_ANNOTATION_PROPERTY,
        )
        private val SUPPORTED_DATATYPES: Set<String> = setOf(
            XSD_STRING,
            XSD_BOOLEAN,
            XSD_INTEGER,
            XSD_DECIMAL,
            XSD_DATE,
            XSD_DATE_TIME,
        )
        private val BUILT_IN_RESOURCES: Set<String> = setOf(
            RDF_TYPE,
            RDF_PROPERTY,
            RDFS_LABEL,
            RDFS_SUBCLASS_OF,
            RDFS_DOMAIN,
            RDFS_RANGE,
            OWL_CLASS,
            RDFS_CLASS,
            OWL_OBJECT_PROPERTY,
            OWL_DATATYPE_PROPERTY,
            OWL_NAMED_INDIVIDUAL,
            OWL_ANNOTATION_PROPERTY,
            RDF_LANG_STRING,
        ) + SUPPORTED_DATATYPES
        private val STRUCTURAL_PREDICATES: Set<String> = setOf(
            RDF_TYPE,
            RDFS_LABEL,
            RDFS_SUBCLASS_OF,
            RDFS_DOMAIN,
            RDFS_RANGE,
        )
        private val SEMANTIC_METADATA_PREDICATES: Set<String> = setOf(
            RDFS_LABEL,
            "http://www.w3.org/2000/01/rdf-schema#comment",
            "http://www.w3.org/2004/02/skos/core#prefLabel",
            "http://www.w3.org/2004/02/skos/core#altLabel",
            "http://www.w3.org/2004/02/skos/core#definition",
            "http://purl.org/dc/terms/source",
        )
        private val LANGUAGE_TAG = Regex("[A-Za-z]{1,8}(?:-[A-Za-z0-9]{1,8})*")
        private val INTEGER_VALUE = Regex("[+-]?[0-9]+")
        private val DECIMAL_VALUE = Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)")
        private val DATE_VALUE = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
        private val DATE_TIME_VALUE = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]+)?(?:Z|[+-][0-9]{2}:[0-9]{2})?")

        private fun validReport(): ValidationReport =
            ValidationReport(
                status = ValidationStatus.Valid,
                issues = emptyList(),
            )
    }
}
