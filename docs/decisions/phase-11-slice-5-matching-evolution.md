# Phase 11 Slice 5: Ontology Matching, Duplicate Prevention, And Iterative Comparison

## Status

Completed on 2026-07-24.

## ExecPlan Slice

Phase 11 Slice 5: ontology matching, duplicate prevention, and iterative comparison.

## Goal

Turn verified document candidates into deterministic, evidence-aware matches and recommendations without creating or staging ontology edits.

## Implementation

- Added a bounded matcher that consumes records from existing Entio search boundaries instead of introducing an index, embedding store, vector database, or external retrieval path.
- Searches applied local content, imports, private draft work, shared staging, current proposals, same-task candidates, durable prior provenance, and curated FIBO records in a stable order.
- Filters FIBO matches to explicitly supplied pinned module/source IDs.
- Uses canonical IRIs, normalized semantic identities, and normalized typed-operation identities with deterministic scores and match reasons.
- Treats an exact typed-operation match in applied/current/same-task/durable work as `Confirm`, preserving evidence without a duplicate ontology edit.
- Keeps label-only results as reviewable reuse or extension suggestions; a label alone does not prove a duplicate.
- Produces `Confirm`, local/imported/FIBO reuse, `Extend`, `Revise`, `CreateLocal`, `Split`, `Merge`, `Conflict`, `Supersede`, `InsufficientEvidence`, and `Unsupported` outcomes.
- Keeps split, merge, conflict, revise, supersede, low-confidence, ambiguous, and unresolved-target outcomes behind mandatory clarification.
- Includes evidence-linked conflict alternatives and affected ontology IRIs.
- Uses authority status, business area, jurisdiction, effective date, and expiration date when ranking and explaining results. A newer date alone never means supersession.
- Caches completed exact work and recomputes deterministically when the exact-work key changes.
- Adds no typed conversion, staging, proposal, apply, or ontology source behavior.

## Tests Added

- Applied local, imported, private draft, shared staging, current proposal, same-task, durable provenance, and curated FIBO matches.
- Rejection of unapproved FIBO source records.
- Same-task and cross-workflow normalized typed-operation duplicate prevention.
- Local/imported reuse, no-match creation, ambiguity, confirm, extend, revise, split, merge, conflict, unsupported, and explicit supersession.
- Evidence-linked conflict alternatives and mandatory clarification.
- Business-area, jurisdiction, effective-date, and expiration-date ranking.
- Newer supporting evidence that does not silently supersede authoritative meaning.
- Stable repeated results and reprocessing after an exact-work-key change.

## Verification

```bash
./gradlew :semantic-engine:test
./gradlew :semantic-engine:build
./gradlew :web-server:test
git diff --check
git status --short
```

Results:

- `:semantic-engine:test`: passed.
- `:semantic-engine:build`: passed.
- `:web-server:test`: passed.
- `git diff --check`: passed.
- `git status --short`: showed only the approved Slice 5 files.

## Decisions, Assumptions, And Limitations

- Server orchestration must populate `DocumentSemanticRecord` only from already authorized existing Entio sources. The matcher performs no I/O or authorization itself.
- An exact label may support a reuse suggestion but cannot create a `Confirm` duplicate decision without canonical semantic or typed-operation identity.
- Split and merge remain review-only because the current typed-operation set cannot safely represent every complete restructuring.
- Authority and applicability affect ranking and explanations; they never let recency override a trusted source automatically.
- Recommendation generation remains read-only. Slice 7 owns any later conversion into the existing staging and proposal workflow.
