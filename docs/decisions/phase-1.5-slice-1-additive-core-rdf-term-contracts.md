# Phase 1.5 Slice 1 Completion: Additive Core RDF Term Contracts

## ExecPlan Slice Implemented

Slice 1: Additive Core RDF Term Contracts.

## Goal

Introduce Entio-owned RDF term contracts in `core-types` without breaking existing Phase 1 graph consumers.

## Files Modified

- `core-types/src/main/kotlin/com/entio/core/Iri.kt`
- `core-types/src/main/kotlin/com/entio/core/RdfTerm.kt`
- `core-types/src/test/kotlin/com/entio/core/RdfTermTest.kt`
- `docs/decisions/phase-1.5-slice-1-additive-core-rdf-term-contracts.md`

## Tests Added Or Updated

- Added `RdfTermTest`.
- Covered construction of IRI resources, blank-node resources, and literals.
- Covered that literals are not RDF resources.
- Covered preservation of literal datatype IRI and language tag.
- Covered that existing `GraphTriple` construction remains source-compatible.

## Verification Commands

```bash
./gradlew :core-types:test
./gradlew check
```

## Verification Results

- `./gradlew :core-types:test`: passed.
- `./gradlew check`: passed.

## Git Commit

A Git commit was created after verification.

## Assumptions, Limitations, And Follow-Up Work

- `Iri` now implements `RdfResource` so existing IRI-backed values can participate in the additive RDF term model.
- `GraphTriple` and `GraphState` were intentionally left source-compatible with Phase 1 consumers.
- The existing graph object model still stores `objectValue` as a string. The RDF-term-aware triple migration is reserved for the next approved ExecPlan slice.

## Notable Implementation Decisions

- Kept the new contracts in `core-types` with no Jena, RDF4J, OWL API, parser, validation, diff, CLI, project-loading, mutation, persistence, or reasoning behavior.
- Represented blank-node values with the `"_:"` prefix for a clear local display value while documenting that blank-node IDs are not durable business identities.
