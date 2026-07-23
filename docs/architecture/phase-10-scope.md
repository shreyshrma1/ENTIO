# Phase 10 Scope

## Phase Name

**Phase 10: Materialize Inferred Relationships**

## Status

Draft

## Purpose

Phase 10 lets a user take selected inferred facts produced by Entio's OWL reasoner and stage them as normal asserted ontology changes.

Entio already keeps inferred facts separate from asserted source facts. Phase 10 adds a safe bridge between those two worlds:

```text
reasoning result
→ user selects inferred facts
→ Entio converts them into existing typed edits
→ normal preview, validation, approval, apply, reload, and rollback
```

The user must remain in control. Entio must never automatically write inferred facts into ontology sources.

## Central Product Promise

> A user can review an inferred relationship, choose to make it explicit, and send it through the same trusted proposal workflow as any other ontology edit.

## Problem

Entio can already infer relationships such as:

- transitive superclass relationships;
- inferred individual types;
- inferred object-property assertions.

These facts are useful, but they remain read-only reasoning results.

Users currently have no direct way to:

- select one or more inferred facts;
- turn them into explicit asserted statements;
- preview the impact;
- validate them;
- approve them through the normal review flow.

## Goals

Phase 10 should:

- let users select supported inferred facts from the Reasoning view;
- convert them into existing typed staged edits;
- prevent duplicate assertions;
- reject stale reasoning results;
- preserve the origin of each staged assertion;
- support batch staging of multiple inferred facts;
- reuse the normal proposal, validation, approval, apply, reload, and rollback workflow;
- keep inferred and asserted facts visually distinct;
- remain deterministic and fully human-controlled.

## Non-Goals

Phase 10 must not add:

- automatic materialization of all inferred facts;
- background assertion of reasoning results;
- AI-selected inferred facts without user confirmation;
- direct writes to ontology files;
- raw RDF or SPARQL mutation;
- new inference rules;
- replacement of HermiT or the current reasoning engine;
- unsupported inference types;
- weakening ontology constraints to make an inference stageable;
- inferred facts displayed as if they were already asserted.

## Supported Inference Types

Phase 10 should support only inference types that map cleanly to existing typed edit paths.

1. **Subclass relationship**
   - Example: `MortgageLoan subClassOf Loan`
   - Existing path: add superclass

2. **Individual type**
   - Example: `loan123 rdf:type Loan`
   - Existing path: assign type

3. **Object-property assertion**
   - Example: `loan123 hasBorrower customer456`
   - Existing path: add object-property assertion

Anything else remains unsupported unless added to the approved spec.

## User Flow

```text
User opens Reasoning
→ Entio shows inferred facts separately from asserted facts
→ user selects one or more supported inferred facts
→ user chooses "Stage as asserted"
→ Entio checks freshness, duplicates, source scope, and edit compatibility
→ Entio converts valid facts into typed staged edits
→ user reviews semantic diff and validation
→ proposal enters the normal human approval flow
```

## Reasoning View Changes

The Reasoning section should show supported inferred facts with:

- subject;
- relationship or type;
- object or target;
- inference type;
- source reasoning run;
- project fingerprint;
- whether the fact is already asserted;
- whether it is stageable;
- reason when it is not stageable.

Users should be able to:

- select one fact;
- select multiple facts;
- stage selected facts;
- clear selection;
- view why a fact cannot be staged.

The UI must not allow direct application from the Reasoning view.

## Staging Rules

Before staging, Entio must verify:

- the reasoning result belongs to the current project;
- the reasoning fingerprint matches the current project fingerprint;
- the inferred fact still exists in a fresh reasoning result;
- the target source is writable and allowed;
- the fact is not already asserted;
- the matching typed edit path exists;
- the operation does not require raw RDF fallback.

If any check fails, the fact must not be staged.

For one user action, recommended behavior is all-or-nothing batch staging.

## Duplicate Prevention

Entio must detect duplicates against:

- currently asserted ontology facts;
- current staged changes;
- current proposal contents;
- repeated selections in the same batch.

An already asserted fact should be shown as informational, not stageable.

A fact already staged should link to the existing staged item rather than create another copy.

## Provenance

Every staged assertion created from reasoning must record that it originated from an inferred fact.

Required provenance should include:

- inference type;
- reasoning run or result ID;
- reasoning fingerprint;
- original inferred fact identity;
- user who staged it;
- timestamp;
- target source.

This is workflow metadata. It should not automatically become an ontology annotation.

The proposal review should clearly show:

```text
Origin: Materialized from reasoning
```

## Source Selection

