# Phase 10 Slice 7: End-To-End And Regression Gate

## Status

Complete.

## Verified Journey

Playwright now covers the review-controlled browser workflow: run applied-graph reasoning, inspect asserted and inferred sections, keyboard-select a supported inferred fact, submit a fact identifier rather than a client-authored triple, review reasoning provenance and the explicit assertion diff, reject without applying, restage, and explicitly accept/apply from Changes.

The browser test inspects every materialization request and proves that Reasoning does not call proposal approval or application endpoints. Existing server integration tests cover stale reruns, tampered facts, source safety, job ownership, cross-user isolation, idempotency, batch bounds, cancellation, timeout, and per-project concurrent materialization. Copied-fixture tests compare source bytes across staging, rejection, application, and forced multi-source rollback.

## Bounded Timing Evidence

The deterministic copied development fixture produced these local staging times:

| Selected facts | Elapsed |
| ---: | ---: |
| 1 | 313 ms |
| 25 | 24 ms |
| 100 | 29 ms |

The one-fact result includes JVM and ontology-loader warm-up. All runs remained below the 10-second test ceiling and used no live network dependency.

## Full Verification

- `./gradlew :core-types:test :semantic-engine:test :validation-engine:test :graph-diff:test :web-server:test` — passed
- `./gradlew test` — passed
- `./gradlew build` — passed
- `./gradlew check` — passed
- `(cd web-app && npm ci && npm audit --omit=dev && npm test && npm run build && npm run test:e2e)` — passed; zero production audit findings, 74 unit tests, and 3 Playwright journeys
- `(cd vscode-extension && npm ci && npm test)` — passed, 37 tests
- `git diff --check` — passed
- `git status --short` — contained only this slice's approved test and completion-artifact changes
