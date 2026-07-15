# ExecPlan: Phase 4 OWL Reasoning And SHACL Constraints

## Status

Draft

## Source Spec

- `docs/specs/0007-phase-4-owl-reasoning-shacl-revised.md`
- `docs/architecture/phase-4-scope.md`

## Objective

Add a Kotlin-owned OWL reasoning and SHACL validation layer for small local Entio projects. The implementation must preserve the existing asserted-versus-inferred distinction, resolve approved local imports, expose deterministic reasoning and SHACL results, and route all proposed ontology, data, and shape changes through the existing preview, semantic-diff, approval, atomic-apply, reload, and rollback workflow.

Phase 4 is intentionally bounded. OWL API and HermiT are the OWL boundary, Apache Jena SHACL is the SHACL boundary, and third-party library types must remain inside `semantic-engine` or a narrowly owned worker. `core-types`, the CLI, and the VS Code extension should expose Entio-owned contracts rather than library objects.

## Current State

- `core-types` owns RDF terms, graph contracts, typed ontology edits, proposals, validation reports, diffs, semantic descriptors, and search contracts.
- `semantic-engine` loads projects, resolves configured ontology sources, parses Turtle through Apache Jena, extracts explicit symbols and semantic descriptors, translates supported edits, and supports proposal workflows.
- `validation-engine` performs deterministic project, proposal, and semantic metadata validation.
- `graph-diff` produces semantic graph and proposal diffs.
- `cli` exposes project, symbol, diff, proposal, and semantic-description operations through a thin command boundary.
- `vscode-extension` presents the controlled editing workbench and delegates semantic work to the Kotlin CLI.
- `examples/simple-ontology` is a committed fixture and must be copied before mutating tests.
- Phase 3 is complete. Phase 4 implementation has not started.

## Target State

- Loading a project produces an asserted graph view plus a deterministic reasoning result and, when configured, a SHACL validation result.
- Valid individual and combined proposal previews run the same reasoning and SHACL pipeline without mutating source files.
- OWL reasoning uses a pinned OWL API/HermiT compatibility set and reports asserted facts, inferred facts, consistency, unsatisfiable classes, imports, explanations, feature support, status, fingerprints, and safe failures.
- SHACL validation uses Apache Jena SHACL against explicitly configured data and shapes graphs, with asserted-only validation as the default and an explicit asserted-plus-inferred mode.
- Typed SHACL edits are previewed, validated, diffed, approved or rejected, and applied atomically across affected source files.
- The CLI and VS Code workbench present Entio-owned reasoning, SHACL, and proposal-impact results without duplicating semantic logic.
- Existing source-safety guarantees remain intact: preview does not write, approval is required, stale proposals are blocked, post-save state is verified, and failures roll back all affected sources.

## Scope

### Affected Modules And Files

Expected areas, subject to existing ownership:

- `core-types/src/main/kotlin/com/entio/core/`
  - Reasoning status, fingerprints, asserted/inferred facts, import reports, feature reports, explanations, SHACL shape/constraint contracts, validation results, proposal impact, and multi-source apply contracts.
- `core-types/src/test/kotlin/com/entio/core/`
  - Contract construction, equality, ordering, RDF-term preservation, and state-transition tests.
- `semantic-engine/src/main/kotlin/com/entio/semantic/`
  - Import resolution, OWL API/HermiT adapter, reasoning worker, reasoning lifecycle, explanations, feature reporting, SHACL source-role loading, Jena SHACL adapter, shape extraction, typed SHACL edit translation, and proposal integration.
- `semantic-engine/src/test/kotlin/com/entio/semantic/`
  - Reasoning, import, worker, explanation, SHACL, graph-role, round-trip, and proposal integration tests.
- `validation-engine/src/main/kotlin/com/entio/validation/`
  - Deterministic reasoning/SHACL result validation, baseline-aware proposal blocking, and configuration checks.
- `validation-engine/src/test/kotlin/com/entio/validation/`
  - Failure, baseline, status, severity, and deterministic ordering tests.
- `graph-diff/src/main/kotlin/com/entio/diff/`
  - Explicit semantic diff, inferred-impact, and SHACL-impact formatting only.
- `graph-diff/src/test/kotlin/com/entio/diff/`
  - Impact attribution, ordering, blank-node-safe, and normalized diff tests.
- `cli/src/main/kotlin/com/entio/cli/`
  - Thin machine-readable reasoning, SHACL, and proposal-impact command boundaries.
- `cli/src/test/kotlin/com/entio/cli/`
  - Request/response, status, error, compatibility, and end-to-end CLI tests.
