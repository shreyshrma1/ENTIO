package com.entio.web.ai

import com.entio.core.BaselineImpactStatus
import com.entio.core.ChangeProposalStatus
import com.entio.core.ChangeSet
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.GraphChange
import com.entio.core.GraphChangeKind
import com.entio.core.ProposalImpactReport
import com.entio.core.ReasoningResult
import com.entio.core.ReasoningRunStatus
import com.entio.core.SemanticDiff
import com.entio.core.StagedChange
import com.entio.core.ValidationIssue
import com.entio.core.ValidationReport
import com.entio.core.ValidationSeverity
import com.entio.core.ValidationStatus
import com.entio.diff.ProposalImpactAnalyzer
import com.entio.semantic.ProjectLoader
import com.entio.semantic.ProposalCreator
import com.entio.semantic.ReasoningService
import com.entio.web.PreparedWebProposal
import com.entio.web.WebProposalPlanner
import com.entio.web.WebWorkflowFailure
import com.entio.web.contract.ProjectRegistry
import com.entio.web.contract.WebPermission
import com.fasterxml.jackson.databind.JsonNode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant

public enum class AiDraftAnalysisStatus {
    COMPLETED,
    BLOCKED,
    FAILED,
    STALE,
}

public enum class AiDraftAnalysisStage {
    VALIDATION,
    PREVIEW,
    DIFF,
    REASONING,
    SHACL,
    IMPACT,
}

public data class AiDraftAnalysisReference(
    val stage: AiDraftAnalysisStage,
    val id: String,
)

public data class AiDraftFinding(
    val id: String,
    val code: String,
    val severity: ValidationSeverity,
    val message: String,
    val source: String? = null,
)

public data class AiDraftAnalysis(
    val id: String,
    val draftId: String,
    val conversationId: String,
    val userId: String,
    val projectId: String,
    val revision: Int,
    val baselineFingerprint: String,
    val draftFingerprint: String,
    val status: AiDraftAnalysisStatus,
    val validationReport: ValidationReport,
    val previewGraphFingerprint: String?,
    val semanticDiff: SemanticDiff?,
    val currentReasoning: ReasoningResult?,
    val previewReasoning: ReasoningResult?,
    val impact: ProposalImpactReport?,
    val findings: List<AiDraftFinding>,
    val references: List<AiDraftAnalysisReference>,
    val readyForReview: Boolean,
    val createdAt: Instant,
)

public interface AiDraftAnalysisStore {
    public fun put(analysis: AiDraftAnalysis): AiDraftAnalysis
    public fun get(userId: String, projectId: String, conversationId: String, analysisId: String): AiDraftAnalysis
    public fun findExact(
        userId: String,
        projectId: String,
        conversationId: String,
        draftId: String,
        revision: Int,
        baselineFingerprint: String,
        draftFingerprint: String,
    ): AiDraftAnalysis?
}

public class InMemoryAiDraftAnalysisStore : AiDraftAnalysisStore {
    private val analyses: MutableMap<String, AiDraftAnalysis> = linkedMapOf()

    @Synchronized
    override fun put(analysis: AiDraftAnalysis): AiDraftAnalysis {
        analyses[analysis.id] = analysis
        return analysis
    }

    @Synchronized
    override fun get(userId: String, projectId: String, conversationId: String, analysisId: String): AiDraftAnalysis {
        val analysis = analyses[analysisId]
            ?: throw AiDraftFailure("missing-draft-analysis", "Draft analysis '$analysisId' was not found.")
        if (analysis.userId != userId || analysis.projectId != projectId || analysis.conversationId != conversationId) {
            throw AiDraftFailure("draft-analysis-scope-violation", "The draft analysis is outside the requested scope.")
        }
        return analysis
    }

    @Synchronized
    override fun findExact(
        userId: String,
        projectId: String,
        conversationId: String,
        draftId: String,
        revision: Int,
        baselineFingerprint: String,
        draftFingerprint: String,
    ): AiDraftAnalysis? = analyses.values.firstOrNull {
        it.userId == userId &&
            it.projectId == projectId &&
            it.conversationId == conversationId &&
            it.draftId == draftId &&
            it.revision == revision &&
            it.baselineFingerprint == baselineFingerprint &&
            it.draftFingerprint == draftFingerprint
    }
}

