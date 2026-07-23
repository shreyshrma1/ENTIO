# Phase 10 Slice 1: Core Materialization Contracts And Provenance

## ExecPlan Slice

Slice 1: Core Materialization Contracts And Provenance.

## Goal

Define immutable, presentation-independent contracts for inference identity,
stageability, source selection, bounded batches, and reasoning provenance.

## Files Modified

- `core-types/src/main/kotlin/com/entio/core/InferenceMaterializationContracts.kt`
- `core-types/src/main/kotlin/com/entio/core/Phase25PlusContracts.kt`
- `core-types/src/test/kotlin/com/entio/core/InferenceMaterializationContractsTest.kt`
- `docs/decisions/phase-10-slice-1-core-contracts.md`

## Tests Added Or Updated

- Added focused construction and invariant tests for all three supported inference
  kinds and every stageability state.
- Added malformed identity, empty/oversized/duplicate batch, source-selection, import
  reference, provenance, and prepared-batch duplicate tests.
- Verified ordinary `StagedChange` construction remains valid without provenance.

## Verification

- `./gradlew :core-types:test`: passed.
- `./gradlew :core-types:check`: passed.
- `git diff --check`: passed.

## Git Commit

Yes. This record is included in the focused Slice 1 commit.

## Assumptions And Limitations

- Import references are bounded to 20 sorted unique source IDs; materialization batches
  remain bounded to 100.
- The contracts contain no labels because identity and provenance are semantic and
  presentation-neutral.
- Identity digest computation and semantic analysis remain in Slice 2.

## Notable Decisions

- `SemanticFactKey` and `InferenceFactId` are distinct value classes with their approved
  version prefixes and lowercase SHA-256 shape validation.
- `PreparedInferenceMaterializationBatch` rejects duplicate browser IDs, semantic keys,
  and asserted triples before a server session can be mutated.
- `StagedChange.materializationProvenance` is nullable with a default, preserving every
  existing ordinary staging call site.