- `vscode-extension/src/`
  - Reasoning views, SHACL authoring/result views, status controls, and proposal-impact presentation.
- `vscode-extension/src/test/`
  - Message normalization, rendering, form state, failure-state, and workflow tests.
- `examples/` and temporary test directories
  - Copied fixtures and small local import/SHACL fixtures only.
- `build.gradle.kts`, module `build.gradle.kts` files, and Gradle wrapper/version configuration
  - Only the pinned library and worker packaging changes required by the approved slices.
- `docs/decisions/` and `docs/phase-summaries/`
  - Focused ADRs or the final Phase 4 summary only when required by the implementation workflow.

### Recommended Dependencies

- Keep the existing Kotlin/JVM Gradle setup and Apache Jena `5.3.0` dependency currently used by `semantic-engine`.
- Add a pinned compatible OWL API distribution dependency in `semantic-engine` or the reasoning worker. The exact OWL API version must be locked together with HermiT before implementation is merged; no dynamic or floating version is allowed.
- Add a pinned HermiT OWL API reasoner dependency, using the Maven artifact selected by the compatibility spike. The selected version must be recorded in Gradle and in the slice completion artifact.
- Reuse Apache Jena core/ARQ and add the Jena SHACL artifact matching the existing Jena version. Keep Jena types behind `semantic-engine`.
- Use the JVM standard library `ProcessBuilder`, streams, and temporary-file APIs for the reasoning worker. Do not add a server, RPC framework, coroutine runtime, database, or plugin system.
- Reuse the current CLI JSON boundary. Add a JSON dependency only if the existing implementation cannot represent the approved result contracts; if required, pin it and keep its use within the CLI boundary.
- Do not add search engines, embeddings, LLM SDKs, external ontology clients, persistence, or graph databases.

## Dependency Order And Multi-Agent Safety

Implement slices serially in the order below. Later slices consume contracts and services established by earlier slices.

1. Core reasoning, SHACL, and impact contracts.
2. Library compatibility and reasoning-worker boundary.
3. Project graph roles and local import closure.
4. OWL ontology adapter, profile report, and supported-feature detection.
5. Reasoning execution, asserted/inferred results, consistency, and unsatisfiable classes.
6. Reasoning lifecycle, fingerprints, cache reuse, timeout, cancellation, and safe failure.
7. Reasoning explanations and selected-result inspection.
8. SHACL shapes graph loading and typed shape/constraint authoring.
9. SHACL validation, result normalization, and asserted/inferred validation modes.
10. Proposal impact, baseline-aware approval, multi-source atomic apply, and rollback integration.
11. CLI reasoning, SHACL, and proposal-impact boundary.
12. VS Code reasoning and SHACL workbench presentation.
13. End-to-end Phase 4 regression and documentation summary.

Slices 1 through 7 must be serial because they establish the reasoning contracts, dependency boundary, inputs, execution lifecycle, and explanations. Slices 8 and 9 must be serial because validation consumes shape-loading and authoring contracts. Slice 10 must wait for both reasoning and SHACL results. Slices 11 and 12 must be serial after the Kotlin boundary is stable because they share request/response and workbench state models. Slice 13 must run last.

After Slice 1, narrowly scoped test-only work may be parallelized only when each agent owns separate test files and does not alter shared contracts, build files, CLI schemas, workbench message models, specs, ExecPlans, or `AGENTS.md`. No implementation slices should be parallelized for Phase 4.

Do not parallelize edits to:

- `core-types`
- `shared`
- root or module build files
- dependency/version locks
- worker protocol contracts
- CLI JSON contracts
- VS Code message contracts
- specs, ExecPlans, ADRs, or summaries
- `AGENTS.md`

## Implementation Slices

### Slice 1: Core OWL, SHACL, And Impact Contracts

#### Goal

Define immutable Entio-owned contracts for reasoning runs, asserted and inferred facts, imports, explanations, feature reports, SHACL roles/shapes/constraints/results, validation modes, proposal impact, and multi-source apply outcomes.

#### Allowed files/modules

- `core-types/src/main/kotlin/com/entio/core/`
- Matching `core-types/src/test/kotlin/com/entio/core/`
- `docs/decisions/` completion artifact only

#### Forbidden actions/modules

- Do not change `semantic-engine`, `validation-engine`, `graph-diff`, `cli`, or `vscode-extension`.
- Do not expose OWL API, HermiT, or Jena SHACL types.
- Do not implement reasoning, SHACL validation, import resolution, serialization, or source mutation.
- Do not add dependencies, persistence, or new modules.

