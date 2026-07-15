package com.entio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Phase4ContractsTest {
    private val customer = Iri("https://example.com/entio/simple#Customer")
    private val shrey = Iri("https://example.com/entio/simple#Shrey")
    private val label = Iri("http://www.w3.org/2000/01/rdf-schema#label")

    @Test
    fun representsReasoningOriginAndRunMetadata(): Unit {
        val fingerprints = ReasoningFingerprints(
            graphFingerprint = "graph-1",
            importClosureFingerprint = "imports-1",
            reasonerConfigurationFingerprint = "config-1",
        )
        val metadata = ReasoningRunMetadata(
            status = ReasoningRunStatus.Completed,
            reasonerName = "HermiT",
            reasonerVersion = "pinned",
            owlApiVersion = "pinned",
            fingerprints = fingerprints,
            importClosureComplete = true,
        )
        val result = ReasoningResult(
            metadata = metadata,
            consistency = ConsistencyStatus.Consistent,
            classRelationships = listOf(
                ReasoningClassRelationship(customer, Iri("https://example.com/entio/simple#Account"), FactOrigin.Asserted),
            ),
            individualTypes = listOf(
                ReasoningIndividualType(shrey, customer, FactOrigin.Inferred),
            ),
        )

        assertEquals(FactOrigin.Asserted, result.classRelationships.single().origin)
        assertEquals(FactOrigin.Inferred, result.individualTypes.single().origin)
        assertEquals(ReasoningRunStatus.Completed, result.metadata.status)
        assertTrue(result.metadata.importClosureComplete)
        assertEquals(result, result.copy())
    }

    @Test
    fun representsImportsFeaturesAndExplanations(): Unit {
        val import = ImportFinding(
            importedIri = Iri("https://example.com/imported"),
            kind = ImportFindingKind.Cycle,
            message = "Import cycle detected.",
            sourceId = "simple",
        )
        val imports = ImportClosureReport(
            sourceIds = listOf("simple", "imported"),
            findings = listOf(import),
            complete = true,
        )
        val feature = OwlFeatureFinding(
            feature = "owl:equivalentClass",
            support = OwlFeatureSupport.Partial,
            affectsCompleteness = true,
        )
        val explanation = ReasoningExplanation(
            target = customer,
            kind = ReasoningExplanationKind.Inference,
            rule = "subclass propagation",
            assertedEvidence = listOf(GraphTriple(customer, label, RdfLiteral("Customer"))),
            complete = false,
            caveat = "Minimal justification unavailable.",
        )

        assertEquals(ImportFindingKind.Cycle, imports.findings.single().kind)
        assertFalse(imports.findings.single().kind == ImportFindingKind.Missing)
        assertEquals(OwlFeatureSupport.Partial, feature.support)
        assertEquals(ReasoningExplanationKind.Inference, explanation.kind)
        assertFalse(explanation.complete)
    }

    @Test
    fun preservesShaclRolesTargetsConstraintsAndResults(): Unit {
        val shapeId = ShaclShapeId(
            iri = Iri("https://example.com/entio/simple#CustomerShape"),
            sourceId = "shapes",
        )
        val shape = ShaclNodeShape(
            id = shapeId,
            targets = listOf(ShaclTarget.TargetClass(customer)),
            propertyShapes = listOf(
                ShaclPropertyShape(
                    id = ShaclShapeId(
                        iri = Iri("https://example.com/entio/simple#CustomerNameShape"),
                        sourceId = "shapes",
                    ),
                    path = ShaclPath.DirectProperty(label),
                    constraints = listOf(
                        ShaclConstraint(
                            kind = ShaclConstraintKind.MinCount,
                            value = ShaclConstraintValue.IntegerValue(1),
                        ),
                    ),
                ),
            ),
        )
        val result = ShaclValidationResult(
            severity = ShaclSeverity.Violation,
            message = "A customer must have a label.",
            focusNode = shrey,
            path = ShaclPath.DirectProperty(label),
            shape = shape.id,
            constraint = ShaclConstraintKind.MinCount,
        )
        val report = ShaclValidationReport(
            status = ShaclValidationStatus.Completed,
            mode = ShaclValidationMode.AssertedOnly,
            graphIdentity = ShaclGraphIdentity(
                dataSourceIds = listOf("simple"),
                shapesSourceIds = listOf("shapes"),
                dataGraphFingerprint = "data-1",
                shapesGraphFingerprint = "shapes-1",
            ),
            results = listOf(result),
        )

        assertEquals(setOf(ShaclGraphRole.Shapes), ShaclSourceRole("shapes", setOf(ShaclGraphRole.Shapes)).roles)
        assertIs<ShaclTarget.TargetClass>(shape.targets.single())
        assertEquals(ShaclConstraintKind.MinCount, shape.propertyShapes.single().constraints.single().kind)
        assertEquals(ShaclValidationMode.AssertedOnly, report.mode)
        assertEquals(ShaclSeverity.Violation, report.results.single().severity)
    }

    @Test
    fun representsProposalImpactAndMultiSourceOutcomes(): Unit {
        val diff = SemanticDiff(entries = emptyList())
        val impact = ProposalImpactReport(
            explicitDiff = diff,
            reasoningImpact = ReasoningImpactSummary(
                consistencyChanged = true,
                unsatisfiableClassesAdded = listOf(customer),
            ),
            shaclImpact = ShaclImpactSummary(),
            status = BaselineImpactStatus.BlocksApproval,
            blockingMessages = listOf("The preview is inconsistent."),
        )
        val apply = MultiSourceApplyResult(
            status = MultiSourceApplyStatus.RolledBack,
            changedFiles = listOf("ontology/simple.ttl", "shapes/simple-shapes.ttl"),
            restoredFiles = listOf("ontology/simple.ttl", "shapes/simple-shapes.ttl"),
        )

        assertEquals(BaselineImpactStatus.BlocksApproval, impact.status)
        assertEquals(customer, impact.reasoningImpact.unsatisfiableClassesAdded.single())
        assertEquals(MultiSourceApplyStatus.RolledBack, apply.status)
        assertEquals(2, apply.restoredFiles.size)
    }
}
