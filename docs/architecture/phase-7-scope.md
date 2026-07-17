# Phase 7 Scope

## Phase Name

**Phase 7: Tool-Driven Native AI Ontology Copilot**

## Status

Draft. Phase 6 is complete, and Phase 7 is the active planning boundary. This scope does not represent implemented Phase 7 behavior.

## Purpose

Phase 7 connects Entio's native AI assistant to the OpenAI API and turns it into a tool-driven ontology copilot.

The AI should be able to help a user understand, design, extend, repair, and review an Entio knowledge graph through natural-language conversation. It may use approved Entio capabilities to inspect the project, search local and external ontology content, prepare typed edits, assemble multi-edit proposals, validate those proposals, run reasoning and SHACL validation, inspect semantic impact, revise its own draft, and explain the final result.

The AI must remain subordinate to Entio's deterministic semantic workflow.

It must never:

- write ontology files directly;
- approve or reject a proposal;
- apply a proposal;
- bypass validation;
- bypass reasoning or SHACL checks;
- edit `entio.yaml` or another Entio project-configuration file;
- access arbitrary files, the shell, the operating system, network resources, or external services outside the explicitly approved AI provider and Entio capability set;
- modify FIBO or other immutable packaged ontology assets;
- grant itself additional permissions;
- claim a capability that Entio has not exposed.

The central Phase 7 promise is:

> A user can describe, refine, and understand an ontology change conversationally. Entio AI may use any approved ontology capability to prepare and self-validate a complete proposal, but only a human can approve, reject, and trigger application of the final result.

## Product Goals

Phase 7 should provide:

- A real OpenAI-backed AI assistant inside Entio.
- Conversational understanding of the current project, selected entity, staged changes, proposal preview, reasoning results, SHACL findings, and FIBO catalog.
- Natural-language ontology editing across all existing typed Entio edit capabilities.
- Multi-step planning for larger ontology-design tasks.
- A bounded tool loop that lets the AI inspect, propose, validate, revise, and explain its work.
- Follow-up questions that preserve relevant conversational context.
- Explanations of anything pertaining to:
  - the current ontology;
  - inferred facts;
  - SHACL findings;
  - FIBO reuse;
  - staged edits;
  - semantic diffs;
  - validation reports;
  - Entio application workflows;
  - permissions and unavailable actions.
- Human review of the final proposal before any source mutation.
- Explicit visibility into:
  - what the AI inspected;
  - what tools it used;
  - what it proposes;
  - why it proposes it;
  - what validation found;
  - what reasoning changed;
  - what SHACL changed;
  - what remains uncertain.
- Safe failure when the user request is outside the AI's approved Entio scope.
- Preservation of all existing Phase 1 through Phase 6 behavior, clients, contracts, and workflows.

## Core Design Principle

The AI is constrained to **Entio capabilities**, not directly to low-level modules or raw graph operations.

The existing Kotlin modules remain authoritative:

```text
OpenAI model
    |
    | requests approved tool calls
    v
Entio AI capability registry
    |
    +-- read-only semantic capabilities
    +-- typed ontology-edit capabilities
    +-- FIBO capabilities
    +-- proposal-preview capabilities
    +-- validation capabilities
    +-- reasoning capabilities
    +-- SHACL capabilities
    +-- application-help capabilities
    |
    v
Existing Entio Kotlin services
```

The model chooses and sequences high-level tasks.

Entio decides:

- which tools exist;
- which user may call them;
- which project and sources they may access;
- whether arguments are valid;
- how typed edits are constructed;
- how proposals are previewed;
- how validation, reasoning, and SHACL run;
- whether a proposal may enter human review;
- whether a human may approve or reject it.

## Recommended OpenAI Integration

Use the OpenAI **Responses API** through the Kotlin server.

Use:

- custom function tools;
- strict JSON-schema tool arguments;
- server-side API-key use only;
- streamed response events where useful;
- application-owned conversation state;
- pinned model configuration;
- explicit request, tool-call, cost, and elapsed-time limits.

The OpenAI API key remains the user's own credential.

Phase 7 should preserve the Phase 6 credential boundary:

- The React client never calls OpenAI directly.
- The key is sent to the Kotlin server through the approved credential settings flow.
- The key is stored only in server-side user-session memory.
- The key is never written to disk.
- The key is removed on logout, session expiration, explicit deletion, or server restart.
- The browser retains only credential status after submission.
- The key never appears in URLs, logs, analytics, WebSocket events, collaboration state, proposals, ontology files, snapshots, or another user's session.
- OpenAI authorization headers and provider errors are redacted.
- OpenAI usage and billing belong to the user's own API account.
- Entio remains fully usable without an OpenAI API key.

## AI Roles

Phase 7 should distinguish these AI roles.

### 1. Explainer

Answers questions about:

- classes;
- properties;
- individuals;
- definitions;
- hierarchy;
- domains and ranges;
- assertions;
- asserted versus inferred facts;
- unsatisfiable classes;
- consistency;
- SHACL violations;
- proposal impact;
- FIBO candidates and dependencies;
- Entio workflow and UI state.

### 2. Ontology Designer

Helps plan ontology structures, including:

- classes;
- properties;
- individuals;
- labels;
- definitions;
- alternate labels;
- annotations;
- superclass relationships;
- domains;
- ranges;
- assertions;
- local extensions of external ontology terms;
- reuse of FIBO concepts.

### 3. Proposal Builder

Converts an approved design direction into typed Entio edits.

### 4. Proposal Reviewer

Explains:

- explicit semantic diff;
- reasoning impact;
- SHACL impact;
- validation errors;
- affected sources;
- unresolved questions;
- potential risks.

### 5. Repair Assistant

Inspects structured validation, reasoning, or SHACL failures and revises its own draft proposal.

It may repair only its draft.

It may not alter applied source files or another user's staged changes without explicit user action.

### 6. Entio Guide

Explains how to use:

- hierarchy navigation;
- entity details;
- staged changes;
- proposal review;
- reasoning;
- SHACL;
- FIBO;
- collaboration;
- AI settings;
- permissions;
- stale/conflicted states;
- approval and rejection workflows.

Help must be grounded in versioned Entio help content and current application metadata.

## Approved AI Operation Types

Initial conversation requests should be classified into approved operations.

### Read And Explain

- `EXPLAIN_ENTITY`
- `COMPARE_ENTITIES`
- `EXPLAIN_RELATIONSHIP`
- `EXPLAIN_INFERENCE`
- `EXPLAIN_CONSISTENCY_RESULT`
- `EXPLAIN_UNSATISFIABLE_CLASS`
- `EXPLAIN_SHACL_RESULT`
- `EXPLAIN_PROPOSAL`
- `EXPLAIN_SEMANTIC_DIFF`
- `EXPLAIN_VALIDATION_REPORT`
- `EXPLAIN_FIBO_CANDIDATE`
- `EXPLAIN_FIBO_DEPENDENCY`
- `SUMMARIZE_PROJECT`
- `SUMMARIZE_PROPOSAL`
- `SUMMARIZE_RECENT_ACTIVITY`

### Search And Inspect

- `SEARCH_LOCAL_ONTOLOGY`
- `BROWSE_HIERARCHY`
- `GET_ENTITY_USAGE`
- `SEARCH_FIBO`
- `GET_FIBO_DESCRIPTOR`
- `GET_FIBO_DEPENDENCIES`
- `GET_REASONING_RESULT`
- `GET_SHACL_RESULTS`
- `GET_STAGED_CHANGES`
- `GET_PROPOSAL_IMPACT`

### Design And Edit

- `DESIGN_ONTOLOGY_CHANGE`
- `SUGGEST_CLASS`
- `SUGGEST_PROPERTY`
- `SUGGEST_INDIVIDUAL`
- `SUGGEST_SUPERCLASS`
- `SUGGEST_DOMAIN`
- `SUGGEST_RANGE`
- `SUGGEST_ASSERTION`
- `SUGGEST_LABEL`
- `SUGGEST_DEFINITION`
- `SUGGEST_ALTERNATE_LABEL`
- `SUGGEST_ANNOTATION`
- `SUGGEST_DELETE_ENTITY`
- `SUGGEST_EXTERNAL_REUSE`
- `SUGGEST_LOCAL_SUBCLASS_OF_EXTERNAL`

### Draft Management

