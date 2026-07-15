# Phase 5 Slice 7: Machine-Readable CLI Boundary

## Status

Implemented on `feature/phase-5-slice-7-machine-readable-cli`.

## Implemented

The CLI now exposes thin, structured JSON commands for the approved read-only FIBO catalog boundary:

- `external-sources` lists the approved FIBO source and reports that selection is read-only.
- `external-manifest` returns the pinned release, package identity, catalog schema, checksums, curated seeds, and package status.
- `external-browse` pages curated modules, all modules, or one module's catalog elements.
- `external-describe` returns one external semantic descriptor by IRI.
- `external-search` delegates deterministic filtering and ranking to `FiboSchemaSearchService` and returns score breakdowns, confidence, match reasons, tie metadata, pagination, and no-silent-truncation metadata.
- `external-dependencies` delegates explicit dependency calculation to `ExternalDependencyReviewer` and returns blocked required-dependency state without mutating the project.
- `external-proposal` prepares external reuse, local-subclass, or package-reference proposals through `ExternalProposalPreparer` without writing project files.

All commands return structured success and error JSON. Project paths are resolved against repository ancestors when the CLI is launched from the Gradle `cli` module directory. Bundled FIBO assets remain read-only.

## Tests And Verification

Added `ExternalCatalogCliTest` coverage for:

- source and manifest metadata;
- curated browsing, descriptor lookup, and search response metadata;
- explicit dependency review and blocked external proposal preparation;
- invalid filters and valid empty search results;
- preservation of the local example source during read-only external proposal preparation.

Verification passed:

```bash
./gradlew :cli:test
./gradlew :cli:build
./gradlew test
```

The first combined verification attempt encountered a generated Gradle XML test-report write failure in `validation-engine`; cleaning that ignored module build output and rerunning the module test resolved it. No tracked source or test files were changed to work around the generated-output issue.

## Boundaries And Limitations

The CLI does not parse FIBO, rank candidates, assemble descriptors, calculate dependencies, or generate RDF itself. It delegates those operations to semantic-engine services and uses the existing JSON fragment boundary. External proposal preparation returns a baseline-aware proposal preview but does not add a second application path; approval, application, reload, and rollback remain owned by the existing proposal lifecycle and later workbench integration.

The CLI exposes the approved FIBO source only because Phase 5 approves no additional external ontology source. It does not persist session selection, modify `entio.yaml`, modify local Turtle sources, modify bundled FIBO assets, or add a server/API/database boundary.
