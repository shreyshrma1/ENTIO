package com.entio.validation

import com.entio.core.DeletionPlan
import com.entio.core.DeletionPlanStatus
import com.entio.core.ProposalBaseline
import com.entio.core.ValidationIssue
import com.entio.core.ValidationReport
import com.entio.core.ValidationSeverity
import com.entio.core.ValidationStatus

/** Validates deletion target/source, dependency, and baseline safety before proposal creation. */
public class DeletionValidator {
    public fun validate(
        plan: DeletionPlan?,
        requestedSourceId: String,
        currentSourceId: String,
        baseline: ProposalBaseline? = null,
        currentBaseline: ProposalBaseline? = null,
    ): ValidationReport {
        val issues = mutableListOf<ValidationIssue>()
        if (plan == null) {
            issues += issue("missing-deletion-target", "The deletion target does not exist.")
        } else {
            if (plan.status == DeletionPlanStatus.InvalidDependencySelection) {
                issues += issue(
                    code = "invalid-deletion-dependency-selection",
                    message = "One or more selected dependent statements do not belong to the deletion plan.",
                )
            } else if (plan.target.sourceId != requestedSourceId || requestedSourceId != currentSourceId ||
                plan.status == DeletionPlanStatus.Invalid
            ) {
                issues += issue(
                    code = "wrong-deletion-source",
                    message = "The deletion target does not belong to the requested current source.",
                )
            }
            if (plan.status == DeletionPlanStatus.RequiresExplicitDependencies ||
                plan.status == DeletionPlanStatus.Blocked
            ) {
                issues += issue(
                    code = "unresolved-deletion-dependencies",
                    message = "Dependent statements must be explicitly selected before deletion.",
                )
            }
        }

        if (baseline != null && currentBaseline != null && baseline != currentBaseline) {
            issues += issue(
                code = "stale-deletion-baseline",
                message = "The project changed after the deletion was prepared.",
            )
        }

        return ValidationReport(
            status = if (issues.isEmpty()) ValidationStatus.Valid else ValidationStatus.Invalid,
            issues = issues,
        )
    }

    private fun issue(code: String, message: String): ValidationIssue =
        ValidationIssue(
            severity = ValidationSeverity.Error,
            code = code,
            message = message,
        )
}
