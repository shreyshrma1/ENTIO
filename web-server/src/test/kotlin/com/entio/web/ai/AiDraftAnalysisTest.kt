package com.entio.web.ai

import com.entio.core.StagedChange
import com.entio.web.StagingWorkflowService
import com.entio.web.WebProposalPlanner
import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebPermission
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
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AiDraftAnalysisTest {
    private val now: Instant = Instant.parse("2026-07-17T13:00:00Z")

    @Test
    fun analysisMatchesTheExistingPlannerAndCachesOnlyTheExactDraftRevision(): Unit {
        val fixture = fixture()
        fixture.workspace.create(fixture.scope, "draft-1")
        val draft = fixture.workspace.add(
            fixture.scope,
            "draft-1",
            AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
            createClass("Receivable Account"),
        )

        val analysis = fixture.analysis.analyze(fixture.scope, draft.id)
        val prepared = WebProposalPlanner().prepare(
            fixture.baseline.load("simple"),
            draft.items.map(::stagedChange),
        )

        assertEquals(prepared.proposal.diff, analysis.semanticDiff)
        assertEquals(prepared.impact.shaclImpact, analysis.impact?.shaclImpact)
        assertEquals(analysis, fixture.analysis.analyze(fixture.scope, draft.id))
        assertEquals(draft.draftFingerprint, analysis.draftFingerprint)
        assertEquals(draft.revisions.single().revision, analysis.revision)

        val changed = fixture.workspace.add(
            fixture.scope,
            draft.id,
            AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
            createClass("Payable Account"),
        )
        assertTrue(changed.analysisReferenceIds.isEmpty())

        val changedAnalysis = fixture.analysis.analyze(fixture.scope, draft.id)
        assertNotEquals(analysis.id, changedAnalysis.id)
        assertNotEquals(analysis.draftFingerprint, changedAnalysis.draftFingerprint)
        assertEquals(changed.revisions.last().revision, changedAnalysis.revision)
    }

    @Test
    fun projectMutationInvalidatesTheAnalysisAndMarksThePrivateDraftStale(): Unit {
        val fixture = fixture()
        fixture.workspace.create(fixture.scope, "draft-1")
        fixture.workspace.add(
            fixture.scope,
            "draft-1",
            AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
            createClass("Receivable Account"),
        )
        fixture.analysis.analyze(fixture.scope, "draft-1")

        val ontology = fixture.projectRoot.resolve("ontology/simple.ttl")
        Files.writeString(
            ontology,
            Files.readString(ontology) + "\n<https://example.com/entio/simple#ExternalChange> a <http://www.w3.org/2002/07/owl#Class> .\n",
        )

        val failure = kotlin.runCatching { fixture.analysis.analyze(fixture.scope, "draft-1") }.exceptionOrNull()
        assertEquals("stale-draft-baseline", (failure as AiDraftFailure).code)
        assertEquals(AiDraftStatus.STALE, fixture.workspace.read(fixture.scope, "draft-1").status)
        assertTrue(fixture.workspace.read(fixture.scope, "draft-1").analysisReferenceIds.isEmpty())
    }

    @Test
    fun blockingFindingsCannotBeOverriddenByNarrativeAndCorrectionRemainsPrivate(): Unit {
        val fixture = fixture()
        fixture.workspace.create(fixture.scope, "draft-1")
        val invalid = fixture.workspace.add(
            fixture.scope,
            "draft-1",
            AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
            AiAddDraftItemArguments(
                sourceId = "simple",
                request = WebStageChangeRequest(
                    sourceId = "simple",
                    editType = "add-superclass",
                    classLabel = "Customer",
                    superclassLabel = "Customer",
                ),
                rationale = "Model Customer as its own parent.",
            ),
        )

        val blocked = fixture.analysis.analyze(fixture.scope, invalid.id)
        assertFalse(blocked.readyForReview)
        assertTrue(blocked.findings.any { it.severity == com.entio.core.ValidationSeverity.Error })

        val itemId = invalid.items.single().id
        val outcome = AiDraftSelfCorrectionController(fixture.workspace, fixture.analysis).correct(
            fixture.scope,
            invalid.id,
            AiRunPolicy(maxCorrectionCycles = 1),
            listOf(
                AiStructuredDraftCorrection.Update(
                    capabilityName = AiTypedEditCapabilityAdapter.UPDATE_ONTOLOGY_CAPABILITY,
                    arguments = AiUpdateDraftItemArguments(
                        itemId = itemId,
                        sourceId = "simple",
                        request = WebStageChangeRequest(
                            sourceId = "simple",
                            editType = "add-superclass",
                            classLabel = "Customer",
                            superclassLabel = "Account",
                        ),
                        rationale = "Use the existing Account class as the reviewed parent.",
                    ),
                    explanation = "The model claims this is valid; deterministic checks still decide.",
                ),
            ),
        )

        assertEquals(1, outcome.records.size)
        assertEquals(blocked.id, outcome.records.single().originalAnalysisId)
        assertEquals(blocked.findings.map(AiDraftFinding::id), outcome.records.single().originalFindingIds)
        assertEquals(outcome.analysis.id, outcome.records.single().resultingAnalysisId)
        assertTrue(fixture.staging.snapshot("simple").entries.isEmpty())
    }

    @Test
    fun zeroCorrectionLimitDoesNotMutateTheDraft(): Unit {
        val fixture = fixture()
        fixture.workspace.create(fixture.scope, "draft-1")
        val draft = fixture.workspace.add(
            fixture.scope,
            "draft-1",
            AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
            AiAddDraftItemArguments(
                sourceId = "simple",
                request = WebStageChangeRequest(
                    sourceId = "simple",
                    editType = "add-superclass",
                    classLabel = "Customer",
                    superclassLabel = "Customer",
                ),
                rationale = "Exercise the deterministic correction limit.",
            ),
        )

        val outcome = AiDraftSelfCorrectionController(fixture.workspace, fixture.analysis).correct(
            fixture.scope,
            draft.id,
            AiRunPolicy(maxCorrectionCycles = 0),
            listOf(AiStructuredDraftCorrection.Remove(AiRemoveDraftItemArguments(draft.items.single().id, "Remove it."), "Remove it.")),
        )

        assertTrue(outcome.limitReached)
        assertFalse(outcome.analysis.readyForReview)
        assertEquals(draft.items, fixture.workspace.read(fixture.scope, draft.id).items)
        assertTrue(outcome.records.isEmpty())
        assertTrue(fixture.staging.snapshot("simple").entries.isEmpty())
    }

    @Test
    fun analysisCapabilitiesUseStrictDraftIdsAndReturnStructuredStageReferences(): Unit {
        val fixture = fixture()
        fixture.workspace.create(fixture.scope, "draft-1")
        fixture.workspace.add(
            fixture.scope,
            "draft-1",
            AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
            createClass("Receivable Account"),
        )
        val registry = AiCapabilityRegistry(defaultAiCapabilityDefinitions())
        val definition = registry.snapshot(fixture.scope).definitions.single { it.name == "entio_draft_impact" }
        val invocation = AiDecodedCapabilityInvocation(
            invocationId = "call-1",
            definition = definition,
            arguments = definition.decoder.decode(com.fasterxml.jackson.databind.ObjectMapper().readTree("""{"draftId":"draft-1"}""")),
        )

        val payload = AiDraftAnalysisCapabilityService(fixture.analysis).execute(invocation, fixture.scope)

        assertEquals(AiDraftAnalysisStage.IMPACT, payload.stage)
        assertTrue(payload.referenceId.endsWith(":impact"))
        assertEquals(fixture.analysis.analyze(fixture.scope, "draft-1").id, payload.analysisId)
    }

    private fun createClass(label: String): AiAddDraftItemArguments = AiAddDraftItemArguments(
        sourceId = "simple",
        request = WebStageChangeRequest("simple", "create-class", label = label),
        rationale = "Add the reviewed $label concept.",
    )

    private fun stagedChange(item: AiDraftItem): StagedChange {
        val operation = item.operation as AiTypedDraftOperation
        return StagedChange(
            id = item.id,
            order = item.order,
            targetSourceId = operation.targetSourceId,
            summary = operation.summary,
            operation = operation.preparedOperation,
            normalizedValues = operation.normalizedValues,
        )
    }

    private fun fixture(): Fixture {
        val source = listOf(Path.of("examples/simple-ontology"), Path.of("../examples/simple-ontology"))
            .first(Files::isDirectory)
            .toAbsolutePath()
            .normalize()
        val parent = Files.createTempDirectory("entio-ai-draft-analysis")
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
        val store = InMemoryAiDraftStore()
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val baseline = AiProjectBaselineService(registry)
        val scope = AiCapabilityScope(
            userId = "alice",
            projectId = "simple",
            conversationId = "conversation-1",
            allowedSourceIds = listOf("simple", "shapes"),
            baselineFingerprint = baseline.current("simple", "simple"),
            role = "CONTRIBUTOR",
            permissions = setOf(WebPermission.USE_AI.name, WebPermission.PREPARE_EDIT.name),
            availableFeatures = setOf(AiCapabilityFeatures.PRIVATE_DRAFT),
            createdAt = now,
        )
        var nextItemId = 1
        val workspace = AiPrivateDraftWorkspace(
            store = store,
            adapter = AiTypedEditCapabilityAdapter(staging),
            clock = clock,
            itemIdFactory = { "item-${nextItemId++}" },
        )
        return Fixture(
            projectRoot = project,
            staging = staging,
            scope = scope,
            workspace = workspace,
            baseline = baseline,
            analysis = AiDraftAnalysisService(store, InMemoryAiDraftAnalysisStore(), baseline, clock = clock),
        )
    }

    private data class Fixture(
        val projectRoot: Path,
        val staging: StagingWorkflowService,
        val scope: AiCapabilityScope,
        val workspace: AiPrivateDraftWorkspace,
        val baseline: AiProjectBaselineService,
        val analysis: AiDraftAnalysisService,
    )
}
