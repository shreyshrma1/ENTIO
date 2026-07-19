# Phase 8 Slice 8: Repair And Revision Completion

## ExecPlan Slice Implemented

Phase 8 Slice 8, Repair Packets, Bounded Repair, Undo, And Evidence-Based Follow-Up.

## Goal

Convert deterministic findings into structured repair packets, permit only inventoried draft-only automatic repairs within fixed limits, support safe private-workspace revision, and ground follow-up context in authoritative task evidence.

## Files Modified

- `web-server/src/main/kotlin/com/entio/web/ai/AiRepairService.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiTaskRevisionService.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiTaskFollowUpContext.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiTaskStateMachine.kt`
- matching repair, revision, and follow-up tests
- `docs/decisions/phase-8-slice-8-repair-code-inventory.md`
- `docs/decisions/phase-8-slice-8-repair-and-revision.md`

## Tests Added Or Updated

- Added repair-code inventory completeness, status enforcement, packet completeness, and stable ordering coverage.
- Added unknown, unsupported, explanation-only, and ambiguous finding denial coverage.
- Added private mutation, retained finding/revision history, mandatory reanalysis, and per-package/task repair-limit coverage.
- Added latest-item undo, package undo, dependency-safe item removal, assumption revision, package revision, and analysis rerun coverage.
- Added evidence-grounded follow-up context coverage for completed and failed work, assumptions, findings, repairs, questions, analysis references, and the current private draft.

## Verification Commands

```bash
./gradlew :web-server:test --tests '*AiRepairPacketBuilderTest' --tests '*AiRepairControllerTest' --tests '*AiTaskRevisionServiceTest' --tests '*AiTaskFollowUpContextTest'
./gradlew :web-server:test
git diff --check
```

## Verification Results

- Focused repair, revision, and follow-up tests: passed.
- Full `web-server` test suite: passed.
- `git diff --check`: passed.

## Git Commit

Yes. This artifact is included in `Add Phase 8 bounded task repair`.

## Assumptions And Limitations

- Automatic repair delegates only to an injected private typed-draft mutation and never mutates shared or applied project state.
- A deterministic finding must provide the exact candidate references required by its inventoried operation; otherwise the task pauses for clarification.
- Repair completion is not inferred from model text: every successful mutation returns the task to deterministic validation.

## Notable Decisions

- Unknown codes and every code not marked `AUTO_REPAIRABLE` are denied automatic mutation.
- Original findings and all repair revisions are retained as immutable task evidence.
- Repair is capped at three cycles per package and eight cycles per task.
- Undo and revision reuse existing private-draft dependency checks and task-state transitions.
- Follow-up context is assembled from server-owned task, analysis, repair, and private-draft records rather than conversation replay.
