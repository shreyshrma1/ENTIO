# Phase 2.5+ Slice 5 Completion: Structured CLI Boundary

## Status

Implemented on `feature/phase-2.5-plus-slice-5-cli-boundary`.

## Delivered

- Added a pinned Jackson Kotlin parser confined to the CLI module for versioned structured request files.
- Added `proposal-request` for ordered heterogeneous edit parsing, schema validation, normalization, and stable machine-readable conflict output.
- Added `resolve-label`, `generate-iri`, and `deletion-dependencies` commands that delegate semantic behavior to Kotlin engine services.
- Preserved existing single-edit proposal commands and response shapes.
- Added structured errors for malformed JSON, unsupported schema/edit kinds, missing sources, ambiguous labels, missing namespaces, collisions, and dependency blockers.

## Verification

- `./gradlew :cli:test --no-daemon --console=plain`
- `./gradlew test --no-daemon --console=plain`

Both commands passed.

## Scope Check

This slice does not implement combined proposal application, persistence, server behavior, Turtle parsing in CLI, or VS Code state. The structured request boundary is stateless and ready for the later combined lifecycle slice.
