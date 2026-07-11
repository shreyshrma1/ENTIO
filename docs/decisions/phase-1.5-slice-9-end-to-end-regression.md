# Phase 1.5 Slice 9 Completion: End-To-End Phase 1.5 Regression

## ExecPlan Slice Implemented

Slice 9: End-To-End Phase 1.5 Regression.

## Goal

Verify the corrected loading and RDF term model across the engine.

## Files Modified

- `cli/src/test/kotlin/com/entio/cli/Phase15EndToEndRegressionTest.kt`
- `docs/decisions/phase-1.5-slice-9-end-to-end-regression.md`

## Tests Added Or Updated

- Added `Phase15EndToEndRegressionTest`.
- Covered `ProjectLoader` loading a project with:
  - blank-node subjects,
  - blank-node objects,
  - datatyped literals,
  - language-tagged literals.
- Covered corrected RDF terms surviving parsing and combined graph assembly.
- Covered validation succeeding for the same fixture.
- Covered graph diffing against a changed project graph.
- Covered CLI `validate`, `symbols`, and `diff` behavior on the same fixture.

## Verification Commands

```bash
./gradlew test
./gradlew build
./gradlew check
```

## Verification Results

- `./gradlew test`: passed.
- `./gradlew build`: passed.
- `./gradlew check`: passed.

## Git Commit

A Git commit was created after verification.

## Assumptions, Limitations, And Follow-Up Work

- The regression fixture is created inside the test to avoid adding a broad fixture corpus.
- No committed example fixture update was needed for this slice.
- The test asserts engine behavior through public APIs and CLI execution rather than adding new production behavior.

## Notable Implementation Decisions

- Placed the regression test in the `cli` test module because that module can exercise `ProjectLoader`, validation, graph diffing, and CLI command behavior together.
- Did not add new product modules, build files, architecture docs, specs, ExecPlans, ontology mutation, persistence, reasoning, UI, server, document-ingestion, AI, or database behavior.