#### Expected changes or output

- Immutable contracts for run status, consistency, inferred fact origin, fingerprints, import findings, OWL feature support, explanations, SHACL source roles, stable shape identity, targets, direct property paths, supported constraints, severities, validation results, validation mode, baseline comparison, proposal impact, and atomic multi-source results.
- Explicit status and result states rather than loose strings.
- Deterministic equality, ordering, and RDF-term preservation.

#### Tests

- Construct every contract and verify equality, defaults, ordering, and absent-value handling.
- Preserve IRI, blank-node, plain-literal, datatyped-literal, and language-tagged values.
- Verify asserted/inferred origin and proposal-impact state cannot be confused.

#### Verification commands

```bash
./gradlew :core-types:test
./gradlew test
```

#### Stop conditions

- A public contract requires a direct third-party semantic-web type.
- A contract requires implementing reasoning or SHACL behavior.
- Existing Phase 3 contracts require an incompatible change without an approved decision.

### Slice 2: Library Compatibility And Reasoning Worker Boundary

#### Goal

Pin and prove the OWL API/HermiT compatibility set, align Jena SHACL dependencies, and define a narrow worker protocol that can run reasoning outside the main process.

#### Allowed files/modules

- Root and module `build.gradle.kts` files.
- Gradle version or dependency configuration files if already present.
- `semantic-engine/src/main/kotlin/com/entio/semantic/` for worker protocol and process boundary types.
- Matching `semantic-engine/src/test/kotlin/com/entio/semantic/`.
- Gradle wrapper files only if required by the pinned toolchain.

#### Forbidden actions/modules

- Do not implement project loading, import resolution, OWL classification, or SHACL validation.
- Do not add server, RPC, coroutine, database, persistence, or plugin infrastructure.
- Do not let third-party library types cross `semantic-engine` public contracts.
- Do not use unpinned, latest, dynamic, or version-range dependencies.
- Do not launch a worker from the CLI or VS Code.

#### Expected changes or output

- A documented, pinned, compatible OWL API/HermiT set and Jena SHACL version aligned with Jena `5.3.0`.
- A versioned worker request/response model with graph input identity, configuration, status, normalized output, and structured startup/crash/timeout/malformed-output errors.
- A packaging approach that can launch the worker from the semantic engine without requiring a server.

#### Tests

- Load the selected OWL API/HermiT classes in a minimal compatibility test.
- Load Jena SHACL classes in a minimal compatibility test.
- Verify worker request/response serialization and protocol version rejection.
- Verify malformed, empty, and unexpected worker output is reported safely.

#### Verification commands

```bash
./gradlew :semantic-engine:test
./gradlew dependencies
./gradlew test
```

#### Stop conditions

- The selected libraries cannot coexist on the current JVM/toolchain.
- A compatible dependency set cannot be pinned and tested.
- Worker cancellation requires unsafe in-process thread interruption or new infrastructure outside the approved boundary.

### Slice 3: Project Graph Roles And Local Import Closure

#### Goal

Load explicit ontology/data/shapes source roles and resolve the complete locally available `owl:imports` closure without silent network access.

#### Allowed files/modules

- `core-types` only for a narrowly necessary source-role or import contract correction.
- `semantic-engine/src/main/kotlin/com/entio/semantic/`.
- Matching semantic-engine tests.
- `examples/` only for small local import and role fixtures.

#### Forbidden actions/modules

- Do not run OWL reasoning or SHACL validation yet.
- Do not download imports from the internet.
- Do not treat imported ontologies as SHACL shapes unless explicitly configured.
- Do not mutate source files or alter existing project-loader behavior outside role/import support.
- Do not add external ontology catalogs or persistence.

#### Expected changes or output

- Explicit source-role loading for ontology, data, and shapes, including sources with multiple configured roles.
- Local, bundled, or explicitly configured import resolution with one-load-per-source behavior.
- Cycle, missing, unresolved, unsupported, and incomplete-import findings with stable ordering.
- Import and graph fingerprints suitable for later cache invalidation.

#### Tests

- Resolve nested local imports and preserve source attribution.
- Detect cycles without repeated loading.
- Report missing imports and enforce the configured incomplete-import policy.
- Verify old source defaults remain ontology plus data and not shapes.

#### Verification commands

```bash
./gradlew :semantic-engine:test
./gradlew test
```

#### Stop conditions

- Import resolution requires network access or a broad ontology client.
- Source roles cannot be distinguished at the core boundary.
- A missing import is silently treated as complete.

