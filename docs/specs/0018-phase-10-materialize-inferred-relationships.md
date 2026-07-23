# Feature Spec: Phase 10 Materialize Inferred Relationships

## Status

Approved for implementation on 2026-07-23.

## Problem

Entio's bounded OWL reasoning service already separates asserted facts from inferred facts and returns inferred class relationships, individual types, and object-property relationships. Those results are read-only. A user who decides that one of those logical consequences should be explicit in the local ontology must currently recreate the same statement manually in another editor.

Phase 10 adds a controlled bridge from a completed reasoning result to Entio's existing typed-edit and proposal workflow:

```text
completed applied-graph reasoning result
→ user selects supported inferred facts
→ Entio validates identity, freshness, duplicates, and target source
→ Entio converts the complete selection into existing typed edits
→ shared staging, preview, validation, review, approval, apply, reload, and rollback
```

The bridge materializes selected facts as assertions. It does not change how Entio reasons, and it never writes an inferred fact automatically.

## Goals

- Show asserted and inferred reasoning facts in separate, clearly labeled groups.
- Describe every inferred fact with a stable, server-issued identity rather than display text.
- Support materialization of inferred subclass relationships, individual types, and object-property assertions.
- Show whether each inferred fact is stageable and, when it is not, a deterministic reason.
- Let a user select one, several, or all currently visible stageable inferred facts.
- Stage one selection as an all-or-nothing batch.
- Convert supported facts into the existing `AddSuperclassEdit`, `AssignTypeEdit`, and `AddObjectPropertyAssertionEdit` paths.
- Reject stale, missing, unsupported, duplicate, cross-project, cross-user, imported-target, read-only-target, and ambiguous-source requests safely.
- Resolve an unambiguous writable local target automatically and require an explicit source choice when several valid targets exist.
- Preserve typed workflow provenance for every materialized assertion without adding ontology annotations.
- Reuse the current shared staging, proposal, validation, reasoning-impact, SHACL-impact, approval, apply, reload, and rollback behavior.
- Keep Kotlin authoritative for inference identity, source resolution, semantic validation, duplicate detection, conversion, and batch atomicity.
- Keep React responsible only for display, selection, source-choice interaction, messages, and navigation to proposal review.

## Non-Goals

- Automatic or background materialization.
- A `materialize all project inferences` operation.
- AI selection, recommendation, or staging of inferred facts.
- New inference rules, a new reasoner, or changes to HermiT/OWL API behavior.
- Materializing inferred facts from incomplete, failed, cancelled, timed-out, stale, or proposal-scoped reasoning runs.
- Materializing equivalence axioms, datatype values, annotation statements, same-as statements, negative assertions, anonymous expressions, literals, SHACL results, or any inference type not listed in this spec.
- Raw RDF, Turtle, or SPARQL mutation.
- Direct source writes or a second approval path.
- Writing provenance into the ontology automatically.
- Modifying imported, bundled, FIBO, or other external/read-only ontology sources.
- Durable reasoning-result, staged-session, proposal, or provenance persistence.
- New CLI or VS Code materialization commands in Phase 10.
- Displaying inferred relationships in the Phase 9 ontology map.
- New modules, frameworks, databases, queues, or frontend state libraries.

Current repository boundaries and phase-level non-goals remain governed by `AGENTS.md`.

## Proposed Behavior

### 1. Eligible Reasoning Results

Only a completed, complete, applied-graph reasoning job may supply materialization candidates.

The job must:

- belong to the current project;
- have been submitted by the current authenticated development user;
- have `Reasoning` kind and `Applied` scope;
- have `Completed` status;
- contain a retained `ReasoningResult`;
- have a graph fingerprint equal to the current applied project graph fingerprint;
- have complete import closure according to the retained reasoning metadata.

Proposal-scoped results are not materializable. Materializing from a proposal preview into that same proposal would create a circular baseline and approval dependency.

The existing reasoning result remains read-only. Phase 10 adds a separate server-owned materialization service that consumes retained results and delegates accepted edits to shared staging.

### 2. Supported Inference Types

