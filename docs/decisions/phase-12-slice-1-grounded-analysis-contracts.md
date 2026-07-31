# Phase 12 Slice 1 Grounded Analysis Contracts

Status: Complete

Date: 2026-07-31

## ExecPlan Slice Implemented

Slice 1: Add Provider-Neutral Phase 12 Contracts.

## Goal

Add immutable neutral contracts for deterministic candidate extraction,
ontology retrieval, grounded semantic decisions, complete coverage,
`NeedsInput`, distinct count units, Phase 12 stages, and frozen work identity
before adding behavior.

## Files Modified

- `core-types/src/main/kotlin/com/entio/core/DocumentGroundedAnalysisContracts.kt`
- `core-types/src/main/kotlin/com/entio/core/DocumentAnalysisPipelineContracts.kt`
- `core-types/src/test/kotlin/com/entio/core/DocumentGroundedAnalysisContractsTest.kt`
- `docs/decisions/phase-12-slice-1-grounded-analysis-contracts.md`

## Implementation

- Added stable evidence-span, candidate, nearby-hint, retrieval fingerprint,
  structural-context, match-reason, and server-issued selection contracts.
- Added the audited top-20 prompt choice and compact structural-context bounds.
- Added reuse, extension, propose-new, unresolved, administrative, and
  illustrative dispositions. Reuse and extension require opaque selection IDs;
  other dispositions reject them.
- Added connected semantic references and explicit prerequisite origins.
- Added a complete one-disposition-per-candidate ledger.
- Added distinct grounded recommendation statuses including `NeedsInput` and
  typed editable-field descriptions.
- Added separate evidence, candidate, grounded-item, recommendation, and
  expanded-edit counts without a task-wide product ceiling.
- Added provider-neutral Phase 12 stage and work-key input contracts.
- Added the exact audited `phase-12-...-v1` version constants while preserving
  all legacy Phase 11.5+ contracts.

The contracts use only `core-types` values. They contain no provider-specific
name, web-server type, RDF-library type, raw RDF, typed operation payload,
credential, filesystem path, or write instruction.

## Tests Added

`DocumentGroundedAnalysisContractsTest` covers:

- the complete candidate, scope, disposition, status, editable-field, and stage
  vocabulary;
- deterministic candidate evidence and ordering;
- stable retrieval ordering, IDs, reasons, bounds, and imported/FIBO read-only
  rules;
- required selection IDs for reuse and extension;
- rejection of model-owned IRI substitutions and unsorted or duplicate IDs;
- complete candidate coverage and valid item references;
- explicit `NeedsInput` fields and distinct nonnegative count units;
- frozen work-key fingerprint and version validation.

## Verification

- `./gradlew :core-types:test` — passed, 96 tests.
- `./gradlew :core-types:build` — passed.
- `git diff --check` — passed.

One focused test initially used a one-element reason list for an ordering
failure assertion. The fixture was corrected to use two sorted reasons, after
which the complete required verification passed. No production behavior was
changed to satisfy that test.

## Git

The focused commit is created from this completed record on branch
`feature/phase-12-grounded-contracts`, pushed, and then merged locally into the
accumulated `main` with a non-fast-forward merge.

## Assumptions And Limitations

- Slice 1 defines data shape and invariants only; extraction, retrieval,
  provider calls, orchestration, verification, and browser behavior belong to
  later slices.
- Public HTTP compatibility remains additive. The existing recommendation
  status enum is unchanged; the Phase 12 grounded status is separate until the
  Slice 7 public adapter exposes it.
- Full-state duplicate and no-op checks are intentionally not represented by
  the top-20 prompt result list; Slice 3 provides that independent behavior.
