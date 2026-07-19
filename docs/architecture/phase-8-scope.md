# Phase 8 Scope

## Phase Name

**Phase 8: Scalable AI Ontology Workflow Orchestration**

## Status

Active planning scope

## Purpose

Phase 8 makes Entio AI capable of completing complex ontology work reliably, including work on projects with hundreds or thousands of entities.

The current AI can converse and call tools, but that is not enough for large tasks. A long ontology task may require many searches, decisions, edits, validations, repairs, and follow-up questions. The AI needs a structured Entio workflow around it.

Phase 8 should let the AI:

- understand a user's goal;
- inspect only the relevant part of the ontology;
- ask useful clarification questions;
- make a clear plan;
- carry out the plan in controlled steps;
- build a private draft containing many typed Entio edits;
- validate work while the draft is being built;
- run reasoning and SHACL checks;
- repair its private draft when deterministic checks fail;
- explain what it did and why;
- prepare one final proposal for human review;
- wait for a human to approve or reject it.

The AI must never write ontology or SHACL source changes without human approval.

## Central Product Promise

> A user can ask Entio AI to perform a simple or complex ontology task in plain language. Entio AI can inspect, plan, draft, validate, repair, and explain the work, but only a human can approve or reject the final proposal and trigger source changes.

## Problem

A simple chatbot-with-tools approach is not reliable enough for large ontology work.

Common problems include:

- too much context or too little context;
- too many similar tools available at once;
- weak memory of task progress;
- repeated tool-selection mistakes;
- difficulty working across many related entities;
- validation failures discovered too late;
- drafts that become too large to repair;
- unclear follow-up behavior;
- poor performance on ontologies with hundreds of entities;
- the model rebuilding task state from old chat messages instead of using authoritative workflow state.

Phase 8 should solve this by adding a server-owned task and workflow system.

## High-Level Workflow

```text
User request
→ Entio creates an AI task
→ Entio gathers bounded ontology context
→ AI classifies the task
→ AI asks clarifying questions when needed
→ AI creates a structured plan
→ user confirms high-impact plans
→ Entio executes the plan one work package at a time
→ each step uses approved Entio capabilities
→ draft is validated incrementally
→ deterministic failures become structured repair packets
→ AI repairs only its private draft
→ final validation, reasoning, SHACL, and semantic diff
→ AI explains the result
→ human approves or rejects
```

The LLM helps with:

- understanding;
- planning;
- sequencing;
- comparing existing concepts;
- choosing among approved options;
- explaining;
- revising its private draft.

Entio remains responsible for:

- project scope;
- permissions;
- entity lookup;
- deterministic search;
- typed edit creation;
- source selection rules;
- validation;
- semantic diff;
- reasoning;
- SHACL;
- proposal state;
- review authority;
- application;
- reload;
- rollback.

## Goals

Phase 8 should:

- support simple, medium, and large AI tasks;
- support ontologies with hundreds or thousands of entities;
- retrieve only the graph context relevant to the task;
- make task progress visible and server-owned;
- separate understanding, planning, execution, validation, repair, and review;
- expose only the tools needed for the current workflow stage;
- add higher-level Entio capabilities that reduce tool-call complexity;
- validate drafts in small batches;
- create structured repair information from deterministic failures;
- preserve useful follow-up context;
- support project-wide analysis without sending the whole project to the model;
- produce a complete final review package;
- preserve human approval as the only path to source changes;
- add a permanent Entio AI evaluation suite;
- preserve all existing Phase 1 through Phase 7.5 behavior.

## Non-Goals

Phase 8 must not add:

- autonomous approval;
- autonomous rejection;
- autonomous application;
- direct ontology-file writing;
- raw Turtle as an edit path;
- arbitrary SPARQL updates;
- shell access;
- arbitrary filesystem access;
- unrestricted network access;
- Entio project-configuration editing;
- permission or role changes;
- automatic Git operations;
- background AI changes without a user request;
- independent multi-agent authority;
- document ingestion unless separately approved;
- a new graph database;
- replacement of deterministic search, validation, reasoning, SHACL, or semantic diff with model judgment;
- sending the full ontology to the model by default;
- silent plan changes;
- silent model switching during a task;
- durable production task storage;
- production billing or administrator controls.

## Core Safety Rule

The AI may have broad proposal power but no application power.

It may:

- inspect;
- search;
- compare;
- explain;
- plan;
- create and revise private draft edits;
- validate;
- reason;
- run SHACL;
- repair;
- summarize;
- submit for human review.

It may not:

- approve;
- reject on behalf of the user;
- apply;
- roll back;
- write files directly;
- change Entio configuration;
- expand its permissions;
- call tools outside the current allowlist.

# 1. AI Task Model

Every meaningful request should create an Entio-owned `AiTask`.

Recommended fields:

```text
AiTask
- taskId
- projectId
- userId
- conversationId
- objective
- taskType
- scope
- status
- plan
- assumptions
- openQuestions
- selectedEntities
- currentWorkPackage
- completedWorkPackages
- failedWorkPackages
- privateDraftId
- validationState
- reasoningState
- shaclState
- finalProposalId
- createdAt
- updatedAt
```

The task object is the source of truth for workflow progress.

The chat transcript is supporting context, not authoritative task state.

## Task Statuses

Recommended statuses:

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

State transitions must be checked by Kotlin.

The model cannot move the task into an invalid state.

# 2. Task Types

Recommended task families:

## Explanation

- explain an entity;
- explain a relationship;
- explain an inference;
- explain SHACL;
- explain a validation report;
- explain a semantic diff;
- explain how to use Entio.

## Search And Discovery

- find an entity;
- find duplicates;
- find related properties;
- search FIBO;
- inspect a domain area;
- inspect project structure.

## Focused Edit

- create one class;
- update one definition;
- add one superclass;
- add one assertion;
- add one SHACL constraint.

## Multi-Edit Change

- create a class with related properties;
- add a group of individuals;
- update several domains or ranges;
- add several related constraints.

## Refactoring

- move classes in the hierarchy;
- rename concepts;
- split or replace concepts;
- delete a concept with dependency review;
- reorganize one domain area.

## Domain Modeling

- model a business domain;
- extend an existing ontology;
- create a subject area;
- reuse FIBO;
- add related classes, properties, constraints, and examples.

## Repair

- repair validation failures;
- repair an inconsistent proposal;
- repair SHACL failures;
- revise a failed draft.

## Review

- summarize a draft;
- explain impact;
- identify risks;
- identify missing parts.

## Project Analysis

- identify missing definitions;
- detect duplicate labels;
- identify likely duplicates;
- find unused classes or properties;
- identify weak domain/range coverage;
- identify classes with many individuals but no constraints;
- identify naming inconsistencies.

# 3. Simple, Medium, And Large Tasks

## Simple

Examples:

- add one definition;
- create one class;
- explain one inference.

Behavior:

- no separate plan unless ambiguity exists;
- small capability set;
- one final validation pass.

## Medium

Examples:

- create a class and related properties;
- add several constraints;
- refactor one small hierarchy area.

Behavior:

- short plan;
- execution in batches;
- validation after each meaningful batch.

## Large

Examples:

- model commercial lending;
- extend a 500-entity ontology;
- restructure a large hierarchy;
- create 40 to 100 related edits.

Behavior:

- required structured plan;
- required user confirmation;
- explicit work packages;
- incremental validation;
- visible progress;
- bounded repair;
- mandatory final review package.

# 4. Project Map

Entio should build a compact project map instead of sending the entire ontology.

It should include:

- project name;
- ontology and SHACL sources;
- namespaces;
- source roles;
- top-level classes;
- entity counts;
- major domain areas;
- commonly used external ontologies;
- reasoning status;
- SHACL status;
- current staged-change counts;
- project naming and IRI conventions.

The project map must be:

- deterministic;
- bounded;
- tied to the project fingerprint;
- refreshed when the project changes;
- safe to send to the model;
- free of complete source files.

# 5. Ontology Neighborhood Retrieval

For a selected entity, Entio should retrieve a bounded neighborhood.

Possible contents:

- entity descriptor;
- direct parents and children;
- related properties;
- domains and ranges;
- related individuals;
- asserted and inferred types;
- relevant SHACL shapes;
- relevant findings;
- usage references;
- source;
- nearby external concepts;
- staged changes affecting the entity.

Retrieval must be limited by:

- depth;
- entity count;
- result size;
- source scope;
- project fingerprint.

The model may request controlled expansion:

- expand parents;
- expand children;
- expand properties;
- expand usage;
- expand constraints;
- expand individuals;
- expand FIBO candidates.

# 6. Search Strategy

Entio should use layered retrieval.

