# ExecPlan: Phase 6 Collaborative Web Workbench And Native AI Foundation

## Status

Implemented. The approved Phase 6 slices and regression work are complete; see `docs/phase-summaries/phase-6-summary.md` for the resulting repository state and documented limitations. Historical current-state language below describes the repository before Phase 6 implementation.

## Source Spec

- [Phase 6 feature spec](../specs/0009-phase-6-collaborative-web-workbench-native-ai-foundation.md)
- [Phase 6 scope](../architecture/phase-6-scope.md)

## Objective

Add an additive browser workbench and native AI foundation around the existing Kotlin/JVM semantic engine. The web application should provide a polished, label-first interface for browsing ontology entities, staging typed edits, reviewing semantic changes, coordinating with other users, inspecting asynchronous reasoning and SHACL work, browsing FIBO content, and receiving provider-neutral AI suggestions.

The Kotlin semantic engine remains the source of truth. The web backend is an adapter and session coordinator, not a second ontology engine. The existing CLI and VS Code extension must continue to work unchanged.

## Current State

Entio currently contains the Kotlin/JVM modules `core-types`, `semantic-engine`, `validation-engine`, `graph-diff`, `cli`, and `shared`, together with the VS Code ontology workbench. The existing engine owns project loading, RDF term fidelity, ontology parsing, symbols, validation, semantic diffs, typed edits, proposal lifecycle behavior, OWL reasoning, SHACL validation, and external ontology/FIBO workflows that are already present on the approved base.

There is no Phase 6 web application or Ktor web-server module yet. Existing clients call the semantic engine through the CLI or their established boundaries. Phase 6 must add the browser surface without moving semantic behavior into React, HTTP routes, or a second parser.

## Target State

The repository will contain:

```text
web-server/
  build.gradle.kts
  src/main/kotlin/com/entio/web/
  src/test/kotlin/com/entio/web/

web-app/
  package.json
  vite.config.ts
  src/
  tests/
  e2e/
```

`web-server` will expose versioned HTTP and WebSocket adapters over reusable Kotlin services. `web-app` will be a React/TypeScript/Vite client that consumes those contracts. Development storage may remain in memory behind explicit interfaces; no production database or identity system is part of this phase.

## Scope

Phase 6 includes:

- A React/TypeScript browser workbench with routing, hierarchy navigation, entity tabs, activity areas, and accessible loading/error/stale/conflict states.
- A Kotlin/Ktor adapter that delegates semantic operations to existing modules.
- Label-first class, property, individual, shape, reasoning, SHACL, FIBO, and proposal views with IRIs available on demand.
- Server-authoritative staged changes and proposal lifecycle behavior.
- Two-session collaboration with presence, shared staged changes, activity, ordering, baseline conflict detection, and refresh events.
- Asynchronous reasoning and SHACL jobs with status, progress, cancellation, invalidation, stale-result handling, and WebSocket updates.
- FIBO browsing, search, module details, dependency display, and typed reuse proposals through existing Phase 5 capabilities.
- A provider-neutral native AI boundary, an assistant panel, per-user credential settings, and conversion of accepted AI suggestions into ordinary typed staged edits.
- Regression coverage for the existing Kotlin modules, CLI, VS Code extension, examples, proposal workflows, reasoning, SHACL, and FIBO behavior.

## Non-Goals

This plan must not add:

- A rewrite of the semantic engine or a second RDF, OWL, SHACL, or Turtle framework.
- A replacement CLI protocol or semantic behavior implemented in HTTP routes or TypeScript.
- Full production authentication, tenancy, roles, audit history, deployment, observability, or administration.
- A graph database, offline editing, peer-to-peer synchronization, Yjs or another CRDT framework, full graph CRDTs, fine-grained RDF merge, or durable collaborative comments.
- New FIBO retrieval/indexing infrastructure or mutation of immutable external ontology assets.
- Full Protégé parity, arbitrary OWL class-expression editing, or a full SHACL authoring environment.
- Autonomous agents, automatic proposal application, AI bypass of validation/review, or shared/funded AI credentials.
- Replacing or weakening the existing VS Code extension, CLI, Kotlin modules, or their public behavior.
- A mobile-first experience.
- Git operations inside Entio. The proposal workflow is git-like by analogy only.

## Architecture And Fixed Decisions

### Module boundaries

- `web-server` owns HTTP/WebSocket transport, project/session orchestration, job coordination, credential boundaries, and response mapping.
- `web-app` owns presentation, routing, browser state, accessibility, and transport clients. It must not parse RDF or decide semantic validity.
- `core-types` remains the home of shared Entio contracts. Additions must be backward compatible and justified by a web or collaboration boundary.
- `semantic-engine`, `validation-engine`, `graph-diff`, and existing proposal/reasoning/SHACL/FIBO services remain semantic sources of truth.
- `cli` remains a supported client boundary. `web-server` must call reusable services directly rather than shelling out to CLI commands.
- `shared` remains minimal and must not become a web framework or product-service bucket.

### Recommended stack

