# Feature Spec: Phase 2 Controlled Ontology Editing Workbench

## Status

Draft

## Problem

Phase 1 and Phase 1.5 established a Kotlin/JVM semantic-engine foundation for loading small local Entio projects, parsing Turtle/RDF ontology sources, preserving RDF term fidelity, extracting symbols, validating projects, producing semantic diffs, and exposing those capabilities through a thin CLI.

Entio still cannot safely edit ontology projects. Users can inspect loaded ontology content, but there is no controlled pathway for creating ontology changes, previewing their semantic impact, validating the proposed graph, approving or rejecting the proposal, and applying approved changes back to Turtle source files.

Directly editing Turtle files remains possible, but it bypasses Entio's core product promise: ontology-first, deterministic, human-reviewable graph changes. Phase 2 should introduce a minimal controlled ontology editing workbench that lets users make small ontology changes through typed operations while preserving deterministic validation and semantic review.

Phase 2 should also avoid overstating source-control behavior. Entio should support a git-like semantic review workflow in the product sense: draft, preview, diff, review, approve, and apply. It should not manage Git branches, staging, commits, pushes, or pull requests.

## Goals

- Add a controlled mutation pathway for small local ontology projects.
- Represent graph additions and removals as explicit Entio change objects.
- Group graph changes into atomic change sets.
- Represent human-reviewable change proposals.
- Translate supported typed ontology edits into graph changes in the Kotlin semantic engine.
- Generate an in-memory preview graph without changing source files.
- Generate a semantic diff between the current graph and preview graph.
- Validate the preview graph before approval.
- Serialize the approved preview graph to Turtle through established RDF tooling.
- Reparse temporary serialized Turtle and verify semantic equivalence before replacing project files.
- Apply approved proposals atomically to the correct ontology source file.
- Restore the prior source file and graph state if application or verification fails.
- Detect stale proposals whose baseline no longer matches the current project.
- Preserve unrelated project files.
- Introduce a minimal VS Code workbench for inspecting ontology projects and approving controlled edits.
- Keep the TypeScript VS Code layer thin and dependent on the Kotlin semantic engine for semantic behavior.
- Keep the workflow deterministic and test-covered.

## Non-Goals

- No autonomous AI agents.
- No LLM-generated ontology edits.
- No Schema RAG.
- No document ingestion.
- No document-to-KG conversion.
- No entity resolution.
- No full domain ontology indexing.
- No production graph storage.
- No full Protégé feature parity.
- No full OWL class-expression editing.
- No cardinality restriction editing.
- No property-chain editing.
- No full OWL reasoning.
- No inference explanation.
- No full SHACL authoring or validation environment.
- No multi-user collaboration.
- No enterprise authentication or authorization.
- No long-term project version history.
- No Git staging, commits, pushes, branch management, or pull-request creation inside Entio.
- No source-text-preserving Turtle round trips.
- No full drag-and-drop graph editing.
- No separate desktop application.
- No custom RDF, OWL, SHACL, Turtle, or Git framework.

## Proposed Behavior

Phase 2 should introduce one controlled ontology-editing pathway.

A user should be able to open a local Entio project in VS Code, launch the Entio ontology workbench, inspect ontology sources and loaded symbols, create a supported typed ontology edit, preview the resulting graph, review the semantic diff and validation result, reject the proposal with no source-file changes, or approve the proposal and apply it safely to the target Turtle source.

The workflow should be:

1. The workbench loads a local Entio project through the Kotlin semantic engine.
2. The user creates a typed ontology edit.
3. The Kotlin engine translates the typed edit into one or more graph changes.
4. Entio groups graph changes into a change set.
5. Entio creates a change proposal with a baseline fingerprint of the target project and ontology source.
6. Entio applies the change set to an in-memory preview graph.
7. Entio generates a semantic diff between the current graph and preview graph.
8. Entio validates the preview graph.
9. Entio serializes the preview graph to temporary Turtle.
10. Entio reparses the temporary Turtle and verifies semantic equivalence with the preview graph.
11. The user reviews the semantic diff, validation result, target source, and file impact.
12. The user approves or rejects the proposal.
13. Entio applies only approved, valid, and current proposals.
14. Entio writes only affected ontology source files.
15. Entio reloads the project and verifies that the saved graph matches the approved preview.
16. Entio restores the prior source if save or verification fails.

The proposal lifecycle should include at least:

- Draft.
- Previewed.
- Verified.
- Ready for review.
- Rejected.
- Approved.
- Applied.
- Verification failed.
- Stale.
- Apply failed.
- Rolled back.

The workflow may resemble Git review concepts because users inspect semantic diffs before applying changes, but Entio should not implement a Git workflow.

## Inputs And Outputs

### Inputs

Phase 2 should continue to use the local project shape established by Phase 1 and Phase 1.5:

```text
project-root/
  entio.yaml
  ontology/
    simple.ttl
```

Minimal config remains:

```yaml
name: simple-ontology
ontologySources:
  - id: simple
    path: ontology/simple.ttl
    format: turtle
```

Phase 2 inputs include:

- A local project root.
- Existing Entio project configuration.
- Existing Turtle ontology sources.
- A selected ontology source for a proposed edit.
- A supported typed ontology edit.
- User approval or rejection.

Supported typed ontology edits should include:

- Create a class.
- Change or add an entity label.
- Add a superclass relationship.
- Remove a superclass relationship.
- Create an object property.
- Create a datatype property.
- Set a property domain.
- Set a property range.
- Create an individual.
- Assign a type to an individual.
- Add an object-property assertion.
- Add a datatype-property assertion.

Generic graph-change inputs should include:

- Add a triple.
- Remove a triple.
- Apply a set of additions and removals together.

### Outputs

Phase 2 should produce structured outputs that future interfaces can consume:

- Loaded project state.
- Ontology source summaries.
- Symbol lists.
- Selected entity details.
- Graph change objects.
- Change set objects.
- Change proposal objects.
- Preview graph results.
- Semantic diff results.
- Validation reports.
- Temporary serialization verification results.
- Proposal application results.
- Rollback results when application fails.
- VS Code workbench request and response payloads.

The VS Code workbench should display:

- Project name.
- Ontology source list.
- Classes, properties, and individuals.
- Selected entity details.
- Edit forms for supported typed edits.
- Semantic diff.
- Validation result.
- Target ontology source.
- Target file status.
- Approval and rejection actions.

## Core Data Contracts

Phase 2 should introduce or refine Entio-specific contracts for controlled editing:

- `GraphChange`: one addition or removal of an RDF triple.
- `GraphChangeKind`: addition or removal.
- `ChangeSet`: one or more graph changes that must be applied together.
- `TypedOntologyEdit`: a supported user-facing ontology edit.
- `ChangeProposal`: a draft change set with target source, baseline, preview, diff, validation result, status, and review metadata.
- `ChangeProposalStatus`: fixed proposal lifecycle states.
- `ChangePreview`: the result of applying a change set to an in-memory graph.
- `ProposalBaseline`: file and graph fingerprints used to detect stale proposals.
- `SemanticEquivalenceResult`: result of serializing, reparsing, and comparing preview graph state.
- `ApplyProposalResult`: successful or failed application result.
- `RollbackResult`: whether Entio restored the prior file and graph state after failure.
- `SourceFileImpact`: files expected to change during proposal application.
- `WorkbenchRequest` and `WorkbenchResponse`: structured boundary objects for the VS Code layer.

These contracts should organize Entio workflows around existing RDF terms and graph states. They should not replace RDF, OWL, SHACL, Turtle, or external source-control concepts.

## Validation Behavior

Phase 2 should preserve Phase 1 and Phase 1.5 validation behavior and add proposal-specific deterministic checks.

Existing validation should still cover:

- Project root exists and is a directory.
- `entio.yaml` exists.
- `entio.yaml` is valid YAML.
- Required config fields are present.
- Ontology source IDs are unique.
- Ontology paths are relative, normalized, and remain under the project root.
- Referenced ontology files exist.
- Ontology formats are supported.
- Turtle files parse successfully.
- Loaded symbols can be extracted without crashing.

Proposal validation should check:

- The change set is not empty.
- The target ontology source exists or is an explicitly approved new file.
- Every triple addition is representable by the Phase 1.5 RDF term model.
- Duplicate additions are detected deterministically.
- Removing a triple that does not exist is detected deterministically.
- Typed ontology edits produce valid graph changes.
- The preview graph can be generated.
- The semantic diff can be generated.
- The preview graph passes required deterministic validation.
- The preview graph can be serialized to temporary Turtle.
- The temporary Turtle can be reparsed.
- The reparsed temporary graph is semantically equivalent to the preview graph.
- The proposal baseline still matches the current project before application.
- Only the affected ontology source files are modified during application.

Approval should be disabled when:

- Proposal validation fails.
- Preview graph generation fails.
- Semantic diff generation fails.
- Temporary serialization or reparsing fails.
- Semantic equivalence verification fails.
- The proposal is stale.
- The target source changed after preview generation.

## Error Behavior

Expected failures should return structured results instead of unhandled exceptions.

Phase 2 should return deterministic failures for:

- Unsupported typed ontology edits.
- Invalid RDF terms.
- Duplicate triple additions.
- Missing triples requested for removal.
- Missing or stale target ontology sources.
- Config loading failure.
- Ontology parsing failure.
- Symbol extraction failure.
- Validation failure.
- Preview generation failure.
- Semantic diff failure.
- Temporary Turtle serialization failure.
- Temporary Turtle reparse failure.
- Semantic equivalence verification failure.
- Atomic file replacement failure.
- Post-save reload failure.
- Post-save semantic verification failure.
- Rollback failure.

