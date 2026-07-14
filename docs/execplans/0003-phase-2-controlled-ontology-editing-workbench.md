# ExecPlan: Phase 2 Controlled Ontology Editing Workbench

## Status

Completed

## Source Spec

[Feature Spec: Phase 2 Controlled Ontology Editing Workbench](../specs/0003-phase-2-controlled-ontology-editing-workbench.md)

## Goal

Implement Phase 2 as a controlled ontology-editing workbench on top of the completed Phase 1 and Phase 1.5 semantic-engine foundation.

Phase 2 should let users inspect a small local Entio project, create supported ontology edits through typed operations, preview the proposed graph, review semantic diffs and validation results, approve or reject proposals, and safely apply approved proposals to Turtle source files.

The implementation must keep the Kotlin semantic engine responsible for RDF and ontology behavior. The VS Code layer should be thin and should not parse Turtle, construct RDF independently for ordinary editing operations, validate ontology rules, or write ontology files directly.

Phase 2 should use a git-like semantic review workflow only by analogy: draft, preview, diff, review, approve, and apply. Entio must not stage files, create commits, push branches, manage Git branches, or create pull requests.

## Related Spec

This ExecPlan implements `docs/specs/0003-phase-2-controlled-ontology-editing-workbench.md`.

Related context:

- `docs/architecture/phase-2-scope.md`
- `docs/phase-summaries/phase-1.5-summary.md`
- `AGENTS.md`
- `README.md`

## Current State

The repository already includes:

- Kotlin/JVM Gradle modules:
  - `core-types`
  - `semantic-engine`
  - `validation-engine`
  - `graph-diff`
  - `cli`
  - `shared`
- `ProjectLoader` in `semantic-engine`.
- `EntioProject` as a loaded-project aggregate.
- RDF-term-aware graph contracts in `core-types`.
- Deterministic Turtle parsing through Apache Jena in `semantic-engine`.
- Symbol extraction in `semantic-engine`.
- Validation reporting in `validation-engine`.
- Semantic graph diffing and formatting in `graph-diff`.
- CLI commands for `validate`, `symbols`, and `diff`.
- `examples/simple-ontology` as the current local fixture.

The repository does not yet include:

- Controlled graph-change application.
- Typed ontology edit translation.
- In-memory preview graph generation.
- Proposal baseline or stale-proposal detection.
- Proposal validation beyond existing project validation.
- Turtle serialization of edited preview graphs.
- Atomic source-file application and rollback.
- Machine-readable command output for workbench integration.
- VS Code extension infrastructure or a workbench UI.

## Target State

After Phase 2:

- `core-types` contains stable Entio contracts for graph changes, change sets, typed ontology edits, proposals, proposal statuses, previews, source-file impact, baseline fingerprints, apply results, rollback results, and workbench boundary payloads where shared across Kotlin modules.
- `semantic-engine` can translate supported typed ontology edits into graph changes, apply change sets to in-memory graph states, generate preview graph results, serialize preview graphs to temporary Turtle through existing RDF tooling, reparse the temporary Turtle, and verify semantic equivalence.
- `validation-engine` can validate proposals deterministically and report proposal-specific issues.
- `graph-diff` can generate and format semantic diffs for preview proposals using the existing graph diff foundation.
- `cli` exposes thin machine-readable commands needed by the workbench and delegates semantic behavior to engine modules.
- A minimal TypeScript VS Code extension can detect an Entio project, open a webview workbench, display project summaries and selected entity details, request preview/diff/validation/application actions, and display engine results.
- Approved proposals can be applied atomically to the correct Turtle source, and failures after touching a source file attempt rollback.
- Existing Phase 1 and Phase 1.5 behavior continues to work.

## Affected Modules And Files

Expected Kotlin modules:

- `core-types`
- `semantic-engine`
- `validation-engine`
- `graph-diff`
- `cli`

Expected TypeScript extension files:

- A new VS Code extension directory, subject to the approved slice that introduces it.
- Minimal package and TypeScript configuration for the extension.
- Webview source files for the ontology workbench.

Expected documentation and fixtures:

- `docs/decisions/` completion notes or ADRs when a slice makes an important architectural decision.
- `examples/simple-ontology/` only if a small fixture update is needed for Phase 2 tests.
- `docs/phase-summaries/phase-2-summary.md` after implementation of last sliceis complete.

Files and modules that should remain out of scope unless a later approved slice explicitly allows them:

- Web app infrastructure.
- API server infrastructure.
- Database or production graph-storage infrastructure.
- Autonomous agent infrastructure.
- Document ingestion infrastructure.
- Schema RAG infrastructure.
- Entity resolution infrastructure.
- Git automation for staging, commits, pushes, branch management, or pull requests.

## Recommended Dependencies

Phase 2 should keep dependencies minimal.