Only facts with `FactOrigin.Inferred` and one of these exact mappings are supported:

| Inferred fact | Typed edit | Asserted statement |
| --- | --- | --- |
| Class relationship: subject class to superclass | `AddSuperclassEdit` | `subject rdfs:subClassOf superclass` |
| Individual type | `AssignTypeEdit` | `individual rdf:type class` |
| Object-property relationship | `AddObjectPropertyAssertionEdit` | `subject predicate object` |

All terms must be named IRI resources. Blank nodes and literal objects are not stageable. For object-property materialization, the predicate must resolve to an existing object property in the current project/import closure; arbitrary predicates are unsupported.

Phase 10 does not reinterpret display labels or reconstruct triples in React.

### 3. Stable Inference Identity

Every returned reasoning fact includes an opaque `factId` generated by Kotlin from canonical data:

- project ID;
- reasoning job ID;
- reasoning graph fingerprint;
- inference type;
- subject IRI;
- predicate IRI;
- object/target IRI.

The server uses the following pinned identities:

- `semanticFactKey`: prefix `entio-semantic-fact-v1:` followed by the lowercase hexadecimal SHA-256 digest of a UTF-8, length-prefixed sequence containing inference kind, canonical subject term, canonical predicate IRI, and canonical object term;
- `factId`: prefix `entio-reasoning-fact-v1:` followed by the lowercase hexadecimal SHA-256 digest of a UTF-8, length-prefixed sequence containing project ID, submitting-user ID, reasoning-job ID, graph fingerprint, and `semanticFactKey`.

Each length-prefixed component is encoded as `<UTF-8 byte length>:<component bytes>`. Named RDF resources use `iri:<absolute IRI>` as their canonical term. Ordering, labels, timestamps, and source display names do not affect either identity.

`factId` identifies a fact only inside its retained reasoning job. A staging request supplies the job ID and selected fact IDs; it never supplies a client-authored triple. The server looks up the original retained facts again before conversion.

### 4. Reasoning View

The Reasoning workspace shows a completed result in two sections:

- `Asserted facts`;
- `Inferred facts`.

Each inferred row shows:

- subject label with IRI available in technical text;
- relationship/type label;
- object or target label with IRI available in technical text;
- inference type;
- reasoning job ID;
- graph fingerprint abbreviation;
- asserted state;
- stageability state;
- selected target source when resolved;
- import-dependence indicator when applicable;
- deterministic non-stageable reason.

Supported inferred rows use checkboxes. Non-stageable rows remain visible but disabled. The user may:

- select or clear individual stageable rows;
- select all visible stageable rows;
- clear the full selection;
- choose a target source for an ambiguous row;
- activate `Stage as asserted`;
- move to the existing Changes/Review Queue after a successful batch.

`Select all visible` applies only to facts in the currently rendered bounded result, never hidden or truncated server facts.

Selection is temporary React state. It is cleared when:

- another reasoning job becomes active;
- the job becomes stale or non-completed;
- the project changes;
- staging succeeds;
- the page reloads.

### 5. Bounded Results And Batch Size

The existing semantic-job details boundary remains bounded. Phase 10 may raise no existing limit above 100.

- One candidate-details response returns at most 100 reasoning facts.
- One materialization request contains at most 100 distinct fact IDs.
- `Select all visible` selects at most those 100 returned facts.
- A truncated result clearly states that only the loaded facts are selectable.

Adding pagination is allowed only by reusing the current bounded semantic-job detail contract with deterministic offsets or opaque continuation. An unbounded facts response is forbidden.

### 6. Stageability Analysis

Kotlin determines stageability for each inferred fact against the current applied project and shared staged state.

A fact is stageable only when:

- its reasoning job satisfies the eligibility rules;
- its fact kind has an approved typed-edit mapping;
- every required term is a named IRI;
- referenced entities and the object-property predicate are known and compatible;
- the fact still appears as inferred in a fresh reasoning check over the same current fingerprint;
- the identical triple is not already asserted in the current applied graph;
- an equivalent addition is not already present in shared staging or the current proposal;
- at least one valid writable local target source exists;
- the selected target source, when required, is one of the server-returned candidates;
- the typed edit translates without raw RDF fallback.

