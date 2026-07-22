# Phase 9 Slice 2: Semantic Graph Service

## ExecPlan Slice Implemented

Phase 9 Slice 2, Deterministic Local Ontology Graph Service.

## Goal

Build bounded, deterministic read-only graph pages from existing asserted local semantic descriptors without moving semantic interpretation into web or UI code.

## Files Modified

- `semantic-engine/src/main/kotlin/com/entio/semantic/OntologyGraphService.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/OntologyGraphServiceTest.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/ProjectLoaderTest.kt`
- `docs/decisions/phase-9-slice-2-semantic-graph-service.md`

## Tests Added Or Updated

`OntologyGraphServiceTest` covers supported nodes and edge kinds, excluded entities and literals, deterministic neighborhoods, immutability, same-source and cross-source resolution, ambiguity diagnostics, 1,000-entity bounds, and extraction performance.

With explicit user authorization, `ProjectLoaderTest` was aligned with the repository's already-committed expanded example ontology. No example fixture or loader behavior changed.

## Verification Commands

- `./gradlew :semantic-engine:test --tests com.entio.semantic.OntologyGraphServiceTest --tests com.entio.semantic.ProjectLoaderTest` — passed.
- `./gradlew :semantic-engine:test` — passed, 176 tests.
- `./gradlew :semantic-engine:check` — passed.
- `git diff --check` — passed.

## Git Commit

A focused Slice 2 commit is created on `phase-9-slice-2-semantic-graph-service`; its hash is recorded by Git and in the slice handoff.

## Assumptions, Limitations, And Follow-Up

- The service consumes only descriptors assembled from `EntioProject.ontologies`; imported catalog descriptors are never supplied.
- Self-referential relationships are omitted because the Phase 9 edge contract requires distinct visible endpoints.
- Cursor state is deterministic engine data. Slice 3 owns opaque, user/project/fingerprint-bound continuation IDs.

## Notable Implementation Decisions

Endpoint resolution prefers an exact same-source declaration, uses a sole declaration in another allowed local source, and otherwise omits the relationship while incrementing a bounded diagnostic count. Paging always removes orphan edges.
