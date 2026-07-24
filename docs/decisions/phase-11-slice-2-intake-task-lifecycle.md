# Phase 11 Slice 2: Safe Intake, Temporary Lifecycle, And Task Orchestration

## Status

Completed on 2026-07-24.

## ExecPlan Slice

Phase 11 Slice 2: safe intake, temporary lifecycle, and task orchestration.

## Goal

Add authorized, bounded document intake and temporary task handling, plus the approved durable provenance repository boundary, without starting extraction, OCR, provider analysis, semantic matching, or ontology changes.

## Implementation

- Added metadata-first multipart intake for PDF, DOCX, UTF-8 TXT, and UTF-8 Markdown documents.
- Enforced the ten-document, 25 MB per-document, and determinable 500-page PDF bounds before provider work.
- Added extension, declared media type, signature, UTF-8, encrypted-file, and safe DOCX package checks.
- Added streaming upload writes, SHA-256 checksums, server-generated IDs, safe filenames, and required authority metadata.
- Added server-owned temporary roots, ownership markers, generated paths, symlink and traversal defenses, owner-only permissions where supported, and bounded cleanup.
- Added owner- and project-scoped current-session task lookup, progress, cancellation, deletion, expiry, shutdown cleanup, and same-project checksum duplicate detection.
- Required an idempotency key for multipart intake and retained completed intake responses for safe current-session replay.
- Added a versioned, bounded, atomically replaced JSON repository for applied document provenance. It is project-authorized and physically separate from both temporary files and ontology project roots.
- Added pending provenance events so a later slice can coordinate provenance with the existing apply and rollback path.
- Added only the minimum Ktor route wiring needed for intake and task lifecycle operations.

## Tests Added

- Successful intake for all four approved media types and authority metadata.
- Type mismatch, size limit, encrypted PDF, unsafe DOCX relationship, unsafe filename, and count-limit rejection.
- Partial failure cleanup and removal of stored bytes after rejection.
- Owner/project isolation, duplicate checks, cancellation, deletion, shutdown, stale-startup cleanup, traversal containment, and symlink-safe deletion.
- Provenance authorization, project separation, idempotent retention, pending-event commit, restart survival, and independence from temporary cleanup.
- Existing web-server tests continue to pass.

## Verification

```bash
./gradlew :web-server:test
./gradlew :web-server:build
git diff --check
git status --short
```

Results:

- `:web-server:test`: passed.
- `:web-server:build`: passed.
- `git diff --check`: passed.
- `git status --short`: showed only the approved Slice 2 files.

## Decisions, Assumptions, And Limitations

- Upload completion leaves a task in `uploaded`; it does not consume a later extraction/provider execution slot.
- Temporary task data is session-scoped and is removed at cancellation, deletion, expiry, shutdown, or restart.
- Durable provenance stores only bounded applied workflow evidence and metadata. It does not retain original uploads, complete extracted text, page images, credentials, paths, or provider payloads.
- Slice 2 does not expose durable provenance through a public route. Later internal use must still supply a registered project and the route layer must enforce the approved permission.
- Extraction, OCR, provider execution, semantic matching, recommendation review, draft conversion, proposal application, and source writes remain for later slices.
