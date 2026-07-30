# Phase 11.5+ Slice 5 Semantic Provider And Orchestration

Status: Complete

Date: 2026-07-30

## ExecPlan Slice Implemented

Slice 5: Replace The Final Provider Boundary And Wire Orchestration.

## Goal

Ask the selected model for a bounded semantic plan, validate that plan
strictly, and let Kotlin compile supported meaning into the existing reviewed
proposal workflow.

## Files Modified

- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentAnalysisService.kt`
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentIngestionOrchestrator.kt`
- `web-server/src/main/kotlin/com/entio/web/ingestion/OpenAiDocumentAnalysisClient.kt`
- `web-server/src/test/kotlin/com/entio/web/ingestion/DocumentIngestionOrchestratorTest.kt`
- `web-server/src/test/kotlin/com/entio/web/ingestion/OpenAiDocumentAnalysisClientTest.kt`
- `docs/decisions/phase-11.5-plus-slice-5-semantic-provider-and-orchestration.md`

## Implementation

The final provider boundary now:

- requests semantic items, connected groups, evidence, rationale, outcomes,
  ambiguity, and critic dispositions;
- uses strict Phase 11.5+ request and response versions;
- rejects unknown fields and does not expose internal operation kinds, source
  IDs, final IRIs, raw RDF, or write instructions;
- validates the server-issued work key and known discovery, alignment,
  evidence, and critic identifiers;
- runs deterministic completeness verification before compilation;
- compiles accepted semantic meaning with the Kotlin semantic compiler;
- converts compiler output into the existing verified final-plan boundary so
  proposal review, approval, apply, reload, and rollback remain unchanged;
- preserves the selected model, bounded retry and correction behavior,
  cancellation handling, stage hashes, and safe failure reporting.

New document tasks use the semantic provider path. The retained low-level
contracts remain readable for existing data but are not used as a fallback.

## Tests Added Or Updated

The provider client tests cover the exact semantic schema, successful parsing,
and rejection of unknown or prohibited fields. The orchestrator fixture now
returns semantic plans and exercises successful compilation, bounded
correction, retry, review-only handling, failure handling, and the ten-document
call budget.

## Verification

- `./gradlew :web-server:test --tests '*OpenAiDocumentAnalysisClientTest*'` —
  passed.
- `./gradlew :web-server:test --tests '*DocumentAnalysisServiceTest*'` —
  passed.
- `./gradlew :web-server:test --tests '*DocumentIngestionOrchestratorTest*'` —
  passed.
- `./gradlew :web-server:check` — passed.

## Git Commit

A focused Git commit will be created on
`feature/phase-11-5-plus-slice-5-semantic-provider` after this completion
record is reviewed and staged with the implementation.

## Assumptions And Limitations

- Kotlin remains the only authority for operation selection, dependency
  ordering, final IRI generation, edit limits, and preview construction.
- The existing final-plan representation is retained as an internal adapter
  boundary for downstream review and provenance compatibility.
- This slice does not change the browser review presentation or applied
  provenance contracts; those changes belong to Slice 6.
