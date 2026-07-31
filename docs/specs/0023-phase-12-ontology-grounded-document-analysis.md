# Feature Spec: Phase 12 Ontology-Grounded Document Analysis

## Status

Approved for implementation.

This specification defines Phase 12. It builds on the implemented Phase 11,
Phase 11.5, and Phase 11.5+ document-ingestion workflow. Phase 12 is not yet
implemented. The newer approved ExecPlan is authoritative for dependency order,
slice ownership, allowed files, completion artifacts, verification commands,
and stop conditions. If implementation detail here conflicts with that plan,
the ExecPlan controls without expanding this specification's product scope.

## Related Documents

- [Phase 12 scope](../architecture/phase-12-scope.md)
- [Phase 12 ExecPlan](../execplans/0023-phase-12-ontology-grounded-document-analysis.md)
- [Current document analysis and adaptive recovery](../architecture/document-analysis-and-adaptive-recovery.md)
- [Phase 11 scope](../architecture/phase-11-scope.md)
- [Phase 11 spec](0020-phase-11-ai-powered-document-ingestion-and-ontology-evolution.md)
- [Phase 11.5 scope](../architecture/phase-11.5-scope.md)
- [Phase 11.5+ scope](../architecture/phase-11.5-plus-scope.md)
- [Phase 11.5+ spec](0022-phase-11.5-plus-deterministic-compilation-of-connected-document-models.md)

## Problem

Entio currently asks the model to understand document meaning before the model
sees relevant entities from the current ontology. Kotlin searches, verifies,
and compiles later, but that is sometimes too late to prevent a weak semantic
choice.

This ordering can cause:

- a new class to be proposed when an approved class could be reused;
- a property to be proposed without a useful domain or range;
- a broad placeholder to be created instead of extending a close ontology
  match;
- related prerequisites to appear as separate, blocked edits;
- deterministic validation to reject a model choice without enough information
  to suggest the better semantic choice;
- unnecessarily large prompts when ontology context is supplied without first
  narrowing it;
- confusing progress reports where model items are mistaken for final edits.

Phase 12 must retrieve a small set of relevant, authorized ontology candidates
before the model builds the document model. The model then interprets the
business evidence in light of those choices, while Kotlin remains responsible
for repeatable verification and compilation.

## Goals

- Add deterministic local natural-language processing (NLP) candidate
  extraction before semantic model calls.
- Preserve exact evidence locations for every extracted candidate.
- Search every applicable authorized ontology scope before the model decides
  whether an item is new.
- Reuse the current semantic description search, document matching, and pinned
  FIBO search services.
- Give the model compact evidence and relevant ontology choices together.
- Require a clear reuse, extend, propose-new, or unresolved decision for every
  modeled ontology item.
- Prevent the model from selecting an existing entity by inventing or copying an
  IRI outside the returned candidate set.
- Require property domain and range context, datatype ranges, and individual
  types as part of connected modeling.
- Keep model-recommended prerequisites attached to the edit they support.
- Let reviewers edit matches and prerequisite fields before acceptance.
- Verify evidence, identifiers, entity kinds, domains, ranges, datatypes, types,
  duplicates, ontology rules, source permissions, and freshness in Kotlin.
- Compile valid meaning through the existing Phase 11.5+ compiler and typed edit
  services.
- Reduce avoidable blocked and review-only results without weakening the
  existing human-review boundary.
- Preserve existing proposal approval, atomic apply, reload, rollback, and
  applied-document provenance.
- Report NLP candidates, modeled items, recommendations, and typed edits as
  separate counts.

## Non-Goals

Phase 12 does not add:

- embeddings or embedding generation;
- a vector database, vector index, or approximate-nearest-neighbor search;
- a new graph database, search server, or second ontology index;
- an external hosted NLP service;
- external ontology retrieval beyond project imports and the pinned approved
  FIBO catalog;
- automatic approval, application, or source-file writes;
- raw RDF, Turtle, SPARQL, or model-generated Entio operation DTOs;
- a second compiler, staging workflow, proposal workflow, or apply path;
- an autonomous agent, model-controlled retrieval tool, or unrestricted retry
  loop;
- unsupported OWL or SHACL semantics;
- guaranteed executable treatment for every complex business rule;
- durable storage for source documents, extracted text, incomplete tasks,
  prompts, responses, or review workspaces;
