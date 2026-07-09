# Feature Spec: Phase 1 Core Semantic Engine

## Status

Draft

## Problem

Entio needs a first backend foundation for building trustworthy enterprise knowledge graphs from ontology-governed project files.

The system must be able to load a small Entio project, parse small Turtle/RDF ontology files with established JVM semantic-web libraries, represent Entio-specific project and review concepts, run deterministic validation checks, produce human-reviewable semantic diffs, and expose the core behavior through a thin CLI.

## Goals

- Define the minimal Kotlin/JVM Core Semantic Engine behavior for Phase 1.
- Support loading a small local Entio project from an `entio.yaml` file.
- Support ontology source references to local Turtle/RDF files.
- Parse Turtle/RDF using an established JVM library, not custom parsing.
- Represent Entio project configuration, ontology source references, loaded symbols, validation reports, semantic diffs, and change proposals.
- Run deterministic validation checks with stable, testable output.
- Generate basic semantic diffs suitable for human review.
- Provide a thin CLI for project loading, validation, symbol listing, and diffing.
- Keep module boundaries clear and prepare for later interfaces to consume structured outputs.

## Non-Goals

- No VS Code extension.
- No web app.
- No autonomous AI agents.
- No document ingestion.
- No entity resolution.
- No Schema RAG.
- No full domain ontology indexing or remote ontology fetching.
- No production graph database or graph server.
- No custom RDF, OWL, SHACL, or Turtle framework.
- No full OWL reasoning.
- No full SHACL authoring environment.
- No human review UI or workflow engine.

## Proposed Behavior

Phase 1 should let a developer run Entio against a small local project directory and receive deterministic structured results.

The engine's workflow should be:

- Read `entio.yaml` from a project root.
- Validate the project configuration shape.
- Resolve configured ontology source files relative to the project root.
- Parse supported Turtle/RDF ontology files through an established JVM library.
- Extract a simple list of loaded symbols from parsed ontology data.
- Produce structured validation reports with stable issue codes, severities, messages, and source locations where available.
- Compare two graph states and produce a structured semantic diff with added, removed, and changed entries where possible.
- Expose the behavior through reusable Kotlin modules and a thin CLI.

The CLI should call reusable module APIs. It should not inherently contain any functionality.

## Inputs And Outputs

This section describes the project files, configuration shape, core data structures, semantic diff output, and CLI behavior involved in Phase 1.

### Expected Input Project Structure

A Phase 1 Entio project is a small local folder:

```text
example-project/
  entio.yaml
  ontology/
    domain.ttl
```

Multiple ontology files may be supported:

```text
example-project/
  entio.yaml
  ontology/
    core.ttl
    finance.ttl
```

The project root is the directory containing `entio.yaml`.

### Expected `entio.yaml` Shape

Phase 1 should support a minimal YAML shape:

```yaml
name: simple-ontology
ontologySources:
  - id: simple
    path: ontology/simple.ttl
    format: turtle
```

Fields:

- `name`: required project name, non-empty string.
- `ontologySources`: required list of ontology source references.
- `ontologySources[].id`: required stable source ID, unique within the project.
- `ontologySources[].path`: required relative path from the project root.
- `ontologySources[].format`: required ontology format. Phase 1 supports `turtle`.

Validation should reject:

- Missing `entio.yaml`.
- Invalid YAML.
- Missing `name`.
- Empty `name`.
- Missing `ontologySources`.
- Empty `ontologySources`.
- Duplicate source IDs.
- Absolute or unsafe ontology paths.
- Missing ontology files.
- Unsupported formats.

### Core Data Objects

The following objects should be introduced in `core-types` as immutable Kotlin data classes, enums, or sealed interfaces.

Project and source objects:

- `EntioProjectConfig`
- `EntioProject`
- `OntologySourceReference`
- `ResolvedOntologySource`
- `OntologyFormat`

Ontology and symbol objects:

- `LoadedOntology`
- `LoadedSymbol`
- `SymbolKind`
- `Iri`

Validation objects:

- `ValidationSeverity`
- `ValidationIssue`
- `ValidationReport`
- `ValidationStatus`

Diff and review objects:

- `GraphTriple`
- `GraphState`
- `SemanticDiff`
- `SemanticDiffEntry`
- `SemanticDiffKind`
- `ChangeProposal`
- `ChangeProposalStatus`

Result objects:

- `EntioResult<T>`

