# Entio Phase 3 Summary

## Status

Phase 3, the Semantic Description Layer, is complete on local `main` through Slice 11. The implementation remains a local Kotlin/JVM semantic engine with a thin machine-readable CLI boundary and a TypeScript VS Code presentation layer. Phase 4 subsequently completed the bounded OWL reasoning and SHACL constraint foundation. Phase 5 planning now covers external ontology browsing and basic Schema RAG.

## What Phase 3 Implemented

Phase 3 adds human-readable semantic descriptions and deterministic semantic search without replacing the RDF graph model or proposal lifecycle.

The implemented behavior includes:

- Explicit RDF annotation vocabulary for preferred labels, alternate labels, definitions, and general annotations.
- Extraction of explicit annotation metadata while preserving language tags, datatypes, and RDF resource values.
- Descriptor assembly for classes, object properties, datatype properties, annotation properties, and individuals.
- Deterministic preferred-label selection, fallback behavior, language handling, alternate-label ordering, definition ordering, and annotation ordering.
- Typed semantic edit translation for annotation-property creation, definitions, alternate labels, and general annotations.
- Deterministic semantic metadata validation integrated with existing proposal validation.
- Machine-readable CLI commands for descriptor lookup and deterministic search.
- Machine-readable CLI handling for semantic edit requests through the existing combined proposal preview, validation, diff, apply, reject, stale-baseline, and rollback lifecycle.
- VS Code semantic details, label-aware semantic search, match-reason presentation, semantic edit forms, RDF-term-safe value controls, and staged semantic previews.
- The VS Code semantic metadata forms submit definitions, alternate labels, and annotation values as plain strings. They do not expose language-tag, datatype, or resource-IRI controls for these human- and LLM-facing fields.
- Copied-fixture regression coverage for preview, rejection, approval, apply, reload, language/datatype round trips, descriptor output, search, and rollback behavior.

The workflow remains:

1. Load an Entio project and parse its ontology sources with Apache Jena.
2. Extract explicit semantic metadata and assemble deterministic descriptors.
3. Search descriptors through the Kotlin semantic engine when a user enters a query.
4. Translate supported semantic edit requests into graph changes in the Kotlin engine.
5. Preview, validate, diff, and verify the proposed graph without changing the source.
6. Keep valid edits in the existing VS Code staged list for combined review. Semantic metadata entered through the workbench is treated as plain human-readable text.
7. Apply only an approved, current proposal through the existing atomic applier, or reject it without source mutation.
8. Reload the project and expose the resulting descriptor/search state again.

The workflow is git-like by analogy only. Entio does not create commits, branches, pushes, pull requests, or durable project versions.

## Repository Structure

The repository contains the Kotlin/JVM Gradle modules and the VS Code extension:

```text
core-types/          Shared Entio contracts and RDF-term-aware data objects
semantic-engine/     Project loading, parsing, descriptors, search, and semantic translation
validation-engine/   Deterministic project, proposal, and semantic metadata validation
graph-diff/          Graph and proposal semantic diff generation
cli/                 Thin machine-readable and human-readable command-line boundary
shared/              Minimal generic utilities
vscode-extension/    TypeScript VS Code workbench presentation and delegation layer
examples/            Small local Entio project fixtures
docs/                Architecture, specifications, ExecPlans, decisions, and summaries
```

The VS Code extension delegates semantic work to the Kotlin CLI. It does not parse RDF or write Turtle directly.

## Module Responsibilities

### `core-types`

Defines shared Entio contracts, including RDF terms and triples, loaded projects and symbols, semantic descriptor kinds, localized text, annotation values, semantic edit requests, validation reports, proposals, baselines, diffs, apply results, and rollback results. It does not own RDF parsing or semantic policy.

### `semantic-engine`

Loads Entio projects, resolves ontology sources, parses Turtle/RDF with Apache Jena, extracts symbols and explicit metadata, assembles descriptors, performs deterministic semantic search, translates typed semantic edits, and provides project/proposal helpers.

### `validation-engine`

Runs deterministic project and proposal checks. Phase 3 adds semantic metadata validation for RDF literal shape, language tags, datatypes, target existence, annotation-property compatibility, and preferred-label ambiguity while retaining existing graph-change and proposal lifecycle checks.

### `graph-diff`

Compares graph states and formats semantic diff entries used by previews and human review. Phase 3 consumes the existing diff boundary rather than introducing a second semantic diff implementation.

### `cli`

Provides the reusable command boundary. Phase 3 adds `descriptor`/`describe` and `search`, plus structured semantic edit request handling through `proposal-combined`. The CLI delegates parsing, descriptor assembly, translation, validation, diffing, apply, rejection, and rollback to reusable modules.

