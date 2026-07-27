# Phase 11.5 Slice 3 Completion: Connected Document Model

**Status:** Complete

**Branch:** `feature/phase-11-5-slice-3-connected-model`

**Commit:** `Implement connected document modeling`

## Slice

Slice 3 implements the task-level connected-model stage defined by the approved
Phase 11.5 ExecPlan. It consumes only complete, verified Slice 2 discoveries.
It does not perform current-ontology alignment, reconciliation, critique, final
planning, typed-edit generation, staging, application, or source writes.

## Goal

Turn verified document meaning into one coherent local domain model before the
current ontology can influence a match. The stage can introduce supporting
concepts before the properties, relationships, facts, constraints, and rules
that depend on them.

## Implementation

The connected-model path provides:

- a dedicated, ontology-blind request and strict provider response schema;
- model items for classes, object properties, datatype properties, annotation
  properties, hierarchy, domain and range assignments, individuals, types,
  object and datatype facts, shapes, constraints, and complex rules;
- explicit model-local reference roles such as property, domain, range,
  subject, predicate, object, subclass, superclass, and target class;
- Kotlin-generated stable item IDs in place of provider-local IDs;
- contiguous deterministic ordering that requires every dependency to refer to
  an earlier item;
- a rationale and one or more verified discovery IDs for every model item;
- a requirement that every modeled item has business-content evidence, so
  document-control metadata cannot become a business concept by itself;
- review-only treatment for complex rules that current typed operations may
  not express safely;
- deterministic whole-discovery packing under the 60,000-character input
  limit without truncation or sampling;
- one connected-model call per chunk and exactly one separate consolidation
  call only when more than one chunk is required;
- preflight call-budget validation that reserves the four required downstream
  stages before any connected-model provider call;
- strict final limits of 300 model items, twenty references per item, one exact
  retry per logical call, three retry attempts per task, and twenty provider
  attempts per task;
- safe rejection of missing, duplicate, forward, cyclic, excessive, wrongly
  typed, or otherwise inconsistent provider references; and
- stage records with timing, selected model, prompt and schema versions,
  request and response hashes, attempt counts, and item counts.

The existing Phase 11 direct candidate-analysis service remains present during
this slice. The approved Slice 8 orchestrator will replace its production use
with the complete Phase 11.5 sequence.

## Files

- `core-types/src/main/kotlin/com/entio/core/DocumentAnalysisPipelineContracts.kt`
- `core-types/src/test/kotlin/com/entio/core/DocumentAnalysisPipelineContractsTest.kt`
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentAnalysisService.kt`
- `web-server/src/main/kotlin/com/entio/web/ingestion/OpenAiDocumentAnalysisClient.kt`
- `web-server/src/test/kotlin/com/entio/web/ingestion/DocumentAnalysisServiceTest.kt`
- `web-server/src/test/kotlin/com/entio/web/ingestion/OpenAiDocumentAnalysisClientTest.kt`
- `docs/decisions/phase-11.5-slice-3-connected-model.md`

The core contract change completes a reviewed Slice 1 invariant: references
now carry their semantic role, and datatype value assertions carry a typed RDF
literal. No new module, dependency, provider adapter, service duplicate, or
alternate artifact was added.

## Tests

Focused and regression tests prove:

- `Payment` and `Payment Approval Record` are declared before the property that
  connects them;
- a property domain and range resolve through explicit local references;
- administrative metadata does not become a business concept;
- aggregation and separation-of-duty rules remain distinct review-only model
  items;
- every model item traces to a verified business discovery;
- provider requests contain no ontology snapshot, ontology fingerprint,
  writable source, target source, final IRI, or executable edit;
- oversized inventories preserve every discovery across deterministic chunks
  and use one consolidation call;
- insufficient logical-call budget blocks before any provider call;
- missing, duplicate, cyclic, forward, and excessive references fail safely;
- a transient logical call receives at most one retry with byte-identical
  frozen input;
- strict connected-model and consolidation schemas reject extra fields; and
- credentials remain in the authorization header and never enter request
  bodies.

## Verification

Commands completed successfully before commit:

```bash
./gradlew :core-types:test
./gradlew :web-server:test
./gradlew :web-server:build
git diff --check
git status --short
```

Gradle clean tasks removed only ignored generated module build outputs after
the local incremental compiler emitted duplicate class files whose names ended
in ` 2.class`. The clean rerun passed from a fresh compilation.

The required commands are run again after the local non-fast-forward merge.

## Decisions And Assumptions

- Provider-local IDs are temporary handles. They are accepted only within one
  response and are replaced by stable Kotlin-derived IDs.
- A dependency must already exist earlier in the same model. Forward
  references and cycles therefore fail before later stages can use them.
- The connected model receives complete verified discovery records, including
  evidence references, but receives no current ontology context.
- Chunk boundaries preserve whole discovery records. Entio does not truncate
  or silently omit discoveries to make a provider call fit.
- Chunk outputs are validated independently before consolidation, and the
  consolidated output is validated again against the full discovery inventory.
- Complex rules remain descriptive and review-only. This stage does not coerce
  them into a data field, SHACL constraint, or executable operation.
- Four logical calls are reserved for reconciliation, alignment, critique, and
  final planning. The full Slice 8 orchestrator will own the shared task-level
  call ledger across all stages.

## Commit Record

This artifact is included in the focused commit
`Implement connected document modeling` on
`feature/phase-11-5-slice-3-connected-model`. The immutable commit hash is
recorded in Git history.
