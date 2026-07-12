# Entio Phase 2.5 Implementation Summary

## Status And Scope

Phase 2.5 completes the basic user-facing ontology editing surface that Phase 2 left partial. It extends the existing Kotlin semantic engine, machine-readable CLI, and VS Code workbench without adding a second RDF or ontology mutation pathway.

This summary describes the implementation currently present in the repository. It is based on the source files, tests, examples, Phase 2.5 specification, and Phase 2.5 ExecPlan rather than on the plan alone.

Phase 1, Phase 1.5, and Phase 2 remain the foundation underneath Phase 2.5:

- Phase 1 established project loading, Turtle parsing, symbol extraction, deterministic validation, semantic graph diffs, and the CLI.
- Phase 1.5 corrected RDF-term fidelity across graph triples, parsing, symbols, validation, and diffs, and made project loading reusable.
- Phase 2 introduced typed ontology edits, graph previews, proposals, baselines, semantic-equivalence checks, atomic application, rollback, machine-readable proposal commands, and the initial VS Code workbench.
- Phase 2.5 makes the remaining supported edit kinds available through the CLI and focused VS Code form modes.

## Phase 1 Foundation

Phase 1 implemented a small Kotlin/JVM Core Semantic Engine for local Entio projects.

The Phase 1 foundation can:

- Load and validate a local `entio.yaml` project configuration.
- Resolve relative Turtle ontology sources inside the project root.
- Parse small Turtle/RDF files using Apache Jena.
- Preserve IRIs, blank nodes, plain literals, datatyped literals, and language-tagged literals in Entio-owned RDF term types.
- Extract basic classes, properties, shapes, and individuals as loaded symbols.
- Produce deterministic validation reports with structured issue codes.
- Compare graph states and produce semantic diffs.
- Expose project validation, symbol listing, and directory diffing through a thin CLI.

Phase 1 intentionally did not include ontology editing, proposal approval, source persistence, VS Code, web, document ingestion, AI agents, Schema RAG, entity resolution, production graph storage, full FIBO indexing, OWL reasoning, full SHACL validation, or a custom RDF/OWL/SHACL framework.

## Repository Structure

```text
core-types/
semantic-engine/
validation-engine/
graph-diff/
cli/
shared/
vscode-extension/
examples/simple-ontology/
docs/architecture/
docs/specs/
docs/execplans/
docs/decisions/
docs/phase-summaries/
skills/
```

The Kotlin/JVM modules are built as a Gradle multi-module project. The VS Code extension is a separate TypeScript project that invokes the Kotlin CLI through a machine-readable process boundary.

## Module Responsibilities

### `core-types`

Defines shared Entio contracts and does not parse RDF or implement ontology behavior. Its contracts include project aggregates, RDF terms, graph states, graph changes, proposals, baselines, validation reports, semantic diffs, typed ontology edits, and apply or rollback results.

### `semantic-engine`

Owns project configuration loading, ontology source resolution, Turtle parsing, symbol extraction, graph previews, typed-edit translation, proposal creation, Turtle round-trip verification, atomic proposal application, reload, and rollback.

Apache Jena is contained here for RDF/Turtle parsing and serialization. Other modules consume Entio-owned types where possible.

### `validation-engine`

Validates projects and proposals deterministically. It checks project structure, target sources, previews, current baselines, semantic-equivalence results, referenced resources, expected symbol and property kinds, duplicate additions, missing removals, literal compatibility, explicit domain and range compatibility, and superclass safety.

The validation is based on explicit graph statements. It does not perform full OWL reasoning or full SHACL validation.

### `graph-diff`

Compares graph states and creates human-reviewable semantic diffs. It handles additions and removals and includes a focused label-change presentation. It also attaches proposal diffs and formats them for CLI output.

### `cli`

Provides the thin command-line boundary. It parses arguments, loads the project, calls reusable engine services, formats terminal or JSON output, and returns process exit codes. It does not parse Turtle or write RDF directly.

The CLI exposes:

- `validate`
- `symbols`
- `diff`
- `project-summary`
- `proposal-preview`
- `proposal-validate`
- `proposal-diff`
- `proposal-apply`
- `proposal-reject`

### `shared`

Remains intentionally minimal. It contains only generic utilities and does not contain Entio ontology, validation, diff, or proposal logic.

### `vscode-extension`

Provides a minimal ontology workbench. It detects an Entio project, displays sources and symbols, collects edit fields, invokes the machine-readable CLI, renders preview and validation results, enables approval or rejection, refreshes after application, and opens changed sources.

The extension does not parse RDF, construct graph triples, validate ontology semantics, or write Turtle directly.

## Main Data Objects And Contracts

The principal shared contracts are defined in `core-types`.

### Project And Ontology Contracts

