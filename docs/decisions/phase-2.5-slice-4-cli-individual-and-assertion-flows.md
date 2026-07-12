# Phase 2.5 Slice 4 Completion: CLI Individual And Assertion Flows

## ExecPlan Slice Implemented

Slice 4: CLI Individual And Assertion Flows.

## Goal

Expose individual creation and object or datatype property assertions through the existing machine-readable proposal lifecycle.

## Files Modified

- `cli/src/main/kotlin/com/entio/cli/ProposalCommandSupport.kt`
- `cli/src/test/kotlin/com/entio/cli/MachineReadableCliTest.kt`
- `docs/decisions/phase-2.5-slice-4-cli-individual-and-assertion-flows.md`

## Implemented Behavior

- `create-individual` supports an optional initial type and label.
- Object-property assertions accept existing subject, property, and object resources.
- Datatype-property assertions accept a literal value with optional datatype and language fields.
- Individual and assertion requests reuse the existing typed-edit translator, proposal preview, semantic diff, deterministic validation, equivalence verification, and approval boundary.
- No direct source-file writes or separate persistence path was introduced.

## Tests Added

- Individual creation with an initial type and label.
- Object-property assertion against existing resources.
- Datatype-property assertion with a typed integer literal.

## Verification Commands

```bash
./gradlew :cli:test
./gradlew :validation-engine:test
./gradlew test
```

## Verification Results

- `./gradlew :cli:test` passed.
- `./gradlew :validation-engine:test` passed.
- `./gradlew test` passed.

## Git Commit Status

The slice is complete and ready for its focused commit, remote branch push, and clean local merge into `main`.

Focused commit: `183ff125bbebb4b52dab77cfb0e0ef944a83957a`.

## Assumptions And Limitations

- A create-individual request supports one optional initial type per proposal.
- Assertions reference existing resources; entity resolution and external lookup remain out of scope.
- Literal compatibility remains authoritative in the Kotlin validation engine.

## Notable Implementation Decisions

- The optional individual label is composed as an existing `SetEntityLabelEdit` change so the core typed-edit contracts remain unchanged.
- Assertion forms remain thin request adapters and do not parse RDF or perform validation in CLI code.