- a new CLI or VS Code document-ingestion surface;
- non-English document analysis;
- a custom general-purpose NLP framework.

Current phase-level boundaries and non-goals remain those in `AGENTS.md`.

## Proposed Behavior

### 1. Preserve Existing Intake, Extraction, And Apply Boundaries

Phase 12 keeps the current project authorization, upload, file validation,
temporary storage, extraction, selective local OCR, evidence location,
cancellation, and cleanup behavior.

It also keeps the existing private draft, proposal, human approval, atomic
apply, reload verification, rollback, and applied-document provenance behavior.
No new analysis route may write ontology or SHACL sources.

The Phase 12 change begins after verified extracted text is available and ends
when connected recommendations have been deterministically compiled for review.

### 2. Build A Deterministic Candidate Inventory

Entio runs a local, server-side candidate-extraction stage before semantic model
calls. The implementation may add one established JVM-compatible NLP library
and its pinned English resources after the ExecPlan's contract and dependency
audit confirms licensing, package size, startup time, Java compatibility, and
repeatability.

Candidate extraction identifies:

- named organizations, people, locations, dates, identifiers, and amounts;
- important concept terms and noun phrases;
- relationship phrases and nearby participants;
- attribute and value pairs;
- obligation, prohibition, condition, threshold, and control cues;
- likely administrative metadata and illustrative examples.

This stage does not make final OWL-kind or reuse decisions. For example,
`Payment Analyst` may be a named role or concept candidate; the later grounded
model stage decides whether the ontology should represent it as a class,
individual, reuse target, or unresolved item.

Each candidate record contains:

- a stable server-issued candidate ID;
- a candidate-extraction category;
- display text and a deterministic normalized form;
- one or more exact evidence span references;
- document ID and checksum;
- nearby subject, relationship, object, attribute, or value hints when the NLP
  tool provides them;
- extractor and NLP contract versions;
- deterministic ordering fields.

The ID is derived from server-held inputs including document checksum, evidence
location, normalized text, and extraction category. The browser and model never
choose it.

Exact duplicate spans may be joined. Similar phrases are kept separate unless a
later model decision or deterministic exact-identity rule joins them. Token
overlap alone is not proof of semantic identity.

### 3. Retrieve Ontology Choices Before Modeling

For each retained candidate, Entio searches these authorized scopes in order:

1. applied local ontology;
2. imported project ontologies;
3. current private draft and shared staging;
4. current proposal;
5. other candidates in the same document task;
6. durable provenance from successfully applied document work;
7. the pinned approved FIBO catalog.

The retrieval implementation reuses:

- `SemanticDescriptionService` for local and imported descriptors;
- `DocumentOntologyMatcher` records and scoring where they cover current work,
  same-task items, and retained provenance;
- `FiboSchemaSearchService` for the pinned FIBO catalog.

A narrow retrieval facade may coordinate those services, build queries, remove
duplicate results, and expose one contract. It must not maintain a second copy
of ontology data or reimplement semantic rules.

The query uses the candidate's display and normalized text plus available
relationship, participant, attribute, and value context. Kind hints may narrow
the query, but uncertain NLP categories must search all compatible ontology
kinds rather than treating a heuristic as fact.

Each retrieval result contains:

- an opaque server-issued selection ID;
- canonical entity IRI;
- ontology entity kind;
- scope and source ID;
- preferred label and bounded alternate labels;
- bounded definition text when available;
- directly relevant hierarchy, domain, range, datatype, or asserted-type
  context;
- deterministic match reasons and score;
- ontology, current-work, provenance, and pinned-catalog fingerprints as
  applicable.

Entio sends at most the top 20 results for one candidate to the model. Ranking
and ties are stable for frozen inputs. Tie-breaking uses score, approved scope
order, entity kind, canonical IRI, and source ID.

The top-20 prompt bound does not limit exact duplicate, collision, no-op, or
source checks. Kotlin may inspect the complete authorized project state for
those checks.

A candidate with no search result remains valid input. The model may propose it
as new or unresolved.

### 4. Freeze A Grounded-Analysis Work Key

Before a grounded model call, Entio records a work key containing at least:

- authorized project and task identity;
- document IDs, checksums, metadata, and extraction versions;
- evidence inventory hash;
- candidate-extraction contract and resource versions;
- candidate inventory hash;
- ontology and current-work fingerprints;
- retained-provenance fingerprint;
- pinned FIBO version;
- retrieval contract and ranking version;
- selected verified provider model;
- grounded-model prompt and response contract versions.

