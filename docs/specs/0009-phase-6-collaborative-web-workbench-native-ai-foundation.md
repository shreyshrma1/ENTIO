# Feature Spec: Phase 6 Collaborative Web Workbench And Native AI Foundation

## Status

Draft

Phase 6 is a planning boundary. Writing this specification does not activate Phase 6 implementation or change the current repository phase by itself.

## Problem

Entio currently provides its semantic engine through Kotlin/JVM modules, a CLI, and a VS Code ontology workbench. These clients support controlled ontology editing, semantic descriptions, OWL reasoning, SHACL validation, and FIBO browsing, but they do not provide a shared browser workspace for multiple users.

Users need a modern, accessible workbench that combines ontology exploration, editing, proposal review, reasoning, SHACL results, FIBO reuse, and optional AI assistance without creating a second semantic implementation. Multiple users also need to see shared activity and staged changes while preserving Entio's baseline checks and human approval workflow.

Phase 6 must be strictly additive. The browser application is another frontend over the existing Entio services. It does not replace, deprecate, weaken, or require changes to the VS Code extension, CLI, or existing backend behavior beyond approved additive and backward-compatible contracts.

## Goals

- Add a React/TypeScript browser application as an additional Entio frontend.
- Add a Kotlin/Ktor application server that adapts existing reusable Entio services.
- Preserve the VS Code extension, CLI, Kotlin modules, and existing Phase 1 through Phase 5 behavior.
- Provide an expandable, lazy-loaded ontology hierarchy and label-first entity navigation.
- Open classes, properties, individuals, shapes, reasoning results, SHACL results, FIBO entries, proposals, and AI conversations in workbench tabs.
- Preserve staged edits, combined previews, semantic diffs, reasoning impact, SHACL impact, approval, rejection, application, reload, and rollback.
- Support at least two connected browser sessions with presence, shared staged-change updates, activity visibility, and baseline-conflict reporting.
- Use one server-authoritative shared staged set per project collaboration session.
- Run reasoning and SHACL validation as asynchronous server-managed jobs while users continue browsing and editing.
- Expose the Phase 5 FIBO browsing and deterministic schema-search capabilities in the browser.
- Complete the reusable external-intent-to-shared-staged-change boundary needed for end-to-end FIBO reuse in the browser.
- Provide optional native AI assistance through a provider-neutral Kotlin boundary, initially using each user's own OpenAI API key.
- Ensure AI suggestions use approved typed operations, enter the normal staged workflow, and never bypass deterministic validation or human approval.
- Keep ontology projects filesystem-backed and existing backend services directly usable without starting the web application.

## Non-Goals

- Rewriting the Kotlin semantic engine in TypeScript or Node.js.
- Replacing Apache Jena, OWL API, HermiT, Jena SHACL, the CLI, or the VS Code extension.
- Changing existing CLI commands, machine-readable responses, or backend behavior except through additive, backward-compatible contracts.
- Making the web application a dependency of the CLI, VS Code extension, or semantic engine.
- Full production authentication, enterprise authorization, multi-organization tenancy, or durable audit retention.
- Raw simultaneous Turtle editing, offline collaboration, peer-to-peer synchronization, RDF graph CRDTs, or automatic ontology conflict merging.
- Yjs or another CRDT framework in the initial Phase 6 implementation.
- Full Protégé parity, arbitrary OWL class-expression editing, or complete SHACL authoring.
- Creating, editing, or deleting SHACL shapes from the browser unless an approved typed SHACL mutation contract already exists.
- A graph database migration, a new FIBO retrieval system, or support for additional external ontology sources.
- Autonomous AI application of ontology changes, AI bypass of validation or approval, or uncontrolled source-file access by AI.
- AI-generated SHACL mutations unless a supported typed SHACL edit contract is separately approved.
- Bundling, sharing, or funding an Entio-wide OpenAI API key.
- Requiring an OpenAI API key for non-AI functionality.
- Mobile-first support, full Git integration, or Git operations inside Entio.
- Durable project, proposal, collaboration, comment, or workspace persistence unless required by an approved later phase.

