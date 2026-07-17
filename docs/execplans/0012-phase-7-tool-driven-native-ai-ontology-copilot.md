# ExecPlan: Phase 7 Tool-Driven Native AI Ontology Copilot

## Status

Approved and implementation-ready, subject only to the base-branch and clean-working-tree checks in the Planning Boundary Prerequisite. It does not activate Phase 7 implementation by itself.

## Source Spec

- [Phase 7 feature spec](../specs/0012-phase-7-tool-driven-native-ai-ontology-copilot.md)
- [Phase 7 scope](../architecture/phase-7-scope.md)

## Goal

Replace the Phase 6 deterministic assistant's production role with a real, server-side OpenAI Responses API integration that can use a strictly allowlisted set of Entio capabilities, conduct bounded follow-up conversations, prepare and analyze private multi-edit AI drafts, and submit a current draft into the existing human proposal-review workflow.

Kotlin remains authoritative for project scope, permissions, semantic lookup, typed edits, validation, preview, diff, reasoning, SHACL, FIBO, proposal creation, and workflow state. The model receives no direct access to files, Turtle, SPARQL, shell commands, unrestricted networking, secrets, project configuration, approval, rejection, application, or rollback.

## Planning Boundary Prerequisite

Phase 6 is complete, and Phase 7 is the active planning boundary. Before Slice 1 implementation:

1. Confirm the approved base contains all Phase 5 and Phase 6 implementation commits.
2. Confirm the working tree is clean and the base branch is current with its remote under `AGENTS.md`.

Stop rather than implementing Phase 7 from a base that does not satisfy these conditions.

## Current State

The repository currently provides:

- Kotlin/JVM semantic modules for RDF project loading, semantic descriptors, typed edits, deterministic validation, semantic diff, proposal application and rollback, OWL reasoning, SHACL, and deterministic FIBO search/reuse.
- A `web-server` Ktor module with registered-project boundaries, development identity, collaboration, shared staging, proposal orchestration, semantic jobs, FIBO adapters, structured errors, and in-memory session state.
- An in-memory per-user `AiCredentialStore`, a provider-neutral `AiProviderClient`, and credential status/save/test/remove routes.
- A limited `AiAssistantService`, bounded context builder, nine operation types, typed suggestion validator, deterministic development provider, and one request/response endpoint.
- A React `web-app` with a credential settings surface, a bounded assistant panel, staging actions, transport clients, queries, and tests.
- No real OpenAI provider call, Responses API tool loop, application-owned conversation service, immutable capability scope, strict capability registry, private AI draft, self-correction loop, AI-specific audit record, or AI draft submission boundary.

The deterministic Phase 6 provider directly returns canned explanations and a small number of suggestions. It is suitable for tests but not the Phase 7 product behavior.

## Target State

The Phase 7 base will provide:

- An OpenAI Responses API adapter behind the existing provider-neutral server boundary.
- Explicit server configuration for provider endpoint, pinned model identifier, prompt version, storage policy, timeouts, retries, and run limits.
- A server-created immutable scope and registry snapshot for each AI run.
- Strict custom function schemas and Kotlin argument validation for every available capability.
- Read capabilities over existing project, semantic, reasoning, SHACL, proposal, FIBO, workflow, permission, and help services.
- Private, in-memory, user-and-conversation-owned AI drafts made only from approved typed Entio edits.
- Deterministic draft validation, preview, diff, reasoning, SHACL, and impact through existing services.
- Bounded planning, clarification, tool use, self-correction, cancellation, and limit handling.
- Explicit draft submission into the existing human proposal-review workflow with AI attribution.
- Versioned HTTP and private server-sent event contracts for conversations, runs, drafts, and run activity.
- A conversational React assistant with evidence, tool summaries, draft review, analysis, stale/conflict states, and submit-for-review controls.
- Deterministic fake-provider, security, prompt-injection, scope, limit, provider, web, and end-to-end tests.

## Scope

### Affected modules and files

Primary implementation areas:

```text
web-server/build.gradle.kts
web-server/src/main/kotlin/com/entio/web/Application.kt
web-server/src/main/kotlin/com/entio/web/ai/
web-server/src/main/kotlin/com/entio/web/contract/
web-server/src/main/resources/entio-help/v1/
web-server/src/test/kotlin/com/entio/web/
web-server/src/test/kotlin/com/entio/web/ai/

web-app/src/workbench/AiAssistantPanel.tsx
web-app/src/workbench/AiCredentialSettings.tsx
web-app/src/web/contracts.ts
web-app/src/web/projectApi.ts
web-app/src/web/queries.ts
web-app/src/styles.css
web-app/src/**/*.test.ts
web-app/src/**/*.test.tsx
web-app/src/App.e2e.test.tsx

docs/decisions/phase-7-slice-*.md
docs/phase-summaries/phase-7-summary.md
```

Existing Kotlin semantic modules are dependencies, not default edit targets. If a required service is not reusable from `web-server`, stop and revise the approved plan before modifying `core-types`, `semantic-engine`, `validation-engine`, `graph-diff`, `cli`, or `shared`.

### Recommended dependencies

Add only the provider transport dependencies required by `web-server`:

- `io.ktor:ktor-client-core-jvm` for the provider-neutral HTTP client boundary.
- `io.ktor:ktor-client-cio-jvm` for production outbound HTTPS from the Kotlin server.
- `io.ktor:ktor-client-content-negotiation-jvm` and the existing Jackson integration for typed JSON requests and responses.
- `io.ktor:ktor-client-mock-jvm` as a test dependency for deterministic provider contract tests.
- `io.ktor:ktor-server-sse-jvm` only for the private AI run event endpoint.

Use the same approved Ktor version already declared by `web-server`. Do not add an unofficial OpenAI SDK, JSON-schema framework, database, queue, dependency-injection framework, vector store, or second web framework. Strict capability schemas should be explicit Entio definitions paired with typed Jackson decoding and Kotlin semantic validation.

## Non-Goals

This plan must not implement:

- Autonomous proposal approval, rejection, application, or rollback.
- Direct RDF/Turtle/SPARQL editing or source-file writes by AI code.
- Project configuration, registry, permission, role, model-policy, or immutable FIBO mutation.
- Arbitrary file, shell, code, environment, browser, remote MCP, or network tools.
- OpenAI built-in web search, file search, code interpreter, or computer-use tools.
- Durable conversation, draft, audit, credential, collaboration, or job persistence.
- Production authentication, tenancy, administrator controls, organization billing, deployment, or observability.
- Multi-provider product support beyond retaining a provider-neutral interface and implementing OpenAI first.
- Embeddings, vector search, document ingestion, entity-resolution agents, or autonomous multi-agent workflows.
- A new semantic or AI Gradle module.
- Semantic policy in React or provider DTOs in semantic core modules.
- AI SHACL mutation outside the bounded operations completed and proven by the Phase 7 typed SHACL proposal-integration slice.
- Changes to CLI or VS Code behavior.

## Fixed Implementation Decisions

### Provider transport