The server returns stageability using explicit states rather than free-form booleans. Expected states include:

- `Stageable`;
- `AlreadyAsserted`;
- `AlreadyStaged`;
- `Stale`;
- `UnsupportedType`;
- `UnsupportedTerm`;
- `MissingEntity`;
- `InvalidPredicate`;
- `NoWritableSource`;
- `AmbiguousSource`;
- `ImportDependencyUnsafe`.

Each non-stageable state has a safe user-facing message. `AlreadyStaged` includes the existing staged entry ID so the UI can link to or identify the current review item.

### 7. Freshness And Reconfirmation

The job fingerprint is a precondition, not sufficient proof by itself.

On `Stage as asserted`, the server:

1. reloads the current applied project;
2. compares its graph fingerprint with the retained job fingerprint;
3. reruns reasoning against that current graph using the existing reasoning service;
4. requires a completed result with matching reasoning fingerprints;
5. confirms that every selected canonical fact is still inferred;
6. recomputes duplicates and source candidates;
7. converts and stages the complete batch.

If the project or relevant reasoning result changes during the operation, the whole request fails as stale. No selected edit is staged.

The fresh result may replace or refresh the retained job details only if that behavior is already safe for the current job manager; correctness does not depend on replacing it.

### 8. Duplicate Prevention

Duplicate identity is the canonical asserted `GraphTriple`, not the reasoning label or fact ID.

Before staging, Entio checks:

- the current applied graph;
- every current staged operation after typed-edit translation;
- the current prepared proposal contents;
- duplicate fact IDs and duplicate canonical triples inside the request.

Behavior is:

- already asserted: informational and not stageable;
- already staged/proposed: not stageable and references the existing staged item;
- repeated selection in one request: request validation failure;
- two different inferred facts that translate to the same triple: request validation failure.

Phase 10 does not silently discard duplicates from a submitted batch because doing so would violate all-or-nothing review expectations.

After an approved materialization is applied, later reasoning views show the statement as asserted. Entio does not duplicate the same statement in inferred output merely to preserve its history. Workflow provenance remains attached to the staged/proposal record for the life of the current in-memory session.

### 9. Source Resolution

A writable local source is a resolved project source with an ontology role that is owned by the registered local project. Imported closure documents, bundled assets, FIBO sources, and external catalog sources are never writable candidates.

For every supported fact, Kotlin computes candidates from writable local sources that declare the subject. A source declares a subject when its loaded asserted graph contains a declaration triple for that named resource. Existing `EntioProject.ontologies`, `ResolvedOntologySource`, source roles, and loaded symbols provide this evidence without a new ownership registry.

Resolution rules:

- one valid candidate: select it automatically;
- more than one valid candidate: return all candidates in stable source-ID order and require explicit user selection;
- no valid candidate: mark the fact not stageable.

There is no fallback to an arbitrary local source when no writable local source declares the subject.

The object/target's source does not override subject ownership. If subject and object are declared in different local sources, the assertion is written to the resolved subject source and references the object's IRI.

For a subclass inference, the subject is the subclass. For an individual type or object-property assertion, the subject is the individual/resource on the left side of the triple.

A batch may target several local sources. It still enters one existing multi-source proposal and must retain existing atomic application and rollback guarantees.

The chosen source is shown before staging and is recomputed and revalidated by the server on submission.

### 10. Import-Derived Inferences

An import-derived fact may be materialized locally only when:

- the current import closure is complete;
- the fact's subject can be asserted in a valid writable local target under the source rules;
- all referenced IRIs are valid named resources;
- the typed edit does not need to create, copy, or modify an imported entity;
- no imported, bundled, FIBO, or external source is a target;
- the server can identify that imported knowledge participated.

