# Phase 7 Slice 1: AI Contracts

## ExecPlan Slice

Slice 1 of `docs/execplans/0010-phase-7-native-ai-assistant.md`.

## Goal

Define the server-owned contracts and in-memory state boundaries required for bounded Phase 7 AI conversations, runs, drafts, revisions, responses, and audit records without granting approval, apply, filesystem, or configuration authority.

## Files Modified

- `web-server/src/main/kotlin/com/entio/web/ai/AiAssistantContracts.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiProviderContracts.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiSessionContracts.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiSessionStores.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiSessionContractsTest.kt`
- `docs/decisions/phase-7-slice-1-ai-contracts.md`

## Implemented Behavior

- Added explicit conversation, message, run, capability-scope, draft, revision, response, warning, limit, next-action, and audit contracts.
- Added fixed run and draft states with guarded run transitions and cancellation support.
- Added approved default limits for capability calls, draft edits, correction cycles, active runs, local context, and FIBO candidates.
- Added deterministic in-memory conversation, run, draft, and audit stores with user, project, and conversation scope enforcement.
- Made the provider completion boundary suspendable so later provider calls can be cancelled by their owning coroutine.
- Added a suspend-safe credential access boundary that does not hold the credential-store monitor while provider work runs.
- Kept AI drafts limited to references to approved typed operations; the contracts contain no raw RDF, source-writing, approval, or apply capability.

## Tests And Verification

Added focused coverage for approved default limits, deterministic ordering, ownership isolation, active-run limits, run cancellation and transitions, draft conversation scope, revision ordering, and credential-field exclusion from response and audit contracts.

Verification commands:

- `./gradlew :web-server:test`
- `./gradlew test`
- `git diff --check`

## Commit

The implementation and this completion artifact are included in the focused Slice 1 commit reported in the completion summary.

## Assumptions And Limitations

- State remains in memory and is lost when the server restarts.
- Capability schemas and execution are introduced by later approved slices.
- The existing development user ID and project ID boundaries are the stable ownership keys available in Phase 7.
- Suspendable provider calls support cooperative cancellation; provider-specific timeout and network cancellation behavior belongs to the provider adapter slice.
- No AI contract can approve or apply ontology changes.
