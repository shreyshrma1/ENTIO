# Phase 7 Slice 13 Security Hardening

## ExecPlan Slice

Slice 13: Prompt-Injection, Limits, Redaction, And Failure Hardening.

## Goal

Adversarially verify and harden the Phase 7 provider, capability, conversation, private-draft, event, audit, and browser boundaries without adding product capability or weakening deterministic human review.

## Files Modified

- `web-server/src/main/kotlin/com/entio/web/ai/AiConversationService.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiConversationServiceTest.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/OpenAiResponsesClientTest.kt`
- `web-app/src/workbench/AiAssistantPanel.test.tsx`
- `docs/decisions/phase-7-slice-13-security-hardening.md`

## Implementation Result

- Rejected a second conversation turn while a provider or capability run is active, before the second message can be stored or any private draft can be mutated.
- Continued to permit only explicit plan, clarification, and cancellation decisions for paused runs.
- Withheld provider text that falsely claims approval, application, rejection, rollback, permission changes, or disclosure of credentials.
- Redacted recognizable authorization headers, OpenAI-style secret tokens, and API-key assignments from safe failure text before it reaches conversation messages or private run events.
- Preserved the existing fixed capability registry, strict tool schemas, per-call scope validation, private ownership checks, idempotency, bounded execution, cancellation, and human-review boundary.
- Kept provider prompt sections structurally separate: trusted policy, user content, strict tool definitions, and untrusted tool results remain distinct request items.
- Added no file, shell, arbitrary network, secret, raw RDF, approval, application, rejection, rollback, permission, or project-configuration capability.

## Tests Added Or Updated

- Concurrent message rejection before message append, provider re-entry, or private draft mutation.
- Provider authority-claim blocking and authorization-secret redaction across answers, events, and audit serialization.
- Prompt-injection fixtures for user text, ontology labels, FIBO metadata, help text, proposal comments, and tool results.
- Exhaustive assertion that injected content cannot add forbidden tools to the provider request.
- Browser rendering of provider failure as `FAILED`, with no human-review submission or apply control.
- Existing coverage continues to verify unknown, duplicate, replayed, unauthorized, malformed, stale-registry, cross-user, cross-project, source-scope, request, tool, edit, context, elapsed-time, token, correction, cancellation, provider-outage, and idempotency behavior.

## Verification

- `./gradlew :web-server:test` - passed.
- `./gradlew test` - passed across all Gradle modules.
- Focused `AiConversationServiceTest` and `OpenAiResponsesClientTest` execution - passed.
- Focused `AiAssistantPanel.test.tsx` execution - passed with 7 tests.
- `npm ci && npm test && npm run build && npm run test:e2e` in `web-app` - passed with 28 unit/component tests, a production build, and one browser journey. `npm ci` reported two existing high-severity audit findings; this slice changed no dependency or lockfile.
- `git diff --check` - passed.

## Git Commit

A focused Slice 13 commit will be created on `feature/phase-7-slice-13-security-hardening` after all required verification passes.

## Assumptions And Limitations

- Provider credentials remain server-memory-only and are still absent from response DTOs, events, audit records, and request bodies.
- Untrusted ontology and external text remains available to the model only as bounded data; it is not heuristically rewritten because authorization is enforced by the registry and dispatcher rather than model compliance.
- Provider request timeouts and retry classification remain enforced by the existing approved OpenAI client configuration.
- In-memory stores and event retention retain their previously documented server-lifetime limitations.

## Notable Decisions

- Concurrent sends fail deterministically instead of attaching a second provider loop to the active run.
- Unsupported authority claims are treated as failed provider output rather than displayed with a successful or review-ready state.
- Redaction is a final user-visible safety boundary; provider adapters must continue returning generic structured failures and must never intentionally include raw credentials.