Stageability and proposal review show `Depends on imported knowledge`. The materialization analysis records an `InferenceImportDependence` value with a state of `LocalOnly`, `Imported`, or `Unknown` and a bounded, sorted list of contributing source IDs. Existing per-fact `sourceId`, project source declarations, and the import-closure report are sufficient when they deterministically prove local-only or imported-reference participation. They are not treated as a minimal explanation. If those inputs cannot prove the state, it is `Unknown`.

If import dependence cannot be represented safely from the retained reasoning result and existing explanation/import metadata, the fact remains read-only with `ImportDependencyUnsafe`.

### 11. Atomic Batch Staging

Batch behavior is all-or-nothing.

The materialization service prepares every selected item before changing shared staging:

- validates job and current fingerprint;
- resolves every fact;
- verifies duplicates;
- resolves or validates source choices;
- creates the existing typed edit;
- runs the existing translator/preview compatibility checks;
- constructs typed provenance.

Only after all items pass does the service append all entries to the current project session in deterministic request order. If any item fails, shared staging and the current proposal remain unchanged.

The batch request uses one idempotency key. The exact server extension is
`stageMaterializations(projectId, userId, idempotencyKey, preparedItems)`, where every
`PreparedInferenceMaterialization` already contains its typed edit, selected source,
canonical triple, semantic key, and provenance. The synchronized method validates the
entire prepared list and idempotency decision before changing `ProjectSession`, then
appends new entries in deterministic request order and clears the prepared proposal once.
Replaying the identical completed request returns the original fact-to-staged mapping.
Reusing its key with different fact IDs or source choices fails.

A successful batch invalidates proposal-scoped semantic jobs, publishes the normal shared-staging collaboration update, and returns the existing staging response plus a fact-ID-to-staged-ID mapping.

### 12. Provenance

Materialization provenance is typed workflow metadata, not an ontology statement and not an unstructured UI-only map.

Each materialized staged entry records:

- origin: `MaterializedFromReasoning`;
- inference type;
- reasoning job/result ID;
- graph fingerprint;
- canonical fact ID;
- subject, predicate, and object/target IRIs;
- staging user ID;
- staging timestamp;
- target source ID;
- whether imported knowledge contributed;
- bounded, sorted import references when known.

The core staged-change contract should gain a narrowly scoped optional `InferenceMaterializationProvenance` value rather than encoding required provenance only in `normalizedValues`. Existing staged entries remain source-compatible through a nullable/default value.

The web staging DTO exposes a safe adapted provenance object. It does not expose filesystem paths, raw source content, reasoner internals, or credentials.

Proposal review displays:

```text
Origin: Materialized from reasoning
Reasoning run: <bounded job identifier>
Target source: <source ID>
Entailed before assertion: Yes
Import-derived: Yes/No
```

The normal semantic diff remains the asserted triple addition. Provenance does not masquerade as a graph diff or ontology annotation.

### 13. Validation, Review, Apply, And Rollback

Materialized edits follow the current workflow unchanged:

```text
existing typed edit
→ staged entry
→ combined preview
→ deterministic validation
→ semantic diff
→ reasoning impact
→ SHACL impact where applicable
→ shared proposal
→ human acceptance or rejection
→ atomic apply
→ reload verification
→ rollback on failure
```

The review UI shows both the explicit assertion being added and its reasoning provenance. Being already logically entailed does not bypass validation or human review.

No ontology source changes before normal proposal acceptance/application. Rejection removes the proposal without writing the materialized facts.

### 14. Authorization And Isolation

Existing project registration, development identity, and authorization remain authoritative.

- Viewing reasoning facts requires the existing browse/user boundary.
- Staging materialized facts requires `STAGE_OWN_CHANGE`.
- The materialization route derives the user from the authenticated session and never accepts an author ID from React.
- A retained reasoning job is materializable only by the user who submitted it.
- Project ID, job ID, fact ID, source choices, idempotency state, and staged entries are checked together.
- A user cannot materialize from another project or another user's retained job.
- Successfully staged entries enter the existing project-shared review queue and are visible to collaborators according to current behavior.

This is development-user isolation within the current in-memory boundary, not a claim of production tenancy or durable authentication.

### 15. Server And Frontend Boundary

