package com.entio.web.ai

import com.entio.core.ExternalEntityKind
import com.entio.core.Iri
import com.entio.web.CollaborationHub
import com.entio.web.FiboWebService
import com.entio.web.SemanticJobManager
import com.entio.web.StagingWorkflowService
import com.entio.web.WebFiboElement
import com.entio.web.WebReasoningFact
import com.entio.web.WebSemanticJobState
import com.entio.web.WebShaclFinding
import com.entio.web.contract.WebCollaborationEvent
import com.entio.web.contract.WebDiffEntry
import com.entio.web.contract.WebPageRequest

public data class AiSemanticFact(
    val kind: String,
    val subject: String,
    val predicate: String?,
    val objectValue: String,
    val provenance: AiFactProvenance,
    val sourceId: String?,
)

public data class AiShaclFinding(
    val resultId: String,
    val severity: String,
    val message: String,
    val focusNode: String,
    val path: String?,
    val shapeIri: String,
    val constraint: String,
    val sourceId: String?,
)

public data class AiSemanticJobPayload(
    val jobId: String,
    val kind: String,
    val scope: String,
    val state: String,
    val graphFingerprint: String,
    val proposalFingerprint: String?,
    val consistency: String?,
    val facts: List<AiSemanticFact>,
    val unsatisfiableClasses: List<String>,
    val shaclFindings: List<AiShaclFinding>,
    val explanationsState: String,
    val warnings: List<String>,
    val errors: List<String>,
    override val evidence: List<AiEvidenceReference>,
    override val truncated: Boolean,
) : AiCapabilityPayload

public data class AiProposalDiffEntry(
    val kind: String,
    val subject: String,
    val predicate: String?,
    val objectValue: String?,
    val description: String,
)

public data class AiProposalReadPayload(
    val proposalId: String,
    val state: String,
    val baselineFingerprint: String?,
    val stagedChangeIds: List<String>,
    val validationMessages: List<String>,
    val diff: List<AiProposalDiffEntry>,
    val impactState: String,
    val impactMessage: String,
    override val evidence: List<AiEvidenceReference>,
    override val truncated: Boolean,
) : AiCapabilityPayload

public data class AiActivityEvent(
    val id: String,
    val sequence: Long,
    val type: String,
    val timestamp: String,
    val stagedChangeId: String?,
    val proposalId: String?,
    val jobId: String?,
)

public data class AiActivityPayload(
    val events: List<AiActivityEvent>,
    override val evidence: List<AiEvidenceReference>,
    override val truncated: Boolean,
) : AiCapabilityPayload

public data class AiFiboHit(
    val iri: String,
    val label: String,
    val kind: String,
    val moduleIri: String,
    val definitions: List<String>,
)

public data class AiFiboSearchPayload(
    val query: String,
    val hits: List<AiFiboHit>,
    override val evidence: List<AiEvidenceReference>,
    override val truncated: Boolean,
) : AiCapabilityPayload

public data class AiFiboDependency(
    val category: String,
    val requirement: String,
    val selection: String,
    val reason: String,
    val externalIri: String?,
    val label: String?,
)

public data class AiFiboEntityPayload(
    val entity: AiFiboHit,
    val alternateLabels: List<String>,
    val parents: List<String>,
    val domains: List<String>,
    val ranges: List<String>,
    val dependencies: List<AiFiboDependency>,
    override val evidence: List<AiEvidenceReference>,
    override val truncated: Boolean,
) : AiCapabilityPayload

