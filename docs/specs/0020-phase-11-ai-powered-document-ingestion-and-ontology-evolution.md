# Phase 11: AI-Powered Document Ingestion And Ontology Evolution

## Status

Approved on 2026-07-24.

## Related Documents

- [Phase 11 scope](../architecture/phase-11-scope.md)
- [Product principles](../architecture/000-product-principles.md)
- [Kotlin engine guidelines](../architecture/003-kotlin-engine-guidelines.md)

## Problem

Entio can already load, inspect, validate, reason over, and safely change an ontology, but users must enter every proposed change themselves. Business meaning is often recorded first in policies, contracts, standards, procedures, and other documents. Turning those documents into ontology structure and business facts is slow, and the connection between a change and its supporting passage is easy to lose.

Phase 11 adds a controlled document-ingestion workflow. Entio extracts located text, uses AI to identify possible meaning, compares that meaning with the current ontology and approved FIBO content, and presents evidence-linked recommendations. A reviewer decides what to accept. Accepted items become the same typed private-draft operations Entio already validates and sends through human proposal review.

The current codebase already includes a native OpenAI-backed ontology assistant for project conversations, ontology-aware answers, structured review-only proposals, deterministic validation, and staging. Phase 11 extends its server-side credential, verified-model, provider, context, and review foundations; it does not create or restore the assistant.

The feature must also handle later documents. New evidence can support existing meaning, add detail, suggest a revision, expose a split or duplicate, conflict with older evidence, or explicitly supersede it. Entio must show these outcomes without deciding them silently.

## Goals

Phase 11 will:

- accept PDF, DOCX, TXT, and Markdown documents through the web application;
- support embedded-text, scanned, and mixed PDFs;
- preserve page and text-block locations for extracted evidence;
- clearly mark OCR-derived and low-confidence text;
- create an evidence-linked document summary and candidate recommendations;
- separate ontology-structure recommendations from business-fact recommendations;
- search the local ontology, imports, the current draft and proposal state, retained ingestion evidence, and approved FIBO content before suggesting a new entity;
- describe whether evidence confirms, extends, revises, splits, merges, conflicts with, or supersedes earlier meaning;
- let users review, edit, accept, reject, merge, rematch, clarify, and reconsider recommendations;
- convert accepted recommendations only into supported typed private-draft operations;
- reuse existing preview, diff, validation, reasoning, SHACL, proposal, approval, apply, reload, and rollback behavior;
- keep document processing project-scoped, bounded, observable, cancellable, and safe.

## Non-Goals

Phase 11 does not include:

- automatic approval or application;
- direct RDF, Turtle, SPARQL, ontology-source, or SHACL-source editing;
- a raw-triple fallback when no typed operation exists;
- handwritten-document OCR or standalone image ingestion;
- spreadsheets, email archives, web pages, or general document crawling;
- a document management system or production-grade durable document store;
- durable uploads, extracted text, page images, incomplete task state, or review workspaces across server restarts;
- automatic conflict, split, merge, or supersession decisions;
- training or fine-tuning models on uploaded documents;
- embeddings, an unbounded vector index, or external ontology search beyond the pinned approved FIBO catalog;
- provenance written as ontology annotations;
- CLI or VS Code ingestion surfaces;
- browser-owned semantic matching, duplicate detection, or ontology-statement construction.
- replacement or removal of the active native ontology assistant.

The current phase-level boundaries and historical non-goals remain governed by `AGENTS.md`.

## Proposed Behavior

### 1. Start An Ingestion Task

An authorized project user opens the web ingestion workspace, selects between one and ten documents, supplies document metadata, and starts a task.

Supported media are:

- PDF: `application/pdf`;
- DOCX: `application/vnd.openxmlformats-officedocument.wordprocessingml.document`;
- plain text: `text/plain`;
- Markdown: `text/markdown` or a `.md` file validated as UTF-8 text.

File extension, detected content, and declared media type must agree. DOCX ZIP entries are treated as data: macros, external relationships, scripts, and embedded executables are not run. Filenames are display metadata only and never form a server path.

Each document receives a server-issued ID and records its safe display filename, detected media type, byte size, SHA-256 checksum, project, uploader, upload time, authority metadata, and status. The server returns the task ID after intake and before extraction or provider work.

Document metadata supports:

- status: `Authoritative`, `Supporting`, `Draft`, `Historical`, `Superseded`, or `Amendment`;
- optional business area and jurisdiction;
- optional effective and expiration dates;
- an optional reference to the document this one amends or supersedes.