Current repository and phase-level non-goals remain defined by `AGENTS.md`.

## Approved Repository Layout

Phase 6 should add two isolated product areas:

```text
web-server/
  build.gradle.kts
  src/main/kotlin/
  src/test/kotlin/

web-app/
  package.json
  vite.config.ts
  src/
  tests/
```

`web-server` is a new Gradle module.

It may depend on:

- `core-types`
- `semantic-engine`
- `validation-engine`
- `graph-diff`
- `shared` only for genuinely generic utilities

It must not depend on `cli`.

`web-app` is a separate React/TypeScript workspace and is not a Gradle module.

The client relationships are:

```text
React web application
        |
        | HTTP and WebSocket
        v
Kotlin/Ktor web-server
        |
        v
Existing reusable Entio Kotlin services

VS Code extension
        |
        | Existing CLI JSON boundary
        v
Kotlin CLI
        |
        v
The same reusable Entio Kotlin services
```

The CLI and Ktor server call reusable services independently. Web route handlers must not become the home of ontology semantics.

## Approved Frontend Stack

Use:

- React
- TypeScript
- Vite
- React Router
- TanStack Query for server-owned data and cache invalidation
- local React state or a small Zustand store for open tabs, form drafts, layout, and client preferences
- accessible component primitives such as Radix UI
- Vitest
- React Testing Library
- Playwright for primary browser journeys

WebSocket messages should notify the client that authoritative server state has changed. TanStack Query should refetch or update the corresponding server-owned data.

The browser must not maintain a second independent proposal, reasoning, SHACL, FIBO, or ontology state machine.

## Approved Backend Stack

Use Kotlin/JVM and Ktor.

The Ktor server must:

- call existing reusable Entio services;
- expose Entio-owned HTTP and WebSocket DTOs;
- keep RDF, OWL, SHACL, reasoning, search, proposal, and persistence policy outside route handlers;
- remain removable without affecting the CLI or VS Code extension;
- expose only server-approved projects rather than arbitrary filesystem paths;
- enforce user capabilities and final application permissions;
- manage collaboration sessions and asynchronous jobs in memory for Phase 6.

## Project Registry And Filesystem Boundary

Browser clients must never submit arbitrary project-root filesystem paths.

The server owns a project registry:

```text
projectId -> approved normalized filesystem project root
```

The registry may come from development configuration for Phase 6.

The server must:

- expose only configured projects;
- identify projects by stable `projectId`;
- normalize and validate every registered root;
- reject path traversal;
- never expose filesystem browsing;
- reuse existing Entio source-resolution protections inside each project;
- exclude unregistered projects from all HTTP, WebSocket, collaboration, reasoning, and AI operations.

## State Ownership And Lifetime

Phase 6 uses these state boundaries:

| State | Owner | Lifetime |
|---|---|---|
| Ontology and SHACL source files | Existing Kotlin project storage | Durable |
| FIBO package | Existing immutable Phase 5 package | Durable and read-only |
| Registered project mapping | Ktor server configuration | Server lifetime |
| Connected users | Collaboration session service | In memory |
| Entity presence/activity | Collaboration session service | In memory |
| Open tabs and layout | Browser client | Browser session |
| Local unsubmitted form drafts | Browser client | Browser session |
| Shared staged changes | Project collaboration session | In memory |
| Proposal preview | Project collaboration session using Kotlin services | Until invalidated, removed, applied, or session ends |
| Reasoning jobs | Semantic job manager | In memory |
| SHACL jobs | Semantic job manager | In memory |
| Latest valid semantic job results | Semantic job manager | Server/session lifetime |
| OpenAI API key | Server-side user session secret store | User session only |
| Applied changes | Existing Kotlin application services | Durable in project source files |

