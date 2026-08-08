# Feature Spec: Phase 13 Domain-Ontology-First Modeling And Full-Corpus Retrieval

## Status

Approved Phase 13 specification as of 2026-08-08. Phase 13 has not been
implemented.

Implementation remains complete through Phase 12. ExecPlan Slice 0 audit and
ADR work is authorized. Production implementation remains blocked until Slice
0 decisions are written back into an approved revision of this specification
and the ExecPlan.

This specification defines the required Phase 13 behavior. It is based on the
approved product direction in
[`phase-13-scope.md`](../architecture/phase-13-scope.md). The Phase 13 ExecPlan
must implement this specification without expanding it. No implementation may
begin beyond Slice 0 until the required architecture decisions are approved and
incorporated into revised planning documents.

## Related Documents

- [Phase 13 scope](../architecture/phase-13-scope.md)
- [Phase 5 external ontology spec](0008-phase-5-external-ontology-browsing-schema-rag.md)
- [Phase 12 ontology-grounded document analysis spec](0023-phase-12-ontology-grounded-document-analysis.md)
- [Kotlin engine guidelines](../architecture/003-kotlin-engine-guidelines.md)
- [Approved reproducible FIBO package decision](../decisions/phase-5-slice-2-approved-reproducible-fibo-package.md)

## Problem

Entio currently exposes FIBO as an always-available external catalog. Users can
browse a curated foundation, search a wider deterministic catalog, inspect
dependencies, and stage supported reuse. This is useful but disconnected from
normal ontology authoring.

The current behavior has five limitations:

1. A project does not explicitly choose whether FIBO should guide its modeling.
2. Users must remember to visit a separate external ontology panel.
3. Ordinary class, property, hierarchy, assertion, SHACL, map, and review
   workflows do not consistently check the full approved FIBO release.
4. Current lexical ranking misses useful paraphrases when project wording and
   FIBO wording differ.
5. Current external reuse treats source semantics as immutable. It does not
   provide a clear project-owned layer where users keep a FIBO IRI while
   changing labels, definitions, or other supported statements.

Phase 13 replaces that product model. FIBO becomes an optional, project-scoped
domain ontology. If activated, Entio recommends relevant FIBO classes and
properties throughout applicable human-driven modeling. Users remain in
control of every semantic decision and every applied change.

## Product Principle

> FIBO supplies trusted candidate meaning, retrieval finds useful choices,
> Kotlin verifies identity and structure, and a person decides what the project
> means.

Retrieval is advisory. It never proves equivalence, determines ontology
validity, approves a proposal, or writes project files by itself.

## Goals

- Let each project explicitly choose no domain ontology or the approved FIBO
  release.
- Keep domain-ontology activation optional and non-destructive.
- Present a selectable FIBO foundation grouped by understandable subjects.
- Let users select the complete foundation, groups, or individual classes and
  properties.
- Search every eligible class, object property, and datatype property in the
  complete approved pinned FIBO snapshot.
- Combine lexical, vector, ontology, graph, and project-context signals.
- Provide explainable recommendations throughout applicable human-driven
  modeling workflows.
- Preserve the canonical FIBO IRI for every reused entity.
- Materialize selected FIBO entities into a project-owned managed ontology
  source rather than using plain `owl:imports` as the reuse mechanism.
- Let users change every statement supported by Entio's typed edit system while
  keeping the reused entity's canonical IRI fixed.
- Retain the original FIBO source description and fingerprints separately from
  the project's current meaning.
- Distinguish unchanged reuse, customized reuse, local extension, mapping, and
  purely local modeling.
- Calculate and explain dependencies before staging reuse.
- Revalidate every recommendation and dependency in Kotlin before staging.
- Preserve the existing proposal, validation, diff, approval, atomic apply,
  reload, and rollback path.
- Preserve current document-ingestion and assistant behavior without connecting
  either workflow to the new retrieval path.
- Provide reproducible source and index builds, safe degraded behavior, and
  measurable retrieval quality.

## Non-Goals

Phase 13 does not provide:

- another domain ontology besides FIBO;
- arbitrary ontology URLs or uploads;
- simultaneous use or reconciliation of multiple domain ontologies;
- a moving FIBO `latest` release;
- live public FIBO queries during interactive authoring;
- automatic release upgrades or deprecation migration;
- automatic reuse, import, approval, or apply;
- embedding-based validation;
- automatic logical equivalence, `owl:equivalentClass`,
  `owl:equivalentProperty`, or `owl:sameAs`;
- general-purpose ontology alignment or bulk automatic alignment;
- arbitrary FIBO individual import;
- document-ingestion use of the Phase 13 index;
- native assistant use of the Phase 13 index;
- a hosted vector database, external search cluster, production database, or
  organization-wide shared index;
- a new RDF, OWL, SHACL, reasoning, proposal, or apply framework;
- full CLI or VS Code interaction parity with the React authoring experience;
- a full FIBO release-upgrade workflow;
- durable recommendation personalization or training from user feedback.

Current repository-wide boundaries and non-goals remain defined by
`AGENTS.md`.

## Proposed Behavior

The following sections define the approved source, project configuration,
managed reuse semantics, retrieval pipeline, user actions, workflow
integration, compatibility boundaries, and operational requirements that make
up Phase 13.

## Approved Source And Retrieval Assets

### Pinned FIBO snapshot

Phase 13 continues to use the currently approved offline snapshot:

- source ID: `fibo`;
- release: `master_2026Q2`;
- commit: `f59157fe156e3d91b1c045222d0a7dc06b7d78a2`;
- OMG Commons version: `1.3`;
- package schema: `entio-fibo-package-v1`;
- current catalog schema: `fibo-catalog-v1`;
- source files: `297` at the time of this specification;
- current catalog elements: `4,579` at the time of this specification.

The package manifest and checksum ledger remain the authority. Counts are
verified build expectations, not substitutes for checksum verification.

Slice 0 must determine whether this commit is an official production
publication or an Entio-approved snapshot of FIBO master. Until that evidence is
recorded, product and documentation text must call it an approved pinned
snapshot rather than a production release.

The public FIBO website and repository may be consulted only by the deliberate
offline package-update process. They are not part of project loading or an
interactive recommendation request.

### Eligible search corpus

