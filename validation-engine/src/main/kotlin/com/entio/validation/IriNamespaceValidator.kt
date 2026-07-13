package com.entio.validation

import com.entio.core.EntioProjectConfig
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import com.entio.core.ValidationStatus
import com.entio.core.ValidationReport

/** Performs deterministic project-level checks for the optional generated-IRI namespace. */
public class IriNamespaceValidator {
    public fun validate(config: EntioProjectConfig): ValidationReport {
        val namespace = config.iriNamespace?.namespace?.value
        if (namespace == null) {
            return ValidationReport(status = ValidationStatus.Valid, issues = emptyList())
        }

        val issue = if (namespace.any(Char::isWhitespace) ||
            (!namespace.startsWith("http://") && !namespace.startsWith("https://")) ||
            (!namespace.endsWith('#') && !namespace.endsWith('/'))
        ) {
            ValidationIssue(
                severity = ValidationSeverity.Error,
                code = "invalid-iri-namespace",
                message = "iriNamespace must be an absolute HTTP(S) IRI ending with '#' or '/'.",
                source = "iriNamespace",
            )
        } else {
            null
        }

        return if (issue == null) {
            ValidationReport(status = ValidationStatus.Valid, issues = emptyList())
        } else {
            ValidationReport(status = ValidationStatus.Invalid, issues = listOf(issue))
        }
    }
}
