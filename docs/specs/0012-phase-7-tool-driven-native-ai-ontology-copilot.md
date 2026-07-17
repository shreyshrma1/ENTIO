# Feature Spec: Phase 7 Tool-Driven Native AI Ontology Copilot

## Status

Draft for review. Phase 6 is complete, and Phase 7 is the active planning boundary. This document specifies Phase 7 but does not activate implementation.

## Problem

Entio has a Phase 6 provider-neutral AI boundary, in-memory per-user credential handling, a deterministic development assistant, and a browser assistant panel. That foundation can demonstrate bounded explanations and a small number of typed suggestions, but it does not call a real AI provider, maintain reciprocal conversations, plan multi-edit ontology work, operate a private AI draft, use Entio capabilities through a bounded tool loop, or validate and revise a complete proposed change before human review.

Users need to describe ontology work in natural language and ask follow-up questions without giving a model authority over RDF, project files, validation, permissions, or proposal application. The assistant must be useful enough to inspect local and external ontology context, explain semantic results, prepare all supported typed edits, and compose multi-edit proposals, while preserving Entio's ontology-first and human-review principles.

## Goals

- Connect Entio's existing server-side AI boundary to the OpenAI Responses API.
- Provide reciprocal, project-scoped conversations with bounded application-owned context.
- Expose an explicit Kotlin capability registry rather than giving the model direct service, filesystem, shell, or network access.
- Support explanations grounded in project descriptors, asserted facts, inferred facts, reasoning results, SHACL findings, FIBO descriptors, proposal data, and current workflow metadata.
- Support every ontology edit that has an approved Entio typed-edit and proposal path.
- Keep AI-generated edits in a private, user-and-conversation-scoped draft until the user explicitly submits them for human review.
- Support small direct edit requests and human-confirmed plans for broad ontology-design requests.
- Validate, preview, diff, reason over, SHACL-check, and inspect the impact of AI drafts through existing deterministic Kotlin services.
- Permit bounded self-correction of the current AI draft from structured Entio findings without weakening policy.
- Preserve human authority over proposal approval, rejection, application, and rollback.
- Make inspected evidence, tool activity, draft revisions, uncertainty, limits, and failures visible to the user.
- Preserve all Phase 1 through Phase 6 engine, CLI, VS Code, web, FIBO, reasoning, SHACL, collaboration, proposal, and rollback behavior.

## Non-Goals

Phase 7 does not include:

- Autonomous approval, rejection, application, or rollback.
- Background AI changes without an active user request.
- Direct Turtle generation, arbitrary RDF triples, SPARQL updates, or direct source-file writes.
- Editing `entio.yaml`, the project registry, source configuration, permissions, server configuration, model policy, or the FIBO package.
- Shell execution, arbitrary code execution, filesystem browsing, environment-variable access, or unrestricted network access.
- OpenAI built-in web search, file search, code interpreter, computer use, or remote MCP tools.
- Durable conversation, AI draft, audit, or credential storage.
- Production authentication, organization administration, billing controls, or shared organization-funded credentials.
- Multiple production AI providers beyond retaining a provider-neutral internal boundary and adding the initial OpenAI adapter.
- Embeddings, a vector database, a second schema index, document ingestion, or document-driven entity resolution.
- Fine-tuning or autonomous multi-agent orchestration.
- A second ontology engine or model-owned RDF, OWL, SHACL, FIBO, validation, reasoning, or diff logic.
- Full AI SHACL authoring unless an approved typed SHACL mutation can already enter the normal proposal workflow. Narrative SHACL guidance remains allowed.
- Full Protégé parity or arbitrary OWL class-expression authoring.

Current phase-level architecture and historical non-goals remain governed by `AGENTS.md`.

## Existing Foundation

Phase 7 builds additively on the actual repository state:

- `web-server` exposes Ktor HTTP and WebSocket adapters, development identity, project allowlisting, shared staging, proposal workflow, collaboration, semantic jobs, FIBO services, and server-memory AI credentials.
- `web-server` defines a provider-neutral `AiProviderClient`, `AiAssistantProvider`, bounded context DTOs, a deterministic development provider, and a limited `AiAssistantService`.
- `web-app` contains the React workbench, provider credential settings, an assistant panel, transport clients, queries, collaboration behavior, and workflow tests.
- `core-types`, `semantic-engine`, `validation-engine`, and `graph-diff` remain authoritative for semantic contracts and behavior.
- Existing typed ontology edits, semantic metadata edits, external reuse intents, proposal services, reasoning, SHACL validation, and FIBO search must be reused.