### Slice 4: OWL Adapter, Profile Report, And Feature Detection

#### Goal

Adapt resolved RDF input to OWL API/HermiT, detect OWL 2 DL/profile features, and report supported, unsupported, and partial coverage before execution.

#### Allowed files/modules

- `semantic-engine/src/main/kotlin/com/entio/semantic/`.
- Matching semantic-engine tests.
- `core-types` only for a narrowly necessary feature-report correction.

#### Forbidden actions/modules

- Do not expose OWL API or HermiT types outside semantic-engine.
- Do not materialize inferred triples into Turtle.
- Do not claim full OWL 2 DL or full reasoner coverage.
- Do not implement explanations, SHACL, CLI, or UI behavior.
- Do not silently ignore constructs that may affect completeness.

#### Expected changes or output

- OWL ontology construction over the resolved import closure.
- Reasoner configuration owned by semantic-engine.
- Stable feature/profile report with supported, unsupported, ignored, and partial constructs.
- Explicit completeness impact attached to relevant findings.

#### Tests

- Build a small ontology with class hierarchy, equivalent classes, inverse properties, and transitive properties.
- Detect supported and unsupported constructs deterministically.
- Verify source RDF terms remain represented as asserted data separate from adapter state.

#### Verification commands

```bash
./gradlew :semantic-engine:test
./gradlew test
```

#### Stop conditions

- The adapter requires a custom OWL/RDF framework.
- Unsupported features are hidden or reported as complete.
- Third-party reasoner objects leak into Entio contracts.

### Slice 5: Reasoning Execution And Asserted/Inferred Results

#### Goal

Run the selected reasoner and return deterministic class hierarchy, individual type, supported property, consistency, and unsatisfiable-class results while preserving asserted/inferred separation.

#### Allowed files/modules

- `semantic-engine/src/main/kotlin/com/entio/semantic/`.
- Matching semantic-engine tests.
- `core-types` only for contract corrections required by observed result shape.

#### Forbidden actions/modules

- Do not add UI or CLI behavior.
- Do not materialize inferred facts into source files.
- Do not promise unsupported OWL consequences.
- Do not implement caching, cancellation, explanations, SHACL, or proposal apply in this slice.
- Do not let asserted and inferred facts share an untyped collection.

#### Expected changes or output

- Reasoning service for loaded and in-memory preview graphs.
- Deterministic normalized results for guaranteed capabilities and reliably returned equivalent/inverse/transitive consequences.
- Consistency and unsatisfiable-class status.
- Reasoning version metadata, graph/import fingerprints, warnings, errors, and completeness.

#### Tests

- Verify transitive class hierarchy and inferred individual types.
- Verify consistency and unsatisfiable classes.
- Verify supported equivalent-class, inverse-property, and transitive-property cases.
- Verify no inferred fact is written or reported as asserted.

#### Verification commands

```bash
./gradlew :semantic-engine:test
./gradlew test
```

#### Stop conditions

- Reasoning mutates source or preview graph state.
- A result cannot distinguish asserted from inferred origin.
- A reasoner failure is represented as a successful complete result.

### Slice 6: Reasoning Lifecycle, Fingerprints, Cancellation, And Safe Failure

#### Goal

Run reasoning in the worker process with status transitions, timeout, cancellation, reuse for unchanged inputs, and stale-result protection.

#### Allowed files/modules

- `semantic-engine/src/main/kotlin/com/entio/semantic/`.
- Matching semantic-engine tests.
- `core-types` only for status or fingerprint corrections required by the lifecycle.
- Focused Gradle packaging changes only if required by the worker boundary.

#### Forbidden actions/modules

- Do not add a server, RPC system, coroutine runtime, or durable result store.
- Do not freeze the main process while waiting indefinitely for HermiT.
- Do not accept late results after cancellation, timeout, or a newer run.
- Do not change source files on worker failure.
- Do not trigger reasoning on every UI keystroke.

#### Expected changes or output

- Worker launch and termination through a narrow process API.
- Running, completed, failed, cancelled, timed-out, incomplete, and unavailable states.
- Stable graph/import/configuration fingerprints and result reuse.
- Safe failure with actionable diagnostics and a usable project state.

#### Tests

- Verify completed, failed, malformed-output, timed-out, cancelled, and crashed worker paths.
- Verify unchanged fingerprints reuse results and changed inputs invalidate them.
- Verify late results are discarded and source files remain unchanged.

#### Verification commands

```bash
./gradlew :semantic-engine:test
./gradlew test
```

#### Stop conditions

