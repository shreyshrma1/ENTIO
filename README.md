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

This repository is currently in Phase 0B: documentation and planning context for Phase 1.

Phase 1 will be the Core Semantic Engine, the first backend foundation for Entio. The Phase 1 core engine will be built in Kotlin/JVM because the core work is ontology loading, RDF/Turtle parsing, deterministic validation, semantic diffing, and CLI behavior.

This repository does not contain product implementation logic yet. The actual Kotlin/JVM Gradle multi-module scaffold will be created later by an explicit scaffold task.

## Future Workspace Direction

The future Phase 1 scaffold should use Kotlin/JVM with Gradle modules:

- `core-types`
- `semantic-engine`
- `validation-engine`
- `graph-diff`
- `cli`
- `shared`

TypeScript may still be used later for VS Code or web interfaces, but those interfaces are not part of Phase 1. Future TypeScript layers should consume the Kotlin/JVM core engine rather than duplicate semantic logic.

## Phase 1 Focus

Phase 1 should support:

- Loading an Entio project.
- Parsing small Turtle/RDF ontology files using existing libraries.
- Representing Entio-specific project objects, symbols, validation results, and graph diffs.
- Running basic validation checks.
- Generating semantic diffs.
- Exposing these capabilities through a simple CLI.

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