Phase 7 replaces the deterministic assistant's production role. The deterministic provider remains available only as a test fake or explicitly selected local development adapter.

## Fixed Architecture Decisions

### Kotlin remains authoritative

The model may request a named capability, but Kotlin decides:

- which capabilities are available;
- whether the user, project, source, and workflow state permit the call;
- whether tool arguments are structurally and semantically valid;
- which existing Entio service performs the operation;
- whether a typed edit may enter the private AI draft;
- whether validation, reasoning, SHACL, impact, and baseline checks pass;
- whether a draft may be submitted for human review.

React renders state and sends user intent. It must not reproduce capability policy, translate raw model output into RDF, or decide semantic validity.

### No new semantic module

Phase 7 should extend the existing `web-server` AI/session boundary and `web-app` assistant surface. It should not add an `ai-engine` Gradle module. Reusable semantic work continues to live in the existing engine modules; provider, conversation, run, draft, and web-session orchestration belongs in `web-server`.

### OpenAI integration

The initial production adapter uses the OpenAI Responses API from the Kotlin server. It uses custom function tools generated from Entio's capability registry and strict JSON schemas. The React application never calls OpenAI.

The adapter must:

- use a server-configured, explicit model identifier rather than an unqualified `latest` alias;
- set provider storage behavior deliberately and keep Entio's session state authoritative;
- support provider request IDs, response IDs, usage metadata, timeouts, cancellation, and safe retry classification;
- stream user-visible narrative and run events where practical;
- redact credentials, authorization headers, and unsafe provider error details;
- never enable provider tools outside the supplied Entio custom functions.

### Human review remains authoritative

The model receives no capability for approval, rejection, application, or rollback. Those actions remain existing human-controlled Entio operations. An assistant response cannot itself change a source file.

## Proposed Behavior

### 1. Credential and availability behavior

A user configures their personal OpenAI API key through the existing settings surface. The credential:

- travels only from the browser to the Kotlin server over the configured connection;
- is stored only in server memory for that user session;
- is never returned to the browser after submission;
- is removed on explicit deletion, logout, session expiration when available, or server restart;
- is never placed in URLs, logs, collaboration events, proposals, ontology files, snapshots, audit records, or another user's request.

The provider ID for the initial adapter is a stable server-owned value such as `openai`; users must not enter arbitrary provider implementations. Non-AI Entio features remain available when no key is configured or the provider is unavailable.

### 2. Conversation lifecycle

The user can create a project-scoped conversation, send messages, answer clarification questions, inspect prior messages, cancel an active run, and continue follow-up discussion.

Each conversation is owned by one user and one project. It records bounded session-memory state:

- conversation ID;
- user and assistant messages;
- operation types;
- selected project, entity, source, proposal, reasoning, and SHACL context;
- tool-call summaries and Entio result references;
- current private AI draft ID;
- model and prompt version;
- OpenAI response IDs where retained;
- timestamps and available token/cost metadata.

Switching projects must not carry private draft or ontology context into the new project. Conversation state is lost on server restart and is not presented as durable history.

### 3. Server-created AI scope

Every run receives an immutable `AiCapabilityScope` constructed by Kotlin from the authenticated/development user, registered project, current conversation, allowed source IDs, baseline fingerprint, collaboration session, role, permissions, and feature availability.

The model cannot alter or expand this scope. Every capability call revalidates it. A valid call in one turn does not create permanent authority for a later turn.

### 4. Capability registry

The server exposes only capabilities allowed for the current run. Every capability definition includes:

- stable name and operation type;
- concise model-facing description;
- strict input JSON schema with `additionalProperties: false`;
- explicit required and nullable fields;
- bounded arrays and stable enums;
- read-only or private-draft mutation classification;
- required user role and project feature;
- project and source scope rules;
- result-size and timeout limits;
- audit classification;
- confirmation requirement.

The registry must not include capabilities for filesystem access, shell execution, raw Turtle, raw SPARQL, arbitrary network calls, secrets, project configuration, permissions, approval, rejection, application, or rollback.

### 5. Read and explain capabilities