Metadata informs comparison and explanation. Recency alone never establishes authority.

### 2. Temporary Storage And Lifecycle

Original files, rendered PDF page images, and extracted-text artifacts are stored under a server-created task directory outside project ontology sources. The server generates every path and never accepts a client path.

Task state, summaries, recommendations, decisions, and exact-work cache keys remain in memory. Temporary files are deleted when:

- the user deletes the task;
- intake fails;
- a task is cancelled and cleanup completes; or
- the server shuts down normally.

The server also attempts startup cleanup of stale Entio-owned task directories. A restart ends every incomplete task and removes access to its in-memory evidence, recommendations, and decisions.

Successfully applied document-derived changes are different. Entio retains a minimal durable provenance record so later ingestion workflows can compare new evidence with the evidence behind current ontology content. This repository is isolated from ontology source files and does not retain original uploads, page images, or complete extracted documents by default. Its exact schema, authorization, retention, cleanup, and migration behavior must be approved in Slice 0. Production document retention and a general durable workflow store remain out of scope.

### 3. Extract Located Text

Extraction is deterministic for a given file and extractor version.

For each PDF page:

1. Extract embedded text with Apache PDFBox.
2. Treat it as usable when it contains at least 30 non-whitespace characters, at least 60 percent letter/number characters among non-whitespace characters, no repeated replacement-character pattern, and at least one text line with three or more words.
3. Use the embedded text when usable.
4. Otherwise render only that page and run local Tesseract OCR through a fixed Kotlin adapter.

These rules are deliberately conservative and fixture-tested. Users may view an extraction warning, but the browser cannot force OCR or select an arbitrary executable.

DOCX extraction uses Apache POI and preserves paragraphs, headings, and table cells in reading order. TXT and Markdown are decoded as UTF-8; invalid UTF-8 is rejected. Markdown headings are location metadata, not executable content, and Markdown is not rendered as trusted HTML.

Every extracted block records:

- document ID and safe filename;
- one-based page number for PDF, when applicable;
- section or heading when available;
- stable block ID;
- start and end character offsets in the normalized document text;
- exact normalized text;
- extraction method and extractor version.

OCR blocks additionally record:

- average confidence from 0 to 100;
- a server-owned page-image reference;
- word or line rectangles where Tesseract provides them;
- page width and height;
- normalized rectangle coordinates from 0 to 1;
- an `OCR-derived` marker.

OCR confidence below 80 is visibly low confidence. Evidence below 60 cannot support an accepted recommendation until a reviewer confirms or corrects the excerpt. Entio never fills unreadable words.

### 4. Analyze In Bounded Stages

Analysis uses the current user's configured, verified, explicitly selected provider model. The selected model must support the structured response contract approved by Entio. There is no silent fallback to another model. If no eligible model is configured, extraction may complete but analysis remains blocked with a safe settings prompt.

Analysis has separate bounded stages:

1. identify candidate entities, relationships, values, rules, ambiguity, and conflicts from located text;
2. verify each claimed excerpt exactly against extracted text;
3. match verified candidates against Entio-controlled ontology and FIBO results;
4. compare candidates with current ontology meaning and retained evidence;
5. prepare the document summary and recommendations.

For a multi-document task, Entio extracts and analyzes each document independently before comparing their verified candidates. The provider never receives every document as one unbounded prompt.

The provider receives bounded text blocks and opaque identifiers, not filesystem paths, credentials, permissions, tools, or raw project configuration. System instructions state that document text is untrusted data. Text that asks the model to ignore rules, reveal secrets, call tools, or change permissions remains ordinary document content.

Provider output is never accepted as evidence by itself. Kotlin must:

- resolve every evidence reference to an extracted block;
- reproduce the exact excerpt from server-held text using validated offsets;
- reject invented or altered quotes;
- reject unsupported recommendation types and malformed identifiers;
- perform local, import, draft, proposal, and FIBO matching through existing semantic services;
- assign stable candidate and recommendation identities;
- enforce limits and deterministic ordering.

### 5. Produce A Document Summary

Each successfully analyzed document has one review-only summary that:

- explains the document's purpose;
- highlights main entities and relationships;
- lists important rules, dates, amounts, and obligations;
- separates likely ontology-structure changes from likely business facts;
- calls out changed meaning, ambiguity, and conflict;
- labels explicit evidence, strongly implied meaning, and modeling suggestions differently;
- links every highlighted item to evidence and related recommendations.

