# ExecPlan: Phase 10 Materialize Inferred Relationships

## Status

Complete as of 2026-07-23. All slices were implemented in dependency order, verified, pushed on independent branches, and merged locally into `main`.

Implementation must proceed one slice at a time in the exact dependency order below. Slices must not be combined or implemented in parallel.

## Related Spec

- [Phase 10 Materialize Inferred Relationships](../specs/0018-phase-10-materialize-inferred-relationships.md)
- [Phase 10 Scope](../architecture/phase-10-scope.md)

## Goal

Let an authenticated user select supported inferred facts from one completed applied-graph reasoning result and stage them as ordinary asserted ontology changes. Kotlin must revalidate the current graph and inference set, resolve duplicates and writable sources, convert the complete selection into existing typed edits, attach reasoning provenance, and append the batch atomically to the existing shared review queue.

No Phase 10 action may write an ontology source directly or bypass preview, validation, semantic diff, reasoning/SHACL impact, human acceptance, atomic apply, reload verification, or rollback.

## Approved Product Decisions

These decisions come from the Phase 10 spec and must not be reopened silently during implementation:

- Supported inferred facts are subclass relationships, individual types, and object-property assertions only.
- Only complete, completed, applied-graph reasoning jobs are materializable.
- A retained reasoning job is materializable only by the user who submitted it.
- The server accepts job ID and opaque fact IDs, not client-authored triples.
- Fact identity is canonical, versioned, job-bound, graph-fingerprint-bound, and label-independent.
- The server reruns reasoning and confirms every selected inference immediately before staging.
- One request contains at most 100 facts and stages all or none.
- Duplicate checking covers applied, staged, proposal, and within-request facts.
- Source resolution uses subject ownership first; one candidate is automatic, several require explicit selection, and none blocks.
- The object's source does not override the subject's target source.
- Imported, bundled, FIBO, external, and read-only sources are never targets.
- Safe import-derived facts may be asserted locally and carry import-dependence metadata.
- Provenance is typed workflow metadata, not an ontology annotation or graph-diff fact.
- Materialized entries enter the current shared project review queue.
- React owns selection and presentation only.
- CLI, VS Code, ontology-map inference rendering, persistence, AI selection, and automatic materialization are deferred.

## Inference Identity Rules

Each materialization candidate has two distinct identities:

- `factId`: an opaque ID exposed to the browser and bound to the retained reasoning job, submitting user, project, and graph fingerprint.
- `semanticFactKey`: a server-only canonical identity based on inference type and canonical RDF terms.

The browser submits only `factId`.

Before staging, the server resolves each submitted `factId` to its retained `semanticFactKey`, reruns reasoning, and confirms that the same semantic key exists in the fresh result.

The fresh rerun is not expected to reproduce the original job-bound `factId`.

## Batch Duplicate Behavior

All-or-nothing applies to errors, not harmless existing entries.

For each selected fact:

- already asserted: reject the complete request;
- unsupported, stale, ambiguous, or invalid: reject the complete request;
- already staged or already represented by the current proposal: treat it as an idempotent existing result;
- new and valid: append it as a new staged entry.

If every selected fact is already staged or already represented by the current proposal, return success with the existing staged-entry IDs and make no state change.

Duplicate `factId` values or duplicate semantic facts repeated within the same request are malformed input and reject the complete request.

## Fresh Reasoning Graph

The pre-staging reasoning rerun uses the current applied project graph and its current import closure.

It excludes:

- private AI drafts;
- shared staged changes;
- current proposal preview changes;
- unapplied source edits.

Staged and proposed changes are checked separately during duplicate detection.

## Subject Ownership Rules

For source resolution:

- for a subclass relationship, the subject is the child class;
- for an individual type, the subject is the individual;
- for an object-property assertion, the subject is the assertion subject.

Resolution then follows these rules:

- one writable local source declaring the subject: choose it automatically;
- several writable local sources declaring the subject: require an explicit source choice;
- no writable local source declaring the subject: not stageable;
- subject declared only in imported, bundled, FIBO, external, or read-only sources: not stageable;
- the object's source never becomes the target merely because the object is local.

## Materialization Execution Limits

- Only one materialization request may execute at a time per project.
- The server must support cancellation or a bounded timeout using the existing semantic-job conventions.
- Timeout, cancellation, or reasoning failure must leave shared staging and proposal state unchanged.
- The UI must show that Entio is rechecking reasoning before staging.
- Slice 7 must record elapsed time for 1-fact, 25-fact, and 100-fact requests on the approved development fixture.
- Performance evidence must not depend on a live external service or network.

## Additional Materialization Rules

- Materialization never changes the retained reasoning job or its result.
- A staged fact remains an ordinary asserted addition even if a later reasoning run no longer entails it.
- Removing a previously materialized assertion uses the normal ontology editing workflow, not the Reasoning view.
- The UI must display the target source before the user submits the staging request.
- If import dependence cannot be proven from deterministic retained or freshly computed evidence, the candidate is marked unsafe rather than guessed.

## Current State

