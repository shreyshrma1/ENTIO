# Phase 8 Slice 4: Context And Capability Bundles Completion

## ExecPlan Slice Implemented

Phase 8 Slice 4, Task Context Packages And Stage Capability Bundles.

## Goal

Expose exactly one frozen, server-selected capability bundle per provider step and assemble deterministic fingerprint-current task context within explicit entity, FIBO, SHACL, source, and byte bounds.

## Files Modified

- `web-server/src/main/kotlin/com/entio/web/ai/AiCapabilityBundleRegistry.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiTaskContextPackageBuilder.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiCapabilityBundleRegistryTest.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiTaskContextPackageBuilderTest.kt`
- `docs/decisions/phase-8-slice-4-context-and-bundles.md`

## Tests Added Or Updated

- Added bundle version, contents, and server-owned stage mapping coverage.
- Added mutation-free exploration/help and approval/application absence assertions.
- Added frozen snapshot, cross-bundle, and stale-scope rejection coverage.
- Added deterministic task-context ordering, source, entity, FIBO, SHACL, byte, and fingerprint bounds.
- Added untrusted project-data and prompt-injection boundary coverage.

## Verification Commands

```bash
./gradlew :web-server:test --tests '*AiCapabilityBundleRegistryTest' --tests '*AiTaskContextPackageBuilderTest' --tests '*AiCapabilityRegistryTest' --tests '*OpenAiResponsesClientTest'
./gradlew :web-server:test
git diff --check
```

## Verification Results

- Focused bundle, context, registry, and provider request tests: passed.
- Full `web-server` test suite: passed.
- `git diff --check`: passed.

## Git Commit

Yes. This artifact is included in `Add Phase 8 task context bundles`.

## Assumptions And Limitations

- Planning capability definitions, execution, analysis orchestration, repair, routes, and UI remain later-slice work.
- Context inputs are structured, bounded outputs from prior slice services.
- Project-provided summaries are explicitly delimited as untrusted data and cannot change server-selected rules or bundle membership.

## Notable Decisions

- Bundle ID, version, and exact capability definitions are frozen into the issued registry snapshot.
- No bundle contains approval, rejection, apply, rollback, raw RDF/SPARQL, shell, filesystem, secrets, or unrestricted network capabilities.
- Initial context contains at most 20 entities and explicit expansion at most 50; repeated full expansion is rejected.
- Stale project maps or neighborhoods fail before provider serialization.
