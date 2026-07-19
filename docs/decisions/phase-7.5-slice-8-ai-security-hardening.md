# Phase 7.5 Slice 8 Completion Record

## ExecPlan Slice Implemented

Slice 8: Security, Privacy, Limits, And Regression Hardening.

## Goal

Prove credential/model isolation, safe provider mapping, abuse resistance, and preservation of the approved Phase 7 boundary.

## Files Modified

- Expanded provider/model web-contract isolation and logout tests.
- Added adversarial malformed-inventory and contract-field regression tests.
- Strengthened the capability registry test to forbid model-management tools.
- Added a browser-storage regression test for credential submission.
- Finalized the provider-model ADR and stable help for invalid credentials, timeout, outage, and verification charge disclosure.

## Tests Added Or Updated

- Cross-user status, discovery, and selection attempts cannot observe or mutate another user's configuration.
- Arbitrary model IDs, aliases, unsupported categories, URL-like IDs, control characters, and empty inventory values are excluded or rejected.
- Logout removes credential, discovery, and selected-model state together.
- Credential replacement remains unable to bypass per-user discovery limits; existing verification-limit and replay tests remain green.
- DTO/state/audit fields, provider responses, SSE, and frontend form handling remain free of keys, authorization headers, raw provider objects, and browser-storage writes.
- The AI capability registry exposes no credential, configuration, permission, or model-management function.
- Non-AI health/project behavior remains available with no compatible models or provider outage.

## Verification

- `./gradlew :web-server:test` — passed (137 tests).
- `(cd web-app && npm test && npm run build)` — passed (60 tests and production build).
- `git diff --check` — passed with no whitespace errors.

## Git Commit

A focused Slice 8 commit was created on `feature/phase-7.5-slice-8-ai-security-hardening` and includes security regressions, help/ADR updates, and this record.

## Assumptions, Limitations, And Follow-Up

- Local limits are intentionally in-memory development protections; provider quotas and rate-limit responses remain authoritative.
- No production identity, persistence, billing, log platform, or generic rate-limit framework was introduced.
- Deterministic end-to-end journey consolidation and the Phase 7.5 summary belong to Slice 9.

## Notable Implementation Decisions

- Missing current-user credentials produce the same stable error regardless of whether another user has configured AI.
- Browser tests spy on storage writes rather than relying only on implementation inspection.
- User help describes recovery states without exposing provider bodies or claiming exact verification pricing.
- The approved capability allowlist treats model management as server/settings behavior, never as an AI-callable tool.