Equivalent frozen inputs produce the same candidate IDs, retrieval IDs,
retrieval ordering, and work key. Any relevant change permits reprocessing but
invalidates stale model selections.

All new Phase 12 prompt, request, response, verification, review, progress,
work-key, and benchmark contracts use an audited `phase-12-...-v1` version
family. Legacy Phase 11.5 and Phase 11.5+ contracts remain available only where
the ExecPlan's compatibility audit shows they are still required; new Phase 12
tasks do not fall back to ontology-blind modeling.

### 5. Send Bounded Grounded Modeling Requests

Entio groups related candidates into bounded model requests. A request contains:

- compact candidate records;
- exact evidence excerpts and locations needed by those candidates;
- nearby candidate connections;
- bounded retrieval results;
- allowed semantic item kinds;
- allowed disposition values;
- clear instructions that document text and retrieved descriptions are
  untrusted data;
- opaque IDs rather than credentials, filesystem paths, provider settings, or
  raw project configuration.

Groups should follow evidence and candidate connections where practical so a
relationship is considered with its likely participants. Provider token and
response-size bounds remain enforced. Failed oversized groups use existing
adaptive splitting; successful groups are retained.

The model has no retrieval tool and cannot request arbitrary ontology content.
All ontology context is selected by Kotlin before the request.

### 6. Require Grounded Semantic Decisions

Each modeled ontology item has:

- a stable task-local item ID;
- semantic item kind;
- label and optional definition;
- connected item references;
- supporting candidate and evidence IDs;
- rationale;
- one allowed disposition;
- confidence for evidence, modeling, and ontology fit;
- unresolved ambiguity when applicable;
- model-recommended prerequisite markers when applicable.

Allowed ontology dispositions are:

- `ReuseExisting`;
- `ExtendExisting`;
- `ProposeNew`;
- `Unresolved`.

`ReuseExisting` and `ExtendExisting` require exactly one server-issued retrieval
selection ID. The model may not provide a final IRI as a substitute. The
selected result must be compatible with the semantic item kind.

`ProposeNew` means that none of the supplied retrieval choices represents the
intended business meaning. It does not bypass Kotlin's full duplicate and
collision checks.

`Unresolved` retains the item and its choices for a reviewer. It is used when
the evidence supports an important item but not one safe semantic choice.

Administrative metadata and illustrative examples receive explicit coverage
dispositions. Illustrative individuals retain the current individual-creation
confirmation gate.

### 7. Require Connected Prerequisites

The grounded semantic contract requires:

- every new or extended object property to identify a domain and range;
- every new or extended datatype property to identify a domain and datatype
  range;
- every new individual to identify a class type;
- every assertion to identify compatible subject, property, and target/value;
- every supported constraint to identify the class or property shape it
  constrains.

A prerequisite may reuse a retrieved entity, extend one, refer to another new
item in the same connected group, or remain an editable unresolved choice.

When the document does not explicitly name a required supporting class, the
model may recommend one. The review must label it **Model-recommended
prerequisite**, attach it to the main item it satisfies, expose it for editing,
and preserve evidence from the surrounding connected meaning. It does not need
to pretend that the document used that exact class name.

A prerequisite is not emitted as a separate unattached recommendation. Shared
prerequisites may be referenced by several recommendations when dependency
ordering and atomic application remain safe.

If the model omits a required field, Kotlin retains the main item and creates a
`NeedsInput` review field with compatible retrieved choices when available. It
does not manufacture a confident semantic answer. The item becomes executable
only after the field is completed and reverified.

### 8. Handle Model-Supplemented Candidates Safely

The model may notice important evidence that the local candidate extractor
missed. Such an item must include exact evidence references and is marked
`ModelSupplement`.

Kotlin then:

1. verifies the evidence and item category;
2. creates a stable supplemental candidate ID;
3. runs the same deterministic retrieval over all authorized scopes;
4. performs complete duplicate and kind checks;
5. permits `ProposeNew` only when no plausible existing match remains;
6. otherwise changes the item to `Unresolved` and presents the retrieved choices
   to the reviewer.

This fallback preserves model recall without letting an item bypass pre-model
retrieval or silently create a duplicate.

### 9. Verify Every Grounded Result In Kotlin

Kotlin validates in this order:

1. response schema, version, size, IDs, enums, and bounds;
2. candidate, evidence, and connected-item references;
3. exact evidence excerpts, offsets, locations, and document ownership;
4. retrieval selection IDs and frozen fingerprints;
5. canonical entity identity, kind, scope, and source permissions;
6. reuse and extension compatibility;
7. domains, ranges, datatype ranges, individual types, and assertion roles;
8. prerequisite attachment and dependency completeness;
9. exact duplicates, normalized typed-operation duplicates, IRI collisions, and
   no-op changes;
10. current work, same-task items, retained provenance, imports, and FIBO rules;
11. complete candidate and discovery dispositions;
12. supported semantic and SHACL patterns;
13. deterministic compilation, ordering, previews, and confidence.

Kotlin does not infer that similar business labels have identical meaning. It
verifies the selected semantic choice; it does not replace that choice with a
different entity to make validation pass.

Independently valid groups may continue when another group is unresolved. No
candidate is silently removed from the coverage ledger.

### 10. Compile Through The Existing Semantic Compiler

Supported grounded semantic items are converted into the existing Phase 11.5+
semantic-plan representation and compiled by the current deterministic Kotlin
compiler.

Kotlin continues to own:

- final IRI generation for new local entities;
- collision checks;
- existing-entity reference resolution;
- typed operation selection;
- declaration and dependency ordering;
- domain, range, datatype, type, assertion, hierarchy, label, definition, and
  supported SHACL operations;
- duplicate and no-op suppression;
- source selection and writable-source checks;
- connected recommendation boundaries;
- validation, semantic diff, reasoning, and SHACL previews.

Unsupported separation-of-duty, aggregation, procedural, temporal, and other
complex rules remain evidence-linked review-only context unless an existing
approved typed operation represents their full meaning.

The model never emits final Entio operation kinds, raw graph statements, or
source-write instructions.

### 11. Build Connected Review Recommendations

One review recommendation represents one understandable connected change. It
contains the main edit, the prerequisites it needs, relevant reuse alternatives,
and related unsupported meaning.

Recommendation statuses are:

- `Executable`: all required fields are complete and deterministic checks pass;
- `Mixed`: executable edits are available and related unsupported meaning
  remains visible;
- `NeedsInput`: a reviewer can resolve a missing match, kind, source, domain,
  range, datatype, type, or prerequisite field;
- `ReviewOnly`: important meaning has no approved executable representation;
- `Blocked`: stale, unauthorized, unverified, structurally invalid, or otherwise
  unsafe work cannot proceed.

`NeedsInput` is preferred over `Blocked` when ordinary reviewer input can make a
recommendation complete. `ReviewOnly` is not used merely because the model
omitted a prerequisite that Entio can expose as an editable field.

Model-recommended prerequisites remain clearly distinguished from terms stated
explicitly in the document.

### 12. Make Review Compact And Editable

When review opens, every recommendation is collapsed. The visible summary shows
only:

- recommendation name;
- edit type;
- confidence;
- status;
- an expand or collapse control.

Expanding shows:

- plain-language intent;
- exact compiled changes;
- selected existing entity or proposed new entity;
- alternative retrieval candidates and match reasons;
- generated IRI and target source;
- domain, range, datatype, type, and prerequisite fields;
- evidence and provenance;
- model and deterministic confidence dimensions;
- review-only context and safe warnings.

The reviewer may change:

- reuse, extend, new, or unresolved treatment;
- an existing-entity match;
- ontology entity kind where the allowed edit contracts support it;
- label and definition;
- writable target source;
- domain and range;
- datatype range;
- individual type;
- model-recommended prerequisite labels and matches.

Every edit returns the recommendation to pending review and triggers Kotlin
revalidation and recompilation. The browser cannot construct raw typed
operations or mark an invalid result executable.

### 13. Report Counts Without Mixing Levels

Progress and review contracts report separate fields for:

- extracted evidence blocks;
- NLP candidates retained and rejected;
- grounded model items retained, unresolved, and rejected;
- connected recommendations by status;
- expanded typed edits.

Messages must name the unit. For example, `292 grounded model items retained`
must not imply that the review contains 292 cards or 292 edits.

The final review summary reports recommendation and expanded-edit totals rather
than reusing the model-item count.

### 14. Preserve Bounded Work Without A Silent Semantic Ceiling

