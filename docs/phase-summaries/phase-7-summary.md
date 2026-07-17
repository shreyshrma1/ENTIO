# Phase 7 Implementation Summary

## Status

Phase 7 implements Entio's tool-driven native AI ontology copilot on top of the completed semantic engine, controlled editing workflow, reasoning and SHACL services, FIBO catalog, Ktor web boundary, and React workbench.

The Kotlin server remains authoritative. The model can inspect bounded structured context and prepare private typed drafts, but it cannot approve, reject, apply, roll back, edit project configuration, change permissions, access arbitrary files or networks, retrieve credentials, or bypass deterministic analysis and human review.

## What Phase 7 Implemented

Phase 7 adds:

- Provider-neutral AI session, conversation, message, run, scope, draft, revision, audit, warning, limit, and next-action contracts.
- A scope-filtered capability registry with strict schemas and server-owned authorization.
- An approved OpenAI Responses API adapter using the allowlisted `gpt-5.2` model, the fixed OpenAI endpoint, disabled provider storage, sequential custom tools, bounded retries, request timeouts, cancellation, structured failure mapping, and redacted errors.
- Per-user provider credentials stored only in server memory and never returned to the browser.
- Bounded local project, entity, hierarchy, usage, screen, action, workflow, help, reasoning, SHACL, proposal, activity, and FIBO read capabilities.
- Private typed ontology and SHACL draft capabilities that reuse Entio's existing staging contracts rather than producing RDF directly.
- Private draft item update, removal, reorder, undo, clear, revision history, attribution, and fingerprint behavior.
- Deterministic private-draft analysis through existing preview, validation, semantic diff, reasoning, SHACL, and impact services.
- Bounded self-correction of private drafts when deterministic findings identify a supported correction.
- Explicit submission of a current, analyzed private draft into the ordinary shared human-review workflow.
- Project- and user-scoped versioned HTTP contracts for conversations, messages, runs, cancellation, drafts, analysis, review submission, and contextual help.
- Private SSE run events with ordered event IDs, bounded retention, reconnect cursors, and authoritative-state resynchronization.
- A React conversation UI with project/entity context, message history, plan confirmation, clarification, cancellation, safe activity, run limits, draft review, deterministic findings, semantic diff, provenance, and proposal handoff.
- Adversarial hardening for prompt injection, unknown and replayed capabilities, cross-scope access, concurrent turns, false provider authority claims, secret redaction, limits, cancellation, provider failures, and browser failure states.

## Repository Structure

Phase 7 primarily extends these existing areas:

- `web-server/src/main/kotlin/com/entio/web/ai/`: AI contracts, provider adapter, capability registry and dispatch, read adapters, typed draft tools, analysis, self-correction, conversation loop, submission, and web mapping.
- `web-server/src/main/kotlin/com/entio/web/contract/`: versioned AI request and response DTOs alongside the existing web contracts.
- `web-server/src/main/kotlin/com/entio/web/Application.kt`: authenticated, project-scoped HTTP and private SSE routes.
- `web-app/src/web/`: TypeScript transport contracts, API clients, SSE parsing, query keys, and mutations.
- `web-app/src/workbench/AiAssistantPanel.tsx`: the user-facing conversational copilot.
- `web-app/src/workbench/ai/`: safe run activity and private-draft review components.
- `docs/decisions/phase-7-slice-*.md`: implementation evidence and slice-specific decisions.

The existing `core-types`, `semantic-engine`, `validation-engine`, `graph-diff`, `cli`, `shared`, and VS Code extension boundaries remain intact. Phase 7 does not move semantic logic into React or create a second RDF framework.

## Main Contracts And Services

The central session contracts are:

- `AiConversation` and `AiConversationMessage`: private project conversation history and current draft reference.
- `AiRun`, `AiRunStatus`, `AiRunPolicy`, and `AiLimit`: one bounded provider/tool execution and its enforced limits.
- `AiCapabilityScope`: immutable user, project, conversation, source, baseline, role, permission, and feature scope.
- `AiCapabilityDefinition`, `AiCapabilityRegistrySnapshot`, and strict schema types: the exact tools available to one run.
- `AiDraft`, `AiDraftItem`, `AiDraftRevision`, and typed draft operations: private, ordered, attributable proposed edits.
- `AiDraftAnalysis`: deterministic validation, preview, diff, reasoning, SHACL, impact, findings, and references for a specific draft revision.
- `AiAuditRecord`: model, prompt, allowed-capability, call, revision, result-reference, usage, status, and timing metadata without credentials or raw provider payloads.

