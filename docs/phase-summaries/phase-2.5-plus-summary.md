# Entio Phase 2.5+ Summary

## What Phase 2.5+ Implemented

Phase 2.5+ extends the Phase 2 controlled ontology-editing workflow with more usable entity selection, deterministic identifier generation, safe deletion review, multi-edit staging, combined proposal review, and machine-readable CLI support. The final implementation also carries explicit deletion dependency identities through the Kotlin/CLI boundary and exposes deletion selection and preview controls in the VS Code workbench.

The implemented workflow is:

1. Load an Entio project and its ontology graph through the Kotlin semantic engine.
2. Resolve existing entities by label, optional kind, source, or explicit IRI.
3. Generate deterministic IRIs for new classes, properties, and individuals when the project provides an IRI namespace.
4. Inspect explicit graph dependencies before deletion and block unresolved dependent references.
5. Preview valid individual edits and hold them in an in-memory VS Code staged list.
6. Edit, re-preview, cancel, or remove staged entries without changing source files.
7. Submit the ordered staged edits through the structured `proposal-combined` CLI boundary.
8. Produce one combined preview graph, semantic diff, validation report, Turtle round-trip result, baseline, and affected-source list.
9. Reject without writing, or approve the complete current set for Kotlin-owned atomic application.
10. Reload the workbench after successful application and expose changed-source opening and rollback results.

For deletion, direct definition statements are always included, dependent statements receive stable keys, unresolved references block preview, and the user must explicitly select each dependent statement before deletion can enter the staged list.

The Kotlin engine remains the source of truth for RDF terms, graph changes, validation, semantic diffing, Turtle serialization, stale checks, source persistence, and rollback. The VS Code extension owns form state, staged session state, CLI invocation, and presentation only.

## Repository Structure

```text
core-types/          Shared Entio contracts and result objects
semantic-engine/     Project loading, parsing, translation, preview, and application
validation-engine/   Deterministic project and proposal validation
graph-diff/          Graph comparison and combined preview orchestration
cli/                 Thin human- and machine-readable Kotlin CLI boundary
shared/              Minimal generic utilities
vscode-extension/    TypeScript VS Code workbench boundary
examples/            Committed example projects and ontology fixtures
docs/                Architecture, specs, ExecPlans, decisions, and summaries
```

## Module Responsibilities

### `core-types`

Defines Entio-specific contracts, including project configuration, ontology sources, RDF term and graph models, loaded symbols, typed ontology edits, graph changes, staged entries, conflict metadata, validation reports, semantic diffs, proposal baselines, apply results, rollback results, and combined proposal metadata.

It does not implement RDF parsing, OWL reasoning, SHACL processing, or source-file persistence.

### `semantic-engine`

Loads project configuration and ontology sources, parses Turtle through Apache Jena, preserves RDF term fidelity, extracts symbols and relationships, translates typed edits into graph changes, normalizes staged changes, verifies Turtle round trips, and applies approved proposals atomically with reload and rollback.

### `validation-engine`

Runs deterministic project, proposal, edit-specific, deletion, baseline, and semantic-equivalence checks. It returns structured validation reports and does not use AI judgment or full OWL/SHACL reasoning.

### `graph-diff`

Produces deterministic semantic graph diffs and coordinates combined in-memory previews using normalized staged changes, validation, diffing, and round-trip verification.

### `cli`

Provides the original `validate`, `symbols`, `diff`, project-summary, and single-edit proposal commands. It also provides structured label resolution, IRI generation, deletion dependency inspection, `proposal-request`, and `proposal-combined` lifecycle operations.

### `shared`

Remains intentionally small and contains only generic utilities. Product semantics are kept in the owning modules.

### `vscode-extension`

Provides the workbench UI boundary. It renders project and symbol details, label-first selectors, generated-IRI results, deletion dependency review, explicit dependent-statement checkboxes, a Delete action for supported symbols, in-memory staged edits, combined preview, approval, rejection, refresh, and changed-source actions. It does not construct RDF, validate graphs, generate final semantic identifiers, or write Turtle directly.

## Main Contracts

Important shared contracts include:

- `EntioProject`, `EntioProjectConfig`, `OntologySourceReference`, and resolved source objects.
- `Iri`, `BlankNodeResource`, `RdfLiteral`, `RdfTerm`, `GraphTriple`, and `GraphState`.
- `LoadedSymbol`, `SymbolDetails`, `SymbolRelationship`, and `SymbolKind`.
- Typed edit objects such as `CreateClassEdit`, `CreateObjectPropertyEdit`, `CreateIndividualEdit`, and assertion or relationship edits.
- `GraphChange`, `ChangeSet`, `StagedChange`, `StagedChangeSet`, normalized staged sets, and conflict results.
- `ValidationReport`, `ValidationIssue`, `ValidationSeverity`, and validation status.
- `SemanticDiff` and diff entries.
- `ChangeProposal`, `ProposalBaseline`, `CombinedProposalPreview`, apply results, and rollback results.
- `EntitySelector`, entity resolution results, generated IRI results, deletion plans, and dependency statements.

## Developer Commands

From the repository root:

```bash
./gradlew test
./gradlew build
./gradlew check
```

For the VS Code extension:

```bash
cd vscode-extension
npm install
npm test
```

The Kotlin CLI can be run through Gradle. Examples include:

```bash
./gradlew :cli:run --args="validate ../examples/simple-ontology"
./gradlew :cli:run --args="symbols ../examples/simple-ontology"
./gradlew :cli:run --args="diff before-project after-project"
```

When invoking `:cli:run`, Gradle runs the CLI with the `cli` module as its working directory, so the example project is addressed as `../examples/simple-ontology`:

```bash
./gradlew :cli:run --args="validate ../examples/simple-ontology"
./gradlew :cli:run --args="symbols ../examples/simple-ontology"
./gradlew :cli:run --args="project-summary ../examples/simple-ontology"
```

The structured proposal commands accept a temporary JSON request file. The combined command supports `preview`, `validate`, `diff`, `apply`, and `reject` actions.

## Examples And Fixtures

`examples/simple-ontology/` is the committed example project with `entio.yaml` and `ontology/simple.ttl`. Its configuration includes the project IRI namespace, and its ontology includes classes, object properties, individuals, labels, and the explicit `Shrey` to `Invoice 20874` object-property relationship used by deletion regressions.

Regression tests copy this example into temporary directories before modifying it. Other tests create temporary Turtle projects for ambiguous labels, namespace configuration, collisions, staged conflicts, deletion references, stale baselines, and rollback injection. The committed example is not modified by the regression suite.

## Explicit Non-Goals

Phase 2.5+ does not include:

- Durable staged-session or proposal persistence.
- Entio-managed Git staging, commits, branches, pushes, or pull requests.
- A web app, document ingestion, autonomous agents, Schema RAG, entity resolution beyond deterministic label matching, or production graph storage.
- A custom RDF, OWL, SHACL, or Turtle framework.
- Full OWL reasoning, full SHACL authoring, inferred deletion dependencies, or full Protégé parity.
- Direct RDF construction, Turtle writes, or semantic validation in TypeScript.

## Known Limitations And Follow-Up Work

- The VS Code `EntioEngineClient` currently rejects nonzero CLI exit codes before parsing stdout. As a result, structured ambiguous, missing, collision, and deletion-blocker payloads are modeled and tested at the adapter boundary, but the running workbench may present those nonzero responses as invocation errors. The client boundary should be improved in a later focused change if the UI must render those payloads directly.
- The VS Code deletion surface now exposes supported-symbol Delete actions, explicit dependent-statement selection, and deletion preview through the existing staged/combined lifecycle. A full launched Extension Development Host test with a live Kotlin CLI remains outside the automated regression suite.
- Combined VS Code requests use process-scoped temporary JSON files because the current CLI boundary accepts request files. These files are cleaned up and are not a durable proposal store.
- Combined application currently requires one target ontology source. Multi-source atomic application remains outside the approved scope.
- The extension regression suite verifies model, request, and rendered webview behavior. A manually launched Extension Development Host and a live installed CLI are not part of the automated test run.
