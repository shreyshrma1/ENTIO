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

This repository contains the implemented Entio foundation through Phase 10. Phase 10, Materialize Inferred Relationships, is complete.

Native AI execution is not part of the current product surface; the remaining provider boundary is limited to credential entry, model discovery, and model selection.

Phase 1 is the first backend foundation for Entio. It uses Kotlin/JVM because the core work is ontology loading, RDF/Turtle parsing, deterministic validation, semantic diffing, and CLI behavior.

The current implementation supports small local Entio projects, Turtle/RDF parsing through Apache Jena, RDF-term-aware graph triples, deterministic validation reports, semantic graph diffs, reusable project loading, and a thin CLI.

Phases 2 through 8 are preserved as historical delivery records. The current workbench preserves the Kotlin semantic engine as the authority for RDF and ontology behavior and exposes only optional provider credential and model settings from the former AI surface. Phase 9 adds an additive, bounded, read-only ontology map inside Explore. Phase 10 lets web users deliberately stage supported inferred facts through the existing proposal workflow.

## Workspace Structure

The repository contains the Kotlin/Gradle semantic workspace, a Ktor web adapter, and two TypeScript clients:

- `core-types`
- `semantic-engine`
- `validation-engine`
- `graph-diff`
- `cli`
- `shared`
- `web-server`
- `web-app`
- `vscode-extension`

The VS Code extension consumes the Kotlin/JVM core engine through the machine-readable CLI boundary. The React web application consumes versioned Ktor HTTP and WebSocket contracts. Neither client duplicates RDF or semantic policy.

## Current Implemented Capabilities

The current implementation supports:

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
- Browsing projects and symbols in the VS Code ontology workbench.
- Resolving entities by label, generating deterministic IRIs, reviewing deletion dependencies, staging multiple edits, and previewing combined changes.
- Building semantic descriptors for classes, properties, annotation properties, and individuals.
- Selecting preferred labels and extracting alternate labels, definitions, and explicit annotations deterministically.
- Searching semantic descriptions through the Kotlin engine and machine-readable CLI.
- Editing supported semantic metadata through the existing staged proposal workflow.
- Running bounded OWL reasoning with asserted and inferred separation, import status, explanations, fingerprints, and feature limitations.
- Validating configured SHACL data against configured shape graphs in asserted-only or explicit asserted-plus-inferred modes.
- Inspecting SHACL shapes and reasoning/SHACL proposal impact through the CLI and VS Code workbench.
- Applying approved multi-source proposals atomically with reload verification and rollback on failure.
- Loading a pinned, read-only FIBO package and searching its catalog deterministically.
- Reviewing external dependencies and preparing controlled FIBO reuse or local-extension proposals while preserving original external IRIs.
- Serving approved projects through versioned Ktor HTTP and WebSocket contracts.
- Browsing and editing projects through a React web workbench with server-authoritative staging and proposal state.
- Coordinating collaboration presence, shared activity, baseline conflicts, and asynchronous reasoning and SHACL jobs.
- Browsing FIBO content and staging supported external reuse intents from the browser.
- Storing optional per-user provider credentials in server memory without returning the secret to the browser.
- Discovering models available to the current provider credential and requiring explicit model selection and verification.
- Opening a project-scoped ontology map from Explore to inspect bounded local classes, properties, individuals, and asserted relationships without modifying ontology data.
- Expanding, searching, filtering, dragging, panning, scrolling, zooming, and navigating the map while retaining temporary state only for the open tab.
- Rejecting stale or invalid graph continuations through fingerprint-aware, authorized Ktor read contracts.
- Reviewing asserted and inferred reasoning facts separately in the web Reasoning workspace.
- Staging selected inferred subclass relationships, individual types, and object-property assertions as an all-or-nothing batch using server-issued fact IDs.
- Rechecking reasoning freshness, duplicates, writable sources, and import safety before staging, then retaining reasoning provenance through normal proposal review, atomic apply, reload, and rollback.

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
./gradlew :cli:run --args="deletion-dependencies ../examples/simple-ontology simple --iri https://example.com/entio/simple#recievedInvoice"
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

Run the web server:

```bash
./gradlew :web-server:run
```

Run and verify the web application:

```bash
cd web-app
npm ci
npm run dev
npm test
npm run build
npm run test:e2e
```

Verify the VS Code extension:

```bash
cd vscode-extension
npm ci
npm test
```

## Current Limitations

