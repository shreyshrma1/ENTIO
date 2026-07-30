# Phase 11.5+ Scope

## Phase Name

**Phase 11.5+: Deterministic Compilation of Connected Document Models**

## Status

Implemented and verified on 2026-07-30.

Phase 11.5 remains the implemented foundation for Phase 11.5+.

## Purpose

Phase 11.5+ improves the final part of Entio’s document-ingestion pipeline.

The current pipeline can reliably:

- upload and parse documents;
- extract located text;
- discover concepts, facts, rules, and metadata;
- build a connected domain model;
- verify evidence;
- reach human review without unsafe automatic changes.

The remaining problem is semantic consistency.

The model can sometimes produce a useful connected structure, but the final planning stage is still asked to turn that structure into exact low-level ontology operations. This causes valid ideas to be lost, malformed, or attached to the wrong ontology elements.

Phase 11.5+ changes that responsibility:

> The model decides what the ontology should mean. Kotlin compiles that meaning into exact supported Entio operations.

## Current Problem

Recent trials show that the pipeline is mechanically reliable but semantically inconsistent.

Observed issues include:

- good connected concepts failing to become executable changes;
- missing relationships between otherwise useful concepts;
- properties being attached to unsuitable existing classes;
- conditional rules being modeled as ordinary fields;
- unsupported low-level operations blocking otherwise useful recommendations;
- significant variation across identical runs;
- inflated confidence despite weak ontology fit.

The main bottleneck is now final planning.

The model is being asked to perform two different jobs at once:

1. choose the correct semantic treatment;
2. format exact Entio operations correctly.

Phase 11.5+ separates these jobs.

## Central Product Principle

> AI produces a connected semantic plan. Kotlin deterministically produces executable ontology edits.

## Goals

Phase 11.5+ should:

- remove low-level Entio operation formatting from the model;
- preserve the connected domain model produced by earlier stages;
- introduce a clear semantic-plan contract;
- add a deterministic Kotlin change-set compiler;
- compile supported semantic patterns into existing typed operations;
- keep unsupported complex rules as review-only findings;
- add a completeness gate before final compilation;
- separate semantic quality from compilation quality;
- reduce run-to-run variation caused by operation formatting;
- preserve exact provenance;
- keep all changes under human review;
- retain the existing validation, reasoning, SHACL, proposal, apply, reload, and rollback workflow.

## Non-Goals

Phase 11.5+ must not add:

- automatic approval or application;
- raw RDF, Turtle, or SPARQL generation;
- a second ontology apply path;
- a general autonomous agent;
- unrestricted model loops;
- unsupported OWL or SHACL semantics;
- automatic promotion of illustrative people or companies into production ontology data;
- a new persistence layer;
- broad changes to intake, extraction, OCR, or document storage.

## New Final-Planning Boundary

The final model stage should no longer produce exact Entio edit operations.

Instead, it should produce a **semantic plan**.

A semantic plan should describe:

- what concept should exist;
- what kind of concept it is;
- how concepts relate;
- what hierarchy is intended;
- what individuals and facts are represented;
- what constraints or rules apply;
- which parts are executable;
- which parts must remain review-only;
- why each decision is supported;
- which evidence supports it.

Example:

```text
Concept: Payment
Kind: Class

Concept: Payment Approval Record
Kind: Class

Relationship: has approval record
Subject: Payment
Object: Payment Approval Record

Rule:
Payments at or above USD 25,000 require separate approval.

Treatment:
Compile the class and property structure.
Compile the rule only if the current SHACL support can represent it safely.
Otherwise retain it as review-only.
```

The model must not produce:

- final IRIs;
- raw triples;
- low-level operation ordering;
- exact Entio DTO payloads;
- source-file write instructions.

## Deterministic Kotlin Compiler

Kotlin should compile the verified semantic plan into existing supported operations.

The compiler should own:

- temporary-reference resolution;
- dependency ordering;
- final IRI generation;
- collision checks;
- duplicate checks;
- typed operation selection;
- domain and range application;
- hierarchy edits;
- individual creation;
- assertion creation;
- supported SHACL generation;
- review-only fallback;
- expanded edit counting;
- atomic recommendation boundaries.

Example compilation:

```text
Semantic plan:
Payment hasApprovalRecord PaymentApprovalRecord

Compiled operations:
1. Create class Payment
2. Create class PaymentApprovalRecord
3. Create object property hasApprovalRecord
4. Set domain to Payment
5. Set range to PaymentApprovalRecord
```

