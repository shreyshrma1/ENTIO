package com.entio.web.ai

public enum class AiCapabilityBundleId {
    EXPLORATION,
    PLANNING,
    ONTOLOGY_EDITING,
    SHACL,
    ANALYSIS,
    REPAIR,
    HELP,
}

public data class AiCapabilityBundle(
    val id: AiCapabilityBundleId,
    val version: String,
    val capabilityNames: Set<String>,
)

public data class AiFrozenCapabilityBundle(
    val bundle: AiCapabilityBundle,
    val registry: AiCapabilityRegistry,
    val snapshot: AiCapabilityRegistrySnapshot,
)

/** Selects one server-owned capability bundle for a provider step. */
public class AiCapabilityBundleRegistry(
    definitions: List<AiCapabilityDefinition> = defaultAiCapabilityDefinitions(),
) {
    private val definitionsByName = definitions.associateBy(AiCapabilityDefinition::name)
    private val bundles: Map<AiCapabilityBundleId, AiCapabilityBundle> = bundleNames.mapValues { (id, names) ->
        AiCapabilityBundle(id, VERSION, names)
    }

    init {
        require(definitionsByName.size == definitions.size)
        require(bundles.values.flatMap { it.capabilityNames }.all(definitionsByName::containsKey))
        require(bundles.values.none { bundle -> bundle.capabilityNames.any(::forbiddenName) })
    }

    public fun bundleFor(task: AiTask, helpRequested: Boolean = false): AiCapabilityBundle = when {
        helpRequested -> bundles.getValue(AiCapabilityBundleId.HELP)
        task.status in setOf(AiTaskStatus.UNDERSTANDING, AiTaskStatus.AWAITING_CLARIFICATION) ->
            bundles.getValue(AiCapabilityBundleId.EXPLORATION)
        task.status in setOf(AiTaskStatus.PLANNING, AiTaskStatus.AWAITING_PLAN_CONFIRMATION) ->
            bundles.getValue(AiCapabilityBundleId.PLANNING)
        task.status in setOf(AiTaskStatus.VALIDATING, AiTaskStatus.RUNNING_REASONING, AiTaskStatus.RUNNING_SHACL) ->
            bundles.getValue(AiCapabilityBundleId.ANALYSIS)
        task.status == AiTaskStatus.REPAIRING -> bundles.getValue(AiCapabilityBundleId.REPAIR)
        task.type == AiTaskType.REPAIR -> bundles.getValue(AiCapabilityBundleId.REPAIR)
        task.type == AiTaskType.REVIEW || task.type == AiTaskType.PROJECT_ANALYSIS ->
            bundles.getValue(AiCapabilityBundleId.ANALYSIS)
        task.objective.contains("shacl", ignoreCase = true) -> bundles.getValue(AiCapabilityBundleId.SHACL)
        task.type in AiTaskClassifier.mutatingTypes -> bundles.getValue(AiCapabilityBundleId.ONTOLOGY_EDITING)
        else -> bundles.getValue(AiCapabilityBundleId.EXPLORATION)
    }

    public fun freeze(task: AiTask, scope: AiCapabilityScope, helpRequested: Boolean = false): AiFrozenCapabilityBundle {
        val bundle = bundleFor(task, helpRequested)
        val registry = AiCapabilityRegistry(bundle.capabilityNames.map(definitionsByName::getValue))
        return AiFrozenCapabilityBundle(bundle, registry, registry.snapshot(scope))
    }

    public companion object {
        public const val VERSION: String = "phase-8-capability-bundles-v1"

        private val read = setOf(
            "entio_project_summary", "entio_entity_detail", "entio_compare_entities",
            "entio_search_local_entities", "entio_hierarchy_neighborhood", "entio_entity_usage",
            "entio_workflow_state", "entio_fibo_search", "entio_fibo_entity",
        )
        private val help = setOf("entio_help", "entio_error_help", "entio_available_actions", "entio_workflow_state")
        private val edits = typedEditCapabilityDefinitions().map(AiCapabilityDefinition::name).toSet()
        private val analysis = draftAnalysisCapabilityDefinitions().map(AiCapabilityDefinition::name).toSet()
        private val bundleNames = mapOf(
            AiCapabilityBundleId.EXPLORATION to read,
            AiCapabilityBundleId.PLANNING to read,
            AiCapabilityBundleId.ONTOLOGY_EDITING to (read + edits),
            AiCapabilityBundleId.SHACL to (read + edits + setOf("entio_draft_validate", "entio_draft_preview", "entio_draft_shacl")),
            AiCapabilityBundleId.ANALYSIS to (read + analysis),
            AiCapabilityBundleId.REPAIR to (read + edits + analysis),
            AiCapabilityBundleId.HELP to help,
        )

        private fun forbiddenName(name: String): Boolean = listOf(
            "approve", "reject", "apply", "rollback", "config", "shell", "filesystem", "sparql", "raw_rdf", "secret", "network",
        ).any(name::contains)
    }
}