- `EntioProjectConfig` stores the project name and configured ontology sources.
- `OntologySourceReference` describes a configured source by ID, relative path, and format.
- `ResolvedOntologySource` records the safe project-root-relative path used by the engine.
- `LoadedOntology` contains a resolved source and its graph.
- `EntioProject` aggregates configuration, resolved sources, loaded ontologies, symbols, and the combined graph.
- `OntologyFormat` currently supports `Turtle` only.

### RDF And Graph Contracts

- `RdfTerm` is the root term abstraction.
- `RdfResource` represents resource terms; `Iri` and `BlankNodeResource` are its implementations.
- `RdfLiteral` stores lexical form, optional datatype IRI, and optional language tag.
- `GraphTriple` stores RDF-term-aware subject, predicate, and object data while retaining compatibility views for older consumers.
- `GraphState` stores a set of graph triples.
- `GraphChange` and `ChangeSet` represent additions and removals.
- `ChangePreview` contains the in-memory preview graph and the change set used to create it.

### Symbol And Validation Contracts

- `LoadedSymbol` stores an IRI, optional label, `SymbolKind`, and source ID.
- `ValidationIssue` stores severity, stable code, message, and optional source.
- `ValidationReport` stores a valid or invalid status and deterministically ordered issues.
- `ValidationSeverity` and `ValidationStatus` model fixed validation states explicitly.

### Proposal And Diff Contracts

- `ChangeProposal` stores proposal identity, title, target source, change set, baseline, status, preview, diff, validation report, source impact, and optional review metadata.
- `ProposalBaseline` stores project, target-source, source-file, and graph fingerprints used for stale checks.
- `SourceFileImpact` identifies the ontology files affected by a proposal.
- `SemanticDiff` contains `SemanticDiffEntry` values for added, removed, or changed graph facts.
- `SemanticEquivalenceResult` reports equivalent, non-equivalent, or failed Turtle round-trip verification.
- `ApplyProposalResult` and `RollbackResult` describe application and recovery outcomes.

### Typed Edit Contracts

`TypedOntologyEdit` has concrete contracts for:

- Creating classes.
- Creating object and datatype properties.
- Setting property domains and ranges.
- Creating individuals and assigning types.
- Adding object-property and datatype-property assertions.
- Adding and removing direct superclass relationships.
- Setting entity labels.

Phase 2.5 composes some requests from these existing contracts. For example, an individual with a label is represented by an individual edit plus a label graph change, and replacement operations are represented by explicit removals followed by additions.

## End-To-End Workflow

All supported edits use the same workflow:

1. The CLI or VS Code workbench collects a target source, edit kind, and edit-specific fields.
2. The boundary normalizes the request and maps it to an existing typed edit contract.
3. The semantic engine translates the typed edit into graph changes. Phase 2.5 composes optional domain, range, label, or replacement changes where needed.
4. `ProjectLoader` loads the current project and graph.
5. `ProposalCreator` creates a proposal against the current project and source fingerprints.
6. `GraphChangePreviewer` applies the change set in memory without changing source files.
7. `graph-diff` generates the semantic diff and source-file impact.
8. The preview graph is serialized to temporary Turtle, reparsed, and checked for semantic equivalence.
9. `ProjectValidator` and `ProposalValidator` produce deterministic validation reports.
10. The CLI or workbench presents the preview, diff, validation result, equivalence result, and affected source.
11. Rejection returns a rejected result without writing the source.
12. Approval through the CLI apply command or the VS Code approval action passes the current proposal to `ProposalApplier`.
13. `ProposalApplier` rechecks the baseline, writes only the target ontology source, reloads it, and compares the saved graph with the approved preview.
14. A stale proposal is blocked before writing. A save or verification failure restores the previous source and reports rollback status.
15. The workbench refreshes the project model after successful application and can open the changed source.

This workflow is git-like by analogy only. Entio does not perform Git staging, commits, pushes, branches, or pull requests, and it does not store long-term proposal history.

## Developer Commands

From the repository root:

```bash
./gradlew test
./gradlew build
./gradlew check
```

Run focused Kotlin module tests when working on one area:

```bash
./gradlew :core-types:test
./gradlew :semantic-engine:test
./gradlew :validation-engine:test
./gradlew :graph-diff:test
./gradlew :cli:test
```

Run the CLI from the repository root. Because Gradle runs the application from the `cli` module directory, repository-relative example paths use `../examples`:

```bash
./gradlew :cli:run --args="validate ../examples/simple-ontology"
./gradlew :cli:run --args="symbols ../examples/simple-ontology"
./gradlew :cli:run --args="diff ../examples/simple-ontology ../examples/simple-ontology"
./gradlew :cli:run --args="project-summary ../examples/simple-ontology"
```

The proposal commands accept `--edit` plus edit-specific options. For example:

```bash
./gradlew :cli:run --args="proposal-preview ../examples/simple-ontology simple --edit create-object-property --property-iri https://example.com/hasAccount --domain-iri https://example.com/Customer --range-iri https://example.com/Account"
```

