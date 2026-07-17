# Feature Spec: Phase 5 External Ontology Browsing And Basic Schema RAG

## Status

Implemented. Phase 5 is complete and summarized in `docs/phase-summaries/phase-5-summary.md`. This specification remains the approved behavioral record.

## Problem

Entio can now describe and edit local ontology elements through a deterministic semantic-description layer, but users still need to recreate concepts and properties that already exist in trusted standards. This encourages duplicate modeling and makes it harder to use established financial ontology vocabulary.

Phase 5 should make one trusted external ontology, FIBO, available inside Entio. Users need to browse a useful curated foundation set, search the wider approved catalog, inspect the original semantic descriptions, understand supporting dependencies, and decide whether to reuse an external concept or extend it locally.

External ontology content must remain read-only and reproducible. Selecting or searching FIBO must not change the local project. Any use of a FIBO element in the local project must become a staged, reviewable proposal that reuses the existing preview, validation, semantic diff, approval, atomic application, reload, and rollback workflow.

## Goals

- Provide an extensible external-ontology source model with FIBO as the only Phase 5 source.
- Ship one fixed, versioned, reproducible FIBO package without downloading from the network at runtime.
- Load and display an approved curated FIBO Foundations package immediately after FIBO is selected.
- Keep the wider pinned FIBO catalog searchable without displaying the full catalog by default.
- Build Phase 3-compatible semantic descriptions for external FIBO classes and properties.
- Preserve original FIBO IRIs and clearly distinguish external elements from local project elements.
- Distinguish catalog-available, curated, selected, and already-used external elements.
- Search both classes and properties by labels, definitions, IRIs, kind, module, domain, range, and hierarchy context.
- Return deterministic ranked candidates with plain-language match explanations.
- Let users inspect explicit supporting dependencies before staging external reuse or local-extension changes.
- Route every selected FIBO concept and dependency through the existing controlled proposal workflow.
- Keep FIBO assets immutable and prevent local project files from changing before explicit approval.

## Non-Goals

- Supporting external ontology sources other than FIBO in Phase 5.
- Automatically downloading, live-synchronizing, or silently upgrading FIBO.
- Editing, rewriting, or serializing bundled FIBO assets.
- Copying FIBO concepts into the local namespace by default.
- Full automatic ontology import without dependency review and human approval.
- LLM-based retrieval, LLM-based ranking, embeddings, vector search, or external retrieval services.
- Document ingestion, document-to-knowledge-graph conversion, or autonomous ontology construction.
- Entity resolution across documents or external data sources.
- OWL reasoning, inferred relationship generation, or full SHACL authoring and validation.
- Full Protégé feature parity, production graph storage, multi-user collaboration, authentication, authorization, or a separate desktop application.
- Durable staged-session or proposal persistence.
- Actual Git operations inside Entio.
- A custom RDF, OWL, SHACL, Turtle, or search framework.

Current phase-level non-goals and repository rules remain defined by `AGENTS.md`.

## Proposed Behavior

### External ontology source selection

The system will expose an external ontology source model with these common fields:

- Stable source ID.
- Display name.
- Description.
- Exact pinned version.
- Availability status.
- Curated starter package metadata.
- Searchable catalog metadata.
- Original source and licensing metadata.

Only FIBO will be selectable in Phase 5. The model must not hard-code the workbench or CLI around FIBO-specific control flow where a generic source contract is sufficient.

Selecting FIBO will activate the bundled package, expose its manifest and release metadata, show the curated Foundations package, and enable browsing and search. Selection is read-only and must not modify `entio.yaml`, local ontology files, or any other project file.

### Fixed FIBO package

Entio will ship a versioned FIBO asset package tied to one exact release. The package must contain, directly or through a reproducible build process:

- A manifest with the FIBO release and package format version.
- The approved curated Foundations modules.
- Required FIBO dependency files and required OMG Commons files for that release.
- The wider approved searchable catalog.
- Source ontology, module, version, and licensing metadata.
- A reproducible catalog build or index.
- Required licensing and attribution information.

A candidate package layout is:

```text
external-ontologies/
└── fibo/
    ├── manifest.yaml
    ├── foundations/
    ├── catalog/
    ├── dependencies/
    └── indexes/
```

The exact repository asset layout may be finalized by the ExecPlan, but the manifest, catalog, curated package, and dependency closure must be deterministic and tied to the same release. The application must fail clearly when the package is missing, malformed, incomplete, or inconsistent with the expected release.

### Curated Foundations package

The curated package will contain approved broadly useful FIBO foundation areas, selected from the pinned release and generated from their ontology files and explicit imports. The initial selection should cover, as applicable:

- Agreements, contracts, and related commitments.
- Parties, people, organizations, and roles.
- Documents and records.
- Dates, periods, schedules, and time concepts.
- Quantities, monetary amounts, currencies, percentages, and rates.
- Ownership and control.
- Products and services.
- Payments, payers, payees, and payment schedules.

The exact modules and their required imports must be recorded as approved package metadata rather than manually recreating FIBO concepts. Specialized modules remain searchable but are not shown as part of the default curated package unless explicitly included in the approved package definition.

The curated package must preserve original FIBO and dependency IRIs, labels, alternate labels, definitions, entity kinds, direct parents, domains, ranges, source ontology, FIBO domain and module, and available release or maturity metadata when explicitly present.

### External semantic descriptions

Every external FIBO class and property will use the Phase 3 semantic-description shape, extended with external catalog metadata. An external descriptor will contain, where explicitly present:

- Original IRI.
- Preferred label.
- Alternate labels.
- Definitions.
- General annotations.
- Entity kind.
- Source ontology and exact release.
- FIBO domain and module.
- Direct superclasses and direct subclasses.
- Property domains and ranges.
- Local or external status.
- Catalog, curated-package, selected, and already-used status.

Descriptions must organize explicit statements only. Phase 5 will not infer parents, domains, ranges, imports, or dependencies through OWL reasoning.

External descriptors must use Entio-owned RDF and semantic-description contracts. Apache Jena types must remain contained inside the semantic engine.

### Catalog browsing

The VS Code workbench will add a dedicated External Ontologies area. After FIBO is selected, it will show:

- FIBO display name and exact release.
- Manifest and catalog availability status.
- Curated Foundations package categories or modules.
- Classes and properties in the selected category or module.
- Search over the wider pinned catalog.
- Filters for entity kind, FIBO domain, and module.
- Available, selected, already-used, local, and external status.

The UI will display readable labels as the primary value and expose the full IRI in technical details. It will favor module browsing, lists, selected-element details, and small contextual relationship views instead of rendering the entire FIBO graph.

### Deterministic Schema RAG search

Phase 5 search is deterministic catalog retrieval and ranking, not generative AI. It will search both classes and properties.

#### Query inputs

`SchemaSearchQuery` will support:

- Search text.
- Optional entity kind filter: class, object property, datatype property, or annotation property when available.
- Optional FIBO domain filter.
- Optional FIBO module/source filter.
- Optional expected parent-class context.
- Optional expected property domain context.
- Optional expected property range context.
- Optional preference for curated Foundations results.
- Optional local-project context for already-used related elements.

Entity-kind, domain, module, and source filters are deterministic filters. They must not cause candidates of a requested incompatible kind to be returned.

#### Candidate generation

Candidates may be generated from:

- Exact preferred-label matches.
- Case-insensitive preferred-label matches.
- Normalized preferred-label matches.
- Exact, case-insensitive, or normalized alternate-label matches.
- Definition keyword overlap.
- IRI token or substring matches.
- Explicit structural context where the query supplies a parent, domain, or range.

Normalization must be stable and documented in implementation tests. At minimum it must be case-insensitive, trim surrounding whitespace, and treat runs of non-alphanumeric characters as token separators.

#### Candidate ranking

The initial deterministic score will be additive and will use these weights:

- Exact preferred-label match: `100`.
- Case-insensitive preferred-label match: `80`.
- Normalized preferred-label match: `65`.
- Exact alternate-label match: `55`.
- Case-insensitive alternate-label match: `45`.
- Normalized alternate-label match: `35`.
- Definition keyword overlap: up to `30`, using `5` points per distinct matched query token.
- IRI token or substring match: `25`.
- Expected parent-class compatibility: `20`.
- Property domain compatibility: `20`.
- Property range compatibility: `20`.
- Curated Foundations membership: `10`.
- Preferred module or domain context: `5`.
- Related local-project use: `5`.

Each signal contributes at most once per candidate, except definition overlap, which is capped at `30`. Hard filters are applied before scoring. Ties are broken by preferred label, entity kind, original IRI, source module, and then stable catalog order, all ascending. Ranking must produce the same output for the same catalog and query.

Each result must explain the positive signals that contributed to its score in plain language, for example:

- Exact preferred-label match.
- Alternate-label match for `Business Loan`.
- Property domain matches the selected `Loan` class.
- Property range matches `Organization`.
- Found in the FIBO Foundations package.
- Related FIBO parent is already used by the local project.

