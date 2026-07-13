# Phase 2.5+ Slice 10 Completion: Deletion Proposal Selection And CLI Boundary

## ExecPlan Slice

Slice 10: Deletion Proposal Selection And CLI Boundary.

## Goal

Carry explicit dependent-statement selections through the Kotlin and machine-readable CLI boundary so deletion proposals continue through the existing normalization, preview, validation, diff, approval, application, rejection, stale, and rollback workflow.

## Files Modified

- `core-types/src/main/kotlin/com/entio/core/Phase25PlusContracts.kt`
- `core-types/src/test/kotlin/com/entio/core/Phase25PlusContractsTest.kt`
- `semantic-engine/src/main/kotlin/com/entio/semantic/DeletionDependencyAnalyzer.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/DeletionDependencyAnalyzerTest.kt`
- `validation-engine/src/main/kotlin/com/entio/validation/DeletionValidator.kt`
- `validation-engine/src/test/kotlin/com/entio/validation/DeletionValidatorTest.kt`
- `cli/src/main/kotlin/com/entio/cli/StructuredRequestParser.kt`
- `cli/src/main/kotlin/com/entio/cli/StructuredProposalCommand.kt`
- `cli/src/test/kotlin/com/entio/cli/StructuredRequestCliTest.kt`
- This completion record.

## Delivered

- Added a deterministic RDF-term-aware identity key for explicit deletion dependency statements.
- Preserved the existing graph-triple selection compatibility while supporting selection by stable dependency keys.
- Added structured request parsing for `selectedDependencyKeys` arrays.
- Added machine-readable dependency keys, readable labels, selection state, and invalid-selection details to deletion inspection output.
- Routed selected deletion dependencies through structured proposal normalization and the existing combined proposal path.
- Kept unresolved dependencies blocking and rejected unknown dependency keys deterministically.
- Preserved the existing source-writing, approval, and rollback boundaries.

## Tests Added Or Updated

- Stable dependency identity and RDF-term distinction tests in `core-types`.
- Stable-key selection and unknown-key rejection tests in `semantic-engine`.
- Invalid dependency selection validation test in `validation-engine`.
- Structured deletion request, explicit selection, and machine-readable dependency output tests in `cli`.

## Verification

- `./gradlew :core-types:test :semantic-engine:test :validation-engine:test :cli:test` passed.
- Full `./gradlew test` is run before commit.

## Scope Check

This slice does not change the VS Code extension, add a second proposal lifecycle, add direct source writes, introduce inferred OWL reasoning, or add persistence/Git behavior. UI deletion controls and explicit selection interaction remain in Slice 11.

## Git

The slice commit and remote branch are recorded after full verification.
