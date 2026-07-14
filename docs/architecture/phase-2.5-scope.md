# Phase 2.5 Scope

Phase 2.5 completes the user-facing ontology editing work that Phase 2 originally intended to provide.

Phase 2.5 is complete. Phase 2.5+ subsequently added label-first usability, deterministic IRI generation, deletion dependency review, and combined staged changes.

Phase 2 already implemented the controlled proposal workflow, semantic diff generation, validation, round-trip verification, safe Turtle saving, rollback, a minimal VS Code workbench, and one complete user-facing edit flow for creating a class.

Phase 2.5 should extend that same safe workflow to the remaining ontology edit types that already exist, fully or partially, in the Kotlin typed-edit contracts.

## Phase 2.5 Goals

Phase 2.5 should make the Phase 2 workbench useful for basic ontology editing rather than only demonstrating class creation.

It should support the following user-facing edit operations through both the machine-readable CLI boundary and the VS Code workbench:

- Create an object property.
- Create a datatype property.
- Set or change a property domain.
- Set or change a property range.
- Create an individual.
- Assign a class/type to an individual.
- Add an object-property assertion between individuals.
- Add a datatype-property value to an individual.
- Add a superclass relationship.
- Remove a superclass relationship.
- Add or change an entity label where supported by the existing typed-edit model.

Every edit must use the existing Phase 2 proposal workflow:

1. Build the proposal.
2. Preview the resulting graph in memory.
3. Generate a semantic diff.
4. Run deterministic validation.
5. Verify temporary Turtle serialization and reparsing.
6. Allow the user to approve or reject.
7. Apply only approved, current proposals.
8. Save atomically.
9. Reload and verify the saved graph.
10. Restore the prior source if verification fails.

Phase 2.5 should not create a second editing pathway. All new edit forms must reuse the existing proposal, validation, persistence, and rollback behavior.

## Working Definitions

- completed edit flow: an ontology edit that is available through the Kotlin engine, machine-readable CLI, and VS Code workbench, and supports preview, validation, approval, rejection, application, refresh, and source opening.

- object property: a relationship whose value is another resource or individual.

- datatype property: a property whose value is a literal such as text, a number, a date, or a boolean.

- property domain: the class or classes whose instances may use a property.

- property range: the class, datatype, or value type expected as the property value.

- individual: a specific instance of one or more ontology classes.

- object-property assertion: a relationship between two individuals or resources.

- datatype-property assertion: a literal value attached to an individual or resource.

- superclass relationship: a statement that one class is a more specific kind of another class.

## Expected Project Concepts

Phase 2.5 may introduce or refine:

- CLI request and response payloads for each edit kind.
- Workbench form models for each edit kind.
- Edit-specific validation messages.
- Edit-specific semantic diff summaries.
- Property-type selection.
- Class, property, datatype, and individual selection controls.
- Literal value and datatype input models.
- Reusable workbench proposal components.
- Reusable CLI proposal preparation logic.
- Reusable end-to-end test fixtures for supported edits.

These concepts should reuse the Phase 2 typed-edit and proposal contracts rather than duplicate them.

## Required User-Facing Edit Flows

### Create Object Property

The user should be able to provide:

- Target ontology source.
- Property IRI or local name.
- Label.
- Optional domain.
- Optional range.

The preview should show the property declaration and any domain, range, or label triples.

### Create Datatype Property

The user should be able to provide:

- Target ontology source.
- Property IRI or local name.
- Label.
- Optional domain.
- Datatype range.

The initial supported datatype list should be intentionally small and may include:

- `xsd:string`
- `xsd:boolean`
- `xsd:integer`
- `xsd:decimal`
- `xsd:date`
- `xsd:dateTime`

### Set Property Domain

The user should be able to:

- Select an object or datatype property.
- Select a class as its domain.
- Preview the added or changed domain statement.
- Remove or replace an existing domain only through an explicit proposal.

### Set Property Range

For object properties, the user should be able to select a class range.

For datatype properties, the user should be able to select a supported datatype range.

The UI should not allow obviously incompatible range choices.

### Create Individual

The user should be able to provide:

- Target ontology source.
- Individual IRI or local name.
- Optional label.
- One or more class/type assignments where supported.

At minimum, one class/type assignment should be supported in the initial workbench form.

### Assign Individual Type

The user should be able to:

- Select an existing individual.
- Select an existing class.
- Preview the new type assertion.
- Reject duplicate type assertions.

### Add Object-Property Assertion

The user should be able to:

- Select a subject individual.
- Select an object property.
- Select an object individual or resource.
- Preview the new relationship.

Validation should check, where possible with current Phase 2 semantics:

- The selected property exists.
- The property is an object property.
- The subject and object exist.
- The assertion is not already present.
- The assertion is compatible with known domain and range declarations.

### Add Datatype-Property Assertion

The user should be able to:

- Select a subject individual.
- Select a datatype property.
- Enter a value.
- Select or infer a supported datatype.
- Optionally provide a language tag for string values if supported by the existing RDF contracts.

Validation should check:

- The selected property exists.
- The property is a datatype property.
- The entered value can be represented by the selected datatype.
- The assertion is not already present.
- The value is compatible with a known property range.

### Add Superclass Relationship

The user should be able to:

- Select an existing child class.
- Select an existing parent class.
- Preview the new `rdfs:subClassOf` statement.

Validation should reject:

- A class being made a subclass of itself.
- Duplicate superclass relationships.
- Clearly invalid selections.

