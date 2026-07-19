# Feature Spec: Phase 8 Scalable AI Ontology Workflow Orchestration

## Status

Draft for review in the active Phase 8 stage. This document specifies Phase 8 behavior but does not authorize implementation. It is derived from `docs/architecture/phase-8-scope.md` and preserves the completed Phase 1 through Phase 7.5 contracts.

## Problem

Entio's Phase 7 and Phase 7.5 assistant can hold project-scoped conversations, inspect bounded context, call approved capabilities, prepare private typed drafts, run deterministic analysis, and submit a reviewed draft into the ordinary proposal workflow. That run-oriented tool loop is sufficient for focused work, but it is not a reliable source of truth for a task that spans many entities, decisions, draft revisions, validation cycles, or user checkpoints.

For medium and large ontology work, the current assistant can lose progress in conversation history, expose too many similar capabilities, repeat retrieval, discover validation failures too late, and exhaust per-turn limits before producing a coherent result. Sending more ontology content or granting broader model authority would worsen safety and scalability rather than solve the workflow problem.

Phase 8 therefore needs a server-owned task workspace and orchestrator. The model may help understand, plan, compare, draft, repair, and explain, while Kotlin owns task state, transitions, scope, capability bundles, deterministic retrieval, typed edits, validation, checkpoints, review submission, and every safety limit.

## Central Product Promise

> A user can ask Entio AI to perform a simple or complex ontology task in plain language. Entio AI can inspect, plan, draft, validate, repair, and explain the work, but only a human can approve or reject the final proposal and trigger source changes.

## Goals

- Represent every meaningful AI request as an Entio-owned task whose structured state, rather than chat history, is authoritative.
- Support explanation, discovery, focused edit, multi-edit, refactoring, domain-modeling, repair, review, and project-analysis tasks.
- Classify tasks as simple, medium, or large and apply proportionate planning, confirmation, batching, and validation behavior.
- Build deterministic, fingerprinted project maps and bounded ontology neighborhoods suitable for projects with hundreds or thousands of entities.
- Construct fresh, bounded context packages without sending complete ontology sources or full project graphs by default.
- Expose only the capability bundle permitted for the current task stage and work package.
- Add higher-level Entio capabilities that produce existing typed edits and reduce repetitive low-level model calls.
- Model medium and large work as dependency-ordered work packages with visible, server-owned progress.
- Build one private task draft in bounded batches and validate each meaningful batch or work package.
- Convert deterministic validation, reasoning, and SHACL failures into structured repair packets.
- Permit bounded repair of the task's private draft without weakening deterministic rules or changing applied project state.
- Pause safely for material clarification, plan confirmation, destructive decisions, business-meaning choices, cancellation, staleness, and limits.
- Produce a complete, fingerprinted final review package and submit it through the existing human-review workflow.
- Make follow-up explanations and revisions use task evidence, work-package results, and draft history.
- Add permanent deterministic evaluations for small, medium, large, explanation, performance, and safety tasks.
- Preserve all Phase 1 through Phase 7.5 behavior and module dependency direction.

## Non-Goals

Phase 8 does not include:

- Autonomous approval, rejection, application, rollback, or source publication.
- Direct ontology or SHACL file writes by AI.
- Raw Turtle, arbitrary SPARQL updates, shell access, arbitrary filesystem access, or unrestricted network access.
- Project-configuration, identity, role, or permission editing.
- Automatic Git operations or background ontology changes without a user request.
- Independent multi-agent authority or recursive AI task creation.
- Document ingestion, cross-document entity resolution, embeddings, or a new external ontology catalog.
- A new graph database, production queue, or durable production task store.
- Production identity, tenancy, billing, budgets, administrator controls, or deployment observability.
- Replacing deterministic search, validation, semantic diff, OWL reasoning, or SHACL with model judgment.
- Sending a complete ontology, complete hierarchy, source file, or unrestricted provider payload to the model by default.
- Silent plan changes, silent permission expansion, or model switching during a task.
- New Gradle modules, a new server framework, a new dependency-injection framework, or semantic policy in React.

Current phase-level and historical non-goals remain governed by `AGENTS.md`.

## Existing Foundation

Phase 8 builds on, rather than replaces:

- Phase 7 conversations, runs, immutable scopes, capability registry snapshots, private drafts, revisions, analysis, audits, SSE events, and review submission.
- Phase 7.5 user-specific OpenAI discovery, explicit verification and selection, and immutable run-bound model configuration.
- Existing project loading, semantic descriptions, search, hierarchy, usage, reasoning, SHACL, FIBO, graph diff, staging, proposals, atomic application, reload, and rollback services.
- Existing Ktor development identity and in-memory server state.
- Existing React workbench, assistant, proposal review, collaboration, reasoning, SHACL, and FIBO surfaces.
- Deterministic fake providers, Ktor mock transport, copied semantic fixtures, and existing browser test infrastructure.

## Fixed Architecture Decisions

### Kotlin owns task state and transitions

`web-server` owns task identity, scope, status, workspace revision, plan, work packages, checkpoints, limits, progress, staleness, and review readiness. The model may propose structured state changes only through stage-appropriate capabilities. React renders server state and submits explicit user actions; it does not infer validity or advance the workflow locally.

### Chat is supporting context

Conversation messages remain useful input and explanation history, but an `AiTaskWorkspace` is authoritative for objective, assumptions, questions, selected entities, plan, current package, completed and failed packages, private draft, analysis history, repairs, and review package.

### Task state remains process-memory state

Phase 8 task and workspace stores are private, per-user, project-scoped, and in memory. They may resume during the same server session and are lost on restart. No database or production queue is added.

### Existing semantic services remain authoritative

Project maps, neighborhoods, typed changes, preview, validation, semantic diff, reasoning, SHACL, proposal submission, application, reload, and rollback delegate to existing Kotlin services. Phase 8 does not implement a second RDF, OWL, SHACL, or proposal engine.

### Higher-level capabilities still emit typed edits

Composite preparation and repair capabilities may coordinate retrieval and prepare multiple operations, but their output is a bounded collection of existing typed Entio requests. Raw RDF is never an internal shortcut.

### Capability exposure is stage-specific

The orchestrator chooses a frozen bundle for each provider step from exploration, planning, ontology editing, SHACL, analysis, repair, or help capabilities. The model cannot add capabilities, select a broader bundle, approve, apply, or access reviewer controls.

### One task uses one bound model

The verified Phase 7.5 model binding is captured when task execution begins and remains stable through provider calls for that task. A changed user selection affects a future task or an explicitly restarted task, never an active task silently.

### Work packages are the progress unit

Medium and large tasks execute dependency-ordered work packages. Progress reports completed packages and the current stage; it does not invent percentages. A blocked required dependency prevents later packages from running.

### Human checkpoints are explicit commands

Clarification answers, plan confirmation, checkpoint continuation, cancellation, review submission, approval, and rejection are explicit user actions. Plan confirmation is not proposal approval. Review submission does not grant approval or application authority.

## Proposed Behavior

### 1. Task creation and classification

Every meaningful assistant request creates an `AiTask` associated with the current user, project, conversation, allowed sources, project fingerprint, verified model binding, and objective. Empty or non-meaningful input may remain a clarification without creating a mutating task.

The task classifier assigns:

- a task family: explanation, search and discovery, focused edit, multi-edit, refactoring, domain modeling, repair, review, or project analysis;
- a size: simple, medium, or large;
- an initial capability stage;
- whether clarification or plan confirmation is required.

Classification is structured, bounded, auditable, and checked by Kotlin. Out-of-scope requests fail before mutation capabilities are exposed.

### 2. Task and workspace state

The server stores an immutable/versioned task representation containing at least:

- task, project, user, and conversation IDs;
- objective, family, size, scope, and status;
- project and model fingerprints/versions;
- assumptions and open questions;
- selected entity references;
- plan and work-package records;
- current, completed, blocked, and failed work packages;
- private draft ID and fingerprint;
- validation, reasoning, SHACL, repair, and review references;
- limits and counters;
- created and updated timestamps.

The workspace additionally owns context references, unresolved references, draft revision history, analysis history, repair history, checkpoints, and final review package. Updates use expected workspace revisions so concurrent or replayed commands cannot silently overwrite newer state.

### 3. State machine

Supported statuses are:

- `UNDERSTANDING`
- `AWAITING_CLARIFICATION`
- `PLANNING`
- `AWAITING_PLAN_CONFIRMATION`
- `READY_TO_EXECUTE`
- `EXECUTING`
- `VALIDATING`
- `RUNNING_REASONING`
- `RUNNING_SHACL`
- `REPAIRING`
- `PAUSED`
- `READY_FOR_REVIEW`
- `SUBMITTED_FOR_REVIEW`
- `FAILED`
- `CANCELLED`
- `STALE`
- `LIMIT_REACHED`

