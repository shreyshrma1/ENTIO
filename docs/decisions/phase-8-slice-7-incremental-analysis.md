# Phase 8 Slice 7: Incremental Analysis Completion

## ExecPlan Slice Implemented

Phase 8 Slice 7, Incremental Deterministic Analysis And Staleness.

## Goal

Run fingerprinted deterministic analysis in fixed order at task boundaries, block unsafe package progress, and stop stale tasks while preserving their private drafts.

## Files Modified

- `web-server/src/main/kotlin/com/entio/web/ai/AiIncrementalValidationService.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiTaskStalenessService.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiTaskContracts.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiTaskContextPackageBuilder.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiTaskStateMachine.kt`
- matching incremental-analysis and staleness tests
- `docs/decisions/phase-8-slice-7-incremental-analysis.md`

## Tests Added Or Updated

- Added fixed stage ordering, Kotlin-derived relevance, and deterministic not-relevant skips.
- Added warnings versus blocking behavior and package blocking.
- Added stage-by-stage fingerprint mismatch rejection and analysis history/reference coverage.
- Added premature versus complete final analysis behavior.
- Added project changes across retrieval, execution, validation, reasoning, SHACL, and final work.
- Added cache invalidation, private-draft preservation, refreshed fingerprint, revalidation, and meaning-change confirmation coverage.

## Verification Commands

```bash
./gradlew :web-server:test --tests '*AiIncrementalValidationServiceTest' --tests '*AiTaskStalenessServiceTest' --tests '*AiDraftAnalysisTest'
./gradlew :web-server:test
git diff --check
```

## Verification Results

- Focused incremental validation, staleness, and draft-analysis tests: passed.
- Full `web-server` test suite: passed.
- `git diff --check`: passed.

## Git Commit

Yes. This artifact is included in `Add Phase 8 incremental task analysis`.

## Assumptions And Limitations

- Existing deterministic adapters supply actual preview, validation, diff, reasoning, and SHACL results; the task service orders and fingerprints them.
- This slice emits finding codes but does not implement repair.
- Initial scope provenance remains immutable; a separate current project fingerprint records an explicitly refreshed analysis base.

## Notable Decisions

- Blocking, stale, incomplete, and failed outcomes stop the package and its dependents; model text cannot override them.
- Reasoning and SHACL relevance is derived from approved typed edit kinds and configured sources.
- Final valid or warning analysis becomes review-ready only after all required packages complete.
- A changed project fingerprint invalidates caches and requires revalidation; changed meaning also requires plan confirmation.
