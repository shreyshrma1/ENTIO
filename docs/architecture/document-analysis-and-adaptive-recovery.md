# Document Analysis And Adaptive Recovery

## Purpose

Entio reads uploaded business documents and prepares evidence-linked ontology
changes for human review. The Phase 12 production path separates four
responsibilities:

- local Kotlin services extract stable candidates and retrieve authorized
  ontology choices;
- the selected model interprets business meaning from bounded evidence and
  those choices;
- Kotlin verifies the result and compiles supported meaning;
- a person edits, accepts, rejects, approves, or applies the result.

The model cannot browse the ontology, call tools, choose arbitrary existing
IRIs, approve changes, write RDF, change ontology files, or bypass the existing
proposal workflow.

## Current Production Flow

New document-ingestion tasks use this path by default:

```text
upload and intake validation
→ text extraction and bounded OCR
→ deterministic local candidate extraction
→ deterministic retrieval from authorized ontology scopes
→ frozen grounded-analysis work key
→ bounded grounded model interpretation
→ Kotlin evidence, selection, structure, duplicate, and freshness verification
→ existing deterministic semantic compilation and change-set verification
→ connected editable human review
→ existing draft, proposal, approval, apply, reload, rollback, and provenance workflow
```

The Phase 11.5+ discovery, connected-modeling, consolidation, and prerequisite
provider sequence remains available only through explicit compatibility
configuration and historical tests. It is not the default Phase 12 path and is
not an automatic fallback after a grounded-analysis failure.

## Process In Detail

### 1. Upload and intake validation

The web application accepts a bounded set of English PDF, DOCX, TXT, and
Markdown documents for an authorized project. Intake checks file type, size,
count, duplicate content, and task ownership before analysis starts.

Uploaded files and incomplete tasks are temporary. They are not added to the
ontology or retained as permanent project history.

### 2. Located text extraction

Kotlin extracts ordered text blocks from each document. PDF pages with
unreliable embedded text may use bounded local OCR.

Every block retains its document, page or section location, extraction method,
and exact text. Later candidate and model evidence must resolve back to these
blocks and offsets.

### 3. Deterministic local candidate extraction

`DocumentCandidateExtractionService` analyzes the verified English text before
any document semantic model call. It uses pinned Apache OpenNLP `2.5.11` with
English sentence-detector, tokenizer, part-of-speech, and lemmatizer resources
`1.3.0`, plus bounded product-specific patterns.

The extractor can retain:

- concept terms;
- people, organizations, locations, and identifiers;
- relationship phrases;
- attribute values, dates, and monetary amounts;
- rule cues;
- administrative and illustrative spans.

Each candidate contains a stable ID, category, normalized text, document
checksum, exact evidence offsets, extractor contract version, and NLP resource
version. Kotlin deduplicates exact candidate identities and orders the complete
inventory deterministically.

This stage does not decide whether a candidate is an ontology class, property,
individual, assertion, or rule. It prepares evidence-linked search input.

### 4. Deterministic authorized retrieval

`DocumentRetrievalContextFactory` assembles an authorized snapshot, and
`DocumentOntologyRetrievalService` searches every applicable scope:

1. applied local ontology entities;
2. imported ontology entities;
3. private draft work;
4. shared staging;
5. the current proposal;
6. same-task records;
7. retained applied-document provenance;
8. the pinned read-only FIBO catalog.

Retrieval reuses current semantic descriptions, explicit current-work and
provenance records, same-task records, and `FiboSchemaSearchService`. It does not
copy the ontology, build embeddings, maintain a vector store, or call an
external search service.

Ranking uses exact normalized identity, preferred and alternate labels, IRI
local names, bounded token overlap, kind compatibility, nearby candidate hints,
approved scope order, canonical IRI, and source ID. A result includes a stable
server-issued selection ID, canonical IRI, kind, scope, source, writability,
bounded definition and labels, structural context, match reasons, score, and
the frozen ontology/current-work/provenance/catalog fingerprints.

At most 20 ranked choices are sent to the model for one candidate. Kotlin keeps
complete authorized-state matches separately for duplicate, collision, and
no-op checks, so the prompt bound does not weaken final verification. A
candidate with no retrieved choices is still valid input.

### 5. Frozen grounded-analysis work key

