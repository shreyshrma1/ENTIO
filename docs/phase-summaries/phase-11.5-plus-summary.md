# Phase 11.5+ Summary

## Status

Phase 11.5+ was implemented and verified on 2026-07-30.

Phase 11 remains the document upload, extraction, evidence, authorization,
proposal, apply, reload, rollback, and applied-provenance foundation. Phase
11.5 remains the discovery, connected-modeling, reconciliation, alignment, and
critic foundation. Phase 11.5+ changes only the final handoff: the model
produces a connected semantic plan, and Kotlin verifies and compiles supported
meaning into the existing typed-edit workflow.

## Delivered Behavior

- Neutral semantic-plan, semantic-reference, grouping, coverage, confidence,
  compilation, and preview contracts.
- A deterministic completeness ledger that keeps semantic coverage separate
  from compilation success.
- Kotlin compilation for the approved class, property, assertion, annotation,
  hierarchy, value, and bounded SHACL patterns.
- Stable task-local references, deterministic IRIs, dependency ordering,
  duplicate and collision checks, expanded-edit limits, safe splitting, and
  blocked or review-only fallback.
- A strict no-tools provider contract that contains semantic meaning but no
  final IRIs, raw RDF, low-level Entio operations, source writes, or apply
  instructions.
- Server-authoritative semantic planning, correction, freshness checks,
  compilation, exact previews, grouped review, and typed-draft conversion.
- Review presentation for exact changes, generated IRIs, evidence, confidence,
  coverage, compilation, blockers, critique, and retained review-only meaning.
- Applied provenance that links compiled executable changes with related
  retained review-only findings without writing provenance into ontology
  source files.
- A permanent offline regression harness and opt-in ten-run controlled-provider
  release gate.

The existing private-draft, proposal, validation, human approval, atomic apply,
reload, and rollback path remains the only ontology write path.

## Slice Commits

| Slice | Commit | Result |
| --- | --- | --- |
| 0 | `1026444` | Contract audit, supported-pattern inventory, benchmark thresholds, and stop conditions |
| 1 | `35ff1aa` | Neutral semantic-plan and compilation contracts |
| 2 | `4c3fed3` | Completeness, compilation, confidence, and benchmark metrics |
| 3 | `bc414a5` | Deterministic semantic-plan compiler |
| 4 | `7bebda3` | Dependency ordering, splitting, preview, and stale-state safety |
| 5 | `22eb802` | Strict semantic provider contract and orchestration |
| 6 | `86b4475` | Grouped review, typed-draft handoff, and provenance |
| 7 | `9edf09b` | Permanent benchmark, security diagnostics, and full regression |

Each slice was developed on its own branch, verified, pushed, and merged
locally into `main` with a non-fast-forward merge. `main` was not pushed.

## Main Affected Areas

- `core-types/src/main/kotlin/com/entio/core/`
- `semantic-engine/src/main/kotlin/com/entio/semantic/`
- `web-server/src/main/kotlin/com/entio/web/ingestion/`
- `web-server/src/main/kotlin/com/entio/web/contract/`
- `web-app/src/workbench/document-ingestion/`
- corresponding Kotlin, React, integration, fixture, and end-to-end tests
- Phase 11.5+ architecture, specification, ExecPlan, decisions, and summary

No module, dependency, persistence layer, CLI ingestion surface, or VS Code
ingestion surface was added.

## Decision Records

- `docs/decisions/phase-11.5-plus-slice-0-contract-audit.md`
- `docs/decisions/phase-11.5-plus-slice-1-semantic-contracts.md`
- `docs/decisions/phase-11.5-plus-slice-2-completeness-metrics.md`
- `docs/decisions/phase-11.5-plus-slice-3-semantic-compiler.md`
- `docs/decisions/phase-11.5-plus-slice-4-safe-ordering-and-preview.md`
- `docs/decisions/phase-11.5-plus-slice-5-semantic-provider-and-orchestration.md`
- `docs/decisions/phase-11.5-plus-slice-6-grouped-review-and-provenance.md`
- `docs/decisions/phase-11.5-plus-slice-7-benchmark-regression.md`

## Controlled Benchmark

The ten-run provider gate used `gpt-4o-mini`, contract
`phase-11-5-plus-semantic-plan-response-v1`, and frozen input SHA-256
`6c41e84b1d1839dc2b4539753265ca41b1a83ab65fcaa2fdbc7c5eb1a9538b59`.

- All 13 required concepts: 10 of 10 runs.
- All 6 required relationships: 10 of 10 runs.
- All 4 required complex review-only meanings: 10 of 10 runs.
- Supported compilation: 100%.
- Exact provenance, complete ledger, and illustrative gates: 10 of 10 runs.
- Prohibited executable recommendations: 0 runs.
- Automatic ontology writes: 0 runs.
- Provider correction calls: 0.

Token usage and cost were unavailable from the current provider adapter. The
exact command, run durations, credential handling, and full result are recorded
in the Slice 7 decision.

## Verification

The following commands passed on the accumulated implementation:

```bash
./gradlew test
./gradlew check
./gradlew build

cd web-app
npm ci
npm audit --omit=dev
npm test
npm run build
npm run test:e2e

cd ../vscode-extension
npm ci
npm test

cd ..
git diff --check
git status --short
```

The web suite passed 101 tests and four Playwright workflows. The VS Code
extension passed 37 tests. The controlled provider benchmark passed separately
because normal tests remain offline.

## Limits And Non-Goals

- Model output can still vary; deterministic compilation removes operation
  formatting variance but cannot guarantee correct modeling judgment.
- Unsupported complex, temporal, aggregation, and separation-of-duty meaning
  stays review-only.
- Compilation is limited to the approved typed-edit and bounded SHACL pattern
  inventory.
- Documents, OCR artifacts, incomplete tasks, semantic plans, and review
  workspaces remain temporary.
- There is no automatic approval or apply, raw RDF fallback, direct ontology
  write, second apply path, unrestricted agent loop, new persistence layer,
  external indexing, or document ingestion through CLI or VS Code.
- Provider token and cost reporting remains unavailable.