The interfaces for project storage, collaboration state, proposal state, semantic jobs, user identity, and secrets must remain separate so later persistence can replace the in-memory implementations.

## Identity And Permissions

Phase 6 should use predefined development users or a simple development login.

Required roles:

- `CONTRIBUTOR`
- `REVIEWER`

Server-enforced capabilities:

| Action | Contributor | Reviewer |
|---|---:|---:|
| Browse projects and entities | Yes | Yes |
| Prepare edits | Yes | Yes |
| Stage edits | Yes | Yes |
| Remove own staged edit | Yes | Yes |
| Remove another user's staged edit | No | Yes |
| Preview combined proposal | Yes | Yes |
| Review proposal impact | Yes | Yes |
| Approve or reject | No | Yes |
| Apply or roll back | No | Yes |
| Cancel project-wide semantic job | No | Yes |

The browser must not be trusted to enforce permissions.

## Proposed Behavior

### Architecture And Compatibility

The browser is a new client of Entio-owned Kotlin capabilities. Existing backend services remain directly testable without starting Ktor.

All Phase 1 through Phase 5 regression suites must continue to pass. The web application must be removable or disabled without breaking existing clients.

A Phase 6 implementation must stop if it requires removing, renaming, weakening, or incompatibly changing an existing public contract or workflow.

### Web Workbench Shell

The application should provide a persistent desktop-oriented shell with:

- Product and project identity.
- Current applied graph fingerprint and shared staged-change count.
- Connected-user presence.
- Reasoning and SHACL status.
- AI assistant and AI credential status.
- User/session controls.

The left navigation should provide:

- project sources;
- expandable class hierarchy;
- classes;
- object properties;
- datatype properties;
- annotation properties;
- individuals;
- SHACL shapes;
- FIBO access.

Labels are primary; technical IRIs are progressively disclosed.

### Hierarchy Behavior

The hierarchy must:

- load children lazily;
- return stable ordering;
- expose child counts or continuation metadata;
- distinguish asserted and inferred hierarchy links;
- support server-side search/filtering;
- cancel or ignore obsolete requests;
- avoid returning the full hierarchy at project load;
- open entities in tabs;
- show local, external, modified, staged, invalid, and stale status where applicable.

### Main Workspace Tabs

Tabs should support:

- open;
- close;
- reorder;
- preserve local unsaved form state for the browser session;
- indicate staged, conflicting, modified, external, invalid, or stale state;
- open related entities in a new tab.

Supported tab types include:

- project overview;
- entity details;
- class hierarchy;
- property details;
- individual details;
- SHACL shape inspection;
- reasoning results;
- SHACL validation results;
- FIBO browser/search;
- proposal review;
- AI assistant conversation.

### Entity Details

Common entity information includes:

- preferred label;
- technical IRI;
- definitions;
- alternate labels;
- annotations;
- asserted source attribution;
- local or external origin;
- asserted relationships;
- inferred relationships and inference origin;
- supporting asserted facts where available;
- reasoning status;
- SHACL findings;
- edit and deletion actions through existing workflows.

Class views should include parents, inferred parents, subclasses, related properties, and related individuals. Property views should include domains, ranges, and assertions. Individual views should include types and property values.

SHACL shape views are inspection-focused in Phase 6. They should display targets, direct paths, constraints, severities, messages, and validation findings. Browser create/edit/delete shape actions are not included unless the backend already exposes an approved typed SHACL mutation contract.

### Editing And Proposal Workflow

Human and AI edits follow the existing controlled flow:

```text
prepare typed edit
→ validate
→ stage in the shared project session
→ build combined preview
→ show semantic diff and proposal impact
→ review collaborators and conflicts
→ approve or reject
→ apply through Kotlin
→ reload
→ rerun reasoning and SHACL
→ roll back if verification fails
```

No web-specific mutation path is allowed.