- Cancellation cannot terminate the worker safely.
- A stale result can replace a newer result.
- Worker packaging or launch requires unapproved infrastructure.

### Slice 7: Reasoning Explanations And Selected-Result Inspection

#### Goal

Provide deterministic explanations for selected inferences, inconsistencies, and unsatisfiable classes without promising complete minimal justifications.

#### Allowed files/modules

- `semantic-engine/src/main/kotlin/com/entio/semantic/`.
- Matching semantic-engine tests.
- `core-types` only for explanation contract corrections.

#### Forbidden actions/modules

- Do not add an LLM explanation service.
- Do not claim minimal or complete explanations for all OWL 2 DL cases.
- Do not put explanation policy in the CLI or VS Code extension.
- Do not change reasoning semantics or materialize facts.

#### Expected changes or output

- Path-based explanations for supported subclass and type propagation.
- Deterministic inverse/transitive explanations for supported cases.
- Relevant asserted facts and source attribution for inconsistency and unsatisfiable-class findings.
- Explicit caveats when reasoner-backed minimal justification is unavailable.

#### Tests

- Verify stable explanation ordering and source attribution.
- Verify explanations for selected superclass, individual type, inverse, and transitive results.
- Verify inconsistency and unsatisfiable-class fallback wording.

#### Verification commands

```bash
./gradlew :semantic-engine:test
./gradlew test
```

#### Stop conditions

- Explanation requires nondeterministic text generation.
- Explanation code changes the underlying reasoning result.
- The implementation claims unsupported justification completeness.

### Slice 8: SHACL Graph Roles And Typed Shape Authoring

#### Goal

Load Jena SHACL shapes separately from data, and provide Entio-owned typed contracts and translation for supported node shapes, property shapes, targets, paths, and constraints.

#### Allowed files/modules

- `semantic-engine/src/main/kotlin/com/entio/semantic/`.
- Matching semantic-engine tests.
- `core-types` only for shape or constraint contract corrections.
- `examples/` only for copied SHACL fixtures.

#### Forbidden actions/modules

- Do not validate shapes yet or add SHACL-SPARQL.
- Do not support complex property paths in this slice.
- Do not require users to author raw Turtle in the VS Code layer.
- Do not treat imported ontology sources as shapes without explicit role configuration.
- Do not expose Jena SHACL types across module boundaries.

#### Expected changes or output

- Explicit data/shapes graph loading and fingerprints.
- Stable Entio-generated shape IRIs for editable shapes.
- Typed authoring and translation for node shapes, property shapes, targetClass, targetNode, targetSubjectsOf, targetObjectsOf, direct property paths, count/datatype/class/in-list/numeric/string/closed-shape constraints, severity, and message.
- Add/edit/delete shape and constraint changes represented through existing graph changes.

#### Tests

- Load a source configured as ontology/data, shapes, and both.
- Verify stable shape identity across serialization and reload.
- Translate each supported target and constraint without blank-node identity loss.
- Reject complex paths and unsupported constraint forms explicitly.

#### Verification commands

```bash
./gradlew :semantic-engine:test
./gradlew :core-types:test
./gradlew test
```

#### Stop conditions

- Shape authoring requires direct Jena objects at a public boundary.
- A shape edit cannot round-trip with stable identity.
- The implementation expands into full SHACL or complex path support.

### Slice 9: SHACL Validation And Result Normalization

#### Goal

Run Apache Jena SHACL against the configured data and shapes graphs and expose deterministic, source-aware validation results in asserted-only and explicit asserted-plus-inferred modes.

#### Allowed files/modules

- `semantic-engine/src/main/kotlin/com/entio/semantic/`.
- `validation-engine/src/main/kotlin/com/entio/validation/`.
- Matching tests in both modules.
- `core-types` only for validation-result corrections.

#### Forbidden actions/modules

- Do not implement custom SHACL validation semantics.
- Do not add SHACL-SPARQL, complex paths, auto-repair, or LLM-generated messages.
- Do not silently switch validation mode from asserted-only to asserted-plus-inferred.
- Do not mutate source files during validation.

#### Expected changes or output

- Jena SHACL adapter with explicit data/shapes graph inputs.
- Normalized results containing severity, message, focus node, path, shape, constraint, value, source, mode, and graph fingerprints.
- Deterministic ordering and stable result identity.
- Validation status and errors for invalid target/path/count/datatype/class/list/numeric/regex/severity/message definitions.

#### Tests

- Cover every approved constraint category and severity.
- Verify asserted-only results and explicit asserted-plus-inferred results differ only when inferred facts affect validation.
- Verify source and shapes fingerprints, deterministic ordering, and malformed-shape failures.

