# Phase 7 Slice 3: OpenAI Provider

## ExecPlan Slice

Slice 3 of `docs/execplans/0012-phase-7-tool-driven-native-ai-ontology-copilot.md`.

## Goal

Add the approved server-side OpenAI Responses adapter with explicit configuration, strict custom-function serialization, safe event parsing, cancellation, timeout and retry behavior, usage mapping, and credential redaction without executing tools.

## Files Modified

- `web-server/build.gradle.kts`
- `web-server/src/main/kotlin/com/entio/web/ai/AiProviderContracts.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/OpenAiResponsesClient.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/OpenAiResponsesClientTest.kt`
- `docs/decisions/phase-7-slice-3-openai-provider.md`

## Implemented Behavior

- Added Ktor client dependencies using the existing repository Ktor version and deterministic `MockEngine` test support.
- Added validated provider configuration fixed to provider `openai`, model `gpt-5.2`, endpoint `https://api.openai.com/v1/responses`, `store: false`, a ten-second connect timeout, a 120-second request timeout, and at most two retries.
- Added server-only Responses requests containing trusted policy, bounded user input, and custom function tools generated from the current capability registry.
- Serialized every function with strict mode, all properties required, nullable optional fields, bounded schema constraints, and `additionalProperties: false`.
- Added streaming parsing for text, function arguments, completed function calls, terminal completion and usage, refusal, incomplete, cancellation, and provider errors.
- Added structured redacted error classification for invalid credentials, rate limits, timeouts, provider availability, malformed responses, refusals, incomplete responses, cancellation, and other provider failures.
- Made credential testing suspendable and safe while retaining the deterministic development provider.
- Kept function calls as parsed requests only; this slice does not execute any tool.

## Tests And Verification

Mock-engine tests cover request shape, fixed configuration, strict functions, function-call parsing, usage, retries, status classification, redaction, malformed streams, refusal, incomplete responses, cancellation, and safe credential testing. No live API key or external network is used.

Verification commands:

- `./gradlew :web-server:test`
- `./gradlew test`
- `./gradlew :web-server:build`
- `git diff --check`

## Commit

The implementation and this completion artifact are included in the focused Slice 3 commit reported in the completion summary.

## Assumptions And Limitations

- Provider configuration is explicit and server-owned; users cannot select arbitrary models or endpoints.
- The adapter stores no OpenAI response through the API and does not expose built-in tools.
- Function calls are parsed but intentionally not executed until the approved conversation/tool-loop slice.
- The existing Phase 6 assistant interface remains available through a compatibility mapping while Phase 7 orchestration is introduced later.
- Provider response bodies and headers are never copied into user-visible failures.