- Only Turtle is supported.
- Semantic diffs are graph-triple based, with a small special case for `rdfs:label` changes.
- The VS Code extension covers the approved Phase 2.5 and Phase 2.5+ typed-edit and deletion flows, but it does not provide full Protégé-level ontology authoring or a full launched Extension Development Host regression suite.
- Entio does not store project versions itself; `diff` compares two project directories supplied by the caller.
- Proposal state is reconstructed within the current CLI invocation and is not persisted as long-term project history.
- Jena serialization does not preserve original Turtle source formatting or comments.
- Proposal and staged-change state is process/session scoped rather than a durable review store.
- Web project registration, development identity, collaboration, semantic jobs, staging, and AI credentials are in-memory development boundaries rather than production persistence or authentication.
- Provider credentials, model settings, and discovery state remain in memory and are cleared on server restart. Native assistant conversations, runs, drafts, audits, and task workspaces are not exposed by the current product surface.
- Reasoning results and materialization provenance are in-memory workflow state. Materialization is web-only, always user initiated, supports at most 100 selected facts per request, and does not add inferred relationships to the ontology map.

## Implemented Phase 2 Through Phase 6 Workflow

Phase 2 provides:

- Add controlled graph changes and atomic change sets.
- Translate supported typed ontology edits into graph changes in the Kotlin engine.
- Generate preview graphs without changing source files.
- Generate semantic diffs and validation reports before approval.
- Apply only approved and current proposals to the correct Turtle source.
- Restore the previous source and graph state when save or verification fails.
- A minimal VS Code ontology workbench.
- Label-first selection, deterministic IRI generation, explicit deletion dependency review, and multi-edit staging.
- Combined preview, rejection restoration, atomic application, reload, and rollback for the complete staged set.
- A git-like semantic workflow by analogy only: draft, preview, diff, review, approve, and apply.

Phase 3 adds:

- Human-readable semantic descriptors and explicit annotation vocabulary.
- Deterministic preferred-label selection, semantic ordering, and label-aware search.
- Typed semantic edits for annotation properties, definitions, alternate labels, and general annotations.
- Semantic validation and CLI/VS Code integration without moving RDF or semantic policy into TypeScript.

Phase 4 adds:

- Bounded OWL reasoning with asserted and inferred separation, explanations, import status, and explicit feature limitations.
- SHACL shape authoring and validation against configured data and shape graphs.
- Reasoning and SHACL proposal-impact checks within the controlled review workflow.

Phase 5 adds:

- A pinned, immutable FIBO package with deterministic catalog browsing and search.
- External semantic descriptors, dependency review, and controlled reuse or local-extension proposals.
- CLI and VS Code workflows that preserve original external IRIs.

Phase 6 adds:

- A versioned Ktor server over reusable Kotlin services.
- A React browser workbench with shared staging, proposals, collaboration, reasoning, SHACL, and FIBO views.
- In-memory development identity, collaboration, semantic-job, and credential boundaries.
- Optional provider credential and model settings with server-side verification; no native assistant execution.

## Explicit Historical Non-Goals For Phase 2 Through Phase 2.5+

Phase 2 should not include:

- Web app.
- Document ingestion.
- Autonomous AI agents.
- LLM-generated ontology edits.
- Schema RAG.
- Entity resolution across documents or external sources. Local deterministic label resolution is included in Phase 2.5+.
- Full domain ontology indexing.
- Production graph storage.
- Full Protégé feature parity.
- Full OWL class-expression editing.
- Full SHACL authoring or validation environment.
- A custom RDF, OWL, or SHACL framework.
- Long-term project version history.
- Durable staged-session or proposal persistence.
- Git staging, commits, pushes, branch management, or pull-request creation inside Entio.
- OWL reasoning or full SHACL validation.
- Schema RAG, embeddings, external ontology retrieval, and AI-generated ontology edits.

## Current Phase

Phase 10 is complete. It adds controlled materialization of selected inferred relationships to the existing web review workflow.

Phase 10:

- supports inferred subclass relationships, individual types, and object-property assertions;
- accepts opaque server-issued fact IDs rather than browser-authored triples;
- reruns applied-graph reasoning and validates ownership, freshness, duplicates, source choice, and import safety;
- stages up to 100 selected facts atomically through existing typed edits and the shared review queue;
- records reasoning provenance as workflow metadata, not ontology annotations;
- preserves human review, validation, semantic diff, SHACL/reasoning impact, atomic apply, reload, and rollback.

The Phase 9 ontology map remains read-only and asserted-only. Automatic materialization, durable persistence, CLI/VS Code materialization commands, AI selection, and map inference display remain out of scope.

Phases 7, 7.5, and 8 remain available as historical planning and implementation records. Their native AI execution surfaces have been removed from the current product; only provider credential and model selection settings remain active.

## Technical Principle