Cycle detection may be included if it can be implemented deterministically without expanding into full OWL reasoning.

### Remove Superclass Relationship

The user should be able to:

- Select an existing direct superclass relationship.
- Preview the removed statement.
- Apply the removal through the same approval flow.

The UI should only offer asserted relationships that currently exist in the selected target source.

### Add Or Change Entity Label

Where supported by the existing typed-edit contracts, the user should be able to:

- Select an entity.
- Add a label.
- Replace a selected existing label.
- Optionally provide a language tag.

A replacement should be represented as an explicit removal and addition so the semantic diff is clear.

## CLI Requirements

Phase 2.5 should extend the existing machine-readable CLI boundary.

The CLI should support proposal preparation, validation, diff, apply, and reject behavior for every Phase 2.5 edit kind.

The implementation may either:

- Add edit-specific CLI options to the current proposal commands, or
- Accept one structured JSON edit request through a common proposal command.

Prefer the approach that:

- Keeps the CLI thin.
- Avoids duplicate parsing logic.
- Reuses the typed-edit translator.
- Produces stable machine-readable output.
- Is easy for the VS Code extension to call and test.

The CLI should continue to delegate all semantic behavior to Kotlin engine services.

## VS Code Workbench Requirements

The workbench should add focused forms for all required Phase 2.5 edit types.

The interface should support:

- Choosing the edit type.
- Choosing the target ontology source.
- Selecting existing classes, properties, and individuals.
- Entering IRIs, labels, literal values, datatypes, and language tags where applicable.
- Showing field-level validation before proposal submission where practical.
- Showing the semantic diff and validation report returned by the engine.
- Approving or rejecting.
- Refreshing project state after a successful application.
- Opening the changed Turtle source.

The workbench should reuse shared proposal preview and result components rather than create a separate preview screen for each edit type.

The workbench must not:

- Parse Turtle itself.
- Create RDF triples independently.
- Write ontology source files directly.
- Bypass proposal validation or approval.
- Add editing behavior that does not exist in the Kotlin typed-edit layer.

## Validation Expectations

Phase 2.5 should use the strongest deterministic checks supported by the current engine without introducing full reasoning.

It should validate:

- Required fields.
- Valid IRIs.
- Target source existence.
- Referenced entity existence.
- Correct selected entity kind.
- Duplicate additions.
- Removal of missing statements.
- Literal datatype compatibility.
- Known property domain compatibility.
- Known property range compatibility.
- Self-subclass relationships.
- Proposal baseline freshness.
- Temporary Turtle round-trip equivalence.

Validation should produce structured issues that the CLI and workbench can display consistently.

## Saving, Approval, And Rollback

Phase 2.5 must preserve the Phase 2 rule:

> No ontology source file is changed unless a semantic diff has been generated, the preview graph has been validated and round-trip verified, and the proposal has been approved.

All new edit types must use the existing atomic save process:

- Recheck the proposal baseline.
- Write to a temporary Turtle file.
- Reparse and verify.
- Replace only the target source.
- Reload the project.
- Confirm the saved graph matches the approved preview.
- Restore the prior source if verification fails.

Rejection must leave the project unchanged.

## Testing Requirements

Each user-facing edit flow should include:

- Typed-edit translation tests.
- CLI request parsing tests.
- Machine-readable response tests.
- Preview tests.
- Semantic diff tests.
- Validation success tests.
- Validation failure tests.
- Rejection tests.
- Successful application tests.
- Stale-proposal tests where relevant.
- Round-trip persistence tests.
- VS Code extension form and process-adapter tests.
- Refresh behavior after successful application.

End-to-end tests should use temporary copies of small committed fixtures and must not modify committed example files.

## Non-Goals

Phase 2.5 should not include:

- New AI or LLM behavior.
- Schema RAG.
- Document ingestion.
- Document-to-KG conversion.
- Entity resolution.
- Autonomous agents.
- Full OWL class-expression editing.
- Equivalent-class editing.
- Disjoint-class editing.
- Property characteristics such as transitive, symmetric, or functional.
- Inverse-property editing.
- Property chains.
- Cardinality restrictions.
- Full OWL reasoning.
- Inference explanation.
- Full SHACL authoring or validation.
- Source-text-preserving Turtle editing.
- Long-term proposal persistence.
- Project version history.
- Actual Git staging, commits, pushes, branches, or pull requests inside Entio.
- Multi-user collaboration.
- A separate desktop application.
- A full drag-and-drop ontology graph editor.

## Success Criteria

Phase 2.5 is successful if a user can complete the following through the VS Code workbench:

- Create an object property.
- Create a datatype property.
- Set or change a property domain.
- Set or change a property range.
- Create an individual.
- Assign a class/type to an individual.
- Add an object-property assertion.
- Add a datatype-property value.
- Add a superclass relationship.
- Remove a superclass relationship.
- Add or change an entity label where included in the approved spec.

For every supported edit, the user must be able to:

- Preview the proposed graph without changing the source.
- See the semantic diff.
- See validation and round-trip verification results.
- Reject the proposal with no project change.
- Approve a valid proposal.
- Apply the proposal atomically.
- Reload the project and see the result.
- Recover the prior source automatically if save verification fails.
- Confirm that only the intended ontology source changed.

Phase 2.5 is not expected to add new ontology-editing depth beyond the Phase 2 typed-edit contracts. Its purpose is to finish exposing the originally intended Phase 2 editing capabilities consistently through the CLI and VS Code workbench.