- `CREATE_AI_DRAFT`
- `ADD_AI_DRAFT_ITEM`
- `UPDATE_AI_DRAFT_ITEM`
- `REMOVE_AI_DRAFT_ITEM`
- `REORDER_AI_DRAFT`
- `CLEAR_AI_DRAFT`
- `VALIDATE_AI_DRAFT`
- `PREVIEW_AI_DRAFT`
- `RUN_AI_DRAFT_REASONING`
- `RUN_AI_DRAFT_SHACL`
- `GET_AI_DRAFT_IMPACT`
- `SUBMIT_AI_DRAFT_FOR_HUMAN_REVIEW`

### Entio Help

- `GET_ENTIO_FEATURE_HELP`
- `GET_CURRENT_SCREEN_CONTEXT`
- `GET_AVAILABLE_ACTIONS`
- `EXPLAIN_WORKFLOW_STATE`
- `EXPLAIN_PERMISSION`
- `EXPLAIN_ERROR_CODE`

## Capability Registry

Phase 7 should add an Entio-owned registry of AI-callable capabilities.

Each capability must define:

- stable capability name;
- description;
- input JSON schema;
- result contract;
- read-only or draft-mutating classification;
- required project capability;
- required user role;
- allowed source scope;
- maximum result size;
- timeout;
- audit behavior;
- whether explicit user confirmation is required before execution.

The model must receive only the capabilities allowed for the current:

- user;
- project;
- conversation;
- current workflow state;
- project source scope;
- feature availability.

The model may not request capabilities that were not supplied in the current OpenAI request.

## Read-Only Capability Set

Recommended read-only tools include:

- `get_project_summary`
- `get_entity_descriptor`
- `compare_entity_descriptors`
- `search_local_ontology`
- `browse_hierarchy`
- `get_entity_usage`
- `get_reasoning_result`
- `explain_inference`
- `get_consistency_result`
- `get_unsatisfiable_classes`
- `get_shacl_findings`
- `get_proposal_impact`
- `get_staged_changes`
- `search_fibo`
- `get_fibo_descriptor`
- `get_fibo_dependencies`
- `get_entio_feature_help`
- `get_current_screen_context`
- `get_available_actions`
- `explain_error_code`

Read-only tools must not:

- construct changes;
- mutate staged state;
- write source files;
- modify sessions outside normal read metadata;
- access arbitrary files.

## Typed Edit Capability Set

The AI should be able to propose every edit currently supported by Entio's typed-edit boundary.

Recommended tools include:

- `propose_create_class`
- `propose_create_object_property`
- `propose_create_datatype_property`
- `propose_create_annotation_property`
- `propose_create_individual`
- `propose_assign_type`
- `propose_add_superclass`
- `propose_remove_superclass`
- `propose_set_property_domain`
- `propose_set_property_range`
- `propose_add_object_assertion`
- `propose_add_datatype_assertion`
- `propose_set_label`
- `propose_add_definition`
- `propose_add_alternate_label`
- `propose_add_annotation`
- `propose_delete_entity`
- `propose_external_reuse`
- `propose_local_subclass_of_external`

Each edit tool must:

- create or update an item in the AI draft;
- use Entio-owned typed edit contracts;
- validate source and entity references;
- preserve original external IRIs;
- refuse unsupported graph operations;
- never create raw arbitrary triples as a fallback.

## SHACL Mutation Boundary

Phase 7 should not claim full AI SHACL authoring unless the backend exposes approved typed SHACL mutation contracts.

If supported typed SHACL edits exist, the capability layer may expose:

- `propose_create_node_shape`
- `propose_create_property_shape`
- `propose_add_shacl_constraint`
- `propose_update_shacl_constraint`
- `propose_remove_shacl_constraint`
- `propose_delete_shacl_shape`

If they do not exist, the AI may:

- explain SHACL;
- suggest a conceptual constraint in narrative form;
- identify which typed backend capability is missing.

It must not generate raw SHACL RDF and stage it as a workaround.

## Explicitly Forbidden AI Capabilities

The AI must never receive tools for:

- approving a proposal;
- rejecting a proposal;
- applying a proposal;
- rolling back a proposal;
- writing Turtle;
- editing source files directly;
- editing `entio.yaml`;
- editing any Entio project configuration;
- modifying the project registry;
- changing permissions;
- changing user roles;
- accessing arbitrary files;
- listing directories;
- reading environment variables;
- executing shell commands;
- running arbitrary code;
- making unrestricted network requests;
- modifying the FIBO package;
- changing the AI capability registry;
- changing AI safety policy;
- retrieving API keys;
- viewing another user's AI credential;
- altering collaboration-session authority.

