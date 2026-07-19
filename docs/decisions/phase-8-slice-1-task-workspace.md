# Phase 8 Slice 1: Task Workspace Completion

## ExecPlan Slice Implemented

Phase 8 Slice 1, Task Contracts, State Machine, And In-Memory Workspace.

## Goal

Add authoritative immutable task/workspace contracts, Kotlin-enforced lifecycle transitions, revision-checked in-memory storage, and immutable initial scope/model provenance without adding provider, planning, execution, retrieval, analysis, route, or browser behavior.

## Files Modified

- `web-server/src/main/kotlin/com/entio/web/ai/AiTaskContracts.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiTaskStateMachine.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiTaskStore.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiTaskContractsTest.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiTaskStateMachineTest.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiTaskStoreTest.kt`
- `docs/architecture/ai-subsystem-map.md`
- `docs/decisions/phase-8-slice-1-task-workspace.md`

## Tests Added Or Updated

- Added contract and policy invariant coverage.
- Added exhaustive declared-transition coverage and workspace-mutation guards.
- Added deterministic normalization, revision compare-and-set, transition, immutable provenance, non-disclosing ownership, terminal-state, and process-restart store coverage.

## Verification Commands

```bash
./gradlew :web-server:test --tests '*AiTaskContractsTest' --tests '*AiTaskStateMachineTest' --tests '*AiTaskStoreTest'
./gradlew :web-server:test
git diff --check
```

## Verification Results

- Focused task contract/state/store tests: passed.
- Full `web-server` test suite: passed.
- `git diff --check`: passed.

## Git Commit

Yes. This completion artifact is included in the focused Slice 1 commit `Add Phase 8 task workspace foundation`.

## Assumptions And Limitations

- Task and workspace state is intentionally in memory and disappears on server restart.
- Slice 1 defines plan, package, draft, and analysis references only as workspace placeholders; it does not implement later-slice behavior.
- Exact elapsed-time defaults remain explicit policy values and may be adjusted only by the evidence-driven Phase 8 evaluation slice.

## Notable Decisions

- Task ownership failures use the same non-disclosing `missing-ai-task` response for absent, cross-user, and cross-project lookups.
- Initial task scope, policy, creation time, and model binding cannot change during workspace updates.
- Workspace updates use exact compare-and-set revisions and normalize bounded reference collections deterministically.
- Legal status transitions are a server-owned Kotlin table; provider or browser claims cannot bypass it.
