# ExecPlan: Phase 8 Scalable AI Ontology Workflow Orchestration

## Status

Approved and implementation-ready after the repository base and phase-status checks below are satisfied.

Implementation must proceed one slice at a time in the exact dependency order below.

## Source Spec

[`docs/specs/0014-phase-8-scalable-ai-ontology-workflow-orchestration.md`](../specs/0014-phase-8-scalable-ai-ontology-workflow-orchestration.md)

Primary scope source: [`docs/architecture/phase-8-scope.md`](../architecture/phase-8-scope.md).

## Implementation Prerequisite

Before Slice 1:

1. Read `AGENTS.md`, `README.md`, the approved Phase 8 spec, this ExecPlan, the Phase 8 scope, and the implement-core-feature skill.
2. Confirm Phase 7.5 is complete and merged into the approved base branch.
3. Confirm `README.md` and `AGENTS.md` identify Phase 8 as the active implementation phase.
4. Confirm the working tree is clean.
5. Confirm the local base branch is current with its remote according to `AGENTS.md`.
6. Run the current Phase 1 through Phase 7.5 verification suite or the approved repository baseline verification.
7. Stop if the base is missing required Phase 7.5 work, contains unexplained failures, has uncommitted overlapping work, or requires merge, rebase, reset, force, conflict resolution, or another destructive operation.

Do not begin Phase 8 from an uncertain or dirty base.

## Goal

Implement a Kotlin-owned, in-memory AI task and workspace orchestrator that can classify, retrieve, plan, execute, batch, validate, repair, explain, and submit simple through large ontology tasks while exposing only stage-appropriate capabilities and preserving human-only approval and application.

The implementation must scale deterministically to large fixtures without placing complete ontology content in provider context, must reuse existing typed edit and semantic-analysis paths, and must leave Phase 1 through Phase 7.5 behavior compatible.

## Current State

The approved base contains:

- Phase 7 conversations, turns, runs, immutable scopes, capability registry snapshots, provider/tool loops, private drafts, revisions, analysis, bounded correction, audits, SSE events, and explicit review submission.
- Phase 7.5 current-user model discovery, explicit selection and verification, immutable run model binding, and safe unavailable-model recovery.
- Bounded local entity, hierarchy, usage, reasoning, SHACL, proposal, activity, help, and FIBO capabilities.
- Existing typed ontology and SHACL preparation through `WebStageChangeRequest`, private-draft adapters, shared staging, proposal review, atomic application, reload, and rollback.
- A React assistant panel centered on conversations and one current private draft.
- In-memory development identity, collaboration, jobs, credentials, conversations, runs, drafts, audits, and events.

The current provider run is the main unit of progress. There is no authoritative multi-run `AiTask`, task transition table, work-package dependency graph, project map cache, stage bundle registry, batch executor, repair packet, task review package, or task-oriented UI.

## Target State

The completed Phase 8 base provides:

- Authoritative private `AiTask` and versioned `AiTaskWorkspace` state in Kotlin.
- Deterministic classification by family and size, legal transition enforcement, idempotent commands, limits, ownership, cancellation, pause/resume, and staleness.
- Fingerprinted compact project maps, bounded ontology neighborhoods, layered retrieval, safe caching, and context expansion.
- Versioned capability bundles selected by task stage, not by model/client request.
- Structured plans, dependency-ordered work packages, explicit clarification and checkpoints, and visible progress.
- Composite capabilities that prepare only existing typed Entio operations.
- Bounded draft batches with incremental preview, validation, diff, reasoning, and SHACL.
- Structured repair packets, bounded draft-only repair, revision/undo, and evidence-based follow-up.
- Complete task review packages submitted through the existing human-review workflow.
- Project-scoped versioned task HTTP/SSE contracts and an accessible React task experience.
- Permanent deterministic evaluation, performance, regression, and adversarial suites for small through large tasks.

## Resolved Planning Decisions

The following spec questions are resolved for this plan:

1. Existing Phase 7 conversation/run routes remain compatible. Task routes compose existing services rather than replacing or removing legacy resources during Phase 8.
2. The 500-entity fixture is the blocking response-time benchmark. The 1,000-entity fixture is a blocking boundedness/leakage/correctness test and records timing without establishing a universal hardware-independent latency threshold.
3. Reasoning runs when a package changes hierarchy, types, domains/ranges, OWL-relevant assertions, or imported/reused ontology structure. SHACL runs when a package changes data/shape sources, classes/properties targeted by relevant shapes, assertions/values, or shape constraints. Kotlin derives these flags from typed operations and configured sources.
4. Initial project-map domain areas use deterministic source and top-level hierarchy summaries only. Model-generated clustering is out of scope.
5. Exact elapsed-time defaults are finalized from Slice 12 fixture evidence; until then they are explicit policy fields with deterministic fake-clock tests.
6. Initial composite capabilities cover class models, property models, bounded domain-model batches, external reuse, entity refactor preparation, and repair only where all outputs map to approved existing typed operations. Unsupported operations return structured ambiguity/unsupported results rather than raw fallbacks.
7. A task whose bound model becomes unavailable pauses safely. Resumption requires explicit user confirmation and creates a new execution provenance segment with a newly verified model; it never silently edits the original binding.

## Provisional Elapsed-Time Policy

These values are implementation defaults until Slice 12 replaces or confirms them using deterministic fixture evidence:

- Maximum active execution time for one work package: 5 minutes.
- Maximum active execution time for one AI task: 30 minutes.
- Maximum one provider request at a time for a mutating task.
- Time spent waiting for clarification, plan confirmation, or a human checkpoint does not count as active execution time.
- Cancellation must interrupt the current provider request where supported and must prevent the next capability call, batch append, or analysis step.
- Reaching a work-package time limit preserves the private workspace, records the current package as incomplete, and moves the task to `LIMIT_REACHED`.
- Reaching the task time limit preserves the private workspace and prevents further execution until the user narrows the task or starts a new task.
- These values are development safeguards, not production service guarantees.

