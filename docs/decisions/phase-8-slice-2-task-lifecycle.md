# Phase 8 Slice 2: Task Lifecycle Completion

## ExecPlan Slice Implemented

Phase 8 Slice 2, Task Classification, Policy, Ownership, Limits, And Lifecycle Controls.

## Goal

Create proportionately classified tasks and enforce server-owned active-task, pause, resume, cancellation, stale, limit, and explicit model-rebind rules without adding retrieval, planning, execution, analysis, route, or browser behavior.

## Files Modified

- `web-server/src/main/kotlin/com/entio/web/ai/AiTaskClassifier.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiTaskContracts.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiTaskLifecycleService.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiTaskStateMachine.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiTaskStore.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiTaskClassifierTest.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiTaskLifecycleServiceTest.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiTaskPolicyTest.kt`
- `docs/decisions/phase-8-slice-2-task-lifecycle.md`

## Tests Added Or Updated

- Added representative family and size classification coverage with stable, bounded evidence.
- Added simple versus planned task marker and forbidden/recursive request coverage.
- Added mutating and read-only concurrency limit coverage.
- Added pause, resume, cancellation, terminal, stale, and preserved limit evidence coverage.
- Added unavailable-model pause and user-confirmed rebind provenance coverage.

## Verification Commands

```bash
./gradlew :web-server:test --tests '*AiTaskClassifierTest' --tests '*AiTaskLifecycleServiceTest' --tests '*AiTaskPolicyTest'
./gradlew :web-server:test
git diff --check
```

## Verification Results

- Focused classifier, lifecycle, and policy tests: passed.
- Full `web-server` test suite: passed.
- `git diff --check`: passed.

## Git Commit

Yes. This completion artifact is included in the focused Slice 2 commit `Add Phase 8 task lifecycle controls`.

## Assumptions And Limitations

- Classification is deterministic and permission-neutral; it cannot grant edit or reviewer authority.
- Lifecycle storage remains in memory, consistent with the approved Phase 8 boundary.
- Model verification and selection are supplied through the existing Phase 7 binding boundary; this slice records only an explicitly confirmed rebind.
- Retrieval, plan construction, package execution, and provider orchestration remain later-slice responsibilities.

## Notable Decisions

- Simple tasks enter `READY_TO_EXECUTE`; medium and large tasks enter `PLANNING` under server control.
- One active mutating task and three active read-only tasks are allowed per user/project.
- Model unavailability pauses the task without discarding workspace state and cannot resume without an explicit confirmed rebind.
- A rebind completes the prior execution segment, appends a new immutable provenance segment, and preserves the original task binding.
- Limit records remain attached to a safely stopped workspace for inspection and narrow follow-up.