- Kotlin/Ktor server with Netty, content negotiation, JSON serialization, and WebSockets.
- Existing Kotlin semantic dependencies reused through module APIs. Ktor dependencies belong only to `web-server`.
- React, TypeScript, Vite, React Router, TanStack Query, a small Zustand store or local React state for client-only workspace state, and an accessible component/token layer such as Radix UI primitives for `web-app`.
- TanStack Query owns server data and cache invalidation. Browser-local state owns tabs, drafts, layout, and preferences only.
- Vitest and Testing Library for frontend unit/component tests; Playwright for approved end-to-end coverage.
- Ktor content negotiation uses one pinned Kotlin JSON serialization approach inside `web-server`. Web DTOs are Entio-owned and must not couple the server to internal CLI payload classes.
- Ktor client CIO plus JSON support behind a provider interface for the initial OpenAI integration. Do not add a vendor SDK to core modules.
- Structured Kotlin coroutines only where required by Ktor and asynchronous job execution; do not introduce a general coroutine platform or plugin system.

### Project registry and development identity

Browser clients use stable project IDs and never submit arbitrary filesystem roots.

`web-server` owns an approved project registry:

```text
projectId -> normalized, allowlisted Entio project root
```

The registry must reject path traversal and unregistered roots and must reuse existing project/source safety checks.

Phase 6 uses deterministic development users with server-enforced roles:

- `CONTRIBUTOR`
- `REVIEWER`

Contributors may browse, prepare, stage, preview, and remove their own staged changes. Reviewers may additionally remove any staged change, approve, reject, apply, roll back, and cancel project-wide semantic jobs.

### State ownership

Keep these boundaries explicit:

- Durable project/filesystem state is owned by the existing project and ontology services.
- The immutable FIBO package remains owned by the existing Phase 5 package boundary.
- Per-project collaboration-session state contains connected users, presence, entity activity, one shared staged set, and current session metadata. It is in memory for Phase 6.
- Browser-local state contains open tabs, layout, preferences, and unsubmitted form drafts.
- Proposal state contains typed payloads, source targets, baselines, validation, diffs, approval/rejection, and apply results. It is session-scoped until applied, rejected, removed, invalidated, or the session ends.
- Job state contains job ID, project/graph fingerprint, status, optional phase progress, timestamps, cancellation, and stale-result information. It is project-scoped and in memory.
- Credential state is held only in server-side user-session memory. The browser receives status, not the secret.
- In-memory implementations must be hidden behind interfaces and documented as non-production.

### Versioned contracts

Use `/api/v1` for HTTP resources. The exact DTOs must be defined in Kotlin-facing contract types and mapped to frontend types. A provisional resource layout is:

| Area | Endpoint shape | Purpose |
| --- | --- | --- |
| Project | `/api/v1/projects/{projectId}/summary` | Project and source summary |
| Navigation | `/api/v1/projects/{projectId}/hierarchy`, `/search` | Label-first hierarchy and search |
| Entities | `/api/v1/projects/{projectId}/entities/{iri}` | Entity details and related facts |
| FIBO | `/api/v1/projects/{projectId}/fibo/...` | Curated modules, details, dependencies, reuse |
| Staging | `/api/v1/projects/{projectId}/staged-changes` | Shared staged set and metadata |
| Proposals | `/api/v1/projects/{projectId}/proposal/...` | Preview, validate, review, approve, reject, apply |
| Jobs | `/api/v1/projects/{projectId}/jobs/reasoning`, `/jobs/shacl` | Start, inspect, cancel, and refresh jobs |
| AI | `/api/v1/projects/{projectId}/ai/...` | Credential status and provider-neutral requests |
| Session | `/api/v1/projects/{projectId}/session` | WebSocket session boundary |

WebSocket events must cover presence, entity activity, staged-change updates, proposal updates, job progress/completion, project reload, conflicts, and structured errors. Exact request and response schemas are implementation outputs of Slice 2 and must remain additive to existing public contracts.

The common web-contract foundation must define:

- API version and request ID;
- structured error envelope;
- stable project, session, user, entity, staged-change, proposal, event, and job IDs;
- baseline and fingerprint fields;
- pagination and continuation metadata;
- stable encoded-IRI handling;
- idempotency keys for staging, approval/application, and other retry-sensitive operations.

Reusing an idempotency key with a different payload must be rejected.

## Dependency Order And Multi-Agent Safety

All slices are serial. A later slice may begin only after the prior slice has been verified, committed, pushed, merged locally into `main`, and post-merge verified. No parallel work is permitted on this plan.

The following are shared-contract surfaces and must never be edited concurrently with other work:

- `core-types`
- `shared`
- `web-server` contract DTOs
- `web-app` transport types
- `settings.gradle.kts`, root `build.gradle.kts`, module build files, and frontend package/lock files
- Phase specs, ExecPlans, ADRs, and completion summaries
- `AGENTS.md`

Implementation slices:

1. Application boundary and build scaffolds
2. Web contract foundation, project registry, and development identity
3. Read-only project adapters
4. React workbench shell, hierarchy, routing, and entity tabs
5. Shared staging and proposal HTTP workflow
6. Collaboration sessions, presence, and shared updates
7. Asynchronous reasoning and SHACL jobs
8. FIBO browser and shared-staging integration
9. Mandatory core-web checkpoint after Slice 8; verification and completion artifact only, with no implementation branch, commit, push, or merge
10. AI provider boundary and credential safety
11. AI assistant and typed edit integration
12. UI hardening, accessibility, and activity integration
13. End-to-end Phase 6 regression and documentation summary

## Implementation Slices

### Slice 1: Application Boundary And Build Scaffolds

#### Goal

Create the additive `web-server` Ktor module and `web-app` React/Vite application with a health/readiness boundary and minimal test harnesses.

#### Allowed files/modules

- Root Gradle settings and build files only for registering `web-server` and its approved dependencies.
- `web-server/build.gradle.kts`.
- `web-server/src/main/kotlin/com/entio/web/` and focused tests.
- `web-app/package.json`, lockfile, TypeScript/Vite configuration, `index.html`, `src/`, and focused tests.
- `.gitignore` only for required generated frontend artifacts.
- A focused ADR if the final Ktor boundary differs from this plan.

#### Dependencies

Existing Kotlin modules; Ktor server core/Netty/content negotiation/WebSockets; React, TypeScript, and Vite. Keep the initial endpoint to health/readiness and a non-semantic shell.

#### Forbidden actions/modules

- Do not modify semantic behavior in `core-types`, `semantic-engine`, `validation-engine`, `graph-diff`, or `cli`.
- Do not add database, authentication, dependency injection, AI, FIBO retrieval, or collaboration infrastructure.
- Do not parse RDF, write ontology files, or add VS Code behavior.

#### Expected output

Both projects install/build, the server starts with a health response, and the browser shell loads without claiming Phase 6 product functionality.

#### Tests

Ktor health/readiness tests, frontend render/build tests, and configuration tests for the two project boundaries.

#### Verification commands

```bash
./gradlew :web-server:test
./gradlew test
(cd web-app && npm ci && npm test && npm run build)
```

#### Stop conditions

Stop if registering the module requires changing existing public behavior, if frontend tooling cannot be isolated, or if the server requires a database or production identity system.

### Slice 2: Web Contract Foundation, Project Registry, And Development Identity

#### Goal

Define the common versioned web-contract foundation, approved project registry, deterministic development identity, contributor/reviewer permissions, structured errors, pagination, fingerprints, and idempotency behavior.

#### Allowed files/modules

- `web-server/src/main/kotlin/com/entio/web/` contracts, registry, identity, authorization, mapping, and tests.
- `web-app/src/` transport types, session/current-user helpers, and focused tests.
- Additive `core-types` changes only when an Entio-owned cross-client contract is genuinely required.

#### Dependencies

Slice 1.

#### Forbidden actions/modules

- Do not expose arbitrary filesystem paths to browser clients.
- Do not add production authentication, a database, semantic parsing, proposal mutation, WebSockets, jobs, AI calls, or CLI subprocess execution.
- Do not change existing CLI response shapes or semantic contracts.
- Do not put ontology policy in authorization or route code.

#### Expected output

- `/api/v1` contract foundation with request IDs, structured errors, stable IDs, baseline/fingerprint fields, pagination, encoded-IRI rules, and idempotency keys.
- Server-owned `projectId -> normalized allowlisted root` registry.
- Deterministic development users and server-enforced `CONTRIBUTOR` and `REVIEWER` roles.
- Current-user/session response.
- Rejection of arbitrary, unregistered, absolute, or traversal project paths.
- Idempotency semantics that reject key reuse with different payloads.

#### Tests

- Contract serialization and versioning.
- Structured error mapping.
- Project registry allowlist and traversal rejection.
- Development-user identity and role enforcement.
- Current-user response.
- Pagination/continuation and encoded-IRI handling.
- Idempotency replay and conflicting-key rejection.

#### Verification commands

```bash
./gradlew :web-server:test
./gradlew test
(cd web-app && npm test && npm run build)
```

#### Stop conditions

Stop if browser access requires arbitrary filesystem paths, if production identity or persistence is required, if permission checks must live in React, or if an existing contract must break.

### Slice 3: Read-Only Project Adapters

#### Goal

Expose project summary, ontology sources, lazy label-first hierarchy, entity details, and semantic search through typed web-server responses backed by existing services.

#### Allowed files/modules

- `web-server/src/main/kotlin/com/entio/web/` adapters, routes, mapping, and tests.
- `web-app/src/` transport types/client helpers and focused tests.
- Additive engine API changes only when an existing reusable service cannot expose an already-supported result without duplication.

#### Dependencies

Slices 1 and 2; `ProjectLoader`, symbol extraction, existing description/detail services, and existing reasoning/SHACL/FIBO read contracts where available.

#### Forbidden actions/modules

- Do not put RDF parsing, label resolution, semantic search logic, or validation rules in routes or TypeScript.
- Do not add proposal mutation, WebSockets, jobs, AI calls, or CLI subprocess execution.
- Do not change existing CLI response shapes or semantic contracts.

#### Expected output

