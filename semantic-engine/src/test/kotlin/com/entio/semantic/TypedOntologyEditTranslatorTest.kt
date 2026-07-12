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
import com.entio.core.GraphChangeKind
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.core.RemoveSuperclassEdit
import com.entio.core.SetEntityLabelEdit
import com.entio.core.SetPropertyDomainEdit
import com.entio.core.SetPropertyRangeEdit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TypedOntologyEditTranslatorTest {
    private val translator = TypedOntologyEditTranslator()

    @Test
    fun translatesCreateClassWithOptionalLabel(): Unit {
        val changeSet = translate(
            CreateClassEdit(
                classIri = CUSTOMER,
                label = RdfLiteral("Customer", languageTag = "en"),
            ),
        )

        assertEquals(
            listOf(
                added(CUSTOMER, RDF_TYPE, OWL_CLASS),
                added(CUSTOMER, RDFS_LABEL, RdfLiteral("Customer", languageTag = "en")),
            ),
            changeSet.changes,
        )
    }

    @Test
    fun translatesSetEntityLabel(): Unit {
        val label = RdfLiteral(
            lexicalForm = "Customer",
            datatypeIri = XSD_STRING,
        )

        val changeSet = translate(SetEntityLabelEdit(entity = CUSTOMER, label = label))

        assertEquals(listOf(added(CUSTOMER, RDFS_LABEL, label)), changeSet.changes)
    }

    @Test
    fun translatesSuperclassEdits(): Unit {
        val add = translate(AddSuperclassEdit(classIri = CUSTOMER, superclassIri = ACCOUNT))
        val remove = translate(RemoveSuperclassEdit(classIri = CUSTOMER, superclassIri = ACCOUNT))

        assertEquals(listOf(added(CUSTOMER, RDFS_SUBCLASS_OF, ACCOUNT)), add.changes)
        assertEquals(listOf(removed(CUSTOMER, RDFS_SUBCLASS_OF, ACCOUNT)), remove.changes)
    }

    @Test
    fun translatesPropertyCreationDomainAndRange(): Unit {
        val objectProperty = Iri("https://example.com/ownsAccount")
        val datatypeProperty = Iri("https://example.com/customerName")

        assertEquals(
            listOf(
                added(objectProperty, RDF_TYPE, OWL_OBJECT_PROPERTY),
                added(objectProperty, RDFS_LABEL, RdfLiteral("owns account")),
            ),
            translate(CreateObjectPropertyEdit(propertyIri = objectProperty, label = RdfLiteral("owns account"))).changes,
        )
        assertEquals(
            listOf(added(datatypeProperty, RDF_TYPE, OWL_DATATYPE_PROPERTY)),
            translate(CreateDatatypePropertyEdit(propertyIri = datatypeProperty)).changes,
        )
        assertEquals(
            listOf(added(objectProperty, RDFS_DOMAIN, CUSTOMER)),
            translate(SetPropertyDomainEdit(propertyIri = objectProperty, domainClassIri = CUSTOMER)).changes,
        )
        assertEquals(
            listOf(added(datatypeProperty, RDFS_RANGE, XSD_STRING)),
            translate(SetPropertyRangeEdit(propertyIri = datatypeProperty, rangeIri = XSD_STRING)).changes,
        )
    }

    @Test
    fun translatesIndividualAndTypeEdits(): Unit {
        val individual = Iri("https://example.com/customer-1")

        assertEquals(
            listOf(
                added(individual, RDF_TYPE, OWL_NAMED_INDIVIDUAL),
                added(individual, RDF_TYPE, CUSTOMER),
            ),
            translate(CreateIndividualEdit(individualIri = individual, classIri = CUSTOMER)).changes,
        )
        assertEquals(
            listOf(added(individual, RDF_TYPE, CUSTOMER)),
            translate(AssignTypeEdit(resource = individual, typeIri = CUSTOMER)).changes,
        )
    }

    @Test
    fun translatesObjectAndDatatypePropertyAssertions(): Unit {
        val ownsAccount = Iri("https://example.com/ownsAccount")
        val customerName = Iri("https://example.com/customerName")
        val literal = RdfLiteral(
            lexicalForm = "Customer",
            datatypeIri = XSD_STRING,
            languageTag = "en",
        )

        assertEquals(
            listOf(added(CUSTOMER, ownsAccount, ACCOUNT)),
            translate(
                AddObjectPropertyAssertionEdit(
                    subject = CUSTOMER,
                    propertyIri = ownsAccount,
                    objectResource = ACCOUNT,
                ),
            ).changes,
        )
        assertEquals(
            listOf(added(CUSTOMER, customerName, literal)),
            translate(
                AddDatatypePropertyAssertionEdit(
                    subject = CUSTOMER,
                    propertyIri = customerName,
                    value = literal,
                ),
            ).changes,
        )
    }

    @Test
    fun returnsStructuredFailureForBlankIriValues(): Unit {
        val failure = assertIs<EntioResult.Failure>(
            translator.translate(CreateClassEdit(classIri = Iri("   "))),
        )

        assertEquals("Typed ontology edit is invalid.", failure.message)
        assertEquals("invalid-iri", failure.issues.single().code)
        assertEquals("classIri", failure.issues.single().source)
    }

    private fun translate(edit: com.entio.core.TypedOntologyEdit): ChangeSet =
        assertIs<EntioResult.Success<ChangeSet>>(translator.translate(edit)).value

    private fun added(
        subject: com.entio.core.RdfResource,
        predicate: Iri,
        objectTerm: com.entio.core.RdfTerm,
    ): com.entio.core.GraphChange =
        com.entio.core.GraphChange(
            kind = GraphChangeKind.Addition,
            triple = GraphTriple(subject = subject, predicate = predicate, objectTerm = objectTerm),
        )

    private fun removed(
        subject: com.entio.core.RdfResource,
        predicate: Iri,
        objectTerm: com.entio.core.RdfTerm,
    ): com.entio.core.GraphChange =
        com.entio.core.GraphChange(
            kind = GraphChangeKind.Removal,
            triple = GraphTriple(subject = subject, predicate = predicate, objectTerm = objectTerm),
        )

    private companion object {
        private val CUSTOMER: Iri = Iri("https://example.com/Customer")
        private val ACCOUNT: Iri = Iri("https://example.com/Account")
        private val RDF_TYPE: Iri = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
        private val RDFS_LABEL: Iri = Iri("http://www.w3.org/2000/01/rdf-schema#label")
        private val RDFS_SUBCLASS_OF: Iri = Iri("http://www.w3.org/2000/01/rdf-schema#subClassOf")
        private val RDFS_DOMAIN: Iri = Iri("http://www.w3.org/2000/01/rdf-schema#domain")
        private val RDFS_RANGE: Iri = Iri("http://www.w3.org/2000/01/rdf-schema#range")
        private val OWL_CLASS: Iri = Iri("http://www.w3.org/2002/07/owl#Class")
        private val OWL_OBJECT_PROPERTY: Iri = Iri("http://www.w3.org/2002/07/owl#ObjectProperty")
        private val OWL_DATATYPE_PROPERTY: Iri = Iri("http://www.w3.org/2002/07/owl#DatatypeProperty")
        private val OWL_NAMED_INDIVIDUAL: Iri = Iri("http://www.w3.org/2002/07/owl#NamedIndividual")
        private val XSD_STRING: Iri = Iri("http://www.w3.org/2001/XMLSchema#string")
    }
}