The assistant may inspect only structured Entio outputs. Required read behavior includes:

- project summaries;
- entity descriptors and comparisons;
- hierarchy neighborhoods and entity usage;
- deterministic local semantic search;
- asserted and inferred facts;
- reasoning status, consistency, unsatisfiable classes, and explanations;
- SHACL findings and shape descriptors;
- staged changes, proposal summaries, validation reports, semantic diffs, and proposal impact;
- deterministic FIBO search, descriptors, modules, and dependencies;
- recent activity visible to the current user;
- current screen context, available actions, permissions, workflow state, and versioned Entio help.

Responses must distinguish asserted, inferred, external, staged, AI-generated, stale, conflicted, incomplete, and unsupported information.

### 6. Typed edit capabilities

The assistant may prepare only edits backed by approved Kotlin contracts and the normal proposal workflow. The capability set must cover the existing supported boundaries for:

- classes;
- object, datatype, and annotation properties;
- individuals and type assertions;
- superclasses;
- property domains and ranges;
- object and datatype assertions;
- preferred labels, definitions, alternate labels, and explicit annotations;
- supported entity deletion with dependency selection;
- FIBO/external reuse and local subclassing of an external class.

Tool adapters must call the same translators and validators used by non-AI workflows. They must preserve original external IRIs and must not fall back to raw triples when an operation is unsupported.

SHACL mutation tools are exposed only if the implementation audit proves that the relevant typed shape edits can traverse the approved proposal, review, application, reload, and rollback path. Otherwise the assistant may explain or suggest a conceptual constraint and must report that no approved mutation capability exists.

### 7. Private AI draft workspace

The assistant works in a private server-owned `AiDraft`, not the shared staged queue. A draft includes:

- draft, project, conversation, and user IDs;
- baseline fingerprint and allowed source IDs;
- ordered typed draft items;
- per-item rationale and dependencies;
- revision history;
- validation and conflict state;
- preview, semantic diff, reasoning, SHACL, and impact references;
- created and updated timestamps.

The user can ask the assistant to add, update, remove, reorder, undo, or clear draft items. All mutations affect only the current user's current conversation draft. The draft becomes stale when its baseline changes.

### 8. Planning and clarification

For a focused request with unambiguous targets, the assistant may prepare a draft directly.

For a broad request, the assistant first produces a human-readable plan containing intended concepts, external reuse, local additions, source targets, constraints, likely reasoning effects, open decisions, and estimated edit count. The user may confirm, revise, answer questions, or cancel. Plan confirmation permits draft preparation but is not proposal approval.

The assistant asks a clarification when ambiguity materially changes concept identity, source, local versus external reuse, property direction, domain/range, datatype, deletion impact, FIBO dependencies, or whether a concept is a class, role, property, or individual.

### 9. Deterministic draft analysis

The assistant can request analysis of its current draft. The server performs the complete existing sequence:

```text
typed-edit validation
-> in-memory preview
-> semantic diff
-> deterministic proposal validation
-> OWL reasoning
-> SHACL validation
-> proposal impact
```

Results remain structured Entio facts. The model may explain them but cannot override blocking findings or claim success before the sequence completes.

### 10. Bounded self-correction

The assistant may revise its own private draft after structured failures such as a missing reference, duplicate entity, unsupported datatype, stale baseline, validation issue, introduced inconsistency, SHACL violation, missing FIBO dependency, or invalid deletion selection.

Self-correction:

- changes only the current AI draft;
- records a revision and explanation;
- preserves the original finding;
- reruns the required deterministic checks;
- stops after the configured correction limit;
- never removes or weakens validation policy.

### 11. Submission for human review

The assistant submits a draft only after an explicit user request. Submission must atomically:

1. recheck user, project, source, and baseline scope;
2. verify the draft is current and complete;
3. rerun required deterministic analysis when cached results do not match the current draft fingerprint;
4. translate draft items through existing typed-edit adapters;
5. create an ordinary Entio review proposal through the existing shared workflow;
6. preserve AI attribution, accepting user, conversation/run reference, rationale, analysis references, and semantic diff;
7. lock or version the submitted draft;
8. return the proposal ID and review state.

If the existing shared staged queue cannot safely accept the draft as an isolated proposal, submission fails with a structured conflict rather than silently mixing changes. The model cannot approve, reject, apply, or roll back the returned proposal.