Before calling the model, Kotlin re-creates the retrieval context and rejects a
fingerprint change as stale. It then hashes the project and task identity,
document checksums, evidence and candidate inventories, retrieval results,
ontology/current-work/provenance/catalog fingerprints, extractor and resource
versions, retrieval ranking, selected verified model, and grounded prompt and
response versions.

This work key makes equivalent frozen inputs repeatable and invalidates model
selections when any relevant input changes.

### 6. Bounded grounded model interpretation

The selected verified model receives compact candidates, exact evidence, and
their authorized choices in one strict no-tools request. It returns connected
semantic items and complete candidate coverage using these dispositions:

- `ReuseExisting` with an exact supplied selection ID;
- `ExtendExisting` with an exact supplied writable selection ID;
- `ProposeNew` when no supplied choice represents the meaning;
- `Unresolved` when the evidence or choices do not support a safe decision;
- explicit administrative or illustrative treatment for non-ontology content.

The response can describe classes, properties, hierarchy, domains, ranges,
assertions, values, constraints, references, confidence, and ambiguity. It
cannot return final entity IRIs, raw RDF, Turtle, SPARQL, Entio operations,
credentials, paths, approval, or write instructions.

Model output remains judgment and can vary. Retrieval narrows the choices but
does not prove semantic identity.

### 7. Kotlin verification and compilation

`DocumentGroundedAnalysisVerifier` checks:

- candidate and evidence identity;
- complete candidate coverage;
- supplied selection IDs and their exact IRI, kind, scope, source, and
  fingerprints;
- extension writability;
- connected references and compatible kinds;
- domains, ranges, datatypes, types, and reviewer-solvable prerequisites;
- full-state duplicates, collisions, no-ops, and stale work;
- supported, review-only, unresolved, and blocked outcomes.

Kotlin never substitutes a different retrieved entity to make a model choice
compile. Reviewer-solvable gaps become editable `NeedsInput` fields.
Unsupported complex meaning remains visible and non-executable. Unsafe work is
blocked with a stable reason.

The verified connected plan then enters the existing
`DocumentSemanticPlanCompiler` and `DocumentChangeSetPlanVerifier`. Kotlin owns
temporary-reference resolution, collision-checked IRI generation, writable
source selection, dependency ordering, expanded edit counting, typed-operation
construction, and final freshness checks. No second compiler or raw-RDF path
exists.

### 8. Connected editable review and apply

The review workspace initially shows a compact collapsed summary with the name,
edit type, confidence, status, and expansion control. Expanded review shows:

- grounded disposition and selected or alternative server-issued choices;
- canonical IRI, source, kind, match reasons, and structural context;
- exact typed changes and generated IRIs;
- evidence and confidence dimensions;
- model-recommended and Kotlin-supplied prerequisite origins;
- editable labels, kinds, sources, domains, ranges, datatypes, types, and
  prerequisite fields;
- retained review-only meaning, ambiguity, blockers, and individual gates.

Reviewer edits are reverified and recompiled in Kotlin and return the
recommendation to pending review. Accepted recommendations are translated into
dependency-safe typed draft batches of at most 20 expanded edits. The existing
private-draft, proposal, validation, human approval, atomic apply, reload,
rollback, and applied-provenance workflow remains the only way a document
change reaches ontology or SHACL sources.

## Counts

Phase 12 reports different counts for different stages:

- evidence blocks;
- retained and rejected NLP candidates;
- retained, unresolved, and rejected grounded items;
- executable, needs-input, review-only, and blocked recommendations;
- expanded typed edits.

A candidate is search input, a grounded item is modeled meaning, a
recommendation is one connected review bundle, and a typed edit is one concrete
operation. These counts are not interchangeable. There is no task-wide product
ceiling that silently discards valid modeled items, recommendations, or edits;
resource and atomic-batch safeguards remain bounded.

## Bounds And Adaptive Recovery

The existing document, byte, page, extracted-text, OCR, concurrency, task-life,
provider-response, and timeout bounds remain in force. Phase 12 adds these
grounded limits:

- at most 20 prompt-visible retrieval choices per candidate;
- at most 40 candidates in one grounded request group;
- at most 15 grounded logical calls per task;
- at most 20 grounded provider attempts per task;
- at most one exact-input retry for one retryable grounded call;
- at most 20 expanded typed edits in one final recommendation or staging batch.

If a grounded request reaches an approved response/output limit or the provider
is temporarily unavailable, Entio can split only that candidate group into two
balanced groups while preserving the same frozen candidate and retrieval
records. Successful groups are retained. A single-candidate group cannot split
further and fails safely.