The principal services are:

- `OpenAiResponsesClient`: the approved external provider boundary.
- `AiCapabilityRegistry` and `DefaultAiCapabilityDispatcher`: capability selection, decoding, authorization, and routing.
- Local and semantic read capability services: bounded adapters over existing Entio services.
- `AiPrivateDraftWorkspace` and typed edit adapters: private typed draft preparation and revision.
- `AiDraftAnalysisService` and correction services: deterministic analysis and bounded correction.
- `AiConversationService`: intent classification, plan and clarification pauses, sequential provider/tool loop, limits, cancellation, events, audits, and safe terminal results.
- `AiReviewSubmissionService`: explicit import of an analyzed private draft into shared staging and proposal review.
- `AiWebBoundary`: user/project scoping, idempotency, transport mapping, private SSE windows, and structured web failures.

## Capability Boundary

Read capabilities cover project summaries, local entity details and comparisons, local search, hierarchy neighborhoods, usage, current screen context, available actions, workflow state, Entio help, error help, semantic jobs, proposals, recent activity, FIBO search, and FIBO details.

Mutation-capable tools operate only on the current private draft. They add or update approved typed ontology and SHACL edits, remove or reorder draft items, undo or clear draft revisions, and request deterministic draft analysis. Each invocation is decoded against a strict registry snapshot and revalidated against the current user, project, source, feature, permission, and baseline scope.

There is no model capability for approval, rejection, application, rollback, raw Turtle, raw SPARQL, shell access, filesystem access, arbitrary network calls, secrets, project configuration, or permission changes.

## End-To-End Workflow

1. A user adds the approved OpenAI credential in Settings. The secret remains in server memory and the browser receives status only.
2. The user creates or opens a project-scoped private conversation.
3. Entio classifies the request deterministically. Broad requests pause for plan confirmation; materially ambiguous edits pause for clarification; explicitly forbidden requests fail before provider execution.
4. The server constructs a bounded provider request with trusted policy, user conversation, the current authorized tool definitions, and untrusted tool results kept as separate items.
5. The provider may call only tools in that run's registry snapshot. The server decodes, authorizes, executes, records, and returns each result sequentially.
6. Read tools return bounded structured evidence. Edit tools modify only the current private draft through approved typed Entio contracts.
7. The user reviews draft items and revisions and runs deterministic analysis. Validation, preview, semantic diff, reasoning, SHACL, and impact results remain server-owned.
8. A current analysis may support bounded draft correction; it cannot override a blocking deterministic result.
9. The user explicitly submits a ready draft for human review. Submission imports the typed items into normal shared staging and returns the ordinary proposal review route.
10. A separate authorized reviewer approves and applies through the existing proposal controls. Atomic persistence, reload verification, and rollback remain the pre-existing Entio workflow and are never delegated to AI.

## Web And Streaming Behavior

The React assistant uses versioned HTTP resources for authoritative conversation, run, draft, analysis, and submission state. Private SSE events provide safe status and capability activity with monotonically increasing IDs. Clients can reconnect with `Last-Event-ID`; if retained history is insufficient, the server instructs the client to refetch authoritative state.

The UI presents labels first and keeps IRIs and fingerprints behind progressive disclosure. It renders missing credentials, loading, planning, clarification, running, cancellation, limits, disconnected streams, stale drafts, conflicts, invalid drafts, analysis failures, provider failures, and review readiness without presenting failure as success. It exposes submission for human review, not approval or application.

## Security And Safety Behavior