The absence of these tools is a hard security boundary.

## AI Scope Object

Every AI run must execute within a server-created scope.

Example:

```json
{
  "projectId": "finance-model",
  "userId": "user-123",
  "conversationId": "conversation-456",
  "collaborationSessionId": "session-789",
  "allowedSourceIds": ["core", "lending"],
  "baselineFingerprint": "abc123",
  "allowedCapabilities": [
    "READ_ONTOLOGY",
    "READ_REASONING",
    "READ_SHACL",
    "SEARCH_FIBO",
    "PREPARE_TYPED_EDITS",
    "MANAGE_AI_DRAFT",
    "PREVIEW",
    "VALIDATE",
    "EXPLAIN",
    "GET_ENTIO_HELP"
  ]
}
```

Kotlin must construct and validate the scope.

The model may not:

- alter it;
- expand it;
- choose a new project;
- select an unapproved source;
- access another conversation's draft;
- access another user's private state.

## AI Draft Workspace

The AI should work in a dedicated draft workspace rather than directly in the shared staged set.

The AI draft contains:

- draft ID;
- project ID;
- conversation ID;
- user ID;
- baseline fingerprint;
- allowed source IDs;
- ordered typed draft items;
- rationale per item;
- dependencies;
- validation state;
- conflict state;
- preview fingerprint;
- reasoning result reference;
- SHACL result reference;
- proposal-impact reference;
- creation and update timestamps.

The AI draft is:

- private to the user and conversation until submitted for human review;
- server-owned;
- session-scoped in Phase 7 unless a later phase adds persistence;
- never an applied project state;
- invalidated when its baseline becomes stale;
- separate from the shared human staged set until submission.

## Human Review Boundary

The AI may call:

- `submit_ai_draft_for_human_review`

This capability should:

- verify the current draft;
- verify its baseline;
- create a reviewable Entio proposal or shared staged set using existing workflows;
- preserve AI attribution;
- include the AI rationale;
- include validation, reasoning, SHACL, and diff results;
- lock or version the submitted draft for review;
- return a proposal ID and review state.

It must not:

- approve;
- reject;
- apply;
- roll back;
- impersonate a reviewer.

Only a human reviewer can invoke the existing Entio review controls.

## Planning Behavior

### Small Requests

For focused requests such as:

- "Add a definition to Loan."
- "Make Commercial Loan a subclass of Loan."
- "Rename this class."

The AI may prepare the draft directly.

### Large Requests

For broader requests such as:

- "Create a complete commercial-lending ontology."
- "Model our customer onboarding process."
- "Reuse FIBO concepts for payments and accounts."

The AI should first create a human-readable plan.

The plan should include:

- intended concepts;
- intended reuse;
- intended local additions;
- expected source files;
- expected constraints;
- expected reasoning consequences;
- open decisions;
- estimated edit count.

The user can:

- continue;
- modify the plan;
- answer questions;
- cancel.

Plan confirmation is not final proposal approval.

The resulting proposal still requires the normal review process.

## Clarification Behavior

The AI should ask follow-up questions when ambiguity materially affects:

- concept identity;
- local versus external reuse;
- target ontology source;
- domain or range;
- literal datatype;
- relationship direction;
- deletion impact;
- FIBO dependency selection;
- whether a concept is a class, role, property, or individual;
- whether a change should replace or add to existing values.

The AI should not ask unnecessary questions for low-risk, clearly specified edits.

## Tool Loop

A Phase 7 AI run should use a bounded tool loop.

Recommended sequence:

```text
receive user request
→ classify intent
→ gather minimal context
→ ask clarification if needed
→ produce or confirm plan
→ create AI draft
→ add typed edit items
→ validate draft
→ preview draft
→ run reasoning
→ run SHACL
→ inspect proposal impact
→ revise draft if necessary
→ present final explanation
→ submit for human review only after user request
```

The AI may repeat validation and repair within approved limits.

The AI may not loop indefinitely.

## AI Run Limits

The system should enforce configurable limits.

Recommended initial defaults:

- Maximum 20 tool calls per user turn.
- Maximum 50 draft edits per AI run.
- Maximum 3 automated self-correction cycles.
- Maximum 1 active AI run per user and project.
- Maximum 20 local entities in context unless explicitly expanded.
- Maximum 10 FIBO candidates inspected per search step.
- Maximum bounded tool-result size per capability.
- Maximum AI run elapsed time.
- Maximum OpenAI request count per turn.
- Maximum estimated token or cost budget where measurable.
- No recursive AI self-invocation.
- No parallel draft mutation by two AI runs.

If a limit is reached, the AI must:

- stop safely;
- preserve the current draft;
- explain the limit;
- allow the user to continue in another turn.

## Self-Correction

The AI may revise its own draft after structured failures.

Examples:

- missing class reference;
- invalid domain or range;
- duplicate entity;
- unsupported datatype;
- stale baseline;
- SHACL violation introduced by the draft;
- inconsistency introduced by the draft;
- missing FIBO dependency;
- invalid deletion dependency selection.

Self-correction must:

- use structured Entio findings;
- modify only the current AI draft;
- retain a revision history within the conversation session;
- explain what changed;
- stop after the configured retry limit;
- never weaken validation policy.

## Conversation Model

The assistant should support reciprocal follow-up conversation.

Examples:

- "Why did you make Borrower a role?"
- "Use Organization instead."
- "Undo the last proposed item."
- "Why did this create an inference?"
- "What happens if we remove this parent?"
- "Explain the SHACL violation."
- "Show only the inferred changes."
- "What still needs to be modeled?"
- "How do I apply this in Entio?"

### Application-Owned Conversation State

Entio should own the conversation state.

It should store, in session memory for Phase 7:

- conversation ID;
- user messages;
- assistant messages;
- operation types;
- tool-call summaries;
- Entio result references;
- AI draft ID;
- proposal IDs;
- current project and entity context;
- model and prompt version;
- OpenAI response IDs where used;
- timestamps;
- token/cost metadata where available.

Entio should reconstruct a bounded request context for each OpenAI call.

It should not rely exclusively on provider-managed conversation state.

Provider storage and retention behavior must be configured deliberately.

## AI Context Builder

The context builder should retrieve only information relevant to the current request.

Possible context:

- current project summary;
- selected entity descriptor;
- selected related entities;
- hierarchy neighborhood;
- asserted relationships;
- inferred relationships;
- reasoning explanation;
- SHACL findings;
- current AI draft;
- shared staged changes relevant to the target;
- proposal impact;
- FIBO candidates;
- user role and available actions;
- current screen and workflow state;
- versioned Entio help content.

The context builder must not:

- send the entire project by default;
- send all source files;
- send another user's private draft;
- send unrelated projects;
- send credentials;
- send logs or filesystem metadata;
- include content beyond configured limits.

## Prompt-Injection Boundary

Ontology content is untrusted data.

This includes:

- labels;
- definitions;
- annotations;
- comments;
- source text;
- individual literal values;
- FIBO definitions;
- external ontology metadata;
- user-authored help text;
- proposal comments.

The OpenAI request must structurally separate:

- trusted developer/system policy;
- user request;
- tool definitions;
- structured Entio context;
- untrusted ontology text.

Untrusted ontology text must not:

- change system instructions;
- grant tools;
- change permissions;
- request secrets;
- bypass review;
- alter validation;
- redefine tool behavior;
- cause unrelated data access.

Prompt-injection tests are required.

## AI Response Contract

An AI response should separate:

- `answer`
- `operationType`
- `evidence`
- `assertedFacts`
- `inferredFacts`
- `shaclFindings`
- `fiboResults`
- `draftSummary`
- `typedSuggestions`
- `uncertainty`
- `warnings`
- `limitsReached`
- `nextActions`

Evidence items should identify:

- Entio object type;
- stable object ID or IRI;
- asserted or inferred origin;
- source reference where available;
- result fingerprint where applicable.

The AI should not present unsupported claims as ontology facts.

## OpenAI Tool Schema Requirements

Every custom function tool should use:

- stable tool name;
- concise description;
- strict JSON schema;
- `additionalProperties: false`;
- explicit required fields;
- nullable fields represented explicitly;
- bounded arrays;
- stable enums;
- no raw arbitrary object fields;
- no direct filesystem paths;
- no raw Turtle or SPARQL fields unless a later approved capability explicitly requires them.

Strict tool arguments do not replace Kotlin validation.