- `core-types/Phase4Contracts.kt` defines reasoning results, asserted/inferred origin, class relationships, individual types, property relationships, and graph/reasoner fingerprints.
- `core-types/TypedOntologyEdit.kt` already defines `AddSuperclassEdit`, `AssignTypeEdit`, and `AddObjectPropertyAssertionEdit`.
- `core-types/Phase25PlusContracts.kt` defines typed staged operations and staged entries but has no reasoning-materialization provenance.
- `semantic-engine/ReasoningService.kt` runs OWL API/HermiT reasoning and returns deterministic normalized facts.
- `semantic-engine/TypedOntologyEditTranslator.kt` translates all three required typed edits into graph additions.
- `web-server/SemanticJobManager.kt` retains bounded in-memory reasoning results and exposes facts, fingerprints, state, and stale detection.
- Semantic jobs are currently project-bound but do not retain the submitting user as a materialization owner.
- `web-server/StagingWorkflowService.kt` stages one request at a time and owns shared in-memory proposal state. It does not expose an atomic prepared-typed-edit batch append.
- `web-server/contract/StagingContracts.kt` exposes normalized staged entries but no typed reasoning provenance.
- `web-app/SemanticJobPanel.tsx` displays status and result counts only; it does not fetch or render fact details.
- `web-app/StagingPanel.tsx` displays staged triples and authors but no materialization-origin section.
- Existing project source contracts identify local resolved sources and ontology/data/shapes roles. Imported and external assets are not project write targets.

## Target State

- Neutral core contracts represent supported materialization identities, stageability, source candidates, and provenance.
- A semantic-engine service converts retained inferred facts into existing typed edits and deterministically analyzes terms, duplicates, target sources, and import safety.
- Semantic jobs retain submitting-user ownership and expose bounded server-computed materialization candidates.
- One Ktor POST route accepts opaque fact IDs/source choices and delegates a fresh reasoning check plus atomic staging.
- Shared staging can append a prepared multi-source typed-edit batch with one idempotency decision and no partial state.
- Every materialized staged entry carries typed provenance that proposal review displays clearly.
- The Reasoning UI separates asserted/inferred facts, supports bounded selection and source choice, stages the complete selection, and navigates to Changes.
- Existing proposal application remains the only path that writes asserted statements.

## Affected Modules And Files

Expected changes are limited to existing modules:

- `core-types`
  - `Phase4Contracts.kt` only for narrowly required inference identity/import metadata;
  - `Phase25PlusContracts.kt` for optional materialization provenance on staged entries;
  - preferably one focused `InferenceMaterializationContracts.kt` for neutral enums/data contracts;
  - focused contract tests.
- `semantic-engine`
  - new focused `InferenceMaterializationService.kt`;
  - minimal reuse/visibility adjustments in reasoning, import, and typed-edit helpers only when compilation requires them;
  - focused service tests and copied fixtures.
- `web-server`
  - `SemanticJobManager.kt` and `JobContracts.kt`;
  - `StagingWorkflowService.kt`;
  - a focused materialization orchestrator/service and versioned DTO file;
  - `Application.kt` route registration;
  - existing authorization, collaboration, idempotency, and job invalidation hooks without a new subsystem;
  - focused contract, route, staging, workflow, and application tests.
- `web-app`
  - semantic-job/materialization contracts and functions under `src/web/`;
  - a focused inferred-facts component under `src/workbench/`;
  - `SemanticJobPanel.tsx`, `StagingPanel.tsx`, and minimal `ProjectWorkspace.tsx` navigation wiring;
  - focused CSS and Vitest/Playwright tests.
- `docs`
  - one exact completion artifact per slice under `docs/decisions/`;
  - final Phase 10 summary and current-status updates only after every gate passes.

No new Gradle module, npm dependency, database, persistence layer, queue, server framework, authentication system, semantic rule engine, CLI command, or VS Code command is planned.

## Dependency Order

1. Slice 0: approved Phase 10 scope/spec/ExecPlan.
2. Slice 1: Slice 0.
3. Slice 2: Slice 1.
4. Slice 3: Slice 2.
5. Slice 4: Slice 3.
6. Slice 5: Slice 4.
7. Slice 6: Slice 5.
8. Slice 7: Slice 6.
9. Slice 8: Slice 7.

Every later slice starts from the locally merged result of every earlier slice.

## Implementation Slices

### Slice 0: Planning Approval And Contract Audit

#### Goal

Confirm the spec against current public contracts, pin exact additive contract shapes and canonical identity rules, and record approval before implementation changes begin.

#### Allowed Files And Modules

- `docs/specs/0018-phase-10-materialize-inferred-relationships.md`
- `docs/execplans/0018-phase-10-materialize-inferred-relationships.md`
- `docs/decisions/phase-10-slice-0-contract-audit.md`
- read-only inspection of current source and tests

#### Forbidden Actions And Modules

- No production, fixture, dependency, lockfile, or test-code changes.
- No edits to the user-authored Phase 10 scope without explicit instruction.
- No implementation while either planning document is unapproved.
- No broad redesign of reasoning, staging, jobs, proposals, identity, or authorization.

#### Expected Changes Or Output

- `docs/decisions/phase-10-slice-0-contract-audit.md`.
- Approval status recorded in the spec and ExecPlan.
- Exact canonical fact encoding version and digest decision.
- Exact neutral contract names and package ownership.
- Evidence that existing typed edits represent all three supported facts.
- Evidence that source ownership can be derived from current project/source/symbol contracts.
- Decision on the minimum import-dependence metadata needed from reasoning.
- Decision on bounded fact details: retain the existing 100 maximum and add pagination only if existing response-size tests prove it necessary.
- Exact atomic staging extension point and idempotency behavior.
- Exact submitting-user ownership change for semantic jobs without changing ordinary shared result visibility more than required.

