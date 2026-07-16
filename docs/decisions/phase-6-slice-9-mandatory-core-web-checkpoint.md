# Phase 6 Slice 9: Mandatory Core-Web Checkpoint

## ExecPlan Slice

Slice 9 of `docs/execplans/0009-phase-6-collaborative-web-workbench-native-ai-foundation-execplan.md`.

## Goal

Verify the complete non-AI collaborative web foundation before native AI work begins.

## Implementation

The checkpoint initially exposed that the ExecPlan-required `web-app` command `npm run test:e2e` did not exist. Added a focused application-journey smoke test at `web-app/src/App.e2e.test.tsx` and wired it through `web-app/package.json`.

The smoke test covers project navigation, local entity inspection, FIBO module browsing, external element selection, and external detail rendering through the same React application boundary used by the workbench. It uses the existing Vitest and Testing Library setup rather than adding a second browser framework.

## Verification

Passed:

- `./gradlew test --no-daemon`
- `./gradlew build --no-daemon`
- `./gradlew check --no-daemon`
- `(cd web-app && npm ci && npm test && npm run build)`
- `(cd web-app && npm run test:e2e)`
- `(cd vscode-extension && npm ci && npm test)`
- `git diff --check`

The web suite contains 13 tests, including the new application-journey smoke test. The VS Code extension suite contains 37 tests.

## Result

The non-AI web foundation, existing Kotlin/CLI compatibility, VS Code compatibility, asynchronous semantic job lifecycle, collaboration coverage, and FIBO staging path are ready for the next approved slice.

## Assumptions And Limitations

- The new `test:e2e` command is a deterministic application-journey smoke test running in the existing jsdom test environment. A real browser automation layer remains outside this checkpoint.
- The checkpoint does not add AI behavior or change the existing semantic engine contracts.