The web client can load an approved project, browse lazy hierarchy children, search by label, open entity details, and request technical IRIs on demand. Asserted/inferred and source/origin metadata remain explicit.

Hierarchy contracts must provide stable ordering, child counts or continuation metadata, default and maximum batch sizes, and no full-tree response on project load.

#### Tests

Contract serialization, adapter delegation, missing entity/source errors, deterministic ordering, pagination/filtering, stale-request handling, label-first rendering, IRI-on-demand behavior, and hierarchy batch limits.

#### Verification commands

```bash
./gradlew :web-server:test
./gradlew test
(cd web-app && npm test && npm run build)
```

#### Stop conditions

Stop if read-only results cannot be produced by existing semantic services, if the adapter duplicates parsing or ranking, or if a breaking core contract is required.

### Slice 4: React Workbench Shell, Hierarchy, Routing, And Entity Tabs

#### Goal

Build the usable browser navigation experience: global header, collapsible sidebar, project hierarchy, search, tabs, label-first entity views, and progressive technical details.

#### Allowed files/modules

- `web-app/src/` routes, components, state, design tokens, accessibility behavior, and tests.
- `web-server` read-route adjustments only when required by Slice 2 contracts.

#### Dependencies

Slices 1-3; React Router and the approved client data/cache layer.

#### Forbidden actions/modules

- Do not implement typed edit translation, staging, proposal lifecycle, WebSockets, reasoning, SHACL, FIBO retrieval, or AI.
- Do not place semantic rules or RDF libraries in `web-app`.
- Do not remove or replace the VS Code workbench.

#### Expected output

Users can open a project, navigate sources and a bounded lazy hierarchy, search by label, open multiple entity tabs, inspect class/property/individual/shape details, and reveal IRIs and raw technical facts only when requested.

Shape tabs are inspection-only. They do not expose create, edit, or delete actions unless an already-approved typed SHACL mutation contract exists.

#### Tests

Routing, tab open/close/restore behavior, hierarchy lazy loading, label rendering, error/loading/stale states, keyboard navigation, focus management, and responsive desktop pane behavior.

#### Verification commands

```bash
(cd web-app && npm test && npm run build)
```

If any permitted `web-server` or Kotlin file is changed, also run:

```bash
./gradlew :web-server:test
./gradlew test
```

#### Stop conditions

Stop if the UI needs to invent a semantic response, if technical IRIs become the default display, or if the shell requires changes to existing clients.

### Slice 5: Shared Staging And Proposal HTTP Workflow

#### Goal

Expose the existing typed-edit, staged-change, preview, validation, diff, approve, reject, apply, reload, and rollback workflow through a single-client web boundary.

#### Allowed files/modules

- `web-server` staging/proposal services, DTOs, routes, error mapping, and tests.
- Additive `core-types` contract metadata only when required for author/session/baseline fields.
- `web-app` edit forms, staged list, proposal preview/review state, and tests.

#### Dependencies

Slices 1-4; existing typed edit translation, proposal validation, semantic diff, atomic apply, rollback, reasoning, SHACL, and source reload services.

#### Forbidden actions/modules

- Do not write RDF directly from HTTP or TypeScript.
- Do not bypass validation, baseline checks, human approval, apply, or rollback.
- Do not implement collaboration, AI, or a new proposal model.

#### Expected output

- One server-owned shared staged set per project collaboration session.
- Browser form drafts remain private until explicitly staged.
- Stable staged-change IDs, author, latest editor, baseline, typed payload, validation state, conflict state, optional comment, and AI-generated flag.
- Staged state survives temporary browser disconnects for the session lifetime but is not durable across server restart or session closure.
- `STALE` and `CONFLICTED` entries remain visible but cannot enter an applied proposal.
- Users may discard, edit/restage, or re-prepare stale/conflicted entries against the current graph.
- No automatic ontology merge.
- Combined previews produce semantic diffs and impact information, and only an approved current proposal reaches the Kotlin applier.
- Rejection leaves source files unchanged and returns the staged set for correction.
- Idempotent stage and apply requests do not duplicate work.

#### Tests

Typed request validation, stage/preview/review lifecycle, stable staged IDs and metadata, private-draft versus shared-stage behavior, stale/conflicted recovery paths, approval requirements, idempotent stage/apply retries, apply/rollback delegation, response serialization, and UI state transitions.

#### Verification commands

```bash
./gradlew :web-server:test
(cd web-app && npm test && npm run build)
./gradlew test
```

#### Stop conditions

Stop if the workflow mutates sources before approval, loses staged metadata, silently overwrites a baseline, or requires changing existing proposal semantics.

### Slice 6: Collaboration Sessions, Presence, And Shared Updates

#### Goal

Add server-authoritative project sessions with two-client presence, entity activity, shared staged changes, ordered events, and explicit conflict handling.

#### Allowed files/modules

- `web-server` session model, in-memory stores/interfaces, WebSocket routes, event schemas, conflict handling, and tests.
- `web-app` WebSocket client, presence/activity indicators, shared staged state, reconnect behavior, and tests.
- Additive `core-types` event contracts only when necessary.

#### Dependencies

