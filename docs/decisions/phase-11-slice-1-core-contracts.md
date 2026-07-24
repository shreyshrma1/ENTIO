# Phase 11 Slice 1: Neutral Ingestion, Evidence, And Recommendation Contracts

## Status

Completed on 2026-07-24.

## ExecPlan Slice

Phase 11 Slice 1: neutral ingestion, evidence, and recommendation contracts.

## Goal

Add the minimal immutable product contracts and invariants required by later Phase 11 slices without introducing web, parser, provider, filesystem, UI, RDF-write, or source-write concerns.

## Files Modified

- `core-types/src/main/kotlin/com/entio/core/DocumentIngestionContracts.kt`
- `core-types/src/main/kotlin/com/entio/core/DocumentRecommendationContracts.kt`
- `core-types/src/test/kotlin/com/entio/core/DocumentIngestionContractsTest.kt`
- `core-types/src/test/kotlin/com/entio/core/DocumentRecommendationContractsTest.kt`
- `docs/decisions/phase-11-slice-1-core-contracts.md`

## Implementation

- Added opaque document, task, block, and evidence identities.
- Added explicit media, authority, lifecycle, extraction, evidence, candidate, recommendation, confidence, review, and match-scope states.
- Added immutable authority, document, page geometry, coordinate, located-text, and exact-evidence records.
- Added separate ontology-structure and business-fact candidates and recommendations.
- Added deterministic match, ambiguity, conflict, dependency, summary-highlight, review-decision, and ordering contracts.
- Added private-draft provenance and minimal durable applied-provenance records.
- Enforced the approved file, text, excerpt, confidence, coordinate, identity, ordering, category/action, clarification, and evidence-link invariants.
- Kept core records independent from Ktor, Jackson, PDFBox, POI, Tesseract, Jena, React, and filesystem types.

## Tests Added

- Valid construction of neutral document, located OCR, evidence, recommendation, summary, and applied-provenance records.
- Invalid identity, filename, checksum, byte, date, offset, confidence, coordinate, OCR-field, evidence, and ordering cases.
- Schema and business-fact category separation.
- Local/imported/FIBO reuse selection rules and create-local duplicate protection.
- Mandatory clarification for low-confidence and risky recommendations.
- `Confirm` provenance without a no-op typed edit.
- Summary highlights that require both evidence and recommendation links.
- Reflection-based review that public contract fields do not expose forbidden adapter, parser, provider, semantic-library, or filesystem types.

## Verification

```bash
./gradlew :core-types:test
./gradlew :core-types:build
git diff --check
git status --short
```

Results:

- `:core-types:test`: passed.
- `:core-types:build`: passed.
- `git diff --check`: passed.
- `git status --short`: showed only the five Slice 1 files listed above.

## Commit

A focused Slice 1 commit was created on `feature/phase-11-slice-1-core-contracts`; its hash is recorded in Git history.

## Decisions, Assumptions, And Limitations

- Core records are domain contracts, not JSON DTOs. Slice 6 will map them to the lower-camel and kebab-case transport shape approved by Slice 0.
- Stable identity generation remains service behavior for later slices; Slice 1 records and validates the stable identity inputs.
- Exact excerpt equality against server-held text remains semantic-engine behavior for Slice 4; Slice 1 guarantees internally consistent ranges and bounded excerpts.
- Applied provenance contains only bounded workflow metadata and exact supporting excerpts. It contains no full document, path, provider payload, or ontology annotation.
- No dependency, module, semantic behavior, route, UI, staging, or source-write behavior was added.