Kotlin owns:

- canonical fact identity;
- result eligibility and freshness;
- supported-type and term validation;
- entity and predicate compatibility;
- asserted/staged/proposal duplicate checks;
- source candidates and selected-source validation;
- import-dependence safety;
- conversion to existing typed edits;
- batch preparation and atomic append;
- provenance;
- idempotency and safe errors.

Ktor:

- adapts versioned request/response DTOs;
- enforces identity, permission, project, job, and request limits;
- delegates to Kotlin-owned services;
- triggers existing collaboration and semantic-job invalidation hooks.

React owns:

- bounded result presentation;
- checkbox/source-choice state;
- disabled/button state derived from server stageability;
- clear stale/error/success messages;
- navigation to the existing proposal review.

React must not construct triples, choose semantic edit types, infer source ownership, determine duplicates, or decide whether a fact remains inferred.

### 16. CLI And VS Code

No new materialization command or VS Code control is added in Phase 10.

Existing CLI and VS Code reasoning, proposal, validation, apply, and rollback behavior must continue to pass regression tests. The semantic materialization service and core contracts should remain UI-independent so a later approved phase can add another adapter without redesigning the engine.

## Inputs

### Candidate Details

Existing semantic-job detail input:

- project ID;
- reasoning job ID;
- bounded detail limit or continuation;
- current authenticated user.

Phase 10 fact output adds stable identity, labels, stageability, source candidates, and import-dependence metadata.

### Materialization Request

`POST /api/v1/projects/{projectId}/semantic-jobs/{jobId}/materializations`

The request contains:

- ordered distinct `factIds`, maximum 100;
- zero or one selected target source ID per ambiguous fact;
- one idempotency key.

The request does not contain a triple, edit type, provenance author, timestamp, or asserted/inferred claim.

## Outputs

### Materialization Candidate

Each candidate contains:

- `factId`;
- inference type;
- subject, predicate, and object/target identities and labels;
- origin;
- reasoning job ID;
- graph fingerprint;
- asserted state;
- stageability state and safe reason;
- existing staged entry ID when applicable;
- stable target-source candidates;
- selected source when unambiguous;
- import-derived flag and bounded safe references.

### Materialization Result

A successful response contains:

- API version;
- project ID;
- reasoning job ID;
- graph fingerprint;
- fact-ID-to-staged-entry-ID mappings;
- the current existing `WebStagingResponse`.

Failures use the existing safe web error envelope and do not return partially staged IDs.

## Validation Behavior

Validation is deterministic and ordered:

1. authenticate and authorize;
2. validate project and job ownership;
3. validate request size, uniqueness, and idempotency;
4. require a completed applied reasoning result;
5. reload and fingerprint the current applied graph;
6. rerun reasoning and confirm completion/fingerprint/fact membership;
7. resolve retained fact IDs to canonical facts;
8. validate supported kind and named terms;
9. check asserted, staged, proposal, and within-batch duplicates;
10. compute and validate writable local source candidates;
11. validate import-derived safety;
12. convert each fact to its existing typed edit;
13. translate/preview every edit without source mutation;
14. atomically append the complete batch with provenance;
15. clear the prepared proposal and invalidate proposal semantic jobs;
16. publish the normal collaboration update.

Ordering of returned candidates and batch mappings follows canonical inference-type, subject IRI, predicate IRI, object IRI, then fact ID ordering unless the request order is explicitly retained for staged-entry order. The same eligible job, current graph, staged state, user, and source choices produce the same validation outcome.

## Error Behavior

Expected safe error codes include:

- `unknown-semantic-job`;
- `reasoning-job-forbidden`;
- `reasoning-job-not-complete`;
- `reasoning-scope-not-materializable`;
- `stale-reasoning-result`;
- `invalid-materialization-batch`;
- `materialization-batch-too-large`;
- `unknown-inferred-fact`;
- `unsupported-inference-type`;
- `unsupported-inference-term`;
- `inferred-fact-no-longer-present`;
- `inferred-fact-already-asserted`;
- `inferred-fact-already-staged`;
- `duplicate-materialization-selection`;
- `no-writable-materialization-source`;
- `ambiguous-materialization-source`;
- `invalid-materialization-source`;
- `import-derived-materialization-unsafe`;
- `materialization-edit-incompatible`;
- `materialization-idempotency-conflict`;
- `materialization-batch-failed`.