The Phase 13 corpus contains every verified FIBO or approved dependency entity
in the pinned package that Entio can describe as one of:

- OWL class;
- OWL object property;
- OWL datatype property.

Release and provisional entities are eligible by default. Deprecated entities
remain searchable with a warning and are not placed in the recommended band
unless an exact already-reused IRI is being inspected. Informative entities are
excluded from ordinary recommendations and available only in explicit broad
browsing when package metadata supports them.

Annotation properties and individuals remain visible through existing source
descriptions where needed but are not Phase 13 reuse recommendations.

Every record includes a verified `sourceFamily` of `FIBO` or `OMG_COMMONS`.
Commons entities must never be presented as FIBO entities. Slice 0 must approve
whether Commons appears in default recommendations or only when FIBO hierarchy,
domain, range, or dependencies make it relevant. Every result shows its actual
source family.

### Local search implementation

Phase 13 uses an embedded local search implementation inside
`semantic-engine`; it does not add a Gradle module or server process.

The approved implementation family is:

- Apache Lucene Core and Analysis Common for BM25 lexical search;
- a canonical-IRI-ordered local vector artifact searched by exact cosine scan;
- Microsoft ONNX Runtime CPU for local JVM inference;
- `sentence-transformers/all-MiniLM-L6-v2` as the baseline candidate, compared
  with at least one other locally runnable, permissively licensed candidate
  when one passes the initial audit;
- the model that best meets the locked quality, platform, size, and latency
  requirements, pinned to an exact audited revision and ONNX artifact.

Approximate-nearest-neighbor search is not part of Phase 13. The current corpus
is small enough that exact scan should be preferred for stable ordering and
simpler verification. If exact scan misses the approved latency, implementation
stops and the spec must be amended rather than silently adding ANN behavior.

The dependency and model audit in ExecPlan Slice 0 must record exact library
versions, checksums, supported platforms, model revision, ONNX file,
tokenization assets, pooling behavior, normalization behavior, licenses, NOTICE
requirements, disk size, startup cost, and Java 21 compatibility. Slice 1 must
not start until this record is approved. If the audited model cannot be run
locally without custom tokenization or unsafe model loading, implementation
stops and the spec must be amended; a hosted embedding fallback is not
authorized.

The exact source package and generated index are versioned independently. The
index manifest uses a new `entio-domain-search-index-v1` contract and records:

- FIBO package fingerprint;
- semantic descriptor contract version;
- eligible entity count and fingerprints;
- Lucene and analyzer contract versions;
- embedding model ID, revision, artifact checksums, and license;
- tokenization, pooling, normalization, and input-text contract versions;
- vector dimension and similarity function;
- graph-context contract version;
- ranking contract version;
- generated file checksums.

### Index generation and verification

Index generation is an offline Gradle task. It reads the verified Phase 5
package without modifying it and writes a separately fingerprinted Phase 13
package beneath
`external-ontologies/domain-search/fibo/master_2026Q2/`.

The Phase 5 `manifest.yaml`, `catalog-metadata-v1.json`, catalog ordering,
checksum ledger, and package fingerprint remain byte-for-byte unchanged. This
is required because Phase 12 includes the existing manifest and catalog
metadata in frozen retrieval work keys.

The verifier must:

- verify the existing source package first;
- regenerate descriptors, lexical documents, vectors, and manifests in a
  temporary directory;
- compare deterministic artifacts byte for byte where the approved libraries
  permit it;
- compare vectors within an approved numerical tolerance where bit-for-bit
  output is not portable;
- verify entity identity and record fingerprints against the semantic engine;
- reject missing, extra, duplicate, corrupt, stale, or wrong-dimension records;
- confirm no network access is required;
- report clearly which artifact or contract failed.

Generated binary search files and model assets may be committed only after the
license, size, and reproducibility audit approves them.

## Project Domain Profile

### Configuration shape

The durable project selection lives in the Entio-owned sidecar
`.entio/domain-profile.yaml`:

```yaml
schema: entio-domain-profile-v1
sourceId: fibo
release: master_2026Q2
packageFingerprint: 015142b94819291379b89c3bba92048f037f1d8e635d3f1342d29f0f02f374ad
managedSourceId: fibo-reuse
```

Absence of the sidecar means no domain ontology is active. Phase 13 does not
add a second source value. The managed source and provenance locations are
fixed conventions derived by the loader:

- `ontology/fibo-reuse.ttl`;
- `.entio/domain-reuse/events-v1.jsonl`.

The loader validates the schema, exact source, release, package fingerprint,
managed source ID, fixed paths, and safe placement within the project root. If
the profile is valid, `ProjectLoader` augments the loaded project with the
managed Turtle source; activation does not rewrite the user's hand-authored
`entio.yaml` or require a duplicate `ontologySources` entry.

### Activation

Activation is an explicit project-configuration operation, not an ontology
proposal. After a confirmation screen, the server:

1. verifies the approved FIBO package and Phase 13 index;
2. verifies that the fixed profile, managed-source, provenance, and transaction
   paths are safe and compatible with any existing files;
3. previews the exact profile sidecar and empty managed source;
4. prepares temporary files and a recovery journal containing original and
   intended hashes;
5. places the empty managed source and atomically replaces the profile sidecar
   as the activation commit point;
6. reloads the project and verifies the profile;
7. marks the journal committed and removes it, or deterministically restores
   the previous state after failure.

Activation writes no RDF statements. The new managed Turtle source is empty.
Activation does not select foundation concepts or stage ontology changes.
An orphaned empty managed source created before the profile commit point is
safe to remove during recovery. A non-empty or unexpected file is never
silently overwritten or deleted.

The browser submits a server-issued activation token bound to the project
configuration fingerprint and approved package fingerprint. It cannot submit a
trusted release, arbitrary path, or package fingerprint.

### Deactivation

Deactivation is allowed only when:

- the managed reuse source contains no statements;
- no local ontology statement depends on a reused FIBO entity recorded by the
  profile;
- no current staged item or proposal depends on the profile;
- the provenance sidecar contains no applied reuse record that would become
  orphaned, or all such records represent fully removed reuse and may be
  retained as inactive history;
- document ingestion and assistant compatibility checks succeed.

