# Phase 12 Scope

## Phase Name

**Phase 12: Ontology-Grounded Document Analysis**

## Status

Implemented and verified on 2026-07-31.

Phase 12 is complete. The approved specification defines the delivered product
behavior, and the Phase 12 ExecPlan records implementation order, slice
boundaries, allowed files, verification, and stop conditions. The verified
delivery is summarized in `docs/phase-summaries/phase-12-summary.md`.

Phase 12 builds on the implemented Phase 11, Phase 11.5, and Phase 11.5+
document-ingestion foundations. It changes how Entio finds document candidates
and when the model receives ontology context. It does not replace extraction,
human review, typed compilation, proposal approval, apply, reload, rollback, or
applied-document provenance.

## Purpose

Entio should show the model relevant ontology choices before the model builds a
document model.

Today, the model first interprets the document without seeing the ontology.
Entio compares that model with the ontology later. This can produce duplicate
concepts, weak property context, and unnecessary model-recommended classes that
could have reused something already present.

Phase 12 changes the order:

```text
extract document evidence
→ find candidate terms, names, relationships, and values locally
→ retrieve relevant current and approved ontology entities
→ let the model interpret the evidence with those choices in view
→ verify and compile the result in Kotlin
→ present connected recommendations for human review
→ use the existing safe apply and rollback workflow
```

This is retrieval-assisted modeling without embeddings or a vector database.

## Central Product Decision

> Retrieval narrows the choices, the model interprets business meaning, Kotlin
> enforces repeatable structure and safety, and a person controls what is
> applied.

The model may choose only from server-issued ontology candidates when it claims
that a document item reuses or extends an existing entity. Kotlin verifies that
the candidate is still current, has the expected kind, and belongs to an
approved scope.

## Current Problem

The current analysis flow is strongest after the model has already made its
main modeling decisions. By then:

- a document term may have been modeled as new even though an approved entity
  already exists;
- a property may lack a useful domain or range because related classes were not
  considered together;
- the model may use broad placeholders instead of a close ontology match;
- a later deterministic check can identify a problem but cannot recover the
  semantic choice the model would have made with better context;
- large ontology context is expensive and unreliable when sent without first
  narrowing it to relevant candidates;
- status counts can confuse connected-model items, recommendation bundles, and
  final typed edits.

Phase 12 moves bounded retrieval before semantic modeling so these choices are
made with relevant context available.

## Goals

Phase 12 should:

- identify a deterministic inventory of candidate names, concepts,
  relationships, attributes, values, requirements, and rule cues from extracted
  English text;
- preserve exact document locations for every candidate;
- search the current ontology, imports, current work, retained applied
  provenance, and pinned approved FIBO content before the model decides whether
  an ontology entity is new;
- reuse Entio's existing semantic search and matching services;
- give the model compact, relevant ontology context instead of a large ontology
  dump;
- require the model to classify each modeled item as reuse, extend, propose new,
  or unresolved;
- require new and extended properties and individuals to include their needed
  domain, range, datatype, or type context;
- keep supporting prerequisites in the same recommendation as the edit they
  support;
- let reviewers change proposed entity matches, labels, kinds, sources,
  domains, ranges, datatypes, types, and model-recommended prerequisites;
- use Kotlin to verify evidence, identifiers, kinds, domains, ranges, types,
  duplicates, source permissions, ontology rules, and freshness;
- compile valid meaning into existing typed ontology operations;
- reduce avoidable blocked and review-only recommendations without weakening
  safety;
- preserve the existing human approval, atomic apply, reload, rollback, and
  provenance workflow;
- report candidate, modeled-item, recommendation, and typed-edit counts as
  different measures.

## Non-Goals

Phase 12 does not add:

- embeddings;
- a vector database or vector index;
- approximate-nearest-neighbor search;
- a new graph database, document database, or search service;
- an external hosted NLP or retrieval service;
- external ontology search beyond project imports and the pinned approved FIBO
  catalog;
- a second copy of the ontology for retrieval;
- automatic approval or application;
- raw RDF, Turtle, SPARQL, or direct source-file writes from the model;
- an autonomous agent or unrestricted model loop;
- unsupported OWL or SHACL meanings;
- a second staging, proposal, or apply workflow;
- durable storage for uploads, incomplete tasks, model prompts, or review
  workspaces;
- document-ingestion commands in the CLI or VS Code extension;
- support for non-English documents;
- a promise that every business rule can become an executable ontology edit.

Current phase-level boundaries and non-goals remain those in `AGENTS.md`.