Every tool call must still pass:

- user authorization;
- project scope;
- source scope;
- baseline validation;
- semantic validation;
- capability-specific limits.

## Model Configuration

Phase 7 should use:

- a pinned OpenAI model identifier or approved model family plus explicit version policy;
- deterministic prompt versioning;
- low or controlled randomness for tool-driven editing;
- streaming for user-visible narrative where practical;
- tool calling for all ontology actions;
- structured output for final response objects where supported;
- provider timeout and retry policy;
- request IDs and provider response IDs for support and auditing.

The exact model may be selected in the spec or ExecPlan, but implementation must not use an unpinned "latest" alias without an approved compatibility policy.

## AI Audit Record

Every AI run should create an Entio-owned audit record in session state.

Include:

- run ID;
- conversation ID;
- user ID;
- project ID;
- model;
- prompt version;
- allowed capability list;
- tool calls;
- tool outcomes;
- draft revisions;
- validation results;
- reasoning and SHACL result references;
- final proposal reference;
- failure or limit state;
- start and end timestamps;
- token/cost metadata where available.

The audit record must not contain:

- API keys;
- authorization headers;
- unrelated project content;
- raw secrets.

Durable audit persistence is not required in Phase 7.

## User Experience

### AI Assistant Panel

The AI assistant should show:

- current project context;
- selected entity context;
- current AI draft status;
- current proposal status;
- credential status;
- AI run status;
- stop/cancel action.

### Suggested Actions

Examples:

- Explain selected entity.
- Explain current inference.
- Explain SHACL finding.
- Design a change.
- Edit selected entity.
- Search FIBO.
- Review current draft.
- Submit for human review.
- Explain how to use this screen.

### AI Draft Review

Before submission, show:

- planned changes;
- typed draft items;
- validation state;
- reasoning impact;
- SHACL impact;
- semantic diff;
- warnings;
- unresolved questions;
- AI revision history.

### Final Human Review

The existing Entio proposal review remains authoritative.

The AI panel may link to it.

It must not reproduce approval authority inside the model response.

## Entio Help System

Phase 7 should add or formalize a versioned help source.

Help should cover:

- application navigation;
- entity types;
- edit workflows;
- staging;
- proposal review;
- reasoning;
- asserted versus inferred;
- SHACL;
- FIBO;
- collaboration;
- stale and conflict states;
- permissions;
- AI credentials;
- AI limitations;
- error codes.

The help capability should use:

- versioned Entio documentation;
- current feature availability;
- current user permissions;
- current screen metadata.

The AI must not claim unavailable UI features.

## Collaboration Behavior

In collaborative projects:

- The AI draft is private until submitted.
- Other users do not see private AI reasoning or draft content by default.
- Submitted AI changes enter the normal shared staged or proposal workflow.
- Submitted items show:
  - AI-generated marker;
  - accepting user;
  - conversation/run reference;
  - rationale.
- A human contributor cannot use AI to bypass reviewer permissions.
- One user's API key is never used for another user's AI request.
- One user's private AI draft cannot mutate another user's draft.
- Shared baseline changes may make the AI draft stale.
- A stale AI draft must be revalidated or re-prepared before submission.

## Validation And Proposal Workflow

The final AI proposal must follow:

```text
AI draft
→ typed-edit validation
→ in-memory preview
→ semantic diff
→ deterministic proposal validation
→ OWL reasoning
→ SHACL validation
→ proposal impact
→ AI explanation
→ human review
→ human approve or reject
→ existing Entio application and rollback workflow
```

No stage may be skipped.

## Project Configuration Protection

The AI must not edit:

- `entio.yaml`;
- project registry configuration;
- ontology source lists;
- source roles;
- namespaces defined only through project configuration;
- API settings;
- user permissions;
- server configuration;
- FIBO package manifest;
- model configuration.

If a user asks for such a change, the AI should:

- explain that it is outside the approved AI scope;
- provide manual application guidance where safe;
- not create a tool call or proposal.

## Source Scope Protection

Each AI draft must target only approved ontology or SHACL source IDs supplied by the server scope.

The AI must not:

- invent a source path;
- target an unregistered source;
- move content between sources unless an approved typed capability exists;
- create a new source file;
- edit immutable external assets.

## Non-Goals

Phase 7 should not include:

- Autonomous proposal approval.
- Autonomous proposal rejection.
- Autonomous application.
- Background AI that changes projects without a user request.
- Arbitrary raw triple editing.
- Arbitrary Turtle generation.
- Arbitrary SPARQL updates.
- Shell or code execution.
- Filesystem access.
- General web browsing by the model.
- Remote MCP servers.
- Editing Entio project configuration.
- Editing authentication or authorization.
- Durable AI conversation storage.
- Durable AI audit storage.
- Multi-provider support beyond the provider-neutral boundary and initial OpenAI implementation.
- Fine-tuning.
- Embeddings or a new vector database.
- Document ingestion.
- Entity-resolution agents beyond existing approved Entio capabilities.
- Full autonomous ontology engineering.
- Replacing deterministic Entio search, validation, reasoning, or SHACL with model judgment.
- Allowing the AI to claim a successful change before Entio validation completes.

## Expected Kotlin Components

Phase 7 may introduce:

- `AiConversationService`
- `AiConversation`
- `AiConversationMessage`
- `AiOperationType`
- `AiContextBuilder`
- `AiContextPackage`
- `AiCapabilityRegistry`
- `AiCapabilityDefinition`
- `AiCapabilityScope`
- `AiCapabilityResult`
- `AiRunPolicy`
- `AiRun`
- `AiRunStatus`
- `AiToolLoop`
- `AiToolCall`
- `AiToolResult`
- `AiDraftWorkspace`
- `AiDraft`
- `AiDraftItem`
- `AiDraftRevision`
- `AiDraftValidator`
- `AiDraftPreviewService`
- `AiSuggestionValidator`
- `AiResponse`
- `AiEvidence`
- `AiWarning`
- `AiAuditRecord`
- `OpenAiResponsesClient`
- `AiProvider`
- `AiProviderRequest`
- `AiProviderResponse`
- `EntioHelpService`

Names may vary in the spec and ExecPlan, but responsibilities must remain clear.

## Expected Web Contracts

Potential API areas:

```text
/api/v1/projects/{projectId}/ai/conversations
/api/v1/projects/{projectId}/ai/conversations/{conversationId}
/api/v1/projects/{projectId}/ai/conversations/{conversationId}/messages
/api/v1/projects/{projectId}/ai/runs
/api/v1/projects/{projectId}/ai/runs/{runId}
/api/v1/projects/{projectId}/ai/runs/{runId}/cancel
/api/v1/projects/{projectId}/ai/drafts
/api/v1/projects/{projectId}/ai/drafts/{draftId}
/api/v1/projects/{projectId}/ai/drafts/{draftId}/validate
/api/v1/projects/{projectId}/ai/drafts/{draftId}/preview
/api/v1/projects/{projectId}/ai/drafts/{draftId}/reasoning
/api/v1/projects/{projectId}/ai/drafts/{draftId}/shacl
/api/v1/projects/{projectId}/ai/drafts/{draftId}/impact
/api/v1/projects/{projectId}/ai/drafts/{draftId}/submit
/api/v1/projects/{projectId}/ai/help
```

WebSocket or streaming events may include:

- AI run started;
- text delta;
- tool requested;
- tool started;
- tool completed;
- draft updated;
- validation completed;
- reasoning completed;
- SHACL completed;
- revision completed;
- awaiting clarification;
- awaiting user confirmation;
- ready for human review;
- run failed;
- run cancelled;
- limit reached.

## Error And Status Model

AI run statuses should include:

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

Structured errors should include:

- missing credential;
- invalid credential;
- provider unavailable;
- provider rate limited;
- provider timeout;
- malformed tool request;
- unauthorized capability;
- out-of-scope project;
- out-of-scope source;
- stale baseline;
- validation failed;
- reasoning incomplete;
- SHACL blocked;
- tool-call limit reached;
- draft-edit limit reached;
- context limit reached;
- unsupported requested operation;
- forbidden project-configuration request.

## Testing Requirements

### Capability Security

- The model receives only allowed tools.
- Forbidden tools do not exist.
- Project and source scope are enforced.
- Cross-user access is rejected.
- Cross-project access is rejected.
- Project-configuration edits are rejected.
- FIBO assets cannot be mutated.
- Raw file and shell access are unavailable.

### Tool Schema