Entio must determine where the asserted statement will be written.

Rules:

- use the writable local source that owns the subject when unambiguous;
- if exactly one writable source is valid, select it automatically;
- if multiple writable sources are valid, require user selection;
- never write into imported or read-only external sources;
- never modify FIBO or another bundled external ontology;
- always show the chosen target source before staging.

The spec must define exact source-resolution behavior for each supported inference type.

## Import-Derived Inferences

An inferred fact may depend on imported ontology content.

It may be asserted locally only when:

- the resulting assertion is valid in a writable local source;
- all referenced IRIs are allowed;
- no imported source is modified;
- the proposal clearly shows that the inference depended on imported knowledge.

If this cannot be represented safely with existing typed edits, the fact remains read-only.

## Staleness

Reasoning results must be fingerprint-bound.

If the project changes after reasoning ran:

- staging is blocked;
- the UI shows the result as stale;
- the user must rerun reasoning;
- stale selections are cleared or invalidated.

Display text alone must never be used to identify an inference.

## Validation And Review

Materialized inferred facts must follow the normal workflow:

```text
typed edit
→ staged change
→ preview
→ semantic diff
→ validation
→ reasoning impact
→ SHACL impact where relevant
→ proposal
→ human approval or rejection
→ apply
→ reload
→ rollback on failure
```

No new approval path should be created.

The proposal should show:

- the asserted statement being added;
- its reasoning origin;
- whether it was already logically entailed before materialization;
- validation and reasoning impact;
- target source.

## Read-Only Reasoning Boundary

Reasoning remains read-only.

The reasoner may produce facts, explanations, consistency results, and impact results.

It must not:

- stage changes by itself;
- select facts for materialization;
- apply edits;
- choose ambiguous source targets;
- bypass human approval.

## Server And Frontend Ownership

Kotlin should own:

- inference identity;
- supported-type checks;
- fingerprint validation;
- duplicate detection;
- source resolution;
- conversion to typed edits;
- batch atomicity;
- provenance metadata;
- safe errors.

React should own:

- selection state;
- display;
- button state;
- stale and error messages;
- handoff to existing proposal review.

React must not construct ontology statements independently.

## Suggested Delivery Areas

The later spec and ExecPlan should likely separate work into:

1. materialization contracts;
2. reasoning-result to typed-edit adapter;
3. duplicate, source, and staleness checks;
4. batch staging service;
5. Reasoning UI selection and stage action;
6. proposal provenance display;
7. end-to-end verification and regressions.

The exact slice structure should be decided after repository inspection.

## Required Test Scenarios

The spec and ExecPlan should include tests for:

- one inferred subclass relationship;
- one inferred individual type;
- one inferred object-property assertion;
- multiple inferred facts;
- already asserted fact rejection;
- already staged fact rejection;
- stale fingerprint rejection;
- unsupported inference type rejection;
- ambiguous writable source requiring user input;
- imported/read-only source protection;
- import-derived inference staged into a valid local source;
- provenance visible in proposal review;
- no direct ontology write before approval;
- normal apply, reload, and rollback;
- cross-project and cross-user isolation;
- current reasoning, proposal, validation, SHACL, CLI, and web regressions.

## Acceptance Criteria

Phase 10 is complete when:

1. Supported inferred facts are shown separately in Reasoning.
2. Users can select one or more supported inferred facts.
3. `Stage as asserted` creates existing typed staged edits.
4. Already asserted or staged facts do not create duplicates.
5. Stale reasoning results cannot be staged.
6. Ambiguous source selection is handled explicitly.
7. Imported and read-only sources are never modified.
8. Reasoning provenance is visible in proposal review.
9. Materialized facts use the normal validation and approval workflow.
10. No inferred fact is written without explicit human action.
11. No raw RDF or direct source mutation is introduced.
12. Existing reasoning and proposal behavior remains unchanged.
13. Deterministic unit, integration, UI, and end-to-end tests pass.

## Open Questions For The Spec

The spec should resolve:

- Should batch staging be all-or-nothing or allow explicit partial acceptance?
- What exact reasoning result IDs are stable enough for provenance and duplicate checks?
- What target source should be chosen when subject and object come from different local sources?
- How should import-derived inference provenance be displayed?
- Should users be able to select all visible stageable facts?
- Should later reasoning runs mark materialized facts as both asserted and inferred?
- What exact proposal metadata fields should store reasoning origin?
- Should CLI support be included now or deferred?

If an answer requires new inference types, raw RDF mutation, automatic materialization, or changes to the approval model, it must be deferred rather than expanding Phase 10.