## Plain-Language Terms

### Candidate extraction

Candidate extraction is a local first pass over extracted text. It finds spans
that may matter, such as `Payment Analyst`, `receives`, `invoice`, `$25,000`, or
`must be approved`.

It does not decide that a phrase is definitely an OWL class or object property.
It prepares a stable inventory for retrieval and later model interpretation.

### Deterministic retrieval

Retrieval means searching Entio-controlled ontology descriptions and returning
a small, consistently ordered set of possible matches. The same candidate,
ontology state, search version, and approved catalog must return the same
results in the same order.

This is sometimes called retrieval-augmented generation, or RAG. In Phase 12 it
uses lexical and ontology-structure search, not embeddings.

### Grounded modeling

Grounded modeling means the model receives both the document evidence and the
retrieved ontology candidates. It must explain whether the document item should
reuse an entity, extend one, be proposed as new, or remain unresolved.

### Connected recommendation

A connected recommendation keeps a main edit together with the supporting
pieces it needs. For example, a property, its domain and range, and any new
supporting classes appear in one review bundle rather than as unrelated cards.

## Phase 12 Pipeline

```text
1. upload and intake validation
2. text extraction and bounded OCR
3. deterministic candidate extraction
4. deterministic ontology retrieval
5. ontology-grounded model interpretation
6. deterministic evidence and ontology verification
7. deterministic semantic assembly and typed compilation
8. connected human review and editing
9. existing proposal, approval, apply, reload, rollback, and provenance
```

### 1. Upload And Intake Validation

Phase 12 keeps the existing authorized, project-scoped upload boundary for PDF,
DOCX, TXT, and Markdown. Existing file, type, count, size, encryption,
temporary-storage, cancellation, and cleanup behavior remains unchanged.

### 2. Text Extraction And Evidence

Phase 12 keeps the existing PDF, DOCX, TXT, Markdown, and bounded local OCR
behavior. Extracted blocks retain document, page or section, offsets, method,
confidence, and exact text.

No candidate or model claim is evidence by itself. Evidence still resolves to
server-held extracted text.

### 3. Deterministic Candidate Extraction

Before any semantic model call, a local natural-language processing (NLP) stage
creates a candidate inventory.
The stage may use one established, pinned, JVM-compatible NLP library and its
pinned English resources. ExecPlan Slice 0 must audit its license, package size,
startup cost, supported Java version, and deterministic behavior. Entio must not
build a custom general-purpose NLP framework.

The inventory covers:

- named entities, such as organizations, people, dates, locations, identifiers,
  and monetary amounts;
- repeated or important concept terms and noun phrases;
- relationship phrases and their nearby participants;
- attribute and value pairs;
- obligation, prohibition, condition, threshold, and control cues;
- likely administrative or illustrative text.

Each candidate includes:

- a stable server-issued ID;
- its extraction category;
- normalized and display text;
- exact evidence span IDs and locations;
- nearby subject, predicate, object, attribute, or value hints when available;
- document identity and checksum;
- the pinned extractor and NLP contract versions.

Candidate IDs are derived from stable server-held inputs. Exact duplicate spans
may be joined. Similar phrases are not silently merged merely because their
tokens overlap.

Candidate extraction narrows the search problem; it is not the final semantic
authority. A traditional named-entity recognizer is useful for people,
organizations, dates, and values, but it cannot decide the full ontology model.

### 4. Deterministic Ontology Retrieval

Kotlin searches for possible matches for every retained candidate. Search uses
the candidate text, normalized forms, nearby relationship participants, and
available kind hints.

Search scopes remain ordered as follows:

1. applied local ontology;
2. imported project ontologies;
3. current private draft and shared staging;
4. current proposal;
5. other candidates in the same task, including other documents;
6. retained provenance from successfully applied document work;
7. the pinned approved FIBO catalog.

Phase 12 should reuse:

- `SemanticDescriptionService` for deterministic local and imported descriptor
  search;
- `DocumentOntologyMatcher` and its record model for current-work, same-task,
  and retained-provenance comparison;
- `FiboSchemaSearchService` for deterministic search of the pinned FIBO
  catalog.

A narrow document-retrieval adapter may coordinate these services and normalize
their results. It must not become a second semantic engine or index.

Each returned retrieval candidate includes:

- an opaque server-issued selection ID;
- canonical IRI;
- entity kind;
- scope and source;
- preferred and alternate labels;
- a short definition when available;
- relevant superclass, domain, range, datatype, or asserted-type context;
- deterministic match reasons and score;
- ontology, current-work, and catalog fingerprints.

