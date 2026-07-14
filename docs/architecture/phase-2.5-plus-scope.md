# Phase 2.5+ Scope

Phase 2.5+ completes the next layer of usability for the Entio ontology workbench.

Phase 2.5+ is complete. The implemented result includes label-first selection, deterministic collision-checked IRI generation, explicit deletion dependency review, multi-edit staging, combined proposal review, and copied-fixture regression coverage. The detailed requirements below remain the scope record for that completed work.

Phase 2.5 already supports creating and editing basic ontology elements through a controlled proposal workflow. It can preview changes, generate semantic diffs, validate proposed graphs, verify Turtle round trips, apply approved changes atomically, reload the project, and roll back failed saves.

Phase 2.5+ should improve how users identify ontology elements, add deletion support, and allow several edits to be staged and reviewed together before application.

## Phase 2.5+ Goals

Phase 2.5+ should support:

- Editing ontology elements primarily by human-readable labels rather than by manually entered IRIs.
- Generating a deterministic unique IRI for every newly created class, property, or individual.
- Showing the generated IRI in the selected item’s details.
- Preventing accidental IRI collisions.
- Deleting supported ontology elements through the existing proposal workflow.
- Deleting classes.
- Deleting object properties.
- Deleting datatype properties.
- Deleting individuals.
- Removing related assertions or references only through explicit, reviewable change proposals.
- Staging multiple edits before applying them.
- Showing all staged edits in one staged-change list.
- Clearing the edit form after a change is successfully previewed and staged.
- Editing a specific staged change.
- Refilling the edit form when the user chooses to edit a staged change.
- Revalidating an edited staged change before returning it to the staged list.
- Generating one combined preview, semantic diff, validation result, and save operation for the complete staged change set.
- Applying all approved staged edits atomically.
- Leaving the project unchanged if the combined change set fails verification or is rejected.

Phase 2.5+ should continue using the existing Kotlin semantic engine as the single source of truth for ontology behavior. It should not create a separate editing or persistence pathway.

## Working Definitions

- label-first editing: a user workflow in which ontology elements are selected, created, and displayed primarily by label, while IRIs remain available as technical details.

- generated IRI: a unique ontology identifier created by Entio from deterministic project and item information rather than manually entered by the user.

- IRI namespace: the configured base IRI used when Entio creates identifiers for new local ontology elements.

- IRI local name: the deterministic identifier appended to the configured namespace.

- deletion proposal: a controlled change proposal that removes an ontology element and any explicitly approved dependent triples.

- dependent reference: a graph statement that refers to the element being deleted, such as a subclass relationship, domain, range, type assignment, assertion, or label.

- staged change: a successfully previewed and individually validated edit that has not yet been applied to the ontology source.

- staged change set: the ordered collection of staged changes that will be combined into one final preview and atomic application.

- edit staged change: reopening one staged change in the edit form, temporarily removing it from the staged list, changing its values, previewing it again, and returning the verified version to the staged list.

- combined preview: the proposed graph produced by applying all staged changes in order to the current project graph without changing the source files.

## Label-First Editing

All user-facing selectors, forms, staged-change summaries, previews, and entity navigation should use labels as the primary display value.

The workbench should not require ordinary users to type IRIs for existing ontology elements.

For existing items, controls should display:

- Preferred label.
- Entity kind.
- Source ontology where needed to disambiguate.
- Short technical identifier only when necessary.

When two items have the same label, the UI should disambiguate them using one or more of:

- Entity kind.
- Source ontology.
- Parent class.
- Namespace.
- Full IRI in a secondary detail line.

The full IRI should remain visible when the user selects or opens an ontology item.

The entity details panel should show at least:

- Label.
- Full IRI.
- Entity kind.
- Source ontology.
- Direct superclass, domain, range, or type where relevant.
- Statements involving the entity where supported by the current workbench.

Machine-readable CLI and Kotlin boundaries may continue to use IRIs internally. Label-first behavior is a user-interface and request-resolution requirement, not a replacement for RDF identity.

## Deterministic IRI Generation

New ontology elements should receive Entio-generated IRIs by default.

The user should normally provide:

- Label.
- Entity kind.
- Target ontology source.
- Any edit-specific details.

Entio should generate the IRI deterministically from an approved namespace and normalized label.

The exact formula should be defined in the Phase 2.5+ specification, but it should follow this model:

```text
generated IRI =
configured namespace
+
normalized local name
+
deterministic collision suffix when required
```

The normalized local name should be deterministic and stable for the same input. It should:

