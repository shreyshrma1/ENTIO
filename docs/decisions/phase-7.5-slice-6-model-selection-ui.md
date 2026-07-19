# Phase 7.5 Slice 6 Completion Record

## ExecPlan Slice Implemented

Slice 6: React Credential And Model-Selection Experience.

## Goal

Render the server-owned credential, discovery, selection, and verification state machine, and require a verified `READY` selection before the assistant can start or continue work.

## Files Modified

- Added typed provider-settings and approved-model descriptors to the React web contracts.
- Added transport and query helpers for discovery, selection, retest, clearing, replacement, and removal.
- Reworked `AiCredentialSettings` around server-returned candidates and accessible lifecycle states.
- Gated `AiAssistantPanel` conversation creation and message submission on verified model readiness.
- Added scoped model-card styles, component/transport tests, Playwright coverage, and updated intentional visual baselines.

## Tests Added Or Updated

- Provider settings tests cover key clearing after accepted and rejected submissions, explicit select-and-test, invalid credentials, no-compatible-models, stale discovery, unavailable selections, failed verification, rate limiting, timeout, and provider-down states.
- Assistant tests cover ready, missing-credential, and configured-but-unselected behavior.
- Transport tests assert the new routes, HTTP methods, and verification idempotency headers.
- The browser workbench journey now consumes unified provider settings and confirms the verified selected model.

## Verification

- `(cd web-app && npm test && npm run build && npm run test:e2e -- --grep 'credential|model|assistant')` — passed.
- `git diff --check` — passed with no whitespace errors.

## Git Commit

A focused Slice 6 commit was created on `feature/phase-7.5-slice-6-model-selection-ui` and includes the React contracts, model settings experience, assistant gating, tests, snapshots, and this record.

## Assumptions, Limitations, And Follow-Up

- React renders only candidate descriptors returned by Entio and contains no compatibility intersection or arbitrary model-ID field.
- The credential exists only in component state until submission and is cleared for both success and failure handling; it is never placed in query or browser storage.
- Verification discloses that its harmless provider request may incur a small charge.
- Runtime enforcement and unavailable-model recovery belong to Slice 7; the Slice 6 UI consumes the current server state without implementing fallback.

## Notable Implementation Decisions

- Credential presence is insufficient for readiness. Both new-conversation and send controls require `selectionStatus === "READY"`.
- Provider failures use stable redacted error codes to distinguish rate limit, timeout, and service-unavailable guidance.
- Selection and retest requests use unique idempotency keys generated at the action boundary.
- Existing UI primitives provide status announcements and disabled controls without introducing a frontend framework or client-owned policy.