At most 20 ranked candidates are sent to the model for one document candidate.
Exact duplicate and no-op checks may inspect the complete authorized scope and
are not limited to those 20 prompt results. Stable tie-breaking uses score,
scope order, entity kind, canonical IRI, and source ID.

No search result gives the model write authority. Imported and FIBO entities
remain read-only.

### 5. Ontology-Grounded Model Interpretation

The verified selected model receives bounded groups containing:

- candidate records;
- exact supporting evidence;
- nearby related candidates;
- retrieved ontology choices;
- current allowed semantic item and decision contracts;
- opaque IDs rather than filesystem paths, credentials, or project secrets.

For every modeled item, the model chooses one disposition:

- **Reuse existing**: use an exact server-issued retrieval candidate without
  changing its schema meaning;
- **Extend existing**: use an exact server-issued retrieval candidate and
  propose supported additions;
- **Propose new**: create a new local entity because no supplied candidate is a
  suitable semantic match;
- **Unresolved**: retain the item for a reviewer because the evidence or
  ontology choice is ambiguous.

Administrative and illustrative candidates receive explicit non-ontology
dispositions and remain in the coverage ledger.

The model also describes how items connect. A property must identify its domain
and range or datatype range. An individual must identify its type. A new
supporting class is labeled as a model-recommended prerequisite and attached to
the property or individual it supports.

When selecting reuse or extend, the model must return the server-issued
retrieval candidate ID. A label or invented IRI is not a valid selection.

The model may identify an important evidence-grounded item missed by the local
NLP stage. Kotlin records it as a model supplement, verifies its evidence, and
runs the same deterministic retrieval before allowing a create decision. If a
plausible existing match appears after that search, the item becomes unresolved
for human selection rather than creating a silent duplicate.

Document text remains untrusted data. Instructions inside a document cannot
change the system contract, call tools, select models, reveal secrets, widen
permissions, or bypass review.

### 6. Deterministic Verification

Kotlin verifies:

- candidate and evidence IDs;
- exact excerpts, offsets, pages, sections, and document ownership;
- retrieval selection IDs and current fingerprints;
- canonical IRIs and source scopes;
- entity kinds and reference roles;
- domain, range, datatype, and individual-type compatibility;
- imported and FIBO read-only rules;
- duplicates, collisions, and no-op changes;
- current draft, staging, proposal, same-task, and retained-provenance conflicts;
- coverage and explicit disposition of every candidate;
- supported ontology and SHACL patterns;
- typed operation dependencies and ordering.

Kotlin does not decide that two merely similar business terms mean the same
thing. The model or reviewer makes that semantic choice from verified retrieval
options.

Missing prerequisites do not become separate, unattached recommendations. They
remain fields or supporting edits in the same connected recommendation. If the
model cannot choose a safe value, the recommendation reaches review with a
clear **Needs input** field rather than disappearing or pretending to be
complete.

### 7. Semantic Assembly And Typed Compilation

The existing Phase 11.5+ semantic compiler remains the only compiler for
document-derived ontology meaning. Kotlin:

- resolves new and existing references;
- creates collision-checked final IRIs for approved new entities;
- places declarations before dependent assignments and assertions;
- compiles supported semantic patterns through existing typed edit services;
- keeps unsupported complex rules visible as review-only context;
- groups supporting prerequisites with the main edit;
- creates a complete coverage disposition;
- produces validation, semantic diff, reasoning, and SHACL previews.

Phase 12 does not restore model-generated low-level operations.

### 8. Human Review And Editing

The review list initially shows only:

- recommendation name;
- edit type;
- confidence;
- status;
- an expand or collapse control.

Expanded content shows evidence, selected and alternative ontology matches,
exact typed changes, definitions, generated IRIs, domains, ranges, datatypes,
types, prerequisites, confidence dimensions, and deterministic warnings.

Reviewers can change exposed semantic fields before acceptance. A changed
selection or field is reverified and recompiled in Kotlin. Editing does not
approve or apply anything.

Phase 12 should minimize avoidable `Blocked` and `ReviewOnly` results:

- missing domain, range, datatype, or type context becomes an editable field in
  the connected recommendation;
- a model-recommended prerequisite can compile when it is clearly marked,
  connected, editable, evidence-linked to the surrounding recommendation, and
  deterministically valid;
- ambiguity becomes `Needs input` with choices when a reviewer can resolve it;
- unsupported complex meaning remains review-only rather than being forced into
  an incorrect ontology form;