#### Verification commands

```bash
./gradlew :semantic-engine:test
./gradlew :validation-engine:test
./gradlew test
```

#### Stop conditions

- Validation semantics are reimplemented instead of delegated to Jena.
- Results cannot identify data graph, shapes graph, or validation mode.
- A validation failure can write or partially alter a source.

### Slice 10: Proposal Impact, Baseline Approval, Atomic Apply, And Rollback

#### Goal

Integrate reasoning and SHACL into individual and combined proposal previews, baseline-aware blocking, multi-source atomic application, reload, verification, rejection, and rollback.

#### Allowed files/modules

- `semantic-engine/src/main/kotlin/com/entio/semantic/`.
- `validation-engine/src/main/kotlin/com/entio/validation/`.
- `graph-diff/src/main/kotlin/com/entio/diff/`.
- Matching tests in these modules.
- `core-types` only for approved impact/apply contract corrections.

#### Forbidden actions/modules

- Do not implement CLI or VS Code presentation.
- Do not apply inferred triples automatically.
- Do not block on unchanged baseline violations or warnings/info unless the approved policy says otherwise.
- Do not partially apply multi-source proposals.
- Do not bypass stale-baseline, graph-isomorphism, round-trip, post-save, or rollback checks.
- Do not add durable version history or Git operations.

#### Expected changes or output

- Proposal impact report with separate explicit semantic diff, reasoning impact, and SHACL validation impact.
- Baseline comparison: new/worsened violations and inconsistency/unsatisfiability block; unchanged findings remain visible; resolved findings are reported as improvements; incomplete results block according to policy.
- Combined preview pipeline: build graph, reason, validate, diff, present impact.
- Multi-source atomic apply with temporary files, graph-isomorphism verification, reload, rerun reasoning/SHACL, all-source replacement, and all-source rollback.
- Rejection preserves staged changes and source files.

#### Tests

- Verify individual and combined previews without mutation.
- Verify new, worsened, unchanged, and resolved SHACL findings.
- Verify new, worsened, unchanged, and resolved consistency/unsatisfiable findings.
- Verify stale baselines, conflicts, blank nodes, RDF lists, round-trip mismatch, post-save mismatch, atomic apply, rejection, and rollback.

#### Verification commands

```bash
./gradlew :semantic-engine:test
./gradlew :validation-engine:test
./gradlew :graph-diff:test
./gradlew test
```

#### Stop conditions

- Preview writes any source file.
- Multi-source apply can leave only some files changed.
- Baseline comparison cannot distinguish new/worsened findings from unchanged findings.
- Graph equality is not blank-node-safe.

### Slice 11: Machine-Readable CLI Boundary

#### Goal

Expose reasoning runs, import/profile reports, explanations, SHACL validation, shape edits, validation modes, and proposal impact through a thin backward-compatible CLI boundary.

#### Allowed files/modules

- `cli/src/main/kotlin/com/entio/cli/`.
- `cli/src/test/kotlin/com/entio/cli/`.
- `cli/build.gradle.kts` only for a narrowly scoped pinned JSON dependency if required.
- Focused semantic-engine or validation tests only for boundary corrections.

#### Forbidden actions/modules

- Do not parse RDF, reason, validate SHACL, or apply changes directly in CLI code.
- Do not add a server, persistence, session store, or Git workflow.
- Do not break existing commands or invent a second proposal lifecycle.
- Do not expose OWL API, HermiT, or Jena SHACL types.

#### Expected changes or output

- Structured commands for reasoning/refresh, reasoning explanations, SHACL validation, SHACL mode, shape operations, and proposal impact.
- Stable JSON fields for status, fingerprints, completeness, asserted/inferred results, imports, feature reports, SHACL findings, and errors.
- Backward-compatible existing Phase 1-3 commands and structured malformed-request errors.

#### Tests

- Parse valid and invalid requests.
- Verify each result status and failure state is machine-readable.
- Verify deterministic ordering and backward compatibility for existing commands.
- Verify combined proposal responses include separated explicit, reasoning, and SHACL impact.

#### Verification commands

```bash
./gradlew :cli:test
./gradlew test
```

#### Stop conditions

- CLI begins owning semantic policy or source persistence.
- A result requires leaking third-party library objects.
- Existing command compatibility cannot be preserved without an approved scope change.

### Slice 12: VS Code Reasoning And SHACL Workbench

#### Goal

Present reasoning status, asserted/inferred facts, explanations, OWL limitations, SHACL shapes, constraints, validation results, and proposal impact in the existing workbench.