Slice 12 may change these defaults only when deterministic fixture evidence supports the change. Any change must be recorded in `docs/decisions/phase-8-slice-12-evaluation-and-verification.md`.

## Architecture And Ownership

Primary ownership remains:

```text
web-server/src/main/kotlin/com/entio/web/ai/
  task contracts, stores, transitions, classification, planning,
  retrieval/context, bundles, orchestration, batching, analysis,
  repair, review packages, audits, events, provider coordination

web-server/src/main/kotlin/com/entio/web/contract/
  versioned task transport DTOs only

web-server/src/main/kotlin/com/entio/web/Application.kt
  authenticated/project-scoped route wiring only

web-app/src/web/
  transport contracts, clients, query/mutation state, SSE mapping

web-app/src/workbench/ai/ and existing assistant surfaces
  presentation and explicit user actions only
```

Existing semantic modules remain authoritative dependencies. If a reusable bounded semantic query is demonstrably missing, stop and revise the plan before editing a lower-level module; do not duplicate semantic behavior in `web-server` or React.

## Expected Affected Files And Modules

Primary allowed implementation area:

```text
web-server/src/main/kotlin/com/entio/web/Application.kt
web-server/src/main/kotlin/com/entio/web/ai/
web-server/src/main/kotlin/com/entio/web/contract/
web-server/src/main/resources/entio-help/v1/
web-server/src/test/kotlin/com/entio/web/
web-server/src/test/kotlin/com/entio/web/ai/
web-server/src/test/resources/ (only deterministic Phase 8 fixtures if needed)

web-app/src/web/
web-app/src/workbench/AiAssistantPanel.tsx
web-app/src/workbench/AiAssistantPanel.test.tsx
web-app/src/workbench/ai/
web-app/src/styles.css
web-app/e2e/

docs/architecture/ai-subsystem-map.md
docs/decisions/phase-8-*.md
docs/phase-summaries/phase-8-summary.md
```

Generated large fixtures should be produced deterministically in test code or focused test-resource generators. Do not commit copied variants of ontology files when deterministic generation is sufficient.

Default forbidden implementation targets:

```text
core-types/
semantic-engine/
validation-engine/
graph-diff/
cli/
shared/
vscode-extension/
examples/
Gradle module structure
```

Stop and amend the approved spec/plan before changing a forbidden target.

## Global Implementation Rules

