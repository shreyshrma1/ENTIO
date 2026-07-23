# Phase 10 Slice 5: Reasoning Fact Selection And Stage Action

## Status

Complete.

## Decision

The Reasoning workspace now presents asserted facts separately from bounded inferred materialization candidates. The browser displays server-provided fact types, stageability, source candidates, import dependence, and explanations without deriving triples, edit types, provenance, or semantic eligibility.

Users may select one or more enabled candidates, select all currently loaded candidates up to the existing 100-fact result bound, clear the selection, and choose a server-provided target source for ambiguous candidates. Submission contains only fact identifiers, explicit source choices, and an idempotency key. The server remains authoritative and rechecks the applied graph and reasoning result before atomically staging anything.

Selection is cleared when the project, reasoning result, or job state changes and after successful staging. Correctable server failures preserve selection. Success refreshes shared staging state and opens Changes for normal review; the Reasoning UI cannot approve, apply, or write ontology sources.

## Verification

From `web-app`:

- `npm ci` — passed
- `npm audit --omit=dev` — passed with zero vulnerabilities
- `npm test` — passed, 21 files and 73 tests
- `npm run build` — passed

From the repository root:

- `git diff --check` — passed