Slice 5’s staging workflow; Ktor WebSockets; existing proposal baseline contracts.

#### Forbidden actions/modules

- Do not use a client as the authority for ontology mutation.
- Do not add a full graph CRDT, peer-to-peer protocol, offline editing, durable comments, or production identity/authorization.
- Do not silently overwrite concurrent changes or alter proposal application semantics.

#### Expected output

Two browser sessions see connected users, open-entity activity, staged changes and authors, proposal/apply/reload events, and explicit baseline conflicts.

Every event contains an event ID, project ID, collaboration-session ID, monotonically increasing session sequence, event type, timestamp, and applicable entity/staged-change/proposal/job ID.

HTTP remains authoritative. Reconnect performs an authoritative HTTP refresh. Duplicate events are ignored, and sequence gaps or out-of-order delivery trigger refetch rather than corrupting client state.

#### Tests

Two-client WebSocket tests, presence join/leave, event ordering, staged-change broadcast, reconnect snapshot, conflict detection, duplicate event handling, and unauthorized mutation rejection.

#### Verification commands

```bash
./gradlew :web-server:test
(cd web-app && npm test && npm run build)
```

#### Stop conditions

Stop if synchronization requires a CRDT for authoritative ontology state, if conflict handling is implicit, or if user secrets enter session events.

### Slice 7: Asynchronous Reasoning And SHACL Jobs

#### Goal

Run reasoning and SHACL work as cancellable, observable jobs with graph/proposal fingerprints, stale-result detection, and browser updates.

#### Allowed files/modules

- `web-server` job interfaces, manager, in-memory job store, endpoints, WebSocket events, and tests.
- Additive wrappers in `semantic-engine` or `validation-engine` only to invoke existing reasoning/SHACL services safely.
- `web-app` job hooks, status panels, progress/error/stale displays, cancellation controls, and tests.

#### Dependencies

Slices 5 and 6; existing OWL reasoning and SHACL services; Ktor structured concurrency.

#### Forbidden actions/modules

- Do not implement a new reasoner or SHACL engine.
- Do not block HTTP requests on long-running semantic work.
- Do not present stale results as current, mutate ontology sources from a job, or make AI depend on jobs in this slice.

#### Expected output

Reasoning and SHACL requests return job IDs. Jobs report queued/running/completed/failed/cancelled/incomplete/stale states, optional phase/status progress, and timestamps.

Concurrency rules:

- At most one active applied-graph reasoning job per project.
- At most one active reasoning job per proposal-preview fingerprint.
- A newer applied-graph job supersedes an older active job.
- A staged-set change cancels or marks matching preview jobs stale.
- The latest valid applied result remains visible while a replacement runs.
- Results are accepted only when fingerprints match.
- Asserted-plus-inferred SHACL may reuse only a complete matching reasoning result.
- AI requests do not block semantic jobs.
- Numeric percentage progress is optional; phase/status progress must not falsely imply precision.

The UI distinguishes applied, proposal, and stale graph results and updates without a full-page reload.

#### Tests

Job lifecycle, cancellation, failure, fingerprint invalidation, stale result suppression, asserted-only versus asserted-plus-inferred modes, proposal impact, WebSocket updates, and UI status rendering.

#### Verification commands

```bash
./gradlew :web-server:test
./gradlew test
(cd web-app && npm test && npm run build)
```

#### Stop conditions

Stop if a result cannot be tied to a graph/proposal fingerprint, if cancellation is unsafe, or if existing reasoning/SHACL semantics must be rewritten.

### Slice 8: FIBO Browser And Shared-Staging Integration

#### Goal

Expose curated FIBO modules, broader search, details, dependency context, match reasons, and typed reuse/local-subclass proposals in the web workbench.

#### Allowed files/modules

- `web-server` FIBO adapters, routes, mapping, and tests.
- `web-app` FIBO browse/search/detail/dependency/reuse views and tests.
- Narrow additive changes in `core-types`, `semantic-engine`, and existing Phase 5 proposal adapters needed to convert a validated `ExternalProposalIntent` into the common shared staged-change representation.

#### Dependencies

Slices 3, 5, and 7; existing Phase 5 external ontology/FIBO boundary; immutable bundled assets.

#### Forbidden actions/modules

- Do not create a new FIBO retrieval/indexing system.
- Do not mutate external ontology assets or drop source IRIs.
- Do not add AI behavior or direct source writes in FIBO views.

#### Expected output

Users can browse modules, search curated/external content, inspect labels/definitions/IRIs/dependencies, and convert a validated external reuse or local-subclass intent into the same shared staged-change representation used by local edits.

The complete path is:

```text
FIBO selection
→ dependency review
→ shared staged item
→ combined preview
→ reasoning and SHACL impact
→ reviewer apply
→ reload
```

Original FIBO IRIs are preserved and bundled assets remain byte-for-byte unchanged.

#### Tests

Module pagination, label/definition fallback, details, dependency display, external source errors, IRI preservation, immutable asset checks, and reuse proposal routing.

#### Verification commands

```bash
./gradlew :web-server:test
(cd web-app && npm test && npm run build)
./gradlew test
```

