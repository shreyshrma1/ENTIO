package com.entio.web.ai

public data class AiTaskClassification(
    val type: AiTaskType,
    val size: AiTaskSize,
    val requiresPlanning: Boolean,
    val mutating: Boolean,
    val evidence: List<String>,
    val confidence: Double = 1.0,
)

public class AiTaskClassificationFailure(
    public val code: String,
    message: String,
) : IllegalArgumentException(message)

/**
 * Deterministic, permission-neutral semantic classification of a bounded user objective.
 *
 * This is deliberately a broad signal interpreter rather than a phrase allow-list. The model
 * remains responsible for deciding the concrete ontology operations; this classifier only gives
 * the server a proportional task family, size, and policy envelope before provider execution.
 */
public class AiTaskClassifier {
    public fun classify(objective: String): AiTaskClassification {
        val normalized = objective.trim().lowercase()
        if (normalized.isBlank()) {
            throw AiTaskClassificationFailure("missing-ai-task-objective", "An AI task objective is required.")
        }
        val tokens = tokenize(normalized)
        if (forbidden(tokens)) {
            throw AiTaskClassificationFailure(
                "forbidden-ai-task-objective",
                "The objective is outside the approved AI task boundary.",
            )
        }

        val scores = AiTaskType.entries.associateWith { type -> score(type, tokens) }
        val type = scores.maxWithOrNull(compareBy<Map.Entry<AiTaskType, Int>> { it.value }.thenBy { it.key.ordinal })
            ?.key
            ?.takeIf { scores.getValue(it) > 0 }
            ?: AiTaskType.EXPLANATION
        val size = classifySize(tokens, type, scores)
        val evidence = evidenceByType.getValue(type).filter { signal -> signal in tokens }.take(5).ifEmpty {
            listOf("semantic:${type.name.lowercase()}")
        }
        val totalSignal = scores.values.sum().coerceAtLeast(1)
        val confidence = (scores.getValue(type).toDouble() / totalSignal.toDouble()).coerceIn(0.0, 1.0)
        return AiTaskClassification(
            type = type,
            size = size,
            requiresPlanning = size != AiTaskSize.SIMPLE,
            mutating = type in mutatingTypes,
            evidence = evidence,
            confidence = confidence,
        )
    }

    private fun score(type: AiTaskType, tokens: Set<String>): Int = signalWeights.getValue(type)
        .filterKeys(tokens::contains)
        .values
        .sum()

    private fun classifySize(
        tokens: Set<String>,
        type: AiTaskType,
        scores: Map<AiTaskType, Int>,
    ): AiTaskSize = when {
        type in largeTypes || largeSignals.any(tokens::contains) -> AiTaskSize.LARGE
        type == AiTaskType.DOMAIN_MODELING ||
            scores.getValue(AiTaskType.MULTI_EDIT_CHANGE) >= 3 ||
            editFamilyCount(tokens) >= 3 -> AiTaskSize.MEDIUM
        mediumSignals.any(tokens::contains) || type in mediumTypes -> AiTaskSize.MEDIUM
        else -> AiTaskSize.SIMPLE
    }

    private fun editFamilyCount(tokens: Set<String>): Int = listOf(
        classSignals,
        propertySignals,
        relationshipSignals,
        individualSignals,
        constraintSignals,
        externalSignals,
    ).count { signals -> signals.any(tokens::contains) }

    private fun tokenize(value: String): Set<String> = Regex("[a-z0-9]+").findAll(value)
        .map { normalizeToken(it.value) }
        .filter { it.length > 1 }
        .toSet()

    private fun normalizeToken(token: String): String = when (token) {
        "classes" -> "class"
        "properties" -> "property"
        "objects" -> "object"
        "individuals" -> "individual"
        "relationships" -> "relationship"
        "assertions" -> "assertion"
        "constraints" -> "constraint"
        "definitions" -> "definition"
        "examples" -> "example"
        "accounts" -> "account"
        "creating", "created" -> "create"
        "adding", "added" -> "add"
        "defining", "defined" -> "define"
        "writing", "written" -> "write"
        "modeling", "modelling", "modeled", "modelled" -> "model"
        else -> token
    }

