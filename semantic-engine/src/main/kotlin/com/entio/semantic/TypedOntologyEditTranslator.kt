package com.entio.semantic

import com.entio.core.AddDatatypePropertyAssertionEdit
import com.entio.core.AddObjectPropertyAssertionEdit
import com.entio.core.AddSuperclassEdit
import com.entio.core.AssignTypeEdit
import com.entio.core.ChangeSet
import com.entio.core.CreateClassEdit
import com.entio.core.CreateDatatypePropertyEdit
import com.entio.core.CreateIndividualEdit
import com.entio.core.CreateObjectPropertyEdit
import com.entio.core.EntioResult
import com.entio.core.GraphChange
import com.entio.core.GraphChangeKind
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.RemoveSuperclassEdit
import com.entio.core.RemovePropertyDomainEdit
import com.entio.core.RemovePropertyRangeEdit
import com.entio.core.SetEntityLabelEdit
import com.entio.core.SetPropertyDomainEdit
import com.entio.core.SetPropertyRangeEdit
import com.entio.core.SemanticEditRequest
import com.entio.core.TypedOntologyEdit
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity

public class TypedOntologyEditTranslator {
    public fun translate(edit: TypedOntologyEdit): EntioResult<ChangeSet> {
        val issues = edit.validationIssues()
        if (issues.isNotEmpty()) {
            return EntioResult.Failure(
                message = "Typed ontology edit is invalid.",
                issues = issues,
            )
        }

        return EntioResult.Success(
            ChangeSet(
                changes = edit.toChanges(),
            ),
        )
    }

    /**
     * Translates edits whose meaning depends on the current graph.
     *
     * A preferred-label edit replaces every existing `rdfs:label` for the entity
     * instead of blindly adding another label. A null result means the requested
     * state is already fully represented by the graph.
     */
    public fun translateAgainstGraph(
        edit: TypedOntologyEdit,
        currentGraph: GraphState,
    ): EntioResult<ChangeSet?> {
        val issues = edit.validationIssues()
        if (issues.isNotEmpty()) {
            return EntioResult.Failure(
                message = "Typed ontology edit is invalid.",
                issues = issues,
            )
        }

        val changes = when (edit) {
            is SetEntityLabelEdit -> edit.labelReplacementChanges(currentGraph)
            else -> edit.toChanges()
        }
        return EntioResult.Success(changes.takeIf(List<GraphChange>::isNotEmpty)?.let(::ChangeSet))
    }

    public fun translate(
        edit: SemanticEditRequest,
        existingAnnotationProperties: Set<Iri>? = null,
    ): EntioResult<ChangeSet> {
        val issues = edit.validationIssues(existingAnnotationProperties)
        if (issues.isNotEmpty()) {
            return EntioResult.Failure(
                message = "Semantic ontology edit is invalid.",
                issues = issues,
            )
        }

        return EntioResult.Success(ChangeSet(changes = edit.toChanges()))
    }

    private fun TypedOntologyEdit.toChanges(): List<GraphChange> =
        when (this) {
            is CreateClassEdit -> typeAssertion(classIri, OWL_CLASS) + optionalLabel(classIri, label)
            is SetEntityLabelEdit -> listOf(addTriple(entity, RDFS_LABEL, label))
            is AddSuperclassEdit -> listOf(addTriple(classIri, RDFS_SUBCLASS_OF, superclassIri))
            is RemoveSuperclassEdit -> listOf(removeTriple(classIri, RDFS_SUBCLASS_OF, superclassIri))
            is CreateObjectPropertyEdit -> typeAssertion(propertyIri, OWL_OBJECT_PROPERTY) + optionalLabel(propertyIri, label)
            is CreateDatatypePropertyEdit -> typeAssertion(propertyIri, OWL_DATATYPE_PROPERTY) + optionalLabel(propertyIri, label)
            is SetPropertyDomainEdit -> listOf(addTriple(propertyIri, RDFS_DOMAIN, domainClassIri))
            is SetPropertyRangeEdit -> listOf(addTriple(propertyIri, RDFS_RANGE, rangeIri))
            is RemovePropertyDomainEdit -> listOf(removeTriple(propertyIri, RDFS_DOMAIN, domainClassIri))
            is RemovePropertyRangeEdit -> listOf(removeTriple(propertyIri, RDFS_RANGE, rangeIri))
            is CreateIndividualEdit -> typeAssertion(individualIri, OWL_NAMED_INDIVIDUAL) +
                classIri?.let { listOf(addTriple(individualIri, RDF_TYPE, it)) }.orEmpty()
            is AssignTypeEdit -> listOf(addTriple(resource, RDF_TYPE, typeIri))
            is AddObjectPropertyAssertionEdit -> listOf(addTriple(subject, propertyIri, objectResource))
            is AddDatatypePropertyAssertionEdit -> listOf(addTriple(subject, propertyIri, value))
        }

