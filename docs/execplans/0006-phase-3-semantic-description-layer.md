# ExecPlan: Phase 3 Semantic Description Layer

## Status

Draft

## Source Spec

- `docs/specs/0006-phase-3-semantic-description-layer.md`
- `docs/architecture/phase-3-scope.md`

## Objective

Build a reusable Kotlin-owned semantic description and annotation-editing layer for Entio. The implementation must organize explicit RDF facts into deterministic descriptors, expose descriptor and search operations through the CLI, and add semantic details and typed annotation editing to the VS Code workbench while reusing the existing proposal lifecycle.

## Current State

- `core-types` owns RDF terms, graph contracts, typed ontology edits, proposals, validation reports, diffs, and symbol details.
- `semantic-engine` loads projects, parses Turtle through Apache Jena, extracts symbols and relationships, resolves entities, and translates supported edits into graph changes.
- `validation-engine` performs deterministic project and proposal validation.
- `graph-diff` produces semantic graph and proposal diffs.
- `cli` exposes machine-readable proposal and workbench operations.
- `vscode-extension` renders project summaries, symbol details, typed editing forms, staged edits, deletion review, combined preview, approval, rejection, refresh, and source opening.
- `examples/simple-ontology` is a fixture source and must be copied before mutation in tests.

## Target State

- Kotlin exposes stable Entio-owned semantic descriptor, localized text, annotation, and semantic search contracts.
- The semantic engine assembles deterministic descriptors from explicit ontology statements, including structural facts and descriptive metadata.
- Typed definition, alternate-label, annotation-property, and general-annotation edits are translated into ordinary graph changes.
- Deterministic validation covers semantic metadata and integrates with existing proposal validation.
- CLI descriptor/search and semantic-edit operations are structured and backward compatible.
- The VS Code workbench displays semantic details, searches by preferred or alternate labels, and supports typed semantic edits through the existing staged and combined approval flow.
- Copied-fixture regression tests cover extraction, editing, rejection, atomic apply, reload, and rollback.

## Scope

### Affected Modules And Files

Expected areas, subject to existing ownership:

- `core-types/src/main/kotlin/com/entio/core/`
  - Semantic descriptor, localized text, annotation, search, and typed semantic-edit contracts.
- `core-types/src/test/kotlin/com/entio/core/`
  - Contract construction, equality, ordering, and RDF-term preservation tests.
- `semantic-engine/src/main/kotlin/com/entio/semantic/`
  - Annotation vocabulary, descriptor assembly, label policy, search, typed semantic-edit translation, and workflow integration.
- `semantic-engine/src/test/kotlin/com/entio/semantic/`
  - Extraction, ordering, edit translation, and copied-fixture service tests.
- `validation-engine/src/main/kotlin/com/entio/validation/`
  - Semantic metadata validation and proposal integration.
- `validation-engine/src/test/kotlin/com/entio/validation/`
  - Deterministic semantic validation tests.
- `graph-diff/src/main/kotlin/com/entio/diff/`
  - Only focused formatting or attribution support required for semantic edit diffs.
- `graph-diff/src/test/kotlin/com/entio/diff/`
  - Semantic edit diff tests.
- `cli/src/main/kotlin/com/entio/cli/`
  - Structured descriptor, search, and semantic-edit command boundaries.
- `cli/src/test/kotlin/com/entio/cli/`
  - JSON schema, backward compatibility, and CLI integration tests.
- `vscode-extension/src/`
  - Semantic detail rendering, search controls, typed semantic forms, staged-edit integration, and tests.
- `examples/` and temporary test directories
  - Copied fixtures only; committed examples must not be mutated by tests.
- `docs/decisions/` and `docs/phase-summaries/`
  - Completion artifacts or focused decisions only when explicitly required by the implementation workflow.

### Recommended Dependencies

- Reuse the existing Apache Jena dependency for RDF graph semantics and Turtle parsing.
- Reuse existing Gradle modules and the current TypeScript toolchain.
- Add no semantic-web, OWL, SHACL, server, database, persistence, or UI framework dependency.
- Add no JSON dependency unless the current CLI boundary cannot support the required structured request/response shape; if required, keep it limited to `cli` and use the project-approved pinned JSON library.
- Do not add a search engine, embedding library, LLM SDK, or external ontology client.

## Dependency Order And Multi-Agent Safety

Implement slices serially in the order below. Later slices consume earlier contracts and services.