A sentence without valid supporting evidence is omitted. The summary never becomes ontology content automatically.

### 6. Create Recommendations

Recommendations are divided into `OntologyStructure` and `BusinessFact` sections. Each record includes:

- server-issued recommendation ID and type;
- proposed label, value, or definition;
- recommended action;
- confidence band and rationale;
- evidence references and evidence types;
- local, imported, and FIBO match candidates;
- ambiguity, conflict, and dependency references;
- target source when relevant;
- review status;
- related typed-draft item IDs;
- provider model and prompt version for AI-assisted fields.

Supported actions are:

- `Confirm`;
- `ReuseLocal`;
- `ReuseImportedOrFibo`;
- `Extend`;
- `Revise`;
- `CreateLocal`;
- `Split`;
- `Merge`;
- `Conflict`;
- `Supersede`;
- `InsufficientEvidence`;
- `Unsupported`.

Supported evidence types are:

- `Explicit`;
- `StronglyImplied`;
- `ModelingSuggestion`;
- `CombinedEvidence`;
- `ExternalOntologyEvidence`;
- `ReasoningImpact`.

The UI must not present these evidence types as equal. Only exact document passages are quoted. External ontology and reasoning evidence link to their own Entio records rather than pretending to be document text.

Confidence is shown as:

- `High`: 80–100;
- `Medium`: 60–79;
- `Low`: 0–59.

Confidence helps review; it never grants approval. Mandatory clarification is required for low-confidence OCR, ambiguous close matches, conflicts, split or merge actions, supersession, replacement of existing meaning, and any recommendation whose typed target or writable source is unresolved.

Recommendation outcomes have these additional rules:

- `Confirm` records supporting provenance and creates no ontology edit;
- reuse may create supported instance assertions but must not create a duplicate schema entity;
- extend and revise create typed edits only for changed fields or relationships;
- split, merge, conflict, and supersede require explicit human decisions;
- a split or merge remains review-only when it would require bulk reference migration, destructive deletion, unsupported equivalence or dependency semantics, or raw RDF.

### 7. Match And Prevent Duplicates

For every candidate, Kotlin searches in this order:

1. the applied local ontology;
2. imported project ontologies;
3. the user's current private draft and shared staged changes;
4. the current proposal;
5. other recommendations in the same task;
6. durable provenance from earlier successfully applied document-ingestion workflows;
7. the pinned approved FIBO catalog, limited to its existing curated modules.

Search results may use labels and definitions for ranking, but duplicate decisions use canonical IRIs and normalized typed operations. A new local entity cannot be recommended until the bounded searches complete. Close matches remain an explicit reviewer choice.

An identical upload is detected by project ID plus SHA-256 checksum. Entio does not repeat extraction or provider calls when the checksum, ontology fingerprint, selected model, prompt version, extractor version, and document metadata are identical to completed work retained in the current session. The user may reprocess the same document after any of those inputs changes.

### 8. Compare New And Earlier Evidence

Entio compares new candidates with the current ontology and retained evidence from earlier completed workflows, including workflows completed before a server restart. It may recommend confirm, extend, revise, split, merge, conflict, or supersede.

Conflict records show each interpretation, its passages, document status, applicability, dates, and affected ontology elements. Entio may offer resolution choices but cannot select one. Supersession requires explicit document metadata or evidence and reviewer confirmation; a newer date is not sufficient.

Several documents in one task are extracted independently and then compared together. The task produces one review workspace and one private draft. Accepted operations may be divided into atomic draft batches, but they enter one existing proposal-review package for that task.

### 9. Cross-Workflow Provenance

For every successfully applied document-derived change, Entio durably retains enough workflow provenance to show:

- the ontology entity or assertion that was created or changed;
- the document identity, checksum, safe filename, and exact supporting excerpt;
- the page, block, offsets, and extraction method where available;
- the recommendation and human decision that produced the change;
- the model, prompt version, confidence, and evidence type;
- the apply event and resulting ontology fingerprint.

This record supports later comparison, including `Confirm` outcomes that add evidence without creating no-op ontology edits. It is project-authorized workflow and audit data, not an ontology annotation. Temporary task deletion does not remove provenance already attached to a successful apply.

### 10. Review Recommendations

The web workspace contains:

- document summary;
- evidence viewer;
- ontology-structure recommendations;
- business-fact recommendations;
- draft impact.

The user can inspect, filter, accept, reject, edit, merge duplicate recommendations, change a match, choose a writable source, enter clarification, request bounded reconsideration, and group accepted items into batches.

For PDF evidence, the viewer opens the retained page image or PDF page and highlights coordinates when available. For embedded PDF text without coordinates, it opens the page and highlights the exact excerpt in the extracted-text panel. DOCX, TXT, and Markdown open a safe extracted-text view at the relevant block. No active document content runs in the browser.

Reconsideration sends the user's clarification, the original bounded evidence, and current Entio-owned match choices through the same provider boundary. It does not widen document access or recommendation types.

### 11. Build A Typed Private Draft

Only accepted, unambiguous recommendations can be converted. Kotlin maps them to existing typed operations for supported:

- classes, object properties, datatype properties, and individuals;
- labels, alternate labels, and definitions;
- superclass, type, domain, and range relationships;
- object-property assertions and datatype values;
- approved SHACL constraint edits;
- approved FIBO reuse.

Unsupported output stays a recommendation and cannot fall back to raw RDF. Before conversion, the server rechecks evidence, project and user scope, graph fingerprint, duplicates, target source, dependencies, limits, and current recommendation decisions.

Up to 100 accepted edits are allowed per task. One atomic draft batch contains at most 20 edits. A task may therefore build up to five ordered batches. A failed item prevents its batch from entering the private draft; earlier valid batches remain reviewable and can be removed through the normal draft workflow.

The existing flow remains:

```text
typed private draft
→ preview
→ semantic diff
→ deterministic validation
→ reasoning and SHACL impact
→ repair or clarification
→ proposal review
→ human approval
→ atomic apply
→ reload verification
→ rollback on failure
```

No document-ingestion route writes ontology sources.

On successful apply, the server commits the approved provenance records to the durable provenance repository and links them to the resulting entities or assertions and apply fingerprint. If that provenance commit cannot satisfy the Slice 0 atomicity and recovery contract, the apply must not be reported as a successful document-derived workflow.

### 12. Language, Encryption, OCR Artifacts, And Concurrency

Phase 11 initially supports English-language documents. Non-English or mixed-language documents are rejected or marked unsupported unless Slice 0 approves language detection, model behavior, OCR language packs, extraction, and matching for additional languages.

Password-protected or encrypted PDF and DOCX files are rejected. Entio never accepts or retains document passwords.

Rendered OCR page images are temporary extraction artifacts. Slice 0 must pin rendering DPI, page dimensions, total rendered-image bytes, evidence-view lifetime, and cleanup timing. Images are deleted when the task is deleted, expires, or the server restarts.

Slice 0 must also pin bounded concurrency. The default is:

- one active ingestion execution per project;
- a fixed server-wide maximum of active ingestion tasks;
- one provider request at a time per task;
- one OCR page operation at a time per task unless a safe bounded alternative is approved;
- no new work after cancellation;
- no continued work for deleted or superseded tasks.

## Inputs

### User Inputs

- one to ten supported documents;
- document authority and applicability metadata;
- selected project;
- recommendation review decisions;
- corrections and clarification;
- local/imported/FIBO match selection;
- writable target source;
- draft batch grouping;
- explicit submission to existing proposal review.

### Existing Entio Inputs

- applied project graph and fingerprint;
- import closure and writable source information;
- private draft, shared staging, and proposal state;
- semantic descriptors and deterministic search;
- pinned curated FIBO catalog;
- durable provenance from earlier successfully applied ingestion workflows;
- selected and verified provider model;
- active server-side assistant credential and OpenAI provider boundaries;
- validation, reasoning, and SHACL services.

## Outputs

- ingestion task status and progress events;
- safe document metadata and checksum;
- located extracted-text blocks and extraction warnings;
- OCR confidence and coordinates where available;
- one evidence-linked summary per document;
- schema and instance recommendations;
- match, ambiguity, duplicate, conflict, and provenance records;
- explicit reviewer decision history;
- durable supporting provenance for successfully applied document-derived changes;
- supported typed private-draft batches;
- existing preview, diff, validation, reasoning, SHACL, and proposal-review results.

## Task State And Progress

The server owns these states:

```text
Uploaded
→ Extracting
→ Analyzing
→ Matching
→ Comparing
→ PreparingRecommendations
→ AwaitingReview
→ BuildingDraft
→ Validating
→ ReadyForProposalReview
```