Recommended existing dependencies:

- Apache Jena remains the semantic-web library for RDF/Turtle parsing and should also be used for Turtle serialization and reparsing where needed.
- Existing Gradle/JUnit test dependencies remain sufficient for Kotlin module tests unless a specific slice justifies a small addition.

Recommended new dependencies:

- VS Code extension development will require the minimal TypeScript and VS Code extension dependencies needed to compile and test a local extension.
- A small JSON serialization library may be introduced for CLI/workbench payloads if the existing project does not already include one. Prefer a simple, well-supported JVM library and keep JSON boundary types explicit.

Avoid:

- New RDF, OWL, SHACL, Turtle, or reasoning frameworks.
- Server frameworks.
- Dependency-injection frameworks.
- Database layers.
- Coroutine infrastructure.
- Plugin systems.
- UI frameworks that make the workbench heavier than the Phase 2 scope requires.

## Dependency Order

Implementation should proceed from stable shared contracts to engine behavior, then CLI/workbench integration.

Serial dependencies:

1. Core editing contracts must be introduced before engine modules consume them.
2. Graph-change preview behavior depends on core editing contracts.
3. Typed ontology edit translation depends on graph-change contracts.
4. Proposal baseline and validation behavior depends on preview and change-set contracts.
5. Serialization and semantic equivalence verification depends on preview graphs.
6. Atomic apply and rollback depends on baseline checks and serialization verification.
7. Machine-readable CLI commands depend on stable engine APIs.
8. VS Code workbench behavior depends on stable CLI or process boundary payloads.

Potentially parallel after contracts stabilize:

- Proposal validation tests and graph-diff formatting tests may proceed in parallel after preview result contracts are stable.
- Workbench UI layout and request/response rendering may proceed in parallel after JSON boundary contracts are stable.
- Fixture updates may proceed in parallel with module tests after expected behavior is documented.

Do not parallelize work on unstable shared contracts:

- `core-types`
- `shared`
- build files
- specs
- execplans
- `AGENTS.md`
- JSON boundary contracts consumed by both CLI and VS Code

## Step-By-Step Implementation Plan

### Slice 1: Core Editing Contracts

Goal:

Introduce Entio-owned data contracts for graph changes, change sets, typed ontology edits, proposals, proposal statuses, previews, baselines, file impact, apply results, and rollback results.

Allowed files/modules:

- `core-types/src/main/kotlin/com/entio/core/`
- `core-types/src/test/kotlin/com/entio/core/`
- `docs/decisions/` only for a completion artifact or ADR if required by the implementation skill.

Forbidden actions/modules:

- Do not modify engine behavior.
- Do not add VS Code extension infrastructure.
- Do not add CLI commands.
- Do not add source-file mutation.
- Do not add serialization behavior.
- Do not add Git automation.
- Do not modify `semantic-engine`, `validation-engine`, `graph-diff`, `cli`, or `shared`.

Expected changes or output:

- Immutable core data objects for graph changes and change sets.
- A sealed or otherwise fixed model for supported typed ontology edits.
- Proposal status values for the Phase 2 lifecycle.
- Baseline, preview, source-file impact, apply, and rollback result contracts.
- Focused construction and equality tests.

Tests:

- Construct each new contract.
- Confirm statuses and edit kinds are fixed and deterministic.
- Confirm change sets preserve deterministic ordering or explicitly sort when required.
- Confirm invalid empty change sets are rejected by construction or represented as validation failures, depending on the chosen model.

Verification commands:

```bash
./gradlew :core-types:test
./gradlew test
```

Stop conditions:

- The contracts require dependencies on engine modules.
- The model would allow literals as RDF subjects.
- The model introduces Git workflow concepts beyond external metadata or git-like semantic review wording.
- The model requires source persistence before preview behavior exists.

### Slice 2: Graph Change Preview Engine

Goal:

Implement in-memory graph-change application that produces preview graph results without mutating the original graph.

Allowed files/modules:

- `semantic-engine/src/main/kotlin/com/entio/semantic/`
- `semantic-engine/src/test/kotlin/com/entio/semantic/`
- `core-types/src/main/kotlin/com/entio/core/` only for small additive contract refinements required by implementation.
- `core-types/src/test/kotlin/com/entio/core/` only for matching contract tests.
- `docs/decisions/` only for a completion artifact or ADR if required.

Forbidden actions/modules:

- Do not write ontology source files.
- Do not serialize Turtle.
- Do not add VS Code infrastructure.
- Do not add CLI commands.
- Do not modify validation or graph-diff modules except through existing public contracts.
- Do not add Git automation.

Expected changes or output:

- A preview service or function that applies additions and removals to a `GraphState`.
- Duplicate additions and missing removals are detected deterministically or returned as structured preview failures.
- The original graph state remains unchanged.

Tests:

- Add a triple to a preview graph.
- Remove an existing triple from a preview graph.
- Detect duplicate addition.
- Detect removal of a missing triple.
- Applying a change set does not mutate the original graph.
- Preview output ordering is deterministic.

Verification commands:

```bash
./gradlew :semantic-engine:test
./gradlew test
```

Stop conditions:

- Preview behavior requires source-file writes.
- Preview behavior requires a custom RDF framework.
- Existing Phase 1.5 graph contracts are insufficient and need broad redesign.

### Slice 3: Typed Ontology Edit Translation

Goal:

Translate supported user-facing ontology edits into graph changes in the Kotlin semantic engine.

Allowed files/modules:

- `semantic-engine/src/main/kotlin/com/entio/semantic/`
- `semantic-engine/src/test/kotlin/com/entio/semantic/`
- `core-types/src/main/kotlin/com/entio/core/` only for additive typed-edit contract refinements.
- `core-types/src/test/kotlin/com/entio/core/` only for matching tests.
- `docs/decisions/` only for a completion artifact or ADR if required.

Forbidden actions/modules:

- Do not implement UI forms.
- Do not add source-file persistence.
- Do not add CLI commands.
- Do not add validation rules outside translation preconditions.
- Do not add OWL reasoning.
- Do not add full OWL class-expression editing.
- Do not add Git automation.

Expected changes or output:

- Translation for:
  - create class,
  - add or change label,
  - add and remove superclass relationship,
  - create object property,
  - create datatype property,
  - set domain,
  - set range,
  - create individual,
  - assign type,
  - add object-property assertion,
  - add datatype-property assertion.
- Unsupported or invalid typed edits return structured failures.

Tests:

- Each supported typed edit produces expected triples.
- Invalid IRIs and invalid RDF term positions fail deterministically.
- Literal values preserve datatype and language-tag metadata where relevant.

Verification commands:

```bash
./gradlew :semantic-engine:test
./gradlew test
```

Stop conditions:

- Translation requires the TypeScript layer to construct RDF triples directly.
- Translation expands into full OWL authoring.
- Translation requires reasoning or SHACL authoring.

### Slice 4: Proposal Creation And Baseline Fingerprints

Goal:

Create proposals with target source information, baseline fingerprints, preview graph, semantic diff placeholder or result slot, validation result slot, status, and file impact.

Allowed files/modules:

- `semantic-engine/src/main/kotlin/com/entio/semantic/`
- `semantic-engine/src/test/kotlin/com/entio/semantic/`
- `core-types/src/main/kotlin/com/entio/core/` only for additive proposal contract refinements.
- `core-types/src/test/kotlin/com/entio/core/` only for matching tests.
- `docs/decisions/` only for a completion artifact or ADR if required.

Forbidden actions/modules:

- Do not apply proposals to disk.
- Do not add VS Code infrastructure.
- Do not add Git branch, staging, commit, push, or pull-request behavior.
- Do not persist long-term proposal history.
- Do not add database or cache infrastructure.

Expected changes or output:

- Proposal creation from a loaded project, target source, and change set.
- Baseline fingerprints for relevant graph and source-file state.
- Stale proposal detection based on current project and target source state.
- Source-file impact metadata.

Tests:

- Proposal includes target source and baseline data.
- Proposal detects unchanged baseline as current.
- Proposal detects changed target file as stale.
- Proposal does not block unrelated project file changes.

Verification commands:

```bash
./gradlew :semantic-engine:test
./gradlew test
```

Stop conditions:

- Baseline detection requires Git state.
- Proposal metadata requires persistent project version history.
- Proposal creation requires writing source files.

### Slice 5: Proposal Validation

Goal:

Add deterministic proposal validation that combines existing project validation with proposal-specific checks.

Allowed files/modules:

- `validation-engine/src/main/kotlin/com/entio/validation/`
- `validation-engine/src/test/kotlin/com/entio/validation/`
- `core-types/src/main/kotlin/com/entio/core/` only for additive validation issue codes or result refinements.
- `core-types/src/test/kotlin/com/entio/core/` only for matching tests.
- `semantic-engine/src/test/kotlin/com/entio/semantic/` only if shared test fixtures need to cover validation inputs.
- `docs/decisions/` only for a completion artifact or ADR if required.

Forbidden actions/modules:

- Do not add source-file persistence.
- Do not add VS Code infrastructure.
- Do not add graph diff behavior.
- Do not add OWL reasoning or full SHACL validation.
- Do not add Git automation.
- Do not move product logic into `shared`.

Expected changes or output:

- Proposal validation checks for empty change sets, duplicate additions, missing removals, invalid target source, stale baseline, preview failure, and semantic-equivalence failure status when available.
- Validation reports remain deterministic and sorted.

Tests:

- Valid proposal returns valid report.
- Empty change set returns deterministic issue.
- Duplicate addition returns deterministic issue.
- Missing removal returns deterministic issue.
- Stale proposal returns deterministic issue.
- Existing project validation behavior remains unchanged.

Verification commands:

```bash
./gradlew :validation-engine:test
./gradlew test
```

Stop conditions:

- Proposal validation requires UI state.
- Proposal validation requires Git state.
- Proposal validation requires AI judgment.

### Slice 6: Proposal Semantic Diff Integration

Goal:

Integrate proposal preview results with semantic diff generation and formatting.

Allowed files/modules:

- `graph-diff/src/main/kotlin/com/entio/diff/`
- `graph-diff/src/test/kotlin/com/entio/diff/`
- `semantic-engine/src/main/kotlin/com/entio/semantic/` only for wiring proposal preview outputs to diff inputs.
- `semantic-engine/src/test/kotlin/com/entio/semantic/` only for integration tests.
- `core-types/src/main/kotlin/com/entio/core/` only for additive diff metadata if required.
- `core-types/src/test/kotlin/com/entio/core/` only for matching tests.
- `docs/decisions/` only for a completion artifact or ADR if required.

Forbidden actions/modules:

- Do not add source-file persistence.
- Do not add VS Code infrastructure.
- Do not add CLI commands.
- Do not change existing diff semantics unless required by the proposal spec.
- Do not add Git diff or staged-diff behavior.

Expected changes or output:

- Proposal preview can produce a semantic diff between current graph and preview graph.
- Diff formatting remains deterministic and readable.
- Label-change behavior remains compatible with existing graph diff behavior.

Tests:

- Proposal diff reports added triples.
- Proposal diff reports removed triples.
- Proposal diff reports label changes where applicable.
- Diff output remains deterministic.
- Existing graph diff tests continue to pass.

Verification commands:

```bash
./gradlew :graph-diff:test
./gradlew test
```

Stop conditions:

- Diff integration requires source-file writes.
- Diff integration requires Git diff behavior.
- Diff integration requires a new RDF framework.

### Slice 7: Turtle Serialization And Semantic Equivalence

Goal:

Serialize preview graphs to temporary Turtle with established RDF tooling, reparse the temporary Turtle, and verify semantic equivalence with the preview graph.

Allowed files/modules:

- `semantic-engine/src/main/kotlin/com/entio/semantic/`
- `semantic-engine/src/test/kotlin/com/entio/semantic/`
- `core-types/src/main/kotlin/com/entio/core/` only for additive semantic-equivalence result contracts.
- `core-types/src/test/kotlin/com/entio/core/` only for matching tests.
- `examples/simple-ontology/` only if a small fixture update is needed.
- `docs/decisions/` only for a completion artifact or ADR if required.

Forbidden actions/modules:

- Do not replace project source files.
- Do not promise source-text-preserving Turtle round trips.
- Do not add a custom Turtle serializer.
- Do not add VS Code infrastructure.
- Do not add Git automation.

Expected changes or output:

- Temporary Turtle serialization for preview graphs.
- Reparse using the existing parser boundary or Apache Jena inside `semantic-engine`.
- Semantic equivalence check between preview graph and reparsed graph.
- Structured failure for serialization, reparse, and equivalence errors.

Tests:

- Serialize and reparse a preview graph with IRI resources.
- Serialize and reparse plain literals.
- Serialize and reparse datatyped literals.
- Serialize and reparse language-tagged literals.
- Serialize and reparse blank nodes without treating blank-node identifiers as durable.
- Detect semantic-equivalence failure deterministically.

Verification commands:

```bash
./gradlew :semantic-engine:test
./gradlew test
```

Stop conditions:

- Serialization requires preserving original Turtle text layout.
- Serialization would expose Jena types outside semantic-engine boundaries.
- Equivalence checking requires named graph support or canonicalization beyond Phase 2 needs.

### Slice 8: Atomic Proposal Application And Rollback

Goal:

Apply approved, valid, current proposals atomically to the target Turtle source and restore the prior source if save or verification fails.

Allowed files/modules:

- `semantic-engine/src/main/kotlin/com/entio/semantic/`
- `semantic-engine/src/test/kotlin/com/entio/semantic/`
- `validation-engine/src/main/kotlin/com/entio/validation/` only for final apply precondition validation wiring.
- `validation-engine/src/test/kotlin/com/entio/validation/` only for matching tests.
- `core-types/src/main/kotlin/com/entio/core/` only for additive apply or rollback result refinements.
- `core-types/src/test/kotlin/com/entio/core/` only for matching tests.
- `examples/simple-ontology/` only if a fixture copy is needed for tests.
- `docs/decisions/` only for a completion artifact or ADR if required.

Forbidden actions/modules:

- Do not add VS Code infrastructure.
- Do not add Git staging, commits, pushes, branch management, or pull requests.
- Do not add long-term version history.
- Do not modify unrelated project files.
- Do not add database or cache infrastructure.

Expected changes or output:

- Apply only approved proposals.
- Recheck baseline immediately before writing.
- Write to a temporary file first.
- Reparse and verify temporary Turtle before replacing the target source.
- Replace the target source atomically where the platform permits.
- Reload the project after save.
- Verify the saved graph matches the approved preview.
- Restore the original source if post-save verification fails.
- Return structured apply and rollback results.

Tests:

- Approved proposal writes only the target ontology source.
- Rejected proposal does not write files.
- Stale proposal is blocked before writing.
- Temporary serialization failure does not modify source.
- Post-save verification failure restores prior source.
- Application reports rollback success or failure.
- Unrelated files remain untouched.

Verification commands:

```bash
./gradlew :semantic-engine:test
./gradlew :validation-engine:test
./gradlew test
```

Stop conditions:

- Apply behavior requires Git staging or commits.
- Apply behavior cannot avoid modifying unrelated files.
- Rollback cannot be represented in structured results.

### Slice 9: Machine-Readable CLI Boundary

Goal:

Expose thin machine-readable CLI commands needed by the VS Code workbench while keeping semantic behavior in reusable Kotlin modules.

Allowed files/modules:

- `cli/src/main/kotlin/com/entio/cli/`
- `cli/src/test/kotlin/com/entio/cli/`
- `core-types/src/main/kotlin/com/entio/core/` only for additive boundary response contracts if required.
- `core-types/src/test/kotlin/com/entio/core/` only for matching tests.
- `semantic-engine/src/main/kotlin/com/entio/semantic/` only for small public API adjustments needed by CLI delegation.
- `semantic-engine/src/test/kotlin/com/entio/semantic/` only for matching tests.
- `docs/decisions/` only for a completion artifact or ADR if required.

Forbidden actions/modules:

- Do not put parsing, validation, diffing, preview, or apply logic directly in CLI files.
- Do not add VS Code extension files.
- Do not add server mode or watch mode.
- Do not add Git automation.
- Do not break existing `validate`, `symbols`, or `diff` commands.

Expected changes or output:

- Machine-readable command output for project summary, proposal preview, proposal validation, proposal diff, proposal apply, and proposal reject where needed.
- Existing text CLI commands continue to work.
- CLI remains a thin wrapper over engine modules.

Tests:

- Existing CLI tests pass.
- Machine-readable project summary command returns stable output.
- Preview command returns structured preview/diff/validation data.
- Apply command delegates to engine and returns structured result.
- Invalid inputs produce deterministic non-zero exit codes and structured errors.

Verification commands:

```bash
./gradlew :cli:test
./gradlew test
./gradlew build
```

Stop conditions:

- CLI starts owning semantic behavior.
- CLI requires a local API server.
- Existing commands regress.

### Slice 10: Minimal VS Code Extension Scaffold

Goal:

Introduce the smallest VS Code extension scaffold needed to open an Entio ontology workbench.

Allowed files/modules:

- A new VS Code extension directory and files explicitly named by the implementation prompt.
- Minimal TypeScript package/config files for the extension.
- Extension tests if practical for the chosen scaffold.
- `docs/decisions/` only for a completion artifact or ADR if required.

Forbidden actions/modules:

- Do not modify Kotlin semantic behavior.
- Do not parse Turtle in TypeScript.
- Do not implement ontology validation in TypeScript.
- Do not construct RDF triples directly in TypeScript for ordinary edits.
- Do not write ontology files directly from TypeScript.
- Do not add web app infrastructure.
- Do not add server infrastructure.
- Do not add Git automation.

Expected changes or output:

- VS Code extension activation.
- Command `Entio: Open Ontology Workbench`.
- Minimal webview shell.
- Project detection for a local Entio workspace.
- Process-boundary helper for invoking the Kotlin CLI or engine wrapper.

Tests:

- Extension compiles.
- Command registration is test-covered if the scaffold supports it.
- Project detection handles missing and present `entio.yaml`.
- No RDF parsing or validation logic appears in TypeScript tests or source.

Verification commands:

```bash
./gradlew test
./gradlew build
```

Additional TypeScript verification commands should be added by the slice when the extension package scripts exist.

Stop conditions:

- Extension work requires a web app framework.
- Extension work requires direct Turtle parsing or file writes.
- Extension work requires broad build-system changes not scoped by an approved slice.

### Slice 11: Workbench Project Browser

Goal:

Display project name, ontology sources, classes, properties, individuals, and selected entity details in the VS Code workbench.

Allowed files/modules:

- VS Code extension source and tests.
- `cli/src/main/kotlin/com/entio/cli/` only for machine-readable project summary refinements.
- `cli/src/test/kotlin/com/entio/cli/` only for matching tests.
- `core-types/src/main/kotlin/com/entio/core/` only for additive response contracts if required.
- `docs/decisions/` only for a completion artifact or ADR if required.

Forbidden actions/modules:

- Do not add ontology mutation UI yet.
- Do not parse Turtle in TypeScript.
- Do not add direct source-file writes.
- Do not add graph editor behavior.
- Do not add Git automation.
- Do not modify unrelated Kotlin engine behavior.

Expected changes or output:

- Workbench displays project summary data from the Kotlin boundary.
- Workbench displays ontology source list.
- Workbench displays symbol groups.
- Workbench displays selected entity details.
- Workbench can refresh after source changes.

Tests:

- Project summary rendering handles valid data.
- Missing project produces a clear error state.
- Selected entity detail rendering is deterministic.
- Refresh re-requests engine data.

Verification commands:

```bash
./gradlew :cli:test
./gradlew test
```

Additional TypeScript verification commands should be run once extension scripts exist.

Stop conditions:

- Workbench needs to parse RDF locally.
- Workbench requires mutation behavior before preview/apply APIs are stable.
- UI scope expands into a full graph editor.

### Slice 12: Workbench Edit Forms And Preview Flow

Goal:

Add focused workbench forms for supported typed ontology edits and wire them to preview, diff, and validation results.

Allowed files/modules:

- VS Code extension source and tests.
- `cli/src/main/kotlin/com/entio/cli/` only for preview/diff/validation command refinements.
- `cli/src/test/kotlin/com/entio/cli/` only for matching tests.
- `docs/decisions/` only for a completion artifact or ADR if required.

Forbidden actions/modules:

- Do not construct RDF triples directly in TypeScript for ordinary edits.
- Do not write source files from TypeScript.
- Do not add apply behavior unless the approved slice includes it.
- Do not add full OWL authoring forms.
- Do not add graph editor behavior.
- Do not add Git automation.

Expected changes or output:

- Forms for supported typed ontology edits.
- Preview request from webview to extension to Kotlin boundary.
- Display semantic diff, validation result, target source, and file impact.
- Disable approval when preview, validation, diff, or equivalence checks fail.

Tests:

- Each form creates the expected typed-edit request payload.
- Preview success renders diff and validation results.
- Preview failure renders structured errors.
- Approval action is disabled for invalid proposals.
- TypeScript code does not include RDF parsing or validation logic.

Verification commands:

```bash
./gradlew :cli:test
./gradlew test
```

Additional TypeScript verification commands should be run once extension scripts exist.

Stop conditions:

- TypeScript needs to construct RDF triples independently.
- UI forms require unsupported ontology edit types.
- Preview API is unstable or incomplete.

### Slice 13: Workbench Approval, Apply, Reject, And Refresh

Goal:

Wire workbench approval and rejection actions to Kotlin proposal application while preserving source-file safety.

Allowed files/modules:

- VS Code extension source and tests.
- `cli/src/main/kotlin/com/entio/cli/` only for apply/reject command refinements.
- `cli/src/test/kotlin/com/entio/cli/` only for matching tests.
- `semantic-engine/src/test/kotlin/com/entio/semantic/` only for end-to-end apply fixture coverage if needed.
- `docs/decisions/` only for a completion artifact or ADR if required.

Forbidden actions/modules:

- Do not write ontology files directly from TypeScript.
- Do not stage, commit, push, manage branches, or create pull requests.
- Do not add long-term proposal history.
- Do not add multi-user collaboration.
- Do not add web app infrastructure.

Expected changes or output:

- Reject action drops the current proposal and leaves source files unchanged.
- Approve action calls the Kotlin apply boundary.
- Apply result shows success, stale, validation failure, apply failure, or rollback result.
- Workbench refreshes project data after successful apply.
- Workbench can open the modified Turtle source in VS Code.

Tests:

- Reject does not call apply.
- Approve calls apply with proposal identity or payload.
- Stale apply result is displayed clearly.
- Rollback failure result is displayed clearly.
- Refresh happens after successful apply.

Verification commands:

```bash
./gradlew :cli:test
./gradlew test
./gradlew build
```

Additional TypeScript verification commands should be run once extension scripts exist.

Stop conditions:

- Approval requires Git automation.
- Apply must be performed by TypeScript.
- Source-file writes cannot be constrained to affected ontology files.

### Slice 14: End-To-End Phase 2 Regression

Goal:

Add end-to-end tests proving the complete Phase 2 workflow against a small fixture.

Allowed files/modules:

- `examples/simple-ontology/` only if a small fixture update is needed.
- Kotlin module tests in `core-types`, `semantic-engine`, `validation-engine`, `graph-diff`, and `cli`.
- VS Code extension tests if the extension scaffold supports them.
- `docs/decisions/` only for a completion artifact or ADR if required.