### Local Drafts And Shared Staging

A form draft is private to the browser until the user explicitly stages it.

```text
local browser form draft
→ user chooses Stage
→ server validates and adds it to the shared staged set
```

Each project collaboration session has one shared staged set.

Each staged item records:

- stable staged-change ID;
- author;
- creation time;
- latest editor;
- edit type;
- target entity;
- target source;
- baseline;
- typed edit payload;
- validation state;
- conflict state;
- optional comment;
- AI-generated indicator.

### Disconnect Behavior

Shared staged changes belong to the collaboration session, not to one WebSocket connection.

- Temporary disconnect does not remove a user's staged changes.
- Users may remove their own staged changes.
- Reviewers may remove or reject any staged change.
- Shared staged state is not durable across server restart or explicit session closure.
- The UI must clearly communicate this limitation.

### Conflict Behavior

A staged item becomes `STALE` or `CONFLICTED` when its baseline no longer matches the authoritative project state or when another staged operation creates an incompatible target change.

A stale or conflicted item:

- cannot be applied;
- remains visible;
- may be discarded;
- may be re-prepared against the current graph;
- may be manually edited and restaged.

Phase 6 does not automatically merge conflicting ontology edits.

### Proposal Review

The combined proposal review must show:

- explicit graph changes;
- reasoning impact;
- SHACL impact;
- affected sources;
- contributors;
- conflicts;
- blocking issues;
- AI-generated entries;
- approval and rejection controls.

Final approval, application, and rollback are server-authorized reviewer actions.

## Collaboration Protocol

Phase 6 uses server-authoritative HTTP and WebSocket behavior. Yjs and CRDT synchronization are deferred.

At least two browser sessions must be able to join one project collaboration session and see:

- connected users;
- current entity activity where appropriate;
- shared staged changes and authors;
- proposal updates;
- reasoning and SHACL job updates;
- applied/reloaded project events;
- baseline conflicts.

### WebSocket Reliability

Every project-session event must include:

- event ID;
- project ID;
- collaboration-session ID;
- monotonically increasing project-session sequence;
- event type;
- server timestamp;
- applicable entity, staged-change, proposal, or job ID.

Clients record the last sequence received.

On reconnect:

- the client fetches current authoritative state through HTTP;
- the server is not required to replay unlimited event history;
- duplicate events are ignored;
- missing or out-of-order events trigger authoritative refetch.

WebSocket events are notifications, not the sole source of truth.

## Asynchronous Semantic Jobs

### Reasoning Jobs

Reasoning runs as a background job with:

- job ID;
- project ID;
- graph or proposal-preview fingerprint;
- job target type;
- queued, running, completed, failed, cancelled, incomplete, and stale states;
- start and completion timestamps;
- status/progress metadata where available;
- cancellation;
- stale-result detection;
- latest-valid-result retention.

Concurrency rules:

- At most one active applied-graph reasoning job per project.
- At most one active reasoning job per proposal-preview fingerprint.
- A newer applied-graph job supersedes an older active job.
- A preview job becomes stale or is cancelled when the staged set changes.
- The latest valid applied result remains visible while a newer job runs.
- A result is accepted only when its fingerprint matches its target.
- AI requests must not block reasoning jobs.

The UI distinguishes:

- applied-graph reasoning;
- proposal-preview reasoning;
- stale reasoning from an older graph.

Users can continue navigating and editing while reasoning runs.

### SHACL Jobs

SHACL validation should follow the same job model where practical.

SHACL jobs may reuse a valid reasoning result when asserted-plus-inferred validation is explicitly requested.

The UI should show:

- validation mode;
- affected entities;
- newly introduced or worsened findings;
- unchanged findings;
- improved or resolved findings;
- stale or incomplete status.

## FIBO Browser And Reuse

The browser should expose:

- curated module browsing;
- wider deterministic catalog search;
- external descriptions;
- score breakdowns and match reasons;
- dependency review;
- external reuse preparation;
- local subclass preparation.

