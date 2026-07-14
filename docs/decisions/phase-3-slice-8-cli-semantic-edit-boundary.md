# Phase 3 Slice 8: CLI Semantic Edit And Proposal Boundary

## Status

Completed on the Slice 8 branch and merged into local `main` after verification.

## Goal

Expose structured Phase 3 semantic edit requests through the existing combined proposal lifecycle without moving RDF translation, semantic validation, source persistence, staging, or rollback policy into the CLI.

## Implementation

- Extended structured request handling for annotation-property creation, definitions, alternate labels, and general annotations.
- Added validation and conversion of semantic request fields into the existing `SemanticEditRequest` contracts.
- Delegated graph-change translation to `TypedOntologyEditTranslator`.
- Routed semantic requests through the existing proposal creator, preview, diff, validation, semantic-equivalence, apply, reject, stale-baseline, and rollback services.
- Kept structural requests on their existing staged-change and combined-preview path.
- Rejected mixed structural and semantic request batches with a structured error so request semantics remain unambiguous.
- Preserved existing `proposal-request` parsing and all Phase 2 structured command behavior.

## Tests And Verification

Added CLI coverage for:

- semantic annotation-property preview and rejection without source mutation;
- semantic annotation-property apply through the existing proposal applier;
- malformed semantic edit fields and structured error attribution.

Existing structured request tests continue to cover malformed JSON, unsupported schemas, structural requests, stale baselines, deletion dependencies, preview, rejection, and apply behavior.

Verification passed:

```text
./gradlew :cli:test
./gradlew test
```

## Scope Boundary

This slice does not add a second proposal or staging system, persistent request storage, UI dependencies, direct RDF serialization in CLI parsing, or direct source writes outside `ProposalApplier`.