The supported edit kinds are `create-class`, `create-object-property`, `create-datatype-property`, `set-property-domain`, `set-property-range`, `create-individual`, `assign-individual-type`, `add-object-property-assertion`, `add-datatype-property-assertion`, `add-superclass`, `remove-superclass`, and `set-entity-label`. `--replace-existing` is available for replacement-style domain, range, and label changes.

For the VS Code boundary:

```bash
cd vscode-extension
npm install
npm run compile
npm test
```

`npm test` compiles the extension and runs the Node-based request, rendering, model, and result-boundary tests.

## Tests And Fixtures

The repository contains focused Kotlin tests in each Gradle module and TypeScript tests under `vscode-extension/src/test/`.

Coverage includes:

- Core data contracts, RDF terms, graph changes, diffs, proposals, and reports.
- YAML project configuration loading and safe source resolution.
- Turtle parsing, RDF-term fidelity, symbol extraction, previews, round-trip equivalence, project loading, proposal creation, application, and rollback.
- Deterministic project and proposal validation.
- Graph diff generation and formatting.
- CLI text and machine-readable commands.
- Copied-fixture Phase 1.5, Phase 2, and Phase 2.5 regressions.
- VS Code request normalization, form rendering, CLI argument construction, project modeling, preview normalization, action results, refresh boundaries, and source-opening messages.

The committed example project is:

```text
examples/simple-ontology/
  entio.yaml
  ontology/simple.ttl
  .vscode/settings.json
```

It is a small Turtle fixture containing the current example classes and property. End-to-end tests copy this project to a temporary directory and create focused temporary Turtle fixtures when properties, individuals, assertions, or hierarchy relationships are needed. Tests do not modify the committed example in place.

## Phase 1 Non-Goals

The Phase 1 foundation intentionally did not include:

- VS Code or web application infrastructure.
- Document ingestion or document-to-knowledge-graph conversion.
- Autonomous AI agents, LLM-generated ontology edits, or Schema RAG.
- Entity resolution, external data discovery, or multi-user collaboration.
- Stardog integration, production graph storage, or full FIBO indexing.
- Full Protégé feature parity.
- Full OWL class-expression editing, OWL reasoning, or full SHACL authoring and validation.
- A custom RDF, OWL, SHACL, or Turtle framework.
- Long-term project or proposal version history.

Phase 2 and Phase 2.5 add controlled editing and review workflows, but they do not remove these boundaries.

## Known Limitations And Follow-Up Work

- Turtle is the only configured ontology format.
- Jena serialization preserves graph meaning but not original Turtle formatting, comments, or source layout.
- Semantic diffs remain graph-triple based, with focused label-change formatting rather than a complete ontology-aware semantic diff language.
- Validation uses explicit graph facts and deterministic compatibility checks. It does not provide OWL inference, cycle reasoning, or full SHACL validation.
- Proposal state is created within a CLI or workbench interaction and is not persisted as long-term project history.
- The `ProposalReview` contract exists, but there is no multi-user review store, authentication, authorization, or server-backed proposal system.
- The CLI apply command performs the approval boundary within one invocation; there is no durable human-review queue.
- The VS Code workbench has focused form modes for all Phase 2.5 edit kinds, but current controls collect IRIs and values as text fields. They are not full symbol selectors backed by ontology browsing.
- VS Code tests cover the TypeScript request, rendering, process-boundary, and result models. They do not launch a full Extension Development Host or provide browser-style UI integration tests.
- Property, domain, range, and label replacement currently target one explicit value at a time.
- Blank-node identifiers remain parser-local and should not be treated as durable business identities.
- Multiple ontology sources are supported by the project model, but the workbench remains a small single-target-source editing surface.

## Plan And Implementation Mismatches

The Phase 2.5 spec and ExecPlan describe the intended target and are useful design context, but the implementation has a few concrete differences:

1. The plan describes entity and class selectors in the workbench. The current forms use IRI input controls rather than selectors that discover and constrain loaded symbols.
2. The plan calls for extensive VS Code apply, reject, refresh, and open-source regression coverage. The current tests cover the shared request and result boundaries and rendering; the copied-fixture end-to-end workflow is implemented and tested on the Kotlin/CLI side rather than through a launched Extension Development Host.
3. The plan describes a human-reviewable proposal lifecycle. The core proposal and review contracts exist, but proposal state is still in memory and the CLI apply command approves within the current invocation instead of persisting a review record.
4. The current `README.md` still contains Phase 2-era wording that says the CLI and VS Code edit surface exposes only `create-class`. That statement is stale relative to the Phase 2.5 source and tests; this summary is based on the implementation rather than that outdated limitation note.
5. The repository guidance and README describe Phase 2 as the latest completed phase, while the Phase 2.5 implementation branches and merged local `main` contain the completed Phase 2.5 work. A later documentation-status update should align those top-level documents with this summary.

These differences do not indicate a second semantic pathway. They identify where the current implementation is narrower than the full product vision or where status documentation has not yet caught up with the completed slices.