## Layer 1: Exact And Normalized Search

- exact label;
- case-insensitive label;
- alternate label;
- normalized words;
- exact IRI or prefixed name when supplied.

## Layer 2: Existing Deterministic Semantic Search

- labels;
- annotations;
- parent context;
- domain/range context;
- entity kind;
- source.

## Layer 3: External Search

- FIBO;
- future approved external sources.

## Layer 4: Model-Assisted Comparison

The model may compare candidates already returned by Entio.

It must not invent unavailable entities or replace deterministic retrieval.

# 7. Context Package

The model should receive a structured package:

```text
Task
Project map
Current workflow state
Selected entities
Relevant ontology neighborhoods
Relevant SHACL shapes
Relevant reasoning results
Relevant staged changes
Relevant FIBO candidates
Project rules
Available capabilities
Open questions
Current draft summary
```

Each item should identify:

- stable ID;
- label;
- entity kind;
- source;
- asserted or inferred status;
- fingerprint where relevant.

## Initial Context Limits

Recommended defaults:

- up to 20 directly relevant entities;
- up to 50 entities after explicit expansion;
- up to 10 FIBO candidates per search;
- up to 20 relevant SHACL findings;
- only relevant staged items plus aggregate counts;
- bounded result size per tool;
- no full source files by default.

If more context is needed, the AI must request another bounded retrieval.

# 8. Context Freshness

Every package should be associated with:

- project fingerprint;
- task ID;
- work package;
- draft fingerprint;
- reasoning fingerprint;
- SHACL fingerprint.

When the project changes:

- mark affected tasks stale;
- stop further draft mutation;
- refresh context;
- revalidate the draft;
- ask for confirmation when meaning changed.

# 9. Capability Bundles

The model should not receive every tool at once.

## Exploration Bundle

- `get_project_map`
- `search_entities`
- `get_entity_summary`
- `get_entity_neighborhood`
- `compare_entities`
- `get_entity_usage`
- `search_fibo`
- `get_fibo_candidate`

## Planning Bundle

- exploration tools;
- `create_task_plan`
- `update_task_plan`
- `estimate_change_scope`
- `identify_open_questions`
- `record_assumption`

## Ontology Editing Bundle

- approved class edits;
- approved property edits;
- approved individual edits;
- hierarchy changes;
- domain/range changes;
- assertion changes;
- annotation changes;
- deletion changes;
- external-reuse changes.

## SHACL Bundle

- approved shape creation;
- approved property-shape creation;
- approved target changes;
- approved count, datatype, class, numeric, and pattern constraints;
- approved severity and message changes;
- approved constraint removal;
- approved shape deletion.

## Analysis Bundle

- `validate_draft`
- `preview_draft`
- `run_reasoning`
- `run_shacl`
- `get_semantic_diff`
- `get_proposal_impact`
- `get_blocking_findings`

## Repair Bundle

- `get_repair_packet`
- `replace_draft_item`
- `remove_draft_item`
- `add_repair_draft_item`
- `rerun_validation`
- `rerun_reasoning`
- `rerun_shacl`

## Help Bundle

- `get_current_screen_context`
- `get_available_actions`
- `get_entio_help`
- `explain_error_code`
- `explain_workflow_state`

The orchestrator chooses the bundle.

The model cannot add tools or bundles.

# 10. Higher-Level Capabilities

The AI should use higher-level Entio capabilities where possible.

## `prepare_class_model`

May:

- search for duplicates;
- find likely parent classes;
- search FIBO;
- create class, label, definition, and related draft items;
- report ambiguity.

## `prepare_property_model`

May:

- determine object versus datatype property;
- resolve domain and range;
- prepare definition;
- prepare related constraints;
- detect duplicates.

## `prepare_domain_model`

May:

- prepare a related set of classes;
- prepare hierarchy;
- prepare properties;
- prepare constraints;
- divide work into batches.

## `prepare_external_reuse`

May:

- search FIBO;
- compare candidates;
- identify dependencies;
- prepare direct reuse or a local subclass;
- preserve external IRIs.

## `prepare_entity_refactor`

May:

- inspect usage;
- identify dependencies;
- prepare replacement or hierarchy changes;
- prepare deletion review;
- identify affected constraints.

## `repair_draft_from_findings`

May:

- read deterministic findings;
- identify affected draft items;
- prepare allowed repairs;
- preserve original findings;
- rerun analysis.

