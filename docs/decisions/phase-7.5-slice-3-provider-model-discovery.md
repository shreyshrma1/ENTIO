# Phase 7.5 Slice 3 Completion Record

## ExecPlan Slice Implemented

Slice 3: Provider-Neutral Discovery And OpenAI Model-List Adapter.

## Goal

Discover models through a user's callback-scoped credential, immediately map OpenAI payloads into provider-neutral descriptors, and expose harmless model verification plus a model-aware runtime compatibility seam.

## Files Modified

- Added provider-neutral discovery, verification, error, and deterministic-fixture contracts under `web-server/src/main/kotlin/com/entio/web/ai/provider/`.
- Added the fixed-host `OpenAiModelDiscoveryClient` under `provider/openai/`.
- Added focused Ktor `MockEngine` provider tests.
- Added an additive model-aware overload to the existing `AiToolLoopProvider`; existing execution continues to use its configured model until Slice 7.
- Added this completion record.

## Tests Added Or Updated

`OpenAiModelDiscoveryClientTest` covers fixed method/host/path/authorization behavior, zero/multiple/unknown inventories, deduplication, provider-field removal, malformed JSON, 401/403/429/5xx, timeout, transport failure, cancellation, redirect rejection, redaction, minimal verification, model-access loss, pre-call ID validation, and deterministic provider fixtures.

Existing `OpenAiResponsesClientTest` remains unchanged and passes through the additive compatibility seam.

## Verification

- `./gradlew :web-server:test --tests '*OpenAiModelDiscoveryClientTest' --tests '*OpenAiResponsesClientTest'` — passed.
- `./gradlew :web-server:test` — passed.
- `git diff --check` — passed with no whitespace errors.

## Git Commit

A focused Slice 3 commit was created on `feature/phase-7.5-slice-3-model-discovery-adapter` and includes the provider contracts, OpenAI adapter, tests, compatibility seam, and this record.

## Assumptions, Limitations, And Follow-Up

- The discovery adapter returns provider visibility evidence only; Slice 2 policy and later verification state determine usability.
- The verification request contains a harmless no-argument Entio function and no project or ontology context.
- Existing conversation execution retains the configured provider-instance model. Per-run selected-model execution belongs to Slice 7.
- No route, React state, user settings, persistence, or live network test is included.

## Notable Implementation Decisions

- Both OpenAI endpoints are exact HTTPS URLs on `api.openai.com`; redirects are disabled.
- Only model IDs cross out of the OpenAI model-list adapter.
- Provider errors retain safe category/retry information and discard raw provider messages, authorization headers, and credential content.
- Cancellation propagates so structured coroutine cancellation remains authoritative.
