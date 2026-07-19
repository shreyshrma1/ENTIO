package com.entio.web.ai

import com.entio.web.contract.WebStageChangeRequest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AiTaskRevisionServiceTest {
    @Test
    fun undoLatestAndLatestPackagePreserveRevisionHistory(): Unit {
        val first = editingFixture()
        val service = AiTaskRevisionService(first.tasks, first.drafts)
        first.service.append(
            first.scope, "draft-1",
            first.batchRequest(listOf(first.entry(WebStageChangeRequest("simple", "create-class", label = "Receivable")))),
        )
        val undone = service.undoLatest(first.scope, "task-1", "draft-1")
        assertEquals(0, undone.items.size)
        assertEquals(listOf("task-batch-add", "undo"), undone.revisions.map(AiDraftRevision::action))

        val second = editingFixture()
        val packageService = AiTaskRevisionService(second.tasks, second.drafts)
        second.service.append(
            second.scope, "draft-1",
            second.batchRequest(listOf(second.entry(WebStageChangeRequest("simple", "create-class", label = "Payable")))),
        )
        assertEquals(0, packageService.undoPackage(second.scope, "task-1", "draft-1", "package-a").items.size)
    }

    @Test
    fun itemRemovalIsDependencySafeAndAssumptionRevisionRequiresReanalysis(): Unit {
        val fixture = editingFixture()
        val first = fixture.drafts.add(
            fixture.scope, "draft-1", AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
            AiAddDraftItemArguments(
                "simple", WebStageChangeRequest("simple", "create-class", label = "Parent"), "Create parent.",
            ),
        )
        val parentId = first.items.single().id
        fixture.drafts.add(
            fixture.scope, "draft-1", AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
            AiAddDraftItemArguments(
                "simple", WebStageChangeRequest("simple", "create-class", label = "Child"), "Create child.", listOf(parentId),
            ),
        )
        val service = AiTaskRevisionService(fixture.tasks, fixture.drafts)
        assertEquals("draft-item-has-dependents", assertFailsWith<AiDraftFailure> {
            service.removeItem(fixture.scope, "task-1", "draft-1", parentId)
        }.code)

        val now = Instant.parse("2026-07-20T02:00:00Z")
        val taskStore = InMemoryAiTaskStore()
        taskStore.create(
            AiTaskWorkspace(
                taskFixture(status = AiTaskStatus.REPAIRING),
                assumptions = listOf(AiTaskAssumption("assumption-1", "Old choice", now)),
            ),
        )
        val assumptionService = AiTaskRevisionService(taskStore, fixture.drafts, Clock.fixed(now, ZoneOffset.UTC))
        val changed = assumptionService.changeAssumption("alice", "simple", "task-1", 0, "assumption-1", "New external choice")
        assertEquals(AiTaskStatus.VALIDATING, changed.task.status)
        assertEquals("New external choice", changed.assumptions.single().statement)
    }
}
