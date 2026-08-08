# Phase 13 Scope

## Phase Name

**Phase 13: Domain-Ontology-First Modeling and Full-Corpus Retrieval**

## Status

Approved Phase 13 scope as of 2026-08-08. Phase 13 has not been implemented;
repository implementation remains complete through Phase 12.

This document defines the current Phase 13 product direction in one place. The
Phase 13 feature specification and ExecPlan refine the implementation details
without expanding the product scope or weakening the safety and human-review
boundaries defined here. ExecPlan Slice 0 audit and ADR work is authorized.
Production implementation remains blocked until its decisions are incorporated
into approved revisions of the planning documents.

Phase 13 changes Entio's treatment of external domain ontologies. FIBO is no
longer an always-present reference catalog in a separate browser. A user may
instead activate a domain ontology for a project, choose the parts they want to
reuse, and make that domain ontology part of normal ontology modeling.

FIBO is the only supported domain ontology in Phase 13. The contracts should be
domain-neutral so another approved domain ontology can be added in a later
phase without rebuilding every modeling workflow.

## Purpose

Entio should help users reuse established domain meaning before they create
overlapping local concepts.

Today, FIBO is a pinned external catalog that users can browse and search. It is
useful, but separate from the main modeling experience and limited to a curated
searchable package. Users must remember to visit the external ontology area,
and ordinary authoring actions do not consistently consider the full FIBO
ontology.

Phase 13 makes domain reuse a project-level choice and a normal part of
human-driven modeling:

```text
choose an optional domain ontology
→ choose foundational concepts to bring into the project
→ search the complete pinned domain release
→ recommend reuse during normal modeling
→ let the user reuse, customize, extend, map, or remain local
→ preview and validate the resulting changes
→ apply only after explicit human approval
```

The first supported source is the Financial Industry Business Ontology (FIBO).
Users who do not need FIBO can leave domain-ontology support disabled.

## Central Product Principle

> An activated domain ontology informs every relevant human-driven modeling
> decision, but it never makes that decision for the user.

Retrieval finds relevant domain concepts. Kotlin verifies their identity,
release, kind, structure, compatibility, and dependencies. Entio explains the
recommendation. A person chooses whether to reuse, customize, extend, map, or
create locally. The existing proposal, validation, approval, atomic apply,
reload, and rollback workflow remains authoritative.

## Decisions Established By This Phase

Phase 13 establishes the following product decisions:

- Domain-ontology use is optional and project scoped.
- FIBO is the only selectable domain ontology in Phase 13.
- A project uses one exact approved pinned FIBO snapshot at a time. Slice 0
  must determine whether that snapshot is also an official production release.
- Entio searches the complete approved release, not only the existing curated
  catalog.
- Search combines lexical, vector, ontology-structural, and project-context
  signals.
- Domain recommendations appear throughout applicable human-driven modeling
  workflows.
- Document ingestion and the native ontology assistant do not use the new
  Phase 13 retrieval service yet.
- Selecting a FIBO concept creates a project-owned, editable reuse
  representation while preserving the original FIBO IRI.
- An imported concept's label, definition, hierarchy, domain, range, and other
  Entio-supported statements may be changed through the normal review
  workflow. Its canonical FIBO IRI cannot be changed.
- Entio retains the source release and original source statements so users can
  distinguish FIBO meaning from project customizations.
- No recommendation, import, customization, or dependency is silently applied.

## Why Phase 13 Is A New Phase

This work is not a small update to the existing external ontology panel. It
changes several earlier boundaries:

- Phase 5 intentionally used a fixed curated package and deterministic textual
  search rather than full FIBO indexing.
- Phase 5 treated FIBO primarily as a read-only external catalog.
- Phase 12 explicitly excluded embeddings, vector indexes, and full external
  ontology retrieval.
- Current authoring workflows do not share one domain-recommendation service.
- Current external reuse preserves source statements rather than creating the
  editable project-owned reuse model required here.

Phase 13 therefore requires a new specification, ExecPlan, dependency review,
security review, migration plan, benchmarks, and focused tests before code is
changed.

## Plain-Language Terms

### Domain ontology

A domain ontology is an established model for a field of knowledge. FIBO is a
domain ontology for financial business concepts and relationships.

### Domain profile

A domain profile records the domain ontology and exact release selected for a
project, the foundational concepts chosen by the user, and the concepts later
reused by the project.

### Foundational concept

A foundational concept is a broadly useful class or property offered as a
starting point. Examples may cover organizations, agreements, identifiers,
dates, quantities, classifications, ownership, products, and services.

The foundation is an Entio presentation profile over an approved FIBO release.
It is not a claim that these are the only foundational concepts in FIBO.

### Reused concept

A reused concept is a project entity that keeps the canonical FIBO IRI. It is
traceable to the selected FIBO release but is represented in project-controlled
content so supported statements can be customized.

### Source snapshot

A source snapshot is the verified FIBO description and relevant axioms Entio
saw when the concept was selected. It lets Entio show the original meaning,
identify local changes, and support a later release-upgrade workflow.

### Local customization

A local customization is a project-owned change to a reused concept. Examples
include a preferred business label, a locally useful definition, a different
parent, or a locally appropriate property domain or range.

The unchanged IRI preserves identity and traceability, but it does not prove
that customized project statements remain identical to the current FIBO
release. Entio must show that distinction honestly.

### Local extension

A local extension uses a new local IRI and relates it to a FIBO concept, usually
through a supported superclass or subproperty relationship. It is preferable
when the local meaning is narrower or materially different.

