# Phase 7 Slice 8 Draft Analysis And Self-Correction

## ExecPlan Slice

Slice 8: Draft Validation, Preview, Semantic Analysis, And Self-Correction.

## Goal

Connect exact private draft revisions to Entio's existing deterministic proposal, validation, diff, reasoning, SHACL, and impact services, then permit bounded corrections that remain private until human review.

## Files Modified

- `web-server/src/main/kotlin/com/entio/web/ai/AiCapabilityContracts.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiCapabilityRegistry.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiDraftAnalysis.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiLocalReadCapabilities.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiDraftAnalysisTest.kt`
- `docs/decisions/phase-7-slice-8-draft-analysis-self-correction.md`

## Implementation Result

- Added baseline- and draft-fingerprint-aware private draft analysis.
- Reused the existing web proposal planner, proposal validator, graph diff, OWL reasoning, SHACL validation, and proposal-impact services without adding semantic logic.
- Added structured validation, preview, diff, reasoning, SHACL, and impact references tied to one exact draft revision.
- Added exact-result caching and invalidation when either the draft or applied project changes.
- Added bounded validate, preview, reason, SHACL, and impact capability operations.
- Added a correction controller that can update or remove only private draft items, reruns deterministic analysis after each correction, preserves original findings and explanations, and stops at the configured correction-cycle limit.
- Kept source files, shared staging, proposal approval, and proposal application outside the analysis and correction boundary.

## Tests Added Or Updated

- Compared private draft output with the existing proposal planner for the same change set.
- Verified exact-revision cache reuse and invalidation after draft mutation.
- Verified applied-project mutation marks the private draft stale.
- Verified blocking findings prevent ready-for-review state regardless of narrative claims.
- Verified correction reruns analysis, preserves original finding references, and leaves shared staging empty.
- Verified a zero correction limit stops without mutating the private draft.
- Verified strict draft-analysis capability decoding and stage-specific references.

## Verification

- `./gradlew :web-server:test --tests com.entio.web.ai.AiDraftAnalysisTest` - passed.
- `./gradlew :web-server:test` - passed.
- `./gradlew test` - passed.
- `./gradlew build` - passed.
- `git diff --check` - passed.

## Git Commit

A focused Slice 8 commit was created on `feature/phase-7-slice-8-draft-analysis-self-correction`.

## Assumptions And Limitations

- Private draft analysis remains in-memory and does not represent durable project history.
- A project must contain at least one graph triple so the existing proposal baseline implementation can produce the canonical project fingerprint.
- Self-correction accepts only structured update or remove operations already supported by the private typed-edit workspace.

## Notable Decisions

- Deterministic services remain authoritative; provider prose cannot suppress, downgrade, or replace a validation, reasoning, SHACL, or impact finding.
- Cached analysis is reusable only for the same user, project, conversation, draft, revision, baseline fingerprint, and draft fingerprint.
- Corrections never stage, approve, apply, or write changes.
