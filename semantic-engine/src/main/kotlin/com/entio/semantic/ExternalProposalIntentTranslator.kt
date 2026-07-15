package com.entio.semantic

import com.entio.core.ChangeSet
import com.entio.core.EntioResult
import com.entio.core.ExternalDependencyCategory
import com.entio.core.ExternalDependencySelection
import com.entio.core.ExternalOntologyReference
import com.entio.core.ExternalProposalIntent
import com.entio.core.GraphChange
import com.entio.core.GraphChangeKind
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.Phase5PackageIdentity
import com.entio.core.RdfResource
import com.entio.core.RdfTerm
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity

/** Translates approved external reuse intents into existing graph-change proposals. */
public class ExternalProposalIntentTranslator {
    public fun translate(
        intent: ExternalProposalIntent,
        targetOntologyIri: Iri,
    ): EntioResult<ChangeSet> {
        val issues = mutableListOf<ValidationIssue>()
        if (intent.sourceId != Phase5PackageIdentity.SOURCE_ID) {
            issues += issue("unsupported-external-source", "Only the approved FIBO source is supported.")
        }
        if (targetOntologyIri.value.isBlank()) {
            issues += issue("invalid-target-ontology", "Target ontology IRI must not be blank.")
        }
        validateIntentFields(intent, issues)
        intent.dependencies.dependencies.forEach { dependency ->
            if (dependency.packageAvailable.not()) {
                issues += issue("unsupported-dependency", "Dependency '${dependency.reason}' is not available in the approved package.")
            }
            if (dependency.requirement == com.entio.core.ExternalDependencyRequirement.Required &&
                dependency.visibility == com.entio.core.ExternalDependencyVisibility.UserVisible &&
                dependency.selection in setOf(ExternalDependencySelection.Missing, ExternalDependencySelection.Rejected)
            ) {
                issues += issue("unapproved-required-dependency", "All required user-visible dependencies must be approved before translation.")
            }
        }
        if (issues.isNotEmpty()) {
            return EntioResult.Failure(
                message = "External proposal intent is not ready for translation.",
                issues = issues,
            )
        }

        val changes = when (intent) {
            is ExternalProposalIntent.ReuseExternalClass -> importsAndNoCopy(
                targetOntologyIri,
                sourceModules(intent),
            )
            is ExternalProposalIntent.ReuseExternalObjectProperty -> importsAndNoCopy(
                targetOntologyIri,
                sourceModules(intent),
            )
            is ExternalProposalIntent.ReuseExternalDatatypeProperty -> importsAndNoCopy(
                targetOntologyIri,
                sourceModules(intent),
            )
            is ExternalProposalIntent.CreateLocalSubclassOfExternalClass -> importsAndChanges(
                targetOntologyIri = targetOntologyIri,
                sourceModules = sourceModules(intent),
                changes = listOf(
                    addition(intent.localClassIri, RDF_TYPE, OWL_CLASS),
                    addition(intent.localClassIri, RDFS_SUBCLASS_OF, intent.externalSuperclassIri),
                ),
            )
            is ExternalProposalIntent.AddExternalOntologyReference -> importsAndNoCopy(
                targetOntologyIri,
                intent.reference.modules,
            )
        }

        return if (changes.isEmpty()) {
            EntioResult.Failure(
                message = "External proposal intent does not identify an approved source module.",
                issues = listOf(issue("missing-source-module", "An approved source ontology module is required.")),
            )
        } else {
            EntioResult.Success(ChangeSet(changes.distinct()))
        }
    }

    private fun sourceModules(intent: ExternalProposalIntent): List<Iri> = intent.dependencies.dependencies
        .filter { it.category == ExternalDependencyCategory.SourceOntology }
        .mapNotNull { it.externalIri ?: it.sourceModule }
        .distinct()
        .sortedBy(Iri::value)

    private fun validateIntentFields(intent: ExternalProposalIntent, issues: MutableList<ValidationIssue>): Unit = when (intent) {
        is ExternalProposalIntent.ReuseExternalClass -> validateIri(intent.classIri, "classIri", issues)
        is ExternalProposalIntent.ReuseExternalObjectProperty -> validateIri(intent.propertyIri, "propertyIri", issues)
        is ExternalProposalIntent.ReuseExternalDatatypeProperty -> validateIri(intent.propertyIri, "propertyIri", issues)
        is ExternalProposalIntent.CreateLocalSubclassOfExternalClass -> {
            validateIri(intent.localClassIri, "localClassIri", issues)
            validateIri(intent.externalSuperclassIri, "externalSuperclassIri", issues)
        }
        is ExternalProposalIntent.AddExternalOntologyReference -> validateReference(intent.reference, issues)
    }

    private fun validateReference(reference: ExternalOntologyReference, issues: MutableList<ValidationIssue>): Unit {
        if (reference.source != Phase5PackageIdentity.SOURCE_ID) {
            issues += issue("unsupported-external-source", "External reference source does not match the approved package.")
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
    }

    private fun validateIri(iri: Iri, field: String, issues: MutableList<ValidationIssue>): Unit {
        if (iri.value.isBlank()) issues += issue("invalid-external-iri", "External proposal field '$field' must not be blank.")
    }

    private fun importsAndNoCopy(targetOntologyIri: Iri, sourceModules: List<Iri>): List<GraphChange> =
        importsAndChanges(targetOntologyIri, sourceModules, emptyList())

    private fun importsAndChanges(
        targetOntologyIri: Iri,
        sourceModules: List<Iri>,
        changes: List<GraphChange>,
    ): List<GraphChange> = sourceModules
        .distinct()
        .sortedBy(Iri::value)
        .map { moduleIri -> addition(targetOntologyIri, OWL_IMPORTS, moduleIri) }
        .plus(changes)

    private fun addition(subject: RdfResource, predicate: Iri, objectTerm: RdfTerm): GraphChange = GraphChange(
        kind = GraphChangeKind.Addition,
        triple = GraphTriple(subject = subject, predicate = predicate, objectTerm = objectTerm),
    )

    private fun issue(code: String, message: String): ValidationIssue = ValidationIssue(
        severity = ValidationSeverity.Error,
        code = code,
        message = message,
        source = "external-proposal-intent",
    )

    private companion object {
        private val OWL_IMPORTS = Iri("http://www.w3.org/2002/07/owl#imports")
        private val RDF_TYPE = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
        private val OWL_CLASS = Iri("http://www.w3.org/2002/07/owl#Class")
        private val RDFS_SUBCLASS_OF = Iri("http://www.w3.org/2000/01/rdf-schema#subClassOf")
    }
}