- Trim surrounding whitespace.
- Normalize internal whitespace.
- Remove or transform unsupported characters.
- Use an approved casing convention.
- Avoid empty identifiers.
- Avoid reserved or invalid local names.

A suitable initial convention may be UpperCamelCase for classes and individuals, and lowerCamelCase for properties, but the final convention must be explicitly approved in the specification.

Examples:

```text
Label: Commercial Loan
Class IRI: https://example.com/ontology#CommercialLoan

Label: has borrower
Object property IRI: https://example.com/ontology#hasBorrower
```

IRI generation must check the current project before finalizing the identifier.

If the generated IRI already exists:

- If the existing entity represents the same intended item, Entio should reject duplicate creation or direct the user to the existing item.
- If the label is the same but a distinct new entity is explicitly required, Entio should generate a deterministic collision suffix according to the approved formula.
- Entio must not use random IDs.
- Entio must not silently overwrite an existing entity.

The project configuration may need a local namespace or base IRI setting if one does not already exist. The specification and ExecPlan should define how the namespace is selected and validated.

The generated IRI should be shown:

- In the preview.
- In the staged-change details.
- In the selected entity’s detail panel.
- In validation messages when relevant.

Phase 2.5+ does not need to support arbitrary manual IRI entry as the main workflow. An advanced override may be deferred unless explicitly approved.

## Deletion Capabilities

Phase 2.5+ should support user-facing deletion of:

- Classes.
- Object properties.
- Datatype properties.
- Individuals.

Deletion must use the existing proposal, preview, validation, approval, save, and rollback pathway.

A deletion must not immediately remove an item from the source file.

The workflow should be:

1. The user selects an ontology item by label.
2. The user chooses Delete.
3. Entio finds all explicit graph statements that define or reference that item.
4. Entio presents the direct deletion and affected references.
5. The user chooses the approved deletion behavior.
6. Entio creates a deletion proposal.
7. The deletion is previewed and validated.
8. The user stages, rejects, or applies it through the normal workflow.

### Direct Deletion

Direct deletion should remove the triples that define the selected element, such as:

- Its RDF or OWL type declaration.
- Its label and annotations where included in scope.
- Its direct hierarchy, domain, range, type, or assertion statements where the selected item is the subject.

### Referenced-By Handling

If other graph statements refer to the selected item, Entio should not silently remove them.

Examples include:

- A class used as another class’s superclass.
- A class used as a property domain or range.
- A class used as an individual’s type.
- A property used in assertions.
- An individual used as the object of another assertion.

The user should see a dependency summary before the deletion can be staged.

The initial supported behaviors should be defined in the specification and may include:

- Block deletion until references are removed separately.
- Delete the item and explicitly selected dependent statements together.
- Replace references with another existing item before deletion.

Phase 2.5+ should prefer safe blocking over silent cascading when behavior is ambiguous.

Deletion validation should reject:

- Deletion of an item that does not exist.
- Deletion from the wrong source where source ownership matters.
- Deletion with unresolved dependent references.
- Deletion based on a stale project baseline.
- Deletion that creates an invalid combined staged graph under current deterministic rules.

Deletion does not need to implement full OWL dependency reasoning. It should operate on explicit graph statements available to the current engine.

## Multiple Staged Edits

Phase 2.5+ should allow a user to prepare multiple edits before applying them.

The workbench should maintain a staged-change list for the current editing session.

Each staged change should show:

- Human-readable summary.
- Edit kind.
- Main labels involved.
- Generated or existing IRIs in secondary details.
- Target ontology source.
- Individual validation status.
- Edit action.
- Remove-from-stage action.

The user should be able to stage supported create, update, relationship, assertion, label, and delete edits.

### Staging Flow

The expected flow is:

1. The user fills in an edit form.
2. The user clicks `Preview Change`.
3. Entio translates and validates the individual edit.
4. If preview succeeds, the edit is added to the staged-change list.
5. The entry fields are cleared.
6. The user may prepare another edit.
7. The project source remains unchanged.

If preview fails:

- The edit is not staged.
- The form remains populated.
- Validation errors are shown.
- The user can correct and retry.

### Editing A Staged Change

Every staged change should have an Edit button.

When the user clicks Edit:

1. The staged change is loaded back into the corresponding edit form.
2. The form fields are populated with the staged values.
3. The change is temporarily removed or marked as being edited so it is not included twice.
4. The user changes the form values.
5. The user clicks `Preview Change`.
6. Entio validates the revised edit.
7. If successful, the revised version returns to the staged list in the appropriate position.
8. The form fields are cleared again.

