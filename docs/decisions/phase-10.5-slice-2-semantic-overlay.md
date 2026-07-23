# Phase 10.5 Slice 2 Semantic Overlay

## ExecPlan Slice Implemented

Slice 2: Semantic Inferred-Fact Projection And Graph Overlay.

## Goal

Project complete reasoning results into deterministic bounded Explore facts and ontology-map edges without changing reasoning rules, layout semantics, or ontology state.

## Files Modified

- `semantic-engine/src/main/kotlin/com/entio/semantic/InferredFactsReadService.kt`
- `semantic-engine/src/main/kotlin/com/entio/semantic/OntologyGraphService.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/InferredFactsReadServiceTest.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/OntologyGraphServiceTest.kt`
- `docs/decisions/phase-10.5-slice-2-semantic-overlay.md`

## Implementation

- Added complete-result projection for inferred subclass relationships, individual types, and object-property assertions.
- Reused Phase 10 semantic identities for its three supported fact families.
- Added the approved deterministic effective domain/range projection from asserted property schema plus inferred superclass relationships.
- Excluded anonymous, unknown, invalid-predicate, asserted-duplicate, and incomplete facts.
- Added engine-owned field placements, stable ordering, deduplication, and a 100-fact bound.
- Extended ontology graph extraction to layer inferred applied/proposal edges and overlay summaries.
- Kept inferred subclass, domain/range, and type edges out of asserted root/tree selection.
- Preserved existing asserted-only method defaults.

## Tests Added Or Updated

- All supported inferred fact families and effective domain/range.
- Stable ordering and asserted duplicate suppression.
- Failed/incomplete result handling.
- Bounded truncation.
- Proposal fingerprint provenance.
- Inferred graph-edge provenance and overlay metadata.
- Identical asserted overview node placement with the overlay on and off.

## Verification Commands

- `./gradlew :semantic-engine:test` — passed.
- `./gradlew :semantic-engine:build` — passed.
- `./gradlew :core-types:test :semantic-engine:test` — passed.
- `git diff --check` — passed.
- `git status --short` — showed only expected Slice 2 files before commit.

## Git Commit

Yes. This completion record is included in the focused Slice 2 commit.

## Assumptions And Limitations

- Inferred graph edges are emitted only when both endpoints resolve to existing scoped graph nodes.
- Proposal graph assembly and current-result orchestration are deferred to Slice 3.
- The semantic service never runs the reasoner; it consumes a verified retained result.

## Notable Decisions

- Effective domain/range does not add reasoner rules or materialization support.
- Asserted facts are removed from the inferred projection before limits are applied.
- Primary map placement continues to use asserted relationships only.
