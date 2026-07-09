package com.entio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidationReportTest {
    @Test
    fun reportsStatusAsBooleanOkValue(): Unit {
        val validReport = ValidationReport(
            status = ValidationStatus.Valid,
            issues = emptyList(),
        )
        val invalidReport = ValidationReport(
            status = ValidationStatus.Invalid,
            issues = listOf(
                ValidationIssue(
                    severity = ValidationSeverity.Error,
                    code = "invalid-config",
                    message = "Project configuration is invalid.",
                ),
            ),
        )

        assertTrue(validReport.ok)
        assertFalse(invalidReport.ok)
        assertEquals("invalid-config", invalidReport.issues.single().code)
    }

    @Test
    fun dataObjectsUseValueEquality(): Unit {
        val first = ValidationIssue(
            severity = ValidationSeverity.Warning,
            code = "missing-label",
            message = "Symbol does not have a label.",
            source = "https://example.com/Symbol",
        )
        val second = ValidationIssue(
            severity = ValidationSeverity.Warning,
            code = "missing-label",
            message = "Symbol does not have a label.",
            source = "https://example.com/Symbol",
        )

        assertEquals(first, second)
    }
}