Kotlin defines the legal transition table and action preconditions. Terminal tasks cannot mutate. Stale tasks cannot continue editing until context is refreshed and the draft is revalidated. The model cannot directly assign status.

### 4. Simple, medium, and large behavior

- Simple tasks may skip a separate plan when unambiguous, use a small bundle, create a bounded draft, and run final validation.
- Medium tasks receive a short plan, bounded work packages, batch validation every 10 to 20 draft items and at package boundaries, and checkpoints when impact becomes material.
- Large tasks require a structured plan, explicit user confirmation, dependency-ordered packages, incremental validation, bounded repair, visible progress, and a final review package.

The initial policy supports at most 12 work packages, 100 draft items per task, and 20 draft items per batch. Exact limits are server-owned and returned as structured policy data.

### 5. Project map

Entio builds a deterministic compact project map keyed by project fingerprint. It includes project identity, source IDs and roles, namespaces, entity counts, bounded top-level classes/domain areas, commonly used external ontologies, reasoning and SHACL availability/status, staged-change counts, and discovered naming/IRI conventions.

The map contains no complete source file and no unbounded entity listing. Stable ordering and size limits make repeated maps identical for the same fingerprint and policy version.

### 6. Search and ontology neighborhoods

Layered retrieval uses exact/normalized lookup, existing deterministic semantic search, approved FIBO search, and model comparison only over returned candidates.

A bounded neighborhood may include the selected descriptor, direct parents and children, domain/range relations, related properties and individuals, asserted/inferred markers, relevant shapes and findings, usage, source, nearby approved external candidates, and staged impacts. Requests are limited by direction, depth, entity count, source, bytes, and fingerprint.

The model may explicitly expand parents, children, properties, usage, constraints, individuals, or FIBO candidates. Each expansion remains paginated and bounded; it does not unlock full-project context.

### 7. Context packages and freshness

Each provider step receives only the context required for its current work package:

- task and current workflow state;
- project map reference and bounded content;
- selected entities and relevant neighborhoods;
- relevant SHACL, reasoning, staged-change, and FIBO evidence;
- project rules, assumptions, open questions, and draft summary;
- current capability bundle.

Every package records project, task, workspace, work-package, draft, reasoning, and SHACL fingerprints where applicable. The default initial entity limit is 20 and explicit expansion may reach 50. Search returns at most 10 FIBO candidates and relevant SHACL findings are bounded to 20.

If a project fingerprint changes, Entio marks affected tasks stale, stops mutation, invalidates unsafe caches, refreshes context, and revalidates the private draft. If meaning or selected identities changed, user confirmation is required before resumption.

### 8. Capability bundles

The server defines versioned bundles:

- Exploration: project map, deterministic search, entity summary/neighborhood/usage, comparison, and FIBO reads.
- Planning: exploration plus structured plan, estimates, questions, and assumptions.
- Ontology editing: approved typed ontology, metadata, hierarchy, property, individual, assertion, deletion, and external-reuse drafts.
- SHACL: approved typed shape and constraint drafts.
- Analysis: preview, validation, semantic diff, reasoning, SHACL, impact, and blocking findings.
- Repair: repair packet reads, bounded draft replacement/removal/addition, and analysis reruns.
- Help: current screen/actions, Entio help, error codes, and workflow state.

Bundle selection follows task status and work-package policy. Explanation tasks receive no mutation tools; repair steps receive no review, approval, application, configuration, filesystem, shell, or unrestricted network tools.

### 9. Plans and work packages

A structured plan contains goal, assumptions, open questions, expected sources, estimated total edits, risk flags, and ordered work packages. Each package contains an ID, title, purpose, dependencies, expected entities/edit count, allowed bundle, confirmation requirement, status, result/evidence, validation outcome, and retry count.

Plan revisions are explicit, versioned, and visible. Large, destructive, hierarchy-refactoring, cross-source, material external-reuse, high-impact SHACL, or user-requested plans require confirmation. Silent plan changes are rejected.

### 10. Clarification and checkpoints