/** Computes the same project baseline used by existing proposals without retaining a proposal or writing a source. */
public class AiProjectBaselineService(
    private val projects: ProjectRegistry,
    private val loader: ProjectLoader = ProjectLoader(),
    private val proposalCreator: ProposalCreator = ProposalCreator(),
) {
    public fun current(projectId: String, targetSourceId: String): String {
        val project = load(projectId)
        val triple = project.graph.triples.sortedBy { it.toString() }.firstOrNull()
            ?: throw AiDraftFailure("empty-project-graph", "A project baseline cannot be created for an empty graph.")
        val probe = ChangeSet(listOf(GraphChange(GraphChangeKind.Removal, triple)))
        return when (val result = proposalCreator.createProposal(project, targetSourceId, probe, "ai-baseline-probe", "AI baseline probe")) {
            is EntioResult.Failure -> throw AiDraftFailure("baseline-fingerprint-failed", result.message)
            is EntioResult.Success -> result.value.baseline.projectFingerprint
        }
    }

    internal fun load(projectId: String): EntioProject = when (val result = loader.loadProject(projects.rootFor(projectId))) {
        is EntioResult.Failure -> throw AiDraftFailure("project-load-failed", result.message)
        is EntioResult.Success -> result.value
    }
}

public class AiDraftAnalysisService internal constructor(
    private val drafts: AiDraftStore,
    private val analyses: AiDraftAnalysisStore,
    private val baseline: AiProjectBaselineService,
    private val planner: WebProposalPlanner = WebProposalPlanner(),
    private val reasoning: ReasoningService = ReasoningService(),
    private val impactAnalyzer: ProposalImpactAnalyzer = ProposalImpactAnalyzer(),
    private val clock: Clock = Clock.systemUTC(),
) {
    @Synchronized
    public fun analyze(scope: AiCapabilityScope, draftId: String): AiDraftAnalysis {
        val draft = drafts.get(scope.userId, scope.projectId, scope.conversationId, draftId)
        if (draft.items.isEmpty()) throw AiDraftFailure("empty-private-draft", "Add a typed edit before running draft analysis.")
        val draftFingerprint = draft.draftFingerprint
            ?: throw AiDraftFailure("missing-draft-fingerprint", "The private draft has no deterministic fingerprint.")
        val revision = draft.revisions.maxOfOrNull(AiDraftRevision::revision) ?: 0
        val currentBaseline = baseline.current(scope.projectId, draft.allowedSourceIds.first())
        if (scope.baselineFingerprint != draft.baselineFingerprint || currentBaseline != draft.baselineFingerprint) {
            markStale(draft)
            throw AiDraftFailure("stale-draft-baseline", "The private draft baseline no longer matches the current project.")
        }
        analyses.findExact(
            scope.userId,
            scope.projectId,
            scope.conversationId,
            draft.id,
            revision,
            currentBaseline,
            draftFingerprint,
        )?.let { cached ->
            attachAnalysis(draft, cached)
            return cached
        }

        val project = baseline.load(scope.projectId)
        val entries = draft.items.map(::toStagedChange)
        val prepared = try {
            planner.prepare(project, entries)
        } catch (failure: WebWorkflowFailure) {
            return failedAnalysis(draft, revision, currentBaseline, draftFingerprint, failure.code, failure.message.orEmpty())
        }
        val currentReasoning = try {
            reason(project.graph, "current")
        } catch (failure: AiDraftFailure) {
            return failedAnalysis(draft, revision, currentBaseline, draftFingerprint, failure.code, failure.message.orEmpty())
        }
        val previewGraph = prepared.proposal.preview?.graph
            ?: return failedAnalysis(draft, revision, currentBaseline, draftFingerprint, "missing-preview", "The deterministic proposal preview was not created.")
        val previewReasoning = try {
            reason(previewGraph, "preview")
        } catch (failure: AiDraftFailure) {
            return failedAnalysis(
                draft,
                revision,
                currentBaseline,
                draftFingerprint,
                failure.code,
                failure.message.orEmpty(),
                prepared,
                currentReasoning,
            )
        }
        val reasoningImpact = when (
            val result = impactAnalyzer.analyze(project.graph, previewGraph, currentReasoning, previewReasoning, null, null)
        ) {
            is EntioResult.Failure -> return failedAnalysis(
                draft,
                revision,
                currentBaseline,
                draftFingerprint,
                "reasoning-impact-failed",
                result.message,
                prepared,
                currentReasoning,
                previewReasoning,
            )
            is EntioResult.Success -> result.value
        }
        val impact = combineImpact(prepared.impact, reasoningImpact)
        val validation = combineValidation(prepared.proposal.validationReport, impact.blockingMessages)
        val ready = validation.ok &&
            impact.status == BaselineImpactStatus.Safe &&
            currentReasoning.metadata.status == ReasoningRunStatus.Completed &&
            previewReasoning.metadata.status == ReasoningRunStatus.Completed &&
            prepared.proposal.status == ChangeProposalStatus.ReadyForReview
        val analysisId = analysisId(draft, revision, currentBaseline, draftFingerprint)
        val findings = findings(validation, impact)
        val analysis = AiDraftAnalysis(
            id = analysisId,
            draftId = draft.id,
            conversationId = draft.conversationId,
            userId = draft.userId,
            projectId = draft.projectId,
            revision = revision,
            baselineFingerprint = currentBaseline,
            draftFingerprint = draftFingerprint,
            status = if (ready) AiDraftAnalysisStatus.COMPLETED else AiDraftAnalysisStatus.BLOCKED,
            validationReport = validation,
            previewGraphFingerprint = previewReasoning.metadata.fingerprints.graphFingerprint,
            semanticDiff = prepared.proposal.diff,
            currentReasoning = currentReasoning,
            previewReasoning = previewReasoning,
            impact = impact,
            findings = findings,
            references = references(analysisId),
            readyForReview = ready,
            createdAt = clock.instant(),
        )
        analyses.put(analysis)
        attachAnalysis(draft, analysis)
        return analysis
    }

    private fun reason(graph: com.entio.core.GraphState, stage: String): ReasoningResult = when (val result = reasoning.reason(graph)) {
        is EntioResult.Failure -> throw AiDraftFailure("$stage-reasoning-failed", result.message)
        is EntioResult.Success -> result.value
    }

    private fun toStagedChange(item: AiDraftItem): StagedChange {
        val operation = item.operation as? AiTypedDraftOperation
            ?: throw AiDraftFailure("unsupported-draft-operation", "Draft item '${item.id}' is not an approved typed operation.")
        return StagedChange(
            id = item.id,
            order = item.order,
            targetSourceId = operation.targetSourceId,
            summary = operation.summary,
            operation = operation.preparedOperation,
            normalizedValues = operation.normalizedValues,
        )
    }

    private fun combineImpact(shacl: ProposalImpactReport, reasoned: ProposalImpactReport): ProposalImpactReport {
        val messages = (shacl.blockingMessages + reasoned.blockingMessages).distinct().sorted()
        val statuses = listOf(shacl.status, reasoned.status)
        val status = when {
            BaselineImpactStatus.Failed in statuses -> BaselineImpactStatus.Failed
            BaselineImpactStatus.Incomplete in statuses -> BaselineImpactStatus.Incomplete
            BaselineImpactStatus.BlocksApproval in statuses || messages.isNotEmpty() -> BaselineImpactStatus.BlocksApproval
            else -> BaselineImpactStatus.Safe
        }
        return ProposalImpactReport(
            explicitDiff = shacl.explicitDiff,
            reasoningImpact = reasoned.reasoningImpact,
            shaclImpact = shacl.shaclImpact,
            status = status,
            blockingMessages = messages,
        )
    }

    private fun combineValidation(existing: ValidationReport?, blockingMessages: List<String>): ValidationReport {
        val issues = existing?.issues.orEmpty() + blockingMessages.map { message ->
            ValidationIssue(ValidationSeverity.Error, "blocking-semantic-impact", message)
        }
        val normalized = issues.distinct().sortedWith(compareBy({ it.severity }, { it.code }, { it.source.orEmpty() }, { it.message }))
        return ValidationReport(
            status = if (normalized.any { it.severity == ValidationSeverity.Error }) ValidationStatus.Invalid else ValidationStatus.Valid,
            issues = normalized,
        )
    }

    private fun findings(validation: ValidationReport, impact: ProposalImpactReport): List<AiDraftFinding> {
        val validationFindings = validation.issues.map { issue ->
            AiDraftFinding(
                id = stableId(issue.code, issue.message, issue.source.orEmpty()),
                code = issue.code,
                severity = issue.severity,
                message = issue.message,
                source = issue.source,
            )
        }
        val impactFindings = impact.blockingMessages.map { message ->
            AiDraftFinding(stableId("impact", message), "blocking-semantic-impact", ValidationSeverity.Error, message)
        }
        return (validationFindings + impactFindings).distinctBy(AiDraftFinding::id).sortedBy(AiDraftFinding::id)
    }

    private fun failedAnalysis(
        draft: AiDraft,
        revision: Int,
        baselineFingerprint: String,
        draftFingerprint: String,
        code: String,
        message: String,
        prepared: PreparedWebProposal? = null,
        currentReasoning: ReasoningResult? = null,
        previewReasoning: ReasoningResult? = null,
    ): AiDraftAnalysis {
        val issue = ValidationIssue(ValidationSeverity.Error, code, message.ifBlank { "Draft analysis failed." })
        val report = ValidationReport(ValidationStatus.Invalid, listOf(issue))
        val id = analysisId(draft, revision, baselineFingerprint, draftFingerprint)
        val analysis = AiDraftAnalysis(
            id = id,
            draftId = draft.id,
            conversationId = draft.conversationId,
            userId = draft.userId,
            projectId = draft.projectId,
            revision = revision,
            baselineFingerprint = baselineFingerprint,
            draftFingerprint = draftFingerprint,
            status = AiDraftAnalysisStatus.FAILED,
            validationReport = report,
            previewGraphFingerprint = previewReasoning?.metadata?.fingerprints?.graphFingerprint,
            semanticDiff = prepared?.proposal?.diff,
            currentReasoning = currentReasoning,
            previewReasoning = previewReasoning,
            impact = prepared?.impact,
            findings = listOf(AiDraftFinding(stableId(code, issue.message), code, ValidationSeverity.Error, issue.message)),
            references = references(id),
            readyForReview = false,
            createdAt = clock.instant(),
        )
        analyses.put(analysis)
        attachAnalysis(draft, analysis)
        return analysis
    }

    private fun attachAnalysis(draft: AiDraft, analysis: AiDraftAnalysis): Unit {
        val current = drafts.get(draft.userId, draft.projectId, draft.conversationId, draft.id)
        if (current.draftFingerprint != analysis.draftFingerprint || current.baselineFingerprint != analysis.baselineFingerprint) {
            markStale(current)
            throw AiDraftFailure("stale-draft-analysis", "The private draft changed while analysis was running.")
        }
        drafts.update(
            current.copy(
                status = if (analysis.readyForReview) AiDraftStatus.READY_FOR_REVIEW else AiDraftStatus.INVALID,
                analysisReferenceIds = analysis.references.map(AiDraftAnalysisReference::id),
                updatedAt = clock.instant(),
            ),
        )
    }

    private fun markStale(draft: AiDraft): Unit {
        drafts.update(draft.copy(status = AiDraftStatus.STALE, analysisReferenceIds = emptyList(), updatedAt = clock.instant()))
    }

    private fun analysisId(draft: AiDraft, revision: Int, baseline: String, fingerprint: String): String =
        "draft-analysis-${stableId(draft.userId, draft.projectId, draft.conversationId, draft.id, revision.toString(), baseline, fingerprint)}"

    private fun references(analysisId: String): List<AiDraftAnalysisReference> = AiDraftAnalysisStage.entries.map { stage ->
        AiDraftAnalysisReference(stage, "$analysisId:${stage.name.lowercase()}")
    }
}

