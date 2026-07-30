# Phase 11.5+ Semantic-Plan Reference Correction

## Corrective Slice

This is a focused corrective follow-up to Phase 11.5+ Slice 5, semantic provider
and orchestration.

## Goal

Keep semantic judgment with the model while moving opaque identifier and
coverage bookkeeping into deterministic Kotlin. Recover safely from one strict
semantic-plan failure without weakening Kotlin validation or the human-review
boundary.

## Changes

- The runtime semantic-planning instruction now lists the exact retained
  connected-model item IDs.
- The model must produce one semantic item per retained connected item, reuse
  its exact ID, emit every referenced semantic item, place every item in a
  semantic group, and verify reference roles before returning.
- The strict provider schema constrains item IDs to the retained IDs and requires
  the exact retained item count, at least one group, and the expected coverage
  entry count.
- Kotlin restores each semantic item's discovery and evidence IDs from the
  verified connected model. It also restores group evidence from grouped items
  and generates the complete coverage ledger from verified discoveries and
  accepted group outcomes.
- Provider-copied discovery, evidence, and coverage identifiers cannot change
  provenance or cause a harmless transcription failure.
- Unknown semantic-item or alignment targets, missing retained items, incomplete
  references, incompatible roles, and unsupported semantic structures still
  fail closed.
- Valid model-created groups are expanded with the transitive closure of their
  explicit typed references. This lets the compiler create a referenced class,
  property, individual, or shape before compiling the item that uses it.
- Retained items that the model leaves completely ungrouped become transparent
  standalone compilation units containing only that item and its explicit
  dependency closure. Kotlin does not infer additional business relationships.
- A dependency with a review-only or blocked outcome lowers the generated
  compilation unit to the same safe outcome instead of being promoted to
  executable.
- Strict semantic-plan schema, coverage, critic, item, group, and reference
  failures are eligible for one bounded full-plan regeneration.
- The regeneration contains only the safe failure code and original verified
  request. It asks for a complete response rather than a partial patch.
- A second invalid response still fails closed. Kotlin does not remove dangling
  references, invent targets, or change their meaning.
- Previously message-less strict checks now produce redacted diagnostic
  categories without exposing provider output.
- Provider requests are no longer rejected, truncated, or packed according to
  a fixed character count. Whole extracted blocks and complete verified stage
  inputs remain available to the selected model. Document, discovery, evidence,
  model-item, output-token, logical-call, provider-attempt, and wall-time bounds
  remain in force.

## Files Modified

- `core-types/src/main/kotlin/com/entio/core/DocumentAnalysisPipelineContracts.kt`
- `semantic-engine/src/main/kotlin/com/entio/semantic/DocumentSemanticPlanCompiler.kt`
- `semantic-engine/src/test/kotlin/com/entio/semantic/DocumentSemanticPlanCompilerTest.kt`
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentAnalysisService.kt`
- `web-server/src/main/kotlin/com/entio/web/ingestion/DocumentIngestionOrchestrator.kt`
- `web-server/src/main/kotlin/com/entio/web/ingestion/OpenAiDocumentAnalysisClient.kt`
- `web-server/src/test/kotlin/com/entio/web/ingestion/DocumentAnalysisServiceTest.kt`
- `web-server/src/test/kotlin/com/entio/web/ingestion/DocumentIngestionOrchestratorTest.kt`
- `web-server/src/test/kotlin/com/entio/web/ingestion/OpenAiDocumentAnalysisClientTest.kt`
- `docs/architecture/document-analysis-and-adaptive-recovery.md`
- `docs/decisions/phase-11.5-plus-semantic-plan-reference-correction.md`

## Tests

- Provider tests verify strict dynamic bounds, deterministic bookkeeping,
  missing-item failures, standalone group completion, and bounded regeneration
  categories.
- Compiler tests verify omitted but explicitly referenced declarations are
  included and ordered before dependent operations.
- A bounded throughput test compiles 100 plans containing 50 groups and 100
  typed operations per plan.
- Connected-model requests are sized by estimated output pressure rather than a
  fixed input-character cap. The estimate accounts for discovery descriptions,
  evidence, and cross-discovery references.
- If a connected-model chunk still reaches the provider output limit, or if it
  receives a provider-unavailable response, only that chunk is split into two
  balanced children without repeating the failed parent request. Successful chunks
  are retained, every discovery remains represented in one successful child
  request, and the task-wide provider and logical-call budgets include failed
  parent attempts.
- Adaptive splitting stops safely when a single discovery still exceeds the
  provider boundary or the remaining call budget cannot accommodate both
  children.
- Logical calls and provider attempts are accounted separately: an exact-input
  retry consumes the provider-attempt budget but does not masquerade as a new
  planned logical call.
- Orchestration tests verify an invalid reference receives exactly one retry,
  the second prompt contains the safe diagnostic, and provider-attempt
  accounting remains accurate.
- Existing tests continue to verify that invalid output never reaches review or
  the ontology write path.

## Verification

The following commands are required:

```bash
./gradlew :web-server:test \
  --tests '*OpenAiDocumentAnalysisClientTest*' \
  --tests '*DocumentIngestionOrchestratorTest*'
./gradlew :semantic-engine:test \
  --tests '*DocumentSemanticPlanCompilerTest*'
./gradlew test
./gradlew check
./gradlew build
./gradlew :web-server:clean :web-server:compileKotlin
git diff --check
```

## Result And Limits

No modules, dependencies, persistence, client behavior, or write paths changed.
Semantic kinds, references, grouping, outcomes, confidence, ambiguity, and
rationale remain model-produced and deterministically validated. The human
review and atomic apply boundaries are unchanged.

Git commit and push status are recorded by the task handoff.
