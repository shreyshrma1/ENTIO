package com.entio.diff

import com.entio.core.Iri
import com.entio.core.SemanticDiff
import com.entio.core.SemanticDiffEntry
import com.entio.core.SemanticDiffKind
import kotlin.test.Test
import kotlin.test.assertEquals

class SemanticDiffFormatterTest {
    private val formatter = SemanticDiffFormatter()

    @Test
    fun formatsEmptyDiff(): Unit {
        assertEquals("No semantic changes.", formatter.format(SemanticDiff(entries = emptyList())))
    }

    @Test
    fun formatsEntryDescriptions(): Unit {
        val diff = SemanticDiff(
            entries = listOf(
                SemanticDiffEntry(
                    kind = SemanticDiffKind.Added,
                    subject = Iri("https://example.com/Customer"),
                    predicate = Iri("https://example.com/name"),
                    objectValue = "Customer",
                    description = "Added customer name.",
                ),
                SemanticDiffEntry(
                    kind = SemanticDiffKind.Removed,
                    subject = Iri("https://example.com/Account"),
                    predicate = Iri("https://example.com/name"),
                    objectValue = "Account",
                    description = "Removed account name.",
                ),
            ),
        )

        assertEquals(
            "Added customer name.\nRemoved account name.",
            formatter.format(diff),
        )
    }
}