### Mapping

A mapping keeps a local entity and records a supported relationship to a FIBO
entity. Phase 13 must use only mapping relations already approved by the
specification and semantic engine. It must not infer equivalence from search
similarity.

### Retrieval-augmented modeling

Retrieval-augmented modeling means Entio searches trusted ontology content and
shows relevant evidence while the user models. A generative model is not
required for this interaction. In Phase 13, retrieval augments the human
authoring experience rather than granting an AI authority to edit the ontology.

### Hybrid search

Hybrid search combines multiple complementary signals:

- lexical matching for exact terminology and identifiers;
- vector similarity for paraphrases and related meaning;
- ontology-aware checks for entity kind, hierarchy, domain, and range;
- graph-aware scoring for relationships to concepts already used by the
  project;
- project-context scoring for the action the user is performing.

No single signal proves that a concept is the correct reuse target.

## Goals

Phase 13 should:

- make domain-ontology activation an optional project-level choice;
- support one approved pinned FIBO snapshot per activated project;
- present a clear, selectable set of foundational classes and properties;
- let users select all foundation concepts, groups, or individual concepts;
- calculate and explain required dependencies before staging reuse;
- preserve the original FIBO IRI for every reused concept;
- let users customize all Entio-supported statements except the canonical IRI;
- retain original source statements, release identity, fingerprints, and
  project authorship for comparison and provenance;
- index the complete approved FIBO release;
- provide fast hybrid retrieval without querying the public FIBO website for
  every user action;
- integrate domain recommendations into all applicable human-driven ontology
  modeling workflows;
- make recommendations contextual, explainable, and optional;
- favor already reused concepts when they remain suitable;
- let users explicitly reuse, customize, extend, map, or continue locally;
- preserve deterministic semantic eligibility, validation, dependency, and
  application behavior in Kotlin;
- keep all changes inside the existing controlled proposal workflow;
- remain usable when no domain ontology is activated;
- define domain-neutral contracts without prematurely supporting arbitrary
  ontology sources;
- provide measurable retrieval quality, latency, and stability targets;
- migrate existing FIBO reuse without silently changing project meaning.

## Non-Goals

Phase 13 does not add:

- a second supported domain ontology;
- arbitrary ontology URLs or user-uploaded external ontologies;
- simultaneous activation or reconciliation of multiple domain ontologies;
- live web search or public FIBO page fetching for each recommendation;
- automatic FIBO release upgrades;
- automatic replacement of deprecated or removed FIBO entities;
- automatic import, approval, application, or source-file writing;
- model-generated proof of equivalence;
- automatic `owl:equivalentClass`, `owl:equivalentProperty`, or `owl:sameAs`
  assertions based on retrieval similarity;
- general-purpose ontology alignment;
- automatic bulk alignment of an existing local ontology;
- unrestricted editing of the downloaded FIBO release itself;
- document-ingestion integration with the Phase 13 retrieval service;
- native ontology-assistant integration with the Phase 13 retrieval service;
- a new reasoning engine, RDF parser, OWL framework, SHACL engine, proposal
  workflow, or apply path;
- production identity, tenancy, hosted search infrastructure, or organization-
  wide shared indexes;
- a guarantee that every FIBO construct can be edited through Entio's bounded
  typed operations;
- silent reinterpretation of local project terms;
- removal of the user's ability to create a purely local ontology.

## Product Experience

### 1. Projects Without A Domain Ontology

Domain support is optional. A new or existing project may select **None** and
continue to use Entio as it does today.

When no domain ontology is active:

- no FIBO recommendations appear in ordinary authoring;
- local and imported project search continues to work;
- validation, reasoning, SHACL, proposals, document ingestion, and the native
  assistant continue to work within their existing boundaries;
- the user may activate FIBO later.

Entio must not frame the absence of FIBO as an error.

### 2. Activating FIBO

The project settings experience offers an optional domain ontology selection.
Phase 13 initially shows:

```text
Domain ontology
○ None
○ Financial Industry Business Ontology (FIBO)
```

Before activation, Entio shows:

- the exact approved pinned FIBO snapshot and its publication status;
- source and licensing information;
- index availability and integrity status;
- a plain-language explanation of project-owned reuse;
- the fact that selected entities keep their FIBO IRIs;
- the fact that local customizations can diverge from the source release;
- the fact that activation alone does not change ontology content.

Activation creates or updates a durable, versioned project domain profile. It
does not import every FIBO entity and does not bypass review.

The specification and ExecPlan must define the exact project configuration
serialization and migration. The browser must not become the authority for the
active release or profile contents.

### 3. Choosing A Foundation

After FIBO is activated, Entio presents foundation groups such as:

- agents and organizations;
- agreements and commitments;
- identifiers and classifications;
- dates and temporal concepts;
- quantities, units, and measures;
- ownership and control;
- products and services;
- other broadly useful concepts approved for the release profile.

The exact groups and members are release metadata generated from the approved
source, reviewed as product content, and versioned. They must not be an
untracked hard-coded browser list.

Users may:

- select the complete foundation;
- select one or more groups;
- select individual classes and properties;
- inspect definitions and relationships before selection;
- search the full release for concepts outside the foundation;
- skip foundation selection and activate FIBO only for later recommendations.

Entio calculates the structural and source dependency closure for the selected
concepts. The review must distinguish concepts the user chose from supporting
entities and ontology modules Entio requires.

### 4. Staging Foundation Reuse

Foundation selection creates a reviewable change set. The preview includes:

- explicitly selected classes and properties;
- required supporting entities and axioms;
- source modules and dependency reasons;
- unchanged canonical IRIs;
- source release and fingerprints;
- target project sources or managed layers;
- conflicts with existing local or imported entities;
- the number of project-owned statements to be added;
- validation and semantic diff results.

Nothing is written until the user approves the current proposal.

### 5. Editing A Reused Concept

A reused concept is editable through Entio's existing supported typed forms.
The canonical IRI is locked. Other supported fields may be changed, including:

- preferred label;
- alternate labels;
- definition;
- supported annotations;
- superclass or superproperty;
- property domain and range;
- supported assertions and values;
- other semantics already supported by Entio's typed edit system.

The details view shows two clearly separated descriptions:

```text
Project meaning
  The labels, definition, and axioms currently used by this project.

FIBO source meaning
  The original statements from the project's pinned FIBO release.
```

Changing a reused concept creates a normal proposal. Entio shows whether the
proposal changes only wording or also changes logical structure.

If a customization appears to materially narrow or conflict with the FIBO
source meaning, Entio should recommend a local extension. This is an advisory
reuse warning unless an existing deterministic ontology rule is actually
violated. Retrieval or an embedding score must never decide ontology validity.

### 6. Reuse Choices

Where supported, a domain recommendation offers explicit actions:

1. **Reuse** — use the FIBO IRI and initial source statements.
2. **Reuse and customize** — use the FIBO IRI and prepare project-owned labels,
   definitions, or other supported changes.
3. **Extend locally** — create a local IRI related to the FIBO concept through
   a supported hierarchy relationship.
4. **Map** — retain or create a local entity and add an approved mapping
   relationship.
5. **Continue locally** — create or edit without domain reuse.
6. **Not relevant** — dismiss the recommendation for the current intent.

The available actions depend on entity kind, current project state, supported
typed operations, and deterministic compatibility checks.

## Project-Owned Reuse Model

### Why Plain `owl:imports` Is Insufficient

An `owl:imports` statement can bring an ontology module and its assertions into
the project's logical view. It does not, by itself, provide an intuitive way
to replace project-facing labels or definitions, selectively manage concepts,
or distinguish authoritative source statements from project customizations.

RDF also treats an additional local label or definition as another statement;
it does not automatically treat it as a replacement. Entio therefore needs an
explicit project-owned reuse model and display policy.

### Required Conceptual Layers

Phase 13 uses three conceptual layers even if the final physical storage is
optimized differently:

```text
Pinned FIBO source snapshot
  immutable approved release content used for provenance and retrieval

Project reuse layer
  selected entities using their canonical FIBO IRIs and project-owned axioms

Local ontology layer
  entities using project IRIs, including local extensions and mappings
```

The pinned source snapshot is not edited by users. The project reuse layer is
edited only through supported Entio operations and the proposal workflow.

### Canonical Identity

For a reused entity:

- the FIBO IRI is its canonical IRI;
- Entio must prevent rename operations that replace that IRI;
- labels are display annotations, not identity;
- source release and original statements remain inspectable;
- local changes do not rewrite the cached or downloaded FIBO source package.

Keeping the IRI allows traceability, but provenance must not rely on the IRI
alone. The same IRI can be described by multiple graphs and releases.

### Required Provenance

For each reused concept, Entio retains at least:

- domain source ID;
- exact approved FIBO snapshot and verified publication status;
- canonical IRI;
- source ontology and module;
- original entity kind;
- source statement snapshot or deterministic fingerprint;
- dependency closure identity;
- selection time and selecting project actor where current identity supports
  it;
- current project statement fingerprint;
- whether the project description is unchanged or customized;
- which successfully applied proposal introduced each project customization.

The specification must decide which provenance belongs in ontology source and
which belongs in project-authorized retained provenance. Provenance must not be
presented as an ontology axiom when it is operational metadata.

### Source And Project Annotation Policy

Entio must never silently merge source and project annotations into one
apparently authoritative description.

The normal display policy is:

- show the project preferred label as the primary label when present;
- otherwise show the pinned FIBO preferred label;
- show project definitions as project-authored meaning;
- keep the pinned FIBO definition visible and searchable;
- preserve language tags and datatypes;
- label source and project authorship clearly;
- use deterministic selection when multiple project labels are present.

### Structural Customization

Phase 13 permits users to change supported structural statements while keeping
the FIBO IRI. This is powerful and must be transparent.

Entio must:

- show the original and proposed structure side by side;
- identify added, removed, and changed source-derived statements;
- distinguish an annotation-only customization from a logical customization;
- explain that the project meaning may no longer match unmodified FIBO;
- recommend a local extension when that would communicate the distinction more
  honestly;
- continue to enforce deterministic OWL, validation, reasoning, source, and
  proposal rules;
- never claim semantic equivalence based on retrieval similarity.

## Complete FIBO Release And Indexing

### Approved Source

Phase 13 uses one exact approved pinned FIBO snapshot. Slice 0 must record
whether it is an official production publication or an Entio-approved snapshot
of master. It must not use a moving `latest`, unresolved branch, or unresolved
public URL as project state.

The release acquisition or build process must verify:

- source identity and release metadata;
- licensing and attribution requirements;
- file checksums;
- supported RDF serializations;
- import resolution;
- required OMG Commons or other approved dependencies;
- entity and module counts;
- deprecated entities and maturity metadata where published;
- repeatable index generation.

Runtime authoring requests search an Entio-controlled local index. They do not
depend on the public FIBO site being available.

