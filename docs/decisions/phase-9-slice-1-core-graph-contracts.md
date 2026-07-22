# Phase 9 Slice 1: Core Graph Contracts

## ExecPlan Slice Implemented

Phase 9 Slice 1, Core Graph Read Contracts.

## Goal

Define immutable, UI-independent core contracts for supported graph nodes, asserted edges, bounded queries, deterministic paging, opaque adapter continuations, and ambiguous cross-source diagnostics.

## Files Modified

- `core-types/src/main/kotlin/com/entio/core/OntologyGraphContracts.kt`
- `core-types/src/test/kotlin/com/entio/core/OntologyGraphContractsTest.kt`
- `docs/decisions/phase-9-slice-1-core-graph-contracts.md`

## Tests Added Or Updated

`OntologyGraphContractsTest` covers all supported node, edge, load, expansion, limit, identity, summary, page, continuation, and invalid-state behavior.

## Verification Commands

- `./gradlew :core-types:test` — passed.
- `./gradlew :core-types:check` — passed.
- `git diff --check` — passed.

## Git Commit

A focused Slice 1 commit is created on `phase-9-slice-1-core-graph-contracts`; its hash is recorded by Git and in the slice handoff.

## Assumptions, Limitations, And Follow-Up

- Semantic-engine cursors remain explicit deterministic state; the web adapter will replace them with server-owned opaque continuation IDs in Slice 3.
- Node summaries contain bounded human-readable labels but no layout, HTTP, serialization, or UI coordinates.
- Only asserted Phase 9 relationships and supported local entity kinds are modeled.

## Notable Implementation Decisions

Graph identity is exactly `(sourceId, entityIri)`. Every returned page enforces complete edge endpoints, and object-assertion edges retain their predicate IRI so parallel predicates remain distinct.
