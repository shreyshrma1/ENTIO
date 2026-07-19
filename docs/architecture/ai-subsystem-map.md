# Entio AI Subsystem Map

## Status

This map records the Phase 7 implementation boundary immediately before Phase 7.5 model discovery and selection. It describes ownership; it does not add or authorize product behavior.

The server AI implementation currently uses one Kotlin package, `com.entio.web.ai`. The responsibilities inside that package are distinguishable by file and stable service boundaries. Phase 7.5 Slice 1 therefore keeps the files in place instead of creating a behavior-neutral package-move diff. Later slices may introduce focused `provider`, `provider.openai`, `credentials`, or `models` packages when new code gives those packages concrete ownership.

## Server Ownership

| Concern | Current entry points | State owner | Boundary |
| --- | --- | --- | --- |
| Credential secret and status | `AiProviderContracts.kt` (`AiCredentialService`, `AiCredentialStore`) | `InMemoryAiCredentialStore` plus service-owned test status | Secrets are server-only and exposed only through callback-scoped credential access. |
| Provider-neutral credential test | `AiProviderClient` | Provider implementation | Returns an Entio-owned pass/fail result; it must not expose the credential. |
| OpenAI Responses transport | `OpenAiResponsesClient.kt` | `OpenAiResponsesClient` configuration and request-local transport state | Owns the fixed OpenAI host, authorization header, provider DTO mapping, retries, and redacted failures. |
| Deterministic provider doubles | `DevelopmentAiProviderClient`, `DevelopmentAiAssistantProvider`, `DevelopmentAiToolLoopProvider` | Stateless deterministic implementations | Default automated-test boundary; no external provider call is required. |
| Assistant compatibility boundary | `AiAssistantContracts.kt` | `AiAssistantService` and bounded context builder | Preserves the earlier assistant contract while delegating provider work through `AiAssistantProvider`. |
| Capability contracts and policy | `AiCapabilityContracts.kt`, `AiCapabilityRegistry.kt` | `AiCapabilityRegistry` | Kotlin defines every callable capability, schema, role, source scope, limits, audit classification, and forbidden names. |
| Capability execution | `AiCapabilityDispatcher.kt`, `AiLocalReadCapabilities.kt`, `AiSemanticReadCapabilities.kt` | Capability services and dispatcher | Adapts approved calls to existing Entio services. It does not move semantic policy into the AI layer. |
| Typed draft edits | `AiTypedEditCapabilities.kt` | `AiPrivateDraftWorkspace` and `AiDraftStore` | Uses existing typed edit preparation; never writes RDF or shared staging directly. |
| Conversation and run orchestration | `AiConversationService.kt` | `AiConversationStore`, `AiRunStore`, run event state | Owns intent, bounded tool loops, clarification/plan states, cancellation, and run lifecycle. |
| Conversation, run, draft, and audit records | `AiSessionContracts.kt`, `AiSessionStores.kt` | Per-user in-memory stores | Enforces user/project ownership and session-scoped persistence. |
| Draft analysis and correction | `AiDraftAnalysis.kt` | `AiDraftAnalysisStore` and draft workspace | Reuses deterministic validation, preview, reasoning, SHACL, and impact services without changing their authority. |
| Human-review handoff | `AiReviewSubmissionService.kt` | Submission service and in-memory attribution audit | Submits a verified AI draft into the existing review workflow; it cannot approve, reject, or apply. |
| Web mapping and idempotency | `AiWebBoundary.kt` | Boundary-local idempotency store | Maps authenticated project AI requests to Entio web DTOs and redacted errors. |
| Route composition | `Application.kt` | Ktor application wiring | Authenticates the current development user and exposes credential, assistant, conversation, run, draft, help, and SSE routes. |

## Browser Ownership

| Concern | Entry points | Responsibility |
| --- | --- | --- |
| Credential settings | `web-app/src/workbench/AiCredentialSettings.tsx` | Sends a credential to Entio once, renders server status, and provides test/removal actions. It does not call OpenAI. |
| Conversation shell | `web-app/src/workbench/AiAssistantPanel.tsx` | Renders conversation, plan, clarification, run, and draft states from versioned Entio contracts. |
| Run events | `web-app/src/workbench/ai/AiRunTimeline.tsx` | Displays server-owned SSE lifecycle events and resynchronization state. |
| Draft review | `web-app/src/workbench/ai/AiDraftReview.tsx` | Displays typed draft items and deterministic analysis; final authority remains in the ordinary proposal workflow. |
| Transport contracts | `web-app/src/web/contracts.ts`, `projectApi.ts`, and `queries.ts` | Owns typed HTTP/query adaptation only. It must not reconstruct server policy. |