- `OpenAiResponsesClient` uses HTTPS from `web-server` to `https://api.openai.com/v1/responses`.
- The only permitted production provider host is `api.openai.com`; redirects to another host are rejected.
- The initial approved model identifier is `gpt-5.2`.
- The server configuration must expose `ENTIO_OPENAI_MODEL`, but Phase 7 accepts only the fixed allowlist `gpt-5.2`. Adding or replacing a model requires a separately approved configuration change and provider-contract regression run.
- Moving aliases such as `latest` are rejected.
- Provider response storage is disabled by default and Phase 7 requests must send `store: false`.
- Phase 7 does not use provider background mode.
- The provider connect timeout is 10 seconds.
- The provider request timeout is 120 seconds.
- A provider request may be retried at most twice for connection failure, HTTP 408, HTTP 429, or HTTP 5xx responses.
- Retries use exponential backoff with jitter and must preserve the Entio request ID and idempotency boundary.
- Authentication, authorization, malformed request, refusal, and semantic tool-validation failures are not retried automatically.
- OpenAI custom function tools come only from the current `AiCapabilityRegistry` snapshot.
- Automated tests use a fake provider or Ktor `MockEngine`; they never use a real API key or network.

### State ownership

- Conversation, run, draft, and audit stores are in-memory interfaces keyed by user and project.
- Entio reconstructs bounded provider context for each request and does not depend solely on provider-managed conversation history.
- The credential store remains server-only and per-user.
- Private AI events use a user-scoped SSE endpoint, not the project collaboration broadcast.

### Draft and review boundary

- Capability calls may read Entio state or mutate only the current private AI draft.
- Draft items hold approved typed Entio requests and never raw triples.
- Submission revalidates and atomically imports the draft into an ordinary Entio proposal-review path.
- A non-empty or incompatible shared staging state produces a structured conflict rather than partial mixing.
- Submission returns a proposal for human review; it does not approve or apply it.

### Prompt and evidence boundary

- Trusted policy, user input, tool definitions, and untrusted ontology/external data are structurally separate.
- Assistant claims about ontology state must reference Entio evidence.
- Tool summaries may be shown; hidden model reasoning and raw provider payloads must not be exposed.

### Typed-edit capability inventory

Before Slice 6 implementation begins, Slice 6 must create and approve:

```text
docs/decisions/phase-7-slice-7-typed-edit-capability-inventory.md
```

The inventory must list every proposed AI mutation capability and prove the corresponding existing Entio path:

```text
AI capability
→ existing typed request or intent
→ deterministic validation
→ in-memory preview
→ semantic diff
→ proposal review
→ human apply
→ reload
→ rollback
```

For every capability, record:

- AI capability name;
- existing Kotlin typed request or intent;
- translator/service used;
- supported target entity kinds;
- supported target source roles;
- dependency-review requirements;
- preview support;
- validation support;
- reasoning/SHACL impact support;
- application support;
- rollback support;
- tests proving parity with the non-AI path;
- final status: `APPROVED`, `READ_ONLY`, `DEFERRED`, or `UNSUPPORTED`.

Only capabilities marked `APPROVED` may enter the AI registry.

The inventory must not infer support from the existence of a Kotlin class alone.

### SHACL mutation decision

Phase 7 will complete a bounded typed SHACL proposal pathway before exposing any model-callable SHACL mutation capability.

The new typed SHACL proposal-integration slice must prove that each supported operation travels through:

```text
typed SHACL request
→ deterministic request validation
→ ordinary Entio staging
→ preview shapes graph
→ current and preview SHACL validation
→ human-readable semantic diff
→ new/worsened/unchanged/resolved finding impact
→ human review
→ atomic application
→ reload verification
→ rollback on failure
```

Only SHACL operations proven by this path and marked `APPROVED` in the typed-edit capability inventory may enter the AI capability registry.

The initial bounded SHACL mutation set may include:

- create node shape;
- create property shape;
- set target class;
- set property path;
- set minimum count;
- set maximum count;
- set datatype;
- set expected class;
- set minimum inclusive value;
- set maximum inclusive value;
- set pattern;
- set severity;
- set validation message;
- update a supported constraint;
- remove a supported constraint;
- delete a shape.

The following remain excluded:

- arbitrary SHACL RDF mutation;
- raw Turtle or SPARQL updates;
- SHACL-SPARQL constraints;
- JavaScript constraints;
- custom constraint components;
- arbitrary or recursive property paths;
- complex logical combinations without a safe typed contract;
- any shape operation that cannot complete the full preview, review, application, reload, and rollback lifecycle.

The AI may still inspect and explain all available SHACL results. It may mutate SHACL only through inventory-approved typed capabilities after the non-AI pathway passes its end-to-end tests.

### Private SSE retention and reconnect policy

The private AI SSE boundary uses in-memory, per-user event retention.

- Retain the latest 250 safe events per AI run.
- Retain events only for the server lifetime.
- Retain terminal-run events until the owning conversation is removed, the user session ends, or the server restarts.
- Each event has a monotonically increasing per-run sequence and SSE event ID.
- A reconnect may supply the last received event ID.
- If the requested event is still retained, stream subsequent events.
- If the event is no longer retained or a sequence gap is detected, return a structured resynchronization signal and require authoritative HTTP refetch.
- SSE events never contain credentials, raw provider payloads, hidden reasoning, private data from another user, or unrestricted ontology content.
- No durable replay log is introduced.

## Dependency Order And Multi-Agent Safety

All slices are serial because they evolve shared AI contracts, server routes, and the same assistant UI. Do not implement slices in parallel.

Dependency order:

```text
1. Contracts and run policy
2. Scope, capability registry, and strict schemas
3. OpenAI provider and credential hardening
4. Local read and help capabilities
5. Reasoning, SHACL, proposal, and FIBO read capabilities
6. Typed SHACL proposal integration
7. Private draft and inventory-approved typed edit capabilities
8. Draft analysis and bounded self-correction
9. Conversation service and bounded tool loop
10. Human review submission, attribution, and audit
11. Versioned HTTP and private SSE contracts
12. React conversation and draft-review experience
13. Security, limits, and failure hardening
14. End-to-end regression and Phase 7 summary
```

Do not allow parallel work on:

- `web-server/src/main/kotlin/com/entio/web/ai/`;
- API DTOs or routes;
- capability names or schemas;
- run/draft/conversation status contracts;
- provider configuration;
- shared web query keys;
- specs, ExecPlans, build files, or `AGENTS.md`.

## Implementation Slices

### Slice 1: AI Conversation, Run, Draft, And Policy Contracts

#### Goal

Replace loose Phase 6 assistant state with explicit server-owned contracts for conversations, runs, scopes, drafts, revisions, responses, evidence, warnings, limits, statuses, and session-memory stores. Preserve the current provider-neutral boundary while making it suspendable and cancellable.

#### Allowed files/modules

- `web-server/src/main/kotlin/com/entio/web/ai/`
- `web-server/src/test/kotlin/com/entio/web/ai/`
- Existing Phase 6 AI tests when contract names change.
- `docs/decisions/phase-7-slice-1-ai-contracts.md`