#### Stop conditions

Stop if closing the Phase 5 staging gap requires a breaking Phase 5 contract, a second proposal system, new retrieval infrastructure, or mutation of bundled FIBO assets.

## Mandatory Core-Web Checkpoint After Slice 8

### Goal

Verify the complete non-AI collaborative web foundation before beginning native AI implementation.

This checkpoint is not an implementation slice. It must run on the accumulated local `main` after Slice 8 has been merged and post-merge verified.

The checkpoint requires:

- the verification commands below;
- a checkpoint completion artifact recording the results;
- a clean working tree after the artifact is committed according to the repository documentation rules, if the approved workflow requires documentation commits.

The checkpoint does not require:

- a new implementation branch;
- product-code changes;
- a slice implementation commit;
- a remote slice branch;
- a non-fast-forward merge.

If the checkpoint requires product-code changes to pass, stop and ask rather than treating those changes as checkpoint work.

### Required checks

- Project registry and filesystem allowlist.
- Development identity and contributor/reviewer roles.
- Read-only project adapters.
- Hierarchy and entity workbench.
- Shared staged set and proposal workflow.
- Two-client collaboration and reconnect behavior.
- Conflict detection and recovery.
- Asynchronous reasoning and SHACL jobs.
- FIBO external intent entering the common staged path.
- Existing CLI and VS Code compatibility.

### Verification commands

```bash
./gradlew test
./gradlew build
./gradlew check
(cd web-app && npm ci && npm test && npm run build)
(cd web-app && npm run test:e2e)
(cd vscode-extension && npm ci && npm test)
git diff --check
```

### Stop conditions

Do not begin Slice 10 if any required non-AI capability, compatibility check, two-client workflow, semantic-job lifecycle, or FIBO staging path is failing. Do not create an implementation branch or modify product code to repair a failed checkpoint without approval.

### Slice 10: AI Provider Boundary And Credential Safety

#### Goal

Create a provider-neutral AI boundary and per-user credential settings with safe status, test, replace, and remove behavior.

#### Allowed files/modules

- `web-server` AI provider interfaces, credential service/store interfaces, status/test routes, redacted errors, and tests.
- `web-app` settings/status controls and tests.
- A focused secret-management ADR if the development storage boundary requires clarification.

#### Dependencies

Slice 1’s server boundary and Slice 2 identity/session contracts; existing semantic read services for future context; Ktor client CIO/JSON only behind the provider interface.

#### Forbidden actions/modules

- Do not send keys to React, URLs, logs, analytics, collaboration events, proposals, or ontology files.
- Do not store a shared/funded key or require a key for non-AI features.
- Do not hard-code OpenAI semantics into core modules or add autonomous agents.
- Do not call a provider without an explicit user request.

#### Expected output

Users can add, replace, test, and remove their own key. Keys are stored only in server-side memory keyed by user session, never persisted to disk, and removed on logout, session expiration, explicit deletion, or server restart.

React stores only credential status after submission. Provider authorization headers and errors are redacted. One user's key lifecycle cannot affect another user's key. Non-AI routes work without credentials.

#### Tests

Credential lifecycle, logout/session-expiration/server-restart destruction, cross-user isolation, key non-persistence in response/log/event/snapshot fixtures, provider authorization/error redaction, missing-key behavior, explicit-request gating, and frontend status states.

#### Verification commands

```bash
./gradlew :web-server:test
(cd web-app && npm test && npm run build)
```

#### Stop conditions

Stop if the browser can read the key, if a secret can appear in a serialized object or log, if production secret storage is required, or if AI becomes required for ordinary workbench use.

### Slice 11: AI Assistant And Typed Edit Integration

#### Goal

Add a docked assistant that receives bounded semantic context and converts accepted suggestions into supported typed edits in the ordinary staging and review workflow.

#### Allowed files/modules

- `web-server` bounded AI context, request/response contracts, provider orchestration, suggestion validation, and tests.
- `web-app` assistant panel, context display, suggestion actions, staging integration, and tests.
- Additive `core-types` typed suggestion contracts only when required by the approved boundary.

#### Dependencies

Slices 3, 5, 7, and 10; provider/credential boundary; existing typed edit translators.

#### Forbidden actions/modules

- Do not allow arbitrary RDF/Turtle output to be applied.
- Do not let AI write files, apply proposals, bypass validation, or bypass human approval.
- Do not expose unrelated project content or secrets as context.
- Do not build autonomous agents, schema RAG, or a second semantic interpretation layer.

#### Expected output

The initial operation types are:

- `EXPLAIN_ENTITY`
- `EXPLAIN_INFERENCE`
- `EXPLAIN_SHACL_RESULT`
- `SEARCH_FIBO`
- `SUGGEST_DEFINITION`
- `SUGGEST_SUPERCLASS`
- `SUGGEST_PROPERTY`
- `SUGGEST_EXTERNAL_REUSE`
- `SUMMARIZE_PROPOSAL`

Responses separate narrative answer, evidence, asserted facts, inferred facts, FIBO results, typed suggestions, uncertainty, and warnings.

