# Phase 5 Scope

Phase 5 adds external ontology browsing and basic Schema RAG to Entio.

Phase 3 already gives Entio a structured way to describe ontology classes, properties, annotation properties, and individuals using labels, alternate labels, definitions, structural relationships, source information, and IRIs.

Phase 5 should use that semantic description format for external ontology content, starting with FIBO.

The purpose of Phase 5 is to help users build on trusted existing ontology standards instead of creating every class and property from scratch.

## Phase 5 Goals

Phase 5 should support:

- Letting the user select an external ontology source.
- Supporting FIBO as the only available external ontology source in the first implementation.
- Designing the source-selection model so additional domain ontologies can be added later.
- Shipping Entio with a fixed, versioned FIBO package.
- Loading a curated FIBO Foundations package immediately after the user selects FIBO.
- Showing the curated FIBO Foundations package in a dedicated section of the VS Code workbench.
- Making a wider pinned FIBO catalog searchable without displaying the entire catalog by default.
- Building a Phase 3 semantic description for every external FIBO class and property.
- Preserving original FIBO IRIs.
- Clearly distinguishing external FIBO elements from local project elements.
- Clearly distinguishing:
  - elements available in the external catalog;
  - elements included in the curated Foundations package;
  - elements already referenced or used by the local project.
- Searching both classes and properties.
- Searching by preferred label, alternate label, definition, IRI, entity kind, source module, hierarchy, domain, and range.
- Returning ranked search candidates with a plain-language explanation of why each result matched.
- Letting the user select FIBO concepts and supporting dependencies for use in the local project.
- Requiring every selected FIBO concept and dependency to go through the existing staged proposal workflow.
- Preventing any local project file from changing until the user has previewed, validated, and approved the proposed additions.
- Reusing the existing semantic diff, validation, staging, combined preview, approval, atomic save, reload, and rollback behavior.

Phase 5 should make FIBO immediately useful inside Entio while keeping external ontology reuse controlled, reviewable, and safe.

## Working Definitions

- external ontology source: a trusted ontology package that is not authored inside the current Entio project.

- ontology catalog: the searchable set of classes, properties, labels, definitions, hierarchy, domain, range, and source information made available from an external ontology.

- pinned release: one specific approved version of FIBO that remains fixed until Entio deliberately upgrades it.

- curated FIBO Foundations package: a selected group of broadly useful FIBO foundational modules and their required dependencies that Entio displays by default.

- wider FIBO catalog: the rest of the approved pinned FIBO release that is searchable but not shown in full by default.

- external semantic description: the Phase 3 structured description of an external FIBO class or property.

- available external element: a FIBO element that can be browsed or searched but has not been selected for use in the local project.

- selected external element: a FIBO class or property the user wants to reference or use in the local project.

- supporting dependency: another external class, property, or ontology module needed to understand or safely use the selected element.

- reuse: using the original FIBO IRI directly rather than creating a duplicate local copy.

- local extension: a local class or property that builds on a FIBO concept, such as a local class that is a subclass of a FIBO class.

- basic Schema RAG: deterministic search and ranking over the bundled FIBO catalog using labels, definitions, hierarchy, domain, range, entity kind, and source context.

## External Ontology Source Selection

The workbench should include an external ontology section.

The user should be able to:

- View available external ontology sources.
- Select FIBO.
- See the selected FIBO release.
- See whether the bundled catalog is available and loaded.
- See the curated Foundations package.
- Search the wider pinned FIBO catalog.

Only FIBO is required in Phase 5.

The design should allow later ontology sources to provide the same basic information:

- Source ID.
- Display name.
- Version.
- Description.
- Availability status.
- Curated starter package.
- Searchable catalog.
- Original source metadata.

Selecting FIBO should not change the local ontology project.

Selecting FIBO should only:

- Activate the bundled FIBO catalog.
- Display the curated Foundations package.
- Enable FIBO browsing and search.
- Make FIBO semantic descriptions available to the UI and CLI.