#### Dependencies

- Approved Phase 7 spec and ExecPlan.
- Completed Phase 6 AI credential and assistant foundation.

#### Forbidden actions/modules

- No OpenAI network client.
- No tool execution or semantic adapters.
- No web routes or React changes.
- No edits to semantic core modules, CLI, VS Code, or build dependencies.
- No approval/apply capability in any enum or contract.

#### Expected output

- Explicit `AiConversation`, `AiConversationMessage`, `AiRun`, `AiRunStatus`, `AiRunPolicy`, `AiCapabilityScope`, `AiDraft`, `AiDraftItem`, `AiDraftRevision`, `AiDraftStatus`, `AiAuditRecord`, and structured response contracts.
- In-memory store interfaces and deterministic implementations with user/project ownership checks.
- Default run limits matching the approved spec.
- Cancellation-safe provider interfaces using `suspend` where required.

#### Tests

- Contract construction and deterministic ordering.
- Cross-user and cross-project store access rejection.
- Draft ownership and baseline state.
- One-active-run policy.
- Run status transition validity and cancellation.
- No secret fields in response or audit DTOs.

#### Verification commands

```bash
./gradlew :web-server:test
./gradlew test
git diff --check
```

#### Stop conditions

- Stop if these contracts require provider types in `core-types`.
- Stop if the existing identity boundary cannot provide a stable user ID.
- Stop if model-accessible approval, apply, filesystem, or configuration authority is required.

### Slice 2: Capability Scope, Registry, And Strict Tool Schemas

#### Goal

Create the allowlisted capability registry, immutable run scope, strict JSON schemas, typed argument decoding, authorization checks, and audit metadata before implementing any provider tool loop.

#### Allowed files/modules

- `web-server/src/main/kotlin/com/entio/web/ai/`
- `web-server/src/main/kotlin/com/entio/web/contract/` for additive permission/feature metadata only.
- `web-server/src/test/kotlin/com/entio/web/ai/`
- `docs/decisions/phase-7-slice-2-capability-registry.md`

#### Dependencies

- Slice 1.
- Existing project registry, development identity, permissions, and source descriptors.

#### Forbidden actions/modules

- No provider network calls.
- No capability implementation that reads or mutates semantic state yet.
- No raw arbitrary JSON object arguments.
- No raw paths, Turtle, SPARQL, shell, code, unrestricted URL, secret, approval, rejection, apply, or rollback schema.
- No React or semantic core edits.

#### Expected output

- `AiCapabilityRegistry`, definitions, categories, stable operation types, invocation/result envelopes, and confirmation metadata.
- Server-created scope from user, project, conversation, source, baseline, feature, and permission state.
- Strict tool schemas with `additionalProperties: false`, bounded arrays, required fields, stable enums, and typed decoders.
- Per-call scope revalidation and registry snapshot validation.
- Explicit forbidden-capability tests.

#### Tests

- Valid schemas accept valid inputs.
- Unknown fields, missing fields, invalid enum values, invalid IRIs, oversized arrays, and malformed values fail.
- Registry output contains only currently allowed capabilities.
- Stale registry output cannot invoke a removed capability.
- Project/source/user/permission scope violations fail.
- Forbidden capability names are absent.

#### Verification commands

```bash
./gradlew :web-server:test
./gradlew test
git diff --check
```

#### Stop conditions

- Stop if authorization cannot be checked on every invocation.
- Stop if strict schemas would require arbitrary unvalidated maps.
- Stop if a requested operation lacks an approved Entio service boundary.

### Slice 3: OpenAI Responses Provider And Credential Hardening

#### Goal

Implement the initial real OpenAI provider adapter, explicit model/provider configuration, safe provider testing, request/response mapping, usage metadata, streaming event parsing, cancellation, timeout behavior, and redaction.

#### Allowed files/modules

- `web-server/build.gradle.kts`
- `web-server/src/main/kotlin/com/entio/web/ai/`
- `web-server/src/test/kotlin/com/entio/web/ai/`
- Existing AI credential tests.
- `docs/decisions/phase-7-slice-3-openai-provider.md`

#### Dependencies

- Slices 1 and 2.
- Existing in-memory credential service.
- The fixed provider, model, storage, timeout, retry, and endpoint decisions in this ExecPlan.

#### Forbidden actions/modules

- No React calls to OpenAI.
- No API key in DTO responses, logs, exceptions, URLs, events, audit records, or snapshots.
- No OpenAI built-in tools.
- No tool execution yet; provider tests may round-trip fixture tool calls only.
- No unofficial OpenAI SDK, database, or logging interceptor that can expose headers.
- No edits to semantic core, CLI, or VS Code.

#### Expected output

- Ktor client dependencies using the existing Ktor version.
- `OpenAiResponsesClient` behind the provider-neutral interface.
- Required explicit model ID, prompt version, endpoint allowlist, storage behavior, timeout, and retry classification.
- Strict custom-function serialization from registry definitions.
- Provider event parsing for text deltas, function calls, completion, refusal, incomplete, cancellation, usage, and errors.
- Safe credential test against the provider boundary.
- Deterministic `MockEngine` tests and a retained fake provider.

#### Tests

- Request shape contains only the configured model, trusted input, and supplied custom functions.
- `latest`-style unapproved model aliases and non-OpenAI endpoints are rejected.
- Function call IDs and arguments are parsed without executing unknown tools.
- Timeout, rate limit, invalid key, malformed response, refusal, incomplete response, and cancellation map to structured states.
- Headers and provider response bodies are redacted from user-facing errors.
- Credential separation and deletion remain correct.

#### Verification commands

```bash
./gradlew :web-server:test
./gradlew test
./gradlew :web-server:build
git diff --check
```

#### Stop conditions

- Stop if the configured model is not `gpt-5.2`, the provider endpoint is not the approved OpenAI endpoint, or `store: false` cannot be enforced.
- Stop if provider errors can leak request headers or credentials.
- Stop if automated tests require a live API key or external network.

### Slice 4: Local Ontology Context And Versioned Help Capabilities

#### Goal

Implement bounded, read-only capabilities for project summaries, entity descriptors, comparisons, local search, hierarchy, usage, screen context, available actions, permissions, workflow state, feature help, and error-code explanations.

#### Allowed files/modules

- `web-server/src/main/kotlin/com/entio/web/ai/`
- `web-server/src/main/resources/entio-help/v1/`
- `web-server/src/test/kotlin/com/entio/web/ai/`
- Existing read-only web adapters as dependencies, not semantic rewrites.
- `docs/decisions/phase-7-slice-4-local-read-help-capabilities.md`

#### Dependencies

- Slices 1 and 2.
- Existing read-only project adapter and web permission/workflow metadata.

#### Forbidden actions/modules

- No draft mutation.
- No direct file reads or arbitrary classpath resource selection from model arguments.
- No full-project or full-source context by default.
- No model-authored help treated as trusted policy.
- No semantic logic in route handlers or React.

#### Expected output

