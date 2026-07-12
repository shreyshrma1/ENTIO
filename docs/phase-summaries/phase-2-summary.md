# Phase 2 Implementation Summary

## Overview

Phase 2 implemented the Controlled Ontology Editing Workbench for small local Entio projects. It adds a controlled, reviewable mutation pathway on top of the Phase 1 and Phase 1.5 Kotlin/JVM semantic engine.

The implemented workflow is proposal-oriented rather than source-text-oriented:

1. Load a local Entio project and its Turtle graph.
2. Translate a supported typed ontology edit into explicit graph changes.
3. Build an in-memory preview graph without changing source files.
4. Generate a semantic diff and deterministic validation report.
5. Verify that temporary Turtle serialization and reparsing are semantically equivalent.
6. Review, reject, or approve the proposal.
7. Apply an approved and current proposal atomically to its target Turtle source.
8. Reload the project and verify the saved graph.
9. Restore the prior source when post-save verification fails.

Phase 2 also adds a minimal VS Code extension that delegates semantic behavior to the Kotlin engine through machine-readable CLI responses.

## Repository Structure

```text
.
├── core-types/
├── semantic-engine/
├── validation-engine/
├── graph-diff/
├── cli/
├── shared/
├── vscode-extension/
├── examples/simple-ontology/
├── docs/
│   ├── architecture/
│   ├── decisions/
│   ├── execplans/
│   ├── phase-summaries/
│   └── specs/
├── skills/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
└── gradlew.bat
```

The six Kotlin directories are Gradle modules. `vscode-extension` is a separate TypeScript VS Code extension package and is not a Gradle module.

## Module Responsibilities

### `core-types`

Defines Entio-specific contracts without implementing RDF parsing or ontology mutation. Its models include project configuration, ontology source references, resolved sources, loaded ontologies and symbols, RDF terms and triples, graph state, graph changes, change sets, previews, proposals, baselines, validation reports, semantic diffs, apply results, and rollback results.

### `semantic-engine`

Owns project loading and semantic behavior. It loads `entio.yaml`, resolves local ontology sources, parses Turtle with Apache Jena, extracts symbols, previews graph changes, translates typed edits, creates proposal baselines, verifies Turtle round trips, and applies approved proposals with source replacement and rollback.

### `validation-engine`

Runs deterministic project and proposal checks. It validates project structure, target sources, preview graphs, current proposal baselines, and semantic-equivalence results. Validation issues are sorted deterministically before reports are returned.

### `graph-diff`

Compares graph states and produces semantic diffs. It reports added and removed triples and recognizes a changed `rdfs:label` as a changed semantic entry. It also attaches proposal diffs and formats them for human review.

### `cli`

Provides a thin picocli command-line boundary. It loads reusable Kotlin services, prepares typed proposals, emits text or machine-readable JSON, and delegates parsing, validation, diffing, serialization, and application to the engine modules.

### `shared`

Remains intentionally minimal and contains only generic shared utilities. It does not contain ontology or proposal product logic.

### `vscode-extension`

Provides the minimal VS Code workbench. It detects an Entio project, invokes the CLI, renders project sources and symbols, submits the supported edit form, displays preview/diff/validation results, sends approve or reject actions, refreshes after application or Turtle file changes, and opens a changed source file in VS Code.

The extension does not parse Turtle, construct RDF independently, write ontology files, or perform Git operations.

## Main Contracts

### Project and RDF state

- `EntioProjectConfig` and `OntologySourceReference` describe the local project configuration.
- `ResolvedOntologySource` points to a resolved source file.
- `EntioProject` aggregates configuration, sources, loaded ontologies, symbols, and the combined `GraphState`.
- `RdfResource` distinguishes `Iri` and `BlankNodeResource`.
- `RdfLiteral` preserves lexical form, optional datatype IRI, and optional language tag.
- `GraphTriple` and `GraphState` represent RDF-aware graph data while retaining Phase 1 compatibility views.

### Editing and proposal state

- `GraphChange` represents one addition or removal of a triple.
- `ChangeSet` groups changes and rejects an empty set.
- `ChangePreview` contains the proposed graph and its change set.
- `TypedOntologyEdit` is a sealed contract for supported operations such as creating classes, adding superclass relationships, creating properties or individuals, assigning types, and adding assertions.
- `ChangeProposal` carries the change set, target source, baseline, preview, diff, validation report, status, and source-file impact.
- `ProposalBaseline` records project, target-source, and graph fingerprints for stale-proposal detection.
- `ApplyProposalResult` and `RollbackResult` report application, restoration, and failure outcomes.

### Validation and diff state

- `ValidationReport` contains a valid or invalid status and ordered `ValidationIssue` values.
- `SemanticDiff` contains ordered `SemanticDiffEntry` values classified as added, removed, or changed.
- Machine-readable CLI payloads expose proposal validation, semantic equivalence, diff entries, changed files, and rollback status to the extension.

## End-To-End Workflow

