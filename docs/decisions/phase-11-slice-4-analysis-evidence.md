# Phase 11 Slice 4: Bounded Analysis And Evidence Verification

## Status

Completed on 2026-07-24.

## ExecPlan Slice

Phase 11 Slice 4: bounded analysis boundary and evidence verification.

## Goal

Analyze bounded extracted blocks through the current user's verified selected model while accepting document evidence only when Kotlin can resolve it exactly against server-held text.

## Implementation

- Added versioned per-document and cross-document analysis request and response contracts.
- Added deterministic block packing capped by block and request character limits; prompts contain opaque block IDs and bounded text, never server paths, credentials, permissions, or complete unbounded documents.
- Added a fixed system instruction that treats document content as untrusted quoted data and denies tools, secrets, permission changes, external URLs, and rule bypass.
- Reused callback-scoped credentials and required a current `READY`, verified, compatible OpenAI model. There is no fallback model.
- Added an ingestion-only OpenAI Responses adapter with the fixed HTTPS endpoint, redirects disabled, `store: false`, no tools, strict JSON Schema, bounded responses, safe status classification, and timeout behavior.
- Limited each task to twenty provider calls, each failed call to at most two transient retries, and checked cancellation before new work.
- Added exact-work caching over task/document checksums, ontology fingerprint, selected model, prompt version, extractor version, and authority metadata.
- Added `DocumentEvidenceVerifier` in `semantic-engine` to resolve block-relative offsets and compare the claimed excerpt byte-for-character with server-held normalized text.
- Rejected missing, altered, invented, out-of-range, cross-document, duplicate, unsupported-type, and over-limit evidence.
- Added stable candidate identities, approved evidence and interpretation labels, deterministic ordering, and summaries generated only from verified candidate and evidence IDs.
- Kept this boundary separate from assistant conversation state, tools, autonomous loops, semantic matching, staging, proposal application, and source writes.

## Tests Added

- Exact, altered, invented, out-of-range, duplicate, cross-document, and multi-passage evidence.
- Entity, relationship, value, business-rule, and ambiguity candidate shapes through provider fakes.
- Prompt-injection text requesting secrets, tools, network access, permissions, and rule bypass.
- Missing/unverified/incompatible model, cancellation, transient retry, permanent failure, call accounting, and identical-work replay.
- Strict OpenAI request fields, secret exclusion from request bodies, unsupported response-field rejection, and safe rate-limit classification.
- Grounded summaries that reference only verified candidates and evidence.

## Verification

```bash
./gradlew :semantic-engine:test
./gradlew :web-server:test
./gradlew :web-server:build
git diff --check
git status --short
```

Results:

- `:semantic-engine:test`: passed.
- `:web-server:test`: passed.
- `:web-server:build`: passed.
- `git diff --check`: passed.
- `git status --short`: showed only the approved Slice 4 files.

## Decisions, Assumptions, And Limitations

- Provider text is never evidence by itself. The stored excerpt is reconstructed from the server-held block after offsets and document identity pass verification.
- Cross-document comparison receives a bounded sample of each document and stable prior candidate keys, not concatenated full documents.
- Summaries are deterministic projections of verified candidates in this slice. Slice 5 can attach recommendation identities after semantic matching.
- Provider failures expose fixed safe codes and messages; response payloads, credentials, paths, and excerpts are not logged or placed in error messages.
- This slice produces candidates only. It does not select ontology matches, recommend edits, stage changes, or modify ontology sources.
