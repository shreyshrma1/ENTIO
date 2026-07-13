# Phase 2.5+ Slice 7 Completion: VS Code Label-First Controls And Deletion Review

## Status

Implemented on `feature/phase-2.5-plus-slice-7-vscode-label-deletion`.

## Delivered

- Added typed VS Code adapters for Kotlin-owned label resolution, deterministic IRI generation, and deletion dependency inspection.
- Added label-backed entity selector controls with kind and source filters while keeping final resolution in the Kotlin CLI.
- Added generated-IRI controls and display for supported new class, property, and individual forms.
- Added deletion dependency review controls that show direct/dependent statements and explicitly communicate when deletion remains blocked.
- Added response models and routing for resolved, ambiguous, missing, generated-IRI, and dependency-review states without constructing RDF or writing Turtle in TypeScript.
- Preserved the existing preview, approval, rejection, refresh, and relationship-detail behavior.

## Verification

- `npm test` from `vscode-extension/`

All 21 extension tests passed.

## Scope Check

This slice does not add staged-session state, deletion graph mutations, independent label or IRI semantics, direct Turtle writes, a UI framework, or a second approval workflow. The Kotlin CLI remains the source of truth for resolution, identifier generation, and deletion analysis.