FIBO assets and original IRIs remain immutable.

Phase 6 must complete an additive reusable boundary that converts an approved Phase 5 external proposal intent into the same shared staged-change representation used by local typed edits.

The browser must not claim full end-to-end FIBO reuse until this boundary can:

- preserve original FIBO IRIs;
- include selected dependencies;
- enter the shared staged set;
- participate in combined preview;
- use the existing validation, reasoning, SHACL, approval, application, reload, and rollback workflow.

## Native AI Assistant

### Supported Operations

The initial AI boundary should use explicit operation types:

- `EXPLAIN_ENTITY`
- `EXPLAIN_INFERENCE`
- `EXPLAIN_SHACL_RESULT`
- `SEARCH_FIBO`
- `SUGGEST_DEFINITION`
- `SUGGEST_SUPERCLASS`
- `SUGGEST_PROPERTY`
- `SUGGEST_EXTERNAL_REUSE`
- `SUMMARIZE_PROPOSAL`

AI may explain possible SHACL rules, but it must not create a staged SHACL mutation unless a supported typed SHACL edit contract exists.

### Context Boundary

The server builds structured context from existing Entio services.

Context may include:

- current entity descriptor;
- explicitly selected related entities;
- asserted relationships;
- inferred relationships;
- reasoning explanation;
- SHACL findings;
- relevant staged changes;
- relevant FIBO candidates;
- user question.

The server must not send uncontrolled project files or unrelated project data.

Ontology labels, definitions, annotations, source text, and FIBO content are untrusted data, not system instructions.

The provider request must clearly separate:

- trusted system policy;
- user request;
- structured ontology context;
- untrusted ontology text.

The model cannot change tools, permissions, validation rules, or approval policy.

### AI Response Contract

An AI response should separate:

- narrative answer;
- referenced Entio evidence;
- inferred facts;
- FIBO results;
- proposed typed edits;
- uncertainty;
- warnings.

Only supported typed suggestions may be converted into staged edits.

AI output is untrusted until the Kotlin server validates and translates it.

### Credential Handling

Each user supplies their own OpenAI API key.

Phase 6 policy:

- key entered through settings;
- sent to Ktor over the configured secure connection;
- stored only in server-side memory keyed by authenticated/development user session;
- never persisted to disk;
- removed on logout, session expiration, server restart, or explicit deletion;
- React stores only credential status, never the key after submission;
- never included in URLs, logs, analytics, WebSocket events, collaboration state, proposals, ontology files, snapshots, or another user's session;
- authorization headers and provider errors are redacted;
- usage and billing remain associated with the user's OpenAI API account.

The React client must never call OpenAI directly.

Missing, invalid, revoked, removed, or rate-limited credentials return clear AI-unavailable states without affecting non-AI functionality.

## Versioned Web Contracts

Use a versioned `/api/v1` and `/ws/v1` boundary.

Expected HTTP resource areas include:

```text
/api/v1/projects
/api/v1/projects/{projectId}
/api/v1/projects/{projectId}/hierarchy
/api/v1/projects/{projectId}/entities/{encodedIri}
/api/v1/projects/{projectId}/search
/api/v1/projects/{projectId}/staged-changes
/api/v1/projects/{projectId}/proposals/preview
/api/v1/projects/{projectId}/proposals/review
/api/v1/projects/{projectId}/proposals/apply
/api/v1/projects/{projectId}/reasoning/jobs
/api/v1/projects/{projectId}/shacl/jobs
/api/v1/projects/{projectId}/fibo/browse
/api/v1/projects/{projectId}/fibo/search
/api/v1/projects/{projectId}/fibo/dependencies
/api/v1/projects/{projectId}/ai/requests
/api/v1/users/me/ai-credential
```

WebSocket:

```text
/ws/v1/projects/{projectId}
```

