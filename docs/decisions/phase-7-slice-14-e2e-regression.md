# Phase 7 Slice 14 End-To-End Regression

## ExecPlan Slice

Slice 14: End-To-End Phase 7 Regression And Documentation Summary.

## Goal

Verify the complete Phase 7 journey with deterministic provider fixtures, preserve Phase 1 through Phase 6 behavior, and document the implemented result and actual limitations.

## Files Modified

- `web-server/src/test/kotlin/com/entio/web/AiWebContractTest.kt`
- `web-app/e2e/workbench.spec.ts`
- `docs/phase-summaries/phase-7-summary.md`
- `docs/decisions/phase-7-slice-14-e2e-regression.md`

## Regression Result

- Extended the copied-fixture server regression from conversation and private draft creation through deterministic analysis, idempotent submission, separate reviewer approval, atomic application, and project reload.
- Proved that the contributing user cannot approve the submitted AI proposal and that source bytes remain unchanged until reviewer application.
- Extended the browser journey through an explanation, broad-request plan confirmation, private typed draft, deterministic analysis, explicit review submission, and the server-provided proposal handoff route.
- Continued to cover clarification, cancellation, stale/conflict, limits, provider outage, prompt injection, scope isolation, rejection, rollback, FIBO, reasoning, SHACL, collaboration, and existing proposal workflows through focused server, component, semantic-engine, CLI, and browser suites.
- Kept all provider execution deterministic and offline in automated tests.
- Did not modify committed example projects or FIBO assets.

## Verification

- Focused `AiWebContractTest` - passed.
- Focused Playwright workbench journey - passed.
- `./gradlew test` - passed.
- `./gradlew build` - passed.
- `./gradlew check` - passed.
- `npm ci && npm test && npm run build && npm run test:e2e` in `web-app` - passed with 28 unit/component tests, a production build, and one expanded browser journey. The clean install reported two existing high-severity audit findings; Slice 14 changed no dependency or lockfile.
- `npm ci && npm test` in `vscode-extension` - passed with 37 tests and no reported vulnerabilities.
- `git diff --check` - passed.
- Fixture-integrity review found no changes under `examples/` or `external-ontologies/`.

## Git Commit

A focused Slice 14 commit will be created on `feature/phase-7-slice-14-e2e-regression` after full-phase verification passes.

## Assumptions And Limitations

- Automated provider behavior uses deterministic fakes or Ktor `MockEngine`; no live key or external request is used in CI.
- The browser journey uses intercepted versioned routes, while server contract tests exercise real Ktor routes and temporary project files.
- Atomic rollback remains proven by the existing semantic-engine, CLI, multi-source, and web-server regression tests rather than by introducing a second Phase 7 apply implementation.
- The implementation summary records the current synchronous message response and safe retained SSE behavior rather than claiming live token streaming.

## Notable Decisions

- The final regression verifies that AI submission enters the ordinary proposal path and that only the existing reviewer/apply authority changes source files.
- Existing lower-level rollback tests remain authoritative because Phase 7 does not own or replace proposal application.
- The summary distinguishes delivered behavior from planned production infrastructure and does not present in-memory state as durable.
