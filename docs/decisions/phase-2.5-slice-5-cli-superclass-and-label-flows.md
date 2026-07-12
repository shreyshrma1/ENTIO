# Phase 2.5 Slice 5 Completion: CLI Superclass And Label Flows

## ExecPlan Slice Implemented

Slice 5: CLI Superclass And Label Flows.

## Goal

Expose direct superclass relationship changes and entity label addition or replacement through the existing proposal commands.

## Files Modified

- `cli/src/main/kotlin/com/entio/cli/ProposalCommandSupport.kt`
- `cli/src/test/kotlin/com/entio/cli/MachineReadableCliTest.kt`
- `docs/decisions/phase-2.5-slice-5-cli-superclass-and-label-flows.md`

## Implemented Behavior

- Direct `add-superclass` and `remove-superclass` requests use the existing typed-edit translator and proposal lifecycle.
- `set-entity-label` remains additive by default.
- `set-entity-label --replace-existing` removes current explicit labels for the target entity before adding the requested label.
- Existing preview and deterministic validation continue to reject self-subclass, duplicate, and missing relationship changes.
- No hierarchy reasoning or source-text editing was introduced.

## Tests Added

- Superclass addition preview.
- Superclass removal preview.
- Label replacement as an explicit removal plus addition.

## Verification Commands

```bash
./gradlew :cli:test
./gradlew :graph-diff:test
./gradlew test
```

## Verification Results

- `./gradlew :cli:test` passed.
- `./gradlew :graph-diff:test` passed.
- `./gradlew test` passed.

## Git Commit Status

The slice is complete and ready for its focused commit, remote branch push, and clean local merge into `main`.

Focused commit: `215793f9deab417ce67a3fbd4cd182314500336b`.

## Assumptions And Limitations

- Superclass operations address explicit direct `rdfs:subClassOf` statements only.
- Label replacement removes all current explicit labels for the selected entity in the target graph.
- Equivalent-class, disjoint-class, cycles, and full OWL reasoning remain out of scope.

## Notable Implementation Decisions

- Label replacement is represented as ordinary graph removal and addition, preserving one proposal, diff, validation, approval, and apply pathway.
- The shared `--replace-existing` option is only applied to operations whose change semantics explicitly support replacement.
