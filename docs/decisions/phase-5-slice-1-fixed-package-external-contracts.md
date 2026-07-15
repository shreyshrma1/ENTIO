# Phase 5 Slice 1: Fixed Package And External Ontology Contracts

## ExecPlan slice implemented

Slice 1 of `docs/execplans/0008-phase-5-external-ontology-browsing-schema-rag.md`.

## Goal

Define Entio-owned immutable contracts for the approved fixed FIBO package, external semantic descriptions, deterministic search, explicit dependencies, and external reuse/local-extension intents.

## Files modified

- `core-types/src/main/kotlin/com/entio/core/Phase5Contracts.kt`
- `core-types/src/test/kotlin/com/entio/core/Phase5ContractsTest.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/ProjectLoaderTest.kt` (stale committed example-fixture expectation, explicitly authorized during implementation)

## Tests added or updated

- Added focused contract tests for approved package identity invariants, RDF-term preservation, asserted `already-used` state, dependency visibility, search page bounds, and proposal intent kinds.
- Updated the existing example-project loader test to expect the committed `shapes` source and extracted `CustomerShape` symbol.

## Verification

Passed:

```bash
./gradlew :core-types:test
./gradlew test
```

## Result

Slice 1 is complete. The contracts encode the fixed package identity and Phase 5 data boundaries without implementing package loading, search execution, dependency calculation, proposal translation, CLI behavior, or VS Code behavior.

## Assumptions and limitations

- Package assets, catalog loading, search execution, dependency calculation, proposal translation, CLI commands, and VS Code behavior remain future slices.
- The stale loader test update was limited to the repository's existing committed example fixture and does not change product behavior.
