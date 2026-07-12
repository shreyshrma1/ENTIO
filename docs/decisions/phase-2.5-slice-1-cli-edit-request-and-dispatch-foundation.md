# Phase 2.5 Slice 1 Completion: CLI Edit Request And Dispatch Foundation

## ExecPlan Slice Implemented

Slice 1: CLI Edit Request And Dispatch Foundation.

## Goal

Extend the existing proposal CLI boundary to recognize all Phase 2.5 edit kinds and dispatch them to the existing Kotlin typed-edit contracts without adding a second semantic pathway.

## Files Modified

- `cli/src/main/kotlin/com/entio/cli/ProposalCommands.kt`
- `cli/src/main/kotlin/com/entio/cli/ProposalCommandSupport.kt`
- `cli/src/test/kotlin/com/entio/cli/MachineReadableCliTest.kt`
- `cli/src/test/kotlin/com/entio/cli/CliExampleProjectTest.kt`
- `cli/src/test/kotlin/com/entio/cli/Phase2EndToEndRegressionTest.kt`
- `docs/decisions/phase-2.5-slice-1-cli-edit-request-and-dispatch-foundation.md`

The existing CLI regression tests were updated because the committed example fixture already contains the user-added `Invoice` class. The diff test now appends a distinct valid `Order` class, and proposal regression tests use `PurchaseOrder` for new proposals.

## Implemented Behavior

- Added a shared internal `CliEditRequest` model for edit-specific fields.
- Added CLI options for properties, domains, ranges, datatypes, individuals, types, assertions, superclass relationships, labels, and language tags.
- Added dispatch to the existing typed-edit contracts for all Phase 2.5 edit kinds.
- Preserved the existing `create-class` command behavior.
- Preserved structured unsupported-edit failures.
- Added boundary coverage proving all Phase 2.5 edit kinds dispatch without being rejected as unsupported.

Domain/range composition and edit-specific compatibility validation remain in their approved later slices.

## Verification Commands

```bash
./gradlew :cli:test --tests com.entio.cli.MachineReadableCliTest
./gradlew :cli:test
./gradlew test
```

## Verification Results

- Focused `MachineReadableCliTest` passed.
- `./gradlew :cli:test` passed.
- `./gradlew test` passed.

## Git Commit Status

The implementation and verification are complete. This artifact accompanies the focused Slice 1 commit and its remote branch push before the local non-fast-forward merge into `main`.

Focused commit: `3b768c98d7592041681fc3d058edc5f9d67cc962`.

## Assumptions And Limitations

- The existing `core-types` typed-edit contracts are sufficient for request dispatch.
- Edit-specific validation is intentionally deferred to Slice 2.
- Optional property domain/range composition is intentionally deferred to Slice 3.
- The CLI remains a thin adapter and does not parse RDF or write ontology files directly.

## Notable Implementation Decisions

- A typed internal request model keeps picocli parsing separate from semantic translation.
- The command set remains unchanged; existing proposal commands are extended through `--edit` and edit-specific options.
- The committed example fixture is treated as the current baseline rather than being reverted or rewritten.