    private fun SetEntityLabelEdit.labelReplacementChanges(currentGraph: GraphState): List<GraphChange> {
        val replacement = GraphTriple(entity, RDFS_LABEL, label)
        val existingLabels = currentGraph.triples.filter { triple ->
            triple.subjectResource == entity &&
                triple.predicate == RDFS_LABEL
        }
        return existingLabels
            .filterNot { it == replacement }
            .map { GraphChange(GraphChangeKind.Removal, it) } +
            if (replacement in currentGraph.triples) emptyList() else listOf(GraphChange(GraphChangeKind.Addition, replacement))
    }

    private fun SemanticEditRequest.toChanges(): List<GraphChange> =
        when (this) {
            is SemanticEditRequest.CreateAnnotationProperty -> typeAssertion(propertyIri, OWL_ANNOTATION_PROPERTY) +
                label?.let { listOf(addTriple(propertyIri, RDFS_LABEL, it)) }.orEmpty() +
                definition?.let { listOf(addTriple(propertyIri, SKOS_DEFINITION, it)) }.orEmpty()
            is SemanticEditRequest.AddDefinition -> listOf(addTriple(target, SKOS_DEFINITION, value))
            is SemanticEditRequest.ReplaceDefinition -> listOf(
                removeTriple(target, SKOS_DEFINITION, existing),
                addTriple(target, SKOS_DEFINITION, replacement),
            )
            is SemanticEditRequest.RemoveDefinition -> listOf(removeTriple(target, SKOS_DEFINITION, value))
            is SemanticEditRequest.AddAlternateLabel -> listOf(addTriple(target, SKOS_ALT_LABEL, value))
            is SemanticEditRequest.ReplaceAlternateLabel -> listOf(
                removeTriple(target, SKOS_ALT_LABEL, existing),
                addTriple(target, SKOS_ALT_LABEL, replacement),
            )
            is SemanticEditRequest.RemoveAlternateLabel -> listOf(removeTriple(target, SKOS_ALT_LABEL, value))
            is SemanticEditRequest.AddAnnotation -> listOf(addTriple(target, property, value.term))
            is SemanticEditRequest.RemoveAnnotation -> listOf(removeTriple(target, property, value.term))
        }

    private fun typeAssertion(
        resource: RdfResource,
        typeIri: Iri,
    ): List<GraphChange> =
        listOf(addTriple(resource, RDF_TYPE, typeIri))

    private fun optionalLabel(
        resource: RdfResource,
        label: RdfLiteral?,
    ): List<GraphChange> =
        label?.let { listOf(addTriple(resource, RDFS_LABEL, it)) }.orEmpty()

    private fun addTriple(
        subject: RdfResource,
        predicate: Iri,
        objectTerm: com.entio.core.RdfTerm,
    ): GraphChange =
        GraphChange(
            kind = GraphChangeKind.Addition,
            triple = GraphTriple(
                subject = subject,
                predicate = predicate,
                objectTerm = objectTerm,
            ),
        )

    private fun removeTriple(
        subject: RdfResource,
        predicate: Iri,
        objectTerm: com.entio.core.RdfTerm,
    ): GraphChange =
        GraphChange(
            kind = GraphChangeKind.Removal,
            triple = GraphTriple(
                subject = subject,
                predicate = predicate,
                objectTerm = objectTerm,
            ),
        )

    private fun TypedOntologyEdit.validationIssues(): List<ValidationIssue> =
        resourcesToValidate()
            .filter { (_, resource) -> resource.value.isBlank() }
            .map { (field, _) ->
                ValidationIssue(
                    severity = ValidationSeverity.Error,
                    code = "invalid-iri",
                    message = "Typed ontology edit field '$field' must not be blank.",
                    source = field,
                )
            }

    private fun TypedOntologyEdit.resourcesToValidate(): List<Pair<String, RdfResource>> =
        when (this) {
            is CreateClassEdit -> listOf("classIri" to classIri)
            is SetEntityLabelEdit -> listOf("entity" to entity)
            is AddSuperclassEdit -> listOf("classIri" to classIri, "superclassIri" to superclassIri)
            is RemoveSuperclassEdit -> listOf("classIri" to classIri, "superclassIri" to superclassIri)
            is CreateObjectPropertyEdit -> listOf("propertyIri" to propertyIri)
            is CreateDatatypePropertyEdit -> listOf("propertyIri" to propertyIri)
            is SetPropertyDomainEdit -> listOf("propertyIri" to propertyIri, "domainClassIri" to domainClassIri)
            is SetPropertyRangeEdit -> listOf("propertyIri" to propertyIri, "rangeIri" to rangeIri)
            is RemovePropertyDomainEdit -> listOf("propertyIri" to propertyIri, "domainClassIri" to domainClassIri)
            is RemovePropertyRangeEdit -> listOf("propertyIri" to propertyIri, "rangeIri" to rangeIri)
            is CreateIndividualEdit -> listOfNotNull("individualIri" to individualIri, classIri?.let { "classIri" to it })
            is AssignTypeEdit -> listOf("resource" to resource, "typeIri" to typeIri)
            is AddObjectPropertyAssertionEdit -> listOf(
                "subject" to subject,
                "propertyIri" to propertyIri,
                "objectResource" to objectResource,
            )
            is AddDatatypePropertyAssertionEdit -> listOf("subject" to subject, "propertyIri" to propertyIri)
        }

