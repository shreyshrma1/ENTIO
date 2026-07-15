# Phase 5 Slice 6: External Reuse, Local Extension, And Phase 4 Integration

## ExecPlan slice implemented

Slice 6 of `docs/execplans/0008-phase-5-external-ontology-browsing-schema-rag.md`.

## Goal

Translate explicitly approved external ontology intents into the existing Entio graph-change and baseline-aware proposal workflow while preserving original external IRIs and avoiding copies of bundled FIBO content.

## Files modified

- `semantic-engine/src/main/kotlin/com/entio/semantic/ExternalProposalIntentTranslator.kt` — translates approved external reuse, local subclass, and external module reference intents into import and local-extension graph changes.
- `semantic-engine/src/main/kotlin/com/entio/semantic/ExternalProposalPreparer.kt` — delegates translated changes to the existing `ProposalCreator` baseline/preview contract.
- `semantic-engine/src/test/kotlin/com/entio/semantic/ExternalProposalIntentTranslatorTest.kt` — verifies reuse, local subclass, external reference, and dependency-approval behavior.
- `semantic-engine/src/test/kotlin/com/entio/semantic/ExternalProposalPreparerTest.kt` — verifies proposal creation, baseline/preview generation, and source immutability.
- `validation-engine/src/main/kotlin/com/entio/validation/ExternalProposalIntentValidator.kt` — validates fixed FIBO identity, external intent fields, and dependency approval before translation.
- `validation-engine/src/test/kotlin/com/entio/validation/ExternalProposalIntentValidatorTest.kt` — verifies approved intents, stale package identity, unapproved dependencies, and unsupported sources.
- This completion record.

## Behavior added

- Reusing an external class or property adds the approved source-module `owl:imports` change and does not create a local copy or rewrite the external IRI.
- Creating a local subclass adds only the local class declaration, explicit `rdfs:subClassOf` relation to the external superclass, and the required source-module import.
- Adding an external ontology reference translates selected module IRIs into import changes while preserving the reference’s fixed release, commit, fingerprint, and module contracts for validation.
- Required user-visible dependencies must be `AlreadyAvailable` or explicitly approved as `NewlySelected`; missing or rejected dependencies block translation.
- `ExternalProposalPreparer` routes translated changes through the existing `ProposalCreator`, preserving baseline fingerprints, previews, and source-file impact without mutating files.
- The existing `MultiSourceAtomicApplier`, reasoning, SHACL, rollback, and semantic-equivalence paths remain the only later application path; no second writer or transaction system was introduced.

## Tests and verification

Passed:

```bash
./gradlew :semantic-engine:test --tests com.entio.semantic.ExternalProposalIntentTranslatorTest --tests com.entio.semantic.ExternalProposalPreparerTest :validation-engine:test --tests com.entio.validation.ExternalProposalIntentValidatorTest
./gradlew :semantic-engine:test
./gradlew :validation-engine:test
./gradlew :graph-diff:test
./gradlew test
```

## Assumptions and limitations

- The current project configuration model does not yet contain a persisted `externalOntologyReferences` field, so this slice validates the fixed reference contract and translates selected modules into existing ontology import changes; configuration-file application remains for the later boundary/integration work.
- Phase 4 reasoning and SHACL fingerprints already incorporate graph/import closure inputs through existing services; this slice does not duplicate or replace those services.
- CLI and VS Code presentation remain later slices. External package assets remain read-only and are never proposal targets.

## Git

- Commit: created for this slice after verification.
- Remote push: the slice branch is pushed after verification.
