# Phase 3 Slice 7: CLI Descriptor And Semantic Search Boundary

## Status

Completed on the Slice 7 branch and merged into local `main` after verification.

## Goal

Expose Kotlin-owned semantic descriptors and deterministic label-aware search through machine-readable CLI commands without duplicating descriptor assembly, label policy, ranking, or RDF parsing in the CLI.

## Implementation

- Added `SemanticDescriptionService` in `semantic-engine` for descriptor lookup and search.
- Added machine-readable `descriptor` and `describe` CLI commands for selected entity descriptors.
- Added a machine-readable `search` CLI command with optional preferred-language, descriptor-kind, and source-id filters.
- Search uses deterministic case-insensitive matching over preferred labels, alternate labels, full IRIs, and general annotation values.
- Search reason precedence is preferred label, alternate label, IRI, then annotation, with stable ordering by rank, label, entity IRI, and source id.
- Added descriptor JSON for common metadata and every supported descriptor kind, including structural assertions, annotations, and language/datatype fields.
- Existing Phase 1 and Phase 2 CLI commands remain registered with their existing response behavior.

## Tests And Verification

Added semantic-engine tests for:

- descriptor lookup and missing descriptor errors;
- preferred-label, alternate-label, and annotation match reasons;
- source and kind filters;
- deterministic search behavior.

Added CLI tests for:

- descriptor JSON output and the `describe` alias;
- filtered and unfiltered search output;
- structured missing-descriptor errors.

Verification passed:

```text
./gradlew :cli:test
./gradlew test
```

## Scope Boundary

This slice does not add fuzzy matching, search indexes, embeddings, external retrieval, persistence, server infrastructure, or UI behavior. The CLI remains a thin adapter over the semantic-engine service and existing project loader.