/** Read-only adapters over retained semantic, workflow, collaboration, and pinned FIBO services. */
public class AiSemanticReadCapabilityService(
    private val semanticJobs: SemanticJobManager,
    private val staging: StagingWorkflowService,
    private val collaboration: CollaborationHub,
    private val fibo: FiboWebService,
) {
    public fun execute(
        invocation: AiDecodedCapabilityInvocation,
        scope: AiCapabilityScope,
    ): AiCapabilityExecution {
        val payload = when (val arguments = invocation.arguments) {
            is AiSemanticJobArguments -> semanticJob(scope, arguments)
            is AiProposalReadArguments -> proposal(scope, arguments)
            is AiActivityReadArguments -> activity(scope, arguments)
            is AiFiboSearchArguments -> fiboSearch(scope, arguments)
            is AiFiboEntityArguments -> fiboEntity(scope, arguments)
            else -> throw AiCapabilityFailure("capability-service-mismatch", "This capability requires the local read service.")
        }
        val status = when {
            payload is AiSemanticJobPayload && payload.state == WebSemanticJobState.Stale.name -> AiCapabilityResultStatus.STALE
            payload.truncated -> AiCapabilityResultStatus.LIMIT_REACHED
            else -> AiCapabilityResultStatus.COMPLETED
        }
        return AiCapabilityExecution(
            AiCapabilityResult(
                invocationId = invocation.invocationId,
                capabilityName = invocation.definition.name,
                status = status,
                summary = payload.summary(),
                resultReferenceIds = payload.evidence.map(AiEvidenceReference::id),
            ),
            payload,
        )
    }

    public fun semanticJob(scope: AiCapabilityScope, arguments: AiSemanticJobArguments): AiSemanticJobPayload {
        val details = semanticJobs.details(scope.projectId, arguments.jobId, arguments.limit)
            ?: throw AiCapabilityFailure("missing-semantic-job", "The requested semantic job was not found in the current project.")
        val current = details.job.status == WebSemanticJobState.Completed
        val visibleFacts = if (current) details.facts.filter { it.sourceId in scope.allowedSourceIds } else emptyList()
        val visibleFindings = if (current) details.shaclFindings.filter { it.sourceId in scope.allowedSourceIds } else emptyList()
        val omittedScopedResults = current && (
            visibleFacts.size != details.facts.size || visibleFindings.size != details.shaclFindings.size
            )
        val visibleIris = visibleFacts.flatMap { listOf(it.subject, it.objectValue) }.toSet()
        val unsatisfiable = if (current) details.unsatisfiableClasses.filter(visibleIris::contains) else emptyList()
        val warnings = buildList {
            addAll(details.warnings)
            if (!current) add("Results are not exposed because the semantic job is ${details.job.status.name.lowercase()}.")
            if (omittedScopedResults) add("Results outside the current source scope were omitted.")
            if (details.unsatisfiableClasses.size > unsatisfiable.size) add("Unsatisfiable classes without allowed-source evidence were omitted.")
            add("Detailed reasoning explanations are unavailable because this job did not retain explanation artifacts.")
        }
        val facts = visibleFacts.map(WebReasoningFact::toAiFact)
        val findings = visibleFindings.map(WebShaclFinding::toAiFinding)
        return AiSemanticJobPayload(
            jobId = details.job.id,
            kind = details.job.kind.name,
            scope = details.job.scope.name,
            state = details.job.status.name,
            graphFingerprint = details.job.graphFingerprint,
            proposalFingerprint = details.job.proposalFingerprint,
            consistency = details.job.resultSummary["consistency"]?.toString(),
            facts = facts,
            unsatisfiableClasses = unsatisfiable,
            shaclFindings = findings,
            explanationsState = "UNAVAILABLE",
            warnings = warnings.distinct(),
            errors = details.errors,
            evidence = buildList {
                add(reference(scope, "semantic-job", details.job.id, details.job.kind.name, AiFactProvenance.APPLICATION))
                facts.forEach { fact -> add(reference(scope, "semantic-fact", fact.key(), fact.kind, fact.provenance, fact.sourceId)) }
                findings.forEach { finding -> add(reference(scope, "shacl-finding", finding.resultId, finding.message, AiFactProvenance.SHACL, finding.sourceId)) }
            },
            truncated = details.truncated || omittedScopedResults || details.unsatisfiableClasses.size > unsatisfiable.size,
        )
    }

    public fun proposal(scope: AiCapabilityScope, arguments: AiProposalReadArguments): AiProposalReadPayload {
        val snapshot = staging.snapshot(scope.projectId)
        val visibleEntries = snapshot.entries.filter { it.sourceId in scope.allowedSourceIds }
        val visibleIds = visibleEntries.map { it.id }.toSet()
        val proposal = snapshot.proposal
            ?.takeIf { it.stagedChangeIds.isNotEmpty() && it.stagedChangeIds.all(visibleIds::contains) }
            ?: throw AiCapabilityFailure("missing-proposal", "No current proposal is fully visible in the current source scope.")
        if (arguments.proposalId != null && arguments.proposalId != proposal.id) {
            throw AiCapabilityFailure("missing-proposal", "The requested proposal is not the current visible proposal.")
        }
        val messages = proposal.validationMessages.take(arguments.limit)
        val diff = proposal.diff.take(arguments.limit).map(WebDiffEntry::toAiDiff)
        val truncated = proposal.validationMessages.size > messages.size || proposal.diff.size > diff.size
        return AiProposalReadPayload(
            proposalId = proposal.id,
            state = proposal.status,
            baselineFingerprint = proposal.baselineProjectFingerprint,
            stagedChangeIds = proposal.stagedChangeIds,
            validationMessages = messages,
            diff = diff,
            impactState = "UNAVAILABLE",
            impactMessage = "The current web workflow does not retain a separate Phase 4 proposal-impact report.",
            evidence = listOf(reference(scope, "proposal", proposal.id, proposal.status, AiFactProvenance.PROPOSAL)),
            truncated = truncated,
        )
    }

    public fun activity(scope: AiCapabilityScope, arguments: AiActivityReadArguments): AiActivityPayload {
        val snapshot = collaboration.recentSharedActivity(scope.projectId, arguments.limit)
        val events = snapshot.events.map(WebCollaborationEvent::toAiActivity)
        return AiActivityPayload(
            events = events,
            evidence = events.map { event -> reference(scope, "activity", event.id, event.type, AiFactProvenance.APPLICATION) },
            truncated = snapshot.truncated,
        )
    }

    public fun fiboSearch(scope: AiCapabilityScope, arguments: AiFiboSearchArguments): AiFiboSearchPayload {
        val response = fibo.search(
            projectId = scope.projectId,
            text = arguments.query,
            kind = arguments.kind?.toExternalKind(),
            moduleIri = arguments.moduleIri?.let(::Iri),
            curatedOnly = true,
            request = WebPageRequest(limit = arguments.limit),
        )
        val hits = response.page.items.map(WebFiboElement::toAiHit)
        return AiFiboSearchPayload(
            query = arguments.query,
            hits = hits,
            evidence = hits.map { hit -> reference(scope, "fibo-entity", hit.iri, hit.label, AiFactProvenance.EXTERNAL) },
            truncated = response.page.nextOffset != null,
        )
    }

    public fun fiboEntity(scope: AiCapabilityScope, arguments: AiFiboEntityArguments): AiFiboEntityPayload {
        val response = try {
            fibo.details(scope.projectId, Iri(arguments.entityIri))
        } catch (failure: IllegalArgumentException) {
            throw AiCapabilityFailure("missing-fibo-entity", failure.message ?: "The requested FIBO entity was not found.")
        }
        val element = response.element
        val dependencies = response.dependencies.take(MAX_FIBO_DEPENDENCIES).map {
            AiFiboDependency(it.category, it.requirement, it.selection, it.reason, it.externalIri, it.label)
        }
        return AiFiboEntityPayload(
            entity = element.toAiHit(),
            alternateLabels = element.alternateLabels.take(MAX_DESCRIPTORS),
            parents = element.parents.take(MAX_DESCRIPTORS),
            domains = element.domains.take(MAX_DESCRIPTORS),
            ranges = element.ranges.take(MAX_DESCRIPTORS),
            dependencies = dependencies,
            evidence = listOf(reference(scope, "fibo-entity", element.iri, element.label, AiFactProvenance.EXTERNAL)),
            truncated = response.dependencies.size > dependencies.size || element.alternateLabels.size > MAX_DESCRIPTORS ||
                element.parents.size > MAX_DESCRIPTORS || element.domains.size > MAX_DESCRIPTORS || element.ranges.size > MAX_DESCRIPTORS,
        )
    }

    private fun reference(
        scope: AiCapabilityScope,
        kind: String,
        key: String,
        label: String,
        provenance: AiFactProvenance,
        sourceId: String? = null,
    ): AiEvidenceReference = AiEvidenceReference(
        id = stableReferenceId(scope.projectId, kind, key, sourceId.orEmpty(), scope.baselineFingerprint),
        label = label,
        kind = kind,
        provenance = provenance,
        sourceId = sourceId,
    )

    private companion object {
        const val MAX_DESCRIPTORS: Int = 20
        const val MAX_FIBO_DEPENDENCIES: Int = 20
    }
}