The assistant uses an allowlisted context builder. Ontology labels, definitions, annotations, source text, and FIBO content are untrusted data and cannot become system instructions. Trusted policy, user request, and ontology context are structurally separated. Unrelated project data and full source files are excluded by default.

Only supported typed suggestions become ordinary staged edits after explicit user action. AI may explain possible SHACL rules but cannot stage a SHACL mutation without an approved typed SHACL backend contract.

#### Tests

Operation-type coverage, response-field separation, context allowlisting, prompt-injection fixtures, unrelated-project exclusion, suggestion schema validation, typed-edit conversion, unsupported suggestion rejection, SHACL-mutation rejection, AI failure/retry, approval gating, key isolation, and UI staging/review behavior.

#### Verification commands

```bash
./gradlew :web-server:test
(cd web-app && npm test && npm run build)
```

#### Stop conditions

Stop if ontology text can alter trusted provider instructions, if unrelated data or secrets can enter context, if the provider can return an unbounded mutation, if suggestions can be applied without review, or if the web client must contain semantic logic.

### Slice 12: UI Hardening, Accessibility, And Activity Integration

#### Goal

Make the workbench credible for repeated use by completing activity surfaces, tabs, status badges, keyboard navigation, focus behavior, responsive desktop panes, and clear loading/error/conflict states.

#### Allowed files/modules

- `web-app/src/` UI components, state, styles/design tokens, accessibility tests, and interaction tests.
- `web-server` response/status mapping adjustments only when necessary to render already-approved state.

#### Dependencies

Slices 4-11.

#### Forbidden actions/modules

- Do not add new semantic capabilities, retrieval systems, authentication, persistence, or AI operations.
- Do not replace existing clients or make raw RDF/IRIs the default user experience.
- Do not hide conflict, stale, failed, or approval-required states.

#### Expected output

Users can understand current graph state, shared activity, staged/proposal status, job status, FIBO context, and AI credential status at a glance, with accessible controls and predictable recovery paths.

#### Tests

Keyboard-only flows, focus order, accessible names/roles, contrast and status announcements, tab/activity behavior, loading/error/retry/conflict states, and representative browser interactions.

#### Verification commands

```bash
(cd web-app && npm test && npm run build)
```

If any permitted `web-server` or Kotlin file is changed, also run:

```bash
./gradlew :web-server:test
./gradlew test
```

#### Stop conditions

Stop if accessibility or status work requires hiding a semantic error, if the UI introduces unapproved workflow behavior, or if visual polish requires changing core contracts.

### Slice 13: End-To-End Phase 6 Regression And Documentation Summary

#### Goal

Prove that the browser workbench is additive and usable across the full product journey, then document the actual Phase 6 result and known gaps.

#### Allowed files/modules

- `web-server` integration tests and test fixtures.
- `web-app/e2e/`, Playwright configuration, and test fixtures.
- Existing Kotlin and VS Code tests only when stale because of an approved additive contract.
- `docs/phase-summaries/phase-6-summary.md`.

#### Dependencies

Slices 1-12.

#### Forbidden actions/modules

- Do not add product behavior that was not implemented in earlier slices.
- Do not weaken or delete regression tests to make the phase pass.
- Do not push `main`, change deployment infrastructure, or add production persistence/authentication.

#### Expected output

The full browser journey works for project open, navigation, entity inspection, typed edit staging, preview/review, collaboration, reasoning/SHACL jobs, FIBO reuse, bounded AI suggestion staging, approval/apply, reload, and rollback. The summary describes actual implementation and mismatches rather than planned behavior.

#### Tests

Two-client collaboration journey, proposal conflict/recovery and apply refresh, reasoning/SHACL job lifecycle, FIBO browse/reuse through common staging, AI credential safety and typed staging, prompt-injection safety, server shutdown/session cleanup, browser accessibility smoke tests, existing CLI and VS Code regression tests, examples, and all existing Gradle tests.

#### Verification commands

```bash
./gradlew test
./gradlew build
./gradlew check
(cd web-app && npm ci && npm test && npm run build)
(cd web-app && npm run test:e2e)
(cd vscode-extension && npm ci && npm test)
git diff --check
```

#### Stop conditions

Stop if any existing client regression fails, a required browser journey cannot be verified, any secret exposure test fails, or any full-phase verification is failing. Do not mark Phase 6 complete while a required capability is only mocked or documented.

## Test Plan

### Kotlin server and contract tests

- DTO serialization and backward-compatible response mapping.
- Direct delegation to existing project, symbol, proposal, reasoning, SHACL, and FIBO services.
- Deterministic ordering and stable fingerprints.
- HTTP validation, error status mapping, and stale/conflict behavior.
- WebSocket event schema, ordering, reconnect snapshots, and two-session isolation.
- Job lifecycle, cancellation, invalidation, and stale-result suppression.
- AI context boundaries, provider errors, credential isolation, and approval gates.
- Server shutdown, session expiration, WebSocket cleanup, semantic-job cancellation, API-key destruction, expected staged-state loss, and source-file integrity.

### Frontend tests

