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
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.RemoveSuperclassEdit
import com.entio.core.SetEntityLabelEdit
import com.entio.core.SetPropertyDomainEdit
import com.entio.core.SetPropertyRangeEdit
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
            is CreateIndividualEdit -> typeAssertion(individualIri, OWL_NAMED_INDIVIDUAL) +
                classIri?.let { listOf(addTriple(individualIri, RDF_TYPE, it)) }.orEmpty()
            is AssignTypeEdit -> listOf(addTriple(resource, RDF_TYPE, typeIri))
            is AddObjectPropertyAssertionEdit -> listOf(addTriple(subject, propertyIri, objectResource))
            is AddDatatypePropertyAssertionEdit -> listOf(addTriple(subject, propertyIri, value))
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
            is CreateIndividualEdit -> listOfNotNull("individualIri" to individualIri, classIri?.let { "classIri" to it })
            is AssignTypeEdit -> listOf("resource" to resource, "typeIri" to typeIri)
            is AddObjectPropertyAssertionEdit -> listOf(
                "subject" to subject,
                "propertyIri" to propertyIri,
                "objectResource" to objectResource,
            )
            is AddDatatypePropertyAssertionEdit -> listOf("subject" to subject, "propertyIri" to propertyIri)
        }

    private companion object {
        private val RDF_TYPE: Iri = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
        private val RDFS_LABEL: Iri = Iri("http://www.w3.org/2000/01/rdf-schema#label")
        private val RDFS_SUBCLASS_OF: Iri = Iri("http://www.w3.org/2000/01/rdf-schema#subClassOf")
        private val RDFS_DOMAIN: Iri = Iri("http://www.w3.org/2000/01/rdf-schema#domain")
        private val RDFS_RANGE: Iri = Iri("http://www.w3.org/2000/01/rdf-schema#range")
        private val OWL_CLASS: Iri = Iri("http://www.w3.org/2002/07/owl#Class")
        private val OWL_OBJECT_PROPERTY: Iri = Iri("http://www.w3.org/2002/07/owl#ObjectProperty")
        private val OWL_DATATYPE_PROPERTY: Iri = Iri("http://www.w3.org/2002/07/owl#DatatypeProperty")
        private val OWL_NAMED_INDIVIDUAL: Iri = Iri("http://www.w3.org/2002/07/owl#NamedIndividual")
    }
}