Entio should not reinvent RDF, OWL, or SHACL.

The project should use existing libraries for RDF parsing, graph representation, ontology handling, and validation where possible. Entio should define only the product-specific types and workflows that make the system useful, such as project configuration, validation reports, semantic diffs, and human-reviewable change proposals.

## Architecture Notes

- [Product Principles](docs/architecture/000-product-principles.md)
- [Phase 1 Scope](docs/architecture/phase-1-scope.md)
- [Phase 1.5 Scope](docs/architecture/phase-1.5-scope.md)
- [Phase 2 Scope](docs/architecture/phase-2-scope.md)
- [Phase 2.5 Scope](docs/architecture/phase-2.5-scope.md)
- [Phase 2.5+ Scope](docs/architecture/phase-2.5-plus-scope.md)
- [Phase 3 Scope](docs/architecture/phase-3-scope.md)
- [Phase 4 Scope](docs/architecture/phase-4-scope.md)
- [Phase 5 Scope](docs/architecture/phase-5-scope.md)
- [Phase 6 Scope](docs/architecture/phase-6-scope.md)
- [Phase 7 Scope](docs/architecture/phase-7-scope.md)
- [Phase 7.5 Scope](docs/architecture/phase-7.5-scope.md)
- [Phase 8 Scope](docs/architecture/phase-8-scope.md)
- [Phase 9 Scope](docs/architecture/phase-9-scope.md)
- [Technical Approach](docs/architecture/002-technical-approach.md)
- [Kotlin Engine Guidelines](docs/architecture/003-kotlin-engine-guidelines.md)
- [Phase 1.5 Spec](docs/specs/0002-phase-1.5-core-semantic-engine-stabilization.md)
- [Phase 2 Spec](docs/specs/0003-phase-2-controlled-ontology-editing-workbench.md)
- [Phase 2.5+ Spec](docs/specs/0005-phase-2.5-plus-ontology-workbench-usability.md)
- [Phase 1 Implementation Summary](docs/phase-summaries/phase-1-summary.md)
- [Phase 1.5 Implementation Summary](docs/phase-summaries/phase-1.5-summary.md)
- [Phase 2 Implementation Summary](docs/phase-summaries/phase-2-summary.md)
- [Phase 2.5+ Implementation Summary](docs/phase-summaries/phase-2.5-plus-summary.md)
- [Phase 3 Spec](docs/specs/0006-phase-3-semantic-description-layer.md)
- [Phase 3 ExecPlan](docs/execplans/0006-phase-3-semantic-description-layer.md)
- [Phase 3 Implementation Summary](docs/phase-summaries/phase-3-summary.md)
- [Phase 4 Spec](docs/specs/0007-phase-4-owl-reasoning-shacl-revised.md)
- [Phase 4 Implementation Summary](docs/phase-summaries/phase-4-summary.md)
- [Phase 5 Spec](docs/specs/0008-phase-5-external-ontology-browsing-schema-rag.md)
- [Phase 5 Implementation Summary](docs/phase-summaries/phase-5-summary.md)
- [Phase 6 Spec](docs/specs/0009-phase-6-collaborative-web-workbench-native-ai-foundation.md)
- [Phase 6 ExecPlan](docs/execplans/0009-phase-6-collaborative-web-workbench-native-ai-foundation-execplan.md)
- [Phase 6 Implementation Summary](docs/phase-summaries/phase-6-summary.md)
- [Phase 7 Spec](docs/specs/0012-phase-7-tool-driven-native-ai-ontology-copilot.md)
- [Phase 7 ExecPlan](docs/execplans/0012-phase-7-tool-driven-native-ai-ontology-copilot.md)
- [Phase 7.5 Spec](docs/specs/0013-phase-7.5-openai-model-discovery-selection-ai-boundary-cleanup.md)
- [Phase 7.5 ExecPlan](docs/execplans/0013-phase-7.5-openai-model-discovery-selection-ai-boundary-cleanup.md)
- [Phase 8 Spec](docs/specs/0014-phase-8-scalable-ai-ontology-workflow-orchestration.md)
- [Phase 8 ExecPlan](docs/execplans/0014-phase-8-scalable-ai-ontology-workflow-orchestration.md)
- [Phase 8 Implementation Summary](docs/phase-summaries/phase-8-summary.md)
- [Phase 9 Spec](docs/specs/0016-phase-9-interactive-ontology-graph-visualization.md)
- [Phase 9 ExecPlan](docs/execplans/0016-phase-9-interactive-ontology-graph-visualization.md)
- [Phase 9 Implementation Summary](docs/phase-summaries/phase-9-summary.md)
