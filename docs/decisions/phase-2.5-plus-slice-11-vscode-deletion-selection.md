# Phase 2.5+ Slice 11 Completion: VS Code Deletion Selection And Proposal Flow

## ExecPlan Slice

Slice 11: VS Code Deletion Selection And Proposal Flow.

## Goal

Make supported deletion behavior usable in the workbench through an explicit Delete action, dependency selection controls, and a deletion preview that reuses the existing staged and combined proposal lifecycle.

## Files Modified

- `vscode-extension/src/proposalPreview.ts`
- `vscode-extension/src/webview.ts`
- `vscode-extension/src/extension.ts`
- `vscode-extension/src/test/proposalPreview.test.ts`
- `vscode-extension/src/test/webview.test.ts`
- This completion record.

## Delivered

- Added a Delete action to selected Class, Property, and Individual details.
- Added an explicit deletion review with always-included direct statements and selectable dependent statements.
- Kept deletion preview disabled until dependency inspection is safe and every dependent statement has a stable selected key.
- Carried stable `selectedDependencyKeys` through the typed request and combined proposal boundary.
- Added readable primary dependency text using labels or local vocabulary names while retaining technical IRIs in the underlying response model.
- Routed deletion previews through the existing combined proposal preview and staged-change lifecycle.
- Preserved the existing approval, rejection, application, refresh, and rollback routing; the extension does not write ontology sources directly.

## Tests Added Or Updated

- Request parsing and combined-request tests for `delete-entity` and selected dependency keys.
- Invocation and response-model tests for stable dependency keys.
- Webview rendering and generated-script tests for the Delete action, deletion preview, dependency selection controls, and existing preview controls.

## Verification

- `cd vscode-extension && npm test` passed with 29 tests.
- Full Gradle verification is run before commit.

## Scope Check

This slice does not implement RDF or Turtle behavior in TypeScript, perform label resolution or dependency analysis locally, write ontology sources directly, add a second proposal lifecycle, or introduce persistence or Git behavior. Kotlin remains responsible for semantic analysis and mutation.

## Assumptions And Limitations

- The workbench receives stable dependency keys from the machine-readable Kotlin CLI boundary implemented in Slice 10.
- The existing combined proposal path is used for a single deletion preview so deletion does not gain a separate application lifecycle.
- Final end-to-end coverage across copied fixtures remains part of Slice 12.

## Git

The slice commit and remote branch are recorded after full verification.
