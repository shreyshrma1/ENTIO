# Phase 1.5 Scope

Phase 1.5 is complete. It was the Core Semantic Engine Stabilization phase. It built directly on the completed Phase 1 implementation and prepared the semantic engine for later ontology mutation, ontology-aware modeling, Schema RAG, document ingestion, and agent workflows.

Phase 1.5 is not a new product-feature phase. Its purpose is to consolidate the Phase 1 loading flow behind a reusable API and correct the RDF data model so that later phases do not depend on lossy or CLI-specific behavior.

## Current Starting Point

At the start of Phase 1.5, the repository already includes:

- Kotlin/JVM Gradle multi-module structure.
- Entio project config loading from `entio.yaml`.
- Safe local ontology source resolution.
- Turtle/RDF parsing through Apache Jena.
- Basic symbol extraction.
- Deterministic validation reports.
- Basic graph diffs.
- Thin CLI commands for validation, symbols, and diffing.
- An `EntioProject` data type that is not yet constructed by a reusable engine API.
- A simplified RDF triple representation that does not preserve all RDF term distinctions.

## Phase 1.5 Goals

Phase 1.5 should make the completed Phase 1 engine reusable and semantically safer.

It should support:

- Loading a complete Entio project through one reusable `ProjectLoader` API.
- Constructing and returning an `EntioProject` aggregate.
- Moving project-loading orchestration out of CLI-specific code and into `semantic-engine`.
- Preserving RDF resources (`RdfResource`) and terms (`RdfTerm`) without collapsing IRIs, blank nodes, and literals into one simplified value.
- Preserving literal datatype IRIs and language tags.
- Preserving blank-node participation in parsed RDF graphs.
- Updating symbol extraction, graph diffing, validation, formatting, and CLI behavior to work with the corrected RDF term model.
- Preserving all existing Phase 1 behavior and deterministic output.


Phase 1.5 remains backend/core-engine work only. It should establish stable contracts for later phases without introducing ontology editing, AI, document processing, or UI behavior.

## Working Definitions

- project loading orchestration: the ordered process of loading project configuration, resolving ontology sources, parsing ontology files, extracting symbols, and assembling the combined project state.

- `ProjectLoader`: the reusable semantic-engine service that owns project loading orchestration and returns a structured success or failure result.

- `EntioProject`: the canonical in-memory aggregate for a successfully loaded Entio project. It should contain the loaded project configuration, resolved sources, parsed ontologies, extracted symbols, and combined graph state needed by later engine consumers.

- RDF term: any value that may appear as an RDF node. In Phase 1.5, this includes IRI resources, blank-node resources, and literals.

- RDF resource: an RDF term that can identify a graph resource and may appear in subject position. In Phase 1.5, this includes IRI resources and blank-node resources, but not literals.

- literal: an RDF value with a lexical form and optional datatype IRI or language tag.

- graph state: a deterministic set of Entio-owned RDF triples. A triple should preserve subject resource type, predicate IRI, and object term type.

- combined project graph: the union of triples from all loaded ontology sources. Duplicate triples may be collapsed in the combined graph, while source-specific loaded ontologies retain their own graph contents.

## Expected Project Concepts

Phase 1.5 may introduce or refine product-specific concepts such as:

- `ProjectLoader`.
- A fully populated `EntioProject` aggregate.
- Structured project-loading failures.
- `RdfTerm`.
- `RdfResource`.
- IRI resource values.
- Blank-node resource values.
- Literal values with datatype and language metadata.
- Updated `GraphTriple` and `GraphState` contracts.
- Deterministic RDF term comparison and formatting behavior.

These concepts should wrap or organize standards-based RDF data. They should not replace RDF, OWL, or SHACL semantics with a custom semantic-web framework.

## Required Behavior

### Reusable Project Loading

Phase 1.5 should provide one public semantic-engine entry point equivalent to:

```kotlin
fun loadProject(projectRoot: Path): EntioResult<EntioProject>
```

The project loader should:

- Load `entio.yaml`.
- Resolve configured ontology sources.
- Parse each supported ontology source.
- Extract symbols from each loaded ontology.
- Assemble source-specific loaded ontology results.
- Assemble a deterministic combined symbol list.
- Assemble a deterministic combined graph state.
- Return structured failures without requiring callers to reproduce the loading sequence.

The CLI should delegate to this reusable API rather than owning semantic project-loading orchestration.

### RDF Term Fidelity

Phase 1.5 should preserve at least:

- IRI subjects.
- Blank-node subjects.
- IRI objects.
- Blank-node objects.
- Plain literals.
- Datatyped literals.
- Language-tagged literals.

The internal Entio graph model should prevent literals from being used as triple subjects.

### Compatibility Updates

Existing Phase 1 behavior should continue to work after the RDF term model changes.

Affected behavior may include:

- Turtle parsing.
- Symbol extraction.
- Project validation.
- Graph equality and ordering.
- Semantic diff generation.
- Semantic diff formatting.
- CLI output.
- Unit and end-to-end tests.

## Design Constraints

- Apache Jena should remain contained inside `semantic-engine` rather than leaking across module boundaries.
- `core-types` should contain stable Entio-owned contracts, not Jena types.
- `ProjectLoader` should compose existing Phase 1 services rather than duplicating their behavior.
- The CLI should remain a thin adapter for argument parsing, output formatting, and exit codes.
- A loaded project should remain separate from its validation report. Loading and validation should be independently callable.
- Source-specific ontology graphs should remain available even if the combined project graph collapses duplicate triples.
- Blank-node identifiers should not be treated as durable user-facing semantic identities.
- Phase 1.5 should preserve deterministic ordering and deterministic test output.
- Existing public behavior should remain compatible unless a contract must change to preserve correct RDF semantics.

## Non-Goals

Phase 1.5 should not include:

- Ontology mutation or persistence of edited graphs.
- Add, remove, rename, or delete operations for classes, properties, individuals, or triples.
- Applying or approving change proposals.
- Undo, redo, rollback, or change history.
- OWL class-expression modeling.
- Full OWL axiom interpretation.
- OWL reasoning or consistency checking.
- Full SHACL validation or authoring.
- Schema RAG.
- Document ingestion.
- LLM integration.
- Autonomous AI agents.
- Entity resolution.
- Human review UI or approval workflow.
- VS Code extension.
- Web app.
- API server or server mode.
- Neptune, Stardog, or other graph database integration.
- Named graph support unless explicitly approved by a later spec.
- RDF graph canonicalization beyond what is required for deterministic Phase 1.5 tests.
- Full source-text-preserving Turtle round trips.
- A custom RDF, OWL, or SHACL framework.

## Success Criteria

Phase 1.5 is successful if:

- A developer can call one reusable `ProjectLoader` API to load a complete Entio project.
- The loader returns a populated `EntioProject` containing the project configuration, resolved sources, loaded ontologies, extracted symbols, and combined graph state.
- The CLI no longer owns project-loading orchestration.
- Parsed triples distinguish IRIs, blank nodes, and literals.
- Literal datatype IRIs and language tags are preserved.
- Literals cannot be represented as RDF subjects through the Entio type model.
- Existing validation, symbol, and diff CLI commands continue to work.
- Semantic diffing remains deterministic under the corrected RDF term model.
- Multi-source project loading has deterministic behavior.
- Duplicate triples may be collapsed in the combined graph without losing source-specific ontology graphs.
- Existing Phase 1 tests are updated as needed and continue to pass.
- New tests cover project aggregation and RDF term fidelity.
- `./gradlew test`, `./gradlew build`, and `./gradlew check` all pass.
- Repository status documentation accurately reflects completion of Phase 1 and the role of Phase 1.5.
- No Phase 2 or later product behavior is introduced.

Phase 1.5 is not expected to make Entio an ontology editor or document-to-KG system. It should leave the repository with a reusable, faithful, and stable semantic foundation for those later phases.
