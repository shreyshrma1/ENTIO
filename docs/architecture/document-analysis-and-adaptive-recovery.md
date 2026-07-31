# Document Analysis And Adaptive Recovery

## Purpose

Entio reads uploaded business documents and prepares evidence-linked ontology
changes for human review.

The current design separates three responsibilities:

- the model identifies and connects meaning in the documents;
- Kotlin verifies evidence, completes required structure, and compiles safe
  typed operations;
- a person reviews, edits, accepts, or rejects the result.

The model cannot approve changes, write RDF, change ontology files, or bypass
the existing proposal workflow.

## Current Production Flow

New document-ingestion tasks use this streamlined path:

```text
upload and intake validation
→ text extraction and bounded OCR
→ one evidence-grounded discovery call per document
→ connected-model calls over bounded discovery chunks
→ consolidation when more than one chunk succeeds
→ focused prerequisite completion when context is missing
→ current ontology snapshot
→ deterministic Kotlin semantic assembly
→ deterministic compilation and verification
→ grouped human review
→ existing draft, proposal, approval, and apply workflow
```

The current production path does **not** make separate model calls for
reconciliation, ontology alignment, modeling critique, or final planning. Those
older contracts remain in the codebase for compatibility and historical tests,
but `DocumentIngestionOrchestrator` uses the streamlined path for new tasks.

## Process In Detail

### 1. Upload and intake validation

The web application accepts a bounded set of PDF, DOCX, TXT, and Markdown
documents for an authorized project. Intake checks file type, size, count,
duplicate content, and task ownership before analysis starts.

Uploaded files and incomplete tasks are temporary. They are not added to the
ontology or retained as permanent project history.

### 2. Text extraction

Kotlin extracts located text blocks from each document. PDF pages with
unreliable embedded text may use bounded local OCR.

Every extracted block retains its document, page or section location,
extraction method, and exact text. Later evidence references must point back to
these blocks.

### 3. Evidence-grounded discovery

The selected verified model receives one ontology-blind discovery request per
document. It identifies possible:

- concepts and roles;
- relationships and attributes;
- individuals and assertions;
- requirements, controls, and conditional rules;
- administrative or illustrative content.

Each finding must cite evidence in the same document. Kotlin verifies the
evidence IDs, block IDs, offsets, excerpts, classifications, ordering, and
confidence values. Invalid findings are rejected individually when the valid
remainder is still usable.

At this stage the model does not see the current ontology and does not propose
Entio operations.

### 4. Connected document modeling

The model next receives the verified discoveries and describes how they connect.
For example, it may describe:

- a `Payment` class;
- a `Payment Analyst` role;
- a `receives instruction` object property;
- the property's domain and range;
- an approval rule that current typed operations cannot fully express.

This is the main semantic step. Kotlin does not infer these business
relationships from labels.

Large discovery inventories are divided into chunks based on the amount of
structured output they are expected to require. A chunk contains compact
discovery records and evidence IDs rather than repeating full evidence excerpts.

When more than one chunk succeeds, one consolidation call attempts to join the
chunk models. Kotlin accepts the consolidated result only when it preserves
discovery coverage, core declarations, enough structure, and complete property
and individual context. Otherwise Kotlin keeps the independently verified chunk
models and records why consolidation was not used.

### 5. Prerequisite completion

Some ontology changes require additional structure:

- an object property requires a domain and range;
- a datatype property requires a domain and datatype range;
- an individual requires a type.

When connected modeling omits this context, Entio sends one focused
prerequisite-completion request containing only the incomplete items, nearby
connected items, and their verified discoveries. If the response is valid but
still incomplete, Entio may make one focused correction request for the
remaining slots.

If provider completion still fails, Kotlin does not discard the useful meaning.
It supplies a conservative, editable placeholder and marks it
`reviewerInputRequired`. Examples include an editable `Finding` domain for
`finding status` or an editable `Organization` type for a named bank.

A placeholder is a proposed modeling choice, not a claim that the document used
that exact term.

### 6. Current ontology context

After connected modeling, Kotlin reloads the current project and records:

- the current ontology fingerprint;
- the current-work fingerprint;
- writable ontology sources;
- existing entity IRIs and kinds;
- the project IRI namespace;
- retained applied-document provenance.

The current streamlined path uses this context during deterministic compilation
and stale-state checks. It does not ask the model to perform a separate ontology
alignment pass.

### 7. Deterministic semantic assembly

Kotlin converts the verified connected model into a neutral semantic plan. No
provider call occurs at this stage.

Assembly performs repeatable structural work:

1. join exact duplicate declarations;
2. join exact duplicate assignments and relationships only when their kinds,
   normalized labels, references, literals, and datatypes match;
3. add missing editable property or individual prerequisites;
4. retain wrong-kind domain, range, or type references as review context and
   add a correctly typed editable placeholder;
5. omit model-recommended declarations that are not connected to anything;
6. propagate review-only status through explicit dependencies;
7. build one recommendation bundle for each connected component;
8. create a complete coverage disposition for every verified discovery.

Kotlin does not merge merely similar concepts or invent new business
relationships. Those decisions require semantic judgment.

