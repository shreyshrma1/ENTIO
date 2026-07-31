# Phase 12 Slice 7: Connected Editable Review

Status: Complete on 2026-07-31.

## Decision

Phase 12 grounded analysis now installs its verified interpretation context with
the existing in-memory connected review workspace. Each connected
recommendation exposes its grounded dispositions, server-issued selected and
alternative selection IDs, canonical IRIs, deterministic match reasons,
structural context, prerequisite origins, reviewer-solvable fields, and
`Executable`, `NeedsInput`, `ReviewOnly`, or `Blocked` status.

The browser renders this information only as server-owned review context. It
does not accept arbitrary existing-entity IRIs, decide compatibility, generate
operations, or write ontology data. Existing editable typed-operation fields
continue to round-trip through Kotlin final-plan verification and return the
recommendation to pending review before it can be accepted.

## Review And Batching

Recommendations remain collapsed by default. Their summary retains only the
name and edit type, confidence, review status, and native expansion control.
Expanded cards show semantic intent, exact typed changes, evidence, generated
IRIs, retrieved alternatives, sources, structural domain/range/datatype/type
context, prerequisite provenance, confidence, and safe warnings.

The historical `maximumAcceptedEdits` response field was removed. The existing
document-draft path remains the sole staging path: it preserves connected
recommendation groups and packs accepted typed edits into dependency-safe
atomic batches of at most 20 without imposing a task-wide semantic ceiling.

## Counts And Safety

The review response reports distinct totals for evidence blocks, retained and
rejected NLP candidates, retained/unresolved/rejected grounded items,
recommendation outcomes, and expanded typed edits. Review context remains
temporary and project/user scoped. Stale work keys and graph fingerprints,
individual confirmation gates, deterministic verification, proposal approval,
atomic apply, reload, rollback, and applied provenance remain unchanged.

The orchestrator handoff change was explicitly authorized for this slice so
verified grounded metadata could reach its existing review owner without a
second review workflow.