- Read-only capability adapters returning bounded structured DTOs and stable evidence references.
- Context builder that selects only request-relevant project/entity neighborhoods.
- Versioned, server-bundled help entries and a help service constrained to known IDs.
- Clear asserted/inferred/external/staged provenance fields where applicable.

#### Tests

- Relevant context is returned in deterministic order and within limits.
- Unrelated project/source/private data is absent.
- Help matches current feature and permission metadata.
- Unknown entities, screens, actions, and error codes fail safely.
- Malicious ontology/help text remains data and cannot alter capability scope.

#### Verification commands

```bash
./gradlew :web-server:test
./gradlew test
git diff --check
```

#### Stop conditions

- Stop if a capability requires arbitrary filesystem access.
- Stop if context cannot be bounded without changing semantic behavior.
- Stop if help claims an action not exposed by current Entio metadata.

### Slice 5: Reasoning, SHACL, Proposal, Activity, And FIBO Read Capabilities

#### Goal

Add bounded read adapters for existing reasoning, consistency, unsatisfiable classes, explanations, SHACL findings, staged changes, validation, semantic diff, proposal impact, activity, FIBO search, descriptors, and dependency review.

#### Allowed files/modules

- `web-server/src/main/kotlin/com/entio/web/ai/`
- `web-server/src/test/kotlin/com/entio/web/ai/`
- Existing semantic-job, staging, collaboration, and FIBO web services as dependencies.
- `docs/decisions/phase-7-slice-5-semantic-read-capabilities.md`

#### Dependencies

- Slices 1, 2, and 4.
- Existing Phase 4 reasoning/SHACL and Phase 5 FIBO services.

#### Forbidden actions/modules

- No new reasoner, SHACL engine, FIBO ranking, proposal diff, or activity store.
- No FIBO mutation.
- No direct access to another user's private activity or draft.
- No draft mutation.
- No raw full result graphs in provider context.

#### Expected output

- Capability adapters over current structured results and fingerprints.
- Bounded summaries plus stable result references for detailed UI retrieval.
- Explicit incomplete, stale, unsupported, unavailable, and permission states.
- Evidence provenance for asserted/inferred/SHACL/external/proposal facts.

#### Tests

- Existing result fingerprints and statuses are preserved.
- Stale and incomplete semantic jobs are not presented as current facts.
- FIBO ranking and descriptors match deterministic Phase 5 services.
- Proposal and activity access respects project and user scope.
- Result-size limits truncate safely with an explicit continuation/limit marker.

#### Verification commands

```bash
./gradlew :web-server:test
./gradlew test
git diff --check
```

#### Stop conditions

- Stop if adapters would duplicate semantic calculations.
- Stop if FIBO or semantic job results cannot be tied to stable fingerprints.
- Stop if private collaboration data would be exposed.

### Slice 6: Typed SHACL Proposal Integration

#### Goal

Complete the non-AI typed SHACL editing workflow by connecting the existing `ShaclShapeAuthoringService` and SHACL impact machinery to the ordinary Entio staging, preview, diff, human review, atomic application, reload verification, and rollback path.

This slice must finish and prove the public typed SHACL proposal boundary before any SHACL mutation capability is exposed to the AI.

#### Allowed files/modules

- `core-types` only for additive typed SHACL request and result contracts when a genuinely cross-client contract is required.
- `semantic-engine` only for narrow reusable typed SHACL translation, source-targeting, and validation adapters.
- `graph-diff` only for human-readable SHACL change descriptions or missing impact metadata.
- `web-server` staging, proposal mapping, source validation, review DTOs, and focused tests.
- `cli` structured proposal mapping and focused tests when CLI parity is part of the existing public proposal contract.
- `web-app` only for the minimum SHACL proposal form or review rendering needed to prove the ordinary non-AI workflow.
- `docs/decisions/phase-7-slice-6-typed-shacl-proposal-integration.md`.

#### Dependencies

- Slices 1 through 5.
- Existing `ShaclShapeAuthoringService`.
- Existing SHACL parsing and validation.
- Existing proposal-impact comparison of new, worsened, unchanged, and resolved findings.
- Existing staged-change, preview, atomic multi-source application, reload-verification, and rollback services.
- Existing source-role, mutability, and project-scope protections.

#### Initial supported operations

The bounded initial operation set may include:

- create node shape;
- create property shape;
- set target class;
- set property path;
- set minimum count;
- set maximum count;
- set datatype;
- set expected class;
- set minimum inclusive value;
- set maximum inclusive value;
- set pattern;
- set severity;
- set validation message;
- update one supported constraint;
- remove one supported constraint;
- delete a shape.

Only operations that can be represented by strict typed contracts and complete the full lifecycle may be marked supported.

#### Explicitly deferred operations

- SHACL-SPARQL constraints.
- JavaScript constraints.
- Custom constraint components.
- Arbitrary RDF shape mutation.
- Raw Turtle or SPARQL updates.
- Arbitrary or recursive property paths.
- Complex logical combinations without an existing safe typed representation.
- Any operation that cannot complete preview, impact, review, atomic application, reload verification, and rollback.

#### Forbidden actions/modules

- No AI or OpenAI tool exposure in this slice.
- No raw Turtle, raw RDF triple, SPARQL update, or arbitrary predicate staging.
- No direct source-file mutation from HTTP, CLI mapping, or React.
- No bypass of ordinary staged-change, proposal, approval, application, or rollback boundaries.
- No new SHACL engine.
- No weakening of existing source-role, baseline, validation, or permission checks.
- No claim of support based only on the existence of a lower-level authoring class.

#### Required workflow

Every supported SHACL edit must pass through:

```text
typed SHACL request
→ request and source validation
→ ordinary staged change
→ preview shapes graph
→ validate current data against current shapes
→ validate preview data against preview shapes
→ classify new, worsened, unchanged, and resolved findings
→ human-readable SHACL semantic diff
→ human review
→ atomic application across all affected sources
→ reload
→ post-apply verification
→ rollback on failure
```

#### Expected output

- Stable typed SHACL edit contracts.
- Structured CLI mapping where required by the existing public proposal contract.
- Web staging support in the same proposal system used by ontology edits.
- Shape-source targeting, role, mutability, and baseline validation.
- Human-readable descriptions of supported SHACL changes.
- Preview behavior that distinguishes the current and preview shapes graphs.
- Proposal impact that identifies affected entities and finding-state changes.
- Atomic combined ontology-and-SHACL application when one proposal affects both graph families.
- Reload verification and rollback across all affected sources.
- End-to-end tests proving the complete ordinary non-AI lifecycle.
- No raw SHACL fallback path.

#### Tests

At minimum:

1. Create a property shape requiring one borrower.
2. Preview displays the shape addition.
3. SHACL impact reports newly invalid entities.
4. Rejection leaves all source files unchanged.
5. Human approval updates the correct shape source.
6. Reload reproduces the previewed findings.
7. A forced post-save verification failure rolls back every affected source.
8. Edit an existing supported constraint.
9. Remove a supported constraint.
10. Delete a shape with dependency review.
11. Reject an unsupported property path.
12. Reject immutable, incorrect-role, unregistered, or out-of-scope source targets.
13. Reject a stale baseline.
14. Apply a combined ontology-and-SHACL proposal atomically.
15. Preserve existing ontology-only proposal behavior.