    private fun SemanticEditRequest.validationIssues(
        existingAnnotationProperties: Set<Iri>?,
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        resourcesToValidate().filter { (_, resource) -> resource.value.isBlank() }.forEach { (field, _) ->
            issues += ValidationIssue(
                severity = ValidationSeverity.Error,
                code = "invalid-iri",
                message = "Semantic ontology edit field '$field' must not be blank.",
                source = field,
            )
        }
        literalsToValidate().filter { (_, literal) -> literal.lexicalForm.isBlank() }.forEach { (field, _) ->
            issues += ValidationIssue(
                severity = ValidationSeverity.Error,
                code = "missing-annotation-value",
                message = "Semantic ontology edit field '$field' must not be blank.",
                source = field,
            )
        }
        if (existingAnnotationProperties != null) {
            annotationPropertiesToValidate()
                .filterNot { it in existingAnnotationProperties }
                .forEach { property ->
                    issues += ValidationIssue(
                        severity = ValidationSeverity.Error,
                        code = "missing-annotation-property",
                        message = "Annotation property '${property.value}' does not exist.",
                        source = property.value,
                    )
                }
        }
        return issues
    }

    private fun SemanticEditRequest.resourcesToValidate(): List<Pair<String, RdfResource>> =
        when (this) {
            is SemanticEditRequest.CreateAnnotationProperty -> listOf("propertyIri" to propertyIri)
            is SemanticEditRequest.AddDefinition -> listOf("target" to target)
            is SemanticEditRequest.ReplaceDefinition -> listOf("target" to target)
            is SemanticEditRequest.RemoveDefinition -> listOf("target" to target)
            is SemanticEditRequest.AddAlternateLabel -> listOf("target" to target)
            is SemanticEditRequest.ReplaceAlternateLabel -> listOf("target" to target)
            is SemanticEditRequest.RemoveAlternateLabel -> listOf("target" to target)
            is SemanticEditRequest.AddAnnotation -> listOf("target" to target, "property" to property)
            is SemanticEditRequest.RemoveAnnotation -> listOf("target" to target, "property" to property)
        }

    private fun SemanticEditRequest.literalsToValidate(): List<Pair<String, RdfLiteral>> =
        when (this) {
            is SemanticEditRequest.CreateAnnotationProperty -> listOfNotNull(
                label?.let { "label" to it },
                definition?.let { "definition" to it },
            )
            is SemanticEditRequest.AddDefinition -> listOf("value" to value)
            is SemanticEditRequest.ReplaceDefinition -> listOf("existing" to existing, "replacement" to replacement)
            is SemanticEditRequest.RemoveDefinition -> listOf("value" to value)
            is SemanticEditRequest.AddAlternateLabel -> listOf("value" to value)
            is SemanticEditRequest.ReplaceAlternateLabel -> listOf("existing" to existing, "replacement" to replacement)
            is SemanticEditRequest.RemoveAlternateLabel -> listOf("value" to value)
            is SemanticEditRequest.AddAnnotation,
            is SemanticEditRequest.RemoveAnnotation,
            -> emptyList()
        }

    private fun SemanticEditRequest.annotationPropertiesToValidate(): List<Iri> =
        when (this) {
            is SemanticEditRequest.AddAnnotation -> listOf(property)
            is SemanticEditRequest.RemoveAnnotation -> listOf(property)
            else -> emptyList()
        }

    private companion object {
        private val RDF_TYPE: Iri = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
        private val RDFS_LABEL: Iri = Iri("http://www.w3.org/2000/01/rdf-schema#label")
        private val RDFS_SUBCLASS_OF: Iri = Iri("http://www.w3.org/2000/01/rdf-schema#subClassOf")
        private val RDFS_DOMAIN: Iri = Iri("http://www.w3.org/2000/01/rdf-schema#domain")
        private val RDFS_RANGE: Iri = Iri("http://www.w3.org/2000/01/rdf-schema#range")
        private val SKOS_ALT_LABEL: Iri = Iri("http://www.w3.org/2004/02/skos/core#altLabel")
        private val SKOS_DEFINITION: Iri = Iri("http://www.w3.org/2004/02/skos/core#definition")
        private val OWL_CLASS: Iri = Iri("http://www.w3.org/2002/07/owl#Class")
        private val OWL_OBJECT_PROPERTY: Iri = Iri("http://www.w3.org/2002/07/owl#ObjectProperty")
        private val OWL_DATATYPE_PROPERTY: Iri = Iri("http://www.w3.org/2002/07/owl#DatatypeProperty")
        private val OWL_NAMED_INDIVIDUAL: Iri = Iri("http://www.w3.org/2002/07/owl#NamedIndividual")
        private val OWL_ANNOTATION_PROPERTY: Iri = Iri("http://www.w3.org/2002/07/owl#AnnotationProperty")
    }
}
