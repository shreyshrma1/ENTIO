# Phase 11.5 Scope

## Phase Name

**Phase 11.5: Multi-Stage AI Modeling and Connected Ontology Change Sets**

## Status

Approved for implementation on 2026-07-27.

Amended on 2026-07-29 to reduce ordinary provider cost. The current production
pipeline keeps the same logical responsibilities but consolidates them into:

```text
per-document discovery
→ connected cross-document semantic synthesis
→ ontology-aware recommendation planning and modeling review
→ deterministic verification
→ human review
```

Cross-document reconciliation is part of semantic synthesis. Ontology alignment,
modeling critique, and final change-set planning are part of one ontology-aware
planning call. Model consolidation remains conditional on connected-model
chunking. A correction call is made only when deterministic validation returns a
specific repairable finding. The older separate-stage descriptions below define
the responsibilities that the consolidated calls must still perform; they no
longer require one provider call per heading.

The required contract, migration, prompt, limit, and benchmark audit is recorded in
`docs/decisions/phase-11.5-slice-0-contract-audit.md`. Production implementation
begins with ExecPlan Slice 1 only after Slice 0 is verified and merged.

## Purpose

Phase 11.5 redesigns document ingestion so Entio no longer asks one model call to jump directly from document text to isolated ontology edits.

Instead, Entio uses specialized AI stages without assigning every logical
responsibility to a separate provider call:

```text
document discovery
→ connected cross-document semantic synthesis
→ ontology-aware recommendation planning and modeling review
→ deterministic verification
→ human review
```

The goal is to improve modeling quality, reduce incorrect domain and range assignments, preserve important concepts that do not yet exist in the ontology, and prevent document metadata or complex business rules from being forced into inappropriate ontology properties.

## Central Product Principle

> AI should reason in specialized stages. Entio should deterministically verify every executable result before it enters the proposal workflow.

## Problem Being Solved

The current Phase 11 approach encourages the model to produce executable edits too early.

When the correct supporting concepts are missing, the model may:

- attach properties to unrelated existing classes;
- treat document metadata as business concepts;
- force conditional rules into datatype properties;
- miss supporting classes and relationships;
- generate isolated edits instead of a connected model;
- assign overly high confidence to weak recommendations.

Examples include:

- creating `complianceStatus` from a document-control value such as `Status: Approved`;
- attaching a high-value payment threshold to `Account` because `Payment` does not exist;
- creating `hasServicingCompliance` from `Customer` to `Loan` without evidence;
- connecting payment approval to `Account` and `Invoice` instead of modeling `Payment` and `PaymentApprovalRecord`.

Phase 11.5 fixes the analysis contract and workflow rather than relying on more prompt prohibitions.

## Goals

Phase 11.5 should:

- separate document understanding from ontology editing;
- use multiple bounded model calls with specific responsibilities;
- preserve discoveries that do not yet map to Entio edit types;
- support connected recommendations containing several dependent operations;
- allow new operations to reference other new operations through temporary IDs;
- model both ontology structure and business facts;
- distinguish executable edits from review-only complex rules;
- critique modeling quality before final recommendations are produced;
- separate evidence confidence, modeling confidence, and ontology-fit confidence;
- deterministically verify every executable change set;
- keep all changes under human review;
- provide visible progress, cancellation, and provenance for every AI stage;
- add permanent regression fixtures for known document-ingestion failures.

## Non-Goals

Phase 11.5 must not add:

- automatic approval or application;
- raw RDF, Turtle, or SPARQL generation;
- unrestricted autonomous agents;
- unbounded model loops;
- unrestricted external retrieval;
- a new ontology apply path;
- replacement of deterministic validation with model judgment;
- unsupported OWL or SHACL semantics;
- automatic promotion of illustrative examples into production ontology data;
- a general-purpose assistant unrelated to document ingestion.

## Analysis Pipeline

### 1. Document Discovery

The discovery call should identify what is present in the document without drafting ontology edits.

It should extract:

- concepts;
- definitions;
- individuals;
- illustrative examples;
- relationships;
- attributes and values;
- requirements;
- controls;
- conditional business rules;
- conflicts;
- ambiguities;
- document metadata;
- authority and applicability information.