1. Core semantic contracts and RDF-term-safe metadata values.
2. Annotation vocabulary and explicit annotation extraction.
3. Descriptor assembly for all required entity kinds and structural facts.
4. Deterministic preferred-label policy, alternate labels, definitions, and stable ordering.
5. Typed semantic-edit translation for annotation properties, definitions, alternate labels, and annotations.
6. Deterministic semantic validation and integration with existing proposal validation.
7. CLI descriptor and semantic search boundary.
8. CLI semantic-edit request/response and proposal lifecycle integration.
9. VS Code semantic details and label-aware search presentation.
10. VS Code semantic edit forms and staged workflow integration.
11. Copied-fixture end-to-end regression and Phase 3 documentation summary.

Slices 1 through 6 must be serial because they establish and consume shared semantic contracts and Kotlin behavior. Slices 7 and 8 must be serial because they share structured CLI contracts. Slices 9 and 10 must be serial because they share webview state and message models. Slice 11 must run last.

After Slice 4 establishes stable descriptor contracts, narrowly scoped test-only work may be parallelized if each agent owns separate test files. Do not parallelize edits to `core-types`, `shared`, build files, CLI JSON contracts, workbench message models, specs, ExecPlans, or `AGENTS.md`.

## Implementation Slices

### Slice 1: Core Semantic Contracts

#### Goal

Define immutable Entio-owned contracts for localized text, annotation values/statements, semantic descriptors, search results, and typed semantic edits.

#### Allowed files/modules

- `core-types/src/main/kotlin/com/entio/core/`
- Matching `core-types/src/test/kotlin/com/entio/core/`
- `docs/decisions/` completion artifact only

#### Forbidden actions/modules

- Do not change semantic-engine, validation-engine, graph-diff, cli, or vscode-extension.
- Do not expose Apache Jena types or mutable graph implementations.
- Do not implement descriptor extraction, search, validation, serialization, or source mutation.
- Do not add dependencies, persistence, or new modules.

#### Expected changes or output

- Immutable contracts for `LocalizedText`, `AnnotationValue`, `AnnotationStatement`, descriptor kinds, common descriptor fields, kind-specific descriptor fields, search results, and semantic edit requests.
- Explicit states for semantic edit kinds and match reasons.
- Stable equality and ordering rules that preserve RDF terms, language tags, and datatypes.

#### Tests

- Construct every contract and verify equality and defaults.
- Preserve IRI, blank-node, plain-literal, datatyped-literal, and language-tagged terms.
- Verify deterministic ordering and explicit state values.

#### Verification commands

```bash
./gradlew :core-types:test
./gradlew test
```

#### Stop conditions

- A contract requires a direct Jena type or a new dependency.
- The implementation begins reading graphs, selecting labels, or applying edits.
- Existing Phase 2 contracts require an incompatible change without an approved decision.

### Slice 2: Annotation Vocabulary And Explicit Metadata Extraction

#### Goal

Centralize recognized annotation-property IRIs and extract explicit annotation statements without applying semantic policy or inference.

#### Allowed files/modules

- `semantic-engine/src/main/kotlin/com/entio/semantic/`
- Matching `semantic-engine/src/test/kotlin/com/entio/semantic/`
- `core-types` only for an unavoidable contract correction documented in the completion artifact

#### Forbidden actions/modules

- Do not add CLI or VS Code behavior.
- Do not infer annotations, perform OWL reasoning, or fetch external vocabularies.
- Do not flatten RDF terms into strings or discard language tags and datatypes.
- Do not mutate sources or create a second annotation persistence path.

#### Expected changes or output

- Constants/value objects for `rdfs:label`, `rdfs:comment`, `skos:prefLabel`, `skos:altLabel`, `skos:definition`, and `dcterms:source`.
- Deterministic extraction of explicit annotation statements for a subject.
- Clear separation between recognized semantic metadata and general annotations.

#### Tests

- Extract each required vocabulary property.
- Preserve value term shape, language tags, and datatypes.
- Verify stable ordering and duplicate-preserving raw extraction.
- Ignore structural triples when collecting general annotations.

#### Verification commands

```bash
./gradlew :semantic-engine:test
./gradlew test
```

#### Stop conditions

- Extraction requires inference or a new RDF model.
- A vocabulary mapping silently changes existing symbol semantics.
- Annotation extraction starts writing or modifying ontology sources.