If these conditions hold, Entio previews deactivation and uses the same
journaled profile-commit protocol to remove the profile sidecar. It may then
remove an empty generated managed source. Applied history is retained as
inactive history. Otherwise Entio explains the blocking dependencies and
requires the user to remove them through normal proposals first.

### Configuration ownership

`semantic-engine` owns profile parsing, validation, preview data, transaction
and recovery behavior, and loaded-project augmentation. `web-server` owns
project authorization, server-issued tokens, idempotency, and route adaptation.
React renders the preview and confirmation. No client writes the sidecar,
`entio.yaml`, or the managed source directly.

## Managed Reuse And Provenance

### Managed project source

Selected FIBO entities are materialized into the project's configured
`ontology/fibo-reuse.ttl` source. This is a normal project ontology source for
parsing, validation, reasoning, diff, apply, reload, and rollback, but it has a
special managed role:

- only canonical IRIs from the active approved source may be introduced as
  reused entity subjects;
- FIBO package files remain immutable;
- the managed source is changed only through existing typed proposals;
- the source contains project-owned statements, not an `owl:imports` of all
  FIBO;
- the source does not automatically change when Entio later supports another
  release.

### Initial materialization

For each explicitly selected entity, a new Phase 13 preparer and translator
prepare the bounded explicit source statements that Entio's typed editing and
semantic description systems can preserve. The current Phase 5
`ExternalProposalIntentTranslator` remains unchanged for compatibility.

- class or property declaration;
- preferred label and bounded alternate labels;
- definition and supported annotations;
- directly required superclass or superproperty statements;
- property domain and range statements;
- required declaration statements for referenced entities;
- approved supporting annotation vocabulary statements only when the project
  representation needs them.

Every candidate is classified as `CompleteSupportedMaterialization`,
`PartialMaterialization`, or `UnsupportedForReuse`. A partial result explicitly
lists omitted source axioms and requires acknowledgement in the proposal
preview; it must never be described as a complete import. Unsupported meaning
blocks the affected selection. Entio does not copy arbitrary restrictions,
anonymous class expressions, rules, reference individuals, or unsupported OWL
constructs into the managed source.

### Dependency closure

Dependency calculation begins with the user-selected entity and includes:

- its declaration;
- explicitly selected source annotations;
- direct named parents or superproperties required by the selected structure;
- named domain and range entities;
- recursively required declarations for those named entities;
- approved ontology or Commons vocabulary identifiers necessary to interpret
  copied statements.

The dependency graph is deterministic, cycle safe, sorted by canonical IRI,
and classified as:

- explicitly selected;
- required structural dependency;
- already present unchanged;
- already present customized;
- conflicting;
- unsupported;
- missing.

No module-wide `owl:imports` is added automatically. Module information remains
provenance and browsing context.

### Bounded batching

One recommendation request returns at most `10` default results and `50` broad
search results. One staging request contains at most `20` explicitly selected
entities, matching the existing bounded typed-edit batch. Its dependency
closure is also limited to `100` entities, `2,000` generated RDF statements,
`2 MiB` of prepared preview data, a traversal depth of `16`, and `10 seconds`
of preparation time. Exceeding any hard bound blocks the affected batch with a
specific error; Entio does not silently truncate semantic meaning. The complete
foundation **Select all** action creates a deterministic import plan partitioned
into review batches of at most `20` explicit selections. Dependencies may
expand a batch but must satisfy existing graph-change and payload safeguards.

Each batch is separately previewed and approved. Completed batches remain
applied if a later batch is rejected; the UI shows partial progress clearly.
Entio does not silently discard selections that exceed one batch.

### Editable project meaning

After reuse is applied, the project may edit every statement that the current
typed-edit system supports. The canonical entity IRI is locked.

The entity details view separates:

- **Project meaning** — current applied or proposed project statements;
- **FIBO source meaning** — the verified snapshot from the pinned release;
- **Differences** — added, removed, and changed annotations and logical
  statements.

Entio classifies a customization as:

- unchanged;
- annotation-only;
- logical structure changed;
- source entity deprecated;
- source snapshot unavailable or invalid.

A logical difference produces a prominent advisory that project meaning may no
longer match unmodified FIBO. It does not fail validation unless an existing
deterministic rule is violated.

### Provenance sidecar

Operational provenance is stored in the configured project-relative JSON Lines
file under `.entio/domain-reuse/`. It is not asserted as ontology truth.

Each successfully applied reuse or customization event records:

- record schema `entio-domain-reuse-provenance-v1`;
- source ID, release, package fingerprint, and record fingerprint;
- canonical IRI and entity kind;
- source ontology and module;
- source statement fingerprint and bounded source snapshot;
- dependency-set fingerprint;
- target managed source ID;
- proposal ID, applied change-set ID, actor ID, and apply timestamp supplied by
  the existing authorized apply event;
- baseline and resulting project fingerprints;
- project statement fingerprint;
- customization classification;
- prior provenance record ID when applicable.

The timestamp is audit data and never enters deterministic semantic ranking or
validation.

The sidecar update participates in a journaled apply protocol. Entio prepares
the complete next managed-source and provenance bytes in temporary files,
records original and intended hashes in a recovery journal, replaces both
targets, reloads and verifies the project and provenance, and only then marks
the transaction committed. Startup recovery uses the journal and hashes to
finish or restore an interrupted operation deterministically. This is an
observable all-or-nothing contract, not a claim that two filesystem renames
are intrinsically atomic. A failed apply never leaves unprovenanced managed
statements.

### Removal

Removing a reused entity removes project-owned statements from the managed
source only after the existing deletion dependency review succeeds. It never
changes the pinned package. The provenance sidecar receives a removal event and
retains history.

## Foundation Experience

### Foundation groups

Phase 13 defines these initial presentation groups from the current approved
curated seeds and their verified members:

1. Agreements and contracts
2. Documents, identifiers, and classifications
3. Parties, people, organizations, and roles
4. Dates, events, and schedules
5. Amounts, quantities, currencies, and units
6. Ownership and control
7. Products, services, payments, and schedules
8. Places and real property

Group membership is generated into
`external-ontologies/domain-search/fibo/master_2026Q2/foundation-profile-v1.json`,
reviewed as product content, versioned, and checksum protected. The browser
does not own this list.

### Foundation reads

The foundation response contains:

- source and release identity;
- group IDs, labels, descriptions, and counts;
- server-issued selection IDs for eligible entities;
- entity kind, label, definition, source module, and reuse status;
- dependency-summary counts;
- unavailable or unsupported-member warnings;
- pagination for group members.

### Foundation plan

The user may select all groups, selected groups, or individual entities. Entio
returns a frozen plan containing ordered batches, explicit selections,
dependencies, conflicts, unsupported items, estimated statement counts, and
target source. A plan with conflicts, missing dependencies, or unsupported
required meaning cannot be staged until the user changes the selection.

## Hybrid Recommendation Behavior

### Structured intent

Every participating workflow sends a `DomainModelingIntent` containing only the
context applicable to that action:

- project ID and operation kind;
- requested entity kind;
- draft label, alternate wording, and definition;
- current entity selection ID when editing;
- server-resolved parent, domain, range, datatype, subject, predicate, object,
  target class, or property path IDs;
- nearby project entity IDs;
- current source target;
- language preference;
- project, profile, ontology, staging, proposal, package, and index
  fingerprints.

The client may propose text and selected project IDs. The server resolves all
semantic context and constructs the authoritative intent.

### Intent operation kinds

Phase 13 supports fixed operation kinds for:

- global semantic search;
- create class;
- create object property;
- create datatype property;
- create individual type selection;
- edit label or definition;
- edit class hierarchy;
- edit property hierarchy;
- edit domain;
- edit range or datatype;
- add assertion or value;
- delete or replace entity;
- proposal reuse review;
- SHACL target class;
- SHACL property path;
- SHACL class or datatype constraint;
- ontology-map related search;
- reasoning-workspace related search;
- foundation expansion.

Unknown strings are rejected. Fixed states use an enum or sealed contract.

### Retrieval stages

For each intent, Entio:

1. normalizes bounded search text deterministically;
2. retrieves up to `100` lexical candidates;
3. creates one local query embedding, performs an exact cosine scan over all
   eligible vectors in canonical-IRI order, and keeps up to `100` vector
   candidates;
4. unions candidates by canonical IRI;
5. removes ineligible entities;
6. calculates ontology and project-context features;
7. computes a normalized final score;
8. applies stable tie-breaking;
9. assigns confidence bands and permitted actions;
10. returns at most `10` default recommendations or `50` explicit broad-search
    results.

### Ranking contract

The initial `domain-ranking-v1` score is a normalized weighted combination:

- lexical relevance: `35%`;
- vector similarity: `25%`;
- entity-kind and structural compatibility: `20%`;
- project graph and already-reused context: `15%`;
- foundation and module context: `5%`.

Hard incompatibility is evaluated before scoring and cannot be overcome by text
or vector similarity. Missing optional context contributes zero rather than a
penalty. Deprecated entities receive a final penalty and warning unless the
intent inspects that exact reused IRI.

Each component is normalized using versioned rules documented in tests. Final
ties are broken by:

1. eligibility band;
2. final normalized score descending;
3. exact preferred-label match before other matches;
4. already-reused before not-yet-reused;
5. entity kind;
6. preferred label using root-locale comparison;
7. canonical IRI;
8. source module IRI.

Vector values and raw internal score components are not accepted from clients.

### Hard ineligibility

A candidate is ineligible for a reuse action when:

- its IRI is absent from the pinned release;
- release, package, record, or index fingerprints do not match;
- its entity kind is incompatible with the requested operation;
- an object property is requested as a datatype property or the reverse;
- a required domain, range, datatype, or source condition is deterministically
  incompatible;
- the entity is informative, malformed, missing, or unsupported;
- required dependencies cannot be resolved;
- selecting it would create a prohibited source cycle or duplicate conflict;
- the active project profile is absent or stale;
- authorization or freshness checks fail.

An ineligible candidate may appear in broad browsing only when a safe
plain-language explanation is useful. It cannot produce a reuse action token.

### Warnings that do not make a result ineligible

- deprecated source entity;
- additional dependency cost;
- source module not yet used;
- local concept with similar wording;
- project customization differs from source;
- incomplete optional domain or range context;
- low retrieval confidence.

### Confidence bands

Recommendations use `Strong`, `Possible`, or `Low` bands derived from approved
benchmark thresholds. They do not use probability language. `Low` results are
collapsed by default and never block local creation.

### Explanation contract

Every result explains only verified contributing signals, for example:

- exact preferred-label or alternate-label match;
- definition or paraphrase similarity;
- compatible class or property kind;
- matching domain and range;
- parent already reused;
- connected project neighbor;
- foundation membership;
- source module already used;
- deprecation or dependency warning.

An explanation must name the relevant project or FIBO entity when doing so is
safe. It must not claim equivalence, correctness, or validation success.

### Recommendation identity and freshness

The server issues an opaque recommendation ID derived from:

- authorized project and user scope;
- normalized intent fingerprint;
- project ontology and current-work fingerprints;
- active profile fingerprint;
- FIBO package and index fingerprints;
- candidate record fingerprint;
- ranking and result-contract versions;
- availability mode.

Recommendation state is bounded and session scoped. Each project/user scope
holds at most `500` recommendation records and `50` frozen plans. Both expire
`30 minutes` after creation and are invalidated by server restart. Cleanup
evicts expired entries first, then the oldest creation sequence; it never uses
scores or user text as an eviction key. Staging resolves the ID and repeats
identity, kind, dependency, authorization, duplicate, and freshness checks.
Expired, evicted, restarted, or fingerprint-stale IDs fail with
`domain-recommendation-stale` and a refresh hint.

### Degraded modes

Retrieval reports one of:

- `Full` — lexical, vector, ontology, graph, and project signals available;
- `LexicalStructural` — vector generation or vector index unavailable;
- `Unavailable` — domain retrieval cannot run.

`LexicalStructural` remains functional and visible. `Unavailable` does not
block valid local modeling but displays that domain reuse was not checked. New
FIBO reuse cannot be staged when source verification or the complete identity
index is unavailable.

## User Actions From A Recommendation

### Reuse

Materialize the source entity and dependencies into the managed reuse source
using its canonical IRI and approved initial statements.

### Reuse and customize

Prepare the reuse materialization and user-entered supported changes in one
reviewable batch. The preview separates source-derived and project-authored
statements.

### Extend locally

