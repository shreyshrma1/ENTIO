# Phase 4 Implementation Summary

## Status

Phase 4, OWL Reasoning and SHACL Constraints, is implemented through the thirteen approved ExecPlan slices. The implementation extends the existing Kotlin/JVM semantic engine, CLI, graph-diff, validation, and VS Code workbench boundaries. It does not replace the Phase 1 through Phase 3 workflow or introduce a separate semantic implementation in TypeScript.

## What Phase 4 Implemented

Phase 4 added a bounded reasoning and SHACL workflow for local Entio projects:

- OWL reasoning through the isolated OWL API and HermiT integration.
- Explicit separation of asserted facts and inferred facts.
- Reasoning status, consistency status, import-closure completeness, fingerprints, unsupported-feature findings, and unsatisfiable classes.
- Local import closure discovery with findings for missing, cyclic, and incomplete imports.
- Reasoning lifecycle metadata, deterministic reuse keys, timeout/cancellation boundaries, and safe incomplete or failed results.
- Explanations for selected reasoning results where the supported adapter can provide them.
- SHACL graph roles, typed shape descriptors, supported targets, direct paths, constraints, severities, and messages.
- Jena-backed SHACL validation normalized into Entio-owned validation contracts.
- Explicit `asserted-only` and `asserted-and-inferred` validation modes.
- Proposal impact that separates explicit graph changes, reasoning impact, and SHACL impact, including baseline-aware blocking.
- Atomic multi-source proposal application with reload verification and complete rollback on failure.
- Machine-readable CLI commands for reasoning, SHACL validation and shape inspection, and proposal impact.
- VS Code workbench views for the Phase 4 reasoning, SHACL, and proposal-impact results.
- Copied-fixture end-to-end regression coverage that verifies preview and rejection do not mutate sources and that an approved change can be applied and reloaded.

The implementation remains deliberately bounded. It supports the approved Phase 4 subset and reports unsupported or incomplete behavior instead of presenting it as complete OWL or SHACL coverage.

## Repository Structure

The repository is a Kotlin/JVM Gradle multi-module workspace with a separate TypeScript VS Code client:

```text
core-types/          Shared Entio contracts and data objects
semantic-engine/     Project loading, RDF/OWL/SHACL adapters, reasoning, and source application
validation-engine/   Deterministic project, proposal, deletion, and metadata validation
graph-diff/          Graph diffs, proposal previews, and proposal impact analysis
cli/                 Thin command-line and JSON boundary
shared/              Minimal generic utilities
vscode-extension/   VS Code presentation and CLI delegation layer
examples/            Small local Entio fixtures
docs/                Architecture, specifications, ExecPlans, decisions, and summaries
```

The Gradle modules target JVM 21. The VS Code extension is compiled and tested independently with TypeScript and Node.js.

## Module Responsibilities

### `core-types`

Defines the product-owned contracts used across the engine:

- RDF terms, graph triples, graph state, projects, loaded ontologies, and source references.
- `ReasoningResult`, `ReasoningRunMetadata`, `ReasoningFingerprints`, `FactOrigin`, and consistency/status types.
- Import closure reports and findings.
- OWL feature support and explanation contracts.
- SHACL graph identities, targets, paths, constraints, shapes, severities, validation results, and validation reports.
- Proposal impact, reasoning impact, SHACL impact, multi-source apply, and rollback result contracts.

Third-party OWL API, HermiT, and Jena implementation types do not form the public Phase 4 contracts.

### `semantic-engine`

Owns semantic operations and library boundaries:

- Loads Entio projects and resolves local ontology sources.
- Builds local import closure reports without network fallback.
- Parses RDF/Turtle through Apache Jena.
- Adapts supported ontology graphs to OWL API and detects supported or partial OWL features.
- Runs the isolated reasoning worker boundary and returns asserted/inferred results.
- Provides reasoning lifecycle, fingerprints, safe-failure, timeout, cancellation, and explanation services.
- Resolves SHACL graph roles, authors supported typed shape descriptions, and validates through Jena SHACL.
- Applies approved source changes across multiple sources with reload and rollback checks.

### `validation-engine`

Runs deterministic validation for project configuration, namespaces, metadata, proposals, deletions, severity, and incomplete or blocking proposal conditions. It consumes Entio contracts rather than implementing RDF or OWL parsing.

### `graph-diff`

Compares asserted graph states and formats semantic diffs. Phase 4 adds proposal impact aggregation, baseline-aware reasoning impact, SHACL result impact, and combined proposal preview without mutating source files.

### `cli`

Provides the thin machine-readable boundary. It parses command arguments, delegates to Kotlin services, and serializes Entio-owned JSON responses. It does not perform reasoning, SHACL validation, graph comparison, or source writing itself.

### `vscode-extension`

Provides the user-facing workbench. It invokes the Kotlin CLI, normalizes responses into TypeScript view models, and renders project, symbol, reasoning, SHACL, and proposal-impact information. It does not parse RDF, run OWL reasoning, validate SHACL, or write Turtle directly.

### `shared`

Remains intentionally minimal. It does not contain semantic policy or Phase 4 product logic.

## Main Contracts And Workflow

The Phase 4 workflow is:

1. `ProjectLoader` loads the project configuration, resolves local sources, parses graphs, assigns graph roles, and reports local import closure status.
2. The reasoning service receives the loaded asserted graph and closure metadata. It runs the supported OWL profile through the isolated library boundary and returns deterministic asserted and inferred relationships, consistency, unsupported-feature findings, fingerprints, and status.
3. The SHACL service receives data and shapes graphs selected by configured roles. It runs the supported Jena SHACL subset and returns normalized `ShaclValidationReport` results. The default CLI/workbench path is asserted-only; asserted-plus-inferred validation must be requested explicitly and requires a complete inferred graph.
4. A typed ontology edit is translated into a graph change and a preview graph. `ProposalImpactAnalyzer` compares the current and preview states, then reports explicit graph diff, reasoning impact, SHACL impact, and blocking messages against the proposal baseline.
5. Preview and rejection leave source files unchanged. Approval passes through the existing proposal validation boundary and `MultiSourceAtomicApplier`, which writes temporary Turtle, reparses and verifies it, replaces the participating sources, reloads the project, and restores all original sources if verification fails.
6. The CLI exposes these results as deterministic JSON. The VS Code workbench refreshes the same CLI state and displays it without duplicating semantic behavior.

Important Phase 4 objects include `ReasoningResult`, `ReasoningExplanation`, `ImportClosureReport`, `OwlFeatureReport`, `ShaclNodeShape`, `ShaclPropertyShape`, `ShaclValidationReport`, `ProposalImpactReport`, and `MultiSourceApplyResult`.

## CLI Commands

The existing Phase 1 through Phase 3 commands remain available. Phase 4 adds:

```bash
./gradlew :cli:run --args="reasoning-refresh ../examples/simple-ontology"
./gradlew :cli:run --args="reasoning-explain ../examples/simple-ontology --target-iri https://example.com/entio/simple#Shrey"
./gradlew :cli:run --args="shacl-validate ../examples/simple-ontology --mode asserted-only"
./gradlew :cli:run --args="shacl-validate ../examples/simple-ontology --mode asserted-and-inferred"
./gradlew :cli:run --args="shacl-shapes ../examples/simple-ontology"
./gradlew :cli:run --args="proposal-impact ../examples/simple-ontology --request-file proposal.json"
```

The exact explanation selector options depend on the command contract and are reported through the CLI help output. `reasoning-refresh` also has the `reasoning` alias. Phase 4 command failures are returned through the machine-readable output boundary rather than being presented as unstructured semantic-engine exceptions.

## Verification Commands

Run the Kotlin verification suite from the repository root:

```bash
./gradlew test
./gradlew build
./gradlew check
```

Run the VS Code extension checks separately:

```bash
cd vscode-extension
npm test
```

The Phase 4 regression test copies `examples/simple-ontology` into a temporary directory before adding its test-only SHACL and reasoning fixtures. It verifies deterministic CLI output, asserted/inferred result presence, SHACL findings and descriptors, proposal-impact sections, source preservation during preview and rejection, and reload after apply.

## Examples And Fixtures

The committed example remains:

```text
examples/simple-ontology/
  entio.yaml
  ontology/simple.ttl
```

It is a small Turtle fixture containing classes, individuals, labels, and a property. Phase 4 tests create temporary copies and add local shape, reasoning, import, failure, and multi-source fixtures as needed. Committed examples are not mutated by the test suite.

## Explicit Non-Goals

Phase 4 does not include:

- A custom OWL, RDF, Turtle, or SHACL framework.
- Complete OWL 2 DL reasoning coverage or automatic materialization of inferred triples.
- External ontology downloads, internet import fallback, Schema RAG, embeddings, or document ingestion.
- Autonomous agents, entity resolution, or production graph storage.
- Durable proposal/version history, Git operations, or Git-like repository state inside Entio.
- Full Protégé feature parity, arbitrary OWL class-expression editing, or a full SHACL authoring environment.
- A separate web application or server layer.
- TypeScript semantic logic that bypasses the Kotlin engine.

## Deviations And Known Limitations

The implementation differs from the broadest wording in the Phase 4 ExecPlan in several deliberate ways:

- The plan calls for broad copied-fixture coverage across imports, cycles, blank nodes, RDF lists, worker failures, rollback, and UI boundaries. The repository has focused unit and integration coverage for these areas, plus the new copied-example regression, but not every scenario is covered by one end-to-end test or a full Extension Development Host test.
- The CLI exposes reasoning refresh/explanation, SHACL validation/shape inspection, and proposal impact. It does not expose every internal lifecycle operation, such as a standalone cancellation or shape-mutation command.
- The VS Code workbench displays asserted-only SHACL validation by default. It does not yet provide a separate asserted-plus-inferred mode control, shape authoring UI, or a full reasoning explanation inspector.
- Shape descriptors are currently an inspection boundary. Shape mutation remains subject to a later approved typed-edit workflow.
- Reasoning and SHACL support is intentionally profile- and construct-bounded. Unsupported OWL features, incomplete imports, unavailable inferred graphs, and unsupported SHACL paths are reported as limitations or safe failures.
- Proposal state remains process/session scoped. Phase 4 does not add durable version storage, project history, or Git integration.

These limitations are recorded so later work can extend the approved contracts without overstating the current engine’s guarantees.
