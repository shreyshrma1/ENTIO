# Phase 1.5 Slice 8 Completion: CLI Delegates To ProjectLoader

## ExecPlan Slice Implemented

Slice 8: CLI Delegates To ProjectLoader.

## Goal

Remove CLI-owned project-loading orchestration and delegate to `ProjectLoader`.

## Files Modified

- `cli/src/main/kotlin/com/entio/cli/CliProjectReader.kt`
- `cli/src/test/kotlin/com/entio/cli/CliModuleTest.kt`
- `docs/decisions/phase-1.5-slice-8-cli-delegates-to-project-loader.md`

## Tests Added Or Updated

- Added coverage that `symbols` reports structured project-loading failures.
- Added coverage that `diff` reports structured project-loading failures.
- Existing CLI tests continue to cover:
  - `validate` behavior,
  - stable `symbols` output,
  - deterministic `diff` output,
  - stable command parsing exit codes.

## Verification Commands

```bash
./gradlew :cli:test
./gradlew check
```

## Verification Results

- `./gradlew :cli:test`: passed.
- `./gradlew check`: passed.

## Git Commit

A Git commit was created after verification.

## Assumptions, Limitations, And Follow-Up Work

- `validate` continues to use `ProjectValidator`, as required by the Slice 8 ExecPlan.
- `symbols` now uses `ProjectLoader` through `CliProjectReader` and formats the loaded project symbols.
- `diff` now uses `ProjectLoader` through `CliProjectReader` for both project roots and diffs their combined graph states.
- CLI exit codes remain unchanged for the covered command paths.

## Notable Implementation Decisions

- Simplified `CliProjectReader` into a thin adapter over `ProjectLoader`.
- Removed CLI-owned config loading, source resolution, ontology parsing, symbol extraction, and graph assembly from `CliProjectReader`.
- Did not edit `core-types`, `semantic-engine`, `validation-engine`, `graph-diff`, `shared`, build files, or dependencies.
- Did not add ontology parsing, validation rules, graph diff logic, UI, server, watch, API, document-ingestion, AI, or database behavior to CLI files.