When application fails after touching a source file, Entio should attempt to restore the prior source automatically and return a result that records whether restoration succeeded.

## VS Code Workbench Behavior

Phase 2 should introduce a minimal TypeScript VS Code extension with an Entio webview.

The VS Code extension should:

- Detect an Entio project in the active workspace.
- Launch the command `Entio: Open Ontology Workbench`.
- Render the workbench in a VS Code webview.
- Call the Kotlin semantic engine for loading, preview, diff, validation, and application.
- Display structured engine results.
- Refresh after Turtle file changes.
- Open the modified Turtle source in VS Code.

The VS Code extension must not:

- Parse Turtle itself.
- Implement ontology validation rules.
- Construct RDF triples independently for ordinary editing operations.
- Write ontology files directly.
- Modify unrelated project files.

For Phase 2, communication may use structured JSON through CLI process calls. A local API server is not required.

## Test Cases

- The engine creates a graph-change addition for a valid RDF triple.
- The engine creates a graph-change removal for an existing RDF triple.
- The engine rejects an empty change set.
- The engine detects duplicate triple additions.
- The engine detects removal of a missing triple.
- A create-class typed edit produces the expected RDF type triple.
- A label edit produces the expected label triple.
- A superclass edit produces the expected subclass triple.
- Object-property and datatype-property edits produce expected RDF type triples.
- Domain and range edits produce expected triples.
- Individual creation and type assignment produce expected triples.
- Object-property and datatype-property assertions produce expected triples.
- Applying a change set to a preview graph does not mutate the original graph.
- Preview graph generation is deterministic.
- Semantic diff generation reports added triples.
- Semantic diff generation reports removed triples.
- Semantic diff generation reports label changes where applicable.
- Proposal validation blocks invalid previews.
- Proposal validation blocks stale baselines.
- Temporary Turtle serialization can be reparsed.
- Reparsed temporary Turtle is semantically equivalent to the preview graph.
- Approved proposal application writes only the target ontology source.
- Rejected proposal does not change source files.
- Application failure restores the prior source when possible.
- Post-save reload confirms the intended semantic diff is present.
- Post-save reload detects unexpected graph changes.
- The VS Code workbench can load project summary data from the engine.
- The VS Code workbench can request preview, diff, validation, approve, reject, and refresh actions.
- The TypeScript layer does not contain RDF parsing or validation logic.
- Existing Phase 1 and Phase 1.5 CLI behavior continues to pass.

## Acceptance Criteria

- Phase 2 has a documented and implemented controlled ontology mutation pathway.
- Users can open a local Entio project in VS Code.
- Users can launch an Entio ontology workbench.
- Users can view ontology sources, classes, properties, individuals, and selected entity details.
- Users can create supported typed ontology edits.
- The Kotlin engine translates typed edits into graph changes.
- The engine can preview proposed graph changes without modifying source files.
- The engine produces semantic diffs for proposed changes.
- The engine validates proposed graphs before approval.
- The engine verifies temporary Turtle serialization and reparsing before application.
- Users can reject proposals without changing the project.
- Users can approve valid proposals.
- Approved proposals are applied atomically to the correct Turtle source.
- Entio detects stale proposals before application.
- Entio restores the prior source if save or verification fails.
- Entio modifies only affected ontology source files.
- The workflow is git-like by analogy only and does not stage, commit, push, or create pull requests.
- VS Code behavior stays thin and delegates semantic work to the Kotlin engine.
- `./gradlew test` passes.
- `./gradlew build` passes.
- `./gradlew check` passes.
- No Phase 3 or later product behavior is introduced.

## Open Questions

- Should Phase 2 serialize an entire target ontology source after applying a proposal, or should it attempt smaller source-file updates while accepting that source-text preservation is not guaranteed?
- How should Entio choose the target ontology source when a typed edit references symbols from multiple sources?
- Should Phase 2 support creating a new ontology source file, or only editing an existing source?
- What exact JSON boundary should the VS Code extension use when invoking Kotlin engine behavior?
- Should the VS Code workbench call the existing CLI, a dedicated engine command, or a small process wrapper?
- How much selected-entity neighborhood visualization is useful without expanding into a full graph editor?
- Should proposal metadata be kept only in memory for Phase 2, or persisted temporarily in a local project cache?
- What minimum validation rules should block approval in Phase 2 beyond parser and graph consistency checks?
- How should blank-node identity be displayed in proposal diffs without implying durable semantic identity?
- Should Phase 2 expose a command for machine-readable JSON semantic diffs before the VS Code workbench depends on them?
