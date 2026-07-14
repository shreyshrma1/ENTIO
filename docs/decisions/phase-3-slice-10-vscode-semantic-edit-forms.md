# Phase 3 Slice 10: VS Code Semantic Edit Forms And Staged Workflow

## Status

Completed on the Slice 10 branch and merged into local `main` after verification.

## Goal

Expose the approved Phase 3 semantic edit kinds through the existing VS Code typed request and staged proposal workflow without constructing RDF or bypassing review safeguards.

## Implementation

- Extended the existing proposal request model with annotation-property creation, definition, alternate-label, and general annotation edit kinds.
- Added form controls for target and property IRIs, language tags, datatypes, literal values, resource values, replacement values, and annotation-property labels or definitions.
- Routed semantic form previews through the existing `proposal-combined` preview boundary and converted a valid single-edit combined result into the existing staged preview model.
- Preserved the existing staged edit, replacement, removal, cancel, combined preview, approval, rejection, refresh, and source-opening behavior.
- Kept RDF-term handling explicit at the request boundary by distinguishing literal values from resource IRIs and retaining language/datatype fields.
- Kept full IRIs in technical form fields while user-facing details continue to use Kotlin-provided labels when available.

## Tests And Verification

Added extension tests for:

- normalization of all approved semantic edit kinds;
- language-tagged literal request preservation;
- resource-valued annotation requests;
- conversion of a combined semantic preview into a staged preview model;
- semantic form controls and conditional preview routing.

Verification passed:

```text
cd vscode-extension && npm test
```

The suite passed with 32 tests.

## Scope Boundary

This slice does not construct RDF triples, choose predicates, implement semantic policy, persist staged edits across sessions, add unsupported OWL controls, write Turtle directly, or create a second proposal lifecycle. Kotlin remains responsible for semantic translation, validation, preview, diffing, approval, rejection, apply, and rollback.
