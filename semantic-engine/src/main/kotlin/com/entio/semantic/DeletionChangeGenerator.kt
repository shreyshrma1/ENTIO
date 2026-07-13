package com.entio.semantic

import com.entio.core.ChangeSet
import com.entio.core.EntioResult
import com.entio.core.GraphChange
import com.entio.core.GraphChangeKind
import com.entio.core.DeletionPlan
import com.entio.core.DeletionPlanStatus
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity

/** Converts an explicitly approved deletion plan into removal changes without writing sources. */
public class DeletionChangeGenerator {
    public fun generate(plan: DeletionPlan): EntioResult<ChangeSet> {
        if (plan.status != DeletionPlanStatus.Safe) {
            return failure(
                code = "unresolved-deletion-dependencies",
                message = "Deletion dependencies must be explicitly selected before change generation.",
            )
        }

        val changes = (plan.directStatements + plan.dependentStatements.filter { it.selectedForRemoval })
            .distinctBy { it.statement }
            .map { dependency ->
                GraphChange(
                    kind = GraphChangeKind.Removal,
                    triple = dependency.statement,
                )
            }

        if (changes.isEmpty()) {
            return failure(
                code = "missing-deletion-statements",
                message = "The deletion target has no explicit graph statements to remove.",
            )
        }

        return EntioResult.Success(ChangeSet(changes))
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
}
