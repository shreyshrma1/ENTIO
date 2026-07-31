package com.entio.web.ingestion

import com.entio.core.DocumentAuthorityMetadata
import com.entio.core.DocumentAuthorityStatus
import com.entio.core.DocumentAnalysisWorkKey
import com.entio.core.DocumentAssertionClassification
import com.entio.core.DocumentCandidateCategory
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
import com.entio.core.DocumentTaskId
import com.entio.core.DocumentTemporaryReference
import com.entio.core.DocumentTextBlockId
import com.entio.core.IngestionDocument
import com.entio.core.Iri
import com.entio.core.LocatedDocumentTextBlock
import com.entio.core.RdfLiteral
import com.entio.web.contract.WebPageRequest
import com.entio.semantic.DocumentDraftTranslationContext
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