### 8. Connected recommendation bundles

A connected component becomes one user-facing recommendation. Kotlin forms the
component before separating executable and unsupported meaning, so prerequisites
stay beside the edit they support.

One bundle may contain both:

- supported items that Kotlin can compile; and
- unsupported or ambiguous meaning retained as review context.

This produces a `Mixed` recommendation instead of a separate task-wide
`Review Only` card. For example, Kotlin may compile an editable
`Complaint Trend Report` class while retaining a complex monthly-review rule on
the same recommendation.

A clearly marked model-recommended prerequisite may be compilable even when the
document does not explicitly name that class. It must remain editable, cite
verified evidence for the surrounding bundle, use compatible references, pass
deterministic checks, and receive human approval.

### 9. Deterministic compilation and verification

The semantic compiler converts supported items into Entio's existing typed
operations. Supported patterns include declarations, hierarchy, property domain
and range, individual types and assertions, labels and definitions, and bounded
SHACL patterns.

Kotlin owns:

- supported pattern selection;
- temporary-reference resolution;
- IRI generation;
- source selection;
- dependency ordering;
- duplicate and no-op checks;
- expanded edit counting;
- stale ontology and current-work checks;
- final operation verification.

The final recommendation is not written by the model. Kotlin assembles and
compiles it directly from the verified connected structure.

One invalid bundle does not invalidate unrelated bundles. Unsafe work remains
blocked locally with a stable reason code.

### 10. Human review and apply

The review screen initially shows a compact row containing the name, edit type,
confidence, status, and an expansion control. Expanding a row shows exact typed
changes, evidence, confidence dimensions, model-recommended fields, reviewer
placeholders, and retained review context.

Recommendations can be:

- `Executable`: all retained content compiled into typed operations;
- `Mixed`: typed operations are available and unsupported meaning remains
  visible on the same recommendation;
- `ReviewOnly`: no supported ontology operation can yet represent the retained
  meaning;
- `Blocked`: deterministic safety checks found an unresolved problem.

Users may edit exposed labels, entity references, datatypes, and prerequisite
fields before accepting a recommendation. Editing returns it to pending review;
it does not approve or apply it.

Accepted recommendations enter the existing private-draft and proposal
workflow. The existing approval, atomic apply, reload, rollback, and applied
provenance paths remain the only way document-derived changes reach ontology
sources.

## Model Items, Recommendations, And Typed Edits

These counts describe different things:

- a **discovery** is one evidence-grounded piece of document meaning;
- a **connected-model item** is one structural piece such as a class, property,
  domain, range, assertion, or rule;
- a **recommendation** is one connected user-facing bundle;
- a **typed edit** is one concrete operation staged for the ontology.

Therefore, retaining 292 connected-model items does not imply 292 review cards
or 292 ontology edits. One property recommendation may contain a property
declaration, two class declarations, a domain assignment, a range assignment,
and review context. Exact duplicates and shared prerequisites can further reduce
the final recommendation count.

## Bounds

The current safety bounds are local and resource-oriented:

- at most 10 documents per task;
- at most 200 discoveries per document and 2,000 per task;
- at most 300 connected items in one provider response;
- at most 15 planned logical provider calls per task;
- at most 20 provider attempts per task;
- at most 3 automatic retry attempts across a task;
- at most 1,000,000 characters in one provider response;
- at most 20 expanded typed edits in one final recommendation.

Discovery and ordinary connected-model calls allow up to 16,000 output tokens.
Consolidation allows up to 32,000 because it must combine multiple verified
chunk models.

There is no task-wide ceiling on retained connected items, recommendations, or
typed edits. Large valid tasks may produce hundreds of reviewable changes.
Accepted changes are divided into bounded internal staging batches rather than
being discarded.

## Why Chunking Exists

Chunking protects provider reliability, not semantic correctness. A model can
usually reason about a bounded subset more reliably than an inventory requiring
a very large structured response.

Chunking does not intentionally truncate extracted text or discard discoveries.
Kotlin retains the complete verified discovery inventory and verifies coverage
after the connected-model stage.

### Adaptive splitting

If a connected-model chunk reaches the provider output limit or the provider is
temporarily unavailable:

1. keep every chunk that already succeeded;
2. split only the failed chunk into two balanced discovery sets;
3. process the children independently;
4. consolidate or merge the verified child results;
5. verify retained coverage.

A chunk containing one discovery cannot be split further. Entio then stops with
a safe failure instead of weakening or deleting that discovery.

## Retry And Failure Behavior

### Retryable provider failure

Rate limits, temporary unavailability, and network interruptions may receive a
bounded exact-input retry. A retry is another HTTP attempt for the same logical
call. The global task budget prevents repeated retries from growing without
limit.

For connected-model output exhaustion or provider unavailability, adaptive
splitting is preferred over repeatedly sending the same oversized chunk.

### Authorization, quota, model, or request-schema failure

These failures are not repaired by chunking. The task stops or becomes blocked
until the credential, provider account, selected model, or request contract is
corrected.

### Structurally invalid model output

