package com.entio.semantic

import com.entio.core.EntioResult
import com.entio.core.Iri
import com.entio.core.LoadedOntology
import com.entio.core.OntologyEntityDescriptor
import com.entio.core.RdfLiteral
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SemanticDescriptorAssemblerTest {
    private val parser = OntologyParser()
    private val assembler = SemanticDescriptorAssembler()

    @Test
    fun assemblesAllRequiredKindsAndExplicitStructuralFacts(): Unit {
        val ontology = parse(
            """
            @prefix ex: <https://example.com/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

            ex:Party a owl:Class .
            ex:Customer a owl:Class ; rdfs:subClassOf ex:Party ; rdfs:label "Customer" .
            ex:Invoice a owl:Class .
            ex:receivedInvoice a owl:ObjectProperty ;
                rdfs:domain ex:Customer ;
                rdfs:range ex:Invoice .
            ex:age a owl:DatatypeProperty ;
                rdfs:domain ex:Customer ;
                rdfs:range xsd:integer .
            ex:note a owl:AnnotationProperty .
            ex:Shrey a ex:Customer ;
                ex:receivedInvoice ex:Invoice20874 ;
                ex:age "42"^^xsd:integer ;
                ex:note "Important"@en .
            ex:Invoice20874 a ex:Invoice .
            """.trimIndent(),
        )

        val descriptors = assembler.assemble(ontology)

        assertEquals(
            listOf(
                "https://example.com/Customer",
                "https://example.com/Invoice",
                "https://example.com/Invoice20874",
                "https://example.com/Party",
                "https://example.com/Shrey",
                "https://example.com/age",
                "https://example.com/note",
                "https://example.com/receivedInvoice",
            ),
            descriptors.map { (it.common.entity as Iri).value },
        )
        assertEquals(
            setOf(
                OntologyEntityDescriptor.Class::class,
                OntologyEntityDescriptor.ObjectProperty::class,
                OntologyEntityDescriptor.DatatypeProperty::class,
                OntologyEntityDescriptor.AnnotationProperty::class,
                OntologyEntityDescriptor.Individual::class,
            ),
            descriptors.map { it::class }.toSet(),
        )

        val customer = assertIs<OntologyEntityDescriptor.Class>(descriptors.first())
        assertEquals(listOf(Iri("https://example.com/Party")), customer.directSuperclasses)
        assertEquals(listOf(Iri("https://example.com/Shrey")), customer.directlyTypedIndividuals)
        assertEquals("simple", customer.common.sourceId)
        assertEquals("simple", customer.common.sourceOntologyId)

        val receivedInvoice = assertIs<OntologyEntityDescriptor.ObjectProperty>(
            descriptors.single { it.common.entity == Iri("https://example.com/receivedInvoice") },
        )
        assertEquals(listOf(Iri("https://example.com/Customer")), receivedInvoice.domains)
        assertEquals(listOf(Iri("https://example.com/Invoice")), receivedInvoice.ranges)
        assertEquals(Iri("https://example.com/Invoice20874"), receivedInvoice.directAssertions.single().value)

        val age = assertIs<OntologyEntityDescriptor.DatatypeProperty>(
            descriptors.single { it.common.entity == Iri("https://example.com/age") },
        )
        assertEquals(listOf(Iri("http://www.w3.org/2001/XMLSchema#integer")), age.datatypeRanges)
        assertEquals(RdfLiteral("42", datatypeIri = Iri("http://www.w3.org/2001/XMLSchema#integer")), age.directAssertions.single().value)

        val note = assertIs<OntologyEntityDescriptor.AnnotationProperty>(
            descriptors.single { it.common.entity == Iri("https://example.com/note") },
        )
        assertEquals(1, note.statementsUsingProperty.size)
        assertEquals(Iri("https://example.com/Shrey"), note.statementsUsingProperty.single().subject)
    }

    @Test
    fun assemblesIndividualAssertionsOnlyFromExplicitPropertyTypes(): Unit {
        val ontology = parse(
            """
            @prefix ex: <https://example.com/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            ex:Customer a owl:Class .
            ex:owns a owl:ObjectProperty .
            ex:name a owl:DatatypeProperty .
            ex:Shrey a ex:Customer ;
                ex:owns ex:Account ;
                ex:name "Shrey" ;
                ex:untyped ex:Ignored .
            """.trimIndent(),
        )

        val individual = assertIs<OntologyEntityDescriptor.Individual>(
            assembler.assemble(ontology).single { it.common.entity == Iri("https://example.com/Shrey") },
        )

        assertEquals(listOf(Iri("https://example.com/owns")), individual.objectPropertyAssertions.map { it.property })
        assertEquals(listOf(Iri("https://example.com/name")), individual.datatypePropertyAssertions.map { it.property })
        assertTrue(individual.objectPropertyAssertions.none { it.property == Iri("https://example.com/untyped") })
    }

    @Test
    fun doesNotInferTransitiveHierarchyOrMissingDomainsAndRanges(): Unit {
        val ontology = parse(
            """
            @prefix ex: <https://example.com/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            ex:Grandparent a owl:Class .
            ex:Parent a owl:Class ; rdfs:subClassOf ex:Grandparent .
            ex:Child a owl:Class ; rdfs:subClassOf ex:Parent .
            ex:relation a owl:ObjectProperty .
            """.trimIndent(),
        )

        val descriptors = assembler.assemble(ontology)
        val child = assertIs<OntologyEntityDescriptor.Class>(descriptors.single { it.common.entity == Iri("https://example.com/Child") })
        val relation = assertIs<OntologyEntityDescriptor.ObjectProperty>(descriptors.single { it.common.entity == Iri("https://example.com/relation") })

        assertEquals(listOf(Iri("https://example.com/Parent")), child.directSuperclasses)
        assertEquals(emptyList(), child.directSubclasses)
        assertEquals(emptyList(), relation.domains)
        assertEquals(emptyList(), relation.ranges)
    }

    private fun parse(content: String): LoadedOntology {
        val result = parser.parse(SemanticEngineTestFixtures.resolvedSource(content))
        return assertIs<EntioResult.Success<LoadedOntology>>(result).value
    }
}