public sealed interface AiStructuredDraftCorrection {
    public val explanation: String

    public data class Update(
        val capabilityName: String,
        val arguments: AiUpdateDraftItemArguments,
        override val explanation: String,
    ) : AiStructuredDraftCorrection

    public data class Remove(
        val arguments: AiRemoveDraftItemArguments,
        override val explanation: String,
    ) : AiStructuredDraftCorrection
}

public data class AiDraftCorrectionRecord(
    val correctionNumber: Int,
    val explanation: String,
    val originalAnalysisId: String,
    val originalFindingIds: List<String>,
    val resultingRevision: Int,
    val resultingAnalysisId: String,
)

public data class AiDraftCorrectionOutcome(
    val draft: AiDraft,
    val analysis: AiDraftAnalysis,
    val records: List<AiDraftCorrectionRecord>,
    val limitReached: Boolean,
)

public class AiDraftSelfCorrectionController(
    private val workspace: AiPrivateDraftWorkspace,
    private val analysisService: AiDraftAnalysisService,
) {
    public fun correct(
        scope: AiCapabilityScope,
        draftId: String,
        policy: AiRunPolicy,
        corrections: List<AiStructuredDraftCorrection>,
        runId: String? = null,
    ): AiDraftCorrectionOutcome {
        var analysis = analysisService.analyze(scope, draftId)
        var draft = workspace.read(scope, draftId)
        val records = mutableListOf<AiDraftCorrectionRecord>()
        if (analysis.readyForReview) return AiDraftCorrectionOutcome(draft, analysis, emptyList(), false)

        corrections.take(policy.maxCorrectionCycles).forEachIndexed { index, correction ->
            val original = analysis
            draft = when (correction) {
                is AiStructuredDraftCorrection.Update -> workspace.update(
                    scope,
                    draftId,
                    correction.capabilityName,
                    correction.arguments,
                    runId,
                )
                is AiStructuredDraftCorrection.Remove -> workspace.remove(scope, draftId, correction.arguments)
            }
            analysis = analysisService.analyze(scope, draftId)
            records += AiDraftCorrectionRecord(
                correctionNumber = index + 1,
                explanation = correction.explanation,
                originalAnalysisId = original.id,
                originalFindingIds = original.findings.map(AiDraftFinding::id),
                resultingRevision = draft.revisions.maxOf(AiDraftRevision::revision),
                resultingAnalysisId = analysis.id,
            )
            if (analysis.readyForReview) return AiDraftCorrectionOutcome(draft, analysis, records, false)
        }
        return AiDraftCorrectionOutcome(
            draft = draft,
            analysis = analysis,
            records = records,
            limitReached = !analysis.readyForReview && corrections.size >= policy.maxCorrectionCycles,
        )
    }
}

