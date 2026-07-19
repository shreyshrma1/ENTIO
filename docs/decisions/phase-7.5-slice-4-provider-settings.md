# Phase 7.5 Slice 4 Completion Record

## ExecPlan Slice Implemented

Slice 4: Per-User Settings, Selection, And Verification.

## Goal

Own credential-adjacent discovery and verified model selection per user in server memory without placing secrets in settings records or project state.

## Files Modified

- Added `AiUserProviderSettings`, its in-memory store, credential-generation lifecycle, discovery service, provider-settings service, selection service, verification service, and local call limiter.
- Added focused discovery, selection, verification, and shared deterministic test support.
- Added this completion record.

## Tests Added Or Updated

- `AiModelDiscoveryServiceTest` covers per-user zero/one/multiple inventories, explicit-selection state, freshness, forced refresh, credential error classification, and discovery limits across credential replacement.
- `AiModelSelectionServiceTest` covers arbitrary/cross-user ID rejection, explicit selection, clear, stale refresh, unavailable candidates, credential replacement/removal, and restart cleanup.
- `AiModelVerificationServiceTest` covers harmless callback-scoped verification, idempotency, credential preservation versus invalidation, local limits, secret absence, and cross-user isolation.

## Verification

- `./gradlew :web-server:test --tests '*AiModelDiscoveryServiceTest' --tests '*AiModelSelectionServiceTest' --tests '*AiModelVerificationServiceTest'` — passed.
- `./gradlew :web-server:test` — passed.
- `git diff --check` — passed with no whitespace errors.

## Git Commit

A focused Slice 4 commit was created on `feature/phase-7.5-slice-4-provider-settings` and includes the services, stores, tests, and this record.

## Assumptions, Limitations, And Follow-Up

- All state is intentionally in memory and is cleared on credential removal, logout cleanup, explicit store clearing, or restart.
- The settings record contains status, candidate descriptors, timestamps, policy version, selection, and safe error category only; the API key remains solely in `AiCredentialStore`.
- Routes and React integration belong to Slices 5 and 6.
- Provider 429 categories remain distinct from Entio's local call-limit failure.

## Notable Implementation Decisions

- Discovery freshness is fixed at 15 minutes.
- Discovery is limited to five calls and verification to three calls per user per rolling 15 minutes, with one concurrent call of each kind per credential generation.
- The limiter retains a user-session history across credential generations so replacing a key cannot bypass local safeguards.
- Successful verification is the only operation that stores a selected model as `READY`; one candidate is never auto-selected.