### 12. Collaboration behavior

Private conversations, model narrative, and private drafts are not broadcast. After submission, ordinary collaboration rules apply to the proposal and submitted items. Other users see only approved shared metadata, including the AI-generated marker, submitting user, rationale, and run reference.

Another user's staged or applied change may make a private draft stale. A stale draft must be refreshed, revised, and revalidated before submission.

### 13. Entio help behavior

The assistant may answer application questions from versioned, server-bundled Entio help plus current feature, screen, workflow, and permission metadata. Help content covers navigation, entity types, editing, staging, proposals, reasoning, asserted versus inferred facts, SHACL, FIBO, collaboration, conflicts, permissions, credentials, AI limits, and error codes.

The help capability must not claim unavailable actions and must not create an ontology edit unless the user asks for one.

### 14. Assistant user experience

The existing assistant side panel becomes a conversational workspace showing:

- current project and selected-entity context;
- credential and run status;
- conversation messages;
- clarification and plan-confirmation states;
- tool activity summaries without hidden reasoning or secrets;
- evidence and provenance;
- private draft items and revision history;
- validation, reasoning, SHACL, diff, and impact summaries;
- uncertainty, warnings, limit states, stale/conflict states, and failures;
- stop/cancel, revise, validate, preview, and submit-for-review actions;
- a link to the authoritative proposal review after submission.

Labels remain primary. IRIs, raw RDF details, provider IDs, and fingerprints use progressive disclosure.

## Inputs

### User inputs

- Personal OpenAI API key submitted through settings.
- Natural-language user messages.
- Explicit clarification answers and plan decisions.
- Explicit requests to validate, revise, cancel, or submit a draft.
- Current browser project, entity, source, proposal, reasoning, SHACL, FIBO, and workflow context.

### Server inputs

- Registered project ID and development/authenticated user identity.
- Current permission and collaboration-session metadata.
- Allowed ontology and SHACL source IDs.
- Current applied graph and baseline fingerprints.
- Structured outputs from existing Entio semantic, validation, diff, proposal, reasoning, SHACL, FIBO, staging, and collaboration services.
- Versioned Entio help resources.
- Server configuration for explicit OpenAI model identifier, prompt version, provider endpoint, timeouts, tool limits, draft limits, correction limits, context limits, and cost/token limits where measurable.

### Provider inputs

OpenAI receives only:

- trusted Entio developer policy;
- the current user request;
- bounded prior conversation content;
- strict allowed tool definitions for the current scope;
- structured, relevant Entio context;
- clearly delimited untrusted ontology and external text.

It must not receive the API key as message content, arbitrary files, full source files by default, unrelated projects, another user's data, or unbounded logs.

## Outputs

### Conversation output

An assistant response separates:

- answer;
- operation type;
- evidence and provenance;
- asserted facts;
- inferred facts;
- SHACL findings;
- FIBO results;
- draft summary;
- typed suggestions;
- uncertainty;
- warnings;
- reached limits;
- available next actions.

### Draft output

The private draft API returns stable IDs, ordered typed items, rationales, revision metadata, baseline and draft fingerprints, allowed source IDs, analysis status/references, stale/conflict state, and submission eligibility. It never returns secrets or provider authorization data.

### Review output

Successful submission returns an existing Entio proposal ID, review state, semantic diff and analysis references, AI attribution, and a review URL or client route. It does not return an approved or applied state.

### Audit output

Each run creates a session-memory audit record containing run, conversation, user, project, model, prompt version, allowed capabilities, tool calls/outcomes, draft revisions, result references, status, timestamps, and available usage metadata. Audit records exclude secrets and unrelated content.

## Core Contracts

Phase 7 should introduce or evolve server-owned contracts with responsibilities equivalent to:

- `AiConversation`, `AiConversationMessage`, and `AiConversationService`.
- `AiRun`, `AiRunStatus`, `AiRunPolicy`, and `AiAuditRecord`.
- `AiCapabilityRegistry`, `AiCapabilityDefinition`, `AiCapabilityScope`, `AiCapabilityInvocation`, and `AiCapabilityResult`.
- `AiContextPackage` and `AiContextBuilder`.
- `AiDraft`, `AiDraftItem`, `AiDraftRevision`, `AiDraftWorkspace`, and `AiDraftStatus`.
- `AiDraftValidator`, `AiDraftPreviewService`, and `AiDraftSubmissionService`.
- `AiProvider`, `AiProviderRequest`, `AiProviderEvent`, `AiProviderResponse`, and `OpenAiResponsesClient`.
- `AiResponse`, `AiEvidence`, `AiWarning`, `AiLimit`, and `AiNextAction`.
- `EntioHelpService` and versioned help entries.