### `shared`

Remains intentionally small and contains only generic utilities. It does not contain semantic policy or ontology product logic.

### `vscode-extension`

Normalizes Kotlin CLI responses, renders project symbols and semantic details, delegates semantic search and descriptor lookup, provides semantic edit forms, and keeps valid previews in the existing in-memory staged workflow. Its semantic metadata forms use plain string values for definitions, alternate labels, and annotations; lower-level RDF term options remain outside this UI boundary. It does not own RDF parsing, label policy, search ranking, or source persistence.

## Main Contracts

Important Phase 3 contracts include:

- `OntologyEntityDescriptor`: common semantic metadata plus kind-specific class, property, annotation-property, or individual structure.
- `LocalizedText`: lexical value with optional language tag and datatype IRI.
- `AnnotationValue` and RDF-term types: distinguish resources from literals and preserve literal metadata.
- `SemanticEditRequest`: typed requests for annotation-property creation, definitions, alternate labels, and general annotations.
- `SemanticSearchQuery` and `SemanticSearchResult`: deterministic query filters, match reason, and stable rank.
- `ValidationReport` and `ValidationIssue`: deterministic semantic and structural validation results.
- `ChangeSet`, `ChangeProposal`, `CombinedProposal`, and baseline contracts: preserve preview, review, stale detection, apply, reject, and rollback behavior.

## Developer Commands

Kotlin and Gradle verification:

```bash
./gradlew test
./gradlew build
./gradlew check
```

VS Code extension verification:

```bash
cd vscode-extension && npm test
```

Representative Phase 3 CLI commands, using an absolute project path when invoking Gradle from the repository root:

```bash
./gradlew :cli:run --args="descriptor $PWD/examples/simple-ontology https://example.com/entio/simple#Customer"
./gradlew :cli:run --args="search $PWD/examples/simple-ontology Customer"
```

The existing Phase 1, Phase 2, and Phase 2.5+ CLI commands remain available. Gradle’s `:cli:run` task executes from the CLI module working directory, so relative example paths may need to use `../examples/simple-ontology` instead.

## Fixtures And Regression Coverage

The committed fixture is `examples/simple-ontology`, containing `entio.yaml` and `ontology/simple.ttl`. Mutating tests copy this project into temporary directories before changing it.

Phase 3 regression coverage includes:

- Semantic descriptor and search service tests.
- CLI descriptor, search, semantic request, combined preview, rejection, apply, stale, and malformed-request tests.
- VS Code response normalization, semantic presentation, semantic form, staged workflow, and message-boundary tests.
- Copied-fixture end-to-end coverage for language-tagged definitions, datatyped annotations, alternate-label search, annotation search, preview without mutation, rejection without mutation, apply, and reload.
- Existing Phase 2.5+ regression coverage for deletion dependencies, atomic apply, post-save verification failure, and rollback restoration.

## Explicit Non-Goals

Phase 3 does not include:

- A web application or server API.
- Document ingestion, Schema RAG, embeddings, external retrieval, or autonomous agents.
- LLM-generated ontology edits.
- Entity resolution across documents or external sources.
- OWL reasoning, full OWL class-expression authoring, or a full SHACL environment.
- A custom RDF, OWL, SHACL, or Turtle framework.
- Durable staged-session or proposal persistence.
- Project version history or Git operations inside Entio.
- Direct RDF parsing, label policy, search ranking, or ontology writes in TypeScript.
- Full Protégé feature parity.

## Known Limitations And Follow-Up Work

- Turtle remains the supported ontology source format; broader RDF serialization support is not implemented.
- Descriptor metadata is based on explicit graph facts and does not perform OWL inference.
- Search is deterministic case-insensitive matching over preferred labels, alternate labels, IRIs, and general annotation values. It is not fuzzy search, indexed search, embedding search, or external retrieval.
- Semantic edit forms expose only the approved Phase 3 metadata operations. Unsupported OWL and SHACL editing remains out of scope.
- VS Code tests validate rendering, normalization, CLI message boundaries, and the plain-string semantic metadata form boundary; there is no full launched Extension Development Host regression suite.
- The workbench intentionally does not expose language tags, datatypes, or resource-valued annotations for semantic metadata entry. Those RDF-term forms remain available only through lower-level core and CLI contracts when needed.
- Staged changes and proposal state remain process/session scoped and are not durable across extension or CLI sessions.
- The CLI currently exposes semantic edit lifecycle behavior through the combined proposal boundary; it does not introduce a durable request store or independent staging service.
- Jena serialization preserves graph meaning and RDF terms but not original Turtle formatting or comments.