These objects should organize Entio workflow concepts. They should not replace the underlying RDF model supplied by Apache Jena, RDF4J, or another approved semantic-web library.

### Semantic Diff Output

Phase 1 semantic diffing should compare two graph states and produce a stable, human-reviewable summary.

Minimum behavior:

- Identify added triples.
- Identify removed triples.
- Sort diff entries deterministically.
- Preserve subject, predicate, and object identity.
- Provide readable labels or descriptions where available.

Nice-to-have within Phase 1, if small:

- Group obvious class additions.
- Group obvious property additions.
- Surface label changes as changed entries rather than only raw triple add/remove pairs.

Out of scope for Phase 1:

- Full ontology-aware reasoning diffs.
- Full OWL axiom diffs.
- Visual diff UI.
- Human approval workflows.

### CLI Input and Output

The Phase 1 CLI should be a thin command-line wrapper over reusable module APIs.

Candidate commands:

```text
entio validate <project-root>
entio symbols <project-root>
entio diff <before-project-root> <after-project-root>
```

Expected behavior:

- `validate` loads the project, resolves ontology sources, parses ontology files, runs validation, prints a readable validation report, and exits non-zero if errors are present.
- `symbols` loads and parses the project, then prints extracted symbols in stable order.
- `diff` loads two project roots and prints a stable semantic diff.

The CLI should not:

- Parse Turtle/RDF directly.
- Implement validation rules directly.
- Compute semantic diffs directly.
- Depend on UI, web, or VS Code concepts.

## Validation and Error Handling

Validation must be deterministic and repeatable.

Phase 1 validation should include:

- Project root exists and is a directory.
- `entio.yaml` exists.
- `entio.yaml` parses as YAML.
- Required config fields are present.
- Ontology source IDs are unique.
- Ontology paths are relative, normalized, and stay within the project root.
- Referenced ontology files exist.
- Ontology formats are supported.
- Turtle/RDF files parse successfully.
- Loaded symbols can be extracted without crashing.
- Diff output can be produced with stable identity.

Validation reports should:

- Include `ok` or status information.
- Include zero or more descriptive structured issues.
- Use stable issue codes.
- Include severity: error, warning, or info.
- Use deterministic ordering.
- Avoid machine-specific absolute paths in user-facing messages where practical.

Expected project problems should produce validation issues rather than unstructured crashes.

## Test Cases

- Valid simple project loads successfully.
- Missing `entio.yaml` returns a validation error.
- Invalid YAML returns a validation error.
- Missing project name returns a validation error.
- Empty ontology source list returns a validation error.
- Duplicate ontology source IDs return a validation error.
- Unsafe ontology path returns a validation error.
- Missing ontology file returns a validation error.
- Unsupported ontology format returns a validation error.
- Invalid Turtle file returns a parse validation error.
- Valid Turtle file produces expected loaded symbols.
- Validation report issue ordering is stable.
- Diff of identical graph states is empty.
- Diff with one added triple reports one added entry.
- Diff with one removed triple reports one removed entry.
- CLI `validate` returns zero for a valid project.
- CLI `validate` returns non-zero for an invalid project.
- CLI output ordering is stable across repeated runs.

## Acceptance Criteria

- The Kotlin/JVM Gradle scaffold exists with the intended modules.
- `core-types` defines the core immutable data objects and fixed states.
- `semantic-engine` can load project config, resolve ontology sources, parse Turtle/RDF, and extract basic symbols.
- `validation-engine` produces deterministic validation reports for supported Phase 1 checks.
- `graph-diff` produces deterministic semantic diffs for basic graph additions and removals.
- `cli` exposes thin commands for validation, symbols, and diff.
- `shared` contains only generic utilities.
- All behavior is covered by focused tests.
- The example project can be used in an end-to-end test.
- Verification commands pass.
- No Phase 1 non-goals are introduced.
- No custom RDF, OWL, SHACL, or Turtle framework is introduced.

## Open Questions

- Should Phase 1 prefer Apache Jena or RDF4J as the initial RDF/Turtle library?
- Should `entio.yaml` allow multiple ontology formats later, or should Phase 1 keep only `turtle`?
- Should labels be extracted only from `rdfs:label`, or should common alternatives be included?
- Should symbol extraction include OWL classes and RDF properties only, or also individuals and SHACL shapes in the first implementation?
- Should CLI output default to plain text, JSON, or support both?
- How much semantic grouping should graph diffs attempt in Phase 1 beyond raw triple additions and removals?
