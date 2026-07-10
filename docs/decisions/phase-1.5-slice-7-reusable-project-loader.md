# Phase 1.5 Slice 7 Completion: Reusable ProjectLoader

## ExecPlan Slice Implemented

Slice 7: Reusable ProjectLoader.

## Goal

Add the canonical project-loading API in `semantic-engine`.

## Files Modified

- `core-types/src/main/kotlin/com/entio/core/EntioProject.kt`
- `core-types/src/test/kotlin/com/entio/core/CoreTypesConstructionTest.kt`
- `semantic-engine/src/main/kotlin/com/entio/semantic/ProjectLoader.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/ProjectLoaderTest.kt`
- `docs/decisions/phase-1.5-slice-7-reusable-project-loader.md`

## Tests Added Or Updated

- Added `ProjectLoaderTest`.
- Covered loading `examples/simple-ontology`.
- Covered returned project aggregate contents: config, resolved sources, loaded ontologies, symbols, and combined graph state.
- Covered deterministic multi-source loading.
- Covered duplicate triple collapse in the combined graph while preserving source-specific ontology graphs.
- Covered structured failures for config loading, source resolution, Turtle parsing, and symbol extraction.
- Updated the core construction test for the `EntioProject.graph` aggregate field.

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

- `EntioProject` now includes a combined `GraphState` because the approved Slice 7 requirements call for a returned project aggregate with graph state.
- `ProjectLoader` composes existing services: `ProjectConfigLoader`, `OntologySourceResolver`, `OntologyParser`, and `SymbolExtractor`.
- Project loading returns a failure if any ontology source fails to parse, rather than returning partial success.
- Combined symbols are sorted deterministically by IRI, source ID, kind, and label.
- Duplicate triples are collapsed only in `EntioProject.graph`; source-specific `LoadedOntology` graphs remain unchanged.

## Notable Implementation Decisions

- Kept `ProjectLoader` in `semantic-engine`.
- Kept Jena contained behind `OntologyParser`.
- Did not edit `validation-engine`, `graph-diff`, `cli`, `shared`, build files, or dependencies.
- Did not add CLI formatting, exit-code behavior, ontology mutation, persistence, reasoning, approval, version storage, UI, server, or document-ingestion behavior.
