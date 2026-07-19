# Phase 8 Slice 6: Orchestration And Batching Completion

## ExecPlan Slice Implemented

Phase 8 Slice 6, Work-Package Orchestration, Composite Capabilities, And Draft Batching.

## Goal

Execute confirmed packages serially and append bounded, atomically prepared ordinary typed edits to one private task draft without exposing raw RDF, shared staging mutation, approval, application, or background queue behavior.

## Files Modified

- `docs/decisions/phase-8-slice-6-composite-capability-inventory.md`
- `web-server/src/main/kotlin/com/entio/web/ai/AiWorkPackageExecutor.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiCompositeCapabilityService.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiDraftBatchService.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiTypedEditCapabilities.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiSessionContracts.kt`
- matching executor, composite, and batch tests
- `docs/decisions/phase-8-slice-6-orchestration-batching.md`

## Tests Added Or Updated

- Added approved inventory subset and unlisted/unapproved/ambiguous composite rejection.
- Added parity with ordinary typed preparation and no shared-staging mutation.
- Added serial dependency execution, ordered progress, frozen binding/context/bundle, replay, blocked dependency, and interruption coverage.
- Added atomic invalid-batch rejection, multi-source preparation support, attribution, batch/task limits, and deterministic 50-edit coverage.
- Kept existing focused Phase 7 conversation tests in required verification.

## Verification Commands

```bash
./gradlew :web-server:test --tests '*AiWorkPackageExecutorTest' --tests '*AiCompositeCapabilityServiceTest' --tests '*AiDraftBatchServiceTest' --tests '*AiConversationServiceTest'
./gradlew :web-server:test
git diff --check
```

## Verification Results

- Focused executor, composite, batch, and compatibility tests: passed.
- Full `web-server` test suite: passed.
- `git diff --check`: passed.

## Git Commit

Yes. This artifact is included in `Add Phase 8 serial task orchestration`.

## Assumptions And Limitations

- Incremental analysis and repair remain hooks for later slices.
- Composite external reuse and entity refactoring remain clarification-required and are not exposed.
- Execution is synchronous and serial; there is no scheduler or external queue.

## Notable Decisions

- The mandatory inventory is the composite allowlist; only `APPROVED` entries can be exposed.
- Every batch operation is prepared and conflict-checked before one private-draft revision becomes visible.
- Draft attribution records task, package, execution segment, accepting user, conversation, and provider run.
- Provider/package/task limits safely stop execution while preserving the task workspace and private draft.