The result must include original IRI, entity kind, preferred label, alternate labels, definition, source ontology, FIBO domain and module, direct parents, domains, ranges, curated-package status, local-project-use status, score or rank, and match explanation.

### Searching while creating local elements

When a user creates a local class, the workbench may search FIBO by intended label or phrase and show related classes. The user may:

- Reuse a selected FIBO class directly.
- Use a selected FIBO class as the parent of a new local class.
- Reject all candidates and continue locally.

When a user creates a local property, the workbench may search FIBO by intended label or relationship meaning with expected domain and range context. The user may:

- Reuse a selected FIBO object or datatype property directly.
- Inspect compatible FIBO properties.
- Reject all candidates and continue locally.

Search suggestions must never silently replace the user’s local edit or create a local duplicate. The user’s explicit selection determines whether an external reuse or local-extension proposal is prepared.

### External reuse and local extension

Selecting an external element will not immediately change the project. The user must review the element and its explicit supporting dependency set first.

Phase 5 will support these controlled proposal intents, subject to the implementation plan’s final contract names:

- Reference or reuse a FIBO class directly.
- Reference or reuse a FIBO object property directly.
- Reference or reuse a FIBO datatype property directly.
- Create a local class whose explicit parent is a FIBO class.
- Add an approved external ontology/project reference needed for the selected use.
- Stage explicit supporting dependencies.

Reuse preserves the original FIBO IRI. A local extension uses a local IRI and references the FIBO IRI, for example:

```turtle
local:CommercialLoan
    rdfs:subClassOf fibo:Loan .
```

Entio must not create a local copy of a FIBO element by default, change its IRI, copy its definitions into a new concept as if they were local truth, or modify the bundled FIBO files.

### Dependency review

Before staging a selected FIBO concept, Entio will calculate an explicit dependency set from catalog and package metadata. The set may include:

- Direct parent classes.
- Property domains and ranges.
- Source ontology and module references.
- Required imported ontology modules.
- Supporting annotation or identity metadata where required.

The dependency review will show each dependency, why it is required, whether it is already available or used, and the exact external IRI/source. The user may approve the complete set, reject it, or return to search. Entio must not silently add an uncontrolled dependency closure.

### Proposal workflow integration

Every selected FIBO element and dependency must use the existing controlled workflow:

1. Select a FIBO concept.
2. Calculate its explicit supporting dependencies.
3. Review the concept and dependency set.
4. Create staged changes without modifying project files.
5. Build one combined preview.
6. Generate a semantic diff.
7. Run deterministic validation.
8. Verify Turtle serialization and reparsing equivalence.
9. Approve or reject the complete proposal.
10. Leave the project unchanged on rejection.
11. Apply an approved proposal atomically.
12. Reload the project and verify the expected local references or extensions.
13. Restore the prior local source if final verification fails.

The curated package may be loaded and displayed immediately, but no element becomes part of the local project until it passes staging, preview, validation, diff, explicit approval, atomic application, reload, and rollback safeguards.

### CLI boundary

The machine-readable CLI will provide thin commands for:

- Listing external ontology sources.
- Activating FIBO and reading release metadata.
- Browsing curated Foundations categories/modules.
- Fetching an external semantic descriptor.
- Searching the wider pinned catalog.
- Filtering by entity kind, domain, and module.
- Inspecting explicit dependency sets.
- Preparing external reuse or local-extension proposal intents.
- Reusing the existing preview, validation, diff, apply, reject, reload, and rollback operations.

Catalog, search, dependency, and proposal responses will be structured JSON. The CLI will delegate catalog loading, descriptor assembly, candidate generation, ranking, dependency calculation, and RDF change construction to reusable Kotlin modules. It will not parse FIBO separately, rank results independently, modify FIBO files, or write local ontology files outside the existing proposal application path.

### VS Code boundary

The VS Code extension will render Kotlin-owned source metadata, descriptors, search candidates, match explanations, statuses, dependency review, staged changes, combined previews, diffs, and proposal results. It will not parse FIBO files, rank candidates independently, decide dependencies independently, generate RDF changes independently, modify bundled FIBO assets, write local project files directly, or bypass the proposal workflow.

## Inputs And Outputs

### Inputs

- A bundled FIBO package and manifest for one exact release.
- Curated package and dependency metadata.
- A loaded local Entio project for local-use status and proposal context.
- External source selection and catalog browse requests.
- Schema search text, filters, structural context, and local-project context.
- External descriptor and dependency lookup requests.
- Explicit user-selected external concepts and proposal intents.
- Existing proposal baseline and staged-change metadata.

### Outputs