These are web/session orchestration contracts and should remain in `web-server` unless a contract must be shared with an existing Kotlin semantic service. Provider DTOs must not leak into `core-types` or semantic modules.

## Web Contracts

The versioned API should cover:

```text
POST   /api/v1/projects/{projectId}/ai/conversations
GET    /api/v1/projects/{projectId}/ai/conversations/{conversationId}
DELETE /api/v1/projects/{projectId}/ai/conversations/{conversationId}
POST   /api/v1/projects/{projectId}/ai/conversations/{conversationId}/messages
GET    /api/v1/projects/{projectId}/ai/runs/{runId}
POST   /api/v1/projects/{projectId}/ai/runs/{runId}/cancel
GET    /api/v1/projects/{projectId}/ai/runs/{runId}/events
GET    /api/v1/projects/{projectId}/ai/drafts/{draftId}
POST   /api/v1/projects/{projectId}/ai/drafts/{draftId}/validate
POST   /api/v1/projects/{projectId}/ai/drafts/{draftId}/preview
POST   /api/v1/projects/{projectId}/ai/drafts/{draftId}/reasoning
POST   /api/v1/projects/{projectId}/ai/drafts/{draftId}/shacl
POST   /api/v1/projects/{projectId}/ai/drafts/{draftId}/impact
POST   /api/v1/projects/{projectId}/ai/drafts/{draftId}/submit
GET    /api/v1/projects/{projectId}/ai/help
```

Exact route consolidation is allowed, but the contracts must remain explicit, project-scoped, user-scoped, versioned, and testable. User-visible streaming should use a private authenticated event stream rather than broadcasting private AI events over project collaboration channels.

Event types include run start/status, safe text delta, capability requested/started/completed, draft updated, analysis completed, clarification requested, plan confirmation requested, ready for review, failure, cancellation, limit reached, and stale state. Raw hidden reasoning, credentials, authorization headers, and unrestricted provider payloads are never streamed.

## Validation Behavior

### Capability validation

- Reject a capability not present in the current registry snapshot.
- Reject unknown fields, missing required fields, invalid enums, oversized arrays, invalid IRIs, and arguments outside configured bounds.
- Revalidate user, project, source, feature, role, baseline, and draft ownership for every call.
- Reject read calls that request unrelated projects or private state.
- Reject draft mutations not backed by approved typed Entio contracts.

### Draft validation

- Validate item structure when added or revised.
- Resolve references through deterministic Entio services.
- Validate source mutability and source role.
- Detect duplicate and conflicting items.
- Validate deletion dependencies explicitly.
- Mark the draft stale when its baseline no longer matches.
- Require a current preview and complete deterministic analysis before submission.

### Provider validation

- Refuse an absent or unsupported provider configuration.
- Refuse a blank model identifier or an unapproved moving alias.
- Parse provider responses defensively.
- Accept only known custom function calls with valid IDs and arguments.
- Reject malformed, duplicate, replayed, or over-limit tool calls.
- Treat provider text and tool arguments as untrusted input.

### Human-review validation

- Confirm the submitting user may stage and create a proposal.
- Confirm reviewer authority remains separate.
- Confirm draft fingerprint, baseline, validation, reasoning, SHACL, diff, and impact references correspond to the submitted items.
- Never infer approval from plan confirmation, draft submission, provider text, or a model tool call.

## Error Behavior

AI run states include:

- `QUEUED`
- `RUNNING`
- `AWAITING_CLARIFICATION`
- `AWAITING_PLAN_CONFIRMATION`
- `CALLING_TOOL`
- `VALIDATING_DRAFT`
- `RUNNING_REASONING`
- `RUNNING_SHACL`
- `REVISING_DRAFT`
- `READY_FOR_REVIEW`
- `FAILED`
- `CANCELLED`
- `LIMIT_REACHED`
- `STALE`