### Slice 3: Semantic Descriptor Assembly

#### Goal

Assemble descriptors for classes, object properties, datatype properties, annotation properties, and individuals from explicit graph facts.

#### Allowed files/modules

- `semantic-engine/src/main/kotlin/com/entio/semantic/`
- Matching `semantic-engine/src/test/kotlin/com/entio/semantic/`
- `core-types` only for approved contract corrections

#### Forbidden actions/modules

- Do not implement preferred-label ranking, fuzzy search, or UI rendering.
- Do not infer transitive superclasses, domains, ranges, or property behavior.
- Do not add full OWL reasoning or SHACL validation.
- Do not change CLI schemas or write source files.

#### Expected changes or output

- A reusable descriptor service that includes common fields and kind-specific explicit structural facts.
- Direct superclass/subclass, domain/range, asserted type, and direct assertion collection where available.
- Source ontology and local/imported metadata when available from the loaded project.
- Stable descriptor collection ordering.

#### Tests

- Build every required descriptor kind from fixture graphs.
- Collect explicit class hierarchy, domains, ranges, types, object assertions, and datatype assertions.
- Verify no inferred facts appear.
- Verify deterministic ordering and source attribution.

#### Verification commands

```bash
./gradlew :semantic-engine:test
./gradlew test
```

#### Stop conditions

- Correct output requires full OWL inference or external ontology data.
- Descriptor assembly exposes Jena types outside semantic-engine.
- The service mutates graph or source state.

### Slice 4: Deterministic Labels, Definitions, And Semantic Ordering

#### Goal

Implement preferred-label selection, alternate-label and definition collection, duplicate handling, language policy inputs, and deterministic ordering.

#### Allowed files/modules

- `semantic-engine/src/main/kotlin/com/entio/semantic/`
- `semantic-engine/src/test/kotlin/com/entio/semantic/`
- `validation-engine/src/main/kotlin/com/entio/validation/` only for focused metadata issue integration
- Matching validation tests
- `core-types` only for a narrowly necessary policy contract correction

#### Forbidden actions/modules

- Do not use fuzzy matching, embeddings, AI, or external lookup.
- Do not let TypeScript choose preferred labels or rank semantic results.
- Do not silently select among multiple preferred labels when producing validation output.
- Do not mutate source files or add persistence.

#### Expected changes or output

- Deterministic preferred-label policy using the approved priority.
- Alternate labels and definitions with exact duplicate suppression in descriptors.
- Structured representation of ambiguous or invalid preferred-label state.
- Stable ordering across repeated runs and language-tag preservation.

#### Tests

- Verify every policy priority and IRI-local-name fallback.
- Verify alternate-label and definition extraction, duplicates, language tags, and datatypes.
- Verify multiple preferred labels in one language produce a deterministic validation issue.
- Verify repeatability across repeated descriptor builds.

#### Verification commands

```bash
./gradlew :semantic-engine:test
./gradlew :validation-engine:test
./gradlew test
```

#### Stop conditions

- Label choice requires semantic similarity or AI judgment.
- Language behavior cannot be deterministic from explicit inputs.
- Descriptor policy requires changing unrelated Phase 2 behavior.

### Slice 5: Typed Semantic Edit Translation

#### Goal

Translate annotation-property, definition, alternate-label, and general-annotation edit requests into ordinary Entio graph changes.

#### Allowed files/modules

- `semantic-engine/src/main/kotlin/com/entio/semantic/`
- `semantic-engine/src/test/kotlin/com/entio/semantic/`
- `core-types` only for approved semantic-edit contract corrections

#### Forbidden actions/modules

- Do not add CLI flags or VS Code form logic.
- Do not build RDF triples in TypeScript or expose raw triple construction to users.
- Do not write source files, bypass proposal previews, or create a separate save path.
- Do not implement unsupported OWL or annotation reasoning.

#### Expected changes or output

- Typed edit translation for creating annotation properties and adding, replacing, or removing definitions, alternate labels, and annotations.
- Explicit removal-plus-addition representation for replacements.
- RDF-term-safe language and datatype handling.
- Reuse of existing graph-change and proposal contracts.

#### Tests

- Translate each edit kind into expected additions/removals.
- Verify replacement diffs contain both removal and addition.
- Preserve language tags, datatypes, IRI values where supported, and target source identity.
- Reject missing targets and missing annotation properties.