private fun WebReasoningFact.toAiFact(): AiSemanticFact = AiSemanticFact(
    kind,
    subject,
    predicate,
    objectValue,
    if (origin.equals("Inferred", ignoreCase = true)) AiFactProvenance.INFERRED else AiFactProvenance.ASSERTED,
    sourceId,
)

private fun AiSemanticFact.key(): String = listOf(kind, subject, predicate, objectValue).joinToString("|")

private fun WebShaclFinding.toAiFinding(): AiShaclFinding = AiShaclFinding(
    resultId,
    severity,
    message,
    focusNode,
    path,
    shapeIri,
    constraint,
    sourceId,
)

private fun WebDiffEntry.toAiDiff(): AiProposalDiffEntry = AiProposalDiffEntry(kind, subject, predicate, objectValue, description)

private fun WebCollaborationEvent.toAiActivity(): AiActivityEvent = AiActivityEvent(
    eventId,
    sequence,
    eventType,
    timestamp,
    stagedChangeId,
    proposalId,
    jobId,
)

private fun WebFiboElement.toAiHit(): AiFiboHit = AiFiboHit(iri, label, kind, moduleIri, definitions.take(3))

private fun AiFiboEntityKind.toExternalKind(): ExternalEntityKind = when (this) {
    AiFiboEntityKind.CLASS -> ExternalEntityKind.Class
    AiFiboEntityKind.OBJECT_PROPERTY -> ExternalEntityKind.ObjectProperty
    AiFiboEntityKind.DATATYPE_PROPERTY -> ExternalEntityKind.DatatypeProperty
}
