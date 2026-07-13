# Entio Phase 2.5 Implementation Summary

## Status And Scope

Phase 2.5 completes the basic user-facing ontology editing surface that Phase 2 left partial. It extends the existing Kotlin/JVM semantic engine, machine-readable CLI, and VS Code workbench without introducing a second RDF or ontology mutation pathway.

This document describes the implementation currently present in the repository. It is based on the source files, tests, examples, Phase 2.5 architecture scope, specification, and ExecPlan. The specification and ExecPlan describe the intended target; the limitations and mismatches near the end record where the implementation is narrower.

Phase 2.5 builds on these earlier foundations:

- Phase 1 established local project loading, Turtle parsing, symbol extraction, deterministic validation, graph diffs, and the initial CLI.
- Phase 1.5 corrected RDF-term fidelity across graph triples, parsing, symbols, validation, and diffs, and made project loading reusable.
- Phase 2 introduced typed ontology edits, graph previews, proposals, baselines, semantic-equivalence checks, atomic application, rollback, machine-readable proposal commands, and the initial VS Code workbench.
- Phase 2.5 exposes the remaining supported edit kinds through the CLI and focused VS Code form modes.

## What Phase 2.5 Implemented

The completed Phase 2.5 slices add the following user-facing edit kinds:

- Create an object property.
- Create a datatype property.
- Add or replace a property domain.
- Add or replace a property range.
- Create an individual, optionally with an initial type.
- Assign a type to an existing individual or resource.
- Add an object-property assertion.
- Add a datatype-property assertion with a literal value, datatype, or language tag.
- Add or remove a direct superclass relationship.
- Add or replace an entity label.

The existing `create-class` flow remains supported. Each edit is prepared through the same controlled workflow:

1. Parse the CLI or workbench request.
2. Translate it into an existing `TypedOntologyEdit` contract.
3. Compose the translated changes with optional label, domain, range, or replacement changes.
4. Create a proposal against the current project and source baseline.
5. Apply the change set to an in-memory preview graph.
6. Generate a semantic diff.
7. Run deterministic proposal validation.
8. Serialize the preview to temporary Turtle, reparse it, and verify semantic equivalence.
9. Return the preview, diff, validation report, equivalence result, and affected source information.
10. Reject without writing, or approve and apply only a valid, current proposal.
11. Reload the project and verify the saved graph.
12. Restore the prior source and report rollback status if save verification fails.
13. Refresh the VS Code workbench after successful application and allow the changed source to be opened.

The Kotlin engine remains responsible for RDF and ontology behavior. The CLI and VS Code extension collect input, call the engine boundary, and display structured results.

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

The six Kotlin modules are built by the root Gradle multi-module project. `vscode-extension` is a separate TypeScript project. It invokes the Kotlin CLI through a machine-readable process boundary rather than parsing Turtle, constructing RDF triples, or writing ontology files itself.

Phase 2.5 was implemented as ten independent slices. Each slice was developed on its own branch, tested, committed, pushed, and merged into local `main` in dependency order. The completion records are stored under `docs/decisions/phase-2.5-slice-*.md`.

## Module Responsibilities

### `core-types`

Defines shared Entio contracts. It does not parse RDF or implement ontology behavior. Its contracts cover project data, RDF terms, graph states and changes, typed ontology edits, proposals, baselines, validation reports, semantic diffs, application results, and rollback results.

### `semantic-engine`

Owns project configuration loading, safe ontology-source resolution, Turtle parsing, symbol extraction, graph previews, typed-edit translation, proposal creation, Turtle round-trip verification, atomic application, reload, and rollback.

Apache Jena is used here for RDF/Turtle parsing, graph handling, and serialization. Entio-specific workflow code is kept around that library boundary.

### `validation-engine`

Produces deterministic project and proposal validation reports. In addition to the Phase 1 checks, proposal validation handles required structure, target-source and baseline checks, referenced resources, expected property and symbol kinds, duplicate changes, missing removals, literal compatibility, explicit domain/range compatibility, and superclass safety.

Validation uses explicit graph facts. It does not perform full OWL reasoning or full SHACL validation.

### `graph-diff`

Compares graph states and produces semantic diffs containing added and removed facts. It also generates proposal diffs and provides stable human-readable formatting, including focused handling for label changes.

### `cli`

Provides the thin command-line boundary. Picocli parses arguments, reusable engine services prepare and apply proposals, and the CLI emits text or machine-readable JSON responses with process exit codes. Ontology parsing, validation, graph diffing, and source writes remain outside CLI command code.

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

The proposal commands share one request boundary with `--edit` and edit-specific options. They do not create a separate proposal protocol or persistence store.

### `shared`

Remains intentionally minimal and contains only generic utilities. It does not contain ontology, validation, diff, or proposal logic.

### `vscode-extension`