Existing intake, extraction, provider request, provider response, timeout,
retry, and concurrency safeguards remain. Retrieval sends no more than 20
ranked choices per candidate, and model calls remain chunked.

There is no product-level task ceiling that silently converts valid modeled
items into review-only results or discards valid recommendations or typed edits.
Accepted edits are divided into dependency-safe internal draft batches of at
most 20 typed edits using the existing atomic workflow. The existing
20-expanded-edit recommendation boundary may remain only as an atomic
compilation boundary with deterministic splitting. Neither bound is a
task-wide item, recommendation, or edit ceiling.

If a memory, time, or emergency safety threshold prevents complete processing,
the task fails with an explicit incomplete-work error and a correct processed
count. It must not claim complete coverage, continue to later stages after
truncation, or turn overflow into fake review-only findings.

### 15. Keep Human Approval As The Only Write Authority

Accepting a recommendation adds verified typed work to the existing private
draft. Existing proposal review and explicit human approval remain required.

Analysis, retrieval, modeling, recommendation editing, and acceptance do not
write ontology sources. Existing atomic apply, reload verification, rollback,
and applied-document provenance remain the only final path.

## Inputs And Outputs

### User Inputs

- authorized project and existing supported document set;
- current document metadata required by Phase 11;
- the user's verified selected compatible provider model;
- existing start, cancel, retry, delete, accept, reject, and individual
  confirmation actions;
- review edits to the explicitly supported semantic fields.

The user does not provide raw RDF, model prompts, ontology query syntax, vector
configuration, arbitrary external catalog endpoints, or source-write
instructions.

### Existing Entio Inputs

- extracted located text and OCR confidence;
- loaded local and imported ontology descriptors;
- private draft, shared staging, and current proposal state;
- durable applied-document provenance;
- pinned FIBO catalog and version;
- writable source and namespace rules;
- current ontology and current-work fingerprints;
- existing typed ontology and SHACL edit services;
- existing semantic compiler, proposal, apply, reload, rollback, and provenance
  services.

### New Structured Inputs

- versioned candidate-extraction configuration and resource identity;
- deterministic candidate inventory;
- versioned document-retrieval query and result contracts;
- frozen grounded-analysis work key;
- versioned grounded-model request and response;
- review edits that reference server-issued candidate and selection IDs.

### Outputs

- deterministic evidence-linked NLP candidates;
- bounded, deterministically ranked ontology candidates with match reasons;
- grounded semantic items and their dispositions;
- complete candidate and evidence coverage ledger;
- connected prerequisites and unresolved editable fields;
- Kotlin-verified reuse, extend, and new-entity decisions;
- compiled connected recommendations;
- distinct candidate, model-item, recommendation, and typed-edit counts;
- validation, semantic diff, reasoning, and SHACL previews;
- existing private-draft batches for accepted executable work;
- existing applied provenance after successful approved application;
- safe stage and error records without credentials or full document contents.

## Validation Behavior

- Candidate IDs, normalized forms, ordering, and exact-span joins are
  deterministic for frozen extracted text and NLP resources.
- Every candidate references authorized server-held evidence.
- Every retrieval query runs across all applicable approved scopes before a
  `ProposeNew` decision can become executable.
- Retrieval results use stable ranks, reasons, selection IDs, and tie-breaking.
- A reuse or extension selection resolves only through an ID supplied in that
  item's grounded request or a later reviewer-authorized refreshed result.
- Canonical IRI, kind, source, scope, and fingerprint are revalidated before
  compilation.
- Imported and FIBO entities remain read-only.
- Properties require compatible domains and ranges; datatype properties require
  supported datatype ranges; individuals require compatible types.
- Assertions require compatible subjects, properties, objects, or values.
- Model-recommended prerequisites must be connected, visible, editable, and
  deterministically valid.
- Duplicate, collision, no-op, stale-state, and source checks inspect the full
  authorized state rather than only prompt-visible retrieval results.
- Every candidate and model supplement receives exactly one final disposition.
- Every executable item maps to an approved semantic compiler pattern.
- Browser edits are reverified and recompiled before acceptance.
- All server-generated IDs, hashes, ordering, counts, and compilation results
  are deterministic for the same verified inputs.

## Error Behavior

- Unsupported language, unsafe file, extraction failure, and OCR failure retain
  current Phase 11 behavior.
- Local NLP initialization or processing failure stops the candidate stage with
  a stable safe error; no ontology state changes.
