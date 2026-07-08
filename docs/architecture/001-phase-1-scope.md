# Phase 1 Scope

Phase 1 is the Core Semantic Engine. It is the first backend foundation of Entio.

The current repository work is Phase 0B: documentation and planning context needed before the Kotlin/JVM Phase 1 scaffold and implementation begin.

## Phase 1 Goals

Phase 1 should provide the minimal backend foundation for working with small ontology projects.

It should support:

- Loading an Entio project.
- Parsing small Turtle/RDF ontology files using existing libraries.
- Representing Entio-specific project objects.
- Representing symbols from loaded ontologies.
- Representing validation results.
- Representing graph diffs.
- Running basic validation checks.
- Generating semantic diffs.
- Exposing core capabilities through a simple CLI.

Phase 1 is backend/core-engine work only. Future TypeScript interfaces, such as VS Code or web UI layers, may be added later, but they are not part of Phase 1.

## Working Definitions

- Entio project: folder that contains project configuration and ontology files. In Phase 1, it is intentionally small and does not include documents, AI agents, external integrations, or production graph storage.

- symbol: named thing loaded from an ontology, such as a class, property, individual, shape, or namespace term.

- semantic diff: structured summary of meaningful graph changes, such as added triples, removed triples, added classes, changed labels, or changed relationships.

- human-reviewable change proposal: draft change that can be inspected before becoming official. In Phase 1, this may only be represented as a data structure, not a full review workflow.

## Expected Project Concepts

Phase 1 may introduce product-specific concepts such as:

- Entio project configuration.
- Ontology source references.
- Loaded symbols.
- Validation report objects.
- Semantic diff objects.
- Human-reviewable change proposal objects.

These should wrap or organize standards-based data. They should not replace RDF, OWL, or SHACL concepts.

## Non-Goals

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

## Success Criteria

Phase 1 is successful if a developer can use a small CLI to load an Entio project, parse small Turtle/RDF ontology files, run basic validation checks, and view semantic diffs in a form that can support human review.

Phase 1 is not expected to solve large-scale graph storage, document extraction, entity matching, production governance, or full enterprise deployment.