- Strict schemas accept valid inputs.
- Unknown fields are rejected.
- Missing required fields are rejected.
- Oversized arrays are rejected.
- Invalid IRIs are rejected.
- Unsupported enum values are rejected.
- Kotlin semantic validation still runs after schema validation.

### Draft Workflow

- Small edit request creates a valid draft.
- Large request creates a plan before editing.
- User may revise the plan.
- Draft items may be added, updated, removed, and reordered.
- Validation failures remain in the draft.
- Self-correction revises only the AI draft.
- Retry limit stops safely.
- Stale drafts cannot be submitted.
- Submission produces a human-reviewable proposal without applying it.

### Conversation

- Follow-up questions preserve relevant context.
- "Undo the last proposed item" updates the AI draft.
- Explanation questions do not create edits.
- Project switching does not leak context.
- Conversation cancellation stops the AI run.
- Provider-managed state is not the sole source of truth.

### Prompt Injection

- Malicious ontology labels cannot alter instructions.
- Malicious definitions cannot request secrets.
- FIBO text cannot grant tools.
- User content cannot add forbidden capabilities.
- Tool output is treated as data.
- The AI cannot expose credentials.
- Unrelated project data is not sent.

### Validation And Reasoning

- AI draft validation uses existing Entio validators.
- Preview uses existing Entio graph-change services.
- Reasoning uses existing Phase 4 services.
- SHACL uses existing Phase 4 services.
- FIBO search uses existing Phase 5 deterministic ranking.
- The model cannot override a blocking result.
- Final proposal impact matches the draft preview.

### Human Authority

- AI cannot approve.
- AI cannot reject.
- AI cannot apply.
- AI cannot roll back.
- Human reviewer permissions remain enforced.
- Apply remains an existing Entio human action.
- AI submission preserves attribution.

### Credentials And Provider

- Key remains server-side.
- Key is removed on logout, expiration, deletion, and restart.
- Provider headers and errors are redacted.
- Missing key does not affect non-AI functionality.
- One user's key cannot serve another user.
- Controlled fake provider supports deterministic tests.

### Limits

- Tool-call limit stops safely.
- Draft-edit limit stops safely.
- Correction-cycle limit stops safely.
- Context limit stops safely.
- Run timeout stops safely.
- Draft remains available after a limit.
- No duplicate concurrent draft mutation.

### Entio Help

- Help matches current feature availability.
- Help respects current permissions.
- Help does not claim unavailable UI actions.
- Error explanations map to real Entio error codes.
- Application guidance does not create ontology edits unless requested.

### Regression

All existing Phase 1 through Phase 6 tests must continue to pass.

## Success Criteria

Phase 7 is successful when:

- A user can connect their personal OpenAI API key and start an Entio AI conversation.
- The AI can explain the current ontology, reasoning, SHACL, FIBO, proposals, and application workflow.
- The AI can use approved tools to prepare every ontology edit supported by Entio's typed-edit boundary.
- The AI can compose multiple edits into one private AI draft.
- The AI can ask meaningful clarification questions.
- The AI can plan larger ontology changes.
- The AI can validate, preview, reason over, and SHACL-check its draft.
- The AI can revise its draft after structured failures.
- The AI can explain exactly what it changed and why.
- The AI can submit a completed draft for human review.
- The AI cannot approve, reject, apply, or roll back the proposal.
- The AI cannot edit project configuration.
- The AI cannot access arbitrary files, the shell, unrelated projects, or unauthorized sources.
- The AI cannot bypass deterministic Entio validation.
- Follow-up questions retain relevant conversational and draft context.
- Existing CLI, VS Code, web application, semantic engine, reasoning, SHACL, FIBO, collaboration, application, and rollback behavior remain compatible.
- The implementation is covered by capability, security, tool-schema, conversation, draft, prompt-injection, provider, limit, and end-to-end tests.

## Likely Follow-Up Work

Possible later phases include:

- Durable AI conversation and audit storage.
- Production-grade encrypted key storage.
- Additional AI providers.
- Voice interaction.
- Document-assisted ontology design.
- Entity resolution tools.
- AI-generated SHACL through complete typed backend support.
- Broader ontology refactoring tools.
- Agent orchestration across documents and data sources.
- Organization policy controls.
- Cost budgets and administrator controls.
- Production monitoring and evaluation.
- Fine-tuned or domain-specialized models.