#### Allowed files/modules

- `vscode-extension/src/`.
- Matching `vscode-extension/src/test/`.
- CLI tests only for focused boundary corrections.

#### Forbidden actions/modules

- Do not parse RDF, reason, validate SHACL, generate semantic labels, or write Turtle in TypeScript.
- Do not add a UI framework, persistent session store, or independent semantic service.
- Do not edit inferred facts directly; route edits to asserted source facts or typed SHACL edits.
- Do not bypass staged changes, combined preview, approval, rejection, reload, or rollback.

#### Expected changes or output

- Reasoning view with status, refresh/cancel actions, asserted/inferred indicators, consistency, unsatisfiable classes, imports, unsupported features, fingerprints, and explanations.
- SHACL shape and constraint authoring forms using labels for user-facing fields and full IRIs in technical details.
- Validation results grouped by severity and linked to node/path/shape/constraint/source.
- Proposal review showing explicit diff, reasoning impact, SHACL impact, baseline blocking, and incomplete-result warnings.
- Loading, empty, unavailable, timed-out, cancelled, and error states.

#### Tests

- Normalize all new CLI responses without local semantic interpretation.
- Render reasoning and SHACL states and result categories.
- Verify form normalization and typed request generation.
- Verify refresh/cancel, failed preview, staged restoration, combined preview, approval, rejection, reload, and source opening.

#### Verification commands

```bash
cd vscode-extension && npm test
```

#### Stop conditions

- The extension needs independent RDF, OWL, or SHACL logic.
- UI code writes or rewrites ontology or shapes files directly.
- Workbench state requires durable persistence or a new framework.

### Slice 13: Phase 4 End-To-End Regression And Documentation Summary

#### Goal

Verify the complete OWL reasoning and SHACL workflow on copied fixtures and document the actual Phase 4 result, deviations, and limitations.

#### Allowed files/modules

- `cli/src/test/kotlin/com/entio/cli/`.
- `semantic-engine/src/test/kotlin/com/entio/semantic/`.
- `validation-engine/src/test/kotlin/com/entio/validation/`.
- `graph-diff/src/test/kotlin/com/entio/diff/`.
- `vscode-extension/src/test/`.
- Temporary copied fixtures under test-controlled directories.
- `docs/phase-summaries/phase-4-summary.md`.
- `docs/decisions/` completion artifact only.

#### Forbidden actions/modules

- Do not mutate committed examples as part of tests.
- Do not add new product behavior beyond approved Phase 4 slices.
- Do not weaken assertions, skip failing verification, or add unrelated documentation.
- Do not add external ontology downloads, Schema RAG, agents, persistence, Git behavior, or graph storage.

#### Expected changes or output

- End-to-end coverage for local imports, reasoning status, asserted/inferred views, explanations, profile findings, SHACL authoring, validation modes, baseline-aware impact, combined preview, atomic multi-source apply, rejection, reload, and rollback.
- A factual Phase 4 summary based on the implemented repository, including deviations and known limitations.

#### Tests

- Copy `examples/simple-ontology` before mutation.
- Add small local fixtures for imports, cycles, missing imports, inconsistent classes, inferred types, SHACL shapes, blank nodes, RDF lists, and multi-source changes.
- Verify all approved CLI and VS Code workflows.
- Verify source files remain unchanged on preview, rejection, timeout, cancellation, and failure.

#### Verification commands

```bash
./gradlew test
./gradlew build
./gradlew check
cd vscode-extension && npm test
```

#### Stop conditions

- Full Phase 4 behavior cannot be verified without external services or unapproved infrastructure.
- A regression requires changing earlier approved semantics beyond this plan.
- A committed fixture must be mutated to make a test pass.

## Test Plan

- Keep public contract tests in `core-types`.
- Keep OWL API, HermiT, import, worker, reasoning, explanation, and Jena adapter tests in `semantic-engine`.
- Keep deterministic baseline, severity, status, incomplete-result, and proposal-blocking tests in `validation-engine`.
- Keep only explicit/inferred/SHACL impact attribution and formatting tests in `graph-diff`.
- Keep JSON boundary, backward compatibility, and machine-readable failure tests in `cli`.
- Keep rendering, form-state, cancellation, loading, and message-boundary tests in `vscode-extension`.
- Copy fixtures before every mutating integration test.
- Assert deterministic ordering at every module and CLI boundary.
- Test both asserted-only and explicit asserted-plus-inferred SHACL modes.
- Test new, worsened, unchanged, and resolved reasoning and SHACL findings.
- Test missing, cyclic, incomplete, and unsupported imports without network access.
- Test worker timeout, cancellation, crash, stale output, and malformed output.
- Test blank-node-safe graph isomorphism and RDF-list normalization during round-trip and post-save checks.

