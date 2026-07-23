package com.entio.web.ai

import com.entio.core.EntioResult
import com.entio.core.FactOrigin
import com.entio.core.GraphState
import com.entio.core.RdfLiteral
import com.entio.semantic.ReasoningService
import java.util.Locale

internal data class AiReasoningContext(
    val text: String,
    val hasDraftDelta: Boolean,
)

/**
 * Formats bounded, read-only Entio reasoning results as trusted AI context.
 *
 * The model interprets these facts; this adapter does not classify user intent
 * or turn inferred facts into asserted edits.
 */
internal class AiReasoningContextBuilder(
    private val reasoningService: ReasoningService = ReasoningService(),
    private val maxFacts: Int = 120,
) {
    fun build(appliedGraph: GraphState, draftGraph: GraphState?, request: String): AiReasoningContext {
        val applied = reasoningService.reason(appliedGraph)
        val draft = draftGraph?.takeIf { it != appliedGraph }?.let(reasoningService::reason)
        val appliedResult = (applied as? EntioResult.Success)?.value
        val draftResult = (draft as? EntioResult.Success)?.value
        val appliedFacts = appliedResult?.inferredFacts().orEmpty()
        val draftFacts = draftResult?.inferredFacts().orEmpty()
        val added = draftFacts - appliedFacts
        val removed = appliedFacts - draftFacts
        val labels = labels(draftGraph ?: appliedGraph)
        val requestTokens = tokens(request)

        fun relevant(facts: Set<ReasonedFact>): List<ReasonedFact> = facts
            .sortedWith(
                compareByDescending<ReasonedFact> { fact ->
                    tokens(listOf(fact.subject, fact.predicate, fact.objectValue).joinToString(" ") { labels[it] ?: it })
                        .count { it in requestTokens }
                }.thenBy { it.kind }.thenBy { it.subject }.thenBy { it.predicate }.thenBy { it.objectValue },
            )
            .take(maxFacts)

        val text = buildString {
            appendLine("TRUSTED ENTIO REASONING CONTEXT")
            appendLine("These conclusions were computed by Entio's Kotlin reasoning service. Treat them as ontology evidence; do not claim you derived them yourself.")
            appendLine("Reasoning is read-only. Inferred facts below are not asserted source triples and must not be silently materialized.")
            appendResult("Applied graph", appliedResult, applied)
            appendFacts("Applied inferred facts", relevant(appliedFacts), labels)
            if (draftGraph != null && draftGraph != appliedGraph) {
                appendResult("Effective private-draft graph", draftResult, draft)
                appendFacts("New inferred consequences introduced by the private draft", relevant(added), labels)
                appendFacts("Applied inferences removed by the private draft", relevant(removed), labels)
                if (added.isEmpty() && removed.isEmpty()) appendLine("Reasoning delta: no inferred facts changed.")
            }
        }.trim()
        return AiReasoningContext(text, added.isNotEmpty() || removed.isNotEmpty())
    }

    private fun StringBuilder.appendResult(
        scope: String,
        result: com.entio.core.ReasoningResult?,
        raw: EntioResult<com.entio.core.ReasoningResult>?,
    ) {
        if (result == null) {
            val message = (raw as? EntioResult.Failure)?.message ?: "reasoning was unavailable"
            appendLine("$scope reasoning: unavailable ($message). Do not invent missing conclusions.")
            return
        }
        appendLine(
            "$scope reasoning: status=${result.metadata.status}; consistency=${result.consistency}; " +
                "graphFingerprint=${result.metadata.fingerprints.graphFingerprint}; importClosureComplete=${result.metadata.importClosureComplete}.",
        )
        result.warnings.take(8).forEach { appendLine("- reasoning warning: $it") }
    }

    private fun StringBuilder.appendFacts(
        heading: String,
        facts: List<ReasonedFact>,
        labels: Map<String, String>,
    ) {
        appendLine("$heading (${facts.size} shown):")
        if (facts.isEmpty()) {
            appendLine("- none")
            return
        }
        facts.forEach { fact ->
            appendLine(
                "- ${fact.kind}: ${labels[fact.subject] ?: fact.subject} <${fact.subject}> — " +
                    "${labels[fact.predicate] ?: fact.predicate} <${fact.predicate}> — " +
                    "${labels[fact.objectValue] ?: fact.objectValue} <${fact.objectValue}>",
            )
        }
    }

    private fun com.entio.core.ReasoningResult.inferredFacts(): Set<ReasonedFact> = buildSet {
        classRelationships.filter { it.origin == FactOrigin.Inferred }.forEach {
            add(ReasonedFact("inferred subclass", it.subject.value, RDFS_SUBCLASS_OF, it.objectClass.value))
        }
        individualTypes.filter { it.origin == FactOrigin.Inferred }.forEach {
            add(ReasonedFact("inferred individual type", it.individual.value, RDF_TYPE, it.type.value))
        }
        propertyRelationships.filter { it.origin == FactOrigin.Inferred }.forEach {
            add(ReasonedFact("inferred object relationship", it.subject.value, it.predicate.value, it.objectResource.value))
        }
    }

    private fun labels(graph: GraphState): Map<String, String> = graph.triples
        .asSequence()
        .filter { it.predicate.value in LABEL_PREDICATES }
        .mapNotNull { triple -> (triple.objectTerm as? RdfLiteral)?.lexicalForm?.let { triple.subjectResource.value to it } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, values) -> values.first() }

    private fun tokens(value: String): Set<String> = TOKEN_PATTERN.findAll(value.lowercase(Locale.ROOT))
        .map { it.value }
        .filter { it.length > 1 }
        .toSet()

    private data class ReasonedFact(
        val kind: String,
        val subject: String,
        val predicate: String,
        val objectValue: String,
    )

    private companion object {
        private const val RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
        private const val RDFS_SUBCLASS_OF = "http://www.w3.org/2000/01/rdf-schema#subClassOf"
        private val LABEL_PREDICATES = setOf(
            "http://www.w3.org/2000/01/rdf-schema#label",
            "http://www.w3.org/2004/02/skos/core#prefLabel",
        )
        private val TOKEN_PATTERN = Regex("[a-z0-9]+")
    }
}