Questions are limited to choices that materially change ontology meaning, identity, source, reuse, deletion/deprecation, domain/range, shape target, migration, or repair. Answers update task state and resume the same work package.

Planning checkpoints show the goal, package list, estimated edits, affected sources, external choices, destructive actions, and questions. Mid-task checkpoints occur for newly discovered destructive impact, major ambiguity, cross-source meaning, large SHACL impact, or business-semantic repair. Users may continue, revise, answer, pause, or cancel.

### 11. Work-package execution and batching

The orchestrator executes one package at a time after dependency and checkpoint checks. Provider steps receive the frozen package bundle and context. Mutations enter only the task's private draft through approved typed adapters.

Composite capabilities such as class model, property model, domain model, external reuse, refactor, and repair may reduce model calls. They perform deterministic lookup/validation and return typed draft items, ambiguity, evidence, and bounded summaries.

Medium tasks validate every 10 to 20 items and at package completion. Large tasks validate each package and run final combined analysis. A blocked or stale batch cannot mark its package complete or unlock dependents.

### 12. Incremental analysis

Each required batch runs the applicable sequence:

```text
typed edit preparation
→ preview
→ deterministic validation
→ semantic diff
→ reasoning when relevant
→ SHACL when relevant
→ fingerprinted package result
```

Results are `VALID`, `WARNING`, `BLOCKED`, `STALE`, `INCOMPLETE`, or `FAILED`. Model text cannot change a deterministic result, hide blocking findings, or mark an incomplete analysis ready.

### 13. Repair packets and repair loop

Deterministic failures produce repair packets containing stable finding ID, code, severity, package, affected draft items/entities, expected and actual conditions, source, evidence, allowed repair actions, and deterministic candidate references where available.

The model may repair only the current private draft using the repair bundle. Original findings and every draft revision remain available. Each package permits at most three repair cycles and a task at most eight by default. Analysis reruns after repair. Entio pauses for the user when several repairs are semantically plausible and never weakens validation rules merely to pass.

### 14. Long-running execution

Large tasks execute asynchronously inside the existing server process with authoritative task status, private progress events, cancellation, pause/resume, clarification and confirmation waits, and safe failure. Work-package events replace fake percentage completion.

Only one mutating task per user/project may be active. Up to three read-only explanation tasks may be active. Default limits also include 30 provider tool calls per package, 200 per task, bounded package/task time, bounded context bytes/entities, and no recursive task creation.

When a limit is reached, Entio preserves the workspace, reports the exact limit category, and lets the user narrow or start a new task. It does not submit incomplete work automatically.

### 15. Undo, revision, and follow-up

Within the private workspace a user may undo the latest draft item or work package, revise a package, remove a proposed entity, change an assumption, select a different external concept, and rerun analysis. Undo never changes applied project state.

Follow-up answers use task state, evidence references, plan versions, package results, findings, and draft history. Requests such as “why,” “what changed,” “what failed,” “show hierarchy only,” or “continue” must not be answered solely by replaying old chat text.

### 16. Project-wide analysis

Project analysis may find missing definitions, duplicate labels, likely duplicate candidates, unused classes/properties, missing domains/ranges, weak constraints, suspicious hierarchy depth, inconsistent naming, and stale annotations. Kotlin performs bounded server-side scans and returns deterministic summaries or candidate sets. The model receives the results, not every raw entity.

### 17. Final review package and submission

After all required packages and final combined analysis pass, Entio creates an immutable/fingerprinted review package containing objective, plan summary, completed packages, draft/proposal fingerprints, change summary, semantic diff, validation, reasoning, SHACL, sources, external dependencies, assumptions, warnings, unresolved questions, rationale, submitter, and timestamps.

The user explicitly submits the package through the existing Phase 7 review submission service. Submission imports the exact typed private draft into normal shared staging and returns the authoritative proposal review route. A separate authorized human uses existing review controls to approve or reject and, if approved, apply. AI never receives those controls.

### 18. Collaboration and privacy

Task conversations, plans, workspace revisions, draft history, repair history, provider payloads, and pre-submission evidence are private to the task owner. Other users see only the ordinary submitted proposal, AI attribution, submitting user, task summary/rationale, and affected sources. Another user's applied project change may make a task stale but does not expose either user's private state.

### 19. Audit and traceability