Forbidden actions/modules:

- Do not add new product behavior beyond the implemented Phase 2 workflow.
- Do not add broad fixture suites or large ontology datasets.
- Do not add web app, server, database, AI, document ingestion, Schema RAG, or entity-resolution infrastructure.
- Do not add Git automation.

Expected changes or output:

- A regression test covers load, typed edit, preview, diff, validation, approve, apply, reload, semantic equivalence, and source-file impact.
- Existing Phase 1 and Phase 1.5 behavior remains covered.
- Failure-path regression covers stale proposal or rollback behavior.

Tests:

- End-to-end happy path on a copied fixture.
- Reject path leaves copied fixture unchanged.
- Stale proposal blocks apply.
- Apply failure attempts rollback.
- Existing CLI commands still pass.

Verification commands:

```bash
./gradlew test
./gradlew build
./gradlew check
```

Additional TypeScript verification commands should be run once extension scripts exist.

Stop conditions:

- Regression requires modifying real example fixtures in-place rather than copied test fixtures.
- Regression depends on external services.
- Regression requires Git staging, commits, pushes, or pull requests.

### Slice 15: Phase 2 Documentation Summary

Goal:

Document the actual completed Phase 2 implementation after the code slices are merged.

Allowed files/modules:

- `docs/phase-summaries/phase-2-summary.md`
- `README.md` only for repository status updates.
- `AGENTS.md` only for repository status and active phase updates after Phase 2 completion.
- `docs/architecture/` only for narrow status updates if needed.
- `docs/decisions/` only for completion artifacts or ADRs if required.

Forbidden actions/modules:

- Do not change source code.
- Do not change tests.
- Do not change build files.
- Do not invent future behavior not implemented.
- Do not mark Phase 2 complete before verification passes.

Expected changes or output:

- Summary of implemented Phase 2 behavior.
- Current repository structure.
- Module responsibilities.
- Main contracts.
- How proposal preview, validation, diffing, approval, apply, rollback, CLI, and VS Code workbench fit together.
- Commands to test/build.
- Known limitations and follow-up work.

Tests:

- Documentation-only review.

Verification commands:

```bash
git diff --stat
```

Stop conditions:

- Implementation is incomplete.
- Verification has not passed.
- The summary would need to describe behavior that is not actually implemented.

## Test Plan

Phase 2 testing should include:

- Focused unit tests for new core contracts.
- Semantic-engine tests for graph-change preview, typed edit translation, proposal baselines, stale detection, serialization, semantic equivalence, apply, and rollback.
- Validation-engine tests for proposal validation issues and deterministic ordering.
- Graph-diff tests for proposal diffs and formatting.
- CLI tests for machine-readable commands and existing command compatibility.
- VS Code extension tests for command registration, project detection, request/response handling, rendering states, preview flow, approve/reject flow, and refresh behavior where practical.
- End-to-end fixture tests using copied local project fixtures so source files are not modified in-place.

Existing Phase 1 and Phase 1.5 tests must continue to pass.

## Verification Commands

Run before completing Kotlin behavior slices:

```bash
./gradlew test
./gradlew build
./gradlew check
```

Run focused module checks during slices:

```bash
./gradlew :core-types:test
./gradlew :semantic-engine:test
./gradlew :validation-engine:test
./gradlew :graph-diff:test
./gradlew :cli:test
```

Run TypeScript extension verification commands once the extension package defines them. The slice that introduces the extension must document the exact commands in its completion artifact.

## Rollback Notes

Each slice should be independently revertible.

Rollback guidance:

- Contract-only slices can be reverted by removing the new data objects and tests if no downstream slice depends on them.
- Engine behavior slices should be reverted before dependent CLI or VS Code slices.
- CLI boundary changes should be reverted before VS Code integration that depends on them.
- VS Code workbench slices should be revertible without changing Kotlin engine behavior.
- Fixture changes should be small and reversible.
- Do not use destructive Git commands to rollback user work.

If a later slice discovers a contract issue in an earlier slice, prefer an additive correction slice unless the contract has not yet been consumed downstream.

## Risks And Assumptions

Risks:

- Turtle serialization may not preserve source text formatting. Phase 2 explicitly does not require source-text-preserving round trips.
- Blank-node identity may be hard to display without implying durable identity.
- Atomic replacement behavior can vary by platform and filesystem.
- Proposal baseline checks may need careful design to avoid blocking unrelated file changes.
- VS Code extension setup may introduce new build tooling and CI considerations.
- Machine-readable CLI payloads may need stable schemas before the workbench depends on them.
- The workbench scope could expand into a full graph editor if not constrained.

Assumptions:

