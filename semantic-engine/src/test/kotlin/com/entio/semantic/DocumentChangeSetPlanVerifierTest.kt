package com.entio.semantic

import com.entio.core.DocumentAnalysisWorkKey
import com.entio.core.DocumentContentClassification
import com.entio.core.DocumentConfidenceDimensions
import com.entio.core.DocumentCoverageDisposition
import com.entio.core.DocumentCoverageDispositionKind
import com.entio.core.DocumentDiscoveryKind
import com.entio.core.DocumentEvidenceId
import com.entio.core.DocumentFinalPlan
import com.entio.core.DocumentFinalRecommendation
import com.entio.core.DocumentFinalRecommendationStatus
import com.entio.core.DocumentIndividualClassification
import com.entio.core.DocumentPlanOperand
import com.entio.core.DocumentPlanOperation
import com.entio.core.DocumentPlanOperationKind
import com.entio.core.DocumentTemporaryReference
import com.entio.core.DocumentTemporaryReferenceKind
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DocumentChangeSetPlanVerifierTest {
    @Test
    fun verifiesConnectedClassPropertyDomainAndRangeAndGeneratesCollisionCheckedIris(): Unit {
        val customer = DocumentTemporaryReference("new:class:Customer")
        val account = DocumentTemporaryReference("new:class:Account")
        val property = DocumentTemporaryReference("new:objectProperty:ownsAccount")
        val operations = listOf(
            create("create-customer", 0, DocumentPlanOperationKind.CreateClass, customer),
            create("create-account", 1, DocumentPlanOperationKind.CreateClass, account),
            create("create-property", 2, DocumentPlanOperationKind.CreateObjectProperty, property),
            operation(
                "domain",
                3,
                DocumentPlanOperationKind.SetPropertyDomain,
                listOf(temp(property), temp(customer)),
                listOf("create-customer", "create-property"),
            ),
            operation(
                "range",
                4,
                DocumentPlanOperationKind.SetPropertyRange,
                listOf(temp(property), temp(account)),
                listOf("create-account", "create-property"),
            ),
        )
        val verified = verifier.verify(plan(recommendation(operations)), context())

        assertEquals(3, verified.finalIris.size)
        assertEquals("https://example.com/simple#Customer", verified.finalIris.getValue(customer).value)
        assertTrue(verified.previews.single().semanticDiffSummary.contains("5 ordered"))
    }

    @Test
    fun verifiesIndividualAssertionAndSupportedShaclConstraint(): Unit {
        val person = DocumentTemporaryReference("new:individual:JordanLee")
        val customer = DocumentTemporaryReference("new:class:Customer")
        val shape = DocumentTemporaryReference("new:shape:CustomerShape")
        val operations = listOf(
            create("customer", 0, DocumentPlanOperationKind.CreateClass, customer),
            create("person", 1, DocumentPlanOperationKind.CreateIndividual, person),
            operation(
                "type",
                2,
                DocumentPlanOperationKind.AssignType,
                listOf(temp(person), temp(customer)),
                listOf("customer", "person"),
            ),
            create("shape", 3, DocumentPlanOperationKind.CreateNodeShape, shape),
            operation(
                "constraint",
                4,
                DocumentPlanOperationKind.UpdateShaclConstraint,
                listOf(temp(shape), DocumentPlanOperand.TextValue("MinCount"), DocumentPlanOperand.IntegerValue(1)),
                listOf("shape"),
            ),
        )

        val verified = verifier.verify(plan(recommendation(operations)), context())

        assertEquals(5, verified.plan.recommendations.single().expandedTypedEditCount)
        assertTrue(verified.previews.single().shaclSummary.contains("Supported typed SHACL"))
    }

    @Test
    fun rejectsUnwritableStaleCollidingAndKindInvalidPlans(): Unit {
        val customer = DocumentTemporaryReference("new:class:Customer")
        val property = DocumentTemporaryReference("new:objectProperty:ownsAccount")
        val missingSource = DocumentPlanOperation(
            id = "customer-without-source",
            kind = DocumentPlanOperationKind.CreateClass,
            order = 0,
            declaration = customer,
            expandedTypedEditCount = 1,
        )
        assertEquals(
            listOf("source-required"),
            verifier.verify(
                plan(recommendation(listOf(missingSource))),
                context(),
            ).plan.recommendations.single().blockers,
        )
        assertEquals(
            listOf("property-context-required"),
            verifier.verify(
                plan(recommendation(listOf(create(
                    "property",
                    0,
                    DocumentPlanOperationKind.CreateObjectProperty,
                    property,
                )))),
                context(),
            ).plan.recommendations.single().blockers,
        )
        val invalidKind = recommendation(
            listOf(
                create("customer", 0, DocumentPlanOperationKind.CreateClass, customer),
                create("property", 1, DocumentPlanOperationKind.CreateObjectProperty, property),
                operation(
                    "bad-domain",
                    2,
                    DocumentPlanOperationKind.SetPropertyDomain,
                    listOf(temp(customer), temp(property)),
                    listOf("customer", "property"),
                ),
            ),
        )
        assertEquals(
            listOf("operation-kind-invalid"),
            verifier.verify(plan(invalidKind), context()).plan.recommendations.single().blockers,
        )
        assertEquals(
            listOf("iri-collision"),
            verifier.verify(
                plan(recommendation(listOf(create("customer", 0, DocumentPlanOperationKind.CreateClass, customer)))),
                context(existing = mapOf(Iri("https://example.com/simple#Customer") to DocumentTemporaryReferenceKind.Class)),
            ).plan.recommendations.single().blockers,
        )
        assertFailsWith<IllegalArgumentException> {
            verifier.verify(plan(recommendation(emptyList(), DocumentFinalRecommendationStatus.Blocked)), context(current = "stale"))
        }
        val unwritable = operation(
            "label",
            0,
            DocumentPlanOperationKind.SetEntityLabel,
            listOf(
                DocumentPlanOperand.ExistingEntity(Iri("https://example.com/simple#Customer")),
                DocumentPlanOperand.TextValue("Customer"),
                DocumentPlanOperand.SourceId("readonly"),
            ),
        )
        assertEquals(
            listOf("unwritable-source"),
            verifier.verify(
                plan(recommendation(listOf(unwritable))),
                context(existing = mapOf(Iri("https://example.com/simple#Customer") to DocumentTemporaryReferenceKind.Class)),
            ).plan.recommendations.single().blockers,
        )
    }

    @Test
    fun optionalLeafExclusionAndSplitUseCompleteDependencyClosure(): Unit {
        val customer = DocumentTemporaryReference("new:class:Customer")
        val operations = listOf(
            create("customer", 0, DocumentPlanOperationKind.CreateClass, customer),
            operation(
                "label",
                1,
                DocumentPlanOperationKind.SetEntityLabel,
                listOf(temp(customer), DocumentPlanOperand.TextValue("Customer")),
                listOf("customer"),
                optional = true,
            ),
        )
        val recommendation = recommendation(operations)

        assertEquals(listOf("customer"), verifier.excludeOptionalLeaves(recommendation, setOf("label")).operations.map { it.id })
        assertEquals(setOf("customer", "label"), verifier.dependencyClosure(recommendation, setOf("label")))
        assertFailsWith<IllegalArgumentException> {
            verifier.excludeOptionalLeaves(recommendation, setOf("customer"))
        }
    }

    @Test
    fun invalidOperationBlocksItsAtomicRecommendationWithoutDiscardingUnrelatedWork(): Unit {
        val invalid = recommendation(
            listOf(
                operation(
                    "bad-label",
                    0,
                    DocumentPlanOperationKind.SetEntityLabel,
                    listOf(
                        DocumentPlanOperand.ExistingEntity(Iri("https://example.com/simple#Customer")),
                        DocumentPlanOperand.TextValue("Customer"),
                        DocumentPlanOperand.SourceId("readonly"),
                    ),
                ),
            ),
        ).copy(id = "recommendation-a", title = "A", discoveryIds = listOf("discovery-1"))
        val account = DocumentTemporaryReference("new:class:Account")
        val valid = recommendation(
            listOf(create("create-account", 0, DocumentPlanOperationKind.CreateClass, account)),
        ).copy(id = "recommendation-b", title = "B", discoveryIds = listOf("discovery-2"))
        val plan = DocumentFinalPlan(
            workKey = DocumentAnalysisWorkKey("a".repeat(64)),
            verifiedDiscoveryIds = listOf("discovery-1", "discovery-2"),
            criticFindingIds = emptyList(),
            recommendations = listOf(invalid, valid).sortedBy(DocumentFinalRecommendation::stableOrderingKey),
            coverage = listOf(
                DocumentCoverageDisposition(
                    "discovery-1",
                    DocumentCoverageDispositionKind.ExecutableRecommendation,
                    recommendationId = "recommendation-a",
                ),
                DocumentCoverageDisposition(
                    "discovery-2",
                    DocumentCoverageDispositionKind.ExecutableRecommendation,
                    recommendationId = "recommendation-b",
                ),
            ),
        )

        val verified = verifier.verify(
            plan,
            context(existing = mapOf(Iri("https://example.com/simple#Customer") to DocumentTemporaryReferenceKind.Class)),
        )

        assertEquals(DocumentFinalRecommendationStatus.Blocked, verified.plan.recommendations
            .single { it.id == "recommendation-a" }.status)
        assertEquals(DocumentFinalRecommendationStatus.Executable, verified.plan.recommendations
            .single { it.id == "recommendation-b" }.status)
        assertEquals("https://example.com/simple#Account", verified.finalIris.getValue(account).value)
    }

    @Test
    fun blocksAPropertyRangeThatUsesTextInsteadOfAnEntityReference(): Unit {
        val payment = DocumentTemporaryReference("new:class:Payment")
        val purpose = DocumentTemporaryReference("new:datatypeProperty:hasBusinessPurpose")
        val recommendation = recommendation(
            listOf(
                create("payment", 0, DocumentPlanOperationKind.CreateClass, payment),
                create("purpose", 1, DocumentPlanOperationKind.CreateDatatypeProperty, purpose),
                operation(
                    "domain",
                    2,
                    DocumentPlanOperationKind.SetPropertyDomain,
                    listOf(temp(purpose), temp(payment)),
                    listOf("payment", "purpose"),
                ),
                operation(
                    "range",
                    3,
                    DocumentPlanOperationKind.SetPropertyRange,
                    listOf(temp(purpose), DocumentPlanOperand.TextValue("string")),
                    listOf("purpose"),
                ),
            ),
        )

        val verified = verifier.verify(plan(recommendation), context())

        assertEquals(
            DocumentFinalRecommendationStatus.Blocked,
            verified.plan.recommendations.single().status,
        )
        assertEquals(
            listOf("operation-operand-invalid"),
            verified.plan.recommendations.single().blockers,
        )
    }

    @Test
    fun blocksAPropertyDeclarationThatCarriesDomainAndRangeAsDeclarationOperands(): Unit {
        val property = DocumentTemporaryReference("new:objectProperty:hasAccount")
        val recommendation = recommendation(
            listOf(
                DocumentPlanOperation(
                    id = "property",
                    kind = DocumentPlanOperationKind.CreateObjectProperty,
                    order = 0,
                    declaration = property,
                    operands = listOf(
                        DocumentPlanOperand.ExistingEntity(Iri("https://example.com/simple#Customer")),
                        DocumentPlanOperand.ExistingEntity(Iri("https://example.com/simple#Account")),
                        DocumentPlanOperand.SourceId("simple"),
                    ),
                    expandedTypedEditCount = 1,
                ),
            ),
        )

        val verified = verifier.verify(plan(recommendation), context())

        assertEquals(DocumentFinalRecommendationStatus.Blocked, verified.plan.recommendations.single().status)
        assertEquals(listOf("operation-operand-invalid"), verified.plan.recommendations.single().blockers)
    }

    @Test
    fun blocksMultipleDeclarationsThatHaveNoConnectingOperation(): Unit {
        val payment = DocumentTemporaryReference("new:class:Payment")
        val control = DocumentTemporaryReference("new:class:PaymentControl")
        val recommendation = recommendation(
            listOf(
                create("payment", 0, DocumentPlanOperationKind.CreateClass, payment),
                create("control", 1, DocumentPlanOperationKind.CreateClass, control),
                operation(
                    "label",
                    2,
                    DocumentPlanOperationKind.SetEntityLabel,
                    listOf(temp(control), DocumentPlanOperand.TextValue("Payment Authorization Control")),
                    listOf("control"),
                ),
            ),
        )

        val verified = verifier.verify(plan(recommendation), context())

        assertEquals(
            DocumentFinalRecommendationStatus.Blocked,
            verified.plan.recommendations.single().status,
        )
        assertEquals(
            listOf("disconnected-declarations"),
            verified.plan.recommendations.single().blockers,
        )
    }

    @Test
    fun blocksAdministrativeMetadataAndNormativeStatementsModeledAsClasses(): Unit {
        val policy = DocumentTemporaryReference("new:class:CommercialAccountPolicy")
        val policyRecommendation = recommendation(
            listOf(create("policy", 0, DocumentPlanOperationKind.CreateClass, policy)),
        )
        val administrative = verifier.verify(
            plan(policyRecommendation),
            context(
                discoveryKind = DocumentDiscoveryKind.Metadata,
                contentClassification = DocumentContentClassification.AdministrativeMetadata,
            ),
        )
        assertEquals(
            listOf("administrative-metadata-not-executable"),
            administrative.plan.recommendations.single().blockers,
        )

        val control = DocumentTemporaryReference("new:class:PaymentPurposeControl")
        val normative = verifier.verify(
            plan(recommendation(listOf(create("control", 0, DocumentPlanOperationKind.CreateClass, control)))),
            context(discoveryKind = DocumentDiscoveryKind.Requirement),
        )
        assertEquals(
            listOf("normative-meaning-modeled-as-class"),
            normative.plan.recommendations.single().blockers,
        )

        val payment = DocumentTemporaryReference("new:class:Payment")
        val normativePayment = verifier.verify(
            plan(recommendation(listOf(create("payment", 0, DocumentPlanOperationKind.CreateClass, payment)))),
            context(discoveryKind = DocumentDiscoveryKind.Requirement),
        )
        assertEquals(DocumentFinalRecommendationStatus.Executable, normativePayment.plan.recommendations.single().status)
        assertTrue(normativePayment.plan.recommendations.single().blockers.isEmpty())

        val operationalConcept = verifier.verify(
            plan(recommendation(listOf(create("payment", 0, DocumentPlanOperationKind.CreateClass, payment)))),
            context(discoveryKind = DocumentDiscoveryKind.Concept),
        )
        assertEquals(DocumentFinalRecommendationStatus.Executable, operationalConcept.plan.recommendations.single().status)
        assertTrue(operationalConcept.plan.recommendations.single().blockers.isEmpty())

        val validation = DocumentTemporaryReference("new:class:ElectronicSignaturesValidation")
        val normativeProcess = verifier.verify(
            plan(recommendation(listOf(create("validation", 0, DocumentPlanOperationKind.CreateClass, validation)))),
            context(discoveryKind = DocumentDiscoveryKind.Requirement),
        )
        assertEquals(
            listOf("normative-meaning-modeled-as-class"),
            normativeProcess.plan.recommendations.single().blockers,
        )

        val genericDefinition = DocumentTemporaryReference("new:class:Definition")
        val genericCategory = verifier.verify(
            plan(recommendation(listOf(create("definition", 0, DocumentPlanOperationKind.CreateClass, genericDefinition)))),
            context(discoveryKind = DocumentDiscoveryKind.Definition),
        )
        assertEquals(
            listOf("class-evidence-required"),
            genericCategory.plan.recommendations.single().blockers,
        )

        val pluralControl = DocumentTemporaryReference("new:class:ComplianceControls")
        val pluralGenericCategory = verifier.verify(
            plan(recommendation(listOf(create("controls", 0, DocumentPlanOperationKind.CreateClass, pluralControl)))),
            context(discoveryKind = DocumentDiscoveryKind.Concept),
        )
        assertEquals(
            listOf("normative-meaning-modeled-as-class"),
            pluralGenericCategory.plan.recommendations.single().blockers,
        )
    }

    @Test
    fun requiresParticularProductionEvidenceAndAnExplicitTypeForNewIndividuals(): Unit {
        val analyst = DocumentTemporaryReference("new:individual:ComplianceTestingAnalyst")
        val roleResult = verifier.verify(
            plan(recommendation(listOf(create(
                "analyst",
                0,
                DocumentPlanOperationKind.CreateIndividual,
                analyst,
            )))),
            context(discoveryKind = DocumentDiscoveryKind.Role),
        )
        assertEquals(
            listOf("individual-evidence-required"),
            roleResult.plan.recommendations.single().blockers,
        )

        val namedPerson = DocumentTemporaryReference("new:individual:MariaChen")
        val untypedResult = verifier.verify(
            plan(recommendation(listOf(create(
                "person",
                0,
                DocumentPlanOperationKind.CreateIndividual,
                namedPerson,
            )))),
            context(
                discoveryKind = DocumentDiscoveryKind.Individual,
                individualClassification = DocumentIndividualClassification.Production,
            ),
        )
        assertEquals(
            listOf("individual-type-required"),
            untypedResult.plan.recommendations.single().blockers,
        )
    }

    @Test
    fun allowsAnExplicitReviewerPlaceholderToCompleteAnIndividualType(): Unit {
        val organization = DocumentTemporaryReference("new:class:Organization")
        val bank = DocumentTemporaryReference("new:individual:MeridianCommunityBank")
        val operations = listOf(
            create(
                "organization",
                0,
                DocumentPlanOperationKind.CreateClass,
                organization,
            ).copy(reviewerInputRequired = true),
            create(
                "bank",
                1,
                DocumentPlanOperationKind.CreateIndividual,
                bank,
            ),
            operation(
                "bank-type",
                2,
                DocumentPlanOperationKind.AssignType,
                listOf(temp(bank), temp(organization)),
                listOf("bank", "organization"),
            ).copy(reviewerInputRequired = true),
        )

        val verified = verifier.verify(
            plan(recommendation(operations)),
            context(
                discoveryKind = DocumentDiscoveryKind.Individual,
                individualClassification = DocumentIndividualClassification.Production,
            ),
        )

        assertTrue(verified.plan.recommendations.single().blockers.isEmpty())
    }

    @Test
    fun allowsAnAttachedModelRecommendedClassWithoutStrictClassEvidence(): Unit {
        val approvalDecision = DocumentTemporaryReference("new:class:ApprovalDecision")
        val createsDecision = DocumentTemporaryReference("new:objectProperty:createsApprovalDecision")
        val meridianPay = Iri("https://example.com/simple#MeridianPay")
        val connected = listOf(
            create(
                "approval-decision",
                0,
                DocumentPlanOperationKind.CreateClass,
                approvalDecision,
            ).copy(modelRecommended = true),
            create(
                "creates-decision",
                1,
                DocumentPlanOperationKind.CreateObjectProperty,
                createsDecision,
            ),
            operation(
                "decision-domain",
                2,
                DocumentPlanOperationKind.SetPropertyDomain,
                listOf(temp(createsDecision), DocumentPlanOperand.ExistingEntity(meridianPay)),
                listOf("creates-decision"),
            ),
            operation(
                "decision-range",
                3,
                DocumentPlanOperationKind.SetPropertyRange,
                listOf(temp(createsDecision), temp(approvalDecision)),
                listOf("approval-decision", "creates-decision"),
            ).copy(modelRecommended = true),
        )

        val verified = verifier.verify(
            plan(recommendation(connected)),
            context(
                existing = mapOf(meridianPay to DocumentTemporaryReferenceKind.Class),
                discoveryKind = DocumentDiscoveryKind.Relationship,
            ),
        )

        assertEquals(DocumentFinalRecommendationStatus.Executable, verified.plan.recommendations.single().status)
        assertTrue(verified.plan.recommendations.single().blockers.isEmpty())

        val unattached = verifier.verify(
            plan(recommendation(listOf(
                create(
                    "approval-decision",
                    0,
                    DocumentPlanOperationKind.CreateClass,
                    approvalDecision,
                ).copy(modelRecommended = true),
            ))),
            context(discoveryKind = DocumentDiscoveryKind.Relationship),
        )
        assertEquals(
            listOf("model-recommended-prerequisite-unattached"),
            unattached.plan.recommendations.single().blockers,
        )

        val editableRecommendation = verifier.verify(
            plan(recommendation(listOf(
                create(
                    "approval-decision",
                    0,
                    DocumentPlanOperationKind.CreateClass,
                    approvalDecision,
                ).copy(modelRecommended = true, reviewerInputRequired = true),
            ))),
            context(discoveryKind = DocumentDiscoveryKind.Relationship),
        )
        assertEquals(
            DocumentFinalRecommendationStatus.Executable,
            editableRecommendation.plan.recommendations.single().status,
        )
        assertTrue(editableRecommendation.plan.recommendations.single().blockers.isEmpty())
    }

    private val verifier = DocumentChangeSetPlanVerifier()

    private fun context(
        existing: Map<Iri, DocumentTemporaryReferenceKind> = emptyMap(),
        current: String = "ontology",
        discoveryKind: DocumentDiscoveryKind? = null,
        contentClassification: DocumentContentClassification = DocumentContentClassification.BusinessContent,
        individualClassification: DocumentIndividualClassification? = null,
    ): DocumentPlanVerificationContext = DocumentPlanVerificationContext(
        expectedOntologyFingerprint = "ontology",
        currentOntologyFingerprint = current,
        expectedCurrentWorkFingerprint = "work",
        currentWorkFingerprint = "work",
        writableSourceIds = setOf("simple"),
        existingEntityKinds = existing,
        iriNamespace = "https://example.com/simple",
        discoveryKinds = discoveryKind?.let { mapOf("discovery-1" to it) }.orEmpty(),
        discoveryContentClassifications = discoveryKind?.let {
            mapOf("discovery-1" to contentClassification)
        }.orEmpty(),
        discoveryIndividualClassifications = discoveryKind?.let {
            mapOf("discovery-1" to individualClassification)
        }.orEmpty(),
    )

    private fun plan(recommendation: DocumentFinalRecommendation): DocumentFinalPlan = DocumentFinalPlan(
        workKey = DocumentAnalysisWorkKey("a".repeat(64)),
        verifiedDiscoveryIds = listOf("discovery-1"),
        criticFindingIds = emptyList(),
        recommendations = listOf(recommendation).sortedBy(DocumentFinalRecommendation::stableOrderingKey),
        coverage = listOf(
            DocumentCoverageDisposition(
                "discovery-1",
                if (recommendation.status == DocumentFinalRecommendationStatus.ReviewOnly) {
                    DocumentCoverageDispositionKind.ReviewOnlyFinding
                } else {
                    DocumentCoverageDispositionKind.ExecutableRecommendation
                },
                recommendationId = recommendation.id,
            ),
        ),
    )

    private fun recommendation(
        operations: List<DocumentPlanOperation>,
        status: DocumentFinalRecommendationStatus =
            if (operations.isEmpty()) DocumentFinalRecommendationStatus.Blocked else DocumentFinalRecommendationStatus.Executable,
    ): DocumentFinalRecommendation = DocumentFinalRecommendation(
        id = "recommendation-1",
        title = "Connected customer model",
        description = "Create a connected model from verified discoveries.",
        discoveryIds = listOf("discovery-1"),
        evidenceIds = listOf(DocumentEvidenceId("evidence-1")),
        operations = operations,
        confidence = DocumentConfidenceDimensions(90, 85, 80),
        status = status,
        blockers = if (status == DocumentFinalRecommendationStatus.Blocked) listOf("invalid-operation") else emptyList(),
    )

    private fun create(
        id: String,
        order: Int,
        kind: DocumentPlanOperationKind,
        reference: DocumentTemporaryReference,
    ): DocumentPlanOperation = DocumentPlanOperation(
        id,
        kind,
        order,
        reference,
        operands = listOf(DocumentPlanOperand.SourceId("simple")),
        expandedTypedEditCount = 1,
    )

    private fun operation(
        id: String,
        order: Int,
        kind: DocumentPlanOperationKind,
        operands: List<DocumentPlanOperand>,
        dependencies: List<String> = emptyList(),
        optional: Boolean = false,
    ): DocumentPlanOperation = DocumentPlanOperation(
        id,
        kind,
        order,
        operands = if (operands.any { it is DocumentPlanOperand.SourceId }) {
            operands
        } else {
            operands + DocumentPlanOperand.SourceId("simple")
        },
        dependsOnOperationIds = dependencies.sorted(),
        expandedTypedEditCount = 1,
        optionalLeaf = optional,
    )

    private fun temp(reference: DocumentTemporaryReference): DocumentPlanOperand =
        DocumentPlanOperand.TemporaryEntity(reference)
}
