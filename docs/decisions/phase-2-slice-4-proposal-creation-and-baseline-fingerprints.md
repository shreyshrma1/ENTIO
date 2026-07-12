# Phase 2 Slice 4 Completion: Proposal Creation And Baseline Fingerprints

## ExecPlan Slice Implemented

Slice 4: Proposal Creation And Baseline Fingerprints.

## Goal

Create change proposals with target source information, baseline fingerprints, preview graph data, proposal status, and source-file impact metadata.

## Files Modified

- `semantic-engine/src/main/kotlin/com/entio/semantic/ProposalCreator.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/ProposalCreatorTest.kt`
- `docs/decisions/phase-2-slice-4-proposal-creation-and-baseline-fingerprints.md`

## Tests Added Or Updated

- Added `ProposalCreatorTest`.

## Verification Commands

- `./gradlew :semantic-engine:test` - passed.
- `./gradlew test` - passed.

## Git Commit Status

Created with commit message `Add proposal creation and baseline fingerprints`.

## Assumptions, Limitations, And Follow-Up Work

- Proposal creation requires an already loaded `EntioProject`.
- The created proposal is marked `Previewed` because this slice creates an in-memory preview graph during proposal creation.
- Baseline fingerprints cover the loaded graph, resolved ontology sources, and target source file content.
- Stale detection compares the stored baseline to the current loaded project and target source file state.
- Unrelated files outside resolved ontology sources do not affect proposal currency.
- This slice does not apply proposals to disk, persist proposal history, add VS Code behavior, add database/cache infrastructure, or add Git automation.

## Notable Implementation Decisions

- `ProposalCreator` stays in `semantic-engine` because it coordinates loaded project state, preview generation, and source-file fingerprints.
- Fingerprints use deterministic SHA-256 values over source bytes and sorted graph-triple keys.
- The implementation reuses existing `ChangeProposal`, `ProposalBaseline`, `ChangePreview`, and `SourceFileImpact` contracts without adding new `core-types` contracts.