#### Tests

- Documentation and contract traceability review.
- Verify every acceptance criterion maps to at least one planned slice/test.
- Verify every scope open question is resolved by the spec or explicitly deferred.

#### Verification Commands

```bash
git diff --check
git status --short
```

#### Stop Conditions

- Existing typed edits cannot represent one supported inference without raw RDF.
- Safe import dependence requires a new explanation engine or unverifiable guess.
- Current source contracts cannot distinguish writable local targets from imports/external assets.
- Atomic staging requires replacing the proposal workflow rather than a narrow additive API.
- Approval would expand inference types, persistence, imported-source writes, or browser semantic ownership.

### Slice 1: Core Materialization Contracts And Provenance

#### Goal

Define immutable, UI-independent materialization identity, stageability, source-candidate, batch, and provenance contracts.

#### Allowed Files And Modules

- `docs/decisions/phase-10-slice-1-core-contracts.md`
- `core-types/src/main/kotlin/com/entio/core/InferenceMaterializationContracts.kt`
- `core-types/src/main/kotlin/com/entio/core/Phase25PlusContracts.kt`
- `core-types/src/main/kotlin/com/entio/core/Phase4Contracts.kt` only if Slice 0 proves a narrow additive field is required
- focused tests under `core-types/src/test/kotlin/com/entio/core/`

#### Forbidden Actions And Modules

- No Ktor, JSON, React, filesystem path, Jena, OWL API, HermiT, clock implementation, or HTTP types.
- No new inferred fact kinds or general-purpose provenance framework.
- No raw triple mutation operation.
- No required constructor change that breaks ordinary existing staged entries.
- No product logic in `shared`.

#### Expected Changes Or Output

- `docs/decisions/phase-10-slice-1-core-contracts.md`.
- Explicit inference-type and stageability enums.
- Separate `factId` and server-only `semanticFactKey` contracts following the approved identity rules.
- Canonical materialization fact identity input and opaque browser identity value.
- Neutral source candidate/selection data.
- `InferenceMaterializationProvenance` with origin, job/fingerprint/fact, canonical terms, user, timestamp, target source, and bounded import references.
- Optional provenance on `StagedChange` with a default preserving all existing call sites.
- Batch input/result contracts suitable for semantic and server adapters without web concerns.
- Constructor invariants for nonblank IDs/fingerprints, named terms, maximum batch size, unique facts, stable import references, and matching target source.

#### Tests

- Construct all three supported identities and every stageability state.
- Reject blank/malformed required identity fields and oversized/duplicate batches.
- Prove labels do not participate in semantic identity.
- Prove ordinary staged changes remain valid without provenance.
- Prove provenance is immutable, deterministic, and presentation-neutral.

#### Verification Commands

```bash
./gradlew :core-types:test
./gradlew :core-types:check
git diff --check
```

#### Stop Conditions

- Contracts require server session or UI state.
- A core contract duplicates existing RDF terms instead of reusing them.
- Adding provenance breaks unrelated staged-change construction beyond mechanical default compatibility.
- Correct identity requires third-party types outside `semantic-engine`.

### Slice 2: Semantic Materialization Analysis And Typed-Edit Conversion

#### Goal

Implement deterministic Kotlin analysis that identifies supported inferred facts, computes canonical identities, checks semantic compatibility and applied duplicates, resolves writable source candidates, records safe import dependence, and converts facts to existing typed edits.

#### Allowed Files And Modules

- `docs/decisions/phase-10-slice-2-semantic-adapter.md`
- new `semantic-engine/src/main/kotlin/com/entio/semantic/InferenceMaterializationService.kt`
- focused tests under `semantic-engine/src/test/kotlin/com/entio/semantic/`
- minimal, reviewed visibility/additive changes to existing reasoning, import, source, symbol, and typed-edit helpers
- Slice 1 core contracts

#### Forbidden Actions And Modules

- No Ktor DTO, user session, shared staging, React, CLI command, VS Code command, source write, proposal apply, persistence, or raw RDF fallback.
- No new OWL rules or custom reasoner.
- No imported, bundled, FIBO, or external target sources.
- No ambiguous source choice inside the service.
- No label-based fact identity or semantic validation.

#### Expected Changes Or Output

- `docs/decisions/phase-10-slice-2-semantic-adapter.md`.
- Versioned canonical encoder for `semanticFactKey` and a separate job/user/project/fingerprint-bound `factId` encoder selected in Slice 0.
- Deterministic extraction of inferred-only supported facts from `ReasoningResult`.
- Exact conversion to `AddSuperclassEdit`, `AssignTypeEdit`, and `AddObjectPropertyAssertionEdit`.
- Named-resource and object-property compatibility checks.
- Applied-graph duplicate detection by canonical `GraphTriple`.
- Subject-first writable local source candidate resolution with stable source ordering.
- Safe cross-local-source references and no external/import target.
- Bounded import-dependence metadata or explicit `ImportDependencyUnsafe`.
- Stable candidate ordering and safe stageability reasons.
- Compatibility proof through the existing typed-edit translator/preview path without mutation.

