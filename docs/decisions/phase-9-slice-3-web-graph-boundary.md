# Phase 9 Slice 3 Completion: Web Graph Boundary

## Status

Complete.

## Slice boundary

This slice adds only the versioned, read-only Ktor boundary for the semantic graph service completed in Slice 2. It adds graph DTOs, an adapter, two GET routes, focused tests, and this completion record. It does not add graph traversal to Ktor, mutation routes, persistence, caching, a new identity or authorization system, WebSocket messages, or changes to staging, proposals, AI, reasoning, SHACL, FIBO, CLI, or VS Code behavior.

One user-authorized scope exception updates the stale CLI and CLI regression-test calls to `ExternalProposalPreparer.prepare` with named arguments. The semantic API had gained two defaulted parameters, so the former positional calls no longer compiled. This is a compilation-only compatibility repair and does not change external-catalog behavior.

The same authorization covers refreshing historical CLI regression expectations after the shared example ontology was expanded: the deterministic symbol baseline, current FIBO Customer identity, a still-missing FIBO dependency, collision label, and SHACL target now match the checked-in fixture while preserving each test's original behavioral purpose.

## Delivered behavior

- `GET /api/v1/projects/{projectId}/graph` returns a bounded root or entity-centered graph page.
- `GET /api/v1/projects/{projectId}/graph/neighborhood` returns a bounded category-filtered neighborhood page.
- Both routes use the registered-project boundary, development identity, and existing `BROWSE` authorization action.
- Responses expose safe source IDs and deterministic SHA-256 node and edge IDs. They do not expose filesystem paths, raw source content, semantic-library objects, or unrestricted triples.
- A fingerprint is calculated from exactly the selected local ontology/data sources. An expected fingerprint mismatch returns a stale conflict before data can be merged.
- Semantic cursors remain server-side. Browser continuations are random, single-use, process-local IDs bound to user, project, selected sources, fingerprint, query type, entity/seed, and categories.
- Continuations expire after ten minutes. Unknown, expired, process-lost, or scope-mismatched IDs are rejected without exposing their stored state.
- Source, entity, category, load, permission, fingerprint, and continuation failures map to structured versioned web errors.

## Verification

The slice is verified with the commands required by the approved ExecPlan:

```bash
./gradlew :web-server:test
./gradlew :web-server:check
./gradlew test
git diff --check
```

Focused route tests additionally verify DTO bounds and identifiers, fingerprint-protected neighborhood reads, identity rejection, invalid source/entity handling, stale fingerprints, lost continuations, and absence of filesystem paths and Turtle source content.
