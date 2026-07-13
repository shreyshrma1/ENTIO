# Phase 2.5+ Slice 1 Completion: Core Contracts

## Status

Implemented on `feature/phase-2.5-plus-slice-1-core-contracts`.

## Delivered

- Added optional `iriNamespace` metadata to `EntioProjectConfig`.
- Added Entio-owned contracts for namespace metadata, entity selection and resolution, generated IRI results, deletion dependencies, staged operations, staged change sets, conflicts, validation attribution, and combined proposal metadata.
- Added explicit lifecycle and outcome enums/sealed states for later semantic and workbench services.
- Kept contracts immutable and based on Entio RDF terms; no Apache Jena types or new dependencies were added.
- Preserved staged entry order as supplied by callers.

## Verification

- `./gradlew :core-types:test --no-daemon --console=plain`
- `./gradlew test --no-daemon --console=plain`

Both commands passed.

## Scope Check

This slice does not resolve labels, normalize or generate IRIs, traverse graphs, validate changes, detect conflicts, mutate source files, add CLI behavior, or add VS Code behavior. Those concerns remain for later slices in the approved ExecPlan.
