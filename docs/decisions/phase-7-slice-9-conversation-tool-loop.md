# Phase 7 Slice 9 Conversation Tool Loop

## ExecPlan Slice

Slice 9: Reciprocal Conversation And Bounded Tool Loop.

## Goal

Add application-owned conversation reconstruction, deterministic intent gates, and a sequential OpenAI tool loop over the approved capability registry, private draft workspace, and deterministic analysis services.

## Files Modified

- `web-server/src/main/kotlin/com/entio/web/ai/AiCapabilityDispatcher.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiConversationService.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiSessionContracts.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiSessionStores.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/OpenAiResponsesClient.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiConversationServiceTest.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiSessionContractsTest.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/OpenAiResponsesClientTest.kt`
- `docs/decisions/phase-7-slice-9-conversation-tool-loop.md`

## Implementation Result

- Added deterministic intent classification for explanations, focused edits, broad plans, clarification, draft management, analysis, help, and out-of-scope requests.
- Added explicit plan-confirmation and clarification state machines that block draft mutation until the user resolves the boundary decision.
- Added application-owned bounded transcript reconstruction; provider response IDs remain supplemental transport context.
- Added a sequential tool loop that uses one immutable capability-registry snapshot for the entire run.
- Added strict dispatch to existing local-read, semantic-read, private-draft, and draft-analysis adapters.
- Returned ordered function outputs to the provider without enabling parallel tools, provider built-in tools, or recursive AI calls.
- Added cancellation propagation and limits for provider requests, capability calls, draft edits, context messages, elapsed time, and token usage.
- Added stable run events and audit records while keeping all draft changes private and out of shared staging.

## Tests Added Or Updated

- Verified focused requests prepare private typed edits and return tool outputs in call order.
- Verified broad requests pause for plan confirmation before creating a draft.
- Verified material ambiguity pauses for clarification and resumes with bounded conversation context.
- Verified follow-up explanation, draft revision, and undo requests retain the current conversation and private draft.
- Verified unknown, duplicate, replayed, and unauthorized calls fail without duplicate mutation.
- Verified provider-request, capability-call, draft-edit, context, elapsed-time, input-token, and output-token limits stop safely.
- Verified provider cancellation reaches the in-flight request and leaves the run authoritatively cancelled.
- Verified OpenAI request serialization preserves ordered function outputs and treats response IDs as optional continuation hints.

## Verification

- `./gradlew :web-server:test --tests com.entio.web.ai.AiConversationServiceTest --tests com.entio.web.ai.AiSessionContractsTest --tests com.entio.web.ai.OpenAiResponsesClientTest` - passed.
- `./gradlew :web-server:test` - passed.
- `./gradlew test` - passed.
- `./gradlew build` - passed.
- `git diff --check` - passed.

## Git Commit

A focused Slice 9 commit was created on `feature/phase-7-slice-9-conversation-tool-loop`.

## Assumptions And Limitations

- Conversation, run, audit, and private draft state remain in-memory at this slice.
- `READY_FOR_REVIEW` means the bounded provider turn completed safely; submission to shared human review is intentionally deferred to Slice 10.
- Intent classification is a deterministic application boundary and does not replace semantic validation or provider reasoning.

## Notable Decisions

- Entio reconstructs bounded conversation context from its own store on every provider request.
- One immutable registry snapshot authorizes every call in a run; provider output cannot expand scope or permissions.
- Tool calls execute sequentially and duplicate or replayed call IDs fail closed.
- No conversation path can approve, apply, or submit a private draft automatically.
