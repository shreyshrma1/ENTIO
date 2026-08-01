# Phase 12 Candidate Promotion And Scalable Grounding

Status: Accepted

Date: 2026-07-31

## Context

The original candidate extractor treated every occurrence-level noun phrase,
relationship phrase, and value as retrieval input. Two benchmark documents
produced more than one thousand candidates. Fixed task-wide grounded-call
limits then made completion depend on document size, while repeated mentions
created duplicate model work and review noise.

## Decision

- Represent each extracted occurrence as an evidence mention.
- Group only exact or safely normalized lexical equivalents and retain all
  mention-to-candidate links.
- Keep similar concepts separate for model or reviewer judgment.
- Deterministically demote standalone values and document-only text and reject
  low-value fragments before retrieval.
- Promote a group only from an explicit definition, connected relationship or
  rule, repeated meaningful context, relevant named entity, or exact strong
  ontology match.
- Retain every non-promoted mention disposition in the coverage ledger without
  creating a recommendation card.
- Keep the 20-candidate request bound and bounded retry/splitting behavior, but
  remove task-wide logical-call and attempt ceilings. The number of groups may
  grow with the promoted inventory.
- Report mention, group, promotion, document-only, and supporting-value counts
  separately from grounded items and recommendations.

## Consequences

Retrieval and model calls operate once per promoted grouped candidate rather
than once per occurrence. Large document sets can create more groups without
silent truncation. Exact evidence provenance remains available for every
occurrence, while the review surface remains recommendation-oriented. The
pipeline continues to fail safely for an irreducible single-candidate provider
failure, invalid output, cancellation, authorization, or deterministic
verification failure.

## Implementation Record

The corrective implementation updates the neutral Phase 12 contracts,
candidate extraction and orchestration services, deterministic retrieval hot
path, review count contract and presentation, frozen benchmark expectation,
focused tests, active architecture description, and Phase 12 summary. It adds
no dependency, module, ontology write path, task persistence, or browser-side
semantic policy.

The corrected frozen two-PDF extraction regression produces 698 evidence
mentions, 432 safely grouped candidate terms, and 102 ontology-bearing
candidates before ontology-label promotion. The live simple-ontology path
promotes 112 after exact ontology matches are included. These are
fixture diagnostics, not runtime ceilings.

Grounded requests preserve small relationship/participant components before
packing unrelated candidates. Provider-local semantic item IDs are
deterministically namespaced by request group before aggregation, and
administrative or illustrative grounded dispositions remain in coverage
without creating review cards.

Verification passed:

- `./gradlew test`
- `./gradlew build`
- `./gradlew check`
- `(cd web-app && npm ci && npm audit --omit=dev && npm test && npm run build && npm run test:e2e)`
- `(cd vscode-extension && npm ci && npm test)`
- `git diff --check`

A focused commit is created on `fix/document-candidate-promotion`, the branch is
pushed without force, and the completed unit is merged locally into `main` with
a non-fast-forward merge. `main` is not pushed.