The compiler must fail safely when the semantic plan cannot be expressed using approved Entio operations.

## Supported Semantic Patterns

Phase 11.5+ should support deterministic compilation for common patterns.

### Class pattern

- create class;
- add preferred label;
- add definition;
- add superclass where supported.

### Object-property pattern

- create object property;
- add label and definition;
- set domain;
- set range.

### Datatype-property pattern

- create datatype property;
- add label and definition;
- set domain;
- set datatype range.

### Individual pattern

- create individual;
- assign type;
- add supported values and relationships.

### Relationship assertion pattern

- subject individual;
- object property;
- object individual.

### Required-relationship constraint

Example:

```text
A high-value payment must have an approval record.
```

Compile only when supported by current SHACL operations.

### Threshold constraint

Example:

```text
Payment amount must be at least USD 25,000 before the high-value approval rule applies.
```

Compile only when the current constraint model can represent the intended meaning without distortion.

### Separation-of-duty rule

Example:

```text
The same person may not both create and finally approve a payment.
```

This should remain review-only unless Entio has an approved executable rule representation.

### Aggregation rule

Example:

```text
Linked payments forming one business transaction must be aggregated before applying the threshold.
```

This should remain review-only unless safely expressible.

## Executable and Review-Only Outcomes

Every semantic-plan item must be classified as one of:

### Executable

Maps safely to existing typed ontology or SHACL operations.

### Review-only

Important business meaning that Entio cannot currently express safely.

### Blocked

Cannot proceed because evidence, dependencies, ontology fit, or supported operations are insufficient.

Review-only items must remain visible and retain provenance. They must not be forced into incorrect classes or properties.

## Completeness Gate

Before final compilation, Entio should compare the connected model with the full discovery inventory.

Every important discovery must receive one disposition:

- compiled into an executable recommendation;
- retained as review-only;
- merged into another item;
- matched to an existing ontology entity;
- classified as administrative metadata;
- classified as illustrative;
- rejected with reason;
- marked unsupported.

Any important discovery without a disposition should block final planning.

For the current benchmark documents, the completeness gate should explicitly account for concepts such as:

- Payment;
- Account;
- Payment Instruction;
- Payment Approval Record;
- Supporting Record;
- Invoice;
- Payment Destination;
- Consumer Loan;
- Loan Servicing;
- Payment Suspense;
- Servicing Control;
- High-Value Approval Rule;
- Linked-Payment Aggregation Rule.

## Semantic Coverage and Compilation Success

Phase 11.5+ should track two separate quality measures.

### 1. Semantic Coverage

Measures whether the connected model identified the important:

- concepts;
- relationships;
- individuals;
- facts;
- controls;
- rules;
- ambiguities.

### 2. Compilation Success

Measures whether accepted semantic-plan items were converted into valid Entio operations.

A run may have:

- high semantic coverage but poor compilation;
- low semantic coverage but successful compilation of the few items found;
- both high;
- both low.

These measures must not be combined into one vague success score.

## Confidence Model

Confidence should remain separated into:

- evidence confidence;
- modeling confidence;
- ontology-fit confidence;
- compilation confidence.

Overall confidence should not exceed the weakest relevant dimension.

Compilation confidence should reflect whether Kotlin successfully mapped the semantic plan into approved typed operations.

## Final-Planning Model Responsibilities

The final model stage should:

- select the semantic treatment;
- group related concepts and relationships;
- decide which items belong in one recommendation;
- identify executable versus review-only meaning;
- provide rationale;
- reference exact evidence;
- incorporate critic findings;
- identify unresolved ambiguity.

It should not:

- generate exact Entio operations;
- generate final IRIs;
- choose unsupported SHACL forms;
- silently omit discoveries;
- attach a rule to an unrelated class merely because the correct class is absent.

## Compiler Responsibilities

Kotlin should:

- resolve all temporary IDs;
- expand semantic patterns into typed operations;
- enforce declaration and dependency order;
- calculate expanded edit counts;
- split recommendations only when dependency-safe;
- reject unsupported mappings;
- preserve provenance;
- produce semantic diff;
- run validation;
- run reasoning;
- run SHACL checks;
- prepare existing private-draft batches.

## Atomicity

Atomicity should be measured after semantic patterns expand into typed edits.

One atomic recommendation may contain no more than the existing approved edit limit.

