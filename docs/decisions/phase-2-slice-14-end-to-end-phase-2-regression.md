# Phase 2 Slice 14 Completion: End-To-End Phase 2 Regression

## ExecPlan Slice Implemented

Slice 14: End-To-End Phase 2 Regression.

## Goal

Add end-to-end regression coverage for the implemented Phase 2 workflow using a copied `examples/simple-ontology` fixture.

## Files Modified

- `cli/src/test/kotlin/com/entio/cli/Phase2EndToEndRegressionTest.kt`
- `docs/decisions/phase-2-slice-14-end-to-end-phase-2-regression.md`

## Implemented Coverage

- Loads the copied example project through `ProjectLoader`.
- Translates a `CreateClassEdit` into a graph change.
- Creates a proposal, attaches its semantic diff, validates it, and verifies Turtle round-trip equivalence.
- Confirms preview and validation do not change the copied source file.
- Approves and applies the proposal, reloads the project, and verifies semantic equivalence with the approved preview graph.
- Confirms the applied result reports only the target ontology source as changed.
- Confirms rejection leaves the copied source unchanged.
- Confirms a stale proposal is blocked without overwriting the externally changed source.
- Confirms a post-save verification failure restores the original copied source.
- Existing CLI regression tests continue to cover `validate`, `symbols`, `diff`, and machine-readable proposal commands.

The test copies the example project into a temporary directory for every scenario. It does not modify `examples/simple-ontology` in place and does not add Git automation or external service dependencies.

## Verification Commands

```bash
./gradlew :cli:test --tests com.entio.cli.Phase2EndToEndRegressionTest
./gradlew test
./gradlew build
./gradlew check
```

## Verification Results

- `./gradlew :cli:test --tests com.entio.cli.Phase2EndToEndRegressionTest` passed.
- `./gradlew test` passed.
- `./gradlew build` passed.
- `./gradlew check` passed.
- TypeScript extension tests were not run because Node/npm/tsc are not available in the current environment.

## Git Commit Status

No Git commit was created by this implementation step. The branch remains ready for review and for a focused commit after explicit authorization.

## Assumptions, Limitations, And Follow-Up Work

- The regression exercises the currently implemented `create-class` typed edit and existing proposal CLI boundaries; it does not add new edit kinds.
- The copied fixture is temporary and is removed with the test environment rather than persisted as a new example dataset.
- VS Code runtime behavior remains covered by the extension tests from Slice 13 and was not executed in this environment because the Node toolchain is unavailable.

## Notable Implementation Decisions

- The regression is located in the CLI test module so it verifies the cross-module workflow through the same reusable Kotlin APIs and CLI boundary used by the workbench.
- Rollback is tested by injecting a semantic-equivalence failure into `ProposalApplier`, ensuring the test remains deterministic while exercising the real source restoration path.
