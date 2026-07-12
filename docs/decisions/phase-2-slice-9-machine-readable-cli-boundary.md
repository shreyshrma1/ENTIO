# Phase 2 Slice 9 Completion: Machine-Readable CLI Boundary

## ExecPlan Slice Implemented

Slice 9: Machine-Readable CLI Boundary.

## Goal

Expose stable machine-readable CLI responses for the Phase 2 workflow while keeping parsing, previewing, validation, diffing, serialization, and application in reusable Kotlin modules.

## Files Modified

- `cli/src/main/kotlin/com/entio/cli/CliOutput.kt`
- `cli/src/main/kotlin/com/entio/cli/DiffCommand.kt`
- `cli/src/main/kotlin/com/entio/cli/EntioCli.kt`
- `cli/src/main/kotlin/com/entio/cli/JsonOutput.kt`
- `cli/src/main/kotlin/com/entio/cli/ProjectSummaryCommand.kt`
- `cli/src/main/kotlin/com/entio/cli/ProposalCommandSupport.kt`
- `cli/src/main/kotlin/com/entio/cli/ProposalCommands.kt`
- `cli/src/main/kotlin/com/entio/cli/SymbolsCommand.kt`
- `cli/src/main/kotlin/com/entio/cli/ValidateCommand.kt`
- `cli/src/test/kotlin/com/entio/cli/MachineReadableCliTest.kt`
- `docs/decisions/phase-2-slice-9-machine-readable-cli-boundary.md`

## Implemented Behavior

- Added JSON output mode to the existing `validate`, `symbols`, and `diff` commands while preserving their existing text output by default.
- Added `project-summary` and `summary` commands returning project, ontology-source, graph, and symbol data as JSON.
- Added `proposal-preview`, `proposal-validate`, `proposal-diff`, `proposal-apply`, and `proposal-reject` commands.
- Kept proposal commands thin by composing `ProjectLoader`, `TypedOntologyEditTranslator`, `ProposalCreator`, `ProposalDiffGenerator`, `ProposalValidator`, `PreviewTurtleRoundTripVerifier`, and `ProposalApplier`.
- Added structured JSON errors and deterministic non-zero exit codes for domain failures.
- Added focused command tests covering project summaries, existing-command JSON output, proposal preview/validation/diff, rejection without file changes, and approved proposal application.

The CLI proposal input is intentionally narrow for this slice: it accepts a `create-class` edit through `--class-iri` and optional `--label`, along with target source, proposal ID, and title options. The existing engine contracts remain the reusable home for the broader typed-edit model.

## Verification Commands

```bash
./gradlew :cli:test
./gradlew test
./gradlew build
```

## Verification Results

All listed commands passed.

Manual smoke checks also passed for:

```bash
./gradlew :cli:run --args="project-summary ../examples/simple-ontology"
./gradlew :cli:run --args="proposal-preview ../examples/simple-ontology simple --class-iri https://example.com/entio/simple#Invoice --label Invoice"
./gradlew :cli:run --args="validate ../examples/simple-ontology --json"
```

## Git Commit Status

No Git commit was created by this implementation step. The branch remains ready for review and for a focused commit after explicit authorization.

## Assumptions, Limitations, And Follow-Up Work

- The repository does not already include a JSON dependency, and this slice does not change build files. A small deterministic CLI-local JSON encoder is used for the boundary payloads.
- Proposal metadata remains in memory for the duration of one CLI invocation. This slice does not add proposal persistence or a proposal store between commands.
- `proposal-apply` prepares and validates a proposal in the same invocation before marking it approved and delegating to `ProposalApplier`.
- `proposal-reject` prepares the proposal for a reviewable structured response but never calls the applier and therefore leaves source files unchanged.
- The CLI does not add a local API server, watch mode, VS Code files, Git automation, or semantic logic implemented directly in CLI commands.
- Future UI work may refine the JSON schema or add boundary support for additional typed edit payloads after the contract is reviewed.

## Notable Implementation Decisions

- JSON responses use stable field ordering and explicit status, validation, diff, equivalence, source-impact, and rollback fields so process callers can consume them without parsing human-oriented text.
- Existing text command behavior and exit semantics remain unchanged when `--json` is not supplied.
- Proposal workflow composition stays at the CLI boundary; RDF parsing, graph mutation, proposal validation, semantic diffing, serialization, and atomic persistence remain delegated to their owning modules.
