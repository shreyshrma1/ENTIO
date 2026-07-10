# Phase 1 Slice 9 Completion: CLI Commands

## ExecPlan Slice Implemented

Slice 9: CLI Commands.

## Goal

Add thin CLI commands over reusable Phase 1 modules.

## Files Modified

- `cli/build.gradle.kts`
- `cli/src/main/kotlin/com/entio/cli/EntioCli.kt`
- `cli/src/main/kotlin/com/entio/cli/CliProjectReader.kt`
- `cli/src/main/kotlin/com/entio/cli/CliOutput.kt`
- `cli/src/main/kotlin/com/entio/cli/ValidateCommand.kt`
- `cli/src/main/kotlin/com/entio/cli/SymbolsCommand.kt`
- `cli/src/main/kotlin/com/entio/cli/DiffCommand.kt`
- `cli/src/test/kotlin/com/entio/cli/CliModuleTest.kt`
- `docs/decisions/phase-1-slice-9-cli-commands.md`

## Tests Added Or Updated

- Replaced the placeholder CLI module test with tests for:
  - module name exposure.
  - `validate` success exit code and output.
  - `validate` failure exit code and output.
  - `symbols` output.
  - `diff` no-change exit code and output.
  - `diff` changed-graph exit code and output.
  - command parsing failure exit code.

## Verification Commands

- `./gradlew :cli:test`
- `./gradlew check`

## Verification Results

- `./gradlew :cli:test`: passed.
- `./gradlew check`: passed.

## Git Commit Status

A Git commit was created after the user explicitly authorized commit and push.

## Assumptions, Limitations, And Follow-Up

- Picocli was selected as the lightweight CLI parser because it is mature, Java-friendly, and appropriate for a Kotlin/JVM command-line wrapper.
- `diff` exits with `0` when no semantic changes are found, `1` when differences are found, and `2` when either project cannot be loaded.
- `validate` and `symbols` exit with `1` when project loading or validation fails.
- CLI output is text-only. JSON output and optional flags from the ExecPlan remain future work.

## Implementation Decisions

- CLI commands delegate validation, ontology loading, symbol extraction, and diff computation to existing reusable modules.
- The CLI module formats text output and maps results to stable process exit codes.
- `CliProjectReader` composes semantic-engine APIs for CLI use without implementing Turtle parsing, validation rules, or diff logic.
