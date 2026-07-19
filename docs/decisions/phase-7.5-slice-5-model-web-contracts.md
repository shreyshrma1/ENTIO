# Phase 7.5 Slice 5 Completion Record

## ExecPlan Slice Implemented

Slice 5: Versioned Web Contracts And Compatibility Migration.

## Goal

Expose current-user provider settings, discovery, candidate models, selection, verification, and clearing through redacted versioned Entio routes while preserving legacy credential clients.

## Files Modified

- Added versioned provider/model DTOs in `AiModelWebContracts.kt`.
- Added `AiModelWebBoundary` for safe settings-to-web projection and available actions.
- Wired model services and all approved `/api/v1/ai` routes in `Application.kt`.
- Added the model provider dependency to `WebApplicationDependencies`.
- Updated AI credential help and stable error help entries.
- Added focused Ktor web-contract tests and this completion record.

## Tests Added Or Updated

`AiModelWebContractTest` covers the full credential/discovery/select/retest/clear/remove contract, idempotent replay, Alice/Bob isolation, arbitrary-ID rejection, no-compatible-model state, provider failure with healthy non-AI routes, redaction, and legacy credential compatibility.

Existing `AiCredentialTest` and `ApplicationTest` remain green.

## Verification

- `./gradlew :web-server:test --tests '*AiCredentialTest' --tests '*AiModelWebContractTest' --tests '*ApplicationTest'` — passed.
- `./gradlew :web-server:test` — passed.
- `git diff --check` — passed with no whitespace errors.

## Git Commit

A focused Slice 5 commit was created on `feature/phase-7.5-slice-5-model-web-contracts` and includes the contracts, web boundary, routes, help, tests, and this record.

## Assumptions, Limitations, And Follow-Up

- New OpenAI credential submissions use unified provider settings and immediate discovery. Non-OpenAI development-provider submissions retain the legacy Phase 6/7 response shape for compatibility tests.
- Legacy credential-status and credential-test routes remain available and are not removed.
- React migration to the unified response belongs to Slice 6.
- Provider failures are represented as redacted settings states; no-compatible-models is a successful valid-credential response, not an authentication error.

## Notable Implementation Decisions

- Verification-triggering selection and retest routes require `Idempotency-Key`.
- Candidate DTOs expose only Entio-owned descriptors, qualitative metadata, compatibility/verification status, and policy version.
- Selection remains a user resource under `/api/v1/ai`; no project identifier is accepted.
- Model-management failures map to stable public `AI_*` codes and bounded HTTP statuses without provider bodies.
