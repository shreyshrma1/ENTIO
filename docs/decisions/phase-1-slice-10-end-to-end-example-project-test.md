# Phase 1 Slice 10 Completion: End-To-End Example Project Test

## ExecPlan Slice Implemented

Slice 10: End-To-End Example Project Test.

## Goal

Verify the full Phase 1 path against `examples/simple-ontology`.

## Files Modified

- `cli/src/test/kotlin/com/entio/cli/CliExampleProjectTest.kt`
- `docs/decisions/phase-1-slice-10-end-to-end-example-project-test.md`

## Tests Added Or Updated

- Added a happy-path CLI integration test for `examples/simple-ontology` that verifies:
  - `entio validate` succeeds.
  - `entio symbols` lists the expected symbols in stable order.
  - `entio diff` reports a controlled before/after change.
- Added a small invalid project test that verifies deterministic validation failure output.

## Verification Commands

- `./gradlew test`
- `./gradlew build`
- `./gradlew check`

## Verification Results

- `./gradlew test`: passed.
- `./gradlew build`: passed.
- `./gradlew check`: passed.

## Git Commit Status

A Git commit was created after the user explicitly authorized commit and push.

## Assumptions, Limitations, And Follow-Up

- The existing `examples/simple-ontology` fixture was valid, so it was not modified.
- The before/after diff fixture is created in a temporary directory during the test to avoid adding a broader fixture corpus.
- The integration test is placed in the `cli` module because the slice verifies the end-to-end path through the public CLI surface.

## Implementation Decisions

- The test discovers the repository root by walking up from the test working directory until it finds `examples/simple-ontology/entio.yaml`, which keeps it stable whether Gradle starts tests from the root project or the `cli` project directory.
- The invalid project test creates only the minimal invalid fixture needed to verify the validation path.