#### Tests

- One fixture/test for each supported inference mapping.
- Asserted fact exclusion and applied duplicate detection.
- Blank-node/literal/unsupported-predicate rejection.
- Stable `semanticFactKey` across labels and repeated runs with the same canonical fact.
- `factId` changes for job, user, project, or fingerprint changes.
- `semanticFactKey` changes for inference-type or canonical-term changes.
- One, many, and no writable source candidates.
- Subject and object in different local sources.
- Imported subject/reference cases, safe local assertion, and unsafe import dependence.
- FIBO/external/read-only target protection.
- Existing typed-edit translator regressions.

#### Verification Commands

```bash
./gradlew :semantic-engine:test
./gradlew :semantic-engine:check
./gradlew :core-types:test :semantic-engine:test
git diff --check
```

#### Stop Conditions

- A required inference cannot be distinguished from asserted facts reliably.
- Object-property compatibility needs browser or route policy.
- Safe source resolution would silently choose among multiple valid sources.
- Import dependence can only be guessed or requires copying imported axioms.
- Any conversion needs a new graph mutation path.

### Slice 3: Atomic Shared-Staging Batch And Duplicate Checks

#### Goal

Extend shared staging narrowly so a fully prepared materialization batch is appended atomically with typed provenance, idempotency, staged/proposal duplicate detection, and no source mutation.

#### Allowed Files And Modules

- `docs/decisions/phase-10-slice-3-atomic-staging.md`
- `web-server/src/main/kotlin/com/entio/web/StagingWorkflowService.kt`
- a focused internal materialization/batch helper under `web-server/src/main/kotlin/com/entio/web/`
- `web-server/src/main/kotlin/com/entio/web/contract/StagingContracts.kt`
- focused `StagingWorkflowService` and proposal-planner tests
- Slice 1 and Slice 2 contracts/services

#### Forbidden Actions And Modules

- No route/UI changes yet.
- No direct graph-change staging when an approved typed edit exists.
- No partial append, best-effort filtering, silent duplicate removal, direct source write, apply, or automatic approval.
- No replacement of the existing project session or proposal planner.
- No provenance stored only as loose `normalizedValues`.

#### Expected Changes Or Output

- `docs/decisions/phase-10-slice-3-atomic-staging.md`.
- One synchronized service operation that prepares/checks all items before mutating `ProjectSession`.
- Duplicate checks against translated current staged entries and current proposal contents.
- Already staged or proposal-represented facts return existing staged identities and do not cause partial failure.
- Already asserted facts reject the complete request.
- Duplicate checks inside the request by `factId`, `semanticFactKey`, and canonical triple.
- One batch idempotency decision and deterministic staged-entry ordering.
- Provenance copied into every staged entry with server-supplied user/time/target source.
- Existing prepared proposal cleared once after successful append and unchanged after failure.
- Fact-ID-to-staged-ID mapping.
- Existing web staging DTO safely exposes optional typed materialization provenance.
- Existing ordinary `stage` behavior remains unchanged.

#### Tests

- One and multiple item atomic append.
- First/middle/last failure leaves entries, proposal, counters, and idempotency state correct.
- Mixed batch with new and already staged/proposed facts appends only the new facts and returns existing identities for the rest.
- All-existing batch returns success with no state change.
- Already asserted fact rejects the complete batch.
- Duplicate `factId`, duplicate `semanticFactKey`, and duplicate translated triple inside one request reject the batch.
- Multi-source batch retains deterministic order and target sources.
- Identical idempotent replay and conflicting replay.
- Server user/time cannot be supplied by a caller.
- Preview after staging produces normal typed edits and semantic diff.
- No ontology source changes during staging or preview.

#### Verification Commands

```bash
./gradlew :web-server:test
./gradlew :web-server:check
./gradlew :core-types:test :semantic-engine:test :web-server:test
git diff --check
```

#### Stop Conditions

- Atomic append cannot be implemented without exposing or replacing broad session internals.
- Existing idempotency semantics cannot cover a complete batch.
- Duplicate comparison requires trusting normalized display values.
- Adding provenance changes ordinary proposal semantics or ontology output.
- A failed batch changes shared staging or proposal state.

### Slice 4: Semantic Job Ownership, Freshness Orchestration, And Ktor Boundary

#### Goal

Expose bounded candidates and one authorized materialization route that rechecks the current graph and inference set, then delegates the verified batch to Slice 3.

#### Allowed Files And Modules

- `docs/decisions/phase-10-slice-4-web-boundary.md`
- `web-server/src/main/kotlin/com/entio/web/JobContracts.kt`
- `web-server/src/main/kotlin/com/entio/web/SemanticJobManager.kt`
- a focused `InferenceMaterializationWebService.kt`
- focused versioned DTOs under `web-server/src/main/kotlin/com/entio/web/contract/`
- `web-server/src/main/kotlin/com/entio/web/Application.kt`
- minimal existing authorization/collaboration hook usage
- focused contract, manager, route, authorization, and application tests

#### Forbidden Actions And Modules