public data class AiDraftAnalysisArguments(
    val draftId: String,
) : AiCapabilityArguments

public data class AiDraftAnalysisCapabilityPayload(
    val analysisId: String,
    val stage: AiDraftAnalysisStage,
    val referenceId: String,
    val status: AiDraftAnalysisStatus,
    val readyForReview: Boolean,
    val findingIds: List<String>,
)

public class AiDraftAnalysisCapabilityService(
    private val analyses: AiDraftAnalysisService,
) {
    public fun execute(invocation: AiDecodedCapabilityInvocation, scope: AiCapabilityScope): AiDraftAnalysisCapabilityPayload {
        val arguments = invocation.arguments as? AiDraftAnalysisArguments
            ?: throw AiCapabilityFailure("capability-service-mismatch", "This capability requires the draft analysis service.")
        val stage = when (invocation.definition.operationType) {
            AiCapabilityOperationType.DRAFT_VALIDATE -> AiDraftAnalysisStage.VALIDATION
            AiCapabilityOperationType.DRAFT_PREVIEW -> AiDraftAnalysisStage.PREVIEW
            AiCapabilityOperationType.DRAFT_REASON -> AiDraftAnalysisStage.REASONING
            AiCapabilityOperationType.DRAFT_SHACL -> AiDraftAnalysisStage.SHACL
            AiCapabilityOperationType.DRAFT_IMPACT -> AiDraftAnalysisStage.IMPACT
            else -> throw AiCapabilityFailure("capability-service-mismatch", "This capability is not a draft analysis operation.")
        }
        val analysis = analyses.analyze(scope, arguments.draftId)
        return AiDraftAnalysisCapabilityPayload(
            analysisId = analysis.id,
            stage = stage,
            referenceId = analysis.references.single { it.stage == stage }.id,
            status = analysis.status,
            readyForReview = analysis.readyForReview,
            findingIds = analysis.findings.map(AiDraftFinding::id),
        )
    }
}