#### Verification commands

```bash
./gradlew :semantic-engine:test
./gradlew :graph-diff:test
./gradlew :web-server:test
./gradlew :cli:test
./gradlew test
(cd web-app && npm ci && npm test && npm run build)
git diff --check
```

If the slice does not change `web-app`, the frontend command may be omitted and the completion artifact must state that no frontend file was changed.

#### Stop conditions

- Stop if a proposed operation cannot be represented through a typed contract.
- Stop if preview requires direct source-file mutation.
- Stop if current and preview shape graphs cannot be distinguished.
- Stop if impact cannot be tied to exact graph, shape, and proposal fingerprints.
- Stop if application cannot be atomic across all affected sources.
- Stop if rollback cannot restore every affected source.
- Stop if public staging would require raw Turtle, raw triples, or arbitrary SPARQL.
- Stop if ordinary non-AI SHACL staging and review cannot be proven before AI exposure.

### Slice 7: Private AI Draft And Typed Edit Capabilities

#### Goal

Create the approved typed-edit capability inventory, then implement the private AI draft workspace and capability adapters only for ontology, metadata, deletion, external reuse, and SHACL entries marked `APPROVED`.

#### Allowed files/modules

- `web-server/src/main/kotlin/com/entio/web/ai/`
- `web-server/src/main/kotlin/com/entio/web/StagingWorkflowService.kt` only for a narrowly approved reusable typed-request adapter when unavoidable.
- `web-server/src/test/kotlin/com/entio/web/ai/`
- `docs/decisions/phase-7-slice-7-private-draft-typed-edits.md`

#### Dependencies

- Slices 1 through 6.
- The completed typed SHACL proposal pathway from Slice 6.
- Existing typed ontology edits, semantic edit requests, deletion review, FIBO intents, translators, and proposal services.

#### Forbidden actions/modules

- No source-file writes or shared staging mutation.
- No raw triples, Turtle, SPARQL, or arbitrary predicates.
- No SHACL mutation outside the operations proven by Slice 6 and marked `APPROVED` in the inventory; no raw SHACL fallback.
- No approval, rejection, application, or rollback operation.
- No new semantic behavior in `web-server`.
- No edits to semantic core modules without stopping and revising the plan.

#### Expected output

- `docs/decisions/phase-7-slice-7-typed-edit-capability-inventory.md` proving the complete Entio lifecycle for every candidate AI edit.
- Private draft create/read/add/update/remove/reorder/undo/clear behavior with revision history.
- Stable capability adapters only for class, property, individual, type, hierarchy, domain/range, assertion, label, definition, alternate-label, annotation, deletion, external reuse, local-subclass, and bounded SHACL intents marked `APPROVED` by the inventory.
- Model-callable SHACL tools for the bounded operation set proven by Slice 6, with strict schemas and no raw RDF representation.
- Deterministic label/IRI resolution through existing services.
- Source mutability, source role, dependency, duplicate, and conflict checks.
- Explicit report of unsupported operations rather than raw RDF fallback.
- Audit-safe rationale and AI attribution per item.

#### Tests

- Each approved edit capability produces the same typed request/change intent as the corresponding non-AI path.
- Add/update/remove/reorder/undo/clear preserve deterministic revision history.
- Cross-user and cross-conversation mutation fails.
- Immutable FIBO and out-of-scope sources cannot be targeted.
- Deletion requires explicit dependency selection.
- Unsupported SHACL, deferred advanced SHACL, and arbitrary graph requests fail without draft mutation.
- Every approved SHACL capability produces the same typed request and proposal behavior as the corresponding non-AI path from Slice 6.
- Shared staged state remains unchanged.

#### Verification commands

```bash
./gradlew :web-server:test
./gradlew test
git diff --check
```

#### Stop conditions

- Stop if any advertised edit lacks an existing typed translator and proposal path.
- Stop if private draft mutation requires shared staging side effects.
- Stop if the inventory cannot prove the complete existing lifecycle for an advertised capability.
- Stop if a SHACL tool cannot reuse the typed public pathway completed in Slice 6.
- Keep every unproven or deferred SHACL operation absent.

### Slice 8: Draft Validation, Preview, Semantic Analysis, And Self-Correction

#### Goal

Connect private drafts to existing deterministic validation, in-memory preview, semantic diff, OWL reasoning, SHACL validation, and proposal-impact services, then add bounded revision from structured findings.

#### Allowed files/modules

- `web-server/src/main/kotlin/com/entio/web/ai/`
- `web-server/src/test/kotlin/com/entio/web/ai/`
- Existing semantic services as dependencies.
- `docs/decisions/phase-7-slice-8-draft-analysis-self-correction.md`

#### Dependencies

- Slices 5 through 7.
- Existing proposal preview, validation, diff, reasoning, SHACL, and impact services.

#### Forbidden actions/modules

- No model judgment as a validation result.
- No source writes, shared staging changes, proposal approval, or application.
- No weakening, filtering, or suppression of blocking findings.
- No new reasoner, SHACL validator, or diff engine.
- No unbounded correction loop.

#### Expected output

- Draft fingerprint and baseline-aware analysis service.
- Structured analysis references tied to one exact draft revision.
- Stale invalidation when project or draft fingerprint changes.
- Capability operations to validate, preview, reason, run SHACL, and inspect impact.
- Self-correction controller limited to the configured cycles and current private draft.
- Revision explanation and preservation of original findings.

#### Tests

- Analysis matches existing service output for the same change set.
- Cached results are reused only when fingerprints match.
- Draft mutation invalidates prior analysis.
- Blocking findings prevent ready-for-review state.
- Correction changes only the private draft and reruns analysis.
- Correction limit stops safely and preserves the draft.
- Provider narrative cannot convert a failure to success.

#### Verification commands

```bash
./gradlew :web-server:test
./gradlew test
./gradlew build
git diff --check
```

#### Stop conditions

- Stop if an analysis stage cannot use an existing deterministic service.
- Stop if a result cannot be associated with baseline and draft fingerprints.
- Stop if self-correction would modify shared or applied state.

### Slice 9: Conversation Service, Planning, Clarification, And Bounded Tool Loop

#### Goal

Implement reciprocal conversations and the bounded OpenAI tool loop over the completed registry, read adapters, private draft, and analysis services.

#### Allowed files/modules

- `web-server/src/main/kotlin/com/entio/web/ai/`
- `web-server/src/test/kotlin/com/entio/web/ai/`
- `docs/decisions/phase-7-slice-9-conversation-tool-loop.md`

#### Dependencies

- Slices 1 through 8.
- OpenAI adapter and deterministic fake provider.

#### Forbidden actions/modules

- No recursive AI invocation.
- No parallel mutation of one draft.
- No provider-controlled authorization or conversation authority.
- No hidden expansion of tool scope between loop iterations.
- No use of provider built-in tools.
- No automatic submission for human review.

