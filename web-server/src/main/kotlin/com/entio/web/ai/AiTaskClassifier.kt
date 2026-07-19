package com.entio.web.ai

public data class AiTaskClassification(
    val type: AiTaskType,
    val size: AiTaskSize,
    val requiresPlanning: Boolean,
    val mutating: Boolean,
    val evidence: List<String>,
)

public class AiTaskClassificationFailure(
    public val code: String,
    message: String,
) : IllegalArgumentException(message)

/** Deterministic, permission-neutral classification of a bounded user objective. */
public class AiTaskClassifier {
    public fun classify(objective: String): AiTaskClassification {
        val normalized = objective.trim().lowercase()
        if (normalized.isBlank()) {
            throw AiTaskClassificationFailure("missing-ai-task-objective", "An AI task objective is required.")
        }
        val forbidden = forbiddenEvidence.firstOrNull(normalized::contains)
        if (forbidden != null) {
            throw AiTaskClassificationFailure(
                "forbidden-ai-task-objective",
                "The objective is outside the approved AI task boundary.",
            )
        }

        val type = classifyType(normalized)
        val size = classifySize(normalized, type)
        val evidence = evidenceByType.getValue(type).filter(normalized::contains).take(3).ifEmpty {
            listOf("default:${type.name.lowercase()}")
        }
        return AiTaskClassification(
            type = type,
            size = size,
            requiresPlanning = size != AiTaskSize.SIMPLE,
            mutating = type in mutatingTypes,
            evidence = evidence,
        )
    }

    private fun classifyType(objective: String): AiTaskType = when {
        evidenceByType.getValue(AiTaskType.REPAIR).any(objective::contains) -> AiTaskType.REPAIR
        evidenceByType.getValue(AiTaskType.REFACTORING).any(objective::contains) -> AiTaskType.REFACTORING
        evidenceByType.getValue(AiTaskType.DOMAIN_MODELING).any(objective::contains) -> AiTaskType.DOMAIN_MODELING
        evidenceByType.getValue(AiTaskType.REVIEW).any(objective::contains) -> AiTaskType.REVIEW
        evidenceByType.getValue(AiTaskType.PROJECT_ANALYSIS).any(objective::contains) -> AiTaskType.PROJECT_ANALYSIS
        evidenceByType.getValue(AiTaskType.SEARCH_AND_DISCOVERY).any(objective::contains) ->
            AiTaskType.SEARCH_AND_DISCOVERY
        evidenceByType.getValue(AiTaskType.MULTI_EDIT_CHANGE).any(objective::contains) -> AiTaskType.MULTI_EDIT_CHANGE
        evidenceByType.getValue(AiTaskType.FOCUSED_EDIT).any(objective::contains) -> AiTaskType.FOCUSED_EDIT
        else -> AiTaskType.EXPLANATION
    }

    private fun classifySize(objective: String, type: AiTaskType): AiTaskSize = when {
        largeEvidence.any(objective::contains) || type in largeTypes -> AiTaskSize.LARGE
        mediumEvidence.any(objective::contains) || type in mediumTypes -> AiTaskSize.MEDIUM
        else -> AiTaskSize.SIMPLE
    }

    public companion object {
        public val mutatingTypes: Set<AiTaskType> = setOf(
            AiTaskType.FOCUSED_EDIT,
            AiTaskType.MULTI_EDIT_CHANGE,
            AiTaskType.REFACTORING,
            AiTaskType.DOMAIN_MODELING,
            AiTaskType.REPAIR,
        )

        private val largeTypes = setOf(AiTaskType.REFACTORING, AiTaskType.DOMAIN_MODELING)
        private val mediumTypes = setOf(AiTaskType.MULTI_EDIT_CHANGE, AiTaskType.REPAIR, AiTaskType.PROJECT_ANALYSIS)
        private val largeEvidence = listOf("entire ontology", "whole ontology", "all sources", "domain model")
        private val mediumEvidence = listOf("all classes", "multiple", "several", "project-wide", " and ")
        private val forbiddenEvidence = listOf(
            "write turtle",
            "raw rdf",
            "bypass validation",
            "approve my own",
            "submit as reviewer",
            "create another ai task",
            "create ai task",
            "recursive task",
        )
        private val evidenceByType: Map<AiTaskType, List<String>> = mapOf(
            AiTaskType.EXPLANATION to listOf("explain", "what is", "how does"),
            AiTaskType.SEARCH_AND_DISCOVERY to listOf("find", "search", "discover", "locate"),
            AiTaskType.FOCUSED_EDIT to listOf("add a definition", "add definition", "rename", "change label"),
            AiTaskType.MULTI_EDIT_CHANGE to listOf("all classes", "multiple", "several", "bulk"),
            AiTaskType.REFACTORING to listOf("refactor", "reorganize", "restructure"),
            AiTaskType.DOMAIN_MODELING to listOf("model the domain", "domain model", "design ontology"),
            AiTaskType.REPAIR to listOf("repair", "fix validation", "fix shacl", "resolve inconsistency"),
            AiTaskType.REVIEW to listOf("review", "assess draft", "inspect changes"),
            AiTaskType.PROJECT_ANALYSIS to listOf("analyze project", "analyse project", "project-wide analysis"),
        )
    }
}
