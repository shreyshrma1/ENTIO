# Phase 11 Correction: Connect Document Ingestion Orchestration

## Status

Complete and verified.

## Reason For The Correction

The approved extraction, analysis, matching, review, and typed-draft components existed, but production intake did not invoke them. Uploads therefore stopped after validation and could not reach the Phase 11 review workflow.

## Delivered

- Connected successful multipart intake to task-owned asynchronous processing.
- Added ordered task transitions through extraction, analysis, matching, recommendation preparation, and review readiness.
- Reused the configured Tesseract boundary, verified current-user model and credential boundary, Kotlin evidence verification, ontology matcher, semantic descriptors, durable prior provenance, and deterministic IRI generation.
- Built the review workspace from verified extracted blocks and current graph fingerprints without changing ontology sources.
- Added safe model-blocked behavior when a current verified model or credential is unavailable.
- Preserved per-project and server-wide task bounds from the beginning of upload processing.
- Added cancellation checks across extraction, analysis, matching, and recommendation preparation.
- Rechecked current model selection before accepted recommendations can become typed draft items.

## Boundaries Preserved

- Provider output still cannot create RDF, typed edits, or review decisions.
- The Kotlin semantic engine remains responsible for matching, identities, and draft operands.
- Processing remains in memory; only successfully applied provenance uses the approved durable repository.
- Intake, analysis, and review do not write ontology sources.
- Existing proposal review, approval, atomic apply, reload verification, and rollback remain authoritative.

## Verification

- `./gradlew :web-server:test`
- `./gradlew test`
- `./gradlew build`
- `git diff --check`
- `git status --short`
