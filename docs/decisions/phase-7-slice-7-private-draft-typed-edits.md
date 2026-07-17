# Phase 7 Slice 7 Private Draft And Typed Edits

## ExecPlan Slice

Slice 7: Private AI Draft And Typed Edit Capabilities.

## Goal

Audit existing edit lifecycles, expose only proven typed ontology and SHACL capabilities, and provide a private, revisioned AI draft workspace that never mutates shared staging or source files.

## Files Modified

- `web-server/src/main/kotlin/com/entio/web/StagingWorkflowService.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiCapabilityContracts.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiCapabilityRegistry.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiLocalReadCapabilities.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiSessionContracts.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiTypedEditCapabilities.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiCapabilityRegistryTest.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiTypedEditCapabilitiesTest.kt`
- `docs/decisions/phase-7-slice-7-typed-edit-capability-inventory.md`
- `docs/decisions/phase-7-slice-7-private-draft-typed-edits.md`

## Implementation Result

- Added a side-effect-free internal preparation adapter that reuses the ordinary staging service's label resolution, IRI generation, source-role checks, deletion analysis, and typed ontology or SHACL operation construction.
- Added strict model-callable private draft schemas for approved ontology and bounded SHACL edits.
- Added private draft create, read, add, update, remove, reorder, undo, and clear behavior.
- Added deterministic revision snapshots and draft fingerprints.
- Added user, project, conversation, source, baseline, duplicate, conflict, dependency, and submitted-state protections.
- Preserved rationale and AI attribution for every typed draft item.
- Kept shared staging unchanged and provided no approval, rejection, application, rollback, raw RDF, Turtle, or SPARQL capability.
- Deferred metadata, external reuse, local subclassing, and advanced SHACL mutation where the complete private typed lifecycle could not be proven within the slice boundary.

## Tests Added Or Updated

- Added coverage for every approved ontology, deletion, and SHACL edit type.
- Added strict capability-schema coverage and raw-SHACL/raw-field rejection.
- Added deterministic add, update, reorder, undo, remove, and clear history coverage.
- Added cross-user, cross-conversation, stale baseline, source scope, duplicate, conflict, dependency, immutable external, and unsupported-operation coverage.
- Added assertions that shared staged state remains empty.

## Verification

- `./gradlew :web-server:test --tests 'com.entio.web.ai.AiTypedEditCapabilitiesTest' --tests 'com.entio.web.ai.AiCapabilityRegistryTest'` - passed.
- `./gradlew :web-server:clean :web-server:test` - passed.
- `./gradlew test` - passed.
- `git diff --check` - passed.

## Git Commit

A focused Slice 7 commit was created on `feature/phase-7-slice-7-private-draft-typed-edits`.

## Assumptions And Limitations

- A capability is approved only when the existing repository proves its complete typed proposal lifecycle.
- Metadata and external reuse contracts remain useful foundations, but their current web adapters are not private-draft safe and therefore are not advertised to the model.
- Private draft state remains server-memory state and does not represent durable history.

## Notable Decisions

- The ordinary staging preparation path was extracted narrowly rather than duplicated in AI code.
- Deletion cannot enter a private draft until every dependent statement has been explicitly selected.
- Bounded SHACL tools use only the five operations proven by Slice 6.