## Fixed FIBO Package

Entio should ship with a fixed FIBO package rather than automatically downloading the latest release.

The bundled package should include:

- One exact FIBO release.
- A manifest identifying the release.
- The approved curated Foundations modules.
- Required dependency files.
- Required OMG Commons files when the selected FIBO release imports them.
- The wider approved searchable FIBO catalog.
- Source, module, and version metadata.
- A reproducible catalog build or index.
- Licensing and attribution information required for distribution.

The FIBO package should be stored or packaged as a versioned Entio asset.

A possible structure is:

```text
external-ontologies/
└── fibo/
    ├── manifest.yaml
    ├── foundations/
    ├── catalog/
    ├── dependencies/
    └── indexes/
```

The exact structure may change in the specification or ExecPlan, but the package must remain reproducible and tied to one known release.

Entio should not silently update FIBO when the application starts.

A future FIBO upgrade should be a deliberate, tested, versioned Entio change.

## Curated FIBO Foundations Package

The curated Foundations package should provide a useful starting point for common financial modeling.

It should include approved foundational areas such as:

- Agreements and contracts.
- Parties, people, organizations, and roles.
- Documents and records.
- Dates, periods, schedules, and time-related concepts.
- Quantities, monetary amounts, currencies, percentages, and rates.
- Ownership and control.
- Products and services.
- Payments, payers, payees, and payment schedules.

The exact ontology files must be selected from the pinned FIBO release in the Phase 5 specification.

The curated package should be generated from approved modules and required imports, not by manually recreating FIBO concepts.

The curated package should preserve:

- Original FIBO and dependency IRIs.
- Original labels.
- Alternate labels.
- Definitions.
- Entity kinds.
- Direct parents.
- Domains.
- Ranges.
- Source ontology.
- FIBO domain and module.
- Release or maturity metadata where available.

The curated package should not include the entire FIBO release by default.

Specialized areas such as detailed loan products, securities, derivatives, corporate actions, or market data should remain available through the wider searchable catalog unless explicitly added later as separate starter packages.

## Dedicated Workbench Section

The VS Code workbench should include a dedicated external ontology area.

The area should show:

- Selected external ontology source.
- FIBO release version.
- Curated Foundations package status.
- Foundation categories or modules.
- Classes and properties within those categories.
- Search across the wider FIBO catalog.
- Filters by entity kind, domain, module, or source.
- Local, external, available, selected, and already-used status.

For each external element, the user should be able to view:

- Preferred label.
- Alternate labels.
- Definition.
- Entity kind.
- Original FIBO IRI.
- Source ontology.
- FIBO domain and module.
- Direct parent classes.
- Domain and range where relevant.
- Related local project elements where practical.
- Whether the element is available, selected, or already used by the project.

The UI should use readable labels as the primary display value and show the full IRI in technical details.

The UI should not present all FIBO content in one large graph.

The first implementation should favor:

- Module browsing.
- Class and property lists.
- Search.
- Selected-element details.
- Small contextual relationship views where useful.

## External Semantic Descriptions

Every external FIBO class and property should use the same semantic description format established in Phase 3.

The Kotlin semantic engine should read the bundled FIBO statements and assemble descriptions containing:

- IRI.
- Preferred label.
- Alternate labels.
- Definitions.
- General annotations.
- Entity kind.
- Source ontology.
- FIBO domain and module.
- Direct superclasses.
- Direct subclasses where available from explicit statements.
- Property domains.
- Property ranges.
- Local or external status.
- Available, selected, or already-used status.

The semantic engine should organize only information explicitly present in the bundled ontology files.

Phase 5 should not add OWL reasoning or infer new relationships.

The same external semantic descriptions should be available through:

- Kotlin services.
- Machine-readable CLI responses.
- VS Code workbench.
- Basic Schema RAG.
- Future AI and agent workflows.

## Basic Schema RAG Search

Phase 5 should provide deterministic search over the bundled FIBO catalog.