- No retrieval result is a valid empty outcome and does not fail the task.
- An unavailable or stale ontology scope causes retrieval to refresh or fail
  safely; Phase 12 does not fall back to ontology-blind semantic modeling.
- An invented, missing, stale, or wrong-kind retrieval selection is rejected.
  Entio does not resolve it from model-provided label text.
- Invalid model JSON, unknown fields, duplicate item IDs, bad references, and
  response overflow follow existing bounded correction and retry rules.
- Independently valid grounded groups may continue. The affected candidate must
  remain visible as unresolved when a safe partial result is possible.
- Missing reviewer-solvable domain, range, datatype, type, match, or prerequisite
  context produces `NeedsInput`, not a separate unattached edit.
- Unsupported but evidence-backed complex meaning produces `ReviewOnly`.
- Stale evidence, unauthorized sources, cross-project IDs, incompatible entity
  kinds, unsafe dependencies, or unverifiable claims produce `Blocked`.
- A full-state duplicate or no-op prevents redundant creation and explains the
  matching existing or current-work item.
- Provider timeout, rate limit, temporary HTTP failure, and output-limit errors
  use current bounded retry and adaptive chunk handling.
- Reaching an emergency resource threshold produces an explicit incomplete-work
  failure. Entio does not truncate silently or report complete coverage.
- Cancellation stops remaining local and provider work, cleans temporary state
  according to existing rules, and leaves ontology sources unchanged.
- Any compilation, preview, apply, reload, or verification failure uses the
  existing atomic safe-failure and rollback boundaries.

## Test Cases

### Candidate Extraction

- Extract organizations, people, dates, identifiers, locations, amounts, and
  repeated concept phrases from fixed English text.
- Extract relationship phrases with nearby participants.
- Extract attribute/value and obligation/condition/threshold cues.
- Keep exact document, page or section, block, and offset references.
- Produce identical candidate IDs and ordering for identical frozen inputs.
- Join exact duplicate spans without merging merely similar business terms.
- Separate administrative and illustrative candidates from business candidates.
- Reject cross-document, missing, or altered evidence spans.
- Fail safely when the pinned NLP resource cannot initialize.

### Deterministic Retrieval

- Search applied local, imported, private draft, shared staging, proposal,
  same-task, durable provenance, and pinned FIBO scopes in the approved order.
- Reuse `SemanticDescriptionService` results for local and imported entities.
- Reuse `DocumentOntologyMatcher` records for current work, same-task, and prior
  provenance where applicable.
- Reuse `FiboSchemaSearchService` results for pinned FIBO content.
- Return stable selection IDs, scores, reasons, order, and ties.
- Include bounded labels, definitions, kinds, sources, and relevant structural
  context.
- Search compatible kinds when the NLP hint is uncertain.
- Return an empty result without failing.
- Send at most 20 results per candidate while performing full-state duplicate
  and no-op checks.
- Reject unapproved FIBO modules, stale imports, and cross-project records.

### Grounded Model Contract

- Accept reuse, extend, propose-new, and unresolved decisions with valid
  evidence and connected references.
- Require one supplied retrieval selection ID for reuse and extend.
- Reject a final IRI used instead of a selection ID.
- Reject an ID that was not present in the item's bounded request.
- Preserve rationale and evidence, modeling, and ontology-fit confidence.
- Require domain and range for an object property.
- Require domain and datatype range for a datatype property.
- Require a type for a new individual.
- Require compatible participants for assertions.
- Preserve administrative and illustrative dispositions.
- Treat document instructions as data and prevent prompt-injection behavior.

### Model Supplements And Completeness

- Verify evidence for a model-supplemented candidate.
- Run deterministic retrieval before allowing the supplemental item to be new.
- Change a supplemental create decision to unresolved when a plausible existing
  match is found.
- Reject a model supplement without exact evidence.
- Give every retained candidate and supplement exactly one disposition.
- Reject missing, duplicate, or contradictory dispositions.
- Never report complete coverage after truncation or an incomplete chunk.

### Selection And Ontology Verification

- Revalidate selection fingerprints before compilation.
- Reject a wrong-kind class, property, or individual selection.
- Reject writes to imported and FIBO sources.
- Detect applied, current-work, same-task, and retained-provenance duplicates.
- Detect normalized typed-operation duplicates and no-op extensions.
- Generate collision-checked IRIs only for verified new local entities.
- Reject stale ontology, current-work, catalog, provenance, model, prompt, or
  candidate-extractor inputs.
