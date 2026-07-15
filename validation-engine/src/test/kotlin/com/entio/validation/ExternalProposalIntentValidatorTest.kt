package com.entio.validation

import com.entio.core.ExternalDependency
import com.entio.core.ExternalDependencyCategory
import com.entio.core.ExternalDependencyClosure
import com.entio.core.ExternalDependencyRequirement
import com.entio.core.ExternalDependencySelection
import com.entio.core.ExternalDependencySet
import com.entio.core.ExternalDependencyVisibility
import com.entio.core.ExternalOntologyMaturity
import com.entio.core.ExternalPackageFingerprint
import com.entio.core.ExternalProposalIntent
import com.entio.core.ExternalOntologyReference
import com.entio.core.Iri
import com.entio.core.ValidationStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalProposalIntentValidatorTest {
    private val module = Iri("https://spec.edmcouncil.org/fibo/ontology/FND/Parties/Parties/")
    private val classIri = Iri("https://spec.edmcouncil.org/fibo/ontology/FND/Parties/Parties/Party")

    @Test
    fun acceptsApprovedReuseIntent(): Unit {
        val intent = ExternalProposalIntent.ReuseExternalClass(
            classIri = classIri,
            sourceId = "fibo",
            dependencies = dependencies(ExternalDependencySelection.NewlySelected),
        )

        val report = ExternalProposalIntentValidator().validate(intent)

        assertEquals(ValidationStatus.Valid, report.status)
        assertTrue(report.issues.isEmpty())
    }

    @Test
    fun rejectsUnapprovedDependenciesAndStaleReferenceIdentity(): Unit {
        val intent = ExternalProposalIntent.AddExternalOntologyReference(
            reference = ExternalOntologyReference(
                source = "fibo",
                release = "old-release",
                commitSha = "old-commit",
                packageFingerprint = ExternalPackageFingerprint("fingerprint"),
                modules = listOf(module),
            ),
            sourceId = "fibo",
            dependencies = dependencies(ExternalDependencySelection.Missing),
        )

        val report = ExternalProposalIntentValidator().validate(intent)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertTrue(report.issues.any { it.code == "unapproved-required-dependency" })
        assertTrue(report.issues.any { it.code == "stale-package-release" })
        assertTrue(report.issues.any { it.code == "stale-package-commit" })
    }

    @Test
    fun rejectsUnsupportedSource(): Unit {
        val intent = ExternalProposalIntent.ReuseExternalClass(
            classIri = classIri,
            sourceId = "other-source",
            dependencies = dependencies(ExternalDependencySelection.NewlySelected),
        )

        val report = ExternalProposalIntentValidator().validate(intent)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertTrue(report.issues.any { it.code == "unsupported-external-source" })
    }

    private fun dependencies(selection: ExternalDependencySelection): ExternalDependencySet = ExternalDependencySet(
        dependencies = listOf(
            ExternalDependency(
                category = ExternalDependencyCategory.SourceOntology,
                requirement = ExternalDependencyRequirement.Required,
                closure = ExternalDependencyClosure.Direct,
                visibility = ExternalDependencyVisibility.UserVisible,
                selection = selection,
                reason = "test",
                externalIri = module,
                sourceModule = module,
                maturity = ExternalOntologyMaturity.Release,
            ),
        ),
    )
}