## Main Flows

### Credential flow before Phase 7.5

```text
React credential settings
  -> Ktor /api/v1/ai credential routes
  -> AiCredentialService
  -> AiCredentialStore callback
  -> AiProviderClient
```

Only the store callback and provider adapter receive the API key. Status DTOs contain no secret. Phase 7.5 model settings must remain separate from this secret store and must be keyed by the current user.

### Conversation and tool flow

```text
React assistant
  -> Application routes
  -> AiWebBoundary
  -> AiConversationService
  -> AiCapabilityRegistry / AiCapabilityDispatcher
  -> existing Entio services
  -> private AiDraftStore
  -> deterministic analysis
  -> AiReviewSubmissionService
  -> existing human review workflow
```

Provider requests are initiated only by the conversation service through the provider interface. Capability results remain structured Entio data. The provider never receives direct semantic-module, filesystem, shell, approval, or application access.

### State ownership

- Credentials: per-user server memory in `AiCredentialStore`; secret values never enter conversation, draft, audit, event, or web DTO state.
- Conversations, runs, drafts, and AI audits: per-user and per-project server memory with ownership checks in the session stores.
- Shared staging and proposals: existing server-owned workbench services outside the AI subsystem; AI reaches them only through the bounded submission service.
- Semantic project state: existing Kotlin engine and service modules; the AI subsystem is an adapter and consumer.
- Browser cache: redacted status and versioned response data only; it is never authoritative for permissions, capability schemas, validity, or model compatibility.

## Phase 7.5 Provider, Credential, And Model Boundaries

Phase 7.5 must preserve these separations:

- `provider`: provider-neutral discovery, verification, and run execution contracts.
- `provider.openai`: fixed-host HTTP requests and immediate mapping of OpenAI payloads and failures.
- `credentials`: callback-scoped secret access and credential-generation lifecycle.
- `models`: server-owned compatibility policy, discovered candidate projection, per-user selection, verification, and freshness state without secrets.
- `web`: redacted versioned DTO mapping and current-user routes.

Model management is application infrastructure. It must not become an AI capability, project setting, ontology edit, collaboration preference, or browser-owned policy.

## Test Doubles And Verification Seams

- `DevelopmentAiProviderClient` provides deterministic credential-test behavior.
- `DevelopmentAiAssistantProvider` provides the Phase 6 compatibility response boundary.
- `DevelopmentAiToolLoopProvider` provides deterministic Phase 7 tool-loop behavior.
- `OpenAiResponsesClientTest` uses Ktor `MockEngine` rather than a live credential or network request.
- Application and service tests construct in-memory credential, conversation, run, draft, analysis, audit, and review stores.
- React tests mock the Entio transport boundary; browser tests never call OpenAI.

New Phase 7.5 discovery and verification behavior must extend these deterministic seams and remain runnable without a real API key.

## Forbidden Dependency Directions

- `core-types`, `semantic-engine`, `validation-engine`, `graph-diff`, `cli`, and `shared` must not depend on `web-server`, AI provider code, or browser code.
- Semantic modules must not receive credentials, provider DTOs, model settings, HTTP clients, or web contracts.
- React must not call OpenAI, retain credentials, evaluate compatibility policy, manufacture candidate IDs, or decide AI readiness independently.
- Provider/OpenAI adapters must not call semantic services, draft stores, proposal services, collaboration state, or model-selection web routes.
- Credential storage must not depend on conversation, draft, project, ontology, or collaboration state.
- Model discovery and selection must not be exposed as AI-callable capabilities.
- AI capability execution must not approve, reject, apply, roll back, write ontology sources, or edit project/provider configuration.
- Web DTOs, events, audits, logs, snapshots, and browser storage must not contain credentials, authorization headers, raw provider payloads, or unrestricted provider inventory.

## Slice 1 Cleanup Decision

The flat server package contains broad files, but its current entry points, state stores, and dependency directions are explicit and covered by focused tests. Moving existing public Kotlin types before their Phase 7.5 replacements exist would produce widespread import churn without changing ownership. Slice 1 therefore makes no source moves. Concrete new provider/model responsibilities may be placed in focused subpackages in later approved slices while compatibility seams protect existing behavior.
