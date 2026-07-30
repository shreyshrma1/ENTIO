# Phase 11.5+ Slice 4 Safe Ordering And Preview

Status: Complete

Date: 2026-07-30

## ExecPlan Slice Implemented

Slice 4: Resolve References, Order Dependencies, Enforce Limits, And Preview.

## Goal

Turn compiled semantic patterns into deterministic, bounded, dependency-safe
recommendations that remain consumable by the existing proposal preview path.

## Files Modified

- `core-types/src/main/kotlin/com/entio/core/DocumentAnalysisPipelineContracts.kt`
- `semantic-engine/src/main/kotlin/com/entio/semantic/DocumentSemanticPlanCompiler.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/DocumentSemanticPlanCompilerTest.kt`
- `docs/decisions/phase-11.5-plus-slice-4-safe-ordering-and-preview.md`

## Implementation

The compiler now:

- derives exact operation dependencies from temporary references;
- rejects unresolved dependencies and dependency cycles;
- applies a stable topological order with declarations before use;
- calculates expanded typed-edit counts from deterministic compiler output;
- blocks duplicate operations and no-ops already present in current work;
- blocks stale ontology and current-work fingerprints;
- enforces 20 expanded edits per recommendation and 100 per task;
- splits oversized groups only across disconnected dependency closures that
  are independently coherent;
- blocks an indivisible oversized closure with
  `atomic-group-exceeds-limit`;
- preserves the original semantic group ID on safely split compiler results.

The additive `sourceGroupId` result field identifies the approved semantic
group when one group is safely divided into bounded compiled parts.

Final IRIs remain generated deterministically from the approved namespace and
temporary reference. Existing compiler kind and collision checks remain in
force.

## Tests Added Or Updated

The focused compiler suite now additionally covers:

- declaration-before-use topological ordering;
- exact new-to-new dependencies;
- safe splitting of independent closures;
- blocking an oversized atomic closure;
- stale ontology and current-work fingerprints;
- duplicate current-work prevention;
- whole-task edit-limit enforcement.

The full semantic-engine suite covers existing reference, kind, collision,
source, optional-leaf, dependency-closure, and verifier behavior. The existing
web-server document draft/proposal integration test confirms the complete
compiled typed batch still enters the established proposal preview path.

## Verification

- `./gradlew :semantic-engine:test` — passed.
- `./gradlew :semantic-engine:check` — passed.
- `./gradlew :web-server:test --tests '*DocumentDraftProposalIntegrationTest*'`
  — passed.
- `git diff --check` — passed.

## Git Commit

A focused Git commit will be created on
`feature/phase-11-5-plus-slice-4-safe-ordering-preview` after this completion
record is reviewed and staged with the implementation.

## Assumptions And Limitations

- A safe split is permitted only between disconnected operation dependency
  components. A connected component is treated as atomic.
- Provider migration remains in Slice 5; model output still does not control
  the ordering, counts, dependencies, or final IRIs implemented here.
- The proposal, apply, reload, and rollback architecture is unchanged.

## Notable Decisions

- Task-limit overflow blocks the compiled task instead of silently dropping
  semantic items.
- Deterministic operation keys include operation kind, declaration, and typed
  operands, excluding only the already-validated source operand.
- Split result IDs are stable, one-based, zero-padded part identifiers.