If a recommendation expands beyond the limit:

- split it only when each resulting recommendation is independently coherent;
- preserve dependency safety;
- do not allow partial invalid application;
- otherwise block it and request redesign.

## Benchmark and Regression Requirements

The existing two test PDFs should remain permanent regression fixtures.

The benchmark should require that Entio:

- identifies Payment;
- identifies Payment Approval Record;
- identifies consumer-loan servicing controls;
- identifies the high-value approval rule;
- identifies important example individuals and facts;
- preserves exact provenance;
- marks illustrative individuals clearly;
- does not create Compliance Status from document metadata;
- does not create Customer → Loan as has servicing compliance;
- does not attach payment rules to Account merely because Payment is absent;
- does not model Account → Invoice as payment approval;
- keeps linked-payment aggregation and separation-of-duty rules review-only when unsupported;
- compiles supported connected-model items successfully;
- explains every discovery disposition.

## Initial Quality Targets

Recommended initial acceptance targets:

- required core concepts found in at least 9 of 10 identical runs;
- required major relationships found in at least 8 of 10 identical runs;
- at least 95% of supported semantic-plan items compile successfully;
- prohibited bad recommendations appear in 0 of 10 runs;
- unsupported complex rules remain review-only in 10 of 10 runs;
- exact provenance remains valid in 10 of 10 runs;
- no ontology changes are applied automatically.

These are initial engineering targets, not permanent product guarantees.

## Suggested Delivery Areas

The later ExecPlan should likely separate work into:

1. freeze and record the current reliable baseline;
2. define the semantic-plan contract;
3. define deterministic semantic patterns;
4. implement the Kotlin compiler;
5. add temporary-reference and dependency resolution;
6. add the completeness gate;
7. separate semantic coverage from compilation metrics;
8. update final-planning prompts and schemas;
9. update grouped review output;
10. rerun permanent benchmarks;
11. compare the current model with a stronger model using the same pipeline;
12. finalize documentation and verification.

The exact slice structure should be decided after repository inspection.

## Required Test Scenarios

The later spec and ExecPlan should include tests for:

- connected class and property compilation;
- domain and range compilation;
- datatype-property compilation;
- individual and assertion compilation;
- supported SHACL compilation;
- unsupported complex-rule fallback;
- temporary-reference resolution;
- dependency ordering;
- duplicate and collision prevention;
- expanded edit counting;
- atomic split safety;
- completeness ledger;
- semantic coverage scoring;
- compilation success scoring;
- incorrect metadata classification;
- missing supporting concepts;
- bad domain and range rejection;
- exact provenance preservation;
- illustrative individual approval gates;
- repeated-run consistency;
- deterministic benchmark expectations;
- validation, reasoning, SHACL, proposal, apply, reload, and rollback regression.

## Acceptance Criteria

Phase 11.5+ is complete when:

1. The final model stage produces semantic plans rather than exact Entio operations.
2. Kotlin compiles supported semantic plans into existing typed operations.
3. Missing supporting concepts can be created before dependent properties.
4. Unsupported complex rules remain review-only.
5. Every important discovery receives a final disposition.
6. Semantic coverage and compilation success are measured separately.
7. Temporary references resolve deterministically.
8. Expanded edit limits and atomicity are enforced.
9. Exact provenance is preserved.
10. Grouped recommendations remain understandable to reviewers.
11. The existing private-draft and proposal workflow is reused.
12. No ontology source changes occur before human approval.
13. The permanent regression fixtures meet the approved quality targets.
14. Repeated identical runs show materially improved consistency.
15. Existing validation, reasoning, SHACL, apply, reload, and rollback behavior remains green.

## Open Questions for the Spec

The spec should resolve:

- What exact semantic-plan item types are supported?
- Which semantic patterns compile deterministically?
- Which SHACL rules are supported?
- What expanded edit limit applies per recommendation?
- How should dependency-safe splitting work?
- Which discoveries count as important for completeness?
- How are semantic coverage scores calculated?
- How are compilation success scores calculated?
- How are critic findings incorporated into the semantic plan?
- Can the user edit the semantic plan before compilation?
- Which review-only findings persist after apply?
- When should a stronger model be used?
- How should model comparisons be scored for cost, speed, consistency, and quality?

If an answer requires raw RDF, unsupported rule execution, autonomous agents, or automatic approval, it should be deferred rather than silently added to Phase 11.5+.
