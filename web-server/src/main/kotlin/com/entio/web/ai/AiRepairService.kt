package com.entio.web.ai

import java.time.Clock
import java.time.Instant
import java.util.UUID

public enum class AiRepairCodeStatus { AUTO_REPAIRABLE, CLARIFICATION_REQUIRED, EXPLANATION_ONLY, UNSUPPORTED }

public data class AiRepairFinding(
    val findingId: String,
    val code: String,
    val workPackageId: String,
    val entityIris: List<String>,
    val draftItemIds: List<String>,
    val expected: String?,
    val actual: String?,
    val source: String,
    val evidenceReferences: List<String>,
    val deterministicCandidateReferences: List<String>,
)

public data class AiRepairPacket(
    val id: String,
    val finding: AiRepairFinding,
    val status: AiRepairCodeStatus,
    val allowedTypedActions: List<String>,
    val requiresClarification: Boolean,
    val createdAt: Instant,
)

public class AiRepairFailure(public val code: String, message: String) : IllegalArgumentException(message)

public class AiRepairPacketBuilder(
    private val clock: Clock = Clock.systemUTC(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    public fun build(findings: List<AiRepairFinding>): List<AiRepairPacket> = findings
        .sortedWith(compareBy(AiRepairFinding::code, AiRepairFinding::findingId))
        .map { finding ->
            val entry = inventory[finding.code] ?: Inventory(AiRepairCodeStatus.UNSUPPORTED, emptyList())
            val candidatesSafe = finding.deterministicCandidateReferences.size == 1 || finding.code == "duplicate-draft-edit"
            val status = if (entry.status == AiRepairCodeStatus.AUTO_REPAIRABLE && !candidatesSafe) {
                AiRepairCodeStatus.CLARIFICATION_REQUIRED
            } else {
                entry.status
            }
            AiRepairPacket(
                idFactory(), finding, status, entry.actions,
                status == AiRepairCodeStatus.CLARIFICATION_REQUIRED, clock.instant(),
            )
        }

    public companion object {
        private data class Inventory(val status: AiRepairCodeStatus, val actions: List<String>)
        private val inventory = mapOf(
            "invalid-language-tag" to Inventory(AiRepairCodeStatus.AUTO_REPAIRABLE, listOf("update-typed-item")),
            "duplicate-draft-edit" to Inventory(AiRepairCodeStatus.AUTO_REPAIRABLE, listOf("remove-later-item")),
            "ambiguous-preferred-label" to Inventory(AiRepairCodeStatus.CLARIFICATION_REQUIRED, emptyList()),
            "incompatible-property-domain" to Inventory(AiRepairCodeStatus.CLARIFICATION_REQUIRED, emptyList()),
            "incompatible-property-range" to Inventory(AiRepairCodeStatus.CLARIFICATION_REQUIRED, emptyList()),
            "shacl-validation" to Inventory(AiRepairCodeStatus.EXPLANATION_ONLY, emptyList()),
        )

        public fun inventoryStatuses(): Map<String, AiRepairCodeStatus> = inventory.mapValues { it.value.status }
    }
}

public fun interface AiPrivateRepairMutation {
    public fun apply(packet: AiRepairPacket): String
}

public data class AiRepairRevision(
    val packet: AiRepairPacket,
    val cycle: Int,
    val draftRevisionReference: String,
    val createdAt: Instant,
)

public class AiRepairController(
    private val store: AiTaskStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val histories: MutableMap<String, List<AiRepairRevision>> = linkedMapOf()

    @Synchronized
    public fun repair(
        userId: String,
        projectId: String,
        taskId: String,
        expectedRevision: Long,
        packet: AiRepairPacket,
        mutation: AiPrivateRepairMutation,
    ): AiTaskWorkspace {
        val current = store.get(userId, projectId, taskId)
        if (current.revision != expectedRevision) throw AiRepairFailure("stale-repair-task", "The task changed before repair.")
        if (packet.status != AiRepairCodeStatus.AUTO_REPAIRABLE || packet.allowedTypedActions.isEmpty()) {
            val status = if (packet.requiresClarification) AiTaskStatus.PAUSED else AiTaskStatus.FAILED
            return update(
                current,
                expectedRevision,
                status,
                pause = if (status == AiTaskStatus.PAUSED) {
                    AiTaskPause(
                        "repair-clarification-required",
                        "The repair requires user clarification of business meaning.",
                        current.task.status,
                        createdAt = clock.instant(),
                    )
                } else {
                    current.pause
                },
            )
        }
        val packageCycles = histories[taskId].orEmpty().count { it.packet.finding.workPackageId == packet.finding.workPackageId }
        if (packageCycles >= current.task.policy.maxRepairCyclesPerPackage ||
            current.counters.repairCycleCount >= current.task.policy.maxRepairCyclesPerTask
        ) {
            throw AiRepairFailure("repair-cycle-limit", "The bounded repair cycle limit was reached.")
        }
        val draftRevision = mutation.apply(packet)
        val cycle = current.counters.repairCycleCount + 1
        histories[taskId] = histories[taskId].orEmpty() + AiRepairRevision(packet, cycle, draftRevision, clock.instant())
        return update(
            current,
            expectedRevision,
            AiTaskStatus.VALIDATING,
            counters = current.counters.copy(repairCycleCount = cycle),
        )
    }

    public fun history(userId: String, projectId: String, taskId: String): List<AiRepairRevision> {
        store.get(userId, projectId, taskId)
        return histories[taskId].orEmpty()
    }

    private fun update(
        current: AiTaskWorkspace,
        expectedRevision: Long,
        status: AiTaskStatus,
        counters: AiTaskCounters = current.counters,
        pause: AiTaskPause? = current.pause,
    ): AiTaskWorkspace {
        val now = clock.instant()
        return store.update(
            current.task.userId, current.task.projectId, current.task.id, expectedRevision,
            current.copy(
                task = current.task.copy(status = status, updatedAt = now), revision = expectedRevision + 1,
                counters = counters, pause = pause, updatedAt = now,
            ),
        )
    }
}
