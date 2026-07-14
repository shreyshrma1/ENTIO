# Technical Approach

Entio should build product workflow on top of existing semantic-web tooling rather than replacing it.

Phase 1 should prefer simple, inspectable implementations over broad abstractions.

## Do Not Reinvent RDF, OWL, Or SHACL

RDF, OWL, and SHACL are established standards with existing parsers, graph libraries, and validation tools.

Entio should use existing libraries where possible for:

- Turtle/RDF parsing.
- RDF graph representation.
- Namespace and IRI handling.
- Ontology loading.
- SHACL-style validation if and when shape validation is needed.

Entio-specific code should focus on project-level behavior and review workflows.

## Phase 1 Language And Library Posture

Phase 1 core-engine implementation should use Kotlin/JVM.

Kotlin/JVM is the selected direction because the Phase 1 core work is ontology loading, Turtle/RDF parsing, deterministic validation, semantic diffing, and CLI behavior. The JVM ecosystem gives Entio access to mature semantic-web libraries such as Apache Jena, RDF4J, and OWL API. Kotlin provides a modern developer experience while remaining compatible with those Java libraries.

Gradle is the build system for Entio's Kotlin/JVM multi-module workspace.

No semantic-web dependencies are added during Phase 0B. When Phase 1 implementation begins, dependency choices should be justified by a spec or architecture decision. The selected stack should make it straightforward to parse small Turtle/RDF files, inspect triples, and build deterministic validation and diff workflows.

TypeScript may still be used later for VS Code or web interfaces, but it is not the Phase 1 core-engine implementation language.

## Product-Specific Types

Entio may define its own types for concepts such as:

- Project configuration.
- Ontology source metadata.
- Loaded symbols.
- Validation messages.
- Validation reports.
- Semantic graph diffs.
- Change proposals for human review.

These types should reference or wrap standards-based graph data instead of inventing a parallel ontology model.

## Validation Approach

Validation should be deterministic and repeatable.

Early validation can start with basic checks, such as:

- Project configuration is valid.
- Referenced ontology files exist.
- Turtle/RDF files parse successfully.
- Required namespaces or project metadata are present.
- Proposed changes reference known symbols where required.
- Diff output can be generated without losing semantic identity.

Later validation can incorporate SHACL or other standards-based validation tools.

## Diff Approach

Semantic diffs should be designed for human review.

Phase 1 diffs can begin with graph-level additions and removals, but the representation should leave room for higher-level review concepts such as added classes, removed properties, changed labels, changed constraints, or changed relationships.

## CLI Approach

The Phase 1 CLI should be simple and backend-focused.

It should expose core engine capabilities without implying a full application platform. Candidate commands may include loading a project, validating a project, and diffing two graph states.

The CLI should remain a thin interface over reusable engine logic.