- Implement slices serially; do not combine slices or implement later slices early.
- Each slice is an independent branch, testable unit, focused commit, remote branch, completion artifact, and non-fast-forward local merge into the accumulated approved base.
- Start every slice from a clean accumulated `main` (or the repository's then-approved base) and verify it has not unexpectedly changed before local merge.
- Use existing libraries and services. Add no framework, database, queue, graph store, OpenAI SDK, or Gradle module.
- Preserve old Phase 7 HTTP resources and DTO behavior unless a slice explicitly adds a compatibility adapter and regression coverage.
- Never use a real API key or unrestricted network in automated tests.
- Every task mutation command uses current user/project/task scope, expected workspace revision, and idempotency.
- Every provider request captures the current task execution segment's verified model and frozen capability bundle.
- Every slice completion artifact uses exactly `docs/decisions/phase-8-slice-<n>-<slug>.md`; update an existing exact file rather than creating a duplicate.
- Do not push or merge a slice whose required verification fails.

## Dependency Order

All slices are serial:

```text
1. Task contracts, state machine, and in-memory workspace
2. Task classification, policy, ownership, limits, and lifecycle controls
3. Fingerprinted project map, bounded retrieval, and cache
4. Task context packages and stage capability bundles
5. Structured planning, work packages, clarification, and checkpoints
6. Work-package orchestration, composite capabilities, and draft batching
7. Incremental deterministic analysis and staleness
8. Repair packets, bounded repair, undo, and evidence-based follow-up
9. Final review package, submission, audit, and collaboration visibility
10. Versioned task web contracts, events, and compatibility boundary
11. React task workspace and human checkpoints
12. Permanent evaluations, large-ontology performance, security, and phase completion
```

Later slices depend on the completed and locally merged output of every earlier slice.

## Slice 1: Task Contracts, State Machine, And In-Memory Workspace

### Goal

Add the authoritative task/workspace data model, legal transition table, versioned in-memory store, and immutable task scope/model provenance without provider execution or UI changes.

### Allowed files and modules

- `web-server/src/main/kotlin/com/entio/web/ai/` task/session contract and store files.
- Matching `web-server/src/test/kotlin/com/entio/web/ai/` tests.
- `docs/architecture/ai-subsystem-map.md` if task ownership needs to be added.
- `docs/decisions/phase-8-slice-1-task-workspace.md`.

### Forbidden actions and modules

- No provider calls, capability changes, routes, React, planning, execution, semantic queries, or draft analysis.
- No durable persistence or production queue.
- No semantic modules or shared product types.
- No automatic reconstruction from chat after restart.

### Expected changes or output

- Add immutable contracts for task ID, family, size, status, scope snapshot, execution segment/model provenance, workspace revision, assumptions, questions, entity references, plan/work-package placeholders, private-draft reference, analysis references, counters, and timestamps.
- Add a Kotlin transition table and command precondition service.
- Add an in-memory task/workspace store keyed by owner, project, and task with synchronized compare-and-set revision updates.
- Reject invalid transitions, terminal mutation, stale mutation, cross-scope reads, and revision conflicts.
- Establish server-owned default policy fields for package, draft, batch, repair, tool-call, context, concurrency, and elapsed-time limits.
- Update the AI subsystem map with task versus conversation/run ownership.

### Tests

- Every legal and representative illegal transition.
- Task creation invariants and immutable scope/model provenance.
- Revision compare-and-set, replay, ownership, cross-project isolation, and non-disclosing missing-task behavior.
- Process-memory cleanup/restart semantics.
- Policy invariant and serialization stability tests.

### Verification commands

```bash
./gradlew :web-server:test --tests '*AiTaskContractsTest' --tests '*AiTaskStateMachineTest' --tests '*AiTaskStoreTest'
./gradlew :web-server:test
git diff --check
```

### Stop conditions

- Stop if task state must live in `core-types`, `shared`, project configuration, or durable storage.
- Stop if legal transitions cannot be expressed deterministically in Kotlin.
- Stop if task lookup leaks whether another user's task exists.

## Slice 2: Task Classification, Policy, Ownership, Limits, And Lifecycle Controls

### Goal

Create meaningful tasks from user objectives, classify family/size proportionately, and enforce active-task, pause/resume, cancellation, limit, and explicit model-rebind lifecycle rules.

### Allowed files and modules

- Task/classifier/policy services under `web-server/src/main/kotlin/com/entio/web/ai/`.
- Existing Phase 7 classifier/run/model-binding adapters where additive composition is required.
- Matching server tests.
- `docs/decisions/phase-8-slice-2-task-lifecycle.md`.

### Forbidden actions and modules

- No retrieval, planning, edit execution, analysis, routes, or React.
- No model-chosen task status or policy override.
- No silent model switching or recursive tasks.
- No production scheduler/queue.

### Expected changes or output

- Add structured classification into the approved families and simple/medium/large sizes.
- Reuse deterministic boundary checks for forbidden, clarification, and focused-edit intent where appropriate.
- Create one active mutating task per user/project and at most three read-only explanation tasks.
- Add pause, resume, cancel, safe limit preservation, and stale/model-unavailable pause behavior.
- Add explicit execution-segment rebind after user-confirmed newly verified model selection while preserving prior provenance.
- Keep classification evidence bounded and auditable; reject out-of-scope requests before mutation bundles exist.

### Tests

- Representative family/size matrix and stable classification boundaries.
- Simple task skips unnecessary planning marker; medium/large markers are correct.
- Active-task concurrency, read-only concurrency, cancellation, pause/resume, and terminal behavior.
- Model unavailable pauses without losing workspace; explicit rebind creates a new provenance segment.
- Forbidden requests never receive mutation-ready state.

### Verification commands

```bash
./gradlew :web-server:test --tests '*AiTaskClassifierTest' --tests '*AiTaskLifecycleServiceTest' --tests '*AiTaskPolicyTest'
./gradlew :web-server:test
git diff --check
```

### Stop conditions

- Stop if classification grants permission or reviewer authority.
- Stop if resumption would silently change models.
- Stop if lifecycle state cannot be enforced without client-owned truth.

## Slice 3: Fingerprinted Project Map, Bounded Retrieval, And Cache

### Goal

Build deterministic compact project maps, layered search/neighborhood retrieval, project-wide summary scans, and safe fingerprint-keyed caches without sending complete ontologies.

### Allowed files and modules

- AI context/read adapters under `web-server/src/main/kotlin/com/entio/web/ai/`.
- Existing `web-server` read-only project, reasoning, SHACL, FIBO, staging, and project-registry adapters as consumers.
- Server tests and deterministic test-only large fixture generators/resources.
- `docs/decisions/phase-8-slice-3-bounded-retrieval.md`.

### Forbidden actions and modules

- No new RDF parser, semantic index, vector store, embedding service, graph database, or model-generated clustering.
- No unbounded hierarchy/entity/source payload.
- No model/provider calls, task execution, routes, or UI.
- No changes to lower semantic modules without plan revision.

### Expected changes or output

- Add a project map keyed by project fingerprint and retrieval-policy version.
- Include sources/roles, namespaces, counts, deterministic top-level hierarchy/domain summaries, external ontology usage, reasoning/SHACL availability, staged counts, and bounded naming/IRI conventions.
- Add bounded neighborhood queries for descriptor, parents/children, properties, domains/ranges, individuals, asserted/inferred markers, relevant shapes/findings, usage, staged impact, and FIBO candidates.
- Add explicit paginated expansions by approved direction/category.
- Add bounded project-analysis summaries for the approved initial checks using existing descriptors and services.
- Cache only stable maps/descriptors/neighborhoods and invalidate by project/reasoning/SHACL/draft fingerprints as applicable.
- Never cache credentials, cross-user private state, or unfingerprinted findings.

### Tests

- Stable ordering/fingerprint tests and map size bounds.
- Exact/normalized/semantic/FIBO layering and source preservation.
- Neighborhood depth/entity/byte/page/source limits and asserted/inferred markers.
- Cache hit, miss, owner isolation where relevant, and fingerprint invalidation.
- No full-source/full-project leakage assertions.
- Generated 500- and 1,000-entity bounded retrieval tests.

### Verification commands

```bash
./gradlew :web-server:test --tests '*AiProjectMapServiceTest' --tests '*AiOntologyNeighborhoodServiceTest' --tests '*AiContextCacheTest' --tests '*AiLargeOntologyRetrievalTest'
./gradlew :web-server:test
git diff --check
```

### Stop conditions

- Stop if required semantic behavior would be duplicated in `web-server`.
- Stop if the full ontology/source must be serialized for normal retrieval.
- Stop if cache freshness cannot be tied to authoritative fingerprints.
- Stop if generated fixtures create duplicate committed ontology assets.

## Slice 4: Task Context Packages And Stage Capability Bundles

### Goal

Construct bounded, fingerprinted context per task/work package and expose only a frozen server-selected capability bundle for the current stage.

### Allowed files and modules

- AI context package, capability registry, scope, and provider request assembly in `web-server`.
- Matching server tests.
- `docs/decisions/phase-8-slice-4-context-and-bundles.md`.

### Forbidden actions and modules

- No edit execution, planning logic, analysis, repair, routes, or UI.
- No client/model-provided bundle names as authority.
- No capability for approval, rejection, apply, rollback, configuration, shell, filesystem, raw RDF/SPARQL, secrets, or unrestricted network.
- No whole-project provider context.

### Expected changes or output

- Add versioned exploration, planning, ontology-editing, SHACL, analysis, repair, and help bundles.
- Map task status/package policy to the one allowed bundle for each provider step.
- Freeze bundle ID/version and capability definitions into provider/run audit context.
- Build task context from the current workspace, project map, selected neighborhoods, relevant analysis/staging/FIBO evidence, rules, questions, and draft summary.
- Enforce initial 20-entity and expanded 50-entity limits, 10 FIBO candidates, 20 SHACL findings, bounded bytes, and current fingerprints.
- Reject stale context, repeated unavailable expansion, capability calls outside the frozen bundle, and model/client attempts to widen scope.

### Tests

- Bundle contents and stage mapping.
- Explanation/help bundles have no mutation; repair has no submission/application; editing has only approved typed operations.
- Registry snapshots reject cross-bundle and stale calls.
- Context deterministic ordering, byte/entity limits, fingerprints, and untrusted-data boundaries.
- Provider request tests proving no full ontology, credentials, or unrelated private task state.
- Prompt-injection attempts cannot alter bundle or scope.

### Verification commands

```bash
./gradlew :web-server:test --tests '*AiCapabilityBundleRegistryTest' --tests '*AiTaskContextPackageBuilderTest' --tests '*AiCapabilityRegistryTest' --tests '*OpenAiResponsesClientTest'
./gradlew :web-server:test
git diff --check
```

### Stop conditions

- Stop if all capabilities must be exposed simultaneously.
- Stop if model text or React must decide bundle membership.
- Stop if safe context cannot be bounded before provider serialization.

## Slice 5: Structured Planning, Work Packages, Clarification, And Checkpoints

### Goal

Add versioned plans and dependency-ordered work packages with deterministic validation, material clarification, explicit confirmation, and checkpoint state.

### Allowed files and modules

- Task planning/checkpoint services and planning capability definitions in `web-server`.
- Task store/contracts from prior slices and matching tests.
- `docs/decisions/phase-8-slice-5-planning-checkpoints.md`.

### Forbidden actions and modules

- No ontology/SHACL draft mutation or analysis execution.
- No silent plan mutation or confirmation inference.
- No cyclic or missing work-package dependencies.
- No provider-generated reviewer approval.

### Expected changes or output

- Add plan, plan revision, work package, risk flag, estimate, dependency, evidence, status, and confirmation contracts.
- Validate unique IDs, acyclic dependencies, package/edit limits, allowed bundle, expected sources, and confirmation rules.
- Support simple-task direct readiness, medium short plans, and mandatory large plans.
- Require confirmation for large edit counts, deletion, hierarchy refactor, material external reuse, high-impact SHACL, multi-source work, identity ambiguity, or user request.
- Add bounded open questions/assumptions and explicit answer/update behavior that resumes the same package.
- Add planning and mid-task checkpoints with continue, revise, answer, pause, and cancel actions.
- Preserve every plan revision and prevent unconfirmed revisions from executing.

### Tests

- Simple, medium, large, destructive, SHACL, reuse, and cross-source plan cases.
- DAG validation, stable order, missing/cyclic dependency rejection, and package limits.
- Explicit confirmation/version matching and stale confirmation rejection.
- Clarification updates task state and resumes the intended package.
- Silent revision, provider-claimed confirmation, and replay attempts fail.

### Verification commands

```bash
./gradlew :web-server:test --tests '*AiWorkflowPlanTest' --tests '*AiCheckpointServiceTest' --tests '*AiTaskPlanningServiceTest'
./gradlew :web-server:test
git diff --check
```

### Stop conditions

- Stop if destructive or large plans can execute without explicit confirmation.
- Stop if dependency correctness relies on the model.
- Stop if confirmation could be confused with proposal approval.

## Slice 6: Work-Package Orchestration, Composite Capabilities, And Draft Batching

### Goal

Execute confirmed packages serially, coordinate provider steps and higher-level capabilities, and append bounded groups of ordinary typed operations to one private task draft.

### Allowed files and modules

- Task orchestrator/executor, provider coordination, composite capability, typed draft adapter, and batching services in `web-server`.
- Existing Phase 7 conversation/run/draft/capability services as dependencies or compatibility adapters.
- Matching server tests.
- `docs/decisions/phase-8-slice-6-orchestration-batching.md`.

### Forbidden actions and modules

- No analysis/repair implementation beyond hooks for later slices.
- No parallel package execution.
- No raw RDF, direct staging/source mutation, approval, apply, or rollback capability.
- No background task without a user-created task and confirmed required checkpoints.
- No package execution after a blocked dependency.

### Expected changes or output

Before implementing composite capabilities, create:

```text
docs/decisions/phase-8-slice-6-composite-capability-inventory.md
```

For each candidate composite capability, record:

- capability name;
- user intent it supports;
- existing read services used;
- existing typed edit operations produced;
- maximum output size;
- ambiguity conditions;
- unsupported conditions;
- required validation;
- tests proving parity with normal Entio editing;
- final status: `APPROVED`, `CLARIFICATION_REQUIRED`, `EXPLANATION_ONLY`, or `UNSUPPORTED`.

Only inventory entries marked `APPROVED` may be exposed to the orchestrator.

Then:

- Add serial work-package execution with dependency/status/revision checks and ordered progress events.
- Integrate the frozen task model binding, context package, bundle snapshot, provider limits, cancellation, and audit references.
- Add bounded composite preparation for class model, property model, domain-model batch, external reuse, and entity refactor using existing deterministic reads and typed preparation.
- Return structured ambiguity/unsupported results instead of guessing or falling back to raw graphs.
- Add a draft batch service that validates/prepares all operations before atomically appending a bounded batch to the private draft.
- Attribute every draft item to task, package, execution segment, user, and provider run.
- Enforce at most 20 items per batch, 100 per task, 30 tool calls per package, 200 per task, and one mutating task per user/project.
- Preserve Phase 7 focused conversation behavior through compatibility tests.

### Tests

- Composite-capability inventory completeness and status enforcement.
- No capability missing from the inventory can be exposed.
- Dependency-ordered serial execution and blocked-dependent behavior.
- Cancellation between calls/batches, pause/checkpoint interruption, replay/idempotency, and limit preservation.
- Composite output maps only to approved typed edits and matches manually staged preparation semantics.
- Batch atomicity on one invalid operation and correct multi-source typed preparation.
- 50-edit deterministic task across bounded batches.
- No shared staging/source mutation before explicit submission/approval.
- Existing focused Phase 7 draft tests remain green.

### Verification commands

```bash
./gradlew :web-server:test --tests '*AiWorkPackageExecutorTest' --tests '*AiCompositeCapabilityServiceTest' --tests '*AiDraftBatchServiceTest' --tests '*AiConversationServiceTest'
./gradlew :web-server:test
git diff --check
```

### Stop conditions

- Stop if the inventory is missing, incomplete, or cannot prove parity with normal Entio editing.
- Stop if a composite capability cannot express output as existing typed requests.
- Stop if batch append is partially visible after one item fails.
- Stop if package execution requires parallelism or an external queue.
- Stop if Phase 7 private draft/review semantics would be bypassed.

## Slice 7: Incremental Deterministic Analysis And Staleness

### Goal

Run fingerprinted preview, validation, diff, reasoning, and SHACL at batch/package boundaries; derive relevance in Kotlin; and stop safely on blocking or stale results.

### Allowed files and modules

- Task incremental-analysis/staleness services in `web-server`.
- Existing private-draft analysis, reasoning, SHACL, diff, staging, project fingerprint, and collaboration adapters as dependencies.
- Matching tests.
- `docs/decisions/phase-8-slice-7-incremental-analysis.md`.

### Forbidden actions and modules

- No model judgment replacing analysis or changing finding severity.
- No validation-rule weakening.
- No repair implementation beyond emitting structured finding inputs.
- No source writes or review submission.

### Expected changes or output

- Add analysis stage/result contracts with `VALID`, `WARNING`, `BLOCKED`, `STALE`, `INCOMPLETE`, and `FAILED`.
- Run typed preparation, preview, validation, semantic diff, and derived reasoning/SHACL stages for each required batch/package.
- Derive reasoning and SHACL relevance from typed operations and configured sources using the resolved rules.
- Store current analysis references/history keyed to task, workspace, package, draft, project, reasoning, and SHACL fingerprints.
- Block package completion/dependents on blocked, stale, incomplete, or failed analysis.
- Run final combined analysis only after required packages complete.
- Observe project/collaboration fingerprint changes, mark task stale, invalidate caches/analysis, stop mutation, refresh, and require confirmation if meaning changed.

### Tests

- Analysis ordering, relevance derivation, skipped-not-relevant stages, warning versus blocking behavior.
- Fingerprint matching and stale result rejection at every stage.
- Package blocking and later dependency prevention.
- Project change during retrieval, execution, validation, and final analysis.
- Current draft preservation and revalidation after refresh.
- Existing reasoning, SHACL, proposal-impact, and draft-analysis regressions.

### Verification commands

```bash
./gradlew :web-server:test --tests '*AiIncrementalValidationServiceTest' --tests '*AiTaskStalenessServiceTest' --tests '*AiDraftAnalysisTest'
./gradlew :web-server:test
git diff --check
```

### Stop conditions

- Stop if the model must decide whether deterministic checks passed.
- Stop if analysis cannot be tied to current fingerprints.
- Stop if a stale task can mutate or submit before refresh/revalidation.

## Slice 8: Repair Packets, Bounded Repair, Undo, And Evidence-Based Follow-Up

### Goal

Convert deterministic findings to structured repair packets, execute bounded draft-only repairs, support package/item undo and revision, and ground follow-up answers in task evidence.

### Allowed files and modules

- Repair, revision, follow-up context, and task workspace services in `web-server`.
- Existing private draft mutation/correction services as dependencies.
- Matching tests.
- `docs/decisions/phase-8-slice-8-repair-and-revision.md`.

### Forbidden actions and modules

- No repair of applied/shared project state.
- No invented finding, passing status, or allowed repair action.
- No weakening/removing validation rules to pass.
- No approval, apply, rollback, route, or UI work.

### Expected changes or output

Before implementing automatic repair, create:

```text
docs/decisions/phase-8-slice-8-repair-code-inventory.md
```

For each deterministic finding code considered for repair, record:

- finding code;
- deterministic source;
- affected entity and draft-item references;
- allowed typed repair operations;
- whether business clarification may be required;
- whether automatic repair is safe;
- required revalidation;
- tests;
- final status: `AUTO_REPAIRABLE`, `CLARIFICATION_REQUIRED`, `EXPLANATION_ONLY`, or `UNSUPPORTED`.

Unknown findings and findings not marked `AUTO_REPAIRABLE` must never be repaired automatically.

Then:

- Add repair packets with stable finding, package, item/entity, expected/actual, source, evidence, allowed action, and deterministic candidate references.
- Map only known deterministic finding families to approved typed repair actions; unsupported findings pause or fail safely.
- Add repair controller limits of three cycles per package and eight per task, with original findings and every revision retained.
- Require reanalysis after each repair and keep packages blocked until current analysis permits completion.
- Pause for user clarification when multiple repairs require business meaning.
- Add undo latest item/package, revise package, remove proposed entity, change assumption/external choice, and rerun behavior within the private workspace.
- Build follow-up context from task state/evidence rather than conversation replay alone.

### Tests

- Repair-code inventory completeness and status enforcement.
- Unknown and non-auto-repairable findings never enter automatic repair.
- Repair packet completeness, stable ordering, allowed-action mapping, and unsupported findings.
- Draft-only mutation, retained original findings/history, mandatory reanalysis, and bounded cycles.
- Ambiguous repairs pause; model cannot claim success or invent actions.
- Item/package undo and dependency-safe revision.
- Follow-up “why/what changed/what failed/what remains” references authoritative evidence.
- Existing Phase 7 correction and draft revision regressions.

### Verification commands

```bash
./gradlew :web-server:test --tests '*AiRepairPacketBuilderTest' --tests '*AiRepairControllerTest' --tests '*AiTaskRevisionServiceTest' --tests '*AiTaskFollowUpContextTest'
./gradlew :web-server:test
git diff --check
```

### Stop conditions

- Stop if the repair-code inventory is missing, incomplete, or cannot map a finding to a safe typed operation.
- Stop if repair requires a raw graph edit or unsupported typed operation.
- Stop if original findings or revision history would be overwritten.
- Stop if ambiguous business meaning would be selected without the user.

## Slice 9: Final Review Package, Submission, Audit, And Collaboration Visibility

### Goal

Create a complete current review package, submit the exact task draft through existing review submission, extend traceability, and preserve private-versus-shared collaboration boundaries.

### Allowed files and modules

- Task review/audit services and existing AI review submission adapters in `web-server`.
- Existing collaboration/activity mappings only where submitted task metadata is exposed.
- Matching tests.
- `docs/decisions/phase-8-slice-9-review-and-audit.md`.

### Forbidden actions and modules

- No AI approval, rejection, application, rollback, or reviewer permission.
- No submission from incomplete/stale/blocked work.
- No private conversation/plan/repair/provider payload in shared collaboration state.
- No route or React changes yet.

### Expected changes or output

- Build an immutable review package containing the spec-required objective, plan/packages, fingerprints, summaries, analysis references, sources, dependencies, assumptions, warnings, questions, rationale, submitter, and timestamps.
- Require completed required packages and current final combined analysis.
- Submit exactly the current private typed draft through `AiReviewSubmissionService`/ordinary staging and return the proposal review reference.
- Preserve AI task/package/user attribution on submitted staging/proposal metadata without changing reviewer authority.
- Extend audit records with task, execution segment, bundle, plan/draft revisions, analyses, repair cycles, checkpoints, final proposal, timing, and safe usage metadata.
- Expose only post-submission task summary/rationale/source attribution to collaborators.

### Tests

- Review package completeness and stable fingerprint.
- Not-ready, stale, changed-draft, repeated submission, and idempotency behavior.
- Exact typed-draft import and ordinary proposal route.
- No source mutation before separate human approval/application.
- Contributor versus reviewer authority and capability-registry exclusion.
- Audit redaction and private collaboration isolation.

### Verification commands

```bash
./gradlew :web-server:test --tests '*AiReviewPackageBuilderTest' --tests '*AiReviewSubmissionServiceTest' --tests '*AiTaskAuditTest' --tests '*AiTaskCollaborationPrivacyTest'
./gradlew :web-server:test
git diff --check
```

### Stop conditions

- Stop if review packaging grants or implies approval.
- Stop if submission cannot prove exact draft/fingerprint identity.
- Stop if private task state would enter shared collaboration payloads.

## Slice 10: Versioned Task Web Contracts, Events, And Compatibility Boundary

### Goal

Expose authenticated project-scoped task resources, commands, workspace views, review submission, and private ordered events while preserving Phase 7 routes.

### Allowed files and modules

- `web-server/src/main/kotlin/com/entio/web/Application.kt`.
- AI web boundary/mapper and `web-server/src/main/kotlin/com/entio/web/contract/` DTOs.
- Entio help resources for task status/actions/errors.
- Application/web-contract tests.
- `docs/decisions/phase-8-slice-10-task-web-contracts.md`.

### Forbidden actions and modules

- No semantic policy in routes/DTO mappers.
- No removal or meaning change to supported Phase 7 conversation/run/draft routes.
- No credentials, raw provider objects, full ontology, private cross-user state, or reviewer authority in DTOs/events.
- No React changes.

### Expected changes or output

- Add create/read/message/clarification/plan-confirm/execute/pause/resume/cancel/workspace/draft/analysis/review-package/events/submit task endpoints from the spec.
- Require current identity/project scope; require idempotency and expected revision for provider-triggering/mutating commands.
- Map structured task errors and safe next actions consistently.
- Add private ordered SSE events for task/context/question/plan/package/batch/analysis/repair/checkpoint/pause/review/failure/cancel/limit stages.
- Reuse bounded retention, reconnect cursor, and resynchronization behavior from Phase 7.
- Keep old resources compatible and add task references only additively where useful.
- Add help content for statuses, limits, stale tasks, checkpoints, and review handoff.

### Tests

- Full route status/body/idempotency/revision matrix.
- Cross-user/project, permission, missing, stale, conflict, and limit errors.
- DTO redaction and bounded payload tests.
- SSE order, reconnect, retention gap, privacy, cancellation, and terminal events.
- Existing Phase 7 HTTP/SSE compatibility regressions.

### Verification commands

```bash
./gradlew :web-server:test --tests '*AiTaskWebContractTest' --tests '*ApplicationTest' --tests '*AiWebContractTest'
./gradlew :web-server:test
git diff --check
```

### Stop conditions

- Stop if routes must reproduce orchestration or semantic logic.
- Stop if private events cannot be scoped to the task owner.
- Stop if old supported clients would break without an approved compatibility decision.

## Slice 11: React Task Workspace And Human Checkpoints

### Goal

Add an accessible task-oriented workbench over authoritative server contracts, including plan, progress, context, draft, analysis, repair, checkpoints, and final proposal handoff.

### Allowed files and modules

- `web-app/src/web/` task DTO/client/query/SSE files and tests.
- `web-app/src/workbench/AiAssistantPanel.tsx` and tests.
- `web-app/src/workbench/ai/` task components and tests.
- `web-app/src/styles.css`.
- Focused `web-app/e2e/` task journey.
- `docs/decisions/phase-8-slice-11-task-ui.md`.

### Forbidden actions and modules

- No semantic/task transition logic in TypeScript.
- No client-created fingerprints, bundle selection, inferred completion, or local approval/application authority.
- No direct provider calls, credential storage, or raw ontology rendering.
- No unrelated visual redesign.

### Expected changes or output

- Add task APIs, query keys, mutations, event parsing, refetch/resynchronization, and structured failure mapping.
- Render task header with objective/status/model/project/current package/progress and pause/resume/cancel actions.
- Render plan packages/dependencies/estimates/assumptions/questions and explicit confirm/revise/answer controls.
- Render bounded context/evidence, draft grouped by package, revision history, analysis, findings, repair packets, warnings, and unresolved work.
- Render final review package and explicit submit-for-human-review action followed by authoritative proposal link.
- Use package counts/stages rather than fake percentages; provide accessible live status and disabled-action explanations.
- Preserve conversation continuity and non-AI workbench usability during long-running, paused, stale, disconnected, unavailable-model, failed, cancelled, and limit states.

### Tests

- Component tests for every task status and checkpoint action.
- Query invalidation, expected revision/idempotency, SSE order/gap recovery, cancellation, and stale conflict behavior.
- No-success-on-failure and no-review-control-authority assertions.
- Accessible labels/live regions/focus behavior.
- Deterministic browser journey from simple task through review submission plus a paused large-plan journey.
- Existing assistant/model-selection/proposal/workbench tests.

### Verification commands

```bash
(cd web-app && npm test)
(cd web-app && npm run build)
(cd web-app && npm run test:e2e)
git diff --check
```

### Stop conditions

- Stop if React must decide task legality or semantic validity.
- Stop if event loss cannot recover from authoritative HTTP state.
- Stop if UI actions expose approval/application to the model or task executor.

## Slice 12: Permanent Evaluations, Large-Ontology Performance, Security, And Phase Completion

### Goal

Prove Phase 8 end to end with deterministic fixtures, enforce bounded performance/security properties, resolve policy timing defaults from evidence, run all regressions, and document completion.

### Allowed files and modules

- Focused Phase 8 evaluation/test harnesses under existing `web-server` and `web-app` test trees.
- Deterministic test-only fixture generators/resources.
- Existing AI security/provider/request tests.
- `docs/decisions/phase-8-slice-12-evaluation-and-verification.md`.
- `docs/phase-summaries/phase-8-summary.md`.
- `README.md`, `AGENTS.md`, and `docs/architecture/ai-subsystem-map.md` only to describe verified completed behavior after all tests pass.

### Forbidden actions and modules

- No new production feature behavior except evidence-driven limit/default corrections required to satisfy the approved spec.
- No real provider key/network, benchmark service, telemetry platform, database, queue, billing, or administration.
- No weakening tests or limits to claim completion.
- No committed duplicate fixture copies or generated build output.

### Expected changes or output

- Add permanent deterministic scenarios for small edits/explanations, medium lending, large 50-edit domain work, hierarchy refactor, SHACL failure/repair, FIBO reuse, stale collaboration, unavailable model, and safety attacks.
- Add deterministic 500- and 1,000-entity generators/fixtures.
- Prove bounded project/context payloads and absence of complete ontology/source leakage at both sizes.
- Establish and document the blocking 500-entity response-time budget on supported development hardware; record 1,000-entity timing as diagnostic while keeping boundedness/correctness blocking.
- Record the benchmark environment in the Slice 12 decision artifact:
  - operating system;
  - processor architecture;
  - available memory;
  - JVM version;
  - Node version;
  - warm-up procedure;
  - number of measured runs;
  - median observed time;
  - maximum observed time;
  - final blocking threshold.
- Performance tests must not depend on a live model or external network.
- Finalize package/task elapsed-time defaults using fake-clock and measured fixture evidence.
- Measure completion, correct selection/source, typed-edit validity, validation/repair, call count, duration, usage, unauthorized attempts, and review completeness without asserting model quality from live calls.
- Run adversarial tests for prompt injection, capability expansion, direct write/config/shell/files/network/secrets, approval/application, cross-user/project access, replay, stale data, malformed plans, and limit bypass.
- Update summary and current-phase docs only after complete verification.

### Tests

- All primary end-to-end journeys in the scope/spec.
- Permanent small/medium/large/explanation/safety evaluation matrix.
- 50+ edit and 500/1,000 entity cases.
- Full Kotlin, React, browser, VS Code, security, redaction, and compatibility regression.

### Verification commands

```bash
./gradlew test
./gradlew build
./gradlew check
(cd web-app && npm ci && npm test && npm run build && npm run test:e2e)
(cd vscode-extension && npm ci && npm test)
git diff --check
git status --short
```

### Stop conditions

- Stop if the 500-entity blocking budget fails or context is unbounded.
- Stop if the 1,000-entity case leaks full project/source content or fails correctness.
- Stop if any existing Phase 1 through Phase 7.5 regression fails.
- Stop if an evaluation requires a live credential or network.
- Stop if documentation would claim unverified or incomplete behavior.

## Test Plan

### Unit and contract tests

- Task/store/state/policy invariants.
- Classifier, plan DAG, checkpoints, bundles, context bounds, caches, batches, analysis relevance, repair mapping, review readiness, audits, DTOs, and errors.
- Pure deterministic tests use fake clocks, IDs, fingerprints, and provider scripts.

### Service and integration tests

- Task creation through review submission using copied/generated projects.
- Existing semantic/staging/draft/analysis/review services remain the execution path.
- Project mutation by another user makes active tasks stale.
- Provider/model failure preserves private state and requires explicit provenance-safe resumption.

### Provider-loop tests

- Deterministic fake provider scripts for clarification, planning, package execution, composite capabilities, repairs, follow-up, malformed calls, replay, limits, cancellation, and unavailable model.
- No automated call to OpenAI or another external network.

### Browser tests

- Simple focused edit through final review handoff.
- Large plan confirmation, package progress, checkpoint pause/resume, and cancellation.
- Event disconnect/resynchronization, stale task, repair, limit, provider failure, and accessible status.

### Performance and boundedness tests

- Deterministic 500-entity response-time gate.
- Deterministic 1,000-entity boundedness, leakage, ordering, and correctness gate with recorded diagnostic timing.
- 50+ edit batching and incremental validation.
- Cache hit/invalidation and unchanged-context provider-call reduction.

### Security tests

- Prompt injection across every untrusted source.
- Capability/bundle widening, scope/permission escalation, direct writes, reviewer actions, raw RDF/SPARQL, shell/files/network/secrets, replay, concurrency, and cross-user/project attempts.
- Redaction of credentials, headers, raw provider payloads, chain of thought, and unrelated private state.

### Regression tests

- All Phase 1 through Phase 7.5 Gradle tests.
- Existing React and Playwright workbench flows.
- Existing VS Code extension tests.

## Full Verification Commands

Run after all slices are locally merged into the accumulated base:

```bash
./gradlew test
./gradlew build
./gradlew check
(cd web-app && npm ci && npm test && npm run build && npm run test:e2e)
(cd vscode-extension && npm ci && npm test)
git diff --check
git status --short
```

An optional manual real-provider smoke test may be documented separately, requires a user-supplied credential, may incur cost, and is not a completion requirement.

## Rollback Notes

- Each slice must remain separately revertible through its focused commit and visible non-fast-forward merge boundary.
- Task resources are additive. Rolling back Phase 8 should leave Phase 7 conversation/run/draft routes and Phase 7.5 provider settings functional.
- In-memory task stores require no data migration. Rollback or restart discards private Phase 8 task state; communicate this before restarting an active development session.
- Cache rollback consists of removing Phase 8 in-memory cache wiring; no persisted cache migration exists.
- Composite operations and batches store ordinary typed requests. Never attempt to roll back by editing ontology files; discard the private task draft or reject the submitted ordinary proposal through existing controls.
- If a submitted proposal exists, use existing human proposal rejection/application rollback behavior rather than Phase 8 task state.
- Do not use destructive Git reset, forced push, source-file deletion, or manual graph reversal as rollback mechanisms.

## Risks And Assumptions

### Risks

- The orchestration surface is broad; serial slices and compatibility gates are required to prevent a second AI architecture from forming beside Phase 7.
- Existing read adapters may repeatedly load project state; large-fixture evidence may require bounded caching or batch adapters without moving semantic policy.
- Generic typed tool schemas can cause provider selection errors; composite capabilities need narrow required schemas and deterministic adapters.
- Long-running in-process coroutines can race cancellation, staleness, and concurrent user actions; workspace revision checks and ordered state transitions are mandatory.
- Large draft analysis may be expensive; incremental results must not be mistaken for final combined validity.
- Repair candidates may be technically valid but semantically ambiguous; user checkpoints must take precedence over automatic repair.
- Event streams are not authoritative storage; reconnect must refetch task state.
- In-memory tasks disappear on restart; UI/help must state this clearly.
- A fixed timing threshold can be flaky across hardware; only the documented 500-entity benchmark environment should enforce latency, while correctness/boundedness remains universal.

### Assumptions

- Existing typed preparation, private draft, analysis, and review submission services remain reusable without semantic-module changes.
- Existing project and proposal fingerprints are sufficient to detect relevant staleness; additional task/workspace fingerprints can be composed in `web-server`.
- Ktor coroutines and current in-memory patterns are sufficient for same-session asynchronous tasks; no external queue is required.
- The Phase 7.5 binding service can provide immutable model/policy provenance for task execution segments.
- Generated large fixtures can be deterministic and isolated under test code/resources.
- Existing development identity and permissions remain the Phase 8 authorization boundary.

## Boundary Check

- This plan fits Phase 8 and implements only the approved scalable orchestration scope.
- It avoids durable production storage, queues, billing, identity, document ingestion, autonomous multi-agent authority, and other current non-goals.
- Task/orchestration logic remains in `web-server`; React remains a transport/presentation client.
- Existing semantic modules and libraries remain authoritative; the plan does not reinvent RDF, OWL, SHACL, reasoning, or diff behavior.
- Higher-level capabilities produce existing typed edits and never raw RDF.
- Human approval/application remains outside every AI bundle and task command.
- No new module, framework, database, graph store, provider SDK, or unrestricted integration is introduced.

## Definition Of Done

Phase 8 is complete only when:

- All 12 slices have their own branch, implementation, tests, verification, focused commit, pushed remote branch, completion artifact, and clean non-fast-forward local merge.
- Task state, planning, retrieval, bundles, execution, batching, analysis, repair, review, web contracts, UI, evaluations, and security behavior satisfy the source spec.
- `README.md` and `AGENTS.md` identify Phase 8 as the active or completed phase as appropriate.
- The Slice 6 composite-capability inventory and Slice 8 repair-code inventory exist, are complete, and match the implemented allowlists.
- A deterministic large task prepares at least 50 valid typed edits in bounded batches and reaches a complete review package.
- 500- and 1,000-entity tests prove bounded context and no whole-ontology leakage; the documented 500-entity performance gate passes.
- Explicit submission hands the exact draft to the ordinary review workflow, and only separate human controls can approve/apply.
- Existing Phase 1 through Phase 7.5 behavior and all full verification commands pass.
- `docs/phase-summaries/phase-8-summary.md`, the AI subsystem map, and current-phase documentation accurately describe only verified implementation.
- The final worktree is clean, all slice branches are pushed, local accumulated `main` contains the slice merge boundaries, and `main` itself is not pushed unless separately authorized.

## Implementation Details To Resolve During Approved Slices

No product-scope question blocks implementation.

Slice owners must record evidence-based answers for:

- Exact class and package names after inspecting the current Phase 7/7.5 AI tree.
- The measured 500-entity latency budget.
- Whether the provisional 5-minute package limit and 30-minute task limit should be confirmed or changed.
- The approved composite-capability inventory created in Slice 6.
- The approved repair-code inventory created in Slice 8.
- Any Phase 7 DTO that needs an additive task reference for compatibility rather than replacement.

These are implementation details inside the approved plan, not permission to expand scope.

If an answer requires a forbidden module, new dependency or framework, durable infrastructure, scope expansion, or behavior inconsistent with the source spec, stop and revise the approved documents before implementation.