# Phase 11.5+ Slice 1 Semantic Contracts

Status: Complete

Date: 2026-07-30

## ExecPlan Slice Implemented

Slice 1: Add Provider-Neutral Semantic-Plan Contracts.

## Goal

Represent connected document meaning as bounded, deterministic semantic items
and recommendation groups without allowing the model-facing plan to carry
final IRIs or low-level Entio operations.

## Files Modified

- `core-types/src/main/kotlin/com/entio/core/DocumentAnalysisPipelineContracts.kt`
- `core-types/src/test/kotlin/com/entio/core/DocumentAnalysisPipelineContractsTest.kt`
- `docs/decisions/phase-11.5-plus-slice-1-semantic-contracts.md`

## Implementation

The core contracts now define:

- additive Phase 11.5+ prompt, request, response, pattern-registry, compiler,
  and review versions;
- semantic item kinds, outcomes, task-local and exact-alignment references,
  recommendation groups, and complete semantic plans;
- explicit `MatchedExisting` and `Blocked` discovery dispositions;
- compilation statuses, safe failures, final reference mappings, separate
  quality metrics, and optional compilation confidence;
- verified semantic plans and deterministic compiled recommendation results.

Constructors enforce approved bounds, deterministic ordering, unique IDs,
compatible reference roles, complete evidence, resolved task-local references,
critic dispositions, and absence of self-reference. Final IRIs and low-level
operations exist only in compiler-result contracts, not in provider-facing
semantic-plan items.

## Tests Added Or Updated

Focused core contract tests cover:

- every approved semantic item kind and its compatible reference roles;
- invalid roles, duplicate identities, self-reference, missing evidence, and
  unresolved references;
- deterministic plan and group ordering;
- matched-existing and blocked coverage dispositions;
- separate semantic-coverage and compilation-success metrics;
- compilation confidence being not applicable for review-only outcomes;
- final-reference resolution after compilation;
- the absence of final-IRI and operation fields from provider-facing semantic
  plan items;
- independence from provider, server, parser, filesystem, and UI types.

## Verification

- `git diff --check` — passed.
- `./gradlew :core-types:test` — passed.
- `./gradlew :core-types:check` — passed.

## Git Commit

A focused Git commit will be created on
`feature/phase-11-5-plus-slice-1-semantic-contracts` after this completion
record is reviewed and staged with the implementation.

## Assumptions And Limitations

- The contracts are additive. Legacy Phase 11.5 final-plan contracts remain in
  place until their consumers migrate in later approved slices.
- Runtime completeness scoring belongs to Slice 2.
- Semantic compilation, pattern behavior, and typed-edit translation belong to
  Slice 3.
- Provider orchestration, public review contracts, and React presentation
  remain unchanged in this slice.

## Notable Decisions

- Provider-facing references identify either an exact verified alignment or a
  task-local semantic item. They do not contain an invented or final IRI.
- Review-only groups omit compilation confidence instead of presenting a
  misleading score.
- Semantic coverage and compilation success are represented as separate
  metrics so neither can conceal weakness in the other.
