package com.entio.validation

import com.entio.core.ExternalDependency
import com.entio.core.ExternalDependencyCategory
import com.entio.core.ExternalDependencyRequirement
import com.entio.core.ExternalDependencySelection
import com.entio.core.ExternalDependencySet
import com.entio.core.ExternalDependencySetStatus
import com.entio.core.ExternalDependencyVisibility
import com.entio.core.ExternalOntologyMaturity
import com.entio.core.ValidationIssue
import com.entio.core.ValidationReport
import com.entio.core.ValidationSeverity
import com.entio.core.ValidationStatus

/** Deterministically rejects incomplete or contradictory user dependency selections. */
public class ExternalDependencyReviewValidator {
    public fun validate(dependencySet: ExternalDependencySet): ValidationReport {
        val issues = mutableListOf<ValidationIssue>()
        when (dependencySet.status) {
            ExternalDependencySetStatus.Complete -> Unit
            ExternalDependencySetStatus.Incomplete -> issues += issue(
                "incomplete-dependency-set",
                "The dependency review is incomplete.",
            )
            ExternalDependencySetStatus.Conflicting -> issues += issue(
                "conflicting-dependency-set",
                "The dependency review contains conflicting selections.",
            )
            ExternalDependencySetStatus.Invalid -> issues += issue(
                "invalid-dependency-set",
                "The dependency review is invalid.",
            )
        }

        issues += duplicateIssues(dependencySet.dependencies)
        issues += conflictingSelectionIssues(dependencySet.dependencies)
        dependencySet.dependencies.forEach { dependency ->
            val source = dependency.externalIri?.value ?: dependency.sourceModule?.value ?: dependency.category.name
            if (!dependency.packageAvailable) {
                issues += issue("unsupported-dependency", "The dependency is not available in the approved package.", source)
            }
            if (dependency.selection == ExternalDependencySelection.AlreadyAvailable && !dependency.packageAvailable) {
                issues += issue("stale-dependency", "A dependency marked already available is no longer available.", source)
            }
            if (requiresExternalIri(dependency.category) && dependency.externalIri == null) {
                issues += issue("invalid-dependency-iri", "This dependency category requires an external IRI.", source)
            }
            if (requiresSourceModule(dependency.category) && dependency.sourceModule == null) {
                issues += issue("invalid-dependency-module", "This dependency category requires a source module.", source)
            }
            if (dependency.requirement == ExternalDependencyRequirement.Required &&
                dependency.visibility == ExternalDependencyVisibility.UserVisible &&
                dependency.selection in setOf(ExternalDependencySelection.Missing, ExternalDependencySelection.Rejected)
            ) {
                issues += issue(
                    "unapproved-required-dependency",
                    "A required user-visible dependency must be available or explicitly selected.",
                    source,
                )
            }
            if (dependency.selection == ExternalDependencySelection.Rejected &&
                dependency.requirement == ExternalDependencyRequirement.Optional
            ) {
                issues += ValidationIssue(
                    severity = ValidationSeverity.Warning,
                    code = "rejected-optional-dependency",
                    message = "An optional dependency was rejected.",
                    source = source,
                )
            }
            if (dependency.category == ExternalDependencyCategory.Metadata &&
                dependency.maturity == ExternalOntologyMaturity.Unknown
            ) {
                issues += issue("invalid-maturity-metadata", "Maturity metadata is required for metadata review.", source)
            }
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

    private fun duplicateIssues(dependencies: List<ExternalDependency>): List<ValidationIssue> = dependencies
        .groupBy { dependency -> Triple(dependency.category, dependency.externalIri, dependency.sourceModule) }
        .filterValues { values -> values.size > 1 }
        .map { (key, _) -> issue("duplicate-dependency", "The dependency appears more than once: $key.", key.toString()) }

    private fun conflictingSelectionIssues(dependencies: List<ExternalDependency>): List<ValidationIssue> = dependencies
        .groupBy { dependency -> Triple(dependency.category, dependency.externalIri, dependency.sourceModule) }
        .filterValues { values -> values.map { it.selection }.distinct().size > 1 }
        .map { (key, _) -> issue("conflicting-dependency-selection", "The dependency has conflicting selections: $key.", key.toString()) }

    private fun requiresExternalIri(category: ExternalDependencyCategory): Boolean = category in setOf(
        ExternalDependencyCategory.SemanticParent,
        ExternalDependencyCategory.PropertyDomain,
        ExternalDependencyCategory.PropertyRange,
        ExternalDependencyCategory.OwlImport,
        ExternalDependencyCategory.Metadata,
        ExternalDependencyCategory.LocalReference,
    )

    private fun requiresSourceModule(category: ExternalDependencyCategory): Boolean = category in setOf(
        ExternalDependencyCategory.SourceOntology,
        ExternalDependencyCategory.PackageRuntime,
    )

    private fun issue(code: String, message: String, source: String? = null): ValidationIssue = ValidationIssue(
        severity = ValidationSeverity.Error,
        code = code,
        message = message,
        source = source,
    )

    private companion object {
        private val issueComparator = compareBy<ValidationIssue> { it.severity.ordinal }
            .thenBy { it.code }
            .thenBy { it.source.orEmpty() }
            .thenBy { it.message }
    }
}