- Preserve exact evidence and project isolation.

### Prerequisites And Compilation

- Group a new property's domain and range classes with the property.
- Reuse an existing domain and create a model-recommended range in one bundle.
- Group a new individual's model-recommended type with the individual.
- Clearly label every model-recommended prerequisite.
- Permit a valid editable prerequisite without requiring strict direct evidence
  for its exact recommended label.
- Show missing reviewer-solvable context as `NeedsInput`.
- Revalidate and compile after the reviewer chooses a different domain, range,
  datatype, type, or match.
- Compile supported classes, properties, hierarchy, individuals, assertions,
  annotations, and SHACL patterns through existing typed services.
- Keep unsupported complex rules visible as review-only context.
- Preserve deterministic dependencies and atomic application.

### Review And Counts

- Render every completed recommendation collapsed by default.
- Show only name, edit type, confidence, status, and expand control while
  collapsed.
- Show evidence, matches, alternatives, exact changes, prerequisites, and
  editable fields when expanded.
- Keep model-recommended and document-explicit fields visually distinct.
- Prevent the browser from creating raw typed operations or overriding Kotlin
  validity.
- Report evidence-block, NLP-candidate, model-item, recommendation, and
  expanded-edit counts separately.
- Confirm that hundreds of model items may produce fewer connected
  recommendations without presenting the counts as contradictory.
- Confirm that valid work is not converted to review-only or discarded because
  of a task-wide item or edit ceiling.

### Workflow, Security, And Recovery

- Preserve existing upload, extraction, OCR, cancellation, deletion, and cleanup
  behavior.
- Never send credentials, filesystem paths, unauthorized ontology content, or
  arbitrary tools to the model.
- Do not expose full documents or prompts in ordinary logs and progress events.
- Preserve project and user isolation for candidates and retrieval IDs.
- Reuse existing private draft, proposal, approval, apply, reload, rollback, and
  provenance services.
- Prove no ontology or SHACL source changes during analysis, retrieval,
  modeling, review editing, or acceptance.
- Restore prior source state after failed approved apply verification.
- Fail visibly instead of truncating when an emergency resource threshold is
  reached.

### Offline Permanent Fixtures

Using:

- `examples/simple-ontology/documents/consumer-lending-servicing-compliance-standard.pdf`;
- `examples/simple-ontology/documents/commercial-account-and-payment-authorization-policy.pdf`;

verify from recorded fixtures that:

- candidate extraction and retrieval are byte-for-byte stable for frozen
  inputs;
- expected local, imported, same-task, prior-provenance, and FIBO matches appear
  in the top 20 where those scopes contain the expected entity;
- `Payment`, `Account`, `Payment Instruction`, `Payment Approval Record`,
  `Supporting Record`, `Invoice`, `Payment Destination`, `Consumer Loan`, `Loan
  Servicing`, `Payment Suspense`, and `Servicing Control` receive complete
  dispositions;
- relationship candidates retain their participants and supporting evidence;
- property recommendations contain domain and range or an editable
  `NeedsInput` field;
- no expected reuse target is duplicated as a new local entity;
- unsupported linked-payment aggregation and separation-of-duty meaning remains
  visible without unsafe compilation;
- exact provenance and individual-creation gates remain valid;
- no ontology change is applied automatically.

### Controlled Provider Benchmark

Run ten trials with frozen documents, ontology state, candidate inventory,
retrieval results, prompt, selected model, and provider settings. Require:

- required core concepts in at least 9 of 10 runs;
- required major relationships in at least 8 of 10 runs;
- expected unambiguous existing-entity reuse decisions in at least 9 of 10
  runs;
- duplicate new-entity recommendations for expected reuse targets in 0 of 10
  runs;
- prohibited executable recommendations in 0 of 10 runs;
- exact evidence and provenance validity in 10 of 10 runs;
- supported compilation success of at least 95%;
- unsupported complex rules kept non-executable in 10 of 10 runs;
- no automatic ontology writes in 10 of 10 runs;
- provider calls, output limits, attempts, duration, tokens when available, and
  all safe failures recorded without secrets.