#### Expected output

- Application-owned bounded conversation reconstruction.
- Intent classification into explanation, small edit, broad plan, clarification, draft management, analysis, help, and out-of-scope states.
- Plan confirmation and clarification state machines.
- Sequential tool loop with call, request, edit, correction, context, elapsed-time, and usage limits.
- Cancellation propagation to provider and current capability work.
- Stable run/audit events and safe final response assembly.

#### Tests

- Focused request prepares a draft directly.
- Broad request pauses for plan confirmation.
- Material ambiguity pauses for clarification and resumes with bounded context.
- Follow-up, undo, revision, and explanation requests preserve relevant state.
- Tool output is returned to the provider in the correct call order.
- Unknown, replayed, duplicate, unauthorized, and over-limit calls fail.
- Cancellation and every configured limit produce the expected terminal/nonterminal state.
- Provider-managed response IDs are supplemental, not authoritative.

#### Verification commands

```bash
./gradlew :web-server:test
./gradlew test
./gradlew build
git diff --check
```

#### Stop conditions

- Stop if a tool loop can execute a capability outside its registry snapshot.
- Stop if cancellation cannot terminate provider and draft mutation work.
- Stop if large requests mutate a draft before required plan confirmation.

### Slice 10: Human Review Submission, AI Attribution, And Audit

#### Goal

Submit a current, fully analyzed private AI draft into the existing human proposal-review workflow without granting the model review or application authority.

#### Allowed files/modules

- `web-server/src/main/kotlin/com/entio/web/ai/`
- `web-server/src/main/kotlin/com/entio/web/StagingWorkflowService.kt` for a narrow atomic import/proposal method.
- `web-server/src/main/kotlin/com/entio/web/CollaborationHub.kt` for submitted-proposal events only.
- Related server tests.
- `docs/decisions/phase-7-slice-10-human-review-submission.md`

#### Dependencies

- Slices 7 through 9.
- Existing shared staging, proposal, permission, collaboration, apply, and rollback behavior.

#### Forbidden actions/modules

- No model-callable approve, reject, apply, or rollback operation.
- No partial import when submission fails.
- No silent merge with incompatible shared staged changes.
- No broadcast of private messages, draft revisions, or provider text.
- No source-file write during submission.

#### Expected output

- Atomic submission service that revalidates ownership, scope, baseline, draft fingerprint, and analysis fingerprints.
- Creation of an ordinary Entio review proposal with AI marker, submitting user, conversation/run reference, rationale, diff, and analysis references.
- Locked/versioned submitted draft and returned proposal ID/review state.
- Session-memory audit record and safe shared collaboration event.
- Structured conflict when shared staging cannot accept the draft safely.

#### Tests

- Explicit user action is required.
- Current valid draft creates one review proposal and no source mutation.
- Stale, incomplete, blocked, unauthorized, or conflicting drafts do not partially stage.
- Attribution and rationale survive into review DTOs.
- Other collaborators see submitted metadata but not private conversation content.
- Existing human reviewer permissions and apply/rollback tests remain unchanged.

#### Verification commands

```bash
./gradlew :web-server:test
./gradlew test
./gradlew build
git diff --check
```

#### Stop conditions

- Stop if submission cannot be atomic.
- Stop if existing review controls cannot consume the resulting proposal.
- Stop if any path implicitly approves or applies the proposal.

### Slice 11: Versioned AI HTTP And Private SSE Boundary

#### Goal

Expose conversations, messages, runs, cancellation, drafts, analysis, submission, help, and private run events through versioned, project/user-scoped web contracts.

#### Allowed files/modules

- `web-server/build.gradle.kts` for the approved SSE dependency.
- `web-server/src/main/kotlin/com/entio/web/Application.kt`
- `web-server/src/main/kotlin/com/entio/web/contract/`
- `web-server/src/main/kotlin/com/entio/web/ai/`
- `web-server/src/test/kotlin/com/entio/web/`
- `docs/decisions/phase-7-slice-11-ai-web-contracts.md`

#### Dependencies

- Slices 1 through 10.
- Existing `/api/v1` structured error and identity boundaries.
- The fixed private SSE retention and reconnect policy in this ExecPlan.

#### Forbidden actions/modules

- No OpenAI credential or raw provider payload in response DTOs.
- No private AI event over project-wide collaboration WebSocket.
- No route-level semantic logic.
- No unversioned endpoints.
- No client-supplied project roots, source paths, scope, permissions, or model IDs.

#### Expected output

- Versioned conversation/run/draft/help DTOs and routes.
- Private authenticated SSE stream implementing the fixed 250-event per-run in-memory retention, ordered event IDs, reconnection cursor, resynchronization signal, and authoritative HTTP recovery policy.
- Idempotency for message submission, draft mutation, and review submission where retries could duplicate effects.
- Structured status/error mapping and authoritative GET recovery after disconnect.
- Deprecation or compatibility handling for the Phase 6 single-request assistant route.

#### Tests

- Route success and every structured failure status.
- Cross-user/project resource access rejection.
- SSE event ordering, filtering, reconnect, completion, cancellation, and no secret fields.
- Retry/idempotency does not duplicate messages, edits, or proposals.
- Old Phase 6 route either delegates compatibly or returns a documented versioned deprecation response.

#### Verification commands

```bash
./gradlew :web-server:test
./gradlew test
./gradlew :web-server:build
git diff --check
```

#### Stop conditions

- Stop if SSE events cannot be isolated per user.
- Stop if route DTOs expose provider or semantic library types.
- Stop if retries can duplicate draft or proposal mutations.

### Slice 12: React Conversation, Draft Review, And Human Handoff

#### Goal

Replace the Phase 6 operation dropdown assistant with a conversational panel that consumes the versioned Phase 7 APIs, streams safe run activity, reviews private drafts, and hands submitted proposals to the existing review UI.

#### Allowed files/modules

- `web-app/src/workbench/AiAssistantPanel.tsx`
- `web-app/src/workbench/AiCredentialSettings.tsx`
- New focused components under `web-app/src/workbench/ai/`
- `web-app/src/web/contracts.ts`
- `web-app/src/web/projectApi.ts`
- `web-app/src/web/queries.ts`
- `web-app/src/styles.css`
- Related web-app unit/component/e2e tests.
- `docs/decisions/phase-7-slice-12-ai-conversation-ui.md`

#### Dependencies

- Slice 11.
- Existing Phase 6 light workbench, settings tab, assistant drawer, proposal review, staging, and accessibility patterns.

#### Forbidden actions/modules

- No direct OpenAI call or retained API key.
- No semantic validation, typed-edit translation, prompt policy, or capability authorization in TypeScript.
- No production placeholder assistant data.
- No approve/apply control inside model output.
- No display of hidden reasoning, credentials, raw provider payloads, or backend failures as success.
- No unrelated visual redesign.

#### Expected output

