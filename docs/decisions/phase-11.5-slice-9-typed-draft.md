# Phase 11.5 Slice 9: Typed Draft

Status: Complete

Date: 2026-07-27

## Decision

Entio converts an accepted Phase 11.5 connected recommendation into the existing
typed staging operations. Kotlin resolves temporary references to the final,
collision-checked IRIs created during plan verification. A grouped recommendation
is staged as one atomic unit and may contain no more than twenty typed edits.

Before conversion, the server rechecks the analysis work key, applied graph,
verified evidence, writable source, selected model, prompt versions, individual
confirmations, and duplicate operation keys. Review-only findings never become
staged operations. When a mixed recommendation is eventually applied, its related
review-only findings remain attached to the durable document provenance.

## Workflow

1. The reviewer accepts an executable grouped recommendation.
2. The server resolves its dependency-ordered operations and temporary references.
3. The existing staging workflow validates every typed ontology, semantic, SHACL,
   or approved external-reuse operation before appending the atomic group.
4. The existing proposal, validation, reasoning, SHACL, approval, apply, reload,
   and rollback workflow remains authoritative.

Ontology source files are not changed during document analysis, review, or staging.

## Verification

The slice verifies connected temporary-reference translation, existing translation
regressions, review gates, staging limits, proposal integration, and applied
provenance through the semantic-engine and web-server test suites.