`ProjectLoader` reads the project configuration, resolves the configured Turtle source, parses it through Apache Jena, extracts symbols, and assembles the project graph.

`TypedOntologyEditTranslator` converts a typed edit into a `ChangeSet`. `GraphChangePreviewer` applies that set to an in-memory copy of the graph and rejects duplicate additions or missing removals. `ProposalCreator` captures the baseline and creates a `ChangeProposal` without changing the source.

`ProposalDiffGenerator` compares the current and preview graphs. `ProposalValidator` combines project checks, preview checks, baseline checks, and semantic-equivalence checks. `PreviewTurtleRoundTripVerifier` serializes the preview graph with Jena, reparses it, and compares the graphs semantically.

On approval, `ProposalApplier` rechecks the baseline, verifies the preview, serializes to a temporary Turtle file, atomically replaces only the target source, reloads the project, and compares the saved graph with the approved preview. A failed post-save verification restores the original source and reports whether restoration succeeded. A stale proposal is rejected before writing.

Rejection does not call the applier and leaves source files unchanged. The workbench refreshes its project summary after a successful application and listens for Turtle file changes. It can open a changed source using VS Code document APIs.

## CLI Commands

The CLI exposes the original commands:

```bash
./gradlew :cli:run --args="validate ../examples/simple-ontology"
./gradlew :cli:run --args="symbols ../examples/simple-ontology"
./gradlew :cli:run --args="diff ../examples/simple-ontology ../examples/simple-ontology"
```

It also exposes the Phase 2 machine-readable commands:

```bash
./gradlew :cli:run --args="project-summary ../examples/simple-ontology"
./gradlew :cli:run --args="proposal-preview ../examples/simple-ontology simple --edit create-class --class-iri https://example.com/Invoice --label Invoice"
./gradlew :cli:run --args="proposal-validate ../examples/simple-ontology simple --edit create-class --class-iri https://example.com/Invoice --label Invoice"
./gradlew :cli:run --args="proposal-diff ../examples/simple-ontology simple --edit create-class --class-iri https://example.com/Invoice --label Invoice"
./gradlew :cli:run --args="proposal-apply ../examples/simple-ontology simple --edit create-class --class-iri https://example.com/Invoice --label Invoice"
./gradlew :cli:run --args="proposal-reject ../examples/simple-ontology simple --edit create-class --class-iri https://example.com/Invoice --label Invoice"
```

Gradle runs `:cli:run` from the `cli` module directory, so repository-relative example paths use `../examples/...`. The proposal commands currently prepare a proposal within the command invocation; proposal metadata is not persisted between separate CLI processes.

## Developer Verification

Run the Kotlin/JVM verification suite with:

```bash
./gradlew test
./gradlew build
./gradlew check
```

Focused checks are available for each Gradle module, for example:

```bash
./gradlew :core-types:test
./gradlew :semantic-engine:test
./gradlew :validation-engine:test
./gradlew :graph-diff:test
./gradlew :cli:test
```

The extension package defines:

```bash
cd vscode-extension
npm install
npm test
```

The extension test command compiles TypeScript and runs the compiled Node test files. It now passes with 12 tests.

## Examples And Fixtures

`examples/simple-ontology` contains the small committed fixture used by CLI and end-to-end tests:

```text
examples/simple-ontology/
├── entio.yaml
└── ontology/
    └── simple.ttl
```

The fixture declares `Customer`, `Account`, and `ownsAccount`. Phase 2 end-to-end tests copy this directory into a temporary location before preview, rejection, application, stale-proposal, or rollback scenarios. The committed example is not modified in place by those tests.

## Explicit Non-Goals

Phase 2 does not include:

- A web app, server, database, or production graph store.
- Document ingestion, document-to-knowledge-graph conversion, Schema RAG, entity resolution, or autonomous AI agents.
- LLM-generated ontology edits.
- Full Protégé parity, full OWL class-expression editing, OWL reasoning, inference explanation, or full SHACL authoring/validation.
- Multi-user collaboration, authentication, authorization, or long-term project version history.
- Git staging, commits, pushes, branches, pull requests, or any other Git workflow inside Entio.
- Source-text-preserving Turtle round trips or a full drag-and-drop graph editor.
- A separate desktop application.

The product workflow is Git-like only by analogy: draft, preview, diff, review, approve, and apply.

## Known Limitations And Follow-Up Work

- Turtle is the only ontology source format currently implemented.
- The core typed-edit contract and translator cover multiple edit kinds, but the current machine-readable CLI and VS Code form expose only `create-class`.
- Proposal state is in memory and reconstructed by the current CLI boundary. There is no durable proposal store or project version history.
- Applying a proposal serializes the target graph through Jena; source formatting and comments are not preserved.
- Semantic diffs are graph-based, with a focused special case for `rdfs:label` changes rather than a complete ontology-aware change taxonomy.
- The workbench is intentionally minimal. It shows project data and selected symbol details and supports a focused edit/preview/apply flow, not a full ontology editor.
- No later planning phase is active in the repository yet; future work should be specified before implementation begins.
