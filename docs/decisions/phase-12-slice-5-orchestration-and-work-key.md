# Phase 12 Slice 5: Orchestration And Frozen Work Key

Status: Complete on 2026-07-31 after the explicitly authorized Slice 6/Slice 5
dependency reorder.

## Decision

New production document tasks now run the Phase 12 sequence after existing text
extraction:

1. deterministic local candidate extraction;
2. deterministic retrieval over the loaded project, current staging/proposal,
   durable provenance, and pinned FIBO catalog;
3. a freshness rebuild before the first grounded provider call;
4. bounded grounded modeling with the verified selected model;
5. deterministic grounded verification and existing semantic compilation;
6. existing final-plan verification and review installation.

The legacy Phase 11.5 pipeline remains available only through the explicit
`groundedAnalysisEnabled = false` compatibility configuration used by its
historical regression fixtures. The production default is grounded analysis;
there is no automatic ontology-blind fallback when grounded capability or
retrieval fails.

## Frozen Identity And Safety

The Phase 12 work key includes document and evidence inventories, candidates,
retrieval results, ontology, current work, provenance, pinned FIBO metadata,
extractor and NLP resource versions, ranking, selected model, prompt, and
response versions. Ontology and current-work fingerprints are checked again
before modeling and compilation.

Candidate extraction and retrieval stage records have zero provider attempts.
Grounded modeling separately records its bounded provider-attempt count. The
workflow preserves cancellation checks, project ownership, temporary cleanup,
and the existing no-write review boundary.

## Authorized Adapter Amendment

With explicit user authorization, `DocumentAnalysisService.kt` exposes a narrow
grounded-plan entry into the existing semantic compiler and final-plan
contracts. This keeps compilation policy in its existing owner and avoids a
second compiler in orchestration.

## Verification

The required orchestration, lifecycle, bounds, and route integration test set
passes, including a new test proving local candidates and zero-attempt
retrieval occur before the first grounded provider call while ontology source
bytes remain unchanged.