The controlled benchmark is an explicit release gate. It is opt-in and does not
run as part of the ordinary offline test suite. A missing credential blocks the
benchmark but does not permit a hidden model substitution. Phase 12 extends the
existing `DocumentSemanticProviderBenchmarkTest` path and its permanent
two-document fixtures rather than adding a competing benchmark. A benchmark
waiver may be recorded only with explicit user approval.

## Acceptance Criteria

Phase 12 is complete when:

1. A pinned local NLP stage creates stable, evidence-linked candidates before
   semantic model calls.
2. Candidate extraction uses an established library or bounded local adapter
   rather than a custom general-purpose NLP framework.
3. Every retained candidate is searched against all applicable authorized
   scopes before grounded modeling.
4. Retrieval reuses current Entio semantic search, current-work/provenance
   matching, and pinned FIBO services without a second ontology index.
5. Frozen inputs produce stable candidate IDs, retrieval IDs, rankings, reasons,
   and work keys.
6. The selected model receives document evidence and bounded relevant ontology
   choices in the same grounded request.
7. Every modeled ontology item has one reuse, extend, propose-new, or unresolved
   disposition.
8. Reuse and extend selections use server-issued IDs and Kotlin revalidates their
   IRI, kind, scope, source, and fingerprints.
9. Model-supplemented items receive evidence verification and deterministic
   retrieval before any new-entity recommendation can compile.
10. New and extended properties, individuals, assertions, and constraints carry
    their required connected context.
11. Model-recommended prerequisites are grouped with the main edit, marked
    clearly, editable, and not rejected solely because the document did not use
    the exact recommended label.
12. Reviewer-solvable missing context appears as `NeedsInput`, while unsupported
    complex meaning remains visible and unsafe work remains blocked.
13. Kotlin verifies evidence, selections, identifiers, entity kinds, domains,
    ranges, datatypes, types, duplicates, source permissions, ontology rules,
    dependencies, and freshness.
14. Valid supported items compile only through the existing Phase 11.5+ semantic
    compiler and typed edit services.
15. Completed review recommendations are collapsed by default and show only
    name, edit type, confidence, status, and expansion control until opened.
16. Reviewers can edit supported matches and prerequisite fields, with Kotlin
    revalidation before acceptance.
17. Progress and review report candidate, modeled-item, recommendation, and
    expanded-edit counts separately.
18. No valid modeled item, recommendation, or edit is silently dropped or made
    review-only because of a task-wide product ceiling.
19. Existing human proposal approval, atomic apply, reload, rollback, and
    applied-document provenance remain the only ontology write workflow.
20. No source changes occur before explicit human approval.
21. The implementation adds no embeddings, vector database, second ontology
    index, external retrieval service, autonomous agent, durable task store,
    raw-RDF path, CLI ingestion surface, or VS Code ingestion surface.
22. Offline deterministic tests, existing full regression suites, and the
    approved controlled-provider benchmark pass.

## Slice 0 Audit Decisions

The ExecPlan's first slice must record these implementation decisions before
production code changes, without expanding scope:

- Which established JVM-compatible NLP library and English resource set meet
  license, distribution-size, startup-time, Java-version, and deterministic
  fixture requirements?
- Can existing current-work and retained-provenance record builders be used
  directly, or is one narrow retrieval facade required?
- Which compact hierarchy, definition, domain, range, datatype, and asserted
  type fields provide enough grounded context within provider limits?
- Which exact contracts in the pinned `phase-12-...-v1` family should carry
  distinct candidate, model-item, recommendation, and typed-edit counts?
- What exact frozen ontology state and expected top-20 matches form the Phase 12
  two-document benchmark manifest?

No answer may introduce embeddings, a vector database, automatic approval, a
new persistence layer, an external ontology service, or a second write path.

## Boundary Check

- Phase 12 is an explicitly requested new phase and therefore may update the
  current document-analysis boundary through this approved scope, spec, and
  ExecPlan.
- Kotlin remains authoritative for ontology retrieval, verification,
  compilation, and safe application.
- Ktor remains responsible for authorized temporary task orchestration, local
  NLP coordination, and provider calls.
- React remains responsible for compact presentation and explicit review input,
  not semantic validity.
- Existing RDF, OWL, SHACL, proposal, provenance, semantic search, and FIBO
  services are reused.
- The proposed local NLP dependency is narrow, server-side, pinned, and subject
  to an explicit dependency audit.
- No speculative module, database, vector store, ontology index, external
  retrieval service, apply path, CLI surface, or VS Code surface is introduced.