#### Verification commands

```bash
./gradlew :semantic-engine:test
./gradlew test
```

#### Stop conditions

- Translation requires direct source persistence or a UI-specific contract.
- An edit cannot be expressed through existing graph changes without a broad redesign.
- The implementation silently changes an annotation value without a diff.

### Slice 6: Semantic Validation And Proposal Lifecycle Integration

#### Goal

Add deterministic validation for semantic metadata and route semantic edits through the existing preview, diff, round-trip, staging, approval, apply, reload, rejection, and rollback lifecycle.

#### Allowed files/modules

- `validation-engine/src/main/kotlin/com/entio/validation/`
- `semantic-engine/src/main/kotlin/com/entio/semantic/` for focused proposal-service integration
- `graph-diff/src/main/kotlin/com/entio/diff/` only for semantic diff formatting support
- Matching tests in these modules

#### Forbidden actions/modules

- Do not implement CLI or VS Code presentation.
- Do not add full OWL reasoning, SHACL, persistence, or partial apply behavior.
- Do not bypass stale checks, Turtle round-trip verification, atomic apply, or rollback.
- Do not change source files during preview or rejection.

#### Expected changes or output

- Validation issues for missing values/properties, invalid language tags/datatypes, duplicates, kind mismatches, missing removals, ambiguity, stale baselines, staged conflicts, and round-trip non-equivalence.
- Semantic edits participate in existing single and combined proposal results.
- Rejection preserves staged state and source files; apply reloads and verifies.

#### Tests

- Exercise each semantic validation issue.
- Verify invalid previews do not stage or mutate source files.
- Verify combined semantic diffs, approval, rejection, stale blocking, atomic apply, reload, and rollback.

#### Verification commands

```bash
./gradlew :validation-engine:test
./gradlew :semantic-engine:test
./gradlew :graph-diff:test
./gradlew test
```

#### Stop conditions

- Validation requires AI judgment or full reasoning.
- Semantic edits bypass the established proposal lifecycle.
- A failure can leave a partially applied source change.

### Slice 7: CLI Descriptor And Semantic Search Boundary

#### Goal

Expose Kotlin-owned descriptors and deterministic label-aware search through structured CLI commands.

#### Allowed files/modules

- `cli/src/main/kotlin/com/entio/cli/`
- `cli/src/test/kotlin/com/entio/cli/`
- Focused semantic-engine test corrections only if the boundary reveals a contract issue

#### Forbidden actions/modules

- Do not implement descriptor assembly, label policy, ranking, or RDF parsing in CLI.
- Do not break existing command options or response fields.
- Do not add a server, search index, fuzzy matching, embeddings, or external lookup.
- Do not add persistence for descriptors or search sessions.

#### Expected changes or output

- Machine-readable descriptor retrieval for selected entities and project-level use where approved.
- Search by preferred label, alternate label, full IRI, kind, and source ontology.
- Stable match reasons, ranking, ambiguity, and structured errors.
- Backward-compatible existing CLI responses.

#### Tests

- Descriptor JSON for every entity kind.
- Search by preferred label, alternate label, IRI, kind, and source.
- Ambiguous and missing results with match reasons and stable ordering.
- Existing Phase 2 CLI regression tests.

#### Verification commands

```bash
./gradlew :cli:test
./gradlew test
```

#### Stop conditions

- CLI begins duplicating Kotlin semantic logic.
- Structured output requires incompatible changes to existing commands.
- Search requires fuzzy or external retrieval behavior.

### Slice 8: CLI Semantic Edit And Proposal Boundary

#### Goal

Expose typed semantic edits and their existing proposal lifecycle through machine-readable CLI requests and responses.

#### Allowed files/modules

- `cli/src/main/kotlin/com/entio/cli/`
- `cli/src/test/kotlin/com/entio/cli/`
- `cli/build.gradle.kts` only if a narrowly scoped existing JSON boundary dependency is insufficient
- Matching integration tests

#### Forbidden actions/modules

- Do not translate semantic edits into RDF in CLI.
- Do not add a second apply, staging, persistence, or rollback mechanism.
- Do not break single-edit or existing Phase 2 command compatibility.
- Do not add UI dependencies or source-file writes in CLI parsing code.

#### Expected changes or output

- Structured requests for annotation-property creation, definition, alternate-label, and annotation edits.
- Preview, validate, diff, apply, reject, stale, and rollback responses using existing proposal contracts.
- Stable structured validation and failure attribution.

