# Phase 6 Slice 7: Asynchronous Reasoning And SHACL Jobs

## ExecPlan slice implemented

Slice 7, Asynchronous Reasoning And SHACL Jobs.

## Goal

Run existing OWL reasoning and SHACL validation services as observable, cancellable web jobs without blocking HTTP requests or presenting stale results as current.

## Files modified

- `web-server/build.gradle.kts`
- `web-server/src/main/kotlin/com/entio/web/Application.kt`
- `web-server/src/main/kotlin/com/entio/web/CollaborationHub.kt`
- `web-server/src/main/kotlin/com/entio/web/JobContracts.kt`
- `web-server/src/main/kotlin/com/entio/web/SemanticJobManager.kt`
- `web-server/src/main/kotlin/com/entio/web/StagingWorkflowService.kt`
- `web-server/src/main/kotlin/com/entio/web/WebGraphFingerprint.kt`
- `web-server/src/test/kotlin/com/entio/web/ApplicationTest.kt`
- `web-app/src/styles.css`
- `web-app/src/web/jobs.test.ts`
- `web-app/src/web/projectApi.ts`
- `web-app/src/web/queries.ts`
- `web-app/src/workbench/ProjectWorkspace.tsx`
- `web-app/src/workbench/SemanticJobPanel.tsx`

## Implementation

- Added in-memory job contracts and a coroutine-backed manager for reasoning and SHACL jobs.
- Added applied-graph and proposal-preview graph snapshots with graph and proposal fingerprints.
- Added queued, running, completed, failed, cancelled, incomplete, and stale states with phase, timestamps, summaries, and errors.
- Added applied-graph supersession, proposal invalidation, duplicate proposal-job suppression, current-fingerprint checks, and matching inferred-graph reuse.
- Added HTTP submission, polling, and reviewer cancellation endpoints.
- Added job identifiers to collaboration events.
- Added a workbench panel for scope selection, job start, progress/status, fingerprints, stale/error messages, polling, and cancellation.
- Reused the existing `ReasoningService`, `ShaclGraphLoader`, and `ShaclValidationService`; no new semantic engine was introduced.

## Tests added or updated

- Added a web-server lifecycle test covering immediate job responses, reasoning completion, and asserted-only SHACL completion.
- Added typed web API tests covering semantic job submission, polling, and cancellation.

## Verification

- `./gradlew :web-server:test --tests com.entio.web.ApplicationTest.semanticJobsReturnImmediatelyAndExposeReasoningAndShaclLifecycle --no-daemon` - passed.
- `./gradlew :web-server:test --no-daemon` - passed.
- `./gradlew test --no-daemon` - passed.
- `(cd web-app && npm test && npm run build)` - passed: 6 test files and 12 tests.

## Git

- Branch: `feature/phase-6-reasoning-shacl-jobs`
- Commit: Created on this branch with message `Add Phase 6 asynchronous semantic jobs`; the final hash is reported with the implementation result.

## Assumptions and limitations

- Job state is intentionally in memory and is scoped to the running web-server process.
- Result summaries are exposed through the web boundary; full reasoning and validation result browsing remains outside this slice.
- Cancellation marks jobs promptly, while an already-running third-party semantic library call may finish internally before the coroutine observes cancellation. Fingerprint checks prevent its result from being accepted as current.
