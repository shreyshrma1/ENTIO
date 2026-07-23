# Phase 10.5 Slice 6 Completion: Regression Gate

Status: Complete
Date: 2026-07-23

## Evidence

- Core inferred contracts enforce provenance, graph-state separation, completeness, uniqueness, and the 100-fact aggregate bound.
- Semantic tests cover asserted-priority deduplication, deterministic ordering, effective domain/range projection, unsupported resources, and inferred graph edges.
- Server tests cover additive flags, project-owned result selection, safe states, authorization, stale continuations, and unchanged materialization ownership.
- Explore tests cover off-by-default flags, query-key isolation, request parameters, provenance labels, and safe state messaging.
- Map tests cover non-color inferred labels, focus neighborhoods, click/drag/pan/zoom behavior, information-card placement, and asserted-only layout inputs.
- Existing materialization, proposal, staging, CLI, VS Code, and browser workflows remain green.

## Full Verification Results

- `./gradlew test` — passed.
- `./gradlew build` — passed.
- `./gradlew check` — passed.
- `web-app: npm ci` — passed.
- `web-app: npm audit --omit=dev` — zero vulnerabilities.
- `web-app: npm test` — 22 files, 88 tests passed.
- `web-app: npm run build` — passed.
- `web-app: npm run test:e2e` — 3 tests passed.
- `vscode-extension: npm ci && npm test` — 37 tests passed.
- `git diff --check` — passed.

The Playwright mock server logged expected WebSocket proxy connection warnings because no Kotlin backend was started; all browser assertions passed.