Structured failures include missing/invalid credential, provider unavailable, rate limit, timeout, malformed response, malformed tool request, unknown/unauthorized capability, project/source scope violation, cross-user access, stale baseline, validation failure, reasoning incomplete, SHACL blocked, unsupported operation, project-configuration request, context/tool/edit/correction/time limit, concurrent draft mutation, and review submission conflict.

Failures must:

- preserve the current private draft when safe;
- stop further tool execution when the state is terminal;
- present a safe user-facing explanation and retry/next action when applicable;
- retain structured internal diagnostics without secrets;
- leave non-AI Entio functionality available;
- never report a proposal as submitted, approved, or applied when the corresponding Kotlin operation failed.

## Security And Prompt-Injection Behavior

Ontology labels, definitions, annotations, comments, literal values, source text, FIBO metadata, help text, proposal comments, tool results, and provider output are untrusted data.

Trusted policy, user messages, tool schemas, and untrusted context must be structurally separated. Untrusted content cannot grant capabilities, change permissions, request secrets, modify validation policy, redefine tool behavior, expand source scope, trigger unrelated data access, or bypass review.

The provider request supplies only the tool definitions authorized for that run. The provider has no path to retrieve credentials, arbitrary files, environment variables, or another user's state.

## Limits

Initial defaults are server-configurable and enforced by Kotlin. The implementation should begin with the scope recommendations:

- at most 20 capability calls per user turn;
- at most 50 draft edits per run;
- at most 3 automated self-correction cycles;
- at most 1 active AI run per user and project;
- at most 20 local entities in context unless explicitly expanded within policy;
- at most 10 FIBO candidates per search step;
- bounded capability result sizes;
- bounded OpenAI requests, elapsed time, output, and estimated token/cost usage where measurable;
- no recursive AI invocation;
- no parallel mutation of the same private draft.

Reaching a limit stops safely, preserves the draft, records the limit, and allows continuation in a later turn.

## Test Cases

### Provider and credentials

- A valid user credential is stored only in server memory and is never returned.
- Missing, invalid, revoked, rate-limited, and timed-out credentials produce AI-unavailable states without breaking non-AI routes.
- Provider headers and errors are redacted.
- One user's credential cannot serve another user's request.
- Server restart, logout, expiration when supported, and explicit removal clear the credential.
- A deterministic fake provider drives all automated tests without external network calls.

### Capability registry and scope

- The model receives only the capabilities allowed for the current user, project, source, feature, and workflow state.
- Forbidden capability names do not exist.
- Strict schemas accept valid inputs and reject unknown fields, missing fields, invalid enums, invalid IRIs, and oversized arrays.
- Cross-user, cross-project, out-of-source, immutable FIBO, project-configuration, and permission-changing calls are rejected.
- A capability removed between calls cannot be invoked from stale model output.

### Read and explanation behavior

- Entity, inference, consistency, unsatisfiable-class, SHACL, proposal, diff, validation, FIBO, activity, permission, workflow, and error explanations cite structured Entio evidence.
- Asserted and inferred facts remain distinct.
- Unsupported or unavailable information is described as such rather than invented.
- Context building sends relevant bounded data and omits unrelated projects, full source files, credentials, and private drafts.

### Draft workflow

- A focused request creates a private valid draft item.
- A broad request produces a plan before draft mutation.
- Clarification pauses the run and a follow-up answer resumes it.
- Every approved typed edit adapter can add a draft item.
- Draft items can be updated, removed, reordered, undone, and cleared.
- Two users cannot read or mutate each other's drafts.
- A baseline change marks the draft stale.
- A stale or incomplete draft cannot be submitted.

### Deterministic analysis and correction

- Draft validation, preview, diff, reasoning, SHACL, and impact reuse existing Kotlin services.
- Blocking findings cannot be overridden by model text.
- Self-correction changes only the current draft and records each revision.
- Correction stops after the configured limit.
- The final analysis fingerprints match the submitted draft.

### Human authority

- Submission requires an explicit user action.
- Submission creates a normal human-review proposal without approving or applying it.
- AI attribution and rationale survive submission.
- AI has no approve, reject, apply, or rollback capability.
- Existing reviewer permissions and atomic apply/rollback behavior remain authoritative.

### Conversation and streaming

