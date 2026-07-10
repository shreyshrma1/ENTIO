package com.entio.diff

import com.entio.core.BlankNodeResource
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.RdfTerm
import com.entio.core.SemanticDiff
import com.entio.core.SemanticDiffEntry
import com.entio.core.SemanticDiffKind

public class GraphDiffer {
    public fun diff(
        before: GraphState,
        after: GraphState,
    ): SemanticDiff {
        val removedTriples = before.triples - after.triples
        val addedTriples = after.triples - before.triples
        val labelChanges = labelChanges(
            removedTriples = removedTriples,
            addedTriples = addedTriples,
        )
        val changedBeforeTriples = labelChanges.map { it.before.triple }.toSet()
        val changedAfterTriples = labelChanges.map { it.after.triple }.toSet()

        val entries = buildList {
            addAll(labelChanges.map { it.toDiffEntry() })
            addAll((addedTriples - changedAfterTriples).map { it.toDiffEntry(SemanticDiffKind.Added) })
            addAll((removedTriples - changedBeforeTriples).map { it.toDiffEntry(SemanticDiffKind.Removed) })
        }.sortedWith(diffEntryComparator)

        return SemanticDiff(entries = entries)
    }

    private fun labelChanges(
        removedTriples: Set<GraphTriple>,
        addedTriples: Set<GraphTriple>,
    ): List<LabelChange> {
        val removedLabels = removedTriples
            .mapNotNull { it.toLabelTriple() }
            .associateBy { it.subject.value }
        val addedLabels = addedTriples
            .mapNotNull { it.toLabelTriple() }
            .associateBy { it.subject.value }

        return removedLabels.keys
            .intersect(addedLabels.keys)
            .map { subject ->
                LabelChange(
                    before = removedLabels.getValue(subject),
                    after = addedLabels.getValue(subject),
                )
            }
    }

    private fun GraphTriple.toLabelTriple(): LabelTriple? {
        if (predicate.value != RDFS_LABEL) {
            return null
        }

        val label = objectTerm as? RdfLiteral ?: return null

        return LabelTriple(
            subject = subjectResource,
            triple = this,
            label = label,
        )
    }

    private fun GraphTriple.toDiffEntry(kind: SemanticDiffKind): SemanticDiffEntry =
        SemanticDiffEntry(
            kind = kind,
            subject = subject,
            predicate = predicate,
            objectValue = objectValue,
            description = when (kind) {
                SemanticDiffKind.Added -> "Added triple ${formatTriple()}."
                SemanticDiffKind.Removed -> "Removed triple ${formatTriple()}."
                SemanticDiffKind.Changed -> "Changed triple ${formatTriple()}."
            },
        )

    private fun LabelChange.toDiffEntry(): SemanticDiffEntry =
        SemanticDiffEntry(
            kind = SemanticDiffKind.Changed,
            subject = after.triple.subject,
            predicate = after.triple.predicate,
            objectValue = "${before.label.lexicalForm} -> ${after.label.lexicalForm}",
            description = "Changed label for ${after.subject.formatResource()} from ${before.label.formatTerm()} to ${after.label.formatTerm()}.",
        )

    private fun GraphTriple.formatTriple(): String =
        "(${subjectResource.formatResource()}, ${predicate.value}, ${objectTerm.formatTerm()})"

    private fun RdfResource.formatResource(): String =
        when (this) {
            is Iri -> value
            is BlankNodeResource -> "blank node ($value)"
        }

    private fun RdfTerm.formatTerm(): String =
        when (this) {
            is RdfLiteral -> formatLiteral()
            is RdfResource -> formatResource()
        }

    private fun RdfLiteral.formatLiteral(): String {
        val quotedLexicalForm = "\"$lexicalForm\""
        val datatype = datatypeIri
        val language = languageTag

        return when {
            language != null -> "$quotedLexicalForm@$language"
            datatype != null -> "$quotedLexicalForm^^${datatype.value}"
            else -> quotedLexicalForm
        }
    }

    private data class LabelTriple(
        val subject: RdfResource,
        val triple: GraphTriple,
        val label: RdfLiteral,
    )

    private data class LabelChange(
        val before: LabelTriple,
        val after: LabelTriple,
    )

    private companion object {
        private const val RDFS_LABEL: String = "http://www.w3.org/2000/01/rdf-schema#label"

        private val diffEntryComparator: Comparator<SemanticDiffEntry> =
            compareBy<SemanticDiffEntry> { it.subject.value }
                .thenBy { it.predicate?.value.orEmpty() }
                .thenBy { it.objectValue.orEmpty() }
                .thenBy { it.description }
                .thenBy { it.kind.sortOrder }

        private val SemanticDiffKind.sortOrder: Int
            get() = when (this) {
                SemanticDiffKind.Changed -> 0
                SemanticDiffKind.Added -> 1
                SemanticDiffKind.Removed -> 2
            }
    }
}