- Routing, hierarchy, tabs, label-first rendering, and IRI-on-demand details.
- Staged/proposal state transitions and reload after apply/reject.
- Presence, activity, reconnect, conflicts, and shared staged changes.
- Reasoning/SHACL progress and status states.
- FIBO browse, details, dependencies, and reuse staging.
- AI settings, missing/invalid/rate-limited states, bounded context, and typed suggestions.
- Accessibility, keyboard navigation, focus, and user-visible error recovery.

### End-to-end and compatibility tests

Use a deterministic fixture based on `examples/simple-ontology`. Run at least two browser clients against one server session. Preserve existing CLI, VS Code, Gradle, OWL, SHACL, FIBO, proposal, and source-reload tests. The browser test environment must not require a real provider key; provider calls use a controlled fake or local test adapter.

## Verification Commands

Slice-level commands are listed above. The minimum full-phase gate is:

```bash
./gradlew test
./gradlew build
./gradlew check
(cd web-app && npm ci && npm test && npm run build)
(cd web-app && npm run test:e2e)
(cd vscode-extension && npm ci && npm test)
git diff --check
```

The final verification must also exercise direct existing CLI commands and the example project, without routing them through the new web server. If the web app is disabled or removed, the existing Kotlin and VS Code workflows must remain buildable and testable.

## Risks And Assumptions

- Existing Phase 1-5 services expose enough reusable APIs for web adapters. If not, extend contracts additively and stop before moving semantic logic into `web-server`.
- A project registry and filesystem-root policy can be supplied for development without introducing production tenancy or authentication.
- In-memory sessions and jobs are acceptable for Phase 6 development and testing; restart persistence is explicitly deferred.
- Two browser sessions can be represented by a development identity model with stable user ID, display name, avatar, session, contributor, and reviewer fields.
- Reasoning and SHACL services can be invoked safely from bounded asynchronous jobs and can report graph fingerprints.
- FIBO browsing/reuse services and immutable assets remain available through their existing Phase 5 boundary.
- The initial AI provider is OpenAI-compatible, but the application boundary must remain provider-neutral. The provider adapter must be replaceable.
- The browser will not receive a provider key. Development secret storage must sit behind an interface that can later be replaced by a secure production store.
- Exact DTO field names are finalized in Slice 2, but the `/api/v1` and `/ws/v1` boundaries, structured errors, stable IDs, pagination, fingerprints, and idempotency requirements are fixed.
- Every hierarchy/search/activity/AI-context contract must enforce bounded default and maximum sizes and return counts or continuation metadata.

## Remaining ExecPlan Decisions

The following narrow implementation details may be resolved during the relevant slices without changing approved product behavior:

- Exact development project-registry configuration file format.
- Exact predefined development user fixtures and login presentation.
- Exact in-memory session expiration values.
- Exact hierarchy/search page-size defaults and enforced maxima.
- Exact phase/status labels exposed by reasoning and SHACL jobs.
- Exact provider-neutral AI response DTO field names.
- Exact AI context item and token limits.
- Exact production secret-management interface reserved for a later phase.

The following are fixed and are not open:

- No Yjs or CRDT framework in Phase 6.
- Semantic jobs are project-scoped and deduplicated by project and fingerprint.
- OpenAI keys are server-side session-memory secrets only.
- Ktor uses one pinned server-local Kotlin JSON serialization approach with Entio-owned web DTOs.
## Rollback Notes

Each slice must be committed and pushed on its own branch, then merged locally into `main` with a visible non-fast-forward merge. A slice can be rolled back by reverting its merge commit without rewriting history. `web-server` and `web-app` must remain removable as additive surfaces; no database migrations or ontology asset rewrites may be introduced. If a slice needs a breaking change to an existing client or semantic contract, stop and leave the slice unmerged until the scope is revised.

## Definition Of Done

Phase 6 is complete only when:

- All twelve implementation slices and the mandatory core-web checkpoint have been completed in order. Each implementation slice has been independently verified, committed, pushed, and merged locally; the checkpoint has its required completion artifact and verification record.
- The web-server is an additive Ktor adapter over existing Kotlin semantic services.
- The React workbench supports navigation, entity details, typed staging, proposal review/application, collaboration, reasoning/SHACL jobs, FIBO reuse, and bounded AI suggestions as specified.
- Two browser sessions can observe shared activity and one shared staged set; disconnect/reconnect is safe, and concurrent baseline changes produce explicit stale/conflict states with discard and re-preparation paths.
- AI credentials remain per-user in server-side session memory and are destroyed on logout, expiration, removal, or restart; no key appears in browser state, logs, URLs, events, proposals, snapshots, or ontology files.
- AI operations use the approved typed operation set, treat ontology content as untrusted context, and cannot write or apply ontology changes outside the existing typed-edit and human-review workflow.
- `./gradlew test`, `./gradlew build`, `./gradlew check`, web tests/build/e2e, VS Code tests, and the example-project regression all pass.
- Existing CLI and VS Code behavior remains compatible.
- `docs/phase-summaries/phase-6-summary.md` records what was actually implemented, mismatches, limitations, and follow-up work.
- The working tree is clean and `main` is not pushed by the implementation process.
