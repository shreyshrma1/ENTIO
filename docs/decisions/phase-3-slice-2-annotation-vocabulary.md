# Phase 3 Slice 2: Annotation Vocabulary And Explicit Metadata Extraction

## ExecPlan Slice Implemented

Slice 2, Annotation Vocabulary And Explicit Metadata Extraction, from `docs/execplans/0006-phase-3-semantic-description-layer.md`.

## Goal

Centralize the approved annotation vocabulary and extract explicit annotation statements without applying label policy, inference, or mutation.

## Files Modified

- `semantic-engine/src/main/kotlin/com/entio/semantic/AnnotationVocabulary.kt`
- `semantic-engine/src/main/kotlin/com/entio/semantic/ExplicitAnnotationExtractor.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/ExplicitAnnotationExtractorTest.kt`

## Changes

- Added constants for `rdfs:label`, `rdfs:comment`, `skos:prefLabel`, `skos:altLabel`, `skos:definition`, and `dcterms:source`.
- Added explicit structural-predicate classification.
- Added extraction of recognized metadata and general annotations for a subject.
- Preserved RDF resource, blank-node, plain-literal, datatyped-literal, and language-tagged values.
- Kept distinct statements and sorted results by a stable RDF-term-aware key.

## Tests And Verification

- `./gradlew :semantic-engine:test` — passed.
- `./gradlew test` — passed.

## Commit

This completion record is part of the Slice 2 change. The commit is created after the staged diff review.

## Assumptions And Limitations

- Extraction operates on explicit statements already represented by Entio graph terms.
- Preferred-label selection, descriptor assembly, inference, validation, CLI behavior, and source mutation remain later slices.
