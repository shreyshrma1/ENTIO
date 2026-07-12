# Phase 2.5 Slice 2 Completion: Deterministic Edit-Specific Validation

## ExecPlan Slice Implemented

Slice 2: Deterministic Edit-Specific Validation.

## Goal

Extend the existing deterministic proposal validation boundary with edit-specific checks while keeping semantic interpretation in the Kotlin validation engine.

## Files Modified

- `validation-engine/src/main/kotlin/com/entio/validation/ProposalValidator.kt`
- `validation-engine/src/test/kotlin/com/entio/validation/ProposalValidatorTest.kt`
- `docs/decisions/phase-2.5-slice-2-deterministic-edit-specific-validation.md`

## Implemented Behavior

- Validates referenced resources and expected class or property kinds using explicit graph statements.
- Rejects incompatible object-property and datatype-property assertions.
- Validates the supported literal datatypes and language-tag shape.
- Checks known property domain and range compatibility without inference or OWL reasoning.
- Rejects self-subclass relationships and preserves existing duplicate-addition and missing-removal checks.
- Keeps validation issue ordering deterministic through the existing sorter.

## Tests Added

- Missing referenced resource for an object-property assertion.
- Datatype assertion against an object property.
- Invalid typed literal against a known datatype range.
- Explicit domain and range incompatibility.
- Self-subclass rejection.

## Verification Commands

```bash
./gradlew :validation-engine:test
./gradlew :semantic-engine:test
./gradlew test
```

## Verification Results

- `./gradlew :validation-engine:test` passed.
- `./gradlew :semantic-engine:test` passed.
- `./gradlew test` passed.

## Git Commit Status

The slice is complete and ready for its focused commit, remote branch push, and clean local merge into `main`.

Focused commit: `39a841d4d4ab17829cc49facb22c753aa439fba8`.

## Assumptions And Limitations

- Compatibility checks use explicit current and planned graph statements only.
- Full OWL reasoning, inference, and SHACL validation remain out of scope.
- The supported literal datatype set remains the Phase 2.5 specification’s small initial set.

## Notable Implementation Decisions

- Validation infers the edit category from the existing graph change predicates and RDF term types because the proposal contract intentionally remains independent of CLI request models.
- Existing preview validation remains authoritative for generic duplicate additions and missing removals; edit-specific checks add semantic context without replacing that shared behavior.
