# Phase 7 Slice 5: Semantic Read Capabilities

## ExecPlan Slice

Slice 5 of `docs/execplans/0012-phase-7-tool-driven-native-ai-ontology-copilot.md`.

## Goal

Expose bounded, read-only AI capabilities over retained reasoning and SHACL results, current proposal validation and diff data, shared workflow activity, and the pinned FIBO catalog without duplicating semantic calculations or widening project, source, or user scope.

## Files Modified

- `web-server/src/main/kotlin/com/entio/web/JobContracts.kt`
- `web-server/src/main/kotlin/com/entio/web/SemanticJobManager.kt`
- `web-server/src/main/kotlin/com/entio/web/contract/CollaborationContracts.kt`
- `web-server/src/main/kotlin/com/entio/web/CollaborationHub.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiCapabilityContracts.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiCapabilityRegistry.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiLocalReadCapabilities.kt`
- `web-server/src/main/kotlin/com/entio/web/ai/AiSemanticReadCapabilities.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiCapabilityRegistryTest.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/AiSemanticReadCapabilitiesTest.kt`
- `web-server/src/test/kotlin/com/entio/web/ai/OpenAiResponsesClientTest.kt`
- `docs/decisions/phase-7-slice-5-semantic-read-capabilities.md`

## Implemented Behavior

- Retained completed reasoning and SHACL result objects with their existing semantic jobs and exposed bounded web DTOs tied to graph and proposal fingerprints.
- Added AI reads for retained semantic jobs, current visible proposal validation and diff data, bounded shared workflow activity, deterministic curated FIBO search, and FIBO descriptors and dependencies.
- Suppressed semantic facts and findings outside the run's allowed source IDs and withheld retained results from stale, incomplete, failed, cancelled, queued, or running jobs.
- Added explicit `UNAVAILABLE` states for detailed reasoning explanations and separate Phase 4 proposal-impact reports because the current web workflow does not retain those artifacts.
- Added a bounded in-memory history of shared staged-change, proposal, and semantic-job collaboration events. Presence and entity-activity events are not retained or exposed to AI reads.
- Delegated FIBO ranking and descriptors to `FiboWebService`; no external ranking, ontology parsing, proposal logic, or mutation was added to the AI layer.

## Tests Added Or Updated

- Added focused semantic-read tests for job identity and fingerprints, source filtering, bounded proposal and activity reads, explicit unavailable states, and deterministic FIBO delegation.
- Updated capability-registry tests for strict authorization of the five new tools.
- Updated OpenAI tool serialization coverage for the expanded registry.

## Verification

- `./gradlew :web-server:test` - passed.
- `./gradlew test` - passed.
- `git diff --check` - passed.

## Assumptions And Limitations

- The user explicitly authorized focused additive changes to `SemanticJobManager`, job contracts, `CollaborationHub`, and collaboration contracts after the existing services were found not to retain the detailed data required by this slice.
- Collaboration activity is bounded and in-memory only; this is not a durable activity store.
- Detailed reasoning explanations are not reconstructed by the AI adapter. They remain unavailable until an approved workflow retains explanation artifacts.
- The current proposal workflow exposes validation and explicit semantic diff data but not the separate `ProposalImpactReport`; the adapter reports this honestly instead of recalculating it.
- Raw full result graphs are never returned.

## Git

A focused Git commit is created as part of this verified slice.
