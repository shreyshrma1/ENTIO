# Phase 10 Slice 3: Atomic Shared-Staging Batch And Duplicate Checks

## ExecPlan Slice

Slice 3: Atomic Shared-Staging Batch And Duplicate Checks.

## Goal

Append a fully prepared inference-materialization batch to the existing shared staging
session atomically, with typed provenance, deterministic mappings, duplicate handling,
and one idempotency decision.

## Files Modified

- `web-server/src/main/kotlin/com/entio/web/StagingWorkflowService.kt`
- `web-server/src/main/kotlin/com/entio/web/contract/StagingContracts.kt`
- `web-server/src/test/kotlin/com/entio/web/InferenceMaterializationStagingTest.kt`
- `docs/decisions/phase-10-slice-3-atomic-staging.md`

## Tests Added Or Updated

- Added one- and multi-item atomic append coverage.
- Added first, middle, and last validation-failure checks proving no staging/proposal
  mutation and reusable idempotency.
- Added mixed new/existing and all-existing behavior.
- Added applied duplicate rejection and identical/conflicting replay behavior.
- Added deterministic multi-source ordering and target retention.
- Added server-owned user/time provenance and safe DTO adaptation.
- Added ordinary staging, typed proposal preview, and source-preservation regressions.
- Core prepared-batch tests cover duplicate fact IDs, semantic facts, and triples.

## Verification

- `./gradlew :web-server:test`: passed.
- `./gradlew :web-server:check`: passed.
- `./gradlew :core-types:test :semantic-engine:test :web-server:test`: passed.
- `git diff --check`: passed.

## Git Commit

Yes. This record is included in the focused Slice 3 commit.

## Assumptions And Limitations

- Current proposal contents derive from the retained staged entries, so canonical
  staged-triple matching also covers the current prepared proposal.
- The in-memory replay map has the same development-session lifetime as existing
  staging and idempotency state.
- Route authorization, fresh reasoning, and collaboration publication remain Slice 4.

## Notable Decisions

- The service validates applied duplicates, targets, and typed-edit/triple equality
  before recording idempotency or changing the session.
- All-existing batches return their existing staged IDs without changing session or
  idempotency state.
- New entries are appended in request order; mixed batches return mappings in the
  original request order.
- Caller-provided provenance author and time are replaced with the authenticated
  server user and server clock at the single mutation point.
