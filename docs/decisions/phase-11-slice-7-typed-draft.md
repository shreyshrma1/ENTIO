# Phase 11 Slice 7 Completion: Typed Draft And Proposal Integration

## Status

Complete and verified.

The original task-wide limit recorded below was superseded on 2026-07-30.
Document recommendations are still atomic and limited to 20 edits each, and
each internal staging batch is still limited to 20 edits. There is no longer a
100-edit or five-batch limit across the whole document task. Valid accepted
recommendations are staged in as many bounded batches as they require. There is
also no combined connected-item ceiling: the 300-item limit applies to each
provider response, while verified task results and their deterministic
prerequisites are retained without task-wide truncation.

## Delivered

- Added a Kotlin translation matrix that converts every approved, accepted document recommendation into existing typed ontology, semantic, SHACL, or curated FIBO reuse operations.
- Added explicit gates for stale graph, evidence, model, and prompt state; duplicate operations; unresolved clarification; low-confidence evidence; missing operands; and unsupported recommendation actions.
- Added server-owned, all-or-nothing staging for batches of at most 20 typed edits, with at most 100 edits and five ordered schema-before-fact batches per task.
- Reused the shared staging, semantic preview, validation, reasoning, SHACL impact, review, approval, atomic apply, reload verification, and rollback workflow.
- Attached bounded document provenance to each staged item and displayed its task, recommendation, evidence count, confidence, and target source during proposal review.
- Added a write-ahead provenance coordinator that commits durable applied-document records only after ontology reload verification succeeds.
- Added provenance-only handling for accepted confirmations, without creating empty graph edits.
- Added the review action that sends accepted recommendations to the shared proposal while retaining rejected, blocked, or stale review decisions.

## Boundaries Preserved

- Document ingestion does not emit raw RDF, mutate graphs directly, write ontology sources before approval, create a separate proposal type, or auto-approve changes.
- Provenance is stored outside Turtle and SHACL sources and does not become an ontology annotation.
- Failed batch validation leaves that batch unchanged; failed provenance commit uses the existing source rollback path.
- Ordinary non-document staging and apply behavior does not invoke document-provenance work.
- CLI, VS Code, database, and unrelated product surfaces remain unchanged.

## Verification

- `./gradlew :semantic-engine:test`
- `./gradlew :web-server:test`
- `./gradlew test`
- `./gradlew build`
- `cd web-app && npm test`
- `cd web-app && npm run build`
- `git diff --check`
- `git status --short`
