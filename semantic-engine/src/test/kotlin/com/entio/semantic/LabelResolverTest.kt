package com.entio.semantic

import com.entio.core.EntityResolutionResult
import com.entio.core.EntitySelector
import com.entio.core.Iri
import com.entio.core.LoadedSymbol
import com.entio.core.SymbolKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LabelResolverTest {
    private val resolver = LabelResolver()
    private val symbols = listOf(
        LoadedSymbol(Iri("https://example.com/Customer"), "Customer", SymbolKind.Class, "simple"),
        LoadedSymbol(Iri("https://example.com/CustomerRecord"), "Customer", SymbolKind.Individual, "simple"),
        LoadedSymbol(Iri("https://other.example/Customer"), "Customer", SymbolKind.Class, "other"),
        LoadedSymbol(Iri("https://example.com/name"), "Name", SymbolKind.Property, "simple"),
        LoadedSymbol(Iri("https://example.com/shapes/CustomerShape"), null, SymbolKind.Shape, "shapes"),
    )

    @Test
    fun resolvesUniqueExactLabel(): Unit {
        val result = resolver.resolve(
            symbols = symbols,
            selector = EntitySelector(label = "Name", kind = SymbolKind.Property),
        )

        val resolved = assertIs<EntityResolutionResult.Resolved>(result)
        assertEquals("https://example.com/name", resolved.candidate.iri.value)
    }

    @Test
    fun returnsMissingForUnknownLabel(): Unit {
        assertEquals(
            EntityResolutionResult.NotFound,
            resolver.resolve(symbols, EntitySelector(label = "Missing")),
        )
    }

    @Test
    fun returnsDeterministicAmbiguityCandidates(): Unit {
        val result = resolver.resolve(symbols, EntitySelector(label = "Customer"))

        val ambiguous = assertIs<EntityResolutionResult.Ambiguous>(result)
        assertEquals(
            listOf(
                "https://other.example/Customer",
                "https://example.com/Customer",
                "https://example.com/CustomerRecord",
            ),
            ambiguous.candidates.map { it.iri.value },
        )
    }

    @Test
    fun appliesKindAndSourceFilters(): Unit {
        val result = resolver.resolve(
            symbols,
            EntitySelector(label = "Customer", kind = SymbolKind.Class, sourceId = "simple"),
        )

        val resolved = assertIs<EntityResolutionResult.Resolved>(result)
        assertEquals("https://example.com/Customer", resolved.candidate.iri.value)
    }

    @Test
    fun resolvesExplicitIriWithoutFuzzyMatching(): Unit {
        val result = resolver.resolve(
            symbols,
            EntitySelector(iri = Iri("https://example.com/name")),
        )

        val resolved = assertIs<EntityResolutionResult.Resolved>(result)
        assertEquals("Name", resolved.candidate.label)
    }

    @Test
    fun resolvesAnUnlabeledShapeByItsLocalName(): Unit {
        val result = resolver.resolve(
            symbols,
            EntitySelector(label = "CustomerShape", kind = SymbolKind.Shape, sourceId = "shapes"),
        )

        val resolved = assertIs<EntityResolutionResult.Resolved>(result)
        assertEquals("https://example.com/shapes/CustomerShape", resolved.candidate.iri.value)
        assertEquals(null, resolved.candidate.label)
    }
}