- Credentials and authorization headers are excluded from provider request bodies, DTOs, SSE events, audits, and user-visible errors.
- Ontology text, FIBO metadata, help content, proposal text, user messages, tool results, and provider output are treated as untrusted data.
- Prompt-injection content cannot add tools or widen a frozen capability scope.
- Unknown, malformed, duplicate, replayed, stale-registry, unauthorized, and out-of-source calls fail without duplicate mutation.
- A second conversation turn cannot attach to an active provider/tool run.
- Provider text that claims approval, application, rejection, rollback, permission changes, or credential disclosure is withheld and the run fails safely.
- Request, capability-call, draft-edit, correction, context, elapsed-time, input-token, output-token, and active-run limits stop work while preserving the private draft.
- Cross-user and cross-project state returns non-disclosing scope failures.
- AI attribution survives review submission, while reviewer authority remains separate.

## Developer Commands

Run the complete Kotlin verification:

```bash
./gradlew test
./gradlew build
./gradlew check
```

Run the web server tests:

```bash
./gradlew :web-server:test
```

Run the React verification:

```bash
cd web-app
npm ci
npm test
npm run build
npm run test:e2e
```

Run the VS Code regression:

```bash
cd vscode-extension
npm ci
npm test
```

Run the application locally in separate terminals:

```bash
./gradlew :web-server:run
```

```bash
cd web-app
npm ci
npm run dev
```

## Examples And Test Fixtures

- `examples/simple-ontology/` remains the committed local ontology and SHACL example.
- AI server tests copy or construct projects under temporary directories before staging or applying changes.
- Provider tests use deterministic fake providers or Ktor `MockEngine`; automated tests never require a live OpenAI key or external network.
- Prompt-injection fixtures cover user text, ontology labels, FIBO metadata, help text, proposal comments, and tool results.
- The browser regression intercepts versioned HTTP and SSE routes to exercise deterministic conversation, planning, private draft, analysis, submission, and proposal handoff behavior.
- Existing semantic-engine and CLI regressions continue to prove atomic application and restoration after forced post-save verification failure.

## Explicit Non-Goals

Phase 7 does not add:

- Autonomous approval, application, rollback, publishing, or project-configuration changes.
- Raw RDF/Turtle generation, arbitrary SPARQL, shell, filesystem, environment, secret, or unrestricted network tools.
- Durable conversations, drafts, audits, credentials, idempotency records, SSE history, or provider response storage.
- Production authentication, tenancy, secret management, billing, deployment, observability, or administration.
- A replacement semantic engine or semantic decisions in TypeScript.
- Full Protégé parity, arbitrary OWL expression editing, arbitrary SHACL graph mutation, document ingestion, entity resolution, embeddings, or an unbounded agent framework.
- Shared AI credentials or AI activity broadcast over the collaboration WebSocket.
- Git operations inside Entio.

## Known Limitations And Follow-Up Work

- AI conversations, runs, drafts, audits, credentials, idempotency entries, and retained SSE events are process-memory state and disappear on server restart.
- The production adapter supports the single allowlisted OpenAI model and endpoint. Provider choice, model policy, quota management, and production credential storage need later approved infrastructure.
- Message submission currently waits for the server-side turn result. SSE exposes safe retained run activity and reconnection, but the implementation does not stream model text deltas into the UI while the message request is in flight.
- The browser can review draft items and revision history, run analysis, and submit. Direct graphical item editing, reordering, and undo are available through typed conversation capabilities rather than dedicated draft-row controls.
- Self-correction is bounded and supports only corrections representable by approved typed operations.
- External and ontology context is intentionally bounded; the assistant can report that information is unavailable rather than loading arbitrary sources.
- The web Playwright test uses deterministic route interception rather than a live OpenAI provider or a deployed multi-process environment.
- The frontend install currently reports two high-severity `npm audit` findings from the existing dependency tree. Phase 7 did not automatically change dependency versions outside its approved slices.

## Plan And Implementation Differences

- The implemented private SSE boundary streams safe lifecycle and capability events, but not live provider text deltas. Authoritative conversation state is returned by HTTP and recovered after reconnect.
- The React draft review is intentionally review-oriented. Draft mutations beyond analysis and submission are model-invoked typed capabilities, not a complete direct manipulation UI.
- Persistence, production identity, deployment hardening, durable audit storage, and production quota/cost controls remain explicitly deferred, matching the development-boundary constraint.
- No committed example or FIBO asset was changed by Phase 7 implementation.