    private fun forbidden(tokens: Set<String>): Boolean = listOf(
        setOf("raw", "rdf"),
        setOf("write", "turtle"),
        setOf("bypass", "validation"),
        setOf("approve", "own"),
        setOf("submit", "reviewer"),
        setOf("create", "recursive", "task"),
        setOf("create", "another", "task"),
    ).any(tokens::containsAll)

    public companion object {
        public val mutatingTypes: Set<AiTaskType> = setOf(
            AiTaskType.FOCUSED_EDIT,
            AiTaskType.MULTI_EDIT_CHANGE,
            AiTaskType.REFACTORING,
            AiTaskType.DOMAIN_MODELING,
            AiTaskType.REPAIR,
        )

        private val largeTypes = setOf(AiTaskType.REFACTORING)
        private val mediumTypes = setOf(AiTaskType.MULTI_EDIT_CHANGE, AiTaskType.REPAIR, AiTaskType.PROJECT_ANALYSIS)
        private val largeSignals = setOf("entire", "whole", "source", "domain", "comprehensive", "complete")
        private val mediumSignals = setOf("all", "multiple", "several", "bulk", "every", "each", "project")
        private val classSignals = setOf("class", "concept", "hierarchy", "subclass", "superclass", "type")
        private val propertySignals = setOf("property", "relation", "attribute", "domain", "range", "field")
        private val relationshipSignals = setOf("assertion", "relationship", "connect", "relate", "link", "triple")
        private val individualSignals = setOf("individual", "object", "instance", "example", "record")
        private val constraintSignals = setOf("shacl", "constraint", "shape", "validation", "cardinality")
        private val externalSignals = setOf("fibo", "reuse", "external", "catalog", "import")
        private val evidenceByType: Map<AiTaskType, List<String>> = mapOf(
            AiTaskType.EXPLANATION to listOf("explain", "what", "how", "why", "describe"),
            AiTaskType.SEARCH_AND_DISCOVERY to listOf("find", "search", "discover", "locate", "explore"),
            AiTaskType.FOCUSED_EDIT to listOf("add", "create", "change", "rename", "define", "write"),
            AiTaskType.MULTI_EDIT_CHANGE to listOf("all", "multiple", "several", "bulk", "each", "every"),
            AiTaskType.REFACTORING to listOf("refactor", "reorganize", "restructure", "migrate"),
            AiTaskType.DOMAIN_MODELING to listOf("model", "design", "build", "domain", "ontology"),
            AiTaskType.REPAIR to listOf("repair", "fix", "resolve", "correct", "inconsistency"),
            AiTaskType.REVIEW to listOf("review", "assess", "inspect", "evaluate"),
            AiTaskType.PROJECT_ANALYSIS to listOf("analyze", "analyse", "overview", "summarize", "project"),
        )
        private val signalWeights: Map<AiTaskType, Map<String, Int>> = mapOf(
            AiTaskType.EXPLANATION to mapOf("explain" to 4, "what" to 2, "how" to 2, "why" to 2, "describe" to 3),
            AiTaskType.SEARCH_AND_DISCOVERY to mapOf("find" to 4, "search" to 4, "discover" to 3, "locate" to 3, "explore" to 2),
            AiTaskType.FOCUSED_EDIT to mapOf("add" to 3, "create" to 3, "change" to 2, "rename" to 3, "define" to 2, "write" to 2),
            AiTaskType.MULTI_EDIT_CHANGE to mapOf("all" to 4, "multiple" to 3, "several" to 3, "bulk" to 4, "each" to 3, "every" to 3),
            AiTaskType.REFACTORING to mapOf("refactor" to 5, "reorganize" to 4, "restructure" to 4, "migrate" to 3),
            AiTaskType.DOMAIN_MODELING to mapOf("model" to 5, "design" to 4, "build" to 3, "domain" to 4, "ontology" to 3),
            AiTaskType.REPAIR to mapOf("repair" to 5, "fix" to 4, "resolve" to 3, "correct" to 3, "inconsistency" to 4),
            AiTaskType.REVIEW to mapOf("review" to 4, "assess" to 3, "inspect" to 3, "evaluate" to 3),
            AiTaskType.PROJECT_ANALYSIS to mapOf("analyze" to 4, "analyse" to 4, "overview" to 3, "summarize" to 3, "project" to 2),
        )
    }
}
