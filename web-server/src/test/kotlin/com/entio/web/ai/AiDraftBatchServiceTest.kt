package com.entio.web.ai

import com.entio.web.StagingWorkflowService
import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebStageChangeRequest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.isRegularFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AiDraftBatchServiceTest {
    @Test
    fun invalidOperationMakesEntireBatchInvisible(): Unit {
        val fixture = editingFixture()
        val request = fixture.batchRequest(
            listOf(
                fixture.entry(WebStageChangeRequest("simple", "create-class", label = "Valid Class")),
                fixture.entry(WebStageChangeRequest("simple", "raw-rdf", value = "unsafe")),
            ),
        )

        assertFailsWith<AiDraftFailure> { fixture.service.append(fixture.scope, "draft-1", request) }
        assertTrue(fixture.drafts.read(fixture.scope, "draft-1").items.isEmpty())
    }

    @Test
    fun fiftyEditsAppendDeterministicallyAcrossBoundedAtomicBatches(): Unit {
        val fixture = editingFixture()
        val entries = (0 until 50).map { index ->
            fixture.entry(WebStageChangeRequest("simple", "create-class", label = "Generated Class $index"))
        }

        var draft = fixture.drafts.read(fixture.scope, "draft-1")
        entries.chunked(20).forEach { batch ->
            draft = fixture.service.append(fixture.scope, "draft-1", fixture.batchRequest(batch))
        }

        assertEquals(50, draft.items.size)
        assertEquals(listOf(20, 40, 50), draft.revisions.map { it.itemIds.size })
        assertTrue(draft.items.all { it.attribution?.taskId == "task-1" && it.attribution?.workPackageId == "package-a" })
        assertTrue(draft.items.all { it.attribution?.executionSegmentId == "segment-1" && it.attribution?.runId == "run-1" })
        assertTrue(fixture.staging.snapshot("simple").entries.isEmpty())
    }

    @Test
    fun batchAndTaskLimitsAreEnforcedBeforeAppend(): Unit {
        val fixture = editingFixture()
        val entries = (0 until 21).map { fixture.entry(WebStageChangeRequest("simple", "create-class", label = "Class $it")) }
        assertEquals("task-batch-limit", assertFailsWith<AiDraftBatchFailure> {
            fixture.service.append(fixture.scope, "draft-1", fixture.batchRequest(entries))
        }.code)
    }

    @Test
    fun oneAtomicBatchUsesOrdinaryTypedPreparationAcrossAllowedSources(): Unit {
        val fixture = editingFixture()
        val ontology = fixture.entry(WebStageChangeRequest("simple", "create-class", label = "Receivable"))
        val shaclRequest = WebStageChangeRequest(
            "shapes", "shacl-create-node-shape", shapeLabel = "Receivable Shape", targetClassLabel = "Account",
        )
        val shacl = AiDraftBatchEntry(
            AiTypedEditCapabilityAdapter.ADD_SHACL_CAPABILITY,
            AiAddDraftItemArguments("shapes", shaclRequest, "Prepare the approved shape."),
        )

        val draft = fixture.service.append(fixture.scope, "draft-1", fixture.batchRequest(listOf(ontology, shacl)))

        assertEquals(listOf("shapes", "simple"), draft.items.map { it.operation.targetSourceId }.sorted())
        assertEquals(1, draft.revisions.size)
    }
}

internal data class EditingFixture(
    val scope: AiCapabilityScope,
    val staging: StagingWorkflowService,
    val adapter: AiTypedEditCapabilityAdapter,
    val drafts: AiPrivateDraftWorkspace,
    val tasks: InMemoryAiTaskStore,
    val service: AiDraftBatchService,
) {
    fun entry(request: WebStageChangeRequest) = AiDraftBatchEntry(
        AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
        AiAddDraftItemArguments(request.sourceId, request, "Prepared by approved task package."),
    )

    fun batchRequest(entries: List<AiDraftBatchEntry>) = AiDraftBatchRequest(
        taskId = "task-1",
        workPackageId = "package-a",
        executionSegmentId = "segment-1",
        providerRunId = "run-1",
        expectedTaskRevision = 0,
        entries = entries,
    )
}

internal fun editingFixture(): EditingFixture {
    val now = Instant.parse("2026-07-19T20:00:00Z")
    val source = listOf(Path.of("examples/simple-ontology"), Path.of("../examples/simple-ontology"))
        .first(Files::isDirectory).toAbsolutePath().normalize()
    val parent = Files.createTempDirectory("entio-ai-task-batch")
    val project = parent.resolve("simple-ontology")
    Files.walk(source).use { paths ->
        paths.forEach { current ->
            val target = project.resolve(source.relativize(current).toString())
            if (current.isRegularFile()) {
                Files.createDirectories(target.parent)
                Files.copy(current, target, StandardCopyOption.REPLACE_EXISTING)
            } else {
                Files.createDirectories(target)
            }
        }
    }
    val registry = InMemoryProjectRegistry(setOf(parent))
    registry.register("simple", "Simple", project)
    val staging = StagingWorkflowService(registry)
    val adapter = AiTypedEditCapabilityAdapter(staging)
    var itemId = 0
    val drafts = AiPrivateDraftWorkspace(
        InMemoryAiDraftStore(), adapter, Clock.fixed(now, ZoneOffset.UTC), itemIdFactory = { "item-${++itemId}" },
    )
    val scope = capabilityScope().copy(allowedSourceIds = listOf("simple", "shapes"), baselineFingerprint = "project-fingerprint-1")
    drafts.create(scope, "draft-1")
    val tasks = InMemoryAiTaskStore()
    tasks.create(AiTaskWorkspace(taskFixture(status = AiTaskStatus.EXECUTING), currentWorkPackageId = "package-a"))
    return EditingFixture(scope, staging, adapter, drafts, tasks, AiDraftBatchService(tasks, drafts))
}
