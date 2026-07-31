package com.entio.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentAnalysisPipelineContractsTest {
    private val documentId = DocumentId("document-1")
    private val evidenceId = DocumentEvidenceId("evidence-1")
    private val evidence = DocumentEvidence(
        id = evidenceId,
        type = DocumentEvidenceType.Explicit,
        references = listOf(
            DocumentEvidenceReference(
                id = evidenceId,
                documentId = documentId,
                blockId = DocumentTextBlockId("block-1"),
                pageNumber = 1,
                startOffsetInBlock = 0,
                endOffsetInBlock = 7,
                exactExcerpt = "Payment",
                extractionMethod = DocumentExtractionMethod.EmbeddedText,
            ),
        ),
    )

    @Test
    fun `records provider and deterministic stages without leaking provider types`(): Unit {
        val startedAt = Instant.parse("2026-07-27T12:00:00Z")
        val completedAt = Instant.parse("2026-07-27T12:00:01Z")
        DocumentAnalysisStage.entries.forEachIndexed { stageIndex, stage ->
            DocumentAnalysisStageState.entries.forEachIndexed { stateIndex, state ->
                val providerMetadata = stage.providerBacked && state != DocumentAnalysisStageState.Pending
                val active = state in setOf(
                    DocumentAnalysisStageState.Running,
                    DocumentAnalysisStageState.Retrying,
                )
                val terminal = state.terminal
                val successful = state == DocumentAnalysisStageState.Succeeded
                val record = DocumentAnalysisStageRecord(
                    recordId = "stage-$stageIndex-$stateIndex",
                    stage = stage,
                    state = state,
                    scopeId = "scope-$stageIndex",
                    startedAt = startedAt.takeIf { state != DocumentAnalysisStageState.Pending },
                    finishedAt = completedAt.takeIf { terminal },
                    durationMillis = 1_000L.takeIf { terminal },
                    selectedModelId = "gpt-model".takeIf { providerMetadata },
                    promptVersion = DocumentAnalysisPipelineVersions.DISCOVERY_PROMPT.takeIf { providerMetadata },
                    requestSchemaVersion =
                        DocumentAnalysisPipelineVersions.DISCOVERY_REQUEST.takeIf { providerMetadata },
                    responseSchemaVersion =
                        DocumentAnalysisPipelineVersions.DISCOVERY_RESPONSE.takeIf { providerMetadata },
                    inputSha256 = "a".repeat(64).takeIf { state != DocumentAnalysisStageState.Pending },
                    outputSha256 = "b".repeat(64).takeIf { successful },
                    providerAttemptCount = if (providerMetadata) 1 else 0,
                    completedCount = if (active) 0 else 1,
                    totalCount = 1,
                    safeCode = "safe-stage-stop".takeIf { terminal && !successful },
                )

                assertEquals(stage, record.stage)
                assertEquals(state, record.state)
            }
        }
        val providerStage = successfulProviderStage(startedAt, completedAt)
        val deterministicStage = successfulDeterministicStage(startedAt, completedAt)

        assertTrue(providerStage.stableOrderingKey < deterministicStage.stableOrderingKey)
        assertFailsWith<IllegalArgumentException> {
            providerStage.copy(providerAttemptCount = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            deterministicStage.copy(selectedModelId = "gpt-model")
        }
    }

    @Test
    fun `keeps discovery classifications independent and evidence grounded`(): Unit {
        val administrativeAttribute = discovery(
            kind = DocumentDiscoveryKind.Attribute,
            contentClassification = DocumentContentClassification.AdministrativeMetadata,
        )
        val illustrativeIndividual = discovery(
            id = "discovery-2",
            kind = DocumentDiscoveryKind.Individual,
            individualClassification = DocumentIndividualClassification.Illustrative,
        )

        assertEquals(DocumentDiscoveryKind.Attribute, administrativeAttribute.kind)
        assertEquals(DocumentIndividualClassification.Illustrative, illustrativeIndividual.individualClassification)
        assertFailsWith<IllegalArgumentException> {
            discovery(kind = DocumentDiscoveryKind.Individual)
        }
        assertFailsWith<IllegalArgumentException> {
            discovery(evidence = emptyList())
        }
    }

    @Test
    fun `requires connected model references to point to earlier items`(): Unit {
        assertTrue(DocumentConnectedModel(emptyList()).items.isEmpty())
        val payment = connectedItem(id = "item-1", order = 0, label = "Payment")
        val approvalRecord = connectedItem(id = "item-2", order = 1, label = "Payment Approval Record")
        val relationship = connectedItem(
            id = "item-3",
            order = 2,
            kind = DocumentConnectedModelItemKind.SubclassRelationship,
            label = "Payment Approval Record is a Payment",
            references = listOf(
                DocumentConnectedModelReference(DocumentConnectedModelReferenceRole.Subclass, approvalRecord.id),
                DocumentConnectedModelReference(DocumentConnectedModelReferenceRole.Superclass, payment.id),
            ).sortedBy(DocumentConnectedModelReference::stableOrderingKey),
        )
        val property = connectedItem(
            id = "item-4",
            order = 3,
            kind = DocumentConnectedModelItemKind.ObjectProperty,
            label = "Has approval record",
        )
        val domain = connectedItem(
            id = "item-5",
            order = 4,
            kind = DocumentConnectedModelItemKind.DomainAssignment,
            label = "Approved amount domain",
            references = listOf(
                DocumentConnectedModelReference(DocumentConnectedModelReferenceRole.Property, property.id),
                DocumentConnectedModelReference(DocumentConnectedModelReferenceRole.Domain, approvalRecord.id),
            ).sortedBy(DocumentConnectedModelReference::stableOrderingKey),
        )
        val range = connectedItem(
            id = "item-6",
            order = 5,
            kind = DocumentConnectedModelItemKind.RangeAssignment,
            label = "Approved amount range",
            references = listOf(
                DocumentConnectedModelReference(DocumentConnectedModelReferenceRole.Property, property.id),
                DocumentConnectedModelReference(DocumentConnectedModelReferenceRole.Range, payment.id),
            ).sortedBy(DocumentConnectedModelReference::stableOrderingKey),
        )
        val model = DocumentConnectedModel(
            listOf(payment, approvalRecord, relationship, property, domain, range),
        )

        assertEquals(listOf(property.id, approvalRecord.id).sorted(), domain.referencedItemIds)
        assertEquals(6, model.items.size)
        assertFailsWith<IllegalArgumentException> {
            connectedItem(
                id = "item-7",
                order = 6,
                kind = DocumentConnectedModelItemKind.DomainAssignment,
                references = listOf(
                    DocumentConnectedModelReference(DocumentConnectedModelReferenceRole.Property, property.id),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DocumentConnectedModel(
                listOf(
                    payment,
                    connectedItem(
                        id = "item-2",
                        order = 1,
                        kind = DocumentConnectedModelItemKind.SubclassRelationship,
                        references = listOf(
                            DocumentConnectedModelReference(
                                DocumentConnectedModelReferenceRole.Subclass,
                                "item-3",
                            ),
                            DocumentConnectedModelReference(
                                DocumentConnectedModelReferenceRole.Superclass,
                                "item-1",
                            ),
                        ).sortedBy(DocumentConnectedModelReference::stableOrderingKey),
                    ),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            connectedItem(
                id = "item-2",
                order = 1,
                kind = DocumentConnectedModelItemKind.ComplexRule,
                references = listOf(
                    DocumentConnectedModelReference(DocumentConnectedModelReferenceRole.Related, payment.id),
                ),
            ).copy(reviewOnlyEligible = false)
        }
    }

    @Test
    fun `requires literal values only for datatype assertions`(): Unit {
        val payment = connectedItem(id = "item-1", order = 0)
        val property = connectedItem(
            id = "item-2",
            order = 1,
            kind = DocumentConnectedModelItemKind.DatatypeProperty,
        )
        val assertion = connectedItem(
            id = "item-3",
            order = 2,
            kind = DocumentConnectedModelItemKind.DatatypeValueAssertion,
            references = listOf(
                DocumentConnectedModelReference(DocumentConnectedModelReferenceRole.Subject, payment.id),
                DocumentConnectedModelReference(DocumentConnectedModelReferenceRole.Predicate, property.id),
            ).sortedBy(DocumentConnectedModelReference::stableOrderingKey),
            literalValue = RdfLiteral("1000"),
        )

        assertEquals("1000", assertion.literalValue?.lexicalForm)
        assertFailsWith<IllegalArgumentException> {
            connectedItem(
                id = "item-3",
                order = 2,
                kind = DocumentConnectedModelItemKind.DatatypeValueAssertion,
                references = assertion.references,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            payment.copy(literalValue = RdfLiteral("not allowed"))
        }
    }

    @Test
    fun `requires human decisions for document conflicts and supersession claims`(): Unit {
        val conflict = DocumentReconciliationRecord(
            id = "reconciliation-1",
            kind = DocumentReconciliationKind.Conflict,
            participantIds = listOf("discovery-1", "discovery-2"),
            evidenceIds = listOf(evidenceId),
            explanation = "The documents specify incompatible approval thresholds.",
            humanDecisionRequired = true,
        )

        assertTrue(conflict.humanDecisionRequired)
        assertFailsWith<IllegalArgumentException> {
            conflict.copy(humanDecisionRequired = false)
        }
    }

    @Test
    fun `validates temporary references dependencies and atomic edit bounds`(): Unit {
        val reference = DocumentTemporaryReference("new:class:Payment")
        DocumentTemporaryReferenceKind.entries.forEach { kind ->
            assertEquals(
                kind,
                DocumentTemporaryReference("new:${kind.token}:Valid1").kind,
            )
        }
        val operations = listOf(
            DocumentPlanOperation(
                id = "operation-1",
                kind = DocumentPlanOperationKind.CreateClass,
                order = 0,
                declaration = reference,
                expandedTypedEditCount = 1,
            ),
            DocumentPlanOperation(
                id = "operation-2",
                kind = DocumentPlanOperationKind.SetEntityLabel,
                order = 1,
                operands = listOf(
                    DocumentPlanOperand.TemporaryEntity(reference),
                    DocumentPlanOperand.TextValue("Payment"),
                ),
                dependsOnOperationIds = listOf("operation-1"),
                expandedTypedEditCount = 1,
            ),
        )

        val recommendation = recommendation(operations = operations)
        assertEquals(2, recommendation.expandedTypedEditCount)
        assertEquals(DocumentTemporaryReferenceKind.Class, reference.kind)
        assertFailsWith<IllegalArgumentException> {
            DocumentTemporaryReference("new:class:payment-item")
        }
        assertFailsWith<IllegalArgumentException> {
            recommendation(
                operations = listOf(
                    operations[1].copy(order = 0, dependsOnOperationIds = emptyList()),
                    operations[0].copy(order = 1),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            recommendation(
                operations = listOf(
                    operations[0].copy(expandedTypedEditCount = MAX_DOCUMENT_EXPANDED_TYPED_EDITS_PER_RECOMMENDATION),
                    operations[1],
                ),
            )
        }
    }

    @Test
    fun `enforces numeric and collection boundaries`(): Unit {
        assertFailsWith<IllegalArgumentException> {
            successfulProviderStage(
                Instant.parse("2026-07-27T12:00:00Z"),
                Instant.parse("2026-07-27T12:00:01Z"),
            ).copy(providerAttemptCount = MAX_DOCUMENT_PROVIDER_ATTEMPTS + 1)
        }
        assertFailsWith<IllegalArgumentException> {
            discovery().copy(evidenceConfidence = 101)
        }
        val excessiveDiscoveryIds = (0..MAX_DOCUMENT_DISCOVERIES_PER_TASK).map { index ->
            "discovery-${index.toString().padStart(4, '0')}"
        }
        assertFailsWith<IllegalArgumentException> {
            DocumentFinalPlan(
                workKey = DocumentAnalysisWorkKey("a".repeat(64)),
                verifiedDiscoveryIds = excessiveDiscoveryIds,
                criticFindingIds = emptyList(),
                recommendations = emptyList(),
                coverage = excessiveDiscoveryIds.map {
                    DocumentCoverageDisposition(it, DocumentCoverageDispositionKind.Unsupported)
                },
            )
        }
    }

    @Test
    fun `keeps unsupported complex rules review only`(): Unit {
        val finding = DocumentReviewOnlyFinding(
            id = "review-only-1",
            summary = "The policy describes a temporal exception.",
            reason = "The rule is outside the supported SHACL subset.",
            discoveryIds = listOf("discovery-1"),
            evidenceIds = listOf(evidenceId),
        )
        val recommendation = recommendation(
            operations = emptyList(),
            reviewOnlyFindings = listOf(finding),
            status = DocumentFinalRecommendationStatus.ReviewOnly,
        )

        assertEquals(DocumentFinalRecommendationStatus.ReviewOnly, recommendation.status)
        assertFailsWith<IllegalArgumentException> {
            recommendation.copy(
                status = DocumentFinalRecommendationStatus.Executable,
                operations = listOf(
                    DocumentPlanOperation(
                        id = "operation-1",
                        kind = DocumentPlanOperationKind.CreateClass,
                        order = 0,
                        declaration = DocumentTemporaryReference("new:class:TemporalException"),
                        expandedTypedEditCount = 1,
                    ),
                ),
            )
        }
    }

    @Test
    fun `blocks unresolved critic findings and unconfirmed individual creation`(): Unit {
        val individualReference = DocumentTemporaryReference("new:individual:Payment902771")
        val operation = DocumentPlanOperation(
            id = "operation-1",
            kind = DocumentPlanOperationKind.CreateIndividual,
            order = 0,
            declaration = individualReference,
            expandedTypedEditCount = 1,
        )
        val gate = DocumentIndividualReviewGate(
            operationId = operation.id,
            classification = DocumentIndividualClassification.Illustrative,
        )
        val unresolved = DocumentCriticDisposition(
            findingId = "finding-1",
            kind = DocumentCriticDispositionKind.Unresolved,
        )

        assertFalse(gate.executable)
        val blocked = recommendation(
            operations = listOf(operation),
            criticDispositions = listOf(unresolved),
            status = DocumentFinalRecommendationStatus.Blocked,
            blockers = listOf("individual-confirmation-required", "unresolved-critic-finding"),
            individualReviewGates = listOf(gate),
        )
        assertEquals(DocumentFinalRecommendationStatus.Blocked, blocked.status)
        assertTrue(gate.copy(creationConfirmed = true).executable)
        assertFailsWith<IllegalArgumentException> {
            blocked.copy(
                status = DocumentFinalRecommendationStatus.Executable,
                blockers = emptyList(),
            )
        }
    }

    @Test
    fun `requires exact discovery coverage and one disposition per critic finding`(): Unit {
        val recommendation = recommendation()
        val plan = DocumentFinalPlan(
            workKey = DocumentAnalysisWorkKey("a".repeat(64)),
            verifiedDiscoveryIds = listOf("discovery-1"),
            criticFindingIds = emptyList(),
            recommendations = listOf(recommendation),
            coverage = listOf(
                DocumentCoverageDisposition(
                    discoveryId = "discovery-1",
                    kind = DocumentCoverageDispositionKind.ExecutableRecommendation,
                    recommendationId = recommendation.id,
                ),
            ),
        )
        val noChangePlan = DocumentFinalPlan(
            workKey = DocumentAnalysisWorkKey("b".repeat(64)),
            verifiedDiscoveryIds = listOf("discovery-1"),
            criticFindingIds = emptyList(),
            recommendations = emptyList(),
            coverage = listOf(
                DocumentCoverageDisposition(
                    discoveryId = "discovery-1",
                    kind = DocumentCoverageDispositionKind.AdministrativeMetadata,
                ),
            ),
        )

        assertEquals("recommendation-1", plan.recommendations.single().id)
        assertTrue(noChangePlan.recommendations.isEmpty())
        assertFailsWith<IllegalArgumentException> {
            plan.copy(coverage = emptyList())
        }
    }

    @Test
    fun `uses the weakest confidence dimension as overall confidence`(): Unit {
        val confidence = DocumentConfidenceDimensions(
            evidence = 92,
            modeling = 81,
            ontologyFit = 87,
        )

        assertEquals(81, confidence.overall)
        assertFailsWith<IllegalArgumentException> {
            confidence.copy(overall = 90)
        }
    }

    @Test
    fun `pins Phase 11 point 5 plus contract versions`(): Unit {
        assertEquals(
            "phase-11-5-plus-semantic-plan-prompt-v1",
            DocumentAnalysisPipelineVersions.SEMANTIC_PLAN_PROMPT,
        )
        assertEquals(
            "phase-11-5-plus-semantic-plan-request-v1",
            DocumentAnalysisPipelineVersions.SEMANTIC_PLAN_REQUEST,
        )
        assertEquals(
            "phase-11-5-plus-semantic-plan-response-v1",
            DocumentAnalysisPipelineVersions.SEMANTIC_PLAN_RESPONSE,
        )
        assertEquals(
            "phase-11-5-plus-pattern-registry-v1",
            DocumentAnalysisPipelineVersions.SEMANTIC_PATTERN_REGISTRY,
        )
        assertEquals(
            "phase-11-5-plus-compiler-result-v1",
            DocumentAnalysisPipelineVersions.COMPILER_RESULT,
        )
        assertEquals(
            "phase-11-5-plus-document-review-v1",
            DocumentAnalysisPipelineVersions.DOCUMENT_REVIEW,
        )
    }

    @Test
    fun `accepts every approved semantic item kind with compatible references`(): Unit {
        val items = DocumentSemanticItemKind.entries.mapIndexed { index, kind ->
            val id = "semantic-${index.toString().padStart(2, '0')}"
            semanticItem(
                id = id,
                kind = kind,
                references = semanticReferences(kind),
                literalValue = RdfLiteral("25000").takeIf {
                    kind == DocumentSemanticItemKind.DatatypeValueAssertion
                },
                datatypeIntent = "decimal".takeIf {
                    kind in setOf(
                        DocumentSemanticItemKind.DatatypeProperty,
                        DocumentSemanticItemKind.DatatypePropertyRange,
                    )
                },
                outcome = if (kind == DocumentSemanticItemKind.ComplexRule) {
                    DocumentSemanticOutcome.ReviewOnly
                } else {
                    DocumentSemanticOutcome.Executable
                },
            )
        }.sortedBy(DocumentSemanticPlanItem::stableOrderingKey)

        assertEquals(DocumentSemanticItemKind.entries.size, items.size)
        assertTrue(items.all { it.discoveryIds == listOf("discovery-1") })
    }

    @Test
    fun `rejects invalid semantic references evidence and identities`(): Unit {
        val classItem = semanticItem(id = "semantic-class")
        val selfReference = DocumentSemanticReference(
            DocumentSemanticReferenceRole.Related,
            DocumentSemanticReferenceTarget.SemanticItem(classItem.id),
        )

        assertFailsWith<IllegalArgumentException> {
            classItem.copy(references = listOf(selfReference))
        }
        assertFailsWith<IllegalArgumentException> {
            semanticItem(
                kind = DocumentSemanticItemKind.SubclassRelationship,
                references = listOf(
                    alignmentReference(DocumentSemanticReferenceRole.Subclass),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            classItem.copy(evidenceIds = emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            DocumentSemanticPlan(
                workKey = DocumentAnalysisWorkKey("a".repeat(64)),
                verifiedDiscoveryIds = listOf("discovery-1"),
                criticFindingIds = emptyList(),
                items = listOf(
                    semanticItem(
                        id = "semantic-referencing",
                        kind = DocumentSemanticItemKind.ComplexRule,
                        references = listOf(
                            DocumentSemanticReference(
                                DocumentSemanticReferenceRole.Related,
                                DocumentSemanticReferenceTarget.SemanticItem("semantic-missing"),
                            ),
                        ),
                        outcome = DocumentSemanticOutcome.ReviewOnly,
                    ),
                ),
                groups = emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DocumentSemanticPlan(
                workKey = DocumentAnalysisWorkKey("b".repeat(64)),
                verifiedDiscoveryIds = listOf("discovery-1"),
                criticFindingIds = emptyList(),
                items = listOf(classItem, classItem),
                groups = emptyList(),
            )
        }
    }

    @Test
    fun `requires deterministic semantic plans and group references`(): Unit {
        val classItem = semanticItem(id = "semantic-class", label = "Payment")
        val propertyItem = semanticItem(
            id = "semantic-property",
            kind = DocumentSemanticItemKind.ObjectProperty,
            label = "has approval record",
        )
        val orderedItems = listOf(classItem, propertyItem).sortedBy(DocumentSemanticPlanItem::stableOrderingKey)
        val group = semanticGroup(itemIds = orderedItems.map(DocumentSemanticPlanItem::id).sorted())
        val plan = DocumentSemanticPlan(
            workKey = DocumentAnalysisWorkKey("c".repeat(64)),
            verifiedDiscoveryIds = listOf("discovery-1"),
            criticFindingIds = emptyList(),
            items = orderedItems,
            groups = listOf(group),
        )

        assertEquals(orderedItems, plan.items)
        assertFailsWith<IllegalArgumentException> {
            plan.copy(items = orderedItems.reversed())
        }
        assertFailsWith<IllegalArgumentException> {
            plan.copy(groups = listOf(group.copy(itemIds = listOf("semantic-missing"))))
        }
    }

    @Test
    fun `models matched existing and blocked discovery dispositions explicitly`(): Unit {
        val matched = DocumentCoverageDisposition(
            discoveryId = "discovery-1",
            kind = DocumentCoverageDispositionKind.MatchedExisting,
            alignmentId = "alignment-1",
        )
        val blocked = DocumentCoverageDisposition(
            discoveryId = "discovery-2",
            kind = DocumentCoverageDispositionKind.Blocked,
            rationale = "The required supporting concept is unresolved.",
        )

        assertEquals("alignment-1", matched.alignmentId)
        assertEquals("The required supporting concept is unresolved.", blocked.rationale)
        assertFailsWith<IllegalArgumentException> {
            matched.copy(alignmentId = null)
        }
        assertFailsWith<IllegalArgumentException> {
            blocked.copy(rationale = null)
        }
    }

    @Test
    fun `keeps semantic coverage separate from compilation success`(): Unit {
        val semanticCoverage = DocumentQualityMetric(9, 10, 90)
        val compilationSuccess = DocumentQualityMetric(
            numerator = 19,
            denominator = 20,
            percentage = 95,
            failureCodes = listOf("unsupported-mapping"),
        )
        val notApplicable = DocumentQualityMetric(0, 0, null)

        assertEquals(90, semanticCoverage.percentage)
        assertEquals(95, compilationSuccess.percentage)
        assertEquals(null, notApplicable.percentage)
        assertFailsWith<IllegalArgumentException> {
            semanticCoverage.copy(percentage = 91)
        }
    }

    @Test
    fun `uses optional compilation confidence for executable and review only outcomes`(): Unit {
        val compiledConfidence = DocumentCompiledConfidenceDimensions(
            evidence = 92,
            modeling = 85,
            ontologyFit = 88,
            compilation = 100,
        )
        val reviewOnlyConfidence = DocumentCompiledConfidenceDimensions(
            evidence = 92,
            modeling = 85,
            ontologyFit = 88,
            compilation = null,
        )
        val reviewOnly = DocumentCompiledRecommendationResult(
            groupId = "group-1",
            status = DocumentCompilationStatus.ReviewOnly,
            confidence = reviewOnlyConfidence,
        )

        assertEquals(85, compiledConfidence.overall)
        assertEquals(null, reviewOnly.confidence.compilation)
        assertFailsWith<IllegalArgumentException> {
            reviewOnly.copy(confidence = compiledConfidence)
        }
    }

    @Test
    fun `compiled results resolve references without putting final IRIs in semantic plans`(): Unit {
        val item = semanticItem(id = "semantic-class")
        val group = semanticGroup(itemIds = listOf(item.id))
        val plan = DocumentSemanticPlan(
            workKey = DocumentAnalysisWorkKey("d".repeat(64)),
            verifiedDiscoveryIds = listOf("discovery-1"),
            criticFindingIds = emptyList(),
            items = listOf(item),
            groups = listOf(group),
        )
        val compiled = DocumentCompiledRecommendationResult(
            groupId = group.id,
            status = DocumentCompilationStatus.Compiled,
            operations = listOf(
                DocumentPlanOperation(
                    id = "operation-1",
                    kind = DocumentPlanOperationKind.CreateClass,
                    order = 0,
                    declaration = DocumentTemporaryReference("new:class:Payment"),
                    expandedTypedEditCount = 1,
                ),
            ),
            references = listOf(
                DocumentCompiledReference(
                    semanticItemId = item.id,
                    temporaryReference = DocumentTemporaryReference("new:class:Payment"),
                    finalIri = Iri("https://example.com/Payment"),
                ),
            ),
            confidence = DocumentCompiledConfidenceDimensions(90, 85, 80, 100),
        )
        val result = DocumentCompiledPlanResult(
            workKey = plan.workKey,
            semanticPlan = DocumentVerifiedSemanticPlan(
                plan,
                listOf(
                    DocumentCoverageDisposition(
                        "discovery-1",
                        DocumentCoverageDispositionKind.ExecutableRecommendation,
                        recommendationId = group.id,
                    ),
                ),
            ),
            recommendations = listOf(compiled),
            semanticCoverage = DocumentQualityMetric(1, 1, 100),
            compilationSuccess = DocumentQualityMetric(1, 1, 100),
        )

        assertEquals("https://example.com/Payment", result.recommendations.single().references.single().finalIri.value)
        assertTrue(DocumentSemanticPlanItem::class.java.declaredFields.none { it.type == Iri::class.java })
        assertTrue(DocumentSemanticPlanItem::class.java.declaredFields.none { it.type == DocumentPlanOperation::class.java })
    }

    @Test
    fun `pipeline contracts stay independent from provider server and UI types`(): Unit {
        val forbiddenPrefixes = listOf(
            "io.ktor.",
            "com.fasterxml.",
            "com.entio.web.",
            "org.apache.pdfbox.",
            "org.apache.jena.",
            "java.nio.file.",
        )
        val contractTypes = listOf(
            DocumentAnalysisStageRecord::class,
            DocumentDiscovery::class,
            DocumentConnectedModel::class,
            DocumentReconciliationRecord::class,
            DocumentAlignmentRecord::class,
            DocumentCriticFinding::class,
            DocumentFinalPlan::class,
            DocumentSemanticPlanItem::class,
            DocumentSemanticRecommendationGroup::class,
            DocumentSemanticPlan::class,
        )

        contractTypes.flatMap { it.java.declaredFields.toList() }.forEach { field ->
            assertTrue(forbiddenPrefixes.none(field.type.name::startsWith), "${field.name} leaked ${field.type.name}")
        }
    }

    private fun semanticItem(
        id: String = "semantic-1",
        kind: DocumentSemanticItemKind = DocumentSemanticItemKind.Class,
        label: String = "Payment",
        references: List<DocumentSemanticReference> = emptyList(),
        literalValue: RdfLiteral? = null,
        datatypeIntent: String? = null,
        outcome: DocumentSemanticOutcome = DocumentSemanticOutcome.Executable,
    ): DocumentSemanticPlanItem = DocumentSemanticPlanItem(
        id = id,
        kind = kind,
        label = label,
        definition = "A payment described by the document.",
        literalValue = literalValue,
        datatypeIntent = datatypeIntent,
        references = references,
        discoveryIds = listOf("discovery-1"),
        evidenceIds = listOf(evidenceId),
        rationale = "The verified evidence supports this semantic treatment.",
        outcome = outcome,
        confidence = DocumentConfidenceDimensions(90, 85, 80),
    )

    private fun semanticGroup(
        id: String = "group-1",
        itemIds: List<String>,
    ): DocumentSemanticRecommendationGroup = DocumentSemanticRecommendationGroup(
        id = id,
        title = "Add payment model",
        description = "Create the connected payment structure.",
        itemIds = itemIds,
        discoveryIds = listOf("discovery-1"),
        evidenceIds = listOf(evidenceId),
        outcome = DocumentSemanticOutcome.Executable,
        rationale = "The items form one coherent recommendation.",
        confidence = DocumentConfidenceDimensions(90, 85, 80),
    )

    private fun semanticReferences(kind: DocumentSemanticItemKind): List<DocumentSemanticReference> {
        val roles = when (kind) {
            DocumentSemanticItemKind.SubclassRelationship ->
                listOf(DocumentSemanticReferenceRole.Subclass, DocumentSemanticReferenceRole.Superclass)
            DocumentSemanticItemKind.ObjectPropertyDomain,
            DocumentSemanticItemKind.DatatypePropertyDomain,
            -> listOf(DocumentSemanticReferenceRole.Property, DocumentSemanticReferenceRole.Domain)
            DocumentSemanticItemKind.ObjectPropertyRange,
            DocumentSemanticItemKind.DatatypePropertyRange,
            -> listOf(DocumentSemanticReferenceRole.Property, DocumentSemanticReferenceRole.Range)
            DocumentSemanticItemKind.IndividualType ->
                listOf(DocumentSemanticReferenceRole.Individual, DocumentSemanticReferenceRole.Type)
            DocumentSemanticItemKind.ObjectPropertyAssertion ->
                listOf(
                    DocumentSemanticReferenceRole.Subject,
                    DocumentSemanticReferenceRole.Predicate,
                    DocumentSemanticReferenceRole.Object,
                )
            DocumentSemanticItemKind.DatatypeValueAssertion ->
                listOf(DocumentSemanticReferenceRole.Subject, DocumentSemanticReferenceRole.Predicate)
            DocumentSemanticItemKind.PreferredLabel,
            DocumentSemanticItemKind.Definition,
            DocumentSemanticItemKind.AlternateLabel,
            -> listOf(DocumentSemanticReferenceRole.Entity)
            DocumentSemanticItemKind.NodeShape -> listOf(DocumentSemanticReferenceRole.TargetClass)
            DocumentSemanticItemKind.PropertyShape ->
                listOf(DocumentSemanticReferenceRole.Shape, DocumentSemanticReferenceRole.Path)
            DocumentSemanticItemKind.ShaclConstraint ->
                listOf(DocumentSemanticReferenceRole.ConstraintTarget)
            DocumentSemanticItemKind.ComplexRule -> listOf(DocumentSemanticReferenceRole.Related)
            DocumentSemanticItemKind.Class,
            DocumentSemanticItemKind.ObjectProperty,
            DocumentSemanticItemKind.DatatypeProperty,
            DocumentSemanticItemKind.AnnotationProperty,
            DocumentSemanticItemKind.Individual,
            -> emptyList()
        }
        return roles.map(::alignmentReference).sortedBy(DocumentSemanticReference::stableOrderingKey)
    }

    private fun alignmentReference(role: DocumentSemanticReferenceRole): DocumentSemanticReference =
        DocumentSemanticReference(
            role,
            DocumentSemanticReferenceTarget.Alignment("alignment-${role.name.lowercase()}"),
        )

    private fun discovery(
        id: String = "discovery-1",
        kind: DocumentDiscoveryKind = DocumentDiscoveryKind.Concept,
        contentClassification: DocumentContentClassification = DocumentContentClassification.BusinessContent,
        evidence: List<DocumentEvidence> = listOf(this.evidence),
        individualClassification: DocumentIndividualClassification? = null,
    ): DocumentDiscovery = DocumentDiscovery(
        id = id,
        documentId = documentId,
        kind = kind,
        contentClassification = contentClassification,
        assertionClassification = DocumentAssertionClassification.ExplicitFact,
        description = "The document identifies a payment.",
        evidence = evidence,
        evidenceConfidence = 90,
        individualClassification = individualClassification,
    )

    private fun connectedItem(
        id: String = "item-1",
        order: Int = 0,
        kind: DocumentConnectedModelItemKind = DocumentConnectedModelItemKind.Class,
        label: String = "Payment",
        references: List<DocumentConnectedModelReference> = emptyList(),
        literalValue: RdfLiteral? = null,
    ): DocumentConnectedModelItem = DocumentConnectedModelItem(
        id = id,
        kind = kind,
        label = label,
        rationale = "The discovery defines a payment concept.",
        discoveryIds = listOf("discovery-1"),
        references = references,
        literalValue = literalValue,
        order = order,
        reviewOnlyEligible = kind == DocumentConnectedModelItemKind.ComplexRule,
    )

    private fun recommendation(
        id: String = "recommendation-1",
        title: String = "Add payment concept",
        operations: List<DocumentPlanOperation> = listOf(
            DocumentPlanOperation(
                id = "operation-1",
                kind = DocumentPlanOperationKind.CreateClass,
                order = 0,
                declaration = DocumentTemporaryReference("new:class:Payment"),
                expandedTypedEditCount = 1,
            ),
        ),
        reviewOnlyFindings: List<DocumentReviewOnlyFinding> = emptyList(),
        criticDispositions: List<DocumentCriticDisposition> = emptyList(),
        status: DocumentFinalRecommendationStatus = DocumentFinalRecommendationStatus.Executable,
        blockers: List<String> = emptyList(),
        individualReviewGates: List<DocumentIndividualReviewGate> = emptyList(),
    ): DocumentFinalRecommendation = DocumentFinalRecommendation(
        id = id,
        title = title,
        description = "Create a payment class backed by the document.",
        discoveryIds = listOf("discovery-1"),
        evidenceIds = listOf(evidenceId),
        operations = operations,
        reviewOnlyFindings = reviewOnlyFindings,
        criticDispositions = criticDispositions,
        confidence = DocumentConfidenceDimensions(
            evidence = 90,
            modeling = 85,
            ontologyFit = 80,
        ),
        status = status,
        blockers = blockers,
        individualReviewGates = individualReviewGates,
    )

    private fun successfulProviderStage(
        startedAt: Instant,
        completedAt: Instant,
    ): DocumentAnalysisStageRecord = DocumentAnalysisStageRecord(
        recordId = "stage-provider",
        stage = DocumentAnalysisStage.Discovery,
        state = DocumentAnalysisStageState.Succeeded,
        scopeId = "document-1",
        startedAt = startedAt,
        finishedAt = completedAt,
        durationMillis = 1_000,
        selectedModelId = "gpt-model",
        promptVersion = DocumentAnalysisPipelineVersions.DISCOVERY_PROMPT,
        requestSchemaVersion = DocumentAnalysisPipelineVersions.DISCOVERY_REQUEST,
        responseSchemaVersion = DocumentAnalysisPipelineVersions.DISCOVERY_RESPONSE,
        inputSha256 = "a".repeat(64),
        outputSha256 = "b".repeat(64),
        providerAttemptCount = 1,
        completedCount = 1,
        totalCount = 1,
    )

    private fun successfulDeterministicStage(
        startedAt: Instant,
        completedAt: Instant,
    ): DocumentAnalysisStageRecord = DocumentAnalysisStageRecord(
        recordId = "stage-deterministic",
        stage = DocumentAnalysisStage.DeterministicVerification,
        state = DocumentAnalysisStageState.Succeeded,
        scopeId = "task-1",
        startedAt = startedAt,
        finishedAt = completedAt,
        durationMillis = 1_000,
        inputSha256 = "c".repeat(64),
        outputSha256 = "d".repeat(64),
        completedCount = 1,
        totalCount = 1,
    )
}
