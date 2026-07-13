package com.entio.semantic

import com.entio.core.EntioProject
import com.entio.core.EntioProjectConfig
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.LoadedOntology
import com.entio.core.LoadedSymbol
import com.entio.core.OntologyFormat
import com.entio.core.OntologySourceReference
import com.entio.core.RdfLiteral
import com.entio.core.ResolvedOntologySource
import com.entio.core.SymbolKind
import com.entio.core.SymbolRelationshipDirection
import com.entio.core.SymbolRelationshipKind
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SymbolRelationshipExtractorTest {
    @Test
    fun extractsTypesOutgoingPropertiesIncomingRelationshipsAndLiteralMetadata(): Unit {
        val customer = Iri("https://example.com/Customer")
        val shrey = Iri("https://example.com/Shrey")
        val invoice = Iri("https://example.com/Invoice")
        val invoice20874 = Iri("https://example.com/20874")
        val receivedInvoice = Iri("https://example.com/receivedInvoice")
        val nickname = Iri("https://example.com/nickname")
        val rdfType = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
        val rdfsLabel = Iri("http://www.w3.org/2000/01/rdf-schema#label")
        val owlNamedIndividual = Iri("http://www.w3.org/2002/07/owl#NamedIndividual")
        val owlObjectProperty = Iri("http://www.w3.org/2002/07/owl#ObjectProperty")
        val triples = setOf(
            triple(shrey, rdfType, customer),
            triple(shrey, rdfType, owlNamedIndividual),
            triple(shrey, rdfsLabel, RdfLiteral("Shrey")),
            triple(receivedInvoice, rdfType, owlObjectProperty),
            triple(receivedInvoice, rdfsLabel, RdfLiteral("received invoice")),
            triple(shrey, receivedInvoice, invoice20874),
            triple(nickname, rdfType, Iri("http://www.w3.org/2002/07/owl#DatatypeProperty")),
            triple(nickname, rdfsLabel, RdfLiteral("nickname")),
            triple(shrey, nickname, RdfLiteral("Shrey", languageTag = "en")),
            triple(invoice20874, rdfType, invoice),
            triple(invoice20874, rdfsLabel, RdfLiteral("Invoice 20874")),
        )
        val source = ResolvedOntologySource(
            id = "simple",
            path = Path.of("ontology/simple.ttl"),
            format = OntologyFormat.Turtle,
        )
        val project = EntioProject(
            config = EntioProjectConfig(
                name = "simple",
                ontologySources = listOf(
                    OntologySourceReference("simple", "ontology/simple.ttl", OntologyFormat.Turtle),
                ),
            ),
            resolvedSources = listOf(source),
            ontologies = listOf(LoadedOntology(source, GraphState(triples))),
            symbols = listOf(
                LoadedSymbol(customer, "Customer", SymbolKind.Class, "simple"),
                LoadedSymbol(shrey, "Shrey", SymbolKind.Individual, "simple"),
                LoadedSymbol(invoice, "Invoice", SymbolKind.Class, "simple"),
                LoadedSymbol(invoice20874, "Invoice 20874", SymbolKind.Individual, "simple"),
                LoadedSymbol(receivedInvoice, "received invoice", SymbolKind.Property, "simple"),
                LoadedSymbol(nickname, "nickname", SymbolKind.Property, "simple"),
            ),
            graph = GraphState(triples),
        )

        val details = SymbolRelationshipExtractor().extractDetails(project)
            .single { it.symbol.iri == shrey }

        val types = details.relationships.filter { it.kind == SymbolRelationshipKind.Type }
        assertEquals(
            setOf(customer, owlNamedIndividual),
            types.map { it.value }.toSet(),
        )

        val outgoingObject = details.relationships.single {
            it.kind == SymbolRelationshipKind.Property && it.predicate == receivedInvoice
        }
        assertEquals(SymbolRelationshipDirection.Outgoing, outgoingObject.direction)
        assertEquals(invoice20874, outgoingObject.value)
        assertEquals("Invoice 20874", outgoingObject.valueLabel)
        assertEquals("received invoice", outgoingObject.predicateLabel)

        val outgoingLiteral = details.relationships.single { it.predicate == nickname }
        val literal = assertNotNull(outgoingLiteral.value as? RdfLiteral)
        assertEquals("Shrey", literal.lexicalForm)
        assertEquals("en", literal.languageTag)

        val invoiceDetails = SymbolRelationshipExtractor().extractDetails(project)
            .single { it.symbol.iri == invoice20874 }
        val incoming = invoiceDetails.relationships.single { it.predicate == receivedInvoice }
        assertEquals(SymbolRelationshipDirection.Incoming, incoming.direction)
        assertEquals(shrey, incoming.value)
        assertEquals("Shrey", incoming.valueLabel)
    }

    private fun triple(subject: Iri, predicate: Iri, objectTerm: com.entio.core.RdfTerm): GraphTriple =
        GraphTriple(subject = subject, predicate = predicate, objectTerm = objectTerm)
}