#### Tests

- Parse valid and invalid semantic edit requests.
- Verify backward-compatible single-edit behavior.
- Verify preview, validation, diff, rejection, apply, reload, stale, and rollback results.
- Verify malformed requests produce structured errors.

#### Verification commands

```bash
./gradlew :cli:test
./gradlew test
```

#### Stop conditions

- CLI code owns semantic policy or source persistence.
- The request schema requires long-term storage or a new service.
- Existing response compatibility cannot be preserved without approved scope change.

### Slice 9: VS Code Semantic Details And Search Presentation

#### Goal

Present Kotlin-owned descriptors and search results in the workbench, including preferred labels, alternate labels, definitions, annotations, structural details, source, kind, and full IRI.

#### Allowed files/modules

- `vscode-extension/src/`
- Matching `vscode-extension/src/test/`
- CLI tests only for a discovered boundary correction

#### Forbidden actions/modules

- Do not parse RDF, choose preferred labels, rank results, or build annotation values in TypeScript.
- Do not write ontology files directly.
- Do not add a UI framework, state-management dependency, or separate search service.
- Do not bypass the Kotlin CLI boundary.

#### Expected changes or output

- Semantic details section for selected entities.
- Label-aware search controls and match-reason display.
- Readable labels in ordinary relationship/dependency lists with full IRI available in technical details.
- Empty, loading, ambiguous, and error states.

#### Tests

- Normalize descriptor and search responses.
- Render each descriptor kind and semantic collection.
- Display alternate-label and match-reason results.
- Verify technical full-IRI display and no local semantic interpretation.

#### Verification commands

```bash
cd vscode-extension && npm test
```

#### Stop conditions

- The extension needs independent RDF parsing or label policy.
- A UI operation mutates Turtle directly.
- A new UI framework or persistent session store is proposed.

### Slice 10: VS Code Semantic Edit Forms And Staged Workflow

#### Goal

Add forms and staged-session behavior for annotation properties, definitions, alternate labels, and general annotations through the existing typed preview workflow.

#### Allowed files/modules

- `vscode-extension/src/`
- Matching `vscode-extension/src/test/`
- `cli` only for focused request/response compatibility corrections

#### Forbidden actions/modules

- Do not construct RDF triples or choose annotation predicates outside Kotlin responses.
- Do not create a separate save path or bypass preview, validation, combined review, approval, rejection, or rollback.
- Do not persist staged changes between extension sessions.
- Do not add unsupported semantic or OWL editing controls.

#### Expected changes or output

- Forms for creating annotation properties and adding/editing/removing definitions, alternate labels, and annotations.
- Language-tag and datatype controls where supported.
- Staged entries with edit, remove, cancel, clear-after-preview, combined preview, and rejection restoration behavior.
- Full IRI in technical fields while using labels in user-facing lists.

#### Tests

- Validate form normalization and typed request generation.
- Verify successful preview stages and clears forms.
- Verify failed preview retains inputs.
- Verify staged edit restoration, replacement, remove, combined preview, approval, rejection, refresh, and source opening.
- Verify annotation values remain RDF-term-safe at the CLI boundary.

#### Verification commands

```bash
cd vscode-extension && npm test
```

#### Stop conditions

- The form needs to implement semantic policy locally.
- A staged edit cannot be represented by the existing combined request lifecycle.
- UI code writes or rewrites Turtle files.

### Slice 11: Phase 3 End-To-End Regression And Documentation Summary

#### Goal

Verify the complete semantic description and annotation workflow on copied fixtures and document the actual Phase 3 result.

#### Allowed files/modules

- `cli/src/test/kotlin/com/entio/cli/`
- `semantic-engine/src/test/kotlin/com/entio/semantic/`
- `validation-engine/src/test/kotlin/com/entio/validation/`
- `vscode-extension/src/test/`
- Temporary copied fixtures under test-controlled directories
- `docs/phase-summaries/phase-3-summary.md`
- `docs/decisions/` completion artifact only

#### Forbidden actions/modules

- Do not change committed examples as part of tests.
- Do not add new product behavior beyond the approved slices.
- Do not weaken assertions, skip failing verification, or add unrelated documentation changes.
- Do not create persistence, AI, Schema RAG, or external ontology integration.

#### Expected changes or output

