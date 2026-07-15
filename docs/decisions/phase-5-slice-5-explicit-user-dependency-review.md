# Phase 5 Slice 5: Explicit User Dependency Review And Validation

## ExecPlan slice implemented

Slice 5 of `docs/execplans/0008-phase-5-external-ontology-browsing-schema-rag.md`.

## Goal

Produce a bounded dependency review for one selected external catalog element while keeping the pinned FIBO/OMG Commons package-runtime closure implementation-only and validating user-visible selections deterministically.

## Files modified

- `semantic-engine/src/main/kotlin/com/entio/semantic/ExternalDependencyReviewer.kt` — builds deterministic dependency sets from explicit catalog descriptors, package metadata, and asserted local graph facts.
- `semantic-engine/src/test/kotlin/com/entio/semantic/ExternalDependencyReviewerTest.kt` — verifies bounded reviews, explicit class/property dependencies, package-runtime visibility, and already-available local/module state.
- `validation-engine/src/main/kotlin/com/entio/validation/ExternalDependencyReviewValidator.kt` — validates dependency-set status, required selections, package availability, stale states, required fields, duplicate entries, conflicting selections, maturity metadata, and optional rejections.
- `validation-engine/src/test/kotlin/com/entio/validation/ExternalDependencyReviewValidatorTest.kt` — verifies valid, incomplete, unsupported, stale, duplicate, conflicting, and warning-only dependency outcomes.
- This completion record.

## Behavior added

- Selected classes expose direct semantic-parent dependencies.
- Selected object and datatype properties expose explicit domain and range dependencies.
- The source ontology module is user-visible and required, while the pinned package-runtime closure is represented separately as one implementation-only transitive dependency.
- Package-resolved ontology imports remain implementation-only and are not expanded into individual manual approvals.
- Non-release maturity creates an explicit metadata acknowledgement dependency.
- Asserted local graph references and imported modules are recognized as already available without using inferred facts.
- Dependency entries preserve category, requirement, closure, visibility, selection, original IRIs, source modules, maturity, package availability, and deterministic reasons.
- Newly discovered required user-visible dependencies begin as `Missing`; only `AlreadyAvailable` or explicitly approved `NewlySelected` entries satisfy validation.
- Validation rejects incomplete, conflicting, invalid, unsupported, stale, duplicate, and unapproved required dependency states without mutating a project, proposal, or bundled ontology asset.

## Tests and verification

Passed:

```bash
./gradlew :semantic-engine:test --tests com.entio.semantic.ExternalDependencyReviewerTest :validation-engine:test --tests com.entio.validation.ExternalDependencyReviewValidatorTest
./gradlew :semantic-engine:test
./gradlew :validation-engine:test
./gradlew test
```

## Assumptions and limitations

- The current Phase 5 project model does not yet expose the planned `externalOntologyReferences` configuration field, so approved external module references are represented through explicit dependency selections and asserted graph imports until the integration slice.
- Dependency review uses explicit catalog descriptors and asserted local triples only; it does not compute a transitive semantic reasoning closure.
- CLI and VS Code presentation, proposal translation, source/configuration mutation, and Phase 4 integration remain later slices.

## Git

- Commit: created for this slice after verification.
- Remote push: the slice branch is pushed after verification.
