# Phase 10 Slice 4: Semantic Job Ownership, Freshness, And Ktor Boundary

## ExecPlan Slice

Slice 4: Semantic Job Ownership, Freshness Orchestration, And Ktor Boundary.

## Goal

Expose owner-bound bounded inference candidates and one authorized materialization route
that reloads the applied graph, reruns reasoning, matches selected semantic facts, and
delegates the complete verified batch to atomic shared staging.

## Files Modified

- `web-server/src/main/kotlin/com/entio/web/JobContracts.kt`
- `web-server/src/main/kotlin/com/entio/web/SemanticJobManager.kt`
- `web-server/src/main/kotlin/com/entio/web/InferenceMaterializationWebService.kt`
- `web-server/src/main/kotlin/com/entio/web/contract/InferenceMaterializationWebContracts.kt`
- `web-server/src/main/kotlin/com/entio/web/Application.kt`
- `web-server/src/main/kotlin/com/entio/web/StagingWorkflowService.kt`
- `web-server/src/test/kotlin/com/entio/web/InferenceMaterializationWebServiceTest.kt`
- `docs/decisions/phase-10-slice-4-web-boundary.md`

The staging-service change is one internal read-only semantic-key lookup required to
adapt current shared duplicates into candidate details; it does not change Slice 3
staging behavior.

## Tests Added Or Updated

- Added completed applied-job candidate and successful route/service staging coverage.
- Added owner-only materialization candidates and cross-user isolation.
- Added tampered ID, invalid source, oversized/duplicate request, stale graph, SHACL
  job, cancelled job, and missing proposal-scope coverage.
- Added fresh rerun matching through server-only semantic keys.
- Added route request/response, authorization, and safe not-found behavior.
- Added per-project concurrency and bounded-timeout checks proving unchanged staging.
- Verified staging changes do not modify ontology sources or call apply.

## Verification

- `./gradlew :web-server:test`: passed.
- `./gradlew :web-server:check`: passed.
- `./gradlew test`: passed.
- `git diff --check`: passed.

## Git Commit

Yes. This record is included in the focused Slice 4 commit.

## Assumptions And Limitations

- Existing ordinary project-scoped job status/details visibility remains compatible.
  Materialization candidates are emitted only to the submitting user.
- The existing development identity and in-memory job/session lifetime are preserved.
- The timeout guard checks cancellation/deadline immediately before the only staging
  mutation, so slow or interrupted reasoning cannot leave a partial batch.

## Notable Decisions

- React/HTTP submits opaque fact IDs and optional source choices only.
- A fresh result is matched to retained selections by `semanticFactKey`; fresh fact IDs
  are not used for correctness.
- One `Mutex` per project rejects concurrent materialization work.
- Cross-user job lookup returns the same safe unknown-job response as an absent job.
- Collaboration publishes one ordinary staged-change event after a successful route
  response; proposal jobs are invalidated through the existing hook.
