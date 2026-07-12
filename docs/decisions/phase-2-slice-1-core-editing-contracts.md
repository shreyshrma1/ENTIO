# Phase 2 Slice 1 Completion: Core Editing Contracts

## ExecPlan Slice Implemented

Slice 1: Core Editing Contracts.

## Goal

Introduce Entio-owned data contracts for graph changes, change sets, typed ontology edits, proposals, proposal statuses, previews, baselines, file impact, apply results, and rollback results.

## Files Modified

- `core-types/src/main/kotlin/com/entio/core/GraphChange.kt`
- `core-types/src/main/kotlin/com/entio/core/TypedOntologyEdit.kt`
- `core-types/src/main/kotlin/com/entio/core/ProposalBaseline.kt`
- `core-types/src/main/kotlin/com/entio/core/ChangePreview.kt`
- `core-types/src/main/kotlin/com/entio/core/ChangeProposal.kt`
- `core-types/src/main/kotlin/com/entio/core/ProposalResults.kt`
- `core-types/src/test/kotlin/com/entio/core/CoreEditingContractsTest.kt`
- `core-types/src/test/kotlin/com/entio/core/CoreTypesEnumTest.kt`
- `core-types/src/test/kotlin/com/entio/core/SemanticDiffTest.kt`
- `docs/decisions/phase-2-slice-1-core-editing-contracts.md`

## Tests Added Or Updated

- Added `CoreEditingContractsTest`.
- Updated `CoreTypesEnumTest` for new fixed states.
- Updated `SemanticDiffTest` for the expanded `ChangeProposal` contract.

## Verification Commands

- `./gradlew :core-types:test` - passed.
- `./gradlew test` - passed.

## Git Commit Status

Created with commit message `Add Phase 2 core editing contracts`.

## Assumptions, Limitations, And Follow-Up Work

- `ChangeSet` rejects empty change lists by construction using `require`.
- `ChangeSet` preserves caller-provided order; later engine slices may add deterministic sorting where graph application needs it.
- These contracts do not implement graph preview, typed edit translation, source persistence, serialization, CLI behavior, VS Code behavior, or Git workflow automation.
- Typed ontology edits use `RdfResource` for subject-like positions so literals cannot be modeled as subjects through these contracts.

## Notable Implementation Decisions

- Proposal lifecycle states were expanded to match Phase 2 planning: draft, previewed, verified, ready for review, rejected, approved, applied, verification failed, stale, apply failed, and rolled back.
- Apply, rollback, and semantic-equivalence outcomes are modeled as sealed interfaces so later slices can return explicit fixed result states without loose strings.