### One Semantic Source Of Truth

The index accelerates retrieval; it is not a second ontology authority.

The Kotlin semantic engine remains responsible for parsing approved RDF,
constructing semantic descriptions, identifying entity kinds, resolving
dependencies, and verifying relationships. Index records must be derived from
that verified representation or a reproducible build using the same semantic
contracts.

Vector metadata or search documents must not independently redefine hierarchy,
domain, range, imports, deprecation, or entity identity.

### Indexed Entity Record

Each searchable class or property should include, where available:

- canonical IRI;
- entity kind;
- preferred and alternate labels;
- definitions, explanatory notes, and examples;
- source ontology, module, and domain area;
- direct parents and children;
- property domain and range;
- inverse or related property context;
- selected neighboring entities;
- maturity and deprecation information;
- dependency identifiers;
- release and record fingerprints;
- text used to create the vector representation;
- embedding model and vector-contract version.

Large annotations and graph neighborhoods must be bounded. The complete source
remains available to the semantic engine for verification when a candidate is
selected.

## Hybrid Retrieval

### Retrieval Pipeline

The normal retrieval pipeline is:

```text
structured modeling intent
→ lexical candidate retrieval
→ vector candidate retrieval
→ candidate union and exact identity deduplication
→ deterministic entity-kind and compatibility checks
→ ontology- and graph-aware reranking
→ project-context reranking
→ bounded, explainable recommendations
```

Lexical and vector retrieval broaden recall. Kotlin-owned checks and reranking
improve precision and prevent structurally inappropriate results from being
presented as simple matches.

### Structured Modeling Intent

Every participating workflow sends a structured intent rather than only a text
query. Depending on the action, it may contain:

- operation type;
- requested entity kind;
- label and alternate wording;
- draft definition;
- current entity IRI when editing;
- selected or expected parent;
- expected domain and range;
- expected datatype;
- nearby classes, properties, and assertions;
- currently reused FIBO concepts;
- project language preferences;
- source and authorization context.

The browser may collect this context, but the server resolves all entity IDs and
rechecks authorization and freshness.

### Lexical Retrieval

Lexical retrieval finds exact and near-exact terminology in labels,
definitions, notes, module names, and IRIs. It should support deterministic
normalization and an established ranking method such as BM25 or an equivalent
approved implementation.

Lexical matching is strongest for exact business vocabulary and uncommon
identifiers. It should remain independently usable when vector retrieval is
unavailable.

### Vector Retrieval

Vector retrieval uses embeddings to find related meaning expressed with
different words. For example, a request for `company that provides home loans`
may retrieve a concept described with `mortgage lender` terminology.

The embedding system must:

- use an explicitly approved and versioned model;
- build vectors from bounded, documented ontology fields;
- record the model and input-contract version in the index manifest;
- avoid sending project ontology content to an external service unless a later
  approved specification explicitly authorizes that boundary;
- support reproducible index rebuilds for the same source and embedding
  contract as far as the selected technology permits;
- expose health and availability without leaking vectors or internal prompts;
- degrade safely to lexical and structural retrieval when unavailable.

The Phase 13 specification and ExecPlan must choose and audit the embedding
implementation. This scope does not approve a new hosted provider, vector
database, or production search service by implication.

### Ontology-Aware Eligibility And Scoring

Kotlin checks at least:

- canonical IRI exists in the pinned release;
- release and record fingerprints are current;
- entity kind is compatible with the action;
- object and datatype properties are not confused;
- domain, range, datatype, and type context is compatible where known;
- entity is not excluded or deprecated without a clear warning;
- required dependencies are resolvable;
- candidate is not an exact duplicate or no-op in the current project;
- the user and project are authorized for the requested action.

Some failures make a candidate ineligible. Others remain visible with an
explanation. The later specification must define that distinction explicitly.

### Graph-Aware And Project-Aware Reranking

A candidate may rank higher when:

- it is connected to FIBO entities already reused by the project;
- its parent is already present;
- its domain or range matches the current modeling context;
- it belongs to a module already used by the project;
- its graph neighborhood matches nearby project entities;
- it requires fewer additional dependencies without sacrificing meaning;
- it is part of the approved foundation profile;
- it is an exact source match for a currently customized concept.

Being nearby in the graph is supporting evidence, not proof of reuse.

### Stable Results And Versioning

The same frozen intent, project state, release, index, retrieval contract, and
availability mode should produce stable eligibility and tie-breaking.

Vector similarity may vary slightly across platforms or approved library
versions. Phase 13 must therefore version:

- source release;
- semantic descriptor contract;
- lexical index contract;
- embedding model and input contract;
- graph-context contract;
- ranking weights and thresholds;
- eligibility rules;
- result contract.

Stable server-issued recommendation IDs must bind candidates to these frozen
inputs. A stale ID cannot be used to stage a changed or different entity.

### Explainable Results

Every recommendation must include understandable reasons, such as:

- exact alternate-label match;
- definition similarity;
- compatible entity kind;
- matching domain and range;
- parent already reused by the project;
- same FIBO module as existing project concepts;
- deprecated source entity;
- additional dependency impact.

Entio must not display a similarity number as if it were a probability that the
concept is correct. Internally useful scores may be shown only with clear
labels and supporting reasons.

### Low Confidence And No Match

Entio must support honest uncertainty:

- low-confidence candidates are labeled accordingly;
- structurally incompatible candidates do not become recommended reuse actions;
- no-result is a valid outcome;
- users may broaden the search explicitly;
- local creation remains available;
- a weak match never blocks an otherwise valid proposal.

