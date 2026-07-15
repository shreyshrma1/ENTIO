package com.entio.semantic

import com.entio.core.OwlFeatureFinding
import com.entio.core.OwlFeatureReport
import com.entio.core.OwlFeatureSupport
import org.semanticweb.owlapi.profiles.OWL2DLProfile

public class OwlFeatureReporter {
    public fun report(document: OwlOntologyDocument): OwlFeatureReport {
        val ontology = document.ontology
        val findings = ontology.axioms()
            .toList()
            .map { axiom -> axiom.axiomType.name }
            .toSet()
            .sorted()
            .map { axiomType ->
                OwlFeatureFinding(
                    feature = axiomType,
                    support = supportedAxiomTypes[axiomType] ?: OwlFeatureSupport.Partial,
                    affectsCompleteness = axiomType !in supportedAxiomTypes,
                    message = if (axiomType in supportedAxiomTypes) {
                        null
                    } else {
                        "Entio has not declared complete Phase 4 coverage for this OWL axiom type."
                    },
                )
            }
            .toMutableList()

        val profileReport = OWL2DLProfile().checkOntology(ontology)
        if (!profileReport.isInProfile) {
            findings += OwlFeatureFinding(
                feature = "OWL 2 DL profile",
                support = OwlFeatureSupport.Unsupported,
                affectsCompleteness = true,
                message = "The ontology is outside the OWL 2 DL profile.",
            )
        }

        return OwlFeatureReport(
            profile = "OWL 2 DL",
            findings = findings.sortedWith(compareBy { it.feature }),
        )
    }

    private companion object {
        private val supportedAxiomTypes: Map<String, OwlFeatureSupport> = mapOf(
            "SubClassOf" to OwlFeatureSupport.Supported,
            "EquivalentClasses" to OwlFeatureSupport.Partial,
            "InverseObjectProperties" to OwlFeatureSupport.Partial,
            "TransitiveObjectProperty" to OwlFeatureSupport.Partial,
            "ClassAssertion" to OwlFeatureSupport.Supported,
        )
    }
}
