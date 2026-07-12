# Phase 2 Slice 5 Completion: Proposal Validation

## ExecPlan Slice Implemented

Slice 5: Proposal Validation.

## Goal

Add deterministic proposal validation that combines existing project validation output with proposal-specific checks.

## Files Modified

- `validation-engine/src/main/kotlin/com/entio/validation/ProposalValidator.kt`
- `validation-engine/src/test/kotlin/com/entio/validation/ProposalValidatorTest.kt`
- `docs/decisions/phase-2-slice-5-proposal-validation.md`

## Tests Added Or Updated

- Added `ProposalValidatorTest`.

## Verification Commands

- `./gradlew :validation-engine:test` - passed.
- `./gradlew test` - passed.

## Git Commit Status

Created with commit message `Add proposal validation`.

## Assumptions, Limitations, And Follow-Up Work

- `ChangeSet` already rejects empty change sets by construction, so proposal validation does not need to produce a runtime empty-change-set issue for normally constructed objects.
- `ProposalValidator` accepts an optional existing project `ValidationReport` so callers can combine project validation issues with proposal-specific issues without making proposal validation reload projects from disk.
- Duplicate additions and missing removals are detected by rerunning preview validation against the current loaded graph.
- Stale proposal checks use the existing semantic-engine `ProposalCreator.isCurrent` baseline comparison.
- Semantic-equivalence validation is checked only when a `SemanticEquivalenceResult` is available or when the proposal status is `VerificationFailed`.
- This slice does not add source-file persistence, VS Code infrastructure, graph-diff behavior, OWL reasoning, full SHACL validation, Git automation, or shared-module product logic.

## Notable Implementation Decisions

- Proposal validation lives in `validation-engine` and reuses existing `ValidationReport`, `ValidationIssue`, and `ValidationIssueSorter` behavior.
- The validator returns sorted deterministic reports and preserves existing project validation behavior.
- Missing target-source validation is reported once; baseline comparison is skipped when the target source is absent because target validation already owns that failure.