The wider pinned FIBO catalog should be searchable even though only the curated Foundations package is visible by default.

Search should cover both classes and properties.

### Candidate Generation

The first stage should find candidates using:

- Exact preferred-label match.
- Case-insensitive preferred-label match.
- Normalized-label match.
- Alternate-label match.
- Definition keyword match.
- IRI match.
- Entity-kind filter.
- Source module or domain filter.

Embeddings are not required in Phase 5.

### Candidate Ranking

Candidates should be ranked using deterministic signals such as:

- Strength of label match.
- Alternate-label match.
- Definition keyword overlap.
- Requested entity kind.
- Expected parent-class context.
- Property domain compatibility.
- Property range compatibility.
- Whether the result is in the curated Foundations package.
- Whether the result comes from a preferred FIBO module.
- Whether the local project already uses a related FIBO concept.

The exact ranking weights should be defined and tested in the specification.

The ranking should not use an LLM.

### Search Result

Each search candidate should include:

- Original FIBO IRI.
- Entity kind.
- Preferred label.
- Alternate labels.
- Definition.
- Source ontology.
- FIBO domain and module.
- Direct parents.
- Domains.
- Ranges.
- Foundations-package status.
- Local-project-use status.
- Score or rank.
- Plain-language match explanation.

A result explanation might say:

- Exact preferred-label match.
- Alternate-label match for “Business Loan.”
- Property domain matches the selected Loan class.
- Property range matches Organization.
- Found in the FIBO Foundations package.
- Related FIBO parent already used by the project.

## Searching While Creating A Local Element

The workbench should allow a user who is creating a local class or property to search FIBO before completing the local creation.

For a local class, the user should be able to:

- Enter the intended label or search phrase.
- Search FIBO classes.
- View related classes.
- Select a FIBO class to reuse directly.
- Select a FIBO class as the parent of a new local class.
- Reject all candidates and continue with a local class.

For a local property, the user should be able to:

- Enter the intended label or relationship meaning.
- Search FIBO properties.
- Provide expected domain and range context.
- View matching FIBO properties.
- Select a FIBO property to reuse.
- Reject all candidates and continue with a local property.

Entio should search properties as well as classes.

The system should not reuse the correct nouns while inventing unnecessary new relationships.

## Using A FIBO Element In The Local Project

Selecting an external element does not immediately change the project.

The user should choose how the external element will be used.

Supported Phase 5 actions may include:

- Reference the FIBO class directly.
- Use a FIBO class as the parent of a new local class.
- Use a FIBO object property directly.
- Use a FIBO datatype property directly.
- Add an approved ontology import or project reference.
- Stage required supporting dependencies.

Entio should preserve the original FIBO IRI.

Entio should not:

- Create a local copy of the FIBO concept by default.
- Change the FIBO IRI.
- Copy FIBO definitions into a new local concept as though the local concept were the original.
- Modify bundled FIBO files.

A local extension may reference a FIBO concept, for example:

```turtle
local:CommercialLoan
    rdfs:subClassOf fibo:Loan .
```

## Dependency Review

When a user selects a FIBO class or property, Entio should identify the supporting external elements needed for the proposed use.

Phase 5 dependency review should consider explicit information such as:

- Direct parent classes.
- Property domains.
- Property ranges.
- Source ontology.
- Required imported ontology modules.
- Required dependency files.
- Supporting annotation or identity metadata where required.

Entio should show the dependency list before the proposal is staged.

The user should be able to:

- See which dependencies are required.
- See why each dependency is needed.
- Approve the complete dependency set.
- Reject the selection.
- Return to search and choose a different candidate.

Entio should not silently add an uncontrolled set of FIBO modules.

Phase 5 should use explicit dependency rules from the pinned catalog and should not require full OWL reasoning.

## Existing Proposal Workflow Integration

Every FIBO concept and dependency selected for local-project use must follow the existing controlled workflow.

The required flow is:

1. The user selects a FIBO concept.
2. Entio identifies its supporting dependencies.
3. The user reviews the concept and dependency set.
4. Entio creates staged changes without modifying project files.
5. Entio builds one combined preview.
6. Entio generates a semantic diff.
7. Entio validates the proposed project graph.
8. Entio verifies Turtle serialization and reparsing.
9. The user approves or rejects the complete proposal.
10. Rejection leaves the project unchanged.
11. Approval applies the proposal atomically.
12. Entio reloads the project and confirms the expected result.
13. Entio restores the prior source if final verification fails.

The curated Foundations package may be loaded and displayed immediately, but using any of its elements in the local project still requires staging, preview, validation, and explicit approval.

The same rule applies to specialized FIBO elements found through search.

## Expected Project Concepts

Phase 5 may introduce or refine:

- `ExternalOntologySource`
- `ExternalOntologyManifest`
- `ExternalOntologyVersion`
- `ExternalOntologyCatalog`
- `ExternalOntologyModule`
- `ExternalOntologyElementStatus`
- `CuratedPackage`
- `CatalogElement`
- `ExternalSemanticDescriptor`
- `SchemaSearchQuery`
- `SchemaCandidate`
- `SchemaMatchReason`
- `ExternalDependency`
- `ExternalDependencySet`
- `ReuseExternalClassEdit`
- `ReuseExternalPropertyEdit`
- `CreateLocalSubclassOfExternalClassEdit`
- `AddExternalOntologyReferenceEdit`

Names may vary in the specification, but responsibilities should remain clear.

The contracts should use Entio-owned RDF and semantic-description types and should not expose Apache Jena types outside the semantic engine.

## CLI Requirements

Phase 5 should extend the machine-readable CLI so the VS Code extension can:

- List external ontology sources.
- Select or activate FIBO.
- Read the pinned FIBO release metadata.
- Browse the curated Foundations package.
- Fetch external semantic descriptions.
- Search the wider FIBO catalog.
- Filter classes and properties.
- Return ranked candidates and match explanations.
- Inspect dependency sets.
- Prepare staged reuse or local-extension proposals.
- Preview, validate, diff, apply, reject, reload, and roll back external ontology changes.

The CLI should remain thin.

It should not:

- Implement search ranking independently.
- Parse FIBO separately from the semantic engine.
- Construct RDF changes independently.
- Modify FIBO files.
- Write local ontology files outside the existing proposal application path.

Structured JSON should be used for catalog, search, dependency, and proposal responses.

## VS Code Workbench Requirements

The workbench should add:

- An External Ontologies section.
- A FIBO source selector.
- FIBO release information.
- A visible curated Foundations browser.
- Category or module navigation.
- Class and property browsing.
- Search across the wider FIBO catalog.
- Entity-kind and module filters.
- External semantic details.
- Original FIBO IRI display.
- Candidate rank and match explanation.
- Available, selected, and already-used status.
- Dependency review.
- Actions for reuse, local subclassing, and supported property reuse.
- Existing staged-change list integration.
- Combined preview and semantic diff.
- Approval, rejection, refresh, and changed-source opening.

The extension should not:

- Parse FIBO files.
- Rank results independently.
- Decide dependencies independently.
- Generate local RDF changes independently.
- Modify bundled FIBO files.
- Write local project files directly.
- Bypass the existing proposal workflow.

## Validation Expectations

Phase 5 should add deterministic validation for:

- Missing or unavailable external ontology package.
- Unsupported external ontology source.
- Invalid or missing FIBO manifest.
- FIBO release mismatch.
- Missing bundled dependency file.
- Duplicate catalog element IRI.
- Invalid external semantic description.
- Search request with unsupported entity kind.
- Missing selected candidate.
- Candidate and requested-kind mismatch.
- Invalid or incomplete dependency set.
- Attempt to modify a bundled FIBO source.
- Attempt to create a duplicate local copy of a FIBO element.
- Stale local-project baseline.
- Combined proposal conflicts.
- Turtle round-trip non-equivalence.

