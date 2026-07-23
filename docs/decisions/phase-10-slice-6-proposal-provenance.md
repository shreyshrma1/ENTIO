# Phase 10 Slice 6: Proposal Provenance And Workflow Integration

## Status

Complete.

## Decision

Materialized entries use the existing staged-edit and proposal workflow. Proposal details identify their origin as `Materialized from reasoning` and show the server-provided inference type, reasoning run, target source, whether the fact was entailed before assertion, and whether imported knowledge contributed. Internal graph fingerprints, fact identifiers, user/timestamp fields, and import source identifiers remain available to server contracts but are not exposed as ontology annotations or separate graph changes.

Ordinary staged entries are unchanged. A materialized entry's semantic diff is only the explicit asserted triple addition, and it remains subject to normal preview, validation, reasoning/SHACL impact, approval, atomic apply, reload, and rollback behavior.

Copied-fixture tests prove that rejection leaves sources byte-for-byte unchanged; multi-source batches apply and reload through the existing atomic writer; forced post-save verification failure restores every target; and an applied materialization is subsequently detected as asserted.

## Verification

- `./gradlew :semantic-engine:test :validation-engine:test :graph-diff:test :web-server:test` — passed
- `(cd web-app && npm test && npm run build)` — passed, 21 files and 74 tests
- `git diff --check` — passed
