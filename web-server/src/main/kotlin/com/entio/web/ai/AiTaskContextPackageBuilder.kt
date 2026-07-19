package com.entio.web.ai

public data class AiTaskContextEvidence(
    val neighborhoods: List<AiOntologyNeighborhood> = emptyList(),
    val fiboCandidates: List<AiRetrievalEntity> = emptyList(),
    val shaclFindings: List<String> = emptyList(),
    val stagingSummary: String? = null,
    val draftSummary: String? = null,
    val rules: List<String> = emptyList(),
)

public data class AiTaskContextPackage(
    val taskId: String,
    val taskRevision: Long,
    val projectMap: AiProjectMap,
    val entities: List<AiRetrievalEntity>,
    val fiboCandidates: List<AiRetrievalEntity>,
    val shaclFindings: List<String>,
    val assumptions: List<String>,
    val openQuestions: List<String>,
    val stagingSummary: String?,
    val draftSummary: String?,
    val rules: List<String>,
    val expanded: Boolean,
    val approximateBytes: Int,
)

public class AiTaskContextFailure(public val code: String, message: String) : IllegalArgumentException(message)

public class AiTaskContextPackageBuilder(
    private val maxBytes: Int = 64_000,
) {
    public fun build(
        workspace: AiTaskWorkspace,
        projectMap: AiProjectMap,
        evidence: AiTaskContextEvidence,
        expanded: Boolean = false,
    ): AiTaskContextPackage {
        requireCurrent(workspace, projectMap, evidence)
        if (expanded && workspace.task.policy.maxExpandedContextEntities <= workspace.task.policy.maxContextEntities) {
            throw AiTaskContextFailure("context-expansion-unavailable", "No larger context expansion is available.")
        }
        if (expanded && workspace.selectedEntities.size >= workspace.task.policy.maxExpandedContextEntities) {
            throw AiTaskContextFailure("context-expansion-already-used", "The bounded context is already fully expanded.")
        }
        val entityLimit = if (expanded) workspace.task.policy.maxExpandedContextEntities else workspace.task.policy.maxContextEntities
        val entities = evidence.neighborhoods.flatMap { listOf(it.target) + it.entities }
            .filter { it.sourceId in workspace.task.scope.allowedSourceIds }
            .distinctBy { listOf(it.sourceId, it.iri) }
            .sortedWith(compareBy(AiRetrievalEntity::sourceId, AiRetrievalEntity::label, AiRetrievalEntity::iri))
            .take(entityLimit)
        val result = AiTaskContextPackage(
            taskId = workspace.task.id,
            taskRevision = workspace.revision,
            projectMap = projectMap,
            entities = entities,
            fiboCandidates = evidence.fiboCandidates.distinctBy(AiRetrievalEntity::iri)
                .sortedBy(AiRetrievalEntity::iri).take(workspace.task.policy.maxFiboCandidatesPerSearch),
            shaclFindings = evidence.shaclFindings.distinct().sorted().take(workspace.task.policy.maxShaclFindingsInContext),
            assumptions = workspace.assumptions.map(AiTaskAssumption::statement).sorted(),
            openQuestions = workspace.openQuestions.map(AiTaskOpenQuestion::question).sorted(),
            stagingSummary = evidence.stagingSummary?.let(::untrusted),
            draftSummary = evidence.draftSummary?.let(::untrusted),
            rules = evidence.rules.distinct().sorted(),
            expanded = expanded,
            approximateBytes = 0,
        )
        val bytes = estimate(result)
        if (bytes > maxBytes) throw AiTaskContextFailure("task-context-byte-limit", "The bounded task context exceeds its byte limit.")
        return result.copy(approximateBytes = bytes)
    }

    private fun requireCurrent(
        workspace: AiTaskWorkspace,
        projectMap: AiProjectMap,
        evidence: AiTaskContextEvidence,
    ) {
        if (projectMap.projectFingerprint != workspace.currentProjectFingerprint) {
            throw AiTaskContextFailure("stale-task-context", "The project map no longer matches the task scope.")
        }
        if (projectMap.retrievalPolicyVersion != AI_RETRIEVAL_POLICY_VERSION) {
            throw AiTaskContextFailure("stale-retrieval-policy", "The project map uses a stale retrieval policy.")
        }
        if (evidence.neighborhoods.any { it.projectFingerprint != projectMap.projectFingerprint }) {
            throw AiTaskContextFailure("stale-task-context", "Neighborhood evidence no longer matches the project map.")
        }
    }

    private fun estimate(value: AiTaskContextPackage): Int = (
        value.entities.sumOf { it.iri.length + it.label.length + it.definitions.sumOf(String::length) } +
            value.fiboCandidates.sumOf { it.iri.length + it.label.length } +
            value.shaclFindings.sumOf(String::length) + value.assumptions.sumOf(String::length) +
            value.openQuestions.sumOf(String::length) + value.rules.sumOf(String::length) +
            value.stagingSummary.orEmpty().length + value.draftSummary.orEmpty().length + 512
    ) * 2

    private fun untrusted(value: String): String = "<untrusted-project-data>${value.take(4_000)}</untrusted-project-data>"
}
