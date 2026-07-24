package com.entio.semantic

import com.entio.core.DocumentCandidateCategory
import com.entio.core.DocumentEvidence
import com.entio.core.DocumentEvidenceId
import com.entio.core.DocumentEvidenceReference
import com.entio.core.DocumentEvidenceType
import com.entio.core.DocumentExtractionMethod
import com.entio.core.DocumentId
import com.entio.core.DocumentMatchCandidate
import com.entio.core.DocumentMatchScope
import com.entio.core.DocumentRecommendation
import com.entio.core.DocumentRecommendationAction
import com.entio.core.DocumentRecommendationCategory
import com.entio.core.DocumentRecommendationReviewStatus
import com.entio.core.DocumentTextBlockId
import com.entio.core.EditableShaclConstraint
import com.entio.core.EditableShaclConstraintKind
import com.entio.core.EditableShaclConstraintValue
import com.entio.core.ExternalProposalIntent
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.core.SemanticEditRequest
import com.entio.core.TypedShaclEdit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DocumentRecommendationDraftTranslatorTest {
    private val translator = DocumentRecommendationDraftTranslator()

    @Test
    fun mapsEveryApprovedOntologyAndSemanticCategory(): Unit {
        val cases = listOf(
            DocumentCandidateCategory.Class to context(targetIri = CLASS),
            DocumentCandidateCategory.ObjectProperty to context(targetIri = PROPERTY),
            DocumentCandidateCategory.DatatypeProperty to context(targetIri = PROPERTY),
            DocumentCandidateCategory.Individual to context(targetIri = SUBJECT, relatedIri = CLASS),
            DocumentCandidateCategory.Label to context(targetIri = SUBJECT),
            DocumentCandidateCategory.AnnotationValue to context(targetIri = SUBJECT, objectTerm = literal("Client")),
            DocumentCandidateCategory.Definition to context(targetIri = SUBJECT, objectTerm = literal("A customer record.")),
            DocumentCandidateCategory.SuperclassRelationship to context(targetIri = CLASS, relatedIri = SUPERCLASS),
            DocumentCandidateCategory.Domain to context(propertyIri = PROPERTY, relatedIri = CLASS),
            DocumentCandidateCategory.Range to context(propertyIri = PROPERTY, relatedIri = RANGE),
            DocumentCandidateCategory.TypeAssertion to context(targetIri = SUBJECT, relatedIri = CLASS),
            DocumentCandidateCategory.ObjectPropertyAssertion to context(targetIri = SUBJECT, propertyIri = PROPERTY, objectTerm = OBJECT),
            DocumentCandidateCategory.DatatypeValue to context(targetIri = SUBJECT, propertyIri = PROPERTY, objectTerm = literal("42")),
        )

        cases.forEach { (category, context) ->
            val result = translator.translateSafely(recommendation(category), context)
            val prepared = assertIs<DocumentDraftTranslationResult.Prepared>(result)
            assertTrue(prepared.operations.isNotEmpty(), category.name)
            assertTrue(prepared.operations.all { !it.confirmOnly && !it.normalizedTypedOperationKey.isNullOrBlank() })
        }
    }

    @Test
    fun mapsExactRevisionsApprovedShaclAndFiboReuse(): Unit {
        val revision = recommendation(DocumentCandidateCategory.SuperclassRelationship, DocumentRecommendationAction.Revise)
        val revised = assertIs<DocumentDraftTranslationResult.Prepared>(
            translator.translateSafely(
                revision,
                context(
                    targetIri = CLASS,
                    relatedIri = SUPERCLASS,
                    existingRelatedIri = Iri("https://example.com/OldParent"),
                    clarificationResolved = true,
                ),
            ),
        )
        assertEquals(2, revised.operations.size)

        val alternateLabel = assertIs<DocumentDraftTranslationResult.Prepared>(
            translator.translateSafely(
                recommendation(DocumentCandidateCategory.AnnotationValue, DocumentRecommendationAction.Revise),
                context(
                    targetIri = SUBJECT,
                    objectTerm = literal("Buyer"),
                    existingLiteral = literal("Client"),
                    clarificationResolved = true,
                ),
            ),
        )
        assertIs<SemanticEditRequest.ReplaceAlternateLabel>(
            assertIs<DocumentDraftOperation.Semantic>(alternateLabel.operations.single().operation).edit,
        )

        val shacl = TypedShaclEdit.CreatePropertyShape(
            sourceId = "shapes",
            shapeIri = Iri("https://example.com/CustomerShape"),
            label = "Customer identifier",
            targetClassIri = CLASS,
            pathIri = PROPERTY,
            constraint = EditableShaclConstraint(
                EditableShaclConstraintKind.MinCount,
                EditableShaclConstraintValue.IntegerValue(1),
            ),
        )
        val shaclResult = assertIs<DocumentDraftTranslationResult.Prepared>(
            translator.translateSafely(
                recommendation(DocumentCandidateCategory.ShaclConstraint),
                context(targetSourceId = "shapes", shaclEdit = shacl),
            ),
        )
        assertIs<DocumentDraftOperation.Shacl>(shaclResult.operations.single().operation)

        val match = DocumentMatchCandidate(
            DocumentMatchScope.CuratedFibo,
            Iri("https://spec.example/FiboClass"),
            "fibo",
            "FIBO class",
            100,
            "Pinned curated match.",
        )
        val fibo = recommendation(
            DocumentCandidateCategory.Class,
            DocumentRecommendationAction.ReuseImportedOrFibo,
            matches = listOf(match),
            selected = match,
        )
        val fiboResult = assertIs<DocumentDraftTranslationResult.Prepared>(
            translator.translateSafely(
                fibo,
                context(
                    externalIntent = ExternalProposalIntent.ReuseExternalClass(match.entityIri, "fibo"),
                    targetOntologyIri = Iri("https://example.com/ontology"),
                ),
            ),
        )
        assertIs<DocumentDraftOperation.ExternalReuse>(fiboResult.operations.single().operation)
    }

    @Test
    fun preservesConfirmAsProvenanceOnlyAndBlocksUnsafeInputs(): Unit {
        val confirm = recommendation(DocumentCandidateCategory.Class, DocumentRecommendationAction.Confirm)
        val confirmed = assertIs<DocumentDraftTranslationResult.Prepared>(
            translator.translateSafely(confirm, context(targetSourceId = null)),
        )
        assertTrue(confirmed.operations.single().confirmOnly)

        val lowConfidence = recommendation(DocumentCandidateCategory.Class, confidence = 40)
        assertEquals(
            "document-low-confidence-unconfirmed",
            assertIs<DocumentDraftTranslationResult.Blocked>(
                translator.translateSafely(lowConfidence, context(targetIri = CLASS)),
            ).code,
        )
        val unsupported = recommendation(DocumentCandidateCategory.Class, DocumentRecommendationAction.Split)
        assertEquals(
            "document-recommendation-unsupported",
            assertIs<DocumentDraftTranslationResult.Blocked>(
                translator.translateSafely(unsupported, context(targetIri = CLASS, clarificationResolved = true)),
            ).code,
        )
        val stale = translator.translateSafely(
            recommendation(DocumentCandidateCategory.Class),
            context(targetIri = CLASS, graphCurrent = false),
        )
        assertEquals("document-draft-stale", assertIs<DocumentDraftTranslationResult.Blocked>(stale).code)
        val duplicate = translator.translateSafely(
            recommendation(DocumentCandidateCategory.Class),
            context(targetIri = CLASS).copy(duplicateOperation = true),
        )
        assertEquals("document-draft-duplicate", assertIs<DocumentDraftTranslationResult.Blocked>(duplicate).code)
        val changedModel = translator.translateSafely(
            recommendation(DocumentCandidateCategory.Class),
            context(targetIri = CLASS).copy(modelAndPromptCurrent = false),
        )
        assertEquals("document-draft-stale", assertIs<DocumentDraftTranslationResult.Blocked>(changedModel).code)
        val missingRevisionOperand = translator.translateSafely(
            recommendation(DocumentCandidateCategory.Range, DocumentRecommendationAction.Revise),
            context(propertyIri = PROPERTY, relatedIri = RANGE, clarificationResolved = true),
        )
        assertEquals(
            "document-draft-operand-missing",
            assertIs<DocumentDraftTranslationResult.Blocked>(missingRevisionOperand).code,
        )
    }

    private fun recommendation(
        type: DocumentCandidateCategory,
        action: DocumentRecommendationAction = DocumentRecommendationAction.Extend,
        confidence: Int = 90,
        matches: List<DocumentMatchCandidate> = emptyList(),
        selected: DocumentMatchCandidate? = null,
    ): DocumentRecommendation = DocumentRecommendation(
        id = "recommendation-${type.name.lowercase()}-${action.name.lowercase()}",
        candidateIds = listOf("candidate-${type.name.lowercase()}"),
        type = type,
        category = if (type in setOf(
                DocumentCandidateCategory.Individual,
                DocumentCandidateCategory.TypeAssertion,
                DocumentCandidateCategory.ObjectPropertyAssertion,
                DocumentCandidateCategory.DatatypeValue,
            )
        ) {
            DocumentRecommendationCategory.BusinessFact
        } else {
            DocumentRecommendationCategory.OntologyStructure
        },
        proposedLabel = "Customer",
        proposedDefinition = if (type == DocumentCandidateCategory.Definition) literal("A customer record.") else null,
        action = action,
        confidence = confidence,
        rationale = "Verified document evidence supports this operation.",
        evidence = listOf(evidence()),
        matches = matches,
        selectedMatch = selected,
        targetSourceId = if (action == DocumentRecommendationAction.Confirm) null else "ontology",
        reviewStatus = DocumentRecommendationReviewStatus.Pending,
        mandatoryClarificationReasons = if (confidence < 60 || action in setOf(
                DocumentRecommendationAction.Split,
                DocumentRecommendationAction.Revise,
            )
        ) {
            listOf("Explicit confirmation required.")
        } else {
            emptyList()
        },
    )

    private fun context(
        targetSourceId: String? = "ontology",
        targetIri: Iri? = null,
        relatedIri: Iri? = null,
        existingRelatedIri: Iri? = null,
        propertyIri: Iri? = null,
        objectTerm: com.entio.core.RdfTerm? = null,
        existingLiteral: RdfLiteral? = null,
        shaclEdit: TypedShaclEdit? = null,
        externalIntent: ExternalProposalIntent? = null,
        targetOntologyIri: Iri? = null,
        clarificationResolved: Boolean = false,
        graphCurrent: Boolean = true,
    ): DocumentDraftTranslationContext = DocumentDraftTranslationContext(
        targetSourceId,
        targetIri,
        relatedIri,
        existingRelatedIri,
        propertyIri,
        objectTerm,
        existingLiteral,
        shaclEdit = shaclEdit,
        externalIntent = externalIntent,
        targetOntologyIri = targetOntologyIri,
        acceptedForDraft = true,
        clarificationResolved = clarificationResolved,
        lowConfidenceOcrConfirmed = clarificationResolved,
        graphCurrent = graphCurrent,
    )

    private fun evidence(): DocumentEvidence = DocumentEvidence(
        DocumentEvidenceId("evidence-group"),
        DocumentEvidenceType.Explicit,
        listOf(
            DocumentEvidenceReference(
                DocumentEvidenceId("evidence-1"),
                DocumentId("document-1"),
                DocumentTextBlockId("block-1"),
                startOffsetInBlock = 0,
                endOffsetInBlock = 8,
                exactExcerpt = "Customer",
                extractionMethod = DocumentExtractionMethod.Text,
            ),
        ),
    )

    private fun literal(value: String): RdfLiteral =
        RdfLiteral(value, datatypeIri = Iri("http://www.w3.org/2001/XMLSchema#string"))

    private companion object {
        val CLASS = Iri("https://example.com/Customer")
        val SUPERCLASS = Iri("https://example.com/Party")
        val PROPERTY = Iri("https://example.com/customerId")
        val RANGE = Iri("http://www.w3.org/2001/XMLSchema#string")
        val SUBJECT = Iri("https://example.com/customer-1")
        val OBJECT = Iri("https://example.com/account-1")
    }
}
