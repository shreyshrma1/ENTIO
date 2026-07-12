# Phase 1.5 Implementation Summary

## Status

Phase 1.5 implemented a stabilization pass over the Phase 1 Kotlin/JVM Core Semantic Engine.

The implementation now includes a reusable `ProjectLoader`, a populated `EntioProject` aggregate, RDF-term-aware graph contracts, updated parsing, symbol extraction, validation compatibility, RDF-term-aware graph diff formatting, CLI delegation to `ProjectLoader`, and an end-to-end regression test.

This document summarizes the actual repository state after the Phase 1.5 implementation slices. It does not describe future behavior that has not been implemented.

## What Phase 1.5 Implemented

Phase 1.5 implemented:

- Entio-owned RDF term contracts in `core-types`.
- RDF resource and term distinction for IRIs, blank nodes, and literals.
- Literal datatype IRI and language tag preservation.
- RDF-term-aware `GraphTriple` construction while preserving temporary Phase 1 compatibility views.
- Turtle/RDF parsing that converts Apache Jena nodes into Entio-owned RDF terms.
- Symbol extraction over corrected RDF terms.
- Validation compatibility with the corrected graph model.
- Graph diffing and formatting for IRI resources, blank-node resources, and literals.
- A reusable `ProjectLoader` in `semantic-engine`.
- A populated `EntioProject` aggregate with config, resolved sources, source-specific loaded ontologies, extracted symbols, and combined graph state.
- CLI delegation to `ProjectLoader` for `symbols` and `diff`.
- End-to-end regression coverage for loading, validation, symbols, diffing, and CLI behavior over a fixture with blank nodes, datatyped literals, and language-tagged literals.

## Current Repository Structure

The current Kotlin/JVM Gradle workspace contains:

```text
core-types/
semantic-engine/
validation-engine/
graph-diff/
cli/
shared/
examples/simple-ontology/
docs/
```

Important documentation areas:

- `docs/architecture/`: product principles, technical approach, Phase 1 scope, Phase 1.5 scope, and Kotlin guidelines.
- `docs/specs/0002-phase-1.5-core-semantic-engine-stabilization.md`: Phase 1.5 feature spec.
- `docs/execplans/0002-phase-1.5-core-semantic-engine-stabilization.md`: Phase 1.5 implementation plan.
- `docs/decisions/phase-1.5-slice-*.md`: slice completion records.
- `docs/phase-summaries/`: implementation summaries.

## Modules

### `core-types`

`core-types` defines shared Entio data contracts. It remains independent from engine modules.

Important contracts:

- Project and source objects: `EntioProjectConfig`, `EntioProject`, `OntologySourceReference`, `ResolvedOntologySource`, `OntologyFormat`.
- Ontology and symbol objects: `LoadedOntology`, `LoadedSymbol`, `SymbolKind`, `Iri`.
- RDF term contracts: `RdfTerm`, `RdfResource`, `BlankNodeResource`, `RdfLiteral`.
- Graph objects: `GraphState`, `GraphTriple`.
- Validation objects: `ValidationReport`, `ValidationIssue`, `ValidationSeverity`, `ValidationStatus`.
- Diff objects: `SemanticDiff`, `SemanticDiffEntry`, `SemanticDiffKind`.
- Result wrapper: `EntioResult`.
- Review placeholder: `ChangeProposal`, `ChangeProposalStatus`.

`GraphTriple` now exposes RDF-term-aware fields:

- `subjectResource: RdfResource`
- `predicate: Iri`
- `objectTerm: RdfTerm`

It also preserves temporary compatibility fields:

- `subject: Iri`
- `objectValue: String`

Those compatibility fields keep existing consumers compiling while Phase 1.5 code migrates toward RDF-term-aware behavior.

### `semantic-engine`

`semantic-engine` owns project loading and Jena-backed ontology parsing.

Implemented services:

- `ProjectConfigLoader`: loads and validates the minimal `entio.yaml` shape.
- `OntologySourceResolver`: resolves local relative ontology paths safely under the project root.
- `OntologyParser`: parses Turtle/RDF through Apache Jena and converts Jena RDF nodes into Entio-owned `GraphTriple` values.
- `SymbolExtractor`: extracts classes, properties, shapes, and individuals from RDF type triples, using literal labels when present.
- `ProjectLoader`: composes config loading, source resolution, parsing, and symbol extraction into one reusable project-loading API.

`ProjectLoader.loadProject(projectRoot)` returns `EntioResult<EntioProject>`.

On success, `EntioProject` contains:

- project config,
- resolved ontology sources,
- source-specific loaded ontologies,
- deterministic extracted symbols,
- combined graph state.

