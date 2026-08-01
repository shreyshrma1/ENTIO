package com.entio.web.ingestion

import com.entio.core.DocumentAuthorityMetadata
import com.entio.core.DocumentAuthorityStatus
import com.entio.core.DocumentAnalysisCounts
import com.entio.core.DocumentAnalysisPipelineVersions
import com.entio.core.DocumentAnalysisWorkKey
import com.entio.core.DocumentAssertionClassification
import com.entio.core.DocumentCandidateCategory
import com.entio.core.DocumentCandidateExtractionCategory
import com.entio.core.DocumentCandidateOrigin
import com.entio.core.DocumentConfidenceDimensions
import com.entio.core.DocumentContentClassification
import com.entio.core.DocumentCoverageDisposition
import com.entio.core.DocumentCoverageDispositionKind
import com.entio.core.DocumentDiscovery
import com.entio.core.DocumentDiscoveryKind
import com.entio.core.DocumentEvidence
import com.entio.core.DocumentEvidenceId
import com.entio.core.DocumentEvidenceReference
import com.entio.core.DocumentEvidenceType
import com.entio.core.DocumentExtractionMethod
import com.entio.core.DocumentFinalPlan
import com.entio.core.DocumentFinalRecommendation
import com.entio.core.DocumentFinalRecommendationStatus
import com.entio.core.DocumentGroundedAnalysisResult
import com.entio.core.DocumentGroundedCandidate
import com.entio.core.DocumentGroundedCoverageDisposition
import com.entio.core.DocumentGroundedDisposition
import com.entio.core.DocumentGroundedEvidenceSpan
import com.entio.core.DocumentGroundedRecommendationStatus
import com.entio.core.DocumentGroundedSemanticItem
import com.entio.core.DocumentGroupedDecisionKind
import com.entio.core.DocumentId
import com.entio.core.DocumentMatchCandidate
import com.entio.core.DocumentMatchScope
import com.entio.core.DocumentMediaType
import com.entio.core.DocumentProcessingStatus
import com.entio.core.DocumentPlanOperand
import com.entio.core.DocumentPlanOperation
import com.entio.core.DocumentPlanOperationKind
import com.entio.core.DocumentRecommendation
import com.entio.core.DocumentRecommendationAction
import com.entio.core.DocumentRecommendationCategory
import com.entio.core.DocumentOntologyRetrievalResult
import com.entio.core.DocumentSemanticItemKind
import com.entio.core.DocumentTaskId
import com.entio.core.DocumentTemporaryReference
import com.entio.core.DocumentTextBlockId
import com.entio.core.IngestionDocument
import com.entio.core.Iri
import com.entio.core.LocatedDocumentTextBlock
import com.entio.core.RdfLiteral
import com.entio.web.contract.WebPageRequest
import com.entio.semantic.DocumentDraftTranslationContext
import com.entio.semantic.DocumentChangeSetPlanVerifier
import com.entio.semantic.DocumentGroundedAnalysisVerifier
import com.entio.semantic.DocumentGroundedVerificationInput
import com.entio.semantic.DocumentPlanVerificationContext
import com.entio.semantic.DocumentSemanticCompilerContext
import com.entio.semantic.DocumentVerifiedFinalPlan
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentReviewWorkspaceTest {
    @Test
    fun resolvesUnresolvedGroundedClassThroughServerReverificationAndCompilation(): Unit {
        val store = unresolvedGroundedFixture()
        val before = store.readVerified("project-a", "task-grounded", "alice", WebPageRequest())
        val recommendation = before.recommendations.items.single()

        assertEquals("NeedsInput", recommendation.connectedStatus)
        assertEquals("NeedsInput", recommendation.groundedItems.single().status)
        assertEquals(
            setOf("Disposition", "EntityKind", "Label", "Selection"),
            recommendation.groundedItems.single().editableFields.map { it.kind }.toSet(),
        )
        assertEquals(1, before.draftImpact.needsInputCount)
        assertEquals(0, before.draftImpact.blockedCount)

        store.resolveVerifiedGroundedItem(
            projectId = "project-a",
            taskId = "task-grounded",
            recommendationId = recommendation.id,
            edit = DocumentReviewGroundedItemEdit(
                itemId = "grounded-item-policy",
                disposition = "ProposeNew",
                kind = "Class",
                label = "Servicing Policy",
                definition = "A policy governing loan servicing activities.",
            ),
            userId = "alice",
            expectedWorkKey = VERIFIED_WORK_KEY,
            expectedGraphFingerprint = "graph-fingerprint",
        )

        val after = store.readVerified("project-a", "task-grounded", "alice", WebPageRequest())
        val resolved = after.recommendations.items.single()
        assertEquals("Executable", resolved.connectedStatus)
        assertTrue(resolved.changePreview.draftable)
        assertTrue(resolved.changePreview.operations.any { it.operation == "Create Class" })
        assertTrue(resolved.changePreview.operations.any { it.operation == "Add Definition" })
        assertEquals(0, after.draftImpact.needsInputCount)
        assertEquals(1, after.draftImpact.executableCount)
        assertEquals(0, after.analysisCounts?.groundedItemsUnresolved)
    }

    @Test
    fun resolvesUnresolvedDatatypePropertyWithReviewerSelectedDomainAndDatatype(): Unit {
        val store = unresolvedGroundedFixture()
        val recommendation = store.readVerified("project-a", "task-grounded", "alice", WebPageRequest())
            .recommendations.items.single()

        store.resolveVerifiedGroundedItem(
            "project-a",
            "task-grounded",
            recommendation.id,
            DocumentReviewGroundedItemEdit(
                itemId = "grounded-item-policy",
                disposition = "ProposeNew",
                kind = "DatatypeProperty",
                label = "servicing policy code",
                definition = "A code identifying the applicable servicing policy.",
                domainSelectionId = "selection-loan",
                datatypeIri = "http://www.w3.org/2001/XMLSchema#string",
            ),
            "alice",
            VERIFIED_WORK_KEY,
            "graph-fingerprint",
        )

        val resolved = store.readVerified("project-a", "task-grounded", "alice", WebPageRequest())
            .recommendations.items.single()
        assertEquals("Mixed", resolved.connectedStatus)
        assertTrue(resolved.changePreview.draftable)
        assertEquals(
            setOf("Create Datatype Property", "Set Property Domain", "Set Property Range", "Add Definition"),
            resolved.changePreview.operations.map { it.operation }.toSet(),
        )
    }

    @Test
    fun presentsNonExactProviderReuseAsActionableNeedsInput(): Unit {
        val store = unresolvedGroundedFixture(DocumentGroundedDisposition.ReuseExisting)

        val workspace = store.readVerified("project-a", "task-grounded", "alice", WebPageRequest())
        val recommendation = workspace.recommendations.items.single()

        assertEquals("NeedsInput", recommendation.connectedStatus)
        assertEquals("NeedsInput", recommendation.type)
        assertEquals("NeedsInput", recommendation.groundedItems.single().status)
        assertFalse(recommendation.changePreview.draftable)
        assertTrue(recommendation.changePreview.blockingReason?.contains("grounded ontology decision") == true)
        assertEquals("Servicing Policy", recommendation.proposedLabel)
        assertEquals(0, workspace.draftImpact.matchedCount)
        assertEquals(1, workspace.draftImpact.needsInputCount)
        assertTrue(workspace.documentOnlyFindings.isEmpty())
    }

    @Test
    fun presentsExactProviderReuseAsMatchedAndConfirmableWithoutAnOntologyEdit(): Unit {
        val store = unresolvedGroundedFixture(
            disposition = DocumentGroundedDisposition.ReuseExisting,
            selectionPreferredLabel = "Servicing Policy",
            selectionIri = Iri("https://example.com/ontology/ServicingPolicy"),
        )

        val workspace = store.readVerified("project-a", "task-grounded", "alice", WebPageRequest())
        val recommendation = workspace.recommendations.items.single()

        assertEquals("Matched", recommendation.connectedStatus)
        assertEquals("ExistingOntologyReuse", recommendation.type)
        assertEquals("Matched", recommendation.groundedItems.single().status)
        assertFalse(recommendation.changePreview.draftable)
        assertEquals(null, recommendation.changePreview.blockingReason)
        assertTrue(recommendation.changePreview.summary.contains("Confirm that this evidence maps"))
        assertEquals(1, workspace.draftImpact.matchedCount)

        val confirmed = store.retainVerifiedReviewOnly(
            "project-a",
            "task-grounded",
            recommendation.id,
            "alice",
            workspace.exactWorkKey,
            workspace.graphFingerprint,
            null,
        )
        assertEquals(DocumentGroupedDecisionKind.Drafted, confirmed.decision.kind)
    }

    @Test
    fun movesAdministrativeMeaningToDocumentOnlyCoverageInsteadOfAReviewCard(): Unit {
        val store = unresolvedGroundedFixture(
            DocumentGroundedDisposition.Administrative,
            DocumentSemanticItemKind.Class,
            "Document section",
        )

        val workspace = store.readVerified("project-a", "task-grounded", "alice", WebPageRequest())

        assertEquals(0, workspace.recommendations.total)
        assertTrue(workspace.recommendations.items.isEmpty())
        assertEquals(0, workspace.draftImpact.pendingCount)
        assertEquals(0, workspace.draftImpact.reviewOnlyCount)
        assertEquals(1, workspace.draftImpact.documentOnlyCount)
        assertEquals("Document section", workspace.documentOnlyFindings.single().label)
        assertTrue(workspace.documentOnlyFindings.single().evidence.isNotEmpty())
    }

    @Test
    fun exposesAndSavesEditableModelRecommendedPrerequisiteLabels(): Unit {
        val store = verifiedFixture()
        val before = store.readVerified("project-a", "task-verified", "alice", WebPageRequest())
            .recommendations.items.single()
        val domain = before.changePreview.operations.single { it.semanticRole == "Domain class" }
        val range = before.changePreview.operations.single { it.semanticRole == "Range class" }
        val datatypeRange = before.changePreview.operations.single { it.editableIri != null }
        assertEquals("Payment system", domain.editableLabel)
        assertEquals("Review decision", range.editableLabel)
        assertFalse(domain.modelRecommended)
        assertTrue(domain.reviewerInputRequired)
        assertTrue(range.modelRecommended)
        assertEquals(listOf("reviewer-input-required"), before.mandatoryClarificationReasons)
        assertTrue(before.changePreview.draftable)
        assertCode("document-reviewer-input-acknowledgement-required") {
            store.acceptVerified(
                "project-a",
                "task-verified",
                "recommendation-property-context",
                "alice",
                VERIFIED_WORK_KEY,
                "graph-fingerprint",
            )
        }

        store.editVerifiedOperations(
            projectId = "project-a",
            taskId = "task-verified",
            recommendationId = "recommendation-property-context",
            edits = listOf(
                DocumentReviewOperationEdit(checkNotNull(domain.operationId), "Payment processing platform"),
                DocumentReviewOperationEdit(checkNotNull(range.operationId), "Authorization decision"),
                DocumentReviewOperationEdit(
                    operationId = checkNotNull(datatypeRange.operationId),
                    entityIri = "http://www.w3.org/2001/XMLSchema#token",
                ),
            ),
            userId = "alice",
            expectedWorkKey = VERIFIED_WORK_KEY,
            expectedGraphFingerprint = "graph-fingerprint",
        )

        val after = store.readVerified("project-a", "task-verified", "alice", WebPageRequest())
            .recommendations.items.single()
        assertEquals(
            setOf(
                "Payment processing platform",
                "Authorization decision",
                "creates review decision",
                "decision reference",
            ),
            after.changePreview.operations.mapNotNull(DocumentReviewProposedOperation::editableLabel).toSet(),
        )
        assertEquals("Pending", after.reviewStatus)
        assertTrue(after.mandatoryClarificationReasons.isEmpty())
        assertEquals(
            "http://www.w3.org/2001/XMLSchema#token",
            after.changePreview.operations.single { it.operationId == datatypeRange.operationId }.editableIri,
        )
        assertCode("document-operation-edit-invalid") {
            store.editVerifiedOperations(
                "project-a",
                "task-verified",
                "recommendation-property-context",
                listOf(DocumentReviewOperationEdit("set-domain", "Not a declaration")),
                "alice",
                VERIFIED_WORK_KEY,
                "graph-fingerprint",
            )
        }
    }

    @Test
    fun keepsReadsBoundedAuthorizedAndFreeOfFullDocumentText(): Unit {
        val store = fixture()

        val firstPage = store.read("project-a", "task-1", "alice", WebPageRequest(0, 1))
        assertEquals(1, firstPage.recommendations.items.size)
        assertEquals(2, firstPage.recommendations.total)
        assertEquals(1, firstPage.recommendations.nextOffset)
        assertTrue(firstPage.recommendations.items.single().changePreview.draftable)
        assertEquals("Add definition", firstPage.recommendations.items.single().changePreview.operations.single().operation)
        assertTrue(firstPage.draftImpact.readOnly)
        assertFailsWith<DocumentIngestionFailure> {
            store.read("project-a", "task-1", "bob", WebPageRequest())
        }
        assertFailsWith<DocumentIngestionFailure> {
            store.read("project-b", "task-1", "alice", WebPageRequest())
        }

        val json = ObjectMapper().findAndRegisterModules().writeValueAsString(firstPage)
        assertFalse(json.contains(FULL_TEXT_SUFFIX))
        assertFalse(json.contains("/private/server/path"))
        assertFalse(json.contains("provider-payload"))

        val evidence = store.evidence("project-a", "task-1", "alice", "evidence-1")
        assertEquals("Customer", evidence.text.substring(evidence.highlightStart, evidence.highlightEnd))
        assertEquals("Text", evidence.extractionMethod)
        assertFalse(evidence.pageImageAvailable)
    }

    @Test
    fun supportsReviewChoicesAndRejectsInvalidOrStaleTransitions(): Unit {
        val store = fixture()
        val base = request("accept")

        assertEquals(
            "Accepted",
            store.decide("project-a", "task-1", "recommendation-1", "alice", base)
                .recommendations.items.first { it.id == "recommendation-1" }.reviewStatus,
        )
        assertEquals(
            "Rejected",
            store.decide("project-a", "task-1", "recommendation-1", "alice", request("reject"))
                .recommendations.items.first { it.id == "recommendation-1" }.reviewStatus,
        )
        val edited = store.decide(
            "project-a",
            "task-1",
            "recommendation-1",
            "alice",
            request("edit").copy(proposedLabel = "Business customer", targetSourceId = "ontology"),
        ).recommendations.items.first { it.id == "recommendation-1" }
        assertEquals("Business customer", edited.proposedLabel)
        assertEquals("ontology", edited.targetSourceId)

        val rematched = store.decide(
            "project-a",
            "task-1",
            "recommendation-1",
            "alice",
            request("rematch").copy(selectedMatchIri = "https://example.com/Customer"),
        ).recommendations.items.first { it.id == "recommendation-1" }
        assertEquals("https://example.com/Customer", rematched.selectedMatchIri)

        assertEquals(
            "NeedsClarification",
            store.decide(
                "project-a",
                "task-1",
                "recommendation-1",
                "alice",
                request("clarify").copy(clarification = "Confirm the applicable customer population."),
            ).recommendations.items.first { it.id == "recommendation-1" }.reviewStatus,
        )
        repeat(3) {
            store.decide(
                "project-a",
                "task-1",
                "recommendation-1",
                "alice",
                request("reconsider").copy(clarification = "Recheck bounded evidence ${it + 1}."),
            )
        }
        assertCode("document-reconsideration-limit") {
            store.decide(
                "project-a",
                "task-1",
                "recommendation-1",
                "alice",
                request("reconsider").copy(clarification = "One more."),
            )
        }

        val merged = store.decide(
            "project-a",
            "task-1",
            "recommendation-1",
            "alice",
            request("merge").copy(mergedRecommendationIds = listOf("recommendation-2")),
        )
        assertEquals("Rejected", merged.recommendations.items.first { it.id == "recommendation-2" }.reviewStatus)
        assertCode("document-review-stale") {
            store.decide(
                "project-a",
                "task-1",
                "recommendation-1",
                "alice",
                base.copy(expectedGraphFingerprint = "changed"),
            )
        }
        assertCode("document-match-invalid") {
            store.decide(
                "project-a",
                "task-1",
                "recommendation-1",
                "alice",
                request("rematch").copy(selectedMatchIri = "https://example.com/Unknown"),
            )
        }
    }

    private fun fixture(): DocumentReviewWorkspaceStore {
        val now = Instant.parse("2026-07-24T12:00:00Z")
        val store = DocumentReviewWorkspaceStore(Clock.fixed(now, ZoneOffset.UTC))
        val documentId = DocumentId("document-1")
        val blockId = DocumentTextBlockId("block-1")
        val text = "Customer policy applies. $FULL_TEXT_SUFFIX"
        val document = IngestionDocument(
            id = documentId,
            taskId = DocumentTaskId("task-1"),
            safeFilename = "policy.txt",
            mediaType = DocumentMediaType.Text,
            byteSize = text.length.toLong(),
            checksumSha256 = "a".repeat(64),
            projectId = "project-a",
            uploaderUserId = "alice",
            uploadedAt = now,
            authority = DocumentAuthorityMetadata(DocumentAuthorityStatus.Authoritative),
            status = DocumentProcessingStatus.AwaitingReview,
        )
        val block = LocatedDocumentTextBlock(
            id = blockId,
            documentId = documentId,
            safeFilename = "policy.txt",
            blockOrder = 0,
            startOffset = 0,
            endOffset = text.length,
            exactText = text,
            extractionMethod = DocumentExtractionMethod.Text,
            extractorVersion = "test-extractor",
        )
        val evidenceReference = DocumentEvidenceReference(
            id = DocumentEvidenceId("evidence-1"),
            documentId = documentId,
            blockId = blockId,
            startOffsetInBlock = 0,
            endOffsetInBlock = 8,
            exactExcerpt = "Customer",
            extractionMethod = DocumentExtractionMethod.Text,
        )
        val evidence = DocumentEvidence(
            id = DocumentEvidenceId("evidence-group-1"),
            type = DocumentEvidenceType.Explicit,
            references = listOf(evidenceReference),
        )
        val match = DocumentMatchCandidate(
            scope = DocumentMatchScope.AppliedLocal,
            entityIri = Iri("https://example.com/Customer"),
            sourceId = "ontology",
            preferredLabel = "Customer",
            score = 100,
            reason = "Canonical local match.",
        )
        val recommendations = (1..2).map { index ->
            DocumentRecommendation(
                id = "recommendation-$index",
                candidateIds = listOf("candidate-$index"),
                type = DocumentCandidateCategory.Class,
                category = DocumentRecommendationCategory.OntologyStructure,
                proposedLabel = "Customer $index",
                proposedDefinition = RdfLiteral("A customer described by the reviewed policy."),
                action = DocumentRecommendationAction.Extend,
                confidence = 90,
                rationale = "The source explicitly describes a customer concept.",
                evidence = listOf(evidence),
                matches = listOf(match),
                selectedMatch = match,
                targetSourceId = "ontology",
            )
        }
        val task = DocumentIngestionTaskSnapshot(
            taskId = "task-1",
            projectId = "project-a",
            ownerUserId = "alice",
            status = "awaiting-review",
            createdAt = now.toString(),
            updatedAt = now.toString(),
            documents = listOf(
                DocumentIngestionDocumentSnapshot(
                    documentId = "document-1",
                    safeFilename = "policy.txt",
                    mediaType = "text",
                    byteSize = text.length.toLong(),
                    checksumSha256 = "a".repeat(64),
                    authorityStatus = "authoritative",
                    status = "awaiting-review",
                ),
            ),
            progress = DocumentIngestionProgress("awaiting-review", 1, 1, 100, "Recommendations are ready for review."),
        )
        store.install(
            DocumentReviewWorkspaceInput(
                task = task,
                exactWorkKey = "work-key",
                graphFingerprint = "graph-fingerprint",
                extractedDocuments = listOf(ExtractedDocument(document, listOf(block), emptyList(), emptyMap())),
                summaries = listOf(
                    VerifiedDocumentAnalysisSummary(
                        "document-1",
                        "Defines customer policy.",
                        listOf(VerifiedDocumentAnalysisHighlight("Customer", "candidate-1", listOf("evidence-1"))),
                    ),
                ),
                recommendations = recommendations,
                priorWorkflowProvenance = mapOf("recommendation-1" to listOf("applied-record-1")),
                draftContexts = recommendations.associate { recommendation ->
                    recommendation.id to DocumentDraftTranslationContext(
                        targetSourceId = "ontology",
                        targetIri = match.entityIri,
                    )
                },
            ),
        )
        return store
    }

    private fun verifiedFixture(): DocumentReviewWorkspaceStore {
        val now = Instant.parse("2026-07-24T12:00:00Z")
        val store = DocumentReviewWorkspaceStore(Clock.fixed(now, ZoneOffset.UTC))
        val documentId = DocumentId("document-verified")
        val blockId = DocumentTextBlockId("block-verified")
        val text = "MeridianPay creates a review decision."
        val document = IngestionDocument(
            id = documentId,
            taskId = DocumentTaskId("task-verified"),
            safeFilename = "payment-policy.txt",
            mediaType = DocumentMediaType.Text,
            byteSize = text.length.toLong(),
            checksumSha256 = "b".repeat(64),
            projectId = "project-a",
            uploaderUserId = "alice",
            uploadedAt = now,
            authority = DocumentAuthorityMetadata(DocumentAuthorityStatus.Authoritative),
            status = DocumentProcessingStatus.AwaitingReview,
        )
        val block = LocatedDocumentTextBlock(
            id = blockId,
            documentId = documentId,
            safeFilename = document.safeFilename,
            blockOrder = 0,
            startOffset = 0,
            endOffset = text.length,
            exactText = text,
            extractionMethod = DocumentExtractionMethod.Text,
            extractorVersion = "test-extractor",
        )
        val evidence = DocumentEvidence(
            id = DocumentEvidenceId("evidence-group-verified"),
            type = DocumentEvidenceType.Explicit,
            references = listOf(
                DocumentEvidenceReference(
                    id = DocumentEvidenceId("evidence-verified"),
                    documentId = documentId,
                    blockId = blockId,
                    startOffsetInBlock = 0,
                    endOffsetInBlock = text.length,
                    exactExcerpt = text,
                    extractionMethod = DocumentExtractionMethod.Text,
                ),
            ),
        )
        val discovery = DocumentDiscovery(
            id = "discovery-review-decision",
            documentId = documentId,
            kind = DocumentDiscoveryKind.Relationship,
            contentClassification = DocumentContentClassification.BusinessContent,
            assertionClassification = DocumentAssertionClassification.ExplicitFact,
            description = text,
            evidence = listOf(evidence),
            evidenceConfidence = 95,
        )
        val domain = DocumentTemporaryReference("new:class:PaymentSystem")
        val range = DocumentTemporaryReference("new:class:ReviewDecision")
        val property = DocumentTemporaryReference("new:objectProperty:CreatesReviewDecision")
        val datatypeProperty = DocumentTemporaryReference("new:datatypeProperty:DecisionReference")
        val source = DocumentPlanOperand.SourceId("ontology")
        val operations = listOf(
            DocumentPlanOperation(
                "create-domain",
                DocumentPlanOperationKind.CreateClass,
                0,
                domain,
                listOf(DocumentPlanOperand.TextValue("Payment system"), source),
                expandedTypedEditCount = 1,
                reviewerInputRequired = true,
            ),
            DocumentPlanOperation(
                "create-range",
                DocumentPlanOperationKind.CreateClass,
                1,
                range,
                listOf(DocumentPlanOperand.TextValue("Review decision"), source),
                expandedTypedEditCount = 1,
                modelRecommended = true,
            ),
            DocumentPlanOperation(
                "create-property",
                DocumentPlanOperationKind.CreateObjectProperty,
                2,
                property,
                listOf(DocumentPlanOperand.TextValue("creates review decision"), source),
                expandedTypedEditCount = 1,
            ),
            DocumentPlanOperation(
                "set-domain",
                DocumentPlanOperationKind.SetPropertyDomain,
                3,
                operands = listOf(
                    DocumentPlanOperand.TemporaryEntity(property),
                    DocumentPlanOperand.TemporaryEntity(domain),
                    source,
                ),
                dependsOnOperationIds = listOf("create-domain", "create-property"),
                expandedTypedEditCount = 1,
                reviewerInputRequired = true,
            ),
            DocumentPlanOperation(
                "set-range",
                DocumentPlanOperationKind.SetPropertyRange,
                4,
                operands = listOf(
                    DocumentPlanOperand.TemporaryEntity(property),
                    DocumentPlanOperand.TemporaryEntity(range),
                    source,
                ),
                dependsOnOperationIds = listOf("create-property", "create-range"),
                expandedTypedEditCount = 1,
                modelRecommended = true,
            ),
            DocumentPlanOperation(
                "create-datatype-property",
                DocumentPlanOperationKind.CreateDatatypeProperty,
                5,
                datatypeProperty,
                listOf(DocumentPlanOperand.TextValue("decision reference"), source),
                expandedTypedEditCount = 1,
            ),
            DocumentPlanOperation(
                "set-datatype-domain",
                DocumentPlanOperationKind.SetPropertyDomain,
                6,
                operands = listOf(
                    DocumentPlanOperand.TemporaryEntity(datatypeProperty),
                    DocumentPlanOperand.TemporaryEntity(domain),
                    source,
                ),
                dependsOnOperationIds = listOf("create-datatype-property", "create-domain"),
                expandedTypedEditCount = 1,
            ),
            DocumentPlanOperation(
                "set-datatype-range",
                DocumentPlanOperationKind.SetPropertyRange,
                7,
                operands = listOf(
                    DocumentPlanOperand.TemporaryEntity(datatypeProperty),
                    DocumentPlanOperand.ExistingEntity(Iri("http://www.w3.org/2001/XMLSchema#string")),
                    source,
                ),
                dependsOnOperationIds = listOf("create-datatype-property"),
                expandedTypedEditCount = 1,
                modelRecommended = true,
            ),
        )
        val recommendation = DocumentFinalRecommendation(
            id = "recommendation-property-context",
            title = "Create creates review decision",
            description = "Create the relationship with editable domain and range.",
            discoveryIds = listOf(discovery.id),
            evidenceIds = listOf(evidence.id),
            operations = operations,
            confidence = DocumentConfidenceDimensions(95, 85, 80),
            status = DocumentFinalRecommendationStatus.Executable,
        )
        val finalPlan = DocumentFinalPlan(
            workKey = DocumentAnalysisWorkKey(VERIFIED_WORK_KEY),
            verifiedDiscoveryIds = listOf(discovery.id),
            criticFindingIds = emptyList(),
            recommendations = listOf(recommendation),
            coverage = listOf(
                DocumentCoverageDisposition(
                    discoveryId = discovery.id,
                    kind = DocumentCoverageDispositionKind.ExecutableRecommendation,
                    recommendationId = recommendation.id,
                ),
            ),
        )
        val task = DocumentIngestionTaskSnapshot(
            taskId = "task-verified",
            projectId = "project-a",
            ownerUserId = "alice",
            status = "awaiting-review",
            createdAt = now.toString(),
            updatedAt = now.toString(),
            documents = listOf(
                DocumentIngestionDocumentSnapshot(
                    documentId.value,
                    document.safeFilename,
                    "text",
                    document.byteSize,
                    document.checksumSha256,
                    "authoritative",
                    "awaiting-review",
                ),
            ),
            progress = DocumentIngestionProgress("awaiting-review", 1, 1, 100, "Ready."),
        )
        store.installVerifiedPlan(
            task = task,
            workKey = VERIFIED_WORK_KEY,
            graphFingerprint = "graph-fingerprint",
            plan = DocumentVerifiedFinalPlan(
                plan = finalPlan,
                finalIris = mapOf(
                    domain to Iri("https://example.com/PaymentSystem"),
                    range to Iri("https://example.com/ReviewDecision"),
                    property to Iri("https://example.com/CreatesReviewDecision"),
                    datatypeProperty to Iri("https://example.com/DecisionReference"),
                ),
                previews = emptyList(),
            ),
            extractedDocuments = listOf(ExtractedDocument(document, listOf(block), emptyList(), emptyMap())),
            discoveries = listOf(discovery),
        )
        return store
    }

    private fun unresolvedGroundedFixture(
        disposition: DocumentGroundedDisposition = DocumentGroundedDisposition.Unresolved,
        kind: DocumentSemanticItemKind = DocumentSemanticItemKind.Class,
        itemLabel: String = "Servicing Policy",
        selectionPreferredLabel: String = "Loan",
        selectionIri: Iri = Iri("https://example.com/ontology/Loan"),
    ): DocumentReviewWorkspaceStore {
        val now = Instant.parse("2026-07-31T12:00:00Z")
        val store = DocumentReviewWorkspaceStore(Clock.fixed(now, ZoneOffset.UTC))
        val documentId = DocumentId("document-grounded")
        val blockId = DocumentTextBlockId("block-grounded")
        val text = "The Servicing Policy governs loan servicing activities."
        val document = IngestionDocument(
            id = documentId,
            taskId = DocumentTaskId("task-grounded"),
            safeFilename = "servicing-policy.txt",
            mediaType = DocumentMediaType.Text,
            byteSize = text.length.toLong(),
            checksumSha256 = "c".repeat(64),
            projectId = "project-a",
            uploaderUserId = "alice",
            uploadedAt = now,
            authority = DocumentAuthorityMetadata(DocumentAuthorityStatus.Authoritative),
            status = DocumentProcessingStatus.AwaitingReview,
        )
        val block = LocatedDocumentTextBlock(
            id = blockId,
            documentId = documentId,
            safeFilename = document.safeFilename,
            blockOrder = 0,
            startOffset = 0,
            endOffset = text.length,
            exactText = text,
            extractionMethod = DocumentExtractionMethod.Text,
            extractorVersion = "test-extractor",
        )
        val reference = DocumentEvidenceReference(
            id = DocumentEvidenceId("reference-policy"),
            documentId = documentId,
            blockId = blockId,
            startOffsetInBlock = 4,
            endOffsetInBlock = 20,
            exactExcerpt = "Servicing Policy",
            extractionMethod = DocumentExtractionMethod.Text,
        )
        val evidence = DocumentEvidence(
            id = DocumentEvidenceId("evidence-policy"),
            type = DocumentEvidenceType.Explicit,
            references = listOf(reference),
        )
        val candidate = DocumentGroundedCandidate(
            id = "candidate-policy",
            origin = DocumentCandidateOrigin.LocalNlp,
            category = DocumentCandidateExtractionCategory.ConceptTerm,
            displayText = "Servicing Policy",
            normalizedText = "servicing policy",
            documentId = documentId,
            documentChecksumSha256 = document.checksumSha256,
            evidenceSpans = listOf(
                DocumentGroundedEvidenceSpan(
                    evidence.id,
                    reference.id,
                    documentId,
                    blockId,
                    null,
                    null,
                    reference.startOffsetInBlock,
                    reference.endOffsetInBlock,
                    reference.exactExcerpt,
                ),
            ),
            extractorContractVersion = DocumentAnalysisPipelineVersions.CANDIDATE_EXTRACTION_CONTRACT,
            resourceVersion = DocumentAnalysisPipelineVersions.NLP_RESOURCE_SET,
        )
        val item = DocumentGroundedSemanticItem(
            id = "grounded-item-policy",
            kind = kind,
            label = itemLabel,
            definition = "A policy described by the document.",
            candidateIds = listOf(candidate.id),
            evidenceIds = listOf(evidence.id),
            disposition = disposition,
            selectionId = if (disposition == DocumentGroundedDisposition.ReuseExisting) "selection-loan" else null,
            rationale = "The evidence supports an important policy concept but needs a reviewer decision.",
            confidence = DocumentConfidenceDimensions(95, 80, 65),
            ambiguity = if (disposition == DocumentGroundedDisposition.Unresolved) {
                "Reuse or creation requires reviewer confirmation."
            } else {
                null
            },
        )
        val analysis = DocumentGroundedAnalysisResult(
            DocumentAnalysisPipelineVersions.GROUNDED_RESPONSE,
            listOf(item),
            listOf(
                DocumentGroundedCoverageDisposition(
                    candidate.id,
                    item.id,
                    disposition,
                    "The important concept needs a reviewer ontology decision.",
                ),
            ),
        )
        val retrieval = DocumentOntologyRetrievalResult(
            candidate.id,
            DocumentAnalysisPipelineVersions.RETRIEVAL_QUERY,
            DocumentAnalysisPipelineVersions.RETRIEVAL_RANKING,
            DocumentAnalysisPipelineVersions.RETRIEVAL_RESULT,
            listOf(
                com.entio.core.DocumentOntologyRetrievalSelection(
                    selectionId = "selection-loan",
                    candidateId = candidate.id,
                    canonicalIri = selectionIri,
                    kind = com.entio.core.SemanticDescriptorKind.Class,
                    scope = DocumentMatchScope.AppliedLocal,
                    sourceId = "ontology",
                    writable = true,
                    preferredLabel = selectionPreferredLabel,
                    score = 50,
                    matchReasons = listOf(
                        com.entio.core.DocumentRetrievalMatchReason("token-overlap", "Related lending context", 50),
                    ),
                    fingerprints = com.entio.core.DocumentRetrievalFingerprints(
                        "1".repeat(64),
                        "2".repeat(64),
                        "3".repeat(64),
                        "4".repeat(64),
                    ),
                ),
            ),
            true,
        )
        val compilerContext = DocumentSemanticCompilerContext(
            targetSourceId = "ontology",
            iriNamespace = "https://example.com/ontology",
            existingEntities = mapOf(
                selectionIri to com.entio.core.DocumentTemporaryReferenceKind.Class,
            ),
            alignedEntities = emptyMap(),
            expectedOntologyFingerprint = "graph-fingerprint",
            currentOntologyFingerprint = "graph-fingerprint",
            expectedCurrentWorkFingerprint = "current-work",
            currentWorkFingerprint = "current-work",
        )
        val verified = DocumentGroundedAnalysisVerifier().verify(
            DocumentGroundedVerificationInput(
                DocumentAnalysisWorkKey(VERIFIED_WORK_KEY),
                listOf(candidate),
                listOf(retrieval),
                emptyList(),
                analysis,
                "graph-fingerprint",
                "graph-fingerprint",
                "current-work",
                "current-work",
            ),
        )
        val planned = when (
            val result = SemanticCompilingDocumentFinalPlanningProvider(
                DocumentSemanticPlanningProvider { _, _, _, _ ->
                    DocumentSemanticPlanningProviderResult.Failed(false, "unused-provider")
                },
            ).compileGrounded(verified.plan, compilerContext)
        ) {
            is DocumentFinalPlanningProviderResult.Completed -> result.response.plan
            is DocumentFinalPlanningProviderResult.Failed -> error(result.safeCode)
        }
        val verificationContext = DocumentPlanVerificationContext(
            "graph-fingerprint",
            "graph-fingerprint",
            "current-work",
            "current-work",
            setOf("ontology"),
            compilerContext.existingEntities,
            compilerContext.iriNamespace,
        )
        val finalPlan = DocumentChangeSetPlanVerifier().verify(planned, verificationContext)
        val discovery = DocumentDiscovery(
            id = candidate.id,
            documentId = documentId,
            kind = DocumentDiscoveryKind.Concept,
            contentClassification = DocumentContentClassification.BusinessContent,
            assertionClassification = DocumentAssertionClassification.ExplicitFact,
            description = text,
            evidence = listOf(evidence),
            evidenceConfidence = 95,
        )
        store.installVerifiedPlan(
            task = DocumentIngestionTaskSnapshot(
                taskId = "task-grounded",
                projectId = "project-a",
                ownerUserId = "alice",
                status = "awaiting-review",
                createdAt = now.toString(),
                updatedAt = now.toString(),
                documents = listOf(
                    DocumentIngestionDocumentSnapshot(
                        documentId.value,
                        document.safeFilename,
                        "text",
                        document.byteSize,
                        document.checksumSha256,
                        "authoritative",
                        "awaiting-review",
                    ),
                ),
                progress = DocumentIngestionProgress("awaiting-review", 1, 1, 100, "Ready."),
            ),
            workKey = VERIFIED_WORK_KEY,
            graphFingerprint = "graph-fingerprint",
            plan = finalPlan,
            extractedDocuments = listOf(ExtractedDocument(document, listOf(block), emptyList(), emptyMap())),
            discoveries = listOf(discovery),
            groundedReview = DocumentGroundedReviewContext(
                candidates = listOf(candidate),
                analysis = analysis,
                retrieval = listOf(retrieval),
                fullStateMatches = emptyList(),
                compilerContext = compilerContext,
                verificationContext = verificationContext,
                nonRecommendationCoverage = emptyMap(),
                mentionCoverage = emptyList(),
                editableFields = verified.editableFields,
                statusByItemId = verified.statusByItemId,
                itemIdsByRecommendationId = verified.plan.groups.associate { it.id to it.itemIds },
                counts = DocumentAnalysisCounts(
                    evidenceBlocks = 1,
                    evidenceMentions = 1,
                    groupedCandidates = 1,
                    ontologyBearingCandidates = 1,
                    documentOnlyMentions = 0,
                    supportingValueMentions = 0,
                    nlpCandidatesRetained = 1,
                    nlpCandidatesRejected = 0,
                    groundedItemsRetained = 1,
                    groundedItemsUnresolved = if (disposition == DocumentGroundedDisposition.Unresolved) 1 else 0,
                    groundedItemsRejected = 0,
                    recommendationsExecutable = verified.statusByItemId.values.count {
                        it == DocumentGroundedRecommendationStatus.Executable
                    },
                    recommendationsMixed = 0,
                    recommendationsNeedsInput = verified.statusByItemId.values.count {
                        it == DocumentGroundedRecommendationStatus.NeedsInput
                    },
                    recommendationsReviewOnly = verified.statusByItemId.values.count {
                        it == DocumentGroundedRecommendationStatus.ReviewOnly
                    },
                    recommendationsBlocked = 0,
                    expandedTypedEdits = 0,
                ),
            ),
        )
        return store
    }

    private fun request(action: String): DocumentReviewDecisionRequest =
        DocumentReviewDecisionRequest(action, "work-key", "graph-fingerprint")

    private fun assertCode(code: String, block: () -> Unit): Unit {
        val failure = assertFailsWith<DocumentIngestionFailure> { block() }
        assertEquals(code, failure.code)
    }

    private companion object {
        const val VERIFIED_WORK_KEY: String =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val FULL_TEXT_SUFFIX: String =
            "This sentence represents content outside the bounded evidence response and must not leak."
    }
}
