# Phase 2 Scope

Phase 2 is the Controlled Ontology Editing Workbench. It builds on the completed Phase 1 and Phase 1.5 semantic-engine foundation.

Phase 2 introduces safe ontology mutation, preview and approval workflows, source-file persistence, and a minimal VS Code user interface for viewing and editing small local Entio ontology projects.

## Phase 2 Goals

Phase 2 should provide the minimal product foundation for viewing and editing small ontology projects safely.

It should support:

- Opening a local Entio project in VS Code.
- Launching an Entio ontology workbench in a VS Code webview.
- Viewing ontology sources, classes, properties, individuals, and selected entity details.
- Creating controlled ontology edits through typed editing operations.
- Adding RDF triples.
- Removing RDF triples.
- Grouping multiple graph changes into one atomic change set.
- Previewing the resulting graph before any source file is changed.
- Generating a semantic diff before application.
- Validating the proposed graph before approval.
- Rejecting a proposal without altering the project.
- Applying an approved proposal atomically.
- Saving approved changes back to the correct Turtle source.
- Reloading the saved project and verifying semantic equivalence.
- Restoring the previous file and graph state if save or verification fails.
- Using a git-like proposal workflow for semantic changes: draft, preview, diff, review, approve, and apply.
- Writing only the files changed by an approved proposal.
- Leaving unrelated project files untouched.

Phase 2 should establish one controlled mutation pathway that can later be used by both human editors and Entio AI agents.

## Working Definitions

- ontology workbench: a VS Code webview that lets a user inspect and make controlled edits to an Entio ontology project without directly manipulating RDF triples for ordinary editing tasks.

- graph change: one explicit addition or removal of an RDF triple.

- change set: one or more graph changes that must be applied together or not at all.

- typed ontology edit: a user-facing ontology operation, such as creating a class or adding a superclass, that the Kotlin engine translates into one or more graph changes.

- change proposal: a draft change set with a target ontology source, baseline project state, semantic diff, validation result, status, and review metadata.

- preview graph: an in-memory graph produced by applying a proposed change set to the current graph without modifying the official ontology files.

- semantic approval: explicit user confirmation that the semantic diff and validation result are acceptable and that the proposal may be written to the project.

- atomic application: applying all approved graph changes successfully as one unit, or leaving the project unchanged if any part fails.

- semantic equivalence verification: reparsing the saved Turtle source and confirming that the resulting graph matches the approved preview graph.

- stale proposal: a proposal whose baseline file or graph state no longer matches the current project because the project changed after the preview was created.

- git-like semantic review workflow: an Entio proposal process that resembles Git review concepts such as diffs and approval, but does not require Entio to manage Git branches, staging, commits, pushes, or pull requests.

## Expected Project Concepts

Phase 2 may introduce product-specific concepts such as:

- Graph change objects.
- Change set objects.
- Change proposal objects.
- Change preview objects.
- Proposal status values.
- Proposal validation results.
- Proposal baseline fingerprints.
- Applied change results.
- Save and rollback results.
- Source file status.
- Change provenance metadata.
- Typed ontology editing commands.
- VS Code workbench requests and responses.

These should organize standards-based RDF and ontology changes. They should not replace RDF, OWL, SHACL, Turtle, or external source-control concepts.

## Backend Editing Capabilities

Phase 2 should support generic graph operations:

- Add a triple.
- Remove a triple.
- Apply a set of additions and removals to an in-memory graph.
- Detect duplicate additions.
- Detect removal of triples that do not exist.
- Generate the proposed graph.
- Generate a semantic diff between the current and proposed graph.
- Validate the proposed graph.
- Reject a proposal without mutating the current project.
- Apply an approved proposal atomically.

Phase 2 should also support a minimal set of typed ontology edits:

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

Typed ontology edits should be translated into graph changes by the Kotlin engine. The VS Code extension should not construct or mutate RDF independently.

## Preview, Validation, And Approval

Every ontology edit should follow this sequence:

1. The user creates or edits a proposal in the workbench.
2. Entio translates the user action into a typed ontology edit.
3. The Kotlin engine converts the edit into a change set.
4. The change set is applied to an in-memory preview graph.
5. Entio generates a semantic diff.
6. Entio validates the proposed graph.
7. Entio serializes the preview graph to temporary Turtle.
8. Entio reparses the temporary Turtle and verifies semantic equivalence.
9. The user reviews the semantic diff, validation result, target source, and file impact.
10. The user approves or rejects the proposal.
11. Only approved and still-current proposals may be written to the project.

Approval should be disabled if:

- A semantic diff cannot be generated.
- Validation fails under required Phase 2 rules.
- Temporary serialization or reparsing fails.
- Semantic equivalence verification fails.
- The proposal is stale.
- The target source changed after the preview was generated.

## Saving And Rollback

The save process should:

- Recheck the proposal baseline immediately before writing.
- Recheck the current graph and target file state.
- Write the approved ontology to a temporary file.
- Reparse the temporary file.
- Verify semantic equivalence with the approved preview.
- Atomically replace the original Turtle source only after verification succeeds.
- Reload the full Entio project.
- Confirm that the intended semantic diff is present.
- Confirm that no unexpected graph changes occurred.
- Restore the original source automatically if post-save verification fails.

