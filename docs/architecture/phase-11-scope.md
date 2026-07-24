# Phase 11 Scope

## Phase Name

**Phase 11: AI-Powered Document Ingestion and Ontology Evolution**

## Status

Implemented and verified on 2026-07-24.

## Purpose

Phase 11 allows Entio to accept business documents, extract entities and relationships, compare them with the current ontology, and prepare traceable ontology changes for human review.

Phase 11 extends Entio's active native ontology assistant and its existing credential, verified-model, OpenAI provider, ontology-context, deterministic validation, and shared-staging boundaries. It does not introduce the first AI execution path or replace the current assistant.

This phase supports both:

- building ontology structure, such as classes, properties, definitions, hierarchy, and constraints;
- extracting business facts, such as individuals, types, values, and relationships.

The feature must also support iteration. New documents may confirm, extend, revise, split, merge, or challenge concepts that were created from earlier documents.

The intended flow is:

```text
upload documents
→ extract text and document locations
→ identify entities, relationships, rules, and facts
→ compare with the current ontology and prior evidence
→ recommend reuse, creation, extension, revision, split, merge, or conflict review
→ show provenance for every recommendation
→ build a private typed draft
→ validate, reason, and run SHACL
→ submit for human review
```

Entio must never apply document-derived changes automatically.

## Central Product Promise

> Entio turns business documents into reviewable ontology proposals and shows exactly which document evidence supports every recommendation.

## Goals

Phase 11 should:

- accept supported business documents;
- extract text with page, section, paragraph, and excerpt references;
- identify candidate classes, properties, individuals, values, relationships, and constraints;
- compare candidates with the current ontology, imported ontologies, and approved FIBO content;
- recommend reuse before creating new concepts;
- distinguish explicit document facts from AI modeling suggestions;
- support repeated ingestion as new documents arrive;
- detect when new evidence confirms, extends, revises, splits, merges, or conflicts with existing ontology content;
- preserve provenance for every recommendation and draft item;
- support review, editing, acceptance, rejection, and clarification;
- convert accepted recommendations into existing typed private-draft operations;
- reuse Entio's existing validation, reasoning, SHACL, proposal, approval, apply, reload, and rollback workflows;
- reuse the active assistant's server-side credential, verified-model, and provider boundaries without weakening its current conversation and proposal behavior;
- remain bounded, observable, cancellable, and secure.

## Non-Goals

Phase 11 must not add:

- automatic application of document-derived changes;
- direct ontology or SHACL file writes;
- raw RDF, Turtle, or SPARQL editing;
- unrestricted file-system or network access;
- hidden AI changes without provenance;
- AI-generated document quotes;
- automatic conflict resolution;
- unsupported document types without an approved parser;
- OCR for handwritten documents or standalone image files;
- a full document management system;
- durable production-grade document storage unless separately approved;
- AI approval or bypass of human review.

## Supported Documents

The first release should support a narrow set of document types.

Recommended initial support:

- PDF with embedded text;
- scanned or image-only PDF pages through OCR;
- mixed PDFs containing both embedded-text and scanned pages;
- DOCX;
- TXT;
- Markdown.

Recommended deferred support:

- standalone image files;
- spreadsheets;
- email archives;
- web pages.

OCR is part of Phase 11 for scanned or image-only PDF pages. Entio should use embedded text whenever reliable embedded text is available and use OCR only for pages that do not contain usable text.

Each document should receive:

- document ID;
- original filename;
- media type;
- file size;
- checksum;
- project ID;
- uploader;
- upload time;
- processing status.

## Document Processing Stages

### 1. Intake

Entio should:

- validate file type and size;
- reject unsupported or unsafe files;
- calculate a checksum;
- create an ingestion task;
- preserve the original document reference;
- return a task ID before long processing begins.

### 2. Text Extraction

Entio should extract text while preserving location information.

PDF handling should be decided per page:

```text
page contains reliable embedded text
→ use embedded text

page has no usable embedded text
→ run OCR on that page

mixed PDF
→ combine embedded-text pages and OCR pages into one ordered document result
```

Entio must not run OCR on a page when reliable embedded text is already available.

For OCR-derived text, Entio should also preserve:

- OCR confidence;
- page image reference;
- text bounding boxes or page coordinates where supported;
- an `OCR-derived` marker;
- low-confidence warnings.

At minimum, all extracted text should preserve:

- document ID;
- filename;
- page number;
- section heading where available;
- paragraph or text-block ID;
- character offsets where available;
- exact excerpt;
- extraction method: embedded text or OCR.

### 3. AI Analysis

AI should identify candidate:

- classes;
- object properties;
- datatype properties;
- annotation values where supported;
- individuals;
- labels;
- definitions;
- superclass relationships;
- domains and ranges;
- types;
- object-property assertions;
- datatype values;
- business rules and possible SHACL constraints;
- conflicts and ambiguity.