Terminal states are `Completed`, `Cancelled`, and `Failed`. A task may also be `BlockedForModel`, `BlockedForClarification`, or `BlockedForLimits`.

Progress events contain safe stage, document counts, bounded percentages, and user-readable messages. They contain no credentials, raw provider payloads, server paths, or document excerpts. Cancellation stops pending provider work, prevents new draft conversion, and performs temporary-file cleanup. Provider retry is limited to two retries for transient failures and never retries validation or authorization failures.

## Limits

Phase 11 uses these initial limits:

| Limit | Value |
| --- | ---: |
| File size | 25 MB per document |
| PDF pages | 500 per document |
| Documents | 10 per task |
| Normalized extracted text | 5,000,000 characters per document |
| OCR pages | 200 per document |
| OCR wall time | 10 minutes per document |
| Candidates | 2,000 per document |
| Evidence excerpts | 8 per recommendation |
| Excerpt length | 500 characters |
| Provider calls | 20 per task, including retries and reconsideration |
| Task wall time | 30 minutes before awaiting review |
| Accepted edits | 100 per task |
| Atomic draft batch | 20 edits |

Extraction and analysis stop safely when a limit is reached. Partial output is labeled incomplete and cannot silently become a complete proposal. The contract audit may lower a limit when deterministic scale fixtures show the initial value is unsafe; raising a limit requires updating this spec.

## Validation Behavior

Entio validates at four boundaries.

### Intake Validation

- authorization and project scope;
- document count, size, type, content signature, and checksum;
- safe display filename and metadata;
- ZIP-entry and compression bounds for DOCX;
- page and extracted-text limits.

### Evidence Validation

- document, page, block, and offset references exist;
- exact excerpts match server-held extracted text;
- OCR confidence gates are respected;
- evidence type is allowed;
- combined evidence references at least two valid records;
- provider and prompt provenance is recorded for AI-assisted content.

### Recommendation Validation

- identifiers, actions, categories, and dependencies are valid;
- schema and instance recommendations remain separate;
- canonical matches resolve in the authorized project/FIBO scope;
- new-entity recommendations have completed reuse search;
- ambiguity, conflict, split, merge, and supersession gates require clarification;
- duplicates are suppressed by canonical IRI and typed-operation identity.

### Draft Validation

- the graph fingerprint and evidence are current;
- the reviewer is authorized;
- every operation is supported and target source is writable;
- batch and task limits hold;
- existing preview, semantic diff, validation, reasoning, and SHACL checks run;
- successful apply can atomically retain or recover the required durable provenance record;
- normal stale-proposal, approval, apply, reload, and rollback checks remain unchanged.

## Error Behavior

Errors are safe, specific, and recoverable where possible.

- Unsupported, unsafe, oversized, malformed, or encrypted files are rejected before analysis.
- Password-protected PDFs and DOCX files are unsupported in Phase 11.
- A page extraction failure identifies the page and prevents a complete proposal.
- Low-confidence OCR requires correction or confirmation; unreadable text is never guessed.
- Missing provider settings block analysis but preserve completed extraction.
- Provider timeout or malformed output records a safe task error without exposing provider payloads or credentials.
- Invented or mismatched excerpts invalidate the affected recommendation.
- Stale ontology, draft, proposal, model, prompt, extractor, or metadata inputs require recomparison before draft conversion.
- Cancellation leaves ontology and existing staging unchanged.
- Cross-user or cross-project requests return the existing authorization error shape without confirming that another task exists.
- Temporary-storage failure stops intake or processing; Entio does not fall back to project directories.
- A durable-provenance failure prevents the workflow from being reported as successfully applied and follows the Slice 0 recovery contract.
- Validation, reasoning, or SHACL failure returns the user to review and repair. It never applies a partial ontology change.

## Security And Privacy

- Documents are untrusted input at every stage.
- Only authorized project users may create or read tasks.
- Provider credentials stay server-side and are never included in prompts, logs, or responses.
- Provider requests contain only the bounded excerpts needed for the current stage.
- Fixed provider endpoints and the selected verified model are used; document text cannot choose tools, URLs, models, or permissions.
- Parsers do not run macros, scripts, external links, or embedded programs.
- Archive expansion, images, pages, text, provider calls, and processing time are bounded.
- Logs use IDs and counts, not document bodies or exact evidence excerpts.
- Temporary files use server-generated names and project/task authorization.
- Durable provenance is project-authorized, excludes credentials and server paths, and remains separate from ontology source files.
- HTML and Markdown from documents are escaped and rendered as text.
- Deleting or restarting removes temporary task access but not provenance already retained for a successful apply; no claim is made about provider-side retention beyond the configured provider's policy.

