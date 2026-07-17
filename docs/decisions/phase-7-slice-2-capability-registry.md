# Phase 7 Slice 2: Capability Registry

## ExecPlan Slice

Slice 2 of `docs/execplans/0012-phase-7-tool-driven-native-ai-ontology-copilot.md`.

## Goal

Define the allowlisted, scope-aware capability registry and strict typed argument boundary that later provider tool loops must use, before any semantic capability is executed.

## Files Modified

- `web-server/src/main/kotlin/com/entio/web/ai/AiCapabilityContracts.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiCapabilityRegistry.kt`
- `web-server/src/main/kotlin/com/entio/web/contract/DevelopmentIdentity.kt`
- `web-server/src/main/kotlin/com/entio/web/contract/WebContracts.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiCapabilityRegistryTest.kt`
- `docs/decisions/phase-7-slice-2-capability-registry.md`

## Implemented Behavior

- Added stable capability categories, operation types, access classifications, scope rules, audit classes, confirmation metadata, invocation envelopes, and result envelopes.
- Added Kotlin-owned strict JSON schema contracts that prohibit additional properties and define required fields, nullable fields, bounded strings, arrays, integers, formats, and enums.
- Added typed decoders for initial project-summary, entity-detail, local-search, and Entio-help arguments.
- Added a scope factory that validates the registered project, development identity, available source descriptors, baseline, role, permissions, and features.
- Added an allowlisted registry that filters definitions for the current scope and signs snapshots with deterministic scope-and-definition fingerprints.
- Added per-invocation user, project, conversation, source, permission, feature, baseline, and registry-snapshot revalidation.
- Added the additive `USE_AI` web permission without granting approval, apply, rollback, configuration, filesystem, or arbitrary network authority.

## Tests And Verification

Focused tests cover valid typed decoding; unknown, missing, malformed, invalid-IRI, invalid-enum, and oversized arguments; scope construction; permission and feature filtering; stale snapshots; cross-scope invocation; source allowlists; and forbidden capability names.

Verification commands:

- `./gradlew :web-server:test`
- `./gradlew test`
- `git diff --check`

## Commit

The implementation and this completion artifact are included in the focused Slice 2 commit reported in the completion summary.

## Assumptions And Limitations

- This slice defines capability contracts and decoders only; it does not execute semantic reads or mutations.
- Available source IDs, baseline fingerprints, collaboration sessions, and feature flags are supplied by trusted server services when a run is created.
- Initial definitions establish the schema and authorization pattern; later slices add approved capabilities and implementations through this registry.
- No capability exposed here can approve, reject, apply, roll back, access secrets, or invoke raw RDF, SPARQL, files, shell commands, or arbitrary URLs.
