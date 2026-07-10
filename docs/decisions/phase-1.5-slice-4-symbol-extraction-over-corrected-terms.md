# Phase 1.5 Slice 4 Completion: Symbol Extraction Over Corrected Terms

## ExecPlan Slice Implemented

Slice 4: Symbol Extraction Over Corrected Terms.

## Goal

Update symbol extraction to use the corrected RDF term model.

## Files Modified

- `semantic-engine/src/main/kotlin/com/entio/semantic/SymbolExtractor.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/SymbolExtractorTest.kt`
- `docs/decisions/phase-1.5-slice-4-symbol-extraction-over-corrected-terms.md`

## Tests Added Or Updated

- Updated symbol extraction coverage to exercise RDF-term-aware behavior.
- Added coverage for ignoring non-literal labels.
- Added coverage for blank-node typed resources not crashing extraction.
- Existing symbol extraction tests continue to cover classes, properties, individuals, shapes, labels, and deterministic ordering.

## Verification Commands

```bash
./gradlew :semantic-engine:test
./gradlew check
```

## Verification Results

- `./gradlew :semantic-engine:test`: passed.
- `./gradlew check`: passed.

## Git Commit

A Git commit was created after verification.

## Assumptions, Limitations, And Follow-Up Work

- Symbol extraction now reads `GraphTriple.subjectResource` and `GraphTriple.objectTerm`.
- Only IRI subjects become `LoadedSymbol` values because `LoadedSymbol.iri` is still an `Iri`.
- Labels are extracted only from `RdfLiteral` objects.
- Non-literal labels are ignored deterministically.
- Blank-node typed subjects are ignored rather than converted into durable symbols.

## Notable Implementation Decisions

- Matched RDF type objects through RDF resource terms rather than compatibility strings.
- Preserved existing symbol kind behavior for known RDF, RDFS, OWL, and SHACL type IRIs.
- Did not add ontology reasoning or OWL class-expression interpretation.
- Did not edit parser behavior, validation, graph diff, CLI, build files, dependencies, or shared utilities.