Higher-level capabilities must output typed Entio edits.

They must never use raw RDF as a shortcut.

# 11. Structured Plan And Work Packages

A large task should use a plan made of work packages.

Example:

```text
Goal: Model commercial lending

1. Inspect existing lending and party concepts
2. Compare FIBO reuse options
3. Create local lending hierarchy
4. Create object properties
5. Create datatype properties
6. Create SHACL constraints
7. Validate and repair
8. Prepare final review
```

Each work package should contain:

- ID;
- title;
- purpose;
- dependencies;
- expected entities;
- expected edit count;
- allowed capability bundle;
- confirmation requirement;
- status;
- result;
- evidence;
- validation outcome;
- retry count.

Later packages must not run when a required earlier package is blocked.

# 12. Plan Confirmation

Confirmation should be required when:

- the expected edit count is large;
- deletions are planned;
- hierarchy refactoring is planned;
- external reuse decisions are material;
- high-impact SHACL constraints are planned;
- several source files will change;
- ambiguity affects concept identity;
- the user asks to review the plan.

Plan confirmation is not final approval.

# 13. Clarification Questions

The AI should ask only questions that materially affect the model.

Examples:

- local concept or external reuse;
- class or individual;
- object or datatype property;
- domain;
- range;
- source;
- deletion or deprecation;
- target class for a shape;
- whether existing entities should be migrated.

Answers should update the task and plan.

The AI should resume from the same work package.

# 14. Private Task Workspace

Recommended state:

```text
AiTaskWorkspace
- task
- projectMapReference
- contextReferences
- plan
- workPackages
- selectedEntities
- privateDraft
- unresolvedReferences
- assumptions
- validationHistory
- reasoningHistory
- shaclHistory
- repairHistory
- reviewPackage
```

The workspace is:

- private to the user and task;
- server-owned;
- in memory for Phase 8;
- fingerprinted;
- versioned;
- cancellable;
- resumable during the server session;
- never applied project state.

# 15. Draft Batching

The AI should not create one large unvalidated draft.

Recommended policy:

## Small Task

- build complete draft;
- validate once;
- run final analysis.

## Medium Task

- validate every 10 to 20 draft items;
- validate at the end of each work package.

## Large Task

- validate every work package;
- block later packages when one fails;
- run final combined analysis.

The ExecPlan may adjust exact thresholds after repository review.

# 16. Incremental Validation

Each batch should run:

```text
typed edit validation
→ preview
→ semantic diff
→ reasoning when relevant
→ SHACL when relevant
→ work-package result
```

Possible results:

- valid;
- warning;
- blocked;
- stale;
- incomplete;
- failed.

A blocked batch must not be marked complete.

# 17. Repair Packets

Deterministic failures should become structured repair packets.

Recommended fields:

```text
RepairPacket
- findingId
- code
- severity
- affectedWorkPackage
- affectedDraftItems
- affectedEntities
- expectedCondition
- actualCondition
- source
- supportingEvidence
- allowedRepairActions
- suggestedRepairCandidates
```

Example:

```text
Code: RANGE_TYPE_MISMATCH
Affected item: Set range of hasBorrower to Account
Expected: Party-compatible class
Actual: Account
Allowed repairs:
- replace range
- remove range
- create local BorrowerRole
```

Repair packets must come from Entio findings.

The model cannot invent a passing validation result.

# 18. Repair Loop

The AI may repair only its private draft.

Rules:

- bounded repair cycles per work package;
- bounded total repair cycles;
- retain original findings;
- retain draft revision history;
- rerun failed analysis;
- explain each repair;
- stop when limits are reached;
- ask the user when several repairs are equally valid;
- never weaken validation rules merely to pass.

# 19. Long-Running Tasks

Large tasks may run for several minutes.

Phase 8 should support:

- background execution;
- progress events;
- cancellation;
- pauses for clarification;
- pauses for plan confirmation;
- pauses for human checkpoints;
- resume during the same server session;
- authoritative status;
- safe failure.

State is not durable across server restart in Phase 8.

## Progress Reporting

Use work packages rather than fake percentages.

Example:

```text
2 of 6 work packages complete
Current: Create object properties
Status: Validating batch
```

# 20. Human Checkpoints

## Planning Checkpoint

Show:

- goal;
- work packages;
- estimated edits;
- affected sources;
- external reuse decisions;
- destructive actions;
- open questions.

Actions:

- continue;
- revise;
- answer;
- cancel.

## Mid-Task Checkpoint

Use when:

- a destructive decision appears;
- a major ambiguity appears;
- a large SHACL impact appears;
- a cross-source decision appears;
- a repair requires business meaning.

## Final Review Checkpoint

Always required.

Show:

- objective;
- completed work packages;
- proposed changes;
- semantic diff;
- validation report;
- reasoning impact;
- SHACL impact;
- external dependencies;
- affected sources;
- rationale;
- warnings;
- unresolved questions;
- deferred work.

Only the human can approve or reject.

# 21. Final Review Package

Recommended structure:

```text
AiReviewPackage
- taskId
- objective
- planSummary
- completedWorkPackages
- draftFingerprint
- proposalFingerprint
- changeSummary
- semanticDiffReference
- validationReportReference
- reasoningImpactReference
- shaclImpactReference
- affectedSources
- externalDependencies
- assumptions
- warnings
- unresolvedQuestions
- aiRationale
- submittedBy
- createdAt
```

# 22. Follow-Up Conversation

The user should be able to ask:

- why did you choose this class;
- why did you reuse this FIBO concept;
- what changed;
- what failed;
- what remains;
- undo the last work package;
- change this domain;
- remove these constraints;
- explain the SHACL findings;
- show only hierarchy changes;
- continue;
- revise the plan.

Answers must use task state and evidence, not only old chat messages.

# 23. Undo And Revision

The user should be able to:

- undo the last draft item;
- undo the last work package;
- revise one package;
- remove one proposed entity;
- change an assumption;
- select a different external concept;
- rerun validation.

Undo affects only the private workspace until submission.

# 24. Project-Wide Analysis

Supported analysis may include:

- missing definitions;
- duplicate labels;
- likely duplicate entities;
- unused classes;
- unused properties;
- missing domains or ranges;
- weak constraint coverage;
- suspicious hierarchy depth;
- inconsistent naming;
- stale annotations.

Project-wide analysis should use server-side scans and summaries.

The model should receive results, not every raw entity.

# 25. Large-Ontology Performance

Phase 8 should support:

- 500-entity projects;
- 1,000-entity projects;
- large hierarchies;
- several ontology sources;
- several SHACL sources;
- hundreds of findings;
- drafts with 50 or more edits.

Rules:

- never load the full hierarchy into model context;
- paginate;
- use bounded neighborhoods;
- cache project maps by fingerprint;
- cache deterministic search results where safe;
- reuse descriptors;
- stream progress;
- avoid repeated provider calls for unchanged context;
- invalidate caches when fingerprints change.

# 26. Context Caching

Safe cache candidates:

- tool definitions;
- project conventions;
- help documentation;
- project map;
- stable descriptors;
- stable neighborhoods.

Do not cache:

- API keys;
- private cross-user data;
- stale proposal information;
- another user's task;
- findings without fingerprints.

# 27. Initial Task Limits

Recommended defaults:

- one active mutating task per user and project;
- up to three concurrent read-only explanation tasks;
- maximum 12 work packages;
- maximum 100 draft items;
- maximum 20 draft items per batch;
- maximum 3 repair cycles per package;
- maximum 8 repair cycles per task;
- maximum 30 model tool calls per package;
- maximum 200 model tool calls per task;
- bounded elapsed time per package;
- bounded total task time;
- bounded context entities per step;
- maximum 10 FIBO candidates per search;
- no recursive AI task creation.

When a limit is reached:

- stop safely;
- preserve the workspace;
- explain the limit;
- allow the user to narrow the task or continue in a new task.

# 28. Permissions

A contributor may:

- create tasks;
- inspect;
- plan;
- build private drafts;
- validate;
- submit for review.

A reviewer may additionally use existing human review controls.

The model never receives reviewer authority.

# 29. Collaboration

Private task state is not shared by default.

Other users may see after submission:

- proposal;
- AI-generated marker;
- submitting user;
- task summary;
- rationale;
- affected sources.

They should not see:

- private conversation;
- private plan before submission;
- private draft revisions;
- hidden provider payloads;
- private repair history.

A project change by another user may make the task stale.

# 30. Application Help

The AI should explain:

- how to perform workflows;
- why actions are disabled;
- status meanings;
- reasoning;
- SHACL;
- proposal review;
- model selection;
- AI limits.

Help must use current Entio documentation and feature metadata.

# 31. Audit And Traceability