A failed application should:

- Leave the original ontology file unchanged or restore it.
- Return a structured failure result.
- Record whether the prior state was restored successfully.

Phase 2 does not need permanent project version history. Safe temporary-file replacement and proposal-level rollback are sufficient.

## Git-Like Semantic Review Workflow

Phase 2 should not introduce an actual Git workflow inside Entio.

The workflow is git-like only in the product sense that users create proposed changes, inspect semantic diffs, approve or reject those changes, and then apply approved changes to project files.

Before applying an approved proposal, Entio should check:

- The target ontology file has not changed since the proposal preview was generated.
- The proposal baseline matches the current project state.
- The target file exists or is an explicitly approved new file.

Unrelated project file changes should not automatically block an ontology edit.

Entio should:

- Preserve unrelated modified files.
- Write only ontology files changed by the approved proposal.
- Never modify unrelated files.
- Record affected files, baseline hashes, resulting hashes, semantic diff status, and application status.
- Allow the user to review the semantic diff in VS Code before application.

Entio may coexist with a user's external Git workflow, but Phase 2 should not stage files, create commits, push branches, or open pull requests.

The expected proposal lifecycle is conceptually:

- Draft.
- Previewed.
- Verified.
- Ready for review.
- Rejected or approved.
- Applied.

Additional failure states may include:

- Verification failed.
- Stale.
- Apply failed.
- Rolled back.

## VS Code Workbench

Phase 2 should introduce a minimal TypeScript VS Code extension with an Entio webview.

The workbench should support:

- Detecting an Entio project in the active workspace.
- Launching the command `Entio: Open Ontology Workbench`.
- Showing project name and ontology sources.
- Showing loaded classes, properties, and individuals.
- Selecting an entity and viewing its details.
- Creating supported typed ontology edits.
- Showing the semantic diff before save.
- Showing validation results.
- Showing the target ontology file.
- Showing target file status.
- Approving or rejecting a proposal.
- Refreshing after the Turtle file changes.
- Opening the modified Turtle source in VS Code.

The workbench should prioritize:

- Class hierarchy navigation.
- Property navigation.
- Individual navigation.
- Selected-entity details.
- Focused editing forms.
- Change preview and approval.

A full whole-ontology graph editor is not required in Phase 2. A contextual graph or selected-entity neighborhood may be included if it remains small and does not expand the phase.

## Kotlin And TypeScript Boundary

The Kotlin semantic engine should own:

- Project loading.
- RDF and ontology interpretation.
- Graph change construction.
- Preview graph generation.
- Semantic diff generation.
- Validation.
- Proposal state checks.
- Turtle serialization.
- Atomic save and rollback.
- File baseline checks and safe application behavior.

The TypeScript extension should own:

- VS Code activation.
- Workspace and project detection.
- Webview rendering.
- User input forms.
- Calling the Kotlin engine.
- Displaying project, diff, validation, and file-impact results.
- Refreshing after file changes.

The TypeScript extension must not:

- Parse Turtle itself.
- Implement ontology validation rules.
- Construct RDF triples independently for ordinary editing operations.
- Write ontology files directly.
- Modify unrelated project files.

For Phase 2, communication may use structured JSON through CLI process calls. A local API server is not required.

## Non-Goals

Phase 2 should not include:

- Autonomous AI agents.
- LLM-generated ontology edits.
- Schema RAG.
- Document ingestion.
- Document-to-KG conversion.
- Entity resolution.
- Full domain ontology indexing.
- Production graph storage.
- Full Protégé feature parity.
- Full OWL class-expression editing.
- Cardinality restriction editing.
- Property-chain editing.
- Full OWL reasoning.
- Inference explanation.
- Full SHACL authoring or validation environment.
- Multi-user collaboration.
- Enterprise authentication or authorization.
- Long-term version history.
- Git staging, commits, pushes, or pull-request creation inside Entio.
- Source-text-preserving Turtle round trips.
- Full drag-and-drop graph editing.
- A separate desktop application.

## Success Criteria

Phase 2 is successful if a user can:

- Open a local Entio project in VS Code.
- Launch the Entio ontology workbench.
- View ontology sources, classes, properties, individuals, and selected entity details.
- Create a supported ontology edit.
- Preview the proposed graph without changing any source file.
- View a semantic diff.
- View deterministic validation and round-trip verification results.
- Reject the proposal with no project change.
- Approve a valid proposal.
- Have Entio confirm that the proposal is not stale.
- Save the complete change atomically to the correct Turtle source.
- Reload the project and verify semantic equivalence.
- Recover the prior source automatically if application or verification fails.
- Confirm that only the affected ontology source files changed.
- Continue using their own external source-control workflow outside Entio if desired.

Phase 2 is not expected to provide complete ontology-authoring parity with Protégé. It should establish the safe ontology mutation, approval, persistence, semantic review, and UI foundations that later Entio AI agents will use.
