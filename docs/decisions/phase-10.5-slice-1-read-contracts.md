# Phase 10.5 Slice 1 Read Contracts

## ExecPlan Slice Implemented

Slice 1: Provenance-Aware Core Read Contracts.

## Goal

Add immutable, UI-neutral contracts for bounded applied/proposal inferred reads and inferred ontology-map provenance.

## Files Modified

- `core-types/src/main/kotlin/com/entio/core/InferredFactsReadContracts.kt`
- `core-types/src/main/kotlin/com/entio/core/InferenceMaterializationContracts.kt`
- `core-types/src/main/kotlin/com/entio/core/OntologyGraphContracts.kt`
- `core-types/src/test/kotlin/com/entio/core/InferredFactsReadContractsTest.kt`
- `core-types/src/test/kotlin/com/entio/core/OntologyGraphContractsTest.kt`
- `docs/decisions/phase-10.5-slice-1-read-contracts.md`

## Implementation

- Added fixed applied/proposal graph-state and inferred-read availability enums.
- Added supported inferred read kinds and engine-owned field placements.
- Added bounded inferred facts, overlay payloads, and graph overlay summaries with constructor invariants.
- Preserved Phase 10 semantic keys and allowed the audited read-only identity namespace for effective domain/range facts.
- Added inferred graph-edge provenance with a required applied/proposal graph state.
- Added optional, unique inferred overlay summaries to graph pages while preserving asserted-only defaults.

## Tests Added Or Updated

- Construct current applied and proposal overlays.
- Accept only approved semantic key prefixes.
- Reject invalid provenance, graph-state fingerprints, bounds, duplicates, and truncation metadata.
- Require inferred graph edges to identify their graph state.
- Reject duplicate graph overlay summaries.
- Preserve existing asserted edge and graph-page behavior.

## Verification Commands

- `./gradlew :core-types:test` — passed.
- `./gradlew :core-types:build` — passed.
- `git diff --check` — passed.
- `git status --short` — showed only the expected Slice 1 files before commit.

## Git Commit

Yes. This completion record is included in the focused Slice 1 commit.

## Assumptions And Limitations

- These contracts contain no job orchestration, semantic projection, web DTOs, or presentation logic.
- Effective domain/range identity is supported by the neutral key type, but projection is deferred to Slice 2.
- Existing asserted graph callers remain source compatible because all new graph fields have asserted-only defaults.

## Notable Decisions

- Applied/proposal origin is separate from semantic fact identity.
- Kotlin supplies explicit field placements so UI clients do not interpret RDF predicates.
- Only current overlays may contain facts; updating, unavailable, failed, and off states remain metadata-only.