## Full-Phase Verification

After Slice 13 is complete and merged into local `main`, run:

```bash
./gradlew test
./gradlew build
./gradlew check
cd vscode-extension && npm test
```

Also perform these manual checks against copied fixtures:

- Load a project with local imports and confirm the complete closure, cycle findings, fingerprints, and asserted/inferred separation.
- Preview a class hierarchy or data change and confirm reasoning and SHACL results are computed before approval without source mutation.
- Create and edit a shape and constraint through the workbench, inspect technical RDF details, and confirm validation output.
- Approve a multi-source proposal, reload, rerun reasoning and SHACL, and confirm graph-isomorphic round-trip verification.
- Reject a proposal and confirm source files and staged changes remain available.
- Force a timeout, cancellation, incomplete import, or post-save verification failure and confirm safe failure or complete rollback.

## Risks And Assumptions

- OWL API and HermiT compatibility may constrain the JVM or require a different artifact pair than initially expected. Slice 2 must resolve and pin this before later implementation begins.
- HermiT behavior and explanation support may vary by supported OWL construct. Entio must report partial or unavailable explanations instead of overstating guarantees.
- Reasoning worker packaging may require a Gradle distribution or dedicated worker classpath; it must remain a local process boundary without a server.
- Jena SHACL behavior must be contained in semantic-engine and normalized into Entio contracts.
- Import closure completeness depends on local files and explicit configuration. No internet fallback is permitted.
- OWL reasoning and SHACL can produce large result sets even for small projects; deterministic ordering and bounded UI rendering are required.
- Baseline-aware blocking assumes current and preview results have stable identities and fingerprints.
- Blank-node and RDF-list handling requires graph isomorphism or an equivalent normalization strategy; string comparison is insufficient.
- The workbench is a presentation layer. Any semantic behavior that cannot be represented through the Kotlin/CLI contracts is a contract or scope issue, not a reason to duplicate logic in TypeScript.

## Rollback Notes

- Before implementation, preserve the current Phase 3 baseline and do not mutate committed examples.
- Revert Phase 4 slice commits in reverse dependency order if a contract or library decision must be withdrawn.
- Remove OWL API/HermiT/Jena SHACL dependencies and worker packaging from the Gradle files if Slice 2 is rejected.
- Remove reasoning and SHACL adapters without changing existing Phase 1-3 parsing, descriptor, proposal, or CLI behavior.
- Keep new source-role configuration backward compatible; old projects must continue to load with their existing defaults.
- If a slice introduces an incompatible public contract, stop before merging and resolve through an approved spec or ADR rather than applying an ad hoc migration.
- Never use rollback as permission to reset or discard unrelated user work.

## Definition Of Done

Phase 4 is complete only when:

- All thirteen slices are implemented in dependency order with focused tests and completion artifacts.
- OWL API and HermiT versions are pinned, compatible, and isolated behind semantic-engine/worker boundaries.
- Jena SHACL is used for validation and its types do not cross public Entio boundaries.
- Project load, preview, combined preview, post-approval reload, and manual refresh run the approved reasoning/SHACL lifecycle.
- Asserted and inferred facts are distinct and inferred facts are never materialized automatically.
- Local import closure, cycles, missing imports, incomplete results, feature limitations, fingerprints, explanations, timeout, cancellation, reuse, and safe failure are represented correctly.
- Supported SHACL shapes, targets, direct paths, constraints, severity, messages, and asserted-only/default plus explicit asserted-plus-inferred validation are available through typed workflows.
- Proposal impact separates explicit graph changes, reasoning impact, and SHACL validation impact, with baseline-aware blocking.
- Multi-source approval is atomic, graph-isomorphism-safe, reload-verified, and fully rolled back on failure.
- CLI and VS Code expose the results without duplicating semantic logic or writing RDF directly.
- Copied-fixture regression tests pass, including rejection, approval, reload, rollback, blank nodes, lists, imports, worker failure, and UI boundary cases.
- `./gradlew test`, `./gradlew build`, `./gradlew check`, and `cd vscode-extension && npm test` pass.
- `docs/phase-summaries/phase-4-summary.md` accurately describes the implemented repository and explicitly records deviations and follow-up work.
- No Phase 5 capabilities such as external ontology browsing, Schema RAG, document ingestion, entity resolution, or autonomous agents have been added.
