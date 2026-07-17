package com.entio.web.ai

import com.entio.web.CollaborationHub
import com.entio.web.StagingWorkflowService
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class AiReviewSubmissionServiceTest {
    private val now: Instant = Instant.parse("2026-07-17T15:00:00Z")

    @Test
    fun explicitSubmissionCreatesOneOrdinaryReviewProposalWithoutWritingTheSource(): Unit = runBlocking {
        val fixture = fixture()
        val prepared = fixture.readyDraft("Receivable Account")
        val sourceBefore = Files.readString(fixture.projectRoot.resolve("ontology/simple.ttl"))

        val result = fixture.submissions.submit(fixture.scope, fixture.request(prepared, explicit = true))

        assertEquals("READYFORREVIEW", result.reviewState)
        assertEquals(result.proposalId, result.review.proposal?.id)
        assertEquals(1, result.review.entries.size)
        assertTrue(result.review.entries.single().aiGenerated)
        assertEquals("alice", result.review.entries.single().authorId)
        assertEquals("Add the reviewed Receivable Account concept.", result.review.entries.single().comment)
        assertEquals("Prepare the reviewed accounting concept.", result.rationale)
        assertEquals("alice", result.itemAttributions.single().acceptingUserId)
        assertEquals("conversation-1", result.itemAttributions.single().conversationId)
        assertEquals("run-1", result.itemAttributions.single().runId)
        assertEquals(prepared.analysis.references.map(AiDraftAnalysisReference::id).sorted(), result.analysisReferenceIds)
        assertEquals(sourceBefore, Files.readString(fixture.projectRoot.resolve("ontology/simple.ttl")))
        assertEquals(AiDraftStatus.SUBMITTED, fixture.drafts.get("alice", "simple", "conversation-1", "draft-1").status)
        assertFailsWith<AiDraftFailure> {
            fixture.workspace.add(fixture.scope, "draft-1", AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY, createClass("Payable Account"), "run-1")
        }
        assertEquals(result.proposalId, fixture.submissionAudits.get("alice", "simple", result.submissionId).proposalId)

        val shared = fixture.collaboration.recentSharedActivity("simple").events.single()
        assertEquals("proposal.ai-submitted", shared.eventType)
        assertEquals(result.proposalId, shared.proposalId)
        assertEquals(
            mapOf(
                "aiGenerated" to true,
                "submittingUserId" to "alice",
                "runId" to "run-1",
                "rationale" to "Prepare the reviewed accounting concept.",
            ),
            shared.data,
        )
        assertFalse(shared.data.toString().contains("Receivable Account"))
        assertFalse(shared.data.toString().contains("conversation"))
    }

    @Test
    fun missingExplicitActionUnauthorizedAndIncompleteDraftsNeverEnterSharedStaging(): Unit = runBlocking {
        val explicitFixture = fixture()
        val prepared = explicitFixture.readyDraft("Receivable Account")
        assertFailureCode("explicit-submission-required") {
            explicitFixture.submissions.submit(explicitFixture.scope, explicitFixture.request(prepared, explicit = false))
        }
        assertTrue(explicitFixture.staging.snapshot("simple").entries.isEmpty())

        val unauthorizedFixture = fixture()
        val unauthorizedPrepared = unauthorizedFixture.readyDraft("Receivable Account")
        val unauthorizedScope = unauthorizedFixture.scope.copy(
            permissions = unauthorizedFixture.scope.permissions - WebPermission.STAGE_OWN_CHANGE.name,
        )
        assertFailureCode("review-submission-forbidden") {
            unauthorizedFixture.submissions.submit(unauthorizedScope, unauthorizedFixture.request(unauthorizedPrepared, explicit = true))
        }
        assertTrue(unauthorizedFixture.staging.snapshot("simple").entries.isEmpty())

        val incompleteFixture = fixture()
        incompleteFixture.workspace.create(incompleteFixture.scope, "draft-1")
        val incomplete = incompleteFixture.workspace.add(
            incompleteFixture.scope,
            "draft-1",
            AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
            createClass("Receivable Account"),
            "run-1",
        )
        assertFailureCode("draft-not-ready-for-review") {
            incompleteFixture.submissions.submit(
                incompleteFixture.scope,
                AiReviewSubmissionRequest(
                    draftId = incomplete.id,
                    analysisId = "missing",
                    runId = "run-1",
                    rationale = "Submit it.",
                    expectedBaselineFingerprint = incomplete.baselineFingerprint,
                    expectedDraftFingerprint = assertNotNull(incomplete.draftFingerprint),
                    expectedPreviewGraphFingerprint = "missing",
                    expectedAnalysisReferenceIds = emptyList(),
                    explicitUserAction = true,
                ),
            )
        }
        assertTrue(incompleteFixture.staging.snapshot("simple").entries.isEmpty())
    }

    @Test
    fun mismatchedDraftAndAnalysisFingerprintsNeverEnterSharedStaging(): Unit = runBlocking {
        val fixture = fixture()
        val prepared = fixture.readyDraft("Receivable Account")
        val request = fixture.request(prepared, explicit = true)

        assertFailureCode("stale-draft-fingerprint") {
            fixture.submissions.submit(fixture.scope, request.copy(expectedDraftFingerprint = "stale-fingerprint"))
        }
        assertFailureCode("stale-or-incomplete-analysis") {
            fixture.submissions.submit(fixture.scope, request.copy(expectedPreviewGraphFingerprint = "stale-preview"))
        }
        assertFailureCode("stale-or-incomplete-analysis") {
            fixture.submissions.submit(fixture.scope, request.copy(expectedAnalysisReferenceIds = listOf("stale-reference")))
        }

        assertTrue(fixture.staging.snapshot("simple").entries.isEmpty())
        assertEquals(AiDraftStatus.READY_FOR_REVIEW, fixture.drafts.get("alice", "simple", "conversation-1", "draft-1").status)
    }

    @Test
    fun blockedStaleAndConflictingDraftsFailWithoutPartialImport(): Unit = runBlocking {
        val blockedFixture = fixture()
        blockedFixture.workspace.create(blockedFixture.scope, "draft-1")
        blockedFixture.workspace.add(
            blockedFixture.scope,
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
                rationale = "Exercise blocked submission.",
            ),
            "run-1",
        )
        val blockedAnalysis = blockedFixture.analysis.analyze(blockedFixture.scope, "draft-1")
        val blockedDraft = blockedFixture.drafts.get("alice", "simple", "conversation-1", "draft-1")
        assertFalse(blockedAnalysis.readyForReview)
        assertFailureCode("draft-not-ready-for-review") {
            blockedFixture.submissions.submit(blockedFixture.scope, blockedFixture.request(Prepared(blockedDraft, blockedAnalysis), true))
        }
        assertTrue(blockedFixture.staging.snapshot("simple").entries.isEmpty())

        val staleFixture = fixture()
        val stalePrepared = staleFixture.readyDraft("Receivable Account")
        val ontology = staleFixture.projectRoot.resolve("ontology/simple.ttl")
        Files.writeString(
            ontology,
            Files.readString(ontology) + "\n<https://example.com/entio/simple#ExternalChange> a <http://www.w3.org/2002/07/owl#Class> .\n",
        )
        assertFailureCode("stale-draft-baseline") {
            staleFixture.submissions.submit(staleFixture.scope, staleFixture.request(stalePrepared, true))
        }
        assertTrue(staleFixture.staging.snapshot("simple").entries.isEmpty())
        assertEquals(AiDraftStatus.STALE, staleFixture.drafts.get("alice", "simple", "conversation-1", "draft-1").status)

        val conflictFixture = fixture()
        val conflictPrepared = conflictFixture.readyDraft("Receivable Account")
        conflictFixture.staging.stage(
            "simple",
            WebStageChangeRequest("simple", "create-class", label = "Human Staged Concept"),
            "another-user",
        )
        assertFailureCode("shared-staging-conflict") {
            conflictFixture.submissions.submit(conflictFixture.scope, conflictFixture.request(conflictPrepared, true))
        }
        val shared = conflictFixture.staging.snapshot("simple")
        assertEquals(1, shared.entries.size)
        assertEquals("another-user", shared.entries.single().authorId)
        assertEquals(AiDraftStatus.READY_FOR_REVIEW, conflictFixture.drafts.get("alice", "simple", "conversation-1", "draft-1").status)
    }

    private suspend fun assertFailureCode(expected: String, block: suspend () -> Unit): Unit {
        val failure = assertFailsWith<AiDraftFailure> { block() }
        assertEquals(expected, failure.code)
    }

    private fun createClass(label: String): AiAddDraftItemArguments = AiAddDraftItemArguments(
        sourceId = "simple",
        request = WebStageChangeRequest("simple", "create-class", label = label),
        rationale = "Add the reviewed $label concept.",
    )

    private fun fixture(): Fixture {
        val source = listOf(Path.of("examples/simple-ontology"), Path.of("../examples/simple-ontology"))
            .first(Files::isDirectory)
            .toAbsolutePath()
            .normalize()
        val parent = Files.createTempDirectory("entio-ai-review-submission")
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
        val projects = InMemoryProjectRegistry(setOf(parent))
        projects.register("simple", "Simple", project)
        val staging = StagingWorkflowService(projects)
        val collaboration = CollaborationHub(projects, staging::snapshot)
        val drafts = InMemoryAiDraftStore()
        val analyses = InMemoryAiDraftAnalysisStore()
        val runs = InMemoryAiRunStore()
        val submissionAudits = InMemoryAiReviewSubmissionAuditStore()
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val baseline = AiProjectBaselineService(projects)
        val scope = AiCapabilityScope(
            userId = "alice",
            projectId = "simple",
            conversationId = "conversation-1",
            allowedSourceIds = listOf("shapes", "simple"),
            baselineFingerprint = baseline.current("simple", "simple"),
            role = "CONTRIBUTOR",
            permissions = setOf(
                WebPermission.USE_AI.name,
                WebPermission.PREPARE_EDIT.name,
                WebPermission.STAGE_OWN_CHANGE.name,
            ),
            availableFeatures = setOf(AiCapabilityFeatures.PRIVATE_DRAFT),
            createdAt = now,
        )
        var nextItemId = 1
        val workspace = AiPrivateDraftWorkspace(
            drafts,
            AiTypedEditCapabilityAdapter(staging),
            clock,
            itemIdFactory = { "item-${nextItemId++}" },
        )
        val analysis = AiDraftAnalysisService(drafts, analyses, baseline, clock = clock)
        runs.create(
            AiRun(
                id = "run-1",
                conversationId = scope.conversationId,
                userId = scope.userId,
                projectId = scope.projectId,
                scope = scope,
                status = AiRunStatus.READY_FOR_REVIEW,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val submissions = AiReviewSubmissionService(
            drafts,
            workspace,
            analyses,
            runs,
            baseline,
            staging,
            collaboration,
            submissionAudits,
            clock,
            idFactory = { "submission-1" },
        )
        return Fixture(project, staging, collaboration, drafts, workspace, analysis, submissionAudits, submissions, scope)
    }

    private data class Prepared(val draft: AiDraft, val analysis: AiDraftAnalysis)

    private data class Fixture(
        val projectRoot: Path,
        val staging: StagingWorkflowService,
        val collaboration: CollaborationHub,
        val drafts: InMemoryAiDraftStore,
        val workspace: AiPrivateDraftWorkspace,
        val analysis: AiDraftAnalysisService,
        val submissionAudits: InMemoryAiReviewSubmissionAuditStore,
        val submissions: AiReviewSubmissionService,
        val scope: AiCapabilityScope,
    ) {
        fun readyDraft(label: String): Prepared {
            workspace.create(scope, "draft-1")
            workspace.add(
                scope,
                "draft-1",
                AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
                AiAddDraftItemArguments(
                    sourceId = "simple",
                    request = WebStageChangeRequest("simple", "create-class", label = label),
                    rationale = "Add the reviewed $label concept.",
                ),
                "run-1",
            )
            val analysisResult = analysis.analyze(scope, "draft-1")
            return Prepared(drafts.get("alice", "simple", "conversation-1", "draft-1"), analysisResult)
        }

        fun request(prepared: Prepared, explicit: Boolean): AiReviewSubmissionRequest = AiReviewSubmissionRequest(
            draftId = prepared.draft.id,
            analysisId = prepared.analysis.id,
            runId = "run-1",
            rationale = "Prepare the reviewed accounting concept.",
            expectedBaselineFingerprint = prepared.draft.baselineFingerprint,
            expectedDraftFingerprint = assertNotNull(prepared.draft.draftFingerprint),
            expectedPreviewGraphFingerprint = assertNotNull(prepared.analysis.previewGraphFingerprint),
            expectedAnalysisReferenceIds = prepared.analysis.references.map(AiDraftAnalysisReference::id),
            explicitUserAction = explicit,
        )
    }
}
