# Phase 1 Slice 7: Validation Report Generation

## ExecPlan Slice Implemented

Slice 7: Validation Report Generation.

## Goal

Produce deterministic validation reports across project config loading, ontology source resolution, Turtle parsing, and symbol extraction checks.

## Files Modified

- `validation-engine/src/main/kotlin/com/entio/validation/ProjectValidator.kt`
- `validation-engine/src/main/kotlin/com/entio/validation/ValidationIssueSorter.kt`
- `validation-engine/src/test/kotlin/com/entio/validation/ProjectValidatorTest.kt`
- `validation-engine/src/test/kotlin/com/entio/validation/ValidationIssueSorterTest.kt`
- `docs/decisions/phase-1-slice-7-validation-report-generation.md`

## Tests Added Or Updated

- Added `ProjectValidatorTest` coverage for:
  - valid project reports.
  - missing project root.
  - project root that is not a directory.
  - missing `entio.yaml`.
  - invalid YAML.
  - missing required config fields.
  - duplicate ontology source IDs.
  - unsafe source paths.
  - missing ontology files.
  - invalid Turtle.
  - deterministic issue ordering.
- Added `ValidationIssueSorterTest` coverage for severity, code, source, and message ordering.

## Verification Commands Run

- `./gradlew :validation-engine:test`
- `./gradlew check`

## Verification Results

- `./gradlew :validation-engine:test`: passed.
- `./gradlew check`: passed.

## Git Commit

A focused Git commit is being created for this slice after verification.

## Assumptions, Limitations, And Follow-Up Work

- `ProjectValidator` stops after config or source resolution failures because later checks require successful earlier outputs.
- Turtle parse failures are collected across all resolved ontology sources before the report is returned.
- Symbol extraction is checked for crash-free execution, but no semantic symbol-quality rules are introduced in this slice.
- CLI output and exit-code behavior remain in a later approved slice.

## Notable Implementation Decisions

- Validation uses public `semantic-engine` APIs rather than duplicating config loading, source resolution, parsing, or symbol extraction logic.
- Validation issues are sorted deterministically by severity, code, source, and message.
