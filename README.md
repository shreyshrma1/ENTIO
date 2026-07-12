# Entio

Entio is a product for building clean, trustworthy knowledge graphs from enterprise information.

Entio's core principle is that AI should not freely invent a graph from documents or data. Entio gives AI a rulebook first: an approved ontology that defines the business concepts, relationships, and constraints that graph changes must follow. AI can help propose additions or edits, but those changes are treated as drafts until they pass deterministic validation and a human reviewer approves them.

## Long-Term Vision

Entio should eventually help teams:

- Build, validate, version, and maintain enterprise knowledge graphs.
- Reuse trusted existing ontologies such as FIBO, Schema.org, Wikidata, and internal enterprise ontologies where appropriate.
- Treat AI-generated graph and ontology changes as drafts, not final truth.
- Validate proposed changes with deterministic checks before publishing.
- Show semantic diffs so humans can review what changed.
- Support document-to-knowledge-graph construction.
- Support multiple interfaces, including VS Code, a web app, CLI, and APIs.

## Current Repository Status

This repository now contains the completed Phase 1 Kotlin/JVM Core Semantic Engine foundation, the completed Phase 1.5 Core Semantic Engine Stabilization work, and the completed Phase 2 Controlled Ontology Editing Workbench foundation.

Phase 1 is the first backend foundation for Entio. It uses Kotlin/JVM because the core work is ontology loading, RDF/Turtle parsing, deterministic validation, semantic diffing, and CLI behavior.

The current implementation supports small local Entio projects, Turtle/RDF parsing through Apache Jena, RDF-term-aware graph triples, deterministic validation reports, semantic graph diffs, reusable project loading, and a thin CLI.

Phase 2 is complete. It adds safe ontology mutation, preview and approval workflows, source-file persistence, and a minimal VS Code workbench while keeping the Kotlin semantic engine responsible for RDF and ontology behavior. No later planning phase is active yet.

## Workspace Structure

The repository contains the Phase 1 and Phase 2 Kotlin/Gradle workspace plus a separate TypeScript VS Code extension:

- `core-types`
- `semantic-engine`
- `validation-engine`
- `graph-diff`
- `cli`
- `shared`
- `vscode-extension`

The VS Code extension consumes the Kotlin/JVM core engine through the machine-readable CLI boundary rather than duplicating semantic logic.

## Current Capabilities

The Phase 1, Phase 1.5, and Phase 2 implementation currently supports:

- Loading an Entio project configuration.
- Loading a project through a reusable `ProjectLoader`.
- Returning a populated `EntioProject` aggregate.
- Resolving local ontology source files.
- Parsing small Turtle/RDF ontology files using existing libraries.
- Representing Entio-specific project objects, RDF terms, symbols, validation results, and graph diffs.
- Running basic validation checks.
- Generating semantic diffs.
- Exposing these capabilities through a simple CLI and machine-readable proposal commands.
- Translating typed ontology edits into graph changes and preview graphs.
- Creating proposal baselines and detecting stale proposals.
- Validating proposals and verifying Turtle serialization/reparsing equivalence.
- Applying approved proposals atomically and restoring the prior source after post-save verification failure.
- Browsing projects and symbols in a minimal VS Code ontology workbench.

Implemented CLI commands:

```bash
./gradlew :cli:run --args="validate ../examples/simple-ontology"
./gradlew :cli:run --args="symbols ../examples/simple-ontology"
./gradlew :cli:run --args="diff ../examples/simple-ontology ../examples/simple-ontology"
./gradlew :cli:run --args="project-summary ../examples/simple-ontology"
./gradlew :cli:run --args="proposal-preview ../examples/simple-ontology simple --edit create-class --class-iri https://example.com/Invoice --label Invoice"
./gradlew :cli:run --args="proposal-validate ../examples/simple-ontology simple --edit create-class --class-iri https://example.com/Invoice --label Invoice"
./gradlew :cli:run --args="proposal-diff ../examples/simple-ontology simple --edit create-class --class-iri https://example.com/Invoice --label Invoice"
./gradlew :cli:run --args="proposal-apply ../examples/simple-ontology simple --edit create-class --class-iri https://example.com/Invoice --label Invoice"
./gradlew :cli:run --args="proposal-reject ../examples/simple-ontology simple --edit create-class --class-iri https://example.com/Invoice --label Invoice"
```

Note: Gradle runs `:cli:run` from the `cli` module working directory, so the example path uses `../examples/simple-ontology`.

## Developer Commands

Run tests:

```bash
./gradlew test
```

Build all modules:

```bash
./gradlew build
```

Run checks:

```bash
./gradlew check
```

## Current Limitations

- Only Turtle is supported.
- Semantic diffs are graph-triple based, with a small special case for `rdfs:label` changes.
- The current CLI and VS Code edit form expose `create-class`; broader typed edit contracts are available in the Kotlin core but do not yet have CLI or UI forms.
- Entio does not store project versions itself; `diff` compares two project directories supplied by the caller.
- Proposal state is reconstructed within the current CLI invocation and is not persisted as long-term project history.
- Jena serialization does not preserve original Turtle source formatting or comments.

## Implemented Phase 2 Workflow

Phase 2 provides:

- Add controlled graph changes and atomic change sets.
- Translate supported typed ontology edits into graph changes in the Kotlin engine.
- Generate preview graphs without changing source files.
- Generate semantic diffs and validation reports before approval.
- Apply only approved and current proposals to the correct Turtle source.
- Restore the previous source and graph state when save or verification fails.
- A minimal VS Code ontology workbench.
- A git-like semantic workflow by analogy only: draft, preview, diff, review, approve, and apply.

## Explicit Non-Goals For Phase 2

Phase 2 should not include:

- Web app.
- Document ingestion.
- Autonomous AI agents.
- LLM-generated ontology edits.
- Schema RAG.
- Entity resolution.
- Full domain ontology indexing.
- Production graph storage.
- Full Protégé feature parity.
- Full OWL class-expression editing.
- Full SHACL authoring or validation environment.
- A custom RDF, OWL, or SHACL framework.
- Long-term project version history.
- Git staging, commits, pushes, branch management, or pull-request creation inside Entio.
- OWL reasoning or full SHACL validation.

## Technical Principle

Entio should not reinvent RDF, OWL, or SHACL.

The project should use existing libraries for RDF parsing, graph representation, ontology handling, and validation where possible. Entio should define only the product-specific types and workflows that make the system useful, such as project configuration, validation reports, semantic diffs, and human-reviewable change proposals.

## Architecture Notes

- [Product Principles](docs/architecture/000-product-principles.md)
- [Phase 1 Scope](docs/architecture/phase-1-scope.md)
- [Phase 1.5 Scope](docs/architecture/phase-1.5-scope.md)
- [Phase 2 Scope](docs/architecture/phase-2-scope.md)
- [Technical Approach](docs/architecture/002-technical-approach.md)
- [Kotlin Engine Guidelines](docs/architecture/003-kotlin-engine-guidelines.md)
- [Phase 1.5 Spec](docs/specs/0002-phase-1.5-core-semantic-engine-stabilization.md)
- [Phase 2 Spec](docs/specs/0003-phase-2-controlled-ontology-editing-workbench.md)
- [Phase 1 Implementation Summary](docs/phase-summaries/phase-1-summary.md)
- [Phase 1.5 Implementation Summary](docs/phase-summaries/phase-1.5-summary.md)
- [Phase 2 Implementation Summary](docs/phase-summaries/phase-2-summary.md)
