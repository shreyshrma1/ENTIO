package com.entio.semantic

import com.entio.core.EntioResult
import com.entio.core.GeneratedIri
import com.entio.core.Iri
import com.entio.core.IriCollisionOutcome
import com.entio.core.IriNamespaceConfig
import com.entio.core.LoadedSymbol
import com.entio.core.SymbolKind
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity

/** Generates stable local IRIs from labels without randomness or source mutation. */
public class DeterministicIriGenerator {
    public fun generate(
        label: String,
        kind: SymbolKind,
        namespace: IriNamespaceConfig?,
        existingSymbols: Iterable<LoadedSymbol>,
        distinctEntity: Boolean = false,
    ): EntioResult<GeneratedIri> {
        if (namespace == null) {
            return failure(
                code = "missing-iri-namespace",
                message = "An iriNamespace is required to generate an identifier.",
            )
        }

        val localName = normalizeLocalName(label, kind)
            ?: return failure(
                code = "invalid-local-name",
                message = "The label cannot produce a valid local name.",
            )

        val existing = existingSymbols.toList()
        val baseIri = Iri(namespace.namespace.value + localName)
        val matchingExisting = existing.firstOrNull { it.iri == baseIri }

        if (matchingExisting != null && !distinctEntity) {
            return failure(
                code = "iri-collision",
                message = "Generated IRI '${baseIri.value}' already exists.",
            )
        }

        if (matchingExisting == null) {
            return EntioResult.Success(
                GeneratedIri(
                    iri = baseIri,
                    localName = localName,
                    collision = IriCollisionOutcome.New,
                    normalizationVersion = namespace.normalizationVersion,
                ),
            )
        }

        var suffix = 2
        var candidateLocalName = "$localName\u005f\u005f$suffix"
        var candidateIri = Iri(namespace.namespace.value + candidateLocalName)
        while (existing.any { it.iri == candidateIri }) {
            suffix += 1
            candidateLocalName = "$localName\u005f\u005f$suffix"
            candidateIri = Iri(namespace.namespace.value + candidateLocalName)
        }

        return EntioResult.Success(
            GeneratedIri(
                iri = candidateIri,
                localName = candidateLocalName,
                collision = IriCollisionOutcome.SuffixRequired,
                normalizationVersion = namespace.normalizationVersion,
            ),
        )
    }

    internal fun normalizeLocalName(label: String, kind: SymbolKind): String? {
        val words = label.trim()
            .split(UNSUPPORTED_CHARACTERS)
            .filter { it.isNotEmpty() }

        if (words.isEmpty()) {
            return null
        }

        val joined = words.joinToString(separator = "") { word ->
            word.replaceFirstChar { character -> character.uppercase() }
        }
        val property = kind == SymbolKind.Property
        val cased = if (property) {
            joined.replaceFirstChar { character -> character.lowercase() }
        } else {
            joined
        }

        return if (cased.first().isDigit()) {
            if (property) "item$cased" else "Item$cased"
        } else {
            cased
        }
    }

    private fun failure(code: String, message: String): EntioResult.Failure =
        EntioResult.Failure(
            message = message,
            issues = listOf(
                ValidationIssue(
                    severity = ValidationSeverity.Error,
                    code = code,
                    message = message,
                ),
            ),
        )

    private companion object {
        private val UNSUPPORTED_CHARACTERS = Regex("[^A-Za-z0-9]+")
    }
}