- Conversation creation, message history, composer, clarification, plan confirmation, cancellation, and follow-up flows.
- Current project/entity context chips and credential/run status.
- Safe capability activity timeline and evidence/provenance views.
- Private draft item list, revisions, analysis states, diff/impact summaries, uncertainty, warnings, limits, stale/conflict states, and submit-for-review action.
- Link/navigation to the authoritative proposal review after submission.
- Labels as primary text with IRI/raw details behind disclosure.
- Loading, empty, error, disconnected, reconnecting, cancelled, permission, stale, conflict, and ready-for-review states.

#### Tests

- Conversation and follow-up rendering.
- Clarification and plan confirmation behavior.
- Stream event ordering and query-cache recovery.
- Draft revision, analysis, stale/conflict, and limit displays.
- Submit-for-review navigates to existing proposal review and never calls apply.
- Missing credential and provider failure preserve non-AI workbench behavior.
- Keyboard navigation, focus management, screen-reader labels, and reduced-motion behavior.

#### Verification commands

```bash
(cd web-app && npm ci && npm test && npm run build && npm run test:e2e)
git diff --check
```

#### Stop conditions

- Stop if the UI requires an unapproved backend contract.
- Stop if any semantic decision would be duplicated in React.
- Stop if the API key remains in React state after a successful save.
- Stop if private AI activity can appear in another user's session.

### Slice 13: Prompt-Injection, Limits, Redaction, And Failure Hardening

#### Goal

Perform a focused adversarial pass across provider requests, capability execution, context construction, streaming, drafts, audit records, UI states, and cancellation before end-to-end acceptance.

#### Allowed files/modules

- Phase 7 files in `web-server` and `web-app`.
- Security and failure-focused tests.
- `docs/decisions/phase-7-slice-13-security-hardening.md`

#### Dependencies

- Slices 1 through 13.

#### Forbidden actions/modules

- No new product capability.
- No relaxation of deterministic validation or human review.
- No logging of raw provider requests, credentials, or untrusted full project content.
- No broad refactor outside the Phase 7 boundary.

#### Expected output

- Prompt-injection fixture suite covering ontology, FIBO, help, user, proposal, and tool-result content.
- Exhaustive forbidden-capability and cross-scope tests.
- Limit, timeout, cancellation, retry, malformed-provider, disconnect, replay, and concurrency tests.
- Secret scanning assertions for DTOs, events, audits, errors, and logs.
- UI failure states that never imply success.

#### Tests

- Every prompt-injection and forbidden-tool case from the spec.
- One user's key, conversation, draft, run, and events are inaccessible to another.
- Each run limit stops safely and preserves the draft.
- Provider timeout/cancellation terminates work.
- Replayed tool/message/submission calls are idempotent or rejected.
- Error and audit serialization contains no key or authorization header.
- Non-AI routes remain healthy during provider outage.

#### Verification commands

```bash
./gradlew :web-server:test
./gradlew test
(cd web-app && npm ci && npm test && npm run build && npm run test:e2e)
git diff --check
```

#### Stop conditions

- Stop on any cross-user, cross-project, source-scope, secret, forbidden-tool, or authority-boundary failure.
- Stop if a limit or cancellation leaves uncontrolled work running.
- Stop if a provider failure is rendered as a completed or submitted change.

### Slice 14: End-To-End Phase 7 Regression And Documentation Summary

#### Goal

Verify the complete Phase 7 journey with deterministic fake-provider fixtures, preserve all Phase 1 through Phase 6 behavior, and document the implemented result and actual limitations.

#### Allowed files/modules

- Focused regression tests in `web-server` and `web-app`.
- Existing tests only when fixture expectations must reflect approved Phase 7 additive fields.
- `docs/decisions/phase-7-slice-14-e2e-regression.md`
- `docs/phase-summaries/phase-7-summary.md`

#### Dependencies

- Slices 1 through 12.

#### Forbidden actions/modules

- No new product behavior.
- No live OpenAI key or external network in CI.
- No mutation of committed examples or FIBO assets.
- No weakening or deletion of existing regression coverage to obtain a pass.
- No claim that durable storage, production identity, autonomous application, or deferred/unsupported SHACL mutation exists.

#### Expected output

- Copied-fixture server regression for conversation, read tools, multi-edit draft, analysis, correction, submission, human review, approval/application through the existing human path, reload, and rollback.
- Browser regression for credential status, conversation, clarification/plan, draft, analysis, cancellation, stale/conflict, submission, and proposal handoff.
- Phase 7 summary based on actual implementation, including deviations and limitations.
- Completion artifact listing slice verification and final hashes when implemented under the approved Git workflow.

#### Tests

- A focused request prepares one private draft item.
- A broad request pauses for plan confirmation, prepares multiple edits, validates, reasons, runs SHACL, inspects impact, and submits for human review.
- A structured failure triggers bounded correction.
- Human reviewer, not AI, approves and applies through existing controls.
- Rejection and forced post-save verification failure preserve existing rollback behavior.
- Prompt injection, stale baseline, permission denial, provider outage, cancellation, and limit cases complete safely.
- Committed example and FIBO package remain unchanged.

#### Verification commands

```bash
./gradlew test
./gradlew build
./gradlew check
(cd web-app && npm ci && npm test && npm run build && npm run test:e2e)
(cd vscode-extension && npm ci && npm test)
git diff --check
git status --short
```

#### Stop conditions

- Stop if any required Phase 7 acceptance case fails.
- Stop if any Phase 1 through Phase 6 regression fails.
- Stop if a committed fixture is mutated.
- Stop if the summary would need to claim behavior not proven by tests.

## Test Plan

### Kotlin unit tests

- Conversation, run, scope, draft, revision, audit, and limit contracts.
- In-memory store ownership and concurrency.
- Capability registry selection, strict schemas, typed decoding, and per-call authorization.
- Provider request/response/event mapping with Ktor `MockEngine`.
- Credential isolation and redaction.
- Bounded context construction and help lookup.
- Every read-only and typed-edit capability adapter, including inventory-approved SHACL tools.
- Draft analysis, stale invalidation, self-correction, and submission.
- Prompt injection, replay, cancellation, timeout, and limits.

### Ktor contract tests

- Versioned routes, structured errors, idempotency, ownership, and project/source scoping.
- SSE event privacy, ordering, reconnection, terminal states, and redaction.
- Compatibility/deprecation behavior for the Phase 6 assistant route.
- Provider outage isolation from non-AI routes.

### React tests

- Credential availability and safe credential clearing.
- Conversation, plan, clarification, tool summary, evidence, draft, analysis, stale/conflict, cancellation, limit, failure, and review handoff states.
- Query invalidation and authoritative recovery after stream disconnect.
- Accessibility, keyboard, focus, reduced motion, and progressive disclosure.

### End-to-end tests

- Use a deterministic fake provider and copied Entio fixture.
- Cover explanation-only, focused ontology edit, focused SHACL edit, combined ontology-and-SHACL edit, broad plan, correction, stale/conflict, cancellation, submission, and human review journeys.
- Verify AI never applies and source files remain unchanged until the existing human apply operation.
- Verify applied changes reload and forced failures roll back through existing behavior.

