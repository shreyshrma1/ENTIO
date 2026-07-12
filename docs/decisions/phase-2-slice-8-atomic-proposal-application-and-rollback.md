# Phase 2 Slice 8 Completion: Atomic Proposal Application And Rollback

## ExecPlan Slice Implemented

Slice 8: Atomic Proposal Application And Rollback

## Goal

Apply approved, valid, current proposals atomically to the target Turtle source and restore the prior source if save or verification fails.

## Files Modified

- `semantic-engine/src/main/kotlin/com/entio/semantic/ProposalApplier.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/ProposalApplierTest.kt`
- `docs/decisions/phase-2-slice-8-atomic-proposal-application-and-rollback.md`

## Tests Added Or Updated

Added focused semantic-engine tests covering:

- Approved proposal writes only the target ontology source.
- Rejected proposal does not write source files.
- Stale proposal is blocked before writing.
- Temporary serialization failure does not modify the source.
- Post-save semantic verification failure restores the prior source.
- Rollback failure is represented in the structured result.
- Unrelated files remain untouched when application fails after writing.

## Verification Commands Run

```bash
./gradlew :semantic-engine:test
./gradlew :validation-engine:test
./gradlew test
```

## Verification Results

All verification commands passed.

The first Gradle run required access to the local Gradle wrapper cache under `~/.gradle`, so verification was rerun with elevated filesystem access. No project files outside the slice scope were changed for verification.

## Git Commit

A Git commit was created after the user explicitly approved committing and pushing this slice.

## Assumptions And Limitations

- Proposal application stays in `semantic-engine` and does not introduce a new dependency from `semantic-engine` to `validation-engine`, preserving the current module dependency direction.
- The applier checks that the proposal is approved, reloads the current project, verifies the proposal baseline is current, verifies the preview can round-trip through Turtle, writes serialized preview Turtle to a temporary file, replaces the target source, reloads the project, and compares the saved graph with the approved preview.
- The implementation does not preserve original Turtle text layout. This matches the Phase 2 non-goal for source-text-preserving Turtle round trips.
- The implementation writes only the target ontology source identified by the proposal. Multi-source source-preserving partitioning remains outside this slice.
- No Git staging, commits, pushes, branch management, pull requests, long-term version history, database storage, or VS Code infrastructure was added.

## Notable Implementation Decisions

- `ProposalApplier` returns the existing `ApplyProposalResult` and `RollbackResult` contracts without requiring core-type changes.
- Small function injection points are used for tests around serialization, project loading, equivalence comparison, and rollback failure paths. The default behavior uses the existing `ProjectLoader`, `ProposalCreator`, and `PreviewTurtleRoundTripVerifier`.
- Atomic replacement uses `StandardCopyOption.ATOMIC_MOVE` where supported and falls back to replacement when atomic move is not available on the platform.
