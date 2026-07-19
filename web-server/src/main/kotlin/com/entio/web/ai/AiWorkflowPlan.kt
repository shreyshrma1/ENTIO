package com.entio.web.ai

import java.time.Instant

public enum class AiWorkPackageStatus { PENDING, READY, IN_PROGRESS, BLOCKED, COMPLETED, FAILED, CANCELLED }

public enum class AiPlanRiskFlag {
    LARGE_EDIT_COUNT,
    DELETION,
    HIERARCHY_REFACTOR,
    MATERIAL_EXTERNAL_REUSE,
    HIGH_IMPACT_SHACL,
    MULTI_SOURCE,
    IDENTITY_AMBIGUITY,
    USER_REQUESTED_CONFIRMATION,
}

public data class AiWorkEstimate(val expectedEditCount: Int, val relativeEffort: String) {
    init {
        require(expectedEditCount >= 0)
        require(relativeEffort in setOf("SMALL", "MEDIUM", "LARGE"))
    }
}

public data class AiWorkPackage(
    val id: String,
    val title: String,
    val dependsOn: List<String> = emptyList(),
    val expectedSourceIds: List<String>,
    val bundleId: AiCapabilityBundleId,
    val estimate: AiWorkEstimate,
    val evidenceReferences: List<String> = emptyList(),
    val riskFlags: Set<AiPlanRiskFlag> = emptySet(),
    val status: AiWorkPackageStatus = AiWorkPackageStatus.PENDING,
)

public data class AiPlanConfirmation(
    val userId: String,
    val planRevision: Int,
    val confirmedAt: Instant,
)

public data class AiWorkflowPlanRevision(
    val planId: String,
    val revision: Int,
    val taskId: String,
    val workPackages: List<AiWorkPackage>,
    val requiresConfirmation: Boolean,
    val confirmation: AiPlanConfirmation? = null,
    val createdAt: Instant,
) {
    init {
        require(planId.isNotBlank())
        require(revision > 0)
        require(taskId.isNotBlank())
        require(confirmation == null || confirmation.planRevision == revision)
    }
}

public data class AiWorkflowPlanHistory(
    val planId: String,
    val revisions: List<AiWorkflowPlanRevision>,
) {
    init {
        require(revisions.isNotEmpty())
        require(revisions.all { it.planId == planId })
    }

    public val current: AiWorkflowPlanRevision get() = revisions.maxBy(AiWorkflowPlanRevision::revision)
}

public class AiWorkflowPlanFailure(public val code: String, message: String) : IllegalArgumentException(message)

public class AiWorkflowPlanValidator {
    public fun validate(task: AiTask, packages: List<AiWorkPackage>): List<AiWorkPackage> {
        if (packages.isEmpty()) throw AiWorkflowPlanFailure("empty-workflow-plan", "A workflow plan requires work packages.")
        if (packages.size > task.policy.maxWorkPackages) throw AiWorkflowPlanFailure("work-package-limit", "The workflow plan exceeds its package limit.")
        val ids = packages.map(AiWorkPackage::id)
        if (ids.any(String::isBlank) || ids.distinct().size != ids.size) {
            throw AiWorkflowPlanFailure("duplicate-work-package", "Work package IDs must be unique and non-blank.")
        }
        val byId = packages.associateBy(AiWorkPackage::id)
        packages.forEach { item ->
            if (item.dependsOn.any { it !in byId }) throw AiWorkflowPlanFailure("missing-work-package-dependency", "A work package dependency is missing.")
            if (!task.scope.allowedSourceIds.containsAll(item.expectedSourceIds)) {
                throw AiWorkflowPlanFailure("work-package-source-scope", "A work package expects a source outside task scope.")
            }
            if (item.bundleId !in allowedExecutionBundles) {
                throw AiWorkflowPlanFailure("work-package-bundle-invalid", "A work package uses an unavailable bundle.")
            }
            if (item.estimate.expectedEditCount > task.policy.maxDraftItemsPerBatch) {
                throw AiWorkflowPlanFailure("work-package-edit-limit", "A work package exceeds the per-batch edit limit.")
            }
        }
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        fun visit(id: String) {
            if (id in visiting) throw AiWorkflowPlanFailure("cyclic-work-package-dependency", "Work package dependencies must be acyclic.")
            if (visited.add(id)) {
                visiting += id
                byId.getValue(id).dependsOn.sorted().forEach(::visit)
                visiting -= id
            }
        }
        ids.sorted().forEach(::visit)
        return topological(packages)
    }

    private fun topological(packages: List<AiWorkPackage>): List<AiWorkPackage> {
        val remaining = packages.associateBy(AiWorkPackage::id).toMutableMap()
        val result = mutableListOf<AiWorkPackage>()
        while (remaining.isNotEmpty()) {
            val completed = result.map(AiWorkPackage::id).toSet()
            val ready = remaining.values.filter { completed.containsAll(it.dependsOn) }.sortedBy(AiWorkPackage::id)
            if (ready.isEmpty()) throw AiWorkflowPlanFailure("cyclic-work-package-dependency", "Work package dependencies must be acyclic.")
            ready.forEach { result += it; remaining.remove(it.id) }
        }
        return result
    }

    private companion object {
        val allowedExecutionBundles = setOf(
            AiCapabilityBundleId.EXPLORATION,
            AiCapabilityBundleId.ONTOLOGY_EDITING,
            AiCapabilityBundleId.SHACL,
            AiCapabilityBundleId.ANALYSIS,
            AiCapabilityBundleId.REPAIR,
        )
    }
}