Validation should not use AI judgment or full OWL reasoning.

## Testing Requirements

Phase 5 should include focused tests for:

### FIBO Package

- Manifest loading.
- Exact release validation.
- Curated module inclusion.
- Required dependency loading.
- Missing or invalid asset handling.
- No network requirement.
- Reproducible catalog construction.

### External Semantic Descriptions

- Descriptor creation for FIBO classes and properties.
- Preferred labels.
- Alternate labels.
- Definitions.
- Hierarchy.
- Domain and range.
- Source domain and module.
- Original IRI preservation.
- Deterministic ordering.

### Catalog Browsing

- Curated Foundations package visibility.
- Module and category navigation.
- Available, selected, and already-used status.
- Full IRI display.

### Schema Search

- Exact label search.
- Normalized label search.
- Alternate-label search.
- Definition keyword search.
- IRI search.
- Class-only search.
- Property-only search.
- Parent-context ranking.
- Domain and range compatibility ranking.
- Match explanations.
- Stable ordering.

### Dependency Review

- Direct parent dependency.
- Domain dependency.
- Range dependency.
- Source-module dependency.
- Required import dependency.
- Explicit review and approval.
- Rejection without project changes.
- Missing dependency blocking.

### Proposal Integration

- Reuse external class.
- Create local subclass of external class.
- Reuse external property.
- Stage external concepts with dependencies.
- Combined semantic diff.
- Validation.
- Approval and atomic application.
- Rejection without source mutation.
- Stale-baseline blocking.
- Reload and rollback.

### UI And CLI

- Machine-readable catalog and search responses.
- VS Code Foundations browser.
- Search result rendering.
- Candidate explanation rendering.
- Dependency review rendering.
- Staged proposal integration.
- Refresh after successful application.

Tests should use bundled test assets and temporary project copies.

Tests must not modify committed FIBO assets or committed example projects.

## Non-Goals

Phase 5 should not include:

- Additional external ontology sources beyond FIBO.
- Automatic downloading of the newest FIBO release.
- Live synchronization with the FIBO repository.
- Editing or modifying FIBO itself.
- Copying FIBO concepts into the local namespace by default.
- Full automatic ontology import without user review.
- LLM-based retrieval or ranking.
- Embeddings or vector search.
- Document ingestion.
- Document-to-KG conversion.
- Autonomous ontology construction.
- Entity resolution across documents or external data sources.
- Full OWL reasoning.
- Full SHACL authoring or validation.
- Full Protégé parity.
- Production graph storage.
- Multi-user collaboration.
- Authentication or authorization.
- Long-term proposal persistence.
- Actual Git operations inside Entio.
- A separate desktop application.

## Success Criteria

Phase 5 is successful if a user can:

- Open the External Ontologies section.
- Select FIBO.
- See the exact bundled FIBO release.
- Immediately browse the curated FIBO Foundations package.
- View classes and properties using Phase 3 semantic descriptions.
- Search the wider pinned FIBO catalog.
- Search both classes and properties.
- See ranked candidates with clear explanations.
- Inspect original FIBO IRIs, definitions, hierarchy, domains, ranges, source modules, and dependency information.
- Select a FIBO class or property.
- Review all required supporting dependencies.
- Stage the selected external concept and dependencies without modifying project files.
- Preview one combined semantic diff.
- View deterministic validation and Turtle round-trip results.
- Reject the proposal with no project changes.
- Approve and apply the complete proposal atomically.
- Reload the project and see the expected FIBO references or local extensions.
- Confirm that original FIBO IRIs were preserved.
- Confirm that bundled FIBO files were not modified.
- Recover the prior local source automatically if final verification fails.

Phase 5 should establish Entio’s first trusted external ontology catalog and basic Schema RAG workflow while preserving the existing safety rule: nothing is added to the local project without staging, preview, deterministic validation, semantic diff, explicit approval, atomic application, reload, and rollback.
