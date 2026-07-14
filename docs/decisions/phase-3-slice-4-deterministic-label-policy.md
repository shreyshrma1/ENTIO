# Phase 3 Slice 4: Deterministic Labels And Semantic Ordering

## ExecPlan Slice Implemented

Slice 4, Deterministic Labels, Definitions, And Semantic Ordering, from `docs/execplans/0006-phase-3-semantic-description-layer.md`.

## Goal

Apply the approved deterministic label policy, collect alternate labels and definitions, expose ambiguity, and keep semantic metadata stable across repeated runs.

## Files Modified

- `core-types/src/main/kotlin/com/entio/core/SemanticDescriptionContracts.kt`
- `semantic-engine/src/main/kotlin/com/entio/semantic/SemanticLabelPolicy.kt`
- `semantic-engine/src/main/kotlin/com/entio/semantic/SemanticDescriptorAssembler.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/SemanticLabelPolicyTest.kt`

## Changes

- Added the Entio-owned preferred-label source and ambiguous-language contract fields.
- Implemented configured-language priority for `skos:prefLabel` and `rdfs:label`, followed by no-language values, stable available labels, and readable IRI local-name fallback.
- Added exact duplicate suppression for alternate labels and definitions while retaining RDF language and datatype metadata.
- Kept `rdfs:comment`, source metadata, and other non-label values as general annotations.
- Routed descriptor assembly through the deterministic policy without adding fuzzy matching, external lookup, AI, or inference.

## Tests And Verification

- `./gradlew :semantic-engine:test` — passed.
- `./gradlew test` — passed.

## Commit

This completion record is part of the Slice 4 change. The commit is created after the staged diff review.

## Assumptions And Limitations

- Ambiguous preferred-label languages are exposed for later validation integration; Slice 4 does not create validation issues itself.
- Language-tagged literals retain the RDF `rdf:langString` datatype when the parser supplies it.
- Search ranking and typed semantic editing remain later slices.