Errors identify the affected `factId` where safe, but never expose paths, source contents, stack traces, raw library failures, or other users' retained job data. Any error leaves shared staging, proposals, ontology files, and reasoning results unchanged.

## Test Cases

### Core Contracts And Identity

- Construct each supported inference identity and provenance type.
- Canonical identity is stable across labels and ordering.
- Identity changes with job, graph fingerprint, inference type, or any RDF term.
- Staged changes without materialization provenance remain valid.
- Provenance ordering and safe serialization are deterministic.

### Materialization Conversion

- Inferred subclass relationship becomes `AddSuperclassEdit`.
- Inferred individual type becomes `AssignTypeEdit`.
- Inferred property relationship becomes `AddObjectPropertyAssertionEdit`.
- Asserted facts are never accepted as inferred candidates.
- Unsupported kinds, blank nodes, literal objects, and non-object-property predicates are rejected.
- Conversion delegates to the existing typed-edit translator.

### Freshness And Duplicates

- Matching completed applied reasoning result succeeds.
- Failed, cancelled, timed-out, incomplete, stale, and proposal-scoped results fail.
- Project fingerprint change blocks staging.
- Fact absent from the fresh rerun blocks staging.
- Already asserted fact is informational and rejected for staging.
- Already staged/proposed fact links to its existing staged entry.
- Duplicate fact ID and duplicate translated triple inside one request fail.
- Display-label changes do not identify a different fact.

### Source Resolution And Imports

- Subject owned by one writable local source selects it automatically.
- Subject owned by several writable sources requires explicit selection.
- Invalid selected source is rejected.
- Subject and object in different local sources target the subject source.
- Exactly one valid local source is selected when the subject has no local declaration.
- No writable source blocks staging.
- Imported, bundled, FIBO, and external sources never become targets.
- Safe import-derived inference stages into a local source and records import dependence.
- Unsafe or unrepresentable import dependence remains read-only.
- Multi-source batch produces one existing atomic proposal.

### Batch And Provenance

- One fact stages successfully.
- Multiple supported facts stage in deterministic order.
- Batch is all-or-nothing when the first, middle, or last item fails.
- Batch size over 100 fails.
- Identical idempotent replay returns the original result.
- Conflicting idempotency replay fails.
- Staging user and timestamp come from the server.
- Proposal review displays `Origin: Materialized from reasoning`.
- Proposal shows target source, entailed-before-assertion state, and import dependence.
- Provenance does not add an ontology annotation or graph-diff entry.

### Workflow And Isolation

- No source file changes before approval.
- Preview includes the explicit triple addition.
- Existing validation, reasoning impact, and SHACL impact run.
- Rejection changes no source.
- Approval and apply write through the existing typed-edit path.
- Reload shows the fact asserted.
- Post-save failure restores every affected source.
- Later reasoning reports the materialized statement as asserted.
- Cross-project job/fact/source IDs fail safely.
- Another user cannot materialize the submitting user's retained job.
- The resulting shared staged entries remain visible to authorized collaborators.
- Existing reasoning, staging, proposal, validation, SHACL, collaboration, CLI, VS Code, and ontology-map regressions remain green.

### Frontend And Browser

- Asserted and inferred sections are visually and semantically distinct.
- All three supported inferred types render labels and technical identities.
- Disabled candidates expose their non-stageable reason.
- Single, multiple, select-all-visible, and clear-selection behavior works.
- Ambiguous source selection is required before staging.
- Stale job invalidates and clears selection.
- Successful staging clears selection and opens/focuses Changes.
- Errors preserve the current selection for correction when safe.
- Keyboard and screen-reader users can select candidates and invoke staging.
- No reasoning-view action calls apply or writes an ontology source directly.