Every discovery must include exact evidence.

Required evidence includes:

- document ID;
- page number;
- section or heading where available;
- block or paragraph reference;
- exact excerpt;
- extraction method;
- OCR confidence where applicable.

The discovery stage must distinguish:

- business content;
- administrative document metadata;
- explicit facts;
- implied facts;
- model interpretation;
- illustrative examples.

It must not produce executable ontology edits.

### 2. Connected Domain Modeling

The modeling call should use the discovery inventory to create a connected domain model.

It should not be limited to classes and properties that already exist.

A single recommendation may propose a connected set such as:

```text
Create Payment
Create PaymentApprovalRecord
Create hasApprovalRecord
Set hasApprovalRecord domain to Payment
Set hasApprovalRecord range to PaymentApprovalRecord
Create approvedBy
Set approvedBy domain to PaymentApprovalRecord
Set approvedBy range to Approver
Add separate-approval rule
```

This stage should identify:

- supporting concepts needed for a relationship;
- appropriate class hierarchy;
- property meaning;
- domain and range;
- individuals and assertions;
- constraints;
- complex rules that cannot be expressed safely.

### 3. Cross-Document Reconciliation Responsibility

When several documents are part of one task, or when durable prior provenance is
available, connected semantic synthesis should reconcile them without requiring
a separate provider call.

The reconciliation call should identify:

- duplicate concepts;
- alternate labels;
- supporting evidence;
- conflicting meanings;
- refinements;
- superseding statements;
- context-specific interpretations;
- date, jurisdiction, product, and business-unit differences.

A newer document must not automatically override an older one.

The call may recommend conflict handling, but human confirmation remains required.

### 4. Ontology Alignment Responsibility

The ontology-aware recommendation-planning call should compare the connected
domain model with:

- the applied local ontology;
- imported ontologies;
- current private draft;
- shared staged work;
- current proposal;
- approved FIBO content;
- durable prior provenance from earlier applied ingestion workflows.

For each modeled concept or relationship, it should recommend:

- reuse;
- extend;
- revise;
- create;
- split;
- merge;
- conflict review;
- leave unchanged;
- unsupported.

Alignment should happen after the connected model exists so missing supporting concepts do not force incorrect reuse.

### 5. Modeling Critique Responsibility

The ontology-aware recommendation-planning call must review the proposed model
before returning recommendations. A separate critic call is not unconditional;
deterministic findings trigger a bounded correction call when needed.

It should ask:

- Does the evidence support the proposed meaning?
- Are domain and range semantically correct?
- Is document metadata being mistaken for a business concept?
- Is a conditional rule being modeled incorrectly as a property?
- Are important supporting classes missing?
- Are important relationships missing?
- Are illustrative examples clearly marked?
- Is the proposal connected appropriately to the current ontology?
- Are split, merge, and revision decisions justified?
- Are confidence levels calibrated appropriately?

The critic should be able to:

- approve;
- revise;
- split;
- replace;
- downgrade;
- reject;
- request clarification.

The critic output is advisory until incorporated into the final plan.

### 6. Final Change-Set Planning Responsibility

The consolidated ontology-aware planning call should incorporate:

- document discoveries;
- connected model;
- reconciliation results;
- ontology alignment;
- critic findings;
- user-supplied clarifications.

It should produce bounded structured recommendations.

Each recommendation may contain several dependent operations and must remain atomic at the recommendation level.

## Connected Change-Set Contract

The model response must support:

- temporary references such as `new:Payment`;
- multiple new connected concepts;
- dependencies between new operations;
- atomic multi-operation recommendations;
- existing ontology references;
- individuals and fact assertions;
- typed RDF operations already supported by Entio;
- supported SHACL constraints;
- review-only complex rules;
- rationale for every class, property, domain, range, and relationship;
- exact provenance links;
- separate confidence dimensions.

Example:

```text
Recommendation: Model high-value payment approval

Operations:
1. Create class new:Payment
2. Create class new:PaymentApprovalRecord
3. Create object property new:hasApprovalRecord
4. Set domain of new:hasApprovalRecord to new:Payment
5. Set range of new:hasApprovalRecord to new:PaymentApprovalRecord
6. Create datatype property new:paymentAmount
7. Set domain of new:paymentAmount to new:Payment
8. Set range of new:paymentAmount to xsd:decimal

Constraint:
Payments at or above USD 25,000 require a separate approval record.

Review-only rule:
Linked payments forming one business transaction must be aggregated before applying the threshold.
```

## Temporary Reference Rules

Temporary IDs should:

- be unique within one final plan;
- use a fixed deterministic format;
- reference only items in the same plan;
- resolve before typed draft creation;
- fail validation if unresolved;
- never become final IRIs;
- preserve dependency order.

The final IRI generation process remains owned by Kotlin.

## Executable and Review-Only Outcomes

Phase 11.5 must distinguish:

### Executable changes

Changes that map safely to existing typed RDF or SHACL operations.

### Review-only findings

Important business meaning that Entio cannot safely express with current edit types.

Examples:

- linked payments must be aggregated as one business transaction;
- one employee may not both create and finally approve the same payment;
- one standard applies only when a specific service governs the transaction;
- the more customer-protective interpretation applies while two policies conflict.

Review-only findings must not be forced into incorrect datatype or object properties.

## Confidence Model

Each recommendation should include:

- **Evidence confidence**: how clearly the source supports the discovery;
- **Modeling confidence**: how confident the model is that the proposed ontology structure is conceptually correct;
- **Ontology-fit confidence**: how well the proposal fits the current ontology and imported models;
- **Overall confidence**: limited by the weakest of the three dimensions.

A high evidence score must not hide poor modeling quality.

## Deterministic Verification

Only after the model has completed reasoning should Entio verify the final plan.

Entio must verify:

- response schema;
- numeric and count limits;
- exact evidence quotations;
- document and page references;
- existing ontology references;
- temporary-reference resolution;
- dependency ordering;
- IRI validity;
- collision prevention;
- duplicate prevention;
- supported typed RDF operations;
- supported SHACL operations;
- source writability;
- graph fingerprint freshness;
- complete change-set validation;
- semantic diff;
- reasoning;
- SHACL validation.

Deterministic validation can prove evidence and structural correctness.

It cannot prove that a modeling judgment is conceptually correct. The critic pass and human review remain necessary.

## Human Review

The review UI should group information by recommendation rather than by isolated edit.

For each recommendation, users should see:

- discovery summary;
- connected concepts;
- proposed operations;
- dependencies;
- exact evidence;
- model rationale;
- critic findings;
- confidence dimensions;
- executable changes;
- review-only rules;
- expected ontology impact.

Users should be able to:

- accept;
- reject;
- edit;
- rematch;
- request reconsideration;
- split a recommendation;
- exclude individual operations when safe;
- provide clarification;
- submit accepted recommendations to the existing private-draft workflow.

## Illustrative Individuals

Named people, companies, accounts, loans, invoices, and payments found in policy examples should be identified.

They must be marked as:

- illustrative;
- production;
- ambiguous;
- unknown.

Illustrative individuals must not automatically become production ontology data.

Creating them as individuals requires explicit reviewer approval.

## Status, Progress, and Provenance

Each AI stage should be:

- visible in task progress;
- individually bounded;
- cancellable;
- timed;
- recorded in provenance;
- associated with model ID and prompt version;
- associated with input and output hashes;
- safe to retry only under approved rules.

Suggested status stages:

```text
discovering document content
→ building connected model
→ reconciling documents
→ aligning with ontology
→ critiquing model
→ preparing final change sets
→ deterministic verification
→ awaiting review
```

## Model Call Limits

The later spec and ExecPlan should pin:

- maximum documents per task;
- maximum discovery items;
- maximum connected-model entities;
- maximum recommendations;
- maximum operations per recommendation;
- maximum model calls per task;
- timeout per call;
- retry limits;
- maximum total task duration;
- maximum evidence blocks per recommendation.

No stage may start an unbounded agent loop.

## Accuracy Benchmark