If the revised preview fails:

- The edit remains in editing state.
- The form remains populated.
- The invalid revision is not added to the staged list.
- The user can continue editing or cancel.

The user should be able to cancel staged-change editing and restore the prior staged version.

### Removing A Staged Change

The user should be able to remove a staged change before final application.

Removing a staged change:

- Removes it from the staged list.
- Does not alter any ontology source.
- Causes the combined preview to be regenerated when needed.

## Combined Preview And Application

Individual staged changes are not official project changes.

Before application, Entio must combine all staged changes into one ordered change set.

The combined workflow should:

1. Reload or confirm the current project baseline.
2. Apply staged changes in deterministic order to an in-memory graph.
3. Detect conflicts between staged edits.
4. Generate one combined preview graph.
5. Generate one combined semantic diff.
6. Run deterministic validation against the final proposed graph.
7. Serialize the complete preview to temporary Turtle.
8. Reparse it and verify semantic equivalence.
9. Show the combined result to the user.
10. Allow approval or rejection of the complete staged set.
11. Apply all staged changes atomically if approved.

Examples of staged conflicts include:

- Creating and deleting the same item.
- Creating duplicate entities that generate the same IRI.
- Deleting a class and then assigning an individual to that class.
- Removing a superclass relationship that another staged edit assumes exists.
- Setting incompatible property types or ranges.
- Referring to an entity that another staged edit removes.

The staged-change order should be deterministic and visible where order affects behavior.

The final application must preserve the existing Phase 2 rule:

> No ontology source is changed unless a semantic diff has been generated, the final preview graph has been validated and round-trip verified, and the complete staged change set has been approved.

If any staged change fails combined validation:

- Nothing is written.
- The user sees which staged changes contributed to the failure.
- The staged list remains available for correction.

If final application or verification fails:

- The prior source is preserved or restored.
- No partial subset of staged edits remains applied.
- The failure and rollback result are returned structurally.

After a successful application:

- The project reloads.
- The workbench refreshes.
- The staged list is cleared.
- The changed source can be opened in VS Code.

## CLI Requirements

Phase 2.5+ should extend the machine-readable CLI boundary without moving semantic behavior into CLI command code.

The CLI should support:

- Resolving existing entities from label-backed workbench selections to IRIs.
- Requesting deterministic IRI generation for new entities.
- Previewing deletion proposals.
- Returning dependency information for deletion.
- Previewing individual staged edits.
- Previewing a combined staged change set.
- Validating a combined staged change set.
- Applying the complete approved staged set.
- Rejecting without writing.

A structured JSON request is preferred for multi-edit staging because command-line flags become unwieldy for multiple heterogeneous edits.

The request should preserve:

- Edit type.
- Form values.
- Resolved IRIs.
- Generated IRIs.
- Target source.
- Staged order.
- Baseline information.

The CLI should return:

- Normalized staged edits.
- Generated identifiers.
- Individual and combined validation results.
- Combined semantic diff.
- Affected source files.
- Application and rollback results.

## VS Code Workbench Requirements

The workbench should be updated so that:

- Existing items are selected by label-backed controls.
- Full IRIs appear in item details and secondary preview information.
- New-item forms ask for labels instead of mandatory IRI text.
- Generated IRIs are shown after preview.
- Delete actions are available for supported item kinds.
- Dependency information is shown before deletion can be staged.
- A staged-change list is visible.
- Each staged change has Edit and Remove actions.
- Successful `Preview Change` clears the current form.
- Editing a staged change restores its form values.
- Re-previewing a revised staged change returns it to the staged list.
- A final `Preview All Changes` action produces the combined diff and validation result.
- A final approval action applies the complete staged set.
- A rejected combined proposal leaves all source files unchanged.
- The workbench refreshes after successful application.

The workbench should reuse existing preview, validation, application, refresh, and source-opening components where possible.

## Kotlin And TypeScript Boundary

The Kotlin engine should own:

- Label-to-IRI resolution.
- Deterministic IRI generation.
- Collision detection.
- Entity existence and kind checks.
- Deletion dependency analysis.
- Deletion graph-change generation.
- Staged change normalization.
- Combined change-set creation.
- Conflict detection.
- Combined preview generation.
- Semantic diff generation.
- Validation.
- Turtle round-trip verification.
- Atomic application and rollback.

The TypeScript extension should own:

