# Phase 11.5+ Slice 6 Grouped Review And Provenance

Status: Complete

Date: 2026-07-30

## ExecPlan Slice Implemented

Slice 6: Update Grouped Review And Applied Provenance.

## Goal

Show semantic intent and deterministic compilation clearly while preserving
the existing human-review, staging, proposal, approval, and apply boundaries.

## Files Modified

- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentReviewWorkspace.kt`
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentApplyProvenanceCoordinator.kt`
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentIngestionWebService.kt`
- `web-server/src/test/kotlin/com/entio/web/ingestion/DocumentDraftProposalIntegrationTest.kt`
- `web-app/src/web/projectApi.ts`
- `web-app/src/workbench/document-ingestion/DocumentIngestionWorkspace.tsx`
- `web-app/src/workbench/document-ingestion/DocumentIngestionWorkspace.test.tsx`
- `docs/decisions/phase-11.5-plus-slice-6-grouped-review-and-provenance.md`

## Authorized Scope Clarification

The approved slice requires the new `Retain as documented rule` decision, but
the ExecPlan's allowed-file list omitted the existing route service that owns
document review decisions. The user explicitly authorized the narrow addition
of `DocumentIngestionWebService.kt` on 2026-07-30. No other scope was added.

## Implementation

Grouped review now presents:

- plain-language semantic intent;
- exact typed changes, generated IRIs, target sources, and dependencies;
- executable, mixed, review-only, and blocked states;
- evidence, critic dispositions, individual gates, and safe blockers;
- semantic coverage and compilation success as separate deterministic metrics;
- evidence, modeling, ontology-fit, compilation, and weakest-value overall
  confidence;
- `Not applicable` compilation confidence for pure review-only groups.

The browser sends only recommendation IDs, current-work checks, and supported
review decisions. It does not compile, resolve references, or validate
operations.

Executable and mixed groups continue through the existing private-draft and
proposal workflow. Their related review-only findings remain attached to
applied provenance. A pure review-only group can now be durably retained only
after the owner chooses `Retain as documented rule`. That record has exact
evidence and verified pipeline metadata, creates no typed operation, claims no
proposal, and does not alter ontology sources. Rejection and ordinary
review-only display do not write provenance.

## Tests Added Or Updated

- Focused provenance coverage verifies explicit review-only retention without
  an ontology edit or proposal.
- Browser tests cover semantic intent, generated IRIs, separate quality
  metrics, compilation confidence, and the ID-only retain decision.
- Existing review, route, staging, mixed-provenance, apply, and rollback tests
  remain green.

## Verification

- `./gradlew :web-server:test --tests '*DocumentReview*' --tests '*DocumentApplyProvenance*'`
  — passed.
- `./gradlew :web-server:test --tests '*DocumentIngestionRouteIntegrationTest*'`
  — passed.
- `./gradlew :web-server:test --tests '*DocumentDraftProposalIntegrationTest*'`
  — passed.
- `npm --prefix web-app test -- --run DocumentIngestionWorkspace` — passed.
- `npm --prefix web-app run build` — passed.
- `npm --prefix web-app run test:e2e -- document-ingestion.spec.ts` — passed.
- `./gradlew :web-server:check` — passed.
- `git diff --check` — passed.

## Git Commit

A focused Git commit will be created on
`feature/phase-11-5-plus-slice-6-review-provenance` after this completion
record is reviewed and staged with the implementation.

## Assumptions And Limitations

- The existing durable applied-document provenance repository is reused; no
  new repository or persistence system was added.
- Retained review-only records use the existing confirmation-compatible
  no-operation provenance shape and are distinguished by their retained
  review-only findings and semantic pipeline version.
- The browser remains presentation-only and cannot directly approve, apply, or
  write ontology data.