### Regression tests

- All Gradle modules.
- Existing web-server and web-app behavior.
- Existing CLI and VS Code extension tests.
- Phase 4 reasoning/SHACL and Phase 5 FIBO tests.
- Phase 6 collaboration, staging, proposal, job, credential, and web tests.

## Full Verification Commands

```bash
./gradlew test
./gradlew build
./gradlew check
(cd web-app && npm ci && npm test && npm run build && npm run test:e2e)
(cd vscode-extension && npm ci && npm test)
git diff --check
git status --short
```

A real-provider smoke test is manual and optional because it requires the user's credential and may incur cost. It must not be part of CI, a completion artifact, or committed configuration. Automated acceptance uses the deterministic provider boundary.

## Risks And Assumptions

- Phase 7 implementation assumes the approved base continues to contain the completed Phase 5 and Phase 6 foundations documented by their implementation summaries.
- OpenAI model availability can change over time. Phase 7 pins `gpt-5.2`; changing that allowlist requires a separately approved configuration revision and provider regression run.
- Responses API event shapes can evolve. Provider DTOs must remain isolated behind `OpenAiResponsesClient` and contract-tested.
- The existing Phase 6 development identity does not provide a production session-expiration event. Phase 7 can clear credentials on logout, removal, restart, and any existing expiration hook, but must not claim production session security.
- In-memory state is lost on restart. The UI and help must state this clearly.
- Private SSE streams add per-user transport complexity; incorrect filtering could leak private activity, so Slice 12 treats any leak as a hard stop.
- Existing typed edit families are split across ontology edits, semantic metadata edits, external intents, deletion behavior, and shape authoring. Slice 6 must inventory the approved proposal path rather than infer support from class existence alone.
- Model-callable SHACL mutation is allowed only for operations completed by Slice 6 and marked `APPROVED` in the Slice 7 capability inventory; advanced and raw SHACL mutation remains excluded.
- A non-empty shared staged queue can make AI submission ambiguous. The Phase 7 conservative behavior is an atomic conflict, not automatic merging.
- Provider usage metadata may not expose exact cost. Limits should use available token/request measurements and conservative configured budgets without claiming precise billing.
- Prompt injection cannot be solved by prompting alone. The primary controls are absent forbidden tools, immutable Kotlin scope, strict schemas, per-call authorization, bounded context, and deterministic validation.

## Resolved Implementation Decisions

The following decisions are fixed for Phase 7:

- OpenAI Responses endpoint: `https://api.openai.com/v1/responses`.
- Allowed provider host: `api.openai.com`.
- Initial and only approved model: `gpt-5.2`.
- Moving model aliases are rejected.
- Provider response storage: disabled with `store: false`.
- Provider background mode: disabled.
- Connect timeout: 10 seconds.
- Request timeout: 120 seconds.
- Automatic retry count: at most two retries.
- Retryable failures: connection failure, HTTP 408, HTTP 429, and HTTP 5xx.
- AI SHACL mutation: enabled only for the bounded typed operations proven by Slice 6 and marked `APPROVED` in the Slice 7 capability inventory.
- Typed edit exposure: only entries marked `APPROVED` in the Slice 7 capability inventory.
- Private SSE retention: latest 250 safe events per AI run, in memory for the server lifetime.
- SSE reconnect: resume from retained event ID or require authoritative HTTP resynchronization.

No product or architecture decision remains open before implementation. A later change to any fixed value requires a separately approved revision.

## Rollback Notes

- Each slice must be committed independently and remain revertible without removing prior slices.
- The Phase 6 deterministic provider should remain available as a test fake throughout implementation, so provider slices can be reverted without breaking non-AI workbench tests.
- The OpenAI adapter is isolated behind provider interfaces; reverting Slice 3 removes outbound provider behavior without affecting semantic modules.
- Capability adapters mutate only private in-memory drafts until Slice 10, so Slices 1 through 9 can be reverted without source-file migration.
- The new HTTP boundary is additive. Preserve the Phase 6 route until compatibility or deprecation behavior is verified.
- No database or persistent schema migration is introduced.
- If UI work must be reverted, the versioned server contracts and tests can remain; the existing Phase 6 assistant panel can be restored without semantic rollback.
- Do not roll back by deleting existing validation, proposal, reasoning, SHACL, FIBO, collaboration, application, or rollback behavior.

## Definition Of Done

Phase 7 is complete only when:

- Repository phase status is aligned and the approved base includes Phase 5 and Phase 6.
- All 14 slices are implemented serially with their completion artifacts.
- A real OpenAI Responses API adapter exists behind the provider-neutral Kotlin boundary.
- Credentials remain server-only, per-user, in-memory, redacted, and removable.
- Conversation, run, draft, scope, audit, limit, and response state is Entio-owned and bounded.
- Every provider tool is an allowlisted strict Entio capability with per-call Kotlin authorization and validation.
- Read capabilities cover local ontology, reasoning, SHACL, proposals, FIBO, workflow, permissions, activity, and help.
- Every typed edit marked `APPROVED` in `docs/decisions/phase-7-slice-7-typed-edit-capability-inventory.md`, including the bounded SHACL operations proven by Slice 6, is available through the private draft boundary, with read-only, deferred, and unsupported operations rejected explicitly.
- Draft validation, preview, diff, reasoning, SHACL, and impact use existing deterministic services and matching fingerprints.
- Planning, clarification, cancellation, limits, and bounded self-correction work as specified.
- Explicit user submission creates a normal human-review proposal with AI attribution and no approval/application side effect.
- No AI capability can access arbitrary files, shell, environment, unrestricted network, secrets, configuration, permissions, approval, rejection, application, rollback, immutable FIBO mutation, raw Turtle, raw triples, or arbitrary SHACL/SPARQL mutation.
- React uses only versioned Entio contracts, stores no credential after save, and contains no semantic policy.
- Security, prompt-injection, cross-scope, provider, limit, cancellation, stale/conflict, and failure tests pass.
- Full Gradle, web-app, Playwright, VS Code, and diff checks pass.
- `docs/phase-summaries/phase-7-summary.md` describes actual implemented behavior, deviations, non-goals, and limitations.
- No secrets, generated dependency folders, logs, or mutated committed fixtures are present in the final diff.

## Boundary Check

- The plan fits the explicitly requested Phase 7 scope and is ready after the base-branch and clean-working-tree checks.
- It adds no speculative semantic framework, database, vector store, production identity, unrestricted agent tooling, or durable persistence.
- It preserves dependency direction: `web-server` delegates to existing engine modules, and `web-app` delegates to versioned server contracts.
- It keeps `core-types`, `semantic-engine`, `validation-engine`, `graph-diff`, `cli`, `shared`, and `vscode-extension` unchanged unless a missing approved prerequisite triggers a stop and plan revision.
- Existing RDF, OWL, SHACL, reasoning, FIBO, diff, validation, proposal, application, and rollback implementations remain authoritative.
- Human review is mandatory and model-accessible approval/application tools remain absent.