## Required Integration Across Entio

Phase 13 retrieval is cross-cutting. It should be implemented through one
Kotlin-owned domain recommendation capability rather than duplicated in each
client or workflow.

### Explore And Global Semantic Search

When FIBO is active, normal semantic search covers:

- applied local entities;
- imported project entities;
- reused FIBO entities;
- local extensions and mappings;
- available entities from the complete pinned FIBO release.

Results clearly show whether an entity is local, reused unchanged, reused and
customized, a local extension, or available from FIBO.

The separate external ontology area becomes domain-profile administration and
broad browsing rather than the only place to discover FIBO.

### Class Creation

Class recommendations use the draft label, definition, intended parent, nearby
entities, and current project context. The user can reuse, customize, extend,
map, dismiss, or continue locally.

### Property Creation

Property recommendations use the draft label and definition plus object versus
datatype kind, domain, range, datatype, parent-property, and inverse context.
Structural compatibility should outweigh superficial textual similarity.

### Individual Creation

Retrieval recommends suitable local or FIBO-backed types and applicable
properties. It must not import arbitrary FIBO individuals unless the later
specification explicitly approves a bounded reference-data category.

### Label, Definition, And Semantic Editing

When a local entity's wording or meaning changes, Entio checks for relevant
FIBO concepts. When a reused entity changes, Entio compares project meaning
with the pinned source and may recommend another FIBO concept or a local
extension.

### Hierarchy Editing

Superclass and superproperty selection includes compatible FIBO candidates and
uses parent, child, sibling, and project graph context. Existing deterministic
cycle, dependency, source, and reasoning checks remain authoritative.

### Domain, Range, And Datatype Editing

Property editing recommends compatible local and FIBO-backed classes or
datatypes. Class inspection may also show applicable FIBO properties whose
domains are compatible with the class.

### Assertions And Values

Assertion authoring recommends applicable properties and target types using
domain, range, direction, and current subject context. Retrieval does not
invent assertion values and does not replace deterministic validation.

### Deletion And Replacement

Before a supported deletion, Entio may recommend a FIBO replacement, broader
parent, or migration target. Removing a reused concept means removing its
project reuse statements and resolving dependents; Entio never deletes the
entity from the pinned FIBO source package.

### Shared Staging And Proposal Review

New or substantially changed semantic content includes a domain-reuse review.
Reviewers can inspect close candidates and, where supported, change a local
creation into reuse, customization, extension, or mapping before approval.

The recommendation and selected action are frozen and reverified when staged.
Search results themselves are not ontology edits.

### SHACL Authoring

Target-class, property-path, class, and datatype selection includes compatible
domain candidates. RAG helps find vocabulary; Kotlin and the existing SHACL
engine decide supported shape structure and validation results.

### Ontology Map

The read-only ontology map may offer **Find related FIBO concepts** for a
selected node. Available FIBO entities are visually distinct and do not become
part of the asserted layout or project graph until reuse is approved and
applied.

### Reasoning Workspace

Reasoning remains deterministic. Retrieval may help users find FIBO concepts
related to an asserted or inferred entity, but embeddings do not create
inferences, validate facts, or change reasoning results.

### FIBO Foundation Expansion

Users can add concepts beyond the initial foundation at any time through the
same recommendation, dependency, proposal, and approval path.

### CLI And VS Code

The later specification must audit existing FIBO CLI and VS Code contracts. At
minimum, they must remain compatible with the new project profile and must not
silently use the obsolete curated-only meaning.

Phase 13 does not require every rich recommendation interaction to have full UI
parity in CLI and VS Code. Kotlin-owned reads and mutations must nevertheless
remain reusable, versioned, and presentation-neutral.

## Explicitly Deferred Integrations

### Document Ingestion

Phase 12 document retrieval continues to use its implemented bounded,
deterministic scopes and pinned catalog contract. Phase 13 must not quietly
replace it with embedding retrieval.

A later phase may connect document candidates to the Phase 13 index after
separate evaluation of cost, prompt bounds, retrieval quality, frozen work
keys, provenance, and compatibility with Phase 12 verification.

### Native Ontology Assistant

The assistant keeps its current bounded ontology and FIBO context behavior.
It does not gain unrestricted access to the Phase 13 retrieval service, raw
vectors, index administration, or live external sources.

A later phase may expose narrow recommendation tools after tool authorization,
prompt-injection, provenance, cost, and response-verification work is approved.

## Shared Domain Recommendation Service

The reusable logical boundary is:

```text
React, VS Code, CLI, and server workflows
                  ↓
        structured modeling intent
                  ↓
      Domain Recommendation Service
                  ↓
 lexical + vector candidate retrieval
                  ↓
 Kotlin eligibility and graph-aware reranking
                  ↓
 explainable, versioned recommendation records
                  ↓
      existing typed proposal workflow
```

The service owns coordination, not ontology truth. It may depend on reusable
semantic-engine capabilities and approved retrieval components. It must not
move semantic policy into React or duplicate RDF behavior in `web-server`.

A recommendation record should contain at least:

- opaque recommendation ID;
- frozen intent fingerprint;
- canonical FIBO IRI;
- FIBO release and record fingerprint;
- entity kind;
- original source annotations;
- current project-facing annotations when reused;
- source ontology and module;
- reuse status;
- bounded relevant hierarchy, domain, and range context;
- match reasons;
- compatibility and warning codes;
- dependency summary;
- permitted next actions;
- ranking and contract versions;
- freshness information.

