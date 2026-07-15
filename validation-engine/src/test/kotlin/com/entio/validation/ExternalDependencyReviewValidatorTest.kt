package com.entio.validation

import com.entio.core.ExternalDependency
import com.entio.core.ExternalDependencyCategory
import com.entio.core.ExternalDependencyClosure
import com.entio.core.ExternalDependencyRequirement
import com.entio.core.ExternalDependencySelection
import com.entio.core.ExternalDependencySet
import com.entio.core.ExternalDependencySetStatus
import com.entio.core.ExternalDependencyVisibility
import com.entio.core.ExternalOntologyMaturity
import com.entio.core.Iri
import com.entio.core.ValidationStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalDependencyReviewValidatorTest {
    private val validator = ExternalDependencyReviewValidator()

    @Test
    fun acceptsAvailableAndExplicitlySelectedRequiredDependencies(): Unit {
        val report = validator.validate(
            ExternalDependencySet(
                dependencies = listOf(
                    dependency(
                        category = ExternalDependencyCategory.SourceOntology,
                        externalIri = "https://example.com/module",
                        sourceModule = "https://example.com/module",
                        selection = ExternalDependencySelection.AlreadyAvailable,
                    ),
                    dependency(
                        category = ExternalDependencyCategory.SemanticParent,
                        externalIri = "https://example.com/parent",
                        selection = ExternalDependencySelection.NewlySelected,
                    ),
                    dependency(
                        category = ExternalDependencyCategory.PackageRuntime,
                        externalIri = null,
                        sourceModule = "https://example.com/module",
                        visibility = ExternalDependencyVisibility.ImplementationOnly,
                    ),
                ),
            ),
        )

        assertEquals(ValidationStatus.Valid, report.status)
        assertTrue(report.issues.isEmpty())
    }

    @Test
    fun rejectsMissingRequiredDependenciesAndInvalidPackageState(): Unit {
        val report = validator.validate(
            ExternalDependencySet(
                dependencies = listOf(
                    dependency(
                        category = ExternalDependencyCategory.PropertyDomain,
                        externalIri = "https://example.com/domain",
                        selection = ExternalDependencySelection.Missing,
                    ),
                    dependency(
                        category = ExternalDependencyCategory.PackageRuntime,
                        externalIri = null,
                        sourceModule = "https://example.com/module",
                        selection = ExternalDependencySelection.AlreadyAvailable,
                        packageAvailable = false,
                        visibility = ExternalDependencyVisibility.ImplementationOnly,
                    ),
                ),
            ),
        )

        assertEquals(ValidationStatus.Invalid, report.status)
        assertTrue(report.issues.any { it.code == "unapproved-required-dependency" })
        assertTrue(report.issues.any { it.code == "unsupported-dependency" })
        assertTrue(report.issues.any { it.code == "stale-dependency" })
    }

    @Test
    fun rejectsIncompleteConflictingAndDuplicateSelectionsDeterministically(): Unit {
        val duplicate = dependency(
            category = ExternalDependencyCategory.SemanticParent,
            externalIri = "https://example.com/parent",
        )
        val report = validator.validate(
            ExternalDependencySet(
                status = ExternalDependencySetStatus.Conflicting,
                dependencies = listOf(
                    duplicate,
                    duplicate.copy(selection = ExternalDependencySelection.Rejected),
                ),
            ),
        )

        assertEquals(ValidationStatus.Invalid, report.status)
        assertTrue(report.issues.any { it.code == "conflicting-dependency-set" })
        assertTrue(report.issues.any { it.code == "duplicate-dependency" })
        assertTrue(report.issues.any { it.code == "conflicting-dependency-selection" })
        assertFalse(report.issues.zipWithNext().any { (first, second) ->
            first.severity.ordinal > second.severity.ordinal ||
                (first.severity == second.severity && first.code > second.code)
        })
    }

    @Test
    fun allowsRejectedOptionalDependencyAsAWarning(): Unit {
        val report = validator.validate(
            ExternalDependencySet(
                dependencies = listOf(
                    dependency(
                        category = ExternalDependencyCategory.LocalReference,
                        externalIri = "https://example.com/local",
                        requirement = ExternalDependencyRequirement.Optional,
                        selection = ExternalDependencySelection.Rejected,
                    ),
                ),
            ),
        )

        assertEquals(ValidationStatus.Valid, report.status)
        assertEquals("rejected-optional-dependency", report.issues.single().code)
    }

    private fun dependency(
        category: ExternalDependencyCategory,
        externalIri: String?,
        sourceModule: String? = "https://example.com/module",
        requirement: ExternalDependencyRequirement = ExternalDependencyRequirement.Required,
        visibility: ExternalDependencyVisibility = ExternalDependencyVisibility.UserVisible,
        selection: ExternalDependencySelection = ExternalDependencySelection.NewlySelected,
        packageAvailable: Boolean = true,
    ): ExternalDependency = ExternalDependency(
        category = category,
        requirement = requirement,
        closure = if (category == ExternalDependencyCategory.PackageRuntime) {
            ExternalDependencyClosure.PackageTransitive
        } else {
            ExternalDependencyClosure.Direct
        },
        visibility = visibility,
        selection = selection,
        reason = "test",
        externalIri = externalIri?.let(::Iri),
        sourceModule = sourceModule?.let(::Iri),
        maturity = ExternalOntologyMaturity.Release,
        packageAvailable = packageAvailable,
    )
}