Create a local class or property IRI and add a supported superclass or
superproperty relation to the selected FIBO entity. The required FIBO entity is
also reused if not already present.

### Map

Keep the local entity and add an annotation mapping using one of:

- `skos:closeMatch`;
- `skos:relatedMatch`.

Phase 13 does not offer `skos:exactMatch` or OWL logical equivalence from a
retrieval result. These are IRI-valued annotation assertions: they have no OWL
reasoning effect and do not require the target entity to be materialized merely
because a mapping names it. The target IRI must still resolve to a verified
record in the active package.

### Continue locally

Continue the original valid local action. The recommendation produces no
ontology statement.

### Not relevant

Dismiss the result for the current intent. Dismissal is session scoped and does
not alter validation, retrieval assets, or future unrelated intents.

## Required Workflow Integration

The React web workbench must integrate the shared recommendation behavior in
all workflows below when FIBO is active.

The ExecPlan integration completeness matrix is authoritative for delivery.
For every listed surface, implementation must identify the intent producer,
recommendation trigger, permitted actions, inactive-profile behavior, degraded
behavior, staging path, and focused tests. A surface is not complete merely
because the shared retrieval service exists.

### Explore and global semantic search

One search experience returns local, imported, reused, customized, extended,
mapped, and available-FIBO results with clear locality and status labels.

### Class creation

Recommendations update from the bounded draft label, definition, intended
parent, and nearby project context. The form never silently changes its edit
type.

### Object- and datatype-property creation

Recommendations include property kind, domain, range or datatype,
superproperty, and inverse context. Cross-kind candidates are not actionable.

### Individual creation

Recommendations help choose an applicable reused or available FIBO class as the
individual's type and appropriate properties for assertions. FIBO reference
individuals are not importable in Phase 13.

### Labels, definitions, and semantic annotations

Editing a local entity checks for close FIBO concepts. Editing a reused entity
shows source-versus-project differences and may advise local extension when the
meaning diverges.

### Hierarchy

Superclass and superproperty pickers include eligible FIBO candidates and
explanations. Existing cycle and reasoning checks remain authoritative.

### Domain, range, and datatype

Property editors include compatible FIBO classes and datatypes. Class details
may show applicable FIBO properties whose asserted domains are compatible.

### Assertions and values

Property selection uses subject type, direction, domain, range, and target type
context. Retrieval never invents values.

### Deletion and replacement

Dependency review may include FIBO replacement or broader-parent candidates.
Removing reuse means removing project statements, never source-package data.

### Shared staging and proposal review

New and substantially changed semantic edits receive a domain-reuse check. A
reviewer may replace a local creation with a supported reuse, customize,
extension, or mapping action before approval. Changing the action regenerates
the preview; it does not rewrite a proposal client side.

### SHACL authoring

Target class, property path, class constraint, and datatype selectors include
eligible domain candidates. SHACL validity remains deterministic.

### Ontology map

The map offers a read-only related-FIBO action for the selected node. Available
FIBO nodes appear in a separate bounded result panel, not in asserted layout,
until applied.

### Reasoning workspace

Users may search related FIBO concepts from an asserted or inferred fact.
Retrieval does not create or alter inferences.

### Foundation expansion

Domain administration and ordinary search can prepare additional reuse plans
outside the initial foundation.

### Debouncing and cancellation

Typing-triggered requests wait `300 ms` after the last meaningful change. A
request requires at least two normalized alphanumeric characters unless a
structured entity selection supplies sufficient context. Superseded requests
are cancelled or ignored by request ID. Rich reranking runs when kind and
structural context are known.

## CLI And VS Code Compatibility

The CLI remains thin and gains machine-readable commands for:

- domain-source availability;
- profile validation and activation preview;
- foundation groups and plan preview;
- full-corpus search and recommendations;
- source-versus-project description;
- reuse dependency preview;
- reuse/customize/extend/map proposal preparation;
- domain-profile status and migration diagnostics.

Configuration activation apply is exposed to the web server through a reusable
Kotlin service. A CLI mutation command is not required in Phase 13.

The VS Code extension must:

- understand inactive and active domain profiles;
- stop presenting FIBO as silently always selected;
- use the new full-corpus read contracts for its existing external area;
- continue to stage reuse through Kotlin-owned proposal commands;
- show source-versus-project status for reused entities where its current
  details surface supports it.

Full contextual recommendation cards in every VS Code editor are not required.
No TypeScript client calculates ranking or dependencies.

## Document Ingestion And Assistant Compatibility

### Document ingestion

New Phase 12 tasks continue to use `DocumentOntologyRetrievalService`, its
current deterministic FIBO catalog adapter, existing selection IDs, and frozen
work-key contract. It does not call the Phase 13 embedding or recommendation
service.

The Phase 13 package migration must keep the old catalog files and fingerprints
available to document ingestion, or provide an audited compatibility adapter
that returns exactly the existing Phase 12 meaning and ordering.

### Native assistant

The assistant continues to use its existing bounded FIBO context request and
`FiboWebService` compatibility behavior. It does not receive Phase 13 vectors,
recommendation tools, profile mutation tools, or unrestricted full-corpus
context.

Assistant behavior for projects without an active domain profile remains
unchanged during Phase 13 so this phase does not silently alter existing AI
prompts. A later phase must decide whether assistant FIBO context should honor
profile activation.

## Inputs

Phase 13 accepts these bounded input categories:

- an explicit activation/deactivation confirmation token;
- server-issued foundation group, element, plan, recommendation, and dependency
  IDs;
- a structured operation kind;
- draft labels, alternate wording, and definitions;
- server-resolvable local entity and source IDs;
- explicit user action: reuse, customize, extend, map, continue locally, or
  dismiss;
- local IRI and supported typed fields when creating an extension;
- page, result-limit, kind, module, maturity, and broad-search filters;
- existing staged proposal choices where authorized.

Text fields use existing limits where available. New retrieval text is limited
to `2,000` Unicode characters after transport decoding, with at most `256`
normalized model word pieces passed to the embedding model. Lists are sorted,
deduplicated, and bounded before indexing or hashing.

Clients never supply trusted source axioms, source fingerprints, vector values,
ranking scores, dependency closure, package paths, or arbitrary FIBO IRIs for a
mutation.

## Outputs

Versioned structured outputs include:

- source availability and index health;
- active domain profile and migration status;
- configuration activation/deactivation preview;
- foundation groups and members;
- frozen foundation import plans and batches;
- source and project semantic descriptions;
- recommendation lists and confidence bands;
- verified match reasons and warnings;
- dependency previews;
- permitted actions;
- degraded-mode status;
- source-versus-project differences;
- prepared typed proposal items;
- structured errors with stable codes;
- benchmark and index-verification reports.

Responses do not expose filesystem paths outside safe project-relative display,
raw vectors, model tensors, secrets, internal stack traces, or unbounded source
content.

## Validation Behavior

### Configuration validation

Entio reports deterministic issues for:

- unsupported source or release;
- package fingerprint mismatch;
- missing or malformed profile fields;
- unsafe or absolute managed paths;
- duplicate or inconsistent managed source;
- missing managed Turtle source;
- invalid provenance sidecar;
- inactive profile with managed reuse dependencies;
- unsupported historical release.

### Package and index validation

Entio reports deterministic issues for:

- missing, corrupt, or wrong-release package;
- failed source checksum or license inventory;
- missing or extra search entity;
- duplicate canonical IRI;
- descriptor/index fingerprint mismatch;
- embedding model or tokenizer mismatch;
- wrong vector dimension or invalid numeric value;
- stale lexical or vector index;
- unsupported index schema;
- incomplete foundation profile.

### Recommendation validation

Before returning an actionable recommendation, Entio validates:

- active profile and authorization;
- operation kind and requested entity kind;
- source and index freshness;
- candidate identity and kind;
- structural compatibility;
- project duplicate and no-op state;
- source, staging, and proposal fingerprints;
- dependency resolvability;
- result and explanation consistency.

### Proposal validation

Before staging, Entio repeats all relevant checks and verifies:

- recommendation ID belongs to the current project and user;
- explicit action is permitted;
- target source is the managed reuse source where required;
- canonical FIBO IRI is unchanged;
- local extension IRI belongs to the configured local namespace;
- mapping predicate is approved;
- every project-authored statement is supported by typed operations;
- source snapshots and provenance records are complete;
- the combined proposal passes existing validation, diff, and round-trip
  checks.

Embedding similarity is never a validation rule.

## Error Behavior

Expected failures use structured codes and safe messages.

Representative codes include:

- `domain-profile-inactive`;
- `domain-profile-invalid`;
- `domain-profile-stale`;
- `domain-activation-stale`;
- `domain-deactivation-blocked`;
- `domain-package-unavailable`;
- `domain-package-invalid`;
- `domain-index-unavailable`;
- `domain-index-invalid`;
- `domain-vector-degraded`;
- `domain-query-invalid`;
- `domain-query-too-large`;
- `domain-operation-unsupported`;
- `domain-recommendation-not-found`;
- `domain-recommendation-stale`;
- `domain-candidate-ineligible`;
- `domain-kind-mismatch`;
- `domain-dependency-missing`;
- `domain-dependency-unsupported`;
- `domain-reuse-conflict`;
- `domain-canonical-iri-locked`;
- `domain-managed-source-invalid`;
- `domain-provenance-write-failed`;
- `domain-migration-required`.

Failures obey these rules:

- activation and deactivation restore previous configuration files;
- search failure never changes project state;
- vector failure uses lexical-structural mode when safe;
- package identity failure blocks new reuse but not local modeling;
- stale recommendations require refresh and cannot be staged;
- unsupported dependencies block only the affected reuse selection;
- provenance failure restores ontology and provenance files;
- browser errors never expose stack traces, credentials, raw model paths, or
  unrestricted project content.

## Authorization, Privacy, And Security

- All profile, recommendation, plan, and staging routes are project scoped.
- Read routes require current project authorization.
- Configuration and proposal mutations require the current authenticated
  development identity and existing idempotency rules.
- Server-issued IDs are opaque, scoped, bounded, and freshness checked.
- Project text and local definitions are embedded locally only.
- No project text is sent to OpenAI, Hugging Face, or another external service
  by Phase 13 retrieval.
- Model and index assets are loaded only from checksum-approved local paths.
- RDF labels, definitions, and annotations are untrusted display text.
- Package paths, profile paths, and generated outputs cannot escape approved
  roots.
- Search requests and model inference have input, result, time, memory, and
  concurrency bounds.
- Logs record fingerprints and counts rather than raw project text or vectors.

## Performance And Quality Requirements

The final values below are acceptance targets for the existing `4,579`-element
corpus on the repository's supported Java 21 development baseline. Slice 0 may
propose amendments only with measured evidence and spec approval.

### Performance

- verified index load: no more than `3 seconds` on a warm filesystem;
- first local embedding request after model load begins: no more than `5
  seconds`;
- warm default recommendation p95: no more than `300 ms`;
- warm lexical-structural recommendation p95: no more than `150 ms`;
- explicit broad-search p95: no more than `750 ms`;
- peak additional server memory for one loaded model and index: no more than
  `512 MiB`;
- committed model plus Phase 13 index assets: no more than `250 MiB`;
- offline clean index generation: no more than `15 minutes` on the supported
  development baseline;
- at least `8` concurrent bounded warm recommendation requests without wrong-
  project leakage or p95 above `1 second`.

### Retrieval quality

The benchmark corpus is versioned and divided before ranking calibration into:

- a development set used to build examples and inspect failures;
- a regression set used during implementation;
- a locked acceptance set whose relevance judgments are not used to tune
  weights or confidence thresholds.

Each query records the allowed entity kinds, all accepted relevant IRIs,
explicitly irrelevant hard negatives, whether no match is correct, and a short
judgment rationale. Two reviewers must agree on acceptance-set judgments;
disagreement is resolved and recorded before the set is locked. Benchmark
changes require a version bump and an audit note. Report per-kind and
FIBO-versus-OMG-Commons metrics as well as aggregate metrics.

The locked acceptance set must achieve:

- recall@10 of at least `0.85` for queries with one or more approved relevant
  IRIs;
- precision@3 of at least `0.70`;
- requested entity-kind correctness of `1.00` for actionable results;
- object/datatype property cross-kind action errors of `0`;
- deterministic hard-incompatibility suppression of `1.00`;
- explanation factual accuracy of `1.00` for emitted structured reasons;
- no-match correctness of at least `0.80` on approved no-match cases;
- identical final ordering for repeated frozen-input tests on the same
  supported platform and dependency versions;