## Test Cases

### Intake And Extraction

- accept a text-based PDF, DOCX, TXT, and Markdown file;
- reject unsupported type, type/signature mismatch, encrypted file, unsafe DOCX, oversized file, too many documents, and excessive pages;
- detect duplicate uploads by checksum and exact-work key;
- extract embedded PDF text without OCR;
- OCR an image-only PDF page;
- combine embedded and OCR pages in order for a mixed PDF;
- mark extraction method, page, block, offsets, confidence, and coordinates;
- flag OCR below 80 and require confirmation below 60;
- handle documents with and without headings;
- cancel during extraction and clean temporary files.

### Analysis And Evidence

- extract explicit entities and relationships;
- produce schema and instance recommendations separately;
- identify dates, amounts, obligations, values, and possible constraints;
- verify every excerpt exactly against extracted text;
- reject invented, altered, out-of-range, or cross-document excerpts;
- support multi-passage and multi-document evidence;
- distinguish explicit, implied, modeling, external, and reasoning evidence;
- omit unsupported summary claims;
- resist prompt-injection instructions inside a document;
- handle provider timeout, retry bound, malformed output, and cancellation.

### Matching And Evolution

- match local, imported, and FIBO entities;
- require review for ambiguous close matches;
- recommend a new concept only after bounded reuse search;
- confirm, extend, revise, split, merge, conflict, and supersede;
- respect authority, effective dates, expiration, jurisdiction, and business area;
- never infer supersession from recency alone;
- suppress duplicate candidates and typed operations across all defined scopes;
- record `Confirm` as supporting provenance without a no-op ontology edit;
- keep unsupported split or merge migrations review-only;
- compare a later workflow with durable provenance retained before a restart;
- allow reprocessing when ontology fingerprint, metadata, extractor, prompt, or model changes.

### Review And Draft

- show evidence-linked summaries and recommendations;
- open a PDF page or extracted-text block safely;
- accept, reject, edit, merge, rematch, clarify, and reconsider;
- require clarification for every mandatory case;
- build supported typed edits in batches of at most 20 and tasks of at most 100;
- reject unsupported raw-RDF conversion;
- preserve provenance and reviewer decisions through proposal review;
- retain approved provenance after successful apply without writing it into ontology source files;
- show validation failure and repair, reasoning impact, and SHACL impact;
- prove no source write occurs before existing human approval;
- apply, reload, and roll back only through existing workflows.

### Isolation, Scale, And Lifecycle

- enforce user and project isolation for documents, excerpts, tasks, and progress;
- avoid secrets, paths, and excerpts in logs and progress events;
- enforce every file, page, text, OCR, candidate, evidence, call, time, and edit limit;
- avoid repeat provider calls for identical completed work;
- remove temporary artifacts on deletion, cancellation, and restart cleanup;
- retain applied-change provenance across restart and authorize it by project;
- pass deterministic unit, integration, frontend, security, scale, and end-to-end fixtures.

## Acceptance Criteria

Phase 11 is complete when:

1. Authorized web users can ingest PDF, DOCX, TXT, and Markdown documents within the defined bounds.
2. Embedded PDF text is preferred page by page, while image-only pages use bounded local OCR.
3. Extracted evidence retains reliable locations and clearly identifies OCR and low confidence.
4. Each analyzed document has an evidence-linked summary covering its main entities and relationships.
5. Ontology structure and business facts are extracted and reviewed separately.
6. Every recommendation has validated provenance and exact supporting evidence where document evidence is claimed.
7. Explicit, implied, modeling, combined, external, and reasoning evidence are visibly distinct.
8. Entio searches local, imported, draft/proposal, durable prior provenance, and curated FIBO content before suggesting creation.
9. New evidence can produce confirm, extend, revise, split, merge, conflict, or supersede recommendations without automatic decisions.
10. Users can inspect, edit, accept, reject, merge, rematch, clarify, and reconsider recommendations.
11. Accepted recommendations convert only to supported typed private-draft operations under current graph and evidence checks.
12. No document route writes ontology or SHACL sources, and no change is applied before existing human approval.
13. Existing semantic diff, validation, reasoning, SHACL, proposal, apply, reload, and rollback paths are reused.
14. Documents cannot widen permissions, choose tools or endpoints, expose credentials, or cross project boundaries.
15. Successfully applied document-derived changes retain project-authorized provenance across later workflows and server restarts without writing it into ontology sources.
16. Tasks are bounded, observable, cancellable, retry-limited, and cleaned up according to the temporary lifecycle.
17. English-language and encryption boundaries, OCR artifact limits, and concurrency limits are enforced.
18. All required deterministic backend, frontend, security, scale, and end-to-end tests pass.