- No client-authored triple/edit/provenance fields.
- No materialization from proposal, incomplete, stale, failed, cancelled, timed-out, or another user's job.
- No new identity/authorization framework, persistence, queue, job kind, WebSocket protocol, or direct apply route.
- No semantic conversion in Ktor route handlers.
- No unbounded details response or batch over 100.

#### Expected Changes Or Output

- `docs/decisions/phase-10-slice-4-web-boundary.md`.
- Semantic-job records retain submitting user ID internally.
- Submit/find/details/cancel behavior remains compatible; materialization ownership is enforced without exposing another user's private job data.
- Completed reasoning details expose server-computed fact IDs, labels, origins, stageability, source candidates, duplicate links, and import metadata.
- `POST /api/v1/projects/{projectId}/semantic-jobs/{jobId}/materializations`.
- Request contains fact IDs, per-fact source selections, and idempotency key only.
- One materialization request executes at a time per project.
- Bounded timeout/cancellation behavior reuses existing semantic-job conventions and preserves staging/proposal state on failure.
- Route enforces authenticated user, `STAGE_OWN_CHANGE`, project/job ownership, request bounds, and safe DTO validation.
- Orchestrator reloads the current applied project graph and import closure, excluding drafts, staged changes, proposal preview changes, and unapplied edits.
- It verifies graph fingerprint, resolves submitted `factId` values to retained `semanticFactKey` values, reruns reasoning, confirms every semantic key still exists, recomputes source/duplicate checks, then invokes atomic staging.
- Success invalidates proposal semantic jobs and publishes one normal shared-staging collaboration event.
- Safe structured error mapping from the spec with no paths, source content, stack traces, or cross-user leakage.

#### Tests

- Candidate contract for all supported/non-stageable states.
- Completed applied job success.
- Every ineligible job state/scope failure.
- Fingerprint change and fact-disappears-on-rerun failure.
- Fresh rerun matches by `semanticFactKey`, not by the original job-bound `factId`.
- Unknown/tampered fact ID and invalid source choice.
- Request bounds and malformed/duplicate selections.
- Cross-project and cross-user job/fact/source isolation.
- Contributor staging permission and reviewer compatibility.
- Imported/FIBO/read-only source protection through the HTTP boundary.
- Idempotent retry after response loss.
- Existing semantic-job status/details/cancel route regressions.
- No POST success can directly apply or mutate a source.

#### Verification Commands

```bash
./gradlew :web-server:test
./gradlew :web-server:check
./gradlew test
git diff --check
```

#### Stop Conditions

- Freshness correctness depends only on a client-supplied fingerprint.
- Rerun output cannot be matched canonically to retained selected facts.
- Job ownership breaks required existing shared collaboration behavior beyond the approved materialization boundary.
- Route implementation starts performing ontology semantics.
- Any failure produces partial staged state or sensitive output.

### Slice 5: Reasoning Fact Selection And Stage Action

#### Goal

Add accessible bounded inferred-fact review, selection, source choice, and staging to the React Reasoning workspace while keeping semantic decisions server-owned.

#### Allowed Files And Modules

- `docs/decisions/phase-10-slice-5-reasoning-ui.md`
- materialization/job contracts, API functions, and query hooks under `web-app/src/web/`
- new focused components/tests under `web-app/src/workbench/`
- `web-app/src/workbench/SemanticJobPanel.tsx`
- minimal `ProjectWorkspace.tsx` callback for navigating to Changes
- focused styles in `web-app/src/styles.css`

#### Forbidden Actions And Modules

- No RDF parsing, triple construction, typed-edit selection, duplicate logic, source inference, freshness decision, raw API bypass, direct apply, or ontology-map change.
- No new dependency, global state framework, browser persistence, unbounded select-all, or broad Reasoning/Changes redesign.
- No UI control for unsupported or proposal-scoped materialization.
- No client-generated author/timestamp/provenance.

#### Expected Changes Or Output

- `docs/decisions/phase-10-slice-5-reasoning-ui.md`.
- Typed job details/materialization requests aligned with server fixtures.
- Asserted and inferred sections with explicit origin.
- Rows for subject, relationship/type, object/target, inference type, target source, import dependence, and stageability.
- Disabled non-stageable rows with accessible reasons and existing-stage references.
- Single, multiple, select-all-visible, and clear selection.
- Stable source selector for ambiguous candidates.
- `Stage as asserted` enabled only when the visible client requirements are satisfied, while the server remains authoritative.
- While staging, the UI clearly shows that Entio is reloading the applied graph and rechecking reasoning.
- The selected target source is visible before submission.
- Selection invalidated on job/project/stale transitions and cleared on success.
- Safe error messages; correctable errors retain selection.
- Success updates staged-query state and opens/focuses Changes/Review Queue.
- Truncation and maximum-batch messaging.
- Keyboard, focus, live-region, and screen-reader labeling.

#### Tests

- Render asserted/inferred separation and all three supported types.
- Render every server stageability state without client reinterpretation.
- Single/multiple/select-all-visible/clear behavior.
- Select-all excludes disabled rows and never exceeds loaded results.
- Ambiguous source requirement and stable selection payload.
- Stale/result change clears selection.
- Successful request uses fact IDs/source choices only, refreshes staging, and navigates to Changes.
- Server error handling and retry.
- No materialization action calls proposal approve/apply/source-write endpoints.
- Existing SemanticJobPanel behavior and web build remain green.