Exact request and response schemas belong in the ExecPlan, but every contract must use:

- stable Entio-owned IDs and DTOs;
- structured error responses;
- project-scoped authorization;
- explicit baseline and fingerprint fields;
- idempotency keys for operations where retries could otherwise duplicate staging or application;
- no third-party semantic-web types.

## Inputs And Outputs

### Inputs

- Registered Entio project IDs.
- Existing `entio.yaml`, ontology, and SHACL sources behind the registry.
- Existing Kotlin semantic service outputs and proposal contracts.
- Existing immutable FIBO package and Phase 5 contracts.
- Versioned browser HTTP requests.
- Versioned WebSocket session messages.
- Development user/session identity and role.
- User-entered typed edits and optional comments.
- Current-user OpenAI API key only for explicitly initiated AI requests.

### Outputs

- Versioned HTTP responses for projects, hierarchy, entities, search, FIBO, jobs, staged changes, proposals, collaboration, and AI.
- WebSocket events for presence, activity, staged changes, proposals, jobs, reloads, and conflicts.
- React views for navigation, entity tabs, editing, proposal review, reasoning, SHACL, FIBO, collaboration, settings, and AI.
- Structured AI suggestions that can be converted into supported typed staged edits.
- No direct ontology-file mutation from the browser or AI layer.

## Validation And Error Handling

- Existing Kotlin validation, proposal baseline, semantic diff, reasoning, SHACL, and rollback rules remain authoritative.
- HTTP and WebSocket boundaries validate request shape, project identity, session membership, role, current baseline, and supported operation type.
- Invalid or stale requests return structured errors without mutation.
- Conflicting or stale staged changes cannot enter an applied proposal.
- Long-running jobs expose queued, running, completed, failed, cancelled, incomplete, and stale states.
- Job results are accepted only for their producing fingerprint.
- Reviewer/application permissions are enforced server-side.
- AI output is untrusted and must be translated into supported typed edits before staging.
- AI context must treat ontology content as untrusted data.
- A failed apply or post-apply verification uses existing rollback behavior and broadcasts the resulting authoritative state.
- Web failures must not corrupt existing CLI, VS Code, or direct service behavior.

## Primary Vertical Product Journey

The mandatory Phase 6 vertical journey is:

1. Open a registered project in the browser.
2. Navigate a lazy-loaded hierarchy.
3. Open an entity tab.
4. Prepare a supported typed edit.
5. Stage it into the shared collaboration session.
6. A second user sees the staged edit and author.
7. Build a combined preview.
8. Run reasoning for the preview asynchronously.
9. Review explicit, reasoning, and SHACL impact.
10. A reviewer approves and applies the proposal.
11. Both clients receive reload and updated job events.
12. The AI explains the selected entity and suggests one supported typed edit using the current user's own OpenAI API key.
13. The accepted AI suggestion enters the same shared staged workflow.

Additional FIBO, activity, tab-restoration, and expanded SHACL presentation may be implemented in later Phase 6 slices after this path is complete.

## Test Cases

### Compatibility

- Existing Kotlin tests, CLI tests, VS Code compilation/tests, example-project regressions, reasoning, SHACL, proposal, and FIBO tests continue to pass.
- Backend services remain directly usable without Ktor.
- Removing or disabling `web-app` and `web-server` does not break existing clients.

### Project And Navigation

- Only registered projects are visible.
- Arbitrary paths and traversal attempts are rejected.
- Hierarchy loading is lazy, ordered, filterable, and stale-request safe.
- Entity tabs render explicit source attribution, inferred relationships, and technical details correctly.

### Shared Staging And Collaboration

- Two clients join one project session.
- Presence, entity activity, and shared staged changes synchronize.
- Local drafts remain private until staged.
- Disconnecting does not immediately remove shared staged changes.
- Stale and conflicting changes cannot be applied.
- Users can discard or re-prepare stale edits.
- Contributors and reviewers receive the correct server-enforced capabilities.
- Reconnect fetches authoritative state.
- Duplicate or out-of-order WebSocket events do not corrupt client state.