- stale state, invalid evidence, unauthorized sources, or structurally unsafe
  changes remain blocked.

### 9. Existing Apply And Provenance Workflow

Accepted executable changes enter the existing private-draft and proposal
workflow. Existing human approval, atomic apply, reload verification, rollback,
and applied-document provenance remain the only ontology write path.

No Phase 12 analysis route writes ontology or SHACL sources.

## Counts And Resource Bounds

The product must label each count by what it measures:

- extracted evidence blocks;
- NLP candidates;
- grounded model items;
- connected recommendation bundles;
- expanded typed edits.

These counts are not interchangeable. A property bundle may contain a property,
two supporting classes, domain and range assignments, and several evidence
spans while still appearing as one review recommendation.

Phase 12 preserves existing document, file, page, extracted-text, provider-call,
response-size, timeout, and concurrency safeguards. Provider input and output
remain chunked.

There is no product-level task ceiling that silently discards otherwise valid
modeled items, recommendations, or typed edits. Work is processed in bounded
chunks, and accepted changes may be divided into bounded internal staging
batches. If an emergency resource safeguard is reached, the task stops with an
explicit incomplete-work error; it must not claim complete coverage or continue
after truncating valid meaning.

The 20 retrieval candidates per document candidate bound limits prompt context,
not duplicate checking, final recommendations, or ontology edits.

## Determinism Boundary

Phase 12 is more repeatable, but the whole pipeline is not fully deterministic.

Deterministic for the same frozen inputs:

- text and location verification;
- local NLP configuration and candidate IDs;
- ontology queries, rankings, match reasons, and server-issued selection IDs;
- fingerprint and source checks;
- evidence, IRI, kind, domain, range, type, duplicate, and ontology validation;
- semantic compilation, dependency order, and previews;
- review edits and apply behavior.

Model-dependent:

- business interpretation;
- choosing among plausible retrieved candidates;
- deciding whether existing meaning should be extended;
- proposing new connected meaning;
- identifying ambiguity and unsupported rules.

The model operates inside a smaller verified choice set, which should reduce
variation and duplicates without pretending that semantic interpretation is a
purely mechanical task.

## Architecture Ownership

| Responsibility | Owner |
| --- | --- |
| Upload, extraction, OCR, task lifecycle, and provider calls | Ktor web server |
| Local NLP candidate extraction | Server-side Kotlin ingestion service |
| Ontology descriptors, search, matching, kinds, and structure | Kotlin semantic engine |
| Candidate and retrieval contracts | Core types |
| Business interpretation over evidence and retrieved choices | Selected model |
| Evidence, selection, freshness, duplicate, and ontology verification | Kotlin |
| Semantic assembly and typed compilation | Kotlin semantic engine and existing typed services |
| Compact review, evidence display, and editable fields | React web application |
| Accept, reject, edit, approve, and apply decisions | Human reviewer |

No product logic is added to `shared`. The CLI and VS Code extension do not gain
document-ingestion behavior.

## Security And Privacy

- Documents and provider output remain untrusted.
- Provider credentials remain server-side and are never included in retrieval
  records or browser responses.
- The model receives only bounded evidence and authorized ontology candidates.
- The model cannot browse an ontology, call retrieval repeatedly, or invent a
  new retrieval scope.
- Server-issued candidate IDs do not widen access to an IRI or project.
- Logs and progress events do not contain credentials, full prompts, full
  documents, or unauthorized ontology content.
- Prompt and response capture remains an explicit local diagnostic mode, not a
  durable production store.
- Uploaded documents, extracted content, and incomplete work remain temporary.

## Failure And Recovery

- Candidate extraction failure stops analysis with a stable safe code and keeps
  ontology sources unchanged.
- A candidate with no retrieval results is still sent to grounded modeling; no
  result is not itself an error.
- A stale retrieval fingerprint causes retrieval to run again before modeling
  or blocks a stale model result before compilation.
- A model-selected ID outside the supplied candidates is invalid and cannot be
  resolved from its label or IRI.
- Invalid items do not authorize guessed replacements. Independently valid
  items may continue, while the affected candidate receives an unresolved
  disposition so coverage remains visible.
- Missing property or individual context becomes editable `Needs input` state
  in its connected recommendation when safe alternatives can be shown.
- Provider timeouts, output limits, and temporary HTTP failures use the existing
  bounded retry and adaptive chunk behavior.
- Phase 12 does not fall back to ontology-blind modeling after retrieval fails.
- Cancellation and any final failure leave ontology sources unchanged.

## Benchmark And Regression Direction