#### Verification Commands

```bash
cd web-app
npm ci
npm audit --omit=dev
npm test
npm run build
```

Then from the repository root:

```bash
git diff --check
```

#### Stop Conditions

- The UI needs to derive a triple/edit type/source to submit.
- Current job details cannot remain bounded.
- Accessibility requires replacing the current workbench framework.
- Successful staging cannot reuse existing Changes navigation/query state.
- A browser action can write or apply directly.

### Slice 6: Proposal Provenance And Existing Workflow Integration

#### Goal

Show reasoning origin in the existing review experience and prove materialized edits retain normal preview, validation, multi-source apply, reload, and rollback behavior.

#### Allowed Files And Modules

- `docs/decisions/phase-10-slice-6-proposal-provenance.md`
- `web-app/src/workbench/StagingPanel.tsx`
- focused StagingPanel tests and styles
- focused `web-server` staging/proposal/apply integration tests
- copied temporary reasoning/project fixtures
- minimal DTO adjustments proven necessary by Slice 5 integration

#### Forbidden Actions And Modules

- No new proposal type, approval state, review queue, apply endpoint, ontology annotation, or graph-diff provenance entry.
- No weakening validation because a fact was entailed.
- No direct write from Reasoning or review presentation.
- No broad Changes UI redesign.
- No changes to CLI/VS Code product surfaces.

#### Expected Changes Or Output

- `docs/decisions/phase-10-slice-6-proposal-provenance.md`.
- Each materialized entry shows `Origin: Materialized from reasoning`.
- Review shows reasoning run, target source, entailed-before-assertion, and import-derived state.
- Explicit semantic diff remains the asserted triple addition.
- Provenance does not appear as an ontology annotation or separate graph change.
- Multi-source materialization batches enter one current proposal.
- Normal validation, reasoning impact, and SHACL impact remain visible/operative.
- Rejection leaves all sources unchanged.
- Acceptance/application writes through existing typed-edit/multi-source atomic paths.
- Reload shows the assertion.
- Post-save verification failure restores all original sources.
- Later reasoning treats the applied statement as asserted.

#### Tests

- Staging details for ordinary entries remain unchanged.
- Materialized subclass/type/object-assertion provenance presentation.
- Safe import-dependence presentation with no path leakage.
- Net diff contains only expected asserted additions.
- Preview and reject preserve sources.
- Single-source apply/reload.
- Multi-source apply/reload.
- Forced post-save failure rolls back all targets.
- Later reasoning asserted/inferred separation.
- Existing proposal validation, SHACL impact, approval, and remediation regressions.

#### Verification Commands

```bash
./gradlew :semantic-engine:test :validation-engine:test :graph-diff:test :web-server:test
cd web-app
npm test
npm run build
```

Then from the repository root:

```bash
git diff --check
```

#### Stop Conditions

- Provenance requires ontology mutation or a second diff model.
- Existing proposal/apply cannot carry the typed edits unchanged.
- Materialized assertions bypass or weaken a validation/approval gate.
- Multi-source apply loses existing atomic rollback guarantees.
- Applied facts remain mislabeled as inferred-only.

### Slice 7: End-To-End Materialization And Regression Gate

#### Goal

Verify the complete Phase 10 promise through real HTTP/browser workflows and prove isolation, atomicity, source safety, and compatibility.

#### Allowed Files And Modules

- `docs/decisions/phase-10-slice-7-end-to-end.md`
- focused copied fixtures under existing Kotlin test resources/helpers
- focused `web-server` application/integration tests
- `web-app/e2e/` materialization coverage and test-only route/fixture support
- existing regression tests requiring minimal fixture updates

#### Forbidden Actions And Modules

- No production-only test endpoint or fixture behavior.
- No committed example mutation.
- No skipped/flaky test, raised limit, partial-batch fallback, weakened permission, or inflated timeout merely to pass.
- No unrelated production feature or documentation completion claim.

#### Expected Changes Or Output

- `docs/decisions/phase-10-slice-7-end-to-end.md`.
- Copied deterministic ontology fixture producing one inference of each supported type.
- Browser journey: run reasoning, inspect separated facts, select facts, resolve ambiguous source when present, stage, review provenance/diff, reject without write, restage, accept/apply, reload, and observe asserted results.
- Server integration coverage for stale rerun, duplicates, imported source safety, multi-source batch, idempotency, cross-project/user isolation, and rollback.
- Evidence that no Reasoning UI action calls apply before review acceptance.
- Recorded elapsed time for 1-fact, 25-fact, and 100-fact materialization requests on the approved development fixture, without live network dependencies.
- Existing reasoning, proposal, validation, SHACL, collaboration, map, CLI, VS Code, and web workflows remain green.

#### Tests

- Every spec test case and acceptance criterion not already proven at a lower layer.
- At least one negative browser state for stale or non-stageable facts.
- Browser keyboard selection and source-choice coverage.
- Request inspection proving fact IDs—not triples—cross the browser boundary.
- File-content comparison before staging, after rejection, after apply, and after forced rollback.
- Per-project concurrent materialization rejection or serialization.
- Timeout, cancellation, and reasoning failure leave shared staging and proposal state unchanged.

#### Verification Commands

