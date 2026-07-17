# Phase 7 Slice 11 AI Web Contracts

## ExecPlan Slice

Slice 11: Versioned AI HTTP And Private SSE Boundary.

## Goal

Expose Phase 7 conversations, messages, runs, cancellation, private drafts, deterministic analysis, human review submission, contextual help, and private run events through versioned project- and user-scoped web contracts.

## Files Modified

- `web-server/build.gradle.kts`
- `web-server/src/main/kotlin/com/entio/web/Application.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiConversationService.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiWebBoundary.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/OpenAiResponsesClient.kt`
- `web-server/src/main/kotlin/com/entio/web/contract/AiWebContracts.kt`
- `web-server/src/main/kotlin/com/entio/web/contract/WebContracts.kt`
- `web-server/src/test/kotlin/com/entio/web/AiAssistantTest.kt`
- `web-server/src/test/kotlin/com/entio/web/AiCredentialTest.kt`
- `web-server/src/test/kotlin/com/entio/web/AiWebContractTest.kt`
- `docs/decisions/phase-7-slice-11-ai-web-contracts.md`

## Implementation Result

- Added versioned HTTP routes and transport DTOs for conversation lifecycle, messages, runs, cancellation, private drafts, deterministic analysis stages, review submission, and contextual help.
- Kept capability scope, project roots, source paths, permissions, feature availability, provider choice, model ID, and baseline fingerprints server-owned.
- Added request idempotency for message and review-submission retries so replay does not duplicate messages, private edits, staged changes, or proposals.
- Added a private authenticated SSE route with monotonically increasing per-run IDs, the latest 250 safe events retained in memory, `Last-Event-ID` reconnect, and an explicit resynchronization signal with authoritative HTTP recovery routes.
- Kept private AI events off the project collaboration WebSocket and mapped cross-user or cross-project access to non-disclosing scope failures.
- Added structured HTTP status mapping for malformed requests, missing resources, authorization failures, stale state, conflicts, and incomplete review state.
- Exposed ISO-8601 timestamps as strings so web contracts do not leak JVM date types or require an additional serialization dependency.
- Wired the approved OpenAI Responses adapter as the production default while retaining an explicit deterministic development tool-loop fallback for focused tests and compatibility paths.
- Preserved the Phase 6 single-request assistant route and verified that it remains operational with explicit provider injection.
- Kept route handlers as transport orchestration only; deterministic ontology preparation, analysis, review submission, and staging remain in reusable Kotlin services.

## Tests Added Or Updated

- Verified full conversation creation, focused typed-edit drafting, deterministic analysis, human review submission, and source preservation before review application.
- Verified repeated message and submission requests return the original result without duplicate provider calls, messages, edits, staged entries, or proposals.
- Verified cross-user conversation and event access returns scope-safe failures without private response content.
- Verified SSE ordering, terminal completion, reconnect from a retained event, out-of-window resynchronization, cancellation events, and credential/provider-data redaction.
- Verified malformed idempotency requests and unknown projects use structured status responses.
- Verified contextual help and the Phase 6 assistant compatibility route.
- Updated Phase 6 credential and assistant tests to inject their deterministic provider explicitly now that the approved OpenAI adapter is the server default.

## Verification

- `./gradlew :web-server:test` - passed after a clean rebuild removed stale ignored duplicate bytecode.
- `./gradlew test` - passed.
- `./gradlew :web-server:build` - passed.
- `git diff --check` - passed.

## Git Commit

A focused Slice 11 commit was prepared on `feature/phase-7-slice-11-ai-web-contracts` after all required verification passed.

## Assumptions And Limitations

- Conversations, runs, drafts, idempotency records, and retained SSE events remain in server memory for the current server lifetime.
- SSE retains the latest 250 safe events per run; clients whose cursor is outside that window must refetch authoritative conversation, run, and draft state over HTTP.
- The deterministic analysis aliases currently return the same complete analysis aggregate because the underlying service executes the approved validation, preview, reasoning, SHACL, and impact sequence as one server-owned operation.
- Browser conversation and draft-review UI work is deferred to Slice 12.

## Notable Decisions

- Private AI progress uses a user-scoped SSE channel rather than the shared collaboration WebSocket.
- Review submission remains an explicit human action and does not expose approval, application, or rollback authority to the model.
- The server creates every capability scope from registered project, identity, authorization, feature, and baseline state; clients cannot submit or widen scope.
