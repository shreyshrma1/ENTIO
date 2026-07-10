package com.entio.diff

import com.entio.core.GraphState
import com.entio.core.GraphTriple
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
        val changedBeforeTriples = labelChanges.map { it.before }.toSet()
        val changedAfterTriples = labelChanges.map { it.after }.toSet()

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
            .filter { it.predicate.value == RDFS_LABEL }
            .associateBy { it.subject.value }
        val addedLabels = addedTriples
            .filter { it.predicate.value == RDFS_LABEL }
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
            subject = after.subject,
            predicate = after.predicate,
            objectValue = "${before.objectValue} -> ${after.objectValue}",
            description = "Changed label for ${after.subject.value} from '${before.objectValue}' to '${after.objectValue}'.",
        )

    private fun GraphTriple.formatTriple(): String =
        "(${subject.value}, ${predicate.value}, $objectValue)"

    private data class LabelChange(
        val before: GraphTriple,
        val after: GraphTriple,
    )

    private companion object {
        private const val RDFS_LABEL: String = "http://www.w3.org/2000/01/rdf-schema#label"

        private val diffEntryComparator: Comparator<SemanticDiffEntry> =
            compareBy<SemanticDiffEntry> { it.subject.value }
                .thenBy { it.predicate?.value.orEmpty() }
                .thenBy { it.objectValue.orEmpty() }
                .thenBy { it.kind.sortOrder }

        private val SemanticDiffKind.sortOrder: Int
            get() = when (this) {
                SemanticDiffKind.Changed -> 0
                SemanticDiffKind.Added -> 1
                SemanticDiffKind.Removed -> 2
            }
    }
}