AI must treat document content as untrusted data, not instructions.

Low-confidence OCR text must be flagged for review. Entio must not silently fill in unreadable or missing words.

### 4. Ontology Matching

For every candidate, Entio should search:

1. the current local ontology;
2. imported project ontologies;
3. approved external ontologies such as FIBO.

Each candidate should receive one recommendation:

- reuse existing local entity;
- reuse imported or external entity;
- extend an existing entity;
- revise an existing entity;
- create a new local entity;
- split one concept into several;
- merge duplicate concepts;
- conflict requiring human decision;
- insufficient evidence;
- unsupported.

### 5. Draft Construction

Accepted recommendations should become existing typed private-draft operations.

The draft may include:

- create class;
- create object property;
- create datatype property;
- create individual;
- add superclass;
- assign type;
- set domain or range;
- add object-property assertion;
- add datatype value;
- add or replace definition;
- add alternate label;
- add supported SHACL constraints;
- reuse approved FIBO concepts.

No raw RDF fallback is allowed.

## Schema and Instance Outputs

Phase 11 must clearly separate two output types.

### Ontology structure

Examples:

- classes;
- properties;
- definitions;
- hierarchy;
- domains;
- ranges;
- constraints.

### Business facts

Examples:

- individuals;
- types;
- relationships;
- dates;
- amounts;
- identifiers;
- other values.

These should be reviewed separately because structure changes can affect many facts.

## Iterative Ontology Evolution

New documents must be compared with the existing ontology and earlier document evidence.

Entio should support these outcomes:

### Confirm

The new document supports the existing model.

### Extend

The current concept is still correct, but the document adds detail.

### Revise

The document changes the understood meaning, definition, field, relationship, domain, range, or constraint.

### Split

One concept is being used for several different meanings.

### Merge

Two concepts appear to represent the same thing.

### Conflict

Documents disagree or apply in different contexts.

### Supersede

A newer authoritative document explicitly replaces older evidence.

Entio must not assume that a newer document is automatically more authoritative.

## Document Authority and Applicability

Users should be able to describe document status and scope, such as:

- authoritative;
- supporting;
- draft;
- historical;
- superseded;
- amendment;
- applicable business area;
- applicable jurisdiction;
- effective date;
- expiration date.

These values should help Entio explain conflicts and recommendations.

They must not allow AI to change ontology content without review.

## Provenance

Every recommendation and resulting draft item must include traceable evidence.

Required provenance:

- document ID;
- filename;
- page number where available;
- section or heading where available;
- paragraph or text-block ID;
- character offsets where available;
- exact supporting excerpt;
- processing task ID;
- model ID and prompt version where AI was used;
- extraction method;
- confidence;
- evidence type;
- target ontology entity or draft item;
- user decision;
- approval history.

Provenance is workflow metadata by default.

It should not automatically become an ontology annotation unless separately approved.

## Evidence Types

Entio must clearly distinguish:

### Explicit

The document directly states the fact.

### Strongly implied

The relationship is clear from context but not stated in one sentence.

### Modeling suggestion

The AI recommends an ontology structure based on the document.

### Combined evidence

The recommendation depends on several passages or documents.

### External ontology evidence

The recommendation depends on an existing FIBO or imported concept.

### Reasoning impact

Entio's reasoner derives consequences from the proposed draft.

These must not be presented as equivalent levels of evidence.

## Conflict Handling

When documents disagree, Entio should show:

- each conflicting value or meaning;
- the supporting document and passage;
- document authority and effective date;
- affected ontology elements;
- recommended resolution options.

Entio must not choose silently.

The user may:

- select one interpretation;
- limit each interpretation to a context;
- mark one document as superseding another;
- keep the issue unresolved;
- reject both recommendations.

## Recommendation Review

The review UI should allow users to:

- inspect each recommendation;
- view all supporting evidence;
- open the original document at the relevant page where possible;
- compare local and FIBO matches;
- accept;
- reject;
- edit;
- merge duplicate recommendations;
- change the matched ontology entity;
- choose target source;
- request AI reconsideration;
- resolve ambiguity;
- group recommendations into draft batches.

Recommended review areas:

1. **Document summary**
   - AI-generated summary of the document;
   - highlighted extracted entities;
   - highlighted extracted relationships;
   - important rules, dates, amounts, and conflicts;
   - clear links from highlighted items to their recommendations and evidence;

2. **Evidence**
   - document passages and extracted facts;

3. **Recommendations**
   - reuse, create, revise, split, merge, or conflict decisions;

4. **Draft impact**
   - typed changes, semantic diff, validation, reasoning, and SHACL results.

## AI Document Summary

