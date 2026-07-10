package com.entio.validation

import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity

public class ValidationIssueSorter {
    public fun sortIssues(issues: List<ValidationIssue>): List<ValidationIssue> =
        issues.sortedWith(
            compareBy<ValidationIssue> { it.severity.sortOrder }
                .thenBy { it.code }
                .thenBy { it.source.orEmpty() }
                .thenBy { it.message },
        )

    private val ValidationSeverity.sortOrder: Int
        get() = when (this) {
            ValidationSeverity.Error -> 0
            ValidationSeverity.Warning -> 1
            ValidationSeverity.Info -> 2
        }
}
