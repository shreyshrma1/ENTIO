# Phase 2.5+ Slice 6 Completion: CLI Combined Proposal Lifecycle

## Status

Implemented on `feature/phase-2.5-plus-slice-6-cli-lifecycle`.

## Delivered

- Added the machine-readable `proposal-combined` CLI command for structured preview, validation, diff, apply, and reject actions.
- Connected structured requests to the existing staged-change normalizer and `CombinedPreviewService` for one combined graph preview, semantic diff, deterministic validation, and Turtle round-trip equivalence result.
- Preserved no-write behavior for preview, validation, diff, rejection, stale baselines, conflicts, and invalid combined proposals.
- Connected approved current proposals to the existing `ProposalApplier`, including reload, changed-file reporting, stale detection, and rollback results without adding a second source-writing path.
- Added focused CLI coverage for preview/reject no-write behavior, successful apply and reload, and stale-baseline blocking.

## Verification

- `./gradlew :cli:test --no-daemon --console=plain`
- `./gradlew :cli:test :semantic-engine:test :validation-engine:test test --no-daemon --console=plain`

All commands passed.

## Scope Check

This slice does not add proposal persistence, Git automation, a second apply path, or VS Code workbench behavior. Structured CLI invocations remain stateless, and semantic behavior remains owned by the existing Kotlin services.
