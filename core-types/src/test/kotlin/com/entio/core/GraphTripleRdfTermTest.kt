package com.entio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GraphTripleRdfTermTest {
    @Test
    fun preventsLiteralSubjectsThroughRdfResourceType(): Unit {
        val literal: RdfTerm = RdfLiteral(lexicalForm = "Customer")

        assertFalse(literal is RdfResource)
    }

    @Test
    fun graphTriplesUseValueEqualityWithRdfTerms(): Unit {
        val first = GraphTriple(
            subject = Iri("https://example.com/Customer"),
            predicate = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
            objectTerm = Iri("http://www.w3.org/2002/07/owl#Class"),
        )
        val second = GraphTriple(
            subject = Iri("https://example.com/Customer"),
            predicate = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
            objectTerm = Iri("http://www.w3.org/2002/07/owl#Class"),
        )

        assertEquals(first, second)
    }

    @Test
    fun representsIriBlankNodeAndLiteralObjects(): Unit {
        val iriObject = GraphTriple(
            subject = Iri("https://example.com/Customer"),
            predicate = Iri("https://example.com/relatedTo"),
            objectTerm = Iri("https://example.com/Account"),
        )
        val blankNodeObject = GraphTriple(
            subject = Iri("https://example.com/Customer"),
            predicate = Iri("https://example.com/hasDetail"),
            objectTerm = BlankNodeResource(id = "b0"),
        )
        val literalObject = GraphTriple(
            subject = BlankNodeResource(id = "b1"),
            predicate = Iri("http://www.w3.org/2000/01/rdf-schema#label"),
            objectTerm = RdfLiteral(lexicalForm = "Customer"),
        )

        assertEquals(Iri("https://example.com/Account"), iriObject.objectTerm)
        assertEquals(BlankNodeResource(id = "b0"), blankNodeObject.objectTerm)
        assertEquals(RdfLiteral(lexicalForm = "Customer"), literalObject.objectTerm)
        assertEquals(BlankNodeResource(id = "b1"), literalObject.subjectResource)
    }

    @Test
    fun preservesLiteralDatatypeAndLanguageThroughGraphTriple(): Unit {
        val literal = RdfLiteral(
            lexicalForm = "Customer",
            datatypeIri = Iri("http://www.w3.org/2001/XMLSchema#string"),
            languageTag = "en",
        )
        val triple = GraphTriple(
            subject = Iri("https://example.com/Customer"),
            predicate = Iri("http://www.w3.org/2000/01/rdf-schema#label"),
            objectTerm = literal,
        )

        assertEquals(literal, triple.objectTerm)
        assertEquals("Customer", triple.objectValue)
    }

    @Test
    fun existingPhaseOneTripleConstructionStillWorks(): Unit {
        val triple = GraphTriple(
            subject = Iri("https://example.com/Customer"),
            predicate = Iri("http://www.w3.org/2000/01/rdf-schema#label"),
            objectValue = "Customer",
        )

        assertEquals(Iri("https://example.com/Customer"), triple.subject)
        assertEquals(Iri("https://example.com/Customer"), triple.subjectResource)
        assertEquals(RdfLiteral(lexicalForm = "Customer"), triple.objectTerm)
        assertEquals("Customer", triple.objectValue)
    }
}
