# Phase 3 Slice 1: Core Semantic Contracts

## ExecPlan Slice Implemented

Slice 1, Core Semantic Contracts, from `docs/execplans/0006-phase-3-semantic-description-layer.md`.

## Goal

Define immutable Entio-owned contracts for semantic descriptions, RDF-term-aware metadata values, deterministic search results, and typed semantic edit requests.

## Files Modified

- `core-types/src/main/kotlin/com/entio/core/SemanticDescriptionContracts.kt`
- `core-types/src/test/kotlin/com/entio/core/SemanticDescriptionContractsTest.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/ProjectLoaderTest.kt`
- `cli/src/test/kotlin/com/entio/cli/CliExampleProjectTest.kt`
- `cli/src/test/kotlin/com/entio/cli/Phase25PlusEndToEndRegressionTest.kt`

The last three test changes align existing fixture expectations with the committed example fixture. The temporary Phase 2.5+ deletion fixture now adds its referenced property explicitly instead of relying on removed committed example data.

## Changes

- Added immutable localized text, annotation value, annotation statement, descriptor, search, and semantic edit contracts.
- Preserved IRI resources, blank nodes, plain literals, datatyped literals, and language-tagged literals.
- Added explicit descriptor kinds, semantic match reasons, and semantic edit kinds.
- Added stable ordering keys without implementing extraction, search, validation, serialization, or source mutation.

## Tests And Verification

- `./gradlew :core-types:test` — passed.
- `./gradlew test` — passed.

## Commit

This completion record is part of the Slice 1 change. The commit is created after the staged diff review.

## Assumptions And Limitations

- Descriptor assembly, label policy, semantic search, and edit translation remain later slices.
- The existing Phase 2.5+ regression tests use a temporary copied fixture for deletion-specific relationship coverage; committed examples remain unchanged.
