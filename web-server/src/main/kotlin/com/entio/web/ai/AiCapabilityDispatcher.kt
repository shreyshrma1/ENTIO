package com.entio.web.ai

import com.fasterxml.jackson.databind.ObjectMapper

public data class AiToolExecution(
    val result: AiCapabilityResult,
    val providerOutput: String,
)

public interface AiCapabilityDispatcher {
    public fun execute(
        invocation: AiDecodedCapabilityInvocation,
        scope: AiCapabilityScope,
        context: AiCurrentScreenContext,
        draftId: String?,
        runId: String,
    ): AiToolExecution
}

/** Routes decoded invocations to existing bounded adapters; it performs no semantic work itself. */
public class DefaultAiCapabilityDispatcher(
    private val localReads: AiLocalReadCapabilityService? = null,
    private val semanticReads: AiSemanticReadCapabilityService? = null,
    private val drafts: AiPrivateDraftWorkspace? = null,
    private val draftAnalysis: AiDraftAnalysisCapabilityService? = null,
    private val objectMapper: ObjectMapper = ObjectMapper(),
) : AiCapabilityDispatcher {
    override fun execute(
        invocation: AiDecodedCapabilityInvocation,
        scope: AiCapabilityScope,
        context: AiCurrentScreenContext,
        draftId: String?,
        runId: String,
    ): AiToolExecution = when (invocation.arguments) {
        is AiSemanticJobArguments,
        is AiProposalReadArguments,
        is AiActivityReadArguments,
        is AiFiboSearchArguments,
        is AiFiboEntityArguments,
        -> semantic(invocation, scope)
        is AiAddDraftItemArguments,
        is AiUpdateDraftItemArguments,
        is AiRemoveDraftItemArguments,
        is AiReorderDraftItemsArguments,
        is AiUndoDraftArguments,
        is AiClearDraftArguments,
        -> mutateDraft(invocation, scope, requiredDraftId(draftId), runId)
        is AiDraftAnalysisArguments -> analyze(invocation, scope)
        else -> local(invocation, scope, context)
    }

    private fun local(
        invocation: AiDecodedCapabilityInvocation,
        scope: AiCapabilityScope,
        context: AiCurrentScreenContext,
    ): AiToolExecution {
        val execution = localReads?.execute(invocation, scope, context)
            ?: throw AiCapabilityFailure("capability-service-unavailable", "The local read capability service is unavailable.")
        return execution.result.toToolExecution(execution.payload)
    }

    private fun semantic(invocation: AiDecodedCapabilityInvocation, scope: AiCapabilityScope): AiToolExecution {
        val execution = semanticReads?.execute(invocation, scope)
            ?: throw AiCapabilityFailure("capability-service-unavailable", "The semantic read capability service is unavailable.")
        return execution.result.toToolExecution(execution.payload)
    }

    private fun mutateDraft(
        invocation: AiDecodedCapabilityInvocation,
        scope: AiCapabilityScope,
        draftId: String,
        runId: String,
    ): AiToolExecution {
        val workspace = drafts
            ?: throw AiCapabilityFailure("capability-service-unavailable", "The private draft capability service is unavailable.")
        val draft = when (val arguments = invocation.arguments) {
            is AiAddDraftItemArguments -> workspace.add(scope, draftId, invocation.definition.name, arguments, runId)
            is AiUpdateDraftItemArguments -> workspace.update(scope, draftId, invocation.definition.name, arguments, runId)
            is AiRemoveDraftItemArguments -> workspace.remove(scope, draftId, arguments)
            is AiReorderDraftItemsArguments -> workspace.reorder(scope, draftId, arguments)
            is AiUndoDraftArguments -> workspace.undo(scope, draftId, arguments)
            is AiClearDraftArguments -> workspace.clear(scope, draftId, arguments)
            else -> throw AiCapabilityFailure("capability-service-mismatch", "This capability is not a private draft mutation.")
        }
        val revision = draft.revisions.maxOfOrNull(AiDraftRevision::revision) ?: 0
        val reference = "draft:${draft.id}:revision:$revision"
        val result = AiCapabilityResult(
            invocationId = invocation.invocationId,
            capabilityName = invocation.definition.name,
            status = AiCapabilityResultStatus.COMPLETED,
            summary = "Private draft ${draft.id} is ${draft.status.name.lowercase()} at revision $revision with ${draft.items.size} item(s).",
            resultReferenceIds = listOf(reference),
        )
        return result.toToolExecution(
            mapOf(
                "draftId" to draft.id,
                "status" to draft.status.name,
                "revision" to revision,
                "itemCount" to draft.items.size,
                "draftFingerprint" to draft.draftFingerprint,
                "referenceId" to reference,
            ),
        )
    }

    private fun analyze(invocation: AiDecodedCapabilityInvocation, scope: AiCapabilityScope): AiToolExecution {
        val payload = draftAnalysis?.execute(invocation, scope)
            ?: throw AiCapabilityFailure("capability-service-unavailable", "The private draft analysis service is unavailable.")
        val result = AiCapabilityResult(
            invocationId = invocation.invocationId,
            capabilityName = invocation.definition.name,
            status = when (payload.status) {
                AiDraftAnalysisStatus.COMPLETED,
                AiDraftAnalysisStatus.BLOCKED,
                -> AiCapabilityResultStatus.COMPLETED
                AiDraftAnalysisStatus.FAILED -> AiCapabilityResultStatus.FAILED
                AiDraftAnalysisStatus.STALE -> AiCapabilityResultStatus.STALE
            },
            summary = "Draft analysis ${payload.status.name.lowercase()} for ${payload.stage.name.lowercase()}.",
            resultReferenceIds = listOf(payload.referenceId),
        )
        return result.toToolExecution(payload)
    }

    private fun AiCapabilityResult.toToolExecution(payload: Any): AiToolExecution {
        val output = runCatching {
            objectMapper.writeValueAsString(mapOf("result" to this, "payload" to payload))
        }.getOrElse {
            objectMapper.writeValueAsString(
                mapOf(
                    "status" to status.name,
                    "summary" to summary,
                    "resultReferenceIds" to resultReferenceIds,
                ),
            )
        }
        return AiToolExecution(this, output)
    }

    private fun requiredDraftId(draftId: String?): String = draftId
        ?: throw AiCapabilityFailure("missing-private-draft", "A current private draft is required for this capability.")
}
