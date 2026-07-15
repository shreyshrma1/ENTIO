package com.entio.validation

import com.entio.core.ExternalOntologyReference
import com.entio.core.ExternalProposalIntent
import com.entio.core.Phase5PackageIdentity
import com.entio.core.ValidationIssue
import com.entio.core.ValidationReport
import com.entio.core.ValidationSeverity
import com.entio.core.ValidationStatus

/** Validates external proposal intent and fixed-package identity before translation or staging. */
public class ExternalProposalIntentValidator(
    private val dependencyValidator: ExternalDependencyReviewValidator = ExternalDependencyReviewValidator(),
) {
    public fun validate(intent: ExternalProposalIntent): ValidationReport {
        val issues = mutableListOf<ValidationIssue>()
        if (intent.sourceId != Phase5PackageIdentity.SOURCE_ID) {
            issues += issue("unsupported-external-source", "Only the approved FIBO source is supported.")
        }
        issues += dependencyValidator.validate(intent.dependencies).issues
            .filter { it.severity == ValidationSeverity.Error }
        when (intent) {
            is ExternalProposalIntent.ReuseExternalClass -> validateIri(intent.classIri.value, "classIri", issues)
            is ExternalProposalIntent.ReuseExternalObjectProperty -> validateIri(intent.propertyIri.value, "propertyIri", issues)
            is ExternalProposalIntent.ReuseExternalDatatypeProperty -> validateIri(intent.propertyIri.value, "propertyIri", issues)
            is ExternalProposalIntent.CreateLocalSubclassOfExternalClass -> {
                validateIri(intent.localClassIri.value, "localClassIri", issues)
                validateIri(intent.externalSuperclassIri.value, "externalSuperclassIri", issues)
            }
            is ExternalProposalIntent.AddExternalOntologyReference -> validateReference(intent.reference, issues)
        }
        val sortedIssues = issues.sortedWith(issueComparator)
        return ValidationReport(
            status = if (sortedIssues.any { it.severity == ValidationSeverity.Error }) {
                ValidationStatus.Invalid
            } else {
                ValidationStatus.Valid
            },
            issues = sortedIssues,
        )
    }

    private fun validateReference(reference: ExternalOntologyReference, issues: MutableList<ValidationIssue>): Unit {
        if (reference.source != Phase5PackageIdentity.SOURCE_ID) {
            issues += issue("unsupported-external-source", "External reference source does not match the approved FIBO package.")
        }
        if (reference.release != Phase5PackageIdentity.RELEASE) {
            issues += issue("stale-package-release", "External reference release does not match the approved package.")
        }
        if (reference.commitSha != Phase5PackageIdentity.COMMIT_SHA) {
            issues += issue("stale-package-commit", "External reference commit does not match the approved package.")
        }
        if (reference.packageFingerprint.algorithm != Phase5PackageIdentity.CHECKSUM_ALGORITHM) {
            issues += issue("invalid-package-fingerprint", "External package fingerprint must use SHA-256.")
        }
        if (reference.packageFingerprint.value.isBlank()) {
            issues += issue("missing-package-fingerprint", "External package fingerprint must not be blank.")
        }
        if (reference.modules.isEmpty()) {
            issues += issue("missing-external-module", "At least one approved external module is required.")
        }
        if (reference.modules.distinct().size != reference.modules.size) {
            issues += issue("duplicate-external-module", "External module references must be unique.")
        }
    }

    private fun validateIri(value: String, field: String, issues: MutableList<ValidationIssue>): Unit {
        if (value.isBlank()) issues += issue("invalid-external-iri", "External proposal field '$field' must not be blank.")
    }

    private fun issue(code: String, message: String): ValidationIssue = ValidationIssue(
        severity = ValidationSeverity.Error,
        code = code,
        message = message,
        source = "external-proposal-intent",
    )

    private companion object {
        private val issueComparator = compareBy<ValidationIssue> { it.severity.ordinal }
            .thenBy { it.code }
            .thenBy { it.message }
    }
}
