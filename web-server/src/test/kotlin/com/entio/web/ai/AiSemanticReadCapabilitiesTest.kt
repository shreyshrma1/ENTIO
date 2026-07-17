package com.entio.web.ai

import com.entio.core.Iri
import com.entio.web.CollaborationHub
import com.entio.web.FiboWebService
import com.entio.web.SemanticJobManager
import com.entio.web.StagingWorkflowService
import com.entio.web.WebJobRequest
import com.entio.web.WebSemanticJobState
import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebPageRequest
import com.entio.web.contract.WebPermission
import com.entio.web.contract.WebStageChangeRequest
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AiSemanticReadCapabilitiesTest {
    @Test
    fun semanticResultsPreserveJobIdentityFingerprintAndSourceScope(): Unit {
        val fixture = fixture()
        val started = fixture.semanticJobs.submit("simple", WebJobRequest(kind = "reasoning", scope = "applied"))
        val completed = awaitTerminal(fixture.semanticJobs, started.id)
        val direct = assertNotNull(fixture.semanticJobs.details("simple", started.id, 1))

        val payload = fixture.capabilities.semanticJob(fixture.scope, AiSemanticJobArguments(started.id, 1))

        assertEquals(completed.status.name, payload.state)
        assertEquals(completed.graphFingerprint, payload.graphFingerprint)
        assertEquals(direct.job.resultSummary["consistency"]?.toString(), payload.consistency)
        assertTrue(payload.facts.all { it.sourceId in fixture.scope.allowedSourceIds })
        assertEquals("UNAVAILABLE", payload.explanationsState)
        assertTrue(payload.warnings.any { it.contains("explanation") })
        if (direct.truncated) assertTrue(payload.truncated)
    }

    @Test
    fun completedProposalResultBecomesStaleWhenTheProposalChanges(): Unit {
        val fixture = fixture()
        fixture.staging.stage(
            "simple",
            WebStageChangeRequest(
                sourceId = "simple",
                editType = "create-class",
                classIri = "https://example.com/entio/simple#FirstAiClass",
                label = "First AI class",
            ),
            "alice",
        )
        fixture.staging.preview("simple", "alice")
        val started = fixture.semanticJobs.submit("simple", WebJobRequest(kind = "reasoning", scope = "proposal"))
        assertEquals(WebSemanticJobState.Completed, awaitTerminal(fixture.semanticJobs, started.id).status)

        fixture.staging.stage(
            "simple",
            WebStageChangeRequest(
                sourceId = "simple",
                editType = "create-class",
                classIri = "https://example.com/entio/simple#SecondAiClass",
                label = "Second AI class",
            ),
            "alice",
        )
        val payload = fixture.capabilities.semanticJob(fixture.scope, AiSemanticJobArguments(started.id, 10))

        assertEquals(WebSemanticJobState.Stale.name, payload.state)
        assertTrue(payload.facts.isEmpty())
        assertTrue(payload.warnings.any { it.contains("not exposed") })
    }

    @Test
    fun proposalAndActivityReadsRemainCurrentProjectBoundedAndExplicit(): Unit = runBlocking {
        val fixture = fixture()
        fixture.staging.stage(
            "simple",
            WebStageChangeRequest(
                sourceId = "simple",
                editType = "create-class",
                classIri = "https://example.com/entio/simple#AiTestClass",
                label = "AI test class",
            ),
            "alice",
        )
        val preview = fixture.staging.preview("simple", "alice")
        fixture.collaboration.stagedChange("simple", "stage-1", preview.proposal?.id)
        fixture.collaboration.proposal("simple", "proposal.previewed", preview.proposal?.id)

        val proposal = fixture.capabilities.proposal(fixture.scope, AiProposalReadArguments(preview.proposal?.id, 1))
        val activity = fixture.capabilities.activity(fixture.scope, AiActivityReadArguments(1))

        assertEquals(preview.proposal?.id, proposal.proposalId)
        assertEquals(preview.proposal?.baselineProjectFingerprint, proposal.baselineFingerprint)
        assertEquals("UNAVAILABLE", proposal.impactState)
        assertTrue(proposal.diff.size <= 1)
        assertEquals(1, activity.events.size)
        assertTrue(activity.truncated)
        assertTrue(activity.events.single().type.startsWith("proposal."))
        assertTrue(activity.events.none { it.type.startsWith("presence.") || it.type == "entity.activity" })
    }

    @Test
    fun fiboReadsDelegateToPinnedDeterministicSearchAndDescriptors(): Unit {
        val fixture = fixture()
        val direct = fixture.fibo.search(
            "simple",
            "agreement",
            null,
            null,
            true,
            WebPageRequest(limit = 2),
        )

        val search = fixture.capabilities.fiboSearch(
            fixture.scope,
            AiFiboSearchArguments("agreement", null, null, 2),
        )
        val detail = fixture.capabilities.fiboEntity(
            fixture.scope,
            AiFiboEntityArguments("https://www.omg.org/spec/Commons/ContextualIdentifiers/ContextualIdentifier"),
        )

        assertEquals(direct.page.items.map { it.iri }, search.hits.map { it.iri })
        assertEquals(direct.page.items.map { it.label }, search.hits.map { it.label })
        assertEquals("contextual identifier", detail.entity.label)
        assertFalse(detail.entity.definitions.isEmpty())
        assertTrue(detail.evidence.all { it.provenance == AiFactProvenance.EXTERNAL })
    }

    private fun fixture(): Fixture {
        val projectRoot = listOf(
            Path.of("examples/simple-ontology"),
            Path.of("../examples/simple-ontology"),
        ).map { it.toAbsolutePath().normalize() }.first { it.resolve("entio.yaml").toFile().isFile }
        val registry = InMemoryProjectRegistry(setOf(projectRoot.parent))
        registry.register("simple", "Simple ontology", projectRoot)
        val staging = StagingWorkflowService(registry)
        val semanticJobs = SemanticJobManager(staging, registry)
        val collaboration = CollaborationHub(registry, stagingSnapshot = { staging.snapshot(it) })
        val fibo = FiboWebService(registry, staging)
        return Fixture(
            staging,
            semanticJobs,
            collaboration,
            fibo,
            AiSemanticReadCapabilityService(semanticJobs, staging, collaboration, fibo),
            AiCapabilityScope(
                userId = "alice",
                projectId = "simple",
                conversationId = "conversation-1",
                allowedSourceIds = listOf("simple", "shapes"),
                baselineFingerprint = "baseline",
                role = "CONTRIBUTOR",
                permissions = setOf(WebPermission.BROWSE.name, WebPermission.USE_AI.name),
                availableFeatures = setOf(
                    AiCapabilityFeatures.SEMANTIC_RESULTS,
                    AiCapabilityFeatures.PROPOSAL_READ,
                    AiCapabilityFeatures.ACTIVITY_READ,
                    AiCapabilityFeatures.FIBO_READ,
                ),
                createdAt = Instant.parse("2026-07-17T12:00:00Z"),
            ),
        )
    }

    private fun awaitTerminal(manager: SemanticJobManager, jobId: String): com.entio.web.WebSemanticJobStatus {
        repeat(200) {
            val status = assertNotNull(manager.find("simple", jobId))
            if (status.status !in setOf(WebSemanticJobState.Queued, WebSemanticJobState.Running)) return status
            Thread.sleep(10)
        }
        error("Semantic job did not complete.")
    }

    private data class Fixture(
        val staging: StagingWorkflowService,
        val semanticJobs: SemanticJobManager,
        val collaboration: CollaborationHub,
        val fibo: FiboWebService,
        val capabilities: AiSemanticReadCapabilityService,
        val scope: AiCapabilityScope,
    )
}
