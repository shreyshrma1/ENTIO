package com.entio.web.ingestion

import com.entio.core.DocumentCandidateCategory
import com.entio.core.DocumentEvidence
import com.entio.core.DocumentEvidenceId
import com.entio.core.DocumentEvidenceReference
import com.entio.core.DocumentEvidenceType
import com.entio.core.DocumentExtractionMethod
import com.entio.core.DocumentId
import com.entio.core.DocumentRecommendation
import com.entio.core.DocumentRecommendationAction
import com.entio.core.DocumentRecommendationCategory
import com.entio.core.DocumentTextBlockId
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.semantic.DocumentDraftTranslationContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentReviewChangeExplainerTest {
    private val explainer = DocumentReviewChangeExplainer()

    @Test
    fun showsTheExactTypedOperationThatApprovalWouldStage(): Unit {
        val explanation = explainer.explain(
            recommendation(
                type = DocumentCandidateCategory.DatatypeProperty,
                action = DocumentRecommendationAction.CreateLocal,
                label = "account closure date",
            ).copy(
                proposedDefinition = RdfLiteral("Account closure means the date the account is closed after all adjustments."),
                proposedDomainIri = Iri("https://example.com/entio/simple#Account"),
                proposedRangeIri = Iri("http://www.w3.org/2001/XMLSchema#date"),
            ),
            DocumentDraftTranslationContext(
                targetSourceId = "simple",
                targetIri = Iri("https://example.com/entio/simple#accountClosureDate"),
                domainIri = Iri("https://example.com/entio/simple#Account"),
                rangeIri = Iri("http://www.w3.org/2001/XMLSchema#date"),
            ),
        )

        assertTrue(explanation.changePreview.draftable)
        assertEquals(4, explanation.changePreview.operations.size)
        assertEquals("Create data field", explanation.changePreview.operations[0].operation)
        assertEquals(
            "Create https://example.com/entio/simple#accountClosureDate with label “account closure date”.",
            explanation.changePreview.operations[0].description,
        )
        assertEquals("Set relationship source", explanation.changePreview.operations[1].operation)
        assertEquals("Set relationship target", explanation.changePreview.operations[2].operation)
        assertEquals("Add definition", explanation.changePreview.operations[3].operation)
        assertTrue(explanation.changePreview.operations.all { it.targetSourceId == "simple" })
    }

    @Test
    fun explainsANewConceptAndItsConnectionAsOneCompoundChange(): Unit {
        val account = Iri("https://example.com/entio/simple#Account")
        val accountClosure = Iri("https://example.com/entio/simple#AccountClosure")
        val hasAccountClosure = Iri("https://example.com/entio/simple#hasAccountClosure")
        val explanation = explainer.explain(
            recommendation(
                type = DocumentCandidateCategory.Class,
                action = DocumentRecommendationAction.CreateLocal,
                label = "Account closure",
            ).copy(
                proposedDefinition = RdfLiteral("Account closure means the date the account is closed after all adjustments."),
                proposedConnectionLabel = "has account closure",
                proposedConnectionDomainIri = account,
            ),
            DocumentDraftTranslationContext(
                targetSourceId = "simple",
                targetIri = accountClosure,
                connectionPropertyIri = hasAccountClosure,
                connectionDomainIri = account,
            ),
        )

        assertTrue(explanation.changePreview.draftable)
        assertEquals(5, explanation.changePreview.operations.size)
        assertEquals(
            listOf(
                "Create class",
                "Add definition",
                "Create relationship",
                "Set relationship source",
                "Set relationship target",
            ),
            explanation.changePreview.operations.map { it.operation },
        )
        assertTrue(explanation.changePreview.operations.last().description.contains(accountClosure.value))
    }

    @Test
    fun makesReviewOnlyAmbiguityExplicitAndNonDraftable(): Unit {
        val explanation = explainer.explain(
            recommendation(
                type = DocumentCandidateCategory.Ambiguity,
                action = DocumentRecommendationAction.Extend,
                label = "Account closure definition",
            ),
            DocumentDraftTranslationContext(
                targetSourceId = "simple",
                targetIri = Iri("urn:entio:document-candidate:other"),
            ),
        )

        assertFalse(explanation.changePreview.draftable)
        assertEquals("No ontology change can be created from this recommendation.", explanation.changePreview.summary)
        assertEquals("This recommendation remains review-only.", explanation.changePreview.blockingReason)
        assertTrue(explanation.description.contains("cannot safely map"))
    }

    private fun recommendation(
        type: DocumentCandidateCategory,
        action: DocumentRecommendationAction,
        label: String,
    ): DocumentRecommendation = DocumentRecommendation(
        id = "recommendation-${type.name.lowercase()}",
        candidateIds = listOf("candidate-1"),
        type = type,
        category = DocumentRecommendationCategory.OntologyStructure,
        proposedLabel = label,
        action = action,
        confidence = 80,
        rationale = "Verified document finding.",
        evidence = listOf(
            DocumentEvidence(
                id = DocumentEvidenceId("evidence-group-1"),
                type = DocumentEvidenceType.Explicit,
                references = listOf(
                    DocumentEvidenceReference(
                        id = DocumentEvidenceId("evidence-1"),
                        documentId = DocumentId("document-1"),
                        blockId = DocumentTextBlockId("block-1"),
                        startOffsetInBlock = 0,
                        endOffsetInBlock = 7,
                        exactExcerpt = "Account",
                        extractionMethod = DocumentExtractionMethod.Text,
                    ),
                ),
            ),
        ),
        targetSourceId = "simple",
    )
}