The browser submits recommendation IDs and explicit user choices. Kotlin
resolves and revalidates the underlying entity; the browser does not submit
trusted IRIs, scores, dependencies, or source axioms as authority.

## Architecture And Module Ownership

### `core-types`

May define stable domain-profile, source-release, reuse-provenance,
recommendation, intent, compatibility, and status objects that are genuinely
shared across engine boundaries. Fixed states should use enums or sealed types,
not loose strings.

### `semantic-engine`

Owns:

- FIBO source loading and verification;
- full-release semantic descriptor generation;
- entity identity and kind;
- dependency calculation;
- source snapshot comparison;
- reuse and customization translation into supported graph changes;
- domain, range, hierarchy, and graph compatibility;
- recommendation eligibility and deterministic reranking policy;
- proposal-time freshness verification.

It must continue to use established RDF and OWL libraries.

### Retrieval Component Placement

The specification and ExecPlan must determine whether lexical and vector index
implementation belongs inside `semantic-engine` or behind a narrowly focused
new internal component. A new Gradle module is not approved by this scope
alone. The decision must preserve dependency direction and avoid placing
product logic in `shared`.

### `web-server`

Owns:

- authorized project-scoped activation and profile routes;
- index availability and job coordination;
- recommendation request bounds and cancellation;
- server-issued IDs and freshness state;
- development-session and shared-staging adaptation;
- credential and secret isolation;
- versioned HTTP and WebSocket contracts.

It does not parse FIBO independently, calculate ontology compatibility, or
construct RDF changes.

### `web-app`

Owns:

- activation and foundation-selection presentation;
- debounced recommendation requests;
- source-versus-project comparison views;
- recommendation explanations and reuse actions;
- dependency and proposal review presentation;
- temporary UI visibility, selection, and dismissal state.

It does not calculate semantic matches, trust vector scores as validity, decide
dependencies, or write ontology files.

### `validation-engine` And `graph-diff`

Continue to own deterministic validation and semantic diff behavior. They may
consume new typed reuse metadata only where the approved specification requires
it. Retrieval similarity must not become a validation pass/fail rule.

### `shared`

Must not become a home for index code, embeddings, domain policy, or FIBO
logic.

## Trust And Safety Boundaries

FIBO content, index text, user queries, and project annotations are data, not
instructions.

Phase 13 must ensure:

- only approved source releases enter the index;
- downloads and build inputs are checksum verified;
- ontology parsing remains bounded and uses existing libraries;
- index records cannot introduce an unverified canonical IRI;
- vector output never bypasses semantic verification;
- clients cannot choose the active release by inventing a release ID;
- clients cannot stage a candidate by submitting an arbitrary IRI;
- recommendation IDs are project scoped and freshness checked;
- source and dependency paths cannot escape approved assets;
- labels and definitions are safely rendered as untrusted text;
- no credentials or project secrets enter embedding text;
- project-authored content is not sent to a third-party embedding provider
  without a separately approved boundary;
- resource, result, request, and concurrency bounds prevent index or query abuse;
- cancellation and failure leave ontology sources unchanged.

## Determinism, Reproducibility, And Failure Behavior

The complete retrieval system contains approximate components, so Phase 13 must
be precise about what is deterministic.

The following must be repeatable for frozen inputs:

- source release verification;
- semantic descriptor construction;
- eligible entity set;
- identity deduplication;
- dependency closure;
- kind, domain, range, and project-state compatibility;
- tie-breaking after normalized scores;
- source snapshot and project fingerprints;
- proposal translation, validation, diff, and apply behavior.

The approved vector implementation should be reproducible within its documented
platform contract. Entio must not promise bit-for-bit cross-platform vector
identity unless verified.

Failure behavior:

- if FIBO assets are invalid, activation and new reuse stop clearly;
- if the vector index is unavailable, lexical and ontology-aware retrieval may
  continue with a visible degraded-mode status;
- if all retrieval is unavailable, local modeling remains available and Entio
  clearly states that domain recommendations could not be checked;
- if a recommendation becomes stale, staging fails safely and refreshes it;
- if a dependency cannot be resolved, reuse cannot be staged;
- if post-save verification fails, the existing atomic restoration behavior
  remains in force.

## Performance And Interaction Requirements

RAG should be present throughout authoring without making forms noisy or slow.

The implementation should use:

- debounced lightweight retrieval while meaningful text is entered;
- richer reranking after entity kind and structural context are known;
- caching keyed by frozen intent, project fingerprint, release, and index
  version;
- cancellation of superseded requests;
- explicit **Find more in FIBO** for broader searches;
- bounded default results with pagination or refinement for broad exploration;
- no network fetch from the public FIBO site in the interactive query path.

The specification and ExecPlan must set and benchmark concrete targets for:

- initial index build time;
- index size and application packaging impact;
- cold and warm query latency;
- memory use;
- concurrent project requests;
- lexical degraded-mode latency;
- recommendation refresh behavior while typing.

## Retrieval Quality And Evaluation

Phase 13 cannot be considered complete because search returns plausible-looking
results. It needs an audited evaluation set.

The benchmark should include:

- exact FIBO labels;
- common abbreviations and alternate labels;
- plain-language paraphrases;
- finance-specific terminology;
- directionally opposite relationships;
- class-versus-individual ambiguity;
- class-versus-property ambiguity;
- object-versus-datatype property ambiguity;
- domain and range mismatches;
- deprecated concepts;
- concepts from modules not already used by the project;
- suitable no-match cases;
- local concepts that should remain local;
- reused concepts with customized labels and definitions.