Tests use copied temporary fixtures and must not modify committed examples or bundled external ontology assets.

## Acceptance Criteria

1. A completed applied-graph reasoning result displays asserted and inferred facts separately.
2. Every inferred fact has a server-issued stable identity and explicit inference type.
3. Supported subclass, individual-type, and object-property inferences expose deterministic stageability.
4. Users can select one, multiple, or all visible stageable inferred facts and clear selection.
5. `Stage as asserted` creates only existing typed staged edits.
6. One materialization request is all-or-nothing and bounded to 100 facts.
7. The current graph is reloaded and reasoning is reconfirmed before staging.
8. Stale, incomplete, missing, proposal-scoped, cross-project, and cross-user results cannot be materialized.
9. Already asserted, already staged/proposed, and within-batch duplicate triples create no duplicate entry.
10. Unambiguous writable local sources are selected automatically and ambiguous choices require explicit user input.
11. Imported, bundled, FIBO, external, and other read-only sources are never modified.
12. Safe import-derived facts may be asserted locally and are visibly marked as import-dependent.
13. Every created staged entry carries typed reasoning provenance, including user, time, fingerprint, fact, and target source.
14. Proposal review shows `Origin: Materialized from reasoning`, the explicit assertion, target source, prior entailment, and import dependence.
15. Materialized facts use the existing preview, validation, reasoning-impact, SHACL-impact, approval, apply, reload, and rollback path.
16. No source changes before explicit human acceptance/application.
17. React constructs no ontology triple, source decision, duplicate decision, or typed semantic edit.
18. Existing reasoning, proposal, validation, SHACL, collaboration, CLI, VS Code, and ontology-map behavior remains compatible.
19. Deterministic unit, integration, frontend, browser, and full repository verification passes.

## Boundary Check

- Phase fit: Phase 10 is explicitly scoped by `docs/architecture/phase-10-scope.md` and adds the requested human-controlled bridge from existing Phase 4 reasoning to existing staging.
- Current non-goals: the feature does not reactivate native AI execution, edit from the ontology map, add durable persistence, or introduce external indexing.
- Architecture: `core-types` owns neutral identity/provenance contracts; `semantic-engine` owns reasoning-fact conversion and semantic/source checks; `web-server` owns retained-job and shared-session orchestration; `web-app` owns temporary selection and presentation.
- Dependency direction: no engine module depends on Ktor or React, and `shared` remains unchanged.
- Existing tooling: Phase 10 reuses HermiT, OWL API, Jena-backed graph terms, typed-edit translation, validation, diff, proposal, and atomic application. It adds no custom RDF, OWL, SHACL, or reasoning framework.
- Speculative infrastructure: no new module, persistence layer, database, queue, identity system, frontend state framework, CLI command, or VS Code surface is required.

## Approved Contract Decisions

- `core-types` owns `InferenceMaterializationKind`, `InferenceStageability`,
  `InferenceImportDependence`, `InferenceMaterializationCandidate`,
  `InferenceMaterializationProvenance`, and the prepared/result batch contracts.
- The three existing typed edits cover the complete approved semantic output:
  `AddSuperclassEdit`, `AssignTypeEdit`, and `AddObjectPropertyAssertionEdit`.
- Candidate details retain the existing maximum of 100. Slice 0 found no response-size
  evidence requiring pagination, so Phase 10 adds none.
- `MutableSemanticJob` gains an internal immutable `submittedByUserId`. Submission
  receives the authenticated user ID. Existing project-scoped status/details/cancel
  behavior remains compatible; only materialization lookup requires the same user.
- Import dependence uses the deterministic evidence described above. Unknown evidence
  is unsafe and never guessed.
- Atomic staging uses the exact prepared-batch extension described in section 11.
- UI component filenames and private helper names remain compilation-level decisions
  within the allowed slices and do not alter these contracts.

If resolving one of these requires raw RDF fallback, automatic materialization, a new inference type, imported-source mutation, durable persistence, a new approval model, or browser-owned semantic policy, implementation must stop and the approved documents must be amended first.
