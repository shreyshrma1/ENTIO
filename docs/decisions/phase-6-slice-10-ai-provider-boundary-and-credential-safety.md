# Phase 6 Slice 10: AI Provider Boundary And Credential Safety

## ExecPlan Slice

Slice 10 of `docs/execplans/0009-phase-6-collaborative-web-workbench-native-ai-foundation-execplan.md`.

## Goal

Create a provider-neutral server boundary for per-user AI credentials with safe status, explicit testing, replacement, removal, logout cleanup, and in-memory lifecycle behavior.

## Files Modified

- `web-server/src/main/kotlin/com/entio/web/ai/AiProviderContracts.kt`
- `web-server/src/main/kotlin/com/entio/web/Application.kt`
- `web-server/src/main/kotlin/com/entio/web/contract/WebContracts.kt`
- `web-server/src/test/kotlin/com/entio/web/AiCredentialTest.kt`
- `web-app/src/web/projectApi.ts`
- `web-app/src/web/queries.ts`
- `web-app/src/workbench/AiCredentialSettings.tsx`
- `web-app/src/workbench/AiCredentialSettings.test.tsx`
- `web-app/src/workbench/ProjectListPage.tsx`
- `web-app/src/styles.css`

## Implemented Behavior

- Added a provider-neutral `AiProviderClient` boundary and a development provider adapter with redacted success/failure messages.
- Added an in-memory server-only credential store keyed by development user ID.
- Added status, save/replace, explicit test, remove, and logout endpoints under `/api/v1/ai/...`.
- Ensured status and test responses never include the credential, and no credential is sent through React state after successful submission, URLs, logs, collaboration events, proposals, or ontology files.
- Added a frontend settings panel that displays status and test results while clearing the submitted credential input after save.
- Added explicit user-request gating: saving a credential does not call the provider; provider testing occurs only through the test action.

## Tests And Verification

Added server coverage for initial state, credential lifecycle, explicit test behavior, cross-user isolation, redacted provider failures, logout cleanup, and in-memory restart clearing. Added frontend coverage for status-only rendering and input clearing.

Passed:

- `./gradlew :web-server:test --no-daemon`
- `./gradlew test --no-daemon`
- `(cd web-app && npm test && npm run build)`

## Commit

The implementation commit is created on the Slice 10 branch and is reported with the implementation result.

## Assumptions And Limitations

- The default provider is a development-only provider-neutral boundary; no external AI network call or provider-specific semantics are introduced in this slice.
- Credential state is keyed by the existing development identity rather than a persistent production session system. Explicit logout and in-memory clearing cover the available lifecycle boundaries; production session expiration remains part of a later authentication boundary.
- Credentials are intentionally not persisted across server restart. Production secret storage is not introduced.