The existing documents remain permanent fixtures:

- `examples/simple-ontology/documents/consumer-lending-servicing-compliance-standard.pdf`;
- `examples/simple-ontology/documents/commercial-account-and-payment-authorization-policy.pdf`.

Offline tests should prove deterministic candidate extraction, evidence spans,
retrieval order, match reasons, selection validation, duplicate prevention,
prerequisite grouping, compilation, review editing, and safe apply behavior.

Controlled provider benchmarks should compare frozen Phase 11.5+ and Phase 12
inputs and report:

- required concepts and relationships found;
- correct reuse and extension choices;
- avoidable new-entity recommendations;
- unresolved choices;
- connected recommendations and expanded typed edits;
- compilation success;
- prohibited recommendations;
- evidence and provenance validity;
- provider calls, tokens when available, duration, and failures;
- run-to-run consistency.

The benchmark must not apply ontology changes automatically.

## Suggested Delivery Areas

The approved ExecPlan separates delivery into these dependency-ordered slices:

1. current-contract and dependency audit;
2. provider-neutral Phase 12 contracts;
3. deterministic local candidate extraction;
4. deterministic ontology retrieval over existing search services;
5. the grounded provider boundary;
6. production orchestration and work-key integration;
7. deterministic selection, evidence, prerequisite, and compilation checks;
8. connected editable review and bounded draft batching;
9. offline regression, security, scale, and controlled-provider benchmarking;
10. final documentation and phase completion.

The Phase 12 ExecPlan is authoritative for exact branches, allowed files,
commands, completion artifacts, and stop conditions.

## Acceptance Criteria

Phase 12 is complete when:

1. A deterministic local NLP stage creates evidence-linked candidates before
   semantic model calls.
2. Every retained candidate receives bounded results from all applicable
   authorized ontology scopes before grounded modeling.
3. Retrieval reuses the existing local/imported semantic search,
   current-work/provenance matching, and pinned FIBO search services.
4. The model receives evidence and relevant ontology choices together.
5. Every modeled ontology item is classified as reuse, extend, propose new, or
   unresolved.
6. Reuse and extend decisions reference server-issued retrieval candidate IDs,
   not model-invented IRIs.
7. Kotlin revalidates evidence, selections, fingerprints, kinds, domains,
   ranges, datatypes, types, duplicates, sources, and ontology rules.
8. New supporting prerequisites are grouped with the item they satisfy and are
   clearly marked and editable.
9. Missing context that a reviewer can supply reaches review as `Needs input`
   instead of being discarded or separated into an unattached edit.
10. Valid supported meaning compiles through the existing Phase 11.5+ semantic
    compiler and typed edit services.
11. Unsupported complex meaning remains visible without being misrepresented as
    an executable ontology edit.
12. Reviewers can inspect alternatives and edit matches, kinds, sources,
    domains, ranges, datatypes, types, and prerequisites before acceptance.
13. Candidate, modeled-item, recommendation, and typed-edit counts are reported
    separately and accurately.
14. No valid item, recommendation, or edit is silently dropped to satisfy a
    task-wide product ceiling.
15. Existing human approval, apply, reload, rollback, and provenance behavior is
    reused, and no ontology source changes before approval.
16. The implementation adds no embeddings, vector database, second ontology
    index, external retrieval service, or new apply path.
17. Offline deterministic tests and the approved controlled-provider benchmark
    pass the Phase 12 quality gates defined by the spec and ExecPlan.

## Implemented Slice 0 Audit Decisions

- Local NLP uses Apache OpenNLP `2.5.11` with pinned English sentence,
  tokenizer, part-of-speech, and lemmatizer resources `1.3.0`.
- Existing semantic descriptions, current-work records, retained provenance,
  same-task records, and pinned FIBO search feed one narrow deterministic
  retrieval service; no second ontology index or persistence layer was added.
- Compact choices carry bounded labels, definitions, hierarchy, domain, range,
  datatype, asserted-type, reason, score, source, scope, and fingerprint data.
- The `phase-12-...-v1` contract family distinguishes candidate, grounded-item,
  recommendation, and expanded-edit counts and freezes the grounded work key.
- The existing two PDFs, simple ontology, current-work state, candidate and
  retrieval inventory, selected model, prompt/response versions, and thresholds
  form the permanent opt-in benchmark gate.

These decisions are recorded in
`docs/decisions/phase-12-slice-0-contract-and-dependency-audit.md`. The
implementation adds no embeddings, vector database, automatic approval, new
persistence layer, external retrieval service, or new ontology write path.