- lexical degraded-mode recall@10 no worse than the current Phase 5 benchmark
  for its supported query set.

The benchmark includes exact labels, alternate labels, abbreviations,
paraphrases, financial terms, directional opposites, kind ambiguity, domain and
range conflicts, deprecated entities, no-match cases, and customized reuse.

## Migration Behavior

### Existing projects

No existing project is automatically activated. Existing RDF statements remain
unchanged.

When Entio detects applied FIBO IRIs in an inactive project, it reports one of:

- `NoExistingReuse`;
- `ExistingReuseRecognized` with the verified current package;
- `ExistingReuseAmbiguous`;
- `ExistingReuseUnsupported`.

For recognized use, Entio offers an explicit migration preview that:

- adds the domain profile;
- chooses or creates the managed source;
- moves no existing statements unless the user separately approves a normal
  semantic proposal;
- seeds provenance only from deterministic source and project evidence;
- records uncertainty rather than inventing a historical release.

### Existing external APIs

The current `/external/fibo/*` routes and CLI commands remain available for the
entirety of Phase 13 for assistant, document, VS Code, and migration
compatibility. Removing them is outside Phase 13. Human web authoring moves to
new `/domain-ontologies/*` and `/domain-recommendations/*` contracts.

Compatibility adapters must preserve existing Phase 12 ordering and assistant
payload meaning. They must not silently swap hybrid ranking into those flows.

### Existing open work

Current staged items and proposals retain their original baselines. If profile
activation changes source configuration or project fingerprint in a way that
makes them stale, Entio marks them stale using existing behavior. It does not
rewrite them.

## Web Contract Families

The Ktor boundary adds versioned `v1` routes under the existing API prefix:

- `GET /api/v1/domain-ontologies`;
- `GET /api/v1/projects/{projectId}/domain-ontology`;
- `POST /api/v1/projects/{projectId}/domain-ontology/activation-preview`;
- `POST /api/v1/projects/{projectId}/domain-ontology/activate`;
- `POST /api/v1/projects/{projectId}/domain-ontology/deactivation-preview`;
- `POST /api/v1/projects/{projectId}/domain-ontology/deactivate`;
- `GET /api/v1/projects/{projectId}/domain-ontology/foundation`;
- `POST /api/v1/projects/{projectId}/domain-ontology/foundation-plans`;
- `GET /api/v1/projects/{projectId}/domain-ontology/foundation-plans/{planId}`;
- `POST /api/v1/projects/{projectId}/domain-recommendations`;
- `GET /api/v1/projects/{projectId}/domain-recommendations/{recommendationId}`;
- `POST /api/v1/projects/{projectId}/domain-recommendations/{recommendationId}/dependency-preview`;
- `POST /api/v1/projects/{projectId}/domain-recommendations/{recommendationId}/stage`;
- `GET /api/v1/projects/{projectId}/domain-reuse/{entityId}`;
- `GET /api/v1/projects/{projectId}/domain-migration`;
- `POST /api/v1/projects/{projectId}/domain-migration/preview`.

The exact DTO names may differ, but responsibilities, bounds, and trust rules
must remain. Long-running index generation is not a normal web route.

## Test Cases

### Configuration and activation

- Load a project with no domain-profile sidecar.
- Reject unsupported source, release, or fingerprint.
- Preview activation without changing files.
- Activate FIBO and create only an empty managed source.
- Confirm activation leaves `entio.yaml` byte-for-byte unchanged.
- Reload the activated project successfully.
- Reject conflicting, unsafe, or unexpected fixed-path files.
- Restore all files after activation verification failure.
- Recover deterministically after interruption before and after the profile
  commit point.
- Block deactivation with reused or staged dependencies.
- Deactivate an empty profile without erasing history.

### Package and index

- Verify the exact FIBO package and Commons dependency set.
- Classify and display every eligible record as FIBO or OMG Commons.
- Confirm Phase 5 manifest, catalog metadata, checksums, ordering, and package
  fingerprint remain byte-for-byte unchanged.
- Generate all eligible descriptors from the full package.
- Generate and reload lexical and vector indexes offline.
- Prove exact vector scan considers the complete eligible vector set before
  bounded result selection.
- Reject duplicate, missing, extra, corrupt, stale, or wrong-dimension records.
- Verify model, tokenizer, pooling, and normalization checksums.
- Confirm the interactive path performs no network calls.
- Reproduce index artifacts within the approved numerical contract.

### Foundation

- List all approved foundation groups deterministically.
- Select one entity, one group, multiple groups, and all groups.
- Distinguish explicit selections from dependencies.
- Partition a complete selection into stable batches of at most 20 explicit
  entities.
- Report conflicts and unsupported dependencies before staging.
- Enforce explicit-selection, dependency, statement, preview-byte, depth, and
  preparation-time limits without truncation.
- Resume after some foundation batches have already been applied.

### Retrieval

- Exact label, alternate label, definition, IRI, and abbreviation queries.
- Plain-language paraphrase retrieved by vector search.
- Strong lexical result retained when vector search is unavailable.
- Class, object-property, and datatype-property hard filtering.
- Domain, range, hierarchy, and project-neighbor reranking.
- Already-reused preference and customized-source matching.
- Deprecated and informative behavior.
- Stable deduplication and tie-breaking.
- Low-confidence and no-match behavior.
- Accurate structured explanations.
- Default 10 and broad 50 result bounds.
- Cancellation and stale recommendation rejection.
- Cross-project and cross-user recommendation ID rejection.
- Recommendation and plan TTL, capacity, deterministic eviction, and restart
  invalidation.

### Reuse, customization, extension, and mapping

- Reuse a class, object property, and datatype property with canonical IRIs.
- Reuse and customize label and definition in one proposal.
- Distinguish complete, partial, and unsupported materialization and require
  acknowledgement of every omitted axiom for partial materialization.
- Change supported hierarchy, domain, and range statements.
- Prevent canonical IRI replacement.
- Show source and project meaning separately.
- Classify annotation-only and logical customization.
- Create a local subclass and local subproperty.
- Create `skos:closeMatch` and `skos:relatedMatch` mappings.
- Confirm mappings have annotation semantics and do not require target
  materialization or create inferred equivalence.