Kotlin may request one bounded correction when a connected-model response is
mostly unusable. A small invalid minority can be skipped while verified items
continue. Missing prerequisites use the focused prerequisite stage instead of
regenerating the entire connected model.

### Incomplete prerequisite response

Entio may request one focused correction. If required context is still missing,
Kotlin supplies editable reviewer placeholders and continues to review.

### Deterministic compilation failure

The affected bundle becomes `Blocked`; unrelated bundles remain available.
Wrong-kind property or type context is handled specially: the invalid reference
is retained as review context and a correctly typed editable placeholder is
proposed in the same bundle.

### HTTP 500

An HTTP 500 means the provider failed while processing an accepted request. It
does not mean Kotlin rejected the ontology plan, and it does not by itself prove
that the API key or document is invalid.

Entio classifies provider HTTP 500 responses as temporary provider
unavailability. During connected modeling, the failed chunk may be split when
the remaining call budget permits it.

### Output-token limit

An output-token-limit failure means the provider stopped before completing the
required structured JSON. Entio does not parse or accept the partial result.
During connected modeling it attempts smaller chunks; it does not increase the
limit without bound.

## Call Accounting

Entio tracks:

- a **logical call**, meaning a distinct document, chunk, consolidation, or
  prerequisite request;
- a **provider attempt**, meaning one HTTP request, including retries;
- a **deterministic stage**, meaning local Kotlin work with no provider request.

Discovery, connected modeling, consolidation, and prerequisite completion use
provider calls. Semantic assembly, compilation, verification, review, and apply
do not.

Keeping these counts separate makes status and retry-limit errors easier to
interpret.

## Responsibility Boundary

| Responsibility | Owner |
| --- | --- |
| Understand the document's business meaning | Model |
| Decide which discovered concepts and relationships connect | Model |
| Recommend implied prerequisites for review | Model |
| Identify ambiguity and complex unsupported rules | Model |
| Verify evidence IDs, excerpts, locations, and classifications | Kotlin |
| Verify connected references and compatible item kinds | Kotlin |
| Detect missing domain, range, datatype, and type context | Kotlin |
| Supply visibly editable fallback prerequisites | Kotlin |
| Build connected recommendation bundles | Kotlin |
| Compile semantic items into supported typed operations | Kotlin |
| Check freshness, sources, dependencies, duplicates, and limits | Kotlin |
| Edit, accept, reject, approve, or apply recommendations | Human reviewer |

The short version is: the model supplies meaning, Kotlin supplies repeatable
structure and safety, and a person controls ontology changes.

## Planned Phase 12 Change

Phase 12 is approved for implementation but is not part of the current
production flow. It will insert deterministic local candidate extraction and
retrieval from authorized ontology scopes before the model interprets business
meaning. The model will receive compact evidence and relevant existing-entity
choices together, while Kotlin continues to verify selections, assemble
structure, compile typed edits, and enforce the review boundary.

Phase 12 adds no embeddings, vector database, second ontology index, or new
write path. See the approved [scope](phase-12-scope.md),
[specification](../specs/0023-phase-12-ontology-grounded-document-analysis.md),
and [ExecPlan](../execplans/0023-phase-12-ontology-grounded-document-analysis.md).

## Example

Suppose a document says:

> A payment analyst receives an instruction and supporting record before
> creating an approval decision.

Connected modeling may return:

- `Payment Analyst` and `Instruction` classes;
- a `receives instruction` object property;
- domain and range assignments for that property;
- a model-recommended `Approval Decision` class;
- a `creates approval decision` property with its domain and range;
- a complex timing rule that cannot be represented by current typed operations.

Kotlin keeps those pieces in connected bundles, adds any missing editable
context, orders declarations before assignments, and compiles supported
operations. The timing rule remains visible as review context. The reviewer can
change the recommended class, domain, range, or datatype before accepting the
bundle.

## Safety Boundaries

- Documents and provider output are always untrusted.
- Provider credentials remain server-side and are never returned to the browser.
- The model has no tools, filesystem access, apply authority, or raw RDF path.
- Kotlin remains authoritative for supported ontology behavior.
- Partial structured model output is not accepted.
- Every executable operation must pass deterministic verification.
- Every recommendation remains subject to human review.
- Applied document provenance is stored separately from ontology source files.
- The CLI and VS Code extension do not expose document-ingestion workflows.

## Implementation Guide

The main implementation locations are:

- `DocumentIngestionOrchestrator.kt`: active production sequence and progress;
- `DocumentAnalysisService.kt`: discovery, connected modeling, prerequisite
  completion, deterministic assembly, compilation coordination, and limits;
- `OpenAiDocumentAnalysisClient.kt`: provider requests, structured schemas,
  output limits, and safe provider failure classification;
- `DocumentSemanticPlanCompiler.kt`: deterministic semantic-to-operation
  compilation;
- `DocumentChangeSetPlanVerifier.kt`: final operation safety checks;
- `DocumentReviewWorkspace.kt`: grouped review, editing, and draft conversion;
- `DocumentIngestionWorkspace.tsx`: upload, progress, evidence, and collapsible
  recommendation presentation.