Duplicate triples collapse only in the combined `EntioProject.graph`; source-specific ontology graphs remain available in `EntioProject.ontologies`.

### `validation-engine`

`validation-engine` keeps deterministic validation behavior.

Implemented classes:

- `ProjectValidator`
- `ValidationIssueSorter`

`ProjectValidator` still composes lower-level semantic services directly rather than calling `ProjectLoader`. This preserves validation-specific issue reporting and keeps the Phase 1 validation behavior stable.

Validation covers:

- missing project root,
- project root not being a directory,
- missing or invalid `entio.yaml`,
- missing required config fields,
- empty ontology sources,
- duplicate ontology source IDs,
- absolute or unsafe ontology paths,
- missing ontology source files,
- invalid Turtle,
- symbol extraction failures.

### `graph-diff`

`graph-diff` compares `GraphState` values and formats semantic diffs.

Implemented classes:

- `GraphDiffer`
- `SemanticDiffFormatter`

`GraphDiffer` now compares triples using RDF-term-aware equality and formats descriptions for:

- IRI resources,
- blank-node resources,
- plain literals,
- datatyped literals,
- language-tagged literals.

It preserves:

- added triple detection,
- removed triple detection,
- literal `rdfs:label` changes as `Changed` entries,
- deterministic output ordering.

Blank-node output is labeled as blank-node output and should not be treated as durable semantic identity.

### `cli`

`cli` exposes the engine through Picocli commands:

- `validate`
- `symbols`
- `diff`

`validate` continues to call `ProjectValidator`.

`symbols` and `diff` now delegate project loading to `ProjectLoader` through `CliProjectReader`:

- `symbols` formats `EntioProject.symbols`.
- `diff` loads both project roots and compares their combined graph states.

The CLI remains a thin adapter for argument parsing, output formatting, and exit codes. It does not own ontology parsing, validation rules, symbol extraction, or graph diff logic.

### `shared`

`shared` remains intentionally minimal.

It contains only a module marker and smoke test. No Entio product logic has been added to `shared`.

## How The Pieces Fit Together

### Project Loading

The reusable loading flow is:

1. `ProjectLoader.loadProject(projectRoot)` is called.
2. `ProjectConfigLoader` loads `entio.yaml`.
3. `OntologySourceResolver` resolves configured local ontology files.
4. `OntologyParser` parses each Turtle source through Apache Jena.
5. `SymbolExtractor` extracts symbols from each loaded ontology.
6. `ProjectLoader` assembles an `EntioProject`.
7. `EntioProject.graph` contains the combined graph state.

If config loading, source resolution, parsing, or symbol extraction fails, `ProjectLoader` returns `EntioResult.Failure` with structured validation issues.

### Ontology Parsing

`OntologyParser` supports Turtle sources.

Apache Jena remains contained inside `semantic-engine`. Public Entio contracts do not expose Jena types.

Parsed RDF nodes are mapped into Entio-owned terms:

- URI resources become `Iri`.
- Blank nodes become `BlankNodeResource`.
- Literals become `RdfLiteral`.
- Literal datatype IRIs and language tags are preserved.

### Symbol Extraction

`SymbolExtractor` operates on `GraphTriple.subjectResource` and `GraphTriple.objectTerm`.

It recognizes:

- OWL and RDFS classes,
- RDF and OWL properties,
- SHACL node and property shapes,
- other typed IRI subjects as individuals.

It ignores blank-node typed subjects as durable symbols and ignores non-literal labels deterministically.

### Validation

`ProjectValidator` remains validation-focused.

It checks the same Phase 1 project, config, source, parse, and symbol-extraction conditions while remaining compatible with the corrected RDF term model.

Validation output remains a `ValidationReport` with deterministic `ValidationIssue` ordering.

### Diffing

`GraphDiffer.diff(before, after)` compares two `GraphState` values.

It produces:

- `Added` entries for triples in `after` only,
- `Removed` entries for triples in `before` only,
- `Changed` entries for literal `rdfs:label` changes on the same subject.

Formatting is human-readable and RDF-term-aware, but it remains a basic triple diff. It does not perform reasoning or ontology-aware inference.

### CLI Commands

CLI behavior:

```bash
./gradlew :cli:run --args="validate ../examples/simple-ontology"
./gradlew :cli:run --args="symbols ../examples/simple-ontology"
./gradlew :cli:run --args="diff ../examples/simple-ontology ../examples/simple-ontology"
```

Exit codes:

- `validate`: `0` for valid projects, `1` for validation failure.
- `symbols`: `0` for success, `1` for loading failure.
- `diff`: `0` for no changes, `1` for diff entries, `2` for project loading failure.

Gradle runs `:cli:run` from the `cli` module working directory, so example paths use `../examples/simple-ontology`.

## Developer Commands

Run all tests:

```bash
./gradlew test
```

Build all modules:

```bash
./gradlew build
```

Run all checks:

```bash
./gradlew check
```

Run focused module tests:

```bash
./gradlew :core-types:test
./gradlew :semantic-engine:test
./gradlew :validation-engine:test
./gradlew :graph-diff:test
./gradlew :cli:test
./gradlew :shared:test
```

## Examples And Fixtures

The committed example project is:

```text
examples/simple-ontology/
  entio.yaml
  ontology/simple.ttl
```

The example config declares one Turtle ontology source:

```yaml
name: simple-ontology
ontologySources:
  - id: simple
    path: ontology/simple.ttl
    format: turtle
```

The example ontology defines:

- `Customer` as an `rdfs:Class`,
- `Account` as an `rdfs:Class`,
- `ownsAccount` as an `rdf:Property`.

Tests also create small temporary fixtures for:

- invalid YAML,
- missing config files,
- unsafe paths,
- duplicate source IDs,
- missing ontology files,
- invalid Turtle,
- RDF term fidelity,
- before/after graph diffs,
- CLI end-to-end behavior.

`Phase15EndToEndRegressionTest` creates a temporary fixture with blank nodes, datatyped literals, and language-tagged literals.

## Test Coverage

Current tests cover:

- RDF term construction and compatibility.
- Core object construction.
- Project config loading.
- Ontology source resolution.
- Turtle parsing with IRI resources, blank nodes, plain literals, datatyped literals, and language-tagged literals.
- Symbol extraction over corrected RDF terms.
- Validation compatibility and deterministic issue sorting.
- Graph diffing and formatting over mixed RDF term types.
- `ProjectLoader` success and structured failure behavior.
- CLI command behavior and exit codes.
- End-to-end Phase 1.5 regression behavior.

## Phase 1.5 Non-Goals

Phase 1.5 intentionally does not include:

- VS Code extension.
- Web app.
- API server or server mode.
- Watch mode.
- Document ingestion.
- LLM integration.
- Autonomous AI agents.
- Schema RAG.
- Entity resolution.
- Graph database integration.
- Full domain ontology indexing.
- Ontology mutation or persistence of edited graphs.
- Change approval, undo, redo, rollback, or version history.
- Human review UI or workflow.
- OWL reasoning.
- Full SHACL validation or authoring.
- Named graph support.
- Full source-text-preserving Turtle round trips.
- Custom RDF, OWL, SHACL, or Turtle framework.

## Known Limitations And Follow-Up Work

- `README.md` and `AGENTS.md` have been updated to describe Phase 1.5 as complete and Phase 2 as the active planning phase.
- `ProjectValidator` still composes lower-level services directly rather than using `ProjectLoader`. This is intentional for now because Slice 8 said `validate` should keep using validation behavior unless Slice 5 routed validation through `ProjectLoader`.
- `GraphTriple` still includes compatibility fields `subject: Iri` and `objectValue: String`. RDF-term-aware code should prefer `subjectResource` and `objectTerm`.
- Blank-node identifiers are parser-local and should not be treated as durable semantic identities.
- Only Turtle sources are supported.
- Diffing is triple-based and only has a small special case for literal label changes.
- CLI output is text-only.
- Entio does not store project versions. `diff` compares two project directories supplied by the caller.
- `ChangeProposal` remains a data object only; no approval workflow exists.
- `shared` remains minimal and contains no real utilities yet.

## Plan Versus Implementation Notes

The implementation follows the Phase 1.5 spec and ExecPlan closely:

- `ProjectLoader` exists and returns `EntioResult<EntioProject>`.
- `EntioProject` is populated by engine behavior.
- RDF terms preserve IRI resources, blank nodes, literals, datatype IRIs, and language tags.
- CLI `symbols` and `diff` delegate project loading to `ProjectLoader`.
- End-to-end regression coverage exists.
- `./gradlew test`, `./gradlew build`, and `./gradlew check` have passed during the final regression slice.

Differences or clarifications:

- Validation was not refactored to use `ProjectLoader`; it remains validation-specific orchestration.
- Temporary `GraphTriple` compatibility fields remain in place.
- No committed example fixture update was needed for blank-node and literal metadata coverage; the Phase 1.5 regression test creates that fixture at test time.
- Documentation status metadata has been updated in the main repository status docs to say Phase 1.5 is complete.