- Reject exact/equivalent mappings from retrieval.
- Remove project reuse without changing package assets.
- Atomically restore ontology and provenance after failure.

### Workflow integration

- Explore/global search.
- Class creation.
- Object- and datatype-property creation.
- Individual type and assertion property selection.
- Label, definition, and semantic annotation editing.
- Class and property hierarchy editing.
- Domain, range, and datatype editing.
- Assertion and value editing.
- Deletion and replacement.
- Shared staging and proposal review.
- SHACL target, path, class, and datatype selection.
- Ontology-map related search.
- Reasoning-workspace related search.
- Foundation expansion.
- Disabled-profile behavior in every workflow.

### Compatibility and boundaries

- Phase 12 document retrieval produces unchanged ordered results and work keys.
- Assistant bounded FIBO context remains unchanged.
- Existing `/external/fibo/*` compatibility routes remain bounded.
- CLI remains presentation-only.
- VS Code does not rank or resolve dependencies.
- React does not trust raw IRIs, scores, or source statements.
- Retrieval never decides validation or reasoning results.
- Local modeling remains possible in unavailable mode.
- No automatic approval or apply occurs.

### Performance and quality

- Run the versioned benchmark corpus in full and lexical-degraded modes.
- Record recall@10, precision@3, kind correctness, no-match correctness, and
  explanation accuracy.
- Measure clean generation, index load, cold inference, warm p95, broad p95,
  memory, asset size, and eight-request concurrency.
- Fail verification when approved thresholds are missed.

## Acceptance Criteria

Phase 13 is accepted only when:

1. Projects explicitly support no domain ontology or the exact approved FIBO
   profile.
2. Activation is previewed, explicit, atomic, and adds no ontology statements.
3. FIBO source and generated retrieval assets verify offline.
4. Users can select complete, grouped, or individual foundation content.
5. Foundation plans distinguish explicit selections, dependencies, conflicts,
   and stable batches.
6. Every eligible class, object property, and datatype property in the pinned
   package is searchable.
7. Hybrid retrieval combines the five approved signal families and provides a
   safe lexical-structural fallback.
8. All specified human-driven web workflows consult the shared recommendation
   service when the profile is active.
9. Recommendations are bounded, explainable, optional, versioned, authorized,
   and freshness checked.
10. Hard structural incompatibility cannot be overcome by lexical or vector
    similarity.
11. Users can reuse, reuse and customize, extend, map, continue locally, or
    dismiss where the action is supported.
12. Reused entities retain canonical FIBO IRIs, and the IRI cannot be edited.
13. All other currently supported typed statements remain editable through the
    normal proposal workflow.
14. Project meaning, pinned source meaning, and their differences remain
    separately inspectable.
15. Applied reuse and customization have complete project-local provenance.
16. Journaled ontology and provenance writes recover to one verified state
    after failure or interruption.
17. FIBO source assets are never modified.
18. No recommendation or selection bypasses preview, validation, diff, human
    approval, atomic apply, reload, or rollback.
19. Existing projects are not automatically activated or semantically changed.
20. Document ingestion and the native assistant remain on their existing
    retrieval contracts.
21. CLI and VS Code compatibility does not move semantic policy into clients.
22. Security, privacy, package, index, migration, regression, quality, and
    performance tests pass.
23. The approved benchmark meets every stated quality and performance target.
24. Required ADRs and slice completion records are present.
25. `./gradlew test`, `./gradlew build`, `./gradlew check`, web tests, web build,
    web end-to-end tests, and VS Code tests pass.
26. Activation never rewrites `entio.yaml`, and Phase 13 assets do not change
    Phase 5 or Phase 12 package fingerprints, catalog ordering, or work keys.
27. FIBO and OMG Commons identities are labeled correctly throughout search,
    foundation, dependency, and proposal views.
28. Recommendation and plan TTL, capacity, eviction, restart, and stale-ID
    behavior are deterministic and tested.
29. Every row of the ExecPlan integration completeness matrix is implemented
    or is explicitly documented as out of scope by this specification.

## Slice 0 Decisions

The pre-implementation gates were resolved on 2026-08-08. The normative detail
is recorded in:

- `docs/decisions/phase-13-local-hybrid-retrieval.md`;
- `docs/decisions/phase-13-project-domain-profile-persistence.md`;
- `docs/decisions/phase-13-editable-external-iri-reuse.md`;
- `docs/decisions/phase-13-slice-0-contract-audit.md`.

In summary, the approved source is an Entio-approved master snapshot rather
than an official production publication; Commons is contextual by default;
Lucene `10.5.0`, ONNX Runtime CPU `1.28.0`, DJL tokenizers/API `0.36.0`, and
`all-MiniLM-L6-v2` at revision
`94ea1512acaefbfe2e255b2d2ea4bf0d9d7b3dc3` are pinned; assets are committed
under the separate Phase 13 manifest; existing typed removals and IRI-valued
annotations are sufficient; and a narrow sidecar participant extends the
existing atomic apply path. Full-mode performance acceptance uses the recorded
Apple Silicon/Java 21 baseline and retains explicit lexical-structural degraded
mode elsewhere.

The locked benchmark judgments were approved by two reviewers before being
locked. They are executable acceptance data, not tuning data. The hybrid
service—not the vector signal in isolation—must meet the locked quality gates.
Each affected UI slice must still perform its approved extraction audit before
changing a form.

## Boundary Check

- **Phase fit:** This is the approved Phase 13 specification and therefore may
  revise Phase 5 and Phase 12-era non-goals only where this document says so.
- **Speculative infrastructure:** The design uses the existing verified package,
  current modules, local files, and an embedded library. It adds no server,
  database, Gradle module, or hosted provider.
- **Module ownership:** RDF identity and recommendation policy remain in Kotlin;
  Ktor adapts authorized contracts; React and VS Code present results.
- **Standards tooling:** Apache Jena, OWL API, and current SHACL/reasoning tools
  remain authoritative. Lucene and ONNX Runtime provide retrieval mechanics;
  Entio does not implement a custom RDF, OWL, SHACL, tokenizer, or vector
  database framework.
- **Human control:** Retrieval never approves, applies, or validates ontology
  meaning. All ontology mutations use the existing controlled workflow.