### Proposal And Jobs

- Combined previews use existing Kotlin proposal behavior.
- Reasoning and SHACL jobs run while navigation/editing continues.
- Newer jobs supersede or stale older jobs according to the approved rules.
- Results with mismatched fingerprints are rejected.
- Apply, reload, and rollback events reach connected clients.

### FIBO

- Browsing and deterministic search use existing Phase 5 behavior.
- External proposal intents can enter the common shared staged set.
- FIBO assets remain immutable.
- Original FIBO IRIs and dependencies are preserved.

### AI And Secrets

- Missing, invalid, revoked, removed, and rate-limited keys produce correct states.
- The browser never retains the submitted key.
- Keys do not appear in logs, URLs, WebSocket events, collaboration state, proposals, ontology files, snapshots, or other sessions.
- Ontology text is treated as untrusted context.
- Unsupported or malformed model suggestions cannot be staged.
- Supported suggestions remain subject to normal validation and approval.
- AI SHACL explanations do not become shape mutations without a typed backend contract.

### Frontend

- React component, routing, Query-cache, tab-state, form-state, and error-state tests pass.
- Playwright covers the primary vertical product journey.
- Loading, empty, unavailable, stale, conflict, and permission states are visible and accessible.

## Acceptance Criteria

- Entio runs as a browser-based ontology workbench without replacing the VS Code extension or CLI.
- `web-server` and `web-app` follow the approved isolated repository layout.
- Browser clients use registered project IDs and cannot submit arbitrary filesystem paths.
- React uses Ktor contracts and existing Kotlin services rather than duplicating semantics.
- The hierarchy is lazy-loaded, label-first, searchable, and distinguishes asserted from inferred relationships.
- Users can open entities in tabs and inspect human-readable and technical details.
- One shared staged set exists per project collaboration session.
- Local drafts remain private until staged.
- Shared staged changes survive temporary browser disconnects for the session lifetime.
- Stale or conflicting changes cannot be applied and may be discarded or re-prepared.
- At least two users can connect, see presence, share staged activity, and receive authoritative project/job updates.
- The server enforces contributor and reviewer permissions.
- WebSocket events are sequenced notifications and HTTP remains the authoritative state source.
- Reasoning and SHACL execute asynchronously with bounded concurrency, cancellation, fingerprints, and stale-result protection.
- FIBO external proposal preparation can enter the common staged-change path.
- SHACL browser views remain inspection-focused unless approved typed mutation support exists.
- A user with a valid personal OpenAI API key can request supported assistance and convert an accepted supported suggestion into a typed staged edit.
- OpenAI keys are stored only in server-side session memory for Phase 6 and never exposed to clients, collaborators, logs, or semantic records.
- AI context treats ontology text as untrusted data and is limited to relevant structured project context.
- AI suggestions cannot directly mutate ontology files or bypass validation and human approval.
- Existing Phase 1 through Phase 5 regression suites and explicit client compatibility checks pass.
- The web application can be disabled or removed without breaking the CLI, VS Code extension, or semantic engine.
- The primary vertical product journey passes automated end-to-end testing.

## Remaining ExecPlan Decisions

The following implementation details may be finalized by the ExecPlan without changing product behavior:

- Exact package versions for React, Vite, Ktor, TanStack Query, component primitives, Vitest, and Playwright.
- Exact Kotlin class and package names.
- Exact DTO field names and serialization details.
- Exact development user fixtures and login screen presentation.
- Exact in-memory session timeout values.
- Exact page sizes and hierarchy child-batch sizes.
- Exact AI context token limits and request timeouts.
- Exact server startup configuration and development project-registry file format.

These decisions must remain consistent with the approved architecture and cannot introduce incompatible existing-client changes.