Metrics should cover:

- recall at bounded result counts;
- precision of the top recommendations;
- correct entity-kind rate;
- incompatible-candidate suppression;
- no-match quality;
- explanation accuracy;
- stability for frozen inputs;
- cold and warm latency;
- effect of project graph context;
- lexical-only behavior when vectors are unavailable.

Product acceptance thresholds must be approved before implementation claims
completion. Test queries and expected relevant IRIs should be versioned with the
approved FIBO release.

## Migration From The Existing FIBO Experience

Phase 13 replaces the product meaning of the current always-available external
FIBO browser, but it must not silently alter existing projects.

The migration must inventory:

- projects that never used FIBO;
- projects with staged FIBO reuse;
- projects with applied FIBO references;
- existing local extensions of FIBO entities;
- proposal and rollback records that contain FIBO operations;
- CLI, VS Code, web, assistant, and document-analysis contracts that reference
  the curated catalog;
- tests and fixtures pinned to the current FIBO package.

Expected migration principles:

- existing applied ontology statements remain unchanged;
- an existing FIBO reference does not automatically activate or import the
  entire new foundation;
- Entio may offer to create a domain profile from verified existing use;
- source release must be inferred only when deterministic evidence supports it;
- ambiguous source release becomes an explicit migration warning;
- open proposals retain their original semantics or are invalidated clearly;
- document ingestion and assistant behavior remain on their existing contracts;
- deprecated APIs are removed only after audited client migration.

The current curated foundation may seed the new foundation presentation, but it
must be regenerated and reviewed against the approved full release rather than
assumed correct indefinitely.

## Release And Upgrade Policy

Phase 13 pins one approved snapshot and does not implement a full upgrade
workflow. It must nevertheless store enough information to make upgrades safe
later.

For Phase 13:

- Entio ships or reproducibly builds one approved release and index;
- each activated project records that exact release;
- projects do not silently follow a newer application default;
- a newer FIBO release does not rewrite project reuse statements;
- original source snapshots remain available for comparison;
- unsupported or missing historical assets produce a clear project status.

A later phase should add explicit upgrade preview covering changed, deprecated,
removed, and moved entities; dependency changes; source-versus-project diffs;
and preservation of local customizations.

## User Feedback And Dismissal

Users need control over repeated recommendations without creating a hidden
semantic policy.

Phase 13 may retain project-session feedback such as:

- accepted recommendation;
- dismissed for the current action;
- not relevant to the intended meaning;
- prefer local creation this time.

Durable learning, organization-wide ranking personalization, or model training
from this feedback is out of scope unless separately specified. Dismissal must
not erase FIBO content or make deterministic validation ignore actual
conflicts.

## Observability And Audit

Development and authorized audit information should make retrieval behavior
diagnosable without exposing secrets or raw vectors.

Useful records include:

- project and active domain profile IDs;
- source release and index version;
- structured operation type;
- sanitized query fingerprint;
- lexical, vector, and final candidate counts;
- degraded-mode status;
- eligibility exclusion and warning counts;
- selected recommendation and action;
- dependency count;
- latency by retrieval stage;
- cancellation, stale-result, and failure codes.

Production logging must not record credentials, unrestricted project text,
private definitions, or raw embedding inputs by default.

## Required Contracts

The specification should define versioned contracts for at least:

- available domain ontology sources;
- approved releases and availability;
- project domain profile;
- foundation groups and members;
- activation, deactivation, and profile changes;
- source and project descriptions;
- full-release search;
- structured modeling intent;
- recommendation result and explanations;
- recommendation freshness;
- dependency preview;
- reuse, customize, extend, map, dismiss, and local actions;
- index manifest, health, and degraded mode;
- source snapshot and project customization status;
- migration status and warnings;
- benchmark corpus and results.

All mutation contracts must use server-issued identifiers and explicit user
intent. They must enter existing typed change and proposal boundaries rather
than creating a domain-specific apply route.

## Testing Requirements

Implementation must include focused tests for:

### Source And Index Tests

- exact release and checksum verification;
- complete expected module traversal;
- import and dependency resolution;
- semantic descriptor generation;
- lexical index reproducibility;
- vector record generation and versioning;
- index corruption and mismatch handling;
- deprecated and missing entity handling.

### Retrieval Tests

- exact, fuzzy, and paraphrase queries;
- class and property kind filtering;
- object and datatype property separation;
- domain and range compatibility;
- graph-aware and project-aware reranking;
- already-reused preference;
- low-confidence and no-match behavior;
- stable tie-breaking;
- stale recommendation rejection;
- lexical degraded mode;
- bounded results and cancellation.

### Reuse And Editing Tests

- foundation group and individual selection;
- dependency preview;
- unchanged canonical IRI;
- editable labels and definitions;
- structural customization;
- original source comparison;
- local extension and supported mapping;
- source-versus-project annotation display policy;
- proposal preview, validation, diff, approval, apply, reload, rollback, and
  restoration on failure;
- removal of project reuse without alteration of the source package.

### Workflow Integration Tests

- Explore and global semantic search;
- class creation;
- object- and datatype-property creation;
- individual typing;
- semantic annotation editing;
- hierarchy changes;
- domain and range editing;
- assertions;
- deletion and replacement;
- shared staging and proposal review;
- SHACL authoring;
- ontology map related-concept search;
- reasoning-workspace related-concept search;
- full-corpus foundation expansion.

### Boundary Tests

