# Phase 11.5 Slice 8: Pipeline Orchestration, Status, And Provenance

## Status

Completed on 2026-07-27.

## Implemented Boundary

Slice 8 replaces the production single-stage analysis path with one fixed Phase 11.5 sequence:

1. per-document discovery;
2. connected modeling, with consolidation only when deterministic chunking requires it;
3. cross-document and prior-provenance reconciliation;
4. current-ontology alignment;
5. modeling critique; and
6. final planning followed by deterministic verification.

No stage starts after cancellation or failed upstream verification. The task is bounded to thirty minutes, each OpenAI call remains bounded to 120 seconds, and every stage reuses the same verified selected model.

## Budget And Retry Ownership

A task-wide provider wrapper counts logical request hashes and provider attempts across all six services. It enforces:

- at most fifteen planned logical calls;
- at most twenty provider attempts;
- at most three automatic retries for the task; and
- no more than one retry of any logical call, using the exact same stage and request hash.

The orchestrator checks the document-count budget before discovery and passes the remaining logical-call budget into chunked modeling and reconciliation. A ten-document task fits only the single connected-model-call path plus the four required downstream calls and final planning. Reconsideration may start later only when the remaining attempt and call reserves can complete the whole bounded alignment/critic/final sequence.

## Visible And Temporary State

Task snapshots now retain bounded, ordered stage records containing stage state, times, duration, model ID, prompt and schema versions, input and output hashes, attempt counts, completion counts, and safe codes. Public progress messages describe the current stage without including prompts, provider payloads, credentials, or untrusted document text.

Intermediate discoveries, models, reconciliation, alignments, critic findings, final plans, and final IRI maps remain in memory. A server restart invalidates that temporary work. The review store retains only the verified final plan for the next workflow slice; Slice 8 does not expose review decisions, stage drafts, proposals, or source writes.

## Applied Provenance Compatibility

The applied-provenance snapshot adds optional Phase 11.5 pipeline metadata: work key, model ID, prompt versions, and stage output hashes. Existing version-one records remain readable, and saving existing applied records preserves any new pipeline metadata. No ontology source stores this provenance.

## Verification

Focused tests cover:

- fixed stage order and stage-specific progress;
- a ten-document maximum-budget pipeline;
- model-not-ready and safe permanent provider failures;
- no ontology source write before review;
- verified final-plan retention;
- bounded task stage records; and
- applied pipeline metadata surviving repository restart.

The Slice 8 verification commands are:

```bash
./gradlew :web-server:test
./gradlew :web-server:build
./gradlew check
git diff --check
git status --short
```