Authorization, quota, model-access, request-schema, invented-selection,
freshness, and deterministic verification failures are not repaired by
switching models, widening retrieval, or falling back to ontology-blind
analysis. Partial structured output is not accepted.

## Determinism Boundary

For frozen inputs, Kotlin deterministically owns:

- located evidence and local candidate IDs;
- retrieval IDs, scores, reasons, ordering, and fingerprints;
- grounded work keys;
- evidence and selection validation;
- duplicate, collision, source, kind, and freshness checks;
- semantic compilation, dependency order, typed edits, and status counts.

The model still owns semantic judgment: whether evidence denotes ontology
meaning, whether two concepts are equivalent, which plausible choice fits, and
whether a new concept is justified. Human review still owns acceptance and
application. Phase 12 makes the pipeline more repeatable; it does not make the
whole pipeline deterministic.

## Responsibility Boundary

| Responsibility | Owner |
| --- | --- |
| Locate and extract document text | Kotlin |
| Extract stable evidence-linked search candidates | Kotlin with pinned local OpenNLP resources |
| Search and rank authorized ontology choices | Kotlin |
| Interpret business meaning from evidence and choices | Selected model |
| Choose reuse, extension, new, or unresolved treatment | Selected model, constrained to supplied IDs for existing entities |
| Verify evidence, choices, structure, duplicates, and freshness | Kotlin |
| Build connected plans and compile supported typed operations | Kotlin |
| Present evidence, alternatives, exact changes, and editable fields | React from server-owned contracts |
| Edit, accept, reject, approve, or apply recommendations | Human reviewer |

The short version is: retrieval narrows the choices, the model interprets
meaning, Kotlin supplies repeatable structure and safety, and a person controls
ontology changes.

## Security And Persistence

- Documents, ontology text, and provider output are always untrusted.
- Provider credentials remain server-side and are never returned to the
  browser or included in grounded prompts.
- Retrieval uses only project-authorized local state, imports, workspaces,
  provenance, and the pinned FIBO catalog.
- The model has no tools, filesystem access, arbitrary URL access, or apply
  authority.
- Uploads, extracted text, OCR images, incomplete tasks, work keys, provider
  payloads, and review workspaces remain temporary.
- Applied document provenance is the only durable document-analysis record and
  is stored separately from ontology source files.
- The CLI and VS Code extension expose no document-ingestion workflow.

## No-Write Guarantee

Candidate extraction, retrieval, grounded modeling, verification, compilation,
and review do not write ontology or SHACL sources. A reviewer must accept a
recommendation, stage the typed draft, review the existing proposal, and
explicitly approve it before the existing atomic apply service can write. The
same reload, rollback, and applied-provenance guarantees remain in force.

## Implementation Guide

The main implementation locations are:

- `DocumentIngestionOrchestrator.kt`: active Phase 12 sequence, work key,
  progress, counts, and review handoff;
- `DocumentCandidateExtractionService.kt`: pinned local OpenNLP pipeline and
  exact evidence spans;
- `DocumentRetrievalContextFactory.kt`: authorized scopes and fingerprints;
- `DocumentOntologyRetrievalService.kt`: deterministic lexical/structural
  ranking and full-state matching;
- `DocumentGroundedAnalysisService.kt`: request grouping, retry, splitting, and
  coverage validation;
- `OpenAiDocumentAnalysisClient.kt`: strict no-tools provider schema, limits,
  and safe failure classification;
- `DocumentGroundedAnalysisVerifier.kt`: selection, structure, duplicate,
  freshness, and editable-field verification;
- `DocumentSemanticPlanCompiler.kt` and `DocumentChangeSetPlanVerifier.kt`:
  deterministic compilation and final safety;
- `DocumentReviewWorkspace.kt`: grounded review context, editing, counts, and
  typed draft conversion;
- `DocumentIngestionWorkspace.tsx`: upload, progress, evidence, alternatives,
  collapsed recommendations, and explicit review actions.

The approved scope, specification, ExecPlan, and verified delivery record are:

- [Phase 12 scope](phase-12-scope.md)
- [Phase 12 specification](../specs/0023-phase-12-ontology-grounded-document-analysis.md)
- [Phase 12 ExecPlan](../execplans/0023-phase-12-ontology-grounded-document-analysis.md)
- [Phase 12 summary](../phase-summaries/phase-12-summary.md)
