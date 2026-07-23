package com.entio.web.ai

import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiReasoningContextBuilderTest {
    @Test
    fun reportsAppliedInferencesAndPrivateDraftDeltaWithoutMaterializingThem(): Unit {
        val applied = GraphState(
            setOf(
                triple(ACCOUNT, RDF_TYPE, OWL_CLASS),
                triple(CHECKING, RDF_TYPE, OWL_CLASS),
                triple(CHECKING, RDFS_SUBCLASS_OF, ACCOUNT),
                triple(OWNS_ACCOUNT, RDF_TYPE, OWL_OBJECT_PROPERTY),
                triple(OWNS_ACCOUNT, RDFS_RANGE, CHECKING),
                triple(ACCOUNT_101, RDF_TYPE, ACCOUNT),
                triple(SHREY, OWNS_ACCOUNT, ACCOUNT_101),
                label(ACCOUNT_101, "Account 101"),
                label(CHECKING, "Checking Account"),
            ),
        )
        val draft = GraphState(
            applied.triples + setOf(
                triple(CHECKING_33271, RDF_TYPE, CHECKING),
                triple(SHREY, OWNS_ACCOUNT, CHECKING_33271),
                label(CHECKING_33271, "Checking Account 33271"),
            ),
        )

        val context = AiReasoningContextBuilder().build(applied, draft, "Create Checking Account 33271 and link Shrey with owns account.")

        assertTrue(context.hasDraftDelta)
        assertTrue(context.text.contains("Account 101"))
        assertTrue(context.text.contains("inferred individual type"))
        assertTrue(context.text.contains("Checking Account 33271"))
        assertTrue(context.text.contains("New inferred consequences introduced by the private draft"))
        assertTrue(context.text.contains("Inferred facts below are not asserted source triples"))
        assertFalse(applied.triples.any { it.subjectResource == ACCOUNT_101 && it.objectTerm == CHECKING })
        assertFalse(draft.triples.any { it.subjectResource == CHECKING_33271 && it.objectTerm == ACCOUNT })
    }

    private fun triple(subject: Iri, predicate: Iri, objectValue: Iri): GraphTriple =
        GraphTriple(subject, predicate, objectValue)

    private fun label(subject: Iri, value: String): GraphTriple =
        GraphTriple(subject, RDFS_LABEL, RdfLiteral(value))

    private companion object {
        private val NS = "https://example.com/"
        private val ACCOUNT = Iri("${NS}Account")
        private val CHECKING = Iri("${NS}Checking")
        private val ACCOUNT_101 = Iri("${NS}Account101")
        private val CHECKING_33271 = Iri("${NS}CheckingAccount33271")
        private val SHREY = Iri("${NS}Shrey")
        private val OWNS_ACCOUNT = Iri("${NS}ownsAccount")
        private val RDF_TYPE = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
        private val RDFS_LABEL = Iri("http://www.w3.org/2000/01/rdf-schema#label")
        private val RDFS_RANGE = Iri("http://www.w3.org/2000/01/rdf-schema#range")
        private val RDFS_SUBCLASS_OF = Iri("http://www.w3.org/2000/01/rdf-schema#subClassOf")
        private val OWL_CLASS = Iri("http://www.w3.org/2002/07/owl#Class")
        private val OWL_OBJECT_PROPERTY = Iri("http://www.w3.org/2002/07/owl#ObjectProperty")
    }
}
