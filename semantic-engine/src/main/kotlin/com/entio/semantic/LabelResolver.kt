package com.entio.semantic

import com.entio.core.EntityCandidate
import com.entio.core.EntityResolutionResult
import com.entio.core.EntitySelector
import com.entio.core.LoadedSymbol

/** Resolves exact labels or explicit IRIs against the currently loaded symbols. */
public class LabelResolver {
    public fun resolve(
        symbols: Iterable<LoadedSymbol>,
        selector: EntitySelector,
    ): EntityResolutionResult {
        if (selector.label == null && selector.iri == null) {
            return EntityResolutionResult.Invalid("A label or IRI is required.")
        }

        val selectorLabel = selector.label
        val candidates = symbols
            .filter { symbol -> selector.iri == null || symbol.iri == selector.iri }
            .filter { symbol -> selectorLabel == null || symbol.matchesLabel(selectorLabel) }
            .filter { symbol -> selector.kind == null || symbol.kind == selector.kind }
            .filter { symbol -> selector.sourceId == null || symbol.sourceId == selector.sourceId }
            .map { symbol ->
                EntityCandidate(
                    iri = symbol.iri,
                    label = symbol.label,
                    kind = symbol.kind,
                    sourceId = symbol.sourceId,
                )
            }
            .sortedWith(compareBy<EntityCandidate>({ it.label ?: "" }, { it.sourceId }, { it.kind.ordinal }, { it.iri.value }))

        return when (candidates.size) {
            0 -> EntityResolutionResult.NotFound
            1 -> EntityResolutionResult.Resolved(candidates.single())
            else -> EntityResolutionResult.Ambiguous(candidates)
        }
    }

    private fun LoadedSymbol.matchesLabel(label: String): Boolean {
        val symbolLabel = this.label
        return symbolLabel == label || (symbolLabel == null && iri.localName() == label)
    }

    private fun com.entio.core.Iri.localName(): String {
        val localName = this.value.substringAfterLast('#', this.value.substringAfterLast('/'))
        return localName.takeIf { it.isNotBlank() }.orEmpty()
    }
}
