# Phase 2.5 Slice 3 Completion: CLI Property, Domain, And Range Flows

## ExecPlan Slice Implemented

Slice 3: CLI Property, Domain, And Range Flows.

## Goal

Expose property creation and domain or range changes through the existing machine-readable proposal commands without bypassing the shared semantic workflow.

## Files Modified

- `cli/src/main/kotlin/com/entio/cli/ProposalCommands.kt`
- `cli/src/main/kotlin/com/entio/cli/ProposalCommandSupport.kt`
- `cli/src/test/kotlin/com/entio/cli/MachineReadableCliTest.kt`
- `docs/decisions/phase-2.5-slice-3-cli-property-domain-range-flows.md`

## Implemented Behavior

- Object-property and datatype-property creation accepts optional labels, domains, and ranges.
- Datatype-property creation accepts `--datatype` as the range shorthand.
- Property domain and range edits support addition by default.
- `--replace-existing` makes set-domain and set-range proposals remove current statements before adding the requested replacement.
- All composed changes continue through the existing proposal creation, preview, semantic diff, validation, equivalence, approval, and apply paths.
- Existing machine-readable JSON output and `create-class` behavior remain unchanged.

## Tests Added Or Updated

- Object-property creation with optional domain and range.
- Datatype-property creation with a datatype range.
- Range replacement removes the old statement and adds the new statement.
- Existing CLI regression tests remain green.

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

Focused commit: `ec9a470de72fa652418d2f772ed3dad4e168aeeb`.

## Assumptions And Limitations

- Replacement is explicit through `--replace-existing`; the default remains additive.
- One domain and one range are supported per request.
- Property characteristics, inverse properties, chains, cardinality, and reasoning remain out of scope.

## Notable Implementation Decisions

- Optional property metadata is composed from existing `SetPropertyDomainEdit` and `SetPropertyRangeEdit` contracts rather than adding new core types.
- Replacement removes only the target property’s existing explicit domain or range statements and then adds the requested statement in one proposal change set.
