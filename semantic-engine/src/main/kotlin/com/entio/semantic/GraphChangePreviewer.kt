package com.entio.semantic

import com.entio.core.ChangePreview
import com.entio.core.ChangeSet
import com.entio.core.EntioResult
import com.entio.core.GraphChange
import com.entio.core.GraphChangeKind
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity

public class GraphChangePreviewer {
    public fun preview(
        currentGraph: GraphState,
        changeSet: ChangeSet,
    ): EntioResult<ChangePreview> {
        val previewTriples = currentGraph.triples.toMutableSet()
        val issues = mutableListOf<ValidationIssue>()

        changeSet.changes.forEachIndexed { index, change ->
            when (change.kind) {
                GraphChangeKind.Addition -> applyAddition(previewTriples, change, index, issues)
                GraphChangeKind.Removal -> applyRemoval(previewTriples, change, index, issues)
            }
        }

        if (issues.isNotEmpty()) {
            return EntioResult.Failure(
                message = "Graph change preview failed.",
                issues = issues,
            )
        }

        return EntioResult.Success(
            ChangePreview(
                graph = GraphState(triples = previewTriples.toSet()),
                changeSet = changeSet,
            ),
        )
    }

    private fun applyAddition(
        previewTriples: MutableSet<GraphTriple>,
        change: GraphChange,
        index: Int,
        issues: MutableList<ValidationIssue>,
    ): Unit {
        if (!previewTriples.add(change.triple)) {
            issues += ValidationIssue(
                severity = ValidationSeverity.Error,
                code = "duplicate-triple-addition",
                message = "Cannot add a triple that already exists in the preview graph.",
                source = "changeSet.changes[$index]",
            )
        }
    }

    private fun applyRemoval(
        previewTriples: MutableSet<GraphTriple>,
        change: GraphChange,
        index: Int,
        issues: MutableList<ValidationIssue>,
    ): Unit {
        if (!previewTriples.remove(change.triple)) {
            issues += ValidationIssue(
                severity = ValidationSeverity.Error,
                code = "missing-triple-removal",
                message = "Cannot remove a triple that does not exist in the preview graph.",
                source = "changeSet.changes[$index]",
            )
        }
    }
}
