# Phase 1 Slice 6: Loaded Symbol Extraction

## ExecPlan Slice Implemented

Slice 6: Loaded Symbol Extraction.

## Goal

Extract a stable list of basic ontology symbols from a loaded ontology.

## Files Modified

- `semantic-engine/src/main/kotlin/com/entio/semantic/SymbolExtractor.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/SymbolExtractorTest.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/SemanticEngineTestFixtures.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/OntologyParserTest.kt`
- `docs/decisions/phase-1-slice-6-loaded-symbol-extraction.md`

## Tests Added Or Updated

- Added `SymbolExtractorTest` coverage for:
  - extracting classes.
  - extracting properties.
  - extracting individuals.
  - extracting SHACL shapes.
  - extracting labels from `rdfs:label`.
  - stable symbol ordering.
- Added a shared test fixture helper for semantic-engine Turtle parsing tests.
- Updated `OntologyParserTest` to use the shared semantic-engine test fixture helper.

## Verification Commands Run

- `./gradlew :semantic-engine:test`
- `./gradlew check`

## Verification Results

- `./gradlew :semantic-engine:test`: passed.
- `./gradlew check`: passed.

## Git Commit

A focused Git commit is being created for this slice after verification.

## Assumptions, Limitations, And Follow-Up Work

- Symbol extraction uses explicit RDF type triples from parsed graph data and does not perform reasoning.
- Supported Phase 1 symbol kinds include class, property, individual, and SHACL shape symbols.
- Labels are extracted from `rdfs:label` only.
- Namespace term extraction remains unimplemented because the approved slice does not define a concrete namespace source model.

## Notable Implementation Decisions

- Jena types remain contained in `OntologyParser`; `SymbolExtractor` works from Entio-owned `LoadedOntology` and `GraphTriple` values.
- When a symbol has multiple type triples, the extractor picks the most specific supported kind using a deterministic priority order.