- Label-first controls and rendering.
- Form state.
- Staged-list state for the active workbench session.
- Reopening a staged change into its form.
- Clearing forms after successful preview.
- Calling the CLI.
- Displaying generated IRIs, dependencies, diffs, validation, and application results.
- Refreshing after successful application.

The extension must not:

- Generate final IRIs independently.
- Resolve labels to ontology entities without the Kotlin boundary.
- Construct deletion triples independently.
- Combine staged RDF changes independently.
- Write Turtle files directly.
- Bypass final combined preview, validation, approval, or atomic application.

## Validation Expectations

Phase 2.5+ should add deterministic validation for:

- Missing or invalid labels.
- Missing namespace configuration.
- Invalid generated local names.
- Generated IRI collisions.
- Ambiguous label selection.
- Entity kind mismatch.
- Deletion of missing entities.
- Deletion with unresolved dependencies.
- Duplicate staged changes.
- Conflicting staged changes.
- References to items deleted by another staged change.
- Combined change sets that become empty after normalization.
- Stale project or source baselines.
- Final Turtle round-trip non-equivalence.

Validation should remain based on explicit graph facts and current engine capabilities. Phase 2.5+ should not introduce full OWL reasoning.

## Testing Requirements

Phase 2.5+ should include focused tests for:

### Label-First Editing

- Displaying labels in selectors and summaries.
- Resolving a unique label to the correct IRI.
- Rejecting ambiguous labels.
- Showing the full IRI in entity details.
- Generating deterministic IRIs.
- Producing the same IRI for the same valid input.
- Detecting collisions.
- Applying the approved collision strategy.

### Deletion

- Deleting a class with no references.
- Deleting an object property with no assertions.
- Deleting a datatype property.
- Deleting an individual.
- Detecting dependent references.
- Blocking unresolved deletion.
- Explicitly removing approved dependent statements.
- Rejecting deletion of a missing item.
- Preserving the source on rejection or failure.

### Staging

- Staging more than one edit.
- Clearing the form after successful preview.
- Keeping the form populated after failed preview.
- Editing a staged change.
- Restoring form data correctly.
- Replacing the old staged version after successful re-preview.
- Cancelling staged editing and restoring the prior version.
- Removing a staged change.
- Detecting conflicts between staged changes.
- Producing one combined semantic diff.
- Applying all staged changes atomically.
- Preserving all staged changes when final validation fails.
- Clearing staged changes after successful application.

### Integration

- Machine-readable CLI request and response behavior.
- Kotlin combined-preview and application behavior.
- VS Code form, staged-list, edit, remove, and refresh behavior.
- Round-trip persistence.
- Stale proposal detection.
- Rollback after failed post-save verification.

Tests should use temporary copies of small committed fixtures and should not modify committed example files.

## Non-Goals

Phase 2.5+ should not include:

- Schema RAG.
- AI-generated ontology edits.
- Document ingestion.
- Document-to-KG conversion.
- Entity resolution across documents.
- Autonomous agents.
- Full OWL reasoning.
- Full SHACL authoring or validation.
- Full Protégé parity.
- Equivalent-class editing.
- Disjoint-class editing.
- Inverse-property editing.
- Property chains.
- Cardinality restrictions.
- Property characteristics.
- Long-term staged-change persistence.
- Multi-user review queues.
- Authentication or authorization.
- Actual Git staging, commits, pushes, branches, or pull requests inside Entio.
- Source-text-preserving Turtle editing.
- A separate desktop application.
- A full drag-and-drop graph editor.

## Success Criteria

Phase 2.5+ is successful if a user can:

- Browse and select ontology items primarily by label.
- Open an item and view its full IRI.
- Create a new class, property, or individual without manually entering an IRI.
- Receive a deterministic unique IRI generated by Entio.
- Delete a supported class, property, or individual through a reviewed proposal.
- See references that block or expand a deletion.
- Stage multiple ontology edits without changing the source files.
- See every staged edit in one list.
- Edit a staged change and have its form values restored.
- Re-preview the edited change and return it to the staged list.
- Remove a staged change.
- Preview the complete staged change set.
- View one combined semantic diff.
- View combined validation and Turtle round-trip verification results.
- Reject the staged set with no project change.
- Approve and apply the entire staged set atomically.
- Reload the project and see the complete result.
- Recover the prior source automatically if final save verification fails.
- Confirm that only the intended ontology source files changed.

Phase 2.5+ should improve usability and multi-edit review without changing Entio’s core safety rule: no source mutation without preview, semantic diff, deterministic verification, and explicit approval.
