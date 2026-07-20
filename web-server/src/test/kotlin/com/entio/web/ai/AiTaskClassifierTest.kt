package com.entio.web.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiTaskClassifierTest {
    private val classifier = AiTaskClassifier()

    @Test
    fun classifiesRepresentativeFamiliesAndSizesDeterministically(): Unit {
        val cases = listOf(
            "Explain Account" to (AiTaskType.EXPLANATION to AiTaskSize.SIMPLE),
            "Find concepts related to invoices" to (AiTaskType.SEARCH_AND_DISCOVERY to AiTaskSize.SIMPLE),
            "Add a definition to Account" to (AiTaskType.FOCUSED_EDIT to AiTaskSize.SIMPLE),
            "Design a class for a lending concept" to (AiTaskType.DOMAIN_MODELING to AiTaskSize.MEDIUM),
            "Add definitions to all classes" to (AiTaskType.MULTI_EDIT_CHANGE to AiTaskSize.MEDIUM),
            "Refactor the entire ontology" to (AiTaskType.REFACTORING to AiTaskSize.LARGE),
            "Model the domain for lending" to (AiTaskType.DOMAIN_MODELING to AiTaskSize.LARGE),
            "Repair the SHACL findings" to (AiTaskType.REPAIR to AiTaskSize.MEDIUM),
            "Review the current draft" to (AiTaskType.REVIEW to AiTaskSize.SIMPLE),
            "Analyze project structure" to (AiTaskType.PROJECT_ANALYSIS to AiTaskSize.MEDIUM),
        )

        cases.forEach { (objective, expected) ->
            val first = classifier.classify(objective)
            val second = classifier.classify(objective)
            assertEquals(expected.first, first.type, objective)
            assertEquals(expected.second, first.size, objective)
            assertEquals(first, second, objective)
        }
    }

    @Test
    fun planningMarkerIsProportionalAndDoesNotGrantAuthority(): Unit {
        val simple = classifier.classify("Add a definition to Account")
        val medium = classifier.classify("Add definitions to all classes")

        assertFalse(simple.requiresPlanning)
        assertTrue(simple.mutating)
        assertTrue(medium.requiresPlanning)
        assertTrue(medium.evidence.size <= 3)
    }

    @Test
    fun unfamiliarSemanticModelingObjectiveGetsAnEditEnvelopeWithoutACommandPhrase(): Unit {
        val classification = classifier.classify("Reason about a lending model with connected concepts and examples")

        assertEquals(AiTaskType.DOMAIN_MODELING, classification.type)
        assertEquals(AiTaskSize.MEDIUM, classification.size)
        assertTrue(classification.requiresPlanning)
        assertTrue(classification.mutating)
        assertTrue(classification.confidence > 0.0)
    }

    @Test
    fun forbiddenAndRecursiveObjectivesFailBeforeClassification(): Unit {
        listOf(
            "Write Turtle directly and bypass validation",
            "Approve my own draft",
            "Create another AI task to do this",
        ).forEach { objective ->
            val failure = assertFailsWith<AiTaskClassificationFailure> { classifier.classify(objective) }
            assertEquals("forbidden-ai-task-objective", failure.code)
        }
    }
}