- External ontology source and manifest models.
- Curated package and module browsing models.
- External semantic descriptors using Phase 3 fields plus FIBO metadata and status.
- Deterministically ranked `SchemaCandidate` results with scores and match explanations.
- Explicit dependency sets with reasons and statuses.
- Structured validation reports for package, catalog, search, dependencies, and proposals.
- Typed external reuse and local-extension proposal requests.
- Existing proposal previews, semantic diffs, approval/rejection results, apply/reload results, and rollback results.
- Machine-readable CLI responses for source selection, catalog browsing, descriptors, search, dependency review, and proposal lifecycle operations.
- VS Code external-ontology browsing, search, details, dependency review, staged changes, and proposal workflow states.

## Expected Data Contracts

Phase 5 may introduce or refine these Entio-owned contracts:

- `ExternalOntologySource`: source identity, display metadata, version, availability, curated package, catalog, and attribution metadata.
- `ExternalOntologyManifest`: exact FIBO release, package version, checksums or asset identity, curated modules, dependencies, and licensing metadata.
- `ExternalOntologyCatalog`: deterministic searchable elements, module metadata, and catalog version.
- `ExternalOntologyModule`: FIBO domain/module identity and browse metadata.
- `ExternalOntologyElementStatus`: local/external, available/curated/selected/already-used status.
- `CatalogElement` or `ExternalSemanticDescriptor`: Phase 3 semantic description plus FIBO source, module, release, and status data.
- `SchemaSearchQuery`: text, filters, structural context, and local-project context.
- `SchemaCandidate`: descriptor identity, score/rank, and match explanations.
- `SchemaMatchReason`: typed deterministic ranking signals.
- `ExternalDependency`: exact external element/module and reason for requirement.
- `ExternalDependencySet`: ordered dependencies, selection state, and completeness status.
- `ReuseExternalClassEdit`: proposal intent that preserves the FIBO class IRI.
- `ReuseExternalPropertyEdit`: proposal intent that preserves the FIBO property IRI.
- `CreateLocalSubclassOfExternalClassEdit`: local class creation with an explicit FIBO superclass.
- `AddExternalOntologyReferenceEdit`: approved local project reference to the external FIBO package or required module.

Names may vary in implementation, but the contracts must remain Entio-owned and must not expose Apache Jena types outside the semantic engine.

## Validation And Error Handling

Validation must be deterministic and must return structured issues with enough source, element, dependency, query, and proposal context for the CLI and UI to explain the failure.

The system must report errors for:

- Missing, unreadable, malformed, or unavailable FIBO package.
- Missing, malformed, or mismatched FIBO manifest.
- FIBO release mismatch.
- Missing required dependency or OMG Commons asset.
- Duplicate catalog element IRI.
- Invalid external semantic descriptor.
- Unsupported external ontology source.
- Unsupported entity kind or invalid search filter.
- Missing selected candidate.
- Candidate and requested-kind mismatch.
- Invalid, incomplete, or conflicting dependency set.
- Attempt to modify a bundled FIBO source.
- Attempt to create a duplicate local copy of a FIBO element.
- Missing or stale local-project baseline.
- Conflicting combined proposal changes.
- Turtle serialization or reparsing non-equivalence.

Empty search results are valid responses, not package failures. Ambiguous or tied results must remain explicit and must not be silently selected. Rejected external proposals must not modify local project files. Failed application must use existing rollback behavior. FIBO assets must remain unchanged in every operation.

## Test Cases

### FIBO package and manifest

- Load a valid manifest for the pinned release.
- Reject an unsupported or mismatched release.
- Load the curated Foundations modules and required imports.
- Reject missing, malformed, or incomplete assets.
- Verify catalog construction is reproducible.
- Verify no network access is required.
- Verify checksums or equivalent asset identity when the package format supports them.
- Verify bundled FIBO files are never modified by browse, search, preview, approval, rejection, or rollback.

### External semantic descriptions

- Build descriptors for FIBO classes and object/datatype properties.
- Preserve original IRIs, labels, alternate labels, definitions, hierarchy, domains, ranges, source ontology, FIBO domain, module, and release.
- Preserve explicit RDF term shape and deterministic ordering.
- Report curated, available, selected, already-used, local, and external statuses correctly.
- Do not infer unstated relationships.

### Catalog browsing

- Display curated Foundations modules after selecting FIBO.
- Browse categories and modules without loading the entire catalog into the default view.
- Return classes and properties within a selected module.
- Display full IRIs in technical details.
- Mark local project use and selection status deterministically.

### Schema search