Record:

- task ID;
- user;
- project;
- selected model;
- prompt version;
- capability bundle per step;
- tool calls;
- Entio results;
- plan revisions;
- draft revisions;
- validation;
- reasoning;
- SHACL;
- repair cycles;
- checkpoints;
- final proposal reference;
- timing;
- usage metadata where available.

Do not record:

- API keys;
- authorization headers;
- hidden chain of thought;
- unrestricted raw provider payloads;
- unrelated project data.

# 32. User Interface

## Task Header

Show:

- objective;
- status;
- model;
- project;
- current work package;
- progress;
- pause/cancel.

## Plan View

Show:

- packages;
- dependencies;
- statuses;
- estimated edits;
- confirmations;
- open questions.

## Context View

Show:

- selected entities;
- hierarchy;
- properties;
- constraints;
- FIBO candidates;
- evidence.

## Draft View

Show:

- items grouped by package;
- validation;
- source;
- rationale;
- revision history.

## Analysis View

Show:

- validation;
- semantic diff;
- reasoning;
- SHACL;
- repair packets;
- unresolved issues.

## Final Review View

Show the complete review package and link to the authoritative Entio proposal review.

# 33. Recommended Server Components

Possible components:

- `AiTaskService`
- `AiTask`
- `AiTaskStatus`
- `AiTaskType`
- `AiTaskStore`
- `AiTaskClassifier`
- `AiTaskOrchestrator`
- `AiProjectMapService`
- `AiOntologyNeighborhoodService`
- `AiContextPackageBuilder`
- `AiCapabilityBundleRegistry`
- `AiWorkflowPlanner`
- `AiWorkPackage`
- `AiWorkPackageExecutor`
- `AiTaskWorkspace`
- `AiDraftBatchService`
- `AiIncrementalValidationService`
- `AiRepairPacketBuilder`
- `AiRepairController`
- `AiCheckpointService`
- `AiReviewPackageBuilder`
- `AiTaskEventService`
- `AiEvaluationService`

Names may vary, but responsibilities should remain separate.

# 34. Recommended Web Contracts

Possible endpoints:

```text
POST   /api/v1/projects/{projectId}/ai/tasks
GET    /api/v1/projects/{projectId}/ai/tasks/{taskId}
POST   /api/v1/projects/{projectId}/ai/tasks/{taskId}/messages
POST   /api/v1/projects/{projectId}/ai/tasks/{taskId}/clarifications
POST   /api/v1/projects/{projectId}/ai/tasks/{taskId}/plan
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
POST   /api/v1/projects/{projectId}/ai/tasks/{taskId}/submit
```

Private events may report:

- task created;
- context gathering;
- clarification required;
- plan created;
- plan confirmed;
- work package started;
- batch created;
- validation started/completed;
- reasoning started/completed;
- SHACL started/completed;
- repair started/completed;
- checkpoint required;
- paused/resumed;
- review ready;
- failed;
- cancelled;
- limit reached.

# 35. Structured Errors

Recommended errors:

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

# 36. Evaluation Framework

Phase 8 should add a permanent evaluation suite.

## Small Tasks

- create class;
- add definition;
- add superclass;
- create individual;
- add property;
- add one SHACL constraint.

## Medium Tasks

- model checking accounts;
- add borrower relationships;
- reuse one FIBO concept;
- add related properties and constraints;
- repair several validation failures.

## Large Tasks

- model commercial lending;
- extend a 500-entity ontology;
- restructure a hierarchy;
- create 50 related edits;
- combine ontology and SHACL edits;
- repair a large draft.

## Explanation Tasks

- explain inference;
- explain SHACL;
- explain semantic diff;
- explain concept choice;
- explain application workflow.

## Safety Tasks

- request direct write;
- request config edit;
- request shell access;
- request approval;
- prompt injection in labels or definitions;
- cross-project access attempt.

## Metrics

Measure:

- task completion;
- correct entity selection;
- duplicate avoidance;
- correct source selection;
- correct external reuse;
- valid typed edits;
- validation success;
- reasoning consistency;
- SHACL impact quality;
- repair success;
- clarification usefulness;
- plan quality;
- tool-call count;
- duration;
- token use;
- cost where available;
- human acceptance rate;
- unauthorized-action attempts;
- final proposal quality.

# 37. Deterministic Fixtures

Use copied fixtures.

Recommended fixtures:

- small banking ontology;
- medium lending ontology;
- generated 500+ entity ontology;
- generated 1,000+ entity ontology;
- hierarchy conflict fixture;
- SHACL failure fixture;
- FIBO reuse fixture;
- stale-task fixture;
- collaboration-conflict fixture.

# 38. Testing Requirements

## Task State

- valid transitions;
- invalid transition rejection;
- cancellation;
- pause/resume;
- stale invalidation;
- ownership;
- cross-project rejection.

## Retrieval

- bounded project map;
- deterministic neighborhoods;
- context expansion;
- freshness;
- pagination;
- no full-project leakage.

## Planning

- safe simple tasks skip planning;
- large tasks require planning;
- destructive plans require confirmation;
- clarification resumes correctly;
- plan revision works.

## Capability Bundles

- only approved tools are exposed;
- bundles change by stage;
- model cannot expand bundle;
- explanation tasks cannot mutate;
- repair tasks cannot apply.

## Execution

- packages execute in order;
- dependencies are respected;
- failures block later packages;
- batching works;
- progress events are ordered.

## Validation

- incremental validation;
- final combined validation;
- fingerprint matching;
- stale result rejection;
- no model override.

## Repair

- structured packet;
- bounded repair;
- draft-only changes;
- repair history;
- user clarification where needed.

## Review

- complete review package;
- no source mutation before approval;
- existing review workflow receives the proposal;
- AI cannot approve or apply.

## Performance

- 500-entity fixture;
- 1,000-entity fixture;
- bounded context;
- no complete ontology in model context;
- cache invalidation;
- acceptable response time.

## Security

- no direct write;
- no config edit;
- no arbitrary files;
- no shell;
- no unrestricted network;
- no permission escalation;
- no cross-user private task access;
- prompt-injection resistance.

## Regression

All existing Phase 1 through Phase 7.5 tests must pass.

# 39. Primary End-To-End Journeys

## Journey 1: Simple Edit

```text
add definition
→ focused task
→ retrieve entity
→ create typed draft
→ validate
→ review package
→ human decision
```

## Journey 2: Medium Domain Extension

```text
add borrower model
→ short plan
→ retrieve relevant concepts
→ compare local and FIBO options
→ build draft in batches
→ validate and run SHACL
→ repair if needed
→ review package
```

## Journey 3: Large Domain Model

```text
model commercial lending
→ project map
→ domain retrieval
→ structured plan
→ user confirmation
→ package execution
→ incremental validation
→ repair
→ combined reasoning and SHACL
→ final review package
```

## Journey 4: Large Existing Ontology

```text
extend 500-entity ontology
→ bounded search and neighborhoods
→ no full graph in model context
→ relevant area selected
→ planned edits
→ context refresh as needed
→ final proposal
```

## Journey 5: Refactor With Checkpoint

```text
reorganize hierarchy
→ dependency analysis
→ high-impact plan
→ user confirmation
→ refactor draft
→ reasoning and SHACL impact
→ final review
```

## Journey 6: Stale Task

```text
task running
→ another user changes project
→ task stale
→ execution stops
→ context refresh
→ draft revalidation
→ user confirmation if meaning changed
```

# 40. Success Criteria

Phase 8 is successful when:

- simple tasks work reliably;
- medium tasks complete through plans and batches;
- large tasks can create 50 or more valid typed edits;
- tasks work on ontologies with at least 500 entities;
- full ontology content is not sent by default;
- retrieval is bounded and fingerprinted;
- task progress is server-owned and visible;
- capability bundles change by workflow stage;
- higher-level Entio capabilities reduce tool complexity;
- validation occurs incrementally;
- repair packets are structured;
- repair loops are bounded;
- follow-up questions use task state;
- final review packages are complete;
- human approval remains mandatory;
- AI cannot write files directly;
- AI cannot change configuration;
- AI cannot approve, reject, apply, or roll back;
- Phase 1 through Phase 7.5 remain compatible;
- permanent evaluations cover small, medium, large, explanation, and safety tasks.

# 41. Likely Follow-Up Work

Possible future phases:

- durable task persistence;
- production task queue;
- organization policies;
- administrator controls;
- cost budgets;
- reusable domain templates;
- document-assisted modeling;
- entity-resolution workflows;
- multi-project migrations;
- more external ontologies;
- task-based model routing;
- production AI observability;
- human feedback learning;
- domain-specific evaluation packs.
