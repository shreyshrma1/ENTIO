# Phase 10.5 Slice 3 Completion: Web Read Boundary

Status: Complete
Date: 2026-07-23

## Delivered

- Added project-owned applied and proposal reasoning-result selection. Clients cannot choose a retained job.
- Added automatic applied refresh on authorized reads and proposal refresh after staging lifecycle changes.
- Added a read-only staged-graph snapshot that uses the existing proposal planner without changing proposal review state.
- Added separate `Off`, `Updating`, `Current`, `Unavailable`, and `Failed` applied/proposal overlays.
- Added bounded additive inferred-read DTOs and opt-in flags to outline, hierarchy, entity, graph-initial, and graph-neighborhood reads.
- Bound graph continuations to asserted and inferred fingerprints so stale continuations cannot cross graph revisions.
- Preserved Phase 10’s user-owned materialization eligibility and explicit semantic-job contracts.

## Contract Notes

- `includeAppliedInferred` and `includeProposalInferred` default to `false`.
- Applied and proposal facts remain separately identified.
- The server projects, deduplicates, orders, and limits inferred facts; React receives placements rather than interpreting RDF.
- Automatic project refresh jobs do not emit user-facing collaboration job events.
- Incomplete, cancelled, failed, missing-proposal, and superseded results never expose facts as current.

## Verification

- `./gradlew :web-server:test`
- `./gradlew :web-server:build`
- `./gradlew :core-types:test :semantic-engine:test :web-server:test`
- `git diff --check`
