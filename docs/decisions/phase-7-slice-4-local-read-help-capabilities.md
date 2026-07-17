# Phase 7 Slice 4: Local Read And Help Capabilities

## ExecPlan Slice

Slice 4 of `docs/execplans/0012-phase-7-tool-driven-native-ai-ontology-copilot.md`.

## Goal

Expose bounded, read-only AI capabilities over existing Entio project, entity, hierarchy, search, workflow, permission, screen-context, and help metadata without widening project or source scope.

## Files Modified

- `web-server/src/main/kotlin/com/entio/web/ai/AiCapabilityContracts.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiCapabilityRegistry.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiLocalReadCapabilities.kt`
- `web-server/src/main/resources/entio-help/v1/help.json`
- `web-server/src/test/kotlin/com/entio/web/ai/AiCapabilityRegistryTest.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiLocalReadCapabilitiesTest.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/OpenAiResponsesClientTest.kt`
- `docs/decisions/phase-7-slice-4-local-read-help-capabilities.md`

## Implemented Behavior

- Added strict capability definitions for entity comparison, hierarchy neighborhoods, entity usage, current screen context, available actions and permissions, workflow state, and known error help.
- Added structured bounded payloads with stable evidence references and explicit asserted, external, staged, or application provenance.
- Added an immutable context-package builder that includes only the current project, selected entity, screen metadata, and bounded workflow state.
- Delegated ontology reads to `ReadOnlyProjectAdapter` and workflow reads to `StagingWorkflowService`; no semantic logic or mutation was added to routes or UI code.
- Constrained every optional source read to the run's allowed source IDs. Omitting a source does not grant project-wide source access.
- Added fixed, versioned Entio help loaded from `/entio-help/v1/help.json`. Model arguments can select only known topics and error codes, never a resource path.
- Filtered help actions and permissions against current server-created metadata so help cannot claim an action unavailable to the current run.

## Tests And Verification

Focused tests cover deterministic bounded results, strict schemas, comparison, hierarchy, usage, source exclusion, help and permission metadata, prompt-like ontology content as inert data, unknown entities, invalid screen actions, and unknown error codes.

Verification commands:

- `./gradlew :web-server:test`
- `./gradlew test`
- `git diff --check`

## Assumptions And Limitations

- This slice exposes asserted local descriptor context. Inferred facts, reasoning, SHACL, proposals, activity, and FIBO are added by the next approved read-capability slice.
- Screen context is supplied by the trusted server boundary rather than decoded from model arguments.
- Results are intentionally bounded; a `LIMIT_REACHED` result instructs later orchestration to request a narrower query rather than widening context automatically.
- Help text is product documentation data, not provider policy or authority over deterministic Entio behavior.