- Find an exact preferred-label match.
- Find case-insensitive and normalized preferred-label matches.
- Find alternate-label matches and report the reason.
- Find definition keyword matches.
- Find IRI matches.
- Apply class-only and property-only filters.
- Apply domain and module filters.
- Rank expected parent-class matches.
- Rank compatible property domains and ranges.
- Rank curated Foundations results and related local-project use according to the defined weights.
- Return stable ordering for equal scores.
- Return plain-language explanations for every positive match signal.
- Return an empty result for a valid query with no candidates.
- Preserve ambiguity rather than selecting arbitrarily.

### Dependency review

- Identify direct parent dependencies.
- Identify property domain and range dependencies.
- Identify source-module and required-import dependencies.
- Show why each dependency is required.
- Block staging when required dependencies are missing or rejected.
- Allow explicit approval of the complete dependency set.
- Reject without changing the local project.

### Proposal integration

- Prepare reuse of an external class while preserving its original IRI.
- Prepare reuse of an external property while preserving its original IRI.
- Prepare a local subclass of an external class.
- Stage selected concepts and dependencies without changing project files.
- Produce one combined semantic diff.
- Validate the external reference and local proposal deterministically.
- Approve and apply atomically.
- Reject without source mutation.
- Block stale local-project baselines.
- Reload and verify the expected result.
- Restore the previous local source on failed final verification.

### CLI and VS Code

- Return machine-readable source, manifest, catalog, descriptor, search, candidate, dependency, and proposal responses.
- Render FIBO release and curated Foundations status.
- Render browse results and external semantic details.
- Render candidate scores, explanations, and statuses.
- Render dependency review and incomplete/blocked states.
- Integrate selected external proposals with the existing staged-change list.
- Refresh workbench state after successful application.
- Keep semantic logic in Kotlin and treat the extension as a presentation/delegation layer.
- Use temporary copies of local projects and bundled test assets; never mutate committed examples or committed FIBO assets.

## Acceptance Criteria

- A user can select FIBO and see the exact bundled release and package status.
- The curated Foundations package is immediately browseable after FIBO selection.
- The wider pinned FIBO catalog is searchable without displaying all elements by default.
- Classes and properties can both be searched and filtered.
- External elements are represented with Phase 3 semantic descriptions and original FIBO IRIs.
- The UI distinguishes external, local, available, curated, selected, and already-used elements.
- Search ranking is deterministic, uses the defined scoring signals, and explains why each result matched.
- A user can inspect an external class or property’s labels, definitions, hierarchy, domains, ranges, source module, and dependencies.
- A user can select a FIBO class/property for direct reuse or use a FIBO class as the parent of a local class.
- Every selected concept and required dependency is reviewed before staging.
- Staging, preview, semantic diff, validation, approval, rejection, atomic apply, reload, and rollback reuse the existing proposal workflow.
- No local project file changes before explicit approval.
- Rejection leaves the local project unchanged.
- Approval preserves original FIBO IRIs and never modifies bundled FIBO assets.
- Failed final verification restores the prior local source.
- The CLI and VS Code consume Kotlin-owned catalog and semantic-search results without duplicating RDF parsing or ranking logic.
- Focused package, semantic-engine, validation, graph-diff, CLI, and VS Code tests pass, including copied-fixture tests.
- Phase 5 adds no additional external ontology source, AI retrieval, embeddings, inference, persistence, Git workflow, or unrelated infrastructure.

## Open Questions

- Which exact FIBO release is approved for the first bundled package?
- Which precise FIBO Foundations modules and imports form the curated starter package for that release?
- Which package format and checksum strategy best support reproducible distribution without adding unnecessary infrastructure?
- How should FIBO licensing and attribution files be packaged and surfaced to users?
- Should external ontology references be stored in `entio.yaml`, a separate project metadata file, or an existing ontology-source contract?
- What exact local graph/configuration changes represent direct external class/property reuse?
- Should a direct reuse proposal add only an external reference, or also stage a typed local declaration that uses the original IRI?
- How should required imported ontology modules be represented in a local project while keeping the bundled assets read-only?
- Which FIBO metadata fields are consistently available enough to become required descriptor fields?
- Should source domain and module filters be hard filters, ranking preferences, or both when a query supplies them?
- Should parent, domain, and range compatibility be supplied as explicit search context only, or also inferred from the current edit form?
- How should multiple equally ranked candidates be presented and selected in the workbench?
- Should the catalog be stored as parsed source files, a generated index, or both?
- How should blank-node external statements be represented in descriptors and dependency review?
- What is the minimum supported set of FIBO maturity or release metadata for the first package?
- How should an external element already used by the local project be displayed and prevented from being redundantly staged?
