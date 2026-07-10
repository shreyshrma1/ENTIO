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

This repository now contains the Phase 1 Kotlin/JVM Core Semantic Engine foundation.

Phase 1 is the first backend foundation for Entio. It uses Kotlin/JVM because the core work is ontology loading, RDF/Turtle parsing, deterministic validation, semantic diffing, and CLI behavior.

The current implementation supports small local Entio projects, Turtle/RDF parsing through Apache Jena, deterministic validation reports, semantic graph diffs, and a thin CLI.

## Workspace Structure

The Phase 1 Gradle workspace contains:

- `core-types`
- `semantic-engine`
- `validation-engine`
- `graph-diff`
- `cli`
- `shared`

TypeScript may still be used later for VS Code or web interfaces, but those interfaces are not part of Phase 1. Future TypeScript layers should consume the Kotlin/JVM core engine rather than duplicate semantic logic.

## Phase 1 Capabilities

Phase 1 currently supports:

- Loading an Entio project configuration.
- Resolving local ontology source files.
- Parsing small Turtle/RDF ontology files using existing libraries.
- Representing Entio-specific project objects, symbols, validation results, and graph diffs.
- Running basic validation checks.
- Generating semantic diffs.
- Exposing these capabilities through a simple CLI.

Implemented CLI commands:

```bash
./gradlew :cli:run --args="validate ../examples/simple-ontology"
./gradlew :cli:run --args="symbols ../examples/simple-ontology"
./gradlew :cli:run --args="diff ../examples/simple-ontology ../examples/simple-ontology"
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

- `semantic-engine` currently exposes the project-loading workflow as smaller services, not as a single public `ProjectLoader`.
- `EntioProject` exists as a core data object, but it is not yet constructed by a public engine API.
- Only Turtle is supported.
- Semantic diffs are graph-triple based, with a small special case for `rdfs:label` changes.
- CLI output is text-only.
- Entio does not store project versions itself; `diff` compares two project directories supplied by the caller.

## Explicit Non-Goals For Phase 1

Phase 1 should not include:

- VS Code extension.
- Web app.
- Document ingestion.
- Autonomous AI agents.
- Schema RAG.
- Entity resolution.
- Stardog integration.
- Full FIBO indexing.
- A custom RDF, OWL, or SHACL framework.

## Technical Principle

Entio should not reinvent RDF, OWL, or SHACL.

The project should use existing libraries for RDF parsing, graph representation, ontology handling, and validation where possible. Entio should define only the product-specific types and workflows that make the system useful, such as project configuration, validation reports, semantic diffs, and human-reviewable change proposals.

## Architecture Notes

- [Product Principles](docs/architecture/000-product-principles.md)
- [Phase 1 Scope](docs/architecture/001-phase-1-scope.md)
- [Technical Approach](docs/architecture/002-technical-approach.md)
- [Kotlin Engine Guidelines](docs/architecture/003-kotlin-engine-guidelines.md)
- [Phase 1 Implementation Summary](docs/phase-summaries/phase-1-summary.md)
