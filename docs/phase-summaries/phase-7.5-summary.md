# Phase 7.5 Implementation Summary

## Status

Phase 7.5 is complete. Entio replaces the Phase 7 pinned-model requirement with a current-user OpenAI discovery, compatibility, explicit selection, live verification, and immutable run-binding workflow while preserving the Kotlin semantic engine and human-review boundary.

## Delivered Behavior

- A documented AI subsystem ownership map with focused provider, OpenAI adapter, model-policy, credential, settings, conversation, draft, capability, audit, and web boundaries.
- A versioned server-owned compatibility policy that filters malformed IDs, moving aliases, unsupported providers, and non-conversational model categories.
- Optional qualitative metadata and deterministic ordering for known models; discovered unknown conversational candidates remain neutral until verified.
- OpenAI model-list discovery and harmless Responses verification through fixed server-controlled endpoints and request policy.
- Per-user in-memory credential generation, discovery freshness, approved candidates, explicit selection, verification, idempotency, and local abuse limits.
- Versioned redacted HTTP contracts for provider settings, refresh, select-and-test, retest, clear, replacement, removal, and legacy credential compatibility.
- A React settings experience that renders only server candidates, discloses possible verification charge, clears key input on success or failure, and never writes credentials to browser storage.
- Assistant readiness gated on `READY`, not credential presence.
- Immutable run bindings containing model ID, compatibility-policy version, prompt version, credential generation, and server request-policy version.
- Safe model access-loss recovery that stops the run, preserves conversation/private draft, marks only the matching generation unavailable, refreshes discovery, and never falls back automatically.
- Adversarial coverage for arbitrary IDs, malformed inventory, replay, cross-user access, raw-payload/key/header leakage, model-management tool exclusion, logout, replacement/removal, restart cleanup, limits, and provider outage.

## Primary Journeys Verified

The deterministic browser journey covers multiple eligible models, explicit select-and-test, charge disclosure, changing models, a selected model becoming unavailable, reselection, credential replacement yielding no compatible models, non-AI settings continuity, refresh, and restored readiness. Server integration tests cover the same authoritative contracts and prove that an active multi-request run keeps its captured model while a future run uses a newly verified model.

## Architecture And Safety Boundary

The server owns provider endpoints, model compatibility, credentials, model state, verification, request settings, run binding, and recovery. React owns presentation only. The AI capability registry cannot manage credentials, models, permissions, approval, application, rollback, files, shell, raw Turtle, raw SPARQL, or arbitrary networks.

The semantic engine, deterministic validation/reasoning/SHACL/diff pipeline, private draft workflow, review submission, reviewer authority, atomic application, reload, and rollback behavior are unchanged.

## State And Limits

- Credentials, provider settings, selections, runs, conversations, drafts, audits, idempotency records, and local limits remain process-memory development state.
- OpenAI is the only production provider boundary; multi-provider selection is not implemented.
- Verification may incur a small provider charge. Exact pricing, billing, budgets, quota administration, and expert provider tuning are not implemented.
- Provider model-list membership is not proof of compatibility; explicit live verification is required.
- No automated test uses a real API key or external OpenAI request.

## Verification

Phase completion ran:

```bash
./gradlew test
./gradlew build
./gradlew check
(cd web-app && npm test && npm run build && npm run test:e2e)
(cd vscode-extension && npm test)
git diff --check
git status --short
```

All automated verification passed. The optional manual real-provider smoke test was not run because it requires a user-supplied key and may incur cost.

## Deferred Work

- Durable production credential/settings/session/audit persistence and production secret management.
- Production identity, tenancy, billing, budgets, quota dashboards, deployment, and observability.
- Multi-provider discovery and selection.
- Automatic routing or fallback, exact price claims, remote model taxonomy, benchmarking, and expert request tuning.
- Live model text delta streaming while a message request is in flight.
