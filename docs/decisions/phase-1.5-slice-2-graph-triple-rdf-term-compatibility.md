# Phase 1.5 Slice 2 Completion: Graph Triple RDF Term Compatibility

## ExecPlan Slice Implemented

Slice 2: Graph Triple RDF Term Compatibility.

## Goal

Update shared graph contracts to expose RDF term-aware triples while preserving enough compatibility for consumers to migrate in controlled downstream slices.

## Files Modified

- `core-types/src/main/kotlin/com/entio/core/GraphState.kt`
- `core-types/src/test/kotlin/com/entio/core/GraphTripleRdfTermTest.kt`
- `docs/decisions/phase-1.5-slice-2-graph-triple-rdf-term-compatibility.md`

## Tests Added Or Updated

- Added `GraphTripleRdfTermTest`.
- Covered that literals are not accepted as RDF resources.
- Covered graph triple value equality with RDF terms.
- Covered IRI, blank-node, and literal object representation.
- Covered literal datatype IRI and language tag preservation through graph triples.
- Covered existing Phase 1 `GraphTriple` construction compatibility.

## Verification Commands

```bash
./gradlew :core-types:test
./gradlew clean check
./gradlew check
```

## Verification Results

- `./gradlew :core-types:test`: passed.
- `./gradlew check`: initially failed because stale generated `graph-diff` test class files with ` 2` suffixes were present under `build/`.
- `./gradlew clean check`: passed.
- `./gradlew check`: passed after the clean verification removed stale generated test artifacts.

## Git Commit

A Git commit was created after verification.

## Assumptions, Limitations, And Follow-Up Work

- `GraphTriple.subjectResource` and `GraphTriple.objectTerm` are the RDF-term-aware fields for Phase 1.5 consumers.
- `GraphTriple.subject` and `GraphTriple.objectValue` remain temporary Phase 1 compatibility views so downstream modules can migrate in later approved slices without breaking compilation in this slice.
- Blank-node subjects are representable through `subjectResource`; the compatibility `subject` value is an IRI-shaped view for existing callers and should not be treated as a durable blank-node identity.

## Notable Implementation Decisions

- Kept the change inside `core-types`; no parser, validation, diff, CLI, project-loading, build, dependency, mutation, persistence, reasoning, or UI behavior was added.
- Preserved downstream source compatibility while still allowing RDF-aware graph construction for the next migration slices.