internal fun draftAnalysisCapabilityDefinitions(): List<AiCapabilityDefinition> = listOf(
    analysisDefinition("entio_draft_validate", AiCapabilityOperationType.DRAFT_VALIDATE, "Validate the exact current private draft revision."),
    analysisDefinition("entio_draft_preview", AiCapabilityOperationType.DRAFT_PREVIEW, "Create and inspect the exact in-memory private draft preview."),
    analysisDefinition("entio_draft_reason", AiCapabilityOperationType.DRAFT_REASON, "Run deterministic OWL reasoning for the current private draft preview."),
    analysisDefinition("entio_draft_shacl", AiCapabilityOperationType.DRAFT_SHACL, "Run deterministic SHACL validation for the current private draft preview."),
    analysisDefinition("entio_draft_impact", AiCapabilityOperationType.DRAFT_IMPACT, "Inspect semantic diff, reasoning, SHACL, and blocking impact for the current private draft."),
)

private fun analysisDefinition(
    name: String,
    operationType: AiCapabilityOperationType,
    description: String,
): AiCapabilityDefinition = AiCapabilityDefinition(
    name = name,
    operationType = operationType,
    category = AiCapabilityCategory.ANALYSIS,
    description = description,
    inputSchema = AiObjectSchema(
        properties = listOf(AiSchemaProperty("draftId", AiStringSchema(maxLength = 128), description = "Current private draft ID.")),
        required = setOf("draftId"),
    ),
    access = AiCapabilityAccess.READ_ONLY,
    requiredRole = AiRequiredRole.CONTRIBUTOR,
    requiredPermissions = setOf(WebPermission.USE_AI.name, WebPermission.PREPARE_EDIT.name),
    requiredFeature = AiCapabilityFeatures.PRIVATE_DRAFT,
    sourceScope = AiSourceScopeRule.NONE,
    resultLimit = 1,
    timeoutMillis = 60_000,
    auditClassification = AiCapabilityAuditClassification.ANALYSIS,
    decoder = AiCapabilityArgumentDecoder(::decodeAnalysisArguments),
)

private fun decodeAnalysisArguments(input: JsonNode): AiCapabilityArguments {
    if (!input.isObject) throw AiCapabilityFailure("malformed-arguments", "Capability arguments must be a JSON object.")
    val fields = input.fieldNames().asSequence().toSet()
    if (fields != setOf("draftId")) {
        val code = if ("draftId" !in fields) "missing-argument" else "unknown-argument"
        throw AiCapabilityFailure(code, "Draft analysis requires only draftId.")
    }
    val value = input.get("draftId")
    if (!value.isTextual || value.textValue().isBlank() || value.textValue().length > 128) {
        throw AiCapabilityFailure("malformed-argument", "draftId must be a bounded non-empty string.")
    }
    return AiDraftAnalysisArguments(value.textValue())
}

private fun stableId(vararg values: String): String = MessageDigest.getInstance("SHA-256")
    .digest(values.joinToString("\u0000").toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