Provides the minimal ontology workbench. It detects the Entio project, displays sources and symbols, exposes an edit-kind selector and form modes, invokes the machine-readable CLI, renders preview/diff/validation/equivalence results, enables approval or rejection, refreshes after application, and opens a changed source.

The extension does not interpret RDF or Turtle and does not write ontology source files directly.

## Main Data Objects And Contracts

The shared contracts live in `core-types`.

### Project And Ontology

- `EntioProjectConfig` stores the project name and configured ontology sources.
- `OntologySourceReference` identifies a configured source by ID, relative path, and format.
- `ResolvedOntologySource` records a validated project-root-relative source path.
- `LoadedOntology` contains a resolved source and its graph.
- `EntioProject` aggregates configuration, resolved sources, loaded ontologies, symbols, and the combined graph.
- `OntologyFormat` currently supports Turtle only.

### RDF And Graph

- `RdfTerm` is the root RDF-term abstraction.
- `RdfResource`, `Iri`, and `BlankNodeResource` represent resource terms.
- `RdfLiteral` stores lexical form, optional datatype IRI, and optional language tag.
- `GraphTriple` stores RDF-term-aware subject, predicate, and object values while retaining compatibility views used by earlier consumers.
- `GraphState` stores graph triples.
- `GraphChange`, `ChangeSet`, and `ChangePreview` represent edits and in-memory preview state.

### Symbols And Validation

- `LoadedSymbol` stores an IRI, optional label, symbol kind, and source ID.
- `SymbolKind` models classes, properties, individuals, shapes, namespace terms, and unknown symbols.
- `ValidationIssue` stores severity, stable code, message, and optional source.
- `ValidationReport` stores a valid or invalid result and deterministically ordered issues.

### Proposals, Diffs, And Results

- `ChangeProposal` stores proposal identity, title, target source, change set, baseline, status, preview, diff, validation report, source impact, and optional review metadata.
- `ChangeProposalStatus` models draft, preview, verification, review, rejection, approval, application, stale, and rollback-related states.
- `ProposalBaseline` stores project, target-source, source-file, and graph fingerprints used to block stale proposals.
- `SourceFileImpact` identifies the ontology files affected by a proposal.
- `SemanticDiff` and `SemanticDiffEntry` describe added, removed, or changed graph facts.
- `SemanticEquivalenceResult` reports successful, failed, or non-equivalent Turtle round-trip verification.
- `ApplyProposalResult` and `RollbackResult` describe save and recovery outcomes.
- `ProposalReview` carries reviewer metadata, but it is an in-memory contract rather than a persistent review record.

### Typed Edit Contracts

`TypedOntologyEdit` has concrete contracts for class creation, object and datatype property creation, property domain and range changes, individual creation, type assignment, object and datatype assertions, direct superclass changes, and entity labels. The CLI and workbench reuse these contracts instead of constructing raw graph triples independently.

## CLI And Workbench Workflow

The Kotlin path is the source of truth for every supported edit. A typical preview begins at either `proposal-preview` or the VS Code form:

1. The caller supplies a project root, target source ID, edit kind, and edit-specific fields.
2. `ProposalCommandSupport` or the VS Code request boundary normalizes those fields.
3. The CLI constructs a typed edit and calls `TypedOntologyEditTranslator`.
4. The translator creates a `ChangeSet`; the CLI support layer composes optional changes such as labels or replacement removals.
5. `ProjectLoader` and `ProposalCreator` establish the current project and `ProposalBaseline`.
6. `GraphChangePreviewer` creates a preview without changing disk.
7. `ProposalDiffGenerator` and `ProposalValidator` produce the diff and validation report.
8. `PreviewTurtleRoundTripVerifier` checks that temporary Turtle serialization and reparsing preserve graph meaning.
9. The structured response is rendered by the CLI or normalized into the workbench preview model.
10. Apply rechecks freshness, writes only the target source, reloads it, and verifies equivalence. Reject returns without changing files.
11. The extension refreshes its project model after successful application and can open the changed ontology source.

The workflow is git-like by analogy only: draft, preview, diff, review, approve, and apply. Entio does not perform Git staging, commits, pushes, branch management, pull requests, or long-term version storage.

## Developer Commands

From the repository root:

```bash
./gradlew test
./gradlew build
./gradlew check
```

Focused Kotlin module tests can be run with:

```bash
./gradlew :core-types:test
./gradlew :semantic-engine:test
./gradlew :validation-engine:test
./gradlew :graph-diff:test
./gradlew :cli:test
```

Because Gradle runs `:cli:run` from the `cli` module directory, example paths use `../examples`:

```bash
./gradlew :cli:run --args="validate ../examples/simple-ontology"
./gradlew :cli:run --args="symbols ../examples/simple-ontology"
./gradlew :cli:run --args="diff ../examples/simple-ontology ../examples/simple-ontology"
./gradlew :cli:run --args="project-summary ../examples/simple-ontology"
./gradlew :cli:run --args="proposal-preview ../examples/simple-ontology simple --edit create-object-property --property-iri https://example.com/hasAccount --domain-iri https://example.com/Customer --range-iri https://example.com/Account"
```

The supported proposal edit kinds are:

```text
create-class
create-object-property
create-datatype-property
set-property-domain
set-property-range
create-individual
assign-individual-type
add-object-property-assertion
add-datatype-property-assertion
add-superclass
remove-superclass
set-entity-label
```

`--replace-existing` enables explicit replacement behavior for supported domain, range, and label changes.

For the VS Code boundary:

```bash
cd vscode-extension
npm install
npm run compile
npm test
```

`npm test` compiles the extension and runs the Node-based tests for request normalization, CLI argument construction, workbench models, form rendering, preview results, action results, refresh, and source-opening boundaries.

## Examples And Fixtures

The committed example project is:

```text
examples/simple-ontology/
  entio.yaml
  ontology/simple.ttl
  .vscode/settings.json
```

It is a small Turtle project used for local CLI use and as the base for copied-fixture regression tests. Tests create temporary copies or focused temporary Turtle files for properties, individuals, assertions, labels, and hierarchy relationships. They do not modify the committed example in place.

The extension can be pointed at the Kotlin CLI through the `entio.cliCommand` VS Code setting. The repository includes the extension source and tests, but does not include a packaged marketplace extension or a full Extension Development Host test suite.

## Phase 2.5 Non-Goals

Phase 2.5 intentionally does not include:

- New AI or LLM behavior, document ingestion, document-to-knowledge-graph conversion, Schema RAG, entity resolution, or autonomous agents.
- Full OWL class-expression, equivalent-class, disjoint-class, inverse-property, property-chain, cardinality, or property-characteristic editing.
- Full OWL reasoning, inference explanation, or full SHACL authoring and validation.
- Source-text-preserving Turtle editing.
- Long-term proposal persistence, project version history, or a multi-user review system.
- Actual Git staging, commits, pushes, branches, or pull requests inside Entio.
- Multi-user collaboration, authentication, authorization, a server, a database, or a separate desktop application.
- A full drag-and-drop ontology graph editor or full Protégé feature parity.
- New Gradle modules, a web application, or a new semantic-web framework.

The existing Phase 1 and Phase 2 non-goals remain in force.

## Known Limitations And Follow-Up Work

- Turtle is the only configured ontology format.
- Jena serialization preserves graph meaning but not original Turtle formatting, comments, or source layout.
- Semantic diffs are graph-triple based, with focused label-change formatting rather than a complete ontology-aware diff language.
- Validation uses explicit graph facts and deterministic compatibility checks. It does not provide OWL inference, full hierarchy reasoning, or full SHACL validation.
- Proposal state is reconstructed during a CLI or workbench interaction. There is no durable proposal history, review queue, authentication, authorization, or server-backed review store.
- The CLI apply command marks the proposal approved within the same invocation. It does not represent a persisted human approval performed in a separate session.
- The VS Code form modes accept entity IRIs and values as text inputs. They do not yet provide ontology-backed symbol selectors that constrain choices to discovered classes, properties, or individuals.
- VS Code tests cover TypeScript request, rendering, process-boundary, and result models. They do not launch a full Extension Development Host or provide browser-style UI integration tests.
- Property, domain, range, and label replacement currently operate on one explicit value at a time.
- Blank-node identifiers are parser-local and should not be treated as durable business identities.
- Multiple ontology sources are supported by the project model, but the workbench remains a small source-targeted editing surface.

## Plan And Implementation Mismatches

The Phase 2.5 specification and ExecPlan were broader than some delivered details:

1. The plan calls for selecting existing classes, properties, and individuals. The current workbench uses text-based IRI fields rather than ontology-backed selectors.
2. The plan calls for complete VS Code apply, reject, refresh, and open-source regression coverage. The extension implements those boundaries, but the tests are TypeScript boundary/rendering tests; copied-fixture end-to-end coverage is concentrated in Kotlin and CLI tests rather than a launched Extension Development Host.
3. The plan describes a human-reviewable proposal lifecycle. The proposal and review contracts exist, but state is in memory and there is no durable multi-user review workflow.
4. The plan allows richer label and replacement behavior than the current one-value-at-a-time forms expose.
5. `AGENTS.md` and `README.md` currently describe Phase 2 as the latest completed phase and do not yet make Phase 2.5 the active status. This summary reflects the implemented Phase 2.5 code and tests without changing those repository-status documents.

These limitations are follow-up work, not evidence of a second semantic pathway. The Kotlin semantic engine remains the single implementation boundary for graph changes, validation, diffing, serialization, and source persistence.