- Apache Jena remains available for Turtle serialization and reparsing.
- The Phase 1.5 RDF term model is sufficient for Phase 2 graph changes.
- Phase 2 works on small local Turtle projects.
- Proposal metadata can be kept in memory unless a later approved plan explicitly adds temporary persistence.
- External source-control tools remain outside Entio's Phase 2 responsibilities.
- The VS Code extension can call Kotlin behavior through process invocation or a thin CLI boundary without a local API server.

## Open Questions

- Should Phase 2 serialize the entire target ontology source after applying a proposal, or should it attempt smaller source-file updates while accepting that source-text preservation is not guaranteed?
- How should Entio choose the target ontology source when a typed edit references symbols from multiple sources?
- Should Phase 2 support creating a new ontology source file, or only editing existing sources?
- What exact JSON schema should the VS Code extension use for project summary, preview, validation, diff, apply, and reject actions?
- Should the VS Code workbench call existing CLI commands, new machine-readable CLI commands, or a dedicated process wrapper?
- How much selected-entity neighborhood visualization is useful without becoming a full graph editor?
- Should proposal metadata stay in memory for Phase 2, or should there be temporary local persistence?
- What minimum proposal validation rules should block approval beyond parser, graph consistency, and semantic-equivalence checks?
- How should blank-node identifiers appear in user-facing diffs?

## Multi-Agent Safety

Implement these slices serially:

- Slice 1: Core Editing Contracts.
- Slice 2: Graph Change Preview Engine.
- Slice 3: Typed Ontology Edit Translation.
- Slice 4: Proposal Creation And Baseline Fingerprints.
- Slice 7: Turtle Serialization And Semantic Equivalence.
- Slice 8: Atomic Proposal Application And Rollback.
- Slice 9: Machine-Readable CLI Boundary.
- Slice 10: Minimal VS Code Extension Scaffold.

Potentially parallel after stable contracts exist:

- Slice 5: Proposal Validation may proceed alongside Slice 6 after preview/proposal contracts are stable.
- Slice 6: Proposal Semantic Diff Integration may proceed alongside Slice 5 after preview/proposal contracts are stable.
- Slice 11: Workbench Project Browser may proceed alongside UI-only work after the extension scaffold and project-summary boundary are stable.
- Slice 12 and Slice 13 should not run in parallel until apply and preview boundaries are stable.
- Slice 14 must run after all behavior slices it verifies.
- Slice 15 must run after implementation and verification are complete.

Do not parallelize edits to:

- `core-types`
- `shared`
- build files
- specs
- execplans
- `AGENTS.md`
- JSON boundary contracts shared by CLI and VS Code

## Boundary Check

This plan fits the current active phase because Phase 2 is explicitly the Controlled Ontology Editing Workbench phase.

This plan avoids current non-goals by excluding:

- Autonomous AI agents.
- LLM-generated ontology edits.
- Schema RAG.
- Document ingestion and document-to-KG conversion.
- Entity resolution.
- Production graph storage.
- Full Protégé parity.
- Full OWL class-expression editing.
- Full SHACL authoring or validation.
- Long-term project version history.
- Git staging, commits, pushes, branch management, or pull-request creation inside Entio.
- Web app or API server infrastructure.

This plan preserves module responsibilities:

- `core-types` owns Entio data contracts.
- `semantic-engine` owns project loading, RDF interpretation, edit translation, preview, serialization, equivalence, apply, and rollback.
- `validation-engine` owns deterministic validation reports.
- `graph-diff` owns semantic diff generation and formatting.
- `cli` remains a thin process boundary.
- The VS Code extension owns activation, webview rendering, user input, and display.
- `shared` remains generic and should not collect product logic.

This plan preserves the rule that Entio should use existing RDF, OWL, SHACL, and Turtle tooling rather than reinventing standards. Phase 2 should continue using Apache Jena for RDF/Turtle parsing and serialization behind semantic-engine boundaries.

## Definition Of Done

Phase 2 is done when:

- Controlled graph changes and change sets exist.
- Supported typed ontology edits translate to graph changes in the Kotlin engine.
- Preview graphs are generated without mutating source files.
- Proposal semantic diffs and validation results are generated before approval.
- Temporary Turtle serialization, reparsing, and semantic equivalence checks pass for approved previews.
- Stale proposals are detected before application.
- Approved proposals apply atomically to the correct Turtle source.
- Failed application attempts restore the previous source and reports rollback status.
- The VS Code workbench can load a project, display ontology content, create supported typed edits, preview changes, show diff and validation results, reject proposals, approve proposals, apply changes, and refresh.
- Existing Phase 1 and Phase 1.5 CLI commands still work.
- `./gradlew test`, `./gradlew build`, and `./gradlew check` pass.
- TypeScript extension verification commands pass once defined.
- No Phase 3 or later behavior is introduced.
- No Git workflow automation is introduced.
- A Phase 2 implementation summary documents the actual completed behavior.