- End-to-end coverage for descriptors, search, semantic edits, combined diff, rejection, approval, reload, atomic apply, and rollback.
- A factual Phase 3 summary based on the implemented repository, including deviations and limitations.

#### Tests

- Copy `examples/simple-ontology` before mutation.
- Verify language-tagged and datatyped values survive round trips.
- Verify multiple staged semantic edits and deterministic search.
- Verify rejection leaves source and staged state available.
- Verify apply and failed post-save verification behavior.

#### Verification commands

```bash
./gradlew test
./gradlew build
./gradlew check
cd vscode-extension && npm test
```

#### Stop conditions

- Full Phase 3 behavior cannot be verified without external services or unapproved infrastructure.
- A regression requires altering earlier approved semantics outside the current plan.
- Committed fixtures would need mutation to make the test pass.

## Test Plan

- Keep contract tests in `core-types`.
- Keep graph and descriptor behavior tests in `semantic-engine`.
- Keep deterministic semantic issue tests in `validation-engine`.
- Keep only focused semantic diff formatting tests in `graph-diff`.
- Keep JSON boundary and backward compatibility tests in `cli`.
- Keep rendering, form-state, and message-boundary tests in `vscode-extension`.
- Use copied fixtures for every mutating integration test.
- Add deterministic ordering assertions wherever a collection crosses a module boundary.
- Verify both valid and failure paths, especially stale baselines, duplicate metadata, ambiguity, rejection, and rollback.

## Verification Commands

Per-slice verification is specified above. Full Phase 3 verification is:

```bash
./gradlew test
./gradlew build
./gradlew check
cd vscode-extension && npm test
```

Manual checks, using a copied example project, should confirm:

- Descriptor details and full IRI display.
- Preferred and alternate label search with match reasons.
- Definition, alternate-label, annotation-property, and annotation forms.
- Staged edit correction and rejection restoration.
- Combined semantic diff, approval, reload, and source opening.
- No source mutation before approval and automatic recovery after failed verification.

## Rollback Notes

- Each slice must be committed independently and can be reverted without rewriting history.
- Revert later CLI or VS Code slices before reverting the semantic contracts they consume.
- If a contract correction is required, stop and record the compatibility decision before continuing dependent slices.
- Runtime source changes remain protected by the existing proposal baseline, rejection, atomic apply, reload, and rollback mechanisms.
- Do not delete or rewrite committed example fixtures to undo a test failure; use temporary copies.

## Risks And Assumptions

- Existing RDF graphs may contain multiple or conflicting labels; descriptors must remain inspectable while validation reports the conflict.
- The preferred UI language source is not yet selected; the first implementation should use an explicit input with a deterministic default rather than infer from the host environment.
- Some annotation values may be blank nodes or IRIs; the contract must preserve them even if the first UI form supports text values only.
- Imported/local status may not be available in every current project configuration and should be represented as unknown rather than guessed.
- The current CLI JSON boundary may need a narrowly scoped dependency or existing parser extension, but no broad infrastructure is justified.

## Boundary Check

- This plan fits the Phase 3 semantic description scope and does not activate Schema RAG or AI behavior.
- It keeps semantic truth in Kotlin and the CLI/VS Code layers thin.
- It uses Apache Jena for RDF/Turtle behavior and does not create a custom semantic-web framework.
- It avoids new persistence, server, database, Git, reasoning, and external retrieval infrastructure.
- It preserves existing preview, diff, validation, approval, staging, atomic apply, reload, and rollback contracts.

## Definition Of Done

- All approved slices are implemented in dependency order with focused tests.
- Semantic descriptors exist for all required entity kinds and contain explicit structural and descriptive facts.
- Preferred-label selection, alternate-label extraction, definitions, annotations, language tags, datatypes, and ordering are deterministic.
- Typed semantic edits use the existing graph-change and proposal lifecycle.
- CLI descriptor, search, and semantic-edit boundaries are machine-readable and backward compatible.
- VS Code displays semantic details and supports approved semantic edits without duplicating RDF logic.
- Copied-fixture end-to-end tests pass without modifying committed examples.
- `./gradlew test`, `./gradlew build`, `./gradlew check`, and `cd vscode-extension && npm test` pass.
- A factual `docs/phase-summaries/phase-3-summary.md` exists and records actual implementation, limitations, and deviations.
- No Phase 3 non-goal or later-phase infrastructure has been introduced.
