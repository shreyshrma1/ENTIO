# Phase 2.5+ Slice 2 Completion: IRI Resolution

## Status

Implemented on `feature/phase-2.5-plus-slice-2-iri-resolution`.

## Delivered

- Added optional `iriNamespace` parsing with backward-compatible loading for projects that omit it.
- Added deterministic namespace validation and structured configuration issues.
- Added exact label and explicit-IRI resolution with kind/source filters and stable ambiguity ordering.
- Added deterministic class/individual UpperCamel and property lowerCamel local-name normalization.
- Added missing-namespace, invalid-label, collision, and deterministic suffix outcomes for generated IRIs.
- Kept resolution and generation in Kotlin; no UI logic, source mutation, persistence, randomness, or new dependencies were added.

## Verification

- `./gradlew :semantic-engine:test --no-daemon --console=plain`
- `./gradlew :validation-engine:test --no-daemon --console=plain`
- `./gradlew test --no-daemon --console=plain`

All commands passed.

## Scope Check

This slice does not analyze deletion dependencies, normalize staged changes, detect combined conflicts, add CLI operations, or change the VS Code extension. Those concerns remain for later slices in the approved ExecPlan.