The two Phase 11 test PDFs should become permanent regression fixtures.

The benchmark must require that Entio:

- identifies `Payment`;
- identifies `PaymentApprovalRecord`;
- identifies consumer-loan servicing controls;
- identifies the high-value approval rule;
- identifies core individuals and facts from the examples;
- distinguishes illustrative individuals from production data;
- preserves exact provenance;
- does not create `Compliance Status` from document-control metadata;
- does not propose `Customer → Loan` as `has servicing compliance`;
- does not attach a payment rule to `Account` merely because `Payment` is absent;
- does not model `Account → Invoice` as payment approval;
- represents unsupported complex rules as review-only findings;
- proposes connected supporting concepts before dependent properties;
- produces confidence dimensions that reflect both evidence and modeling quality.

## Required Test Scenarios

The later spec and ExecPlan should include:

- discovery inventory completeness;
- administrative metadata classification;
- explicit and implied evidence;
- connected new classes and properties;
- temporary references between proposed entities;
- dependency ordering;
- multi-operation atomic recommendations;
- individuals and illustrative examples;
- conditional rule represented as SHACL when supported;
- complex rule retained as review-only;
- local ontology reuse;
- FIBO reuse;
- ontology extension;
- ontology revision;
- split and merge recommendation;
- conflict and supersession;
- critic rejection of incorrect domain and range;
- critic rejection of metadata-derived business concepts;
- completeness comparison between discoveries and final recommendations;
- separate confidence dimensions;
- malformed or unresolved temporary references;
- invented evidence rejection;
- stale ontology fingerprint;
- deterministic validation failure;
- cancellation at every model stage;
- provider timeout and malformed output;
- permanent regression benchmark for the two sample PDFs.

## Suggested Delivery Areas

The later ExecPlan should likely separate work into:

1. connected change-set and temporary-reference contracts;
2. document discovery contract and model call;
3. connected domain-model call;
4. cross-document reconciliation;
5. ontology alignment;
6. modeling critic;
7. final plan generation;
8. deterministic reference and dependency verification;
9. confidence recalibration;
10. review UI grouping and status updates;
11. regression benchmark and end-to-end verification.

The exact slice structure should be decided after repository inspection.

## Acceptance Criteria

Phase 11.5 is complete when:

1. Document ingestion no longer jumps directly from text to isolated edits.
2. Discovery, modeling, reconciliation, alignment, critique, and final planning are separate bounded stages.
3. New ontology operations can reference other proposed entities through temporary IDs.
4. One recommendation can contain several connected atomic operations.
5. Important rules that cannot be expressed safely remain review-only.
6. Administrative document metadata is separated from business ontology content.
7. Individuals and facts are extracted and illustrative examples are clearly marked.
8. Domain and range recommendations include explicit rationale.
9. Confidence is split into evidence, modeling, and ontology-fit dimensions.
10. The critic can revise, reject, split, or replace weak recommendations.
11. Kotlin verifies evidence, references, dependencies, supported operations, duplicates, and graph validity.
12. Accepted recommendations continue through the existing private-draft, validation, reasoning, SHACL, proposal, approval, apply, reload, and rollback workflow.
13. The permanent regression fixtures pass all required positive and negative expectations.
14. No ontology source changes occur before human approval.

## Open Questions for the Spec

The spec should resolve:

- Which stages use separate model calls versus combined calls?
- May the critic use the same model as the modeling stage?
- What exact temporary-ID format is used?
- What is the maximum number of operations in one recommendation?
- Which SHACL rule types are executable?
- How are review-only rules displayed and retained?
- How are illustrative individuals represented in contracts?
- How is completeness measured between discoveries and final recommendations?
- What happens when the critic and modeling stage disagree?
- Can users rerun only one failed stage?
- Which stage owns cross-document conflict resolution?
- How are prompt versions and intermediate results stored?
- Which confidence formula limits overall confidence?
- How are existing Phase 11 single-stage contracts migrated or retired?

If an answer requires raw RDF, unsupported semantic operations, autonomous agents, or automatic approval, it should be deferred rather than silently added to Phase 11.5.
