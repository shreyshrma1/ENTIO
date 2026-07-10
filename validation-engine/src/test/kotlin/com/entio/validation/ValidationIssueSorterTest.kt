package com.entio.validation

import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import kotlin.test.Test
import kotlin.test.assertEquals

class ValidationIssueSorterTest {
    private val sorter = ValidationIssueSorter()

    @Test
    fun sortsIssuesBySeverityCodeSourceAndMessage(): Unit {
        val issues = listOf(
            issue(ValidationSeverity.Info, "z-info", "b", "message"),
            issue(ValidationSeverity.Error, "b-error", "b", "message"),
            issue(ValidationSeverity.Warning, "a-warning", "a", "message"),
            issue(ValidationSeverity.Error, "a-error", "b", "message"),
            issue(ValidationSeverity.Error, "a-error", "a", "message"),
        )

        val sorted = sorter.sortIssues(issues)

        assertEquals(
            listOf(
                "Error:a-error:a:message",
                "Error:a-error:b:message",
                "Error:b-error:b:message",
                "Warning:a-warning:a:message",
                "Info:z-info:b:message",
            ),
            sorted.map { "${it.severity}:${it.code}:${it.source}:${it.message}" },
        )
    }

    private fun issue(
        severity: ValidationSeverity,
        code: String,
        source: String,
        message: String,
    ): ValidationIssue =
        ValidationIssue(
            severity = severity,
            code = code,
            message = message,
            source = source,
        )
}