- Follow-up questions retain bounded relevant context.
- Project switching does not leak conversation state.
- Cancellation terminates provider and tool work and reports `CANCELLED`.
- Reconnection retrieves authoritative run and draft state without replaying a mutation.
- Stream ordering is deterministic and duplicate events do not duplicate draft changes.

### Prompt injection

- Malicious labels, definitions, annotations, literal values, FIBO metadata, help text, and tool output cannot grant tools or alter policy.
- User text cannot add forbidden capabilities.
- Prompt-injection content cannot retrieve credentials or unrelated project data.
- Provider output requesting a raw file, shell, apply, or permission tool is rejected because no such capability exists.

### Limits and failures

- Tool-call, draft-edit, correction, context, request, timeout, and cost/token limits stop safely.
- Concurrent draft mutation is rejected deterministically.
- Provider failure preserves the draft and never reports success.
- A shared staging conflict blocks submission without partial import.

### User experience

- The assistant panel renders loading, empty, clarification, planning, running, tool activity, draft, stale, conflict, limit, error, cancelled, and ready-for-review states.
- Keyboard, focus, screen-reader, and reduced-motion behavior remain accessible.
- Labels are primary and IRIs/raw details are progressively disclosed.
- Private AI events are not shown to another collaborator.

### Regression

- All existing Phase 1 through Phase 6 Kotlin, CLI, VS Code, web-server, web-app, collaboration, FIBO, reasoning, SHACL, proposal, application, reload, and rollback tests pass.

## Acceptance Criteria

Phase 7 is accepted when:

- A user can configure a personal OpenAI API key and run a real server-side OpenAI conversation.
- Entio owns conversation, run, draft, scope, and audit state in server memory.
- The OpenAI request receives only strict, currently authorized Entio custom function tools.
- The assistant explains ontology, reasoning, SHACL, FIBO, proposal, workflow, permission, and help context using cited structured evidence.
- The assistant can prepare every edit supported by the approved typed-edit/proposal boundary without raw RDF fallback.
- Focused requests can create draft items and broad requests require a human-readable plan.
- The user can revise a private multi-edit draft through follow-up conversation.
- Draft validation, preview, semantic diff, reasoning, SHACL, and impact use existing deterministic Kotlin services.
- Bounded self-correction records revisions and cannot weaken validation.
- Explicit submission creates a normal human-review proposal with AI attribution and does not approve or apply it.
- No model-accessible capability can approve, reject, apply, roll back, edit project configuration, access arbitrary files/shell/network, retrieve secrets, or mutate immutable FIBO assets.
- Prompt-injection, cross-user, cross-project, source-scope, schema, limit, cancellation, provider, and stale-baseline tests pass.
- The React client contains no OpenAI credential after submission and no semantic decision logic.
- Non-AI Entio behavior works with no credential configured.
- All Phase 1 through Phase 6 regression suites remain green.

## Open Questions

- Which explicit OpenAI model identifier or approved fixed allowlist will the deployment use? Phase 7 must reject an unapproved moving alias and must not hard-code an obsolete model in product contracts.
- Should provider-side response storage be disabled for all Phase 7 requests, or allowed under a documented retention policy? Entio remains the authoritative conversation store either way.
- What exact elapsed-time, request, output-token, and estimated-cost defaults should production configuration use beyond the fixed tool/edit/correction/context limits?
- How long should completed private run events remain available for stream reconnection within the in-memory session?
- Does the approved base expose a complete SHACL typed-mutation-to-proposal path? If not, Phase 7 will remain explanation-only for SHACL mutation.
- When a shared staged queue is non-empty, should AI submission always fail with a conflict in Phase 7, or may an explicit future merge operation combine compatible items? The conservative Phase 7 default is to fail without partial mutation.
- Phase 6 uses development identity rather than production sessions. Which existing lifecycle event should represent session expiration until production authentication exists?

## Boundary Check

- Phase 6 is complete, and Phase 7 is the active planning boundary.
- The spec adds no custom RDF, OWL, SHACL, Turtle, reasoning, validation, diff, FIBO, or proposal implementation.
- The spec adds no vector database, document ingestion, arbitrary agent tools, durable storage, production identity, or unrestricted network capability.
- Kotlin remains the semantic and authorization authority; React remains a client.
- Existing human review, deterministic validation, application, and rollback boundaries remain mandatory.
- Phase 7 implementation must stop if the approved base does not contain the Phase 5 and Phase 6 prerequisites described here.