## Resolved Product Decisions

- Initial formats are PDF, DOCX, TXT, and Markdown only.
- PDF extraction uses PDFBox; DOCX uses Apache POI; OCR uses a fixed local Tesseract adapter.
- Embedded PDF text uses the deterministic reliability test in this spec.
- OCR below 80 is low confidence; below 60 requires confirmation or correction.
- OCR coordinates store page dimensions and normalized rectangles.
- Files, extraction artifacts, incomplete task state, and review workspaces are temporary.
- Provenance for successfully applied document-derived changes is retained durably and separately from ontology source files.
- Temporary task retention lasts only for the current server session or until task deletion/cancellation.
- Analysis is multi-stage, not one unrestricted prompt.
- Each document is analyzed independently before bounded cross-document comparison.
- The current user's verified selected compatible model performs analysis; no model ID is hardcoded.
- Mandatory clarification cases are explicitly listed in this spec.
- Multi-document tasks compare documents together and produce one review workspace and private draft.
- Authority and applicability are user-supplied metadata and evidence, not AI-granted status.
- One task produces one existing proposal-review package, optionally built from several typed draft batches.
- `Confirm` adds supporting provenance without creating an ontology edit.
- Unsupported split or merge migrations remain review-only.
- Phase 11 initially supports English and rejects encrypted documents.
- Provenance remains workflow metadata rather than an ontology annotation.
- Only the existing pinned curated FIBO catalog is searched.
- Exact identical work is reused; changed ontology, metadata, extractor, prompt, or model permits reprocessing.
- PDF pages open in a safe viewer; other formats use the safe extracted-text viewer.

## Open Questions

These questions must be closed during Slice 0 before implementation:

- Do PDFBox, Apache POI, Tess4J/Tesseract, and their transitive licenses satisfy the repository's approved dependency and distribution policy?
- Is a bundled Tesseract runtime allowed for development and deployment, or must the server require an administrator-provided fixed binary/data installation?
- Which already-supported selected models pass the structured-output and context-bound fixtures for this workflow?
- Which existing typed SHACL operations are safe to expose from ingestion without expanding Phase 4's supported constraint set?
- Can the current in-memory identity boundary express the exact upload, review, and proposal permissions additively, or must Phase 11 remain development-user scoped?
- Which existing document-viewer component can safely render retained PDFs, or is a minimal browser-native PDF page view required?
- What minimal durable provenance repository schema, retention, authorization, migration, atomicity, and recovery contract will be used?
- What OCR rendering DPI, page dimensions, total-image-byte limit, and evidence-view lifetime are safe?
- What server-wide ingestion concurrency limit is safe?
- Does English-only intake use deterministic language detection or an explicit user declaration plus validation?

If any answer requires production persistence, arbitrary process execution, a new apply path, raw RDF, unbounded indexing, or expanded ontology semantics, that part must be deferred or the spec must be revised and reapproved.

## Boundary Check

- The feature is explicitly authorized by the Phase 11 scope.
- It extends the active native ontology assistant without adding autonomous tools, unrestricted editing, direct source writes, approval authority, or automatic apply.
- It uses existing RDF, OWL, SHACL, proposal, and FIBO services instead of reimplementing semantic standards.
- Kotlin owns extraction validation, evidence, semantic matching, typed conversion, security, and limits.
- Ktor owns authorized task orchestration, temporary workflow state, and the minimal durable applied-change provenance repository.
- React owns upload, progress, evidence, review, and navigation only.
- No new Gradle module, general database, queue, durable document/task store, CLI command, or VS Code command is required; the narrowly scoped durable provenance repository is the approved exception.
- The parsing and OCR dependencies are narrow implementation dependencies that require Slice 0 approval, not speculative product infrastructure.