```bash
./gradlew :core-types:test :semantic-engine:test :validation-engine:test :graph-diff:test :web-server:test
./gradlew test
./gradlew build
./gradlew check
```

```bash
cd web-app
npm ci
npm audit --omit=dev
npm test
npm run build
npm run test:e2e
```

```bash
cd vscode-extension
npm ci
npm test
```

```bash
git diff --check
git status --short
```

#### Stop Conditions

- Any acceptance criterion or existing regression fails.
- Browser or HTTP tests can submit a client-authored triple/edit/provenance author.
- Staging or rejection modifies a source.
- Any batch is partially staged/applied.
- Cross-project/user or external/read-only protection fails.
- Full verification would require expanding the approved scope.

### Slice 8: Final Documentation And Phase Completion

#### Goal

Record verified Phase 10 delivery and update current repository status without rewriting historical records.

#### Allowed Files And Modules

- `docs/decisions/phase-10-slice-8-phase-completion.md`
- `docs/phase-summaries/phase-10-summary.md`
- `README.md`
- `AGENTS.md`
- Phase 10 spec and ExecPlan status/link corrections
- documentation index/link corrections directly required by Phase 10

#### Forbidden Actions And Modules

- No production or test-code changes mixed into this slice.
- No completion claim before every prior slice and full verification passes.
- No rewrite of historical Phase 1-9 records.
- No modification of `docs/architecture/phase-10-scope.md` without explicit instruction.
- No claim of automatic materialization, durable persistence, production identity, CLI/VS Code support, or map inference display.

#### Expected Changes Or Output

- `docs/decisions/phase-10-slice-8-phase-completion.md`.
- `docs/phase-summaries/phase-10-summary.md` with delivered behavior, exact supported types, batch/result limits, identity/source/provenance rules, files/modules, fixtures, verification evidence, limitations, and rollback notes.
- README and AGENTS current-state updates only for verified behavior.
- Spec marked implemented and ExecPlan marked complete only when Definition of Done is satisfied.
- Clear retained limitations: in-memory results/provenance, web-only materialization UI, no automatic materialization, and asserted-only ontology map.

#### Tests

- Documentation link and diff review.
- Verify claims against retained Slice 7 command output and completion artifacts.
- Re-run only documentation checks unless final edits affect executable tooling.

#### Verification Commands

```bash
git diff --check
git diff --stat
git status --short
```

#### Stop Conditions

- Any prior completion artifact or verification evidence is missing.
- Documentation would describe unimplemented behavior.
- Unrelated user changes would be overwritten.

## Test Plan

Testing proceeds from semantic identity outward:

1. Core contract tests prove immutable fact identity, stageability, batch bounds, and optional provenance compatibility.
2. Semantic-engine tests prove inferred-only extraction, canonical identity, existing typed-edit conversion, applied duplicate checks, source candidates, and import safety.
3. Staging-service tests prove staged/proposal duplicate checks, all-or-nothing append, deterministic order, provenance, and idempotency.
4. Semantic-job/Ktor tests prove job ownership, current-fingerprint rerun, fact membership, permissions, safe errors, bounded requests, and collaboration hooks.
5. React tests prove separated results, selection, source choice, stale behavior, safe payloads, and Changes handoff without semantic decisions.
6. Proposal/apply tests prove provenance presentation and unchanged validation, diff, reasoning/SHACL impact, apply, reload, and rollback.
7. Playwright and application tests prove the complete human-controlled journey and negative source/isolation behavior.
8. Full Gradle, web-app, and VS Code suites prove compatibility.

Tests must use temporary copies of fixtures for any operation that may apply changes. Bundled FIBO and committed example files must remain byte-for-byte unchanged.

## Full Verification Commands

After all implementation slices are locally merged:

```bash
./gradlew test
./gradlew build
./gradlew check
```

```bash
cd web-app
npm ci
npm audit --omit=dev
npm test
npm run build
npm run test:e2e
```

```bash
cd vscode-extension
npm ci
npm test
```

From the repository root:

```bash
git diff --check
git status --short
```

## Rollback Notes

- Phase 10 adds no storage migration and writes no source outside the existing proposal application path.
- A safe UI rollback first removes/hides inferred-fact selection and the materialization POST call while leaving ordinary Reasoning and Changes views intact.
- Remove the materialization route/web adapter next; existing semantic-job status/details routes continue to work.
- Remove the atomic prepared-batch staging API only after the route consumer is gone; ordinary single-change staging remains.
- Remove semantic materialization analysis and core contracts after all adapters are removed.
- Optional staged provenance fields can remain harmlessly nullable during a staged rollback, but final rollback should remove them after consumers and retained test fixtures are updated.
- Any already applied materialized fact is an ordinary asserted ontology statement. Code rollback does not silently delete it; users must remove it through the normal reviewed edit workflow.
- Any staged but unapplied materialization remains removable through the existing review queue. If incompatible with rollback code, discard it before deployment rollback or restart the current in-memory development session.

## Risks And Assumptions