Audits record task/user/project IDs, model and policy versions, bundle per step, tool calls, Entio result references, plan and draft revisions, deterministic analyses, repair cycles, checkpoints, final proposal reference, timing, and safe usage metadata.

Audits never record API keys, authorization headers, hidden chain of thought, unrestricted raw provider payloads, another user's private task, or unrelated project content.

### 20. React task experience

The workbench adds an AI task surface that presents:

- task objective, status, bound model, project, package progress, pause, resume, and cancel;
- plan packages, dependencies, estimates, confirmations, assumptions, and questions;
- selected context, evidence, hierarchy, properties, constraints, and FIBO candidates;
- draft items grouped by package with source, rationale, validation, and revision history;
- validation, diff, reasoning, SHACL, repair packets, warnings, and unresolved issues;
- final review package and link to the authoritative proposal review.

The UI uses accessible live status, preserves non-AI workbench usability during task/provider failure, and refetches authoritative state after event gaps. It does not expose source mutation or model reviewer authority.

### 21. Evaluation suite

Phase 8 adds deterministic provider scripts and copied/generated fixtures for small banking, medium lending, 500-entity, 1,000-entity, hierarchy conflict, SHACL failure, FIBO reuse, stale task, and collaboration conflict cases.

Evaluations measure task completion, entity/source selection, duplicate avoidance, typed-edit validity, validation/reasoning/SHACL outcomes, repair success, clarification and plan quality, provider/tool calls, duration, token usage, unauthorized-action attempts, and final proposal completeness. Automated tests do not use a real key or make external provider calls.

## Inputs

### User inputs

- Plain-language objective and follow-up messages.
- Clarification answers and plan revisions.
- Explicit plan/checkpoint confirmation, pause, resume, cancellation, and review submission actions.
- Explicit private-draft revisions or undo requests.
- Existing selected/verified model and current Entio identity/project context.

### Server-owned inputs

- Project, source, permission, feature, and fingerprint scope.
- Existing semantic descriptors, search, hierarchy, usage, reasoning, SHACL, FIBO, staging, and proposal services.
- Versioned task policy, transition table, capability bundles, retrieval limits, batching limits, repair limits, and provider limits.
- Current private draft, analysis references, project map, and cache entries tied to fingerprints.
- Current Entio help and feature metadata.

The browser and model cannot provide provider endpoints, capability names outside the selected bundle, permission changes, reviewer authority, fingerprints, or server limit overrides.

## Outputs

Versioned server outputs include:

- Task identity, objective, type, size, status, scope summary, model descriptor, revision, and timestamps.
- Structured plan, work packages, assumptions, questions, confirmations, and progress.
- Bounded project maps, context/neighborhood references, evidence, and freshness metadata.
- Private draft summary/items/revisions grouped by package.
- Incremental and final validation, semantic diff, reasoning, SHACL, findings, and repair packets.
- Ordered private task events with resynchronization instructions.
- Complete final review package and ordinary proposal-review reference after explicit submission.
- Structured errors, limits, and permitted next actions.

Outputs never include credentials, authorization headers, complete source files, unrestricted raw graph/project content, hidden chain of thought, raw provider payloads, private state belonging to another user, or review/application authority.

## Validation Behavior

Validation is deterministic for the same task policy, project and draft fingerprints, workspace revision, and request:

- Task commands must match user, project, task, expected workspace revision, permissions, and current status.
- Task transitions must be present in the Kotlin transition table.
- Work-package IDs are unique, dependencies exist, and the dependency graph is acyclic.
- Package estimates, package count, batch size, draft size, repair cycles, provider calls, elapsed time, and context size remain within policy.
- A package cannot execute before required dependencies and confirmations complete.
- A capability call must belong to the frozen current-stage bundle and immutable task scope.
- Every draft operation must pass the existing typed preparation path; no raw graph mutation is accepted.
- Context, analysis, cache, and review references must match current fingerprints.
- A blocked, stale, incomplete, failed, or limit-reached result cannot be represented as complete or ready for review.
- Review readiness requires every required package complete and current final preview, validation, diff, reasoning, and SHACL results as applicable.
- Submission imports exactly the current private draft fingerprint and never grants approval or application authority.
- Repeated commands with the same idempotency key do not duplicate provider calls, task transitions, draft mutations, analyses, or submission.

## Error Behavior

Structured task errors include:

- `AI_TASK_NOT_FOUND`
- `AI_TASK_STALE`
- `AI_TASK_CONFLICT`
- `AI_TASK_LIMIT_REACHED`
- `AI_TASK_ALREADY_RUNNING`
- `AI_PLAN_CONFIRMATION_REQUIRED`
- `AI_CLARIFICATION_REQUIRED`
- `AI_CONTEXT_LIMIT_REACHED`
- `AI_WORK_PACKAGE_BLOCKED`
- `AI_VALIDATION_BLOCKED`
- `AI_REASONING_INCOMPLETE`
- `AI_SHACL_BLOCKED`
- `AI_REPAIR_LIMIT_REACHED`
- `AI_MODEL_UNAVAILABLE`
- `AI_PERMISSION_DENIED`
- `AI_UNSUPPORTED_WORKFLOW`
- `AI_REVIEW_PACKAGE_NOT_READY`

Errors are redacted, include safe next actions where possible, and preserve the workspace unless cancellation or explicit deletion applies. Provider failure cannot be represented as successful Entio validation. Cross-user and cross-project lookups return non-disclosing failures. A server restart reports that in-memory task state is unavailable rather than reconstructing authoritative progress from chat.

## Web Contract Behavior

Phase 8 adds project-scoped task resources under `/api/v1/projects/{projectId}/ai/tasks` while retaining Phase 7 conversation/run/draft routes for compatibility during migration:

```text
POST   /api/v1/projects/{projectId}/ai/tasks
GET    /api/v1/projects/{projectId}/ai/tasks/{taskId}
POST   /api/v1/projects/{projectId}/ai/tasks/{taskId}/messages
POST   /api/v1/projects/{projectId}/ai/tasks/{taskId}/clarifications
PUT    /api/v1/projects/{projectId}/ai/tasks/{taskId}/plan
POST   /api/v1/projects/{projectId}/ai/tasks/{taskId}/plan/confirm
POST   /api/v1/projects/{projectId}/ai/tasks/{taskId}/execute
POST   /api/v1/projects/{projectId}/ai/tasks/{taskId}/pause
POST   /api/v1/projects/{projectId}/ai/tasks/{taskId}/resume
POST   /api/v1/projects/{projectId}/ai/tasks/{taskId}/cancel
GET    /api/v1/projects/{projectId}/ai/tasks/{taskId}/workspace
GET    /api/v1/projects/{projectId}/ai/tasks/{taskId}/draft
GET    /api/v1/projects/{projectId}/ai/tasks/{taskId}/analysis
GET    /api/v1/projects/{projectId}/ai/tasks/{taskId}/review-package
GET    /api/v1/projects/{projectId}/ai/tasks/{taskId}/events
POST   /api/v1/projects/{projectId}/ai/tasks/{taskId}/submit
```

Mutation and provider-triggering requests require idempotency keys and expected workspace revisions. Private events use ordered IDs, bounded retention, reconnect cursors, and authoritative-state resynchronization consistent with the Phase 7 SSE boundary.

## Test Cases

### Task state and ownership

- Every legal state transition succeeds and every illegal transition is rejected.
- Pause/resume, cancellation, clarification, plan confirmation, stale invalidation, and limit preservation behave deterministically.
- Concurrent/replayed commands cannot duplicate state or draft mutations.
- Cross-user and cross-project task/workspace access is rejected without disclosure.

### Retrieval and context

- Project maps and neighborhoods are stable for the same fingerprint and bounded by policy.
- Exact, semantic, usage, hierarchy, SHACL, and FIBO retrieval preserve source and asserted/inferred markers.
- Expansion is paginated and bounded; full source or project content never appears in provider context.
- Cache reuse and invalidation follow project/draft/reasoning/SHACL fingerprints.
- 500- and 1,000-entity fixtures stay within context limits and acceptable deterministic budgets.

### Planning and bundles

- Unambiguous simple tasks skip unnecessary planning.
- Medium tasks create short package plans; large and destructive tasks require explicit confirmation.
- Plan dependencies are acyclic, revisions visible, and blocked dependencies prevent execution.
- Only the stage bundle is exposed; explanation cannot mutate, repair cannot submit/apply, and the model cannot expand a bundle.

### Execution, analysis, and repair

