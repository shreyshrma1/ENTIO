package com.entio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class RdfTermTest {
    @Test
    fun constructsEachRdfTermKind(): Unit {
        val iri = Iri("https://example.com/Customer")
        val blankNode = BlankNodeResource(id = "b0")
        val literal = RdfLiteral(lexicalForm = "Customer")

        assertIs<RdfResource>(iri)
        assertEquals("https://example.com/Customer", iri.value)
        assertEquals("_:b0", blankNode.value)
        assertEquals("Customer", literal.lexicalForm)
    }

    @Test
    fun literalsAreNotRdfResources(): Unit {
        val literal: RdfTerm = RdfLiteral(lexicalForm = "Customer")

        assertFalse(literal is RdfResource)
    }

    @Test
    fun literalDatatypeAndLanguageFieldsArePreserved(): Unit {
        val literal = RdfLiteral(
            lexicalForm = "Customer",
            datatypeIri = Iri("http://www.w3.org/2001/XMLSchema#string"),
            languageTag = "en",
        )

        assertEquals("Customer", literal.lexicalForm)
        assertEquals("http://www.w3.org/2001/XMLSchema#string", literal.datatypeIri?.value)
        assertEquals("en", literal.languageTag)
    }

    @Test
    fun existingGraphTripleConstructionStillWorks(): Unit {
        val triple = GraphTriple(
            subject = Iri("https://example.com/Customer"),
            predicate = Iri("http://www.w3.org/2000/01/rdf-schema#label"),
            objectValue = "Customer",
        )

        assertEquals("https://example.com/Customer", triple.subject.value)
        assertEquals("http://www.w3.org/2000/01/rdf-schema#label", triple.predicate.value)
        assertEquals("Customer", triple.objectValue)
    }
}
