# Phase 8 Slice 10: Task Web Contracts Completion

## ExecPlan Slice Implemented

Phase 8 Slice 10, Versioned Task Web Contracts, Events, And Compatibility Boundary.

## Goal

Expose authenticated project-scoped task resources and commands through additive versioned contracts, with expected revisions, idempotency, bounded private events, and Phase 7 compatibility.

## Files Modified

- `web-server/src/main/kotlin/com/entio/web/Application.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiTaskWebBoundary.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiCapabilityContracts.kt`
- `web-server/src/main/kotlin/com/entio/web/contract/AiTaskWebContracts.kt`
- `web-server/src/main/resources/entio-help/v1/help.json`
- matching task web-contract and existing application/AI compatibility tests
- `docs/decisions/phase-8-slice-10-task-web-contracts.md`

## Tests Added Or Updated

- Added task create/read/workspace/resource and pause/resume/cancel command coverage.
- Added idempotent replay, conflicting reuse, stale revision, cross-user/project privacy, and terminal-state coverage.
- Added ordered bounded task-event retention, reconnect cursor, gap resynchronization, redaction, and ownership coverage.
- Retained the full existing Phase 7 application and AI web-contract regression suites.

## Verification Commands

```bash
./gradlew :web-server:test --tests '*AiTaskWebContractTest' --tests '*ApplicationTest' --tests '*AiWebContractTest'
./gradlew :web-server:test
git diff --check
```

## Verification Results

- Focused task, application, and Phase 7 compatibility tests: passed.
- Full `web-server` test suite: passed.
- `git diff --check`: passed.

## Git Commit

Yes. This artifact is included in `Add Phase 8 task web contracts`.

## Assumptions And Limitations

- Routes and DTO mapping delegate task legality to server-owned lifecycle/store services and do not reproduce semantic policy.
- Draft, analysis, and review-package endpoints expose bounded authoritative references; detailed private resources remain owned by their existing services.
- Provider-triggering task orchestration commands are accepted into the task boundary and represented by authoritative task events; provider execution remains owned by the orchestration services.

## Notable Decisions

- All mutating/provider-triggering routes require both `Idempotency-Key` and an expected workspace revision.
- Task events are private to the owner, ordered, retained to 200 events, and require authoritative resynchronization after a retention gap.
- Task DTOs exclude credentials, raw provider objects, ontology graphs, reviewer authority, and cross-user state.
- Existing conversation, run, draft, and SSE endpoints retain their Phase 7 meanings.