- no Phase 13 retrieval in document ingestion;
- no unrestricted Phase 13 retrieval in the assistant;
- no browser-owned semantic scoring or dependency calculation;
- no arbitrary IRI staging;
- no live public FIBO fetch in interactive retrieval;
- no automatic approval or apply;
- no writes to pinned source assets;
- no project content sent across an unapproved embedding boundary;
- unchanged behavior when the domain ontology is disabled.

### Migration And Regression Tests

- projects without FIBO use;
- existing applied FIBO reuse;
- existing local FIBO extensions;
- staged and stale proposals;
- current Phase 12 document analysis;
- current assistant FIBO context;
- CLI and VS Code compatibility;
- existing reasoning, SHACL, graph, apply, and rollback behavior.

## Acceptance Criteria

Phase 13 is complete only when all of the following are true:

1. A project can explicitly choose no domain ontology or activate the approved
   pinned FIBO snapshot.
2. Activation does not silently modify ontology sources.
3. Users can inspect and select all, grouped, or individual foundation classes
   and properties.
4. Entio computes and explains the verified dependency closure before staging.
5. Selected reuse changes enter the existing proposal and approval workflow.
6. Reused entities preserve their canonical FIBO IRIs.
7. Users can edit every Entio-supported statement except that canonical IRI.
8. Original FIBO source meaning and current project meaning remain separately
   visible and traceable.
9. The complete approved FIBO release is searchable through a versioned local
   index.
10. Hybrid retrieval uses lexical, vector, ontology-aware, graph-aware, and
    project-context signals with an approved degraded mode.
11. Recommendations are explainable and never treated as proof of semantic
    equivalence.
12. Applicable human-driven modeling workflows use the shared recommendation
    capability.
13. Document ingestion and the native ontology assistant remain outside the new
    retrieval boundary.
14. Users can always dismiss a recommendation and continue with a valid local
    model.
15. The browser cannot stage arbitrary external IRIs, dependencies, or source
    statements as trusted input.
16. Kotlin verifies source identity, release, kind, compatibility,
    dependencies, freshness, and project state before staging.
17. Retrieval failure never writes ontology sources and has clear fallback
    behavior.
18. Existing deterministic validation, reasoning, SHACL, proposal, apply,
    reload, rollback, and provenance behavior remains intact.
19. Migration preserves existing project meaning and does not automatically
    activate or expand FIBO.
20. Retrieval benchmarks meet approved quality, latency, memory, and stability
    thresholds.
21. Focused unit, integration, contract, migration, security, and regression
    tests pass.
22. The Phase 13 specification, ExecPlan, and any required ADRs are approved
    before implementation is reported complete.

## Required Planning Before Implementation

The Phase 13 specification and ExecPlan must resolve these decisions before the
first implementation slice:

- exact approved FIBO snapshot and verified publication status;
- exact source acquisition and redistribution process;
- license and attribution treatment;
- foundation groups and review ownership;
- durable domain-profile and source-snapshot storage;
- physical separation of source, project reuse, and local layers;
- representation of removal or replacement of source-derived statements;
- supported mapping predicates;
- lexical search library and index format;
- embedding model, runtime, license, model size, and Java compatibility;
- whether embeddings are generated at build time, installation time, or both;
- retrieval component module placement without dependency reversal;
- graph-neighborhood construction and bounds;
- ranking, thresholds, result bounds, and stable tie-breaking;
- degraded-mode requirements;
- project content privacy at the embedding boundary;
- concrete performance and quality thresholds;
- existing FIBO asset, API, CLI, VS Code, assistant, and document migration;
- rollback and historical-source availability;
- ADRs required for vector retrieval, editable external-IRI reuse, and project
  profile persistence.

If any decision requires a new service, module, dependency, persistence layer,
or external provider, the specification and ExecPlan must justify it
explicitly. This scope does not authorize speculative infrastructure.

## Recommended Implementation Slices For The Future ExecPlan

The ExecPlan may refine the sequence, but a safe dependency order is:

1. Audit the current FIBO implementation, source release, licensing,
   dependencies, and all client contracts.
2. Approve the domain profile, source snapshot, reuse semantics, and migration
   contracts.
3. Build and verify complete-release semantic descriptors and a reproducible
   lexical index.
4. Audit and add the approved embedding implementation and vector index.
5. Implement hybrid retrieval, compatibility checks, graph reranking,
   explanations, benchmarks, and degraded mode.
6. Implement optional FIBO activation and foundation selection without ontology
   mutation.
7. Implement project-owned reuse, editable customization, dependencies,
   provenance, and controlled proposals.
8. Integrate Explore, global search, and broad domain browsing.
9. Integrate class, property, individual, annotation, hierarchy, domain, range,
   assertion, and deletion workflows.
10. Integrate shared proposal review, SHACL authoring, ontology map, and
    reasoning-related discovery.
11. Migrate existing FIBO use and preserve excluded assistant and document
    boundaries.
12. Run full quality, performance, security, migration, and regression
    verification and publish a Phase 13 summary only after acceptance.

## Final Product Boundary

Phase 13 makes FIBO reuse part of Entio's normal human-driven modeling
experience. It does not make FIBO mandatory, let retrieval decide ontology
truth, or create an automatic external-ontology write path.

The intended responsibility split is:

> The full pinned domain release supplies trusted candidate meaning. Hybrid
> retrieval finds relevant possibilities. Kotlin verifies identity, structure,
> dependencies, and safety. Entio explains the choices. A person decides what
> the project means and approves every applied change.