- Packages execute in dependency order and drafts batch at configured thresholds.
- Composite capabilities yield only existing typed Entio operations.
- Incremental and final analysis match fingerprints and reject stale results.
- Blocking findings generate complete repair packets.
- Repair changes only private draft state, preserves findings/history, reruns analysis, and stops at limits.
- Equivalent ambiguous repairs pause for user meaning rather than choosing silently.

### Review and safety

- Review packages contain all required current references and cannot be created from incomplete work.
- Explicit submission imports the exact typed draft into existing shared staging.
- No source changes occur before separate human approval and application.
- AI cannot approve, reject, apply, roll back, edit configuration, access shell/files/secrets, issue raw RDF/SPARQL, or cross scopes.
- Prompt injection in user text, labels, definitions, help, FIBO, findings, and tool output cannot widen scope or bundles.

### UI and events

- Task header, plans, checkpoints, package progress, context, draft, analysis, repair, and review states render accessibly.
- Event reconnect and retention gaps recover by refetching authoritative state.
- Cancellation and provider/task failures never appear as success and do not disable non-AI workbench features.

### Regression and evaluation

- Phase 1 through Phase 7.5 Kotlin, React, VS Code, and browser tests remain green.
- Permanent deterministic scenarios cover small, medium, large, explanation, stale, repair, performance, and adversarial tasks.
- No automated test requires a live credential or unrestricted network.

## Acceptance Criteria

Phase 8 is accepted when:

1. Meaningful requests create authoritative, private, in-memory tasks with Kotlin-enforced transitions and immutable scope/model provenance.
2. Simple tasks remain reliable and proportionate; medium tasks plan and batch; large/high-impact tasks require confirmation and dependency-ordered packages.
3. Project maps, neighborhoods, context expansion, and project-wide scans are deterministic, bounded, fingerprinted, and proven on at least a 500-entity fixture without sending the whole ontology.
4. Capability exposure changes by workflow stage and cannot be expanded by model or client input.
5. Higher-level preparation and repair capabilities output only existing typed Entio operations.
6. A task can produce at least 50 typed edits in batches while staying within the initial 100-item task limit.
7. Validation occurs incrementally and as a final combined pass; reasoning and SHACL run when relevant.
8. Blocking findings become structured repair packets and repair loops are bounded, draft-only, auditable, and revalidated.
9. Progress, clarification, confirmation, pause/resume, cancellation, staleness, limits, undo, and follow-up behavior use authoritative task state.
10. A complete final review package is created only from current successful analysis and enters the existing proposal workflow only after explicit user submission.
11. Only an authorized human can approve/reject and trigger application; AI has no source-writing, reviewer, configuration, shell, filesystem, raw RDF/SPARQL, permission, or unrestricted-network capability.
12. Permanent deterministic evaluation and adversarial suites pass, including 500- and 1,000-entity bounded-context fixtures.
13. All existing Phase 1 through Phase 7.5 verification remains green.

## Boundary Check

- The feature fits the active Phase 8 boundary in `AGENTS.md` and `docs/architecture/phase-8-scope.md`.
- Task orchestration remains in `web-server`; React remains presentation-only; semantic modules remain authoritative dependencies.
- The feature does not add durable production infrastructure, document ingestion, autonomous agents, new graph storage, or unapproved external integrations.
- Existing RDF, OWL, SHACL, reasoning, and diff libraries/services are reused; no standards framework is reinvented.
- Human approval and application remain outside AI authority.

## Open Questions

These questions do not block the spec but should be resolved in the implementation slices that inspect current code and performance evidence:

1. Which existing Phase 7 run/conversation contracts should become compatibility adapters over tasks, and which should remain independent for focused legacy conversations?
2. Should the first implementation support the full 1,000-entity fixture as an acceptance gate or treat 500 entities as the required gate and 1,000 as a measured non-blocking benchmark?
3. Which deterministic signals define “reasoning relevant” and “SHACL relevant” for package analysis without model discretion?
4. Should project-map “major domain areas” be limited to deterministic top-level hierarchy/source summaries initially, deferring richer clustering?
5. What elapsed-time budgets are appropriate per package and task after fixture benchmarking on supported development hardware?
6. Which existing typed operations can safely participate in the first composite `prepare_domain_model` capability, and which should remain explicit low-level tools until their atomic preparation behavior is proven?
7. How should a task with a valid private draft but an unavailable bound model be resumed: explicit restart with a newly selected model, or a user-confirmed task rebind that creates a new execution provenance segment?
