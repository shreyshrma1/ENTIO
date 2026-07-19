# Phase 8 Slice 3: Bounded Retrieval Completion

## ExecPlan Slice Implemented

Phase 8 Slice 3, Fingerprinted Project Map, Bounded Retrieval, And Cache.

## Goal

Provide deterministic fingerprinted project summaries, layered search, paginated ontology neighborhoods, bounded project-analysis checks, and fingerprint-safe caching without serializing full ontology sources or adding a second semantic engine.

## Files Modified

- `web-server/src/main/kotlin/com/entio/web/ai/AiBoundedRetrievalContracts.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiProjectMapService.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiOntologyNeighborhoodService.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiContextCache.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiProjectMapServiceTest.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiOntologyNeighborhoodServiceTest.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiContextCacheTest.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiLargeOntologyRetrievalTest.kt`
- `docs/decisions/phase-8-slice-3-bounded-retrieval.md`

## Tests Added Or Updated

- Added stable ordering, fingerprint, summary-field, convention, and map-bound coverage.
- Added exact, normalized, semantic, and FIBO search-layer coverage with source preservation.
- Added paginated neighborhood source, entity, byte, provenance, and expansion bounds.
- Added cache hit/miss, owner isolation, fingerprint invalidation, and project invalidation coverage.
- Added generated 500- and 1,000-entity retrieval tests without committed duplicate ontology fixtures.

## Verification Commands

```bash
./gradlew :web-server:test --tests '*AiProjectMapServiceTest' --tests '*AiOntologyNeighborhoodServiceTest' --tests '*AiContextCacheTest' --tests '*AiLargeOntologyRetrievalTest'
./gradlew :web-server:test
git diff --check
```

## Verification Results

- Focused project-map, neighborhood, cache, and large-ontology tests: passed.
- Full `web-server` test suite: passed.
- `git diff --check`: passed.

## Git Commit

Yes. This artifact is included in `Add Phase 8 bounded ontology retrieval`.

## Assumptions And Limitations

- Inputs are structured snapshots produced by existing authoritative read services; this slice does not parse RDF or infer new semantic facts.
- Private draft-derived cache entries require owner and draft fingerprints; stable public maps may omit an owner.
- FIBO candidates are supplied by the existing pinned catalog adapter and are capped at ten.
- Model/provider calls, task execution, routes, and UI remain outside this slice.

## Notable Decisions

- Project maps are keyed by authoritative project fingerprint and retrieval-policy version.
- Neighborhoods preserve asserted/inferred markers and relevant reasoning, SHACL, and draft fingerprints.
- Expansion is explicit by approved category and page; a normal lookup never emits a complete hierarchy or source.
- Cache keys contain every authoritative fingerprint relevant to the cached resource and never contain credentials.
