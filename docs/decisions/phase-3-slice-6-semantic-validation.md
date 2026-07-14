# Phase 3 Slice 6: Semantic Validation And Proposal Lifecycle Integration

## Status

Completed on the Slice 6 branch and merged into local `main` after verification.

## Goal

Add deterministic validation for semantic metadata and integrate it with the existing proposal validation lifecycle without bypassing preview, stale-baseline, semantic-equivalence, approval, apply, rejection, or rollback safeguards.

## Implementation

- Added `SemanticMetadataValidator` in `validation-engine`.
- Integrated semantic metadata validation into `ProposalValidator`.
- Recognized annotation-property declarations as a valid property kind for proposal validation.
- Kept structural and ordinary property validation separate from standard semantic metadata predicates and declared annotation properties.
- Validated literal shape, language tags, supported datatypes, incompatible annotation-property kinds, missing semantic targets, and ambiguous preferred labels.
- Evaluated preferred-label ambiguity against the planned graph after removals and additions, so replacement edits do not retain removed values during validation.
- Preserved existing duplicate and missing-removal checks in `GraphChangePreviewer`, rather than duplicating those checks in the semantic validator.

## Tests And Verification

Added focused validation tests covering:

- invalid language tags and unsupported datatypes;
- non-literal standard metadata values;
- ambiguous preferred labels;
- missing semantic targets;
- object-property and annotation-property kind conflicts.

Verification passed:

```text
./gradlew :validation-engine:test
./gradlew :semantic-engine:test
./gradlew :graph-diff:test
./gradlew test
```

The typed semantic edit translator remains responsible for rejecting missing custom annotation-property references before graph changes are produced. The validator operates on ordinary graph changes and therefore does not infer whether an undeclared custom predicate was intended as an annotation property or an ordinary ontology property.

## Scope Boundary

This slice does not add OWL reasoning, SHACL validation, AI judgment, persistence, UI behavior, or new proposal lifecycle states. Existing preview and proposal services remain the source of truth for duplicate changes, missing removals, stale baselines, source mutation safety, and atomic apply or rollback behavior.