Each ingestion proposal should include an AI-generated document summary before the detailed recommendations.

The summary should:

- explain the document's purpose in clear language;
- identify the main extracted entities;
- identify the main extracted relationships;
- identify important business rules, dates, amounts, and obligations;
- identify likely ontology structure changes;
- identify likely individual or fact additions;
- highlight conflicts or changed meanings;
- distinguish explicit document statements from AI suggestions;
- link every highlighted entity or relationship to its evidence and related recommendation.

The summary must not introduce facts that are absent from the document or unsupported by the extracted evidence.

## Recommendation Record

Each proposal should include one document-level AI summary and a set of individual recommendations.

Each recommendation should include:

- recommendation ID;
- recommendation type;
- schema or instance category;
- proposed label or value;
- proposed definition where relevant;
- matched local entities;
- matched external entities;
- recommended action;
- confidence;
- rationale;
- evidence references;
- ambiguity flags;
- conflict references;
- dependencies;
- target source;
- review status;
- related draft item IDs.

## Duplicate Prevention

Entio should check for duplicates against:

- the applied ontology;
- imported entities;
- the current private draft;
- shared staged changes;
- the current proposal;
- other recommendations in the same task;
- earlier ingestion tasks where evidence is still available.

Duplicate detection must use canonical IRIs and typed operations, not labels alone.

## Human Control

Human confirmation is required before:

- creating a new class or property;
- changing an existing definition;
- changing hierarchy, domain, range, or constraints;
- selecting among close local or FIBO matches;
- resolving document conflicts;
- merging or splitting concepts;
- replacing earlier accepted meaning;
- submitting the final draft for proposal review.

The AI may recommend but must never approve or apply.

## Task Model

Document ingestion should run as a server-owned task.

Suggested stages:

```text
uploaded
→ extracting
→ analyzing
→ matching
→ comparing with existing evidence
→ preparing recommendations
→ awaiting review
→ building draft
→ validating
→ ready for proposal review
```

The task should support:

- progress events;
- cancellation;
- bounded retry;
- safe failure messages;
- task history during the current development session;
- duplicate-upload detection by checksum;
- no repeated provider calls for identical completed work.

## Limits

The later spec should define:

- maximum document size;
- maximum page count;
- maximum extracted text size;
- maximum OCR pages per document;
- maximum OCR processing time;
- minimum OCR confidence thresholds;
- maximum documents per task;
- maximum candidates per document;
- maximum evidence excerpts per recommendation;
- maximum excerpt length;
- maximum provider calls;
- maximum processing time;
- maximum accepted edits per task;
- maximum edits per atomic draft batch.

Recommended starting values:

- 25 MB per document;
- 500 pages per document;
- 10 documents per ingestion task;
- 2,000 candidates per document;
- 100 accepted edits per task;
- 20 edits per atomic draft batch.

These values should be confirmed with deterministic fixtures.

## Storage and Lifecycle

The spec must decide where these are stored:

- original uploaded documents;
- extracted text;
- page index;
- recommendation records;
- provenance;
- task state.

Recommended first release:

- temporary server-side document storage;
- in-memory task and recommendation state;
- cleanup on task deletion or server restart;
- minimal durable provenance for successfully applied document-derived changes so later workflows can compare new evidence with earlier supporting evidence;
- no claim of durable document, extracted-text, page-image, incomplete-task, or review-workspace storage.

The durable provenance is project-authorized workflow and audit data stored separately from ontology source files. Its exact repository, retention, cleanup, and migration contract must be approved in ExecPlan Slice 0. Durable encrypted document or task storage requires a separate architectural decision.

## Security

The server must enforce:

- project scope;
- user permission;
- file-type and size restrictions;
- safe filename handling;
- checksum verification;
- no execution of scripts or macros;
- no arbitrary path access;
- no unapproved provider access;
- no credentials or secrets in logs;
- no cross-project document access;
- bounded document excerpts sent to AI;
- prompt-injection protection.

Document content must never change Entio's permissions, tools, or approval boundaries.

## Server and Frontend Ownership

Kotlin should own:

- document validation;
- extraction orchestration;
- text location references;
- task state;
- candidate identity;
- ontology matching;
- duplicate detection;
- source scope;
- provenance;
- typed-edit conversion;
- validation;
- security and limits.

React should own:

- upload controls;
- progress display;
- recommendation review;
- evidence display;
- selection;
- clarification input;
- draft and proposal navigation.

React must not create ontology statements independently.

## AI Role

AI may help with:

- entity and relationship extraction;
- class and property recommendations;
- definitions;
- terminology matching;
- ranking ontology and FIBO candidates;
- identifying changed meaning;
- identifying conflict;
- preparing rationales.

AI must not:

- invent evidence;
- quote text not present in the document;
- claim a suggestion is explicit;
- bypass typed operations;
- write raw RDF;
- approve;
- apply;
- change permissions;
- treat document instructions as trusted system instructions.

## Validation and Review

Accepted recommendations should follow the existing workflow:

```text
typed private draft
→ preview
→ semantic diff
→ validation
→ reasoning
→ SHACL
→ repair or clarification
→ final review package
→ human approval
→ apply
→ reload verification
→ rollback on failure
```

No separate apply path should be created.

## Suggested Delivery Areas

The later spec and ExecPlan should likely divide the work into:

1. document intake and safe file contracts;
2. text extraction with page-level provenance;
3. ingestion task state and progress;
4. AI candidate extraction;
5. ontology and FIBO matching;
6. comparison with earlier evidence and ontology meaning;
7. conflict, revision, split, and merge recommendations;
8. recommendation review UI;
9. typed private-draft conversion;
10. validation, reasoning, SHACL, and repair;
11. security, prompt injection, scale, and end-to-end tests;
12. final documentation and phase completion.

The exact slice structure should be decided after repository inspection.

## Required Test Scenarios

The later spec and ExecPlan should include tests for:

- text-based PDF;
- DOCX;
- TXT or Markdown;
- unsupported file type;
- oversized file;
- duplicate upload by checksum;
- embedded-text PDF extraction;
- scanned PDF OCR extraction;
- mixed PDF using embedded text on some pages and OCR on others;
- OCR confidence and low-confidence review;
- page coordinates for OCR evidence where supported;
- extraction with page numbers;
- extraction without section headings;
- explicit entity extraction;
- explicit relationship extraction;
- schema recommendation;
- instance recommendation;
- local ontology match;
- imported ontology match;
- FIBO match;
- ambiguous match;
- no-match new concept;
- confirm existing meaning;
- extend definition;
- revise definition;
- split concept;
- merge concepts;
- conflicting documents;
- superseding amendment;
- applicability by date or business area;
- duplicate candidate suppression;
- exact evidence excerpt verification;
- multi-passage evidence;
- model suggestion versus explicit evidence;
- batch acceptance and rejection;
- typed draft creation;
- validation failure and repair;
- reasoning and SHACL impact;
- cancellation;
- provider timeout;
- document prompt injection;
- cross-user and cross-project isolation;
- no source write before approval;
- apply, reload, and rollback through existing workflows.

## Acceptance Criteria

Phase 11 is complete when:

1. Users can upload approved document types, including scanned and mixed PDFs.
2. Entio uses embedded text when available and OCR only for pages without usable embedded text.
3. Entio extracts text with reliable page-level location references and marks OCR-derived text clearly.
4. Each proposal includes an AI document summary highlighting extracted entities and relationships.
5. Entio identifies both ontology structure and business facts.
6. Every recommendation has traceable evidence.
7. Explicit facts, implied facts, and modeling suggestions are clearly separated.
8. Entio searches local, imported, and approved FIBO concepts before recommending new ones.
9. New documents can confirm, extend, revise, split, merge, or challenge existing ontology content.
10. Conflicts are shown for human resolution.
11. Users can review, edit, accept, reject, and reconsider recommendations.
12. Accepted recommendations become existing typed private-draft operations.
13. No ontology source changes before human approval.
14. Existing validation, reasoning, SHACL, proposal, apply, reload, and rollback workflows are reused.
15. Document content cannot widen permissions or tool access.
16. Processing is bounded, observable, and cancellable.
17. Provenance for successfully applied document-derived changes remains available to later workflows, including after restart, without becoming an ontology annotation.
18. Deterministic unit, integration, frontend, security, scale, and end-to-end tests pass.

## Open Questions for the Spec

The spec should resolve:

- Which document types are supported first beyond PDF, DOCX, TXT, and Markdown?
- Which OCR library or service is approved?
- What makes embedded PDF text reliable enough to avoid OCR?
- What OCR confidence level requires user review?
- How are page coordinates stored for OCR-derived evidence?
- Where are uploaded documents and extracted text stored?
- How long are documents and provenance retained?
- Which extraction libraries are approved?
- Which model performs document analysis?
- Is analysis one pass or several stages?
- What confidence levels are shown?
- Which recommendations require mandatory clarification?
- How are several documents processed together?
- How does the user mark authority, effective date, or superseded status?
- How are conflicting documents compared?
- Can one ingestion task produce several proposals?
- Should provenance later become ontology annotations?
- What exact document, page, candidate, and provider-call limits apply?
- Which FIBO modules are searched?
- Should the same document be reprocessed after ontology changes?
- What document viewer and page-opening behavior is required?

If an answer requires automatic application, raw RDF editing, OCR beyond supported PDF-page extraction, production-grade document storage, or another major architectural expansion, it should be deferred rather than silently added to Phase 11.
