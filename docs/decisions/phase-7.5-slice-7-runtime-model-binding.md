# Phase 7.5 Slice 7 Completion Record

## ExecPlan Slice Implemented

Slice 7: Runtime Model Binding And Unavailable-Model Recovery.

## Goal

Require a fresh verified selection when a run starts, bind that immutable model and server request policy to the run, and recover safely if provider access disappears.

## Files Modified

- Added immutable run model bindings and corresponding audit metadata.
- Added a production resolver backed by per-user provider settings and a fixed resolver for deterministic providers.
- Wired verified selection into run creation and every provider request in a tool loop.
- Made the OpenAI Responses adapter accept only a validated runtime model ID while retaining server-owned endpoint, storage, prompt, retry, and timeout policy.
- Added model-unavailable/access-denied classification and selection refresh recovery.
- Updated focused conversation, provider, audit, model-availability, and web compatibility tests.

## Tests Added Or Updated

- Missing, unverified, and stale selections are rejected with `AI_MODEL_SELECTION_REQUIRED`.
- A multi-request tool loop retains one model even when selection changes concurrently; a later run uses the new verified selection.
- Run and audit records capture model ID, catalog version, prompt version, credential generation, and request-policy version without secrets.
- Model access loss fails the run, preserves conversation/private-draft state, marks selection unavailable, refreshes discovery, and performs no fallback.
- OpenAI payload tests prove the run-bound model is used without changing server request policy.

## Verification

- `./gradlew :web-server:test --tests '*AiConversationServiceTest' --tests '*OpenAiResponsesClientTest' --tests '*AiModelAvailability*Test'` — passed.
- `./gradlew :web-server:test` — passed.
- `git diff --check` — passed with no whitespace errors.

## Git Commit

A focused Slice 7 commit was created on `feature/phase-7.5-slice-7-runtime-model-binding` and includes runtime binding, recovery, tests, and this record.

## Assumptions, Limitations, And Follow-Up

- Model selection is resolved only for a new run. Resuming a paused run retains its original binding.
- Recovery refresh is best effort: the failed run remains failed and no replacement model is selected automatically.
- Existing conversations are not themselves authoritative for model choice; each completed run and audit records the model actually used.
- Broader rate-limit, log-capture, and privacy regression hardening belongs to Slice 8.

## Notable Implementation Decisions

- Runtime model identifiers are catalog-derived and syntax checked at the OpenAI boundary; clients cannot supply them in conversation requests.
- The binding includes credential generation so access-loss recovery cannot invalidate a newer credential or selection.
- Both HTTP and streamed `model_not_found` failures map to model-unavailable recovery.
- Provider endpoint, `store=false`, prompt version, tools, timeouts, and retries remain server controlled.