- Current semantic-job IDs are random retained identifiers; canonical fact IDs therefore need a server-owned versioned digest and cannot be reconstructed by React.
- `ReasoningResult.sourceId` may be insufficient alone to prove import dependence. Slice 0 must select a narrow additive metadata approach or mark uncertain facts unsafe.
- Current semantic jobs are project-bound, not owner-bound. Adding submitting-user ownership must preserve ordinary job status/details behavior while enforcing materialization isolation.
- Current staging is synchronized but stages one entry at a time. Calling it repeatedly would violate atomicity; Slice 3 requires one prepared-batch mutation.
- Existing `normalizedValues` support display and compatibility helpers but are not a sufficient typed provenance store.
- Source roles identify local ontology edit targets, but an IRI may be declared in multiple sources. Ambiguity must remain explicit.
- A fresh reasoning rerun may be the most expensive part of staging. Correctness takes priority; no cache may bypass fingerprint and fact-membership checks.
- The development identity/staging/job stores are in memory. Phase 10 preserves that limitation and does not claim durable audit history.
- Existing proposal reasoning/SHACL impact may be asynchronous or bounded; Phase 10 must reuse it and not invent a special approval shortcut for already-entailed facts.
- The ontology map remains asserted-only. Applying a materialized fact may make it visible through its normal asserted graph on refresh, but Phase 10 does not add inferred visualization.

## Boundary Check

- Phase fit: the user supplied a dedicated Phase 10 scope for materializing selected existing inferences.
- Non-goals: no automatic materialization, new inference rules, raw mutation, imported-source writes, AI selection, persistence, CLI/VS Code materialization, or map inference display.
- Speculative infrastructure: no new module, database, queue, framework, authentication system, frontend state library, or durable store.
- Module responsibility: core holds neutral contracts; semantic-engine owns ontology meaning and typed conversion; web-server owns job/staging orchestration and safe adaptation; React owns temporary interaction; `shared` remains unchanged.
- Dependency direction: engine modules never depend on Ktor/React, and third-party OWL/Jena types remain in `semantic-engine`.
- Standards: existing OWL API/HermiT, RDF terms, typed edits, Jena-backed graph handling, validation, and proposal services are reused. No RDF/OWL/SHACL implementation is reinvented.
- Approval: this ExecPlan is not authorization to implement until the scope, spec, and plan are explicitly approved.

## Definition Of Done

Phase 10 is complete only when:

- scope, spec, and ExecPlan are approved;
- every slice is implemented in order with its exact completion artifact;
- all three supported inferred fact types convert through existing typed edits;
- browser `factId` values are opaque and job/user/project/fingerprint-bound, while server-only `semanticFactKey` values are canonical, deterministic, and label-independent;
- fresh reasoning confirms retained selections by `semanticFactKey`, not by reproducing the original `factId`;
- candidate details and batches remain bounded to 100;
- the current graph is reloaded and every selected inference is reconfirmed before staging;
- batch staging is all-or-nothing for errors, idempotent for already staged/proposal-represented facts, and rejects already asserted facts;
- asserted, staged, proposal, and within-batch duplicates create no new entry;
- source resolution follows the approved subject definitions, is deterministic, explicit when ambiguous, and never targets imported/external/read-only sources;
- safe import-derived provenance is visible and unsafe cases remain read-only;
- every staged materialization has typed server-authored provenance;
- proposal review shows origin and the explicit assertion without adding provenance to ontology data;
- no source changes before human acceptance/application;
- normal validation, reasoning/SHACL impact, approval, apply, reload, and rollback pass;
- cross-project and cross-user isolation pass;
- React submits fact IDs/source choices only and owns no semantic decision;
- existing reasoning, staging, proposal, validation, SHACL, collaboration, CLI, VS Code, ontology-map, and provider-setting regressions pass;
- one materialization request executes at a time per project, bounded timeout/cancellation leaves state unchanged, and 1/25/100-fact timing evidence is recorded;
- full Gradle, web-app, Playwright, VS Code, diff, and clean-status verification passes;
- Phase 10 documentation accurately records delivered behavior and limitations.

## Slice 0 Contract Resolutions

- `semanticFactKey` uses `entio-semantic-fact-v1:` plus lowercase hexadecimal SHA-256
  over length-prefixed inference kind and canonical RDF terms.
- `factId` uses `entio-reasoning-fact-v1:` plus lowercase hexadecimal SHA-256 over
  length-prefixed project ID, submitting-user ID, job ID, graph fingerprint, and
  `semanticFactKey`.
- Neutral core contracts are `InferenceMaterializationKind`,
  `InferenceStageability`, `InferenceImportDependence`,
  `InferenceMaterializationCandidate`, `InferenceMaterializationProvenance`, and
  focused prepared/result batch contracts.
- Import dependence is derived conservatively from retained per-fact source identity,
  loaded local/imported declarations, and the import-closure report. Unproven
  dependence is `Unknown` and not stageable.
- The existing bounded details maximum remains 100 and Phase 10 adds no pagination.
- The atomic staging extension is
  `stageMaterializations(projectId, userId, idempotencyKey, preparedItems)`.
- Semantic-job records retain an internal immutable `submittedByUserId`; ordinary
  project-scoped reads remain compatible, while materialization requires an exact
  current-user match.

Later slices may resolve exact filenames and private helper names through compilation evidence. These decisions may not introduce a new inference type, raw RDF fallback, partial batch, automatic materialization, imported-source mutation, browser semantic policy, persistence, or approval bypass. If one becomes necessary, stop and amend the approved spec and ExecPlan before continuing.
