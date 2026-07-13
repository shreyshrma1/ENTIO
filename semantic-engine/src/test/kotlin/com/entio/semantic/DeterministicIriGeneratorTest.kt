package com.entio.semantic

import com.entio.core.EntioResult
import com.entio.core.Iri
import com.entio.core.IriNamespaceConfig
import com.entio.core.LoadedSymbol
import com.entio.core.SymbolKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DeterministicIriGeneratorTest {
    private val generator = DeterministicIriGenerator()
    private val namespace = IriNamespaceConfig(Iri("https://example.com/entio/simple#"))

    @Test
    fun normalizesClassesAndIndividualsToUpperCamel(): Unit {
        val classResult = generator.generate("Commercial Loan", SymbolKind.Class, namespace, emptyList())
        val individualResult = generator.generate("123 invoice", SymbolKind.Individual, namespace, emptyList())

        assertEquals("https://example.com/entio/simple#CommercialLoan", generated(classResult).iri.value)
        assertEquals("https://example.com/entio/simple#Item123Invoice", generated(individualResult).iri.value)
    }

    @Test
    fun normalizesPropertiesToLowerCamel(): Unit {
        val result = generator.generate("has borrower", SymbolKind.Property, namespace, emptyList())

        assertEquals("https://example.com/entio/simple#hasBorrower", generated(result).iri.value)
    }

    @Test
    fun rejectsEmptyLabelsAndMissingNamespace(): Unit {
        val empty = generator.generate("---", SymbolKind.Class, namespace, emptyList())
        val missingNamespace = generator.generate("Customer", SymbolKind.Class, null, emptyList())

        assertEquals("invalid-local-name", failure(empty).issues.single().code)
        assertEquals("missing-iri-namespace", failure(missingNamespace).issues.single().code)
    }

    @Test
    fun rejectsCollisionUnlessDistinctEntityIsRequested(): Unit {
        val existing = listOf(
            LoadedSymbol(
                iri = Iri("https://example.com/entio/simple#Customer"),
                label = "Customer",
                kind = SymbolKind.Class,
                sourceId = "simple",
            ),
            LoadedSymbol(
                iri = Iri("https://example.com/entio/simple#Customer__2"),
                label = "Another",
                kind = SymbolKind.Class,
                sourceId = "simple",
            ),
        )

        val rejected = generator.generate("Customer", SymbolKind.Class, namespace, existing)
        val suffixed = generator.generate("Customer", SymbolKind.Class, namespace, existing, distinctEntity = true)

        assertEquals("iri-collision", failure(rejected).issues.single().code)
        assertEquals("https://example.com/entio/simple#Customer__3", generated(suffixed).iri.value)
    }

    @Test
    fun generationIsRepeatable(): Unit {
        val first = generated(generator.generate("Received invoice", SymbolKind.Property, namespace, emptyList()))
        val second = generated(generator.generate("Received invoice", SymbolKind.Property, namespace, emptyList()))

        assertEquals(first, second)
    }

    private fun generated(result: EntioResult<com.entio.core.GeneratedIri>): com.entio.core.GeneratedIri =
        assertIs<EntioResult.Success<com.entio.core.GeneratedIri>>(result).value

    private fun failure(result: EntioResult<com.entio.core.GeneratedIri>): EntioResult.Failure =
        assertIs<EntioResult.Failure>(result)
}
